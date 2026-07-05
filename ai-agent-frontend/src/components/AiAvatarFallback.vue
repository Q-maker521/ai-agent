<template>
  <div class="avatar" :class="type">
    <span v-if="type === 'rag'">知</span>
    <span v-else>智</span>
  </div>
</template>

<script setup>
defineProps({ type: { type: String, default: 'agent' } })
</script>

<style scoped>
.avatar {
  width: 100%; height: 100%;
  display: flex; align-items: center; justify-content: center;
  font-size: 0.75rem; font-weight: 700;
  border-radius: var(--radius-sm);
}
.rag {
  background: linear-gradient(135deg, #e8f5e9, #c8e6c9);
  color: #2e7d32;
}
.agent {
  background: linear-gradient(135deg, var(--accent-light), rgba(0,113,227,0.15));
  color: var(--accent);
}
</style>
