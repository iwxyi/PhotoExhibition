<template>
  <div class="min-h-screen transition-colors duration-1000" :style="backgroundStyle">
    <nav class="fixed top-4 left-4 z-50">
      <!-- scroll-aware back button with glassmorphism -->
      <div
        ref="backButtonRef"
        class="back-button-container"
        :class="{ 'is-collapsed': isBackButtonCollapsed }"
        @click="handleBack"
        @mousemove="onBackButtonMouseMove"
        @mouseleave="onBackButtonMouseLeave"
        @mousedown="onBackButtonMouseDown"
        aria-label="返回"
        title="返回"
      >
        <div class="back-button-glass">
          <!-- icon (always visible) -->
          <svg class="back-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
            <path d="M19 12H5M12 19l-7-7 7-7"/>
          </svg>
          <!-- text (visible when expanded) -->
          <span class="back-text">BACK</span>
        </div>
      </div>

      <!-- multi-select toolbar (placed after back button) -->
      <div v-if="multiSelectActive" class="inline-flex items-center gap-2 ml-3">
        <button class="btn-primary px-3 py-1 text-sm" @click="selectAll">全选</button>
        <button class="btn-primary px-3 py-1 text-sm ml-1" @click="invertSelection">反选</button>
        <button class="btn-primary px-3 py-1 text-sm ml-1 flex items-center gap-1" @click="downloadSelected" title="下载选中">
          <span>⤓</span>
          <span v-if="selectedIds.size > 0">{{ selectedIds.size }}</span>
        </button>
        <button class="btn-primary px-3 py-1 text-sm ml-1 flex items-center gap-1" @click="downloadZipSelected" title="下载 ZIP（服务器/回退兼容）">
          <span>⤓</span>
          <span>打包</span>
        </button>
      </div>
    </nav>

    <main class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-12">
      <!-- 滚动进度条 -->
      <div class="fixed top-0 left-0 right-0 h-0.5 z-50 bg-transparent">
        <div
          class="h-full bg-gradient-to-r from-blue-500 via-purple-500 to-pink-500 transition-all duration-100 ease-out"
          :style="{ width: scrollProgress + '%', opacity: scrollProgress > 0 ? 1 : 0 }"
        ></div>
      </div>

      <!-- download progress bar -->
      <div v-if="downloadInProgress" class="fixed left-0 right-0 top-0 z-50">
        <div class="h-1 bg-gray-200 dark:bg-gray-800 w-full">
          <div :style="{ width: downloadProgress + '%' }" class="h-1 bg-blue-600 transition-width duration-200"></div>
        </div>
      </div>

      <!-- 初始加载状态 - 固定定位，不影响布局 -->
      <div v-if="isInitialLoading" class="fixed left-1/2 top-[200px] -translate-x-1/2 z-40">
        <div class="animate-spin rounded-full h-10 w-10 border-t-2 border-b-2 border-gray-400 dark:border-gray-500"></div>
      </div>

      <!-- 相册内容 -->
      <div v-if="album" class="album-content-wrapper">
        <!-- 相册信息 - 居中显示 -->
        <div class="album-header-center">
          <h1 class="album-title" :style="textStyle">{{ album.name }}</h1>
          <p v-if="album.description" class="album-description">{{ album.description }}</p>
          <!-- 分割线：位于备注和照片数量之间 -->
          <div class="album-header-divider"></div>
          <p class="album-meta" :style="{ ...textStyle, opacity: 0.8 }">
            <svg class="w-4 h-4 album-meta-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <rect x="3" y="3" width="18" height="18" rx="2" ry="2"/>
              <circle cx="8.5" cy="8.5" r="1.5"/>
              <polyline points="21,15 16,10 5,21"/>
            </svg>
            {{ album.photoCount }} 张照片
            <span v-if="commentCount > 0" class="text-gray-400">·</span>
            <span v-if="commentCount > 0">{{ commentCount }} 条评论</span>
          </p>
        </div>

        <!-- 人物列表 - 横向可滚动 -->
        <div
          v-if="albumPersons.length > 0"
          class="album-persons-section"
          :class="{ 'album-persons-section--dark': atmosphereEnabled && album?.backgroundColor }"
        >
          <div class="album-persons-scroll">
            <div
              v-for="person in albumPersons"
              :key="person.id"
              class="album-person-card"
              @click="router.push({ path: `/p/${person.id}`, query: { from: route.fullPath } })"
            >
              <div class="person-avatar-wrapper">
                <img
                  v-if="person.sampleThumbnailPath"
                  :src="getImageUrl({ thumbnailPath: person.sampleThumbnailPath })"
                  :alt="person.name"
                  class="person-avatar"
                />
                <div v-else class="person-avatar-placeholder">
                  <svg class="w-6 h-6" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/>
                    <circle cx="12" cy="7" r="4"/>
                  </svg>
                </div>
              </div>
              <span class="person-name">{{ person.name }}</span>
              <span class="person-count">{{ person.faceCount }} 张</span>
            </div>
          </div>
        </div>

        <MasonryLayout
          :items="masonryItems"
          :column-count="columnCount"
          :gap="8"
          :show-like-button="!multiSelectActive"
          @image-loaded="handleImageLoaded"
        >
          <template #default="{ item: photo, index }">
            <div
              class="photo-card cursor-pointer"
              :style="getPhotoStyle(photo)"
              :data-photo-id="photo.id"
              @pointerdown="onPhotoPointerDown(photo, index, $event)"
              @pointerup="onPhotoPointerUp(photo, index, $event)"
              @click="handlePhotoClick(photo, index, $event)"
              :ref="(el: Element | ComponentPublicInstance | null) => setPhotoRef(el as Element | null, photo.id)"
            >
              <!-- multi-select checkbox (shown only in multiselect mode) -->
              <div v-if="multiSelectActive" class="absolute top-3 left-3 z-40">
                <input
                  type="checkbox"
                  class="select-checkbox"
                  :checked="selectedIds.has(photo.id)"
                  @click.stop.prevent="toggleSelect(photo.id, index)"
                  aria-label="选择图片"
                />
              </div>
              <img
                :src="getImageUrl(photo)"
                :alt="photo.filename"
                class="photo-image w-full h-full"
                loading="lazy"
                @load="$emit('image-loaded')"
                @error="$emit('image-loaded')"
              />
              <!-- magnifier (shown in multiselect mode) -->
              <button
                v-if="multiSelectActive"
                class="absolute bottom-3 right-3 z-40 btn-magnify"
                @pointerdown.stop
                @pointerup.stop
                @click.stop="openViewer(index, $event)"
                title="查看原图"
              >
                ⤢
              </button>
              <div class="gradient-overlay">
                <div class="photo-info absolute bottom-0 left-0 right-0 p-4 text-white">
                  <p class="text-sm font-light truncate">{{ photo.filename }}</p>
                  <p v-if="photo.width && photo.height" class="photo-dimensions text-xs opacity-70 mt-0.5">
                    {{ photo.width }} × {{ photo.height }}
                  </p>
                </div>
              </div>
            </div>
          </template>
        </MasonryLayout>

        <!-- 加载更多状态 -->
        <div v-if="loadingMore" class="mt-8 text-center">
          <div class="animate-spin rounded-full h-8 w-8 border-t-2 border-b-2 border-gray-900 dark:border-white mx-auto"></div>
          <p class="mt-2 text-sm text-gray-600 dark:text-gray-400">正在加载更多照片...</p>
        </div>

        <!-- 已加载全部提示 -->
        <div v-if="!hasMore && !loadingMore && photos.length > pageSize" class="mt-8 text-center">
          <p class="text-sm text-gray-500 dark:text-gray-400">已加载全部照片</p>
        </div>
      </div>

      <!-- 评论区域 - 只有当图片加载完成并延迟一段时间后才显示，避免闪烁 -->
      <CommentSection
        v-show="showComments"
        :visible="showComments"
        :album-id="album?.id || 0"
        :text-color="textStyle.color"
        :background-color="commentBackgroundColor"
        :border-color="commentBorderColor"
        :input-border-color="inputBorderColor"
        :is-dark-mode="themeStore.isDark"
        :is-atmosphere-enabled="atmosphereEnabled"
      />

      <!-- 回到顶部按钮 -->
      <Transition name="fade">
        <button
          v-if="scrollProgress > 20"
          @click="scrollToTop"
          class="fixed bottom-8 right-8 z-40 w-12 h-12 rounded-full bg-white/80 dark:bg-gray-800/80 backdrop-blur-md shadow-lg hover:shadow-xl hover:scale-105 transition-all duration-300 flex items-center justify-center text-gray-700 dark:text-gray-300 border border-gray-200/50 dark:border-gray-700/50"
          title="回到顶部"
        >
          <svg class="w-5 h-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
            <path d="M18 15l-6-6-6 6"/>
          </svg>
        </button>
      </Transition>
    </main>
    <PhotoViewer
      v-model:visible="viewerVisible"
      :photos="photos"
      :start-index="viewerIndex"
      :origin-rect="viewerOriginRect"
      :auto-show-faces="false"
    />

    <!-- 氛围特效 -->
    <AtmosphereEffects :effects="albumAtmosphereEffects" />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, onActivated, ref, nextTick, watch, type ComponentPublicInstance } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { usePhotoStore } from '@/stores/photo'
