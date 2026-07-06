package com.aiagent.rag;

import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;

/**
 * 创建上下文查询增强器的工厂。
 * <p>
 * 通过 {@code allowEmptyContext(false)} + 自定义 {@code emptyContextPromptTemplate}，
 * 当向量库检索为空时强制 LLM 诚实告知而非凭空编造答案。
 *
 * @see RetrievalAugmentationAdvisor
 */
public class ContextualQueryAugmenterFactory {

    /**
     * @param emptyContextMessage 检索为空时注入的兜底提示（会渲染为 PromptTemplate）
     */
    public static ContextualQueryAugmenter createInstance(String emptyContextMessage) {
        PromptTemplate emptyContextPromptTemplate = new PromptTemplate(emptyContextMessage);
        return ContextualQueryAugmenter.builder()
                .allowEmptyContext(false)  // 不允许空上下文通过：注入兜底提示
                .emptyContextPromptTemplate(emptyContextPromptTemplate)
                .build();
    }
}
