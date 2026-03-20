<script setup lang="ts">
import { ref, onMounted, computed, watch, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { albumApi, aiApi, personApi, configApi, aiSearchApi } from '@/api'
import type { AiSearchResponse, AiSearchSuggestionAction } from '@/api'
import { useAuthStore } from '@/stores/auth'
import { usePhotoStore } from '@/stores/photo'
import { useLanguageStore } from '@/stores/language'
import AppHeader from '@/components/AppHeader.vue'
import SettingsMenu from '@/components/SettingsMenu.vue'
import PhotoViewer from '@/components/PhotoViewer.vue'
import AlbumCard from '@/components/AlbumCard.vue'
import type { Photo } from '@/stores/photo'

const route = useRoute()
const router = useRouter()
const photoStore = usePhotoStore()
const languageStore = useLanguageStore()

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
  fileSize?: number
  contentHash?: string
  format?: string
  colorCategory?: string
  colorPalette?: string[]
  exifData?: Record<string, any>
  cameraMake?: string
  cameraModel?: string
  lensModel?: string
  focalLength?: string
  focalLengthMm?: number
  aperture?: string
  apertureValue?: number
  shutterSpeed?: string
  shutterSpeedSeconds?: number
  iso?: number
  qualityScore?: number
  focusX?: number
  focusY?: number
  viewCount?: number
  likeCount?: number
  isFeatured?: boolean
  location?: string
  dominantColor?: string
  // AI 评分
  aiOverallScore?: number
  aiTechnicalScore?: number
  aiCompositionScore?: number
  aiAppealScore?: number
  aiStrengths?: string[]
  aiWeaknesses?: string[]
  aiSuggestions?: string[]
  // 标签和人物
  tags?: Array<{ id: number; name: string; color?: string }>
  faces?: Array<{
    id: number
    personId?: number
    personName?: string
    x?: number
    y?: number
    width?: number
    height?: number
    isConfirmed?: boolean
    confidence?: number
  }>
  assignedPersonId?: number
  assignedPersonName?: string
  isHidden?: boolean
  createdAt?: string
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
const editableKeyword = ref('')
const faceId = ref<number | null>(null)
const tagId = ref<number | null>(null)
const tagName = ref<string>('')
const clusterThreshold = ref<number>(0.7)

// 监听关键词变化，更新页面标题
watch(keyword, (newKeyword) => {
  editableKeyword.value = newKeyword ? decodeURIComponent(newKeyword) : ''
  if (newKeyword) {
    const baseTitle = languageStore.language === 'zh' ? '光忆集' : 'Aurellic Memoriq'
    document.title = `${baseTitle} - 搜索: ${decodeURIComponent(newKeyword)}`
  } else {
    document.title = languageStore.language === 'zh' ? '光忆集' : 'Aurellic Memoriq'
  }
})

// 加载聚类阈值配置
const loadClusterThreshold = async () => {
  try {
    const response = await configApi.getFaceClusterThreshold()
    clusterThreshold.value = response.data.faceClusterThreshold || 0.7
  } catch (e) {
    console.error('加载聚类阈值失败:', e)
  }
}

// 清理滚动监听
onUnmounted(() => {
  window.removeEventListener('scroll', handleScroll)
  if (scrollTimer) clearTimeout(scrollTimer)
})
const loading = ref(true)
const albums = ref<AlbumDTO[]>([])
const persons = ref<PersonSummary[]>([])
const photos = ref<PhotoDTO[]>([])
const facePhotos = ref<FacePhoto[]>([])
const similarFaces = ref<FaceFace[]>([])
const hasSearched = ref(false)
const searchMode = ref<'keyword' | 'face' | 'tag'>('keyword')

// AI 搜索相关状态
const aiSearchEnabled = ref(false)
const aiSearchResult = ref<AiSearchResponse | null>(null)
const aiPhotos = ref<PhotoDTO[]>([])
const aiPage = ref(0)
const aiHasMore = ref(false)
const aiLoadingMore = ref(false)

type DisplaySuggestion = AiSearchSuggestionAction | string

// 标签搜索相关
const tagPhotos = ref<PhotoDTO[]>([])
const tagPage = ref(0)
const tagHasMore = ref(true)
const tagLoadingMore = ref(false)

onMounted(async () => {
  // 检查 AI 搜索是否启用
  try {
    const statusRes = await configApi.getAiSearchStatus()
    aiSearchEnabled.value = statusRes.data.enabled
  } catch {
    aiSearchEnabled.value = false
  }

  // 先加载聚类阈值配置
  await loadClusterThreshold()

  // 从查询参数获取关键词
  keyword.value = (route.query.q as string) || (route.params.keyword as string) || ''
  faceId.value = route.query.faceId ? parseInt(route.query.faceId as string, 10) : null
  tagId.value = route.query.tagId ? parseInt(route.query.tagId as string, 10) : null
  tagName.value = (route.query.tagName as string) || ''

  if (faceId.value && !isNaN(faceId.value)) {
    searchMode.value = 'face'
    await searchSimilarFaces()
  } else if (tagId.value && !isNaN(tagId.value)) {
    searchMode.value = 'tag'
    await searchByTag()
  } else if (keyword.value) {
    searchMode.value = 'keyword'
    await search()
  } else {
    loading.value = false
  }

  // 添加滚动监听
  window.addEventListener('scroll', handleScroll, { passive: true })
})

// 监听路由变化，支持 /search?q=xxx 和 /s/xxx 之间的切换
watch(
  () => [route.query.q, route.params.keyword, route.query.faceId, route.query.tagId],
  async ([newQ, newKeyword, newFaceId, newTagId]) => {
    const newKeywordValue = (newQ as string) || (newKeyword as string) || ''
    const newFaceIdValue = newFaceId ? parseInt(newFaceId as string, 10) : null
    const newTagIdValue = newTagId ? parseInt(newTagId as string, 10) : null

    keyword.value = newKeywordValue
    faceId.value = newFaceIdValue && !isNaN(newFaceIdValue) ? newFaceIdValue : null
    tagId.value = newTagIdValue && !isNaN(newTagIdValue) ? newTagIdValue : null

    if (faceId.value && !isNaN(faceId.value)) {
      searchMode.value = 'face'
      await searchSimilarFaces()
    } else if (tagId.value && !isNaN(tagId.value)) {
      searchMode.value = 'tag'
      await searchByTag()
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
      tagPhotos.value = []
      aiSearchResult.value = null
      aiPhotos.value = []
    }
  }
)

const search = async () => {
  loading.value = true
  hasSearched.value = true

  if (aiSearchEnabled.value) {
    await aiSearch()
  } else {
    await standardSearch()
  }
}

const submitKeywordSearch = async () => {
  const nextKeyword = editableKeyword.value.trim()
  if (!nextKeyword) {
    return
  }

  if (nextKeyword === keyword.value && searchMode.value === 'keyword') {
    await search()
    return
  }

  await router.push({
    path: '/search',
    query: { q: nextKeyword }
  })
}

const applySuggestion = async (suggestion: DisplaySuggestion) => {
  if (typeof suggestion === 'string') {
    editableKeyword.value = suggestion
    await submitKeywordSearch()
    return
  }

  if (!aiSearchResult.value?.parsedIntent) {
    editableKeyword.value = suggestion.label
    await submitKeywordSearch()
    return
  }

  loading.value = true
  hasSearched.value = true
  aiPage.value = 0
  aiPhotos.value = []

  try {
    const decodedKeyword = decodeURIComponent(keyword.value)
    const res = await aiSearchApi.execute(decodedKeyword, aiSearchResult.value.parsedIntent, suggestion, 0, 30)
    applyAiSearchResponse(res.data)
  } catch (e) {
    console.error('执行搜索建议失败，回退到普通搜索词:', e)
    editableKeyword.value = suggestion.label
    await submitKeywordSearch()
  } finally {
    loading.value = false
  }
}

const applyAiSearchResponse = (data: AiSearchResponse) => {
  aiSearchResult.value = data
  aiPhotos.value = data.photos || []
  aiHasMore.value = aiPhotos.value.length >= 30
  albums.value = data.albums || []
  persons.value = data.persons || []
}

const standardSearch = async () => {
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

const aiSearch = async () => {
  aiSearchResult.value = null
  aiPhotos.value = []
  aiPage.value = 0

  try {
    const decodedKeyword = decodeURIComponent(keyword.value)
    const res = await aiSearchApi.search(decodedKeyword, 0, 30)

    if (!res.data.aiSearchEnabled) {
      // AI搜索未启用，fallback到普通搜索
      aiSearchEnabled.value = false
      await standardSearch()
      return
    }

    applyAiSearchResponse(res.data)
  } catch (e) {
    console.error('AI搜索失败, fallback到普通搜索:', e)
    await standardSearch()
  } finally {
    loading.value = false
  }
}

const loadMoreAiPhotos = async () => {
  if (aiLoadingMore.value || !aiHasMore.value) return
  aiLoadingMore.value = true
  try {
    const nextPage = aiPage.value + 1
    const decodedKeyword = decodeURIComponent(keyword.value)
    const res = await aiSearchApi.search(decodedKeyword, nextPage, 30)
    const newPhotos = res.data.photos || []
    aiPhotos.value = [...aiPhotos.value, ...newPhotos]
    aiPage.value = nextPage
    aiHasMore.value = newPhotos.length >= 30
  } catch (e) {
    console.error('加载更多AI搜索结果失败:', e)
  } finally {
    aiLoadingMore.value = false
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

    // 获取相似人脸，使用聚类阈值
    const response = await aiApi.findSimilarFaces(faceId.value!, 20, clusterThreshold.value)
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

// 标签搜索
const searchByTag = async () => {
  loading.value = true
  hasSearched.value = true
  tagPage.value = 0
  tagPhotos.value = []
  tagHasMore.value = true

  try {
    const response = await photoStore.filterPhotos({ tagIds: [tagId.value!] }, 0, 30)
    const photosData = response as any
    tagPhotos.value = photosData?.content || photosData || []
    tagHasMore.value = tagPhotos.value.length >= 30
  } catch (e) {
    console.error('标签搜索失败:', e)
    tagPhotos.value = []
    tagHasMore.value = false
  } finally {
    loading.value = false
  }
}

// 加载更多标签搜索结果
const loadMoreTagPhotos = async () => {
  if (tagLoadingMore.value || !tagHasMore.value) return

  tagLoadingMore.value = true
  try {
    const nextPage = tagPage.value + 1
    const response = await photoStore.filterPhotos({ tagIds: [tagId.value!] }, nextPage, 30)
    const newPhotos = (response as any)?.content || (response as any) || []
    tagPhotos.value = [...tagPhotos.value, ...newPhotos]
    tagPage.value = nextPage
    tagHasMore.value = newPhotos.length >= 30
  } catch (e) {
    console.error('加载更多标签照片失败:', e)
  } finally {
    tagLoadingMore.value = false
  }
}

// 滚动加载更多
let scrollTimer: ReturnType<typeof setTimeout> | null = null
const handleScroll = () => {
  if (scrollTimer) return
  scrollTimer = setTimeout(() => {
    if (searchMode.value !== 'tag' && !(searchMode.value === 'keyword' && aiSearchEnabled.value)) {
      scrollTimer = null
      return
    }

    const scrollTop = window.scrollY || document.documentElement.scrollTop
    const windowHeight = window.innerHeight
    const documentHeight = document.documentElement.scrollHeight

    if (scrollTop + windowHeight >= documentHeight - 500) {
      if (searchMode.value === 'tag') {
        loadMoreTagPhotos()
      } else if (aiSearchEnabled.value && aiHasMore.value) {
        loadMoreAiPhotos()
      }
    }
    scrollTimer = null
  }, 100)
}

const keywordPhotoResults = computed(() => {
  if (searchMode.value !== 'keyword') {
    return [] as PhotoDTO[]
  }
  if (aiSearchEnabled.value && aiSearchResult.value) {
    return aiPhotos.value
  }
  return photos.value
})

const displaySuggestions = computed<DisplaySuggestion[]>(() => {
  if (!aiSearchResult.value) {
    return []
  }
  if (aiSearchResult.value.suggestionActions?.length) {
    return aiSearchResult.value.suggestionActions
  }
  return aiSearchResult.value.suggestions || []
})

const hasAiConditionSummary = computed(() => {
  const parsedIntent = aiSearchResult.value?.parsedIntent
  return Boolean(
    aiSearchResult.value?.matchedPersonName ||
    aiSearchResult.value?.matchedTagNames?.length ||
    parsedIntent?.startDate ||
    parsedIntent?.colorCategory ||
    parsedIntent?.cameraModel ||
    parsedIntent?.lensModel ||
    parsedIntent?.filenameKeywords?.length ||
    parsedIntent?.should?.length ||
    parsedIntent?.mustNot?.length
  )
})

const showKeywordPhotoEmptyHint = computed(() => {
  if (searchMode.value !== 'keyword' || !aiSearchEnabled.value || !aiSearchResult.value) {
    return false
  }
  return keywordPhotoResults.value.length === 0 && (
    Boolean(aiSearchResult.value.answer) ||
    albums.value.length > 0 ||
    persons.value.length > 0
  )
})

const getSuggestionKey = (suggestion: DisplaySuggestion) => {
  return typeof suggestion === 'string' ? suggestion : `${suggestion.actionType}:${suggestion.label}`
}

const getSuggestionLabel = (suggestion: DisplaySuggestion) => {
  return typeof suggestion === 'string' ? suggestion : suggestion.label
}

const isRelaxSuggestion = (suggestion: DisplaySuggestion) => {
  return typeof suggestion !== 'string' && suggestion.actionType === 'remove_condition_types'
}

const isNarrowSuggestion = (suggestion: DisplaySuggestion) => {
  return typeof suggestion !== 'string' && suggestion.actionType === 'keep_only_condition_types'
}

const getSuggestionButtonClass = (suggestion: DisplaySuggestion) => {
  if (isRelaxSuggestion(suggestion)) {
    return 'border-emerald-200 text-emerald-700 hover:border-emerald-400 hover:text-emerald-800 dark:border-emerald-800 dark:text-emerald-300'
  }
  if (isNarrowSuggestion(suggestion)) {
    return 'border-sky-200 text-sky-700 hover:border-sky-400 hover:text-sky-800 dark:border-sky-800 dark:text-sky-300'
  }
  return 'border-gray-200 text-gray-700 hover:border-blue-400 hover:text-blue-600 dark:border-gray-700 dark:text-gray-200 dark:hover:text-blue-300'
}

const displayAnswerText = computed(() => {
  const answer = aiSearchResult.value?.answer?.trim()
  if (!answer) {
    return ''
  }
  return answer.replace(/^检索结论[:：]\s*/, '')
})

const formatDateRangeLabel = (startDate?: string, endDate?: string) => {
  if (!startDate) {
    return ''
  }

  const currentYear = new Date().getFullYear()
  if (endDate && startDate.endsWith('-01-01') && endDate.endsWith('-12-31')) {
    const startYear = Number.parseInt(startDate.slice(0, 4), 10)
    const endYear = Number.parseInt(endDate.slice(0, 4), 10)
    if (startYear === endYear) {
      if (startYear === currentYear) return '今年全年'
      if (startYear === currentYear - 1) return '去年全年'
      if (startYear === currentYear - 2) return '前年全年'
      return `${startYear} 全年`
    }
  }

  return `${startDate} ~ ${endDate || '至今'}`
}

const hasResults = computed(() => {
  if (searchMode.value === 'face') {
    return similarFaces.value.length > 0 || facePhotos.value.length > 0
  }
  if (searchMode.value === 'tag') {
    return tagPhotos.value.length > 0
  }
  if (aiSearchEnabled.value && aiSearchResult.value) {
    return Boolean(aiSearchResult.value.answer) || aiPhotos.value.length > 0 || albums.value.length > 0 || persons.value.length > 0
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

// 关键词搜索结果 PhotoViewer 相关状态
const keywordViewerVisible = ref(false)
const keywordViewerIndex = ref(0)
const keywordViewerOriginRect = ref<{ top: number; left: number; width: number; height: number } | null>(null)

const keywordViewerPhotos = computed<Photo[]>(() => {
  return keywordPhotoResults.value.map((photo) => ({
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
    folderPath: photo.folderPath || '',
    // EXIF 信息
    cameraMake: photo.cameraMake,
    cameraModel: photo.cameraModel,
    lensModel: photo.lensModel,
    focalLength: photo.focalLength,
    focalLengthMm: photo.focalLengthMm,
    aperture: photo.aperture,
    apertureValue: photo.apertureValue,
    shutterSpeed: photo.shutterSpeed,
    shutterSpeedSeconds: photo.shutterSpeedSeconds,
    iso: photo.iso,
    location: photo.location || '',
    format: photo.format,
    colorCategory: photo.colorCategory,
    colorPalette: photo.colorPalette,
    exifData: photo.exifData,
    fileSize: photo.fileSize,
    qualityScore: photo.qualityScore,
    focusX: photo.focusX,
    focusY: photo.focusY,
    viewCount: photo.viewCount || 0,
    likeCount: photo.likeCount || 0,
    isFeatured: photo.isFeatured || false,
    // AI 评分
    aiOverallScore: photo.aiOverallScore,
    aiTechnicalScore: photo.aiTechnicalScore,
    aiCompositionScore: photo.aiCompositionScore,
    aiAppealScore: photo.aiAppealScore,
    aiStrengths: photo.aiStrengths,
    aiWeaknesses: photo.aiWeaknesses,
    aiSuggestions: photo.aiSuggestions,
    // 标签和人物
    tags: photo.tags,
    faces: photo.faces,
    assignedPersonId: photo.assignedPersonId,
    assignedPersonName: photo.assignedPersonName,
    // 其他
    contentHash: photo.contentHash,
    dominantColor: photo.dominantColor,
    isHidden: photo.isHidden,
    createdAt: photo.createdAt,
  }))
})

const openKeywordPhotoViewer = (index: number, e: MouseEvent) => {
  keywordViewerIndex.value = index
  const img = (e.target as HTMLElement).closest('img') as HTMLImageElement | null
  const rectSource = img || (e.currentTarget as HTMLElement | null)
  if (rectSource) {
    const rect = rectSource.getBoundingClientRect()
    keywordViewerOriginRect.value = {
      top: rect.top,
      left: rect.left,
      width: rect.width,
      height: rect.height
    }
  } else {
    keywordViewerOriginRect.value = null
  }
  keywordViewerVisible.value = true
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
      <div class="mb-8">
        <div v-if="searchMode === 'keyword'" class="flex flex-col sm:flex-row gap-3">
          <div class="flex-1 flex items-center gap-3 px-4 py-3 bg-white dark:bg-gray-800 rounded-2xl border border-gray-200 dark:border-gray-700 shadow-sm">
            <svg class="w-5 h-5 text-gray-400 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
            </svg>
            <input
              v-model="editableKeyword"
              type="text"
              placeholder="搜索相册、人物、照片..."
              class="flex-1 bg-transparent text-base text-gray-800 dark:text-white outline-none placeholder-gray-400 dark:placeholder-gray-500"
              @keyup.enter="submitKeywordSearch"
            />
          </div>
          <button
            @click="submitKeywordSearch"
            class="px-5 py-3 bg-blue-500 hover:bg-blue-600 text-white rounded-2xl text-sm font-medium transition-colors"
          >
            搜索
          </button>
        </div>
        <p class="text-gray-600 dark:text-gray-400" v-else-if="faceId">
          相似人脸搜索 (人脸ID: {{ faceId }})
        </p>
        <p class="text-gray-600 dark:text-gray-400" v-else-if="tagId">
          标签: {{ tagName || ('#' + tagId) }}
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
        <!-- AI搜索结果 -->
        <div v-if="aiSearchEnabled && aiSearchResult && searchMode === 'keyword'" class="mb-10">
          <div v-if="aiSearchResult.answer" class="mb-3 rounded-xl border border-amber-200/80 bg-amber-50/90 px-3 py-2.5 dark:border-amber-800 dark:bg-amber-900/20">
            <div class="flex items-start gap-2.5">
              <div class="mt-0.5 h-8 w-1 rounded-full bg-amber-400/80 dark:bg-amber-500/70"></div>
              <div class="min-w-0 flex-1">
                <div class="mb-0.5 flex items-center gap-1.5">
                  <span class="inline-flex items-center rounded-full bg-amber-100 px-2 py-0.5 text-[10px] font-semibold text-amber-700 dark:bg-amber-900/40 dark:text-amber-300">
                    检索结论
                  </span>
                </div>
                <p class="text-sm leading-5 text-amber-900 dark:text-amber-100">
                  {{ displayAnswerText }}
                </p>
              </div>
            </div>
          </div>

          <div v-if="aiSearchResult.relaxedReason" class="mb-4 p-3 bg-slate-50 dark:bg-slate-800/70 rounded-lg border border-slate-200 dark:border-slate-700">
            <p class="text-xs text-slate-600 dark:text-slate-300">
              {{ aiSearchResult.relaxedReason }}
            </p>
          </div>

          <div v-if="hasAiConditionSummary" class="flex flex-wrap gap-2 mb-4">
            <span v-if="aiSearchResult.matchedPersonName" class="px-3 py-1 bg-green-100 dark:bg-green-900/30 text-green-700 dark:text-green-300 rounded-full text-xs font-medium">
              人物: {{ aiSearchResult.matchedPersonName }}
            </span>
            <span v-for="tag in (aiSearchResult.matchedTagNames || [])" :key="tag" class="px-3 py-1 bg-purple-100 dark:bg-purple-900/30 text-purple-700 dark:text-purple-300 rounded-full text-xs font-medium">
              {{ tag }}
            </span>
            <span v-if="aiSearchResult.parsedIntent?.startDate" class="px-3 py-1 bg-gray-100 dark:bg-gray-700 text-gray-700 dark:text-gray-300 rounded-full text-xs font-medium">
              {{ formatDateRangeLabel(aiSearchResult.parsedIntent.startDate, aiSearchResult.parsedIntent.endDate) }}
            </span>
            <span v-if="aiSearchResult.parsedIntent?.colorCategory" class="px-3 py-1 bg-pink-100 dark:bg-pink-900/30 text-pink-700 dark:text-pink-300 rounded-full text-xs font-medium">
              色彩: {{ aiSearchResult.parsedIntent.colorCategory }}
            </span>
            <span v-if="aiSearchResult.parsedIntent?.cameraModel" class="px-3 py-1 bg-gray-100 dark:bg-gray-700 text-gray-700 dark:text-gray-300 rounded-full text-xs font-medium">
              {{ aiSearchResult.parsedIntent.cameraModel }}
            </span>
            <span v-if="aiSearchResult.parsedIntent?.lensModel" class="px-3 py-1 bg-gray-100 dark:bg-gray-700 text-gray-700 dark:text-gray-300 rounded-full text-xs font-medium">
              {{ aiSearchResult.parsedIntent.lensModel }}
            </span>
            <span v-if="aiSearchResult.parsedIntent?.filenameKeywords?.length" class="px-3 py-1 bg-cyan-100 dark:bg-cyan-900/30 text-cyan-700 dark:text-cyan-300 rounded-full text-xs font-medium">
              文件名: {{ aiSearchResult.parsedIntent.filenameKeywords.join(', ') }}
            </span>
            <span v-if="aiSearchResult.parsedIntent?.should?.length" class="px-3 py-1 bg-sky-100 dark:bg-sky-900/30 text-sky-700 dark:text-sky-300 rounded-full text-xs font-medium">
              满足其一
            </span>
            <span v-if="aiSearchResult.parsedIntent?.mustNot?.length" class="px-3 py-1 bg-rose-100 dark:bg-rose-900/30 text-rose-700 dark:text-rose-300 rounded-full text-xs font-medium">
              已排除部分条件
            </span>
          </div>

          <div v-if="displaySuggestions.length" class="mb-4 flex flex-wrap gap-2">
            <button
              v-for="suggestion in displaySuggestions"
              :key="getSuggestionKey(suggestion)"
              @click="applySuggestion(suggestion)"
              class="inline-flex items-center gap-1.5 px-3 py-1.5 bg-white dark:bg-gray-800 border rounded-full text-xs transition-colors"
              :class="getSuggestionButtonClass(suggestion)"
            >
              <svg v-if="isRelaxSuggestion(suggestion)" class="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 12h16m-4-4l4 4-4 4" />
              </svg>
              <svg v-else-if="isNarrowSuggestion(suggestion)" class="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M20 12H4m4-4l-4 4 4 4" />
              </svg>
              <svg v-else class="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
              </svg>
              {{ getSuggestionLabel(suggestion) }}
            </button>
          </div>
        </div>

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

        <!-- 标签搜索结果 -->
        <div v-if="searchMode === 'tag' && tagPhotos.length > 0" class="mb-10">
          <h2 class="text-xl font-semibold text-gray-800 dark:text-white mb-4 flex items-center">
            <svg class="w-6 h-6 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z" />
            </svg>
            照片 ({{ tagPhotos.length }})
          </h2>
          <div class="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6 gap-4">
            <a
              v-for="photo in tagPhotos"
              :key="photo.id"
              :href="`/photo/${photo.albumId}/${photo.id}`"
              target="_blank"
              class="bg-white dark:bg-gray-800 rounded-lg overflow-hidden shadow-md hover:shadow-lg transition-shadow cursor-pointer group block"
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
            </a>
          </div>
          <!-- 加载更多 -->
          <div v-if="tagHasMore" class="flex justify-center mt-6">
            <button
              v-if="!tagLoadingMore"
              @click="loadMoreTagPhotos"
              class="px-6 py-2 bg-blue-500 hover:bg-blue-600 text-white rounded-full text-sm font-medium transition-colors"
            >
              加载更多
            </button>
            <div v-else class="flex items-center gap-2 text-gray-500">
              <div class="animate-spin rounded-full h-5 w-5 border-b-2 border-blue-500"></div>
              <span>加载中...</span>
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

        <!-- 相册结果 -->
        <div v-if="searchMode === 'keyword' && albums.length > 0" class="mb-10">
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

        <div v-if="showKeywordPhotoEmptyHint" class="mb-10 p-4 rounded-2xl border border-slate-200 dark:border-slate-700 bg-slate-50 dark:bg-slate-800/60">
          <p class="text-sm text-slate-600 dark:text-slate-300 leading-6">
            这次搜索找到了说明、人物或相册结果，但还没有命中具体照片；可以试试上面的建议词继续缩放范围。
          </p>
        </div>

        <!-- 照片结果 -->
        <div v-if="searchMode === 'keyword' && keywordPhotoResults.length > 0" class="mb-10">
          <h2 class="text-xl font-semibold text-gray-800 dark:text-white mb-4 flex items-center">
            <svg class="w-6 h-6 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z" />
            </svg>
            照片 ({{ keywordPhotoResults.length }})
          </h2>
          <div class="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6 gap-4">
            <div
              v-for="(photo, index) in keywordPhotoResults"
              :key="photo.id"
              class="bg-white dark:bg-gray-800 rounded-lg overflow-hidden shadow-md hover:shadow-lg transition-shadow cursor-pointer group"
              @click="openKeywordPhotoViewer(index, $event)"
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
          <div v-if="aiSearchEnabled && aiSearchResult && aiHasMore" class="flex justify-center mt-6">
            <button
              v-if="!aiLoadingMore"
              @click="loadMoreAiPhotos"
              class="px-6 py-2 bg-blue-500 hover:bg-blue-600 text-white rounded-full text-sm font-medium transition-colors"
            >
              加载更多
            </button>
            <div v-else class="flex items-center gap-2 text-gray-500">
              <div class="animate-spin rounded-full h-5 w-5 border-b-2 border-blue-500"></div>
              <span>加载中...</span>
            </div>
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
    <!-- 关键词搜索结果的 PhotoViewer -->
    <PhotoViewer
      v-if="keywordViewerPhotos.length > 0"
      :photos="keywordViewerPhotos"
      :visible="keywordViewerVisible"
      :start-index="keywordViewerIndex"
      :origin-rect="keywordViewerOriginRect"
      @update:visible="keywordViewerVisible = $event"
    />
  </div>
</template>
