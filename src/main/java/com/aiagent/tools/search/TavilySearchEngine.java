package com.aiagent.tools.search;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Tavily Search API 搜索引擎 — 专为 AI Agent 设计的搜索服务。
 *
 * <p>Tavily 返回的 content 字段是 LLM 优化过的正文摘要，质量远高于
 * 传统搜索引擎的 snippet。自带相关性评分（0-1）。
 *
 * <p><b>注册</b>：https://tavily.com → 邮箱注册 → 获取 API Key
 * <br><b>免费额度</b>：1000 次/月
 * <br><b>API 文档</b>：https://docs.tavily.com
 */
@Slf4j
public class TavilySearchEngine implements SearchEngine {

    private static final String API_ENDPOINT = "https://api.tavily.com/search";
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String apiKey;

    public TavilySearchEngine(String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    public List<SearchResult> search(String query, int maxResults) {
        if (apiKey == null || apiKey.isBlank()) {
            log.debug("Tavily API key not configured, skipping");
            return List.of();
        }

        List<SearchResult> results = new ArrayList<>();
        try {
            // 构建请求体
            String body = MAPPER.writeValueAsString(
                    java.util.Map.of(
                            "api_key", apiKey,
                            "query", query,
                            "search_depth", "basic",
                            "max_results", Math.min(maxResults, 10),
                            "topic", "general"));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_ENDPOINT))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(15))
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(
                    request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.warn("Tavily API returned HTTP {}: {}",
                        response.statusCode(),
                        response.body().length() > 300
                                ? response.body().substring(0, 300)
                                : response.body());
                return List.of();
            }

            JsonNode root = MAPPER.readTree(response.body());
            JsonNode resultsNode = root.get("results");
            if (resultsNode == null || !resultsNode.isArray()) {
                log.debug("Tavily: no results for '{}'", query);
                return List.of();
            }

            int rank = 0;
            for (JsonNode item : resultsNode) {
                if (rank >= maxResults) break;
                rank++;

                String title = item.has("title")
                        ? item.get("title").asText("") : "";
                String url = item.has("url")
                        ? item.get("url").asText("") : "";
                // Tavily 的 content 是 LLM 优化过的正文摘要，比 snippet 质量高
                String content = item.has("content")
                        ? item.get("content").asText("") : "";

                if (title.isEmpty()) continue;

                results.add(SearchResult.of(
                        title, url, content, "tavily", rank));
            }

            // 记录响应时间（调试用）
            if (root.has("response_time")) {
                log.debug("Tavily: {} results for '{}' in {}s",
                        results.size(), query, root.get("response_time").asText());
            } else {
                log.debug("Tavily: {} results for '{}'", results.size(), query);
            }

        } catch (Exception e) {
            log.warn("Tavily search failed for '{}': {}", query, e.getMessage());
        }
        return results;
    }
}
