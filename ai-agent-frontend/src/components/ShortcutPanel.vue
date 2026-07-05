<template>
  <Teleport to="body">
    <Transition name="modal">
      <div v-if="visible" class="overlay" @click.self="close">
        <div class="panel glass-strong">
          <h3>快捷键</h3>
          <div class="shortcut-list">
            <div v-for="s in shortcuts" :key="s.key" class="shortcut-row">
              <kbd>{{ s.key }}</kbd>
              <span>{{ s.desc }}</span>
            </div>
          </div>
          <button class="close-btn" @click="close">关闭</button>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'

const visible = ref(false)
const shortcuts = [
  { key: 'Enter', desc: '发送消息' },
  { key: 'Esc', desc: '停止生成' },
  { key: '/', desc: '聚焦输入框' },
  { key: '?', desc: '显示/隐藏此面板' },
]

function onKeydown(e) {
  if (e.key === '?' && !isInputElement(e.target)) {
    e.preventDefault()
    visible.value = !visible.value
  }
  if (e.key === 'Escape' && visible.value) {
    visible.value = false
  }
}

function isInputElement(el) {
  const tag = el.tagName
  return tag === 'INPUT' || tag === 'TEXTAREA' || el.isContentEditable
}

function close() { visible.value = false }

onMounted(() => window.addEventListener('keydown', onKeydown))
onBeforeUnmount(() => window.removeEventListener('keydown', onKeydown))
</script>

<style scoped>
.overlay {
  position: fixed; inset: 0; z-index: 9998;
  display: flex; align-items: center; justify-content: center;
  background: rgba(0,0,0,0.15);
  backdrop-filter: blur(4px);
}
.panel {
  width: 320px; padding: 24px; border-radius: var(--radius-lg);
  box-shadow: var(--shadow-lg);
  border: 1px solid var(--border-subtle);
}
.panel h3 {
  font-size: 1rem; font-weight: 700; margin-bottom: 16px;
  color: var(--text-primary);
}
.shortcut-row {
  display: flex; align-items: center; gap: 12px;
  padding: 8px 0; font-size: 0.85rem; color: var(--text-secondary);
}
kbd {
  min-width: 52px; text-align: center;
  padding: 3px 8px; border-radius: 4px;
  background: var(--bg-secondary); border: 1px solid var(--border-subtle);
  font-family: var(--font-mono); font-size: 0.75rem;
  color: var(--text-primary);
}
.close-btn {
  margin-top: 16px; width: 100%; padding: 8px;
  border-radius: var(--radius-sm); border: 1px solid var(--border-subtle);
  background: var(--bg-secondary); color: var(--text-secondary);
  font-size: 0.82rem; cursor: pointer;
}
.close-btn:hover { color: var(--text-primary); }
.modal-enter-active { transition: all 0.2s var(--ease-out); }
.modal-leave-active { transition: all 0.15s; }
.modal-enter-from, .modal-leave-to { opacity: 0; }
.modal-enter-from .panel { transform: scale(0.95); }
</style>
