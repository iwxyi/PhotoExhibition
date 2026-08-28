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
          <h1 class="album-title" :style="titleStyle">{{ album.displayName || album.name }}</h1>
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
            <span v-if="album.takenAt" class="text-gray-400">·</span>
            <span v-if="album.takenAt">{{ formatAlbumTakenAt(album.takenAt) }}</span>
            <span v-if="commentCount > 0" class="text-gray-400">·</span>
            <span v-if="commentCount > 0">{{ commentCount }} 条评论</span>
          </p>
        </div>

        <!-- 人物列表 - 横向可滚动 -->
        <div
          class="album-persons-slot"
          :class="{ 'album-persons-slot--reserved': albumPersons.length > 0, 'album-persons-slot--visible': showAlbumPersons && albumPersons.length > 0 }"
          ref="albumPersonsSlotRef"
          :style="{ '--persons-height': `${albumPersonsHeight}px` }"
        >
        <div
          v-if="albumPersons.length > 0"
          class="album-persons-section"
          :class="{ 'album-persons-section--dark': atmosphereEnabled && hasAtmosphereColors && themeStore.isDark, 'album-persons-section--light': atmosphereEnabled && hasAtmosphereColors && !themeStore.isDark }"
        >
          <div class="album-persons-scroll">
            <a
              v-for="(person, index) in albumPersons"
              :key="person.id"
              :href="buildPublicPath(`/p/${person.id}?from=${encodeURIComponent(route.fullPath)}`, route.path)"
              target="_blank"
              rel="noopener noreferrer"
              class="album-person-card"
              :class="{ 'album-person-card--highlighted': hoveredPhotoPersonIds.has(person.id) }"
              :style="{ '--delay': `${index * 80}ms` }"
              @click="handlePersonClick(person, $event)"
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
            </a>
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
              @mouseenter="onPhotoHover(photo, true)"
              @mouseleave="onPhotoHover(photo, false)"
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
import { useAuthStore } from '@/stores/auth'
import { useLanguageStore } from '@/stores/language'
import { buildPublicPath, stripPublicSlug } from '@/utils/publicRoute'
import { buildPhotoAssetUrl } from '@/utils/photoUrl'

const route = useRoute()
const router = useRouter()
const photoStore = usePhotoStore()
const authStore = useAuthStore()
const languageStore = useLanguageStore()
const themeStore = useThemeStore()

// 预存的氛围背景色（从相册列表页传递，用于避免页面跳转时的闪烁）
// 需要在下面的 immediate watch 注册前初始化，避免首次执行时触发 TDZ。
const presetAtmosphereBg = ref<string | null>(null)

const album = computed(() => photoStore.currentAlbum)
const photos = computed(() => photoStore.photos)

// 监听相册数据变化，更新页面标题
watch(album, (newAlbum) => {
  if (newAlbum?.name) {
    const baseTitle = languageStore.language === 'zh'
      ? (authStore.projectNameZh || authStore.projectNameEn || '光忆集')
      : (authStore.projectNameEn || authStore.projectNameZh || 'Aurellic Memoriq')
    document.title = `${baseTitle} - ${newAlbum.displayName || newAlbum.name}`
  }
  // 当相册数据加载后，清除预存的背景色（避免覆盖真实数据）
  if (newAlbum) {
    const bgColor = themeStore.isDark ? newAlbum.darkBgColor : newAlbum.lightBgColor
    if (bgColor) {
      presetAtmosphereBg.value = null
      // 清理 sessionStorage
      sessionStorage.removeItem(`album-atmosphere-bg-${newAlbum.id}`)
    }
  }
}, { immediate: true })

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
// 人物栏会改变照片瀑布流的起始位置，等封面过渡完成后再渲染，避免动画中途整体下移。
const showAlbumPersons = ref(false)
const albumPersonsSlotRef = ref<HTMLElement | null>(null)
const albumPersonsHeight = ref(0)
let albumPersonsRevealTimer: number | null = null

watch(albumPersons, async () => {
  await nextTick()
  albumPersonsHeight.value = albumPersonsSlotRef.value?.scrollHeight || 0
})

