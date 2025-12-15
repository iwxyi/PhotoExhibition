<template>
  <div class="min-h-screen bg-white dark:bg-gray-900">
    <nav class="sticky top-0 z-50 bg-white/80 dark:bg-gray-900/80 backdrop-blur-md border-b border-gray-200 dark:border-gray-800">
      <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div class="flex justify-between items-center h-16">
          <router-link to="/" class="text-2xl font-light tracking-wider">摄影展</router-link>
          <button @click="$router.back()" class="p-2 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-800">
            返回
          </button>
        </div>
      </div>
    </nav>

    <main class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-12">
      <div v-if="loading" class="flex justify-center items-center h-96">
        <div class="animate-spin rounded-full h-12 w-12 border-t-2 border-b-2 border-gray-900 dark:border-white"></div>
      </div>

      <div v-else-if="photo" class="grid grid-cols-1 lg:grid-cols-3 gap-12">
        <!-- 图片区域 -->
        <div class="lg:col-span-2">
          <div class="sticky top-24 flex justify-center">
            <img
              :src="getImageUrl(photo)"
              :alt="photo.filename"
              class="max-w-full w-auto h-auto max-h-[80vh] object-contain rounded-lg shadow-2xl"
              loading="lazy"
            />
          </div>
        </div>

        <!-- 信息区域 -->
        <div class="space-y-6">
          <div>
            <h1 class="text-3xl font-light mb-4">{{ photo.filename }}</h1>
            <p class="text-sm text-gray-500 dark:text-gray-400">查看次数: {{ photo.viewCount }}</p>
          </div>

          <!-- EXIF信息 -->
          <div v-if="photo.cameraModel || photo.lensModel" class="border-t border-gray-200 dark:border-gray-800 pt-6">
            <h2 class="text-lg font-light mb-4">拍摄信息</h2>
            <dl class="space-y-2 text-sm">
              <div v-if="photo.cameraModel" class="flex justify-between">
                <dt class="text-gray-500 dark:text-gray-400">相机</dt>
                <dd>{{ photo.cameraModel }}</dd>
              </div>
              <div v-if="photo.lensModel" class="flex justify-between">
                <dt class="text-gray-500 dark:text-gray-400">镜头</dt>
                <dd>{{ photo.lensModel }}</dd>
              </div>
              <div v-if="photo.focalLength" class="flex justify-between">
                <dt class="text-gray-500 dark:text-gray-400">焦距</dt>
                <dd>{{ photo.focalLength }}</dd>
              </div>
              <div v-if="photo.aperture" class="flex justify-between">
                <dt class="text-gray-500 dark:text-gray-400">光圈</dt>
                <dd>{{ photo.aperture }}</dd>
              </div>
              <div v-if="photo.shutterSpeed" class="flex justify-between">
                <dt class="text-gray-500 dark:text-gray-400">快门</dt>
                <dd>{{ photo.shutterSpeed }}</dd>
              </div>
              <div v-if="photo.iso" class="flex justify-between">
                <dt class="text-gray-500 dark:text-gray-400">ISO</dt>
                <dd>{{ photo.iso }}</dd>
              </div>
              <div v-if="photo.takenAt" class="flex justify-between">
                <dt class="text-gray-500 dark:text-gray-400">拍摄时间</dt>
                <dd>{{ new Date(photo.takenAt).toLocaleString('zh-CN') }}</dd>
              </div>
            </dl>
          </div>

          <!-- 色彩信息 -->
          <div v-if="photo.dominantColor || photo.colorPalette" class="border-t border-gray-200 dark:border-gray-800 pt-6">
            <h2 class="text-lg font-light mb-4">色彩</h2>
            <div v-if="photo.dominantColor" class="mb-4">
              <p class="text-sm text-gray-500 dark:text-gray-400 mb-2">主色调</p>
              <div
                class="w-full h-12 rounded-lg"
                :style="{ backgroundColor: photo.dominantColor }"
              ></div>
            </div>
            <div v-if="photo.colorPalette && photo.colorPalette.length > 0">
              <p class="text-sm text-gray-500 dark:text-gray-400 mb-2">调色板</p>
              <div class="flex gap-2">
                <div
                  v-for="(color, index) in photo.colorPalette"
                  :key="index"
                  class="w-12 h-12 rounded-lg"
                  :style="{ backgroundColor: color }"
                ></div>
              </div>
            </div>
          </div>

          <!-- 标签 -->
          <div v-if="photo.tags && photo.tags.length" class="border-t border-gray-200 dark:border-gray-800 pt-6">
            <h2 class="text-lg font-light mb-4">标签</h2>
            <div class="flex flex-wrap gap-2">
              <span
                v-for="t in photo.tags"
                :key="t.id"
                class="px-3 py-1 rounded-full text-sm cursor-pointer"
                :style="{ backgroundColor: t.color || 'rgba(59,130,246,0.1)', color: t.color ? '#fff' : '#2563eb' }"
                @click="openTag(t)"
              >
                {{ t.name }}
              </span>
            </div>
          </div>

          <!-- 人物信息 -->
          <div v-if="photo.faces && photo.faces.length" class="border-t border-gray-200 dark:border-gray-800 pt-6">
            <h2 class="text-lg font-light mb-4">人物</h2>
            <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <div
                v-for="face in photo.faces"
                :key="face.id"
                class="bg-gray-100 dark:bg-gray-800 rounded-lg border border-gray-200 dark:border-gray-700 p-3 flex gap-3"
              >
                <div class="w-20 h-20 bg-gray-700 rounded overflow-hidden relative">
                  <img :src="getFaceThumb(face)" class="absolute" :style="getFaceCropStyle(face)" loading="lazy" />
                </div>
                <div class="flex-1 space-y-2 text-sm">
                  <div class="text-gray-200">
                    <span class="text-gray-500 dark:text-gray-400 text-xs block">人物</span>
                    {{ face.personName || '未标注' }}
                  </div>
                  <div class="text-gray-200">
                    <span class="text-gray-500 dark:text-gray-400 text-xs block">备注</span>
                    {{ face.personDescription || '—' }}
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useRoute } from 'vue-router'
import { usePhotoStore } from '@/stores/photo'

const route = useRoute()
const photoStore = usePhotoStore()
const router = useRouter()

const photo = computed(() => photoStore.currentPhoto)
const loading = computed(() => photoStore.loading)

const openTag = (tag: any) => {
  if (!tag?.id) return
  router.push({ path: '/wall', query: { tagId: tag.id, tagName: tag.name } })
}

const getImageUrl = (photo: any) => {
  return `/api/files${photo.originalPath}`
}

const getFaceCropStyle = (face: any) => {
  if (!face?.width || !face?.height || face.width <= 0 || face.height <= 0) {
    return { position: 'absolute', inset: 0, objectFit: 'cover', objectPosition: 'center center' }
  }
  const centerX = ((face.x || 0) + face.width / 2) * 100
  const centerY = ((face.y || 0) + face.height / 2) * 100
  return {
    position: 'absolute',
    inset: 0,
    width: '100%',
    height: '100%',
    objectFit: 'cover',
    objectPosition: `${centerX}% ${centerY}%`
  }
}

const getFaceThumb = (face: any) => {
  if (face?.photoThumbnailPath) return `/api/files${face.photoThumbnailPath}`
  if (photo.value?.thumbnailPath) return `/api/files${photo.value.thumbnailPath}`
  return photo.value ? `/api/files${photo.value.originalPath}` : ''
}

onMounted(async () => {
  const photoId = parseInt(route.params.id as string)
  await photoStore.fetchPhotoById(photoId)
})
</script>

