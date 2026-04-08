<template>
  <div class="min-h-screen admin-shell text-white">
    <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
      <!-- 头部 -->
      <div class="flex items-center justify-between mb-6">
        <h1 class="text-2xl font-light">文件浏览器</h1>
        <router-link to="/admin" class="px-4 py-2 bg-gray-900/70 hover:bg-gray-700 rounded-lg border border-white/10 transition-colors">返回</router-link>
      </div>

      <!-- 路径导航栏（限制在 basePath 下，只显示相对路径） -->
      <div class="glass-panel p-4 mb-4">
        <div class="flex items-center gap-2 flex-wrap">
          <button
            @click="goToPath(basePath)"
            class="px-3 py-1 bg-gray-700 hover:bg-gray-600 rounded text-sm"
          >
            {{ rootButtonLabel }}
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
            @click="goToParent"
            :disabled="isAtRoot"
            class="ml-auto px-3 py-1 rounded text-sm transition-colors"
            :class="isAtRoot ? 'bg-gray-700/50 text-gray-500 cursor-not-allowed' : 'bg-blue-600 hover:bg-blue-700'"
          >
            返回上级
          </button>
        </div>
      </div>

      <!-- 工具栏 -->
      <div class="glass-panel p-4 mb-4 flex items-center gap-2 flex-wrap">
        <div v-if="canSelectStorageProvider" class="min-w-[240px] mr-2">
          <div class="text-xs text-gray-400 mb-1">管理员指定写入位置</div>
          <select
            v-model.number="selectedProviderId"
            @change="changeStorageProvider"
            class="w-full px-3 py-2 bg-gray-900/70 border border-white/10 rounded-lg text-sm"
          >
            <option
              v-for="provider in availableStorageProviders"
              :key="provider.id"
              :value="provider.id"
              :disabled="!provider.enabled || !provider.browserSupported"
            >
              {{ provider.name }} · {{ storageTypeLabel(provider.type) }}{{ provider.browserSupported ? '' : '（暂不支持浏览）' }}
            </option>
          </select>
        </div>
        <button
          @click="openCreateDialog"
          :disabled="!supportsDirectoryCreation"
          class="px-4 py-2 bg-green-600 hover:bg-green-700 rounded-lg text-sm disabled:opacity-50 disabled:cursor-not-allowed"
        >
          + 新建文件夹
        </button>
        <button
          @click="refresh"
          class="px-4 py-2 bg-gray-900/70 hover:bg-gray-700 rounded-lg text-sm whitespace-nowrap min-w-[90px] border border-white/10"
        >
          刷新
        </button>
        <button
          @click="triggerFileInput(false)"
          :disabled="!activeProviderSupported"
          class="px-4 py-2 bg-gray-700 hover:bg-gray-600 rounded-lg text-sm whitespace-nowrap disabled:opacity-50 disabled:cursor-not-allowed"
        >
          上传文件
        </button>
        <button
          @click="triggerFileInput(true)"
          :disabled="!activeProviderSupported"
          class="px-4 py-2 bg-gray-700 hover:bg-gray-600 rounded-lg text-sm whitespace-nowrap disabled:opacity-50 disabled:cursor-not-allowed"
        >
          上传文件夹
        </button>
        <button
          @click="toggleMultiSelect"
          :disabled="!supportsItemManagement"
          class="px-4 py-2 bg-gray-700 hover:bg-gray-600 rounded-lg text-sm whitespace-nowrap disabled:opacity-50 disabled:cursor-not-allowed"
          :class="{ 'opacity-50 cursor-not-allowed': !supportsItemManagement }"
        >
          {{ multiSelect ? '关闭多选' : '开启多选' }}
        </button>
        <template v-if="multiSelect">
          <button
            @click="moveSelected"
            :disabled="!selectedPaths.size || !supportsItemManagement"
            class="px-4 py-2 bg-blue-600 hover:bg-blue-700 rounded-lg text-sm disabled:opacity-50 whitespace-nowrap"
          >
            移动已选 ({{ selectedPaths.size }})
          </button>
          <button
            @click="deleteSelected"
            :disabled="!selectedPaths.size || !supportsItemManagement"
            class="px-4 py-2 bg-red-600 hover:bg-red-700 rounded-lg text-sm disabled:opacity-50 whitespace-nowrap"
          >
            删除已选 ({{ selectedPaths.size }})
          </button>
          <button
            @click="selectAll"
            :disabled="!supportsItemManagement"
            class="px-4 py-2 bg-gray-700 hover:bg-gray-600 rounded-lg text-sm whitespace-nowrap"
            :class="{ 'opacity-50 cursor-not-allowed': !supportsItemManagement }"
          >
            全选
          </button>
          <button
            @click="invertSelection"
            :disabled="!supportsItemManagement"
            class="px-4 py-2 bg-gray-700 hover:bg-gray-600 rounded-lg text-sm whitespace-nowrap"
            :class="{ 'opacity-50 cursor-not-allowed': !supportsItemManagement }"
          >
            反选
          </button>
        </template>
        <input ref="fileInput" type="file" multiple class="hidden" @change="handleFileInput(false, $event)" />
        <input ref="dirInput" type="file" multiple webkitdirectory class="hidden" @change="handleFileInput(true, $event)" />
        <div class="ml-auto text-right text-xs text-gray-400">
          <div>当前生效存储：{{ storageProviderName || '未选择' }}<span v-if="storageProviderType"> · {{ storageTypeLabel(storageProviderType) }}</span></div>
          <div class="truncate max-w-[360px]" :title="rootPathSummary">
            根目录：{{ rootPathSummary }}
          </div>
          <div v-if="selectedStorageProvider" class="flex flex-wrap justify-end gap-2 mt-2">
            <span class="px-2 py-1 rounded-full border text-[11px]"
              :class="selectedStorageProvider.browserSupported ? 'border-emerald-500/30 text-emerald-200' : 'border-gray-600 text-gray-400'">
              浏览 {{ selectedStorageProvider.browserSupported ? '可用' : '未接通' }}
            </span>
            <span class="px-2 py-1 rounded-full border text-[11px]"
              :class="selectedStorageProvider.uploadSupported ? 'border-emerald-500/30 text-emerald-200' : 'border-gray-600 text-gray-400'">
              上传 {{ selectedStorageProvider.uploadSupported ? '可用' : '未接通' }}
            </span>
            <span class="px-2 py-1 rounded-full border text-[11px]"
              :class="selectedStorageProvider.scanSupported ? 'border-emerald-500/30 text-emerald-200' : 'border-amber-500/30 text-amber-200'">
              扫描 {{ selectedStorageProvider.scanSupported ? '可用' : '受限' }}
            </span>
            <span class="px-2 py-1 rounded-full border text-[11px]"
              :class="supportsItemManagement ? 'border-emerald-500/30 text-emerald-200' : 'border-gray-600 text-gray-400'">
              管理 {{ supportsItemManagement ? '可用' : '未接通' }}
            </span>
            <span class="px-2 py-1 rounded-full border text-[11px]"
              :class="supportsPreview ? 'border-emerald-500/30 text-emerald-200' : 'border-gray-600 text-gray-400'">
              预览 {{ supportsPreview ? '可用' : '未接通' }}
            </span>
          </div>
          <div class="truncate max-w-[360px] text-[11px] text-gray-500">
            移动时请输入相对当前存储根目录的路径，不再要求绝对路径
          </div>
        </div>
      </div>

      <div
        v-if="selectedStorageProvider?.supportMessage"
        class="glass-panel p-4 mb-4 text-sm border"
        :class="selectedStorageProvider.uploadSupported && supportsItemManagement
          ? 'text-sky-200 border-sky-500/20'
          : 'text-amber-300 border-amber-500/20'"
      >
        {{ selectedStorageProvider.supportMessage }}
      </div>
      <div
        v-if="!supportsDirectoryCreation || !supportsItemManagement || !activeProviderSupported"
        class="glass-panel p-4 mb-4 text-xs text-gray-300 border border-white/10 space-y-1"
      >
        <div v-if="!activeProviderSupported">上传受限：{{ uploadDisabledReason }}</div>
        <div v-if="!supportsDirectoryCreation">建目录受限：{{ directoryCreationDisabledReason }}</div>
        <div v-if="!supportsItemManagement">批量管理受限：{{ managementDisabledReason }}</div>
      </div>

      <!-- 上传进度 -->
      <div v-if="uploading" class="glass-panel p-4 mb-4">
        <div class="flex items-center gap-3">
          <div class="animate-spin w-5 h-5 border-2 border-blue-400 border-t-transparent rounded-full"></div>
          <span class="text-blue-300">{{ uploadStatus }}</span>
        </div>
      </div>

      <!-- 文件列表 -->
      <div
        class="glass-panel p-4 relative"
        @dragover.prevent="onDragOver"
        @dragleave.prevent="onDragLeave"
        @drop.prevent="handleDrop"
      >
        <!-- 拖拽覆盖层 -->
        <div
          v-if="isDragOver"
          class="absolute inset-0 bg-blue-500/20 border-2 border-dashed border-blue-400 rounded-lg z-10 flex items-center justify-center pointer-events-none"
        >
          <div class="text-center">
            <div class="text-4xl mb-2">📂</div>
            <div class="text-blue-300 text-lg">拖放文件或文件夹到此处上传</div>
          </div>
        </div>
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
        v-if="contextMenu.item && !contextMenu.item.isDirectory"
        @click="downloadContextFile"
        class="w-full text-left px-4 py-2 hover:bg-gray-700"
      >
        下载
      </button>
      <button
        @click="startRename"
        :disabled="!supportsItemManagement"
        class="w-full text-left px-4 py-2 hover:bg-gray-700"
        :class="{ 'opacity-50 cursor-not-allowed': !supportsItemManagement }"
      >
        重命名
      </button>
      <button
        @click="confirmDelete"
        :disabled="!supportsItemManagement"
        class="w-full text-left px-4 py-2 hover:bg-red-600 rounded-b-lg text-red-300"
        :class="{ 'opacity-50 cursor-not-allowed': !supportsItemManagement }"
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
        <div class="mb-4 rounded-lg border border-white/10 bg-white/5 px-4 py-3 text-xs text-gray-300 space-y-1">
          <div>当前目录：{{ rootPathSummary }}</div>
          <div>仅会在当前目录下创建一层新文件夹。</div>
        </div>
        <label class="block space-y-2 mb-4">
          <span class="text-sm text-gray-300">文件夹名称</span>
          <input
            v-model="newFolderName"
            @keyup.enter="createFolder"
            placeholder="请输入新文件夹名称"
            ref="newFolderInput"
            class="w-full px-4 py-2 bg-gray-700 border border-gray-600 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
          />
          <span class="block text-xs text-gray-400">只填写名称，不要包含 `/` 或完整路径。</span>
        </label>
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
        <div class="mb-4 rounded-lg border border-white/10 bg-white/5 px-4 py-3 text-xs text-gray-300 space-y-1">
          <div>当前对象：{{ itemToRename?.name || '—' }}</div>
          <div>所在目录：{{ rootPathSummary }}</div>
        </div>
        <label class="block space-y-2 mb-4">
          <span class="text-sm text-gray-300">新名称</span>
          <input
            v-model="renameValue"
            @keyup.enter="renameItem"
            placeholder="请输入新的文件或文件夹名称"
            class="w-full px-4 py-2 bg-gray-700 border border-gray-600 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
            autofocus
          />
          <span class="block text-xs text-gray-400">只修改名称，不要输入目录路径。</span>
        </label>
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
import { api, type UploadPrecheckResponse } from '@/api'
import { useAuthStore } from '@/stores/auth'
import { buildPhotoAssetUrl } from '@/utils/photoUrl'
import { buildPublicPath } from '@/utils/publicRoute'
import { storageTypeLabel } from '@/utils/providerLabels'

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

