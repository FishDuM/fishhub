<template>
  <nav class="mobile-bottom-nav" aria-label="移动端主导航">
    <router-link to="/discover" class="mobile-nav-item" active-class="mobile-nav-active" aria-label="发现">
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor"><path d="m3 10 9-7 9 7v10a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1V10Z" stroke-width="1.8" stroke-linejoin="round"/></svg>
    </router-link>
    <button class="mobile-nav-item" aria-label="搜索" @click="handleOpenSearch">
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor"><circle cx="11" cy="11" r="6.5" stroke-width="1.8"/><path d="m16 16 4.2 4.2" stroke-width="1.8" stroke-linecap="round"/></svg>
    </button>
    <button class="mobile-nav-item mobile-publish" aria-label="发布" @click="handlePublishNote">
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor"><rect x="3" y="5" width="18" height="14" rx="4" stroke-width="1.8"/><path d="M12 8.5v7M8.5 12h7" stroke-width="1.8" stroke-linecap="round"/></svg>
    </button>
    <router-link v-if="isLoggedIn" :to="`/user/profile/${userStore.profile.userId}`" class="mobile-nav-item" active-class="mobile-nav-active" aria-label="我的">
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor"><circle cx="12" cy="8" r="3.5" stroke-width="1.8"/><path d="M5 21a7 7 0 0 1 14 0" stroke-width="1.8" stroke-linecap="round"/></svg>
    </router-link>
    <button v-else class="mobile-nav-item" aria-label="登录" @click="handleShowLogin">
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor"><circle cx="12" cy="8" r="3.5" stroke-width="1.8"/><path d="M5 21a7 7 0 0 1 14 0" stroke-width="1.8" stroke-linecap="round"/></svg>
    </button>
  </nav>

  <aside class="desktop-sidebar fixed top-17 bottom-0 bg-white">
    <div class="w-[320px]">
      <nav class="py-5">
        <ul class="space-y-2">
          <li class="px-5">
            <router-link 
              to="/discover" 
              class="font-bold flex items-center px-5 py-3 rounded-full left-nav"
              active-class="left-nav-active"
            >
              <SidebarNavIcon type="home" class="w-[20px] h-[20px] mr-3 text-[var(--color-secondary-label)]" />
              发现
            </router-link>
          </li>
          <li class="px-5">
            <button 
              class="font-bold flex items-center px-5 py-3 rounded-full left-nav w-full text-left"
              @click="handlePublishNote"
            >
              <SidebarNavIcon type="publish" class="w-[18px] h-[18px] mr-3 text-[var(--color-secondary-label)]" />
              发布
            </button>
          </li>
          <li v-if="!isLoggedIn" class="px-5 space-y-3">
            <button 
              v-if="!isLoggedIn" 
              class="w-full bg-[var(--color-primary)] text-[var(--color-primary-contrast)] font-bold rounded-full py-3 mt-4 cursor-pointer"
              @click="handleShowLogin"
            >
              登录
            </button>

            <div class="rounded-xl px-5 py-4 border border-gray-200">
              <h3 class="text-sm mb-2">马上登录即可</h3>
              <div class="space-y-2 text-[var(--color-tertiary-label)] text-sm">
                <div class="flex items-center">
                  <svg class="w-4 h-4 mr-2" viewBox="0 0 24 24" fill="none">
                    <path d="M5 13l4 4L19 7" stroke="currentColor" stroke-width="2"/>
                  </svg>
                  刷到更懂你的优质内容
                </div>
                <div class="flex items-center">
                  <svg class="w-4 h-4 mr-2" viewBox="0 0 24 24" fill="none">
                    <path d="M21 11.5a8.38 8.38 0 0 1-.9 3.8 8.5 8.5 0 0 1-7.6 4.7 8.38 8.38 0 0 1-3.8-.9L3 21l1.9-5.7a8.38 8.38 0 0 1-.9-3.8 8.5 8.5 0 0 1 4.7-7.6 8.38 8.38 0 0 1 3.8-.9h.5a8.48 8.48 0 0 1 8 8v.5z" stroke="currentColor" stroke-width="2"/>
                  </svg>
                  搜索最新种草、拔草信息
                </div>
                <div class="flex items-center">
                  <svg class="w-4 h-4 mr-2" viewBox="0 0 24 24" fill="none">
                    <path d="M12 20l-1.45-1.32C5.4 13.93 2 10.88 2 7.5 2 4.42 4.42 2 7.5 2c1.74 0 3.41.81 4.5 2.09C13.09 2.81 14.76 2 16.5 2 19.58 2 22 4.42 22 7.5c0 3.38-3.4 6.43-8.55 11.18L12 20z" stroke="currentColor" stroke-width="2"/>
                  </svg>
                  查看收藏、点赞的笔记
                </div>
                <div class="flex items-center">
                  <svg class="w-4 h-4 mr-2" viewBox="0 0 24 24" fill="none">
                    <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8zM23 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75" stroke="currentColor" stroke-width="2"/>
                  </svg>
                  与他人更好地互动、交流
                </div>
              </div>
            </div>
          </li>
          <li v-if="isLoggedIn" class="px-5">
            <router-link 
              :to="`/user/profile/${userStore.profile.userId}`"
              class="font-bold flex items-center px-5 py-3 rounded-full left-nav"
              active-class="left-nav-active"
            >
              <UserAvatar
                :src="userStore.profile.avatar"
                :alt="`${userStore.profile.nickname || '当前用户'}的头像`"
                class="w-[22px] h-[22px] rounded-full object-cover mr-3"
              />
              我
            </router-link>
          </li>
        </ul>
      </nav>
      <div class="absolute bottom-8 left-0 w-full">
        <div class="px-5 pl-10 relative">
          <button 
            class="font-bold flex items-center px-5 py-3 rounded-full hover:bg-gray-100 w-full"
            @click="toggleMoreMenu"
          >
            <svg class="w-[24px] h-[24px] mr-3" viewBox="0 0 24 24" fill="none">
              <path d="M4 6h16M4 12h16M4 18h16" stroke="currentColor" stroke-width="2"/>
            </svg>
            更多
          </button>

          <MoreMenu v-model:visible="showMoreMenu" />
        </div>
      </div>
    </div>
  </aside>

  <PublishModal v-model:visible="showPublishModal" />
