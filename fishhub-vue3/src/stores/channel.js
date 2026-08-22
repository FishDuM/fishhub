import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getAllChannel } from '@/api/channel'

export const useChannelStore = defineStore('channel', () => {
  const channels = ref([])
  
  const loadChannels = async () => {
    try {
      const res = await getAllChannel()
      if (res.success && res.data) {
        channels.value = res.data
      }
    } catch (error) {
      console.error('加载频道数据失败:', error)
    }
  }
  
  const getChannelNameById = (id) => {
    const channel = channels.value.find(item => item.id === id)
    return channel ? channel.name : ''
  }
  
  return {
    channels,
    loadChannels,
    getChannelNameById
  }
}) 