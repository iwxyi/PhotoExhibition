<template>
  <div class="min-h-screen bg-white dark:bg-gray-900">
    <nav
      class="sticky top-0 z-50 bg-white/80 dark:bg-gray-900/80 backdrop-blur-md border-b border-gray-200 dark:border-gray-800 safe-area-inset-top transition-transform duration-300 ease-in-out transform-gpu"
      :class="{ '-translate-y-full': isMobile && navHidden }"
      style="padding-top: env(safe-area-inset-top);"
    >
      <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div class="flex justify-between items-center h-12">
          <AppHeader :show-nav-links="!isMobile" />
          <div class="flex items-center space-x-4">
            <SearchSpotlight />
            <FilterPanel ref="filterPanelRef" v-model:show="showFilter" :categories="categories" :initial-filters="urlFilters" @reset="handleFilterReset" @update:selectedTags="updateSelectedTags" @filters-applied="handleFiltersApplied" />
            <PublicAccountMenu />
          </div>
        </div>
      </div>
    </nav>

    <main class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 pt-6 pb-12">

      <div v-if="activeTagId" class="mb-6 flex items-center gap-3 text-sm text-gray-600 dark:text-gray-300">
        <span class="px-3 py-1 rounded-full bg-blue-100 text-blue-700 dark:bg-blue-900/50 dark:text-blue-200">
          标签：{{ activeTagName || ('#' + activeTagId) }}
        </span>
        <button class="text-blue-500 hover:underline" @click="clearTag">清除标签过滤</button>
      </div>
      <div v-if="currentFilters" class="mb-6 flex items-center justify-between gap-3 text-sm text-gray-600 dark:text-gray-300">
        <div class="px-3 py-1 rounded bg-gray-100/60 dark:bg-gray-800/60 cursor-pointer hover:bg-gray-200/60 dark:hover:bg-gray-700/60 transition-colors" @click="showFilter = true">
          筛选：{{ filterSummary }}
        </div>
        <button class="text-red-500 hover:text-red-600 text-lg font-bold leading-none" @click="clearFilters">×</button>
      </div>
      <div ref="masonryContainer" class="masonry-container">
        <div
          v-for="(photo, idx) in photos"
          :key="`photo-${photo.id}-${photo.filename}-${idx}`"
          :ref="el => setItemRef(el, idx)"
          class="masonry-item photo-card cursor-pointer"
          :style="getItemStyle(idx)"
          :data-photo-id="photo.id"
          @click="openViewer(idx, $event)"
        >
          <div class="masonry-image-wrapper rounded-lg overflow-hidden relative">
            <img
              :src="getImageUrl(photo)"
              :alt="photo.filename"
              class="masonry-photo-image"
              :style="getImageStyle(idx)"
              loading="lazy"
              @load="onImageLoad(idx)"
              @error="onImageError"
            />
          </div>
          <!-- 曝光参数悬浮层 -->
          <div v-if="photo.focalLength || photo.aperture || photo.iso || photo.shutterSpeed" class="photo-info-overlay">
            <div class="params-row">
              <span v-if="photo.focalLength" class="param">{{ photo.focalLength }}mm</span>
              <span v-if="photo.aperture" class="param">f/{{ photo.aperture }}</span>
              <span v-if="photo.shutterSpeed" class="param">{{ photo.shutterSpeed }}</span>
              <span v-if="photo.iso" class="param">ISO {{ photo.iso }}</span>
            </div>
          </div>
          <!-- 点赞覆盖层 -->
          <div
            class="like-overlay"
            :class="{ 'visible': (likedIds.has(photo.id) || (likesMap.get(photo.id) || 0) > 0) }"
            @click.stop="likePhoto(photo.id, $event)"
            title="点赞"
          >
            <!-- 可扩展的按钮容器：heart + count -->
            <div :class="['like-btn', { liked: likedIds.has(photo.id) }]">
              <svg :class="['heart', { liked: likedIds.has(photo.id) }]" viewBox="0 0 24 24" width="18" height="18" xmlns="http://www.w3.org/2000/svg" aria-hidden="true" focusable="false">
                <path
                  class="heart-path"
                  stroke="currentColor"
                  stroke-width="1.5"
                  d="M12 20.35l-2.25-2.25c-2.75-2.75-5.5-5.5-5.5-8.5c0-2.5 2-4.5 4.5-4.5c1.25 0 2.5 .625 3.5 1.75c1-.875 2.25-1.75 3.5-1.75c2.5 0 4.5 2 4.5 4.5c0 3-2.75 5.75-5.5 8.5L12 20.35z"
                />
              </svg>
              <span v-if="(likesMap.get(photo.id) || 0) > 0" class="like-count">{{ likesMap.get(photo.id) }}</span>
            </div>
            <!-- burst container removed (canvas-based burst used) -->
          </div>
        </div>
      </div>

      <!-- 加载状态 -->
      <div v-if="isLoadingMore && photos.length > 0" class="mt-12 text-center">
        <div class="inline-block w-6 h-6 border-2 border-gray-300 border-t-gray-900 dark:border-gray-600 dark:border-t-white rounded-full animate-spin"></div>
      </div>
      <div v-if="!loading && photos.length === 0" class="mt-24 text-center">
        <p class="text-sm text-gray-400">暂无图片</p>
      </div>
      <div v-if="!hasMore && photos.length > 0" class="mt-12 text-center">
        <p class="text-xs text-gray-400 tracking-widest">— 已加载全部 —</p>
      </div>
    </main>
    <PhotoViewer
      v-model:visible="viewerVisible"
      :photos="photos"
      :start-index="viewerIndex"
      :origin-rect="viewerOriginRect"
      :auto-show-faces="false"
    />

    <!-- 移动端底部导航栏 -->
    <MobileBottomNav v-if="isMobile" />
  </div>
</template>

