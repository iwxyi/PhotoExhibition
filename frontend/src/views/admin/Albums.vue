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
          <!-- 封面预览 -->
          <div
            class="relative overflow-hidden cursor-pointer hover:opacity-90 transition-opacity"
            @click="openAlbum(album.id)"
            title="点击查看相册"
          >
            <CoverDisplay
              :covers="getAlbumCovers(album)"
              :default-covers="getDefaultCovers(album)"
              :photo-count="album.photoCount || 0"
              size="lg"
            />
            <!-- 聚合标签 - 封面右上角 -->
            <div
              v-if="album.aggregateSubAlbums"
              class="absolute top-2 right-2 px-2 py-0.5 bg-blue-500/30 backdrop-blur-sm rounded text-xs text-blue-200 border border-blue-400/30"
              title="已开启聚合下级相册"
            >
              聚合
            </div>
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
        <!-- 菜单毛玻璃背景 -->
        <div
          class="absolute glass-menu rounded-lg shadow-2xl z-10 w-56"
          :style="menuStyle"
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
            <!-- 设置封面菜单项 -->
            <button
              @click="openCoverDialog(showMenuForAlbum)"
              class="w-full text-left px-4 py-2 text-sm text-gray-300 hover:bg-gray-700 flex items-center gap-2"
            >
              <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z" />
              </svg>
              设置封面
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
            <!-- 移动至菜单项 -->
            <div class="relative group/move">
              <button
                class="w-full text-left px-4 py-2 text-sm text-gray-300 hover:bg-gray-700 flex items-center justify-between"
                @mouseenter="loadMoveMenuData(showMenuForAlbum)"
              >
                <span class="flex items-center gap-2">
                  <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 7h12m0 0l-4-4m4 4l-4 4m0 6H4m0 0l4 4m-4-4l4-4" />
                  </svg>
                  移动至
                </span>
                <svg class="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5l7 7-7 7" /></svg>
              </button>
              <!-- 移动至子菜单 -->
              <div class="hidden group-hover/move:block absolute left-full top-0 w-56 glass-menu rounded-lg shadow-2xl z-20 ml-1">
                <div class="py-1">
                  <!-- 分类 -->
                  <div class="relative group/cat">
                    <button class="w-full text-left px-4 py-2 text-sm text-gray-300 hover:bg-gray-700 flex items-center justify-between">
                      <span>分类</span>
                      <svg class="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5l7 7-7 7" /></svg>
                    </button>
                    <div class="hidden group-hover/cat:block absolute left-full top-0 w-48 glass-menu rounded-lg shadow-2xl z-30 ml-1 max-h-80 overflow-y-auto">
                      <div class="py-1">
                        <button
                          v-for="cat in moveCategories"
                          :key="cat.path"
                          @click="doMoveToCategory(showMenuForAlbum, cat)"
                          class="w-full text-left px-4 py-2 text-sm text-gray-300 hover:bg-gray-700 truncate"
                          :title="cat.name"
                        >
                          {{ cat.name }}
                        </button>
                        <div v-if="moveCategories.length === 0" class="px-4 py-2 text-xs text-gray-500">暂无分类</div>
                      </div>
                    </div>
                  </div>
                  <!-- 上一级 -->
                  <button
                    @click="doMoveToParent(showMenuForAlbum)"
                    class="w-full text-left px-4 py-2 text-sm text-gray-300 hover:bg-gray-700"
                  >
                    上一级
                  </button>
                  <!-- 下一级 -->
                  <div class="relative group/child">
                    <button
                      class="w-full text-left px-4 py-2 text-sm text-gray-300 hover:bg-gray-700 flex items-center justify-between"
                      @mouseenter="loadChildDirs(showMenuForAlbum)"
                    >
                      <span>下一级</span>
                      <svg class="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5l7 7-7 7" /></svg>
                    </button>
                    <div class="hidden group-hover/child:block absolute left-full top-0 w-48 glass-menu rounded-lg shadow-2xl z-30 ml-1 max-h-80 overflow-y-auto">
                      <div class="py-1">
                        <button
                          v-for="dir in moveChildDirs"
                          :key="dir.path"
                          @click="doMoveToChild(showMenuForAlbum, dir)"
                          class="w-full text-left px-4 py-2 text-sm text-gray-300 hover:bg-gray-700 truncate"
                          :title="dir.name"
                        >
                          {{ dir.name }}
                        </button>
                        <div v-if="moveChildDirs.length === 0" class="px-4 py-2 text-xs text-gray-500">暂无子目录</div>
                      </div>
                    </div>
                  </div>
                  <!-- 分割线 -->
                  <div class="border-t border-gray-600 my-1"></div>
                  <!-- 指定路径 -->
                  <button
                    @click="openPathPicker(showMenuForAlbum)"
                    class="w-full text-left px-4 py-2 text-sm text-gray-300 hover:bg-gray-700"
                  >
                    指定路径…
                  </button>
                </div>
              </div>
            </div>
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

            <!-- 下载权限设置 -->
            <div class="px-4 py-2">
              <label class="block text-xs text-gray-400 mb-1">下载权限</label>
              <select
                :value="showMenuForAlbum.downloadAllowed"
                @change="setAlbumDownloadAllowed(showMenuForAlbum, ($event.target as HTMLSelectElement).value)"
                class="w-full px-2 py-1 bg-gray-700 border border-gray-600 rounded text-xs text-gray-300 focus:outline-none focus:ring-1 focus:ring-blue-500"
              >
                <option value="">跟随全局设置</option>
                <option :value="true">允许下载</option>
                <option :value="false">禁止下载</option>
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
        class="fixed inset-0 z-50 flex items-center justify-center p-4"
        @click.self="closeAllMenus"
      >
        <div class="glass-dialog rounded-lg p-6 max-w-md w-full text-gray-100">
          <h3 class="text-lg font-medium mb-4 text-gray-100">添加标签</h3>
          <div class="mb-4">
            <input
              ref="tagInputRef"
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
        class="fixed inset-0 z-50 flex items-center justify-center p-4"
        @click.self="closeAllMenus"
      >
        <div class="glass-dialog rounded-lg max-w-2xl w-full max-h-[80vh] flex flex-col text-gray-100">
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

    <!-- 设置封面对话框 -->
    <teleport to="body">
      <div
        v-if="coverDialogVisible"
        class="fixed inset-0 z-50 flex items-center justify-center p-4"
        @click.self="coverDialogVisible = false"
      >
        <div class="glass-dialog rounded-lg max-w-4xl w-full max-h-[80vh] flex flex-col text-gray-100">
          <!-- 头部 -->
          <div class="p-4 border-b border-gray-700 flex items-center justify-between">
            <h3 class="text-lg font-medium">设置封面</h3>
            <button
              @click="coverDialogVisible = false"
              class="text-gray-400 hover:text-white"
            >
              <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
              </svg>
            </button>
          </div>

          <!-- 已选封面预览 -->
          <div class="p-4 bg-gray-900/50 border-b border-gray-700">
            <div class="text-sm text-gray-400 mb-2">已选封面 ({{ selectedCoverIds.length }}/4)</div>
            <div class="flex gap-2">
              <div
                v-for="id in selectedCoverIds"
                :key="id"
                class="relative w-20 h-20 rounded overflow-hidden border-2 border-blue-500"
              >
                <img
                  :src="getPhotoUrl(coverDialogPhotos.find(p => p.id === id))"
                  class="w-full h-full object-cover"
                />
                <button
                  @click="toggleCoverSelection(coverDialogPhotos.find(p => p.id === id))"
                  class="absolute top-0 right-0 bg-red-500 text-white w-5 h-5 flex items-center justify-center text-xs"
                >
                  ×
                </button>
                <div class="absolute bottom-0 left-0 right-0 bg-black/60 text-center text-xs py-0.5">
                  {{ getCoverIndex(id) }}
                </div>
              </div>
              <div
                v-if="selectedCoverIds.length === 0"
                class="text-gray-500 text-sm flex items-center"
              >
                未选择封面，将使用自动生成的封面
              </div>
            </div>
          </div>

          <!-- 照片列表 -->
          <div class="flex-1 overflow-y-auto p-4">
            <div v-if="coverDialogLoading" class="text-center py-8 text-gray-400">
              加载中...
            </div>
            <div v-else-if="coverDialogPhotos.length === 0" class="text-center py-8 text-gray-400">
              暂无照片
            </div>
            <div v-else class="grid grid-cols-4 md:grid-cols-6 lg:grid-cols-8 gap-2">
              <div
                v-for="photo in coverDialogPhotos"
                :key="photo.id"
                :class="[
                  'relative aspect-square rounded overflow-hidden cursor-pointer transition-all',
                  isCoverSelected(photo.id) ? 'ring-2 ring-blue-500' : 'hover:ring-2 hover:ring-gray-400'
                ]"
                @click="toggleCoverSelection(photo)"
              >
                <img
                  :src="getPhotoUrl(photo)"
                  class="w-full h-full object-cover"
                  loading="lazy"
                />
                <!-- 选中标记 -->
                <div
                  v-if="isCoverSelected(photo.id)"
                  class="absolute inset-0 bg-blue-500/30 flex items-center justify-center"
                >
                  <div class="w-8 h-8 bg-blue-500 rounded-full flex items-center justify-center font-bold text-white">
                    {{ getCoverIndex(photo.id) }}
                  </div>
                </div>
              </div>
            </div>
          </div>

          <!-- 底部按钮 -->
          <div class="p-4 border-t border-gray-700 flex gap-3">
            <button
              @click="clearCover"
              class="px-4 py-2 bg-gray-700 hover:bg-gray-600 rounded text-sm"
              :disabled="selectedCoverIds.length === 0"
            >
              清除封面
            </button>
            <div class="flex-1"></div>
            <button
              @click="coverDialogVisible = false"
              class="px-4 py-2 bg-gray-700 hover:bg-gray-600 rounded text-sm"
            >
              取消
            </button>
            <button
              @click="saveCover"
              class="px-4 py-2 bg-blue-600 hover:bg-blue-700 rounded text-sm"
            >
              保存
            </button>
          </div>
        </div>
      </div>
    </teleport>

    <!-- 编辑备注对话框 -->
    <teleport to="body">
      <div
        v-if="descriptionDialogVisible"
        class="fixed inset-0 z-50 flex items-center justify-center p-4"
        @click.self="descriptionDialogVisible = false"
      >
        <div class="glass-dialog rounded-lg p-6 max-w-md w-full text-gray-100">
          <h3 class="text-lg font-medium mb-4 text-gray-100">编辑备注</h3>
          <textarea
            ref="descriptionInputRef"
            v-model="descriptionInput"
            rows="4"
            placeholder="输入相册备注"
            class="w-full px-3 py-2 bg-gray-700/50 border border-gray-600 rounded text-sm text-gray-100 placeholder-gray-400 focus:outline-none focus:ring-2 focus:ring-blue-500 resize-none"
          ></textarea>
          <div class="flex gap-3 mt-4">
            <button
              @click="saveDescription"
              class="flex-1 px-4 py-2 bg-blue-600 hover:bg-blue-500 rounded text-sm"
            >
              保存
            </button>
            <button
              @click="descriptionDialogVisible = false"
              class="flex-1 px-4 py-2 bg-gray-700 hover:bg-gray-600 rounded text-sm"
            >
              取消
            </button>
          </div>
        </div>
      </div>
    </teleport>

    <!-- 重命名对话框 -->
    <teleport to="body">
      <div
        v-if="renameDialogVisible"
        class="fixed inset-0 z-50 flex items-center justify-center p-4"
        @click.self="renameDialogVisible = false"
      >
        <div class="glass-dialog rounded-lg p-6 max-w-md w-full text-gray-100">
          <h3 class="text-lg font-medium mb-4 text-gray-100">重命名相册</h3>
          <input
            ref="nameInputRef"
            v-model="nameInput"
            placeholder="输入新名称"
            class="w-full px-3 py-2 bg-gray-700/50 border border-gray-600 rounded text-sm text-gray-100 placeholder-gray-400 focus:outline-none focus:ring-2 focus:ring-blue-500"
            @keyup.enter="saveName"
          />
          <div class="flex gap-3 mt-4">
            <button
              @click="saveName"
              class="flex-1 px-4 py-2 bg-blue-600 hover:bg-blue-500 rounded text-sm"
            >
              保存
            </button>
            <button
              @click="renameDialogVisible = false"
              class="flex-1 px-4 py-2 bg-gray-700 hover:bg-gray-600 rounded text-sm"
            >
              取消
            </button>
          </div>
        </div>
      </div>
    </teleport>

    <!-- 移动冲突对话框 -->
    <teleport to="body">
      <div
        v-if="moveConflictDialogVisible"
        class="fixed inset-0 z-50 flex items-center justify-center p-4"
        @click.self="moveConflictDialogVisible = false"
      >
        <div class="glass-dialog rounded-lg p-6 max-w-lg w-full text-gray-100">
          <h3 class="text-lg font-medium mb-3 text-yellow-400 flex items-center gap-2">
            <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-2.5L13.732 4c-.77-.833-1.964-.833-2.732 0L4.082 16.5c-.77.833.192 2.5 1.732 2.5z" /></svg>
            同名文件夹冲突
          </h3>
          <p class="text-sm text-gray-300 mb-3">{{ moveConflictInfo.message }}</p>
          <!-- 文件清单 -->
          <div v-if="moveConflictInfo.conflictFiles && moveConflictInfo.conflictFiles.length > 0" class="mb-4 max-h-40 overflow-y-auto bg-gray-900/60 rounded p-2 border border-gray-700">
            <div class="text-xs text-gray-400 mb-1">目标文件夹内的文件：</div>
            <div v-for="f in moveConflictInfo.conflictFiles" :key="f" class="text-xs text-gray-300 py-0.5 truncate" :title="f">
              📄 {{ f }}
            </div>
          </div>
          <div class="flex flex-col gap-2">
            <button
              @click="executeMoveWithResolution('overwrite')"
              class="w-full px-4 py-2 bg-red-600 hover:bg-red-700 rounded text-sm flex items-center justify-center gap-2"
            >
              覆盖（删除目标文件夹内容后移动）
            </button>
            <button
              @click="executeMoveWithResolution('rename')"
              class="w-full px-4 py-2 bg-blue-600 hover:bg-blue-700 rounded text-sm flex items-center justify-center gap-2"
            >
              重命名为 "{{ moveConflictInfo.suggestedNewName }}"
            </button>
            <button
              @click="moveConflictDialogVisible = false"
              class="w-full px-4 py-2 bg-gray-700 hover:bg-gray-600 rounded text-sm"
            >
              取消
            </button>
          </div>
        </div>
      </div>
    </teleport>

    <!-- 路径选择器对话框 -->
    <teleport to="body">
      <div
        v-if="pathPickerVisible"
        class="fixed inset-0 z-50 flex items-center justify-center p-4"
        @click.self="pathPickerVisible = false"
      >
        <div class="glass-dialog rounded-lg max-w-lg w-full max-h-[70vh] flex flex-col text-gray-100">
          <div class="p-4 border-b border-gray-700">
            <h3 class="text-lg font-medium">选择目标路径</h3>
            <p class="text-xs text-gray-400 mt-1">将相册 "{{ pathPickerAlbum?.displayTitle || pathPickerAlbum?.name }}" 移动到选定目录</p>
          </div>
          <!-- 当前路径 -->
          <div class="px-4 py-2 bg-gray-900/50 border-b border-gray-700 flex items-center gap-2">
            <span class="text-xs text-gray-400">当前：</span>
            <span class="text-xs text-gray-200 truncate flex-1" :title="pathPickerCurrentPath">{{ pathPickerCurrentPath }}</span>
            <button
              v-if="pathPickerParentPath"
              @click="navigatePathPicker(pathPickerParentPath)"
              class="px-2 py-1 bg-gray-700 hover:bg-gray-600 rounded text-xs flex-shrink-0"
            >
              ↑ 上级
            </button>
          </div>
          <!-- 目录列表 -->
          <div class="flex-1 overflow-y-auto p-2">
            <div v-if="pathPickerLoading" class="text-center py-4 text-gray-400 text-sm">加载中...</div>
            <div v-else-if="pathPickerDirs.length === 0" class="text-center py-4 text-gray-500 text-sm">该目录下无子目录</div>
            <div v-else>
              <button
                v-for="dir in pathPickerDirs"
                :key="dir.path"
                @dblclick="navigatePathPicker(dir.path)"
                @click="selectPickerDir(dir)"
                :class="[
                  'w-full text-left px-3 py-2 rounded text-sm flex items-center gap-2 mb-1',
                  pathPickerSelectedDir?.path === dir.path ? 'bg-blue-600/30 border border-blue-500/50' : 'hover:bg-gray-700/50'
                ]"
              >
                <svg class="w-4 h-4 text-yellow-400 flex-shrink-0" fill="currentColor" viewBox="0 0 20 20"><path d="M2 6a2 2 0 012-2h5l2 2h5a2 2 0 012 2v6a2 2 0 01-2 2H4a2 2 0 01-2-2V6z" /></svg>
                <span class="truncate">{{ dir.name }}</span>
              </button>
            </div>
          </div>
          <!-- 底部按钮 -->
          <div class="p-4 border-t border-gray-700 flex gap-3">
            <div class="text-xs text-gray-400 flex-1 self-center truncate">
              移动到：{{ pathPickerSelectedDir?.path || pathPickerCurrentPath }}
            </div>
            <button
              @click="pathPickerVisible = false"
              class="px-4 py-2 bg-gray-700 hover:bg-gray-600 rounded text-sm"
            >
              取消
            </button>
            <button
              @click="confirmPathPicker"
              class="px-4 py-2 bg-blue-600 hover:bg-blue-700 rounded text-sm"
            >
              确认移动
            </button>
          </div>
        </div>
      </div>
    </teleport>

    <!-- 加载状态提示 -->
    <div v-if="loadingMore" class="text-center py-4 text-gray-400 text-sm">
      加载中...
    </div>
    <div v-else-if="!hasMoreData && albums.length > 0" class="text-center py-4 text-gray-500 text-sm">
      已加载全部 {{ albums.length }} 个相册
    </div>
    <div v-else-if="albums.length === 0 && !loading" class="text-center py-4 text-gray-500 text-sm">
      没有找到相册
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount, watch, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { api, albumApi } from '@/api'
import CoverDisplay from '@/components/CoverDisplay.vue'

