<template>
  <div
    class="group relative bg-white dark:bg-gray-800 rounded-xl shadow-sm hover:shadow-lg transition-all duration-300 cursor-pointer transform hover:-translate-y-1 overflow-hidden border border-gray-100 dark:border-gray-700"
    @click="$emit('click')"
  >
    <!-- 人物头像区域 -->
    <div class="relative aspect-square bg-gradient-to-br from-gray-100 to-gray-200 dark:from-gray-700 dark:to-gray-800 overflow-hidden">
      <!-- 代表图片网格 -->
      <div class="absolute inset-0 grid grid-cols-2 grid-rows-2 gap-0.5 p-1">
        <div
          v-for="(photo, index) in samplePhotos"
          :key="index"
          class="relative bg-gray-200 dark:bg-gray-600 rounded overflow-hidden"
          :class="getPhotoClass(index)"
        >
          <img
            v-if="photo"
            :src="convertImagePath(photo.photoThumbnailPath || photo.thumbnailPath)"
            :alt="`人物照片 ${index + 1}`"
            class="w-full h-full object-cover transition-transform duration-300 group-hover:scale-105"
            loading="lazy"
          />
          <div v-else class="w-full h-full bg-gray-300 dark:bg-gray-600 flex items-center justify-center">
            <svg class="w-6 h-6 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z" />
            </svg>
          </div>
        </div>
      </div>

      <!-- 覆盖层 -->
      <div class="absolute inset-0 bg-black/0 group-hover:bg-black/10 transition-colors duration-300"></div>

      <!-- 统计信息 -->
      <div class="absolute bottom-2 left-2 right-2 flex justify-between items-center">
        <div class="flex items-center space-x-2 text-white text-xs font-medium bg-black/50 backdrop-blur-sm rounded-full px-2 py-1">
          <svg class="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z" />
          </svg>
          <span>{{ person.faceCount || 0 }}</span>
        </div>

        <div class="flex items-center space-x-2 text-white text-xs font-medium bg-black/50 backdrop-blur-sm rounded-full px-2 py-1">
          <svg class="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M19 11H5m14 0a2 2 0 012 2v6a2 2 0 01-2 2H5a2 2 0 01-2-2v-6a2 2 0 012-2m14 0V9a2 2 0 00-2-2M5 11V9a2 2 0 012-2m0 0V5a2 2 0 012-2h6a2 2 0 012 2v2M7 7h10" />
          </svg>
          <span>{{ person.albumCount || 0 }}</span>
        </div>
      </div>
    </div>

    <!-- 人物信息 -->
    <div class="p-4">
      <h3 class="text-lg font-semibold text-gray-900 dark:text-white mb-1 truncate">
        {{ person.name }}
      </h3>
      <p
        v-if="person.description"
        class="text-sm text-gray-600 dark:text-gray-400 line-clamp-2 mb-2"
      >
        {{ person.description }}
      </p>

      <!-- 统计信息 -->
      <div class="flex flex-wrap gap-3 text-xs text-gray-500 dark:text-gray-400">
        <div v-if="person.faceCount && person.faceCount > 0" class="flex items-center">
          <svg class="w-4 h-4 mr-1" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z" />
          </svg>
          <span>{{ person.faceCount }} 张人脸照片</span>
        </div>
        <div v-if="person.albumCount && person.albumCount > 0" class="flex items-center">
          <svg class="w-4 h-4 mr-1" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 11H5m14 0a2 2 0 012 2v6a2 2 0 01-2 2H5a2 2 0 01-2-2v-6a2 2 0 012-2m14 0V9a2 2 0 00-2-2M5 11V9a2 2 0 012-2m0 0V5a2 2 0 012-2h6a2 2 0 012 2v2M7 7h10" />
          </svg>
          <span>{{ person.albumCount }} 个相册</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { PersonSummary, Photo, personApi } from '@/api'

const props = defineProps<{
  person: PersonSummary
}>()

defineEmits<{
  click: []
}>()

// 人物的代表图片数组
const samplePhotos = ref<(Photo | null)[]>([null, null, null, null])

// 在组件挂载时获取该人物的4张代表照片
onMounted(async () => {
  try {
    const response = await personApi.getPersonSamplePhotos(props.person.id)
    const faces = response.data || []

    // 转换为Photo格式
    const formattedPhotos: (Photo | null)[] = faces.map(face => ({
      id: face.photoId || 0,
      albumId: 0,
      filename: face.photoFilename || '',
      originalPath: face.photoOriginalPath || '',
      thumbnailPath: face.photoThumbnailPath || '',
      createdAt: '',
      viewCount: 0,
      likeCount: 0,
      isFeatured: false
    } as Photo))

    // 填充到4个位置
    for (let i = 0; i < 4; i++) {
      samplePhotos.value[i] = formattedPhotos[i] || null
    }
  } catch (error) {
    console.error('获取人物代表图片失败:', error)
    // 如果获取失败，至少显示sampleThumbnailPath作为fallback
    if (props.person.sampleThumbnailPath) {
      samplePhotos.value[0] = {
        id: props.person.samplePhotoId || 0,
        albumId: 0,
        filename: '',
        originalPath: props.person.sampleOriginalPath || '',
        thumbnailPath: props.person.sampleThumbnailPath,
        createdAt: '',
        viewCount: 0,
        likeCount: 0,
        isFeatured: false
      } as Photo
    }
  }
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

// 根据索引决定照片的样式
const getPhotoClass = (index: number) => {
  const classes = [
    'rounded-tl-lg', // 左上
    'rounded-tr-lg', // 右上
    'rounded-bl-lg', // 左下
    'rounded-br-lg'  // 右下
  ]
  return classes[index] || ''
}
</script>

<style scoped>
.line-clamp-2 {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
</style>
