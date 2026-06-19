package com.aiagent.session;

import com.aiagent.agent.DevAssistantAgent;
import com.aiagent.config.ModelConfig;

/**
 * Agent 会话对象，包装 Agent 实例及其会话元数据。
 * <p>
 * 支持存储用户自定义的 {@link ModelConfig}（API Key + 模型名），
 * 方便前端查询当前配置状态。
 */
public class AgentSession {

    private final String sessionId;
    private DevAssistantAgent agent;
    private final long createdAt;
    private volatile long lastAccessedAt;

    /** 用户自定义配置（null 表示使用默认配置） */
    private ModelConfig modelConfig;

    public AgentSession(String sessionId, DevAssistantAgent agent) {
        this.sessionId = sessionId;
        this.agent = agent;
        this.createdAt = System.currentTimeMillis();
        this.lastAccessedAt = this.createdAt;
    }

    public AgentSession(String sessionId, DevAssistantAgent agent, ModelConfig modelConfig) {
        this(sessionId, agent);
        this.modelConfig = modelConfig;
    }

    public String getSessionId() { return sessionId; }

    public DevAssistantAgent getAgent() {
        this.lastAccessedAt = System.currentTimeMillis();
        return agent;
    }

    public long getCreatedAt() { return createdAt; }
    public long getLastAccessedAt() { return lastAccessedAt; }

    public ModelConfig getModelConfig() { return modelConfig; }

    /** 替换内部的 Agent 实例（用于配置变更后重建） */
    public void replaceAgent(DevAssistantAgent newAgent, ModelConfig newConfig) {
        this.agent = newAgent;
        this.modelConfig = newConfig;
        this.lastAccessedAt = System.currentTimeMillis();
    }

    public boolean isExpired(long timeoutMs) {
        return System.currentTimeMillis() - lastAccessedAt > timeoutMs;
    }
}
