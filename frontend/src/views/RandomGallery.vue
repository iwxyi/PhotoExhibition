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
          :data-photo-id="photo.id"
          @click="openViewer(idx, $event)"
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
defineOptions({ name: 'Random' })
import { ref, computed, onMounted, onUnmounted, onActivated, onDeactivated, nextTick } from 'vue'
import { usePhotoStore } from '@/stores/photo'
import { useThemeStore } from '@/stores/theme'
import NavLinks from '@/components/NavLinks.vue'
import MobileBottomNav from '@/components/MobileBottomNav.vue'
import PhotoViewer from '@/components/PhotoViewer.vue'
import SettingsMenu from '@/components/SettingsMenu.vue'
import { useUiSettings } from '@/composables/useUiSettings'
import { useMobileNav } from '@/composables/useMobileNav'
import { useNavAutoHide } from '@/composables/useNavAutoHide'
import { api } from '@/api'

const photoStore = usePhotoStore()
const themeStore = useThemeStore()
const { previewSize } = useUiSettings()
const { isMobile } = useMobileNav()
const { isHidden: navHidden } = useNavAutoHide()

const photos = computed(() => photoStore.photos)
const loading = computed(() => photoStore.loading)
const currentPage = ref(0)
const hasMore = ref(true)
const savedScrollTop = ref(0)
const isLoadingMore = ref(false)

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
  if (previewSize.value === 'xs') return 'grid grid-cols-3 sm:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6 gap-3'
  if (previewSize.value === 'sm') return 'grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5 gap-4'
  if (previewSize.value === 'lg') return 'grid grid-cols-1 md:grid-cols-2 lg:grid-cols-2 xl:grid-cols-3 gap-7'
  return 'grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6'
})

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
    // 初始化点赞数据
    loadLikedFromStorage()
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
</style>

