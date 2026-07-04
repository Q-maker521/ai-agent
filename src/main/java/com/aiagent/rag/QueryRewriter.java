package com.aiagent.rag;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.transformation.QueryTransformer;
import org.springframework.ai.rag.preretrieval.query.transformation.RewriteQueryTransformer;
import org.springframework.stereotype.Component;

/**
 * 查询重写器
 */
@Component
public class QueryRewriter {

    private final QueryTransformer defaultQueryTransformer;
    private final ChatClient.Builder defaultChatClientBuilder;

    public QueryRewriter(ChatModel dashscopeChatModel) {
        this.defaultChatClientBuilder = ChatClient.builder(dashscopeChatModel);
        this.defaultQueryTransformer = RewriteQueryTransformer.builder()
                .chatClientBuilder(defaultChatClientBuilder)
                .build();
    }

    /**
     * 使用默认 ChatModel（DashScope）执行查询重写。
     */
    public String doQueryRewrite(String prompt) {
        return doQueryRewrite(prompt, null);
    }

    /**
     * 使用自定义 ChatModel 执行查询重写。
     *
     * @param prompt    用户原始提问
     * @param chatModel 自定义模型（null 则使用默认 DashScope）
     */
    public String doQueryRewrite(String prompt, ChatModel chatModel) {
        QueryTransformer transformer;
        if (chatModel != null) {
            transformer = RewriteQueryTransformer.builder()
                    .chatClientBuilder(ChatClient.builder(chatModel))
                    .build();
        } else {
            transformer = this.defaultQueryTransformer;
        }
        Query query = new Query(prompt);
        Query transformedQuery = transformer.transform(query);
        return transformedQuery.text();
    }
}
