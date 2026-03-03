<template>
  <canvas
    ref="canvasRef"
    class="canvas-particle-layer"
    :class="{ 'above-layer': layer === 'above', 'background-layer': layer === 'background' }"
  />
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, watch, nextTick } from 'vue'

export interface ParticlePhysics {
  gravity: number
  windX: number
  windVariance: number
  rotationSpeed: number
  flutter: number
  minSpeed: number
  maxSpeed: number
  minSize: number
  maxSize: number
  fadeIn: number
  fadeOut: number
  sway: boolean
  swayAmplitude: number
  swayFrequency: number
  glow?: number
  glowColor?: string
  pulse?: boolean
  pulseSpeed?: number
  pulseMin?: number
  trail?: number
  trailFade?: number
  angle?: number
  angleVariance?: number
  wander?: boolean
  wanderStrength?: number
  scaleOscillation?: number
  spawnMode?: 'top' | 'random' | 'bottom' | 'edges'
  minLifetime?: number
  maxLifetime?: number
  aspectRatio?: number
  lockRotation?: boolean
  rotationOffset?: number
}

export interface ParticlePreset {
  type: string
  images: string[]
  physics: ParticlePhysics
}

interface Props {
  preset: ParticlePreset
  count: number
  layer?: 'above' | 'background'
  opacity?: number
  speedMultiplier?: number
  sizeMultiplier?: number
}

const props = withDefaults(defineProps<Props>(), {
  layer: 'above',
  opacity: 0.9,
  speedMultiplier: 1,
  sizeMultiplier: 1
})

interface Particle {
  x: number
  y: number
  vx: number
  vy: number
  size: number
  rotation: number
  rotationSpeed: number
  imgIndex: number
  opacity: number
  age: number
  lifetime: number
  swayPhase: number
  swaySpeed: number
  flutter: number
  baseOpacity: number
  prevPositions: { x: number; y: number; opacity: number }[]
}

const canvasRef = ref<HTMLCanvasElement>()
let ctx: CanvasRenderingContext2D | null = null
let animId = 0
let lastTime = 0
let particles: Particle[] = []
let loadedImages: HTMLImageElement[] = []
let isActive = false
let width = 0
let height = 0

const loadImages = async (urls: string[]): Promise<HTMLImageElement[]> => {
  const promises = urls.map(url => {
    return new Promise<HTMLImageElement>((resolve, reject) => {
      const img = new Image()
      img.crossOrigin = 'anonymous'
      img.onload = () => resolve(img)
      img.onerror = () => reject(new Error(`Failed to load: ${url}`))
      img.src = url
    })
  })
  return Promise.all(promises)
}