import { useUiSettings } from '@/composables/useUiSettings'
import { useThemeStore } from '@/stores/theme'
import PhotoViewer from '@/components/PhotoViewer.vue'
import AtmosphereEffects from '@/components/AtmosphereEffects.vue'
import MasonryLayout from '@/components/MasonryLayout.vue'
import CommentSection from '@/components/CommentSection.vue'
import { commentApi, api, personApi, albumApi } from '@/api'

const route = useRoute()
const router = useRouter()
const photoStore = usePhotoStore()

const album = computed(() => photoStore.currentAlbum)
const photos = computed(() => photoStore.photos)

// 从路由参数获取相册 ID（支持 /album/:id 和 /a/:id 两种路由）
const albumId = computed(() => {
  const id = route.params.id || route.params.keyword
  if (!id) return null
  const parsed = parseInt(id as string)
  return isNaN(parsed) ? null : parsed
})

// 用于存储按名称搜索找到的相册 ID（当路由参数是名称时）
const resolvedAlbumId = ref<number | null>(null)

// 最终使用的相册 ID（优先使用路由 ID，否则使用搜索结果）
const finalAlbumId = computed(() => {
  return albumId.value || resolvedAlbumId.value
})

// 解析相册 ID（支持 ID 或名称）
const resolveAlbumId = async (): Promise<number | null> => {
  // 如果是数字 ID，直接返回
  if (albumId.value) {
    return albumId.value
  }

  // 否则尝试按名称搜索
  const keyword = route.params.id || route.params.keyword
  if (keyword) {
    try {
      const searchResponse = await albumApi.searchByName(decodeURIComponent(keyword as string))
      if (searchResponse.data && searchResponse.data.id) {
        resolvedAlbumId.value = searchResponse.data.id
        return searchResponse.data.id
      }
    } catch (error) {
      console.error('按名称搜索相册失败:', error)
    }
  }
  return null
}

// 全局下载权限设置
const globalDownloadAllowed = ref(false)

// 检查是否允许下载（用于控制长按多选功能）
const isDownloadAllowed = computed(() => {
  const albumData = album.value
  if (!albumData) return false

  // 如果相册有明确的设置，使用相册设置
  if (albumData.downloadAllowed !== null && albumData.downloadAllowed !== undefined) {
    return albumData.downloadAllowed
  }

  // 否则使用全局设置
  return globalDownloadAllowed.value
})

// 评论数量
const commentCount = ref(0)

// 相册中的人物列表
interface AlbumPerson {
  id: number
  name: string
  description?: string
  sampleThumbnailPath?: string
  faceCount?: number
}
const albumPersons = ref<AlbumPerson[]>([])
const albumPersonsLoading = ref(false)

// 图片加载状态
const imagesLoaded = ref(false)
const totalImages = ref(0)
const loadedImagesCount = ref(0)

// 评论显示延迟：图片加载完成后再延迟一段时间显示评论区
const showComments = ref(false)
let showCommentsTimer: ReturnType<typeof setTimeout> | null = null

// 分页加载状态
const currentPage = ref(0)
const loadingMore = ref(false)
const hasMore = ref(true)
const pageSize = 30 // 每次加载30张照片
const isInitialLoading = ref(true) // 初始加载状态，用于避免显示旧数据

// 关键：进入详情页的 setup 阶段就同步清空上一相册数据，避免首帧闪现旧标题/备注
// （相册详情页不在 KeepAlive include 列表中，因此不能依赖 onDeactivated）
photoStore.currentAlbum = null
photoStore.photos = []
commentCount.value = 0
albumPersons.value = []
albumPersonsLoading.value = false
imagesLoaded.value = false
loadedImagesCount.value = 0
totalImages.value = 0

const { atmosphereEnabled, previewSize } = useUiSettings()

// 获取主题store
const themeStore = useThemeStore()

// 窗口宽度响应式（用于触发columnCount重新计算）
const windowWidth = ref(typeof window !== 'undefined' ? window.innerWidth : 1920)

// 监听窗口大小变化（实时响应）
const handleResize = () => {
  windowWidth.value = window.innerWidth
}

onUnmounted(() => {
  if (showCommentsTimer) {
    clearTimeout(showCommentsTimer)
    showCommentsTimer = null
  }
})

// 加载更多照片的分页函数
const loadMorePhotos = async () => {
  if (loadingMore.value || !hasMore.value) return

  try {
    loadingMore.value = true
    currentPage.value++

    const result = await photoStore.fetchPhotosByAlbum(albumId.value, currentPage.value, pageSize)
    hasMore.value = !result.last

    // 更新总数，并重置已加载计数（因为新增的图片需要重新加载）
    const previousCount = totalImages.value
    totalImages.value = photos.value.length

    // 如果有新图片加载，重置加载计数
    if (totalImages.value > previousCount) {
      loadedImagesCount.value = 0
      imagesLoaded.value = false
    }
  } catch (error) {
    console.error('加载更多照片失败:', error)
  } finally {
    loadingMore.value = false
  }
}

// 监听图片加载完成状态，控制评论区的延迟显示
watch(imagesLoaded, (loaded) => {
  if (showCommentsTimer) {
    clearTimeout(showCommentsTimer)
    showCommentsTimer = null
  }

  if (loaded) {
    // 图片全部加载后，延迟 2 秒再显示评论，避免图片和评论同时抖动
    showCommentsTimer = setTimeout(() => {
      showComments.value = true
    }, 2000)
  } else {
    // 切换相册或重新加载时，立即隐藏评论区
    showComments.value = false
  }
})

// 滚动监听器，用于自动加载更多照片
let scrollThrottleTimer: ReturnType<typeof setTimeout> | null = null
const SCROLL_THROTTLE_MS = 100 // 100ms节流
const LOAD_THRESHOLD = 1000 // 距离底部1000px时开始加载

// 返回按钮折叠状态
const isBackButtonCollapsed = ref(false)
const scrollProgress = ref(0)
let lastScrollTop = 0
const SCROLL_HYSTERESIS = 20 // 滚动滞后，避免频繁切换

const handleScroll = () => {
  if (scrollThrottleTimer) return

  scrollThrottleTimer = setTimeout(() => {
    // 加载更多照片逻辑
    const scrollTop = window.scrollY || document.documentElement.scrollTop
    const windowHeight = window.innerHeight
    const documentHeight = document.documentElement.scrollHeight

    // 计算滚动进度（0-100）
    const maxScroll = Math.max(0, documentHeight - windowHeight)
    scrollProgress.value = maxScroll > 0 ? Math.round((scrollTop / maxScroll) * 100) : 0

    // 返回按钮折叠逻辑：向上滚动时展开，向下滚动时折叠
    const scrollDelta = scrollTop - lastScrollTop
    if (Math.abs(scrollDelta) > SCROLL_HYSTERESIS) {
      // 向下滚动超过阈值，折叠按钮
      if (scrollDelta > 5) {
        isBackButtonCollapsed.value = true
      }
      // 向上滚动超过阈值，展开按钮
      else if (scrollDelta < -5) {
        isBackButtonCollapsed.value = false
      }
      lastScrollTop = scrollTop
    }

    // 距离底部LOAD_THRESHOLD像素时开始加载
    if (scrollTop + windowHeight >= documentHeight - LOAD_THRESHOLD) {
      loadMorePhotos()
    }

    scrollThrottleTimer = null
  }, SCROLL_THROTTLE_MS)
}

// 回到顶部
const scrollToTop = () => {
  window.scrollTo({
    top: 0,
    behavior: 'smooth'
  })
}