<script setup lang="ts">
defineOptions({ name: 'Wall' })
import { ref, computed, onMounted, onUnmounted, onActivated, onDeactivated, nextTick, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { usePhotoStore } from '@/stores/photo'
import { useThemeStore } from '@/stores/theme'
import NavLinks from '@/components/NavLinks.vue'
import MobileBottomNav from '@/components/MobileBottomNav.vue'
import PhotoViewer from '@/components/PhotoViewer.vue'
import FilterPanel from '@/components/FilterPanel.vue'
import SearchSpotlight from '@/components/SearchSpotlight.vue'
import AppHeader from '@/components/AppHeader.vue'
import PublicAccountMenu from '@/components/PublicAccountMenu.vue'
import { useUiSettings } from '@/composables/useUiSettings'
import { useMobileNav } from '@/composables/useMobileNav'
import { useNavAutoHide } from '@/composables/useNavAutoHide'
import { sortCategories, loadCategorySortOrder } from '@/composables/useCategorySorting'
import { api } from '@/api'
import { buildPublicPath } from '@/utils/publicRoute'
import { buildPhotoAssetUrl } from '@/utils/photoUrl'

const photoStore = usePhotoStore()
const themeStore = useThemeStore()
const route = useRoute()
const router = useRouter()

const photos = computed(() => photoStore.photosWall)
const loading = computed(() => photoStore.loading)
const categories = computed(() => sortCategories(photoStore.categories))
const currentPage = ref(0)
const hasMore = ref(true)
const viewerVisible = ref(false)
const viewerIndex = ref(0)
const showFilter = ref(false)
const viewerOriginRect = ref<{ top: number; left: number; width: number; height: number } | null>(null)
const savedScrollTop = ref(0)
const masonryContainer = ref<HTMLElement | null>(null)
const isLoadingMore = ref(false)
// 标记组件是否已激活（用于区分首次加载和从其他页面返回）
const isActivatedFlag = ref(false)
// 记录上次加载数据时的路由信息，用于判断是否需要重新加载
const lastDataLoadedInfo = ref<{
  query: Record<string, any>;
  fullPath: string;
} | null>(null)
const { previewSize, parallaxEnabled } = useUiSettings()
const { isMobile } = useMobileNav()
const { isHidden: navHidden } = useNavAutoHide()
const windowWidth = ref(typeof window !== 'undefined' ? window.innerWidth : 1920)
const itemRefs = ref<(HTMLElement | null)[]>([])
const columnHeights = ref<number[]>([])
const itemPositions = ref<Array<{ left: number; top: number }>>([])
const parallaxOffsets = ref<number[]>([])
const activeTagId = ref<number | null>(null)
const activeTagName = ref<string | null>(null)
const activePersonId = ref<number | null>(null)
const activePersonName = ref<string | null>(null)
const filterPanelRef = ref()

// 从URL解析的筛选条件
const urlFilters = ref<any>(null)

// 标记是否正在清除筛选（用于防止 watch 重新加载）
const isClearingFilters = ref(false)

// 标记是否正在应用筛选（用于防止 watch 重复触发）
const isApplyingFilters = ref(false)

// 同步筛选面板参数
const syncFilterPanel = async () => {
  if (filterPanelRef.value && urlFilters.value) {
    // 调用FilterPanel的同步方法
    if (filterPanelRef.value.syncFromExternal) {
      await filterPanelRef.value.syncFromExternal(urlFilters.value)
    }
  }
}

// 当前已启用的筛选（来自 store.lastFilters 或 URL 参数）
const currentFilters = computed(() => {
  // 优先使用 store 中的筛选条件
  if (photoStore.lastFilters?.value) {
    return photoStore.lastFilters.value
  }
  // 其次检查 URL 中的筛选参数
  if (urlFilters.value) {
    return urlFilters.value
  }
  return null
})

// 选中的标签列表
const selectedTags = ref<any[]>([])

// 更新选中的标签
const updateSelectedTags = (tags: any[]) => {
  selectedTags.value = tags
}

const filterSummary = computed(() => {
  const f = currentFilters.value
  if (!f) return ''
  const parts: string[] = []
  if (f.tagIds && f.tagIds.length) {
    // 获取标签名称列表 - 从选中的标签中查找
    const tagNames = f.tagIds.map(id => {
      const tag = selectedTags.value.find(t => t.id === id)
      return tag ? tag.name : `ID:${id}`
    })
    parts.push(`标签(${f.tagIds.length})：${tagNames.join('，')}`)
  }
  if (f.cameraModel) parts.push(f.cameraModel)
  if (f.lensModel) parts.push(f.lensModel)
  if (f.colorCategory) {
    // 转换颜色类别为中文显示
    const colorMap: Record<string, string> = {
      'RED': '🔴 红色',
      'ORANGE': '🟠 橙色',
      'YELLOW': '🟡 黄色',
      'GREEN': '🟢 绿色',
      'BLUE': '🔵 蓝色',
      'PURPLE': '🟣 紫色',
      'PINK': '🩷 粉色',
      'BROWN': '🤎 棕色',
      'GRAY': '⚪ 灰色',
      'BLACK': '⚫ 黑色',
      'WHITE': '⚪ 白色'
    }
    parts.push(colorMap[f.colorCategory] || f.colorCategory)
  }
  if (f.category) parts.push(`分类：${f.category}`)
  if (f.minFocalLength != null || f.maxFocalLength != null) parts.push(`焦距 ${f.minFocalLength || '∞'}-${f.maxFocalLength || '∞'}`)
  if (f.minShutterSpeed != null || f.maxShutterSpeed != null) parts.push(`快门 ${formatShutterSpeed(f.minShutterSpeed) || '∞'}-${formatShutterSpeed(f.maxShutterSpeed) || '∞'}`)
  if (f.minAperture != null || f.maxAperture != null) parts.push(`光圈 ${f.minAperture || '∞'}-${f.maxAperture || '∞'}`)
  if (f.minIso != null || f.maxIso != null) parts.push(`ISO ${f.minIso || '∞'}-${f.maxIso || '∞'}`)
  if (f.minQualityScore) parts.push(`评分≥${f.minQualityScore}`)
  // 时间范围
  if (f.startDate || f.endDate) {
    // 如果开始和结束日期相同，只显示一个日期
    if (f.startDate && f.endDate && f.startDate === f.endDate) {
      parts.push(`📅 ${f.startDate}`)
    } else {
      const start = f.startDate ? f.startDate.slice(5) : '开始'
      const end = f.endDate ? f.endDate.slice(5) : '现在'
      parts.push(`📅 ${start} - ${end}`)
    }
  }
  return parts.join(' · ')
})

// 格式化快门速度显示
const formatShutterSpeed = (value: number | null) => {
  if (value === null) return null
  if (value === 0) return '0'

  // 如果快门速度 >= 1秒，显示整数
  if (value >= 1) return Math.round(value).toString()

  // 如果快门速度 < 1秒，转换为分数形式
  const denominator = Math.round(1 / value)

  // 确保分母是合理的范围
  if (denominator < 1 || denominator > 8000) return value.toString()

  return `1/${denominator}`
}

const clearFilters = async () => {
  // 标记正在清除筛选，防止重复触发
  isClearingFilters.value = true

  // 先滚动到页面顶部（隐藏顶部筛选信息栏后不会导致布局抖动）
  window.scrollTo({ top: 0, behavior: 'instant' })

  // 然后清除数据（保持当前数据可见，直到新数据加载完成）
  selectedTags.value = [] // 重置选中的标签
  urlFilters.value = null // 重置内部的 URL filters 状态

  // 清除 URL 中的筛选参数
  const { filters: _f, ...restQuery } = route.query
  await router.replace({
    path: route.path,
    query: restQuery
  })

  // 清除 store 中的筛选状态
  photoStore.clearLastFilters()

  // 加载所有照片
  await loadPhotosWithoutClear()

  // 清除标志
  isClearingFilters.value = false
}



const handleFilterReset = async () => {
  // 标记正在清除筛选，防止重复触发
  isClearingFilters.value = true

  // 先滚动到页面顶部
  window.scrollTo({ top: 0, behavior: 'instant' })

  // 然后清除数据
  selectedTags.value = [] // 重置选中的标签
  urlFilters.value = null // 重置内部的 URL filters 状态

  // 清除 URL 中的筛选参数
  const { filters: _f, ...restQuery } = route.query
  await router.replace({
    path: route.path,
    query: restQuery
  })

  // 清除 store 中的筛选状态
  photoStore.clearLastFilters()

  // 加载所有照片
  await loadPhotosWithoutClear()

  // 清除标志
  isClearingFilters.value = false
}

const handleFiltersApplied = (filters: any) => {
  // 标记正在应用筛选，防止 watch 重复触发
  isApplyingFilters.value = true

  // 重置分页状态，让新的筛选可以重新加载更多
  currentPage.value = 0
  hasMore.value = true

  // 更新 URL 中的筛选参数
  if (filters && hasEffectiveFilters(filters)) {
    router.replace({
      query: {
        ...route.query,
        filters: JSON.stringify(filters)
      }
    })
  } else {
    // 清除筛选参数
    const { filters: _, ...restQuery } = route.query
    router.replace({
      query: restQuery
    })
  }

  // 延迟清除标志，确保 watch 已经处理完
  setTimeout(() => {
    isApplyingFilters.value = false
  }, 100)
}

// 检查筛选条件是否有实际限制（非默认值）
const hasEffectiveFilters = (filterData: any) => {
  return (
    (filterData.tagIds && filterData.tagIds.length > 0) ||
    (filterData.cameraModel && filterData.cameraModel.trim() !== '') ||
    (filterData.lensModel && filterData.lensModel.trim() !== '') ||
    (filterData.colorCategory && filterData.colorCategory.trim() !== '') ||
    (filterData.category && filterData.category.trim() !== '') ||
    (filterData.minQualityScore && filterData.minQualityScore > 0) ||
    (filterData.minFocalLength !== null && filterData.minFocalLength !== undefined) ||
    (filterData.maxFocalLength !== null && filterData.maxFocalLength !== undefined) ||
    (filterData.minShutterSpeed !== null && filterData.minShutterSpeed !== undefined) ||
    (filterData.maxShutterSpeed !== null && filterData.maxShutterSpeed !== undefined) ||
    (filterData.minAperture !== null && filterData.minAperture !== undefined) ||
    (filterData.maxAperture !== null && filterData.maxAperture !== undefined) ||
    (filterData.minIso !== null && filterData.minIso !== undefined) ||
    (filterData.maxIso !== null && filterData.maxIso !== undefined) ||
    (filterData.startDate && filterData.startDate.trim() !== '') ||
    (filterData.endDate && filterData.endDate.trim() !== '')
  )
}

// 点赞相关（匿名点赞，使用 localStorage 保存用户是否已点赞）
const likedIds = ref<Set<number>>(new Set())
const likesMap = ref<Map<number, number>>(new Map())

// 调试用的computed
const debugLikesMap = computed(() => {
  const result: Record<number, number> = {}
  likesMap.value.forEach((value, key) => {
    result[key] = value
  })
  return result
})

// 根据预览尺寸计算列数（与RandomGallery和AlbumDetail保持一致）
const columnCount = computed(() => {
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
    else if (width < 1280) count = 4
    else count = 4
  }
  console.log('计算列数: width=', width, 'previewSize=', previewSize.value, 'count=', count)
  return count
})

