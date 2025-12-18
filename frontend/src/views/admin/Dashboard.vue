<template>
  <div class="min-h-screen admin-shell text-white">
    <!-- 顶部导航 -->
    <nav class="glass-toolbar">
      <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div class="flex justify-between h-16">
          <div class="flex items-center">
            <h1 class="text-xl font-light tracking-wide">管理后台</h1>
          </div>
          <div class="flex items-center space-x-4">
            <button
              @click="themeStore.toggleTheme"
              class="p-2 rounded-full bg-black/20 hover:bg-black/35 border border-white/15 transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-300/80"
              :title="themeStore.isDark ? '切换为浅色模式' : '切换为深色模式'"
            >
              <svg v-if="!themeStore.isDark" class="w-4 h-4 text-amber-200" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M20.354 15.354A9 9 0 018.646 3.646 9.003 9.003 0 0012 21a9.003 9.003 0 008.354-5.646z" />
              </svg>
              <svg v-else class="w-4 h-4 text-sky-200" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 3v1m0 16v1m9-9h-1M4 12H3m15.364 6.364l-.707-.707M6.343 6.343l-.707-.707m12.728 0l-.707.707M6.343 17.657l-.707.707M16 12a4 4 0 11-8 0 4 4 0 018 0z" />
              </svg>
            </button>
            <span class="text-gray-200/80 text-sm">欢迎，{{ authStore.username }}</span>
            <button
              @click="handleLogout"
              class="px-3 py-1.5 text-xs bg-gray-900/70 hover:bg-gray-800 rounded-lg transition-colors border border-white/15"
            >
              退出登录
            </button>
            <router-link
              to="/"
              class="btn-primary text-xs"
            >
              返回首页
            </router-link>
          </div>
        </div>
      </div>
    </nav>

    <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8 space-y-8">
      <!-- Hero 区域 -->
      <section class="admin-hero">
        <div class="admin-hero-gradient"></div>
        <div class="admin-hero-particle admin-hero-particle--small"></div>
        <div class="admin-hero-particle admin-hero-particle--medium"></div>
        <div class="admin-hero-particle admin-hero-particle--large"></div>
        <div class="admin-hero-content">
          <div class="space-y-4">
            <div class="admin-hero-metric">
              <span class="admin-hero-metric-dot"></span>
              <span>Photo Exhibition · Admin</span>
            </div>
            <div>
              <h2 class="text-2xl sm:text-3xl lg:text-4xl font-light tracking-wide mb-2">
                智能相册中枢
              </h2>
              <p class="text-sm sm:text-base text-slate-300/90 max-w-xl">
                统一管理相册、标签、人脸与文件系统，配合自动扫描与 AI 能力，让你的作品集始终保持有序而精彩。
              </p>
            </div>
            <div class="grid grid-cols-2 sm:grid-cols-3 gap-4 pt-1">
        <router-link
          to="/admin/albums"
                class="admin-hero-stat hover:bg-slate-900/70 transition-colors cursor-pointer"
        >
                <div class="admin-hero-stat-label">相册</div>
                <div class="admin-hero-stat-value">{{ stats.albums }}</div>
        </router-link>
        <router-link
          to="/admin/photos"
                class="admin-hero-stat hover:bg-slate-900/70 transition-colors cursor-pointer"
        >
                <div class="admin-hero-stat-label">照片</div>
                <div class="admin-hero-stat-value">{{ stats.photos }}</div>
        </router-link>
        <router-link
          to="/admin/persons"
                class="admin-hero-stat hover:bg-slate-900/70 transition-colors cursor-pointer"
        >
                <div class="admin-hero-stat-label">已识别人脸</div>
                <div class="admin-hero-stat-value">{{ stats.faces }}</div>
        </router-link>
            </div>
          </div>
          <div class="admin-hero-secondary space-y-3">
            <div class="flex items-center justify-between gap-4">
              <div class="text-xs text-slate-300/90">
                <div class="font-medium mb-1">最近扫描状态</div>
                <div class="space-y-0.5">
                  <p>状态：<span class="text-sky-300">{{ scanStatus }}</span></p>
                  <p>进度：<span class="text-sky-300">{{ scanProgressText }}</span></p>
                  <p>时间：<span class="text-slate-300">{{ lastScanTime || '—' }}</span></p>
                </div>
              </div>
            <button
              @click="triggerScan"
              :disabled="scanning"
                class="btn-primary text-xs disabled:opacity-60"
            >
                {{ scanning ? '扫描中…' : '立即触发扫描' }}
            </button>
            </div>
          </div>
        </div>
      </section>

        <!-- 数据管理 -->
      <div class="glass-panel p-6 admin-card-animate admin-card-4">
          <h2 class="text-xl font-light mb-4">数据管理</h2>
          <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <router-link
              to="/admin/tags"
            class="block px-4 py-3 bg-gray-900/60 hover:bg-gray-700 rounded-lg transition-colors border border-white/10"
            >
              <div class="text-center">标签管理</div>
              <div class="text-xs text-gray-400 text-center mt-1">{{ stats.tags }} 个</div>
            </router-link>
            <router-link
              to="/admin/migration"
            class="block px-4 py-3 bg-gray-900/60 hover:bg-gray-700 rounded-lg transition-colors text-center border border-white/10"
            >
              数据迁移
            </router-link>
            <router-link
              to="/admin/file-browser"
            class="block px-4 py-3 bg-gray-900/60 hover:bg-gray-700 rounded-lg transition-colors text-center border border-white/10"
            >
              文件浏览器
            </router-link>
          <router-link
            to="/admin/theme"
            class="block px-4 py-3 bg-gray-900/60 hover:bg-gray-700 rounded-lg transition-colors text-center border border-white/10"
          >
            主题与风格
          </router-link>
        </div>
      </div>

      <!-- API测试工具 -->
      <div class="glass-panel p-6">
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
import { ref, onMounted, onUnmounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useThemeStore } from '@/stores/theme'
import { api } from '@/api'