// 背景样式（基于相册的背景颜色或默认主题，支持氛围开关）
const backgroundStyle = computed(() => {
  if (atmosphereEnabled.value && album.value?.backgroundColor) {
    // 启用氛围时，总是使用相册的背景颜色作为基础氛围
    // 背景层特效会叠加在背景色之上
    const baseColor = album.value.backgroundColor!
    return {
      backgroundColor: baseColor
    }
  } else if (!atmosphereEnabled.value) {
    // 关闭氛围时使用纯色背景，与主页一致
    return {
      backgroundColor: themeStore.isDark ? '#000000' : '#ffffff'
    }
  }
  return {}
})


// 文字样式（确保在任何背景下都有足够对比度）
const textStyle = computed(() => {
  if (atmosphereEnabled.value && album.value?.backgroundColor) {
    // 启用氛围时，根据相册背景色选择文字颜色
    const bgBrightness = getBrightness(album.value.backgroundColor!)
    const isLightBackground = bgBrightness > 0.5

    if (isLightBackground) {
      // 浅色背景 -> 使用深色文字
      return { color: '#1a1a1a' }
    } else {
      // 深色背景 -> 使用浅色文字
      return { color: '#ffffff' }
    }
  } else if (!atmosphereEnabled.value) {
    // 关闭氛围时使用默认的主题文字颜色
    return {
      color: themeStore.isDark ? '#ffffff' : '#1a1a1a'
    }
  }
  return {}
})

// 氛围特效列表
const albumAtmosphereEffects = computed(() => {
  if (!atmosphereEnabled.value) {
    return []
  }
  return album.value?.atmosphereEffects || [] as any[]
})

// 评论区域背景和边框颜色
const commentBackgroundColor = computed(() => {
  if (atmosphereEnabled.value && album.value?.backgroundColor) {
    // 启用氛围时，使用半透明的相册背景色
    const bgColor = album.value.backgroundColor!
    // 将十六进制颜色转换为rgba，添加透明度
    if (bgColor.startsWith('#')) {
      const r = parseInt(bgColor.slice(1, 3), 16)
      const g = parseInt(bgColor.slice(3, 5), 16)
      const b = parseInt(bgColor.slice(5, 7), 16)
      return `rgba(${r}, ${g}, ${b}, 0.85)`
    }
    return 'rgba(255, 255, 255, 0.85)'
  } else {
    // 关闭氛围时使用半透明的主题背景色
    return themeStore.isDark ? 'rgba(31, 41, 55, 0.85)' : 'rgba(255, 255, 255, 0.85)'
  }
})

const commentBorderColor = computed(() => {
  if (atmosphereEnabled.value && album.value?.backgroundColor) {
    // 启用氛围时，使用更透明的边框
    const bgColor = album.value.backgroundColor!
    if (bgColor.startsWith('#')) {
      const r = parseInt(bgColor.slice(1, 3), 16)
      const g = parseInt(bgColor.slice(3, 5), 16)
      const b = parseInt(bgColor.slice(5, 7), 16)
      return `rgba(${r}, ${g}, ${b}, 0.3)`
    }
    return 'rgba(229, 231, 235, 0.3)'
  } else {
    // 关闭氛围时使用半透明的主题边框色
    return themeStore.isDark ? 'rgba(75, 85, 99, 0.3)' : 'rgba(229, 231, 235, 0.3)'
  }
})

// 输入框边框颜色（根据背景模式）
const inputBorderColor = computed(() => {
  // 如果开启氛围模式或处于夜间模式，使用浅白色边框
  if (atmosphereEnabled.value || themeStore.isDark) {
    return 'rgb(255 255 255 / 0.3)' // 浅白色
  }
  // 日间模式使用深灰色边框
  return 'rgb(107 114 128 / 0.5)' // 深灰色
})

// 计算列数（响应式，与其他页面保持一致）
const columnCount = computed(() => {
  if (typeof window === 'undefined') return 3

  const width = windowWidth.value
  let count = 3 // 默认值

  if (previewSize.value === 'sm') {
    // 小: 最多列数（适合小图片）
    if (width < 640) count = 3
    else if (width < 1024) count = 4
    else if (width < 1280) count = 5
    else count = 6
  } else if (previewSize.value === 'md') {
    // 中: 中等列数
    if (width < 640) count = 2
    else if (width < 1024) count = 3
    else if (width < 1280) count = 4
    else count = 4
  } else if (previewSize.value === 'lg') {
    // 大: 最少列数（适合大图片）
    if (width < 640) count = 1
    else if (width < 1024) count = 2
    else if (width < 1280) count = 3
    else count = 3
  } else {
    // 默认 md
    if (width < 640) count = 2
    else if (width < 1024) count = 3
    else if (width < 1280) count = 5
    else count = 5
  }
  return count
})

// 转换照片数据为瀑布流组件需要的格式
const masonryItems = computed(() => {
  const items = photos.value.map(photo => ({
    id: photo.id,
    data: photo,
    width: photo.width || 1,
    height: photo.height || 1
  }))
  return items
})

const viewerVisible = ref(false)
const viewerIndex = ref(0)
const viewerOriginRect = ref<{ top: number; left: number; width: number; height: number } | null>(null)

const photoRefs = ref<Map<number, HTMLElement>>(new Map())
const isTransitioning = ref(false)
const transitionPhotoIds = ref<number[]>([])
const remainingPhotosVisible = ref(false)
const remainingPhotoIndexes = ref<Map<number, number>>(new Map())
let transitionClones: HTMLElement[] = []
// multi-select state
const multiSelectActive = ref(false)
const selectedIds = ref<Set<number>>(new Set())
const lastSelectedIndex = ref<number | null>(null)

// long press / sliding state
let longPressTimer: ReturnType<typeof setTimeout> | null = null
let sliding = false
let slideInitialPressedWasSelected = false
let slideStartPhotoId: number | null = null // 滑动开始的图片ID
let slideStartX = 0 // 滑动开始的X坐标
let slideStartY = 0 // 滑动开始的Y坐标
let hasDraggedDuringPress = false // track if user dragged during pointer down
// whether the last interaction was a long-press (suppress click)
const longPressActivated = ref(false)

const toggleSelect = (photoId: number, idx?: number) => {
  // Work with a new Set to ensure Vue reactivity picks up changes
  const prev = new Set(selectedIds.value)
  if (prev.has(photoId)) prev.delete(photoId)
  else prev.add(photoId)
  selectedIds.value = prev
  // update lastSelectedIndex for range selection
  if (typeof idx === 'number') lastSelectedIndex.value = idx
}

const clearMultiSelect = () => {
  multiSelectActive.value = false
  selectedIds.value = new Set()
  lastSelectedIndex.value = null
}

/* startLongPressFor removed (logic in onPhotoPointerDown) */

const endPress = () => {
  if (longPressTimer) {
    clearTimeout(longPressTimer)
    longPressTimer = null
  }
  sliding = false
  slideStartPhotoId = null
  slideStartX = 0
  slideStartY = 0
}

// helper to get photoId under point
const photoIdAtPoint = (x: number, y: number) => {
  const el = document.elementFromPoint(x, y) as HTMLElement | null
  if (!el) return null
  const card = el.closest('.photo-card') as HTMLElement | null
  if (!card) return null
  const idAttr = card.getAttribute('data-photo-id')
  return idAttr ? Number(idAttr) : null
}

// helper to get photo element rect
const getPhotoRect = (photoId: number) => {
  const photoElement = photoRefs.value.get(photoId)
  return photoElement ? photoElement.getBoundingClientRect() : null
}

// helper to get all photos within a rectangle (box selection)
const getPhotosInRect = (rect: DOMRect) => {
  const photosInRect: number[] = []
  photoRefs.value.forEach((element, photoId) => {
    const photoRect = element.getBoundingClientRect()
    // Check if photo rect overlaps with selection rect
    if (photoRect.left < rect.right &&
        photoRect.right > rect.left &&
        photoRect.top < rect.bottom &&
        photoRect.bottom > rect.top) {
      photosInRect.push(photoId)
    }
  })
  return photosInRect
}

