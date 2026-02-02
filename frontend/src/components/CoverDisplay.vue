<template>
  <div class="cover-display relative" :class="[sizeClass]">
    <!-- 封面内容 -->
    <div class="w-full h-full">
      <!-- 使用自适应布局 -->
      <div :class="layoutClass" class="w-full h-full gap-[2px]">
        <div
          v-for="(photo, index) in displayPhotos"
          :key="photo.id"
          class="relative bg-gray-800 overflow-hidden"
          :class="getItemClass(index)"
        >
          <img
            :src="getPhotoUrl(photo)"
            :alt="photo.filename"
            class="w-full h-full object-cover"
            loading="lazy"
            @error="handleError"
          />
        </div>
      </div>
    </div>

    <!-- 右下角显示黑色矩形 -->
    <div
      v-if="photoCount > 0"
      class="absolute bottom-1.5 right-1.5 bg-black/60 px-2 py-0.5 rounded"
    >
      <span class="text-xs text-white">共 {{ photoCount }} 张</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

interface Photo {
  id: number
  filename: string
  smallThumbPath?: string
  webpPath?: string
  thumbnailPath?: string
  originalPath?: string
  width?: number
  height?: number
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
}

const props = withDefaults(defineProps<Props>(), {
  covers: () => [],
  defaultCovers: () => ({ left: undefined, rightTop: undefined, rightBottom: undefined }),
  photoCount: 0,
  size: 'md'
})

// 获取所有照片（自定义封面优先，否则使用默认封面）
const allPhotos = computed(() => {
  if (props.covers && props.covers.length > 0) {
    return props.covers
  }
  // 使用默认封面
  const defaults = []
  if (props.defaultCovers?.left) defaults.push(props.defaultCovers.left)
  if (props.defaultCovers?.rightTop) defaults.push(props.defaultCovers.rightTop)
  if (props.defaultCovers?.rightBottom) defaults.push(props.defaultCovers.rightBottom)
  return defaults
})

// 判断图片类型：竖图、横图、正方形
const getPhotoType = (photo: Photo): 'vertical' | 'horizontal' | 'square' => {
  if (!photo.width || !photo.height) return 'horizontal' // 未知尺寸按横图处理
  const ratio = photo.width / photo.height
  if (ratio > 1.15) return 'horizontal' // 宽 > 高 × 1.15 → 横图
  if (ratio < 0.85) return 'vertical'   // 高 > 宽 × 1.15 → 竖图
  return 'square' // 接近1:1 → 正方形
}

// 计算图片"主体占比"（主体越大越适合做主图）
const getMainScore = (photo: Photo): number => {
  if (!photo.width || !photo.height) return 0
  // 面积越大、越接近正方形，分数越高
  const area = photo.width * photo.height
  const ratio = photo.width / photo.height
  const shapeScore = ratio >= 1 ? ratio : 1 / ratio // 越接近1（正方形）分数越高
  return area * shapeScore
}

// 智能排序：主体大的放前面（适合做主图占大位置）
const sortedPhotos = computed(() => {
  const photos = [...allPhotos.value]
  return photos.sort((a, b) => getMainScore(b) - getMainScore(a))
})

// 显示的照片（最多9张，太多用网格布局）
const displayPhotos = computed(() => {
  const photos = sortedPhotos.value
  if (photos.length <= 9) return photos
  // 超过9张取前9张
  return photos.slice(0, 9)
})

// 计算最佳布局
const layoutInfo = computed(() => {
  const count = displayPhotos.value.length
  if (count === 0) return { type: 'empty', rows: 1, cols: 1 }
  if (count === 1) return { type: 'single', rows: 1, cols: 1 }
  if (count === 2) return getTwoColumnLayout()
  if (count === 3) return getThreeColumnLayout()
  if (count === 4) return { type: 'grid-2x2', rows: 2, cols: 2 }
  if (count <= 6) return { type: 'grid-2x3', rows: 2, cols: 3 }
  if (count <= 8) return { type: 'grid-2x4', rows: 2, cols: 4 }
  return { type: 'grid-3x3', rows: 3, cols: 3 }
})

// 两张图片的布局
const getTwoColumnLayout = () => {
  const [p1, p2] = displayPhotos.value
  const type1 = getPhotoType(p1)
  const type2 = getPhotoType(p2)
  
  // 两张都是竖图 → 左右布局
  if (type1 === 'vertical' && type2 === 'vertical') {
    return { type: 'horizontal-2', rows: 1, cols: 2 }
  }
  // 其他情况（至少有一张是横图）→ 上下布局（让横图更完整显示）
  return { type: 'vertical-2', rows: 2, cols: 1 }
}

// 三张图片的布局
const getThreeColumnLayout = () => {
  const photos = displayPhotos.value
  const types = photos.map(getPhotoType)
  const verticalCount = types.filter(t => t === 'vertical').length
  
  // 3张竖图 → 左中右三张横排（最完整展示竖图）
  if (verticalCount >= 3) {
    return { type: 'horizontal-3', rows: 1, cols: 3 }
  }
  
  // 其他情况（混合或有横图）→ masonry 布局
  // 左边第一张（最大的）占两行，右边上下两张
  return { type: 'masonry-3', rows: 2, cols: 2, mainFirst: true }
}

// 布局样式
const layoutClass = computed(() => {
  const layout = layoutInfo.value
  switch (layout.type) {
    case 'single':
      return 'grid grid-rows-1 grid-cols-1'
    case 'vertical-2':
      return 'grid grid-rows-2 grid-cols-1'
    case 'horizontal-2':
      return 'grid grid-rows-1 grid-cols-2'
    case 'masonry-3':
      return 'grid grid-cols-[2fr,3fr] grid-rows-2'
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

// 获取每个格子的样式
const getItemClass = (index: number) => {
  const layout = layoutInfo.value
  const count = displayPhotos.value.length
  
  // masonry-3 布局：左上是主图（占两行），右上和左下是其他图
  if (layout.type === 'masonry-3' && count >= 3) {
    if (index === 0) return 'row-span-2' // 左上占两行
    return ''
  }
  
  return ''
}

const getPhotoUrl = (photo?: Photo): string => {
  if (!photo) return ''
  if (photo.smallThumbPath) return `/api/files${photo.smallThumbPath}`
  if (photo.webpPath) return `/api/files${photo.webpPath}`
  if (photo.thumbnailPath) return `/api/files${photo.thumbnailPath}`
  return `/api/files${photo.originalPath}`
}

const handleError = (event: Event) => {
  const img = event.target as HTMLImageElement
  img.style.display = 'none'
}

const sizeClass = computed(() => {
  switch (props.size) {
    case 'sm': return 'h-24 md:h-28 lg:h-32'
    case 'lg': return 'h-56 md:h-60 lg:h-64'
    default: return 'h-40 md:h-44 lg:h-48'
  }
})
</script>

<style scoped>
.cover-display {
  @apply w-full overflow-hidden rounded-lg;
}
</style>
