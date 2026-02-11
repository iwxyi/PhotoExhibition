<template>
  <div
    class="group relative bg-white dark:bg-gray-800 rounded-xl shadow-sm hover:shadow-lg transition-all duration-300 cursor-pointer transform hover:-translate-y-1 overflow-hidden border border-gray-100 dark:border-gray-700"
    @click="$emit('click')"
  >
    <!-- 顶部：头像 + 名字 -->
    <div class="flex items-start gap-3 p-4 pb-2">
      <!-- 人物头像（圆形、小） -->
      <div class="flex-shrink-0 w-8 h-8 rounded-full overflow-hidden shadow ring-2 ring-white/50 dark:ring-gray-700/50">
        <img
          v-if="person.sampleThumbnailPath"
          :src="convertImagePath(person.sampleThumbnailPath)"
          :alt="person.name"
          class="w-full h-full object-cover"
        />
        <div v-else class="w-full h-full bg-gradient-to-br from-gray-200 to-gray-300 dark:from-gray-700 dark:to-gray-800 flex items-center justify-center">
          <svg class="w-4 h-4 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z" />
          </svg>
        </div>
      </div>
      <!-- 名字和备注 -->
      <div class="flex-1 min-w-0 pt-0.5">
        <!-- 名字 -->
        <h3 class="text-base font-medium text-gray-900 dark:text-white truncate">
          {{ person.name }}
        </h3>
        <!-- 备注（如果有） -->
        <p
          v-if="person.description"
          class="text-xs text-gray-500 dark:text-gray-400 truncate"
        >
          {{ person.description }}
        </p>
      </div>
    </div>

    <!-- 封面照片区域 -->
    <div class="relative px-4 pb-2">
      <!-- 使用 CoverDisplay 组件展示封面图片（不显示数量） -->
      <CoverDisplay
        :covers="coverPhotos"
        :size="props.size"
      />

      <!-- 相册数量（底部） -->
      <div class="absolute bottom-2 right-5 flex items-center gap-2 text-white text-xs font-medium bg-black/50 backdrop-blur-sm rounded-full px-2.5 py-1">
        <svg class="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z" />
        </svg>
        <span>{{ person.faceCount || 0 }}</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { PersonSummary, Photo, personApi } from '@/api'
import CoverDisplay from './CoverDisplay.vue'

interface Props {
  person: PersonSummary
  size?: 'sm' | 'md' | 'lg'
}

const props = withDefaults(defineProps<Props>(), {
  size: 'md'
})

defineEmits<{
  click: []
}>()

// 人物的代表图片数组
const samplePhotos = ref<(Photo | null)[]>([null, null, null, null])

// 将 samplePhotos 转换为 CoverDisplay 需要的格式
const coverPhotos = computed(() => {
  return samplePhotos.value.filter((p): p is Photo => p !== null)
})

// 在组件挂载时获取该人物的4张代表照片
onMounted(async () => {
  try {
    const response = await personApi.getPersonSamplePhotos(props.person.id)
    const faces = response.data || []

    // 转换为Photo格式（包含宽高信息用于布局判断）
    const formattedPhotos: (Photo | null)[] = faces.map(face => ({
      id: face.photoId || 0,
      albumId: 0,
      filename: face.photoFilename || '',
      originalPath: face.photoOriginalPath || '',
      thumbnailPath: face.photoThumbnailPath || '',
      width: face.photoWidth,
      height: face.photoHeight,
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
        width: undefined,
        height: undefined,
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
</script>
