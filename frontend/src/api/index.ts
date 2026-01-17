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
  tags?: Tag[]
  faces?: FaceFace[]
  createdAt: string
}

export interface Tag {
  id: number
  name: string
  color?: string
}

export const api = axios.create({
  baseURL: 'http://localhost:6061/api',
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
  takenAt: string
  coverImagePath?: string
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
  getPersonPhotos: (personId: number, page = 0, size = 20) => api.get(`/public/persons/${personId}/photos?page=${page}&size=${size}`)
}

