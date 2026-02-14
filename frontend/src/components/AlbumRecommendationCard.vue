<template>
  <div
    class="group relative bg-white dark:bg-gray-800 rounded-xl overflow-hidden border border-gray-100 dark:border-gray-700 cursor-pointer transition-all duration-300 hover:shadow-xl hover:-translate-y-1 hover:border-blue-400/50 dark:hover:border-blue-500/30"
    @click="$emit('click')"
  >
    <!-- 相册封面 -->
    <div class="relative bg-gradient-to-br from-gray-100 to-gray-200 dark:from-gray-700 dark:to-gray-800 overflow-hidden">
      <!-- 使用 CoverDisplay 组件展示最多3张图片 -->
      <CoverDisplay
        :covers="coverPhotos"
        :photo-count="recommendation.photoCount || 0"
        size="lg"
      />
      <!-- 占位符（当没有封面图时显示） -->
      <div v-if="!hasAnyCover" class="absolute inset-0 flex items-center justify-center">
        <svg class="w-16 h-16 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z" />
        </svg>
      </div>
      <!-- 悬停遮罩效果 -->
      <div class="absolute inset-0 bg-gradient-to-t from-black/40 via-black/0 to-black/0 opacity-0 group-hover:opacity-100 transition-opacity duration-300"></div>
    </div>

    <!-- 相册信息 -->
    <div class="p-4 transition-all duration-300 group-hover:bg-gray-50 dark:group-hover:bg-gray-750">
      <h3 class="text-lg font-semibold text-gray-900 dark:text-white mb-2 truncate group-hover:text-blue-600 dark:group-hover:text-blue-400 transition-colors">
        {{ recommendation.albumName }}
      </h3>

      <!-- 进度条 - 使用渐变色 -->
      <div class="w-full bg-gray-100 dark:bg-gray-700 rounded-full h-1.5 mb-2 overflow-hidden">
        <div
          class="h-full rounded-full transition-all duration-500 ease-out"
          :class="[
            matchPercentage >= 80 ? 'bg-gradient-to-r from-green-400 to-emerald-500' :
            matchPercentage >= 50 ? 'bg-gradient-to-r from-blue-400 via-purple-500 to-pink-500' :
            'bg-gradient-to-r from-blue-400 to-purple-500'
          ]"
          :style="{ width: `${matchPercentage}%` }"
        ></div>
      </div>

      <!-- 匹配信息和数量 -->
      <div class="flex items-center justify-between text-sm">
        <span class="text-gray-500 dark:text-gray-500 group-hover:text-gray-700 dark:group-hover:text-gray-400 transition-colors">{{ matchPercentage }}% 匹配</span>
        <span class="text-gray-600 dark:text-gray-400 font-medium">{{ recommendation.similarFaceCount }} / {{ recommendation.photoCount }}</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { AlbumRecommendation } from '@/api'
import CoverDisplay from '@/components/CoverDisplay.vue'

const props = defineProps<{
  recommendation: AlbumRecommendation
}>()

defineEmits<{
  click: []
}>()

// 构建封面照片数组（用于 CoverDisplay）
interface Photo {
  id: number
  filename: string
  thumbnailPath?: string
  smallThumbPath?: string
  width?: number
  height?: number
}

const coverPhotos = computed<Photo[]>(() => {
  const photos: Photo[] = []
  const paths = [
    props.recommendation.coverImagePath1,
    props.recommendation.coverImagePath2,
    props.recommendation.coverImagePath3
  ]

  paths.forEach((path, index) => {
    if (path) {
      photos.push({
        id: index + 1,
        filename: `cover_${index + 1}`,
        thumbnailPath: path
      })
    }
  })

  return photos
})

// 是否有任何封面图
const hasAnyCover = computed(() => {
  return !!props.recommendation.coverImagePath1 ||
         !!props.recommendation.coverImagePath2 ||
         !!props.recommendation.coverImagePath3
})

// 计算已确认人脸占比百分比
const matchPercentage = computed(() => {
  const count = props.recommendation.photoCount || 0
  if (count === 0) return 0
  const confirmedFaces = props.recommendation.similarFaceCount || 0
  return Math.round((confirmedFaces / count) * 100)
})

</script>
