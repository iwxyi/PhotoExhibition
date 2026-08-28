<template>
  <div class="min-h-screen admin-shell admin-file-browser-page">
    <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-3 admin-file-browser-shell">
      <div class="glass-panel p-2.5 admin-file-browser-breadcrumbs">
        <div class="flex items-center gap-2 flex-wrap">
          <div class="min-w-0 flex-1 overflow-x-auto">
            <div class="flex items-center gap-2 min-w-max">
              <button
                @click="goToPath(basePath)"
                @dragover.prevent="handleBreadcrumbDragOver(basePath)"
                @dragleave.prevent="handleBreadcrumbDragLeave(basePath)"
                @drop.prevent="handleBreadcrumbDrop($event, basePath)"
                class="admin-file-browser-root-button flex h-8 w-8 items-center justify-center rounded-full border p-0 text-sm transition-all duration-200"
                :class="breadcrumbClass(-1)"
                :title="rootButtonLabel"
                aria-label="返回根目录"
              >
                <span aria-hidden="true">⌂</span>
              </button>
              <template v-for="(crumb, index) in breadcrumbSegments" :key="crumb.path">
                <button
                  @click.stop="toggleBreadcrumbMenu($event, index === 0 ? basePath : breadcrumbSegments[index - 1].path)"
                  class="admin-file-browser-breadcrumb-separator rounded-full px-1 py-0.5 transition"
                  title="查看此层级内容"
                >
                  /
                </button>
                <button
                  @click="goToPath(crumb.path)"
                  @dragover.prevent="handleBreadcrumbDragOver(crumb.path)"
                  @dragleave.prevent="handleBreadcrumbDragLeave(crumb.path)"
                  @drop.prevent="handleBreadcrumbDrop($event, crumb.path)"
                  class="rounded-full border px-2.5 py-1 text-sm transition-all duration-200"
                  :class="breadcrumbClass(index, crumb.isGhost)"
                >
                  {{ crumb.label }}
                </button>
              </template>
            </div>
          </div>
          <button
            @click="goToParent"
            :disabled="isAtRoot"
            class="admin-file-browser-breadcrumb-action admin-file-browser-parent-button px-2.5 py-1 rounded text-sm transition-colors shrink-0"
            :class="isAtRoot ? 'admin-file-browser-breadcrumb-action--disabled cursor-not-allowed' : 'admin-file-browser-breadcrumb-action--primary'"
            title="返回上级目录"
            aria-label="返回上级目录"
          >
            <span aria-hidden="true">↑</span>
          </button>
        </div>
      </div>

      <!-- 工具栏 -->
      <div class="glass-panel glass-toolbar flex items-center gap-2 flex-wrap admin-file-browser-toolbar">
        <div class="relative shrink-0" @click.stop>
          <button
            ref="actionsTriggerRef"
            type="button"
            class="admin-button-primary flex items-center gap-2 rounded-lg px-3 py-1.5 text-sm"
            aria-haspopup="menu"
            :aria-expanded="actionsMenu.show"
            @click="toggleActionsMenu"
          >
            操作 <span class="text-xs opacity-70">▾</span>
          </button>
        </div>
        <label class="admin-file-browser-search relative shrink-0" aria-label="搜索当前目录">
          <span class="pointer-events-none absolute inset-y-0 left-3 flex items-center text-xs text-[color:var(--pe-admin-text-faint)]">⌕</span>
          <input v-model="searchKeyword" type="search" autocomplete="off" placeholder="搜索当前目录" class="admin-file-browser-search-input w-[180px] rounded-lg py-1.5 pl-8 pr-8 text-sm outline-none transition" />
          <button v-if="searchKeyword" type="button" class="admin-file-browser-search-clear absolute inset-y-0 right-1 flex w-7 items-center justify-center rounded-full text-sm" aria-label="清除搜索" @click="searchKeyword = ''">×</button>
        </label>
        <template v-if="multiSelect">
        <button
          @click="moveSelected"
          :disabled="!selectedPaths.size || !supportsItemManagement"
            class="px-3 py-1.5 bg-blue-600 hover:bg-blue-700 rounded-lg text-sm disabled:opacity-50 whitespace-nowrap"
        >
            移动已选 ({{ selectedPaths.size }})
          </button>
          <button
            @click="deleteSelected"
            :disabled="!selectedPaths.size || !supportsItemManagement"
            class="px-3 py-1.5 bg-red-600 hover:bg-red-700 rounded-lg text-sm disabled:opacity-50 whitespace-nowrap"
          >
            删除已选 ({{ selectedPaths.size }})
          </button>
          <button
            @click="selectAll"
            :disabled="!supportsItemManagement"
            class="admin-button-soft px-3 py-1.5 rounded-lg text-sm whitespace-nowrap"
            :class="{ 'opacity-50 cursor-not-allowed': !supportsItemManagement }"
          >
            全选
          </button>
          <button
            @click="invertSelection"
            :disabled="!supportsItemManagement"
            class="admin-button-soft px-3 py-1.5 rounded-lg text-sm whitespace-nowrap"
            :class="{ 'opacity-50 cursor-not-allowed': !supportsItemManagement }"
          >
            反选
          </button>
        </template>
        <input ref="fileInput" type="file" multiple class="hidden" @change="handleFileInput(false, $event)" />
        <input ref="dirInput" type="file" multiple webkitdirectory class="hidden" @change="handleFileInput(true, $event)" />
        <div v-if="canSelectStorageProvider" class="admin-file-browser-provider-select admin-file-browser-provider-select--end shrink-0" @click.stop>
          <button
            ref="providerTriggerRef"
            type="button"
            class="admin-file-browser-select-trigger admin-file-browser-provider-field flex w-[168px] max-w-[168px] items-center justify-between gap-2 rounded-lg px-3 py-1.5 text-sm"
            :title="providerSummaryLabel"
            @click="toggleProviderMenu"
          >
            <span class="truncate">{{ providerSummaryLabel }}</span>
            <span class="admin-file-browser-select-arrow">▾</span>
          </button>
        </div>
      </div>
      <div
        v-if="actionsMenu.show"
        class="glass-popover admin-floating-popover admin-file-browser-popover fixed z-[95] w-[220px] rounded-2xl"
        :style="{ left: `${actionsMenu.x}px`, top: `${actionsMenu.y}px` }"
        @click.stop
      >
        <div class="admin-file-browser-popover-title px-3 pb-1 pt-3 text-[11px]">当前目录操作</div>
        <button type="button" class="admin-file-browser-popover-option admin-file-browser-context-action w-full text-left text-sm" @click="openCreateDialog(); actionsMenu.show = false" :disabled="!supportsDirectoryCreation">新建文件夹</button>
        <button type="button" class="admin-file-browser-popover-option admin-file-browser-context-action w-full text-left text-sm" @click="triggerFileInput(false); actionsMenu.show = false" :disabled="!activeProviderSupported">上传文件</button>
        <button type="button" class="admin-file-browser-popover-option admin-file-browser-context-action w-full text-left text-sm" @click="triggerFileInput(true); actionsMenu.show = false" :disabled="!activeProviderSupported">上传文件夹</button>
        <button
          type="button"
          class="admin-file-browser-popover-option admin-file-browser-context-action w-full text-left text-sm"
          :disabled="isAtRoot"
          :title="isAtRoot ? '根目录没有相册设置' : '打开当前目录的相册设置'"
          @click="openCurrentDirectoryAlbumSettings(); actionsMenu.show = false"
        >
          目录设置
        </button>
        <button type="button" class="admin-file-browser-popover-option admin-file-browser-context-action w-full text-left text-sm" @click="toggleMultiSelect(); actionsMenu.show = false" :disabled="!supportsItemManagement">{{ multiSelect ? '关闭多选模式' : '开启多选模式' }}</button>
        <div class="admin-file-browser-popover-divider mx-3"></div>
        <button type="button" class="admin-file-browser-popover-option admin-file-browser-context-action w-full text-left text-sm" @click="refresh(); actionsMenu.show = false">刷新当前目录</button>
        <div class="h-1.5 shrink-0"></div>
      </div>
      <div
        v-if="!supportsDirectoryCreation || !supportsItemManagement || !activeProviderSupported"
        class="glass-panel p-3 text-xs space-y-1 admin-file-browser-warning"
      >
        <div v-if="!activeProviderSupported">上传受限：{{ uploadDisabledReason }}</div>
        <div v-if="!supportsDirectoryCreation">建目录受限：{{ directoryCreationDisabledReason }}</div>
        <div v-if="!supportsItemManagement">批量管理受限：{{ managementDisabledReason }}</div>
      </div>

      <!-- 上传进度 -->
      <div v-if="uploading" class="glass-panel p-3 admin-file-browser-uploading">
        <div class="flex items-center gap-3">
          <div class="animate-spin w-5 h-5 rounded-full border-2 border-[color:var(--pe-theme-primary)] border-t-transparent"></div>
          <span class="admin-file-browser-feedback admin-file-browser-feedback--accent">{{ uploadStatus }}</span>
        </div>
      </div>

      <div
        class="glass-panel p-3 pb-24 relative transition-all duration-200"
        :class="['admin-file-browser-content', viewMode === 'list' ? 'admin-file-browser-content--list' : 'admin-file-browser-content--grid']"
        @dragover.prevent="onDragOver"
        @dragleave.prevent="onDragLeave"
        @drop.prevent="handleDrop"
      >
        <!-- 拖拽覆盖层 -->
        <div
          v-if="isDragOver"
          class="admin-file-browser-drag-overlay absolute inset-0 z-10 flex items-center justify-center rounded-lg border-2 border-dashed pointer-events-none"
        >
          <div class="text-center">
            <div class="text-4xl mb-2">{{ dragMode === 'move' ? '🧭' : '📂' }}</div>
            <div class="text-lg admin-file-browser-feedback admin-file-browser-feedback--accent">{{ dragMode === 'move' ? '拖放到目标文件夹即可移动' : '拖放文件或文件夹到此处上传' }}</div>
          </div>
        </div>
        <div v-if="loading" class="py-12">
          <div class="flex items-center justify-center gap-3 text-cyan-200">
            <div class="relative h-10 w-10">
              <span class="absolute inset-0 rounded-full border border-cyan-400/20"></span>
              <span class="absolute inset-1 rounded-full border-2 border-transparent border-t-cyan-300 border-r-sky-400 animate-spin"></span>
              <span class="absolute inset-[10px] rounded-full bg-cyan-300/20 animate-pulse"></span>
            </div>
            <div class="text-left">
              <div class="text-sm text-[color:var(--pe-admin-text-primary)]">正在整理当前目录</div>
            </div>
          </div>
        </div>
        <div v-else-if="error" class="text-center py-8 admin-file-browser-feedback admin-file-browser-feedback--danger">
          {{ error }}
        </div>
        <div v-else-if="!items.length" class="text-center py-8 admin-file-browser-empty-text">
          当前目录为空
        </div>
        <div v-else>
          <div class="mb-3 text-xs admin-file-browser-list-meta">
            {{ searchKeyword ? `找到 ${sortedItems.length} 项（当前目录共 ${items.length} 项）` : `共 ${items.length} 项` }}，当前第 {{ currentPage }} / {{ totalPages }} 页
          </div>
          <div :class="[gridClass, 'admin-file-browser-grid']">
          <!-- 文件夹 -->
          <div
            v-for="dir in paginatedDirectories"
            :key="dir.path"
            @click="goToPath(dir.path)"
            @contextmenu.prevent="showContextMenu($event, dir)"
            @dragstart="handleItemDragStart($event, dir)"
            @dragend="handleItemDragEnd"
            @dragover.prevent="handleFolderDragOver(dir)"
            @dragleave.prevent="handleFolderDragLeave(dir)"
            @drop.prevent="handleFolderDrop($event, dir)"
            :draggable="supportsItemManagement"
            class="group admin-file-browser-card admin-file-browser-card--directory rounded-2xl p-3 cursor-pointer transition-all duration-200 relative"
            :class="{
              'ring-2 ring-blue-500': selectedItem?.path === dir.path,
              'ring-2 ring-cyan-400 bg-cyan-500/10': dragMoveTargetPath === dir.path,
              'flex items-center gap-2 px-3 py-1.5 rounded-xl hover:translate-y-0': viewMode === 'list'
            }"
          >
            <label v-if="multiSelect" class="absolute top-2 right-12 z-[2]">
              <input type="checkbox" class="w-4 h-4" :checked="selectedPaths.has(dir.path)" @click.stop="toggleSelect(dir.path)" />
            </label>
            <button
              @click.stop="showItemMenu($event, dir)"
              class="admin-file-browser-item-menu absolute right-3 top-3 z-[2] flex h-8 w-8 items-center justify-center rounded-full transition"
              title="更多操作"
            >
              ⋯
            </button>
            <!-- 三合一封面 -->
            <div
              v-if="dir.leftVertical || dir.rightTop || dir.rightBottom"
              :class="viewMode === 'list'
                ? 'mb-0 h-10 w-10 shrink-0 overflow-hidden rounded-lg'
                : 'mb-2 grid h-32 grid-cols-2 gap-[2px] overflow-hidden rounded-lg'"
            >
              <template v-if="viewMode === 'list'">
                <img
                  :src="getImageUrl(dir.leftVertical || dir.rightTop || dir.rightBottom)"
                  :alt="dir.name"
                  class="h-full w-full rounded-xl object-cover"
                  loading="lazy"
                />
              </template>
              <template v-else>
                <div class="row-span-2 overflow-hidden">
                  <img
                    v-if="dir.leftVertical"
                    :src="getImageUrl(dir.leftVertical)"
                    :alt="dir.name"
                    class="w-full h-full object-cover"
                    loading="lazy"
                  />
                  <div v-else class="w-full h-full admin-file-browser-cover-fallback"></div>
                </div>
                <div class="overflow-hidden">
                  <img
                    v-if="dir.rightTop"
                    :src="getImageUrl(dir.rightTop)"
                    :alt="dir.name"
                    class="w-full h-full object-cover"
                    loading="lazy"
                  />
                  <div v-else class="w-full h-full admin-file-browser-cover-fallback"></div>
                </div>
                <div class="overflow-hidden relative">
                  <img
                    v-if="dir.rightBottom"
                    :src="getImageUrl(dir.rightBottom)"
                    :alt="dir.name"
                    class="w-full h-full object-cover"
                    loading="lazy"
                  />
                  <div v-else class="w-full h-full admin-file-browser-cover-fallback"></div>
                  <div
                    v-if="dir.photoCount && dir.photoCount > 0"
                    class="admin-file-browser-cover-count absolute inset-0 flex items-center justify-center text-xs font-semibold"
                  >
                    共 {{ dir.photoCount }} 张
                  </div>
                </div>
              </template>
            </div>
            <!-- 无封面时显示默认图标 -->
            <div
              v-else
              :class="viewMode === 'list'
                ? 'admin-file-browser-card-icon-shell mb-0 flex h-10 w-10 shrink-0 items-center justify-center rounded-lg'
                : 'admin-file-browser-card-icon-shell mb-2 flex h-32 items-center justify-center rounded-xl'"
            >
              <div class="transition-transform duration-200 group-hover:scale-110" :class="viewMode === 'list' ? 'text-xl' : 'text-4xl'">📁</div>
            </div>
            <div class="flex-1 min-w-0">
              <div class="flex items-start justify-between gap-3">
                <div class="min-w-0">
                  <div class="truncate font-medium text-[color:var(--pe-admin-text-primary)]" :title="dir.name">{{ dir.name }}</div>
                  <div class="mt-0.5 flex flex-wrap gap-x-2 gap-y-0 text-[11px] text-[color:var(--pe-admin-text-muted)]">
                    <span>{{ dir.photoCount ? `${dir.photoCount} 张图片` : '文件夹' }}</span>
                    <span v-if="dir.lastModified">{{ formatDate(dir.lastModified) }}</span>
                  </div>
                </div>
              </div>
              <div class="mt-0.5 flex flex-wrap gap-1" :class="viewMode === 'list' ? 'hidden xl:flex' : ''">
                <span
                  v-if="dir.albumHasCustomCover"
                  class="admin-file-browser-status-chip admin-file-browser-status-chip--fuchsia rounded-full px-2 py-0.5 text-[10px]"
                >
                  自定义封面
                </span>
                <span
                  v-if="dir.albumAggregateSubAlbums"
                  class="admin-file-browser-status-chip admin-file-browser-status-chip--amber rounded-full px-2 py-0.5 text-[10px]"
                >
                  聚合子相册
                </span>
                <span
                  v-if="dir.albumHidden"
                  class="admin-file-browser-status-chip admin-file-browser-status-chip--rose rounded-full px-2 py-0.5 text-[10px]"
                >
                  已隐藏
                </span>
              </div>
              <div v-if="dir.albumDescription && viewMode !== 'list'" class="mt-1 truncate text-[11px] text-[color:var(--pe-admin-text-faint)]">
                {{ dir.albumDescription }}
              </div>
            </div>
          </div>

          <!-- 文件 -->
          <div
            v-for="file in paginatedFiles"
            :key="file.path"
            @click="openFile(file)"
            @contextmenu.prevent="showContextMenu($event, file)"
            @dragstart="handleItemDragStart($event, file)"
            @dragend="handleItemDragEnd"
            :draggable="supportsItemManagement"
            class="group admin-file-browser-card admin-file-browser-card--file rounded-2xl p-3 cursor-pointer transition-all duration-200 relative"
            :class="{ 'ring-2 ring-blue-500': selectedItem?.path === file.path, 'flex items-center gap-2 px-3 py-1.5 rounded-xl hover:translate-y-0': viewMode === 'list' }"
          >
            <label v-if="multiSelect" class="absolute top-2 right-12 z-[2]">
              <input type="checkbox" class="w-4 h-4" :checked="selectedPaths.has(file.path)" @click.stop="toggleSelect(file.path)" />
            </label>
            <button
              @click.stop="showItemMenu($event, file)"
              class="admin-file-browser-item-menu absolute right-3 top-3 z-[2] flex h-8 w-8 items-center justify-center rounded-full transition"
              title="更多操作"
            >
              ⋯
            </button>
            <!-- 图片文件显示缩略图 -->
            <div
              v-if="file.thumbnail"
              :class="viewMode === 'list'
                ? 'admin-file-browser-thumb-shell mb-0 flex h-10 w-10 shrink-0 items-center justify-center overflow-hidden rounded-lg'
                : 'admin-file-browser-thumb-shell mb-2 aspect-square flex items-center justify-center overflow-hidden rounded-xl'"
            >
              <img
                :src="getImageUrl(file.thumbnail)"
                :alt="file.name"
                class="w-full h-full object-cover transition-transform duration-300 group-hover:scale-[1.03]"
                loading="lazy"
              />
            </div>
            <!-- 非图片文件显示默认图标 -->
            <div
              v-else
              :class="viewMode === 'list'
                ? 'admin-file-browser-card-icon-shell mb-0 flex h-10 w-10 shrink-0 items-center justify-center rounded-lg'
                : 'admin-file-browser-card-icon-shell mb-2 flex h-32 items-center justify-center rounded-xl'"
            >
              <div class="transition-transform duration-200 group-hover:scale-110" :class="viewMode === 'list' ? 'text-xl' : 'text-4xl'">{{ isImageFile(file) ? '🖼️' : '📄' }}</div>
            </div>
            <div class="flex-1 min-w-0">
              <div class="flex items-start justify-between gap-3">
                <div class="min-w-0">
                  <div class="truncate font-medium text-[color:var(--pe-admin-text-primary)]" :title="file.name">{{ file.name }}</div>
                  <div class="mt-0.5 flex flex-wrap gap-x-2 gap-y-0 text-[11px] text-[color:var(--pe-admin-text-muted)]">
                    <span>{{ isImageFile(file) ? '图片' : '文件' }}</span>
                    <span>{{ formatFileSize(file.size) }}</span>
                    <span>{{ formatDate(file.lastModified) }}</span>
                    <span v-if="file.thumbnail?.id">已入库</span>
                  </div>
                </div>
              </div>
              <div v-if="file.photoHidden" class="mt-1">
                <span class="admin-file-browser-status-chip admin-file-browser-status-chip--rose rounded-full px-2 py-0.5 text-[10px]">
                  已隐藏
                </span>
              </div>
            </div>
          </div>
        </div>
        </div>
      </div>
    </div>

    <div
      v-if="breadcrumbMenu.show"
      class="breadcrumb-menu glass-popover admin-floating-popover admin-file-browser-popover fixed z-[80] w-[320px] overflow-hidden rounded-2xl"
      :style="{ left: `${breadcrumbMenu.x}px`, top: `${breadcrumbMenu.y}px` }"
    >
      <div class="admin-file-browser-popover-head px-4 py-3 text-xs">
        {{ breadcrumbMenu.pathLabel }}
      </div>
      <div v-if="breadcrumbMenu.loading" class="px-4 py-6 text-sm admin-file-browser-popover-empty">加载中...</div>
      <div v-else-if="!breadcrumbMenu.items.length" class="px-4 py-6 text-sm admin-file-browser-popover-empty">此层级下暂无内容</div>
      <div v-else class="max-h-[360px] overflow-auto py-2">
        <button
          v-for="entry in breadcrumbMenu.items"
          :key="entry.path"
          @click="openBreadcrumbMenuItem(entry)"
          class="admin-file-browser-popover-option flex w-full items-center justify-between gap-3 px-4 py-2.5 text-left text-sm transition"
        >
          <div class="min-w-0">
            <div class="truncate text-[color:var(--pe-admin-text-primary)]">{{ entry.name }}</div>
            <div class="mt-1 text-xs text-[color:var(--pe-admin-text-faint)]">{{ entry.isDirectory ? '文件夹' : formatFileSize(entry.size) }}</div>
          </div>
          <span class="shrink-0 admin-file-browser-popover-icon">{{ entry.isDirectory ? '📁' : '📄' }}</span>
        </button>
      </div>
    </div>

    <div class="fixed right-4 bottom-0 z-[70] max-w-[calc(100vw-2rem)] px-3 py-2 admin-file-browser-floating-dock">
      <div class="flex items-center gap-2 overflow-x-auto overflow-y-visible whitespace-nowrap">
        <div class="relative" @click.stop>
          <button
            ref="viewTriggerRef"
            type="button"
            class="admin-file-browser-select-trigger min-w-[166px] rounded-full px-3 py-1.5 text-sm"
            @click="toggleViewMenu"
          >
            <span class="truncate">{{ viewSummaryLabel }}</span>
            <span class="admin-file-browser-select-arrow">▾</span>
          </button>
        </div>
        <div class="relative" @click.stop>
          <button
            ref="sortTriggerRef"
            @click="toggleSortMenu"
            class="admin-file-browser-dock-icon flex h-9 w-9 items-center justify-center rounded-full text-sm"
            :title="sortSummaryLabel"
          >
            <span class="text-base leading-none text-[color:var(--pe-admin-text-primary)]">{{ sortMenu.show ? '↕' : '↕' }}</span>
          </button>
        </div>
        <div class="relative" @click.stop>
          <button
            ref="pageSizeTriggerRef"
            type="button"
            class="admin-file-browser-select-trigger min-w-[92px] rounded-full px-3 py-1.5 text-sm"
            @click="togglePageSizeMenu"
          >
            <span>{{ pageSize }} / 页</span>
            <span class="admin-file-browser-select-arrow">▾</span>
          </button>
        </div>
        <button
          @click="prevPage"
          :disabled="currentPage <= 1"
          class="admin-file-browser-dock-button rounded-full px-3 py-1.5 text-sm transition disabled:opacity-40 disabled:cursor-not-allowed"
        >
          上一页
        </button>
        <span class="admin-file-browser-dock-meta text-xs">{{ currentPage }}/{{ totalPages }}</span>
        <button
          @click="nextPage"
          :disabled="currentPage >= totalPages"
          class="admin-file-browser-dock-button rounded-full px-3 py-1.5 text-sm transition disabled:opacity-40 disabled:cursor-not-allowed"
        >
          下一页
        </button>
      </div>
    </div>

    <div
      v-if="providerMenu.show"
      class="glass-popover admin-floating-popover admin-file-browser-popover fixed z-[95] w-[280px] rounded-2xl"
      :style="{ left: `${providerMenu.x}px`, top: `${providerMenu.y}px` }"
      @click.stop
    >
      <div class="admin-file-browser-popover-title px-3 pb-1 pt-3 text-[11px]">存储提供者</div>
      <button
        v-for="provider in availableStorageProviders"
        :key="provider.id"
        type="button"
        @click="selectStorageProvider(provider.id)"
        class="admin-file-browser-popover-option mx-1.5 flex w-[calc(100%-0.75rem)] items-center justify-between gap-2 rounded-xl px-2.5 py-2 text-left text-sm leading-none transition"
        :class="{ 'ring-1 admin-file-browser-popover-option--active': selectedProviderId === provider.id }"
        :disabled="!provider.enabled || !provider.browserSupported"
      >
        <div class="min-w-0">
          <div class="truncate">{{ provider.name }}</div>
          <div class="mt-1 text-[11px] text-[color:var(--pe-admin-text-faint)]">
            {{ storageTypeLabel(provider.type) }}{{ provider.browserSupported ? '' : ' · 暂不支持浏览' }}
          </div>
        </div>
        <span
          class="admin-file-browser-popover-check flex h-5 w-5 shrink-0 items-center justify-center rounded-full text-[11px]"
          :class="{ 'admin-file-browser-popover-check--active': selectedProviderId === provider.id }"
        >{{ selectedProviderId === provider.id ? '●' : '○' }}</span>
      </button>
      <div class="h-1.5 shrink-0"></div>
    </div>

    <div
      v-if="viewMenu.show"
      class="glass-popover admin-floating-popover admin-file-browser-popover fixed z-[95] w-[244px] rounded-2xl"
      :style="{ left: `${viewMenu.x}px`, top: `${viewMenu.y}px` }"
      @click.stop
    >
      <div class="admin-file-browser-popover-title px-3 pb-1 pt-3 text-[11px]">视图</div>
      <button
        v-for="option in viewModeOptions"
        :key="option.value"
        type="button"
        @click="selectViewMode(option.value)"
        class="admin-file-browser-popover-option mx-1.5 flex w-[calc(100%-0.75rem)] items-center justify-between gap-2 rounded-xl px-2.5 py-2 text-left text-sm leading-none transition"
        :class="{ 'ring-1 admin-file-browser-popover-option--active': viewMode === option.value }"
      >
        <span>{{ option.label }}</span>
        <span
          class="admin-file-browser-popover-check flex h-5 w-5 shrink-0 items-center justify-center rounded-full text-[11px]"
          :class="{ 'admin-file-browser-popover-check--active': viewMode === option.value }"
        >{{ viewMode === option.value ? '●' : '○' }}</span>
      </button>
      <div class="admin-file-browser-popover-divider mx-3"></div>
      <div class="admin-file-browser-popover-title px-3 pb-1 pt-2.5 text-[11px]">尺寸</div>
      <button
        v-for="option in gridPresetOptions"
        :key="option.value"
        type="button"
        @click="selectGridPreset(option.value)"
        class="admin-file-browser-popover-option mx-1.5 flex w-[calc(100%-0.75rem)] items-center justify-between gap-2 rounded-xl px-2.5 py-2 text-left text-sm leading-none transition"
        :class="{ 'ring-1 admin-file-browser-popover-option--active': viewMode === 'grid' && gridPreset === option.value }"
      >
        <span>{{ option.label }}</span>
        <span
          class="admin-file-browser-popover-check flex h-5 w-5 shrink-0 items-center justify-center rounded-full text-[11px]"
          :class="{ 'admin-file-browser-popover-check--active': viewMode === 'grid' && gridPreset === option.value }"
        >{{ viewMode === 'grid' && gridPreset === option.value ? '●' : '○' }}</span>
      </button>
      <div class="h-1.5 shrink-0"></div>
    </div>

    <div
      v-if="pageSizeMenu.show"
      class="glass-popover admin-floating-popover admin-file-browser-popover fixed z-[95] w-[136px] rounded-2xl"
      :style="{ left: `${pageSizeMenu.x}px`, top: `${pageSizeMenu.y}px` }"
      @click.stop
    >
      <div class="admin-file-browser-popover-title px-3 pb-1 pt-3 text-[11px]">每页数量</div>
      <button
        v-for="option in pageSizeOptions"
        :key="option"
        type="button"
        @click="selectPageSize(option)"
        class="admin-file-browser-popover-option mx-1.5 flex w-[calc(100%-0.75rem)] items-center justify-between gap-2 rounded-xl px-2.5 py-2 text-left text-sm leading-none transition"
        :class="{ 'ring-1 admin-file-browser-popover-option--active': pageSize === option }"
      >
        <span>{{ option }} / 页</span>
        <span
          class="admin-file-browser-popover-check flex h-5 w-5 shrink-0 items-center justify-center rounded-full text-[11px]"
          :class="{ 'admin-file-browser-popover-check--active': pageSize === option }"
        >{{ pageSize === option ? '●' : '○' }}</span>
      </button>
      <div class="h-1.5 shrink-0"></div>
    </div>

    <div
      v-if="sortMenu.show"
      class="glass-popover admin-floating-popover admin-file-browser-popover fixed z-[95] w-[232px] rounded-2xl"
      :style="{ left: `${sortMenu.x}px`, top: `${sortMenu.y}px` }"
      @click.stop
    >
      <div class="admin-file-browser-popover-title px-3 pb-1 pt-3 text-[11px]">排序方式</div>
      <button
        v-for="option in sortModeOptions"
        :key="option.value"
        @click="selectSortMode(option.value)"
        class="admin-file-browser-popover-option mx-1.5 flex w-[calc(100%-0.75rem)] items-center justify-between gap-2 rounded-xl px-2.5 py-2 text-left text-sm leading-none transition"
        :class="{ 'ring-1 admin-file-browser-popover-option--active': sortMode === option.value }"
      >
        <span>{{ option.label }}</span>
        <span
          class="admin-file-browser-popover-check flex h-5 w-5 shrink-0 items-center justify-center rounded-full text-[11px]"
          :class="{ 'admin-file-browser-popover-check--active': sortMode === option.value }"
        >{{ sortMode === option.value ? '●' : '○' }}</span>
      </button>
      <div class="admin-file-browser-popover-divider mx-3"></div>
      <div class="admin-file-browser-popover-title px-3 pb-1 pt-2.5 text-[11px]">类型策略</div>
      <button
        v-for="option in typeOrderOptions"
        :key="option.value"
        @click="selectTypeOrderMode(option.value)"
        class="admin-file-browser-popover-option mx-1.5 flex w-[calc(100%-0.75rem)] items-center justify-between gap-2 rounded-xl px-2.5 py-2 text-left text-sm leading-none transition"
        :class="{ 'ring-1 admin-file-browser-popover-option--active': typeOrderMode === option.value }"
      >
        <span>{{ option.label }}</span>
        <span
          class="admin-file-browser-popover-check flex h-5 w-5 shrink-0 items-center justify-center rounded-full text-[11px]"
          :class="{ 'admin-file-browser-popover-check--active': typeOrderMode === option.value }"
        >{{ typeOrderMode === option.value ? '●' : '○' }}</span>
      </button>
      <div class="h-1.5 shrink-0"></div>
    </div>

    <!-- 右键菜单 -->
    <div
      v-if="contextMenu.show"
      :style="{ left: contextMenu.x + 'px', top: contextMenu.y + 'px' }"
      class="glass-popover admin-floating-popover admin-file-browser-context-menu fixed z-50 min-w-[180px] overflow-hidden rounded-2xl"
    >
      <button
        v-if="contextMenu.item?.isDirectory"
        @click="goToPath(contextMenu.item.path)"
        class="admin-file-browser-context-action w-full text-left px-4 py-3 text-sm transition"
      >
        打开
      </button>
      <button
        v-if="contextMenu.item?.isDirectory && !contextMenu.item?.albumBound"
        @click="bindContextDirectory"
        class="admin-file-browser-context-action w-full text-left px-4 py-3 text-sm transition"
      >
        绑定为相册
      </button>
      <button
        v-if="contextMenu.item?.isDirectory && contextMenu.item?.albumBound"
        @click="openContextAlbumSettings"
        class="admin-file-browser-context-action w-full text-left px-4 py-3 text-sm transition"
      >
        相册设置
      </button>
      <button
        v-if="contextMenu.item && !contextMenu.item.isDirectory && isImageFile(contextMenu.item)"
        @click="openContextViewer"
        class="admin-file-browser-context-action w-full text-left px-4 py-3 text-sm transition"
      >
        查看图片
      </button>
      <button
        v-if="contextMenu.item && !contextMenu.item.isDirectory"
        @click="downloadContextFile"
        class="admin-file-browser-context-action w-full text-left px-4 py-3 text-sm transition"
      >
        下载
      </button>
      <button
        v-if="contextMenu.item && !contextMenu.item.isDirectory && contextMenu.item.thumbnail?.id"
        @click="toggleContextPhotoHidden"
        class="admin-file-browser-context-action w-full text-left px-4 py-3 text-sm transition"
      >
        {{ contextMenu.item.photoHidden ? '恢复公开显示' : '隐藏图片' }}
      </button>
      <button
        v-if="contextMenu.item && !contextMenu.item.isDirectory && contextMenu.item.thumbnail?.id"
        @click="openContextPhotoTagDialog"
        class="admin-file-browser-context-action w-full text-left px-4 py-3 text-sm transition"
      >
        标签管理
      </button>
      <button
        v-if="contextMenu.item && !contextMenu.item.isDirectory && contextMenu.item.thumbnail?.id"
        @click="rescanContextPhotoFaces"
        class="admin-file-browser-context-action w-full text-left px-4 py-3 text-sm transition"
      >
        重建人脸
      </button>
      <button
        v-if="contextMenu.item && !contextMenu.item.isDirectory && currentDirectoryAlbum.albumBound && contextMenu.item.thumbnail?.id"
        @click="setContextPhotoAsCover"
        class="admin-file-browser-context-action w-full text-left px-4 py-3 text-sm transition"
      >
        设为当前相册封面
      </button>
      <button
        @click="startRename"
        :disabled="!supportsItemManagement"
        class="admin-file-browser-context-action w-full text-left px-4 py-3 text-sm transition"
        :class="{ 'opacity-50 cursor-not-allowed': !supportsItemManagement }"
      >
        重命名
      </button>
      <button
        @click="confirmDelete"
        :disabled="!supportsItemManagement"
        class="admin-file-browser-context-action admin-file-browser-context-action--danger w-full text-left px-4 py-3 text-sm transition"
        :class="{ 'opacity-50 cursor-not-allowed': !supportsItemManagement }"
      >
        删除
      </button>
    </div>

    <!-- 创建文件夹对话框 -->
    <div
      v-if="showCreateDialog"
      class="admin-modal-overlay fixed inset-0 flex items-center justify-center z-50"
      @click.self="showCreateDialog = false"
    >
      <div class="glass-modal admin-modal-card admin-file-browser-dialog w-full max-w-md p-6">
        <h2 class="text-xl font-light mb-4">新建文件夹</h2>
        <label class="block space-y-2 mb-4">
          <span class="text-sm admin-file-browser-dialog-label">文件夹名称</span>
          <input
            v-model="newFolderName"
            @keyup.enter="createFolder"
            placeholder="文件夹名称"
            ref="newFolderInput"
            class="admin-file-browser-dialog-input w-full px-4 py-2 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
          />
          <span class="block text-xs admin-file-browser-dialog-help">只填写名称，不要包含 `/` 或完整路径。</span>
        </label>
        <div class="flex gap-2 justify-end">
          <button
            @click="showCreateDialog = false"
            class="admin-button-soft px-4 py-2 rounded-lg"
          >
            取消
          </button>
          <button
            @click="createFolder"
            :disabled="!newFolderName.trim() || creating"
            class="admin-button-primary px-4 py-2 rounded-lg disabled:opacity-50"
          >
            {{ creating ? '创建中...' : '创建' }}
          </button>
        </div>
      </div>
    </div>

    <!-- 重命名对话框 -->
    <div
      v-if="showRenameDialog"
      class="admin-modal-overlay fixed inset-0 flex items-center justify-center z-50"
      @click.self="showRenameDialog = false"
    >
      <div class="glass-modal admin-modal-card admin-file-browser-dialog w-full max-w-md p-6">
        <h2 class="text-xl font-light mb-4">重命名</h2>
        <label class="block space-y-2 mb-4">
          <span class="text-sm admin-file-browser-dialog-label">新名称</span>
          <input
            v-model="renameValue"
            @keyup.enter="renameItem"
            placeholder="输入新名称"
            class="admin-file-browser-dialog-input w-full px-4 py-2 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
            autofocus
          />
          <span class="block text-xs admin-file-browser-dialog-help">只修改名称，不要输入目录路径。</span>
        </label>
        <div class="flex gap-2 justify-end">
          <button
            @click="showRenameDialog = false"
            class="admin-button-soft px-4 py-2 rounded-lg"
          >
            取消
          </button>
          <button
            @click="renameItem"
            :disabled="!renameValue.trim() || renaming"
            class="admin-button-primary px-4 py-2 rounded-lg disabled:opacity-50"
          >
            {{ renaming ? '重命名中...' : '确定' }}
          </button>
        </div>
      </div>
    </div>

    <div
      v-if="showMoveDialog"
      class="admin-modal-overlay fixed inset-0 z-[64] flex items-center justify-center p-4"
      @click.self="closeMoveDialog"
    >
      <div class="glass-modal admin-modal-card admin-file-browser-dialog w-full max-w-lg overflow-hidden rounded-[28px]">
        <div class="admin-file-browser-dialog-head flex items-center justify-between gap-4 px-6 py-4">
          <div>
            <h2 class="text-xl font-light">移动项目</h2>
            <div class="admin-file-browser-dialog-help mt-1 text-xs">已选择 {{ movePendingPaths.length }} 项</div>
          </div>
          <button @click="closeMoveDialog" class="admin-button-soft px-3 py-2 rounded-xl text-sm">关闭</button>
        </div>
        <div class="px-6 py-5 space-y-4">
          <div class="admin-file-browser-dialog-block rounded-2xl p-4 space-y-2">
            <div class="admin-file-browser-dialog-help text-xs">目标目录</div>
            <input
              v-model="moveTargetInput"
              ref="moveTargetInputRef"
              @keyup.enter="submitMoveDialog"
              placeholder="目标目录，留空为根目录"
              class="admin-file-browser-dialog-input w-full rounded-2xl px-4 py-3 text-sm outline-none transition focus:border-sky-400/40"
            />
          </div>
          <div class="admin-file-browser-dialog-block rounded-2xl p-4">
            <div class="admin-file-browser-dialog-help text-xs">待移动项目</div>
            <div class="mt-3 flex flex-wrap gap-2 max-h-32 overflow-auto">
              <span
                v-for="path in movePendingPaths"
                :key="`move-${path}`"
                class="admin-file-browser-dialog-chip rounded-full px-2.5 py-1 text-[11px]"
              >
                {{ path.split('/').filter(Boolean).slice(-1)[0] || path }}
              </span>
            </div>
          </div>
        </div>
        <div class="admin-file-browser-dialog-foot flex justify-end gap-2 px-6 py-4">
          <button @click="closeMoveDialog" class="admin-button-soft px-4 py-2 rounded-xl">取消</button>
          <button
            @click="submitMoveDialog"
            :disabled="movingItems"
            class="admin-button-primary px-4 py-2 rounded-xl disabled:opacity-50"
          >
            {{ movingItems ? '移动中...' : '确认移动' }}
          </button>
        </div>
      </div>
    </div>

    <div
      v-if="showDeleteDialog"
      class="admin-modal-overlay fixed inset-0 flex items-center justify-center z-[65] p-4"
      @click.self="closeDeleteDialog"
    >
      <div class="glass-modal admin-modal-card admin-file-browser-dialog w-full max-w-4xl overflow-hidden rounded-[28px]">
        <div class="admin-file-browser-dialog-head flex items-center justify-between gap-4 px-6 py-4">
          <div>
            <h2 class="text-xl font-light">确认删除</h2>
            <div class="admin-file-browser-dialog-help mt-1 text-xs">将删除 {{ deletePreviewSummary }}</div>
          </div>
          <button @click="closeDeleteDialog" class="admin-button-soft px-3 py-2 rounded-xl text-sm">关闭</button>
        </div>
        <div class="max-h-[70vh] overflow-auto px-6 py-5 space-y-4">
          <div v-if="loadingDeletePreview" class="text-sm admin-file-browser-dialog-label">正在生成删除清单...</div>
          <div v-else-if="deletePreviewError" class="text-sm text-red-300">{{ deletePreviewError }}</div>
          <div v-else-if="!deletePreviewEntries.length" class="text-sm admin-file-browser-dialog-help">没有可删除内容。</div>
          <div v-else class="space-y-4">
            <div
              v-for="(entry, index) in deletePreviewEntries"
              :key="`${entry.path}-${index}`"
              class="admin-file-browser-dialog-block rounded-2xl p-4 space-y-3"
            >
              <div class="flex items-start justify-between gap-4">
                <div class="min-w-0">
                  <div class="text-sm text-[color:var(--pe-admin-text-primary)] truncate">{{ entry.name }}</div>
                  <div class="admin-file-browser-dialog-help mt-1 break-all text-xs">{{ entry.path }}</div>
                </div>
                <div class="admin-file-browser-dialog-help whitespace-nowrap text-xs">
                  {{ entry.isDirectory ? `目录 · ${entry.photoCount} 张图片` : '文件' }}
                </div>
              </div>
              <div v-if="entry.photos?.length" class="space-y-2">
                <div
                  v-for="photo in entry.photos"
                  :key="photo.photoId || photo.path"
                  class="admin-file-browser-dialog-subblock rounded-xl px-3 py-2"
                >
                  <div class="text-sm admin-file-browser-dialog-label">{{ photo.filename }}</div>
                  <div class="flex flex-wrap gap-2 mt-2">
                    <span
                      v-for="tag in photo.tags"
                      :key="`${photo.photoId}-${tag}`"
                      class="admin-file-browser-dialog-chip px-2 py-1 rounded-full text-[11px]"
                    >
                      {{ tag }}
                    </span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
        <div class="admin-file-browser-dialog-foot flex justify-end gap-2 px-6 py-4">
          <button @click="closeDeleteDialog" class="admin-button-soft px-4 py-2 rounded-xl">取消</button>
          <button
            @click="submitDeleteConfirmed"
            :disabled="loadingDeletePreview || deletingItems"
            class="admin-button-danger px-4 py-2 rounded-xl disabled:opacity-50"
          >
            {{ deletingItems ? '删除中...' : '确认删除' }}
          </button>
        </div>
      </div>
    </div>

    <div
      v-if="showAlbumSettingsDrawer"
      class="admin-modal-overlay fixed inset-0 z-[78]"
      @click.self="closeAlbumSettingsDrawer"
    >
      <div class="glass-modal admin-modal-card admin-file-browser-drawer absolute right-0 top-0 h-full w-full max-w-xl border-l">
        <div class="flex h-full flex-col">
          <div class="admin-file-browser-dialog-head flex items-center justify-between gap-4 px-6 py-5">
            <div class="min-w-0">
              <h2 class="truncate text-xl font-light admin-file-browser-dialog-title">{{ albumSettingsTarget?.name || '相册设置' }}</h2>
              <div class="admin-file-browser-dialog-help mt-1 truncate text-xs">{{ albumSettingsTarget?.path }}</div>
            </div>
            <button
              @click="closeAlbumSettingsDrawer"
              class="admin-button-soft rounded-xl px-3 py-2 text-sm"
            >
              关闭
            </button>
          </div>
          <div class="flex-1 overflow-auto px-6 py-5 space-y-5">
            <div class="admin-file-browser-dialog-block rounded-2xl p-4">
              <div class="text-sm admin-file-browser-dialog-label">基础状态</div>
              <div class="mt-3 flex flex-wrap gap-2">
                <span class="admin-file-browser-status-chip admin-file-browser-status-chip--sky rounded-full px-2.5 py-1 text-[11px]">已绑定相册</span>
                <span v-if="albumSettingsForm.aggregateSubAlbums" class="admin-file-browser-status-chip admin-file-browser-status-chip--amber rounded-full px-2.5 py-1 text-[11px]">聚合子相册</span>
                <span v-if="albumSettingsForm.isHidden" class="admin-file-browser-status-chip admin-file-browser-status-chip--rose rounded-full px-2.5 py-1 text-[11px]">已隐藏</span>
                <span v-if="albumSettingsTarget?.albumHasCustomCover" class="admin-file-browser-status-chip admin-file-browser-status-chip--fuchsia rounded-full px-2.5 py-1 text-[11px]">自定义封面</span>
              </div>
            </div>

            <label class="block space-y-2">
              <span class="text-sm admin-file-browser-dialog-label">相册说明</span>
              <textarea
                v-model="albumSettingsForm.description"
                rows="6"
                placeholder="相册说明"
                class="admin-file-browser-dialog-input w-full rounded-2xl px-4 py-3 text-sm outline-none transition"
              />
            </label>

            <button
              @click="albumSettingsForm.isHidden = !albumSettingsForm.isHidden"
              class="admin-file-browser-dialog-block flex w-full items-center justify-between rounded-2xl px-4 py-4 text-left transition"
            >
              <div>
                <div class="text-sm admin-file-browser-dialog-title">公开展示</div>
                <div class="admin-file-browser-dialog-help mt-1 text-xs">关闭后，这个相册不会在前台对外展示。</div>
              </div>
              <div
                class="admin-file-browser-status-chip rounded-full px-3 py-1 text-xs"
                :class="albumSettingsForm.isHidden ? 'admin-file-browser-status-chip--rose' : 'admin-file-browser-status-chip--emerald'"
              >
                {{ albumSettingsForm.isHidden ? '已隐藏' : '公开中' }}
              </div>
            </button>

            <button
              @click="albumSettingsForm.aggregateSubAlbums = !albumSettingsForm.aggregateSubAlbums"
              class="admin-file-browser-dialog-block flex w-full items-center justify-between rounded-2xl px-4 py-4 text-left transition"
            >
              <div>
                <div class="text-sm admin-file-browser-dialog-title">聚合子相册</div>
                <div class="admin-file-browser-dialog-help mt-1 text-xs">开启后，这个目录会把下级相册一起作为展示集合。</div>
              </div>
              <div
                class="admin-file-browser-status-chip rounded-full px-3 py-1 text-xs"
                :class="albumSettingsForm.aggregateSubAlbums ? 'admin-file-browser-status-chip--amber' : 'admin-file-browser-status-chip--muted'"
              >
                {{ albumSettingsForm.aggregateSubAlbums ? '已开启' : '未开启' }}
              </div>
            </button>

            <label class="block space-y-2">
              <span class="text-sm admin-file-browser-dialog-label">图片排序方式</span>
              <select
                v-model="albumSettingsForm.photoSortOrder"
                class="admin-file-browser-dialog-input w-full rounded-2xl px-4 py-3 text-sm outline-none transition"
              >
                <option value="">跟随全局设置</option>
                <option value="takenAtDesc">拍摄时间从新到旧</option>
                <option value="takenAtAsc">拍摄时间从旧到新</option>
                <option value="createdAtDesc">上传时间从新到旧</option>
                <option value="createdAtAsc">上传时间从旧到新</option>
                <option value="qualityDesc">评分从高到低</option>
                <option value="filenameAsc">文件名正序</option>
                <option value="filenameDesc">文件名倒序</option>
              </select>
            </label>

            <label class="block space-y-2">
              <span class="text-sm admin-file-browser-dialog-label">下载权限</span>
              <select
                v-model="albumSettingsForm.downloadAllowed"
                class="admin-file-browser-dialog-input w-full rounded-2xl px-4 py-3 text-sm outline-none transition"
              >
                <option value="">跟随全局设置</option>
                <option value="true">允许下载</option>
                <option value="false">禁止下载</option>
              </select>
            </label>

            <div class="admin-file-browser-dialog-block rounded-2xl p-4">
              <div class="flex items-center justify-between gap-3">
                <div>
                  <div class="text-sm admin-file-browser-dialog-title">自定义封面</div>
                  <div class="admin-file-browser-dialog-help mt-1 text-xs">最多选择 4 张。</div>
                </div>
                <button
                  @click="openAlbumCoverDialog"
                  class="admin-file-browser-status-chip admin-file-browser-status-chip--fuchsia rounded-xl px-4 py-2 text-sm transition"
                >
                  设置封面
                </button>
              </div>
            </div>

          </div>
          <div class="admin-file-browser-dialog-foot flex items-center justify-end gap-3 px-6 py-4">
            <button
              @click="closeAlbumSettingsDrawer"
              class="admin-button-soft rounded-xl px-4 py-2"
            >
              取消
            </button>
            <button
              @click="saveAlbumSettings"
              :disabled="savingAlbumSettings || !albumSettingsDirty"
              class="admin-button-primary rounded-xl px-4 py-2 disabled:cursor-not-allowed disabled:opacity-50"
            >
              {{ savingAlbumSettings ? '保存中...' : '保存设置' }}
            </button>
          </div>
        </div>
      </div>
    </div>

    <div
      v-if="showAlbumCoverDialog"
      class="admin-modal-overlay fixed inset-0 z-[69] flex items-center justify-center p-4"
      @click.self="closeAlbumCoverDialog"
    >
      <div class="glass-modal admin-modal-card admin-file-browser-dialog w-full max-w-5xl overflow-hidden rounded-[28px]">
        <div class="admin-file-browser-dialog-head flex items-center justify-between gap-4 px-6 py-4">
          <div>
            <h2 class="text-xl font-light">设置相册封面</h2>
            <div class="admin-file-browser-dialog-help mt-1 text-xs">最多选择 4 张，首图优先。</div>
          </div>
          <button @click="closeAlbumCoverDialog" class="admin-button-soft px-3 py-2 rounded-xl text-sm">关闭</button>
        </div>
        <div class="px-6 py-5">
          <div v-if="!browserViewerPhotos.length" class="py-10 text-center text-sm admin-file-browser-dialog-help">
            当前目录还没有可用图片，暂时无法设置自定义封面。
          </div>
          <div v-else class="grid grid-cols-2 md:grid-cols-3 xl:grid-cols-4 gap-4 max-h-[70vh] overflow-auto">
            <button
              v-for="photo in browserViewerPhotos"
              :key="`cover-${photo.browserPath}`"
              @click="toggleCoverSelection(photo)"
              class="admin-file-browser-cover-card group relative rounded-2xl overflow-hidden text-left transition"
              :class="selectedCoverIds.includes(photo.id) ? 'admin-file-browser-cover-card--active' : 'admin-file-browser-cover-card--idle'"
            >
              <div class="admin-file-browser-cover-frame aspect-square">
                <img
                  :src="buildPhotoAssetUrl(photo, 'small') || buildPhotoAssetUrl(photo, 'thumbnail') || buildPhotoAssetUrl(photo, 'large') || ''"
                  :alt="photo.filename"
                  class="w-full h-full object-cover transition duration-300 group-hover:scale-[1.03]"
                />
              </div>
              <div class="admin-file-browser-cover-order absolute top-3 left-3 rounded-full px-2.5 py-1 text-[11px]">
                {{ coverIndexLabel(photo.id) }}
              </div>
              <div class="p-3">
                <div class="truncate text-sm admin-file-browser-dialog-title">{{ photo.filename }}</div>
              </div>
            </button>
          </div>
        </div>
        <div class="admin-file-browser-dialog-foot flex items-center justify-end gap-3 px-6 py-4">
          <button @click="closeAlbumCoverDialog" class="admin-button-soft rounded-xl px-4 py-2">取消</button>
          <button
            @click="saveAlbumCoverSelection"
            :disabled="savingAlbumSettings"
            class="admin-button-primary rounded-xl px-4 py-2 disabled:opacity-50"
          >
            {{ savingAlbumSettings ? '保存中...' : '保存封面' }}
          </button>
        </div>
      </div>
    </div>

    <PhotoViewer
      v-if="browserViewerPhotos.length > 0"
      :photos="browserViewerPhotos"
      :visible="viewerVisible"
      :start-index="viewerIndex"
      :admin-menu-actions="viewerAdminActions"
      @update:visible="viewerVisible = $event"
      @viewer-index-change="handleViewerIndexChange"
      @admin-action="handleViewerAdminAction"
    />

    <div
      v-if="showPhotoTagDialog"
      class="admin-modal-overlay fixed inset-0 z-[72] flex items-center justify-center p-4"
      @click.self="closePhotoTagDialog"
    >
      <div class="glass-modal admin-modal-card admin-file-browser-dialog w-full max-w-2xl overflow-hidden rounded-[28px]">
        <div class="admin-file-browser-dialog-head flex items-center justify-between gap-4 px-6 py-4">
          <div>
            <h2 class="text-xl font-light">图片标签管理</h2>
            <div class="admin-file-browser-dialog-help mt-1 text-xs">{{ currentViewerPhoto?.filename }}</div>
          </div>
          <button @click="closePhotoTagDialog" class="admin-button-soft px-3 py-2 rounded-xl text-sm">关闭</button>
        </div>
        <div class="px-6 py-5 space-y-5">
          <div>
            <div class="text-sm admin-file-browser-dialog-label">当前标签</div>
            <div class="mt-3 flex flex-wrap gap-2">
              <button
                v-for="tag in (currentViewerPhoto?.tags || [])"
                :key="`photo-tag-${tag.id}`"
                @click="removeCurrentPhotoTag(tag.id)"
                :disabled="savingPhotoTag"
                class="admin-file-browser-status-chip admin-file-browser-status-chip--rose rounded-full px-3 py-1.5 text-xs transition disabled:opacity-60"
              >
                {{ tag.name }} ×
              </button>
              <div v-if="!(currentViewerPhoto?.tags || []).length" class="admin-file-browser-dialog-subhelp text-xs">
                这张图片还没有标签。
              </div>
            </div>
          </div>

          <div>
            <div class="text-sm admin-file-browser-dialog-label">添加标签</div>
            <input
              v-model="photoTagKeyword"
              @keyup.enter="addPhotoTagByName(photoTagKeyword)"
              placeholder="输入标签名称，回车添加"
              class="admin-file-browser-dialog-input mt-3 w-full rounded-2xl px-4 py-3 text-sm outline-none transition"
            />
            <div class="mt-3 flex flex-wrap gap-2 max-h-48 overflow-auto">
              <button
                v-for="tag in filteredPhotoTags"
                :key="`candidate-tag-${tag.id}`"
                @click="addPhotoTagByName(tag.name)"
                :disabled="savingPhotoTag"
                class="admin-file-browser-dialog-chip rounded-full px-3 py-1.5 text-xs transition disabled:opacity-60"
              >
                {{ tag.name }}
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch, nextTick } from 'vue'
import { api, getEffectiveAuthToken, type UploadPrecheckResponse } from '@/api'
import { useAuthStore } from '@/stores/auth'
import type { Photo as ViewerPhoto } from '@/stores/photo'
import { buildPhotoAssetUrl } from '@/utils/photoUrl'
import { buildPublicPath } from '@/utils/publicRoute'
import { storageTypeLabel } from '@/utils/providerLabels'
import PhotoViewer from '@/components/PhotoViewer.vue'
import { useAdminFeedback } from '@/composables/useAdminFeedback'

