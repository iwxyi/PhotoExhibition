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
          </div>
        </div>
      </div>
    </nav>

    <main class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-12">
      <div class="masonry-grid">
        <div
          v-for="(photo, idx) in photos"
          :key="photo.id"
          class="masonry-item photo-card cursor-pointer"
          @click="openViewer(idx)"
        >
          <img
            :src="getImageUrl(photo)"
            :alt="photo.filename"
            class="photo-image w-full"
            loading="lazy"
            @load="onImageLoad"
          />
          <div class="gradient-overlay">
            <div class="absolute bottom-0 left-0 right-0 p-4 text-white">
              <p class="text-sm font-light">{{ photo.filename }}</p>
              <p v-if="photo.cameraModel" class="text-xs opacity-75 mt-1">{{ photo.cameraModel }}</p>
            </div>
          </div>
        </div>
      </div>

      <div v-if="loading" class="text-center mt-12">
        <div class="animate-spin rounded-full h-12 w-12 border-t-2 border-b-2 border-gray-900 dark:border-white mx-auto"></div>
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
defineOptions({ name: 'Wall' })
import { ref, computed, onMounted, onUnmounted, onActivated, onDeactivated, nextTick } from 'vue'
import { usePhotoStore } from '@/stores/photo'
import { useThemeStore } from '@/stores/theme'
import NavLinks from '@/components/NavLinks.vue'
import PhotoViewer from '@/components/PhotoViewer.vue'

const photoStore = usePhotoStore()
const themeStore = useThemeStore()

const photos = computed(() => photoStore.photos)
const loading = computed(() => photoStore.loading)
const currentPage = ref(0)
const hasMore = ref(true)
const viewerVisible = ref(false)
const viewerIndex = ref(0)
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

const openViewer = (idx: number) => {
  viewerIndex.value = idx
  viewerVisible.value = true
}

const onImageLoad = () => {
  // 图片加载完成后的处理
}

const loadMore = async () => {
  if (loading.value || !hasMore.value) return
  currentPage.value++
  const data = await photoStore.fetchPhotoWall(currentPage.value)
  hasMore.value = !data.last
}

const handleScroll = () => {
  if (window.innerHeight + window.scrollY >= document.documentElement.scrollHeight - 1000) {
    loadMore()
  }
}

onMounted(async () => {
  await photoStore.fetchPhotoWall(0)
  window.addEventListener('scroll', handleScroll)
})

onUnmounted(() => {
  window.removeEventListener('scroll', handleScroll)
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

<style scoped>
.masonry-grid {
  column-count: 1;
  column-gap: 1.5rem;
}

@media (min-width: 640px) {
  .masonry-grid {
    column-count: 2;
  }
}

@media (min-width: 1024px) {
  .masonry-grid {
    column-count: 3;
  }
}

@media (min-width: 1280px) {
  .masonry-grid {
    column-count: 4;
  }
}

.masonry-item {
  break-inside: avoid;
  margin-bottom: 1.5rem;
}
</style>

