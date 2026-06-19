package com.aiagent.config;

/**
 * 用户自定义模型配置（多 Provider 支持）。
 *
 * @param provider  提供商：dashscope / openai / anthropic / openai-compatible
 * @param apiKey    用户自填的 API Key
 * @param modelName 模型名称，如 gpt-4o / claude-sonnet-4-6 / qwen-plus
 * @param baseUrl   自定义 API 地址（仅 openai-compatible 时使用，如 https://api.deepseek.com）
 */
public record ModelConfig(String provider, String apiKey, String modelName, String baseUrl) {

    public static final String PROVIDER_DASHSCOPE = "dashscope";
    public static final String PROVIDER_OPENAI = "openai";
    public static final String PROVIDER_ANTHROPIC = "anthropic";
    public static final String PROVIDER_OPENAI_COMPATIBLE = "openai-compatible";

    public static final String DEFAULT_MODEL = "qwen3.7-max";
    public static final String DEFAULT_PROVIDER = PROVIDER_DASHSCOPE;

    public boolean isValid() {
        return apiKey != null && !apiKey.isBlank()
                && modelName != null && !modelName.isBlank()
                && provider != null && !provider.isBlank();
    }

    /** 返回有效的 provider（为空时默认 dashscope） */
    public String effectiveProvider() {
        return provider != null && !provider.isBlank() ? provider : DEFAULT_PROVIDER;
    }

    public static ModelConfig defaultConfig() {
        return new ModelConfig(null, null, DEFAULT_MODEL, null);
    }
}
