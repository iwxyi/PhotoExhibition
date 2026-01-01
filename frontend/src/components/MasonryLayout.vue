<template>
  <div class="masonry-layout" ref="containerRef">
    <div
      v-for="(item, index) in positionedItems"
      :key="item.id"
      class="masonry-item"
      :style="item.style"
    >
      <slot :item="item.data" :index="index" />
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
</style>