// handle auto-scroll when dragging near screen edges
const handleAutoScroll = (mouseX: number, mouseY: number) => {
  const scrollZone = 50 // pixels from edge to start scrolling
  const scrollSpeed = 8 // pixels per frame
  const viewportHeight = window.innerHeight
  const viewportWidth = window.innerWidth

  let scrollX = 0
  let scrollY = 0

  // Check vertical scrolling
  if (mouseY < scrollZone) {
    scrollY = -scrollSpeed // scroll up
  } else if (mouseY > viewportHeight - scrollZone) {
    scrollY = scrollSpeed // scroll down
  }

  // Check horizontal scrolling
  if (mouseX < scrollZone) {
    scrollX = -scrollSpeed // scroll left
  } else if (mouseX > viewportWidth - scrollZone) {
    scrollX = scrollSpeed // scroll right
  }

  if (scrollX !== 0 || scrollY !== 0) {
    window.scrollBy(scrollX, scrollY)
  }
}

// range select between lastSelectedIndex and idx inclusive
const selectRange = (fromIdx: number | null, toIdx: number) => {
  if (fromIdx === null) {
    lastSelectedIndex.value = toIdx
    selectedIds.value.add(masonryItems.value[toIdx].id as number)
    return
  }
  const a = Math.min(fromIdx, toIdx)
  const b = Math.max(fromIdx, toIdx)
  for (let i = a; i <= b; i++) {
    selectedIds.value.add(masonryItems.value[i].id as number)
  }
  lastSelectedIndex.value = toIdx
}

const getImageUrl = (photo: any) => {
  // 优先使用中缩略图（用于瀑布流显示）
  if (photo.mediumThumbPath) {
    return `/api/files${photo.mediumThumbPath}`
  }
  // 回退到webp
  if (photo.webpPath) {
    return `/api/files${photo.webpPath}`
  }
  // 最后回退到小缩略图或原图
  if (photo.thumbnailPath) {
    return `/api/files${photo.thumbnailPath}`
  }
  return `/api/files${photo.originalPath}`
}

const getPhotoStyle = (photo: any) => {
  const photoId = photo.id
  // 如果是封面图片且正在过渡，隐藏它们
  if (isTransitioning.value && transitionPhotoIds.value.includes(photoId)) {
    return {
      visibility: 'hidden' as const,
      transition: 'none'
    }
  }

  // 如果动画还没有开始，为剩余图片添加初始动画样式
  if (!remainingPhotosVisible.value && !transitionPhotoIds.value.includes(photoId)) {
    return {
      opacity: '0',
      transform: 'translateY(30px)',
      transition: 'none'
    }
  }

  // 如果动画已经开始，添加渐进动画
  if (remainingPhotosVisible.value && !transitionPhotoIds.value.includes(photoId)) {
    const index = remainingPhotoIndexes.value.get(photoId) || 0
    // 使用非线性延迟：前面的图片延迟少，后面的图片延迟相对更多，但不是完全线性
    const baseDelay = Math.min(index * 20, 80) // 最大延迟80ms，比之前更短
    const randomFactor = Math.random() * 15 // 添加轻微的随机性使动画更自然
    const delay = Math.max(0, baseDelay + randomFactor - 7)

    return {
      opacity: '1',
      transform: 'translateY(0)',
      transition: 'all 0.5s cubic-bezier(0.22, 1, 0.36, 1)',
      transitionDelay: `${delay}ms`
    }
  }

  // 默认样式
  return {}
}

const setPhotoRef = (el: Element | null, photoId: number) => {
  const domEl = el as HTMLElement | null
  if (domEl) {
    photoRefs.value.set(photoId, domEl)
  } else {
    photoRefs.value.delete(photoId)
  }
}

const openViewer = (idx: number, e: MouseEvent) => {
  viewerIndex.value = idx

  // 以图片本身为主，避免外层卡片比查看器中的图片更大导致"从大缩小"的观感
  const img = (e.target as HTMLElement).closest('img') as HTMLImageElement | null
  const rectSource = img || (e.currentTarget as HTMLElement | null)
  if (rectSource) {
    const rect = rectSource.getBoundingClientRect()
    viewerOriginRect.value = {
      top: rect.top,
      left: rect.left,
      width: rect.width,
      height: rect.height
    }
  } else {
    viewerOriginRect.value = null
  }

  viewerVisible.value = true
}

// Handle photo interactions: click / long-press / sliding selection
const onPhotoPointerDown = (photo: any, idx: number, e: PointerEvent) => {
  // Prevent default browser behavior immediately (text selection, etc.)
  e.preventDefault()

  // reset drag tracking
  hasDraggedDuringPress = false

  // start long-press timer
  if (longPressTimer) clearTimeout(longPressTimer)
  const photoId = photo.id
  const wasSelected = selectedIds.value.has(photoId)
  // adjust long-press duration by input type for better UX
  const ptrType = (e as any).pointerType || 'mouse'
  const useDuration = 300 // 统一使用300ms，避免与浏览器长按菜单冲突
  // remember pointer start for mouse drag detection and long-press movement check
  const startX = (e as any).clientX || 0
  const startY = (e as any).clientY || 0
  // flag to track if long-press has been cancelled due to movement
  let longPressCancelled = false

  longPressTimer = setTimeout(() => {
    // only trigger long press if not cancelled by movement and download is allowed
    if (!longPressCancelled && isDownloadAllowed.value) {
      // long press triggered
      longPressActivated.value = true
      if (!multiSelectActive.value) {
        // activate multi-select and select pressed (use new Set for reactivity)
        multiSelectActive.value = true
        selectedIds.value = new Set([...selectedIds.value, photoId])
        lastSelectedIndex.value = idx
        // sliding initial state
        slideInitialPressedWasSelected = wasSelected
        sliding = true
        slideStartPhotoId = photoId
        slideStartX = startX
        slideStartY = startY
      } else {
        // already in multi-select: treat as range-select between lastSelectedIndex and this idx
        selectRange(lastSelectedIndex.value, idx)
      }
    }
  }, useDuration)

  // start pointer capture for sliding
  const target = e.currentTarget as HTMLElement | null
  try { (target as any)?.setPointerCapture?.((e as any).pointerId) } catch (e) {}
  // listen for pointermove globally
  const onMove = (ev: PointerEvent) => {
    const pType = (ev as any).pointerType || 'mouse'
    const dx = Math.abs((ev as any).clientX - startX)
    const dy = Math.abs((ev as any).clientY - startY)
    const moveThreshold = 6


    // For touch devices, cancel long-press if moved significantly (balance between finger jitter and drag prevention)
    if (pType === 'touch' && !sliding && !longPressCancelled) {
      // Allow small finger jitter but prevent obvious dragging
      const touchMoveThreshold = 8 // Allow ~8px movement for natural finger jitter, but prevent dragging
      if (dx > touchMoveThreshold || dy > touchMoveThreshold) {
        // cancel long-press due to significant movement
        longPressCancelled = true
        hasDraggedDuringPress = true
        if (longPressTimer) {
          clearTimeout(longPressTimer)
          longPressTimer = null
        }
      }
    }

    // For mouse devices, cancel long-press if moved significantly (same logic as touch)
    if (pType === 'mouse' && !sliding && !longPressCancelled) {
      const mouseMoveThreshold = 8 // Same threshold as touch for consistency
      if (dx > mouseMoveThreshold || dy > mouseMoveThreshold) {
        // cancel long-press due to significant movement
        longPressCancelled = true
        hasDraggedDuringPress = true
        if (longPressTimer) {
          clearTimeout(longPressTimer)
          longPressTimer = null
        }
      }
    }

    // mouse drag: only allow sliding selection if multi-select is already active
    if (pType === 'mouse' && !sliding && multiSelectActive.value) {
      if (dx > moveThreshold || dy > moveThreshold) {
        // prevent default browser behavior (text selection, etc.) when starting drag selection
        ev.preventDefault()
        // begin sliding selection for mouse drag (only if multi-select is active)
        slideInitialPressedWasSelected = wasSelected
        sliding = true
        slideStartPhotoId = photoId
        slideStartX = startX
        slideStartY = startY
        // Don't set longPressActivated for mouse drag in active multi-select mode
        // cancel longPressTimer since we've started sliding
        if (longPressTimer) { clearTimeout(longPressTimer); longPressTimer = null }
      }
    }

    if (!sliding) return

    // prevent default browser behavior during sliding selection
    ev.preventDefault()

    // Calculate selection rectangle from start point to current point
    const currentX = ev.clientX
    const currentY = ev.clientY

    const selectionRect = new DOMRect(
      Math.min(slideStartX, currentX),
      Math.min(slideStartY, currentY),
      Math.abs(currentX - slideStartX),
      Math.abs(currentY - slideStartY)
    )

    // Auto-scroll if near screen edges
    handleAutoScroll(currentX, currentY)

    // Get all photos within the selection rectangle
    const photosInRect = getPhotosInRect(selectionRect)

    // Update selection: always select photos in rectangle (box selection behavior)
    const cur = new Set(selectedIds.value)

    // Add all photos in the rectangle to selection
    photosInRect.forEach(photoId => {
      cur.add(photoId)
    })

    selectedIds.value = cur
  }
  window.addEventListener('pointermove', onMove)

  // attach a one-time cleanup when pointerup
  const onUp = () => {
    endPress()
    window.removeEventListener('pointermove', onMove)
    window.removeEventListener('pointerup', onUp)
    try { (target as any)?.releasePointerCapture?.((e as any).pointerId) } catch (e) {}
    // clear long-press activation shortly after pointer up to prevent click
    if (longPressActivated.value) {
      setTimeout(() => { longPressActivated.value = false }, 50)
    }
  }
  window.addEventListener('pointerup', onUp)
}

