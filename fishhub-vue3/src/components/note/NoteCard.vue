<template>
  <div class="note-card">
    <div 
      class="note-media relative rounded-lg overflow-hidden"
      @click="$emit('click', note)"
    >
      <!-- 视频笔记 -->
      <template v-if="Number(note.type) === 1">
        <video
          ref="videoRef"
          :src="note.videoUri" 
          class="w-full object-cover border border-gray-200 rounded-2xl hover:brightness-80 cursor-pointer"
          preload="metadata"
          muted
        ></video>
        <div class="absolute right-2 top-2 play-icon">
          <svg class="w-3 h-3" viewBox="0 0 24 24" fill="currentColor">
            <path d="M8 5v14l11-7z"/>
          </svg>
        </div>
      </template>

      <!-- 图文笔记（默认） -->
      <img v-else
        :src="coverUrl" 
        class="w-full min-h-[120px] bg-gray-100 object-cover border border-gray-200 rounded-2xl hover:brightness-80 cursor-pointer"
        loading="lazy"
      />
    </div>

    <div class="p-[12px]">
      <h3 class="note-title" v-if="note.highlightTitle" v-html="note.highlightTitle"></h3>
      <h3 class="note-title" v-else>{{ note.title }}</h3>
      <div class="flex items-center">
        <router-link v-if="note.creatorId" :to="`/user/profile/${note.creatorId}`">
          <UserAvatar
            :src="note.avatar"
            :alt="`${note.nickname || '用户'}的头像`"
            class="w-[20px] h-[20px] mr-[6px] rounded-full border border-gray-200 object-cover"
          />
        </router-link>
        <div v-else>
          <UserAvatar
            :src="note.avatar"
            :alt="`${note.nickname || '用户'}的头像`"
            class="w-[20px] h-[20px] mr-[6px] rounded-full border border-gray-200 object-cover"
          />
        </div>
        <span class="text-[12px] text-gray-600 hover:text-gray-800 flex-1 truncate">
          <router-link v-if="note.creatorId" :to="`/user/profile/${note.creatorId}`">
            {{ note.nickname }}
          </router-link>
          <template v-else>{{ note.nickname }}</template>
        </span>
        <button 
          class="flex items-center transition-colors group"
          @click.stop="toggleLike"
        >
          <LikeIcon
            :active="isLiked"
            class="w-[16px] h-[16px] transition-all duration-300"
            :class="{'scale-animation': isLiked}"
          />
          <span 
            class="ml-1 text-[12px] text-gray-600 transition-all duration-300"
            :class="{'scale-animation': isLiked}"
          >
            {{ likeCount || 0 }}
          </span>
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, inject, watch } from 'vue'
import { likeNote, unlikeNote } from '@/api/note'
import { message } from '@/utils/message'
import { useUserStore } from '@/stores/user'
import LikeIcon from '@/components/common/LikeIcon.vue'
import UserAvatar from '@/components/common/UserAvatar.vue'

const userStore = useUserStore()

const props = defineProps({
  note: {
    type: Object,
    required: true
  }
})

const emit = defineEmits(['click', 'like-change'])

// 封面图片解析（支持 cover 字段及 imgUris 降级回退）
const coverUrl = computed(() => {
  if (props.note.cover) return props.note.cover
  if (Array.isArray(props.note.imgUris) && props.note.imgUris.length > 0) return props.note.imgUris[0]
  if (typeof props.note.imgUris === 'string' && props.note.imgUris) return props.note.imgUris.split(',')[0]
  return ''
})

// 点赞状态
const isLiked = computed(() => Boolean(props.note.isLiked))
const likeCount = ref(props.note.likeTotal)
const isLikeSubmitting = ref(false)

// 详情弹窗会更新父级列表中的笔记对象；同步该变更，避免卡片继续显示初始化时的旧点赞数。
watch(() => props.note.likeTotal, (likeTotal) => {
  likeCount.value = likeTotal
})

const parseLikeTotal = (value) => {
  const text = String(value ?? 0).trim()
  if (text.endsWith('万')) {
    return Math.round((Number.parseFloat(text.slice(0, -1)) || 0) * 10000)
  }
  return Number(text) || 0
}

const formatLikeTotal = (total) => {
  if (total < 10000) return total
  return `${(total / 10000).toFixed(1).replace(/\.0$/, '')}万`
}


// 登录状态控制
const isLoggedIn = computed(() => userStore.isLoggedIn)
const showLoginModal = inject('showLoginModal')

// 切换点赞状态
const toggleLike = () => {
  if (!isLoggedIn.value) {
    showLoginModal.value = true
    return
  }
  if (isLikeSubmitting.value) return
  isLikeSubmitting.value = true

  const wasLiked = isLiked.value
  const noteId = props.note.id ?? props.note.noteId
  const request = wasLiked ? unlikeNote(noteId) : likeNote(noteId)

  request.then(res => {
    if (!res.success) {
      message.error(res.message || res.errorMessage || (wasLiked ? '取消点赞失败' : '点赞失败'))
      return
    }

    const likeTotal = formatLikeTotal(Math.max(0, parseLikeTotal(likeCount.value) + (wasLiked ? -1 : 1)))
    emit('like-change', { noteId, isLiked: !wasLiked, likeTotal })
  }).catch(err => {
    const errData = err?.response?.data
    message.error(errData?.message || errData?.errorMessage || (wasLiked ? '取消点赞失败，请稍后重试' : '点赞失败，请稍后重试'))
  }).finally(() => {
    isLikeSubmitting.value = false
  })
}
</script>

<style scoped>
.note-title {
    margin-bottom: 8px;
    word-break: break-all;
    display: -webkit-box;
    -webkit-box-orient: vertical;
    -webkit-line-clamp: 2;
    overflow: hidden;
    font-weight: 500;
    font-size: 14px;
    line-height: 140%;
    color: var(--color-primary-label);
}

/* 点赞动画 */
@keyframes scale {
  0% {
    transform: scale(1);
  }
  50% {
    transform: scale(1.2);
  }
  100% {
    transform: scale(1);
  }
}

.scale-animation {
  animation: scale 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

/* 悬浮效果 */
button:hover svg {
  transform: scale(1.1);
}

.play-icon {
  display: flex
;
    align-items: center;
    justify-content: center;
    position: absolute;
    right: 14px;
    top: 14px;
    width: 20px;
    height: 20px;
    color: #fff;
    background: rgba(64,64,64,0.25);
    backdrop-filter: saturate(150%) blur(10px);
    border-radius: 20px;
}

@media (max-width: 767px) {
  .note-media :deep(img), .note-media :deep(video) { display: block; border-radius: 14px; }
  .note-card > div + div { padding: 9px 2px 2px; }
  .note-title { margin-bottom: 6px; font-size: 15px; line-height: 1.38; }
  .play-icon { top: 10px; right: 10px; width: 26px; height: 26px; }
  .play-icon svg { width: 14px; height: 14px; }
}
</style>
