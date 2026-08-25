<template>
  <div class="search-result-page min-h-screen bg-white">
    <div class="border-b border-gray-100">
      <div class="search-result-tabs flex items-center my-2">
        <nav class="grow">
          <ul class="flex items-center">
            <li v-for="category in categories" :key="category.name">
              <a 
              @click.prevent="handleTabChange(category.type)"
                href="#" 
                class="px-4 py-2 rounded-full transition-colors h-[40px] hover:bg-[var(--color-active-background)]"
                :class="selectedType === category.type ? 'bg-[var(--color-surface-muted)] text-[var(--color-primary-label)] font-bold' : 'text-[var(--color-secondary-label)] hover:text-[var(--color-primary-label)]'"
              >
                {{ category.name }}
              </a>
            </li>
          </ul>
        </nav>
        <div
        class="cursor-pointer flex items-center px-3 hover:bg-[var(--color-surface-muted)] rounded-full text-[var(--color-secondary-label)] hover:text-[var(--color-primary-label)] relative h-[40px]">
          <div v-if="selectedType !== 2" class="flex items-center" @click="showFilter = !showFilter">
            <FilterIcon class="w-5 h-5" />
            <div>筛选</div>
          </div>

          <div 
            v-if="showFilter" 
            ref="filterRef"
            class="filter-panel absolute right-0 top-12 w-[460px] bg-white rounded-lg shadow-lg p-5 z-50 border border-gray-100"
          >
            <div class="space-y-5">
              <div>
                <div class="text-gray-600 font-medium mb-3 text-xs">排序依据</div>
                <div class="grid grid-cols-3 gap-2">
                  <button 
                    v-for="sort in sortOptions" 
                    :key="sort.value"
                    class="px-3 py-2 rounded-full text-sm inline-flex items-center justify-center"
                    :class="selectedSort === sort.value ? 'border border-[var(--color-primary)] text-[var(--color-primary)] bg-white' : 'bg-gray-100 text-gray-600 hover:bg-gray-200'"
                    @click="selectedSort = sort.value"
                  >
                    <div class="flex items-center justify-center">
                      <span>{{ sort.label }}</span>
                      <svg 
                        v-if="selectedSort === sort.value"
                        class="w-3 h-3 ml-1" 
                        viewBox="0 0 24 24" 
                        fill="none" 
                        stroke="currentColor"
                      >
                        <path d="M20 6L9 17l-5-5" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
                      </svg>
                    </div>
                  </button>
                </div>
              </div>

              <div>
                <div class="text-gray-600 font-medium mb-3 text-xs">笔记类型</div>
                <div class="grid grid-cols-3 gap-2">
                  <button 
                    v-for="type in noteTypes" 
                    :key="type.value"
                    class="px-3 py-2 rounded-full text-sm inline-flex items-center justify-center"
                    :class="selectedType === type.value ? 'border border-[var(--color-primary)] text-[var(--color-primary)] bg-white' : 'bg-gray-100 text-gray-600 hover:bg-gray-200'"
                    @click="selectedType = type.value"
                  >
                    <div class="flex items-center justify-center">
                      <span>{{ type.label }}</span>
                      <svg 
                        v-if="selectedType === type.value"
                        class="w-3 h-3 ml-1" 
                        viewBox="0 0 24 24" 
                        fill="none" 
                        stroke="currentColor"
                      >
                        <path d="M20 6L9 17l-5-5" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
                      </svg>
                    </div>
                  </button>
                </div>
              </div>

              <div>
                <div class="text-gray-600 font-medium mb-3 text-xs">发布时间</div>
                <div class="grid grid-cols-3 gap-2">
                  <button 
                    v-for="time in timeFilters" 
                    :key="time.value"
                    class="px-3 py-2 rounded-full text-sm inline-flex items-center justify-center"
                    :class="selectedTime === time.value ? 'border border-[var(--color-primary)] text-[var(--color-primary)] bg-white' : 'bg-gray-100 text-gray-600 hover:bg-gray-200'"
                    @click="selectedTime = time.value"
                  >
                    <div class="flex items-center justify-center">
                      <span>{{ time.label }}</span>
                      <svg 
                        v-if="selectedTime === time.value"
                        class="w-3 h-3 ml-1" 
                        viewBox="0 0 24 24" 
                        fill="none" 
                        stroke="currentColor"
                      >
                        <path d="M20 6L9 17l-5-5" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
                      </svg>
                    </div>
                  </button>
                </div>
              </div>

              <div class="flex gap-2 pt-2">
                <button 
                  class="cursor-pointer flex-1 h-10 font-bold rounded-full border border-gray-200 text-gray-600 hover:bg-gray-50"
                  @click="resetFilters"
                >
                  重置
                </button>
                <button 
                  class="cursor-pointer flex-1 h-10 font-bold rounded-full bg-[var(--color-primary)] text-[var(--color-primary-contrast)] hover:opacity-90"
                  @click="applyFilters"
                >
                  确定
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>


    <div class="container mx-auto mt-6">

      <LoadingSpinner :active="isLoading && currPageNo === 1" />

      <div v-if="activeTab !== 'users'" class="masonry-container">
        <div v-for="note in searchResults" :key="note.id" class="masonry-item">
          <NoteCard :note="note" @click="onNoteClick" @like-change="handleCardLikeChange" />
        </div>
      </div>

      <div v-else>
        <div v-for="user in searchResults" :key="user.id" class="flex flex-col">
          <UserCard 
            type="user"
            :user="user" 
            @follow="handleFollowUser"
            @login-required="handleLoginRequired"
          />
            </div>
          </div>

      <div v-if="!isLoading && searchResults.length === 0" class="flex flex-col justify-center items-center">
        <div>
          <EmptyStateIllustration variant="search" class="mt-10" />
        </div>
        <div class="empty-text">未搜索到相关结果</div>
      </div>

        <div v-if="searchResults.length > 0 && !hasMore" class="bottom-line">
          <div class="line"></div>
          <div class="text">fish 也是有底线的</div>
          <div class="line"></div>
      </div>
    </div>

    <NoteDetailModal 
      v-model:visible="showModal"
      :note="selectedNote"
      @interaction-change="handleNoteInteractionChange"
    />
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, watch, computed, inject } from 'vue'
import { useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'
import NoteCard from '@/components/note/NoteCard.vue'
import EmptyStateIllustration from '@/components/common/EmptyStateIllustration.vue'
import FilterIcon from '@/components/common/FilterIcon.vue'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import NoteDetailModal from '@/components/note/NoteDetailModal.vue'
import { onClickOutside } from '@vueuse/core'
import { searchNote, searchUser } from '@/api/search'
import { checkFollowingBatch, followUser, unfollowUser } from '@/api/relation'
import UserCard from '@/components/user/UserCard.vue'
import { message } from '@/utils/message'

const route = useRoute()
const userStore = useUserStore()
const showLoginModal = inject('showLoginModal')
const activeTab = ref('notes') // 默认选中笔记标签
const showModal = ref(false)
const selectedNote = ref(null)



const showFilter = ref(false)
const filterRef = ref(null)
const selectedTime = ref(null)
const selectedSort = ref(null)
const selectedType = ref(null)

const handleTabChange = (type) => {
  const nextActiveTab = type === 2 ? 'users' : 'others'
  const activeTabChanged = activeTab.value !== nextActiveTab

  selectedType.value = type
  activeTab.value = nextActiveTab

  // 图文与视频共享笔记结果视图，activeTab 不会变化，需在这里主动刷新。
  // 切换到/离开“用户”时交由 activeTab 的监听器刷新，避免重复请求。
  if (!activeTabChanged) {
    performSearch(true)
  }
}

const categories = [
  { name: '全部', type: null },
  { name: '图文', type: 0 },
  { name: '视频', type: 1 },
  { name: '用户', type: 2 },
]

const sortOptions = [
  { label: '综合', value: null },
  { label: '最新', value: 0 },
  { label: '最多点赞', value: 1 },
  { label: '最多评论', value: 2 },
  { label: '最多收藏', value: 3 }
]

const noteTypes = [
  { label: '不限', value: null },
  { label: '图文', value: 0 },
  { label: '视频', value: 1 },
]

const timeFilters = [
  { label: '不限', value: null },
  { label: '一天内', value: 0 },
  { label: '一周内', value: 1 },
  { label: '半年内', value: 2 }
]

onClickOutside(filterRef, () => {
  showFilter.value = false
})

const resetFilters = () => {
  selectedTime.value = null
  selectedSort.value = null
  selectedType.value = null
}

const applyFilters = () => {
  showFilter.value = false
  performSearch(true)
}

const keyword = computed(() => route.query.keyword || '')

const searchResults = ref([])
const isLoading = ref(false)
const hasMore = ref(true)
const currPageNo = ref(1)
let latestSearchId = 0

const normalizeSearchUser = (user) => ({
  ...user,
  id: user.userId,
  isLiked: false
})

const normalizeSearchNote = (note) => ({
  ...note,
  id: note.noteId ?? note.id,
  type: note.type ?? (note.videoUri ? 1 : 0),
  creatorId: note.creatorId ?? null,
  likeTotal: note.likeTotal ?? '0'
})

const appendSearchResults = (res, normalize) => {
  const newResults = (res.data || []).map(normalize)
  const existingIds = new Set(searchResults.value.map(item => item.id))
  const uniqueNewResults = newResults.filter(item => item.id != null && !existingIds.has(item.id))

  searchResults.value = [...searchResults.value, ...uniqueNewResults]
  currPageNo.value = Number(res.pageNo || currPageNo.value) + 1
  hasMore.value = Number(res.pageNo || 0) < Number(res.totalPage || 0)
}

const handleSearchFailure = (messageText) => {
  hasMore.value = false
  message.show(messageText || '搜索服务暂不可用，请稍后重试')
}

const performSearch = async (isFirstPage = true) => {
  if (!isFirstPage && isLoading.value) return

  const searchId = ++latestSearchId
  const searchTab = activeTab.value
  const searchKeyword = keyword.value
  const searchType = selectedType.value
  const searchSort = selectedSort.value
  const searchTime = selectedTime.value

  isLoading.value = true
  
  if (isFirstPage) {
    searchResults.value = []
    currPageNo.value = 1
    hasMore.value = true
  }
  
  const requestPageNo = currPageNo.value

  try {
    const res = searchTab === 'users'
      ? await searchUser(searchKeyword, requestPageNo)
      : await searchNote(searchKeyword, searchType, searchSort, searchTime, requestPageNo)

    if (searchId !== latestSearchId) return
    if (!res.success) {
      handleSearchFailure(res.message)
      return
    }

    if (searchTab === 'users' && userStore.isLoggedIn && (res.data || []).length > 0) {
      const targetUserIds = res.data.map(user => user.userId).filter(Boolean)
      const followingRes = await checkFollowingBatch(targetUserIds)
      if (searchId !== latestSearchId) return
      if (followingRes.success) {
        const followedIds = new Set((followingRes.data || []).map(String))
        res.data = res.data.map(user => ({
          ...user,
          isFollowed: followedIds.has(String(user.userId))
        }))
      }
    }

    appendSearchResults(res, searchTab === 'users' ? normalizeSearchUser : normalizeSearchNote)
  } catch {
    if (searchId === latestSearchId) handleSearchFailure()
  } finally {
    if (searchId === latestSearchId) isLoading.value = false
  }
}

watch(() => route.query.keyword, (newKeyword, oldKeyword) => {
  if (newKeyword !== oldKeyword) {
    performSearch(true) // 重置并执行新搜索
  }
}, { immediate: true })

watch(activeTab, () => {
  performSearch(true)
})

const loadMore = () => {
  if (!hasMore.value || isLoading.value) return
  performSearch(false)
}

const handleScroll = () => {
  if (isLoading.value || !hasMore.value) return
  
  const scrollTop = window.pageYOffset || document.documentElement.scrollTop
  const windowHeight = window.innerHeight
  const documentHeight = document.documentElement.scrollHeight
  
  if (documentHeight - scrollTop - windowHeight < 200) {
    loadMore()
  }
}

onMounted(() => {
  window.addEventListener('scroll', handleScroll)
})

onUnmounted(() => {
  window.removeEventListener('scroll', handleScroll)
})


const onNoteClick = (note) => {
  selectedNote.value = note
  showModal.value = true
}

const handleNoteInteractionChange = ({ noteId, likeTotal, collectTotal, isLiked, isCollected }) => {
  const selectedNoteId = selectedNote.value?.id ?? selectedNote.value?.noteId
  if (selectedNote.value && String(selectedNoteId) === String(noteId)) {
    Object.assign(selectedNote.value, { likeTotal, collectTotal, isLiked, isCollected })
  }
  const note = searchResults.value.find(item => String(item.id ?? item.noteId) === String(noteId))
  if (note) Object.assign(note, { likeTotal, collectTotal, isLiked, isCollected })
}


const handleCardLikeChange = ({ noteId, isLiked, likeTotal }) => {
  const note = searchResults.value.find(item => String(item.id ?? item.noteId) === String(noteId))
  if (note) Object.assign(note, { isLiked, likeTotal })
}

const handleFollowUser = (userId) => {
  const user = searchResults.value.find(item => String(item.userId ?? item.id) === String(userId))
  if (!user) return

  const isFollowed = Boolean(user.isFollowed)
  const request = isFollowed ? unfollowUser(userId) : followUser(userId)
  request.then(res => {
    if (!res.success) {
      message.error(res.message || (isFollowed ? '取消关注失败' : '关注失败'))
      return
    }
    user.isFollowed = !isFollowed
    message.success(isFollowed ? '已取消关注' : '关注成功')
  }).catch(error => {
    console.error(isFollowed ? '取消关注失败:' : '关注失败:', error)
    message.error(isFollowed ? '取消关注失败' : '关注失败')
  })
}

const handleLoginRequired = () => {
  if (showLoginModal) showLoginModal.value = true
}
</script>

<style scoped>
.masonry-container {
  columns: 5;
  column-gap: 16px;
}

.masonry-item {
  break-inside: avoid;
  margin-bottom: 16px;
}

@media (max-width: 1800px) {
  .masonry-container {
    columns: 5;
  }
}

@media (max-width: 1400px) {
  .masonry-container {
    columns: 4;
  }
}

@media (max-width: 1100px) {
  .masonry-container {
    columns: 3;
  }
}

@media (max-width: 767px) {
  .search-result-tabs { padding: 0 12px; overflow-x: auto; }
  .search-result-tabs nav { min-width: max-content; }
  .search-result-tabs li a { display: inline-flex; align-items: center; padding-inline: 12px; }
  .filter-panel {
    position: fixed;
    top: 76px;
    right: 12px;
    left: 12px;
    width: auto;
    max-height: calc(100vh - 156px);
    overflow-y: auto;
  }
  .search-result-page > .container { margin-top: 16px; padding: 0 12px; }
  .masonry-container { columns: 2; column-gap: 12px; }
  .masonry-item { margin-bottom: 12px; }
  .bottom-line { padding-bottom: 24px; }
}

.empty-text {
  font-size: 14px;
  line-height: 18px;
  text-align: center;
  color: var(--color-tertiary-label);
  margin-top: 16px;
}

/* 底线样式 */
.bottom-line {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px 0 40px;
  color: var(--color-tertiary-label);
  font-size: 14px;
}

.bottom-line .line {
  height: 1px;
  width: 80px;
  background-color: var(--color-border);
}

.bottom-line .text {
  padding: 0 16px;
}
</style> 
