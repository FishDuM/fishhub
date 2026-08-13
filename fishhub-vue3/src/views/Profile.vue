<template>
  <div class="profile-page min-h-screen bg-white">
    <div class="profile-card flex items-center justify-center">
      <div class="bg-white rounded-lg p-6">
          <div class="profile-summary flex items-start">
          <div class="avatar-wrapper">
            <UserAvatar
              :src="profile.avatar"
              :alt="`${profile.nickname || '用户'}的头像`"
              class="user-image"
            />
              
          </div>

          <div class="info">
            <div>
              <div>
                <div class="flex">
                  <h1 class="user-nickname">{{ profile.nickname }}</h1>
                  <div class="flex items-center gap-3">
                    <button 
                    v-if="!userStore.token || userStore.profile.userId !== profile.userId"
                    @click="handleFollow"
                    :class="isFollowing ? 'border border-gray-300 text-gray-600 bg-white' : 'bg-[var(--color-primary)] text-[var(--color-primary-contrast)]'"
                    class="rounded-full font-bold w-[96px] h-[40px] cursor-pointer">
                      {{ isFollowing ? '已关注' : '关注' }}
                    </button>
                    <div class="relative">
                      <button 
                        class="w-9 h-9 cursor-pointer border border-gray-200 hover:bg-gray-50 rounded-full flex items-center justify-center"
                        @click="toggleDropdown"
                        ref="dropdownTrigger"
                      >
                        <svg class="w-5 h-5 text-gray-600" viewBox="0 0 24 24" fill="none" stroke="currentColor">
                          <path
                            d="M12 5v.01M12 12v.01M12 19v.01M12 6a1 1 0 110-2 1 1 0 010 2zm0 7a1 1 0 110-2 1 1 0 010 2zm0 7a1 1 0 110-2 1 1 0 010 2z" />
                        </svg>
                        
                      </button>

                      <Transition
                        enter-active-class="transition duration-100 ease-out"
                        enter-from-class="transform scale-95 opacity-0"
                        enter-to-class="transform scale-100 opacity-100"
                        leave-active-class="transition duration-75 ease-in"
                        leave-from-class="transform scale-100 opacity-100"
                        leave-to-class="transform scale-95 opacity-0"
                      >
                        <div 
                          v-if="showDropdown"
                          class="absolute right-0 mt-2 w-[140px] bg-white rounded-lg shadow-lg p-1 z-50 border border-gray-100"
                        >
                          <button 
                            v-if="!userStore.token || userStore.profile.userId === profile.userId"
                            class="w-full px-4 py-2 text-left text-gray-600 hover:text-gray-800 
                            hover:bg-gray-50 flex items-center rounded-lg cursor-pointer"
                            @click="editProfile"
                          >
                            <ProfileActionIcon type="edit" class="w-5 h-5 mr-2 text-[var(--color-secondary-label)]" />
                            编辑资料
                          </button>
                          <button 
                            class=" cursor-not-allowed w-full px-4 py-2 text-left text-gray-600 hover:text-gray-800 
                            hover:bg-gray-50 flex items-center rounded-lg"
                          >
                          <ProfileActionIcon type="report" class="w-4 h-4 mr-2.5 ml-[1px] text-[var(--color-tertiary-label)]" />
                            举报
                          </button>
                        </div>
                      </Transition>
                    </div>
                  </div>
                  
                </div>


              </div>

              <div class="user-content">
                    <span>飞鱼社区号：{{ profile.fishhubId }}</span>
                    <span class="mx-2">|</span>
                    <span>IP属地：中国</span>
                  </div>

              <div class="user-desc">
                {{ profile.introduction || '此用户还未填写简介'}}
              </div>

              <div class="flex items-center mt-[16px] text-[12px]">
                <span v-if="profile.age != null" class="user-tag">
                  <GenderIcon :sex="profile.sex" class="w-3 h-3 text-[var(--color-secondary-label)]" />
                  {{ profile.age }}岁
                </span>
                <span class="user-tag">中国</span>
              </div>

              <div class="flex items-center gap-8 mt-[20px]">
                <router-link :to="`/user/${profile.userId}/relation?tab=following`" class="flex items-center cursor-pointer">
                  <div class="text-[14px] font-medium text-[var(--color-primary-label)] mr-[4px]">{{ profile.followingTotal || 0 }}</div>
                  <div class="text-sm text-[var(--color-tertiary-label)]">关注</div>
                </router-link>
                <router-link :to="`/user/${profile.userId}/relation?tab=followers`" class="flex items-center cursor-pointer">
                  <div class="text-[14px] font-medium text-[var(--color-primary-label)] mr-[4px]">{{ profile.fansTotal || 0}}</div>
                  <div class="text-sm text-[var(--color-tertiary-label)]">粉丝</div>
                </router-link>
                <div class="flex items-center">
                  <div class="text-[14px] font-medium text-[var(--color-primary-label)] mr-[4px]">{{ profile.likeAndCollectTotal || 0 }}</div>
                  <div class="text-sm text-[var(--color-tertiary-label)]">获赞与收藏</div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

          <div class="mt-[48px]">
        <TabNav
          v-model="activeTab"
          :tabs="[
            { key: 'notes', label: '笔记' },
            { key: 'collect', label: '收藏' },
            { key: 'like', label: '赞过' }
          ]"
        />
        
      <div class="masonry-container mt-[16px]" v-if="activeTab === 'notes'">
            <div v-if="notes.length > 0" class="masonry-grid">
              <div v-for="colIndex in columnCount" :key="colIndex" class="masonry-column">
                <div 
                  v-for="note in getColumnNotes(colIndex-1)" 
                  :key="note.id" 
                  class="masonry-item"
                >
                  <NoteCard 
                    :note="note" 
                    @click="onNoteClick"
                    @like-change="handleCardLikeChange"
                  />
                </div>
              </div>
            </div>
            <div v-else class="flex flex-col items-center justify-center mt-[16px]">
                <div class="divider"></div>
                <div>
          <EmptyStateIllustration variant="profile" class="mt-10" />
                  </div>
                  <div class="empty-text">该用户暂未发布笔记</div>
          </div>
        </div>

        


        <div v-else-if="activeTab === 'collect'" class="masonry-container mt-[16px]">
          <div v-if="notes.length > 0" class="masonry-grid">
                <div v-for="colIndex in columnCount" :key="colIndex" class="masonry-column">
                  <div 
                    v-for="note in getColumnNotes(colIndex-1)" 
                    :key="note.id" 
                    class="masonry-item"
                  >
                    <NoteCard 
                      :note="note" 
                      @click="onNoteClick"
                      @like-change="handleCardLikeChange"
                    />
                  </div>
                </div>
              </div>
              <div v-else class="flex flex-col items-center justify-center mt-[16px]">
                  <div class="divider"></div>
                  <div class="mt-5">
                    <EmptyStateIllustration variant="content" class="mt-10" />
                  </div>
                  <div class="empty-text">该用户暂未收藏笔记</div>
              </div>
        </div>

        <div v-else class="masonry-container mt-[16px]">
          <div v-if="notes.length > 0" class="masonry-grid">
                <div v-for="colIndex in columnCount" :key="colIndex" class="masonry-column">
                  <div 
                    v-for="note in getColumnNotes(colIndex-1)" 
                    :key="note.id" 
                    class="masonry-item"
                  >
                    <NoteCard 
                      :note="note" 
                      @click="onNoteClick"
                      @like-change="handleCardLikeChange"
                    />
                  </div>
                </div>
              </div>
              <div v-else class="flex flex-col items-center justify-center mt-[16px]">
                  <div class="divider"></div>
                  <div class="mt-5">
                    <EmptyStateIllustration variant="content" class="mt-10" />
                  </div>
                  <div class="empty-text">该用户暂未点赞笔记</div>
              </div>
        </div>
      </div>

    <div v-if="notes.length > 0 && !hasMore" class="bottom-line">
      <div class="line"></div>
      <div class="text">fish 也是有底线的</div>
      <div class="line"></div>
      </div>

          <NoteDetailModal 
            v-model:visible="showModal"
            :note="selectedNote"
            @interaction-change="handleNoteInteractionChange"
          />

          <EditProfileModal 
            v-model:visible="showEditModal" 
            @update-success="handleProfileUpdated"
          />
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, computed, watch, inject } from 'vue'
import TabNav from '@/components/common/TabNav.vue'
import NoteCard from '@/components/note/NoteCard.vue'
import EmptyStateIllustration from '@/components/common/EmptyStateIllustration.vue'
import GenderIcon from '@/components/common/GenderIcon.vue'
import UserAvatar from '@/components/common/UserAvatar.vue'
import { useUserStore } from '@/stores/user'
import { getUserProfile } from '@/api/user'
import EditProfileModal from '@/components/profile/EditProfileModal.vue'
import ProfileActionIcon from '@/components/profile/ProfileActionIcon.vue'
import { getPublishedNoteList, getCollectedNoteList, getLikedNoteList } from '@/api/note'
import { useRoute } from 'vue-router'
import NoteDetailModal from '@/components/note/NoteDetailModal.vue'
import { followUser, unfollowUser, checkFollowing } from '@/api/relation'
import { message } from '@/utils/message'

