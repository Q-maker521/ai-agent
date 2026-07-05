<template>
  <div class="agent-chat">
    <!-- 顶栏 -->
    <header class="header">
      <button class="back-btn" @click="goBack">
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M19 12H5M12 19l-7-7 7-7"/></svg>
        返回
      </button>
      <div class="header-center">
        <h1>Agent 智能助手</h1>
        <span class="badge">ReAct 模式</span>
        <span v-if="sessionId" class="session-badge" :title="'会话: ' + sessionId">
          💬 {{ sessionId.substring(0, 8) }}
        </span>
      </div>
      <div class="header-actions">
        <button class="toggle-btn" @click="showChain = !showChain">
          {{ showChain ? '隐藏' : '显示' }}思考链
        </button>
      </div>
    </header>

    <!-- 主体：侧边栏 + 聊天 -->
    <div class="content">
      <!-- 会话列表侧边栏 -->
      <transition name="slide-sidebar">
        <aside v-if="showSidebar" class="sidebar">
          <div class="sidebar-header">
            <button class="new-chat-btn" @click="newConversation">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 5v14M5 12h14"/></svg>
              新对话
            </button>
          </div>
          <div class="session-list">
            <div
              v-for="s in sessionList"
              :key="s.sessionId"
              class="session-item"
              :class="{ active: s.sessionId === sessionId }"
              @click="switchSession(s.sessionId)"
            >
              <div class="session-info">
                <span class="session-title">{{ s.title }}</span>
                <span class="session-meta">
                  {{ s.messageCount }} 条消息
                  <span v-if="s.isActive" class="active-dot" title="活跃中">●</span>
                  <span v-else class="inactive-dot" title="已保存">○</span>
                </span>
              </div>
              <button
                class="delete-session-btn"
                @click.stop="deleteCurrentSession(s.sessionId)"
                title="删除会话"
              >×</button>
            </div>
            <div v-if="sessionList.length === 0" class="empty-list">
              暂无历史会话
            </div>
          </div>
          <div class="sidebar-footer">
            <button class="toggle-sidebar-btn" @click="showSidebar = false" title="收起侧边栏">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M15 18l-6-6 6-6"/></svg>
            </button>
          </div>
        </aside>
      </transition>

      <!-- 侧边栏展开按钮（收起后显示） -->
      <button v-if="!showSidebar" class="show-sidebar-btn" @click="showSidebar = true" title="展开侧边栏">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M9 18l6-6-6-6"/></svg>
      </button>

      <div class="main">
        <div class="chat-panel" :class="{ full: !showChain }">
          <ChatRoom
            :messages="messages"
            :connection-status="connectionStatus"
            ai-type="agent"
            @send-message="sendMessage"
          />
        </div>

        <transition name="slide">
          <div v-if="showChain" class="chain-panel">
            <ThinkingChain
              :events="agentEvents"
              :is-running="connectionStatus === 'connecting'"
            />
          </div>
        </transition>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { useHead } from '@vueuse/head'
import ChatRoom from '../components/ChatRoom.vue'
import ThinkingChain from '../components/ThinkingChain.vue'
import { getSessionContext, listSessions, deleteSession as deleteSessionApi } from '../api/index.js'

useHead({
  title: 'Agent 智能助手 — ReAct 自主规划',
  meta: [
    { name: 'description', content: '基于 ReAct 模式的自主规划 AI Agent，支持多工具协作与思考链实时可视化。' },
    { name: 'keywords', content: 'AI Agent, ReAct, 工具调用, 自主规划, 思考链' }
  ]
})

const router = useRouter()
const messages = ref([])
const agentEvents = ref([])
const connectionStatus = ref('disconnected')
const showChain = ref(true)
const sessionId = ref('')
const historyLoaded = ref(false)
const sessionList = ref([])  // 会话列表
const showSidebar = ref(true)
let eventSource = null
let streamingMsgIndex = -1  // 当前流式消息在 messages 中的索引（模块级，避免 TDZ）

const addMessage = (content, isUser, type = '') => {
  messages.value.push({ content, isUser, type, time: Date.now() })
}

