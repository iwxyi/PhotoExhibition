<template>
  <section class="glass-panel admin-task-overview" aria-label="后台任务">
    <header class="admin-task-overview__head">
      <h2>后台任务</h2>
      <div class="admin-task-overview__actions">
        <button class="admin-button-soft" :disabled="busy || owner === 'system'" @click="controlScope(true)">{{ globalScope ? '整体暂停' : '暂停任务' }}</button>
        <button class="admin-button-soft" :disabled="busy || owner === 'system'" @click="controlScope(false)">{{ globalScope ? '整体恢复' : '恢复任务' }}</button>
        <button class="admin-button-soft" :disabled="busy || owner === 'system'" @click="retryFailures">批量重试失败</button>
        <button class="admin-button-soft" :disabled="busy" @click="load">刷新</button>
      </div>
    </header>
    <label v-if="multiAccount && auth.isSuperAdmin" class="admin-task-overview__owner">账号
      <select v-model="owner" class="admin-field"><option value="all">全部账号</option><option value="system">系统任务</option><option v-for="account in accounts" :key="account.id" :value="String(account.id)">{{ account.nickname || account.username || `用户 #${account.id}` }}</option></select>
    </label>
    <p v-if="error" class="text-rose-300 text-sm" role="alert">{{ error }}</p>
    <div class="admin-task-overview__body admin-task-table-scroll" :aria-busy="loading">
      <table class="admin-data-table admin-task-history__table">
        <thead><tr><th>任务</th><th v-if="multiAccount">账号</th><th>状态</th><th>进度</th><th>失败 / 跳过</th><th>创建时间</th><th>更新时间</th><th>参数 / 错误</th><th>操作</th></tr></thead>
        <tbody>
          <tr v-for="task in rows" :key="key(task)">
            <td>{{ taskLabel(task) }} <span class="admin-table-muted">#{{ task.id }}</span><div v-if="task.sourceJobId" class="admin-table-muted">重试自 #{{ task.sourceJobId }}</div></td>
            <td v-if="multiAccount">{{ task.ownerLabel || `用户 #${task.ownerUserId ?? task.userId ?? task.requestedByUserId ?? '—'}` }}</td>
            <td><span :class="statusClass(task.status)">{{ statusLabel(task.status) }}</span></td>
            <td class="tabular-nums"><div>{{ task.processedItems || 0 }} / {{ task.totalItems || 0 }}</div><div class="admin-task-table-progress" role="progressbar" :aria-label="`${taskLabel(task)}进度`" :aria-valuenow="percent(task)" aria-valuemin="0" aria-valuemax="100"><span :style="{ width: `${percent(task)}%` }"></span></div></td>
            <td class="tabular-nums">{{ task.failedItems || 0 }} / {{ task.skippedItems || 0 }}</td>
            <td>{{ formatDate(task.createdAt) }}</td><td>{{ formatDate(task.updatedAt || task.finishedAt || task.createdAt) }}</td>
            <td class="admin-task-history__detail"><div v-if="task.parameters && Object.keys(task.parameters).length">{{ JSON.stringify(task.parameters) }}</div><div :class="['FAILED','PARTIAL_SUCCESS','BLOCKED'].includes(task.status) ? 'text-rose-300' : 'admin-table-muted'">{{ taskSummary(task) }}</div></td>
            <td><div class="admin-task-table-actions">
              <button v-if="(!task.scan || auth.isSuperAdmin) && ['FAILED','PARTIAL_SUCCESS','BLOCKED'].includes(task.status)" class="admin-button-soft" :disabled="busy" @click="act(task, 'retry')">重试失败项</button>
              <button v-if="['FAILED','PARTIAL_SUCCESS'].includes(task.status)" class="admin-button-soft" :disabled="busy" @click="act(task, 'ignore')">忽略</button>
              <button v-if="(!task.scan || auth.isSuperAdmin) && ['RUNNING','QUEUED','PENDING'].includes(task.status)" class="admin-button-soft" :disabled="busy" @click="act(task, 'pause')">暂停</button>
              <button v-if="(!task.scan || auth.isSuperAdmin) && task.status === 'PAUSED'" class="admin-button-soft" :disabled="busy" @click="act(task, task.scan ? 'retry' : 'resume')">恢复</button>
              <button v-if="(!task.scan || auth.isSuperAdmin) && ['RUNNING','QUEUED','PENDING','PAUSED','BLOCKED','WAITING_DEPENDENCY'].includes(task.status)" class="admin-button-soft" :disabled="busy" @click="act(task, 'cancel')">取消</button>
            </div></td>
          </tr>
          <tr v-if="!rows.length"><td :colspan="multiAccount ? 9 : 8" class="admin-task-overview__empty">{{ loading ? '正在加载任务…' : '暂无任务记录' }}</td></tr>
        </tbody>
      </table>
    </div>
    <footer class="admin-task-history__pagination">
      <span>共 {{ total }} 条</span>
      <label>每页 <select v-model.number="size" class="admin-field"><option :value="20">20</option><option :value="50">50</option><option :value="100">100</option></select> 条</label>
      <button class="admin-button-soft" :disabled="loading || page === 0" @click="page--">上一页</button>
      <span>{{ page + 1 }} / {{ totalPages }}</span>
      <button class="admin-button-soft" :disabled="loading || page + 1 >= totalPages" @click="page++">下一页</button>
    </footer>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { api } from '@/api'
