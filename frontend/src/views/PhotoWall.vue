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
      <div ref="masonryContainer" class="masonry-container">
        <div
          v-for="(photo, idx) in photos"
          :key="`photo-${photo.id}-${idx}`"
          :ref="el => setItemRef(el, idx)"
          class="masonry-item photo-card cursor-pointer"
          :style="getItemStyle(idx)"
          @click="openViewer(idx)"
        >
          <img
            :src="getImageUrl(photo)"
            :alt="photo.filename"
            class="masonry-photo-image"
            loading="lazy"
            @load="onImageLoad(idx)"
            @error="onImageError"
          />
          <div class="gradient-overlay">
            <div class="absolute bottom-0 left-0 right-0 p-4 text-white">
              <p class="text-sm font-light">{{ photo.filename }}</p>
              <p v-if="photo.cameraModel" class="text-xs opacity-75 mt-1">{{ photo.cameraModel }}</p>
            </div>
          </div>
        </div>
      </div>

      <div v-if="loading && photos.length > 0" class="text-center mt-12">
        <div class="animate-spin rounded-full h-12 w-12 border-t-2 border-b-2 border-gray-900 dark:border-white mx-auto"></div>
      </div>
      <div v-if="!loading && photos.length === 0" class="text-center mt-12 text-gray-500 dark:text-gray-400">
        <p>暂无图片</p>
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
defineOptions({ name: 'Wall' })
import { ref, computed, onMounted, onUnmounted, onActivated, onDeactivated, nextTick, watch } from 'vue'
import { usePhotoStore } from '@/stores/photo'
import { useThemeStore } from '@/stores/theme'
import NavLinks from '@/components/NavLinks.vue'
import PhotoViewer from '@/components/PhotoViewer.vue'
import SettingsMenu from '@/components/SettingsMenu.vue'
import { useUiSettings } from '@/composables/useUiSettings'

const photoStore = usePhotoStore()
const themeStore = useThemeStore()

const photos = computed(() => photoStore.photos)
const loading = computed(() => photoStore.loading)
const currentPage = ref(0)
const hasMore = ref(true)
const viewerVisible = ref(false)
const viewerIndex = ref(0)
const savedScrollTop = ref(0)
const masonryContainer = ref<HTMLElement | null>(null)
const isLoadingMore = ref(false)
const { previewSize } = useUiSettings()
const windowWidth = ref(typeof window !== 'undefined' ? window.innerWidth : 1920)
const itemRefs = ref<(HTMLElement | null)[]>([])
const columnHeights = ref<number[]>([])
const itemPositions = ref<Array<{ left: number; top: number }>>([])

// 根据预览尺寸计算列数
const columnCount = computed(() => {
  const width = windowWidth.value
  let count = 4 // 默认值
  if (previewSize.value === 'sm') {
    if (width < 640) count = 2
    else if (width < 1024) count = 3
    else if (width < 1280) count = 4
    else count = 5
  } else if (previewSize.value === 'lg') {
    if (width < 640) count = 1
    else if (width < 1024) count = 2
    else count = 3
  } else {
    // md
    if (width < 640) count = 1
    else if (width < 1024) count = 2
    else if (width < 1280) count = 3
    else count = 4
  }
  console.log('计算列数: width=', width, 'previewSize=', previewSize.value, 'count=', count)
  return count
})

// 计算每列的宽度
const columnWidth = computed(() => {
  if (!masonryContainer.value) return 0
  const containerWidth = masonryContainer.value.clientWidth
  const gap = 20 // 1.25rem = 20px
  const cols = columnCount.value
  return (containerWidth - (cols - 1) * gap) / cols
})

// 设置 item ref
const setItemRef = (el: any, idx: number) => {
  if (el) {
    itemRefs.value[idx] = el
  }
}

// 获取 item 样式
const getItemStyle = (idx: number) => {
  const pos = itemPositions.value[idx]
  if (!pos) return { visibility: 'hidden' }
  return {
    position: 'absolute',
    left: `${pos.left}px`,
    top: `${pos.top}px`,
    width: `${columnWidth.value}px`,
    visibility: 'visible'
  }
}

// 监听窗口大小变化
let resizeTimer: ReturnType<typeof setTimeout> | null = null
const handleResize = () => {
  if (resizeTimer) clearTimeout(resizeTimer)
  resizeTimer = setTimeout(() => {
    const newWidth = window.innerWidth
    windowWidth.value = newWidth
    console.log('窗口大小变化:', newWidth, '新列数:', columnCount.value)
    nextTick(() => {
      layoutItems()
    })
  }, 150)
}

