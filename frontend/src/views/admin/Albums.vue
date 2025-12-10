<template>
  <div class="min-h-screen bg-gray-900 text-white">
    <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
      <div class="flex items-center justify-between mb-6">
        <h1 class="text-2xl font-light">相册管理</h1>
        <div class="space-x-3">
          <button @click="load" :disabled="loading" class="px-4 py-2 bg-blue-600 hover:bg-blue-700 rounded-lg disabled:opacity-50">刷新</button>
          <router-link to="/admin" class="px-4 py-2 bg-gray-700 hover:bg-gray-600 rounded-lg">返回</router-link>
        </div>
      </div>

      <div class="bg-gray-800 rounded-lg p-4">
        <div class="flex flex-wrap gap-4 mb-4">
          <input v-model="keyword" placeholder="搜索名称/路径" class="px-3 py-2 bg-gray-700 border border-gray-600 rounded w-64 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
          <button @click="load" :disabled="loading" class="px-4 py-2 bg-gray-700 hover:bg-gray-600 rounded-lg text-sm disabled:opacity-50">查询</button>
          <button @click="editSelected" :disabled="selectedIds.length !== 1 || loading" class="px-4 py-2 bg-amber-600 hover:bg-amber-700 rounded-lg text-sm disabled:opacity-50">编辑</button>
          <button @click="deleteSelected" :disabled="selectedIds.length === 0 || loading" class="px-4 py-2 bg-red-600 hover:bg-red-700 rounded-lg text-sm disabled:opacity-50">删除</button>
        </div>

        <div class="overflow-auto">
          <table class="min-w-full text-sm">
            <thead class="text-left text-gray-400">
              <tr>
                <th class="py-2 pr-4">
                  <input type="checkbox" class="accent-blue-500" :checked="allSelected" @change="toggleAll" />
                </th>
                <th class="py-2 pr-4">ID</th>
                <th class="py-2 pr-4">名称</th>
                <th class="py-2 pr-4">路径</th>
                <th class="py-2 pr-4">照片数</th>
                <th class="py-2 pr-4">拍摄时间</th>
                <th class="py-2 pr-4">标签</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="a in albums" :key="a.id" class="border-t border-gray-700 hover:bg-gray-700/60">
                <td class="py-2 pr-4">
                  <input type="checkbox" class="accent-blue-500" v-model="selectedIds" :value="a.id" />
                </td>
                <td class="py-2 pr-4">{{ a.id }}</td>
                <td class="py-2 pr-4 whitespace-nowrap">{{ a.displayTitle || a.name }}</td>
                <td class="py-2 pr-4 text-gray-300">{{ a.path }}</td>
                <td class="py-2 pr-4">{{ a.photoCount }}</td>
                <td class="py-2 pr-4 whitespace-nowrap">{{ formatDate(a.takenAt) }}</td>
                <td class="py-2 pr-4">
                  <span v-for="t in (a.tags || []).slice(0, 3)" :key="t.id" class="px-2 py-1 bg-white/10 rounded text-xs mr-1 inline-block">{{ t.name }}</span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <div class="flex items-center justify-between mt-4 text-sm text-gray-300">
          <span>第 {{ page + 1 }} 页 / 共 {{ totalPages }} 页</span>
          <div class="space-x-2">
            <button @click="prev" :disabled="page===0 || loading" class="px-3 py-1 bg-gray-700 hover:bg-gray-600 rounded disabled:opacity-40">上一页</button>
            <button @click="next" :disabled="page>=totalPages-1 || loading" class="px-3 py-1 bg-gray-700 hover:bg-gray-600 rounded disabled:opacity-40">下一页</button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { api } from '@/api'

const albums = ref<any[]>([])
const loading = ref(false)
const page = ref(0)
const size = ref(12)
const totalPages = ref(1)
const keyword = ref('')
const selectedIds = ref<number[]>([])

const load = async () => {
  loading.value = true
  try {
    const params: any = { page: page.value, size: size.value }
    // 简单关键词过滤（前端过滤）
    const res = await api.get('/albums', { params })
    let content = res.data.content || res.data || []
    if (keyword.value.trim()) {
      const kw = keyword.value.trim().toLowerCase()
      content = content.filter((a: any) =>
        (a.name || '').toLowerCase().includes(kw) ||
        (a.path || '').toLowerCase().includes(kw) ||
        (a.displayTitle || '').toLowerCase().includes(kw)
      )
    }
    albums.value = content
    totalPages.value = res.data.totalPages || 1
  } finally {
    loading.value = false
  }
}

const formatDate = (val?: string) => {
  if (!val) return ''
  return val.slice(0, 10)
}

const allSelected = computed(() => albums.value.length > 0 && selectedIds.value.length === albums.value.length)

const toggleAll = (e: Event) => {
  const checked = (e.target as HTMLInputElement).checked
  if (checked) {
    selectedIds.value = albums.value.map((a: any) => a.id)
  } else {
    selectedIds.value = []
  }
}

const editSelected = async () => {
  if (selectedIds.value.length !== 1) return
  const a = albums.value.find((x: any) => x.id === selectedIds.value[0])
  if (!a) return
  const newName = window.prompt('修改相册名称', a.name)
  if (newName === null || newName.trim() === '') return
  const newDesc = window.prompt('修改描述(可选)', a.description || '')
  await api.put(`/albums/${a.id}`, { name: newName.trim(), description: newDesc ?? '' })
  await load()
}

const deleteSelected = async () => {
  if (selectedIds.value.length === 0) return
  if (!window.confirm(`确定删除选中的 ${selectedIds.value.length} 个相册？`)) return
  for (const id of selectedIds.value) {
    await api.delete(`/albums/${id}`)
  }
  selectedIds.value = []
  await load()
}

const prev = () => {
  if (page.value === 0) return
  page.value--
  load()
}
const next = () => {
  if (page.value >= totalPages.value - 1) return
  page.value++
  load()
}

onMounted(() => {
  load()
})
</script>