const spawnParticle = (initialSpawn: boolean): Particle => {
  const ph = props.preset.physics
  const baseSpeed = (ph.minSpeed + Math.random() * (ph.maxSpeed - ph.minSpeed)) * props.speedMultiplier
  const size = (ph.minSize + Math.random() * (ph.maxSize - ph.minSize)) * props.sizeMultiplier

  const angleDeg = ph.angle ?? 90
  const variance = ph.angleVariance ?? 0
  const actualAngle = (angleDeg + (Math.random() - 0.5) * variance * 2) * Math.PI / 180

  let x: number, y: number
  const spawn = ph.spawnMode || 'top'
  if (initialSpawn && spawn !== 'bottom') {
    x = Math.random() * width
    y = Math.random() * height
  } else if (spawn === 'bottom') {
    x = Math.random() * width
    y = initialSpawn ? Math.random() * height : height + size
  } else if (spawn === 'random') {
    x = Math.random() * width
    y = Math.random() * height
  } else if (spawn === 'edges') {
    const side = Math.floor(Math.random() * 4)
    if (side === 0) { x = Math.random() * width; y = -size }
    else if (side === 1) { x = width + size; y = Math.random() * height }
    else if (side === 2) { x = Math.random() * width; y = height + size }
    else { x = -size; y = Math.random() * height }
  } else {
    x = Math.random() * width
    y = initialSpawn ? Math.random() * height : -size * 2
  }

  const minL = ph.minLifetime ?? 8000
  const maxL = ph.maxLifetime ?? 20000

  const vx = Math.cos(actualAngle) * baseSpeed + ph.windX + (Math.random() - 0.5) * ph.windVariance * 2
  const vy = Math.sin(actualAngle) * baseSpeed
  const initRotation = ph.lockRotation
    ? Math.atan2(vy, vx) + (ph.rotationOffset ?? 0)
    : Math.random() * Math.PI * 2

  return {
    x, y,
    vx, vy,
    size,
    rotation: initRotation,
    rotationSpeed: ph.lockRotation ? 0 : (Math.random() - 0.5) * ph.rotationSpeed * 2,
    imgIndex: Math.floor(Math.random() * loadedImages.length),
    opacity: initialSpawn ? props.opacity : 0,
    baseOpacity: props.opacity,
    age: initialSpawn ? Math.random() * minL : 0,
    lifetime: minL + Math.random() * (maxL - minL),
    swayPhase: Math.random() * Math.PI * 2,
    swaySpeed: 0.5 + Math.random() * 1.5,
    flutter: ph.flutter * (0.5 + Math.random()),
    prevPositions: []
  }
}

const updateParticle = (p: Particle, dt: number): boolean => {
  const ph = props.preset.physics
  const dtSec = dt * 0.001
  p.age += dt

  if (ph.trail && ph.trail > 0) {
    p.prevPositions.unshift({ x: p.x, y: p.y, opacity: p.opacity })
    if (p.prevPositions.length > ph.trail) p.prevPositions.length = ph.trail
  }

  p.vy += ph.gravity * dtSec
  p.x += p.vx * dtSec * props.speedMultiplier
  p.y += p.vy * dtSec * props.speedMultiplier

  if (ph.lockRotation) {
    p.rotation = Math.atan2(p.vy, p.vx) + (ph.rotationOffset ?? 0)
  } else {
    p.rotation += p.rotationSpeed * dtSec
  }

  if (ph.sway) {
    p.swayPhase += p.swaySpeed * dtSec
    p.x += Math.sin(p.swayPhase) * ph.swayAmplitude * dtSec
  }

  if (p.flutter > 0) {
    p.vy += Math.sin(p.age * 0.002) * p.flutter * dtSec
    p.vx += Math.cos(p.age * 0.0015) * p.flutter * 0.5 * dtSec
  }

  if (ph.wander) {
    const str = ph.wanderStrength ?? 30
    p.vx += (Math.random() - 0.5) * str * dtSec
    p.vy += (Math.random() - 0.5) * str * dtSec
    const maxV = (ph.maxSpeed || 50) * props.speedMultiplier
    const speed = Math.sqrt(p.vx * p.vx + p.vy * p.vy)
    if (speed > maxV) {
      p.vx = (p.vx / speed) * maxV
      p.vy = (p.vy / speed) * maxV
    }
  }

  let targetOpacity = p.baseOpacity
  if (p.age < ph.fadeIn) {
    targetOpacity = (p.age / ph.fadeIn) * p.baseOpacity
  } else if (p.age > p.lifetime - ph.fadeOut) {
    targetOpacity = ((p.lifetime - p.age) / ph.fadeOut) * p.baseOpacity
  }

  if (ph.pulse) {
    const speed = ph.pulseSpeed ?? 2
    const min = ph.pulseMin ?? 0.2
    const wave = (Math.sin(p.age * speed * 0.001) + 1) * 0.5
    targetOpacity *= min + wave * (1 - min)
  }

  p.opacity = targetOpacity

  const margin = p.size * 4
  const outOfBounds = p.x < -margin || p.x > width + margin || p.y < -margin || p.y > height + margin
  return p.age < p.lifetime && !outOfBounds
}

