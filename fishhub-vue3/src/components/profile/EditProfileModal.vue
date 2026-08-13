<template>
  <BaseModal
    :visible="visible"
    @update:visible="$emit('update:visible', $event)"
    title="编辑资料"
    width="640px"
    @confirm="handleConfirm"
  >
    <div class="p-6">

      <div class="flex items-center gap-4 mb-6">
        <UserAvatar
          :src="form.avatar"
          :alt="`${form.nickname || '当前用户'}的头像`"
          class="w-20 h-20 rounded-full object-cover border-1 border-gray-100"
        />
        <div>
          <input
            type="file"
            ref="avatarInput"
            accept="image/*"
            class="hidden"
            @change="handleAvatarChange"
          />
          <button
            class="text-sm px-4 h-9 border border-gray-200 rounded-full text-gray-600 hover:bg-gray-50"
            @click="$refs.avatarInput.click()"
          >
            更换头像
          </button>
        </div>
      </div>


      <div class="space-y-6">

        <div>
          <label class="block text-sm font-medium text-gray-700 mb-2">昵称</label>
          <div class="relative">
            <input
              v-model="form.nickname"
              type="text"
              class="w-full h-10 px-3 border border-gray-200 rounded-lg focus:outline-none focus:border-[var(--color-primary)]"
              placeholder="请输入昵称"
              maxlength="24"
              @input="updateNicknameCount"
            />
            <span class="absolute right-3 top-1/2 transform -translate-y-1/2 text-xs text-gray-400">
              {{ nicknameCount }}/24
            </span>
          </div>
          <p class="mt-1 text-xs text-gray-500">
            请设置 2-24 个字符，不包括 @<>/ 等无效字符哦
          </p>
        </div>


        <div>
          <label class="block text-sm font-medium text-gray-700 mb-2">飞鱼社区号</label>
          <div class="relative">
            <input
              v-model="form.fishhubId"
              type="text"
              class="w-full h-10 px-3 border border-gray-200 rounded-lg focus:outline-none focus:border-[var(--color-primary)]"
              placeholder="请输入飞鱼社区号"
              maxlength="15"
              @input="updateFishhubIdCount"
            />
            <span class="absolute right-3 top-1/2 transform -translate-y-1/2 text-xs text-gray-400">
              {{ fishhubIdCount }}/15
            </span>
          </div>
          <p class="mt-1 text-xs text-gray-500">
            6-15 个字符，仅可使用英文（必须）、数字、下划线
          </p>
        </div>


        <div>
          <label class="block text-sm font-medium text-gray-700 mb-2">生日</label>
          <div class="flex gap-3 text-sm">
            <select
              v-model="form.birthYear"
              class="h-10 px-3 border border-gray-200 rounded-lg focus:outline-none focus:border-[var(--color-primary)] bg-white"
            >
              <option
                v-for="year in birthYears"
                :key="year"
                :value="year"
              >{{ year }}</option>
            </select>
            <select
              v-model="form.birthMonth"
              class="h-10 px-3 border border-gray-200 rounded-lg focus:outline-none focus:border-[var(--color-primary)] bg-white"
            >
              <option
                v-for="month in months"
                :key="month.value"
                :value="month.value"
              >{{ month.label }}</option>
            </select>
            <select
              v-model="form.birthDay"
              class="h-10 px-3 border border-gray-200 rounded-lg focus:outline-none focus:border-[var(--color-primary)] bg-white"
            >
              <option
                v-for="day in getDaysInMonth(form.birthYear, form.birthMonth)"
                :key="day"
                :value="day.toString().padStart(2, '0')"
              >{{ day }}日</option>
            </select>
          </div>
        </div>


        <div>
          <label class="block text-sm font-medium text-gray-700 mb-2">简介</label>
          <div class="relative">
            <textarea
              v-model="form.introduction"
              rows="3"
              class="w-full p-3 border border-gray-200 rounded-lg focus:outline-none focus:border-[var(--color-primary)] resize-vertical min-h-[80px] max-h-[200px] pr-16"
              placeholder="介绍一下自己吧"
              maxlength="100"
              @input="updateIntroductionCount"
            ></textarea>
            <span class="absolute right-3 bottom-3 text-xs text-gray-400">
              {{ introductionCount }}/100
            </span>
          </div>
        </div>


        <div>
          <label class="block text-sm font-medium text-gray-700 mb-2">性别</label>
          <div class="flex gap-4">
            <label class="flex items-center cursor-pointer">
              <input
                v-model="form.sex"
                type="radio"
                :value="0"
                class="w-4 h-4 text-[var(--color-primary)]"
              />
              <span class="ml-2">女</span>
            </label>
            <label class="flex items-center cursor-pointer">
              <input
                v-model="form.sex"
                type="radio"
                :value="1"
                class="w-4 h-4 text-[var(--color-primary)]"
              />
              <span class="ml-2">男</span>
            </label>
          </div>
        </div>
      </div>
    </div>
  </BaseModal>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import BaseModal from '@/components/common/BaseModal.vue'
