<template>
  <div class="min-h-screen admin-shell text-white">
    <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
      <div class="flex items-center justify-between mb-6">
        <h1 class="text-2xl font-light">图片管理</h1>
        <div class="space-x-3">
          <button @click="load" :disabled="loading" class="btn-primary disabled:opacity-50">刷新</button>
          <router-link to="/admin" class="px-4 py-2 bg-gray-900/70 hover:bg-gray-700 rounded-lg border border-white/10 transition-colors">返回</router-link>
        </div>
      </div>

      <div class="glass-panel p-4">
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
                <th class="py-2 pr-4">缩略图</th>
                <th class="py-2 pr-4">文件名</th>
                <th class="py-2 pr-4">尺寸</th>
                <th class="py-2 pr-4">格式</th>
                <th class="py-2 pr-4">拍摄时间</th>
                <th class="py-2 pr-4">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="p in photos" :key="p.id" class="border-t border-gray-700 hover:bg-gray-700/60">
                <td class="py-2 pr-4">
                  <input type="checkbox" class="accent-blue-500" v-model="selectedIds" :value="p.id" />
                </td>
                <td class="py-2 pr-4">{{ p.id }}</td>
                <td class="py-2 pr-4">
                  <div
                    class="w-16 h-16 bg-gray-700 rounded overflow-hidden border border-gray-600 flex items-center justify-center cursor-pointer"
                    @click="openPhoto(p.id)"
                    @mouseenter="showPreview(p)"
                    @mousemove="movePreview"
                    @mouseleave="hidePreview"
                  >
                    <img
                      v-if="p.thumbnailPath || p.webpPath || p.originalPath"
                      :src="getThumbUrl(p)"
                      :alt="p.filename"
                      class="w-full h-full object-cover"
                      loading="lazy"
                    />
                    <span v-else class="text-xs text-gray-500">无图</span>
                  </div>
                </td>
                <td class="py-2 pr-4 whitespace-nowrap">
                  <div class="flex flex-col gap-1">
                    <span>{{ p.filename }}</span>
                    <div class="flex flex-wrap gap-1">
                      <span
                        v-for="t in p.tags || []"
                        :key="t.id"
                        class="px-2 py-0.5 rounded-full text-xs cursor-pointer"
                        :style="{ backgroundColor: t.color || 'rgba(59,130,246,0.1)', color: t.color ? '#fff' : '#93c5fd' }"
                        @click.stop="openTag(t)"
                      >
                        {{ t.name }}
                      </span>
                    </div>
                  </div>
                </td>
                <td class="py-2 pr-4">{{ p.width }} x {{ p.height }}</td>
                <td class="py-2 pr-4">{{ p.format }}</td>
                <td class="py-2 pr-4 whitespace-nowrap">{{ formatDate(p.takenAt) }}</td>
                <td class="py-2 pr-4 space-x-2">
                  <button @click="openFaceDialog(p)" class="px-3 py-1 bg-gray-700 hover:bg-gray-600 rounded text-xs">人脸</button>
                </td>
              </tr>
            </tbody>
          </table>
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
    <!-- 人脸标注弹窗 -->
    <div v-if="showFaceDialog" class="fixed inset-0 bg-black/60 flex items-center justify-center z-50" @click.self="closeFaceDialog">
      <div class="glass-panel w-full max-w-3xl p-6 max-h-[80vh] overflow-y-auto">
        <div class="flex items-center justify-between mb-4">
          <div>
            <h3 class="text-lg font-light">人脸标注 - {{ activePhoto?.filename }}</h3>
            <p class="text-sm text-gray-400">可为检测到的人脸设置姓名和说明</p>
          </div>
          <button @click="closeFaceDialog" class="px-3 py-1 bg-gray-700 hover:bg-gray-600 rounded text-sm">关闭</button>
        </div>

        <div v-if="faceLoading" class="text-gray-400">加载中...</div>
        <div v-else-if="!faces.length" class="text-gray-400">未检测到人脸</div>
        <div v-else class="space-y-4">
          <div v-for="face in faces" :key="face.id" class="border border-gray-700 rounded-lg p-4">
            <div class="flex items-start gap-4">
              <!-- 圆形人脸照片 - 更大尺寸，针对人脸居中放大裁切 -->
              <div class="flex-shrink-0">
                <div
                  v-if="getFaceImageUrl(face)"
                  class="w-28 h-28 rounded-full bg-gray-700 border border-gray-600 overflow-hidden relative"
                  :style="getFaceCropStyle(face)"
                >
                </div>
                <div
                  v-else
                  class="w-28 h-28 rounded-full bg-gray-700 border border-gray-600 flex items-center justify-center text-gray-500 text-xs"
                >
                  无图
                </div>
              </div>
              <!-- 名字和说明输入框 -->
              <div class="flex-1 min-w-0 flex flex-col gap-3">
                <input
                  v-model="face.personName"
                  placeholder="输入姓名，留空则移除关联"
                  class="w-full px-3 py-2 bg-gray-700 border border-gray-600 rounded focus:outline-none focus:ring-2 focus:ring-blue-500 text-sm"
                />
                <textarea
                  v-model="face.personDescription"
                  rows="2"
                  placeholder="备注（例如：家庭成员、朋友、客户等）"
                  class="w-full px-3 py-2 bg-gray-700 border border-gray-600 rounded focus:outline-none focus:ring-2 focus:ring-blue-500 text-sm"
                ></textarea>
                <!-- 位置、置信度信息 -->
                <div class="text-xs text-gray-500">
                  位置：X {{ formatPercent(face.x) }} / Y {{ formatPercent(face.y) }} / 宽 {{ formatPercent(face.width) }} / 高 {{ formatPercent(face.height) }}
                  <span class="ml-2">置信度：{{ (face.confidence * 100).toFixed(0) }}%</span>
                </div>
                <div class="text-right">
                  <button @click="saveFace(face)" :disabled="savingFaceId===face.id" class="px-3 py-1 bg-blue-600 hover:bg-blue-700 rounded text-sm disabled:opacity-50">
                    {{ savingFaceId===face.id ? '保存中...' : '保存' }}
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>
        <div class="mt-4 flex items-center justify-between">
          <button
            class="px-3 py-1 bg-amber-600 hover:bg-amber-700 rounded text-sm disabled:opacity-50"
            :disabled="rescanLoading"
            @click="rescanFaces"
          >
            {{ rescanLoading ? '重建中...' : '重建人脸' }}
          </button>
          <span class="text-xs text-gray-400" v-if="rescanMessage">{{ rescanMessage }}</span>
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
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, computed } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '@/api'

