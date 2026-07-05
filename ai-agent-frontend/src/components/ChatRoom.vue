<template>
  <div class="chat-room">
    <!-- 消息列表 -->
    <div class="messages" ref="messagesContainer">
      <div v-for="(msg, index) in messages" :key="index" class="msg-wrapper">
        <!-- AI 消息 -->
        <div v-if="!msg.isUser" class="msg ai-msg" :class="msg.type">
          <div class="msg-avatar">
            <AiAvatarFallback :type="aiType" />
          </div>
          <div class="msg-bubble">
            <div v-if="msg.type === 'ai-final'" class="msg-text markdown-body" v-html="renderMarkdown(msg.content)"></div>
            <div v-else class="msg-text">{{ msg.content }}</div>
            <div class="msg-time">
              {{ formatTime(msg.time) }}
              <span v-if="connectionStatus === 'connecting' && index === messages.length - 1" class="typing-dot">●</span>
            </div>
            <div v-if="msg.type === 'ai-final'" class="msg-actions">
              <button class="action-btn" @click="copyMessage(msg.content)" title="复制">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="9" y="9" width="13" height="13" rx="2" ry="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/></svg>
              </button>
              <button class="action-btn" @click="regenerate(msg, index)" title="重新生成">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="1 4 1 10 7 10"/><path d="M3.51 15a9 9 0 1 0 2.13-9.36L1 10"/></svg>
              </button>
            </div>
          </div>
        </div>

        <!-- 用户消息 -->
        <div v-else class="msg user-msg" :class="msg.type">
          <div class="msg-bubble">
            <div class="msg-text">{{ msg.content }}</div>
            <div class="msg-time">{{ formatTime(msg.time) }}</div>
          </div>
          <div class="msg-avatar user-avatar">我</div>
        </div>
      </div>
    </div>

    <!-- 输入区 -->
    <div class="input-area">
      <div class="input-row">
        <textarea
          v-model="inputMessage"
          @keydown.enter.exact.prevent="send"
          placeholder="输入任务描述..."
          class="input-field"
          :disabled="connectionStatus === 'connecting'"
          rows="1"
        ></textarea>
        <button
          @click="send"
          class="send-btn"
          :disabled="connectionStatus === 'connecting' || !inputMessage.trim()"
        >
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <line x1="22" y1="2" x2="11" y2="13"/><polygon points="22 2 15 22 11 13 2 9 22 2"/>
          </svg>
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, watch, nextTick, onMounted, inject } from 'vue'
import AiAvatarFallback from './AiAvatarFallback.vue'
import { marked } from 'marked'
import { markedHighlight } from 'marked-highlight'
import hljs from 'highlight.js/lib/core'
import javascript from 'highlight.js/lib/languages/javascript'
import json from 'highlight.js/lib/languages/json'
import bash from 'highlight.js/lib/languages/bash'
import python from 'highlight.js/lib/languages/python'
import java from 'highlight.js/lib/languages/java'
import xml from 'highlight.js/lib/languages/xml'
import 'highlight.js/styles/github.css'

hljs.registerLanguage('javascript', javascript)
hljs.registerLanguage('json', json)
hljs.registerLanguage('bash', bash)
hljs.registerLanguage('python', python)
hljs.registerLanguage('java', java)
hljs.registerLanguage('xml', xml)
hljs.registerLanguage('html', xml)

marked.use(markedHighlight({
  langPrefix: 'hljs language-',
  highlight(code, lang) {
    if (lang && hljs.getLanguage(lang)) {
      return hljs.highlight(code, { language: lang }).value
    }
    return hljs.highlightAuto(code).value
  }
}))
marked.use({ breaks: true, gfm: true })

const props = defineProps({
  messages: { type: Array, default: () => [] },
  connectionStatus: { type: String, default: 'disconnected' },
  aiType: { type: String, default: 'agent' }
})

const emit = defineEmits(['send-message', 'regenerate'])
const toast = inject('toast', () => {})
const inputMessage = ref('')
const messagesContainer = ref(null)

const send = () => {
  if (!inputMessage.value.trim() || props.connectionStatus === 'connecting') return
  emit('send-message', inputMessage.value)
  inputMessage.value = ''
}

function formatTime(ts) {
  const d = new Date(ts)
  const now = new Date()
  const time = d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  if (d.toDateString() === now.toDateString()) return time
  const yesterday = new Date(now)
  yesterday.setDate(yesterday.getDate() - 1)
  if (d.toDateString() === yesterday.toDateString()) return '昨天 ' + time
  return `${String(d.getMonth()+1).padStart(2,'0')}/${String(d.getDate()).padStart(2,'0')} ${time}`
}

function renderMarkdown(text) {
  if (!text) return ''
  return marked(text)
}

async function copyMessage(text) {
  try {
    await navigator.clipboard.writeText(text)
    toast('已复制到剪贴板', 'success')
  } catch {
    toast('复制失败', 'error')
  }
}

function regenerate(msg, msgIndex) {
  const msgs = props.messages
  for (let i = msgIndex - 1; i >= 0; i--) {
    if (msgs[i].isUser) {
      emit('regenerate', msgs[i].content)
      return
    }
  }
}

