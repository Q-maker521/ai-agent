package com.aiagent.agent.event;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Agent 运行时结构化事件，用于 SSE 流式推送到前端可视化。
 *
 * <p>事件类型说明：
 * <ul>
 *   <li>agent_start     — Agent 开始执行，携带总步数</li>
 *   <li>step_start      — 单步开始</li>
 *   <li>thinking        — LLM 的推理文本（决定调用哪些工具）</li>
 *   <li>thinking_delta  — 增量思考文本片段（token 级流式）</li>
 *   <li>tool_call       — 即将调用的工具名和参数</li>
 *   <li>tool_result     — 工具执行返回的结果</li>
 *   <li>step_end        — 单步结束，携带步骤摘要</li>
 *   <li>agent_finish    — Agent 执行完成</li>
 *   <li>final_answer    — Agent 的最终回复文本（与 step_end 区分，专用于聊天面板展示）</li>
 *   <li>agent_error     — 执行出错</li>
 * </ul>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AgentEvent(
        String type,
        int stepNumber,
        int totalSteps,
        String content,
        String toolName,
        String toolInput,
        String toolOutput,
        long timestamp,
        String delta      // thinking_delta 增量文本片段（token 级流式）
) {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static AgentEvent agentStarted(int totalSteps) {
        return new AgentEvent("agent_start", 0, totalSteps, "Agent started", null, null, null,
                System.currentTimeMillis(), null);
    }

    public static AgentEvent stepStarted(int step, int total) {
        return new AgentEvent("step_start", step, total, "Step " + step + " started", null, null, null,
                System.currentTimeMillis(), null);
    }

    public static AgentEvent thinking(int step, String thought) {
        return new AgentEvent("thinking", step, 0, thought, null, null, null,
                System.currentTimeMillis(), null);
    }

    /**
     * 增量思考文本片段（token 级流式）。
     * 在每一步 think() 中逐 token 推送，前端累积拼接为完整思考文本。
     */
    public static AgentEvent thinkingDelta(int step, String delta) {
        return new AgentEvent("thinking_delta", step, 0, null, null, null, null,
                System.currentTimeMillis(), delta);
    }

    public static AgentEvent toolCall(int step, String name, String input) {
        return new AgentEvent("tool_call", step, 0, null, name, input, null,
                System.currentTimeMillis(), null);
    }

    public static AgentEvent toolResult(int step, String name, String output) {
        return new AgentEvent("tool_result", step, 0, null, name, null, output,
                System.currentTimeMillis(), null);
    }

    public static AgentEvent stepEnd(int step, String summary) {
        return new AgentEvent("step_end", step, 0, summary, null, null, null,
                System.currentTimeMillis(), null);
    }

    public static AgentEvent agentFinished(String summary) {
        return new AgentEvent("agent_finish", 0, 0, summary, null, null, null,
                System.currentTimeMillis(), null);
    }

    /**
     * Agent 最终回复文本 — 专用于聊天面板展示，与 step_end（步骤摘要）语义不同。
     * 内容来自最后一步 LLM 的自然语言总结，而非工具原始输出。
     */
    public static AgentEvent finalAnswer(int step, String content) {
        return new AgentEvent("final_answer", step, 0, content, null, null, null,
                System.currentTimeMillis(), null);
    }

    public static AgentEvent agentError(int step, String error) {
        return new AgentEvent("agent_error", step, 0, error, null, null, null,
                System.currentTimeMillis(), null);
    }

    /**
     * 将事件序列化为 JSON 字符串，方便 SSE 传输
     */
    public String toJson() {
        try {
            return objectMapper.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            return "{\"type\":\"error\",\"content\":\"serialization failed\"}";
        }
    }
}
