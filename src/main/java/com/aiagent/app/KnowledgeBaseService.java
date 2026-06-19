package com.aiagent.app;

import com.aiagent.advisor.LoggingAdvisor;
import com.aiagent.rag.QueryRewriter;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;

@Component
@Slf4j
public class KnowledgeBaseService {

    private final ChatClient chatClient;

    private static final String SYSTEM_PROMPT = "You are a knowledgeable research assistant with access to a document repository. "
            + "Answer questions based on retrieved context. When information comes from the knowledge base, cite the source document. "
            + "If the answer cannot be found in the available documents, honestly state that and suggest alternative approaches. "
            + "Maintain a professional, helpful tone and structure your answers clearly.";

    public KnowledgeBaseService(ChatModel dashscopeChatModel) {
        MessageWindowChatMemory chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(new InMemoryChatMemoryRepository())
                .maxMessages(20)
                .build();
        chatClient = ChatClient.builder(dashscopeChatModel)
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        new LoggingAdvisor()
                )
                .build();
    }

    // ==================== 基础对话 ====================

    public String doChat(String message, String chatId) {
        ChatResponse chatResponse = chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }

    public Flux<String> doChatByStream(String message, String chatId) {
        return chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .stream()
                .content();
    }

    // ==================== RAG 知识库问答 ====================

    @Resource
    private VectorStore vectorStore;

    @Resource
    private Advisor ragCloudAdvisor;

    @Autowired(required = false)
    private VectorStore pgVectorVectorStore;

    @Resource
    private QueryRewriter queryRewriter;

    /**
     * RAG 知识库对话（同步）
     */
    public String doChatWithRag(String message, String chatId) {
        String rewrittenMessage = queryRewriter.doQueryRewrite(message);
        ChatResponse chatResponse = chatClient
                .prompt()
                .user(rewrittenMessage)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .advisors(new LoggingAdvisor())
                .advisors(new QuestionAnswerAdvisor(vectorStore))
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        log.info("RAG response: {}", content);
        return content;
    }

    /**
     * RAG 知识库对话（SSE 流式）
     */
    public Flux<String> doChatWithRagByStream(String message, String chatId) {
        String rewrittenMessage = queryRewriter.doQueryRewrite(message);
        return chatClient
                .prompt()
                .user(rewrittenMessage)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .advisors(new LoggingAdvisor())
                .advisors(new QuestionAnswerAdvisor(vectorStore))
                .stream()
                .content();
    }

    // ==================== 工具调用 & MCP ====================

    @Resource
    private ToolCallback[] allTools;

    public String doChatWithTools(String message, String chatId) {
        ChatResponse chatResponse = chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .advisors(new LoggingAdvisor())
                .toolCallbacks(allTools)
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }

    @Autowired(required = false)
    private ToolCallbackProvider toolCallbackProvider;

    public String doChatWithMcp(String message, String chatId) {
        ChatResponse chatResponse = chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .advisors(new LoggingAdvisor())
                .toolCallbacks(toolCallbackProvider)
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }

    record ResearchSummary(String title, List<String> suggestions) {
    }

    public ResearchSummary doChatWithReport(String message, String chatId) {
        ResearchSummary researchSummary = chatClient
                .prompt()
                .system(SYSTEM_PROMPT + " After each conversation, generate a research summary with title '{username} Research Report' and a list of suggestions.")
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .call()
                .entity(ResearchSummary.class);
        log.info("researchSummary: {}", researchSummary);
        return researchSummary;
    }
}
