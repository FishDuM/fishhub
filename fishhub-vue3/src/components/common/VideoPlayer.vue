<template>
  <div
    class="video-player-container w-full"
    ref="containerRef"
    @mousemove="handleMouseMove"
    @mouseleave="hideControls"
  >

    <video
      ref="videoRef"
      class="video-element"
      :src="videoUrl"
      :poster="poster"
      :muted="muted"
      :autoplay="autoplay"
      :loop="loop"
      :controls="showNativeControls"
      :playsinline="playsinline"
      @play="onPlay"
      @pause="onPause"
      @ended="onEnded"
      @timeupdate="onTimeUpdate"
      @loadedmetadata="onMetadataLoaded"
      @click="togglePlay"
      @error="onError"
    ></video>


    <div
      v-if="useCustomControls"
      class="custom-controls"
      :class="{ 'controls-hidden': !controlsVisible && isPlaying }"
    >

      <div class="progress-container">
        <div
          class="progress-bar"
          @click="seekToPosition"
          @mousedown="startDragging"
        >
          <div class="progress-background"></div>
          <div class="progress-fill" :style="{ width: `${displayProgress}%` }"></div>
          <div
            class="progress-handle"
            :style="{ left: `${displayProgress}%` }"
            :class="{ 'handle-active': isDragging }"
          ></div>
          <div
            v-if="showProgressTooltip || isDragging"
            class="progress-tooltip"
            :style="{ left: `${tooltipPosition}%` }"
          >
            {{ formatTime(previewTime) }}
          </div>
        </div>
      </div>


      <div class="controls-row">

        <button
          class="play-pause-btn"
          @click.stop="togglePlay"
        >
          <svg v-if="isPlaying" class="pause-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor">
            <rect x="6" y="4" width="4" height="16" rx="1" fill="currentColor" />
            <rect x="14" y="4" width="4" height="16" rx="1" fill="currentColor" />
          </svg>
          <svg v-else class="play-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor">
            <path d="M8 5v14l11-7z" fill="currentColor" />
          </svg>
        </button>


        <div class="time-display">
          {{ isDragging ? formatTime(previewTime) : formatTime(currentTime) }} / {{ formatTime(duration) }}
        </div>


        <div class="volume-container">
          <button class="volume-btn" @click.stop="toggleMute">
            <svg v-if="isMuted" class="volume-muted-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor">
              <path d="M11 5L6 9H2v6h4l5 4V5z" fill="currentColor" />
              <path d="M23 9l-6 6M17 9l6 6" stroke="currentColor" stroke-width="2" stroke-linecap="round" />
            </svg>
            <svg v-else class="volume-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor">
              <path d="M11 5L6 9H2v6h4l5 4V5z" fill="currentColor" />
              <path d="M15.54 8.46a5 5 0 0 1 0 7.07" stroke="currentColor" stroke-width="2" stroke-linecap="round" />
              <path d="M19.07 4.93a10 10 0 0 1 0 14.14" stroke="currentColor" stroke-width="2" stroke-linecap="round" />
            </svg>
          </button>
          <div class="volume-slider-container">
            <input
              type="range"
              min="0"
              max="1"
              step="0.01"
              :value="effectiveVolume"
              class="volume-slider"
              @input="updateVolume"
            />
            <div class="volume-slider-fill" :style="{ width: `${volumePercent}%` }"></div>
            <div class="volume-tooltip" :style="{ left: `${volumePercent}%` }">
              {{ Math.round(volumePercent) }}%
            </div>
          </div>
        </div>


        <button class="fullscreen-btn" @click.stop="toggleFullscreen">
          <svg v-if="isFullscreen" class="exit-fullscreen-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor">
            <path d="M8 3v3a2 2 0 0 1-2 2H3m18 0h-3a2 2 0 0 1-2-2V3m0 18v-3a2 2 0 0 1 2-2h3M3 16h3a2 2 0 0 1 2 2v3" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
          </svg>
          <svg v-else class="fullscreen-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor">
            <path d="M8 3H5a2 2 0 0 0-2 2v3m18 0V5a2 2 0 0 0-2-2h-3m0 18h3a2 2 0 0 0 2-2v-3M3 16v3a2 2 0 0 0 2 2h3" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
          </svg>
        </button>
      </div>
    </div>


    <div
      v-if="useCustomControls && !isPlaying && !isEnded"
      class="big-play-button"
      @click.stop="togglePlay"
    >
      <svg class="big-play-icon" viewBox="0 0 24 24" fill="none">
        <path d="M8 5v14l11-7z" fill="white"></path>
      </svg>
    </div>


    <div
      v-if="useCustomControls && isEnded"
      class="replay-button"
      @click.stop="replay"
    >
      <svg class="replay-icon" viewBox="0 0 24 24" fill="none">
        <circle cx="12" cy="12" r="11" fill="rgba(0, 0, 0, 0.5)"/>
        <path d="M12 7C9.23858 7 7 9.23858 7 12C7 14.7614 9.23858 17 12 17C13.6569 17 15.1372 16.1652 16 14.8V16.5C16 16.7761 16.2239 17 16.5 17C16.7761 17 17 16.7761 17 16.5V13.5C17 13.2239 16.7761 13 16.5 13H13.5C13.2239 13 13 13.2239 13 13.5C13 13.7761 13.2239 14 13.5 14H15.4286C14.7412 15.1935 13.4593 16 12 16C9.79086 16 8 14.2091 8 12C8 9.79086 9.79086 8 12 8C14.2091 8 16 9.79086 16 12C16 12.2761 16.2239 12.5 16.5 12.5C16.7761 12.5 17 12.2761 17 12C17 9.23858 14.7614 7 12 7Z" fill="white"/>
      </svg>
    </div>


    <div v-if="isLoading" class="loading-indicator">
      <div class="spinner"></div>
    </div>


    <div v-if="hasError" class="error-message">
      <svg class="error-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor">
        <circle cx="12" cy="12" r="10" stroke="red" stroke-width="2" />
        <path d="M12 8v4M12 16h.01" stroke="red" stroke-width="2" stroke-linecap="round" />
      </svg>
      <span>视频加载失败</span>
      <button @click="retryLoading" class="retry-button">重试</button>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, onBeforeUnmount } from 'vue'

