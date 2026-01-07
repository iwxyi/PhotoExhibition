<template>
  <div class="min-h-screen admin-shell text-white">
    <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
      <div class="flex items-center justify-between mb-6">
        <h1 class="text-2xl font-light">相册管理</h1>
        <div class="space-x-3">
          <button v-on:click="load" :disabled="loading" class="btn-primary disabled:opacity-50">刷新</button>
          <button v-on:click="forceScanAndRebuild" :disabled="loading" class="btn-primary disabled:opacity-50">
            重新扫描
          </button>
          <router-link to="/admin" class="px-4 py-2 bg-gray-900/70 hover:bg-gray-700 rounded-lg border border-white/10 transition-colors">返回</router-link>
        </div>
      </div>

      <div class="glass-panel p-4 mb-6">
        <div class="flex flex-wrap gap-4">
          <input v-model="keyword" placeholder="搜索名称/路径" class="px-3 py-2 bg-gray-700 border border-gray-600 rounded w-64 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
          <button v-on:click="load" :disabled="loading" class="px-4 py-2 bg-gray-700 hover:bg-gray-600 rounded-lg text-sm disabled:opacity-50">查询</button>
        </div>
      </div>

      <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4" @click="closeAllMenus">
        <div
          v-for="album in albums"
          :key="album.id"
          class="glass-panel overflow-hidden hover:ring-2 hover:ring-blue-500/80 transition-all flex flex-col"
        >
          <!-- 三合一封面预览（左竖 + 右上/右下） -->
          <div
            class="relative h-40 md:h-44 lg:h-48 bg-gray-900 overflow-hidden flex-shrink-0 cursor-pointer hover:opacity-90 transition-opacity"
            @click="openAlbum(album.id)"
            title="点击查看相册"
          >
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
                <span class="text-xs text-gray-500">
                  {{ formatDate(album.takenAt) }}
                </span>
              </div>
              <div class="flex items-center justify-between gap-2">
                <p class="text-xs text-gray-400 truncate flex-grow" :title="album.relativePath">
                  {{ album.relativePath || album.path }}
                </p>
                <!-- 更多菜单按钮 -->
                <button
                  @click="openMenu($event, album)"
                  class="p-1 bg-gray-700 hover:bg-gray-600 rounded text-xs transition-colors flex-shrink-0"
                  title="更多操作"
                >
                  <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 5v.01M12 12v.01M12 19v.01M12 6a1 1 0 110-2 1 1 0 010 2zm0 7a1 1 0 110-2 1 1 0 010 2zm0 7a1 1 0 110-2 1 1 0 010 2z" />
                  </svg>
                </button>
              </div>
            </div>

            <!-- 可伸缩的中间区域 -->
            <div class="flex-grow min-h-0 mt-2">
              <!-- 特效 -->
              <div v-if="album.atmosphereEffects && album.atmosphereEffects.length > 0" class="mb-2">
                <div class="flex flex-wrap gap-1">
                  <span
                    v-for="effect in album.atmosphereEffects"
                    :key="effect.type"
                    class="px-2 py-0.5 bg-purple-500/20 border border-purple-500/40 rounded-md text-xs inline-flex items-center gap-1 cursor-pointer hover:bg-purple-500/30 transition-colors"
                    :title="'点击编辑特效: ' + getEffectDisplayName(effect.type)"
                    @click.stop="editAlbumEffects(album)"
                  >
                    <svg class="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M11.049 2.927c.3-.921 1.603-.921 1.902 0l1.519 4.674a1 1 0 00.95.69h4.915c.969 0 1.371 1.24.588 1.81l-3.976 2.888a1 1 0 00-.363 1.118l1.518 4.674c.3.922-.755 1.688-1.538 1.118l-3.976-2.888a1 1 0 00-1.176 0l-3.976 2.888c-.783.57-1.838-.197-1.538-1.118l1.518-4.674a1 1 0 00-.363-1.118l-3.976-2.888c-.784-.57-.38-1.81.588-1.81h4.914a1 1 0 00.951-.69l1.519-4.674z" />
                    </svg>
                    {{ getEffectDisplayName(effect.type) }}({{ getIntensityDisplay(effect.intensity || 'medium') }}|{{ effect.layer === 'background' ? '下' : '上' }})
                  </span>
                </div>
              </div>

              <!-- 标签 -->
              <div v-if="album.tags && album.tags.length > 0" class="mb-2">
                <div class="flex flex-wrap gap-1">
                  <span
                    v-for="t in album.tags"
                    :key="t.id"
                    class="px-1.5 py-0.5 bg-blue-500/20 border border-blue-500/40 rounded text-xs inline-flex items-center gap-1 cursor-pointer hover:bg-blue-500/30 transition-colors"
                    :title="'点击编辑标签'"
                    @click.stop="editAlbumTags(album)"
                  >
                    {{ t.name }}
                    <button
                      v-on:click.stop="removeTag(album, t.id)"
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
          </div>
        </div>
      </div>
    </div>

    <!-- 更多操作菜单 -->
    <teleport to="body">
      <div
        v-if="showMenuForAlbum"
        class="fixed inset-0 z-50"
        @click="closeAllMenus"
      >
        <div
          class="absolute bg-gray-800 border border-gray-600 rounded-lg shadow-lg z-10 w-48"
          :style="{ left: menuPosition.x + 'px', top: menuPosition.y + 'px' }"
          @click.stop
        >
          <div class="py-1">
            <!-- 添加标签菜单项 -->
            <button
              @click="addTag(showMenuForAlbum)"
              class="w-full text-left px-4 py-2 text-sm text-gray-300 hover:bg-gray-700 flex items-center gap-2"
            >
              <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M7 7h.01M7 3h5c.512 0 1.024.195 1.414.586l7 7a2 2 0 010 2.828l-7 7a2 2 0 01-2.828 0l-7-7A1.994 1.994 0 013 12V7a4 4 0 014-4z" />
              </svg>
              添加标签
            </button>
            <!-- 编辑备注菜单项 -->
            <button
              @click="editDescription(showMenuForAlbum)"
              class="w-full text-left px-4 py-2 text-sm text-gray-300 hover:bg-gray-700 flex items-center gap-2"
            >
              <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z" />
              </svg>
              编辑备注
            </button>
            <!-- 相册特效菜单项 -->
            <button
              @click="editAtmosphereEffects(showMenuForAlbum)"
              class="w-full text-left px-4 py-2 text-sm text-gray-300 hover:bg-gray-700 flex items-center gap-2"
            >
              <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 3v4M3 5h4M6 17v4m-2-2h4m5-16l2.286 6.857L21 12l-5.714 2.143L13 21l-2.286-6.857L5 12l5.714-2.143L13 3z" />
              </svg>
              相册特效
            </button>
            <!-- 重命名菜单项 -->
            <button
              @click="editName(showMenuForAlbum)"
              class="w-full text-left px-4 py-2 text-sm text-gray-300 hover:bg-gray-700 flex items-center gap-2"
            >
              <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z" />
              </svg>
              重命名
            </button>
            <!-- 删除菜单项 -->
            <button
              @click="deleteAlbum(showMenuForAlbum)"
              class="w-full text-left px-4 py-2 text-sm text-red-400 hover:bg-red-900/50 flex items-center gap-2"
            >
              <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
              </svg>
              删除相册
            </button>
            <!-- 分割线 -->
            <div class="border-t border-gray-600 my-1"></div>
            <!-- 聚合下级相册菜单项 -->
            <button
              v-if="hasSubAlbums(showMenuForAlbum)"
              @click="toggleAggregateSubAlbums(showMenuForAlbum)"
              class="w-full text-left px-4 py-2 text-sm text-gray-300 hover:bg-gray-700 flex items-center justify-between"
            >
              <span>聚合下级相册</span>
              <div class="flex items-center">
                <div
                  :class="showMenuForAlbum.aggregateSubAlbums ? 'bg-green-500' : 'bg-gray-600'"
                  class="w-3 h-3 rounded-full transition-colors"
                ></div>
              </div>
            </button>
            <!-- 聚合到上一级菜单项 -->
            <button
              v-if="!isTopLevelAlbum(showMenuForAlbum)"
              @click="aggregateToParent(showMenuForAlbum)"
              class="w-full text-left px-4 py-2 text-sm text-gray-300 hover:bg-gray-700"
            >
              聚合到上一级
            </button>
            <!-- 设置排序方式菜单项 -->
            <div class="border-t border-gray-600 my-1"></div>
            <div class="px-4 py-2">
              <label class="block text-xs text-gray-400 mb-1">相册排序方式</label>
              <select
                :value="showMenuForAlbum.photoSortOrder"
                @change="setAlbumSortOrder(showMenuForAlbum, ($event.target as HTMLSelectElement).value)"
                class="w-full px-2 py-1 bg-gray-700 border border-gray-600 rounded text-xs text-gray-300 focus:outline-none focus:ring-1 focus:ring-blue-500"
              >
                <option value="">跟随全局设置</option>
                <option value="taken_at_desc">拍摄时间倒序</option>
                <option value="taken_at_asc">拍摄时间正序</option>
                <option value="filename_desc">文件名倒序</option>
                <option value="filename_asc">文件名正序</option>
                <option value="created_at_desc">创建时间倒序</option>
                <option value="created_at_asc">创建时间正序</option>
              </select>
            </div>
          </div>
        </div>
      </div>
    </teleport>

    <!-- 添加标签对话框 -->
    <teleport to="body">
      <div
        v-if="tagDialogVisible"
        class="fixed inset-0 z-50 bg-black/50 flex items-center justify-center p-4"
        @click.self="closeAllMenus"
      >
        <div class="bg-gray-800 rounded-lg p-6 max-w-md w-full text-gray-100">
          <h3 class="text-lg font-medium mb-4 text-gray-100">添加标签</h3>
          <div class="mb-4">
            <input
              v-model="tagKeyword"
              v-on:input="searchTags"
              placeholder="搜索或输入新标签名称"
              class="w-full px-3 py-2 bg-gray-700 border border-gray-600 rounded text-sm text-gray-100 placeholder-gray-400 focus:outline-none focus:ring-2 focus:ring-blue-500"
              v-on:keyup.enter="confirmAddTag"
            />
          </div>
          <!-- 标签候选：瀑布流胶囊布局 -->
          <div class="max-h-60 overflow-auto mb-4 border border-gray-700 rounded bg-gray-900/60">
            <div v-if="filteredTags.length > 0" class="p-2">
              <div class="flex flex-wrap gap-2">
                <button
                  v-for="tag in filteredTags"
                  :key="tag.id"
                  v-on:click="selectTag(tag)"
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
              v-on:click="confirmAddTag"
              class="flex-1 px-4 py-2 bg-blue-600 hover:bg-blue-700 rounded text-sm disabled:opacity-50"
              :disabled="!tagKeyword.trim()"
            >
              确定
            </button>
            <button
              v-on:click="tagDialogVisible = false"
              class="flex-1 px-4 py-2 bg-gray-700 hover:bg-gray-600 rounded text-sm"
            >
              取消
            </button>
        </div>
      </div>
    </div>
    </teleport>

    <!-- 相册特效对话框 -->
    <teleport to="body">
      <div
        v-if="effectsDialogVisible"
        class="fixed inset-0 z-50 bg-black/50 flex items-center justify-center p-4"
        @click.self="closeAllMenus"
      >
        <div class="bg-gray-800 rounded-lg max-w-2xl w-full max-h-[80vh] flex flex-col text-gray-100">
          <!-- 头部 -->
          <div class="p-6 pb-4">
            <h3 class="text-lg font-medium mb-4 text-gray-100">设置相册特效</h3>
            <p class="text-sm text-gray-400 mb-4">
              为相册 <strong>{{ currentAlbum?.displayTitle || currentAlbum?.name }}</strong> 设置氛围特效
            </p>
          </div>

          <!-- 可滚动内容区域 -->
          <div class="flex-1 overflow-y-auto px-6">

          <!-- 当前特效列表 -->
          <div class="mb-4">
            <h4 class="text-sm font-medium text-gray-300 mb-2">当前特效</h4>
            <div v-if="currentEffects.length === 0" class="text-sm text-gray-500 italic">
              暂无特效，可从下方添加
            </div>
            <div v-else class="space-y-2">
              <div
                v-for="(effect, index) in currentEffects"
                :key="index"
                class="bg-gray-700/50 rounded p-3"
              >
                <div class="flex items-center justify-between mb-2">
                  <div class="flex items-center gap-2">
                    <span class="text-sm font-medium">{{ getEffectName(effect.type) }}</span>
                    <span class="text-xs text-gray-400">({{ getIntensityDisplay(effect.intensity || 'medium') }})</span>
                  </div>
                  <button
                    @click="removeEffect(index)"
                    class="text-red-400 hover:text-red-300 text-sm"
                    title="移除特效"
                  >
                    ✕
                  </button>
                </div>
                <!-- 预设强度选择 -->
                <div class="flex items-center gap-4 mb-3">
                  <div class="flex items-center gap-2">
                    <label class="text-xs text-gray-400">预设强度:</label>
                    <select
                      :value="effect.intensity || 'medium'"
                      @change="updateEffectIntensity(index, ($event.target as HTMLSelectElement).value)"
                      class="px-2 py-1 bg-gray-600 border border-gray-500 rounded text-xs text-gray-200"
                    >
                      <option value="low">低</option>
                      <option value="medium">中</option>
                      <option value="high">高</option>
                      <option value="custom">自定义</option>
                    </select>
                  </div>
                  <div class="flex items-center gap-2">
                    <label class="text-xs text-gray-400">层级:</label>
                    <select
                      :value="effect.layer || 'above'"
                      @change="updateEffectLayer(index, ($event.target as HTMLSelectElement).value)"
                      class="px-2 py-1 bg-gray-600 border border-gray-500 rounded text-xs text-gray-200"
                    >
                      <option value="above">图片上方</option>
                      <option value="background">背景层</option>
                    </select>
                  </div>
                </div>

                <!-- 详细参数调节 -->
                <div class="space-y-2">
                  <div class="text-xs text-gray-400 mb-2">自定义参数 (1-10):</div>
                  <div class="grid grid-cols-2 gap-3">
                    <!-- 速度 -->
                    <div class="flex flex-col">
                      <label class="text-xs text-gray-400 mb-1">速度: {{ getCustomValue(effect, 'speed') }}</label>
                      <input
                        type="range"
                        min="1"
                        max="10"
                        :value="getCustomValue(effect, 'speed')"
                        @input="updateCustomParam(index, 'speed', parseInt(($event.target as HTMLInputElement).value))"
                        class="w-full h-2 bg-gray-600 rounded-lg appearance-none cursor-pointer slider"
                      />
                    </div>
                    <!-- 大小 -->
                    <div class="flex flex-col">
                      <label class="text-xs text-gray-400 mb-1">大小: {{ getCustomValue(effect, 'size') }}</label>
                      <input
                        type="range"
                        min="1"
                        max="10"
                        :value="getCustomValue(effect, 'size')"
                        @input="updateCustomParam(index, 'size', parseInt(($event.target as HTMLInputElement).value))"
                        class="w-full h-2 bg-gray-600 rounded-lg appearance-none cursor-pointer slider"
                      />
                    </div>
                    <!-- 数量 -->
                    <div class="flex flex-col">
                      <label class="text-xs text-gray-400 mb-1">数量: {{ getCustomValue(effect, 'count') }}</label>
                      <input
                        type="range"
                        min="1"
                        max="10"
                        :value="getCustomValue(effect, 'count')"
                        @input="updateCustomParam(index, 'count', parseInt(($event.target as HTMLInputElement).value))"
                        class="w-full h-2 bg-gray-600 rounded-lg appearance-none cursor-pointer slider"
                      />
                    </div>
                    <!-- 透明度 -->
                    <div class="flex flex-col">
                      <label class="text-xs text-gray-400 mb-1">透明度: {{ getCustomValue(effect, 'opacity') }}</label>
                      <input
                        type="range"
                        min="1"
                        max="10"
                        :value="getCustomValue(effect, 'opacity')"
                        @input="updateCustomParam(index, 'opacity', parseInt(($event.target as HTMLInputElement).value))"
                        class="w-full h-2 bg-gray-600 rounded-lg appearance-none cursor-pointer slider"
                      />
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>

            <!-- 添加特效 -->
            <div class="mb-6">
              <h4 class="text-sm font-medium text-gray-300 mb-3">添加特效</h4>
              <div class="max-h-60 overflow-y-auto">
                <div class="grid grid-cols-3 gap-3">
                  <div
                    v-for="effect in availableEffects"
                    :key="effect.type"
                    :class="[
                      'border rounded p-3 cursor-pointer transition-colors',
                      isEffectSelected(effect.type)
                        ? 'border-blue-500 bg-blue-500/20 text-blue-100'
                        : 'border-gray-600 hover:border-blue-500/50 hover:bg-gray-700/30'
                    ]"
                    @click="toggleEffect(effect)"
                  >
                    <div class="font-medium text-sm">{{ effect.name }}</div>
                    <div class="text-xs text-gray-400 mt-1">{{ effect.description }}</div>
                  </div>
                </div>
              </div>
            </div>

          </div>

          <!-- 底部按钮区域 -->
          <div class="flex gap-3 p-6 pt-4 border-t border-gray-700">
            <button
              @click="saveAtmosphereEffects"
              class="flex-1 px-4 py-2 bg-blue-600 hover:bg-blue-700 rounded text-sm disabled:opacity-50"
              :disabled="!currentAlbum"
            >
              保存
            </button>
            <button
              @click="effectsDialogVisible = false"
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
import { ref, computed, onMounted, onBeforeUnmount, watch } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '@/api'

