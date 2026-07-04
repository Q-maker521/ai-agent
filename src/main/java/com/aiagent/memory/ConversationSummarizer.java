package com.aiagent.memory;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 对话历史增量摘要器。
 *
 * <p>当消息数量超过阈值时，不直接丢弃旧消息，而是用 LLM 将中间消息
 * 压缩为一段摘要，保留在上下文头部。这样 Agent 在长对话中仍能回忆起
 * 早期的关键信息（人名、决策、重要事实等）。
 *
 * <h3>使用方式</h3>
 * <pre>
 *   if (messageList.size() > MAX_MESSAGE_COUNT) {
 *       messageList = summarizer.compress(chatClient, messageList, 20);
 *   }
 * </pre>
 */
@Component
@Slf4j
public class ConversationSummarizer {

    /**
     * 将消息列表中的旧消息压缩为摘要。
     *
     * @param chatClient 用于生成摘要的 ChatClient（复用用户的 Provider 配置）
     * @param messages   完整消息列表
     * @param keepRecent 保留最近多少条消息不压缩
     * @return 压缩后的消息列表：[首条] + [摘要 SystemMessage] + [最近 keepRecent 条]
     */
    public List<Message> compress(ChatClient chatClient,
                                   List<Message> messages,
                                   int keepRecent) {
        if (messages.size() <= keepRecent + 10) {
            return messages; // 消息不多，不需要压缩
        }

        // 1. 提取需要压缩的中间消息（去掉首条 + 去掉最近 keepRecent 条）
        int fromIndex = 1;
        int toIndex = messages.size() - keepRecent;
        if (toIndex <= fromIndex) {
            return messages; // 没有中间消息需要压缩
        }
        List<Message> oldMessages = messages.subList(fromIndex, toIndex);

        // 2. 构建摘要 prompt
        String summaryPrompt = buildSummaryPrompt(oldMessages);

        // 3. 调用 LLM 生成摘要
        String summary;
        try {
            summary = chatClient.prompt()
                    .user(summaryPrompt)
                    .call()
                    .content();
        } catch (Exception e) {
            log.warn("Failed to generate conversation summary: {}", e.getMessage());
            // 降级：简单截断（保留首条 + 最近 keepRecent 条）
            return fallbackTrim(messages, keepRecent);
        }
        if (summary == null || summary.isBlank()) {
            return fallbackTrim(messages, keepRecent);
        }

        // 4. 构建新消息列表
        List<Message> compressed = new ArrayList<>();
        compressed.add(messages.get(0)); // 保留原始第一条（用户核心任务）
        compressed.add(new SystemMessage(
                "[对话历史摘要] " + summary.trim())); // 注入摘要
        compressed.addAll(messages.subList(toIndex, messages.size())); // 最近 N 条

        log.info("Compressed {} messages → {} (summary: {} chars)",
                messages.size(), compressed.size(), summary.length());
        return compressed;
    }

    /**
     * 构建摘要生成的 prompt。
     */
    private String buildSummaryPrompt(List<Message> oldMessages) {
        StringBuilder sb = new StringBuilder();
        sb.append("请将以下对话历史压缩为一段 200 字以内的摘要，" +
                  "保留关键信息（人名、决策、重要事实、用户偏好）。" +
                  "只输出摘要文本，不要加任何前缀或解释。\n\n");
        for (Message msg : oldMessages) {
            String role = msg.getMessageType().name();
            String text = msg.getText();
            if (text != null && !text.isBlank()) {
                // 截断过长内容，避免摘要 prompt 过长
                if (text.length() > 300) {
                    text = text.substring(0, 300) + "...";
                }
                sb.append("[").append(role).append("] ").append(text).append("\n");
            }
        }
        return sb.toString();
    }

    /**
     * 降级方案：简单截断。
     */
    private List<Message> fallbackTrim(List<Message> messages, int keepRecent) {
        int toIndex = messages.size() - keepRecent;
        if (toIndex <= 1) return messages;
        List<Message> trimmed = new ArrayList<>();
        trimmed.add(messages.get(0));
        trimmed.addAll(messages.subList(toIndex, messages.size()));
        return trimmed;
    }
}
