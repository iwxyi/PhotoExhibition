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

      <!-- 人脸聚类设置 -->
      <section class="glass-panel p-6 space-y-4">
        <div class="flex items-center justify-between flex-wrap gap-4">
          <div>
            <h2 class="text-lg font-light">聚类显示最小人脸数量</h2>
            <p class="text-xs text-gray-400">
              设置人物管理页面中聚类结果的最小显示人脸数量，人脸数量少于此值的聚类将不显示。
            </p>
            <p class="text-xs text-gray-400 mt-1">
              提高性能：过滤掉过小的聚类，减少计算量；保证准确性：避免遗漏潜在人物。
            </p>
          </div>
          <div class="flex items-center gap-3">
            <input
              v-model="minClusterFaceCount"
              type="number"
              min="1"
              max="10"
              class="w-20 px-3 py-2 bg-gray-700 border border-gray-600 rounded-lg text-white text-center focus:outline-none focus:ring-2 focus:ring-blue-500"
              @input="minClusterFaceCount = Math.max(1, Math.min(10, parseInt($event.target.value) || 1))"
            />
            <span class="text-xs text-gray-300">人脸</span>
          </div>
        </div>

        <!-- 设置说明 -->
        <div class="bg-green-900/20 border border-green-500/30 rounded-lg p-4">
          <h3 class="text-sm font-medium text-green-300 mb-2">性能与准确性平衡</h3>
          <div class="text-xs text-gray-300 space-y-1">
            <p>• 设为 1：显示所有聚类，包括单人脸聚类，准确性最高但性能较慢</p>
            <p>• 设为 2：过滤掉单人脸聚类，性能提升但可能遗漏一些人物</p>
            <p>• 设为 3+：只显示多人聚类，性能最佳但准确性降低</p>
            <p>• 推荐值：2（平衡性能和准确性的最佳选择）</p>
          </div>
        </div>
      </section>

      <!-- 全局下载权限设置 -->
      <section class="glass-panel p-6 space-y-4">
        <div class="flex items-center justify-between flex-wrap gap-4">
          <div>
            <h2 class="text-lg font-light">全局下载权限</h2>
            <p class="text-xs text-gray-400">
              控制是否允许用户下载相册中的图片。
            </p>
            <p class="text-xs text-gray-400 mt-1">
              全局设置可以被单个相册的设置覆盖。
            </p>
          </div>
          <div class="flex items-center gap-3">
            <label class="flex items-center gap-2 cursor-pointer">
              <input
                type="checkbox"
                v-model="globalDownloadAllowed"
                class="w-4 h-4 text-blue-600 bg-gray-700 border-gray-600 rounded focus:ring-blue-500 focus:ring-2"
              />
              <span class="text-sm">允许下载</span>
            </label>
          </div>
        </div>
      </section>

      <!-- 相册分类排序设置 -->
      <section class="glass-panel p-6 space-y-4">
        <div class="flex items-center justify-between flex-wrap gap-4">
          <div>
            <h2 class="text-lg font-light">相册分类排序</h2>
            <p class="text-xs text-gray-400">
              设置相册分类的显示顺序，用逗号、空格等分隔多个分类名称。
            </p>
            <p class="text-xs text-gray-400 mt-1">
              未在排序中的分类将自动排在后面。
            </p>
          </div>
          <div class="flex items-center gap-3 min-w-[300px]">
            <input
              v-model="albumCategorySortOrder"
              type="text"
              placeholder="例如：人像,风景 静物 或 人像，风景，静物"
              class="flex-1 px-3 py-2 bg-gray-700 border border-gray-600 rounded-lg text-white placeholder-gray-400 focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>
        </div>
      </section>

      <!-- 标签忽略列表设置 -->
      <section class="glass-panel p-6 space-y-4">
        <div class="flex items-center justify-between flex-wrap gap-4">
          <div>
            <h2 class="text-lg font-light">标签忽略列表</h2>
            <p class="text-xs text-gray-400">
              设置在筛选功能中隐藏的标签，用空格或逗号分隔多个标签名称。
            </p>
            <p class="text-xs text-gray-400 mt-1">
              被忽略的标签不会在 PhotoViewer、图墙筛选等界面中显示。
            </p>
          </div>
          <div class="flex items-center gap-3 min-w-[300px]">
            <input
              v-model="tagIgnoreList"
              type="text"
              placeholder="例如：横图 竖图 全景 或 横图，竖图，全景"
              class="flex-1 px-3 py-2 bg-gray-700 border border-gray-600 rounded-lg text-white placeholder-gray-400 focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>
        </div>
      </section>

      <!-- 用户名更改设置 -->
      <section class="glass-panel p-6 space-y-4">
        <div class="flex items-center justify-between flex-wrap gap-4">
          <div>
            <h2 class="text-lg font-light">更改管理员用户名</h2>
            <p class="text-xs text-gray-400">
              修改当前管理员账户的用户名，用于登录系统。
            </p>
            <p class="text-xs text-gray-400 mt-1">
              用户名长度3-50个字符，只能包含字母、数字、下划线和连字符。
            </p>
          </div>
        </div>

        <!-- 用户名更改表单 -->
        <div class="mt-6 space-y-4">
          <div class="grid grid-cols-1 md:grid-cols-3 gap-4">
            <div>
              <label class="block text-sm font-medium text-gray-300 mb-2">当前用户名</label>
              <input
                v-model="currentUsername"
                type="text"
                class="w-full px-3 py-2 bg-gray-700 border border-gray-600 rounded-lg text-white focus:outline-none focus:ring-2 focus:ring-green-500"
                placeholder="输入当前用户名"
                readonly
              />
            </div>
            <div>
              <label class="block text-sm font-medium text-gray-300 mb-2">新用户名</label>
              <input
                v-model="newUsername"
                type="text"
                class="w-full px-3 py-2 bg-gray-700 border border-gray-600 rounded-lg text-white focus:outline-none focus:ring-2 focus:ring-green-500"
                placeholder="输入新用户名"
                :class="{ 'border-red-500': newUsername && !isValidUsername }"
              />
              <p v-if="newUsername && !isValidUsername" class="text-xs text-red-400 mt-1">
                用户名格式不正确（3-50字符，只能包含字母、数字、下划线、连字符）
              </p>
            </div>
            <div>
              <label class="block text-sm font-medium text-gray-300 mb-2">验证密码</label>
              <input
                v-model="usernameChangePassword"
                type="password"
                class="w-full px-3 py-2 bg-gray-700 border border-gray-600 rounded-lg text-white focus:outline-none focus:ring-2 focus:ring-green-500"
                placeholder="输入当前密码"
              />
            </div>
          </div>

          <div class="flex items-center gap-4">
            <button
              @click="changeUsername"
              :disabled="!canChangeUsername || changingUsername"
              class="px-6 py-2 bg-green-600 hover:bg-green-700 disabled:bg-gray-600 disabled:cursor-not-allowed rounded-lg transition-colors text-sm font-medium"
            >
              {{ changingUsername ? '修改中...' : '更改用户名' }}
            </button>
          </div>
        </div>

        <!-- 用户名更改说明 -->
        <div class="bg-red-900/20 border border-red-500/30 rounded-lg p-4">
          <h3 class="text-sm font-medium text-red-300 mb-2">重要提醒</h3>
          <div class="text-xs text-gray-300 space-y-1">
            <p>• 更改用户名后会自动重新登录</p>
            <p>• 所有使用旧用户名的会话将被终止</p>
            <p>• 请确保新用户名未被其他账户使用</p>
            <p>• 建议定期更改用户名以提高安全性</p>
          </div>
        </div>
      </section>

      <!-- 密码修改设置 -->
      <section class="glass-panel p-6 space-y-4">
        <div class="flex items-center justify-between flex-wrap gap-4">
          <div>
            <h2 class="text-lg font-light">修改管理员密码</h2>
            <p class="text-xs text-gray-400">
              修改当前管理员账户的密码，提高账户安全性。
            </p>
            <p class="text-xs text-gray-400 mt-1">
              密码长度至少6位，建议使用强密码。
            </p>
          </div>
        </div>

        <!-- 密码修改表单 -->
        <div class="mt-6 space-y-4">
          <div class="grid grid-cols-1 md:grid-cols-3 gap-4">
            <div>
              <label class="block text-sm font-medium text-gray-300 mb-2">当前密码</label>
              <input
                v-model="currentPassword"
                type="password"
                class="w-full px-3 py-2 bg-gray-700 border border-gray-600 rounded-lg text-white focus:outline-none focus:ring-2 focus:ring-blue-500"
                placeholder="输入当前密码"
              />
            </div>
            <div>
              <label class="block text-sm font-medium text-gray-300 mb-2">新密码</label>
              <input
                v-model="newPassword"
                type="password"
                class="w-full px-3 py-2 bg-gray-700 border border-gray-600 rounded-lg text-white focus:outline-none focus:ring-2 focus:ring-blue-500"
                placeholder="输入新密码"
              />
            </div>
            <div>
              <label class="block text-sm font-medium text-gray-300 mb-2">确认新密码</label>
              <input
                v-model="confirmPassword"
                type="password"
                class="w-full px-3 py-2 bg-gray-700 border border-gray-600 rounded-lg text-white focus:outline-none focus:ring-2 focus:ring-blue-500"
                placeholder="再次输入新密码"
              />
            </div>
          </div>

          <div class="flex items-center gap-4">
            <button
              @click="changePassword"
              :disabled="!canChangePassword || changingPassword"
              class="px-6 py-2 bg-blue-600 hover:bg-blue-700 disabled:bg-gray-600 disabled:cursor-not-allowed rounded-lg transition-colors text-sm font-medium"
            >
              {{ changingPassword ? '修改中...' : '修改密码' }}
            </button>
          </div>
        </div>

        <!-- 密码修改说明 -->
        <div class="bg-yellow-900/20 border border-yellow-500/30 rounded-lg p-4">
          <h3 class="text-sm font-medium text-yellow-300 mb-2">安全提醒</h3>
          <div class="text-xs text-gray-300 space-y-1">
            <p>• 修改密码后需要重新登录</p>
            <p>• 建议定期更换密码以确保账户安全</p>
            <p>• 如果忘记密码，需要通过数据库直接修改或重新初始化管理员账户</p>
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
          <!-- EXIF 更新入口已集成到 API 测试工具中 -->
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
import { ref, onMounted, computed } from 'vue'
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

    // 设置保存成功，显示提示
    originalMaxAlbumDepth.value = maxAlbumDepth.value
    originalPhotoSortOrder.value = photoSortOrder.value
    originalAlbumSortOrder.value = albumSortOrder.value
    originalWallSortOrder.value = wallSortOrder.value
    originalMinClusterFaceCount.value = minClusterFaceCount.value
    originalGlobalDownloadAllowed.value = globalDownloadAllowed.value
    originalAlbumCategorySortOrder.value = albumCategorySortOrder.value
    originalTagIgnoreList.value = tagIgnoreList.value
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
      username: 'admin', // 默认管理员用户名
      oldPassword: currentPassword.value,
      newPassword: newPassword.value
    })

    alert('✅ 密码修改成功！请重新登录。')

    // 清空表单
    currentPassword.value = ''
    newPassword.value = ''
    confirmPassword.value = ''

    // 退出登录
    localStorage.removeItem('admin_token')
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
      localStorage.setItem('admin_token', response.data.token)
    }

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
  const token = localStorage.getItem('admin_token')
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
