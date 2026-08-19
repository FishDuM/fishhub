import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useUserStore = defineStore('user', () => {
  const token = ref('')
  // 主页用户信息
  const profile = ref({})
  // 全局登录弹窗显隐控制
  const showLoginModal = ref(false)

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

  // 退出登录
  const logout = () => {
    token.value = ''
    // 删除用户信息
    profile.value = {}
  }

  return {
    token,
    profile,
    showLoginModal,
    setProfile,
    setToken,
    openLoginModal,
    closeLoginModal,
    logout,
  }
}, 
{
  // 开启持久化，仅持久化 token 和 profile，不持久化弹窗状态
  persist: {
    paths: ['token', 'profile']
  }
})
