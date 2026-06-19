package com.aiagent.agent;

import cn.hutool.core.util.StrUtil;
import com.aiagent.agent.event.AgentEvent;
import com.aiagent.agent.model.AgentState;
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
        messageList.add(new UserMessage(userPrompt));
        List<String> results = new ArrayList<>();
        try {
            for (int i = 0; i < maxSteps && state != AgentState.FINISHED; i++) {
                int stepNumber = i + 1;
                currentStep = stepNumber;
                log.info("Executing step {}/{}", stepNumber, maxSteps);
                String stepResult = step();
                String result = "Step " + stepNumber + ": " + stepResult;
                results.add(result);
            }
            if (currentStep >= maxSteps) {
                state = AgentState.FINISHED;
                results.add("Terminated: Reached max steps (" + maxSteps + ")");
            }
            return String.join("\n", results);
        } catch (Exception e) {
            state = AgentState.ERROR;
            log.error("error executing agent", e);
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
        CompletableFuture.runAsync(() -> {
            try {
                if (this.state != AgentState.IDLE) {
                    sseEmitter.send("错误：无法从状态运行代理：" + this.state);
                    sseEmitter.complete();
                    return;
                }
                if (StrUtil.isBlank(userPrompt)) {
                    sseEmitter.send("错误：不能使用空提示词运行代理");
                    sseEmitter.complete();
                    return;
                }
            } catch (Exception e) {
                sseEmitter.completeWithError(e);
            }
            this.state = AgentState.RUNNING;
            messageList.add(new UserMessage(userPrompt));
            List<String> results = new ArrayList<>();
            try {
                for (int i = 0; i < maxSteps && state != AgentState.FINISHED; i++) {
                    int stepNumber = i + 1;
                    currentStep = stepNumber;
                    log.info("Executing step {}/{}", stepNumber, maxSteps);
                    String stepResult = step();
                    String result = "Step " + stepNumber + ": " + stepResult;
                    results.add(result);
                    sseEmitter.send(result);
                }
                if (currentStep >= maxSteps) {
                    state = AgentState.FINISHED;
                    results.add("Terminated: Reached max steps (" + maxSteps + ")");
                    sseEmitter.send("执行结束：达到最大步骤（" + maxSteps + "）");
                }
                sseEmitter.complete();
            } catch (Exception e) {
                state = AgentState.ERROR;
                log.error("error executing agent", e);
                try {
                    sseEmitter.send("执行错误：" + e.getMessage());
                    sseEmitter.complete();
                } catch (IOException ex) {
                    sseEmitter.completeWithError(ex);
                }
            } finally {
                this.cleanup();
            }
        });

        sseEmitter.onTimeout(() -> {
            this.state = AgentState.ERROR;
            this.cleanup();
            log.warn("SSE connection timeout");
        });
        sseEmitter.onCompletion(() -> {
            if (this.state == AgentState.RUNNING) {
                this.state = AgentState.FINISHED;
            }
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

                    // 发射步骤开始事件
                    sendJsonEvent(sseEmitter, AgentEvent.stepStarted(stepNumber, maxSteps));

                    String stepResult = step();

                    // 发射步骤结束事件
                    sendJsonEvent(sseEmitter, AgentEvent.stepEnd(stepNumber, stepResult));
                }
                if (currentStep >= maxSteps) {
                    state = AgentState.FINISHED;
                    emitFinalAnswerIfAvailable(sseEmitter);
                    sendJsonEvent(sseEmitter, AgentEvent.agentFinished("Reached max steps (" + maxSteps + ")"));
                } else {
                    emitFinalAnswerIfAvailable(sseEmitter);
                    sendJsonEvent(sseEmitter, AgentEvent.agentFinished("Task completed successfully"));
                }
                sseEmitter.complete();
            } catch (Exception e) {
                state = AgentState.ERROR;
                log.error("error executing agent", e);
                sendJsonEvent(sseEmitter, AgentEvent.agentError(currentStep, e.getMessage()));
                sseEmitter.complete();
            } finally {
                this.eventSink = null;
                this.cleanup();
            }
        });

        sseEmitter.onTimeout(() -> {
            this.state = AgentState.ERROR;
            this.cleanup();
            log.warn("SSE connection timeout");
        });
        sseEmitter.onCompletion(() -> {
            if (this.state == AgentState.RUNNING) {
                this.state = AgentState.FINISHED;
            }
            this.cleanup();
            log.info("SSE connection completed");
        });
        return sseEmitter;
    }

    private void sendJsonEvent(SseEmitter emitter, AgentEvent event) {
        try {
            emitter.send(SseEmitter.event().data(event.toJson()));
        } catch (IOException e) {
            log.error("Failed to send SSE event", e);
        }
    }

    /**
     * 如果子类是 ToolCallAgent 且保存了最终思考文本，则发射 final_answer 事件。
     * final_answer 与 step_end 不同：它承载 LLM 的自然语言最终总结，
     * 专门用于聊天面板展示，而非工具原始输出。
     */
    private void emitFinalAnswerIfAvailable(SseEmitter sseEmitter) {
        if (this instanceof ToolCallAgent tca) {
            String finalText = tca.getLastThinkingText();
            if (StrUtil.isNotBlank(finalText)) {
                sendJsonEvent(sseEmitter, AgentEvent.finalAnswer(currentStep, finalText));
            }
        }
    }

    /**
     * 定义单步执行逻辑 — 子类必须实现
     */
    public abstract String step();

    /**
     * 清理资源 — 子类可按需重写
     */
    protected void cleanup() {
    }
}
