package com.aiagent.tools.utils;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * 正文提取器 — 基于 Jsoup 的简化版 Readability 算法。
 *
 * <p>从 HTML 页面中智能提取正文内容，去除导航栏、广告、侧边栏、
 * 页脚等噪音。不引入任何新依赖（基于已有 Jsoup 1.19.1）。
 *
 * <h3>算法流程</h3>
 * <ol>
 *   <li>去除噪音标签（script, style, nav, footer 等）</li>
 *   <li>按优先级定位主内容区（article → main → 文本密度打分）</li>
 *   <li>提取保留段落结构的纯文本</li>
 *   <li>按 maxLength 截断</li>
 * </ol>
 */
public final class ContentExtractor {

    private ContentExtractor() { /* utility class */ }

    // ──── 噪音标签/属性 ────

    /** 直接移除的标签 */
    private static final Set<String> NOISE_TAGS = Set.of(
            "script", "style", "nav", "footer", "header", "aside",
            "noscript", "iframe", "form", "svg", "canvas", "video",
            "audio", "button", "input", "select", "textarea", "object",
            "embed", "applet", "map"
    );

    /** class/id 包含这些关键词的元素视为噪音 */
    private static final Set<String> NOISE_CLASS_PATTERNS = Set.of(
            "sidebar", "side-bar", "widget", "advertisement", "ad-", "_ad",
            "banner", "popup", "modal", "overlay", "social-share",
            "comment", "related-posts", "recommend", "breadcrumb",
            "pagination", "nav", "menu", "footer", "header", "copyright",
            "disclaimer", "cookie", "newsletter", "subscribe"
    );

    /** 明确的内容标签 */
    private static final Set<String> CONTENT_TAGS = Set.of(
            "article", "main"
    );

    /** 块级标签（保留换行结构） */
    private static final Set<String> BLOCK_TAGS = Set.of(
            "p", "div", "section", "article", "main", "aside",
            "h1", "h2", "h3", "h4", "h5", "h6",
            "li", "blockquote", "pre", "figure", "figcaption",
            "table", "tr", "hr", "br", "dl", "dt", "dd",
            "header", "footer", "nav", "form", "fieldset"
    );

    // ──── 公开方法 ────

    /**
     * 从 HTML Document 中提取正文。
     *
     * @param doc      Jsoup 解析后的 Document
     * @param maxLength 最大返回字符数（正文截断于此）
     * @return 清洗后的正文文本
     */
    public static String extract(Document doc, int maxLength) {
        if (doc == null || doc.body() == null) {
            return "";
        }

        // 克隆一份，避免修改原始 Document
        Element body = doc.body().clone();

        // ① 去除噪音
        removeNoise(body);

        // ② 定位主内容区
        Element contentRoot = findContentRoot(body);

        // ③ 提取文本
        StringBuilder sb = new StringBuilder();
        extractText(contentRoot, sb);
        String result = sb.toString().replaceAll("\\n{3,}", "\n\n").trim();

        // ④ 截断
        if (result.length() > maxLength) {
            result = result.substring(0, maxLength)
                    + "\n\n[... content truncated, original extracted length: "
                    + result.length() + " characters]";
        }

        return result;
    }

    // ──── 噪音去除 ────

    /**
     * 递归移除噪音标签和噪音 class/id 的元素。
     */
    private static void removeNoise(Element root) {
        // ① 移除明确噪音标签
        for (String tag : NOISE_TAGS) {
            root.select(tag).remove();
        }

        // ② 移除带有噪音 class/id 的元素（但保留可能是内容的 div/section）
        Elements allElements = root.select("*");
        for (Element el : allElements) {
            if (isNoiseByAttribute(el) && !isContentTag(el)) {
                el.remove();
            }
        }

        // ③ 移除空元素（递归，从叶子开始）
        removeEmptyElements(root);
    }

