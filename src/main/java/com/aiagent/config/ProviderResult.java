package com.aiagent.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.ChatOptions;

/**
 * DynamicChatModelFactory 的返回值，封装了 ChatClient 和 Provider 专属的 ChatOptions。
 */
public record ProviderResult(ChatClient chatClient, ChatOptions chatOptions) {
}
