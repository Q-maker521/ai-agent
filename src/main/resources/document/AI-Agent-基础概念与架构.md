# AI Agent 基础概念与架构

## 什么是 AI Agent

AI Agent（人工智能代理）是一种能够**自主感知环境、制定计划、执行行动**的智能系统。与传统的 LLM 问答不同，Agent 不是被动地回答问题，而是主动地完成任务。

核心公式：**Agent = LLM + Memory + Tools + Planning**

## Agent 的核心组件

### 1. LLM（大语言模型）—— 大脑

LLM 是 Agent 的推理引擎，负责理解任务、分解步骤、做出决策。常用的模型包括：
- GPT-4o / GPT-4.1（OpenAI）
- Claude Opus 4.8 / Claude Sonnet 4.6（Anthropic）
- Qwen-Max / Qwen-Plus（阿里云百炼）
- DeepSeek V3 / R1（DeepSeek）

### 2. Memory（记忆系统）—— 笔记本

记忆系统让 Agent 能够记住对话历史和执行过程，主要分为：

- **短期记忆**：当前对话的上下文窗口，如 MessageWindowChatMemory（滑动窗口保留最近 N 条消息）
- **长期记忆**：跨会话的持久化存储，如向量数据库存储历史交互
- **工作记忆**：当前任务的中间状态，如 ReAct 循环中的 messageList

Spring AI 中的实现：
- `InMemoryChatMemory`：基于内存的对话记忆
- `MessageWindowChatMemory`：滑动窗口记忆，maxMessages=20 表示只保留最近 20 条
- `FileBasedChatMemory`：文件持久化记忆
- `VectorStore`：向量化长期记忆

### 3. Tools（工具）—— 手脚

工具是 Agent 与外部世界交互的能力。常见的工具类型：

- **搜索工具**（WebSearchTool）：调用搜索引擎获取实时信息
- **爬虫工具**（WebScrapingTool）：抓取网页内容
- **文件工具**（FileOperationTool）：读写本地文件
- **终端工具**（TerminalOperationTool）：执行系统命令
- **RAG 检索工具**：从知识库检索文档

Spring AI 中通过 `@Tool` 注解定义工具方法，`ToolCallbacks.from()` 注册为 Spring Bean。

### 4. Planning（规划）—— 行动策略

Agent 需要将复杂任务分解为可执行的步骤。主要策略：

- **ReAct 模式**：Reasoning（推理）→ Acting（执行）→ Observation（观察）循环
- **Plan-and-Execute**：先制定完整计划，再逐步执行
- **Tree of Thoughts**：多路径推理树，选择最优路径

## Agent 的架构模式

### 单一 Agent 架构

```
用户输入 → Agent（LLM + Tools）→ 任务完成
```

适用于简单、明确的任务。

### 多 Agent 协作架构

```
用户输入 → Orchestrator（调度器）
              ├── Agent A（搜索专家）
              ├── Agent B（分析专家）
              └── Agent C（写作专家）
                     ↓
               最终输出
```

适用于复杂、多步骤、需要多领域知识的任务。

## Spring AI 中的 Agent 实现

Spring AI 提供了构建 Agent 的基础设施：

1. **ChatClient**：与 LLM 交互的统一接口
2. **ToolCallback**：工具注册与调用机制
3. **Advisor**：请求拦截器链（类似中间件），可用于 RAG、日志、记忆等
4. **VectorStore**：向量存储，支持 SimpleVectorStore（内存）、PgVectorStore（PostgreSQL）等
5. **ChatMemory**：对话记忆管理

## 关键设计权衡

| 维度 | 简单方案 | 复杂方案 |
|------|---------|---------|
| 记忆 | 内存 ChatMemory | Redis + 向量数据库 |
| 工具调用 | 同步顺序调用 | 异步并发 + 超时控制 |
| 错误处理 | try-catch 返回错误信息 | 自动重试 + 降级策略 |
| 模型 | 单模型 | 多模型路由（简单任务用小模型，复杂任务用大模型） |
