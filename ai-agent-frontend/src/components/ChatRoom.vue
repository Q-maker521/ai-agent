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
            <div class="msg-text">{{ msg.content }}</div>
            <div class="msg-time">
              {{ formatTime(msg.time) }}
              <span v-if="connectionStatus === 'connecting' && index === messages.length - 1" class="typing-dot">●</span>
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
import { ref, watch, nextTick, onMounted } from 'vue'
import AiAvatarFallback from './AiAvatarFallback.vue'

const props = defineProps({
  messages: { type: Array, default: () => [] },
  connectionStatus: { type: String, default: 'disconnected' },
  aiType: { type: String, default: 'agent' }
})

const emit = defineEmits(['send-message'])
const inputMessage = ref('')
const messagesContainer = ref(null)

const send = () => {
  if (!inputMessage.value.trim() || props.connectionStatus === 'connecting') return
  emit('send-message', inputMessage.value)
  inputMessage.value = ''
}

const formatTime = (ts) => new Date(ts).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })

const scroll = async () => {
  await nextTick()
  if (messagesContainer.value) messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
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

@media (max-width: 768px) {
  .messages { padding: 14px 12px; }
  .msg { max-width: 92%; }
  .input-area { padding: 10px 12px 14px; }
}
</style>