const render = (timestamp: number) => {
  if (!isActive || !ctx) return

  const dt = lastTime ? Math.min(timestamp - lastTime, 50) : 16
  lastTime = timestamp

  ctx.clearRect(0, 0, width, height)

  const ph = props.preset.physics
  const hasGlow = (ph.glow ?? 0) > 0

  for (let i = particles.length - 1; i >= 0; i--) {
    const p = particles[i]
    if (!updateParticle(p, dt)) {
      particles[i] = spawnParticle(false)
      continue
    }

    const img = loadedImages[p.imgIndex]
    if (!img) continue

    const drawW = ph.aspectRatio ? p.size * ph.aspectRatio : p.size
    const drawH = p.size
    const scaleOsc = ph.scaleOscillation
      ? 1 + Math.sin(p.age * 0.003) * ph.scaleOscillation
      : 1

    if (ph.trail && p.prevPositions.length > 0) {
      const fade = ph.trailFade ?? 0.7
      for (let t = p.prevPositions.length - 1; t >= 0; t--) {
        const pp = p.prevPositions[t]
        const alpha = pp.opacity * Math.pow(fade, t + 1)
        if (alpha < 0.01) continue
        ctx.save()
        ctx.globalAlpha = alpha
        ctx.translate(pp.x, pp.y)
        ctx.rotate(p.rotation)
        ctx.drawImage(img, -drawW * scaleOsc / 2, -drawH * scaleOsc / 2, drawW * scaleOsc, drawH * scaleOsc)
        ctx.restore()
      }
    }

    ctx.save()
    ctx.globalAlpha = Math.max(0, Math.min(1, p.opacity))

    if (hasGlow) {
      ctx.shadowBlur = ph.glow!
      ctx.shadowColor = ph.glowColor || 'rgba(255,255,255,0.8)'
    }

    ctx.translate(p.x, p.y)
    ctx.rotate(p.rotation)
    ctx.drawImage(img, -drawW * scaleOsc / 2, -drawH * scaleOsc / 2, drawW * scaleOsc, drawH * scaleOsc)
    ctx.restore()
  }

  while (particles.length < props.count) particles.push(spawnParticle(false))
  while (particles.length > props.count) particles.pop()

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

const start = async () => {
  if (isActive) return
  if (!props.preset?.images?.length) return
  try {
    loadedImages = await loadImages(props.preset.images)
  } catch (e) {
    console.warn('Failed to load particle images:', e)
    return
  }
  if (loadedImages.length === 0) return
  resizeCanvas()
  isActive = true
  lastTime = 0
  particles = []
  const initialCount = Math.min(props.count, Math.floor(props.count * 0.7))
  for (let i = 0; i < initialCount; i++) particles.push(spawnParticle(true))
  animId = requestAnimationFrame(render)
}

const stop = () => {
  isActive = false
  if (animId) { cancelAnimationFrame(animId); animId = 0 }
  particles = []
  if (ctx) ctx.clearRect(0, 0, width, height)
}

let resizeHandler: (() => void) | null = null

onMounted(() => {
  resizeHandler = () => { if (isActive) resizeCanvas() }
  window.addEventListener('resize', resizeHandler)
  nextTick(() => start())
})

onBeforeUnmount(() => {
  stop()
  if (resizeHandler) window.removeEventListener('resize', resizeHandler)
})

watch(() => [props.preset, props.count, props.opacity, props.speedMultiplier, props.sizeMultiplier], () => {
  stop()
  nextTick(() => start())
}, { deep: true })
</script>

<style scoped>
.canvas-particle-layer {
  position: fixed;
  top: 0;
  left: 0;
  pointer-events: none;
  overflow: hidden;
}
.canvas-particle-layer.above-layer { z-index: 2; }
.canvas-particle-layer.background-layer { z-index: 0; }
</style>
