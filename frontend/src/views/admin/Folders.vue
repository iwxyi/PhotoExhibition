<template>
  <div class="min-h-screen admin-shell text-white">
    <div class="max-w-5xl mx-auto px-4 sm:px-6 lg:px-8 py-8 space-y-8">
      <div class="flex items-center justify-between">
        <h1 class="text-2xl font-light">数据迁移</h1>
        <router-link to="/admin" class="px-4 py-2 bg-gray-900/70 hover:bg-gray-700 rounded-lg border border-white/10 transition-colors">返回</router-link>
      </div>

      <!-- 移动/重命名 -->
      <div class="glass-panel p-6 space-y-4">
        <h2 class="text-xl font-light">移动 / 重命名目录</h2>
        <div class="space-y-3">
          <div class="flex gap-2 flex-wrap">
            <input v-model="source" placeholder="源目录绝对路径" class="flex-1 min-w-[260px] px-3 py-2 bg-gray-700 border border-gray-600 rounded text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
            <button @click="useBaseAsSource" class="px-3 py-2 bg-gray-700 hover:bg-gray-600 rounded text-sm">默认路径</button>
            <select
              v-if="dirOptions.length"
              v-model="source"
              class="px-3 py-2 bg-gray-700 border border-gray-600 rounded text-sm max-w-full"
            >
              <option v-for="d in dirOptions" :key="d" :value="d">{{ d }}</option>
            </select>
          </div>
          <input v-model="target" placeholder="目标目录绝对路径" class="w-full px-3 py-2 bg-gray-700 border border-gray-600 rounded text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
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
      <div class="glass-panel p-6 space-y-4">
        <h2 class="text-xl font-light text-red-300">删除目录（谨慎）</h2>
        <div class="space-y-3">
          <div class="flex gap-2 flex-wrap">
            <input v-model="deletePath" placeholder="要删除的目录绝对路径" class="flex-1 min-w-[260px] px-3 py-2 bg-gray-700 border border-gray-600 rounded text-sm focus:outline-none focus:ring-2 focus:ring-red-500" />
            <button @click="useBaseAsDelete" class="px-3 py-2 bg-gray-700 hover:bg-gray-600 rounded text-sm">默认路径</button>
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
      <div v-if="message" class="glass-panel p-4 text-sm" :class="error ? 'text-red-300' : 'text-green-300'">
        {{ message }}
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '@/api'

const router = useRouter()

const source = ref('')
const target = ref('')
const deletePath = ref('')
const loading = ref(false)
const message = ref('')
const error = ref(false)
const basePath = ref('')
const dirOptions = ref<string[]>([])

const loadBasePath = async () => {
  try {
    const res = await api.get('/admin/folders/base-path')
    basePath.value = res.data?.basePath || ''
    if (basePath.value) {
      source.value = basePath.value
      deletePath.value = basePath.value
    }
  } catch {
    // ignore
  }
}

const loadDirs = async (path?: string) => {
  try {
    const res = await api.get('/admin/folders/list', { params: { path } })
    dirOptions.value = res.data?.dirs || []
    // 去掉 base path 本身，列表只展示子目录
    dirOptions.value = dirOptions.value.filter(d => d !== basePath.value)
  } catch {
    dirOptions.value = []
  }
}

const useBaseAsSource = () => {
  if (basePath.value) source.value = basePath.value
}
const useBaseAsDelete = () => {
  if (basePath.value) deletePath.value = basePath.value
}

const move = async () => {
  loading.value = true
  message.value = ''
  error.value = false
  try {
    await api.post('/admin/folders/move', null, { params: { source: source.value, target: target.value } })
    message.value = '移动完成'
  } catch (e: any) {
    error.value = true
    message.value = e.response?.data?.error || e.message || '移动失败'
  } finally {
    loading.value = false
  }
}

const remove = async () => {
  if (!window.confirm('确认删除该目录以及数据库记录？此操作不可撤销。')) return
  loading.value = true
  message.value = ''
  error.value = false
  try {
    await api.delete('/admin/folders', { params: { path: deletePath.value } })
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