// 当前悬浮的照片关联的人物ID集合
const hoveredPhotoPersonIds = ref<Set<number>>(new Set())

// 从照片数据中获取关联的人物ID列表
const getPhotoPersonIds = (photo: any): number[] => {
  if (!photo?.faces || !Array.isArray(photo.faces)) return []
  const personIds: number[] = []
  for (const face of photo.faces) {
    if (face.personId) personIds.push(face.personId)
  }
  return [...new Set(personIds)] // 去重
}

// 鼠标悬停在照片上时
const onPhotoHover = (photo: any, isEnter: boolean) => {
  // 从照片数据中获取人物ID - 兼容 masonry item 结构
  const rawPhoto = photo?.data || photo
  const personIds = getPhotoPersonIds(rawPhoto)

  if (isEnter) {
    // 添加该照片关联的所有人物ID
    personIds.forEach(id => hoveredPhotoPersonIds.value.add(id))
  } else {
    // 移除该照片关联的所有人物ID
    personIds.forEach(id => hoveredPhotoPersonIds.value.delete(id))
  }
  // 触发响应式更新
  hoveredPhotoPersonIds.value = new Set(hoveredPhotoPersonIds.value)
}

// 人物卡片点击处理 - 保留点击事件以支持链接跳转
const handlePersonClick = (_person: AlbumPerson, _event: MouseEvent) => {
  // 使用 href 跳转，不需要额外处理
}

// 图片加载状态
const imagesLoaded = ref(false)
const totalImages = ref(0)
const loadedImagesCount = ref(0)

// 评论显示：照片加载完成后显示（API返回后即显示）
const showComments = ref(false)

// 监听照片数量变化，API返回照片后显示评论区
watch(() => photos.value.length, (newCount) => {
  console.log('[AlbumDetail] photos.length 变化:', newCount)
  if (newCount > 0) {
    showComments.value = true
    console.log('[AlbumDetail] showComments 设为 true')
  }
})

// 分页加载状态
const currentPage = ref(0)
const loadingMore = ref(false)
const hasMore = ref(true)
const pageSize = 30 // 每次加载30张照片
const isInitialLoading = ref(true) // 初始加载状态，用于避免显示旧数据
let isDisposed = false

// 首屏优先复用首页列表中已经拿到的相册摘要，避免标题、介绍和氛围色
// 必须等待详情接口返回。详情接口随后会在 loadAlbumData 中后台校正这些数据。
// 如果是直接打开详情 URL，列表中没有摘要，此处仍保持空值并走完整加载态。
const seededAlbumId = albumId.value
const listedAlbum = seededAlbumId
  ? photoStore.albums.find((item) => item.id === seededAlbumId) || null
  : null
// 使用快照而不是直接共享 Home 列表中的 Pinia 响应式代理，
// 避免路由切换时两个组件同时更新同一个对象。
const seededAlbum = listedAlbum ? { ...listedAlbum } : null
photoStore.currentAlbum = seededAlbum
photoStore.photos = []
commentCount.value = 0
albumPersons.value = []
albumPersonsLoading.value = false
showAlbumPersons.value = false
albumPersonsHeight.value = 0
imagesLoaded.value = false
loadedImagesCount.value = 0
totalImages.value = 0
showComments.value = false

const { atmosphereEnabled, previewSize } = useUiSettings()

// 窗口宽度响应式（用于触发columnCount重新计算）
const windowWidth = ref(typeof window !== 'undefined' ? window.innerWidth : 1920)

// 监听窗口大小变化（实时响应）
const handleResize = () => {
  windowWidth.value = window.innerWidth
}

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

// 是否有氛围颜色数据
const hasAtmosphereColors = computed(() => {
  return !!(album.value?.darkBgColor && album.value?.lightBgColor)
})

// 当前模式下的氛围背景色
const atmosphereBgColor = computed(() => {
  if (!hasAtmosphereColors.value) return null
  return themeStore.isDark ? album.value!.darkBgColor! : album.value!.lightBgColor!
})

// 当前模式下的氛围点缀色（用于标题等）
const atmosphereAccentColor = computed(() => {
  if (!album.value?.darkAccentColor || !album.value?.lightAccentColor) return null
  return themeStore.isDark ? album.value.darkAccentColor : album.value.lightAccentColor
})

