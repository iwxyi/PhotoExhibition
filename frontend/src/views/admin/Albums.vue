<template>
  <div class="min-h-screen admin-shell admin-albums-page" :class="{ 'admin-albums-page--selection': selectionMode }">
    <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 admin-content-rail">
      <div class="glass-panel admin-query-toolbar admin-query-toolbar--compact">
        <div class="admin-query-toolbar__fields">
          <input v-model="keyword" aria-label="搜索相册" placeholder="搜索相册" class="admin-field admin-albums-field admin-query-toolbar__search" @keyup.enter="load" />
          <button v-on:click="load" :disabled="loading" class="admin-button-soft admin-query-toolbar__icon-button disabled:opacity-50" title="搜索相册" aria-label="搜索相册">
            <svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="11" cy="11" r="6" /><path d="m16 16 4 4" /></svg>
          </button>
          <span v-if="loading && albums.length === 0" class="admin-albums-empty-state admin-query-toolbar__status">加载中...</span>
        </div>
        <div class="admin-query-toolbar__actions">
          <button v-if="albums.length" @click="toggleSelectionMode" class="admin-query-toolbar__button admin-button-soft">
            {{ selectionMode ? '退出多选' : '批量管理' }}
          </button>
          <template v-if="selectionMode">
            <span class="admin-albums-selection-count">已选 {{ selectedAlbumIds.length }}</span>
            <button @click="selectAllAlbums" class="admin-query-toolbar__button admin-button-soft">全选</button>
            <button @click="batchSetHidden(false)" :disabled="!selectedAlbumIds.length || batchLoading" class="admin-query-toolbar__button admin-button-soft disabled:opacity-50">显示</button>
            <button @click="batchSetHidden(true)" :disabled="!selectedAlbumIds.length || batchLoading" class="admin-query-toolbar__button admin-button-soft disabled:opacity-50">隐藏</button>
            <button @click="openBatchTagDialog" :disabled="!selectedAlbumIds.length || batchLoading" class="admin-query-toolbar__button admin-button-soft disabled:opacity-50">加标签</button>
            <button @click="batchDelete" :disabled="!selectedAlbumIds.length || batchLoading" class="admin-query-toolbar__button admin-button-danger disabled:opacity-50">删除</button>
          </template>
          <button v-on:click="load" :disabled="loading" class="admin-button-soft admin-query-toolbar__button disabled:opacity-50">刷新</button>
          <button v-if="authStore.isSuperAdmin" v-on:click="forceScanAndRebuild" :disabled="loading" class="btn-primary admin-query-toolbar__button disabled:opacity-50">重新扫描</button>
        </div>
      </div>

      <div class="admin-albums-grid" @click="closeAllMenus">
        <div
          v-for="album in albums"
          :key="album.id"
          class="admin-albums-card glass-panel relative overflow-hidden hover:ring-2 hover:ring-blue-500/80 transition-all flex flex-col"
          :class="{ 'admin-albums-card--hidden': album.isHidden }"
          tabindex="0"
          @keydown.enter.prevent="openPhotoManageModal(album)"
          @contextmenu.prevent.stop="openMenu($event, album)"
        >
          <label v-show="selectionMode" class="admin-albums-card-select absolute top-2 left-2 z-20">
            <input type="checkbox" :checked="selectedAlbumIds.includes(album.id)" @click.stop="toggleAlbumSelection(album.id)" />
          </label>
          <button
            @click="openMenu($event, album)"
            class="admin-albums-card-menu absolute top-2 right-2 z-20"
            title="更多操作"
            aria-label="相册更多操作"
            aria-haspopup="menu"
            :aria-expanded="showMenuForAlbum?.id === album.id"
          >
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <circle cx="12" cy="5" r="1.55" />
              <circle cx="12" cy="12" r="1.55" />
              <circle cx="12" cy="19" r="1.55" />
            </svg>
          </button>
          <!-- 封面预览 -->
          <div
            class="relative overflow-hidden cursor-pointer hover:opacity-90 transition-opacity"
            @click="openPhotoManageModal(album)"
            title="点击管理照片"
          >
            <CoverDisplay
              :covers="getAlbumCovers(album)"
              :default-covers="getDefaultCovers(album)"
              :photo-count="album.photoCount || 0"
              size="md"
            />
            <!-- 聚合标签 - 封面右上角 -->
            <div
              v-if="album.aggregateSubAlbums"
              class="absolute top-2 right-11 px-2 py-0.5 bg-blue-500/30 backdrop-blur-sm rounded text-xs text-blue-200 border border-blue-400/30"
              title="已开启聚合下级相册"
            >
              聚合
            </div>
          </div>

          <!-- 相册信息 -->
          <div class="p-3 flex flex-col flex-grow">
            <!-- 固定内容区 -->
            <div class="flex-shrink-0">
            <div class="admin-albums-card-heading flex items-center gap-2 mb-0.5">
                <h3 class="min-w-0 flex-1 truncate text-sm font-medium leading-5" :title="album.displayTitle || album.name">
                  {{ album.displayTitle || album.name }}
                </h3>
                <span class="admin-albums-card-date admin-albums-meta shrink-0 whitespace-nowrap text-[11px] text-gray-500">
                  {{ formatDate(album.takenAt) }}
                </span>
              </div>
            </div>

            <!-- 可伸缩的中间区域 -->
            <div class="flex-grow min-h-0 mt-2">
              <!-- 特效 -->
              <div v-if="album.atmosphereEffects && album.atmosphereEffects.length > 0" class="mb-2">
                <div class="flex flex-wrap gap-1">
                  <span
                    v-for="effect in album.atmosphereEffects.slice(0, 2)"
                    :key="effect.type"
                    class="admin-albums-effect-pill px-2 py-0.5 bg-purple-500/20 border border-purple-500/40 rounded-md text-xs inline-flex items-center gap-1 cursor-pointer hover:bg-purple-500/30 transition-colors"
                    :title="'点击编辑特效: ' + getEffectDisplayName(effect.type)"
                    @click.stop="editAlbumEffects(album)"
                  >
                    <svg class="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M11.049 2.927c.3-.921 1.603-.921 1.902 0l1.519 4.674a1 1 0 00.95.69h4.915c.969 0 1.371 1.24.588 1.81l-3.976 2.888a1 1 0 00-.363 1.118l1.518 4.674c.3.922-.755 1.688-1.538 1.118l-3.976-2.888a1 1 0 00-1.176 0l-3.976 2.888c-.783.57-1.838-.197-1.538-1.118l1.518-4.674a1 1 0 00-.363-1.118l-3.976-2.888c-.784-.57-.38-1.81.588-1.81h4.914a1 1 0 00.951-.69l1.519-4.674z" />
                    </svg>
                    {{ getEffectDisplayName(effect.type) }}({{ getIntensityDisplay(effect.intensity || 'medium') }}|{{ effect.layer === 'background' ? '下' : '上' }})
                  </span>
                  <span v-if="album.atmosphereEffects.length > 2" class="admin-albums-overflow-pill">+{{ album.atmosphereEffects.length - 2 }}</span>
                </div>
              </div>

              <!-- 标签 -->
              <div v-if="album.tags && album.tags.length > 0" class="mb-2">
                <div class="flex flex-wrap gap-1">
                  <span
                    v-for="t in album.tags.slice(0, 3)"
                    :key="t.id"
                    class="admin-albums-tag-pill px-1.5 py-0.5 bg-blue-500/20 border border-blue-500/40 rounded text-xs inline-flex items-center gap-1 cursor-pointer hover:bg-blue-500/30 transition-colors"
                    :title="'点击编辑标签'"
                    @click.stop="editAlbumTags(album)"
                  >
                    {{ t.name }}
                    <button
                      v-on:click.stop="removeTag(album, t.id)"
                      class="admin-albums-tag-remove hover:text-red-400"
                      title="移除标签"
                    >
                      ×
                    </button>
                  </span>
                  <span v-if="album.tags.length > 3" class="admin-albums-overflow-pill">+{{ album.tags.length - 3 }}</span>
                </div>
              </div>

              <!-- 备注 -->
              <div v-if="album.description" class="admin-albums-description text-xs text-gray-300 bg-gray-900/50 p-1.5 rounded line-clamp-1">
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
          class="absolute glass-menu admin-albums-menu admin-albums-menu--album rounded-lg shadow-2xl z-10 w-48"
          :style="menuStyle"
          role="menu"
          @click.stop
        >
          <div class="py-1">
            <!-- 查看相册菜单项 -->
            <button
              @click="openAlbum(showMenuForAlbum.id); closeAllMenus()"
              class="w-full text-left px-4 py-2 text-sm text-gray-300 hover:bg-gray-700 flex items-center gap-2"
            >
              <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z" />
              </svg>
              查看相册
            </button>
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
            <!-- 编辑操作分组 -->
            <div class="admin-albums-menu__section-label px-4 pt-2 pb-1 text-[10px]">编辑</div>
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
            <div class="relative" @mouseenter="openMoveMenu($event)" @mouseleave="scheduleMoveMenuClose">
              <button
                class="w-full text-left px-4 py-2 text-sm text-gray-300 hover:bg-gray-700 flex items-center justify-between"
                @mouseenter="loadMoveMenuData(showMenuForAlbum)"
                aria-haspopup="menu"
                :aria-expanded="showMoveMenu"
              >
                <span class="flex items-center gap-2">
                  <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 7h12m0 0l-4-4m4 4l-4 4m0 6H4m0 0l4 4m-4-4l4-4" />
                  </svg>
                  移动至
                </span>
                <svg class="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5l7 7-7 7" /></svg>
              </button>
              <teleport to="body">
              <!-- 子菜单提升到 body，避免一级菜单的 backdrop-filter 成为它的背板根。 -->
              <div
                v-show="showMoveMenu"
                class="fixed glass-menu admin-albums-menu admin-albums-menu--nested rounded-lg shadow-2xl z-[60] overflow-y-auto"
                :style="moveMenuStyle"
                role="menu"
                @click.stop
                @mouseenter="cancelMoveMenuClose"
                @mouseleave="scheduleMoveMenuClose"
              >
                <div class="py-1">
                  <!-- 分类 -->
                  <button
                    class="w-full text-left px-4 py-2 text-sm text-gray-300 hover:bg-gray-700 flex items-center justify-between"
                    @mouseenter="openMoveSubmenu('categories', $event)"
                    aria-haspopup="menu"
                    :aria-expanded="activeMoveSubmenu === 'categories'"
                  >
                      <span>分类</span>
                      <svg class="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5l7 7-7 7" /></svg>
                  </button>
                  <!-- 上一级 -->
                  <button
                    @click="doMoveToParent(showMenuForAlbum)"
                    class="w-full text-left px-4 py-2 text-sm text-gray-300 hover:bg-gray-700"
                  >
                    上一级
                  </button>
                  <!-- 下一级 -->
                  <button
                      class="w-full text-left px-4 py-2 text-sm text-gray-300 hover:bg-gray-700 flex items-center justify-between"
                      @mouseenter="openMoveSubmenu('children', $event)"
                      aria-haspopup="menu"
                      :aria-expanded="activeMoveSubmenu === 'children'"
                  >
                      <span>下一级</span>
                      <svg class="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5l7 7-7 7" /></svg>
                  </button>
                  <!-- 分割线 -->
                  <div class="border-t border-gray-600 my-1"></div>
                  <!-- 合并至同级 -->
                  <button
                      class="w-full text-left px-4 py-2 text-sm text-gray-300 hover:bg-gray-700 flex items-center justify-between"
                      @mouseenter="openMoveSubmenu('siblings', $event)"
                      aria-haspopup="menu"
                      :aria-expanded="activeMoveSubmenu === 'siblings'"
                  >
                      <span>合并至同级</span>
                      <svg class="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5l7 7-7 7" /></svg>
                  </button>
                  <!-- 指定路径 -->
                  <button
                    @click="openPathPicker(showMenuForAlbum)"
                    class="w-full text-left px-4 py-2 text-sm text-gray-300 hover:bg-gray-700"
                  >
                    指定路径…
                  </button>
                </div>
              </div>
              </teleport>
              <teleport to="body">
                <div
                  v-if="activeMoveSubmenu"
                  class="fixed glass-menu admin-albums-menu admin-albums-menu--nested rounded-lg shadow-2xl z-[70] max-h-[calc(100vh-2rem)] overflow-y-auto"
                  :style="moveSubmenuStyle"
                  role="menu"
                  @click.stop
                  @mouseenter="cancelMoveMenuClose"
                  @mouseleave="scheduleMoveMenuClose"
                >
                  <div class="py-1">
                    <template v-if="activeMoveSubmenu === 'categories'">
                      <button
                        v-for="cat in moveCategories"
                        :key="cat.path"
                        @click="doMoveToCategory(showMenuForAlbum, cat)"
                        class="w-full text-left px-4 py-2 text-sm text-gray-300 hover:bg-gray-700 truncate"
                        :title="cat.name"
                      >{{ cat.name }}</button>
                      <div v-if="moveCategories.length === 0" class="px-4 py-2 text-xs text-gray-500">暂无分类</div>
                    </template>
                    <template v-else-if="activeMoveSubmenu === 'children'">
                      <button
                        v-for="dir in moveChildDirs"
                        :key="dir.path"
                        @click="doMoveToChild(showMenuForAlbum, dir)"
                        class="w-full text-left px-4 py-2 text-sm text-gray-300 hover:bg-gray-700 truncate"
                        :title="dir.name"
                      >{{ dir.name }}</button>
                      <div v-if="moveChildDirs.length === 0" class="px-4 py-2 text-xs text-gray-500">暂无子目录</div>
                    </template>
                    <template v-else>
                      <button
                        v-for="dir in mergeSiblingDirs"
                        :key="dir.path"
                        @click="doMergeToSibling(showMenuForAlbum, dir)"
                        class="w-full text-left px-4 py-2 text-sm text-gray-300 hover:bg-gray-700 truncate"
                        :title="dir.name"
                      >{{ dir.name }}</button>
                      <div v-if="mergeSiblingDirs.length === 0" class="px-4 py-2 text-xs text-gray-500">暂无同级目录</div>
                    </template>
                  </div>
                </div>
              </teleport>
            </div>
            <!-- 隐藏相册菜单项 -->
            <button
              @click="toggleAlbumHidden(showMenuForAlbum, !showMenuForAlbum.isHidden); closeAllMenus()"
              class="w-full text-left px-4 py-2 text-sm text-gray-300 hover:bg-gray-700 flex items-center gap-2"
            >
              <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13.875 18.825A10.05 10.05 0 0112 19c-4.478 0-8.268-2.943-9.543-7a9.97 9.97 0 011.563-3.029m5.858.908a3 3 0 114.243 4.243M9.878 9.878l4.242 4.242M9.88 9.88l-3.29-3.29m7.532 7.532l3.29 3.29M3 3l3.59 3.59m0 0A9.953 9.953 0 0112 5c4.478 0 8.268 2.943 9.543 7a10.025 10.025 0 01-4.132 5.411m0 0L21 21" />
              </svg>
              {{ showMenuForAlbum.isHidden ? '显示相册' : '隐藏相册' }}
            </button>
            <!-- 删除菜单项 -->
            <button
              @click="deleteAlbum(showMenuForAlbum)"
              class="admin-albums-menu__button admin-albums-menu__button--danger w-full text-left px-4 py-2 text-sm text-red-400 hover:bg-red-900/50 flex items-center gap-2"
            >
              <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
              </svg>
              删除相册
            </button>
            <!-- 结构与权限分组 -->
            <div class="admin-albums-menu__section-label px-4 pt-2 pb-1 text-[10px]">结构与权限</div>
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
                  class="admin-albums-toggle-dot w-3 h-3 rounded-full transition-colors"
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
              <label class="admin-albums-field-label block text-xs text-gray-400 mb-1">相册排序方式</label>
              <select
                :value="showMenuForAlbum.photoSortOrder"
                @change="setAlbumSortOrder(showMenuForAlbum, ($event.target as HTMLSelectElement).value)"
                class="admin-field admin-albums-inline-select w-full px-2 py-1 bg-gray-700 border border-gray-600 rounded text-xs text-gray-300 focus:outline-none focus:ring-1 focus:ring-blue-500"
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
              <label class="admin-albums-field-label block text-xs text-gray-400 mb-1">下载权限</label>
              <select
                :value="showMenuForAlbum.downloadAllowed"
                @change="setAlbumDownloadAllowed(showMenuForAlbum, ($event.target as HTMLSelectElement).value)"
                class="admin-field admin-albums-inline-select w-full px-2 py-1 bg-gray-700 border border-gray-600 rounded text-xs text-gray-300 focus:outline-none focus:ring-1 focus:ring-blue-500"
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
        <div class="glass-dialog admin-albums-dialog admin-albums-dialog--tag rounded-lg p-6 max-w-md w-full text-gray-100">
          <h3 class="text-lg font-medium mb-4 text-gray-100">{{ batchTagMode ? `为 ${selectedAlbumIds.length} 个相册添加标签` : '添加标签' }}</h3>
          <div class="mb-4">
            <label class="block space-y-2">
              <span class="admin-albums-field-label text-sm text-gray-300">标签名称</span>
              <input
                ref="tagInputRef"
                v-model="tagKeyword"
                v-on:input="searchTags"
                placeholder="搜索已有标签或输入新标签名称"
                class="admin-field admin-albums-field w-full px-3 py-2 bg-gray-700 border border-gray-600 rounded text-sm text-gray-100 placeholder-gray-400 focus:outline-none focus:ring-2 focus:ring-blue-500"
                v-on:keyup.enter="confirmAddTag"
              />
            </label>
          </div>
          <!-- 标签候选：瀑布流胶囊布局 -->
          <div class="admin-albums-tag-results max-h-60 overflow-auto mb-4 border border-gray-700 rounded bg-gray-900/60">
            <div v-if="filteredTags.length > 0" class="p-2">
              <div class="flex flex-wrap gap-2">
                <button
                  v-for="tag in filteredTags"
                  :key="tag.id"
                  v-on:click="selectTag(tag)"
                  class="admin-albums-tag-candidate px-3 py-1 rounded-full bg-gray-700 hover:bg-blue-600 text-xs text-gray-100 border border-gray-500 transition-colors cursor-pointer"
                >
                  {{ tag.name }}
                </button>
              </div>
            </div>
            <div v-else-if="tagKeyword.trim()" class="admin-albums-note px-3 py-2 text-gray-200 text-sm">
              没有找到标签 "{{ tagKeyword }}"，点击确定创建新标签
            </div>
            <div v-else class="admin-albums-note px-3 py-2 text-gray-200 text-sm">
              请输入标签名称
            </div>
          </div>
          <div class="flex gap-2">
            <button
              v-on:click="confirmAddTag"
              class="admin-button-primary flex-1 px-4 py-2 rounded text-sm disabled:opacity-50"
              :disabled="!tagKeyword.trim()"
            >
              确定
            </button>
            <button
              v-on:click="tagDialogVisible = false; batchTagMode = false"
              class="admin-button-soft flex-1 px-4 py-2 rounded text-sm"
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
        <div class="glass-dialog admin-albums-dialog admin-albums-dialog--effects rounded-lg max-w-2xl w-full max-h-[80vh] flex flex-col text-gray-100">
          <!-- 头部 -->
          <div class="p-6 pb-4">
            <h3 class="text-lg font-medium mb-4 text-gray-100">设置相册特效： <span class="admin-albums-note text-sm text-gray-400 mb-4"><strong>{{ currentAlbum?.displayTitle || currentAlbum?.name }}</strong></span></h3>
          </div>

          <!-- 可滚动内容区域 -->
          <div class="flex-1 overflow-y-auto px-6">

            <!-- 当前特效列表 -->
            <div class="mb-4">
              <h4 class="admin-albums-subtitle text-sm font-medium text-gray-300 mb-2">当前特效</h4>
              <div v-if="currentEffects.length === 0" class="admin-albums-empty-state text-sm text-gray-500 italic">
                暂无特效，可从下方添加
              </div>
              <div v-else class="space-y-2">
                <div
                  v-for="(effect, index) in currentEffects"
                  :key="index"
                  class="admin-albums-effect-card bg-gray-700/50 rounded p-3"
                >
                  <div class="flex items-center justify-between mb-2">
                    <div class="flex items-center gap-2">
                      <span class="text-sm font-medium">{{ getEffectName(effect.type) }}</span>
                      <span class="admin-albums-note text-xs text-gray-400">({{ getIntensityDisplay(effect.intensity || 'medium') }})</span>
                    </div>
                    <button
                      @click="removeEffect(index)"
                      class="admin-albums-effect-remove text-red-400 hover:text-red-300 text-sm"
                      title="移除特效"
                    >
                      ✕
                    </button>
                  </div>
                  <!-- 预设强度选择 -->
                  <div class="flex items-center gap-4 mb-3">
                    <div class="flex items-center gap-2">
                      <label class="admin-albums-field-label text-xs text-gray-400">预设强度:</label>
                      <select
                        :value="effect.intensity || 'medium'"
                        @change="updateEffectIntensity(index, ($event.target as HTMLSelectElement).value)"
                        class="admin-field admin-albums-inline-select px-2 py-1 bg-gray-600 border border-gray-500 rounded text-xs text-gray-200"
                      >
                        <option value="low">低</option>
                        <option value="medium">中</option>
                        <option value="high">高</option>
                        <option value="custom">自定义</option>
                      </select>
                    </div>
                    <div class="flex items-center gap-2">
                      <label class="admin-albums-field-label text-xs text-gray-400">层级:</label>
                      <select
                        :value="effect.layer || 'above'"
                        @change="updateEffectLayer(index, ($event.target as HTMLSelectElement).value)"
                        class="admin-field admin-albums-inline-select px-2 py-1 bg-gray-600 border border-gray-500 rounded text-xs text-gray-200"
                      >
                        <option value="above">图片上方</option>
                        <option value="background">背景层</option>
                      </select>
                    </div>
                  </div>

                  <!-- 按类型显示对应参数 -->
                  <div class="space-y-2">
                    <div class="admin-albums-field-label text-xs text-gray-400 mb-2">自定义参数 (1-10):</div>
                    <div class="grid grid-cols-2 gap-3">
                      <div
                        v-for="param in getEffectParams(effect.type)"
                        :key="param.key"
                        class="flex flex-col"
                      >
                        <label class="admin-albums-field-label text-xs text-gray-400 mb-1">{{ param.label }}: {{ getCustomValue(effect, param.key) }}</label>
                        <input
                          type="range"
                          :min="param.min || 1"
                          :max="param.max || 10"
                          :value="getCustomValue(effect, param.key)"
                          @input="updateCustomParam(index, param.key, parseInt(($event.target as HTMLInputElement).value))"
                          class="admin-albums-slider w-full h-2 bg-gray-600 rounded-lg appearance-none cursor-pointer slider"
                        />
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <!-- 添加特效 -->
            <div class="mb-6">
              <h4 class="admin-albums-subtitle text-sm font-medium text-gray-300 mb-3">添加特效</h4>
              <div class="max-h-60 overflow-y-auto">
                <div class="grid grid-cols-3 gap-3">
                  <div
                    v-for="effect in availableEffects"
                    :key="effect.type"
                    :class="[
                      'admin-albums-effect-option border rounded p-3 cursor-pointer transition-colors relative',
                      isEffectSelected(effect.type)
                        ? 'admin-albums-effect-option--active border-blue-500 bg-blue-500/20 text-blue-100'
                        : 'admin-albums-effect-option--idle border-gray-600 hover:border-blue-500/50 hover:bg-gray-700/30'
                    ]"
                    @click="toggleEffect(effect)"
                  >
                    <span
                      v-if="effect.category"
                      class="absolute top-1 right-1 px-1.5 py-0.5 bg-violet-500/30 border border-violet-500/50 rounded text-[10px] text-violet-300 leading-none"
                    >{{ effect.category }}</span>
                    <span
                      v-else-if="effect.imageMode"
                      class="absolute top-1 right-1 px-1.5 py-0.5 bg-emerald-500/30 border border-emerald-500/50 rounded text-[10px] text-emerald-300 leading-none"
                    >Canvas</span>
                    <div class="font-medium text-sm">{{ effect.name }}</div>
                    <div class="admin-albums-note text-xs text-gray-400 mt-1">{{ effect.description }}</div>
                  </div>
                </div>
              </div>
            </div>

          </div>

          <!-- 底部按钮区域 -->
          <div class="flex gap-3 p-6 pt-4 border-t border-gray-700">
            <button
              @click="saveAtmosphereEffects"
              class="admin-button-primary flex-1 px-4 py-2 rounded text-sm disabled:opacity-50"
              :disabled="!currentAlbum"
            >
              保存
            </button>
            <button
              @click="effectsDialogVisible = false"
              class="admin-button-soft flex-1 px-4 py-2 rounded text-sm"
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
        <div class="glass-dialog admin-albums-dialog admin-albums-dialog--cover rounded-lg max-w-4xl w-full max-h-[80vh] flex flex-col text-gray-100">
          <!-- 头部 -->
          <div class="p-4 border-b border-gray-700 flex items-center justify-between">
            <h3 class="text-lg font-medium">设置封面</h3>
            <button
              @click="coverDialogVisible = false"
              class="admin-albums-dialog-close text-gray-400 hover:text-white"
            >
              <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
              </svg>
            </button>
          </div>

          <!-- 已选封面预览 -->
          <div class="p-4 bg-gray-900/50 border-b border-gray-700">
            <div class="admin-albums-note text-sm text-gray-400 mb-2">已选封面 ({{ selectedCoverIds.length }}/4)</div>
            <div class="flex gap-2">
              <div
                v-for="id in selectedCoverIds"
                :key="id"
                class="admin-albums-cover-preview relative w-20 h-20 rounded overflow-hidden border-2 border-blue-500"
              >
                <img
                  :src="getPhotoUrl(coverDialogPhotos.find(p => p.id === id))"
                  class="w-full h-full object-cover"
                />
                <button
                  @click="toggleCoverSelection(coverDialogPhotos.find(p => p.id === id))"
                  class="admin-albums-cover-remove absolute top-0 right-0 bg-red-500 text-white w-5 h-5 flex items-center justify-center text-xs"
                >
                  ×
                </button>
                <div class="admin-albums-cover-order absolute bottom-0 left-0 right-0 bg-black/60 text-center text-xs py-0.5">
                  {{ getCoverIndex(id) }}
                </div>
              </div>
              <div
                v-if="selectedCoverIds.length === 0"
                class="admin-albums-empty-state text-gray-500 text-sm flex items-center"
              >
                未选择封面，将使用自动生成的封面
              </div>
            </div>
          </div>

          <!-- 照片列表 -->
          <div class="flex-1 overflow-y-auto p-4">
            <div v-if="coverDialogLoading" class="admin-albums-empty-state text-center py-8 text-gray-400">
              加载中...
            </div>
            <div v-else-if="coverDialogPhotos.length === 0" class="admin-albums-empty-state text-center py-8 text-gray-400">
              暂无照片
            </div>
            <div v-else class="grid grid-cols-4 md:grid-cols-6 lg:grid-cols-8 gap-2">
                <div
                  v-for="photo in coverDialogPhotos"
                  :key="photo.id"
                  :class="[
                  'admin-albums-cover-option relative aspect-square rounded overflow-hidden cursor-pointer transition-all',
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
                  class="admin-albums-cover-selected absolute inset-0 bg-blue-500/30 flex items-center justify-center"
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
              class="admin-button-soft px-4 py-2 rounded text-sm"
              :disabled="selectedCoverIds.length === 0"
            >
              清除封面
            </button>
            <div class="flex-1"></div>
            <button
              @click="coverDialogVisible = false"
              class="admin-button-soft px-4 py-2 rounded text-sm"
            >
              取消
            </button>
            <button
              @click="saveCover"
              class="admin-button-primary px-4 py-2 rounded text-sm"
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
        <div class="glass-dialog admin-albums-dialog admin-albums-dialog--description rounded-lg p-6 max-w-md w-full text-gray-100">
          <h3 class="text-lg font-medium mb-4 text-gray-100">编辑备注</h3>
          <label class="block space-y-2">
            <span class="admin-albums-field-label text-sm text-gray-300">相册备注</span>
            <textarea
              ref="descriptionInputRef"
              v-model="descriptionInput"
              rows="4"
              placeholder="输入相册说明、拍摄背景或其它备注"
              class="admin-field admin-albums-field w-full px-3 py-2 bg-gray-700/50 border border-gray-600 rounded text-sm text-gray-100 placeholder-gray-400 focus:outline-none focus:ring-2 focus:ring-blue-500 resize-none"
            ></textarea>
          </label>
          <div class="flex gap-3 mt-4">
            <button
              @click="saveDescription"
              class="admin-button-primary flex-1 px-4 py-2 rounded text-sm"
            >
              保存
            </button>
            <button
              @click="descriptionDialogVisible = false"
              class="admin-button-soft flex-1 px-4 py-2 rounded text-sm"
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
        <div class="glass-dialog admin-albums-dialog admin-albums-dialog--rename rounded-lg p-6 max-w-md w-full text-gray-100">
          <h3 class="text-lg font-medium mb-4 text-gray-100">重命名相册</h3>
          <label class="block space-y-2">
            <span class="admin-albums-field-label text-sm text-gray-300">相册新名称</span>
            <input
              ref="nameInputRef"
              v-model="nameInput"
              placeholder="输入新的相册名称"
              class="admin-field admin-albums-field w-full px-3 py-2 bg-gray-700/50 border border-gray-600 rounded text-sm text-gray-100 placeholder-gray-400 focus:outline-none focus:ring-2 focus:ring-blue-500"
              @keyup.enter="saveName"
            />
          </label>
          <div class="flex gap-3 mt-4">
            <button
              @click="saveName"
              class="admin-button-primary flex-1 px-4 py-2 rounded text-sm"
            >
              保存
            </button>
            <button
              @click="renameDialogVisible = false"
              class="admin-button-soft flex-1 px-4 py-2 rounded text-sm"
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
        <div class="glass-dialog admin-albums-dialog admin-albums-dialog--move-conflict rounded-lg p-6 max-w-lg w-full text-gray-100">
          <h3 class="admin-albums-warning-title text-lg font-medium mb-3 text-yellow-400 flex items-center gap-2">
            <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-2.5L13.732 4c-.77-.833-1.964-.833-2.732 0L4.082 16.5c-.77.833.192 2.5 1.732 2.5z" /></svg>
            同名文件夹冲突
          </h3>
          <p class="admin-albums-note text-sm text-gray-300 mb-3">{{ moveConflictInfo.message }}</p>
          <!-- 文件清单 -->
          <div v-if="moveConflictInfo.conflictFiles && moveConflictInfo.conflictFiles.length > 0" class="mb-4 max-h-40 overflow-y-auto bg-gray-900/60 rounded p-2 border border-gray-700">
            <div class="admin-albums-field-label text-xs text-gray-400 mb-1">目标文件夹内的文件：</div>
            <div v-for="f in moveConflictInfo.conflictFiles" :key="f" class="admin-albums-note text-xs text-gray-300 py-0.5 truncate" :title="f">
              📄 {{ f }}
            </div>
          </div>
          <div class="flex flex-col gap-2">
            <button
              @click="executeMoveWithResolution('overwrite')"
              class="admin-button-danger w-full px-4 py-2 rounded text-sm flex items-center justify-center gap-2"
            >
              覆盖（删除目标文件夹内容后移动）
            </button>
            <button
              @click="executeMoveWithResolution('rename')"
              class="admin-button-primary w-full px-4 py-2 rounded text-sm flex items-center justify-center gap-2"
            >
              重命名为 "{{ moveConflictInfo.suggestedNewName }}"
            </button>
            <button
              @click="moveConflictDialogVisible = false"
              class="admin-button-soft w-full px-4 py-2 rounded text-sm"
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
        <div class="glass-dialog admin-albums-dialog admin-albums-dialog--path-picker rounded-lg max-w-lg w-full max-h-[70vh] flex flex-col text-gray-100">
          <div class="p-4 border-b border-gray-700">
            <h3 class="text-lg font-medium">选择目标路径</h3>
            <p class="admin-albums-note text-xs text-gray-400 mt-1">将相册 "{{ pathPickerAlbum?.displayTitle || pathPickerAlbum?.name }}" 移动到选定目录</p>
          </div>
          <!-- 当前路径 -->
          <div class="px-4 py-2 bg-gray-900/50 border-b border-gray-700 flex items-center gap-2">
            <span class="admin-albums-field-label text-xs text-gray-400">当前：</span>
            <span class="admin-albums-note text-xs text-gray-200 truncate flex-1" :title="pathPickerCurrentPath">{{ pathPickerCurrentPath }}</span>
            <button
              v-if="pathPickerParentPath"
              @click="navigatePathPicker(pathPickerParentPath)"
              class="admin-button-soft px-2 py-1 rounded text-xs flex-shrink-0"
            >
              ↑ 上级
            </button>
          </div>
          <!-- 目录列表 -->
          <div class="flex-1 overflow-y-auto p-2">
            <div v-if="pathPickerLoading" class="admin-albums-empty-state text-center py-4 text-gray-400 text-sm">加载中...</div>
            <div v-else-if="pathPickerDirs.length === 0" class="admin-albums-empty-state text-center py-4 text-gray-500 text-sm">该目录下无子目录</div>
            <div v-else>
              <button
                v-for="dir in pathPickerDirs"
                :key="dir.path"
                @dblclick="navigatePathPicker(dir.path)"
                @click="selectPickerDir(dir)"
                :class="[
                  'w-full text-left px-3 py-2 rounded text-sm flex items-center gap-2 mb-1',
                  pathPickerSelectedDir?.path === dir.path ? 'admin-albums-picker-option admin-albums-picker-option--active bg-blue-600/30 border border-blue-500/50' : 'admin-albums-picker-option hover:bg-gray-700/50'
                ]"
              >
                <svg class="w-4 h-4 text-yellow-400 flex-shrink-0" fill="currentColor" viewBox="0 0 20 20"><path d="M2 6a2 2 0 012-2h5l2 2h5a2 2 0 012 2v6a2 2 0 01-2 2H4a2 2 0 01-2-2V6z" /></svg>
                <span class="truncate">{{ dir.name }}</span>
              </button>
            </div>
          </div>
          <!-- 底部按钮 -->
          <div class="p-4 border-t border-gray-700 flex gap-3">
            <div class="admin-albums-note text-xs text-gray-400 flex-1 self-center truncate">
              移动到：{{ pathPickerSelectedDir?.path || pathPickerCurrentPath }}
            </div>
            <button
              @click="pathPickerVisible = false"
              class="admin-button-soft px-4 py-2 rounded text-sm"
            >
              取消
            </button>
            <button
              @click="confirmPathPicker"
              class="admin-button-primary px-4 py-2 rounded text-sm"
            >
              确认移动
            </button>
          </div>
        </div>
      </div>
    </teleport>

    <!-- 照片管理弹窗 -->
    <teleport to="body">
      <div
        v-if="photoModalVisible"
        class="fixed inset-0 z-50 flex items-center justify-center p-4"
        @click.self="closePhotoModal"
      >
        <div class="glass-dialog admin-albums-dialog admin-albums-dialog--photos rounded-lg w-full sm:max-w-5xl max-h-[90vh] sm:max-h-[85vh] flex flex-col text-gray-100 overflow-hidden">
          <!-- Header -->
          <div class="flex flex-col sm:flex-row sm:items-center justify-between p-3 sm:p-4 border-b border-gray-600/50 flex-shrink-0 gap-2">
            <div class="flex-1 min-w-0">
              <h3 class="text-base sm:text-lg font-medium truncate">{{ photoModalAlbum?.displayTitle || photoModalAlbum?.name }}</h3>
              <div class="flex items-center gap-2 text-xs text-gray-400 mt-1">
                <span>{{ photoModalPhotos.length }} 张照片</span>
                <span v-if="photoModalSelected.size > 0" class="admin-albums-selection-note text-blue-400">
                  已选 {{ photoModalSelected.size }} 张
                </span>
              </div>
            </div>
            <div class="flex items-center gap-2 flex-shrink-0">
              <!-- Select all -->
              <button
                v-if="photoModalPhotos.length > 0"
                @click="toggleSelectAllPhotos"
                class="admin-button-soft px-3 py-1.5 rounded text-xs transition-colors"
              >
                {{ photoModalSelected.size === photoModalPhotos.length ? '取消全选' : '全选' }}
              </button>
              <!-- Batch actions dropdown -->
              <DropMenu
                v-model:visible="photoMoveMenuVisible"
                trigger="click"
                :offset="4"
              >
                <template #trigger>
                  <button class="admin-button-primary px-3 py-1.5 rounded text-xs transition-colors flex items-center gap-1">
                    <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 5v.01M12 12v.01M12 19v.01M12 6a1 1 0 110-2 1 1 0 010 2zm0 7a1 1 0 110-2 1 1 0 010 2zm0 7a1 1 0 110-2 1 1 0 010 2z" />
                    </svg>
                    更多操作
                  </button>
                </template>

                <div class="w-56 glass-menu admin-albums-menu admin-albums-menu--photo rounded-lg shadow-2xl py-1">
                  <!-- 移动至 -->
                  <DropMenu trigger="hover" placement="right" :offset="2">
                    <template #trigger>
                      <button class="admin-albums-menu__button w-full text-left px-4 py-2 text-sm text-gray-300 hover:bg-gray-700 flex items-center justify-between">
                        <span class="flex items-center gap-2">
                          <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 7h12m0 0l-4-4m4 4l-4 4m0 6H4m0 0l4 4m-4-4l4-4" />
                          </svg>
                          移动至
                        </span>
                        <svg class="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5l7 7-7 7" />
                        </svg>
                      </button>
                    </template>

                    <div class="glass-menu admin-albums-menu admin-albums-menu--photo-sub rounded-lg shadow-xl py-1 min-w-[180px]">
                      <!-- 上一级 -->
                      <button
                        v-if="photoMoveTargets.parentDir"
                        @click="doMovePhotosTo(photoMoveTargets.parentDir.path)"
                        class="admin-albums-menu__button w-full text-left px-3 py-2 text-sm text-gray-300 hover:bg-gray-700 truncate"
                        :title="photoMoveTargets.parentDir.name"
                      >
                        ⬆️ 上一级 ({{ photoMoveTargets.parentDir.name }})
                      </button>
                      <!-- 同级目录 -->
                      <DropMenu v-if="photoMoveTargets.siblingDirs?.length > 0" trigger="hover" placement="right" :offset="2">
                        <template #trigger>
                          <button class="admin-albums-menu__button w-full text-left px-3 py-2 text-sm text-gray-300 hover:bg-gray-700 flex items-center justify-between">
                            <span>📂 同级 ({{ photoMoveTargets.siblingDirs.length }})</span>
                            <svg class="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5l7 7-7 7" />
                            </svg>
                          </button>
                        </template>
                        <div class="glass-menu admin-albums-menu admin-albums-menu--photo-sub rounded-lg shadow-xl py-1 min-w-[160px]">
                          <button
                            v-for="dir in photoMoveTargets.siblingDirs"
                            :key="'sibling-' + dir.path"
                            @click="doMovePhotosTo(dir.path)"
                            class="admin-albums-menu__button w-full text-left px-3 py-2 text-sm text-gray-300 hover:bg-gray-700 truncate"
                            :title="dir.name"
                          >
                            📁 {{ dir.name }}
                          </button>
                        </div>
                      </DropMenu>
                      <!-- 下一级 -->
                      <DropMenu v-if="photoMoveTargets.childDirs?.length > 0" trigger="hover" placement="right" :offset="2">
                        <template #trigger>
                          <button class="admin-albums-menu__button w-full text-left px-3 py-2 text-sm text-gray-300 hover:bg-gray-700 flex items-center justify-between">
                            <span>📂 下一级 ({{ photoMoveTargets.childDirs.length }})</span>
                            <svg class="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5l7 7-7 7" />
                            </svg>
                          </button>
                        </template>
                        <div class="glass-menu admin-albums-menu admin-albums-menu--photo-sub rounded-lg shadow-xl py-1 min-w-[160px]">
                          <button
                            v-for="dir in photoMoveTargets.childDirs"
                            :key="'child-' + dir.path"
                            @click="doMovePhotosTo(dir.path)"
                            class="admin-albums-menu__button w-full text-left px-3 py-2 text-sm text-gray-300 hover:bg-gray-700 truncate"
                            :title="dir.name"
                          >
                            📁 {{ dir.name }}
                          </button>
                        </div>
                      </DropMenu>
                      <!-- 无可移动位置 -->
                      <div
                        v-if="!photoMoveTargets.parentDir && !photoMoveTargets.siblingDirs?.length && !photoMoveTargets.childDirs?.length"
                        class="admin-albums-empty-state px-3 py-2 text-xs text-gray-500"
                      >
                        暂无可移动位置
                      </div>
                    </div>
                  </DropMenu>

                  <!-- 分割线 -->
                  <div class="border-t border-gray-600 my-1"></div>

                  <!-- 隐藏 -->
                  <button
                    @click="togglePhotoHidden('hide')"
                    :disabled="photoModalSelected.size === 0 || !hasVisiblePhotos"
                    class="admin-albums-menu__button w-full text-left px-4 py-2 text-sm text-gray-300 hover:bg-gray-700 flex items-center gap-2 disabled:opacity-40 disabled:cursor-not-allowed"
                  >
                    <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z" />
                    </svg>
                    隐藏 ({{ visibleSelectedCount }})
                  </button>
                  <button
                    @click="togglePhotoHidden('show')"
                    :disabled="photoModalSelected.size === 0 || !hasHiddenPhotos"
                    class="admin-albums-menu__button w-full text-left px-4 py-2 text-sm text-gray-300 hover:bg-gray-700 flex items-center gap-2 disabled:opacity-40 disabled:cursor-not-allowed"
                  >
                    <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13.875 18.825A10.05 10.05 0 0112 19c-4.478 0-8.268-2.943-9.542-7a9.956 9.956 0 012.223-3.592m3.1-2.223A9.953 9.953 0 0112 5c4.478 0 8.268 2.943 9.542 7a9.965 9.965 0 01-4.132 5.411M15 12a3 3 0 00-4.243-2.829M3 3l18 18" />
                    </svg>
                    显示 ({{ hiddenSelectedCount }})
                  </button>
                  <!-- 删除 -->
                  <button
                    @click="deleteSelectedPhotos"
                    class="admin-albums-menu__button admin-albums-menu__button--danger w-full text-left px-4 py-2 text-sm text-red-400 hover:bg-gray-700 flex items-center gap-2"
                  >
                    <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                    </svg>
                    删除 ({{ photoModalSelected.size }})
                  </button>
                </div>
              </DropMenu>

              <!-- Close -->
              <button
                @click="closePhotoModal"
                class="admin-button-soft p-1.5 rounded transition-colors"
              >
                <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
                </svg>
              </button>
            </div>
          </div>
          <!-- Photo grid -->
          <div class="flex-1 overflow-y-auto p-4 min-h-0">
            <div v-if="photoModalLoading" class="admin-albums-empty-state text-center py-8 text-gray-400">加载中...</div>
            <div v-else-if="photoModalPhotos.length === 0" class="admin-albums-empty-state text-center py-8 text-gray-500">暂无照片</div>
            <div v-else class="grid grid-cols-3 sm:grid-cols-4 md:grid-cols-5 lg:grid-cols-6 xl:grid-cols-8 gap-1 sm:gap-2">
              <div
                v-for="photo in photoModalPhotos"
                :key="photo.id"
                class="admin-albums-photo-tile relative aspect-square rounded overflow-hidden cursor-pointer group"
                :class="[photoModalSelected.has(photo.id) ? 'admin-albums-photo-tile--selected ring-2 ring-blue-500' : 'admin-albums-photo-tile--idle hover:ring-1 hover:ring-gray-500', photo.isHidden ? 'opacity-50' : '']"
                @click="togglePhotoSelect(photo.id)"
              >
                <img
                  :src="getPhotoUrl(photo)"
                  class="w-full h-full object-cover"
                  loading="lazy"
                />
                <!-- Selection overlay -->
                <div
                  v-if="photoModalSelected.has(photo.id)"
                  class="admin-albums-photo-selection absolute inset-0 bg-blue-500/20"
                ></div>
                <!-- Checkbox -->
                <div
                  class="admin-albums-photo-checkbox absolute top-1 left-1 w-5 h-5 rounded border-2 flex items-center justify-center transition-all"
                  :class="photoModalSelected.has(photo.id)
                    ? 'bg-blue-500 border-blue-500'
                    : 'border-white/50 bg-black/30 opacity-0 group-hover:opacity-100'"
                >
                  <svg v-if="photoModalSelected.has(photo.id)" class="w-3 h-3 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="3" d="M5 13l4 4L19 7" />
                  </svg>
                </div>
                <!-- Filename tooltip -->
                <div class="admin-albums-photo-caption absolute bottom-0 left-0 right-0 p-1 bg-black/60 text-xs text-gray-300 truncate opacity-0 group-hover:opacity-100 transition-opacity">
                  {{ photo.filename }}
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </teleport>

    <!-- 照片移动冲突对话框 -->
    <teleport to="body">
      <div
        v-if="photoConflictDialogVisible"
        class="fixed inset-0 z-[60] flex items-center justify-center p-4"
        @click.self="photoConflictDialogVisible = false"
      >
        <div class="glass-dialog admin-albums-dialog admin-albums-dialog--photo-conflict rounded-lg p-6 max-w-md w-full text-gray-100">
          <h3 class="text-lg font-medium mb-3">文件名冲突</h3>
          <p class="admin-albums-note text-sm text-gray-300 mb-2">{{ photoConflictInfo.message }}</p>
          <div v-if="photoConflictInfo.conflictFiles?.length" class="mb-4 p-2 bg-gray-900/50 rounded text-xs text-gray-400 max-h-32 overflow-y-auto">
            <div v-for="f in photoConflictInfo.conflictFiles" :key="f">{{ f }}</div>
          </div>
          <p class="admin-albums-note text-sm text-gray-400 mb-4">请选择处理方式：</p>
          <div class="flex gap-3">
            <button
              @click="resolvePhotoConflict('overwrite')"
              class="admin-button-danger flex-1 px-4 py-2 rounded text-sm"
            >
              覆盖
            </button>
            <button
              @click="resolvePhotoConflict('rename')"
              class="admin-button-primary flex-1 px-4 py-2 rounded text-sm"
            >
              重命名
            </button>
            <button
              @click="resolvePhotoConflict('cancel')"
              class="admin-button-soft flex-1 px-4 py-2 rounded text-sm"
            >
              取消
            </button>
          </div>
        </div>
      </div>
    </teleport>

    <!-- 加载状态提示 -->
    <div class="admin-albums-load-status text-center py-3 text-xs">
      <span v-if="loadingMore">正在加载更多…</span>
      <span v-else-if="loadMoreError" class="text-rose-300">{{ loadMoreError }} <button class="underline" @click="loadMore">重试</button></span>
      <span v-else-if="albums.length > 0">已加载 {{ albums.length }}<span v-if="totalAlbumCount"> / 共 {{ totalAlbumCount }}</span> 个相册</span>
    </div>
    <div v-if="!hasMoreData && albums.length > 0" class="admin-albums-empty-state text-center py-4 text-gray-500 text-sm">
      已加载全部 {{ albums.length }} 个相册
    </div>
    <div v-if="albums.length === 0 && !loading" class="admin-albums-empty-state text-center py-4 text-gray-500 text-sm">
      没有找到相册
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount, watch, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { api, albumApi, getEffectiveAuthToken } from '@/api'
import { useAuthStore } from '@/stores/auth'
import { getEffectParamDefs } from '@/config/particlePresets'
import { shaderParamDefs, isShaderEffect } from '@/config/shaderEffects'
import CoverDisplay from '@/components/CoverDisplay.vue'
import { buildPhotoAssetUrl } from '@/utils/photoUrl'
import DropMenu from '@/components/DropMenu.vue'
import { useAdminFeedback } from '@/composables/useAdminFeedback'

