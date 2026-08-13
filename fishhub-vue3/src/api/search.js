import axios from '@/axios'

const API_PREFIX = '/search/search'

export function searchNote(keyword, type, sort, publishTimeRange, pageNo) {
  return axios.post(`${API_PREFIX}/note`, { keyword, type, sort, publishTimeRange, pageNo })
}

export function searchUser(keyword, pageNo) {
  return axios.post(`${API_PREFIX}/user`, { keyword, pageNo })
}


