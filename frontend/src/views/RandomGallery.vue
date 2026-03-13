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
            <FilterPanel ref="filterPanelRef" v-model:show="showFilter" :categories="categories" :initial-filters="urlFilters" @reset="handleFilterReset" @update:selectedTags="updateSelectedTags" @filters-applied="handleFiltersApplied" />
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
      <div v-if="currentFilters" class="mb-6 flex items-center justify-between gap-3 text-sm text-gray-600 dark:text-gray-300">
        <div class="px-3 py-1 rounded bg-gray-100/60 dark:bg-gray-800/60 cursor-pointer hover:bg-gray-200/60 dark:hover:bg-gray-700/60 transition-colors" @click="showFilter = true">
          筛选：{{ filterSummary }}
        </div>
        <button class="text-red-500 hover:text-red-600 text-lg font-bold leading-none" @click="clearFilters">×</button>
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
          :data-photo-id="photo.id"
          @click="openViewer(idx, $event)"
        >
          <div class="aspect-square overflow-hidden rounded-lg relative">
            <img
              :src="getImageUrl(photo)"
              :alt="photo.filename"
              class="photo-image w-full h-full transition-transform duration-400"
              :style="getImageStyle(photo)"
              loading="lazy"
              @error="onImageError"
            />
            <!-- 曝光参数悬浮层 -->
            <div v-if="photo.focalLength || photo.aperture || photo.iso || photo.shutterSpeed" class="photo-info-overlay">
              <div class="params-row">
                <span v-if="photo.focalLength" class="param">{{ photo.focalLength }}mm</span>
                <span v-if="photo.aperture" class="param">f/{{ photo.aperture }}</span>
                <span v-if="photo.shutterSpeed" class="param">{{ photo.shutterSpeed }}</span>
                <span v-if="photo.iso" class="param">ISO {{ photo.iso }}</span>
              </div>
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
defineOptions({ name: 'Random' })
import { ref, computed, onMounted, onUnmounted, onActivated, onDeactivated } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { usePhotoStore } from '@/stores/photo'
import { useThemeStore } from '@/stores/theme'

const route = useRoute()
const router = useRouter()

// 从URL参数解析的筛选条件
const urlFilters = ref<any>(null)
import NavLinks from '@/components/NavLinks.vue'
import MobileBottomNav from '@/components/MobileBottomNav.vue'
import PhotoViewer from '@/components/PhotoViewer.vue'
import FilterPanel from '@/components/FilterPanel.vue'
import SettingsMenu from '@/components/SettingsMenu.vue'
import AppHeader from '@/components/AppHeader.vue'
import { useUiSettings } from '@/composables/useUiSettings'
import { useMobileNav } from '@/composables/useMobileNav'
import { useNavAutoHide } from '@/composables/useNavAutoHide'
import { sortCategories, loadCategorySortOrder } from '@/composables/useCategorySorting'
import { api } from '@/api'

const photoStore = usePhotoStore()
const themeStore = useThemeStore()
const { previewSize } = useUiSettings()
const { isMobile } = useMobileNav()
const { isHidden: navHidden } = useNavAutoHide()

const photos = computed(() => photoStore.photosRandom)
const loading = computed(() => photoStore.loading)
const categories = computed(() => sortCategories(photoStore.categories))
const currentPage = ref(0)
const hasMore = ref(true)
const savedScrollTop = ref(0)
const showFilter = ref(false)
const isLoadingMore = ref(false)
// 标记组件是否已激活（用于区分首次加载和从其他页面返回）
const isActivatedFlag = ref(false)
const filterPanelRef = ref()

// 点赞相关（匿名点赞，使用 localStorage 保存用户是否已点赞）
const likedIds = ref<Set<number>>(new Set())
const likesMap = ref<Map<number, number>>(new Map())

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

const viewerVisible = ref(false)
const viewerIndex = ref(0)
const viewerOriginRect = ref<{ top: number; left: number; width: number; height: number } | null>(null)
const gridClass = computed(() => {
  if (previewSize.value === 'sm') return 'grid grid-cols-3 sm:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6 gap-3'
  if (previewSize.value === 'md') return 'grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 gap-4'
  if (previewSize.value === 'lg') return 'grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-6'
  return 'grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 gap-4'
})

// 当前已启用的筛选（来自 store.lastFilters）
const currentFilters = computed(() => photoStore.lastFilters || null)

// 选中的标签列表
const selectedTags = ref<any[]>([])

// 更新选中的标签
const updateSelectedTags = (tags: any[]) => {
  selectedTags.value = tags
}

