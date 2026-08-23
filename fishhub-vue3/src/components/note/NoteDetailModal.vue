<template>
  <Teleport to="body">

    <div v-if="visible" class="fixed inset-0 bg-gray-800/25 z-[100]" @click="onClose"></div>


    <Transition
      name="zoom"
      appear
      @before-enter="onBeforeEnter"
      @enter="onEnter"
      @leave="onLeave"
    >
      <div v-if="visible" class="fixed inset-0 z-[101] pointer-events-none">
        <div
          class="note-detail-dialog absolute left-1/2 top-1/2 -translate-x-1/2 -translate-y-1/2 bg-white h-[90vh]
          max-w-[90%] md:max-w-[85%] lg:max-w-[80%] xl:max-w-[70%] 2xl:max-w-[60%] w-auto rounded-lg flex overflow-hidden pointer-events-auto"
          ref="modalRef"
        >

          <div class="h-full flex-1 flex items-center justify-center overflow-hidden">
            <div class="h-full w-full flex items-center justify-center">
              <ImageCarousel v-if="currNote.type === 0" :images="currNote.imgUris || []" class="h-full w-full" />
              <VideoPlayer v-else
                      :video-url="currNote.videoUri"
                      :autoplay="true"
                    ></VideoPlayer>
            </div>
          </div>


          <div class="note-detail-sidebar w-[480px] min-w-[480px] flex flex-col bg-white">


            <div
              class="p-[24px] flex items-center sticky top-0 bg-white"
              :class="{'border-b border-gray-100': isScrolled}"
              ref="authorInfoRef"
            >
              <router-link :to="`/user/profile/${currNote.creatorId}`">
                <UserAvatar
                  :src="currNote.avatar"
                  :alt="`${currNote.creatorName || '用户'}的头像`"
                  class="w-[40px]! h-[40px]! rounded-full object-cover"
                />
              </router-link>

              <router-link :to="`/user/profile/${currNote.creatorId}`" class="ml-[12px] flex-1">
                      <div class="font-medium text-[16px] text-gray-600 hover:text-gray-800">{{ currNote.creatorName }}</div>
              </router-link>


              <button
              v-if="!userStore.isLoggedIn || userStore.profile.userId !== currNote.creatorId"
              @click="handleFollow"
              :class="isCreatorFollowed ? 'border border-gray-300 text-gray-600 bg-white' : 'bg-[var(--color-primary)] text-[var(--color-primary-contrast)]'"
              class="px-6 py-2 rounded-full font-bold hover:opacity-90 w-[96px] h-[40px] cursor-pointer">
                {{ isCreatorFollowed ? '已关注' : '关注' }}
              </button>
              <button
                v-else
                @click="handleVisibilityToggle"
                class="px-4 py-2 rounded-full font-bold border border-gray-300 text-gray-600 bg-white hover:bg-gray-50 h-[40px] cursor-pointer">
                {{ Number(currNote.visible) === 1 ? '公开笔记' : '设为私密' }}
              </button>
            </div>


            <div
              class="overflow-y-auto flex-1"
              @scroll="handleScroll"
              ref="scrollContainerRef"
            >

              <div
                class="text-[var(--color-primary-label)] px-[24px] pb-[24px] flex-1"
                ref="contentRef"
              >
                <h1 class="title">{{ currNote.title }}</h1>
                <div class="note-content whitespace-pre-wrap">{{ currNote.content }}</div>
                <ul v-if="currNote.topics && currNote.topics.length > 0" class="text-[var(--color-link)] flex flex-wrap gap-2">
                  <li v-for="(topic, index) in currNote.topics" :key="index" class="cursor-pointer">#{{topic.name}}</li>
                </ul>
                <div class="text-gray-500 text-[14px] mt-[12px]">
                  编辑于 {{ currNote.updateTime }}
                </div>
              </div>


              <div class="h-[1px] border-b border-gray-100 mx-[24px]"></div>


              <CommentList
                :comments="comments"
                :total="commentTotal"
                :has-more="hasMoreComments"
                @load-more="loadMoreComments"
                @reply="onReplyClick"
                @click-comment="focusComment"
                @expand-replies="handleExpandReplies"
                @like="handleCommentLike"
                @delete="handleDeleteComment"
              />
            </div>



            <div class="border-t border-gray-100 p-[16px]">
              <div class="flex flex-col text-gray-500 text-[15px]">

                <div class="flex items-center gap-2">

                  <div
                    v-if="!isLoggedIn"
                    class="content-input grow cursor-pointer"
                    @click="focusComment"
                  >
                    <svg class="w-4 h-4 text-gray-400" viewBox="0 0 24 24" fill="none" stroke="currentColor">
                      <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2M12 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8z" stroke-width="2"/>
                    </svg>
                    <span class="text-gray-500 text-sm ml-2">登录后评论</span>
                  </div>


                  <div
                    v-else
                    class="flex flex-col"
                    :class="{ 'w-full': isInputFocused }"
                  >

                    <div
                      v-if="replyTo"
                      class="flex flex-col px-3 py-2 text-[14px] w-full"
                    >
                      <div class="flex items-center reply">
                        回复
                        <span class="text-[var(--color-primary-label)] mx-1">{{ replyTo.nickname }}</span>
                      </div>
                      <div class="reply-content line-clamp-1">
                        {{ replyTo.content }} <span v-if="replyTo.imageUrl">[图片]</span>
                      </div>
                    </div>


                    <div
                      class="gap-2 rounded-full flex items-center content-input"
                      :class="{
                        'w-full px-[16px]!': isInputFocused,
                        'w-[200px]': !isInputFocused
                      }"
                      @click="focusComment"
                    >

                      <UserAvatar
                        v-if="!isInputFocused"
                        :src="userStore.profile.avatar"
                        :alt="`${userStore.profile.nickname || '当前用户'}的头像`"
                        class="w-[24px] h-[24px] rounded-full object-cover shrink-0"
                      />


                      <div
                        v-if="!isInputFocused && !commentContent"
                        class="text-gray-500 text-sm ml-2 whitespace-nowrap overflow-hidden text-ellipsis"
                      >
                        说点什么...
                      </div>


                      <input
                        type="text"
                        placeholder="说点什么..."
                        v-model="commentContent"
                        class="flex-1 bg-transparent focus:outline-none min-w-0 text-[var(--color-primary-label)]"
                        :class="{
                          'ml-2 text-sm': !isInputFocused,
                          'text-[16px]': isInputFocused
                        }"
                        @blur="onInputBlur"
                        ref="commentInput"
                      />

                    </div>

                    <div class="mt-[8px]" v-if="commentImage">
                      <img :src="commentImage"
                      class="w-[60px]! rounded-lg object-cover cursor-zoom-in hover:brightness-80 ml-2">
                    </div>
                  </div>


                  <div v-if="!isInputFocused" class="flex items-center gap-4 ml-auto text-[var(--color-primary-label)]">
                    <div class="flex items-center gap-1 cursor-pointer hover:text-gray-800">
                      <LikeIcon
                        :active="isNoteLiked"
                        class="w-[20px] h-[20px] transition-all duration-200"
                        :class="[isNoteLiked ? 'animate-like' : 'animate-unlike']"
                        @click="handleNoteLike"
                      />
                      <span>{{ currNote.likeTotal }}</span>
                    </div>
                    <div class="flex items-center gap-1 cursor-pointer hover:text-gray-800">
                      <svg
                        class="collect-icon w-[20px] h-[20px] transition-all duration-200"
                        :class="[isNoteCollected ? 'animate-like is-active' : 'animate-unlike']"
                        viewBox="0 0 24 24"
                        fill="none"
                        stroke="currentColor"
                        @click="handleNoteCollect"
                      >
                        <path d="M12 2l3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01L12 2z" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
                      </svg>
                      <span>{{ currNote.collectTotal }}</span>
                    </div>
                    <div
                      class="flex items-center gap-1 cursor-pointer hover:text-gray-800"
                      @click="focusComment"
                    >
                      <svg class="w-[20px] h-[20px]" viewBox="0 0 24 24" fill="none" stroke="currentColor">
                        <path d="M21 11.5a8.38 8.38 0 0 1-.9 3.8 8.5 8.5 0 0 1-7.6 4.7 8.38 8.38 0 0 1-3.8-.9L3 21l1.9-5.7a8.38 8.38 0 0 1-.9-3.8 8.5 8.5 0 0 1 4.7-7.6 8.38 8.38 0 0 1 3.8-.9h.5a8.48 8.48 0 0 1 8 8v.5z" stroke-width="2"/>
                      </svg>
                      <span class="text-sm">{{ commentTotal }}</span>
                    </div>
                  </div>
                </div>



                <div v-if="isInputFocused" class="flex items-center justify-between mt-3">
                  <div class="flex items-center gap-1">
                    <div class="relative">
                      <button
                        class="p-[10px] hover:text-[var(--color-primary-label)] hover:bg-gray-100 rounded-full"
                        @click="toggleEmojiPicker"
                        ref="emojiButtonRef"
                      >
                        <svg class="w-5 h-5" viewBox="0 0 24 24" fill="none">
                          <path d="M12 22C17.5228 22 22 17.5228 22 12C22 6.47715 17.5228 2 12 2C6.47715 2 2 6.47715 2 12C2 17.5228 6.47715 22 12 22Z" stroke="currentColor" stroke-width="2"/>
                          <path d="M8.5 15C8.5 15 9.8125 17 12 17C14.1875 17 15.5 15 15.5 15" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
                          <path d="M9 10H9.01" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
                          <path d="M15 10H15.01" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
                        </svg>
                      </button>


                      <div
                        v-if="showEmojiPicker"
                        class="emoji-picker border border-gray-200"
                        ref="emojiPickerRef"
                      >
                        <div class="emoji-grid">
                          <button
                            v-for="emoji in emojiList"
                            :key="emoji"
                            class="emoji-item"
                            @click="insertEmoji(emoji)"
                          >
                            {{ emoji }}
                          </button>
                        </div>
                      </div>
                    </div>


                    <div class="relative">
                      <button
                        class="p-[10px] hover:text-[var(--color-primary-label)] hover:bg-gray-100 rounded-full"
                        @click="triggerFileUpload"
                      >
                        <svg class="w-5 h-5" viewBox="0 0 24 24" fill="none" stroke="currentColor">
                          <path d="M19 3H5C3.89543 3 3 3.89543 3 5V19C3 20.1046 3.89543 21 5 21H19C20.1046 21 21 20.1046 21 19V5C21 3.89543 20.1046 3 19 3Z" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
                          <path d="M8.5 10C9.32843 10 10 9.32843 10 8.5C10 7.67157 9.32843 7 8.5 7C7.67157 7 7 7.67157 7 8.5C7 9.32843 7.67157 10 8.5 10Z" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
                          <path d="M21 15L16 10L5 21" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
                        </svg>
                      </button>
                      <input
                        type="file"
                        ref="fileInputRef"
                        class="hidden"
                        accept="image/*"
                        @change="handleFileChange"
                      />
                    </div>
                  </div>
                  <div class="flex items-center gap-2">

                    <button
                      class="w-[64px] h-[40px] text-[16px] text-[var(--color-primary-contrast)] bg-[var(--color-primary)]
                      rounded-full font-bold cursor-pointer"
                      :class="{'opacity-50': !commentContent.trim() && !commentImage}"
                      @click="handlePublishComment"
                    >
                      发送
                    </button>
                    <button class="border border-gray-200 w-[64px] h-[40px] text-[16px]
                    font-bold text-gray-600 hover:text-gray-800 hover:bg-gray-100
                    rounded-full cursor-pointer" @click="onCancel">
                      取消
                    </button>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup>