import { useAuthStore } from '@/stores/auth'

const props = withDefaults(defineProps<{ multiAccount?: boolean; accounts?: any[] }>(), { multiAccount: false, accounts: () => [] })
const auth = useAuthStore()
const owner = ref('all')
const page = ref(0)
const size = ref(20)
const total = ref(0)
const totalPages = ref(1)
const rows = ref<any[]>([])
const loading = ref(false)
const busy = ref(false)
const error = ref('')
const globalScope = computed(() => props.multiAccount && auth.isSuperAdmin && owner.value === 'all')
let requestId = 0
let timer: ReturnType<typeof setInterval> | undefined
const key = (task: any) => `${task.scan ? 'scan' : 'job'}-${task.id}`
const percent = (task: any) => task.totalItems > 0 ? Math.min(100, Math.floor((task.processedItems || 0) * 100 / task.totalItems)) : 0
const formatDate = (value: string) => value ? new Date(value).toLocaleString('zh-CN') : '—'
const statusLabel = (status: string) => ({ PENDING: '待处理', QUEUED: '排队中', RUNNING: '执行中', PAUSED: '已暂停', BLOCKED: '已阻塞', WAITING_DEPENDENCY: '等待条件', SUCCEEDED: '已完成', COMPLETED: '已完成', PARTIAL_SUCCESS: '部分成功', FAILED: '失败', SKIPPED: '已跳过', CANCELED: '已取消', IGNORED: '已忽略', RETRIED: '已重试' } as Record<string, string>)[status] || status
const statusClass = (status: string) => ['FAILED','PARTIAL_SUCCESS'].includes(status) ? 'text-rose-300' : ['SUCCEEDED','COMPLETED'].includes(status) ? 'text-emerald-300' : ['RUNNING','QUEUED','BLOCKED','PENDING'].includes(status) ? 'text-amber-300' : 'admin-table-muted'
const taskSummary = (task: any) => task.errorSummary || task.errorMessage || task.blockingReason
  || (['SUCCEEDED','COMPLETED'].includes(task.status) ? `成功 ${task.succeededItems ?? task.processedItems ?? 0} 项`
    : task.status === 'SKIPPED' ? `跳过 ${task.skippedItems || 0} 项`
      : task.status === 'RETRIED' ? '失败项已转入新的重试任务'
        : task.status === 'IGNORED' ? '该失败已忽略' : '—')
