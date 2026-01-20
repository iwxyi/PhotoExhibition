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
              class="px-3 py-1.5 text-xs bg-blue-600 hover:bg-blue-700 text-white rounded-lg transition-colors border border-blue-500/30"
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
                style="padding: 14px 16px;"
        >
                <div class="admin-hero-stat-label">相册</div>
                <div class="admin-hero-stat-value">{{ stats.albums }}</div>
        </router-link>
        <router-link
          to="/admin/photos"
                class="admin-hero-stat hover:bg-slate-900/70 transition-colors cursor-pointer"
                style="padding: 14px 16px;"
        >
                <div class="admin-hero-stat-label">照片</div>
                <div class="admin-hero-stat-value">{{ stats.photos }}</div>
        </router-link>
        <router-link
          to="/admin/persons"
                class="admin-hero-stat hover:bg-slate-900/70 transition-colors cursor-pointer"
                style="padding: 14px 16px;"
        >
                <div class="admin-hero-stat-label">人物</div>
                <div class="admin-hero-stat-value">{{ stats.persons }}</div>
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
                class="px-3 py-1.5 text-xs bg-blue-600 hover:bg-blue-700 text-white rounded-lg transition-colors disabled:opacity-60 border border-blue-500/30"
            >
                {{ scanning ? '扫描中…' : '立即触发扫描' }}
            </button>
            </div>
          </div>
        </div>
      </section>

        <!-- 数据管理和API测试工具 -->
      <div class="grid grid-cols-1 lg:grid-cols-2 gap-4">
        <div class="glass-panel p-4 admin-card-animate admin-card-4">
          <h2 class="text-lg font-light mb-3">数据管理</h2>
          <div class="grid grid-cols-2 gap-2">
            <router-link
              to="/admin/tags"
            class="block px-3 py-2 bg-gray-900/60 hover:bg-gray-700 rounded-lg transition-colors border border-white/10 text-sm"
            >
              <div class="text-center">标签管理</div>
              <div class="text-xs text-gray-400 text-center mt-0.5">{{ stats.tags }} 个</div>
            </router-link>
            <router-link
              to="/admin/migration"
            class="block px-3 py-2 bg-gray-900/60 hover:bg-gray-700 rounded-lg transition-colors text-center border border-white/10 text-sm"
            >
              数据迁移
            </router-link>
            <router-link
              to="/admin/file-browser"
            class="block px-3 py-2 bg-gray-900/60 hover:bg-gray-700 rounded-lg transition-colors text-center border border-white/10 text-sm"
            >
              文件浏览器
            </router-link>
          <router-link
            to="/admin/theme"
            class="block px-3 py-2 bg-gray-900/60 hover:bg-gray-700 rounded-lg transition-colors text-center border border-white/10 text-sm"
          >
            主题与风格
          </router-link>
          <router-link
            to="/admin/settings"
            class="block px-3 py-2 bg-gray-900/60 hover:bg-gray-700 rounded-lg transition-colors text-center border border-white/10 text-sm"
          >
            系统设置
          </router-link>
          </div>
        </div>

        <div class="glass-panel p-4 admin-card-animate admin-card-4">
        <h2 class="text-lg font-light mb-3">API测试工具</h2>
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
              <option value="POST /admin/scan/force">强制扫描（重新处理所有图片）</option>
              <option value="POST /admin/thumbnails/clear">清空缩略图（重新生成三级缩略图）</option>
              <option value="POST /admin/faces/clear">清空人脸数据（重新生成人脸识别）</option>
              <option value="POST /admin/smart-tags/clear">清空智能标签（重新生成AI标签）</option>
              <option value="POST /admin/cleanup/orphaned">清理删除残留（清理不存在文件的记录）</option>
              <option value="POST /admin/cleanup/duplicate-faces">清理重复人脸（删除同一照片的重复人脸记录）</option>
              <option value="POST /admin/albums/update-times">更新相册时间（重新计算拍摄时间和相册名时间）</option>
              <option value="POST /admin/photos/update-times">更新照片时间（重新从EXIF和路径提取拍摄时间）</option>
              <!-- 同步更新照片时间已移除；仅保留异步接口 -->
              <option value="POST /admin/update-exif-data">更新 EXIF 数值字段（回填历史图片）</option>
              <option value="POST /admin/update-color-categories">更新颜色分类（为历史图片设置颜色分类）</option>
              <option value="POST /admin/recalculate-photo-colors">更新照片颜色（重新计算色调、分类、相册氛围等）</option>
              <option value="POST /admin/ai-scoring/clear-all">清空照片AI评分</option>
              <option value="POST /admin/ai-scoring/update-all">强制重新评分所有图片AI评分</option>
              <option value="GET /admin/faces/{id}/similar">相似人脸查询</option>
              <option value="GET /admin/scan/analyze-unscanned">分析未扫描的文件</option>
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
          <div v-if="taskStatus" class="mt-4 bg-gray-900 p-3 rounded-lg">
            <div class="flex items-center justify-between mb-2">
              <div>
                <div class="text-sm text-gray-200">任务 ID: <span class="text-sky-300">{{ taskStatus.taskId }}</span></div>
                <div class="text-xs text-gray-400">状态: <span class="text-sky-300">{{ taskStatus.status }}</span></div>
                <div class="text-xs text-gray-400">进度: <span class="text-sky-300">{{ taskStatus.current }} / {{ taskStatus.total }}</span></div>
              </div>
              <div>
                <button @click="stopTaskPoll" class="px-2 py-1 text-xs bg-red-600 rounded">停止</button>
              </div>
            </div>
            <div class="text-xs text-gray-300 max-h-48 overflow-auto">
              <pre class="whitespace-pre-wrap break-words">{{ taskStatus.logs.join('\n') }}</pre>
            </div>
          </div>
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
  const percentage = total > 0 ? Math.min(100, Math.floor((current / total) * 100)) : 0
  return `${current} / ${total} (${percentage}%)`
})
const selectedApi = ref('')
const testing = ref(false)
const apiResponse = ref<any>(null)
const pathInput = ref('')
const faceIdInput = ref('')
const topInput = ref('')
const thresholdInput = ref('')

