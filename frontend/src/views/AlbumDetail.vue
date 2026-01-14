<template>
  <div class="min-h-screen transition-colors duration-1000" :style="backgroundStyle">
    <nav class="fixed top-4 right-4 z-50">
      <!-- multi-select toolbar (placed before X so X stays at far right) -->
      <div v-if="multiSelectActive" class="inline-flex items-center gap-2 mr-3">
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

      <button
        @click="handleBack"
        @mousemove="onBackButtonMouseMove"
        @mouseleave="onBackButtonMouseLeave"
        @mousedown="onBackButtonMouseDown"
        class="btn-back"
        ref="backButtonRef"
        aria-label="关闭"
        title="关闭"
      >
        <svg class="w-5 h-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
          <line x1="18" y1="6" x2="6" y2="18"></line>
          <line x1="6" y1="6" x2="18" y2="18"></line>
        </svg>
      </button>
    </nav>

    <main class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-12">
      <!-- download progress bar -->
      <div v-if="downloadInProgress" class="fixed left-0 right-0 top-0 z-50">
        <div class="h-1 bg-gray-200 dark:bg-gray-800 w-full">
          <div :style="{ width: downloadProgress + '%' }" class="h-1 bg-blue-600 transition-width duration-200"></div>
        </div>
      </div>
        <div v-if="album">
          <div class="mb-12">
            <h1 class="text-4xl font-light mb-4" :style="textStyle">{{ album.name }}</h1>
            <p v-if="album.description" class="mb-4" :style="{ ...textStyle, opacity: 0.8 }">{{ album.description }}</p>
            <p class="text-sm" :style="{ ...textStyle, opacity: 0.6 }">{{ album.photoCount }} 张照片</p>
          </div>

        <MasonryLayout
          :items="masonryItems"
          :column-count="columnCount"
          :gap="20"
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
                <div class="absolute bottom-0 left-0 right-0 p-4 text-white">
                  <p class="text-sm font-light">{{ photo.filename }}</p>
                </div>
              </div>
            </div>
          </template>
        </MasonryLayout>
      </div>
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
import { computed, onMounted, onUnmounted, ref, nextTick, type ComponentPublicInstance } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { usePhotoStore } from '@/stores/photo'
import { useUiSettings } from '@/composables/useUiSettings'
import { useThemeStore } from '@/stores/theme'
import PhotoViewer from '@/components/PhotoViewer.vue'
import AtmosphereEffects from '@/components/AtmosphereEffects.vue'
import MasonryLayout from '@/components/MasonryLayout.vue'

const route = useRoute()
const router = useRouter()
const photoStore = usePhotoStore()

const album = computed(() => photoStore.currentAlbum)
const photos = computed(() => photoStore.photos)

const { atmosphereEnabled, previewSize } = useUiSettings()

// 获取主题store
const themeStore = useThemeStore()

// 窗口宽度响应式（用于触发columnCount重新计算）
const windowWidth = ref(typeof window !== 'undefined' ? window.innerWidth : 1920)

