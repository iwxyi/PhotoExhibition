import { ref } from 'vue'

export type CoverSize = 'sm' | 'md' | 'lg'
export type PreviewSize = 'sm' | 'md' | 'lg'

const COVER_KEY = 'pe-cover-size'
const PREVIEW_KEY = 'pe-preview-size'
const THUMB_KEY = 'pe-thumb-height'
const PARALLAX_KEY = 'pe-parallax-enabled'

const previewSizeMap: Record<PreviewSize, number> = {
  sm: 80,
  md: 120,
  lg: 160
}

const coverSize = ref<CoverSize>((localStorage.getItem(COVER_KEY) as CoverSize) || 'md')
const previewSize = ref<PreviewSize>((localStorage.getItem(PREVIEW_KEY) as PreviewSize) || 'md')
const parallaxEnabled = ref<boolean>(localStorage.getItem(PARALLAX_KEY) !== 'false') // 默认启用

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

  return {
    coverSize,
    previewSize,
    parallaxEnabled,
    setCoverSize,
    setPreviewSize,
    setParallaxEnabled,
    previewSizeMap
  }
}