// 监听 photos 变化
watch(() => photos.value.length, () => {
  nextTick(() => {
    layoutItems()
  })
})

// 监听列数变化
watch(columnCount, () => {
  nextTick(() => {
    layoutItems()
  })
})

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

const onImageLoad = (idx: number) => {
  // 图片加载完成后重新布局
  nextTick(() => {
    layoutItems()
  })
}

// 瀑布流布局函数
const layoutItems = () => {
  if (!masonryContainer.value || photos.value.length === 0) return
  
  const cols = columnCount.value
  const gap = 20
  const containerWidth = masonryContainer.value.clientWidth
  const itemWidth = (containerWidth - (cols - 1) * gap) / cols
  
  // 初始化列高度
  columnHeights.value = new Array(cols).fill(0)
  itemPositions.value = []
  
  // 计算每个 item 的位置
  itemRefs.value.forEach((item, idx) => {
    if (!item) return
    
    // 找到最短的列
    let minHeight = columnHeights.value[0]
    let minCol = 0
    for (let i = 1; i < cols; i++) {
      if (columnHeights.value[i] < minHeight) {
        minHeight = columnHeights.value[i]
        minCol = i
      }
    }
    
    // 计算位置
    const left = minCol * (itemWidth + gap)
    const top = minHeight
    
    itemPositions.value[idx] = { left, top }
    
    // 更新列高度
    const itemHeight = item.offsetHeight || 200 // 默认高度
    columnHeights.value[minCol] = top + itemHeight + 20 // 20px margin-bottom
  })
  
  // 设置容器高度
  const maxHeight = Math.max(...columnHeights.value)
  masonryContainer.value.style.height = `${maxHeight}px`
}

const onImageError = (e: Event) => {
  // 图片加载失败时的处理
  const img = e.target as HTMLImageElement
  img.style.display = 'none'
}

const loadMore = async () => {
  // 防止重复加载
  if (loading.value || isLoadingMore.value || !hasMore.value) return
  
  // 保存当前滚动位置
  const scrollTop = window.scrollY || document.documentElement.scrollTop
  
  try {
    isLoadingMore.value = true
    currentPage.value++
    const data = await photoStore.fetchPhotoWall(currentPage.value)
    
    if (!data || !data.content || data.content.length === 0) {
      hasMore.value = false
      return
    }
    
    hasMore.value = !data.last
    
    // 等待新图片加载后重新布局并恢复滚动位置
    nextTick(() => {
      setTimeout(() => {
        layoutItems()
        window.scrollTo({ top: scrollTop, behavior: 'instant' })
      }, 200)
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
    // 初始化窗口宽度
    windowWidth.value = window.innerWidth
    await photoStore.fetchPhotoWall(0)
    window.addEventListener('scroll', handleScroll, { passive: true })
    window.addEventListener('resize', handleResize)
    // 等待图片加载后布局
    nextTick(() => {
      setTimeout(() => {
        layoutItems()
      }, 100)
    })
  } catch (error) {
    console.error('初始化加载失败:', error)
  }
})

onUnmounted(() => {
  window.removeEventListener('scroll', handleScroll)
  window.removeEventListener('resize', handleResize)
  if (scrollTimer) clearTimeout(scrollTimer)
  if (resizeTimer) clearTimeout(resizeTimer)
})

onActivated(() => {
  nextTick(() => {
    window.scrollTo({ top: savedScrollTop.value, behavior: 'instant' as ScrollBehavior })
    window.addEventListener('scroll', handleScroll, { passive: true })
    window.addEventListener('resize', handleResize)
  })
})

onDeactivated(() => {
  savedScrollTop.value = window.scrollY || 0
  window.removeEventListener('scroll', handleScroll)
  window.removeEventListener('resize', handleResize)
})
</script>

<style scoped>
.masonry-container {
  position: relative;
  width: 100%;
}

.masonry-item {
  margin-bottom: 1.25rem;
  transition: transform 0.3s;
}

.masonry-photo-image {
  width: 100%;
  height: auto;
  display: block;
  object-fit: cover;
  transition: transform 0.5s;
}

.masonry-photo-image:hover {
  transform: scale(1.05);
}
</style>

