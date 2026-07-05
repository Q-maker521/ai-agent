<template>
  <router-view v-slot="{ Component }">
    <Transition name="page" mode="out-in">
      <component :is="Component" />
    </Transition>
  </router-view>
  <Toast ref="toastRef" :message="toastMsg" :type="toastType" />
</template>

<script setup>
import { ref, provide } from 'vue'
import Toast from './components/Toast.vue'

const toastRef = ref(null)
const toastMsg = ref('')
const toastType = ref('success')

function showToast(message, type = 'success') {
  // 触发 Toast 重新渲染的关键：先清空再设置
  toastMsg.value = ''
  setTimeout(() => {
    toastMsg.value = message
    toastType.value = type
  }, 0)
}

provide('toast', showToast)
</script>
