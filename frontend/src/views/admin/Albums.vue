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

      <div class="bg-gray-800 rounded-lg p-4 mb-6">
        <div class="flex flex-wrap gap-4">
          <input v-model="keyword" placeholder="搜索名称/路径" class="px-3 py-2 bg-gray-700 border border-gray-600 rounded w-64 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
          <button @click="load" :disabled="loading" class="px-4 py-2 bg-gray-700 hover:bg-gray-600 rounded-lg text-sm disabled:opacity-50">查询</button>
        </div>
      </div>

      <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
        <div
          v-for="album in albums"
          :key="album.id"
          class="bg-gray-800 rounded-lg overflow-hidden hover:ring-2 hover:ring-blue-500 transition-all flex flex-col cursor-default"
          @contextmenu.prevent="openContextMenu($event, album)"
        >
          <!-- 三合一封面预览（左竖 + 右上/右下） -->
          <!-- 缩小高度，让卡片更紧凑；高度随宽度响应调整 -->
          <div class="relative h-32 md:h-36 lg:h-40 bg-gray-900 overflow-hidden flex-shrink-0">
            <template v-if="album.coverImages && (album.coverImages.leftVertical || album.coverImages.rightTop || album.coverImages.rightBottom)">
              <div class="grid h-full w-full grid-cols-[2fr,3fr] grid-rows-2 gap-[2px]">
                <!-- 左侧竖图（占两行） -->
                <div class="row-span-2 bg-gray-800 overflow-hidden">
                  <img
                    v-if="album.coverImages.leftVertical"
                    :src="getPhotoUrl(album.coverImages.leftVertical)"
                    :alt="album.coverImages.leftVertical.filename"
                    class="w-full h-full object-cover"
                    @error="onImageError"
                  />
                  <div v-else class="w-full h-full bg-gray-800" />
                </div>
                <!-- 右上横图 -->
                <div class="bg-gray-800 overflow-hidden">
                  <img
                    v-if="album.coverImages.rightTop"
                    :src="getPhotoUrl(album.coverImages.rightTop)"
                    :alt="album.coverImages.rightTop.filename"
                    class="w-full h-full object-cover"
                    @error="onImageError"
                  />
                  <div v-else class="w-full h-full bg-gray-800" />
                </div>
                <!-- 右下横图，带“共 x 张”蒙版 -->
                <div class="relative bg-gray-800 overflow-hidden">
                  <img
                    v-if="album.coverImages.rightBottom"
                    :src="getPhotoUrl(album.coverImages.rightBottom)"
                    :alt="album.coverImages.rightBottom.filename"
                    class="w-full h-full object-cover"
                    @error="onImageError"
                  />
                  <div v-else class="w-full h-full bg-gray-800" />
                  <div class="absolute inset-0 bg-black/45 flex items-center justify-center">
                    <span class="text-xs text-white">共 {{ album.photoCount || 0 }} 张</span>
                  </div>
                </div>
              </div>
            </template>
            <template v-else>
              <div class="flex items-center justify-center h-full text-gray-500">
                <svg class="w-16 h-16" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path
                    stroke-linecap="round"
                    stroke-linejoin="round"
                    stroke-width="2"
                    d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z"
                  />
                </svg>
              </div>
            </template>
          </div>

          <!-- 相册信息 -->
          <div class="p-3 flex flex-col flex-grow">
            <!-- 固定内容区 -->
            <div class="flex-shrink-0">
              <div class="flex items-center justify-between gap-2 mb-0.5">
                <h3 class="text-base font-medium truncate" :title="album.displayTitle || album.name">
                  {{ album.displayTitle || album.name }}
                </h3>
                <!-- 时间放在标题行最右侧，右对齐且不换行 -->
                <span class="text-xs text-gray-500 flex-shrink-0 text-right whitespace-nowrap">
                  {{ formatDate(album.takenAt) }}
                </span>
              </div>
              <p class="text-xs text-gray-400 mb-1 truncate" :title="album.relativePath">
                {{ album.relativePath || album.path }}
              </p>
            </div>

            <!-- 可伸缩的中间区域 -->
            <div class="flex-grow min-h-0 mt-2">
              <!-- 标签 -->
              <div v-if="album.tags && album.tags.length > 0" class="mb-2">
                <div class="flex flex-wrap gap-1">
                  <span
                    v-for="t in album.tags"
                    :key="t.id"
                    class="px-1.5 py-0.5 bg-blue-500/20 border border-blue-500/40 rounded text-xs inline-flex items-center gap-1"
                  >
                    {{ t.name }}
                    <button
                      @click.stop="removeTag(album, t.id)"
                      class="hover:text-red-400"
                      title="移除标签"
                    >
                      ×
                    </button>
                  </span>
                </div>
              </div>

              <!-- 备注 -->
              <div v-if="album.description" class="text-xs text-gray-300 bg-gray-900/50 p-1.5 rounded line-clamp-2">
                {{ album.description }}
              </div>
        </div>

            <!-- 操作改为右键菜单触发，这里不再显示按钮 -->
          </div>
        </div>
      </div>

      <!-- 右键菜单 -->
      <div
        v-if="contextMenu.visible"
        class="fixed z-50 bg-gray-800 border border-gray-700 rounded-lg shadow-lg min-w-[140px] text-sm"
        :style="{ left: contextMenu.x + 'px', top: contextMenu.y + 'px' }"
      >
        <button
          class="w-full text-left px-4 py-2 hover:bg-gray-700 rounded-t-lg"
          @click="handleMenuAddTag"
        >
          添加标签
        </button>
        <button
          class="w-full text-left px-4 py-2 hover:bg-gray-700"
          @click="handleMenuEditDescription"
        >
          编辑备注
        </button>
        <button
          class="w-full text-left px-4 py-2 hover:bg-gray-700"
          @click="handleMenuEditName"
        >
          重命名
        </button>
        <button
          class="w-full text-left px-4 py-2 hover:bg-red-600 text-red-300 rounded-b-lg"
          @click="handleMenuDelete"
        >
          删除相册
        </button>
      </div>
    </div>

    <!-- 添加标签对话框 -->
    <teleport to="body">
      <div
        v-if="tagDialogVisible"
        class="fixed inset-0 z-50 bg-black/50 flex items-center justify-center p-4"
        @click.self="tagDialogVisible = false"
      >
        <div class="bg-gray-800 rounded-lg p-6 max-w-md w-full text-gray-100">
          <h3 class="text-lg font-medium mb-4 text-gray-100">添加标签</h3>
          <div class="mb-4">
            <input
              v-model="tagKeyword"
              @input="searchTags"
              placeholder="搜索或输入新标签名称"
              class="w-full px-3 py-2 bg-gray-700 border border-gray-600 rounded text-sm text-gray-100 placeholder-gray-400 focus:outline-none focus:ring-2 focus:ring-blue-500"
              @keyup.enter="confirmAddTag"
            />
          </div>
          <!-- 标签候选：瀑布流胶囊布局 -->
          <div class="max-h-60 overflow-auto mb-4 border border-gray-700 rounded bg-gray-900/60">
            <div v-if="filteredTags.length > 0" class="p-2">
              <div class="flex flex-wrap gap-2">
                <button
                  v-for="tag in filteredTags"
                  :key="tag.id"
                  @click="selectTag(tag)"
                  class="px-3 py-1 rounded-full bg-gray-700 hover:bg-blue-600 text-xs text-gray-100 border border-gray-500 transition-colors cursor-pointer"
                >
                  {{ tag.name }}
                </button>
              </div>
            </div>
            <div v-else-if="tagKeyword.trim()" class="px-3 py-2 text-gray-200 text-sm">
              没有找到标签 "{{ tagKeyword }}"，点击确定创建新标签
            </div>
            <div v-else class="px-3 py-2 text-gray-200 text-sm">
              请输入标签名称
            </div>
          </div>
          <div class="flex gap-2">
            <button
              @click="confirmAddTag"
              class="flex-1 px-4 py-2 bg-blue-600 hover:bg-blue-700 rounded text-sm disabled:opacity-50"
              :disabled="!tagKeyword.trim()"
            >
              确定
            </button>
            <button
              @click="tagDialogVisible = false"
              class="flex-1 px-4 py-2 bg-gray-700 hover:bg-gray-600 rounded text-sm"
            >
              取消
            </button>
        </div>
      </div>
    </div>
    </teleport>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '@/api'

