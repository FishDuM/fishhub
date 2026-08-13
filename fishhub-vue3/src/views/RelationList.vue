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
      
      <div v-if="!loading && users.length === 0" class="text-center py-10 text-gray-500 flex flex-col items-center ">
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
        
                  <div v-if="users.length > 0" class="bottom-line">
                    <div class="line"></div>
                    <div class="text">fish 也是有底线的</div>
                    <div class="line"></div>
                  </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, watch, onUnmounted, inject } from 'vue'
import { useRoute } from 'vue-router'
import UserCard from '@/components/user/UserCard.vue'
import { getFollowingList, getFansList, followUser, unfollowUser } from '@/api/relation'
import { message } from '@/utils/message'
import EmptyStateIllustration from '@/components/common/EmptyStateIllustration.vue'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const userStore = useUserStore()
const showLoginModal = inject('showLoginModal')
const userId = ref(route.params.userId)
const activeTab = ref(route.query.tab || 'following')
const users = ref([])
const loading = ref(true)
const loadingMore = ref(false)
const hasMore = ref(true)
const pageNo = ref(1)
const listType = ref('following')

const fetchFollowingList = async (isLoadMore = false) => {
  if (isLoading.value) return
  isLoading.value = true
  
  if (isLoadMore) {
    loadingMore.value = true
  } else {
    loading.value = true
    users.value = []
    pageNo.value = 1
  }
  
  try {
    const res = await getFollowingList(userId.value, pageNo.value)
    if (res.success) {
      const newUsers = res.data || []
      
      // 后端明确返回关注状态；关注列表中的用户始终是已关注状态。
      const processedUsers = newUsers.map(user => ({
        ...user,
        isFollowed: Boolean(user.isFollowed)
      }))
      
      if (isLoadMore) {
        users.value = [...users.value, ...processedUsers]
      } else {
        users.value = processedUsers
      }
      
      hasMore.value = res.pageNo < res.totalPage
      pageNo.value = res.pageNo + 1
    }
  } catch (error) {
    console.error('获取关注列表失败', error)
  } finally {
    loading.value = false
    loadingMore.value = false
    isLoading.value = false
  }
}

const fetchFollowersList = async (isLoadMore = false) => {
  if (isLoading.value) return
  isLoading.value = true
  
  if (isLoadMore) {
    loadingMore.value = true
  } else {
    loading.value = true
    users.value = []
    pageNo.value = 1
  }
  
  try {
    const res = await getFansList(userId.value, pageNo.value)
    if (res.success) {
      const newUsers = res.data || []
      
      // 粉丝是否被当前用户关注由后端计算，游客固定为 false。
      const processedUsers = newUsers.map(user => ({
        ...user,
        isFollowed: Boolean(user.isFollowed)
      }))
      
      if (isLoadMore) {
        users.value = [...users.value, ...processedUsers]
      } else {
        users.value = processedUsers
      }
      
      hasMore.value = res.pageNo < res.totalPage
      pageNo.value = res.pageNo + 1
    }
  } catch (error) {
    console.error('获取粉丝列表失败', error)
  } finally {
    loading.value = false
    loadingMore.value = false
    isLoading.value = false
  }
}

const loadMore = () => {
  if (activeTab.value === 'following') {
    fetchFollowingList(true)
  } else {
    fetchFollowersList(true)
  }
}

const handleFollowUser = (followUserId) => {
  if (!userStore.token) {
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

const isLoading = ref(false)

const handleScroll = () => {
  if (loading.value || loadingMore.value || !hasMore.value) return
  
  const scrollTop = window.pageYOffset || document.documentElement.scrollTop
  const windowHeight = window.innerHeight
  const documentHeight = document.documentElement.scrollHeight
  
  if (documentHeight - scrollTop - windowHeight < 200) {
    loadMore()
  }
}

watch(activeTab, (newTab) => {
  if (newTab === 'following') {
    listType.value = 'following'
    fetchFollowingList()
  } else {
    listType.value = 'fans'
    fetchFollowersList()
  }
})

onMounted(() => {
  window.addEventListener('scroll', handleScroll)
  
  if (activeTab.value === 'following') {
    fetchFollowingList()
  } else {
    fetchFollowersList()
  }
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
