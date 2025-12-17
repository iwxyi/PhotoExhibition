<template>
  <div class="min-h-screen bg-gray-900 text-white">
    <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
      <!-- 头部 -->
      <div class="flex items-center justify-between mb-6">
        <h1 class="text-2xl font-light">文件浏览器</h1>
        <router-link to="/admin" class="px-4 py-2 bg-gray-700 hover:bg-gray-600 rounded-lg">返回</router-link>
      </div>

      <!-- 路径导航栏（限制在 basePath 下，只显示相对路径） -->
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
          class="px-4 py-2 bg-gray-700 hover:bg-gray-600 rounded-lg text-sm whitespace-nowrap min-w-[90px]"
        >
          刷新
        </button>
        <button
          @click="triggerFileInput(false)"
          class="px-4 py-2 bg-gray-700 hover:bg-gray-600 rounded-lg text-sm whitespace-nowrap"
        >
          上传文件
        </button>
        <button
          @click="triggerFileInput(true)"
          class="px-4 py-2 bg-gray-700 hover:bg-gray-600 rounded-lg text-sm whitespace-nowrap"
        >
          上传文件夹
        </button>
        <button
          @click="toggleMultiSelect"
          class="px-4 py-2 bg-gray-700 hover:bg-gray-600 rounded-lg text-sm whitespace-nowrap"
        >
          {{ multiSelect ? '关闭多选' : '开启多选' }}
        </button>
        <template v-if="multiSelect">
          <button
            @click="moveSelected"
            :disabled="!selectedPaths.size"
            class="px-4 py-2 bg-blue-600 hover:bg-blue-700 rounded-lg text-sm disabled:opacity-50 whitespace-nowrap"
          >
            移动已选 ({{ selectedPaths.size }})
          </button>
          <button
            @click="deleteSelected"
            :disabled="!selectedPaths.size"
            class="px-4 py-2 bg-red-600 hover:bg-red-700 rounded-lg text-sm disabled:opacity-50 whitespace-nowrap"
          >
            删除已选 ({{ selectedPaths.size }})
          </button>
          <button
            @click="selectAll"
            class="px-4 py-2 bg-gray-700 hover:bg-gray-600 rounded-lg text-sm whitespace-nowrap"
          >
            全选
          </button>
          <button
            @click="invertSelection"
            class="px-4 py-2 bg-gray-700 hover:bg-gray-600 rounded-lg text-sm whitespace-nowrap"
          >
            反选
          </button>
        </template>
        <input ref="fileInput" type="file" multiple class="hidden" @change="handleFileInput(false, $event)" />
        <input ref="dirInput" type="file" multiple webkitdirectory class="hidden" @change="handleFileInput(true, $event)" />
      </div>

      <!-- 文件列表 -->
      <div
        class="bg-gray-800 rounded-lg p-4"
        @dragover.prevent
        @drop.prevent="handleDrop"
      >
        <div v-if="loading" class="text-center py-8 text-gray-400">
          加载中...
        </div>
        <div v-else-if="error" class="text-center py-8 text-red-400">
          {{ error }}
        </div>
        <div v-else-if="!items.length" class="text-center py-8 text-gray-400">
          当前目录为空
        </div>
        <div v-else class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-3">
          <!-- 文件夹 -->
          <div
            v-for="dir in directories"
            :key="dir.path"
            @click="goToPath(dir.path)"
            @contextmenu.prevent="showContextMenu($event, dir)"
            class="bg-gray-700 hover:bg-gray-600 rounded-lg p-4 cursor-pointer transition-colors relative"
            :class="{ 'ring-2 ring-blue-500': selectedItem?.path === dir.path }"
          >
            <label v-if="multiSelect" class="absolute top-2 right-2">
              <input type="checkbox" class="w-4 h-4" :checked="selectedPaths.has(dir.path)" @click.stop="toggleSelect(dir.path)" />
            </label>
            <!-- 三合一封面 -->
            <div v-if="dir.leftVertical || dir.rightTop || dir.rightBottom" class="grid grid-cols-2 gap-[2px] mb-3 h-32 rounded-lg overflow-hidden">
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
            <label v-if="multiSelect" class="absolute top-2 right-2">
              <input type="checkbox" class="w-4 h-4" :checked="selectedPaths.has(file.path)" @click.stop="toggleSelect(file.path)" />
            </label>
            <!-- 图片文件显示缩略图 -->
            <div v-if="file.thumbnail" class="mb-3 h-32 rounded-lg overflow-hidden flex items-center justify-center">
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
          ref="newFolderInput"
          class="w-full px-4 py-2 bg-gray-700 border border-gray-600 rounded-lg mb-4 focus:outline-none focus:ring-2 focus:ring-blue-500"
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
import { ref, computed, onMounted, onUnmounted, watch, nextTick } from 'vue'
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
const selectedPaths = ref<Set<string>>(new Set())
const multiSelect = ref(false)
const uploading = ref(false)

