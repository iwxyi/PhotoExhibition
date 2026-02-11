<template>
  <div
    class="photo-card cursor-pointer group space-y-1 w-full mx-auto transform-gpu bg-white dark:bg-gray-800"
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
    <!-- 封面布局 -->
    <CoverDisplay
      :covers="customCovers"
      :default-covers="defaultCovers"
      :photo-count="album.photoCount || 0"
      :size="sizeValue"
    />

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
import CoverDisplay from '@/components/CoverDisplay.vue'

type Size = 'sm' | 'md' | 'lg'

const props = defineProps<{
  album: Album
  size?: Size
}>()

const emit = defineEmits<{
  click: []
}>()

const cardRef = ref<HTMLElement>()

const sizeValue = computed(() => props.size || 'md')

// 自定义封面列表（根据 coverImageIds 排序）
const customCovers = computed(() => {
  if (!props.album.coverImageIds || props.album.coverImageIds.length === 0) {
    return []
  }
  
  const covers: any[] = []
  const coverMap = props.album.coverImages || {}
  const cover1 = coverMap.cover1 || coverMap.leftVertical
  const cover2 = coverMap.cover2 || coverMap.rightTop
  const cover3 = coverMap.cover3 || coverMap.rightBottom
  const cover4 = coverMap.cover4
  
  for (const id of props.album.coverImageIds) {
    if (cover1?.id === id) covers.push(cover1)
    else if (cover2?.id === id) covers.push(cover2)
    else if (cover3?.id === id) covers.push(cover3)
    else if (cover4?.id === id) covers.push(cover4)
  }
  
  return covers
})

// 默认封面（自动生成的）
const defaultCovers = computed(() => {
  const coverMap = props.album.coverImages || {}
  return {
    left: coverMap.cover1 || coverMap.leftVertical,
    rightTop: coverMap.cover2 || coverMap.rightTop,
    rightBottom: coverMap.cover3 || coverMap.rightBottom
  }
})

// 默认封面数组（用于FLIP动画）
const defaultCoversPhotos = computed(() => {
  const photos: any[] = []
  if (defaultCovers.value.left) photos.push(defaultCovers.value.left)
  if (defaultCovers.value.rightTop) photos.push(defaultCovers.value.rightTop)
  if (defaultCovers.value.rightBottom) photos.push(defaultCovers.value.rightBottom)
  return photos
})

const takenDateText = computed(() => {
  if (!props.album.takenAt) return ''
  return props.album.takenAt.slice(0, 10)
})

const cardSizeClass = computed(() => {
  const size = props.size || 'md'
  if (size === 'sm') return 'max-w-[200px]'
  if (size === 'md') return 'max-w-[240px]'
  if (size === 'lg') return 'max-w-[280px]'
  return 'max-w-[240px]'
})

const handleClick = () => {
  // 记录封面图的位置和对应的照片ID
  const coverRects: Array<{ photoId: number; slot: string; rect: DOMRect }> = []
  
  // 获取实际显示的封面图片
  const actualCovers = customCovers.value.length > 0 ? customCovers.value : defaultCoversPhotos.value
  
  // 查询所有带 data-photo-id 属性的封面元素
  const slotElements = cardRef.value?.querySelectorAll('[data-photo-id]') || []
  
  slotElements.forEach((element) => {
    const photoId = element.getAttribute('data-photo-id')
    const slot = element.getAttribute('data-slot')
    const rect = element.getBoundingClientRect()
    
    // 找到对应的封面图片
    const cover = actualCovers.find(c => c.id.toString() === photoId)
    if (cover && photoId) {
      coverRects.push({ 
        photoId: parseInt(photoId), 
        slot: slot || `photo-${photoId}`, 
        rect 
      })
  }
  })
  
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
    // 设置导航时间戳，用于判断是否从正常导航进入
    sessionStorage.setItem('album-navigation-active', Date.now().toString())
    console.log('[点击相册] 已保存封面位置和导航时间戳', { albumId: props.album.id, coverRectsCount: coverRects.length })
  }
  
  emit('click')
}
</script>

<style scoped>
/* 默认无阴影，悬浮时显示阴影 */
.photo-card {
  transition: transform 0.3s ease, box-shadow 0.3s ease;
}

.photo-card:hover {
  box-shadow: 0 10px 25px -5px rgba(0, 0, 0, 0.1), 0 8px 10px -6px rgba(0, 0, 0, 0.1);
}
</style>