const router = useRouter()

const photos = ref<any[]>([])
const loading = ref(false)
const page = ref(0)
const size = ref(20)
const totalPages = ref(1)
const keyword = ref('')
const selectedIds = ref<number[]>([])
const showFaceDialog = ref(false)
const faces = ref<any[]>([])
const faceLoading = ref(false)
const savingFaceId = ref<number | null>(null)
const activePhoto = ref<any | null>(null)
const rescanLoading = ref(false)
const rescanMessage = ref('')
const previewVisible = ref(false)
const previewUrl = ref('')
const previewStyle = ref<{ left: string; top: string }>({ left: '0px', top: '0px' })
const pageNumbers = computed(() => {
  const total = Math.max(totalPages.value, 1)
  const current = page.value
  const span = 2 // 前后各2页
  let start = Math.max(0, current - span)
  let end = Math.min(total - 1, current + span)
  // 保证固定长度（最多5个），必要时向两侧补齐
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

const getThumbUrl = (p: any) => {
  if (p.thumbnailPath) return `/api/files${p.thumbnailPath}`
  if (p.webpPath) return `/api/files${p.webpPath}`
  if (p.originalPath) return `/api/files${p.originalPath}`
  return ''
}

const formatPercent = (val?: number) => {
  if (val === undefined || val === null) return '-'
  return `${(val * 100).toFixed(0)}%`
}

const getFaceImageUrl = (face: any) => {
  const paths = [
    face.photoThumbnailPath,
    face.photoOriginalPath,
    activePhoto.value?.thumbnailPath,
    activePhoto.value?.webpPath,
    activePhoto.value?.originalPath
  ]
  const firstPath = paths.find(p => p && typeof p === 'string' && p.length > 0)
  if (!firstPath) return ''
  const base = firstPath.startsWith('/api/files') ? firstPath : `/api/files${firstPath}`
  return encodeURI(base)
}

const getFaceCropStyle = (face: any) => {
  const imageUrl = getFaceImageUrl(face)
  if (!imageUrl) {
    return {}
  }
  
  const hasSize = face.width && face.height && face.width > 0 && face.height > 0
  if (!hasSize) {
    return {
      backgroundImage: `url(${imageUrl})`,
      backgroundSize: 'cover',
      backgroundPosition: 'center center'
    }
  }
  
  // 计算人脸中心位置（归一化坐标 0-1 转换为百分比）
  const centerX = ((face.x || 0) + (face.width || 0) / 2) * 100
  const centerY = ((face.y || 0) + (face.height || 0) / 2) * 100
  
  // 计算缩放比例：让人脸区域在圆形头像中占据更大空间，确保脸部清晰
  // 人脸区域越小，缩放越大；最小2倍，最大3倍
  const faceArea = (face.width || 0) * (face.height || 0)
  const scale = Math.min(3, Math.max(2, 0.3 / Math.max(faceArea, 0.05)))
  
  return {
    backgroundImage: `url(${imageUrl})`,
    backgroundSize: `${scale * 100}%`,
    backgroundPosition: `${centerX}% ${centerY}%`,
    backgroundRepeat: 'no-repeat'
  }
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

const openPhoto = (photoId: number) => {
  window.open(`/photo/${photoId}`, '_blank')
}

const openFaceDialog = async (photo: any) => {
  activePhoto.value = photo
  showFaceDialog.value = true
  await loadFaces(photo.id)
}

const closeFaceDialog = () => {
  showFaceDialog.value = false
  faces.value = []
  activePhoto.value = null
}

const loadFaces = async (photoId: number) => {
  faceLoading.value = true
  rescanMessage.value = ''
  try {
    const res = await api.get(`/admin/photos/${photoId}/faces`)
    faces.value = res.data || []
  } catch (e) {
    console.error('加载人脸失败', e)
  } finally {
    faceLoading.value = false
  }
}

const saveFace = async (face: any) => {
  if (!face.id) return
  savingFaceId.value = face.id
  try {
    await api.put(`/admin/faces/${face.id}`, {
      name: face.personName || '',
      description: face.personDescription || ''
    })
    if (activePhoto.value?.id) {
      await loadFaces(activePhoto.value.id)
    }
  } catch (e) {
    alert('保存失败')
  } finally {
    savingFaceId.value = null
  }
}

const rescanFaces = async () => {
  if (!activePhoto.value?.id) return
  rescanLoading.value = true
  rescanMessage.value = ''
  try {
    const res = await api.post(`/admin/photos/${activePhoto.value.id}/rescan-faces`)
    rescanMessage.value = res.data?.message || '重建完成'
    await loadFaces(activePhoto.value.id)
  } catch (e: any) {
    rescanMessage.value = e?.response?.data?.error || e?.message || '重建失败'
  } finally {
    rescanLoading.value = false
  }
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
const openTag = (tag: any) => {
  if (!tag?.id) return
  const route = router.resolve({ path: '/wall', query: { tagId: tag.id, tagName: tag.name } })
  window.open(route.href, '_blank')
}

const showPreview = (p: any) => {
  previewUrl.value = getThumbUrl(p)
  previewVisible.value = !!previewUrl.value
}

const movePreview = (e: MouseEvent) => {
  if (!previewVisible.value) return
  const offset = 16
  const x = e.clientX + offset
  const y = e.clientY + offset
  const maxX = window.innerWidth - 220
  const maxY = window.innerHeight - 220
  previewStyle.value = {
    left: `${Math.min(x, maxX)}px`,
    top: `${Math.min(y, maxY)}px`
  }
}

const hidePreview = () => {
  previewVisible.value = false
  previewUrl.value = ''
}
const jumpTo = (p: number) => {
  if (p < 0 || p >= totalPages.value || p === page.value) return
  page.value = p
  load()
}

const handleGlobalKeydown = (e: KeyboardEvent) => {
  if (e.key === 'Escape') {
    // 如果人脸弹窗打开，先关闭弹窗
    if (showFaceDialog.value) {
      showFaceDialog.value = false
      return
    }
    // 否则返回首页
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
