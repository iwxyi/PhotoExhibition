<template>
  <div class="min-h-screen bg-white dark:bg-gray-900">
    <nav class="sticky top-0 z-50 bg-white/80 dark:bg-gray-900/80 backdrop-blur-md border-b border-gray-200 dark:border-gray-800">
      <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div class="flex justify-between items-center h-12">
          <AppHeader :is-detail-page="true" />
          <button @click="$router.back()" class="p-2 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-800 transition-all duration-300 hover:scale-105 active:scale-95">
            返回
          </button>
        </div>
      </div>
    </nav>

    <main class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-12">
      <div v-if="loading" class="flex justify-center items-center h-96">
        <div class="animate-spin rounded-full h-12 w-12 border-t-2 border-b-2 border-gray-900 dark:border-white"></div>
      </div>

      <div v-else-if="photo" class="grid grid-cols-1 lg:grid-cols-12 gap-4">
        <!-- 图片区域 -->
        <div class="lg:col-span-8">
          <div class="sticky top-24 flex justify-center">
            <div class="relative group overflow-hidden rounded-xl shadow-2xl">
              <img
                :src="getImageUrl(photo)"
                :alt="photo.filename"
                class="max-w-full w-auto h-auto max-h-[80vh] object-contain transition-transform duration-500 group-hover:scale-105"
                loading="lazy"
              />
              <div class="absolute inset-0 bg-gradient-to-t from-black/30 to-transparent opacity-0 group-hover:opacity-100 transition-opacity duration-300"></div>
            </div>
          </div>
        </div>

        <!-- 信息区域 - 改为右侧垂直信息流 -->
        <div class="lg:col-span-4 space-y-4">
          <!-- 标题卡片 -->
          <div class="bg-gradient-to-br from-gray-50 to-white dark:from-gray-800 dark:to-gray-900 rounded-2xl p-5 shadow-lg border border-gray-100 dark:border-gray-700 animate-fade-in-up">
            <h1 class="text-2xl font-bold mb-3 truncate">{{ photo.filename }}</h1>
            <div class="flex items-center gap-4 text-sm text-gray-500 dark:text-gray-400">
              <span v-if="photo.takenAt" class="flex items-center gap-1">
                <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z"/>
                </svg>
                {{ formatDate(photo.takenAt) }}
              </span>
              <span class="flex items-center gap-1">
                <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z"/>
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z"/>
                </svg>
                {{ photo.viewCount }}
              </span>
            </div>
          </div>

          <!-- 调色板卡片 -->
          <div v-if="photo.colorPalette && photo.colorPalette.length > 0" class="bg-gradient-to-br from-gray-50 to-white dark:from-gray-800 dark:to-gray-900 rounded-2xl p-5 shadow-lg border border-gray-100 dark:border-gray-700 animate-fade-in-up" style="animation-delay: 0.1s">
            <h2 class="text-lg font-semibold mb-4 flex items-center gap-2">
              <svg class="w-5 h-5 text-blue-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M7 21a4 4 0 01-4-4V5a2 2 0 012-2h4a2 2 0 012 2v12a4 4 0 01-4 4zm0 0h12a2 2 0 002-2v-4a2 2 0 00-2-2h-2.343M11 7.343l1.657-1.657a2 2 0 012.828 0l2.829 2.829a2 2 0 010 2.828l-8.486 8.485M7 17h.01"/>
              </svg>
              调色板
            </h2>
            <div class="flex flex-wrap gap-2">
              <div
                v-for="(color, index) in photo.colorPalette"
                :key="index"
                class="relative group"
              >
                <div
                  class="w-12 h-12 rounded-lg shadow-md transition-all duration-300 hover:scale-110 hover:shadow-xl hover:-translate-y-1 cursor-pointer"
                  :style="{ backgroundColor: color, animationDelay: `${index * 0.03}s` }"
                ></div>
                <div class="absolute -bottom-8 left-1/2 -translate-x-1/2 px-2 py-1 bg-gray-900 dark:bg-white text-white dark:text-gray-900 text-xs rounded opacity-0 group-hover:opacity-100 transition-all duration-300 whitespace-nowrap pointer-events-none z-10">
                  {{ color }}
                </div>
                <div class="absolute -bottom-8 left-1/2 -translate-x-1/2 w-0 h-0 border-l-4 border-r-4 border-t-4 border-transparent border-t-gray-900 dark:border-t-white opacity-0 group-hover:opacity-100 transition-all duration-300 pointer-events-none"></div>
              </div>
            </div>
          </div>

          <!-- EXIF信息卡片 -->
          <div v-if="photo.cameraModel || photo.lensModel || photo.focalLength || photo.aperture || photo.shutterSpeed || photo.iso" class="bg-gradient-to-br from-gray-50 to-white dark:from-gray-800 dark:to-gray-900 rounded-2xl p-5 shadow-lg border border-gray-100 dark:border-gray-700 animate-fade-in-up" style="animation-delay: 0.2s">
            <h2 class="text-lg font-semibold mb-4 flex items-center gap-2">
              <svg class="w-5 h-5 text-green-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M3 9a2 2 0 012-2h.93a2 2 0 001.664-.89l.812-1.22A2 2 0 0110.07 4h3.86a2 2 0 011.664.89l.812 1.22A2 2 0 0018.07 7H19a2 2 0 012 2v9a2 2 0 01-2 2H5a2 2 0 01-2-2V9z"/>
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 13a3 3 0 11-6 0 3 3 0 016 0z"/>
              </svg>
              拍摄信息
            </h2>
            <div class="space-y-3">
              <!-- 相机和镜头 -->
              <div v-if="photo.cameraModel || photo.lensModel" class="grid grid-cols-2 gap-3">
                <div v-if="photo.cameraModel" class="bg-white dark:bg-gray-700/50 rounded-xl p-3 border border-gray-100 dark:border-gray-600 hover:shadow-md transition-shadow">
                  <span class="text-xs text-gray-400 block mb-1">相机</span>
                  <span class="text-sm font-medium">{{ photo.cameraModel }}</span>
                </div>
                <div v-if="photo.lensModel" class="bg-white dark:bg-gray-700/50 rounded-xl p-3 border border-gray-100 dark:border-gray-600 hover:shadow-md transition-shadow">
                  <span class="text-xs text-gray-400 block mb-1">镜头</span>
                  <span class="text-sm font-medium">{{ photo.lensModel }}</span>
                </div>
              </div>
              <!-- 焦距光圈快门ISO合并一行 -->
              <div v-if="photo.focalLength || photo.aperture || photo.shutterSpeed || photo.iso" class="flex flex-wrap gap-2">
                <div v-if="photo.focalLength" class="bg-white dark:bg-gray-700/50 rounded-lg px-3 py-2 border border-gray-100 dark:border-gray-600">
                  <span class="text-xs text-gray-400">焦距</span>
                  <span class="text-sm font-medium ml-1">{{ photo.focalLength }}</span>
                </div>
                <div v-if="photo.aperture" class="bg-white dark:bg-gray-700/50 rounded-lg px-3 py-2 border border-gray-100 dark:border-gray-600">
                  <span class="text-xs text-gray-400">光圈</span>
                  <span class="text-sm font-medium ml-1">{{ photo.aperture }}</span>
                </div>
                <div v-if="photo.shutterSpeed" class="bg-white dark:bg-gray-700/50 rounded-lg px-3 py-2 border border-gray-100 dark:border-gray-600">
                  <span class="text-xs text-gray-400">快门</span>
                  <span class="text-sm font-medium ml-1">{{ photo.shutterSpeed }}</span>
                </div>
                <div v-if="photo.iso" class="bg-white dark:bg-gray-700/50 rounded-lg px-3 py-2 border border-gray-100 dark:border-gray-600">
                  <span class="text-xs text-gray-400">ISO</span>
                  <span class="text-sm font-medium ml-1">{{ photo.iso }}</span>
                </div>
              </div>
            </div>
          </div>

          <!-- 标签卡片 -->
          <div v-if="photo.tags && photo.tags.length" class="bg-gradient-to-br from-gray-50 to-white dark:from-gray-800 dark:to-gray-900 rounded-2xl p-5 shadow-lg border border-gray-100 dark:border-gray-700 animate-fade-in-up" style="animation-delay: 0.3s">
            <h2 class="text-lg font-semibold mb-4 flex items-center gap-2">
              <svg class="w-5 h-5 text-purple-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M7 7h.01M7 3h5c.512 0 1.024.195 1.414.586l7 7a2 2 0 010 2.828l-7 7a2 2 0 01-2.828 0l-7-7A1.994 1.994 0 013 12V7a4 4 0 014-4z"/>
              </svg>
              标签
            </h2>
            <div class="flex flex-wrap gap-2">
              <a
                v-for="t in photo.tags"
                :key="t.id"
                :href="`/search?tagId=${t.id}&tagName=${encodeURIComponent(t.name)}`"
                target="_blank"
                class="px-4 py-1.5 rounded-full text-sm cursor-pointer transition-all duration-300 hover:scale-105 hover:shadow-lg"
                :style="{ backgroundColor: t.color || 'rgba(59,130,246,0.1)', color: t.color ? '#fff' : '#2563eb' }"
              >
                {{ t.name }}
              </a>
            </div>
          </div>

          <!-- 人物卡片 -->
          <div v-if="photo.faces && photo.faces.length" class="bg-gradient-to-br from-gray-50 to-white dark:from-gray-800 dark:to-gray-900 rounded-2xl p-5 shadow-lg border border-gray-100 dark:border-gray-700 animate-fade-in-up" style="animation-delay: 0.4s">
            <h2 class="text-lg font-semibold mb-4 flex items-center gap-2">
              <svg class="w-5 h-5 text-orange-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z"/>
              </svg>
              人物
            </h2>
            <div class="grid grid-cols-3 sm:grid-cols-4 md:grid-cols-5 gap-3">
              <a
                v-for="(face, idx) in photo.faces"
                :key="face.id"
                :href="face.personId ? `/wall?personId=${face.personId}&personName=${encodeURIComponent(face.personName || '')}` : `/search?faceId=${face.id}`"
                target="_blank"
                class="group flex flex-col items-center p-2 rounded-xl hover:bg-gray-100 dark:hover:bg-gray-700/50 transition-colors duration-200"
              >
                <div
                  class="w-14 h-14 rounded-full overflow-hidden shadow-md ring-2 transition-all duration-300 group-hover:scale-110 group-hover:shadow-lg"
                  :class="getFaceColorClass(idx)"
                  :style="getFaceAvatarStyle(face)"
                ></div>
                <span class="mt-2 text-xs font-medium truncate max-w-[60px] text-gray-600 dark:text-gray-400 group-hover:text-orange-500 transition-colors duration-200">
                  {{ face.personName || '未命名' }}
                </span>
              </a>
            </div>
          </div>
        </div>
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { usePhotoStore } from '@/stores/photo'
import AppHeader from '@/components/AppHeader.vue'

