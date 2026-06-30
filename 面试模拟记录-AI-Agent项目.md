# AI 智能研发助手 Agent — 面试模拟记录

> 面试官视角，围绕项目架构、设计决策、生产化问题展开三轮递进式提问。

---

## 第一轮：架构理解与设计决策

---

### Q1：三层继承 vs 单层实现

**问题：**

你的 Agent 用了 `BaseAgent → ReActAgent → ToolCallAgent → DevAssistantAgent` 四层继承链。面试中常被问：**为什么不用一个类搞定？每多一层抽象，你是在解决什么问题？如果产品需求说"我们要支持一种非 ReAct、基于 Plan-Execute 的新 Agent 模式"，你现在的设计能多快响应？**

**我的回答：**

采用三层继承模式首先可以实现单个模块只负责单个功能——BaseAgent 只需要负责基本代码循环、状态机、流式输出，ReActAgent 负责推理模式，ToolCallAgent 实现工具的调用，DevAssistantAgent 管理配置（系统提示词 + 循环步数上限）。实现了代码解耦，如果要修改某些地方改动较少甚至可以直接添加，保证了代码的高可用性。如果需要新的模式 Plan-Execute，我可以直接实现这个类并继承 BaseAgent，基本不需要太多改动。

**问题分析及答案：**

你的回答停在"是什么"，面试官想听的是**"当 X 变化时，只有 Y 需要改"**。

`ReActAgent` 和 `ToolCallAgent` 分开的真正原因是它们代表了**两个独立的变化维度**：

| 类 | 变化维度 | 问题 |
|---|---|---|
| `ReActAgent` | **推理模式** | "怎么从 LLM 响应中决定下一步？" |
| `ToolCallAgent` | **工具执行机制** | "用什么机制调用工具？" |

换一个推理模式——比如不用 Function Calling，而是让 LLM 输出 `{"tool": "search", "args": {...}}` JSON 文本然后自己解析——只需要写一个新的 `JsonParseAgent extends ReActAgent`，**工具执行那套代码完全不用动**。这才是分开的真正价值：**沿着变化维度切分**。

> 💡 面试技巧：用"当 X 变化时，只有 Y 需要改"论证设计选择，比笼统说"解耦"有力得多。

---

### Q1.2：`instanceof` 是不是设计坏味道？

**问题：**

继承链深了会有一个代价——子类对父类的内部实现细节越来越敏感。你这个项目里，`BaseAgent.emitFinalAnswerIfAvailable()` 做了一个 `instanceof ToolCallAgent` 的判断。**这是设计上的"坏味道"吗？如果是你，会怎么消除它？**

**我的回答：**

我并没有意识到这个问题。

**问题分析及答案：**

是的，这是坏味道。父类不应该知道自己有哪些子类，这违反了**依赖倒置原则（DIP）**。

**修复方案**——使用模板方法模式（Hook Method）：

```java
// BaseAgent 中：定义一个钩子方法，子类选择性覆盖
protected String getFinalAnswer() {
    return null;  // 默认：没有最终答案
}

// emitFinalAnswerIfAvailable 改为：
String finalText = getFinalAnswer();
if (StrUtil.isNotBlank(finalText)) {
    sendJsonEvent(sseEmitter, AgentEvent.finalAnswer(currentStep, finalText));
}

// ToolCallAgent 覆盖：
@Override
protected String getFinalAnswer() {
    return this.lastThinkingText;
}
```

父类只管"有没有最终答案、有就发事件"，子类自己决定"什么是最终答案"。**这才是各层只关心自己该关心的。**

---

### Q2：为什么是 SSE 而不是 WebSocket？

**问题：**

Agent 的思考链是一连串单向的事件推送。你选了 SSE。**但如果某天产品说"用户要能在 Agent 执行中途打断它、发一个新指令"，SSE 还能胜任吗？你会怎么改？**

**我的回答：**

不能胜任。如果我想要修改的话，我可能会从状态管理部分入手——当用户再次发出新指令时，状态标志为 FINISHED 然后重新接收指令。

**问题分析及答案：**

你抓到了"状态机"切入点，但方案有隐患：

**追问**：你把 state 设为 FINISHED 然后"重新收指令"，但此时 `messageList` 里已经堆了 N 步的工具调用历史。新指令是接在旧历史上继续，还是清空重来？如果清空，用户刚才让 Agent 搜到的半成品数据就丢了；如果不清空，上下文会越来越长、token 成本越来越高。

**优化方案**：引入一个专门的状态（如 `INTERRUPTED`），被打断时保留最后 3 轮对话作为"滑动窗口"缓冲区，用户的新指令可以选择基于缓冲区继续、还是彻底清空重来。

**关于 SSE vs WebSocket 的本质**：

