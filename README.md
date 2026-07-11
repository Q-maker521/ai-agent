# AI Agent Engineering Platform

一个面向工程落地的 AI Agent 项目，基于 Spring Boot 3、Spring AI 和 Vue 3 构建，覆盖 ReAct Agent Loop、工具调用、RAG 知识库、多模型兼容、SSE 流式输出和思考链可视化。

线上演示：http://8.139.5.187:8090/

## 项目亮点

- ReAct Agent：实现 Think -> Act -> Observe 的循环执行模型，支持最大步数控制、状态机流转和任务取消。
- Tool Calling：内置联网搜索、网页抓取、文件读写、终端命令、PDF 生成、资源下载等工具，并统一注册到 Agent。
- 思考链可视化：后端通过 JSON SSE 推送 `thinking`、`thinking_delta`、`tool_call`、`tool_result`、`final_answer` 等事件，前端实时渲染执行过程。
- RAG 知识库：支持 Markdown/PDF/Word/TXT 文档上传、Tika 解析、文本切分、关键词增强、向量化和相似度检索。
- 多模型兼容：支持 DashScope、OpenAI、Anthropic 以及 OpenAI-Compatible API，用户可在前端配置 API Key、模型名和 Base URL。
- 会话管理：支持会话创建、重命名、删除、恢复、持久化记忆和过期清理。
- 工程化上线：前后端分离，本项目已在 Windows 云服务器上通过计划任务完成自启动部署。

## 技术栈

| 模块 | 技术 |
| --- | --- |
| 后端 | Java 21, Spring Boot 3.4, Spring AI 1.0 |
| Agent | ReAct, Tool Calling, SSE, Session Memory |
| RAG | Spring AI VectorStore, Tika, TokenTextSplitter, PGVector 可选 |
| 前端 | Vue 3, Vite, Markdown 渲染, SSE 客户端 |
| 工具 | Tavily Search, Bing/DuckDuckGo fallback, Jsoup, iText PDF |
| 部署 | Maven, npm, Windows Server Scheduled Task |

## 架构概览

```text
Vue 3 Frontend
  - ChatRoom / ThinkingChain / KnowledgeRag / Settings
  - 通过 SSE 接收 Agent 执行事件
        |
        v
Spring Boot API
  - AiController: Agent 对话、RAG 问答、会话管理
  - ModelConfigController: 多模型配置
  - HealthController: 健康检查
        |
        v
Agent Core
  - BaseAgent: 状态机、步骤循环、取消控制
  - ReActAgent: think/act 模板流程
  - ToolCallAgent: LLM 工具调用和事件流
  - DevAssistantAgent: 面向开发任务的 Agent 实例
        |
        v
Tools / RAG / Memory
  - WebSearch, WebScrape, File, Terminal, PDF, Download
  - VectorStore, DocumentLoader, QueryRewrite, KeywordEnricher
  - FileBasedChatMemory, AgentSessionManager
```

## 快速启动

### 1. 环境要求

- JDK 21+
- Maven 3.9+
- Node.js 18+

### 2. 配置环境变量

复制环境变量模板：

```bash
cp .env.example .env
```

至少配置一个可用模型提供商。示例：

```bash
DASHSCOPE_API_KEY=your-api-key
TAVILY_API_KEY=your-tavily-key
```

也可以不在 `.env` 中写死 Key，直接在前端 Settings 页面填写模型配置。`.env` 文件已被 Git 忽略，请不要提交真实密钥。

### 3. 启动后端

```bash
mvn spring-boot:run
```

默认后端地址：

```text
http://localhost:8123/api
```

健康检查：

```text
http://localhost:8123/api/health
```

### 4. 启动前端

```bash
cd ai-agent-frontend
npm install
npm run dev
```

默认前端地址：

```text
http://localhost:3000
```

## 主要接口

| 接口 | 方法 | 说明 |
| --- | --- | --- |
| `/api/health` | GET | 健康检查 |
| `/api/ai/agent/chat/stream` | GET | Agent SSE 对话 |
| `/api/ai/agent/cancel` | POST | 取消正在执行的 Agent 任务 |
| `/api/ai/agent/sessions` | GET | 获取会话列表 |
| `/api/ai/agent/sessions/{sessionId}` | GET/PATCH/DELETE | 会话详情、重命名、删除 |
| `/api/ai/rag/chat/sse` | GET | RAG SSE 问答 |
| `/api/ai/rag/documents` | GET | 获取知识库文档 |
| `/api/ai/rag/documents/upload` | POST | 上传知识库文档 |
| `/api/ai/config` | GET/POST | 获取或保存模型配置 |

## 目录结构

```text
src/main/java/com/aiagent
  agent/        Agent 状态机、ReAct 循环和工具调用实现
  app/          RAG 问答服务
  chatmemory/   文件持久化会话记忆
  config/       CORS、多模型配置和模型工厂
  controller/   REST API 和 SSE 接口
  memory/       对话摘要记忆
  rag/          文档加载、切分、向量检索和查询增强
  session/      Agent 会话生命周期管理
  tools/        工具实现和搜索引擎适配

ai-agent-frontend
  src/views/        页面视图
  src/components/   聊天、思考链、通知、骨架屏等组件
  src/api/          前端 API 封装

image-search-mcp-server
  独立的图片搜索 MCP 服务示例
```

## 安全说明

- 不要提交 `.env`、真实 API Key、服务器密码、部署压缩包或本地工作文档。
- 前端 Settings 中填写的 Key 仅用于当前会话配置，请在公开演示环境中谨慎使用。
- 终端工具已做命令白名单和危险命令拦截，但生产环境仍建议进一步增加沙箱、权限分级和审计日志。

## 后续优化方向

- 引入更完整的工具调用审计和成本统计。
- 将会话、任务记录和向量索引迁移到数据库。
- 增加多 Agent 协作或 LangGraph/Workflow 编排能力。
- 增加用户登录、配额限制和按用户隔离的 API Key 管理。
