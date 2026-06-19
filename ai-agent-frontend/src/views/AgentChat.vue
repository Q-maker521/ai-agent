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
      </div>
      <button class="toggle-btn" @click="showChain = !showChain">
        {{ showChain ? '隐藏' : '显示' }}思考链
      </button>
    </header>

    <!-- 主体 -->
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
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { useHead } from '@vueuse/head'
import ChatRoom from '../components/ChatRoom.vue'
import ThinkingChain from '../components/ThinkingChain.vue'

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
let eventSource = null

const addMessage = (content, isUser, type = '') => {
  messages.value.push({ content, isUser, type, time: Date.now() })
}

const sendMessage = (message) => {
  addMessage(message, true, 'user-question')
  if (eventSource) eventSource.close()
  connectionStatus.value = 'connecting'
  agentEvents.value = []

  const API_BASE = import.meta.env.DEV ? 'http://localhost:8123/api' : '/api'
  const url = `${API_BASE}/ai/agent/chat/stream?message=${encodeURIComponent(message)}&sessionId=${sessionId.value}`
  eventSource = new EventSource(url)

  let finalResponse = ''
  let finalAnswerText = ''
  eventSource.onmessage = (event) => {
    try {
      const evt = JSON.parse(event.data)
      agentEvents.value.push(evt)
      if (evt.type === 'step_end' && evt.content) finalResponse += evt.content + '\n'
      if (evt.type === 'final_answer' && evt.content) finalAnswerText = evt.content
      if (evt.type === 'agent_finish') {
        connectionStatus.value = 'disconnected'
        const reply = finalAnswerText || finalResponse.trim() || '任务完成。'
        addMessage(reply, false, 'ai-final')
        eventSource.close()
      }
      if (evt.type === 'agent_error') {
        connectionStatus.value = 'error'
        addMessage('执行出错：' + evt.content, false, 'ai-error')
        eventSource.close()
      }
    } catch { /* ignore non-JSON */ }
  }
  eventSource.onerror = () => {
    if (connectionStatus.value === 'connecting') {
      connectionStatus.value = 'error'
      const reply = finalAnswerText || (finalResponse ? finalResponse.trim() : '')
      if (reply) addMessage(reply, false, 'ai-final')
    }
    eventSource.close()
  }
}

const goBack = () => router.push('/')

onMounted(() => {
  // 优先使用已保存的 sessionId（用户在设置中配置了 API Key）
  const savedSessionId = localStorage.getItem('aiagent_session_id')
  sessionId.value = savedSessionId || 'agent_' + Math.random().toString(36).substring(2, 10)
  addMessage('你好，我是 AI 智能助手。我可以搜索网页、读写文件、执行终端命令、爬取内容、生成 PDF 报告。请告诉我你需要完成什么任务？', false)
})

onBeforeUnmount(() => { if (eventSource) eventSource.close() })
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
