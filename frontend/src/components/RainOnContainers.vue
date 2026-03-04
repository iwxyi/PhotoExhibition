<template>
  <canvas
    ref="canvasRef"
    class="canvas-rain-on-containers"
    :class="{ 'above-layer': layer === 'above', 'background-layer': layer === 'background' }"
  />
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, watch, nextTick } from 'vue'

interface Props {
  count?: number
  layer?: 'above' | 'background'
  opacity?: number
  speedMultiplier?: number
  sizeMultiplier?: number
  interaction?: any
  intensity?: string
}

const props = withDefaults(defineProps<Props>(), {
  count: 100,
  layer: 'above',
  opacity: 0.7,
  speedMultiplier: 1,
  sizeMultiplier: 1,
  intensity: 'medium'
})

// 落在上边缘的雨滴
interface RainDrop {
  x: number
  y: number
  vx: number
  vy: number
  size: number
  opacity: number
  state: 'falling' | 'splashing' | 'done'
  splashParticles: SplashParticle[]
  age: number
}

// 溅起的水花
interface SplashParticle {
  x: number
  y: number
  vx: number
  vy: number
  size: number
  opacity: number
  age: number
}

// 从边缘流下的水流
interface WaterStream {
  x: number
  y: number
  targetRect: DOMRect
  side: 'left' | 'right'
  progress: number // 0-1, 沿图片边缘下行的进度
  opacity: number
  width: number
  age: number
}

// 从底部滴落的水滴
interface DripDrop {
  x: number
  y: number
  vx: number
  vy: number
  size: number
  opacity: number
  age: number
}

const canvasRef = ref<HTMLCanvasElement>()
let ctx: CanvasRenderingContext2D | null = null
let animId = 0
let lastTime = 0
let rainDrops: RainDrop[] = []
let waterStreams: WaterStream[] = []
let dripDrops: DripDrop[] = []
let isActive = false
let width = 0
let height = 0
let topRowContainers: DOMRect[] = [] // 只存储第一行的容器

const getIntensityConfig = () => {
  const intensity = props.intensity || 'medium'
  const configMap: Record<string, { dropCount: number; speed: number; splashChance: number; streamChance: number }> = {
    low: { dropCount: 40, speed: 1.5, splashChance: 0.6, streamChance: 0.4 },
    medium: { dropCount: 70, speed: 2.5, splashChance: 0.7, streamChance: 0.5 },
    high: { dropCount: 100, speed: 4, splashChance: 0.8, streamChance: 0.6 }
  }
  return configMap[intensity] || configMap.medium
}

// 收集第一行（最上面一行）的图片容器
const collectTopRowContainers = () => {
  const photoCards = document.querySelectorAll('.photo-card')
  const allRects: Array<{ rect: DOMRect; top: number }> = []

  photoCards.forEach(card => {
    const rect = card.getBoundingClientRect()
    if (rect.width > 0 && rect.height > 0 && rect.bottom > 0 && rect.top < height) {
      allRects.push({ rect, top: rect.top })
    }
  })

  // 按top位置排序，找出最上面的一排
  allRects.sort((a, b) => a.top - b.top)

  // 找出第一行的高度范围（允许10px误差）
  if (allRects.length > 0) {
    const firstRowTop = allRects[0].top
    const firstRowBottom = firstRowTop + allRects[0].rect.height

    // 只保留第一行的容器
    topRowContainers = allRects
      .filter(({ rect, top }) => {
        return Math.abs(top - firstRowTop) < 15 || (top >= firstRowTop && top < firstRowBottom)
      })
      .map(({ rect }) => rect)
  } else {
    topRowContainers = []
  }
}

const spawnRainDrop = (initialSpawn: boolean): RainDrop => {
  const config = getIntensityConfig()
  const baseSpeed = config.speed * props.speedMultiplier
  const size = (2 + Math.random() * 2) * props.sizeMultiplier

  let x: number, y: number

  if (initialSpawn) {
    x = Math.random() * width
    y = Math.random() * height * 0.3
  } else {
    x = Math.random() * width
    y = -size * 2
  }

  return {
    x,
    y,
    vx: (Math.random() - 0.5) * 10,
    vy: baseSpeed + Math.random() * baseSpeed * 0.5,
    size,
    opacity: 0,
    state: 'falling',
    splashParticles: [],
    age: 0
  }
}

const spawnSplashParticle = (x: number, y: number): SplashParticle => {
  const angle = Math.random() * Math.PI // 向上的半圆
  const speed = 30 + Math.random() * 50
  return {
    x,
    y,
    vx: Math.cos(angle) * speed * (Math.random() > 0.5 ? 1 : -1),
    vy: -Math.sin(angle) * speed,
    size: 1 + Math.random() * 2,
    opacity: 0.8,
    age: 0
  }
}