const router = useRouter()

const albums = ref<any[]>([])
const loading = ref(false)
const keyword = ref('')
const showMenuForAlbum = ref<any>(null)
const menuPosition = ref({ x: 0, y: 0 })

// 相册排序设置
const albumSortOrder = ref('name_asc')

// 监听排序设置变化，重新加载相册（排除初始化时的第一次设置）
let isInitialized = false
watch(albumSortOrder, async (newSort, oldSort) => {
  if (newSort !== oldSort && isInitialized) {
    console.log('相册排序设置已更改，重新加载相册列表')
    await load()
  }
})

// 标签相关
const allTags = ref<any[]>([])
const tagDialogVisible = ref(false)
const tagKeyword = ref('')
const currentAlbum = ref<any>(null)
const selectedAlbumForTags = ref<any>(null)

// 特效相关
const effectsDialogVisible = ref(false)
const selectedAlbumForEffects = ref<any>(null)
const availableEffects = ref([
  {
    type: 'snow',
    name: '雪景',
    description: '飘落的雪花特效',
    intensityOptions: ['low', 'medium', 'high']
  },
  {
    type: 'cherry_blossom',
    name: '樱花',
    description: '飘落的樱花瓣特效',
    intensityOptions: ['low', 'medium', 'high']
  },
  {
    type: 'birthday',
    name: '生日',
    description: '生日气球和彩带特效',
    intensityOptions: ['low', 'medium', 'high']
  },
  {
    type: 'meteor',
    name: '流星',
    description: '划过的流星特效',
    intensityOptions: ['low', 'medium', 'high']
  },
  {
    type: 'starry_sky',
    name: '星空',
    description: '闪烁的星星特效',
    intensityOptions: ['low', 'medium', 'high']
  },
  {
    type: 'fireworks',
    name: '烟花',
    description: '绽放的烟花特效',
    intensityOptions: ['low', 'medium', 'high']
  },
  {
    type: 'autumn_leaves',
    name: '秋叶',
    description: '飘落的秋叶特效',
    intensityOptions: ['low', 'medium', 'high']
  }
])
const currentEffects = ref<any[]>([])