const taskLabel = (task: any) => {
  const type = task.scan ? task.taskType : task.jobType
  const labels: Record<string, string> = { FULL_SCAN: '强制扫描', UPLOAD_SCAN: '上传扫描', RESUME_SCAN: '续扫任务', INCREMENTAL_SCAN: '增量扫描', VISUAL_ANALYSIS: 'AI 视觉分析', FACE_RESCAN: '重建人脸', FACE_EMBEDDING: '重建人脸特征', AI_SCORING: 'AI 评分', BACKGROUND_REMOVAL: '背景移除', COLOR_RECALCULATE: '颜色重算', COLOR_CATEGORY: '颜色分类', EXIF_REBUILD: 'EXIF 重建', PHOTO_TIME_REBUILD: '照片时间重建', HASH_REBUILD: '哈希补全', BACKGROUND_CACHE_CLEAR: '背景缓存清理', ALBUM_ATMOSPHERE_REBUILD: '相册氛围重建', MODEL_REBUILD_FACE_DETECTION: '模型重建 · 人脸检测', MODEL_REBUILD_FACE_RECOGNITION: '模型重建 · 人脸特征', MODEL_REBUILD_IMAGE_CLASSIFICATION: '模型重建 · 图像分类', MODEL_REBUILD_SALIENCY_DETECTION: '模型重建 · 显著性检测', MODEL_REBUILD_SCENE_RECOGNITION: '模型重建 · 场景识别', MODEL_REBUILD_EMOTION_ANALYSIS: '模型重建 · 情绪分析', MODEL_REBUILD_BACKGROUND_REMOVAL: '模型重建 · 背景移除' }
  return labels[type] || type || '后台任务'
}
const load = async () => {
  const request = ++requestId
  loading.value = true
  try {
    const params: Record<string, any> = { page: page.value, size: size.value, currentAccount: !props.multiAccount }
    if (props.multiAccount && owner.value !== 'all') {
      if (owner.value === 'system') params.systemOnly = true
      else params.ownerUserId = owner.value
    }
    const { data } = await api.get('/admin/background-jobs/records', { params })
    if (request !== requestId) return
    rows.value = data.items || []
    total.value = data.total || 0
    totalPages.value = data.totalPages || 1
    page.value = data.page || 0
    error.value = ''
  } catch (e: any) {
    if (request === requestId) error.value = e?.response?.data?.details || e?.response?.data?.error || '加载任务失败'
  } finally { if (request === requestId) loading.value = false }
}
const mutate = async (action: () => Promise<any>) => {
  if (busy.value) return
  busy.value = true
  try { await action(); await load() }
  catch (e: any) { error.value = e?.response?.data?.details || e?.response?.data?.error || e.message }
  finally { busy.value = false }
}
const act = (task: any, action: string) => mutate(() => {
  const endpoint = task.scan
    ? action === 'ignore' ? `/admin/background-jobs/scans/${task.id}/ignore` : `/admin/scan/tasks/${task.id}/${action}`
    : `/admin/background-jobs/${task.id}/${action}`
  return api.post(endpoint, action === 'retry' ? { includeHistorical: false } : undefined)
})
const controlScope = (paused: boolean) => mutate(() => api.post(globalScope.value
  ? `/admin/background-jobs/control/${paused ? 'pause-all' : 'resume-all'}`
  : `/admin/background-jobs/control/users/${props.multiAccount ? owner.value : auth.userId}/${paused ? 'pause' : 'resume'}`))
const retryFailures = () => mutate(() => api.post('/admin/background-jobs/retry-failed', {
  ownerUserId: globalScope.value ? null : props.multiAccount ? Number(owner.value) : Number(auth.userId), includeHistorical: false
}))
watch([owner, size], () => { if (page.value !== 0) page.value = 0; else void load() })
watch(page, () => { void load() })
onMounted(() => { void load(); timer = setInterval(() => { if (!loading.value && !busy.value) void load() }, 5000) })
onUnmounted(() => { requestId++; if (timer) clearInterval(timer) })
</script>
