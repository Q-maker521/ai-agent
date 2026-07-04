package com.aiagent.tools;

import com.aiagent.tools.search.BingSearchEngine;
import com.aiagent.tools.search.DuckDuckGoSearchEngine;
import com.aiagent.tools.search.SearchAggregator;
import com.aiagent.tools.search.SearchResult;
import com.aiagent.tools.search.TavilySearchEngine;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 网页搜索工具 — 多引擎聚合搜索。
 *
 * <p><b>引擎优先级</b>：
 * <ol>
 *   <li>Tavily Search API（最精准，专为 AI Agent 设计）</li>
 *   <li>Bing HTML 抓取（免费 fallback）</li>
 *   <li>DuckDuckGo HTML 抓取（免费，国内可能不可用）</li>
 * </ol>
 *
 * <p>并发查询 → 去重 → 质量评分 → 排序返回 Top 6。
 */
public class WebSearchTool {

    private static final int MAX_RESULTS = 6;

    private final SearchAggregator aggregator;

    /**
     * 创建搜索工具实例。
     *
     * @param tavilyApiKey Tavily Search API key（可为 null，此时仅使用 HTML 抓取）
     */
    public WebSearchTool(String tavilyApiKey) {
        List<com.aiagent.tools.search.SearchEngine> engines = new ArrayList<>();

        // ① 优先：Tavily API（专为 AI Agent 设计，返回 LLM 优化的正文摘要）
        if (tavilyApiKey != null && !tavilyApiKey.isBlank()) {
            engines.add(new TavilySearchEngine(tavilyApiKey));
        }

        // ② Fallback：Bing HTML 抓取（免费，但精度受反爬影响）
        engines.add(new BingSearchEngine());

        // ③ 补充：DuckDuckGo HTML 抓取（国内大概率不可用，但不影响）
        engines.add(new DuckDuckGoSearchEngine());

        this.aggregator = new SearchAggregator(engines);
    }

    /** 无参构造器（仅 HTML 抓取，兼容旧代码） */
    public WebSearchTool() {
        this(null);
    }

    @Tool(description = """
            Search the web using multiple search engines (Tavily + Bing + DuckDuckGo).
            Results are deduplicated, quality-scored, and ranked. Returns top 6 results with
            title, URL, snippet, source engine, and quality score (0-100).

            WHEN TO USE: For real-time news, current events, or specific facts beyond your
            knowledge cutoff. Use ONLY when you genuinely need external information.

            WHEN NOT TO USE: For general knowledge, concepts, explanations, or anything you
            already know from your training data. Do NOT use for definitions or overviews.

            STOP CONDITION: After 3 searches with poor or irrelevant results, abandon search
            and use your own knowledge instead. State "基于我的知识..." in your response.

            QUERY TIPS: Use short, specific keywords (e.g., "AI breakthroughs 2026"), not long
            natural-language questions. Include the target language's keywords for better results.

            RESULT QUALITY: Results sourced from "bing-api" have the highest quality (official API
            ranking). Results with score ≥ 70 are generally reliable.
            """)
    public String searchWeb(
            @ToolParam(description = "Short keyword query (e.g., 'AI news 2026', not 'can you tell me about AI')") String query) {

        if (query == null || query.isBlank()) {
            return "⚠️ Empty search query. Please provide specific keywords.";
        }

        try {
            List<SearchResult> results = aggregator.search(query.trim());

            if (results.isEmpty()) {
                return "⚠️ No results from any search engine for \"" + query + "\". "
                    + "SUGGESTION: Use your own knowledge if the topic is general, "
                    + "or try completely different keywords (e.g., broader terms, "
                    + "different language).";
            }

            // 格式化为 LLM 可读的结果
            String output = results.stream()
                    .map(r -> String.format(
                            "[Score: %d] %s\n  URL: %s\n  Source: %s\n  %s",
                            r.qualityScore(), r.title(), r.url(),
                            r.engine(), r.snippet()))
                    .collect(Collectors.joining("\n\n"));

            // 质量警告
            boolean allLowQuality = results.stream()
                    .allMatch(r -> r.qualityScore() < 40);
            if (allLowQuality) {
                output += "\n\n⚠️ NOTE: All results have low quality scores. "
                    + "If this doesn't help, STOP searching and use your own knowledge instead.";
            }

            // 统计信息
            long apiResults = results.stream()
                    .filter(r -> "tavily".equals(r.engine())).count();
            output += "\n\n[" + results.size() + " unique results after deduplication"
                    + (apiResults > 0 ? ", " + apiResults + " from Tavily API" : "")
                    + "]";

            return output;

        } catch (Exception e) {
            return "Error searching web: " + e.getMessage()
                    + ". Please try a different query or use web_scrape on a known URL.";
        }
    }
}
