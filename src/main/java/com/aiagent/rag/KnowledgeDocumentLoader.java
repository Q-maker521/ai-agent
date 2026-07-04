package com.aiagent.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识库文档加载器（从 classpath:document/ 加载 Markdown 文档并解析 YAML front matter 元数据）。
 *
 * <p>支持的 front matter 字段：
 * <ul>
 *   <li>{@code category} — 文档分类（用于 RAG 检索过滤）</li>
 *   <li>{@code tags} — 标签列表，格式 [tag1, tag2, ...]</li>
 *   <li>{@code summary} — 文档摘要</li>
 * </ul>
 */
@Component
@Slf4j
public class KnowledgeDocumentLoader {

    private final ResourcePatternResolver resourcePatternResolver;

    public KnowledgeDocumentLoader(ResourcePatternResolver resourcePatternResolver) {
        this.resourcePatternResolver = resourcePatternResolver;
    }

    /**
     * 返回知识库中所有文档的元数据（文件名、分类、标签、摘要），
     * 用于前端展示知识库目录。
     */
    public List<Map<String, String>> getDocumentCatalog() {
        List<Map<String, String>> catalog = new ArrayList<>();
        try {
            Resource[] resources = resourcePatternResolver.getResources("classpath:document/*.md");
            for (Resource resource : resources) {
                String rawContent = resource.getContentAsString(StandardCharsets.UTF_8);
                FrontMatter fm = parseFrontMatter(rawContent);
                Map<String, String> item = new LinkedHashMap<>();
                item.put("filename", resource.getFilename());
                item.put("category", fm.category);
                item.put("tags", fm.tags);
                item.put("summary", fm.summary);
                catalog.add(item);
            }
        } catch (IOException e) {
            log.error("Failed to read document catalog", e);
        }
        return catalog;
    }

    /**
     * 加载多篇 Markdown 文档，解析 front matter 作为元数据。
     */
    public List<Document> loadMarkdowns() {
        List<Document> allDocuments = new ArrayList<>();
        try {
            Resource[] resources = resourcePatternResolver.getResources("classpath:document/*.md");
            for (Resource resource : resources) {
                String filename = resource.getFilename();
                log.info("Loading document: {}", filename);

                // 读取原始内容
                String rawContent = resource.getContentAsString(StandardCharsets.UTF_8);

                // 解析 YAML front matter
                FrontMatter fm = parseFrontMatter(rawContent);

                // 构建文档元数据
                Map<String, String> additionalMetadata = new LinkedHashMap<>();
                additionalMetadata.put("filename", filename);
                additionalMetadata.put("category", fm.category);
                additionalMetadata.put("summary", fm.summary);
                additionalMetadata.put("tags", fm.tags);

                MarkdownDocumentReaderConfig config = MarkdownDocumentReaderConfig.builder()
                        .withHorizontalRuleCreateDocument(true)
                        .withIncludeCodeBlock(false)
                        .withIncludeBlockquote(false)
                        .build();

                Resource contentResource;
                if (fm.hasFrontMatter) {
                    // 剥离 front matter 后的内容
                    String contentAfterFm = fm.contentAfterFrontMatter;
                    contentResource = new ByteArrayResource(
                            contentAfterFm.getBytes(StandardCharsets.UTF_8), filename);
                } else {
                    contentResource = resource;
                }

                MarkdownDocumentReader markdownDocumentReader =
                        new MarkdownDocumentReader(contentResource, config);
                List<Document> docs = markdownDocumentReader.get();

                // 给每篇文档块注入统一的元数据
                for (Document doc : docs) {
                    doc.getMetadata().putAll(additionalMetadata);
                }

                allDocuments.addAll(docs);
                log.info("  -> {} chunks, category={}, tags={}", docs.size(), fm.category, fm.tags);
            }
        } catch (IOException e) {
            log.error("Markdown 文档加载失败", e);
        }
        return allDocuments;
    }

    // ─────────── Front Matter 解析 ───────────

    /**
     * Front matter 解析结果。
     */
    private static class FrontMatter {
        boolean hasFrontMatter;
        String category = "uncategorized";
        String summary = "";
        String tags = "";
        String contentAfterFrontMatter;
    }

    /**
     * 解析 Markdown 文件开头的 YAML front matter。
     * <p>
     * 格式：
     * <pre>
     * ---
     * category: agent-basics
     * tags: [tag1, tag2]
     * summary: 一句话摘要
     * ---
     *
     * # 正文标题
     * </pre>
     */
    private FrontMatter parseFrontMatter(String rawContent) {
        FrontMatter fm = new FrontMatter();
        String content = rawContent.replace("\r\n", "\n");

        // 检查是否以 --- 开头
        if (!content.startsWith("---\n") && !content.startsWith("---\r")) {
            fm.hasFrontMatter = false;
            fm.contentAfterFrontMatter = rawContent;
            return fm;
        }

        // 查找结束的 ---
        int endIdx = content.indexOf("\n---\n", 3);
        if (endIdx == -1) {
            // 没有结束标记，当作无 front matter 处理
            fm.hasFrontMatter = false;
            fm.contentAfterFrontMatter = rawContent;
            return fm;
        }

        fm.hasFrontMatter = true;
        String fmBlock = content.substring(4, endIdx); // 跳过开头的 "---\n"
        fm.contentAfterFrontMatter = content.substring(endIdx + 5); // 跳过 "\n---\n"

        // 逐行解析 key: value
        for (String line : fmBlock.split("\n")) {
            line = line.trim();
            if (line.isEmpty()) continue;

            int colonIdx = line.indexOf(':');
            if (colonIdx == -1) continue;

            String key = line.substring(0, colonIdx).trim();
            String value = line.substring(colonIdx + 1).trim();

            switch (key) {
                case "category" -> fm.category = value;
                case "summary" -> fm.summary = value;
                case "tags" -> {
                    // 支持 [tag1, tag2] 或 tag1, tag2 格式
                    fm.tags = value.replace("[", "").replace("]", "").trim();
                }
            }
        }
        return fm;
    }
}
