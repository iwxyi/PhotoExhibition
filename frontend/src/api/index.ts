import axios from 'axios'
import { getCurrentPublicSlug, shouldAttachUserSlug } from '@/utils/publicRoute'

export function getEffectiveAuthToken(): string | null {
  const authToken = localStorage.getItem('auth_token')
  const adminToken = localStorage.getItem('admin_token')

  if (adminToken && !authToken) {
    localStorage.setItem('auth_token', adminToken)
  }
  // `admin_token` was used by earlier releases. Migrate it once, rather than
  // allowing a second storage key to silently override the active session.
  localStorage.removeItem('admin_token')

  return authToken || adminToken || null
}

export function clearStoredAuthSession() {
  localStorage.removeItem('auth_token')
  localStorage.removeItem('admin_token')
  localStorage.removeItem('auth_username')
  localStorage.removeItem('admin_username')
  localStorage.removeItem('auth_role')
  localStorage.removeItem('auth_user_id')
  localStorage.removeItem('auth_slug')
  localStorage.removeItem('auth_nickname')
  localStorage.removeItem('auth_phone')
  localStorage.removeItem('auth_phone_verified')
  localStorage.removeItem('auth_email')
  localStorage.removeItem('auth_email_verified')
  localStorage.removeItem('auth_project_name_zh')
  localStorage.removeItem('auth_project_name_en')
  localStorage.removeItem('auth_avatar_path')
  localStorage.removeItem('auth_multi_user_enabled')
  localStorage.removeItem('auth_sms_login_enabled')
  localStorage.removeItem('auth_email_code_login_enabled')
  localStorage.removeItem('auth_current_vip_plan_id')
  localStorage.removeItem('auth_current_vip_plan_name')
  localStorage.removeItem('auth_current_vip_plan_code')
  localStorage.removeItem('auth_vip_expire_at')
  localStorage.removeItem('auth_effective_storage_quota_bytes')
  localStorage.removeItem('auth_storage_used_bytes')
  delete api.defaults.headers.common['Authorization']
}

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
  photoThumbnailPath?: string  // medium 缩略图（兼容旧字段）
  photoMediumThumbPath?: string  // medium 缩略图
  photoSmallThumbPath?: string   // small 缩略图
  photoOriginalPath?: string
  photoWidth?: number  // 照片宽度
  photoHeight?: number // 照片高度
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
  contentHash?: string
  canonicalPhotoId?: number | null
  canonicalSource?: boolean
  duplicateContent?: boolean
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

export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  first: boolean
  last: boolean
}

export interface UploadPrecheckResponse {
  contentHash: string
  exists: boolean
  canonicalPhotoId?: number | null
  canonicalUserId?: number | null
  canonicalSource?: boolean
  sameOwner: boolean
  visible: boolean
  reusableMetadata: boolean
  reusableDerivatives: boolean
  photoId?: number | null
  originalPath?: string | null
  thumbnailPath?: string | null
  message: string
}

function getServerUrl(): string {
  // Keep authenticated requests same-origin. Vite proxies this path in
  // development and production deployments serve it from the current origin.
  return '/api'
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
    const token = getEffectiveAuthToken()
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }

    const currentSlug = getCurrentPublicSlug()
    if (currentSlug && config.url && shouldAttachUserSlug(config.url)) {
      const currentParams = config.params || {}
      if (!('userSlug' in currentParams)) {
        config.params = { ...currentParams, userSlug: currentSlug }
      }
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
    // 令牌失效时清理本地会话，避免页面继续显示“已登录”但所有后台接口
    // 都收到 401。后台页面直接回登录页；公开页面仅清理会话，不强制跳转。
    if (error?.response?.status === 401 && typeof window !== 'undefined') {
      const currentPath = window.location.pathname
      clearStoredAuthSession()
      if (currentPath.startsWith('/admin') && currentPath !== '/admin/login') {
        const redirect = encodeURIComponent(window.location.pathname + window.location.search)
        window.location.assign(`/admin/login?redirect=${redirect}`)
      }
    }
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
  // 获取人物列表（含代表头像，分页）
  getPersonsWithSample: (page = 0, size = 20) =>
    api.get<PageResponse<PersonSummary>>(`/public/persons/with-sample?page=${page}&size=${size}`),

  // 获取单个人物信息
  getPerson: (personId: number) => api.get<PersonSummary>(`/public/persons/${personId}`),

  // 获取人物的代表图片（用于列表显示）
  getPersonSamplePhotos: (personId: number) => api.get<FaceFace[]>(`/public/persons/${personId}/sample-photos`),

  // 获取人物的相册推荐
  getPersonAlbumRecommendations: (personId: number) => api.get<AlbumRecommendation[]>(`/public/persons/${personId}/album-recommendations`),

  // 获取指定相册中的人物列表（按人脸数量倒序）
  getAlbumPersons: (albumId: number) => api.get<PersonSummary[]>(`/public/albums/${albumId}/persons`),

  // 获取人物被指派的图片（分页）
  getPersonAssignedPhotos: (personId: number, page = 0, size = 20) => api.get(`/public/persons/${personId}/assigned-photos?page=${page}&size=${size}`),

  // 获取人物的所有照片（人脸所在照片）
  getPersonPhotos: (personId: number, page = 0, size = 20) => api.get(`/public/persons/${personId}/photos?page=${page}&size=${size}`),

  // 设置人物的样例照片
  setSamplePhoto: (personId: number, faceId: number) => api.post<Person>(`/admin/persons/${personId}/set-sample`, { faceId }),

  // 创建人物
  createPerson: (name: string, description?: string) =>
    api.post<PersonSummary>('/admin/persons', { name, description }),

  // 根据名称搜索人物
  searchByName: (name: string) =>
    api.get<PersonSummary>(`/public/persons/search?name=${encodeURIComponent(name)}`)
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
  }>(`/admin/photos/${photoId}/similar?limit=${limit}`),

  // 根据人脸ID查找包含该人脸的照片
  findPhotosByFaceId: (faceId: number) => api.get<{
    success: boolean
    data: any[]
  }>(`/admin/faces/${faceId}/photos`),

  // 查找相似人脸
  findSimilarFaces: (faceId: number, top = 20, threshold = 0.6) => api.get<any[]>(`/admin/faces/${faceId}/similar?top=${top}&threshold=${threshold}`),

  // 绑定多个脸到人物（如果人物不存在则创建）
  assignFacesToPerson: (faceIds: number[], personName: string) =>
    api.post('/admin/faces/assign-to-person', { faceIds, personName }),

  // 批量分配人脸到已有人物
  batchAssignFaces: (faceIds: number[], personId: number, confirmed: boolean = true) =>
    api.post('/admin/faces/batch-assign', { faceIds, personId, confirmed })
}

