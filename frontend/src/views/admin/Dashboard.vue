<template>
  <div class="min-h-screen admin-shell admin-dashboard-page">
    <AdminStyleChrome />
    <!-- 顶部导航 -->
    <nav class="glass-toolbar">
      <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div class="flex min-h-[84px] items-center justify-between gap-4 py-3 flex-wrap admin-dashboard-toolbar-shell">
          <div class="flex min-w-0 items-center gap-3 flex-wrap admin-dashboard-toolbar-brand">
            <div class="shrink-0 admin-dashboard-toolbar-title">
              <h1 class="text-xl font-light tracking-wide whitespace-nowrap text-[color:var(--pe-admin-text-primary)]">后台管理</h1>
            </div>
          </div>
          <div class="flex items-center gap-3 flex-wrap justify-end admin-dashboard-toolbar-actions">
            <button
              @click="themeStore.toggleTheme"
              class="admin-button-soft rounded-full p-2 transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-300/80"
              :title="themeStore.isDark ? '切换为浅色模式' : '切换为深色模式'"
            >
              <svg v-if="!themeStore.isDark" class="w-4 h-4 text-amber-200" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M20.354 15.354A9 9 0 018.646 3.646 9.003 9.003 0 0012 21a9.003 9.003 0 008.354-5.646z" />
              </svg>
              <svg v-else class="w-4 h-4 text-sky-200" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 3v1m0 16v1m9-9h-1M4 12H3m15.364 6.364l-.707-.707M6.343 6.343l-.707-.707m12.728 0l-.707.707M6.343 17.657l-.707.707M16 12a4 4 0 11-8 0 4 4 0 018 0z" />
              </svg>
            </button>
            <span class="text-sm text-[color:var(--pe-admin-text-secondary)]">欢迎，{{ authStore.username }}</span>
            <button
              @click="handleLogout"
              class="admin-button-contrast rounded-lg px-3 py-1.5 text-xs transition-colors"
            >
              退出登录
            </button>
            <router-link
              to="/"
              class="admin-button-soft rounded-lg px-3 py-1.5 text-xs transition-colors"
            >
              返回首页
            </router-link>
          </div>
        </div>
      </div>
    </nav>

    <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-3 space-y-3 admin-dashboard-shell">
      <!-- Hero 区域 -->
      <section class="admin-hero admin-dashboard-hero">
        <div class="admin-hero-gradient"></div>
        <div class="admin-hero-particle admin-hero-particle--small"></div>
        <div class="admin-hero-particle admin-hero-particle--medium"></div>
        <div class="admin-hero-particle admin-hero-particle--large"></div>
        <div class="admin-hero-content">
          <div class="space-y-4 admin-dashboard-hero-copy">
            <div>
              <h2 class="text-2xl sm:text-3xl lg:text-4xl font-light tracking-wide mb-2">
                {{ authStore.projectDisplayName || '光忆集' }}
              </h2>
            </div>
            <div class="grid grid-cols-2 sm:grid-cols-3 gap-4 pt-1 admin-dashboard-stat-grid">
        <router-link
          to="/admin/albums"
                class="admin-hero-stat admin-dashboard-stat-card transition-colors cursor-pointer"
                style="padding: 14px 16px;"
        >
                <div class="admin-hero-stat-label">相册</div>
                <div class="admin-hero-stat-value">{{ stats.albums }}</div>
        </router-link>
        <router-link
          to="/admin/photos"
                class="admin-hero-stat admin-dashboard-stat-card transition-colors cursor-pointer"
                style="padding: 14px 16px;"
        >
                <div class="admin-hero-stat-label">照片</div>
                <div class="admin-hero-stat-value">{{ stats.photos }}</div>
        </router-link>
        <router-link
          to="/admin/persons"
                class="admin-hero-stat admin-dashboard-stat-card transition-colors cursor-pointer"
                style="padding: 14px 16px;"
        >
                <div class="admin-hero-stat-label">人物</div>
                <div class="admin-hero-stat-value">{{ stats.persons }}</div>
        </router-link>
            </div>
          </div>
          <div class="admin-hero-secondary space-y-3 admin-dashboard-hero-side">
            <div class="admin-soft-surface rounded-2xl p-4">
              <div class="text-sm text-[color:var(--pe-admin-text-primary)]">我的扫描进度</div>
              <div class="mt-2 space-y-1 text-xs text-[color:var(--pe-admin-text-secondary)]">
                <p>进度：
                  <span
                    class="text-sky-300 cursor-pointer hover:underline hover:text-sky-200 transition-colors"
                    @click="openSkippedFilesModal"
                    title="点击查看你的异常文件"
                  >{{ scanProgressText }}</span>
                </p>
                <p>时间：<span class="text-[color:var(--pe-admin-text-secondary)]">{{ lastScanTime || '—' }}</span></p>
                <p v-if="currentUserQueueSummary?.hasRunningTask">我的队列：<span class="text-emerald-300">正在扫描中</span></p>
                <p v-else-if="(currentUserQueueSummary?.queuedTaskCount || 0) > 0">
                  我的等待：前面还有 <span class="text-amber-300">{{ currentUserQueueSummary?.aheadImageCount || 0 }}</span> 张图片
                </p>
                <p v-if="(currentUserQueueSummary?.queuedTaskCount || 0) > 0">
                  我的待扫：<span class="text-sky-300">{{ currentUserQueueSummary?.pendingImageCount || 0 }}</span> 张图片
                </p>
              </div>
            </div>
          </div>
        </div>
      </section>

      <router-link
        to="/admin/file-browser"
        class="glass-panel block p-5 md:p-6 admin-card-animate admin-card-4 admin-dashboard-feature-card admin-dashboard-feature-card--browser"
      >
        <div class="admin-dashboard-feature-shell">
          <div class="admin-dashboard-feature-copy">
            <div class="admin-dashboard-feature-kicker">核心入口</div>
            <div class="admin-dashboard-feature-title">文件管理</div>
          </div>
          <div class="admin-dashboard-feature-preview" aria-hidden="true">
            <div class="admin-dashboard-feature-preview-window">
              <div class="admin-dashboard-feature-preview-bar">
                <span></span>
                <span></span>
                <span></span>
              </div>
              <div class="admin-dashboard-feature-preview-grid">
                <div class="admin-dashboard-feature-preview-sidebar"></div>
                <div class="admin-dashboard-feature-preview-content">
                  <div></div>
                  <div></div>
                  <div></div>
                  <div></div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </router-link>

      <div class="glass-panel p-5 md:p-6 space-y-5 admin-card-animate admin-card-4 admin-dashboard-data-panel">
        <div class="flex items-center justify-between gap-4 flex-wrap admin-dashboard-panel-head">
          <div class="admin-dashboard-panel-copy">
            <h2 class="text-lg font-light">数据管理</h2>
          </div>
          <div class="flex flex-wrap gap-2 text-xs admin-dashboard-chip-row">
            <span class="chip">照片 {{ stats.photos }}</span>
            <span class="chip">相册 {{ stats.albums }}</span>
            <span class="chip">人物 {{ stats.persons }}</span>
            <span class="chip">标签 {{ stats.tags }}</span>
          </div>
        </div>

        <div class="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-3 gap-3 admin-dashboard-entry-grid">
          <router-link
            to="/admin/tags"
            class="admin-entry-card admin-dashboard-entry-item rounded-2xl px-4 py-4 text-sm"
          >
            <div class="admin-dashboard-entry-kicker">内容</div>
            <div class="admin-dashboard-entry-title">标签管理</div>
            <div class="admin-dashboard-entry-meta">{{ stats.tags }} 个标签</div>
          </router-link>
          <router-link
            to="/admin/theme"
            class="admin-entry-card admin-dashboard-entry-item rounded-2xl px-4 py-4 text-sm"
          >
            <div class="admin-dashboard-entry-kicker">外观</div>
            <div class="admin-dashboard-entry-title">主题与风格</div>
          </router-link>
          <router-link
            to="/admin/settings"
            class="admin-entry-card admin-dashboard-entry-item rounded-2xl px-4 py-4 text-sm"
          >
            <div class="admin-dashboard-entry-kicker">系统</div>
            <div class="admin-dashboard-entry-title">系统设置</div>
          </router-link>
          </div>
      </div>

      <div class="glass-panel p-5 md:p-6 space-y-5 admin-card-animate admin-card-4 admin-dashboard-clean-panel">
        <div class="admin-dashboard-panel-head admin-dashboard-panel-copy">
          <h2 class="text-lg font-light">清理</h2>
        </div>
        <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
          <button
            @click="cleanupFailedFiles"
            :disabled="isCleaningFailedFiles"
            class="admin-entry-card admin-dashboard-entry-item rounded-2xl px-4 py-4 text-left text-sm disabled:cursor-not-allowed disabled:opacity-50"
          >
            <div class="admin-dashboard-entry-kicker">维护</div>
            <div class="admin-dashboard-entry-title">{{ isCleaningFailedFiles ? '清理中...' : '清理失败文件' }}</div>
          </button>
          <button
            @click="cleanupOrphaned"
            :disabled="isCleaningUp"
            class="admin-entry-card admin-dashboard-entry-item rounded-2xl px-4 py-4 text-left text-sm disabled:cursor-not-allowed disabled:opacity-50"
          >
            <div class="admin-dashboard-entry-kicker">维护</div>
            <div class="admin-dashboard-entry-title">{{ isCleaningUp ? '清理中...' : '清理删除残留' }}</div>
          </button>
        </div>
      </div>
    </div>

    <router-link
      to="/admin/file-browser"
      class="admin-floating-action"
      title="打开文件管理"
      aria-label="打开文件管理"
    >
      <span class="admin-floating-action-icon">+</span>
      <span class="admin-floating-action-label">文件管理</span>
    </router-link>

    <!-- 跳过文件详情弹窗 -->
    <div
      v-if="showSkippedModal"
      class="admin-dashboard-modal fixed inset-0 z-50 flex items-center justify-center p-4"
      @click.self="closeSkippedModal"
    >
      <div class="admin-modal-backdrop absolute inset-0"></div>
      <div class="admin-modal-card admin-dashboard-modal-card relative w-full max-w-4xl max-h-[80vh] flex flex-col overflow-hidden">
        <!-- 弹窗头部 -->
        <div class="admin-dashboard-modal-head flex items-center justify-between px-5 py-4 shrink-0">
          <div>
            <h3 class="text-base font-medium">扫描异常文件详情</h3>
            <p class="text-xs mt-0.5 admin-table-muted">
              <template v-if="scanning">
                <span class="text-cyan-400 animate-pulse">扫描进行中，数据实时更新…</span>
              </template>
              <template v-else-if="authStore.isSuperAdmin">
                共 {{ scanTotal }} 个文件，正常 {{ scanTotal - skippedFiles.length }} 个，异常 {{ skippedFiles.length }} 个
                <template v-if="skippedFiles.length > 0">
                  （重复 {{ skippedFiles.filter(f => f.reason === '内容重复').length }}，
                  空文件 {{ skippedFiles.filter(f => f.reason === '文件为空').length }}，
                  其他 {{ skippedFiles.filter(f => f.reason !== '内容重复' && f.reason !== '文件为空').length }}）
                </template>
              </template>
              <template v-else>
                当前仅展示你的图片异常记录，共 {{ skippedFiles.length }} 条
              </template>
            </p>
          </div>
          <button @click="closeSkippedModal" class="admin-dashboard-modal-close p-1.5 rounded-lg transition-colors">
            <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"/>
            </svg>
          </button>
        </div>
        <!-- 表格内容 -->
        <div class="overflow-auto flex-1">
          <div v-if="loadingSkipped" class="flex items-center justify-center py-16 admin-table-muted text-sm">
            加载中…
          </div>
          <div v-else-if="skippedFiles.length === 0" class="flex items-center justify-center py-16 admin-table-muted text-sm">
            无跳过文件，进度数据完全一致
          </div>
          <table v-else class="w-full text-xs admin-data-table border-collapse">
            <thead class="sticky top-0 uppercase tracking-wide">
              <tr>
                <th class="px-4 py-2.5 text-left w-12">#</th>
                <th class="px-4 py-2.5 text-left">相对路径</th>
                <th v-if="authStore.isSuperAdmin" class="px-4 py-2.5 text-left w-24">用户</th>
                <th class="px-4 py-2.5 text-left w-28">原因</th>
                <th class="px-4 py-2.5 text-right w-24">文件大小</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="f in skippedFiles"
                :key="f.index"
                class="admin-dashboard-modal-row transition-colors"
              >
                <td class="px-4 py-2 admin-table-faint">{{ f.index }}</td>
                <td class="px-4 py-2 font-mono admin-table-muted break-all">{{ f.relativePath }}</td>
                <td v-if="authStore.isSuperAdmin" class="px-4 py-2 admin-table-muted">{{ f.userId != null ? `用户#${f.userId}` : '—' }}</td>
                <td class="px-4 py-2">
                  <span
                    class="cursor-help border-b border-dashed admin-dashboard-skip-reason"
                    :class="{
                      'admin-dashboard-skip-reason--duplicate': f.reason === '内容重复',
                      'admin-dashboard-skip-reason--empty': f.reason === '文件为空',
                      'admin-dashboard-skip-reason--other': f.reason !== '内容重复' && f.reason !== '文件为空'
                    }"
                    :title="f.detail"
                  >{{ f.reason }}</span>
                </td>
                <td class="px-4 py-2 text-right admin-table-muted tabular-nums">{{ formatFileSize(f.fileSizeBytes) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>

    <div
      v-if="authStore.isSuperAdmin && showTaskDetailModal"
      class="admin-dashboard-modal fixed inset-0 z-50 flex items-center justify-center p-4"
      @click.self="closeTaskDetailModal"
    >
      <div class="admin-modal-backdrop absolute inset-0"></div>
      <div class="admin-modal-card admin-dashboard-modal-card relative w-full max-w-5xl max-h-[85vh] flex flex-col overflow-hidden">
        <div class="admin-dashboard-modal-head flex items-center justify-between px-5 py-4 shrink-0">
          <div>
            <h3 class="text-base font-medium">
              扫描任务详情
              <span v-if="selectedTaskDetail" class="text-sky-300 ml-2">#{{ selectedTaskDetail.id }}</span>
            </h3>
            <p class="text-xs admin-table-muted mt-0.5">
              单任务视角查看恢复游标、检查点与实时状态。
            </p>
          </div>
          <div class="flex items-center gap-2">
            <button
              @click="refreshSelectedTaskDetail"
              :disabled="loadingTaskDetail || !selectedTaskDetail"
              class="admin-button-soft px-3 py-1.5 text-xs rounded-lg transition-colors disabled:opacity-60"
            >
              {{ loadingTaskDetail ? '刷新中…' : '刷新详情' }}
            </button>
            <button @click="closeTaskDetailModal" class="admin-dashboard-modal-close p-1.5 rounded-lg transition-colors">
              <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"/>
              </svg>
            </button>
          </div>
        </div>

        <div class="overflow-auto flex-1 p-5" v-if="selectedTaskDetail">
          <div class="grid grid-cols-1 xl:grid-cols-3 gap-4 text-sm">
            <div class="admin-dashboard-detail-card rounded-xl p-4 space-y-2">
              <div class="admin-table-faint text-xs">基础信息</div>
              <div>{{ taskTypeLabel(selectedTaskDetail.taskType) }} · {{ taskStatusLabel(selectedTaskDetail.status) }}</div>
              <div class="admin-table-muted break-all">根路径：{{ selectedTaskDetail.rootPathDisplay || selectedTaskDetail.rootPath || '—' }}</div>
              <div class="admin-table-muted">优先级：{{ selectedTaskDetail.priority ?? '—' }}</div>
              <div class="admin-table-muted">归属：{{ selectedTaskDetail.ownerLabel || '系统任务' }}</div>
              <div class="admin-table-muted">用户 ID：{{ selectedTaskDetail.userId ?? '全局' }}</div>
              <div class="admin-table-muted">请求者：{{ selectedTaskDetail.requestedByUserNickname || selectedTaskDetail.requestedByUsername || selectedTaskDetail.requestedByUserId || '系统' }}</div>
              <div class="admin-table-muted">存储：{{ selectedTaskDetail.storageProviderName || selectedTaskDetail.storageProviderId || '默认' }}<span v-if="selectedTaskDetail.storageProviderType"> · {{ storageTypeLabel(selectedTaskDetail.storageProviderType) }}</span></div>
            </div>

            <div class="admin-dashboard-detail-card rounded-xl p-4 space-y-2">
              <div class="admin-table-faint text-xs">恢复状态</div>
              <div class="admin-table-muted break-all">
                恢复游标：{{ selectedTaskDetail.resumeFromPathDisplay || selectedTaskDetail.resumeFromPath || '—' }}
                <span v-if="selectedTaskDetail.resumeFromType" class="ml-2 text-[11px] px-2 py-0.5 rounded-full border border-sky-500/30 bg-sky-500/10 text-sky-300">
                  {{ pathTypeLabel(selectedTaskDetail.resumeFromType) }}
                </span>
              </div>
              <div class="admin-table-muted break-all">
                最近断点：{{ selectedTaskDetail.lastProcessedPathDisplay || selectedTaskDetail.lastProcessedPath || '—' }}
                <span v-if="selectedTaskDetail.lastProcessedType" class="ml-2 text-[11px] px-2 py-0.5 rounded-full border border-purple-500/30 bg-purple-500/10 text-purple-300">
                  {{ pathTypeLabel(selectedTaskDetail.lastProcessedType) }}
                </span>
              </div>
              <div class="admin-table-muted break-all">检查点根路径：{{ selectedTaskDetail.checkpoint?.rootPathDisplay || selectedTaskDetail.checkpoint?.rootPath || '—' }}</div>
              <div class="admin-table-muted">检查点更新时间：{{ formatDateTime(selectedTaskDetail.checkpointUpdatedAt || selectedTaskDetail.checkpoint?.updatedAt) }}</div>
              <div v-if="selectedTaskDetail.errorMessage" class="text-rose-300 break-all">
                错误：{{ selectedTaskDetail.errorMessage }}
              </div>
            </div>

            <div class="admin-dashboard-detail-card rounded-xl p-4 space-y-2">
              <div class="admin-table-faint text-xs">进度统计</div>
              <div class="text-lg">{{ selectedTaskDetail.progressPercent || 0 }}%</div>
              <div class="admin-table-muted">已处理：{{ selectedTaskDetail.processedItems || 0 }} / {{ selectedTaskDetail.totalItems || 0 }}</div>
              <div class="admin-table-muted">跳过：{{ selectedTaskDetail.skippedItems || 0 }}</div>
              <div class="admin-table-muted">失败：{{ selectedTaskDetail.failedItems || 0 }}</div>
              <div class="admin-table-faint text-xs">创建：{{ formatDateTime(selectedTaskDetail.createdAt) }}</div>
              <div class="admin-table-faint text-xs">开始：{{ formatDateTime(selectedTaskDetail.startedAt) }}</div>
              <div class="admin-table-faint text-xs">完成：{{ formatDateTime(selectedTaskDetail.finishedAt) }}</div>
            </div>
          </div>

          <div class="admin-dashboard-detail-card mt-4 rounded-xl p-4">
            <div class="admin-table-faint text-xs mb-3">检查点快照</div>
            <pre class="text-xs whitespace-pre-wrap break-words">{{ JSON.stringify(selectedTaskDetail.checkpoint || {}, null, 2) }}</pre>
          </div>
        </div>

        <div v-else class="flex-1 flex items-center justify-center admin-table-muted text-sm">
          {{ loadingTaskDetail ? '加载任务详情…' : '暂无任务详情' }}
        </div>
      </div>
    </div>


  </div>
</template>

<script setup lang="ts">
import AdminStyleChrome from '@/components/admin/AdminStyleChrome.vue'
import { ref, onMounted, onUnmounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useThemeStore } from '@/stores/theme'
import { api } from '@/api'
import { storageTypeLabel } from '@/utils/providerLabels'

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
const scanProviderOptions = ref<Array<{
  id: number
  name: string
  type: string
  scanSupported?: boolean
  supportMessage?: string | null
}>>([])
const selectedScanProviderId = ref<number | null>(null)
const queueCount = ref(0)
const queuedOwnerCount = ref(0)
const queuedOwnerSummaries = ref<any[]>([])
const currentUserQueueSummary = ref<any | null>(null)
const pausedTaskCount = ref(0)
const runningTaskCount = ref(0)
const queuedImageCount = ref(0)
const runningImageCount = ref(0)
const runningTasks = ref<any[]>([])
const currentScanTask = ref<any | null>(null)
const scanTasks = ref<any[]>([])
const expandedTaskIds = ref<number[]>([])
const showTaskDetailModal = ref(false)
const selectedTaskDetail = ref<any | null>(null)
const loadingTaskDetail = ref(false)
const loadingScanTasks = ref(false)
const loginRecords = ref<any[]>([])
const loadingLoginRecords = ref(false)
const operationLogs = ref<any[]>([])
const loadingOperationLogs = ref(false)
const isCleaningUp = ref(false)
const isCleaningFailedFiles = ref(false)
const lastScanTime = ref<string | null>(null)
const scanProgress = ref<{ current: number; total: number }>({ current: 0, total: 0 })
const scanStatus = computed(() => {
  if (currentScanTask.value) return '扫描中'
  if (queueCount.value > 0) return '排队中'
  if (pausedTaskCount.value > 0) return '已暂停'
  return scanning.value ? '扫描中' : '空闲'
})
const scanProgressText = computed(() => {
  const { current, total } = scanProgress.value
  if (!total) return '0 / 0'
  const percentage = total > 0 ? Math.min(100, Math.floor((current / total) * 100)) : 0
  return `${current} / ${total} (${percentage}%)`
})
const queuedOwnerSummaryText = computed(() =>
  queuedOwnerSummaries.value
    .slice(0, 3)
    .map((item: any) => `${item.ownerLabel || '未知'} ${item.taskCount}`)
    .join('，')
)
const runningTaskSummaryText = computed(() =>
  runningTasks.value
    .slice(0, 3)
    .map((item: any) => `${item.requestedByUserNickname || item.requestedByUsername || item.ownerLabel || `任务 ${item.id}`} · ${item.taskType}`)
    .join('，')
)
const selectedScanProvider = computed(() => {
  if (selectedScanProviderId.value == null) return null
  return scanProviderOptions.value.find((provider) => provider.id === selectedScanProviderId.value) ?? null
})
const scanActionSupported = computed(() => selectedScanProvider.value?.scanSupported !== false)
const scanDisabledReason = computed(() => {
  if (!authStore.isSuperAdmin) return ''
  if (selectedScanProvider.value?.scanSupported === false) {
    return selectedScanProvider.value.supportMessage || '当前存储暂不支持扫描，请切换到可扫描的存储配置。'
  }
  return ''
})

const scanCurrentVal = computed(() => scanProgress.value.current)
const scanTotal = computed(() => scanProgress.value.total)
const scanSummary = ref({
  total: 0,
  scanned: 0,
  failed: 0,
  waiting: 0
})

// 跳过文件弹窗
const showSkippedModal = ref(false)
const loadingSkipped = ref(false)
const skippedFiles = ref<Array<{
  index: number
  userId?: number | null
  relativePath: string
  reason: string
  detail: string
  fileSizeBytes: number
}>>([])

const formatFileSize = (bytes: number): string => {
  if (bytes === 0) return '0 B'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(2)} MB`
}

const formatDateTime = (value?: string | null): string => {
  if (!value) return '—'
  return new Date(value).toLocaleString('zh-CN')
}

const taskTypeLabel = (taskType?: string): string => {
  switch (taskType) {
    case 'FULL_SCAN':
      return '强制扫描'
    case 'UPLOAD_SCAN':
      return '上传扫描'
    case 'RESUME_SCAN':
      return '续扫任务'
    default:
      return '增量扫描'
  }
}

const taskStatusLabel = (status?: string): string => {
  switch (status) {
    case 'RUNNING':
      return '执行中'
    case 'QUEUED':
      return '排队中'
    case 'PENDING':
      return '待处理'
    case 'PAUSED':
      return '已暂停'
    case 'FAILED':
      return '失败'
    case 'COMPLETED':
      return '已完成'
    case 'CANCELED':
      return '已取消'
    default:
      return status || '未知'
  }
}

const pathTypeLabel = (pathType?: string): string => {
  switch (pathType) {
    case 'DIRECTORY':
      return '目录'
    case 'FILE':
      return '文件'
    default:
      return pathType || '未知'
  }
}

const taskStatusClass = (status?: string): string => {
  if (status === 'RUNNING') return 'text-sky-300 border-sky-500/40 bg-sky-500/10'
  if (status === 'QUEUED' || status === 'PENDING') return 'text-amber-300 border-amber-500/40 bg-amber-500/10'
  if (status === 'PAUSED') return 'text-purple-300 border-purple-500/40 bg-purple-500/10'
  if (status === 'FAILED') return 'text-rose-300 border-rose-500/40 bg-rose-500/10'
  if (status === 'COMPLETED') return 'text-emerald-300 border-emerald-500/40 bg-emerald-500/10'
  if (status === 'CANCELED') return 'text-zinc-300 border-zinc-500/40 bg-zinc-500/10'
  return 'text-zinc-300 border-zinc-500/40 bg-zinc-500/10'
}

const operationTypeLabel = (operationType?: string): string => {
  switch (operationType) {
    case 'UPLOAD':
      return '上传文件'
    case 'DELETE':
      return '删除内容'
    case 'UPDATE':
      return '修改内容'
    case 'SCAN_START':
      return '发起扫描'
    case 'SCAN_RESUME':
      return '恢复扫描'
    case 'SCAN_FINISH':
      return '扫描完成'
    case 'CONFIG_UPDATE':
      return '更新配置'
    default:
      return operationType || '操作'
  }
}

const loginMethodLabel = (method?: string): string => {
  switch (method) {
    case 'PHONE_PASSWORD':
      return '手机号+密码'
    case 'SMS_CODE':
      return '短信验证码'
    default:
      return '账号+密码'
  }
}

const canRetryTask = (task: any): boolean =>
  ['FAILED', 'PAUSED', 'CANCELED'].includes(task.status)

const canPauseTask = (task: any): boolean =>
  task.status === 'RUNNING'

const canCancelTask = (task: any): boolean =>
  ['RUNNING', 'QUEUED', 'PENDING'].includes(task.status)

const isTaskExpanded = (taskId: number): boolean =>
  expandedTaskIds.value.includes(taskId)

const toggleTaskExpanded = (taskId: number) => {
  if (isTaskExpanded(taskId)) {
    expandedTaskIds.value = expandedTaskIds.value.filter(id => id !== taskId)
    return
  }
  expandedTaskIds.value = [...expandedTaskIds.value, taskId]
}

let skippedPollTimer: number | null = null
let taskDetailPollTimer: number | null = null

const fetchSkippedFiles = async () => {
  try {
    const res = await api.get('/admin/scan/skipped-files')
    skippedFiles.value = res.data || []
  } catch (e) {
    skippedFiles.value = []
  }
}

const fetchScanTasks = async () => {
  if (!authStore.isSuperAdmin) {
    scanTasks.value = []
    return
  }
  loadingScanTasks.value = true
  try {
    const res = await api.get('/admin/scan/tasks')
    scanTasks.value = res.data || []
    expandedTaskIds.value = expandedTaskIds.value.filter(taskId =>
      scanTasks.value.some(task => task.id === taskId)
    )
  } catch (error) {
    console.error('加载扫描任务失败:', error)
  } finally {
    loadingScanTasks.value = false
  }
}

const shouldPollTaskDetail = (task: any | null): boolean =>
  !!task && ['RUNNING', 'QUEUED', 'PENDING', 'PAUSED'].includes(task.status)

const stopTaskDetailPoll = () => {
  if (taskDetailPollTimer) {
    clearInterval(taskDetailPollTimer)
    taskDetailPollTimer = null
  }
}

const fetchTaskDetail = async (taskId: number) => {
  loadingTaskDetail.value = true
  try {
    const res = await api.get(`/admin/scan/tasks/${taskId}`)
    selectedTaskDetail.value = res.data || null
    if (!shouldPollTaskDetail(selectedTaskDetail.value)) {
      stopTaskDetailPoll()
    }
  } catch (error) {
    console.error('加载任务详情失败:', error)
  } finally {
    loadingTaskDetail.value = false
  }
}

const refreshSelectedTaskDetail = async () => {
  if (!selectedTaskDetail.value?.id) return
  await fetchTaskDetail(selectedTaskDetail.value.id)
}

const openTaskDetail = async (taskId: number) => {
  if (!authStore.isSuperAdmin) return
  showTaskDetailModal.value = true
  await fetchTaskDetail(taskId)
  stopTaskDetailPoll()
  if (shouldPollTaskDetail(selectedTaskDetail.value)) {
    taskDetailPollTimer = window.setInterval(() => {
      if (selectedTaskDetail.value?.id) {
        fetchTaskDetail(selectedTaskDetail.value.id)
      }
    }, 3000)
  }
}

const closeTaskDetailModal = () => {
  showTaskDetailModal.value = false
  selectedTaskDetail.value = null
  stopTaskDetailPoll()
}

const fetchOperationLogs = async () => {
  if (!authStore.isSuperAdmin) {
    operationLogs.value = []
    return
  }
  loadingOperationLogs.value = true
  try {
    const res = await api.get('/admin/operation-logs')
    operationLogs.value = res.data || []
  } catch (error) {
    console.error('加载操作日志失败:', error)
  } finally {
    loadingOperationLogs.value = false
  }
}

const fetchLoginRecords = async () => {
  if (!authStore.isSuperAdmin) {
    loginRecords.value = []
    return
  }
  loadingLoginRecords.value = true
  try {
    const res = await api.get('/admin/login-records')
    loginRecords.value = res.data || []
  } catch (error) {
    console.error('加载登录记录失败:', error)
  } finally {
    loadingLoginRecords.value = false
  }
}

const retryScanTask = async (task: any) => {
  if (!confirm(`确认重新入队扫描任务 #${task.id} 吗？`)) return
  try {
    await api.post(`/admin/scan/tasks/${task.id}/retry`)
    await Promise.all([fetchScanStatus(), fetchScanTasks()])
  } catch (error: any) {
    alert('重新入队失败: ' + (error.response?.data?.error || error.message))
  }
}

const pauseScanTask = async (task: any) => {
  if (!confirm(`确认暂停扫描任务 #${task.id} 吗？`)) return
  try {
    const res = await api.post(`/admin/scan/tasks/${task.id}/pause`)
    if (res.data?.message) {
      alert(res.data.message)
    }
    await Promise.all([fetchScanStatus(), fetchScanTasks()])
  } catch (error: any) {
    alert('暂停失败: ' + (error.response?.data?.error || error.message))
  }
}

const cancelScanTask = async (task: any) => {
  if (!confirm(`确认取消扫描任务 #${task.id} 吗？`)) return
  try {
    const res = await api.post(`/admin/scan/tasks/${task.id}/cancel`)
    if (res.data?.message) {
      alert(res.data.message)
    }
    await Promise.all([fetchScanStatus(), fetchScanTasks()])
  } catch (error: any) {
    alert('取消失败: ' + (error.response?.data?.error || error.message))
  }
}

const openSkippedFilesModal = async () => {
  showSkippedModal.value = true
  loadingSkipped.value = true
  await fetchSkippedFiles()
  loadingSkipped.value = false

  // 如果正在扫描，自动轮询刷新异常列表直到扫描结束
  if (skippedPollTimer) clearInterval(skippedPollTimer)
  if (scanning.value) {
    skippedPollTimer = window.setInterval(async () => {
      await fetchSkippedFiles()
      if (!scanning.value) {
        // 扫描结束后再刷新一次，然后停止轮询
        await fetchSkippedFiles()
        if (skippedPollTimer) { clearInterval(skippedPollTimer); skippedPollTimer = null }
      }
    }, 2000)
  }
}

const closeSkippedModal = () => {
  showSkippedModal.value = false
  if (skippedPollTimer) { clearInterval(skippedPollTimer); skippedPollTimer = null }
}
const selectedApi = ref('')
const testing = ref(false)
const apiResponse = ref<any>(null)
const pathInput = ref('')
const faceIdInput = ref('')
const topInput = ref('')
const thresholdInput = ref('')
const albumIdInput = ref('')

const showAlbumIdInput = computed(() =>
  selectedApi.value.includes('/admin/background-removal')
)

const taskStatus = ref<any | null>(null)
let taskPollTimer: number | null = null

const stopTaskPoll = async () => {
  if (taskPollTimer) {
    clearInterval(taskPollTimer)
    taskPollTimer = null
  }
  if (taskStatus.value?.taskId) {
    try {
      await api.post(`/admin/tasks/${taskStatus.value.taskId}/stop`)
    } catch (e) {
      // ignore
    }
  }
  taskStatus.value = null
}

const pollTask = async (taskId: string) => {
  await stopTaskPoll()
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
          await stopTaskPoll()
        }
      } else {
        await stopTaskPoll()
      }
    } catch (e) {
      // ignore
    }
  }, 2000)
}

