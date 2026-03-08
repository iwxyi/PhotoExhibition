<template>
  <div v-show="commentSectionVisible" class="comment-section max-w-4xl mx-auto mt-12 px-4 transition-opacity duration-500 ease-in-out" :class="{ 'opacity-0': !commentSectionVisible, 'opacity-100': commentSectionVisible }">
    <h2 class="text-2xl font-light mb-8 text-white">评论</h2>
    <pre v-if="!commentSectionVisible">debug: commentSectionVisible = {{ commentSectionVisible }}, visible = {{ visible }}</pre>

    <!-- 评论列表 -->
    <CommentList
      :album-id="albumId"
      :text-color="textColor"
      :background-color="backgroundColor"
      :border-color="borderColor"
      :input-border-color="inputBorderColor"
      :is-dark-mode="props.isDarkMode"
      :is-atmosphere-enabled="props.isAtmosphereEnabled"
      :reply-status-map="replyStatusMap"
      :comments="comments"
      :loading="loading"
      :has-more="hasMore"
      @load-more="loadMoreComments"
      @comment-deleted="handleCommentDeleted"
      @reply-added="handleReplyAdded"
    />

    <!-- 评论表单或已评论提示 -->
    <div v-if="hasCommentedToday" class="text-center py-6">
      <p :style="{ color: textColor, opacity: 0.7 }" class="text-lg">您已评论</p>
      <p :style="{ color: textColor, opacity: 0.5 }" class="text-sm mt-2">每天只能发表一条评论</p>
    </div>
    <CommentForm
      v-else
      :album-id="albumId"
      :text-color="textColor"
      :background-color="backgroundColor"
      :border-color="borderColor"
      :input-border-color="inputBorderColor"
      :is-dark-mode="props.isDarkMode"
      :is-atmosphere-enabled="props.isAtmosphereEnabled"
      @comment-added="handleCommentAdded"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import CommentForm from './CommentForm.vue'
import CommentList from './CommentList.vue'
import { commentApi, CommentDTO } from '@/api'

interface Props {
  albumId: number
  visible?: boolean
  textColor?: string
  backgroundColor?: string
  borderColor?: string
  inputBorderColor?: string
  isDarkMode?: boolean
  isAtmosphereEnabled?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  visible: false,
  textColor: '#1a1a1a',
  backgroundColor: 'rgba(255, 255, 255, 0.8)',
  borderColor: 'rgba(229, 231, 235, 0.5)',
  inputBorderColor: 'rgb(107 114 128 / 0.5)',
  isDarkMode: false,
  isAtmosphereEnabled: false
})

const comments = ref<CommentDTO[]>([])
const loading = ref(false)
const hasMore = ref(true)
const currentPage = ref(0)
const pageSize = 10

// 评论区域是否可见（延迟显示）
const commentSectionVisible = ref(false)

// 用户今天是否已经发过评论
const hasCommentedToday = ref(false)

const textColor = computed(() => props.textColor)
const backgroundColor = computed(() => props.backgroundColor)
const borderColor = computed(() => props.borderColor)
const inputBorderColor = computed(() => props.inputBorderColor)