// 相册相关API
export const albumApi = {
  // 设置相册自定义封面
  setAlbumCover: (albumId: number, coverImageIds: number[]) =>
    api.put(`/albums/${albumId}/cover`, { coverImageIds }),

  // 根据名称搜索相册
  searchByName: (name: string) =>
    api.get<AlbumDTO>(`/albums/search?name=${encodeURIComponent(name)}`),

  // 搜索相册和人物
  searchAll: (q: string) =>
    api.get<{ albums: AlbumDTO[]; persons: PersonSummary[]; photos: any[] }>(`/public/search?q=${encodeURIComponent(q)}`)
}

// 背景移除相关API
export const backgroundRemovalApi = {
  // 检查功能是否可用
  isAvailable: (photoId: number) =>
    api.get<{ enabled: boolean; photoExists: boolean }>(`/photos/${photoId}/background-removal/available`),

  // 获取移除背景后的图片URL（使用 api 的 baseURL）
  // quality: 'small' | 'medium' | 'large'，默认为 medium
  getUrl: (photoId: number, quality: string = 'medium'): string =>
    `${api.defaults.baseURL}/photos/${photoId}/remove-background?quality=${quality}`,

  // 触发异步背景移除任务
  triggerAsync: (photoId: number) =>
    api.post<{ success: boolean; message: string; photoId: number }>(
      `/photos/${photoId}/remove-background/async`
    )
}

// 系统配置相关API
export const configApi = {
  // 获取人脸聚类阈值
  getFaceClusterThreshold: () => api.get<{ faceClusterThreshold: number }>('/admin/config/face-cluster-threshold'),

  // 设置人脸聚类阈值
  setFaceClusterThreshold: (threshold: number) => api.put<{ message: string; faceClusterThreshold: number }>('/admin/config/face-cluster-threshold', { faceClusterThreshold: threshold }),

  // AI搜索状态
  getAiSearchStatus: () => api.get<{ enabled: boolean }>('/photos/ai-search/status')
}

export interface SuperAdminOverview {
  userCount: number
  activeUserCount: number
  disabledUserCount: number
  lockedUserCount: number
  storageProviderCount: number
  enabledStorageProviderCount: number
  multiUserEnabled: boolean
  scanSchedulerEnabled: boolean
  scanWorkerCount: number
  forceBindPhone: boolean
  autoRenewSchedulerEnabled: boolean
  smsProviderType: 'ALIYUN' | 'TENCENT_CLOUD' | 'TWILIO' | 'HUAWEI_CLOUD' | 'VOLCENGINE' | 'CLOOPEN' | 'AWS_SNS' | 'YUNPIAN' | 'SUBMAIL' | 'MESSAGEBIRD' | 'VONAGE' | 'INFOBIP' | 'PLIVO' | 'SINCH' | 'TELNYX' | 'SMSAERO' | 'HTTP_WEBHOOK'
  smsEnabled: boolean
  smsMockEnabled: boolean
  emailProviderType: 'SMTP' | 'ALIYUN_DIRECTMAIL' | 'TENCENT_EXMAIL' | 'AWS_SES' | 'SENDGRID' | 'MAILGUN' | 'RESEND' | 'POSTMARK' | 'BREVO' | 'MAILERSEND' | 'ZEPTOMAIL' | 'MAILJET' | 'SPARKPOST' | 'ELASTIC_EMAIL' | 'SMTP2GO' | 'SENDLAYER' | 'QQ_EXMAIL' | 'NETEASE_EXMAIL' | 'CUSTOM_SMTP'
  emailEnabled: boolean
  emailMockEnabled?: boolean
  emailCodeLoginEnabled?: boolean
  emailCodeExpireMinutes?: number
  paymentProviderType: 'ALIPAY' | 'WECHAT_PAY' | 'STRIPE' | 'PAYPAL' | 'UNIONPAY' | 'PADDLE' | 'LEMON_SQUEEZY' | 'ADYEN' | 'MOLLIE' | 'XENDIT' | 'MIDTRANS' | 'CUSTOM_WEBHOOK'
  paymentEnabled: boolean
  paymentMockEnabled: boolean
  defaultUserQuotaBytes: number
  defaultVipExtraQuotaBytes: number
  localStorageRoot: string
  userDataRoot: string
  totalQuotaBytes: number
  totalUsedBytes: number
  modelCount?: number
  missingModelFileCount?: number
  inactiveModelCount?: number
  modelHealthy?: boolean
}

export interface ProcessingOverviewTaskSummary {
  taskId?: string
  taskName?: string
  modelKey?: string
  modelName?: string
  photoId?: number
  photoName?: string
  method?: string
  path?: string
  queryString?: string
  actorLabel?: string
  ipAddress?: string
  durationMs?: number
  statusCode?: number
  status?: string
  message?: string
  error?: string | null
  current?: number
  total?: number
  progressPercent?: number
  startedAt?: string | null
  finishedAt?: string | null
  updatedAt?: string | null
  latestLog?: string | null
}

export interface ProcessingOverviewWorkerSummary {
  threadType: string
  label: string
  enabled?: boolean
  modelLoaded?: boolean
  queueActive?: boolean
  running?: boolean
  configuredWorkers?: number
  configuredConcurrency?: number
  activeWorkers?: number
  activeRequestCount?: number
  activeUserCount?: number
  recentMinuteRequestCount?: number
  slowRequestThresholdMs?: number
  activeThreads?: number
  runningTaskCount?: number
  runningImageCount?: number
  queuedTaskCount?: number
  queuedImageCount?: number
  queuedTasks?: number
  pausedTaskCount?: number
  completedTaskCount?: number
  summary?: string
  recentTasks?: ProcessingOverviewTaskSummary[]
  recentSlowRequests?: ProcessingOverviewTaskSummary[]
  topActiveEndpoints?: Record<string, any>[]
  runningTasks?: Record<string, any>[]
  queuedOwnerSummaries?: Record<string, any>[]
  scanStatus?: Record<string, any>
}

export interface SuperAdminProcessingOverview {
  workerCount: number
  activeWorkerGroupCount: number
  nonBlockingNote?: string
  workers: ProcessingOverviewWorkerSummary[]
}