const router = useRouter()
const authStore = useAuthStore()
const { notify, confirm } = useAdminFeedback()
const alert = (message: unknown) => {
  const text = String(message).replace(/^[✅❌]\s*/, '')
  notify(text, /失败|错误|不能|未找到|不支持|无效/.test(text) ? 'error' : 'info')
}

const albums = ref<any[]>([])
const loading = ref(false)
const isDataLoaded = ref(false)  // 标记数据是否已加载（用于缓存）
const keyword = ref('')
const selectionMode = ref(false)
const selectedAlbumIds = ref<number[]>([])
const batchLoading = ref(false)
const batchTagMode = ref(false)
const showMenuForAlbum = ref<any>(null)
const menuPosition = ref({ x: 0, y: 0 })
const menuNearRight = ref(false) // 标记菜单是否靠近右边缘

// 相册排序设置
const albumSortOrder = ref('name_asc')

// 分页相关
const PAGE_SIZE = 20
let currentPage = 0
let hasMoreData = true
const loadingMore = ref(false)
const loadMoreError = ref('')
const totalAlbumCount = ref(0)

// 监听排序设置变化，重新加载相册（排除初始化时的第一次设置）
let isInitialized = false
watch(albumSortOrder, async (newSort, oldSort) => {
  if (newSort !== oldSort && isInitialized) {
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
const mergeSiblingDirs = ref<any[]>([]) // 合并至同级的同级目录列表
// 移动至子菜单显示状态（使用 JS 控制，解决鼠标移动间隙问题）
const showMoveMenu = ref(false)
let moveMenuCloseTimer: number | null = null
const moveMenuAnchor = ref({ top: 0, left: 0, right: 0 })
const activeMoveSubmenu = ref<'categories' | 'children' | 'siblings' | null>(null)
const moveSubmenuAnchor = ref({ top: 0, left: 0, right: 0 })
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
  { type: 'cherry_blossom', name: '樱花', description: '实景素材 · 飘落的樱花瓣', imageMode: true },
  { type: 'snow', name: '雪景', description: '实景素材 · 飘落的雪花', imageMode: true },
  { type: 'autumn_leaves', name: '秋叶', description: '实景素材 · 飘落的秋叶', imageMode: true },
  { type: 'starry_sky', name: '星空', description: '闪烁的星星 · 支持闪烁/亮度', imageMode: true },
  { type: 'meteor', name: '流星', description: '划过夜空 · 带拖尾光迹', imageMode: true },
  { type: 'firefly', name: '萤火虫', description: '夜间发光飘动的萤火虫', imageMode: true },
  { type: 'rain', name: '雨滴', description: '倾斜飘落的雨丝', imageMode: true },
  { type: 'rain_on_containers', name: '雨落图片', description: '雨滴落在照片上并沿边缘流淌', imageMode: false },
  { type: 'bubble', name: '气泡', description: '缓缓上升的透明气泡', imageMode: true },
  { type: 'dandelion', name: '蒲公英', description: '随风飘散的蒲公英种子', imageMode: true },
  { type: 'hearts', name: '爱心', description: '缓缓上升的爱心 · 脉动效果', imageMode: true },
  { type: 'dust', name: '光尘', description: '空气中飘浮的暖色光点', imageMode: true },
  { type: 'aurora', name: '极光', description: '流动的彩色极光波带', imageMode: true, category: '光照' },
  { type: 'light_rays', name: '丁达尔光线', description: '从天而降的体积光束', imageMode: true, category: '光照' },
  { type: 'lens_flare', name: '镜头光晕', description: '移动的镜头光晕鬼影', imageMode: true, category: '光照' },
  { type: 'water_ripple', name: '水波纹', description: '扩散的同心椭圆涟漪', imageMode: true, category: '水面' },
  { type: 'wave', name: '海浪', description: '底部涌动的海浪层次', imageMode: true, category: '水面' },
  { type: 'fog', name: '雾气', description: '缓慢飘动的半透明雾层', imageMode: true, category: '氛围' },
  { type: 'bokeh', name: '光斑虚化', description: '大面积柔和虚化光斑', imageMode: true, category: '氛围' },
  { type: 'vignette', name: '暗角呼吸', description: '缓慢呼吸的暗角效果', imageMode: true, category: '氛围' },
  { type: 'film_grain', name: '胶片噪点', description: '复古胶片颗粒感', imageMode: true, category: '风格' },
  { type: 'color_shift', name: '色彩流转', description: '缓慢流动的彩色光晕', imageMode: true, category: '风格' },
  { type: 'glitch', name: '故障风', description: '随机数字故障闪烁', imageMode: true, category: '风格' },
  { type: 'fireworks', name: '烟花', description: '夜空中彩色烟花绽放', imageMode: true, category: '氛围' },
  { type: 'birthday', name: '生日蜡烛', description: '底部蜡烛缓慢燃烧', imageMode: true, category: '氛围' },
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
  loadMoreError.value = ''
  currentPage = 0
  hasMoreData = true
  albums.value = []

  try {
    const params: any = { page: 0, size: PAGE_SIZE, sort: albumSortOrder.value, includeHidden: true }
    if (keyword.value.trim()) params.keyword = keyword.value.trim()
    const res = await api.get('/albums', { params })
    const content = res.data.content || res.data || []
    totalAlbumCount.value = Number(res.data.totalElements ?? content.length)
    albums.value = content
    hasMoreData = content.length === PAGE_SIZE
    currentPage = 0
    isDataLoaded.value = true  // 标记数据已加载
  } catch (e: any) {
    notify('加载相册失败：' + (e.response?.data?.error || e.response?.data?.message || e.message || '网络错误'), 'error')
    hasMoreData = false
  } finally {
    loading.value = false
  }
}

const loadMore = async () => {
  if (loadingMore.value || !hasMoreData) return
  
  loadingMore.value = true
  loadMoreError.value = ''
  try {
    currentPage++
    const params: any = { page: currentPage, size: PAGE_SIZE, sort: albumSortOrder.value, includeHidden: true }
    if (keyword.value.trim()) params.keyword = keyword.value.trim()
    const res = await api.get('/albums', { params })
    const content = res.data.content || res.data || []
    albums.value = [...albums.value, ...content]
    hasMoreData = content.length === PAGE_SIZE // 只有返回完整的 PAGE_SIZE 才认为还有更多
  } catch (e: any) {
    currentPage = Math.max(0, currentPage - 1)
    loadMoreError.value = '加载失败'
  } finally {
    loadingMore.value = false
  }
}

const toggleSelectionMode = () => {
  selectionMode.value = !selectionMode.value
  if (!selectionMode.value) selectedAlbumIds.value = []
}

const toggleAlbumSelection = (id: number) => {
  selectedAlbumIds.value = selectedAlbumIds.value.includes(id)
    ? selectedAlbumIds.value.filter(item => item !== id)
    : [...selectedAlbumIds.value, id]
}

const selectAllAlbums = () => {
  selectedAlbumIds.value = albums.value.map(album => album.id)
}

const batchSetHidden = async (isHidden: boolean) => {
  if (!selectedAlbumIds.value.length || batchLoading.value) return
  batchLoading.value = true
  try {
    await Promise.all(selectedAlbumIds.value.map(id => api.put(`/albums/${id}/hidden`, { isHidden })))
    albums.value.forEach(album => {
      if (selectedAlbumIds.value.includes(album.id)) album.isHidden = isHidden
    })
    notify(isHidden ? '已批量隐藏相册' : '已批量显示相册', 'success')
  } catch (e: any) {
    notify('批量修改隐藏状态失败：' + (e.response?.data?.error || e.message), 'error')
  } finally {
    batchLoading.value = false
  }
}

const batchDelete = async () => {
  if (!selectedAlbumIds.value.length || batchLoading.value) return
  const confirmed = await confirm({
    title: '删除所选相册',
    message: `将删除 ${selectedAlbumIds.value.length} 个相册及其管理记录。此操作不可撤销。`,
    confirmLabel: '删除所选',
    tone: 'danger'
  })
  if (!confirmed) return
  batchLoading.value = true
  try {
    await Promise.all(selectedAlbumIds.value.map(id => api.delete(`/albums/${id}`)))
    selectedAlbumIds.value = []
    selectionMode.value = false
    await load()
    notify('已删除所选相册', 'success')
  } catch (e: any) {
    notify('批量删除失败：' + (e.response?.data?.error || e.message), 'error')
  } finally {
    batchLoading.value = false
  }
}

const openBatchTagDialog = async () => {
  batchTagMode.value = true
  currentAlbum.value = null
  tagKeyword.value = ''
  await loadAllTags()
  tagDialogVisible.value = true
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
  return buildPhotoAssetUrl(photo, 'small') || ''
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
  if (!tagKeyword.value.trim() || (!currentAlbum.value && !batchTagMode.value)) {
    return
  }

  const tagName = tagKeyword.value.trim()

  try {
    // 查找或创建标签
    let tag = allTags.value.find((t: any) => t.name === tagName)
    if (!tag) {
      const res = await api.post('/tags', { name: tagName })
      tag = res.data
      await loadAllTags()
    } else {
    }

    if (batchTagMode.value) {
      await Promise.all(selectedAlbumIds.value.map(id => api.post(`/albums/${id}/tags/${tag.id}`)))
      albums.value.forEach(album => {
        if (selectedAlbumIds.value.includes(album.id)) {
          album.tags = album.tags || []
          if (!album.tags.find((item: any) => item.id === tag.id)) album.tags.push(tag)
        }
      })
      batchTagMode.value = false
      tagDialogVisible.value = false
      notify('已为所选相册添加标签', 'success')
      return
    }

    await api.post(`/albums/${currentAlbum.value.id}/tags/${tag.id}`)

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

const getEffectParams = (type: string) => {
  if (isShaderEffect(type)) return shaderParamDefs[type] || []
  return getEffectParamDefs(type)
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

  const oldName = currentNameAlbum.value.name
  const newName = nameInput.value.trim()

  // 如果名称没变，直接关闭对话框
  if (oldName === newName) {
    renameDialogVisible.value = false
    currentNameAlbum.value = null
    return
  }

  try {
    // 调用重命名API，同时重命名文件夹和数据库记录
    const res = await api.post(`/albums/${currentNameAlbum.value.id}/rename`, {
      newName: newName
    })
    const result = res.data

    if (result.success) {
      // 直接更新本地数据，避免重新加载导致滚动丢失
      // 使用后端返回的完整数据（包括正确计算的 displayTitle）
      const updatedAlbum = result.album
      if (updatedAlbum) {
        updateAlbumData(currentNameAlbum.value.id, {
          name: updatedAlbum.name,
          displayTitle: updatedAlbum.displayTitle,
          relativePath: updatedAlbum.relativePath,
          path: updatedAlbum.path
        })
      }
      renameDialogVisible.value = false
      currentNameAlbum.value = null
      // 不弹窗
    } else {
      alert('重命名失败: ' + (result.message || '未知错误'))
    }
  } catch (e: any) {
    alert('重命名失败: ' + (e.response?.data?.message || e.response?.data?.error || e.message))
  }
}

const deleteAlbum = async (album: any) => {
  const confirmed = await confirm({
    title: '删除相册',
    message: `“${album.displayTitle || album.name}”将从后台删除。`,
    confirmLabel: '删除相册',
    tone: 'danger'
  })
  if (!confirmed) return

  try {
    await api.delete(`/albums/${album.id}`)
    await load()
    closeAllMenus()
  } catch (e: any) {
    notify('删除相册失败：' + (e.response?.data?.error || e.message), 'error')
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

// 加载同级目录列表（用于合并至同级）
const loadSiblingDirs = async (album: any) => {
  if (!album) return
  try {
    const res = await api.get(`/albums/${album.id}/move/siblings`)
    mergeSiblingDirs.value = res.data || []
  } catch (e) {
    console.error('加载同级目录失败:', e)
    mergeSiblingDirs.value = []
  }
}

// 合并相册到同级目录
const doMergeToSibling = async (album: any, sibling: any) => {
  if (!album || !sibling) return

  try {
    const res = await api.post(`/albums/${album.id}/merge`, {
      targetPath: sibling.path
    })
    const result = res.data

    if (result.success) {
      closeAllMenus()
      // 本地更新：移除被合并的相册，刷新目标相册
      const sourceAlbumId = result.sourceAlbumId
      const targetAlbumId = result.targetAlbumId

      // 1. 移除被合并的相册
      albums.value = albums.value.filter(a => a.id !== sourceAlbumId)

      // 2. 刷新目标相册的数据（通过调用API获取最新信息）
      if (targetAlbumId) {
        try {
          const albumRes = await api.get(`/albums/${targetAlbumId}`)
          const updatedAlbum = albumRes.data
          if (updatedAlbum) {
            // 更新目标相册的信息
            const idx = albums.value.findIndex(a => a.id === targetAlbumId)
            if (idx !== -1) {
              albums.value[idx] = { ...albums.value[idx], ...updatedAlbum }
            }
          }
        } catch (e) {
          console.error('刷新目标相册失败:', e)
        }
      }
    } else {
      alert('合并失败: ' + (result.message || '未知错误'))
    }
  } catch (e: any) {
    alert('合并失败: ' + (e.response?.data?.message || e.response?.data?.error || e.message))
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
  const displayParts = splitPath(getAlbumDisplayPath(album))
  if (displayParts.length < 2) {
    alert('已经在最顶层，无法向上移动')
    return
  }
  const parentOfParent = joinPath(splitPath(getAlbumRawPath(album)).slice(0, -2))
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
  const parentPath = joinPath(splitPath(getAlbumRawPath(album)).slice(0, -1))
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
  const menuWidth = 192 // w-48 = 12rem = 192px
  const menuHeight = 500 // 估算菜单最大高度
  const padding = 16 // 屏幕边缘留白

  let x = event.clientX
  let y = event.clientY

  // 如果右边空间不够，往左显示
  if (x + menuWidth + padding > window.innerWidth) {
    x = window.innerWidth - menuWidth - padding
    menuNearRight.value = true // 标记菜单靠近右边缘
  } else {
    menuNearRight.value = false
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

const moveMenuStyle = computed(() => {
  const width = 192
  const margin = 16
  const estimatedHeight = 232
  const fitsRight = moveMenuAnchor.value.right + width + margin <= window.innerWidth
  const left = fitsRight
    ? moveMenuAnchor.value.right
    : Math.max(margin, moveMenuAnchor.value.left - width)
  const top = Math.max(
    margin,
    Math.min(moveMenuAnchor.value.top, window.innerHeight - estimatedHeight - margin)
  )

  return {
    left: `${left}px`,
    top: `${top}px`,
    width: `${width}px`,
    maxHeight: `calc(100vh - ${margin * 2}px)`
  }
})

const moveSubmenuStyle = computed(() => {
  const width = 192
  const margin = 16
  const estimatedHeight = 320
  const fitsRight = moveSubmenuAnchor.value.right + width + margin <= window.innerWidth
  const left = fitsRight
    ? moveSubmenuAnchor.value.right
    : Math.max(margin, moveSubmenuAnchor.value.left - width)
  const top = Math.max(
    margin,
    Math.min(moveSubmenuAnchor.value.top, window.innerHeight - estimatedHeight - margin)
  )

  return {
    left: `${left}px`,
    top: `${top}px`,
    width: `${width}px`
  }
})

// 计算菜单位置的样式
const menuStyle = computed(() => {
  return {
    left: menuPosition.value.x + 'px',
    top: menuPosition.value.y + 'px'
  }
})

const closeAllMenus = () => {
  cancelMoveMenuClose()
  showMenuForAlbum.value = null
  tagDialogVisible.value = false
  effectsDialogVisible.value = false
  coverDialogVisible.value = false
  descriptionDialogVisible.value = false
  renameDialogVisible.value = false
  moveChildDirs.value = []
  mergeSiblingDirs.value = [] // 重置合并至同级目录列表
  // 重置移动至子菜单状态
  showMoveMenu.value = false
  activeMoveSubmenu.value = null
}

const openMoveMenu = (event: MouseEvent) => {
  event.stopPropagation()
  cancelMoveMenuClose()
  const anchor = event.currentTarget as HTMLElement
  const rect = anchor.getBoundingClientRect()
  moveMenuAnchor.value = { top: rect.top, left: rect.left, right: rect.right }
  showMoveMenu.value = true
  if (showMenuForAlbum.value) loadMoveMenuData(showMenuForAlbum.value)
}

const openMoveSubmenu = async (type: 'categories' | 'children' | 'siblings', event: MouseEvent) => {
  cancelMoveMenuClose()
  const anchor = event.currentTarget as HTMLElement
  const rect = anchor.getBoundingClientRect()
  moveSubmenuAnchor.value = { top: rect.top, left: rect.left, right: rect.right }
  activeMoveSubmenu.value = type

  if (!showMenuForAlbum.value) return
  if (type === 'children') await loadChildDirs(showMenuForAlbum.value)
  if (type === 'siblings') await loadSiblingDirs(showMenuForAlbum.value)
}

const cancelMoveMenuClose = () => {
  if (moveMenuCloseTimer !== null) {
    window.clearTimeout(moveMenuCloseTimer)
    moveMenuCloseTimer = null
  }
}

const scheduleMoveMenuClose = () => {
  cancelMoveMenuClose()
  moveMenuCloseTimer = window.setTimeout(() => {
    showMoveMenu.value = false
    activeMoveSubmenu.value = null
  }, 180)
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
      const albumPathParts = splitPath(getAlbumDisplayPath(album))
      const albumPathPrefix = joinPath(albumPathParts)

      // 先从当前列表过滤
      let subAlbums = albums.value.filter(a => {
        // 跳过自己
        if (a.id === album.id) return false

        const aPathParts = splitPath(getAlbumDisplayPath(a))
        const aPath = joinPath(aPathParts)

        // 直接子相册必须是：
        // 1. 路径以当前相册路径 + '/' 开头
        // 2. 层级恰好是当前相册层级 + 1
        const isDirectChild = aPath.startsWith(albumPathPrefix + '/') &&
          aPathParts.length === albumPathParts.length + 1

        return isDirectChild
      })


      // 如果当前列表没有子相册，尝试从后端获取
      if (subAlbums.length === 0) {
        try {
          // 调用后端 API 获取子相册
          const response = await api.get(`/albums/${album.id}/sub-albums`)
          subAlbums = response.data || []
        } catch (fetchError: any) {
          console.warn('从后端获取子相册失败:', fetchError)
        }
      }


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

const getAlbumDisplayPath = (album: any) => album?.relativePath || album?.path || ''
const getAlbumRawPath = (album: any) => album?.path || ''

const splitPath = (path: string): string[] => {
  return (path || '').replace(/\\/g, '/').split('/').filter(p => p.length > 0)
}

const joinPath = (parts: string[]): string => parts.filter(Boolean).join('/')

const aggregateToParent = async (album: any) => {
  // 先关闭菜单
  closeAllMenus()

  const displayPath = getAlbumDisplayPath(album)
  const rawPath = getAlbumRawPath(album)

  // 调试信息：打印相册路径，帮助诊断问题

  // 首先使用后端的 isTopLevel 字段进行判断（如果后端正确计算了的话）
  if (album.isTopLevel === true) {
    alert(`该相册"${album.displayTitle || album.name}"已经是顶级相册，无法聚合到上一级。\n\n路径: ${displayPath}\n\n注意：顶级相册是指位于当前用户根目录分类下的相册（如：人像/相册名）`)
    return
  }

  const displayPathParts = splitPath(displayPath)

  // 对当前后台展示而言，按“用户根目录下的分类/相册/子相册”计算
  const minDepth = 3

  if (displayPathParts.length < minDepth) {
    const detailMsg = `\n\n详细信息：\n` +
      `- 当前路径: ${displayPath}\n` +
      `- 路径层级数: ${displayPathParts.length}\n` +
      `- 最小需要层级: ${minDepth}\n` +
      `- 路径分段: ${JSON.stringify(displayPathParts)}`
    alert(`该相册"${album.displayTitle || album.name}"已经是顶级相册，无法聚合到上一级。${detailMsg}`)
    return
  }

  const parentPath = joinPath(splitPath(rawPath).slice(0, -1))
  const parentDisplayPath = joinPath(displayPathParts.slice(0, -1))

  // 查找父相册（使用统一处理后的路径比较）
  // 同时尝试原始路径和标准化后的路径
  let parentAlbum = albums.value.find(a => {
    const candidateDisplayPath = getAlbumDisplayPath(a).replace(/\\/g, '/')
    return candidateDisplayPath === parentDisplayPath.replace(/\\/g, '/')
  })

  // 如果父相册不存在，尝试创建它
  if (!parentAlbum) {
    try {

      // 直接创建父相册
      const createResponse = await api.post('/albums', { path: parentPath })
      parentAlbum = createResponse.data

      if (!parentAlbum) {
        alert(`无法创建父相册，请检查文件夹路径是否正确。\n\n父相册路径: ${parentDisplayPath}\n子相册路径: ${displayPath}`)
        return
      }

    } catch (createError: any) {
      console.error('创建父相册失败:', createError)
      const errorDetail = createError.response?.data?.error || createError.response?.data?.message || createError.message
      alert(`创建父相册失败: ${errorDetail}\n\n父相册路径: ${parentDisplayPath}\n子相册路径: ${displayPath}`)
      return
    }
  }

  // 找出同一层级的所有相册（父路径相同的相册）
  const siblingAlbums = albums.value.filter(a => {
    const aPathParts = splitPath(getAlbumDisplayPath(a))
    return joinPath(aPathParts.slice(0, -1)) === joinPath(displayPathParts.slice(0, -1))
  })
  const siblingIds = siblingAlbums.map(a => a.id)

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

const toggleAlbumHidden = async (album: any, isHidden: boolean) => {
  try {
    const newHiddenValue = Boolean(isHidden)
    await api.put(`/albums/${album.id}/hidden`, {
      isHidden: newHiddenValue
    })
    // 直接更新本地数据，避免重新加载导致滚动丢失
    updateAlbumData(album.id, {
      isHidden: newHiddenValue
    })
  } catch (e: any) {
    alert('设置隐藏状态失败: ' + (e.response?.data?.error || e.message))
  }
}

const openAlbum = (albumId: number) => {
  const url = buildPublicPath(`/album/${albumId}`, authStore.slug ? `/${authStore.slug}` : undefined)
  window.open(url, '_blank')
}

// ==================== 照片管理弹窗 ====================
const photoModalVisible = ref(false)
const photoModalAlbum = ref<any>(null)
const photoModalPhotos = ref<any[]>([])
const photoModalSelected = ref<Set<number>>(new Set())
const photoModalLoading = ref(false)

const sortPhotoModalPhotos = (photos: any[]) => {
  return [...photos].sort((left, right) => {
    const leftHidden = Boolean(left?.isHidden)
    const rightHidden = Boolean(right?.isHidden)
    if (leftHidden !== rightHidden) {
      return leftHidden ? 1 : -1
    }
    return 0
  })
}
const photoMoveMenuVisible = ref(false)
const photoMoveTargets = ref<any>({})
// 菜单展开状态
const photoMoveExpandedSiblings = ref(false)
const photoMoveExpandedChildren = ref(false)

const getAdminRequestConfig = () => {
  const token = authStore.token || getEffectiveAuthToken()
  return token
    ? {
        headers: {
          Authorization: `Bearer ${token}`
        }
      }
    : undefined
}

const getApiErrorMessage = (error: any, fallback: string) => {
  return (
    error?.response?.data?.message ||
    error?.response?.data?.error ||
    error?.message ||
    fallback
  )
}

// 检查选中的照片是否有隐藏的
const hasHiddenPhotos = computed(() => {
  return photoModalPhotos.value.some(
    p => photoModalSelected.value.has(p.id) && p.isHidden
  )
})

const hasVisiblePhotos = computed(() => {
  return photoModalPhotos.value.some(
    p => photoModalSelected.value.has(p.id) && !p.isHidden
  )
})

const hiddenSelectedCount = computed(() => {
  return photoModalPhotos.value.filter(
    p => photoModalSelected.value.has(p.id) && p.isHidden
  ).length
})

const visibleSelectedCount = computed(() => {
  return photoModalPhotos.value.filter(
    p => photoModalSelected.value.has(p.id) && !p.isHidden
  ).length
})

const applyPhotoHiddenState = (targetHidden: boolean) => {
  const selectedIds = photoModalSelected.value
  photoModalPhotos.value = sortPhotoModalPhotos(
    photoModalPhotos.value.map((photo: any) => (
      selectedIds.has(photo.id)
        ? { ...photo, isHidden: targetHidden }
        : photo
    ))
  )
  photoModalSelected.value = new Set()
}

// 切换照片隐藏/显示状态
const togglePhotoHidden = async (operation: 'hide' | 'show') => {
  if (photoModalSelected.value.size === 0) return

  const action = operation === 'hide' ? '隐藏' : '显示'
  const count = operation === 'hide' ? visibleSelectedCount.value : hiddenSelectedCount.value
  if (count <= 0) return

  try {
    const res = await api.post('/admin/photos/batch', {
      operation,
      photoIds: Array.from(photoModalSelected.value)
    }, getAdminRequestConfig())
    const result = res.data

    if (result.success) {
      applyPhotoHiddenState(operation === 'hide')
    } else {
      alert(`${action}失败: ` + (result.message || result.error || '未知错误'))
    }
  } catch (e: any) {
    alert(`${action}失败: ` + getApiErrorMessage(e, '未知错误'))
  }
}

const openPhotoManageModal = async (album: any) => {
  photoModalAlbum.value = album
  photoModalVisible.value = true
  photoModalSelected.value = new Set()
  photoMoveMenuVisible.value = false
  photoModalLoading.value = true

  try {
    const res = await api.get(`/photos/album/${album.id}`, {
      params: { all: true, includeHidden: true },
      ...getAdminRequestConfig()
    })
    photoModalPhotos.value = sortPhotoModalPhotos(res.data.content || [])
  } catch (e: any) {
    console.error('获取相册照片失败:', e)
    photoModalPhotos.value = []
  } finally {
    photoModalLoading.value = false
  }

  // Load move targets in background
  try {
    const res = await api.get(`/admin/photos/move-targets/${album.id}`, getAdminRequestConfig())
    photoMoveTargets.value = res.data || {}
  } catch (e) {
    console.error('获取移动目标失败:', e)
    photoMoveTargets.value = {}
  }
}

const closePhotoModal = () => {
  photoModalVisible.value = false
  photoMoveMenuVisible.value = false
  photoMoveExpandedSiblings.value = false
  photoMoveExpandedChildren.value = false
}

const togglePhotoSelect = (photoId: number) => {
  const s = new Set(photoModalSelected.value)
  if (s.has(photoId)) {
    s.delete(photoId)
  } else {
    s.add(photoId)
  }
  photoModalSelected.value = s
}

const toggleSelectAllPhotos = () => {
  if (photoModalSelected.value.size === photoModalPhotos.value.length) {
    photoModalSelected.value = new Set()
  } else {
    photoModalSelected.value = new Set(photoModalPhotos.value.map((p: any) => p.id))
  }
}

// 照片移动冲突处理
const photoConflictDialogVisible = ref(false)
const photoConflictInfo = ref<any>({})
const photoConflictTargetPath = ref('')

const doMovePhotosTo = async (targetPath: string, conflictResolution?: string) => {
  if (photoModalSelected.value.size === 0) return

  photoMoveMenuVisible.value = false
  try {
    const res = await api.post('/admin/photos/batch-move', {
      photoIds: Array.from(photoModalSelected.value),
      targetPath,
      conflictResolution: conflictResolution || null
    }, getAdminRequestConfig())
    const result = res.data

    if (result.conflict) {
      photoConflictInfo.value = result
      photoConflictTargetPath.value = targetPath
      photoConflictDialogVisible.value = true
      return
    }

    if (result.success) {
      photoConflictDialogVisible.value = false
      await refreshPhotoModalAfterChange()
    } else {
      alert('移动失败: ' + (result.message || result.error || '未知错误'))
    }
  } catch (e: any) {
    alert('移动失败: ' + getApiErrorMessage(e, '未知错误'))
  }
}

const resolvePhotoConflict = (resolution: string) => {
  photoConflictDialogVisible.value = false
  if (resolution === 'cancel') return
  doMovePhotosTo(photoConflictTargetPath.value, resolution)
}

const refreshPhotoModalAfterChange = async () => {
  photoModalSelected.value = new Set()
  if (photoModalAlbum.value) {
    const albumId = photoModalAlbum.value.id
    try {
      const res = await api.get(`/photos/album/${albumId}`, { params: { all: true, includeHidden: true } })
      photoModalPhotos.value = sortPhotoModalPhotos(res.data.content || [])
    } catch {
      photoModalPhotos.value = []
    }
    if (photoModalPhotos.value.length === 0) {
      closePhotoModal()
    }
  }
  await load()
}

const deleteSelectedPhotos = async () => {
  if (photoModalSelected.value.size === 0) return
  const count = photoModalSelected.value.size
  const confirmed = await confirm({
    title: `删除 ${count} 张照片`,
    message: '此操作不可撤销，照片文件将被永久删除。',
    confirmLabel: '删除照片',
    tone: 'danger'
  })
  if (!confirmed) return

  try {
    const res = await api.post('/admin/photos/batch-delete', {
      photoIds: Array.from(photoModalSelected.value)
    }, getAdminRequestConfig())
    const result = res.data
    if (result.success) {
      notify(result.message || '照片已删除', 'success')
      await refreshPhotoModalAfterChange()
    } else {
      notify('删除失败：' + (result.message || result.error || '未知错误'), 'error')
    }
  } catch (e: any) {
    notify('删除失败：' + getApiErrorMessage(e, '未知错误'), 'error')
  }
}

const forceScanAndRebuild = async () => {
  if (!authStore.isSuperAdmin) {
    notify('普通用户不能主动发起扫描，请等待系统按队列自动处理。', 'info')
    return
  }
  const confirmed = await confirm({
    title: '重新扫描相册',
    message: '将扫描新增或修改的照片，并更新相册元数据。不会重新生成人脸数据和标签。',
    confirmLabel: '开始扫描'
  })

  if (!confirmed) return

  loading.value = true

  try {
    // 执行普通扫描（只处理更改的内容）
    const scanResponse = await api.post('/admin/scan')

    // 等待扫描完成（简单轮询）
    let scanCompleted = false
    let attempts = 0
    const maxAttempts = 300 // 5分钟超时

    while (!scanCompleted && attempts < maxAttempts) {
      await new Promise(resolve => setTimeout(resolve, 1000))
      const statusResponse = await api.get('/admin/scan/status')
      const status = statusResponse.data

      if (!status.scanning) {
        scanCompleted = true
      }
      attempts++
    }

    if (!scanCompleted) {
      throw new Error('扫描超时，请稍后手动检查扫描状态')
    }

    notify('重新扫描完成，已更新相册数据。', 'success')

    // 重新加载相册数据
    await load()

  } catch (error: any) {
    console.error('重新扫描失败:', error)
    const errorMsg = error.response?.data?.error || error.response?.data?.message || error.message
    notify('重新扫描失败：' + errorMsg, 'error')
  } finally {
    loading.value = false
  }
}

const handleGlobalKeydown = (e: KeyboardEvent) => {
  const target = e.target as HTMLElement | null
  const editing = target?.tagName === 'INPUT' || target?.tagName === 'TEXTAREA' || target?.tagName === 'SELECT'
  if (!editing && e.key === 'Delete' && selectionMode.value && selectedAlbumIds.value.length) {
    e.preventDefault()
    batchDelete()
    return
  }
  if (e.key === 'Escape') {
    // 优先关闭照片管理弹窗（最上层）
    if (photoModalVisible.value) {
      closePhotoModal()
      return
    }
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
      closeAllMenus()
      return
    }
  }
}

onMounted(async () => {
  // 先获取排序设置（这是加载相册的必要前提）
  await loadAlbumSortOrder()
  // 排序设置获取完成后，在后台加载其他初始化数据
  loadAllTags().then(() => {
    isInitialized = true
  })
  // 只在首次进入时加载数据，后续进入使用缓存
  if (!isDataLoaded.value) {
    load()  // 移除 await，允许页面先渲染
  }
  window.addEventListener('keydown', handleGlobalKeydown)
  initScrollObserver()
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', handleGlobalKeydown)
  cleanupScrollObserver()
})
</script>

<!-- 全局样式 - 供 teleport 元素使用 -->
<style>
.glass-menu {
  background: rgba(31, 41, 55, 0.75);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  border: 1px solid rgba(75, 85, 99, 0.4);
  box-shadow: 0 10px 40px rgba(0, 0, 0, 0.5);
}

/* Teleported menus must not inherit the translucent shared surface token. */
.admin-albums-menu,
.admin-albums-menu--nested,
.admin-albums-menu--photo-sub {
  opacity: 1 !important;
  border: 1px solid rgba(148, 163, 184, 0.5) !important;
  background: #182235 !important;
  box-shadow: 0 18px 38px rgba(2, 6, 23, 0.42), inset 0 1px rgba(255, 255, 255, 0.14) !important;
  backdrop-filter: blur(40px) saturate(180%) contrast(1.08) !important;
  -webkit-backdrop-filter: blur(40px) saturate(180%) contrast(1.08) !important;
}

:root[data-admin-color-mode='light'] .admin-albums-menu,
:root[data-admin-color-mode='light'] .admin-albums-menu--nested,
:root[data-admin-color-mode='light'] .admin-albums-menu--photo-sub {
  border-color: rgba(148, 163, 184, 0.5) !important;
  background: #ffffff !important;
  box-shadow: 0 12px 26px rgba(15, 23, 42, 0.16) !important;
}

</style>

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
