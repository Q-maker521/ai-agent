package com.aiagent.controller;

import com.aiagent.agent.DevAssistantAgent;
import com.aiagent.app.KnowledgeBaseService;
import com.aiagent.config.DynamicChatModelFactory;
import com.aiagent.config.ModelConfig;
import com.aiagent.rag.KnowledgeDocumentLoader;
import com.aiagent.session.AgentSession;
import com.aiagent.session.AgentSessionManager;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;

@RestController
@RequestMapping("/ai")
@Slf4j
public class AiController {

    @Resource
    private KnowledgeBaseService knowledgeBaseService;

    @Resource
    private AgentSessionManager sessionManager;

    @Resource
    private ToolCallback[] allTools;

    @Resource
    private ChatModel dashscopeChatModel;

    @Resource
    private DynamicChatModelFactory chatModelFactory;

    @Resource
    private KnowledgeDocumentLoader documentLoader;

    @Resource
    private com.aiagent.agent.plan.SimplePlanner planner;

    /**
     * 获取知识库文档目录（分类、标签、摘要）。
     */
    @GetMapping("/rag/documents")
    public java.util.List<java.util.Map<String, String>> getRagDocuments() {
        return documentLoader.getDocumentCatalog();
    }

    /**
     * 根据 sessionId 解析 RAG 使用的 ChatModel（用户配置的 Provider），
     * 未配置则返回 null，由 KnowledgeBaseService 回退到默认 DashScope。
     */
    private ChatModel resolveRagChatModel(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) return null;
        AgentSession session = sessionManager.getSession(sessionId);
        if (session == null) return null;
        ModelConfig config = session.getModelConfig();
        if (config == null || !config.isValid()) return null;
        return chatModelFactory.createChatModel(config);
    }

    /**
     * 同步调用 RAG 知识库问答
     */
    @GetMapping("/rag/chat/sync")
    public String doChatWithRagSync(
            @RequestParam String message,
            @RequestParam String chatId,
            @RequestParam(required = false) String sessionId) {
        ChatModel customModel = resolveRagChatModel(sessionId);
        return knowledgeBaseService.doChatWithRag(message, chatId, customModel);
    }

    /**
     * SSE 流式调用 RAG 知识库问答
     */
    @GetMapping(value = "/rag/chat/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> doChatWithRagSSE(
            @RequestParam String message,
            @RequestParam String chatId,
            @RequestParam(required = false) String sessionId) {
        ChatModel customModel = resolveRagChatModel(sessionId);
        return knowledgeBaseService.doChatWithRagByStream(message, chatId, customModel);
    }

    /**
     * SSE Emitter 方式调用 RAG 知识库问答
     */
    @GetMapping(value = "/rag/chat/sse_emitter")
    public SseEmitter doChatWithRagSseEmitter(
            @RequestParam String message,
            @RequestParam String chatId,
            @RequestParam(required = false) String sessionId) {
        SseEmitter sseEmitter = new SseEmitter(180000L); // 3 分钟超时
        ChatModel customModel = resolveRagChatModel(sessionId);
        knowledgeBaseService.doChatWithRagByStream(message, chatId, customModel)
                .subscribe(chunk -> {
                    try {
                        sseEmitter.send(chunk);
                    } catch (IOException e) {
                        sseEmitter.completeWithError(e);
                    }
                }, sseEmitter::completeWithError, sseEmitter::complete);
        return sseEmitter;
    }

    /**
     * AI 智能研发助手 — 基于 ReAct 模式的自主规划 Agent（纯文本流）
     * <p>
     * 支持 sessionId 参数进行会话复用，保持跨请求的对话记忆。
     * 不传 sessionId 则自动创建新会话。
     */
    @GetMapping("/agent/chat")
    public SseEmitter doChatWithAgent(String message, String sessionId) {
        AgentSession session = sessionManager.getOrCreateSession(sessionId);
        return session.getAgent().runStream(message);
    }

    /**
     * AI 智能研发助手 — 结构化 JSON 事件流（用于前端可视化 + 会话管理）
     * <p>
     * 每个 SSE data 行是一个 AgentEvent JSON 对象，包含 Agent 在每一步的
     * thinking、tool_call、tool_result 等结构化信息。
     * 支持 sessionId 会话复用。
     */
    @GetMapping(value = "/agent/chat/stream")
    public SseEmitter doChatWithAgentStream(String message, String sessionId) {
        AgentSession session = sessionManager.getOrCreateSession(sessionId);
        DevAssistantAgent agent = session.getAgent();

        // 新会话时生成执行计划（消息历史只有当前用户消息）
        if (agent.getMessageList().size() <= 1) {
            try {
                var plan = planner.plan(message, agent.getChatClient());
                if (plan != null && plan.steps() != null && !plan.steps().isEmpty()) {
                    String planText = planner.formatPlanForPrompt(plan);
                    agent.setPlanIntoPrompt(planText);
                }
            } catch (Exception e) {
                // 规划失败不阻塞 Agent 执行
                log.warn("Planning failed for session {}: {}", sessionId, e.getMessage());
            }
        }

        return agent.runStreamStructured(message);
    }


    /**
     * 取消正在执行的 Agent 任务。
     * 前端按 Esc 键时调用此端点。
     */
    @PostMapping("/agent/cancel")
    public java.util.Map<String, Object> cancelAgent(@RequestParam String sessionId) {
        sessionManager.requestCancel(sessionId);
        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("sessionId", sessionId);
        result.put("cancelled", true);
        return result;
    }

    /**
     * 获取当前活跃会话数量（用于监控）
     */
    @GetMapping("/agent/sessions/count")
    public int getActiveSessionCount() {
        return sessionManager.getActiveSessionCount();
    }

    /**
     * [调试/管理] 导出指定会话的完整对话上下文。
     * <p>
     * 优先查内存中的活跃 session，找不到则从磁盘持久化文件恢复。
     * 不传 sessionId 则仅返回当前内存 + 磁盘的会话 ID 列表。
     */
    @GetMapping("/agent/sessions/context")
    public java.util.Map<String, Object> getSessionContext(String sessionId) {
        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();

        // 合并内存和磁盘的会话 ID 列表
        java.util.Set<String> allIds = new java.util.LinkedHashSet<>();
        allIds.addAll(sessionManager.getSessionIds());
        allIds.addAll(sessionManager.getPersistedSessionIds());
        result.put("sessionIds", allIds);

        if (sessionId != null) {
            result.put("sessionId", sessionId);
            AgentSession session = sessionManager.getSession(sessionId);
            if (session != null) {
                // 内存中活跃：直接读取
                DevAssistantAgent agent = session.getAgent();
                result.put("source", "memory");
                result.put("agentState", agent.getState().name());
                result.put("currentStep", agent.getCurrentStep());
                result.put("maxSteps", agent.getMaxSteps());
                result.put("messageCount", agent.getMessageList().size());
                result.put("messages", buildMessageList(agent.getMessageList()));
            } else {
                // 内存中不存在，尝试从磁盘恢复
                java.util.List<org.springframework.ai.chat.messages.Message> diskMessages =
                        sessionManager.getPersistedMessages(sessionId);
                if (!diskMessages.isEmpty()) {
                    result.put("source", "disk");
                    result.put("agentState", "IDLE");
                    result.put("messageCount", diskMessages.size());
                    result.put("messages", buildMessageList(diskMessages));
                } else {
                    result.put("source", "none");
                    result.put("messageCount", 0);
                    result.put("messages", java.util.Collections.emptyList());
                }
            }
        }
        return result;
    }

    private java.util.List<java.util.Map<String, Object>> buildMessageList(
            java.util.List<org.springframework.ai.chat.messages.Message> messages) {
        java.util.List<java.util.Map<String, Object>> result = new java.util.ArrayList<>();
        for (var msg : messages) {
            java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("type", msg.getMessageType().name());
            m.put("text", msg.getText());
            result.add(m);
        }
        return result;
    }

    // ==================== 会话管理端点 ====================

    /**
     * 列出所有会话（内存活跃 + 磁盘历史）。
     */
    @GetMapping("/agent/sessions")
    public java.util.List<com.aiagent.session.AgentSessionManager.SessionMeta> listSessions() {
        return sessionManager.listSessions();
    }

    /**
     * 获取单个会话详情（含消息列表）。
     */
    @GetMapping("/agent/sessions/{sessionId}")
    public java.util.Map<String, Object> getSessionDetail(@PathVariable String sessionId) {
        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        AgentSession session = sessionManager.getSession(sessionId);
        if (session != null) {
            DevAssistantAgent agent = session.getAgent();
            result.put("sessionId", sessionId);
            result.put("source", "memory");
            result.put("agentState", agent.getState().name());
            result.put("messageCount", agent.getMessageList().size());
            result.put("messages", buildMessageList(agent.getMessageList()));
        } else {
            java.util.List<org.springframework.ai.chat.messages.Message> diskMessages =
                    sessionManager.getPersistedMessages(sessionId);
            if (!diskMessages.isEmpty()) {
                result.put("sessionId", sessionId);
                result.put("source", "disk");
                result.put("agentState", "IDLE");
                result.put("messageCount", diskMessages.size());
                result.put("messages", buildMessageList(diskMessages));
            }
        }
        return result;
    }

    /**
     * 删除会话（内存 + 磁盘文件）。
     */
    @DeleteMapping("/agent/sessions/{sessionId}")
    public java.util.Map<String, Object> deleteSession(@PathVariable String sessionId) {
        sessionManager.deleteSession(sessionId);
        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("sessionId", sessionId);
        result.put("deleted", true);
        return result;
    }

    /**
     * 更新会话元数据（如重命名标题）。
     */
    @PatchMapping("/agent/sessions/{sessionId}")
    public java.util.Map<String, Object> updateSession(
            @PathVariable String sessionId,
            @RequestBody java.util.Map<String, Object> body) {
        String title = (String) body.get("title");
        if (title != null && !title.isBlank()) {
            sessionManager.updateSessionMeta(sessionId, title);
        }
        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("sessionId", sessionId);
        result.put("updated", true);
        return result;
    }
}
