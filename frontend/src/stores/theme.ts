import { defineStore } from 'pinia'
import { ref, watch } from 'vue'

type ThemeKey = 'default' | 'ocean' | 'forest' | 'sunset' | 'mono'

interface ThemeDefinition {
  name: string
  accentHue: number
  accentSaturation: string
  accentLightness: string
  glassBlur: string
  radius: string
}

const THEME_KEY_STORAGE = 'pe-theme-key'
const COLOR_MODE_STORAGE = 'theme'

const themes: Record<ThemeKey, ThemeDefinition> = {
  default: {
    name: '默认',
    accentHue: 222,
    accentSaturation: '84%',
    accentLightness: '56%',
    glassBlur: '18px',
    radius: '16px'
  },
  ocean: {
    name: '海蓝',
    accentHue: 199,
    accentSaturation: '89%',
    accentLightness: '55%',
    glassBlur: '22px',
    radius: '18px'
  },
  forest: {
    name: '森林',
    accentHue: 142,
    accentSaturation: '72%',
    accentLightness: '42%',
    glassBlur: '16px',
    radius: '14px'
  },
  sunset: {
    name: '日落',
    accentHue: 18,
    accentSaturation: '92%',
    accentLightness: '60%',
    glassBlur: '20px',
    radius: '18px'
  },
  mono: {
    name: '黑白',
    accentHue: 0,
    accentSaturation: '0%',
    accentLightness: '90%',
    glassBlur: '12px',
    radius: '12px'
  }
}

export const useThemeStore = defineStore('theme', () => {
  const storedMode = localStorage.getItem(COLOR_MODE_STORAGE)
  const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches

  const isDark = ref(storedMode === 'dark' || (!storedMode && prefersDark))
  const currentThemeKey = ref<ThemeKey>(
    (localStorage.getItem(THEME_KEY_STORAGE) as ThemeKey) || 'default'
  )

  const setColorMode = (dark: boolean) => {
    isDark.value = dark
    updateDom()
  }

  const toggleTheme = () => {
    setColorMode(!isDark.value)
  }

  const setThemeKey = (key: ThemeKey) => {
    if (!themes[key]) return
    currentThemeKey.value = key
    localStorage.setItem(THEME_KEY_STORAGE, key)
    applyThemeVariables()
  }

  const applyThemeVariables = () => {
    const theme = themes[currentThemeKey.value]
    const root = document.documentElement

    root.style.setProperty('--pe-accent-h', String(theme.accentHue))
    root.style.setProperty('--pe-accent-s', theme.accentSaturation)
    root.style.setProperty('--pe-accent-l', theme.accentLightness)
    root.style.setProperty('--pe-glass-blur', theme.glassBlur)
    root.style.setProperty('--pe-radius-lg', theme.radius)
  }

  const updateDom = () => {
    if (isDark.value) {
      document.documentElement.classList.add('dark')
      localStorage.setItem(COLOR_MODE_STORAGE, 'dark')
    } else {
      document.documentElement.classList.remove('dark')
      localStorage.setItem(COLOR_MODE_STORAGE, 'light')
    }
    applyThemeVariables()
  }

  // 初始化主题
  updateDom()

  // 监听变化
  watch(isDark, () => {
    updateDom()
  })

  return {
    isDark,
    currentThemeKey,
    themes,
    toggleTheme,
    setThemeKey
  }
})

