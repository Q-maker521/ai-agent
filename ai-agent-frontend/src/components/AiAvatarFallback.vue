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
.rag { background: var(--accent-light); color: var(--accent); }
.agent { background: var(--accent-light); color: var(--accent); }
</style>