const onPhotoPointerUp = (_photo: any, _idx: number, _e: PointerEvent) => {
  // If longPressTimer didn't fire, this was a normal press -> do nothing special here
  if (longPressTimer) {
    clearTimeout(longPressTimer)
    longPressTimer = null
  }
  sliding = false
  slideStartPhotoId = null
  slideStartX = 0
  slideStartY = 0
}

const handlePhotoClick = (photo: any, idx: number, e: MouseEvent) => {
  const photoId = photo.id
  if (longPressActivated.value) {
    // suppress click caused by long-press release
    longPressActivated.value = false
    return
  }
  if (hasDraggedDuringPress) {
    // suppress click caused by drag release
    hasDraggedDuringPress = false
    return
  }
  if (multiSelectActive.value) {
    // toggle selection
    toggleSelect(photoId, idx)
    return
  }
  // otherwise open viewer
  openViewer(idx, e)
}

// bulk actions
const selectAll = () => {
  const allIds = masonryItems.value.map(i => i.id as number)
  selectedIds.value = new Set(allIds)
}

const invertSelection = () => {
  const allIds = masonryItems.value.map(i => i.id as number)
  const cur = new Set<number>()
  allIds.forEach(id => {
    if (!selectedIds.value.has(id)) cur.add(id)
  })
  selectedIds.value = cur
}

const downloadSelected = async () => {
  const ids = Array.from(selectedIds.value)
  if (ids.length === 0) return
  downloadInProgress.value = true
  downloadProgress.value = 0
  // sequentially fetch and trigger download to maximize compatibility
  for (let i = 0; i < ids.length; i++) {
    const id = ids[i]
    const photo = photos.value.find((p: any) => p.id === id)
    if (!photo) continue
    const url = getImageUrl(photo)
    try {
      const resp = await fetch(url, { credentials: 'same-origin' })
      if (!resp.ok) throw new Error('fetch failed')
      const blob = await resp.blob()
      const extMatch = (photo.originalPath || photo.webpPath || '').split('.').pop() || 'jpg'
      const filename = `${photo.filename || 'photo'}.${extMatch}`
      const blobUrl = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = blobUrl
      a.download = filename
      document.body.appendChild(a)
      a.click()
      a.remove()
      URL.revokeObjectURL(blobUrl)
      // small delay to avoid browser download throttling
      await new Promise(r => setTimeout(r, 120))
      // update progress after each download
      downloadProgress.value = Math.round(((i + 1) / ids.length) * 100)
    } catch (e) {
      // fallback: open in new tab
      window.open(url, '_blank')
    }
  }
  // finish
  downloadInProgress.value = false
  downloadProgress.value = 0
}

const downloadZipSelected = async () => {
  const ids = Array.from(selectedIds.value)
  if (ids.length === 0) return
  downloadInProgress.value = true
  downloadProgress.value = 0
  // try server-side zip endpoint first
  try {
    const albumId = album.value?.id
    const endpoint = albumId ? `/api/albums/${albumId}/download-zip` : '/api/photos/zip'
    const resp = await fetch(endpoint, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ ids })
    })
    if (resp.ok) {
      const blob = await resp.blob()
      const blobUrl = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = blobUrl
      a.download = `${album.value?.name || 'photos'}.zip`
      document.body.appendChild(a)
      a.click()
      a.remove()
      URL.revokeObjectURL(blobUrl)
      return
    }
  } catch (e) {
    console.warn('zip download server failed, fallback to client-side', e)
  }
  // try client-side zip using JSZip
  try {
    // @ts-ignore - dynamic import of optional dependency
    const JSZipModule = await import('jszip')
    const JSZip = JSZipModule.default || JSZipModule
    const zip = new JSZip()
    let fetched = 0
    for (const id of ids) {
      const photo = photos.value.find((p: any) => p.id === id)
      if (!photo) continue
      const url = getImageUrl(photo)
      try {
        const resp = await fetch(url, { credentials: 'same-origin' })
        if (!resp.ok) throw new Error('fetch failed')
        const blob = await resp.blob()
        const extMatch = (photo.originalPath || photo.webpPath || '').split('.').pop() || 'jpg'
        const filename = `${photo.filename || 'photo'}.${extMatch}`
        zip.file(filename, blob)
        fetched++
        // update progress during fetch phase (0-60%)
        downloadProgress.value = Math.round((fetched / ids.length) * 60)
      } catch (err) {
        console.warn('fetch for zip failed', id, err)
      }
    }
    const content = await zip.generateAsync({ type: 'blob' }, (meta: any) => {
      // meta.percent provided by JSZip
      const percent = Math.min(100, Math.round(meta.percent || 0))
      // map meta.percent (0-100) to 60-99 range considering fetch progress
      downloadProgress.value = 60 + Math.round((percent / 100) * 39)
    })
    const blobUrl = URL.createObjectURL(content)
    const a = document.createElement('a')
    a.href = blobUrl
    a.download = `${album.value?.name || 'photos'}.zip`
    document.body.appendChild(a)
    a.click()
    a.remove()
    URL.revokeObjectURL(blobUrl)
    downloadProgress.value = 100
    setTimeout(() => {
      downloadInProgress.value = false
      downloadProgress.value = 0
    }, 600)
    return
  } catch (e) {
    console.warn('client-side zip failed or JSZip not available, fallback to sequential downloads', e)
    await downloadSelected()
    downloadInProgress.value = false
  }
}
const handleBack = async () => {

  // 如果多选激活，优先关闭多选
  if (multiSelectActive.value) {
    clearMultiSelect()
    return
  }

  if (viewerVisible.value) {
    viewerVisible.value = false
    return
  }

  // 获取来源页面（从 URL 参数获取，更可靠）
  const fromParam = route.query.from as string
  const entryPage = sessionStorage.getItem('album-entry-page')
  
  // URL 参数优先，其次是 sessionStorage
  const targetPage = fromParam || entryPage
  
  // 判断是否应该使用动画：从 Home 页面直接导航来的（没有 from 参数，且 entryPage 是 / 或空）
  const shouldAnimate = !fromParam && (!entryPage || entryPage === '/' || entryPage === '')
  
  console.log('[AlbumDetail] handleBack - fromParam:', fromParam, 'entryPage:', entryPage, 'shouldAnimate:', shouldAnimate)

  // 清理临时状态
  cleanupAnimationState()

  if (shouldAnimate && photos.value.length > 0) {
    // 从相册列表来的，使用返回动画
    startBackTransitionAndNavigate()
  } else {
    // 从其他页面或直接 URL 进入，直接导航
    // 清理动画相关状态
    sessionStorage.removeItem('album-back-transition')
    sessionStorage.removeItem('album-animation-performed')
    sessionStorage.removeItem('album-navigation-active')
    sessionStorage.removeItem('album-entry-page')
    
    // 根据来源决定去向
    if (targetPage && targetPage !== '/') {
      // 从其他页面来的，返回该页面
      router.push(targetPage)
    } else {
      // 直接 URL 进入或无来源，返回相册列表
      router.push('/')
    }
  }
}

// 分离动画清理逻辑，便于复用
const cleanupAnimationState = () => {
  // 清理定时器
  if ((window as any).__albumTransitionCleanupTimer) {
    clearTimeout((window as any).__albumTransitionCleanupTimer)
    delete (window as any).__albumTransitionCleanupTimer
  }
  if ((window as any).__albumTransitionRemoveTimer) {
    clearTimeout((window as any).__albumTransitionRemoveTimer)
    delete (window as any).__albumTransitionRemoveTimer
  }

  // 清理临时克隆元素（详情页展开动画使用的克隆）
  transitionClones.forEach(clone => {
    clone.remove()
  })
  transitionClones = []

  // 注意：不要清理 .album-back-clone 元素，它们会被传递到 Home 页用于返回动画

  // 恢复所有照片的显示状态
  photoRefs.value.forEach((photoElement) => {
    photoElement.style.visibility = ''
    photoElement.style.pointerEvents = ''
    photoElement.style.transition = ''
  })
}