const route = useRoute()
const photoStore = usePhotoStore()

const photo = computed(() => photoStore.currentPhoto)
const loading = computed(() => photoStore.loading)

// 人物颜色数组，用于区分不同人物
const faceColorClasses = [
  'border-blue-400',
  'border-green-400',
  'border-yellow-400',
  'border-red-400',
  'border-purple-400',
  'border-pink-400',
  'border-indigo-400',
  'border-cyan-400',
]

const getFaceColorClass = (idx: number) => {
  return faceColorClasses[idx % faceColorClasses.length]
}

const formatDate = (dateStr: string) => {
  const date = new Date(dateStr)
  return date.toLocaleDateString('zh-CN', { year: 'numeric', month: 'short', day: 'numeric' })
}

const getImageUrl = (photo: any) => {
  return `/api/files${photo.originalPath}`
}

// 人脸头像 URL 解析 - 直接使用当前显示的图片
const resolveFaceAvatarUrl = (face: any) => {
  if (!photo.value) return ''
  // 直接使用当前显示的图片
  const firstPath = [
    photo.value.originalPath,
    photo.value.webpPath,
    photo.value.thumbnailPath,
    face.photoOriginalPath,
    face.photoThumbnailPath
  ].find(p => p && typeof p === 'string' && p.length > 0) || ''
  const base = firstPath
    ? firstPath.startsWith('/api/files') ? firstPath : `/api/files${firstPath}`
    : ''
  if (!base) return ''
  const prefix = '/api/files'
  if (base.startsWith(prefix)) {
    const raw = base.slice(prefix.length)
    return `${prefix}${encodeURI(raw)}`
  }
  return encodeURI(base)
}

