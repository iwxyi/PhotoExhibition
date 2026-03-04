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
  state: 'falling' | 'splashing'
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

// 在图片上流淌的水流
interface WaterFlow {
  id: number
  side: 'left' | 'right'
  hitX: number // 原始落点相对于图片宽度的位置
  // 动态计算的位置（每一帧重新获取）
  getRect: () => DOMRect | null
  progress: number // 0-1, 沿图片表面下行的进度
  flowProgress: number // >1 时表示已经流到图片底部继续往下
  width: number
  opacity: number
  speed: number
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
let waterFlows: WaterFlow[] = []
let dripDrops: DripDrop[] = []
let isActive = false
let width = 0
let height = 0
let topRowContainers: Array<{ rect: DOMRect; element: Element }> = []
let flowIdCounter = 0

const getIntensityConfig = () => {
  const intensity = props.intensity || 'medium'
  const configMap: Record<string, { dropCount: number; baseSpeed: number; flowSpeed: number; splashChance: number }> = {
    low: { dropCount: 50, baseSpeed: 1.2, flowSpeed: 0.8, splashChance: 0.7 },
    medium: { dropCount: 80, baseSpeed: 2.0, flowSpeed: 1.2, splashChance: 0.8 },
    high: { dropCount: 120, baseSpeed: 3.5, flowSpeed: 2.0, splashChance: 0.9 }
  }
  return configMap[intensity] || configMap.medium
}

// 收集第一行（最上面一行）的图片容器
const collectTopRowContainers = () => {
  const photoCards = document.querySelectorAll('.photo-card')
  const allRects: Array<{ rect: DOMRect; element: Element; top: number }> = []

  photoCards.forEach(card => {
    const rect = card.getBoundingClientRect()
    if (rect.width > 0 && rect.height > 0 && rect.bottom > 0 && rect.top < height) {
      allRects.push({ rect, element: card, top: rect.top })
    }
  })

  // 按top位置排序，找出最上面的一排
  allRects.sort((a, b) => a.top - b.top)

  // 找出第一行
  if (allRects.length > 0) {
    const firstRowTop = allRects[0].top
    // 只保留第一行的容器
    topRowContainers = allRects
      .filter(({ top }) => Math.abs(top - firstRowTop) < 12)
      .map(({ rect, element }) => ({ rect, element }))
  } else {
    topRowContainers = []
  }
}

const spawnRainDrop = (initialSpawn: boolean): RainDrop => {
  const config = getIntensityConfig()
  const baseSpeed = config.baseSpeed * props.speedMultiplier
  const size = (2 + Math.random() * 2.5) * props.sizeMultiplier

  let x: number, y: number

  if (initialSpawn) {
    x = Math.random() * width
    y = Math.random() * height * 0.4
  } else {
    x = Math.random() * width
    y = -size * 2
  }

  return {
    x,
    y,
    vx: (Math.random() - 0.5) * 15,
    vy: baseSpeed + Math.random() * baseSpeed * 0.5,
    size,
    opacity: 0,
    state: 'falling',
    splashParticles: [],
    age: 0
  }
}

const spawnSplashParticle = (x: number, y: number): SplashParticle => {
  const angle = Math.random() * Math.PI
  const speed = 40 + Math.random() * 60
  return {
    x,
    y,
    vx: Math.cos(angle) * speed * (Math.random() > 0.5 ? 1 : -1),
    vy: -Math.sin(angle) * speed,
    size: 1.5 + Math.random() * 2.5,
    opacity: 0.9,
    age: 0
  }
}

const spawnDripDrop = (x: number, y: number): DripDrop => {
  return {
    x,
    y,
    vx: (Math.random() - 0.5) * 4,
    vy: 0,
    size: 2.5 + Math.random() * 2.5,
    opacity: 0.75,
    age: 0
  }
}

// 创建新的水流 - 使用 getter 函数来动态获取容器位置
const createWaterFlow = (element: Element, side: 'left' | 'right', hitX: number): WaterFlow => {
  const config = getIntensityConfig()
  return {
    id: flowIdCounter++,
    side,
    hitX,
    getRect: () => {
      const rect = element.getBoundingClientRect()
      // 检查容器是否仍然在可视区域内
      if (rect.width > 0 && rect.height > 0 && rect.bottom > 0 && rect.top < height) {
        return rect
      }
      return null
    },
    progress: 0,
    flowProgress: 0,
    width: 2 + Math.random() * 2,
    opacity: props.opacity * 0.8,
    speed: config.flowSpeed * props.speedMultiplier,
    age: 0
  }
}

const updateRainDrop = (drop: RainDrop, dt: number): boolean => {
  const dtSec = dt * 0.001
  drop.age += dt

  if (drop.state === 'falling') {
    drop.opacity = Math.min(drop.opacity + dt * 0.003, props.opacity)
    drop.vy += 100 * dtSec
    drop.x += drop.vx * dtSec
    drop.y += drop.vy * dtSec

    const margin = 50
    if (drop.y > height + margin || drop.x < -margin || drop.x > width + margin) {
      return false
    }

    // 检测是否落在第一行容器的上边缘
    for (const { rect, element } of topRowContainers) {
      const hitZoneTop = rect.top - 3
      const hitZoneBottom = rect.top + 10

      if (drop.x >= rect.left && drop.x <= rect.right &&
          drop.y >= hitZoneTop && drop.y <= hitZoneBottom) {
        drop.state = 'splashing'
        drop.y = rect.top

        const config = getIntensityConfig()
        const hitX = (drop.x - rect.left) / rect.width

        // 生成溅起的水花
        const splashCount = Math.floor(4 + Math.random() * 5)
        for (let i = 0; i < splashCount; i++) {
          drop.splashParticles.push(spawnSplashParticle(drop.x, rect.top))
        }

        // 根据落点位置决定水流方向
        const flowSide: 'left' | 'right' = hitX < 0.5 ? 'left' : 'right'

        // 创建水流
        if (Math.random() < config.splashChance) {
          waterFlows.push(createWaterFlow(element, flowSide, hitX))
        }

        return true
      }
    }
  } else if (drop.state === 'splashing') {
    for (let i = drop.splashParticles.length - 1; i >= 0; i--) {
      const p = drop.splashParticles[i]
      p.age += dt
      p.vy += 120 * dtSec
      p.x += p.vx * dtSec
      p.y += p.vy * dtSec
      p.opacity *= 0.94

      if (p.opacity < 0.05 || p.y > height) {
        drop.splashParticles.splice(i, 1)
      }
    }

    if (drop.splashParticles.length === 0) {
      return false
    }
  }

  return drop.age < 10000
}

const updateWaterFlow = (flow: WaterFlow, dt: number): boolean => {
  const dtSec = dt * 0.001
  flow.age += dt

  // 动态获取当前容器位置
  const rect = flow.getRect()
  if (!rect) {
    return false
  }

  const flowAcceleration = Math.min(flow.age / 2000, 1.5)
  const currentSpeed = flow.speed * (1 + flowAcceleration * 0.5)

  const flowPixelSpeed = currentSpeed * 60 * dtSec
  flow.progress += flowPixelSpeed / rect.height
  flow.flowProgress = flow.progress

  // 超过图片底部后继续往下流
  if (flow.progress >= 1) {
    // 额外往下流的距离（像素）
    const extraFlow = (flow.progress - 1) * rect.height
    flow.flowProgress = 1 + extraFlow / 100 // 归一化，每100px算1个进度

    // 到达底部后产生滴落（只产生一次）
    if (flow.progress >= 1 && flow.progress < 1 + dtSec * currentSpeed) {
      const edgeX = flow.side === 'left' ? rect.left : rect.right
      if (Math.random() < 0.5) {
        dripDrops.push(spawnDripDrop(edgeX, rect.bottom))
      }
    }
  }

  // 超过时间限制或透明度太低
  const maxFlowProgress = 2.5 // 最多流到图片底部往下150px
  if (flow.age > 8000 || flow.flowProgress > maxFlowProgress) {
    return false
  }

  return true
}

const updateDripDrop = (drop: DripDrop, dt: number): boolean => {
  const dtSec = dt * 0.001
  drop.age += dt

  drop.vy += 150 * dtSec
  drop.x += drop.vx * dtSec
  drop.y += drop.vy * dtSec
  drop.opacity *= 0.99

  if (drop.y > height + 20 || drop.opacity < 0.02) {
    return false
  }

  return drop.age < 3000
}

const render = (timestamp: number) => {
  if (!isActive || !ctx) return

  const dt = lastTime ? Math.min(timestamp - lastTime, 50) : 16
  lastTime = timestamp

  ctx.clearRect(0, 0, width, height)

  collectTopRowContainers()

  rainDrops = rainDrops.filter(drop => updateRainDrop(drop, dt))
  waterFlows = waterFlows.filter(flow => updateWaterFlow(flow, dt))
  dripDrops = dripDrops.filter(drop => updateDripDrop(drop, dt))

  const targetCount = Math.floor(getIntensityConfig().dropCount * props.sizeMultiplier)
  while (rainDrops.length < targetCount) rainDrops.push(spawnRainDrop(false))

  // 绘制雨滴
  ctx.strokeStyle = 'rgba(170, 190, 220, 0.5)'
  ctx.lineWidth = 1.2
  for (const drop of rainDrops) {
    if (drop.state === 'falling') {
      ctx.globalAlpha = drop.opacity * 0.6
      ctx.beginPath()
      ctx.moveTo(drop.x, drop.y)
      ctx.lineTo(drop.x - drop.vx * 0.06, drop.y - drop.size * 2.5)
      ctx.stroke()
    }
  }

  // 绘制溅起的水花
  for (const drop of rainDrops) {
    if (drop.state === 'splashing') {
      for (const p of drop.splashParticles) {
        ctx.globalAlpha = p.opacity * 0.85
        ctx.fillStyle = 'rgba(200, 220, 240, 0.85)'
        ctx.beginPath()
        ctx.arc(p.x, p.y, p.size, 0, Math.PI * 2)
        ctx.fill()
      }
    }
  }

  // 绘制水流 - 单层连续的水流，更自然
  for (const flow of waterFlows) {
    const rect = flow.getRect()
    if (!rect) continue

    const side = flow.side
    const edgeX = side === 'left' ? rect.left : rect.right
    const rectHeight = rect.height

    // 计算当前进度（可能超过1）
    let currentY: number
    let currentOpacity: number
    let currentWidth: number

    if (flow.flowProgress <= 1) {
      // 在图片内部
      currentY = rect.top + flow.flowProgress * rectHeight
      currentOpacity = flow.opacity * (1 - flow.flowProgress * 0.4)
      // 宽度逐渐变宽
      const topWidth = flow.width * 1.5
      const bottomWidth = flow.width * 5
      currentWidth = topWidth + (bottomWidth - topWidth) * flow.flowProgress
    } else {
      // 流出图片底部，继续往下并逐渐变淡消失
      const extraProgress = (flow.flowProgress - 1) * 100 // 每100px
      currentY = rect.bottom + extraProgress
      // 透明度快速衰减
      currentOpacity = flow.opacity * 0.6 * Math.max(0, 1 - extraProgress / 150)
      // 宽度逐渐变窄然后消失
      currentWidth = flow.width * 5 * Math.max(0.3, 1 - extraProgress / 200)
    }

    if (currentOpacity < 0.02) continue

    // 绘制连续的水流带 - 单层渐变，更自然
    const gradient = ctx.createLinearGradient(
      side === 'left' ? edgeX - currentWidth : edgeX,
      rect.top,
      side === 'left' ? edgeX : edgeX + currentWidth,
      currentY
    )

    if (flow.flowProgress <= 1) {
      // 在图片内部：从上到下渐变
      gradient.addColorStop(0, `rgba(165, 190, 220, ${currentOpacity})`)
      gradient.addColorStop(0.5, `rgba(170, 195, 225, ${currentOpacity * 0.9})`)
      gradient.addColorStop(1, `rgba(175, 200, 230, ${currentOpacity * 0.7})`)
    } else {
      // 流出底部：快速变淡
      gradient.addColorStop(0, `rgba(170, 195, 225, ${currentOpacity * 0.8})`)
      gradient.addColorStop(1, `rgba(180, 205, 235, ${currentOpacity * 0.3})`)
    }

    ctx.globalAlpha = currentOpacity
    ctx.fillStyle = gradient

    // 绘制流线型水滴形状
    ctx.beginPath()
    if (side === 'left') {
      // 左边：从左边缘向外流
      const baseX = edgeX - currentWidth / 2
      ctx.moveTo(baseX, rect.top)
      ctx.lineTo(baseX + currentWidth, rect.top)
      // 底部收窄
      ctx.quadraticCurveTo(
        baseX + currentWidth, currentY - 5,
        edgeX, currentY
      )
      ctx.quadraticCurveTo(
        baseX, currentY - 5,
        baseX, rect.top
      )
    } else {
      // 右边：从右边缘向外流
      const baseX = edgeX + currentWidth / 2
      ctx.moveTo(baseX, rect.top)
      ctx.lineTo(baseX - currentWidth, rect.top)
      ctx.quadraticCurveTo(
        baseX - currentWidth, currentY - 5,
        edgeX, currentY
      )
      ctx.quadraticCurveTo(
        baseX, currentY - 5,
        baseX, rect.top
      )
    }
    ctx.closePath()
    ctx.fill()

    // 添加高光
    if (flow.flowProgress <= 1.2) {
      ctx.globalAlpha = currentOpacity * 0.3
      ctx.strokeStyle = 'rgba(255, 255, 255, 0.5)'
      ctx.lineWidth = 1.5
      ctx.beginPath()
      if (side === 'left') {
        ctx.moveTo(edgeX - 2, rect.top + 5)
        ctx.lineTo(edgeX - 2, currentY - 10)
      } else {
        ctx.moveTo(edgeX + 2, rect.top + 5)
        ctx.lineTo(edgeX + 2, currentY - 10)
      }
      ctx.stroke()
    }
  }

  // 绘制底部滴落的水滴
  for (const drop of dripDrops) {
    ctx.globalAlpha = drop.opacity
    ctx.fillStyle = 'rgba(180, 205, 235, 0.75)'
    ctx.beginPath()
    ctx.ellipse(drop.x, drop.y, drop.size * 0.35, drop.size * 1.3, 0, 0, Math.PI * 2)
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
  waterFlows = []
  dripDrops = []
  flowIdCounter = 0
  resizeCanvas()

  const targetCount = Math.floor(getIntensityConfig().dropCount * props.sizeMultiplier)
  const initialCount = Math.min(targetCount, Math.floor(targetCount * 0.35))
  for (let i = 0; i < initialCount; i++) rainDrops.push(spawnRainDrop(true))

  animId = requestAnimationFrame(render)
}

const stop = () => {
  isActive = false
  if (animId) { cancelAnimationFrame(animId); animId = 0 }
  rainDrops = []
  waterFlows = []
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
