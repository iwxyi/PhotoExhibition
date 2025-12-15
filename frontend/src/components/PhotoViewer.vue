<template>
  <transition name="fade">
    <div
      v-if="visible"
      class="fixed inset-0 z-50 bg-black/90 backdrop-blur-sm flex flex-col outline-none focus:outline-none"
      @keydown.stop.prevent="onKeydown"
      tabindex="0"
      ref="modalRoot"
    >
      <!-- 顶部栏 -->
      <div v-if="!isFullscreen" class="flex items-center justify-between px-4 sm:px-6 py-3 text-white text-sm">
        <div class="flex items-center gap-3">
          <button class="p-2 hover:bg-white/10 rounded" @click="close" title="关闭">
            <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
          <div class="text-xs sm:text-sm opacity-80 flex items-center gap-2">
            <span>{{ currentPhoto?.filename }}</span>
          </div>
        </div>
        <div class="flex items-center gap-3">
          <button class="p-2 hover:bg-white/10 rounded" @click="toggleFullscreen" :title="isFullscreen ? '退出全屏' : '全屏查看'">
            <svg v-if="!isFullscreen" class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 8V4h4M4 4l6 6M20 16v4h-4m4 0l-6-6M16 4h4v4m0-4l-6 6M8 20H4v-4m0 4l6-6" />
            </svg>
            <svg v-else class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 9H5V5m10 10h4v4M9 15H5v4m10-10h4V5" />
            </svg>
          </button>
          <button class="p-2 hover:bg-white/10 rounded" @click="prev">←</button>
          <span class="text-xs sm:text-sm">{{ currentIndex + 1 }} / {{ photos.length }}</span>
          <button class="p-2 hover:bg-white/10 rounded" @click="next">→</button>
          <button
            class="p-2 hover:bg-white/10 rounded"
            @click="toggleInfo"
            :aria-pressed="!infoCollapsed"
            title="信息面板"
          >
            <svg v-if="infoCollapsed" class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 16h-1v-4h-1m1-4h.01M12 19a7 7 0 100-14 7 7 0 000 14z" />
            </svg>
            <svg v-else class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 16h-1v-4h-1m1-4h.01M12 19a7 7 0 100-14 7 7 0 000 14z" />
            </svg>
          </button>
        </div>
      </div>

      <!-- 全屏简化控制：左上关闭，右上全屏切换 -->
      <div v-else class="pointer-events-none">
        <div class="absolute left-4 top-4 z-50 pointer-events-auto">
          <button class="p-2 hover:bg-white/10 rounded text-white" @click="close" title="关闭">
            <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>
        <div class="absolute right-4 top-4 z-50 pointer-events-auto">
          <button class="p-2 hover:bg-white/10 rounded text-white" @click="toggleFullscreen" :title="isFullscreen ? '退出全屏' : '全屏查看'">
            <svg v-if="!isFullscreen" class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 8V4h4M4 4l6 6M20 16v4h-4m4 0l-6-6M16 4h4v4m0-4l-6 6M8 20H4v-4m0 4l6-6" />
            </svg>
            <svg v-else class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 9H5V5m10 10h4v4M9 15H5v4m10-10h4V5" />
            </svg>
          </button>
        </div>
      </div>

      <div class="flex-1 flex overflow-hidden min-h-0">
        <!-- 主图区域 -->
        <div class="flex-1 flex items-center justify-center relative px-2 sm:px-6 min-h-0">
          <div class="relative w-full h-full flex items-center justify-center overflow-hidden" ref="imageContainer">
            <div
              class="relative inline-block"
              :style="getImageTransformStyle()"
              @wheel="onWheelZoom"
              @touchstart="onTouchStartZoom"
              @touchmove="onTouchMoveZoom"
              @touchend="onTouchEndZoom"
              @dblclick="onDoubleClick"
              @mousedown="onMouseDown"
            >
              <img
                v-if="currentPhoto"
                :src="getImageUrl(currentPhoto)"
                :alt="currentPhoto.filename"
                class="select-none"
                :style="getImageStyle()"
                ref="mainImage"
                @load="onImageLoad"
                draggable="false"
              />
            </div>
            <!-- 焦点框覆盖层 -->
            <div
              v-if="currentPhoto && showFocusBox && imageLoaded && currentPhoto.focusX !== undefined && currentPhoto.focusY !== undefined"
              class="absolute pointer-events-none"
              :style="getFocusBoxStyle()"
            >
              <div class="absolute inset-0 border-2 border-yellow-400 shadow-lg shadow-yellow-400/50"></div>
              <div class="absolute -top-6 left-0 text-xs text-yellow-400 bg-black/60 px-1 rounded whitespace-nowrap">
                焦点 ({{ currentPhoto.focusX.toFixed(1) }}%, {{ currentPhoto.focusY.toFixed(1) }}%)
              </div>
            </div>
          </div>

          <!-- 左右切换按钮 -->
          <button
            v-if="!isFullscreen"
            class="absolute left-2 sm:left-6 top-1/2 -translate-y-1/2 p-3 rounded-full bg-black/40 text-white hover:bg-black/60"
            @click="prev"
          >
            ‹
          </button>
          <button
            v-if="!isFullscreen"
            class="absolute right-2 sm:right-6 top-1/2 -translate-y-1/2 p-3 rounded-full bg-black/40 text-white hover:bg-black/60"
            @click="next"
          >
            ›
          </button>
        </div>

        <!-- 信息侧栏 -->
        <transition name="slide-fade">
          <div
            v-if="!infoCollapsed"
            class="w-80 max-w-[80vw] bg-gray-900/80 text-white border-l border-white/10 flex flex-col max-h-full overflow-auto"
          >
            <div class="flex items-center justify-between px-4 py-3 border-b border-white/10">
              <span class="text-sm font-semibold">信息</span>
              <button class="text-xs opacity-70 hover:opacity-100" @click="toggleInfo">折叠</button>
            </div>
            <div class="flex-1 overflow-auto px-4 py-3 space-y-2 text-xs leading-relaxed">
              <div><span class="opacity-60">文件名：</span>{{ currentPhoto?.filename }}</div>
              <div v-if="currentPhoto?.takenAt"><span class="opacity-60">拍摄时间：</span>{{ formatDate(currentPhoto.takenAt) }}</div>
              <div v-if="currentPhoto?.cameraModel"><span class="opacity-60">相机：</span>{{ currentPhoto.cameraModel }}</div>
              <div v-if="currentPhoto?.lensModel"><span class="opacity-60">镜头：</span>{{ currentPhoto.lensModel }}</div>
              <div v-if="currentPhoto?.focalLength"><span class="opacity-60">焦距：</span>{{ currentPhoto.focalLength }}</div>
              <div v-if="currentPhoto?.aperture"><span class="opacity-60">光圈：</span>{{ currentPhoto.aperture }}</div>
              <div v-if="currentPhoto?.shutterSpeed"><span class="opacity-60">快门：</span>{{ currentPhoto.shutterSpeed }}</div>
              <div v-if="currentPhoto?.iso"><span class="opacity-60">ISO：</span>{{ currentPhoto.iso }}</div>
              <div v-if="currentPhoto?.qualityScore"><span class="opacity-60">质量：</span>{{ currentPhoto.qualityScore?.toFixed(1) }}</div>
              <div v-if="currentPhoto?.focusX !== undefined && currentPhoto?.focusY !== undefined">
                <span class="opacity-60">聚焦位置：</span>
                <span class="inline-flex items-center gap-2">
                  X: {{ currentPhoto.focusX.toFixed(1) }}%, Y: {{ currentPhoto.focusY.toFixed(1) }}%
                  <button
                    class="text-xs px-2 py-0.5 bg-white/10 hover:bg-white/20 rounded"
                    @click="showFocusBox = !showFocusBox"
                  >
                    {{ showFocusBox ? '隐藏框' : '显示框' }}
                  </button>
                </span>
              </div>
              <div v-if="currentPhoto?.tags?.length">
                <span class="opacity-60">标签：</span>
                <span class="inline-flex flex-wrap gap-2 mt-1">
                  <span
                    v-for="t in currentPhoto.tags.slice(0, 8)"
                    :key="t.id"
                    class="px-2 py-1 bg-white/10 rounded cursor-pointer hover:bg-white/20"
                    @click.stop="openTag(t)"
                  >
                    {{ t.name }}
                  </span>
                </span>
              </div>
              <div v-if="confirmedPersons.length">
                <span class="opacity-60">已确认人物：</span>
                <span class="inline-flex flex-wrap gap-2 mt-1">
                  <span
                    v-for="p in confirmedPersons"
                    :key="p.key"
                    class="px-2 py-1 bg-green-500/20 border border-green-500/40 rounded"
                  >
                    {{ p.name }} ({{ p.count }} 张)
                  </span>
                </span>
              </div>
              <div v-if="unconfirmedPersons.length">
                <span class="opacity-60">未确认人物：</span>
                <span class="inline-flex flex-wrap gap-2 mt-1">
                  <span
                    v-for="p in unconfirmedPersons"
                    :key="p.key"
                    class="px-2 py-1 bg-yellow-500/20 border border-yellow-500/40 rounded"
                  >
                    {{ p.name }} ({{ p.count }} 张)
                  </span>
                </span>
              </div>
            </div>
          </div>
        </transition>

      </div>

      <!-- 底部缩略图横排 -->
      <transition name="fade">
        <div
          v-if="!isFullscreen"
          class="bg-black/80 border-t border-white/10 overflow-x-auto overflow-y-hidden select-none relative"
          :style="{ height: Math.max(thumbHeight, thumbSize + 18) + 'px' }"
        >
          <div
            class="absolute inset-x-0 top-0 h-3 cursor-ns-resize border-b border-white/20 bg-black/35 z-20"
            @mousedown.prevent="startDrag"
            title="拖动调整高度"
          ></div>
          <div class="h-1"></div>
          <div
            class="flex items-center gap-2 px-3 py-1.5 min-w-max"
            ref="thumbContainer"
          >
            <div
              v-for="(p, idx) in photos"
              :key="p.id"
              class="relative flex-shrink-0 cursor-pointer border transition-all duration-150"
              :style="{ width: thumbSize + 'px', height: thumbSize + 'px' }"
              :class="idx === currentIndex ? 'border-white scale-[1.02]' : 'border-transparent opacity-80 hover:opacity-100'"
              @click="jump(idx)"
              :ref="el => (thumbItems[idx] = el as HTMLElement)"
            >
              <img
                :src="getThumbUrl(p)"
                :alt="p.filename"
                class="w-full h-full object-cover rounded-sm"
              />
              <div
                v-if="idx === currentIndex"
                class="pointer-events-none absolute inset-0 ring-2 ring-white/90 rounded-sm"
              ></div>
            </div>
          </div>
        </div>
      </transition>

    </div>
  </transition>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import type { Photo } from '@/stores/photo'