const handleKeydown = (e: KeyboardEvent) => {
  if (e.key === 'Escape') {
    e.preventDefault() // 防止浏览器默认行为
    // 优先关闭多选模式
    if (multiSelectActive.value) {
      clearMultiSelect()
      return
    }
    if (viewerVisible.value) {
      // 直接关闭查看器，不要等待
      viewerVisible.value = false
    } else {
      // 使用完整的返回动画逻辑（与按钮点击保持一致）
      handleBack()
    }
  }
}

// 返回按钮交互：悬停倾斜、点击水波、离开重置
const backButtonRef = ref<HTMLElement | null>(null)
const onBackButtonMouseMove = (e: MouseEvent) => {
  const el = e.currentTarget as HTMLElement
  if (!el) return
  const rect = el.getBoundingClientRect()
  const cx = rect.left + rect.width / 2
  const cy = rect.top + rect.height / 2
  const dx = e.clientX - cx
  const dy = e.clientY - cy
  const ry = (dx / rect.width) * 10 // rotateY
  const rx = -(dy / rect.height) * 6 // rotateX
  el.style.setProperty('--rX', `${rx}deg`)
  el.style.setProperty('--rY', `${ry}deg`)
  el.style.setProperty('--tx', `${ry * 0.4}px`)
  el.style.setProperty('--ty', `${rx * 0.2}px`)
}

const onBackButtonMouseLeave = (e: MouseEvent) => {
  const el = e.currentTarget as HTMLElement
  if (!el) return
  el.style.setProperty('--rX', '0deg')
  el.style.setProperty('--rY', '0deg')
  el.style.setProperty('--tx', '0px')
  el.style.setProperty('--ty', '0px')
}

const onBackButtonMouseDown = (e: MouseEvent) => {
  const el = e.currentTarget as HTMLElement
  if (!el) return
  // ripple
  const rect = el.getBoundingClientRect()
  const x = e.clientX - rect.left
  const y = e.clientY - rect.top
  const ripple = document.createElement('span')
  ripple.className = 'btn-ripple'
  ripple.style.left = `${x}px`
  ripple.style.top = `${y}px`
  el.appendChild(ripple)
  setTimeout(() => ripple.remove(), 600)
}

// download progress UI state
const downloadInProgress = ref(false)
const downloadProgress = ref(0)

// 执行从封面到详情页的 FLIP 动画
const performCoverTransition = async (): Promise<boolean> => {
  const targetAlbumId = albumId.value
  if (!targetAlbumId) return false

  const storageKey = `album-cover-rects-${targetAlbumId}`
  const storedData = sessionStorage.getItem(storageKey)

  if (!storedData || photos.value.length === 0) {
    return false
  }
  
  try {
    const coverRects: Array<{ photoId: number; rect: { top: number; left: number; width: number; height: number } }> = JSON.parse(storedData)
    
    // 等待 DOM 更新完成
    await nextTick()
    
    // 找到对应的照片元素，并等待瀑布流布局完成
    const transitions: Array<{
      photoId: number
      fromRect: DOMRect
      toRect: DOMRect
      img: HTMLImageElement
    }> = []
    
    for (const { photoId, rect: fromRectData } of coverRects) {
      const photoElement = photoRefs.value.get(photoId)
      if (!photoElement) continue
      
      const img = photoElement.querySelector('img') as HTMLImageElement
      if (!img) continue
      
      // 等待目标元素具有有效的尺寸（瀑布流可能需要额外时间布局）
      let toRect: DOMRect
      let attempts = 0
      const maxAttempts = 10 // 最多等待10次

      do {
        toRect = photoElement.getBoundingClientRect()
        attempts++

        // 如果尺寸无效（宽度或高度为0或小于最小阈值），等待一下再试
        if (toRect.width <= 1 || toRect.height <= 1) {
          await new Promise(resolve => setTimeout(resolve, 50))
        }
      } while ((toRect.width <= 1 || toRect.height <= 1) && attempts < maxAttempts)

      // 如果仍然没有有效的尺寸，使用默认的合理尺寸
      if (toRect.width <= 1 || toRect.height <= 1) {
        console.warn(`目标元素尺寸无效，使用默认尺寸 (photoId: ${photoId}, width: ${toRect.width}, height: ${toRect.height})`)
        // 使用原始尺寸作为默认值，避免动画变成一条线
        toRect = new DOMRect(toRect.left, toRect.top, fromRectData.width, fromRectData.height)
      }
      
      transitions.push({
        photoId,
        fromRect: new DOMRect(fromRectData.left, fromRectData.top, fromRectData.width, fromRectData.height),
        toRect,
        img
      })
    }
    
    if (transitions.length === 0) {
      sessionStorage.removeItem(storageKey)
      return true
    }
    
    // 创建临时克隆元素
    transitionClones = []
    // transitionPhotoIds 和 isTransitioning 已经在 onMounted 中设置了
    // 如果 transitions 为空，清理状态
    if (transitions.length === 0) {
      transitionPhotoIds.value = []
      isTransitioning.value = false
      return false
    }
    
    // 在封面动画开始前，同时开始剩余图片的动画
    nextTick(() => {
      remainingPhotosVisible.value = true
    })

    for (const { photoId, fromRect, toRect, img } of transitions) {
      // 确保图片已经加载完成
      if (!img.complete) {
        await new Promise((resolve) => {
          if (img.complete) {
            resolve(undefined)
          } else {
            img.onload = () => resolve(undefined)
            img.onerror = () => resolve(undefined) // 即使加载失败也继续
          }
        })
      }
      
      const clone = img.cloneNode(true) as HTMLImageElement
      // 确保克隆的图片也使用相同的 src
      clone.src = img.src
      clone.style.position = 'fixed'
      clone.style.top = `${fromRect.top}px`
      clone.style.left = `${fromRect.left}px`
      clone.style.width = `${fromRect.width}px`
      clone.style.height = `${fromRect.height}px`
      clone.style.objectFit = 'cover'
      clone.style.zIndex = '9999'
      clone.style.pointerEvents = 'none'
      clone.style.borderRadius = '8px'
      clone.style.boxShadow = '0 4px 6px -1px rgba(0, 0, 0, 0.1), 0 2px 4px -1px rgba(0, 0, 0, 0.06)'
      // 使用非线性 ease 曲线，但不要回弹
      clone.style.transition = 'all 400ms cubic-bezier(0.22, 1, 0.36, 1)'
      clone.style.willChange = 'transform, width, height, top, left'
      
      document.body.appendChild(clone)
      transitionClones.push(clone)
      
      // 完全隐藏原始图片（使用 visibility 而不是 opacity，避免过渡效果）
      const photoElement = photoRefs.value.get(photoId)
      if (photoElement) {
        photoElement.style.visibility = 'hidden'
        photoElement.style.pointerEvents = 'none'
        photoElement.style.transition = 'none' // 禁用所有过渡效果
      }
      
      // 触发动画
      requestAnimationFrame(() => {
        requestAnimationFrame(() => {
          clone.style.top = `${toRect.top}px`
          clone.style.left = `${toRect.left}px`
          clone.style.width = `${toRect.width}px`
          clone.style.height = `${toRect.height}px`
        })
      })
    }
    
    // 动画完成后（约 420ms），无缝切换
    const cleanupTimer = setTimeout(() => {
      // 使用 requestAnimationFrame 确保在下一帧执行，避免闪烁
      requestAnimationFrame(() => {
        // 先让原始图片可见
        transitions.forEach(({ photoId }) => {
          const photoElement = photoRefs.value.get(photoId)
          if (photoElement) {
            photoElement.style.visibility = 'visible'
            photoElement.style.pointerEvents = ''
          }
        })

        // 在同一帧中立即移除克隆元素
        requestAnimationFrame(() => {
          transitionClones.forEach(clone => {
            clone.remove()
          })
          transitionClones = []

          // 恢复原始图片的样式
          transitions.forEach(({ photoId }) => {
            const photoElement = photoRefs.value.get(photoId)
            if (photoElement) {
              photoElement.style.visibility = ''
              photoElement.style.pointerEvents = ''
              photoElement.style.transition = ''
            }
          })

          isTransitioning.value = false
          transitionPhotoIds.value = []

          // 不要在这里清除 sessionStorage，保留它以便返回时执行反向动画
          // sessionStorage.removeItem(storageKey)
        })
      })
    }, 420)
    
    // 保存清理定时器，以便在组件卸载时清理
    ;(window as any).__albumTransitionCleanupTimer = cleanupTimer
    
    return true
  } catch (error) {
    console.error('执行封面过渡动画失败:', error)
    sessionStorage.removeItem(storageKey)
    isTransitioning.value = false
    transitionPhotoIds.value = []
    return false
  }
}