interface PhotoInfo {
  id?: number
  originalPath?: string
  thumbnailPath?: string
  smallThumbPath?: string
  webpPath?: string
  width?: number
  height?: number
  isHidden?: boolean
  tags?: Array<{ id: number; name: string; color?: string }>
}

interface FileItem {
  name: string
  path: string
  isDirectory?: boolean
  size?: number
  lastModified?: number
  photoCount?: number
  albumBound?: boolean
  albumId?: number | null
  albumHidden?: boolean
  albumAggregateSubAlbums?: boolean
  albumHasCustomCover?: boolean
  albumDescription?: string | null
  albumPhotoSortOrder?: string | null
  albumDownloadAllowed?: boolean | null
  photoHidden?: boolean
  leftVertical?: PhotoInfo
  rightTop?: PhotoInfo
  rightBottom?: PhotoInfo
  thumbnail?: PhotoInfo
}

interface DirectoryAlbumMeta {
  albumBound: boolean
  albumId?: number | null
  albumHidden?: boolean
  albumAggregateSubAlbums?: boolean
  albumHasCustomCover?: boolean
  albumDescription?: string | null
  albumPhotoSortOrder?: string | null
  albumDownloadAllowed?: boolean | null
}

interface BrowserViewerPhoto extends ViewerPhoto {
  browserPath: string
}

