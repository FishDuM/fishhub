<template>
  <div v-if="visible" class="fixed inset-0 flex items-center justify-center" style="z-index: 9999;">

    <div class="absolute inset-0 bg-gray-800/25" style="z-index: 9998;" @click="onClose"></div>

    <Transition
      name="zoom"
      appear
      @before-enter="onBeforeEnter"
      @enter="onEnter"
      @leave="onLeave"
    >
      <div class="login-dialog relative bg-white rounded-lg w-[480px]" style="z-index: 9999;" ref="modalRef">

        <button
          class="absolute right-6 top-6 text-gray-400 hover:text-gray-600 hover:bg-gray-100 rounded-full p-2"
          @click="onClose"
        >
          <svg class="w-5 h-5" viewBox="0 0 24 24" fill="none" stroke="currentColor">
            <path d="M6 18L18 6M6 6l12 12" stroke-width="2" stroke-linecap="round"/>
          </svg>
        </button>

        <div class="text-center px-[72px] py-10">
          <h2 class="text-[18px] font-bold mb-8">{{ loginMode === 'login' ? '账号登录' : '新用户注册' }}</h2>

          <div class="flex mb-6 border-b border-gray-100">
            <button
              type="button"
              class="flex-1 pb-3 text-[15px] font-medium transition-colors border-b-2"
              :class="loginMode === 'login' ? 'text-[var(--color-primary)] border-[var(--color-primary)]' : 'text-gray-500 border-transparent'"
              @click="switchMode('login')"
            >
              登录
            </button>
            <button
              type="button"
              class="flex-1 pb-3 text-[15px] font-medium transition-colors border-b-2"
              :class="loginMode === 'register' ? 'text-[var(--color-primary)] border-[var(--color-primary)]' : 'text-gray-500 border-transparent'"
              @click="switchMode('register')"
            >
              注册
            </button>
          </div>

          <!-- 手机号 -->
          <div class="relative mb-4">
            <div class="flex items-center bg-[var(--color-input-surface)] rounded-3xl h-[48px] px-4">
              <div class="flex items-center text-[15px] text-gray-800">
                <span>+86</span>
                <div class="mx-2 w-[1px] h-[14px] bg-[var(--color-input-divider)]"></div>
              </div>
              <input
                type="text"
                placeholder="输入手机号"
                class="flex-1 outline-none text-[15px] ml-1 bg-transparent caret-[var(--color-primary)]"
                v-model="formattedPhone"
                maxlength="13"
                autocomplete="tel"
                @input="formatPhoneNumber"
              >
            </div>
          </div>

          <!-- 密码 -->
          <div class="relative mb-4">
            <div class="flex items-center bg-[var(--color-input-surface)] rounded-3xl h-[48px] px-4">
              <input
                v-model="password"
                type="password"
                :placeholder="loginMode === 'register' ? '设置密码 (6-20位)' : '输入密码'"
                class="flex-1 outline-none text-[15px] bg-transparent caret-[var(--color-primary)]"
                autocomplete="current-password"
                maxlength="20"
              >
            </div>
          </div>

          <!-- 确认密码（仅注册模式） -->
          <div v-if="loginMode === 'register'" class="relative mb-4">
            <div class="flex items-center bg-[var(--color-input-surface)] rounded-3xl h-[48px] px-4">
              <input
                v-model="confirmPassword"
                type="password"
                placeholder="确认密码"
                class="flex-1 outline-none text-[15px] bg-transparent caret-[var(--color-primary)]"
                autocomplete="new-password"
                maxlength="20"
              >
            </div>
          </div>

          <!-- 图形验证码 -->
          <div class="relative mb-6">
            <div class="flex items-center bg-[var(--color-input-surface)] rounded-3xl h-[48px] px-4">
              <input
                type="text"
                placeholder="输入图形验证码"
                class="flex-1 outline-none text-[15px] bg-transparent caret-[var(--color-primary)]"
                v-model="captchaCode"
                maxlength="4"
                @keyup.enter="handleSubmit"
              >
              <div class="ml-2 flex items-center cursor-pointer select-none" @click="fetchCaptcha" title="点击刷新验证码">
                <img
                  v-if="captchaBase64"
                  :src="captchaBase64"
                  alt="验证码"
                  class="h-[36px] rounded object-contain border border-gray-200"
                >
                <span v-else class="text-xs text-gray-400 px-2">加载中...</span>
              </div>
            </div>
          </div>

          <!-- 提交按钮 -->
          <button
            class="w-full bg-[var(--color-primary)] text-[var(--color-primary-contrast)] rounded-full h-[48px] text-[16px] cursor-pointer
            font-bold hover:bg-opacity-90 mt-2 transition-opacity disabled:opacity-50 disabled:cursor-not-allowed flex items-center justify-center"
            :disabled="isSubmitting"
            @click="handleSubmit"
          >
            <svg v-if="isSubmitting" class="animate-spin -ml-1 mr-2 h-5 w-5 text-white" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
              <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
              <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8H4z"></path>
            </svg>
            <span>{{ isSubmitting ? (loginMode === 'login' ? '登录中...' : '注册中...') : (loginMode === 'login' ? '登录' : '注册并登录') }}</span>
          </button>

          <!-- 协议勾选 -->
          <div class="mt-5 text-xs text-gray-700">
            <div class="flex justify-center gap-1">
              <input type="checkbox" class="w-4 h-4" v-model="agreeTerms">
              <div>
                <span>我已阅读并同意</span>
                <a href="#" class="text-[var(--color-link)] cursor-pointer">《用户协议》</a>
                <a href="#" class="text-[var(--color-link)] cursor-pointer">《隐私政策》</a>
                <a href="#" class="text-[var(--color-link)] cursor-pointer">《儿童/青少年个人信息保护规则》</a>
              </div>
            </div>
          </div>

          <!-- 底部切换提示 -->
          <div class="mt-6 text-[14px] text-gray-500">
            <span v-if="loginMode === 'login'">
              没有账号？<a class="text-[var(--color-primary)] font-medium cursor-pointer" @click="switchMode('register')">去注册</a>
            </span>
            <span v-else>
              已有账号？<a class="text-[var(--color-primary)] font-medium cursor-pointer" @click="switchMode('login')">去登录</a>
            </span>
          </div>
        </div>
      </div>
    </Transition>

    <TermsConfirmModal
      v-model:visible="showTermsConfirm"
      @confirm="handleConfirmTerms"
    />
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import gsap from 'gsap'
import { message } from '@/utils/message'
import TermsConfirmModal from './TermsConfirmModal.vue'
import { login, register, getCaptcha } from '@/api/auth'
import { getUserProfile } from '@/api/user'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()

