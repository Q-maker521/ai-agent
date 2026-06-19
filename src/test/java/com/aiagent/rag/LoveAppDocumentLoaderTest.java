package com.aiagent.rag;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class KnowledgeDocumentLoaderTest {

    @Resource
    private KnowledgeDocumentLoader knowledgeBaseServiceDocumentLoader;

    @Test
    void loadMarkdowns() {
        knowledgeBaseServiceDocumentLoader.loadMarkdowns();
    }
}