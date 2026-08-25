<template>
  <div class="min-h-screen admin-shell admin-folders-page">
    <div class="max-w-5xl mx-auto px-4 sm:px-6 lg:px-8 py-4 space-y-4">
      <div class="admin-folders-hero flex items-center justify-between gap-4">
        <h1 class="text-2xl font-light admin-page-title">数据迁移</h1>
        <router-link to="/admin" class="admin-button-soft admin-page-back-link px-4 py-2 rounded-lg transition-colors">返回</router-link>
      </div>

      <!-- 移动/重命名 -->
      <div class="glass-panel admin-folders-panel p-6 space-y-4">
        <h2 class="text-xl font-light">移动 / 重命名目录</h2>
        <p class="text-sm text-gray-300">请输入相对当前根目录的路径，例如 `分类/相册名`。无需也不建议输入绝对路径。</p>
        <div class="space-y-3">
          <div class="flex gap-2 flex-wrap">
            <label class="flex-1 min-w-[260px] space-y-2">
              <span class="text-sm text-gray-300">源目录相对路径</span>
              <input v-model="source" placeholder="例如 分类/旧相册名" class="w-full px-3 py-2 bg-gray-700 border border-gray-600 rounded text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
              <span class="block text-xs text-gray-400">留空表示当前用户目录根路径。</span>
            </label>
            <button @click="useBaseAsSource" class="px-3 py-2 bg-gray-700 hover:bg-gray-600 rounded text-sm">根目录</button>
            <select
              v-if="dirOptions.length"
              v-model="source"
              class="px-3 py-2 bg-gray-700 border border-gray-600 rounded text-sm max-w-full"
            >
              <option v-for="d in dirOptions" :key="d" :value="d">{{ d }}</option>
            </select>
          </div>
          <label class="block space-y-2">
            <span class="text-sm text-gray-300">目标目录相对路径</span>
            <input v-model="target" placeholder="例如 分类/新相册名" class="w-full px-3 py-2 bg-gray-700 border border-gray-600 rounded text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
            <span class="block text-xs text-gray-400">请输入目标目录完整相对路径，例如 `旅行/东京夜景`。</span>
          </label>
          <button
            @click="move"
            :disabled="loading || !source || !target"
            class="btn-primary disabled:opacity-50"
          >
            {{ loading ? '执行中...' : '执行移动/重命名' }}
          </button>
        </div>
      </div>

      <!-- 删除目录 -->
      <div class="glass-panel admin-folders-panel admin-folders-danger p-6 space-y-4">
        <h2 class="text-xl font-light text-red-300">删除目录（谨慎）</h2>
        <p class="text-sm text-gray-300">删除范围同样基于当前根目录，只输入相对路径即可。</p>
        <div class="space-y-3">
          <div class="flex gap-2 flex-wrap">
            <label class="flex-1 min-w-[260px] space-y-2">
              <span class="text-sm text-gray-300">要删除的目录相对路径</span>
              <input v-model="deletePath" placeholder="例如 分类/待删除相册" class="w-full px-3 py-2 bg-gray-700 border border-gray-600 rounded text-sm focus:outline-none focus:ring-2 focus:ring-red-500" />
              <span class="block text-xs text-gray-400">留空表示当前用户目录根路径，请谨慎操作。</span>
            </label>
            <button @click="useBaseAsDelete" class="px-3 py-2 bg-gray-700 hover:bg-gray-600 rounded text-sm">根目录</button>
            <select
              v-if="dirOptions.length"
              v-model="deletePath"
              class="px-3 py-2 bg-gray-700 border border-gray-600 rounded text-sm max-w-full"
            >
              <option v-for="d in dirOptions" :key="d" :value="d">{{ d }}</option>
            </select>
          </div>
          <button
            @click="remove"
            :disabled="loading || !deletePath"
            class="px-4 py-2 bg-red-600 hover:bg-red-700 rounded-lg disabled:opacity-50"
          >
            {{ loading ? '执行中...' : '删除目录及记录' }}
          </button>
        </div>
        <p class="text-sm text-gray-300">会删除数据库中该目录下的相册/照片记录，并删除磁盘上的文件夹。</p>
      </div>

      <!-- 结果提示 -->
      <div v-if="message" class="glass-panel admin-folders-panel p-4 text-sm" :class="error ? 'text-red-300' : 'text-green-300'">
        {{ message }}
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '@/api'
import { useAdminFeedback } from '@/composables/useAdminFeedback'

