<template>
  <Transition name="dropdown">
    <div 
      v-if="visible" 
      class="absolute bottom-[55px] left-4 bg-white rounded-xl w-[18rem] shadow-lg 
      overflow-hidden border border-gray-100"
      style="z-index: 100;"
    >
      <!-- 主要菜单项 -->
      <div class="p-1.5">
        <div class="px-3 py-2 hover:bg-gray-100 text-gray-600 hover:text-gray-800 rounded-lg cursor-not-allowed flex items-center justify-between">
          <span>关于飞鱼社区</span>
          <svg class="w-4 h-4 text-gray-400" viewBox="0 0 24 24" fill="none" stroke="currentColor">
            <path d="M9 5l7 7-7 7" stroke-width="2" stroke-linecap="round"/>
          </svg>
        </div>
        <div class="px-3 py-2 hover:bg-gray-100 text-gray-600 hover:text-gray-800 rounded-lg cursor-not-allowed flex items-center justify-between">
          <span>隐私、协议</span>
          <svg class="w-4 h-4 text-gray-400" viewBox="0 0 24 24" fill="none" stroke="currentColor">
            <path d="M9 5l7 7-7 7" stroke-width="2" stroke-linecap="round"/>
          </svg>
        </div>
        <div class="px-3 py-2 hover:bg-gray-100 text-gray-600 hover:text-gray-800 rounded-lg cursor-not-allowed">
          帮助与客服
        </div>
      </div>

      <!-- 分隔线 -->
      <div class="h-[1px] bg-gray-100"></div>

      <!-- 次要菜单项 -->
      <div class="p-1.5">
        <div class="px-3 py-2 text-gray-400 text-xs">
          访问方式
        </div>
        <div class="px-3 py-2 hover:bg-gray-100 text-gray-600 hover:text-gray-800 rounded-lg cursor-not-allowed">
          键盘快捷键
        </div>
        <div class="px-3 py-2 hover:bg-gray-100 text-gray-600 hover:text-gray-800 rounded-lg cursor-not-allowed">
          添加飞鱼社区到桌面
        </div>
        <div class="px-3 py-2 hover:bg-gray-100 text-gray-600 hover:text-gray-800 rounded-lg cursor-not-allowed">
          打开小窗模式
        </div>
        <button
          class="theme-switch px-3 py-2 hover:bg-gray-100 text-gray-600 hover:text-gray-800 rounded-lg cursor-pointer flex w-full items-center justify-between"
          type="button"
          @click="toggleTheme"
        >
          <span>{{ isDark ? '切换浅色模式' : '切换深色模式' }}</span>
          <svg v-if="isDark" class="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor"><circle cx="12" cy="12" r="4" stroke-width="1.8"/><path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4" stroke-width="1.8" stroke-linecap="round"/></svg>
          <svg v-else class="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor"><path d="M20.5 15.3A8.5 8.5 0 0 1 8.7 3.5 8.5 8.5 0 1 0 20.5 15.3Z" stroke-width="1.8" stroke-linejoin="round"/></svg>
        </button>
      </div>

      <!-- 分隔线 -->
      <div v-if="isLoggedIn" class="h-[1px] bg-gray-100"></div>

      <!-- 设置相关 -->
      <div v-if="isLoggedIn" class="p-1.5">
        <div class="px-3 py-2 text-gray-400 text-xs">
          设置
        </div>
        <div 
          class="px-3 py-2 hover:bg-gray-50 text-gray-600 hover:text-gray-800 rounded-lg cursor-pointer"
          @click="handleLogout"
        >
          退出登录
        </div>
      </div>
    </div>
  </Transition>

  <!-- 点击外部关闭的遮罩层 -->
  <div v-if="visible" class="fixed inset-0" style="z-index: 99;" @click="onClose"></div>
</template>

<script setup>
import { useUserStore } from '@/stores/user'
import { logout } from '@/api/auth'
import { message } from '@/utils/message'
import { useRouter } from 'vue-router'
import { computed } from 'vue'
import { isDark, toggleTheme } from '@/composables/useTheme'

const router = useRouter()
const userStore = useUserStore()

// 使用 store 中的计算属性判断登录状态
const isLoggedIn = computed(() => !!userStore.token)

defineProps({
  visible: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['update:visible'])

const onClose = () => {
  emit('update:visible', false)
}

const handleLogout = () => {
  logout().then(res => {
    if (res.success) {
      userStore.logout() // 使用 store 的 logout 方法
      message.show('退出登录成功')
      onClose()
      // 跳转首页
      router.push('/')
    }
  })
}
</script>

<style scoped>
.dropdown-enter-active,
.dropdown-leave-active {
  transition: all 0.15s ease-out;
  transform-origin: top center;
}

.dropdown-enter-from,
.dropdown-leave-to {
  opacity: 0;
  transform: translateY(8px);
}
</style>
