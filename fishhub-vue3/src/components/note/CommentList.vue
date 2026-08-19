<template>
  <div class="p-[16px]" @scroll="handleScroll" ref="commentListRef">
    <!-- 只在有评论时显示评论数量 -->
    <div v-if="comments && comments.length > 0" class="text-[14px] ml-[8px] mb-[12px] text-gray-500">
      共 {{ total }} 条评论
    </div>
    
    <!-- 评论列表 -->
    <div>
      <CommentItem 
        v-for="comment in comments" 
        :key="comment.commentId" 
        :comment="comment"
        @reply="$emit('reply', $event)"
        @expand-replies="$emit('expand-replies', $event)"
        @like="$emit('like', $event)"
        @delete="$emit('delete', $event)"
      />
    </div>

    <!-- 空评论状态 -->
    <div v-if="comments === null || comments.length === 0" class="flex flex-col items-center py-[20px] gap-[20px] mb-20">
      <img 
        src="@/assets/empty-comment.png"
        class="w-[100px] h-[100px]"
      />
      <div class="flex items-center justify-center text-[14px]">
        <div class="text-gray-500">这是一片荒地</div>
        <div 
          class="text-[var(--color-link)] ml-[4px] cursor-pointer"
          @click="onClickComment"
        >
          点击评论
        </div>
      </div>
    </div>

    <!-- 到底了提示 -->
    <div v-if="comments && comments.length > 0 && !hasMore" class="flex flex-col items-center end-container">
        - THE END - 
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import CommentItem from './CommentItem.vue'

const props = defineProps({
  comments: {
    type: Array,
    default: () => []
  },
  total: {
    type: [Number, String],
    default: '0'
  },
  hasMore: {
    type: Boolean,
    default: false
  },
})

const emit = defineEmits(['load-more', 'click-comment', 'reply', 'expand-replies', 'like', 'delete'])
const commentListRef = ref(null)

// 处理滚动事件
const handleScroll = (e) => {
  if (!props.comments.length || !props.hasMore) return

  const container = e.target
  const scrollTop = container.scrollTop
  const scrollHeight = container.scrollHeight
  const clientHeight = container.clientHeight

  // 当滚动到距离底部 50px 时触发加载
  if (scrollHeight - scrollTop - clientHeight < 50) {
    emit('load-more')
  }
}



// 点击评论处理
const onClickComment = () => {
  emit('click-comment')
}
</script> 

<style scoped>
.end-container {
  margin-top: 24px;
  margin-bottom: 24px;
  font-size: 12px;
  line-height: 16px;
  color: var(--color-tertiary-label);
}

</style>
