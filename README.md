# AI 智能研发助手 Agent

**基于 ReAct 模式的自主规划 LLM Agent，支持工具调用、RAG 知识库与思考链实时可视化**

[![Java](https://img.shields.io/badge/Java-21-blue)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.4-green)](https://spring.io/projects/spring-boot)
[![Spring AI](https://img.shields.io/badge/Spring%20AI-1.0.0-orange)](https://spring.io/projects/spring-ai)
[![Vue](https://img.shields.io/badge/Vue-3.2-brightgreen)](https://vuejs.org/)
[![License](https://img.shields.io/badge/License-MIT-yellow)](LICENSE)

一个面向生产的 AI Agent，能够自主规划并执行多步骤任务，同时实时可视化其推理过程。基于 **Spring AI 1.0.0** 和 **Vue 3** 构建，展示了当前最前沿的 Agent 架构模式。

---

## ✨ 核心特性

- **自主规划** — ReAct（推理 + 行动）循环，最多 20 步自主执行
- **思考链可视化** — 实时结构化 JSON SSE 事件流：thinking → tool_call → tool_result → final_answer
- **7 个内置工具** — 网页搜索 (Bing)、网页抓取、文件读写、终端命令、PDF 生成、资源下载、任务终止
- **多模型支持** — DashScope / OpenAI / Anthropic / OpenAI 兼容接口，前端 Settings 页面一键切换
- **RAG 知识库** — 检索增强生成，包含查询重写、关键词增强与向量相似度搜索
- **会话管理** — 跨请求对话连续性，会话池自动过期清理
- **MCP 协议** — 通过 stdio/SSE 集成外部 MCP 服务（高德地图、图片搜索）

---

## 🏗 系统架构

```
┌──────────────────────────────────────────────────────────────┐
│                    Vue 3 前端 (ai-agent-frontend)             │
│  Home.vue → AgentChat.vue / KnowledgeRag.vue / Settings.vue │
│  ChatRoom.vue ←── SSE ──→ 后端 API                           │
│  ThinkingChain.vue  ← Agent 推理过程可视化                    │
└──────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌──────────────────────────────────────────────────────────────┐
│                  Spring Boot 后端                              │
│                                                              │
│  AiController                  ModelConfigController         │
│  ├── /ai/rag/chat/*         ← RAG 知识库问答                 │
│  ├── /ai/agent/chat/stream  ← Agent 思考链 (JSON SSE)        │
│  └── /ai/config             ← 多模型配置                      │
│                                                              │
│  Agent 继承链:                                                │
│  BaseAgent → ReActAgent → ToolCallAgent → DevAssistantAgent │
│                                                              │
│  基础设施:                                                    │
│  ├── DynamicChatModelFactory ← 模型工厂分发                   │
│  ├── AgentSessionManager     ← 会话池 + 自动清理              │
│  ├── VectorStore             ← RAG 文档检索                   │
│  └── MCP Client              ← 跨进程工具集成                 │
└──────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌──────────────────────────────────────────────────────────────┐
│  LLM 提供商: DashScope │ OpenAI │ Anthropic │ 兼容接口       │
│  搜索: Bing (免费)     │ 向量存储: 内存 / PGVector            │
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
step_start → thinking → tool_call × N → tool_result × N → step_end
                                                              ↓
                                                       final_answer
                                                              ↓
                                                       agent_finish
```

每一步执行：**思考**（LLM 决定调用哪些工具）→ **行动**（执行工具，记录结果）→ **重复**，直到任务完成或达到最大步数。

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
```

### 启动后端

```bash
# 设置 API Key 并启动
set DASHSCOPE_API_KEY=sk-your-key-here    # Windows
# export DASHSCOPE_API_KEY=sk-your-key-here  # Linux/Mac

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
| DashScope | API Key | qwen-max, qwen-plus, qwen-turbo |
| OpenAI | API Key | gpt-4o, gpt-4o-mini, o3 |
| Anthropic | API Key | claude-sonnet-4-6, claude-opus-4-8 |
| OpenAI 兼容 | API Key + Base URL | deepseek-chat, moonshot-v1 |

配置信息按会话存储在 `localStorage` 中，首次对话时发送到后端。

---

## 🔧 内置工具

| 工具 | 方法 | 说明 |
|------|------|------|
| `web_search` | `searchWeb(query)` | 通过 Bing 搜索网页（免费，无需 API Key） |
| `web_scrape` | `scrapeWebPage(url)` | 抓取并提取网页文本内容 |
| `file_read` | `readFile(name)` | 读取本地文件内容 |
| `file_write` | `writeFile(name, content)` | 写入内容到本地文件 |
| `execute_terminal_command` | `executeTerminalCommand(cmd)` | 执行终端命令（跨平台） |
| `generate_pdf` | `generatePDF(name, content)` | 生成 PDF 文档（支持中文） |
| `download_resource` | `downloadResource(url, name)` | 从远程 URL 下载文件 |
| `terminate` | `doTerminate()` | 任务完成时终止 Agent 会话 |

---

## 📡 API 端点

| 端点 | 方法 | 说明 |
|------|------|------|
| `/api/ai/rag/chat/sync` | GET | RAG 知识库问答（同步） |
| `/api/ai/rag/chat/sse` | GET | RAG 知识库问答（SSE 流式） |
| `/api/ai/agent/chat/stream` | GET | Agent 对话 + 思考链可视化（JSON SSE） |
| `/api/ai/agent/sessions/count` | GET | 活跃会话数监控 |
| `/api/ai/config` | POST | 保存多模型配置 |
| `/api/ai/config` | GET | 获取当前配置 |

---

## 📁 项目结构

```
├── src/main/java/com/aiagent/
│   ├── agent/                        # Agent 核心
│   │   ├── BaseAgent.java            # 状态机 + 步骤循环 + SSE
│   │   ├── ReActAgent.java           # Think → Act 模板方法
│   │   ├── ToolCallAgent.java        # LLM Function Calling 集成
│   │   ├── DevAssistantAgent.java    # 具体 Agent 实例（角色 + 工具 + 提示词）
│   │   ├── model/AgentState.java     # IDLE → RUNNING → FINISHED/ERROR
│   │   └── event/AgentEvent.java     # 结构化 SSE 事件记录
│   ├── session/                      # 会话生命周期
│   │   ├── AgentSession.java         # Agent + ModelConfig 包装
│   │   └── AgentSessionManager.java  # 会话池 + TTL 清理
│   ├── config/                       # 多模型支持
│   │   ├── ModelConfig.java          # Provider + API Key + Model 记录
│   │   ├── ModelConfigController.java # 配置 REST API
│   │   ├── DynamicChatModelFactory.java # 模型工厂分发
│   │   └── ProviderResult.java       # ChatClient + ChatOptions 元组
│   ├── tools/                        # 工具实现
│   │   ├── ToolRegistration.java     # Spring Bean 注册
│   │   ├── WebSearchTool.java        # Bing 搜索（免费）
│   │   ├── WebScrapingTool.java      # 基于 Jsoup 的网页抓取
│   │   ├── FileOperationTool.java    # 本地文件读写
│   │   ├── TerminalOperationTool.java # 跨平台命令执行
│   │   ├── PDFGenerationTool.java    # iText PDF 生成
│   │   ├── ResourceDownloadTool.java # HTTP 文件下载
│   │   └── TerminateTool.java        # 任务完成信号
│   ├── rag/                          # RAG 管道
│   │   ├── VectorStoreConfig.java    # 内存向量库初始化
│   │   ├── KnowledgeDocumentLoader.java # Markdown 文档加载
│   │   ├── DocumentSplitter.java     # 基于 Token 的文本切分
│   │   ├── KeywordEnricher.java      # LLM 关键词提取
│   │   ├── QueryRewriter.java        # LLM 查询重写
│   │   └── PgVectorVectorStoreConfig.java # PostgreSQL 向量库（可选）
│   ├── advisor/                      # 聊天拦截器
│   ├── chatmemory/                   # 对话记忆实现
│   ├── app/KnowledgeBaseService.java # RAG 对话服务
│   └── controller/AiController.java  # REST API 端点
├── src/main/resources/
│   ├── application.yml               # Spring AI + 向量库配置
│   └── document/                     # 知识库 Markdown 文档
│       ├── AI-Agent-基础概念与架构.md
│       ├── ReAct-模式与工具调用.md
│       └── RAG-检索增强生成技术.md
├── ai-agent-frontend/                # Vue 3 前端
│   └── src/
│       ├── views/
│       │   ├── Home.vue              # 首页
│       │   ├── AgentChat.vue         # Agent 对话 + 思考链
│       │   ├── KnowledgeRag.vue      # RAG 知识库问答
│       │   └── Settings.vue          # 多模型配置
│       ├── components/
│       │   ├── ChatRoom.vue          # 聊天界面
│       │   ├── ThinkingChain.vue     # 思考链可视化
│       │   ├── AiAvatarFallback.vue  # AI 头像
│       │   └── AppFooter.vue         # 页脚
│       ├── api/index.js              # API + SSE 封装
│       └── router/index.js           # 路由配置
└── image-search-mcp-server/          # MCP 图片搜索服务
```

---

## 🎯 关键设计决策

1. **手动 ReAct 循环而非自动执行** — Agent 显式控制 Think→Act 循环，每一步发射结构化 JSON 事件供前端可视化，推理过程完全透明。

2. **final_answer 与 step_end 分离** — LLM 的自然语言最终总结通过独立的 `final_answer` 事件发射，与携带工具执行结果的 `step_end` 明确区分，确保聊天面板展示的是 Agent 的真实结论而非原始工具输出。

3. **DynamicChatModelFactory 多模型分发** — 摒弃 Spring Boot 自动配置方式，通过工厂模式按需创建不同 Provider 的 ChatModel，支持运行时根据用户提供的 API Key 动态切换。

4. **优雅降级** — 启动时若 Embedding API 调用失败（免费额度耗尽），向量库以空状态初始化而不阻塞应用启动，对话核心功能仍可正常使用。

5. **免费搜索方案** — 网页搜索基于 Bing 搜索结果页解析，无需注册第三方付费 API，克隆即用。

---

## 📄 许可证

MIT
