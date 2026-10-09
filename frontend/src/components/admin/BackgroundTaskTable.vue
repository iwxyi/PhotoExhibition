<template>
  <section class="glass-panel admin-task-overview" aria-label="后台任务">
    <header class="admin-task-overview__head">
      <div>
        <h2>{{ multiAccount && auth.isSuperAdmin ? '全部账号的后台任务' : '我的后台任务' }}</h2>
      </div>
      <div class="admin-task-overview__actions">
        <button class="admin-button-soft" :disabled="busy || owner === 'system'" @click="controlScope(true)">暂停{{ scopeLabel }}任务</button>
        <button class="admin-button-soft" :disabled="busy || owner === 'system'" @click="controlScope(false)">恢复{{ scopeLabel }}任务</button>
        <button class="admin-button-soft" :disabled="busy || owner === 'system' || !counts.failed" @click="retryFailures">重试近期失败</button>
        <button class="admin-button-soft" :disabled="busy" @click="load">刷新</button>
      </div>
    </header>
    <label v-if="multiAccount && auth.isSuperAdmin" class="admin-task-overview__owner">账号
      <select v-model="owner" class="admin-field"><option value="all">全部账号</option><option value="system">系统任务</option><option v-for="account in accounts" :key="account.id" :value="String(account.id)">{{ account.nickname || account.username || `用户 #${account.id}` }}</option></select>
    </label>
    <p v-if="error" class="text-rose-300 text-sm" role="alert">{{ error }}</p>
    <p v-if="feedback" class="admin-task-feedback" role="status">{{ feedback }}</p>
    <div class="admin-task-overview__tabs" aria-label="任务筛选">
      <button v-for="filter in filters" :key="filter.value" :class="{ 'is-selected': view === filter.value }" :aria-pressed="view === filter.value" @click="view = filter.value">{{ filter.label }} <span>{{ filter.value === 'all' ? Object.values(counts).reduce((sum, value) => sum + value, 0) : counts[filter.value] || 0 }}</span></button>
    </div>
    <div class="admin-task-overview__body admin-task-table-scroll" :aria-busy="loading">
      <table class="admin-data-table admin-task-history__table">
        <thead><tr><th>任务</th><th v-if="multiAccount">账号</th><th>状态</th><th>进度</th><th>失败 / 跳过</th><th>更新时间</th><th>结果</th><th>操作</th></tr></thead>
        <tbody>
          <tr v-for="task in rows" :key="key(task)">
            <td><button class="admin-task-name" @click="selected = task">{{ taskLabel(task) }} <span class="admin-table-muted">#{{ task.id }}</span></button><div v-if="task.sourceJobId" class="admin-table-muted">来源任务 #{{ task.sourceJobId }}</div></td>
            <td v-if="multiAccount">{{ task.ownerLabel || `用户 #${task.ownerUserId ?? task.userId ?? task.requestedByUserId ?? '—'}` }}</td>
            <td><span :class="statusClass(task.status)">{{ task.status === 'RUNNING' && task.cancelRequested ? '正在取消' : task.status === 'RUNNING' && task.pauseRequested ? '正在暂停' : statusLabel(task.status) }}</span></td>
            <td class="tabular-nums"><div>{{ task.processedItems || 0 }} / {{ task.totalItems || 0 }} <span class="admin-table-muted">({{ percent(task) }}%)</span></div><div class="admin-task-table-progress" role="progressbar" :aria-label="`${taskLabel(task)}进度`" :aria-valuenow="percent(task)" aria-valuemin="0" aria-valuemax="100"><span :style="{ width: `${percent(task)}%` }"></span></div></td>
            <td class="tabular-nums">{{ task.failedItems || 0 }} / {{ task.skippedItems || 0 }}</td>
            <td>{{ formatDate(task.updatedAt || task.finishedAt || task.createdAt) }}</td>
            <td class="admin-task-history__detail"><div :class="['FAILED','PARTIAL_SUCCESS','BLOCKED'].includes(task.status) ? 'text-rose-300' : 'admin-table-muted'">{{ taskSummary(task) }}</div><div v-if="actionHint(task)" class="admin-task-action-hint">{{ actionHint(task) }}</div></td>
            <td><div class="admin-task-table-actions">
              <button v-if="(!task.scan || auth.isSuperAdmin) && ['FAILED','PARTIAL_SUCCESS','BLOCKED'].includes(task.status)" class="admin-button-soft" :disabled="busy" @click="act(task, 'retry')">重试失败项</button>
              <button v-if="['FAILED','PARTIAL_SUCCESS'].includes(task.status)" class="admin-button-soft" :disabled="busy" @click="act(task, 'ignore')">忽略</button>
              <button v-if="(!task.scan || auth.isSuperAdmin) && ['RUNNING','QUEUED','PENDING'].includes(task.status)" class="admin-button-soft" :disabled="busy || task.pauseRequested || task.cancelRequested" @click="act(task, 'pause')">暂停</button>
              <button v-if="(!task.scan || auth.isSuperAdmin) && task.status === 'PAUSED'" class="admin-button-soft" :disabled="busy" @click="act(task, task.scan ? 'retry' : 'resume')">恢复</button>
              <button v-if="(!task.scan || auth.isSuperAdmin) && ['RUNNING','QUEUED','PENDING','PAUSED','BLOCKED','WAITING_DEPENDENCY'].includes(task.status)" class="admin-button-soft" :disabled="busy || task.cancelRequested" @click="act(task, 'cancel')">取消</button>
            </div></td>
          </tr>
          <tr v-if="!rows.length"><td :colspan="multiAccount ? 8 : 7" class="admin-task-overview__empty">{{ loading ? '正在加载任务…' : error ? '暂时无法获取任务记录' : view === 'failed' ? '没有待处理的异常' : view === 'all' ? '暂无任务记录' : '当前没有此类任务' }}</td></tr>
        </tbody>
      </table>
    </div>
    <footer class="admin-task-history__pagination">
      <span class="admin-task-connection" :class="error ? 'text-rose-300' : 'admin-table-muted'">{{ error ? '连接异常 · 数据可能不是最新' : lastUpdated ? `更新于 ${lastUpdated}` : '正在连接' }}</span>
      <span>共 {{ total }} 条</span>
      <label>每页 <select v-model.number="size" class="admin-field"><option :value="20">20</option><option :value="50">50</option><option :value="100">100</option></select> 条</label>
      <button class="admin-button-soft" :disabled="loading || page === 0" @click="page--">上一页</button>
      <span>{{ page + 1 }} / {{ totalPages }}</span>
      <button class="admin-button-soft" :disabled="loading || page + 1 >= totalPages" @click="page++">下一页</button>
    </footer>
    <dialog ref="detailDialog" class="admin-task-dialog" @close="selected = null" @click="closeBackdrop">
      <template v-if="selected">
        <header class="admin-task-overview__head"><h2>{{ taskLabel(selected) }} #{{ selected.id }}</h2><button class="admin-button-soft" @click="selected = null">关闭</button></header>
        <dl class="admin-task-details">
          <dt>状态</dt><dd>{{ statusLabel(selected.status) }}</dd>
          <dt>进度</dt><dd>{{ selected.processedItems || 0 }} / {{ selected.totalItems || 0 }}</dd>
          <dt>结果</dt><dd>{{ taskSummary(selected) }}</dd>
          <dt v-if="actionHint(selected)">下一步</dt><dd v-if="actionHint(selected)">{{ actionHint(selected) }}</dd>
          <dt>创建时间</dt><dd>{{ formatDate(selected.createdAt) }}</dd>
          <dt>更新时间</dt><dd>{{ formatDate(selected.updatedAt || selected.finishedAt) }}</dd>
          <dt v-if="selected.sourceJobId">来源任务</dt><dd v-if="selected.sourceJobId">#{{ selected.sourceJobId }}</dd>
          <dt v-if="parameterSummary(selected)">配置</dt><dd v-if="parameterSummary(selected)">{{ parameterSummary(selected) }}</dd>
        </dl>
        <details v-if="rawError(selected)"><summary>技术详情</summary><pre>{{ rawError(selected) }}</pre></details>
      </template>
    </dialog>
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
const feedback = ref('')
const view = ref('all')
const selected = ref<any>(null)
const detailDialog = ref<HTMLDialogElement | null>(null)
const lastUpdated = ref('')
const filters = [{ value: 'all', label: '全部' }, { value: 'failed', label: '需处理' }, { value: 'active', label: '执行中' }, { value: 'waiting', label: '等待' }, { value: 'done', label: '已结束' }]
const globalScope = computed(() => props.multiAccount && auth.isSuperAdmin && owner.value === 'all')
const scopeLabel = computed(() => globalScope.value ? '全部' : props.multiAccount ? '此账号' : '我的')
const counts = ref<Record<string, number>>({ active: 0, waiting: 0, failed: 0, done: 0 })
const closeBackdrop = (event: MouseEvent) => { if (event.target === detailDialog.value) { const bounds = detailDialog.value.getBoundingClientRect(); if (event.clientX < bounds.left || event.clientX > bounds.right || event.clientY < bounds.top || event.clientY > bounds.bottom) selected.value = null } }
watch(selected, task => { if (task && !detailDialog.value?.open) detailDialog.value?.showModal(); else if (!task) detailDialog.value?.close() })
let requestId = 0
let loadedScope = ''
let timer: ReturnType<typeof setInterval> | undefined
const key = (task: any) => `${task.scan ? 'scan' : 'job'}-${task.id}`
const percent = (task: any) => task.totalItems > 0 ? Math.min(100, Math.floor((task.processedItems || 0) * 100 / task.totalItems)) : 0
const formatDate = (value: string) => value ? new Date(value).toLocaleString('zh-CN') : '—'
const statusLabel = (status: string) => ({ PENDING: '待处理', QUEUED: '排队中', RUNNING: '执行中', PAUSED: '已暂停', BLOCKED: '已阻塞', WAITING_DEPENDENCY: '等待条件', SUCCEEDED: '已完成', COMPLETED: '已完成', PARTIAL_SUCCESS: '部分成功', FAILED: '失败', SKIPPED: '已跳过', CANCELED: '已取消', IGNORED: '已忽略', RETRIED: '已重试' } as Record<string, string>)[status] || status
const statusClass = (status: string) => ['FAILED','PARTIAL_SUCCESS'].includes(status) ? 'text-rose-300' : ['SUCCEEDED','COMPLETED'].includes(status) ? 'text-emerald-300' : ['RUNNING','QUEUED','BLOCKED','PENDING'].includes(status) ? 'text-amber-300' : 'admin-table-muted'
const rawError = (task: any): string => task.errorSummary || task.errorMessage || task.blockingReason || ''
const failureKind = (task: any) => {
  const known: Record<string, string> = { QUOTA_EXHAUSTED: 'quota', TRANSIENT_NETWORK: 'network', SOURCE_MISSING: 'file', MODEL_UNAVAILABLE: 'model' }
  if (known[task.errorCode]) return known[task.errorCode]
  const message = rawError(task)
  if (/insufficient_quota|quota.*(exceed|exhaust)|额度|余额|配额/i.test(message)) return 'quota'
  if (/文件不存在|file.*not.*found|no such file/i.test(message)) return 'file'
  if (/已删除|不存在的照片|photo.*(deleted|not found)/i.test(message)) return 'deleted'
  if (/401|403|api.?key|unauthorized|鉴权|认证/i.test(message)) return 'auth'
  if (/429|rate.?limit|限流/i.test(message)) return 'rate'
  if (/timeout|timed out|connect|connection|超时|连接/i.test(message)) return 'network'
  return ''
}
const actionHint = (task: any) => {
  if (!['FAILED', 'PARTIAL_SUCCESS', 'BLOCKED'].includes(task.status)) return ''
  return ({ quota: '补充额度后，再重试失败项。', model: '启用或加载所需模型后，再重试。', file: '先恢复源文件再重试；不再需要的记录可忽略。', deleted: '照片已删除，无需继续处理，可忽略该失败。', auth: '检查大模型接口地址和密钥后，再重试。', rate: '等待接口限流解除后，再重试。', network: '检查网络和接口服务后，再重试。' } as Record<string, string>)[failureKind(task)] || '排除错误原因后重试；不再处理的失败可忽略。'
}
const parameterSummary = (task: any) => Object.entries(task.parameters || {}).map(([name, value]) => {
  if (name === 'preserveBindings') return value ? '保留已有人脸绑定' : '重新建立人脸绑定'
  if (name === 'outputMaxSize') return `输出最长边 ${value} 像素`
  const labels: Record<string, string> = { modelKey: '模型', forceReprocess: '强制重新处理', forceReanalyze: '强制重新分析', forceRebuild: '强制重建' }
  const display = typeof value === 'boolean' ? value ? '是' : '否' : typeof value === 'object' ? JSON.stringify(value) : String(value)
  return `${labels[name] || name}：${display}`
}).join('；')
const taskSummary = (task: any) => (task.status === 'RETRIED' ? '失败项已转入新的重试任务' : task.status === 'IGNORED' ? '该失败已忽略' : ['SUCCEEDED','COMPLETED'].includes(task.status) ? `成功 ${task.succeededItems ?? task.processedItems ?? 0} 项` : '')
  || ({ quota: '大模型额度不足', model: '所需模型不可用', file: '源文件不存在', deleted: '目标照片已删除', auth: '大模型接口认证失败', rate: '接口请求受到限流', network: '接口连接失败或超时' } as Record<string, string>)[failureKind(task)]
  || rawError(task)
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
  const scope = `${owner.value}:${view.value}:${page.value}:${size.value}`
  if (loadedScope && loadedScope !== scope) {
    rows.value = []
    total.value = 0
    totalPages.value = 1
    lastUpdated.value = ''
  }
  loading.value = true
  try {
    const params: Record<string, any> = { page: page.value, size: size.value, currentAccount: !props.multiAccount, view: view.value }
    if (props.multiAccount && owner.value !== 'all') {
      if (owner.value === 'system') params.systemOnly = true
      else params.ownerUserId = owner.value
    }
    const { data } = await api.get('/admin/background-jobs/records', { params })
    if (request !== requestId) return
    rows.value = data.items || []
    counts.value = { active: 0, waiting: 0, failed: 0, done: 0, ...data.statistics }
    lastUpdated.value = new Date().toLocaleTimeString('zh-CN')
    if (selected.value) selected.value = rows.value.find(task => key(task) === key(selected.value)) || selected.value
    total.value = data.total || 0
    totalPages.value = data.totalPages || 1
    page.value = data.page || 0
    loadedScope = `${owner.value}:${view.value}:${page.value}:${size.value}`
    error.value = ''
  } catch (e: any) {
    if (request === requestId) error.value = e?.response?.status === 403 ? '无权查看此范围的任务' : '暂时无法连接任务服务，请稍后刷新'
  } finally { if (request === requestId) loading.value = false }
}
const mutate = async (action: () => Promise<any>, message: (data: any) => string) => {
  if (busy.value) return
  busy.value = true
  feedback.value = ''
  error.value = ''
  try { const { data } = await action(); feedback.value = message(data); await load() }
  catch (e: any) { error.value = e?.response?.data?.details || e?.response?.data?.error || e.message }
  finally { busy.value = false }
}
const act = (task: any, action: string) => {
  if (busy.value || (action === 'cancel' && !window.confirm('取消此任务？已完成的结果会保留，未处理项不再执行。'))) return
  return mutate(() => {
  const endpoint = task.scan
    ? action === 'ignore' ? `/admin/background-jobs/scans/${task.id}/ignore` : `/admin/scan/tasks/${task.id}/${action}`
    : `/admin/background-jobs/${task.id}/${action}`
  return api.post(endpoint, action === 'retry' ? { includeHistorical: false } : undefined)
}, data => action === 'retry' ? `已提交重试任务 #${data.id ?? data.taskId ?? '—'}，原失败记录已标记为已重试。`
  : ({ pause: '暂停请求已提交，当前处理项结束后暂停。', resume: '任务已恢复。', cancel: '取消请求已提交，当前处理项结束后取消。', ignore: '已忽略该失败记录。' } as Record<string, string>)[action] || '操作成功。')
}
const controlScope = (paused: boolean) => {
  if (globalScope.value && !window.confirm(`${paused ? '暂停' : '恢复'}所有账号的任务？已完成结果会保留，正在处理的单项结束后生效。`)) return
  return mutate(() => api.post(globalScope.value
  ? `/admin/background-jobs/control/${paused ? 'pause-all' : 'resume-all'}`
  : `/admin/background-jobs/control/users/${props.multiAccount ? owner.value : auth.userId}/${paused ? 'pause' : 'resume'}`), () => paused ? '已暂停该范围内的任务，正在处理的项目结束后生效。' : '已恢复该范围内的任务；单独暂停的任务需单独恢复。')
}
const retryFailures = () => mutate(() => api.post('/admin/background-jobs/retry-failed', {
  ownerUserId: globalScope.value ? null : props.multiAccount ? Number(owner.value) : Number(auth.userId), includeHistorical: false
}), data => `已提交 ${data.jobs?.length || 0} 个重试任务，仅重试近期失败项，不重复处理成功项。`)
watch([owner, size, view], () => { selected.value = null; if (page.value !== 0) page.value = 0; else void load() })
watch(page, () => { void load() })
onMounted(() => { void load(); timer = setInterval(() => { if (!loading.value && !busy.value) void load() }, 5000) })
onUnmounted(() => { requestId++; if (timer) clearInterval(timer) })
</script>
