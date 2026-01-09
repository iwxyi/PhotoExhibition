import { ref } from 'vue'

export type CoverSize = 'xs' | 'sm' | 'md' | 'lg'
export type PreviewSize = 'xs' | 'sm' | 'md' | 'lg'

const COVER_KEY = 'pe-cover-size'
const PREVIEW_KEY = 'pe-preview-size'
const THUMB_KEY = 'pe-thumb-height'
const PARALLAX_KEY = 'pe-parallax-enabled'
const ATMOSPHERE_KEY = 'pe-atmosphere-enabled'
const VIEW_ORIGINAL_KEY = 'pe-view-original-enabled'

const previewSizeMap: Record<PreviewSize, number> = {
  xs: 60,
  sm: 80,
  md: 120,
  lg: 160
}

const coverSize = ref<CoverSize>((localStorage.getItem(COVER_KEY) as CoverSize) || 'md')
const previewSize = ref<PreviewSize>((localStorage.getItem(PREVIEW_KEY) as PreviewSize) || 'md')
const parallaxEnabled = ref<boolean>(localStorage.getItem(PARALLAX_KEY) !== 'false') // 默认启用
const atmosphereEnabled = ref<boolean>(localStorage.getItem(ATMOSPHERE_KEY) !== 'false') // 默认启用
const viewOriginalEnabled = ref<boolean>(localStorage.getItem(VIEW_ORIGINAL_KEY) === 'true') // 默认关闭

export function useUiSettings() {
  const setCoverSize = (val: CoverSize) => {
    coverSize.value = val
    localStorage.setItem(COVER_KEY, val)
  }

  const setPreviewSize = (val: PreviewSize) => {
    previewSize.value = val
    localStorage.setItem(PREVIEW_KEY, val)
    localStorage.setItem(THUMB_KEY, String(previewSizeMap[val]))
  }

  const setParallaxEnabled = (val: boolean) => {
    parallaxEnabled.value = val
    localStorage.setItem(PARALLAX_KEY, String(val))
  }

  const setAtmosphereEnabled = (val: boolean) => {
    atmosphereEnabled.value = val
    localStorage.setItem(ATMOSPHERE_KEY, String(val))
  }

  const setViewOriginalEnabled = (val: boolean) => {
    viewOriginalEnabled.value = val
    localStorage.setItem(VIEW_ORIGINAL_KEY, String(val))
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