import UserAvatar from '@/components/common/UserAvatar.vue'
import { useUserStore } from '@/stores/user'
import { message } from '@/utils/message'
import { updateUserProfile, getUserProfile } from '@/api/user'

const props = defineProps({
  visible: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['update:visible', 'update-success'])

const userStore = useUserStore()

const form = ref({
  avatar: '',
  avatarFile: null,
  nickname: '',
  fishhubId: '',
  birthYear: '',
  birthMonth: '',
  birthDay: '',
  introduction: '',
  sex: 0
})

const nicknameCount = ref(0)
const fishhubIdCount = ref(0)
const introductionCount = ref(0)

const updateNicknameCount = () => {
  nicknameCount.value = form.value.nickname.length
}

const updateFishhubIdCount = () => {
  fishhubIdCount.value = form.value.fishhubId.length
}

const updateIntroductionCount = () => {
  introductionCount.value = form.value.introduction.length
}

watch(() => props.visible, (newValue) => {
  if (newValue) {
    initFormData()
  }
})

onMounted(() => {
  if (props.visible) {
    initFormData()
  }
})

const initFormData = () => {
  if (userStore.profile) {
    const profile = userStore.profile

    let birthYear = ''
    let birthMonth = ''
    let birthDay = ''

    if (profile.birthday) {
      const birthdayParts = profile.birthday.split('-')
      if (birthdayParts.length === 3) {
        birthYear = birthdayParts[0]
        birthMonth = birthdayParts[1]
        birthDay = birthdayParts[2]
      }
    }

    form.value = {
      avatar: profile.avatar || '',
      avatarFile: null,
      nickname: profile.nickname || '',
      fishhubId: profile.fishhubId || '',
      birthYear,
      birthMonth,
      birthDay,
      introduction: profile.introduction || '',
      sex: profile.sex !== null ? profile.sex : 0
    }

    nicknameCount.value = form.value.nickname.length
    fishhubIdCount.value = form.value.fishhubId.length
    introductionCount.value = form.value.introduction.length
  }
}

const birthYears = computed(() => {
  const currentYear = new Date().getFullYear()
  const years = []
  for (let i = currentYear; i >= currentYear - 100; i--) {
    years.push(i.toString())
  }
  return years
})

const getDaysInMonth = (year, month) => {
  if (!year || !month) return 31
  return new Date(year, month, 0).getDate()
}

const months = [
  { value: '01', label: '1月' },
  { value: '02', label: '2月' },
  { value: '03', label: '3月' },
  { value: '04', label: '4月' },
  { value: '05', label: '5月' },
  { value: '06', label: '6月' },
  { value: '07', label: '7月' },
  { value: '08', label: '8月' },
  { value: '09', label: '9月' },
  { value: '10', label: '10月' },
  { value: '11', label: '11月' },
  { value: '12', label: '12月' }
]

const handleConfirm = async () => {
  if (!form.value.nickname.trim()) {
    message.show('请输入昵称')
    return
  }

  const nickname = form.value.nickname.trim()
  if (nickname.length < 2 || nickname.length > 24) {
    message.show('昵称长度应为 2-24 个字符')
    return
  }

  const invalidCharsRegex = /[@<>/\\:*?"'|]/
  if (invalidCharsRegex.test(nickname)) {
    message.show('昵称不能包含 @<>/ 等特殊字符')
    return
  }

  if (!form.value.fishhubId.trim()) {
    message.show('请输入飞鱼社区号')
    return
  }

  const fishhubId = form.value.fishhubId.trim()
  if (fishhubId.length < 6 || fishhubId.length > 15) {
    message.show('飞鱼社区号长度应为 6-15 个字符')
    return
  }

  const hasLetter = /[a-zA-Z]/.test(fishhubId)
  const validFormat = /^[a-zA-Z0-9_]+$/.test(fishhubId)

  if (!hasLetter) {
    message.show('飞鱼社区号必须包含英文字母')
    return
  }

  if (!validFormat) {
    message.show('飞鱼社区号只能包含英文字母、数字和下划线')
    return
  }

  const birthdayParts = [form.value.birthYear, form.value.birthMonth, form.value.birthDay]
  const selectedBirthdayParts = birthdayParts.filter(Boolean).length
  // 生日可以整体留空，但部分日期无法组成合法值。
  if (selectedBirthdayParts > 0 && selectedBirthdayParts < 3) {
    message.show('请选择完整的生日信息')
    return
  }

  const birthday = selectedBirthdayParts === 3
    ? `${form.value.birthYear}-${form.value.birthMonth}-${form.value.birthDay}`
    : null

  try {
    const profileData = {
      avatar: form.value.avatarFile,
      nickname: form.value.nickname,
      fishhubId,
      birthday,
      introduction: form.value.introduction,
      sex: form.value.sex
    }

    const res = await updateUserProfile(profileData)
    if (!res.success) {
      message.show(res.message || '更新失败，请重试')
      return
    }

    message.show('更新成功')

    const profileRes = await getUserProfile()
    if (profileRes.success) {
      userStore.setProfile(profileRes.data)
    }

    emit('update-success', profileData)
    emit('update:visible', false)

  } catch (error) {
    console.error('更新资料出错:', error)
    message.show('更新失败，请重试')
  }
}

const handleAvatarChange = (event) => {
  const file = event.target.files[0]
  if (!file) return

  if (!file.type.startsWith('image/')) {
    message.show('请上传图片文件')
    return
  }

  if (file.size > 5 * 1024 * 1024) {
    message.show('图片大小不能超过 5MB')
    return
  }

  form.value.avatarFile = file

  const reader = new FileReader()
  reader.onload = (e) => {
    form.value.avatar = e.target.result
  }
  reader.readAsDataURL(file)
}
</script>

<style scoped>
select {
  appearance: none;
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' fill='none' viewBox='0 0 24 24' stroke='%23666666'%3E%3Cpath stroke-linecap='round' stroke-linejoin='round' stroke-width='2' d='M19 9l-7 7-7-7'%3E%3C/path%3E%3C/svg%3E");
  background-repeat: no-repeat;
  background-position: right 8px center;
  background-size: 16px;
  padding-right: 32px;
  min-width: 80px;
}

select:focus {
  outline: none;
  border-color: var(--color-primary);
}

input:disabled {
  background-color: var(--color-surface-muted);
  cursor: not-allowed;
}

@media (max-width: 767px) {
  select { min-width: 0; flex: 1; }
  .p-6 { padding: 20px; }
  .space-y-6 { row-gap: 20px; }
  .flex.gap-3 { gap: 8px; }
}
</style>
