<template>
  <div class="min-h-screen bg-white dark:bg-gray-900">
    <!-- 导航栏 -->
    <nav class="sticky top-0 z-50 bg-white/80 dark:bg-gray-900/80 backdrop-blur-md border-b border-gray-200 dark:border-gray-800">
      <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div class="flex justify-between items-center h-16">
          <div class="flex items-center space-x-8">
            <router-link to="/" class="text-2xl font-light tracking-wider text-gray-900 dark:text-white">
              摄影展
            </router-link>
            <NavLinks />
          </div>
          <div class="flex items-center space-x-4">
            <button
              @click="themeStore.toggleTheme"
              class="p-2 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-800 transition-all duration-200 hover:scale-110 hover:shadow-md transform-gpu group relative overflow-hidden"
            >
              <svg v-if="!themeStore.isDark" class="w-5 h-5 transition-all duration-300 group-hover:rotate-12 group-hover:scale-110" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M20.354 15.354A9 9 0 018.646 3.646 9.003 9.003 0 0012 21a9.003 9.003 0 008.354-5.646z" />
              </svg>
              <svg v-else class="w-5 h-5 transition-all duration-300 group-hover:rotate-12 group-hover:scale-110" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 3v1m0 16v1m9-9h-1M4 12H3m15.364 6.364l-.707-.707M6.343 6.343l-.707-.707m12.728 0l-.707.707M6.343 17.657l-.707.707M16 12a4 4 0 11-8 0 4 4 0 018 0z" />
              </svg>
              <div class="absolute inset-0 bg-gradient-to-r from-yellow-500/10 to-orange-500/10 opacity-0 group-hover:opacity-100 transition-opacity duration-200 rounded-lg"></div>
            </button>
            <FilterPanel v-model:show="showFilter" />
            <SettingsMenu />
          </div>
        </div>
      </div>
    </nav>

    <!-- 相册网格 -->
    <main class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 pt-6 pb-12" style="contain: layout style paint;">
      <!-- 分类 Tabs -->
      <div class="mb-6">
        <div class="flex gap-2 overflow-x-auto pb-2 px-2 py-1">
          <button
            v-for="c in ['全部', ...categories]"
            :key="c"
            @click="selectCategory(c)"
            class="px-4 py-2 rounded-full border transition-all duration-200 hover:scale-105 hover:shadow-sm transform-gpu group relative overflow-hidden font-medium text-sm"
            style="transform-origin: center;"
            :class="c === activeCategory
              ? 'bg-gray-900 text-white border-gray-800 dark:bg-white dark:text-gray-900 dark:border-white shadow-lg ring-2 ring-gray-900/20 dark:ring-white/20 scale-102'
              : 'bg-gray-100 text-gray-800 border-gray-300 hover:bg-gray-200 dark:bg-gray-800 dark:text-gray-100 dark:border-gray-700 dark:hover:bg-gray-700'"
          >
            <span class="relative z-10 transition-transform duration-200 group-hover:scale-105">{{ c }}</span>
            <div
              v-if="c === activeCategory"
              class="absolute inset-0 bg-gradient-to-r from-blue-500/20 to-purple-500/20 rounded-full transition-all duration-300 animate-pulse"
            ></div>
            <div class="absolute inset-0 bg-gradient-to-r from-gray-500/10 to-gray-600/10 opacity-0 group-hover:opacity-100 transition-opacity duration-200 rounded-full"></div>
          </button>
        </div>
      </div>

      <div v-if="loading && albums.length === 0" class="flex justify-center items-center h-96">
        <div class="animate-spin rounded-full h-12 w-12 border-t-2 border-b-2 border-gray-900 dark:border-white"></div>
      </div>

      <div
        v-if="albums.length > 0"
        :class="coverGridClass"
        style="contain: layout style;"
      >
        <AlbumCard
          v-for="album in albums"
          :key="album.id"
          :album="album"
          :size="coverSize"
          @click="goToAlbum(album.id)"
        />
      </div>

      <!-- 加载状态 -->
      <div v-if="isLoadingMore && albums.length > 0" class="mt-12 text-center">
        <div class="animate-spin rounded-full h-12 w-12 border-t-2 border-b-2 border-gray-900 dark:border-white mx-auto"></div>
      </div>
      <div v-if="!hasMore && albums.length > 0" class="mt-12 text-center text-gray-500 dark:text-gray-400">
        <p>已加载全部相册</p>
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
defineOptions({ name: 'Home' })
import { ref, onMounted, onUnmounted, onActivated, onDeactivated, nextTick, computed, watch } from 'vue'
import { useRouter } from 'vue-router'
import { usePhotoStore } from '@/stores/photo'
import { useThemeStore } from '@/stores/theme'
import { api } from '@/api'
import AlbumCard from '@/components/AlbumCard.vue'
import FilterPanel from '@/components/FilterPanel.vue'
import SettingsMenu from '@/components/SettingsMenu.vue'
import { useUiSettings } from '@/composables/useUiSettings'

