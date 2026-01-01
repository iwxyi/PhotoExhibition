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
              class="p-2 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-800 transition-colors"
            >
              <svg v-if="!themeStore.isDark" class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M20.354 15.354A9 9 0 018.646 3.646 9.003 9.003 0 0012 21a9.003 9.003 0 008.354-5.646z" />
              </svg>
              <svg v-else class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 3v1m0 16v1m9-9h-1M4 12H3m15.364 6.364l-.707-.707M6.343 6.343l-.707-.707m12.728 0l-.707.707M6.343 17.657l-.707.707M16 12a4 4 0 11-8 0 4 4 0 018 0z" />
              </svg>
            </button>
            <FilterPanel v-model:show="showFilter" />
            <SettingsMenu />
          </div>
        </div>
      </div>
    </nav>

    <!-- 相册网格 -->
    <main class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-12">
      <!-- 分类 Tabs -->
      <div class="mb-6">
        <div class="flex gap-2 overflow-x-auto pb-2">
          <button
            v-for="c in ['全部', ...categories]"
            :key="c"
            @click="selectCategory(c)"
            class="px-4 py-2 rounded-full border transition-colors"
            :class="c === activeCategory
              ? 'bg-gray-900 text-white border-gray-800 dark:bg-white dark:text-gray-900 dark:border-white'
              : 'bg-gray-100 text-gray-800 border-gray-300 hover:bg-gray-200 dark:bg-gray-800 dark:text-gray-100 dark:border-gray-700 dark:hover:bg-gray-700'"
          >
            {{ c }}
          </button>
        </div>
      </div>

      <div v-if="loading && albums.length === 0" class="flex justify-center items-center h-96">
        <div class="animate-spin rounded-full h-12 w-12 border-t-2 border-b-2 border-gray-900 dark:border-white"></div>
      </div>

      <div
        v-if="albums.length > 0"
        :class="coverGridClass"
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
import { ref, onMounted, onUnmounted, onActivated, onDeactivated, nextTick, computed } from 'vue'
import { useRouter } from 'vue-router'
import { usePhotoStore } from '@/stores/photo'
import { useThemeStore } from '@/stores/theme'
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
const { coverSize } = useUiSettings()
const coverGridClass = computed(() => {
  if (coverSize.value === 'sm') {
    return 'grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5 gap-6'
  }
  if (coverSize.value === 'lg') {
    return 'grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-3 xl:grid-cols-4 gap-6'
  }
  return 'grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5 gap-6'
})

const goToAlbum = (id: number) => {
  router.push(`/album/${id}`)
}

// 从相册详情返回时，让三张封面原图在列表页缩回到封面
const performAlbumBackTransitionIfNeeded = async () => {
  console.log('[Home] performAlbumBackTransitionIfNeeded called at', Date.now())

  const raw = sessionStorage.getItem('album-back-transition')
  if (!raw) {
    console.log('[Home] No album-back-transition data found')
    return
  }

  try {
    const data: { albumId: number; photoIds: number[] } = JSON.parse(raw)
    const albumId = data.albumId

    console.log('[Home] Processing transition for album', albumId, 'at', Date.now())

    const coverKey = `album-cover-rects-${albumId}`
    const coverRaw = sessionStorage.getItem(coverKey)
    if (!coverRaw) {
      console.log('[Home] No cover rects found for album', albumId)
      sessionStorage.removeItem('album-back-transition')
      return
    }

    const coverRects: Array<{ photoId: number; slot?: 'left' | 'rightTop' | 'rightBottom'; rect: { top: number; left: number; width: number; height: number } }> =
      JSON.parse(coverRaw)

    console.log('[Home] Found cover rects:', coverRects.length, 'at', Date.now())

    // 找到当前页面上从详情页带过来的克隆元素
    const clones = Array.from(
      document.querySelectorAll<HTMLElement>(`.album-back-clone[data-album-id="${albumId}"]`)
    )
    console.log('[Home] Looking for clones with selector:', `.album-back-clone[data-album-id="${albumId}"]`)
    console.log('[Home] All album-back-clone elements:', document.querySelectorAll('.album-back-clone'))
    console.log('[Home] Found clones:', clones.length, 'at', Date.now())

    if (!clones.length) {
      console.log('[Home] No clones found for album', albumId)
      sessionStorage.removeItem('album-back-transition')
      return
    }

    // 等待一帧，确保页面布局稳定
    console.log('[Home] Waiting for requestAnimationFrame...')
    await new Promise(resolve => requestAnimationFrame(resolve))
    console.log('[Home] requestAnimationFrame resolved at', Date.now())

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
      ovClone.style.transition = 'opacity 160ms ease, transform 160ms ease'
      ovClone.style.transform = 'scale(0.98)'
      ovClone.classList.add('album-back-overlay-clone')
      document.body.appendChild(ovClone)
      overlayClones.push(ovClone)
    })

    console.log('[Home] Starting animation for', clones.length, 'clones at', Date.now())

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
          console.log('[Home] Animation started for clone', photoId, 'at', Date.now())
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
    
    // 直接调用 API，不通过 store，避免触发 loading
    const { api } = await import('@/api')
    const params: any = { page: currentPage.value, size: 12 }
    if (cat) params.category = cat
    const response = await api.get('/albums', { params })
    const data = response.data
    
    if (!data || !data.content || data.content.length === 0) {
      hasMore.value = false
      return
    }
    
    // 通过 store 追加数据，但不触发 loading
    // 直接操作 store 的 albums ref
    const currentAlbums = [...photoStore.albums]
    photoStore.albums = [...currentAlbums, ...data.content]
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
    await photoStore.fetchCategories()
    const data = await photoStore.fetchAlbums(0, 12, undefined)
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
  if (scrollTimer) clearTimeout(scrollTimer)
})

onActivated(() => {
  console.log('[Home] onActivated called at', Date.now())

  // 恢复用户离开时的滚动位置
  requestAnimationFrame(() => {
    console.log('[Home] Restoring scroll position to', savedScrollTop.value, 'at', Date.now())
    window.scrollTo({ top: savedScrollTop.value, left: 0, behavior: 'instant' as ScrollBehavior })
  })

  window.addEventListener('scroll', handleScroll, { passive: true })

  // 每次从 Album 返回激活 Home 时，检查并执行封面缩回动画
  // 使用单个 nextTick 确保页面完全渲染后再开始动画
  console.log('[Home] Scheduling performAlbumBackTransitionIfNeeded at', Date.now())
  nextTick(() => {
    console.log('[Home] nextTick callback, calling performAlbumBackTransitionIfNeeded at', Date.now())
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
  const data = await photoStore.fetchAlbums(0, 12, cat)
  hasMore.value = !data.last
}
</script>