interface DeletePreviewPhotoItem {
  photoId?: number
  filename: string
  path: string
  tags: string[]
  directHit?: boolean
}

interface DeletePreviewEntry {
  path: string
  name: string
  isDirectory: boolean
  photoCount: number
  photos: DeletePreviewPhotoItem[]
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
const { notify } = useAdminFeedback()
const alert = (message: unknown) => {
  const text = String(message).replace(/^[✅❌]\s*/, '')
  notify(text, /失败|错误|不能|未获取|不支持|无效/.test(text) ? 'error' : 'info')
}
const basePath = ref('')
const currentPath = ref('')
const breadcrumbTrailPath = ref<string | null>(null)
const parentPath = ref<string | null>(null)
const selectedProviderId = ref<number | null>(null)
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
const dragMode = ref<'upload' | 'move'>('upload')
const dragMoveTargetPath = ref('')
const draggingPaths = ref<string[]>([])
const bindingAlbumPath = ref('')
const currentDirectoryAlbum = ref<DirectoryAlbumMeta>({
  albumBound: false,
  albumId: null,
  albumHidden: false,
  albumAggregateSubAlbums: false,
  albumHasCustomCover: false,
  albumDescription: '',
  albumPhotoSortOrder: null,
  albumDownloadAllowed: null
})
const viewerVisible = ref(false)
const viewerIndex = ref(0)
let dragLeaveTimer: ReturnType<typeof setTimeout> | null = null

const showCreateDialog = ref(false)
const newFolderName = ref('')
const creating = ref(false)
const newFolderInput = ref<HTMLInputElement | null>(null)

const showRenameDialog = ref(false)
const renameValue = ref('')
const renaming = ref(false)
const itemToRename = ref<FileItem | null>(null)
const showMoveDialog = ref(false)
const movePendingPaths = ref<string[]>([])
const moveTargetInput = ref('')
const movingItems = ref(false)
const moveTargetInputRef = ref<HTMLInputElement | null>(null)
const showDeleteDialog = ref(false)
const loadingDeletePreview = ref(false)
const deletingItems = ref(false)
const deletePreviewError = ref('')
const deletePreviewEntries = ref<DeletePreviewEntry[]>([])
const deletePendingPaths = ref<string[]>([])
const showAlbumSettingsDrawer = ref(false)
const albumSettingsTarget = ref<FileItem | null>(null)
const savingAlbumSettings = ref(false)
const showAlbumCoverDialog = ref(false)
const selectedCoverIds = ref<number[]>([])
const showPhotoTagDialog = ref(false)
const allTags = ref<Array<{ id: number; name: string; color?: string }>>([])
const photoTagKeyword = ref('')
const savingPhotoTag = ref(false)
const albumSettingsForm = ref({
  description: '',
  isHidden: false,
  aggregateSubAlbums: false,
  photoSortOrder: '',
  downloadAllowed: ''
})

const contextMenu = ref({
  show: false,
  x: 0,
  y: 0,
  item: null as FileItem | null
})
const sortMenu = ref({
  show: false,
  x: 0,
  y: 0
})
const providerMenu = ref({
  show: false,
  x: 0,
  y: 0
})
const viewMenu = ref({
  show: false,
  x: 0,
  y: 0
})
const pageSizeMenu = ref({
  show: false,
  x: 0,
  y: 0
})
const actionsMenu = ref({ show: false, x: 0, y: 0 })
const breadcrumbMenu = ref({
  show: false,
  x: 0,
  y: 0,
  path: '',
  pathLabel: '',
  loading: false,
  items: [] as FileItem[]
})

const fileInput = ref<HTMLInputElement | null>(null)
const dirInput = ref<HTMLInputElement | null>(null)
const providerTriggerRef = ref<HTMLElement | null>(null)
const actionsTriggerRef = ref<HTMLElement | null>(null)
const viewTriggerRef = ref<HTMLElement | null>(null)
const sortTriggerRef = ref<HTMLElement | null>(null)
const pageSizeTriggerRef = ref<HTMLElement | null>(null)
const sortMode = ref<'name-asc' | 'name-desc' | 'lastModified-desc' | 'lastModified-asc' | 'size-desc' | 'size-asc' | 'photoCount-desc' | 'photoCount-asc'>('name-asc')
const typeOrderMode = ref<'mixed' | 'directory-first' | 'file-first'>('mixed')
const searchKeyword = ref('')
const pageSize = ref(48)
const currentPage = ref(1)
const viewMode = ref<'grid' | 'list'>('grid')
const gridPreset = ref<'auto-sm' | 'auto-md' | 'auto-lg' | 'cols-2' | 'cols-3' | 'cols-4' | 'cols-5'>('auto-md')
let fileBrowserPreferenceSaveTimer: ReturnType<typeof setTimeout> | null = null
const pageSizeOptions = [24, 48, 96, 200] as const
const viewModeOptions = [
  { value: 'grid', label: '网格' },
  { value: 'list', label: '列表' }
] as const
const gridPresetOptions = [
  { value: 'auto-sm', label: '自动小' },
  { value: 'auto-md', label: '自动中' },
  { value: 'auto-lg', label: '自动大' },
  { value: 'cols-2', label: '2 列' },
  { value: 'cols-3', label: '3 列' },
  { value: 'cols-4', label: '4 列' },
  { value: 'cols-5', label: '5 列' }
] as const
const sortModeOptions = [
  { value: 'name-asc', label: '名称 A-Z' },
  { value: 'name-desc', label: '名称 Z-A' },
  { value: 'lastModified-desc', label: '时间 新-旧' },
  { value: 'lastModified-asc', label: '时间 旧-新' },
  { value: 'size-desc', label: '大小 大-小' },
  { value: 'size-asc', label: '大小 小-大' },
  { value: 'photoCount-desc', label: '图片数 多-少' },
  { value: 'photoCount-asc', label: '图片数 少-多' }
] as const
const typeOrderOptions = [
  { value: 'mixed', label: '混合' },
  { value: 'directory-first', label: '目录优先' },
  { value: 'file-first', label: '文件优先' }
] as const

const selectedStorageProvider = computed(() => (
  availableStorageProviders.value.find(provider => provider.id === selectedProviderId.value) || null
))
const selectedProviderName = computed(() => selectedStorageProvider.value?.name || '')
const canSelectStorageProvider = computed(() => authStore.role === 'SUPER_ADMIN')
const providerSummaryLabel = computed(() => (
  selectedStorageProvider.value
    ? `${selectedStorageProvider.value.name}`
    : '选择存储提供者'
))
const sortSummaryLabel = computed(() => {
  const sortLabel = sortModeOptions.find(option => option.value === sortMode.value)?.label || '名称 A-Z'
  const typeLabel = typeOrderOptions.find(option => option.value === typeOrderMode.value)?.label || '混合'
  return `${sortLabel} · ${typeLabel}`
})
const viewSummaryLabel = computed(() => {
  const modeLabel = viewModeOptions.find(option => option.value === viewMode.value)?.label || '网格'
  const presetLabel = gridPresetOptions.find(option => option.value === gridPreset.value)?.label || '自动中'
  return viewMode.value === 'list' ? `${modeLabel} · ${presetLabel}` : `${modeLabel} · ${presetLabel}`
})
const filteredItems = computed(() => {
  const keyword = searchKeyword.value.trim().toLocaleLowerCase('zh-CN')
  return items.value.filter(item => {
    if (keyword && !item.name.toLocaleLowerCase('zh-CN').includes(keyword)) return false
    return true
  })
})
const directories = computed(() => filteredItems.value.filter(item => item.isDirectory))
const files = computed(() => filteredItems.value.filter(item => !item.isDirectory))
const sortedItems = computed(() => {
  const [sortKey, sortOrder] = sortMode.value.split('-') as ['name' | 'lastModified' | 'size' | 'photoCount', 'asc' | 'desc']
  const factor = sortOrder === 'asc' ? 1 : -1
  const typeRank = (item: FileItem) => item.isDirectory ? 0 : 1
  const compareType = (a: FileItem, b: FileItem) => {
    if (typeOrderMode.value === 'mixed') {
      return 0
    }
    const result = typeRank(a) - typeRank(b)
    return typeOrderMode.value === 'directory-first' ? result : -result
  }
  return [...filteredItems.value].sort((a, b) => {
    const typeResult = compareType(a, b)
    if (typeResult !== 0) return typeResult
    let result = 0
    if (sortKey === 'name') {
      result = a.name.localeCompare(b.name, 'zh-CN', { numeric: true, sensitivity: 'base' })
    } else if (sortKey === 'lastModified') {
      result = (a.lastModified || 0) - (b.lastModified || 0)
    } else if (sortKey === 'size') {
      result = (a.size || 0) - (b.size || 0)
    } else if (sortKey === 'photoCount') {
      result = (a.photoCount || 0) - (b.photoCount || 0)
    }
    if (result === 0) {
      result = a.name.localeCompare(b.name, 'zh-CN', { numeric: true, sensitivity: 'base' })
    }
    return result * factor
  })
})
const totalPages = computed(() => Math.max(1, Math.ceil(sortedItems.value.length / pageSize.value)))
const paginatedItems = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return sortedItems.value.slice(start, start + pageSize.value)
})
const paginatedDirectories = computed(() => paginatedItems.value.filter(item => item.isDirectory))
const paginatedFiles = computed(() => paginatedItems.value.filter(item => !item.isDirectory))
const gridClass = computed(() => {
  if (viewMode.value === 'list') {
    return 'grid grid-cols-1 gap-1'
  }
  if (gridPreset.value === 'auto-sm') {
    return 'grid gap-2 admin-file-browser-grid--auto-sm'
  }
  if (gridPreset.value === 'auto-md') {
    return 'grid gap-2.5 admin-file-browser-grid--auto-md'
  }
  if (gridPreset.value === 'auto-lg') {
    return 'grid gap-3 admin-file-browser-grid--auto-lg'
  }
  if (gridPreset.value === 'cols-2') {
    return 'grid gap-2.5 admin-file-browser-grid--cols-2'
  }
  if (gridPreset.value === 'cols-3') {
    return 'grid gap-2.5 admin-file-browser-grid--cols-3'
  }
  if (gridPreset.value === 'cols-4') {
    return 'grid gap-2.5 admin-file-browser-grid--cols-4'
  }
  return 'grid gap-2.5 admin-file-browser-grid--cols-5'
})
const selectedDirectoryItems = computed(() => directories.value.filter(item => selectedPaths.value.has(item.path)))
const selectedFileItems = computed(() => files.value.filter(item => selectedPaths.value.has(item.path)))
const unboundSelectedDirectories = computed(() => selectedDirectoryItems.value.filter(item => !item.albumBound))
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
const deletePreviewSummary = computed(() => {
  const photoCount = deletePreviewEntries.value.reduce((sum, entry) => sum + (entry.photos?.length || 0), 0)
  return `${deletePendingPaths.value.length} 项，${photoCount} 张图片及其关联资源`
})
const albumSettingsDirty = computed(() => {
  const target = albumSettingsTarget.value
  if (!target) return false
  return (
    (albumSettingsForm.value.description || '') !== (target.albumDescription || '') ||
    !!albumSettingsForm.value.isHidden !== !!target.albumHidden ||
    !!albumSettingsForm.value.aggregateSubAlbums !== !!target.albumAggregateSubAlbums ||
    (albumSettingsForm.value.photoSortOrder || '') !== (target.albumPhotoSortOrder || '') ||
    albumSettingsForm.value.downloadAllowed !== downloadAllowedFormValue(target.albumDownloadAllowed)
  )
})
const imageExtensions = ['.jpg', '.jpeg', '.png', '.webp', '.gif', '.bmp', '.heic', '.heif', '.avif', '.raw', '.cr2', '.nef', '.arw']
const browserViewerPhotos = computed<BrowserViewerPhoto[]>(() => {
  return files.value
    .filter(file => isImageFile(file) && canOpenInViewer(file))
    .map((file, index) => {
      const photoMeta = file.thumbnail || {}
      return {
        id: photoMeta.id || -1 * (index + 1),
        albumId: currentDirectoryAlbum.value.albumId || 0,
        filename: file.name,
        originalPath: photoMeta.originalPath || (selectedStorageProvider.value?.type === 'LOCAL' ? file.path : undefined),
        thumbnailPath: photoMeta.thumbnailPath,
        webpPath: photoMeta.webpPath,
        width: photoMeta.width,
        height: photoMeta.height,
        fileSize: file.size,
        isHidden: !!file.photoHidden,
        tags: photoMeta.tags || [],
        viewCount: 0,
        likeCount: 0,
        isFeatured: false,
        createdAt: file.lastModified ? new Date(file.lastModified).toISOString() : '',
        browserPath: file.path
      }
    })
})
const currentViewerPhoto = computed(() => browserViewerPhotos.value[viewerIndex.value] || null)
const filteredPhotoTags = computed(() => {
  const keyword = photoTagKeyword.value.trim().toLowerCase()
  if (!keyword) return allTags.value
  return allTags.value.filter(tag => tag.name.toLowerCase().includes(keyword))
})
const currentDirectoryDirectImageCount = computed(() => items.value.filter(file => !file.isDirectory && isImageFile(file)).length)
const currentDirectoryAggregatedChildPhotoCount = computed(() => {
  return items.value
    .filter(item => item.isDirectory)
    .reduce((sum, dir) => sum + (dir.photoCount || 0), 0)
})
const currentDirectoryState = computed(() => {
  const album = currentDirectoryAlbum.value
  const directCount = currentDirectoryDirectImageCount.value
  const childCount = currentDirectoryAggregatedChildPhotoCount.value
  if (!album.albumBound) {
    return {
      label: directCount > 0 ? '目录内有图片，建议绑定相册' : '普通目录',
      tone: 'neutral' as const,
      detail: directCount > 0 ? `当前目录有 ${directCount} 张直接图片，但还没有绑定相册。` : '当前目录仅作为文件夹使用。'
    }
  }
  if (directCount > 0) {
    return {
      label: '可展示相册',
      tone: 'active' as const,
      detail: `当前目录有 ${directCount} 张直接图片，可直接作为相册展示。`
    }
  }
  if (album.albumAggregateSubAlbums && childCount > 0) {
    return {
      label: '聚合相册',
      tone: 'aggregate' as const,
      detail: `当前目录没有直接图片，但会聚合下级目录中的 ${childCount} 张图片。`
    }
  }
  return {
    label: '空相册',
    tone: 'empty' as const,
    detail: '当前目录已绑定相册，但暂时没有可展示图片。'
  }
})
const viewerAdminActions = computed(() => {
  const actions: Array<{ key: string; label: string; tone?: 'default' | 'danger' }> = []
  if (currentViewerPhoto.value) {
    actions.push({ key: 'download', label: '下载原图' })
  }
  if (currentViewerPhoto.value?.id && currentViewerPhoto.value.id > 0) {
    actions.push({ key: 'manage-tags', label: '标签管理' })
  }
  if (currentViewerPhoto.value?.id && currentViewerPhoto.value.id > 0) {
    actions.push({
      key: 'toggle-hidden',
      label: currentViewerPhoto.value.isHidden ? '恢复公开显示' : '隐藏图片'
    })
  }
  if (currentDirectoryAlbum.value.albumBound && currentDirectoryAlbum.value.albumId && currentViewerPhoto.value?.id && currentViewerPhoto.value.id > 0) {
    actions.push({ key: 'set-cover', label: '设为相册封面' })
  }
  if (currentViewerPhoto.value?.id && currentViewerPhoto.value.id > 0) {
    actions.push({ key: 'rescan-faces', label: '重建人脸' })
  }
  if (currentViewerPhoto.value) {
    actions.push({ key: 'delete', label: '删除图片', tone: 'danger' })
  }
  return actions
})
const currentRelativePath = computed(() => {
  const normalizedBase = normalizePath(basePath.value || '/').replace(/[\/\\]+$/, '')
  const normalizedCurrent = normalizePath(currentPath.value || '/')
  if (!normalizedCurrent || normalizedCurrent === normalizedBase) {
    return ''
  }
  return normalizedCurrent.startsWith(normalizedBase)
    ? normalizedCurrent.slice(normalizedBase.length).replace(/^[\/\\]+/, '')
    : normalizedCurrent.replace(/^[\/\\]+/, '')
})
const rootPathSummary = computed(() => currentPath.value || basePath.value || '/')
const isAtRoot = computed(() => {
  if (!currentPath.value || !basePath.value) return true
  return normalizePath(currentPath.value) === normalizePath(basePath.value)
})

