import { ref } from 'vue'

export const isDark = ref(false)

const applyTheme = (dark) => {
  isDark.value = dark
  document.documentElement.classList.toggle('dark', dark)
  document.documentElement.style.colorScheme = dark ? 'dark' : 'light'
}

export const initializeTheme = () => {
  const savedTheme = window.localStorage.getItem('fishhub-theme')
  applyTheme(savedTheme ? savedTheme === 'dark' : window.matchMedia('(prefers-color-scheme: dark)').matches)
}

export const toggleTheme = () => {
  const nextTheme = !isDark.value
  applyTheme(nextTheme)
  window.localStorage.setItem('fishhub-theme', nextTheme ? 'dark' : 'light')
}