const router = useRouter()

const albums = ref<any[]>([])
const loading = ref(false)
const keyword = ref('')
const showMenuForAlbum = ref<any>(null)
const menuPosition = ref({ x: 0, y: 0 })

// 相册排序设置
const albumSortOrder = ref('name_asc')

// 分页相关
const PAGE_SIZE = 20
let currentPage = 0
let hasMoreData = true
const loadingMore = ref(false)

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
const tagInputRef = ref<HTMLInputElement | null>(null)
const currentAlbum = ref<any>(null)
const selectedAlbumForTags = ref<any>(null)

// 备注编辑相关
const descriptionDialogVisible = ref(false)
const descriptionInput = ref('')
const descriptionInputRef = ref<HTMLTextAreaElement | null>(null)
const currentDescriptionAlbum = ref<any>(null)

// 重命名相关
const renameDialogVisible = ref(false)
const nameInput = ref('')
const nameInputRef = ref<HTMLInputElement | null>(null)
const currentNameAlbum = ref<any>(null)

// 移动相关
const moveCategories = ref<any[]>([])
const moveChildDirs = ref<any[]>([])
const moveConflictDialogVisible = ref(false)
const moveConflictInfo = ref<any>({})
const moveConflictAlbum = ref<any>(null)
const moveConflictTargetPath = ref('')
const pathPickerVisible = ref(false)
const pathPickerAlbum = ref<any>(null)
const pathPickerCurrentPath = ref('')
const pathPickerParentPath = ref('')
const pathPickerDirs = ref<any[]>([])
const pathPickerSelectedDir = ref<any>(null)
const pathPickerLoading = ref(false)

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
  currentPage = 0
  hasMoreData = true
  albums.value = []
  
  try {
    const params: any = { page: 0, size: PAGE_SIZE, sort: albumSortOrder.value }
    const res = await api.get('/albums', { params })
    const content = res.data.content || res.data || []
    
    // 关键词过滤
    if (keyword.value.trim()) {
      const kw = keyword.value.trim().toLowerCase()
      const filtered = (content as any[]).filter((a: any) =>
        (a.name || '').toLowerCase().includes(kw) ||
        (a.path || '').toLowerCase().includes(kw) ||
        (a.displayTitle || '').toLowerCase().includes(kw) ||
        (a.relativePath || '').toLowerCase().includes(kw)
      )
      albums.value = filtered
      hasMoreData = false // 过滤模式下不加载更多
    } else {
      albums.value = content
      // 只有返回数量等于请求数量时，才认为还有更多数据
      hasMoreData = content.length === PAGE_SIZE
      currentPage = 0
    }
  } finally {
    loading.value = false
  }
}

