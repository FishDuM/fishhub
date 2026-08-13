<template>
  <Transition
    name="fade"
    appear
  >
    <div
      v-if="active || visible"
      class="loading-overlay pb-5"
    >
      <div class="spinner-container">
        <div class="spinner"></div>
        <div v-if="text" class="loading-text">{{ text }}</div>
      </div>
    </div>
  </Transition>
</template>

<script setup>
import { ref } from 'vue'

const props = defineProps({
  active: {
    type: Boolean,
    default: false
  },
  text: {
    type: String,
    default: ''
  },
  minDuration: {
    type: Number,
    default: 500
  }
})

const visible = ref(false)
let showStartTime = 0
let hideTimer = null

const show = () => {
  if (hideTimer) {
    clearTimeout(hideTimer)
    hideTimer = null
  }
  
  showStartTime = Date.now()
  visible.value = true
}

const hide = () => {
  const elapsedTime = Date.now() - showStartTime
  
  if (elapsedTime < props.minDuration) {
    const remainingTime = props.minDuration - elapsedTime
    hideTimer = setTimeout(() => {
      visible.value = false
      hideTimer = null
    }, remainingTime)
  } else {
    visible.value = false
  }
}

defineExpose({
  show,
  hide
})
</script>

<style scoped>
.loading-overlay {
  width: 100%;
  height: 100%;
  z-index: 9999;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: color-mix(in srgb, var(--color-page) 80%, transparent);
  backdrop-filter: blur(2px);
}

.spinner-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.spinner {
  width: 25px;
  height: 25px;
  border: 3px solid rgba(255, 36, 66, 0.2);
  border-radius: 50%;
  border-top-color: var(--color-primary);
  animation: spin 1s ease-in-out infinite;
}


@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.3s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

.loading-text {
    font-size: 12px;
    line-height: 18px;
    text-align: center;
    color: var(--color-tertiary-label);
    margin-top: 10px;
}
</style> 
