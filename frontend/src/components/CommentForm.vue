<template>
  <div class="comment-form bg-white/80 dark:bg-gray-800/80 backdrop-blur-sm rounded-lg p-6 mb-8 shadow-sm border border-gray-200/50 dark:border-gray-700/50" :style="{ backgroundColor: backgroundColor, borderColor: borderColor }">
    <div class="flex items-center justify-between mb-4">
      <h3 class="text-lg font-medium" :style="{ color: textColor }">
        {{ parentId ? '回复评论' : '发表评论' }}
      </h3>
      <button
        v-if="!parentId"
        type="button"
        class="comment-form-collapse-button"
        :style="{ color: textColor }"
        title="收起评论框"
        aria-label="收起评论框"
        @click="$emit('collapse')"
      >
        <svg viewBox="0 0 24 24" aria-hidden="true"><path d="m6 15 6-6 6 6" /></svg>
      </button>
    </div>

    <form @submit.prevent="submitComment" class="space-y-4">
      <!-- 昵称和邮箱 -->
      <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
        <div>
          <label for="nickname" class="block text-sm font-medium mb-2" :style="{ color: textColor }">
            昵称 <span class="text-red-500">*</span>
          </label>
          <input
            id="nickname"
            v-model="formData.nickname"
            type="text"
            required
            maxlength="50"
            :class="`w-full px-3 py-2 bg-white/10 dark:bg-white/5 backdrop-blur-sm border rounded-md focus:outline-none focus:border-blue-500 placeholder-white/70 ${getTextColorClass()}`"
            :style="{ borderColor: inputBorderColor }"
            placeholder="评论区展示的昵称"
          />
        </div>
        <div>
          <label for="email" class="block text-sm font-medium mb-2" :style="{ color: textColor }">
            邮箱
          </label>
          <input
            id="email"
            v-model="formData.email"
            type="email"
            maxlength="100"
            :class="`w-full px-3 py-2 bg-white/10 dark:bg-white/5 backdrop-blur-sm border rounded-md focus:outline-none focus:border-blue-500 placeholder-white/70 ${getTextColorClass()}`"
            :style="{ borderColor: inputBorderColor }"
            placeholder="不会公开显示，仅用于回复通知"
          />
        </div>
      </div>

      <!-- 评论内容 -->
      <div>
        <label for="content" class="block text-sm font-medium mb-2" :style="{ color: textColor }">
          评论内容 <span class="text-red-500">*</span>
        </label>
        <textarea
          id="content"
          v-model="formData.content"
          required
          maxlength="1000"
          rows="4"
          :class="`w-full px-3 py-2 bg-white/10 dark:bg-white/5 backdrop-blur-sm border rounded-md focus:outline-none focus:border-blue-500 resize-none placeholder-white/70 ${getTextColorClass()}`"
          :style="{ borderColor: inputBorderColor }"
          placeholder="输入评论内容"
        ></textarea>
        <div :class="`text-right text-sm mt-1 ${props.isDarkMode ? 'text-white/70' : 'text-gray-600'}`">
          {{ formData.content.length }}/1000
        </div>
      </div>

      <!-- 提交按钮 -->
      <div class="flex justify-between items-center">
        <button
          v-if="parentId"
          type="button"
          @click="$emit('cancel')"
          :class="`px-4 py-2 ${props.isDarkMode ? 'text-white/70 hover:text-white' : 'text-gray-600 hover:text-gray-800'}`"
        >
          取消回复
        </button>
        <button
          type="submit"
          :disabled="submitting || !isFormValid"
          class="ml-auto px-6 py-2 bg-blue-500/80 hover:bg-blue-500 text-white rounded-md focus:outline-none focus:ring-2 focus:ring-blue-300/50 disabled:opacity-50 disabled:cursor-not-allowed backdrop-blur-sm"
        >
          {{ submitting ? '提交中...' : (parentId ? '回复' : '发表评论') }}
        </button>
      </div>
    </form>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted, nextTick } from 'vue'