| | SSE | WebSocket |
|---|---|---|
| 方向 | 单向（服务端 → 客户端） | 双向 |
| 协议 | 纯 HTTP | 升级后的 TCP |
| 复杂度 | 极低（浏览器原生 `EventSource`） | 需要心跳、重连逻辑 |
| 适用场景 | "我推你看"（Agent 步骤推送） | "我们对话"（协同编辑、即时通讯） |

你的项目是单向推送 Agent 步骤，选 SSE 完全正确。**SSE 不是"不够好所以没用 WebSocket"，而是"恰好够用所以不引入 WebSocket 的复杂度"**——这是工程判断力。

---

### Q3：Function Calling 的"黑盒"问题

**问题：**

`ToolCallAgent.think()` 里有一行很关键的代码：

```java
String thinkingText = assistantMessage.getText();
if (StrUtil.isBlank(thinkingText)) {
    thinkingText = extractReasoningContent(assistantMessage);
}
```

**你为什么要从 metadata 的 `reasoningContent` 字段捞推理文本？这说明不同 LLM 厂商的 Function Calling 实现有什么差异？如果你要接入一个不返回任何推理文本的模型，思考链可视化还能工作吗？**

**我的回答：**

因为不同厂商返回推理文本时的位置不同，有差异，不能在固定位置寻找文本。如果 LLM 本身并不返回任何文本，那么可视化功能就不能工作了。

**问题分析及答案：**

你定位到了表象（不同厂商返回位置不同），但还可以挖得更深。当前代码只在两处找：
- `assistantMessage.getText()`
- `metadata.get("reasoningContent")`

但其他模型（Anthropic Claude 等）的推理文本可能在**不同的 metadata key** 或完全在 **thinking 块**里。更好的做法是在 `DynamicChatModelFactory` 里为每个 Provider 注册一个 `ReasoningExtractor` 策略，不同模型用不同提取逻辑。

**当模型完全不返回推理文本时的替代方案**：

可以用 **Prompt-induced Reasoning（提示诱导推理）**——在 System Prompt 中要求 LLM 将推理过程写入 JSON 字段再返回，然后解析。但这个方法有代价：

- **CoT 隐式化**：推理被"折叠"进 JSON 字段后，模型在生成 JSON 之前的隐式推理链变短，**推理质量可能下降**
- **格式脆弱性**：小模型可能输出不合法 JSON，需要额外解析容错

正确的工程折衷：**优先用原生 reasoningContent，获取不到时 fallback 到 Prompt 诱导方案**——和项目中 VectorStoreConfig 的优雅降级是同一种设计基因。

---

## 第二轮：系统设计与知识广度

---

### Q4：工具系统的扩展性

**问题：**

你现在有 7 个内置工具 + 2 个 MCP 工具。**如果我要加第 8 个内置工具——比如"发送邮件"——我要改哪些文件？能不能做到只新增一个文件就完成工具注册？** 你当前的 `ToolRegistration.java` 设计是否支持这种"零修改注册"？

**我的回答：**

如果要加新的工具，首先就是在 availableTools 中添加该工具的具体使用代码，然后将该工具简要信息放在 ToolRegistration 中即可，需要时才读取其全部内容。我当前的工具支持这种方式。

**问题分析及答案：**

当前设计**做不到"只加一个文件"**。看代码 `ToolRegistration.java:16-31`，每加一个工具需要改两处：
1. `new XxxTool()` 实例化
2. `ToolCallbacks.from(...)` 参数列表加一项

这是**修改已有类**，不是零修改。

**Spring 已经准备好了更优雅的做法**——让每个工具自己声明为 Spring Bean，然后用自动收集：

```java
// 改造后：ToolRegistration 完全不需要改
@Bean
public ToolCallback[] allTools(List<ToolCallback> toolCallbacks) {
    return toolCallbacks.toArray(ToolCallback[]::new);
}

// 加新工具只需新建一个文件：
@Component
public class EmailTool implements ToolCallback {
    @Override
    public ToolDefinition getToolDefinition() { ... }
    @Override
    public String call(String toolInput) { ... }
}
```

这样**新增工具 = 新增一个文件**，不碰任何已有代码。这就是**开闭原则（OCP）**：对扩展开放，对修改封闭。

---

### Q5：上下文窗口管理

**问题：**

你的 Agent 最大 20 步，每一步的 `think()` 都会把完整 `messageList` 发给 LLM。如果每步工具返回 2000 字，20 步就是 4 万字上下文。**你有没有想过当上下文超过模型窗口时会发生什么？如果在你的项目里解决这个问题，你会从哪个环节切入？**

**我的回答：**