const props = defineProps({
  visible: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['update:visible'])

const phone = ref('')
const formattedPhone = ref('')
const password = ref('')
const confirmPassword = ref('')
const captchaKey = ref('')
const captchaCode = ref('')
const captchaBase64 = ref('')
const loginMode = ref('login') // 'login' | 'register'
const agreeTerms = ref(false)

const modalRef = ref(null)
const showTermsConfirm = ref(false)
const isSubmitting = ref(false)

const onClose = () => {
  isSubmitting.value = false
  emit('update:visible', false)
}

const isPhoneValid = computed(() => {
  return phone.value.length === 11 && /^1[3-9]\d{9}$/.test(phone.value)
})

const fetchCaptcha = () => {
  getCaptcha().then(res => {
    if (res && res.success && res.data) {
      captchaKey.value = res.data.captchaKey || ''
      captchaBase64.value = res.data.captchaBase64 || ''
      captchaCode.value = ''
    }
  }).catch(() => {
    // 忽略异常
  })
}

const switchMode = (mode) => {
  if (loginMode.value === mode) return
  loginMode.value = mode
  password.value = ''
  confirmPassword.value = ''
  // 若已加载过图形验证码，则复用现有验证码，不再重新请求后端
  if (!captchaKey.value || !captchaBase64.value) {
    fetchCaptcha()
  }
}

watch(() => props.visible, (val) => {
  if (val) {
    if (!captchaKey.value || !captchaBase64.value) {
      fetchCaptcha()
    }
  }
})

const handleSubmit = () => {
  if (isSubmitting.value) return
  if (!agreeTerms.value) {
    showTermsConfirm.value = true
    return
  }
  doSubmit()
}

const handleConfirmTerms = () => {
  if (isSubmitting.value) return
  agreeTerms.value = true
  doSubmit()
}

const doSubmit = async () => {
  if (isSubmitting.value) return
  if (!isPhoneValid.value) {
    message.warning('请输入正确的11位手机号')
    return
  }

  if (!password.value || password.value.length < 6) {
    message.warning('密码长度不能少于6位')
    return
  }

  if (loginMode.value === 'register') {
    if (password.value !== confirmPassword.value) {
      message.warning('两次输入的密码不一致')
      return
    }
  }

  if (!captchaCode.value || captchaCode.value.length !== 4) {
    message.warning('请输入4位图形验证码')
    return
  }

  if (!captchaKey.value) {
    message.warning('验证码已失效，请重新获取')
    fetchCaptcha()
    return
  }

  const handleAuthError = (res, defaultMsg) => {
    const errorMsg = res?.message || res?.errorMessage || res?.msg || defaultMsg
    message.error(errorMsg)
    // 只有当验证码过期(AUTH-20000)或输错达到10次上限已自动失效(AUTH-20002)时，才刷新更换验证码
    if (res?.errorCode === 'AUTH-20000' || res?.errorCode === 'AUTH-20002') {
      fetchCaptcha()
    } else {
      // 验证码输入错误或密码错误时保留当前图形验证码，仅清空输入框方便重试
      captchaCode.value = ''
    }
  }

  isSubmitting.value = true

  try {
    if (loginMode.value === 'register') {
      const res = await register({
        phone: phone.value,
        password: password.value,
        captchaKey: captchaKey.value,
        captchaCode: captchaCode.value
      })

      if (!res.success) {
        handleAuthError(res, '注册失败')
        return
      }

      userStore.setToken(res.data)
      try {
        const profileRes = await getUserProfile()
        if (profileRes && profileRes.success && profileRes.data) {
          userStore.setProfile(profileRes.data)
        }
      } catch (e) {
        console.warn('获取用户资料失败:', e)
      }

      message.success('注册成功并已登录')
      onClose()
    } else {
      const res = await login({
        phone: phone.value,
        password: password.value,
        captchaKey: captchaKey.value,
        captchaCode: captchaCode.value
      })

      if (!res.success) {
        handleAuthError(res, '手机号或密码错误')
        return
      }

      userStore.setToken(res.data)
      try {
        const profileRes = await getUserProfile()
        if (profileRes && profileRes.success && profileRes.data) {
          userStore.setProfile(profileRes.data)
        }
      } catch (e) {
        console.warn('获取用户资料失败:', e)
      }

      message.success('登录成功')
      onClose()
    }
  } catch (err) {
    handleAuthError(err?.response?.data, loginMode.value === 'register' ? '注册请求失败' : '登录请求失败')
  } finally {
    isSubmitting.value = false
  }
}

const onBeforeEnter = (el) => {
  gsap.set(el, {
    opacity: 0,
    scale: 0.5,
    y: 40
  })
}

const onEnter = (el) => {
  gsap.to(el, {
    opacity: 1,
    scale: 1,
    y: 0,
    duration: 0.3,
    ease: 'back.out(1.7)'
  })
}

const onLeave = (el) => {
  gsap.to(el, {
    opacity: 0,
    scale: 0.5,
    duration: 0.2,
    ease: 'power2.in'
  })
}

const formatPhoneNumber = (event) => {
  let value = event.target.value.replace(/\D/g, '')
  if (value.length > 11) {
    value = value.slice(0, 11)
  }

  if (value.length > 7) {
    formattedPhone.value = `${value.slice(0, 3)} ${value.slice(3, 7)} ${value.slice(7)}`
  } else if (value.length > 3) {
    formattedPhone.value = `${value.slice(0, 3)} ${value.slice(3)}`
  } else {
    formattedPhone.value = value
  }

  phone.value = value
}
</script>

<style scoped>
.zoom-move {
  transition: transform 0.3s ease-out;
}

input:-webkit-autofill,
input:-webkit-autofill:hover,
input:-webkit-autofill:focus {
  /* 覆盖 Chrome 自动填充注入的背景和文字颜色。 */
  -webkit-box-shadow: 0 0 0 30px var(--color-input-surface) inset !important;
  -webkit-text-fill-color: var(--color-primary-label) !important;
}

@media (max-width: 767px) {
  .login-dialog { width: calc(100vw - 32px); border-radius: 16px; }
  .login-dialog > div:last-child { padding: 32px 24px; }
  .login-dialog h2 { margin-bottom: 32px; }
}
</style>
