<template>
  <!-- 基于Canvas的图片素材粒子特效 -->
  <CanvasParticleRenderer
    v-for="eff in imageBasedEffects"
    :key="eff.key"
    :preset="eff.preset"
    :count="eff.count"
    :layer="eff.layer"
    :opacity="eff.opacity"
    :speed-multiplier="eff.speedMul"
    :size-multiplier="eff.sizeMul"
  />

  <!-- 背景层特效（仅CSS类型：生日/流星/星空/烟花） -->
  <div class="atmosphere-effects background-layer">
    <!-- 生日特效 -->
    <div
      v-if="hasCssEffect('birthday') && isBackgroundEffect('birthday')"
      class="birthday-container"
      :class="getEffectClass('birthday')"
    >
      <!-- 气球 -->
      <div
        v-for="balloon in getEffectConfig('birthday').balloonCount"
        :key="'balloon-' + balloon"
        class="birthday-balloon"
        :style="getBirthdayBalloonStyle(balloon)"
      ></div>
      <!-- 彩屑 -->
      <div
        v-for="confetti in getEffectConfig('birthday').confettiCount"
        :key="'confetti-' + confetti"
        class="birthday-confetti"
        :style="getBirthdayConfettiStyle(confetti)"
      ></div>
    </div>
    <!-- 流星特效 -->
    <div
      v-if="hasCssEffect('meteor') && isBackgroundEffect('meteor')"
      class="meteor-container"
      :class="getEffectClass('meteor')"
    >
      <div
        v-for="meteor in getEffectConfig('meteor').meteorCount"
        :key="'meteor-' + meteor"
        class="meteor"
        :style="getMeteorStyle(meteor)"
      ></div>
    </div>
    <!-- 星空特效 -->
    <div
      v-if="hasCssEffect('starry_sky') && isBackgroundEffect('starry_sky')"
      class="starry-sky-container"
      :class="getEffectClass('starry_sky')"
    >
      <div
        v-for="star in getEffectConfig('starry_sky').starCount"
        :key="'star-' + star"
        class="star"
        :style="getStarStyle(star)"
      ></div>
    </div>

    <!-- 烟花特效 -->
    <div
      v-if="hasCssEffect('fireworks') && isBackgroundEffect('fireworks')"
      class="fireworks-container"
      :class="getEffectClass('fireworks')"
    >
      <!-- 烟花发射体 -->
      <div
        v-for="firework in getEffectConfig('fireworks').fireworkCount"
        :key="'firework-rocket-' + firework"
        class="firework-rocket"
        :style="getFireworkRocketStyle(firework)"
      ></div>

      <!-- 烟花爆炸和粒子 -->
      <div
        v-for="firework in getEffectConfig('fireworks').fireworkCount"
        :key="'firework-explosion-' + firework"
        class="firework-explosion"
        :style="getFireworkExplosionStyle(firework)"
      >
        <!-- 爆炸粒子 -->
        <div
          v-for="particle in 24"
          :key="'particle-' + firework + '-' + particle"
          class="firework-particle"
          :style="getFireworkParticleStyle(particle, firework)"
        ></div>
      </div>
    </div>
  </div>

  <!-- 图片上方特效（仅CSS类型） -->
  <div class="atmosphere-effects above-layer">
    <!-- 生日特效 -->
    <div
      v-if="hasCssEffect('birthday') && !isBackgroundEffect('birthday')"
      class="birthday-container"
      :class="getEffectClass('birthday')"
    >
      <!-- 气球 -->
      <div
        v-for="balloon in getEffectConfig('birthday').balloonCount"
        :key="'balloon-' + balloon"
        class="birthday-balloon"
        :style="getBirthdayBalloonStyle(balloon)"
      ></div>

      <!-- 彩屑 -->
      <div
        v-for="confetti in getEffectConfig('birthday').confettiCount"
        :key="'confetti-' + confetti"
        class="birthday-confetti"
        :style="getBirthdayConfettiStyle(confetti)"
      ></div>
    </div>

    <!-- 流星特效 -->
    <div
      v-if="hasCssEffect('meteor') && !isBackgroundEffect('meteor')"
      class="meteor-container"
      :class="getEffectClass('meteor')"
    >
      <div
        v-for="meteor in getEffectConfig('meteor').meteorCount"
        :key="'meteor-' + meteor"
        class="meteor"
        :style="getMeteorStyle(meteor)"
      ></div>
    </div>

    <!-- 星空特效（如果设置为above层级） -->
    <div
      v-if="hasCssEffect('starry_sky') && !isBackgroundEffect('starry_sky')"
      class="starry-sky-container"
      :class="getEffectClass('starry_sky')"
    >
      <div
        v-for="star in getEffectConfig('starry_sky').starCount"
        :key="'star-' + star"
        class="star"
        :style="getStarStyle(star)"
      ></div>
    </div>

    <!-- 烟花特效 -->
    <div
      v-if="hasCssEffect('fireworks') && !isBackgroundEffect('fireworks')"
      class="fireworks-container"
      :class="getEffectClass('fireworks')"
    >
      <!-- 烟花发射体 -->
      <div
        v-for="firework in getEffectConfig('fireworks').fireworkCount"
        :key="'firework-rocket-above-' + firework"
        class="firework-rocket"
        :style="getFireworkRocketStyle(firework)"
      ></div>

      <!-- 烟花爆炸和粒子 -->
      <div
        v-for="firework in getEffectConfig('fireworks').fireworkCount"
        :key="'firework-explosion-above-' + firework"
        class="firework-explosion"
        :style="getFireworkExplosionStyle(firework)"
      >
        <!-- 爆炸粒子 -->
        <div
          v-for="particle in 24"
          :key="'particle-above-' + firework + '-' + particle"
          class="firework-particle"
          :style="getFireworkParticleStyle(particle, firework)"
        ></div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, watch } from 'vue'
