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
  response => {
    const data = response.data
    // 兼容部分网关或服务在业务层返回 200 但内层带有 401 未登录状态码的情况
    if (data && data.success === false && (data.errorCode === '401' || data.errorCode === 'UNAUTHORIZED' || data.errorCode === 'AUTH-10001')) {
      const userStore = useUserStore()
      if (!userStore.showLoginModal) {
        userStore.logout()
        userStore.openLoginModal()
        message.show('登录已过期，请重新登录')
      }
    }
    return data
  },
  (error) => {
    const status = error.response?.status
    const data = error.response?.data
    if (status === 401 || (data && (data.errorCode === '401' || data.errorCode === 'UNAUTHORIZED' || data.errorCode === 'AUTH-10001'))) {
      const userStore = useUserStore()
      if (!userStore.showLoginModal) {
        userStore.logout()
        userStore.openLoginModal()
        message.show('登录已过期，请重新登录')
      }
    } else {
      message.show(data?.message || error.response?.data?.errorMessage || error.message || '请求失败')
    }
    return Promise.reject(error)
  }
)

export default instance
