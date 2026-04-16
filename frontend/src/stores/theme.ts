import { defineStore } from 'pinia'
import { ref, watch } from 'vue'
import { api } from '@/api'
import { useAuthStore } from '@/stores/auth'
import { pushSyncNotice } from '@/composables/useSyncNotice'

interface ThemeDefinition {
  name: string
  mood: string
  accentHue: number
  accentSaturation: string
  accentLightness: string
  glassBlur: string
  radius: string
  preview: {
    primary: string
    secondary: string
    base: string
  }
}

interface StyleFamilyDefinition {
  name: string
  description: string
  surface: 'soft' | 'glass' | 'contrast'
  density: 'comfortable' | 'compact'
  hero: 'layered' | 'flat' | 'editorial'
  emphasis: string
  vibe: string
}

const THEME_KEY_STORAGE = 'pe-theme-key'
const COLOR_MODE_STORAGE = 'theme'
const USER_THEME_CACHE_PREFIX = 'pe-admin-theme-preferences:'
const LAST_THEME_CACHE_KEY = 'pe-admin-theme-last'
const THEME_SYNC_KEY = 'pe-theme-sync'
const THEME_SYNC_CHANNEL = 'pe-theme-sync-channel'

const canUseWindow = typeof window !== 'undefined'
const themeSyncSourceId = canUseWindow ? `theme-${Math.random().toString(36).slice(2, 10)}` : 'theme-server'
const themeSyncChannel = canUseWindow && 'BroadcastChannel' in window
  ? new BroadcastChannel(THEME_SYNC_CHANNEL)
  : null
let themeSyncInitialized = false

const styleFamilies = {
  material: {
    name: 'Material',
    description: '层级清晰，卡片和按钮更像产品后台。',
    surface: 'soft',
    density: 'comfortable',
    hero: 'layered',
    emphasis: '清晰层次',
    vibe: '产品后台'
  },
  glass: {
    name: 'Glass',
    description: '毛玻璃、通透高光，更偏展示型控制台。',
    surface: 'glass',
    density: 'comfortable',
    hero: 'layered',
    emphasis: '通透光感',
    vibe: '未来面板'
  },
  classic: {
    name: 'Classic',
    description: '更稳重，边框和分区更明显，接近企业后台。',
    surface: 'soft',
    density: 'comfortable',
    hero: 'flat',
    emphasis: '稳重规整',
    vibe: '企业控制台'
  },
  gallery: {
    name: 'Gallery',
    description: '更强调展陈感，标题和页面留白更强。',
    surface: 'contrast',
    density: 'comfortable',
    hero: 'editorial',
    emphasis: '展陈氛围',
    vibe: '展览导览'
  },
  compact: {
    name: 'Compact',
    description: '更紧凑，适合高密度管理和运营操作。',
    surface: 'soft',
    density: 'compact',
    hero: 'flat',
    emphasis: '高密操作',
    vibe: '运营面板'
  },
  brutalist: {
    name: 'Brutalist',
    description: '粗边框、硬直角、强对比，像工业化操作台。',
    surface: 'contrast',
    density: 'comfortable',
    hero: 'flat',
    emphasis: '硬核冲击',
    vibe: '工业控制'
  },
  paper: {
    name: 'Paper',
    description: '像编辑台与排版纸张，柔和而克制。',
    surface: 'soft',
    density: 'comfortable',
    hero: 'editorial',
    emphasis: '纸本编排',
    vibe: '文档工作台'
  },
  neon: {
    name: 'Neon',
    description: '深底霓虹、高亮描边，适合强科技感面板。',
    surface: 'contrast',
    density: 'comfortable',
    hero: 'layered',
    emphasis: '赛博发光',
    vibe: '夜间中控'
  },
  zen: {
    name: 'Zen',
    description: '留白更多，边界更轻，节奏更安静。',
    surface: 'soft',
    density: 'comfortable',
    hero: 'editorial',
    emphasis: '安静留白',
    vibe: '东方简静'
  },
  terminal: {
    name: 'Terminal',
    description: '等宽字体、硬线框和状态灯，像运维终端。',
    surface: 'contrast',
    density: 'compact',
    hero: 'flat',
    emphasis: '命令终端',
    vibe: '运维控制'
  }
} satisfies Record<string, StyleFamilyDefinition>

