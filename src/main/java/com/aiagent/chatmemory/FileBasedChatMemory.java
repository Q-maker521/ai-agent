package com.aiagent.chatmemory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 基于 JSON 文件持久化的对话记忆。
 *
 * <p>使用 Jackson 将消息列表序列化为人类可读的 JSON 文件，替代旧版 Kryo 二进制格式。
 * 新会话使用 {@code .json} 扩展名，旧 {@code .kryo} 文件在首次读取时自动迁移。
 *
 * <h3>线程安全</h3>
 * 所有公共方法均加同步锁，防止并发读写导致文件损坏。
 *
 * <h3>原子写入</h3>
 * 先写入 {@code .json.tmp} 临时文件，完成后 rename 到目标文件，
 * 防止写入过程中 JVM 崩溃导致数据文件损坏。
 *
 * <h3>向后兼容</h3>
 * {@link #get(String)} 优先读取 {@code .json} 文件，
 * 不存在时尝试 {@code .kryo} 文件并自动迁移为 JSON 格式。
 */
@Slf4j
public class FileBasedChatMemory implements ChatMemory, ChatMemoryRepository {

    private final String baseDir;

    private static final ObjectMapper objectMapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    /**
     * @param dir 持久化文件的存储目录
     */
    public FileBasedChatMemory(String dir) {
        this.baseDir = dir;
        File baseDirFile = new File(dir);
        if (!baseDirFile.exists()) {
            baseDirFile.mkdirs();
        }
    }

    /**
     * 追加消息到已有对话（ChatMemory 接口实现）。
     */
    @Override
    public synchronized void add(String conversationId, List<Message> messages) {
        List<Message> existing = getOrCreateConversation(conversationId);
        existing.addAll(messages);
        writeToFile(conversationId, existing);
    }

    /**
     * 覆盖写入完整消息列表，用于 Agent 全量保存 messageList。
     */
    public synchronized void save(String conversationId, List<Message> messages) {
        writeToFile(conversationId, new ArrayList<>(messages));
    }

    @Override
    public synchronized List<Message> get(String conversationId) {
        return getOrCreateConversation(conversationId);
    }

    @Override
    public synchronized void clear(String conversationId) {
        File jsonFile = getConversationFile(conversationId);
        File tmpFile = getTmpFile(conversationId);
        File kryoFile = getKryoFile(conversationId);

        if (jsonFile.exists()) jsonFile.delete();
        if (tmpFile.exists()) tmpFile.delete();
        if (kryoFile.exists()) kryoFile.delete();
    }

    /**
     * 检查指定会话的持久化文件是否存在（.json 或旧 .kryo）。
     */
    public synchronized boolean exists(String conversationId) {
        return getConversationFile(conversationId).exists()
                || getKryoFile(conversationId).exists();
    }

    // ==================== ChatMemoryRepository 接口 ====================

    @Override
    public synchronized void deleteByConversationId(String conversationId) {
        clear(conversationId);
    }

    @Override
    public synchronized void saveAll(String conversationId, List<Message> messages) {
        save(conversationId, messages);
    }

    @Override
    public synchronized List<Message> findByConversationId(String conversationId) {
        return get(conversationId);
    }

    @Override
    public List<String> findConversationIds() {
        File dir = new File(baseDir);
        File[] files = dir.listFiles((d, name)
                -> name.endsWith(".json") && !name.endsWith(".json.tmp"));
        if (files == null) return Collections.emptyList();
        List<String> ids = new java.util.ArrayList<>();
        for (File f : files) {
            ids.add(f.getName().replace(".json", ""));
        }
        // 也包含仅有旧 .kryo 文件的会话
        File[] kryoFiles = dir.listFiles((d, name) -> name.endsWith(".kryo"));
        if (kryoFiles != null) {
            for (File f : kryoFiles) {
                String id = f.getName().replace(".kryo", "");
                if (!ids.contains(id)) {
                    ids.add(id);
                }
            }
        }
        return ids;
    }

    // ==================== 内部实现 ====================

    /**
     * 从文件读取会话消息列表。
     * 优先读取 JSON 格式，不存在时尝试迁移旧 Kryo 文件。
     */
    private List<Message> getOrCreateConversation(String conversationId) {
        File jsonFile = getConversationFile(conversationId);
        File tmpFile = getTmpFile(conversationId);

        // 1. 优先读 JSON
        if (jsonFile.exists()) {
            try {
                return readJsonArray(jsonFile);
            } catch (IOException e) {
                log.error("Failed to read JSON file {}: {}", jsonFile.getName(), e.getMessage());
                return new ArrayList<>();
            }
        }

        // 2. 孤儿 .tmp 恢复：如果 json 不存在但 tmp 存在，
        //    说明上次写入在 rename 前崩溃了
        if (tmpFile.exists()) {
            log.warn("Found orphaned .tmp file for session {}, recovering", conversationId);
            try {
                if (tmpFile.renameTo(jsonFile)) {
                    return readJsonArray(jsonFile);
                }
                // rename 失败（Windows 上目标可能刚好被创建），直接读 tmp
                List<Message> messages = readJsonArray(tmpFile);
                jsonFile.delete();
                tmpFile.renameTo(jsonFile);
                return messages;
            } catch (IOException e) {
                log.error("Failed to recover orphan .tmp for {}: {}", conversationId, e.getMessage());
                return new ArrayList<>();
            }
        }

        // 3. 向后兼容：尝试迁移旧 Kryo 文件
        File kryoFile = getKryoFile(conversationId);
        if (kryoFile.exists()) {
            List<Message> messages = readKryoFile(kryoFile);
            if (messages != null && !messages.isEmpty()) {
                // 迁移为 JSON 格式
                writeToFile(conversationId, messages);
                kryoFile.delete();
                log.info("Migrated session {} from Kryo to JSON ({} messages)",
                        conversationId, messages.size());
                return messages;
            }
            log.warn("Failed to read Kryo file for session {}, returning empty", conversationId);
            return new ArrayList<>();
        }

        return new ArrayList<>();
    }

    /**
     * JSON 序列化写入，使用原子写策略：
     * 写临时文件 → 删除旧文件 → rename。
     */
    private void writeToFile(String conversationId, List<Message> messages) {
        File targetFile = getConversationFile(conversationId);
        File tmpFile = getTmpFile(conversationId);

        try {
            // 1. 写入临时文件（手动 Message→Map 转换 + Jackson 序列化）
            writeJsonArray(tmpFile, messages);

            // 2. 原子 rename（Windows 需先删除目标）
            if (targetFile.exists()) {
                targetFile.delete();
            }
            if (!tmpFile.renameTo(targetFile)) {
                // Windows 极端情况：重试一次
                targetFile.delete();
                if (!tmpFile.renameTo(targetFile)) {
                    log.error("Atomic rename failed for session {}", conversationId);
                    // 不删 tmp，下次读取时可通过恢复逻辑找回
                    return;
                }
            }
        } catch (IOException e) {
            tmpFile.delete(); // 清理失败的临时文件
            log.error("Failed to write conversation {}", conversationId, e);
        }
    }

    // ==================== JSON 读写（手动 Message↔Map 转换） ====================

    /**
     * 将消息列表序列化为 List&lt;Map&gt; 后写入 JSON。
     * <p>
     * 不使用 Jackson 的多态序列化，而是将每个 Message 转为
     * {@code {"messageType": "...", "text": "...", ...}} 的简单 Map，
     * 反序列化时再根据 messageType 重建对应的 Spring AI Message 对象。
     */
    private void writeJsonArray(File file, List<Message> messages) throws IOException {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Message msg : messages) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("messageType", msg.getMessageType().name());
            String text = msg.getText();
            map.put("text", text != null ? text : "");

            // 保留 ToolResponseMessage 中的 responses 列表（含工具名和返回数据）
            if (msg instanceof ToolResponseMessage trm && trm.getResponses() != null) {
                List<Map<String, String>> responses = new ArrayList<>();
                for (ToolResponseMessage.ToolResponse r : trm.getResponses()) {
                    Map<String, String> rm = new LinkedHashMap<>();
                    rm.put("id", r.id());
                    rm.put("name", r.name());
                    rm.put("responseData", r.responseData());
                    responses.add(rm);
                }
                map.put("responses", responses);
            }

            // 保留 AssistantMessage 中的 toolCalls（含工具名和参数）
            if (msg instanceof AssistantMessage am && am.getToolCalls() != null) {
                List<Map<String, Object>> toolCalls = new ArrayList<>();
                for (AssistantMessage.ToolCall tc : am.getToolCalls()) {
                    Map<String, Object> tcm = new LinkedHashMap<>();
                    tcm.put("id", tc.id());
                    tcm.put("name", tc.name());
                    tcm.put("arguments", tc.arguments());
                    toolCalls.add(tcm);
                }
                map.put("toolCalls", toolCalls);
            }

            list.add(map);
        }
        objectMapper.writeValue(file, list);
    }

    /**
     * 从 JSON 读取并重建 Spring AI Message 对象列表。
     */
    private List<Message> readJsonArray(File file) throws IOException {
        List<Map<String, Object>> list = objectMapper.readValue(
                file, new TypeReference<List<Map<String, Object>>>() {});
        List<Message> messages = new ArrayList<>();
        for (Map<String, Object> map : list) {
            String type = (String) map.get("messageType");
            String text = (String) map.getOrDefault("text", "");
            MessageType messageType = MessageType.valueOf(type);

            switch (messageType) {
                case USER -> messages.add(new UserMessage(text));
                case ASSISTANT -> {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> toolCallsRaw =
                            (List<Map<String, Object>>) map.get("toolCalls");
                    if (toolCallsRaw != null && !toolCallsRaw.isEmpty()) {
                        List<AssistantMessage.ToolCall> toolCalls = new ArrayList<>();
                        for (Map<String, Object> tc : toolCallsRaw) {
                            toolCalls.add(new AssistantMessage.ToolCall(
                                    (String) tc.get("id"),
                                    "function", // Spring AI 固定类型
                                    (String) tc.get("name"),
                                    (String) tc.get("arguments")));
                        }
                        messages.add(new AssistantMessage(text, Map.of(), toolCalls));
                    } else {
                        messages.add(new AssistantMessage(text));
                    }
                }
                case SYSTEM -> messages.add(new SystemMessage(text));
                case TOOL -> {
                    @SuppressWarnings("unchecked")
                    List<Map<String, String>> responsesRaw =
                            (List<Map<String, String>>) map.get("responses");
                    if (responsesRaw != null && !responsesRaw.isEmpty()) {
                        List<ToolResponseMessage.ToolResponse> responses = new ArrayList<>();
                        for (Map<String, String> r : responsesRaw) {
                            responses.add(new ToolResponseMessage.ToolResponse(
                                    r.get("id"), r.get("name"), r.get("responseData")));
                        }
                        messages.add(new ToolResponseMessage(responses, Map.of()));
                    }
                }
                default -> {
                    // 未知类型 → 尝试作为 UserMessage 处理
                    log.warn("Unknown message type: {}, treating as UserMessage", type);
                    messages.add(new UserMessage(text));
                }
            }
        }
        return messages;
    }

    // ==================== Kryo 兼容读取（仅用于迁移） ====================

    private static final com.esotericsoftware.kryo.Kryo kryo = initKryo();

    private static com.esotericsoftware.kryo.Kryo initKryo() {
        com.esotericsoftware.kryo.Kryo k = new com.esotericsoftware.kryo.Kryo();
        k.setRegistrationRequired(false);
        k.setInstantiatorStrategy(new org.objenesis.strategy.StdInstantiatorStrategy());
        return k;
    }

    private List<Message> readKryoFile(File file) {
        try (com.esotericsoftware.kryo.io.Input input =
                     new com.esotericsoftware.kryo.io.Input(new java.io.FileInputStream(file))) {
            return kryo.readObject(input, ArrayList.class);
        } catch (Exception e) {
            log.error("Failed to read Kryo file {} ({} bytes): {}",
                    file.getName(), file.length(), e.getMessage());
            return null;
        }
    }

    // ==================== 文件路径 ====================

    private File getConversationFile(String conversationId) {
        return new File(baseDir, conversationId + ".json");
    }

    private File getTmpFile(String conversationId) {
        return new File(baseDir, conversationId + ".json.tmp");
    }

    private File getKryoFile(String conversationId) {
        return new File(baseDir, conversationId + ".kryo");
    }
}