const normalizePath = (p: string | null | undefined) => {
  if (!p) return ''
  return p.replace(/\\/g, '/')
}

const isImageFile = (file: FileItem) => {
  const lower = (file.name || '').toLowerCase()
  return imageExtensions.some(ext => lower.endsWith(ext))
}

const canOpenInViewer = (file: FileItem) => {
  if (file.thumbnail?.id) return true
  return selectedStorageProvider.value?.type === 'LOCAL'
}

const directoryStateLabel = (dir: FileItem) => {
  if (!dir.albumBound) {
    return (dir.photoCount || 0) > 0 ? '建议绑定相册' : '普通目录'
  }
  if ((dir.photoCount || 0) > 0) {
    return dir.albumAggregateSubAlbums ? '聚合相册' : '可展示相册'
  }
  if (dir.albumAggregateSubAlbums) {
    return '聚合相册'
  }
  return '空相册'
}

const directoryStateClass = (dir: FileItem) => {
  const label = directoryStateLabel(dir)
  if (label === '可展示相册') {
    return 'border-emerald-400/30 bg-emerald-500/10 text-emerald-100'
  }
  if (label === '聚合相册') {
    return 'border-amber-400/30 bg-amber-500/10 text-amber-100'
  }
  if (label === '空相册') {
    return 'border-slate-400/20 bg-slate-500/10 text-slate-200'
  }
  return 'admin-file-browser-state-chip--default'
}

