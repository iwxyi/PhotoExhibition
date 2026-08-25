<template>
  <div class="min-h-screen bg-stone-50 dark:bg-[#111111]">
    <!-- 导航栏 -->
    <nav
      class="sticky top-0 z-50 bg-white/80 dark:bg-gray-900/80 backdrop-blur-md border-b border-gray-200 dark:border-gray-800 safe-area-inset-top transition-transform duration-300 ease-in-out transform-gpu"
      :class="{ '-translate-y-full': isMobile && navHidden }"
      style="padding-top: env(safe-area-inset-top);"
    >
      <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div class="flex justify-between items-center h-12">
          <AppHeader :show-nav-links="!isMobile" />
          <div class="flex items-center space-x-3">
            <SearchSpotlight />
            <PublicAccountMenu />
          </div>
        </div>
      </div>
    </nav>


    <!-- 相册网格 -->
    <main class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 pt-7 pb-14 md:pt-8 md:pb-16">
      <template v-if="showPublicPortal">
        <section class="py-10 md:py-14">
          <div class="max-w-4xl">
            <p class="text-xs uppercase tracking-[0.28em] text-sky-500/80 mb-3">Multi Site Portal</p>
            <h1 class="text-3xl md:text-5xl font-light text-gray-900 dark:text-white tracking-tight">选择要进入的公开站点</h1>
            <p class="mt-4 max-w-2xl text-sm md:text-base text-gray-500 dark:text-gray-400 leading-7">
              当前已开启多用户模式。请选择一个公开站点进入，后续页面都会使用 `/{slug}` 前缀隔离访问范围。
            </p>
          </div>

          <div v-if="loadingPublicUsers" class="flex justify-center items-center h-72">
            <div class="animate-spin rounded-full h-12 w-12 border-t-2 border-b-2 border-gray-900 dark:border-white"></div>
          </div>

          <div v-else-if="publicUsers.length > 0" class="mt-10 grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-3 gap-5">
            <button
              v-for="user in publicUsers"
              :key="user.userId"
              type="button"
              class="group text-left rounded-3xl border border-gray-200/80 dark:border-gray-800 bg-gradient-to-br from-white to-slate-50 dark:from-gray-900 dark:to-gray-950 p-5 shadow-sm hover:shadow-xl hover:-translate-y-0.5 transition-all"
              @click="enterPublicSite(user.slug)"
            >
              <div class="flex items-center gap-4">
                <div class="h-14 w-14 rounded-2xl overflow-hidden bg-sky-100 dark:bg-sky-950/60 flex items-center justify-center text-sky-700 dark:text-sky-200 font-semibold text-lg">
                  <img v-if="user.avatarPath" :src="user.avatarPath" alt="avatar" class="h-full w-full object-cover" />
                  <span v-else>{{ (user.nickname || user.projectNameZh || user.projectNameEn || user.username || 'U').slice(0, 1).toUpperCase() }}</span>
                </div>
                <div class="min-w-0">
                  <div class="text-lg text-gray-900 dark:text-white truncate">{{ user.projectNameZh || user.projectNameEn || user.nickname || user.username }}</div>
                  <div class="mt-1 text-xs text-gray-500 dark:text-gray-400 truncate">/{{ user.slug }}</div>
                </div>
              </div>
              <div class="mt-5 text-sm text-gray-600 dark:text-gray-300 line-clamp-2 min-h-[40px]">
                {{ user.projectNameEn || user.nickname || `进入 ${user.username} 的公开相册站点` }}
              </div>
              <div class="mt-6 inline-flex items-center gap-2 text-sm text-sky-600 dark:text-sky-300">
                <span>进入站点</span>
                <span class="transition-transform group-hover:translate-x-1">→</span>
              </div>
            </button>
          </div>

          <div v-else class="mt-10 rounded-3xl border border-dashed border-gray-300 dark:border-gray-700 px-6 py-14 text-center text-gray-500 dark:text-gray-400">
            当前还没有可公开访问的用户站点
          </div>
        </section>
      </template>

      <template v-else>
        <!-- 分类 Tabs -->
        <CategoryTabs
          :selected-category="activeCategory"
          :categories="categories"
          @category-changed="selectCategory"
        />

        <div v-if="loading && albums.length === 0" class="flex justify-center items-center h-96">
          <div class="animate-spin rounded-full h-12 w-12 border-t-2 border-b-2 border-gray-900 dark:border-white"></div>
        </div>

        <section v-if="albums.length > 0" class="pt-1 md:pt-2">
        <div
          :class="coverGridClass"
          style="contain: layout style; will-change: auto;"
        >
          <AlbumCard
            v-for="album in albums"
            :key="album.id"
            :album="album"
            :size="coverSize"
            @click="goToAlbum(album.id)"
          />
        </div>
        </section>

        <!-- 加载状态 -->
        <div v-if="isLoadingMore && albums.length > 0" class="mt-12 text-center">
          <div class="animate-spin rounded-full h-12 w-12 border-t-2 border-b-2 border-gray-900 dark:border-white mx-auto"></div>
        </div>
        <div v-if="!hasMore && albums.length > 0" class="mt-12 text-center text-gray-500 dark:text-gray-400">
          <p>已加载全部相册</p>
        </div>
      </template>
    </main>

    <!-- 移动端底部导航栏 -->
    <MobileBottomNav v-if="isMobile" />
  </div>
