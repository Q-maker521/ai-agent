<template>
  <footer class="footer">
    <p>© {{ currentYear }} AI Research Assistant Agent</p>
  </footer>
</template>

<script setup>
import { computed } from 'vue'
const currentYear = computed(() => new Date().getFullYear())
</script>

<style scoped>
.footer {
  text-align: center; padding: 14px 20px;
  color: var(--text-muted); font-size: 0.76rem;
  background: transparent; border-top: 1px solid var(--border-subtle);
  flex-shrink: 0;
}
</style>
