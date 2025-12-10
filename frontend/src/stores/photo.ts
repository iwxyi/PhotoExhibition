import { defineStore } from 'pinia'
import { ref } from 'vue'
import { api } from '@/api'

export interface Album {
  id: number
  name: string
  displayTitle?: string
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
  viewCount: number
  isFeatured: boolean
  tags?: Tag[]
  createdAt: string
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
  const currentAlbum = ref<Album | null>(null)
  const currentPhoto = ref<Photo | null>(null)
  const loading = ref(false)

  const fetchAlbums = async (page = 0, size = 12) => {
    loading.value = true
    try {
      const response = await api.get('/albums', { params: { page, size } })
      albums.value = response.data.content
      return response.data
    } finally {
      loading.value = false
    }
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
      photos.value = response.data.content
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
      photos.value = response.data.content
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
    albums,
    photos,
    currentAlbum,
    currentPhoto,
    loading,
    fetchAlbums,
    fetchAlbumById,
    fetchPhotosByAlbum,
    fetchPhotoWall,
    fetchRandomPhotos,
    fetchPhotoById,
    filterPhotos
  }
})