export interface SuperAdminSettings {
  multiUserEnabled: boolean
  scanSchedulerEnabled: boolean
  scanWorkerCount: number
  forceBindPhone: boolean
  autoRenewSchedulerEnabled: boolean
  smsProviderType: 'ALIYUN' | 'TENCENT_CLOUD' | 'TWILIO' | 'HUAWEI_CLOUD' | 'VOLCENGINE' | 'CLOOPEN' | 'AWS_SNS' | 'YUNPIAN' | 'SUBMAIL' | 'MESSAGEBIRD' | 'VONAGE' | 'INFOBIP' | 'PLIVO' | 'SINCH' | 'TELNYX' | 'SMSAERO' | 'HTTP_WEBHOOK'
  smsEnabled: boolean
  smsMockEnabled: boolean
  smsEndpoint: string
  smsRegionId: string
  smsAccessKeyId: string
  smsAccessKeySecret: string
  smsSignName: string
  smsTemplateCode: string
  smsTemplateParamName: string
  smsSdkAppId: string
  smsCodeExpireMinutes: number
  emailProviderType: 'SMTP' | 'ALIYUN_DIRECTMAIL' | 'TENCENT_EXMAIL' | 'AWS_SES' | 'SENDGRID' | 'MAILGUN' | 'RESEND' | 'POSTMARK' | 'BREVO' | 'MAILERSEND' | 'ZEPTOMAIL' | 'MAILJET' | 'SPARKPOST' | 'ELASTIC_EMAIL' | 'SMTP2GO' | 'SENDLAYER' | 'QQ_EXMAIL' | 'NETEASE_EXMAIL' | 'CUSTOM_SMTP'
  emailEnabled: boolean
  emailMockEnabled?: boolean
  emailCodeLoginEnabled?: boolean
  emailCodeExpireMinutes?: number
  emailHost: string
  emailPort: number
  emailUsername: string
  emailPassword: string
  emailProtocol: string
  emailFromAddress: string
  emailFromName: string
  emailReplyTo: string
  emailSslEnabled: boolean
  emailStarttlsEnabled: boolean
  emailTestRecipient: string
  paymentProviderType: 'ALIPAY' | 'WECHAT_PAY' | 'STRIPE' | 'PAYPAL' | 'UNIONPAY' | 'PADDLE' | 'LEMON_SQUEEZY' | 'ADYEN' | 'MOLLIE' | 'XENDIT' | 'MIDTRANS' | 'CUSTOM_WEBHOOK'
  paymentEnabled: boolean
  paymentMockEnabled: boolean
  paymentAppId: string
  paymentMerchantId: string
  paymentMerchantName: string
  paymentPrivateKey: string
  paymentPublicKey: string
  paymentApiBaseUrl: string
  paymentNotifyUrl: string
  paymentReturnUrl: string
  paymentWebhookSecret: string
  paymentCurrency: string
  paymentVerificationMode: 'AUTO' | 'HMAC' | 'RSA' | 'CERTIFICATE' | 'CUSTOM'
  paymentApiSecret: string
  paymentCertificateSerialNo: string
  paymentPlatformCertificate: string
  defaultUserQuotaBytes: number
  defaultVipExtraQuotaBytes: number
  localStorageRoot: string
  userDataRoot: string
  defaultStorageProviderId?: number | null
}

export interface SendEmailPayload {
  recipient: string
  subject: string
  content: string
  html?: boolean
  templateKey?: string
  variables?: Record<string, any>
}

export interface EmailTemplateField {
  key: string
  label: string
  placeholder?: string
}

export interface EmailTemplateSummary {
  key: string
  name: string
  description: string
  html: boolean
  fields: EmailTemplateField[]
  sampleVariables: Record<string, any>
}

export interface EmailTemplatePreview {
  templateKey: string
  templateName: string
  subject: string
  content: string
  html: boolean
  variables: Record<string, any>
  recipient?: string
  providerType?: string
  message?: string
  success?: boolean
}

export interface AuthPublicSettings {
  multiUserEnabled: boolean
  forceBindPhone: boolean
  smsLoginEnabled: boolean
  emailCodeLoginEnabled?: boolean
}

export interface CurrentUserProfile {
  userId: number
  slug: string
  username: string
  phone?: string | null
  email?: string | null
  nickname?: string | null
  phoneVerified?: boolean
  emailVerified?: boolean
  projectNameZh?: string | null
  projectNameEn?: string | null
  avatarPath?: string | null
  role: string
  multiUserEnabled: boolean
  currentVipPlanId?: number | null
  currentVipPlanName?: string | null
  currentVipPlanCode?: string | null
  vipExpireAt?: string | null
  effectiveStorageQuotaBytes?: number
  storageUsedBytes?: number
}

export interface UserVipOverview {
  userId: number
  username: string
  nickname?: string | null
  storageUsedBytes: number
  storageQuotaBytes: number
  baseQuotaBytes: number
  vipExtraQuotaBytes: number
  currentVipPlanId?: number | null
  currentVipPlanName?: string | null
  currentVipPlanCode?: string | null
  currentVipPlanExtraQuotaBytes?: number
  currentVipPlanDurationDays?: number | null
  currentVipPlanCategory?: string | null
  currentVipQuotaGrantMode?: string | null
  vipExpireAt?: string | null
  paymentEnabled: boolean
  paymentMockEnabled: boolean
  paymentProviderType: 'ALIPAY' | 'WECHAT_PAY' | 'STRIPE' | 'PAYPAL' | 'UNIONPAY' | 'PADDLE' | 'LEMON_SQUEEZY' | 'ADYEN' | 'MOLLIE' | 'XENDIT' | 'MIDTRANS' | 'CUSTOM_WEBHOOK'
}

export interface UserVipPlan {
  id: number
  code: string
  name: string
  description?: string | null
  extraQuotaBytes: number
  durationDays: number
  priceFen: number
  priceYuan: string
  planCategory?: string
  quotaGrantMode?: string
  stackingMode?: string
  enabled: boolean
  sortOrder: number
  available?: boolean
  purchaseAction?: 'PURCHASE' | 'RENEWAL' | 'UPGRADE' | 'BLOCKED' | string
  purchaseHint?: string
  payableAmountFen?: number
  payableAmountYuan?: string
  originalAmountFen?: number
  originalAmountYuan?: string
  creditedAmountFen?: number
  creditedAmountYuan?: string
  effectiveExpireAt?: string | null
}

export interface UserVipCheckoutPreview {
  order: VipOrderSummary
  payment: VipOrderPaymentPreview
}

export interface PaymentInitiationResponse {
  orderId: number
  orderNo: string
  providerType: 'ALIPAY' | 'WECHAT_PAY' | 'STRIPE' | 'PAYPAL' | 'UNIONPAY' | 'PADDLE' | 'LEMON_SQUEEZY' | 'ADYEN' | 'MOLLIE' | 'XENDIT' | 'MIDTRANS' | 'CUSTOM_WEBHOOK'
  providerLabel: string
  httpMethod: string
  launchUrl: string
  redirect: boolean
  actionType?: 'API_REQUEST' | 'REDIRECT_FORM' | 'REDIRECT_GET' | 'QR_CODE' | string
  mockMode: boolean
  liveModeReady: boolean
  message: string
  headers?: Record<string, any> | null
  formFields?: Record<string, any> | null
  qrCodeText?: string | null
  payload: Record<string, any>
  preview: VipOrderPaymentPreview
}

