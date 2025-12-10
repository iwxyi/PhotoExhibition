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
          <div class="sticky top-24">
            <img
              :src="getImageUrl(photo)"
              :alt="photo.filename"
              class="w-full h-auto rounded-lg shadow-2xl"
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
        </div>
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { usePhotoStore } from '@/stores/photo'

const route = useRoute()
const photoStore = usePhotoStore()

const photo = computed(() => photoStore.currentPhoto)
const loading = computed(() => photoStore.loading)

const getImageUrl = (photo: any) => {
  // 详情页显示原图
  return `/api/files${photo.originalPath}`
}

onMounted(async () => {
  const photoId = parseInt(route.params.id as string)
  await photoStore.fetchPhotoById(photoId)
})
</script>

