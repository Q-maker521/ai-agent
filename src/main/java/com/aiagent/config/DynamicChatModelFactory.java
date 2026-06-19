package com.aiagent.config;

import com.aiagent.advisor.LoggingAdvisor;
import com.aiagent.advisor.ReReadingAdvisor;
import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.stereotype.Component;

/**
 * 多 Provider 动态 ChatModel 工厂。
 * <p>
 * 支持 4 种 Provider：
 * <ul>
 *   <li><b>dashscope</b> — 阿里云百炼（DashScopeChatModel）</li>
 *   <li><b>openai</b> — OpenAI 官方（OpenAiChatModel）</li>
 *   <li><b>anthropic</b> — Anthropic Claude（AnthropicChatModel）</li>
 *   <li><b>openai-compatible</b> — OpenAI 兼容接口（自定义 baseUrl，覆盖 DeepSeek/Moonshot/Groq 等）</li>
 * </ul>
 * <p>
 * 如果用户未提供有效配置，返回 null，由调用方回退到默认 Bean。
 */
@Component
@Slf4j
public class DynamicChatModelFactory {

    /**
     * 根据用户配置创建 ProviderResult（ChatClient + ChatOptions）。
     *
     * @return ProviderResult，配置无效时返回 null
     */
    public ProviderResult createChatClient(ModelConfig config) {
        if (config == null || !config.isValid()) {
            log.debug("ModelConfig invalid or null, will use default ChatModel");
            return null;
        }
        String provider = config.effectiveProvider();
        log.info("Creating dynamic ChatClient: provider={}, model={}, apiKey={}...",
                provider, config.modelName(), maskKey(config.apiKey()));

        return switch (provider) {
            case ModelConfig.PROVIDER_OPENAI -> createOpenAi(config);
            case ModelConfig.PROVIDER_ANTHROPIC -> createAnthropic(config);
            case ModelConfig.PROVIDER_OPENAI_COMPATIBLE -> createOpenAiCompatible(config);
            default -> createDashScope(config);
        };
    }

    // ─────────── DashScope ───────────

    private ProviderResult createDashScope(ModelConfig config) {
        DashScopeApi api = DashScopeApi.builder().apiKey(config.apiKey()).build();
        DashScopeChatOptions options = DashScopeChatOptions.builder()
                .withModel(config.modelName())
                .withInternalToolExecutionEnabled(false)
                .build();
        DashScopeChatModel chatModel = DashScopeChatModel.builder()
                .dashScopeApi(api).defaultOptions(options).build();
        ChatClient client = ChatClient.builder(chatModel)
                .defaultAdvisors(new LoggingAdvisor(), new ReReadingAdvisor()).build();
        return new ProviderResult(client, options);
    }

    // ─────────── OpenAI ───────────

    private ProviderResult createOpenAi(ModelConfig config) {
        OpenAiApi api = OpenAiApi.builder().apiKey(config.apiKey()).build();
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(config.modelName()).build();
        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(api).defaultOptions(options).build();
        ChatClient client = ChatClient.builder(chatModel)
                .defaultAdvisors(new LoggingAdvisor(), new ReReadingAdvisor()).build();
        return new ProviderResult(client, options);
    }

    // ─────────── Anthropic ───────────

    private ProviderResult createAnthropic(ModelConfig config) {
        AnthropicApi api = AnthropicApi.builder().apiKey(config.apiKey()).build();
        AnthropicChatOptions options = AnthropicChatOptions.builder()
                .model(config.modelName()).build();
        AnthropicChatModel chatModel = AnthropicChatModel.builder()
                .anthropicApi(api).defaultOptions(options).build();
        ChatClient client = ChatClient.builder(chatModel)
                .defaultAdvisors(new LoggingAdvisor(), new ReReadingAdvisor()).build();
        return new ProviderResult(client, options);
    }

    // ─────────── OpenAI-Compatible（自定义 baseUrl）───────────

    private ProviderResult createOpenAiCompatible(ModelConfig config) {
        String baseUrl = config.baseUrl() != null && !config.baseUrl().isBlank()
                ? config.baseUrl() : "https://api.openai.com";
        OpenAiApi api = OpenAiApi.builder().apiKey(config.apiKey()).baseUrl(baseUrl).build();
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(config.modelName()).build();
        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(api).defaultOptions(options).build();
        ChatClient client = ChatClient.builder(chatModel)
                .defaultAdvisors(new LoggingAdvisor(), new ReReadingAdvisor()).build();
        log.info("OpenAI-compatible provider: baseUrl={}", baseUrl);
        return new ProviderResult(client, options);
    }

    // ─────────── 回退 ───────────

    /** 使用 Spring 容器中的默认 DashScope Bean（环境变量中的 key） */
    public ProviderResult createDefault(org.springframework.ai.chat.model.ChatModel defaultChatModel) {
        ChatClient client = ChatClient.builder(defaultChatModel)
                .defaultAdvisors(new LoggingAdvisor(), new ReReadingAdvisor()).build();
        // 默认 ChatOptions：仅禁用内部工具执行
        ChatOptions options = DashScopeChatOptions.builder()
                .withInternalToolExecutionEnabled(false).build();
        return new ProviderResult(client, options);
    }

    private String maskKey(String key) {
        if (key == null || key.length() <= 8) return "***";
        return key.substring(0, 4) + "***" + key.substring(key.length() - 4);
    }
}
