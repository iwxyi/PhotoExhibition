<template>
  <div class="comment-item">
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

        <div class="text-gray-700 dark:text-gray-300 mb-3 whitespace-pre-wrap">
          {{ comment.content }}
        </div>

        <!-- 操作按钮 -->
        <div class="flex items-center space-x-4 text-sm">
          <button
            v-if="!comment.parentId"
            @click="toggleReply"
            class="text-blue-600 hover:text-blue-800 dark:text-blue-400 dark:hover:text-blue-300"
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
            @reply-added="$emit('reply-added')"
            @comment-deleted="$emit('comment-deleted', $event)"
          />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import CommentForm from './CommentForm.vue'
import { commentApi, CommentDTO } from '@/api'

interface Props {
  comment: CommentDTO
  albumId: number
  textColor?: string
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
  // 这里可以根据需要实现更复杂的权限检查
  // 目前简单地允许删除所有评论（实际应用中应该验证用户身份）
  return true
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

// 处理回复添加
const handleReplyAdded = (reply: CommentDTO) => {
  showReplyForm.value = false
  emit('reply-added')
}
</script>

<style scoped>
.comment-item {
  padding: 1rem 0;
  border-bottom: 1px solid #e5e7eb;
}

.dark .comment-item {
  border-bottom-color: #374151;
}
</style>