const router = useRouter()

const albums = ref<any[]>([])
const loading = ref(false)
const keyword = ref('')

// 标签相关
const allTags = ref<any[]>([])
const tagDialogVisible = ref(false)
const tagKeyword = ref('')
const currentAlbum = ref<any>(null)

// 右键菜单状态
const contextMenu = ref<{
  visible: boolean
  x: number
  y: number
  album: any | null
}>({
  visible: false,
  x: 0,
  y: 0,
  album: null
})

const load = async () => {
  loading.value = true
  try {
    // 一次性加载较多相册，前端不再做分页
    const params: any = { page: 0, size: 1000 }
    const res = await api.get('/albums', { params })
    let content = res.data.content || res.data || []
    
    // 为每个相册提取相对路径（去掉 base-path）
    for (const album of content) {
      album.relativePath = extractRelativePath(album.path)
    }
    
    // 关键词过滤
    if (keyword.value.trim()) {
      const kw = keyword.value.trim().toLowerCase()
      content = content.filter((a: any) =>
        (a.name || '').toLowerCase().includes(kw) ||
        (a.path || '').toLowerCase().includes(kw) ||
        (a.displayTitle || '').toLowerCase().includes(kw) ||
        (a.relativePath || '').toLowerCase().includes(kw)
      )
    }
    
    albums.value = content
  } finally {
    loading.value = false
  }
}