import { ref, computed, nextTick, watch, onBeforeUnmount, inject, onMounted } from 'vue'
import gsap from 'gsap'
import CommentList from './CommentList.vue'
import ImageCarousel from '@/components/common/ImageCarousel.vue'
import VideoPlayer from '@/components/common/VideoPlayer.vue'
import LikeIcon from '@/components/common/LikeIcon.vue'
import UserAvatar from '@/components/common/UserAvatar.vue'
import { getNoteDetail, getNoteInteractionState, likeNote, unlikeNote, collectNote, uncollectNote, updateNoteVisibility } from '@/api/note'
import { getCommentList, publishComment, getChildCommentList, likeComment, unlikeComment, getLikedCommentIds, deleteComment } from '@/api/comment'
import { followUser, unfollowUser, checkFollowing } from '@/api/relation'
import { useUserStore } from '@/stores/user'
import { message } from '@/utils/message'
import { uploadFile } from '@/api/file'

const userStore = useUserStore()

const props = defineProps({
  visible: {
    type: Boolean,
    default: false
  },
  note: {
    type: Object,
    default: () => ({})
  }
})

const currNote = ref({})
const currNoteId = ref('')

const mergeNoteDetail = (detail = {}) => ({
  ...props.note,
  ...detail,
  topics: detail.topics || (detail.topicName ? [{ id: detail.topicId, name: detail.topicName }] : []),
  likeTotal: detail.likeTotal ?? props.note.likeTotal ?? 0,
  collectTotal: detail.collectTotal ?? props.note.collectTotal ?? 0,
  commentTotal: detail.commentTotal ?? props.note.commentTotal ?? 0
})

