package com.aiagent.tools.search;

import cn.hutool.core.collection.CollUtil;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 多引擎搜索聚合器。
 *
 * <p>并发查询多个搜索引擎 → 合并去重 → 质量评分 → 排序返回。
 *
 * <h3>处理流程</h3>
 * <ol>
 *   <li>并发调用所有已注册的搜索引擎（最多等待 10 秒）</li>
 *   <li>按 URL 去重（同 URL 保留排名更高的；同域名 + 相似标题只保留一个）</li>
 *   <li>计算质量评分（来源权威度 + 标题匹配度 + 多引擎互验证）</li>
 *   <li>按评分降序排序，返回 Top N</li>
 * </ol>
 */
@Slf4j
public class SearchAggregator {

    private final List<SearchEngine> engines;
    private final int totalTimeoutMs;
    private final int maxResults;

    /** 聚合站 / SEO 农场域名（质量较低） */
    private static final Set<String> LOW_QUALITY_DOMAINS = Set.of(
            "tophub.today", "zhuanlan.zhihu.com", "baike.baidu.com",
            "wenku.baidu.com", "jingyan.baidu.com", "douyin.com",
            "xiaohongshu.com", "sohu.com/a/", "163.com/dy/",
            "kuaishou.com", "ixigua.com", "pearvideo.com"
    );

    /** 高质量域名后缀 */
    private static final Set<String> HIGH_QUALITY_SUFFIXES = Set.of(
            ".edu", ".gov", ".ac.cn", ".org"
    );

    /** 知名技术媒体域名（含子串匹配） */
    private static final Set<String> REPUTABLE_SOURCES = Set.of(
            "spring.io", "github.com", "stackoverflow.com",
            "medium.com", "infoq.com", "arxiv.org",
            "npmjs.com", "maven.org", "apache.org",
            "wikipedia.org", "developer.mozilla.org", "mdn.io",
            "baeldung.com", "dzone.com", "freecodecamp.org",
            "hackernoon.com", "dev.to", "csdn.net",
            "juejin.cn", "segmentfault.com", "zhihu.com/question",
            "cnblogs.com", "51cto.com", "oschina.net"
    );

    public SearchAggregator(List<SearchEngine> engines) {
        this(engines, 10_000, 6);
    }

    public SearchAggregator(List<SearchEngine> engines, int totalTimeoutMs, int maxResults) {
        this.engines = List.copyOf(engines);
        this.totalTimeoutMs = totalTimeoutMs;
        this.maxResults = maxResults;
    }

