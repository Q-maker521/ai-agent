package com.aiagent.rag;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Collections;
import java.util.List;

/**
 * 向量数据库配置。
 * <p>
 * 如果 embedding API 调用失败（如免费额度耗尽），优雅降级为空向量库，
 * 不影响 Agent 等核心功能正常启动。
 */
@Configuration
@Slf4j
public class VectorStoreConfig {

    @Resource
    private KnowledgeDocumentLoader knowledgeDocumentLoader;

    @Resource
    private DocumentSplitter documentSplitter;

    @Resource
    private KeywordEnricher keywordEnricher;

    @Value("${app.rag.index-on-startup:true}")
    private boolean indexOnStartup;

    @Bean
    VectorStore vectorStore(EmbeddingModel dashscopeEmbeddingModel) {
        SimpleVectorStore simpleVectorStore = SimpleVectorStore.builder(dashscopeEmbeddingModel).build();
        if (!indexOnStartup) {
            log.warn("Vector store startup indexing is disabled. RAG retrieval will use an empty vector store.");
            return simpleVectorStore;
        }
        try {
            List<Document> documentList = knowledgeDocumentLoader.loadMarkdowns();
            List<Document> enrichedDocuments = keywordEnricher.enrichDocuments(documentList);
            simpleVectorStore.add(enrichedDocuments);
            log.info("Vector store initialized with {} documents", enrichedDocuments.size());
        } catch (Exception e) {
            log.warn("Failed to initialize vector store documents (embedding API may be unavailable): {}",
                    e.getMessage());
            // 优雅降级：返回空向量库，应用仍可正常启动
        }
        return simpleVectorStore;
    }
}
