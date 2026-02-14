<template>
  <div class="collage-display relative" :class="[sizeClass]">
    <!-- 容器背景 -->
    <div class="absolute inset-0 bg-gradient-to-br from-gray-100 to-gray-200 dark:from-gray-800 dark:to-gray-900 rounded-lg"></div>
    
    <!-- 封面内容 -->
    <div class="relative w-full h-full overflow-hidden rounded-lg">
      <!-- 立体拼贴布局 -->
      <div :class="layoutClass" class="w-full h-full p-1" :style="containerStyle">
        <div
          v-for="(photo, index) in displayPhotos"
          :key="photo.id"
          class="relative overflow-hidden group shadow-lg transition-all duration-300"
          :class="getItemClass(index)"
          :style="getItemStyle(index)"
          @mouseenter="onItemHover(index)"
          @mouseleave="onItemLeave(index)"
        >
          <!-- 阴影层（底层） -->
          <div 
            class="absolute inset-0 bg-cover bg-center opacity-80 blur-sm scale-105"
            :style="{ backgroundImage: `url(${getPhotoUrl(photo)})` }"
          ></div>
          
          <!-- 主图层（带透明背景） -->
          <div class="relative w-full h-full overflow-hidden">
            <img
              :src="getBackgroundRemovedUrl(photo)"
              :alt="photo.filename"
              class="w-full h-full object-cover transition-transform duration-500 ease-out"
              :class="{ 'scale-110': hoveredIndex === index }"
              loading="lazy"
              @error="handleError($event, photo)"
            />
            
            <!-- 透明背景指示器（调试用） -->
            <div 
              v-if="!hasBackgroundRemoved(photo) && showPlaceholder"
              class="absolute inset-0 flex items-center justify-center bg-gray-200/50 dark:bg-gray-700/50"
            >
              <div class="text-center text-gray-500 dark:text-gray-400">
                <svg class="w-8 h-8 mx-auto mb-1" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z" />
                </svg>
                <span class="text-xs">点击加载透明背景</span>
              </div>
            </div>
          </div>
          
          <!-- 悬浮效果 - 3D 提升 -->
          <div 
            class="absolute inset-0 border-2 border-white/30 dark:border-white/20 rounded pointer-events-none transition-all duration-300"
            :class="{ 'border-white/50 dark:border-white/30': hoveredIndex === index }"
          ></div>
        </div>
      </div>
    </div>

    <!-- 右下角显示毛玻璃效果 -->
    <div
      v-if="photoCount > 0"
      class="absolute bottom-2 right-2 bg-black/40 backdrop-blur-md px-2 py-0.5 rounded-lg"
    >
      <span class="text-xs text-white/90">共 {{ photoCount }} 张</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'

interface Photo {
  id: number
  filename: string
  smallThumbPath?: string
  webpPath?: string
  thumbnailPath?: string
  originalPath?: string
  width?: number
  height?: number
  // 背景移除后的图片路径（如果有）
  backgroundRemovedPath?: string
}

interface Props {
  covers?: Photo[]
  defaultCovers?: {
    left?: Photo
    rightTop?: Photo
    rightBottom?: Photo
  }
  photoCount?: number
  size?: 'sm' | 'md' | 'lg'
  // 是否显示透明背景占位符
  showPlaceholder?: boolean
  // 拼贴间距
  gap?: number
  // 3D 效果强度
  depth?: number
}

const props = withDefaults(defineProps<Props>(), {
  covers: () => [],
  defaultCovers: () => ({ left: undefined, rightTop: undefined, rightBottom: undefined }),
  photoCount: 0,
  size: 'md',
  showPlaceholder: true,
  gap: 4,
  depth: 8
})

// 悬浮状态
const hoveredIndex = ref<number | null>(null)

const onItemHover = (index: number) => {
  hoveredIndex.value = index
}

const onItemLeave = () => {
  hoveredIndex.value = null
}

// 获取所有照片
const allPhotos = computed(() => {
  if (props.covers && props.covers.length > 0) {
    return props.covers
  }
  const defaults = []
  if (props.defaultCovers?.left) defaults.push(props.defaultCovers.left)
  if (props.defaultCovers?.rightTop) defaults.push(props.defaultCovers.rightTop)
  if (props.defaultCovers?.rightBottom) defaults.push(props.defaultCovers.rightBottom)
  return defaults
})

// 显示的照片（最多9张）
const displayPhotos = computed(() => {
  const photos = allPhotos.value
  if (photos.length <= 9) return photos
  return photos.slice(0, 9)
})

