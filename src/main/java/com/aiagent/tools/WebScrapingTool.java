package com.aiagent.tools;

import com.aiagent.tools.utils.ContentExtractor;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.IOException;

/**
 * 网页抓取工具 — 基于 Jsoup 获取网页内容，并用正文提取算法去除噪音。
 *
 * <p>与旧版的区别：使用 {@link ContentExtractor} 智能提取正文，
 * 而非简单的 {@code body.text()}。导航栏、广告、侧边栏等噪音内容
 * 被自动过滤，确保返回内容中正文占比最大化。
 *
 * <p>对于 JS 动态渲染的现代网站（SPA），返回内容可能仍然较少，
 * 此时建议配合 web_search 查找替代来源。
 */
public class WebScrapingTool {

    private static final int TIMEOUT_MS = 15_000;
    private static final int MAX_BODY_LENGTH = 8_000;

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36";

    @Tool(description = """
            Scrape and extract the MAIN CONTENT from a specific web page URL. Uses an
            intelligent content extraction algorithm to remove navigation, ads, sidebars,
            and other noise — keeping only the article body text.

            Returns clean text up to 8000 characters with noise filtered out.

            WHEN TO USE: After web_search has found a promising URL and you need the full
            article content. Call ONLY when you have a specific URL from search results.

            LIMITATIONS: JavaScript-heavy sites may return little or no content. If scraping
            fails (403, 404, timeout), try a different URL from search results — do NOT
            re-scrape the same URL repeatedly.

            TIP: The extraction algorithm auto-detects the main content area (prioritizing
            <article>, <main> tags, then text-density scoring), so you don't need to worry
            about whether the URL is a blog, news site, or documentation page.
            """)
    public String scrapeWebPage(
            @ToolParam(description = "Full URL of the web page to scrape") String url) {
        try {
            Connection connection = Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .timeout(TIMEOUT_MS)
                    .followRedirects(true)
                    .maxBodySize(2_000_000)  // 2 MB max
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");

            Document document = connection.get();

            // ★ 使用正文提取算法替代 body.text()
            String content = ContentExtractor.extract(document, MAX_BODY_LENGTH);

            if (content.isBlank()) {
                return "The page at " + url + " returned no visible text content after "
                        + "content extraction. This might be a JavaScript-rendered site. "
                        + "Try a different URL or use web_search to find alternative sources.";
            }

            return content;

        } catch (IOException e) {
            String msg = e.getMessage();
            if (msg != null) {
                if (msg.contains("403") || msg.contains("Forbidden")) {
                    return "Access denied (HTTP 403) when scraping " + url
                            + ". The site may block automated access. Try a different source.";
                }
                if (msg.contains("404") || msg.contains("Not Found")) {
                    return "Page not found (HTTP 404): " + url
                            + ". Please verify the URL is correct.";
                }
                if (msg.contains("timeout") || msg.contains("timed out")) {
                    return "Connection timed out when scraping " + url
                            + ". The site may be slow or unreachable. Try a different source.";
                }
            }
            return "Error scraping web page: " + e.getMessage();
        } catch (Exception e) {
            return "Unexpected error scraping " + url + ": " + e.getMessage();
        }
    }
}