const emit = defineEmits(['update:visible', 'interaction-change'])

const modalRef = ref(null)
const authorInfoRef = ref(null)
const contentRef = ref(null)
const scrollContainerRef = ref(null)

let animation = null

onBeforeUnmount(() => {
  if (animation) {
    animation.kill()
    animation = null
  }
})

const onClose = () => {
  emit('update:visible', false)
}

const onBeforeEnter = (el) => {
  if (modalRef.value) {
    gsap.set(modalRef.value, {
      scale: 0.8,
      opacity: 0
    })
  }
}

const onEnter = (el, done) => {
  nextTick(() => {
    if (modalRef.value) {
      animation = gsap.to(modalRef.value, {
        scale: 1,
        opacity: 1,
        duration: 0.3,
        ease: 'back.out(1.7)',
        onComplete: done
      })
    } else {
      done()
    }
  })
}

const onLeave = (el, done) => {
  if (modalRef.value) {
    animation = gsap.to(modalRef.value, {
      scale: 0.8,
      opacity: 0,
      duration: 0.2,
      ease: 'power1.in',
      onComplete: done
    })
  } else {
    done()
  }
}

const isScrolled = ref(false)

const handleScroll = (e) => {
  isScrolled.value = e.target.scrollTop > 0

  const container = e.target
  const scrollTop = container.scrollTop
  const scrollHeight = container.scrollHeight
  const clientHeight = container.clientHeight

  if (scrollHeight - scrollTop - clientHeight < 50) {
    loadMoreComments()
  }
}