// 启动返回相册列表时的克隆动画（真正的缩回动画在 Home 页执行）
const startBackTransitionAndNavigate = () => {
  const targetAlbumId = albumId.value
  if (!targetAlbumId) {
    router.push('/')
    return
  }

  const storageKey = `album-cover-rects-${targetAlbumId}`
  const storedData = sessionStorage.getItem(storageKey)
  const isFromDirectUrl = sessionStorage.getItem('album-navigation-active') !== 'true'

  console.log('[返回动画] 启动，返回方式:', isFromDirectUrl ? '直接URL进入' : '正常导航')

  // 如果没有封面位置信息，直接跳转
  if (!storedData || photos.value.length === 0) {
    console.log('[返回动画] 无封面数据，直接跳转')
    sessionStorage.removeItem('album-back-transition')
    sessionStorage.removeItem('album-animation-performed')
    sessionStorage.removeItem('album-navigation-active')
    sessionStorage.removeItem('album-entry-page')
    
    // 根据来源决定去向
    const entryPage = sessionStorage.getItem('album-entry-page')
    if (entryPage && entryPage !== '/') {
      router.push(entryPage)
    } else {
      router.push('/')
    }
    return
  }

  try {
    const coverRects: Array<{ photoId: number }> = JSON.parse(storedData)
    const usedPhotoIds: number[] = []

    // 保存当前滚动位置
    const currentScrollTop = window.scrollY || document.documentElement.scrollTop
    const currentScrollLeft = window.scrollX || document.documentElement.scrollLeft

    // scroll position saved

    // 临时禁用滚动
    const preventScroll = (e: Event) => {
      e.preventDefault()
      window.scrollTo(currentScrollLeft, currentScrollTop)
    }

    // 添加滚动事件监听器，强制保持滚动位置
    window.addEventListener('scroll', preventScroll, { passive: false })
    window.addEventListener('wheel', preventScroll, { passive: false })
    window.addEventListener('touchmove', preventScroll, { passive: false })

    // scroll protection added

    // 在返回前，保存 coverRects 数据到 backTransitionData（因为详情页会在动画完成后清理它）
    const backTransitionData = {
      albumId: targetAlbumId,
      photoIds: [],  // 稍后填充
      scrollTop: currentScrollTop,
      scrollLeft: currentScrollLeft,
      coverRects: coverRects  // 保存封面位置数据，用于 Home 页执行缩回动画
    }

    // 使用 requestAnimationFrame 延迟创建克隆元素，避免影响当前页面布局
    requestAnimationFrame(() => {
      // requestAnimationFrame callback

      // 为三张封面对应的照片创建克隆元素，停留在当前详情页的位置
      for (const { photoId } of coverRects) {
        const photoElement = photoRefs.value.get(photoId)
        if (!photoElement) continue

        const img = photoElement.querySelector('img') as HTMLImageElement
        if (!img) continue

        const fromRect = photoElement.getBoundingClientRect()

        // 创建图片克隆
        const clone = img.cloneNode(true) as HTMLImageElement
        clone.src = img.src
        clone.style.position = 'fixed'
        clone.style.top = `${fromRect.top}px`
        clone.style.left = `${fromRect.left}px`
        clone.style.width = `${fromRect.width}px`
        clone.style.height = `${fromRect.height}px`
        clone.style.objectFit = 'cover'
        clone.style.zIndex = '9999'
        clone.style.pointerEvents = 'none'
        clone.style.borderRadius = '8px'
        clone.style.boxShadow = '0 4px 6px -1px rgba(0, 0, 0, 0.1), 0 2px 4px -1px rgba(0, 0, 0, 0.06)'
        // 使用带轻微回弹效果
        clone.style.transition = 'all 340ms cubic-bezier(0.34, 1.4, 0.63, 1)'
        clone.style.willChange = 'transform, width, height, top, left'
        clone.classList.add('album-back-clone')
        clone.dataset.albumId = String(targetAlbumId)
        clone.dataset.photoId = String(photoId)

        document.body.appendChild(clone)
        usedPhotoIds.push(photoId)

        // 查找"共x张"覆盖层并克隆
        const overlay = photoElement.closest('.cover-display')?.querySelector('.album-cover-overlay') as HTMLElement
        if (overlay) {
          const overlayRect = overlay.getBoundingClientRect()
          const overlayClone = overlay.cloneNode(true) as HTMLElement
          overlayClone.style.position = 'fixed'
          overlayClone.style.top = `${overlayRect.top}px`
          overlayClone.style.left = `${overlayRect.left}px`
          overlayClone.style.width = `${overlayRect.width}px`
          overlayClone.style.height = `${overlayRect.height}px`
          overlayClone.style.zIndex = '10000'
          overlayClone.style.pointerEvents = 'none'
          overlayClone.style.borderRadius = overlay.style.borderRadius || '4px'
          // 初始状态：缩小并向下偏移，准备向上出现的动画
          overlayClone.style.transform = 'scale(0.95) translateY(8px)'
          overlayClone.style.opacity = '0'
          // 使用非线性 ease 曲线
          overlayClone.style.transition = 'all 400ms cubic-bezier(0.22, 1, 0.36, 1), opacity 200ms ease-out'
          overlayClone.style.willChange = 'transform, width, height, top, left'
          overlayClone.classList.add('album-back-overlay-clone')
          overlayClone.dataset.albumId = String(targetAlbumId)
          overlayClone.dataset.photoId = String(photoId)

          document.body.appendChild(overlayClone)
        }
      }

      // clones created

      console.log('[返回动画] 克隆数量:', usedPhotoIds.length)

      // 如果没有创建任何克隆，直接跳转
      if (usedPhotoIds.length === 0) {
        sessionStorage.removeItem('album-back-transition')
        sessionStorage.removeItem('album-animation-performed')
        sessionStorage.removeItem('album-navigation-active')
        sessionStorage.removeItem('album-entry-page')

        const entryPage = sessionStorage.getItem('album-entry-page')
        if (entryPage && entryPage !== '/') {
          router.push(entryPage)
        } else {
          router.push('/')
        }
        return
      }

      // 记录本次返回动画需要用到的相册和照片 ID，供 Home 页继续执行缩回动画
      // 包含 coverRects 数据，因为详情页会在动画完成后清理它
      backTransitionData.photoIds = usedPhotoIds
      backTransitionData.coverRects = coverRects
      sessionStorage.setItem('album-back-transition', JSON.stringify(backTransitionData))

      // 清理已使用的数据
      sessionStorage.removeItem(storageKey)
      // 清理 entry-page，让 Home 页能正常恢复滚动位置
      sessionStorage.removeItem('album-entry-page')

      // session storage set

      // 使用 router.push('/') 确保导航成功（router.back() 在某些情况下可能无效）
      console.log('[返回动画] 导航到 Home')
      router.push('/')

      // 在路由切换后移除滚动防护（使用 setTimeout 确保在下一事件循环中执行）
      setTimeout(() => {
        // remove scroll protection
        window.removeEventListener('scroll', preventScroll)
        window.removeEventListener('wheel', preventScroll)
        window.removeEventListener('touchmove', preventScroll)
      }, 0)
    })
  } catch (error) {
    console.error('启动返回相册列表动画失败:', error)
    // 出错时导航到主页
    sessionStorage.removeItem('album-back-transition')
    sessionStorage.removeItem('album-animation-performed')
    sessionStorage.removeItem('album-navigation-active')
    sessionStorage.removeItem('album-entry-page')
    
    const entryPage = sessionStorage.getItem('album-entry-page')
    if (entryPage && entryPage !== '/') {
      router.push(entryPage)
    } else {
      router.push('/')
    }
  }
}