import CanvasParticleRenderer from './CanvasParticleRenderer.vue'
import { hasImagePreset, getPreset, getParticleCount } from '@/config/particlePresets'
import type { ParticlePreset } from './CanvasParticleRenderer.vue'

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

const imageBasedEffects = computed(() => {
  if (!props.effects) return []
  return props.effects
    .filter(e => hasImagePreset(e.type))
    .map(e => {
      const preset = getPreset(e.type)!
      const intensity = e.intensity || 'medium'
      const customCount = e.config?.custom?.count
      const customSpeed = e.config?.custom?.speed
      const customSize = e.config?.custom?.size
      const customOpacity = e.config?.custom?.opacity
      const count = getParticleCount(e.type, intensity, customCount)
      const layer = (e.config?.layer || e.layer || 'above') as 'above' | 'background'
      const speedMul = intensity === 'custom' && customSpeed !== undefined ? customSpeed / 5 : 1
      const sizeMul = intensity === 'custom' && customSize !== undefined ? customSize / 5 : 1
      const opacity = customOpacity !== undefined ? customOpacity / 10 : 0.9
      return { preset, count, layer, speedMul, sizeMul, opacity, key: e.type + '-' + layer }
    })
})

const cssOnlyEffects = computed(() => {
  if (!props.effects) return []
  return props.effects.filter(e => !hasImagePreset(e.type))
})

const hasCssEffect = (type: string) => {
  return cssOnlyEffects.value.some(e => e.type === type)
}

// 调试：组件挂载时输出特效数据
onMounted(() => {
  console.log('AtmosphereEffects 组件挂载，特效数据:', props.effects)
  if (props.effects) {
    const fireworks = props.effects.find(e => e.type === 'fireworks')
    if (fireworks) {
      console.log('烟花特效数据:', fireworks)
      console.log('烟花layer值:', fireworks.layer)
      console.log('烟花config:', fireworks.config)
    }
  }
})

// 监听特效数据变化
watch(() => props.effects, (newEffects) => {
  console.log('AtmosphereEffects 特效数据更新:', newEffects)
  if (newEffects) {
    const fireworks = newEffects.find(e => e.type === 'fireworks')
    if (fireworks) {
      console.log('烟花特效数据更新:', fireworks)
    }
  }
}, { deep: true })

const hasEffect = (type: string) => {
  return props.effects?.some(effect => effect.type === type) || false
}