确实，上下文窗口管理对于一个 Agent 来说是至关重要的。如果上下文窗口爆满，那么一些关键性的数据也许会被丢掉，对整个项目来说造成的问题是非常大的。如果要解决这个问题的话，我会单独加一个管理上下文窗口类，将这些历史对话统一管理，包括存储用户的关键信息、设置窗口阈值、压缩文档内容、汲取关键信息以及清理无关内容。

**问题分析及答案：**

方向全对，但少了一个关键维度——你只覆盖了**被动防御**（窗口快满了再处理），没提**主动防御**。

**完整的上下文窗口管理应该有三个层次**：

| 层 | 策略 | 作用 |
|------|------|---------|
| **工具层** | Tool Result Truncation | 防止单步返回撑爆窗口——工具原始输出进入 messageList 之前截断，只取前 2000 字符 + `[truncated]` 标记 |
| **会话层** | Sliding Window | 保留最近 N 轮完整历史，旧轮次移出 |
| **记忆层** | Summary Compression | 旧历史不直接丢弃，而是压缩为摘要存入记忆，需要时可检索 |

第三个层次最容易被忽略但最关键——**丢弃不等于遗忘，压缩才是**。你的 `FileBasedChatMemory` 本来是文件级持久化，升级后可以成为摘要记忆的存储层。

Tool Result Truncation 是 Anthropic、OpenAI 官方 Agent 指南里的标准实践——LLM 如果发现被截断、需要更多内容，可以用搜索关键词缩小范围再抓一次。

---

### Q6：安全边界

**问题：**

你的 `TerminalOperationTool` 能执行任意 shell 命令。**如果有人输入"删除系统文件"或"fork 炸弹"，你的 Agent 会怎么处理？** 不要只回答"应该加白名单"——说说白名单应该加在哪个层次、为什么是那个层次。

**我的回答：**

对于安全边界的话最好新加一个单独模块，按一定的规则进行权限开放，比如 `rm -f` 或者一些可能带来严重后果的东西直接拒绝，对于一些正常的权限访问可以请求用户给权限。

**问题分析及答案：**

"按规则分级"是正确的方向，但面试官会追问**安全校验放在哪个层次**：

```
前端          ← ❌ 不要放这里，任何人都能绕过前端
Controller    ← ❌ 也不好，其他调用路径可能不走 Controller
Tool 内部靠后  ← ❌ 工具执行了一半才拦截，可能已经有副作用
Tool 入口     ← ✅ 正确位置：Tool.call() 方法的第一行
```

**在 `TerminalOperationTool.call()` 的第一行做校验**，在你启动 `Process` 之前拦截。命令还没执行，零副作用。

**两个额外的纵深防御**：

1. **用 `ProcessBuilder` 而不是 `Runtime.exec()`**：`ProcessBuilder("rm", path)` 不经过 shell 的 glob 展开和管道解释，注入风险大幅降低。这是 OWASP 标准的 **Command Injection Prevention** 实践。
2. **执行隔离**：Docker/VM 级别沙箱执行 + 超时自动 kill + 磁盘/网络配额限制。

---

## 第三轮：开放性设计题

---

### Q7：生产化改造

**问题：**

假设这个项目明天要上线给 1000 个用户使用。**按优先级列出你要解决的 5 个问题**，并说明为什么这个顺序。

**我的回答：**

1. 权限校验模块
2. 考虑并发问题
3. 上下文窗口管理
（暂时没有其他能想到的了）

**参考答案：**

按"不修就会出事 → 不出事但难用 → 好用但不够强"的优先级排序：

| 优先级 | 问题 | 为什么 |
|--------|------|--------|
| 🔴 1 | **API Key 安全** | 现在用户的 API Key 通过 REST 明文传输存储，泄露就是账单灾难。需要：Key 服务端加密存储 + HTTPS + 用量监控告警 |
| 🔴 2 | **Tool 执行沙箱** | `TerminalOperationTool` 无限制执行——删库跑路。需要：Docker/VM 隔离 + 命令白名单 + 超时 kill |
| 🟡 3 | **并发会话隔离** | `ConcurrentHashMap` 本身线程安全，但 `AgentSession` 内部没有并发控制——同一 sessionId 两个请求可能让 state 混乱。需要：单 session 操作串行化（Actor 模型或 `synchronized`） |
| 🟡 4 | **上下文窗口 + 成本控制** | 1000 用户 × 20 步 × 4 万 token/步 = 天文数字 API 账单。需要：工具结果截断 + 步骤预算 + 用户级别配额 |
| 🟢 5 | **健康检查 + 降级** | LLM API 挂了怎么办？需要：熔断器（Circuit Breaker）+ 友好降级提示而非堆栈追踪 |

---

### Q8：Agent 的可观测性

**问题：**

现在你的 Agent 出错了，你只能看 `log.error("error executing agent", e)`。**如果让你设计一套 Agent 的可观测体系，除了日志，你还会加什么？** 提示：想想指标、追踪、告警三个维度。

