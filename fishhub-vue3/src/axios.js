import axios from 'axios'
import { message } from '@/utils/message'
import { useUserStore } from '@/stores/user'

const instance = axios.create({
  baseURL: '/api',
  timeout: 15000,
  withCredentials: true // 允许跨源携带 HttpOnly Cookie
})

instance.interceptors.request.use((config) => {
  const token = useUserStore().token
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

instance.interceptors.response.use(
  response => response.data,
  (error) => {
    const status = error.response?.status
    if (status === 401) {
      const userStore = useUserStore()
      if (!userStore.showLoginModal) {
        userStore.logout()
        userStore.openLoginModal()
        message.show('请先登录')
      }
    } else {
      message.show(error.response?.data?.message || '请求失败')
    }
    return Promise.reject(error)
  }
)

export default instance