const currCommentPageNo = ref(1)
const totalCommentPage = ref(0)
const comments = ref([])


const commentTotal = ref(0)
const hasMoreComments = computed(() => currCommentPageNo.value < totalCommentPage.value)
const isLoadingMoreComments = ref(false)

const normalizeComment = (comment) => {
  const childComments = [...(comment.childComments || [])]
  if (comment.firstReplyComment &&
      !childComments.some(child => String(child.commentId) === String(comment.firstReplyComment.commentId))) {
    childComments.unshift(comment.firstReplyComment)
  }
  return { ...comment, childComments }
}

const loadMoreComments = () => {
  if (currCommentPageNo.value >= totalCommentPage.value || isLoadingMoreComments.value) return

  isLoadingMoreComments.value = true
  const nextPage = currCommentPageNo.value + 1

  getCommentList(currNoteId.value, nextPage).then(async res => {
    if (res.success) {
      const existingCommentIds = new Set(comments.value.map(c => c.commentId))
      const newComments = (res.data || [])
        .map(normalizeComment)
        .filter(c => !existingCommentIds.has(c.commentId))

      await hydrateCommentLikeState(newComments)
      comments.value = [...comments.value, ...newComments]
      currCommentPageNo.value = res.pageNo
      totalCommentPage.value = res.totalPage
    }
  }).finally(() => {
    isLoadingMoreComments.value = false
  })
}

const isLoggedIn = computed(() => userStore.isLoggedIn)
const isCreatorFollowed = ref(false)

const handleVisibilityToggle = async () => {
  const visible = Number(currNote.value.visible) === 1 ? 0 : 1
  try {
    const res = await updateNoteVisibility(currNoteId.value, visible)
    if (!res.success) {
      message.show(res.message || '修改可见性失败')
      return
    }
    currNote.value.visible = visible
    message.show(visible === 1 ? '已设为仅自己可见' : '已公开笔记')
  } catch (error) {
    console.error('修改笔记可见性失败:', error)
    message.show('修改可见性失败')
  }
}

const hydrateCommentLikeState = async (commentItems = []) => {
  if (!isLoggedIn.value || !commentItems.length) return
  const allItems = commentItems.flatMap(comment => [
    comment,
    ...(comment.firstReplyComment ? [comment.firstReplyComment] : []),
    ...(comment.childComments || [])
  ])
  const ids = [...new Set(allItems.map(comment => comment.commentId).filter(Boolean))]
  if (!ids.length) return
  try {
    const res = await getLikedCommentIds(ids)
    if (!res.success) return
    const likedIds = new Set((res.data || []).map(String))
    allItems.forEach(comment => {
      comment.isLiked = likedIds.has(String(comment.commentId))
    })
  } catch (error) {
    console.error('查询评论点赞状态失败:', error)
  }
}

const isInputFocused = ref(false)
const commentInput = ref(null)
const commentContent = ref('')

const replyTo = ref(null)

const showLoginModal = inject('showLoginModal')

const focusComment = () => {
  if (!isLoggedIn.value) {
    showLoginModal.value = true
    return
  }
  isInputFocused.value = true
  nextTick(() => {
    if (commentInput.value) {
      commentInput.value.focus()
    }
  })
}

