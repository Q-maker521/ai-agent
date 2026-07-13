package com.aiagent.app;

import com.aiagent.advisor.LoggingAdvisor;
import com.aiagent.chatmemory.FileBasedChatMemory;
import com.aiagent.config.DefaultChatModelResolver;
import com.aiagent.rag.QueryRewriter;
import com.aiagent.rag.RagCustomAdvisorFactory;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class KnowledgeBaseService {

    private final ChatClient defaultChatClient;
    private final ChatModel defaultChatModel;

    /** 会话级 ChatClient 缓存（按 sessionId 隔离，各自持有独立的 ChatMemory） */
    private final Map<String, ChatClient> sessionClients = new ConcurrentHashMap<>();

    private static final String SYSTEM_PROMPT = "You are a knowledgeable research assistant with access to a document repository. "
            + "Answer questions based on retrieved context. When information comes from the knowledge base, cite the source document. "
            + "If the answer cannot be found in the available documents, honestly state that and suggest alternative approaches. "
            + "Maintain a professional, helpful tone and structure your answers clearly.";

    /** RAG 检索相似度阈值 — 低于此值的文档不检索（DashScope Embedding 相似度分布 0.3-0.7） */
    private static final double RAG_SIMILARITY_THRESHOLD = 0.4;
    /** RAG 检索参数 — 每次检索返回的最大文档数 */
    private static final int RAG_TOP_K = 5;
    /** 检索结果为空时注入 system prompt 的兜底消息 */
    private static final String EMPTY_CONTEXT_MESSAGE = "重要：知识库检索未找到与此问题相关的任何文档。"
            + "你必须直接告诉用户'知识库中未找到相关信息'，不得基于你自己的训练数据回答此问题。"
            + "同时建议用户：1) 换一种方式提问 2) 使用更具体的关键词 3) 切换到 Agent 模式。"
            + "【禁止编造答案】";

    public KnowledgeBaseService(DefaultChatModelResolver defaultChatModelResolver) {
        this.defaultChatModel = defaultChatModelResolver.resolve();
        FileBasedChatMemory persistentMemory = new FileBasedChatMemory(
                System.getProperty("user.dir") + "/tmp/rag-memory");
        MessageWindowChatMemory chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(persistentMemory)
                .maxMessages(20)
                .build();
        defaultChatClient = ChatClient.builder(this.defaultChatModel)
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        new LoggingAdvisor()
                )
                .build();
    }

    // ==================== 基础对话 ====================

    public String doChat(String message, String chatId) {
        ChatResponse chatResponse = defaultChatClient
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
        return defaultChatClient
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
    
    private QueryRewriter queryRewriter;

    /**
     * RAG 知识库对话（同步）— 使用默认 DashScope。
     */
    public String doChatWithRag(String message, String chatId) {
        return doChatWithRag(message, chatId, null);
    }

    /**
     * RAG 知识库对话（同步）— 使用指定 ChatModel。
     *
     * @param message   用户消息
     * @param chatId    对话 ID（用于记忆隔离）
     * @param chatModel 自定义模型（null 则使用默认 DashScope）
     */
    public String doChatWithRag(String message, String chatId, ChatModel chatModel) {
        ChatClient client = resolveChatClient(chatId, chatModel);
        String rewrittenMessage = queryRewriter.doQueryRewrite(message, chatModel);
        ChatResponse chatResponse = client
                .prompt()
                .user(rewrittenMessage)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .advisors(new LoggingAdvisor())
                .advisors(RagCustomAdvisorFactory.createRagAdvisor(
                        vectorStore, RAG_TOP_K, RAG_SIMILARITY_THRESHOLD, EMPTY_CONTEXT_MESSAGE))
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        log.info("RAG response: {}", content);
        return content;
    }

    /**
     * RAG 知识库对话（SSE 流式）— 使用默认 DashScope。
     */
    public Flux<String> doChatWithRagByStream(String message, String chatId) {
        return doChatWithRagByStream(message, chatId, null);
    }

    /**
     * RAG 知识库对话（SSE 流式）— 使用指定 ChatModel。
     *
     * @param message   用户消息
     * @param chatId    对话 ID（用于记忆隔离）
     * @param chatModel 自定义模型（null 则使用默认 DashScope）
     */
    public Flux<String> doChatWithRagByStream(String message, String chatId, ChatModel chatModel) {
        ChatClient client = resolveChatClient(chatId, chatModel);
        String rewrittenMessage = queryRewriter.doQueryRewrite(message, chatModel);
        return client
                .prompt()
                .user(rewrittenMessage)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .advisors(new LoggingAdvisor())
                .advisors(RagCustomAdvisorFactory.createRagAdvisor(
                        vectorStore, RAG_TOP_K, RAG_SIMILARITY_THRESHOLD, EMPTY_CONTEXT_MESSAGE))
                .stream()
                .content();
    }

    /**
     * 解析要使用的 ChatClient：有自定义 ChatModel 时创建会话级 client（含独立记忆），
     * 否则回退到全局默认 client。
     */
    private ChatClient resolveChatClient(String chatId, ChatModel chatModel) {
        if (chatModel != null) {
            // 按 chatId 缓存，确保同一 RAG 会话的对话记忆连续
            return sessionClients.computeIfAbsent(chatId, id -> {
                FileBasedChatMemory persistentMemory = new FileBasedChatMemory(
                        System.getProperty("user.dir") + "/tmp/rag-memory");
                MessageWindowChatMemory memory = MessageWindowChatMemory.builder()
                        .chatMemoryRepository(persistentMemory)
                        .maxMessages(20)
                        .build();
                return ChatClient.builder(chatModel)
                        .defaultSystem(SYSTEM_PROMPT)
                        .defaultAdvisors(
                                MessageChatMemoryAdvisor.builder(memory).build(),
                                new LoggingAdvisor()
                        )
                        .build();
            });
        }
        return defaultChatClient;
    }

    // ==================== 工具调用 & MCP ====================

    @Resource
    private ToolCallback[] allTools;

    public String doChatWithTools(String message, String chatId) {
        ChatResponse chatResponse = defaultChatClient
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
        ChatResponse chatResponse = defaultChatClient
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
        ResearchSummary researchSummary = defaultChatClient
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
