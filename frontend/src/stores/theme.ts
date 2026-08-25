import { defineStore } from 'pinia'
import { ref, watch } from 'vue'
import { api } from '@/api'
import { useAuthStore } from '@/stores/auth'

type AdminColorMode = 'light' | 'dark' | 'system'

const COLOR_MODE_STORAGE = 'theme'
const ADMIN_COLOR_MODE_STORAGE = 'pe-admin-color-mode'
const canUseWindow = typeof window !== 'undefined'

const normalizeAdminColorMode = (value: unknown): AdminColorMode => {
  return value === 'light' || value === 'dark' || value === 'system' ? value : 'dark'
}

export const useThemeStore = defineStore('theme', () => {
  const authStore = useAuthStore()
  const storedMode = localStorage.getItem(COLOR_MODE_STORAGE)
  const prefersDark = canUseWindow && window.matchMedia('(prefers-color-scheme: dark)').matches

  // Public pages keep their existing light/dark preference independently of admin appearance.
  const isDark = ref(storedMode === 'dark' || (!storedMode && prefersDark))
  const adminColorMode = ref<AdminColorMode>(
    normalizeAdminColorMode(localStorage.getItem(ADMIN_COLOR_MODE_STORAGE) || 'dark')
  )
  const loading = ref(false)
  const saving = ref(false)
  const errorMessage = ref('')

  const applyPublicColorMode = () => {
    document.documentElement.classList.toggle('dark', isDark.value)
    document.documentElement.style.setProperty('--pe-theme-primary', '#2563eb')
    document.documentElement.style.setProperty('--pe-theme-secondary', '#94a3b8')
    document.documentElement.style.setProperty('--pe-theme-base', '#0f172a')
    document.documentElement.style.setProperty('--pe-accent-h', '217')
    document.documentElement.style.setProperty('--pe-accent-s', '91%')
    document.documentElement.style.setProperty('--pe-accent-l', '60%')
    document.documentElement.style.setProperty('--pe-glass-blur', '14px')
    document.documentElement.style.setProperty('--pe-radius-lg', '18px')
  }

  const applyAdminColorMode = () => {
    const resolved = adminColorMode.value === 'system'
      ? (window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light')
      : adminColorMode.value
    document.documentElement.dataset.adminColorMode = resolved
    localStorage.setItem(ADMIN_COLOR_MODE_STORAGE, adminColorMode.value)
  }

  const setColorMode = async (dark: boolean) => {
    isDark.value = dark
    localStorage.setItem(COLOR_MODE_STORAGE, dark ? 'dark' : 'light')
    applyPublicColorMode()
  }

  const toggleTheme = async () => setColorMode(!isDark.value)

  const syncAdminColorMode = async () => {
    if (!authStore.token || !authStore.userId) return
    loading.value = true
    errorMessage.value = ''
    try {
      const { data } = await api.get('/admin/config/admin-theme')
      adminColorMode.value = normalizeAdminColorMode(data?.colorMode)
      applyAdminColorMode()
    } catch (error: any) {
      errorMessage.value = error?.response?.data?.error || error?.message || '加载后台颜色模式失败'
    } finally {
      loading.value = false
    }
  }

  const setAdminColorMode = async (mode: AdminColorMode) => {
    adminColorMode.value = mode
    applyAdminColorMode()
    if (!authStore.token || !authStore.userId) return
    saving.value = true
    errorMessage.value = ''
    try {
      await api.put('/admin/config/admin-theme', { colorMode: mode })
    } catch (error: any) {
      errorMessage.value = error?.response?.data?.error || error?.message || '保存后台颜色模式失败'
    } finally {
      saving.value = false
    }
  }

  applyPublicColorMode()
  applyAdminColorMode()

  watch(isDark, applyPublicColorMode)
  watch(
    () => [authStore.userId, authStore.token] as const,
    ([userId, token]) => {
      if (userId && token) void syncAdminColorMode()
    },
    { immediate: true }
  )

  if (canUseWindow) {
    window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', () => {
      if (adminColorMode.value === 'system') applyAdminColorMode()
    })
  }

  return {
    isDark,
    adminColorMode,
    loading,
    saving,
    errorMessage,
    toggleTheme,
    setColorMode,
    setAdminColorMode,
    syncAdminColorMode
  }
})