const onReplyClick = (comment) => {
  if (comment.isReply) {
    const parentComment = comments.value.find(c =>
      c.replies?.some(r => r.id === comment.id)
    )
    if (parentComment) {
      replyTo.value = comment
      isInputFocused.value = true
      nextTick(() => {
        if (commentInput.value) {
          commentInput.value.focus()
        }
      })
    }
  } else {
    replyTo.value = comment
    isInputFocused.value = true
    nextTick(() => {
      if (commentInput.value) {
        commentInput.value.focus()
      }
    })
  }
}

const onInputBlur = (e) => {
  if (e.relatedTarget?.textContent === '取消') {
    isInputFocused.value = false
  }
}

const commentImage = ref('')
const fileInputRef = ref(null)

const onCancel = () => {
  isInputFocused.value = false
  commentContent.value = ''
  commentImage.value = ''
  replyTo.value = null
  if (commentInput.value) {
    commentInput.value.blur()
  }
}

watch(() => props.visible, (newVisible) => {
  if (newVisible && props.note && props.note.id) {
    currNoteId.value = props.note.id
    currNote.value = { ...props.note }
    isNoteLiked.value = false
    isNoteCollected.value = false
    commentTotal.value = Number(props.note.commentTotal) || 0

    getNoteDetail(props.note.id).then(res => {
      if (res.success && res.data) {
        currNote.value = mergeNoteDetail(res.data)
        if (res.data.commentTotal != null) {
          commentTotal.value = Number(res.data.commentTotal) || 0
        }
      }
    })

    if (isLoggedIn.value) {
      checkFollowing(props.note.creatorId).then(res => {
        if (res.success) isCreatorFollowed.value = Boolean(res.data)
      }).catch(error => console.error('查询关注状态失败:', error))

      getNoteInteractionState(props.note.id).then(res => {
        if (res.success && res.data) {
          isNoteLiked.value = Boolean(res.data.isLiked)
          isNoteCollected.value = Boolean(res.data.isCollected)
          emit('interaction-change', {
            noteId: props.note.id ?? props.note.noteId,
            likeTotal: currNote.value.likeTotal,
            collectTotal: currNote.value.collectTotal,
            isLiked: isNoteLiked.value,
            isCollected: isNoteCollected.value
          })
        }
      })
    }

    getCommentList(props.note.id, 1).then(res => {
      if (res.success) {
        comments.value = (res.data || []).map(normalizeComment)
        hydrateCommentLikeState(comments.value)
        currCommentPageNo.value = res.pageNo
        totalCommentPage.value = res.totalPage
        if (commentTotal.value === 0 && res.totalCount) {
          commentTotal.value = res.totalCount
          currNote.value.commentTotal = res.totalCount
        }
      }
    })


  } else {
    isCreatorFollowed.value = false
    currNote.value = {}
    currNoteId.value = ''
    comments.value = []
    commentTotal.value = 0
    currCommentPageNo.value = 1
    totalCommentPage.value = 1
    commentContent.value = ''
    commentImage.value = ''
    replyTo.value = null
    isInputFocused.value = false
    isScrolled.value = false
    isNoteLiked.value = false
    isNoteCollected.value = false
  }
})

const showEmojiPicker = ref(false)
const emojiButtonRef = ref(null)
const emojiPickerRef = ref(null)

const emojiList = [
  '😀', '😃', '😄', '😁', '😆', '😅', '😂', '🤣', '😊', '😇',
  '🙂', '🙃', '😉', '😌', '😍', '🥰', '😘', '😗', '😙', '😚',
  '😋', '😛', '😝', '😜', '🤪', '🤨', '🧐', '🤓', '😎', '🤩',
  '🥳', '😏', '😒', '😞', '😔', '😟', '😕', '🙁', '☹️', '😣',
  '😖', '😫', '😩', '🥺', '😢', '😭', '😤', '😠', '😡', '🤬',
  '🤯', '😳', '🥵', '🥶', '😱', '😨', '😰', '😥', '😓', '🤗',
  '🤔', '🤭', '🤫', '🤥', '😶', '😐', '😑', '😬', '🙄', '😯',
  '😦', '😧', '😮', '😲', '🥱', '😴', '🤤', '😪', '😵', '🤐',
  '🥴', '🤢', '🤮', '🤧', '😷', '🤒', '🤕', '🤑', '🤠', '😈',
  '👿', '👹', '👺', '🤡', '💩', '👻', '💀', '☠️', '👽', '👾'
]

const toggleEmojiPicker = () => {
  showEmojiPicker.value = !showEmojiPicker.value
}