// 计算每列的宽度 - 与layoutItems保持一致
const columnWidth = computed(() => {
  if (!masonryContainer.value) return 0
  const containerWidth = masonryContainer.value.clientWidth
  const gap = 10  // 减小间距，与其他页面一致
  const cols = columnCount.value
  if (cols <= 0) return 0
  const totalGapWidth = (cols - 1) * gap
  const availableWidth = containerWidth - totalGapWidth
  return Math.floor(availableWidth / cols)
})

// 设置 item ref
const setItemRef = (el: any, idx: number) => {
  if (el) {
    itemRefs.value[idx] = el
  }
}

// 获取 item 样式 - 根据图片宽高比自适应高度
const getItemStyle = (idx: number) => {
  const pos = itemPositions.value[idx]
  if (!pos) return { visibility: 'hidden' }

  const cols = columnCount.value
  const gap = 10  // 减小间距，与其他页面一致
  const containerWidth = masonryContainer.value?.clientWidth || window.innerWidth
  const totalGapWidth = (cols - 1) * gap
  const availableWidth = containerWidth - totalGapWidth
  const colWidth = Math.floor(availableWidth / cols)

  // 获取图片数据
  const photo = photos.value[idx]
  let height = colWidth // 默认正方形

  if (photo) {
    // 根据图片宽高比计算高度
    const aspectRatio = photo.width / photo.height
    height = colWidth / aspectRatio

    // 限制高度范围
    const minHeight = colWidth * 0.3
    const maxHeight = colWidth * 4
    height = Math.max(minHeight, Math.min(maxHeight, height))
  }

  return {
    position: 'absolute',
    left: `${pos.left}px`,
    top: `${pos.top}px`,
    width: `${colWidth}px`,
    height: `${height}px`,
    visibility: 'visible'
  }
}

