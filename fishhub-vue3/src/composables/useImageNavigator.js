import { onBeforeUnmount } from 'vue'

const WHEEL_DELAY = 200

export const useImageNavigator = (getImages, currentIndex) => {
  let wheelTimeout

  const previous = () => {
    const images = getImages()
    if (images.length <= 1) return
    currentIndex.value = currentIndex.value === 0 ? images.length - 1 : currentIndex.value - 1
  }

  const next = () => {
    const images = getImages()
    if (images.length <= 1) return
    currentIndex.value = (currentIndex.value + 1) % images.length
  }

  const handleWheel = (event) => {
    event.preventDefault()
    if (wheelTimeout) return
    event.deltaY > 0 ? next() : previous()
    wheelTimeout = window.setTimeout(() => {
      wheelTimeout = undefined
    }, WHEEL_DELAY)
  }

  onBeforeUnmount(() => {
    if (wheelTimeout) {
      window.clearTimeout(wheelTimeout)
    }
  })

  return { previous, next, handleWheel }
}
