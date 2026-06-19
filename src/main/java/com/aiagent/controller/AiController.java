package com.aiagent.controller;

import com.aiagent.agent.DevAssistantAgent;
import com.aiagent.app.KnowledgeBaseService;
import com.aiagent.session.AgentSession;
import com.aiagent.session.AgentSessionManager;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;

@RestController
@RequestMapping("/ai")
public class AiController {

    @Resource
    private KnowledgeBaseService knowledgeBaseService;

    @Resource
    private AgentSessionManager sessionManager;

    @Resource
    private ToolCallback[] allTools;

    @Resource
    private ChatModel dashscopeChatModel;

    /**
     * 同步调用 RAG 知识库问答
     */
    @GetMapping("/rag/chat/sync")
    public String doChatWithRagSync(String message, String chatId) {
        return knowledgeBaseService.doChatWithRag(message, chatId);
    }

    /**
     * SSE 流式调用 RAG 知识库问答
     */
    @GetMapping(value = "/rag/chat/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> doChatWithRagSSE(String message, String chatId) {
        return knowledgeBaseService.doChatWithRagByStream(message, chatId);
    }

    /**
     * SSE Emitter 方式调用 RAG 知识库问答
     */
    @GetMapping(value = "/rag/chat/sse_emitter")
    public SseEmitter doChatWithRagSseEmitter(String message, String chatId) {
        SseEmitter sseEmitter = new SseEmitter(180000L); // 3 分钟超时
        knowledgeBaseService.doChatWithRagByStream(message, chatId)
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
        return session.getAgent().runStreamStructured(message);
    }

    /**
     * 获取当前活跃会话数量（用于监控）
     */
    @GetMapping("/agent/sessions/count")
    public int getActiveSessionCount() {
        return sessionManager.getActiveSessionCount();
    }
}
