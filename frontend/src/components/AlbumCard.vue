<template>
  <div
    class="photo-card cursor-pointer group w-full transform-gpu rounded-[18px] overflow-hidden"
    :class="[cardSizeClass, !cardBgColor ? 'bg-white/85 dark:bg-[#1a1a1a]/85' : '']"
    :style="cardBgColor ? { backgroundColor: cardBgColor } : {}"
    :data-album-id="album.id"
    role="button"
    :aria-label="`打开相册：${album.displayTitle || album.name}`"
    tabindex="0"
    @click="handleClick"
    @keydown.enter.prevent="handleClick"
    @keydown.space.prevent="handleClick"
    ref="cardRef"
  >
    <!-- 封面布局 -->
    <div class="overflow-hidden rounded-t-[17px]">
      <CoverDisplay
        :covers="customCovers"
        :default-covers="defaultCovers"
        :photo-count="album.photoCount || 0"
        :size="sizeValue"
      />
    </div>

    <!-- 信息块（显示在封面下方） -->
    <div class="px-4 pt-2 pb-2.5 text-gray-900 dark:text-gray-100">
      <div class="flex items-center">
        <h3 class="text-[13.5px] sm:text-sm font-normal tracking-[0.02em] leading-[1.35] truncate text-stone-900 dark:text-stone-100">
          {{ album.displayTitle || album.name }}
        </h3>
      </div>
      <time
        v-if="takenDateText"
        :datetime="album.takenAt"
        class="mt-0.5 block text-[10px] sm:text-[10.5px] leading-3 tracking-[0.1em] uppercase text-stone-500 dark:text-stone-400"
      >
        {{ takenDateText }}
      </time>
      <div v-else aria-hidden="true" class="mt-0.5 h-3"></div>
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
  // 卡片填满网格轨道，视觉上的横向间隙才会与 row-gap 完全一致。
  // 封面尺寸与列数已由首页网格控制，无需再以 max-width 缩窄卡片。
  return 'max-w-none'
})

const handleClick = () => {
  // 记录封面图的位置和对应的照片ID
  const coverRects: Array<{ photoId: number; slot: string; rect: DOMRect; src: string }> = []
  
  // 获取实际显示的封面图片
  const actualCovers = customCovers.value.length > 0 ? customCovers.value : defaultCoversPhotos.value
  
  // 查询所有带 data-photo-id 属性的封面元素
  const slotElements = cardRef.value?.querySelectorAll('[data-photo-id]') || []
  
  slotElements.forEach((element) => {
    const photoId = element.getAttribute('data-photo-id')
    const slot = element.getAttribute('data-slot')
    const rect = element.getBoundingClientRect()
    const image = element instanceof HTMLImageElement ? element : element.querySelector('img')
    
    // 找到对应的封面图片
    const cover = actualCovers.find(c => c.id.toString() === photoId)
    // 懒加载图片首次点击时 currentSrc 可能尚未被浏览器填充，
    // 回退到 src 仍可直接复用首页缩略图作为详情页过渡源。
    const sourceSrc = image?.currentSrc || image?.getAttribute('src') || image?.src || ''
    if (cover && photoId && sourceSrc) {
      coverRects.push({ 
        photoId: parseInt(photoId), 
        slot: slot || `photo-${photoId}`, 
        rect,
        src: sourceSrc
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
        coverRects.map(({ photoId, slot, rect, src }) => ({
          photoId,
          slot,
          src,
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
/* 轻薄封套：封面主导，文字区提供稳定的归档感。 */
.photo-card {
  /* 覆盖全局瀑布流卡片的 mb-6；首页网格只由 gap 控制行列间距。 */
  margin-bottom: 0;
  border: 1px solid rgba(120, 113, 108, 0.16);
  content-visibility: auto;
  contain: layout paint style;
  contain-intrinsic-size: 240px 300px;
  box-shadow: 0 1px 2px rgba(41, 37, 36, 0.035);
  transition: transform 0.28s ease, border-color 0.28s ease, box-shadow 0.28s ease;
}

.photo-card:focus-visible {
  outline: 2px solid rgb(14 165 233);
  outline-offset: 3px;
}

/* 悬浮只增加一层纸张般的浮起感。 */
.photo-card:hover {
  transform: translateY(-2px);
  border-color: rgba(68, 64, 60, 0.24);
  box-shadow: 0 10px 24px rgba(41, 37, 36, 0.08);
}

/* 封面图片悬浮放大 */
.photo-card:hover :deep(.cover-image) {
  transform: scale(1.02);
}

:global(.dark) .photo-card {
  border-color: rgba(255, 255, 255, 0.08);
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.12);
}

:global(.dark) .photo-card:hover {
  border-color: rgba(255, 255, 255, 0.15);
  box-shadow: 0 10px 24px rgba(0, 0, 0, 0.20);
}
</style>
