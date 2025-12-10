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

      <div class="flex-1 flex overflow-hidden">
        <!-- 主图区域 -->
        <div class="flex-1 flex items-center justify-center relative px-2 sm:px-6">
          <img
            v-if="currentPhoto"
            :src="getImageUrl(currentPhoto)"
            :alt="currentPhoto.filename"
            class="max-h-full max-w-full object-contain select-none"
            @wheel.passive="onWheel"
            @touchstart="onTouchStart"
            @touchend="onTouchEnd"
          />

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
              <div v-if="currentPhoto?.tags?.length">
                <span class="opacity-60">标签：</span>
                <span class="inline-flex flex-wrap gap-2 mt-1">
                  <span
                    v-for="t in currentPhoto.tags.slice(0, 8)"
                    :key="t.id"
                    class="px-2 py-1 bg-white/10 rounded"
                  >
                    {{ t.name }}
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

const STORAGE_KEY = 'pe-info-collapsed'
const THUMB_KEY = 'pe-thumb-height'

const currentPhoto = computed(() => props.photos?.[currentIndex.value])

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
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', onKeydown)
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

const onWheel = (e: WheelEvent) => {
  if (Math.abs(e.deltaY) > Math.abs(e.deltaX)) {
    if (e.deltaY > 0) next()
    else prev()
  } else {
    if (e.deltaX > 0) next()
    else prev()
  }
}

const onTouchStart = (e: TouchEvent) => {
  touchStartX.value = e.changedTouches[0].clientX
}

const onTouchEnd = (e: TouchEvent) => {
  const dx = e.changedTouches[0].clientX - touchStartX.value
  if (Math.abs(dx) > 40) {
    if (dx > 0) prev()
    else next()
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

watch(
  () => currentIndex.value,
  () => {
    scrollThumbIntoView()
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

