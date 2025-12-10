<template>
  <div
    class="photo-card cursor-pointer group"
    @click="$emit('click')"
  >
    <!-- 封面布局：左侧竖图 + 右侧上下两张横图 -->
    <div class="grid grid-cols-2 gap-2 h-80">
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
      <div class="overflow-hidden rounded-br-lg">
        <img
          v-if="rightBottomImage"
          :src="getImageUrl(rightBottomImage)"
          :alt="album.name"
          class="photo-image w-full h-full"
          loading="lazy"
        />
        <div v-else class="w-full h-full bg-gray-200 dark:bg-gray-800"></div>
      </div>
    </div>

    <!-- 信息覆盖层 -->
    <div class="gradient-overlay">
      <div class="absolute bottom-0 left-0 right-0 p-6 text-white">
        <h3 class="text-xl font-light mb-2">{{ album.name }}</h3>
        <p class="text-sm opacity-90">{{ album.photoCount }} 张照片</p>
        <div v-if="album.tags && album.tags.length > 0" class="flex flex-wrap gap-2 mt-3">
          <span
            v-for="tag in album.tags.slice(0, 3)"
            :key="tag.id"
            class="px-2 py-1 text-xs bg-white/20 backdrop-blur-sm rounded"
          >
            {{ tag.name }}
          </span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { Album } from '@/stores/photo'

const props = defineProps<{
  album: Album
}>()

const leftImage = computed(() => props.album.coverImages?.leftVertical)
const rightTopImage = computed(() => props.album.coverImages?.rightTop)
const rightBottomImage = computed(() => props.album.coverImages?.rightBottom)

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