const getEffectConfig = (type: string) => {
  const effect = props.effects?.find(e => e.type === type)
  const baseConfig = effect?.config || {}
  const intensity = effect?.intensity || 'medium'

  // 预设强度配置（用于滑块显示，低中高分别对应参数值 3, 5, 7）
  const presetConfigs = {
    low: { speed: 3, size: 3, count: 3 },
    medium: { speed: 5, size: 5, count: 5 },
    high: { speed: 7, size: 7, count: 7 },
    custom: { speed: 5, size: 5, count: 5 } // custom 使用默认值，实际由用户配置决定
  }

  // 预设强度的实际特效参数（固定值，不受用户调整影响）
  const presetEffectParams = {
    low: {
      snow: { particleCount: 30, speed: 0.8, size: 2 },
      cherry_blossom: { particleCount: 20, speed: 0.3, size: 3 },
      meteor: { meteorCount: 3, trailLength: 100, speed: 1.5 },
      starry_sky: { starCount: 100, twinkleSpeed: 1.0, brightness: 0.5 },
      fireworks: { fireworkCount: 20, burstSize: 30 },
      birthday: { balloonCount: 5, confettiCount: 50 },
      autumn_leaves: { leafCount: 15, fallSpeed: 0.5 }
    },
    medium: {
      snow: { particleCount: 100, speed: 1.5, size: 3 },
      cherry_blossom: { particleCount: 50, speed: 0.8, size: 4 },
      meteor: { meteorCount: 5, trailLength: 150, speed: 2.0 },
      starry_sky: { starCount: 200, twinkleSpeed: 1.5, brightness: 0.7 },
      fireworks: { fireworkCount: 30, burstSize: 40 },
      birthday: { balloonCount: 10, confettiCount: 100 },
      autumn_leaves: { leafCount: 30, fallSpeed: 0.8 }
    },
    high: {
      snow: { particleCount: 200, speed: 2.5, size: 4 },
      cherry_blossom: { particleCount: 100, speed: 1.5, size: 5 },
      meteor: { meteorCount: 8, trailLength: 200, speed: 3.0 },
      starry_sky: { starCount: 300, twinkleSpeed: 2.0, brightness: 0.9 },
      fireworks: { fireworkCount: 40, burstSize: 50 },
      birthday: { balloonCount: 15, confettiCount: 150 },
      autumn_leaves: { leafCount: 50, fallSpeed: 1.2 }
    }
  }

  // 特效类型特定的参数配置
  const effectTypeConfigs = {
    snow: {
      baseParticleCount: 100,
      baseSpeed: 1.5,
      baseSize: 3,
      speedRange: { min: 0.1, max: 8.0 }, // 雪花可以非常慢到极快
      sizeRange: { min: 0.5, max: 5.0 },   // 雪花尺寸适中
      countRange: { min: 0.1, max: 10.0 }, // 雪花数量变化大
      layer: 'above'
    },
    cherry_blossom: {
      baseParticleCount: 50,
      baseSpeed: 0.8,
      baseSize: 4,
      speedRange: { min: 0.05, max: 3.0 }, // 樱花飘落较慢
      sizeRange: { min: 0.8, max: 8.0 },   // 樱花花瓣可以很大
      countRange: { min: 0.1, max: 6.0 },  // 樱花数量适中
      sway: true,
      layer: 'above'
    },
    birthday: {
      baseBalloonCount: 10,
      baseConfettiCount: 100,
      baseSpeed: 1.0,
      baseSize: 1.0,
      speedRange: { min: 0.2, max: 4.0 },
      sizeRange: { min: 0.3, max: 3.0 },
      countRange: { min: 0.1, max: 8.0 },
      animationDuration: 3000,
      layer: 'above'
    },
    meteor: {
      baseMeteorCount: 5,
      baseTrailLength: 150,
      baseSpeed: 2.0,
      speedRange: { min: 0.5, max: 10.0 }, // 流星速度变化大
      sizeRange: { min: 0.5, max: 6.0 },   // 尾迹长度变化
      countRange: { min: 0.1, max: 5.0 },  // 流星数量适中
      layer: 'above'
    },
    starry_sky: {
      baseStarCount: 200,
      baseTwinkleSpeed: 1.5,
      baseBrightness: 0.7,
      speedRange: { min: 0.2, max: 5.0 },  // 闪烁速度适中
      sizeRange: { min: 0.1, max: 3.0 },   // 亮度变化
      countRange: { min: 0.1, max: 8.0 },  // 星星数量变化大
      layer: 'background'
    },
    fireworks: {
      baseFireworkCount: 10,
      baseBurstSize: 40,
      baseSpeed: 1.0,
      speedRange: { min: 0.3, max: 6.0 },
      sizeRange: { min: 0.2, max: 5.0 },   // 爆炸大小变化
      countRange: { min: 0.1, max: 7.0 },  // 烟花数量变化
      colors: ['#ff6b6b', '#4ecdc4', '#45b7d1', '#f9ca24', '#f0932b'],
      layer: 'above'
    },
    autumn_leaves: {
      baseLeafCount: 30,
      baseFallSpeed: 0.8,
      baseSize: 1.0,
      speedRange: { min: 0.05, max: 4.0 }, // 落叶速度较慢
      sizeRange: { min: 0.5, max: 6.0 },   // 落叶尺寸变化
      countRange: { min: 0.1, max: 8.0 },  // 落叶数量变化
      sway: true,
      colors: ['#d2691e', '#daa520', '#cd853f', '#deb887'],
      layer: 'above'
    }
  }

  // 基础配置 - 保持与之前相同的参数结构
  const defaultConfigs = {
    snow: {
      particleCount: effectTypeConfigs.snow.baseParticleCount,
      speed: effectTypeConfigs.snow.baseSpeed,
      size: effectTypeConfigs.snow.baseSize,
      layer: 'above'
    },
    cherry_blossom: {
      particleCount: effectTypeConfigs.cherry_blossom.baseParticleCount,
      speed: effectTypeConfigs.cherry_blossom.baseSpeed,
      size: effectTypeConfigs.cherry_blossom.baseSize,
      sway: true,
      layer: 'above'
    },
    birthday: {
      balloonCount: effectTypeConfigs.birthday.baseBalloonCount,
      confettiCount: effectTypeConfigs.birthday.baseConfettiCount,
      animationDuration: 3000,
      layer: 'above'
    },
    meteor: {
      meteorCount: effectTypeConfigs.meteor.baseMeteorCount,
      trailLength: effectTypeConfigs.meteor.baseTrailLength,
      speed: effectTypeConfigs.meteor.baseSpeed,
      layer: 'above'
    },
    starry_sky: {
      starCount: effectTypeConfigs.starry_sky.baseStarCount,
      twinkleSpeed: effectTypeConfigs.starry_sky.baseTwinkleSpeed,
      brightness: effectTypeConfigs.starry_sky.baseBrightness,
      layer: 'background'
    },
    fireworks: {
      fireworkCount: effectTypeConfigs.fireworks.baseFireworkCount,
      burstSize: effectTypeConfigs.fireworks.baseBurstSize,
      colors: ['#ff6b6b', '#4ecdc4', '#45b7d1', '#f9ca24', '#f0932b'],
      layer: 'above'
    },
    autumn_leaves: {
      leafCount: effectTypeConfigs.autumn_leaves.baseLeafCount,
      fallSpeed: effectTypeConfigs.autumn_leaves.baseFallSpeed,
      sway: true,
      colors: ['#d2691e', '#daa520', '#cd853f', '#deb887'],
      layer: 'above'
    }
  }

  // 获取基础配置
  const baseEffectConfig = defaultConfigs[type]
  if (!baseEffectConfig) return baseConfig

  // 检查是否有自定义配置
  const customConfig = baseConfig.custom
  const hasCustomConfig = customConfig && Object.keys(customConfig).length > 0

  // 应用配置
  let result = { ...baseEffectConfig, ...baseConfig }

  // 对于预设强度，使用固定的参数值
  if (!hasCustomConfig && intensity !== 'custom') {
    const presetParams = presetEffectParams[intensity]?.[type]
    if (presetParams) {
      result = { ...result, ...presetParams }
    }
  }
  // 对于自定义强度，根据用户的滑块设置进行调整
  else if (hasCustomConfig) {
    const effectType = effectTypeConfigs[type]

    // 获取用户配置的参数值
    const userSpeed = customConfig.speed
    const userSize = customConfig.size
    const userCount = customConfig.count

    // 应用速度调整 (根据特效类型使用不同的范围)
    if (userSpeed !== undefined) {
      const speedRange = effectType.speedRange
      const speedMultiplier = speedRange.min + (userSpeed - 1) * (speedRange.max - speedRange.min) / 9
      result.speed = baseEffectConfig.speed * speedMultiplier
      if (type === 'starry_sky') result.twinkleSpeed = result.speed
    }

    // 应用大小调整 (根据特效类型使用不同的范围)
    if (userSize !== undefined) {
      const sizeRange = effectType.sizeRange
      const sizeMultiplier = sizeRange.min + (userSize - 1) * (sizeRange.max - sizeRange.min) / 9
      result.size = baseEffectConfig.size * sizeMultiplier
      if (type === 'meteor') result.trailLength = Math.round(baseEffectConfig.trailLength * sizeMultiplier)
      if (type === 'starry_sky') result.brightness = Math.min(1.0, baseEffectConfig.brightness * sizeMultiplier)
      if (type === 'fireworks') result.burstSize = Math.round(baseEffectConfig.burstSize * sizeMultiplier)
    }

    // 应用数量调整 (根据特效类型使用不同的范围)
    if (userCount !== undefined) {
      const countRange = effectType.countRange
      const countMultiplier = countRange.min + (userCount - 1) * (countRange.max - countRange.min) / 9
      if (type === 'snow') result.particleCount = Math.round(baseEffectConfig.particleCount * countMultiplier)
      if (type === 'cherry_blossom') result.particleCount = Math.round(baseEffectConfig.particleCount * countMultiplier)
      if (type === 'meteor') result.meteorCount = Math.round(baseEffectConfig.meteorCount * countMultiplier)
      if (type === 'starry_sky') result.starCount = Math.round(baseEffectConfig.starCount * countMultiplier)
      if (type === 'fireworks') result.fireworkCount = Math.round(baseEffectConfig.fireworkCount * countMultiplier)
      if (type === 'birthday') {
        result.balloonCount = Math.round(baseEffectConfig.balloonCount * countMultiplier)
        result.confettiCount = Math.round(baseEffectConfig.confettiCount * countMultiplier)
      }
      if (type === 'autumn_leaves') result.leafCount = Math.round(baseEffectConfig.leafCount * countMultiplier)
    }

    // 应用透明度参数 (新增)
    if (customConfig.opacity !== undefined) {
      result.opacity = customConfig.opacity / 10 // 1-10 转换为 0.1-1.0
    }
  }

  return result
}