interface BrowserStorageProvider {
  id: number
  name: string
  type: 'LOCAL' | 'FTP' | 'WEBDAV' | 'COS' | 'SFTP' | 'S3_COMPATIBLE' | 'MINIO' | 'OSS' | 'R2' | 'SMB' | 'NFS' | 'AZURE_BLOB' | 'GCS' | 'OBS' | 'TOS' | 'BOS' | 'UCLOUD_US3' | 'JD_JSS' | 'WASABI' | 'QINIU_KODO' | 'B2' | 'UPYUN' | 'DROPBOX' | 'ONEDRIVE'
  enabled: boolean
  baseDirectory?: string | null
  browserSupported: boolean
  uploadSupported: boolean
  scanSupported?: boolean
  previewSupported?: boolean
  supportMessage?: string | null
  scopedBasePath?: string | null
}

interface UploadResponse {
  saved?: number
  message?: string
  scanQueued?: boolean
  scanMessage?: string
  storageProviderId?: number | null
  storageProviderName?: string | null
  storageProviderType?: string | null
}

const authStore = useAuthStore()
const basePath = ref('')
const currentPath = ref('')
const parentPath = ref<string | null>(null)
const selectedProviderId = ref<number | null>(null)
const storageProviderName = ref('')
const storageProviderType = ref('')
const storageProviderBaseDirectory = ref('')
const availableStorageProviders = ref<BrowserStorageProvider[]>([])
const loading = ref(false)
const error = ref('')
const items = ref<FileItem[]>([])
const selectedItem = ref<FileItem | null>(null)
const selectedPaths = ref<Set<string>>(new Set())
const multiSelect = ref(false)
const uploading = ref(false)
const uploadStatus = ref('')
const isDragOver = ref(false)
let dragLeaveTimer: ReturnType<typeof setTimeout> | null = null

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