const userStore = useUserStore()
const route = useRoute()

const activeTab = ref('notes')

const notes = ref([])
const nextCursor = ref(null)


const showModal = ref(false)
const selectedNote = ref(null)

const onNoteClick = (note) => {
  selectedNote.value = note
  showModal.value = true
}

const handleNoteInteractionChange = ({ noteId, likeTotal, collectTotal, isLiked, isCollected }) => {
  const selectedNoteId = selectedNote.value?.id ?? selectedNote.value?.noteId
  if (String(selectedNoteId) !== String(noteId)) return

  Object.assign(selectedNote.value, { likeTotal, collectTotal, isLiked, isCollected })
}

const handleCardLikeChange = ({ noteId, isLiked, likeTotal }) => {
  const note = notes.value.find(item => String(item.id ?? item.noteId) === String(noteId))
  if (note) Object.assign(note, { isLiked, likeTotal })
}

const showDropdown = ref(false)
const dropdownTrigger = ref(null)

const toggleDropdown = () => {
  showDropdown.value = !showDropdown.value
}

const showEditModal = ref(false)

const isLoggedIn = computed(() => !!userStore.token)
const showLoginModal = inject('showLoginModal')

const editProfile = () => {
  if (!isLoggedIn.value) {
    showLoginModal.value = true
    return
  }

  showDropdown.value = false
  showEditModal.value = true
}

