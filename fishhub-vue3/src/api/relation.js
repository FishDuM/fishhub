import axios from '@/axios'

const API_PREFIX = '/relation/relation'

export function followUser(followUserId) {
  return axios.post(`${API_PREFIX}/follow`, { followUserId })
}

export function unfollowUser(unfollowUserId) {
  return axios.post(`${API_PREFIX}/unfollow`, { unfollowUserId })
}

export function checkFollowing(targetUserId) {
  return axios.post(`${API_PREFIX}/is-following`, { targetUserId })
}

export function checkFollowingBatch(targetUserIds) {
  return axios.post(`${API_PREFIX}/is-following/batch`, { targetUserIds })
}

export function getFollowingList(userId, pageNo) {
  return axios.post(`${API_PREFIX}/following/list`, { userId, pageNo })
}

export function getFansList(userId, pageNo) {
  return axios.post(`${API_PREFIX}/fans/list`, { userId, pageNo })
}


