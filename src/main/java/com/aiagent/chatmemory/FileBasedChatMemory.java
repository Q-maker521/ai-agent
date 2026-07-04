package com.aiagent.chatmemory;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import lombok.extern.slf4j.Slf4j;
import org.objenesis.strategy.StdInstantiatorStrategy;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.Message;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 基于文件持久化的对话记忆，使用 Kryo 序列化将消息列表写入磁盘。
 *
 * <p>线程安全：所有公共方法均加同步锁，防止并发读写导致文件损坏。
 *
 * <p>与 {@code add()}（追加）不同，{@link #save(String, List)} 是<b>覆盖写入</b>，
 * 适用于 Agent 全量保存 messageList 的场景。
 */
@Slf4j
public class FileBasedChatMemory implements ChatMemory, ChatMemoryRepository {

    private final String BASE_DIR;
    private static final Kryo kryo = new Kryo();

    static {
        kryo.setRegistrationRequired(false);
        // 设置实例化策略（处理无默认构造函数的类）
        kryo.setInstantiatorStrategy(new StdInstantiatorStrategy());
    }

    /**
     * @param dir 持久化文件的存储目录
     */
    public FileBasedChatMemory(String dir) {
        this.BASE_DIR = dir;
        File baseDir = new File(dir);
        if (!baseDir.exists()) {
            baseDir.mkdirs();
        }
    }

    /**
     * 追加消息到已有对话（ChatMemory 接口实现）。
     */
    @Override
    public synchronized void add(String conversationId, List<Message> messages) {
        List<Message> conversationMessages = getOrCreateConversation(conversationId);
        conversationMessages.addAll(messages);
        writeToFile(conversationId, conversationMessages);
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
        File file = getConversationFile(conversationId);
        if (file.exists()) {
            file.delete();
        }
    }

    /**
     * 检查指定会话的持久化文件是否存在。
     */
    public synchronized boolean exists(String conversationId) {
        return getConversationFile(conversationId).exists();
    }

    /**
     * 按会话 ID 删除持久化文件（ChatMemoryRepository 接口要求）。
     */
    @Override
    public synchronized void deleteByConversationId(String conversationId) {
        clear(conversationId);
    }

    /**
     * 批量保存消息（ChatMemoryRepository 接口要求）。
     * 与 save 行为一致，全量覆盖写入。
     */
    @Override
    public synchronized void saveAll(String conversationId, List<Message> messages) {
        save(conversationId, messages);
    }

    /**
     * 按会话 ID 查找消息（ChatMemoryRepository 接口要求）。
     */
    @Override
    public synchronized List<Message> findByConversationId(String conversationId) {
        return get(conversationId);
    }

    /**
     * 返回所有已知的会话 ID 列表（ChatMemoryRepository 接口要求）。
     */
    @Override
    public List<String> findConversationIds() {
        java.io.File dir = new java.io.File(BASE_DIR);
        java.io.File[] files = dir.listFiles((d, name) -> name.endsWith(".kryo"));
        if (files == null) return java.util.Collections.emptyList();
        return java.util.Arrays.stream(files)
                .map(f -> f.getName().replace(".kryo", ""))
                .collect(java.util.stream.Collectors.toList());
    }

    private List<Message> getOrCreateConversation(String conversationId) {
        File file = getConversationFile(conversationId);
        List<Message> messages = new ArrayList<>();
        if (file.exists()) {
            try (Input input = new Input(new FileInputStream(file))) {
                messages = kryo.readObject(input, ArrayList.class);
            } catch (IOException e) {
                log.error("Failed to read conversation {}", conversationId, e);
            }
        }
        return messages;
    }

    private void writeToFile(String conversationId, List<Message> messages) {
        File file = getConversationFile(conversationId);
        try (Output output = new Output(new FileOutputStream(file))) {
            kryo.writeObject(output, messages);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private File getConversationFile(String conversationId) {
        return new File(BASE_DIR, conversationId + ".kryo");
    }
}
