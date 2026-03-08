<script setup lang="ts">
import { ref, onMounted, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { albumApi, aiApi, personApi } from '@/api'
import { useAuthStore } from '@/stores/auth'
import AppHeader from '@/components/AppHeader.vue'
import SettingsMenu from '@/components/SettingsMenu.vue'
import PhotoViewer from '@/components/PhotoViewer.vue'
import AlbumCard from '@/components/AlbumCard.vue'
import type { Photo } from '@/stores/photo'

const route = useRoute()
const router = useRouter()

// 检测是否为移动端
const isMobile = ref(window.innerWidth < 640)

interface AlbumDTO {
  id: number
  name: string
  description?: string
  path?: string
  coverImageId?: number
  coverImagePath?: string
  photoCount?: number
  coverImages?: {
    cover1?: any
    cover2?: any
    cover3?: any
  }
}

interface PersonSummary {
  id: number
  name: string
  samplePhotoId?: number
  sampleThumbnailPath?: string
  faceCount?: number
}

interface FaceFace {
  id: number
  photoId?: number
  personId?: number
  personName?: string
  isConfirmed?: boolean
  photoThumbnailPath?: string
  photoMediumThumbPath?: string
  similarity?: number
  originalPath?: string
  mediumThumbPath?: string
  thumbnailPath?: string
  // 照片详细信息
  photoFilename?: string
  photoLargeThumbPath?: string
  photoWebpPath?: string
  photoWidth?: number
  photoHeight?: number
  photoTakenAt?: string
  photoFolderPath?: string
  albumId?: number
  // EXIF 信息
  photoLocation?: string
  photoCameraModel?: string
  photoLensModel?: string
  photoAperture?: string
  photoShutterSpeed?: string
  photoIso?: string
  photoFocalLength?: string
}

interface FacePhoto {
  id: number
  albumId: number
  filename: string
  thumbnailPath?: string
  mediumThumbPath?: string
  originalPath?: string
  width?: number
  height?: number
  takenAt?: string
}

interface PhotoDTO {
  id: number
  filename: string
  thumbnailPath?: string
  mediumThumbPath?: string
  largeThumbPath?: string
  originalPath?: string
  webpPath?: string
  width?: number
  height?: number
  takenAt?: string
  albumId?: number
  folderPath?: string
}

const authStore = useAuthStore()

// 用于模板中的全局 window 访问
const globalWindow = typeof window !== 'undefined' ? window : null

// PhotoViewer 相关状态
const viewerVisible = ref(false)
const viewerIndex = ref(0)
const viewerOriginRect = ref<{ top: number; left: number; width: number; height: number } | null>(null)

// 转换为 PhotoViewer 需要的 Photo 格式
const viewerPhotos = computed<Photo[]>(() => {
  return similarFaces.value
    .filter(face => face.photoId)
    .map((face) => ({
      id: face.photoId!,
      filename: face.photoFilename || face.personName || '未命名',
      originalPath: face.photoOriginalPath || face.originalPath || '',
      thumbnailPath: face.photoThumbnailPath || face.thumbnailPath,
      webpPath: face.photoWebpPath,
      smallThumbPath: undefined,
      mediumThumbPath: face.photoMediumThumbPath || face.mediumThumbPath,
      largeThumbPath: face.photoLargeThumbPath,
      width: face.photoWidth || 0,
      height: face.photoHeight || 0,
      takenAt: face.photoTakenAt || '',
      albumId: face.albumId || 0,
      // EXIF 信息
      cameraMake: undefined,
      cameraModel: face.photoCameraModel || '',
      lensModel: face.photoLensModel || '',
      focalLength: face.photoFocalLength || '',
      aperture: face.photoAperture || '',
      shutterSpeed: face.photoShutterSpeed || '',
      iso: face.photoIso ? parseInt(face.photoIso) : undefined,
      // 其他
      location: face.photoLocation || '',
      format: undefined,
      colorCategory: undefined,
      colorPalette: undefined,
      exifData: undefined,
      fileSize: undefined,
      qualityScore: undefined,
      focusX: undefined,
      focusY: undefined,
      focalLengthMm: undefined,
      apertureValue: undefined,
      shutterSpeedSeconds: undefined
    }))
})

// 判断是否已登录
const isAdmin = computed(() => authStore.isAuthenticated)


// 获取已确认的人物（用于"是TA"按钮）
const confirmedPerson = computed(() => {
  const confirmed = similarFaces.value.find(f => f.personId && f.personName)
  if (confirmed) {
    return { id: confirmed.personId, name: confirmed.personName }
  }
  return null
})

// 获取未确认的人脸ID列表
const unconfirmedFaceIds = computed(() => {
  return similarFaces.value
    .filter(f => !f.personId && f.id)
    .map(f => f.id)
})

const keyword = ref('')
const faceId = ref<number | null>(null)
const loading = ref(true)
const albums = ref<AlbumDTO[]>([])
const persons = ref<PersonSummary[]>([])
const photos = ref<PhotoDTO[]>([])
const facePhotos = ref<FacePhoto[]>([])
const similarFaces = ref<FaceFace[]>([])
const hasSearched = ref(false)
const searchMode = ref<'keyword' | 'face'>('keyword')

onMounted(async () => {
  // 从查询参数获取关键词
  keyword.value = (route.query.q as string) || (route.params.keyword as string) || ''
  faceId.value = route.query.faceId ? parseInt(route.query.faceId as string, 10) : null

  if (faceId.value && !isNaN(faceId.value)) {
    searchMode.value = 'face'
    await searchSimilarFaces()
  } else if (keyword.value) {
    searchMode.value = 'keyword'
    await search()
  } else {
    loading.value = false
  }
})

// 监听路由变化，支持 /search?q=xxx 和 /s/xxx 之间的切换
watch(
  () => [route.query.q, route.params.keyword, route.query.faceId],
  async ([newQ, newKeyword, newFaceId]) => {
    const newKeywordValue = (newQ as string) || (newKeyword as string) || ''
    const newFaceIdValue = newFaceId ? parseInt(newFaceId as string, 10) : null

    keyword.value = newKeywordValue
    faceId.value = newFaceIdValue && !isNaN(newFaceIdValue) ? newFaceIdValue : null

    if (faceId.value && !isNaN(faceId.value)) {
      searchMode.value = 'face'
      await searchSimilarFaces()
    } else if (newKeywordValue) {
      searchMode.value = 'keyword'
      await search()
    } else {
      loading.value = false
      hasSearched.value = false
      albums.value = []
      persons.value = []
      photos.value = []
      similarFaces.value = []
    }
  }
)

const search = async () => {
  loading.value = true
  hasSearched.value = true

  try {
    const decodedKeyword = decodeURIComponent(keyword.value)
    const response = await albumApi.searchAll(decodedKeyword)
    albums.value = response.data.albums || []
    persons.value = response.data.persons || []
    photos.value = response.data.photos || []
  } catch (e) {
    console.error('搜索失败:', e)
    albums.value = []
    persons.value = []
    photos.value = []
  } finally {
    loading.value = false
  }
}

const searchSimilarFaces = async () => {
  loading.value = true
  hasSearched.value = true
  similarFaces.value = []
  facePhotos.value = []

  try {
    console.log('正在查询相似人脸, faceId:', faceId.value)

    // 获取包含该人脸的照片
    const photosResponse = await aiApi.findPhotosByFaceId(faceId.value!)
    console.log('照片响应:', photosResponse)
    facePhotos.value = photosResponse.data || []
    console.log('facePhotos:', facePhotos.value)

    // 获取相似人脸
    const response = await aiApi.findSimilarFaces(faceId.value!, 20, 0.5)
    console.log('API 响应:', response)
    console.log('response.data:', response.data)
    similarFaces.value = response.data || []
    console.log('similarFaces:', similarFaces.value)
  } catch (e: any) {
    console.error('搜索相似人脸失败:', e)
    console.error('错误详情:', e.response?.data || e.message)
    similarFaces.value = []
  } finally {
    loading.value = false
  }
}

const hasResults = computed(() => {
  if (searchMode.value === 'face') {
    return similarFaces.value.length > 0 || facePhotos.value.length > 0
  }
  return albums.value.length > 0 || persons.value.length > 0 || photos.value.length > 0
})

const getPersonPhotoUrl = (person: PersonSummary) => {
  if (person.sampleThumbnailPath) {
    return `/api/photos/${person.sampleThumbnailPath.replace(/^\/+/, '')}`
  }
  return ''
}

const goToAlbum = (albumId: number) => {
  window.open(`/a/${albumId}`, '_blank')
}

const getAlbumCoverUrl = (album: AlbumDTO) => {
  // 优先使用 coverImages
  if (album.coverImages) {
    const cover = album.coverImages.cover1 || album.coverImages.cover2 || album.coverImages.cover3
    if (cover?.thumbnailPath) {
      return `/api/files${cover.thumbnailPath}`
    }
    if (cover?.mediumThumbPath) {
      return `/api/files${cover.mediumThumbPath}`
    }
    if (cover?.originalPath) {
      return `/api/files${cover.originalPath}`
    }
  }
  // 降级使用 coverImageId
  if (album.coverImageId) {
    return `/api/photos/thumbnail/${album.coverImageId}`
  }
  return ''
}

const getFacePhotoUrl = (photo: FacePhoto) => {
  if (photo.mediumThumbPath) {
    return `/api/photos/${photo.mediumThumbPath.replace(/^\/+/, '')}`
  }
  if (photo.thumbnailPath) {
    return `/api/photos/${photo.thumbnailPath.replace(/^\/+/, '')}`
  }
  return ''
}

const getPhotoUrl = (photo: PhotoDTO) => {
  if (photo.largeThumbPath) {
    return `/api/files${photo.largeThumbPath}`
  }
  if (photo.webpPath) return `/api/files${photo.webpPath}`
  if (photo.mediumThumbPath) {
    return `/api/photos/${photo.mediumThumbPath.replace(/^\/+/, '')}`
  }
  if (photo.originalPath) return `/api/files${photo.originalPath}`
  return ''
}

const getPhotoThumbUrl = (photo: PhotoDTO) => {
  if (photo.thumbnailPath) {
    return `/api/files${photo.thumbnailPath}`
  }
  return getPhotoUrl(photo)
}

const getFaceUrl = (face: FaceFace) => {
  const path = face.photoMediumThumbPath || face.photoThumbnailPath
  if (path) {
    // 移除开头的斜杠，避免URL中出现双斜杠
    return `/api/photos/${path.replace(/^\/+/, '')}`
  }
  return ''
}

// 打开 PhotoViewer
const openViewer = (index: number, e: MouseEvent) => {
  viewerIndex.value = index
  const img = (e.target as HTMLElement).closest('img') as HTMLImageElement | null
  const rectSource = img || (e.currentTarget as HTMLElement | null)
  if (rectSource) {
    const rect = rectSource.getBoundingClientRect()
    viewerOriginRect.value = {
      top: rect.top,
      left: rect.left,
      width: rect.width,
      height: rect.height
    }
  } else {
    viewerOriginRect.value = null
  }
  viewerVisible.value = true
}


// 新建人物（如果人物已存在则合并）
const createNewPerson = async () => {
  const name = prompt('请输入人物名称:')
  if (!name || !name.trim()) {
    return
  }

  const faceIds = similarFaces.value.map(f => f.id).filter(id => id)
  if (faceIds.length === 0) {
    return
  }

  try {
    // 先尝试搜索已存在的人物
    let personId: number | null = null
    try {
      const searchRes = await personApi.searchByName(name.trim())
      if (searchRes.data && searchRes.data.id) {
        personId = searchRes.data.id
      }
    } catch {
      // 人物不存在，继续创建
    }

    // 如果人物不存在，创建新人物
    if (!personId) {
      const createRes = await personApi.createPerson(name.trim())
      if (createRes.data && createRes.data.id) {
        personId = createRes.data.id
      }
    }

    if (!personId) {
      return
    }

    // 绑定所有相似人脸到人物
    await aiApi.assignFacesToPerson(faceIds, name.trim())

    // 刷新页面
    globalWindow?.location?.reload()
  } catch (e: any) {
    console.error('绑定人脸失败:', e)
  }
}

// 将未确认的人脸合并到已确认的人物
const assignToConfirmedPerson = async () => {
  if (!confirmedPerson.value || unconfirmedFaceIds.value.length === 0) {
    return
  }

  try {
    await aiApi.assignFacesToPerson(unconfirmedFaceIds.value, confirmedPerson.value.name)
    globalWindow?.location?.reload()
  } catch (e: any) {
    console.error('合并人脸失败:', e)
  }
}

// 照片列表 PhotoViewer 相关状态
const photoViewerVisible = ref(false)
const photoViewerIndex = ref(0)
const photoViewerOriginRect = ref<{ top: number; left: number; width: number; height: number } | null>(null)

// 转换为 PhotoViewer 需要的 Photo 格式（用于照片搜索结果）
const photoViewerPhotos = computed<Photo[]>(() => {
  return photos.value.map((photo) => ({
    id: photo.id,
    filename: photo.filename,
    thumbnailPath: photo.thumbnailPath,
    mediumThumbPath: photo.mediumThumbPath,
    largeThumbPath: photo.largeThumbPath,
    originalPath: photo.originalPath,
    webpPath: photo.webpPath,
    width: photo.width || 0,
    height: photo.height || 0,
    takenAt: photo.takenAt || '',
    albumId: photo.albumId || 0,
    folderPath: photo.folderPath || ''
  }))
})

// 打开照片 PhotoViewer
const openPhotoViewer = (index: number, e: MouseEvent) => {
  photoViewerIndex.value = index
  const img = (e.target as HTMLElement).closest('img') as HTMLImageElement | null
  const rectSource = img || (e.currentTarget as HTMLElement | null)
  if (rectSource) {
    const rect = rectSource.getBoundingClientRect()
    photoViewerOriginRect.value = {
      top: rect.top,
      left: rect.left,
      width: rect.width,
      height: rect.height
    }
  } else {
    photoViewerOriginRect.value = null
  }
  photoViewerVisible.value = true
}
</script>

<template>
  <div class="min-h-screen bg-white dark:bg-gray-900">
    <!-- 导航栏 -->
    <nav
      class="sticky top-0 z-50 bg-white/80 dark:bg-gray-900/80 backdrop-blur-md border-b border-gray-200 dark:border-gray-800 safe-area-inset-top transition-transform duration-300 ease-in-out transform-gpu"
      style="padding-top: env(safe-area-inset-top);"
    >
      <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div class="flex justify-between items-center h-12">
          <AppHeader :show-nav-links="!isMobile" />
          <div class="flex items-center space-x-4">
            <SettingsMenu />
          </div>
        </div>
      </div>
    </nav>

    <!-- 内容区域 -->
    <main class="container mx-auto px-4 py-8" :class="{ 'pt-6 pb-12': true }">
      <!-- 搜索标题 -->
      <div class="mb-8">
        <h1 class="text-3xl font-bold text-gray-800 dark:text-white mb-2">
          搜索结果
        </h1>
        <p class="text-gray-600 dark:text-gray-400" v-if="keyword">
          关键词: {{ decodeURIComponent(keyword) }}
        </p>
        <p class="text-gray-600 dark:text-gray-400" v-else-if="faceId">
          相似人脸搜索 (人脸ID: {{ faceId }})
        </p>
      </div>

      <!-- 加载状态 -->
      <div v-if="loading" class="flex justify-center py-20">
        <div class="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-500"></div>
      </div>

      <!-- 无结果 -->
      <div v-else-if="hasSearched && !hasResults" class="text-center py-20">
        <svg class="w-16 h-16 text-gray-400 mx-auto mb-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
            d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
        </svg>
        <p class="text-gray-500 dark:text-gray-400 text-lg">
          未找到相关结果
        </p>
        <p class="text-gray-400 dark:text-gray-500 mt-2">
          试试其他关键词？
        </p>
      </div>

      <!-- 有结果 -->
      <div v-else>
        <!-- 包含该人脸的照片 -->
        <div v-if="searchMode === 'face' && facePhotos.length > 0" class="mb-10">
          <h2 class="text-xl font-semibold text-gray-800 dark:text-white mb-4 flex items-center">
            <svg class="w-6 h-6 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z" />
            </svg>
            包含此人的照片 ({{ facePhotos.length }})
          </h2>
          <div class="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6 gap-4">
            <a
              v-for="photo in facePhotos"
              :key="photo.id"
              :href="`/photo/${photo.albumId}/${photo.id}`"
              target="_blank"
              class="bg-white dark:bg-gray-800 rounded-lg overflow-hidden shadow-md hover:shadow-lg transition-shadow cursor-pointer group block"
            >
              <div class="aspect-square bg-gray-200 dark:bg-gray-700 relative">
                <img
                  v-if="getFacePhotoUrl(photo)"
                  :src="getFacePhotoUrl(photo)"
                  :alt="photo.filename"
                  class="w-full h-full object-cover"
                />
                <div v-else class="w-full h-full flex items-center justify-center">
                  <svg class="w-12 h-12 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z" />
                  </svg>
                </div>
              </div>
              <div class="p-2">
                <p class="text-sm text-gray-600 dark:text-gray-400 truncate">
                  {{ photo.filename }}
                </p>
              </div>
            </a>
          </div>
        </div>

        <!-- 相似人脸结果 -->
        <div v-if="searchMode === 'face' && similarFaces.length > 0" class="mb-10">
          <h2 class="text-xl font-semibold text-gray-800 dark:text-white mb-4 flex items-center justify-between">
            <div class="flex items-center">
              <svg class="w-6 h-6 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                  d="M14.828 14.828a4 4 0 01-5.656 0M9 10h.01M15 10h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
              </svg>
              相似人脸 ({{ similarFaces.length }})
            </div>
            <button
              v-if="isAdmin"
              @click="createNewPerson"
              class="px-3 py-1 text-sm bg-green-600 hover:bg-green-700 text-white rounded-lg flex items-center"
            >
              <svg class="w-4 h-4 mr-1" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 6v6m0 0v6m0-6h6m-6 0H6" />
              </svg>
              新建人物
            </button>
          </h2>
          <div class="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6 gap-4">
            <div
              v-for="(face, index) in similarFaces"
              :key="face.id"
              class="bg-white dark:bg-gray-800 rounded-lg overflow-hidden shadow-md hover:shadow-lg transition-shadow cursor-pointer group"
              @click="face.photoId && openViewer(index, $event)"
            >
              <div class="aspect-square bg-gray-200 dark:bg-gray-700 relative">
                <img
                  v-if="getFaceUrl(face)"
                  :src="getFaceUrl(face)"
                  :alt="face.personName || '未命名'"
                  class="w-full h-full object-cover"
                />
                <div v-else class="w-full h-full flex items-center justify-center">
                  <svg class="w-12 h-12 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                      d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z" />
                  </svg>
                </div>
                <!-- 相似度标签 -->
                <div v-if="face.similarity" class="absolute top-2 left-2 bg-blue-600/80 text-white text-xs px-2 py-1 rounded">
                  {{ Math.round(face.similarity * 100) }}%
                </div>
              </div>
              <div class="p-2 flex items-center justify-between">
                <div class="flex-1 min-w-0">
                  <p class="text-sm font-medium text-gray-800 dark:text-white truncate">
                    {{ face.personName || '未命名' }}
                  </p>
                  <p v-if="face.isConfirmed" class="text-xs text-green-500">
                    已确认
                  </p>
                </div>
                <button
                  v-if="isAdmin && confirmedPerson && face.personId === confirmedPerson.id && unconfirmedFaceIds.length > 0"
                  @click.stop="assignToConfirmedPerson"
                  class="ml-2 px-2 py-1 text-xs bg-blue-600 hover:bg-blue-700 text-white rounded flex-shrink-0"
                  :title="`将${unconfirmedFaceIds.length}张未确认人脸合并到此人物`"
                >
                  是TA
                </button>
              </div>
            </div>
          </div>
        </div>

        <!-- 人物结果 -->
        <div v-if="searchMode === 'keyword' && persons.length > 0" class="mb-10">
          <h2 class="text-xl font-semibold text-gray-800 dark:text-white mb-4 flex items-center">
            <svg class="w-6 h-6 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z" />
            </svg>
            人物 ({{ persons.length }})
          </h2>
          <div class="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 gap-4">
            <router-link
              v-for="person in persons"
              :key="person.id"
              :to="`/p/${person.id}`"
              class="block group"
            >
              <div class="bg-white dark:bg-gray-800 rounded-lg overflow-hidden shadow-md hover:shadow-lg transition-shadow">
                <div class="aspect-square bg-gray-200 dark:bg-gray-700 relative">
                  <img
                    v-if="getPersonPhotoUrl(person)"
                    :src="getPersonPhotoUrl(person)"
                    :alt="person.name"
                    class="w-full h-full object-cover"
                  />
                  <div v-else class="w-full h-full flex items-center justify-center">
                    <svg class="w-12 h-12 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                        d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z" />
                    </svg>
                  </div>
                </div>
                <div class="p-3">
                  <h3 class="font-medium text-gray-800 dark:text-white truncate group-hover:text-blue-500 transition-colors">
                    {{ person.name }}
                  </h3>
                  <p class="text-sm text-gray-500 dark:text-gray-400">
                    {{ person.faceCount || 0 }} 张照片
                  </p>
                </div>
              </div>
            </router-link>
          </div>
        </div>

        <!-- 照片结果 -->
        <div v-if="searchMode === 'keyword' && photos.length > 0" class="mb-10">
          <h2 class="text-xl font-semibold text-gray-800 dark:text-white mb-4 flex items-center">
            <svg class="w-6 h-6 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z" />
            </svg>
            照片 ({{ photos.length }})
          </h2>
          <div class="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6 gap-4">
            <div
              v-for="(photo, index) in photos"
              :key="photo.id"
              class="bg-white dark:bg-gray-800 rounded-lg overflow-hidden shadow-md hover:shadow-lg transition-shadow cursor-pointer group"
              @click="openPhotoViewer(index, $event)"
            >
              <div class="aspect-square bg-gray-200 dark:bg-gray-700 relative">
                <img
                  v-if="getPhotoThumbUrl(photo)"
                  :src="getPhotoThumbUrl(photo)"
                  :alt="photo.filename"
                  class="w-full h-full object-cover"
                  loading="lazy"
                />
                <div v-else class="w-full h-full flex items-center justify-center">
                  <svg class="w-12 h-12 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                      d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z" />
                  </svg>
                </div>
              </div>
              <div class="p-2">
                <p class="text-sm font-medium text-gray-800 dark:text-white truncate">
                  {{ photo.filename }}
                </p>
              </div>
            </div>
          </div>
        </div>

        <!-- 相册结果 -->
        <div v-if="searchMode === 'keyword' && albums.length > 0">
          <h2 class="text-xl font-semibold text-gray-800 dark:text-white mb-4 flex items-center">
            <svg class="w-6 h-6 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z" />
            </svg>
            相册 ({{ albums.length }})
          </h2>
          <div class="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 gap-4">
            <AlbumCard
              v-for="album in albums"
              :key="album.id"
              :album="album as any"
              size="md"
              @click="goToAlbum(album.id)"
            />
          </div>
        </div>
      </div>
    </main>

    <!-- PhotoViewer -->
    <PhotoViewer
      v-if="viewerPhotos.length > 0"
      :photos="viewerPhotos"
      :visible="viewerVisible"
      :start-index="viewerIndex"
      :origin-rect="viewerOriginRect"
      @update:visible="viewerVisible = $event"
    />
    <!-- 照片搜索结果的 PhotoViewer -->
    <PhotoViewer
      v-if="photoViewerPhotos.length > 0"
      :photos="photoViewerPhotos"
      :visible="photoViewerVisible"
      :start-index="photoViewerIndex"
      :origin-rect="photoViewerOriginRect"
      @update:visible="photoViewerVisible = $event"
    />
    <!-- 人脸对应照片的 PhotoViewer（保留以便后续扩展） -->
    <!--
    <PhotoViewer
      v-if="facePhotoViewerPhotos.length > 0"
      :photos="facePhotoViewerPhotos"
      :visible="viewerVisible"
      :start-index="viewerIndex"
      :origin-rect="viewerOriginRect"
      @update:visible="viewerVisible = $event"
    />
    -->
  </div>
</template>