// 计算最佳布局
const layoutInfo = computed(() => {
  const count = displayPhotos.value.length
  if (count === 0) return { type: 'empty', rows: 1, cols: 1 }
  if (count === 1) return { type: 'single', rows: 1, cols: 1 }
  if (count === 2) return { type: 'horizontal-2', rows: 1, cols: 2 }
  if (count === 3) return { type: 'horizontal-3', rows: 1, cols: 3 }
  if (count <= 4) return { type: 'grid-2x2', rows: 2, cols: 2 }
  if (count <= 6) return { type: 'grid-2x3', rows: 2, cols: 3 }
  if (count <= 8) return { type: 'grid-2x4', rows: 2, cols: 4 }
  return { type: 'grid-3x3', rows: 3, cols: 3 }
})

// 布局样式
const layoutClass = computed(() => {
  const layout = layoutInfo.value
  switch (layout.type) {
    case 'single':
      return 'grid grid-rows-1 grid-cols-1'
    case 'horizontal-2':
      return 'grid grid-rows-1 grid-cols-2'
    case 'horizontal-3':
      return 'grid grid-rows-1 grid-cols-3'
    case 'grid-2x2':
      return 'grid grid-cols-2 grid-rows-2'
    case 'grid-2x3':
      return 'grid grid-cols-3 grid-rows-2'
    case 'grid-2x4':
      return 'grid grid-cols-4 grid-rows-2'
    case 'grid-3x3':
      return 'grid grid-cols-3 grid-rows-3'
    default:
      return 'grid'
  }
})

// 容器样式
const containerStyle = computed(() => ({
  gap: `${props.gap}px`,
  perspective: '1000px'
}))

// 每个格子的样式（3D 效果）
const getItemStyle = (index: number) => {
  const isHovered = hoveredIndex.value === index
  const offset = props.depth
  
  return {
    transform: isHovered 
      ? `translateZ(${offset}px) scale(1.02)` 
      : 'translateZ(0) scale(1)',
    boxShadow: isHovered
      ? `${offset}px ${offset}px ${offset * 2}px rgba(0,0,0,0.3)`
      : `${offset / 2}px ${offset / 2}px ${offset}px rgba(0,0,0,0.2)`,
    transition: 'all 0.3s ease-out'
  }
}

// 获取每个格子的额外类
const getItemClass = (index: number) => {
  const layout = layoutInfo.value
  const count = displayPhotos.value.length

  // 2x2 网格布局：主图放大
  if (layout.type === 'grid-2x2' && count >= 4) {
    if (index === 0) return 'row-span-2 col-span-2' // 左上占两行两列
  }

  return ''
}

// 获取普通图片URL
const getPhotoUrl = (photo?: Photo): string => {
  if (!photo) return ''
  if (photo.smallThumbPath) return `/api/files${photo.smallThumbPath}`
  if (photo.webpPath) return `/api/files${photo.webpPath}`
  if (photo.thumbnailPath) return `/api/files${photo.thumbnailPath}`
  return `/api/files${photo.originalPath}`
}

// 获取背景移除后的图片URL
const getBackgroundRemovedUrl = (photo?: Photo): string => {
  if (!photo) return ''
  // 优先使用已处理的透明背景图片
  if (photo.backgroundRemovedPath) {
    return `/api/files${photo.backgroundRemovedPath}`
  }
  // 回退到普通图片（不带3D效果）
  return getPhotoUrl(photo)
}

// 检查是否有背景移除的图片
const hasBackgroundRemoved = (photo?: Photo): boolean => {
  return !!photo?.backgroundRemovedPath
}

// 处理图片加载错误
const handleError = (event: Event, photo: Photo) => {
  const img = event.target as HTMLImageElement
  // 如果透明背景加载失败，回退到普通图片
  if (!hasBackgroundRemoved(photo)) {
    img.style.display = 'none'
  } else {
    // 尝试加载普通版本
    img.src = getPhotoUrl(photo)
    img.onerror = () => {
      img.style.display = 'none'
    }
  }
}

// 尺寸样式
const sizeClass = computed(() => {
  switch (props.size) {
    case 'sm': return 'h-32 md:h-36 lg:h-40'
    case 'lg': return 'h-48 md:h-56 lg:h-64'
    default: return 'h-40 md:h-48 lg:h-56'
  }
})
</script>

<style scoped>
.collage-display {
  @apply w-full overflow-hidden rounded-lg;
  transform-style: preserve-3d;
}

/* 添加 3D 透视效果 */
.collage-display::before {
  content: '';
  position: absolute;
  inset: 0;
  border-radius: 0.5rem;
  background: inherit;
  filter: blur(20px);
  opacity: 0.5;
  z-index: -1;
}
</style>