const loadStats = async () => {
  const results = await Promise.allSettled([
    api.get('/albums/count', { params: { includeHidden: true } }),
    api.get('/photos', { params: { size: 1, page: 0 } }),
    api.get('/tags', { params: { size: 1, page: 0 } }),
    api.get('/admin/persons/count'),
    api.get('/admin/faces', { params: { size: 1, page: 0 } })
  ])

  const [albumsRes, photosRes, tagsRes, personsRes, facesRes] = results

  const albumTotal = albumsRes.status === 'fulfilled'
    ? (albumsRes.value.data ?? 0)
    : stats.value.albums
  const photoTotal = photosRes.status === 'fulfilled'
    ? (photosRes.value.data.totalElements ?? photosRes.value.data.total ?? 0)
    : stats.value.photos
  const tagTotal = tagsRes.status === 'fulfilled'
    ? (Array.isArray(tagsRes.value.data)
        ? tagsRes.value.data.length
        : (tagsRes.value.data.totalElements ?? tagsRes.value.data.total ?? 0))
    : stats.value.tags
  const personTotal = personsRes.status === 'fulfilled'
    ? (personsRes.value.data ?? 0)
    : stats.value.persons
  const faceTotal = facesRes.status === 'fulfilled'
    ? (facesRes.value.data.totalElements ?? facesRes.value.data.total ?? 0)
    : stats.value.faces

  stats.value = {
    albums: albumTotal,
    photos: photoTotal,
    tags: tagTotal,
    persons: personTotal,
    faces: faceTotal
  }

  results.forEach((result, index) => {
    if (result.status === 'rejected') {
      const labels = ['albums', 'photos', 'tags', 'persons', 'faces']
      console.error(`加载 ${labels[index]} 统计失败:`, result.reason)
    }
  })
}