const getEffectClass = (type: string) => {
  const effect = props.effects?.find(e => e.type === type)
  return `intensity-${effect?.intensity || 'medium'}`
}

// 检查特效是否在背景层
const isBackgroundEffect = (type: string) => {
  const effect = props.effects?.find(e => e.type === type)
  const layer = effect?.config?.layer || effect?.layer || 'above'
  const result = layer === 'background'

  // 在页面上显示调试信息
  if (type === 'fireworks') {
    const debugElement = document.getElementById('firework-debug')
    if (debugElement) {
      debugElement.textContent = `烟花图层: ${layer} (背景层: ${result}) | 数据: ${JSON.stringify(effect)}`
    }
  }

  return result
}

// 下雪特效样式
const getSnowFlakeStyle = (index: number) => {
  const config = getEffectConfig('snow')
  const size = config.size || 3
  const speed = config.speed || 1
  const opacity = config.opacity || 0.8
  const isBackground = isBackgroundEffect('snow')

  // 透明度在设置值附近浮动 (±0.2)
  const finalOpacity = Math.max(0.1, Math.min(1.0, opacity + (Math.random() - 0.5) * 0.4))

  // 背景层特效：更多的雪花，更长的延迟，确保持续覆盖整个屏幕
  const delay = isBackground ? Math.random() * 20000 : Math.random() * 10000 // 背景层延迟更长

  return {
    left: `${Math.random() * 100}%`,
    animationDelay: `${delay}ms`,
    animationDuration: `${8000 / speed}ms`, // 背景层动画时间更长
    width: `${size + Math.random() * 2}px`,
    height: `${size + Math.random() * 2}px`,
    opacity: finalOpacity // 使用浮动的透明度
  }
}