const router = useRouter()
const { confirm } = useAdminFeedback()

const source = ref('')
const target = ref('')
const deletePath = ref('')
const loading = ref(false)
const message = ref('')
const error = ref(false)
const basePath = ref('')
const dirOptions = ref<string[]>([])

const normalizePath = (value?: string | null) => (value || '').replace(/\\/g, '/').replace(/\/+/g, '/').replace(/^\/|\/$/g, '')

const toRelativePath = (value?: string | null) => {
  const normalized = normalizePath(value)
  const normalizedBase = normalizePath(basePath.value)
  if (!normalized) return ''
  if (!normalizedBase) return normalized
  if (normalized === normalizedBase) return ''
  return normalized.startsWith(normalizedBase + '/') ? normalized.slice(normalizedBase.length + 1) : normalized
}

const toScopedAbsolutePath = (value?: string | null) => {
  const relative = normalizePath(value)
  if (!relative) return basePath.value
  if (relative.includes('..')) {
    throw new Error('路径不允许包含 ..')
  }
  const normalizedBase = (basePath.value || '').replace(/\\/g, '/').replace(/\/+$/g, '')
  return normalizedBase ? `${normalizedBase}/${relative}` : relative
}

const loadBasePath = async () => {
  try {
    const res = await api.get('/admin/folders/base-path')
    basePath.value = res.data?.basePath || ''
    if (basePath.value) {
      source.value = ''
      deletePath.value = ''
    }
  } catch {
    // ignore
  }
}

const loadDirs = async (path?: string) => {
  try {
    const absolutePath = path ? toScopedAbsolutePath(path) : undefined
    const res = await api.get('/admin/folders/list', { params: { path: absolutePath } })
    dirOptions.value = (res.data?.dirs || [])
      .map((d: string) => toRelativePath(d))
      .filter((d: string) => d !== '')
  } catch {
    dirOptions.value = []
  }
}

const useBaseAsSource = () => {
  source.value = ''
}
const useBaseAsDelete = () => {
  deletePath.value = ''
}

const move = async () => {
  loading.value = true
  message.value = ''
  error.value = false
  try {
    await api.post('/admin/folders/move', null, {
      params: {
        source: toScopedAbsolutePath(source.value),
        target: toScopedAbsolutePath(target.value)
      }
    })
    message.value = '移动完成'
  } catch (e: any) {
    error.value = true
    message.value = e.response?.data?.error || e.message || '移动失败'
  } finally {
    loading.value = false
  }
}

const remove = async () => {
  if (!await confirm({ title: '删除目录', message: '将删除目录以及数据库记录，此操作不可撤销。', confirmLabel: '删除目录', tone: 'danger' })) return
  loading.value = true
  message.value = ''
  error.value = false
  try {
    await api.delete('/admin/folders', { params: { path: toScopedAbsolutePath(deletePath.value) } })
    message.value = '删除完成'
  } catch (e: any) {
    error.value = true
    message.value = e.response?.data?.error || e.message || '删除失败'
  } finally {
    loading.value = false
  }
}

const handleGlobalKeydown = (e: KeyboardEvent) => {
  if (e.key === 'Escape') {
    // 返回首页
    router.push('/admin')
  }
}

onMounted(() => {
  loadBasePath()
  loadDirs()
  window.addEventListener('keydown', handleGlobalKeydown)
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', handleGlobalKeydown)
})
</script>
