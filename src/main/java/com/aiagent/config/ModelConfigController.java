package com.aiagent.config;

import com.aiagent.session.AgentSession;
import com.aiagent.session.AgentSessionManager;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 用户模型配置 REST 端点（多 Provider 支持）。
 * <p>
 * 提供 Provider、API Key、模型名称、自定义 baseUrl 的保存与查询。
 */
@RestController
@RequestMapping("/ai/config")
@Slf4j
public class ModelConfigController {

    @Resource
    private AgentSessionManager sessionManager;

    /**
     * 保存用户配置。
     *
     * <pre>
     * POST /api/ai/config
     * { "provider": "openai", "apiKey": "sk-xxx", "modelName": "gpt-4o",
     *   "baseUrl": "", "sessionId": "可选" }
     * </pre>
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> saveConfig(@RequestBody Map<String, String> body) {
        String provider = body.get("provider");
        String apiKey = body.get("apiKey");
        String modelName = body.get("modelName");
        String baseUrl = body.get("baseUrl");
        String sessionId = body.get("sessionId");

        if (apiKey == null || apiKey.isBlank()) {
            return Map.of("configured", false, "error", "API Key 不能为空");
        }
        if (modelName == null || modelName.isBlank()) {
            modelName = ModelConfig.DEFAULT_MODEL;
        }
        if (provider == null || provider.isBlank()) {
            provider = ModelConfig.DEFAULT_PROVIDER;
        }

        ModelConfig config = new ModelConfig(provider.trim(), apiKey.trim(),
                modelName.trim(), baseUrl != null ? baseUrl.trim() : null);
        log.info("User config: provider={}, model={}", config.effectiveProvider(), config.modelName());

        AgentSession session;
        if (sessionId != null && !sessionId.isBlank()) {
            session = sessionManager.reconfigureSession(sessionId, config);
        } else {
            session = sessionManager.createSessionWithConfig(config);
        }

        return Map.of(
                "sessionId", session.getSessionId(),
                "provider", config.effectiveProvider(),
                "modelName", modelName,
                "configured", true
        );
    }

    /**
     * 查询当前配置（API Key 脱敏返回）。
     */
    @GetMapping
    public Map<String, Object> getConfig(@RequestParam(required = false) String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return Map.of("configured", false, "modelName", ModelConfig.DEFAULT_MODEL,
                    "provider", ModelConfig.DEFAULT_PROVIDER, "hasApiKey", false);
        }
        AgentSession session = sessionManager.getSession(sessionId);
        if (session == null) {
            return Map.of("configured", false, "modelName", ModelConfig.DEFAULT_MODEL,
                    "provider", ModelConfig.DEFAULT_PROVIDER, "hasApiKey", false);
        }
        ModelConfig config = session.getModelConfig();
        boolean hasConfig = config != null && config.isValid();
        return Map.of(
                "sessionId", session.getSessionId(),
                "provider", hasConfig ? config.effectiveProvider() : ModelConfig.DEFAULT_PROVIDER,
                "modelName", hasConfig ? config.modelName() : ModelConfig.DEFAULT_MODEL,
                "hasApiKey", hasConfig,
                "configured", hasConfig
        );
    }
}