// 背景样式（基于相册的背景颜色或默认主题，支持氛围开关）
const backgroundStyle = computed(() => {
  // 优先使用预存的背景色（从相册列表页传递，避免闪烁）
  if (presetAtmosphereBg.value) {
    return {
      backgroundColor: presetAtmosphereBg.value
    }
  }
  if (atmosphereEnabled.value && atmosphereBgColor.value) {
    return {
      backgroundColor: atmosphereBgColor.value
    }
  } else if (!atmosphereEnabled.value) {
    return {
      backgroundColor: themeStore.isDark ? '#000000' : '#ffffff'
    }
  }
  return {}
})

// 文字样式（确保在任何背景下都有足够对比度）
const textStyle = computed(() => {
  if (atmosphereEnabled.value && atmosphereBgColor.value) {
    // 深色模式用浅色文字，浅色模式用深色文字
    return { color: themeStore.isDark ? '#e8e8e8' : '#2a2a2a' }
  } else if (!atmosphereEnabled.value) {
    return {
      color: themeStore.isDark ? '#ffffff' : '#1a1a1a'
    }
  }
  return {}
})

// 标题样式（使用点缀色，在氛围背景上更醒目）
const titleStyle = computed(() => {
  if (atmosphereEnabled.value && atmosphereAccentColor.value) {
    return { color: atmosphereAccentColor.value }
  }
  return textStyle.value
})

// 氛围特效列表
const albumAtmosphereEffects = computed(() => {
  if (!atmosphereEnabled.value) {
    return []
  }
  return album.value?.atmosphereEffects || [] as any[]
})

// 评论区域背景和边框颜色（根据日夜间模式，而非氛围开关）
const commentBackgroundColor = computed(() => {
  // 直接根据日夜间模式判断，不依赖氛围开关
  if (themeStore.isDark) {
    return 'rgba(31, 41, 55, 0.85)' // 深色日间
  } else {
    return 'rgba(255, 255, 255, 0.85)' // 浅色日间
  }
})

// 评论区域边框颜色（根据日夜间模式）
const commentBorderColor = computed(() => {
  return themeStore.isDark ? 'rgba(75, 85, 99, 0.3)' : 'rgba(229, 231, 235, 0.3)'
})

