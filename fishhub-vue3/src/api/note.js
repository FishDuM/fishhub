import axios from '@/axios'

const API_PREFIX = '/note'

export function publishNote(note) {
  return axios.post(`${API_PREFIX}/note/publish`, note)
}

export function getDiscoverNotePageList(channelId, cursor) {
  return axios.post(`${API_PREFIX}/discover/note/list`, { channelId, cursor })
}

export function getNoteDetail(id) {
  return axios.post(`${API_PREFIX}/note/detail`, { id })
}

export function getNoteInteractionState(noteId) {
  return axios.post(`${API_PREFIX}/note/isLikedAndCollectedData`, { noteId })
}

export function likeNote(noteId) {
  return axios.post(`${API_PREFIX}/note/like`, { id: noteId })
}

export function unlikeNote(noteId) {
  return axios.post(`${API_PREFIX}/note/unlike`, { id: noteId })
}

export function collectNote(noteId) {
  return axios.post(`${API_PREFIX}/note/collect`, { id: noteId })
}

export function uncollectNote(noteId) {
  return axios.post(`${API_PREFIX}/note/uncollect`, { id: noteId })
}

export function updateNoteVisibility(noteId, visible) {
  return axios.post(`${API_PREFIX}/note/visible`, { id: noteId, visible })
}

export function getPublishedNoteList(userId, cursor) {
  return axios.post(`${API_PREFIX}/note/published/list`, { userId, cursor })
}

export function getCollectedNoteList(userId, cursor) {
  return axios.post(`${API_PREFIX}/note/collected/list`, {
    userId,
    cursorTime: cursor?.time,
    cursorId: cursor?.id,
  })
}

export function getLikedNoteList(userId, cursor) {
  return axios.post(`${API_PREFIX}/note/liked/list`, {
    userId,
    cursorTime: cursor?.time,
    cursorId: cursor?.id,
  })
}