// 监听窗口大小变化（实时响应）
const handleResize = () => {
  windowWidth.value = window.innerWidth
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

// 计算列数（响应式，与主页保持一致）
const columnCount = computed(() => {
  if (typeof window === 'undefined') return 3

  const width = windowWidth.value
  let count = 4 // 默认值 (md)

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
    // only trigger long press if not cancelled by movement
    if (!longPressCancelled) {
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

  // 在路由切换前清理动画状态，让组件卸载更快

  // 清理定时器
  if ((window as any).__albumTransitionCleanupTimer) {
    clearTimeout((window as any).__albumTransitionCleanupTimer)
    delete (window as any).__albumTransitionCleanupTimer
  }
  if ((window as any).__albumTransitionRemoveTimer) {
    clearTimeout((window as any).__albumTransitionRemoveTimer)
    delete (window as any).__albumTransitionRemoveTimer
  }

  // 清理临时克隆元素
  transitionClones.forEach(clone => {
    clone.remove()
  })
  transitionClones = []

  // 恢复所有照片的显示状态
  photoRefs.value.forEach((photoElement) => {
    photoElement.style.visibility = ''
    photoElement.style.pointerEvents = ''
    photoElement.style.transition = ''
  })

  // 启动返回动画并立即返回相册列表，由 Home 页面继续完成缩回到封面的效果
  startBackTransitionAndNavigate()
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
  const albumId = parseInt(route.params.id as string)
  const storageKey = `album-cover-rects-${albumId}`
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
      // 使用更快且非线性的 easing，使打开封面更有弹性且更快
      clone.style.transition = 'all 380ms cubic-bezier(0.22, 1, 0.36, 1)'
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

  const albumId = parseInt(route.params.id as string)
  const storageKey = `album-cover-rects-${albumId}`
  const storedData = sessionStorage.getItem(storageKey)
  const navigationFlag = sessionStorage.getItem('album-navigation-active')
  const animationPerformed = sessionStorage.getItem('album-animation-performed') === 'true'
  // 只有真正执行过展开动画的页面才能执行返回动画
  const shouldPerformBackTransition = animationPerformed

  // 如果没有动画执行记录，清理可能残留的数据并导航到主页
  if (!shouldPerformBackTransition) {
    sessionStorage.removeItem('album-back-transition')
    sessionStorage.removeItem('album-navigation-active')
    sessionStorage.removeItem('album-animation-performed')
    // 直接导航到主页，而不是使用router.back()（因为可能没有有效的历史记录）
    router.push('/')
    return
  }

  // 如果没有封面位置信息，直接跳转
  if (!storedData || photos.value.length === 0) {
    // no stored data — navigate to home
    router.push('/')
    return
  }

  // 如果不是从正常导航来的，直接跳转
  if (!shouldPerformBackTransition) {
    // not from navigation — navigate to home
    router.push('/')
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
        // 反向/返回克隆也使用更快且非线性的 easing，保持与打开动画一致
        clone.style.transition = 'all 380ms cubic-bezier(0.22, 1, 0.36, 1)'
        clone.style.willChange = 'transform, width, height, top, left'
        clone.classList.add('album-back-clone')
        clone.dataset.albumId = String(albumId)
        clone.dataset.photoId = String(photoId)

        document.body.appendChild(clone)
        usedPhotoIds.push(photoId)
      }

      // clones created

      // 如果没有创建任何克隆，直接跳转
      if (usedPhotoIds.length === 0) {
        router.back()
        return
      }

      // 记录本次返回动画需要用到的相册和照片 ID，供 Home 页继续执行缩回动画
      const backTransitionData = {
        albumId,
        photoIds: usedPhotoIds,
        scrollTop: currentScrollTop,
        scrollLeft: currentScrollLeft
      }
      sessionStorage.setItem('album-back-transition', JSON.stringify(backTransitionData))

      // session storage set

      // 立即跳转，让用户感觉响应更快
      router.back()

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
    router.push('/')
  }
}

onMounted(async () => {
  // 添加窗口大小监听（实时响应）
  window.addEventListener('resize', handleResize)

  const albumId = parseInt(route.params.id as string)
  const storageKey = `album-cover-rects-${albumId}`
  const storedData = sessionStorage.getItem(storageKey)
  
  // 如果有需要动画的图片，且是从正常导航来的，立即隐藏它们（在数据加载前）
  // 检查导航时间戳，确保只有最近的导航才能触发动画
  const navigationTimestamp = sessionStorage.getItem('album-navigation-active')
  const isFromNavigation = navigationTimestamp && (Date.now() - parseInt(navigationTimestamp)) < 5000 // 5秒内

  if (isFromNavigation) {
    // 从正常导航来，保持或设置动画标志
  } else {
    // 如果不是从导航来的（比如刷新），清除之前的动画状态标志
    sessionStorage.removeItem('album-animation-performed')
  }

  if (storedData && isFromNavigation) {
    try {
      const coverRects: Array<{ photoId: number }> = JSON.parse(storedData)
      const photoIdsToHide = coverRects.map(r => r.photoId)

      // 在数据加载前，先标记需要隐藏的图片
      transitionPhotoIds.value = photoIdsToHide
      isTransitioning.value = true
    } catch (e) {
      // 忽略解析错误
    }
  }
  
  // 清除可能遗留的上一相册图片，避免在加载新相册前闪现旧内容
  photoStore.photos = []
  photoStore.currentAlbum = null
  await photoStore.fetchAlbumById(albumId)

  // 根据相册类型决定加载策略
  const album = photoStore.currentAlbum

  // 一次性加载该相册的所有照片（不分页），以便在相册详情中完整展示
  await photoStore.fetchAllPhotosByAlbum(albumId)
  window.addEventListener('keydown', handleKeydown)
  // reference backButtonRef to satisfy linter (it's bound in template)
  void backButtonRef.value
  
  // 等待照片元素渲染完成
  await nextTick()

  if (isFromNavigation && transitionPhotoIds.value.length > 0) {
    // 如果有需要隐藏的图片，立即隐藏它们
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

  // 在开始封面动画之前，先准备剩余图片的动画数据
  let remainingIndex = 0
  photos.value.forEach((photo) => {
    if (!transitionPhotoIds.value.includes(photo.id)) {
      remainingPhotoIndexes.value.set(photo.id, remainingIndex++)
    }
  })

  const hasCoverTransition = isFromNavigation ? await performCoverTransition() : false

  // 如果成功执行了封面动画，标记动画已执行
  if (hasCoverTransition) {
    sessionStorage.setItem('album-navigation-active', 'expanded')
    sessionStorage.setItem('album-animation-performed', 'true')
  }

  // 如果没有封面动画，直接开始剩余图片动画
  if (!hasCoverTransition) {
    remainingPhotosVisible.value = true
  }
})

onUnmounted(() => {
  window.removeEventListener('keydown', handleKeydown)
  window.removeEventListener('resize', handleResize)
  // 注意：动画状态已经在 handleBack 中提前清理了，这里只需要处理可能遗漏的情况

  // 注意：不要在这里清理 album-cover-rects 数据，因为返回动画在 Home.vue 中执行，需要这些数据
  // 这些数据会在 Home.vue 的返回动画完成后清理

  // 清理动画相关标志（以防页面异常退出）
  sessionStorage.removeItem('album-navigation-active')
  sessionStorage.removeItem('album-animation-performed')

  // 清理可能遗留的动画定时器
  if ((window as any).__albumTransitionCleanupTimer) {
    clearTimeout((window as any).__albumTransitionCleanupTimer)
    ;(window as any).__albumTransitionCleanupTimer = null
  }
})

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
  console.log('图片加载完成')
}

const getBrightness = (hex: string) => {
  const rgb = hexToRgb(hex)
  if (!rgb) return 0.5

  // 使用相对亮度公式
  return (rgb.r * 0.299 + rgb.g * 0.587 + rgb.b * 0.114) / 255
}
</script>