import { commentApi, CommentRequest, CommentDTO } from '@/api'

interface Props {
  albumId: number
  parentId?: number
  textColor?: string
  backgroundColor?: string
  borderColor?: string
  inputBorderColor?: string
  isDarkMode?: boolean
}

interface Emits {
  (e: 'comment-added', comment: CommentDTO): void
  (e: 'cancel'): void
  (e: 'collapse'): void
}

const props = withDefaults(defineProps<Props>(), {
  textColor: '#1a1a1a',
  backgroundColor: 'rgba(255, 255, 255, 0.8)',
  borderColor: 'rgba(229, 231, 235, 0.5)',
  inputBorderColor: 'rgb(107 114 128 / 0.5)',
  isDarkMode: false
})

const emit = defineEmits<Emits>()

const submitting = ref(false)
const inputBorderColor = computed(() => props.inputBorderColor)

const getTextColorClass = () => {
  return props.isDarkMode ? 'text-white' : 'text-black'
}

// 从localStorage加载用户信息
const loadUserInfo = () => {
  try {
    const saved = localStorage.getItem('comment-user-info')
    if (saved) {
      return JSON.parse(saved)
    }
  } catch (error) {
    console.error('Failed to load user info:', error)
  }
  return { nickname: '', email: '' }
}

// 保存用户信息到localStorage
const saveUserInfo = (nickname: string, email: string) => {
  try {
    localStorage.setItem('comment-user-info', JSON.stringify({ nickname, email }))
  } catch (error) {
    console.error('Failed to save user info:', error)
  }
}

const formData = ref<CommentRequest>({
  albumId: props.albumId,
  parentId: props.parentId,
  nickname: '',
  email: '',
  content: ''
})

// 初始化时加载用户信息
const initUserInfo = () => {
  const userInfo = loadUserInfo()
  formData.value.nickname = userInfo.nickname
  formData.value.email = userInfo.email
}

// 监听parentId变化，更新表单数据并聚焦
watch(() => props.parentId, (newParentId) => {
  formData.value.parentId = newParentId

  // 延迟聚焦，确保DOM更新完成
  setTimeout(() => {
    nextTick(() => {
      const userInfo = loadUserInfo()
      if (!userInfo.nickname || userInfo.nickname.trim() === '') {
        // 没有昵称，聚焦到昵称输入框
        const nicknameInput = document.querySelector('#nickname') as HTMLInputElement
        if (nicknameInput && nicknameInput.offsetParent !== null) { // 确保元素可见
          nicknameInput.focus()
          nicknameInput.scrollIntoView({ behavior: 'smooth', block: 'center' })
        }
      } else {
        // 有昵称，聚焦到评论输入框
        const contentTextarea = document.querySelector('#content') as HTMLTextAreaElement
        if (contentTextarea && contentTextarea.offsetParent !== null) { // 确保元素可见
          contentTextarea.focus()
          contentTextarea.scrollIntoView({ behavior: 'smooth', block: 'center' })
        }
      }
    })
  }, 100)
})

const isFormValid = computed(() => {
  const email = formData.value.email.trim()
  return formData.value.nickname.trim().length > 0 &&
         formData.value.content.trim().length > 0 &&
         (email.length === 0 || email.includes('@'))
})

const submitComment = async () => {
  if (!isFormValid.value || submitting.value) return

  submitting.value = true
  try {
    const response = await commentApi.createComment(formData.value)
    emit('comment-added', response.data)

    // 保存用户信息到localStorage
    saveUserInfo(formData.value.nickname, formData.value.email)

    // 重置表单（保持昵称和邮箱）
    formData.value.content = ''
  } catch (error: any) {
    console.error('Failed to submit comment:', error)
    // 这里可以显示错误提示
    alert(error.response?.data?.message || '发表评论失败，请稍后再试')
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  initUserInfo()
})
</script>

<style scoped>
/* 表单样式 */
</style>