const props = defineProps<{
  photos: Photo[]
  visible: boolean
  startIndex?: number
}>()

const emit = defineEmits<{
  (e: 'update:visible', val: boolean): void
}>()

const currentIndex = ref(0)
const infoCollapsed = ref(false)
const modalRoot = ref<HTMLElement | null>(null)
const touchStartX = ref(0)
const thumbContainer = ref<HTMLElement | null>(null)
const thumbItems = ref<HTMLElement[]>([])
const thumbHeight = ref<number>(parseInt(localStorage.getItem('pe-thumb-height') || '112', 10) || 112)
const dragging = ref(false)
const dragStartY = ref(0)
const dragStartHeight = ref(0)
const thumbSize = computed(() => Math.max(24, clampThumbHeight(thumbHeight.value - 24)))
const isFullscreen = ref(false)
const showFocusBox = ref(false)
const mainImage = ref<HTMLImageElement | null>(null)
const imageContainer = ref<HTMLElement | null>(null)
const imageSize = ref({ width: 0, height: 0 })
const imageLoaded = ref(false)

// 缩放相关状态
const scale = ref(1)
const translateX = ref(0)
const translateY = ref(0)
const isDragging = ref(false)
const dragStart = ref({ x: 0, y: 0, translateX: 0, translateY: 0 })
const lastTouchDistance = ref(0)
const touchCenter = ref({ x: 0, y: 0 })
const isPinching = ref(false)

