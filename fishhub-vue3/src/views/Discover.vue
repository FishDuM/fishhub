<template>
  <div class="discover-page">

    <CategoryNav
      :active-channel-id="activeChannelId"
      @channel-change="handleChannelChange"
    />

    <LoadingSpinner ref="loadingRef"/>


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
    <div v-else-if="hasLoaded" class="empty-state">
        <div>
        <EmptyStateIllustration variant="content" class="mt-10" />
        </div>
        <div class="empty-text">{{ loadError ? '笔记加载失败，请稍后重试' : '该频道下暂无笔记' }}</div>
        <button v-if="loadError" class="retry-button" @click="loadNotes(activeChannelId, true)">重新加载</button>
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
  </div>
</template>

<script setup>
import NoteCard from '@/components/note/NoteCard.vue'
import NoteDetailModal from '@/components/note/NoteDetailModal.vue'
import CategoryNav from '@/components/layout/CategoryNav.vue'
import EmptyStateIllustration from '@/components/common/EmptyStateIllustration.vue'
import { ref, onMounted, watch, onBeforeUnmount } from 'vue'
import { getDiscoverNotePageList } from '@/api/note'
import { useRoute, useRouter } from 'vue-router'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'


const route = useRoute()
const router = useRouter()

const notes = ref([])
const currPageNo = ref(1)
const hasMore = ref(true)
const isLoading = ref(false)
const hasLoaded = ref(false)
const loadError = ref(false)


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

// 各列独立布局，按索引分配可避免 CSS columns 打乱卡片阅读顺序。
const getColumnNotes = (colIndex) => {
  return notes.value.filter((_, index) => index % columnCount.value === colIndex)
}

const loadingRef = ref(null)

const activeChannelId = ref(0)

const showModal = ref(false)
const selectedNote = ref(null)

const getChannelIdFromRoute = () => {
  const channelId = route.query.channelId
  return channelId ? parseInt(channelId) : 0
}

const loadNotes = (channelId = 0, isFirstPage = true) => {
  if (isLoading.value) return

  isLoading.value = true

  if (isFirstPage) {
    loadingRef.value?.show()
    currPageNo.value = 1
    notes.value = []
    loadError.value = false
  }

  getDiscoverNotePageList(channelId, currPageNo.value).then(res => {
    if (res.success) {
      const newNotes = (res.data || []).map(note => ({ ...note, id: note.noteId ?? note.id }))

      if (isFirstPage) {
        notes.value = newNotes
      } else {
        notes.value = [...notes.value, ...newNotes]
      }

      hasMore.value = res.pageNo < res.totalPage

      if (newNotes.length > 0) {
        currPageNo.value++
      }
    }
  }).catch(() => {
    if (isFirstPage) {
      loadError.value = true
    }
  }).finally(() => {
    isLoading.value = false
    hasLoaded.value = true
    if (isFirstPage) {
      loadingRef.value?.hide()
    }
  })
}

const loadMoreNotes = () => {
  if (!hasMore.value || isLoading.value) return
  loadNotes(activeChannelId.value, false)
}

const handleChannelChange = (channelId) => {
  activeChannelId.value = channelId

  updateRouteQuery(channelId)

  loadNotes(channelId, true)
}

const updateRouteQuery = (channelId) => {
  const query = channelId === 0 ? {} : { channelId }

  // 频道切换属于同页筛选，不应为每次点击新增浏览器历史记录。
  router.replace({
    path: route.path,
    query
  })
}

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

const handleScroll = () => {
  const scrollTop = document.documentElement.scrollTop || document.body.scrollTop
  const scrollHeight = document.documentElement.scrollHeight || document.body.scrollHeight
  const clientHeight = document.documentElement.clientHeight || window.innerHeight

  if (scrollHeight - scrollTop - clientHeight < 100) {
    loadMoreNotes()
  }
}

onMounted(() => {
  const channelId = getChannelIdFromRoute()
  activeChannelId.value = channelId

  loadNotes(channelId, true)

  window.addEventListener('scroll', handleScroll)
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('scroll', handleScroll)
  window.removeEventListener('resize', handleResize)
})

watch(() => route.query.channelId, (newChannelId) => {
  const channelId = newChannelId ? parseInt(newChannelId) : 0
  if (activeChannelId.value !== channelId) {
    activeChannelId.value = channelId
    loadNotes(channelId, true)
  }
}, { immediate: true })
</script>

<style scoped>
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

.discover-page {
  display: flex;
  min-height: calc(100dvh - 120px);
  flex-direction: column;
}

.empty-state {
  display: flex;
  flex: 1;
  min-height: 0;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding-bottom: 40px;
}

.empty-text {
  font-size: 14px;
  line-height: 18px;
  text-align: center;
  color: var(--color-tertiary-label);
  margin-top: 16px;
}

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

@media (max-width: 767px) {
  .discover-page { min-height: calc(100dvh - 138px); }

  .empty-state { padding-bottom: 16px; }

  .masonry-grid {
    display: flex;
    gap: 12px;
    padding: 0 12px;
  }

  .masonry-column { min-width: 0; padding: 0; }
  .masonry-item { margin-bottom: 12px; }
}
</style>