const router = useRouter()
const authStore = useAuthStore()
const themeStore = useThemeStore()

const stats = ref({
  albums: 0,
  photos: 0,
  tags: 0,
  persons: 0,
  faces: 0
})

const scanning = ref(false)
const lastScanTime = ref<string | null>(null)
const scanProgress = ref<{ current: number; total: number }>({ current: 0, total: 0 })
const scanStatus = computed(() => (scanning.value ? '扫描中' : '空闲'))
const scanProgressText = computed(() => {
  const { current, total } = scanProgress.value
  if (!total) return '0 / 0'
  return `${current} / ${total} (${Math.min(100, Math.floor((current / total) * 100))}%)`
})
const selectedApi = ref('')
const testing = ref(false)
const apiResponse = ref<any>(null)
const pathInput = ref('')
const faceIdInput = ref('')
const topInput = ref('')
const thresholdInput = ref('')

const loadStats = async () => {
  try {
    const [albumsRes, photosRes, tagsRes, personsRes, facesRes] = await Promise.all([
      api.get('/albums', { params: { size: 1, page: 0 } }),
      api.get('/photos', { params: { size: 1, page: 0 } }),
      api.get('/tags', { params: { size: 1, page: 0 } }),
      api.get('/admin/persons/items', { params: { threshold: 0.7 } }),
      api.get('/admin/faces', { params: { size: 1, page: 0 } })
    ])
    
    const albumTotal = albumsRes.data.totalElements ?? albumsRes.data.total ?? 0
    const photoTotal = photosRes.data.totalElements ?? photosRes.data.total ?? 0
    const tagTotal = Array.isArray(tagsRes.data)
      ? tagsRes.data.length
      : (tagsRes.data.totalElements ?? tagsRes.data.total ?? 0)
    const personTotal = Array.isArray(personsRes.data)
      ? personsRes.data.filter((p: any) => p.type === 'confirmed').length
      : 0
    const faceTotal = facesRes.data.totalElements ?? facesRes.data.total ?? 0

    stats.value = {
      albums: albumTotal,
      photos: photoTotal,
      tags: tagTotal,
      persons: personTotal,
      faces: faceTotal
    }
  } catch (error) {
    console.error('加载统计信息失败:', error)
  }
}

const triggerScan = async () => {
  scanning.value = true
  lastScanTime.value = new Date().toLocaleString('zh-CN')
  try {
    await api.post('/admin/scan')
    alert('扫描任务已触发')
  } catch (error: any) {
    alert('触发扫描失败: ' + (error.response?.data?.message || error.message))
  } finally {
    scanning.value = false
  }
}

const fetchScanStatus = async () => {
  try {
    const res = await api.get('/admin/scan/status')
    const data = res.data || {}
    scanning.value = !!data.scanning
    scanProgress.value = {
      current: data.current ?? 0,
      total: data.total ?? 0
    }
    if (data.lastScanStart) {
      lastScanTime.value = new Date(data.lastScanStart).toLocaleString('zh-CN')
    }
  } catch (e) {
    // ignore
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

let scanTimer: number | null = null

onMounted(async () => {
  await loadStats()
  await fetchScanStatus()
  scanTimer = window.setInterval(fetchScanStatus, 5000)
})

onUnmounted(() => {
  if (scanTimer) {
    clearInterval(scanTimer)
    scanTimer = null
  }
})

const showPathInput = computed(() => selectedApi.value.includes('/admin/scan'))
const showFaceSimilarInputs = computed(() => selectedApi.value.includes('/admin/faces/{id}/similar'))
</script>

