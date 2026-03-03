<template>
  <!-- Canvas 粒子特效 -->
  <CanvasParticleRenderer
    v-for="eff in particleEffects"
    :key="eff.key"
    :preset="eff.preset"
    :count="eff.count"
    :layer="eff.layer"
    :opacity="eff.opacity"
    :speed-multiplier="eff.speedMul"
    :size-multiplier="eff.sizeMul"
  />
  <!-- Canvas Shader 特效 -->
  <CanvasShaderEffect
    v-for="eff in shaderEffectList"
    :key="eff.key"
    :effect-type="eff.type"
    :params="eff.params"
    :layer="eff.layer"
  />
</template>

<script setup lang="ts">
import { computed } from 'vue'
import CanvasParticleRenderer from './CanvasParticleRenderer.vue'
import CanvasShaderEffect from './CanvasShaderEffect.vue'
import { hasImagePreset, getPreset, getParticleCount } from '@/config/particlePresets'
import { isShaderEffect } from '@/config/shaderEffects'

interface AtmosphereEffect {
  type: string
  intensity: string
  layer?: string
  config?: Record<string, any>
}

const props = defineProps<{ effects: AtmosphereEffect[] }>()

const particleEffects = computed(() => {
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

const shaderEffectList = computed(() => {
  if (!props.effects) return []
  return props.effects
    .filter(e => isShaderEffect(e.type))
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
</script>