const taskStatus = ref<any | null>(null)
let taskPollTimer: number | null = null

const stopTaskPoll = () => {
  if (taskPollTimer) {
    clearInterval(taskPollTimer)
    taskPollTimer = null
  }
  taskStatus.value = null
}

const pollTask = async (taskId: string) => {
  stopTaskPoll()
  taskStatus.value = { taskId, status: 'pending', logs: [] }
  taskPollTimer = window.setInterval(async () => {
    try {
      const res = await api.get(`/admin/tasks/${taskId}`)
      const data = res.data
      if (data && data.found) {
        taskStatus.value = {
          taskId: data.taskId,
          status: data.status,
          current: data.current,
          total: data.total,
          complete: data.complete,
          logs: data.logs || []
        }
        if (data.complete) {
          stopTaskPoll()
        }
      } else {
        // not found -> stop polling
        stopTaskPoll()
      }
    } catch (e) {
      // ignore transient errors but stop after repeated failures could be added
    }
  }, 2000)
}

const loadStats = async () => {
  try {
    const [albumsRes, photosRes, tagsRes, personsRes, facesRes] = await Promise.all([
      api.get('/albums/count'),
      api.get('/photos', { params: { size: 1, page: 0 } }),
      api.get('/tags', { params: { size: 1, page: 0 } }),
      api.get('/admin/persons/count'),
      api.get('/admin/faces', { params: { size: 1, page: 0 } })
    ])

    const albumTotal = albumsRes.data ?? 0
    const photoTotal = photosRes.data.totalElements ?? photosRes.data.total ?? 0
    const tagTotal = Array.isArray(tagsRes.data)
      ? tagsRes.data.length
      : (tagsRes.data.totalElements ?? tagsRes.data.total ?? 0)
    const personTotal = personsRes.data ?? 0
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

  // 清空缩略图需要确认
  if (selectedApi.value === 'POST /admin/thumbnails/clear') {
    const confirmed = confirm(
      '🖼️ 清空缩略图数据\n\n' +
      '此操作将：\n' +
      '• 删除所有缩略图文件（小图、中图、大图）\n' +
      '• 清空数据库中的缩略图路径字段\n' +
      '• 为重新生成三级缩略图做准备\n\n' +
      '⚠️ 执行后需要运行强制扫描来重新生成缩略图。\n\n' +
      '确定要继续吗？'
    )
    if (!confirmed) {
      return
    }
  }

  // 清空人脸数据需要确认
  if (selectedApi.value === 'POST /admin/faces/clear') {
    const confirmed = confirm(
      '👤 清空人脸识别数据\n\n' +
      '此操作将：\n' +
      '• 删除所有人脸检测结果\n' +
      '• 删除所有人物聚类数据\n' +
      '• 清空照片中的人脸关联信息\n\n' +
      '⚠️ 执行后需要运行强制扫描来重新生成人脸识别。\n\n' +
      '确定要继续吗？'
    )
    if (!confirmed) {
      return
    }
  }

  // 清空智能标签需要确认
  if (selectedApi.value === 'POST /admin/smart-tags/clear') {
    const confirmed = confirm(
      '🏷️ 清空智能标签数据\n\n' +
      '此操作将：\n' +
      '• 删除所有AI生成的智能标签\n' +
      '• 保留用户手动添加的标签\n' +
      '• 清空照片中的智能标签关联\n\n' +
      '⚠️ 执行后需要运行强制扫描来重新生成AI标签。\n\n' +
      '确定要继续吗？'
    )
    if (!confirmed) {
      return
    }
  }

  // 清理残留数据需要确认
  if (selectedApi.value === 'POST /admin/cleanup/orphaned') {
    const confirmed = confirm(
      '🧹 清理删除残留数据\n\n' +
      '此操作将：\n' +
      '• 扫描所有照片记录，检查文件是否还存在\n' +
      '• 删除不存在文件的照片记录\n' +
      '• 删除相关的人脸识别数据\n' +
      '• 删除相关的标签关联\n' +
      '• 删除没有照片的空相册\n' +
      '• 删除文件夹路径不存在的相册\n\n' +
      '⚠️ 此操作不可恢复，请谨慎使用！\n' +
      '建议在删除大量文件或文件夹后执行此清理。\n\n' +
      '确定要继续吗？'
    )
    if (!confirmed) {
      return
    }
  }

  // 更新相册时间需要确认
  if (selectedApi.value === 'POST /admin/albums/update-times') {
    const confirmed = confirm(
      '📅 更新相册时间字段\n\n' +
      '此操作将：\n' +
      '• 重新计算所有相册的拍摄时间（最晚照片拍摄时间，聚合相册包含所有子相册的照片）\n' +
      '• 重新解析所有相册名称和上级路径中的时间信息（支持嵌套继承）\n' +
      '• 更新数据库中的时间字段\n\n' +
      '时间解析优先级：\n' +
      '• 当前相册名称的时间 > 上级路径中的时间\n' +
      '支持的时间格式：\n' +
      '• 2025.01.01\n' +
      '• 2025-01-01\n' +
      '• 2025.01.01 9:10\n\n' +
      '此操作是安全的，不会删除任何数据。\n\n' +
      '确定要继续吗？'
    )
    if (!confirmed) {
      return
    }
  }

  // 清空AI评分需要确认
  if (selectedApi.value === 'POST /admin/ai-scoring/clear-all') {
    const confirmed = confirm(
      '🗑️ 清空照片AI评分\n\n' +
      '此操作将：\n' +
      '• 删除所有照片的AI评分记录\n' +
      '• 清除技术质量评分、构图美学评分、主题吸引力评分\n' +
      '• 清除优点分析、不足分析和改进建议\n\n' +
      '⚠️ 此操作不可恢复！建议在重新评分之前执行此操作\n\n' +
      '确定要清空所有AI评分记录吗？'
    );
    if (!confirmed) return;
  }

  // AI评分更新需要确认
  if (selectedApi.value === 'POST /admin/ai-scoring/update-all') {
    const confirmed = confirm(
      '🤖 强制重新评分所有图片AI评分（覆盖现有评分）\n\n' +
      '此操作将：\n' +
      '• 为所有照片生成AI评分（技术质量、构图美学、主题吸引力）\n' +
      '• 分析每张照片的优点和不足\n' +
      '• 提供改进建议\n' +
      '• 生成详细的评分报告\n\n' +
      '⚠️ 异步执行：任务在后台运行，处理大量照片可能需要较长时间\n' +
      '⚠️ 如果没有ONNX Runtime，将使用基础评分算法\n\n' +
      '确定要继续吗？'
    )
    if (!confirmed) {
      testing.value = false
      return
    }
  }

  // 更新照片时间需要确认
  if (selectedApi.value === 'POST /admin/photos/update-times') {
    const isAsync = true
    const confirmed = confirm(
      '📸 更新照片时间信息\n\n' +
      '此操作将：\n' +
      '• 重新读取所有照片的EXIF信息，提取拍摄时间\n' +
      '• 如果EXIF中没有拍摄时间，从文件路径中解析时间\n' +
      '• 最后使用文件创建时间作为兜底时间\n' +
      '• 更新数据库中的拍摄时间字段\n\n' +
      '时间解析优先级：\n' +
      '• EXIF拍摄时间 (DateTimeOriginal)\n' +
      '• 文件路径时间（优先最深层文件夹，支持嵌套继承）\n' +
      '• 文件创建时间\n\n' +
      '支持的路径时间格式：\n' +
      '• 2025.01.01\n' +
      '• 2025-01-01\n' +
      '• 2025.01.01 10:30\n' +
      '• 20230808 (紧凑格式)\n' +
      '• 2023年08月08日\n\n' +
      (isAsync ? '⚠️ 异步执行：任务在后台运行，可通过日志查看进度\n\n' : '⚠️ 同步执行：需要等待所有照片处理完成\n\n') +
      '此操作是安全的，不会删除任何数据。\n\n' +
      '确定要继续吗？'
    )
    if (!confirmed) {
      return
    }
  }

  // 更新 EXIF 数值字段需要确认（可能较慢）
  if (selectedApi.value === 'POST /admin/update-exif-data') {
    const confirmed = confirm(
      '⚠️ 确认回填所有照片的 EXIF 字段？\n\n' +
      '此操作会遍历数据库中的所有照片并尝试解析/回填EXIF字段（快门秒数、焦距mm、光圈值、ISO、镜头型号），可能需要较长时间。\n\n' +
      '建议在低访问时段执行，确定要继续吗？'
    )
    if (!confirmed) return
  }

  // 更新颜色分类需要确认
  if (selectedApi.value === 'POST /admin/update-color-categories') {
    const confirmed = confirm(
      '🎨 确认更新所有照片的颜色分类？\n\n' +
      '此操作会为所有已有主色调的照片设置颜色分类（红色、蓝色、绿色等），便于按颜色分类筛选照片。\n\n' +
      '建议在低访问时段执行，确定要继续吗？'
    )
    if (!confirmed) return
  }

  // 更新照片颜色需要确认（更全面的重新计算）
  if (selectedApi.value === 'POST /admin/recalculate-photo-colors') {
    const confirmed = confirm(
      '🎨🖼️ 确认重新计算所有照片的颜色？\n\n' +
      '此操作将：\n' +
      '• 重新分析所有照片的主色调\n' +
      '• 重新生成照片的调色板\n' +
      '• 重新设置颜色分类\n' +
      '• 更新所有相册的氛围效果\n' +
      '• 更新筛选选项\n\n' +
      '这是一个非常耗时的操作，可能需要几分钟到几十分钟。\n\n' +
      '建议在低访问时段执行，确定要继续吗？'
    )
    if (!confirmed) return
  }

  // 清理重复人脸数据需要确认
  if (selectedApi.value === 'POST /admin/cleanup/duplicate-faces') {
    const confirmed = confirm(
      '🧹 清理重复人脸记录\n\n' +
      '此操作将：\n' +
      '• 扫描所有照片，查找同一张照片有多条人脸记录的情况\n' +
      '• 保留每张照片最新的人脸记录，删除重复的旧记录\n' +
      '• 清理相关的人物关联和聚类数据\n\n' +
      '此操作有助于解决人脸数量异常暴涨的问题。\n' +
      '建议在清理前备份数据库。\n\n' +
      '确定要继续吗？'
    )
    if (!confirmed) {
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
    
    // 如果后端返回 taskId，则开始轮询任务状态
    if (response.data && response.data.taskId) {
      pollTask(response.data.taskId)
    }
    
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
  stopTaskPoll()
})

const showPathInput = computed(() => selectedApi.value.includes('/admin/scan'))
const showFaceSimilarInputs = computed(() => selectedApi.value.includes('/admin/faces/{id}/similar'))
</script>

