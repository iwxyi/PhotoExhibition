import type { ParticlePreset } from '@/components/CanvasParticleRenderer.vue'

const B = '/particles'

export interface EffectParamDef {
  key: string
  label: string
  min?: number
  max?: number
}

export const effectParamDefs: Record<string, EffectParamDef[]> = {
  cherry_blossom: [
    { key: 'count', label: '花瓣数量' },
    { key: 'speed', label: '飘落速度' },
    { key: 'size', label: '花瓣大小' },
    { key: 'sway', label: '摇摆幅度' },
    { key: 'opacity', label: '透明度' },
  ],
  snow: [
    { key: 'count', label: '雪花数量' },
    { key: 'speed', label: '飘落速度' },
    { key: 'size', label: '雪花大小' },
    { key: 'sway', label: '飘散程度' },
    { key: 'opacity', label: '透明度' },
  ],
  autumn_leaves: [
    { key: 'count', label: '落叶数量' },
    { key: 'speed', label: '飘落速度' },
    { key: 'size', label: '叶片大小' },
    { key: 'rotation', label: '旋转速度' },
    { key: 'opacity', label: '透明度' },
  ],
  starry_sky: [
    { key: 'count', label: '星星数量' },
    { key: 'twinkle', label: '闪烁速度' },
    { key: 'size', label: '星星大小' },
    { key: 'brightness', label: '亮度' },
  ],
  meteor: [
    { key: 'count', label: '流星数量' },
    { key: 'speed', label: '划过速度' },
    { key: 'trail', label: '尾迹长度' },
    { key: 'brightness', label: '亮度' },
  ],
  firefly: [
    { key: 'count', label: '萤火虫数量' },
    { key: 'speed', label: '飘动速度' },
    { key: 'glow', label: '发光强度' },
    { key: 'size', label: '大小' },
    { key: 'pulse', label: '闪烁频率' },
  ],
  rain: [
    { key: 'count', label: '雨量' },
    { key: 'speed', label: '下落速度' },
    { key: 'size', label: '雨滴大小' },
    { key: 'wind', label: '风力' },
    { key: 'opacity', label: '透明度' },
  ],
  bubble: [
    { key: 'count', label: '气泡数量' },
    { key: 'speed', label: '上升速度' },
    { key: 'size', label: '气泡大小' },
    { key: 'sway', label: '摇摆幅度' },
    { key: 'opacity', label: '透明度' },
  ],
  dandelion: [
    { key: 'count', label: '种子数量' },
    { key: 'speed', label: '飘动速度' },
    { key: 'size', label: '大小' },
    { key: 'wind', label: '风力' },
    { key: 'opacity', label: '透明度' },
  ],
  hearts: [
    { key: 'count', label: '爱心数量' },
    { key: 'speed', label: '上升速度' },
    { key: 'size', label: '爱心大小' },
    { key: 'sway', label: '摇摆幅度' },
    { key: 'opacity', label: '透明度' },
  ],
  dust: [
    { key: 'count', label: '光尘数量' },
    { key: 'speed', label: '飘动速度' },
    { key: 'size', label: '颗粒大小' },
    { key: 'glow', label: '发光强度' },
    { key: 'opacity', label: '透明度' },
  ],
}