</template>

<script setup lang="ts">
defineOptions({ name: 'Home' })
import { ref, onMounted, onUnmounted, onActivated, onDeactivated, nextTick, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { buildPublicPath } from '@/utils/publicRoute'
import { useAuthStore } from '@/stores/auth'
import { usePhotoStore } from '@/stores/photo'
import { useThemeStore } from '@/stores/theme'
import { api, publicUserApi, type PublicUserProfile } from '@/api'
import AlbumCard from '@/components/AlbumCard.vue'
import FilterPanel from '@/components/FilterPanel.vue'
import AppHeader from '@/components/AppHeader.vue'
import PublicAccountMenu from '@/components/PublicAccountMenu.vue'
import SearchSpotlight from '@/components/SearchSpotlight.vue'
import { useUiSettings } from '@/composables/useUiSettings'
import { useMobileNav } from '@/composables/useMobileNav'
import { useNavAutoHide } from '@/composables/useNavAutoHide'
import { sortCategories, loadCategorySortOrder } from '@/composables/useCategorySorting'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const photoStore = usePhotoStore()
const themeStore = useThemeStore()
import NavLinks from '@/components/NavLinks.vue'
import MobileBottomNav from '@/components/MobileBottomNav.vue'
import CategoryTabs from '@/components/CategoryTabs.vue'

const albums = computed(() => photoStore.albums)
const loading = computed(() => photoStore.loading)
const categories = computed(() => sortCategories(photoStore.categories))
const showFilter = ref(false)
const currentPage = ref(0)
const hasMore = ref(true)
const activeCategory = ref('全部')
const savedScrollTop = ref(0)
const isLoadingMore = ref(false)
const albumSortOrder = ref('name_asc')
const { coverSize } = useUiSettings()
const { isMobile } = useMobileNav()
const { isHidden: navHidden } = useNavAutoHide()
const publicMultiUserEnabled = ref(false)
const loadingPublicUsers = ref(false)
const publicUsers = ref<PublicUserProfile[]>([])
const currentUserSlug = computed(() => typeof route.params.userSlug === 'string' ? route.params.userSlug : null)
const showPublicPortal = computed(() => publicMultiUserEnabled.value && !currentUserSlug.value)

// 预加载缓冲区状态
const preloadBuffer = ref<any[]>([])
const isPreloading = ref(false)
const isInitialized = ref(false)  // 标记是否已初始化完成

// 监听排序设置变化，重新获取数据（只处理用户手动更改，不处理初始化）
watch(albumSortOrder, async (newSort, oldSort) => {
  if (isInitialized.value && newSort !== oldSort) {
    // 重新获取相册数据
    currentPage.value = 0
    hasMore.value = true
    // 清空预加载缓冲区
    preloadBuffer.value = []
    const loadSize = getDynamicLoadSize()
    const cat = activeCategory.value === '全部' ? undefined : activeCategory.value
    const data = await photoStore.fetchAlbums(0, loadSize, cat, newSort)
    hasMore.value = !data.last
    // 切换排序时滚动到顶部
    window.scrollTo({ top: 0, behavior: 'instant' })
  }
})
const coverGridClass = computed(() => {
  if (coverSize.value === 'sm') {
    // 小尺寸：更多列数（适合小封面）
    return 'grid grid-cols-3 sm:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6 gap-3'
  }
  if (coverSize.value === 'md') {
    // 中等尺寸：中等列数
    return 'grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 gap-4'
  }
  if (coverSize.value === 'lg') {
    // 大尺寸：较少列数（适合大封面）
    return 'grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-5'
  }
  return 'grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 gap-4'
})

// 动态计算当前网格布局的列数
const getCurrentGridColumns = () => {
  const size = coverSize.value
  const width = window.innerWidth

  if (size === 'sm') {
    // 小尺寸：更多列数
    if (width >= 1280) return 6 // xl
    if (width >= 1024) return 5 // lg
    if (width >= 768) return 4 // md
    if (width >= 640) return 3 // sm
    return 3 // default (手机上3列)
  }

  if (size === 'md') {
    // 中等尺寸：中等列数
    if (width >= 1280) return 5 // xl
    if (width >= 1024) return 4 // lg
    if (width >= 768) return 3 // md
    if (width >= 640) return 2 // sm
    return 2 // default (手机上2列)
  }

  if (size === 'lg') {
    // 大尺寸：较少列数
    if (width >= 1280) return 4 // xl
    if (width >= 1024) return 3 // lg
    if (width >= 768) return 2 // md
    if (width >= 640) return 1 // sm
    return 1 // default (手机上1列)
  }

  // 默认 md
  if (width >= 1280) return 5 // xl
  if (width >= 1024) return 4 // lg
  if (width >= 768) return 3 // md
  if (width >= 640) return 2 // sm
  return 2 // default
}

// 动态计算应该加载的相册数量
const getDynamicLoadSize = () => {
  const columns = getCurrentGridColumns()
  const viewportHeight = window.innerHeight

  // 估算每个相册卡片的高度（基于封面尺寸）
  const cardHeight = coverSize.value === 'sm' ? 160 : coverSize.value === 'md' ? 240 : coverSize.value === 'lg' ? 320 : 240
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
  // 保存来源页面（当前完整路径），用于返回时判断
  sessionStorage.setItem('album-entry-page', window.location.pathname)
  // 设置导航标志和时间戳，确保只有立即导航才能检测到
  sessionStorage.setItem('album-navigation-active', Date.now().toString())
  // 使用短路由 /a/ID
  router.push(buildPublicPath(`/a/${id}`))
}

const enterPublicSite = (slug: string) => {
  router.push(`/${slug}`)
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
    const overlays = card.querySelectorAll<HTMLElement>('.album-cover-overlay')
    overlays.forEach(overlay => {
      overlay.style.pointerEvents = ''
      overlay.style.opacity = ''
    })
  })

  const raw = sessionStorage.getItem('album-back-transition')
  console.log('[Home] 检测返回动画数据:', { hasData: !!raw })
  if (!raw) {
    return
  }

  try {
    const data: { albumId: number; photoIds: number[]; coverRects: any[] } = JSON.parse(raw)
    const albumId = data.albumId
    const coverRects = data.coverRects || []

    console.log('[Home] 返回动画数据:', { albumId, coverRectsCount: coverRects.length })

    if (coverRects.length === 0) {
      sessionStorage.removeItem('album-back-transition')
      sessionStorage.removeItem('album-animation-performed')
      return
    }

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

    // 立即开始动画，不需要等待（路由切换已经完成，DOM 已就绪）
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
      // 通过 data-photo-id 定位 wrapper
        let wrapper: HTMLElement | null = null
      wrapper = albumCard.querySelector<HTMLElement>(`[data-photo-id="${photoId}"]`)
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
      // 使用轻微回弹效果的 ease-out 曲线
      ovClone.style.transition = 'opacity 120ms cubic-bezier(0.34, 1.56, 0.64, 1), transform 340ms cubic-bezier(0.34, 1.4, 0.63, 1)'
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
          clone.style.top = `${targetRect.top}px`
          clone.style.left = `${targetRect.left}px`
          clone.style.width = `${targetRect.width}px`
          clone.style.height = `${targetRect.height}px`
      })
    })

    // 动画结束后清理克隆和临时状态，并恢复封面缩略图显示（并恢复原先内联 transform）
    // 动画时长是 320ms，动画结束立即恢复交互
    setTimeout(() => {
      // 同时淡入 overlay 克隆并恢复原 overlay，让用户感觉是连续的
      overlayClones.forEach((ov) => {
          ov.style.opacity = '1'
        ov.style.transform = 'scale(1) translateY(0)'
      })

      // 立即恢复原 overlay 的交互（不需要等待克隆淡入完成）
      if (albumCard) {
        // 强制重新查询 overlay 元素（因为 Vue 可能已经重新渲染了）
        const restoredOverlays = albumCard.querySelectorAll<HTMLElement>('.album-cover-overlay')
        restoredOverlays.forEach(o => {
          o.style.pointerEvents = ''
            o.style.opacity = '1'
          // 移除任何残留的 transform，让 overlay 立即就位
          o.style.transform = ''
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

      // 短暂延迟后移除克隆元素（不需要等待淡入完成）
      requestAnimationFrame(() => {
        requestAnimationFrame(() => {
          clones.forEach((clone) => clone.remove())
          overlayClones.forEach((ov) => ov.remove())

          // 动画完成后恢复滚动位置
          const savedPos = sessionStorage.getItem('home-scroll-position')
          if (savedPos) {
            const scrollY = parseInt(savedPos, 10)
            window.scrollTo({ top: scrollY, left: 0, behavior: 'instant' as ScrollBehavior })
            savedScrollTop.value = scrollY
          }

          sessionStorage.removeItem('album-back-transition')
          sessionStorage.removeItem('album-animation-performed')
        })
      })
    }, 320)
  } catch (e) {
    console.error('执行相册返回封面动画失败:', e)
    sessionStorage.removeItem('album-back-transition')
  }
}

// 预加载下一页数据到缓冲区
const preloadNextPage = async () => {
  if (showPublicPortal.value) return
  if (isPreloading.value || !hasMore.value || preloadBuffer.value.length > 0) return

  try {
    isPreloading.value = true
    const nextPage = currentPage.value + 1
    const cat = activeCategory.value === '全部' ? undefined : activeCategory.value
    const loadSize = Math.max(12, getCurrentGridColumns() * 2)

    const data = await photoStore.fetchAlbums(nextPage, loadSize, cat, albumSortOrder.value, false)

    if (data && data.content && data.content.length > 0) {
      preloadBuffer.value = data.content
      // 更新hasMore状态
      hasMore.value = !data.last
    } else {
      hasMore.value = false
    }
  } catch (error) {
    console.error('预加载失败:', error)
    hasMore.value = false
  } finally {
    isPreloading.value = false
  }
}

const loadMore = async () => {
  if (showPublicPortal.value) return
  // 防止重复加载
  if (loading.value || isLoadingMore.value || !hasMore.value) return

  try {
    isLoadingMore.value = true
    currentPage.value++

    let newAlbums: any[] = []

    // 优先使用预加载的缓冲区数据
    if (preloadBuffer.value.length > 0) {
      newAlbums = [...preloadBuffer.value]
      preloadBuffer.value = []
      hasMore.value = true // 假设还有更多数据
    } else {
      // 如果没有预加载数据，则直接加载
    const cat = activeCategory.value === '全部' ? undefined : activeCategory.value
    const loadSize = Math.max(12, getCurrentGridColumns() * 2)

    const data = await photoStore.fetchAlbums(currentPage.value, loadSize, cat, albumSortOrder.value, false)

    if (!data || !data.content || data.content.length === 0) {
      hasMore.value = false
        currentPage.value--
      return
    }

      newAlbums = data.content
    hasMore.value = !data.last
    }

    // 将新数据添加到相册列表
    photoStore.addAlbums(newAlbums)

    // 启动下一页的预加载
    if (hasMore.value) {
      setTimeout(() => preloadNextPage(), 100)
    }

    // 不主动恢复滚动位置，让浏览器自然处理
    // 新内容添加到底部，用户继续滚动时就能看到
  } catch (error) {
    console.error('加载更多失败:', error)
    // 加载失败时回退页码
    currentPage.value--
    hasMore.value = false
  } finally {
    isLoadingMore.value = false
  }
}

// 优化滚动处理：触摸板会产生高频 scroll，避免每帧写 sessionStorage。
let scrollRafId: number | null = null
let scrollSaveTimer: ReturnType<typeof setTimeout> | null = null
const LOAD_THRESHOLD = 1000 // 距离底部1000px时开始加载，增加缓冲区
const PRELOAD_THRESHOLD = 2000 // 距离底部2000px时开始预加载

const handleScroll = () => {
  if (showPublicPortal.value) return

  if (scrollRafId !== null) return

  scrollRafId = requestAnimationFrame(() => {
    scrollRafId = null
    const scrollTop = window.scrollY || document.documentElement.scrollTop
    const windowHeight = window.innerHeight
    const documentHeight = document.documentElement.scrollHeight

    savedScrollTop.value = scrollTop

    if (scrollSaveTimer) {
      clearTimeout(scrollSaveTimer)
    }
    scrollSaveTimer = setTimeout(() => {
      sessionStorage.setItem('home-scroll-position', String(savedScrollTop.value))
      scrollSaveTimer = null
    }, 180)

    // 距离底部PRELOAD_THRESHOLD像素时开始预加载
    if (scrollTop + windowHeight >= documentHeight - PRELOAD_THRESHOLD) {
      preloadNextPage()
    }

    // 距离底部LOAD_THRESHOLD像素时开始加载
    if (scrollTop + windowHeight >= documentHeight - LOAD_THRESHOLD) {
      loadMore()
    }
  })
}

// 获取相册排序设置
const loadAlbumSortOrder = async () => {
  try {
    const response = await api.get('/albums/sort-order')
    const newSort = response.data.albumSortOrder || 'name_asc'
    albumSortOrder.value = newSort
  } catch (error) {
    console.error('获取相册排序设置失败:', error)
    albumSortOrder.value = 'name_asc'
  }
}


onMounted(async () => {
  // 清理可能残留的克隆元素（处理直接URL进入后返回或页面刷新等情况）
  // 注意：不要清理 sessionStorage 数据，因为 performAlbumBackTransitionIfNeeded 需要用到它们
  // 使用 setTimeout 延迟清理，确保动画有足够时间运行
  setTimeout(() => {
    document.querySelectorAll('.album-back-clone, .album-back-overlay-clone').forEach(clone => {
      clone.remove()
    })
  }, 500)

  // 从 sessionStorage 恢复滚动位置（处理刷新后返回的情况）
  const savedPos = sessionStorage.getItem('home-scroll-position')
  if (savedPos) {
    savedScrollTop.value = parseInt(savedPos, 10)
    window.scrollTo({ top: savedScrollTop.value, left: 0, behavior: 'instant' as ScrollBehavior })
  }

  try {
    const publicSettings = await authStore.fetchPublicSettings().catch((error) => {
      console.error('加载公开设置失败:', error)
      return null
    })
    publicMultiUserEnabled.value = !!publicSettings?.multiUserEnabled

    if (showPublicPortal.value) {
      loadingPublicUsers.value = true
      try {
        const { data } = await publicUserApi.listUsers()
        publicUsers.value = data.users || []
      } finally {
        loadingPublicUsers.value = false
      }
      photoStore.albums = []
      hasMore.value = false
      isInitialized.value = true
      return
    }

    await Promise.all([
      photoStore.fetchCategories(),
      loadAlbumSortOrder(),
      loadCategorySortOrder()
    ])

    const loadSize = getDynamicLoadSize()
    const data = await photoStore.fetchAlbums(0, loadSize, undefined, albumSortOrder.value)
    hasMore.value = !data.last
    isInitialized.value = true  // 标记初始化完成，之后 watch 才会生效
    window.addEventListener('scroll', handleScroll, { passive: true })

    // 首次挂载时也尝试执行一次返回动画（例如刷新后从浏览器返回）
    // 使用 requestAnimationFrame 确保在渲染完成后执行
    requestAnimationFrame(() => {
      requestAnimationFrame(() => {
      performAlbumBackTransitionIfNeeded()
      })
    })
  } catch (error) {
    console.error('初始化加载失败:', error)
    hasMore.value = false
  }
})

onUnmounted(() => {
  window.removeEventListener('scroll', handleScroll)
  if (scrollRafId !== null) cancelAnimationFrame(scrollRafId)
  if (scrollSaveTimer) clearTimeout(scrollSaveTimer)
})

onActivated(() => {
  if (showPublicPortal.value) {
    if (!loadingPublicUsers.value && publicUsers.value.length === 0) {
      loadingPublicUsers.value = true
      publicUserApi.listUsers()
        .then(({ data }) => {
          publicUsers.value = data.users || []
        })
        .finally(() => {
          loadingPublicUsers.value = false
        })
    }
    return
  }
  // 恢复滚动位置 - 直接从 sessionStorage 读取
  const savedPos = sessionStorage.getItem('home-scroll-position')
  const scrollY = savedPos ? parseInt(savedPos, 10) : savedScrollTop.value
  window.scrollTo({ top: scrollY, left: 0, behavior: 'instant' as ScrollBehavior })

  // 重新添加滚动事件监听器（从 AlbumDetail 返回后需要重新添加）
  window.addEventListener('scroll', handleScroll, { passive: true })

  // 每次从 Album 返回激活 Home 时，检查并执行封面缩回动画
  // 使用 requestAnimationFrame 确保在下一帧执行，此时 DOM 已更新
  requestAnimationFrame(() => {
    requestAnimationFrame(() => {
      performAlbumBackTransitionIfNeeded()
    })
  })
})

onDeactivated(() => {
  if (showPublicPortal.value) {
    window.removeEventListener('scroll', handleScroll)
    return
  }
  // 保存滚动位置到 sessionStorage 和 ref
  const scrollY = window.scrollY || 0
  const existingPos = sessionStorage.getItem('home-scroll-position')
  // 如果已经有保存的滚动位置且当前是 0（说明是路由切换导致的），不要覆盖
  if (scrollY > 0 || !existingPos) {
    savedScrollTop.value = scrollY
    sessionStorage.setItem('home-scroll-position', String(scrollY))
  }
  window.removeEventListener('scroll', handleScroll)
  if (scrollRafId !== null) {
    cancelAnimationFrame(scrollRafId)
    scrollRafId = null
  }
  if (scrollSaveTimer) {
    clearTimeout(scrollSaveTimer)
    scrollSaveTimer = null
  }
})

const selectCategory = async (c: string) => {
  if (showPublicPortal.value) return
  activeCategory.value = c
  currentPage.value = 0
  hasMore.value = true
  // 清空预加载缓冲区
  preloadBuffer.value = []
  const cat = c === '全部' ? undefined : c
  const loadSize = getDynamicLoadSize()
  const data = await photoStore.fetchAlbums(0, loadSize, cat, albumSortOrder.value)
  hasMore.value = !data.last
  // 切换分类时滚动到顶部
  window.scrollTo({ top: 0, behavior: 'instant' })
}
</script>