// 人脸头像样式（使用背景图实现裁剪）
const getFaceAvatarStyle = (face: any) => {
  const base = resolveFaceAvatarUrl(face)
  const hasSize = face?.width && face?.height && face.width > 0 && face.height > 0
  if (!base) {
    return { backgroundColor: '#374151', backgroundSize: 'cover', backgroundPosition: 'center center' }
  }
  if (!hasSize) {
    return { backgroundImage: `url(${base})`, backgroundSize: '200%', backgroundPosition: 'center center' }
  }

  // 人脸中心坐标（0-1 范围转为百分比）
  const faceCenterX = (face.x || 0) + face.width / 2
  const faceCenterY = (face.y || 0) + face.height / 2

  // 计算相对于中心的偏移（-0.5 到 0.5）
  const offsetX = faceCenterX - 0.5
  const offsetY = faceCenterY - 0.5

  // 距离中心的欧几里得距离
  const distFromCenter = Math.sqrt(offsetX * offsetX + offsetY * offsetY)

  // 添加位置补偿：距离中心越远，补偿越大
  // 边缘区域由于坐标精度损失更大，需要额外调整
  const compensationFactor = 0.3 // 补偿系数
  const compensationX = offsetX * compensationFactor * (distFromCenter / 0.5)
  const compensationY = offsetY * compensationFactor * (distFromCenter / 0.5)

  // 应用补偿后的中心坐标
  const centerX = (faceCenterX + compensationX) * 100
  const centerY = (faceCenterY + compensationY) * 100

  // 目标填充比例：人脸占满圆圈的 80%
  const fillRatio = 0.8
  let scalePercent = (fillRatio / face.width) * 100
  // 限制缩放范围
  scalePercent = Math.min(Math.max(scalePercent, 150), 500)

  return {
    backgroundImage: `url(${base})`,
    backgroundSize: `${scalePercent}%`,
    backgroundPosition: `${centerX}% ${centerY}%`
  }
}

onMounted(async () => {
  const photoId = parseInt(route.params.id as string)
  await photoStore.fetchPhotoById(photoId)
})
</script>