export interface PaymentRefundPreview {
  orderId: number
  orderNo: string
  status?: string | null
  providerType: 'ALIPAY' | 'WECHAT_PAY' | 'STRIPE' | 'PAYPAL' | 'UNIONPAY' | 'PADDLE' | 'LEMON_SQUEEZY' | 'ADYEN' | 'MOLLIE' | 'XENDIT' | 'MIDTRANS' | 'CUSTOM_WEBHOOK'
  providerLabel: string
  mockMode: boolean
  liveModeReady: boolean
  refundReady?: boolean
  refundMode?: string | null
  verificationMode?: string | null
  missingFields?: string[] | null
  readinessWarnings?: string[] | null
  stageReadiness?: Array<{
    stageKey: string
    stageLabel: string
    ready: boolean
    checks?: Array<{
      label: string
      passed: boolean
      failureReason?: string | null
    }> | null
  }> | null
  recommendedConfigFields?: string[] | null
  nextActionHints?: string[] | null
  supportMessage?: string | null
  refundAmountFen: number
  refundAmountYuan: string
  httpMethod: string
  launchUrl: string
  headers?: Record<string, any> | null
  payload: Record<string, any>
  capabilityTags?: string[] | null
  integrationSteps?: string[] | null
  message: string
}

export interface PaymentReturnResult {
  success: boolean
  providerType: 'ALIPAY' | 'WECHAT_PAY' | 'STRIPE' | 'PAYPAL' | 'UNIONPAY' | 'PADDLE' | 'LEMON_SQUEEZY' | 'ADYEN' | 'MOLLIE' | 'XENDIT' | 'MIDTRANS' | 'CUSTOM_WEBHOOK'
  providerLabel?: string | null
  orderNo?: string | null
  resolvedOrderNoSource?: string | null
  status?: string | null
  gatewayStatus?: string | null
  externalTradeNo?: string | null
  paymentNotifiedAt?: string | null
  paidAt?: string | null
  cancelledAt?: string | null
  refundStatus?: string | null
  refundAmountFen?: number | null
  refundedAt?: string | null
  autoRenewEnabled?: boolean
  nextRenewalAt?: string | null
  renewalSourceOrderId?: number | null
  renewalSourceOrderNo?: string | null
  renewalSourceOrderStatus?: string | null
  renewalChildOrderId?: number | null
  renewalChildOrderNo?: string | null
  renewalChildOrderStatus?: string | null
  orderStageLabel?: string | null
  renewalChainType?: 'PRIMARY' | 'RENEWAL_CHILD' | string | null
  canInitiatePayment?: boolean
  canToggleAutoRenew?: boolean
  expireAt?: string | null
  remark?: string | null
  createdAt?: string | null
  updatedAt?: string | null
  returnQuery?: Record<string, string> | null
  terminal?: boolean
  suggestedPollIntervalSeconds?: number | null
  recommendedActions?: string[] | null
  paid?: boolean
  message?: string | null
}

export interface PaymentNotifyPreviewResult {
  success: boolean
  preview: true
  providerType: PaymentReturnResult['providerType']
  providerLabel?: string | null
  recognized: boolean
  verified: boolean
  verificationMode?: string | null
  verificationMessage?: string | null
  orderNo?: string | null
  resolvedOrderNoSource?: string | null
  recognizedStatus?: string | null
  externalTradeNo?: string | null
  eventTime?: string | null
  refundAmountFen?: number | null
  orderExists?: boolean
  currentOrderStatus?: string | null
  predictedLifecycleAction?: 'MARK_PAID' | 'MARK_CANCELLED' | 'MARK_REFUNDED' | 'NONE' | string | null
  wouldUpdateOrder?: boolean
  predictedFinalStatus?: string | null
  recommendedActions?: string[] | null
  message?: string | null
}

export interface PaymentNotifyExecutionResult {
  success: boolean
  recognized?: boolean
  verified?: boolean
  verificationMode?: string | null
  verificationMessage?: string | null
  orderNo?: string | null
  status?: string | null
  externalTradeNo?: string | null
  gatewayStatus?: string | null
  refundStatus?: string | null
  refundAmountFen?: number | null
  message?: string | null
}

export interface BrowserStorageProviderSummary {
  id: number
  name: string
  type: 'LOCAL' | 'FTP' | 'WEBDAV' | 'COS' | 'SFTP' | 'S3_COMPATIBLE' | 'MINIO' | 'OSS' | 'R2' | 'SMB' | 'NFS' | 'AZURE_BLOB' | 'GCS' | 'OBS' | 'TOS' | 'BOS' | 'UCLOUD_US3' | 'JD_JSS' | 'WASABI' | 'QINIU_KODO' | 'B2' | 'UPYUN' | 'DROPBOX' | 'ONEDRIVE'
  enabled: boolean
  baseDirectory?: string | null
  browserSupported: boolean
  uploadSupported: boolean
  supportMessage?: string | null
  scopedBasePath?: string | null
}

export interface PublicUserProfile {
  userId: number
  slug: string
  username: string
  nickname?: string | null
  projectNameZh?: string | null
  projectNameEn?: string | null
  avatarPath?: string | null
}

export interface UserAccountSummary {
  id: number
  slug: string
  username: string
  phone?: string | null
  email?: string | null
  nickname?: string | null
  role: 'SUPER_ADMIN' | 'USER_ADMIN'
  status: 'PENDING' | 'ACTIVE' | 'DISABLED' | 'LOCKED'
  phoneVerified: boolean
  emailVerified?: boolean
  storageQuotaBytes: number
  vipExtraQuotaBytes: number
  currentVipPlanId?: number | null
  currentVipPlanName?: string | null
  currentVipPlanCode?: string | null
  vipPlanExtraQuotaBytes?: number
  vipExpireAt?: string | null
  effectiveStorageQuotaBytes: number
  storageQuotaGb?: number
  vipExtraQuotaGb?: number
  pendingPassword?: string
  pendingPasswordConfirm?: string
  storageUsedBytes: number
  remainingStorageBytes: number
  preferredStorageProviderId?: number | null
  preferredStorageProviderName?: string | null
  preferredStorageProviderType?: 'LOCAL' | 'FTP' | 'WEBDAV' | 'COS' | 'SFTP' | 'S3_COMPATIBLE' | 'MINIO' | 'OSS' | 'R2' | 'SMB' | 'NFS' | 'AZURE_BLOB' | 'GCS' | 'OBS' | 'TOS' | 'BOS' | 'UCLOUD_US3' | 'JD_JSS' | 'WASABI' | 'QINIU_KODO' | 'B2' | 'UPYUN' | 'DROPBOX' | 'ONEDRIVE' | null
  multiUserVisible: boolean
  projectNameZh?: string | null
  projectNameEn?: string | null
  lastLoginAt?: string | null
  lastLoginIp?: string | null
  createdAt: string
  updatedAt: string
}

