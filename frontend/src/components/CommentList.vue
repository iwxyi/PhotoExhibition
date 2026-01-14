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
        v-for="comment in comments"
        :key="comment.id"
        :comment="comment"
        :text-color="textColor"
        :album-id="albumId"
        @reply-added="$emit('reply-added')"
        @comment-deleted="$emit('comment-deleted', $event)"
      />
    </div>

    <!-- 没有评论 -->
    <div v-else-if="!loading" class="text-center py-12">
      <p class="text-gray-500 dark:text-gray-400">暂无评论，快来发表第一条评论吧！</p>
    </div>

    <!-- 加载更多 -->
    <div v-if="hasMore && comments.length > 0" class="text-center mt-8">
      <button
        @click="$emit('load-more')"
        :disabled="loading"
        class="px-6 py-2 border border-gray-300 dark:border-gray-600 text-gray-700 dark:text-gray-300 rounded-md hover:bg-gray-50 dark:hover:bg-gray-700 focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:opacity-50"
      >
        {{ loading ? '加载中...' : '加载更多评论' }}
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import CommentItem from './CommentItem.vue'
import { CommentDTO } from '@/api'

interface Props {
  albumId: number
  comments: CommentDTO[]
  loading: boolean
  hasMore: boolean
  textColor?: string
}

interface Emits {
  (e: 'load-more'): void
  (e: 'comment-deleted', commentId: number): void
  (e: 'reply-added'): void
}

defineProps<Props>()
defineEmits<Emits>()
</script>

<style scoped>
/* 评论列表样式 */
</style>
