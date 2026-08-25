import { createVNode, render } from 'vue'
import Message from '@/components/common/Message.vue'

let messageExposed = null

function getExposed() {
  if (!messageExposed && typeof document !== 'undefined' && document.body) {
    const container = document.createElement('div')
    document.body.appendChild(container)
    const messageInstance = createVNode(Message)
    render(messageInstance, container)
    messageExposed = messageInstance.component?.exposed
  }
  return messageExposed
}

function extractMessage(input, defaultMsg = '操作失败') {
  if (!input) return defaultMsg
  if (typeof input === 'string') return input
  if (typeof input === 'object') {
    return (
      input.message ||
      input.errorMessage ||
      input.msg ||
      input.error ||
      input.response?.data?.message ||
      input.response?.data?.errorMessage ||
      input.data?.message ||
      input.data?.errorMessage ||
      defaultMsg
    )
  }
  return String(input)
}

export const message = {
  show(content, options = {}) {
    const text = extractMessage(content, options.defaultMsg || '操作失败')
    const exposed = getExposed()
    if (exposed) {
      exposed.show({
        content: text,
        type: options.type || 'info',
        duration: options.duration || 2500,
        ...options
      })
    } else {
      console.warn('[Message Toast]', text)
    }
  },
  error(content, options = {}) {
    this.show(content, { type: 'error', ...options })
  },
  success(content, options = {}) {
    this.show(content, { type: 'success', ...options })
  },
  warning(content, options = {}) {
    this.show(content, { type: 'warning', ...options })
  },
  info(content, options = {}) {
    this.show(content, { type: 'info', ...options })
  }
}