export interface VipPlanSummary {
  id: number
  code: string
  name: string
  description?: string | null
  extraQuotaBytes: number
  durationDays: number
  priceFen: number
  planCategory?: string
  quotaGrantMode?: string
  stackingMode?: string
  enabled: boolean
  sortOrder: number
  createdAt: string
  updatedAt: string
  extraQuotaGb?: number
  priceYuan?: number
}

export interface VipOrderSummary {
  id: number
  orderNo: string
  userId: number
  username?: string | null
  nickname?: string | null
  userSlug?: string | null
  vipPlanId: number
  vipPlanName?: string | null
  vipPlanCode?: string | null
  amountFen: number
  amountYuan?: number
  originalAmountFen?: number | null
  originalAmountYuan?: string | null
  creditedAmountFen?: number | null
  creditedAmountYuan?: string | null
  changeType?: 'PURCHASE' | 'RENEWAL' | 'UPGRADE' | string | null
  sourceVipPlanId?: number | null
  status: string
  source: string
  paymentProviderType?: string | null
  externalTradeNo?: string | null
  gatewayStatus?: string | null
  callbackPayloadJson?: string | null
  paymentNotifiedAt?: string | null
  paidAt?: string | null
  cancelledAt?: string | null
  refundStatus?: string | null
  refundAmountFen?: number | null
  refundedAt?: string | null
  autoRenewEnabled?: boolean
  nextRenewalAt?: string | null
  renewalSourceOrderId?: number | null
  renewalSourceOrderNo?: string | null
  renewalSourceOrderStatus?: string | null
  renewalChildOrderId?: number | null
  renewalChildOrderNo?: string | null
  renewalChildOrderStatus?: string | null
  dueForRenewal?: boolean
  canInitiatePayment?: boolean
  canToggleAutoRenew?: boolean
  orderStageLabel?: string | null
  renewalChainType?: 'PRIMARY' | 'RENEWAL_CHILD' | string
  expireAt?: string | null
  pricingDetailJson?: string | null
  remark?: string | null
  message?: string | null
  createdAt: string
  updatedAt: string
}

export interface VipRenewalCandidate {
  orderId: number
  orderNo: string
  userId: number
  username?: string | null
  nickname?: string | null
  userSlug?: string | null
  vipPlanId: number
  vipPlanCode?: string | null
  vipPlanName?: string | null
  status: string
  amountFen: number
  amountYuan?: number
  paymentProviderType?: string | null
  autoRenewEnabled: boolean
  nextRenewalAt?: string | null
  expireAt?: string | null
  paidAt?: string | null
  daysOverdue: number
  hoursOverdue: number
  renewalAction: string
  renewalBlocked?: boolean
  renewalMessage?: string | null
  paymentProviderLabel?: string | null
  paymentMockMode?: boolean
  paymentEnabled?: boolean
  paymentLiveModeReady?: boolean
  paymentMissingFields?: string[] | null
  paymentReadinessWarnings?: string[] | null
  existingRenewalOrderId?: number | null
  existingRenewalOrderNo?: string | null
  existingRenewalOrderStatus?: string | null
  existingRenewalOrderCreatedAt?: string | null
  remark?: string | null
}

export interface VipRenewalPreviewResponse {
  generatedAt: string
  content: VipRenewalCandidate[]
  limit: number
  dueCount: number
  returnedCount: number
  activeAutoRenewOrderCount: number
  dryRun: boolean
  supportedStatuses: string[]
  message: string
}

export interface VipRenewalExecuteItem {
  sourceOrderId: number
  sourceOrderNo: string
  userId: number
  vipPlanId: number
  created: boolean
  reason: string
  createdOrderId?: number
  createdOrderNo?: string
  paymentProviderLabel?: string | null
  paymentMockMode?: boolean
  paymentEnabled?: boolean
  paymentLiveModeReady?: boolean
  paymentMissingFields?: string[] | null
  paymentReadinessWarnings?: string[] | null
  initiationAttempted?: boolean
  initiationSuccess?: boolean
  initiationMessage?: string | null
  initiationActionType?: string | null
  initiationLaunchUrl?: string | null
  initiationRedirect?: boolean
  initiationMockMode?: boolean
  initiationProviderType?: string | null
  existingRenewalOrderId?: number | null
  existingRenewalOrderNo?: string | null
  existingRenewalOrderStatus?: string | null
  username?: string | null
  nickname?: string | null
  vipPlanName?: string | null
}

export interface VipRenewalExecuteResponse {
  executedAt: string
  limit: number
  candidateCount: number
  createdCount: number
  skippedCount: number
  createdOrders: VipRenewalExecuteItem[]
  skippedOrders: VipRenewalExecuteItem[]
  message: string
}

export interface VipOrderPaymentPreview {
  orderId: number
  orderNo: string
  userId: number
  username?: string | null
  vipPlanId: number
  vipPlanName?: string | null
  providerType: 'ALIPAY' | 'WECHAT_PAY' | 'STRIPE' | 'PAYPAL' | 'UNIONPAY' | 'PADDLE' | 'LEMON_SQUEEZY' | 'ADYEN' | 'MOLLIE' | 'XENDIT' | 'MIDTRANS' | 'CUSTOM_WEBHOOK'
  providerLabel: string
  enabled: boolean
  mockEnabled: boolean
  liveModeReady: boolean
  currency: string
  apiBaseUrl: string
  missingFields: string[]
  readinessWarnings?: string[] | null
  signatureReady?: boolean
  callbackVerificationReady?: boolean
  refundReady?: boolean
  stageReadiness?: Array<{
    stageKey: string
    stageLabel: string
    ready: boolean
    checks?: Array<{
      label: string
      passed: boolean
      failureReason?: string | null
    }> | null
  }> | null
  recommendedConfigFields?: string[] | null
  nextActionHints?: string[] | null
  supportMessage: string
  verificationMode?: string | null
  initiationMode?: string | null
  refundMode?: string | null
  capabilityTags?: string[] | null
  integrationSteps?: string[] | null
  requestPayload: Record<string, any>
  callbackPayload: Record<string, any>
}

