---
category: react-pattern
tags: [ReAct, Tool Calling, Function Calling, Agent Loop, 思考链]
summary: ReAct 推理-行动循环模式详解，包含代码实现与工具设计原则
---

# ReAct 模式与工具调用

## 什么是 ReAct 模式

ReAct（**Re**asoning + **Act**ing）是 AI Agent 最经典的执行模式。Agent 在每一步都经历三个环节：

```
思考（Think）→ 行动（Act）→ 观察（Observe）→ 思考（Think）→ ...
```

直到任务完成或达到最大步数。

## ReAct 循环的代码实现

### 类继承结构

```
BaseAgent              ← 状态机 + 步骤循环 + SSE 流式输出
  └─ ReActAgent        ← 定义 step() = think() → act() 模板方法
       └─ ToolCallAgent ← 实现基于 LLM Function Calling 的 think/act
            └─ DevAssistantAgent ← 注入角色、工具列表、时间感知
```

### Think 阶段

Think 阶段调用 LLM，传入：
- 系统提示词（System Prompt）
- 对话历史（messageList）
- 可用工具列表（ToolCallbacks）

LLM 返回：
- 推理文本（thinking text）："我需要先搜索一下..."
- 工具调用列表（Tool Calls）：[web_search(query="...")]

关键代码（ToolCallAgent.java）：
```java
ChatResponse chatResponse = getChatClient().prompt(prompt)
    .system(getSystemPrompt())
    .toolCallbacks(availableTools)
    .call()
    .chatResponse();
```

### Act 阶段

Act 阶段执行 LLM 选择的工具：
1. 提取 AssistantMessage 中的 ToolCall 列表
2. 调用 ToolCallingManager 执行每个工具
3. 将 ToolResponseMessage 追加到 messageList
4. 检查是否调用了 terminate 工具

关键代码（ToolCallAgent.java）：
```java
ToolExecutionResult result = toolCallingManager.executeToolCalls(prompt, chatResponse);
// 将工具返回追加到消息列表
getMessageList().add(toolResponseMessage);
```

### 终止条件

Agent 在以下情况停止：
1. LLM 调用了 `doTerminate` 工具 → state = FINISHED
2. 达到最大步数（maxSteps=20）→ state = FINISHED
3. 出现不可恢复的错误 → state = ERROR

## 思考链可视化

BaseAgent.runStreamStructured() 方法通过 SSE 推送结构化事件：

```
step_start → thinking → tool_call × N → tool_result × N → step_end
                                                              ↓
                                                       final_answer
                                                              ↓
                                                       agent_finish
```

每个事件是一个 AgentEvent JSON：
```json
{"type":"thinking","stepNumber":1,"content":"需要先搜索AI新闻...","timestamp":1718000000}
{"type":"tool_call","stepNumber":1,"toolName":"web_search","toolInput":"{\"query\":\"...\"}","timestamp":1718000000}
{"type":"tool_result","stepNumber":1,"toolName":"web_search","toolOutput":"搜索结果...","timestamp":1718000000}
```

## 工具设计原则

### 1. 工具描述要精确

LLM 根据工具的 `@Tool(description="...")` 来决定调用哪个工具。描述模糊会导致 LLM 选错工具。
- 好的描述：`"Search for information from Bing Search Engine. Returns title, URL, and snippet."`
- 坏的描述：`"Search"`

### 2. 参数要自解释

`@ToolParam(description="...")` 要清楚地说明参数含义和格式。
- 好的描述：`"Name of the file to save the generated PDF, should end with .pdf"`
- 坏的描述：`"file name"`

### 3. 错误信息要有指导性

工具返回的错误信息应该让 LLM 知道如何调整策略：
- 好的错误：`"Access denied (HTTP 403). The site may block automated access. Try a different source."`
- 坏的错误：`"Error"`

### 4. 返回内容要控制长度

工具返回给 LLM 的内容太长会撑爆上下文窗口。需要在工具层面做截断：
- WebScrapingTool：截断 body text 到 8000 字符
- TerminalOperationTool：截断输出到 4000 字符
- WebSearchTool：只返回前 5 条结果

## 常见问题与解决方案

### 问题 1：工具重复调用失败

LLM 用相同的参数反复调用失败的工具。**解决**：在工具返回中给出具体的失败原因和替代建议。

### 问题 2：工具调用死循环

LLM 在多个工具间来回切换但无法完成任务。**解决**：设置 maxSteps 上限 + 在 System Prompt 中强调效率。

### 问题 3：工具返回内容未进入最终回复

LLM 的最后一条 thinking text 是真正的最终回复，但前端只拼接了 step_end（工具返回）。**解决**：新增 `final_answer` 事件类型，独立承载 LLM 的最终总结。
