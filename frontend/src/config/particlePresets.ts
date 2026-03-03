import type { ParticlePreset } from '@/components/CanvasParticleRenderer.vue'

const BASE = '/particles'

export const particlePresets: Record<string, ParticlePreset> = {
  cherry_blossom: {
    type: 'cherry_blossom',
    images: [
      `${BASE}/cherry_blossom/petal1.svg`,
      `${BASE}/cherry_blossom/petal2.svg`,
      `${BASE}/cherry_blossom/petal3.svg`,
      `${BASE}/cherry_blossom/petal4.svg`,
    ],
    physics: {
      gravity: 6,
      windX: 12,
      windVariance: 18,
      rotationSpeed: 1.5,
      flutter: 10,
      minSpeed: 18,
      maxSpeed: 45,
      minSize: 24,
      maxSize: 48,
      fadeIn: 1200,
      fadeOut: 2500,
      sway: true,
      swayAmplitude: 35,
      swayFrequency: 0.8,
    },
  },

  snow: {
    type: 'snow',
    images: [
      `${BASE}/snow/flake1.svg`,
      `${BASE}/snow/flake2.svg`,
      `${BASE}/snow/flake3.svg`,
    ],
    physics: {
      gravity: 3,
      windX: 5,
      windVariance: 12,
      rotationSpeed: 0.8,
      flutter: 4,
      minSpeed: 12,
      maxSpeed: 35,
      minSize: 14,
      maxSize: 36,
      fadeIn: 1000,
      fadeOut: 1500,
      sway: true,
      swayAmplitude: 22,
      swayFrequency: 0.6,
    },
  },

  autumn_leaves: {
    type: 'autumn_leaves',
    images: [
      `${BASE}/autumn_leaves/leaf1.svg`,
      `${BASE}/autumn_leaves/leaf2.svg`,
      `${BASE}/autumn_leaves/leaf3.svg`,
      `${BASE}/autumn_leaves/leaf4.svg`,
    ],
    physics: {
      gravity: 10,
      windX: 18,
      windVariance: 22,
      rotationSpeed: 2.0,
      flutter: 14,
      minSpeed: 15,
      maxSpeed: 40,
      minSize: 28,
      maxSize: 52,
      fadeIn: 1000,
      fadeOut: 2000,
      sway: true,
      swayAmplitude: 45,
      swayFrequency: 0.5,
    },
  },
}

export const intensityParticleCount: Record<string, Record<string, number>> = {
  low:    { cherry_blossom: 15, snow: 25, autumn_leaves: 12 },
  medium: { cherry_blossom: 35, snow: 60, autumn_leaves: 25 },
  high:   { cherry_blossom: 60, snow: 100, autumn_leaves: 45 },
}

export const hasImagePreset = (type: string): boolean => {
  return type in particlePresets
}

export const getPreset = (type: string): ParticlePreset | null => {
  return particlePresets[type] || null
}

export const getParticleCount = (type: string, intensity: string, customCount?: number): number => {
  if (customCount !== undefined && intensity === 'custom') {
    const baseCount = intensityParticleCount.medium[type] || 35
    return Math.round(baseCount * (customCount / 5))
  }
  return intensityParticleCount[intensity]?.[type] || intensityParticleCount.medium[type] || 35
}