const selectedStorageProvider = computed(() => (
  availableStorageProviders.value.find(provider => provider.id === selectedProviderId.value) || null
))
const canSelectStorageProvider = computed(() => authStore.role === 'SUPER_ADMIN')
const directories = computed(() => items.value.filter(item => item.isDirectory))
const files = computed(() => items.value.filter(item => !item.isDirectory))
const activeProviderSupported = computed(() => (
  selectedStorageProvider.value ? selectedStorageProvider.value.uploadSupported : false
))
const supportsItemManagement = computed(() => (
  selectedStorageProvider.value ? !!selectedStorageProvider.value.browserSupported : true
))
const supportsDirectoryCreation = computed(() => (
  selectedStorageProvider.value ? !!selectedStorageProvider.value.browserSupported : true
))
const supportsPreview = computed(() => (
  selectedStorageProvider.value ? (selectedStorageProvider.value.type === 'LOCAL' || !!selectedStorageProvider.value.previewSupported) : true
))
const uploadDisabledReason = computed(() => (
  selectedStorageProvider.value?.supportMessage || '当前存储位置暂不支持上传'
))
const directoryCreationDisabledReason = computed(() => (
  selectedStorageProvider.value?.supportMessage || '当前存储位置暂不支持创建目录'
))
const managementDisabledReason = computed(() => (
  selectedStorageProvider.value?.supportMessage || '当前存储位置暂不支持批量管理'
))
const rootButtonLabel = computed(() => canSelectStorageProvider.value ? '根目录' : '我的相册')
const currentRelativePath = computed(() => pathParts.value.join('/'))
const rootPathSummary = computed(() => {
  if (!canSelectStorageProvider.value) {
    return currentPath.value && currentPath.value !== basePath.value
      ? `我的目录 / ${pathParts.value.join(' / ')}`
      : '我的目录'
  }
  return currentRelativePath.value
    ? `${storageProviderName.value || '存储根目录'} / ${currentRelativePath.value}`
    : `${storageProviderName.value || '存储根目录'}`
})
const isAtRoot = computed(() => {
  if (!currentPath.value || !basePath.value) return true
  return normalizePath(currentPath.value) === normalizePath(basePath.value)
})

