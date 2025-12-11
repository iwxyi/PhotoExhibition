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

      <div v-if="loading && photos.length === 0" class="flex justify-center items-center h-96">
        <div class="animate-spin rounded-full h-12 w-12 border-t-2 border-b-2 border-gray-900 dark:border-white"></div>
      </div>

      <div v-if="!loading && photos.length === 0" class="text-center mt-12 text-gray-500 dark:text-gray-400">
        <p>暂无图片</p>
      </div>

      <div v-if="photos.length > 0" :class="gridClass">
        <div
          v-for="(photo, idx) in photos"
          :key="`photo-${photo.id}-${idx}`"
          class="photo-card cursor-pointer group"
          @click="openViewer(idx)"
        >
          <div class="aspect-square overflow-hidden rounded-lg">
            <img
              :src="getImageUrl(photo)"
              :alt="photo.filename"
              class="photo-image w-full h-full"
              :style="getImageStyle(photo)"
              loading="lazy"
              @error="onImageError"
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

      <div v-if="loading && photos.length > 0" class="text-center mt-12">
        <div class="animate-spin rounded-full h-12 w-12 border-t-2 border-b-2 border-gray-900 dark:border-white mx-auto"></div>
      </div>
      <div v-if="!hasMore && photos.length > 0" class="text-center mt-12 text-gray-500 dark:text-gray-400">
        <p>已加载全部图片</p>
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
import { ref, computed, onMounted, onUnmounted, onActivated, onDeactivated, nextTick } from 'vue'
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
const isLoadingMore = ref(false)

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

const onImageError = (e: Event) => {
  // 图片加载失败时的处理
  const img = e.target as HTMLImageElement
  img.style.display = 'none'
}

// 获取图片样式（智能聚焦主体）
const getImageStyle = (photo: any) => {
  // 如果有焦点位置信息，使用智能裁剪
  if (photo.focusX !== undefined && photo.focusY !== undefined) {
    return {
      objectPosition: `${photo.focusX}% ${photo.focusY}%`,
      objectFit: 'cover'
    }
  }
  // 默认居中
  return {
    objectPosition: 'center center',
    objectFit: 'cover'
  }
}

const loadMore = async () => {
  // 防止重复加载
  if (loading.value || isLoadingMore.value || !hasMore.value) return
  
  // 保存当前滚动位置
  const scrollTop = window.scrollY || document.documentElement.scrollTop
  
  try {
    isLoadingMore.value = true
    currentPage.value++
    const data = await photoStore.fetchRandomPhotos(currentPage.value, 12, 70)
    
    if (!data || !data.content || data.content.length === 0) {
      hasMore.value = false
      return
    }
    
    hasMore.value = !data.last
    
    // 恢复滚动位置
    nextTick(() => {
      window.scrollTo({ top: scrollTop, behavior: 'instant' })
    })
  } catch (error) {
    console.error('加载更多失败:', error)
    // 加载失败时回退页码
    currentPage.value--
    hasMore.value = false
    // 恢复滚动位置
    nextTick(() => {
      window.scrollTo({ top: scrollTop, behavior: 'instant' })
    })
  } finally {
    isLoadingMore.value = false
  }
}

// 防抖滚动处理
let scrollTimer: ReturnType<typeof setTimeout> | null = null
const handleScroll = () => {
  if (scrollTimer) clearTimeout(scrollTimer)
  scrollTimer = setTimeout(() => {
    const scrollTop = window.scrollY || document.documentElement.scrollTop
    const windowHeight = window.innerHeight
    const documentHeight = document.documentElement.scrollHeight
    
    // 距离底部 800px 时开始加载
    if (scrollTop + windowHeight >= documentHeight - 800) {
      loadMore()
    }
  }, 100)
}

onMounted(async () => {
  try {
    currentPage.value = 0
    hasMore.value = true
    const data = await photoStore.fetchRandomPhotos(0, 12, 70)
    hasMore.value = !data.last
    window.addEventListener('scroll', handleScroll, { passive: true })
  } catch (error) {
    console.error('初始化加载失败:', error)
    hasMore.value = false
  }
})

onUnmounted(() => {
  window.removeEventListener('scroll', handleScroll)
  if (scrollTimer) clearTimeout(scrollTimer)
})

onActivated(() => {
  nextTick(() => {
    window.scrollTo({ top: savedScrollTop.value, behavior: 'instant' as ScrollBehavior })
    window.addEventListener('scroll', handleScroll, { passive: true })
  })
})

onDeactivated(() => {
  savedScrollTop.value = window.scrollY || 0
  window.removeEventListener('scroll', handleScroll)
})
</script>