export interface LoginRecordSummary {
  id: number
  userId?: number | null
  userSlug?: string | null
  usernameSnapshot?: string | null
  phoneSnapshot?: string | null
  nickname?: string | null
  loginMethod: 'USERNAME_PASSWORD' | 'PHONE_PASSWORD' | 'SMS_CODE'
  success: boolean
  ipAddress?: string | null
  userAgent?: string | null
  failureReason?: string | null
  createdAt: string
}

export interface OperationLogSummary {
  id: number
  userId?: number | null
  username?: string | null
  nickname?: string | null
  userSlug?: string | null
  operatorUsername?: string | null
  operationType?: string | null
  targetType?: string | null
  targetId?: number | null
  targetPath?: string | null
  detailJson?: string | null
  ipAddress?: string | null
  createdAt: string
}

export interface StorageProviderSummary {
  id: number
  name: string
  type: 'LOCAL' | 'FTP' | 'WEBDAV' | 'COS' | 'SFTP' | 'S3_COMPATIBLE' | 'MINIO' | 'OSS' | 'R2' | 'SMB' | 'NFS' | 'AZURE_BLOB' | 'GCS' | 'OBS' | 'TOS' | 'BOS' | 'UCLOUD_US3' | 'JD_JSS' | 'WASABI' | 'QINIU_KODO' | 'B2' | 'UPYUN' | 'DROPBOX' | 'ONEDRIVE'
  enabled: boolean
  isDefault: boolean
  priority: number
  endpoint?: string | null
  bucketName?: string | null
  baseDirectory?: string | null
  configJson?: string | null
  browserSupported?: boolean
  uploadSupported?: boolean
  scanSupported?: boolean
  previewSupported?: boolean
  supportMessage?: string | null
  resolvedBaseDirectory?: string | null
  createdAt: string
  updatedAt: string
}

export interface StorageProviderTestResult {
  success: boolean
  reachable: boolean
  message: string
  supportMessage?: string | null
  checks: Array<{
    key: string
    label: string
    success: boolean
    message?: string | null
  }>
}

export interface LegacyMigrationSummary {
  success: boolean
  message?: string | null
  startedAt?: string | null
  finishedAt?: string | null
  ownerUserId?: number | null
  ownerUsername?: string | null
  migratedAlbumOwnershipCount?: number
  migratedPhotoOwnershipCount?: number
  migratedPersonOwnershipCount?: number
  migratedFaceOwnershipCount?: number
  migratedCommentOwnershipCount?: number
  migratedTagOwnershipCount?: number
  movedTopLevelEntryCount?: number
  movedAlbumDirectoryCount?: number
  movedPhotoFileCount?: number
  rewrittenAlbumPathCount?: number
  rewrittenPhotoPathCount?: number
  rewrittenPhotoStorageRefCount?: number
  totalOwnershipMigrationCount?: number
  totalPathRewriteCount?: number
}

export interface StoragePathSummary {
  providerId: number
  providerName: string
  path: string
  directoryCount: number
  fileCount: number
  totalBytes: number
  samples: string[]
}

export interface StorageMigrationMatchSummary {
  albumCount: number
  photoCount: number
  albumSamples: string[]
  photoSamples: string[]
}

export interface StorageMigrationPreview {
  source: StoragePathSummary
  target: StoragePathSummary
  database: StorageMigrationMatchSummary
  targetNotEmpty?: boolean
  canExecute?: boolean
  message?: string | null
}

export interface StorageCleanupPreview {
  target: StoragePathSummary
  database: StorageMigrationMatchSummary
  hasContent?: boolean
  message?: string | null
}

export interface StorageMigrationResult extends StorageMigrationPreview {
  success: boolean
  rewrittenAlbumCount?: number
  rewrittenPhotoCount?: number
  clearedTarget?: boolean
  executedAt?: string | null
}

export interface StorageCleanupResult extends StorageCleanupPreview {
  success: boolean
  executedAt?: string | null
}

export interface ConfigurableTablePreference {
  columnOrder: string[]
  hiddenColumns: string[]
  sortKey?: string | null
  sortDirection?: 'asc' | 'desc' | null
}

export type SuperAdminTablePreferences = Record<string, ConfigurableTablePreference>

export interface ModelRebuildTask {
  taskId: string
  modelKey: string
  modelName: string
  includeMissingItems: boolean
  forceRebuild: boolean
  status: string
  message: string
  complete: boolean
  total: number
  processed: number
  skipped: number
  failed: number
  createdAt?: string | null
  startedAt?: string | null
  finishedAt?: string | null
  logs?: string[]
}

export interface ModelValidationSummary {
  inputs: string[]
  outputs: string[]
  sizeBytes: number
  validatedAt?: string | null
}

export interface ManagedModelSummary {
  key: string
  name: string
  code: string
  configuredPath: string
  resolvedPath: string
  fileExists: boolean
  sizeBytes: number
  enabled: boolean
  active: boolean
  rebuildNotes: string[]
  latestValidation?: ModelValidationSummary | null
  latestTask?: ModelRebuildTask | null
  taskHistory?: ModelRebuildTask[] | null
}