const router = useRouter()
const photoStore = usePhotoStore()
const themeStore = useThemeStore()
import NavLinks from '@/components/NavLinks.vue'

const albums = computed(() => photoStore.albums)
const loading = computed(() => photoStore.loading)
const categories = computed(() => photoStore.categories)
const showFilter = ref(false)
const currentPage = ref(0)
const hasMore = ref(true)
const activeCategory = ref('全部')
const savedScrollTop = ref(0)
const isLoadingMore = ref(false)
const albumSortOrder = ref('name_asc')
const { coverSize } = useUiSettings()

// 监听排序设置变化，重新获取数据
watch(albumSortOrder, async (newSort, oldSort) => {
  if (newSort !== oldSort) {
    // 重新获取相册数据
    currentPage.value = 0
    hasMore.value = true
    const loadSize = getDynamicLoadSize()
    const cat = activeCategory.value === '全部' ? undefined : activeCategory.value
    const data = await photoStore.fetchAlbums(0, loadSize, cat, newSort)
    hasMore.value = !data.last
  }
})
const coverGridClass = computed(() => {
  if (coverSize.value === 'sm') {
    return 'grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5 gap-6'
  }
  if (coverSize.value === 'lg') {
    return 'grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-3 xl:grid-cols-4 gap-6'
  }
  return 'grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5 gap-6'
})

// 动态计算当前网格布局的列数
const getCurrentGridColumns = () => {
  const size = coverSize.value
  const width = window.innerWidth

  if (size === 'sm') {
    if (width >= 1280) return 5 // xl
    if (width >= 1024) return 4 // lg
    if (width >= 640) return 3 // sm
    return 2 // default
  }

  if (size === 'lg') {
    if (width >= 1024) return 4 // lg
    if (width >= 768) return 3 // md
    if (width >= 640) return 2 // sm
    return 1 // default
  }

  // size === 'md' (default)
  if (width >= 1280) return 5 // xl
  if (width >= 1024) return 4 // lg
  if (width >= 768) return 3 // md
  if (width >= 640) return 2 // sm
  return 1 // default
}

// 动态计算应该加载的相册数量
const getDynamicLoadSize = () => {
  const columns = getCurrentGridColumns()
  const viewportHeight = window.innerHeight

  // 估算每个相册卡片的高度（基于封面尺寸）
  const cardHeight = coverSize.value === 'sm' ? 200 : coverSize.value === 'lg' ? 320 : 240
  const gap = 24 // gap-6 = 1.5rem = 24px

  // 计算一行的高度（卡片高度 + 间距）
  const rowHeight = cardHeight + gap

  // 计算视窗能显示多少行（加上一些缓冲）
  const visibleRows = Math.ceil(viewportHeight / rowHeight) + 2 // 多加载2行作为缓冲

  // 计算总共需要多少个相册（列数 × 行数）
  const totalNeeded = columns * visibleRows

  // 最少加载一个完整行，最多不超过50个（避免一次性加载太多）
  return Math.max(columns, Math.min(totalNeeded, 50))
}

const goToAlbum = (id: number) => {
  // 设置导航标志和时间戳，确保只有立即导航才能检测到
  sessionStorage.setItem('album-navigation-active', Date.now().toString())
  router.push(`/album/${id}`)
}