export const particlePresets: Record<string, ParticlePreset> = {
  cherry_blossom: {
    type: 'cherry_blossom',
    images: [
      `${B}/cherry_blossom/petal1.svg`, `${B}/cherry_blossom/petal2.svg`,
      `${B}/cherry_blossom/petal3.svg`, `${B}/cherry_blossom/petal4.svg`,
    ],
    physics: {
      gravity: 6, windX: 12, windVariance: 18, rotationSpeed: 1.5,
      flutter: 10, minSpeed: 18, maxSpeed: 45, minSize: 24, maxSize: 48,
      fadeIn: 1200, fadeOut: 2500,
      sway: true, swayAmplitude: 35, swayFrequency: 0.8,
    },
  },

  snow: {
    type: 'snow',
    images: [`${B}/snow/flake1.svg`, `${B}/snow/flake2.svg`, `${B}/snow/flake3.svg`],
    physics: {
      gravity: 3, windX: 5, windVariance: 12, rotationSpeed: 0.8,
      flutter: 4, minSpeed: 12, maxSpeed: 35, minSize: 14, maxSize: 36,
      fadeIn: 1000, fadeOut: 1500,
      sway: true, swayAmplitude: 22, swayFrequency: 0.6,
    },
  },

  autumn_leaves: {
    type: 'autumn_leaves',
    images: [
      `${B}/autumn_leaves/leaf1.svg`, `${B}/autumn_leaves/leaf2.svg`,
      `${B}/autumn_leaves/leaf3.svg`, `${B}/autumn_leaves/leaf4.svg`,
    ],
    physics: {
      gravity: 10, windX: 18, windVariance: 22, rotationSpeed: 2.0,
      flutter: 14, minSpeed: 15, maxSpeed: 40, minSize: 28, maxSize: 52,
      fadeIn: 1000, fadeOut: 2000,
      sway: true, swayAmplitude: 45, swayFrequency: 0.5,
    },
  },

  starry_sky: {
    type: 'starry_sky',
    images: [`${B}/starry_sky/star2.svg`],
    physics: {
      gravity: 0, windX: 0, windVariance: 0, rotationSpeed: 0,
      flutter: 0, minSpeed: 0, maxSpeed: 0, minSize: 3, maxSize: 10,
      fadeIn: 2000, fadeOut: 2000,
      sway: false, swayAmplitude: 0, swayFrequency: 0,
      pulse: true, pulseSpeed: 1.2, pulseMin: 0.1,
      spawnMode: 'random',
      minLifetime: 5000, maxLifetime: 15000,
    },
  },

  meteor: {
    type: 'meteor',
    images: [`${B}/meteor/meteor1.svg`, `${B}/meteor/meteor2.svg`],
    physics: {
      gravity: 2, windX: 0, windVariance: 5, rotationSpeed: 0,
      flutter: 0, minSpeed: 200, maxSpeed: 400, minSize: 18, maxSize: 32,
      fadeIn: 200, fadeOut: 600,
      sway: false, swayAmplitude: 0, swayFrequency: 0,
      angle: 135, angleVariance: 15,
      trail: 5, trailFade: 0.55,
      spawnMode: 'top', aspectRatio: 0.2,
      lockRotation: true, rotationOffset: Math.PI / 2,
      minLifetime: 1500, maxLifetime: 3500,
    },
  },

  firefly: {
    type: 'firefly',
    images: [`${B}/firefly/firefly1.svg`, `${B}/firefly/firefly2.svg`],
    physics: {
      gravity: 0, windX: 0, windVariance: 5, rotationSpeed: 0,
      flutter: 3, minSpeed: 3, maxSpeed: 12, minSize: 12, maxSize: 28,
      fadeIn: 1500, fadeOut: 2000,
      sway: false, swayAmplitude: 0, swayFrequency: 0,
      glow: 16, glowColor: 'rgba(255,255,100,0.6)',
      pulse: true, pulseSpeed: 2.5, pulseMin: 0.1,
      wander: true, wanderStrength: 25,
      spawnMode: 'random',
      minLifetime: 8000, maxLifetime: 20000,
    },
  },

  rain: {
    type: 'rain',
    images: [`${B}/rain/drop1.svg`, `${B}/rain/drop2.svg`],
    physics: {
      gravity: 30, windX: 15, windVariance: 3, rotationSpeed: 0,
      flutter: 0, minSpeed: 250, maxSpeed: 450, minSize: 10, maxSize: 22,
      fadeIn: 100, fadeOut: 200,
      sway: false, swayAmplitude: 0, swayFrequency: 0,
      angle: 84, angleVariance: 3, aspectRatio: 0.2,
      lockRotation: true, rotationOffset: Math.PI / 2,
      minLifetime: 3000, maxLifetime: 5000,
    },
  },

  bubble: {
    type: 'bubble',
    images: [`${B}/bubble/bubble1.svg`, `${B}/bubble/bubble2.svg`],
    physics: {
      gravity: -6, windX: 2, windVariance: 8, rotationSpeed: 0.1,
      flutter: 3, minSpeed: 15, maxSpeed: 35, minSize: 18, maxSize: 48,
      fadeIn: 1500, fadeOut: 2000,
      sway: true, swayAmplitude: 25, swayFrequency: 0.5,
      angle: 270, angleVariance: 20,
      spawnMode: 'bottom', scaleOscillation: 0.08,
      minLifetime: 8000, maxLifetime: 18000,
    },
  },

  dandelion: {
    type: 'dandelion',
    images: [`${B}/dandelion/seed1.svg`, `${B}/dandelion/seed2.svg`],
    physics: {
      gravity: 1.5, windX: 15, windVariance: 20, rotationSpeed: 0.4,
      flutter: 12, minSpeed: 6, maxSpeed: 18, minSize: 20, maxSize: 38,
      fadeIn: 1500, fadeOut: 2500,
      sway: true, swayAmplitude: 35, swayFrequency: 0.4,
      wander: true, wanderStrength: 10,
      minLifetime: 12000, maxLifetime: 25000,
    },
  },

  hearts: {
    type: 'hearts',
    images: [`${B}/hearts/heart1.svg`, `${B}/hearts/heart2.svg`],
    physics: {
      gravity: -4, windX: 0, windVariance: 10, rotationSpeed: 0.3,
      flutter: 5, minSpeed: 12, maxSpeed: 30, minSize: 16, maxSize: 36,
      fadeIn: 1200, fadeOut: 2000,
      sway: true, swayAmplitude: 30, swayFrequency: 0.6,
      angle: 270, angleVariance: 30,
      spawnMode: 'bottom', scaleOscillation: 0.1,
      pulse: true, pulseSpeed: 3, pulseMin: 0.6,
      minLifetime: 6000, maxLifetime: 14000,
    },
  },

  dust: {
    type: 'dust',
    images: [`${B}/dust/mote1.svg`, `${B}/dust/mote2.svg`],
    physics: {
      gravity: 0.5, windX: 3, windVariance: 8, rotationSpeed: 0,
      flutter: 6, minSpeed: 2, maxSpeed: 8, minSize: 6, maxSize: 18,
      fadeIn: 2000, fadeOut: 3000,
      sway: true, swayAmplitude: 15, swayFrequency: 0.3,
      glow: 8, glowColor: 'rgba(255,240,200,0.5)',
      pulse: true, pulseSpeed: 1.0, pulseMin: 0.3,
      wander: true, wanderStrength: 8,
      spawnMode: 'random',
      minLifetime: 10000, maxLifetime: 25000,
    },
  },

}

