package com.aiagent.session;

import com.aiagent.agent.DevAssistantAgent;
import com.aiagent.chatmemory.FileBasedChatMemory;
import com.aiagent.config.DynamicChatModelFactory;
import com.aiagent.config.ModelConfig;
import com.aiagent.config.ProviderResult;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.Arrays;
import java.util.Comparator;
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
    private final ConcurrentHashMap<String, java.util.concurrent.atomic.AtomicBoolean>
            cancelFlags = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cleaner = Executors.newSingleThreadScheduledExecutor();

    private static final long SESSION_TIMEOUT_MS = 30 * 60 * 1000; // 内存 30 分钟
    private static final long DISK_TTL_MS = 7 * 24 * 60 * 60 * 1000; // 磁盘 7 天
    private static final int MAX_DISK_SESSIONS = 20; // 最多保留 20 个会话

    private final ToolCallback[] allTools;
    private final ChatModel defaultChatModel;
    private final DynamicChatModelFactory chatModelFactory;
    private final FileBasedChatMemory chatMemory;
    private final com.aiagent.memory.ConversationSummarizer summarizer;
    private final String persistenceDir;

    public AgentSessionManager(ToolCallback[] allTools,
                               @Qualifier("dashscopeChatModel") ChatModel defaultChatModel,
                               DynamicChatModelFactory chatModelFactory,
                               com.aiagent.memory.ConversationSummarizer summarizer) {
        this.allTools = allTools;
        this.defaultChatModel = defaultChatModel;
        this.chatModelFactory = chatModelFactory;
        this.summarizer = summarizer;
        this.persistenceDir = System.getProperty("user.dir") + "/tmp/agent-memory";
        this.chatMemory = new FileBasedChatMemory(persistenceDir);

        // 每 5 分钟清理一次过期会话（内存 + 磁盘）
        cleaner.scheduleAtFixedRate(this::cleanExpiredSessions, 5, 5, TimeUnit.MINUTES);
    }

    /**
     * 创建使用默认配置的新会话（回退到环境变量中的 key）
     */
    public AgentSession createSession() {
        return createSessionWithConfig(ModelConfig.defaultConfig(), null);
    }

    /**
     * 使用用户自定义配置创建新会话。
     */
    public AgentSession createSessionWithConfig(ModelConfig config) {
        return createSessionWithConfig(config, null);
    }

    /**
     * 使用用户自定义配置 + 指定 sessionId 创建会话。
     * <p>
     * 如果 sessionId 非空且磁盘上有持久化的历史消息，会自动加载恢复。
     *
     * @param config    模型配置
     * @param sessionId 指定会话 ID（null 则自动生成）
     */
    public AgentSession createSessionWithConfig(ModelConfig config, String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = UUID.randomUUID().toString().substring(0, 8);
        }

        ProviderResult result = chatModelFactory.createChatClient(config);
        if (result == null) {
            log.info("Session {} using default ChatModel", sessionId);
            result = chatModelFactory.createDefault(defaultChatModel);
        } else {
            log.info("Session {} using custom config: provider={}, model={}",
                    sessionId, config.effectiveProvider(), config.modelName());
        }

        DevAssistantAgent agent = new DevAssistantAgent(allTools, result);
        configureMaxToolsPerStep(agent, config);
        wireAgentPersistence(agent, sessionId);

        AgentSession session = new AgentSession(sessionId, agent, config.isValid() ? config : null);
        sessions.put(sessionId, session);
        // 持久化用户配置（Provider + ModelName，不含 API Key）
        if (config.isValid()) {
            persistModelConfig(sessionId, config);
        }
        log.info("Created agent session: {} (configured={}, hasPersistedHistory={})",
                sessionId, config.isValid(), chatMemory.exists(sessionId));
        enforceDiskQuota(); // 新会话创建后检查磁盘配额
        return session;
    }

    /**
     * 给 Agent 注入持久化依赖并从磁盘恢复历史消息。
     */
    private void wireAgentPersistence(DevAssistantAgent agent, String sessionId) {
        agent.setSessionId(sessionId);
        agent.setChatMemory(chatMemory);
        agent.setSummarizer(summarizer);
        agent.loadSessionHistory();
        // 每次持久化后自动更新会话元数据（标题、消息数）
        agent.setOnPersistCallback(() -> ensureSessionMeta(agent, sessionId));
        // Agent 生命周期结束时清理 cancelFlag，防止 Map 无限增长
        agent.setOnCleanupCallback(() -> {
            cancelFlags.remove(sessionId);
            log.debug("Cleaned up cancel flag for session {}", sessionId);
        });
    }

    /**
     * 更新已有会话的配置，保留旧对话历史（从磁盘恢复）。
     * <p>
     * 步骤：保存旧 Agent 的 messageList → 创建新 Agent → 注入持久化 →
     * 用旧消息列表覆盖新 Agent 的 messageList → 持久化。
     */
    public AgentSession reconfigureSession(String sessionId, ModelConfig config) {
        // 1. 先持久化旧 Agent 的 messageList（如果还有旧会话）
        AgentSession oldSession = sessions.remove(sessionId);
        if (oldSession != null) {
            oldSession.getAgent().persistMessages();
            log.info("Reconfiguring session: {} (preserving {} messages)",
                    sessionId, oldSession.getAgent().getMessageList().size());
        }

        // 2. 创建新 Agent
        ProviderResult result = chatModelFactory.createChatClient(config);
        if (result == null) {
            result = chatModelFactory.createDefault(defaultChatModel);
        }
        DevAssistantAgent agent = new DevAssistantAgent(allTools, result);
        configureMaxToolsPerStep(agent, config);
        wireAgentPersistence(agent, sessionId);
        // loadSessionHistory() 已在 wireAgentPersistence 中调用，自动从磁盘恢复旧消息

        AgentSession newSession = new AgentSession(sessionId, agent, config.isValid() ? config : null);
        sessions.put(sessionId, newSession);
        // 持久化用户配置（Provider + ModelName，不含 API Key）
        if (config.isValid()) {
            persistModelConfig(sessionId, config);
        }
        log.info("Reconfigured session: {} (provider={}, model={}, restoredMessages={})",
                sessionId, config.effectiveProvider(), config.modelName(),
                agent.getMessageList().size());
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
        if (session != null) {
            // 检查 Agent 是否处于可接受新消息的状态
            com.aiagent.agent.model.AgentState state = session.getAgent().getState();
            if (state == com.aiagent.agent.model.AgentState.FINISHED
                    || state == com.aiagent.agent.model.AgentState.ERROR) {
                // Agent 已完成/出错：保存最新消息，用同一 sessionId 重建 Agent 并恢复历史
                log.info("Session {} agent is {}, recreating agent to accept new message",
                        sessionId, state);
                session.getAgent().persistMessages();
                ModelConfig config = session.getModelConfig();
                if (config == null || !config.isValid()) {
                    config = ModelConfig.defaultConfig();
                }
                AgentSession newSession = createSessionWithConfig(config, sessionId);
                sessions.put(sessionId, newSession);
                return newSession;
            }
            return session;
        }
        // 内存中已过期或服务重启：检查磁盘是否有持久化记录
        if (chatMemory.exists(sessionId)) {
            log.info("Restoring session {} from disk", sessionId);
            ModelConfig savedConfig = loadModelConfig(sessionId);
            if (savedConfig == null || !savedConfig.isValid()) {
                savedConfig = ModelConfig.defaultConfig();
            }
            return createSessionWithConfig(savedConfig, sessionId);
        }
        // 完全新建，复用用户提供的 sessionId
        return createSessionWithConfig(ModelConfig.defaultConfig(), sessionId);
    }

    public void closeSession(String sessionId) {
        AgentSession removed = sessions.remove(sessionId);
        if (removed != null) {
            removed.getAgent().persistMessages(); // 最后保存一次
            log.info("Closed agent session: {}", sessionId);
        }
    }

    public int getActiveSessionCount() {
        return sessions.size();
    }

    public java.util.Set<String> getSessionIds() {
        return java.util.Collections.unmodifiableSet(sessions.keySet());
    }

    /**
     * 从磁盘直接读取指定会话的历史消息（不依赖内存中的 session）。
     * 用于服务重启后前端查询历史上下文。
     */
    public java.util.List<org.springframework.ai.chat.messages.Message> getPersistedMessages(String sessionId) {
        if (chatMemory.exists(sessionId)) {
            return chatMemory.get(sessionId);
        }
        return java.util.Collections.emptyList();
    }

    /**
     * 列出所有磁盘上持久化的会话 ID（含 .json 和旧 .kryo 格式）。
     */
    public java.util.Set<String> getPersistedSessionIds() {
        java.io.File dir = new java.io.File(persistenceDir);
        java.util.Set<String> ids = new java.util.LinkedHashSet<>();
        // .json（新格式）
        java.io.File[] jsonFiles = dir.listFiles((d, name)
                -> name.endsWith(".json") && !name.endsWith(".json.tmp") && !name.endsWith(".meta.json"));
        if (jsonFiles != null) {
            for (java.io.File f : jsonFiles) {
                ids.add(f.getName().replace(".json", ""));
            }
        }
        // .kryo（旧格式，兼容）
        java.io.File[] kryoFiles = dir.listFiles((d, name) -> name.endsWith(".kryo"));
        if (kryoFiles != null) {
            for (java.io.File f : kryoFiles) {
                ids.add(f.getName().replace(".kryo", ""));
            }
        }
        return ids;
    }

    // ==================== 会话元数据管理 ====================

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 会话元数据，存储在 {sessionId}.meta.json 中。
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SessionMeta(
            String sessionId,
            String title,
            int messageCount,
            long createdAt,
            long lastAccessedAt,
            boolean isActive,
            String provider,    // 用户配置的 Provider（null 表示使用默认）
            String modelName    // 用户配置的模型名（null 表示使用默认）
    ) {
        public static SessionMeta create(String sessionId, String title, int messageCount) {
            long now = System.currentTimeMillis();
            return new SessionMeta(sessionId, title, messageCount, now, now, false, null, null);
        }

        public static SessionMeta create(String sessionId, String title, int messageCount,
                                          String provider, String modelName) {
            long now = System.currentTimeMillis();
            return new SessionMeta(sessionId, title, messageCount, now, now, false, provider, modelName);
        }
    }

    private java.io.File getMetaFile(String sessionId) {
        return new java.io.File(
                persistenceDir,
                sessionId + ".meta.json");
    }

    public synchronized void saveSessionMeta(SessionMeta meta) {
        try {
            objectMapper.writeValue(getMetaFile(meta.sessionId()), meta);
        } catch (Exception e) {
            log.warn("Failed to save session meta for {}: {}", meta.sessionId(), e.getMessage());
        }
    }

    private SessionMeta loadSessionMeta(String sessionId) {
        java.io.File file = getMetaFile(sessionId);
        if (file.exists()) {
            try {
                return objectMapper.readValue(file, SessionMeta.class);
            } catch (Exception e) {
                log.debug("Failed to read meta for {}: {}", sessionId, e.getMessage());
            }
        }
        return null;
    }

    /**
     * 列出所有会话（内存活跃 + 磁盘历史），按最后活跃时间倒序。
     */
    public java.util.List<SessionMeta> listSessions() {
        java.util.Map<String, SessionMeta> all = new java.util.LinkedHashMap<>();

        // 1. 从磁盘数据文件（.json 或旧 .kryo）反推所有会话
        java.io.File dir = new java.io.File(persistenceDir);
        // 1a. .json（新格式）
        java.io.File[] jsonFiles = dir.listFiles((d, name)
                -> name.endsWith(".json") && !name.endsWith(".json.tmp") && !name.endsWith(".meta.json"));
        if (jsonFiles != null) {
            for (java.io.File f : jsonFiles) {
                String id = f.getName().replace(".json", "");
                addSessionFromDisk(id, all);
            }
        }
        // 1b. .kryo（旧格式，兼容迁移中）
        java.io.File[] kryoFiles = dir.listFiles((d, name) -> name.endsWith(".kryo"));
        if (kryoFiles != null) {
            for (java.io.File f : kryoFiles) {
                String id = f.getName().replace(".kryo", "");
                if (!all.containsKey(id)) {
                    addSessionFromDisk(id, all);
                }
            }
        }

        // 2. 有 .meta.json 但数据文件被清理的幽灵会话 → 清理 meta
        java.io.File[] metaFiles = dir.listFiles((d, name) -> name.endsWith(".meta.json"));
        if (metaFiles != null) {
            for (java.io.File f : metaFiles) {
                String id = f.getName().replace(".meta.json", "");
                if (!chatMemory.exists(id)) {
                    f.delete(); // 清理幽灵 meta
                    all.remove(id);
                }
            }
        }

        // 3. 合并内存活跃会话的实时状态
        for (var entry : sessions.entrySet()) {
            AgentSession session = entry.getValue();
            DevAssistantAgent agent = session.getAgent();
            SessionMeta old = all.get(entry.getKey());
            SessionMeta live = new SessionMeta(
                    entry.getKey(),
                    old != null ? old.title() : "未命名会话",
                    agent.getMessageList().size(),
                    old != null ? old.createdAt() : session.getCreatedAt(),
                    session.getLastAccessedAt(),
                    true, // 内存中活跃
                    old != null ? old.provider() : null,
                    old != null ? old.modelName() : null
            );
            all.put(entry.getKey(), live);
        }

        // 按 lastAccessedAt 倒序排列
        return all.values().stream()
                .sorted((a, b) -> Long.compare(b.lastAccessedAt(), a.lastAccessedAt()))
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * 从磁盘数据文件加载会话元数据加入列表（用于 listSessions）。
     */
    private void addSessionFromDisk(String id, java.util.Map<String, SessionMeta> all) {
        SessionMeta meta = loadSessionMeta(id);
        if (meta == null) {
            int msgCount = chatMemory.exists(id)
                    ? chatMemory.get(id).size() : 0;
            meta = SessionMeta.create(id, "未命名会话", msgCount);
            saveSessionMeta(meta);
        }
        all.put(id, meta);
    }

    /**
     * 删除会话：内存移除 + 删除持久化文件。
     */
    public synchronized void deleteSession(String sessionId) {
        sessions.remove(sessionId);
        chatMemory.clear(sessionId);
        java.io.File metaFile = getMetaFile(sessionId);
        if (metaFile.exists()) {
            metaFile.delete();
        }
        log.info("Deleted session: {}", sessionId);
    }

    /**
     * 从 messageList 中提取第一条真正的用户消息作为会话标题。
     * 过滤 nextStepPrompt 和前端欢迎语。
     */
    public String autoTitleFromMessages(
            java.util.List<org.springframework.ai.chat.messages.Message> messages) {
        for (var msg : messages) {
            if (msg instanceof org.springframework.ai.chat.messages.UserMessage) {
                String text = msg.getText();
                if (text == null || text.isBlank()) continue;
                // 跳过系统注入的 nextStepPrompt
                if (text.contains("determine the next action")
                        || text.contains("Based on the current task progress")) continue;
                // 跳过前端欢迎语
                if (text.contains("你好，我是 AI 智能助手")) continue;
                // 取前 20 个字作标题
                return text.length() > 20 ? text.substring(0, 20) + "…" : text;
            }
        }
        return "未命名会话";
    }

    public synchronized void updateSessionMeta(String sessionId, String title) {
        SessionMeta old = loadSessionMeta(sessionId);
        if (old == null) {
            // 不存在则创建
            int msgCount = chatMemory.exists(sessionId)
                    ? chatMemory.get(sessionId).size() : 0;
            old = SessionMeta.create(sessionId, title, msgCount);
        }
        SessionMeta updated = new SessionMeta(
                sessionId, title, old.messageCount(),
                old.createdAt(), old.lastAccessedAt(), old.isActive(),
                old.provider(), old.modelName());
        saveSessionMeta(updated);
    }

    /**
     * 更新会话元数据（每次持久化时调用，同步 messageCount 和时间戳）。
     * <p>
     * 首次调用创建 meta；后续调用更新 messageCount 和 lastAccessedAt。
     * 标题一旦由用户手动重命名，不再被自动覆盖。
     */
    public void ensureSessionMeta(DevAssistantAgent agent, String sessionId) {
        if (sessionId == null || sessionId.isBlank()) return;
        var messages = agent.getMessageList();
        int msgCount = messages.size();
        SessionMeta old = loadSessionMeta(sessionId);

        if (old == null) {
            // 首次创建
            String title = autoTitleFromMessages(messages);
            SessionMeta meta = SessionMeta.create(sessionId, title, msgCount);
            saveSessionMeta(meta);
            log.info("Created session meta: {} -> \"{}\" ({} msgs)", sessionId, title, msgCount);
        } else {
            // 每次都更新 msgCount 和 lastAccessedAt；
            // 标题保留已有的（用户可能手动修改），仅"未命名会话"兜底时自动生成
            String title = old.title();
            if (title == null || title.isBlank() || "未命名会话".equals(title)) {
                title = autoTitleFromMessages(messages);
            }
            SessionMeta updated = new SessionMeta(
                    sessionId,
                    title,
                    msgCount,
                    old.createdAt(),
                    System.currentTimeMillis(),
                    old.isActive(),
                    old.provider(),
                    old.modelName()
            );
            saveSessionMeta(updated);
        }
    }

    /**
     * 持久化用户模型配置到 .meta.json（不存储 API Key 明文）。
     */
    private void persistModelConfig(String sessionId, ModelConfig config) {
        if (config == null || !config.isValid()) return;
        SessionMeta old = loadSessionMeta(sessionId);
        SessionMeta updated = new SessionMeta(
                sessionId,
                old != null ? old.title() : "未命名会话",
                old != null ? old.messageCount() : 0,
                old != null ? old.createdAt() : System.currentTimeMillis(),
                System.currentTimeMillis(),
                old != null && old.isActive(),
                config.effectiveProvider(),
                config.modelName()
        );
        saveSessionMeta(updated);
    }

    /**
     * 从 .meta.json 读取用户模型配置（不含 API Key）。
     * 返回的 ModelConfig 的 apiKey 为空，isValid() 为 false，
     * 将回退到默认 ChatModel，但保留了 provider + modelName 信息。
     */
    private ModelConfig loadModelConfig(String sessionId) {
        SessionMeta meta = loadSessionMeta(sessionId);
        if (meta == null || meta.provider() == null || meta.modelName() == null) {
            return null;
        }
        // apiKey 为空 → isValid() 为 false → 回退到默认 ChatModel
        // 但 provider + modelName 被保留，Frontend 可据此提示用户重新输入 Key
        return new ModelConfig(meta.provider(), "", meta.modelName(), null);
    }

    private void cleanExpiredSessions() {
        long now = System.currentTimeMillis();

        // 1. 内存清理：移除超时的活跃会话（30 分钟无活动）
        int before = sessions.size();
        sessions.entrySet().removeIf(entry -> {
            if (entry.getValue().isExpired(SESSION_TIMEOUT_MS)) {
                entry.getValue().getAgent().persistMessages(); // 持久化最后一次
                log.info("Session {} expired from memory (last accessed: {})",
                        entry.getKey(), entry.getValue().getLastAccessedAt());
                return true;
            }
            return false;
        });
        int removed = before - sessions.size();
        if (removed > 0) {
            log.info("Cleaned {} expired sessions from memory, {} remaining", removed, sessions.size());
        }

        // 2. 磁盘清理：超过 7 天未修改的持久化文件（.json + .kryo）
        File dir = new File(persistenceDir);
        // 辅助函数：按扩展名清理
        java.util.function.BiConsumer<String, String> purgeByExt = (ext, suffix) -> {
            File[] files = dir.listFiles((d, n) -> n.endsWith(ext));
            if (files != null) {
                for (File f : files) {
                    if (now - f.lastModified() > DISK_TTL_MS) {
                        String id = f.getName().replace(suffix, "");
                        deleteSessionFiles(id);
                        log.info("Purged stale disk session: {} (age: {} days)",
                                id, (now - f.lastModified()) / (24 * 60 * 60 * 1000));
                    }
                }
            }
        };
        purgeByExt.accept(".json", ".json");
        purgeByExt.accept(".kryo", ".kryo");
    }

    /**
     * 磁盘配额控制：超过最大保留数时，按最后修改时间淘汰最旧的会话。
     * 在每次创建新会话后调用，确保磁盘不会无限增长。
     */
    private void enforceDiskQuota() {
        File dir = new File(persistenceDir);
        // 收集所有数据文件（.json + .kryo，排除 .tmp 和 .meta）
        File[] allDataFiles = dir.listFiles((d, n)
                -> (n.endsWith(".json") && !n.endsWith(".json.tmp") && !n.endsWith(".meta.json"))
                || n.endsWith(".kryo"));
        if (allDataFiles == null || allDataFiles.length <= MAX_DISK_SESSIONS) return;

        // 按最后修改时间升序（最旧的在前）
        Arrays.sort(allDataFiles, Comparator.comparingLong(File::lastModified));
        int toDelete = allDataFiles.length - MAX_DISK_SESSIONS;
        for (int i = 0; i < toDelete; i++) {
            String name = allDataFiles[i].getName();
            String id = name.replace(".json", "").replace(".kryo", "");
            // 跳过当前内存中的活跃会话，只删纯磁盘文件
            if (sessions.containsKey(id)) continue;
            deleteSessionFiles(id);
            log.info("Disk quota: purged oldest session {} ({} total → {} max)",
                    id, allDataFiles.length, MAX_DISK_SESSIONS);
        }
    }

    /**
     * 仅删除磁盘文件（.json + .kryo + .meta.json + .tmp），不影响内存活跃会话。
     */
    private void deleteSessionFiles(String sessionId) {
        chatMemory.clear(sessionId);
        File metaFile = getMetaFile(sessionId);
        if (metaFile.exists()) {
            metaFile.delete();
        }
    }

    // ==================== 取消机制 ====================

    /** 请求取消指定会话的 Agent 执行 */
    public void requestCancel(String sessionId) {
        java.util.concurrent.atomic.AtomicBoolean flag = cancelFlags.computeIfAbsent(
                sessionId, k -> new java.util.concurrent.atomic.AtomicBoolean(false));
        flag.set(true);
        // 同时设置 Agent 实例的取消标志（如果存在）
        AgentSession session = sessions.get(sessionId);
        if (session != null) {
            session.getAgent().setCancelled(true);
        }
        log.info("Cancel requested for session: {}", sessionId);
    }

    /** 检查指定会话是否已被取消 */
    public boolean isCancelled(String sessionId) {
        java.util.concurrent.atomic.AtomicBoolean flag = cancelFlags.get(sessionId);
        return flag != null && flag.get();
    }

    /** 清除取消标志（Agent 停止后调用） */
    public void clearCancelFlag(String sessionId) {
        cancelFlags.remove(sessionId);
    }

    /**
     * 根据 Provider 类型配置 Agent 的工具调用限制。
     * <p>
     * DashScope API 不支持单消息多工具调用（返回 "only one tool call is supported"），
     * 需要在 prompt 层面限制 LLM 每次只选一个工具。OpenAI / Anthropic 不限。
     */
    private void configureMaxToolsPerStep(DevAssistantAgent agent, ModelConfig config) {
        String provider = (config != null) ? config.effectiveProvider() : ModelConfig.DEFAULT_PROVIDER;
        if (ModelConfig.PROVIDER_DASHSCOPE.equals(provider)) {
            agent.setMaxToolsPerStep(1);
            log.debug("Session agent set to single-tool mode (DashScope)");
        }
    }

    @PreDestroy
    public void shutdown() {
        cleaner.shutdown();
        sessions.clear();
    }
}
