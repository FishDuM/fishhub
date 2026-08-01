import { createVNode, render } from 'vue'
import Message from '@/components/common/Message.vue'

const messageInstance = createVNode(Message)
const container = document.createElement('div')
document.body.appendChild(container)
render(messageInstance, container)

export const message = {
  show(msg, duration) {
    // 兼容历史调用：部分组件传入 { type, content }，提示组件只渲染文本。
    const content = typeof msg === 'object' && msg !== null ? msg.content : msg
    messageInstance.component.exposed.show(content || '请求失败', duration)
  }
}
