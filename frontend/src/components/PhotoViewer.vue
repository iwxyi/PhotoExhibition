<template>
  <transition name="fade">
    <div
      v-if="visible"
      class="fixed inset-0 z-50 bg-black/90 backdrop-blur-sm flex flex-col"
      @keydown.stop.prevent="onKeydown"
      tabindex="0"
      ref="modalRoot"
    >
      <!-- 顶部栏 -->
      <div class="flex items-center justify-between px-4 sm:px-6 py-3 text-white text-sm">
        <div class="flex items-center gap-3">
          <button class="p-2 hover:bg-white/10 rounded" @click="close">关闭 Esc</button>
          <div class="text-xs sm:text-sm opacity-80">
            {{ currentPhoto?.filename }}
          </div>
        </div>
        <div class="flex items-center gap-3">
          <button class="p-2 hover:bg-white/10 rounded" @click="prev">←</button>
          <span class="text-xs sm:text-sm">{{ currentIndex + 1 }} / {{ photos.length }}</span>
          <button class="p-2 hover:bg-white/10 rounded" @click="next">→</button>
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
            class="absolute left-2 sm:left-6 top-1/2 -translate-y-1/2 p-3 rounded-full bg-black/40 text-white hover:bg-black/60"
            @click="prev"
          >
            ‹
          </button>
          <button
            class="absolute right-2 sm:right-6 top-1/2 -translate-y-1/2 p-3 rounded-full bg-black/40 text-white hover:bg-black/60"
            @click="next"
          >
            ›
          </button>
        </div>

        <!-- 信息侧栏 -->
        <div
          class="w-80 max-w-[80vw] bg-gray-900/80 text-white border-l border-white/10 flex flex-col"
          v-show="!infoCollapsed"
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

        <!-- 折叠状态时的侧栏开关 -->
        <button
          v-show="infoCollapsed"
          class="absolute right-3 top-20 px-2 py-1 rounded bg-black/50 text-white text-xs"
          @click="toggleInfo"
        >
          展开信息
        </button>
      </div>

      <!-- 底部缩略图横排 -->
      <div class="h-28 bg-black/80 border-t border-white/10 overflow-x-auto">
        <div class="flex items-center gap-2 px-3 py-2 min-w-max">
          <div
            v-for="(p, idx) in photos"
            :key="p.id"
            class="relative w-24 h-24 flex-shrink-0 cursor-pointer border"
            :class="idx === currentIndex ? 'border-white' : 'border-transparent opacity-80 hover:opacity-100'"
            @click="jump(idx)"
          >
            <img
              :src="getThumbUrl(p)"
              :alt="p.filename"
              class="w-full h-full object-cover"
            />
          </div>
        </div>
      </div>

    </div>
  </transition>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import type { Photo, Tag } from '@/stores/photo'

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

const STORAGE_KEY = 'pe-info-collapsed'

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
</style>