// 从后端加载会话历史消息（服务重启后仍可恢复）
const loadSessionHistory = async () => {
  if (!sessionId.value) return
  try {
    const ctx = await getSessionContext(sessionId.value)
    if (ctx.messageCount > 0 && ctx.messages.length > 0) {
      // 将后端消息格式转为前端聊天消息
      for (const msg of ctx.messages) {
        const typeName = msg.type
        const text = (msg.text || '').trim()
        if (!text) continue  // 跳过空消息（如纯 tool_call 的 AssistantMessage）
        if (typeName === 'USER') {
          // 过滤掉内部 nextStepPrompt，特征是包含 "determine the next action"
          if (text.includes('determine the next action') || text.includes('Based on the current task progress')) continue
          addMessage(text, true, 'user-question')
        } else if (typeName === 'ASSISTANT') {
          addMessage(text, false, 'ai-final')
        }
        // TOOL 和 SYSTEM 类型不显示在聊天面板
      }
    }
    historyLoaded.value = true
  } catch (e) {
    console.warn('Failed to load session history:', e)
  }
}

// 开始新对话
const newConversation = () => {
  if (eventSource) eventSource.close()
  messages.value = []
  agentEvents.value = []
  connectionStatus.value = 'disconnected'
  sessionId.value = 'agent_' + Math.random().toString(36).substring(2, 10)
  historyLoaded.value = false
  localStorage.removeItem('aiagent_session_id')
  addMessage('你好，我是 AI 智能助手。我可以搜索网页、读写文件、执行终端命令、爬取内容、生成 PDF 报告。请告诉我你需要完成什么任务？', false)
  loadSessionList()
}

const sendMessage = (message) => {
  addMessage(message, true, 'user-question')
  // 兜底：确保 sessionId 已保存到 localStorage
  localStorage.setItem('aiagent_session_id', sessionId.value)
  if (eventSource) eventSource.close()
  connectionStatus.value = 'connecting'
  agentEvents.value = []
  streamingMsgIndex = -1

  const API_BASE_URL = API_BASE
  const url = `${API_BASE_URL}/ai/agent/chat/stream?message=${encodeURIComponent(message)}&sessionId=${sessionId.value}`
  eventSource = new EventSource(url)

  let finalResponse = ''
  let finalAnswerText = ''
  eventSource.onmessage = (event) => {
    try {
      const evt = JSON.parse(event.data)
      agentEvents.value.push(evt)
      // 新步骤开始 → 重置当前步骤的流式内容
      if (evt.type === 'step_start') {
        finalAnswerText = ''
        streamingMsgIndex = -1
      }

      // token 级流式：实时更新聊天面板中的 AI 消息
      if (evt.type === 'thinking_delta' && (evt.delta || evt.content)) {
        const token = evt.delta || evt.content
        finalAnswerText += token
        if (streamingMsgIndex < 0) {
          // 第一条 token → 创建新的流式消息
          messages.value.push({
            content: finalAnswerText,
            isUser: false,
            type: 'ai-streaming',
            time: Date.now()
          })
          streamingMsgIndex = messages.value.length - 1
        } else {
          // 后续 token → 更新现有流式消息
          const msg = messages.value[streamingMsgIndex]
          if (msg) msg.content = finalAnswerText
        }
      }

      // 注意：thinking 事件是进度通知（"正在调用 LLM..."），
      // 不包含实际回复内容，因此不参与 finalAnswerText 的构建

      if (evt.type === 'final_answer' && evt.content) finalAnswerText = evt.content
      if (evt.type === 'agent_finish') {
        connectionStatus.value = 'disconnected'
        const reply = finalAnswerText || finalResponse.trim() || '任务完成。'
        // 如果有流式消息，将其类型切换为 final
        if (streamingMsgIndex >= 0) {
          const msg = messages.value[streamingMsgIndex]
          if (msg) {
            msg.content = reply
            msg.type = 'ai-final'
          }
        } else {
          addMessage(reply, false, 'ai-final')
        }
        streamingMsgIndex = -1
        eventSource.close()
      }
      if (evt.type === 'agent_error') {
        connectionStatus.value = 'error'
        addMessage('执行出错：' + evt.content, false, 'ai-error')
        streamingMsgIndex = -1
        eventSource.close()
      }
    } catch { /* ignore non-JSON */ }
  }
  eventSource.onerror = () => {
    if (connectionStatus.value === 'connecting') {
      connectionStatus.value = 'error'
      const reply = finalAnswerText || (finalResponse ? finalResponse.trim() : '')
      if (reply) {
        if (streamingMsgIndex >= 0) {
          const msg = messages.value[streamingMsgIndex]
          if (msg) { msg.content = reply; msg.type = 'ai-final' }
        } else {
          addMessage(reply, false, 'ai-final')
        }
      }
    }
    streamingMsgIndex = -1
    eventSource.close()
  }
}

