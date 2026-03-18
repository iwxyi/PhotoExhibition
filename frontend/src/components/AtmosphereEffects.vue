<template>
  <CanvasParticleRenderer
    v-for="eff in particleEffects"
    :key="eff.key"
    :preset="eff.preset"
    :count="eff.count"
    :layer="eff.layer"
    :opacity="eff.opacity"
    :speed-multiplier="eff.speedMul"
    :size-multiplier="eff.sizeMul"
    :interaction="interaction"
  />
  <CanvasShaderEffect
    v-for="eff in shaderEffectList"
    :key="eff.key"
    :effect-type="eff.type"
    :params="eff.params"
    :layer="eff.layer"
    :interaction="interaction"
  />
  <RainOnContainers
    v-for="eff in containerRainEffects"
    :key="eff.key"
    :count="eff.count"
    :layer="eff.layer"
    :opacity="eff.opacity"
    :speed-multiplier="eff.speedMul"
    :size-multiplier="eff.sizeMul"
    :intensity="eff.intensity"
    :interaction="interaction"
  />
</template>

<script setup lang="ts">
import { computed, onMounted, onBeforeUnmount } from 'vue'
import CanvasParticleRenderer from './CanvasParticleRenderer.vue'
import CanvasShaderEffect from './CanvasShaderEffect.vue'
import RainOnContainers from './RainOnContainers.vue'
import { hasImagePreset, getPreset, getParticleCount } from '@/config/particlePresets'
import { isShaderEffect } from '@/config/shaderEffects'
import { useEffectInteraction, type ClickEvent } from '@/composables/useEffectInteraction'

interface AtmosphereEffect {
  type: string
  intensity: string
  layer?: string
  config?: Record<string, any>
}

const props = withDefaults(defineProps<{
  effects: AtmosphereEffect[]
  viewerMode?: boolean
}>(), {
  viewerMode: false
})

const { triggerClick, updateScroll, scrollVelocity, consumeClicks } = useEffectInteraction()

const interaction = computed(() => ({
  scrollVelocity: scrollVelocity.value,
  consumeClicks,
  triggerClick,
}))

let scrollTimer = 0
const onScroll = () => { updateScroll() }
const onClick = (e: MouseEvent) => { triggerClick(e.clientX, e.clientY) }

onMounted(() => {
  window.addEventListener('scroll', onScroll, { passive: true })
  window.addEventListener('click', onClick)
  scrollTimer = window.setInterval(updateScroll, 100)
})
onBeforeUnmount(() => {
  window.removeEventListener('scroll', onScroll)
  window.removeEventListener('click', onClick)
  clearInterval(scrollTimer)
})

const particleEffects = computed(() => {
  if (!props.effects) return []
  return props.effects
    .filter(e => {
      if (!hasImagePreset(e.type)) return false
      // viewerMode: only show 'above' layer, hide 'background' layer
      if (props.viewerMode) {
        const layer = e.config?.layer || e.layer || 'above'
        return layer === 'above'
      }
      return true
    })
    .map(e => {
      const preset = getPreset(e.type)!
      const intensity = e.intensity || 'medium'
      const custom = e.config?.custom || {}
      const count = getParticleCount(e.type, intensity, custom.count)
      const layer = (e.config?.layer || e.layer || 'above') as 'above' | 'background'
      const speedMul = intensity === 'custom' && custom.speed !== undefined ? custom.speed / 5 : 1
      const sizeMul = intensity === 'custom' && custom.size !== undefined ? custom.size / 5 : 1
      const opacity = custom.opacity !== undefined ? custom.opacity / 10 : 0.9
      return { preset, count, layer, speedMul, sizeMul, opacity, key: e.type + '-' + layer }
    })
})

const shaderEffectList = computed(() => {
  if (!props.effects) return []
  return props.effects
    .filter(e => {
      if (!isShaderEffect(e.type)) return false
      // viewerMode: only show 'above' layer, hide 'background' layer
      if (props.viewerMode) {
        const layer = e.config?.layer || e.layer || 'above'
        return layer === 'above'
      }
      return true
    })
    .map(e => {
      const layer = (e.config?.layer || e.layer || 'above') as 'above' | 'background'
      const custom = e.config?.custom || {}
      const intensity = e.intensity || 'medium'
      const presetVal = intensity === 'low' ? 3 : intensity === 'high' ? 8 : 5
      const params: Record<string, number> = {}
      for (const [k, v] of Object.entries(custom)) {
        if (typeof v === 'number') params[k] = v
      }
      if (!params.intensity) params.intensity = presetVal
      if (!params.speed) params.speed = presetVal
      return { type: e.type, params, layer, key: 'shader-' + e.type + '-' + layer }
    })
})

// Special effect: rain drops on photo containers with water flow
const containerRainEffects = computed(() => {
  if (!props.effects) return []
  return props.effects
    .filter(e => {
      if (e.type !== 'rain_on_containers') return false
      // viewerMode: only show 'above' layer, hide 'background' layer
      if (props.viewerMode) {
        const layer = e.config?.layer || e.layer || 'above'
        return layer === 'above'
      }
      return true
    })
    .map(e => {
      const layer = (e.config?.layer || e.layer || 'above') as 'above' | 'background'
      const custom = e.config?.custom || {}
      const intensity = e.intensity || 'medium'
      const presetVal = intensity === 'low' ? 3 : intensity === 'high' ? 8 : 5

      const count = custom.count !== undefined ? Math.round(custom.count * 20) : presetVal * 20
      const speedMul = custom.speed !== undefined ? custom.speed / 5 : 1
      const sizeMul = custom.size !== undefined ? custom.size / 5 : 1
      const opacity = custom.opacity !== undefined ? custom.opacity / 10 : 0.7

      return {
        count,
        layer,
        speedMul,
        sizeMul,
        opacity,
        intensity,
        key: 'rain-on-containers-' + layer
      }
    })
})
</script>
