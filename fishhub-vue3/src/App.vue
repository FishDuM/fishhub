<template>
  <router-view></router-view>
  
  <!-- 登录模态框 -->
  <Teleport to="body">
    <LoginModal v-model:visible="showLoginModal" />
  </Teleport>
</template>

<script setup>
import { provide, onMounted, toRef } from 'vue'
import LoginModal from '@/components/auth/LoginModal.vue'
import { useUserStore } from '@/stores/user'
import { useChannelStore } from '@/stores/channel'
import { initializeTheme } from '@/composables/useTheme'

const userStore = useUserStore()
const showLoginModal = toRef(userStore, 'showLoginModal')
provide('showLoginModal', showLoginModal)

const channelStore = useChannelStore()

onMounted(() => {
  initializeTheme()
  channelStore.loadChannels()
})
</script>

<style scoped>
</style>
