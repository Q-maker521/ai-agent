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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

    // 最新一次 think() 产生的自然语言文本。
    // 当 think() 无工具调用时直接作为最终回复存入 messageList；
    // 有工具调用时在 act() 中与 tool_calls 合并存入，确保上下文链路完整。
    private String lastThinkingText;

    // 工具结果最大长度（字符），超过此长度会被截断。
    // 防止单步工具返回（如网页抓取）撑爆 LLM 上下文窗口。
    // 搜索结果保留 4000 字符，确保 Tavily 等高质量引擎返回的正文
    // 能被 LLM 充分阅读，从而自主判断信息是否足够，减少无效的补搜。
    private static final int MAX_TOOL_RESULT_LENGTH = 4000;

    // 思考文本最大长度（字符），防止思考链过长
    private static final int MAX_THINKING_LENGTH = 1_000;

    // 消息列表最大长度（滑动窗口裁剪，防止上下文窗口溢出）
    private static final int MAX_MESSAGE_COUNT = 50;

    // 当前步的重试计数
    private int retryCount = 0;
    private static final int MAX_RETRIES = 3;

    // 每步最多允许并行调用的工具数（DashScope API 限制为 1，OpenAI/Anthropic 不限制）。
    // AgentSessionManager 根据 Provider 自动设置，也可在运行时由 "only one tool call"
    // 错误触发强制降级。
    private int maxToolsPerStep = Integer.MAX_VALUE;

    // 重试提示：临时注入 system prompt，成功后自动清除。
    // 不直接写入 messageList，避免错误信息伪装成 AI 回复污染上下文。
    private String retryHint = null;

    // 连续无工具调用步数计数器（防止 LLM 完成任务后空转不调用 terminate）
    private int consecutiveNoToolCallSteps = 0;
    private static final int MAX_NO_TOOL_CALL_STEPS = 2;

    // 同类型工具调用计数器（防止搜索死循环，同一工具调用超过上限后强制过滤）
    private final java.util.Map<String, Integer> sameToolCallCount = new java.util.concurrent.ConcurrentHashMap<>();
    private static final int MAX_SAME_TOOL_CALLS = 5;

    // 同类型工具连续失败计数器（与 sameToolCallCount 独立）。
    // 成功时清零，失败时 +1。连续失败 ≥ 阈值时通过 retryHint 通知 LLM 换方案，
    // 而非永久封禁工具。
    private final java.util.Map<String, Integer> consecutiveToolFailures = new java.util.concurrent.ConcurrentHashMap<>();
    private static final int MAX_CONSECUTIVE_FAILURES = 3;

    // 上一步的思考文本，用于检测重复回答（防止 LLM 陷入重复循环）
    private String previousThinkingText = "";
    private static final int MAX_REPEAT_COUNT = 2;

    // 是否已添加过 next-step 提示（只在第一步添加一次）
    private boolean nextStepPromptAdded = false;

    // Provider 专属的 ChatOptions（由 DynamicChatModelFactory 创建）
    private final ChatOptions chatOptions;

    // 对话摘要器（由 AgentSessionManager 注入，可选）
    private com.aiagent.memory.ConversationSummarizer summarizer;

    public void setSummarizer(com.aiagent.memory.ConversationSummarizer summarizer) {
        this.summarizer = summarizer;
    }

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
        // 检查是否被取消（Esc 中断）
        if (isCancelled()) {
            log.info("{} think() aborted: cancelled", getName());
            return false;
        }
        // 只在第一步将 next-step 提示追加到 system prompt，不污染消息历史。
        // 从磁盘恢复时 nextStepPromptAdded 为 false，但提示已在首次执行时注入过一次，
        // 通过检查 agent 已执行的步数来判断是否需要追加。
        String effectiveSystemPrompt = getSystemPrompt();
        if (StrUtil.isNotBlank(getNextStepPrompt()) && !nextStepPromptAdded) {
            effectiveSystemPrompt = getSystemPrompt() + "\n\n" + getNextStepPrompt();
            nextStepPromptAdded = true;
        }

        // 单工具模式：DashScope API 不支持单消息多工具调用，
        // 必须在 prompt 层面限制 LLM 每次只选一个最重要的工具。
        if (maxToolsPerStep == 1) {
            effectiveSystemPrompt += "\n\nCRITICAL: You may only call ONE tool per step. "
                    + "Choose the single most important tool for this step. "
                    + "If you need multiple tools, call them one at a time in separate sequential steps.";
        }

        // 注入上一步失败的重试提示（不写入 messageList，保持上下文干净）
        if (retryHint != null) {
            effectiveSystemPrompt += "\n\n[SYSTEM NOTE: " + retryHint + "]";
        }

        List<Message> messageList = getMessageList();
        // 消息过多时做增量摘要，而非简单丢弃旧消息
        if (messageList.size() > MAX_MESSAGE_COUNT) {
            if (summarizer != null) {
                // 使用 LLM 压缩中间消息为摘要，保留首条 + 最近 20 条
                messageList = summarizer.compress(
                        getChatClient(), messageList, 20);
                log.info("{} compressed messageList from {} to {} messages (with summary)",
                        getName(), getMessageList().size(), messageList.size());
            } else {
                // 降级：简单滑动窗口裁剪
                int keep = MAX_MESSAGE_COUNT - 1;
                List<Message> trimmed = new ArrayList<>();
                trimmed.add(messageList.get(0));
                trimmed.addAll(messageList.subList(
                        messageList.size() - keep, messageList.size()));
                messageList = trimmed;
                log.info("{} trimmed messageList from {} to {} messages",
                        getName(), getMessageList().size(), trimmed.size());
            }
        }
        Prompt prompt = new Prompt(messageList, this.chatOptions);

        // 先发射占位 thinking 事件，让前端思考链立即有反馈，避免"沉默等待"
        emitEvent(AgentEvent.thinking(getCurrentStep(), "正在调用 LLM 分析当前状态..."));

        try {
            // 使用 stream() 替代 call()，实现 token 级流式输出
            reactor.core.publisher.Flux<ChatResponse> flux = getChatClient().prompt(prompt)
                    .system(effectiveSystemPrompt)
                    .toolCallbacks(availableTools)
                    .stream()
                    .chatResponse();

            // 收集完整响应的同时逐 token 推送 thinking_delta 事件
            StringBuilder fullThinking = new StringBuilder();
            ChatResponse chatResponse = flux
                    .doOnNext(chunk -> {
                        // 思考文本截断：超过上限后跳过，防止思考链过长
                        if (fullThinking.length() >= MAX_THINKING_LENGTH) {
                            return;
                        }
                        String delta = extractTextContent(chunk);
                        if (StrUtil.isNotBlank(delta)) {
                            fullThinking.append(delta);
                            // 首次触及上限时发送截断提示
                            if (fullThinking.length() >= MAX_THINKING_LENGTH) {
                                emitEvent(AgentEvent.thinkingDelta(
                                        getCurrentStep(),
                                        "\n\n[... 思考内容过长，已截断 ...]"));
                                return;
                            }
                            emitEvent(AgentEvent.thinkingDelta(
                                    getCurrentStep(), delta));
                        }
                    })
                    .blockLast(); // 阻塞等待完整响应，保持 step() 同步语义
            this.toolCallChatResponse = chatResponse;
            AssistantMessage assistantMessage = chatResponse.getResult().getOutput();
            List<AssistantMessage.ToolCall> toolCallList = assistantMessage.getToolCalls();

            // 从流式累积文本中提取 LLM 思考内容。
            // 不能直接取 assistantMessage.getText()——在 stream() 模式下，
            // blockLast() 返回的最后一个 chunk 可能是结束标记（空文本），
            // 真正的内容已通过 doOnNext 收集到 fullThinking 中。
            String thinkingText = !fullThinking.isEmpty()
                    ? fullThinking.toString()
                    : assistantMessage.getText();
            if (StrUtil.isBlank(thinkingText)) {
                thinkingText = extractReasoningContent(assistantMessage);
            }
            this.lastThinkingText = thinkingText;

            // === 同类型工具调用上限过滤（防止搜索死循环）===
            // 对每个工具调用计数，超过上限的直接过滤，不执行
            List<AssistantMessage.ToolCall> filteredCalls = new ArrayList<>();
            List<String> blockedCalls = new ArrayList<>();
            for (AssistantMessage.ToolCall tc : toolCallList) {
                int count = sameToolCallCount.getOrDefault(tc.name(), 0);
                if (count > MAX_SAME_TOOL_CALLS) {
                    blockedCalls.add(tc.name() + " (×" + count + ")");
                    log.info("{} 工具 {} 已调用 {} 次，超上限 {}，过滤",
                            getName(), tc.name(), count, MAX_SAME_TOOL_CALLS);
                } else {
                    filteredCalls.add(tc);
                }
            }
            if (!blockedCalls.isEmpty()) {
                emitEvent(AgentEvent.thinking(getCurrentStep(),
                        "⚠️ 以下工具已超调用上限，跳过： " + String.join(", ", blockedCalls)
                        + "。请用现有知识直接回答或换完全不同的方法。"));
            }
            // 全部被过滤 → 视为无工具调用
            if (!toolCallList.isEmpty() && filteredCalls.isEmpty()) {
                toolCallList = filteredCalls;
                consecutiveNoToolCallSteps++;
                // 发射思考内容（使用 fullThinking 累积的文本，已在上面计算）
                if (StrUtil.isNotBlank(thinkingText)) {
                    emitEvent(AgentEvent.thinking(getCurrentStep(), thinkingText));
                }
                log.info("{} 所有工具调用被过滤，视为无工具调用", getName());
                // 强制终止条件：全部被过滤 + 连续空转达到阈值
                if (consecutiveNoToolCallSteps >= MAX_NO_TOOL_CALL_STEPS) {
                    log.info("{} 连续 {} 步无有效工具调用，自动终止", getName(), consecutiveNoToolCallSteps);
                    if (hasNoOutput()) {
                        emitEvent(AgentEvent.agentError(getCurrentStep(),
                                "Agent 未能生成有效回复，请检查模型配置或网络连接"));
                    } else {
                        emitEvent(AgentEvent.thinking(getCurrentStep(),
                                "已连续 " + consecutiveNoToolCallSteps + " 步未调用工具，自动结束"));
                    }
                    setState(AgentState.FINISHED);
                }
                return false;
            }
            toolCallList = filteredCalls;
            // === 上限过滤结束 ===

            // 发射 thinking 事件：LLM 的推理文本（使用 fullThinking 累积文本，
            // 已在 stream() 的 blockLast() 之后计算）
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

            // 成功获取响应，重置重试计数和重试提示
            this.retryCount = 0;
            this.retryHint = null;

            if (toolCallList.isEmpty()) {

                // 检测 1：无工具调用时的终止判断
                // 关键设计：如果 LLM 返回了有意义的文本内容，说明这已经是给用户的
                // 最终回复，应立刻终止，不再给 LLM "追加输出"的机会。
                // 否则 LLM 会在下一步看到自己上一步的不完整回复，继续输出续句，
                // 导致 reply 被拆成两半——思考链显示完整内容，但聊天面板只拿到后半截。
                consecutiveNoToolCallSteps++;
                if (StrUtil.isNotBlank(thinkingText)) {
                    // 有文本 → 这就是最终答案，立即终止并保存消息。
                    // assistantMessage.getText() 在 stream() 的最后一个 chunk
                    // 中可能为空，需要用 fullThinking 累积的完整文本构建消息。
                    AssistantMessage msgToStore = StrUtil.isNotBlank(assistantMessage.getText())
                            ? assistantMessage
                            : new AssistantMessage(thinkingText);
                    getMessageList().add(msgToStore);
                    setState(AgentState.FINISHED);
                    log.info("{} 返回文本响应，视为最终答案，自动终止", getName());
                } else if (consecutiveNoToolCallSteps >= MAX_NO_TOOL_CALL_STEPS) {
                    // 无文本且连续空转达到阈值 → 强制终止。
                    // 区分"有历史输出但当前步空转" vs "全程静默失败"。
                    log.info("{} 连续 {} 步无工具调用，自动终止", getName(), consecutiveNoToolCallSteps);
                    if (hasNoOutput()) {
                        emitEvent(AgentEvent.agentError(getCurrentStep(),
                                "Agent 未能生成有效回复，请检查模型配置或网络连接"));
                    } else {
                        emitEvent(AgentEvent.thinking(getCurrentStep(),
                                "已连续 " + consecutiveNoToolCallSteps + " 步未调用工具，自动结束"));
                    }
                    setState(AgentState.FINISHED);
                }
                // 无文本且未达阈值：不给 LLM 回复，继续下一步（给一次重试机会）

                // 检测 2：LLM 重复输出相同内容 → 自动终止
                String currentThinking = StrUtil.isNotBlank(thinkingText) ? thinkingText : "";
                if (StrUtil.isNotBlank(currentThinking) && currentThinking.equals(previousThinkingText)) {
                    log.info("{} 检测到与上一步相同的思考文本，自动终止", getName());
                    emitEvent(AgentEvent.thinking(getCurrentStep(),
                            "检测到重复回答，自动结束任务"));
                    setState(AgentState.FINISHED);
                }
                this.previousThinkingText = currentThinking;

                return false;
            } else {
                // 有工具调用，重置空转计数和重复检测
                consecutiveNoToolCallSteps = 0;
                this.previousThinkingText = "";
                return true;
            }
        } catch (Exception e) {
            log.error("{} 思考过程出错: {}", getName(), e.getMessage());

            // DashScope 单工具限制：不视为真正的错误，强制降级为单工具模式重试。
            // 不增加 retryCount——这不是 LLM 的错，是 API 的硬限制。
            if (e.getMessage() != null && e.getMessage().contains("only one tool call")) {
                log.info("{} 检测到多工具调用限制，自动降级为单工具模式", getName());
                maxToolsPerStep = 1;
                retryHint = "上一步尝试同时调用多个工具，但当前模型只支持单工具调用。"
                        + "请每次只选择一个最重要的工具，需要多个工具时在后续步骤中逐一调用。";
                emitEvent(AgentEvent.thinking(getCurrentStep(),
                        "检测到多工具调用限制，自动切换为单工具模式..."));
                return false; // 跳过 act()，下一步 think() 用受限 prompt 重试
            }

            retryCount++;
            if (retryCount <= MAX_RETRIES) {
                log.info("{} 重试 {}/{}", getName(), retryCount, MAX_RETRIES);
                // 不写入 messageList，改为临时注入 system prompt（下次 think() 时生效）
                retryHint = "LLM call failed: " + e.getMessage()
                        + ". Try a completely different approach.";
                emitEvent(AgentEvent.thinking(getCurrentStep(),
                        "LLM 调用失败 (" + e.getMessage() + ")，正在重试..."));
                return false; // 跳过 act()，下一步 think() 通过 retryHint 获得错误上下文
            }
            emitEvent(AgentEvent.agentError(getCurrentStep(),
                    "Think failed after " + MAX_RETRIES + " retries: " + e.getMessage()));
            return false;
        }
    }

    /**
     * Act 阶段：执行 LLM 选中的工具。
     * <p>
     * 单工具时直接执行，多工具时并行执行以提升效率。
     * 通过 eventSink 发射 tool_result 事件。
     * Agent 终止由 think() 检测"无工具调用 + 有文本"自动触发，
     * 不再依赖显式的 terminate 工具调用。
     *
     * @return 工具执行结果摘要
     */
    @Override
    public String act() {
        if (!toolCallChatResponse.hasToolCalls()) {
            return "没有工具需要调用";
        }
        try {
            // 把 AssistantMessage 加入消息列表。
            // 主流 Agent 做法：text 和 tool_calls 可在同一条消息中共存。
            // 如果 think() 产生了思考文本但 AssistantMessage 中 text 为空（流式模式下常见），
            // 用累积的 lastThinkingText 构造带文本的消息，确保 getFinalAnswerText() 可检索。
            AssistantMessage assistantMessage = toolCallChatResponse.getResult().getOutput();
            AssistantMessage msgToStore;
            if (StrUtil.isNotBlank(lastThinkingText)
                    && StrUtil.isBlank(assistantMessage.getText())) {
                msgToStore = new AssistantMessage(lastThinkingText,
                        assistantMessage.getMetadata(),
                        assistantMessage.getToolCalls());
            } else {
                msgToStore = assistantMessage;
            }
            getMessageList().add(msgToStore);

            // === 同类型工具上限检查（act 层二次校验）===
            List<AssistantMessage.ToolCall> rawCalls = assistantMessage.getToolCalls();
            List<String> blockedInAct = new ArrayList<>();
            for (AssistantMessage.ToolCall tc : rawCalls) {
                int count = sameToolCallCount.getOrDefault(tc.name(), 0);
                if (count > MAX_SAME_TOOL_CALLS) {
                    blockedInAct.add(tc.name());
                }
            }
            if (!blockedInAct.isEmpty()) {
                log.info("{} act() 阻止超限工具: {}", getName(), String.join(", ", blockedInAct));
                emitEvent(AgentEvent.agentError(getCurrentStep(),
                        "工具调用超限，已阻止: " + String.join(", ", blockedInAct)
                        + "。请使用现有知识完成任务。"));
                return "工具 " + String.join(", ", blockedInAct) + " 已超调用上限（最大 "
                        + MAX_SAME_TOOL_CALLS + " 次），请用现有知识回答";
            }

            // === 执行工具调用（多工具时并行）===
            List<ToolResponseMessage.ToolResponse> allResponses;
            if (rawCalls.size() <= 1) {
                // 单工具：保持原有顺序执行路径
                Prompt prompt = new Prompt(getMessageList(), this.chatOptions);
                ToolExecutionResult toolExecutionResult =
                        toolCallingManager.executeToolCalls(prompt, toolCallChatResponse);
                ToolResponseMessage trm = extractToolResponseMessage(toolExecutionResult);
                if (trm == null || CollUtil.isEmpty(trm.getResponses())) {
                    // 框架未能生成 ToolResponseMessage（内部执行错误），
                    // 构造明确的失败响应让 LLM 知道发生了什么，而非返回空列表。
                    AssistantMessage.ToolCall singleTc = rawCalls.get(0);
                    trackToolFailure(singleTc.name());
                    String errMsg = "工具 " + singleTc.name() + " 执行异常：内部框架未生成返回结果。请勿重试。";
                    log.warn("{} 单工具路径 extractToolResponseMessage 返回空: {}", getName(), singleTc.name());
                    allResponses = List.of(new ToolResponseMessage.ToolResponse(
                            singleTc.id(), singleTc.name(), errMsg));
                } else {
                    allResponses = trm.getResponses();
                    // 统计执行结果：成功 → 递增执行计数；失败 → 递增失败计数
                    for (ToolResponseMessage.ToolResponse r : allResponses) {
                        if (isToolError(r.responseData())) {
                            trackToolFailure(r.name());
                        } else {
                            sameToolCallCount.merge(r.name(), 1, Integer::sum);
                            consecutiveToolFailures.remove(r.name());
                        }
                    }
                }
            } else {
                // 多工具：并行执行每个工具调用
                log.info("{} 并行执行 {} 个工具调用", getName(), rawCalls.size());
                List<java.util.concurrent.CompletableFuture<ToolResponseMessage.ToolResponse>> futures =
                        rawCalls.stream()
                                .map(tc -> java.util.concurrent.CompletableFuture.supplyAsync(() ->
                                        executeSingleToolCall(tc)))
                                .toList();
                allResponses = new ArrayList<>();
                for (int idx = 0; idx < futures.size(); idx++) {
                    AssistantMessage.ToolCall tc = rawCalls.get(idx);
                    try {
                        ToolResponseMessage.ToolResponse r = futures.get(idx).get(30, java.util.concurrent.TimeUnit.SECONDS);
                        allResponses.add(r);
                    } catch (java.util.concurrent.TimeoutException e) {
                        log.warn("{} 工具 {} 调用超时 (30s)", getName(), tc.name());
                        trackToolFailure(tc.name());
                        allResponses.add(new ToolResponseMessage.ToolResponse(
                                tc.id(), tc.name(),
                                "工具 " + tc.name() + " 执行超时（30 秒），请勿重试相同操作。"));
                    } catch (Exception e) {
                        log.warn("{} 工具 {} 调用异常: {}", getName(), tc.name(), e.getMessage());
                        trackToolFailure(tc.name());
                        allResponses.add(new ToolResponseMessage.ToolResponse(
                                tc.id(), tc.name(),
                                "工具 " + tc.name() + " 执行异常：" + e.getMessage()));
                    }
                }
            }

            if (allResponses.isEmpty()) {
                return "工具执行完成，但未获取到返回结果";
            }

            // 截断过长的工具返回结果
            List<ToolResponseMessage.ToolResponse> truncatedResponses =
                    allResponses.stream()
                            .map(r -> {
                                String raw = r.responseData();
                                String truncated = truncateToolOutput(raw);
                                return new ToolResponseMessage.ToolResponse(
                                        r.id(), r.name(), truncated);
                            })
                            .toList();
            ToolResponseMessage truncatedMessage =
                    new ToolResponseMessage(truncatedResponses,
                            Map.of());
            getMessageList().add(truncatedMessage);

            // 发射 tool_result 事件（每个工具独立发射）
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
     * 从 ToolExecutionResult 中提取 ToolResponseMessage。
     */
    private ToolResponseMessage extractToolResponseMessage(ToolExecutionResult result) {
        List<Message> history = result.conversationHistory();
        if (CollUtil.isNotEmpty(history)) {
            for (int i = history.size() - 1; i >= 0; i--) {
                if (history.get(i) instanceof ToolResponseMessage trm) {
                    return trm;
                }
            }
        }
        return null;
    }

    /**
     * 执行单个工具调用（用于并行执行模式）。
     * 构建包含 single tool call 的临时 ChatResponse 以复用 ToolCallingManager。
     */
    private ToolResponseMessage.ToolResponse executeSingleToolCall(
            AssistantMessage.ToolCall toolCall) {
        // 找到对应的 ToolCallback
        ToolCallback callback = null;
        for (ToolCallback tc : availableTools) {
            if (tc.getToolDefinition().name().equals(toolCall.name())) {
                callback = tc;
                break;
            }
        }
        if (callback == null) {
            log.warn("{} 找不到工具: {}", getName(), toolCall.name());
            trackToolFailure(toolCall.name());
            return new ToolResponseMessage.ToolResponse(
                    toolCall.id(), toolCall.name(),
                    "错误：找不到工具 " + toolCall.name());
        }
        try {
            String result = callback.call(toolCall.arguments());
            // 成功：递增执行计数，清零失败计数
            sameToolCallCount.merge(toolCall.name(), 1, Integer::sum);
            consecutiveToolFailures.remove(toolCall.name());
            return new ToolResponseMessage.ToolResponse(
                    toolCall.id(), toolCall.name(), result);
        } catch (Exception e) {
            log.warn("{} 工具 {} 执行失败: {}", getName(), toolCall.name(), e.getMessage());
            trackToolFailure(toolCall.name());
            return new ToolResponseMessage.ToolResponse(
                    toolCall.id(), toolCall.name(),
                    "工具执行失败：" + e.getMessage());
        }
    }

    /**
     * 记录工具执行失败，连续失败达到阈值时通过 retryHint 通知 LLM，
     * 而非静默丢弃或永久封禁工具。
     */
    private void trackToolFailure(String toolName) {
        int fails = consecutiveToolFailures.merge(toolName, 1, Integer::sum);
        if (fails >= MAX_CONSECUTIVE_FAILURES) {
            retryHint = "工具 " + toolName + " 已连续失败 " + fails
                    + " 次（" + MAX_CONSECUTIVE_FAILURES + " 次上限）。"
                    + "请停止使用此工具，换用其他工具或用自己的知识直接完成任务。";
            log.info("{} {} 连续失败 {} 次，注入 retryHint", getName(), toolName, fails);
        }
    }

    /**
     * 判断工具返回结果是否表示执行失败。
     * <p>
     * 工具失败时返回的是人类可读的错误消息（如 "Error searching web: timeout"），
     * Spring AI 没有区分成功/失败的机制，需要从文本模式判断。
     */
    private boolean isToolError(String output) {
        if (output == null || output.isEmpty()) return true; // 空输出视为异常
        String s = output.toLowerCase();
        return s.startsWith("error")
            || s.startsWith("错误")
            || s.startsWith("⚠️")
            || s.startsWith("access denied")
            || s.startsWith("page not found")
            || s.startsWith("connection timed out")
            || s.startsWith("download failed")
            || s.startsWith("download timed out")
            || s.startsWith("安全拦截")
            || s.startsWith("command timed out")
            || s.startsWith("工具执行失败")
            || s.startsWith("工具执行异常")
            || s.startsWith("工具执行超时")
            || s.startsWith("工具 ")
            || (s.contains("error") && s.length() < 100); // 短消息中出现 error 大概率是失败
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
     * 返回 LLM 的最终回复文本，供 BaseAgent 发射 final_answer 事件。
     * <p>
     * 优先从 messageList 中获取最后一条有效 AssistantMessage（保证与对话历史一致），
     * 回退到 lastThinkingText（当前步的思考文本）。
     */
    @Override
    protected String getFinalAnswerText() {
        // 优先从 messageList 获取——有文本时总是先存到 messageList 再设 FINISHED
        List<Message> messages = getMessageList();
        for (int i = messages.size() - 1; i >= 0; i--) {
            Message msg = messages.get(i);
            if (msg instanceof AssistantMessage assistantMsg) {
                String text = assistantMsg.getText();
                if (StrUtil.isNotBlank(text)) {
                    return text;
                }
            }
        }
        // 回退到 lastThinkingText（如 extractReasoningContent 提取的内容）
        return lastThinkingText;
    }

    /**
     * 从流式响应的单个 chunk 中提取文本增量。
     * <p>
     * 在 streaming 模式下，每个 chunk 的 getText() 返回增量文本（而非累积）。
     * 如果 getText() 为空，尝试从 reasoningContent metadata 中提取。
     */
    private String extractTextContent(ChatResponse chunk) {
        try {
            if (chunk.getResult() != null && chunk.getResult().getOutput() != null) {
                AssistantMessage msg = chunk.getResult().getOutput();
                String text = msg.getText();
                if (StrUtil.isNotBlank(text)) {
                    return text;
                }
                // fallback: 尝试 reasoning content
                return extractReasoningContent(msg);
            }
        } catch (Exception e) {
            log.debug("Failed to extract text from streaming chunk: {}", e.getMessage());
        }
        return "";
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

    /**
     * 检查 Agent 整个会话中是否产生了任何有效输出。
     * <p>
     * 检查范围：messageList 中的所有 AssistantMessage（文本或工具调用）
     * 和 ToolResponseMessage（工具返回）。如果全部为空/不存在，
     * 说明 LLM 全程未能正常工作（如 API 配额耗尽、模型异常等）。
     * <p>
     * 用于在空转终止时区分"正常完成"和"静默失败"——前者应发射
     * thinking 事件告知用户自动结束，后者应发射 agent_error 告知
     * 用户检查配置。
     */
    private boolean hasNoOutput() {
        List<Message> messages = getMessageList();
        for (Message msg : messages) {
            if (msg instanceof AssistantMessage am) {
                if (StrUtil.isNotBlank(am.getText())) {
                    return false; // 有文本输出
                }
                if (am.getToolCalls() != null && !am.getToolCalls().isEmpty()) {
                    return false; // 有工具调用决策
                }
            }
            if (msg instanceof ToolResponseMessage) {
                return false; // 有工具返回结果
            }
        }
        return true; // 完全没有有效输出
    }
}
