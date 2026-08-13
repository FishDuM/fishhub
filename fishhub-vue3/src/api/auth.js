import axios from '@/axios'

const API_PREFIX = '/auth'

export function getVerificationCode(phone) {
  return axios.post(`${API_PREFIX}/verification/code/send`, { phone })
}

export function login(loginReqVO) {
  return axios.post(`${API_PREFIX}/login`, loginReqVO)
}

export function logout() {
  return axios.post(`${API_PREFIX}/logout`)
}
