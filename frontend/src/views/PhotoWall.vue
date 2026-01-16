<template>
  <div class="min-h-screen bg-white dark:bg-gray-900">
    <nav
      class="sticky top-0 z-50 bg-white/80 dark:bg-gray-900/80 backdrop-blur-md border-b border-gray-200 dark:border-gray-800 safe-area-inset-top transition-transform duration-300 ease-in-out transform-gpu"
      :class="{ '-translate-y-full': isMobile && navHidden }"
      style="padding-top: env(safe-area-inset-top);"
    >
      <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div class="flex justify-between items-center h-16">
          <div class="flex items-center space-x-8">
            <router-link to="/" class="text-2xl font-light tracking-wider">摄影展</router-link>
            <NavLinks v-if="!isMobile" />
          </div>
          <div class="flex items-center space-x-4">
            <button @click="themeStore.toggleTheme" class="p-2 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-800 transition-all duration-200 hover:scale-110 hover:shadow-md transform-gpu group relative overflow-hidden">
              <svg v-if="!themeStore.isDark" class="w-5 h-5 transition-all duration-300 group-hover:rotate-12 group-hover:scale-110" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M20.354 15.354A9 9 0 018.646 3.646 9.003 9.003 0 0012 21a9.003 9.003 0 008.354-5.646z" />
              </svg>
              <svg v-else class="w-5 h-5 transition-all duration-300 group-hover:rotate-12 group-hover:scale-110" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 3v1m0 16v1m9-9h-1M4 12H3m15.364 6.364l-.707-.707M6.343 6.343l-.707-.707m12.728 0l-.707.707M6.343 17.657l-.707.707M16 12a4 4 0 11-8 0 4 4 0 018 0z" />
              </svg>
              <div class="absolute inset-0 bg-gradient-to-r from-yellow-500/10 to-orange-500/10 opacity-0 group-hover:opacity-100 transition-opacity duration-200 rounded-lg"></div>
            </button>
            <FilterPanel v-model:show="showFilter" @reset="handleFilterReset" @update:selectedTags="updateSelectedTags" @filters-applied="handleFiltersApplied" />
            <SettingsMenu />
          </div>
        </div>
      </div>
    </nav>

    <main class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-12">
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
          <div class="masonry-image-wrapper">
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
          <div class="gradient-overlay">
            <div class="absolute bottom-0 left-0 right-0 p-4 text-white">
              <p class="text-sm font-light">{{ photo.filename }}</p>
              <p v-if="photo.cameraModel" class="text-xs opacity-75 mt-1">{{ photo.cameraModel }}</p>
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
import SettingsMenu from '@/components/SettingsMenu.vue'
import { useUiSettings } from '@/composables/useUiSettings'
import { useMobileNav } from '@/composables/useMobileNav'
import { useNavAutoHide } from '@/composables/useNavAutoHide'
import { api } from '@/api'

const photoStore = usePhotoStore()
const themeStore = useThemeStore()
const route = useRoute()
const router = useRouter()

const photos = computed(() => photoStore.photosWall)
const loading = computed(() => photoStore.loading)
const currentPage = ref(0)
const hasMore = ref(true)
const viewerVisible = ref(false)
const viewerIndex = ref(0)
const showFilter = ref(false)
const viewerOriginRect = ref<{ top: number; left: number; width: number; height: number } | null>(null)
const savedScrollTop = ref(0)
const masonryContainer = ref<HTMLElement | null>(null)
const isLoadingMore = ref(false)
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

// 当前已启用的筛选（来自 store.lastFilters）
const currentFilters = computed(() => photoStore.lastFilters || null)

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

// 根据预览尺寸计算列数
const columnCount = computed(() => {
  const width = windowWidth.value
  let count = 3 // 默认值 (md)

  if (previewSize.value === 'sm') {
    // 小: 中等列数
    if (width < 640) count = 2
    else if (width < 1024) count = 3
    else if (width < 1280) count = 4
    else count = 5
  } else if (previewSize.value === 'md') {
    // 中: 默认列数
    if (width < 640) count = 1
    else if (width < 1024) count = 2
    else if (width < 1280) count = 3
    else count = 4
  } else if (previewSize.value === 'lg') {
    // 大: 最少列数
    if (width < 640) count = 1
    else if (width < 1024) count = 2
    else count = 3
  } else {
    // 默认 md
    if (width < 640) count = 1
    else if (width < 1024) count = 2
    else if (width < 1280) count = 3
    else count = 4
  }
  console.log('计算列数: width=', width, 'previewSize=', previewSize.value, 'count=', count)
  return count
})

