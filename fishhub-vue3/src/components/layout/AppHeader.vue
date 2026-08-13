<template>
  <!-- header 本身保持全宽，添加白色背景和阴影 -->
  <div class="header-wrap">
    <header class="app-header">
    <!-- 内容区域使用容器宽度限制 -->
    <div class="header-content" :class="{ 'search-is-open': isSearchOpen }">
      <!-- logo 容器，与左侧栏对齐 -->
      <div v-show="!isSearchOpen" class="brand-wrap">
        <a href="/" class="flex-shrink-0 block select-none no-underline">
        </a>
      </div>
      <div v-show="!isSearchOpen" class="mobile-header-actions">
        <div class="mobile-more-wrap">
          <button class="mobile-more-button" aria-label="更多" @click="toggleMoreMenu">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor"><path d="M5 7h14M5 12h14M5 17h14" stroke-width="1.8" stroke-linecap="round"/></svg>
          </button>
          <MoreMenu v-model:visible="showMoreMenu" />
        </div>
      </div>
      <!-- 搜索框容器 -->
      <div class="search-wrap" :class="{ 'search-open': isSearchOpen }">
        <div class="search-box">
          <input 
            ref="searchInputRef"
            v-model="searchKeyword"
            type="text" 
            placeholder="搜索飞鱼社区" 
            class="w-[480px] h-10 px-4 py-2 rounded-full bg-[var(--color-input-surface)] focus:outline-none text-sm caret-[var(--color-primary)]"
            @keyup.enter="handleSearch"
          />
          <div class="absolute right-3 top-1/2 -translate-y-1/2 flex items-center gap-2">
            <button
              v-if="isSearchOpen"
              class="search-close"
              aria-label="关闭搜索"
              @click="closeSearch"
            >
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor"><path d="m6 6 12 12M18 6 6 18" stroke-width="2" stroke-linecap="round"/></svg>
            </button>
            <!-- 删除按钮 -->
            <button 
              v-if="searchKeyword && !isSearchOpen"
              class="w-5 h-5 flex items-center justify-center text-gray-500 hover:text-gray-600"
              @click="searchKeyword = ''"
            >
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor">
                <path d="M18 6L6 18M6 6l12 12" stroke-width="2" stroke-linecap="round"/>
              </svg>
            </button>
            <button class="cursor-pointer" aria-label="搜索" @click="handleSearchButtonClick">
              <!-- 搜索图标 -->
              <svg class="w-5 h-5 text-gray-500 hover:text-gray-600" viewBox="0 0 24 24" fill="none">
                <path d="M21 21L16.65 16.65M19 11C19 15.4183 15.4183 19 11 19C6.58172 19 3 15.4183 3 11C3 6.58172 6.58172 3 11 3C15.4183 3 19 6.58172 19 11Z" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
              </svg>
            </button>
          </div>
        </div>
      </div>
      <!-- 右侧按钮容器 -->
      <div class="header-actions">
        <span class="cursor-not-allowed text-gray-600 px-5 py-2.5 rounded-full hover:bg-gray-100 hover:text-gray-800">创作中心</span>
      </div>
    </div>
  </header>
  </div>
</template>

<script setup>
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import MoreMenu from './MoreMenu.vue'

const router = useRouter()
const route = useRoute()
const searchKeyword = ref(route.query.keyword || '')
const isSearchOpen = ref(false)
const searchInputRef = ref(null)
const showMoreMenu = ref(false)

const openSearch = async () => {
  if (window.innerWidth > 767) {
    searchInputRef.value?.focus()
    return
  }

  isSearchOpen.value = true
  await nextTick()
  searchInputRef.value?.focus()
}

const closeSearch = () => {
  isSearchOpen.value = false
  searchKeyword.value = ''
}

const toggleMoreMenu = () => {
  showMoreMenu.value = !showMoreMenu.value
}

// 处理搜索
const handleSearch = () => {
  if (!searchKeyword.value.trim()) {
    openSearch()
    return
  }
  router.push({
    name: 'Search',
    query: { keyword: searchKeyword.value.trim() }
  })
}

const handleSearchButtonClick = () => {
  if (window.innerWidth <= 767 && !isSearchOpen.value) {
    openSearch()
    return
  }
  handleSearch()
}

onMounted(() => {
  window.addEventListener('open-mobile-search', openSearch)
})

onBeforeUnmount(() => {
  window.removeEventListener('open-mobile-search', openSearch)
})
</script> 

<style scoped>
.app-header {
  position: fixed;
  inset: 0 0 auto;
  z-index: 30;
  height: 72px;
  padding: 0 clamp(20px, 4vw, 96px);
  background: color-mix(in srgb, var(--color-surface) 96%, transparent);
  border-bottom: 1px solid var(--color-border);
  backdrop-filter: blur(12px);
}

.header-content { display: flex; align-items: center; height: 100%; margin: 0 auto; }
.brand-wrap { padding-left: 40px; }
.brand-wrap a {
  display: block;
  width: 96px;
  height: 34px;
  background: url('@/assets/fishhub-logo.svg') center / contain no-repeat;
}
.search-wrap { display: flex; flex: 1; justify-content: center; }
.search-box { position: relative; }
.header-actions { display: flex; align-items: center; white-space: nowrap; }
.mobile-header-actions { display: none; }

@media (max-width: 1279px) {
  .app-header { padding-inline: 24px; }
  .brand-wrap { padding-left: 0; }
}

@media (max-width: 1023px) and (min-width: 768px) {
  .search-box input { width: min(38vw, 320px); }
  .header-actions :deep(span), .header-actions :deep(a) { padding-inline: 12px; }
}

@media (max-width: 767px) {
  .app-header { height: 64px; padding: 0 20px; }
  .header-content { justify-content: space-between; }
  .brand-wrap a { width: 96px; height: 34px; }
  .header-actions { display: none; }
  .mobile-header-actions { display: block; margin-left: auto; }
  .mobile-more-wrap { position: relative; }
  .mobile-more-button { display: flex; width: 40px; height: 40px; align-items: center; justify-content: center; color: var(--color-primary-label); }
  .mobile-more-button svg { width: 26px; height: 26px; }
  .mobile-more-wrap :deep(.absolute) {
    position: fixed;
    top: 56px;
    right: 12px;
    bottom: auto;
    left: auto;
    width: min(18rem, calc(100vw - 24px));
  }
  .search-wrap { display: none; }
  .search-box input { display: none; }
  .search-box > div { position: static; transform: none; }
  .search-box button { display: flex; width: 40px; height: 40px; align-items: center; justify-content: center; }
  .search-box button svg { width: 25px; height: 25px; color: var(--color-primary-label); }
  .search-close { display: none !important; }

  .header-content.search-is-open { justify-content: stretch; }
  .search-wrap.search-open { display: flex; flex: 1; }
  .search-wrap.search-open .search-box {
    display: flex;
    width: 100%;
    align-items: center;
    overflow: hidden;
    background: var(--color-surface-muted);
    border-radius: 999px;
  }
  .search-wrap.search-open input {
    display: block;
    width: auto;
    flex: 1;
    height: 40px;
    padding-right: 8px;
    background: transparent;
  }
  .search-wrap.search-open .search-box > div {
    position: static !important;
    display: flex;
    flex: 0 0 auto;
    align-items: center;
    gap: 0;
    padding-right: 2px;
    top: auto !important;
    right: auto !important;
    translate: none !important;
    transform: none !important;
  }
  .search-wrap.search-open .search-close { display: flex !important; }
  .search-wrap.search-open .search-close svg { width: 20px; height: 20px; }
}
</style>
