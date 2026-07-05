<template>
  <Teleport to="body">
    <Transition name="toast" @after-leave="$emit('empty')">
      <div v-if="visible" class="toast" :class="type">
        <span class="toast-icon">{{ iconMap[type] }}</span>
        <span class="toast-msg">{{ message }}</span>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup>
import { ref, watch } from 'vue'

const props = defineProps({
  message: { type: String, default: '' },
  type: { type: String, default: 'success' }
})

const visible = ref(false)
const iconMap = { success: '✓', error: '✗', info: 'ℹ' }
let timer = null

watch(() => props.message, (val) => {
  if (!val) return
  visible.value = true
  clearTimeout(timer)
  timer = setTimeout(() => { visible.value = false }, 2800)
})
</script>

<style scoped>
.toast {
  position: fixed; top: 20px; left: 50%; transform: translateX(-50%);
  z-index: 9999;
  display: flex; align-items: center; gap: 8px;
  padding: 10px 20px; border-radius: var(--radius-full);
  font-size: 0.85rem; font-weight: 500;
  box-shadow: var(--shadow-lg);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  pointer-events: none;
}
.toast.success { background: rgba(48,177,88,0.92); color: #fff; }
.toast.error   { background: rgba(255,59,48,0.92); color: #fff; }
.toast.info    { background: rgba(0,113,227,0.92); color: #fff; }
.toast-icon { font-size: 1rem; font-weight: 700; }

.toast-enter-active { transition: all 0.35s var(--spring); }
.toast-leave-active { transition: all 0.2s var(--ease-out); }
.toast-enter-from { opacity: 0; transform: translateX(-50%) translateY(-12px) scale(0.9); }
.toast-leave-to   { opacity: 0; transform: translateX(-50%) translateY(-8px) scale(0.95); }
</style>