const loadMore = async () => {
  if (loadingMore.value || !hasMoreData) return
  
  loadingMore.value = true
  try {
    currentPage++
    const params: any = { page: currentPage, size: PAGE_SIZE, sort: albumSortOrder.value }
    const res = await api.get('/albums', { params })
    const content = res.data.content || res.data || []
    albums.value = [...albums.value, ...content]
    hasMoreData = content.length === PAGE_SIZE // 只有返回完整的 PAGE_SIZE 才认为还有更多
  } finally {
    loadingMore.value = false
  }
}

// 使用 scroll 事件监听滚动，实现更可靠的预加载
let scrollTimeout: number | null = null

const handleScroll = () => {
  if (scrollTimeout) return
  
  scrollTimeout = window.setTimeout(() => {
    scrollTimeout = null
    
    // 如果正在加载或没有更多数据，跳过
    if (loadingMore.value || !hasMoreData || loading.value) return
    
    // 检查是否接近底部
    const scrollTop = window.scrollY
    const windowHeight = window.innerHeight
    const documentHeight = document.documentElement.scrollHeight
    
    // 当滚动到距离底部约 800px 时开始加载
    const distanceToBottom = documentHeight - (scrollTop + windowHeight)
    
    if (distanceToBottom < 850) {
      loadMore()
    }
  }, 100)
}