export const superAdminApi = {
  getOverview: () => api.get<SuperAdminOverview>('/admin/super-admin/overview'),
  getProcessingOverview: () => api.get<SuperAdminProcessingOverview>('/admin/super-admin/processing-overview'),
  getSettings: () => api.get<SuperAdminSettings>('/admin/super-admin/settings'),
  updateSettings: (data: Partial<SuperAdminSettings>) => api.put<SuperAdminSettings>('/admin/super-admin/settings', data),
  getTablePreferences: () => api.get<SuperAdminTablePreferences>('/admin/super-admin/table-preferences'),
  updateTablePreferences: (data: SuperAdminTablePreferences) => api.put<SuperAdminTablePreferences>('/admin/super-admin/table-preferences', data),
  getUsers: (page = 0, size = 10, keyword = '') =>
    api.get<PageResponse<UserAccountSummary>>('/admin/super-admin/users', { params: { page, size, keyword: keyword || undefined } }),
  getLoginRecords: (userId?: number | null, page = 0, size = 20) =>
    api.get<PageResponse<LoginRecordSummary>>('/admin/super-admin/login-records', { params: { userId: userId ?? undefined, page, size } }),
  getOperationLogs: (userId?: number | null, page = 0, size = 20) =>
    api.get<PageResponse<OperationLogSummary>>('/admin/super-admin/operation-logs', { params: { userId: userId ?? undefined, page, size } }),
  getVipPlans: () => api.get<{ vipPlans: VipPlanSummary[] }>('/admin/super-admin/vip-plans'),
  createVipPlan: (data: Partial<VipPlanSummary>) => api.post<VipPlanSummary>('/admin/super-admin/vip-plans', data),
  updateVipPlan: (planId: number, data: Partial<VipPlanSummary>) => api.put<VipPlanSummary>(`/admin/super-admin/vip-plans/${planId}`, data),
  getVipOrders: (userId?: number | null, page = 0, size = 20, autoRenewEnabled?: boolean, dueForRenewal?: boolean) =>
    api.get<PageResponse<VipOrderSummary>>('/admin/super-admin/vip-orders', { params: { userId: userId ?? undefined, page, size, autoRenewEnabled: autoRenewEnabled || undefined, dueForRenewal: dueForRenewal || undefined } }),
  getVipOrderByOrderNo: (orderNo: string) =>
    api.get<VipOrderSummary>('/admin/super-admin/vip-orders/by-order-no', { params: { orderNo } }),
  getVipRenewalPreview: (limit = 20) =>
    api.get<VipRenewalPreviewResponse>('/admin/super-admin/vip-orders/renewal-preview', { params: { limit } }),
  executeVipRenewals: (limit = 20) =>
    api.post<VipRenewalExecuteResponse>('/admin/super-admin/vip-orders/renewal-execute', { limit }),
  createVipOrder: (data: Partial<VipOrderSummary>) => api.post<VipOrderSummary>('/admin/super-admin/vip-orders', data),
  updateVipOrder: (orderId: number, data: Partial<VipOrderSummary>) => api.put<VipOrderSummary>(`/admin/super-admin/vip-orders/${orderId}`, data),
  previewVipOrderPayment: (orderId: number) => api.get<VipOrderPaymentPreview>(`/admin/super-admin/vip-orders/${orderId}/payment-preview`),
  initiateVipOrderPayment: (orderId: number) => api.post<PaymentInitiationResponse>(`/admin/super-admin/vip-orders/${orderId}/payment-initiate`),
  previewVipOrderRefund: (orderId: number, refundAmountFen?: number) =>
    api.get<PaymentRefundPreview>(`/admin/super-admin/vip-orders/${orderId}/refund-preview`, { params: { refundAmountFen: refundAmountFen ?? undefined } }),
  previewPaymentNotify: (providerType: string, payload: Record<string, any> | string, headers: Record<string, string>) =>
    api.post<PaymentNotifyPreviewResult>(`/admin/super-admin/payments/notify-preview/${providerType}`, payload, { headers }),
  mockPayVipOrder: (orderId: number) => api.post<VipOrderSummary>(`/admin/super-admin/vip-orders/${orderId}/mock-pay`),
  cancelVipOrder: (orderId: number, remark?: string) => api.post<VipOrderSummary>(`/admin/super-admin/vip-orders/${orderId}/cancel`, { remark }),
  refundVipOrder: (orderId: number, refundAmountFen?: number, remark?: string) => api.post<VipOrderSummary>(`/admin/super-admin/vip-orders/${orderId}/refund`, { refundAmountFen, remark }),
  confirmVipOrderRefund: (orderId: number, refundAmountFen?: number, remark?: string) =>
    api.post<VipOrderSummary>(`/admin/super-admin/vip-orders/${orderId}/refund-confirm`, { refundAmountFen, remark }),
  markVipOrderRefundFailed: (orderId: number, remark?: string) =>
    api.post<VipOrderSummary>(`/admin/super-admin/vip-orders/${orderId}/refund-failed`, { remark }),
  updateUser: (userId: number, data: Partial<UserAccountSummary>) => api.put<UserAccountSummary>(`/admin/super-admin/users/${userId}`, data),
  resetUserPassword: (userId: number, newPassword: string) => api.post<Record<string, any>>(`/admin/super-admin/users/${userId}/password`, { newPassword }),
  getStorageProviders: () => api.get<{ storageProviders: StorageProviderSummary[] }>('/admin/super-admin/storage-providers'),
  createStorageProvider: (data: Partial<StorageProviderSummary>) => api.post<StorageProviderSummary>('/admin/super-admin/storage-providers', data),
  testStorageProvider: (data: Partial<StorageProviderSummary>) => api.post<StorageProviderTestResult>('/admin/super-admin/storage-providers/test', data),
  runLegacyMigration: () => api.post<LegacyMigrationSummary>('/admin/super-admin/legacy-migration/run'),
  previewStorageMigration: (data: Record<string, any>) => api.post<StorageMigrationPreview>('/admin/super-admin/storage-migration/preview', data),
  executeStorageMigration: (data: Record<string, any>) => api.post<StorageMigrationResult>('/admin/super-admin/storage-migration/execute', data),
  previewStorageCleanup: (data: Record<string, any>) => api.post<StorageCleanupPreview>('/admin/super-admin/storage-cleanup/preview', data),
  executeStorageCleanup: (data: Record<string, any>) => api.post<StorageCleanupResult>('/admin/super-admin/storage-cleanup/execute', data),
  sendTestSms: (phone?: string) => api.post<Record<string, any>>('/admin/super-admin/sms/test', { phone }),
  sendTestEmail: (recipient?: string) => api.post<Record<string, any>>('/admin/super-admin/email/test', { recipient }),
  sendEmail: (payload: SendEmailPayload) => api.post<Record<string, any>>('/admin/super-admin/email/send', payload),
  getEmailTemplates: () => api.get<{ templates: EmailTemplateSummary[] }>('/admin/super-admin/email/templates'),
  previewEmailTemplate: (payload: Pick<SendEmailPayload, 'templateKey' | 'variables'>) =>
    api.post<EmailTemplatePreview>('/admin/super-admin/email/templates/preview', payload),
  sendEmailTemplate: (payload: Pick<SendEmailPayload, 'recipient' | 'templateKey' | 'variables'>) =>
    api.post<EmailTemplatePreview>('/admin/super-admin/email/templates/send', payload),
  getModels: () => api.get<{ models: ManagedModelSummary[] }>('/admin/super-admin/models'),
  downloadModel: (modelKey: string, url: string) => api.post<Record<string, any>>(`/admin/super-admin/models/${modelKey}/download`, { url }),
  reloadModel: (modelKey: string) => api.post<Record<string, any>>(`/admin/super-admin/models/${modelKey}/reload`),
  rebuildModel: (modelKey: string, payload: { includeMissingItems: boolean; forceRebuild: boolean }) =>
    api.post<Record<string, any>>(`/admin/super-admin/models/${modelKey}/rebuild`, payload),
  getModelTask: (taskId: string) => api.get<ModelRebuildTask>(`/admin/super-admin/model-tasks/${taskId}`),
  updateStorageProvider: (providerId: number, data: Partial<StorageProviderSummary>) =>
    api.put<StorageProviderSummary>(`/admin/super-admin/storage-providers/${providerId}`, data)
}