function scroll() {
  nextTick(() => {
    const el = messagesContainer.value
    if (!el) return
    const atBottom = el.scrollHeight - el.scrollTop - el.clientHeight < 100
    if (atBottom) {
      el.scrollTo({ top: el.scrollHeight, behavior: 'smooth' })
    }
  })
}

watch(() => props.messages.length, scroll)
watch(() => props.messages.map(m => m.content).join(''), scroll)
onMounted(scroll)
</script>

<style scoped>
.chat-room { display: flex; flex-direction: column; height: 100%; background: var(--bg-secondary); }

.messages { flex: 1; overflow-y: auto; padding: 20px 24px; }
.msg-wrapper { margin-bottom: 18px; }
.msg { display: flex; align-items: flex-start; gap: 10px; max-width: 85%; }
.user-msg { margin-left: auto; flex-direction: row-reverse; }
.ai-msg { margin-right: auto; }

.msg-avatar { width: 34px; height: 34px; border-radius: var(--radius-sm); overflow: hidden; flex-shrink: 0; }
.user-avatar {
  display: flex; align-items: center; justify-content: center;
  background: var(--accent-light); color: var(--accent);
  font-size: 0.78rem; font-weight: 700; border-radius: var(--radius-sm);
}

.msg-bubble { padding: 10px 15px; border-radius: var(--radius-md); min-width: 60px; }
.ai-msg .msg-bubble {
  background: var(--bg-primary); border: 1px solid var(--border-subtle);
  border-bottom-left-radius: var(--radius-sm);
  box-shadow: var(--shadow-sm);
}
.user-msg .msg-bubble {
  background: var(--accent); color: #fff;
  border-bottom-right-radius: var(--radius-sm);
}
.user-msg .msg-text { color: #fff; }
.user-msg .msg-time { color: rgba(255,255,255,0.7); }

.msg-text { font-size: 0.9rem; line-height: 1.65; white-space: pre-wrap; word-break: break-word; }
.msg-time { font-size: 0.68rem; color: var(--text-muted); margin-top: 4px; display: flex; align-items: center; gap: 4px; }
.typing-dot { color: var(--accent); animation: blink 0.7s infinite; }
@keyframes blink { 0%,100%{opacity:1} 50%{opacity:0} }

.input-area { padding: 14px 20px 18px; border-top: 1px solid var(--border-subtle); background: var(--bg-primary); flex-shrink: 0; }
.input-row {
  display: flex; align-items: flex-end; gap: 8px;
  background: var(--bg-secondary); border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg); padding: 8px 10px 8px 16px;
  transition: border-color 0.2s;
}
.input-row:focus-within { border-color: var(--accent); box-shadow: 0 0 0 3px var(--accent-glow); }
.input-field {
  flex: 1; border: none; background: transparent;
  color: var(--text-primary); font-size: 0.9rem; font-family: var(--font-ui);
  resize: none; outline: none; line-height: 1.5; min-height: 22px; max-height: 120px;
}
.input-field::placeholder { color: var(--text-muted); }
.input-field:disabled { opacity: 0.5; }

.send-btn {
  width: 38px; height: 38px; border-radius: var(--radius-md);
  border: none; background: var(--accent); color: #fff;
  cursor: pointer; display: flex; align-items: center; justify-content: center;
  flex-shrink: 0; transition: all 0.2s;
}
.send-btn:hover:not(:disabled) { box-shadow: 0 2px 12px var(--accent-glow); }
.send-btn:disabled { opacity: 0.3; cursor: not-allowed; }

/* Markdown 渲染样式 */
.markdown-body :deep(p) { margin-bottom: 8px; }
.markdown-body :deep(p:last-child) { margin-bottom: 0; }
.markdown-body :deep(code) {
  background: rgba(0,0,0,0.05); padding: 2px 6px; border-radius: 4px;
  font-family: var(--font-mono); font-size: 0.82em;
}
.markdown-body :deep(pre) {
  background: #1d1d1f; color: #f5f5f7; padding: 12px 16px; border-radius: var(--radius-sm);
  overflow-x: auto; margin: 8px 0; font-size: 0.8rem; line-height: 1.5;
}
.markdown-body :deep(pre code) { background: none; padding: 0; font-size: inherit; }
.markdown-body :deep(strong) { font-weight: 600; }
.markdown-body :deep(a) { color: var(--accent); text-decoration: underline; }

/* 消息操作栏 */
.msg-actions {
  display: flex; gap: 4px; margin-top: 6px;
  opacity: 0; transition: opacity 0.15s;
}
.ai-msg:hover .msg-actions { opacity: 1; }
.action-btn {
  width: 28px; height: 28px; border-radius: 6px;
  border: 1px solid var(--border-subtle); background: var(--bg-primary);
  color: var(--text-secondary); cursor: pointer;
  display: flex; align-items: center; justify-content: center;
  transition: all 0.15s;
}
.action-btn:hover { color: var(--accent); border-color: var(--accent-light); }

/* 流式打字光标 */
.ai-streaming .msg-text::after {
  content: '▊';
  color: var(--accent);
  animation: blink-cursor 0.8s infinite;
}
@keyframes blink-cursor { 0%, 100% { opacity: 1; } 50% { opacity: 0; } }

@media (max-width: 768px) {
  .messages { padding: 14px 12px; }
  .msg { max-width: 92%; }
  .input-area { padding: 10px 12px 14px; }
}
</style>
