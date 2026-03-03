<template>
  <canvas
    ref="canvasRef"
    class="canvas-shader-layer"
    :class="{ 'above-layer': layer === 'above', 'background-layer': layer === 'background' }"
  />
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, watch, nextTick } from 'vue'
import { shaderRegistry, type ShaderParams } from '@/config/shaderEffects'

interface Props {
  effectType: string
  params: ShaderParams
  layer?: 'above' | 'background'
}

const props = withDefaults(defineProps<Props>(), { layer: 'above' })

const canvasRef = ref<HTMLCanvasElement>()
let ctx: CanvasRenderingContext2D | null = null
let animId = 0
let startTime = 0
let isActive = false
let width = 0
let height = 0

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

const render = (timestamp: number) => {
  if (!isActive || !ctx) return
  if (!startTime) startTime = timestamp
  const elapsed = (timestamp - startTime) * 0.001

  ctx.clearRect(0, 0, width, height)

  const fn = shaderRegistry[props.effectType]
  if (fn) fn(ctx, width, height, elapsed, props.params)

  animId = requestAnimationFrame(render)
}

const start = () => {
  if (isActive) return
  resizeCanvas()
  isActive = true
  startTime = 0
  animId = requestAnimationFrame(render)
}

const stop = () => {
  isActive = false
  if (animId) { cancelAnimationFrame(animId); animId = 0 }
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

watch(() => [props.effectType, props.params], () => {
  stop(); nextTick(() => start())
}, { deep: true })
</script>

<style scoped>
.canvas-shader-layer {
  position: fixed;
  top: 0;
  left: 0;
  pointer-events: none;
  overflow: hidden;
}
.canvas-shader-layer.above-layer { z-index: 2; }
.canvas-shader-layer.background-layer { z-index: 0; }
</style>
