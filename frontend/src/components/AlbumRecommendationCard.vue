<template>
  <div
    class="group relative bg-white dark:bg-gray-800 rounded-xl shadow-sm hover:shadow-lg transition-all duration-300 cursor-pointer transform hover:-translate-y-1 overflow-hidden border border-gray-100 dark:border-gray-700"
    @click="$emit('click')"
  >
    <!-- 相册封面 -->
    <div class="relative aspect-[4/3] bg-gradient-to-br from-gray-100 to-gray-200 dark:from-gray-700 dark:to-gray-800 overflow-hidden">
      <!-- 相册封面图片 -->
      <img
        v-if="recommendation.coverImagePath"
        :src="convertImagePath(recommendation.coverImagePath)"
        :alt="recommendation.albumName"
        class="w-full h-full object-cover transition-transform duration-300 group-hover:scale-105"
        loading="lazy"
      />
      <!-- 占位符 -->
      <div v-else class="absolute inset-0 flex items-center justify-center">
        <svg class="w-16 h-16 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z" />
        </svg>
      </div>

      <!-- 覆盖层 -->
      <div class="absolute inset-0 bg-black/0 group-hover:bg-black/10 transition-colors duration-300"></div>

    </div>

    <!-- 相册信息 -->
    <div class="p-4">
      <h3 class="text-lg font-semibold text-gray-900 dark:text-white mb-2 truncate">
        {{ recommendation.albumName }}
      </h3>

      <!-- 进度条 -->
      <div class="w-full bg-gray-200 dark:bg-gray-700 rounded-full h-2 mb-2">
        <div
          class="bg-gradient-to-r from-blue-500 to-purple-500 h-2 rounded-full transition-all duration-500"
          :style="{ width: `${matchPercentage}%` }"
        ></div>
      </div>

      <!-- 匹配信息和数量 -->
      <div class="flex items-center justify-between text-sm">
        <span class="text-gray-500 dark:text-gray-500">{{ matchPercentage }}% 匹配</span>
        <span class="text-gray-600 dark:text-gray-400 font-medium">{{ recommendation.similarFaceCount }} / {{ recommendation.photoCount }}</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { AlbumRecommendation } from '@/api'

const props = defineProps<{
  recommendation: AlbumRecommendation
}>()

defineEmits<{
  click: []
}>()

// 计算已确认人脸占比百分比
const matchPercentage = computed(() => {
  const count = props.recommendation.photoCount || 0
  if (count === 0) return 0
  const confirmedFaces = props.recommendation.similarFaceCount || 0
  return Math.round((confirmedFaces / count) * 100)
})

// 转换图片路径，将数据库中的绝对路径转换为可访问的API路径
const convertImagePath = (path: string) => {
  if (!path) return path
  // 如果路径以/开头，去掉开头的/，然后加上/api/files/前缀
  if (path.startsWith('/')) {
    return `/api/files${path}`
  }
  return path
}

</script>