// 樱花特效样式
const getCherryPetalStyle = (index: number) => {
  const config = getEffectConfig('cherry_blossom')
  const size = config.size || 4
  const speed = config.speed || 1
  const opacity = config.opacity || 0.9
  const isBackground = isBackgroundEffect('cherry_blossom')

  // 透明度在设置值附近浮动 (±0.2)
  const finalOpacity = Math.max(0.1, Math.min(1.0, opacity + (Math.random() - 0.5) * 0.4))

  // 背景层特效：更多的樱花瓣，更长的延迟，确保持续覆盖整个屏幕
  const delay = isBackground ? Math.random() * 25000 : Math.random() * 15000 // 背景层延迟更长

  return {
    left: `${Math.random() * 100}%`,
    animationDelay: `${delay}ms`,
    animationDuration: `${10000 / speed}ms`, // 背景层动画时间更长
    width: `${size + Math.random() * 3}px`,
    height: `${size + Math.random() * 3}px`,
    transform: `rotate(${Math.random() * 360}deg)`,
    opacity: finalOpacity // 使用浮动的透明度
  }
}

// 生日气球样式
const getBirthdayBalloonStyle = (index: number) => {
  const colors = ['#ff6b6b', '#4ecdc4', '#45b7d1', '#f9ca24', '#f0932b', '#eb4d4b', '#6c5ce7']
  const color = colors[index % colors.length]
  const delay = (index * 1000) % 8000

  return {
    left: `${10 + (index * 15) % 80}%`,
    backgroundColor: color,
    animationDelay: `${delay}ms`,
    transform: `scale(${0.8 + Math.random() * 0.4})`
  }
}

// 生日彩屑样式
const getBirthdayConfettiStyle = (index: number) => {
  const colors = ['#ff6b6b', '#4ecdc4', '#45b7d1', '#f9ca24', '#f0932b', '#eb4d4b', '#6c5ce7']
  const color = colors[index % colors.length]
  const delay = Math.random() * 3000

  return {
    left: `${Math.random() * 100}%`,
    backgroundColor: color,
    animationDelay: `${delay}ms`,
    transform: `rotate(${Math.random() * 360}deg)`
  }
}