const props = defineProps({
  videoUrl: {
    type: String,
    required: true
  },
  poster: {
    type: String,
    default: ''
  },
  autoplay: {
    type: Boolean,
    default: false
  },
  loop: {
    type: Boolean,
    default: false
  },
  muted: {
    type: Boolean,
    default: false
  },
  showControls: {
    type: Boolean,
    default: false
  },
  useCustomControls: {
    type: Boolean,
    default: true
  },
  playsinline: {
    type: Boolean,
    default: true
  },
  initialVolume: {
    type: Number,
    default: 1,
    validator: (value) => value >= 0 && value <= 1
  }
})

const emit = defineEmits([
  'play',
  'pause',
  'ended',
  'timeupdate',
  'volumechange',
  'error',
  'loaded'
])

const containerRef = ref(null)
const videoRef = ref(null)

const isPlaying = ref(false)
const isEnded = ref(false)
const isLoading = ref(true)
const hasError = ref(false)
const duration = ref(0)
const currentTime = ref(0)
const progress = ref(0)
const volume = ref(props.initialVolume)
const isMuted = ref(props.muted)
const isFullscreen = ref(false)

const controlsVisible = ref(true)
let controlsTimeout = null
let mouseMoveTimeout = null

const isDragging = ref(false)
const showProgressTooltip = ref(false)
const tooltipPosition = ref(0)
const previewTime = ref(0)
const dragProgress = ref(0)

const lastVolume = ref(props.initialVolume)

const videoElement = computed(() => videoRef.value)

const showNativeControls = computed(() => {
  return props.showControls && !props.useCustomControls
})

const effectiveVolume = computed(() => {
  return isMuted.value ? 0 : volume.value
})

const volumePercent = computed(() => {
  return isMuted.value ? 0 : volume.value * 100
})

