package com.aiagent.tools.search;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.List;

/**
 * Bing 搜索引擎 — 通过抓取 Bing 搜索结果页获取结果。
 *
 * <p>不需要 API Key。www.bing.com 在国内会自动 302 重定向到 cn.bing.com，
 * Jsoup 的 followRedirects 可正确跟随。搜索结果通过 CSS 选择器解析。
 *
 * <p><b>已知限制</b>：
 * <ul>
 *   <li>HTML 抓取受反爬虫策略影响，结果可能不如 API 精准</li>
 *   <li>不建议在生产环境使用，推荐 Bing Web Search API（Azure 免费层 1000 次/月）</li>
 * </ul>
 */
@Slf4j
public class BingSearchEngine implements SearchEngine {

    private static final String SEARCH_URL = "https://www.bing.com/search";
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
                    .followRedirects(true)
                    .header("Accept", "text/html,application/xhtml+xml,"
                            + "application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                    .data("q", query)
                    .data("count", String.valueOf(Math.min(maxResults, 10)))
                    .get();

            Elements resultBlocks = doc.select("li.b_algo");

            for (Element block : resultBlocks) {
                if (results.size() >= maxResults) break;

                Element titleLink = block.selectFirst("h2 a");
                if (titleLink == null) continue;

                String title = titleLink.text().trim();
                String url = titleLink.attr("href");

                Element snippetEl = block.selectFirst("div.b_caption p");
                String snippet = snippetEl != null ? snippetEl.text().trim() : "";

                if (title.isEmpty()) continue;

                results.add(SearchResult.of(
                        title, url, snippet, "bing", results.size() + 1));
            }

            log.debug("Bing: {} results for '{}' (page title: '{}')",
                    results.size(), query, doc.title());

        } catch (Exception e) {
            log.warn("Bing search failed for '{}': {}", query, e.getMessage());
        }
        return results;
    }
}