    /**
     * 执行多引擎搜索并返回聚合排序后的结果。
     *
     * @param query 搜索关键词
     * @return 聚合后的高质量搜索结果
     */
    public List<SearchResult> search(String query) {
        if (CollUtil.isEmpty(engines)) {
            log.warn("No search engines configured");
            return List.of();
        }

        // ── ① 并发查询所有引擎 ──
        @SuppressWarnings("unchecked")
        CompletableFuture<List<SearchResult>>[] futures = engines.stream()
                .map(engine -> CompletableFuture.supplyAsync(
                        () -> engine.search(query, maxResults)))
                .toArray(CompletableFuture[]::new);

        List<SearchResult> allResults = new ArrayList<>();
        try {
            CompletableFuture.allOf(futures)
                    .get(totalTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.warn("Search aggregation timed out or failed: {}", e.getMessage());
        }

        // 收集所有已完成的结果（忽略失败的）
        for (CompletableFuture<List<SearchResult>> future : futures) {
            try {
                if (future.isDone() && !future.isCompletedExceptionally()) {
                    List<SearchResult> engineResults = future.getNow(List.of());
                    allResults.addAll(engineResults);
                }
            } catch (Exception e) {
                log.debug("Skipping failed engine result: {}", e.getMessage());
            }
        }

        if (allResults.isEmpty()) {
            return List.of();
        }

        // ── ② 去重 ──
        List<SearchResult> deduped = deduplicate(allResults);

        // ── ③ 质量评分 ──
        List<SearchResult> scored = scoreResults(deduped, query, allResults);

        // ── ④ 排序返回 ──
        return scored.stream()
                .sorted(Comparator.comparingInt(SearchResult::qualityScore).reversed())
                .limit(maxResults)
                .toList();
    }

    // ──── 去重逻辑 ────

    /**
     * 规则：
     * <ul>
     *   <li>URL 完全一致 → 保留 rank 更小的</li>
     *   <li>同域名 + 标题相似度 ≥ 80% → 只保留 rank 更小的</li>
     * </ul>
     */
    private List<SearchResult> deduplicate(List<SearchResult> results) {
        // 按 URL 去重
        Map<String, SearchResult> byUrl = new LinkedHashMap<>();
        for (SearchResult r : results) {
            String normalizedUrl = normalizeUrl(r.url());
            SearchResult existing = byUrl.get(normalizedUrl);
            if (existing == null || r.rank() < existing.rank()) {
                byUrl.put(normalizedUrl, r);
            }
        }

        // 按域名 + 标题相似度去重
        List<SearchResult> deduped = new ArrayList<>(byUrl.values());
        List<SearchResult> finalList = new ArrayList<>();
        for (SearchResult r : deduped) {
            boolean isDuplicate = false;
            for (SearchResult existing : finalList) {
                if (r.domain().equals(existing.domain())
                        && titleSimilarity(r.title(), existing.title()) >= 0.8) {
                    isDuplicate = true;
                    break;
                }
            }
            if (!isDuplicate) {
                finalList.add(r);
            }
        }
        return finalList;
    }

    private String normalizeUrl(String url) {
        return url.replaceFirst("^https?://(www\\.)?", "")
                .replaceFirst("/$", "")
                .toLowerCase();
    }

    /** 简单的标题相似度（基于公共子串比例） */
    private double titleSimilarity(String a, String b) {
        if (a == null || b == null) return 0;
        String shorter = a.length() <= b.length() ? a : b;
        String longer = a.length() <= b.length() ? b : a;
        if (shorter.isEmpty()) return 0;

        // 计算公共字符数
        int common = 0;
        for (char c : shorter.toCharArray()) {
            if (longer.indexOf(c) >= 0) common++;
        }
        return (double) common / shorter.length();
    }

    // ──── 质量评分 ────

    /**
     * 为每个结果计算质量评分（0-100）。
     *
     * <p>加分项：来源权威度（.edu/.gov/知名媒体）、标题关键词匹配
     * <p>减分项：聚合站/SEO农场
     * <p>互验证：同一结果被多个引擎返回 +15 分
     */
    private List<SearchResult> scoreResults(List<SearchResult> results,
                                             String query,
                                             List<SearchResult> unfiltered) {
        // 统计每个 URL 在几个引擎中出现（用于互验证加分）
        Map<String, Long> urlEngineCount = unfiltered.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        r -> normalizeUrl(r.url()),
                        java.util.stream.Collectors.mapping(
                                SearchResult::engine,
                                java.util.stream.Collectors.toSet())))
                .entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey,
                        e -> (long) e.getValue().size()));

        // 解析查询中的关键词
        String[] queryWords = query.toLowerCase().split("[\\s,，、]+");

        return results.stream()
                .map(r -> {
                    int score = 50; // 基础分

                    String domain = r.domain().toLowerCase();
                    String titleLower = r.title().toLowerCase();

                    // 加分：权威来源
                    for (String suffix : HIGH_QUALITY_SUFFIXES) {
                        if (domain.endsWith(suffix)) {
                            score += 20;
                            break;
                        }
                    }
                    if (domain.contains(".gov")) score += 5; // .gov.cn 等

                    // 加分：知名媒体
                    for (String source : REPUTABLE_SOURCES) {
                        if (domain.contains(source)) {
                            score += 15;
                            break;
                        }
                    }

                    // 减分：聚合站/SEO农场
                    for (String bad : LOW_QUALITY_DOMAINS) {
                        if (domain.contains(bad)) {
                            score -= 30;
                            break;
                        }
                    }

                    // 加分：标题关键词匹配
                    int matchCount = 0;
                    for (String word : queryWords) {
                        if (word.length() >= 2 && titleLower.contains(word)) {
                            matchCount++;
                        }
                    }
                    score += Math.min(20, matchCount * 5);

                    // 加分：多引擎互验证
                    String normalizedUrl = normalizeUrl(r.url());
                    long engineCount = urlEngineCount.getOrDefault(normalizedUrl, 1L);
                    if (engineCount >= 2) {
                        score += 15;
                    }

                    // 钳制在 0-100
                    score = Math.max(0, Math.min(100, score));

                    return r.withScore(score);
                })
                .toList();
    }
}