const insertEmoji = (emoji) => {
  commentContent.value += emoji
  showEmojiPicker.value = false
}

const handleClickOutside = (event) => {
  if (
    showEmojiPicker.value &&
    emojiPickerRef.value &&
    !emojiPickerRef.value.contains(event.target) &&
    !emojiButtonRef.value.contains(event.target)
  ) {
    showEmojiPicker.value = false
  }
}

onMounted(() => {
  document.addEventListener('click', handleClickOutside)
})

onBeforeUnmount(() => {
  document.removeEventListener('click', handleClickOutside)
})



const triggerFileUpload = () => {
  fileInputRef.value.click()
}

const handleFileChange = (event) => {
  const file = event.target.files[0]
  if (!file) return

  if (!file.type.startsWith('image/')) {
    message.show('请选择图片文件')
    return
  }

  if (file.size > 5 * 1024 * 1024) {
    message.show('图片大小不能超过5MB')
    return
  }

  const formData = new FormData()
  formData.append('file', file)

  message.show('图片上传中...')

  uploadFile(formData).then(res => {
    if (res.success) {
      commentImage.value = res.data
    } else {
      message.show('图片上传失败')
    }
  }).catch(err => {
    console.error('图片上传错误:', err)
    message.show('图片上传失败')
  })

  event.target.value = ''
}

const findParentComment = (commentId, commentsList) => {
  for (const comment of commentsList) {
    if (comment.childComments) {
      const childIndex = comment.childComments.findIndex(child => String(child.commentId) === String(commentId))
      if (childIndex !== -1) {
        return { parentComment: comment, isChild: true, childIndex }
      }
    }
    if (String(comment.commentId) === String(commentId)) {
      return { parentComment: comment, isChild: false }
    }
  }
  return null
}


const handlePublishComment = () => {
  if (!commentContent.value.trim() && !commentImage.value) {
    message.show('请输入评论内容或上传图片')
    return
  }

  publishComment({
    noteId: currNoteId.value,
    content: commentContent.value,
    replyCommentId: replyTo.value?.commentId,
    imageUrl: commentImage.value
  }).then(res => {
    if (res.success) {
      message.show('评论成功')

      let commentId = res.data || null

      const newComment = {
        commentId: commentId,
        userId: userStore.profile.userId,
        content: commentContent.value,
        createTime: '刚刚',
        nickname: userStore.profile.nickname,
        avatar: userStore.profile.avatar,
        likeTotal: 0,
        imageUrl: commentImage.value,
        isNewComment: true
      }

      // 回复二级评论时插入同级位置；回复一级评论时进入该评论的回复列表。
      if (replyTo.value && replyTo.value.commentId) {
        const result = findParentComment(replyTo.value.commentId, comments.value)
        if (result) {
          if (result.isChild) {
            newComment.replyUserName = replyTo.value.nickname
            newComment.replyUserId = replyTo.value.userId
            result.parentComment.childComments.splice(result.childIndex + 1, 0, newComment)
            result.parentComment.childCommentTotal = (result.parentComment.childCommentTotal || 0) + 1
          } else {
            if (!result.parentComment.childComments) {
              result.parentComment.childComments = []
            }
            if (!result.parentComment.childCommentTotal) {
              result.parentComment.childCommentTotal = 0
            }
            result.parentComment.childCommentTotal += 1

            if (!result.parentComment.childComments.length) {
              result.parentComment.childComments = [newComment]
            } else {
              result.parentComment.childComments.unshift(newComment)
            }
          }
        }
      } else {
        if (comments.value && comments.value.length > 0) {
          comments.value = [newComment, ...comments.value]
        } else {
          comments.value = [newComment]
        }
      }

      commentTotal.value += 1
      currNote.value.commentTotal = commentTotal.value


      isInputFocused.value = false
      commentContent.value = ''
      commentImage.value = ''
      if (commentInput.value) {
        commentInput.value.blur()
      }

      nextTick(() => {
        if (scrollContainerRef.value) {
          if (!replyTo.value) {
            const contentHeight = contentRef.value ? contentRef.value.offsetHeight : 0
            scrollContainerRef.value.scrollTop = contentHeight
          }
          replyTo.value = null
        }
      })
    }
  })
}

