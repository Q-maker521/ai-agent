package com.aiagent.agent;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.aiagent.agent.event.AgentEvent;
import com.aiagent.agent.model.AgentState;
import com.aiagent.chatmemory.FileBasedChatMemory;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * 抽象基础代理类，管理 Agent 状态机、步骤循环和消息上下文。
 *
 * <h3>状态机</h3>
 * <pre>
 *   IDLE → RUNNING → FINISHED
 *                  → ERROR
 * </pre>
 *
 * <h3>步骤循环</h3>
 * 每一步调用子类实现的 {@link #step()} 方法，直到状态变为 FINISHED
 * 或达到 maxSteps 上限。支持两种输出模式：
 * <ul>
 *   <li>{@link #run(String)} — 同步模式，返回完整结果文本</li>
 *   <li>{@link #runStream(String)} — SSE 流式模式，逐步推送纯文本结果</li>
 *   <li>{@link #runStreamStructured(String)} — SSE 流式模式，推送结构化 JSON 事件（用于前端可视化）</li>
 * </ul>
 */
@Data
@Slf4j
public abstract class BaseAgent {

    // 核心属性
    private String name;

    // 提示词
    private String systemPrompt;
    private String nextStepPrompt;

    // 代理状态
    private AgentState state = AgentState.IDLE;

    // 执行步骤控制
    private int currentStep = 0;
    private int maxSteps = 10;

    // LLM 大模型
    private ChatClient chatClient;

    // Memory 记忆（需要自主维护会话上下文）
    private List<Message> messageList = new ArrayList<>();

    // 会话 ID，同时也是持久化的文件名 key
    private String sessionId;

    // 文件持久化记忆（Kryo 序列化到磁盘）
    private FileBasedChatMemory chatMemory;

    // 持久化后回调（由 AgentSessionManager 注入，用于同步更新 .meta.json）
    private Runnable onPersistCallback;

    // Agent 生命周期结束回调（由 AgentSessionManager 注入，用于清理 cancelFlags 等）
    private Runnable onCleanupCallback;

    // 取消标志（由 AgentSessionManager 设置，Esc 中断时用）
    private volatile boolean cancelled = false;

    /**
     * 事件发射器 — 子类在 think/act 过程中调用此 Consumer 发射结构化事件。
     * 由 runStreamStructured() 在运行时注入。
     */
    protected Consumer<AgentEvent> eventSink;

    /**
     * 运行代理（同步模式）
     */
    public String run(String userPrompt) {
        if (this.state != AgentState.IDLE) {
            throw new RuntimeException("Cannot run agent from state: " + this.state);
        }
        if (StrUtil.isBlank(userPrompt)) {
            throw new RuntimeException("Cannot run agent with empty user prompt");
        }
        this.state = AgentState.RUNNING;
        this.cancelled = false;
        messageList.add(new UserMessage(userPrompt));
        List<String> results = new ArrayList<>();
        try {
            for (int i = 0; i < maxSteps && state != AgentState.FINISHED; i++) {
                int stepNumber = i + 1;
                currentStep = stepNumber;
                log.info("Executing step {}/{}", stepNumber, maxSteps);
                if (cancelled) {
                    results.add("任务已被用户取消");
                    state = AgentState.FINISHED;
                    break;
                }
                String stepResult = step();
                // 自动终止时 stepResult 为 null，跳过记录避免 "null" 文本
                if (stepResult != null) {
                    String result = "Step " + stepNumber + ": " + stepResult;
                    results.add(result);
                }
                persistMessages();
            }
            if (currentStep >= maxSteps) {
                state = AgentState.FINISHED;
                results.add("Terminated: Reached max steps (" + maxSteps + ")");
            }
            persistMessages();
            return String.join("\n", results);
        } catch (Exception e) {
            state = AgentState.ERROR;
            log.error("error executing agent", e);
            persistMessages();
            return "执行错误" + e.getMessage();
        } finally {
            this.cleanup();
        }
    }

    /**
     * 运行代理（SSE 流式输出纯文本）
     */
    public SseEmitter runStream(String userPrompt) {
        SseEmitter sseEmitter = new SseEmitter(300000L); // 5 分钟超时
        ScheduledExecutorService heartbeatExecutor = Executors.newSingleThreadScheduledExecutor();
        ScheduledFuture<?> heartbeat = heartbeatExecutor.scheduleAtFixedRate(() -> {
            try { sseEmitter.send(SseEmitter.event().comment("ping"));
            } catch (IOException ignored) { /* connection closed */ }
        }, 60, 60, TimeUnit.SECONDS);

        CompletableFuture.runAsync(() -> {
            try {
                if (this.state != AgentState.IDLE) {
                    sendTextEvent(sseEmitter, "错误：无法从状态运行代理：" + this.state);
                    sseEmitter.complete();
                    return;
                }
                if (StrUtil.isBlank(userPrompt)) {
                    sendTextEvent(sseEmitter, "错误：不能使用空提示词运行代理");
                    sseEmitter.complete();
                    return;
                }
            } catch (Exception e) {
                sseEmitter.completeWithError(e);
                return;
            }
            this.state = AgentState.RUNNING;
            this.cancelled = false;
            messageList.add(new UserMessage(userPrompt));
            List<String> results = new ArrayList<>();
            try {
                for (int i = 0; i < maxSteps && state != AgentState.FINISHED; i++) {
                    int stepNumber = i + 1;
                    currentStep = stepNumber;
                    log.info("Executing step {}/{}", stepNumber, maxSteps);
                    if (cancelled) {
                        sendTextEvent(sseEmitter, "任务已被用户取消 (Esc)");
                        state = AgentState.FINISHED;
                        break;
                    }
                    String stepResult = step();
                    // 自动终止时 stepResult 为 null，跳过发送避免 "null" 文本
                    if (stepResult != null) {
                        String result = "Step " + stepNumber + ": " + stepResult;
                        results.add(result);
                        sendTextEvent(sseEmitter, result);
                    }
                    // Agent 完成时立即发送 final_answer（与 structured stream 对齐）
                    if (getState() == AgentState.FINISHED) {
                        String finalText = getFinalAnswerText();
                        if (StrUtil.isNotBlank(finalText)) {
                            sendTextEvent(sseEmitter, finalText);
                        }
                        break;
                    }
                    persistMessages();
                }
                if (currentStep >= maxSteps && state != AgentState.FINISHED) {
                    state = AgentState.FINISHED;
                    sendTextEvent(sseEmitter, "执行结束：达到最大步骤（" + maxSteps + "）");
                }
                persistMessages();
                sseEmitter.complete();
            } catch (Exception e) {
                state = AgentState.ERROR;
                log.error("error executing agent", e);
                persistMessages();
                sendTextEvent(sseEmitter, "执行错误：" + e.getMessage());
                sseEmitter.complete();
            } finally {
                stopHeartbeat(heartbeat, heartbeatExecutor);
                this.cleanup();
            }
        });

        sseEmitter.onTimeout(() -> {
            this.state = AgentState.ERROR;
            stopHeartbeat(heartbeat, heartbeatExecutor);
            this.cleanup();
            log.warn("SSE connection timeout");
        });
        sseEmitter.onCompletion(() -> {
            if (this.state == AgentState.RUNNING) {
                this.state = AgentState.FINISHED;
            }
            stopHeartbeat(heartbeat, heartbeatExecutor);
            this.cleanup();
            log.info("SSE connection completed");
        });
        return sseEmitter;
    }

    /**
     * 运行代理（SSE 流式输出结构化 JSON 事件）
     * <p>
     * 每个 SSE data 行是一个序列化的 {@link AgentEvent} JSON 对象，
     * 前端可解析为结构化的 Agent 执行过程并渲染为思考链可视化。
     */
    public SseEmitter runStreamStructured(String userPrompt) {
        SseEmitter sseEmitter = new SseEmitter(300000L);
        ScheduledExecutorService heartbeatExecutor = Executors.newSingleThreadScheduledExecutor();
        ScheduledFuture<?> heartbeat = heartbeatExecutor.scheduleAtFixedRate(() -> {
            try { sseEmitter.send(SseEmitter.event().comment("ping"));
            } catch (IOException ignored) { /* connection closed */ }
        }, 60, 60, TimeUnit.SECONDS);

        CompletableFuture.runAsync(() -> {
            try {
                if (this.state != AgentState.IDLE) {
                    sendJsonEvent(sseEmitter, AgentEvent.agentError(0, "Agent not in IDLE state: " + this.state));
                    sseEmitter.complete();
                    return;
                }
                if (StrUtil.isBlank(userPrompt)) {
                    sendJsonEvent(sseEmitter, AgentEvent.agentError(0, "Empty user prompt"));
                    sseEmitter.complete();
                    return;
                }
            } catch (Exception e) {
                sseEmitter.completeWithError(e);
                return;
            }
            this.state = AgentState.RUNNING;
            this.cancelled = false;
            messageList.add(new UserMessage(userPrompt));

            // 注入事件发射器，子类通过它 emit 事件
            this.eventSink = event -> sendJsonEvent(sseEmitter, event);

            try {
                // 发射 Agent 启动事件
                sendJsonEvent(sseEmitter, AgentEvent.agentStarted(maxSteps));

                for (int i = 0; i < maxSteps && state != AgentState.FINISHED; i++) {
                    int stepNumber = i + 1;
                    currentStep = stepNumber;
                    log.info("Executing step {}/{}", stepNumber, maxSteps);

                    // 检查是否被取消（Esc 中断）
                    if (cancelled) {
                        log.info("Agent {} cancelled by user", getName());
                        sendJsonEvent(sseEmitter, AgentEvent.agentError(currentStep,
                                "用户取消了任务 (Esc)"));
                        state = AgentState.FINISHED;
                        break;
                    }

                    // 发射步骤开始事件
                    sendJsonEvent(sseEmitter, AgentEvent.stepStarted(stepNumber, maxSteps));

                    String stepResult = step();

                    // 自动终止时 step() 返回 null（ReActAgent 检测到 FINISHED
                    // 后跳过 getLastAssistantText() 避免返回上一步的过期文本）。
                    // 此时发射空内容的 step_end，让前端 ThinkingChain 正确关闭
                    // 当前步骤（停止转圈），而 finalResponse 不累积空内容。
                    sendJsonEvent(sseEmitter, AgentEvent.stepEnd(stepNumber,
                            stepResult != null ? stepResult : ""));
                    persistMessages();

                    // 如果在 step() 中状态已变为 FINISHED（自动终止或 doTerminate），
                    // 立即跳出循环，避免后续空转步骤产生重复文本
                    if (getState() == AgentState.FINISHED) {
                        break;
                    }
                }
                if (currentStep >= maxSteps) {
                    state = AgentState.FINISHED;
                    emitFinalAnswerIfAvailable(sseEmitter);
                    sendJsonEvent(sseEmitter, AgentEvent.agentFinished("Reached max steps (" + maxSteps + ")"));
                } else {
                    emitFinalAnswerIfAvailable(sseEmitter);
                    sendJsonEvent(sseEmitter, AgentEvent.agentFinished("Task completed successfully"));
                }
                persistMessages();
                sseEmitter.complete();
            } catch (Exception e) {
                state = AgentState.ERROR;
                log.error("error executing agent", e);
                persistMessages();
                sendJsonEvent(sseEmitter, AgentEvent.agentError(currentStep, e.getMessage()));
                sseEmitter.complete();
            } finally {
                this.eventSink = null;
                stopHeartbeat(heartbeat, heartbeatExecutor);
                this.cleanup();
            }
        });

        sseEmitter.onTimeout(() -> {
            this.state = AgentState.ERROR;
            stopHeartbeat(heartbeat, heartbeatExecutor);
            this.cleanup();
            log.warn("SSE connection timeout");
        });
        sseEmitter.onCompletion(() -> {
            if (this.state == AgentState.RUNNING) {
                this.state = AgentState.FINISHED;
            }
            stopHeartbeat(heartbeat, heartbeatExecutor);
            this.cleanup();
            log.info("SSE connection completed");
        });
        return sseEmitter;
    }

    /**
     * 发送纯文本 SSE 事件（与 sendJsonEvent 使用相同的底层机制）。
     */
    private void sendTextEvent(SseEmitter emitter, String text) {
        try {
            emitter.send(SseEmitter.event().data(text));
        } catch (IOException e) {
            log.error("Failed to send SSE text event", e);
        }
    }

    private void stopHeartbeat(ScheduledFuture<?> heartbeat, ScheduledExecutorService executor) {
        if (heartbeat != null && !heartbeat.isCancelled()) {
            heartbeat.cancel(false);
        }
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
    }

    private void sendJsonEvent(SseEmitter emitter, AgentEvent event) {
        try {
            emitter.send(SseEmitter.event().data(event.toJson()));
        } catch (IOException e) {
            log.error("Failed to send SSE event", e);
        }
    }

    /**
     * 发射 final_answer 事件（如果子类提供了最终回复文本）。
     * <p>
     * final_answer 与 step_end 不同：它承载 LLM 的自然语言最终总结，
     * 专门用于聊天面板展示，而非工具原始输出。
     * <p>
     * 子类（如 ToolCallAgent）重写 {@link #getFinalAnswerText()} 来提供内容。
     */
    private void emitFinalAnswerIfAvailable(SseEmitter sseEmitter) {
        String finalText = getFinalAnswerText();
        if (StrUtil.isNotBlank(finalText)) {
            sendJsonEvent(sseEmitter, AgentEvent.finalAnswer(currentStep, finalText));
        }
    }

    /**
     * 返回 Agent 的最终回复文本。默认返回 null（无最终回复）。
     * 子类可重写以提供 LLM 的自然语言总结。
     */
    protected String getFinalAnswerText() {
        return null;
    }

    /**
     * 定义单步执行逻辑 — 子类必须实现
     */
    public abstract String step();

    /**
     * 清理资源 — 子类可按需重写。
     * 调用 onCleanupCallback 清理外部状态（如 cancelFlags）。
     */
    protected void cleanup() {
        if (onCleanupCallback != null) {
            try {
                onCleanupCallback.run();
            } catch (Exception e) {
                log.warn("Cleanup callback failed for session {}: {}", sessionId, e.getMessage());
            }
        }
    }

    /**
     * 从磁盘加载会话历史消息到 messageList。
     * <p>
     * 在 Agent 创建后调用，恢复上次持久化的对话上下文。
     * 如果磁盘上没有该会话的文件（新建会话或文件被清理），则不做任何操作。
     */
    public void loadSessionHistory() {
        if (chatMemory == null || sessionId == null || sessionId.isBlank()) {
            return;
        }
        if (chatMemory.exists(sessionId)) {
            List<Message> saved = chatMemory.get(sessionId);
            if (CollUtil.isNotEmpty(saved)) {
                this.messageList = saved;
                this.lastPersistedSize = saved.size(); // 同步，避免恢复后首次 persist 冗余写入
                log.info("Loaded {} messages from disk for session {}", saved.size(), sessionId);
            }
        }
    }

    // 上次持久化时的消息数量，用于跳过无新消息时的重复写入
    private int lastPersistedSize = -1;

    /**
     * 将当前 messageList 全量持久化到磁盘。
     * <p>
     * 在每步执行后、Agent 完成/出错时调用，确保对话进度不丢失。
     * 如果消息数量自上次写入以来没有变化，跳过本次写入以减少磁盘 I/O。
     */
    public void persistMessages() {
        if (chatMemory == null || sessionId == null || sessionId.isBlank()) {
            return;
        }
        int currentSize = messageList.size();
        if (currentSize == lastPersistedSize) {
            return; // 无新消息，跳过
        }
        try {
            chatMemory.save(sessionId, messageList);
            lastPersistedSize = currentSize;
            // 通知外部更新会话元数据（标题、消息数等）
            if (onPersistCallback != null) {
                onPersistCallback.run();
            }
        } catch (Exception e) {
            log.warn("Failed to persist messages for session {}: {}", sessionId, e.getMessage());
        }
    }
}
