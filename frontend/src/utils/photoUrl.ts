type PhotoLike = {
  id?: number | null
  originalPath?: string | null
  thumbnailPath?: string | null
  webpPath?: string | null
  smallThumbPath?: string | null
  mediumThumbPath?: string | null
  largeThumbPath?: string | null
  backgroundRemovedPath?: string | null
}

type Variant = 'auto' | 'small' | 'medium' | 'large' | 'thumbnail' | 'webp' | 'original' | 'backgroundRemoved'

const normalizeStaticPath = (value?: string | null) => {
  if (!value) return null
  if (value.startsWith('http://') || value.startsWith('https://')) return value
  if (value.startsWith('storage://')) return null
  return `/api/files${value}`
}

export const buildPhotoAssetUrl = (photo?: PhotoLike | null, variant: Variant = 'auto') => {
  if (!photo) return null
  if (photo.id) {
    return `/api/photos/${photo.id}/asset?variant=${variant}`
  }

  if (variant === 'original') {
    return normalizeStaticPath(photo.originalPath)
  }
  if (variant === 'backgroundRemoved') {
    return normalizeStaticPath(photo.backgroundRemovedPath) || normalizeStaticPath(photo.originalPath)
  }
  if (variant === 'webp') {
    return normalizeStaticPath(photo.webpPath) || normalizeStaticPath(photo.originalPath)
  }
  if (variant === 'large') {
    return normalizeStaticPath(photo.largeThumbPath) || normalizeStaticPath(photo.webpPath) || normalizeStaticPath(photo.originalPath)
  }
  if (variant === 'medium') {
    return normalizeStaticPath(photo.mediumThumbPath) || normalizeStaticPath(photo.thumbnailPath) || normalizeStaticPath(photo.webpPath) || normalizeStaticPath(photo.originalPath)
  }
  if (variant === 'small') {
    return normalizeStaticPath(photo.smallThumbPath) || normalizeStaticPath(photo.mediumThumbPath) || normalizeStaticPath(photo.thumbnailPath) || normalizeStaticPath(photo.originalPath)
  }
  if (variant === 'thumbnail') {
    return normalizeStaticPath(photo.thumbnailPath) || normalizeStaticPath(photo.mediumThumbPath) || normalizeStaticPath(photo.originalPath)
  }
  return (
    normalizeStaticPath(photo.webpPath) ||
    normalizeStaticPath(photo.mediumThumbPath) ||
    normalizeStaticPath(photo.thumbnailPath) ||
    normalizeStaticPath(photo.originalPath)
  )
}
