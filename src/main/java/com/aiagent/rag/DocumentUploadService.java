package com.aiagent.rag;

import com.aiagent.constant.FileConstant;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 文档上传服务 — 用户上传文档的完整处理管线。
 *
 * <h3>处理流程</h3>
 * <ol>
 *   <li>校验文件（类型白名单、非空）</li>
 *   <li>保存到 {@code docs/user-uploads/} 目录（重启后重新加载）</li>
 *   <li>根据扩展名选择解析器：.md → {@link MarkdownDocumentReader}，其他 → {@link TikaDocumentReader}</li>
 *   <li>{@link DocumentSplitter} Token 级切分</li>
 *   <li>注入元数据（filename、source、fileType、uploadTimestamp、fileSize）</li>
 *   <li>{@link KeywordEnricher} LLM 关键词增强</li>
 *   <li>{@link VectorStore#add(List)} 写入向量库</li>
 *   <li>更新 {@code .index.json} 索引（用于查询和删除追踪）</li>
 * </ol>
 *
 * <h3>启动恢复</h3>
 * 通过 {@link #loadAllUserUploads()}（{@code @PostConstruct}）在应用启动时
 * 扫描 {@code docs/user-uploads/} 目录，重新加载所有用户上传的文档到向量库。
 *
 * <h3>线程安全</h3>
 * 所有变更方法加 {@code synchronized}，因为 {@link org.springframework.ai.vectorstore.SimpleVectorStore}
 * 不是线程安全的。
 */
@Component
@Slf4j
public class DocumentUploadService {

    @Resource
    private VectorStore vectorStore;

    @Resource
    private DocumentSplitter documentSplitter;

    @Resource
    private KeywordEnricher keywordEnricher;

    private static final String USER_UPLOADS_DIR = FileConstant.USER_UPLOADS_DIR;
    private static final String INDEX_FILE_NAME = ".index.json";
    private static final long MAX_FILE_SIZE = 50 * 1024 * 1024; // 50 MB

    /** 支持的文件扩展名白名单 */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "md", "txt", "pdf", "doc", "docx", "ppt", "pptx",
            "xls", "xlsx", "html", "htm", "xml", "epub", "csv", "json"
    );

    private static final MarkdownDocumentReaderConfig MARKDOWN_CONFIG =
            MarkdownDocumentReaderConfig.builder()
                    .withHorizontalRuleCreateDocument(true)
                    .withIncludeCodeBlock(false)
                    .withIncludeBlockquote(false)
                    .build();

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 内存索引缓存，key = filename，value = 索引条目 */
    private final Map<String, IndexEntry> indexCache = new ConcurrentHashMap<>();

    // ─────────── 启动恢复 ───────────

    /**
     * 应用启动后扫描 {@code docs/user-uploads/}，重新加载所有用户上传文档到向量库。
     * 每个文件独立容错：单个文件加载失败不影响其他文件。
     */
    @PostConstruct
    void loadAllUserUploads() {
        File uploadDir = new File(USER_UPLOADS_DIR);
        if (!uploadDir.exists() || !uploadDir.isDirectory()) {
            log.info("User uploads directory does not exist, skipping: {}", USER_UPLOADS_DIR);
            return;
        }
        File[] files = uploadDir.listFiles(
                (dir, name) -> !name.equals(INDEX_FILE_NAME) && !name.startsWith("."));
        if (files == null || files.length == 0) {
            log.info("No user-uploaded files found in {}", USER_UPLOADS_DIR);
            return;
        }
        log.info("Loading {} user-uploaded file(s) from {}", files.length, USER_UPLOADS_DIR);
        indexCache.clear();
        for (File file : files) {
            try {
                loadSingleFile(file);
            } catch (Exception e) {
                log.warn("Failed to load user-uploaded file '{}', skipping: {}",
                        file.getName(), e.getMessage());
            }
        }
        persistIndex();
        log.info("User uploads loaded: {} file(s) in index", indexCache.size());
    }

    /** 加载单个文件并走完整管线 */
    private void loadSingleFile(File file) throws Exception {
        String filename = file.getName();
        String extension = getExtension(filename);
        org.springframework.core.io.Resource resource = new FileSystemResource(file);
        List<Document> docs = parseResource(resource, extension);
        docs = documentSplitter.splitCustomized(docs);
        injectMetadata(docs, filename, extension, file.length());
        docs = keywordEnricher.enrichDocuments(docs);
        vectorStore.add(docs);
        List<String> chunkIds = docs.stream().map(Document::getId).toList();
        indexCache.put(filename, new IndexEntry(
                filename, extension, file.length(), chunkIds.size(), chunkIds));
        log.info("  Loaded: {} ({} chunks, type={})", filename, docs.size(), extension);
    }

    // ─────────── 上传 ───────────

    /**
     * 上传并处理一个文档文件。
     *
     * @param file 用户上传的文件（multipart/form-data）
     * @return 包含 filename、fileType、fileSize、chunkCount、uploadedAt 的 Map
     * @throws IllegalArgumentException 文件类型不支持、文件为空
     * @throws RuntimeException        文件保存/解析/向量化失败
     */
    public synchronized Map<String, Object> uploadDocument(MultipartFile file) {
        // 1. 校验
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new IllegalArgumentException("文件名为空");
        }
        if (file.isEmpty() || file.getSize() == 0) {
            throw new IllegalArgumentException("文件为空");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException(
                    "文件大小超过限制 (最大 50MB)，当前: " + (file.getSize() / 1024 / 1024) + "MB");
        }
        String extension = getExtension(originalFilename);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException(
                    "不支持的文件类型: ." + extension + "，支持: " + String.join(", ", ALLOWED_EXTENSIONS));
        }

        // 2. 确保上传目录存在
        File uploadDir = new File(USER_UPLOADS_DIR);
        if (!uploadDir.exists()) {
            uploadDir.mkdirs();
        }

        // 3. 读取文件字节（必须在 transferTo 之前，因为 transferTo 会清除临时文件）
        byte[] fileBytes;
        try {
            fileBytes = file.getBytes();
        } catch (IOException e) {
            throw new RuntimeException("文件读取失败: " + e.getMessage(), e);
        }

        // 4. 保存到磁盘
        Path targetPath = Path.of(USER_UPLOADS_DIR, originalFilename);
        try {
            Files.write(targetPath, fileBytes);
        } catch (IOException e) {
            throw new RuntimeException("文件保存失败: " + e.getMessage(), e);
        }
        log.info("File saved: {}", targetPath);

        // 5. 解析 → 切分 → 元数据注入 → 关键词增强 → 向量化
        try {
            org.springframework.core.io.Resource resource = new ByteArrayResource(fileBytes) {
                @Override
                public String getFilename() {
                    return originalFilename;
                }
            };
            List<Document> docs = parseResource(resource, extension);
            docs = documentSplitter.splitCustomized(docs);
            injectMetadata(docs, originalFilename, extension, file.getSize());
            docs = keywordEnricher.enrichDocuments(docs);
            vectorStore.add(docs);

            // 5. 更新索引
            List<String> chunkIds = docs.stream().map(Document::getId).toList();
            IndexEntry entry = new IndexEntry(
                    originalFilename, extension, file.getSize(), docs.size(), chunkIds);
            indexCache.put(originalFilename, entry);
            persistIndex();

            // 6. 构建返回结果
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("filename", originalFilename);
            result.put("fileType", extension);
            result.put("fileSize", file.getSize());
            result.put("chunkCount", docs.size());
            result.put("uploadedAt", entry.uploadedAt());
            log.info("Document uploaded: {} ({} chunks)", originalFilename, docs.size());
            return result;
        } catch (Exception e) {
            // 解析失败时清理已保存的磁盘文件
            try {
                Files.deleteIfExists(targetPath);
            } catch (IOException ignored) {
            }
            throw new RuntimeException("文件处理失败: " + e.getMessage(), e);
        }
    }

    // ─────────── 删除 ───────────

    /**
     * 删除一个用户上传的文档（从向量库和磁盘中移除）。
     *
     * @param filename 文件名（不含路径）
     * @return 包含 filename、deleted、remainingDocuments 的 Map
     * @throws IllegalArgumentException 文件不存在
     */
    public synchronized Map<String, Object> deleteDocument(String filename) {
        IndexEntry entry = indexCache.get(filename);
        if (entry == null) {
            throw new IllegalArgumentException("文件不存在: " + filename);
        }

        // 从向量库删除
        List<String> chunkIds = entry.chunkIds();
        if (chunkIds != null && !chunkIds.isEmpty()) {
            vectorStore.delete(chunkIds);
        }

        // 从磁盘删除
        Path filePath = Path.of(USER_UPLOADS_DIR, filename);
        try {
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            log.warn("Failed to delete file from disk: {}", filePath, e);
        }

        // 从索引移除
        indexCache.remove(filename);
        persistIndex();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("filename", filename);
        result.put("deleted", true);
        result.put("remainingDocuments", indexCache.size());
        log.info("Document deleted: {} (remaining: {})", filename, indexCache.size());
        return result;
    }

    // ─────────── 目录查询 ───────────

    /**
     * 获取所有用户上传文档的元数据列表。
     */
    public List<Map<String, String>> getUserUploadedCatalog() {
        List<Map<String, String>> catalog = new ArrayList<>();
        for (IndexEntry entry : indexCache.values()) {
            Map<String, String> item = new LinkedHashMap<>();
            item.put("filename", entry.filename());
            item.put("fileType", entry.fileType());
            item.put("fileSize", String.valueOf(entry.fileSize()));
            item.put("chunkCount", String.valueOf(entry.chunkCount()));
            item.put("uploadedAt", entry.uploadedAt());
            item.put("source", "user-upload");
            catalog.add(item);
        }
        return catalog;
    }

    // ─────────── 内部方法 ───────────

    /** 根据扩展名选择解析器 */
    private List<Document> parseResource(org.springframework.core.io.Resource resource, String extension) {
        if ("md".equalsIgnoreCase(extension)) {
            MarkdownDocumentReader reader = new MarkdownDocumentReader(resource, MARKDOWN_CONFIG);
            return reader.get();
        }
        // 其他格式统一用 TikaDocumentReader（PDF/DOCX/PPTX/HTML/TXT 等）
        TikaDocumentReader reader = new TikaDocumentReader(resource);
        return reader.read();
    }

    /** 为每个文档块注入元数据 */
    private void injectMetadata(List<Document> docs, String filename,
                                String extension, long fileSize) {
        String uploadedAt = Instant.now().toString();
        for (Document doc : docs) {
            doc.getMetadata().put("filename", filename);
            doc.getMetadata().put("source", "user-upload");
            doc.getMetadata().put("fileType", extension);
            doc.getMetadata().put("uploadTimestamp", uploadedAt);
            doc.getMetadata().put("fileSize", String.valueOf(fileSize));
        }
    }

    /** 提取文件扩展名（小写） */
    private String getExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot == -1 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot + 1).toLowerCase();
    }

    /** 将内存索引写出到 {@code .index.json}，格式: {@code {"files": { "a.pdf": {...}, ... }}} */
    private void persistIndex() {
        try {
            Map<String, Object> wrapper = new LinkedHashMap<>();
            wrapper.put("files", indexCache);
            objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValue(new File(USER_UPLOADS_DIR, INDEX_FILE_NAME), wrapper);
        } catch (IOException e) {
            log.error("Failed to persist index file", e);
        }
    }

    // ─────────── 索引条目 ───────────

    /**
     * {@code .index.json} 中每条文件的索引记录。
     */
    record IndexEntry(
            String filename,
            String fileType,
            long fileSize,
            int chunkCount,
            List<String> chunkIds,
            String uploadedAt
    ) {
        IndexEntry(String filename, String fileType, long fileSize,
                   int chunkCount, List<String> chunkIds) {
            this(filename, fileType, fileSize, chunkCount, chunkIds,
                    Instant.now().toString());
        }
    }
}
