import { defineStore } from 'pinia'
import { ref } from 'vue'
import { api } from '@/api'

export interface Album {
  id: number
  name: string
  displayTitle?: string
  category?: string
  path: string
  coverImageId?: number
  description?: string
  photoCount: number
  takenAt?: string
  tags?: Tag[]
  coverImages?: CoverImages
  createdAt: string
  updatedAt: string
}

export interface Photo {
  id: number
  albumId: number
  filename: string
  originalPath: string
  thumbnailPath?: string
  webpPath?: string
  fileSize?: number
  width?: number
  height?: number
  format?: string
  dominantColor?: string
  colorPalette?: string[]
  exifData?: Record<string, any>
  cameraMake?: string
  cameraModel?: string
  lensModel?: string
  focalLength?: string
  aperture?: string
  shutterSpeed?: string
  iso?: number
  takenAt?: string
  qualityScore?: number
  focusX?: number // 焦点X位置（百分比 0-100）
  focusY?: number // 焦点Y位置（百分比 0-100）
  viewCount: number
  isFeatured: boolean
  tags?: Tag[]
  faces?: FaceFace[]
  createdAt: string
}

export interface FaceFace {
  id: number
  photoId?: number
  x?: number
  y?: number
  width?: number
  height?: number
  confidence?: number
  personId?: number
  personName?: string
  personDescription?: string
  isConfirmed?: boolean
  photoFilename?: string
  photoThumbnailPath?: string
  photoOriginalPath?: string
  similarity?: number
}

export interface Tag {
  id: number
  name: string
  color?: string
}

export interface CoverImages {
  leftVertical?: Photo
  rightTop?: Photo
  rightBottom?: Photo
}

export const usePhotoStore = defineStore('photo', () => {
  const albums = ref<Album[]>([])
  const photos = ref<Photo[]>([])
  const categories = ref<string[]>([])
  const currentAlbum = ref<Album | null>(null)
  const currentPhoto = ref<Photo | null>(null)
  const loading = ref(false)

  const fetchAlbums = async (page = 0, size = 12, category?: string) => {
    loading.value = true
    try {
      const params: any = { page, size }
      if (category) params.category = category
      const response = await api.get('/albums', { params })
      if (page === 0) {
        albums.value = response.data.content
      } else {
        albums.value = [...albums.value, ...response.data.content]
      }
      return response.data
    } finally {
      loading.value = false
    }
  }

  const fetchCategories = async () => {
    const res = await api.get('/albums/categories')
    categories.value = res.data || []
    return categories.value
  }

  const fetchAlbumById = async (id: number) => {
    loading.value = true
    try {
      const response = await api.get(`/albums/${id}`)
      currentAlbum.value = response.data
      return response.data
    } finally {
      loading.value = false
    }
  }

  const fetchPhotosByAlbum = async (albumId: number, page = 0, size = 20) => {
    loading.value = true
    try {
      const response = await api.get(`/photos/album/${albumId}`, { params: { page, size } })
      photos.value = response.data.content
      return response.data
    } finally {
      loading.value = false
    }
  }

  const fetchPhotoWall = async (page = 0, size = 20) => {
    loading.value = true
    try {
      const response = await api.get('/photos/wall', { params: { page, size } })
      if (page === 0) {
        photos.value = response.data.content
      } else {
        photos.value = [...photos.value, ...response.data.content]
      }
      return response.data
    } finally {
      loading.value = false
    }
  }

  const fetchRandomPhotos = async (page = 0, size = 12, minQualityScore = 70) => {
    loading.value = true
    try {
      const response = await api.get('/photos/random', { 
        params: { page, size, minQualityScore } 
      })
      if (page === 0) {
        photos.value = response.data.content
      } else {
        photos.value = [...photos.value, ...response.data.content]
      }
      return response.data
    } finally {
      loading.value = false
    }
  }

  const fetchPhotoById = async (id: number) => {
    loading.value = true
    try {
      const response = await api.get(`/photos/${id}`)
      currentPhoto.value = response.data
      return response.data
    } finally {
      loading.value = false
    }
  }

  const fetchPhotosByTag = async (tagId: number, page = 0, size = 20) => {
    loading.value = true
    try {
      const response = await api.post('/photos/filter', { tagIds: [tagId], page, size })
      if (page === 0) {
        photos.value = response.data.content
      } else {
        photos.value = [...photos.value, ...response.data.content]
      }
      return response.data
    } finally {
      loading.value = false
    }
  }

  const fetchPhotosByPerson = async (personId: number, page = 0, size = 20) => {
    loading.value = true
    try {
      const response = await api.post('/photos/filter', { personId, page, size })
      if (page === 0) {
        photos.value = response.data.content
      } else {
        photos.value = [...photos.value, ...response.data.content]
      }
      return response.data
    } finally {
      loading.value = false
    }
  }

  const filterPhotos = async (filters: any, page = 0, size = 20) => {
    loading.value = true
    try {
      const response = await api.post('/photos/filter', { ...filters, page, size })
      photos.value = response.data.content
      return response.data
    } finally {
      loading.value = false
    }
  }

  return {
    categories,
    albums,
    photos,
    currentAlbum,
    currentPhoto,
    loading,
    fetchAlbums,
    fetchCategories,
    fetchAlbumById,
    fetchPhotosByAlbum,
    fetchPhotoWall,
    fetchRandomPhotos,
    fetchPhotoById,
    fetchPhotosByTag,
    fetchPhotosByPerson,
    filterPhotos
  }
})

