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

export function getFollowingList(userId, cursor) {
  return axios.post(`${API_PREFIX}/following/list`, { userId, cursor })
}

export function getFansList(userId, cursor) {
  return axios.post(`${API_PREFIX}/fans/list`, { userId, cursor })
}

