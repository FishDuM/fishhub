/**
 * 将时间转换为友好的相对时间格式（如“刚刚”、“5分钟前”、“昨天 14:30”、“3天前”等）
 * 支持 Date 对象、时间戳（毫秒/秒）、ISO 字符串、标准日期字符串（YYYY-MM-DD HH:mm:ss）
 * @param {string|number|Date} timeInput
 * @returns {string}
 */
export function formatRelativeTime(timeInput) {
  if (!timeInput) return ''
  
  let date
  if (timeInput instanceof Date) {
    date = timeInput
  } else if (typeof timeInput === 'number') {
    // 兼容秒级时间戳 (10位) 与毫秒级时间戳 (13位)
    date = new Date(timeInput < 1e11 ? timeInput * 1000 : timeInput)
  } else if (typeof timeInput === 'string') {
    const trimmed = timeInput.trim()
    // 尝试解析常见格式
    const parsedStr = trimmed.replace(/-/g, '/').replace('T', ' ').replace(/\.\d+/, '')
    date = new Date(parsedStr)
    if (isNaN(date.getTime())) {
      date = new Date(trimmed)
    }
  }

  if (!date || isNaN(date.getTime())) {
    return String(timeInput)
  }

  const now = new Date()
  const diffMs = now.getTime() - date.getTime()
  
  // 未来轻微时间差或1分钟内视为“刚刚”
  if (diffMs < 60 * 1000) {
    return '刚刚'
  }

  const diffMinutes = Math.floor(diffMs / (60 * 1000))
  if (diffMinutes < 60) {
    return `${diffMinutes}分钟前`
  }

  const diffHours = Math.floor(diffMs / (60 * 60 * 1000))
  
  // 检查是否是同一天
  const isSameDay = 
    now.getFullYear() === date.getFullYear() &&
    now.getMonth() === date.getMonth() &&
    now.getDate() === date.getDate()

  if (isSameDay) {
    return `${diffHours}小时前`
  }

  // 检查是否是昨天
  const yesterday = new Date(now)
  yesterday.setDate(now.getDate() - 1)
  const isYesterday = 
    yesterday.getFullYear() === date.getFullYear() &&
    yesterday.getMonth() === date.getMonth() &&
    yesterday.getDate() === date.getDate()

  const pad = (n) => String(n).padStart(2, '0')
  const hours = pad(date.getHours())
  const minutes = pad(date.getMinutes())

  if (isYesterday) {
    return `昨天 ${hours}:${minutes}`
  }

  const diffDays = Math.floor(diffMs / (24 * 60 * 60 * 1000))
  if (diffDays < 7) {
    return `${diffDays}天前`
  }

  const month = pad(date.getMonth() + 1)
  const day = pad(date.getDate())

  if (now.getFullYear() === date.getFullYear()) {
    return `${month}-${day}`
  }

  return `${date.getFullYear()}-${month}-${day}`
}