const showCreateDialog = ref(false)
const newFolderName = ref('')
const creating = ref(false)
const newFolderInput = ref<HTMLInputElement | null>(null)

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

const fileInput = ref<HTMLInputElement | null>(null)
const dirInput = ref<HTMLInputElement | null>(null)

const directories = computed(() => items.value.filter(item => item.isDirectory))
const files = computed(() => items.value.filter(item => !item.isDirectory))

const normalizePath = (p: string | null | undefined) => {
  if (!p) return ''
  return p.replace(/\\/g, '/')
}

const isUnderBase = (p: string | null | undefined) => {
  if (!p) return false
  if (!basePath.value) return true
  const base = normalizePath(basePath.value)
  const target = normalizePath(p)
  if (!base) return true
  const baseWithSlash = base.endsWith('/') ? base : base + '/'
  return target === base || target.startsWith(baseWithSlash)
}

const pathParts = computed(() => {
  if (!currentPath.value || currentPath.value === basePath.value) return []
  const cur = normalizePath(currentPath.value)
  const base = normalizePath(basePath.value)
  const relative = cur.startsWith(base) ? cur.slice(base.length).replace(/^[\/\\]+/, '') : cur
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
    const serverPath = data.path || currentPath.value || basePath.value
    // 如果后端返回了绝对路径，而当前 basePath 是相对路径（如 ./data/photos），
    // 则将 basePath 替换为该绝对路径，确保后续前端判断统一基于绝对路径
    if (serverPath && basePath.value && !/^(\/|[A-Za-z]:[\\/])/.test(basePath.value)) {
      basePath.value = normalizePath(serverPath)
    }
    currentPath.value = isUnderBase(serverPath) ? serverPath : basePath.value
    parentPath.value = data.parent && isUnderBase(data.parent) ? data.parent : null
    
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
  let target = path || basePath.value
  if (!isUnderBase(target)) {
    target = basePath.value
  }
  currentPath.value = target
  selectedPaths.value.clear()
  loadFiles(target)
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
  if (loading.value) return
  selectedPaths.value.clear()
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

const toggleSelect = (path: string) => {
  if (!multiSelect.value) return
  const set = new Set(selectedPaths.value)
  if (set.has(path)) set.delete(path)
  else set.add(path)
  selectedPaths.value = set
}

const deleteSelected = async () => {
  if (!selectedPaths.value.size) return
  if (!confirm(`确认删除选中的 ${selectedPaths.value.size} 项？`)) return
  try {
    await api.delete('/admin/folders/browser/delete-items', {
      params: { paths: Array.from(selectedPaths.value) }
    })
    selectedPaths.value.clear()
    await loadFiles()
  } catch (e: any) {
    alert('删除失败: ' + (e.response?.data?.error || e.message))
  }
}

const toggleMultiSelect = () => {
  multiSelect.value = !multiSelect.value
  if (!multiSelect.value) {
    selectedPaths.value.clear()
  }
}

const selectAll = () => {
  if (!multiSelect.value) return
  const set = new Set<string>()
  items.value.forEach(i => set.add(i.path))
  selectedPaths.value = set
}

const invertSelection = () => {
  if (!multiSelect.value) return
  const set = new Set<string>()
  const current = selectedPaths.value
  items.value.forEach(i => {
    if (current.has(i.path)) return
    set.add(i.path)
  })
  // 同时保留未选 → 选，已选 → 取消
  items.value.forEach(i => {
    if (!current.has(i.path)) return
    // 已选的反转为不选，已处理
  })
  selectedPaths.value = set
}

const moveSelected = async () => {
  if (!selectedPaths.value.size) return
  const target = prompt('输入目标目录绝对路径', currentPath.value)
  if (!target) return
  try {
    await api.post('/admin/folders/browser/move-items', null, {
      params: { paths: Array.from(selectedPaths.value), target }
    })
    selectedPaths.value.clear()
    await loadFiles()
  } catch (e: any) {
    alert('移动失败: ' + (e.response?.data?.error || e.message))
  }
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

const triggerFileInput = (isDir: boolean) => {
  if (isDir) {
    dirInput.value?.click()
  } else {
    fileInput.value?.click()
  }
}

const handleFileInput = async (isDir: boolean, event: Event) => {
  const input = event.target as HTMLInputElement
  if (!input.files || !input.files.length) return
  const files = Array.from(input.files)
  const relativePaths = isDir ? files.map(f => (f as any).webkitRelativePath || f.name) : undefined
  await uploadFiles(files, relativePaths)
  input.value = ''
}

const handleDrop = async (event: DragEvent) => {
  const dt = event.dataTransfer
  if (!dt) return
  const files = Array.from(dt.files)
  const relativePaths = files.map(f => (f as any).webkitRelativePath || f.name)
  await uploadFiles(files, relativePaths)
}

const uploadFiles = async (files: File[], relativePaths?: string[]) => {
  if (!files.length) return
  const uploadUrl = '/api/admin/folders/browser/upload' // 走前端同源代理，避免跨域/凭证问题
  const BATCH_SIZE = 10 // 较小批次，降低 EOF 风险
  uploading.value = true
  try {
    for (let i = 0; i < files.length; i += BATCH_SIZE) {
      const slice = files.slice(i, i + BATCH_SIZE)
      const relSlice = relativePaths ? relativePaths.slice(i, i + BATCH_SIZE) : undefined

      const form = new FormData()
      slice.forEach(f => form.append('files', f))
      if (relSlice) {
        relSlice.forEach(p => form.append('relativePaths', p))
      }
      form.append('target', currentPath.value)

      const res = await fetch(uploadUrl, {
        method: 'POST',
        body: form,
        credentials: 'same-origin'
      })
      if (!res.ok) {
        const text = await res.text()
        throw new Error(text || res.statusText)
      }
    }
    await loadFiles()
  } catch (e: any) {
    console.error('上传失败', e)
    alert('上传失败: ' + (e.message || '上传失败'))
  } finally {
    uploading.value = false
  }
}

watch(showCreateDialog, (val) => {
  if (val) {
    nextTick(() => newFolderInput.value?.focus())
  }
})

onMounted(async () => {
  await loadBasePath()
  await loadFiles()
  document.addEventListener('click', handleClickOutside)
  const escHandler = (e: KeyboardEvent) => {
    if (e.key === 'Escape') {
      if (contextMenu.value.show) {
        contextMenu.value.show = false
        selectedItem.value = null
        return
      }
      if (showCreateDialog.value || showRenameDialog.value) return
      goToParent()
    }
  }
  document.addEventListener('keydown', escHandler)
  onUnmounted(() => document.removeEventListener('keydown', escHandler))
})

onUnmounted(() => {
  document.removeEventListener('click', handleClickOutside)
})
</script>

