package com.aiagent.tools;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 网页搜索工具 — 基于 Bing 搜索引擎，无需 API Key。
 *
 * <p>通过抓取 Bing 搜索结果页来获取摘要信息，
 * 返回前 5 条结果的标题、摘要和 URL。
 * Bing 在国内可直接访问，中英文搜索效果均好。
 */
public class WebSearchTool {

    private static final String SEARCH_URL = "https://www.bing.com/search";
    private static final int TIMEOUT_MS = 10_000;
    private static final int MAX_RESULTS = 5;

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36";

    @Tool(description = "Search the web for information using Bing. Returns title, URL, and snippet for each result.")
    public String searchWeb(
            @ToolParam(description = "Search query keyword") String query) {
        try {
            Document doc = Jsoup.connect(SEARCH_URL)
                    .userAgent(USER_AGENT)
                    .timeout(TIMEOUT_MS)
                    .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                    .data("q", query)
                    .data("count", String.valueOf(MAX_RESULTS))
                    .get();

            Elements resultBlocks = doc.select("li.b_algo");
            List<Map<String, String>> results = new ArrayList<>();

            for (Element block : resultBlocks) {
                if (results.size() >= MAX_RESULTS) break;

                // 提取标题和 URL（h2 标签下的第一个 a 标签）
                Element titleLink = block.selectFirst("h2 a");
                if (titleLink == null) continue;

                String title = titleLink.text().trim();
                String url = titleLink.attr("href");

                // 提取摘要
                Element snippet = block.selectFirst("div.b_caption p");
                String snippetText = snippet != null ? snippet.text().trim() : "";

                if (title.isEmpty()) continue;

                Map<String, String> item = new LinkedHashMap<>();
                item.put("title", title);
                item.put("url", url);
                item.put("snippet", snippetText);
                results.add(item);
            }

            if (results.isEmpty()) {
                return "No search results found for query: \"" + query + "\"";
            }

            return results.stream()
                    .map(r -> "Title: " + r.get("title") +
                              "\nURL: " + r.get("url") +
                              "\nSnippet: " + r.get("snippet"))
                    .collect(Collectors.joining("\n\n"));

        } catch (Exception e) {
            return "Error searching web: " + e.getMessage()
                    + ". Please try a different query or use web_scrape on a known URL.";
        }
    }
}
