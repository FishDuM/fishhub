<template>
  <div 
    :class="[
      !isReply && 'p-[8px]',
      !isReply && 'mb-[16px]',
      comment.isNewComment && 'new-comment-animation'
    ]"
    class="flex py-[8px]"
  >
    <UserAvatar
        :src="comment.avatar"
        :alt="`${comment.nickname || '用户'}的头像`"
        :class="[
          'rounded-full object-cover cursor-pointer',
          isReply ? 'w-[24px] h-[24px]' : 'w-[40px] h-[40px]'
        ]"
      />

    <div class="flex-1 ml-[12px]">
      <!-- 评论者信息 -->
      <div class="flex items-center justify-between">
        <div>
          <span class="name cursor-pointer">{{ comment.nickname }}</span>
        </div>
      </div>
      
      <!-- 评论内容 -->
      <div class="content">
        <span v-if="comment.replyUserName">回复 <span class="reply-nickname">{{ comment.replyUserName }}</span> :</span>
        {{ comment.content }}
      </div>

      <!-- 评论图片 -->
      <div v-if="comment.imageUrl" class="mt-[8px]">
        <img 
          :src="comment.imageUrl" 
          class="w-[120px] rounded-lg object-cover cursor-zoom-in hover:brightness-80"
          @click="showPreview = true"
        />
      </div>

      <!-- 图片预览 -->
      <ImagePreview
        v-model:visible="showPreview"
        :images="[comment.imageUrl]"
      />

      <div class="info my-[8px]">{{ formatRelativeTime(comment.createTime) }}</div>

      <!-- 评论底部操作区 -->
      <div class="flex items-center gap-2 text-gray-500 text-[12px] interactions">
        
        <!-- 点赞 -->
        <div 
          class="flex items-center gap-1 cursor-pointer hover:text-gray-800 ml-[2px]"
          @click="toggleLike"
        >
          <LikeIcon
            :active="isLiked"
            class="w-[16px] h-[16px] transition-all duration-200"
            :class="[isLiked ? 'animate-like' : 'animate-unlike']"
          />
          <span :class="{ 'text-[var(--color-like)]': isLiked }">{{ comment.likeTotal }}</span>
        </div>

        <!-- 回复 -->
        <div 
          class="flex items-center gap-1 cursor-pointer hover:text-gray-800"
          @click="onReplyClick"
        >
          <svg class="w-[16px] h-[16px]" viewBox="0 0 24 24" fill="none" stroke="currentColor">
            <path d="M21 11.5a8.38 8.38 0 0 1-.9 3.8 8.5 8.5 0 0 1-7.6 4.7 8.38 8.38 0 0 1-3.8-.9L3 21l1.9-5.7a8.38 8.38 0 0 1-.9-3.8 8.5 8.5 0 0 1 4.7-7.6 8.38 8.38 0 0 1 3.8-.9h.5a8.48 8.48 0 0 1 8 8v.5z" stroke-width="2"/>
          </svg>
          <span>回复</span>
        </div>

        <button
          v-if="String(userStore.profile.userId) === String(comment.userId)"
          type="button"
          class="cursor-pointer hover:text-[var(--color-danger)]"
          @click="emit('delete', comment)"
        >
          删除
        </button>
      </div>

      <!-- 子评论区域 -->
      <template v-if="Number(comment.childCommentTotal) > 0">
        <!-- 已加载的子评论列表 -->
        <div v-if="comment.childComments && comment.childComments.length > 0" class="mt-2">
          <div>
            <CommentItem 
              v-for="(childComment, index) in comment.childComments" 
              :key="index"
              :comment="childComment"
              :is-reply="true"
              @reply="$emit('reply', $event)"
              @like="$emit('like', $event)"
              @delete="$emit('delete', $event)"
            />
          </div>
        </div>
        
        <!-- 展开回复按钮 -->
        <div 
          v-if="Number(comment.childCommentTotal) > 1 && 
                (!comment.childComments || comment.childComments.length < Number(comment.childCommentTotal)) && 
                comment.hasMoreChildComments !== false"
          class="show-more mt-2"
          @click="handleExpandReplies(comment)"
        >
          {{ comment.childComments?.length > 1 ? '展开更多回复' : `展开 ${Number(comment.childCommentTotal) - 1} 条回复` }}
        </div>
      </template>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import ImagePreview from '@/components/common/ImagePreview.vue'
import LikeIcon from '@/components/common/LikeIcon.vue'
import UserAvatar from '@/components/common/UserAvatar.vue'
import { useUserStore } from '@/stores/user'
import { formatRelativeTime } from '@/utils/date'

const userStore = useUserStore()

const props = defineProps({
  comment: {
    type: Object,
    required: true,
    validator: comment => comment && comment.commentId != null
  },
  isReply: {
    type: Boolean,
    default: false
  }
})

const showPreview = ref(false)
const isLiked = computed(() => Boolean(props.comment.isLiked))

const emit = defineEmits(['reply', 'expand-replies', 'like', 'delete'])

const toggleLike = () => {
  emit('like', { comment: props.comment, liked: !isLiked.value })
}

const onReplyClick = () => {
  emit('reply', props.comment)
}

const handleExpandReplies = (comment) => {
  emit('expand-replies', comment)
}
</script>

<style scoped>
.name {
    color: var(--color-secondary-label);
    line-height: 18px;
    font-size: 14px;
}

.name:hover {
    color: var(--color-primary-label);
}

.content {
    margin-top: 4px;
    line-height: 140%;
    color: var(--color-primary-label);
    font-size: 14px;
}


.info {
    display: flex;
    flex-direction: column;
    justify-content: space-between;
    font-size: 12px;
    line-height: 16px;
    color: var(--color-tertiary-label);
}

.interactions {
  color: var(--color-secondary-label);
  font-size: 12px;
  line-height: 16px;
  white-space: nowrap;
}

.interactions svg {
  flex-shrink: 0;
}

.show-more {
    margin-left: 38px;
    height: 20px;
    line-height: 20px;
    color: var(--color-link);
    cursor: pointer;
    font-weight: 500;
    font-size: 14px;
}

@keyframes like {
  0% {
    transform: scale(1);
  }
  25% {
    transform: scale(0.8);
  }
  50% {
    transform: scale(1.2);
  }
  75% {
    transform: scale(0.95);
  }
  100% {
    transform: scale(1);
  }
}

@keyframes unlike {
  0% {
    transform: scale(1);
  }
  25% {
    transform: scale(0.9);
  }
  50% {
    transform: scale(1.1);
  }
  75% {
    transform: scale(0.95);
  }
  100% {
    transform: scale(1);
  }
}

.animate-like {
  animation: like 0.4s cubic-bezier(0.175, 0.885, 0.32, 1.275);
  transform-origin: center;
}

.animate-unlike {
  animation: unlike 0.4s cubic-bezier(0.175, 0.885, 0.32, 1.275);
  transform-origin: center;
}

/* 防止动画重复播放 */
.animate-like, .animate-unlike {
  animation-fill-mode: forwards;
}

/* 防止动画过程中文字抖动 */
.interactions span {
  min-width: 1.5em;
  display: inline-block;
  text-align: left;
}

/* 新评论动画效果 */
.new-comment-animation {
  animation: highlightNewComment 2s ease-out forwards;
}

@keyframes highlightNewComment {
  0% {
    background-color: rgba(255, 36, 66, 0.1);
  }
  100% {
    background-color: transparent;
  }
}

.reply-nickname {
  color: var(--color-secondary-label);
}
</style>
