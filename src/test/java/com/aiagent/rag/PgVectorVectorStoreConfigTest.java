package com.aiagent.rag;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

@SpringBootTest
class PgVectorVectorStoreConfigTest {

    @Resource
    private VectorStore pgVectorVectorStore;

    @Test
    void pgVectorVectorStore() {
        List<Document> documents = List.of(
                new Document("AI Agent 项目可以用于学习工具调用、RAG 和工程化部署", Map.of("meta1", "meta1")),
                new Document("Spring AI 可以帮助 Java 项目接入大模型、Embedding 和向量检索"),
                new Document("ReAct 模式通过推理和行动循环提升复杂任务处理能力", Map.of("meta2", "meta2")));
        // 添加文档
        pgVectorVectorStore.add(documents);
        // 相似度查询
        List<Document> results = pgVectorVectorStore.similaritySearch(SearchRequest.builder().query("怎么学习 AI Agent 工程化").topK(3).build());
        Assertions.assertNotNull(results);
    }
}
