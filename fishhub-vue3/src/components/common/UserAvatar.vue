<template>
  <img :src="avatarSrc" :alt="alt" @error="handleError" />
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import defaultAvatar from '@/assets/avatar.png'

const props = defineProps({
  src: {
    type: String,
    default: ''
  },
  alt: {
    type: String,
    default: '用户头像'
  }
})

const loadFailed = ref(false)
const normalizedSrc = computed(() => props.src?.trim() || '')
const avatarSrc = computed(() => loadFailed.value || !normalizedSrc.value ? defaultAvatar : normalizedSrc.value)

watch(() => props.src, () => {
  loadFailed.value = false
})

const handleError = () => {
  if (avatarSrc.value !== defaultAvatar) {
    loadFailed.value = true
  }
}
</script>
