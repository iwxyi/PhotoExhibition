<template>
  <div class="min-h-screen bg-gray-900 text-white">
    <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
      <div class="flex items-center justify-between mb-6">
        <h1 class="text-2xl font-light">图片管理</h1>
        <div class="space-x-3">
          <button @click="load" :disabled="loading" class="px-4 py-2 bg-blue-600 hover:bg-blue-700 rounded-lg disabled:opacity-50">刷新</button>
          <router-link to="/admin" class="px-4 py-2 bg-gray-700 hover:bg-gray-600 rounded-lg">返回</router-link>
        </div>
      </div>

      <div class="bg-gray-800 rounded-lg p-4">
        <div class="flex flex-wrap gap-4 mb-4">
          <input v-model="keyword" placeholder="搜索文件名/相机/镜头" class="px-3 py-2 bg-gray-700 border border-gray-600 rounded w-64 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
          <button @click="load" :disabled="loading" class="px-4 py-2 bg-gray-700 hover:bg-gray-600 rounded-lg text-sm disabled:opacity-50">查询</button>
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
                <th class="py-2 pr-4">文件名</th>
                <th class="py-2 pr-4">相机</th>
                <th class="py-2 pr-4">镜头</th>
                <th class="py-2 pr-4">尺寸</th>
                <th class="py-2 pr-4">格式</th>
                <th class="py-2 pr-4">拍摄时间</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="p in photos" :key="p.id" class="border-t border-gray-700 hover:bg-gray-700/60">
                <td class="py-2 pr-4">
                  <input type="checkbox" class="accent-blue-500" v-model="selectedIds" :value="p.id" />
                </td>
                <td class="py-2 pr-4">{{ p.id }}</td>
                <td class="py-2 pr-4 whitespace-nowrap">{{ p.filename }}</td>
                <td class="py-2 pr-4">{{ p.cameraModel || '-' }}</td>
                <td class="py-2 pr-4">{{ p.lensModel || '-' }}</td>
                <td class="py-2 pr-4">{{ p.width }} x {{ p.height }}</td>
                <td class="py-2 pr-4">{{ p.format }}</td>
                <td class="py-2 pr-4 whitespace-nowrap">{{ formatDate(p.takenAt) }}</td>
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
import { ref, onMounted, computed } from 'vue'
import { api } from '@/api'

const photos = ref<any[]>([])
const loading = ref(false)
const page = ref(0)
const size = ref(20)
const totalPages = ref(1)
const keyword = ref('')
const selectedIds = ref<number[]>([])

const load = async () => {
  loading.value = true
  try {
    const params: any = { page: page.value, size: size.value }
    const res = await api.get('/photos', { params })
    let content = res.data.content || res.data || []
    if (keyword.value.trim()) {
      const kw = keyword.value.trim().toLowerCase()
      content = content.filter((p: any) =>
        (p.filename || '').toLowerCase().includes(kw) ||
        (p.cameraModel || '').toLowerCase().includes(kw) ||
        (p.lensModel || '').toLowerCase().includes(kw)
      )
    }
    photos.value = content
    totalPages.value = res.data.totalPages || 1
  } finally {
    loading.value = false
  }
}

const formatDate = (val?: string) => {
  if (!val) return ''
  return val.slice(0, 10)
}

const allSelected = computed(() => photos.value.length > 0 && selectedIds.value.length === photos.value.length)

const toggleAll = (e: Event) => {
  const checked = (e.target as HTMLInputElement).checked
  selectedIds.value = checked ? photos.value.map((p: any) => p.id) : []
}

const deleteSelected = async () => {
  if (selectedIds.value.length === 0) return
  if (!window.confirm(`确定删除选中的 ${selectedIds.value.length} 张图片？`)) return
  for (const id of selectedIds.value) {
    await api.delete(`/photos/${id}`)
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

onMounted(() => load())
</script>

