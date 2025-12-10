<template>
  <div
    class="photo-card cursor-pointer group space-y-1 w-full mx-auto"
    :class="cardSizeClass"
    @click="$emit('click')"
  >
    <!-- 封面布局：左侧竖图 + 右侧上下两张横图 -->
    <div class="grid grid-cols-2 gap-0.5 relative w-full" :class="coverSizeClass">
      <!-- 左侧竖图 -->
      <div class="row-span-2 overflow-hidden rounded-l-lg">
        <img
          v-if="leftImage"
          :src="getImageUrl(leftImage)"
          :alt="album.name"
          class="photo-image w-full h-full"
          loading="lazy"
        />
        <div v-else class="w-full h-full bg-gray-200 dark:bg-gray-800 flex items-center justify-center">
          <span class="text-gray-400">暂无图片</span>
        </div>
      </div>

      <!-- 右侧上方横图 -->
      <div class="overflow-hidden rounded-tr-lg">
        <img
          v-if="rightTopImage"
          :src="getImageUrl(rightTopImage)"
          :alt="album.name"
          class="photo-image w-full h-full"
          loading="lazy"
        />
        <div v-else class="w-full h-full bg-gray-200 dark:bg-gray-800"></div>
      </div>

      <!-- 右侧下方横图 -->
      <div class="overflow-hidden rounded-br-lg relative">
        <img
          v-if="rightBottomImage"
          :src="getImageUrl(rightBottomImage)"
          :alt="album.name"
          class="photo-image w-full h-full"
          loading="lazy"
        />
        <div v-else class="w-full h-full bg-gray-200 dark:bg-gray-800"></div>

        <!-- 右下角蒙版显示总数 -->
        <div
          v-if="album.photoCount && album.photoCount > 0"
          class="absolute inset-0 bg-black/35 text-white flex items-center justify-center text-base font-semibold"
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
import { computed } from 'vue'
import type { Album } from '@/stores/photo'

type Size = 'sm' | 'md' | 'lg'

const props = defineProps<{
  album: Album
  size?: Size
}>()

const leftImage = computed(() => props.album.coverImages?.leftVertical)
const rightTopImage = computed(() => props.album.coverImages?.rightTop)
const rightBottomImage = computed(() => props.album.coverImages?.rightBottom)

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
  // 优先使用WebP，其次缩略图，最后原图
  if (photo.webpPath) {
    return `/api/files${photo.webpPath}`
  }
  if (photo.thumbnailPath) {
    return `/api/files${photo.thumbnailPath}`
  }
  return `/api/files${photo.originalPath}`
}
</script>

