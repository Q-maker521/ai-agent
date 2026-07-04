<template>
  <div class="rag-view">
    <header class="header">
      <button class="back-btn" @click="goBack">
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M19 12H5M12 19l-7-7 7-7"/></svg>
        返回
      </button>
      <div class="header-center">
        <h1>RAG 知识库问答</h1>
        <span class="badge">{{ chatId }}</span>
      </div>
      <div style="width:60px"></div>
    </header>
    <div class="content">
      <ChatRoom
        :messages="messages"
        :connection-status="connectionStatus"
        ai-type="rag"
        @send-message="sendMessage"
      />
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { useHead } from '@vueuse/head'
import ChatRoom from '../components/ChatRoom.vue'
import { chatWithRag, getRagDocuments } from '../api'

useHead({
  title: 'RAG 知识库问答 — 检索增强生成',
  meta: [
    { name: 'description', content: '基于 RAG 检索增强生成技术的知识库问答系统，支持文档向量化与智能问答。' },
    { name: 'keywords', content: 'RAG, 知识库, 检索增强, 向量搜索' }
  ]
})

const router = useRouter()
const messages = ref([])
const chatId = ref('')
const connectionStatus = ref('disconnected')
const documents = ref([])
let eventSource = null

const addMessage = (content, isUser) => messages.value.push({ content, isUser, time: Date.now() })

const sendMessage = (message) => {
  addMessage(message, true)
  if (eventSource) eventSource.close()
  const idx = messages.value.length
  addMessage('', false)
  connectionStatus.value = 'connecting'
  const sessionId = localStorage.getItem('aiagent_session_id') || ''
  eventSource = chatWithRag(message, chatId.value, sessionId)
  eventSource.onmessage = (event) => {
    const data = event.data
    if (data && data !== '[DONE]' && idx < messages.value.length) messages.value[idx].content += data
    if (data === '[DONE]') { connectionStatus.value = 'disconnected'; eventSource.close() }
  }
  eventSource.onerror = () => { connectionStatus.value = 'error'; eventSource.close() }
}

const goBack = () => router.push('/')

onMounted(async () => {
  chatId.value = 'rag_' + Math.random().toString(36).substring(2, 8)
  // 加载文档目录
  try {
    documents.value = await getRagDocuments()
  } catch (e) {
    console.warn('Failed to load document catalog:', e)
  }
  let welcome = '你好，我是 AI 知识库助手。请提出你的问题，我将基于知识库文档为你提供答案。'
  if (documents.value.length > 0) {
    welcome += '\n\n📚 当前知识库包含以下文档：'
    for (const doc of documents.value) {
      const name = (doc.filename || '').replace('.md', '')
      welcome += '\n  • ' + name
      if (doc.summary) welcome += ' — ' + doc.summary
    }
  }
  addMessage(welcome, false)
})

onBeforeUnmount(() => { if (eventSource) eventSource.close() })
</script>

<style scoped>
.rag-view { display: flex; flex-direction: column; height: 100vh; background: var(--bg-secondary); }
.header {
  display: flex; align-items: center; justify-content: space-between;
  padding: 10px 20px; background: var(--bg-primary);
  border-bottom: 1px solid var(--border-subtle);
  flex-shrink: 0; box-shadow: var(--shadow-sm);
}
.header-center { display: flex; align-items: center; gap: 10px; }
.header-center h1 { font-size: 1rem; font-weight: 700; color: var(--text-primary); }
.badge {
  font-size: 0.68rem; font-family: var(--font-mono);
  color: var(--accent); background: var(--accent-light);
  padding: 2px 8px; border-radius: 8px;
}
.back-btn {
  display: flex; align-items: center; gap: 4px;
  padding: 6px 14px; border-radius: var(--radius-sm);
  font-size: 0.82rem; color: var(--text-secondary);
  background: var(--bg-primary); border: 1px solid var(--border-subtle);
  cursor: pointer; transition: all 0.2s;
}
.back-btn:hover { color: var(--accent); border-color: var(--accent-light); }
.content { flex: 1; overflow: hidden; }
</style>
