import { defineStore } from 'pinia'
import { ref, watch } from 'vue'
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
  backgroundColor?: string
  foregroundColor?: string
  navbarColor?: string
  atmosphereEffects?: any[]
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
  smallThumbPath?: string
  mediumThumbPath?: string
  largeThumbPath?: string
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
  likeCount: number
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
  const photosWall = ref<Photo[]>([])
  const photosRandom = ref<Photo[]>([])
  const categories = ref<string[]>([])
  const currentAlbum = ref<Album | null>(null)
  const currentPhoto = ref<Photo | null>(null)
  const loading = ref(false)

  const fetchAlbums = async (page = 0, size = 12, category?: string, sort?: string, setLoading = true) => {
    const wasLoading = loading.value
    if (setLoading) loading.value = true
    try {
      const params: any = { page, size }
      if (category) params.category = category
      if (sort) params.sort = sort
      const response = await api.get('/albums', { params })
      // 合并并去重（按 id）
      const incoming: Album[] = response.data.content || []
      if (page === 0) {
        albums.value = incoming
      } else {
        const merged = [...albums.value, ...incoming]
        const seen = new Map<number, Album>()
        merged.forEach(a => {
          if (!seen.has(a.id)) seen.set(a.id, a)
        })
        albums.value = Array.from(seen.values())
      }
      return response.data
    } finally {
      if (setLoading) loading.value = false
      else loading.value = wasLoading // 恢复原来的loading状态
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

  const fetchAllPhotosByAlbum = async (albumId: number) => {
    loading.value = true
    try {
      // 一次性获取所有照片，不分页
      const response = await api.get(`/photos/album/${albumId}`, { params: { all: true } })
      photos.value = response.data.content || []
      return response.data
    } finally {
      loading.value = false
    }
  }

  const fetchPhotoWall = async (page = 0, size = 20) => {
    // 仅当当前路由在 /wall 且当前活跃视图是 'wall' 时才真正请求 wall 接口，
    // 这样可以防止在路由已变更但旧的异步任务尚未取消时触发请求造成闪烁。
    try {
      const path = typeof window !== 'undefined' ? window.location.pathname : ''
      if (path.indexOf('/wall') !== 0 || currentView.value !== 'wall') {
        return { content: [], last: true }
      }
    } catch (e) {
      return { content: [], last: true }
    }
    // 如果当前存在 lastFilters，阻止回退到未筛选的 wall 接口（以避免在筛选会话中加载无关图片）
    if (lastFilters.value) {
      return { content: [], last: true }
    }
    loading.value = true
    try {
      // 添加多个缓存破坏参数
      const timestamp = Date.now()
      const randomId = Math.random().toString(36).substring(2, 15)
      const response = await api.get('/photos/wall', {
        params: {
          page,
          size,
          _t: timestamp,        // 时间戳
          _r: randomId,         // 随机字符串
          _cache: 'bypass'      // 缓存绕过标识
        },
        headers: {
          'Cache-Control': 'no-cache',
          'Pragma': 'no-cache'
        }
      })
      if (page === 0) {
        photosWall.value = response.data.content
      } else {
        photosWall.value = [...photosWall.value, ...response.data.content]
      }
      // response handled
      return response.data
    } finally {
      loading.value = false
    }
  }

  const fetchRandomPhotos = async (page = 0, size = 12, minQualityScore = 70) => {
    try {
      const path = typeof window !== 'undefined' ? window.location.pathname : ''
      if (path.indexOf('/random') !== 0 || currentView.value !== 'random') {
        return { content: [], last: true }
      }
    } catch (e) {
      return { content: [], last: true }
    }
    if (lastFilters.value) {
      return { content: [], last: true }
    }
    loading.value = true
    try {
      const response = await api.get('/photos/random', { 
        params: { page, size, minQualityScore } 
      })
      if (page === 0) {
        photosRandom.value = response.data.content
      } else {
        photosRandom.value = [...photosRandom.value, ...response.data.content]
      }
      // response handled
      return response.data
    } finally {
      loading.value = false
    }
  }
 
  // 保存上次使用的过滤条件，供分页加载继续使用
  const lastFilters = ref<any | null>(null)
  // 当 lastFilters 对应的分页全部加载完毕时置为 true，防止回退到未筛选加载
  const lastFiltersExhausted = ref<boolean>(false)
  // 标记当前是否处于筛选会话（即用户已应用过筛选且未清空）
  const lastFiltersActive = ref<boolean>(false)
  // 标记当前是否正在请求筛选（用于避免并发的 loadMore 在筛选尚未完成时触发）
  const lastFiltersLoading = ref<boolean>(false)
  // 当前活跃视图标识（'wall'|'random'|null），用于避免非活跃视图触发其 API
  const currentView = ref<string | null>(null)

  // 监听 lastFilters 的变化（保留空监听以便未来可插入监控）
  watch(lastFilters, (_newVal, _oldVal) => {
    // no-op
  })

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
      // Assign filter results to the appropriate list depending on currentView
      try {
        const path = typeof window !== 'undefined' ? window.location.pathname : ''
        if (path.indexOf('/random') === 0 || currentView.value === 'random') {
          if (page === 0) photosRandom.value = response.data.content
          else photosRandom.value = [...photosRandom.value, ...response.data.content]
        } else {
          if (page === 0) photosWall.value = response.data.content
          else photosWall.value = [...photosWall.value, ...response.data.content]
        }
      } catch (e) {
        // fallback
        if (page === 0) photos.value = response.data.content
        else photos.value = [...photos.value, ...response.data.content]
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
      // Assign filter results to the appropriate list depending on currentView
      try {
        const path = typeof window !== 'undefined' ? window.location.pathname : ''
        if (path.indexOf('/random') === 0 || currentView.value === 'random') {
          if (page === 0) photosRandom.value = response.data.content
          else photosRandom.value = [...photosRandom.value, ...response.data.content]
        } else {
          if (page === 0) photosWall.value = response.data.content
          else photosWall.value = [...photosWall.value, ...response.data.content]
        }
      } catch (e) {
        // fallback
        if (page === 0) photos.value = response.data.content
        else photos.value = [...photos.value, ...response.data.content]
      }
      return response.data
    } finally {
      loading.value = false
    }
  }

  const filterPhotos = async (filters: any, page = 0, size = 20) => {
    loading.value = true
    lastFiltersLoading.value = true
    try {
      // 记录当前过滤条件
      // 防御性处理：如果 caller 传入了 undefined，但是我们之前已有 lastFilters，则保留之前的 filters（避免被覆盖为 undefined）
      if (!filters && lastFilters.value) {
        filters = lastFilters.value
      }
      lastFilters.value = filters
      lastFiltersActive.value = true
      // 检查是否需要随机排序（在随机页面时）
      const path = typeof window !== 'undefined' ? window.location.pathname : ''
      const isRandomPage = path.indexOf('/random') === 0 || currentView.value === 'random'
      const response = await api.post('/photos/filter', {
        ...filters,
        page,
        size,
        randomOrder: isRandomPage
      })

      // Assign filter results to the appropriate list depending on currentView
      try {
        const path = typeof window !== 'undefined' ? window.location.pathname : ''
        if (path.indexOf('/random') === 0 || currentView.value === 'random') {
          if (page === 0) photosRandom.value = response.data.content
          else photosRandom.value = [...photosRandom.value, ...response.data.content]
        } else {
          if (page === 0) photosWall.value = response.data.content
          else photosWall.value = [...photosWall.value, ...response.data.content]
        }
      } catch (e) {
        // fallback to generic photos array
        if (page === 0) {
          photos.value = response.data.content
        } else {
          photos.value = [...photos.value, ...response.data.content]
        }
      }

      // 标记是否已加载完当前过滤条件对应的所有页
      lastFiltersExhausted.value = !!response.data.last
      return response.data
    } finally {
      loading.value = false
      lastFiltersLoading.value = false
    }
  }

  // helper: 是否存在活动筛选（同步判断，用于视图层快速分支）
  const hasActiveFilters = () => {
    return !!(lastFilters.value || lastFiltersActive.value)
  }

  const isFiltersLoading = () => {
    return !!lastFiltersLoading.value
  }

  // 添加相册到现有列表（用于预加载缓冲区）
  const addAlbums = (newAlbums: Album[]) => {
    if (!newAlbums || newAlbums.length === 0) return

    // 合并并去重（按 id）
    const merged = [...albums.value, ...newAlbums]
    const seen = new Map<number, Album>()
    merged.forEach(a => {
      if (!seen.has(a.id)) seen.set(a.id, a)
    })
    albums.value = Array.from(seen.values())
  }

  const setCurrentView = (view: string | null) => {
    currentView.value = view
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
    fetchAllPhotosByAlbum,
    fetchPhotoWall,
    fetchRandomPhotos,
    fetchPhotoById,
    fetchPhotosByTag,
    fetchPhotosByPerson,
    filterPhotos,
    addAlbums,
    // expose lastFilters and helpers so views can continue pagination or clear filters
    lastFilters,
    lastFiltersExhausted,
    lastFiltersActive,
    clearLastFilters: () => { 
      lastFilters.value = null; 
      lastFiltersExhausted.value = false; 
      lastFiltersActive.value = false 
    },
    hasActiveFilters,
    isFiltersLoading,
    photosWall,
    photosRandom,
    currentView,
    setCurrentView
  }
})

