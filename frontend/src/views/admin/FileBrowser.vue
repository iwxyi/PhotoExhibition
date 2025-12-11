<template>
  <div class="min-h-screen bg-gray-900 text-white">
    <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
      <!-- 头部 -->
      <div class="flex items-center justify-between mb-6">
        <h1 class="text-2xl font-light">文件浏览器</h1>
        <router-link to="/admin" class="px-4 py-2 bg-gray-700 hover:bg-gray-600 rounded-lg">返回</router-link>
      </div>

      <!-- 路径导航栏 -->
      <div class="bg-gray-800 rounded-lg p-4 mb-4">
        <div class="flex items-center gap-2 flex-wrap">
          <button
            @click="goToPath(basePath)"
            class="px-3 py-1 bg-gray-700 hover:bg-gray-600 rounded text-sm"
          >
            根目录
          </button>
          <span class="text-gray-500">/</span>
          <div class="flex items-center gap-2 flex-wrap">
            <button
              v-for="(part, index) in pathParts"
              :key="index"
              @click="navigateToPart(index)"
              class="px-2 py-1 hover:bg-gray-700 rounded text-sm"
            >
              {{ part }}
            </button>
          </div>
          <button
            v-if="currentPath !== basePath"
            @click="goToParent"
            class="ml-auto px-3 py-1 bg-blue-600 hover:bg-blue-700 rounded text-sm"
          >
            返回上级
          </button>
        </div>
        <div class="mt-2 text-sm text-gray-400">
          当前路径: {{ currentPath }}
        </div>
      </div>

      <!-- 工具栏 -->
      <div class="bg-gray-800 rounded-lg p-4 mb-4 flex items-center gap-2 flex-wrap">
        <button
          @click="showCreateDialog = true"
          class="px-4 py-2 bg-green-600 hover:bg-green-700 rounded-lg text-sm"
        >
          + 新建文件夹
        </button>
        <button
          @click="refresh"
          :disabled="loading"
          class="px-4 py-2 bg-gray-700 hover:bg-gray-600 rounded-lg text-sm disabled:opacity-50"
        >
          {{ loading ? '加载中...' : '刷新' }}
        </button>
      </div>

      <!-- 文件列表 -->
      <div class="bg-gray-800 rounded-lg p-4">
        <div v-if="loading" class="text-center py-8 text-gray-400">
          加载中...
        </div>
        <div v-else-if="error" class="text-center py-8 text-red-400">
          {{ error }}
        </div>
        <div v-else-if="!items.length" class="text-center py-8 text-gray-400">
          当前目录为空
        </div>
        <div v-else class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4">
          <!-- 文件夹 -->
          <div
            v-for="dir in directories"
            :key="dir.path"
            @click="goToPath(dir.path)"
            @contextmenu.prevent="showContextMenu($event, dir)"
            class="bg-gray-700 hover:bg-gray-600 rounded-lg p-4 cursor-pointer transition-colors relative"
            :class="{ 'ring-2 ring-blue-500': selectedItem?.path === dir.path }"
          >
            <!-- 三合一封面 -->
            <div v-if="dir.leftVertical || dir.rightTop || dir.rightBottom" class="grid grid-cols-2 gap-0.5 mb-3 h-32 rounded-lg overflow-hidden">
              <!-- 左侧竖图 -->
              <div class="row-span-2 overflow-hidden">
                <img
                  v-if="dir.leftVertical"
                  :src="getImageUrl(dir.leftVertical)"
                  :alt="dir.name"
                  class="w-full h-full object-cover"
                  loading="lazy"
                />
                <div v-else class="w-full h-full bg-gray-600"></div>
              </div>
              <!-- 右侧上方横图 -->
              <div class="overflow-hidden">
                <img
                  v-if="dir.rightTop"
                  :src="getImageUrl(dir.rightTop)"
                  :alt="dir.name"
                  class="w-full h-full object-cover"
                  loading="lazy"
                />
                <div v-else class="w-full h-full bg-gray-600"></div>
              </div>
              <!-- 右侧下方横图 -->
              <div class="overflow-hidden relative">
                <img
                  v-if="dir.rightBottom"
                  :src="getImageUrl(dir.rightBottom)"
                  :alt="dir.name"
                  class="w-full h-full object-cover"
                  loading="lazy"
                />
                <div v-else class="w-full h-full bg-gray-600"></div>
                <!-- 右下角显示照片数量 -->
                <div
                  v-if="dir.photoCount && dir.photoCount > 0"
                  class="absolute inset-0 bg-black/40 text-white flex items-center justify-center text-xs font-semibold"
                >
                  共 {{ dir.photoCount }} 张
                </div>
              </div>
            </div>
            <!-- 无封面时显示默认图标 -->
            <div v-else class="mb-3 flex items-center justify-center h-32 bg-gray-600 rounded-lg">
              <div class="text-4xl">📁</div>
            </div>
            <div class="flex-1 min-w-0">
              <div class="font-medium truncate" :title="dir.name">{{ dir.name }}</div>
              <div class="text-xs text-gray-400 mt-1">
                {{ dir.photoCount ? `${dir.photoCount} 张照片` : '文件夹' }}
              </div>
            </div>
          </div>

          <!-- 文件 -->
          <div
            v-for="file in files"
            :key="file.path"
            @click="openFile(file)"
            @contextmenu.prevent="showContextMenu($event, file)"
            class="bg-gray-700 hover:bg-gray-600 rounded-lg p-4 cursor-pointer transition-colors relative"
            :class="{ 'ring-2 ring-blue-500': selectedItem?.path === file.path }"
          >
            <!-- 图片文件显示缩略图 -->
            <div v-if="file.thumbnail" class="mb-3 h-32 rounded-lg overflow-hidden">
              <img
                :src="getImageUrl(file.thumbnail)"
                :alt="file.name"
                class="w-full h-full object-cover"
                loading="lazy"
              />
            </div>
            <!-- 非图片文件显示默认图标 -->
            <div v-else class="mb-3 flex items-center justify-center h-32 bg-gray-600 rounded-lg">
              <div class="text-4xl">📄</div>
            </div>
            <div class="flex-1 min-w-0">
              <div class="font-medium truncate" :title="file.name">{{ file.name }}</div>
              <div class="text-xs text-gray-400 mt-1">
                {{ formatFileSize(file.size) }}
              </div>
              <div class="text-xs text-gray-500 mt-1">
                {{ formatDate(file.lastModified) }}
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 右键菜单 -->
    <div
      v-if="contextMenu.show"
      :style="{ left: contextMenu.x + 'px', top: contextMenu.y + 'px' }"
      class="fixed bg-gray-800 border border-gray-700 rounded-lg shadow-lg z-50 min-w-[150px]"
    >
      <button
        v-if="contextMenu.item?.isDirectory"
        @click="goToPath(contextMenu.item.path)"
        class="w-full text-left px-4 py-2 hover:bg-gray-700 rounded-t-lg"
      >
        打开
      </button>
      <button
        @click="startRename"
        class="w-full text-left px-4 py-2 hover:bg-gray-700"
      >
        重命名
      </button>
      <button
        @click="confirmDelete"
        class="w-full text-left px-4 py-2 hover:bg-red-600 rounded-b-lg text-red-300"
      >
        删除
      </button>
    </div>

    <!-- 创建文件夹对话框 -->
    <div
      v-if="showCreateDialog"
      class="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50"
      @click.self="showCreateDialog = false"
    >
      <div class="bg-gray-800 rounded-lg p-6 w-full max-w-md">
        <h2 class="text-xl font-light mb-4">新建文件夹</h2>
        <input
          v-model="newFolderName"
          @keyup.enter="createFolder"
          placeholder="文件夹名称"
          class="w-full px-4 py-2 bg-gray-700 border border-gray-600 rounded-lg mb-4 focus:outline-none focus:ring-2 focus:ring-blue-500"
          autofocus
        />
        <div class="flex gap-2 justify-end">
          <button
            @click="showCreateDialog = false"
            class="px-4 py-2 bg-gray-700 hover:bg-gray-600 rounded-lg"
          >
            取消
          </button>
          <button
            @click="createFolder"
            :disabled="!newFolderName.trim() || creating"
            class="px-4 py-2 bg-blue-600 hover:bg-blue-700 rounded-lg disabled:opacity-50"
          >
            {{ creating ? '创建中...' : '创建' }}
          </button>
        </div>
      </div>
    </div>

    <!-- 重命名对话框 -->
    <div
      v-if="showRenameDialog"
      class="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50"
      @click.self="showRenameDialog = false"
    >
      <div class="bg-gray-800 rounded-lg p-6 w-full max-w-md">
        <h2 class="text-xl font-light mb-4">重命名</h2>
        <input
          v-model="renameValue"
          @keyup.enter="renameItem"
          placeholder="新名称"
          class="w-full px-4 py-2 bg-gray-700 border border-gray-600 rounded-lg mb-4 focus:outline-none focus:ring-2 focus:ring-blue-500"
          autofocus
        />
        <div class="flex gap-2 justify-end">
          <button
            @click="showRenameDialog = false"
            class="px-4 py-2 bg-gray-700 hover:bg-gray-600 rounded-lg"
          >
            取消
          </button>
          <button
            @click="renameItem"
            :disabled="!renameValue.trim() || renaming"
            class="px-4 py-2 bg-blue-600 hover:bg-blue-700 rounded-lg disabled:opacity-50"
          >
            {{ renaming ? '重命名中...' : '确定' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { api } from '@/api'

interface PhotoInfo {
  id?: number
  originalPath?: string
  thumbnailPath?: string
  webpPath?: string
  width?: number
  height?: number
}

interface FileItem {
  name: string
  path: string
  isDirectory?: boolean
  size?: number
  lastModified?: number
  photoCount?: number
  leftVertical?: PhotoInfo
  rightTop?: PhotoInfo
  rightBottom?: PhotoInfo
  thumbnail?: PhotoInfo
}

const basePath = ref('')
const currentPath = ref('')
const parentPath = ref<string | null>(null)
const loading = ref(false)
const error = ref('')
const items = ref<FileItem[]>([])
const selectedItem = ref<FileItem | null>(null)

const showCreateDialog = ref(false)
const newFolderName = ref('')
const creating = ref(false)

const showRenameDialog = ref(false)
const renameValue = ref('')
const renaming = ref(false)
const itemToRename = ref<FileItem | null>(null)

const contextMenu = ref({
  show: false,
  x: 0,
  y: 0,
  item: null as FileItem | null
})

const directories = computed(() => items.value.filter(item => item.isDirectory))
const files = computed(() => items.value.filter(item => !item.isDirectory))

const pathParts = computed(() => {
  if (!currentPath.value || currentPath.value === basePath.value) return []
  const relative = currentPath.value.replace(basePath.value, '').replace(/^[\/\\]+/, '')
  return relative.split(/[\/\\]+/).filter(p => p)
})

const loadBasePath = async () => {
  try {
    const res = await api.get('/admin/folders/base-path')
    basePath.value = res.data?.basePath || ''
    if (basePath.value && !currentPath.value) {
      currentPath.value = basePath.value
    }
  } catch (e: any) {
    console.error('加载基础路径失败:', e)
  }
}

const loadFiles = async (path?: string) => {
  loading.value = true
  error.value = ''
  try {
    const res = await api.get('/admin/folders/browser/list', {
      params: { path: path || currentPath.value }
    })
    const data = res.data
    currentPath.value = data.path || currentPath.value
    parentPath.value = data.parent || null
    
    const dirs = (data.directories || []).map((d: any) => ({
      name: d.name,
      path: d.path,
      isDirectory: true,
      photoCount: d.photoCount || 0,
      leftVertical: d.leftVertical,
      rightTop: d.rightTop,
      rightBottom: d.rightBottom
    }))
    const files = (data.files || []).map((f: any) => ({
      name: f.name,
      path: f.path,
      isDirectory: false,
      size: f.size,
      lastModified: f.lastModified,
      thumbnail: f.thumbnail
    }))
    
    items.value = [...dirs, ...files]
  } catch (e: any) {
    error.value = e.response?.data?.error || e.message || '加载失败'
    items.value = []
  } finally {
    loading.value = false
  }
}

const goToPath = (path: string) => {
  currentPath.value = path
  loadFiles(path)
}

const goToParent = () => {
  if (!currentPath.value || currentPath.value === basePath.value) return
  if (parentPath.value) {
    goToPath(parentPath.value)
  } else {
    // 手动计算父路径
    const separator = currentPath.value.includes('\\') ? '\\' : '/'
    const parts = currentPath.value.split(/[\/\\]+/).filter(p => p)
    if (parts.length <= 1) {
      goToPath(basePath.value)
      return
    }
    const parent = parts.slice(0, -1).join(separator)
    const computedParent = currentPath.value.startsWith('/') 
      ? '/' + parent 
      : (currentPath.value.match(/^[A-Z]:/) ? parts[0] + separator + parts.slice(1, -1).join(separator) : parent)
    goToPath(computedParent || basePath.value)
  }
}

const navigateToPart = (index: number) => {
  const parts = pathParts.value.slice(0, index + 1)
  const separator = basePath.value.includes('\\') ? '\\' : '/'
  const path = basePath.value + separator + parts.join(separator)
  goToPath(path)
}

const refresh = () => {
  loadFiles()
}

const createFolder = async () => {
  if (!newFolderName.value.trim()) return
  creating.value = true
  try {
    await api.post('/admin/folders/browser/create', null, {
      params: {
        path: currentPath.value,
        name: newFolderName.value.trim()
      }
    })
    showCreateDialog.value = false
    newFolderName.value = ''
    await loadFiles()
  } catch (e: any) {
    alert('创建失败: ' + (e.response?.data?.error || e.message))
  } finally {
    creating.value = false
  }
}

const showContextMenu = (event: MouseEvent, item: FileItem) => {
  contextMenu.value = {
    show: true,
    x: event.clientX,
    y: event.clientY,
    item
  }
  selectedItem.value = item
}

const startRename = () => {
  if (!contextMenu.value.item) return
  itemToRename.value = contextMenu.value.item
  renameValue.value = contextMenu.value.item.name
  showRenameDialog.value = true
  contextMenu.value.show = false
}

const renameItem = async () => {
  if (!itemToRename.value || !renameValue.value.trim()) return
  renaming.value = true
  try {
    await api.post('/admin/folders/browser/rename', null, {
      params: {
        path: itemToRename.value.path,
        newName: renameValue.value.trim()
      }
    })
    showRenameDialog.value = false
    itemToRename.value = null
    renameValue.value = ''
    await loadFiles()
  } catch (e: any) {
    alert('重命名失败: ' + (e.response?.data?.error || e.message))
  } finally {
    renaming.value = false
  }
}

const confirmDelete = () => {
  if (!contextMenu.value.item) return
  const item = contextMenu.value.item
  const itemType = item.isDirectory ? '文件夹' : '文件'
  if (!confirm(`确认删除${itemType} "${item.name}"？此操作不可撤销。`)) {
    contextMenu.value.show = false
    return
  }
  deleteItem(item)
}

const deleteItem = async (item: FileItem) => {
  contextMenu.value.show = false
  try {
    await api.delete('/admin/folders/browser/delete', {
      params: { path: item.path }
    })
    await loadFiles()
  } catch (e: any) {
    alert('删除失败: ' + (e.response?.data?.error || e.message))
  }
}

const openFile = (file: FileItem) => {
  // 如果是图片并且有对应的 Photo 记录，跳转到图片详情
  if (file.thumbnail && file.thumbnail.id) {
    window.open(`/photo/${file.thumbnail.id}`, '_blank')
    return
  }
  // 尝试直接打开文件
  const url = getImageUrl(file.thumbnail || { originalPath: file.path })
  if (url) {
    window.open(url, '_blank')
  }
}

const handleClickOutside = () => {
  contextMenu.value.show = false
  selectedItem.value = null
}

const formatFileSize = (bytes?: number) => {
  if (!bytes) return '-'
  const units = ['B', 'KB', 'MB', 'GB']
  let size = bytes
  let unitIndex = 0
  while (size >= 1024 && unitIndex < units.length - 1) {
    size /= 1024
    unitIndex++
  }
  return `${size.toFixed(1)} ${units[unitIndex]}`
}

const formatDate = (timestamp?: number) => {
  if (!timestamp) return '-'
  return new Date(timestamp).toLocaleString('zh-CN')
}

const getImageUrl = (photo: PhotoInfo) => {
  if (!photo) return ''
  const build = (p?: string) => {
    if (!p) return ''
    if (p.startsWith('http://') || p.startsWith('https://')) return p
    const normalized = p.startsWith('/') ? p : `/${p}`
    return `/api/files${normalized}`
  }
  // 优先使用WebP，其次缩略图，最后原图
  return build(photo.webpPath) || build(photo.thumbnailPath) || build(photo.originalPath)
}

onMounted(async () => {
  await loadBasePath()
  await loadFiles()
  document.addEventListener('click', handleClickOutside)
})

onUnmounted(() => {
  document.removeEventListener('click', handleClickOutside)
})
</script>

