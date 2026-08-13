import axios from '@/axios'

const API_PREFIX = '/note/channel'

export function getAllChannel() {
  return axios.post(`${API_PREFIX}/list`)
}

