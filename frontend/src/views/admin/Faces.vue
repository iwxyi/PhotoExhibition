<template>
  <div class="min-h-screen bg-gray-900 text-white">
    <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
      <div class="flex items-center justify-between mb-6">
        <div>
          <h1 class="text-2xl font-light">人脸管理</h1>
          <p class="text-sm text-gray-400 mt-1">查看检测到的人脸，显示人物归属</p>
        </div>
        <router-link to="/admin" class="px-4 py-2 bg-gray-700 hover:bg-gray-600 rounded-lg">返回</router-link>
      </div>

      <div class="bg-gray-800 rounded-lg p-4 mb-6">
        <div class="flex flex-wrap gap-4 items-center">
          <input
            v-model="keyword"
            placeholder="搜索姓名或文件名"
            class="px-3 py-2 bg-gray-700 border border-gray-600 rounded text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 w-64"
            @keyup.enter="load"
          />
          <button @click="load" :disabled="loading" class="px-4 py-2 bg-blue-600 hover:bg-blue-700 rounded-lg text-sm disabled:opacity-50">
            {{ loading ? '加载中...' : '搜索' }}
          </button>
          <button @click="resetSearch" class="px-4 py-2 bg-gray-700 hover:bg-gray-600 rounded-lg text-sm">重置</button>
        </div>
      </div>

      <div class="bg-gray-800 rounded-lg p-4">
        <div class="overflow-auto">
          <table class="min-w-full text-sm">
            <thead class="text-left text-gray-400">
              <tr>
                <th class="py-2 pr-4">ID</th>
                <th class="py-2 pr-4">预览</th>
                <th class="py-2 pr-4">文件名</th>
                <th class="py-2 pr-4">人物</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="face in faces" :key="face.id" class="border-t border-gray-700">
                <td class="py-3 pr-4">{{ face.id }}</td>
                <td class="py-3 pr-4">
                  <div
                    class="w-20 h-20 bg-gray-700 rounded overflow-hidden relative cursor-pointer group"
                    @click="face.photoId && openPhoto(face.photoId)"
                    @mouseenter="showPreview(face)"
                    @mousemove="movePreview"
                    @mouseleave="hidePreview"
                  >
                    <img
                      v-if="face.photoThumbnailPath"
                      :src="getImageUrl(face.photoThumbnailPath)"
                      :alt="face.photoFilename"
                      class="absolute"
                      :style="getFaceCropStyle(face)"
                      loading="lazy"
                    />
                    <span v-else class="text-gray-500 text-xs flex items-center justify-center h-full">无缩略图</span>
                  </div>
                </td>
                <td class="py-3 pr-4">
                  <div class="flex flex-col">
                    <span class="truncate max-w-[160px]" :title="face.photoFilename">{{ face.photoFilename || '-' }}</span>
                  </div>
                </td>
                <td class="py-3 pr-4 text-gray-200">
                  {{ face.personName || '未分配' }}
                </td>
              </tr>
              <tr v-if="!faces.length && !loading">
                <td colspan="4" class="py-6 text-center text-gray-400">暂无数据</td>
              </tr>
            </tbody>
          </table>
        </div>

        <div class="flex items-center justify-between mt-4 text-sm text-gray-300">
          <span>第 {{ page + 1 }} / {{ totalPages }} 页</span>
          <div class="flex items-center gap-2">
            <button @click="prev" :disabled="page === 0 || loading" class="px-3 py-1 bg-gray-700 hover:bg-gray-600 rounded disabled:opacity-50">上一页</button>
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
            <button @click="next" :disabled="page >= totalPages - 1 || loading" class="px-3 py-1 bg-gray-700 hover:bg-gray-600 rounded disabled:opacity-50">下一页</button>
          </div>
        </div>
      </div>
    </div>
  </div>
  <transition name="fade">
    <div
      v-if="previewVisible"
      class="preview-float bg-gray-900"
      :style="previewStyle"
    >
      <img :src="previewUrl" alt="预览" class="w-full h-full object-contain" />
    </div>
  </transition>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { api } from '@/api'