const displayProgress = computed(() => {
  return isDragging.value ? dragProgress.value : progress.value
})

const togglePlay = () => {
  if (!videoRef.value) return

  if (isPlaying.value) {
    videoRef.value.pause()
  } else {
    videoRef.value.play().catch(error => {
      console.error('播放失败:', error)
      hasError.value = true
    })
  }
}

const replay = () => {
  if (!videoRef.value) return

  isEnded.value = false
  videoRef.value.currentTime = 0
  videoRef.value.play()
    .then(() => {
      isPlaying.value = true
      if (isPlaying.value) {
        hideControlsDelayed()
      }
    })
    .catch(error => {
      console.error('播放失败:', error)
      hasError.value = true
    })
}

const toggleMute = () => {
  if (!videoRef.value) return

  if (isMuted.value) {
    isMuted.value = false
    videoRef.value.muted = false

    if (lastVolume.value === 0) {
      volume.value = 0.5
      videoRef.value.volume = 0.5
    } else {
      volume.value = lastVolume.value
      videoRef.value.volume = lastVolume.value
    }
  } else {
    lastVolume.value = volume.value
    isMuted.value = true
    videoRef.value.muted = true
  }

  emit('volumechange', { volume: effectiveVolume.value, muted: isMuted.value })
}

const updateVolume = (e) => {
  if (!videoRef.value) return

  const newVolume = parseFloat(e.target.value)
  volume.value = newVolume
  videoRef.value.volume = newVolume

  if (newVolume > 0 && isMuted.value) {
    isMuted.value = false
    videoRef.value.muted = false
  }

  if (newVolume === 0 && !isMuted.value) {
    isMuted.value = true
    videoRef.value.muted = true
  }

  if (newVolume > 0) {
    lastVolume.value = newVolume
  }

  emit('volumechange', { volume: newVolume, muted: isMuted.value })
}

const startDragging = (e) => {
  if (e.button !== 0) return

  isDragging.value = true
  updateDragPosition(e)

  // 指针可能移出进度条，监听 document 才能持续拖动并可靠结束。
  document.addEventListener('mousemove', handleDragMove)
  document.addEventListener('mouseup', stopDragging)

  e.preventDefault()
  e.stopPropagation()
}

const handleDragMove = (e) => {
  if (!isDragging.value) return
  updateDragPosition(e)

  if (videoRef.value) {
    dragProgress.value = (previewTime.value / duration.value) * 100
  }
}

const stopDragging = () => {
  if (!isDragging.value) return

  if (videoRef.value) {
    videoRef.value.currentTime = previewTime.value
  }

  isDragging.value = false
  dragProgress.value = 0

  document.removeEventListener('mousemove', handleDragMove)
  document.removeEventListener('mouseup', stopDragging)
}

const updateDragPosition = (e) => {
  if (!videoRef.value || !containerRef.value) return

  const progressBar = containerRef.value.querySelector('.progress-bar')
  if (!progressBar) return

  const rect = progressBar.getBoundingClientRect()
  const offsetX = e.clientX - rect.left
  const width = rect.width

  let percent = (offsetX / width) * 100
  percent = Math.max(0, Math.min(100, percent))

  tooltipPosition.value = percent

  previewTime.value = (percent / 100) * duration.value
}

const seekToPosition = (e) => {
  if (isDragging.value) return

  updateDragPosition(e)

  if (videoRef.value) {
    videoRef.value.currentTime = previewTime.value
  }
}

const showProgressPreview = (e) => {
  showProgressTooltip.value = true
  updateDragPosition(e)
}

const hideProgressPreview = () => {
  if (!isDragging.value) {
    showProgressTooltip.value = false
  }
}

