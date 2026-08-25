import { ref } from 'vue'
import { api } from '@/api'
import { pushSyncNotice } from '@/composables/useSyncNotice'

export type CoverSize = 'sm' | 'md' | 'lg'
export type PreviewSize = 'sm' | 'md' | 'lg'

const COVER_KEY = 'pe-cover-size'
const PREVIEW_KEY = 'pe-preview-size'
const THUMB_KEY = 'pe-thumb-height'
const PARALLAX_KEY = 'pe-parallax-enabled'
const ATMOSPHERE_KEY = 'pe-atmosphere-enabled'
const VIEW_ORIGINAL_KEY = 'pe-view-original-enabled'
const UI_SETTINGS_SYNC_KEY = 'pe-ui-settings-sync'
const UI_SETTINGS_SYNC_CHANNEL = 'pe-ui-settings-sync-channel'

const canUseWindow = typeof window !== 'undefined'
const uiSettingsSyncSourceId = canUseWindow ? `ui-${Math.random().toString(36).slice(2, 10)}` : 'ui-server'
const uiSettingsSyncChannel = canUseWindow && 'BroadcastChannel' in window
  ? new BroadcastChannel(UI_SETTINGS_SYNC_CHANNEL)
  : null
let uiSettingsSyncInitialized = false

const previewSizeMap: Record<PreviewSize, number> = {
  sm: 80,
  md: 120,
  lg: 160
}

const coverSize = ref<CoverSize>((localStorage.getItem(COVER_KEY) as CoverSize) || 'md')
const previewSize = ref<PreviewSize>((localStorage.getItem(PREVIEW_KEY) as PreviewSize) || 'md')
const parallaxEnabled = ref<boolean>(localStorage.getItem(PARALLAX_KEY) !== 'false') // 默认启用
const atmosphereEnabled = ref<boolean>(localStorage.getItem(ATMOSPHERE_KEY) === 'true') // 默认关闭
const viewOriginalEnabled = ref<boolean>(localStorage.getItem(VIEW_ORIGINAL_KEY) === 'true') // 默认关闭

type UiSettingsPayload = {
  coverSize: CoverSize
  previewSize: PreviewSize
  parallaxEnabled: boolean
  atmosphereEnabled: boolean
  viewOriginalEnabled: boolean
  sourceId: string
  timestamp: number
}

const buildUiSettingsPayload = (): UiSettingsPayload => ({
  coverSize: coverSize.value,
  previewSize: previewSize.value,
  parallaxEnabled: parallaxEnabled.value,
  atmosphereEnabled: atmosphereEnabled.value,
  viewOriginalEnabled: viewOriginalEnabled.value,
  sourceId: uiSettingsSyncSourceId,
  timestamp: Date.now()
})

const applyUiSettingsPayload = (payload?: Partial<UiSettingsPayload> | null, options?: { persistLocal?: boolean }) => {
  if (!payload) return
  const persistLocal = options?.persistLocal !== false
  const nextCoverSize = payload.coverSize === 'sm' || payload.coverSize === 'lg' ? payload.coverSize : 'md'
  const nextPreviewSize = payload.previewSize === 'sm' || payload.previewSize === 'lg' ? payload.previewSize : 'md'
  coverSize.value = nextCoverSize
  previewSize.value = nextPreviewSize
  parallaxEnabled.value = payload.parallaxEnabled !== false
  atmosphereEnabled.value = payload.atmosphereEnabled === true
  viewOriginalEnabled.value = payload.viewOriginalEnabled === true
  if (persistLocal) {
    localStorage.setItem(COVER_KEY, coverSize.value)
    localStorage.setItem(PREVIEW_KEY, previewSize.value)
    localStorage.setItem(THUMB_KEY, String(previewSizeMap[previewSize.value]))
    localStorage.setItem(PARALLAX_KEY, String(parallaxEnabled.value))
    localStorage.setItem(ATMOSPHERE_KEY, String(atmosphereEnabled.value))
    localStorage.setItem(VIEW_ORIGINAL_KEY, String(viewOriginalEnabled.value))
  }
}

const broadcastUiSettings = () => {
  if (!canUseWindow) return
  const payload = buildUiSettingsPayload()
  localStorage.setItem(UI_SETTINGS_SYNC_KEY, JSON.stringify(payload))
  uiSettingsSyncChannel?.postMessage(payload)
}

// 从后端加载全局设置
export const loadGlobalSettings = async () => {
  try {
    const response = await api.get('/admin/config/atmosphere-enabled')
    if (response.data.atmosphereEnabled !== undefined) {
      atmosphereEnabled.value = response.data.atmosphereEnabled
      localStorage.setItem(ATMOSPHERE_KEY, String(response.data.atmosphereEnabled))
    }
  } catch (e) {
    console.warn('加载全局氛围设置失败，使用本地默认值:', e)
  }
}

export function useUiSettings() {
  if (canUseWindow && !uiSettingsSyncInitialized) {
    uiSettingsSyncInitialized = true
    window.addEventListener('storage', (event) => {
      if (event.key !== UI_SETTINGS_SYNC_KEY || !event.newValue) return
      try {
        const payload = JSON.parse(event.newValue) as Partial<UiSettingsPayload>
        if (payload.sourceId === uiSettingsSyncSourceId) return
        applyUiSettingsPayload(payload, { persistLocal: false })
        pushSyncNotice('ui-settings')
      } catch {
        // ignore invalid sync payload
      }
    })
    uiSettingsSyncChannel?.addEventListener('message', (event) => {
      const payload = event.data as Partial<UiSettingsPayload> | undefined
      if (!payload || payload.sourceId === uiSettingsSyncSourceId) return
      applyUiSettingsPayload(payload, { persistLocal: false })
      pushSyncNotice('ui-settings')
    })
  }

  const setCoverSize = (val: CoverSize) => {
    coverSize.value = val
    localStorage.setItem(COVER_KEY, val)
    broadcastUiSettings()
  }

  const setPreviewSize = (val: PreviewSize) => {
    previewSize.value = val
    localStorage.setItem(PREVIEW_KEY, val)
    localStorage.setItem(THUMB_KEY, String(previewSizeMap[val]))
    broadcastUiSettings()
  }

  const setParallaxEnabled = (val: boolean) => {
    parallaxEnabled.value = val
    localStorage.setItem(PARALLAX_KEY, String(val))
    broadcastUiSettings()
  }

  const setAtmosphereEnabled = async (val: boolean) => {
    atmosphereEnabled.value = val
    localStorage.setItem(ATMOSPHERE_KEY, String(val))
    broadcastUiSettings()
    try {
      await api.put('/admin/config/atmosphere-enabled', {
        atmosphereEnabled: val
      })
    } catch (e) {
      console.warn('保存全局氛围设置失败:', e)
    }
  }

  const setViewOriginalEnabled = (val: boolean) => {
    viewOriginalEnabled.value = val
    localStorage.setItem(VIEW_ORIGINAL_KEY, String(val))
    broadcastUiSettings()
  }

  return {
    coverSize,
    previewSize,
    parallaxEnabled,
    atmosphereEnabled,
    viewOriginalEnabled,
    setCoverSize,
    setPreviewSize,
    setParallaxEnabled,
    setAtmosphereEnabled,
    setViewOriginalEnabled,
    previewSizeMap
  }
}
