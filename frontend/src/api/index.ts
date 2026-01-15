import axios from 'axios'

export const api = axios.create({
  baseURL: '/api',
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