const handleClickOutside = (event) => {
  if (dropdownTrigger.value && !dropdownTrigger.value.contains(event.target)) {
    showDropdown.value = false
  }
}

const profile = ref({})
const isFollowing = ref(false)

const loadFollowingState = async (targetUserId) => {
  if (!isLoggedIn.value || !targetUserId || String(targetUserId) === String(userStore.profile.userId)) {
    isFollowing.value = false
    return
  }
  try {
    const res = await checkFollowing(targetUserId)
    if (res.success) isFollowing.value = Boolean(res.data)
  } catch (error) {
    console.error('查询关注状态失败:', error)
  }
}

onMounted(() => {
  document.addEventListener('click', handleClickOutside)

  window.addEventListener('scroll', handleScroll)
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  document.removeEventListener('click', handleClickOutside)
  window.removeEventListener('scroll', handleScroll)
  window.removeEventListener('resize', handleResize)
})

const getColumnCount = () => {
  const width = window.innerWidth
  if (width <= 767) return 2
  if (width <= 1023) return 2
  if (width <= 1100) return 3
  if (width <= 1400) return 4
  return 5
}

const columnCount = ref(getColumnCount())

const handleResize = () => {
  columnCount.value = getColumnCount()
}

const hasMore = ref(true) // 是否有更多数据
const isLoading = ref(false) // 是否正在加载数据

const getColumnNotes = (colIndex) => {
  return notes.value.filter((_, index) => index % columnCount.value === colIndex)
}


const getCurrentNoteList = () => {
  if (activeTab.value === 'collect') return getCollectedNoteList
  if (activeTab.value === 'like') return getLikedNoteList
  return getPublishedNoteList
}

const normalizeNote = (note) => ({ ...note, id: note.noteId ?? note.id })

const loadNotes = async (isFirstPage = true) => {
  if (isLoading.value || !profile.value.userId) return
  isLoading.value = true
  if (isFirstPage) {
    notes.value = []
    nextCursor.value = null
    hasMore.value = true
  }
  try {
    const res = await getCurrentNoteList()(profile.value.userId, nextCursor.value)
    if (!res.success) {
      hasMore.value = false
      return
    }
    const newNotes = (res.data?.notes || []).map(normalizeNote)
    const existingIds = new Set(isFirstPage ? [] : notes.value.map(note => note.id))
    const uniqueNotes = newNotes.filter(note => !existingIds.has(note.id))
    notes.value = isFirstPage ? uniqueNotes : [...notes.value, ...uniqueNotes]
    nextCursor.value = res.data?.nextCursor ?? null
    hasMore.value = Boolean(nextCursor.value && newNotes.length)
  } finally {
    isLoading.value = false
  }
}

const loadMoreNotes = () => loadNotes(false)

const handleScroll = () => {
  if (isLoading.value || !hasMore.value) return
  
  const scrollTop = window.pageYOffset || document.documentElement.scrollTop
  const windowHeight = window.innerHeight
  const documentHeight = document.documentElement.scrollHeight
  
  if (documentHeight - scrollTop - windowHeight < 200) {
    if (!isLoading.value) {
      loadMoreNotes()
    }
  }
}

watch(activeTab, () => loadNotes(true))

const handleProfileUpdated = (updatedProfile) => {
  profile.value = { ...profile.value, ...updatedProfile }
  
  getUserProfile(route.params.userId).then(res => {
    if (res.success) {
      profile.value = res.data
    }
  })
}

