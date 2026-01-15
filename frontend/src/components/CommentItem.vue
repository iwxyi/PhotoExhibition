<template>
  <div class="comment-item group">
    <div class="flex space-x-4">
      <!-- 用户头像 -->
      <div class="flex-shrink-0">
        <div class="w-10 h-10 bg-gray-300 dark:bg-gray-600 rounded-full flex items-center justify-center">
          <span class="text-gray-600 dark:text-gray-300 font-medium">
            {{ comment.nickname.charAt(0).toUpperCase() }}
          </span>
        </div>
      </div>

      <!-- 评论内容 -->
      <div class="flex-1">
        <div class="flex items-center space-x-2 mb-2">
          <span class="font-medium" :style="{ color: textColor }">{{ comment.nickname }}</span>
          <span class="text-sm text-gray-500 dark:text-gray-400">
            {{ formatDate(comment.createdAt) }}
          </span>
          <button
            v-if="canDelete(comment)"
            @click="deleteComment(comment.id)"
            class="text-sm text-red-600 hover:text-red-800 dark:text-red-400 dark:hover:text-red-300"
          >
            删除
          </button>
        </div>

        <div :class="`mb-3 whitespace-pre-wrap ${getTextColorClass()}`">
          {{ comment.content }}
        </div>

        <!-- 操作按钮 -->
        <div v-if="!hasUserRepliedInTree() && !isOwnComment()" class="flex items-center space-x-4 text-sm opacity-0 group-hover:opacity-100 transition-opacity duration-200">
          <button
            @click="toggleReply"
            class="text-blue-400 hover:text-blue-300"
          >
            回复
          </button>
        </div>

        <!-- 回复表单 -->
        <CommentForm
          v-if="showReplyForm"
          :album-id="albumId"
          :parent-id="comment.id"
          :text-color="textColor"
          :background-color="backgroundColor"
          :border-color="borderColor"
          :input-border-color="inputBorderColor"
          :is-dark-mode="props.isDarkMode"
          :is-atmosphere-enabled="props.isAtmosphereEnabled"
          @comment-added="handleReplyAdded"
          @cancel="showReplyForm = false"
        />

        <!-- 回复列表 -->
        <div v-if="comment.replies && comment.replies.length > 0" class="mt-4 space-y-4">
          <CommentItem
            v-for="reply in comment.replies"
            :key="reply.id"
            :comment="reply"
            :text-color="textColor"
            :album-id="albumId"
            :background-color="backgroundColor"
            :border-color="borderColor"
            :input-border-color="inputBorderColor"
            :is-dark-mode="props.isDarkMode"
            :is-atmosphere-enabled="props.isAtmosphereEnabled"
            :reply-status-map="props.replyStatusMap"
            @reply-added="$emit('reply-added')"
            @comment-deleted="$emit('comment-deleted', $event)"
          />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import CommentForm from './CommentForm.vue'
import { commentApi, CommentDTO } from '@/api'
import { useThemeStore } from '@/stores/theme'

interface Props {
  comment: CommentDTO
  albumId: number
  textColor?: string
  backgroundColor?: string
  borderColor?: string
  inputBorderColor?: string
  isDarkMode?: boolean
  isAtmosphereEnabled?: boolean
  replyStatusMap?: Map<number, boolean>
}

interface Emits {
  (e: 'reply-added'): void
  (e: 'comment-deleted', commentId: number): void
}

const props = withDefaults(defineProps<Props>(), {
  textColor: '#1a1a1a'
})

const emit = defineEmits<Emits>()

const showReplyForm = ref(false)
const hasReplied = ref(props.replyStatusMap?.get(props.comment.id) || false)

// 格式化日期
const formatDate = (dateString: string) => {
  const date = new Date(dateString)
  const now = new Date()
  const diffMs = now.getTime() - date.getTime()
  const diffHours = diffMs / (1000 * 60 * 60)
  const diffDays = diffMs / (1000 * 60 * 60 * 24)

  if (diffHours < 1) {
    const diffMinutes = Math.floor(diffMs / (1000 * 60))
    return diffMinutes <= 0 ? '刚刚' : `${diffMinutes}分钟前`
  } else if (diffHours < 24) {
    return `${Math.floor(diffHours)}小时前`
  } else if (diffDays < 30) {
    return `${Math.floor(diffDays)}天前`
  } else {
    return date.toLocaleDateString('zh-CN')
  }
}

// 判断是否可以删除评论（通过邮箱匹配）
const canDelete = (comment: CommentDTO) => {
  // 从localStorage获取当前用户信息
  try {
    const saved = localStorage.getItem('comment-user-info')
    if (saved) {
      const userInfo = JSON.parse(saved)
      // 只有评论作者才能删除自己的评论
      return userInfo.email === comment.email
    }
  } catch (error) {
    console.error('Failed to load user info:', error)
  }
  return false
}

// 删除评论
const deleteComment = async (commentId: number) => {
  if (!confirm('确定要删除这条评论吗？')) return

  try {
    // 这里需要获取当前评论的邮箱，实际应用中应该从用户会话中获取
    // 暂时使用评论本身的邮箱进行删除（需要后端验证）
    await commentApi.deleteComment(commentId, props.comment.email)
    emit('comment-deleted', commentId)
  } catch (error) {
    console.error('Failed to delete comment:', error)
    alert('删除评论失败，请稍后再试')
  }
}

// 切换回复表单
const toggleReply = () => {
  showReplyForm.value = !showReplyForm.value
}

// 处理回复添加后更新状态
const handleReplyAdded = (reply: CommentDTO) => {
  showReplyForm.value = false
  hasReplied.value = true // 标记为已回复
  emit('reply-added', reply, props.comment.id)
}

// 获取文字颜色类
const getTextColorClass = () => {
  // 如果开启氛围模式或处于夜间模式，使用白色文字
  if (props.isAtmosphereEnabled || props.isDarkMode) {
    return 'text-white/90'
  }
  // 日间模式使用黑色文字
  return 'text-black'
}

// 检查是否是用户自己的评论
const isOwnComment = () => {
  try {
    const userInfo = JSON.parse(localStorage.getItem('comment-user-info') || '{}')
    if (!userInfo.email) return false

    // 通过邮箱匹配判断是否是自己的评论
    return userInfo.email === props.comment.email
  } catch (error) {
    return false
  }
}

// 检查用户是否对这个评论树中的任何评论回复过
const hasUserRepliedInTree = () => {
  if (!props.replyStatusMap) return false

  // 检查用户是否对当前评论回复过
  if (props.replyStatusMap.get(props.comment.id)) {
    return true
  }

  // 递归检查子评论
  const checkReplies = (replies: any[]): boolean => {
    for (const reply of replies) {
      if (props.replyStatusMap?.get(reply.id)) {
        return true
      }
      if (reply.replies && reply.replies.length > 0) {
        if (checkReplies(reply.replies)) {
          return true
        }
      }
    }
    return false
  }

  // 检查当前评论的所有回复
  if (props.comment.replies && props.comment.replies.length > 0) {
    return checkReplies(props.comment.replies)
  }

  return false
}


onMounted(() => {
  // 回复状态现在由父组件提供，无需单独检查
})
</script>

<style scoped>
.comment-item {
  padding: 0.75rem 0;
  border-bottom: 1px solid rgba(255, 255, 255, 0.2);
}

.comment-item:last-child {
  border-bottom: none;
}

/* 当没有回复按钮时，减少底部间距 */
.comment-item:has(.comment-item > div > div > div:not([style*="opacity: 1"])) {
  padding-bottom: 0.5rem;
}
</style>
