<template>
  <div class="masonry-layout" ref="containerRef">
    <div
      v-for="(item, index) in positionedItems"
      :key="item.id"
      class="masonry-item"
      :style="item.style"
    >
          <slot :item="item.data" :index="index" @image-loaded="handleImageLoaded" />
      <!-- 点赞覆盖层 -->
      <div
        class="like-overlay"
        :class="{ 'visible': (likedIds.has(item.data?.id) || (likesMap.get(item.data?.id) || 0) > 0) }"
        @click.stop="likePhoto(item.data?.id, $event)"
        title="点赞"
      >
        <!-- 可扩展的按钮容器：heart + count -->
        <div :class="['like-btn', { liked: likedIds.has(item.data?.id) }]">
          <svg :class="['heart', { liked: likedIds.has(item.data?.id) }]" viewBox="0 0 24 24" width="18" height="18" xmlns="http://www.w3.org/2000/svg" aria-hidden="true" focusable="false">
            <path
              class="heart-path"
              stroke="currentColor"
              stroke-width="1.5"
              d="M12 20.35l-2.25-2.25c-2.75-2.75-5.5-5.5-5.5-8.5c0-2.5 2-4.5 4.5-4.5c1.25 0 2.5 .625 3.5 1.75c1-.875 2.25-1.75 3.5-1.75c2.5 0 4.5 2 4.5 4.5c0 3-2.75 5.75-5.5 8.5L12 20.35z"
            />
          </svg>
          <span v-if="(likesMap.get(item.data?.id) || 0) > 0" class="like-count">{{ likesMap.get(item.data?.id) }}</span>
        </div>
        <!-- burst container removed (canvas-based burst used) -->
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