// 获取图片样式（包含视差偏移和放大）
const getImageStyle = (idx: number) => {
  const parallaxOffset = parallaxEnabled.value ? (parallaxOffsets.value[idx] || 0) : 0

  // 基础放大比例：填充容器
  const baseScale = 1.15

  // 额外缩放：补偿视差偏移造成的白边
  // 最大偏移约为图片高度的 20%，需要额外的缩放来填充
  const compensationScale = parallaxEnabled.value ? 0.08 : 0

  const scale = baseScale + compensationScale

  return {
    // offset 为正时，translateY 为负，图片向上移动，显示顶部内容
    transform: `translateY(${-parallaxOffset}px) scale(${scale})`,
    transformOrigin: 'center center'
  }
}

// 点赞相关函数
const loadLikedFromStorage = () => {
  try {
    const raw = localStorage.getItem('likedPhotos')
    if (raw) {
      const arr = JSON.parse(raw)
      likedIds.value = new Set(arr)
    }
  } catch (e) {
    likedIds.value = new Set()
  }
}

const saveLikedToStorage = () => {
  try {
    localStorage.setItem('likedPhotos', JSON.stringify(Array.from(likedIds.value)))
  } catch (e) {
    // ignore
  }
}

const likePhoto = async (photoId: number, ev?: Event) => {
  if (likedIds.value.has(photoId)) {
    // unlike
    try {
      const res = await api.delete(`/photos/${photoId}/like`)
      const newCount = res.data
      likesMap.value.set(photoId, newCount)
      likedIds.value.delete(photoId)
      saveLikedToStorage()
    } catch (e) {
      console.error('unlike failed', e)
    }
  } else {
    // like
    try {
      const res = await api.post(`/photos/${photoId}/like`)
      const newCount = res.data
      likesMap.value.set(photoId, newCount)
      likedIds.value.add(photoId)
      saveLikedToStorage()
      // show burst animation for this photo (canvas) at click coordinates if available
      try {
        const x = ev && (ev as MouseEvent).clientX
        const y = ev && (ev as MouseEvent).clientY
        triggerCanvasBurstFor(photoId, x as number | undefined, y as number | undefined)
      } catch (e) {
        // ignore
      }
      // visual pop on the clicked button
      try {
        const target = ev && (ev.target as HTMLElement)
        const btn = target?.closest?.('.like-btn') as HTMLElement | null
        let cx: number | undefined
        let cy: number | undefined
        if (btn) {
          // prefer heart center
          const heart = btn.querySelector<HTMLElement>('.heart')
          const rect = (heart || btn).getBoundingClientRect()
          cx = rect.left + rect.width / 2
          cy = rect.top + rect.height / 2
          btn.classList.add('pop')
          setTimeout(() => btn.classList.remove('pop'), 420)
        }
        // trigger canvas burst at exact heart center if available
        if (typeof cx === 'number' && typeof cy === 'number') {
          triggerCanvasBurstFor(photoId, cx, cy)
        }
      } catch (e) {
        // ignore
      }
    } catch (e) {
      console.error('like failed', e)
    }
  }
}

// --------------------------
let canvasEl: HTMLCanvasElement | null = null
let ctx: CanvasRenderingContext2D | null = null
let particles: Array<any> = []
let rafId: number | null = null

const ensureCanvas = () => {
  if (canvasEl && ctx) return
  canvasEl = document.createElement('canvas')
  canvasEl.style.position = 'fixed'
  canvasEl.style.left = '0'
  canvasEl.style.top = '0'
  canvasEl.style.width = '100%'
  canvasEl.style.height = '100%'
  canvasEl.style.pointerEvents = 'none'
  canvasEl.style.zIndex = '2147483646'
  canvasEl.width = window.innerWidth
  canvasEl.height = window.innerHeight
  document.body.appendChild(canvasEl)
  ctx = canvasEl.getContext('2d')
  window.addEventListener('resize', () => {
    if (!canvasEl) return
    canvasEl.width = window.innerWidth
    canvasEl.height = window.innerHeight
  })
  startLoop()
}

const startLoop = () => {
  if (rafId) return
  const loop = (t: number) => {
    if (!ctx || !canvasEl) return
    ctx.clearRect(0, 0, canvasEl.width, canvasEl.height)
    const now = performance.now()
    particles = particles.filter(p => {
      const dt = (now - p.t0) / p.life
      if (dt >= 1) return false

      // 前半生命周期：向上发射，后半生命周期：重力下坠
      const gravityStart = 0.4 // 生命周期40%后开始重力
      const gravityFactor = dt > gravityStart ? (dt - gravityStart) / (1 - gravityStart) : 0

      const x = p.x + p.vx * dt
      const y = p.y + p.vy * dt + 0.5 * p.gravity * gravityFactor * gravityFactor

      const alpha = dt < 0.3 ? 1 : 1 - ((dt - 0.3) / 0.7) // 前30%完全不透明，后70%渐隐
      ctx.globalAlpha = alpha
      ctx.fillStyle = p.color
      ctx.beginPath()
      ctx.arc(x, y, p.size * (1 - dt) + 0.5, 0, Math.PI * 2)
      ctx.fill()
      return true
    })
    rafId = requestAnimationFrame(loop)
  }
  rafId = requestAnimationFrame(loop)
}

const spawnCanvasBurst = (x: number, y: number) => {
  ensureCanvas()
  const count = 12
  // main colorful particles - upward burst only
  for (let i = 0; i < count; i++) {
    // distribute particles upward (-π/2 ± π/6, i.e., -90° ± 30°)
    const angleRange = Math.PI / 6 * 2 // 60° total range around upward
    const baseAngle = -Math.PI / 2 + (angleRange / (count - 1)) * i - angleRange / 2 // center around -90°
    const angle = baseAngle + (Math.random() - 0.5) * 0.3 // small random variation
    const speed = 70 + Math.random() * 90
    particles.push({
      x, y,
      vx: Math.cos(angle) * speed,
      vy: Math.sin(angle) * speed,
      gravity: 60 + Math.random() * 80,
      size: 2 + Math.random() * 2,
      color: `hsl(${Math.random() * 360}, 95%, ${50 + Math.random() * 20}%)`,
      t0: performance.now(),
      life: 900 + Math.random() * 400
    })
  }
  // small sparkles - upward burst only
  const sparks = 6
  for (let i = 0; i < sparks; i++) {
    // random angle upward (-π/2 ± π/12, i.e., -90° ± 15°)
    const angle = -Math.PI / 2 + (Math.random() - 0.5) * (Math.PI / 12 * 2)
    const speed = 30 + Math.random() * 45
    particles.push({
      x, y,
      vx: Math.cos(angle) * speed,
      vy: Math.sin(angle) * speed,
      gravity: 20 + Math.random() * 30,
      size: 0.8 + Math.random() * 1.2,
      color: `rgba(255,255,255,${0.8 + Math.random() * 0.2})`,
      t0: performance.now(),
      life: 700 + Math.random() * 300
    })
  }
}