// Esc 键停止 Agent 执行
const API_BASE = import.meta.env.DEV ? 'http://localhost:8123/api' : '/api'

const stopAgent = async () => {
  if (connectionStatus.value !== 'connecting') return
  if (eventSource) {
    eventSource.close()
    eventSource = null
  }
  connectionStatus.value = 'disconnected'
  try {
    await fetch(`${API_BASE}/ai/agent/cancel?sessionId=${sessionId.value}`, { method: 'POST' })
  } catch { /* ignore network errors */ }
  addMessage('⏹ 已停止 (Esc)', false, 'ai-system')
}

const handleKeyDown = (e) => {
  if (e.key === 'Escape' && connectionStatus.value === 'connecting') {
    e.preventDefault()
    stopAgent()
  }
}

const goBack = () => router.push('/')

// 加载会话列表
const loadSessionList = async () => {
  try {
    sessionList.value = await listSessions()
  } catch (e) {
    console.warn('Failed to load session list:', e)
  }
}

// 切换到指定会话
const switchSession = async (sid) => {
  if (sid === sessionId.value) return
  if (eventSource) eventSource.close()
  messages.value = []
  agentEvents.value = []
  connectionStatus.value = 'disconnected'
  sessionId.value = sid
  historyLoaded.value = false
  localStorage.setItem('aiagent_session_id', sid)
  await loadSessionHistory()
  await loadSessionList()
  if (messages.value.length === 0) {
    addMessage('你好，我是 AI 智能助手。我可以搜索网页、读写文件、执行终端命令、爬取内容、生成 PDF 报告。请告诉我你需要完成什么任务？', false)
  }
}

// 删除指定会话
const deleteCurrentSession = async (sid) => {
  if (!confirm('确定删除这个会话？此操作不可恢复。')) return
  try {
    await deleteSessionApi(sid)
  } catch (e) {
    console.warn('Failed to delete session:', e)
  }
  if (sid === sessionId.value) {
    // 删除的是当前会话，切到新会话
    newConversation()
  }
  await loadSessionList()
}

onMounted(async () => {
  // 注册 Esc 键监听
  window.addEventListener('keydown', handleKeyDown)

  // 优先使用已保存的 sessionId（用户在设置中配置了 API Key）
  const savedSessionId = localStorage.getItem('aiagent_session_id')
  sessionId.value = savedSessionId || 'agent_' + Math.random().toString(36).substring(2, 10)
  // 立即保存 sessionId，防止页面刷新后丢失上下文
  if (!savedSessionId) {
    localStorage.setItem('aiagent_session_id', sessionId.value)
  }

  // 尝试从后端恢复该会话的历史消息
  await loadSessionHistory()
  await loadSessionList()

  // 如果没有历史消息，显示欢迎语
  if (messages.value.length === 0) {
    addMessage('你好，我是 AI 智能助手。我可以搜索网页、读写文件、执行终端命令、爬取内容、生成 PDF 报告。请告诉我你需要完成什么任务？', false)
  }
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', handleKeyDown)
  if (eventSource) eventSource.close()
})
</script>

<style scoped>
.agent-chat {
  display: flex; flex-direction: column;
  height: 100vh; background: var(--bg-secondary);
}

.header {
  display: flex; align-items: center; justify-content: space-between;
  padding: 10px 20px;
  background: var(--bg-primary);
  border-bottom: 1px solid var(--border-subtle);
  flex-shrink: 0; z-index: 10;
  box-shadow: var(--shadow-sm);
}
.header-center { display: flex; align-items: center; gap: 10px; }
.header-center h1 {
  font-size: 1rem; font-weight: 700;
  color: var(--text-primary);
}
.badge {
  font-size: 0.7rem; color: var(--accent);
  background: var(--accent-light);
  padding: 2px 10px; border-radius: 10px;
  font-weight: 500;
}
.back-btn, .toggle-btn {
  display: flex; align-items: center; gap: 4px;
  padding: 6px 14px; border-radius: var(--radius-sm);
  font-size: 0.82rem; color: var(--text-secondary);
  background: var(--bg-primary); border: 1px solid var(--border-subtle);
  cursor: pointer; transition: all 0.2s;
}
.back-btn:hover, .toggle-btn:hover {
  color: var(--accent); border-color: var(--accent-light);
}
.header-actions {
  display: flex; align-items: center; gap: 8px;
}
.session-badge {
  font-size: 0.68rem; color: var(--text-tertiary);
  background: var(--bg-secondary);
  padding: 2px 8px; border-radius: 8px;
  font-family: monospace;
  display: flex; align-items: center; gap: 4px;
}