const loadAllTags = async () => {
  try {
    const res = await api.get('/tags', { params: { page: 0, size: 1000 } })
    // 处理可能的分页数据格式
    if (Array.isArray(res.data)) {
      allTags.value = res.data
    } else if (res.data.content) {
      allTags.value = res.data.content
    } else {
      allTags.value = []
    }
    console.log('加载标签成功，共', allTags.value.length, '个标签')
  } catch (e) {
    console.error('加载标签失败:', e)
    allTags.value = []
  }
}

const extractRelativePath = (fullPath: string): string => {
  if (!fullPath) return ''
  // 尝试提取 base-path 之后的部分
  // 假设路径格式类似 /path/to/base/相册名称
  const parts = fullPath.split('/')
  // 返回最后两级路径，或根据实际情况调整
  if (parts.length > 2) {
    return parts.slice(-2).join('/')
  }
  return fullPath
}

const getPhotoUrl = (photo: any): string => {
  if (photo.webpPath) {
    return `/api/files${photo.webpPath}`
  }
  if (photo.thumbnailPath) {
    return `/api/files${photo.thumbnailPath}`
  }
  return `/api/files${photo.originalPath}`
}

const onImageError = (e: Event) => {
  const img = e.target as HTMLImageElement
  img.style.display = 'none'
}

const formatDate = (val?: string) => {
  if (!val) return '暂无日期'
  return val.slice(0, 10)
}

const addTag = async (album: any) => {
  currentAlbum.value = album
  tagKeyword.value = ''
  // 每次打开对话框时重新加载标签列表，确保数据是最新的
  await loadAllTags()
  tagDialogVisible.value = true
}

const searchTags = () => {
  // 标签会通过 filteredTags 自动过滤
}

const filteredTags = computed(() => {
  if (!tagKeyword.value.trim()) {
    return allTags.value
  }
  const kw = tagKeyword.value.trim().toLowerCase()
  return allTags.value.filter((t: any) => t.name.toLowerCase().includes(kw))
})

const selectTag = (tag: any) => {
  tagKeyword.value = tag.name
}