const cleanupOrphaned = async () => {
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
  if (!confirmed) return

  isCleaningUp.value = true
  try {
    const response = await api.post('/admin/cleanup/orphaned')
    await loadStats()
    alert(response.data.message || '清理完成！')
  } catch (error: any) {
    alert('清理失败: ' + (error.response?.data?.error || error.message))
  } finally {
    isCleaningUp.value = false
  }
}

const cleanupFailedFiles = async () => {
  const confirmed = confirm(
    '清理失败文件记录\n\n' +
    '此操作会清空当前账号可见的扫描失败/异常文件记录。\n' +
    '它不会删除真实文件，只会清理失败记录列表。\n\n' +
    '确定继续吗？'
  )
  if (!confirmed) return

  isCleaningFailedFiles.value = true
  try {
    const response = await api.post('/admin/scan/skipped-files/cleanup')
    await fetchSkippedFiles()
    alert(response.data.message || '失败文件记录已清理')
  } catch (error: any) {
    alert('清理失败文件记录失败: ' + (error.response?.data?.error || error.message))
  } finally {
    isCleaningFailedFiles.value = false
  }
}

const triggerScan = async () => {
  if (!authStore.isSuperAdmin) {
    alert('普通用户不能主动发起扫描，请等待系统按队列自动处理。')
    return
  }
  if (!scanActionSupported.value) {
    alert(scanDisabledReason.value || '当前存储暂不支持扫描')
    return
  }
  scanning.value = true
  lastScanTime.value = new Date().toLocaleString('zh-CN')
  try {
    const params = authStore.isSuperAdmin && selectedScanProviderId.value != null
      ? { storageProviderId: selectedScanProviderId.value }
      : undefined
    await api.post('/admin/scan', null, { params })
  } catch (error: any) {
    alert('触发扫描失败: ' + (error.response?.data?.message || error.message))
  } finally {
    scanning.value = false
  }
}

