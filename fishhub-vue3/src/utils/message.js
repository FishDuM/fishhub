import { createVNode, render } from 'vue'
import Message from '@/components/common/Message.vue'

const messageInstance = createVNode(Message)
const container = document.createElement('div')
document.body.appendChild(container)
render(messageInstance, container)

export const message = {
  show(content, options = {}) {
    messageInstance.component.exposed.show({
      content: content || '请求失败',
      ...options
    })
  }
}
