import axios from '@/axios'

const API_PREFIX = '/comment/comment'

export function getCommentList(noteId, pageNo) {
  return axios.post(`${API_PREFIX}/list`, { noteId, pageNo })
}

export function publishComment(comment) {
  return axios.post(`${API_PREFIX}/publish`, comment)
}

export function getChildCommentList(parentCommentId, pageNo) {
  return axios.post(`${API_PREFIX}/child/list`, { parentCommentId, pageNo })
}

export function likeComment(commentId) {
  return axios.post(`${API_PREFIX}/like`, { commentId })
}

export function unlikeComment(commentId) {
  return axios.post(`${API_PREFIX}/unlike`, { commentId })
}

export function getLikedCommentIds(commentIds) {
  return axios.post(`${API_PREFIX}/liked/ids`, { commentIds })
}

export function deleteComment(commentId) {
  return axios.post(`${API_PREFIX}/delete`, { commentId })
}

