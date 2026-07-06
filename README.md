# AI 智能助手 Agent

**基于 ReAct 模式的自主规划 LLM Agent，支持工具调用、RAG 知识库与思考链实时可视化**

[![Java](https://img.shields.io/badge/Java-21-blue)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.4-green)](https://spring.io/projects/spring-boot)
[![Spring AI](https://img.shields.io/badge/Spring%20AI-1.0.0-orange)](https://spring.io/projects/spring-ai)
[![Vue](https://img.shields.io/badge/Vue-3.2-brightgreen)](https://vuejs.org/)
[![License](https://img.shields.io/badge/License-MIT-yellow)](LICENSE)

一个全栈 AI Agent 平台，**全权交由 LLM 自主决策**——Agent 自行判断何时搜索、何时停止、使用哪个工具。基于 **Spring AI 1.0.0** 和 **Vue 3** 构建，支持思考链实时可视化、多 LLM 提供商切换、7 个内置工具、RAG 知识库问答和 MCP 外部工具集成。

---

## ✨ 核心特性

- **全权 LLM 自主决策** — 不编排任何使用流程，LLM 根据用户需求自行判断是否调用工具、何时停止
- **ReAct 自主规划** — 推理 + 行动循环，最多 20 步自主执行
- **思考链可视化** — 实时结构化 JSON SSE 事件流：`thinking` → `thinking_delta`（逐 token）→ `tool_call` → `tool_result` → `final_answer`
- **Token 级流式输出** — 打字机效果，实时逐字推送 LLM 回复
- **并行工具调用** — 多工具并发执行（OpenAI/Anthropic），DashScope 自动降级为单工具模式
- **7 个内置工具** — 网页搜索（Tavily API + Bing + DDG 多引擎聚合）、网页抓取（智能正文提取）、文件读写、终端命令、PDF 生成、资源下载
- **多模型支持** — DashScope / OpenAI / Anthropic / OpenAI 兼容接口，前端 Settings 页面一键切换
- **RAG 知识库** — 检索增强生成，含查询重写、关键词增强、向量相似度搜索、**用户文档上传**（PDF/Word/Markdown/TXT 等，Tika 解析 + Token 切分 + 向量化）、重启自动恢复
- **会话管理** — 跨请求对话连续性，JSON 持久化记忆，会话池 30min 自动过期 + 7 天磁盘 TTL + 20 个数量上限
- **Esc 键即时停止** — 随时中断 Agent 执行，后端线程同步取消
- **显式任务规划** — 复杂任务先出执行计划再行动
- **增量摘要记忆** — 超长对话自动 LLM 摘要压缩，保留关键信息
- **MCP 协议** — 通过 stdio 集成外部 MCP 服务（高德地图、图片搜索）
- **Apple 风格 UI** — 毛玻璃质感、柔光阴影、统一缓动动画、Markdown 渲染 + 代码高亮

---

## 🏗 系统架构

```
┌──────────────────────────────────────────────────────────────┐
│                    Vue 3 前端 (ai-agent-frontend)             │
│  Home.vue → AgentChat.vue / KnowledgeRag.vue / Settings.vue │
│  ChatRoom.vue ←── SSE ──→ 后端 API                           │
│  ThinkingChain.vue  ← Agent 推理过程可视化（独立面板）         │
│  Toast.vue / ShortcutPanel.vue / Skeleton.vue                │
└──────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌──────────────────────────────────────────────────────────────┐
│                  Spring Boot 后端 (localhost:8123/api)        │
│                                                              │
│  AiController                  ModelConfigController         │
│  ├── /ai/rag/chat/*         ← RAG 知识库问答                 │
│  ├── /ai/agent/chat/stream  ← Agent 思考链 (JSON SSE)        │
│  ├── /ai/agent/sessions/*   ← 会话 CRUD + 修复端点           │
│  └── /ai/config             ← 多模型配置                      │
│                                                              │
│  Agent 继承链:                                                │
│  BaseAgent → ReActAgent → ToolCallAgent → DevAssistantAgent │
│                                                              │
│  基础设施:                                                    │
│  ├── DynamicChatModelFactory ← 模型工厂分发                   │
│  ├── AgentSessionManager     ← 会话池 + 30min TTL + 7天磁盘   │
│  ├── ConversationSummarizer  ← 增量摘要记忆                   │
│  ├── SimplePlanner           ← 任务规划                       │
│  ├── FileBasedChatMemory     ← JSON 持久化记忆（原子写入）     │
│  ├── SearchAggregator        ← 多引擎搜索聚合（Tavily/Bing/DDG）│
│  ├── ContentExtractor        ← 智能正文提取                   │
│  ├── VectorStore             ← RAG 文档检索                   │
│  └── MCP Client              ← 跨进程工具集成                 │
└──────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌──────────────────────────────────────────────────────────────┐
│  LLM 提供商: DashScope │ OpenAI │ Anthropic │ 兼容接口       │
│  搜索: Tavily API + Bing + DuckDuckGo (多引擎聚合)            │
│  向量存储: 内存 / PGVector                                    │
└──────────────────────────────────────────────────────────────┘
```

### Agent 状态机

```
IDLE ──→ RUNNING ──→ FINISHED
              │
              └──→ ERROR
```

### 思考链事件流

```
agent_start → step_start → thinking → thinking_delta × N (逐 token 流式)
              → tool_call × N → tool_result × N → step_end
                                                      ↓
                                               final_answer
                                                      ↓
                                               agent_finish
```

每一步执行：**思考**（LLM 自主决定调用哪些工具）→ **行动**（执行工具，记录结果）→ **重复**，直到 LLM 自行判断任务完成。

### 架构哲学

```
人工编排流程     ←—————— 光谱 ——————→     全权 LLM 自主
高效、可预测                        灵活、但行为不可控
```

本项目选择**全自主端**：System Prompt 只声明身份和工具存在，不指导何时用、何时停。LLM 的每个决策都是其智能的真实体现。

---

## 🚀 快速开始

### 环境要求

- JDK 21+
- Maven 3.9+
- Node.js 18+（前端）

### 环境配置

```bash
# 复制环境变量模板并填入真实值
cp .env.example .env
# 编辑 .env，至少填写:
#   DASHSCOPE_API_KEY=sk-xxx    (必填，默认提供商)
#   TAVILY_API_KEY=tvly-xxx     (推荐，提升搜索质量)
```

### 启动后端

```bash
# 设置 API Key 并启动
set DASHSCOPE_API_KEY=sk-your-key-here        # Windows
set TAVILY_API_KEY=tvly-your-key-here         # Windows (可选)
# export DASHSCOPE_API_KEY=sk-your-key-here   # Linux/Mac
# export TAVILY_API_KEY=tvly-your-key-here    # Linux/Mac (可选)

mvn spring-boot:run
```

后端启动于 **http://localhost:8123/api**

### 启动前端

```bash
cd ai-agent-frontend
npm install
npm run dev
```

前端开发服务器启动于 **http://localhost:3000**

### 多模型配置

打开 **http://localhost:3000/settings** 配置你的 LLM 提供商:

| 提供商 | 所需配置 | 模型示例 |
|--------|---------|---------|
| DashScope | API Key | qwen-plus-latest, qwen-max, qwen-turbo |
| OpenAI | API Key | gpt-4o, gpt-4o-mini, o3 |
| Anthropic | API Key | claude-sonnet-4-6, claude-opus-4-8 |
| OpenAI 兼容 | API Key + Base URL | deepseek-chat, moonshot-v1 |

配置信息按会话存储在 `localStorage` 中，首次对话时发送到后端。

---

## 🔧 内置工具

| 工具 | 方法 | 说明 |
|------|------|------|
| `web_search` | `searchWeb(query)` | 多引擎搜索聚合（Tavily API + Bing + DDG），智能去重 + 质量评分 |
| `web_scrape` | `scrapeWebPage(url)` | 抓取网页并智能提取正文（去噪、文本密度算法） |
| `file_read` | `readFile(name)` | 读取本地文件内容 |
| `file_write` | `writeFile(name, content)` | 写入内容到本地文件 |
| `execute_terminal_command` | `executeTerminalCommand(cmd)` | 执行终端命令（双层安全校验，约 45 个白名单命令） |
| `generate_pdf` | `generatePDF(name, content)` | 生成 PDF 文档（支持中文，iText 7） |
| `download_resource` | `downloadResource(url, name)` | 从远程 URL 下载文件（30s 超时） |

### 搜索引擎架构

```
SearchEngine (接口)
├── TavilySearchEngine   → Tavily REST API（AI Agent 专用，最精准）
├── BingSearchEngine      → Bing HTML 抓取（免费 fallback）
└── DuckDuckGoSearchEngine → DDG HTML 抓取（国内不可用，静默降级）

SearchAggregator
  并发查询 → URL 去重 → 质量评分(0-100) → 排序 → Top 6
```

### MCP 外部工具

| 工具 | 来源 | 功能 |
|------|------|------|
| 🗺️ 高德地图 | `@amap/amap-maps-mcp-server` | 地理/地图服务 |
| 🖼️ 图片搜索 | `image-search-mcp-server` | Pexels 图片搜索 |

---

## 📡 API 端点

| 端点 | 方法 | 说明 |
|------|------|------|
| `/api/health` | GET | 健康检查 |
| `/api/ai/agent/chat/stream` | GET | Agent 对话 + 思考链可视化（JSON SSE） |
| `/api/ai/agent/cancel` | POST | 取消正在执行的 Agent 任务 |
| `/api/ai/agent/sessions` | GET | 列出所有会话（内存 + 磁盘） |
| `/api/ai/agent/sessions/count` | GET | 当前活跃会话数 |
| `/api/ai/agent/sessions/context` | GET | 导出指定会话的完整对话上下文 |
| `/api/ai/agent/sessions/{sessionId}` | GET | 获取单个会话详情 |
| `/api/ai/agent/sessions/{sessionId}` | DELETE | 删除会话（内存 + 磁盘文件） |
| `/api/ai/agent/sessions/{sessionId}` | PATCH | 更新会话元数据（如重命名标题） |
| `/api/ai/agent/sessions/repair` | POST | [调试] 扫描修复持久化文件 |
| `/api/ai/rag/chat/sync` | GET | RAG 知识库问答（同步） |
| `/api/ai/rag/chat/sse` | GET | RAG 知识库问答（SSE 流式） |
| `/api/ai/rag/documents` | GET | 知识库文档目录（classpath + 用户上传） |
| `/api/ai/rag/documents/upload` | POST | 上传文档到知识库（multipart/form-data） |
| `/api/ai/rag/documents/{filename}` | DELETE | 删除用户上传的文档 |
| `/api/ai/config` | POST | 保存多模型配置 |
| `/api/ai/config` | GET | 获取当前配置 |

---

## 📁 项目结构

```
├── src/main/java/com/aiagent/
│   ├── agent/                        # Agent 核心
│   │   ├── BaseAgent.java            # 状态机 + 步骤循环 + SSE + 心跳 + 取消
│   │   ├── ReActAgent.java           # Think → Act 模板方法
│   │   ├── ToolCallAgent.java        # LLM Function Calling + 并行工具 + 流式输出
│   │   ├── DevAssistantAgent.java    # Agent 实例（角色 + 工具 + 最小化提示词）
│   │   ├── model/AgentState.java     # IDLE → RUNNING → FINISHED/ERROR
│   │   └── event/AgentEvent.java     # 结构化 SSE 事件记录（含 thinking_delta）
│   ├── session/                      # 会话生命周期
│   │   ├── AgentSession.java         # Agent + ModelConfig 包装
│   │   └── AgentSessionManager.java  # 会话池 + 30min TTL + 7天磁盘 + 20上限 + 取消
│   ├── config/                       # 多模型支持
│   │   ├── ModelConfig.java          # Provider + API Key + Model 记录
│   │   ├── ModelConfigController.java # 配置 REST API
│   │   ├── DynamicChatModelFactory.java # 模型工厂分发（DashScope/OpenAI/Anthropic/兼容）
│   │   ├── ProviderResult.java       # ChatClient + ChatOptions 元组
│   │   └── CorsConfig.java           # CORS 配置
│   ├── tools/                        # 工具实现
│   │   ├── ToolRegistration.java     # Spring Bean 注册
│   │   ├── WebSearchTool.java        # 多引擎搜索聚合
│   │   ├── WebScrapingTool.java      # 基于 Jsoup 的网页抓取 + 正文提取
│   │   ├── FileOperationTool.java    # 本地文件读写
│   │   ├── TerminalOperationTool.java # 跨平台命令执行（双层安全校验）
│   │   ├── PDFGenerationTool.java    # iText PDF 生成
│   │   ├── ResourceDownloadTool.java # HTTP 文件下载
│   │   ├── TerminateTool.java        # 任务完成信号（已弃用，保留类文件）
│   │   ├── search/                   # ★ 多引擎搜索架构
│   │   │   ├── SearchEngine.java     # 搜索引擎接口
│   │   │   ├── TavilySearchEngine.java # Tavily API（AI Agent 专用）
│   │   │   ├── BingSearchEngine.java  # Bing HTML 抓取
│   │   │   ├── DuckDuckGoSearchEngine.java # DDG HTML 抓取
│   │   │   ├── SearchAggregator.java  # 并发聚合 + 去重 + 质量评分
│   │   │   └── SearchResult.java      # 统一搜索结果 record
│   │   └── utils/
│   │       └── ContentExtractor.java  # 智能正文提取（文本密度算法）
│   ├── rag/                          # RAG 管道
│   │   ├── VectorStoreConfig.java    # 内存向量库初始化
│   │   ├── KnowledgeDocumentLoader.java # Markdown 文档加载 + FrontMatter 解析
│   │   ├── DocumentSplitter.java     # 基于 Token 的文本切分
│   │   ├── KeywordEnricher.java      # LLM 关键词提取
│   │   ├── QueryRewriter.java        # LLM 查询重写
│   │   ├── ContextualQueryAugmenterFactory.java # 上下文增强
│   │   ├── RagCustomAdvisorFactory.java # RAG Advisor
│   │   └── PgVectorVectorStoreConfig.java # PostgreSQL 向量库（可选）
│   ├── advisor/                      # 聊天拦截器
│   │   ├── LoggingAdvisor.java       # 请求/响应日志
│   │   └── ReReadingAdvisor.java     # Re2 重读策略
│   ├── chatmemory/
│   │   └── FileBasedChatMemory.java  # JSON 持久化聊天记忆（原子写入 + 旧 Kryo 迁移）
│   ├── memory/
│   │   └── ConversationSummarizer.java # LLM 增量摘要记忆
│   ├── plan/
│   │   ├── Plan.java                 # 任务计划 record
│   │   └── SimplePlanner.java        # LLM 任务规划器
│   ├── app/KnowledgeBaseService.java # RAG 对话服务
│   ├── controller/
│   │   ├── AiController.java         # Agent + RAG + 会话管理 REST API
│   │   ├── GlobalExceptionHandler.java # 统一异常处理（7 个 handler）
│   │   └── HealthController.java     # 健康检查
│   └── AiAgentApplication.java       # Spring Boot 启动类
├── src/main/resources/
│   ├── application.yml               # Spring AI + 向量库 + 多 Provider + Tavily 配置
│   ├── mcp-servers.json              # MCP 进程配置
│   └── document/                     # 知识库 Markdown 文档
│       ├── AI-Agent-基础概念与架构.md
│       ├── ReAct-模式与工具调用.md
│       └── RAG-检索增强生成技术.md
├── ai-agent-frontend/                # Vue 3 前端
│   └── src/
│       ├── views/
│       │   ├── Home.vue              # 首页（统计 count-up + 渐入动画）
│       │   ├── AgentChat.vue         # Agent 对话 + 思考链（毛玻璃 Header）
│       │   ├── KnowledgeRag.vue      # RAG 知识库问答
│       │   └── Settings.vue          # 多模型配置（下划线 tabs）
│       ├── components/
│       │   ├── ChatRoom.vue          # 聊天界面（Markdown 渲染 + 代码高亮）
│       │   ├── ThinkingChain.vue     # 思考链可视化（入场动画 + JSON 着色）
│       │   ├── Toast.vue             # 全局通知（success/error/info）
│       │   ├── ShortcutPanel.vue     # ? 键快捷键面板
│       │   ├── Skeleton.vue          # 负载骨架屏
│       │   ├── AiAvatarFallback.vue  # AI 头像
│       │   └── AppFooter.vue         # 页脚
│       ├── api/index.js              # API + SSE 封装
│       ├── router/index.js           # 路由配置
│       └── style.css                 # Apple Blue 设计系统
└── image-search-mcp-server/          # MCP 图片搜索服务
```

---

## 🎯 关键设计决策

1. **全权 LLM 自主决策** — System Prompt 只声明身份 + 工具存在 + 语言偏好（~10 行），不编写任何工作流指令。LLM 自行判断何时搜索、何时停止、用哪个工具。`nextStepPrompt = null`，每步不注入任何 per-step 指令。

2. **手动 ReAct 循环** — Agent 显式控制 Think→Act 循环，每一步发射结构化 JSON 事件供前端可视化，推理过程完全透明。

3. **思考链与聊天面板严格分离** — `thinking`/`thinking_delta` 只流向思考链面板，`final_answer` 是聊天面板唯一数据源。用户看不到 LLM 内部推理过程。

4. **DynamicChatModelFactory 多模型分发** — 摒弃 Spring Boot 自动配置，通过工厂模式按需创建不同 Provider 的 ChatModel，支持运行时动态切换。

5. **多引擎搜索 + 智能正文提取** — Tavily API（AI Agent 专用）+ Bing + DDG 三层并发，SearchAggregator 去重 + 质量评分，ContentExtractor 文本密度算法提取正文。

6. **JSON 持久化记忆（手动 Message↔Map 转换）** — 放弃 Kryo 二进制序列化，采用人类可读的 JSON 格式 + 原子写入（`.tmp` → rename），避免 Spring AI 升级导致反序列化失败。

7. **三层会话生命周期** — 30min 内存 TTL（灵活响应）+ 7 天磁盘 TTL（持久化）+ 20 个数量上限（防堆积）。

8. **三层纵深防御（工具调用安全）** — 提示词引导 + 工具描述内置使用指南 + 硬上限兜底（`MAX_SAME_TOOL_CALLS=5`），防止工具调用死循环。

9. **优雅降级** — Embedding API 失败时向量库以空状态初始化不阻塞启动；搜索引擎逐个降级（Tavily → Bing → DDG）；LLM 摘要失败回退简单截断。

10. **Token 级流式 + 同步等待** — 内部使用 `Flux<ChatResponse>` 逐 token 推送 `thinking_delta`，外部 `blockLast()` 保持 Agent 循环同步语义不变。

11. **DashScope OpenAI 兼容端点** — DashScope 原生 API 不支持 `qwen3.x` 新模型（返回 "url error"）。`DynamicChatModelFactory` 对 DashScope 使用 `OpenAiApi` 指向 `compatible-mode/v1` 端点，同时兼容新旧全部模型，对上层透明。

12. **RAG 模块化架构** — 基于 Spring AI `RetrievalAugmentationAdvisor` 统一检索增强管道：`VectorStoreDocumentRetriever` 负责一次检索（`topK=5`, `similarityThreshold=0.4`），`ContextualQueryAugmenter` 处理检索结果增强，检索为空时注入兜底指令禁止 LLM 凭空编造。消除旧方案的双次向量查询。

13. **RAG Advisor 链简化** — 从 `EmptyContextAdvisor` + `QuestionAnswerAdvisor`（各自独立检索，2 次向量查询）迁移到单一 `RetrievalAugmentationAdvisor`（1 次检索，内部流转）。代码量减少 ~100 行，为后续添加 `DocumentPostProcessor`（重排序/元数据过滤）奠定架构基础。

14. **Prompt 控制权重归用户** — 全量审计 57 个文件中的 prompt / tool description / system message，消除 4 处诱导 LLM 自主生成 PDF 的漏洞链。核心原则：System Prompt 明确 5 条约束，LLM 仅执行用户明确要求的操作，不主动添加、保存、生成文件。

15. **Tika 多格式文档解析** — 引入 `spring-ai-tika-document-reader` 依赖，支持 PDF / DOCX / PPTX / HTML / EPUB / TXT 等 15+ 格式。`.md` 文件走 MarkdownDocumentReader 保持 front-matter 兼容，其他格式统一走 TikaDocumentReader 自动检测并提取文本。

16. **用户文档上传管线** — `DocumentUploadService` 完整处理：校验（类型白名单 + 50MB 限制）→ 存盘到 `docs/user-uploads/` → 解析 → TokenTextSplitter 切分（200 token/chunk）→ KeywordEnricher LLM 关键词增强 → `vectorStore.add()` 向量化 → `.index.json` 索引追踪。`@PostConstruct` 启动恢复确保重启不丢失。`synchronized` 保证线程安全。

17. **文档管理侧边栏** — RAG 页面左侧 300px 侧边栏（对标 Agent 会话管理栏），支持展开/收起/拖拽上传/文件选择/删除文档，Toast 即时反馈操作结果。收起态显示 56px 图标条。

---

## 📚 详细文档

- [WORKING_DOC.md](./WORKING_DOC.md) — 完整工作文档：架构详解、代码地图、API 接口、配置速查、调试技巧、全部 22 轮修复/增强记录
- [项目问题回顾与解决方案.md](./项目问题回顾与解决方案.md) — 50+ 问题诊断与修复：根因分析、方案对比、经验总结

---

## 📄 许可证

MIT
