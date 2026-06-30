package com.aiagent.agent;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.aiagent.agent.event.AgentEvent;
import com.aiagent.agent.model.AgentState;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 基于 LLM Function Calling 的 ToolCallAgent。
 *
 * <p>think() 阶段调用 LLM 决定要使用哪些工具；
 * act() 阶段执行工具并将结果记录到消息上下文。
 * 在 think/act 过程中通过 eventSink 发射结构化事件，供前端可视化。
 *
 * <p>内置每步最多 3 次重试，提高鲁棒性。
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
public class ToolCallAgent extends ReActAgent {

    // 可用的工具
    private final ToolCallback[] availableTools;

    // 保存工具调用信息的响应结果
    private ChatResponse toolCallChatResponse;

    // 工具调用管理者
    private final ToolCallingManager toolCallingManager;

    // 最新一次 LLM 的思考文本（非工具调用的自然语言部分）
    // 当 terminate 被调用时，它就是 Agent 的最终回复
    private String lastThinkingText;

    // 工具结果最大长度（字符），超过此长度会被截断。
    // 防止单步工具返回（如网页抓取）撑爆 LLM 上下文窗口。
    private static final int MAX_TOOL_RESULT_LENGTH = 2_000;

    // 当前步的重试计数
    private int retryCount = 0;
    private static final int MAX_RETRIES = 3;

    // 是否已添加过 next-step 提示（只在第一步添加一次）
    private boolean nextStepPromptAdded = false;

    // Provider 专属的 ChatOptions（由 DynamicChatModelFactory 创建）
    private final ChatOptions chatOptions;

    public ToolCallAgent(ToolCallback[] availableTools, ChatOptions chatOptions) {
        super();
        this.availableTools = availableTools;
        this.toolCallingManager = ToolCallingManager.builder().build();
        this.chatOptions = chatOptions;
    }

    /**
     * Think 阶段：调用 LLM 决定下一步行动。
     * <p>
     * 向 LLM 发送当前消息上下文 + 可用工具列表，LLM 返回
     * 推理文本（thinking）和选中的工具调用列表。
     * 通过 eventSink 发射 thinking 和 tool_call 事件。
     *
     * @return true 表示需要执行工具，false 表示不需要
     */
    @Override
    public boolean think() {
        // 只在第一步添加 next-step 提示，避免每次调用都重复堆叠
        if (StrUtil.isNotBlank(getNextStepPrompt()) && !nextStepPromptAdded) {
            UserMessage userMessage = new UserMessage(getNextStepPrompt());
            getMessageList().add(userMessage);
            nextStepPromptAdded = true;
        }
        List<Message> messageList = getMessageList();
        Prompt prompt = new Prompt(messageList, this.chatOptions);
        try {
            ChatResponse chatResponse = getChatClient().prompt(prompt)
                    .system(getSystemPrompt())
                    .toolCallbacks(availableTools)
                    .call()
                    .chatResponse();
            this.toolCallChatResponse = chatResponse;
            AssistantMessage assistantMessage = chatResponse.getResult().getOutput();
            List<AssistantMessage.ToolCall> toolCallList = assistantMessage.getToolCalls();

            // 发射 thinking 事件：LLM 的推理文本
            String thinkingText = assistantMessage.getText();
            // 部分模型（如 qwen3.7-max）在 Function Calling 时会把推理放到
            // metadata 的 reasoningContent 字段中，getText() 可能为空
            if (StrUtil.isBlank(thinkingText)) {
                thinkingText = extractReasoningContent(assistantMessage);
            }
            this.lastThinkingText = thinkingText;
            if (StrUtil.isNotBlank(thinkingText)) {
                emitEvent(AgentEvent.thinking(getCurrentStep(), thinkingText));
            }
            log.info("{} 思考: {}", getName(), thinkingText);
            log.info("{} 选择了 {} 个工具", getName(), toolCallList.size());

            // 发射 tool_call 事件：每个工具的名称和参数
            for (AssistantMessage.ToolCall toolCall : toolCallList) {
                emitEvent(AgentEvent.toolCall(getCurrentStep(), toolCall.name(), toolCall.arguments()));
                log.info("  工具: {} 参数: {}", toolCall.name(), toolCall.arguments());
            }

            // 成功获取响应，重置重试计数
            this.retryCount = 0;

            if (toolCallList.isEmpty()) {
                getMessageList().add(assistantMessage);
                return false;
            } else {
                return true;
            }
        } catch (Exception e) {
            log.error("{} 思考过程出错: {}", getName(), e.getMessage());
            retryCount++;
            if (retryCount <= MAX_RETRIES) {
                log.info("{} 重试 {}/{}", getName(), retryCount, MAX_RETRIES);
                getMessageList().add(new AssistantMessage("上一步出错了: " + e.getMessage() + "，请换一种方式重试"));
                return true; // 返回 true 触发重新执行
            }
            emitEvent(AgentEvent.agentError(getCurrentStep(), "Think failed after " + MAX_RETRIES + " retries: " + e.getMessage()));
            getMessageList().add(new AssistantMessage("处理时遇到了错误：" + e.getMessage()));
            return false;
        }
    }

