<template>
  <div
    class="photo-card cursor-pointer group space-y-1 w-full mx-auto transform-gpu"
    :class="cardSizeClass"
    :data-album-id="album.id"
    role="button"
    tabindex="0"
    @click="handleClick"
    @keydown.enter.prevent="handleClick"
    @keydown.space.prevent="handleClick"
    ref="cardRef"
    style="contain: layout style paint; will-change: transform;"
  >
    <!-- 封面布局：左侧竖图 + 右侧上下两张横图 -->
    <div class="grid grid-cols-2 gap-0.5 relative w-full" :class="coverSizeClass">
      <!-- 左侧竖图 -->
      <div class="row-span-2 overflow-hidden rounded-l-lg" ref="leftImageRef" :data-slot="'left'">
        <img
          v-if="leftImage"
          :src="getImageUrl(leftImage)"
          :alt="album.name"
          class="photo-image w-full h-full"
          :data-photo-id="leftImage.id"
          loading="lazy"
          decoding="async"
          @load="handleImageLoad"
          @error="handleImageError"
        />
        <div v-else class="w-full h-full bg-gray-200 dark:bg-gray-800 flex items-center justify-center">
          <span class="text-gray-400">暂无图片</span>
        </div>
      </div>

      <!-- 右侧上方横图 -->
      <div class="overflow-hidden rounded-tr-lg" ref="rightTopImageRef" :data-slot="'rightTop'">
        <img
          v-if="rightTopImage"
          :src="getImageUrl(rightTopImage)"
          :alt="album.name"
          class="photo-image w-full h-full"
          :data-photo-id="rightTopImage.id"
          loading="lazy"
          decoding="async"
          @load="handleImageLoad"
          @error="handleImageError"
        />
        <div v-else class="w-full h-full bg-gray-200 dark:bg-gray-800"></div>
      </div>

      <!-- 右侧下方横图 -->
      <div class="overflow-hidden rounded-br-lg relative" ref="rightBottomImageRef" :data-slot="'rightBottom'">
        <img
          v-if="rightBottomImage"
          :src="getImageUrl(rightBottomImage)"
          :alt="album.name"
          class="photo-image w-full h-full"
          :data-photo-id="rightBottomImage.id"
          loading="lazy"
          decoding="async"
          @load="handleImageLoad"
          @error="handleImageError"
        />
        <div v-else class="w-full h-full bg-gray-200 dark:bg-gray-800"></div>

        <!-- 右下角蒙版显示总数 -->
        <div
          v-if="album.photoCount && album.photoCount > 0"
          class="absolute inset-0 bg-black/35 text-white flex items-center justify-center text-base font-semibold album-cover-overlay"
        >
          共 {{ album.photoCount }} 张
        </div>
      </div>
    </div>

    <!-- 信息块（显示在封面下方） -->
    <div class="px-2 py-1 text-gray-900 dark:text-gray-100 space-y-0.5">
      <div class="flex items-center">
        <h3 class="text-sm font-semibold truncate">{{ album.displayTitle || album.name }}</h3>
      </div>
      <div v-if="takenDateText" class="text-xs text-gray-500 dark:text-gray-400">
        {{ takenDateText }}
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import type { Album } from '@/stores/photo'

type Size = 'sm' | 'md' | 'lg'

const props = defineProps<{
  album: Album
  size?: Size
}>()

const emit = defineEmits<{
  click: []
}>()

const cardRef = ref<HTMLElement>()
const leftImageRef = ref<HTMLElement>()
const rightTopImageRef = ref<HTMLElement>()
const rightBottomImageRef = ref<HTMLElement>()

const leftImage = computed(() => props.album.coverImages?.leftVertical)
const rightTopImage = computed(() => props.album.coverImages?.rightTop)
const rightBottomImage = computed(() => props.album.coverImages?.rightBottom)

// 图片加载优化
const handleImageLoad = (event: Event) => {
  const img = event.target as HTMLImageElement
  // 添加loaded类用于可能的后续样式优化
  img.classList.add('loaded')
}

const handleImageError = (event: Event) => {
  const img = event.target as HTMLImageElement
  // 错误处理，移除broken图片
  img.style.display = 'none'
}

const takenDateText = computed(() => {
  if (!props.album.takenAt) return ''
  return props.album.takenAt.slice(0, 10)
})

const cardSizeClass = computed(() => {
  const size = props.size || 'md'
  if (size === 'sm') return 'max-w-[200px]'
  if (size === 'lg') return 'max-w-[280px]'
  return 'max-w-[240px]'
})

const coverSizeClass = computed(() => {
  const size = props.size || 'md'
  if (size === 'sm') return 'h-40'
  if (size === 'lg') return 'h-56'
  return 'h-48'
})

const getImageUrl = (photo: any) => {
  // 封面使用小缩略图
  if (photo.smallThumbPath) {
    return `/api/files${photo.smallThumbPath}`
  }
  // 回退到webp
  if (photo.webpPath) {
    return `/api/files${photo.webpPath}`
  }
  // 最后回退到原缩略图或原图
  if (photo.thumbnailPath) {
    return `/api/files${photo.thumbnailPath}`
  }
  return `/api/files${photo.originalPath}`
}

const handleClick = () => {
  // 记录三张封面图的位置和对应的照片ID与位置(slot)
  const coverRects: Array<{ photoId: number; slot: 'left' | 'rightTop' | 'rightBottom'; rect: DOMRect }> = []
  
  if (leftImage.value && leftImageRef.value) {
    const rect = leftImageRef.value.getBoundingClientRect()
    coverRects.push({ photoId: leftImage.value.id, slot: 'left', rect })
  }
  
  if (rightTopImage.value && rightTopImageRef.value) {
    const rect = rightTopImageRef.value.getBoundingClientRect()
    coverRects.push({ photoId: rightTopImage.value.id, slot: 'rightTop', rect })
  }
  
  if (rightBottomImage.value && rightBottomImageRef.value) {
    const rect = rightBottomImageRef.value.getBoundingClientRect()
    coverRects.push({ photoId: rightBottomImage.value.id, slot: 'rightBottom', rect })
  }
  
  // 保存到 sessionStorage，供 AlbumDetail 使用
  if (coverRects.length > 0) {
    sessionStorage.setItem(
      `album-cover-rects-${props.album.id}`,
      JSON.stringify(
        coverRects.map(({ photoId, slot, rect }) => ({
          photoId,
          slot,
          rect: {
            top: rect.top,
            left: rect.left,
            width: rect.width,
            height: rect.height
          }
        }))
      )
    )
  }
  
  emit('click')
}
</script>