const STORAGE_KEY = 'pe-info-collapsed'
const THUMB_KEY = 'pe-thumb-height'

const currentPhoto = computed(() => props.photos?.[currentIndex.value])
const router = useRouter()
const confirmedPersons = computed(() => {
  const faces = currentPhoto.value?.faces || []
  const map: Record<string, { key: string; name: string; count: number }> = {}
  faces
    .filter((f) => f.isConfirmed && f.personName)
    .forEach((f) => {
      const key = String(f.personId || f.personName)
      if (!map[key]) {
        map[key] = { key, name: f.personName || '未命名', count: 0 }
      }
      map[key].count += 1
    })
  return Object.values(map)
})
const unconfirmedPersons = computed(() => {
  const faces = currentPhoto.value?.faces || []
  const map: Record<string, { key: string; name: string; count: number }> = {}
  faces
    .filter((f) => !f.isConfirmed)
    .forEach((f, idx) => {
      const key = f.personId ? `p-${f.personId}` : `u-${f.personName || idx}`
      const name = f.personName || '未命名'
      if (!map[key]) {
        map[key] = { key, name, count: 0 }
      }
      map[key].count += 1
    })
  return Object.values(map)
})

const openTag = (tag: any) => {
  if (!tag?.id) return
  router.push({ path: '/wall', query: { tagId: tag.id, tagName: tag.name } })
}