// 已查看的图片ID集合（避免重复显示）
const viewedPhotoIds = ref(new Set<number>())

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
  photoStore.clearLastFilters()
  selectedTags.value = [] // 重置选中的标签
  // 重置筛选面板的状态
  if (filterPanelRef.value && filterPanelRef.value.resetFilters) {
    filterPanelRef.value.resetFilters()
  }
  // 清除URL参数
  if (route.query.filters) {
    await router.replace({ query: {} })
  }
  await loadInitial()
  // 滚动到页面顶部
  window.scrollTo({ top: 0, behavior: 'smooth' })
}


const handleFilterReset = async () => {
  selectedTags.value = [] // 重置选中的标签
  await loadInitial()
  // 滚动到页面顶部
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

const handleFiltersApplied = () => {
  // 重置分页状态，让新的筛选可以重新加载更多
  currentPage.value = 0
  hasMore.value = true
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
  const count = 20
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

// 初始化 likesMap
import { watch } from 'vue'

watch(() => photos.value, (photos) => {
  photos.forEach(photo => {
    const pid = photo?.id
    if (pid != null) {
      likesMap.value.set(pid, photo?.likeCount || 0)
    }
  })
}, { immediate: true, deep: true })

const loadMore = async () => {
  // 防止重复加载
  if (loading.value || isLoadingMore.value || !hasMore.value) return

  try {
    isLoadingMore.value = true
    // 如果筛选请求正在进行，先不触发加载更多（避免竞态）
    if (photoStore.lastFiltersLoading && photoStore.lastFiltersLoading.value) {
      isLoadingMore.value = false
      return
    }
    // 如果存在活动筛选且已被标记为耗尽，直接停止并不增加页码
    if (photoStore.lastFiltersExhausted && photoStore.lastFiltersExhausted.value) {
      hasMore.value = false
      isLoadingMore.value = false
      return
    }
    currentPage.value++
    
    let data: any
    if (photoStore.isFiltersLoading && photoStore.isFiltersLoading()) {
      isLoadingMore.value = false
      return
    }
    if (photoStore.hasActiveFilters && photoStore.hasActiveFilters()) {
      if (photoStore.lastFiltersExhausted && photoStore.lastFiltersExhausted.value) {
        hasMore.value = false
        isLoadingMore.value = false
        return
      }
      const filtersObj = photoStore.lastFilters && photoStore.lastFilters
      if (!filtersObj) {
        currentPage.value--
        hasMore.value = false
        isLoadingMore.value = false
        return
      }
      // 如果当前存在过滤条件，使用同样的过滤参数分页加载（保留随机页面的大小）
      const excludeIds = Array.from(viewedPhotoIds.value)
      const filtersWithExclusion = {
        ...filtersObj,
        excludePhotoIds: excludeIds.length > 0 ? excludeIds : [-1] // 如果为空，传-1（不可能的ID）
      }
      data = await photoStore.filterPhotos(filtersWithExclusion, currentPage.value, 20)
    } else {
      // 确保 currentView 设置为 'random'，防止 store 检查返回空数据
      photoStore.setCurrentView('random')
      data = await photoStore.fetchRandomPhotos(currentPage.value, 20, 70)
    }

    if (!data || !data.content || data.content.length === 0) {
      // 回退页码（请求未返回数据）
      currentPage.value--
      hasMore.value = false
      return
    }

    hasMore.value = !data.last

    // 记录已查看的图片ID（避免后续重复显示）
    if (data.content && data.content.length > 0) {
      data.content.forEach((photo: any) => {
        viewedPhotoIds.value.add(photo.id)
      })
    }
  } catch (error) {
    console.error('加载更多失败:', error)
    // 加载失败时回退页码
    currentPage.value--
    hasMore.value = false
  } finally {
    isLoadingMore.value = false
  }
}

// 防抖滚动处理
let scrollTimer: ReturnType<typeof setTimeout> | null = null
const handleScroll = () => {
  // 节流：100ms 内只执行一次
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

const loadInitial = async () => {
  try {
    // 初始化点赞数据
    loadLikedFromStorage()
    // 获取分类数据
    await Promise.all([
      photoStore.fetchCategories(),
      loadCategorySortOrder()
    ])
    currentPage.value = 0
    hasMore.value = true
    let data: any

    // 解析 URL 参数
    const tagIdParam = route.query.tagId ? Number(route.query.tagId) : null
    const personIdParam = route.query.personId ? Number(route.query.personId) : null

    // 检查URL中的filters参数
    urlFilters.value = null
    let filtersFromUrl: any = null

    if (route.query.filters) {
      try {
        filtersFromUrl = JSON.parse(route.query.filters as string)
      } catch (e) {
        console.warn('解析URL筛选参数失败:', e)
      }
    }

    // 如果有 tagId 参数，添加到 filters 中
    if (tagIdParam) {
      filtersFromUrl = filtersFromUrl || {}
      filtersFromUrl.tagIds = [tagIdParam]
    }

    // 如果有 personId 参数，添加到 filters 中
    if (personIdParam) {
      filtersFromUrl = filtersFromUrl || {}
      filtersFromUrl.personIds = [personIdParam]
    }

    if (filtersFromUrl) {
      urlFilters.value = filtersFromUrl
      // 将URL筛选条件应用到photoStore
      photoStore.lastFilters = filtersFromUrl
      photoStore.lastFiltersActive.value = true
    }

    if (photoStore.lastFilters) {
      // 筛选模式：传递已查看的图片ID列表以避免重复
      const excludeIds = Array.from(viewedPhotoIds.value)
      const filtersWithExclusion = {
        ...photoStore.lastFilters,
        excludePhotoIds: excludeIds.length > 0 ? excludeIds : [-1] // 如果为空，传-1（不可能的ID）
      }
      data = await photoStore.filterPhotos(filtersWithExclusion, 0, 20)
    } else {
      data = await photoStore.fetchRandomPhotos(0, 20, 70)
    }

    // 记录已查看的图片ID
    if (data.content && data.content.length > 0) {
      data.content.forEach((photo: any) => {
        viewedPhotoIds.value.add(photo.id)
      })
    }

    hasMore.value = !data.last
    window.addEventListener('scroll', handleScroll, { passive: true })
    // 滚动到页面顶部
    window.scrollTo({ top: 0, behavior: 'smooth' })
  } catch (error) {
    console.error('初始化加载失败:', error)
    hasMore.value = false
  }
}

onMounted(async () => {
  // 声明当前活跃视图为 random（避免其他视图触发 random API）
  photoStore.setCurrentView && photoStore.setCurrentView('random')

  // 如果已经有数据（从其他页面返回），检查是否需要应用筛选
  if (photos.value.length > 0) {
    // 解析 URL 参数
    const tagIdParam = route.query.tagId ? Number(route.query.tagId) : null
    const personIdParam = route.query.personId ? Number(route.query.personId) : null

    // 如果有 URL 筛选参数，重新应用筛选
    if (tagIdParam || personIdParam || route.query.filters) {
      await loadInitial()
    }
    isActivatedFlag.value = true
    window.addEventListener('scroll', handleScroll, { passive: true })
    return
  }

  // 首次加载
  await loadInitial()
  // 标记组件已激活，后续 onActivated 不再重新加载
  isActivatedFlag.value = true
})

onUnmounted(() => {
  window.removeEventListener('scroll', handleScroll)
  if (scrollTimer) clearTimeout(scrollTimer)
  // 清除当前视图标识
  photoStore.setCurrentView && photoStore.setCurrentView(null)
  try { delete (window as any).__photoStore } catch(e) {}
})

onActivated(() => {
  console.log('[RandomGallery] onActivated 触发')
  // 重置加载状态，确保可以继续加载更多
  hasMore.value = true
  isLoadingMore.value = false

  // 恢复滚动位置
  if (savedScrollTop.value > 0) {
    requestAnimationFrame(() => {
      window.scrollTo({ top: savedScrollTop.value })
    })
  }

  // 添加事件监听器
  window.addEventListener('scroll', handleScroll, { passive: true })
})

onDeactivated(() => {
  savedScrollTop.value = window.scrollY || 0
  window.removeEventListener('scroll', handleScroll)
  // 清除当前视图标识，防止切换时残留触发请求
  photoStore.setCurrentView && photoStore.setCurrentView(null)
  // 标记组件已停用，下次激活时需要恢复状态
  isActivatedFlag.value = false
})
</script>

<style scoped>
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

/* 减少图片间距，与其他页面保持一致 */
.photo-card {
  margin-bottom: 0.15rem !important;
  transition: transform 0.3s ease;
}

.photo-card:hover {
  transform: translateY(-2px);
}

.photo-card:hover .photo-image {
  transform: scale(1.03);
}

.photo-image {
  transition: transform 0.4s ease;
}

/* 曝光参数悬浮层 */
.photo-info-overlay {
  position: absolute;
  bottom: 2px;
  left: 2px;
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
  padding: 4px 6px;
  opacity: 0;
  transition: opacity 0.3s ease, transform 0.3s ease;
  pointer-events: none;
  /* 无背景蒙版，参数悬浮显示 */
}

.group:hover .photo-info-overlay {
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

.camera-info {
  font-size: 10px;
  color: rgba(255, 255, 255, 0.7);
  margin-top: 4px;
  white-space: nowrap;
  margin-left: auto;
}

.camera-param {
  margin-left: auto;
}
</style>