const themes = {
  default: {
    name: '默认',
    mood: '冷静科技',
    accentHue: 222,
    accentSaturation: '84%',
    accentLightness: '56%',
    glassBlur: '18px',
    radius: '16px',
    preview: {
      primary: '#3b82f6',
      secondary: '#60a5fa',
      base: '#0f172a'
    }
  },
  ocean: {
    name: '海蓝',
    mood: '清澈流动',
    accentHue: 199,
    accentSaturation: '89%',
    accentLightness: '55%',
    glassBlur: '22px',
    radius: '18px',
    preview: {
      primary: '#38bdf8',
      secondary: '#0ea5e9',
      base: '#082f49'
    }
  },
  forest: {
    name: '森林',
    mood: '沉静自然',
    accentHue: 142,
    accentSaturation: '72%',
    accentLightness: '42%',
    glassBlur: '16px',
    radius: '14px',
    preview: {
      primary: '#22c55e',
      secondary: '#86efac',
      base: '#052e16'
    }
  },
  sunset: {
    name: '日落',
    mood: '暖色余晖',
    accentHue: 18,
    accentSaturation: '92%',
    accentLightness: '60%',
    glassBlur: '20px',
    radius: '18px',
    preview: {
      primary: '#fb7185',
      secondary: '#f59e0b',
      base: '#431407'
    }
  },
  mono: {
    name: '黑白',
    mood: '极简画廊',
    accentHue: 0,
    accentSaturation: '0%',
    accentLightness: '90%',
    glassBlur: '12px',
    radius: '12px',
    preview: {
      primary: '#e5e7eb',
      secondary: '#a1a1aa',
      base: '#09090b'
    }
  },
  ink: {
    name: '水墨',
    mood: '中式留白',
    accentHue: 210,
    accentSaturation: '16%',
    accentLightness: '82%',
    glassBlur: '14px',
    radius: '20px',
    preview: {
      primary: '#e2e8f0',
      secondary: '#94a3b8',
      base: '#111827'
    }
  },
  vermilion: {
    name: '朱砂',
    mood: '中式华彩',
    accentHue: 8,
    accentSaturation: '78%',
    accentLightness: '56%',
    glassBlur: '16px',
    radius: '18px',
    preview: {
      primary: '#dc2626',
      secondary: '#f59e0b',
      base: '#2b0b0b'
    }
  },
  celadon: {
    name: '青瓷',
    mood: '温润雅致',
    accentHue: 168,
    accentSaturation: '34%',
    accentLightness: '64%',
    glassBlur: '24px',
    radius: '24px',
    preview: {
      primary: '#5eead4',
      secondary: '#99f6e4',
      base: '#0f2f2d'
    }
  },
  imperial: {
    name: '宫阙',
    mood: '东方庄重',
    accentHue: 45,
    accentSaturation: '84%',
    accentLightness: '58%',
    glassBlur: '18px',
    radius: '16px',
    preview: {
      primary: '#fbbf24',
      secondary: '#f87171',
      base: '#3b0a0a'
    }
  },
  rosewood: {
    name: '红木',
    mood: '古典厚重',
    accentHue: 12,
    accentSaturation: '48%',
    accentLightness: '44%',
    glassBlur: '14px',
    radius: '14px',
    preview: {
      primary: '#b45309',
      secondary: '#fca5a5',
      base: '#2c1410'
    }
  },
  baroque: {
    name: 'Baroque',
    mood: '西方古典',
    accentHue: 38,
    accentSaturation: '76%',
    accentLightness: '60%',
    glassBlur: '20px',
    radius: '22px',
    preview: {
      primary: '#f5d0fe',
      secondary: '#fbbf24',
      base: '#241018'
    }
  },
  cathedral: {
    name: 'Cathedral',
    mood: '彩窗肃穆',
    accentHue: 258,
    accentSaturation: '68%',
    accentLightness: '64%',
    glassBlur: '20px',
    radius: '20px',
    preview: {
      primary: '#818cf8',
      secondary: '#22d3ee',
      base: '#111827'
    }
  },
  manor: {
    name: 'Manor',
    mood: '英伦庄园',
    accentHue: 156,
    accentSaturation: '32%',
    accentLightness: '54%',
    glassBlur: '16px',
    radius: '14px',
    preview: {
      primary: '#4ade80',
      secondary: '#facc15',
      base: '#1f2937'
    }
  },
  noir: {
    name: 'Film Noir',
    mood: '电影黑色',
    accentHue: 216,
    accentSaturation: '12%',
    accentLightness: '72%',
    glassBlur: '10px',
    radius: '10px',
    preview: {
      primary: '#d4d4d8',
      secondary: '#71717a',
      base: '#09090b'
    }
  },
  aurora: {
    name: '极光',
    mood: '梦幻流彩',
    accentHue: 186,
    accentSaturation: '90%',
    accentLightness: '62%',
    glassBlur: '24px',
    radius: '28px',
    preview: {
      primary: '#22d3ee',
      secondary: '#a78bfa',
      base: '#0f172a'
    }
  },
  sakura: {
    name: '樱色',
    mood: '柔和浪漫',
    accentHue: 338,
    accentSaturation: '82%',
    accentLightness: '72%',
    glassBlur: '26px',
    radius: '30px',
    preview: {
      primary: '#f9a8d4',
      secondary: '#fbcfe8',
      base: '#4c1d34'
    }
  },
  lavender: {
    name: '薰衣',
    mood: '轻柔安静',
    accentHue: 266,
    accentSaturation: '65%',
    accentLightness: '72%',
    glassBlur: '24px',
    radius: '26px',
    preview: {
      primary: '#c4b5fd',
      secondary: '#e9d5ff',
      base: '#2e1065'
    }
  },
  ember: {
    name: '余烬',
    mood: '炽热夜色',
    accentHue: 15,
    accentSaturation: '95%',
    accentLightness: '58%',
    glassBlur: '18px',
    radius: '16px',
    preview: {
      primary: '#f97316',
      secondary: '#ef4444',
      base: '#1c1917'
    }
  },
  mint: {
    name: '薄荷',
    mood: '清新醒目',
    accentHue: 160,
    accentSaturation: '72%',
    accentLightness: '56%',
    glassBlur: '22px',
    radius: '22px',
    preview: {
      primary: '#34d399',
      secondary: '#a7f3d0',
      base: '#062c25'
    }
  },
  cocoa: {
    name: '可可',
    mood: '温暖松弛',
    accentHue: 24,
    accentSaturation: '54%',
    accentLightness: '55%',
    glassBlur: '16px',
    radius: '18px',
    preview: {
      primary: '#d6a26e',
      secondary: '#fde68a',
      base: '#2a211d'
    }
  },
  festive: {
    name: '庆典',
    mood: '热闹高光',
    accentHue: 347,
    accentSaturation: '92%',
    accentLightness: '60%',
    glassBlur: '18px',
    radius: '20px',
    preview: {
      primary: '#f43f5e',
      secondary: '#facc15',
      base: '#3f0d1d'
    }
  },
  bamboo: {
    name: '竹影',
    mood: '清简东方',
    accentHue: 138,
    accentSaturation: '34%',
    accentLightness: '48%',
    glassBlur: '18px',
    radius: '20px',
    preview: {
      primary: '#65a30d',
      secondary: '#bef264',
      base: '#132a13'
    }
  },
  peony: {
    name: '牡丹',
    mood: '雍容明艳',
    accentHue: 336,
    accentSaturation: '74%',
    accentLightness: '66%',
    glassBlur: '22px',
    radius: '24px',
    preview: {
      primary: '#ec4899',
      secondary: '#fda4af',
      base: '#4a102a'
    }
  },
  jadeite: {
    name: '翡翠',
    mood: '通透莹润',
    accentHue: 164,
    accentSaturation: '62%',
    accentLightness: '52%',
    glassBlur: '24px',
    radius: '24px',
    preview: {
      primary: '#10b981',
      secondary: '#6ee7b7',
      base: '#052e2b'
    }
  },
  parchment: {
    name: '羊皮纸',
    mood: '复古文卷',
    accentHue: 38,
    accentSaturation: '42%',
    accentLightness: '62%',
    glassBlur: '10px',
    radius: '12px',
    preview: {
      primary: '#f4d7a1',
      secondary: '#c08457',
      base: '#3b2f2f'
    }
  },
  desert: {
    name: '沙丘',
    mood: '旷野暖风',
    accentHue: 31,
    accentSaturation: '76%',
    accentLightness: '60%',
    glassBlur: '18px',
    radius: '18px',
    preview: {
      primary: '#fb923c',
      secondary: '#fdba74',
      base: '#4a2712'
    }
  },
  glacier: {
    name: '冰川',
    mood: '寒冽清透',
    accentHue: 198,
    accentSaturation: '74%',
    accentLightness: '68%',
    glassBlur: '26px',
    radius: '26px',
    preview: {
      primary: '#7dd3fc',
      secondary: '#bae6fd',
      base: '#082f49'
    }
  },
  midnight: {
    name: '子夜',
    mood: '深海静默',
    accentHue: 224,
    accentSaturation: '58%',
    accentLightness: '52%',
    glassBlur: '16px',
    radius: '18px',
    preview: {
      primary: '#6366f1',
      secondary: '#38bdf8',
      base: '#020617'
    }
  },
  nebula: {
    name: '星云',
    mood: '宇宙迷雾',
    accentHue: 284,
    accentSaturation: '74%',
    accentLightness: '66%',
    glassBlur: '28px',
    radius: '30px',
    preview: {
      primary: '#c084fc',
      secondary: '#22d3ee',
      base: '#1e1b4b'
    }
  },
  citrus: {
    name: '柑橘',
    mood: '轻快明亮',
    accentHue: 48,
    accentSaturation: '96%',
    accentLightness: '58%',
    glassBlur: '20px',
    radius: '20px',
    preview: {
      primary: '#facc15',
      secondary: '#fb7185',
      base: '#3f2a00'
    }
  },
  matcha: {
    name: '抹茶',
    mood: '柔和治愈',
    accentHue: 102,
    accentSaturation: '36%',
    accentLightness: '56%',
    glassBlur: '18px',
    radius: '18px',
    preview: {
      primary: '#84cc16',
      secondary: '#d9f99d',
      base: '#1a2e05'
    }
  },
  coral: {
    name: '珊瑚',
    mood: '海边假日',
    accentHue: 11,
    accentSaturation: '86%',
    accentLightness: '66%',
    glassBlur: '22px',
    radius: '24px',
    preview: {
      primary: '#fb7185',
      secondary: '#fdba74',
      base: '#4c1d1d'
    }
  },
  velvet: {
    name: '丝绒',
    mood: '夜宴华灯',
    accentHue: 320,
    accentSaturation: '58%',
    accentLightness: '54%',
    glassBlur: '20px',
    radius: '22px',
    preview: {
      primary: '#d946ef',
      secondary: '#f9a8d4',
      base: '#3b0764'
    }
  },
  alpine: {
    name: '雪岭',
    mood: '高山晨雾',
    accentHue: 210,
    accentSaturation: '44%',
    accentLightness: '72%',
    glassBlur: '24px',
    radius: '26px',
    preview: {
      primary: '#bfdbfe',
      secondary: '#e2e8f0',
      base: '#1e293b'
    }
  },
  studio: {
    name: 'Studio',
    mood: '现代展陈',
    accentHue: 15,
    accentSaturation: '10%',
    accentLightness: '92%',
    glassBlur: '12px',
    radius: '14px',
    preview: {
      primary: '#f8fafc',
      secondary: '#cbd5e1',
      base: '#111827'
    }
  },
  arcade: {
    name: 'Arcade',
    mood: '霓虹电玩',
    accentHue: 296,
    accentSaturation: '84%',
    accentLightness: '58%',
    glassBlur: '18px',
    radius: '20px',
    preview: {
      primary: '#e879f9',
      secondary: '#22d3ee',
      base: '#172554'
    }
  },
  harvest: {
    name: '丰收',
    mood: '秋日暖金',
    accentHue: 30,
    accentSaturation: '70%',
    accentLightness: '54%',
    glassBlur: '16px',
    radius: '18px',
    preview: {
      primary: '#f59e0b',
      secondary: '#fcd34d',
      base: '#422006'
    }
  },
  lotus: {
    name: '莲雾',
    mood: '清甜轻盈',
    accentHue: 328,
    accentSaturation: '62%',
    accentLightness: '74%',
    glassBlur: '26px',
    radius: '28px',
    preview: {
      primary: '#f9a8d4',
      secondary: '#fde68a',
      base: '#4a1831'
    }
  },
  steel: {
    name: '钢蓝',
    mood: '硬朗理性',
    accentHue: 210,
    accentSaturation: '18%',
    accentLightness: '58%',
    glassBlur: '12px',
    radius: '12px',
    preview: {
      primary: '#94a3b8',
      secondary: '#e2e8f0',
      base: '#0f172a'
    }
  }
} satisfies Record<string, ThemeDefinition>