</template>

<script setup>
import { ref, inject, computed } from 'vue'
import MoreMenu from './MoreMenu.vue'
import SidebarNavIcon from './SidebarNavIcon.vue'
import PublishModal from '@/components/note/PublishModal.vue'
import UserAvatar from '@/components/common/UserAvatar.vue'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()

const isLoggedIn = computed(() => !!userStore.token)

const showLoginModal = inject('showLoginModal')
const showMoreMenu = ref(false)
const showPublishModal = ref(false)

const toggleMoreMenu = () => {
  showMoreMenu.value = !showMoreMenu.value
}

const handleShowLogin = () => {
  showLoginModal.value = true
}

const handleOpenSearch = () => {
  window.dispatchEvent(new Event('open-mobile-search'))
}

const handlePublishNote = () => {
  if (!isLoggedIn.value) {
    showLoginModal.value = true
    return
  }
  showPublishModal.value = true
}
</script> 

<style scoped>
.mobile-bottom-nav { display: none; }
.desktop-sidebar {
  top: 72px;
  left: 0;
  /* 瀑布流创建了独立的层叠上下文，侧栏及其菜单需整体位于其上方。 */
  z-index: 40;
}

.left-nav:hover {
  background-color: var(--color-active-background);
}

.left-nav-active {
  background-color: var(--color-active-background);
}

@media (max-width: 1279px) and (min-width: 768px) {
  .desktop-sidebar > div { width: 240px; }
  .desktop-sidebar :deep(.left-nav) { padding-inline: 16px; }
}

@media (max-width: 767px) {
  .desktop-sidebar { display: none; }

  .mobile-bottom-nav {
    position: fixed;
    z-index: 40;
    right: 0;
    bottom: 0;
    left: 0;
    display: flex;
    height: calc(64px + env(safe-area-inset-bottom));
    padding: 0 16px env(safe-area-inset-bottom);
    align-items: center;
    justify-content: space-around;
    background: color-mix(in srgb, var(--color-surface) 98%, transparent);
    border-top: 1px solid var(--color-border);
    backdrop-filter: blur(12px);
  }

  .mobile-nav-item {
    display: flex;
    width: 48px;
    height: 48px;
    align-items: center;
    justify-content: center;
    color: var(--color-secondary-label);
    border-radius: 14px;
  }

  .mobile-nav-item svg { width: 25px; height: 25px; }
  .mobile-nav-active { color: var(--color-primary-label); }
  .mobile-publish { color: var(--color-primary-label); }
}
</style>
