<template>
  <!-- header 本身保持全宽，添加白色背景和阴影 -->
  <div class="header-wrap">
    <header class="app-header">
    <!-- 内容区域使用容器宽度限制 -->
    <div class="header-content" :class="{ 'search-is-open': isSearchOpen }">
      <!-- logo 容器，与左侧栏对齐 -->
      <div v-show="!isSearchOpen" class="brand-wrap">
        <a href="/" class="flex-shrink-0 block select-none no-underline">
          <img src="data:image/png;base64,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" 
           class="brand-logo" />
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
            class="w-[480px] h-10 px-4 py-2 rounded-full bg-[#f6f6f6] focus:outline-none text-sm caret-[#ff2442]"
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
            <button class="cursor-pointer" aria-label="搜索" @click="openSearch">
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
.brand-logo { display: none; }
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
