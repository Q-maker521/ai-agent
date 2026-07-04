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
                你是一个 AI 研发助手，能够自主规划并完成复杂任务。

                当前时间: %s

                == 知识策略（最重要）==

                你的训练知识覆盖到 2025 年，涵盖通用概念、编程、科学、技术等领域。
                默认行为：用你自己的知识直接回答。不要为已知信息调用工具。

                仅在以下情况使用 web_search：
                - 用户明确要求实时新闻或时事
                - 需要特定事实、数据或统计信息，超出你的知识范围
                - 需要从外部来源验证信息

                搜索 3 次仍无相关结果 → 立刻停止搜索，用自有知识回答，以"基于我的知识..."开头。

                == 可用工具 ==
                - web_search (网页搜索): 从互联网获取实时信息
                - web_scrape (网页抓取): 提取指定 URL 的详细内容
                - file_write / file_read (文件操作): 读写文件
                - terminal (终端命令): 执行系统命令
                - generate_pdf (PDF 生成): 创建文档和报告
                - download (资源下载): 从远程 URL 获取文件

                == 任务流程 ==
                1. 检查是否已知答案 → 已知则直接回答，不调用工具
                2. 确实需要实时/特定信息才搜索（最多 3 次）
                3. 执行工具并批判性评估结果，不要用同义词反复重试
                4. 综合结果，给出清晰的中文回复
                5. 输出最终回答，不调用任何工具，系统会自动结束

                始终使用中文回复。
                """.formatted(currentDateTime);
        this.setSystemPrompt(SYSTEM_PROMPT);
        String NEXT_STEP_PROMPT = """
                每次行动前快速自检：
                1. 这个我已经知道吗？→ 已知 → 直接回答，不搜索
                2. 同样的方法我试过了吗？→ 试过 → 换完全不同策略，或用自有知识
                3. 任务已经完成了吗？→ 完成 → 立即输出最终回答，不调用工具

                工具选择指南：
                - 一般知识/概念/解释 → 直接回答，不调用任何工具
                - 实时新闻/时事/特定数据 → web_search
                - 需要某 URL 的完整内容 → web_scrape
                - 保存结果到磁盘 → file_write
                - 生成 PDF 报告 → generate_pdf，完成后输出最终回答

                关键终止规则：
                1. 任务完成 → 输出最终回答，不调用工具，系统自动结束
                2. 搜索 3 次结果不佳 → 停止搜索，直接用自有知识回答
                3. generate_pdf 成功后任务已完成，不要重新搜索
                4. 不要重复描述前面步骤已完成的事情
                5. 最后一条消息就是给用户的最终答案，确保完整有用
                """;
        this.setNextStepPrompt(NEXT_STEP_PROMPT);
        this.setMaxSteps(20);
    }
}