const triggerCanvasBurstFor = (photoId: number, clientX?: number, clientY?: number) => {
  // prefer explicit click coordinates (clientX, clientY)
  if (typeof clientX === 'number' && typeof clientY === 'number') {
    spawnCanvasBurst(clientX, clientY)
    return
  }
  // fallback: find DOM element rendered for this photo (data-photo-id attribute) and use center
  const el = document.querySelector(`[data-photo-id='${photoId}']`) as HTMLElement | null
  let x = window.innerWidth / 2
  let y = window.innerHeight / 2
  if (el) {
    const r = el.getBoundingClientRect()
    x = r.left + r.width / 2
    y = r.top + r.height / 2
  }
  spawnCanvasBurst(x, y)
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

// 监听 photos 变化（数量和内容）
watch(() => photos.value, (newPhotos, oldPhotos) => {
  // 检查是否是数据内容变化（不仅仅是数量）
  if (newPhotos.length !== oldPhotos.length ||
      newPhotos.some((photo, index) => photo?.id !== oldPhotos[index]?.id)) {
    // 数据顺序发生变化时，清除DOM引用以强制重新布局
    itemRefs.value = []
    nextTick(() => {
      layoutItems()
    })
  }
}, { deep: true })

// 监听列数变化
watch(columnCount, () => {
  nextTick(() => {
    layoutItems()
  })
})

// 初始化 likesMap
watch(() => photos.value, (photos) => {
  photos.forEach(photo => {
    const pid = photo?.id
    if (pid != null) {
      const likeCount = photo?.likeCount || 0
      likesMap.value.set(pid, likeCount)
    }
  })
}, { immediate: true, deep: true })

// 监听视差滚动开关变化
watch(parallaxEnabled, () => {
  nextTick(() => {
    updateParallax()
  })
})

const getImageUrl = (photo: any) => {
  return buildPhotoAssetUrl(photo, 'medium') || ''
}

const openViewer = (idx: number, e: MouseEvent) => {
  viewerIndex.value = idx

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

const onImageLoad = (idx: number) => {
  // 图片加载完成后重新布局
  // 使用防抖，避免频繁重新布局
  if (layoutTimer) clearTimeout(layoutTimer)
  layoutTimer = setTimeout(() => {
    nextTick(() => {
      layoutItems()
      // 布局后更新视差
      updateParallax()
    })
  }, 50)
}

let layoutTimer: ReturnType<typeof setTimeout> | null = null

// 瀑布流布局函数
// 真正的瀑布流布局 - 根据图片宽高比自适应高度
const layoutItems = () => {
  if (!masonryContainer.value || photos.value.length === 0) return

  const cols = columnCount.value
  if (cols <= 0) return

  const gap = 10  // 减小间距，与其他页面一致
  const container = masonryContainer.value
  const containerWidth = container.clientWidth

  if (containerWidth <= 0) {
    setTimeout(layoutItems, 100)
    return
  }

  // 计算每列的精确宽度
  const totalGapWidth = (cols - 1) * gap
  const availableWidth = containerWidth - totalGapWidth
  const colWidth = Math.floor(availableWidth / cols)

  if (colWidth <= 0) return

  // 初始化列高度追踪
  const colHeights = new Array(cols).fill(0)
  const positions = []

  // 为每个项目分配到最短的列，并根据宽高比计算高度
  itemRefs.value.forEach((itemEl, idx) => {
    if (!itemEl) return

    const photo = photos.value[idx]
    if (!photo) return

    // 找到当前最短的列
    let shortestCol = 0
    let minHeight = colHeights[0]
    for (let i = 1; i < cols; i++) {
      if (colHeights[i] < minHeight) {
        minHeight = colHeights[i]
        shortestCol = i
      }
    }

    // 计算位置
    const left = shortestCol * (colWidth + gap)
    const top = minHeight

    positions[idx] = { left, top }

    // 根据图片宽高比计算实际高度
    const aspectRatio = photo.width / photo.height
    const itemHeight = colWidth / aspectRatio

    // 限制高度范围，避免极端情况
    const minItemHeight = colWidth * 0.3
    const maxItemHeight = colWidth * 4
    const clampedHeight = Math.max(minItemHeight, Math.min(maxItemHeight, itemHeight))

    // 更新列高度
    colHeights[shortestCol] = top + clampedHeight + gap
  })

  // 更新响应式数据
  itemPositions.value = positions
  columnHeights.value = colHeights

  // 设置容器总高度
  const totalHeight = Math.max(...colHeights)
  container.style.height = `${totalHeight}px`
  
  // 设置容器高度
  const maxHeight = Math.max(...columnHeights.value)
  if (maxHeight > 0) {
    masonryContainer.value.style.height = `${maxHeight}px`
  }
  
  // 布局完成后更新视差
  nextTick(() => {
    updateParallax()
  })
}

const onImageError = (e: Event) => {
  // 图片加载失败时的处理
  const img = e.target as HTMLImageElement
  img.style.display = 'none'
}

const loadMore = async () => {
  // 防止重复加载
  if (loading.value || isLoadingMore.value || !hasMore.value) return

  try {
    isLoadingMore.value = true
    // 如果筛选请求正在进行，先不触发加载更多（避免竞态）
    if (photoStore.isFiltersLoading && photoStore.isFiltersLoading()) {
      isLoadingMore.value = false
      return
    }
    // 如果存在活动筛选且已被标记为耗尽，直接停止并不增加页码
    if (photoStore.lastFiltersExhausted && photoStore.lastFiltersExhausted.value) {
      hasMore.value = false
      return
    }
    currentPage.value++

    let data: any
    // 使用 store 的同步 helper判断是否处于活动筛选
    if (photoStore.hasActiveFilters && photoStore.hasActiveFilters()) {
      // 只有当 lastFilters.value 可用时才继续分页请求；否则避免传 undefined 导致覆盖原有 filters
      const filtersObj = photoStore.lastFilters?.value
      if (!filtersObj) {
        // 保持当前页码不变（因为尚未成功加载新页），并结束加载
        currentPage.value--
        hasMore.value = false
        return
      }
      data = await photoStore.filterPhotos(filtersObj, currentPage.value)
    } else if (activePersonId.value) {
      data = await photoStore.fetchPhotosByPerson(activePersonId.value, currentPage.value)
    } else if (activeTagId.value) {
      data = await photoStore.fetchPhotosByTag(activeTagId.value, currentPage.value)
    } else {
      // 确保 currentView 设置为 'wall'，防止 store 检查返回空数据
      photoStore.setCurrentView('wall')
      data = await photoStore.fetchPhotoWall(currentPage.value)
    }

    if (!data || !data.content || data.content.length === 0) {
      // 回退页码（请求未返回数据）
      currentPage.value--
      hasMore.value = false
      return
    }

    console.log('[PhotoWall] 后端返回数据:', { last: data.last, contentLength: data.content?.length, totalPages: data.totalPages, currentPage: currentPage.value })
    hasMore.value = !data.last

    // 新内容自然添加到页面底部，滚动位置自然延续，无需手动恢复
  } catch (error) {
    console.error('加载更多失败:', error)
    // 加载失败时回退页码
    currentPage.value--
    hasMore.value = false
  } finally {
    isLoadingMore.value = false
  }
}

// 计算视差偏移
const updateParallax = () => {
  if (!masonryContainer.value || itemPositions.value.length === 0 || !parallaxEnabled.value) {
    // 如果视差滚动被禁用，清空所有偏移
    parallaxOffsets.value = new Array(itemPositions.value.length).fill(0)
    return
  }

  const scrollTop = window.scrollY || document.documentElement.scrollTop
  const windowHeight = window.innerHeight
  const containerRect = masonryContainer.value.getBoundingClientRect()
  const containerTop = containerRect.top + scrollTop
  const viewportTop = scrollTop
  const viewportBottom = scrollTop + windowHeight

  // 视差强度：图片在视口内滑动时，总偏移量约为图片高度的25%
  const maxOffsetRatio = 0.25

  parallaxOffsets.value = itemPositions.value.map((pos, idx) => {
    if (!itemRefs.value[idx]) return 0

    // 计算图片相对于容器的位置
    const itemHeight = itemRefs.value[idx]?.offsetHeight || 200
    const itemTop = containerTop + pos.top

    // 视差范围：从 itemTop = viewportBottom + extraRange 到 itemTop = viewportTop - extraRange
    // 这样图片在整个视差范围内，translateY 从 +maxOffset 过渡到 -maxOffset
    // 效果：从底部进入时显示顶部，从顶部离开时显示底部
    const extraRange = itemHeight * 1.5
    const parallaxStart = viewportBottom + extraRange  // 视差开始：图片顶部在视口下方
    const parallaxEnd = viewportTop - extraRange  // 视差结束：图片顶部在视口上方
    const totalRange = parallaxEnd - parallaxStart  // 视差总范围

    // 计算进度：基于 itemTop 相对于视差范围的位置
    // progress = 0: itemTop = parallaxStart（图片在视口下方）
    // progress = 0.5: itemTop = viewportBottom（图片中间）
    // progress = 1: itemTop = parallaxEnd（图片在视口上方）
    const progress = Math.min(1, Math.max(0, (itemTop - parallaxStart) / totalRange))

    // 线性视差：从 +maxOffset 过渡到 -maxOffset
    // progress=0: offset = +maxOffset（translateY = -maxOffset，显示顶部）
    // progress=0.5: offset = 0（translateY = 0，正常）
    // progress=1: offset = -maxOffset（translateY = +maxOffset，显示底部）
    const maxOffset = itemHeight * maxOffsetRatio
    const offset = maxOffset * (1 - 2 * progress)

    return offset
  })
}

// 防抖滚动处理
let scrollTimer: ReturnType<typeof setTimeout> | null = null
const handleScroll = () => {
  // 更新视差效果
  updateParallax()

  if (scrollTimer) return

  scrollTimer = setTimeout(() => {
    const scrollTop = window.scrollY || document.documentElement.scrollTop
    const windowHeight = window.innerHeight
    const documentHeight = document.documentElement.scrollHeight

    // 距离底部 800px 时开始加载
    if (scrollTop + windowHeight >= documentHeight - 800) {
      loadMore()
    }

    scrollTimer = null
  }, 100)
}

onMounted(() => {
  (async () => {
    try {
      // 初始化窗口宽度
      windowWidth.value = window.innerWidth
      // 初始化点赞数据
      loadLikedFromStorage()
      await hydrateFromRoute()

      // 同步筛选面板参数
      await syncFilterPanel()

      // 如果已经有数据（从其他页面返回），不重新加载，但可能需要应用筛选
      if (photos.value.length > 0) {
        // 检查是否有URL筛选参数需要应用
        if (photoStore.lastFilters?.value && urlFilters.value) {
          // 重新应用筛选
          await photoStore.filterPhotos(photoStore.lastFilters?.value, 0)
        }
        isActivatedFlag.value = true
        window.addEventListener('scroll', handleScroll, { passive: true })
        window.addEventListener('resize', handleResize)
        // 等待图片加载后布局
        nextTick(() => {
          setTimeout(() => {
            layoutItems()
          }, 100)
        })
        return
      }

      // 声明当前活跃视图为 wall（避免其他视图触发 wall API）
      photoStore.setCurrentView && photoStore.setCurrentView('wall')

      // 根据 URL 参数决定加载方式
      if (activeTagId.value) {
        // 按标签筛选
        await photoStore.fetchPhotosByTag(activeTagId.value, 0)
      } else if (activePersonId.value) {
        // 按人物筛选
        await photoStore.fetchPhotosByPerson(activePersonId.value, 0)
      } else if (urlFilters.value && hasEffectiveFilters(urlFilters.value)) {
        // 应用筛选条件
        await photoStore.filterPhotos(urlFilters.value, 0)
      } else {
        // 加载所有图片
        await loadInitial()
      }

      // 标记组件已激活，后续 onActivated 不再重新加载
      isActivatedFlag.value = true
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
  })()
})

onUnmounted(() => {
  window.removeEventListener('scroll', handleScroll)
  window.removeEventListener('resize', handleResize)
  if (scrollTimer) clearTimeout(scrollTimer)
  if (resizeTimer) clearTimeout(resizeTimer)
  if (layoutTimer) clearTimeout(layoutTimer)
  // 清除当前视图标识
  photoStore.setCurrentView && photoStore.setCurrentView(null)
})

onActivated(() => {
  console.log('[PhotoWall] onActivated 触发')
  // 标记组件已激活，用于路由参数变化监听
  isActivatedFlag.value = true

  // 智能判断是否需要重新加载数据
  // 只有当路由路径发生变化（首次加载或导航到其他路由后返回）时才重新加载
  if (!lastDataLoadedInfo.value || lastDataLoadedInfo.value.fullPath !== route.fullPath) {
    console.log('[PhotoWall] 路由路径变化，需要重新加载数据')
    // 重置加载状态，确保可以继续加载更多
    hasMore.value = true
    isLoadingMore.value = false
    console.log('[PhotoWall] hasMore 重置为:', hasMore.value)

    // 更新窗口宽度
    windowWidth.value = window.innerWidth

    // 恢复滚动位置并添加事件监听器
    setTimeout(() => {
      console.log('[PhotoWall] setTimeout 触发，savedScrollTop:', savedScrollTop.value)
      // 先恢复滚动位置
      window.scrollTo({ top: savedScrollTop.value, behavior: 'instant' as ScrollBehavior })

      // 再次确保滚动位置正确（有时一次可能不够）
      requestAnimationFrame(() => {
        console.log('[PhotoWall] requestAnimationFrame 触发，当前滚动位置:', window.scrollY)
        window.scrollTo({ top: savedScrollTop.value })
      })

      // 添加事件监听器
      window.addEventListener('scroll', handleScroll, { passive: true })
      console.log('[PhotoWall] scroll listener 已添加')
      window.addEventListener('resize', handleResize)

      // 重新布局，确保容器尺寸和位置正确
      if (photos.value.length > 0 && masonryContainer.value) {
        console.log('[PhotoWall] 重新布局，photos:', photos.value.length)
        setTimeout(() => {
          layoutItems()
          updateParallax()
        }, 50)
      }
    }, 10)
  } else {
    console.log('[PhotoWall] 路由路径未变化，保持缓存状态')
    // 路由路径未变化，只是从其他页面返回，不需要重新加载数据
    // 只需恢复滚动位置和事件监听器
    setTimeout(() => {
      window.scrollTo({ top: savedScrollTop.value, behavior: 'instant' as ScrollBehavior })
      window.addEventListener('scroll', handleScroll, { passive: true })
      window.addEventListener('resize', handleResize)
    }, 10)
  }
})

const loadPhotosWithoutClear = async () => {
  currentPage.value = 0
  hasMore.value = true

  // 先清除 store 中的筛选状态
  photoStore.clearLastFilters()

  // 加载所有图片（默认）- 直接调用 API 绕过 store 的检查
  // 先获取新数据，等数据返回后再更新 store
  try {
    photoStore.setCurrentView('wall')
    const response = await api.get('/photos/wall', {
      params: { page: 0, size: 20 }
    })
    // 数据返回后再更新 store，确保 UI 平滑过渡
    photoStore.photosWall = response.data.content || []
    hasMore.value = !response.data.last
  } catch (e) {
    console.error('加载照片墙失败:', e)
  }

  // 滚动到页面顶部（仅当需要时）
  if (window.scrollY > 0) {
    window.scrollTo({ top: 0, behavior: 'auto' })
  }
}

const loadInitial = async () => {
  currentPage.value = 0
  hasMore.value = true

  // 获取分类数据
  await Promise.all([
    photoStore.fetchCategories(),
    loadCategorySortOrder()
  ])

  // 清除 store 中的筛选状态，确保能加载所有照片
  photoStore.clearLastFilters()

  // 加载所有图片（默认）- 直接调用 API 绕过 store 的检查
  try {
    photoStore.setCurrentView('wall')
    const response = await api.get('/photos/wall', {
      params: { page: 0, size: 20 }
    })
    photoStore.photosWall = response.data.content || []
    hasMore.value = !response.data.last
  } catch (e) {
    console.error('加载照片墙失败:', e)
  }
  // 滚动到页面顶部
  window.scrollTo({ top: 0, behavior: 'smooth' })
  // 记录本次加载的路由信息
  lastDataLoadedInfo.value = {
    query: { ...route.query },
    fullPath: route.fullPath
  }
}

const hydrateFromRoute = async () => {
  const tagIdParam = route.query.tagId
  const tagNameParam = route.query.tagName
  activeTagId.value = tagIdParam ? Number(tagIdParam) : null
  activeTagName.value = tagNameParam ? String(tagNameParam) : null

  const personIdParam = route.query.personId
  const personNameParam = route.query.personName
  activePersonId.value = personIdParam ? Number(personIdParam) : null
  activePersonName.value = personNameParam ? String(personNameParam) : null

  // 处理URL中的filters参数
  urlFilters.value = null
  let filtersFromUrl: any = null

  if (route.query.filters) {
    try {
      filtersFromUrl = JSON.parse(route.query.filters as string)
    } catch (e) {
      console.warn('解析URL筛选参数失败:', e)
    }
  }

  // 如果有 tagId 参数，也添加到 filters 中以便筛选面板显示选中状态
  if (activeTagId.value) {
    filtersFromUrl = filtersFromUrl || {}
    filtersFromUrl.tagIds = [activeTagId.value]
  }

  // 如果有 personId 参数，也添加到 filters 中
  if (activePersonId.value) {
    filtersFromUrl = filtersFromUrl || {}
    filtersFromUrl.personIds = [activePersonId.value]
  }

  if (filtersFromUrl) {
    urlFilters.value = filtersFromUrl
    // 保存筛选条件到store，但不自动打开筛选面板 - 使用store方法
    // filterPhotos 内部会设置 lastFilters.value
    if (photoStore && photoStore.filterPhotos) {
      await photoStore.filterPhotos(filtersFromUrl, 0)
    }
  }
}

const clearTag = async () => {
  await router.push({
    path: buildPublicPath('/wall', route.path),
    query: {
      personId: activePersonId.value || undefined,
      personName: activePersonName.value || undefined
    }
  })
}

watch(
  () => [route.query.tagId, route.query.personId, route.query.filters],
  async () => {
    // 仅当组件已激活过且数据已加载时才重新加载
    if (isActivatedFlag.value) {
      // 如果正在清除筛选或应用筛选，跳过本次触发（已经手动处理了）
      if (isClearingFilters.value || isApplyingFilters.value) {
        return
      }
      // 更新内部状态
      await hydrateFromRoute()

      // 根据 URL 参数类型选择加载方式
      if (activeTagId.value) {
        // 按标签筛选
        currentPage.value = 0
        hasMore.value = true
        await photoStore.fetchPhotosByTag(activeTagId.value, 0)
      } else if (activePersonId.value) {
        // 按人物筛选
        currentPage.value = 0
        hasMore.value = true
        await photoStore.fetchPhotosByPerson(activePersonId.value, 0)
      } else if (urlFilters.value && hasEffectiveFilters(urlFilters.value)) {
        // 应用筛选条件
        currentPage.value = 0
        hasMore.value = true
        await photoStore.filterPhotos(urlFilters.value, 0)
      } else {
        // 加载所有照片
        await loadPhotosWithoutClear()
      }
      nextTick(() => {
        setTimeout(() => {
          layoutItems()
        }, 50)
      })
    }
  }
)

onDeactivated(() => {
  savedScrollTop.value = window.scrollY || 0
  window.removeEventListener('scroll', handleScroll)
  window.removeEventListener('resize', handleResize)
  // 清除当前视图标识，防止切换时残留触发请求
  photoStore.setCurrentView && photoStore.setCurrentView(null)
  // 标记组件已停用，下次激活时需要恢复状态
  isActivatedFlag.value = false
})
</script>

<style scoped>
.masonry-container {
  position: relative;
  width: 100%;
}

.masonry-item {
  transition: transform 0.3s ease;
  will-change: transform;
}

.masonry-item:hover {
  transform: translateY(-2px);
}

.masonry-image-wrapper {
  width: 100%;
  overflow: hidden;
  position: relative;
}

/* 曝光参数悬浮层 */
.photo-info-overlay {
  position: absolute;
  bottom: 2px;
  left: 2px;
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
  padding: 8px 12px;
  opacity: 0;
  transition: opacity 0.3s ease, transform 0.3s ease;
  pointer-events: none;
  /* 无背景蒙版，参数悬浮显示 */
}

.masonry-item:hover .photo-info-overlay {
  opacity: 1;
}

.params-row {
  display: flex;
  flex-wrap: nowrap;
  gap: 5px;
  font-family: 'SF Mono', 'Monaco', 'Consolas', monospace;
  font-size: 10px;
  color: rgba(255, 255, 255, 0.9);
  letter-spacing: 0.3px;
}

.param {
  background: rgba(0, 0, 0, 0.5);
  padding: 2px 5px;
  border-radius: 3px;
  white-space: nowrap;
}

.camera-param {
  margin-left: auto;
}

.masonry-photo-image {
  width: 100%;
  height: auto;
  display: block;
  object-fit: cover;
}

.masonry-item:hover .masonry-photo-image {
  transform: scale(1.05);
}

/* like overlay */
.like-overlay {
  position: absolute;
  right: 6px;
  bottom: 6px;
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px 6px;
  border-radius: 9999px;
  background: rgba(0,0,0,0.28); /* 更低的不透明度，减少视觉干扰 */
  color: #fff;
  font-size: 12px;
  opacity: 0; /* 默认隐藏，只有 hover 或 visible 才显示 */
  transition: opacity 0.18s ease, transform 0.12s ease;
  cursor: pointer;
  pointer-events: auto;
}
/* 仅当鼠标悬浮在点赞按钮本身时显示（避免干扰看图） */
.like-overlay:hover {
  opacity: 0.95;
}
.like-overlay.visible {
  /* 如果已有点赞，则常驻显示，但采用较低不透明度以不打扰查看 */
  opacity: 0.6;
}
.like-overlay.visible:hover {
  /* 常驻显示的点赞按钮在hover时也保持较低透明度 */
  opacity: 0.6 !important;
}
.like-overlay .heart {
  color: #fff;
  stroke: currentColor;
}
.like-overlay .heart[fill='#e11d48'] {
  color: #e11d48;
}
.like-count {
  font-weight: 600 !important;
  font-size: 11px !important;
  color: #fff !important;
  text-shadow: 0 1px 2px rgba(0,0,0,0.5) !important;
  display: inline !important;
  visibility: visible !important;
  opacity: 1 !important;
}

/* heart animation */
.like-overlay .heart {
  transition: transform 220ms cubic-bezier(.2,.9,.3,1), color 180ms ease, stroke 180ms ease, fill 180ms ease;
  transform-origin: center center;
  display: inline-block;
}
.like-overlay .heart.liked {
  transform: scale(1.25);
  color: #e11d48;
}
.like-overlay .heart .heart-path {
  fill: transparent;
  transition: fill 220ms ease;
}
.like-overlay .heart.liked .heart-path {
  fill: #e11d48;
}

/* like button expansion and smooth shift */
.like-btn {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  padding: 2px 4px;
  border-radius: 9999px;
  background: transparent;
  transition: padding 260ms cubic-bezier(.2,.9,.3,1);
  overflow: hidden;
  white-space: nowrap;
}
.like-btn .heart {
  transition: transform 220ms cubic-bezier(.2,.9,.3,1);
  transform-origin: left center;
  align-self: center;
  vertical-align: middle;
}
.like-btn .like-count {
  transform: translateX(2px);
  opacity: 0;
  transition: transform 260ms cubic-bezier(.2,.9,.3,1), opacity 200ms ease;
}
.like-btn.liked {
  padding-left: 2px;
  padding-right: 2px;
}
.like-btn.liked .heart {
  transform: translateX(-2px) scale(1.12);
}
.like-btn.liked .like-count {
  transform: translateX(0);
  opacity: 1;
}

/* pop animation when clicked */
.like-btn.pop {
  animation: pop 420ms cubic-bezier(.2,.9,.3,1);
}
@keyframes pop {
  0% { transform: scale(1); }
  30% { transform: scale(1.28); }
  60% { transform: scale(0.98); }
  100% { transform: scale(1); }
}
</style>