const loadChildComments = (parentComment, pageNo = 1) => {
  if (!parentComment || !parentComment.commentId) return

  if (!parentComment.childComments) {
    parentComment.childComments = []
  }
  if (!parentComment.currChildCommentPage) {
    parentComment.currChildCommentPage = 0
  }

  getChildCommentList(parentComment.commentId, parentComment.currChildCommentPage + 1).then(res => {
    if (res.success) {
      if (res.data && res.data.length > 0) {
        parentComment.childComments.push(...res.data)
        hydrateCommentLikeState(res.data)

        parentComment.childCommentTotal = res.totalCount
        parentComment.currChildCommentPage = res.pageNo
        parentComment.totalChildCommentPage = res.totalPage
        parentComment.hasMoreChildComments = res.pageNo < res.totalPage
      } else {
        parentComment.hasMoreChildComments = false
      }
    }
  })
}

const handleExpandReplies = (comment) => {
  loadChildComments(comment)
}

const isNoteLiked = ref(false)
const isNoteLikeSubmitting = ref(false)
const isNoteCollectSubmitting = ref(false)

const parseInteractionTotal = (value) => {
  const text = String(value ?? 0).trim()
  if (text.endsWith('万')) {
    return Math.round((Number.parseFloat(text.slice(0, -1)) || 0) * 10000)
  }
  return Number(text) || 0
}

const formatInteractionTotal = (total) => {
  if (total < 10000) return total
  return `${(total / 10000).toFixed(1).replace(/\.0$/, '')}万`
}

const updateInteractionTotal = (field, delta) => {
  const currentTotal = parseInteractionTotal(currNote.value[field])
  currNote.value[field] = formatInteractionTotal(Math.max(0, currentTotal + delta))
  emit('interaction-change', {
    noteId: currNoteId.value,
    likeTotal: currNote.value.likeTotal,
    collectTotal: currNote.value.collectTotal,
    isLiked: isNoteLiked.value,
    isCollected: isNoteCollected.value
  })
}

const handleNoteLike = () => {
  if (!isLoggedIn.value) {
    showLoginModal.value = true
    return
  }

  if (isNoteLikeSubmitting.value) return
  isNoteLikeSubmitting.value = true

  if (!isNoteLiked.value) {
    likeNote(currNoteId.value).then(res => {
      if (res.success) {
        isNoteLiked.value = true
        updateInteractionTotal('likeTotal', 1)
      } else {
        message.show(res.message)
      }
    }).catch(() => {
      message.show('点赞失败，请稍后重试')
    }).finally(() => {
      isNoteLikeSubmitting.value = false
    })
    return
  }

  unlikeNote(currNoteId.value).then(res => {
    if (res.success) {
      isNoteLiked.value = false
      updateInteractionTotal('likeTotal', -1)
    } else {
      message.show(res.message)
    }
  }).catch(() => {
    message.show('取消点赞失败，请稍后重试')
  }).finally(() => {
    isNoteLikeSubmitting.value = false
  })
}

const handleCommentLike = async ({ comment, liked }) => {
  if (!isLoggedIn.value) {
    showLoginModal.value = true
    return
  }
  if (comment.likeSubmitting) return
  comment.likeSubmitting = true
  try {
    const res = await (liked ? likeComment(comment.commentId) : unlikeComment(comment.commentId))
    if (!res.success) {
      message.show(res.message)
      return
    }
    comment.isLiked = liked
    const total = Number(comment.likeTotal) || 0
    comment.likeTotal = Math.max(0, total + (liked ? 1 : -1))
  } catch (error) {
    console.error(liked ? '评论点赞失败:' : '取消评论点赞失败:', error)
    message.show(liked ? '评论点赞失败' : '取消评论点赞失败')
  } finally {
    comment.likeSubmitting = false
  }
}

const handleDeleteComment = async (comment) => {
  try {
    const res = await deleteComment(comment.commentId)
    if (!res.success) {
      message.show(res.message || '删除失败')
      return
    }
    const located = findParentComment(comment.commentId, comments.value)
    if (located?.isChild) {
      located.parentComment.childComments.splice(located.childIndex, 1)
      located.parentComment.childCommentTotal = Math.max(
        0, Number(located.parentComment.childCommentTotal || 0) - 1)
      commentTotal.value = Math.max(0, Number(commentTotal.value || 0) - 1)
    } else if (located) {
      const index = comments.value.findIndex(item => String(item.commentId) === String(comment.commentId))
      if (index !== -1) comments.value.splice(index, 1)
      const removedTotal = 1 + Number(comment.childCommentTotal || 0)
      commentTotal.value = Math.max(0, Number(commentTotal.value || 0) - removedTotal)
    }
    currNote.value.commentTotal = commentTotal.value
    message.show('删除成功')

  } catch (error) {
    console.error('删除评论失败:', error)
    message.show('删除评论失败')
  }
}

const isNoteCollected = ref(false)

