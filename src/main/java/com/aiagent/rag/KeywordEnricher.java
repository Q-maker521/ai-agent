package com.aiagent.rag;

import com.aiagent.config.DefaultChatModelResolver;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.model.transformer.KeywordMetadataEnricher;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 基于 AI 的文档元信息增强器（为文档补充元信息）
 */
@Component
public class KeywordEnricher {

    @Resource
    private DefaultChatModelResolver defaultChatModelResolver;

    public List<Document> enrichDocuments(List<Document> documents) {
        ChatModel chatModel = defaultChatModelResolver.resolve();
        KeywordMetadataEnricher keywordMetadataEnricher = new KeywordMetadataEnricher(chatModel, 5);
        return  keywordMetadataEnricher.apply(documents);
    }
}