const spawnDripDrop = (x: number, y: number): DripDrop => {
  return {
    x,
    y,
    vx: (Math.random() - 0.5) * 3,
    vy: 0,
    size: 2 + Math.random() * 2,
    opacity: 0.7,
    age: 0
  }
}

const updateRainDrop = (drop: RainDrop, dt: number): boolean => {
  const dtSec = dt * 0.001
  drop.age += dt

  if (drop.state === 'falling') {
    drop.opacity = Math.min(drop.opacity + dt * 0.003, props.opacity)
    drop.vy += 80 * dtSec // 重力
    drop.x += drop.vx * dtSec
    drop.y += drop.vy * dtSec

    const margin = 50
    if (drop.y > height + margin || drop.x < -margin || drop.x > width + margin) {
      return false
    }

    // 检测是否落在第一行容器的上边缘
    for (const rect of topRowContainers) {
      // 检查是否碰到容器的上边缘区域（允许一点误差）
      const hitZoneTop = rect.top - 5
      const hitZoneBottom = rect.top + 8

      if (drop.x >= rect.left && drop.x <= rect.right &&
          drop.y >= hitZoneTop && drop.y <= hitZoneBottom) {
        // 碰撞！开始溅起
        drop.state = 'splashing'
        drop.y = rect.top // 修正位置到上边缘

        const config = getIntensityConfig()

        // 生成溅起的水花
        const splashCount = Math.floor(3 + Math.random() * 4)
        for (let i = 0; i < splashCount; i++) {
          drop.splashParticles.push(spawnSplashParticle(drop.x, rect.top))
        }

        // 决定是否产生水流（从左边或右边流下）
        if (Math.random() < config.streamChance) {
          const side: 'left' | 'right' = drop.x < rect.left + rect.width / 2 ? 'left' : 'right'
          waterStreams.push({
            x: side === 'left' ? rect.left : rect.right,
            y: rect.top,
            targetRect: rect,
            side,
            progress: 0,
            opacity: props.opacity * 0.8,
            width: 2 + Math.random() * 2,
            age: 0
          })
        }
        return true
      }
    }
  } else if (drop.state === 'splashing') {
    // 更新溅起的水花
    for (let i = drop.splashParticles.length - 1; i >= 0; i--) {
      const p = drop.splashParticles[i]
      p.age += dt
      p.vy += 100 * dtSec // 重力
      p.x += p.vx * dtSec
      p.y += p.vy * dtSec
      p.opacity *= 0.96

      if (p.opacity < 0.05 || p.y > height) {
        drop.splashParticles.splice(i, 1)
      }
    }

    // 溅起结束后移除
    if (drop.splashParticles.length === 0) {
      return false
    }
  }

  return drop.age < 12000
}

const updateWaterStream = (stream: WaterStream, dt: number): boolean => {
  const dtSec = dt * 0.001
  stream.age += dt

  const flowSpeed = 50 * dtSec // 沿边缘下行的速度
  const rect = stream.targetRect

  // 检查容器是否仍然有效
  if (!rect || rect.width === 0) {
    return false
  }

  // 更新进度（从顶部到底部）
  stream.progress += flowSpeed / rect.height
  stream.y = rect.top + stream.progress * rect.height

  // 计算x坐标（沿左或右边缘）
  // 边缘会稍微向内弯曲一点，模拟水流沿着边缘的效果
  const edgeOffset = Math.sin(stream.progress * Math.PI) * 3
  if (stream.side === 'left') {
    stream.x = rect.left + edgeOffset
  } else {
    stream.x = rect.right - edgeOffset
  }

  stream.opacity *= 0.997 // 缓慢消失

  // 如果水流到达底部
  if (stream.progress >= 1) {
    // 在底部角落产生滴落
    if (Math.random() < 0.5) {
      dripDrops.push(spawnDripDrop(stream.x, rect.bottom))
    }
    return false
  }

  if (stream.opacity < 0.03 || stream.age > 8000) {
    return false
  }

  return true
}

const updateDripDrop = (drop: DripDrop, dt: number): boolean => {
  const dtSec = dt * 0.001
  drop.age += dt

  drop.vy += 120 * dtSec // 重力，稍大一点
  drop.x += drop.vx * dtSec
  drop.y += drop.vy * dtSec

  // 拉长效果
  drop.opacity *= 0.992

  if (drop.y > height + 20 || drop.opacity < 0.02) {
    return false
  }

  return drop.age < 4000
}

