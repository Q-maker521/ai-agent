package com.aiagent.tools.search;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.List;

/**
 * DuckDuckGo 搜索引擎 — 通过抓取 DDG 纯 HTML 版获取结果。
 *
 * <p>不需要 API Key。DDG HTML 版结构简洁稳定，英文/技术内容搜索
 * 效果好，可作为 Bing 的互补引擎。
 *
 * <p>使用 {@code html.duckduckgo.com/html/} 端点（非 JS 渲染版），
 * CSS 选择器为 {@code .result}。
 */
@Slf4j
public class DuckDuckGoSearchEngine implements SearchEngine {

    private static final String SEARCH_URL = "https://html.duckduckgo.com/html/";
    private static final int DEFAULT_TIMEOUT_MS = 10_000;

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36";

    @Override
    public List<SearchResult> search(String query, int maxResults) {
        List<SearchResult> results = new ArrayList<>();
        try {
            Document doc = Jsoup.connect(SEARCH_URL)
                    .userAgent(USER_AGENT)
                    .timeout(DEFAULT_TIMEOUT_MS)
                    .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                    .data("q", query)
                    .get();

            // DDG HTML 版：每条结果在 .result 容器中
            Elements resultBlocks = doc.select(".result");

            for (Element block : resultBlocks) {
                if (results.size() >= maxResults) break;

                // 标题和链接在 .result__title 下的 a 标签
                Element titleLink = block.selectFirst(".result__title a, .result__a");
                if (titleLink == null) continue;

                String title = titleLink.text().trim();
                String url = extractDdgUrl(titleLink.attr("href"));

                // 摘要
                Element snippetEl = block.selectFirst(".result__snippet");
                String snippet = snippetEl != null ? snippetEl.text().trim() : "";

                if (title.isEmpty()) continue;

                results.add(SearchResult.of(
                        title, url, snippet, "duckduckgo", results.size() + 1));
            }

            log.debug("DuckDuckGo returned {} results for query: {}", results.size(), query);

        } catch (Exception e) {
            log.warn("DuckDuckGo search failed for query '{}': {}", query, e.getMessage());
            // 返回空列表，由 Aggregator 处理降级
        }
        return results;
    }

    /**
     * DDG 的 href 格式为 {@code //duckduckgo.com/l/?uddg=<encoded_url>&rut=...}
     * 需要从中提取真实的 URL。
     */
    private String extractDdgUrl(String href) {
        if (href == null || href.isBlank()) return href;

        // 如果是重定向格式，提取 uddg 参数
        if (href.contains("uddg=")) {
            try {
                int start = href.indexOf("uddg=") + 5;
                int end = href.indexOf('&', start);
                String encoded = end > 0 ? href.substring(start, end) : href.substring(start);
                return java.net.URLDecoder.decode(encoded, "UTF-8");
            } catch (Exception e) {
                log.trace("Failed to decode DDG URL: {}", href);
            }
        }
        return href;
    }
}