// 计算每列的宽度 - 与layoutItems保持一致
const columnWidth = computed(() => {
  if (!masonryContainer.value) return 0
  const containerWidth = masonryContainer.value.clientWidth
  const gap = 20
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
  const gap = 20
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
  // 放大图片以填充容器并允许视差滚动显示不同部分
  // 增加基础放大比例以消除白边
  const baseScale = 1.2
  
  // 根据图片位置和视差偏移动态调整放大比例
  // 如果图片向上偏移（parallaxOffset < 0），需要更大的放大比例来填充下方
  // 如果图片向下偏移（parallaxOffset > 0），需要更大的放大比例来填充上方
  const offsetScale = parallaxEnabled.value && Math.abs(parallaxOffset) > 0 ? 0.05 : 0
  const scale = baseScale + offsetScale
  
  return {
    transform: `translateY(${parallaxOffset}px) scale(${scale})`,
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

  const gap = 20
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
  
  // 保存当前滚动位置
  const scrollTop = window.scrollY || document.documentElement.scrollTop
  
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
      const filtersObj = photoStore.lastFilters && photoStore.lastFilters
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
      data = await photoStore.fetchPhotoWall(currentPage.value)
    }

    if (!data || !data.content || data.content.length === 0) {
      // 回退页码（请求未返回数据）
      currentPage.value--
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
  const viewportCenterY = scrollTop + windowHeight / 2
  
  // 视差强度（可调整，值越大偏移越明显）
  // 减小视差强度，避免图片偏移过大导致白边
  const parallaxStrength = 0.12
  
  parallaxOffsets.value = itemPositions.value.map((pos, idx) => {
    if (!itemRefs.value[idx]) return 0
    
    // 计算图片中心点相对于容器的位置
    const itemHeight = itemRefs.value[idx]?.offsetHeight || 200
    const itemTop = containerTop + pos.top
    const itemBottom = itemTop + itemHeight
    const itemCenterY = itemTop + itemHeight / 2
    
    // 检查图片是否在视口范围内（包括部分可见）
    const isVisible = itemBottom > viewportTop && itemTop < viewportBottom
    
    if (!isVisible) {
      // 如果图片完全不在视口内，不应用视差
      return 0
    }
    
    // 计算图片中心点相对于视口中心的距离
    const distanceFromCenter = itemCenterY - viewportCenterY
    
    // 计算偏移量：距离中心越远，偏移越大；在中心时为0
    // 减小最大偏移量，避免白边
    const maxOffset = itemHeight * 0.08 // 最大偏移为图片高度的8%
    const offset = Math.max(-maxOffset, Math.min(maxOffset, distanceFromCenter * parallaxStrength))
    
    return offset
  })
}

// 防抖滚动处理
let scrollTimer: ReturnType<typeof setTimeout> | null = null
const handleScroll = () => {
  // 更新视差效果
  updateParallax()
  
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
    // 初始化点赞数据
    loadLikedFromStorage()
    hydrateFromRoute()
    // 声明当前活跃视图为 wall（避免其他视图触发 wall API）
    photoStore.setCurrentView && photoStore.setCurrentView('wall')
    await loadInitial()
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
  if (layoutTimer) clearTimeout(layoutTimer)
  // 清除当前视图标识
  photoStore.setCurrentView && photoStore.setCurrentView(null)
})

onActivated(() => {
  // 更新窗口宽度
  windowWidth.value = window.innerWidth
  
  nextTick(() => {
    window.scrollTo({ top: savedScrollTop.value, behavior: 'instant' as ScrollBehavior })
    window.addEventListener('scroll', handleScroll, { passive: true })
    window.addEventListener('resize', handleResize)
    
    // 重新布局，确保容器尺寸和位置正确
    if (photos.value.length > 0 && masonryContainer.value) {
      // 等待 DOM 更新完成
      setTimeout(() => {
        layoutItems()
        updateParallax()
      }, 50)
    }
  })
})

const loadInitial = async () => {
  currentPage.value = 0
  hasMore.value = true
  if (photoStore.lastFilters) {
    await photoStore.filterPhotos(photoStore.lastFilters, 0)
  } else if (activePersonId.value) {
    await photoStore.fetchPhotosByPerson(activePersonId.value, 0)
  } else if (activeTagId.value) {
    await photoStore.fetchPhotosByTag(activeTagId.value, 0)
  } else {
    await photoStore.fetchPhotoWall(0)
  }
  // 滚动到页面顶部
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

const hydrateFromRoute = () => {
  const tagIdParam = route.query.tagId
  const tagNameParam = route.query.tagName
  activeTagId.value = tagIdParam ? Number(tagIdParam) : null
  activeTagName.value = tagNameParam ? String(tagNameParam) : null

  const personIdParam = route.query.personId
  const personNameParam = route.query.personName
  activePersonId.value = personIdParam ? Number(personIdParam) : null
  activePersonName.value = personNameParam ? String(personNameParam) : null
}

const clearTag = async () => {
  await router.push({
    path: '/wall',
    query: {
      personId: activePersonId.value || undefined,
      personName: activePersonName.value || undefined
    }
  })
}

watch(
  () => [route.query.tagId, route.query.personId],
  async () => {
    hydrateFromRoute()
    await loadInitial()
    nextTick(() => {
      setTimeout(() => {
        layoutItems()
      }, 50)
    })
  }
)

onDeactivated(() => {
  savedScrollTop.value = window.scrollY || 0
  window.removeEventListener('scroll', handleScroll)
  window.removeEventListener('resize', handleResize)
  // 清除当前视图标识，防止切换时残留触发请求
  photoStore.setCurrentView && photoStore.setCurrentView(null)
})
</script>

<style scoped>
.masonry-container {
  position: relative;
  width: 100%;
}

.masonry-item {
  margin-bottom: 1.25rem;
  transition: transform 0.1s ease-out;
  will-change: transform;
}

.masonry-image-wrapper {
  width: 100%;
  overflow: hidden;
  position: relative;
  /* 不设置固定高度，让图片自然高度决定容器高度 */
  /* 添加小量 padding 来隐藏可能的白边 */
  padding: 2px 0;
}

.masonry-photo-image {
  width: 100%;
  height: auto;
  display: block;
  object-fit: cover;
  transition: transform 0.1s ease-out;
  will-change: transform;
}

.masonry-photo-image:hover {
  transform: scale(1.2) !important;
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