// 加载相册数据的主要函数（onMounted 和 onActivated 都会调用）
const loadAlbumData = async () => {
  // 解析相册 ID（支持 ID 或名称）
  const targetAlbumId = await resolveAlbumId()
  if (!targetAlbumId) {
    console.error('[AlbumDetail] 无效的相册 ID')
    router.push('/')
    return
  }

  const storageKey = `album-cover-rects-${targetAlbumId}`
  const storedData = sessionStorage.getItem(storageKey)

  // 如果有需要动画的图片，且是从正常导航来的，立即隐藏它们（在数据加载前）
  const navigationTimestamp = sessionStorage.getItem('album-navigation-active')
  const isFromNavigation = navigationTimestamp && (Date.now() - parseInt(navigationTimestamp)) < 5000

  // 获取来源页面
  const fromParam = route.query.from as string
  let savedEntryPage = sessionStorage.getItem('album-entry-page')

  console.log('[AlbumDetail] loadAlbumData - fromParam:', fromParam, 'savedEntryPage:', savedEntryPage)

  if (fromParam) {
    sessionStorage.setItem('album-entry-page', fromParam)
  } else if (!savedEntryPage) {
    if (document.referrer && document.referrer.includes(window.location.origin)) {
      try {
        const referrerUrl = new URL(document.referrer)
        const referrerPath = referrerUrl.pathname
        if (referrerPath && referrerPath !== route.path && !referrerPath.match(/^\/(album|person|photo|a|p)\/\d+$/)) {
          savedEntryPage = referrerPath
          sessionStorage.setItem('album-entry-page', savedEntryPage)
          console.log('[AlbumDetail] loadAlbumData - saved from referrer:', savedEntryPage)
        }
      } catch {
        // ignore
      }
    }
  }

  if (storedData && isFromNavigation) {
    try {
      const coverRects: Array<{ photoId: number }> = JSON.parse(storedData)
      const photoIdsToHide = coverRects.map(r => r.photoId)
      transitionPhotoIds.value = photoIdsToHide
      isTransitioning.value = true
    } catch (e) {
      // ignore
    }
  }

  // 注意：数据已在 onDeactivated 中清空，此处直接加载新数据
  await photoStore.fetchAlbumById(targetAlbumId)

  const album = photoStore.currentAlbum

  // 初始加载第一页照片
  const initialLoadSize = 50
  const result = await photoStore.fetchPhotosByAlbum(targetAlbumId, 0, initialLoadSize)
  hasMore.value = !result.last

  totalImages.value = photos.value.length

  isInitialLoading.value = false

  window.addEventListener('scroll', handleScroll, { passive: true })

  resetImageLoading()

  await loadCommentCount(targetAlbumId)
  await loadAlbumPersons(targetAlbumId)

  // 只在图片真正加载完成时显示评论区，不做超时强制展示
  window.addEventListener('keydown', handleKeydown)

  // 等待照片元素渲染完成
  await nextTick()

  if (isFromNavigation && transitionPhotoIds.value.length > 0) {
    transitionPhotoIds.value.forEach(photoId => {
      const photoElement = photoRefs.value.get(photoId)
      if (photoElement) {
        photoElement.style.visibility = 'hidden'
        photoElement.style.transition = 'none'
      }
    })
  }

  // 再等待一帧，确保所有图片都已渲染
  await new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)))

  // 准备剩余图片的动画数据
  let remainingIndex = 0
  photos.value.forEach((photo) => {
    if (!transitionPhotoIds.value.includes(photo.id)) {
      remainingPhotoIndexes.value.set(photo.id, remainingIndex++)
    }
  })

  // 执行封面动画
  const hasCoverTransition = isFromNavigation ? await performCoverTransition() : false
  console.log('[详情页] 展开动画结果:', { isFromNavigation, hasCoverTransition })

  if (hasCoverTransition) {
    sessionStorage.setItem('album-animation-performed', 'true')
  }

  // 如果没有封面动画，直接开始剩余图片动画
  if (!hasCoverTransition) {
    remainingPhotosVisible.value = true
  }

  // 动画完成后清理上一个相册的动画缓存
  await nextTick()
  sessionStorage.removeItem('album-back-transition')
}

onMounted(async () => {
  // 获取全局下载权限设置
  try {
    const response = await api.get('/admin/config/global-download-allowed')
    globalDownloadAllowed.value = response.data.globalDownloadAllowed !== false
  } catch (error) {
    console.warn('获取全局下载权限设置失败:', error)
    globalDownloadAllowed.value = false
  }

  // 确保页面从顶部开始显示
  window.scrollTo(0, 0)

  // 添加窗口大小监听（实时响应）
  window.addEventListener('resize', handleResize)

  // 加载相册数据
  await loadAlbumData()
})

// KeepAlive 激活时重新加载数据（解决缓存后数据残留问题）
onActivated(async () => {
  // 检查当前相册 ID 是否与路由匹配
  const currentAlbumId = await resolveAlbumId()
  const storeAlbum = photoStore.currentAlbum

  // 如果没有数据或 ID 不匹配，需要重新加载
  if (!storeAlbum || storeAlbum.id !== currentAlbumId) {
    console.log('[AlbumDetail] onActivated - 需要重新加载数据')
    await loadAlbumData()
  } else {
    // 即使数据匹配，也重置滚动位置
    window.scrollTo(0, 0)
    console.log('[AlbumDetail] onActivated - 数据已存在，使用缓存')
  }
})

onUnmounted(() => {
  // 离开详情页（包括按 ESC / 浏览器返回）时清空，避免下次进入时闪现旧信息
  photoStore.currentAlbum = null
  photoStore.photos = []
  commentCount.value = 0
  albumPersons.value = []
  albumPersonsLoading.value = false
  imagesLoaded.value = false
  loadedImagesCount.value = 0
  totalImages.value = 0
  isInitialLoading.value = true

  window.removeEventListener('keydown', handleKeydown)
  window.removeEventListener('resize', handleResize)
  window.removeEventListener('scroll', handleScroll)

  // 清理临时克隆元素（详情页展开动画使用的克隆）
  transitionClones.forEach(clone => {
    clone.remove()
  })
  transitionClones = []

  // 注意：不要清理 .album-back-clone 元素，它们会被传递到 Home 页用于返回动画

  // 注意：不要清理 album-cover-rects 和 album-animation-performed，因为返回动画需要用到这些数据
  // 动画相关数据会在 Home.vue 的返回动画完成后清理

  // 只清理导航标志（如果还在的话）
  sessionStorage.removeItem('album-navigation-active')

  // 清理可能遗留的动画定时器
  if ((window as any).__albumTransitionCleanupTimer) {
    clearTimeout((window as any).__albumTransitionCleanupTimer)
    ;(window as any).__albumTransitionCleanupTimer = null
  }
})

// 获取相册评论数量
const loadCommentCount = async (albumId: number) => {
  try {
    const response = await commentApi.getAlbumCommentCount(albumId)
    commentCount.value = response.data
  } catch (error) {
    console.error('Failed to load comment count:', error)
    commentCount.value = 0
  }
}

// 获取相册中的人物列表
const loadAlbumPersons = async (albumId: number) => {
  try {
    albumPersonsLoading.value = true
    const response = await personApi.getAlbumPersons(albumId)
    albumPersons.value = response.data || []
  } catch (error) {
    console.error('Failed to load album persons:', error)
    albumPersons.value = []
  } finally {
    albumPersonsLoading.value = false
  }
}

// 颜色处理工具函数
const hexToRgb = (hex: string) => {
  const result = /^#?([a-f\d]{2})([a-f\d]{2})([a-f\d]{2})$/i.exec(hex)
  return result ? {
    r: parseInt(result[1], 16),
    g: parseInt(result[2], 16),
    b: parseInt(result[3], 16)
  } : null
}



// 处理图片加载完成事件
const handleImageLoaded = () => {
  // 图片加载完成后可能需要重新计算布局
  loadedImagesCount.value++
  console.log(`图片加载完成: ${loadedImagesCount.value}/${totalImages.value}`)

  // 当所有图片都加载完成时，标记图片已加载
  if (loadedImagesCount.value >= totalImages.value && totalImages.value > 0) {
    imagesLoaded.value = true
  }
}

// 重置图片加载状态
const resetImageLoading = () => {
  loadedImagesCount.value = 0
  imagesLoaded.value = false
}

const getBrightness = (hex: string) => {
  const rgb = hexToRgb(hex)
  if (!rgb) return 0.5

  // 使用相对亮度公式
  return (rgb.r * 0.299 + rgb.g * 0.587 + rgb.b * 0.114) / 255
}
</script>

