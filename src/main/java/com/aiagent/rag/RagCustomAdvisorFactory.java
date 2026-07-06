package com.aiagent.rag;

import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;

/**
 * RAG 检索增强 Advisor 工厂。
 * <p>
 * 基于 Spring AI 模块化 RAG 架构，将文档检索 + 查询增强组装为单个
 * {@link RetrievalAugmentationAdvisor}，替代旧的
 * {@code QuestionAnswerAdvisor} + {@code EmptyContextAdvisor} 组合。
 * <p>
 * 设计意图：
 * <ul>
 *   <li><b>一次检索</b>：{@link VectorStoreDocumentRetriever} 负责向量检索，结果在
 *       Adivsor 内部流转，消除旧方案的双次查询</li>
 *   <li><b>空上下文兜底</b>：{@link ContextualQueryAugmenterFactory} 在检索为空时
 *       注入兜底提示，禁止 LLM 基于训练数据编造答案</li>
 *   <li><b>可扩展</b>：后续可通过 {@code RetrievalAugmentationAdvisor.Builder}
 *       添加 {@code documentPostProcessors}、自定义 {@code queryTransformer} 等</li>
 * </ul>
 */
public class RagCustomAdvisorFactory {

    /**
     * 创建组装好的 RAG Advisor。
     *
     * @param vectorStore         向量库
     * @param topK                每次检索返回的最大文档数
     * @param similarityThreshold 相似度阈值（低于此值的文档不检索）
     * @param emptyContextMessage 检索为空时注入的兜底提示
     * @return 组装好的 Advisor，可直接传入 {@code ChatClient.advisors()}
     */
    public static Advisor createRagAdvisor(VectorStore vectorStore,
                                           int topK,
                                           double similarityThreshold,
                                           String emptyContextMessage) {
        DocumentRetriever documentRetriever = VectorStoreDocumentRetriever.builder()
                .vectorStore(vectorStore)
                .similarityThreshold(similarityThreshold)
                .topK(topK)
                .build();
        return RetrievalAugmentationAdvisor.builder()
                .documentRetriever(documentRetriever)
                .queryAugmenter(ContextualQueryAugmenterFactory.createInstance(emptyContextMessage))
                .build();
    }
}