const loadScanProviderOptions = async () => {
  if (!authStore.isSuperAdmin) {
    scanProviderOptions.value = []
    selectedScanProviderId.value = null
    return
  }
  try {
    const { data } = await api.get('/admin/folders/base-path')
    const providers = Array.isArray(data?.availableStorageProviders) ? data.availableStorageProviders : []
    scanProviderOptions.value = providers
      .filter((item: any) => item?.browserSupported)
      .map((item: any) => ({
        id: item.id,
        name: item.name,
        type: item.type,
        scanSupported: item.scanSupported !== false,
        supportMessage: item.supportMessage || null
      }))
    selectedScanProviderId.value = data?.storageProviderId ?? null
    if (selectedScanProviderId.value != null) {
      const exists = scanProviderOptions.value.some((provider) => provider.id === selectedScanProviderId.value)
      if (!exists) {
        selectedScanProviderId.value = null
      }
    }
  } catch (error) {
    console.warn('加载扫描存储提供者失败', error)
  }
}

const fetchScanStatus = async () => {
  try {
    const res = await api.get('/admin/scan/status')
    const data = res.data || {}
    scanning.value = !!data.scanning
    queueCount.value = data.queuedTaskCount ?? 0
    queuedOwnerCount.value = data.queuedOwnerCount ?? 0
    queuedOwnerSummaries.value = Array.isArray(data.queuedOwnerSummaries) ? data.queuedOwnerSummaries : []
    currentUserQueueSummary.value = data.currentUserQueue ?? null
    pausedTaskCount.value = data.pausedTaskCount ?? 0
    runningTaskCount.value = data.runningTaskCount ?? 0
    queuedImageCount.value = data.queuedImageCount ?? 0
    runningImageCount.value = data.runningImageCount ?? 0
    runningTasks.value = Array.isArray(data.runningTasks) ? data.runningTasks : []
    currentScanTask.value = data.currentTask ?? null
    scanProgress.value = {
      current: data.current ?? 0,
      total: data.total ?? 0
    }
    scanSummary.value = {
      total: data.scanSummary?.total ?? data.filesystemStats?.total ?? 0,
      scanned: data.scanSummary?.scanned ?? data.filesystemStats?.scanned ?? 0,
      failed: data.scanSummary?.failed ?? data.processingStats?.failed ?? 0,
      waiting: data.scanSummary?.waiting ?? data.filesystemStats?.unscanned ?? 0
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

  // 重建所有人脸需要确认
  if (selectedApi.value === 'POST /admin/faces/rebuild-all') {
    const confirmed = confirm(
      '👤 重建所有人脸数据\n\n' +
      '此操作将：\n' +
      '• 对所有照片重新执行人脸检测\n' +
      '• 默认保留可匹配到的已有人物绑定与确认状态\n' +
      '• 对新算法不再认为是人脸的旧记录进行移除\n' +
      '• 使用新的人脸前处理与特征提取重新生成 embedding\n\n' +
      '⚠️ 该任务会在后台异步运行，图片多时可能耗时较长。\n' +
      '⚠️ 这是“只重建人脸”的操作，不会重建缩略图、标签或EXIF。\n\n' +
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

  // 清空AI分析需要确认
  if (selectedApi.value === 'POST /admin/ai-analysis/clear-all') {
    const confirmed = confirm(
      '🗑️ 清空照片AI分析\n\n' +
      '此操作将：\n' +
      '• 删除所有照片的AI分析记录\n' +
      '• 清除技术质量评分、构图美学评分、主题吸引力评分\n' +
      '• 清除优点分析、不足分析和改进建议\n' +
      '• 清除场景识别和情感分析结果\n\n' +
      '⚠️ 此操作不可恢复！建议在重新分析之前执行此操作\n\n' +
      '确定要清空所有AI分析记录吗？'
    );
    if (!confirmed) return;
  }

  // AI分析更新需要确认
  if (selectedApi.value === 'POST /admin/ai-analysis/update-all') {
    const confirmed = confirm(
      '🤖 更新所有照片AI分析\n\n' +
      '此操作将：\n' +
      '• 强制重新为所有照片生成AI分析（覆盖现有分析）\n' +
      '• 重新分析每张照片的技术质量、构图美学、主题吸引力\n' +
      '• 重新识别场景类型（婚礼、毕业典礼、旅行等）\n' +
      '• 重新分析情感色彩（快乐、悲伤、温暖等）\n' +
      '• 更新优点和不足分析，重新生成改进建议\n\n' +
      '⚠️ 此操作会覆盖所有现有的AI分析结果\n' +
      '⚠️ 异步执行：任务在后台运行，处理大量照片可能需要较长时间\n' +
      '⚠️ 如果没有ONNX Runtime，将使用基础分析算法\n\n' +
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
    
    // 背景移除需要相册ID参数（可选）
    if (showAlbumIdInput.value) {
      // albumId 可选，不填则处理所有图片
      const albumId = albumIdInput.value.trim()
      config.params = { 
        ...(albumId ? { albumId: albumId } : {}),
        batchSize: 50,
        saveToPhoto: true 
      }
    }
    
    if (showPathInput.value && pathInput.value.trim()) {
      config.params = { ...(config.params || {}), path: pathInput.value.trim() }
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
  await Promise.all([
    fetchScanStatus(),
    loadScanProviderOptions(),
    ...(authStore.isSuperAdmin ? [fetchScanTasks(), fetchOperationLogs(), fetchLoginRecords()] : [])
  ])
  scanTimer = window.setInterval(() => {
    fetchScanStatus()
    if (authStore.isSuperAdmin) {
      fetchScanTasks()
      fetchOperationLogs()
      fetchLoginRecords()
    }
  }, 5000)
})

onUnmounted(() => {
  if (scanTimer) {
    clearInterval(scanTimer)
    scanTimer = null
  }
  stopTaskPoll()
  stopTaskDetailPoll()
})

const showPathInput = computed(() => selectedApi.value.includes('/admin/scan'))
const showFaceSimilarInputs = computed(() => selectedApi.value.includes('/admin/faces/{id}/similar'))
</script>