export const intensityParticleCount: Record<string, Record<string, number>> = {
  low: {
    cherry_blossom: 15, snow: 25, autumn_leaves: 12,
    starry_sky: 30, meteor: 3, firefly: 10, rain: 80,
    bubble: 8, dandelion: 8, hearts: 8, dust: 30,
  },
  medium: {
    cherry_blossom: 35, snow: 60, autumn_leaves: 25,
    starry_sky: 60, meteor: 6, firefly: 20, rain: 160,
    bubble: 15, dandelion: 15, hearts: 15, dust: 60,
  },
  high: {
    cherry_blossom: 60, snow: 100, autumn_leaves: 45,
    starry_sky: 100, meteor: 10, firefly: 35, rain: 300,
    bubble: 25, dandelion: 25, hearts: 25, dust: 100,
  },
}

export const hasImagePreset = (type: string): boolean => type in particlePresets

export const getPreset = (type: string): ParticlePreset | null => particlePresets[type] || null

export const getParticleCount = (type: string, intensity: string, customCount?: number): number => {
  if (customCount !== undefined && intensity === 'custom') {
    const baseCount = intensityParticleCount.medium[type] || 35
    return Math.round(baseCount * (customCount / 5))
  }
  return intensityParticleCount[intensity]?.[type] || intensityParticleCount.medium[type] || 35
}

export const getEffectParamDefs = (type: string): EffectParamDef[] => {
  return effectParamDefs[type] || [
    { key: 'count', label: '数量' },
    { key: 'speed', label: '速度' },
    { key: 'size', label: '大小' },
    { key: 'opacity', label: '透明度' },
  ]
}
