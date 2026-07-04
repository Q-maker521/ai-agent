package com.aiagent.agent;

import cn.hutool.core.util.StrUtil;
import com.aiagent.agent.model.AgentState;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;

import java.util.List;

/**
 * ReAct (Reasoning and Acting) 模式的代理抽象类
 * 实现了思考-行动的循环模式
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
public abstract class ReActAgent extends BaseAgent {

    /**
     * 处理当前状态并决定下一步行动
     *
     * @return 是否需要执行行动，true表示需要执行，false表示不需要执行
     */
    public abstract boolean think();

    /**
     * 执行决定的行动
     *
     * @return 行动执行结果
     */
    public abstract String act();

    /**
     * 执行单个步骤：思考和行动
     *
     * @return 步骤执行结果
     */
    @Override
    public String step() {
        try {
            // 先思考
            boolean shouldAct = think();
            if (!shouldAct) {
                // 自动终止时（连续无工具调用），不返回过期文本。
                // think() 已将状态设为 FINISHED，getLastAssistantText()
                // 会跳过当前步的空消息找到上一步的文本，导致 step_end
                // 携带重复内容，最终前端 finalResponse 出现重复回复。
                if (getState() == AgentState.FINISHED) {
                    return null;
                }
                // 不需要工具调用时，返回 LLM 的实际文本响应而非占位符
                String llmResponse = getLastAssistantText();
                if (StrUtil.isNotBlank(llmResponse)) {
                    return llmResponse;
                }
                return "思考完成 - 无需行动";
            }
            // 再行动
            return act();
        } catch (Exception e) {
            log.error("步骤执行失败", e);
            return "步骤执行失败：" + e.getMessage();
        }
    }

    /**
     * 从消息列表中提取最后一条 AssistantMessage 的文本内容
     */
    private String getLastAssistantText() {
        List<Message> messages = getMessageList();
        for (int i = messages.size() - 1; i >= 0; i--) {
            Message msg = messages.get(i);
            if (msg instanceof AssistantMessage assistantMsg) {
                String text = assistantMsg.getText();
                if (StrUtil.isNotBlank(text)) {
                    return text;
                }
            }
        }
        return null;
    }

}