export const authPublicApi = {
  getSettings: () => api.get<AuthPublicSettings>('/auth/public-settings')
}

export const authProfileApi = {
  getCurrent: () => api.get<CurrentUserProfile>('/auth/me'),
  updateProfile: (data: Partial<CurrentUserProfile>) => api.put<CurrentUserProfile>('/auth/profile', data),
  sendPhoneCode: () => api.post<{ message: string; expiresInSeconds: number; debugCode?: string | null }>('/auth/phone/send-code'),
  verifyPhoneCode: (code: string) => api.post<CurrentUserProfile>('/auth/phone/verify', { code }),
  sendEmailCode: (email: string) => api.post<{ message: string; expiresInSeconds: number; debugCode?: string | null }>('/auth/email/send-code', { email }),
  sendBindEmailCode: () => api.post<{ message: string; expiresInSeconds: number; debugCode?: string | null }>('/auth/email/verify/send-code'),
  verifyEmailCode: (code: string) => api.post<CurrentUserProfile>('/auth/email/verify', { code }),
  uploadAvatar: (file: File) => {
    const formData = new FormData()
    formData.append('file', file)
    return api.post<CurrentUserProfile>('/auth/avatar', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
  },
  getVipOverview: () => api.get<UserVipOverview>('/auth/vip/overview'),
  getVipPlans: () => api.get<{ plans: UserVipPlan[] }>('/auth/vip/plans'),
  getVipOrders: () => api.get<{ orders: VipOrderSummary[] }>('/auth/vip/orders'),
  createVipOrder: (planId: number) => api.post<VipOrderSummary>('/auth/vip/orders', { planId }),
  getVipCheckout: (orderId: number) => api.get<UserVipCheckoutPreview>(`/auth/vip/orders/${orderId}/checkout`),
  initiateVipCheckout: (orderId: number) => api.post<PaymentInitiationResponse>(`/auth/vip/orders/${orderId}/checkout/initiate`),
  mockPayVipOrder: (orderId: number) => api.post<VipOrderSummary>(`/auth/vip/orders/${orderId}/mock-pay`),
  updateVipOrderAutoRenew: (orderId: number, autoRenewEnabled: boolean) =>
    api.put<VipOrderSummary>(`/auth/vip/orders/${orderId}/auto-renew`, { autoRenewEnabled })
}

export const paymentApi = {
  notify: (providerType: PaymentReturnResult['providerType'], payload: Record<string, any> | string, headers?: Record<string, string>) =>
    api.post<PaymentNotifyExecutionResult>(`/payments/notify/${providerType}`, payload, { headers: headers || {} }),
  getReturnResult: (providerType: PaymentReturnResult['providerType'], queryParams?: Record<string, any>) =>
    api.get<PaymentReturnResult>(`/payments/return/${providerType}`, { params: queryParams || {} })
}

export const publicUserApi = {
  getProfile: (userSlug: string) => api.get<PublicUserProfile>(`/auth/public-user?userSlug=${encodeURIComponent(userSlug)}`),
  listUsers: () => api.get<{ users: PublicUserProfile[] }>('/auth/public-users')
}

// AI搜索相关API
export interface AiSearchCondition {
  type: string
  ids?: number[]
  values?: string[]
  value?: string
  minValue?: number
  maxValue?: number
  startDate?: string
  endDate?: string
}

export interface AiSearchSuggestionAction {
  label: string
  actionType: string
  conditionTypes?: string[]
}

export interface AiSearchResponse {
  queryMode?: string
  usedAi?: boolean
  answer?: string
  needAnswer?: boolean
  relaxed?: boolean
  relaxedReason?: string
  suggestions?: string[]
  suggestionActions?: AiSearchSuggestionAction[]
  explanation?: string
  parsedIntent?: {
    personId?: number
    personIds?: number[]
    tagIds?: number[]
    albumIds?: number[]
    startDate?: string
    endDate?: string
    cameraModel?: string
    lensModel?: string
    colorCategory?: string
    keywords?: string[]
    filenameKeywords?: string[]
    must?: AiSearchCondition[]
    should?: AiSearchCondition[]
    mustNot?: AiSearchCondition[]
    resultTypes?: string[]
    includeHidden?: boolean
    needAnswer?: boolean
    answerPrompt?: string
    answerStyle?: string
  }
  photos?: any[]
  albums?: any[]
  persons?: any[]
  totalElements?: number
  matchedPersonName?: string
  matchedTagNames?: string[]
  matchedAlbumNames?: string[]
  aiSearchEnabled: boolean
  analysisData?: {
    analysisType?: 'body_change' | 'person_overview' | 'person_cooccurrence' | 'person_pair_cooccurrence'
    personId?: number
    personName?: string
    periodLabel?: string
    totalEntities?: number
    photoMatched?: number
    anchorPersonName?: string
    topPersons?: Array<{
      personId?: number
      personName?: string
      matchedPhotoCount?: number
    }>
    topPairs?: Array<{
      personAId?: number
      personAName?: string
      personBId?: number
      personBName?: string
      matchedPhotoCount?: number
    }>
    summaryItems?: string[]
    startYear?: number
    endYear?: number
    totalPhotos?: number
    avgFaceArea?: number
    avgFaceWidth?: number
    avgFaceHeight?: number
    avgAspectRatio?: number
    yearlyStats?: YearlyBodyStats[]
    trend?: string
    conclusion?: string
    changePercent?: number
    firstPeriod?: string
    lastPeriod?: string
  }
  executionPlan?: {
    version?: string
    planType?: string
    stepCount?: number
    operators?: string[]
    resultTypes?: string[]
    evidenceStatus?: string
    resolverUsed?: boolean
    metadata?: Record<string, unknown>
    finalOutputKeys?: string[]
  }
}

export interface YearlyBodyStats {
  year: number
  month: number
  label: string
  faceCount: number
  avgFaceArea: number
  avgFaceWidth: number
  avgFaceHeight: number
  avgAspectRatio: number
}

export const aiSearchApi = {
  search: (q: string, page = 0, size = 30) =>
    api.get<AiSearchResponse>('/photos/ai-search', { params: { q, page, size } }),
  execute: (
    query: string,
    intent: AiSearchResponse['parsedIntent'],
    suggestionAction?: AiSearchSuggestionAction,
    page = 0,
    size = 30
  ) => api.post<AiSearchResponse>('/photos/ai-search/execute', { query, intent, suggestionAction, page, size })
}