watch(
  () => props.visible,
  (val) => {
    if (val) {
      // 聚焦以接收键盘
      requestAnimationFrame(() => modalRoot.value?.focus())
      // 初始化索引
      if (typeof props.startIndex === 'number') {
        currentIndex.value = Math.min(Math.max(props.startIndex, 0), props.photos.length - 1)
      }
      scrollThumbIntoView()
    }
  },
  { immediate: true }
)

onMounted(() => {
  const saved = localStorage.getItem(STORAGE_KEY)
  infoCollapsed.value = saved === '1'
  window.addEventListener('keydown', onKeydown)
  window.addEventListener('resize', onImageLoad)
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', onKeydown)
  window.removeEventListener('resize', onImageLoad)
})

const toggleInfo = () => {
  infoCollapsed.value = !infoCollapsed.value
  localStorage.setItem(STORAGE_KEY, infoCollapsed.value ? '1' : '0')
}

const close = () => {
  emit('update:visible', false)
}

const prev = () => {
  if (!props.photos?.length) return
  currentIndex.value = (currentIndex.value - 1 + props.photos.length) % props.photos.length
}

const next = () => {
  if (!props.photos?.length) return
  currentIndex.value = (currentIndex.value + 1) % props.photos.length
}

const jump = (idx: number) => {
  currentIndex.value = idx
}

const clampThumbHeight = (val: number) => Math.min(260, Math.max(60, val))

const startDrag = (e: MouseEvent) => {
  dragging.value = true
  dragStartY.value = e.clientY
  dragStartHeight.value = thumbHeight.value
  window.addEventListener('mousemove', onDrag)
  window.addEventListener('mouseup', stopDrag)
}

const onDrag = (e: MouseEvent) => {
  if (!dragging.value) return
  const delta = dragStartY.value - e.clientY
  thumbHeight.value = clampThumbHeight(dragStartHeight.value + delta)
}

const stopDrag = () => {
  if (!dragging.value) return
  dragging.value = false
  thumbHeight.value = clampThumbHeight(thumbHeight.value)
  localStorage.setItem(THUMB_KEY, String(thumbHeight.value))
  window.removeEventListener('mousemove', onDrag)
  window.removeEventListener('mouseup', stopDrag)
}

const toggleFullscreen = async () => {
  const el = modalRoot.value
  if (!el) return
  try {
    if (!document.fullscreenElement) {
      await el.requestFullscreen()
      isFullscreen.value = true
    } else {
      await document.exitFullscreen()
      isFullscreen.value = false
    }
  } catch (e) {
    // ignore fullscreen errors
    isFullscreen.value = !!document.fullscreenElement
  }
}

watch(
  () => props.visible,
  (val) => {
    if (!val && document.fullscreenElement) {
      document.exitFullscreen().catch(() => {})
      isFullscreen.value = false
    }
  }
)

const scrollThumbIntoView = () => {
  nextTick(() => {
    const el = thumbItems.value[currentIndex.value]
    const container = thumbContainer.value
    if (el && container) {
      el.scrollIntoView({
        behavior: 'smooth',
        inline: 'center',
        block: 'nearest'
      })
    }
  })
}

const onKeydown = (e: KeyboardEvent) => {
  if (!props.visible) return
  if (e.key === 'Escape') {
    close()
  } else if (e.key === 'ArrowLeft') {
    prev()
  } else if (e.key === 'ArrowRight') {
    next()
  }
}

