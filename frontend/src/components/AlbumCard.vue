<template>
  <div
    class="photo-card cursor-pointer group w-full mx-auto transform-gpu rounded-2xl overflow-hidden"
    :class="[cardSizeClass, !cardBgColor ? 'bg-[rgba(255,255,255,0.82)] dark:bg-[rgba(22,22,22,0.82)]' : '']"
    :style="cardBgColor ? { backgroundColor: cardBgColor } : {}"
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
    <div class="overflow-hidden rounded-t-2xl">
      <CoverDisplay
        :covers="customCovers"
        :default-covers="defaultCovers"
        :photo-count="album.photoCount || 0"
        :size="sizeValue"
      />
    </div>

    <!-- 信息块（显示在封面下方） -->
    <div class="px-4 pt-3 pb-3.5 sm:px-5 text-gray-900 dark:text-gray-100">
      <div class="flex items-center">
        <h3 class="text-[13px] sm:text-[13.5px] font-light tracking-[0.035em] leading-[1.35] truncate text-stone-900 dark:text-stone-100">
          {{ album.displayTitle || album.name }}
        </h3>
      </div>
      <div v-if="takenDateText" class="mt-1 text-[10px] sm:text-[10.5px] tracking-[0.12em] uppercase text-stone-500 dark:text-stone-400">
        {{ takenDateText }}
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import type { Album } from '@/stores/photo'
import { useThemeStore } from '@/stores/theme'
import { useUiSettings } from '@/composables/useUiSettings'
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
const themeStore = useThemeStore()
const { atmosphereEnabled } = useUiSettings()

// 氛围模式下使用相册的背景色
const cardBgColor = computed(() => {
  if (!atmosphereEnabled.value) return null
  const a = props.album
  if (themeStore.isDark) {
    return a.darkBgColor || null
  }
  return a.lightBgColor || null
})

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
  
  // 先保存滚动位置（可能在导航前被重置）
  const scrollY = window.scrollY || 0
  sessionStorage.setItem('home-scroll-position', String(scrollY))
  
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
  
  // 保存氛围背景色（用于页面跳转前预设置底色，避免闪烁）
  if (atmosphereEnabled.value) {
    const bgColor = themeStore.isDark ? props.album.darkBgColor : props.album.lightBgColor
    if (bgColor) {
      sessionStorage.setItem(`album-atmosphere-bg-${props.album.id}`, bgColor)
      console.log('[点击相册] 已保存氛围背景色', { albumId: props.album.id, bgColor })
    }
  }
  
  emit('click')
}
</script>

<style scoped>
/* 默认状态：微妙边框，无阴影 */
.photo-card {
  border: 1px solid rgba(120, 113, 108, 0.18);
  backdrop-filter: saturate(118%) blur(10px);
  -webkit-backdrop-filter: saturate(118%) blur(10px);
  transition: transform 0.28s ease, border-color 0.28s ease, background-color 0.28s ease;
}

/* 悬浮状态：仅轻微上浮和边框加深，避免控件感过重 */
.photo-card:hover {
  transform: translateY(-2px);
  border-color: rgba(68, 64, 60, 0.28);
}

/* 封面图片悬浮放大 */
.photo-card:hover :deep(.cover-image) {
  transform: scale(1.02);
}

:global(.dark) .photo-card {
  border-color: rgba(255, 255, 255, 0.08);
}

:global(.dark) .photo-card:hover {
  border-color: rgba(255, 255, 255, 0.16);
}
</style>
