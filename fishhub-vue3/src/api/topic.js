import axios from '@/axios'

const API_PREFIX = '/note/topic'

export function getTopicList(keyword) {
  return axios.post(`${API_PREFIX}/list`, { keyword })
}