// 触控板/鼠标滚轮缩放
const onWheelZoom = (e: WheelEvent) => {
  // 如果按住 Ctrl/Cmd 键，进行缩放
  if (e.ctrlKey || e.metaKey) {
    e.preventDefault()
    const delta = e.deltaY > 0 ? -0.1 : 0.1
    zoomAtPoint(e.clientX, e.clientY, delta)
  } else {
    // 否则用于切换图片（原有逻辑）
    if (Math.abs(e.deltaY) > Math.abs(e.deltaX)) {
      if (e.deltaY > 0) next()
      else prev()
    } else {
      if (e.deltaX > 0) next()
      else prev()
    }
  }
}

// 触摸缩放和拖拽
const onTouchStartZoom = (e: TouchEvent) => {
  if (e.touches.length === 1) {
    // 单指：准备拖拽
    isDragging.value = true
    dragStart.value = {
      x: e.touches[0].clientX,
      y: e.touches[0].clientY,
      translateX: translateX.value,
      translateY: translateY.value
    }
    touchStartX.value = e.touches[0].clientX
  } else if (e.touches.length === 2) {
    // 双指：准备缩放
    isPinching.value = true
    isDragging.value = false
    const touch1 = e.touches[0]
    const touch2 = e.touches[1]
    lastTouchDistance.value = getTouchDistance(touch1, touch2)
    touchCenter.value = {
      x: (touch1.clientX + touch2.clientX) / 2,
      y: (touch1.clientY + touch2.clientY) / 2
    }
  }
}

const onTouchMoveZoom = (e: TouchEvent) => {
  e.preventDefault()
  
  if (e.touches.length === 1 && isDragging.value && scale.value > 1) {
    // 单指拖拽（仅在缩放后）
    const dx = e.touches[0].clientX - dragStart.value.x
    const dy = e.touches[0].clientY - dragStart.value.y
    translateX.value = dragStart.value.translateX + dx
    translateY.value = dragStart.value.translateY + dy
    constrainTranslation()
  } else if (e.touches.length === 2 && isPinching.value) {
    // 双指缩放
    const touch1 = e.touches[0]
    const touch2 = e.touches[1]
    const distance = getTouchDistance(touch1, touch2)
    const scaleDelta = distance / lastTouchDistance.value
    
    // 更新缩放中心
    touchCenter.value = {
      x: (touch1.clientX + touch2.clientX) / 2,
      y: (touch1.clientY + touch2.clientY) / 2
    }
    
    zoomAtPoint(touchCenter.value.x, touchCenter.value.y, (scaleDelta - 1) * scale.value)
    lastTouchDistance.value = distance
  }
}

const onTouchEndZoom = (e: TouchEvent) => {
  if (e.touches.length === 0) {
    // 所有手指抬起
    if (isDragging.value && scale.value === 1) {
      // 如果未缩放，检查是否是滑动切换图片
      const dx = e.changedTouches[0].clientX - touchStartX.value
      if (Math.abs(dx) > 40) {
        if (dx > 0) prev()
        else next()
      }
    }
    isDragging.value = false
    isPinching.value = false
  } else if (e.touches.length === 1) {
    // 从双指变为单指
    isPinching.value = false
    isDragging.value = true
    dragStart.value = {
      x: e.touches[0].clientX,
      y: e.touches[0].clientY,
      translateX: translateX.value,
      translateY: translateY.value
    }
  }
}

const getTouchDistance = (touch1: Touch, touch2: Touch) => {
  const dx = touch2.clientX - touch1.clientX
  const dy = touch2.clientY - touch1.clientY
  return Math.sqrt(dx * dx + dy * dy)
}

// 鼠标拖拽（仅在缩放后）
const onMouseDown = (e: MouseEvent) => {
  if (scale.value > 1 && e.button === 0) {
    e.preventDefault()
    isDragging.value = true
    dragStart.value = {
      x: e.clientX,
      y: e.clientY,
      translateX: translateX.value,
      translateY: translateY.value
    }
    window.addEventListener('mousemove', onMouseMove)
    window.addEventListener('mouseup', onMouseUp)
  }
}

const onMouseMove = (e: MouseEvent) => {
  if (isDragging.value && scale.value > 1) {
    const dx = e.clientX - dragStart.value.x
    const dy = e.clientY - dragStart.value.y
    translateX.value = dragStart.value.translateX + dx
    translateY.value = dragStart.value.translateY + dy
    constrainTranslation()
  }
}

