<template>
  <div 
    class="relative h-full flex items-center justify-center group"
    @mouseenter="showControls = true"
    @mouseleave="showControls = false"
    @wheel="handleWheel"
  >
    <!-- 图片容器 -->
    <div class="h-full overflow-hidden">
      <div 
        class="h-full flex transition-transform duration-500 ease-in-out"
        :style="{ transform: `translateX(-${currentIndex * 100}%)` }"
      >
        <div 
          v-for="(image, index) in images" 
          :key="index"
          class="h-full w-full flex-shrink-0 flex items-center justify-center bg-gray-100 border-r border-gray-200"
        >
          <img 
            :src="image" 
            class="h-full w-auto object-contain cursor-zoom-in"
            alt="note image"
            @load="onImageLoad"
            ref="imageRefs"
            @click="onImageClick"
          />
        </div>
      </div>
    </div>

    <!-- 图片计数器 -->
    <Transition name="fade">
      <div 
        v-if="images.length > 1 && showControls"
        class="absolute top-4 right-4 px-[12px] py-[6px] rounded-full bg-black/40 backdrop-blur-sm text-white text-xs"
      >
        {{ currentIndex + 1 }}/{{ images.length }}
      </div>
    </Transition>

    <!-- 左右切换按钮 -->
    <Transition name="fade">
      <button 
        v-if="images.length > 1 && showControls"
        class="carousel-btn left-4" 
        @click="prev"
      >
        <svg class="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor">
          <path d="M15 19l-7-7 7-7" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
      </button>
    </Transition>
    
    <Transition name="fade">
      <button 
        v-if="images.length > 1 && showControls"
        class="carousel-btn right-4" 
        @click="next"
      >
        <svg class="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor">
          <path d="M9 5l7 7-7 7" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
      </button>
    </Transition>

    <!-- 底部指示器 -->
    <div 
      v-if="images.length > 1"
      class="absolute bottom-4 left-1/2 -translate-x-1/2 flex items-center gap-2"
    >
      <button 
        v-for="(_, index) in images" 
        :key="index"
        class="w-1.5 h-1.5 rounded-full transition-all duration-200"
        :class="index === currentIndex ? 'bg-white scale-125' : 'bg-white/50 hover:bg-white/80'"
        @click="currentIndex = index"
      />
    </div>

    <!-- 图片预览 -->
    <ImagePreview
      v-model:visible="showPreview"
      :images="images"
      :initial-index="currentIndex"
    />
  </div>
</template>

<script setup>
import { ref, watch, onBeforeUnmount, onMounted } from 'vue'
import ImagePreview from './ImagePreview.vue'
import { useImageNavigator } from '@/composables/useImageNavigator'

const props = defineProps({
  images: {
    type: Array,
    required: true,
    default: () => []
  }
})

const currentIndex = ref(0)
const imageRefs = ref([])
const showControls = ref(false)
const showPreview = ref(false)

const onImageLoad = (event) => {
  const img = event.target
  const container = img.parentElement.parentElement.parentElement
  const ratio = img.naturalWidth / img.naturalHeight
  const containerHeight = container.clientHeight
  const newWidth = containerHeight * ratio

  container.style.width = `${newWidth}px`
}

watch(() => props.images, (newImages) => {
  if (newImages && newImages.length) {
    currentIndex.value = 0
  }
}, { immediate: true })

const { previous: prev, next, handleWheel } = useImageNavigator(() => props.images, currentIndex)

const handleKeydown = (e) => {
  if (!props.images.length || props.images.length <= 1) return
  
  switch (e.key) {
    case 'ArrowLeft':
    case 'PageUp':
      e.preventDefault()
      prev()
      break
    case 'ArrowRight':
    case 'PageDown':
      e.preventDefault()
      next()
      break
  }
}

onMounted(() => {
  window.addEventListener('keydown', handleKeydown)
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', handleKeydown)
  currentIndex.value = 0
})

const onImageClick = () => {
  showPreview.value = true
}
</script>

<style scoped>
.carousel-btn {
  @apply absolute top-1/2;
  transform: translateY(-50%);
  width: 30px;
  height: 30px;
  border-radius: 9999px;
  background-color: rgba(0, 0, 0, 0.2);
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  transition: all 0.2s;
}

.carousel-btn:hover {
  background-color: rgba(0, 0, 0, 0.4);
}

/* 添加计数器的动画效果 */
.carousel-counter-enter-active,
.carousel-counter-leave-active {
  transition: opacity 0.2s ease;
}

.carousel-counter-enter-from,
.carousel-counter-leave-to {
  opacity: 0;
}

/* 添加图片容器过渡效果 */
.carousel-container {
  transition: width 0.3s ease-in-out;
}

/* 添加淡入淡出动画 */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