const toggleFullscreen = () => {
  if (!containerRef.value) return

  if (!isFullscreen.value) {
    if (containerRef.value.requestFullscreen) {
      containerRef.value.requestFullscreen()
    } else if (containerRef.value.webkitRequestFullscreen) {
      containerRef.value.webkitRequestFullscreen()
    } else if (containerRef.value.msRequestFullscreen) {
      containerRef.value.msRequestFullscreen()
    }
  } else {
    if (document.exitFullscreen) {
      document.exitFullscreen()
    } else if (document.webkitExitFullscreen) {
      document.webkitExitFullscreen()
    } else if (document.msExitFullscreen) {
      document.msExitFullscreen()
    }
  }
}

const handleMouseMove = () => {
  if (mouseMoveTimeout) {
    clearTimeout(mouseMoveTimeout)
  }

  showControls()

  if (isPlaying.value) {
    mouseMoveTimeout = setTimeout(() => {
      hideControls()
    }, 3000)
  }
}

const showControls = () => {
  controlsVisible.value = true

  if (controlsTimeout) {
    clearTimeout(controlsTimeout)
    controlsTimeout = null
  }
}

const hideControls = () => {
  if (isPlaying.value) {
    controlsVisible.value = false
  }
}

const retryLoading = () => {
  if (!videoRef.value) return

  hasError.value = false
  isLoading.value = true
  videoRef.value.load()
}

const formatTime = (seconds) => {
  if (isNaN(seconds) || seconds === Infinity) return '00:00'

  const mins = Math.floor(seconds / 60)
  const secs = Math.floor(seconds % 60)

  return `${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`
}

const onPlay = () => {
  isPlaying.value = true
  isEnded.value = false

  if (controlsTimeout) {
    clearTimeout(controlsTimeout)
  }

  controlsTimeout = setTimeout(() => {
    hideControls()
  }, 3000)

  emit('play')
}

const onPause = () => {
  isPlaying.value = false

  showControls()

  emit('pause')
}

const onEnded = () => {
  isPlaying.value = false
  isEnded.value = true
  controlsVisible.value = true

  emit('ended')
}

const onTimeUpdate = () => {
  if (!videoRef.value || isDragging.value) return

  currentTime.value = videoRef.value.currentTime

  if (duration.value > 0) {
    progress.value = (currentTime.value / duration.value) * 100
  }

  emit('timeupdate', { currentTime: currentTime.value, progress: progress.value })
}

const onMetadataLoaded = () => {
  if (!videoRef.value) return

  duration.value = videoRef.value.duration
  isLoading.value = false
  emit('loaded', { duration: duration.value })
}

const onError = (error) => {
  console.error('视频加载错误:', error)
  isLoading.value = false
  hasError.value = true
  emit('error', error)
}

const handleFullscreenChange = () => {
  isFullscreen.value = !!(
    document.fullscreenElement ||
    document.webkitFullscreenElement ||
    document.msFullscreenElement
  )
}

onMounted(() => {
  if (videoRef.value) {
    videoRef.value.volume = volume.value

    if (props.autoplay) {
      controlsTimeout = setTimeout(() => {
        hideControls()
      }, 3000)
    }
  }

  document.addEventListener('fullscreenchange', handleFullscreenChange)
  document.addEventListener('webkitfullscreenchange', handleFullscreenChange)
  document.addEventListener('msfullscreenchange', handleFullscreenChange)
  document.addEventListener('keydown', handleKeyDown)

  const progressBar = containerRef.value?.querySelector('.progress-bar')
  if (progressBar) {
    progressBar.addEventListener('mousemove', showProgressPreview)
    progressBar.addEventListener('mouseleave', hideProgressPreview)
  }
})

onBeforeUnmount(() => {
  if (controlsTimeout) {
    clearTimeout(controlsTimeout)
  }

  if (mouseMoveTimeout) {
    clearTimeout(mouseMoveTimeout)
  }

  document.removeEventListener('fullscreenchange', handleFullscreenChange)
  document.removeEventListener('webkitfullscreenchange', handleFullscreenChange)
  document.removeEventListener('msfullscreenchange', handleFullscreenChange)
  document.removeEventListener('keydown', handleKeyDown)

  const progressBar = containerRef.value?.querySelector('.progress-bar')
  if (progressBar) {
    progressBar.removeEventListener('mousemove', showProgressPreview)
    progressBar.removeEventListener('mouseleave', hideProgressPreview)
  }

  document.removeEventListener('mousemove', handleDragMove)
  document.removeEventListener('mouseup', stopDragging)
})

