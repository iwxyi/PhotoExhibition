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
        <router-link
          to="/admin/albums"
          class="bg-gray-800 rounded-lg p-6 block hover:bg-gray-700 transition-colors"
        >
          <h3 class="text-gray-400 text-sm mb-2">相册总数</h3>
          <p class="text-3xl font-light">{{ stats.albums }}</p>
        </router-link>
        <router-link
          to="/admin/photos"
          class="bg-gray-800 rounded-lg p-6 block hover:bg-gray-700 transition-colors"
        >
          <h3 class="text-gray-400 text-sm mb-2">图片总数</h3>
          <p class="text-3xl font-light">{{ stats.photos }}</p>
        </router-link>
        <router-link
          to="/admin/tags"
          class="bg-gray-800 rounded-lg p-6 block hover:bg-gray-700 transition-colors"
        >
          <h3 class="text-gray-400 text-sm mb-2">标签总数</h3>
          <p class="text-3xl font-light">{{ stats.tags }}</p>
        </router-link>
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
          <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <router-link
              to="/admin/faces"
              class="block px-4 py-3 bg-gray-700 hover:bg-gray-600 rounded-lg transition-colors text-center"
            >
              人脸管理
            </router-link>
            <router-link
              to="/admin/persons"
              class="block px-4 py-3 bg-gray-700 hover:bg-gray-600 rounded-lg transition-colors text-center"
            >
              人物管理
            </router-link>
            <router-link
              to="/admin/migration"
              class="block px-4 py-3 bg-gray-700 hover:bg-gray-600 rounded-lg transition-colors text-center"
            >
              数据迁移
            </router-link>
            <router-link
              to="/admin/file-browser"
              class="block px-4 py-3 bg-gray-700 hover:bg-gray-600 rounded-lg transition-colors text-center"
            >
              文件浏览器
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
              <option value="GET /albums">获取所有相册</option>
              <option value="GET /photos">获取所有图片</option>
              <option value="GET /tags">获取所有标签</option>
              <option value="POST /admin/scan">触发扫描</option>
              <option value="POST /admin/scan/force">强制扫描（重建缩略图/人脸/标签）</option>
              <option value="GET /admin/faces/{id}/similar">相似人脸查询</option>
              <option value="POST /admin/cleanup/all">清理所有数据（只保留账号）</option>
            </select>
          </div>
          <div v-if="showPathInput">
            <label class="block text-sm text-gray-400 mb-2">可选：指定扫描路径</label>
            <input
              v-model="pathInput"
              placeholder="不填则使用配置的 base-path"
              class="w-full px-4 py-2 bg-gray-700 border border-gray-600 rounded-lg text-white focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>
          <div v-if="showFaceSimilarInputs" class="grid grid-cols-1 sm:grid-cols-3 gap-3">
            <div>
              <label class="block text-sm text-gray-400 mb-1">人脸ID</label>
              <input
                v-model="faceIdInput"
                placeholder="必填"
                class="w-full px-3 py-2 bg-gray-700 border border-gray-600 rounded text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
            </div>
            <div>
              <label class="block text-sm text-gray-400 mb-1">Top</label>
              <input
                v-model="topInput"
                type="number"
                min="1"
                placeholder="默认10"
                class="w-full px-3 py-2 bg-gray-700 border border-gray-600 rounded text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
            </div>
            <div>
              <label class="block text-sm text-gray-400 mb-1">阈值</label>
              <input
                v-model="thresholdInput"
                type="number"
                step="0.01"
                min="0"
                max="1"
                placeholder="默认0.6"
                class="w-full px-3 py-2 bg-gray-700 border border-gray-600 rounded text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
            </div>
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
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { api } from '@/api'

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
const pathInput = ref('')
const faceIdInput = ref('')
const topInput = ref('')
const thresholdInput = ref('')

const loadStats = async () => {
  try {
    const [albumsRes, photosRes, tagsRes] = await Promise.all([
      api.get('/albums', { params: { size: 1, page: 0 } }),
      api.get('/photos', { params: { size: 1, page: 0 } }),
      api.get('/tags', { params: { size: 1, page: 0 } })
    ])
    
    const albumTotal = albumsRes.data.totalElements ?? albumsRes.data.total ?? 0
    const photoTotal = photosRes.data.totalElements ?? photosRes.data.total ?? 0
    const tagTotal = Array.isArray(tagsRes.data)
      ? tagsRes.data.length
      : (tagsRes.data.totalElements ?? tagsRes.data.total ?? 0)

    stats.value = {
      albums: albumTotal,
      photos: photoTotal,
      tags: tagTotal
    }
  } catch (error) {
    console.error('加载统计信息失败:', error)
  }
}

const triggerScan = async () => {
  scanning.value = true
  try {
    await api.post('/admin/scan')
    alert('扫描任务已触发')
  } catch (error: any) {
    alert('触发扫描失败: ' + (error.response?.data?.message || error.message))
  } finally {
    scanning.value = false
  }
}

const testApi = async () => {
  if (!selectedApi.value) return
  
  // 清理所有数据需要二次确认
  if (selectedApi.value === 'POST /admin/cleanup/all') {
    const confirmed = confirm(
      '⚠️ 危险操作警告 ⚠️\n\n' +
      '此操作将删除所有数据，包括：\n' +
      '• 所有照片\n' +
      '• 所有相册\n' +
      '• 所有标签\n' +
      '• 所有人脸\n' +
      '• 所有人物\n\n' +
      '只保留账号数据（AdminUser）\n\n' +
      '此操作不可恢复！\n\n' +
      '确定要继续吗？'
    )
    if (!confirmed) {
      return
    }
    // 再次确认
    const doubleConfirmed = confirm('请再次确认：你真的要删除所有数据吗？')
    if (!doubleConfirmed) {
      return
    }
  }
  
  testing.value = true
  apiResponse.value = null
  
  try {
    let [method, path] = selectedApi.value.split(' ')
    const params: any = {}

    if (showFaceSimilarInputs.value) {
      if (!faceIdInput.value.trim()) {
        alert('请填写人脸ID')
        testing.value = false
        return
      }
      path = path.replace('{id}', faceIdInput.value.trim())
      if (topInput.value) params.top = topInput.value
      if (thresholdInput.value) params.threshold = thresholdInput.value
    }

    const config: any = {
      method: method.toLowerCase(),
      url: path
    }
    if (showPathInput.value && pathInput.value.trim()) {
      config.params = { path: pathInput.value.trim() }
    }
    if (Object.keys(params).length) {
      config.params = { ...(config.params || {}), ...params }
    }
    const response = await api(config)
    apiResponse.value = response.data
    
    // 清理成功后刷新统计数据
    if (selectedApi.value === 'POST /admin/cleanup/all' && response.data.success) {
      await loadStats()
      alert('数据清理完成！')
    }
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

const showPathInput = computed(() => selectedApi.value.includes('/admin/scan'))
const showFaceSimilarInputs = computed(() => selectedApi.value.includes('/admin/faces/{id}/similar'))
</script>