// 流星样式
const getMeteorStyle = (index: number) => {
  const config = getEffectConfig('meteor')
  const trailLength = config.trailLength || 150
  const speed = config.speed || 2
  const opacity = config.opacity || 0.9
  const isBackground = isBackgroundEffect('meteor')

  // 透明度在设置值附近浮动 (±0.1)
  const finalOpacity = Math.max(0.1, Math.min(1.0, opacity + (Math.random() - 0.5) * 0.2))

  // 背景层特效：更多的流星，更长的间隔，确保持续覆盖整个屏幕
  const delay = isBackground ? (index * 8000) % 30000 : (index * 3000) % 10000 // 背景层间隔更长
  const startX = Math.random() * 120 - 20 // -20% 到 100%
  const startY = Math.random() * 60 // 0% 到 60%

  return {
    left: `${startX}%`,
    top: `${startY}%`,
    animationDelay: `${delay}ms`,
    animationDuration: `${3000 / speed}ms`, // 背景层动画时间更长
    '--trail-length': `${trailLength}px`,
    opacity: finalOpacity // 使用浮动的透明度
  }
}

// 星空样式
const getStarStyle = (index: number) => {
  const config = getEffectConfig('starry_sky')
  const brightness = config.brightness || 0.8
  const twinkleSpeed = config.twinkleSpeed || 1
  const opacity = config.opacity || brightness // 星空使用亮度作为基础透明度
  const isBackground = isBackgroundEffect('starry_sky')

  // 透明度在设置值附近浮动 (±0.3)，但不超过亮度
  const finalOpacity = Math.max(0.1, Math.min(brightness, opacity + (Math.random() - 0.5) * 0.6))

  // 背景层特效：更多的星星，更好的覆盖
  const delay = isBackground ? Math.random() * 8000 : Math.random() * 3000 // 背景层延迟更随机

  return {
    left: `${Math.random() * 100}%`,
    top: `${Math.random() * 100}%`,
    animationDelay: `${delay}ms`,
    animationDuration: `${3000 / twinkleSpeed}ms`, // 背景层闪烁稍慢
    opacity: finalOpacity // 使用浮动的透明度
  }
}

// 烟花发射体样式 - 从底部升起到随机位置
const getFireworkRocketStyle = (index: number) => {
  const config = getEffectConfig('fireworks')
  const colors = config.colors || ['#ff6b6b', '#4ecdc4', '#45b7d1', '#f9ca24', '#f0932b']

  // 使用固定的种子确保每次调用都生成相同的位置
  const seed = index * 777 // 固定的种子
  const delay = (index * 1000) % 15000 // 1秒间隔，15秒循环周期

  // 使用种子生成固定的随机位置
  const startX = ((seed * 13) % 80) + 10 // 10% 到 90%
  const explosionX = startX // 爆炸X与火箭X相同，确保位置对齐
  const explosionYVh = ((seed * 31) % 67) // 0vh 到 66vh随机高度，覆盖屏幕顶部到2/3高度


  // 根据爆炸高度计算火箭动画时长：距离越近速度越快
  const explosionDistance = explosionYVh // 0-100vh
  const baseDuration = 0.8 // 基础时长0.8秒
  const maxDistance = 100 // 最大距离100vh
  const duration = baseDuration + (explosionDistance / maxDistance) * 0.7 // 0.8s 到 1.5s

  return {
    left: `${startX}%`,
    top: `100%`,
    '--rocket-delay': `${delay}ms`,
    '--rocket-duration': `${duration}s`,
    '--rocket-color': colors[seed % colors.length], // 使用种子选择固定颜色
    // 传递给爆炸元素使用
    '--explosion-x': `${explosionX}%`,
    '--explosion-y-vh': `${explosionYVh}vh`,
    '--start-x': `${startX}%`
  }
}

// 烟花爆炸样式 - 在指定位置爆炸
const getFireworkExplosionStyle = (index: number) => {
  const config = getEffectConfig('fireworks')

  // 与发射体使用相同的种子和延迟
  const seed = index * 777 // 与火箭相同的种子
  // 计算火箭动画时长，然后爆炸延迟相应时间
  const explosionDistance = ((seed * 31) % 101)
  const rocketDuration = 0.8 + (explosionDistance / 100) * 0.7
  const baseDelay = ((index * 1000) % 15000)
  const delay = baseDelay + (rocketDuration * 1000) // 比发射体延迟火箭动画时长

  // 重新计算相同的爆炸位置
  const startX = ((seed * 13) % 80) + 10 // 与火箭X位置相同
  const explosionX = startX // 爆炸X与火箭X相同
  const explosionYVh = ((seed * 31) % 67) // 0vh 到 66vh随机高度随机高度


  return {
    left: `${explosionX}%`,
    top: `${explosionYVh}vh`,
    // 爆炸容器不需要延迟，粒子自己处理时序
  }
}

