import axios from '@/axios'

const API_PREFIX = '/oss/file'

export function uploadFile(formData) {
  return axios.post(`${API_PREFIX}/upload`, formData, {
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  })
}


