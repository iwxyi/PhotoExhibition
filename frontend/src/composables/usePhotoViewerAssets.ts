import { ref, shallowRef, triggerRef } from 'vue'

export type PhotoAssetQuality = 'none' | 'thumbnail' | 'preview' | 'original'

export type PhotoAssetSlot = {
  photoId: number
  thumbnailUrl: string
  previewUrl: string
  originalUrl: string
  displayUrl: string
  quality: PhotoAssetQuality
  thumbnailState: 'idle' | 'loading' | 'ready' | 'error'
  previewState: 'idle' | 'loading' | 'ready' | 'error'
  originalState: 'idle' | 'loading' | 'ready' | 'error'
  requestVersion: number
}

export type PhotoAssetInput = {
  id: number
  thumbnailUrl: string
  previewUrl?: string
  originalUrl: string
}

const qualityRank: Record<PhotoAssetQuality, number> = {
  none: 0,
  thumbnail: 1,
  preview: 2,
  original: 3
}

const loadImage = (url: string) => new Promise<boolean>((resolve) => {
  if (!url) {
    resolve(false)
    return
  }
  const image = new Image()
  let settled = false
  const finish = (result: boolean) => {
    if (settled) return
    settled = true
    resolve(result)
  }
  image.onload = () => {
    const decode = image.decode?.()
    if (decode) decode.then(() => finish(true)).catch(() => finish(true))
    else finish(true)
  }
  image.onerror = () => finish(false)
  image.src = url
  if (image.complete && image.naturalWidth > 0) finish(true)
})

export function usePhotoViewerAssets(resolveAsset: (photo: PhotoAssetInput) => PhotoAssetInput) {
  // Map values are mutable slot objects. A deep ref proxies values on read,
  // making raw/proxy identity checks fail and causing valid async loads to be
  // discarded. Keep slot identity stable; consumers observe fields directly.
  const slots = shallowRef(new Map<number, PhotoAssetSlot>())
  const generation = ref(0)
  const preloadEpoch = ref(0)
  const pending = new Map<string, Promise<boolean>>()

  const ensureSlot = (photo: PhotoAssetInput) => {
    const resolved = resolveAsset(photo)
    const existing = slots.value.get(photo.id)
    if (existing && existing.thumbnailUrl === resolved.thumbnailUrl) {
      // A display-mode toggle (large/original) changes the high-quality URL
      // but must not throw away an already decoded drawable frame.
      const nextPreview = resolved.previewUrl || resolved.originalUrl
      let changed = false
      if (existing.previewUrl !== nextPreview) {
        existing.previewUrl = nextPreview
        existing.previewState = 'idle'
        changed = true
      }
      if (existing.originalUrl !== resolved.originalUrl) {
        existing.originalUrl = resolved.originalUrl
        existing.originalState = 'idle'
        changed = true
      }
      if (changed) triggerRef(slots)
      return existing
    }
    const slot: PhotoAssetSlot = {
      photoId: photo.id,
      thumbnailUrl: resolved.thumbnailUrl,
      previewUrl: resolved.previewUrl || resolved.originalUrl,
      originalUrl: resolved.originalUrl,
      displayUrl: resolved.thumbnailUrl || resolved.previewUrl || resolved.originalUrl,
      quality: resolved.thumbnailUrl ? 'thumbnail' : resolved.previewUrl ? 'preview' : 'original',
      thumbnailState: resolved.thumbnailUrl ? 'idle' : 'error',
      previewState: resolved.previewUrl ? 'idle' : 'error',
      originalState: resolved.originalUrl ? 'idle' : 'error',
      requestVersion: generation.value
    }
    slots.value.set(photo.id, slot)
    return slot
  }

  const promote = (slot: PhotoAssetSlot, quality: PhotoAssetQuality, url: string) => {
    if (!url || qualityRank[quality] < qualityRank[slot.quality]) return
    slot.displayUrl = url
    slot.quality = quality
  }

  const loadQuality = async (photo: PhotoAssetInput, quality: 'thumbnail' | 'preview' | 'original') => {
    const slot = ensureSlot(photo)
    const stateKey = `${quality}State` as 'thumbnailState' | 'previewState' | 'originalState'
    if (slot[stateKey] === 'ready') return true
    const pendingKey = `${photo.id}:${quality}`
    if (slot[stateKey] === 'loading') return pending.get(pendingKey) || false
    const url = quality === 'thumbnail' ? slot.thumbnailUrl : quality === 'preview' ? slot.previewUrl : slot.originalUrl
    if (!url) {
      slot[stateKey] = 'error'
      return false
    }
    slot[stateKey] = 'loading'
    const requestVersion = ++generation.value
    slot.requestVersion = requestVersion
    const request = loadImage(url)
    pending.set(pendingKey, request)
    const ready = await request
    if (pending.get(pendingKey) === request) pending.delete(pendingKey)
    // Different qualities are intentionally loaded concurrently. A global
    // version per slot would make a later original request invalidate an
    // earlier thumbnail request and leave the slot stuck in `loading`.
    const currentUrl = quality === 'thumbnail' ? slot.thumbnailUrl : quality === 'preview' ? slot.previewUrl : slot.originalUrl
    if (slots.value.get(photo.id) !== slot || currentUrl !== url) return false
    slot[stateKey] = ready ? 'ready' : 'error'
    if (ready) {
      promote(slot, quality, url)
      triggerRef(slots)
    }
    return ready
  }

  const preloadThumbnails = (photos: PhotoAssetInput[]) => {
    // Unbounded eager requests overwhelm mobile connection pools and image
    // decoders, which can leave random-gallery thumbnails stuck indefinitely.
    // Keep a small rolling window instead of starting one request per photo.
    const queue = photos.slice()
    const epoch = ++preloadEpoch.value
    const concurrency = typeof navigator !== 'undefined' && navigator.hardwareConcurrency <= 4 ? 2 : 4
    const worker = async () => {
      while (queue.length) {
        if (epoch !== preloadEpoch.value) return
        const photo = queue.shift()
        if (!photo) return
        await loadQuality(photo, 'thumbnail')
      }
    }
    for (let i = 0; i < Math.min(concurrency, queue.length); i += 1) void worker()
  }

  const prepareForDisplay = async (photo: PhotoAssetInput) => {
    const slot = ensureSlot(photo)
    await loadQuality(photo, 'thumbnail')
    if (slot.previewUrl && slot.previewUrl !== slot.thumbnailUrl) void loadQuality(photo, 'preview')
    if (slot.originalUrl) void loadQuality(photo, 'original')
    return slot
  }

  const clear = () => {
    generation.value += 1
    preloadEpoch.value += 1
    pending.clear()
    slots.value.clear()
  }

  return { slots, ensureSlot, loadQuality, preloadThumbnails, prepareForDisplay, clear }
}
