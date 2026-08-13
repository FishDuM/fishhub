<template>
  <Transition name="fade">
    <div 
      v-if="visible"
      :class="typeClass"
      class="fixed left-1/2 top-1/2 -translate-x-1/2 -translate-y-1/2 z-[10000] px-6 py-3 rounded-full whitespace-nowrap font-bold text-white"
    >
      {{ message }}
    </div>
  </Transition>
</template>

<script setup>
import { computed, ref } from 'vue'

const visible = ref(false)
const message = ref('')
const type = ref('info')
let dismissTimer

const typeClass = computed(() => ({
  info: 'bg-[#333]',
  warning: 'bg-amber-600',
  error: 'bg-red-600',
  success: 'bg-emerald-600'
}[type.value] || 'bg-[#333]'))

const show = ({ content, type: nextType = 'info', duration = 2000 }) => {
  if (dismissTimer) {
    clearTimeout(dismissTimer)
  }
  message.value = content
  type.value = nextType
  visible.value = true
  dismissTimer = setTimeout(() => {
    visible.value = false
  }, duration)
}

// 暴露方法给外部使用
defineExpose({
  show
})
</script>

<style scoped>
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.3s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