// 初始化滚动监听
const initScrollObserver = () => {
  // 移除旧的监听器
  window.removeEventListener('scroll', handleScroll)
  
  // 添加新的滚动监听
  window.addEventListener('scroll', handleScroll, { passive: true })
}

// 清理滚动监听
const cleanupScrollObserver = () => {
  window.removeEventListener('scroll', handleScroll)
  if (scrollTimeout) {
    clearTimeout(scrollTimeout)
    scrollTimeout = null
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

// 更新相册数据的辅助函数（避免重新加载导致滚动丢失）
const updateAlbumData = (albumId: number, updates: Record<string, any>) => {
  const album = albums.value.find(a => a.id === albumId)
  if (album) {
    Object.assign(album, updates)
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
  tagKeyword.value = ''
  tagDialogVisible.value = true
  searchTags()
  nextTick(() => {
    tagInputRef.value?.focus()
  })
}

const getPhotoUrl = (photo: any): string => {
  if (!photo) return ''
  if (photo.smallThumbPath) {
    return `/api/files${photo.smallThumbPath}`
  }
  if (photo.webpPath) {
    return `/api/files${photo.webpPath}`
  }
  if (photo.thumbnailPath) {
    return `/api/files${photo.thumbnailPath}`
  }
  return `/api/files${photo.originalPath}`
}

// 获取相册的自定义封面列表
const getAlbumCovers = (album: any): any[] => {
  if (album.coverImageIds && album.coverImageIds.length > 0) {
    const covers: any[] = []
    const coverMap = album.coverImages || {}
    
    // 从 coverImages 中查找所有封面
    const allCovers = [
      coverMap.cover1,
      coverMap.cover2,
      coverMap.cover3,
      coverMap.cover4,
      coverMap.leftVertical,
      coverMap.rightTop,
      coverMap.rightBottom
    ]
    
    for (const id of album.coverImageIds) {
      // 查找对应 ID 的封面
      const cover = allCovers.find(c => c?.id === id)
      if (cover) {
        covers.push(cover)
      }
    }
    
    return covers
  }
  return []
}

// 获取相册的默认封面（自动生成的）
const getDefaultCovers = (album: any): { left?: any; rightTop?: any; rightBottom?: any } => {
  const coverMap = album.coverImages || {}
  return {
    left: coverMap.cover1 || coverMap.leftVertical,
    rightTop: coverMap.cover2 || coverMap.rightTop,
    rightBottom: coverMap.cover3 || coverMap.rightBottom
  }
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

    // 直接更新本地标签数据，避免重新加载导致滚动丢失
    const album = albums.value.find(a => a.id === currentAlbum.value.id)
    if (album) {
      if (!album.tags) album.tags = []
      // 检查标签是否已存在
      if (!album.tags.find((t: any) => t.id === tag.id)) {
        album.tags.push(tag)
      }
    }
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
    // 直接更新本地标签数据，避免重新加载导致滚动丢失
    if (album.tags) {
      album.tags = album.tags.filter((t: any) => t.id !== tagId)
    }
    closeAllMenus()
  } catch (e: any) {
    alert('移除标签失败: ' + (e.response?.data?.error || e.message))
  }
}

const editDescription = async (album: any) => {
  currentDescriptionAlbum.value = album
  descriptionInput.value = album.description || ''
  descriptionDialogVisible.value = true
  showMenuForAlbum.value = null // 关闭菜单
  nextTick(() => {
    descriptionInputRef.value?.focus()
  })
}

const saveDescription = async () => {
  if (!currentDescriptionAlbum.value) return
  
  try {
    await api.put(`/albums/${currentDescriptionAlbum.value.id}`, {
      name: currentDescriptionAlbum.value.name,
      description: descriptionInput.value
    })
    // 直接更新本地数据，避免重新加载导致滚动丢失
    updateAlbumData(currentDescriptionAlbum.value.id, {
      description: descriptionInput.value
    })
    descriptionDialogVisible.value = false
    currentDescriptionAlbum.value = null
  } catch (e: any) {
    alert('保存备注失败: ' + (e.response?.data?.error || e.message))
  }
}

// 设置封面相关
const coverDialogVisible = ref(false)
const coverDialogAlbum = ref<any>(null)
const coverDialogPhotos = ref<any[]>([])
const selectedCoverIds = ref<number[]>([])
const coverDialogLoading = ref(false)

const openCoverDialog = async (album: any) => {
  // 从列表中获取最新的相册数据，确保 coverImageIds 是最新的
  const latestAlbum = albums.value.find(a => a.id === album.id) || album
  coverDialogAlbum.value = latestAlbum
  selectedCoverIds.value = latestAlbum.coverImageIds ? [...latestAlbum.coverImageIds] : []
  coverDialogLoading.value = true
  coverDialogVisible.value = true
  showMenuForAlbum.value = null // 关闭菜单

  try {
    // 获取相册的所有照片
    const res = await api.get(`/photos/album/${album.id}`, { params: { all: true } })
    coverDialogPhotos.value = res.data.content || []
  } catch (e: any) {
    console.error('获取相册照片失败:', e)
    coverDialogPhotos.value = []
  } finally {
    coverDialogLoading.value = false
  }
}

const toggleCoverSelection = (photo: any) => {
  const index = selectedCoverIds.value.indexOf(photo.id)
  if (index >= 0) {
    // 取消选中
    selectedCoverIds.value.splice(index, 1)
  } else {
    // 选中，最多4张
    if (selectedCoverIds.value.length < 4) {
      selectedCoverIds.value.push(photo.id)
    }
  }
}

const isCoverSelected = (photoId: number) => {
  return selectedCoverIds.value.includes(photoId)
}

const getCoverIndex = (photoId: number) => {
  const index = selectedCoverIds.value.indexOf(photoId)
  return index >= 0 ? index + 1 : null
}

const saveCover = async () => {
  if (!coverDialogAlbum.value) return

  const albumId = coverDialogAlbum.value.id
  const originalAlbum = albums.value.find(a => a.id === albumId)
  
  // 先保存选中的封面ID和照片列表，再清空弹窗状态
  const coverIdsToSave = [...selectedCoverIds.value]
  // 保存当前照片列表的副本，用于后续构建封面
  const dialogPhotos = [...coverDialogPhotos.value]
  
  // 关闭弹窗
  coverDialogVisible.value = false
  coverDialogAlbum.value = null
  coverDialogPhotos.value = []
  selectedCoverIds.value = []

  try {
    // 保存封面到后端
    const response = await albumApi.setAlbumCover(albumId, coverIdsToSave)
    const savedCoverImageIds = response.data.coverImageIds || []
    console.log('保存封面成功:', savedCoverImageIds)
    
    // 直接更新本地数据，不重新获取整个相册（避免超时）
    if (originalAlbum) {
      // 更新 coverImageIds
      originalAlbum.coverImageIds = savedCoverImageIds
      
      // 根据 coverImageIds 从照片列表中获取对应的照片来构建封面
      const covers: any[] = []
      for (const id of savedCoverImageIds) {
        // 从弹窗的照片列表中查找对应的照片
        const photo = dialogPhotos.find(p => p.id === id)
        if (photo) {
          covers.push(photo)
        }
      }
      
      // 重新构建 coverImages
      originalAlbum.coverImages = {
        cover1: covers[0] || null,
        cover2: covers[1] || null,
        cover3: covers[2] || null,
        cover4: covers[3] || null,
        leftVertical: covers[0] || null,
        rightTop: covers[1] || null,
        rightBottom: covers[2] || null
      }
      console.log('本地数据已更新, coverImageIds:', originalAlbum.coverImageIds, 'covers:', covers.length)
    }
  } catch (e: any) {
    console.error('更新封面失败:', e)
    alert('设置封面失败: ' + (e.response?.data?.error || e.message))
  }
}

const clearCover = async () => {
  selectedCoverIds.value = []
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
      // 直接更新本地数据，避免重新加载导致滚动丢失
      // 保存为数组而不是JSON字符串，确保显示和编辑时能正确遍历
      updateAlbumData(currentAlbum.value.id, {
        atmosphereEffects: [...currentEffects.value]
      })
    } else {
      alert('设置失败: ' + (response.data.error || '未知错误'))
    }
  } catch (e: any) {
    console.error('保存特效配置失败:', e)
    alert('保存失败: ' + (e.response?.data?.error || e.message))
  }
}