// 从相册详情返回时，让三张封面原图在列表页缩回到封面
const performAlbumBackTransitionIfNeeded = async () => {

  // 注意：不要在这里清理克隆元素，因为它们是在AlbumDetail.vue中刚刚创建的
  // 克隆元素应该在动画完成后才清理

  // 确保所有相册卡片的缩略图都是可见的（清理可能残留的隐藏状态）
  const albumCards = document.querySelectorAll('.photo-card[data-album-id]')
  albumCards.forEach(card => {
    const imgs = card.querySelectorAll('img')
    imgs.forEach(img => {
      img.style.visibility = ''
      img.style.pointerEvents = ''
      img.style.transition = ''
    })
    const overlays = card.querySelectorAll('.album-cover-overlay')
    overlays.forEach(overlay => {
      overlay.style.pointerEvents = ''
      overlay.style.opacity = ''
    })
  })

  const raw = sessionStorage.getItem('album-back-transition')
  if (!raw) {
    return
  }

  try {
    const data: { albumId: number; photoIds: number[] } = JSON.parse(raw)
    const albumId = data.albumId

    const coverKey = `album-cover-rects-${albumId}`
    const coverRaw = sessionStorage.getItem(coverKey)
    if (!coverRaw) {
      sessionStorage.removeItem('album-back-transition')
      return
    }

    const coverRects: Array<{ photoId: number; slot?: 'left' | 'rightTop' | 'rightBottom'; rect: { top: number; left: number; width: number; height: number } }> =
      JSON.parse(coverRaw)

    // 找到当前页面上从详情页带过来的克隆元素
    const clones = Array.from(
      document.querySelectorAll<HTMLElement>(`.album-back-clone[data-album-id="${albumId}"]`)
    )

    if (!clones.length) {
      sessionStorage.removeItem('album-back-transition')
      sessionStorage.removeItem('album-navigation-active')
      sessionStorage.removeItem('album-animation-performed')
      return
    }

    // 等待一帧，确保页面布局稳定
    // debug log removed
    await new Promise(resolve => requestAnimationFrame(resolve))
    // debug log removed

    // 获取目标相册卡片，用于动画计算
    const albumCard = document.querySelector<HTMLElement>(`.photo-card[data-album-id="${albumId}"]`)
    if (!albumCard) {
      sessionStorage.removeItem('album-back-transition')
      return
    }

    // 隐藏目标相册的封面缩略图，避免克隆动画时显示重复图片
    const originalTransforms = new Map<number, { transform: string; transition: string }>()
    const targetRects = new Map<number, DOMRect>()
    // 先准备 overlays 变量以便在函数后续使用
    let overlays: HTMLElement[] = []

    if (albumCard) {
      // 隐藏任何右下角蒙版，避免在动画结束时叠加显示
      overlays = Array.from(albumCard.querySelectorAll<HTMLElement>('.album-cover-overlay'))
      overlays.forEach(o => {
        o.style.pointerEvents = 'none'
        // 通过 opacity 隐藏，便于之后平滑淡入
        o.style.transition = 'none'
        o.style.opacity = '0'
      })

      // 对每个保存的 coverRects，计算目标 wrapper 的实际边界（忽略图片 hover 导致的位移）
      for (const entry of coverRects) {
        const { photoId, slot } = entry
        // 优先通过 slot 定位 wrapper，否则回退到通过 photoId 定位 img 的父容器
        let wrapper: HTMLElement | null = null
        if (slot) {
          wrapper = albumCard.querySelector<HTMLElement>(`[data-slot="${slot}"]`)
        }
        if (!wrapper) {
          const imgEl = albumCard.querySelector<HTMLImageElement>(`img[data-photo-id="${photoId}"]`)
          wrapper = imgEl ? (imgEl.parentElement as HTMLElement) : null
        }
        if (!wrapper) continue

        // 保存当前 img 的 inline transform/transition，以便动画后恢复
        const imgEl = wrapper.querySelector<HTMLImageElement>('img')
        if (imgEl) {
          originalTransforms.set(photoId, {
            transform: imgEl.style.transform || '',
            transition: imgEl.style.transition || ''
          })
          // 临时移除 img 的 transform/transition，确保测量到未浮动的位置
          imgEl.style.transition = 'none'
          imgEl.style.transform = 'none'
        }

        const rect = wrapper.getBoundingClientRect()
        targetRects.set(photoId, rect)
      }

      // 隐藏目标相册的封面缩略图（在我们已经计算好目标位置后）
      const imgs = Array.from(albumCard.querySelectorAll<HTMLImageElement>('img'))
      imgs.forEach(img => {
        img.style.visibility = 'hidden'
        img.style.pointerEvents = 'none'
        img.style.transition = 'none'
      })
    }

    // 准备蒙版克隆（overlayClones），用于在动画结束时平滑显示蒙版
    const overlayClones: HTMLElement[] = []
    overlays.forEach((overlay) => {
      const wrapper = overlay.closest<HTMLElement>('[data-slot]') || overlay.parentElement
      const imgEl = wrapper?.querySelector<HTMLImageElement>('img[data-photo-id]') || undefined
      const photoId = imgEl ? Number(imgEl.dataset.photoId || '0') : NaN
      const targetRect = targetRects.get(photoId) || coverRects.find((r) => r.photoId === photoId)?.rect
      if (!targetRect) return

      const ovClone = overlay.cloneNode(true) as HTMLElement
      ovClone.style.position = 'fixed'
      ovClone.style.top = `${targetRect.top}px`
      ovClone.style.left = `${targetRect.left}px`
      ovClone.style.width = `${targetRect.width}px`
      ovClone.style.height = `${targetRect.height}px`
      ovClone.style.zIndex = '10001'
      ovClone.style.pointerEvents = 'none'
      ovClone.style.opacity = '0'
      ovClone.style.transition = 'opacity 160ms cubic-bezier(.2,.9,.3,1), transform 380ms cubic-bezier(.2,.9,.3,1)'
      ovClone.style.transform = 'scale(0.98)'
      ovClone.classList.add('album-back-overlay-clone')
      document.body.appendChild(ovClone)
      overlayClones.push(ovClone)
    })

    // 为每个克隆设置目标位置（使用刚计算的目标位置），开始缩回动画
    clones.forEach((clone) => {
      const photoId = Number(clone.dataset.photoId || '0')
      const targetRect = targetRects.get(photoId) || coverRects.find((r) => r.photoId === photoId)?.rect
      if (!targetRect) return

      // 根据 slot 或 photoId 决定最终的圆角样式，避免中间交界处出现不一致的圆角
      const coverEntry = coverRects.find((r) => r.photoId === photoId)
      const slot = coverEntry?.slot
      if (slot === 'left') {
        clone.style.borderRadius = '8px 0 0 8px'
      } else if (slot === 'rightTop') {
        clone.style.borderRadius = '0 8px 0 0'
      } else if (slot === 'rightBottom') {
        clone.style.borderRadius = '0 0 8px 0'
      } else {
        clone.style.borderRadius = '8px'
      }

      requestAnimationFrame(() => {
        requestAnimationFrame(() => {
          clone.style.top = `${targetRect.top}px`
          clone.style.left = `${targetRect.left}px`
          clone.style.width = `${targetRect.width}px`
          clone.style.height = `${targetRect.height}px`
        })
      })
    })

    // 动画结束后清理克隆和临时状态，并恢复封面缩略图显示（并恢复原先内联 transform）
    setTimeout(() => {
      // 在移除图片克隆前，先让蒙版克隆淡入（与图片克隆同步或略微滞后）
      overlayClones.forEach((ov) => {
        // 使用微小延迟让蒙版在图片到位时出现
        setTimeout(() => {
          ov.style.opacity = '1'
          ov.style.transform = 'scale(1)'
        }, 420)
      })

      // 同步让原始列表页中的 overlay 也缓慢淡入，避免在克隆移除时产生闪烁
      if (overlays.length > 0) {
        overlays.forEach(o => {
          setTimeout(() => {
            o.style.transition = 'opacity 160ms ease'
            o.style.opacity = '1'
          }, 420)
        })
      }

      // 在蒙版淡入后短暂保留，再移除所有克隆并恢复原有蒙版/图片
      setTimeout(() => {
        clones.forEach((clone) => clone.remove())
        overlayClones.forEach((ov) => ov.remove())
        sessionStorage.removeItem('album-back-transition')
        // 返回后可以清理这次点击生成的封面数据，避免后续干扰
        sessionStorage.removeItem(coverKey)
        // 清理导航和动画标志
        sessionStorage.removeItem('album-navigation-active')
        sessionStorage.removeItem('album-animation-performed')

        if (albumCard) {
          // 恢复并启用 overlay（如果有）——使用之前保存的 overlays 列表
          overlays.forEach(o => {
            o.style.pointerEvents = ''
          })

          const imgs = Array.from(albumCard.querySelectorAll<HTMLImageElement>('img'))
          imgs.forEach(img => {
            // 恢复原先内联 transform/transition（如果我们保存过）
            const pid = Number(img.dataset.photoId || '0')
            const saved = originalTransforms.get(pid)
            if (saved) {
              img.style.transform = saved.transform
              img.style.transition = saved.transition
            } else {
              img.style.transform = ''
              img.style.transition = ''
            }

            img.style.visibility = ''
            img.style.pointerEvents = ''
          })
        }
      }, 620)
    }, 0)
  } catch (e) {
    console.error('执行相册返回封面动画失败:', e)
    sessionStorage.removeItem('album-back-transition')
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
    const cat = activeCategory.value === '全部' ? undefined : activeCategory.value

    // 无限滚动时加载当前显示列数的2倍作为增量
    const loadSize = Math.max(12, getCurrentGridColumns() * 2)

    // 通过 photoStore 加载更多数据，确保排序一致性
    const data = await photoStore.fetchAlbums(currentPage.value, loadSize, cat, albumSortOrder.value, false)

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

// 节流滚动处理，优化滚动性能
let scrollThrottleTimer: ReturnType<typeof setTimeout> | null = null
let lastScrollCheck = 0
const SCROLL_THROTTLE_MS = 16 // ~60fps
const LOAD_THRESHOLD = 800 // 距离底部800px时开始加载

const handleScroll = () => {
  const now = Date.now()

  // 节流控制：确保不会过于频繁执行
  if (now - lastScrollCheck < SCROLL_THROTTLE_MS) return
  lastScrollCheck = now

  // 清除之前的定时器
  if (scrollThrottleTimer) {
    clearTimeout(scrollThrottleTimer)
  }

  // 使用微任务延迟执行，避免在滚动过程中阻塞UI
  scrollThrottleTimer = setTimeout(() => {
    const scrollTop = window.scrollY || document.documentElement.scrollTop
    const windowHeight = window.innerHeight
    const documentHeight = document.documentElement.scrollHeight

    // 距离底部LOAD_THRESHOLD像素时开始加载
    if (scrollTop + windowHeight >= documentHeight - LOAD_THRESHOLD) {
      loadMore()
    }
  }, 0)
}

// 获取相册排序设置
const loadAlbumSortOrder = async () => {
  try {
    const response = await api.get('/admin/config/album-sort-order')
    const newSort = response.data.albumSortOrder || 'name_asc'
    albumSortOrder.value = newSort
  } catch (error) {
    console.error('获取相册排序设置失败:', error)
    albumSortOrder.value = 'name_asc'
  }
}

onMounted(async () => {
  try {
    await Promise.all([
      photoStore.fetchCategories(),
      loadAlbumSortOrder()
    ])

    const loadSize = getDynamicLoadSize()
    const data = await photoStore.fetchAlbums(0, loadSize, undefined, albumSortOrder.value)
    hasMore.value = !data.last
    window.addEventListener('scroll', handleScroll, { passive: true })

    // 首次挂载时也尝试执行一次返回动画（例如刷新后从浏览器返回）
    nextTick(() => {
      performAlbumBackTransitionIfNeeded()
    })
  } catch (error) {
    console.error('初始化加载失败:', error)
    hasMore.value = false
  }
})

onUnmounted(() => {
  window.removeEventListener('scroll', handleScroll)
  if (scrollThrottleTimer) clearTimeout(scrollThrottleTimer)
})

onActivated(() => {
  // debug log removed

  // 恢复用户离开时的滚动位置
  requestAnimationFrame(() => {
    // debug log removed
    window.scrollTo({ top: savedScrollTop.value, left: 0, behavior: 'instant' as ScrollBehavior })
  })

  window.addEventListener('scroll', handleScroll, { passive: true })

  // 每次从 Album 返回激活 Home 时，检查并执行封面缩回动画
  // 使用单个 nextTick 确保页面完全渲染后再开始动画
  // debug log removed
  nextTick(() => {
    // debug log removed
    performAlbumBackTransitionIfNeeded()
  })
})

onDeactivated(() => {
  savedScrollTop.value = window.scrollY || 0
  window.removeEventListener('scroll', handleScroll)
})

const selectCategory = async (c: string) => {
  activeCategory.value = c
  currentPage.value = 0
  hasMore.value = true
  const cat = c === '全部' ? undefined : c
  const loadSize = getDynamicLoadSize()
  const data = await photoStore.fetchAlbums(0, loadSize, cat, albumSortOrder.value)
  hasMore.value = !data.last
}
</script>

