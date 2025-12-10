<template>
  <div class="min-h-screen bg-gray-900 text-white">
    <div class="max-w-5xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
      <div class="flex items-center justify-between mb-6">
        <h1 class="text-2xl font-light">标签管理</h1>
        <div class="space-x-3">
          <button @click="load" :disabled="loading" class="px-4 py-2 bg-blue-600 hover:bg-blue-700 rounded-lg disabled:opacity-50">刷新</button>
          <router-link to="/admin" class="px-4 py-2 bg-gray-700 hover:bg-gray-600 rounded-lg">返回</router-link>
        </div>
      </div>

      <div class="bg-gray-800 rounded-lg p-4">
        <div class="flex flex-wrap gap-4 mb-4">
          <input v-model="keyword" placeholder="搜索标签" class="px-3 py-2 bg-gray-700 border border-gray-600 rounded w-64 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
          <button @click="load" :disabled="loading" class="px-4 py-2 bg-gray-700 hover:bg-gray-600 rounded-lg text-sm disabled:opacity-50">查询</button>
          <button @click="editSelected" :disabled="selectedIds.length !== 1 || loading" class="px-4 py-2 bg-amber-600 hover:bg-amber-700 rounded-lg text-sm disabled:opacity-50">编辑</button>
          <button @click="deleteSelected" :disabled="selectedIds.length === 0 || loading" class="px-4 py-2 bg-red-600 hover:bg-red-700 rounded-lg text-sm disabled:opacity-50">删除</button>
        </div>

        <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
          <div
            v-for="t in pagedTags"
            :key="t.id"
            class="bg-gray-700/60 rounded-lg p-3 flex items-center justify-between"
          >
            <div class="flex items-center gap-2">
              <input type="checkbox" v-model="selectedIds" :value="t.id" class="accent-blue-500" />
              <div>
                <div class="text-sm font-semibold">{{ t.name }}</div>
                <div class="text-xs text-gray-300">ID: {{ t.id }}</div>
              </div>
            </div>
            <div class="flex items-center gap-2">
              <div
                v-if="t.color"
                class="w-6 h-6 rounded border border-white/20"
                :style="{ background: t.color }"
              ></div>
              <button class="text-xs text-amber-300 hover:text-amber-200" @click="editOne(t)">编辑</button>
              <button class="text-xs text-red-300 hover:text-red-200" @click="deleteOne(t.id)">删</button>
            </div>
          </div>
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

const tags = ref<any[]>([])
const loading = ref(false)
const keyword = ref('')
const page = ref(0)
const size = ref(24)
const total = ref(0)
const selectedIds = ref<number[]>([])

const load = async () => {
  loading.value = true
  try {
    const res = await api.get('/tags')
    const list = res.data || []
    tags.value = list
    total.value = list.length
  } finally {
    loading.value = false
  }
}

const filteredTags = computed(() => {
  if (!keyword.value.trim()) return tags.value
  const kw = keyword.value.trim().toLowerCase()
  return tags.value.filter((t: any) => (t.name || '').toLowerCase().includes(kw))
})

const pagedTags = computed(() => {
  const start = page.value * size.value
  return filteredTags.value.slice(start, start + size.value)
})

const totalPages = computed(() => Math.max(1, Math.ceil(filteredTags.value.length / size.value)))

const prev = () => {
  if (page.value === 0) return
  page.value--
}
const next = () => {
  if (page.value >= totalPages.value - 1) return
  page.value++
}

const editOne = async (t: any) => {
  const newName = window.prompt('修改标签名称', t.name)
  if (newName === null || newName.trim() === '') return
  const newColor = window.prompt('修改颜色(可选)', t.color || '')
  await api.put(`/tags/${t.id}`, { name: newName.trim(), color: newColor || null })
  await load()
}

const editSelected = async () => {
  if (selectedIds.value.length !== 1) return
  const t = tags.value.find((x: any) => x.id === selectedIds.value[0])
  if (t) {
    await editOne(t)
  }
}

const deleteOne = async (id: number) => {
  if (!window.confirm('确定删除该标签？将解除关联。')) return
  await api.delete(`/tags/${id}`)
  selectedIds.value = selectedIds.value.filter(x => x !== id)
  await load()
}

const deleteSelected = async () => {
  if (selectedIds.value.length === 0) return
  if (!window.confirm(`确定删除选中的 ${selectedIds.value.length} 个标签？`)) return
  for (const id of selectedIds.value) {
    await api.delete(`/tags/${id}`)
  }
  selectedIds.value = []
  await load()
}

onMounted(() => load())
</script>

