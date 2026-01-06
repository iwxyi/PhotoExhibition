<template>
  <div class="min-h-screen admin-shell text-white">
    <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8 space-y-8">
      <div class="flex items-center justify-between">
        <div>
          <h1 class="text-2xl font-light tracking-wide mb-1">系统设置</h1>
          <p class="text-sm text-gray-300">
            配置相册扫描和系统行为参数。
          </p>
        </div>
        <router-link
          to="/admin"
          class="px-4 py-2 bg-gray-900/70 hover:bg-gray-700 rounded-lg border border-white/10 transition-colors text-sm"
        >
          返回控制台
        </router-link>
      </div>

      <!-- 相册层级设置 -->
      <section class="glass-panel p-6 space-y-4">
        <div class="flex items-center justify-between flex-wrap gap-4">
          <div>
            <h2 class="text-lg font-light">最大相册层级</h2>
            <p class="text-xs text-gray-400">
              控制相册创建的层级深度，默认为1。超过此层级的子文件夹将不再创建独立相册，其中的图片会归属到上级相册。
            </p>
            <p class="text-xs text-gray-400 mt-1">
              路径结构：base-path/分类/顶级相册名/1级层级/2级层级/...，从"1级层级"开始计数。
            </p>
          </div>
          <div class="flex items-center gap-3">
            <input
              v-model="maxAlbumDepth"
              type="number"
              min="0"
              max="10"
              class="w-20 px-3 py-2 bg-gray-700 border border-gray-600 rounded-lg text-white text-center focus:outline-none focus:ring-2 focus:ring-blue-500"
              @input="maxAlbumDepth = Math.max(0, parseInt($event.target.value) || 0)"
            />
            <span class="text-xs text-gray-300">层级</span>
          </div>
        </div>

        <!-- 设置说明 -->
        <div class="bg-blue-900/20 border border-blue-500/30 rounded-lg p-4">
          <h3 class="text-sm font-medium text-blue-300 mb-2">层级说明</h3>
          <div class="text-xs text-gray-300 space-y-1">
            <p>• 层级为 0：只创建顶级相册，所有子文件夹的图片都归属到顶级相册</p>
            <p>• 层级为 1：创建到"1级层级"文件夹，2级及以下的所有图片都归属到"1级层级"相册</p>
            <p>• 层级为 2：创建到"2级层级"文件夹，3级及以下的所有图片都归属到"2级层级"相册</p>
          </div>
        </div>
      </section>

      <!-- 照片排序方式设置 -->
      <section class="glass-panel p-6 space-y-4">
        <div class="flex items-center justify-between flex-wrap gap-4">
          <div>
            <h2 class="text-lg font-light">照片排序方式</h2>
            <p class="text-xs text-gray-400">
              设置照片在相册和图墙中的显示顺序，影响所有相册的照片排序。
            </p>
            <p class="text-xs text-gray-400 mt-1">
              对所有照片进行全局排序，忽略下层文件夹的独立排序。
            </p>
          </div>
          <div class="flex items-center gap-3">
            <select
              v-model="photoSortOrder"
              class="px-4 py-2 bg-gray-700 border border-gray-600 rounded-lg text-white focus:outline-none focus:ring-2 focus:ring-blue-500"
            >
              <option value="taken_at_desc">拍摄时间倒序</option>
              <option value="taken_at_asc">拍摄时间正序</option>
              <option value="filename_desc">文件名倒序</option>
              <option value="filename_asc">文件名正序</option>
              <option value="created_at_desc">创建时间倒序</option>
              <option value="created_at_asc">创建时间正序</option>
            </select>
          </div>
        </div>

        <!-- 排序说明 -->
        <div class="bg-green-900/20 border border-green-500/30 rounded-lg p-4">
          <h3 class="text-sm font-medium text-green-300 mb-2">排序说明</h3>
          <div class="text-xs text-gray-300 space-y-1">
            <p>• 拍摄时间：按照片EXIF信息中的拍摄时间排序</p>
            <p>• 文件名：按照片文件名（不含扩展名）排序</p>
            <p>• 创建时间：按照片入库时间排序</p>
            <p>• 倒序：最新的/最大的排在前面，正序：最旧的/最小的排在前面</p>
          </div>
        </div>
      </section>

      <!-- 相册排序方式设置 -->
      <section class="glass-panel p-6 space-y-4">
        <div class="flex items-center justify-between flex-wrap gap-4">
          <div>
            <h2 class="text-lg font-light">相册排序方式</h2>
            <p class="text-xs text-gray-400">
              设置相册列表的显示顺序，影响相册卡片的排列。
            </p>
            <p class="text-xs text-gray-400 mt-1">
              控制相册在主页和相册列表页面的排序显示。
            </p>
          </div>
          <div class="flex items-center gap-3">
            <select
              v-model="albumSortOrder"
              class="px-4 py-2 bg-gray-700 border border-gray-600 rounded-lg text-white focus:outline-none focus:ring-2 focus:ring-blue-500"
            >
              <option value="name_asc">相册名称正序</option>
              <option value="name_desc">相册名称倒序</option>
              <option value="latest_photo_taken_desc">相册拍摄时间倒序</option>
              <option value="latest_photo_taken_asc">相册拍摄时间正序</option>
              <option value="album_name_date_desc">相册名时间倒序</option>
              <option value="album_name_date_asc">相册名时间正序</option>
              <option value="created_at_desc">创建时间倒序</option>
              <option value="created_at_asc">创建时间正序</option>
            </select>
          </div>
        </div>

        <!-- 排序说明 -->
        <div class="bg-blue-900/20 border border-blue-500/30 rounded-lg p-4">
          <h3 class="text-sm font-medium text-blue-300 mb-2">排序说明</h3>
          <div class="text-xs text-gray-300 space-y-1">
            <p>• 相册名称：按照相册文件夹名称排序</p>
            <p>• 相册拍摄时间：按照相册中最晚的照片拍摄时间排序（聚合相册包含所有子相册的照片）</p>
            <p>• 相册名时间：从相册名称或上级路径中解析的时间，支持嵌套继承（如：2025.01.01、2025-01-01）</p>
            <p>• 创建时间：按照相册首次创建的时间排序</p>
            <p>• 倒序：最新的/最大的排在前面，正序：最旧的/最小的排在前面</p>
          </div>
        </div>
      </section>

      <!-- 图墙排序方式设置 -->
      <section class="glass-panel p-6 space-y-4">
        <div class="flex items-center justify-between flex-wrap gap-4">
          <div>
            <h2 class="text-lg font-light">图墙排序方式</h2>
            <p class="text-xs text-gray-400">
              设置图墙页面的照片显示顺序，影响随机图墙和分类图墙的照片排列。
            </p>
            <p class="text-xs text-gray-400 mt-1">
              不同于相册内的照片排序，这是全局图墙的排序设置。
            </p>
          </div>
          <div class="flex items-center gap-3">
            <select
              v-model="wallSortOrder"
              class="px-4 py-2 bg-gray-700 border border-gray-600 rounded-lg text-white focus:outline-none focus:ring-2 focus:ring-blue-500"
            >
              <option value="taken_at_desc">拍摄时间倒序</option>
              <option value="taken_at_asc">拍摄时间正序</option>
              <option value="filename_desc">文件名倒序</option>
              <option value="filename_asc">文件名正序</option>
              <option value="created_at_desc">创建时间倒序</option>
              <option value="created_at_asc">创建时间正序</option>
            </select>
          </div>
        </div>

        <!-- 排序说明 -->
        <div class="bg-purple-900/20 border border-purple-500/30 rounded-lg p-4">
          <h3 class="text-sm font-medium text-purple-300 mb-2">排序说明</h3>
          <div class="text-xs text-gray-300 space-y-1">
            <p>• 拍摄时间：按照片EXIF信息中的拍摄时间排序</p>
            <p>• 文件名：按照片文件名（不含扩展名）排序</p>
            <p>• 创建时间：按照片入库时间排序</p>
            <p>• 倒序：最新的/最大的排在前面，正序：最旧的/最小的排在前面</p>
          </div>
        </div>
      </section>


      <!-- 重新扫描提示 -->
      <section v-if="settingsChanged" class="glass-panel p-6 space-y-4">
        <div class="flex items-center gap-3">
          <div class="flex-shrink-0 w-8 h-8 bg-yellow-500/20 rounded-full flex items-center justify-center">
            <svg class="w-4 h-4 text-yellow-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-2.5L13.732 4c-.77-.833-1.964-.833-2.732 0L4.082 16.5c-.77.833.192 2.5 1.732 2.5z" />
            </svg>
          </div>
          <div>
            <h3 class="text-lg font-light text-yellow-400">需要重新扫描</h3>
            <p class="text-sm text-gray-300">
              设置已修改，为确保相册结构正确，需要重新扫描整个相册库。
            </p>
          </div>
        </div>

        <div class="flex items-center gap-3">
          <button
            @click="triggerForceScan"
            :disabled="scanning"
            class="px-4 py-2 bg-green-600 hover:bg-green-700 disabled:opacity-50 disabled:cursor-not-allowed rounded-lg transition-colors text-sm"
          >
            {{ scanning ? '扫描中...' : '立即重新扫描' }}
          </button>
          <span class="text-xs text-gray-400">
            这将根据新设置重建所有相册结构，可能需要较长时间
          </span>
        </div>
      </section>

      <!-- 配置说明 -->
      <section class="glass-panel p-4 text-xs text-gray-300 space-y-2">
        <p>• 修改最大相册层级后，必须重新扫描才能生效。</p>
        <p>• 重新扫描会根据新的层级设置重建相册结构，已有的相册可能被合并或删除。</p>
        <p>• 如果相册数量变化较大，建议在访问量较小的时间段进行操作。</p>
      </section>

      <!-- 保存按钮 -->
      <div class="glass-panel p-6 sticky bottom-0 z-10 bg-gray-900/95 backdrop-blur-sm border-t border-gray-700/50">
        <div class="flex items-center justify-end">
          <button
            @click="saveSettings"
            :disabled="saving"
            class="px-6 py-2 bg-blue-600 hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed rounded-lg transition-colors"
          >
            {{ saving ? '保存中...' : '保存设置' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '@/api'

const router = useRouter()

const maxAlbumDepth = ref(1)
const originalMaxAlbumDepth = ref(1)
const photoSortOrder = ref('taken_at_desc')
const originalPhotoSortOrder = ref('taken_at_desc')
const albumSortOrder = ref('name_asc')
const originalAlbumSortOrder = ref('name_asc')
const wallSortOrder = ref('taken_at_desc')
const originalWallSortOrder = ref('taken_at_desc')
const saving = ref(false)
const scanning = ref(false)
const settingsChanged = ref(false)

const loadSettings = async () => {
  try {
    const response = await api.get('/admin/config')
    maxAlbumDepth.value = response.data.maxAlbumDepth
    originalMaxAlbumDepth.value = response.data.maxAlbumDepth
    photoSortOrder.value = response.data.photoSortOrder
    originalPhotoSortOrder.value = response.data.photoSortOrder
    albumSortOrder.value = response.data.albumSortOrder || 'name_asc'
    originalAlbumSortOrder.value = response.data.albumSortOrder || 'name_asc'
    wallSortOrder.value = response.data.wallSortOrder || 'taken_at_desc'
    originalWallSortOrder.value = response.data.wallSortOrder || 'taken_at_desc'
    settingsChanged.value = false
  } catch (error) {
    console.error('加载设置失败:', error)
    alert('加载设置失败')
  }
}

const saveSettings = async () => {
  if (maxAlbumDepth.value < 0) {
    alert('最大相册层级不能为负数')
    return
  }

  // 如果设置发生变化，提醒用户需要重新扫描
  const albumDepthChanged = maxAlbumDepth.value !== originalMaxAlbumDepth.value
  const photoSortChanged = photoSortOrder.value !== originalPhotoSortOrder.value
  const albumSortChanged = albumSortOrder.value !== originalAlbumSortOrder.value
  const wallSortChanged = wallSortOrder.value !== originalWallSortOrder.value

  if (albumDepthChanged || photoSortChanged || albumSortChanged || wallSortChanged) {
    let message = '⚠️ 设置已修改 ⚠️\n\n'

    if (albumDepthChanged) {
      message += `最大相册层级将从 ${originalMaxAlbumDepth.value} 改为 ${maxAlbumDepth.value}\n`
    }
    if (photoSortChanged) {
      const oldSortName = getSortOrderName(originalPhotoSortOrder.value)
      const newSortName = getSortOrderName(photoSortOrder.value)
      message += `照片排序方式将从 "${oldSortName}" 改为 "${newSortName}"\n`
    }
    if (albumSortChanged) {
      const oldAlbumSortName = getSortOrderName(originalAlbumSortOrder.value)
      const newAlbumSortName = getSortOrderName(albumSortOrder.value)
      message += `相册排序方式将从 "${oldAlbumSortName}" 改为 "${newAlbumSortName}"\n`
    }
    if (wallSortChanged) {
      const oldWallSortName = getSortOrderName(originalWallSortOrder.value)
      const newWallSortName = getSortOrderName(wallSortOrder.value)
      message += `图墙排序方式将从 "${oldWallSortName}" 改为 "${newWallSortName}"\n`
    }

    message += '\n下次扫描时，相册结构将根据新设置重新构建。\n'
    if (albumDepthChanged) {
      message += '超出新层级限制的相册将被删除，其照片将归属到上级相册。\n'
    }
    message += '\n建议立即进行重新扫描以应用新设置。\n\n确定要保存吗？'

    const confirmed = confirm(message)
    if (!confirmed) {
      return
    }
  }

  saving.value = true
  try {
    // 保存最大相册层级
    if (albumDepthChanged) {
      await api.put('/admin/config/max-album-depth', {
        maxAlbumDepth: maxAlbumDepth.value
      })
    }

    // 保存照片排序方式
    if (photoSortChanged) {
      await api.put('/admin/config/photo-sort-order', {
        photoSortOrder: photoSortOrder.value
      })
    }

    // 保存相册排序方式
    if (albumSortChanged) {
      await api.put('/admin/config/album-sort-order', {
        albumSortOrder: albumSortOrder.value
      })
    }

    // 保存图墙排序方式
    if (wallSortChanged) {
      await api.put('/admin/config/wall-sort-order', {
        wallSortOrder: wallSortOrder.value
      })
    }

    // 设置保存成功，不再显示alert弹窗
    originalMaxAlbumDepth.value = maxAlbumDepth.value
    originalPhotoSortOrder.value = photoSortOrder.value
    originalAlbumSortOrder.value = albumSortOrder.value
    originalWallSortOrder.value = wallSortOrder.value
    settingsChanged.value = maxAlbumDepth.value !== originalMaxAlbumDepth.value ||
                           photoSortOrder.value !== originalPhotoSortOrder.value ||
                           albumSortOrder.value !== originalAlbumSortOrder.value ||
                           wallSortOrder.value !== originalWallSortOrder.value
  } catch (error: any) {
    alert('保存设置失败: ' + (error.response?.data?.error || error.message))
  } finally {
    saving.value = false
  }
}

const triggerForceScan = async () => {
  if (!confirm('⚠️ 确认重新扫描\n\n这将根据新的层级设置重建所有相册，可能需要较长时间。\n确定要继续吗？')) {
    return
  }

  scanning.value = true
  try {
    await api.post('/admin/scan/force')
    alert('重新扫描任务已启动，请稍后查看控制台的扫描状态')
    settingsChanged.value = false
  } catch (error: any) {
    alert('启动扫描失败: ' + (error.response?.data?.message || error.message))
  } finally {
    scanning.value = false
  }
}

// 获取排序方式的显示名称
const getSortOrderName = (sortOrder: string): string => {
  const names: { [key: string]: string } = {
    'taken_at_desc': '拍摄时间倒序',
    'taken_at_asc': '拍摄时间正序',
    'filename_desc': '文件名倒序',
    'filename_asc': '文件名正序',
    'created_at_desc': '创建时间倒序',
    'created_at_asc': '创建时间正序',
    'name_desc': '相册名称倒序',
    'name_asc': '相册名称正序',
    'latest_photo_taken_desc': '相册拍摄时间倒序',
    'latest_photo_taken_asc': '相册拍摄时间正序',
    'album_name_date_desc': '相册名时间倒序',
    'album_name_date_asc': '相册名时间正序'
  }
  return names[sortOrder] || '拍摄时间倒序'
}

// 监听设置变化
const checkSettingsChanged = () => {
  settingsChanged.value = maxAlbumDepth.value !== originalMaxAlbumDepth.value ||
                         photoSortOrder.value !== originalPhotoSortOrder.value ||
                         albumSortOrder.value !== originalAlbumSortOrder.value ||
                         wallSortOrder.value !== originalWallSortOrder.value
}

onMounted(() => {
  loadSettings()
})
</script>
