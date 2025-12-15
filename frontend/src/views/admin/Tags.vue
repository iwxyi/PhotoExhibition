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
          <button @click="deleteSelected" :disabled="selectedIds.length === 0 || loading" class="px-4 py-2 bg-red-600 hover:bg-red-700 rounded-lg text-sm disabled:opacity-50">删除</button>
        </div>

        <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
          <div
            v-for="t in pagedTags"
            :key="t.id"
            class="bg-gray-700/60 rounded-lg p-3 flex items-center justify-between cursor-pointer hover:bg-gray-700"
            @click="openTag(t)"
          >
            <div class="flex items-center gap-2">
              <input type="checkbox" v-model="selectedIds" :value="t.id" class="accent-blue-500" @click.stop />
              <div>
                <div class="text-sm font-semibold flex items-center gap-2">
                  <span>{{ t.name }}</span>
                  <span class="text-xs text-gray-300">({{ t.photoCount ?? 0 }})</span>
                </div>
                <div class="text-xs text-gray-300">ID: {{ t.id }}</div>
              </div>
            </div>
            <div class="flex items-center gap-2">
              <div
                v-if="t.color"
                class="w-6 h-6 rounded border border-white/20"
                :style="{ background: t.color }"
              ></div>
              <button class="text-xs text-amber-300 hover:text-amber-200" @click.stop="editOne(t)">编辑</button>
            </div>
          </div>
        </div>

        <div class="flex items-center justify-between mt-4 text-sm text-gray-300">
          <span>第 {{ page + 1 }} / {{ totalPages }} 页</span>
          <div class="flex items-center gap-2">
            <button @click="prev" :disabled="page===0 || loading" class="px-3 py-1 bg-gray-700 hover:bg-gray-600 rounded disabled:opacity-40">上一页</button>
            <div class="flex items-center gap-1">
              <button
                v-for="pnum in pageNumbers"
                :key="pnum"
                @click="jumpTo(pnum)"
                :disabled="loading"
                class="px-3 py-1 rounded border border-gray-700"
                :class="pnum === page ? 'bg-blue-600 border-blue-500' : 'bg-gray-700 hover:bg-gray-600'"
              >
                {{ pnum + 1 }}
              </button>
            </div>
            <button @click="next" :disabled="page>=totalPages-1 || loading" class="px-3 py-1 bg-gray-700 hover:bg-gray-600 rounded disabled:opacity-40">下一页</button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '@/api'

const tags = ref<any[]>([])
const loading = ref(false)
const keyword = ref('')
const page = ref(0)
const size = ref(24)
const total = ref(0)
const selectedIds = ref<number[]>([])
const router = useRouter()
const PAGE_STORAGE_KEY = 'admin_tags_page'
const pageNumbers = computed(() => {
  const totalPage = totalPages.value
  const current = page.value
  const span = 2
  let start = Math.max(0, current - span)
  let end = Math.min(totalPage - 1, current + span)
  while (end - start < span * 2 && end < totalPage - 1) end++
  while (end - start < span * 2 && start > 0) start--
  const list = []
  for (let i = start; i <= end; i++) list.push(i)
  return list
})

const load = async () => {
  loading.value = true
  try {
    const res = await api.get('/tags')
    const list = res.data || []
    tags.value = list
    total.value = list.length
    // 如果当前页超出范围，回退到最后一页
    const maxPage = Math.max(0, Math.ceil(filteredTags.value.length / size.value) - 1)
    if (page.value > maxPage) page.value = maxPage
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

const jumpTo = (p: number) => {
  if (p < 0 || p >= totalPages.value || p === page.value) return
  page.value = p
}

const editOne = async (t: any) => {
  const newName = window.prompt('修改标签名称', t.name)
  if (newName === null || newName.trim() === '') return
  const newColor = window.prompt('修改颜色(可选)', t.color || '')
  await api.put(`/tags/${t.id}`, { name: newName.trim(), color: newColor || null })
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

const openTag = (tag: any) => {
  if (!tag?.id) return
  router.push({ path: '/wall', query: { tagId: tag.id, tagName: tag.name } })
}

// 分页持久化（会话级）
const restorePage = () => {
  const saved = sessionStorage.getItem(PAGE_STORAGE_KEY)
  if (saved !== null) {
    const num = parseInt(saved, 10)
    if (!Number.isNaN(num) && num >= 0) page.value = num
  }
}

watch(page, val => {
  sessionStorage.setItem(PAGE_STORAGE_KEY, String(val))
})

onMounted(() => {
  restorePage()
  load()
})
</script>

