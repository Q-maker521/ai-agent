package com.aiagent.session;

import com.aiagent.agent.DevAssistantAgent;
import com.aiagent.config.DynamicChatModelFactory;
import com.aiagent.config.ModelConfig;
import com.aiagent.config.ProviderResult;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Agent 会话池管理器。
 *
 * <p>管理 Agent 会话的生命周期：
 * <ul>
 *   <li>创建会话：为每个用户生成唯一 sessionId 并创建 Agent 实例</li>
 *   <li>复用会话：通过 sessionId 获取已有 Agent，保持对话上下文连续</li>
 *   <li>自定义配置：支持用户提供自己的 API Key 和模型名</li>
 *   <li>自动清理：30 分钟无活动的会话自动过期销毁</li>
 * </ul>
 */
@Component
@Slf4j
public class AgentSessionManager {

    private final ConcurrentHashMap<String, AgentSession> sessions = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cleaner = Executors.newSingleThreadScheduledExecutor();

    private static final long SESSION_TIMEOUT_MS = 30 * 60 * 1000; // 30 分钟

    private final ToolCallback[] allTools;
    private final ChatModel defaultChatModel;
    private final DynamicChatModelFactory chatModelFactory;

    public AgentSessionManager(ToolCallback[] allTools,
                               @Qualifier("dashscopeChatModel") ChatModel defaultChatModel,
                               DynamicChatModelFactory chatModelFactory) {
        this.allTools = allTools;
        this.defaultChatModel = defaultChatModel;
        this.chatModelFactory = chatModelFactory;

        // 每 5 分钟清理一次过期会话
        cleaner.scheduleAtFixedRate(this::cleanExpiredSessions, 5, 5, TimeUnit.MINUTES);
    }

    /**
     * 创建使用默认配置的新会话（回退到环境变量中的 key）
     */
    public AgentSession createSession() {
        return createSessionWithConfig(ModelConfig.defaultConfig());
    }

    /**
     * 使用用户自定义配置创建新会话。
     * <p>
     * 如果用户的配置有效，根据 provider 创建对应的 ChatModel；
     * 否则回退到 Spring 容器中的默认 ChatModel。
     */
    public AgentSession createSessionWithConfig(ModelConfig config) {
        String sessionId = UUID.randomUUID().toString().substring(0, 8);

        // 尝试用用户配置创建，失败则回退到默认
        ProviderResult result = chatModelFactory.createChatClient(config);
        if (result == null) {
            log.info("Session {} using default ChatModel", sessionId);
            result = chatModelFactory.createDefault(defaultChatModel);
        } else {
            log.info("Session {} using custom config: provider={}, model={}",
                    sessionId, config.effectiveProvider(), config.modelName());
        }

        DevAssistantAgent agent = new DevAssistantAgent(allTools, result);
        AgentSession session = new AgentSession(sessionId, agent, config.isValid() ? config : null);
        sessions.put(sessionId, session);
        log.info("Created agent session: {} (configured={})", sessionId, config.isValid());
        return session;
    }

    /**
     * 更新已有会话的配置（创建新 Agent 替换旧 Agent，会丢失旧对话历史）
     */
    public AgentSession reconfigureSession(String sessionId, ModelConfig config) {
        AgentSession oldSession = sessions.remove(sessionId);
        if (oldSession != null) {
            log.info("Reconfiguring session: {}", sessionId);
        }
        ProviderResult result = chatModelFactory.createChatClient(config);
        if (result == null) {
            result = chatModelFactory.createDefault(defaultChatModel);
        }
        DevAssistantAgent agent = new DevAssistantAgent(allTools, result);
        AgentSession newSession = new AgentSession(sessionId, agent, config.isValid() ? config : null);
        sessions.put(sessionId, newSession);
        log.info("Reconfigured session: {} (provider={}, model={})",
                sessionId, config.effectiveProvider(), config.modelName());
        return newSession;
    }

    public AgentSession getSession(String sessionId) {
        AgentSession session = sessions.get(sessionId);
        if (session != null && session.isExpired(SESSION_TIMEOUT_MS)) {
            sessions.remove(sessionId);
            log.info("Session expired: {}", sessionId);
            return null;
        }
        return session;
    }

    public AgentSession getOrCreateSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return createSession();
        }
        AgentSession session = getSession(sessionId);
        return session != null ? session : createSession();
    }

    public void closeSession(String sessionId) {
        AgentSession removed = sessions.remove(sessionId);
        if (removed != null) {
            log.info("Closed agent session: {}", sessionId);
        }
    }

    public int getActiveSessionCount() {
        return sessions.size();
    }

    private void cleanExpiredSessions() {
        int before = sessions.size();
        sessions.entrySet().removeIf(entry -> entry.getValue().isExpired(SESSION_TIMEOUT_MS));
        int removed = before - sessions.size();
        if (removed > 0) {
            log.info("Cleaned {} expired sessions, {} remaining", removed, sessions.size());
        }
    }

    @PreDestroy
    public void shutdown() {
        cleaner.shutdown();
        sessions.clear();
    }
}