interface FaceItem {
  id: number
  photoId?: number
  photoFilename?: string
  photoThumbnailPath?: string
  photoOriginalPath?: string
  x?: number
  y?: number
  width?: number
  height?: number
  personName?: string
}

const faces = ref<FaceItem[]>([])
const loading = ref(false)
const page = ref(0)
const size = ref(10)
const totalPages = ref(1)
const keyword = ref('')
const previewVisible = ref(false)
const previewUrl = ref('')
const previewStyle = ref<{ left: string; top: string }>({ left: '0px', top: '0px' })
const pageNumbers = computed(() => {
  const total = Math.max(totalPages.value, 1)
  const current = page.value
  const span = 2
  let start = Math.max(0, current - span)
  let end = Math.min(total - 1, current + span)
  while (end - start < span * 2 && end < total - 1) end++
  while (end - start < span * 2 && start > 0) start--
  const list = []
  for (let i = start; i <= end; i++) list.push(i)
  return list
})

const load = async () => {
  loading.value = true
  try {
    const params: any = { page: page.value, size: size.value }
    if (keyword.value.trim()) {
      params.keyword = keyword.value.trim()
    }
    const res = await api.get('/admin/faces', { params })
    faces.value = res.data.content || res.data || []
    totalPages.value = res.data.totalPages ?? 1
  } finally {
    loading.value = false
  }
}

const resetSearch = () => {
  keyword.value = ''
  page.value = 0
  load()
}

const prev = () => {
  if (page.value === 0) return
  page.value -= 1
  load()
}

const next = () => {
  if (page.value >= totalPages.value - 1) return
  page.value += 1
  load()
}
const jumpTo = (p: number) => {
  if (p < 0 || p >= totalPages.value || p === page.value) return
  page.value = p
  load()
}

const getImageUrl = (path?: string) => {
  if (!path) return ''
  return path.startsWith('http') ? path : `/api/files${path}`
}

const getFaceCropStyle = (face: FaceItem) => {
  const hasSize = face.width && face.height && face.width > 0 && face.height > 0
  const thumb = face.photoThumbnailPath || face.photoOriginalPath
  if (!thumb || !hasSize) {
    return { position: 'absolute', inset: 0, objectFit: 'cover' }
  }
  const w = Math.min(1, Math.max(0.06, face.width!))
  const h = Math.min(1, Math.max(0.06, face.height!))
  // 限制最大放大倍数，避免超大偏移空白
  const scale = Math.min(2.5, Math.max(1, Math.max(1 / w, 1 / h)))
  const left = clamp((-(face.x || 0) * scale * 100), -120, 120)
  const top = clamp((-(face.y || 0) * scale * 100), -120, 120)
  return {
    position: 'absolute',
    width: `${scale * 100}%`,
    height: `${scale * 100}%`,
    left: `${left}%`,
    top: `${top}%`,
    objectFit: 'cover'
  }
}

const clamp = (v: number, min: number, max: number) => Math.min(max, Math.max(min, v))

const openPhoto = (photoId: number) => {
  window.open(`/photo/${photoId}`, '_blank')
}

const showPreview = (face: FaceItem) => {
  previewUrl.value = getImageUrl(face.photoThumbnailPath || face.photoOriginalPath || '')
  previewVisible.value = !!previewUrl.value
}

const movePreview = (e: MouseEvent) => {
  if (!previewVisible.value) return
  const offset = 16
  // 使用 viewport 坐标，避免滚动后位置偏移
  const x = e.clientX + offset
  const y = e.clientY + offset
  const maxX = window.innerWidth - 240
  const maxY = window.innerHeight - 240
  previewStyle.value = {
    left: `${Math.min(x, maxX)}px`,
    top: `${Math.min(y, maxY)}px`
  }
}

const hidePreview = () => {
  previewVisible.value = false
  previewUrl.value = ''
}

onMounted(() => load())
</script>

<style scoped>
textarea {
  resize: vertical;
}

.preview-float {
  position: fixed;
  z-index: 9999;
  width: 220px;
  height: 220px;
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid rgba(255, 255, 255, 0.15);
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.35);
}
</style>

