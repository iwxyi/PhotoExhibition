<template>
  <div class="min-h-screen bg-gray-900 text-white">
    <!-- 顶部导航 -->
    <nav class="bg-gray-800 border-b border-gray-700">
      <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div class="flex justify-between h-16">
          <div class="flex items-center">
            <h1 class="text-xl font-light">管理后台</h1>
          </div>
          <div class="flex items-center space-x-4">
            <span class="text-gray-400">欢迎，{{ authStore.username }}</span>
            <button
              @click="handleLogout"
              class="px-4 py-2 text-sm bg-gray-700 hover:bg-gray-600 rounded-lg transition-colors"
            >
              退出登录
            </button>
            <router-link
              to="/"
              class="px-4 py-2 text-sm bg-blue-600 hover:bg-blue-700 rounded-lg transition-colors"
            >
              返回首页
            </router-link>
          </div>
        </div>
      </div>
    </nav>

    <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
      <!-- 统计卡片 -->
      <div class="grid grid-cols-1 md:grid-cols-3 gap-6 mb-8">
        <div class="bg-gray-800 rounded-lg p-6">
          <h3 class="text-gray-400 text-sm mb-2">相册总数</h3>
          <p class="text-3xl font-light">{{ stats.albums }}</p>
        </div>
        <div class="bg-gray-800 rounded-lg p-6">
          <h3 class="text-gray-400 text-sm mb-2">图片总数</h3>
          <p class="text-3xl font-light">{{ stats.photos }}</p>
        </div>
        <div class="bg-gray-800 rounded-lg p-6">
          <h3 class="text-gray-400 text-sm mb-2">标签总数</h3>
          <p class="text-3xl font-light">{{ stats.tags }}</p>
        </div>
      </div>

      <!-- 操作面板 -->
      <div class="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <!-- API操作面板 -->
        <div class="bg-gray-800 rounded-lg p-6">
          <h2 class="text-xl font-light mb-4">API操作</h2>
          <div class="space-y-4">
            <button
              @click="triggerScan"
              :disabled="scanning"
              class="w-full px-4 py-2 bg-blue-600 hover:bg-blue-700 rounded-lg transition-colors disabled:opacity-50"
            >
              {{ scanning ? '扫描中...' : '触发图片扫描' }}
            </button>
            <div class="text-sm text-gray-400">
              <p>• 手动触发图片目录扫描</p>
              <p>• 提取EXIF信息并生成缩略图</p>
            </div>
          </div>
        </div>

        <!-- 数据管理 -->
        <div class="bg-gray-800 rounded-lg p-6">
          <h2 class="text-xl font-light mb-4">数据管理</h2>
          <div class="space-y-2">
            <router-link
              to="/admin/albums"
              class="block px-4 py-2 bg-gray-700 hover:bg-gray-600 rounded-lg transition-colors"
            >
              相册管理
            </router-link>
            <router-link
              to="/admin/photos"
              class="block px-4 py-2 bg-gray-700 hover:bg-gray-600 rounded-lg transition-colors"
            >
              图片管理
            </router-link>
            <router-link
              to="/admin/tags"
              class="block px-4 py-2 bg-gray-700 hover:bg-gray-600 rounded-lg transition-colors"
            >
              标签管理
            </router-link>
          </div>
        </div>
      </div>

      <!-- API测试工具 -->
      <div class="mt-8 bg-gray-800 rounded-lg p-6">
        <h2 class="text-xl font-light mb-4">API测试工具</h2>
        <div class="space-y-4">
          <div>
            <label class="block text-sm text-gray-400 mb-2">选择API端点</label>
            <select
              v-model="selectedApi"
              class="w-full px-4 py-2 bg-gray-700 border border-gray-600 rounded-lg text-white focus:outline-none focus:ring-2 focus:ring-blue-500"
            >
              <option value="">-- 选择API --</option>
              <option value="GET /api/albums">获取所有相册</option>
              <option value="GET /api/photos">获取所有图片</option>
              <option value="GET /api/tags">获取所有标签</option>
              <option value="POST /api/admin/scan">触发扫描</option>
            </select>
          </div>
          <button
            @click="testApi"
            :disabled="!selectedApi || testing"
            class="px-4 py-2 bg-green-600 hover:bg-green-700 rounded-lg transition-colors disabled:opacity-50"
          >
            {{ testing ? '请求中...' : '发送请求' }}
          </button>
          <div v-if="apiResponse" class="mt-4">
            <label class="block text-sm text-gray-400 mb-2">响应结果</label>
            <pre class="bg-gray-900 p-4 rounded-lg overflow-auto text-sm">{{ JSON.stringify(apiResponse, null, 2) }}</pre>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import axios from 'axios'

const router = useRouter()
const authStore = useAuthStore()

const stats = ref({
  albums: 0,
  photos: 0,
  tags: 0
})

const scanning = ref(false)
const selectedApi = ref('')
const testing = ref(false)
const apiResponse = ref<any>(null)

const loadStats = async () => {
  try {
    const [albumsRes, photosRes, tagsRes] = await Promise.all([
      axios.get('/api/albums?size=1'),
      axios.get('/api/photos?size=1'),
      axios.get('/api/tags')
    ])
    
    stats.value = {
      albums: albumsRes.data.totalElements || 0,
      photos: photosRes.data.totalElements || 0,
      tags: tagsRes.data.length || 0
    }
  } catch (error) {
    console.error('加载统计信息失败:', error)
  }
}

const triggerScan = async () => {
  scanning.value = true
  try {
    await axios.post('/api/admin/scan')
    alert('扫描任务已触发')
  } catch (error: any) {
    alert('触发扫描失败: ' + (error.response?.data?.message || error.message))
  } finally {
    scanning.value = false
  }
}

const testApi = async () => {
  if (!selectedApi.value) return
  
  testing.value = true
  apiResponse.value = null
  
  try {
    const [method, path] = selectedApi.value.split(' ')
    const response = await axios({
      method: method.toLowerCase(),
      url: path
    })
    apiResponse.value = response.data
  } catch (error: any) {
    apiResponse.value = {
      error: true,
      message: error.message,
      response: error.response?.data
    }
  } finally {
    testing.value = false
  }
}

const handleLogout = () => {
  authStore.logout()
  router.push('/admin/login')
}

onMounted(() => {
  loadStats()
})
</script>