const normalizePath = (p: string | null | undefined) => {
  if (!p) return ''
  return p.replace(/\\/g, '/')
}

const isUnderBase = (p: string | null | undefined, base = basePath.value) => {
  if (!p) return false
  if (!base) return true
  const normalizedBase = normalizePath(base)
  const target = normalizePath(p)
  if (!normalizedBase) return true
  const baseWithSlash = normalizedBase.endsWith('/') ? normalizedBase : normalizedBase + '/'
  return target === normalizedBase || target.startsWith(baseWithSlash)
}

const pathParts = computed(() => {
  if (!currentPath.value || currentPath.value === basePath.value) return []
  const cur = normalizePath(currentPath.value)
  const base = normalizePath(basePath.value)
  const relative = cur.startsWith(base) ? cur.slice(base.length).replace(/^[\/\\]+/, '') : cur
  return relative.split(/[\/\\]+/).filter(p => p)
})

const applyStorageContext = (data: any, resetCurrentPath = false) => {
  if (!data) return
  const nextBasePath = data.basePath || basePath.value || ''
  basePath.value = nextBasePath
  storageProviderName.value = data.storageProviderName || ''
  storageProviderType.value = data.storageProviderType || ''
  storageProviderBaseDirectory.value = data.storageProviderBaseDirectory || ''
  availableStorageProviders.value = data.availableStorageProviders || []
  selectedProviderId.value = data.storageProviderId ?? selectedProviderId.value

  if (resetCurrentPath || !currentPath.value || !isUnderBase(currentPath.value, nextBasePath)) {
    currentPath.value = nextBasePath
  }
}

