<template>
  <div class="masonry-layout" ref="containerRef">
    <div
      v-for="(item, index) in positionedItems"
      :key="item.id"
      class="masonry-item"
      :style="item.style"
    >
      <slot :item="item.data" :index="index" />
      <!-- 点赞覆盖层 -->
      <div
        class="like-overlay"
        :class="{ 'visible': (likedIds.has(item.data?.id) || (likesMap.get(item.data?.id) || 0) > 0) }"
        @click.stop="likePhoto(item.data?.id)"
        title="点赞"
      >
        <svg class="heart" viewBox="0 0 24 24" width="18" height="18" xmlns="http://www.w3.org/2000/svg" :aria-hidden="true">
          <path
            :fill="likedIds.has(item.data?.id) ? '#e11d48' : 'none'"
            stroke="currentColor"
            stroke-width="1.5"
            d="M12 21s-7-4.35-9.07-6.2A5.4 5.4 0 0 1 3 9.75C3 7.14 5.14 5 7.75 5c1.54 0 3.04.84 4.25 2.09C13.21 5.84 14.71 5 16.25 5 18.86 5 21 7.14 21 9.75c0 2.64-1.83 4.46-1.93 4.56C19.36 16.65 12 21 12 21z"
          />
        </svg>
        <span v-if="(likesMap.get(item.data?.id) || 0) > 0" class="like-count">{{ likesMap.get(item.data?.id) }}</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, nextTick, onMounted, onUnmounted, watch } from 'vue'

interface MasonryItem {
  id: string | number
  data: any
  width: number
  height: number
}

interface PositionedItem {
  id: string | number
  data: any
  style: Record<string, any>
}

const props = defineProps<{
  items: MasonryItem[]
  columnCount: number
  gap?: number
  itemMinWidth?: number
}>()

const containerRef = ref<HTMLElement>()
const positionedItems = ref<PositionedItem[]>([])

// 点赞相关（匿名点赞，使用 localStorage 保存用户是否已点赞）
import { api } from '@/api'

const likedIds = ref<Set<number>>(new Set())
const likesMap = ref<Map<number, number>>(new Map())

const loadLikedFromStorage = () => {
  try {
    const raw = localStorage.getItem('likedPhotos')
    if (raw) {
      const arr = JSON.parse(raw)
      likedIds.value = new Set(arr)
    }
  } catch (e) {
    likedIds.value = new Set()
  }
}

const saveLikedToStorage = () => {
  try {
    localStorage.setItem('likedPhotos', JSON.stringify(Array.from(likedIds.value)))
  } catch (e) {
    // ignore
  }
}

const likePhoto = async (photoId: number) => {
  if (likedIds.value.has(photoId)) {
    // unlike
    try {
      const res = await api.delete(`/photos/${photoId}/like`)
      const newCount = res.data
      likesMap.value.set(photoId, newCount)
      likedIds.value.delete(photoId)
      saveLikedToStorage()
    } catch (e) {
      console.error('unlike failed', e)
    }
  } else {
    // like
    try {
      const res = await api.post(`/photos/${photoId}/like`)
      const newCount = res.data
      likesMap.value.set(photoId, newCount)
      likedIds.value.add(photoId)
      saveLikedToStorage()
    } catch (e) {
      console.error('like failed', e)
    }
  }
}

// 初始化 likesMap 与 likedIds
watch(() => props.items, (items) => {
  items.forEach(i => {
    const pid = i.data?.id
    if (pid != null) {
      likesMap.value.set(pid, i.data?.likeCount || 0)
    }
  })
}, { immediate: true, deep: true })

onMounted(() => {
  loadLikedFromStorage()
})

// 计算列宽
const columnWidth = computed(() => {
  if (!containerRef.value) return 0
  const containerWidth = containerRef.value.clientWidth
  const gap = props.gap || 16
  const totalGap = gap * (props.columnCount - 1)
  return (containerWidth - totalGap) / props.columnCount
})

