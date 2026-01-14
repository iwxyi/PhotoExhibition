<template>
  <div class="comment-section max-w-4xl mx-auto mt-12 px-4">
    <h2 class="text-2xl font-light mb-8" :style="{ color: textColor }">评论</h2>

    <!-- 评论列表 -->
    <CommentList
      :album-id="albumId"
      :text-color="textColor"
      :comments="comments"
      :loading="loading"
      :has-more="hasMore"
      @load-more="loadMoreComments"
      @comment-deleted="handleCommentDeleted"
      @reply-added="handleReplyAdded"
    />

    <!-- 已评论提示或评论表单 -->
    <div v-if="hasUserCommented" class="text-center py-6">
      <p :style="{ color: textColor, opacity: 0.7 }" class="text-lg">已评论</p>
      <p :style="{ color: textColor, opacity: 0.5 }" class="text-sm mt-2">每个用户对一个相册只能发表一条评论</p>
    </div>
    <CommentForm
      v-else
      :album-id="albumId"
      :text-color="textColor"
      @comment-added="handleCommentAdded"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import CommentForm from './CommentForm.vue'
import CommentList from './CommentList.vue'
import { commentApi, CommentDTO } from '@/api'

interface Props {
  albumId: number
  textColor?: string
}

const props = withDefaults(defineProps<Props>(), {
  textColor: '#1a1a1a'
})

const comments = ref<CommentDTO[]>([])
const loading = ref(false)
const hasMore = ref(true)
const currentPage = ref(0)
const pageSize = 10

// 用户是否已经对该相册发表过评论
const hasUserCommented = ref(false)

// 获取当前用户信息
const getCurrentUserInfo = () => {
  try {
    const saved = localStorage.getItem('comment-user-info')
    if (saved) {
      return JSON.parse(saved)
    }
  } catch (error) {
    console.error('Failed to load user info:', error)
  }
  return null
}

// 检查用户是否已经评论过
const checkIfUserHasCommented = () => {
  const userInfo = getCurrentUserInfo()
  if (!userInfo || !userInfo.email) {
    hasUserCommented.value = false
    return
  }

  // 检查顶级评论中是否有当前用户的评论
  const userComment = comments.value.find(comment =>
    !comment.parentId && comment.email === userInfo.email
  )
  hasUserCommented.value = !!userComment
}

const textColor = computed(() => props.textColor)

// 加载评论
const loadComments = async (page = 0) => {
  if (loading.value) return

  loading.value = true
  try {
    const response = await commentApi.getAlbumComments(props.albumId, page, pageSize)
    const newComments = response.data.content || []

    if (page === 0) {
      comments.value = newComments
    } else {
      comments.value.push(...newComments)
    }

    hasMore.value = !response.data.last
    currentPage.value = page

    // 检查用户是否已经评论过
    checkIfUserHasCommented()
  } catch (error) {
    console.error('Failed to load comments:', error)
  } finally {
    loading.value = false
  }
}

// 加载更多评论
const loadMoreComments = () => {
  if (!loading.value && hasMore.value) {
    loadComments(currentPage.value + 1)
  }
}

// 处理新评论添加
const handleCommentAdded = (newComment: CommentDTO) => {
  // 如果是顶级评论，添加到列表开头
  if (!newComment.parentId) {
    comments.value.unshift(newComment)
    // 检查用户是否已经评论过
    checkIfUserHasCommented()
  } else {
    // 如果是回复，刷新评论列表（简化处理）
    loadComments(0)
  }
}

// 处理评论删除
const handleCommentDeleted = (deletedCommentId: number) => {
  // 从评论列表中移除已删除的评论
  const removeComment = (comments: CommentDTO[]): CommentDTO[] => {
    return comments.filter(comment => {
      if (comment.id === deletedCommentId) {
        return false
      }
      if (comment.replies) {
        comment.replies = removeComment(comment.replies)
      }
      return true
    })
  }

  comments.value = removeComment(comments.value)

  // 重新检查用户是否已经评论过（删除评论后可能需要重新显示表单）
  checkIfUserHasCommented()
}

// 处理回复添加
const handleReplyAdded = () => {
  // 刷新评论列表以显示新回复
  loadComments(0)
}

onMounted(() => {
  loadComments(0)
})
</script>

<style scoped>
.comment-section {
  /* 样式会在全局样式中定义 */
}
</style>
