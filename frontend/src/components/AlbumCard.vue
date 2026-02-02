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
  // 记录三张封面图的位置和对应的照片ID与位置(slot)
  const coverRects: Array<{ photoId: number; slot: 'left' | 'rightTop' | 'rightBottom'; rect: DOMRect }> = []
  
  // 获取实际显示的封面图片
  const actualCovers = customCovers.value.length > 0 ? customCovers.value : []
  const leftRef = cardRef.value?.querySelector('[data-slot="left"]') as HTMLElement
  const rightTopRef = cardRef.value?.querySelector('[data-slot="rightTop"]') as HTMLElement
  const rightBottomRef = cardRef.value?.querySelector('[data-slot="rightBottom"]') as HTMLElement
  
  // 根据布局记录封面位置
  if (actualCovers.length >= 1 && leftRef) {
    const rect = leftRef.getBoundingClientRect()
    coverRects.push({ photoId: actualCovers[0].id, slot: 'left', rect })
  }
  if (actualCovers.length >= 2 && rightTopRef) {
    const rect = rightTopRef.getBoundingClientRect()
    coverRects.push({ photoId: actualCovers[1].id, slot: 'rightTop', rect })
  }
  if (actualCovers.length >= 3 && rightBottomRef) {
    const rect = rightBottomRef.getBoundingClientRect()
    coverRects.push({ photoId: actualCovers[2].id, slot: 'rightBottom', rect })
  }
  // 4张封面时，cover3 对应右下位置
  if (actualCovers.length >= 4 && rightBottomRef) {
    const rect = rightBottomRef.getBoundingClientRect()
    coverRects.push({ photoId: actualCovers[3].id, slot: 'rightBottom', rect })
  }
  
  // 如果没有自定义封面，使用默认封面
  if (coverRects.length === 0) {
    const leftCover = defaultCovers.value.left
    const rightTopCover = defaultCovers.value.rightTop
    const rightBottomCover = defaultCovers.value.rightBottom
    
    if (leftCover && leftRef) {
      const rect = leftRef.getBoundingClientRect()
      coverRects.push({ photoId: leftCover.id, slot: 'left', rect })
    }
    if (rightTopCover && rightTopRef) {
      const rect = rightTopRef.getBoundingClientRect()
      coverRects.push({ photoId: rightTopCover.id, slot: 'rightTop', rect })
    }
    if (rightBottomCover && rightBottomRef) {
      const rect = rightBottomRef.getBoundingClientRect()
      coverRects.push({ photoId: rightBottomCover.id, slot: 'rightBottom', rect })
    }
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