const handleKeyDown = (event) => {
  // 仅在播放器内部获得焦点时响应，避免抢占页面级快捷键。
  if (!containerRef.value || !containerRef.value.contains(document.activeElement)) return

  switch (event.key) {
    case ' ':
    case 'k':
      event.preventDefault()
      togglePlay()
      break
    case 'ArrowRight':
      event.preventDefault()
      if (videoRef.value) {
        videoRef.value.currentTime = Math.min(videoRef.value.currentTime + 5, duration.value)
      }
      break
    case 'ArrowLeft':
      event.preventDefault()
      if (videoRef.value) {
        videoRef.value.currentTime = Math.max(videoRef.value.currentTime - 5, 0)
      }
      break
    case 'm':
      event.preventDefault()
      toggleMute()
      break
    case 'f':
      event.preventDefault()
      toggleFullscreen()
      break
  }
}

watch(() => props.muted, (newValue) => {
  if (videoRef.value) {
    videoRef.value.muted = newValue
    isMuted.value = newValue
  }
})

defineExpose({
  play: () => {
    if (videoRef.value) {
      videoRef.value.play().catch(error => {
        console.error('播放失败:', error)
        hasError.value = true
      })
    }
  },
  pause: () => {
    if (videoRef.value) {
      videoRef.value.pause()
    }
  },
  stop: () => {
    if (videoRef.value) {
      videoRef.value.pause()
      videoRef.value.currentTime = 0
    }
  },
  seek: (time) => {
    if (videoRef.value) {
      videoRef.value.currentTime = time
    }
  },
  setVolume: (value) => {
    if (videoRef.value && value >= 0 && value <= 1) {
      volume.value = value
      videoRef.value.volume = value
    }
  },
  mute: () => {
    if (videoRef.value) {
      isMuted.value = true
      videoRef.value.muted = true
    }
  },
  unmute: () => {
    if (videoRef.value) {
      isMuted.value = false
      videoRef.value.muted = false
    }
  },
  toggleFullscreen,
  videoElement
})
</script>

<style scoped>
.video-player-container {
  position: relative;
  width: 100%;
  min-width: 480px;
  max-width: 800px;
  height: 100%;
  background-color: #000;
  overflow: hidden;
  display: flex;
  justify-content: center;
  align-items: center;
}



.video-element {
  width: 100%;
  height: 100%;
  object-fit: contain;
  background-color: #000;
}

.custom-controls {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  background: linear-gradient(transparent, rgba(0, 0, 0, 0.7));
  padding: 0;
  display: flex;
  flex-direction: column;
  transition: opacity 0.3s ease;
  z-index: 2;
}

.controls-hidden {
  opacity: 0;
  pointer-events: none;
}

.controls-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px;
}

.progress-container {
  width: 100%;
  padding: 10px 10px 0 10px;
}

.progress-bar {
  height: 4px;
  position: relative;
  cursor: pointer;
  overflow: visible;
  transition: height 0.2s;
  border-radius: 2px;
}

.progress-background {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background-color: rgba(255, 255, 255, 0.3);
  border-radius: 2px;
}

.progress-fill {
  position: absolute;
  top: 0;
  left: 0;
  height: 100%;
  background-color: white;
  border-radius: 2px;
  pointer-events: none;
  z-index: 2;
}

.progress-bar:hover {
  height: 6px;
}

.progress-bar:hover .progress-background,
.progress-bar:hover .progress-fill {
  height: 100%;
}

.time-display {
  font-size: 12px;
  color: white;
  margin-right: auto;
}

.play-pause-btn,
.volume-btn,
.fullscreen-btn {
  background: none;
  border: none;
  color: white;
  cursor: pointer;
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  transition: background-color 0.2s;
}

.play-pause-btn:hover,
.volume-btn:hover,
.fullscreen-btn:hover {
  background-color: rgba(255, 255, 255, 0.2);
}

