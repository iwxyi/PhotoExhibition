import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import axios from 'axios'
import { clearStoredAuthSession, getEffectiveAuthToken } from '@/api'

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(getEffectiveAuthToken())
  const username = ref<string | null>(localStorage.getItem('auth_username') || localStorage.getItem('admin_username'))
  const role = ref<string | null>(localStorage.getItem('auth_role'))
  const userId = ref<string | null>(localStorage.getItem('auth_user_id'))
  const slug = ref<string | null>(localStorage.getItem('auth_slug'))
  const nickname = ref<string | null>(localStorage.getItem('auth_nickname'))
  const phone = ref<string | null>(localStorage.getItem('auth_phone'))
  const phoneVerified = ref<boolean>(localStorage.getItem('auth_phone_verified') === 'true')
  const email = ref<string | null>(localStorage.getItem('auth_email'))
  const emailVerified = ref<boolean>(localStorage.getItem('auth_email_verified') === 'true')
  const projectNameZh = ref<string | null>(localStorage.getItem('auth_project_name_zh'))
  const projectNameEn = ref<string | null>(localStorage.getItem('auth_project_name_en'))
  const avatarPath = ref<string | null>(localStorage.getItem('auth_avatar_path'))
  const multiUserEnabled = ref<boolean>(localStorage.getItem('auth_multi_user_enabled') === 'true')
  const smsLoginEnabled = ref<boolean>(localStorage.getItem('auth_sms_login_enabled') === 'true')
  const emailCodeLoginEnabled = ref<boolean>(localStorage.getItem('auth_email_code_login_enabled') === 'true')
  const currentVipPlanId = ref<string | null>(localStorage.getItem('auth_current_vip_plan_id'))
  const currentVipPlanName = ref<string | null>(localStorage.getItem('auth_current_vip_plan_name'))
  const currentVipPlanCode = ref<string | null>(localStorage.getItem('auth_current_vip_plan_code'))
  const vipExpireAt = ref<string | null>(localStorage.getItem('auth_vip_expire_at'))
  const effectiveStorageQuotaBytes = ref<number>(Number(localStorage.getItem('auth_effective_storage_quota_bytes') || 0))
  const storageUsedBytes = ref<number>(Number(localStorage.getItem('auth_storage_used_bytes') || 0))
  const isAuthenticated = ref<boolean>(!!token.value)
  const isBootstrapping = ref(false)
  const publicSettingsLoaded = ref(false)
  const displayName = computed(() => nickname.value || username.value || '未登录')
  const isSuperAdmin = computed(() => role.value === 'SUPER_ADMIN')
  const projectDisplayName = computed(() => projectNameZh.value || projectNameEn.value || null)

  // 设置axios默认请求头
  if (token.value) {
    axios.defaults.headers.common['Authorization'] = `Bearer ${token.value}`
  }

  const persistProfile = (responseData: any, fallbackUsername?: string) => {
    const newToken = responseData.token || token.value
    const user = responseData.username || fallbackUsername || username.value
    const nextRole = responseData.role || null
    const nextUserId = responseData.userId != null ? String(responseData.userId) : null
    const nextSlug = responseData.slug || null
    const nextNickname = responseData.nickname || null
    const nextPhone = responseData.phone || null
    const nextPhoneVerified = !!responseData.phoneVerified
    const nextEmail = responseData.email || null
    const nextEmailVerified = !!responseData.emailVerified
    const nextProjectNameZh = responseData.projectNameZh || null
    const nextProjectNameEn = responseData.projectNameEn || null
    const nextAvatarPath = responseData.avatarPath || null
    const nextMultiUserEnabled = !!responseData.multiUserEnabled
    const nextCurrentVipPlanId = responseData.currentVipPlanId != null ? String(responseData.currentVipPlanId) : null
    const nextCurrentVipPlanName = responseData.currentVipPlanName || null
    const nextCurrentVipPlanCode = responseData.currentVipPlanCode || null
    const nextVipExpireAt = responseData.vipExpireAt || null
    const nextEffectiveStorageQuotaBytes = Number(responseData.effectiveStorageQuotaBytes || 0)
    const nextStorageUsedBytes = Number(responseData.storageUsedBytes || 0)

    if (newToken) {
      token.value = newToken
      localStorage.setItem('auth_token', newToken)
      localStorage.setItem('admin_token', newToken)
      axios.defaults.headers.common['Authorization'] = `Bearer ${newToken}`
    }

    username.value = user
    role.value = nextRole
    userId.value = nextUserId
    slug.value = nextSlug
    nickname.value = nextNickname
    phone.value = nextPhone
    phoneVerified.value = nextPhoneVerified
    email.value = nextEmail
    emailVerified.value = nextEmailVerified
    projectNameZh.value = nextProjectNameZh
    projectNameEn.value = nextProjectNameEn
    avatarPath.value = nextAvatarPath
    multiUserEnabled.value = nextMultiUserEnabled
    currentVipPlanId.value = nextCurrentVipPlanId
    currentVipPlanName.value = nextCurrentVipPlanName
    currentVipPlanCode.value = nextCurrentVipPlanCode
    vipExpireAt.value = nextVipExpireAt
    effectiveStorageQuotaBytes.value = nextEffectiveStorageQuotaBytes
    storageUsedBytes.value = nextStorageUsedBytes
    isAuthenticated.value = !!newToken

    if (user) {
      localStorage.setItem('auth_username', user)
      localStorage.setItem('admin_username', user)
    }
    if (nextRole) {
      localStorage.setItem('auth_role', nextRole)
    } else {
      localStorage.removeItem('auth_role')
    }
    if (nextUserId) {
      localStorage.setItem('auth_user_id', nextUserId)
    } else {
      localStorage.removeItem('auth_user_id')
    }
    if (nextSlug) {
      localStorage.setItem('auth_slug', nextSlug)
    } else {
      localStorage.removeItem('auth_slug')
    }
    if (nextNickname) {
      localStorage.setItem('auth_nickname', nextNickname)
    } else {
      localStorage.removeItem('auth_nickname')
    }
    if (nextPhone) {
      localStorage.setItem('auth_phone', nextPhone)
    } else {
      localStorage.removeItem('auth_phone')
    }
    localStorage.setItem('auth_phone_verified', String(nextPhoneVerified))
    if (nextEmail) {
      localStorage.setItem('auth_email', nextEmail)
    } else {
      localStorage.removeItem('auth_email')
    }
    localStorage.setItem('auth_email_verified', String(nextEmailVerified))
    if (nextProjectNameZh) {
      localStorage.setItem('auth_project_name_zh', nextProjectNameZh)
    } else {
      localStorage.removeItem('auth_project_name_zh')
    }
    if (nextProjectNameEn) {
      localStorage.setItem('auth_project_name_en', nextProjectNameEn)
    } else {
      localStorage.removeItem('auth_project_name_en')
    }
    if (nextAvatarPath) {
      localStorage.setItem('auth_avatar_path', nextAvatarPath)
    } else {
      localStorage.removeItem('auth_avatar_path')
    }
    if (nextCurrentVipPlanId) {
      localStorage.setItem('auth_current_vip_plan_id', nextCurrentVipPlanId)
    } else {
      localStorage.removeItem('auth_current_vip_plan_id')
    }
    if (nextCurrentVipPlanName) {
      localStorage.setItem('auth_current_vip_plan_name', nextCurrentVipPlanName)
    } else {
      localStorage.removeItem('auth_current_vip_plan_name')
    }
    if (nextCurrentVipPlanCode) {
      localStorage.setItem('auth_current_vip_plan_code', nextCurrentVipPlanCode)
    } else {
      localStorage.removeItem('auth_current_vip_plan_code')
    }
    if (nextVipExpireAt) {
      localStorage.setItem('auth_vip_expire_at', nextVipExpireAt)
    } else {
      localStorage.removeItem('auth_vip_expire_at')
    }
    localStorage.setItem('auth_effective_storage_quota_bytes', String(nextEffectiveStorageQuotaBytes))
    localStorage.setItem('auth_storage_used_bytes', String(nextStorageUsedBytes))
    localStorage.setItem('auth_multi_user_enabled', String(nextMultiUserEnabled))
  }

  const login = async (payload: { username: string; password?: string; code?: string; loginType?: 'password' | 'smsCode' | 'emailCode' }) => {
    try {
      console.log('开始登录请求:', { username: payload.username, loginType: payload.loginType || 'password' })
      const response = await axios.post('/api/auth/login', {
        username: payload.username,
        password: payload.password,
        code: payload.code,
        loginType: payload.loginType || 'password'
      })
      console.log('登录响应:', response.data)
      
      const responseData = response.data
      
      // 处理响应数据
      const newToken = responseData.token
      if (!newToken) {
        console.error('未收到Token:', responseData)
        return { 
          success: false, 
          message: '登录失败：未收到Token' 
        }
      }
      
      persistProfile(responseData, payload.username)
      
      console.log('登录成功，Token已保存')
      return {
        success: true,
        message: responseData.message || '登录成功'
      }
    } catch (error: any) {
      console.error('登录错误:', error)
      console.error('错误响应:', error.response)
      
      let errorMessage = '登录失败，请检查用户名和密码'
      
      if (error.response) {
        // 服务器返回了错误响应
        const errorData = error.response.data
        errorMessage = errorData?.error || 
                      errorData?.message || 
                      `服务器错误: ${error.response.status}`
      } else if (error.request) {
        // 请求已发出但没有收到响应
        errorMessage = '无法连接到服务器，请检查后端服务是否运行'
      } else {
        // 请求配置出错
        errorMessage = error.message || '登录请求失败'
      }
      
      return { 
        success: false, 
        message: errorMessage
      }
    }
  }

  const sendLoginCode = async (phone: string) => {
    try {
      const response = await axios.post('/api/auth/send-code', { phone })
      return {
        success: true,
        message: response.data?.message || '验证码已发送',
        debugCode: response.data?.debugCode || null,
        expiresInSeconds: response.data?.expiresInSeconds || 300
      }
    } catch (error: any) {
      let errorMessage = '验证码发送失败'
      if (error.response) {
        const errorData = error.response.data
        errorMessage = errorData?.error || errorData?.message || `服务器错误: ${error.response.status}`
      } else if (error.request) {
        errorMessage = '无法连接到服务器，请检查后端服务是否运行'
      } else {
        errorMessage = error.message || '验证码发送失败'
      }
      return {
        success: false,
        message: errorMessage,
        debugCode: null,
        expiresInSeconds: 0
      }
    }
  }

  const sendEmailLoginCode = async (emailAddress: string) => {
    try {
      const response = await axios.post('/api/auth/email/send-code', { email: emailAddress })
      return {
        success: true,
        message: response.data?.message || '验证码已发送',
        debugCode: response.data?.debugCode || null,
        expiresInSeconds: response.data?.expiresInSeconds || 300
      }
    } catch (error: any) {
      let errorMessage = '验证码发送失败'
      if (error.response) {
        const errorData = error.response.data
        errorMessage = errorData?.error || errorData?.message || `服务器错误: ${error.response.status}`
      } else if (error.request) {
        errorMessage = '无法连接到服务器，请检查后端服务是否运行'
      } else {
        errorMessage = error.message || '验证码发送失败'
      }
      return {
        success: false,
        message: errorMessage,
        debugCode: null,
        expiresInSeconds: 0
      }
    }
  }

  const sendPasswordResetPhoneCode = async (phoneNumber: string) => {
    try {
      const response = await axios.post('/api/auth/password-reset/phone/send-code', { phone: phoneNumber })
      return {
        success: true,
        message: response.data?.message || '验证码已发送',
        debugCode: response.data?.debugCode || null,
        expiresInSeconds: response.data?.expiresInSeconds || 300
      }
    } catch (error: any) {
      let errorMessage = '验证码发送失败'
      if (error.response) {
        const errorData = error.response.data
        errorMessage = errorData?.error || errorData?.message || `服务器错误: ${error.response.status}`
      } else if (error.request) {
        errorMessage = '无法连接到服务器，请检查后端服务是否运行'
      } else {
        errorMessage = error.message || '验证码发送失败'
      }
      return {
        success: false,
        message: errorMessage,
        debugCode: null,
        expiresInSeconds: 0
      }
    }
  }

  const sendPasswordResetEmailCode = async (emailAddress: string) => {
    try {
      const response = await axios.post('/api/auth/password-reset/email/send-code', { email: emailAddress })
      return {
        success: true,
        message: response.data?.message || '验证码已发送',
        debugCode: response.data?.debugCode || null,
        expiresInSeconds: response.data?.expiresInSeconds || 300
      }
    } catch (error: any) {
      let errorMessage = '验证码发送失败'
      if (error.response) {
        const errorData = error.response.data
        errorMessage = errorData?.error || errorData?.message || `服务器错误: ${error.response.status}`
      } else if (error.request) {
        errorMessage = '无法连接到服务器，请检查后端服务是否运行'
      } else {
        errorMessage = error.message || '验证码发送失败'
      }
      return {
        success: false,
        message: errorMessage,
        debugCode: null,
        expiresInSeconds: 0
      }
    }
  }

  const resetPasswordByPhone = async (payload: { phone: string; code: string; newPassword: string; confirmPassword: string }) => {
    try {
      const response = await axios.post('/api/auth/password-reset/phone/confirm', payload)
      return {
        success: true,
        message: response.data?.message || '密码已重置'
      }
    } catch (error: any) {
      let errorMessage = '重置密码失败'
      if (error.response) {
        const errorData = error.response.data
        errorMessage = errorData?.error || errorData?.message || `服务器错误: ${error.response.status}`
      } else if (error.request) {
        errorMessage = '无法连接到服务器，请检查后端服务是否运行'
      } else {
        errorMessage = error.message || '重置密码失败'
      }
      return {
        success: false,
        message: errorMessage
      }
    }
  }

  const resetPasswordByEmail = async (payload: { email: string; code: string; newPassword: string; confirmPassword: string }) => {
    try {
      const response = await axios.post('/api/auth/password-reset/email/confirm', payload)
      return {
        success: true,
        message: response.data?.message || '密码已重置'
      }
    } catch (error: any) {
      let errorMessage = '重置密码失败'
      if (error.response) {
        const errorData = error.response.data
        errorMessage = errorData?.error || errorData?.message || `服务器错误: ${error.response.status}`
      } else if (error.request) {
        errorMessage = '无法连接到服务器，请检查后端服务是否运行'
      } else {
        errorMessage = error.message || '重置密码失败'
      }
      return {
        success: false,
        message: errorMessage
      }
    }
  }

  const register = async (payload: {
    username: string
    password: string
    confirmPassword: string
    nickname?: string
    slug?: string
    phone?: string
    email?: string
  }) => {
    try {
      const response = await axios.post('/api/auth/register', payload)
      persistProfile(response.data, payload.username)
      return {
        success: true,
        message: response.data.message || '注册成功'
      }
    } catch (error: any) {
      let errorMessage = '注册失败，请稍后重试'
      if (error.response) {
        const errorData = error.response.data
        errorMessage = errorData?.error ||
          errorData?.message ||
          `服务器错误: ${error.response.status}`
      } else if (error.request) {
        errorMessage = '无法连接到服务器，请检查后端服务是否运行'
      } else {
        errorMessage = error.message || '注册请求失败'
      }
      return {
        success: false,
        message: errorMessage
      }
    }
  }

  const logout = () => {
    token.value = null
    username.value = null
    role.value = null
    userId.value = null
    slug.value = null
    nickname.value = null
    phone.value = null
    phoneVerified.value = false
    email.value = null
    emailVerified.value = false
    projectNameZh.value = null
    projectNameEn.value = null
    avatarPath.value = null
    multiUserEnabled.value = false
    smsLoginEnabled.value = false
    currentVipPlanId.value = null
    currentVipPlanName.value = null
    currentVipPlanCode.value = null
    vipExpireAt.value = null
    effectiveStorageQuotaBytes.value = 0
    storageUsedBytes.value = 0
    isAuthenticated.value = false
    clearStoredAuthSession()
  }

  const checkAuth = async () => {
    if (!token.value) {
      return false
    }
    
    try {
      const response = await axios.get('/api/auth/validate', {
        headers: { Authorization: `Bearer ${token.value}` }
      })
      return response.data
    } catch {
      logout()
      return false
    }
  }

  const fetchCurrentUser = async () => {
    if (!token.value) {
      return null
    }

    const response = await axios.get('/api/auth/me', {
      headers: { Authorization: `Bearer ${token.value}` }
    })
    persistProfile(response.data)
    return response.data
  }

  const fetchPublicSettings = async () => {
    try {
      const response = await axios.get('/api/auth/public-settings')
      multiUserEnabled.value = !!response.data?.multiUserEnabled
      smsLoginEnabled.value = !!response.data?.smsLoginEnabled
      emailCodeLoginEnabled.value = !!response.data?.emailCodeLoginEnabled
      localStorage.setItem('auth_multi_user_enabled', String(multiUserEnabled.value))
      localStorage.setItem('auth_sms_login_enabled', String(smsLoginEnabled.value))
      localStorage.setItem('auth_email_code_login_enabled', String(emailCodeLoginEnabled.value))
      publicSettingsLoaded.value = true
      return response.data
    } catch {
      publicSettingsLoaded.value = false
      return null
    }
  }

  const bootstrap = async () => {
    if (isBootstrapping.value) {
      return
    }

    isBootstrapping.value = true
    try {
      await fetchPublicSettings()
      if (!token.value) {
        return
      }
      const valid = await checkAuth()
      if (!valid) {
        return
      }
      await fetchCurrentUser()
    } catch {
      logout()
    } finally {
      isBootstrapping.value = false
    }
  }

  return {
    token,
    username,
    role,
    userId,
    slug,
    nickname,
    phone,
    phoneVerified,
    email,
    emailVerified,
    projectNameZh,
    projectNameEn,
    avatarPath,
    multiUserEnabled,
    smsLoginEnabled,
    emailCodeLoginEnabled,
    currentVipPlanId,
    currentVipPlanName,
    currentVipPlanCode,
    vipExpireAt,
    effectiveStorageQuotaBytes,
    storageUsedBytes,
    isAuthenticated,
    isBootstrapping,
    publicSettingsLoaded,
    displayName,
    isSuperAdmin,
    projectDisplayName,
    login,
    sendLoginCode,
    sendEmailLoginCode,
    sendPasswordResetPhoneCode,
    sendPasswordResetEmailCode,
    resetPasswordByPhone,
    resetPasswordByEmail,
    register,
    logout,
    checkAuth,
    fetchCurrentUser,
    fetchPublicSettings,
    bootstrap
  }
})
