<template>
  <div class="cover-display relative" :class="[sizeClass]">
    <!-- 封面内容 -->
    <div class="w-full h-full">
      <!-- 使用自适应布局 -->
      <div :class="layoutClass" class="w-full h-full gap-[1px]">
        <div
          v-for="(photo, index) in displayPhotos"
          :key="photo.id"
          class="relative bg-gray-800 overflow-hidden group"
          :class="getItemClass(index)"
          :data-photo-id="photo.id"
          :data-slot="getSlotName(photo, index)"
        >
          <img
            :src="getPhotoUrl(photo)"
            :alt="photo.filename"
            class="w-full h-full object-cover transition-transform duration-500 ease-out group-hover:scale-110"
            loading="lazy"
            @error="handleError"
            @load="onImageLoad($event, photo)"
          />
        </div>
      </div>
    </div>

    <!-- 右下角显示毛玻璃效果 -->
    <div
      v-if="photoCount > 0"
      class="absolute bottom-1.5 right-1.5 bg-black/45 px-2 py-0.5 rounded album-cover-overlay"
    >
      <span class="text-xs text-white">共 {{ photoCount }} 张</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, onMounted } from 'vue'
import { buildPhotoAssetUrl } from '@/utils/photoUrl'

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
  keepSquare?: boolean
  preferMediumThumb?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  covers: () => [],
  defaultCovers: () => ({ left: undefined, rightTop: undefined, rightBottom: undefined }),
  photoCount: 0,
  size: 'md',
  keepSquare: false,
  preferMediumThumb: false
})

// 存储图片的实际尺寸（用于布局判断）
const imageDimensions = ref<Map<number, { width: number; height: number }>>(new Map())

// 图片加载完成后获取实际尺寸
const onImageLoad = (event: Event, photo: Photo) => {
  const img = event.target as HTMLImageElement
  if (img.naturalWidth > 0 && img.naturalHeight > 0) {
    imageDimensions.value.set(photo.id, {
      width: img.naturalWidth,
      height: img.naturalHeight
    })
  }
}

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
  // 优先使用预设尺寸
  if (photo.width && photo.height) {
    const ratio = photo.width / photo.height
    if (ratio > 1.15) return 'horizontal' // 宽 > 高 × 1.15 → 横图
    if (ratio < 0.85) return 'vertical'   // 高 > 宽 × 1.15 → 竖图
    return 'square' // 接近1:1 → 正方形
  }
  
  // 使用动态加载的图片尺寸
  const dims = imageDimensions.value.get(photo.id)
  if (dims && dims.width > 0 && dims.height > 0) {
    const ratio = dims.width / dims.height
    if (ratio > 1.15) return 'horizontal'
    if (ratio < 0.85) return 'vertical'
    return 'square'
  }
  
  return 'horizontal' // 未知尺寸按横图处理
}

// 计算图片"主体占比"（主体越大越适合做主图）
const getMainScore = (photo: Photo): number => {
  // 优先使用预设尺寸
  let width = photo.width
  let height = photo.height
  
  // 使用动态加载的图片尺寸
  if ((!width || !height) && imageDimensions.value.has(photo.id)) {
    const dims = imageDimensions.value.get(photo.id)
    if (dims) {
      width = dims.width
      height = dims.height
    }
  }
  
  if (!width || !height) return 0
  // 面积越大、越接近正方形，分数越高
  const area = width * height
  const ratio = width / height
  const shapeScore = ratio >= 1 ? ratio : 1 / ratio // 越接近1（正方形）分数越高
  return area * shapeScore
}

// 智能排序：竖图优先放前面（适合做 masonry-3 左侧大图），然后再按面积排序
const sortedPhotos = computed(() => {
  const photos = [...allPhotos.value]
  // 先按类型优先级排序（竖图 > 横图/正方形），再按面积排序
  return photos.sort((a, b) => {
    const typeA = getPhotoType(a)
    const typeB = getPhotoType(b)
    // 竖图优先
    if (typeA === 'vertical' && typeB !== 'vertical') return -1
    if (typeB === 'vertical' && typeA !== 'vertical') return 1
    // 类型相同则按面积排序
    return getMainScore(b) - getMainScore(a)
  })
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

  // 如果有一张竖图，或者两张都是方的，进行左右布局
  // 这样可以更好地展示人物，避免裁切
  if (type1 === 'vertical' || type2 === 'vertical' || (type1 === 'square' && type2 === 'square')) {
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
  
  // 其他情况（混合或有横图）→ 2x2 网格布局，左上是主图
  // 左边一张，右边上下两张方块
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
      return 'grid grid-cols-2 grid-rows-2'
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
  
  // masonry-3 布局：左列占两行（第一张图），右列上下各一张
  if (layout.type === 'masonry-3' && count >= 3) {
    if (index === 0) return 'row-span-2' // 左上占两行
    if (index === 1) return '' // 右上
    if (index === 2) return '' // 左下
    return ''
  }
  
  return ''
}

// 获取槽位名称（用于FLIP动画）- 使用 photoId 确保唯一性
const getSlotName = (photo: Photo, index: number): string => {
  const layout = layoutInfo.value
  const count = displayPhotos.value.length
  
  // 单张图片
  if (layout.type === 'single') return `photo-${photo.id}`
  
  // 两张图片
  if (count === 2) {
    if (layout.type === 'vertical-2') {
      return index === 0 ? `photo-${photo.id}` : `photo-${photo.id}`
    }
    return `photo-${photo.id}`
  }
  
  // 三张图片
  if (count === 3) {
    if (layout.type === 'horizontal-3') {
      return `photo-${photo.id}`
    }
    // masonry-3: index 0 是左边大图
    if (layout.type === 'masonry-3' && index === 0) {
      return `photo-${photo.id}`
    }
    return `photo-${photo.id}`
  }
  
  // 所有情况都使用 photoId 作为 slot 名称
  return `photo-${photo.id}`
}

const getPhotoUrl = (photo?: Photo): string => {
  if (!photo) return ''

  // 根据封面数量自动选择缩略图质量
  // 只有1张封面时使用中等缩略图，多张封面时使用小缩略图
  const coverCount = displayPhotos.value.length
  const useMediumThumb = coverCount === 1

  if (useMediumThumb) {
    return buildPhotoAssetUrl(photo, 'medium') || ''
  }
  return buildPhotoAssetUrl(photo, 'small') || ''
}

const handleError = (event: Event) => {
  const img = event.target as HTMLImageElement
  img.style.display = 'none'
}

const sizeClass = computed(() => {
  const { size, keepSquare } = props
  // 人物封面保持正方形，其他场景使用固定高度
  if (keepSquare) {
    return 'aspect-square'
  }
  switch (size) {
    case 'sm': return 'h-32 md:h-36 lg:h-40'
    case 'lg': return 'h-48 md:h-56 lg:h-64'
    default: return 'h-40 md:h-48 lg:h-56'
  }
})
</script>

<style scoped>
.cover-display {
  @apply w-full overflow-hidden rounded-lg;
}
</style>
