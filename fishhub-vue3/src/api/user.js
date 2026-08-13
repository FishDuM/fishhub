import axios from '@/axios'

const API_PREFIX = '/user/user'

export function getUserProfile(userId) {
  return axios.post(`${API_PREFIX}/profile`, { userId: userId === 'undefined' ? null : userId })
}

/**
 * 更新用户资料
 */
export function updateUserProfile(data) {
  const formData = new FormData()

  if (data.avatar && data.avatar instanceof File) {
    formData.append('avatar', data.avatar)
  }
  if (data.backgroundImg && data.backgroundImg instanceof File) {
    formData.append('backgroundImg', data.backgroundImg)
  }

  if (data.nickname) formData.append('nickname', data.nickname)
  if (data.fishhubId) formData.append('fishhubId', data.fishhubId)
  if (data.sex !== undefined && data.sex !== null) formData.append('sex', data.sex)
  if (data.birthday) formData.append('birthday', data.birthday)
  formData.append('introduction', data.introduction ?? '')
  
  return axios.post(`${API_PREFIX}/update`, formData, {
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  })
}


