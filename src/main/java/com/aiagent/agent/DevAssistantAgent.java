package com.aiagent.agent;

import com.aiagent.config.ModelConfig;
import com.aiagent.config.ProviderResult;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.tool.ToolCallback;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * AI 智能研发助手 — 基于 ReAct (Reasoning + Acting) 模式的自主规划 Agent。
 *
 * <h3>架构说明</h3>
 * <pre>
 *   用户请求
 *     │
 *     ▼
 *   BaseAgent.runStream()         ← 状态机 + 步骤循环（最大 20 步）
 *     │
 *     ▼
 *   ReActAgent.step()            ← Think → Act 模板方法
 *     │
 *     ├── ToolCallAgent.think()  ← 调用 LLM 决策：需要哪些工具？
 *     │
 *     └── ToolCallAgent.act()    ← 执行工具调用，记录结果到消息上下文
 *     │
 *     ▼
 *   SSE 流式输出（每步结果实时推送前端）
 * </pre>
 *
 * <h3>可用工具</h3>
 * <ul>
 *   <li>WebSearch — 百度搜索引擎检索</li>
 *   <li>WebScraping — 网页内容抓取解析</li>
 *   <li>FileOperation — 文件读写</li>
 *   <li>TerminalOperation — 终端命令执行</li>
 *   <li>PDFGeneration — PDF 报告生成</li>
 *   <li>ResourceDownload — 网络资源下载</li>
 * </ul>
 *
 * @see BaseAgent 状态机与步骤循环
 * @see ReActAgent Think-Act 模式
 * @see ToolCallAgent LLM 工具调用实现
 */
public class DevAssistantAgent extends ToolCallAgent {

    // 保存原始 system prompt，供 setPlanIntoPrompt 追加计划
    private String originalSystemPrompt;

    /**
     * 将执行计划注入到 system prompt 中。
     * <p>
     * 只在首次调用时保存原始 prompt，后续调用直接替换（不重复追加）。
     * 通过 {@link com.aiagent.agent.plan.SimplePlanner#formatPlanForPrompt}
     * 格式化计划文本。
     */
    public void setPlanIntoPrompt(String planText) {
        if (planText == null || planText.isBlank()) {
            return;
        }
        if (this.originalSystemPrompt == null) {
            this.originalSystemPrompt = getSystemPrompt();
        }
        setSystemPrompt(this.originalSystemPrompt + planText);
    }

    /**
     * 使用 ProviderResult 构造 Agent（推荐方式，支持多 Provider）。
     */
    public DevAssistantAgent(ToolCallback[] allTools, ProviderResult providerResult) {
        super(allTools, providerResult.chatOptions());
        this.setChatClient(providerResult.chatClient());
        initPrompts();
    }

    /**
     * 使用已创建好的 ChatClient + ChatOptions 构造 Agent。
     */
    public DevAssistantAgent(ToolCallback[] allTools, ChatClient chatClient, ChatOptions chatOptions) {
        super(allTools, chatOptions);
        this.setChatClient(chatClient);
        initPrompts();
    }

    private void initPrompts() {
        this.setName("DevAssistant");
        // 动态注入当前日期时间，让 LLM 知道实时时间
        String currentDateTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String SYSTEM_PROMPT = """
                你是一个 AI 智能助手。

                当前时间: %s

                你可以使用工具来搜索网页、抓取内容、读写文件、执行终端命令、
                生成 PDF 和下载资源。根据用户的需求自行判断是否需要调用工具。

                始终使用中文回复。
                """.formatted(currentDateTime);
        this.setSystemPrompt(SYSTEM_PROMPT);
        this.setNextStepPrompt(null);
        this.setMaxSteps(20);
    }
}