// 烟花粒子样式 - 爆炸后的散射粒子
const getFireworkParticleStyle = (particleIndex: number, fireworkIndex: number) => {
  const config = getEffectConfig('fireworks')
  const opacity = config.opacity || 0.9

  // 使用种子确保每次生成相同的结果
  const seed = fireworkIndex * 777 + particleIndex * 333

  // 24个粒子均匀分布在360度，增加适量随机性
  const baseAngle = (particleIndex / 24) * 360
  const randomOffset = ((seed * 7) % 40) - 20 // -20到20度的随机偏移
  const angle = baseAngle + randomOffset

  const baseDistance = 120 + ((seed * 13) % 60) // 120-180px固定距离
  const particleDistance = baseDistance

  const targetX = Math.cos(angle * Math.PI / 180) * particleDistance
  const targetY = Math.sin(angle * Math.PI / 180) * particleDistance

  // 初始透明度设为0，动画开始后才显示
  const finalOpacity = 0
  const size = 3 + ((seed * 17) % 4) // 3-6px固定大小

  // 计算延迟 - 所有粒子同时开始，避免一波波炸开
  // 粒子在火箭结束时立即开始扩散
  const particleSeed = fireworkIndex * 777
  const particleExplosionDistance = ((particleSeed * 31) % 101)
  const rocketDuration = 0.8 + (particleExplosionDistance / 100) * 0.7
  const baseDelay = ((fireworkIndex * 1000) % 15000)
  // 火箭延迟 + 火箭时长 = 火箭结束时间，粒子立即开始
  const delay = baseDelay + (rocketDuration * 1000)

  // 使用种子选择固定颜色
  const colors = ['#ff6b6b', '#4ecdc4', '#45b7d1', '#f9ca24', '#f0932b', '#ff9ff3', '#54a0ff', '#5f27cd']
  const color = colors[seed % colors.length]

  return {
    '--target-x': `${targetX}px`,
    '--target-y': `${targetY}px`,
    '--particle-delay': `${delay}ms`,
    '--particle-size': `${size}px`,
    '--particle-color': color,
    opacity: finalOpacity
  }
}

// 秋叶样式
const getAutumnLeafStyle = (index: number) => {
  const config = getEffectConfig('autumn_leaves')
  const colors = config.colors || ['#d2691e', '#daa520', '#cd853f', '#deb887']
  const color = colors[index % colors.length]
  const fallSpeed = config.fallSpeed || 1
  const isBackground = isBackgroundEffect('autumn_leaves')

  // 背景层特效：更多的秋叶，更长的延迟，确保持续覆盖整个屏幕
  const delay = isBackground ? Math.random() * 25000 : Math.random() * 12000 // 背景层延迟更长

  return {
    left: `${Math.random() * 100}%`,
    backgroundColor: color,
    animationDelay: `${delay}ms`,
    animationDuration: `${8000 / fallSpeed}ms`, // 背景层动画时间更长
    transform: `rotate(${Math.random() * 360}deg) scale(${0.8 + Math.random() * 0.4})`,
    opacity: 0 // 初始透明，与动画保持一致
  }
}
</script>

<style scoped>
.atmosphere-effects {
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
  overflow: hidden;
}

.atmosphere-effects.above-layer {
  z-index: 2;
}

.atmosphere-effects.background-layer {
  z-index: 0;
}

/* 全局样式确保特效可见 */
.atmosphere-effects .star,
.atmosphere-effects .snow-flake,
.atmosphere-effects .cherry-petal,
.atmosphere-effects .birthday-balloon,
.atmosphere-effects .birthday-confetti,
.atmosphere-effects .meteor,
.atmosphere-effects .firework,
.atmosphere-effects .autumn-leaf {
  position: absolute;
}

/* 下雪特效 */
.snow-container {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
}

.snow-flake {
  position: absolute;
  background: white;
  border-radius: 50%;
  animation: snowfall linear infinite;
}

@keyframes snowfall {
  0% {
    transform: translateY(-10px) rotate(0deg);
    opacity: 0;
  }
  5% {
    opacity: 1;
  }
  95% {
    opacity: 1;
  }
  100% {
    transform: translateY(110vh) rotate(360deg);
    opacity: 0;
  }
}

/* 樱花特效 */
.cherry-blossom-container {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
}