const confirmAddTag = async () => {
  if (!tagKeyword.value.trim() || !currentAlbum.value) {
    console.log('无法添加标签：缺少必要信息', { tagKeyword: tagKeyword.value, currentAlbum: currentAlbum.value })
    return
  }
  
  const tagName = tagKeyword.value.trim()
  console.log('开始添加标签:', tagName, '到相册:', currentAlbum.value.name)
  
  try {
    // 查找或创建标签
    let tag = allTags.value.find((t: any) => t.name === tagName)
    if (!tag) {
      console.log('标签不存在，创建新标签:', tagName)
      const res = await api.post('/tags', { name: tagName })
      tag = res.data
      console.log('创建标签成功:', tag)
      await loadAllTags()
  } else {
      console.log('使用已有标签:', tag)
    }
    
    // 为相册添加标签
    console.log('为相册添加标签，相册ID:', currentAlbum.value.id, '标签ID:', tag.id)
    await api.post(`/albums/${currentAlbum.value.id}/tags/${tag.id}`)
    console.log('添加标签成功')
    
    // 重新加载相册数据
    await load()
    tagDialogVisible.value = false
  } catch (e: any) {
    console.error('添加标签失败:', e)
    const errorMsg = e.response?.data?.message || e.response?.data?.error || e.message
    alert('添加标签失败: ' + errorMsg)
  }
}

const removeTag = async (album: any, tagId: number) => {
  if (!confirm('确定要移除这个标签吗？')) return
  
  try {
    await api.delete(`/albums/${album.id}/tags/${tagId}`)
    await load()
  } catch (e: any) {
    alert('移除标签失败: ' + (e.response?.data?.error || e.message))
  }
}

const editDescription = async (album: any) => {
  const newDesc = window.prompt('修改备注', album.description || '')
  if (newDesc === null) return
  
  try {
    await api.put(`/albums/${album.id}`, {
      name: album.name,
      description: newDesc
    })
    await load()
  } catch (e: any) {
    alert('修改备注失败: ' + (e.response?.data?.error || e.message))
  }
}

const editName = async (album: any) => {
  const newName = window.prompt('修改相册名称', album.name)
  if (newName === null || newName.trim() === '') return
  
  try {
    await api.put(`/albums/${album.id}`, {
      name: newName.trim(),
      description: album.description || ''
    })
  await load()
  } catch (e: any) {
    alert('修改名称失败: ' + (e.response?.data?.error || e.message))
  }
}

const deleteAlbum = async (album: any) => {
  if (!window.confirm(`确定删除相册"${album.displayTitle || album.name}"吗？`)) return
  
  try {
    await api.delete(`/albums/${album.id}`)
    await load()
  } catch (e: any) {
    alert('删除相册失败: ' + (e.response?.data?.error || e.message))
  }
}

const openContextMenu = (e: MouseEvent, album: any) => {
  contextMenu.value = {
    visible: true,
    x: e.clientX,
    y: e.clientY,
    album
  }
}

const closeContextMenu = () => {
  contextMenu.value.visible = false
  contextMenu.value.album = null
}

const handleMenuAddTag = () => {
  if (!contextMenu.value.album) return
  const album = contextMenu.value.album
  closeContextMenu()
  addTag(album)
}

const handleMenuEditDescription = () => {
  if (!contextMenu.value.album) return
  const album = contextMenu.value.album
  closeContextMenu()
  editDescription(album)
}

const handleMenuEditName = () => {
  if (!contextMenu.value.album) return
  const album = contextMenu.value.album
  closeContextMenu()
  editName(album)
}

const handleMenuDelete = () => {
  if (!contextMenu.value.album) return
  const album = contextMenu.value.album
  closeContextMenu()
  deleteAlbum(album)
}

const handleGlobalKeydown = (e: KeyboardEvent) => {
  if (e.key === 'Escape') {
    // 1. 先关闭右键菜单
    if (contextMenu.value.visible) {
      closeContextMenu()
      return
    }
    // 2. 再关闭标签弹窗
    if (tagDialogVisible.value) {
      tagDialogVisible.value = false
      return
    }
    // 3. 都没有时才返回后台首页
    router.push('/admin')
  }
}

onMounted(async () => {
  console.log('相册管理页面加载')
  await loadAllTags()
  await load()
  console.log('相册管理页面加载完成，相册数:', albums.value.length, '标签数:', allTags.value.length)
  window.addEventListener('keydown', handleGlobalKeydown)
  // 点击空白处关闭右键菜单
  window.addEventListener('click', closeContextMenu)
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', handleGlobalKeydown)
  window.removeEventListener('click', closeContextMenu)
})
</script>
