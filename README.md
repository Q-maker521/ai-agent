# AI Research Assistant Agent

**An Autonomous LLM Agent with ReAct Pattern, Tool Calling, RAG, and Streaming Visualization**

[![Java](https://img.shields.io/badge/Java-21-blue)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.4-green)](https://spring.io/projects/spring-boot)
[![Spring AI](https://img.shields.io/badge/Spring%20AI-1.0.0-orange)](https://spring.io/projects/spring-ai)
[![Vue](https://img.shields.io/badge/Vue-3.2-brightgreen)](https://vuejs.org/)
[![License](https://img.shields.io/badge/License-MIT-yellow)](LICENSE)

A production-oriented AI Agent that autonomously plans, executes multi-step tasks, and visualizes its reasoning process in real-time. Built with **Spring AI 1.0.0** and **Vue 3**, demonstrating state-of-the-art Agent architecture patterns.

---

## ✨ Key Features

- **Autonomous Planning** — ReAct (Reasoning + Acting) loop with up to 20 autonomous steps
- **Thinking Chain Visualization** — Real-time structured JSON SSE events: thinking → tool_call → tool_result → final_answer
- **7 Built-in Tools** — Web Search (Bing), Web Scrape, File I/O, Terminal Exec, PDF Generation, Resource Download, Task Termination
- **Multi-Provider Support** — DashScope / OpenAI / Anthropic / OpenAI-Compatible, switchable via frontend Settings
- **RAG Knowledge Base** — Retrieval-Augmented Generation with query rewriting, keyword enrichment, and vector search
- **Session Management** — Cross-request conversation continuity with auto-expiring session pool
- **MCP Protocol** — Integrates with external MCP servers (Amap Maps, Image Search) via stdio/SSE

---

## 🏗 Architecture

```
┌──────────────────────────────────────────────────────────────┐
│                    Vue 3 Frontend (ai-agent-frontend)        │
│  Home.vue → AgentChat.vue / KnowledgeRag.vue / Settings.vue │
│  ChatRoom.vue ←── SSE ──→ Backend API                       │
│  ThinkingChain.vue  ← Agent reasoning visualization          │
└──────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌──────────────────────────────────────────────────────────────┐
│                  Spring Boot Backend                          │
│                                                              │
│  AiController                  ModelConfigController         │
│  ├── /ai/rag/chat/*         ← RAG Knowledge Base            │
│  ├── /ai/agent/chat/stream  ← Agent (JSON SSE + 思考链)      │
│  └── /ai/config             ← Multi-Provider configuration   │
│                                                              │
│  Agent Hierarchy:                                            │
│  BaseAgent → ReActAgent → ToolCallAgent → DevAssistantAgent │
│                                                              │
│  Infrastructure:                                             │
│  ├── DynamicChatModelFactory ← Provider dispatch             │
│  ├── AgentSessionManager     ← Session pool + auto-cleanup   │
│  ├── VectorStore             ← RAG document retrieval        │
│  └── MCP Client              ← Cross-process tool integration│
└──────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌──────────────────────────────────────────────────────────────┐
│  LLM Providers: DashScope │ OpenAI │ Anthropic │ Compatible  │
│  Search: Bing (free)      │ Vector Store: In-Memory / PGVector│
└──────────────────────────────────────────────────────────────┘
```

### Agent State Machine

```
IDLE ──→ RUNNING ──→ FINISHED
              │
              └──→ ERROR
```

### Thinking Chain Event Flow

```
step_start → thinking → tool_call × N → tool_result × N → step_end
                                                              ↓
                                                       final_answer
                                                              ↓
                                                       agent_finish
```

Each step: **Think** (LLM decides which tools to call) → **Act** (execute tools, record results) → **Repeat** until task is complete or max steps reached.

---

## 🚀 Quick Start

### Prerequisites

- JDK 21+
- Maven 3.9+
- Node.js 18+ (for frontend)

### Environment Setup

```bash
# Copy and configure environment variables
cp .env.example .env
# Edit .env with your API key:
#   DASHSCOPE_API_KEY=sk-xxx    (required, default provider)
```

### Backend

```bash
# Set API key and start
set DASHSCOPE_API_KEY=sk-your-key-here    # Windows
# export DASHSCOPE_API_KEY=sk-your-key-here  # Linux/Mac

mvn spring-boot:run
```

The server starts at **http://localhost:8123/api**

### Frontend

```bash
cd ai-agent-frontend
npm install
npm run dev
```

Frontend dev server at **http://localhost:3000**

### Multi-Provider Configuration

Open **http://localhost:3000/settings** to configure your LLM provider:

| Provider | Required | Model Examples |
|----------|----------|---------------|
| DashScope | API Key | qwen-max, qwen-plus, qwen-turbo |
| OpenAI | API Key | gpt-4o, gpt-4o-mini, o3 |
| Anthropic | API Key | claude-sonnet-4-6, claude-opus-4-8 |
| OpenAI-Compatible | API Key + Base URL | deepseek-chat, moonshot-v1 |

Configuration is stored per-session in `localStorage` and sent to the backend on first chat.

---

## 🔧 Available Tools

| Tool | Function | Description |
|------|----------|-------------|
| `web_search` | `searchWeb(query)` | Search the web via Bing (free, no API key needed) |
| `web_scrape` | `scrapeWebPage(url)` | Extract text content from a URL |
| `file_read` | `readFile(name)` | Read file contents from local disk |
| `file_write` | `writeFile(name, content)` | Write content to a local file |
| `execute_terminal_command` | `executeTerminalCommand(cmd)` | Execute shell commands (cross-platform) |
| `generate_pdf` | `generatePDF(name, content)` | Generate PDF documents with Chinese font support |
| `download_resource` | `downloadResource(url, name)` | Download files from remote URLs |
| `terminate` | `doTerminate()` | End the agent session when task is complete |

---

## 📡 API Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/ai/rag/chat/sync` | GET | RAG knowledge base Q&A (sync) |
| `/api/ai/rag/chat/sse` | GET | RAG knowledge base Q&A (SSE stream) |
| `/api/ai/agent/chat/stream` | GET | Agent chat with thinking chain visualization (JSON SSE) |
| `/api/ai/agent/sessions/count` | GET | Active session count for monitoring |
| `/api/ai/config` | POST | Save multi-provider configuration |
| `/api/ai/config` | GET | Get current configuration |

---

## 📁 Project Structure

```
├── src/main/java/com/aiagent/
│   ├── agent/                        # Agent core
│   │   ├── BaseAgent.java            # State machine + step loop + SSE
│   │   ├── ReActAgent.java           # Think → Act template method
│   │   ├── ToolCallAgent.java        # LLM Function Calling integration
│   │   ├── DevAssistantAgent.java    # Concrete agent (role + tools + prompt)
│   │   ├── model/AgentState.java     # IDLE → RUNNING → FINISHED/ERROR
│   │   └── event/AgentEvent.java     # Structured SSE event record
│   ├── session/                      # Session lifecycle
│   │   ├── AgentSession.java         # Agent + ModelConfig wrapper
│   │   └── AgentSessionManager.java  # Session pool with TTL cleanup
│   ├── config/                       # Multi-provider support
│   │   ├── ModelConfig.java          # Provider + API key + model record
│   │   ├── ModelConfigController.java # REST API for config
│   │   ├── DynamicChatModelFactory.java # Provider dispatch factory
│   │   └── ProviderResult.java       # ChatClient + ChatOptions tuple
│   ├── tools/                        # Tool implementations
│   │   ├── ToolRegistration.java     # Spring bean registration
│   │   ├── WebSearchTool.java        # Bing search (free, no API key)
│   │   ├── WebScrapingTool.java      # Jsoup-based web scraper
│   │   ├── FileOperationTool.java    # Local file read/write
│   │   ├── TerminalOperationTool.java # Cross-platform command execution
│   │   ├── PDFGenerationTool.java    # iText PDF generation
│   │   ├── ResourceDownloadTool.java # HTTP file download
│   │   └── TerminateTool.java        # Task completion signal
│   ├── rag/                          # RAG pipeline
│   │   ├── VectorStoreConfig.java    # In-memory vector store init
│   │   ├── KnowledgeDocumentLoader.java # Markdown document loader
│   │   ├── DocumentSplitter.java     # Token-based text splitting
│   │   ├── KeywordEnricher.java      # LLM keyword extraction
│   │   ├── QueryRewriter.java        # LLM query rewriting
│   │   └── PgVectorVectorStoreConfig.java # PostgreSQL vector store (optional)
│   ├── advisor/                      # Chat advisors (interceptors)
│   ├── chatmemory/                   # Conversation memory impl
│   ├── app/KnowledgeBaseService.java # RAG chat service
│   └── controller/AiController.java  # REST API endpoints
├── src/main/resources/
│   ├── application.yml               # Spring AI + vector store config
│   └── document/                     # Knowledge base markdown files
│       ├── AI-Agent-基础概念与架构.md
│       ├── ReAct-模式与工具调用.md
│       └── RAG-检索增强生成技术.md
├── ai-agent-frontend/                # Vue 3 frontend
│   └── src/
│       ├── views/
│       │   ├── Home.vue              # Landing page
│       │   ├── AgentChat.vue         # Agent chat + thinking chain
│       │   ├── KnowledgeRag.vue      # RAG knowledge base UI
│       │   └── Settings.vue          # Multi-provider configuration
│       ├── components/
│       │   ├── ChatRoom.vue          # Chat interface
│       │   ├── ThinkingChain.vue     # Agent reasoning visualization
│       │   ├── AiAvatarFallback.vue  # AI avatar component
│       │   └── AppFooter.vue         # Page footer
│       ├── api/index.js              # API + SSE helpers
│       └── router/index.js           # Vue Router config
└── image-search-mcp-server/          # MCP server for image search
```

---

## 🎯 Key Design Decisions

1. **Manual ReAct loop over auto-execution** — The agent explicitly controls the Think→Act cycle, providing full visibility into reasoning. Each step emits structured JSON events for frontend visualization.

2. **final_answer vs step_end separation** — The LLM's natural language final summary is emitted as a dedicated `final_answer` event, distinct from `step_end` (which carries tool execution results). This ensures the chat panel shows the agent's actual conclusion, not raw tool output.

3. **Multi-provider with DynamicChatModelFactory** — Rather than Spring Boot auto-configuration, each provider's `ChatModel` is created programmatically via a factory, enabling runtime switching based on user-provided API keys.

4. **Graceful degradation** — Embedding API failures (free tier exhaustion) are caught at startup; the vector store initializes empty rather than crashing the application. Chat functionality remains fully operational.

5. **Free search via Bing** — Web search uses Bing's HTML results page rather than a paid third-party API, making the project fully functional after `git clone` without additional service registration.

---

## 📄 License

MIT
