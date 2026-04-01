<template>
  <div
    class="group relative bg-white dark:bg-gray-800 rounded-2xl shadow-sm overflow-hidden border border-gray-100 dark:border-gray-700"
    @click="$emit('click')"
  >
    <!-- 顶部：头像 + 名字区域（在封面上方） -->
    <div class="relative px-4 pt-4 pb-0">
      <div :class="['flex gap-3', person.description ? 'items-start' : 'items-center']">
        <!-- 头像（带边框光晕和旋转动画） -->
        <div class="relative flex-shrink-0">
          <!-- 头像边框光晕 -->
          <div class="absolute -inset-0.5 rounded-full opacity-50 transition-all duration-300 group-hover:opacity-80" :class="avatarBorderClass"></div>
          <div
            class="relative w-12 h-12 rounded-full overflow-hidden shadow ring-2 ring-white dark:ring-gray-800 transform transition-all duration-500 group-hover:scale-110 group-hover:rotate-3"
          >
            <img
              v-if="person.sampleThumbnailPath"
              :src="convertImagePath(person.sampleThumbnailPath)"
              :alt="person.name"
              class="w-full h-full object-cover"
            />
            <div v-else class="w-full h-full bg-gradient-to-br from-gray-200 to-gray-300 dark:from-gray-700 dark:to-gray-800 flex items-center justify-center">
              <svg class="w-6 h-6 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z" />
              </svg>
            </div>
          </div>
        </div>

        <!-- 名字和备注 -->
        <div class="flex-1 min-w-0" :class="person.description ? 'pt-0.5' : 'pt-0.5'">
          <!-- 名字 -->
          <h3 class="text-base font-medium text-gray-900 dark:text-white truncate group-hover:text-blue-600 dark:group-hover:text-blue-400 transition-colors duration-200">
            {{ person.name }}
          </h3>
          <!-- 备注（如果有） -->
          <p
            v-if="person.description"
            class="text-xs text-gray-500 dark:text-gray-400 truncate mt-0.5"
          >
            {{ person.description }}
          </p>
        </div>
      </div>
    </div>

    <!-- 封面照片区域（带动态氛围光） -->
    <div class="relative mt-2">
      <!-- 动态氛围光 - 根据照片数量/活跃度变化 -->
      <div
        class="absolute inset-0 opacity-30 transition-all duration-500 rounded-lg"
        :class="ambientColorClass"
      ></div>

      <!-- 封面照片 -->
      <CoverDisplay
        :covers="coverPhotos"
        :size="props.size"
        :keep-square="true"
      />

      <!-- 照片数量徽章（右下角） -->
      <div class="absolute bottom-3 right-3 flex items-center gap-1.5 text-white text-xs font-medium bg-black/40 backdrop-blur-md rounded-full px-2.5 py-1 shadow-lg">
        <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z" />
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
import { buildPhotoAssetUrl } from '@/utils/photoUrl'

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

// 根据照片数量计算动态氛围颜色
const ambientColorClass = computed(() => {
  const count = props.person.faceCount || 0
  if (count >= 100) {
    return 'bg-gradient-to-br from-amber-400/30 via-orange-500/20 to-red-500/30'
  } else if (count >= 50) {
    return 'bg-gradient-to-br from-blue-400/30 via-indigo-500/20 to-purple-500/30'
  } else if (count >= 20) {
    return 'bg-gradient-to-br from-green-400/30 via-emerald-500/20 to-teal-500/30'
  } else {
    return 'bg-gradient-to-br from-gray-400/20 via-gray-500/10 to-gray-600/20'
  }
})

// 根据照片数量计算头像边框颜色
const avatarBorderClass = computed(() => {
  const count = props.person.faceCount || 0
  if (count >= 100) {
    return 'bg-gradient-to-r from-amber-400 to-orange-500'
  } else if (count >= 50) {
    return 'bg-gradient-to-r from-blue-400 to-indigo-500'
  } else if (count >= 20) {
    return 'bg-gradient-to-r from-green-400 to-emerald-500'
  } else {
    return 'bg-gradient-to-r from-gray-400 to-gray-500'
  }
})

// 在组件挂载时获取该人物的4张代表照片
onMounted(async () => {
  try {
    const response = await personApi.getPersonSamplePhotos(props.person.id)
    const faces = response.data || []

    // 根据封面数量决定使用哪种缩略图
    // 只有1张时用 mediumThumbPath，多张时用 smallThumbPath
    const useMediumThumb = faces.length === 1

    // 转换为Photo格式（包含宽高信息用于布局判断）
    const formattedPhotos: (Photo | null)[] = faces.map(face => {
      // 使用后端返回的正确路径
      const mediumThumbPath = face.photoMediumThumbPath || face.photoThumbnailPath || ''
      const smallThumbPath = face.photoSmallThumbPath || ''

      // 根据封面数量设置对应的缩略图路径
      // CoverDisplay 的逻辑：只有1张时用 mediumThumbPath，多张时用 smallThumbPath
      if (useMediumThumb) {
        return {
          id: face.photoId || 0,
          albumId: 0,
          filename: face.photoFilename || '',
          originalPath: face.photoOriginalPath || '',
          mediumThumbPath: mediumThumbPath,
          width: face.photoWidth,
          height: face.photoHeight,
          createdAt: '',
          viewCount: 0,
          likeCount: 0,
          isFeatured: false
        } as Photo
      } else {
        return {
          id: face.photoId || 0,
          albumId: 0,
          filename: face.photoFilename || '',
          originalPath: face.photoOriginalPath || '',
          smallThumbPath: smallThumbPath,
          width: face.photoWidth,
          height: face.photoHeight,
          createdAt: '',
          viewCount: 0,
          likeCount: 0,
          isFeatured: false
        } as Photo
      }
    })

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
        // 只有1张时使用 mediumThumbPath
        mediumThumbPath: props.person.sampleThumbnailPath,
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
  return buildPhotoAssetUrl({
    id: props.person.samplePhotoId,
    originalPath: props.person.sampleOriginalPath,
    mediumThumbPath: path
  }, 'medium') || path
}
</script>