/* ===== 内容区 ===== */
.content {
  display: flex; flex: 1; overflow: hidden; position: relative;
}

/* ===== 侧边栏 ===== */
.sidebar {
  width: 260px; flex-shrink: 0;
  display: flex; flex-direction: column;
  background: var(--bg-primary);
  border-right: 1px solid var(--border-subtle);
  overflow: hidden;
}
.sidebar-header {
  padding: 12px; border-bottom: 1px solid var(--border-subtle);
}
.sidebar-header .new-chat-btn {
  width: 100%; justify-content: center;
  padding: 8px 14px; border-radius: var(--radius-sm);
  font-size: 0.85rem; color: var(--accent);
  background: var(--accent-light);
  border: 1px solid var(--accent-light);
  cursor: pointer; transition: all 0.2s;
  display: flex; align-items: center; gap: 6px;
  font-weight: 500;
}
.sidebar-header .new-chat-btn:hover {
  background: var(--accent);
  color: #fff;
}

/* 会话列表 */
.session-list {
  flex: 1; overflow-y: auto; padding: 6px 0;
}
.session-item {
  display: flex; align-items: center;
  padding: 10px 12px; cursor: pointer;
  transition: background 0.15s;
  border-left: 3px solid transparent;
}
.session-item:hover { background: var(--bg-secondary); }
.session-item.active {
  background: var(--accent-light);
  border-left-color: var(--accent);
}
.session-info { flex: 1; min-width: 0; }
.session-title {
  display: block; font-size: 0.85rem; color: var(--text-primary);
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
  font-weight: 500;
}
.session-meta {
  display: block; font-size: 0.7rem; color: var(--text-tertiary);
  margin-top: 2px;
}
.active-dot { color: #22c55e; margin-left: 4px; }
.inactive-dot { color: var(--text-tertiary); margin-left: 4px; }

.delete-session-btn {
  width: 24px; height: 24px; border-radius: 4px;
  border: none; background: transparent;
  color: var(--text-tertiary); cursor: pointer;
  font-size: 1.1rem; line-height: 1;
  display: flex; align-items: center; justify-content: center;
  opacity: 0; transition: all 0.15s;
}
.session-item:hover .delete-session-btn { opacity: 1; }
.delete-session-btn:hover {
  background: #fecaca; color: #dc2626;
}

.empty-list {
  padding: 24px; text-align: center;
  color: var(--text-tertiary); font-size: 0.82rem;
}

.sidebar-footer {
  padding: 8px; border-top: 1px solid var(--border-subtle);
}
.toggle-sidebar-btn {
  width: 100%; padding: 4px; border-radius: 4px;
  border: none; background: transparent;
  color: var(--text-tertiary); cursor: pointer;
  display: flex; justify-content: center;
  transition: all 0.15s;
}
.toggle-sidebar-btn:hover { background: var(--bg-secondary); color: var(--text-secondary); }

/* 展开侧边栏按钮 */
.show-sidebar-btn {
  position: absolute; left: 0; top: 12px; z-index: 5;
  padding: 8px 4px; border-radius: 0 6px 6px 0;
  border: 1px solid var(--border-subtle); border-left: none;
  background: var(--bg-primary); color: var(--text-secondary);
  cursor: pointer; transition: all 0.15s;
}
.show-sidebar-btn:hover { color: var(--accent); }

/* 侧边栏动画 */
.slide-sidebar-enter-active, .slide-sidebar-leave-active {
  transition: width 0.25s, opacity 0.25s; overflow: hidden;
}
.slide-sidebar-enter-from, .slide-sidebar-leave-to { width: 0; opacity: 0; }

.main { display: flex; flex: 1; overflow: hidden; }
.chat-panel { flex: 1; min-width: 0; transition: flex 0.3s; }
.chat-panel.full { flex: 1; }
.chain-panel {
  width: 400px; flex-shrink: 0;
  border-left: 1px solid var(--border-subtle);
  overflow: hidden; background: var(--bg-primary);
}

.slide-enter-active, .slide-leave-active { transition: width 0.3s, opacity 0.3s; overflow: hidden; }
.slide-enter-from, .slide-leave-to { width: 0; opacity: 0; }

@media (max-width: 1024px) { .chain-panel { width: 340px; } }
@media (max-width: 768px) { .chain-panel { width: 300px; } }
</style>