export type ThemeKey = keyof typeof themes
export type StyleFamilyKey = keyof typeof styleFamilies

export const useThemeStore = defineStore('theme', () => {
  const authStore = useAuthStore()
  const storedMode = localStorage.getItem(COLOR_MODE_STORAGE)
  const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches

  const isDark = ref(storedMode === 'dark' || (!storedMode && prefersDark))
  const currentThemeKey = ref<ThemeKey>(
    (localStorage.getItem(THEME_KEY_STORAGE) as ThemeKey) || 'default'
  )
  const currentStyleFamily = ref<StyleFamilyKey>('material')
  const hydratedUserId = ref<string | null>(null)
  const loading = ref(false)
  const saving = ref(false)
  const ready = ref(false)
  const lastSavedAt = ref<number | null>(null)
  const errorMessage = ref('')

  const buildSyncPayload = () => ({
    themeKey: currentThemeKey.value,
    colorMode: isDark.value ? 'dark' : 'light',
    styleFamily: currentStyleFamily.value,
    sourceId: themeSyncSourceId,
    timestamp: Date.now()
  })

  const buildUserThemeCacheKey = (userId?: string | null) => `${USER_THEME_CACHE_PREFIX}${userId || 'guest'}`

  const readCachedPreference = (userId?: string | null) => {
    const raw = localStorage.getItem(buildUserThemeCacheKey(userId))
    if (!raw) return null
    try {
      const parsed = JSON.parse(raw) as { themeKey?: string; colorMode?: string; styleFamily?: string }
      return parsed
    } catch {
      return null
    }
  }

  const readLastPreference = () => {
    const raw = localStorage.getItem(LAST_THEME_CACHE_KEY)
    if (!raw) return null
    try {
      return JSON.parse(raw) as { themeKey?: string; colorMode?: string; styleFamily?: string }
    } catch {
      return null
    }
  }

  const writeCachedPreference = (userId?: string | null) => {
    const payload = JSON.stringify({
      themeKey: currentThemeKey.value,
      colorMode: isDark.value ? 'dark' : 'light',
      styleFamily: currentStyleFamily.value
    })
    localStorage.setItem(buildUserThemeCacheKey(userId), payload)
    localStorage.setItem(LAST_THEME_CACHE_KEY, payload)
  }

  const applyPreference = (
    preference?: { themeKey?: string | null; colorMode?: string | null; styleFamily?: string | null } | null,
    options?: { writeStorage?: boolean }
  ) => {
    const shouldWriteStorage = options?.writeStorage !== false
    const nextThemeKey = preference?.themeKey && themes[preference.themeKey as ThemeKey]
      ? preference.themeKey as ThemeKey
      : 'default'
    const nextStyleFamily = preference?.styleFamily && styleFamilies[preference.styleFamily as StyleFamilyKey]
      ? preference.styleFamily as StyleFamilyKey
      : 'material'
    const nextIsDark = preference?.colorMode
      ? String(preference.colorMode).toLowerCase() !== 'light'
      : prefersDark
    currentThemeKey.value = nextThemeKey
    currentStyleFamily.value = nextStyleFamily
    isDark.value = nextIsDark
    if (shouldWriteStorage) {
      localStorage.setItem(THEME_KEY_STORAGE, nextThemeKey)
      localStorage.setItem(COLOR_MODE_STORAGE, nextIsDark ? 'dark' : 'light')
    }
    updateDom({ persistLocal: shouldWriteStorage })
  }

  const broadcastPreference = () => {
    if (!canUseWindow) return
    const payload = buildSyncPayload()
    localStorage.setItem(THEME_SYNC_KEY, JSON.stringify(payload))
    themeSyncChannel?.postMessage(payload)
  }

  const syncThemePreference = async () => {
    if (!authStore.token || !authStore.userId) {
      ready.value = true
      return
    }
    loading.value = true
    errorMessage.value = ''
    try {
      const { data } = await api.get('/admin/config/admin-theme')
      applyPreference({
        themeKey: typeof data?.themeKey === 'string' ? data.themeKey : 'default',
        colorMode: typeof data?.colorMode === 'string' ? data.colorMode : 'dark',
        styleFamily: typeof data?.styleFamily === 'string' ? data.styleFamily : 'material'
      })
      writeCachedPreference(authStore.userId)
      hydratedUserId.value = authStore.userId
    } catch (error: any) {
      const cached = readCachedPreference(authStore.userId)
      const fallback = cached || readLastPreference()
      if (fallback) {
        applyPreference(fallback)
        hydratedUserId.value = authStore.userId
      }
      errorMessage.value = error?.response?.data?.error || error?.message || '加载后台主题失败'
    } finally {
      loading.value = false
      ready.value = true
    }
  }

  const persistThemePreference = async () => {
    writeCachedPreference(authStore.userId)
    if (!authStore.token || !authStore.userId) {
      return
    }
    saving.value = true
    errorMessage.value = ''
    try {
      await api.put('/admin/config/admin-theme', {
        themeKey: currentThemeKey.value,
        colorMode: isDark.value ? 'dark' : 'light',
        styleFamily: currentStyleFamily.value
      })
      hydratedUserId.value = authStore.userId
      lastSavedAt.value = Date.now()
    } catch (error: any) {
      errorMessage.value = error?.response?.data?.error || error?.message || '保存后台主题失败'
    } finally {
      saving.value = false
    }
  }

  const setColorMode = async (dark: boolean) => {
    isDark.value = dark
    updateDom()
    broadcastPreference()
    await persistThemePreference()
  }

  const toggleTheme = async () => {
    await setColorMode(!isDark.value)
  }

  const setThemeKey = async (key: ThemeKey) => {
    if (!themes[key]) return
    currentThemeKey.value = key
    localStorage.setItem(THEME_KEY_STORAGE, key)
    applyThemeVariables()
    broadcastPreference()
    await persistThemePreference()
  }

  const setStyleFamily = async (key: StyleFamilyKey) => {
    if (!styleFamilies[key]) return
    currentStyleFamily.value = key
    updateDom()
    broadcastPreference()
    await persistThemePreference()
  }

  const applyThemeVariables = () => {
    const theme = themes[currentThemeKey.value]
    const styleFamily = styleFamilies[currentStyleFamily.value]
    const root = document.documentElement
    const primary = theme.preview.primary
    const secondary = theme.preview.secondary
    const base = theme.preview.base

    root.style.setProperty('--pe-accent-h', String(theme.accentHue))
    root.style.setProperty('--pe-accent-s', theme.accentSaturation)
    root.style.setProperty('--pe-accent-l', theme.accentLightness)
    root.style.setProperty('--pe-glass-blur', theme.glassBlur)
    root.style.setProperty('--pe-radius-lg', theme.radius)
    root.style.setProperty('--pe-theme-primary', primary)
    root.style.setProperty('--pe-theme-secondary', secondary)
    root.style.setProperty('--pe-theme-base', base)
    root.dataset.adminStyle = currentStyleFamily.value
    root.style.setProperty('--pe-style-density', styleFamily.density)
    root.style.setProperty('--pe-style-surface', styleFamily.surface)
    root.style.setProperty('--pe-style-hero', styleFamily.hero)

    if (isDark.value) {
      root.style.setProperty('--pe-admin-text-primary', 'rgba(248, 250, 252, 0.96)')
      root.style.setProperty('--pe-admin-text-secondary', 'rgba(226, 232, 240, 0.88)')
      root.style.setProperty('--pe-admin-text-muted', 'rgba(148, 163, 184, 0.92)')
      root.style.setProperty('--pe-admin-text-faint', 'rgba(148, 163, 184, 0.72)')
      root.style.setProperty('--pe-surface-bg-rgb', '15, 23, 42')
      root.style.setProperty('--pe-surface-bg', 'color-mix(in srgb, var(--pe-theme-base) 82%, rgba(15, 23, 42, 0.92))')
      root.style.setProperty('--pe-surface-border', 'color-mix(in srgb, var(--pe-theme-secondary) 26%, rgba(148, 163, 184, 0.42))')
      root.style.setProperty('--pe-surface-shadow', '0 24px 60px rgba(0, 0, 0, 0.62)')
      root.style.setProperty(
        '--pe-admin-bg',
        'radial-gradient(circle at 12% 14%, color-mix(in srgb, var(--pe-theme-primary) 24%, transparent), transparent 28%), radial-gradient(circle at 88% 12%, color-mix(in srgb, var(--pe-theme-secondary) 20%, transparent), transparent 34%), linear-gradient(145deg, color-mix(in srgb, var(--pe-theme-base) 88%, #020617), #020617)'
      )
      root.style.setProperty(
        '--pe-admin-hero-bg',
        'radial-gradient(circle at 8% 12%, color-mix(in srgb, var(--pe-theme-primary) 30%, transparent), transparent 38%), radial-gradient(circle at 92% 14%, color-mix(in srgb, var(--pe-theme-secondary) 24%, transparent), transparent 42%), linear-gradient(135deg, color-mix(in srgb, var(--pe-theme-base) 92%, #020617), color-mix(in srgb, var(--pe-theme-base) 72%, #111827))'
      )
    } else {
      root.style.setProperty('--pe-admin-text-primary', 'rgba(15, 23, 42, 0.96)')
      root.style.setProperty('--pe-admin-text-secondary', 'rgba(30, 41, 59, 0.88)')
      root.style.setProperty('--pe-admin-text-muted', 'rgba(51, 65, 85, 0.88)')
      root.style.setProperty('--pe-admin-text-faint', 'rgba(71, 85, 105, 0.74)')
      root.style.setProperty('--pe-surface-bg-rgb', '248, 250, 252')
      root.style.setProperty('--pe-surface-bg', 'color-mix(in srgb, white 74%, var(--pe-theme-primary) 10%)')
      root.style.setProperty('--pe-surface-border', 'color-mix(in srgb, var(--pe-theme-secondary) 26%, rgba(148, 163, 184, 0.35))')
      root.style.setProperty('--pe-surface-shadow', '0 20px 48px rgba(15, 23, 42, 0.16)')
      root.style.setProperty(
        '--pe-admin-bg',
        'radial-gradient(circle at 10% 10%, color-mix(in srgb, var(--pe-theme-primary) 18%, white), transparent 26%), radial-gradient(circle at 90% 8%, color-mix(in srgb, var(--pe-theme-secondary) 16%, white), transparent 30%), linear-gradient(145deg, color-mix(in srgb, var(--pe-theme-base) 14%, white), white)'
      )
      root.style.setProperty(
        '--pe-admin-hero-bg',
        'radial-gradient(circle at 10% 12%, color-mix(in srgb, var(--pe-theme-primary) 22%, white), transparent 34%), radial-gradient(circle at 92% 10%, color-mix(in srgb, var(--pe-theme-secondary) 20%, white), transparent 38%), linear-gradient(135deg, color-mix(in srgb, var(--pe-theme-base) 12%, white), color-mix(in srgb, var(--pe-theme-base) 4%, white))'
      )
    }

    root.style.setProperty('--pe-admin-font-body', '"Segoe UI", "PingFang SC", "Hiragino Sans GB", "Microsoft YaHei", sans-serif')
    root.style.setProperty('--pe-admin-font-heading', '"Segoe UI", "PingFang SC", "Hiragino Sans GB", "Microsoft YaHei", sans-serif')
    root.style.setProperty('--pe-admin-font-mono', '"SFMono-Regular", "JetBrains Mono", "Consolas", monospace')

    if (currentStyleFamily.value === 'material') {
      root.style.setProperty('--pe-radius-lg', '18px')
      root.style.setProperty('--pe-surface-shadow', isDark.value ? '0 18px 46px rgba(15, 23, 42, 0.22)' : '0 14px 34px rgba(15, 23, 42, 0.1)')
      root.style.setProperty('--pe-admin-font-heading', '"Inter", "Segoe UI", "PingFang SC", sans-serif')
    } else if (currentStyleFamily.value === 'glass') {
      root.style.setProperty('--pe-glass-blur', isDark.value ? '26px' : '22px')
      root.style.setProperty('--pe-surface-shadow', isDark.value ? '0 26px 72px rgba(15, 23, 42, 0.32)' : '0 18px 42px rgba(15, 23, 42, 0.12)')
      root.style.setProperty('--pe-radius-lg', '24px')
      root.style.setProperty('--pe-admin-font-heading', '"Avenir Next", "Segoe UI", "PingFang SC", sans-serif')
    } else if (currentStyleFamily.value === 'classic') {
      root.style.setProperty('--pe-glass-blur', '10px')
      root.style.setProperty('--pe-surface-shadow', isDark.value ? '0 16px 32px rgba(15, 23, 42, 0.22)' : '0 8px 20px rgba(15, 23, 42, 0.08)')
      root.style.setProperty('--pe-radius-lg', '14px')
      root.style.setProperty('--pe-admin-font-body', '"Segoe UI", "PingFang SC", "Microsoft YaHei", sans-serif')
      root.style.setProperty('--pe-admin-font-heading', '"Cambria", "Georgia", "Songti SC", serif')
    } else if (currentStyleFamily.value === 'gallery') {
      root.style.setProperty('--pe-glass-blur', isDark.value ? '18px' : '16px')
      root.style.setProperty('--pe-surface-shadow', isDark.value ? '0 28px 80px rgba(15, 23, 42, 0.34)' : '0 20px 48px rgba(15, 23, 42, 0.12)')
      root.style.setProperty('--pe-radius-lg', '28px')
      root.style.setProperty('--pe-admin-font-heading', '"Didot", "Bodoni MT", "STSong", serif')
    } else if (currentStyleFamily.value === 'compact') {
      root.style.setProperty('--pe-glass-blur', '14px')
      root.style.setProperty('--pe-surface-shadow', isDark.value ? '0 14px 28px rgba(15, 23, 42, 0.2)' : '0 6px 16px rgba(15, 23, 42, 0.06)')
      root.style.setProperty('--pe-radius-lg', '12px')
      root.style.setProperty('--pe-admin-font-heading', '"DIN Alternate", "Bahnschrift", "Segoe UI", sans-serif')
    } else if (currentStyleFamily.value === 'brutalist') {
      root.style.setProperty('--pe-glass-blur', '0px')
      root.style.setProperty('--pe-radius-lg', '10px')
      root.style.setProperty('--pe-surface-shadow', isDark.value ? '12px 12px 0 rgba(0, 0, 0, 0.4)' : '10px 10px 0 rgba(15, 23, 42, 0.16)')
      root.style.setProperty('--pe-admin-font-heading', '"Arial Black", "PingFang SC", sans-serif')
    } else if (currentStyleFamily.value === 'paper') {
      root.style.setProperty('--pe-glass-blur', '0px')
      root.style.setProperty('--pe-radius-lg', '20px')
      root.style.setProperty('--pe-surface-shadow', isDark.value ? '0 18px 36px rgba(15, 23, 42, 0.2)' : '0 14px 30px rgba(120, 98, 72, 0.12)')
      root.style.setProperty('--pe-admin-font-body', '"Georgia", "Noto Serif SC", "Source Han Serif SC", serif')
      root.style.setProperty('--pe-admin-font-heading', '"Georgia", "Noto Serif SC", "Source Han Serif SC", serif')
    } else if (currentStyleFamily.value === 'neon') {
      root.style.setProperty('--pe-glass-blur', isDark.value ? '16px' : '12px')
      root.style.setProperty('--pe-radius-lg', '22px')
      root.style.setProperty('--pe-surface-shadow', isDark.value ? '0 0 0 1px rgba(255,255,255,0.05), 0 0 36px color-mix(in srgb, var(--pe-theme-primary) 26%, transparent)' : '0 14px 34px rgba(15, 23, 42, 0.12)')
      root.style.setProperty('--pe-admin-font-heading', '"Trebuchet MS", "Segoe UI", "PingFang SC", sans-serif')
    } else if (currentStyleFamily.value === 'zen') {
      root.style.setProperty('--pe-glass-blur', '8px')
      root.style.setProperty('--pe-radius-lg', '28px')
      root.style.setProperty('--pe-surface-shadow', isDark.value ? '0 12px 28px rgba(15, 23, 42, 0.16)' : '0 10px 22px rgba(15, 23, 42, 0.08)')
      root.style.setProperty('--pe-admin-font-body', '"PingFang SC", "Hiragino Sans GB", "Noto Sans SC", sans-serif')
      root.style.setProperty('--pe-admin-font-heading', '"STKaiti", "Kaiti SC", "Songti SC", serif')
    } else if (currentStyleFamily.value === 'terminal') {
      root.style.setProperty('--pe-glass-blur', '0px')
      root.style.setProperty('--pe-radius-lg', '8px')
      root.style.setProperty('--pe-surface-shadow', isDark.value ? '0 0 0 1px rgba(34, 197, 94, 0.18), 0 14px 28px rgba(0, 0, 0, 0.42)' : '0 0 0 1px rgba(5, 150, 105, 0.2), 0 10px 20px rgba(15, 23, 42, 0.12)')
      root.style.setProperty('--pe-admin-font-body', '"SFMono-Regular", "JetBrains Mono", "Cascadia Code", monospace')
      root.style.setProperty('--pe-admin-font-heading', '"SFMono-Regular", "JetBrains Mono", "Cascadia Code", monospace')
    }
  }

  const updateDom = (options?: { persistLocal?: boolean }) => {
    const persistLocal = options?.persistLocal !== false
    if (isDark.value) {
      document.documentElement.classList.add('dark')
      if (persistLocal) localStorage.setItem(COLOR_MODE_STORAGE, 'dark')
    } else {
      document.documentElement.classList.remove('dark')
      if (persistLocal) localStorage.setItem(COLOR_MODE_STORAGE, 'light')
    }
    applyThemeVariables()
  }

  // 初始化主题
  const initialCached = readLastPreference() || readCachedPreference(null)
  if (initialCached) {
    applyPreference(initialCached)
  } else {
    updateDom()
  }

  // 监听变化
  watch(isDark, () => {
    updateDom()
  })

  watch(
    () => [authStore.userId, authStore.token] as const,
    async ([userId, token]) => {
      if (!token || !userId) {
        const cached = readCachedPreference(null) || readLastPreference()
        if (cached) {
          applyPreference(cached)
        }
        hydratedUserId.value = null
        ready.value = true
        return
      }
      if (hydratedUserId.value === userId) {
        ready.value = true
        return
      }
      const cached = readCachedPreference(userId)
      if (cached) {
        applyPreference(cached)
      } else {
        const lastCached = readLastPreference()
        if (lastCached) {
          applyPreference(lastCached)
        }
      }
      await syncThemePreference()
    },
    { immediate: true }
  )

  if (canUseWindow && !themeSyncInitialized) {
    themeSyncInitialized = true
    window.addEventListener('storage', (event) => {
      if (event.key !== THEME_SYNC_KEY || !event.newValue) return
      try {
        const payload = JSON.parse(event.newValue) as { themeKey?: string; colorMode?: string; styleFamily?: string; sourceId?: string }
        if (payload.sourceId === themeSyncSourceId) return
        applyPreference(payload, { writeStorage: false })
        pushSyncNotice('theme')
      } catch {
        // ignore invalid sync payload
      }
    })
    themeSyncChannel?.addEventListener('message', (event) => {
      const payload = event.data as { themeKey?: string; colorMode?: string; styleFamily?: string; sourceId?: string } | undefined
      if (!payload || payload.sourceId === themeSyncSourceId) return
      applyPreference(payload, { writeStorage: false })
      pushSyncNotice('theme')
    })
  }

  return {
    isDark,
    currentThemeKey,
    currentStyleFamily,
    themes,
    styleFamilies,
    loading,
    saving,
    ready,
    lastSavedAt,
    errorMessage,
    syncThemePreference,
    toggleTheme,
    setColorMode,
    setThemeKey,
    setStyleFamily
  }
})