const handleNoteCollect = () => {
  if (!isLoggedIn.value) {
    showLoginModal.value = true
    return
  }
  if (isNoteCollectSubmitting.value) return
  isNoteCollectSubmitting.value = true

  if (!isNoteCollected.value) {
    collectNote(currNoteId.value).then(res => {
      if (res.success) {
        isNoteCollected.value = true
        updateInteractionTotal('collectTotal', 1)
      } else {
        message.show(res.message)
      }
    }).catch(() => {
      message.show('收藏失败，请稍后重试')
    }).finally(() => {
      isNoteCollectSubmitting.value = false
    })
    return
  }

  uncollectNote(currNoteId.value).then(res => {
    if (res.success) {
      isNoteCollected.value = false
      updateInteractionTotal('collectTotal', -1)
    } else {
      message.show(res.message)
    }
  }).catch(() => {
    message.show('取消收藏失败，请稍后重试')
  }).finally(() => {
    isNoteCollectSubmitting.value = false
  })
}

const handleFollow = async () => {
  if (!isLoggedIn.value) {
    showLoginModal.value = true
    return
  }
  const wasFollowing = isCreatorFollowed.value
  try {
    const res = await (wasFollowing
      ? unfollowUser(currNote.value.creatorId)
      : followUser(currNote.value.creatorId))
    if (!res.success) {
      message.show(res.message)
      return
    }
    isCreatorFollowed.value = !wasFollowing
    message.show(wasFollowing ? '已取消关注' : '关注成功')
  } catch (error) {
    console.error(wasFollowing ? '取消关注失败:' : '关注失败:', error)
    message.show(wasFollowing ? '取消关注失败' : '关注失败')
  }
}
</script>

<style scoped>
.zoom-move {
  transition: transform 0.3s ease-out;
}

img {
  max-height: 90vh;
  width: auto;
}

.title {
    margin-bottom: 8px;
    font-weight: 600;
    font-size: 18px;
    line-height: 140%;
}

.content-input {
  caret-color: var(--color-primary);
  margin: 0px;
  height: 40px;
  background-color: var(--color-input-surface);
  border: none;
  padding: 0 10px;
  border-radius: 20px;
  outline: none;
  display: flex;
  align-items: center;
  white-space: nowrap;
}

.content-input:not(:focus-within) {
  overflow: hidden;
}

.line-clamp-1 {
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 1;
  -webkit-box-orient: vertical;
}

.reply {
    color: var(--color-secondary-label);
    font-size: 14px;
}

.reply-content {
    color: var(--color-primary-label);
    font-size: 14px;
    width: 100%;
    margin-top: 4px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
}

input::placeholder {
    color: transparent;
}

input:focus::placeholder {
    color: var(--color-tertiary-label);
}

:deep(.carousel-container) {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}

:deep(.carousel-image) {
  max-width: 100%;
  max-height: 90vh;
  object-fit: contain;
  object-position: center;
}

.emoji-picker {
  position: absolute;
  bottom: 45px;
  left: 0;
  max-height: 280px;
  background-color: var(--color-surface);
  border-radius: 8px;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.1);
  padding: 10px;
  z-index: 100;
  overflow-y: auto;
}

.emoji-grid {
  display: grid;
  grid-template-columns: repeat(10, 1fr);
  gap: 5px;
}

.emoji-item {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  font-size: 28px;
  cursor: pointer;
  border-radius: 4px;
  transition: background-color 0.2s;
}

.emoji-item:hover {
  background-color: var(--color-active-background);
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

.collect-icon.is-active {
  color: var(--color-collect);
  fill: currentColor;
}

.animate-like, .animate-unlike {
  animation-fill-mode: forwards;
}

.interactions span {
  min-width: 1.5em;
  display: inline-block;
  text-align: left;
}

@media (max-width: 767px) {
  .note-detail-dialog {
    inset: 0;
    width: 100%;
    height: 100dvh;
    max-width: none;
    border-radius: 0;
    flex-direction: column;
    transform: none;
  }

  .note-detail-dialog > div:first-child { flex: 0 0 42%; min-height: 0; }
  .note-detail-sidebar { width: 100%; min-width: 0; min-height: 0; }
  .note-detail-sidebar :deep(.p-\[24px\]) { padding: 16px; }
  .title { font-size: 17px; }
  .emoji-picker { right: 0; left: auto; max-width: calc(100vw - 24px); }
  .emoji-grid { grid-template-columns: repeat(8, 1fr); }
  .emoji-item { width: 34px; height: 34px; font-size: 24px; }
}
</style>