const loadBasePath = async (providerId = selectedProviderId.value, resetCurrentPath = false) => {
  try {
    const res = await api.get('/admin/folders/base-path', {
      params: { providerId: canSelectStorageProvider.value ? (providerId ?? undefined) : undefined }
    })
    applyStorageContext(res.data, resetCurrentPath)
    return res.data
  } catch (e: any) {
    console.error('加载基础路径失败:', e)
    throw e
  }
}

let loadFilesRequestId = 0
const loadFiles = async (path?: string, allowRecovery = true) => {
  const requestId = ++loadFilesRequestId
  loading.value = true
  error.value = ''
  const requestedPath = path || currentPath.value
  try {
    const res = await api.get('/admin/folders/browser/list', {
      params: {
        path: requestedPath,
        providerId: canSelectStorageProvider.value ? (selectedProviderId.value ?? undefined) : undefined
      }
    })
    if (requestId !== loadFilesRequestId) return
    const data = res.data
    applyStorageContext(data)
    const serverPath = data.path || currentPath.value || basePath.value
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
    const filesList = (data.files || []).map((f: any) => ({
      name: f.name,
      path: f.path,
      isDirectory: false,
      size: f.size,
      lastModified: f.lastModified,
      thumbnail: f.thumbnail
    }))
    
    items.value = [...dirs, ...filesList]
  } catch (e: any) {
    if (requestId !== loadFilesRequestId) return
    const errorMessage = e.response?.data?.error || e.message || '加载失败'
    if (allowRecovery && String(errorMessage).includes('路径超出当前用户可操作范围')) {
      try {
        await loadBasePath(selectedProviderId.value, true)
        if (requestId !== loadFilesRequestId) return
        await loadFiles(basePath.value, false)
        return
      } catch (recoveryError: any) {
        error.value = recoveryError?.response?.data?.error || recoveryError?.message || errorMessage
        items.value = []
        return
      }
    }
    error.value = errorMessage
    items.value = []
  } finally {
    if (requestId === loadFilesRequestId) {
      loading.value = false
    }
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

const openCreateDialog = () => {
  if (!supportsDirectoryCreation.value) {
    alert(directoryCreationDisabledReason.value)
    return
  }
  showCreateDialog.value = true
}

const createFolder = async () => {
  if (!supportsDirectoryCreation.value) {
    alert(directoryCreationDisabledReason.value)
    return
  }
  if (!newFolderName.value.trim()) return
  creating.value = true
  try {
    await api.post('/admin/folders/browser/create', null, {
      params: {
        path: currentPath.value,
        name: newFolderName.value.trim(),
        providerId: canSelectStorageProvider.value ? (selectedProviderId.value ?? undefined) : undefined
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
  if (!supportsItemManagement.value) return
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
        newName: renameValue.value.trim(),
        providerId: canSelectStorageProvider.value ? (selectedProviderId.value ?? undefined) : undefined
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
  if (!supportsItemManagement.value) return
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
      params: {
        path: item.path,
        providerId: canSelectStorageProvider.value ? (selectedProviderId.value ?? undefined) : undefined
      }
    })
    await loadFiles()
  } catch (e: any) {
    alert('删除失败: ' + (e.response?.data?.error || e.message))
  }
}

const openFile = async (file: FileItem) => {
  if (!supportsPreview.value) {
    alert(selectedStorageProvider.value?.supportMessage || '当前存储位置暂未接通文件预览，请先使用已支持预览的存储查看文件内容。')
    return
  }
  // 如果是图片并且有对应的 Photo 记录，跳转到图片详情
  if (file.thumbnail && file.thumbnail.id) {
    window.open(buildPublicPath(`/photo/${file.thumbnail.id}`, authStore.slug ? `/${authStore.slug}` : undefined), '_blank')
    return
  }
  if (selectedStorageProvider.value && selectedStorageProvider.value.type !== 'LOCAL') {
    try {
      const openUrlResponse = await api.get('/admin/folders/browser/open-url', {
        params: {
          path: file.path,
          providerId: canSelectStorageProvider.value ? (selectedProviderId.value ?? undefined) : undefined
        }
      })
      const previewUrl = openUrlResponse.data?.url
      if (previewUrl) {
        window.open(previewUrl, '_blank')
        return
      }
    } catch (_error: any) {
    }
    try {
      const response = await api.get('/admin/folders/browser/preview', {
        params: {
          path: file.path,
          providerId: canSelectStorageProvider.value ? (selectedProviderId.value ?? undefined) : undefined
        },
        responseType: 'blob'
      })
      if (response.data) {
        const blob = response.data instanceof Blob ? response.data : new Blob([response.data])
        const blobUrl = URL.createObjectURL(blob)
        window.open(blobUrl, '_blank')
        window.setTimeout(() => URL.revokeObjectURL(blobUrl), 60 * 1000)
        return
      }
      alert('未获取到可用的预览内容')
    } catch (e: any) {
      alert('打开文件失败: ' + (e.response?.data?.error || e.message))
    }
    return
  }
  // 尝试直接打开文件
  const url = getImageUrl(file.thumbnail || { originalPath: file.path })
  if (url) {
    window.open(url, '_blank')
  }
}

const downloadFile = async (file: FileItem) => {
  contextMenu.value.show = false
  try {
    const response = await api.get('/admin/folders/browser/download', {
      params: {
        path: file.path,
        providerId: canSelectStorageProvider.value ? (selectedProviderId.value ?? undefined) : undefined
      },
      responseType: 'blob'
    })
    const blob = response.data instanceof Blob ? response.data : new Blob([response.data])
    const blobUrl = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = blobUrl
    link.download = resolveDownloadFilename(response.headers?.['content-disposition'], file.name || 'download')
    document.body.appendChild(link)
    link.click()
    link.remove()
    window.setTimeout(() => URL.revokeObjectURL(blobUrl), 60 * 1000)
  } catch (e: any) {
    alert('下载失败: ' + (e.response?.data?.error || e.message))
  }
}

const downloadContextFile = () => {
  if (!contextMenu.value.item || contextMenu.value.item.isDirectory) return
  downloadFile(contextMenu.value.item)
}

const resolveDownloadFilename = (contentDisposition?: string, fallback = 'download') => {
  if (!contentDisposition) return fallback
  const utf8Match = contentDisposition.match(/filename\*=UTF-8''([^;]+)/i)
  if (utf8Match?.[1]) {
    try {
      return decodeURIComponent(utf8Match[1].trim())
    } catch {
      return utf8Match[1].trim()
    }
  }
  const filenameMatch = contentDisposition.match(/filename="([^"]+)"/i) || contentDisposition.match(/filename=([^;]+)/i)
  return filenameMatch?.[1]?.trim() || fallback
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
  if (!supportsItemManagement.value) return
  if (!selectedPaths.value.size) return
  if (!confirm(`确认删除选中的 ${selectedPaths.value.size} 项？`)) return
  try {
    await api.delete('/admin/folders/browser/delete-items', {
      params: {
        paths: Array.from(selectedPaths.value),
        providerId: canSelectStorageProvider.value ? (selectedProviderId.value ?? undefined) : undefined
      }
    })
    selectedPaths.value.clear()
    await loadFiles()
  } catch (e: any) {
    alert('删除失败: ' + (e.response?.data?.error || e.message))
  }
}

const toggleMultiSelect = () => {
  if (!supportsItemManagement.value) return
  multiSelect.value = !multiSelect.value
  if (!multiSelect.value) {
    selectedPaths.value.clear()
  }
}

const selectAll = () => {
  if (!multiSelect.value || !supportsItemManagement.value) return
  const set = new Set<string>()
  items.value.forEach(i => set.add(i.path))
  selectedPaths.value = set
}

const invertSelection = () => {
  if (!multiSelect.value || !supportsItemManagement.value) return
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
  if (!supportsItemManagement.value) {
    alert(selectedStorageProvider.value?.supportMessage || '当前存储位置暂不支持批量管理。')
    return
  }
  if (!selectedPaths.value.size) return
  const targetInput = prompt(
    '输入目标目录（相对当前存储根目录，不支持 .. 回退）',
    currentRelativePath.value || ''
  )
  if (!targetInput) return
  let target = ''
  try {
    target = resolveTargetPath(targetInput)
  } catch (e: any) {
    alert(e?.message || '目标目录格式不合法')
    return
  }
  try {
    await api.post('/admin/folders/browser/move-items', null, {
      params: {
        paths: Array.from(selectedPaths.value),
        target,
        providerId: canSelectStorageProvider.value ? (selectedProviderId.value ?? undefined) : undefined
      }
    })
    selectedPaths.value.clear()
    await loadFiles()
  } catch (e: any) {
    alert('移动失败: ' + (e.response?.data?.error || e.message))
  }
}

const resolveTargetPath = (input: string) => {
  const trimmed = input.trim()
  if (!trimmed || trimmed === '/') return basePath.value
  if (trimmed.includes('..')) {
    throw new Error('目标目录不支持使用 .. 回退，请输入根目录下的相对路径')
  }
  if (isUnderBase(trimmed)) {
    return trimmed
  }
  const normalizedBase = normalizePath(basePath.value).replace(/[\/\\]+$/, '')
  const normalizedRelative = normalizePath(trimmed).replace(/^[\/\\]+/, '')
  return normalizedRelative ? `${normalizedBase}/${normalizedRelative}` : normalizedBase
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
  return buildPhotoAssetUrl({
    id: photo.id,
    webpPath: photo.webpPath,
    thumbnailPath: photo.thumbnailPath,
    originalPath: photo.originalPath
  }, 'auto') || ''
}

const triggerFileInput = (isDir: boolean) => {
  if (!activeProviderSupported.value) return
  if (isDir) {
    dirInput.value?.click()
  } else {
    fileInput.value?.click()
  }
}

const handleFileInput = async (isDir: boolean, event: Event) => {
  const input = event.target as HTMLInputElement
  if (!input.files || !input.files.length) return
  const fileList = Array.from(input.files)
  const relativePaths = isDir ? fileList.map(f => (f as any).webkitRelativePath || f.name) : undefined
  await uploadFiles(fileList, relativePaths)
  input.value = ''
}

const onDragOver = () => {
  if (!activeProviderSupported.value) return
  if (dragLeaveTimer) { clearTimeout(dragLeaveTimer); dragLeaveTimer = null }
  isDragOver.value = true
}
const onDragLeave = () => {
  if (!activeProviderSupported.value) return
  dragLeaveTimer = setTimeout(() => { isDragOver.value = false }, 100)
}

const readEntryRecursive = async (entry: any, basePath: string): Promise<{file: File, relativePath: string}[]> => {
  const results: {file: File, relativePath: string}[] = []
  if (entry.isFile) {
    const file = await new Promise<File>((resolve, reject) => entry.file(resolve, reject))
    results.push({ file, relativePath: basePath + entry.name })
  } else if (entry.isDirectory) {
    const reader = entry.createReader()
    const entries: any[] = await new Promise((resolve) => {
      const all: any[] = []
      const readBatch = () => {
        reader.readEntries((batch: any[]) => {
          if (batch.length === 0) { resolve(all) }
          else { all.push(...batch); readBatch() }
        })
      }
      readBatch()
    })
    for (const child of entries) {
      const childResults = await readEntryRecursive(child, basePath + entry.name + '/')
      results.push(...childResults)
    }
  }
  return results
}

const handleDrop = async (event: DragEvent) => {
  isDragOver.value = false
  if (!activeProviderSupported.value) {
    alert(uploadDisabledReason.value)
    return
  }
  const dt = event.dataTransfer
  if (!dt) return

  const allEntries: {file: File, relativePath: string}[] = []
  const items = dt.items
  if (items && items.length > 0) {
    for (let i = 0; i < items.length; i++) {
      const entry = (items[i] as any).webkitGetAsEntry?.()
      if (entry) {
        const results = await readEntryRecursive(entry, '')
        allEntries.push(...results)
      }
    }
  }

  if (allEntries.length > 0) {
    await uploadFiles(
      allEntries.map(e => e.file),
      allEntries.map(e => e.relativePath)
    )
  } else {
    const fileList = Array.from(dt.files)
    if (fileList.length > 0) {
      await uploadFiles(fileList)
    }
  }
}

let refreshTimer: ReturnType<typeof setTimeout> | null = null
const uploadFiles = async (fileList: File[], relativePaths?: string[]) => {
  if (!fileList.length) return
  if (!activeProviderSupported.value) {
    alert(selectedStorageProvider.value?.supportMessage || '当前存储位置暂不支持上传')
    return
  }
  const uploadUrl = '/api/admin/folders/browser/upload'
  const BATCH_SIZE = 10
  const token = localStorage.getItem('auth_token') || localStorage.getItem('admin_token')
  uploading.value = true
  uploadStatus.value = `正在上传 0 / ${fileList.length} 个文件...`
  let totalSaved = 0
  let finalScanQueued = true
  let finalScanMessage = ''
  try {
    if (!(await runUploadPrecheck(fileList))) {
      uploadStatus.value = '上传已取消'
      return
    }
    for (let i = 0; i < fileList.length; i += BATCH_SIZE) {
      const slice = fileList.slice(i, i + BATCH_SIZE)
      const relSlice = relativePaths ? relativePaths.slice(i, i + BATCH_SIZE) : undefined

      const form = new FormData()
      slice.forEach(f => form.append('files', f))
      if (relSlice) {
        relSlice.forEach(p => form.append('relativePaths', p))
      }
      form.append('target', currentPath.value)
      if (canSelectStorageProvider.value && selectedProviderId.value != null) {
        form.append('providerId', String(selectedProviderId.value))
      }

      const res = await fetch(uploadUrl, {
        method: 'POST',
        body: form,
        credentials: 'same-origin',
        headers: token ? { Authorization: `Bearer ${token}` } : undefined
      })
      if (!res.ok) {
        const text = await res.text()
        throw new Error(text || res.statusText)
      }
      const data: UploadResponse = await res.json()
      totalSaved += data.saved || slice.length
      if (data.scanQueued === false) {
        finalScanQueued = false
      }
      if (data.scanMessage) {
        finalScanMessage = data.scanMessage
      } else if (data.message) {
        finalScanMessage = data.message
      }
      uploadStatus.value = `正在上传 ${Math.min(i + BATCH_SIZE, fileList.length)} / ${fileList.length} 个文件...`
    }
    uploadStatus.value = finalScanQueued
      ? `已保存 ${totalSaved} 个文件，已加入后台扫描...`
      : (finalScanMessage || `已保存 ${totalSaved} 个文件，但当前存储未加入自动扫描`)
    if (refreshTimer) clearTimeout(refreshTimer)
    refreshTimer = setTimeout(() => loadFiles(), 500)
  } catch (e: any) {
    console.error('上传失败', e)
    alert('上传失败: ' + (e.message || '上传失败'))
  } finally {
    setTimeout(() => { uploading.value = false }, 3000)
  }
}

const runUploadPrecheck = async (fileList: File[]) => {
  const duplicates: Array<{ file: string; result: UploadPrecheckResponse }> = []
  const precheckCache = new Map<string, UploadPrecheckResponse>()
  for (let i = 0; i < fileList.length; i++) {
    uploadStatus.value = `正在预检查 ${i + 1} / ${fileList.length} 个文件...`
    const contentHash = await sha256File(fileList[i])
    let result = precheckCache.get(contentHash)
    if (!result) {
      const { data } = await api.get<UploadPrecheckResponse>('/admin/folders/upload-precheck', {
        params: { contentHash, _: Date.now() + i }
      })
      result = data
      if (result) {
        precheckCache.set(contentHash, result)
      }
    }
    if (result?.exists) {
      duplicates.push({ file: fileList[i].name, result })
    }
  }
  if (!duplicates.length) {
    return true
  }
  const sameOwnerCount = duplicates.filter(item => item.result.sameOwner).length
  const canonicalCount = duplicates.length - sameOwnerCount
  const derivativeCount = duplicates.filter(item => item.result.reusableDerivatives).length
  const preview = duplicates
    .slice(0, 5)
    .map(item => `- ${item.file}：${item.result.sameOwner ? '当前用户已存在' : '命中已有规范源'}${item.result.reusableDerivatives ? '，可复用派生资源' : ''}`)
    .join('\n')
  return window.confirm(
    `检测到 ${duplicates.length} 个文件命中重复内容。\n`
    + `- 当前用户已有：${sameOwnerCount} 个\n`
    + `- 命中已有规范源：${canonicalCount} 个\n`
    + `- 可复用派生资源：${derivativeCount} 个\n`
    + `${preview}${duplicates.length > 5 ? '\n- ...' : ''}\n\n`
    + '继续上传会保留独立记录，但后续可复用规范源信息。是否继续？'
  )
}

const sha256File = async (file: File) => {
  const buffer = await file.arrayBuffer()
  const digest = await crypto.subtle.digest('SHA-256', buffer)
  return Array.from(new Uint8Array(digest))
    .map(byte => byte.toString(16).padStart(2, '0'))
    .join('')
}

const changeStorageProvider = async () => {
  selectedPaths.value.clear()
  items.value = []
  parentPath.value = null
  error.value = ''
  await loadBasePath(selectedProviderId.value, true)
  await loadFiles(basePath.value)
}

watch(showCreateDialog, (val) => {
  if (val) {
    nextTick(() => newFolderInput.value?.focus())
  }
})

onMounted(async () => {
  await loadBasePath(selectedProviderId.value, true)
  await loadFiles(basePath.value)
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