// 输入框边框颜色（根据日夜间模式）
const inputBorderColor = computed(() => {
  return themeStore.isDark ? 'rgb(255 255 255 / 0.3)' : 'rgb(107 114 128 / 0.5)'
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
  return buildPhotoAssetUrl(photo, 'medium') || ''
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
  const entrySource = sessionStorage.getItem('album-entry-source')
  const shouldAnimate = entrySource === 'home' && !fromParam
  
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
    sessionStorage.removeItem('album-entry-source')
    
    // 根据来源决定去向
    const normalizedTargetPage = targetPage ? stripPublicSlug(targetPage) : ''
    if (targetPage && normalizedTargetPage !== '/') {
      // 从其他页面来的，返回该页面
      router.push(buildPublicPath(targetPage, route.path))
    } else {
      // 直接 URL 进入或无来源，返回相册列表
      router.push(buildPublicPath('/', route.path))
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
let transitionViewportSnapshot: { width: number; height: number; offsetTop: number } | null = null

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
    transitionViewportSnapshot = {
      width: window.innerWidth,
      height: window.innerHeight,
      offsetTop: window.visualViewport?.offsetTop || 0
    }
    const coverRects: Array<{ photoId: number; src?: string; rect: { top: number; left: number; width: number; height: number } }> = JSON.parse(storedData)
    
    // 等待 DOM 更新完成
    await nextTick()
    
    // 找到对应的照片元素，并等待瀑布流布局完成
    const transitions: Array<{
      photoId: number
      fromRect: DOMRect
      toRect: DOMRect
      img: HTMLImageElement
      sourceSrc: string
    }> = []
    
    for (const { photoId, rect: fromRectData, src } of coverRects) {
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
        img,
        sourceSrc: src || img.currentSrc || img.src
      })
    }

    // Android Chrome 首次进入时，字体/异步内容可能在首帧后继续触发一次
    // 页面重排。等待所有目标卡片连续 3 帧位置与尺寸稳定，再启动 FLIP，
    // 避免动画先到偏上的旧终点、随后页面整体跳下。
    for (let stableFrame = 0; stableFrame < 8; stableFrame++) {
      if (isDisposed || (transitionViewportSnapshot && (
        transitionViewportSnapshot.width !== window.innerWidth ||
        transitionViewportSnapshot.height !== window.innerHeight ||
        transitionViewportSnapshot.offsetTop !== (window.visualViewport?.offsetTop || 0)
      ))) {
        transitionViewportSnapshot = null
        return false
      }
      const before = transitions.map(({ photoId, toRect }) => `${photoId}:${toRect.top.toFixed(2)},${toRect.left.toFixed(2)},${toRect.width.toFixed(2)},${toRect.height.toFixed(2)}`)
      await new Promise(resolve => requestAnimationFrame(resolve))
      let changed = false
      transitions.forEach((transition) => {
        const element = photoRefs.value.get(transition.photoId)
        if (!element) return
        const nextRect = element.getBoundingClientRect()
        const nextKey = `${transition.photoId}:${nextRect.top.toFixed(2)},${nextRect.left.toFixed(2)},${nextRect.width.toFixed(2)},${nextRect.height.toFixed(2)}`
        const index = transitions.indexOf(transition)
        if (before[index] !== nextKey) {
          changed = true
          transition.toRect = nextRect
        }
      })
      if (!changed && stableFrame >= 2) break
      if (!changed) stableFrame++
      else stableFrame = 0
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

    for (const { photoId, fromRect, toRect, sourceSrc } of transitions) {
      // 直接使用首页已经显示的缩略图，不等待详情页图片加载。
      const clone = new Image()
      clone.src = sourceSrc
      clone.dataset.photoId = String(photoId)
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
        const revealPhoto = (photoId: number) => {
          const photoElement = photoRefs.value.get(photoId)
          if (photoElement) {
            photoElement.style.visibility = 'visible'
            photoElement.style.pointerEvents = ''
          }
          const clone = transitionClones.find(item => item.dataset.photoId === String(photoId))
          clone?.remove()
        }

        // 大图已就绪就立即交接；未就绪则保留缩略图占位，待大图 load 后再交接。
        transitions.forEach(({ photoId, img }) => {
          if (img.complete && img.naturalWidth > 0) {
            revealPhoto(photoId)
          } else {
            img.addEventListener('load', () => revealPhoto(photoId), { once: true })
          }
        })

        // 在同一帧中立即移除克隆元素
        requestAnimationFrame(() => {
          transitionClones = transitionClones.filter(clone => clone.isConnected)

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
    transitionViewportSnapshot = null
    
    return true
  } catch (error) {
    console.error('执行封面过渡动画失败:', error)
    transitionViewportSnapshot = null
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
    router.push(buildPublicPath('/', route.path))
    return
  }

  const storageKey = `album-cover-rects-${targetAlbumId}`
  const storedData = sessionStorage.getItem(storageKey)
  const isFromDirectUrl = sessionStorage.getItem('album-entry-source') !== 'home'

  console.log('[返回动画] 启动，返回方式:', isFromDirectUrl ? '直接URL进入' : '正常导航')

  // 如果没有封面位置信息，直接跳转
  if (!storedData || photos.value.length === 0) {
    console.log('[返回动画] 无封面数据，直接跳转')
    sessionStorage.removeItem('album-back-transition')
    sessionStorage.removeItem('album-animation-performed')
    sessionStorage.removeItem('album-navigation-active')
    sessionStorage.removeItem('album-entry-page')
    sessionStorage.removeItem('album-entry-source')
    
    // 根据来源决定去向
    const entryPage = sessionStorage.getItem('album-entry-page')
    if (entryPage && stripPublicSlug(entryPage) !== '/') {
      router.push(buildPublicPath(entryPage, route.path))
    } else {
      router.push(buildPublicPath('/', route.path))
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
        if (entryPage && stripPublicSlug(entryPage) !== '/') {
          router.push(buildPublicPath(entryPage, route.path))
        } else {
          router.push(buildPublicPath('/', route.path))
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
      sessionStorage.removeItem('album-entry-source')

      // session storage set

      // 正常从首页进入时使用历史回退，交给浏览器/KeepAlive 保留首页
      // 原始滚动位置；只有没有可回退历史时才使用首页路径兜底。
      console.log('[返回动画] 导航到 Home，使用历史回退保留原位置')
      if (window.history.length > 1) router.back()
      else router.push(buildPublicPath('/', route.path))

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
    sessionStorage.removeItem('album-entry-source')
    
    const entryPage = sessionStorage.getItem('album-entry-page')
    if (entryPage && stripPublicSlug(entryPage) !== '/') {
      router.push(buildPublicPath(entryPage, route.path))
    } else {
      router.push(buildPublicPath('/', route.path))
    }
  }
}

// 加载相册数据的主要函数（onMounted 和 onActivated 都会调用）
const loadAlbumData = async () => {
  // 用户可能在首屏请求尚未完成时按 ESC 离开，组件卸载后不再继续处理旧请求。
  if (isDisposed) return

  // 解析相册 ID（支持 ID 或名称）
  const targetAlbumId = await resolveAlbumId()
  if (isDisposed) return
  if (!targetAlbumId) {
    console.error('[AlbumDetail] 无效的相册 ID')
    router.push(buildPublicPath('/', route.path))
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

  // 读取预存的氛围背景色（从相册列表页传递，用于避免页面跳转时的闪烁）
  const storedBgColor = sessionStorage.getItem(`album-atmosphere-bg-${targetAlbumId}`)
  if (storedBgColor && atmosphereEnabled.value) {
    presetAtmosphereBg.value = storedBgColor
    console.log('[AlbumDetail] 已读取预存氛围背景色:', storedBgColor)
  }

  await photoStore.fetchAlbumById(targetAlbumId)
  if (isDisposed) return

  const album = photoStore.currentAlbum

  // 首屏照片与人物栏并行请求。人物栏会影响瀑布流起始位置，需在测量动画目标前完成；
  // 评论数不影响布局，延后到动画开始后再后台加载。
  const initialLoadSize = 50
  // 详情首屏动画只依赖照片元数据；人物栏并行加载但不阻塞封面过渡。
  void loadAlbumPersons(targetAlbumId).catch(() => undefined)
  const result = await photoStore.fetchPhotosByAlbum(targetAlbumId, 0, initialLoadSize)
  if (isDisposed) return
  hasMore.value = !result.last

  totalImages.value = photos.value.length

  isInitialLoading.value = false

  window.addEventListener('scroll', handleScroll, { passive: true })

  resetImageLoading()

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

  // 只等待布局完成即可，不等待任何图片资源下载。
  // MasonryLayout 根据照片宽高元数据计算位置，目标卡片出现后就可以测量 FLIP 终点。
  await new Promise(resolve => requestAnimationFrame(resolve))

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

  // 人物栏可能改变瀑布流整体起点，统一在封面动画结束后再显示。
  // 数据即使已经返回，也不会在过渡过程中造成页面跳动。
  if (albumPersonsRevealTimer) window.clearTimeout(albumPersonsRevealTimer)
  albumPersonsRevealTimer = window.setTimeout(() => {
    if (!isDisposed) showAlbumPersons.value = true
  }, hasCoverTransition ? 430 : 120)

  // 评论数不参与首屏布局，动画启动后再补齐，避免阻塞封面展开。
  loadCommentCount(targetAlbumId).catch(() => undefined)

  // 动画完成后清理上一个相册的动画缓存
  await nextTick()
  sessionStorage.removeItem('album-back-transition')
}

onMounted(async () => {
  // 必须在任何异步请求前同步归零。Android Chrome 进入详情页时可能暂时
  // 沿用首页的 scrollTop；若等请求完成后再归零，首次布局/FLIP 会带着
  // 首页滚动偏移计算，随后归零时就会出现整体跳动。
  history.scrollRestoration = 'manual'
  window.scrollTo(0, 0)
  if (document.scrollingElement) document.scrollingElement.scrollTop = 0
  document.documentElement.scrollTop = 0
  document.body.scrollTop = 0

  // 从组件挂载开始就监听 ESC，避免网络请求/图片加载期间无法退出详情页。
  // addEventListener 对同一函数引用是幂等的，后续流程无需重复注册。
  window.addEventListener('keydown', handleKeydown)

  // 获取全局下载权限设置
  try {
    const response = await api.get('/admin/config/global-download-allowed')
    if (isDisposed) return
    globalDownloadAllowed.value = response.data.globalDownloadAllowed !== false
  } catch (error) {
    console.warn('获取全局下载权限设置失败:', error)
    globalDownloadAllowed.value = false
  }

  // 添加窗口大小监听（实时响应）
  if (isDisposed) return
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
    if (document.scrollingElement) document.scrollingElement.scrollTop = 0
    document.documentElement.scrollTop = 0
    document.body.scrollTop = 0
    console.log('[AlbumDetail] onActivated - 数据已存在，使用缓存')
  }
})

onUnmounted(() => {
  isDisposed = true

  // 清理滚动节流定时器，防止组件卸载后定时器回调仍执行
  if (scrollThrottleTimer) {
    clearTimeout(scrollThrottleTimer)
    scrollThrottleTimer = null
  }

  // 离开详情页（包括按 ESC / 浏览器返回）时清空，避免下次进入时闪现旧信息
  photoStore.currentAlbum = null
  photoStore.photos = []
  commentCount.value = 0
  albumPersons.value = []
  albumPersonsLoading.value = false
  showAlbumPersons.value = false
  albumPersonsHeight.value = 0
  imagesLoaded.value = false
  loadedImagesCount.value = 0
  totalImages.value = 0
  isInitialLoading.value = true
  showComments.value = false

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
  if (albumPersonsRevealTimer) {
    window.clearTimeout(albumPersonsRevealTimer)
    albumPersonsRevealTimer = null
  }
})

// 获取相册评论数量
const loadCommentCount = async (id: number) => {
  try {
    const response = await commentApi.getAlbumCommentCount(id)
    if (isDisposed || albumId.value !== id) return
    commentCount.value = response.data
  } catch (error) {
    console.error('Failed to load comment count:', error)
    commentCount.value = 0
  }
}

// 获取相册中的人物列表
const loadAlbumPersons = async (id: number) => {
  try {
    albumPersonsLoading.value = true
    const response = await personApi.getAlbumPersons(id)
    if (isDisposed || albumId.value !== id) return
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

// 相册拍摄时间使用紧凑日期显示，放在照片数量之后。
const formatAlbumTakenAt = (dateStr: string) => {
  const date = new Date(dateStr)
  if (Number.isNaN(date.getTime())) return dateStr
  return date.toLocaleDateString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit'
  })
}



// 处理图片加载完成事件
const handleImageLoaded = () => {
  // 图片加载完成后可能需要重新计算布局
  loadedImagesCount.value++
  console.log(`[CommentSection] 图片加载完成: ${loadedImagesCount.value}/${totalImages.value}`)

  // 使用更宽松的判断：当至少 80% 的图片加载完成时，或者超过 3 秒后，自动标记为已加载
  // 这样可以避免 lazy loading 导致永远无法达到 100% 的问题
  const loadedRatio = totalImages.value > 0 ? loadedImagesCount.value / totalImages.value : 0
  console.log(`[CommentSection] loadedRatio: ${loadedRatio}, threshold: ${Math.min(20, totalImages.value)}`)
  if (loadedRatio >= 0.8 || loadedImagesCount.value >= Math.min(20, totalImages.value)) {
    console.log('[CommentSection] 设置 imagesLoaded = true')
    imagesLoaded.value = true
  }
}

// 重置图片加载状态
const resetImageLoading = () => {
  loadedImagesCount.value = 0
  imagesLoaded.value = false
  // 注意：showComments 由 watch 统一控制，不在这里重置
}

const getBrightness = (hex: string) => {
  const rgb = hexToRgb(hex)
  if (!rgb) return 0.5

  // 使用相对亮度公式
  return (rgb.r * 0.299 + rgb.g * 0.587 + rgb.b * 0.114) / 255
}
</script>