    /** 检查元素的 class/id 是否命中噪音模式 */
    private static boolean isNoiseByAttribute(Element el) {
        String clazz = el.className().toLowerCase();
        String id = el.id().toLowerCase();
        String role = el.attr("role").toLowerCase();

        if ("navigation".equals(role) || "complementary".equals(role)
                || "banner".equals(role) || "contentinfo".equals(role)) {
            return true;
        }

        if ("true".equals(el.attr("aria-hidden"))) {
            return true;
        }

        String combined = clazz + " " + id;
        for (String pattern : NOISE_CLASS_PATTERNS) {
            if (combined.contains(pattern)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isContentTag(Element el) {
        return CONTENT_TAGS.contains(el.tagName().toLowerCase());
    }

    /** 递归移除无文本的空元素 */
    private static void removeEmptyElements(Element root) {
        Elements all = root.select("*");
        // 从叶子节点开始处理（倒序）
        for (int i = all.size() - 1; i >= 0; i--) {
            Element el = all.get(i);
            if (isContentTag(el)) continue; // 不删除内容标签本身
            String text = el.ownText().trim();
            if (text.isEmpty() && el.children().isEmpty()
                    && !"br".equals(el.tagName()) && !"hr".equals(el.tagName())
                    && !"img".equals(el.tagName())) {
                el.remove();
            }
        }
    }

    // ──── 主内容区定位 ────

    /**
     * 按优先级定位主内容区：
     * 1. &lt;article&gt; 标签
     * 2. &lt;main&gt; 标签
     * 3. [role="main"]
     * 4. 文本密度打分最高的 div/section
     */
    private static Element findContentRoot(Element body) {
        // 优先级 1：<article>
        Element article = body.selectFirst("article");
        if (article != null) {
            return article;
        }

        // 优先级 2：<main>
        Element main = body.selectFirst("main");
        if (main != null) {
            return main;
        }

        // 优先级 3：[role="main"]
        Element roleMain = body.selectFirst("[role=main]");
        if (roleMain != null) {
            return roleMain;
        }

        // 优先级 4：文本密度打分
        Element best = findBestByTextDensity(body);
        if (best != null) {
            return best;
        }

        // fallback：返回整个 body
        return body;
    }

    /**
     * 对 body 下的 div/section 进行文本密度打分。
     *
     * <p>得分公式：ownTextLength / (1 + linkTextLength × 2)
     * <ul>
     *   <li>ownTextLength — 该元素"自己的"文本量（不计子元素）</li>
     *   <li>linkTextLength — 该元素内所有 &lt;a&gt; 标签的文本量</li>
     * </ul>
     *
     * <p>得分越高 = 文本越密集、链接越少 = 越可能是正文。
     */
    private static Element findBestByTextDensity(Element body) {
        Elements candidates = body.select("div, section");
        if (candidates.isEmpty()) {
            return null;
        }

        Element best = null;
        double bestScore = 0;
        int bestTotalText = 0;

        for (Element el : candidates) {
            int ownTextLen = el.ownText().length();
            int linkTextLen = 0;
            for (Element a : el.select("a")) {
                linkTextLen += a.text().length();
            }

            double score = ownTextLen / (1.0 + linkTextLen * 2.0);
            int totalText = el.text().length();

            // 优先高分，同分时选文本总量更大的
            if (score > bestScore || (score == bestScore && totalText > bestTotalText)) {
                bestScore = score;
                bestTotalText = totalText;
                best = el;
            }
        }

        // 阈值：至少要有 200 字符且得分 > 1.0 才认定为正文区
        if (best != null && bestTotalText >= 200 && bestScore >= 1.0) {
            return best;
        }
        return null;
    }

    // ──── 文本提取 ────

    /**
     * 递归提取元素内的文本，保留段落结构。
     * <p>每个块级元素结束后追加换行，普通文本节点直接追加。
     */
    private static void extractText(Element root, StringBuilder sb) {
        for (Node child : root.childNodes()) {
            if (child instanceof TextNode) {
                String text = ((TextNode) child).text();
                // 合并多余空白
                String cleaned = text.replaceAll("\\s+", " ");
                if (!cleaned.isBlank()) {
                    sb.append(cleaned);
                }
            } else if (child instanceof Element el) {
                String tag = el.tagName().toLowerCase();

                // 跳过已移除的噪音标签（防御性检查）
                if (NOISE_TAGS.contains(tag)) continue;
                if (isNoiseByAttribute(el)) continue;

                // 递归处理内容标签
                if (BLOCK_TAGS.contains(tag)) {
                    // 标题前加空行使排版更清晰
                    if (tag.matches("h[1-6]") && sb.length() > 0 && !sb.toString().endsWith("\n")) {
                        sb.append("\n");
                    }
                    extractText(el, sb);
                    if (sb.length() > 0 && sb.charAt(sb.length() - 1) != '\n') {
                        sb.append('\n');
                    }
                } else {
                    // 内联元素（span, a, strong, em 等）：直接提取文本不换行
                    extractInline(el, sb);
                }
            }
        }
    }

    /** 提取内联元素的文本（不追加换行） */
    private static void extractInline(Element el, StringBuilder sb) {
        for (Node child : el.childNodes()) {
            if (child instanceof TextNode) {
                String text = ((TextNode) child).text();
                String cleaned = text.replaceAll("\\s+", " ");
                if (!cleaned.isBlank()) {
                    sb.append(cleaned);
                }
            } else if (child instanceof Element childEl) {
                String tag = childEl.tagName().toLowerCase();
                if (BLOCK_TAGS.contains(tag)) {
                    // 内联元素里出现了块级元素（不规范的 HTML），递归
                    extractText(childEl, sb);
                } else {
                    extractInline(childEl, sb);
                }
            }
        }
    }
}