const editName = async (album: any) => {
  currentNameAlbum.value = album
  nameInput.value = album.name || ''
  renameDialogVisible.value = true
  showMenuForAlbum.value = null // 关闭菜单
  nextTick(() => {
    nameInputRef.value?.focus()
  })
}

const saveName = async () => {
  if (!currentNameAlbum.value || !nameInput.value.trim()) return
  
  try {
    const newName = nameInput.value.trim()
    await api.put(`/albums/${currentNameAlbum.value.id}`, {
      name: newName,
      description: currentNameAlbum.value.description || ''
    })
    // 直接更新本地数据，避免重新加载导致滚动丢失
    updateAlbumData(currentNameAlbum.value.id, {
      name: newName,
      displayTitle: newName // 同时更新显示标题
    })
    renameDialogVisible.value = false
    currentNameAlbum.value = null
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

// ==================== 移动相册方法 ====================

const loadMoveMenuData = async (album: any) => {
  if (moveCategories.value.length === 0) {
    try {
      const res = await api.get('/albums/move/categories')
      moveCategories.value = res.data || []
    } catch (e) {
      console.error('加载分类列表失败:', e)
    }
  }
}

const loadChildDirs = async (album: any) => {
  if (!album) return
  try {
    const res = await api.get(`/albums/${album.id}/move/children`)
    moveChildDirs.value = res.data || []
  } catch (e) {
    console.error('加载子目录失败:', e)
    moveChildDirs.value = []
  }
}

const doMoveAlbum = async (album: any, targetPath: string, conflictResolution?: string) => {
  try {
    const res = await api.post(`/albums/${album.id}/move`, {
      targetPath,
      conflictResolution: conflictResolution || null
    })
    const result = res.data

    if (result.conflict) {
      moveConflictInfo.value = result
      moveConflictAlbum.value = album
      moveConflictTargetPath.value = targetPath
      moveConflictDialogVisible.value = true
      closeAllMenus()
      return
    }

    if (result.success) {
      closeAllMenus()
      moveConflictDialogVisible.value = false
      await load()
      alert('✅ ' + (result.message || '相册移动成功'))
    } else {
      alert('移动失败: ' + (result.message || '未知错误'))
    }
  } catch (e: any) {
    alert('移动失败: ' + (e.response?.data?.message || e.response?.data?.error || e.message))
  }
}

const doMoveToCategory = (album: any, category: any) => {
  doMoveAlbum(album, category.path)
}

const doMoveToParent = (album: any) => {
  if (!album) return
  const pathInfo = splitPath(album.path)
  if (pathInfo.parts.length < 2) {
    alert('已经在最顶层，无法向上移动')
    return
  }
  const parentOfParent = joinPath(
    pathInfo.parts.slice(0, -2),
    pathInfo.isAbsolute,
    pathInfo.hasLeadingSlash
  )
  doMoveAlbum(album, parentOfParent)
}

const doMoveToChild = (album: any, dir: any) => {
  doMoveAlbum(album, dir.path)
}

const executeMoveWithResolution = (resolution: string) => {
  if (!moveConflictAlbum.value) return
  doMoveAlbum(moveConflictAlbum.value, moveConflictTargetPath.value, resolution)
}

const openPathPicker = async (album: any) => {
  pathPickerAlbum.value = album
  pathPickerSelectedDir.value = null
  closeAllMenus()
  pathPickerVisible.value = true
  // 默认定位到当前相册的上级目录
  const pathInfo = splitPath(album.path)
  const parentPath = joinPath(pathInfo.parts.slice(0, -1), pathInfo.isAbsolute, pathInfo.hasLeadingSlash)
  await navigatePathPicker(parentPath)
}

const navigatePathPicker = async (dirPath: string) => {
  pathPickerLoading.value = true
  pathPickerSelectedDir.value = null
  try {
    const res = await api.get('/albums/move/directories', { params: { path: dirPath } })
    pathPickerCurrentPath.value = res.data.currentPath || dirPath
    pathPickerParentPath.value = res.data.parent || ''
    pathPickerDirs.value = res.data.directories || []
  } catch (e) {
    console.error('加载目录失败:', e)
  } finally {
    pathPickerLoading.value = false
  }
}

const selectPickerDir = (dir: any) => {
  pathPickerSelectedDir.value = dir
}

const confirmPathPicker = () => {
  if (!pathPickerAlbum.value) return
  const targetPath = pathPickerSelectedDir.value?.path || pathPickerCurrentPath.value
  pathPickerVisible.value = false
  doMoveAlbum(pathPickerAlbum.value, targetPath)
}

// 菜单相关方法
const openMenu = (event: MouseEvent, album: any) => {
  event.stopPropagation()
  showMenuForAlbum.value = album
  
  // 计算菜单位置，防止超出屏幕
  const menuWidth = 224 // w-56 = 14rem = 224px
  const menuHeight = 500 // 估算菜单最大高度
  const padding = 16 // 屏幕边缘留白
  
  let x = event.clientX
  let y = event.clientY
  
  // 如果右边空间不够，往左显示
  if (x + menuWidth + padding > window.innerWidth) {
    x = window.innerWidth - menuWidth - padding
  }
  
  // 如果底部空间不够往上显示
  if (y + menuHeight + padding > window.innerHeight) {
    y = window.innerHeight - menuHeight - padding
  }
  
  // 确保不超出顶部和左边
  x = Math.max(padding, x)
  y = Math.max(padding, y)
  
  menuPosition.value = { x, y }
}

// 计算菜单位置的样式
const menuStyle = computed(() => {
  return {
    left: menuPosition.value.x + 'px',
    top: menuPosition.value.y + 'px'
  }
})

const closeAllMenus = () => {
  showMenuForAlbum.value = null
  tagDialogVisible.value = false
  effectsDialogVisible.value = false
  coverDialogVisible.value = false
  descriptionDialogVisible.value = false
  renameDialogVisible.value = false
  moveChildDirs.value = []
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
  // 先关闭菜单
  closeAllMenus()

  const newValue = !album.aggregateSubAlbums
  const actionText = newValue ? '开启' : '关闭'

  // 找到当前相册的索引位置，用于插入子相册
  const currentIndex = albums.value.findIndex(a => a.id === album.id)

  try {
    await api.put(`/albums/${album.id}/aggregate-sub-albums`, {
      aggregateSubAlbums: newValue
    })

    if (newValue) {
      // 开启聚合：只更新当前相册状态
      updateAlbumData(album.id, {
        aggregateSubAlbums: true
      })
    } else {
      // 关闭聚合：需要从后端获取子相册（因为开启聚合时子相册不显示）
      const albumPathInfo = splitPath(album.path)
      const albumPathParts = albumPathInfo.parts
      const albumPathPrefix = album.path.replace(/\\/g, '/')

      // 先从当前列表过滤
      let subAlbums = albums.value.filter(a => {
        // 跳过自己
        if (a.id === album.id) return false

        const aPath = a.path.replace(/\\/g, '/')
        const aPathInfo = splitPath(a.path)
        const aPathParts = aPathInfo.parts

        // 直接子相册必须是：
        // 1. 路径以当前相册路径 + '/' 开头
        // 2. 层级恰好是当前相册层级 + 1
        const isDirectChild = aPath.startsWith(albumPathPrefix + '/') &&
          aPathParts.length === albumPathParts.length + 1

        return isDirectChild
      })

      console.log('关闭聚合 - 从列表找到的子相册:', subAlbums.map(a => a.name))

      // 如果当前列表没有子相册，尝试从后端获取
      if (subAlbums.length === 0) {
        console.log('关闭聚合 - 当前列表没有子相册，尝试从后端获取')
        try {
          // 调用后端 API 获取子相册
          const response = await api.get(`/albums/${album.id}/sub-albums`)
          subAlbums = response.data || []
          console.log('关闭聚合 - 从后端获取的子相册:', subAlbums.map(a => a.name))
        } catch (fetchError: any) {
          console.warn('从后端获取子相册失败:', fetchError)
        }
      }

      console.log('关闭聚合 - 最终找到的子相册:', subAlbums.map(a => a.name))
      console.log('关闭聚合 - 当前相册路径:', album.path)
      console.log('关闭聚合 - 当前相册层级:', albumPathParts.length)

      // 移除当前相册（它将分裂成多个子相册）
      albums.value = albums.value.filter(a => a.id !== album.id)

      // 在原来位置插入子相册
      let insertIndex = currentIndex >= 0 ? currentIndex : albums.value.length
      for (const subAlbum of subAlbums) {
        if (!albums.value.find(a => a.id === subAlbum.id)) {
          albums.value.splice(insertIndex, 0, subAlbum)
          insertIndex++
        }
      }

      // 如果没有获取到子相册，保留当前相册但更新状态
      if (subAlbums.length === 0) {
        updateAlbumData(album.id, {
          aggregateSubAlbums: false
        })
        // 在原来位置重新添加当前相册
        albums.value.splice(currentIndex, 0, album)
        alert('未找到该相册的直接子相册，请刷新页面重试。')
      }
    }
  } catch (e: any) {
    alert(`${actionText}失败: ` + (e.response?.data?.error || e.message))
  }
}

// 统一的路径分割函数，同时支持 Windows(\) 和 Unix(/) 分隔符
// 返回路径分段和是否为绝对路径
const splitPath = (path: string): { parts: string[]; isAbsolute: boolean; hasLeadingSlash: boolean } => {
  // 检测是否有前导斜杠（对于 /D:/ 这种 Windows 路径）
  const hasLeadingSlash = /^\//.test(path)
  // 检测是否是绝对路径（以 / 开头，或者是 Windows 的 D: 这种格式）
  // 注意：Windows 路径可能是 D:\ 或 D:/ 或 /D:/ (带前导斜杠)
  const isAbsolute = hasLeadingSlash || /^[a-zA-Z]:/.test(path)
  // 先将反斜杠替换为正斜杠，然后分割
  const parts = path.replace(/\\/g, '/').split('/').filter(p => p.length > 0)
  return { parts, isAbsolute, hasLeadingSlash }
}

// 统一的路径连接函数，保留绝对路径标识
// 对于 Windows 盘符路径 (D: 开头)，保持原始格式（有前导斜杠就保留，没有就不加）
// 对于 Unix 绝对路径 (/ 开头)，保持前导斜杠
const joinPath = (parts: string[], isAbsolute: boolean, hasLeadingSlash?: boolean): string => {
  const joined = parts.join('/')
  if (!isAbsolute) {
    return joined
  }
  // 对于 Windows 盘符路径 (如 D:)，保持原始的前导斜杠状态
  if (parts.length > 0 && /^[a-zA-Z]:$/.test(parts[0])) {
    // 如果原始路径有前导斜杠（如 /D:/xxx），就保留
    if (hasLeadingSlash) {
      return '/' + joined
    }
    return joined
  }
  // 对于 Unix 绝对路径，添加前导斜杠
  return '/' + joined
}

const aggregateToParent = async (album: any) => {
  // 先关闭菜单
  closeAllMenus()

  // 调试信息：打印相册路径，帮助诊断问题
  console.log('聚合到上一级 - 相册路径:', album.path)
  console.log('聚合到上一级 - 相册名称:', album.name)
  console.log('聚合到上一级 - isTopLevel:', album.isTopLevel)

  // 首先使用后端的 isTopLevel 字段进行判断（如果后端正确计算了的话）
  if (album.isTopLevel === true) {
    alert(`该相册"${album.displayTitle || album.name}"已经是顶级相册，无法聚合到上一级。\n\n路径: ${album.path}\n\n注意：顶级相册是指位于基础路径分类目录下的相册（如：基础路径/人像/相册名）`)
    return
  }

  // 使用统一的路径分割函数（保留绝对路径信息）
  const pathInfo = splitPath(album.path)
  const pathParts = pathInfo.parts
  const isAbsolutePath = pathInfo.isAbsolute
  const hasLeadingSlash = pathInfo.hasLeadingSlash
  console.log('路径分割结果:', pathParts, '长度:', pathParts.length, '是否绝对路径:', isAbsolutePath, '有前导斜杠:', hasLeadingSlash)

  // 检查路径层级：至少需要 base/分类/相册名 三级才能有父相册
  // 也就是 pathParts.length >= 4（例如：/photos/base/分类/相册名/子相册）
  // 注意：Windows 路径可能是 D:/photos/base/...，所以要考虑盘符的情况
  const minDepth = isAbsolutePath ? 4 : 3 // 绝对路径需要 base/分类/相册名/子相册

  if (pathParts.length < minDepth) {
    const detailMsg = `\n\n详细信息：\n` +
      `- 当前路径: ${album.path}\n` +
      `- 路径层级数: ${pathParts.length}\n` +
      `- 最小需要层级: ${minDepth}\n` +
      `- 路径分段: ${JSON.stringify(pathParts)}`
    alert(`该相册"${album.displayTitle || album.name}"已经是顶级相册，无法聚合到上一级。${detailMsg}`)
    return
  }

  // 构造父相册路径（保留绝对路径标识）
  const parentPath = joinPath(pathParts.slice(0, -1), isAbsolutePath, hasLeadingSlash)
  console.log('父相册路径:', parentPath)

  // 查找父相册（使用统一处理后的路径比较）
  // 同时尝试原始路径和标准化后的路径
  let parentAlbum = albums.value.find(a => {
    const aPathInfo = splitPath(a.path)
    const normalizedPath = joinPath(aPathInfo.parts, aPathInfo.isAbsolute, aPathInfo.hasLeadingSlash)
    return a.path === parentPath ||
      normalizedPath === parentPath ||
      a.path.replace(/\\/g, '/') === parentPath
  })

  // 如果父相册不存在，尝试创建它
  if (!parentAlbum) {
    try {
      console.log('父相册不存在，尝试创建:', parentPath)

      // 直接创建父相册
      const createResponse = await api.post('/albums', { path: parentPath })
      parentAlbum = createResponse.data

      if (!parentAlbum) {
        alert(`无法创建父相册，请检查文件夹路径是否正确。\n\n父相册路径: ${parentPath}\n子相册路径: ${album.path}`)
        return
      }

      console.log('父相册创建成功:', parentAlbum)
    } catch (createError: any) {
      console.error('创建父相册失败:', createError)
      const errorDetail = createError.response?.data?.error || createError.response?.data?.message || createError.message
      alert(`创建父相册失败: ${errorDetail}\n\n父相册路径: ${parentPath}\n子相册路径: ${album.path}`)
      return
    }
  }

  // 找出同一层级的所有相册（父路径相同的相册）
  const siblingAlbums = albums.value.filter(a => {
    const aPathInfo = splitPath(a.path)
    return aPathInfo.parts.slice(0, -1).join('/') === pathParts.slice(0, -1).join('/')
  })
  const siblingIds = siblingAlbums.map(a => a.id)
  console.log('同一层级的相册:', siblingIds)
  console.log('父相册信息:', parentAlbum)

  // 找到第一个同级相册的索引位置，用于插入父相册
  const firstSiblingIndex = albums.value.findIndex(a => siblingIds.includes(a.id))

  try {
    await api.put(`/albums/${parentAlbum.id}/aggregate-sub-albums`, {
      aggregateSubAlbums: true
    })

    // 重新获取父相册的完整信息（包含照片数量）
    let updatedParentAlbum = parentAlbum
    try {
      const parentResponse = await api.get(`/albums/${parentAlbum.id}`)
      updatedParentAlbum = parentResponse.data
      console.log('获取到更新后的父相册信息:', updatedParentAlbum)
    } catch (getError: any) {
      console.warn('获取更新后的父相册信息失败，使用之前的信息:', getError)
      updatedParentAlbum = { ...parentAlbum, aggregateSubAlbums: true }
    }

    // 直接更新本地数据，避免重新加载导致滚动丢失
    // 1. 移除同一层级的所有相册
    albums.value = albums.value.filter(a => !siblingIds.includes(a.id))
    // 2. 在第一个同级相册的位置插入父相册
    const insertIndex = firstSiblingIndex >= 0 ? firstSiblingIndex : albums.value.length
    albums.value.splice(insertIndex, 0, updatedParentAlbum)
    console.log('更新后的相册列表:', albums.value)
  } catch (e: any) {
    const errorDetail = e.response?.data?.error || e.response?.data?.message || e.message
    alert(`聚合到上一级失败: ${errorDetail}\n\n子相册: ${album.displayTitle || album.name}\n父相册: ${parentAlbum.displayTitle || parentAlbum.name}`)
  }
}

const setAlbumSortOrder = async (album: any, sortOrder: string) => {
  try {
    await api.put(`/albums/${album.id}/photo-sort-order`, {
      photoSortOrder: sortOrder
    })
    // 直接更新本地数据，避免重新加载导致滚动丢失
    updateAlbumData(album.id, {
      photoSortOrder: sortOrder
    })
    closeAllMenus()
  } catch (e: any) {
    alert('设置排序方式失败: ' + (e.response?.data?.error || e.message))
  }
}

const setAlbumDownloadAllowed = async (album: any, downloadAllowed: string) => {
  try {
    const value = downloadAllowed === '' ? null : (downloadAllowed === 'true')
    await api.put(`/albums/${album.id}/download-allowed`, {
      downloadAllowed: value
    })
    // 直接更新本地数据，避免重新加载导致滚动丢失
    updateAlbumData(album.id, {
      downloadAllowed: value
    })
    closeAllMenus()
  } catch (e: any) {
    alert('设置下载权限失败: ' + (e.response?.data?.error || e.message))
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
    if (moveConflictDialogVisible.value) {
      moveConflictDialogVisible.value = false
      return
    }
    if (pathPickerVisible.value) {
      pathPickerVisible.value = false
      return
    }
    if (renameDialogVisible.value) {
      renameDialogVisible.value = false
      return
    }
    if (descriptionDialogVisible.value) {
      descriptionDialogVisible.value = false
      return
    }
    if (tagDialogVisible.value) {
      tagDialogVisible.value = false
      return
    }
    if (effectsDialogVisible.value) {
      effectsDialogVisible.value = false
      return
    }
    if (coverDialogVisible.value) {
      coverDialogVisible.value = false
      return
    }
    if (showMenuForAlbum.value) {
      showMenuForAlbum.value = null
      return
    }
    // 所有弹窗都关闭后，返回首页
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
  initScrollObserver()
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', handleGlobalKeydown)
  cleanupScrollObserver()
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

/* 毛玻璃菜单样式 */
.glass-menu {
  background: rgba(31, 41, 55, 0.75);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  border: 1px solid rgba(75, 85, 99, 0.4);
  box-shadow: 0 10px 40px rgba(0, 0, 0, 0.5);
}

/* 毛玻璃弹窗样式 */
.glass-dialog {
  background: rgba(31, 41, 55, 0.85);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border: 1px solid rgba(75, 85, 99, 0.4);
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.6);
}
</style>
