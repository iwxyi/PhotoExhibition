<template>
  <div class="min-h-screen admin-shell admin-faces-page">
    <AdminStyleChrome />
    <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-4">
      <div class="admin-faces-hero flex items-center justify-between gap-4 mb-6">
        <div>
          <h1 class="text-2xl font-light admin-page-title">人脸管理</h1>
        </div>
        <router-link to="/admin" class="admin-button-soft admin-page-back-link px-4 py-2 rounded-lg transition-colors">返回</router-link>
      </div>

      <div class="glass-panel admin-faces-panel admin-faces-toolbar p-4 mb-6">
        <div class="flex flex-wrap gap-4 items-center">
          <label class="space-y-2">
            <span class="text-sm text-gray-300">搜索人脸</span>
            <input
              v-model="keyword"
              placeholder="按人物姓名或文件名搜索"
              class="px-3 py-2 bg-gray-700 border border-gray-600 rounded text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 w-64"
              @keyup.enter="load"
            />
          </label>
          <button @click="load" :disabled="loading" class="px-4 py-2 bg-blue-600 hover:bg-blue-700 rounded-lg text-sm disabled:opacity-50">
            {{ loading ? '加载中...' : '搜索' }}
          </button>
          <button @click="resetSearch" class="px-4 py-2 bg-gray-700 hover:bg-gray-600 rounded-lg text-sm">重置</button>
        </div>
      </div>

      <div class="glass-panel admin-faces-panel p-4">
        <div class="overflow-auto">
          <table class="min-w-full text-sm admin-data-table">
            <thead class="text-left text-gray-400">
              <tr>
                <th class="py-2 pr-4">ID</th>
                <th class="py-2 pr-4">预览</th>
                <th class="py-2 pr-4">文件名</th>
                <th class="py-2 pr-4">人物</th>
                <th class="py-2 pr-4">置信度</th>
                <th class="py-2 pr-4">宽×高</th>
                <th class="py-2 pr-4">比例</th>
                <th class="py-2 pr-4">面积</th>
                <th class="py-2 pr-4">坐标</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="face in faces" :key="face.id" class="admin-faces-row border-t border-gray-700">
                <td class="py-3 pr-4">{{ face.id }}</td>
                <td class="py-3 pr-4">
                  <div
                    class="admin-faces-thumb w-20 h-20 bg-gray-700 rounded overflow-hidden relative cursor-pointer group"
                    @click="face.photoId && openPhoto(face.photoId)"
                    @mouseenter="showPreview(face)"
                    @mousemove="movePreview"
                    @mouseleave="hidePreview"
                  >
                    <img
                      v-if="face.photoThumbnailPath"
                      :src="getImageUrl(face)"
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
                <td class="py-3 pr-4 text-gray-200">{{ formatConfidence(face.confidence) }}</td>
                <td class="py-3 pr-4 text-gray-200">
                  {{ formatPercent(face.width) }} × {{ formatPercent(face.height) }}
                </td>
                <td class="py-3 pr-4 text-gray-200">{{ formatRatio(face.width, face.height) }}</td>
                <td class="py-3 pr-4 text-gray-200">{{ formatArea(face.width, face.height) }}</td>
                <td class="py-3 pr-4 text-gray-200">{{ formatPoint(face.x, face.y) }}</td>
              </tr>
              <tr v-if="!faces.length && !loading">
                <td colspan="9" class="py-6 text-center text-gray-400">暂无数据</td>
              </tr>
            </tbody>
          </table>
        </div>

        <div class="admin-faces-pagination flex items-center justify-between mt-4 text-sm text-gray-300">
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
      class="preview-float admin-faces-preview bg-gray-900"
      :style="previewStyle"
      ref="previewContainer"
    >
      <div class="relative w-full h-full">
        <img
          :src="previewUrl"
          alt="预览"
          class="w-full h-full object-contain"
          @load="onPreviewLoad"
        />
        <div
          v-if="previewFace && hasBox(previewFace)"
          class="absolute border-2 border-amber-400/90 rounded-sm pointer-events-none"
          :style="getPreviewBoxStyle(previewFace)"
        />
      </div>
    </div>
  </transition>
</template>

<script setup lang="ts">
import AdminStyleChrome from '@/components/admin/AdminStyleChrome.vue'
import { ref, onMounted, onBeforeUnmount, computed } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '@/api'
import { buildPhotoAssetUrl } from '@/utils/photoUrl'
import { useAuthStore } from '@/stores/auth'
import { buildPublicPath } from '@/utils/publicRoute'

