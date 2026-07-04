package com.aiagent.tools.search;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Bing Web Search API v7 搜索引擎。
 *
 * <p>使用微软官方 REST API，返回结构化 JSON + 精准相关性排序。
 * 不依赖 HTML 抓取，不受反爬虫影响。
 *
 * <p><b>前置条件</b>：需要 Azure 订阅 + Bing Search v7 资源。
 * 免费层每月 1000 次事务。
 * 获取 key：https://portal.azure.com → 创建资源 → Bing Search v7
 *
 * <p>API 文档：https://learn.microsoft.com/en-us/bing/search-apis/bing-web-search/overview
 */
@Slf4j
public class BingApiSearchEngine implements SearchEngine {

    private static final String API_ENDPOINT = "https://api.bing.microsoft.com/v7.0/search";
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String apiKey;
    private final String market;

    /**
     * @param apiKey Azure Bing Search API key
     * @param market 市场代码（如 "zh-CN", "en-US"），影响搜索结果语言和地区
     */
    public BingApiSearchEngine(String apiKey, String market) {
        this.apiKey = apiKey;
        this.market = market != null ? market : "zh-CN";
    }

    public BingApiSearchEngine(String apiKey) {
        this(apiKey, "zh-CN");
    }

    @Override
    public List<SearchResult> search(String query, int maxResults) {
        if (apiKey == null || apiKey.isBlank()) {
            log.debug("Bing API key not configured, skipping API search");
            return List.of();
        }

        List<SearchResult> results = new ArrayList<>();
        try {
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String url = API_ENDPOINT + "?q=" + encodedQuery
                    + "&count=" + Math.min(maxResults, 15)
                    + "&mkt=" + market
                    + "&safeSearch=Moderate";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Ocp-Apim-Subscription-Key", apiKey)
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(
                    request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.warn("Bing API returned HTTP {}: {}", response.statusCode(),
                        response.body().length() > 200
                                ? response.body().substring(0, 200)
                                : response.body());
                return List.of();
            }

            JsonNode root = MAPPER.readTree(response.body());
            JsonNode webPages = root.get("webPages");
            if (webPages == null) {
                log.debug("Bing API: no webPages in response for '{}'", query);
                return List.of();
            }

            JsonNode values = webPages.get("value");
            if (values == null || !values.isArray()) {
                return List.of();
            }

            int rank = 0;
            for (JsonNode item : values) {
                if (rank >= maxResults) break;
                rank++;

                String title = item.has("name") ? item.get("name").asText("") : "";
                String url2 = item.has("url") ? item.get("url").asText("") : "";
                String snippet = item.has("snippet") ? item.get("snippet").asText("") : "";

                if (title.isEmpty()) continue;

                results.add(SearchResult.of(
                        title, url2, snippet, "bing-api", rank));
            }

            log.debug("Bing API: {} results for '{}'", results.size(), query);

        } catch (Exception e) {
            log.warn("Bing API search failed for '{}': {}", query, e.getMessage());
        }
        return results;
    }
}