.cherry-petal {
  position: absolute;
  background: linear-gradient(45deg, #ffb3ba, #ffdfba);
  border-radius: 50% 0 50% 50%;
  animation: cherry-fall linear infinite;
}

@keyframes cherry-fall {
  0% {
    transform: translateY(-20px) rotate(0deg);
    opacity: 0;
  }
  5% {
    opacity: 1;
  }
  95% {
    opacity: 0.9;
  }
  100% {
    transform: translateY(120vh) rotate(720deg);
    opacity: 0;
  }
}

/* 生日特效 */
.birthday-container {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
}

.birthday-balloon {
  position: absolute;
  bottom: -50px;
  width: 30px;
  height: 40px;
  border-radius: 50% 50% 50% 50% / 60% 60% 40% 40%;
  animation: balloon-rise 8s ease-in-out infinite;
}

@keyframes balloon-rise {
  0% {
    transform: translateY(0) scale(1);
  }
  50% {
    transform: translateY(-80vh) scale(1.1);
  }
  100% {
    transform: translateY(-150vh) scale(0.8);
    opacity: 0;
  }
}

.birthday-confetti {
  position: absolute;
  top: -10px;
  width: 8px;
  height: 8px;
  animation: confetti-fall 3s ease-in-out infinite;
}

@keyframes confetti-fall {
  0% {
    transform: translateY(0) rotate(0deg);
    opacity: 1;
  }
  100% {
    transform: translateY(100vh) rotate(720deg);
    opacity: 0;
  }
}

/* 流星特效 */
.meteor-container {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
}

.meteor {
  position: absolute;
  top: -5px;
  width: 2px;
  height: var(--trail-length);
  background: linear-gradient(to bottom, rgba(255,255,255,0), rgba(255,255,255,0.8));
  animation: meteor-fall linear infinite;
}

@keyframes meteor-fall {
  0% {
    transform: translateX(0) translateY(0) rotate(45deg);
    opacity: 0;
  }
  10% {
    opacity: 1;
  }
  90% {
    opacity: 1;
  }
  100% {
    transform: translateX(200px) translateY(200px) rotate(45deg);
    opacity: 0;
  }
}

/* 星空特效 */
.starry-sky-container {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
}

.star {
  position: absolute;
  width: 3px;
  height: 3px;
  background: white;
  border-radius: 50%;
  box-shadow: 0 0 4px rgba(255, 255, 255, 0.8);
  animation: star-twinkle ease-in-out infinite alternate;
}

@keyframes star-twinkle {
  0% {
    opacity: 0;
    transform: scale(0.8);
  }
  50% {
    opacity: 1;
    transform: scale(1.2);
  }
  100% {
    opacity: 0;
    transform: scale(0.8);
  }
}

/* 烟花特效 */
.fireworks-container {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
}

.firework-rocket {
  position: absolute;
  width: 4px;
  height: 12px;
  background: linear-gradient(to top, var(--rocket-color), #ffffff);
  border-radius: 2px 2px 0 0;
  animation: firework-rocket-launch var(--rocket-duration, 1.5s) ease-out forwards;
  animation-delay: var(--rocket-delay);
  opacity: 0;
}

.firework-explosion {
  position: absolute;
  width: 0;
  height: 0;
  animation: none;
}


.firework-particle {
  position: absolute;
  top: 0;
  left: 0;
  width: var(--particle-size, 3px);
  height: var(--particle-size, 3px);
  background: var(--particle-color, var(--rocket-color));
  border-radius: 50%;
  animation: firework-particle-explode 2s ease-out forwards;
  animation-delay: var(--particle-delay);
  opacity: 0;
  box-shadow: 0 0 4px var(--particle-color, var(--rocket-color));
}

@keyframes firework-rocket-launch {
  0% {
    transform: translateY(0) scale(0.5);
    opacity: 0;
  }
  10% {
    opacity: 1;
  }
  90% {
    transform: translateY(calc(var(--explosion-y-vh) - 100vh)) scale(1);
    opacity: 1;
  }
  100% {
    transform: translateY(calc(var(--explosion-y-vh) - 100vh)) scale(0);
    opacity: 0;
  }
}



@keyframes firework-particle-explode {
  0% {
    transform: translate(0, 0) scale(0);
    opacity: 0;
  }
  20% {
    opacity: 1;
    transform: translate(calc(var(--target-x) * 0.4), calc(var(--target-y) * 0.4)) scale(1);
  }
  60% {
    transform: translate(calc(var(--target-x) * 0.8), calc(var(--target-y) * 0.8)) scale(0.8);
    opacity: 0.9;
  }
  100% {
    transform: translate(var(--target-x), var(--target-y)) scale(0.2);
    opacity: 0;
  }
}

/* 秋叶特效 */
.autumn-leaves-container {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
}

.autumn-leaf {
  position: absolute;
  top: -20px;
  width: 20px;
  height: 30px;
  clip-path: polygon(50% 0%, 100% 25%, 100% 75%, 50% 100%, 0% 75%, 0% 25%);
  animation: leaf-fall linear infinite;
}

@keyframes leaf-fall {
  0% {
    transform: translateY(-20px) rotate(0deg);
    opacity: 0;
  }
  5% {
    opacity: 1;
  }
  95% {
    opacity: 0.9;
  }
  100% {
    transform: translateY(120vh) rotate(720deg);
    opacity: 0;
  }
}

/* 层级调整通过内联样式实现 */

/* 强度调整 */
.intensity-low {
  opacity: 0.6;
}

.intensity-medium {
  opacity: 0.8;
}

.intensity-high {
  opacity: 1;
}
</style>


