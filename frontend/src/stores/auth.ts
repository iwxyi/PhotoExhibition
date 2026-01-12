import { defineStore } from 'pinia'
import { ref } from 'vue'
import axios from 'axios'

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(localStorage.getItem('admin_token'))
  const username = ref<string | null>(localStorage.getItem('admin_username'))
  const isAuthenticated = ref<boolean>(!!token.value)

  // 设置axios默认请求头
  if (token.value) {
    axios.defaults.headers.common['Authorization'] = `Bearer ${token.value}`
  }

  const login = async (loginUsername: string, loginPassword: string) => {
    try {
      console.log('开始登录请求:', { username: loginUsername })
      const response = await axios.post('/api/auth/login', { 
        username: loginUsername, 
        password: loginPassword 
      })
      console.log('登录响应:', response.data)
      
      const responseData = response.data
      
      // 处理响应数据
      const newToken = responseData.token
      const user = responseData.username || loginUsername
      
      if (!newToken) {
        console.error('未收到Token:', responseData)
        return { 
          success: false, 
          message: '登录失败：未收到Token' 
        }
      }
      
      // 使用 store 中的 ref，不是参数
      token.value = newToken
      username.value = user
      isAuthenticated.value = true
      
      localStorage.setItem('admin_token', newToken)
      localStorage.setItem('admin_username', user)
      
      axios.defaults.headers.common['Authorization'] = `Bearer ${newToken}`
      
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

  const logout = () => {
    token.value = null
    username.value = null
    isAuthenticated.value = false
    localStorage.removeItem('admin_token')
    localStorage.removeItem('admin_username')
    delete axios.defaults.headers.common['Authorization']
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

  return {
    token,
    username,
    isAuthenticated,
    login,
    logout,
    checkAuth
  }
})