const directoryStateMutedClass = (dir: FileItem) => {
  const label = directoryStateLabel(dir)
  if (label === '可展示相册') {
    return 'text-emerald-300'
  }
  if (label === '聚合相册') {
    return 'text-amber-300'
  }
  if (label === '空相册') {
    return 'text-slate-300'
  }
  return 'admin-file-browser-state-muted'
}

function downloadAllowedFormValue(value: boolean | null | undefined) {
  if (value === true) return 'true'
  if (value === false) return 'false'
  return ''
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

const isSameOrAncestorPath = (target: string, descendant: string) => {
  const normalizedTarget = normalizePath(target)
  const normalizedDescendant = normalizePath(descendant)
  if (!normalizedTarget || !normalizedDescendant) return false
  if (normalizedTarget === normalizedDescendant) return true
  if (normalizedTarget === '/') {
    return normalizedDescendant.startsWith('/')
  }
  const targetWithSlash = normalizedTarget.endsWith('/') ? normalizedTarget : `${normalizedTarget}/`
  return normalizedDescendant.startsWith(targetWithSlash)
}

const pathParts = computed(() => {
  if (!currentPath.value || currentPath.value === basePath.value) return []
  const cur = normalizePath(currentPath.value)
  const base = normalizePath(basePath.value)
  const relative = cur.startsWith(base) ? cur.slice(base.length).replace(/^[\/\\]+/, '') : cur
  return relative.split(/[\/\\]+/).filter(p => p)
})

const breadcrumbSegments = computed(() => {
  const activeParts = pathParts.value
  const trailSource = breadcrumbTrailPath.value && isUnderBase(breadcrumbTrailPath.value)
    ? normalizePath(breadcrumbTrailPath.value)
    : normalizePath(currentPath.value)
  const base = normalizePath(basePath.value)
  const relative = trailSource.startsWith(base) ? trailSource.slice(base.length).replace(/^[\/\\]+/, '') : trailSource
  const parts = relative ? relative.split(/[\/\\]+/).filter(Boolean) : []
  return parts.map((label, index) => {
    const activePath = index < activeParts.length
      ? buildPathFromParts(parts.slice(0, index + 1))
      : buildPathFromParts(parts.slice(0, index + 1))
    return {
      label,
      path: activePath,
      isGhost: index >= activeParts.length
    }
  })
})

const applyStorageContext = (data: any, resetCurrentPath = false) => {
  if (!data) return
  const nextBasePath = data.basePath || basePath.value || ''
  basePath.value = nextBasePath
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
    currentDirectoryAlbum.value = {
      albumBound: !!data.currentDirectoryAlbum?.albumBound,
      albumId: data.currentDirectoryAlbum?.albumId ?? null,
      albumHidden: !!data.currentDirectoryAlbum?.albumHidden,
      albumAggregateSubAlbums: !!data.currentDirectoryAlbum?.albumAggregateSubAlbums,
      albumHasCustomCover: !!data.currentDirectoryAlbum?.albumHasCustomCover,
      albumDescription: data.currentDirectoryAlbum?.albumDescription ?? '',
      albumPhotoSortOrder: data.currentDirectoryAlbum?.albumPhotoSortOrder ?? '',
      albumDownloadAllowed: data.currentDirectoryAlbum?.albumDownloadAllowed ?? null
    }
    
    const dirs = (data.directories || []).map((d: any) => ({
      name: d.name,
      path: d.path,
      isDirectory: true,
      photoCount: d.photoCount || 0,
      albumBound: !!d.albumBound,
      albumId: d.albumId ?? null,
      albumHidden: !!d.albumHidden,
      albumAggregateSubAlbums: !!d.albumAggregateSubAlbums,
      albumHasCustomCover: !!d.albumHasCustomCover,
      albumDescription: d.albumDescription ?? '',
      albumPhotoSortOrder: d.albumPhotoSortOrder ?? '',
      albumDownloadAllowed: d.albumDownloadAllowed ?? null,
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
      photoHidden: !!f.thumbnail?.isHidden,
      thumbnail: f.thumbnail
    }))
    
    items.value = [...dirs, ...filesList]
    currentPage.value = 1
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
    currentPage.value = 1
    currentDirectoryAlbum.value = {
      albumBound: false,
      albumId: null,
      albumHidden: false,
      albumAggregateSubAlbums: false,
      albumHasCustomCover: false,
      albumDescription: '',
      albumPhotoSortOrder: null,
      albumDownloadAllowed: null
    }
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
  const normalizedTarget = normalizePath(target)
  const referenceTrail = breadcrumbTrailPath.value && isUnderBase(breadcrumbTrailPath.value)
    ? normalizePath(breadcrumbTrailPath.value)
    : normalizePath(currentPath.value)
  if (
    referenceTrail &&
    normalizedTarget !== referenceTrail &&
    isSameOrAncestorPath(normalizedTarget, referenceTrail)
  ) {
    breadcrumbTrailPath.value = referenceTrail
  } else {
    breadcrumbTrailPath.value = null
  }
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
  const path = buildPathFromParts(parts)
  goToPath(path)
}

const buildPathFromParts = (parts: string[]) => {
  const normalizedBase = normalizePath(basePath.value || '/').replace(/[\/\\]+$/, '')
  const joined = parts.join('/')
  if (!joined) return normalizedBase || '/'
  return normalizedBase && normalizedBase !== '/'
    ? `${normalizedBase}/${joined}`
    : `/${joined}`
}

const breadcrumbClass = (index: number, isGhost = false) => {
  if (dragMoveTargetPath.value && dragMoveTargetPath.value === (index < 0 ? basePath.value : breadcrumbSegments.value[index]?.path)) {
    return 'admin-file-browser-breadcrumb-chip admin-file-browser-breadcrumb-chip--drop'
  }
  if (isGhost) {
    return 'admin-file-browser-breadcrumb-chip admin-file-browser-breadcrumb-chip--ghost'
  }
  if ((index < 0 && !pathParts.value.length) || (!isGhost && index === pathParts.value.length - 1)) {
    return 'admin-file-browser-breadcrumb-chip admin-file-browser-breadcrumb-chip--current'
  }
  return 'admin-file-browser-breadcrumb-chip'
}

const toggleBreadcrumbMenu = async (event: MouseEvent, targetPath: string) => {
  event.preventDefault()
  event.stopPropagation()
  if (breadcrumbMenu.value.show && breadcrumbMenu.value.path === targetPath) {
    breadcrumbMenu.value.show = false
    return
  }
  breadcrumbMenu.value = {
    show: true,
    x: event.clientX - 12,
    y: event.clientY + 16,
    path: targetPath,
    pathLabel: normalizePath(targetPath) || rootButtonLabel.value,
    loading: true,
    items: []
  }
  try {
    const { data } = await api.get('/admin/folders/browser/list', {
      params: {
        path: targetPath,
        providerId: canSelectStorageProvider.value ? (selectedProviderId.value ?? undefined) : undefined
      }
    })
    breadcrumbMenu.value.items = [
      ...((data.directories || []).map((d: any) => ({
        name: d.name,
        path: d.path,
        isDirectory: true,
        photoCount: d.photoCount || 0
      }))),
      ...((data.files || []).map((f: any) => ({
        name: f.name,
        path: f.path,
        isDirectory: false,
        size: f.size,
        lastModified: f.lastModified,
        photoHidden: !!f.thumbnail?.isHidden,
        thumbnail: f.thumbnail
      })))
    ]
  } catch (_error) {
    breadcrumbMenu.value.items = []
  } finally {
    breadcrumbMenu.value.loading = false
  }
}

const openBreadcrumbMenuItem = (entry: FileItem) => {
  breadcrumbMenu.value.show = false
  if (entry.isDirectory) {
    goToPath(entry.path)
  } else {
    openFile(entry)
  }
}

const refresh = () => {
  if (loading.value) return
  selectedPaths.value.clear()
  loadFiles()
}

const prevPage = () => {
  if (currentPage.value > 1) {
    currentPage.value -= 1
  }
}

const nextPage = () => {
  if (currentPage.value < totalPages.value) {
    currentPage.value += 1
  }
}

const loadFileBrowserPreferences = async () => {
  try {
    const { data } = await api.get('/admin/config/file-browser-preferences')
    viewMode.value = data?.viewMode === 'list' ? 'list' : 'grid'
    gridPreset.value = ['auto-sm', 'auto-md', 'auto-lg', 'cols-2', 'cols-3', 'cols-4', 'cols-5'].includes(data?.gridPreset) ? data.gridPreset : 'auto-md'
    sortMode.value = ['name-asc', 'name-desc', 'lastModified-desc', 'lastModified-asc', 'size-desc', 'size-asc', 'photoCount-desc', 'photoCount-asc'].includes(data?.sortMode)
      ? data.sortMode
      : 'name-asc'
    typeOrderMode.value = ['mixed', 'directory-first', 'file-first'].includes(data?.typeOrderMode)
      ? data.typeOrderMode
      : 'mixed'
    pageSize.value = [24, 48, 96, 200].includes(Number(data?.pageSize)) ? Number(data.pageSize) : 48
  } catch (_error) {
  }
}

const persistFileBrowserPreferences = async () => {
  try {
    await api.put('/admin/config/file-browser-preferences', {
      viewMode: viewMode.value,
      gridPreset: gridPreset.value,
      sortMode: sortMode.value,
      typeOrderMode: typeOrderMode.value,
      pageSize: pageSize.value
    })
  } catch (_error) {
  }
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

const showItemMenu = (event: MouseEvent, item: FileItem) => {
  event.preventDefault()
  event.stopPropagation()
  showContextMenu(event, item)
}

const openCurrentDirectoryMenu = (event?: MouseEvent) => {
  const fallbackEvent = event || new MouseEvent('click', {
    clientX: window.innerWidth - 240,
    clientY: 140
  })
  showContextMenu(fallbackEvent, {
    name: pathParts.value[pathParts.value.length - 1] || rootButtonLabel.value,
    path: currentPath.value || basePath.value,
    isDirectory: true,
    albumBound: currentDirectoryAlbum.value.albumBound,
    albumId: currentDirectoryAlbum.value.albumId,
    albumHidden: currentDirectoryAlbum.value.albumHidden,
    albumAggregateSubAlbums: currentDirectoryAlbum.value.albumAggregateSubAlbums,
    albumHasCustomCover: currentDirectoryAlbum.value.albumHasCustomCover,
    albumDescription: currentDirectoryAlbum.value.albumDescription,
    albumPhotoSortOrder: currentDirectoryAlbum.value.albumPhotoSortOrder,
    albumDownloadAllowed: currentDirectoryAlbum.value.albumDownloadAllowed
  })
}

const startRename = () => {
  if (!supportsItemManagement.value) return
  if (!contextMenu.value.item) return
  itemToRename.value = contextMenu.value.item
  renameValue.value = contextMenu.value.item.name
  showRenameDialog.value = true
  contextMenu.value.show = false
}

const bindContextDirectory = async () => {
  if (!contextMenu.value.item?.isDirectory) return
  contextMenu.value.show = false
  await bindAlbum(contextMenu.value.item)
  await loadFiles(currentPath.value, false)
}

const openContextAlbumSettings = () => {
  if (!contextMenu.value.item?.isDirectory) return
  contextMenu.value.show = false
  if (contextMenu.value.item.path === currentPath.value) {
    openCurrentDirectoryAlbumSettings()
    return
  }
  openAlbumSettings(contextMenu.value.item)
}

const clearSelection = () => {
  selectedPaths.value.clear()
}

const selectByType = (type: 'directory' | 'file') => {
  if (!multiSelect.value) return
  const targetItems = type === 'directory' ? directories.value : files.value
  selectedPaths.value = new Set(targetItems.map(item => item.path))
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

const openMoveDialog = (paths: string[]) => {
  const uniquePaths = Array.from(new Set(paths.filter(Boolean)))
  if (!uniquePaths.length) return
  movePendingPaths.value = uniquePaths
  moveTargetInput.value = currentRelativePath.value || ''
  showMoveDialog.value = true
}

const closeMoveDialog = () => {
  if (movingItems.value) return
  showMoveDialog.value = false
  movePendingPaths.value = []
  moveTargetInput.value = ''
}

const closeDeleteDialog = () => {
  if (loadingDeletePreview.value || deletingItems.value) return
  showDeleteDialog.value = false
  deletePreviewError.value = ''
  deletePreviewEntries.value = []
  deletePendingPaths.value = []
}

const openDeleteDialog = async (paths: string[]) => {
  const uniquePaths = Array.from(new Set(paths.filter(Boolean)))
  if (!uniquePaths.length) return

  contextMenu.value.show = false
  selectedItem.value = null
  showDeleteDialog.value = true
  loadingDeletePreview.value = true
  deletingItems.value = false
  deletePreviewError.value = ''
  deletePreviewEntries.value = []
  deletePendingPaths.value = uniquePaths

  try {
    const { data } = await api.post('/admin/folders/browser/delete-preview', {
      paths: uniquePaths
    }, {
      params: {
        providerId: canSelectStorageProvider.value ? (selectedProviderId.value ?? undefined) : undefined
      }
    })
    deletePreviewEntries.value = Array.isArray(data?.entries) ? data.entries : []
  } catch (e: any) {
    deletePreviewError.value = e.response?.data?.error || e.message || '生成删除清单失败'
  } finally {
    loadingDeletePreview.value = false
  }
}

const confirmDelete = () => {
  if (!supportsItemManagement.value) return
  if (!contextMenu.value.item) return
  openDeleteDialog([contextMenu.value.item.path])
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
  if (isImageFile(file) && openPhotoViewer(file)) {
    return
  }
  if (!supportsPreview.value) {
    alert(selectedStorageProvider.value?.supportMessage || '当前存储位置暂未接通文件预览，请先使用已支持预览的存储查看文件内容。')
    return
  }
  const localPreviewUrl = getImageUrl(file.thumbnail || { originalPath: file.path })
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
  if (localPreviewUrl) {
    window.open(localPreviewUrl, '_blank')
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

const bindAlbum = async (dir: FileItem) => {
  if (!dir?.isDirectory || bindingAlbumPath.value) return
  bindingAlbumPath.value = dir.path
  try {
    const { data } = await api.post('/admin/folders/browser/bind-album', null, {
      params: {
        path: dir.path,
        providerId: canSelectStorageProvider.value ? (selectedProviderId.value ?? undefined) : undefined
      }
    })
    dir.albumBound = true
    dir.albumId = data?.albumId ?? dir.albumId ?? null
    dir.albumHidden = !!data?.albumHidden
    dir.albumAggregateSubAlbums = !!data?.albumAggregateSubAlbums
    dir.albumHasCustomCover = !!data?.albumHasCustomCover
    dir.albumDescription = data?.albumDescription ?? ''
    if (dir.path === currentPath.value) {
      currentDirectoryAlbum.value = {
        albumBound: true,
        albumId: data?.albumId ?? null,
        albumHidden: !!data?.albumHidden,
        albumAggregateSubAlbums: !!data?.albumAggregateSubAlbums,
        albumHasCustomCover: !!data?.albumHasCustomCover,
        albumDescription: data?.albumDescription ?? ''
      }
    }
  } catch (e: any) {
    alert('绑定相册失败: ' + (e.response?.data?.error || e.message))
  } finally {
    bindingAlbumPath.value = ''
  }
}

const bindCurrentDirectoryAlbum = async () => {
  if (!currentPath.value || bindingAlbumPath.value) return
  await bindAlbum({
    name: pathParts.value[pathParts.value.length - 1] || rootButtonLabel.value,
    path: currentPath.value,
    isDirectory: true,
    albumBound: currentDirectoryAlbum.value.albumBound,
    albumId: currentDirectoryAlbum.value.albumId,
    albumHidden: currentDirectoryAlbum.value.albumHidden,
    albumAggregateSubAlbums: currentDirectoryAlbum.value.albumAggregateSubAlbums,
    albumHasCustomCover: currentDirectoryAlbum.value.albumHasCustomCover,
    albumDescription: currentDirectoryAlbum.value.albumDescription
  })
  await loadFiles(currentPath.value, false)
}

const loadAllTags = async () => {
  const { data } = await api.get('/tags')
  allTags.value = Array.isArray(data) ? data : (data?.content || [])
}

const openPhotoViewer = (file: FileItem) => {
  const index = browserViewerPhotos.value.findIndex(photo => photo.browserPath === file.path)
  if (index < 0) return false
  viewerIndex.value = index
  viewerVisible.value = true
  return true
}

const openPhotoTagDialog = async () => {
  if (!currentViewerPhoto.value?.id || currentViewerPhoto.value.id <= 0) {
    alert('当前图片还没有可用的照片记录，暂时无法管理标签')
    return
  }
  await loadAllTags()
  photoTagKeyword.value = ''
  showPhotoTagDialog.value = true
}

const openContextPhotoTagDialog = async () => {
  const item = contextMenu.value.item
  if (!item || item.isDirectory) return
  contextMenu.value.show = false
  if (openPhotoViewer(item)) {
    await openPhotoTagDialog()
  }
}

const closePhotoTagDialog = () => {
  if (savingPhotoTag.value) return
  showPhotoTagDialog.value = false
  photoTagKeyword.value = ''
}

const syncCurrentViewerPhotoTags = (tags: Array<{ id: number; name: string; color?: string }>) => {
  if (!currentViewerPhoto.value) return
  currentViewerPhoto.value.tags = tags
  const matchedFile = files.value.find(file => file.path === currentViewerPhoto.value?.browserPath)
  if (matchedFile?.thumbnail) {
    matchedFile.thumbnail.tags = tags
  }
}

const addPhotoTagByName = async (tagName: string) => {
  const photo = currentViewerPhoto.value
  if (!photo?.id || photo.id <= 0) return
  const normalizedName = tagName.trim()
  if (!normalizedName) return
  savingPhotoTag.value = true
  try {
    const { data } = await api.post(`/admin/photos/${photo.id}/tags`, { name: normalizedName })
    syncCurrentViewerPhotoTags(data?.tags || [])
    await loadAllTags()
    photoTagKeyword.value = ''
  } catch (e: any) {
    alert('添加图片标签失败: ' + (e.response?.data?.message || e.response?.data?.error || e.message))
  } finally {
    savingPhotoTag.value = false
  }
}

const removeCurrentPhotoTag = async (tagId: number) => {
  const photo = currentViewerPhoto.value
  if (!photo?.id || photo.id <= 0) return
  savingPhotoTag.value = true
  try {
    const { data } = await api.delete(`/admin/photos/${photo.id}/tags/${tagId}`)
    syncCurrentViewerPhotoTags(data?.tags || [])
  } catch (e: any) {
    alert('移除图片标签失败: ' + (e.response?.data?.message || e.response?.data?.error || e.message))
  } finally {
    savingPhotoTag.value = false
  }
}

const openAlbumSettings = (dir: FileItem) => {
  if (!dir?.albumBound || !dir.albumId) return
  albumSettingsTarget.value = dir
  albumSettingsForm.value = {
    description: dir.albumDescription || '',
    isHidden: !!dir.albumHidden,
    aggregateSubAlbums: !!dir.albumAggregateSubAlbums,
    photoSortOrder: dir.albumPhotoSortOrder || '',
    downloadAllowed: downloadAllowedFormValue(dir.albumDownloadAllowed)
  }
  showAlbumSettingsDrawer.value = true
}

const openCurrentDirectoryAlbumSettings = () => {
  if (isAtRoot.value) {
    alert('根目录不支持目录设置')
    return
  }
  if (!currentDirectoryAlbum.value.albumBound || !currentDirectoryAlbum.value.albumId) {
    alert('当前目录尚未绑定相册，请先使用“绑定为相册”')
    return
  }
  openAlbumSettings({
    name: pathParts.value[pathParts.value.length - 1] || rootButtonLabel.value,
    path: currentPath.value || '/',
    isDirectory: true,
    albumBound: currentDirectoryAlbum.value.albumBound,
    albumId: currentDirectoryAlbum.value.albumId,
    albumHidden: currentDirectoryAlbum.value.albumHidden,
    albumAggregateSubAlbums: currentDirectoryAlbum.value.albumAggregateSubAlbums,
    albumHasCustomCover: currentDirectoryAlbum.value.albumHasCustomCover,
    albumDescription: currentDirectoryAlbum.value.albumDescription
  })
}

const closeAlbumSettingsDrawer = () => {
  if (savingAlbumSettings.value) return
  showAlbumSettingsDrawer.value = false
  albumSettingsTarget.value = null
}

const openAlbumCoverDialog = () => {
  if (!albumSettingsTarget.value?.albumId) return
  selectedCoverIds.value = []
  showAlbumCoverDialog.value = true
}

const closeAlbumCoverDialog = () => {
  if (savingAlbumSettings.value) return
  showAlbumCoverDialog.value = false
}

const toggleCoverSelection = (photo: BrowserViewerPhoto) => {
  if (!photo?.id || photo.id <= 0) return
  const current = [...selectedCoverIds.value]
  const index = current.indexOf(photo.id)
  if (index >= 0) {
    current.splice(index, 1)
  } else {
    if (current.length >= 4) return
    current.push(photo.id)
  }
  selectedCoverIds.value = current
}

const coverIndexLabel = (photoId: number) => {
  const index = selectedCoverIds.value.indexOf(photoId)
  return index >= 0 ? `封面 ${index + 1}` : '未选择'
}

const saveAlbumSettings = async () => {
  const target = albumSettingsTarget.value
  if (!target?.albumId || savingAlbumSettings.value || !albumSettingsDirty.value) return
  savingAlbumSettings.value = true
  try {
    await api.put(`/albums/${target.albumId}`, {
      description: albumSettingsForm.value.description
    })
    await api.put(`/albums/${target.albumId}/hidden`, {
      isHidden: !!albumSettingsForm.value.isHidden
    })
    await api.put(`/albums/${target.albumId}/aggregate-sub-albums`, {
      aggregateSubAlbums: !!albumSettingsForm.value.aggregateSubAlbums
    })
    await api.put(`/albums/${target.albumId}/photo-sort-order`, {
      photoSortOrder: albumSettingsForm.value.photoSortOrder || null
    })
    await api.put(`/albums/${target.albumId}/download-allowed`, {
      downloadAllowed: albumSettingsForm.value.downloadAllowed === '' ? null : albumSettingsForm.value.downloadAllowed === 'true'
    })

    target.albumDescription = albumSettingsForm.value.description
    target.albumHidden = !!albumSettingsForm.value.isHidden
    target.albumAggregateSubAlbums = !!albumSettingsForm.value.aggregateSubAlbums
    target.albumPhotoSortOrder = albumSettingsForm.value.photoSortOrder || null
    target.albumDownloadAllowed = albumSettingsForm.value.downloadAllowed === '' ? null : albumSettingsForm.value.downloadAllowed === 'true'
    if (currentDirectoryAlbum.value.albumId === target.albumId) {
      currentDirectoryAlbum.value.albumDescription = target.albumDescription
      currentDirectoryAlbum.value.albumHidden = target.albumHidden
      currentDirectoryAlbum.value.albumAggregateSubAlbums = target.albumAggregateSubAlbums
      currentDirectoryAlbum.value.albumPhotoSortOrder = target.albumPhotoSortOrder
      currentDirectoryAlbum.value.albumDownloadAllowed = target.albumDownloadAllowed
    }
    closeAlbumSettingsDrawer()
  } catch (e: any) {
    alert('保存相册设置失败: ' + (e.response?.data?.error || e.message))
  } finally {
    savingAlbumSettings.value = false
  }
}

const saveAlbumCoverSelection = async () => {
  const target = albumSettingsTarget.value
  if (!target?.albumId || savingAlbumSettings.value) return
  savingAlbumSettings.value = true
  try {
    await api.put(`/albums/${target.albumId}/cover`, {
      coverImageIds: [...selectedCoverIds.value]
    })
    target.albumHasCustomCover = selectedCoverIds.value.length > 0
    if (currentDirectoryAlbum.value.albumId === target.albumId) {
      currentDirectoryAlbum.value.albumHasCustomCover = target.albumHasCustomCover
    }
    closeAlbumCoverDialog()
  } catch (e: any) {
    alert('保存相册封面失败: ' + (e.response?.data?.error || e.message))
  } finally {
    savingAlbumSettings.value = false
  }
}

const handleViewerIndexChange = ({ index }: { index: number }) => {
  viewerIndex.value = index
}

const handleViewerAdminAction = async ({ key }: { key: string }) => {
  const photo = currentViewerPhoto.value
  if (!photo) return
  if (key === 'download') {
    const matchedFile = files.value.find(file => file.path === photo.browserPath)
    if (matchedFile) {
      await downloadFile(matchedFile)
    }
    return
  }
  if (key === 'manage-tags') {
    await openPhotoTagDialog()
    return
  }
  if (key === 'set-cover') {
    if (!currentDirectoryAlbum.value.albumId || !photo.id || photo.id <= 0) {
      alert('当前图片还没有可用的照片记录，暂时无法设为封面')
      return
    }
    try {
      await api.put(`/albums/${currentDirectoryAlbum.value.albumId}/cover`, {
        coverImageIds: [photo.id]
      })
      currentDirectoryAlbum.value.albumHasCustomCover = true
    } catch (e: any) {
      alert('设置封面失败: ' + (e.response?.data?.error || e.message))
    }
    return
  }
  if (key === 'toggle-hidden') {
    if (!photo.id || photo.id <= 0) {
      alert('当前图片还没有可用的照片记录，暂时无法修改显示状态')
      return
    }
    try {
      const nextHidden = !photo.isHidden
      await api.put(`/admin/photos/${photo.id}/hidden`, {
        isHidden: nextHidden
      })
      photo.isHidden = nextHidden
      const matchedFile = files.value.find(file => file.path === photo.browserPath)
      if (matchedFile) {
        matchedFile.photoHidden = nextHidden
        if (matchedFile.thumbnail) {
          matchedFile.thumbnail.isHidden = nextHidden
        }
      }
    } catch (e: any) {
      alert('修改图片显示状态失败: ' + (e.response?.data?.error || e.message))
    }
    return
  }
  if (key === 'rescan-faces') {
    if (!photo.id || photo.id <= 0) {
      alert('当前图片还没有可用的照片记录，暂时无法重建人脸')
      return
    }
    try {
      await api.post(`/admin/photos/${photo.id}/rescan-faces`)
    } catch (e: any) {
      alert('重建人脸失败: ' + (e.response?.data?.error || e.message))
    }
    return
  }
  if (key === 'delete') {
    viewerVisible.value = false
    await openDeleteDialog([photo.browserPath])
  }
}

const openContextViewer = () => {
  const item = contextMenu.value.item
  if (!item || item.isDirectory) return
  contextMenu.value.show = false
  openFile(item)
}

const toggleContextPhotoHidden = async () => {
  const item = contextMenu.value.item
  if (!item || item.isDirectory || !item.thumbnail?.id) return
  contextMenu.value.show = false
  try {
    const nextHidden = !item.photoHidden
    await api.put(`/admin/photos/${item.thumbnail.id}/hidden`, {
      isHidden: nextHidden
    })
    item.photoHidden = nextHidden
    if (item.thumbnail) {
      item.thumbnail.isHidden = nextHidden
    }
  } catch (e: any) {
    alert('修改图片显示状态失败: ' + (e.response?.data?.error || e.message))
  }
}

const rescanContextPhotoFaces = async () => {
  const item = contextMenu.value.item
  if (!item || item.isDirectory || !item.thumbnail?.id) return
  contextMenu.value.show = false
  try {
    await api.post(`/admin/photos/${item.thumbnail.id}/rescan-faces`)
  } catch (e: any) {
    alert('重建人脸失败: ' + (e.response?.data?.error || e.message))
  }
}

const setContextPhotoAsCover = async () => {
  const item = contextMenu.value.item
  if (!item || item.isDirectory || !item.thumbnail?.id || !currentDirectoryAlbum.value.albumId) return
  contextMenu.value.show = false
  try {
    await api.put(`/albums/${currentDirectoryAlbum.value.albumId}/cover`, {
      coverImageIds: [item.thumbnail.id]
    })
    currentDirectoryAlbum.value.albumHasCustomCover = true
  } catch (e: any) {
    alert('设置封面失败: ' + (e.response?.data?.error || e.message))
  }
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
  closeFloatingMenus()
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
  openDeleteDialog(Array.from(selectedPaths.value))
}

const submitDeleteConfirmed = async () => {
  if (!deletePendingPaths.value.length || loadingDeletePreview.value) return
  deletingItems.value = true
  try {
    if (deletePendingPaths.value.length === 1) {
      await api.delete('/admin/folders/browser/delete', {
        params: {
          path: deletePendingPaths.value[0],
          providerId: canSelectStorageProvider.value ? (selectedProviderId.value ?? undefined) : undefined
        }
      })
    } else {
      await api.delete('/admin/folders/browser/delete-items', {
        params: {
          paths: deletePendingPaths.value,
          providerId: canSelectStorageProvider.value ? (selectedProviderId.value ?? undefined) : undefined
        }
      })
    }
    selectedPaths.value.clear()
    closeDeleteDialog()
    await loadFiles()
  } catch (e: any) {
    deletePreviewError.value = e.response?.data?.error || e.message || '删除失败'
  } finally {
    deletingItems.value = false
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
  filteredItems.value.forEach(i => set.add(i.path))
  selectedPaths.value = set
}

const invertSelection = () => {
  if (!multiSelect.value || !supportsItemManagement.value) return
  const set = new Set<string>()
  const current = selectedPaths.value
  filteredItems.value.forEach(i => {
    if (current.has(i.path)) return
    set.add(i.path)
  })
  // 同时保留未选 → 选，已选 → 取消
  filteredItems.value.forEach(i => {
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
  openMoveDialog(Array.from(selectedPaths.value))
}

const submitMoveDialog = async () => {
  if (!movePendingPaths.value.length || movingItems.value) return
  let target = ''
  try {
    target = resolveTargetPath(moveTargetInput.value)
  } catch (e: any) {
    alert(e?.message || '目标目录格式不合法')
    return
  }
  movingItems.value = true
  try {
    await api.post('/admin/folders/browser/move-items', null, {
      params: {
        paths: movePendingPaths.value,
        target,
        providerId: canSelectStorageProvider.value ? (selectedProviderId.value ?? undefined) : undefined
      }
    })
    movingItems.value = false
    selectedPaths.value.clear()
    closeMoveDialog()
    await loadFiles()
  } catch (e: any) {
    alert('移动失败: ' + (e.response?.data?.error || e.message))
  } finally {
    movingItems.value = false
  }
}

const bindSelectedAlbums = async () => {
  if (!unboundSelectedDirectories.value.length || bindingAlbumPath.value) return
  try {
    for (const dir of unboundSelectedDirectories.value) {
      await bindAlbum(dir)
    }
    await loadFiles(currentPath.value, false)
  } catch (_error) {
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
    smallThumbPath: photo.smallThumbPath,
    thumbnailPath: photo.thumbnailPath,
    originalPath: photo.originalPath
  }, 'small') || ''
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
  if (!activeProviderSupported.value && !draggingPaths.value.length) return
  if (dragLeaveTimer) { clearTimeout(dragLeaveTimer); dragLeaveTimer = null }
  isDragOver.value = true
}
const onDragLeave = () => {
  if (!activeProviderSupported.value && !draggingPaths.value.length) return
  dragLeaveTimer = setTimeout(() => { isDragOver.value = false }, 100)
}

const handleItemDragStart = (event: DragEvent, item: FileItem) => {
  if (!supportsItemManagement.value || !event.dataTransfer) return
  const paths = multiSelect.value && selectedPaths.value.has(item.path)
    ? Array.from(selectedPaths.value)
    : [item.path]
  draggingPaths.value = paths
  dragMode.value = 'move'
  event.dataTransfer.effectAllowed = 'move'
  event.dataTransfer.setData('application/x-photoexhibition-paths', JSON.stringify(paths))
  event.dataTransfer.setData('text/plain', item.path)
}

const handleItemDragEnd = () => {
  draggingPaths.value = []
  dragMoveTargetPath.value = ''
  dragMode.value = 'upload'
  isDragOver.value = false
}

const handleFolderDragOver = (dir: FileItem) => {
  if (!draggingPaths.value.length) return
  dragMoveTargetPath.value = dir.path
  dragMode.value = 'move'
}

const handleFolderDragLeave = (dir: FileItem) => {
  if (dragMoveTargetPath.value === dir.path) {
    dragMoveTargetPath.value = ''
  }
}

const movePathsToTarget = async (paths: string[], target: string) => {
  await api.post('/admin/folders/browser/move-items', null, {
    params: {
      paths,
      target,
      providerId: canSelectStorageProvider.value ? (selectedProviderId.value ?? undefined) : undefined
    }
  })
}

const isInvalidMoveTarget = (paths: string[], target: string) => {
  return paths.some(path => {
    const normalizedSource = normalizePath(path)
    const normalizedTarget = normalizePath(target)
    return normalizedSource === normalizedTarget || normalizedTarget.startsWith(`${normalizedSource}/`)
  })
}

const handleBreadcrumbDragOver = (targetPath: string) => {
  if (!draggingPaths.value.length) return
  dragMoveTargetPath.value = targetPath
  dragMode.value = 'move'
}

const handleBreadcrumbDragLeave = (targetPath: string) => {
  if (dragMoveTargetPath.value === targetPath) {
    dragMoveTargetPath.value = ''
  }
}

const handleBreadcrumbDrop = async (event: DragEvent, targetPath: string) => {
  event.stopPropagation()
  const raw = event.dataTransfer?.getData('application/x-photoexhibition-paths')
  let paths: string[]
  try {
    paths = raw ? (JSON.parse(raw) as string[]) : draggingPaths.value
  } catch {
    handleItemDragEnd()
    return
  }
  dragMoveTargetPath.value = ''
  if (!paths?.length) return
  if (isInvalidMoveTarget(paths, targetPath)) {
    alert('不能移动到自身或其子目录中')
    handleItemDragEnd()
    return
  }
  try {
    await movePathsToTarget(paths, targetPath)
    selectedPaths.value.clear()
    await loadFiles()
  } catch (e: any) {
    const message = e.response?.data?.error || e.message || ''
    if (!String(message).includes('不能移动到自身') && !String(message).includes('其子目录')) {
      alert('移动失败: ' + message)
    }
  } finally {
    handleItemDragEnd()
  }
}

const handleFolderDrop = async (event: DragEvent, dir: FileItem) => {
  event.stopPropagation()
  const raw = event.dataTransfer?.getData('application/x-photoexhibition-paths')
  let paths: string[]
  try {
    paths = raw ? (JSON.parse(raw) as string[]) : draggingPaths.value
  } catch {
    handleItemDragEnd()
    return
  }
  dragMoveTargetPath.value = ''
  if (!paths?.length) return
  if (isInvalidMoveTarget(paths, dir.path)) {
    alert('不能移动到自身或其子目录中')
    handleItemDragEnd()
    return
  }
  try {
    await movePathsToTarget(paths, dir.path)
    selectedPaths.value.clear()
    await loadFiles()
  } catch (e: any) {
    const message = e.response?.data?.error || e.message || ''
    if (!String(message).includes('不能移动到自身') && !String(message).includes('其子目录')) {
      alert('移动失败: ' + message)
    }
  } finally {
    handleItemDragEnd()
  }
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
  const internalPathsPayload = event.dataTransfer?.getData('application/x-photoexhibition-paths')
  if (internalPathsPayload) {
    try {
      const paths = JSON.parse(internalPathsPayload)
      if (!Array.isArray(paths) || paths.some(path => typeof path !== 'string')) throw new Error('invalid drag payload')
      await movePathsToTarget(paths, currentPath.value || basePath.value)
      selectedPaths.value.clear()
      await loadFiles()
    } catch (e: any) {
      alert('移动失败: ' + (e.response?.data?.error || e.message))
    } finally {
      handleItemDragEnd()
    }
    return
  }
  if (!activeProviderSupported.value) {
    alert(uploadDisabledReason.value)
    return
  }
  dragMode.value = 'upload'
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
  const token = getEffectiveAuthToken()
  uploading.value = true
  uploadStatus.value = `正在上传 0 / ${fileList.length} 个文件...`
  let totalSaved = 0
  let finalScanQueued = true
  let finalScanMessage = ''
  const uploadTargetPath = currentPath.value || basePath.value
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
    await loadFiles(uploadTargetPath, false)
    if (refreshTimer) clearTimeout(refreshTimer)
    refreshTimer = setTimeout(() => loadFiles(uploadTargetPath, false), 1200)
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
    if (!contentHash) {
      continue
    }
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
  const cryptoApi = globalThis.crypto
  if (!cryptoApi?.subtle?.digest) {
    return ''
  }
  const buffer = await file.arrayBuffer()
  const digest = await cryptoApi.subtle.digest('SHA-256', buffer)
  return Array.from(new Uint8Array(digest))
    .map(byte => byte.toString(16).padStart(2, '0'))
    .join('')
}

const changeStorageProvider = async () => {
  selectedPaths.value.clear()
  items.value = []
  parentPath.value = null
  error.value = ''
  try {
    await loadBasePath(selectedProviderId.value, true)
    await loadFiles(basePath.value, false)
  } catch (e: any) {
    error.value = e?.response?.data?.error || e?.message || '加载文件失败'
    currentPath.value = basePath.value
    items.value = []
  }
}

const closeFloatingMenus = () => {
  breadcrumbMenu.value.show = false
  contextMenu.value.show = false
  sortMenu.value.show = false
  providerMenu.value.show = false
  viewMenu.value.show = false
  pageSizeMenu.value.show = false
  actionsMenu.value.show = false
}

const openFloatingMenu = (
  trigger: HTMLElement | null,
  target: { show: boolean, x: number, y: number },
  menuWidth: number,
  estimatedHeight: number,
  preferAbove = false
) => {
  const gap = 10
  const viewportPadding = 16
  const rect = trigger?.getBoundingClientRect()
  const fallbackLeft = Math.max(16, window.innerWidth - menuWidth - 20)
  const left = rect
    ? Math.min(Math.max(viewportPadding, rect.right - menuWidth), window.innerWidth - menuWidth - viewportPadding)
    : fallbackLeft
  const availableAbove = rect ? rect.top - gap - viewportPadding : window.innerHeight - 120
  const availableBelow = rect ? window.innerHeight - rect.bottom - gap - viewportPadding : window.innerHeight - 120
  const shouldOpenAbove = preferAbove
    ? (availableAbove >= estimatedHeight || availableAbove > availableBelow)
    : !(availableBelow >= estimatedHeight || availableBelow > availableAbove)
  const top = rect
    ? (shouldOpenAbove
      ? Math.max(viewportPadding, rect.top - gap - estimatedHeight)
      : Math.min(window.innerHeight - viewportPadding - estimatedHeight, rect.bottom + gap))
    : Math.max(viewportPadding, window.innerHeight - viewportPadding - estimatedHeight)
  target.x = left
  target.y = top
  target.show = true
}

const toggleProviderMenu = () => {
  if (providerMenu.value.show) {
    providerMenu.value.show = false
    return
  }
  closeFloatingMenus()
  openFloatingMenu(providerTriggerRef.value, providerMenu.value, 280, 72 + availableStorageProviders.value.length * 52)
}

const toggleActionsMenu = () => {
  if (actionsMenu.value.show) {
    actionsMenu.value.show = false
    return
  }
  closeFloatingMenus()
  openFloatingMenu(actionsTriggerRef.value, actionsMenu.value, 220, 280, false)
}

const toggleViewMenu = () => {
  if (viewMenu.value.show) {
    viewMenu.value.show = false
    return
  }
  closeFloatingMenus()
  openFloatingMenu(viewTriggerRef.value, viewMenu.value, 244, 72 + viewModeOptions.length * 32 + gridPresetOptions.length * 32 + 20, true)
}

const toggleSortMenu = () => {
  if (sortMenu.value.show) {
    sortMenu.value.show = false
    return
  }
  const headerHeight = 26
  const optionHeight = 32
  const dividerHeight = 9
  const footerHeight = 8
  const estimatedHeight =
    headerHeight
    + sortModeOptions.length * optionHeight
    + dividerHeight
    + headerHeight
    + typeOrderOptions.length * optionHeight
    + footerHeight
  closeFloatingMenus()
  openFloatingMenu(sortTriggerRef.value, sortMenu.value, 232, estimatedHeight, true)
}

const togglePageSizeMenu = () => {
  if (pageSizeMenu.value.show) {
    pageSizeMenu.value.show = false
    return
  }
  closeFloatingMenus()
  openFloatingMenu(pageSizeTriggerRef.value, pageSizeMenu.value, 136, 72 + pageSizeOptions.length * 32, true)
}

const selectStorageProvider = async (providerId: number) => {
  providerMenu.value.show = false
  if (selectedProviderId.value === providerId) {
    return
  }
  selectedProviderId.value = providerId
  await changeStorageProvider()
}

const selectSortMode = (value: typeof sortMode.value) => {
  sortMode.value = value
}

const selectTypeOrderMode = (value: typeof typeOrderMode.value) => {
  typeOrderMode.value = value
}

const selectViewMode = (value: typeof viewMode.value) => {
  viewMode.value = value
}

const selectGridPreset = (value: typeof gridPreset.value) => {
  viewMode.value = 'grid'
  gridPreset.value = value
}

const selectPageSize = (value: typeof pageSizeOptions[number]) => {
  pageSize.value = value
  pageSizeMenu.value.show = false
}

watch(showCreateDialog, (val) => {
  if (val) {
    nextTick(() => newFolderInput.value?.focus())
  }
})

watch(showMoveDialog, (val) => {
  if (val) {
    nextTick(() => moveTargetInputRef.value?.focus())
  }
})

watch([sortMode, typeOrderMode, pageSize, viewMode, gridPreset], () => {
  currentPage.value = 1
  if (fileBrowserPreferenceSaveTimer) {
    clearTimeout(fileBrowserPreferenceSaveTimer)
  }
  fileBrowserPreferenceSaveTimer = setTimeout(() => {
    persistFileBrowserPreferences()
  }, 250)
})

watch(searchKeyword, () => {
  currentPage.value = 1
  // 搜索范围变化后保留仍在结果中的选择，避免批量操作误带入隐藏项目。
  const visiblePaths = new Set(filteredItems.value.map(item => item.path))
  selectedPaths.value = new Set(Array.from(selectedPaths.value).filter(path => visiblePaths.has(path)))
})

onMounted(async () => {
  try {
    await loadFileBrowserPreferences()
    await loadBasePath(selectedProviderId.value, true)
    await loadFiles(basePath.value, false)
  } catch (e: any) {
    error.value = e?.response?.data?.error || e?.message || '加载文件失败'
    currentPath.value = basePath.value
    items.value = []
  }
  document.addEventListener('click', handleClickOutside)
  const escHandler = (e: KeyboardEvent) => {
    if (e.key === 'Escape') {
      if (viewerVisible.value) {
        viewerVisible.value = false
        return
      }
      if (breadcrumbMenu.value.show) {
        breadcrumbMenu.value.show = false
        return
      }
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
  if (fileBrowserPreferenceSaveTimer) {
    clearTimeout(fileBrowserPreferenceSaveTimer)
  }
})
</script>