const onMouseUp = () => {
  if (isDragging.value) {
    isDragging.value = false
    window.removeEventListener('mousemove', onMouseMove)
    window.removeEventListener('mouseup', onMouseUp)
  }
}

const formatDate = (val?: string) => {
  if (!val) return ''
  return val.slice(0, 10)
}

const getImageUrl = (photo: Photo) => {
  if (photo.webpPath) return `/api/files${photo.webpPath}`
  if (photo.originalPath) return `/api/files${photo.originalPath}`
  return ''
}

const getThumbUrl = (photo: Photo) => {
  if (photo.thumbnailPath) return `/api/files${photo.thumbnailPath}`
  return getImageUrl(photo)
}

const onImageLoad = () => {
  if (mainImage.value) {
    // 确保图片已完全加载
    const img = mainImage.value
    if (img.complete && img.naturalWidth > 0 && img.naturalHeight > 0) {
      imageSize.value = {
        width: img.offsetWidth,
        height: img.offsetHeight
      }
      imageLoaded.value = true
    } else {
      imageLoaded.value = false
    }
  }
}

const getFocusBoxStyle = () => {
  if (!currentPhoto.value || !mainImage.value || !imageContainer.value || !imageLoaded.value) return {}
  
  const photo = currentPhoto.value
  const img = mainImage.value
  const container = imageContainer.value
  
  // 再次检查图片是否已加载完成（双重保险）
  if (!img.complete || img.naturalWidth === 0 || img.naturalHeight === 0) {
    return {}
  }
  
  // 获取图片元素的边界矩形（这是图片元素占用的空间，可能包含空白）
  const imgRect = img.getBoundingClientRect()
  const containerRect = container.getBoundingClientRect()
  
  // 使用当前图片的实际原始尺寸（直接从 img 获取，确保是最新的）
  const naturalWidth = img.naturalWidth
  const naturalHeight = img.naturalHeight
  
  if (naturalWidth === 0 || naturalHeight === 0) return {}
  
  // 图片元素的显示尺寸（可能包含空白区域）
  const elementWidth = imgRect.width
  const elementHeight = imgRect.height
  
  // 计算 object-contain 的缩放比例
  // object-contain 会保持宽高比，使用较小的缩放比例
  const scaleX = elementWidth / naturalWidth
  const scaleY = elementHeight / naturalHeight
  const baseScale = Math.min(scaleX, scaleY)
  
  // 计算图片实际显示的尺寸（基础缩放，不考虑用户缩放）
  const baseDisplayWidth = naturalWidth * baseScale
  const baseDisplayHeight = naturalHeight * baseScale
  
  // 计算图片在元素中的偏移（居中显示）
  // 因为 object-contain，图片会在元素中居中
  const offsetX = (elementWidth - baseDisplayWidth) / 2
  const offsetY = (elementHeight - baseDisplayHeight) / 2
  
  // 计算图片在容器中的位置
  const imgOffsetX = imgRect.left - containerRect.left
  const imgOffsetY = imgRect.top - containerRect.top
  
  // 焦点框大小基于基础显示尺寸的 20%（不考虑用户缩放，保持固定视觉大小）
  const boxSize = Math.min(baseDisplayWidth, baseDisplayHeight) * 0.2 * scale.value
  
  // 计算焦点框的位置（基于百分比）
  // focusX 和 focusY 是 0-100 的百分比，表示在原始图片上的位置
  const focusXPercent = photo.focusX! / 100
  const focusYPercent = photo.focusY! / 100
  
  // 将原始图片的焦点位置转换为基础显示图片上的坐标
  const focusXOnBase = focusXPercent * baseDisplayWidth
  const focusYOnBase = focusYPercent * baseDisplayHeight
  
  // 计算焦点框在基础显示图片上的位置（焦点位置是框的中心）
  const boxLeftOnBase = focusXOnBase - (boxSize / scale.value) / 2
  const boxTopOnBase = focusYOnBase - (boxSize / scale.value) / 2
  
  // 确保焦点框在基础显示图片范围内
  const baseBoxSize = boxSize / scale.value
  const clampedLeft = Math.max(0, Math.min(boxLeftOnBase, baseDisplayWidth - baseBoxSize))
  const clampedTop = Math.max(0, Math.min(boxTopOnBase, baseDisplayHeight - baseBoxSize))
  
  // 转换为相对于容器的绝对位置
  // 容器偏移 + 元素内偏移 + 图片上的位置 + 用户缩放和平移
  const left = imgOffsetX + offsetX + clampedLeft * scale.value + translateX.value
  const top = imgOffsetY + offsetY + clampedTop * scale.value + translateY.value
  
  return {
    left: `${left}px`,
    top: `${top}px`,
    width: `${boxSize}px`,
    height: `${boxSize}px`
  }
}

