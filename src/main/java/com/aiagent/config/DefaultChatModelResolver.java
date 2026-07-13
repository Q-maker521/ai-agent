package com.aiagent.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Resolves the default chat model used by server-side features.
 * <p>
 * The application still falls back to the Spring Boot configured DashScope bean,
 * but production can switch to any OpenAI-compatible provider through
 * environment variables without storing API keys in code.
 */
@Component
@Slf4j
public class DefaultChatModelResolver {

    private final ChatModel defaultChatModel;

    public DefaultChatModelResolver(
            ChatModel dashscopeChatModel,
            DynamicChatModelFactory chatModelFactory,
            @Value("${app.default-chat.provider:}") String provider,
            @Value("${app.default-chat.api-key:}") String apiKey,
            @Value("${app.default-chat.model-name:}") String modelName,
            @Value("${app.default-chat.base-url:}") String baseUrl) {
        ModelConfig config = new ModelConfig(provider, apiKey, modelName, baseUrl);
        if (config.isValid()) {
            this.defaultChatModel = chatModelFactory.createChatModel(config);
            log.info("Default chat model overridden: provider={}, model={}",
                    config.effectiveProvider(), config.modelName());
        } else {
            this.defaultChatModel = dashscopeChatModel;
            log.info("Default chat model uses Spring configured DashScope bean");
        }
    }

    public ChatModel resolve() {
        return defaultChatModel;
    }
}