.play-icon,
.pause-icon,
.volume-icon,
.volume-muted-icon,
.fullscreen-icon,
.exit-fullscreen-icon {
  width: 20px;
  height: 20px;
}

.volume-container {
  display: flex;
  align-items: center;
  gap: 5px;
  position: relative;
}

.volume-slider-container {
  width: 60px;
  height: 4px;
  position: relative;
  display: flex;
  align-items: center;
}

.volume-slider {
  width: 100%;
  height: 100%;
  -webkit-appearance: none;
  background-color: transparent;
  border-radius: 2px;
  outline: none;
  position: absolute;
  top: 0;
  left: 0;
  margin: 0;
  z-index: 3;
  cursor: pointer;
}

.volume-slider-container::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background-color: rgba(255, 255, 255, 0.3);
  border-radius: 2px;
  z-index: 1;
}

.volume-slider-fill {
  position: absolute;
  top: 0;
  left: 0;
  height: 100%;
  background-color: white;
  border-radius: 2px;
  pointer-events: none;
  z-index: 2;
  transition: width 0.1s ease;
}

.volume-slider::-webkit-slider-thumb {
  -webkit-appearance: none;
  width: 12px;
  height: 12px;
  border-radius: 50%;
  background-color: white;
  cursor: pointer;
  position: relative;
  z-index: 4;
}

.volume-slider::-moz-range-thumb {
  width: 12px;
  height: 12px;
  border-radius: 50%;
  background-color: white;
  cursor: pointer;
  border: none;
  position: relative;
  z-index: 4;
}

.volume-tooltip {
  position: absolute;
  top: -25px;
  transform: translateX(-50%);
  background-color: rgba(0, 0, 0, 0.7);
  color: white;
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 10px;
  opacity: 0;
  transition: opacity 0.2s;
  pointer-events: none;
  white-space: nowrap;
  z-index: 5;
}

.volume-slider-container:hover .volume-tooltip {
  opacity: 1;
}

:fullscreen .video-player-container {
  width: 100vw;
  height: 100vh;
}

:-webkit-full-screen .video-player-container {
  width: 100vw;
  height: 100vh;
}

:-ms-fullscreen .video-player-container {
  width: 100vw;
  height: 100vh;
}

.progress-handle {
  position: absolute;
  top: 50%;
  transform: translate(-50%, -50%);
  width: 12px;
  height: 12px;
  border-radius: 50%;
  background-color: white;
  opacity: 0;
  transition: opacity 0.2s;
  pointer-events: none;
  z-index: 3;
}

.progress-bar:hover .progress-handle {
  opacity: 1;
}

.handle-active {
  opacity: 1 !important;
  transform: translate(-50%, -50%) scale(1.2);
  box-shadow: 0 0 0 2px rgba(255, 255, 255, 0.3);
}

.progress-tooltip {
  position: absolute;
  top: -30px;
  transform: translateX(-50%);
  background-color: rgba(0, 0, 0, 0.7);
  color: white;
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 12px;
  pointer-events: none;
  white-space: nowrap;
  z-index: 4;
}

.big-play-button {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  cursor: pointer;
  z-index: 2;
  transition: transform 0.2s;
  width: 64px;
  height: 64px;
  border-radius: 50%;
  background-color: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
}

.big-play-button:hover {
  transform: translate(-50%, -50%) scale(1.1);
  background-color: rgba(0, 0, 0, 0.7);
}

.big-play-icon {
  width: 32px;
  height: 32px;
}

.replay-button {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  cursor: pointer;
  z-index: 2;
  transition: transform 0.2s;
  width: 64px;
  height: 64px;
  border-radius: 50%;
  background-color: rgba(0, 0, 0, 0.3);
  display: flex;
  align-items: center;
  justify-content: center;
}

.replay-button:hover {
  transform: translate(-50%, -50%) scale(1.1);
  background-color: rgba(0, 0, 0, 0.5);
}

.replay-icon {
  width: 64px;
  height: 64px;
}
</style>
