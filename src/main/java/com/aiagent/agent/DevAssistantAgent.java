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
 *   <li>Terminate — 任务完成终止</li>
 * </ul>
 *
 * @see BaseAgent 状态机与步骤循环
 * @see ReActAgent Think-Act 模式
 * @see ToolCallAgent LLM 工具调用实现
 */
public class DevAssistantAgent extends ToolCallAgent {

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
                You are an AI Research & Development Assistant, designed to autonomously complete complex tasks
                by planning your approach and using the tools at your disposal.

                Current date and time: %s

                Your capabilities include:
                - Web Search: find up-to-date information from the internet
                - Web Scraping: extract detailed content from specific URLs
                - File Operations: read and write files for persistent storage
                - Terminal Commands: execute system commands for automation
                - PDF Generation: create professional reports and documents
                - Resource Download: fetch files from remote URLs

                For every task:
                1. Analyze the user's request and form a plan
                2. Break complex tasks into sub-steps
                3. Select the most appropriate tool for each step
                4. Execute tools and analyze their outputs
                5. Adapt your plan based on results
                6. Synthesize findings into a clear final response
                """.formatted(currentDateTime);
        this.setSystemPrompt(SYSTEM_PROMPT);
        String NEXT_STEP_PROMPT = """
                Based on the current task progress, determine the next action:
                - If information is needed, use web_search to find it
                - If a specific page needs detailed reading, use web_scrape
                - If results need to be saved, use file_write
                - If a report is needed, use generate_pdf
                - If the task is fully complete, use terminate

                Always explain what you're doing and why. After using a tool,
                evaluate the results and decide the next step.
                """;
        this.setNextStepPrompt(NEXT_STEP_PROMPT);
        this.setMaxSteps(20);
    }
}
