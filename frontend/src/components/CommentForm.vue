<template>
  <div class="comment-form bg-white dark:bg-gray-800 rounded-lg p-6 mb-8 shadow-sm border border-gray-200 dark:border-gray-700">
    <h3 class="text-lg font-medium mb-4" :style="{ color: textColor }">
      {{ parentId ? '回复评论' : '发表评论' }}
    </h3>

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
            class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500 dark:bg-gray-700 dark:text-white"
            :style="{ color: textColor }"
            placeholder="请输入昵称"
          />
        </div>
        <div>
          <label for="email" class="block text-sm font-medium mb-2" :style="{ color: textColor }">
            邮箱 <span class="text-red-500">*</span>
          </label>
          <input
            id="email"
            v-model="formData.email"
            type="email"
            required
            maxlength="100"
            class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500 dark:bg-gray-700 dark:text-white"
            :style="{ color: textColor }"
            placeholder="请输入邮箱"
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
          class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500 resize-none dark:bg-gray-700 dark:text-white"
          :style="{ color: textColor }"
          placeholder="请输入评论内容..."
        ></textarea>
        <div class="text-right text-sm text-gray-500 dark:text-gray-400 mt-1">
          {{ formData.content.length }}/1000
        </div>
      </div>

      <!-- 提交按钮 -->
      <div class="flex justify-between items-center">
        <button
          v-if="parentId"
          type="button"
          @click="$emit('cancel')"
          class="px-4 py-2 text-gray-600 dark:text-gray-400 hover:text-gray-800 dark:hover:text-gray-200"
        >
          取消回复
        </button>
        <button
          type="submit"
          :disabled="submitting || !isFormValid"
          class="ml-auto px-6 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:opacity-50 disabled:cursor-not-allowed"
        >
          {{ submitting ? '提交中...' : (parentId ? '回复' : '发表评论') }}
        </button>
      </div>
    </form>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { commentApi, CommentRequest, CommentDTO } from '@/api'

interface Props {
  albumId: number
  parentId?: number
  textColor?: string
}

interface Emits {
  (e: 'comment-added', comment: CommentDTO): void
  (e: 'cancel'): void
}

const props = withDefaults(defineProps<Props>(), {
  textColor: '#1a1a1a'
})

const emit = defineEmits<Emits>()

const submitting = ref(false)

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

// 监听parentId变化，更新表单数据
watch(() => props.parentId, (newParentId) => {
  formData.value.parentId = newParentId
})

const isFormValid = computed(() => {
  return formData.value.nickname.trim().length > 0 &&
         formData.value.email.trim().length > 0 &&
         formData.value.email.includes('@') &&
         formData.value.content.trim().length > 0
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