const router = useRouter()
const authStore = useAuthStore()

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
  confidence?: number
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
const previewFace = ref<FaceItem | null>(null)
const previewNatural = ref<{ w: number; h: number }>({ w: 0, h: 0 })
const previewContainer = ref<HTMLElement | null>(null)
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

const getImageUrl = (face?: FaceItem | null) => {
  return buildPhotoAssetUrl({
    id: face?.photoId,
    thumbnailPath: face?.photoThumbnailPath,
    originalPath: face?.photoOriginalPath
  }, 'thumbnail') || ''
}

const formatConfidence = (v?: number) => {
  if (v === undefined || v === null) return '-'
  return `${(v * 100).toFixed(1)}%`
}

const formatPercent = (v?: number) => {
  if (v === undefined || v === null) return '-'
  return `${(v * 100).toFixed(2)}%`
}

const formatRatio = (w?: number, h?: number) => {
  if (!w || !h || h === 0) return '-'
  return w > 0 && h > 0 ? (w / h).toFixed(2) : '-'
}

const formatArea = (w?: number, h?: number) => {
  if (!w || !h) return '-'
  return `${(w * h * 100).toFixed(2)}%`
}

const formatPoint = (x?: number, y?: number) => {
  if (x === undefined || y === undefined || x === null || y === null) return '-'
  return `(${formatPercent(x)}, ${formatPercent(y)})`
}

const getFaceCropStyle = (face: FaceItem) => {
  const hasSize = face.width && face.height && face.width > 0 && face.height > 0
  const thumb = face.photoThumbnailPath || face.photoOriginalPath
  if (!thumb || !hasSize) {
    return { position: 'absolute', inset: 0, objectFit: 'cover', objectPosition: 'center center' }
  }
  // 使用 object-position 居中到人脸中心，避免只显示一角
  const centerX = ((face.x || 0) + face.width / 2) * 100
  const centerY = ((face.y || 0) + face.height / 2) * 100
  return {
    position: 'absolute',
    inset: 0,
    width: '100%',
    height: '100%',
    objectFit: 'cover',
    objectPosition: `${centerX}% ${centerY}%`
  }
}

const clamp = (v: number, min: number, max: number) => Math.min(max, Math.max(min, v))
const hasBox = (face: FaceItem) => {
  return face.x !== undefined && face.y !== undefined && face.width !== undefined && face.height !== undefined
}

const getPreviewBoxStyle = (face: FaceItem) => {
  if (!hasBox(face)) return {}
  const containerW = previewContainer.value?.clientWidth || 220
  const containerH = previewContainer.value?.clientHeight || 220
  const natW = previewNatural.value.w
  const natH = previewNatural.value.h

  // 计算 object-contain 下的真实显示区域
  let dispW = containerW
  let dispH = containerH
  let offsetX = 0
  let offsetY = 0
  if (natW > 0 && natH > 0) {
    const scale = Math.min(containerW / natW, containerH / natH)
    dispW = natW * scale
    dispH = natH * scale
    offsetX = (containerW - dispW) / 2
    offsetY = (containerH - dispH) / 2
  }

  const x = clamp(face.x || 0, 0, 1)
  const y = clamp(face.y || 0, 0, 1)
  const w = clamp(face.width || 0, 0, 1 - x)
  const h = clamp(face.height || 0, 0, 1 - y)
  return {
    left: `${offsetX + x * dispW}px`,
    top: `${offsetY + y * dispH}px`,
    width: `${w * dispW}px`,
    height: `${h * dispH}px`
  }
}

const openPhoto = (photoId: number) => {
  window.open(buildPublicPath(`/photo/${photoId}`, authStore.slug ? `/${authStore.slug}` : undefined), '_blank')
}

const showPreview = (face: FaceItem) => {
  previewUrl.value = getImageUrl(face)
  previewVisible.value = !!previewUrl.value
  previewFace.value = face
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
  previewFace.value = null
}

const onPreviewLoad = (e: Event) => {
  const img = e.target as HTMLImageElement
  previewNatural.value = { w: img.naturalWidth, h: img.naturalHeight }
}

const handleGlobalKeydown = (e: KeyboardEvent) => {
  if (e.key === 'Escape') {
    // 返回首页
    router.push('/admin')
  }
}

onMounted(() => {
  load()
  window.addEventListener('keydown', handleGlobalKeydown)
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', handleGlobalKeydown)
})
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
