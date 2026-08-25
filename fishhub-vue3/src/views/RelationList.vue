<template>
  <div class="min-h-screen bg-white max-w-3xl mx-auto">
    <div class="border-b border-gray-100">
      <div class="flex items-center my-2 h-[40px]">
        <nav class="grow">
          <ul class="flex items-center">
            <li>
              <a 
              @click.prevent="activeTab = 'following'"
                href="#" 
                class="px-4 py-2 rounded-full transition-colors h-[40px] hover:bg-[var(--color-active-background)]"
                :class="activeTab === 'following' ? 'bg-[var(--color-surface-muted)] text-[var(--color-primary-label)] font-bold' : 'text-[var(--color-secondary-label)] hover:text-[var(--color-primary-label)]'"
              >
              关注
              </a>
            </li>
            <li>
              <a 
              @click.prevent="activeTab = 'followers'"
                href="#" 
                class="px-4 py-2 rounded-full transition-colors h-[40px] hover:bg-[var(--color-active-background)]"
                :class="activeTab === 'followers' ? 'bg-[var(--color-surface-muted)] text-[var(--color-primary-label)] font-bold' : 'text-[var(--color-secondary-label)] hover:text-[var(--color-primary-label)]'"
              >
              粉丝
              </a>
            </li>
          </ul>
        </nav>
      </div>
    </div>
    
    <div class="container mx-auto py-4">
      <LoadingSpinner :active="loading && !loadingMore" />
      
      <div v-if="shouldShowEmptyState" class="text-center py-10 text-gray-500 flex flex-col items-center ">
          <EmptyStateIllustration variant="relation" class="mt-10" />
        <div class="empty-text">{{ activeTab === 'following' ? '暂未关注其他用户' : '暂无粉丝' }}</div>
      </div>
      
      <div v-else-if="!loading">
        <div v-for="user in users" :key="user.userId" class="mb-2">
          <UserCard :user="user" @follow="handleFollowUser" @login-required="handleLoginRequired" :type="listType" />
        </div>
        
        <div v-if="hasMore" class="text-center py-4">
          <button 
            @click="loadMore" 
            class="text-gray-500 hover:text-gray-700"
            :disabled="loadingMore"
          >
            {{ loadingMore ? '加载中...' : '加载更多' }}
          </button>
        </div>
        
                  <div v-if="users.length > 0 && !hasMore" class="bottom-line">
                    <div class="line"></div>
                    <div class="text">fish 也是有底线的</div>
                    <div class="line"></div>
                  </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch, onUnmounted, inject } from 'vue'
import { useRoute } from 'vue-router'
import UserCard from '@/components/user/UserCard.vue'
import { getFollowingList, getFansList, followUser, unfollowUser } from '@/api/relation'
import { message } from '@/utils/message'
import EmptyStateIllustration from '@/components/common/EmptyStateIllustration.vue'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import { useUserStore } from '@/stores/user'
import { useLatestRequest } from '@/composables/useLatestRequest'
import { shouldShowEmptyRelationState } from '@/utils/relation'

const route = useRoute()
const userStore = useUserStore()
const showLoginModal = inject('showLoginModal')
const userId = ref(route.params.userId)
const activeTab = ref(route.query.tab || 'following')
const users = ref([])
const loading = ref(true)
const loadingMore = ref(false)
const hasMore = ref(false)
const nextCursor = ref(0)
const isLoading = ref(false)
const { begin: beginRequest, isCurrent: isCurrentRequest } = useLatestRequest()

const listType = computed(() => activeTab.value === 'following' ? 'following' : 'fans')

const shouldShowEmptyState = computed(() => shouldShowEmptyRelationState({
  loading: loading.value,
  userCount: users.value.length,
  hasMore: hasMore.value
}))

const loadRelationList = async (isLoadMore = false) => {
  if (isLoadMore && isLoading.value) return
  const requestId = beginRequest()
  const requestUserId = userId.value
  const requestTab = activeTab.value
  const requestCursor = isLoadMore ? nextCursor.value : 0
  isLoading.value = true
  
  if (isLoadMore) {
    loadingMore.value = true
  } else {
    loading.value = true
    users.value = []
    nextCursor.value = requestCursor
    hasMore.value = false
  }
  
  try {
    const request = requestTab === 'following' ? getFollowingList : getFansList
    const res = await request(requestUserId, requestCursor)
    if (!isCurrentRequest(requestId)) return
    if (res.success) {
      const newUsers = (res.data || []).map(user => ({
        ...user,
        isFollowed: Boolean(user.isFollowed)
      }))
      
      if (isLoadMore) {
        users.value = [...users.value, ...newUsers]
      } else {
        users.value = newUsers
      }
      
      nextCursor.value = res.nextCursor
      hasMore.value = res.nextCursor !== null && res.nextCursor !== undefined
    }
  } catch {
    if (isCurrentRequest(requestId)) message.show('列表加载失败，请稍后重试')
  } finally {
    if (!isCurrentRequest(requestId)) return
    loading.value = false
    loadingMore.value = false
    isLoading.value = false
  }
}

const loadMore = () => {
  loadRelationList(true)
}

const handleFollowUser = (followUserId) => {
  if (!userStore.isLoggedIn) {
    showLoginModal.value = true
    return
  }
  const userIndex = users.value.findIndex(user => user.userId === followUserId)
  if (userIndex === -1) return

  const user = users.value[userIndex]
  const isFollowing = user.isFollowed
  const request = isFollowing ? unfollowUser(followUserId) : followUser(followUserId)

  request.then(res => {
    if (!res.success) {
      message.show(res.message)
      return
    }

    if (activeTab.value === 'following' && isFollowing) {
      users.value.splice(userIndex, 1)
      message.show('取消关注成功')
      return
    }

    user.isFollowed = !isFollowing
    message.show(user.isFollowed ? '关注成功' : '取消关注成功')
  })
}

const handleLoginRequired = () => {
  showLoginModal.value = true
}

const handleScroll = () => {
  if (loading.value || loadingMore.value || !hasMore.value) return
  
  const scrollTop = window.pageYOffset || document.documentElement.scrollTop
  const windowHeight = window.innerHeight
  const documentHeight = document.documentElement.scrollHeight
  
  if (documentHeight - scrollTop - windowHeight < 200) {
    loadMore()
  }
}

watch(activeTab, () => {
  loadRelationList()
})

watch(() => route.params.userId, (newUserId) => {
  userId.value = newUserId
  loadRelationList()
})

onMounted(() => {
  window.addEventListener('scroll', handleScroll)
  
  loadRelationList()
})

onUnmounted(() => {
  window.removeEventListener('scroll', handleScroll)
})
</script>

<style scoped>
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

.empty-text {
  font-size: 14px;
  line-height: 18px;
  text-align: center;
  color: var(--color-tertiary-label);
  margin-top: 16px;
}

@media (max-width: 767px) {
  .min-h-screen { min-height: auto; }
  .container { padding-inline: 16px; }
  .bottom-line { padding-bottom: 24px; }
}
</style>
