<template>
  <div class="min-h-screen admin-shell admin-settings-shell">
    <AdminStyleChrome />
    <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 admin-settings-page">
      <section class="glass-panel admin-settings-group">
        <h2 class="admin-settings-group-title">展示与内容</h2>
        <div class="admin-settings-group-list">
          <div class="admin-settings-row">
            <label class="admin-settings-row-label" for="album-sort-order">相册排序方式</label>
            <div class="admin-settings-control">
            <select
              id="album-sort-order"
              v-model="albumSortOrder"
              class="admin-field admin-settings-select focus:outline-none focus:ring-2 focus:ring-blue-500"
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

          <div class="admin-settings-row">
            <label class="admin-settings-row-label" for="photo-sort-order">照片排序方式</label>
            <div class="admin-settings-control">
            <select
              id="photo-sort-order"
              v-model="photoSortOrder"
              class="admin-field admin-settings-select focus:outline-none focus:ring-2 focus:ring-blue-500"
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

          <div class="admin-settings-row">
            <label class="admin-settings-row-label" for="wall-sort-order">图墙排序方式</label>
            <div class="admin-settings-control">
            <select
              id="wall-sort-order"
              v-model="wallSortOrder"
              class="admin-field admin-settings-select focus:outline-none focus:ring-2 focus:ring-blue-500"
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

          <div class="admin-settings-row">
            <label class="admin-settings-row-label" for="max-album-depth">最大相册层级</label>
            <div class="admin-settings-control admin-settings-control--inline">
            <input
              id="max-album-depth"
              v-model="maxAlbumDepth"
              type="number"
              min="0"
              max="10"
              class="admin-field admin-settings-number-field focus:outline-none focus:ring-2 focus:ring-blue-500"
              @input="maxAlbumDepth = Math.max(0, parseInt($event.target.value) || 0)"
            />
            <span class="text-xs admin-settings-inline-label">层级</span>
            </div>
          </div>

          <div class="admin-settings-row">
            <label class="admin-settings-row-label" for="min-cluster-face-count">聚类显示最小人脸数量</label>
            <div class="admin-settings-control admin-settings-control--inline">
            <input
              id="min-cluster-face-count"
              v-model="minClusterFaceCount"
              type="number"
              min="1"
              max="10"
              class="admin-field admin-settings-number-field focus:outline-none focus:ring-2 focus:ring-blue-500"
              @input="minClusterFaceCount = Math.max(1, Math.min(10, parseInt($event.target.value) || 1))"
            />
            <span class="text-xs admin-settings-inline-label">人脸</span>
            </div>
          </div>

          <div class="admin-settings-row">
            <span class="admin-settings-row-label">全局下载权限</span>
            <div class="admin-settings-control admin-settings-control--inline">
            <label class="admin-settings-toggle cursor-pointer">
              <input
                type="checkbox"
                v-model="globalDownloadAllowed"
                class="admin-settings-toggle-input"
              />
              <span class="text-sm admin-settings-toggle-text">允许下载</span>
            </label>
            </div>
          </div>

          <div class="admin-settings-row">
            <label class="admin-settings-row-label" for="album-category-sort-order">相册分类排序</label>
            <div class="admin-settings-control">
              <input
                id="album-category-sort-order"
                v-model="albumCategorySortOrder"
                type="text"
                placeholder="例如：人像,风景 静物 或 人像，风景，静物"
                class="admin-field admin-settings-input focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
            </div>
          </div>

          <div class="admin-settings-row">
            <label class="admin-settings-row-label" for="tag-ignore-list">标签忽略列表</label>
            <div class="admin-settings-control">
              <input
                id="tag-ignore-list"
                v-model="tagIgnoreList"
                type="text"
                placeholder="例如：横图 竖图 全景 或 横图，竖图，全景"
                class="admin-field admin-settings-input focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
            </div>
          </div>
        </div>
      </section>

      <section class="glass-panel admin-settings-group">
        <div class="admin-settings-group-heading">
          <h2 class="admin-settings-group-title">智能能力</h2>
          <label class="admin-settings-toggle cursor-pointer">
            <input
              type="checkbox"
              v-model="aiSearchEnabled"
              class="admin-settings-toggle-input"
            />
            <span class="text-sm admin-settings-toggle-text">{{ aiSearchEnabled ? '已启用' : '已关闭' }}</span>
          </label>
        </div>

        <div v-if="aiSearchEnabled" class="admin-settings-ai-grid">
          <label class="admin-settings-field-stack">
            <span class="admin-settings-field-label">API 地址</span>
            <input
              v-model="aiSearchApiUrl"
              type="text"
              placeholder="https://api.openai.com/v1"
              class="admin-field admin-settings-input focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </label>
          <label class="admin-settings-field-stack">
            <span class="admin-settings-field-label">API 密钥</span>
            <input
              v-model="aiSearchApiKey"
              type="password"
              placeholder="sk-..."
              class="admin-field admin-settings-input focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </label>
          <label class="admin-settings-field-stack">
            <span class="admin-settings-field-label">模型名称</span>
            <input
              v-model="aiSearchModel"
              type="text"
              placeholder="gpt-4o"
              class="admin-field admin-settings-input focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </label>
        </div>
      </section>

      <section class="glass-panel admin-settings-group">
        <h2 class="admin-settings-group-title">账户安全</h2>
        <div class="admin-settings-account-grid">
          <section class="admin-settings-account-section">
            <h3 class="admin-settings-account-title">用户名</h3>
            <div class="admin-settings-security-fields">
              <label class="admin-settings-field-stack">
                <span class="admin-settings-field-label">当前用户名</span>
              <input
                v-model="currentUsername"
                type="text"
                class="admin-field admin-settings-input focus:outline-none focus:ring-2 focus:ring-green-500"
                placeholder="当前管理员用户名"
                readonly
              />
              </label>
              <label class="admin-settings-field-stack">
                <span class="admin-settings-field-label">新用户名</span>
              <input
                v-model="newUsername"
                type="text"
                class="admin-field admin-settings-input focus:outline-none focus:ring-2 focus:ring-green-500"
                placeholder="新用户名"
                :class="{ 'border-red-500': newUsername && !isValidUsername }"
              />
              <span v-if="newUsername && !isValidUsername" class="admin-settings-validation-error">
                用户名格式不正确（3-50字符，只能包含字母、数字、下划线、连字符）
              </span>
              </label>
              <label class="admin-settings-field-stack">
                <span class="admin-settings-field-label">验证密码</span>
              <input
                v-model="usernameChangePassword"
                type="password"
                class="admin-field admin-settings-input focus:outline-none focus:ring-2 focus:ring-green-500"
                placeholder="当前密码"
              />
              </label>
            </div>
            <button
              @click="changeUsername"
              :disabled="!canChangeUsername || changingUsername"
              class="admin-button-primary admin-settings-section-action transition-colors disabled:cursor-not-allowed"
            >
              {{ changingUsername ? '修改中...' : '更改用户名' }}
            </button>
          </section>

          <section class="admin-settings-account-section">
            <h3 class="admin-settings-account-title">密码</h3>
            <div class="admin-settings-security-fields">
              <label class="admin-settings-field-stack">
                <span class="admin-settings-field-label">当前密码</span>
              <input
                v-model="currentPassword"
                type="password"
                class="admin-field admin-settings-input focus:outline-none focus:ring-2 focus:ring-blue-500"
                placeholder="当前密码"
              />
              </label>
              <label class="admin-settings-field-stack">
                <span class="admin-settings-field-label">新密码</span>
              <input
                v-model="newPassword"
                type="password"
                class="admin-field admin-settings-input focus:outline-none focus:ring-2 focus:ring-blue-500"
                placeholder="新密码"
              />
              </label>
              <label class="admin-settings-field-stack">
                <span class="admin-settings-field-label">确认新密码</span>
              <input
                v-model="confirmPassword"
                type="password"
                class="admin-field admin-settings-input focus:outline-none focus:ring-2 focus:ring-blue-500"
                placeholder="再次输入新密码"
              />
              </label>
            </div>
            <button
              @click="changePassword"
              :disabled="!canChangePassword || changingPassword"
              class="admin-button-primary admin-settings-section-action transition-colors disabled:cursor-not-allowed"
            >
              {{ changingPassword ? '修改中...' : '修改密码' }}
            </button>
          </section>
        </div>
      </section>

      <!-- 重新扫描提示 -->
      <section v-if="settingsChanged" class="glass-panel p-6 space-y-4 admin-settings-block">
        <div class="flex items-center gap-3">
          <div class="admin-settings-warning-icon flex-shrink-0 w-8 h-8 rounded-full flex items-center justify-center">
            <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-2.5L13.732 4c-.77-.833-1.964-.833-2.732 0L4.082 16.5c-.77.833.192 2.5 1.732 2.5z" />
            </svg>
          </div>
          <div>
            <h3 class="text-lg font-light admin-settings-warning-title">需要重新扫描</h3>
            <p class="text-sm admin-settings-warning-copy">
              设置已修改，需要重建相册结构。
            </p>
          </div>
        </div>

        <div class="flex items-center gap-3 flex-wrap">
          <button
            v-if="authStore.isSuperAdmin"
            @click="triggerForceScan"
            :disabled="scanning"
            class="admin-button-primary px-4 py-2 rounded-lg transition-colors text-sm disabled:opacity-50 disabled:cursor-not-allowed"
          >
            {{ scanning ? '扫描中...' : '重新扫描' }}
          </button>
          <span v-else class="text-sm admin-settings-warning-copy">
            普通用户不能主动重新扫描，请等待系统按队列自动处理。
          </span>
        </div>
      </section>

      <!-- 保存按钮 -->
      <div class="glass-panel sticky bottom-0 z-10 admin-settings-footer">
        <div class="flex items-center justify-end">
          <button
            @click="saveSettings"
            :disabled="saving"
            class="admin-button-primary px-6 py-2 rounded-lg transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
          >
            {{ saving ? '保存中...' : '保存设置' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import AdminStyleChrome from '@/components/admin/AdminStyleChrome.vue'
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { api, getEffectiveAuthToken } from '@/api'
import { useAuthStore } from '@/stores/auth'
const router = useRouter()
const authStore = useAuthStore()

const maxAlbumDepth = ref(1)
const originalMaxAlbumDepth = ref(1)
const photoSortOrder = ref('taken_at_desc')
const originalPhotoSortOrder = ref('taken_at_desc')
const albumSortOrder = ref('name_asc')
const originalAlbumSortOrder = ref('name_asc')
const wallSortOrder = ref('taken_at_desc')
const originalWallSortOrder = ref('taken_at_desc')
const needsWallRefresh = ref(false)
const minClusterFaceCount = ref(2)
const originalMinClusterFaceCount = ref(2)
const globalDownloadAllowed = ref(false)
const originalGlobalDownloadAllowed = ref(false)
const albumCategorySortOrder = ref('')
const originalAlbumCategorySortOrder = ref('')
const tagIgnoreList = ref('')
const originalTagIgnoreList = ref('')
const saving = ref(false)
const scanning = ref(false)
const settingsChanged = ref(false)

// AI搜索相关
const aiSearchEnabled = ref(false)
const originalAiSearchEnabled = ref(false)
const aiSearchApiUrl = ref('')
const originalAiSearchApiUrl = ref('')
const aiSearchApiKey = ref('')
const originalAiSearchApiKey = ref('')
const aiSearchModel = ref('gpt-4o')
const originalAiSearchModel = ref('gpt-4o')

// 密码修改相关
const currentPassword = ref('')
const newPassword = ref('')
const confirmPassword = ref('')
const changingPassword = ref(false)

// 用户名更改相关
const currentUsername = ref('')
const newUsername = ref('')
const usernameChangePassword = ref('')
const changingUsername = ref(false)

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
    minClusterFaceCount.value = response.data.minClusterFaceCount || 2
    originalMinClusterFaceCount.value = response.data.minClusterFaceCount || 2
    globalDownloadAllowed.value = response.data.globalDownloadAllowed === true // 默认为false
    originalGlobalDownloadAllowed.value = response.data.globalDownloadAllowed === true
    albumCategorySortOrder.value = response.data.albumCategorySortOrder || ''
    originalAlbumCategorySortOrder.value = response.data.albumCategorySortOrder || ''
    tagIgnoreList.value = response.data.tagIgnoreList || ''
    originalTagIgnoreList.value = response.data.tagIgnoreList || ''
    aiSearchEnabled.value = response.data.aiSearchEnabled === true
    originalAiSearchEnabled.value = response.data.aiSearchEnabled === true
    aiSearchApiUrl.value = response.data.aiSearchApiUrl || ''
    originalAiSearchApiUrl.value = response.data.aiSearchApiUrl || ''
    aiSearchApiKey.value = response.data.aiSearchApiKey || ''
    originalAiSearchApiKey.value = response.data.aiSearchApiKey || ''
    aiSearchModel.value = response.data.aiSearchModel || 'gpt-4o'
    originalAiSearchModel.value = response.data.aiSearchModel || 'gpt-4o'
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
  const clusterFaceCountChanged = minClusterFaceCount.value !== originalMinClusterFaceCount.value
  const globalDownloadChanged = globalDownloadAllowed.value !== originalGlobalDownloadAllowed.value
  const albumCategorySortChanged = albumCategorySortOrder.value !== originalAlbumCategorySortOrder.value
  const tagIgnoreListChanged = tagIgnoreList.value !== originalTagIgnoreList.value

  if (albumDepthChanged || photoSortChanged || albumSortChanged || wallSortChanged || clusterFaceCountChanged) {
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
    if (clusterFaceCountChanged) {
      message += `聚类最小人脸数量将从 ${originalMinClusterFaceCount.value} 改为 ${minClusterFaceCount.value}\n`
    }
    if (globalDownloadChanged) {
      const oldStatus = originalGlobalDownloadAllowed.value ? '允许' : '禁止'
      const newStatus = globalDownloadAllowed.value ? '允许' : '禁止'
      message += `全局下载权限将从 "${oldStatus}" 改为 "${newStatus}"\n`
    }
    if (albumCategorySortChanged) {
      message += `相册分类排序将从 "${originalAlbumCategorySortOrder.value}" 改为 "${albumCategorySortOrder.value}"\n`
    }
    if (tagIgnoreListChanged) {
      message += `标签忽略列表将从 "${originalTagIgnoreList.value}" 改为 "${tagIgnoreList.value}"\n`
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
      // 标记需要刷新图墙
      needsWallRefresh.value = true
    }

    // 保存聚类最小人脸数量
    if (clusterFaceCountChanged) {
      await api.put('/admin/config/min-cluster-face-count', {
        minClusterFaceCount: minClusterFaceCount.value
      })
    }

    // 保存全局下载权限
    if (globalDownloadChanged) {
      await api.put('/admin/config/global-download-allowed', {
        globalDownloadAllowed: globalDownloadAllowed.value
      })
    }

    // 保存相册分类排序
    if (albumCategorySortChanged) {
      await api.put('/admin/config/album-category-sort-order', {
        albumCategorySortOrder: albumCategorySortOrder.value
      })
    }

    // 保存标签忽略列表
    if (tagIgnoreListChanged) {
      await api.put('/admin/config/tag-ignore-list', {
        tagIgnoreList: tagIgnoreList.value
      })
    }

    // 保存AI搜索配置
    const aiEnabledChanged = aiSearchEnabled.value !== originalAiSearchEnabled.value
    const aiUrlChanged = aiSearchApiUrl.value !== originalAiSearchApiUrl.value
    const aiKeyChanged = aiSearchApiKey.value !== originalAiSearchApiKey.value && !aiSearchApiKey.value.includes('****')
    const aiModelChanged = aiSearchModel.value !== originalAiSearchModel.value

    if (aiEnabledChanged) {
      await api.put('/admin/config/ai-search-enabled', { aiSearchEnabled: aiSearchEnabled.value })
    }
    if (aiUrlChanged) {
      await api.put('/admin/config/ai-search-api-url', { aiSearchApiUrl: aiSearchApiUrl.value })
    }
    if (aiKeyChanged) {
      await api.put('/admin/config/ai-search-api-key', { aiSearchApiKey: aiSearchApiKey.value })
    }
    if (aiModelChanged) {
      await api.put('/admin/config/ai-search-model', { aiSearchModel: aiSearchModel.value })
    }

    // 设置保存成功，显示提示
    originalMaxAlbumDepth.value = maxAlbumDepth.value
    originalPhotoSortOrder.value = photoSortOrder.value
    originalAlbumSortOrder.value = albumSortOrder.value
    originalWallSortOrder.value = wallSortOrder.value
    originalMinClusterFaceCount.value = minClusterFaceCount.value
    originalGlobalDownloadAllowed.value = globalDownloadAllowed.value
    originalAlbumCategorySortOrder.value = albumCategorySortOrder.value
    originalTagIgnoreList.value = tagIgnoreList.value
    if (aiEnabledChanged) originalAiSearchEnabled.value = aiSearchEnabled.value
    if (aiUrlChanged) originalAiSearchApiUrl.value = aiSearchApiUrl.value
    if (aiKeyChanged) originalAiSearchApiKey.value = aiSearchApiKey.value
    if (aiModelChanged) originalAiSearchModel.value = aiSearchModel.value
    settingsChanged.value = maxAlbumDepth.value !== originalMaxAlbumDepth.value ||
                           photoSortOrder.value !== originalPhotoSortOrder.value ||
                           albumSortOrder.value !== originalAlbumSortOrder.value ||
                           wallSortOrder.value !== originalWallSortOrder.value ||
                           minClusterFaceCount.value !== originalMinClusterFaceCount.value ||
                           globalDownloadAllowed.value !== originalGlobalDownloadAllowed.value ||
                           albumCategorySortOrder.value !== originalAlbumCategorySortOrder.value ||
                           tagIgnoreList.value !== originalTagIgnoreList.value

    // 显示保存成功的提示
    if (needsWallRefresh.value) {
      alert('✅ 设置保存成功！\n\n图墙排序已更新，请刷新图墙页面查看效果。\n建议使用 Ctrl+F5 强制刷新以清除缓存。')
      needsWallRefresh.value = false
    } else {
      alert('✅ 设置保存成功！')
    }
  } catch (error: any) {
    alert('保存设置失败: ' + (error.response?.data?.error || error.message))
  } finally {
    saving.value = false
  }
}

const triggerForceScan = async () => {
  if (!authStore.isSuperAdmin) {
    alert('普通用户不能主动重新扫描，请等待系统按队列自动处理。')
    return
  }
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

// EXIF 更新由 API 测试工具触发

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

// 密码修改相关方法
const canChangePassword = computed(() => {
  return currentPassword.value.trim() &&
         newPassword.value.trim() &&
         confirmPassword.value.trim() &&
         newPassword.value === confirmPassword.value &&
         newPassword.value.length >= 6
})

// 用户名更改相关方法
const isValidUsername = computed(() => {
  const username = newUsername.value.trim()
  return username.length >= 3 && username.length <= 50 && /^[a-zA-Z0-9_-]+$/.test(username)
})

const canChangeUsername = computed(() => {
  return currentUsername.value.trim() &&
         newUsername.value.trim() &&
         usernameChangePassword.value.trim() &&
         isValidUsername.value &&
         newUsername.value !== currentUsername.value
})

const changePassword = async () => {
  if (!canChangePassword.value) {
    alert('请检查密码输入是否正确')
    return
  }

  if (!confirm('确定要修改密码吗？修改成功后需要重新登录。')) {
    return
  }

  changingPassword.value = true
  try {
    const response = await api.post('/admin/change-password', {
      username: currentUsername.value,
      oldPassword: currentPassword.value,
      newPassword: newPassword.value
    })

    alert('✅ 密码修改成功！请重新登录。')

    // 清空表单
    currentPassword.value = ''
    newPassword.value = ''
    confirmPassword.value = ''

    // 退出登录
    localStorage.removeItem('auth_token')
    localStorage.removeItem('auth_username')
    localStorage.removeItem('auth_role')
    localStorage.removeItem('auth_user_id')
    localStorage.removeItem('auth_slug')
    localStorage.removeItem('admin_token')
    localStorage.removeItem('admin_username')
    router.push('/admin/login')

  } catch (error: any) {
    const errorMsg = error.response?.data?.error || '密码修改失败'
    alert('❌ ' + errorMsg)
  } finally {
    changingPassword.value = false
  }
}

const changeUsername = async () => {
  if (!canChangeUsername.value) {
    alert('请检查用户名输入是否正确')
    return
  }

  if (!confirm('确定要更改用户名吗？更改后将自动重新登录系统。')) {
    return
  }

  changingUsername.value = true
  try {
    const response = await api.post('/admin/change-username', {
      currentUsername: currentUsername.value,
      newUsername: newUsername.value.trim(),
      password: usernameChangePassword.value
    })

    alert('✅ 用户名更改成功！系统将自动重新登录。')

    // 更新本地存储的token
    if (response.data.token) {
      localStorage.setItem('auth_token', response.data.token)
      localStorage.setItem('admin_token', response.data.token)
    }
    localStorage.setItem('auth_username', newUsername.value.trim())
    localStorage.setItem('admin_username', newUsername.value.trim())

    // 清空表单
    newUsername.value = ''
    usernameChangePassword.value = ''

    // 重新加载页面以刷新用户信息
    window.location.reload()

  } catch (error: any) {
    const errorMsg = error.response?.data?.error || '用户名更改失败'
    alert('❌ ' + errorMsg)
  } finally {
    changingUsername.value = false
  }
}

// 初始化当前用户名
const initCurrentUsername = () => {
  // 从localStorage或API获取当前用户名
  // 这里可以从已有的用户信息中获取，或者从token中解析
  const token = getEffectiveAuthToken()
  if (token) {
    try {
      // 简单解析JWT token获取用户名（实际项目中建议使用专门的解析方法）
      const payload = JSON.parse(atob(token.split('.')[1]))
      currentUsername.value = payload.sub || ''
    } catch (e) {
      console.error('解析token失败:', e)
    }
  }
}

onMounted(() => {
  loadSettings()
  initCurrentUsername()
})
</script>