    /**
     * Act 阶段：执行 LLM 选中的工具。
     * <p>
     * 调用 toolCallingManager 执行工具，将结果记录到消息上下文。
     * 通过 eventSink 发射 tool_result 事件。
     * 检测 terminate 工具以结束 Agent 运行。
     *
     * @return 工具执行结果摘要
     */
    @Override
    public String act() {
        if (!toolCallChatResponse.hasToolCalls()) {
            return "没有工具需要调用";
        }
        try {
            // 先把 AssistantMessage（含工具调用指令）加入消息列表
            AssistantMessage assistantMessage = toolCallChatResponse.getResult().getOutput();
            getMessageList().add(assistantMessage);

            // 执行工具调用并追加结果到消息列表
            Prompt prompt = new Prompt(getMessageList(), this.chatOptions);
            ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(prompt, toolCallChatResponse);
            // 取返回结果中的 ToolResponseMessage
            List<Message> updatedHistory = toolExecutionResult.conversationHistory();
            ToolResponseMessage toolResponseMessage = null;
            if (CollUtil.isNotEmpty(updatedHistory)) {
                for (int i = updatedHistory.size() - 1; i >= 0; i--) {
                    if (updatedHistory.get(i) instanceof ToolResponseMessage trm) {
                        toolResponseMessage = trm;
                        break;
                    }
                }
            }
            if (toolResponseMessage == null) {
                return "工具执行完成，但未获取到返回结果";
            }

            // 检查是否调用了 terminate 工具
            boolean terminateToolCalled = toolResponseMessage.getResponses().stream()
                    .anyMatch(response -> response.name().equals("doTerminate"));
            if (terminateToolCalled) {
                setState(AgentState.FINISHED);
            }

            // 截断过长的工具返回结果，防止撑爆 LLM 上下文窗口
            // 替换原始 ToolResponseMessage 为截断后的版本再加入消息列表
            List<ToolResponseMessage.ToolResponse> truncatedResponses =
                    toolResponseMessage.getResponses().stream()
                            .map(r -> {
                                String raw = r.responseData();
                                String truncated = truncateToolOutput(raw);
                                return new ToolResponseMessage.ToolResponse(
                                        r.id(), r.name(), truncated);
                            })
                            .toList();
            ToolResponseMessage truncatedMessage =
                    new ToolResponseMessage(truncatedResponses,
                            toolResponseMessage.getMetadata());
            getMessageList().add(truncatedMessage);

            // 发射 tool_result 事件（使用截断后的输出）
            String results = truncatedResponses.stream()
                    .map(response -> {
                        String output = response.responseData();
                        emitEvent(AgentEvent.toolResult(getCurrentStep(),
                                response.name(), output));
                        return "工具 " + response.name() + " 返回结果: " + output;
                    })
                    .collect(Collectors.joining("\n"));
            log.info(results);
            return results;
        } catch (Exception e) {
            log.error("{} 工具执行失败: {}", getName(), e.getMessage());
            emitEvent(AgentEvent.agentError(getCurrentStep(), "Tool execution failed: " + e.getMessage()));
            return "工具执行失败：" + e.getMessage();
        }
    }

    /**
     * 截断过长的工具输出，防止单步结果撑爆 LLM 上下文窗口。
     * <p>
     * 截断后在末尾追加标记，告知 LLM 内容被截断，LLM 可以
     * 根据截断信息决定是否需要更精确的查询来获取完整内容。
     */
    private String truncateToolOutput(String output) {
        if (output == null || output.length() <= MAX_TOOL_RESULT_LENGTH) {
            return output;
        }
        int originalLength = output.length();
        String truncated = output.substring(0, MAX_TOOL_RESULT_LENGTH);
        return truncated + "\n\n[... 输出已截断，原始长度: "
                + originalLength + " 字符，当前保留前 "
                + MAX_TOOL_RESULT_LENGTH + " 字符]";
    }

    /**
     * 安全地发射事件到 eventSink
     */
    private void emitEvent(AgentEvent event) {
        if (eventSink != null) {
            try {
                eventSink.accept(event);
            } catch (Exception e) {
                log.warn("Failed to emit event: {}", e.getMessage());
            }
        }
    }

    /**
     * 尝试从 AssistantMessage 的 metadata 中提取 reasoning content。
     * 部分模型（如 qwen3.7-max）在 Function Calling 模式下不通过 getText()
     * 返回推理文本，而是放到 metadata 的 "reasoningContent" 键中。
     */
    private String extractReasoningContent(AssistantMessage message) {
        try {
            if (message.getMetadata() != null) {
                Object reasoning = message.getMetadata().get("reasoningContent");
                if (reasoning != null && StrUtil.isNotBlank(reasoning.toString())) {
                    return reasoning.toString();
                }
            }
        } catch (Exception e) {
            log.debug("Failed to extract reasoning content from metadata: {}", e.getMessage());
        }
        return "";
    }
}
