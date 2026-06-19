package com.aiagent.tools;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.IOException;

/**
 * 网页抓取工具 — 基于 Jsoup 获取网页完整 HTML 内容。
 *
 * <p>模拟浏览器请求头以避免被目标网站拒绝，设置超时和自动重定向。
 * 对于 JS 动态渲染的现代网站，返回 HTML 源码可能包含较少可读文本，
 * 此时建议配合 web_search 工具使用。
 */
public class WebScrapingTool {

    private static final int TIMEOUT_MS = 15_000;
    private static final int MAX_BODY_LENGTH = 8_000;

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36";

    @Tool(description = "Scrape the content of a web page and return its HTML body text")
    public String scrapeWebPage(
            @ToolParam(description = "URL of the web page to scrape") String url) {
        try {
            Connection connection = Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .timeout(TIMEOUT_MS)
                    .followRedirects(true)
                    .maxBodySize(2_000_000)  // 2 MB max
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");

            Document document = connection.get();

            // 提取 body 文本用于减少噪音，保留 HTML 结构以便 LLM 解析
            String bodyText = document.body() != null
                    ? document.body().text()
                    : document.text();

            if (bodyText.isBlank()) {
                return "The page at " + url + " returned no visible text content. "
                        + "This might be a JavaScript-rendered site. Try a different URL or use web_search to find alternative sources.";
            }

            // 截断过长内容，避免撑爆 LLM 上下文
            if (bodyText.length() > MAX_BODY_LENGTH) {
                bodyText = bodyText.substring(0, MAX_BODY_LENGTH)
                        + "\n\n[... content truncated, total length: "
                        + bodyText.length() + " characters]";
            }

            return bodyText;

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