const handleFollow = async () => {
  if (!isLoggedIn.value) {
    showLoginModal.value = true
    return
  }
  const wasFollowing = isFollowing.value
  try {
    const res = await (wasFollowing
      ? unfollowUser(profile.value.userId)
      : followUser(profile.value.userId))
    if (!res.success) {
      message.show(res.message)
      return
    }
    isFollowing.value = !wasFollowing
    message.show(wasFollowing ? '已取消关注' : '关注成功')
  } catch (error) {
    console.error(wasFollowing ? '取消关注失败:' : '关注失败:', error)
    message.show(wasFollowing ? '取消关注失败' : '关注失败')
  }
}

watch(() => route.params.userId, (newUserId, oldUserId) => {
  if (newUserId !== oldUserId) {
    nextCursor.value = null
    notes.value = []
    hasMore.value = true
    isLoading.value = false
    
    activeTab.value = 'notes'
    
    getUserProfile(newUserId).then(res => {
      if (res.success) {
        profile.value = res.data
        loadFollowingState(profile.value.userId)

        loadNotes(true)
      }
    })
  }
}, { immediate: true })
</script>

<style scoped>
.user-image {
  border-radius: 50%;
  margin: 0 auto;
  width: 70%;
  height: 100%;
  object-fit: cover;
}

.avatar-wrapper {
    text-align: center;
    width: calc((1728px - 7* 32px) / 6* 1);
    height: calc(0.7* calc((1728px - 7* 32px) / 6* 1));
}

@media screen and (min-width: 1424px) and (max-width: 1727px) {
    .avatar-wrapper {
        width: calc((100vw - 7* 32px) / 6* 1);
        height: calc(0.7* calc((100vw - 7* 32px) / 6* 1));
    }
}

.user-nickname {
    font-weight: 600;
    font-size: 24px;
    line-height: 120%;
    color: var(--color-primary-label);
    word-wrap: break-word;
    width: 100%;
}

.user-content {
    width: 100%;
    font-size: 12px;
    line-height: 120%;
    color: var(--color-tertiary-label);
    display: flex
;
}

.info {
    margin-left: 32px;
    width: calc(32px + calc((100vw - 7* 32px) / 6* 2));
}

.user-desc {
    width: 100%;
    font-size: 14px;
    line-height: 140%;
    color: var(--color-primary-label);
    margin-top: 16px;
    white-space: pre-line;
}

.user-tag {
    display: flex;
    align-items: center;
    justify-content: center;
    padding: 4px 8px;
    gap: 4px;
    height: 18px;
    border-radius: 41px;
    background: var(--color-active-background);
    height: 24px;
    line-height: 24px;
    margin-right: 6px;
    color: var(--color-tertiary-label);
}

/* 使用固定列布局的瀑布流 */
.masonry-grid {
  display: flex;
  width: 100%;
  padding: 0 1px;
  position: relative;
  z-index: 1;
}

.masonry-column {
  flex: 1;
  padding: 0 8px;
}

.masonry-item {
  margin-bottom: 16px;
}

/* 响应式布局 */
@media (max-width: 1100px) {
  .masonry-grid {
    display: grid;
    grid-template-columns: repeat(3, 1fr);
    gap: 16px;
  }
  
  .masonry-column {
    padding: 0;
  }
}

@media (min-width: 1101px) and (max-width: 1400px) {
  .masonry-grid {
    display: grid;
    grid-template-columns: repeat(4, 1fr);
    gap: 16px;
  }
  
  .masonry-column {
    padding: 0;
  }
}

@media (min-width: 1401px) {
  .masonry-grid {
    display: grid;
    grid-template-columns: repeat(5, 1fr);
    gap: 16px;
  }
  
  .masonry-column {
    padding: 0;
  }
}

/* 动画效果 */
.masonry-grid {
  opacity: 0;
  animation: fadeIn 0.3s ease-in-out forwards;
}

@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
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

.empty-text {
  font-size: 14px;
  line-height: 18px;
  text-align: center;
  color: var(--color-tertiary-label);
  margin-top: 16px;
}

.divider {
    width: 100%;
    height: 1px;
    background: var(--color-border);
}

@media (max-width: 767px) {
  .profile-page { min-height: auto; padding-bottom: 8px; }
  .profile-card { display: block; }
  .profile-card > div { padding: 20px 16px; }
  .profile-summary { flex-direction: column; }
  .avatar-wrapper { width: 88px; height: 88px; }
  .user-image { width: 100%; }
  .info { width: 100%; margin: 16px 0 0; }
  .user-nickname { font-size: 21px; }
  .user-content { flex-wrap: wrap; gap: 4px; }
  .user-desc { margin-top: 12px; }
  .masonry-container { margin-top: 12px !important; }
  .masonry-grid { display: flex; gap: 12px; padding: 0 12px; }
  .masonry-column { min-width: 0; padding: 0; }
  .masonry-item { margin-bottom: 12px; }
  .bottom-line { padding-bottom: 24px; }
}
</style>
