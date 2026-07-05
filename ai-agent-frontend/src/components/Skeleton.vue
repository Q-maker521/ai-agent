<template>
  <div class="skeleton-block">
    <div
      v-for="i in lines"
      :key="i"
      class="skeleton-line"
      :style="{ width: i === lines ? '60%' : widths[(i - 1) % widths.length] }"
    />
  </div>
</template>

<script setup>
defineProps({
  lines: { type: Number, default: 3 }
})

const widths = ['100%', '85%', '72%', '60%']
</script>

<style scoped>
.skeleton-block { padding: 12px 0; }
.skeleton-line {
  height: 14px; border-radius: 6px;
  background: linear-gradient(90deg, #e8e8ed 25%, #f0f0f5 50%, #e8e8ed 75%);
  background-size: 200% 100%;
  animation: shimmer 1.5s infinite;
  margin-bottom: 10px;
}
@keyframes shimmer {
  0% { background-position: -200% 0; }
  100% { background-position: 200% 0; }
}
</style>