// 获取相册排序设置
const loadAlbumSortOrder = async () => {
  try {
    const response = await api.get('/admin/config/album-sort-order')
    const newSort = response.data.albumSortOrder || 'name_asc'
    albumSortOrder.value = newSort
  } catch (error) {
    console.error('获取相册排序设置失败:', error)
    albumSortOrder.value = 'name_asc'
  }
}

const load = async () => {
  loading.value = true
  try {
    // 一次性加载较多相册，前端不再做分页
    const params: any = { page: 0, size: 1000, sort: albumSortOrder.value }
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

// 获取特效的显示名称
const getEffectDisplayName = (type: string) => {
  const effectNames: Record<string, string> = {
    snow: '下雪',
    cherry_blossom: '樱花',
    birthday: '生日',
    meteor: '流星',
    starry_sky: '星空',
    fireworks: '烟花',
    autumn_leaves: '秋叶'
  }
  return effectNames[type] || type
}

// 获取强度显示名称
const getIntensityDisplay = (intensity: string) => {
  const intensityNames: Record<string, string> = {
    low: '低',
    medium: '中',
    high: '高',
    custom: '自定义'
  }
  return intensityNames[intensity] || intensity
}

// 编辑相册特效
const editAlbumEffects = async (album: any) => {
  selectedAlbumForEffects.value = album
  currentAlbum.value = album

  try {
    // 获取当前相册的特效配置
    const res = await api.get(`/admin/albums/${album.id}/atmosphere-effects`)
    const effects = res.data.effects || []

    // 确保有自定义配置的特效强度设为"custom"
    effects.forEach((effect: any) => {
      if (effect.config?.custom && Object.keys(effect.config.custom).length > 0) {
        effect.intensity = 'custom'
      } else if (!effect.intensity) {
        // 如果没有强度设置，使用默认值
        effect.intensity = 'medium'
      }
    })

    currentEffects.value = effects
  } catch (e: any) {
    console.warn('获取当前特效配置失败，使用空配置:', e)
    currentEffects.value = []
  }

  effectsDialogVisible.value = true
}

// 编辑相册标签
const editAlbumTags = (album: any) => {
  selectedAlbumForTags.value = album
  tagDialogVisible.value = true
  tagKeyword.value = ''
  searchTags()
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
  showMenuForAlbum.value = null // 关闭菜单
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
    closeAllMenus()
  } catch (e: any) {
    console.error('添加标签失败:', e)
    const errorMsg = e.response?.data?.message || e.response?.data?.error || e.message
    alert('添加标签失败: ' + errorMsg)
  }
}

const removeTag = async (album: any, tagId: number) => {
  try {
    await api.delete(`/albums/${album.id}/tags/${tagId}`)
    await load()
    closeAllMenus()
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
    closeAllMenus()
  } catch (e: any) {
    alert('修改备注失败: ' + (e.response?.data?.error || e.message))
  }
}

const editAtmosphereEffects = async (album: any) => {
  currentAlbum.value = album

  try {
    // 获取当前相册的特效配置
    const res = await api.get(`/admin/albums/${album.id}/atmosphere-effects`)
    currentEffects.value = res.data.effects || []
  } catch (e: any) {
    console.warn('获取当前特效配置失败，使用空配置:', e)
    currentEffects.value = []
  }

  effectsDialogVisible.value = true
  showMenuForAlbum.value = null // 关闭菜单
}

const getEffectName = (type: string): string => {
  const effect = availableEffects.value.find(e => e.type === type)
  return effect?.name || type
}

// 检查特效是否已被选中
const isEffectSelected = (type: string) => {
  return currentEffects.value.some((e: any) => e.type === type)
}

// 切换特效（添加或移除）
const toggleEffect = (effect: any) => {
  const existingIndex = currentEffects.value.findIndex((e: any) => e.type === effect.type)

  if (existingIndex >= 0) {
    // 特效已存在，移除它
    currentEffects.value.splice(existingIndex, 1)
  } else {
    // 特效不存在，添加它，默认使用 medium 强度和空的自定义配置
    const newEffect = {
      type: effect.type,
      intensity: 'medium',
      layer: effect.layer || 'above',
      config: {
        custom: {} // 空的自定义配置，用户可以后续调整
      }
    }
    currentEffects.value.push(newEffect)
  }
}

// 获取自定义参数值
const getCustomValue = (effect: any, param: string) => {
  const intensity = effect.intensity || 'medium'

  // 自定义强度时返回用户设置的值
  if (intensity === 'custom') {
    // 首先尝试从custom配置中获取
    if (effect.config?.custom?.[param] !== undefined) {
      return effect.config.custom[param]
    }

    // 如果没有custom配置，尝试从旧的config字段中推断值
    // 这是为了兼容旧数据格式

    // 对于不同的参数，从config中的对应字段推断
    if (param === 'speed' && effect.config?.speed !== undefined) {
      // speed通常是1-2的范围，对应到1-10需要转换
      const speedValue = Math.round((effect.config.speed - 1) * 4.5 + 1) // 1->1, 2->10
      return Math.max(1, Math.min(10, speedValue))
    }
    if (param === 'size' && effect.config?.size !== undefined) {
      // size通常是2-4的范围，对应到1-10
      const sizeValue = Math.round((effect.config.size - 2) * 4 + 1) // 2->1, 4->10
      return Math.max(1, Math.min(10, sizeValue))
    }
    if (param === 'count' && effect.config?.particleCount !== undefined) {
      // particleCount通常是50-150的范围，对应到1-10
      const countValue = Math.round((effect.config.particleCount - 50) * 9 / 100 + 1) // 50->1, 150->10
      return Math.max(1, Math.min(10, countValue))
    }

    // 如果都推断不出，返回默认值5
    return 5
  }

  // 预设强度时返回预设值
  if (intensity === 'low') return 3
  if (intensity === 'medium') return 5
  if (intensity === 'high') return 7

  return 5
}

// 更新自定义参数
const updateCustomParam = (effectIndex: number, param: string, value: number) => {
  // 当用户修改参数时，将强度设置为自定义
  currentEffects.value[effectIndex].intensity = 'custom'

  if (!currentEffects.value[effectIndex].config) {
    currentEffects.value[effectIndex].config = {}
  }

  // 确保custom配置存在
  if (!currentEffects.value[effectIndex].config.custom) {
    currentEffects.value[effectIndex].config.custom = {}
  }

  // 保存参数值
  currentEffects.value[effectIndex].config.custom[param] = value
}

const removeEffect = (index: number) => {
  currentEffects.value.splice(index, 1)
}

const updateEffectIntensity = (index: number, intensity: string) => {
  const effect = currentEffects.value[index]

  // 如果选择预设强度，清除自定义配置
  if (intensity === 'low' || intensity === 'medium' || intensity === 'high') {
    if (effect.config?.custom) {
      delete effect.config.custom
    }
  }
  // 如果选择自定义强度，初始化自定义配置为当前显示的值
  else if (intensity === 'custom') {
    if (!effect.config) {
      effect.config = {}
    }
    if (!effect.config.custom) {
      // 使用当前显示的值作为自定义配置的初始值
      const currentIntensity = effect.intensity || 'medium'
      const presetValues: Record<string, { speed: number; size: number; count: number; opacity: number }> = {
        low: { speed: 3, size: 3, count: 3, opacity: 3 },
        medium: { speed: 5, size: 5, count: 5, opacity: 5 },
        high: { speed: 7, size: 7, count: 7, opacity: 7 }
      }
      effect.config.custom = { ...presetValues[currentIntensity] }
    }
  }

  effect.intensity = intensity
}

const updateEffectLayer = (index: number, layer: string) => {
  currentEffects.value[index].layer = layer
}

const saveAtmosphereEffects = async () => {
  if (!currentAlbum.value) return

  try {
    const response = await api.put(`/admin/albums/${currentAlbum.value.id}/atmosphere-effects`, {
      effects: currentEffects.value
    })

    if (response.data.success) {
      effectsDialogVisible.value = false
      await load() // 重新加载相册列表
    } else {
      alert('设置失败: ' + (response.data.error || '未知错误'))
    }
  } catch (e: any) {
    console.error('保存特效配置失败:', e)
    alert('保存失败: ' + (e.response?.data?.error || e.message))
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
    closeAllMenus()
  } catch (e: any) {
    alert('修改名称失败: ' + (e.response?.data?.error || e.message))
  }
}

const deleteAlbum = async (album: any) => {
  if (!window.confirm(`确定删除相册"${album.displayTitle || album.name}"吗？`)) return

  try {
    await api.delete(`/albums/${album.id}`)
    await load()
    closeAllMenus()
  } catch (e: any) {
    alert('删除相册失败: ' + (e.response?.data?.error || e.message))
  }
}

// 菜单相关方法
const openMenu = (event: MouseEvent, album: any) => {
  event.stopPropagation()
  showMenuForAlbum.value = album
  menuPosition.value = {
    x: event.clientX,
    y: event.clientY
  }
}

const closeAllMenus = () => {
  showMenuForAlbum.value = null
  tagDialogVisible.value = false
  effectsDialogVisible.value = false
}

const hasSubAlbums = (album: any) => {
  // 使用后端返回的hasSubAlbums字段
  return album.hasSubAlbums === true
}

const isTopLevelAlbum = (album: any) => {
  // 使用后端返回的isTopLevel字段
  return album.isTopLevel === true
}

const toggleAggregateSubAlbums = async (album: any) => {
  const newValue = !album.aggregateSubAlbums
  const actionText = newValue ? '开启' : '关闭'

  if (!confirm(`确定要${actionText}相册"${album.displayTitle || album.name}"的聚合下级相册功能吗？\n\n${newValue ? '开启后，该相册将显示其所有子相册的照片。' : '关闭后，该相册将只显示自己的照片。'}`)) {
    return
  }

  try {
    await api.put(`/albums/${album.id}/aggregate-sub-albums`, {
      aggregateSubAlbums: newValue
    })
    await load()
    closeAllMenus()
    alert(`${actionText}成功`)
  } catch (e: any) {
    alert(`${actionText}失败: ` + (e.response?.data?.error || e.message))
  }
}

const aggregateToParent = async (album: any) => {
  // 检查是否可以聚合到上一级
  const pathParts = album.path.split('/')
  if (pathParts.length < 4) {
    alert('该相册已经是顶级相册，无法聚合到上一级')
    return
  }

  // 构造父相册路径
  const parentPath = pathParts.slice(0, -1).join('/')

  // 查找父相册
  let parentAlbum = albums.value.find(a => a.path === parentPath)

  // 如果父相册不存在，尝试创建它
  if (!parentAlbum) {
    try {
      console.log('父相册不存在，尝试创建:', parentPath)

      // 直接创建父相册
      const createResponse = await api.post('/albums', { path: parentPath })
      parentAlbum = createResponse.data

      if (!parentAlbum) {
        alert('无法创建父相册，请检查文件夹路径是否正确')
        return
      }

      // 将新创建的相册添加到列表中
      albums.value.push(parentAlbum)

    } catch (createError: any) {
      console.error('创建父相册失败:', createError)
      alert('创建父相册失败: ' + (createError.response?.data?.error || createError.message))
      return
    }
  }

  if (!confirm(`确定要将相册"${album.displayTitle || album.name}"聚合到父相册"${parentAlbum.displayTitle || parentAlbum.name}"吗？\n\n这将开启父相册的聚合下级相册功能。`)) {
    return
  }

  try {
    await api.put(`/albums/${parentAlbum.id}/aggregate-sub-albums`, {
      aggregateSubAlbums: true
    })
    await load()
    closeAllMenus()
    alert('聚合到上一级成功')
  } catch (e: any) {
    alert('聚合到上一级失败: ' + (e.response?.data?.error || e.message))
  }
}

const setAlbumSortOrder = async (album: any, sortOrder: string) => {
  try {
    await api.put(`/albums/${album.id}/photo-sort-order`, {
      photoSortOrder: sortOrder
    })
    await load()
    closeAllMenus()
  } catch (e: any) {
    alert('设置排序方式失败: ' + (e.response?.data?.error || e.message))
  }
}

const openAlbum = (albumId: number) => {
  // 在新页面打开相册详情
  const url = `/album/${albumId}`
  window.open(url, '_blank')
}

const forceScanAndRebuild = async () => {
  const confirmed = window.confirm(
    '📸 重新扫描相册\n\n' +
    '此操作将：\n' +
    '• 扫描相册中新增或修改的照片\n' +
    '• 更新相册的元数据信息\n\n' +
    '不会重新生成人脸数据和标签。确定要继续吗？'
  )

  if (!confirmed) return

  loading.value = true

  try {
    // 执行普通扫描（只处理更改的内容）
    console.log('开始重新扫描照片...')
    const scanResponse = await api.post('/admin/scan')
    console.log('扫描任务已启动:', scanResponse.data)

    // 等待扫描完成（简单轮询）
    let scanCompleted = false
    let attempts = 0
    const maxAttempts = 300 // 5分钟超时

    while (!scanCompleted && attempts < maxAttempts) {
      await new Promise(resolve => setTimeout(resolve, 1000))
      const statusResponse = await api.get('/admin/scan/status')
      const status = statusResponse.data
      console.log('扫描状态:', status)

      if (!status.scanning) {
        scanCompleted = true
        console.log('扫描完成')
      }
      attempts++
    }

    if (!scanCompleted) {
      throw new Error('扫描超时，请稍后手动检查扫描状态')
    }

    alert('✅ 重新扫描任务已完成！\n\n' +
          '• 相册扫描：完成\n\n' +
          '请刷新页面查看最新结果。')

    // 重新加载相册数据
    await load()

  } catch (error: any) {
    console.error('重新扫描失败:', error)
    const errorMsg = error.response?.data?.error || error.response?.data?.message || error.message
    alert('❌ 重新扫描失败: ' + errorMsg)
  } finally {
    loading.value = false
  }
}

const handleGlobalKeydown = (e: KeyboardEvent) => {
  if (e.key === 'Escape') {
    // 如果标签弹窗打开，先关闭弹窗
    if (tagDialogVisible.value) {
      tagDialogVisible.value = false
      return
    }
    // 否则返回首页
    router.push('/admin')
  }
}

onMounted(async () => {
  console.log('相册管理页面加载')
  await Promise.all([
    loadAllTags(),
    loadAlbumSortOrder()
  ])
  await load()
  isInitialized = true // 标记初始化完成，后续的排序设置变化才会触发重新加载
  console.log('相册管理页面加载完成，相册数:', albums.value.length, '标签数:', allTags.value.length)
  window.addEventListener('keydown', handleGlobalKeydown)
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', handleGlobalKeydown)
})
</script>

<style scoped>
/* 滑块样式 */
.slider {
  -webkit-appearance: none;
}

.slider::-webkit-slider-thumb {
  -webkit-appearance: none;
  appearance: none;
  width: 16px;
  height: 16px;
  border-radius: 50%;
  background: #60a5fa;
  cursor: pointer;
  border: 2px solid #1f2937;
}

.slider::-webkit-slider-track {
  width: 100%;
  height: 4px;
  background: #374151;
  border-radius: 2px;
}

.slider::-moz-range-thumb {
  width: 16px;
  height: 16px;
  border-radius: 50%;
  background: #60a5fa;
  cursor: pointer;
  border: 2px solid #1f2937;
}

.slider::-moz-range-track {
  width: 100%;
  height: 4px;
  background: #374151;
  border-radius: 2px;
  border: none;
}
</style>