// 加载评论
const loadComments = async (page = 0) => {
  // 如果组件不可见，不加载评论
  if (!props.visible) return
  if (loading.value) return

  loading.value = true
  try {
    const response = await commentApi.getAlbumComments(props.albumId, page, pageSize)
    const newComments = response.data.content || []

    // 组件不可见时，丢弃加载结果
    if (!props.visible) return

    if (page === 0) {
      comments.value = newComments
    } else {
      comments.value.push(...newComments)
    }

    hasMore.value = !response.data.last
    currentPage.value = page

    // 检查用户今天是否已经发过评论
    checkIfUserHasCommentedToday()

    // 批量检查所有评论的回复状态
    await checkAllReplyStatus(comments.value)
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
}

// 检查用户今天是否已经发过评论（调用后端API，使用服务器时间）
const checkIfUserHasCommentedToday = async () => {
  try {
    const userInfo = JSON.parse(localStorage.getItem('comment-user-info') || '{}')
    if (!userInfo.email) {
      hasCommentedToday.value = false
      return
    }

    // 调用后端API检查用户今天是否已经评论过此相册
    const response = await commentApi.hasUserCommentedOnAlbumToday(props.albumId, userInfo.email)
    hasCommentedToday.value = response.data
  } catch (error) {
    console.error('Failed to check if user has commented today:', error)
    hasCommentedToday.value = false
  }
}

// 存储所有评论的回复状态
const replyStatusMap = ref<Map<number, boolean>>(new Map())

// 批量检查所有评论的回复状态
const checkAllReplyStatus = async (comments: CommentDTO[]) => {
  try {
    const userInfo = JSON.parse(localStorage.getItem('comment-user-info') || '{}')
    if (!userInfo.email) return

    // 收集所有需要检查的评论ID（包括嵌套回复）
    const commentIds: number[] = []
    const collectCommentIds = (commentList: CommentDTO[]) => {
      for (const comment of commentList) {
        commentIds.push(comment.id)
        if (comment.replies && comment.replies.length > 0) {
          collectCommentIds(comment.replies)
        }
      }
    }
    collectCommentIds(comments)

    if (commentIds.length === 0) return

    // 使用批量API一次性获取所有回复状态
    try {
      const response = await commentApi.batchHasUserRepliedToComments(commentIds, userInfo.email)
      const newStatusMap = new Map<number, boolean>()

      // 将API返回的对象转换为Map
      Object.entries(response.data).forEach(([commentId, hasReplied]) => {
        newStatusMap.set(Number(commentId), hasReplied as boolean)
      })

      replyStatusMap.value = newStatusMap
    } catch (error) {
      console.error('Failed to batch check reply status:', error)
      // 如果批量API失败，回退到单个API调用
      await fallbackToIndividualChecks(commentIds, userInfo.email)
    }
  } catch (error) {
    console.error('Failed to check all reply status:', error)
  }
}

// 回退方案：单个API调用
const fallbackToIndividualChecks = async (commentIds: number[], email: string) => {
  console.warn('Falling back to individual API calls for reply status checks')

  const statusPromises = commentIds.map(async (commentId) => {
    try {
      const response = await commentApi.hasUserRepliedToComment(commentId, email)
      return { commentId, hasReplied: response.data }
    } catch (error) {
      console.error(`Failed to check reply status for comment ${commentId}:`, error)
      return { commentId, hasReplied: false }
    }
  })

  const results = await Promise.all(statusPromises)
  const newStatusMap = new Map<number, boolean>()
  results.forEach(({ commentId, hasReplied }) => {
    newStatusMap.set(commentId, hasReplied)
  })

  replyStatusMap.value = newStatusMap
}

// 处理回复添加
const handleReplyAdded = (newReply: CommentDTO, parentId: number) => {
  // 创建comments的深拷贝，修改后再重新赋值以触发Vue响应式更新
  const updateCommentsWithReply = (comments: CommentDTO[]): CommentDTO[] => {
    return comments.map(comment => {
      if (comment.id === parentId) {
        // 找到父评论，添加回复
        const updatedComment = { ...comment }
        if (!updatedComment.replies) {
          updatedComment.replies = []
        }
        // 创建新的replies数组，确保Vue检测到变化
        updatedComment.replies = [...updatedComment.replies, newReply]
        return updatedComment
      } else if (comment.replies && comment.replies.length > 0) {
        // 递归处理嵌套回复
        const updatedComment = { ...comment }
        updatedComment.replies = updateCommentsWithReply(comment.replies)
        return updatedComment
      }
      return comment
    })
  }

  // 更新comments数组，触发Vue响应式更新
  comments.value = updateCommentsWithReply(comments.value)

  // 更新回复状态Map，标记用户已回复此评论
  replyStatusMap.value.set(parentId, true)
}

onMounted(() => {
  console.log('[CommentSection] onMounted, props.visible:', props.visible, 'props.albumId:', props.albumId)
  // 只有当父组件要求可见时，才延迟显示评论区域
  if (props.visible) {
    setTimeout(() => {
      console.log('[CommentSection] onMounted 300ms 后设置 commentSectionVisible = true')
      commentSectionVisible.value = true
    }, 300)
  }
})

// 监听 visible 变化，只有当可见时才加载评论
watch(() => props.visible, (newVisible, oldVisible) => {
  console.log('[CommentSection] visible 变化:', newVisible, oldVisible, 'commentSectionVisible:', commentSectionVisible.value)
  if (newVisible && !oldVisible) {
    // 从不可见变为可见时，加载评论
    commentSectionVisible.value = true
    loadComments(0)
  } else if (!newVisible && oldVisible) {
    // 从可见变为不可见时，清空评论数据
    console.log('[CommentSection] visible 从 true 变为 false，清空评论数据')
    comments.value = []
    currentPage.value = 0
    hasMore.value = true
    hasCommentedToday.value = false
    replyStatusMap.value = new Map()
    commentSectionVisible.value = false
  }
})

// 监听albumId变化，当相册切换时重新加载评论
watch(() => props.albumId, (newAlbumId, oldAlbumId) => {
  console.log('[CommentSection] albumId 变化:', newAlbumId, oldAlbumId)
  if (newAlbumId !== oldAlbumId && newAlbumId) {
    // 只有在可见时才加载评论
    if (!props.visible) return

    // 重置状态
    comments.value = []
    currentPage.value = 0
    hasMore.value = true
    hasCommentedToday.value = false
    replyStatusMap.value = new Map()

    // 重新加载评论
    loadComments(0)
  }
})
</script>

<style scoped>
.comment-section {
  /* 样式会在全局样式中定义 */
}
</style>
