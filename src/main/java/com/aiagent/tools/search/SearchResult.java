package com.aiagent.tools.search;

/**
 * 统一搜索结果 — 不同搜索引擎返回的标准格式。
 *
 * @param title        结果标题
 * @param url          目标 URL
 * @param snippet      摘要/描述
 * @param engine       来源引擎标识（"bing" / "duckduckgo"）
 * @param rank         在来源引擎中的排名（1-based）
 * @param qualityScore 聚合后的质量评分（0-100），由 SearchAggregator 计算
 */
public record SearchResult(
        String title,
        String url,
        String snippet,
        String engine,
        int rank,
        int qualityScore) {

    /** 创建带默认评分的搜索结果（评分由 Aggregator 后续填充） */
    public static SearchResult of(String title, String url, String snippet,
                                   String engine, int rank) {
        return new SearchResult(title, url, snippet, engine, rank, 0);
    }

    /** 返回带新评分的副本 */
    public SearchResult withScore(int score) {
        return new SearchResult(title, url, snippet, engine, rank, score);
    }

    /** 提取域名（用于去重） */
    public String domain() {
        try {
            String host = url
                    .replaceFirst("^https?://", "")
                    .replaceFirst("/.*$", "");
            // 去掉 www. 前缀
            return host.replaceFirst("^www\\.", "");
        } catch (Exception e) {
            return url;
        }
    }
}