// 双击缩放
const onDoubleClick = (e: MouseEvent) => {
  if (scale.value > 1) {
    // 如果已缩放，重置
    resetZoom()
  } else {
    // 否则放大到2倍
    zoomAtPoint(e.clientX, e.clientY, 1)
  }
}

// 在指定点缩放
const zoomAtPoint = (clientX: number, clientY: number, delta: number) => {
  if (!mainImage.value || !imageContainer.value) return
  
  const container = imageContainer.value
  const containerRect = container.getBoundingClientRect()
  
  // 计算相对于容器的坐标
  const x = clientX - containerRect.left - containerRect.width / 2
  const y = clientY - containerRect.top - containerRect.height / 2
  
  // 计算新的缩放值
  const targetScale = Math.max(1, Math.min(5, scale.value + delta))
  const scaleDelta = targetScale / scale.value
  
  // 调整平移，使缩放中心点保持不变
  translateX.value = x - (x - translateX.value) * scaleDelta
  translateY.value = y - (y - translateY.value) * scaleDelta
  
  scale.value = targetScale
  constrainTranslation()
}

// 限制平移范围
const constrainTranslation = () => {
  if (!mainImage.value || !imageContainer.value || scale.value <= 1) {
    translateX.value = 0
    translateY.value = 0
    return
  }
  
  const img = mainImage.value
  const container = imageContainer.value
  
  const imgRect = img.getBoundingClientRect()
  const containerRect = container.getBoundingClientRect()
  
  const scaledWidth = imgRect.width * scale.value
  const scaledHeight = imgRect.height * scale.value
  
  const maxX = (scaledWidth - containerRect.width) / 2
  const maxY = (scaledHeight - containerRect.height) / 2
  
  translateX.value = Math.max(-maxX, Math.min(maxX, translateX.value))
  translateY.value = Math.max(-maxY, Math.min(maxY, translateY.value))
}

// 重置缩放
const resetZoom = () => {
  scale.value = 1
  translateX.value = 0
  translateY.value = 0
}

// 获取图片样式（确保 object-contain 正确工作）
const getImageStyle = (): Record<string, string> => {
  if (!mainImage.value || !imageContainer.value) {
    return {
      maxWidth: '100%',
      maxHeight: '100%',
      objectFit: 'contain',
      display: 'block'
    }
  }
  
  const container = imageContainer.value
  const containerRect = container.getBoundingClientRect()
  
  return {
    maxWidth: `${containerRect.width}px`,
    maxHeight: `${containerRect.height}px`,
    width: 'auto',
    height: 'auto',
    objectFit: 'contain',
    display: 'block'
  }
}

// 获取图片变换样式
const getImageTransformStyle = () => {
  return {
    transform: `translate(${translateX.value}px, ${translateY.value}px) scale(${scale.value})`,
    transformOrigin: 'center center'
  }
}

watch(
  () => currentIndex.value,
  () => {
    scrollThumbIntoView()
    // 图片切换时重置状态，等待新图片加载
    imageSize.value = { width: 0, height: 0 }
    imageLoaded.value = false
    // 重置缩放
    resetZoom()
    // 图片加载完成后会自动调用 onImageLoad
  }
)

watch(
  () => props.photos,
  () => {
    thumbItems.value = []
    nextTick(() => scrollThumbIntoView())
  }
)

onBeforeUnmount(() => {
  stopDrag()
  onMouseUp()
  window.removeEventListener('mousemove', onMouseMove)
  window.removeEventListener('mouseup', onMouseUp)
})
</script>

<style scoped>
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.15s ease;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

.slide-fade-enter-active,
.slide-fade-leave-active {
  transition: all 0.18s ease;
}
.slide-fade-enter-from {
  opacity: 0;
  transform: translateX(12px);
}
.slide-fade-leave-to {
  opacity: 0;
  transform: translateX(12px);
}
</style>