// 计算图片在列中的宽度和高度
const calculateItemSize = (item: MasonryItem): { width: number; height: number } => {
  const colWidth = columnWidth.value
  const aspectRatio = item.width / item.height

  // 始终确保图片宽度等于列宽，根据宽高比计算合适的高度
  let height = colWidth / aspectRatio

  // 限制高度范围，避免极端宽高比导致的显示问题
  const maxHeight = colWidth * 3 // 最大高度为列宽的3倍
  const minHeight = colWidth * 0.3 // 最小高度为列宽的0.3倍

  height = Math.max(minHeight, Math.min(maxHeight, height))

  return {
    width: colWidth,
    height: height
  }
}

// 计算瀑布流位置
const calculatePositions = () => {
  if (!containerRef.value || props.items.length === 0) return

  const positions: PositionedItem[] = []
  const columnHeights = new Array(props.columnCount).fill(0)
  const gap = props.gap || 16

  // 当多列为最短时，优先选择最左侧的最短列（自然实现从左到右填充）
  const tieEpsilon = 1 // 像素级阈值，用于处理浮点误差

  props.items.forEach((item) => {
    // 计算图片尺寸（提前计算以便后续使用）
    const size = calculateItemSize(item)

    // 找到当前最短列高度
    const minHeight = Math.min(...columnHeights)

    // 找出所有视为最短的列（高度 <= minHeight + tieEpsilon）
    const candidateIndex = columnHeights.findIndex(h => h <= minHeight + tieEpsilon)

    // candidateIndex 已经是最左侧符合条件的列索引
    const targetColumnIndex = candidateIndex >= 0 ? candidateIndex : columnHeights.indexOf(minHeight)

    // 计算位置
    const x = targetColumnIndex * (columnWidth.value + gap)
    const y = columnHeights[targetColumnIndex]

    // 创建样式对象
    const style: Record<string, any> = {
      position: 'absolute',
      left: `${x}px`,
      top: `${y}px`,
      width: `${size.width}px`,
      height: `${size.height}px`,
      transition: 'all 0.3s ease'
    }

    positions.push({
      id: item.id,
      data: item.data,
      style
    })

    // 更新列高度
    columnHeights[targetColumnIndex] += size.height + gap
  })

  // 设置容器高度为最高列的高度
  const maxHeight = Math.max(...columnHeights)
  if (containerRef.value) {
    containerRef.value.style.height = `${maxHeight}px`
  }

  positionedItems.value = positions
}

// 响应式重新计算
const recalculate = () => {
  nextTick(() => {
    calculatePositions()
  })
}

// 监听窗口大小变化
const handleResize = () => {
  recalculate()
}

let resizeObserver: ResizeObserver | null = null

onMounted(() => {
  window.addEventListener('resize', handleResize)

  // 使用 ResizeObserver 监听容器大小变化
  if (containerRef.value) {
    resizeObserver = new ResizeObserver(() => {
      recalculate()
    })
    resizeObserver.observe(containerRef.value)
  }

  // 初始计算位置
  recalculate()
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  if (resizeObserver) {
    resizeObserver.disconnect()
  }
})

// 监听 props 变化
watch(() => [props.items, props.columnCount, props.gap], recalculate, { deep: true })
</script>

<style scoped>
.masonry-layout {
  position: relative;
  width: 100%;
}

.masonry-item {
  will-change: transform;
  transform: translateZ(0);
}

/* like overlay */
.like-overlay {
  position: absolute;
  right: 8px;
  bottom: 8px;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 8px;
  border-radius: 9999px;
  background: rgba(0,0,0,0.28); /* 更低的不透明度，减少视觉干扰 */
  color: #fff;
  font-size: 12px;
  opacity: 0; /* 默认隐藏，只有 hover 或 visible 才显示 */
  transition: opacity 0.18s ease, transform 0.12s ease;
  cursor: pointer;
  pointer-events: auto;
}
/* 仅当鼠标悬浮在点赞按钮本身时显示（避免干扰看图） */
.like-overlay:hover {
  opacity: 0.95;
}
.like-overlay.visible {
  /* 如果已有点赞，则常驻显示，但采用较低不透明度以不打扰查看 */
  opacity: 0.6;
}
.like-overlay .heart {
  color: #fff;
  stroke: currentColor;
}
.like-overlay .heart[fill='#e11d48'] {
  color: #e11d48;
}
.like-count {
  font-weight: 600;
}
</style>
