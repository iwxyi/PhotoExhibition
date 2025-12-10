<template>
  <div class="min-h-screen bg-white dark:bg-gray-900">
    <nav class="sticky top-0 z-50 bg-white/80 dark:bg-gray-900/80 backdrop-blur-md border-b border-gray-200 dark:border-gray-800">
      <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div class="flex justify-between items-center h-16">
          <div class="flex items-center space-x-8">
            <router-link to="/" class="text-2xl font-light tracking-wider">摄影展</router-link>
            <NavLinks />
          </div>
          <div class="flex items-center space-x-4">
            <button @click="themeStore.toggleTheme" class="p-2 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-800">
              <svg v-if="!themeStore.isDark" class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M20.354 15.354A9 9 0 018.646 3.646 9.003 9.003 0 0012 21a9.003 9.003 0 008.354-5.646z" />
              </svg>
              <svg v-else class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 3v1m0 16v1m9-9h-1M4 12H3m15.364 6.364l-.707-.707M6.343 6.343l-.707-.707m12.728 0l-.707.707M6.343 17.657l-.707.707M16 12a4 4 0 11-8 0 4 4 0 018 0z" />
              </svg>
            </button>
            <SettingsMenu />
          </div>
        </div>
      </div>
    </nav>

    <main class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-12">
      <div class="text-center mb-12">
        <h1 class="text-4xl font-light mb-4">随机精选</h1>
        <p class="text-gray-600 dark:text-gray-400">发现高质量摄影作品</p>
      </div>

      <div v-if="loading" class="flex justify-center items-center h-96">
        <div class="animate-spin rounded-full h-12 w-12 border-t-2 border-b-2 border-gray-900 dark:border-white"></div>
      </div>

      <div v-else :class="gridClass">
        <div
          v-for="(photo, idx) in photos"
          :key="photo.id"
          class="photo-card cursor-pointer group"
          @click="openViewer(idx)"
        >
          <div class="aspect-square overflow-hidden rounded-lg">
            <img
              :src="getImageUrl(photo)"
              :alt="photo.filename"
              class="photo-image w-full h-full"
              loading="lazy"
            />
          </div>
          <div class="gradient-overlay">
            <div class="absolute bottom-0 left-0 right-0 p-6 text-white">
              <p class="text-sm font-light">{{ photo.filename }}</p>
              <div v-if="photo.qualityScore" class="mt-2 flex items-center gap-2">
                <span class="text-xs opacity-75">质量评分: {{ photo.qualityScore.toFixed(1) }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div v-if="hasMore" class="mt-12 text-center">
        <button
          @click="loadMore"
          :disabled="loading"
          class="px-6 py-3 bg-gray-900 dark:bg-white text-white dark:text-gray-900 rounded-lg hover:bg-gray-800 dark:hover:bg-gray-100 transition-colors disabled:opacity-50"
        >
          {{ loading ? '加载中...' : '加载更多' }}
        </button>
      </div>
    </main>
    <PhotoViewer
      v-model:visible="viewerVisible"
      :photos="photos"
      :start-index="viewerIndex"
    />
  </div>
</template>

<script setup lang="ts">
defineOptions({ name: 'Random' })
import { ref, computed, onMounted, onActivated, onDeactivated, nextTick } from 'vue'
import { usePhotoStore } from '@/stores/photo'
import { useThemeStore } from '@/stores/theme'
import NavLinks from '@/components/NavLinks.vue'
import PhotoViewer from '@/components/PhotoViewer.vue'
import SettingsMenu from '@/components/SettingsMenu.vue'
import { useUiSettings } from '@/composables/useUiSettings'

const photoStore = usePhotoStore()
const themeStore = useThemeStore()
const { previewSize } = useUiSettings()

const photos = computed(() => photoStore.photos)
const loading = computed(() => photoStore.loading)
const currentPage = ref(0)
const hasMore = ref(true)
const savedScrollTop = ref(0)

const getImageUrl = (photo: any) => {
  if (photo.webpPath) {
    return `/api/files${photo.webpPath}`
  }
  if (photo.thumbnailPath) {
    return `/api/files${photo.thumbnailPath}`
  }
  return `/api/files${photo.originalPath}`
}

const viewerVisible = ref(false)
const viewerIndex = ref(0)
const gridClass = computed(() => {
  if (previewSize.value === 'sm') return 'grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5 gap-4'
  if (previewSize.value === 'lg') return 'grid grid-cols-1 md:grid-cols-2 lg:grid-cols-2 xl:grid-cols-3 gap-7'
  return 'grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6'
})

const openViewer = (idx: number) => {
  viewerIndex.value = idx
  viewerVisible.value = true
}

const loadMore = async () => {
  currentPage.value++
  const data = await photoStore.fetchRandomPhotos(currentPage.value, 12, 70)
  hasMore.value = !data.last
}

onMounted(async () => {
  await photoStore.fetchRandomPhotos(0, 12, 70)
})

onActivated(() => {
  nextTick(() => {
    window.scrollTo({ top: savedScrollTop.value, behavior: 'instant' as ScrollBehavior })
  })
})

onDeactivated(() => {
  savedScrollTop.value = window.scrollY || 0
})
</script>