const render = (timestamp: number) => {
  if (!isActive || !ctx) return

  const dt = lastTime ? Math.min(timestamp - lastTime, 50) : 16
  lastTime = timestamp

  ctx.clearRect(0, 0, width, height)

  // 收集第一行容器
  collectTopRowContainers()

  // 更新所有粒子
  rainDrops = rainDrops.filter(drop => updateRainDrop(drop, dt))
  waterStreams = waterStreams.filter(stream => updateWaterStream(stream, dt))
  dripDrops = dripDrops.filter(drop => updateDripDrop(drop, dt))

  const targetCount = Math.floor(getIntensityConfig().dropCount * props.sizeMultiplier)
  while (rainDrops.length < targetCount) rainDrops.push(spawnRainDrop(false))

  // 绘制落下的雨滴（细长的雨丝）
  ctx.strokeStyle = 'rgba(180, 200, 230, 0.5)'
  ctx.lineWidth = 1.2
  for (const drop of rainDrops) {
    if (drop.state === 'falling') {
      ctx.globalAlpha = drop.opacity * 0.7
      ctx.beginPath()
      ctx.moveTo(drop.x, drop.y)
      // 雨滴是拉长的
      ctx.lineTo(drop.x - drop.vx * 0.08, drop.y - drop.size * 3)
      ctx.stroke()
    }
  }

  // 绘制溅起的水花
  for (const drop of rainDrops) {
    if (drop.state === 'splashing') {
      for (const p of drop.splashParticles) {
        ctx.globalAlpha = p.opacity * 0.8
        ctx.fillStyle = 'rgba(200, 220, 240, 0.8)'
        ctx.beginPath()
        ctx.arc(p.x, p.y, p.size, 0, Math.PI * 2)
        ctx.fill()
      }
    }
  }

  // 绘制沿边缘流下的水流
  ctx.strokeStyle = 'rgba(160, 180, 210, 0.6)'
  ctx.lineWidth = 2
  ctx.lineCap = 'round'
  for (const stream of waterStreams) {
    ctx.globalAlpha = stream.opacity
    ctx.beginPath()
    ctx.moveTo(stream.x - stream.width / 2, stream.y)
    ctx.lineTo(stream.x + stream.width / 2, stream.y)
    ctx.stroke()
  }

  // 绘制底部滴落的水滴（拉长的椭圆）
  ctx.fillStyle = 'rgba(180, 200, 230, 0.7)'
  for (const drop of dripDrops) {
    ctx.globalAlpha = drop.opacity
    ctx.beginPath()
    // 拉长的水滴形状
    ctx.ellipse(drop.x, drop.y, drop.size * 0.4, drop.size * 1.5, 0, 0, Math.PI * 2)
    ctx.fill()
  }

  animId = requestAnimationFrame(render)
}

const resizeCanvas = () => {
  const canvas = canvasRef.value
  if (!canvas) return
  const dpr = window.devicePixelRatio || 1
  width = window.innerWidth
  height = window.innerHeight
  canvas.width = width * dpr
  canvas.height = height * dpr
  canvas.style.width = width + 'px'
  canvas.style.height = height + 'px'
  ctx = canvas.getContext('2d')
  if (ctx) ctx.scale(dpr, dpr)
}

const start = () => {
  if (isActive) return
  isActive = true
  lastTime = 0
  rainDrops = []
  waterStreams = []
  dripDrops = []
  resizeCanvas()

  const targetCount = Math.floor(getIntensityConfig().dropCount * props.sizeMultiplier)
  const initialCount = Math.min(targetCount, Math.floor(targetCount * 0.4))
  for (let i = 0; i < initialCount; i++) rainDrops.push(spawnRainDrop(true))

  animId = requestAnimationFrame(render)
}

const stop = () => {
  isActive = false
  if (animId) { cancelAnimationFrame(animId); animId = 0 }
  rainDrops = []
  waterStreams = []
  dripDrops = []
  if (ctx) ctx.clearRect(0, 0, width, height)
}

let resizeHandler: (() => void) | null = null
let containerObserver: MutationObserver | null = null
let scrollHandler: (() => void) | null = null

onMounted(() => {
  resizeHandler = () => { if (isActive) resizeCanvas() }
  window.addEventListener('resize', resizeHandler)

  scrollHandler = () => {
    if (isActive) {
      // 滚动时更新容器位置
      collectTopRowContainers()
    }
  }
  window.addEventListener('scroll', scrollHandler, { passive: true })

  containerObserver = new MutationObserver(() => {
    if (isActive) {
      collectTopRowContainers()
    }
  })
  containerObserver.observe(document.body, { childList: true, subtree: true })

  nextTick(() => start())
})

onBeforeUnmount(() => {
  stop()
  if (resizeHandler) window.removeEventListener('resize', resizeHandler)
  if (scrollHandler) window.removeEventListener('scroll', scrollHandler)
  if (containerObserver) containerObserver.disconnect()
})

watch(() => [props.count, props.opacity, props.speedMultiplier, props.sizeMultiplier, props.intensity], () => {
  stop()
  nextTick(() => start())
}, { deep: true })
</script>

<style scoped>
.canvas-rain-on-containers {
  position: fixed;
  top: 0;
  left: 0;
  pointer-events: none;
  overflow: hidden;
}
.canvas-rain-on-containers.above-layer { z-index: 3; }
.canvas-rain-on-containers.background-layer { z-index: 0; }
</style>