const likePhoto = async (photoId: number, ev?: Event) => {
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
      // show burst animation for this photo (canvas) at click coordinates if available
      try {
        const x = ev && (ev as MouseEvent).clientX
        const y = ev && (ev as MouseEvent).clientY
        triggerCanvasBurstFor(photoId, x as number | undefined, y as number | undefined)
      } catch (e) {
        // ignore
      }
      // visual pop on the clicked button
      try {
        const target = ev && (ev.target as HTMLElement)
        const btn = target?.closest?.('.like-btn') as HTMLElement | null
        let cx: number | undefined
        let cy: number | undefined
        if (btn) {
          // prefer heart center
          const heart = btn.querySelector<HTMLElement>('.heart')
          const rect = (heart || btn).getBoundingClientRect()
          cx = rect.left + rect.width / 2
          cy = rect.top + rect.height / 2
          btn.classList.add('pop')
          setTimeout(() => btn.classList.remove('pop'), 420)
        }
        // trigger canvas burst at exact heart center if available
        if (typeof cx === 'number' && typeof cy === 'number') {
          triggerCanvasBurstFor(photoId, cx, cy)
        }
      } catch (e) {
        // ignore
      }
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

// (DOM-based burst removed — using canvas-based burst only)

// --------------------------
// Canvas burst implementation
// --------------------------
let canvasEl: HTMLCanvasElement | null = null
let ctx: CanvasRenderingContext2D | null = null
let particles: Array<any> = []
let rafId: number | null = null

const ensureCanvas = () => {
  if (canvasEl && ctx) return
  canvasEl = document.createElement('canvas')
  canvasEl.style.position = 'fixed'
  canvasEl.style.left = '0'
  canvasEl.style.top = '0'
  canvasEl.style.width = '100%'
  canvasEl.style.height = '100%'
  canvasEl.style.pointerEvents = 'none'
  canvasEl.style.zIndex = '2147483646'
  canvasEl.width = window.innerWidth
  canvasEl.height = window.innerHeight
  document.body.appendChild(canvasEl)
  ctx = canvasEl.getContext('2d')
  window.addEventListener('resize', () => {
    if (!canvasEl) return
    canvasEl.width = window.innerWidth
    canvasEl.height = window.innerHeight
  })
  startLoop()
}

const startLoop = () => {
  if (rafId) return
  const loop = (t: number) => {
    if (!ctx || !canvasEl) return
    ctx.clearRect(0, 0, canvasEl.width, canvasEl.height)
    const now = performance.now()
    particles = particles.filter(p => {
      const dt = (now - p.t0) / p.life
      if (dt >= 1) return false

      // 前半生命周期：向上发射，后半生命周期：重力下坠
      const gravityStart = 0.4 // 生命周期40%后开始重力
      const gravityFactor = dt > gravityStart ? (dt - gravityStart) / (1 - gravityStart) : 0

      const x = p.x + p.vx * dt
      const y = p.y + p.vy * dt + 0.5 * p.gravity * gravityFactor * gravityFactor

      const alpha = dt < 0.3 ? 1 : 1 - ((dt - 0.3) / 0.7) // 前30%完全不透明，后70%渐隐
      ctx.globalAlpha = alpha
      ctx.fillStyle = p.color
      ctx.beginPath()
      ctx.arc(x, y, p.size * (1 - dt) + 0.5, 0, Math.PI * 2)
      ctx.fill()
      return true
    })
    rafId = requestAnimationFrame(loop)
  }
  rafId = requestAnimationFrame(loop)
}

const spawnCanvasBurst = (x: number, y: number) => {
  ensureCanvas()
  const count = 12
  // main colorful particles - upward burst only
  for (let i = 0; i < count; i++) {
    // distribute particles upward (-π/2 ± π/6, i.e., -90° ± 30°)
    const angleRange = Math.PI / 6 * 2 // 60° total range around upward
    const baseAngle = -Math.PI / 2 + (angleRange / (count - 1)) * i - angleRange / 2 // center around -90°
    const angle = baseAngle + (Math.random() - 0.5) * 0.3 // small random variation
    const speed = 70 + Math.random() * 90
    particles.push({
      x, y,
      vx: Math.cos(angle) * speed,
      vy: Math.sin(angle) * speed,
      gravity: 60 + Math.random() * 80,
      size: 2 + Math.random() * 2,
      color: `hsl(${Math.random() * 360}, 95%, ${50 + Math.random() * 20}%)`,
      t0: performance.now(),
      life: 900 + Math.random() * 400
    })
  }
  // small sparkles - upward burst only
  const sparks = 6
  for (let i = 0; i < sparks; i++) {
    // random angle upward (-π/2 ± π/12, i.e., -90° ± 15°)
    const angle = -Math.PI / 2 + (Math.random() - 0.5) * (Math.PI / 12 * 2)
    const speed = 30 + Math.random() * 45
    particles.push({
      x, y,
      vx: Math.cos(angle) * speed,
      vy: Math.sin(angle) * speed,
      gravity: 20 + Math.random() * 30,
      size: 0.8 + Math.random() * 1.2,
      color: `rgba(255,255,255,${0.8 + Math.random() * 0.2})`,
      t0: performance.now(),
      life: 700 + Math.random() * 300
    })
  }
}

const triggerCanvasBurstFor = (photoId: number, clientX?: number, clientY?: number) => {
  // prefer explicit click coordinates (clientX, clientY)
  if (typeof clientX === 'number' && typeof clientY === 'number') {
    spawnCanvasBurst(clientX, clientY)
    return
  }
  // fallback: find DOM element rendered for this photo (data-photo-id attribute) and use center
  const el = document.querySelector(`[data-photo-id='${photoId}']`) as HTMLElement | null
  let x = window.innerWidth / 2
  let y = window.innerHeight / 2
  if (el) {
    const r = el.getBoundingClientRect()
    x = r.left + r.width / 2
    y = r.top + r.height / 2
  }
  spawnCanvasBurst(x, y)
}

// 列宽现在在calculatePositions中实时计算

// 计算图片在列中的宽度和高度
const calculateItemSize = (item: MasonryItem, colWidth: number): { width: number; height: number } => {
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

  // 实时计算列宽，确保使用最新的容器尺寸
  const containerWidth = containerRef.value.clientWidth
  const totalGap = gap * (props.columnCount - 1)
  const currentColumnWidth = (containerWidth - totalGap) / props.columnCount

  // 当多列为最短时，优先选择最左侧的最短列（自然实现从左到右填充）
  const tieEpsilon = 1 // 像素级阈值，用于处理浮点误差

  props.items.forEach((item) => {
    // 计算图片尺寸（提前计算以便后续使用）
    const size = calculateItemSize(item, currentColumnWidth)

    // 找到当前最短列高度
    const minHeight = Math.min(...columnHeights)

    // 找出所有视为最短的列（高度 <= minHeight + tieEpsilon）
    const candidateIndex = columnHeights.findIndex(h => h <= minHeight + tieEpsilon)

    // candidateIndex 已经是最左侧符合条件的列索引
    const targetColumnIndex = candidateIndex >= 0 ? candidateIndex : columnHeights.indexOf(minHeight)

    // 计算位置 - 使用实时计算的列宽
    const x = targetColumnIndex * (currentColumnWidth + gap)
    const y = columnHeights[targetColumnIndex]

    // 确保位置不会超出容器边界
    const maxLeft = containerWidth - size.width
    const safeX = Math.min(x, maxLeft)

    // 创建样式对象
    const style: Record<string, any> = {
      position: 'absolute',
      left: `${safeX}px`,
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
    // 确保使用最新的容器尺寸重新计算
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

// 处理图片加载完成事件
const handleImageLoaded = () => {
  // 图片加载完成后延迟重新计算，确保DOM已更新
  nextTick(() => {
    setTimeout(() => {
      recalculate()
    }, 50)
  })
}

// 监听 props 变化
watch(() => [props.items, props.columnCount, props.gap], recalculate, { deep: true })
</script>

<style scoped>
.masonry-layout {
  position: relative;
  width: 100%;
}

.masonry-layout {
  position: relative;
  z-index: 1;
}

.masonry-item {
  will-change: transform;
  transform: translateZ(0);
}

/* like overlay */
.like-overlay {
  position: absolute;
  right: 6px;
  bottom: 6px;
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px 6px;
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
.like-overlay.visible:hover {
  /* 常驻显示的点赞按钮在hover时也保持较低透明度 */
  opacity: 0.6 !important;
}
.like-overlay .heart {
  color: #fff;
  stroke: currentColor;
}
.like-overlay .heart[fill='#e11d48'] {
  color: #e11d48;
}
.like-count {
  font-weight: 600 !important;
  font-size: 11px !important;
  color: #fff !important;
  text-shadow: 0 1px 2px rgba(0,0,0,0.5) !important;
  display: inline !important;
  visibility: visible !important;
  opacity: 1 !important;
}

/* heart animation */
.like-overlay .heart {
  transition: transform 220ms cubic-bezier(.2,.9,.3,1), color 180ms ease, stroke 180ms ease, fill 180ms ease;
  transform-origin: center center;
  display: inline-block;
}
.like-overlay .heart.liked {
  transform: scale(1.25);
  color: #e11d48;
}
.like-overlay .heart .heart-path {
  fill: transparent;
  transition: fill 220ms ease;
}
.like-overlay .heart.liked .heart-path {
  fill: #e11d48;
}

/* burst dots */
/* DOM-based burst styles removed (using canvas burst now) */
@keyframes burst {
  to {
    transform: translate(var(--tx), var(--ty)) scale(0.9);
    opacity: 0;
  }
}

/* Ensure like-count visible when overlay visible (someone liked) */
.like-overlay.visible .like-count {
  transform: translateX(0);
  opacity: 1;
}
</style>

<style scoped>
/* like button expansion and smooth shift */
.like-btn {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  padding: 2px 4px;
  border-radius: 9999px;
  background: transparent;
  transition: padding 260ms cubic-bezier(.2,.9,.3,1);
  overflow: hidden;
  white-space: nowrap;
}
.like-btn .heart {
  transition: transform 220ms cubic-bezier(.2,.9,.3,1);
  transform-origin: left center;
  align-self: center;
  vertical-align: middle;
}
.like-btn .like-count {
  transform: translateX(2px);
  opacity: 0;
  transition: transform 260ms cubic-bezier(.2,.9,.3,1), opacity 200ms ease;
}
.like-btn.liked {
  padding-left: 2px;
  padding-right: 2px;
}
.like-btn.liked .heart {
  transform: translateX(-2px) scale(1.12);
}
.like-btn.liked .like-count {
  transform: translateX(0);
  opacity: 1;
}

/* pop animation when clicked */
.like-btn.pop {
  animation: pop 420ms cubic-bezier(.2,.9,.3,1);
}
@keyframes pop {
  0% { transform: scale(1); }
  30% { transform: scale(1.28); }
  60% { transform: scale(0.98); }
  100% { transform: scale(1); }
}

/* body burst dot style */
.burst-dot-body {
  position: fixed;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  pointer-events: none;
  background: radial-gradient(circle, #ffd166 0%, #ff7b7b 60%);
  transform: translate(0,0) scale(0.6);
  animation: burst 480ms cubic-bezier(.2,.9,.3,1) forwards;
  z-index: 9999;
}

.burst-ring {
  position: fixed;
  width: 48px;
  height: 48px;
  border-radius: 50%;
  border: 2px solid rgba(255, 123, 123, 0.9);
  pointer-events: none;
  transform: scale(0.6);
  animation: ringpop 520ms cubic-bezier(.2,.9,.3,1) forwards;
  z-index: 9999;
}
@keyframes ringpop {
  to {
    transform: scale(1.3);
    opacity: 0;
  }
}
</style>

<style>
.burst-dot-body {
  position: fixed;
  width: 12px;
  height: 12px;
  border-radius: 50%;
  pointer-events: none;
  background: radial-gradient(circle, #ffd166 0%, #ff7b7b 60%);
  transform: translate3d(0,0,0) scale(0.6);
  will-change: transform, opacity;
  animation: burst 520ms cubic-bezier(.2,.9,.3,1) forwards;
  z-index: 2147483647;
}
.burst-ring {
  position: fixed;
  width: 56px;
  height: 56px;
  border-radius: 50%;
  border: 3px solid rgba(255, 123, 123, 0.98);
  box-shadow: 0 0 18px rgba(255, 123, 123, 0.6);
  pointer-events: none;
  transform: scale(0.6);
  animation: ringpop 520ms cubic-bezier(.2,.9,.3,1) forwards;
  z-index: 2147483647;
}
</style>
