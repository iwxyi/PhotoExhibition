import axios from 'axios'

// 类型定义
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
  takenAt?: string
  qualityScore?: number
  focusX?: number
  focusY?: number
  viewCount: number
  likeCount: number
  isFeatured: boolean
  dominantColor?: string

  // AI评分相关字段
  aiOverallScore?: number // AI综合评分
  aiTechnicalScore?: number // AI技术评分
  aiCompositionScore?: number // AI构图评分
  aiAppealScore?: number // AI吸引力评分
  aiStrengths?: string[] // AI分析优点
  aiWeaknesses?: string[] // AI分析不足
  aiSuggestions?: string[] // AI改进建议

  // AI增强分析字段
  sceneAnalysis?: any[] // 场景识别结果
  emotionAnalysis?: any[] // 情感分析结果
  primaryScene?: string // 主要场景分类
  primaryEmotion?: string // 主要情感
  sceneConfidence?: number // 场景识别置信度
  emotionConfidence?: number // 情感分析置信度

  tags?: Tag[]
  faces?: FaceFace[]
  createdAt: string
}

export interface Tag {
  id: number
  name: string
  color?: string
}

// 获取服务器地址，支持多种配置方式
function getServerUrl(): string {
  // 1. 从URL参数获取（如 ?server=192.168.1.100:6060）
  const urlParams = new URLSearchParams(window.location.search)
  const serverParam = urlParams.get('server')
  if (serverParam) {
    return `http://${serverParam}/api`
  }

  // 2. 从localStorage获取用户设置的服务器地址
  const savedServer = localStorage.getItem('server_url')
  if (savedServer) {
    return `${savedServer}/api`
  }

  // 3. 开发环境的代理会处理/api请求，所以使用相对路径
  // 生产环境或手机访问时，默认使用当前域名的/api
  if (window.location.hostname === 'localhost' || window.location.hostname === '127.0.0.1') {
    return '/api'  // 开发环境使用代理
  } else {
    // 生产环境使用当前域名
    return `${window.location.protocol}//${window.location.host}/api`
  }
}

export const api = axios.create({
  baseURL: getServerUrl(),
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json'
  }
})

// 请求拦截器
api.interceptors.request.use(
  config => {
    // 添加认证token
    const token = localStorage.getItem('admin_token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  error => {
    return Promise.reject(error)
  }
)

// 响应拦截器
api.interceptors.response.use(
  response => {
    return response
  },
  error => {
    console.error('API Error:', error)
    return Promise.reject(error)
  }
)

// 人物相关API
export interface PersonSummary {
  id: number
  name: string
  description?: string
  samplePhotoId?: number
  sampleFaceId?: number
  sampleThumbnailPath?: string
  sampleOriginalPath?: string
  sampleConfidence?: number
  faceCount?: number // 人脸数量（即照片数量）
  albumCount?: number // 相册数量
  createdAt: string
  updatedAt: string
}

export interface AlbumRecommendation {
  albumId: number
  albumName: string
  albumPath: string
  photoCount: number
  similarFaceCount: number
  claimedPhotoCount?: number
  takenAt: string
  coverImagePath?: string
  coverImagePath1?: string
  coverImagePath2?: string
  coverImagePath3?: string
}

// 评论相关API
export interface CommentRequest {
  albumId: number
  parentId?: number
  nickname: string
  email: string
  content: string
}

export interface CommentDTO {
  id: number
  albumId: number
  parentId?: number
  nickname: string
  email: string
  content: string
  createdAt: string
  updatedAt: string
  deleted: boolean
  replies?: CommentDTO[]
}

export const commentApi = {
  // 创建评论
  createComment: (data: CommentRequest) => api.post<CommentDTO>('/comments', data),

  // 删除评论
  deleteComment: (commentId: number, email: string) => api.delete(`/comments/${commentId}?email=${encodeURIComponent(email)}`),

  // 获取相册评论（分页）
  getAlbumComments: (albumId: number, page = 0, size = 10) => api.get(`/comments/albums/${albumId}?page=${page}&size=${size}`),

  // 获取评论回复
  getCommentReplies: (parentId: number) => api.get(`/comments/${parentId}/replies`),

  // 检查用户是否已经对指定评论回复过
  hasUserRepliedToComment: (parentId: number, email?: string) => {
    const params = new URLSearchParams()
    if (email) params.append('email', email)
    return api.get<boolean>(`/comments/${parentId}/has-replied?${params.toString()}`)
  },
  batchHasUserRepliedToComments: (commentIds: number[], email?: string) => {
    const params = new URLSearchParams()
    if (email) params.append('email', email)
    return api.post<Record<number, boolean>>(`/comments/batch-has-replied?${params.toString()}`, commentIds)
  },

  // 检查用户今天是否已经对指定相册发表过评论
  hasUserCommentedOnAlbumToday: (albumId: number, email?: string) => {
    const params = new URLSearchParams()
    if (email) params.append('email', email)
    return api.get<boolean>(`/comments/albums/${albumId}/has-commented-today?${params.toString()}`)
  },

  // 获取相册评论总数
  getAlbumCommentCount: (albumId: number) => api.get<number>(`/comments/albums/${albumId}/count`)
}

// 人物相关API
export const personApi = {
  // 获取人物列表（含代表头像）
  getPersonsWithSample: () => api.get<PersonSummary[]>('/public/persons/with-sample'),

  // 获取单个人物信息
  getPerson: (personId: number) => api.get<PersonSummary>(`/public/persons/${personId}`),

  // 获取人物的代表图片（用于列表显示）
  getPersonSamplePhotos: (personId: number) => api.get<FaceFace[]>(`/public/persons/${personId}/sample-photos`),

  // 获取人物的相册推荐
  getPersonAlbumRecommendations: (personId: number) => api.get<AlbumRecommendation[]>(`/public/persons/${personId}/album-recommendations`),

  // 获取人物被指派的图片（分页）
  getPersonAssignedPhotos: (personId: number, page = 0, size = 20) => api.get(`/public/persons/${personId}/assigned-photos?page=${page}&size=${size}`),

  // 获取人物的所有照片（人脸所在照片）
  getPersonPhotos: (personId: number, page = 0, size = 20) => api.get(`/public/persons/${personId}/photos?page=${page}&size=${size}`),

  // 设置人物的样例照片
  setSamplePhoto: (personId: number, faceId: number) => api.post<Person>(`/admin/persons/${personId}/set-sample`, { faceId })
}

// AI增强分析相关API
export interface SimilarPhotoResult {
  photoId: number
  similarityScore: number
  matchReasons: string[]
  photo: {
    id: number
    filename: string
    thumbnailPath?: string
    takenAt?: string
    albumId: number
  }
}

export const aiApi = {
  // 搜索相似照片
  findSimilarPhotos: (photoId: number, limit = 10) => api.get<{
    success: boolean
    data: SimilarPhotoResult[]
    total: number
  }>(`/admin/photos/${photoId}/similar?limit=${limit}`)
}

// 相册相关API
export const albumApi = {
  // 设置相册自定义封面
  setAlbumCover: (albumId: number, coverImageIds: number[]) =>
    api.put(`/albums/${albumId}/cover`, { coverImageIds })
}

