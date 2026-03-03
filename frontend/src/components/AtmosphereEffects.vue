<template>
  <CanvasParticleRenderer
    v-for="eff in canvasEffects"
    :key="eff.key"
    :preset="eff.preset"
    :count="eff.count"
    :layer="eff.layer"
    :opacity="eff.opacity"
    :speed-multiplier="eff.speedMul"
    :size-multiplier="eff.sizeMul"
  />
</template>

<script setup lang="ts">
import { computed } from 'vue'
import CanvasParticleRenderer from './CanvasParticleRenderer.vue'
import { hasImagePreset, getPreset, getParticleCount } from '@/config/particlePresets'

interface AtmosphereEffect {
  type: string
  intensity: string
  layer?: string
  config?: Record<string, any>
}

interface Props {
  effects: AtmosphereEffect[]
}

const props = defineProps<Props>()

const canvasEffects = computed(() => {
  if (!props.effects) return []
  return props.effects
    .filter(e => hasImagePreset(e.type))
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
</script>