**我的回答：**

对于这方面确实没有太多经验。

**参考答案——可观测性三支柱：**

```
┌─────────────────────────────────────────────┐
│  告警层：什么异常需要人工介入？               │
│  例：5分钟内错误率 > 10% → 钉钉/飞书通知      │
├─────────────────────────────────────────────┤
│  追踪层：一次对话的完整链路是什么？            │
│  例：user request → step1(think→web_search)   │
│       → step2(think→file_write) → final       │
│       每一步耗时 + token消耗 + 工具调用参数     │
├─────────────────────────────────────────────┤
│  指标层：系统整体什么状态？                    │
│  例：活跃会话数、平均完成步数、工具调用分布     │
│      LLM 延迟 P50/P99、token 消耗趋势          │
└─────────────────────────────────────────────┘
```

你的 `AgentEvent` 已经是追踪的雏形了——每个事件都有 `timestamp`。下一步就是把它们结构化存入日志（JSON 格式），用 ELK / Grafana 消费。这个领域叫 **LLM Observability**（LangSmith、LangFuse 等工具就是干这个的）。

---

### Q9：模型选择策略

**问题：**

你现在让用户在 Settings 页面手动选模型。但不同任务难度不同——"帮我搜一下今天天气"和"帮我设计一个数据库表结构并生成 SQL"——用一个模型显然不经济。**如果你要引入一个"模型路由器"来自动决策用哪个模型，你会基于什么规则来路由？**

**我的回答：**

模型路由了解也不是很多。

**参考答案——模型路由策略表：**

模型路由的核心矛盾是**成本 vs 能力 vs 延迟**的三角权衡：

| 策略 | 做法 | 适用场景 |
|------|------|---------|
| **任务复杂度分类** | 用一个极便宜的模型先做意图识别，输出 `{complexity: "simple"\|"medium"\|"complex"}`，然后路由到不同模型 | 通用 |
| **能力匹配** | 分析任务是否需要特定能力（代码 → deepseek、长文本 → 128K 模型、图片 → 多模态） | 多能力需求 |
| **级联（Cascade）** | 先用小模型试，输出质量不够再升级到大模型——"不满意就重来一次" | 成本敏感 |
| **用户分级** | 免费用户用小模型，付费用户用大模型 | 商业驱动 |

**最简单落地的版本**：System Prompt 里加一句分类指令，用当前同样的模型先走一步"任务分析"步骤（不计入 maxSteps），根据分析结果选择后续使用的实际模型。

---

## 面试总结

| 维度 | 当前水平 | 提升方向 |
|------|---------|---------|
| **项目理解** | ✅ 能讲清楚各模块职责和调用链路 | 把"是什么"升级为"为什么这么设计" |
| **设计原则** | ⚠️ 有直觉但不精确 | 用 OCP/SRP/DIP 等术语论证你的选择 |
| **问题诊断** | ✅ Q3 能定位到多厂商差异 | 给出解决方案而不只是定位问题 |
| **生产意识** | ⚠️ 安全/观测/成本尚缺 | 这是正常的新手盲区，多读生产事故复盘 |
| **知识广度** | ⚠️ WebSocket/MCP/观测等边缘领域待扩展 | 每个概念知道"解决了什么问题"即可 |

### 面试话术速记

> 我实现了一个基于 **ReAct 模式**的自主规划 AI Agent。核心设计是**三层抽象**——BaseAgent 管理状态机和步骤循环，ReActAgent 定义 Think-Act 的模板方法，ToolCallAgent 通过 Spring AI 的 Function Calling 机制让 LLM 自主决策调用哪些工具。
>
> 技术亮点包括：**多 Provider 动态切换**（4 种 AI 厂商运行时热切换，通过策略模式实现）、**结构化 SSE 事件流**（将 Agent 内部推理过程通过 10 种事件类型实时推送到前端渲染成思考链可视化）、**会话池管理**（ConcurrentHashMap + 定时清理过期会话）、以及 **RAG 优雅降级**（向量库初始化失败不阻塞应用启动）。
>
> 前端用 Vue 3 实现了增量消费 SSE 事件流的思考链组件，后端集成了 7 个内置工具 + MCP 协议扩展。

### 延伸学习方向

1. **多 Agent 协作**：当前是单 Agent，可以引入"主编排 Agent + 子执行 Agent"的层级结构
2. **工具生成动态化**：当前工具是硬编码的，可以探索 MCP 协议的动态工具发现
3. **记忆系统升级**：当前是文件持久化的消息列表，可以升级为向量化长期记忆 + 摘要记忆
4. **LLM Observability**：了解 LangSmith / LangFuse 等可观测平台
5. **生产级 Agent 架构**：查阅 Anthropic 和 OpenAI 的官方 Agent 最佳实践指南
