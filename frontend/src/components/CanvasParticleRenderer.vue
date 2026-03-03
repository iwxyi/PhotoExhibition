<template>
  <canvas
    ref="canvasRef"
    class="canvas-particle-layer"
    :class="{ 'above-layer': layer === 'above', 'background-layer': layer === 'background' }"
  />
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, watch, nextTick } from 'vue'

export interface ParticlePreset {
  type: string
  images: string[]
  physics: {
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
  }
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

const createParticle = (initialSpawn = false): Particle => {
  const ph = props.preset.physics
  const speed = (ph.minSpeed + Math.random() * (ph.maxSpeed - ph.minSpeed)) * props.speedMultiplier
  const size = (ph.minSize + Math.random() * (ph.maxSize - ph.minSize)) * props.sizeMultiplier

  return {
    x: Math.random() * width,
    y: initialSpawn ? Math.random() * height : -size * 2,
    vx: ph.windX + (Math.random() - 0.5) * ph.windVariance * 2,
    vy: speed,
    size,
    rotation: Math.random() * Math.PI * 2,
    rotationSpeed: (Math.random() - 0.5) * ph.rotationSpeed * 2,
    imgIndex: Math.floor(Math.random() * loadedImages.length),
    opacity: initialSpawn ? props.opacity : 0,
    age: initialSpawn ? Math.random() * 8000 : 0,
    lifetime: 8000 + Math.random() * 12000,
    swayPhase: Math.random() * Math.PI * 2,
    swaySpeed: 0.5 + Math.random() * 1.5,
    flutter: ph.flutter * (0.5 + Math.random())
  }
}

const updateParticle = (p: Particle, dt: number): boolean => {
  const ph = props.preset.physics
  p.age += dt

  p.vy += ph.gravity * dt * 0.001
  p.x += p.vx * dt * 0.001 * props.speedMultiplier
  p.y += p.vy * dt * 0.001 * props.speedMultiplier

  p.rotation += p.rotationSpeed * dt * 0.001

  if (ph.sway) {
    p.swayPhase += p.swaySpeed * dt * 0.001
    p.x += Math.sin(p.swayPhase) * ph.swayAmplitude * dt * 0.001
  }

  if (p.flutter > 0) {
    p.vy += Math.sin(p.age * 0.002) * p.flutter * dt * 0.001
    p.vx += Math.cos(p.age * 0.0015) * p.flutter * 0.5 * dt * 0.001
  }

  const fadeInTime = ph.fadeIn
  const fadeOutStart = p.lifetime - ph.fadeOut
  if (p.age < fadeInTime) {
    p.opacity = (p.age / fadeInTime) * props.opacity
  } else if (p.age > fadeOutStart) {
    p.opacity = ((p.lifetime - p.age) / ph.fadeOut) * props.opacity
  } else {
    p.opacity = props.opacity
  }

  return p.age < p.lifetime && p.y < height + p.size * 2 && p.x > -p.size * 4 && p.x < width + p.size * 4
}

const render = (timestamp: number) => {
  if (!isActive || !ctx) return

  const dt = lastTime ? Math.min(timestamp - lastTime, 50) : 16
  lastTime = timestamp

  ctx.clearRect(0, 0, width, height)

  for (let i = particles.length - 1; i >= 0; i--) {
    const p = particles[i]
    if (!updateParticle(p, dt)) {
      particles[i] = createParticle(false)
      continue
    }

    const img = loadedImages[p.imgIndex]
    if (!img) continue

    ctx.save()
    ctx.globalAlpha = Math.max(0, Math.min(1, p.opacity))
    ctx.translate(p.x, p.y)
    ctx.rotate(p.rotation)
    ctx.drawImage(img, -p.size / 2, -p.size / 2, p.size, p.size)
    ctx.restore()
  }

  while (particles.length < props.count) {
    particles.push(createParticle(false))
  }
  while (particles.length > props.count) {
    particles.pop()
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
  if (ctx) {
    ctx.scale(dpr, dpr)
  }
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
  for (let i = 0; i < initialCount; i++) {
    particles.push(createParticle(true))
  }

  animId = requestAnimationFrame(render)
}

const stop = () => {
  isActive = false
  if (animId) {
    cancelAnimationFrame(animId)
    animId = 0
  }
  particles = []
  if (ctx) {
    ctx.clearRect(0, 0, width, height)
  }
}

let resizeHandler: (() => void) | null = null

onMounted(() => {
  resizeHandler = () => {
    if (isActive) {
      resizeCanvas()
    }
  }
  window.addEventListener('resize', resizeHandler)
  nextTick(() => start())
})

onBeforeUnmount(() => {
  stop()
  if (resizeHandler) {
    window.removeEventListener('resize', resizeHandler)
  }
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

.canvas-particle-layer.above-layer {
  z-index: 2;
}

.canvas-particle-layer.background-layer {
  z-index: 0;
}
</style>
