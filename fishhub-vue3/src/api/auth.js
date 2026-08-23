import axios from '@/axios'

const API_PREFIX = '/auth'

export function getCaptcha() {
  return axios.get(`${API_PREFIX}/captcha`)
}

export function register(registerReqVO) {
  return axios.post(`${API_PREFIX}/register`, registerReqVO)
}

export function login(loginReqVO) {
  return axios.post(`${API_PREFIX}/login`, loginReqVO)
}

export function logout() {
  return axios.post(`${API_PREFIX}/logout`)
}
