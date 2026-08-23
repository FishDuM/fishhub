import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export const useUserStore = defineStore('user', () => {
  const token = ref('')
  const profile = ref({})
  const showLoginModal = ref(false)

  // 会话凭据由 HttpOnly Cookie 承载，前端以 profile.userId 判定登录态
  const isLoggedIn = computed(() => !!profile.value.userId)

  const setProfile = (newProfile) => {
    profile.value = newProfile
  }

  const setToken = (newToken) => {
    token.value = newToken
  }

  const openLoginModal = () => {
    showLoginModal.value = true
  }

  const closeLoginModal = () => {
    showLoginModal.value = false
  }

  const logout = () => {
    token.value = ''
    profile.value = {}
  }

  return {
    token,
    profile,
    showLoginModal,
    isLoggedIn,
    setProfile,
    setToken,
    openLoginModal,
    closeLoginModal,
    logout,
  }
}, {
  // 仅持久化 profile（公开资料），不再持久化 token：会话凭据由 httpOnly Cookie 承载
  persist: {
    paths: ['profile']
  }
})
