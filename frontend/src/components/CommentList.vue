<template>
  <div class="comment-list">
    <!-- 加载状态 -->
    <div v-if="loading && comments.length === 0" class="text-center py-8">
      <div class="inline-block animate-spin rounded-full h-8 w-8 border-b-2 border-blue-600"></div>
      <p class="mt-2 text-gray-600 dark:text-gray-400">加载评论中...</p>
    </div>

    <!-- 评论列表 -->
    <div v-if="comments.length > 0" class="space-y-6">
      <CommentItem
        v-for="comment in visibleComments"
        :key="comment.id"
        :comment="comment"
        :text-color="textColor"
        :album-id="albumId"
        :background-color="backgroundColor"
        :border-color="borderColor"
        :input-border-color="inputBorderColor"
        :is-dark-mode="props.isDarkMode"
        :reply-status-map="props.replyStatusMap"
        @reply-added="(reply, parentId) => $emit('reply-added', reply, parentId)"
        @comment-deleted="$emit('comment-deleted', $event)"
      />
    </div>

    <!-- 没有评论 -->
    <div v-else-if="!loading" class="text-center py-12">
      <p class="text-white/60">暂无评论，快来发表第一条评论吧！</p>
    </div>

    <!-- 加载更多 -->
    <div v-if="hasMore && comments.length > 0" class="text-center mt-8">
      <button
        @click="$emit('load-more')"
        :disabled="loading"
        class="px-6 py-2 border border-white/30 text-white/80 rounded-md hover:bg-white/10 focus:outline-none focus:ring-2 focus:ring-white/50 disabled:opacity-50 backdrop-blur-sm"
      >
        {{ loading ? '加载中...' : '加载更多评论' }}
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import CommentItem from './CommentItem.vue'
import { CommentDTO } from '@/api'

interface Props {
  albumId: number
  comments: CommentDTO[]
  loading: boolean
  hasMore: boolean
  textColor?: string
  backgroundColor?: string
  borderColor?: string
  inputBorderColor?: string
  isDarkMode?: boolean
  replyStatusMap?: Map<number, boolean>
}

interface Emits {
  (e: 'load-more'): void
  (e: 'comment-deleted', commentId: number): void
  (e: 'reply-added'): void
}

const props = defineProps<Props>()
defineEmits<Emits>()

// 过滤掉已删除的评论
const visibleComments = computed(() => {
  return props.comments.filter(comment => !comment.deleted)
})
</script>

<style scoped>
/* 评论列表样式 */
</style>
