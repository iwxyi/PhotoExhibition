<template>
  <div class="h-screen bg-gray-900 text-white flex flex-col overflow-hidden">
    <div class="flex-shrink-0 px-4 sm:px-6 lg:px-8 py-4 border-b border-gray-700">
      <div class="flex items-center justify-between">
        <div>
          <h1 class="text-xl font-light">人物管理</h1>
        </div>
        <router-link to="/admin" class="px-3 py-1.5 bg-gray-700 hover:bg-gray-600 rounded text-sm">返回</router-link>
      </div>
    </div>

    <div class="flex-1 flex gap-2 overflow-hidden px-4 sm:px-6 lg:px-8 py-4">
      <!-- 左侧人物头像列表 -->
      <div 
        class="bg-gray-800 rounded-lg p-3 flex flex-col flex-shrink-0 min-h-0"
        :class="{ 'pointer-events-none opacity-60': loadingPersons }"
        :style="{ width: leftPanelWidth + 'px', minWidth: '200px', maxWidth: '500px' }"
      >
        <div class="mb-3 space-y-2">
          <input
            v-model="personKeyword"
            @input="loadPersons"
            placeholder="搜索..."
            class="w-full px-2 py-1.5 bg-gray-700 border border-gray-600 rounded text-xs focus:outline-none focus:ring-1 focus:ring-blue-500"
          />
          <div class="flex items-center gap-2 text-[11px] text-gray-300">
            <span
              class="whitespace-nowrap"
              :title="'调高更保守，调低更易合并同人。默认 0.70'"
            >
              聚类阈值
            </span>
            <div class="flex-1 relative h-6 flex items-center">
              <div class="absolute left-0 right-0 h-[2px] bg-gray-600 rounded-full"></div>
              <div class="absolute left-0 right-0 flex justify-between px-[2px] pointer-events-none">
                <span
                  v-for="p in snapPoints"
                  :key="p"
                  class="w-1.5 h-1.5 rounded-full bg-gray-500 opacity-70"
                ></span>
              </div>
              <input
                type="range"
                min="0.1"
                max="0.9"
                step="0.01"
                v-model.number="clusterThreshold"
                class="cluster-slider w-full relative z-10"
              />
            </div>
            <input
              type="number"
              min="0.1"
              max="0.9"
              step="0.01"
              v-model.number="clusterThreshold"
              @input="handleThresholdInput"
              class="w-16 px-2 py-1 bg-gray-700 border border-gray-600 rounded text-xs focus:outline-none focus:ring-1 focus:ring-blue-500"
            />
          </div>
        </div>
        <div
          ref="personListContainer"
          class="flex-1 overflow-y-auto overflow-x-hidden"
          :style="{ display: 'grid', gridTemplateColumns: `repeat(${personColumns}, 1fr)`, gap: '8px', alignContent: 'start', gridAutoFlow: 'row' }"
        >
          <!-- 已认领人物 -->
          <div
            v-for="p in visibleConfirmedPersons"
            :key="`confirmed-${p.id}`"
            class="flex flex-col items-center p-1.5 rounded cursor-pointer transition-all border-2 border-transparent bg-gray-800/70 hover:bg-gray-700/80"
            :class="[
              isSelected(p) ? 'border-yellow-500 bg-gray-700/80' : '',
              p.hidden ? 'opacity-40' : ''
            ]"
            @click="selectPerson(p)"
            @contextmenu.prevent="openPersonContextMenu($event, p)"
          >
            <div 
              class="w-12 h-12 rounded-full bg-gray-600 overflow-hidden mb-1 cursor-pointer"
              @click.stop="selectPerson(p)"
            >
              <img v-if="getPersonThumb(p)" :src="getPersonThumb(p)" class="w-full h-full object-cover" />
            </div>
            <div class="text-center w-full">
              <div
                class="font-medium text-xs truncate"
                :title="(p.hidden ? '[已隐藏] ' : '') + (p.name || '未命名')"
              >
                {{ p.name || '未命名' }}
              </div>
              <div class="text-[10px] text-gray-400">({{ p.faceCount || 0 }})</div>
            </div>
          </div>
          
          <!-- 未确认聚类 -->
          <div
            v-for="p in visibleClusterPersons"
            :key="`cluster-${p.id}`"
            class="flex flex-col items-center p-1.5 rounded cursor-pointer transition-all border-2 border-transparent bg-gray-800/60 hover:bg-gray-700/70"
            :class="isSelected(p) ? 'border-yellow-500 bg-gray-700/80' : ''"
            @click="selectPerson(p)"
          >
            <div 
              class="w-12 h-12 rounded-full bg-gray-600 overflow-hidden mb-1 cursor-pointer"
              @click.stop="selectPerson(p)"
            >
              <img v-if="getPersonThumb(p)" :src="getPersonThumb(p)" class="w-full h-full object-cover" />
            </div>
            <div class="text-center w-full">
              <div
                class="font-medium text-xs truncate text-yellow-400"
                :title="p.name || '未命名'"
              >
                {{ p.name || '未命名' }}
              </div>
              <div class="text-[10px] text-gray-400">({{ p.faceCount || 0 }})</div>
            </div>
          </div>
          
          <div v-if="!persons.length && !loadingPersons" class="col-span-full text-gray-500 text-xs text-center py-4">暂无人物</div>
          <div v-if="loadingPersons" class="col-span-full text-gray-500 text-xs text-center py-4">加载中...</div>
        </div>
          <div class="mt-3 text-[11px] text-gray-300">
            <div>共 {{ persons.length }} 个</div>
          </div>

        <!-- 选中人物的姓名 / 备注 / 删除按钮 -->
        <div v-if="selectedItem" class="mt-3 pt-3 border-t border-gray-700 space-y-2">
          <!-- 相似人物推荐（仅聚类显示） -->
          <div v-if="selectedItem.type === 'cluster' && similarPersons.length > 0" class="mb-3">
            <div class="flex gap-2 overflow-x-auto pb-2">
              <button
                v-for="person in similarPersons"
                :key="person.id"
                @click="mergeToExistingPerson(person)"
                class="flex-shrink-0 px-2 py-1 rounded text-[10px] transition-colors"
                :class="person.similarity >= 0.6
                  ? 'bg-blue-600/20 hover:bg-blue-600/40 border border-blue-500/30 text-blue-300 hover:text-blue-200'
                  : 'bg-gray-600/20 hover:bg-gray-600/40 border border-gray-500/30 text-gray-400 hover:text-gray-300'"
              >
                {{ person.name || '未命名' }} ({{ (person.similarity * 100).toFixed(0) }}%)
              </button>
            </div>
          </div>
          <div>
            <div class="flex gap-2">
              <input
                v-model="selectedPersonName"
                @blur="handleSelectedPersonNameBlur"
                @keyup.enter="handleSelectedPersonNameEnter"
                @keyup.esc="resetSelectedPersonName"
                class="flex-1 px-2 py-1 bg-gray-700 border border-gray-600 rounded text-xs focus:outline-none focus:ring-1 focus:ring-blue-500"
                :placeholder="selectedItem.type === 'cluster' ? '人物姓名' : '修改姓名'"
              />
              <button
                v-if="selectedItem.type === 'cluster'"
                @click="createPersonFromSelectedCluster"
                class="px-2 py-1 bg-blue-600 hover:bg-blue-700 rounded text-xs whitespace-nowrap"
                :disabled="!selectedPersonName.trim() || savingPerson"
              >
                新建人物
              </button>
              <button
                v-if="selectedItem.type === 'confirmed'"
                @click="showDeleteDialog"
                class="p-1.5 bg-gray-700 hover:bg-gray-600 rounded transition-colors"
                title="删除人物"
              >
                <svg class="w-4 h-4" fill="none" stroke="rgb(239 68 68)" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"></path>
                </svg>
              </button>
            </div>
          </div>
          <div v-if="selectedItem.type === 'confirmed'">
            <textarea
              v-model="editingDescription"
              @blur="savePersonDescription"
              @keyup.esc="cancelDescriptionEdit"
              rows="2"
              class="w-full px-2 py-1 bg-gray-700 border border-gray-600 rounded text-xs focus:outline-none focus:ring-1 focus:ring-blue-500 resize-none"
              placeholder="添加备注..."
            ></textarea>
          </div>
        </div>
      </div>

      <!-- 可拖拽分割线 -->
      <div
        class="w-1 bg-gray-700 cursor-col-resize hover:bg-gray-600 active:bg-gray-500 transition-colors flex-shrink-0"
        style="touch-action: none; padding: 0 4px; margin: 0 -4px;"
        @mousedown="startResize"
        @touchstart.prevent="startResize"
      ></div>

      <!-- 右侧内容区域 -->
      <div class="flex-1 bg-gray-800 rounded-lg p-3 overflow-hidden flex flex-col min-w-0 relative">
        <!-- 打开大图时的loading：只展示，不阻塞tab切换/按钮 -->
        <div
          v-if="showViewerLoadingOverlay"
          class="absolute inset-0 z-30 pointer-events-none flex items-center justify-center"
        >
          <div class="h-8 w-8 rounded-full border-2 border-gray-400 border-t-transparent animate-spin opacity-50"></div>
        </div>
        <div v-if="!selectedItem" class="text-gray-400 text-xs text-center py-8 flex-1 flex items-center justify-center">
          请从左侧选择一个人物
        </div>
        <div
          v-else
          class="flex-1 flex flex-col overflow-hidden"
          tabindex="0"
          @keydown="handleFaceListKeydown"
        >
          <div class="flex gap-1 mb-3 border-b border-gray-700 flex-shrink-0 items-center">
            <div class="flex gap-1 flex-1 overflow-x-auto">
              <button
                v-if="selectedItem.type === 'confirmed' || selectedItem.type === 'cluster'"
                class="px-3 py-1.5 rounded-t text-xs transition-colors whitespace-nowrap"
                :class="tab === 'confirmed' ? 'bg-gray-700 text-blue-400 border-b-2 border-blue-400' : 'text-gray-400 hover:text-gray-200'"
                @click="tab = 'confirmed'"
              >
                <template v-if="selectedItem.type === 'confirmed'">
                  已认领
                  <span class="ml-1 inline-flex items-center">
                    <span
                      v-if="loadingConfirmedFaces || loadingAssignedPhotos"
                      class="inline-block h-3 w-3 rounded-full border-2 border-current border-t-transparent animate-spin opacity-70"
                    ></span>
                    <span v-else>({{ confirmedFaces.length + assignedPhotos.length }})</span>
                  </span>
                </template>
                <template v-else>
                  聚类
                  <span class="ml-1 inline-flex items-center">
                    <span
                      v-if="loadingPersonFaces"
                      class="inline-block h-3 w-3 rounded-full border-2 border-current border-t-transparent animate-spin opacity-70"
                    ></span>
                    <span v-else>({{ personFaces.length }})</span>
                  </span>
                </template>
              </button>
              <!-- 自动分配tab已隐藏，保留代码以备将来使用
              <button
                v-if="selectedItem.type === 'confirmed'"
                class="px-3 py-1.5 rounded-t text-xs transition-colors whitespace-nowrap"
                :class="tab === 'auto' ? 'bg-gray-700 text-orange-400 border-b-2 border-orange-400' : 'text-gray-400 hover:text-gray-200'"
                @click="tab = 'auto'"
              >
                自动分配 ({{ autoAssignedFaces.length }})
              </button>
              -->
              <button
                v-if="selectedItem.type === 'confirmed'"
                class="px-3 py-1.5 rounded-t text-xs transition-colors whitespace-nowrap"
                :class="tab === 'similar' ? 'bg-gray-700 text-green-400 border-b-2 border-green-400' : 'text-gray-400 hover:text-gray-200'"
                @click="tab = 'similar'"
              >
                相似推荐
                <span class="ml-1 inline-flex items-center">
                  <span
                    v-if="loadingSimilarFaces"
                    class="inline-block h-3 w-3 rounded-full border-2 border-current border-t-transparent animate-spin opacity-70"
                  ></span>
                  <span v-else>({{ similarFaces.length }})</span>
                </span>
              </button>
              <button
                v-if="selectedItem.type === 'confirmed'"
                class="px-3 py-1.5 rounded-t text-xs transition-colors whitespace-nowrap"
                :class="tab === 'albums' ? 'bg-gray-700 text-purple-400 border-b-2 border-purple-400' : 'text-gray-400 hover:text-gray-200'"
                @click="tab = 'albums'"
              >
                套图推荐
                <span class="ml-1 inline-flex items-center">
                  <span
                    v-if="loadingAlbums"
                    class="inline-block h-3 w-3 rounded-full border-2 border-current border-t-transparent animate-spin opacity-70"
                  ></span>
                  <span v-else>({{ albumRecommendations.length }})</span>
                </span>
              </button>
              <button
                class="px-3 py-1.5 rounded-t text-xs transition-colors whitespace-nowrap"
                :class="tab === 'unassigned' ? 'bg-gray-700 text-gray-300 border-b-2 border-gray-300' : 'text-gray-400 hover:text-gray-200'"
                @click="tab = 'unassigned'"
              >
                未分配
                <span class="ml-1 inline-flex items-center">
                  <span
                    v-if="loadingUnassignedFaces"
                    class="inline-block h-3 w-3 rounded-full border-2 border-current border-t-transparent animate-spin opacity-70"
                  ></span>
                  <span v-else-if="unassignedLoadedOnce">({{ unassignedFaces.length }})</span>
                </span>
              </button>
            </div>
            <!-- 操作按钮区域 -->
            <div class="flex gap-2 flex-shrink-0">
                  <button
                    @click="selectAllCurrentTab"
                :disabled="getCurrentTabFaceCount() === 0"
                    class="px-2 py-1 bg-gray-600 hover:bg-gray-500 rounded text-[10px] disabled:opacity-50"
                  >
                    全选
                  </button>
                  <button
                @click="invertSelection(getCurrentTabType())"
                :disabled="getCurrentTabFaceCount() === 0"
                    class="px-2 py-1 bg-gray-600 hover:bg-gray-500 rounded text-[10px] disabled:opacity-50"
                  >
                    反选
                  </button>
                  <button
                v-if="tab !== 'confirmed'"
                @click="attemptClaimOrRemove"
                :aria-disabled="getClaimButtonState.disabled"
                :class="[
                  'px-2 py-1 rounded text-[10px] focus:outline-none',
                  getClaimButtonState.isRemove
                    ? 'bg-red-600 hover:bg-red-700'
                    : (getClaimButtonState.claimType === 'photo' ? 'bg-blue-600 hover:bg-blue-700' : 'bg-emerald-600 hover:bg-emerald-700'),
                  getClaimButtonState.disabled ? 'opacity-50' : ''
                ]"
              >
                <span v-if="isBatchAssigning" class="inline-flex items-center">
                  <span class="animate-spin border-2 border-white border-t-transparent rounded-full w-3 h-3 mr-2"></span>
                  <span>加载中...</span>
                </span>
                <span v-else>{{ getClaimButtonText }}</span>
              </button>
              <button
                v-if="tab === 'confirmed' && selectedItem?.type === 'cluster'"
                    @click="openClaimDialog('cluster')"
                    :disabled="selectedClusterFaces.size === 0"
                    class="px-2 py-1 bg-blue-600 hover:bg-blue-700 rounded text-[10px] disabled:opacity-50"
                  >
                    认领为<template v-if="selectedClusterFaces.size > 0"> ({{ selectedClusterFaces.size }})</template>
                  </button>
              <button
                v-if="tab === 'unassigned'"
                    @click="openClaimDialog('unassigned')"
                    :disabled="selectedUnassigned.size === 0"
                    class="px-2 py-1 bg-blue-600 hover:bg-blue-700 rounded text-[10px] disabled:opacity-50"
                  >
                    认领为<template v-if="selectedUnassigned.size > 0"> ({{ selectedUnassigned.size }})</template>
                  </button>
              <button
                v-if="tab === 'confirmed'"
                    @click="removeSelectedConfirmed"
                    :disabled="selectedConfirmed.size === 0"
                    class="px-2 py-1 bg-red-600 hover:bg-red-700 rounded text-[10px] disabled:opacity-50"
                  >
                    移除<template v-if="selectedConfirmed.size > 0"> ({{ selectedConfirmed.size }})</template>
                  </button>
                </div>
          </div>

          <!-- Tab内容 -->
          <div class="flex-1 overflow-y-auto pr-1 relative" ref="tabScrollContainer" @scroll.passive="handleFaceScroll">
            <!-- 当前tab独立loading蒙版：只影响tab内容，不影响其他tab切换 -->
            <div
              v-if="showTabLoadingOverlay"
              class="absolute inset-0 z-40 pointer-events-auto flex items-center justify-center bg-black/10"
            >
              <div class="h-7 w-7 rounded-full border-2 border-gray-400 border-t-transparent animate-spin opacity-60"></div>
            </div>
            <!-- 已认领照片 -->
            <div v-if="tab === 'confirmed' && selectedItem.type === 'confirmed'">
              <div class="mb-2">
                <span class="text-xs text-gray-400">已认领的人脸</span>
                <span v-if="loadingConfirmedFaces" class="ml-2 text-xs text-blue-400">加载中...</span>
              </div>
              <div 
                ref="confirmedContainer"
                class="grid grid-cols-3 md:grid-cols-4 xl:grid-cols-5 2xl:grid-cols-6 gap-2 pb-4 relative"
                @mousedown="handleMouseDown($event, 'confirmed')"
                @mousemove="handleMouseMove($event, 'confirmed')"
                @mouseup="handleMouseUp($event, 'confirmed')"
                @mouseleave="handleMouseUp($event, 'confirmed')"
              >
                <div
                  v-for="(f, index) in visibleConfirmedFaces"
                  :key="f.id"
                  :data-face-id="f.id"
                  :data-face-index="index"
                  class="bg-gray-700 rounded overflow-hidden border relative group select-none"
                  :class="selectedConfirmed.has(f.id) ? 'border-2 border-blue-500' : 'border-gray-600'"
                  @click="handleFaceClick($event, f.id, 'confirmed')"
                >
                  <div class="relative h-32 bg-gray-800 overflow-hidden" @dblclick.stop="handleFaceDblClick(f)">
                    <img
                      v-if="getFaceThumb(f)"
                      :src="getFaceThumb(f)"
                      class="w-full h-full object-cover pointer-events-none"
                      :style="getFaceCropStyle(f)"
                      :loading="index < confirmedPriorityPageCount ? 'eager' : 'lazy'"
                      :fetchpriority="index < confirmedPriorityRowCount ? 'high' : 'auto'"
                      decoding="async"
                    />
                    <!-- 设为头像按钮 -->
                    <button
                      v-if="!f.isRemoved"
                      @click.stop="setAsPersonAvatar(f)"
                      class="absolute bottom-1 left-1 bg-purple-600 hover:bg-purple-700 text-white px-1.5 py-0.5 rounded text-[10px] opacity-0 group-hover:opacity-100 transition-opacity"
                      title="设为头像"
                    >
                      头像
                    </button>
                    <!-- 移除按钮 -->
                    <button
                      v-if="!f.isRemoved"
                      @click.stop="unassignFace(f.id)"
                      class="absolute bottom-1 right-1 bg-red-600 hover:bg-red-700 text-white px-1.5 py-0.5 rounded text-[10px] opacity-0 group-hover:opacity-100 transition-opacity"
                    >
                      移除
                    </button>
                    <!-- 恢复按钮 -->
                    <button
                      v-if="f.isRemoved"
                      @click.stop="restoreFace(f.id)"
                      class="absolute bottom-1 right-1 bg-green-600 hover:bg-green-700 text-white px-1.5 py-0.5 rounded text-[10px] opacity-0 group-hover:opacity-100 transition-opacity"
                    >
                      恢复
                    </button>
                  </div>
                  <div class="p-1.5">
                    <div
                      class="text-[10px] text-blue-300 truncate cursor-pointer"
                      :title="f.photoFilename"
                        @click.stop="openPhoto(f.photoId)"
                          @dblclick.stop="handleFaceDblClick(f)"
                    >
                      {{ f.photoFilename || '-' }}
                    </div>
                  </div>
                </div>
                <div
                  v-for="n in facePlaceholderCounts.confirmed"
                  :key="`confirmed-ph-${n}`"
                  class="h-40 rounded bg-gray-700/40 border border-gray-700/60 animate-pulse overflow-hidden relative"
                >
                  <div class="absolute top-0 left-0 right-0 h-32 bg-gray-600/50"></div>
                  <div class="absolute bottom-3 left-2 right-2 h-3 bg-gray-600/60 rounded"></div>
                </div>
                <!-- 框选遮罩 -->
                <div
                  v-if="isSelecting && currentTab === 'confirmed'"
                  class="absolute border-2 border-blue-500 bg-blue-500/20 pointer-events-none z-50"
                  :style="selectionBoxStyle"
                ></div>
            </div>


              <!-- 直接指派的照片 -->
              <div v-if="assignedPhotos.length > 0" class="mt-6">
                <div class="mb-2">
                  <span class="text-xs text-gray-400">直接指派的照片 ({{ assignedPhotos.length }}张)</span>
                </div>
                <div class="grid grid-cols-3 md:grid-cols-4 xl:grid-cols-5 2xl:grid-cols-6 gap-2 pb-4">
                <div
                    v-for="(photo, index) in assignedPhotos"
                    :key="`assigned-${photo.id}`"
                    class="bg-gray-700 rounded overflow-hidden border border-gray-600 relative group select-none"
                >
                    <div class="relative h-32 bg-gray-800 overflow-hidden" @dblclick.stop="openViewerForPhoto(photo.id)">
                    <img
                        v-if="photo.thumbnailPath"
                        :src="getImageUrl(photo.thumbnailPath)"
                      class="w-full h-full object-cover pointer-events-none"
                      loading="lazy"
                    />
                    <button
                        @click.stop="handleRemoveClick(photo.id)"
                        class="absolute bottom-1 right-1 bg-red-600 hover:bg-red-700 text-white px-1.5 py-0.5 rounded text-[10px]"
                    >
                        移除
                    </button>
                  </div>
                  <div class="p-1.5">
                    <div
                      class="text-[10px] text-blue-300 truncate cursor-pointer"
                          :title="photo.filename"
                          @click.stop="openPhoto(photo.id)"
                          @dblclick.stop="openViewerForPhoto(photo.id)"
                    >
                          {{ photo.filename || '-' }}
                    </div>
                  </div>
                </div>
                </div>
              </div>

              <div v-if="confirmedFaces.length === 0 && assignedPhotos.length === 0" class="text-gray-400 text-xs text-center py-8">暂无已确认照片</div>
            </div>

<!-- 自动分配照片tab已隐藏，保留代码以备将来使用 -->

            <!-- 套图推荐 -->
            <div v-if="tab === 'albums' && selectedItem.type === 'confirmed'" class="h-full flex">
              <!-- 左列：相册列表 -->
              <div :style="{ width: albumsPanelWidth + 'px', minWidth: '150px', maxWidth: '400px' }" class="bg-gray-800 rounded-lg p-3 flex flex-col flex-shrink-0">
                <div class="mb-3">
                </div>
                <div class="flex-1 overflow-y-auto space-y-2">
                  <div
                    v-for="album in albumRecommendations"
                    :key="album.albumId"
                    class="cursor-pointer rounded p-2 transition-colors"
                    :class="selectedAlbum?.albumId === album.albumId ? 'bg-purple-600 text-white' : 'hover:bg-gray-700 text-gray-300'"
                    @click="selectAlbum(album)"
                  >
                    <div class="font-medium text-sm truncate flex items-center justify-between">
                      <span>{{ album.albumName.replace(/\s*\(\d+\)$/, '') }}</span>
                      <span class="text-xs opacity-75 ml-2 flex-shrink-0">
                        {{ album.claimedPhotoCount || 0 }}/{{ album.photoCount }}
                      </span>
                </div>
                    <div class="text-xs opacity-75 truncate">{{ album.albumPath }}</div>
              </div>
                  <div v-if="albumRecommendations.length === 0" class="text-gray-400 text-xs text-center py-4">
                    暂无相册推荐
              </div>
                </div>
            </div>

              <!-- 可拖拽分割线 -->
              <div
                class="w-1 bg-gray-700 cursor-col-resize hover:bg-gray-600 active:bg-gray-500 transition-colors flex-shrink-0 mx-2"
                style="touch-action: none; padding: 0 4px; margin-left: 4px; margin-right: 4px;"
                @mousedown="startResizeAlbums"
                @touchstart.prevent="startResizeAlbums"
              ></div>

              <!-- 右列：选中相册的人脸图片 -->
              <div class="flex-1 bg-gray-800 rounded-lg p-3 flex flex-col min-h-0">

                <!-- right scroll wrapper: ensures vertical scrolling independent of outer layout -->
                <div ref="albumContainer" class="flex-1 min-h-0 overflow-auto relative">
                  <div v-if="loadingAlbums" class="absolute inset-0 z-40 bg-black/20 flex items-center justify-center">
                    <div class="h-10 w-10 rounded-full border-4 border-gray-300 border-t-transparent animate-spin"></div>
                  </div>
              <div 
                    class="grid gap-3"
                    :style="{ gridTemplateColumns: `repeat(${albumColumns}, 1fr)`, gridAutoRows: 'auto', justifyContent: 'center' }"
                    @mousedown="handleMouseDown($event, 'albums')"
                    @mousemove="handleMouseMove($event, 'albums')"
                    @mouseup="handleMouseUp($event, 'albums')"
                    @mouseleave="handleMouseUp($event, 'albums')"
                  >
                    <div v-if="!selectedAlbum" class="col-span-full text-gray-400 text-sm text-center py-8">
                      请选择左侧的相册
                    </div>
                <div
                      v-for="(f, index) in visibleAlbumFaces"
                  :key="f.id"
                  :data-face-id="f.id"
                  :data-face-index="index"
                  class="bg-gray-700 rounded overflow-hidden border relative group select-none"
                      :class="selectedAlbumFaces.has(f.id) ? 'border-2 border-blue-500' : 'border-purple-600/50'"
                      @click="handleFaceClick($event, f.id, 'albums')"
                >
                      <div v-if="(f.similarity || 0) > 0" class="absolute top-1 right-1 px-1.5 py-0.5 rounded text-[10px] z-10 bg-purple-600/80">
                    {{ ((f.similarity || 0) * 100).toFixed(0) }}%
                  </div>
                    <div style="position:relative;width:100%;padding-top:100%;background:#111;" @dblclick.stop="openViewer(f, f.bestFace?.id ? { preferredFaceId: f.bestFace.id } : {})">
                    <img
                      v-if="getFaceThumb(f)"
                      :src="getFaceThumb(f)"
                        class="absolute inset-0 w-full h-full object-cover pointer-events-none"
                      :style="getFaceCropStyle(f)"
                      loading="lazy"
                    />
                    <!-- 已认领标签：人脸认领为绿色，图片认领为蓝色 -->
                    <div v-if="f.faces && f.faces.some((face: any) => face.personId === selectedPersonId)"
                         class="absolute top-1 right-1 bg-emerald-600 text-white px-1.5 py-0.5 rounded text-[10px] z-10">
                      已认领
                    </div>
                    <div v-else-if="f.photoId && f.assignedPersonId === selectedPersonId"
                         class="absolute top-1 right-1 bg-blue-600 text-white px-1.5 py-0.5 rounded text-[10px] z-10">
                      已认领
                    </div>
                    <!-- 已移除状态 -->
                    <div v-else-if="f.isRemoved"
                         class="absolute top-1 right-1 bg-gray-600 text-white px-1.5 py-0.5 rounded text-[10px] z-10">
                      已移除
                    </div>
                    <!-- 人脸认领按钮（绿色） -->
                    <button
                        v-if="f.faces && f.faces.some((face: any) => face.personId !== selectedPersonId) && f.assignedPersonId !== selectedPersonId && !f.faces.some((face: any) => face.personId === selectedPersonId)"
                        @click.stop="assignAlbumFace(f)"
                        :disabled="isAssigningFaceIds.has(f.bestFace?.id)"
                        class="absolute bottom-1 right-1 bg-emerald-600 hover:bg-emerald-700 text-white px-1.5 py-0.5 rounded text-[10px] opacity-0 group-hover:opacity-100 transition-opacity disabled:opacity-50"
                    >
                        <span v-if="isAssigningFaceIds.has(f.bestFace?.id)" class="inline-flex items-center">
                          <span class="animate-spin border-2 border-white border-t-transparent rounded-full w-3 h-3 mr-2"></span>
                          <span>加载中</span>
                        </span>
                        <span v-else>认领人脸</span>
                    </button>
                    <!-- 认领为按钮 -->
                    <button
                        v-if="f.bestFace?.id && selectedItem.type === 'confirmed'"
                        @click.stop="openClaimDialogForSingleFace(f.bestFace.id)"
                        class="absolute bottom-1 right-[calc(100%+2px)] bg-blue-600 hover:bg-blue-700 text-white px-1.5 py-0.5 rounded text-[10px] opacity-0 group-hover:opacity-100 transition-opacity"
                    >
                        认领为
                    </button>
                    <!-- 图片认领按钮（蓝色） -->
                    <button
                        v-if="f.photoId && f.assignedPersonId !== selectedPersonId && !(f.faces && f.faces.some((face: any) => face.personId === selectedPersonId))"
                        @click.stop="assignPhoto(f.photoId)"
                        :disabled="isAssigningPhotoIds.has(f.photoId)"
                        class="absolute bottom-1 left-1 bg-blue-600 hover:bg-blue-700 text-white px-1.5 py-0.5 rounded text-[10px] opacity-0 group-hover:opacity-100 transition-opacity disabled:opacity-50"
                        :class="{ 'left-12': f.bestFace && f.bestFace.personId !== selectedPersonId && !f.faces.some((face: any) => face.personId === selectedPersonId) }"
                    >
                        <span v-if="isAssigningPhotoIds.has(f.photoId)" class="inline-flex items-center">
                          <span class="animate-spin border-2 border-white border-t-transparent rounded-full w-3 h-3 mr-2"></span>
                          <span>加载中</span>
                        </span>
                        <span v-else>认领图片</span>
                    </button>
                    <!-- 移除按钮 -->
                    <button
                        v-if="(f.photoId && f.assignedPersonId === selectedPersonId) || (f.faces && f.faces.some((face: any) => face.personId === selectedPersonId))"
                        @click.stop="unassignPhotoOrFace(f)"
                        class="absolute bottom-1 right-1 bg-red-600 hover:bg-red-700 text-white px-1.5 py-0.5 rounded text-[10px] opacity-0 group-hover:opacity-100 transition-opacity"
                    >
                        移除
                    </button>
                  </div>
                  <div class="p-1.5">
                    <div
                      class="text-[10px] text-blue-300 truncate cursor-pointer"
                      :title="f.photoFilename"
                      @click.stop="openPhoto(f.photoId)"
                      @dblclick.stop="handleFaceDblClick(f)"
                    >
                      {{ f.photoFilename || '-' }}
                    </div>
                  </div>
                </div>
                    <!-- placeholders removed -->
                </div>
                <div
                    v-if="isSelecting && currentTab === 'albums'"
                  class="absolute border-2 border-blue-500 bg-blue-500/20 pointer-events-none z-50"
                  :style="selectionBoxStyle"
                ></div>
              </div>
              </div>
            </div>

            <!-- 相似推荐 -->
            <div v-if="tab === 'similar' && selectedItem.type === 'confirmed'">
              <div class="mb-2">
                <span class="text-xs text-gray-400">相似推荐（相似度≥50% + 同文件夹≥40%）</span>
                <span v-if="loadingSimilarFaces" class="ml-2 text-xs text-green-400">加载中...</span>
              </div>
              <div 
                ref="similarContainer"
                class="grid grid-cols-3 md:grid-cols-4 xl:grid-cols-5 2xl:grid-cols-6 gap-2 pb-4 relative"
                @mousedown="handleMouseDown($event, 'similar')"
                @mousemove="handleMouseMove($event, 'similar')"
                @mouseup="handleMouseUp($event, 'similar')"
                @mouseleave="handleMouseUp($event, 'similar')"
              >
                <div
                  v-for="(f, index) in visibleSimilarFaces"
                  :key="f.id"
                  :data-face-id="f.id"
                  :data-face-index="index"
                  class="bg-gray-700 rounded overflow-hidden border relative group select-none"
                  :class="selectedSimilar.has(f.id) ? 'border-2 border-blue-500' : (f._isSameFolder ? 'border-purple-600/50' : 'border-green-600/50')"
                  @click="handleFaceClick($event, f.id, 'similar')"
                >
                  <div class="absolute top-1 right-1 px-1.5 py-0.5 rounded text-[10px] z-10"
                       :class="f._isSameFolder ? 'bg-purple-600/80' : 'bg-green-600/80'">
                    <template v-if="f._isSameFolder">
                      📁 {{ ((f.similarity || 0) * 100).toFixed(0) }}%
                    </template>
                    <template v-else>
                    {{ ((f.similarity || 0) * 100).toFixed(0) }}%
                    </template>
                  </div>
                  <div class="relative h-32 bg-gray-800 overflow-hidden" @dblclick.stop="handleFaceDblClick(f)">
                    <img
                      v-if="getFaceThumb(f)"
                      :src="getFaceThumb(f)"
                      class="w-full h-full object-cover pointer-events-none"
                      :style="getFaceCropStyle(f)"
                      loading="lazy"
                    />
                    <button
                      @click.stop="handleAssignClick(f.id, true)"
                      class="absolute bottom-1 right-1 bg-blue-600 hover:bg-blue-700 text-white px-1.5 py-0.5 rounded text-[10px] opacity-0 group-hover:opacity-100 transition-opacity"
                    >
                      认领
                    </button>
                    <!-- 认领为按钮 -->
                    <button
                      v-if="selectedItem.type === 'confirmed'"
                      @click.stop="openClaimDialogForSingleFace(f.id)"
                      class="absolute bottom-1 right-[calc(100%+2px)] bg-blue-600 hover:bg-blue-700 text-white px-1.5 py-0.5 rounded text-[10px] opacity-0 group-hover:opacity-100 transition-opacity"
                    >
                      认领为
                    </button>
                  </div>
                  <div class="p-1.5">
                    <div
                      class="text-[10px] text-blue-300 truncate cursor-pointer"
                      :title="f.photoFilename"
                      @click.stop="openPhoto(f.photoId)"
                      @dblclick.stop="handleFaceDblClick(f)"
                    >
                      {{ f.photoFilename || '-' }}
                    </div>
                  </div>
                </div>
                <div
                  v-for="n in facePlaceholderCounts.similar"
                  :key="`similar-ph-${n}`"
                  class="h-40 rounded bg-gray-700/40 border border-gray-700/60 animate-pulse overflow-hidden relative"
                >
                  <div class="absolute top-0 left-0 right-0 h-32 bg-gray-600/50"></div>
                  <div class="absolute bottom-3 left-2 right-2 h-3 bg-gray-600/60 rounded"></div>
                </div>
                <!-- 框选遮罩 -->
                <div
                  v-if="isSelecting && currentTab === 'similar'"
                  class="absolute border-2 border-blue-500 bg-blue-500/20 pointer-events-none z-50"
                  :style="selectionBoxStyle"
                ></div>
              </div>
              <div v-if="similarFaces.length === 0" class="text-gray-400 text-xs text-center py-8">暂无相似推荐</div>
            </div>


            <!-- 未分配照片 -->
            <div v-if="tab === 'unassigned'">
              <div class="mb-2">
                <span class="text-xs text-gray-400">所有未分配的照片</span>
                <span v-if="loadingUnassignedFaces" class="ml-2 text-xs text-gray-400">加载中...</span>
              </div>
              <div v-if="unassignedLoadedOnce && !loadingUnassignedFaces && unassignedFaces.length === 0" class="text-gray-400 text-xs text-center py-8">
                暂无未分配照片
              </div>
              <div 
                v-show="unassignedFaces.length > 0"
                ref="unassignedContainer"
                class="grid grid-cols-3 md:grid-cols-4 xl:grid-cols-5 2xl:grid-cols-6 gap-2 pb-4 relative"
                @mousedown="handleMouseDown($event, 'unassigned')"
                @mousemove="handleMouseMove($event, 'unassigned')"
                @mouseup="handleMouseUp($event, 'unassigned')"
                @mouseleave="handleMouseUp($event, 'unassigned')"
              >
                <div
                  v-for="(f, index) in visibleUnassignedFaces"
                  :key="f.id"
                  :data-face-id="f.id"
                  :data-face-index="index"
                  class="bg-gray-700 rounded overflow-hidden border relative group select-none"
                  :class="selectedUnassigned.has(f.id) ? 'border-2 border-blue-500' : 'border-gray-600'"
                  @click="handleFaceClick($event, f.id, 'unassigned')"
                >
                  <div class="absolute top-1 right-1 bg-gray-600/80 px-1.5 py-0.5 rounded text-[10px] z-10">
                    <template v-if="f.similarity !== undefined && f.similarity !== null">
                      {{ ((f.similarity || 0) * 100).toFixed(0) }}%
                    </template>
                    <template v-else>
                      {{ ((f.confidence || 0) * 100).toFixed(0) }}%
                    </template>
                  </div>
                  <div class="relative h-32 bg-gray-800 overflow-hidden" @dblclick.stop="handleFaceDblClick(f)">
                    <img
                      v-if="getFaceThumb(f)"
                      :src="getFaceThumb(f)"
                      class="w-full h-full object-cover pointer-events-none"
                      :style="getFaceCropStyle(f)"
                      loading="lazy"
                    />
                    <button
                      v-if="selectedItem.type === 'confirmed'"
                      @click.stop="handleAssignClick(f.id, true)"
                      class="absolute bottom-1 right-1 bg-emerald-600 hover:bg-emerald-700 text-white px-1.5 py-0.5 rounded text-[10px] opacity-0 group-hover:opacity-100 transition-opacity"
                    >
                      认领
                    </button>
                    <button
                      v-if="selectedItem.type === 'confirmed'"
                      @click.stop="openClaimDialogForSingleFace(f.id)"
                      class="absolute bottom-1 right-[calc(100%+2px)] bg-blue-600 hover:bg-blue-700 text-white px-1.5 py-0.5 rounded text-[10px] opacity-0 group-hover:opacity-100 transition-opacity"
                    >
                      认领为
                    </button>
                  </div>
                  <div class="p-1.5">
                    <div
                      class="text-[10px] text-blue-300 truncate cursor-pointer"
                      :title="f.photoFilename"
                      @click.stop="openPhoto(f.photoId)"
                      @dblclick.stop="handleFaceDblClick(f)"
                    >
                      {{ f.photoFilename || '-' }}
                    </div>
                  </div>
                </div>
                <div
                  v-for="n in facePlaceholderCounts.unassigned"
                  :key="`unassigned-ph-${n}`"
                  class="h-40 rounded bg-gray-700/40 border border-gray-700/60 animate-pulse overflow-hidden relative"
                >
                  <div class="absolute top-0 left-0 right-0 h-32 bg-gray-600/50"></div>
                  <div class="absolute bottom-3 left-2 right-2 h-3 bg-gray-600/60 rounded"></div>
                </div>
                <!-- 框选遮罩 -->
                <div
                  v-if="isSelecting && currentTab === 'unassigned'"
                  class="absolute border-2 border-blue-500 bg-blue-500/20 pointer-events-none z-50"
                  :style="selectionBoxStyle"
                ></div>
              </div>
              <div v-if="unassignedFaces.length === 0" class="text-gray-400 text-xs text-center py-8">暂无未分配照片</div>
            </div>

            <!-- 聚类照片（未确认聚类） -->
            <div v-if="tab === 'confirmed' && selectedItem.type === 'cluster'">
              <div class="mb-2">
                <span class="text-xs text-gray-400">聚类中的人脸</span>
              </div>
              <div 
                ref="clusterContainer"
                class="grid grid-cols-3 md:grid-cols-4 xl:grid-cols-5 2xl:grid-cols-6 gap-2 pb-4 relative"
                @mousedown="handleMouseDown($event, 'cluster')"
                @mousemove="handleMouseMove($event, 'cluster')"
                @mouseup="handleMouseUp($event, 'cluster')"
                @mouseleave="handleMouseUp($event, 'cluster')"
              >
                <div
                  v-for="(f, index) in visibleClusterFaces"
                  :key="f.id"
                  :data-face-id="f.id"
                  :data-face-index="index"
                  class="bg-gray-700 rounded overflow-hidden border relative group select-none"
                  :class="selectedClusterFaces.has(f.id) ? 'border-2 border-blue-500' : 'border-gray-600'"
                  @click="handleFaceClick($event, f.id, 'cluster')"
                >
                  <div class="relative h-32 bg-gray-800 overflow-hidden" @dblclick.stop="handleFaceDblClick(f)">
                    <img
                      v-if="getFaceThumb(f)"
                      :src="getFaceThumb(f)"
                      class="w-full h-full object-cover pointer-events-none"
                      :style="getFaceCropStyle(f)"
                      loading="lazy"
                    />
                  </div>
                  <div class="p-1.5">
                    <div
                      class="text-[10px] text-blue-300 truncate cursor-pointer"
                      :title="f.photoFilename"
                      @click.stop="openPhoto(f.photoId)"
                      @dblclick.stop="handleFaceDblClick(f)"
                    >
                      {{ f.photoFilename || '-' }}
                    </div>
                  </div>
                </div>
                <div
                  v-for="n in facePlaceholderCounts.cluster"
                  :key="`cluster-ph-${n}`"
                  class="h-40 rounded bg-gray-700/40 border border-gray-700/60 animate-pulse overflow-hidden relative"
                >
                  <div class="absolute top-0 left-0 right-0 h-32 bg-gray-600/50"></div>
                  <div class="absolute bottom-3 left-2 right-2 h-3 bg-gray-600/60 rounded"></div>
                </div>
                <!-- 框选遮罩 -->
                <div
                  v-if="isSelecting && currentTab === 'cluster'"
                  class="absolute border-2 border-blue-500 bg-blue-500/20 pointer-events-none z-50"
                  :style="selectionBoxStyle"
                ></div>
              </div>
              <div v-if="personFaces.length === 0" class="text-gray-400 text-xs text-center py-8">暂无照片</div>
            </div>
          </div>
        </div>
      </div>
    </div>
  <PhotoViewer
    v-model:visible="viewerVisible"
    :photos="viewerPhotos"
    :start-index="viewerIndex"
    :auto-show-faces="true"
    :force-show-faces="tab === 'similar' || tab === 'unassigned'"
    :open-options="viewerOpenOptions"
    @viewer-index-change="onViewerIndexChange"
  />

  <!-- 删除人物确认对话框 -->
  <div v-if="deleteDialogVisible" class="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50" @click.self="deleteDialogVisible = false">
    <div class="bg-gray-800 rounded-lg p-6 max-w-md w-full mx-4">
      <h3 class="text-lg font-semibold text-white mb-4">删除人物</h3>
      <p class="text-gray-300 mb-6">
        确定要删除人物 <span class="font-semibold text-white">"{{ selectedItem?.name || '未命名' }}"</span> 吗？
      </p>

      <div class="space-y-3 mb-6">
        <div class="p-3 bg-amber-900/20 border border-amber-600/30 rounded">
          <div class="font-medium text-amber-400 mb-1">解散人物</div>
          <div class="text-sm text-gray-300">将所有关联人脸重新设为未分配状态，然后删除人物记录。</div>
        </div>

        <div class="p-3 bg-red-900/20 border border-red-600/30 rounded">
          <div class="font-medium text-red-400 mb-1">删除人物</div>
          <div class="text-sm text-gray-300">直接删除人物记录，人脸仍保持已分配状态但指向不存在的人物。</div>
        </div>
      </div>

      <div class="flex gap-3 justify-end">
        <button
          @click="deleteDialogVisible = false"
          class="px-4 py-2 bg-gray-600 hover:bg-gray-700 text-gray-200 rounded transition-colors"
        >
          取消
        </button>
        <button
          @click="confirmDissolvePerson"
          class="px-4 py-2 bg-amber-600 hover:bg-amber-700 text-white rounded transition-colors"
        >
          解散人物
        </button>
        <button
          @click="confirmDeletePerson"
          class="px-4 py-2 bg-red-600 hover:bg-red-700 text-white rounded transition-colors"
        >
          删除人物
        </button>
      </div>
    </div>
  </div>
  </div>

  <!-- 认领为弹窗 -->
  <div
    v-if="showClaimDialog"
    class="fixed inset-0 z-50 flex items-center justify-center"
    @click.self="closeClaimDialog"
  >
    <!-- 背景蒙版：只稍微暗一点，不模糊 -->
    <div class="absolute inset-0 bg-black/30"></div>
    
    <!-- 弹窗内容：毛玻璃效果 -->
    <div class="relative bg-gray-800/80 backdrop-blur-xl rounded-lg shadow-xl w-[90vw] max-w-4xl h-[80vh] max-h-[800px] flex flex-col border border-gray-700/50">
      <!-- 标题栏 -->
      <div class="flex items-center justify-between p-4 border-b border-gray-700/50">
        <h2 class="text-lg font-medium text-gray-100">认领为</h2>
        <button
          @click="closeClaimDialog"
          class="text-gray-300 hover:text-white transition-colors"
        >
          <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"></path>
          </svg>
        </button>
      </div>

      <!-- 搜索框和操作按钮 -->
      <div class="p-4 border-b border-gray-700/50 flex items-center gap-2">
        <input
          ref="claimDialogSearchInput"
          v-model="claimDialogSearchKeyword"
          @input="filterClaimDialogPersons"
          @keyup.enter="handleClaimDialogEnter"
          placeholder="搜索人物名字..."
          class="flex-1 px-3 py-2 bg-gray-700/50 border border-gray-600/50 rounded text-sm text-gray-100 placeholder-gray-400 focus:outline-none focus:ring-1 focus:ring-blue-500 backdrop-blur-sm"
        />
        <button
          @click="handleCreatePersonFromClaimDialog"
          :disabled="!canCreatePersonFromClaimDialog"
          class="px-3 py-2 bg-green-600 hover:bg-green-700 text-white rounded text-sm transition-colors whitespace-nowrap disabled:opacity-50 disabled:cursor-not-allowed"
        >
          新建人物
        </button>
      </div>

      <!-- 人物列表 -->
      <div 
        class="flex-1 overflow-y-auto p-2"
      >
        <div v-if="loadingClaimDialogPersons" class="flex items-center justify-center h-full">
          <div class="h-8 w-8 rounded-full border-2 border-gray-300 border-t-transparent animate-spin"></div>
        </div>
        <div v-else-if="filteredClaimDialogPersons.length === 0" class="text-gray-300 text-center py-8">
          暂无人物
        </div>
        <div
          v-else
          class="grid grid-cols-3 sm:grid-cols-4 md:grid-cols-5 lg:grid-cols-6 xl:grid-cols-7 gap-2"
        >
          <div
            v-for="person in filteredClaimDialogPersons"
            :key="person.id"
            @click.stop="selectClaimPerson(person)"
            class="flex flex-col items-center p-1.5 rounded bg-gray-700/30 hover:bg-gray-700/50 cursor-pointer transition-colors border-2 backdrop-blur-sm"
            :class="selectedClaimPersonId === person.id
              ? 'border-white bg-white/20' 
              : 'border-gray-600/50 hover:border-gray-500/50'"
          >
            <div class="w-12 h-12 rounded-full bg-gray-600 overflow-hidden mb-1 relative">
              <img
                v-if="getPersonThumb(person)"
                :src="getPersonThumb(person)"
                class="w-full h-full object-cover"
                :class="selectedClaimPersonId === person.id ? 'brightness-110' : ''"
              />
              <!-- 选中标记 -->
              <div v-if="selectedClaimPersonId === person.id" class="absolute inset-0 flex items-center justify-center bg-white/20">
                <svg class="w-6 h-6 text-white" fill="currentColor" viewBox="0 0 20 20">
                  <path fill-rule="evenodd" d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z" clip-rule="evenodd"></path>
                </svg>
              </div>
            </div>
            <div class="text-center w-full">
              <div 
                class="text-xs truncate leading-tight"
                :class="selectedClaimPersonId === person.id ? 'text-white font-medium' : 'text-gray-200'"
                :title="person.name || '未命名'"
              >
                {{ person.name || '未命名' }} <span :class="selectedClaimPersonId === person.id ? 'text-gray-200' : 'text-gray-400'">({{ person.faceCount || 0 }})</span>
              </div>
              <div v-if="person.similarity !== undefined" class="text-[10px] text-blue-300 mt-0.5">
                {{ (person.similarity * 100).toFixed(0) }}%
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 底部操作栏 -->
      <div class="p-4 border-t border-gray-700/50">
        <div class="text-sm text-gray-300 text-center">
          点击人物卡片即可直接认领人脸 | 按回车键确认选择
        </div>
      </div>
    </div>
  </div>

  <!-- 人物右键菜单（毛玻璃） -->
  <teleport to="body">
    <div
      v-if="personContextMenu.show"
      class="fixed inset-0 z-50"
      @click="closePersonContextMenu"
      @contextmenu.prevent="closePersonContextMenu"
    >
      <div
        class="absolute person-glass-menu rounded-lg shadow-2xl z-10 w-44"
        :style="{ left: personContextMenu.x + 'px', top: personContextMenu.y + 'px' }"
        @click.stop
      >
        <div class="py-1">
          <button
            @click="togglePersonHidden(personContextMenu.person!)"
            class="w-full text-left px-4 py-2 text-sm text-gray-300 hover:bg-white/10 flex items-center gap-2 transition-colors"
          >
            <svg v-if="personContextMenu.person?.hidden" class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z"/><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z"/></svg>
            <svg v-else class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13.875 18.825A10.05 10.05 0 0112 19c-4.478 0-8.268-2.943-9.543-7a9.97 9.97 0 011.563-3.029m5.858.908a3 3 0 114.243 4.243M9.878 9.878l4.242 4.242M9.88 9.88l-3.29-3.29m7.532 7.532l3.29 3.29M3 3l3.59 3.59m0 0A9.953 9.953 0 0112 5c4.478 0 8.268 2.943 9.543 7a10.025 10.025 0 01-4.132 5.411m0 0L21 21"/></svg>
            {{ personContextMenu.person?.hidden ? '取消隐藏' : '隐藏' }}
          </button>
          <button
            @click="() => { selectPerson(personContextMenu.person!); closePersonContextMenu(); showDeleteDialog() }"
            class="w-full text-left px-4 py-2 text-sm text-red-400 hover:bg-white/10 flex items-center gap-2 transition-colors"
          >
            <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"/></svg>
            删除
          </button>
        </div>
      </div>
    </div>
  </teleport>
</template>

<script setup lang="ts">
import { ref, onMounted, watch, computed, nextTick, onBeforeUnmount, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { api, personApi } from '@/api'
import { usePhotoStore } from '@/stores/photo'
import PhotoViewer from '@/components/PhotoViewer.vue'

const router = useRouter()
const photoStore = usePhotoStore()

interface PersonListItem {
  type: 'confirmed' | 'cluster'
  id: number
  name?: string
  description?: string
  faceCount?: number
  hidden?: boolean
  sampleThumbnailPath?: string
  sampleOriginalPath?: string
  samplePhotoId?: number
  sampleFaceId?: number
  sampleConfidence?: number
  avgConfidence?: number
}

interface FaceItem {
  id: number
  photoId?: number
  photoFilename?: string
  photoThumbnailPath?: string
  photoOriginalPath?: string
  x?: number
  y?: number
  width?: number
  height?: number
  personId?: number
  personName?: string
  similarity?: number
  isConfirmed?: boolean
}

interface AlbumRecommendation {
  albumId: number
  albumName: string
  albumPath: string
  photoCount: number
  similarFaceCount: number
  similarFaces?: FaceItem[]
  albumPhotos?: any[] // 相册中的所有图片（包括无脸图片）
  takenAt?: string
  claimedPhotoCount?: number // 已认领的图片数量
}

const STORAGE_KEY = 'pe-persons-left-width'

const persons = ref<PersonListItem[]>([])
const confirmedPersons = ref<PersonListItem[]>([])
const clusterPersons = ref<PersonListItem[]>([])
const personKeyword = ref('')
const selectedItem = ref<PersonListItem | null>(null)

const personContextMenu = ref({
  show: false,
  x: 0,
  y: 0,
  person: null as PersonListItem | null
})

// 聚类分页
const clusterPage = ref(0)
const clusterPageSize = ref(80) // 每次加载80个聚类
const hasMoreClusters = ref(true)
const loadingClusters = ref(false)
const selectedPersonId = ref<number | null>(null)
const selectedClusterIndex = ref<number | null>(null)
const loadingPersons = ref(false)
// 各tab的独立加载状态
const loadingConfirmedFaces = ref(false)
const loadingSimilarFaces = ref(false)
const loadingUnassignedFaces = ref(false)
const unassignedLoadedOnce = ref(false) // 标记是否已手动加载过未分配照片

// tab内容区loading蒙版：只遮挡当前tab内容，不影响其他tab切换
const showTabLoadingOverlay = ref(false)
let tabLoadingOverlayTimer: number | null = null

// 打开大图时的loading：只展示，不阻塞tab切换（pointer-events-none）
const showViewerLoadingOverlay = ref(false)
// 取消令牌，用于取消之前的请求
let abortController: AbortController | null = null
const CLUSTER_THRESHOLD_KEY = 'pe-cluster-threshold'
const DEFAULT_CLUSTER_THRESHOLD = 0.7
const clusterThreshold = ref<number>(parseFloat(localStorage.getItem(CLUSTER_THRESHOLD_KEY) || `${DEFAULT_CLUSTER_THRESHOLD}`))
const snapPoints = computed(() => {
  const arr: number[] = []
  for (let v = 0.1; v <= 0.9001; v += 0.1) {
    arr.push(parseFloat(v.toFixed(2)))
  }
  return arr
})
// 删除对话框状态
const deleteDialogVisible = ref(false)

// 认领为弹窗相关
const showClaimDialog = ref(false)
const claimDialogSearchKeyword = ref('')
const claimDialogPersons = ref<PersonListItem[]>([])
const filteredClaimDialogPersons = ref<PersonListItem[]>([])
const loadingClaimDialogPersons = ref(false)
const selectedClaimPersonId = ref<number | null>(null)
const claimDialogSearchInput = ref<HTMLInputElement | null>(null)
const claimDialogSourceTab = ref<'cluster' | 'unassigned' | null>(null) // 记录弹窗来源tab

// 是否可以使用新建人物按钮
const canCreatePersonFromClaimDialog = computed(() => {
  const keyword = claimDialogSearchKeyword.value.trim().toLowerCase()
  if (!keyword) return false
  // 检查是否有同名人物（不区分大小写）
  const hasSameName = claimDialogPersons.value.some(
    p => (p.name || '').toLowerCase() === keyword
  )
  return !hasSameName
})

// 聚类阈值输入处理
const handleThresholdInput = (event: Event) => {
  const target = event.target as HTMLInputElement
  const value = parseFloat(target.value)
  if (!isNaN(value)) {
    clusterThreshold.value = value
  }
}

let thresholdTimer: number | null = null

// 左侧人物列表（直接显示所有已加载的人物，无分页）
const visibleConfirmedPersons = computed(() => persons.value.filter(p => p.type === 'confirmed'))
const visibleClusterPersons = computed(() => persons.value.filter(p => p.type === 'cluster'))

// 编辑相关（列表不再内联编辑，仅右侧姓名输入框使用）
const editingPersonId = ref<number | null>(null)
const editingName = ref('')
const originalName = ref('')
const editingDescription = ref('')
const originalDescription = ref('')
const savingPerson = ref(false)

// 相似人物数据存储
const similarPersonsData = ref([])

// 相似人物推荐（仅限聚类人物）
const similarPersons = computed(() => {
  if (!selectedItem.value || selectedItem.value.type !== 'cluster') return []

  const currentName = selectedPersonName.value.trim().toLowerCase()

  if (!currentName) {
    // 没输入名字，显示所有相似度>=30%的推荐人物
    return similarPersonsData.value.filter(person => person.similarity >= 0.3)
  } else {
    // 输入了名字，显示包含该名字的人物，按相似度排序（不限制最低相似度）
    return similarPersonsData.value
      .filter(person => (person.name || '').toLowerCase().includes(currentName))
      .sort((a, b) => b.similarity - a.similarity)
      .slice(0, 5)
  }
})
const personListContainer = ref<HTMLElement | null>(null)

// 面板宽度和拖拽
const leftPanelWidth = ref(parseInt(localStorage.getItem(STORAGE_KEY) || '280', 10))
const isResizing = ref(false)
const resizeStartX = ref(0)
const resizeStartWidth = ref(0)
const containerWidth = ref(0)

// 相册区域宽度管理
const ALBUMS_STORAGE_KEY = 'pe-persons-albums-width'
const albumsPanelWidth = ref(parseInt(localStorage.getItem(ALBUMS_STORAGE_KEY) || '250', 10))
const isResizingAlbums = ref(false)

// 列数自适应（优化密度，强制适应容器宽度）
const personColumns = computed(() => {
  // 使用更保守的计算：假设每个头像约48px宽 + 8px间距
  // 减去更大的缓冲确保不超出边界
  const effectiveWidth = containerWidth.value - 80
  if (effectiveWidth <= 0) return 2

  // 计算理论最大列数（48px头像 + 8px间距）
  const theoreticalMax = Math.floor(effectiveWidth / 56) // 48 + 8 = 56px

  // 限制在合理范围内，最小2列，最大6列
  return Math.max(2, Math.min(6, theoreticalMax))
})

const tab = ref<'confirmed' | 'auto' | 'similar' | 'albums' | 'unassigned'>('confirmed')

// 说明：tab 切换的加载逻辑在下方统一处理（按需加载/避免重复请求）

// 已确认照片
const confirmedFaces = ref<FaceItem[]>([])
const selectedConfirmed = ref<Set<number>>(new Set())
const loadingConfirmed = ref(false)

// 直接指派的照片（无相似度）
const assignedPhotos = ref<any[]>([])
const selectedAssignedPhotos = ref<Set<number>>(new Set())
const loadingAssignedPhotos = ref(false)

// 自动分配照片
const autoAssignedFaces = ref<FaceItem[]>([])
const selectedAuto = ref<Set<number>>(new Set())
const loadingAuto = ref(false)

// 相似推荐（合并了原来的相似推荐和套图推荐）
const similarFaces = ref<FaceItem[]>([])
const selectedSimilar = ref<Set<number>>(new Set())
const loadingSimilar = ref(false)

// 套图推荐
const albumRecommendations = ref<AlbumRecommendation[]>([])
const selectedAlbum = ref<AlbumRecommendation | null>(null)
const selectedAlbumFaces = ref<Set<number>>(new Set())
const loadingAlbums = ref(false)
// abort controller for album image loads
let albumAbortController: AbortController | null = null
// album grid responsiveness (handled by CSS grid breakpoints)
const albumContainer = ref<HTMLElement | null>(null)
const albumContainerWidth = ref(0)
const albumColumns = computed(() => {
  const minCol = 120
  const maxCols = 6
  let w = albumContainerWidth.value || 0
  // try to derive width from DOM if observer hasn't run yet
  if (!w && albumContainer.value) {
    w = albumContainer.value.clientWidth || (albumContainer.value.parentElement ? albumContainer.value.parentElement.clientWidth : 0)
  }
  // fallback to window width portion if still unknown
  if (!w && typeof window !== 'undefined') {
    w = Math.floor(window.innerWidth * 0.6)
  }
  const fit = Math.floor(w / minCol) || 1
  const cols = Math.min(maxCols, fit)
  return Math.max(1, cols)
})

const updateAlbumContainerWidth = () => {
  if (albumContainer.value) albumContainerWidth.value = albumContainer.value.clientWidth
}
const setAlbumMaxHeight = () => {
  if (!albumContainer.value) return
  const rect = albumContainer.value.getBoundingClientRect()
  const viewportH = window.innerHeight
  // leave space for top paddings/headers (~180px)
  // Use a simpler, stable max-height based on viewport to avoid layout collapse
  const available = Math.max(200, viewportH - 180)
  albumContainer.value.style.maxHeight = `calc(100vh - 180px)`
  albumContainer.value.style.overflowY = 'auto'
}

// 未分配照片
const unassignedFaces = ref<FaceItem[]>([])
const selectedUnassigned = ref<Set<number>>(new Set())
const loadingUnassigned = ref(false)

// 聚类照片（用于未确认聚类）
const personFaces = ref<FaceItem[]>([])
const selectedClusterFaces = ref<Set<number>>(new Set())
const loadingPersonFaces = ref(false)

type FaceTab = 'confirmed' | 'similar' | 'albums' | 'unassigned' | 'cluster'
const FACE_ROWS_PER_PAGE = 3
const facePageSize = ref(0)
const faceVisibleLimits = reactive<Record<FaceTab, number>>({
  confirmed: 0,
  similar: 0,
  albums: 0,
  unassigned: 0,
  cluster: 0
})
const facePlaceholderCounts = reactive<Record<FaceTab, number>>({
  confirmed: 0,
  similar: 0,
  albums: 0,
  unassigned: 0,
  cluster: 0
})
const visibleFacesMap: Record<FaceTab, Ref<FaceItem[]>> = {
  confirmed: ref<FaceItem[]>([]),
  similar: ref<FaceItem[]>([]),
  albums: ref<FaceItem[]>([]),
  unassigned: ref<FaceItem[]>([]),
  cluster: ref<FaceItem[]>([])
}
const visibleConfirmedFaces = computed(() => visibleFacesMap.confirmed.value)
const visibleSimilarFaces = computed(() => visibleFacesMap.similar.value)
const visibleAlbumFaces = computed(() => visibleFacesMap.albums.value)
const visibleUnassignedFaces = computed(() => visibleFacesMap.unassigned.value)
const visibleClusterFaces = computed(() => visibleFacesMap.cluster.value)

// 图片预览（复用通用 PhotoViewer）
const viewerVisible = ref(false)
const viewerPhotos = ref<any[]>([])
const viewerIndex = ref(0)

// 键盘导航：记录当前激活的人脸索引
const activeFaceIndex = ref<number | null>(null)

// 右侧姓名编辑
const selectedPersonName = ref('')
const originalSelectedPersonName = ref('')

const getClientX = (e: MouseEvent | TouchEvent): number => {
  if ('touches' in e) {
    return e.touches[0]?.clientX ?? (e as TouchEvent).changedTouches[0]?.clientX ?? 0
  }
  return (e as MouseEvent).clientX
}

const startResize = (e: MouseEvent | TouchEvent) => {
  isResizing.value = true
  resizeStartX.value = getClientX(e)
  resizeStartWidth.value = leftPanelWidth.value
  updateContainerWidth()
  document.body.style.userSelect = 'none'
  document.body.style.cursor = 'col-resize'
  document.addEventListener('mousemove', handleResize)
  document.addEventListener('mouseup', stopResize)
  document.addEventListener('touchmove', handleResize, { passive: false })
  document.addEventListener('touchend', stopResize)
  document.addEventListener('touchcancel', stopResize)
  e.preventDefault()
}

const handleResize = (e: MouseEvent | TouchEvent) => {
  if (!isResizing.value) return
  e.preventDefault()
  const diff = getClientX(e) - resizeStartX.value
  const newWidth = Math.max(200, Math.min(500, resizeStartWidth.value + diff))
  leftPanelWidth.value = newWidth
  updateContainerWidth()
}

const stopResize = () => {
  isResizing.value = false
  localStorage.setItem(STORAGE_KEY, String(leftPanelWidth.value))
  document.body.style.userSelect = ''
  document.body.style.cursor = ''
  document.removeEventListener('mousemove', handleResize)
  document.removeEventListener('mouseup', stopResize)
  document.removeEventListener('touchmove', handleResize)
  document.removeEventListener('touchend', stopResize)
  document.removeEventListener('touchcancel', stopResize)
}

// 相册区域分割线拖拽
const startResizeAlbums = (e: MouseEvent | TouchEvent) => {
  isResizingAlbums.value = true
  resizeStartX.value = getClientX(e)
  resizeStartWidth.value = albumsPanelWidth.value
  document.body.style.userSelect = 'none'
  document.body.style.cursor = 'col-resize'
  document.addEventListener('mousemove', handleResizeAlbums)
  document.addEventListener('mouseup', stopResizeAlbums)
  document.addEventListener('touchmove', handleResizeAlbums, { passive: false })
  document.addEventListener('touchend', stopResizeAlbums)
  document.addEventListener('touchcancel', stopResizeAlbums)
  e.preventDefault()
}

const handleResizeAlbums = (e: MouseEvent | TouchEvent) => {
  if (!isResizingAlbums.value) return
  e.preventDefault()
  const diff = getClientX(e) - resizeStartX.value
  const newWidth = Math.max(150, Math.min(400, resizeStartWidth.value + diff))
  albumsPanelWidth.value = newWidth
}

const stopResizeAlbums = () => {
  isResizingAlbums.value = false
  localStorage.setItem(ALBUMS_STORAGE_KEY, String(albumsPanelWidth.value))
  document.body.style.userSelect = ''
  document.body.style.cursor = ''
  document.removeEventListener('mousemove', handleResizeAlbums)
  document.removeEventListener('mouseup', stopResizeAlbums)
  document.removeEventListener('touchmove', handleResizeAlbums)
  document.removeEventListener('touchend', stopResizeAlbums)
  document.removeEventListener('touchcancel', stopResizeAlbums)
}

const updateContainerWidth = () => {
  if (personListContainer.value) {
    containerWidth.value = personListContainer.value.clientWidth
  }
}

// 处理人物列表滚动，触发聚类加载
const handlePersonScroll = (e: Event) => {
  const el = e.target as HTMLElement
  if (!el) return

  // 检查是否滚动到接近底部
  const scrollTop = el.scrollTop
  const scrollHeight = el.scrollHeight
  const clientHeight = el.clientHeight
  const nearBottom = scrollTop + clientHeight >= scrollHeight - 150 // 150px 缓冲区

  if (nearBottom && hasMoreClusters.value && !loadingClusters.value) {
    loadMoreClusters()
  }
}

// 检查是否需要加载更多聚类（仅在页面初始化时预加载少量数据）
const checkLoadMoreClusters = () => {
  if (!hasMoreClusters.value || loadingClusters.value) return

  // 只在刚进入页面时预加载第二页，避免无限循环
  // 这个方法现在只在初始化时调用一次，不再递归
  const currentLoadedClusters = clusterPersons.value.length
  const shouldPreloadNextPage = clusterPage.value === 0 && currentLoadedClusters >= clusterPageSize.value - 10

  if (shouldPreloadNextPage) {
    loadMoreClusters()
  }
}

const currentFaceTab = computed<FaceTab>(() => {
  if (selectedItem.value?.type === 'cluster') return 'cluster'
  return (tab.value as FaceTab) || 'confirmed'
})

const currentTabLoadingRaw = computed(() => {
  if (!selectedItem.value) return false
  const t = currentFaceTab.value
  if (t === 'cluster') return loadingPersonFaces.value
  if (t === 'confirmed') return loadingConfirmedFaces.value || loadingAssignedPhotos.value
  if (t === 'similar') return loadingSimilarFaces.value
  if (t === 'albums') return loadingAlbums.value
  if (t === 'unassigned') return loadingUnassignedFaces.value
  return false
})

const handleFaceScroll = (e: Event) => {
  const el = e.target as HTMLElement
  const t = currentFaceTab.value
  if (!t || (facePlaceholderCounts[t] || 0) <= 0) return
  const nearBottom = el.scrollTop + el.clientHeight >= el.scrollHeight - 80
  if (nearBottom) {
    ensureMoreFaces(t)
  }
}

const handleFaceListKeydown = (e: KeyboardEvent) => {
  // PhotoViewer 打开时，优先交给 PhotoViewer 自己处理
  if (viewerVisible.value) return
  if (!selectedItem.value || loadingPersons.value || loadingPersonFaces.value) return

  const faces = getCurrentVisibleFaceList()
  if (!faces.length) return

  const key = e.key

  // Ctrl/Cmd + A: 全选当前标签页人脸
  if ((e.ctrlKey || e.metaKey) && key.toLowerCase() === 'a') {
    e.preventDefault()
    selectAllCurrentTab()
    return
  }

  // Esc: 清除选择
  if (key === 'Escape') {
    e.preventDefault()
    const sel = getCurrentSelection(currentFaceTab.value)
    sel.value = new Set()
    activeFaceIndex.value = null
    return
  }

  // 方向键：在网格中移动激活/选中人脸
  if (key === 'ArrowLeft' || key === 'ArrowRight' || key === 'ArrowUp' || key === 'ArrowDown') {
    e.preventDefault()
    let idx = activeFaceIndex.value ?? 0

    // 估算列数，用于上下移动（与分页估算一致）
    const width = tabScrollContainer.value?.clientWidth || 960
    const approxCardWidth = 160
    const cols = Math.max(2, Math.floor(width / approxCardWidth))

    if (key === 'ArrowLeft') idx = Math.max(0, idx - 1)
    if (key === 'ArrowRight') idx = Math.min(faces.length - 1, idx + 1)
    if (key === 'ArrowUp') idx = Math.max(0, idx - cols)
    if (key === 'ArrowDown') idx = Math.min(faces.length - 1, idx + cols)

    activeFaceIndex.value = idx
    const face = faces[idx]
    if (!face) return

    // 更新选中集合：仅选中当前激活的人脸
    const sel = getCurrentSelection(currentFaceTab.value)
    sel.value = new Set([face.id])

    // 同步 lastSelectedIndex，使用完整列表中的索引
    const fullList = getCurrentFaceList(currentFaceTab.value)
    lastSelectedIndex.value = fullList.findIndex(f => f.id === face.id)
  }
}

const loadPersons = async (options?: { restoreClusterPages?: number }) => {
  const targetClusterPages = options?.restoreClusterPages ?? 1
  loadingPersons.value = true
  try {
    // 一次性加载已确认人物和聚类人物，避免重复API调用
    const confirmedRes = await api.get('/admin/persons/items', {
      params: { threshold: clusterThreshold.value, clusterPage: 0, clusterSize: clusterPageSize.value }
    })
    let list: PersonListItem[] = confirmedRes.data || []

    // 过滤出已确认人物，按确认照片总数降序排序
    confirmedPersons.value = list.filter(p => p.type === 'confirmed')
      .sort((a, b) => (b.faceCount || 0) - (a.faceCount || 0))

    // 过滤出聚类人物
    clusterPersons.value = list.filter(p => p.type === 'cluster')

    // 检查是否还有更多聚类数据
    if (clusterPersons.value.length < clusterPageSize.value) {
      hasMoreClusters.value = false
    } else {
      clusterPage.value = 1 // 下一页从1开始
      hasMoreClusters.value = true
    }

    // 恢复之前已加载的聚类分页（用于创建/编辑后保持滚动位置）
    while (clusterPage.value < targetClusterPages && hasMoreClusters.value) {
      const res = await api.get('/admin/persons/items', {
        params: { threshold: clusterThreshold.value, clusterPage: clusterPage.value, clusterSize: clusterPageSize.value }
      })
      const newClusters: PersonListItem[] = (res.data || []).filter((p: PersonListItem) => p.type === 'cluster')
      if (newClusters.length < clusterPageSize.value) {
        hasMoreClusters.value = false
      }
      clusterPersons.value = [...clusterPersons.value, ...newClusters]
      clusterPage.value++
    }

    // 合并所有人物
    persons.value = [...confirmedPersons.value, ...clusterPersons.value]

    if (personKeyword.value.trim()) {
      const kw = personKeyword.value.trim().toLowerCase()
      persons.value = persons.value.filter(p => (p.name || '').toLowerCase().includes(kw))
    }

    if (persons.value.length && !selectedItem.value) {
      selectPerson(persons.value[0])
    }
  } finally {
    loadingPersons.value = false
  }
}

const loadMoreClusters = async () => {
  if (loadingClusters.value || !hasMoreClusters.value) return

  loadingClusters.value = true
  try {
    const res = await api.get('/admin/persons/items', {
      params: {
        threshold: clusterThreshold.value,
        clusterPage: clusterPage.value,
        clusterSize: clusterPageSize.value
      }
    })

    const newClusters: PersonListItem[] = (res.data || []).filter((p: PersonListItem) => p.type === 'cluster')

    if (newClusters.length < clusterPageSize.value) {
      hasMoreClusters.value = false
    }

    clusterPersons.value = [...clusterPersons.value, ...newClusters]
    clusterPage.value++

    // 更新总人物列表
    persons.value = [...confirmedPersons.value, ...clusterPersons.value]

    if (personKeyword.value.trim()) {
      const kw = personKeyword.value.trim().toLowerCase()
      persons.value = persons.value.filter(p => (p.name || '').toLowerCase().includes(kw))
    }

    // 注意：不再自动检查加载更多，由滚动事件触发

  } finally {
    loadingClusters.value = false
  }
}

const isSelected = (p: PersonListItem) => {
  if (!selectedItem.value) return false
  return selectedItem.value.type === p.type && selectedItem.value.id === p.id
}

const selectPerson = (p: PersonListItem) => {
  // 取消之前的请求
  if (abortController) {
    abortController.abort()
  }
  abortController = new AbortController()

  selectedItem.value = p
  // 切换人物时清空未分配tab的数据，避免遗留上一个人物的数据
  unassignedFaces.value = []
  unassignedLoadedOnce.value = false
  selectedUnassigned.value.clear()
  if (p.type === 'confirmed') {
    selectedPersonId.value = p.id
    selectedClusterIndex.value = null
    selectedPersonName.value = p.name || '未命名'
    originalSelectedPersonName.value = selectedPersonName.value
    editingDescription.value = p.description || ''
    originalDescription.value = p.description || ''
    // 切换人物时清空套图推荐的数据
    selectedAlbum.value = null
    albumRecommendations.value = []
    selectedAlbumFaces.value.clear()
    // 智能选择初始tab：优先显示有内容的tab
    tab.value = 'confirmed' // 默认先显示confirmed，加载后再根据数据调整
    loadAllFaces(abortController.signal)

    // 切换人物时自动加载套图推荐的相册列表（不加载图片）
    loadAlbumRecommendations(abortController.signal, false)
  } else {
    selectedPersonId.value = null
    selectedClusterIndex.value = p.id as number
    selectedPersonName.value = ''
    originalSelectedPersonName.value = ''
    editingDescription.value = ''
    originalDescription.value = ''
    tab.value = 'confirmed'
    loadClusterFaces(abortController.signal)
    // 加载相似人物推荐
    loadSimilarPersonsForCluster(abortController.signal)
  }
}

const loadAllFaces = async (signal?: AbortSignal) => {
  if (!selectedPersonId.value) return

  // 在开始加载前，记录当前的数据状态（用于保持显示）
  const previousData = {
    confirmedFaces: [...confirmedFaces.value],
    assignedPhotos: [...assignedPhotos.value],
    similarFaces: [...similarFaces.value],
    unassignedFaces: [...unassignedFaces.value]
  }

  try {
  // 先加载已确认照片，然后根据结果决定是否切换tab
    await loadConfirmedFaces(signal, false)
    if (signal?.aborted) {
      // 如果被取消，恢复之前的数据
      confirmedFaces.value = previousData.confirmedFaces
      return
    }

    // 已认领tab希望“立刻跟着变”：confirmed 数据一到就先刷新可见列表（预加载2页）
    selectedConfirmed.value.clear()
    resetFaceVisible('confirmed', 2)

    // 关键：让浏览器有机会先完成一次渲染/图片请求派发。
    // 否则在 confirmed 很快返回、但后续几个 tab 的大响应/处理紧随其后时，
    // 主线程可能长时间忙于解析/处理，导致用户感知为“先只出两张固定图片，等其它 tab 结束才全部出现”。
    await nextTick()
    await new Promise<void>(resolve => requestAnimationFrame(() => resolve()))

    // 加载其他数据（不清除选择状态和可见状态）
    // 跳过自动分配数据加载，直接到已确认状态
    // 注意：不加载未分配数据，由用户首次切换到该tab时按需加载
    await Promise.all([
      loadAssignedPhotos(signal, false),
      loadSimilarFaces(signal, false)
    ])

    if (signal?.aborted) {
      // 如果被取消，恢复之前的数据
      confirmedFaces.value = previousData.confirmedFaces
      assignedPhotos.value = previousData.assignedPhotos
      similarFaces.value = previousData.similarFaces
      unassignedFaces.value = previousData.unassignedFaces
      return
    }

    // 所有数据加载完成后，统一更新UI状态
    // confirmed 已在前面立即刷新过，这里只需要确保其它tab刷新
    selectedSimilar.value.clear()
    resetFaceVisible('similar')
    selectedUnassigned.value.clear()
    resetFaceVisible('unassigned')

    // 智能选择初始显示的tab：优先显示有内容的tab
    // 但如果当前就在相似推荐或套图推荐tab，则保持不变（避免认领后自动跳转）
    nextTick(() => {
      if (tab.value === 'similar' || tab.value === 'albums') {
        // 如果当前在相似推荐或套图推荐tab，保持不变
        return
      }

      if (confirmedFaces.value.length > 0 || assignedPhotos.value.length > 0) {
        tab.value = 'confirmed'
      } else if (similarFaces.value.length > 0) {
        tab.value = 'similar'
      } else {
        tab.value = 'unassigned' // 如果都没有内容，显示未分配tab
      }
    })
  } catch (error) {
    if (error.name !== 'AbortError') {
      console.error('加载人脸失败:', error)
      // 出错时恢复之前的数据
      confirmedFaces.value = previousData.confirmedFaces
      assignedPhotos.value = previousData.assignedPhotos
      similarFaces.value = previousData.similarFaces
      unassignedFaces.value = previousData.unassignedFaces
    }
  } finally {
    // individual loading flags are managed inside each loader
  }
}

// 人脸认领 / 移除后，刷新左侧人物数量并保持当前选中与页码
const refreshPersonsAfterFaceChange = async () => {
  const current = selectedItem.value
    ? { id: selectedItem.value.id, type: selectedItem.value.type }
    : null

  // 保存滚动位置和已加载的聚类页数
  const savedScrollTop = personListContainer.value?.scrollTop ?? 0
  const savedClusterPage = clusterPage.value

  await loadPersons({ restoreClusterPages: savedClusterPage })

  if (current) {
    const found = persons.value.find(
      (p) => p.id === current.id && p.type === current.type
    )
    if (found) {
      selectedItem.value = found
      if (found.type === 'confirmed') {
        selectedPersonId.value = found.id
        selectedClusterIndex.value = null
      } else {
        selectedPersonId.value = null
        selectedClusterIndex.value = found.id as number
      }
    }
  }

  // 恢复滚动位置
  await nextTick()
  if (personListContainer.value) {
    personListContainer.value.scrollTop = savedScrollTop
  }
}

const loadConfirmedFaces = async (signal?: AbortSignal, clearData = true) => {
  if (!selectedPersonId.value) return
  loadingConfirmedFaces.value = true
  try {
    const res = await api.get(`/admin/persons/${selectedPersonId.value}/faces/confirmed`, {
      params: { page: 0, size: 10000 }, // 增加到10000确保获取所有数据
      signal
    })
    if (signal?.aborted) return

    confirmedFaces.value = res.data.content || res.data || []
    if (clearData) {
    selectedConfirmed.value.clear()
    resetFaceVisible('confirmed')
    }
  } catch (error) {
    if (error.name !== 'AbortError') {
      console.error('加载已确认人脸失败:', error)
    }
    throw error
  } finally {
    loadingConfirmedFaces.value = false
  }
}

const loadAssignedPhotos = async (signal?: AbortSignal, clearData = true) => {
  if (!selectedPersonId.value) return
  try {
    const res = await api.get(`/admin/persons/${selectedPersonId.value}/assigned-photos`, {
      params: { page: 0, size: 5000 }, // 设置足够大的分页大小来获取所有数据
      signal
    })
    if (signal?.aborted) return

    assignedPhotos.value = res.data.content || res.data || []
    if (clearData) {
      selectedAssignedPhotos.value.clear()
    }
  } catch (error) {
    if (error.name !== 'AbortError') {
      console.error('加载已指派照片失败:', error)
    }
    throw error
  }
}

const loadAutoAssignedFaces = async (signal?: AbortSignal, clearData = true) => {
  if (!selectedPersonId.value) return
  try {
    const res = await api.get(`/admin/persons/${selectedPersonId.value}/faces/auto-assigned`, {
      params: { page: 0, size: 200 },
      signal
    })
    if (signal?.aborted) return

    autoAssignedFaces.value = res.data.content || res.data || []
    if (clearData) {
    selectedAuto.value.clear()
    resetFaceVisible('auto')
    }
  } catch (error) {
    if (error.name !== 'AbortError') {
      console.error('加载自动分配人脸失败:', error)
    }
  }
}

const loadAlbumRecommendations = async (signal?: AbortSignal, keepSelection = false) => {
  if (!selectedPersonId.value) return
  try {
    loadingAlbums.value = true
    const res = await api.get(`/admin/persons/${selectedPersonId.value}/album-recommendations`, { signal })

    if (signal?.aborted) return

    const currentAlbumId = keepSelection ? selectedAlbum.value?.albumId : null
    // 后端已返回 claimedPhotoCount，这里不要再重置为 0，避免触发额外的“逐相册再统计”逻辑
    albumRecommendations.value = (res.data || []).map((album: AlbumRecommendation) => ({
      ...album
    }))

    // 如果需要保持选择，尝试找到对应的相册
    if (keepSelection && currentAlbumId) {
      selectedAlbum.value = albumRecommendations.value.find(a => a.albumId === currentAlbumId) || null
    }

    // 如果没有选中相册，默认选择第一个
    if (!selectedAlbum.value && albumRecommendations.value.length > 0) {
      selectedAlbum.value = albumRecommendations.value[0]
    } else if (albumRecommendations.value.length === 0) {
      // 没有相册时清空选中与显示，避免显示旧数据
      selectedAlbum.value = null
      // 清空可见相册照片
      visibleFacesMap.albums.value = []
      faceVisibleLimits.albums = 0
      facePlaceholderCounts.albums = 0
    }

    // 加载选中相册的所有图片（包括无脸图片）
    if (selectedAlbum.value) {
      await loadAlbumPhotos(selectedAlbum.value.albumId, signal)
    }

    selectedAlbumFaces.value.clear()
  } catch (error) {
    if (signal?.aborted) return
    console.error('加载套图推荐失败:', error)
    albumRecommendations.value = []
  } finally {
    loadingAlbums.value = false
  }
}

// 加载相册中的所有图片（包括无脸图片）
const loadAlbumPhotos = async (albumId: number, signal?: AbortSignal) => {
  if (!selectedPersonId.value) return
  // cancel previous in-flight album load
  if (albumAbortController) {
    albumAbortController.abort()
    albumAbortController = null
  }
  albumAbortController = new AbortController()
  const acSignal = albumAbortController.signal
  try {
    // 获取相册中的所有图片
    const photosRes = await api.get(`/photos/album/${albumId}`, { params: { all: true }, signal: acSignal })
    // 获取相册中的相似人脸（用于匹配显示相似度）
    const facesRes = await api.get(`/admin/persons/${selectedPersonId.value}/albums/${albumId}/similar-faces`, { signal: acSignal })

    if (acSignal.aborted) return

    // 合并图片和人脸数据
    const photos = photosRes.data?.content || photosRes.data || []
    const faces = facesRes.data || []

    // 为每张图片关联其人脸数据（如果有的话）
    let photosWithFaces = photos
      .filter((photo: any) => photo.assignedPersonId !== selectedPersonId.value) // 过滤掉已经被当前人物认领的图片

    photosWithFaces = photosWithFaces.map((photo: any) => {
        const photoFaces = faces.filter((face: any) => face.photoId === photo.id)

        // 判断是否已被当前人物认领
        const isPhotoAssigned = photo.assignedPersonId === selectedPersonId.value
        const hasAssignedFaces = photoFaces.some((face: any) => face.personId === selectedPersonId.value)
        const isAssigned = isPhotoAssigned || hasAssignedFaces

        return {
          ...photo,
          faces: photoFaces,
          // 如果有相似人脸，取最高相似度的那个
          similarity: photoFaces.length > 0 ? Math.max(...photoFaces.map((f: any) => f.similarity || 0)) : 0,
          bestFace: photoFaces.length > 0 ? photoFaces.reduce((best: any, current: any) =>
            (current.similarity || 0) > (best.similarity || 0) ? current : best
          ) : null
        }
    })

    // 排序：已认领的排最后按拍摄时间，未认领的按相似度从高到低，相似度为0的按拍摄时间
    photosWithFaces.sort((a, b) => {
      // 判断是否已被当前人物认领
      const isAssignedA = (a.photoId && a.assignedPersonId === selectedPersonId.value) || (a.faces && a.faces.some((face: any) => face.personId === selectedPersonId.value))
      const isAssignedB = (b.photoId && b.assignedPersonId === selectedPersonId.value) || (b.faces && b.faces.some((face: any) => face.personId === selectedPersonId.value))

      // 已认领的排在最后
      if (isAssignedA !== isAssignedB) {
        return isAssignedA ? 1 : -1
      }

      // 已认领的图片之间按拍摄时间排序（新到旧）
      if (isAssignedA && isAssignedB) {
        const timeA = a.takenAt ? new Date(a.takenAt).getTime() : 0
        const timeB = b.takenAt ? new Date(b.takenAt).getTime() : 0
        return timeB - timeA
      }

      // 未认领的图片按相似度从高到低排序
      const similarityA = a.similarity || 0
      const similarityB = b.similarity || 0
      if (similarityA !== similarityB) {
        return similarityB - similarityA
      }

      // 相似度相同或都为0时，按拍摄时间从新到旧排序
      const timeA = a.takenAt ? new Date(a.takenAt).getTime() : 0
      const timeB = b.takenAt ? new Date(b.takenAt).getTime() : 0
      return timeB - timeA
    })

    // 计算已认领图片的数量
    const claimedPhotoCount = photosWithFaces.filter((photo: any) =>
      photo.assignedPersonId === selectedPersonId.value ||
      (photo.faces && photo.faces.some((face: any) => face.personId === selectedPersonId.value))
    ).length

    // 更新相册列表中的已认领图片数量
    const albumIndex = albumRecommendations.value.findIndex(a => a.albumId === albumId)
    if (albumIndex !== -1) {
      albumRecommendations.value[albumIndex].claimedPhotoCount = claimedPhotoCount
      albumRecommendations.value[albumIndex].albumPhotos = photosWithFaces
    }

    // 更新选中相册的数据
    if (selectedAlbum.value && selectedAlbum.value.albumId === albumId) {
      selectedAlbum.value.albumPhotos = photosWithFaces
      selectedAlbum.value.claimedPhotoCount = claimedPhotoCount
      // 数据更新后重置可见面部列表并调整容器高度
      nextTick(() => {
        resetFaceVisible('albums')
        setAlbumMaxHeight()
      })
    }
  } catch (error) {
    if (albumAbortController && albumAbortController.signal.aborted) return
    console.error('加载相册图片失败:', error)
  } finally {
    if (albumAbortController) {
      albumAbortController = null
    }
  }
}

const loadAlbumSimilarFaces = async (albumId: number, signal?: AbortSignal) => {
  if (!selectedPersonId.value) return
  // cancel previous in-flight album load
  if (albumAbortController) {
    albumAbortController.abort()
    albumAbortController = null
  }
  albumAbortController = new AbortController()
  const acSignal = albumAbortController.signal
  try {
    const res = await api.get(`/admin/persons/${selectedPersonId.value}/albums/${albumId}/similar-faces`, { signal: acSignal })
    if (acSignal.aborted) return

    // 更新选中相册的相似人脸数据（缓存）
    if (selectedAlbum.value && selectedAlbum.value.albumId === albumId) {
      selectedAlbum.value.similarFaces = res.data || []
      // 数据更新后重置可见面部列表并调整容器高度
      nextTick(() => {
        resetFaceVisible('albums')
        setAlbumMaxHeight()
      })
    }
  } catch (error) {
    if (albumAbortController && albumAbortController.signal.aborted) return
    console.error('加载相册相似人脸失败:', error)
  } finally {
    if (albumAbortController) {
      albumAbortController = null
    }
  }
}

// 说明：claimedPhotoCount 已由后端在 album-recommendations 接口返回。
// 过去这里会对每个相册并发再请求一次 similar-faces 来“预取统计”，会导致大量请求与卡顿；已移除。

const loadSimilarFaces = async (signal?: AbortSignal, clearData = true) => {
  if (!selectedPersonId.value) return
  loadingSimilarFaces.value = true
  try {
    // 同时加载相似推荐和套图推荐的数据
    const [similarRes, sameFolderRes] = await Promise.all([
      api.get(`/admin/persons/${selectedPersonId.value}/similar-unassigned`, {
        params: { top: 200, threshold: 0.6 },
        signal
      }),
      api.get(`/admin/persons/${selectedPersonId.value}/faces/same-folder`, {
        params: { top: 200 },
        signal
      })
    ])

    if (signal?.aborted) return

    // 合并相似推荐和套图推荐的数据
    const similarData = (similarRes.data || []).filter((f: FaceItem) => (f.similarity || 0) >= 0.6)
    const sameFolderData = sameFolderRes.data || []

    // 为套图推荐的数据添加标记，方便UI区分
    const markedSameFolderData = sameFolderData.map((item: FaceItem) => ({
      ...item,
      _isSameFolder: true
    }))

    // 合并两个数组
    similarFaces.value = [...similarData, ...markedSameFolderData]
    // debug: log summary to help trace unexpectedly large result sets
    try {
      const ids = similarFaces.value.map((f: any) => f.id)
      const photoIds = Array.from(new Set(similarFaces.value.map((f: any) => f.photoId).filter(Boolean)))
      // debug logging removed
    } catch (e) {
      console.warn('loadSimilarFaces -> logging failed', e)
    }

    if (clearData) {
      selectedSimilar.value.clear()
      resetFaceVisible('similar')
    }
  } catch (error) {
    if (error.name !== 'AbortError') {
      console.error('加载相似人脸失败:', error)
    }
  } finally {
    loadingSimilarFaces.value = false
  }
}


const loadClusterFaces = async (signal?: AbortSignal, clearData = true) => {
  if (selectedClusterIndex.value === null) return
  loadingPersonFaces.value = true

  // 在开始加载前，记录当前的数据状态
  const previousData = [...personFaces.value]

  try {
    const res = await api.get(`/admin/clusters/${selectedClusterIndex.value}/faces`, {
      params: { threshold: clusterThreshold.value },
      signal
    })
    if (signal?.aborted) {
      personFaces.value = previousData
      return
    }

    personFaces.value = res.data || []
    if (clearData) {
    selectedClusterFaces.value.clear()
    resetFaceVisible('cluster')
    }
  } catch (error) {
    if (error.name !== 'AbortError') {
      console.error('加载聚类人脸失败:', error)
      personFaces.value = previousData
    }
  } finally {
    loadingPersonFaces.value = false
  }
}

// 加载聚类的相似人物推荐
const loadSimilarPersonsForCluster = async (signal?: AbortSignal) => {
  if (selectedClusterIndex.value === null) return

  try {
    const res = await api.get(`/admin/clusters/${selectedClusterIndex.value}/similar-persons`, {
      params: { threshold: clusterThreshold.value },
      signal
    })

    if (signal?.aborted) return

    // 转换数据格式以兼容现有UI
    similarPersonsData.value = (res.data || []).map((item: any) => ({
      id: item.personId,
      name: item.personName,
      type: 'confirmed' as const,
      similarity: item.similarity // 已经是小数形式，不需要转换
    }))
  } catch (error) {
    if (error.name !== 'AbortError') {
      console.error('加载相似人物失败:', error)
      similarPersonsData.value = []
    }
  }
}

// 根据当前上下文加载相关的未分配人脸
const loadContextualUnassigned = async (signal?: AbortSignal) => {
  loadingUnassignedFaces.value = true
  try {
    const params: any = { page: 0, size: 100, sort: 'confidence' }

    // 根据当前选择添加上下文参数
    if (selectedItem.value?.type === 'confirmed' && selectedPersonId.value) {
      params.personId = selectedPersonId.value
    } else if (selectedItem.value?.type === 'cluster' && selectedClusterIndex.value !== null) {
      params.clusterIndex = selectedClusterIndex.value
    }

    const res = await api.get('/admin/faces/unassigned', {
      params,
      signal
    })
    if (signal?.aborted) return

    unassignedFaces.value = res.data.content || res.data || []
    selectedUnassigned.value.clear()
    resetFaceVisible('unassigned')
  } catch (error) {
    if (error.name !== 'AbortError') {
      console.error('加载上下文未分配人脸失败:', error)
    }
  } finally {
    loadingUnassignedFaces.value = false
    unassignedLoadedOnce.value = true // 标记已尝试加载
  }
}

const loadUnassigned = async (signal?: AbortSignal, clearData = true) => {
  try {
    const res = await api.get('/admin/faces/unassigned', {
      params: { page: 0, size: 100, sort: 'confidence' }, // 按置信度降序，最多100项
      signal
    })
    if (signal?.aborted) return

    unassignedFaces.value = res.data.content || res.data || []
    if (clearData) {
      selectedUnassigned.value.clear()
      resetFaceVisible('unassigned')
    }
  } catch (error) {
    if (error.name !== 'AbortError') {
      console.error('加载未分配人脸失败:', error)
    }
  }
}

// 监听当前tab的loading状态，优化蒙版显示逻辑（只遮挡tab内容区）
watch(currentTabLoadingRaw, (isLoading) => {
  if (tabLoadingOverlayTimer !== null) {
    clearTimeout(tabLoadingOverlayTimer)
    tabLoadingOverlayTimer = null
  }
  if (isLoading) {
    // 延迟 200ms 再显示蒙版，避免快速切换/瞬间请求引起闪烁
    tabLoadingOverlayTimer = window.setTimeout(() => {
      showTabLoadingOverlay.value = true
      tabLoadingOverlayTimer = null
    }, 200)
  } else {
    // 延迟 100ms 再隐藏蒙版，确保不会出现闪烁
    tabLoadingOverlayTimer = window.setTimeout(() => {
      showTabLoadingOverlay.value = false
      tabLoadingOverlayTimer = null
    }, 100)
  }
})

const startEditName = (p: PersonListItem) => {
  // 保留空实现：列表不再通过点击姓名编辑，全部在右侧输入框完成
}

const cancelEdit = () => {
  editingName.value = originalName.value
  editingPersonId.value = null
}

const cancelDescriptionEdit = () => {
  editingDescription.value = originalDescription.value
}

const savePersonName = async (p: PersonListItem) => {
  if (p.type !== 'confirmed') {
    console.debug('[Persons] savePersonName skipped (not confirmed or no selectedPersonId)', {
      personId: p.id,
      itemType: p.type
    })
    cancelEdit()
    return
  }
  
  const newName = editingName.value.trim()
  if (!newName || newName === originalName.value) {
    console.debug('[Persons] savePersonName skipped (empty or unchanged)', {
      newName,
      originalName: originalName.value
    })
    cancelEdit()
    return
  }
  
  savingPerson.value = true
  try {
    const savedScrollTop = personListContainer.value?.scrollTop ?? 0
    const savedClusterPage = clusterPage.value

    console.debug('[Persons] savePersonName request', {
      apiPersonId: p.id,
      newName
    })
    await api.put(`/admin/persons/${p.id}`, {
      name: newName,
      description: p.description || ''
    })
    await loadPersons({ restoreClusterPages: savedClusterPage })
    cancelEdit()

    await nextTick()
    if (personListContainer.value) {
      personListContainer.value.scrollTop = savedScrollTop
    }
  } catch (e: any) {
    alert('保存失败: ' + (e.response?.data?.error || e.message))
    cancelEdit()
  } finally {
    savingPerson.value = false
  }
}

const saveSelectedPersonName = async () => {
  if (!selectedItem.value || selectedItem.value.type !== 'confirmed') return
  const newName = (selectedPersonName.value || '').trim()
  if (!newName || newName === originalSelectedPersonName.value) {
    selectedPersonName.value = originalSelectedPersonName.value
    return
  }
  savingPerson.value = true
  try {
    const savedScrollTop = personListContainer.value?.scrollTop ?? 0
    const savedClusterPage = clusterPage.value

    await api.put(`/admin/persons/${selectedItem.value.id}`, {
      name: newName,
      description: selectedItem.value.description || ''
    })
    originalSelectedPersonName.value = newName
    // 刷新人物列表，保持选中人物
    const prevId = selectedItem.value.id
    await loadPersons({ restoreClusterPages: savedClusterPage })
    const found = persons.value.find(p => p.id === prevId && p.type === 'confirmed')
    if (found) {
      selectedItem.value = found
      selectedPersonId.value = found.id
    }

    await nextTick()
    if (personListContainer.value) {
      personListContainer.value.scrollTop = savedScrollTop
    }
  } catch (e: any) {
    alert('保存姓名失败: ' + (e.response?.data?.error || e.message))
    selectedPersonName.value = originalSelectedPersonName.value
  } finally {
    savingPerson.value = false
  }
}

const resetSelectedPersonName = () => {
  selectedPersonName.value = originalSelectedPersonName.value
}

const createPersonFromSelectedCluster = async () => {
  if (!selectedItem.value || selectedItem.value.type !== 'cluster' || selectedClusterIndex.value === null) return
  const name = (selectedPersonName.value || '').trim()
  if (!name) return
  savingPerson.value = true
  try {
    // 保存滚动位置和已加载的聚类页数，以便创建后恢复
    const savedScrollTop = personListContainer.value?.scrollTop ?? 0
    const savedClusterPage = clusterPage.value

    // 获取当前聚类的人脸ID
    const facesRes = await api.get(`/admin/clusters/${selectedClusterIndex.value}/faces`, {
      params: { threshold: clusterThreshold.value }
    })
    const faces = facesRes.data || []
    const faceIds = faces.map((f: FaceItem) => f.id)

    const resCreate = await api.post('/admin/persons/from-faces', {
      faceIds,
      name,
      description: ''
    })

    // 重新加载人物列表（恢复之前的聚类页数），并选中新建或合并后的人物
    const createdId = resCreate?.data?.id
    await loadPersons({ restoreClusterPages: savedClusterPage })
    let created: PersonListItem | undefined
    if (createdId) {
      created = persons.value.find(p => p.type === 'confirmed' && p.id === createdId)
    }
    if (!created) {
      created = persons.value.find(p => p.type === 'confirmed' && (p.name || '未命名') === name)
    }
    if (created) {
      selectPerson(created)
    }

    // 恢复滚动位置
    await nextTick()
    if (personListContainer.value) {
      personListContainer.value.scrollTop = savedScrollTop
    }
  } catch (e: any) {
    alert('创建人物失败: ' + (e.response?.data?.error || e.message))
  } finally {
    savingPerson.value = false
  }
}

const handleSelectedPersonNameBlur = async () => {
  if (!selectedItem.value) return
  if (selectedItem.value.type === 'confirmed') {
    await saveSelectedPersonName()
  }
  // 移除cluster类型的自动创建，避免误触
}

const handleSelectedPersonNameEnter = async () => {
  if (!selectedItem.value) return
  if (selectedItem.value.type === 'confirmed') {
    await saveSelectedPersonName()
  } else if (selectedItem.value.type === 'cluster') {
    await createPersonFromSelectedCluster()
  }
}

const createPersonFromName = async (p: PersonListItem) => {
  if (p.type !== 'cluster' || selectedClusterIndex.value === null) {
    cancelEdit()
    return
  }
  
  const name = editingName.value.trim()
  if (!name) {
    cancelEdit()
    return
  }
  
  savingPerson.value = true
  try {
    // 保存滚动位置和已加载的聚类页数
    const savedScrollTop = personListContainer.value?.scrollTop ?? 0
    const savedClusterPage = clusterPage.value

    const res = await api.get(`/admin/clusters/${selectedClusterIndex.value}/faces`, {
      params: { threshold: clusterThreshold.value }
    })
    const faces = res.data || []
    const faceIds = faces.map((f: FaceItem) => f.id)
    
    const resCreate = await api.post('/admin/persons/from-faces', {
      faceIds,
      name: name,
      description: ''
    })
    const createdId = resCreate?.data?.id
    await loadPersons({ restoreClusterPages: savedClusterPage })
    cancelEdit()
    // Prefer selecting by returned id, fallback to name or first entry
    let created: PersonListItem | undefined
    if (createdId) {
      created = persons.value.find(p => p.type === 'confirmed' && p.id === createdId)
    }
    if (!created) {
      created = persons.value.find(p => p.type === 'confirmed' && (p.name || '未命名') === name)
    }
    if (created) selectPerson(created)

    // 恢复滚动位置
    await nextTick()
    if (personListContainer.value) {
      personListContainer.value.scrollTop = savedScrollTop
    }
  } catch (e: any) {
    alert('创建人物失败: ' + (e.response?.data?.error || e.message))
    cancelEdit()
  } finally {
    savingPerson.value = false
  }
}

const focusNameInput = (_e: Event) => {
  // 保留函数占位，但不再强制 select 文本，避免浏览器自动滚动/选区跳动
}

const savePersonDescription = async () => {
  if (!selectedPersonId.value || !selectedItem.value) return
  
  const newDesc = editingDescription.value.trim()
  if (newDesc === originalDescription.value) return

  const savedScrollTop = personListContainer.value?.scrollTop ?? 0
  const savedClusterPage = clusterPage.value
  
  try {
    await api.put(`/admin/persons/${selectedPersonId.value}`, {
      name: selectedItem.value.name || '未命名',
      description: newDesc
    })
    originalDescription.value = newDesc
    await loadPersons({ restoreClusterPages: savedClusterPage })

    await nextTick()
    if (personListContainer.value) {
      personListContainer.value.scrollTop = savedScrollTop
    }
  } catch (e: any) {
    alert('保存备注失败: ' + (e.response?.data?.error || e.message))
    editingDescription.value = originalDescription.value
  }
}

// 显示删除对话框
const showDeleteDialog = () => {
  if (!selectedItem.value || selectedItem.value.type !== 'confirmed') return
  deleteDialogVisible.value = true
}

const openPersonContextMenu = (event: MouseEvent, p: PersonListItem) => {
  if (p.type !== 'confirmed') return
  event.preventDefault()
  personContextMenu.value = { show: true, x: event.clientX, y: event.clientY, person: p }
}

const closePersonContextMenu = () => {
  personContextMenu.value.show = false
}

const togglePersonHidden = async (p: PersonListItem) => {
  closePersonContextMenu()
  if (!p || p.type !== 'confirmed') return
  try {
    await api.post(`/admin/persons/${p.id}/toggle-hidden`)
    const savedClusterPage = clusterPage.value
    await loadPersons({ restoreClusterPages: savedClusterPage })
    if (selectedItem.value?.id === p.id) {
      const found = persons.value.find(x => x.type === 'confirmed' && x.id === p.id)
      if (found) selectedItem.value = found
    }
  } catch (e: any) {
    console.error('切换隐藏状态失败:', e)
  }
}

// 执行解散人物
const confirmDissolvePerson = async () => {
  if (!selectedPersonId.value || !selectedItem.value) return

  deleteDialogVisible.value = false

  const name = selectedItem.value.name || '未命名'
  try {
    const ids = [
      ...confirmedFaces.value.map(f => f.id),
      ...autoAssignedFaces.value.map(f => f.id)
    ]
    if (ids.length > 0) {
      await api.post('/admin/faces/batch-unassign', { faceIds: ids })
    }
    await api.delete(`/admin/persons/${selectedPersonId.value}`)
    await loadPersons()
    selectedItem.value = null
    selectedPersonId.value = null
    if (persons.value.length > 0) {
      selectPerson(persons.value[0])
    }
  } catch (e: any) {
    alert('解散失败: ' + (e.response?.data?.error || e.message))
  }
}

// 执行删除人物
const confirmDeletePerson = async () => {
  if (!selectedPersonId.value || !selectedItem.value) return

  deleteDialogVisible.value = false
  
  try {
    await api.delete(`/admin/persons/${selectedPersonId.value}`)
    await loadPersons()
    selectedItem.value = null
    selectedPersonId.value = null
    if (persons.value.length > 0) {
      selectPerson(persons.value[0])
    }
  } catch (e: any) {
    alert('删除失败: ' + (e.response?.data?.error || e.message))
  }
}

const dissolvePerson = async () => {
  if (!selectedPersonId.value || !selectedItem.value) return
  const name = selectedItem.value.name || '未命名'
  if (!confirm(`确定要解散人物 "${name}" 吗？所有关联人脸将恢复为未分配，并删除该人物。`)) return
  try {
    const ids = [
      ...confirmedFaces.value.map(f => f.id),
      ...autoAssignedFaces.value.map(f => f.id)
    ]
    if (ids.length > 0) {
      await api.post('/admin/faces/batch-unassign', { faceIds: ids })
    }
    await api.delete(`/admin/persons/${selectedPersonId.value}`)
    await loadPersons()
    selectedItem.value = null
    selectedPersonId.value = null
    if (persons.value.length > 0) {
      selectPerson(persons.value[0])
    }
  } catch (e: any) {
    alert('解散失败: ' + (e.response?.data?.error || e.message))
  }
}

// loading states for claim actions
const isAssigningPhotoIds = ref<Set<number>>(new Set())
const isAssigningFaceIds = ref<Set<number>>(new Set())
const isBatchAssigning = ref(false)

const assignFace = async (faceId: number, confirmed: boolean = true) => {
  if (!selectedPersonId.value) return
  // per-face loading indicator
  const s1 = new Set(isAssigningFaceIds.value)
  s1.add(faceId)
  isAssigningFaceIds.value = s1
  try {
    console.info('开始单人人脸认领', { faceId, personId: selectedPersonId.value, confirmed })
    // use batch endpoint for single-face assign to keep API surface consistent
    await api.post('/admin/faces/batch-assign', {
      faceIds: [faceId],
      personId: selectedPersonId.value,
      confirmed: confirmed
    })
    console.info('单人人脸认领完成', { faceId })
    // 本地更新状态，避免刷新列表打断用户操作
    markFaceAssignedLocally(faceId, selectedPersonId.value)
    // 更新人物统计数字
    await refreshPersonsAfterFaceChange()
  } finally {
    const s2 = new Set(isAssigningFaceIds.value)
    s2.delete(faceId)
    isAssigningFaceIds.value = s2
  }
}

const confirmSelectedAuto = async () => {
  if (!selectedPersonId.value) return
  isBatchAssigning.value = true
  try {
  const hasSelection = selectedAuto.value.size > 0
  const targetIds = hasSelection
    ? Array.from(selectedAuto.value)
    : autoAssignedFaces.value.map(f => f.id)

  if (targetIds.length === 0) return

  // 批量认领：一次性提交所有人脸
  await api.post('/admin/faces/batch-assign', {
    faceIds: targetIds,
    personId: selectedPersonId.value,
    confirmed: true
      })

  selectedAuto.value.clear()
  await loadAllFaces()
  await refreshPersonsAfterFaceChange()
  } finally {
    isBatchAssigning.value = false
  }

  // 移除自动切换逻辑，让用户自己选择要查看的tab
}

const removeSelectedAuto = async () => {
  if (selectedAuto.value.size === 0) return
  const ids = Array.from(selectedAuto.value)
  await api.post('/admin/faces/batch-unassign', { faceIds: ids })
  selectedAuto.value.clear()
  await loadAllFaces()
  await refreshPersonsAfterFaceChange()
}

const assignSelectedSimilar = async () => {
  if (!selectedPersonId.value) return
  isBatchAssigning.value = true
  try {
    const hasSelection = selectedSimilar.value.size > 0
    let targetIds: number[] = []

    if (hasSelection) {
      targetIds = Array.from(selectedSimilar.value)
    } else {
      // 当没有选中任何项目时，从所有相似照片中收集人脸
      // 优化：每张照片只收集相似度最高的那个人脸
      const photoBestFace = new Map<number, any>()
      similarFaces.value.forEach((face: any) => {
        const photoId = face.photoId
        if (!photoId) return
        const currentBest = photoBestFace.get(photoId)
        if (!currentBest || (face.similarity || 0) > (currentBest.similarity || 0)) {
          photoBestFace.set(photoId, face)
        }
      })
      targetIds = Array.from(photoBestFace.values()).map(f => f.id)
    }

    if (targetIds.length === 0) return
    await api.post('/admin/faces/batch-assign', {
      faceIds: targetIds,
      personId: selectedPersonId.value,
      confirmed: true
      })

    selectedSimilar.value.clear()
    await loadAllFaces()
    await refreshPersonsAfterFaceChange()
  } finally {
    isBatchAssigning.value = false
  }
}

const assignSelectedAlbumFaces = async () => {
  if (!selectedPersonId.value) return
  isBatchAssigning.value = true
  try {
    const hasSelection = selectedAlbumFaces.value.size > 0
    const albumPhotos = selectedAlbum.value?.albumPhotos || []
    const faceMap: Record<number, any> = {}
    albumPhotos.forEach((photo: any) => {
      (photo.faces || []).forEach((face: any) => {
        faceMap[face.id] = { ...face, photoId: photo.id }
      })
    })

    // Build faceIds to assign:
    // - if user selected faces directly, include those face ids (only similarity>0)
    // - if user selected photo tiles, only include the highest similarity face per photo
    let faceIds: number[] = []
    if (hasSelection) {
      const sel = Array.from(selectedAlbumFaces.value)
      sel.forEach(id => {
        const face = faceMap[id]
        if (face) {
          // 选中的是人脸，直接添加
          if ((face.similarity || 0) > 0 && face.personId !== selectedPersonId.value) {
            faceIds.push(face.id)
          }
        } else {
          // 选中的是照片ID，只取相似度最高的那个人脸
          const photoById = albumPhotos.find((p: any) => p.id === id)
          if (photoById) {
            let bestFace: any = null
            let bestSimilarity = -1
            ;(photoById.faces || []).forEach((f: any) => {
              if ((f.similarity || 0) > 0 && f.personId !== selectedPersonId.value) {
                if ((f.similarity || 0) > bestSimilarity) {
                  bestSimilarity = f.similarity || 0
                  bestFace = f
                }
              }
            })
            if (bestFace) {
              faceIds.push(bestFace.id)
            }
          }
        }
      })
    } else {
      // 当没有选中任何项目时，从所有相册照片中收集未认领的人脸
      // 优化：每张照片只收集相似度最高的那个人脸
      const photoBestFace = new Map<number, any>()
      albumPhotos.forEach((photo: any) => {
        let bestFace: any = null
        let bestSimilarity = -1
        ;(photo.faces || []).forEach((face: any) => {
          // 只收集未分配或分配给其他人物的人脸，且相似度>0
          if (face.personId !== selectedPersonId.value && (face.similarity || 0) > 0) {
            if ((face.similarity || 0) > bestSimilarity) {
              bestSimilarity = face.similarity || 0
              bestFace = face
            }
          }
        })
        if (bestFace) {
          photoBestFace.set(photo.id, bestFace)
        }
      })
      faceIds = Array.from(photoBestFace.values()).map((f: any) => f.id)
    }

    // dedupe
    faceIds = Array.from(new Set(faceIds))
    if (faceIds.length === 0) {
      alert('没有可认领的人脸（可能已被认领或所选项目没有人脸）')
      return
    }
    await api.post('/admin/faces/batch-assign', {
      faceIds,
      personId: selectedPersonId.value,
      confirmed: true
    })

    // 本地更新已认领状态，避免刷新打断用户
    faceIds.forEach(fid => markFaceAssignedLocally(fid, selectedPersonId.value))
    selectedAlbumFaces.value.clear()
    // 更新相册统计
    updateAlbumClaimedCounts()
    await refreshPersonsAfterFaceChange()
  } finally {
    isBatchAssigning.value = false
  }
}

const assignPhoto = async (photoId: number) => {
  if (!selectedPersonId.value) return
  // per-photo loading indicator
  const s1 = new Set(isAssigningPhotoIds.value)
  s1.add(photoId)
  isAssigningPhotoIds.value = s1
  try {
    await api.post('/admin/photos/batch-assign', { photoIds: [photoId], personId: selectedPersonId.value })
    // 刷新数据
    // 本地更新图片状态，不刷新整个列表，保持用户操作连续性
    markPhotoAssignedLocally(photoId, selectedPersonId.value)
    if (selectedAlbum.value?.albumId) {
      // keep album photos as-is but update counts
      updateAlbumClaimedCounts()
    }
  } catch (e: any) {
    console.error('assignPhoto error', e)
    alert('认领图片失败: ' + (e.response?.data?.error || e.message))
  } finally {
    const s2 = new Set(isAssigningPhotoIds.value)
    s2.delete(photoId)
    isAssigningPhotoIds.value = s2
  }
}

const unassignPhotoOrFace = async (f: any) => {
  if (!selectedPersonId.value) return
  console.info('开始移除认领', { item: f, selectedPersonId: selectedPersonId.value })
  try {
    // 检查是否有认领的人脸，如果有则先移除人脸认领
    if (f.faces && f.faces.some((face: any) => face.personId === selectedPersonId.value)) {
      const assignedFace = f.faces.find((face: any) => face.personId === selectedPersonId.value)
      if (assignedFace) {
        await unassignFace(assignedFace.id)
      }
    }

    // 检查图片是否被认领，如果是则移除图片认领
    if (f.photoId && f.assignedPersonId === selectedPersonId.value) {
      await api.post('/admin/photos/batch-unassign', { photoIds: [f.photoId] })
      // update locally immediately so UI reflects change
      try {
        markPhotoUnassignedLocally(f.photoId)
        console.info('unassignPhotoOrFace: 本地标记图片移除', { photoId: f.photoId, remaining: assignedPhotos.value.length })
      } catch (e) {
        console.error('unassignPhotoOrFace 本地标记错误', e)
      }
    }

    // 刷新数据
    if (selectedAlbum.value?.albumId) {
      await loadAlbumPhotos(selectedAlbum.value.albumId)
    }
    await loadAlbumRecommendations(undefined, true)
  await loadAllFaces()
  await refreshPersonsAfterFaceChange()
  } catch (e: any) {
    console.error('unassignPhotoOrFace error', e)
    alert('移除认领失败: ' + (e.response?.data?.error || e.message))
  }
}

const assignSelectedAlbumPhotos = async () => {
  if (!selectedPersonId.value) return

  let photoIds: number[] = []
  const hasSelection = selectedAlbumFaces.value.size > 0
  if (hasSelection) {
    const sel = new Set(Array.from(selectedAlbumFaces.value))
    photoIds = visibleAlbumFaces.value
      .filter((f: any) => sel.has(f.id))
      .map((f: any) => f.photoId)
      .filter(Boolean)
    photoIds = Array.from(new Set(photoIds))
  } else {
    if (!selectedAlbum.value) {
      alert('请先选择一个相册')
      return
    }
    // 使用已加载的相册图片数据
    photoIds = (selectedAlbum.value.albumPhotos || []).map((p: any) => p.id)
  }

  if (photoIds.length === 0) return

  isBatchAssigning.value = true
  try {
    await api.post('/admin/photos/batch-assign', { photoIds, personId: selectedPersonId.value })
    selectedAlbumFaces.value.clear()
    if (selectedAlbum.value?.albumId) {
      await loadAlbumPhotos(selectedAlbum.value.albumId)
    }
    await loadAlbumRecommendations(undefined, true)
    await loadAllFaces()
    await refreshPersonsAfterFaceChange()
  } catch (e: any) {
    console.error('assignSelectedAlbumPhotos error', e)
    alert('批量认领图片失败: ' + (e.response?.data?.error || e.message))
  } finally {
    isBatchAssigning.value = false
  }
}

const assignSelectedUnassigned = async () => {
  const ids = Array.from(selectedUnassigned.value)
  if (ids.length === 0) return
  isBatchAssigning.value = true
  try {
    await api.post('/admin/faces/batch-assign', {
      faceIds: ids,
      personId: selectedPersonId.value,
      confirmed: true
    })
  selectedUnassigned.value.clear()
  await refreshPersonsAfterFaceChange()
  } finally {
    isBatchAssigning.value = false
  }
}

const unassignFace = async (faceId: number) => {
  console.info('开始移除人脸认领', { faceId, selectedPersonId: selectedPersonId.value })
  await api.post('/admin/faces/batch-unassign', { faceIds: [faceId] })
  // 本地更新 - 标记为已移除状态，不立即刷新列表
  markFaceUnassignedLocally(faceId)
  console.info('移除人脸认领完成', { faceId })
  // 不立即刷新，让用户看到"已移除"状态
  // await refreshPersonsAfterFaceChange()
}

const restoreFace = async (faceId: number) => {
  console.info('开始恢复人脸认领', { faceId, selectedPersonId: selectedPersonId.value })
  await api.post('/admin/faces/batch-assign', { faceIds: [faceId], personId: selectedPersonId.value })
  // 本地更新 - 恢复认领状态
  markFaceRestoredLocally(faceId)
  console.info('恢复人脸认领完成', { faceId })
  // 刷新人物统计
  await refreshPersonsAfterFaceChange()
}

// 将人脸设为人物头像
const setAsPersonAvatar = async (face: any) => {
  if (!selectedPersonId.value) return
  const savedScrollTop = personListContainer.value?.scrollTop ?? 0
  const savedClusterPage = clusterPage.value
  try {
    await personApi.setSamplePhoto(selectedPersonId.value, face.id)
    // 更新本地人物样例数据
    if (selectedItem.value && selectedItem.value.type === 'confirmed') {
      selectedItem.value.sampleFaceId = face.id
      selectedItem.value.samplePhotoId = face.photoId
      selectedItem.value.sampleThumbnailPath = face.photoThumbnailPath || face.thumbnailPath
      selectedItem.value.sampleOriginalPath = face.photoOriginalPath || face.originalPath
      selectedItem.value.sampleConfidence = face.confidence
    }
    // 刷新人物卡片
    await loadPersons({ restoreClusterPages: savedClusterPage })
    await nextTick()
    if (personListContainer.value) {
      personListContainer.value.scrollTop = savedScrollTop
    }
  } catch (error) {
    console.error('设置头像失败:', error)
    alert('设置头像失败: ' + (error.response?.data?.error || error.message))
  }
}

const handleRemoveClick = async (photoId: number) => {
  console.log('移除按钮被点击了!', photoId)
  await unassignPhoto(photoId)
}

// 防重复提交：允许快速连续点击不同图片/人脸，但避免同一个 faceId 被重复点击触发并发请求
const assigningFaceIds = ref<Set<number>>(new Set())
const handleAssignClick = async (faceId: number, confirmed: boolean = true) => {
  if (assigningFaceIds.value.has(faceId)) return
  assigningFaceIds.value = new Set(assigningFaceIds.value).add(faceId)
  try {
    await assignFace(faceId, confirmed)
  } finally {
    const next = new Set(assigningFaceIds.value)
    next.delete(faceId)
    assigningFaceIds.value = next
  }
}

const unassignPhoto = async (photoId: number) => {
  console.info('开始移除图片认领', { photoId, selectedPersonId: selectedPersonId.value })
  await api.post('/admin/photos/batch-unassign', { photoIds: [photoId] })
  // update locally immediately so UI reflects change
  markPhotoUnassignedLocally(photoId)
  console.info('移除图片认领完成', { photoId, remainingAssigned: assignedPhotos.value.length })
  await refreshPersonsAfterFaceChange()
}

// assign best available face for an album item (fallback to first unassigned face)
const assignAlbumFace = async (item: any) => {
  if (!selectedPersonId.value) return
  // find face id to assign: prefer bestFace, else first face with personId !== selectedPersonId
  console.info('点击相册项认领人脸', { itemId: item.id, bestFace: item.bestFace?.id, faces: (item.faces || []).map((f: any) => ({ id: f.id, personId: f.personId, similarity: f.similarity })) })
  const candidateFaceId = item.bestFace?.id || (item.faces || []).find((ff: any) => ff.personId !== selectedPersonId.value)?.id
  if (!candidateFaceId) {
    console.warn('assignAlbumFace: 未找到可认领的人脸', { item })
    alert('未找到可认领的人脸')
    return
  }
  try {
    await assignFace(candidateFaceId, true)
  } catch (e: any) {
    console.error('assignAlbumFace 出错', e)
    alert('认领人脸失败: ' + (e?.response?.data?.error || e?.message || String(e)))
  }
}

// ---- local state helpers ----
const markFaceAssignedLocally = (faceId: number, personId: number) => {
  // find previous owner and update various lists
  let prevPersonId: number | null = null
  let faceToMove: any = null

  const updateFace = (face: any) => {
    if (!face) return
    prevPersonId = prevPersonId ?? (face.personId || null)
    face.personId = personId
    face.isConfirmed = true
    if (!faceToMove) faceToMove = { ...face } // 保存一份副本用于移动
  }

  const listsToUpdate = [confirmedFaces.value, similarFaces.value, unassignedFaces.value, personFaces.value]
  for (const list of listsToUpdate) {
    if (!Array.isArray(list)) continue
    const f = list.find((x: any) => x.id === faceId)
    if (f) updateFace(f)
  }

  // albumPhotos
  (selectedAlbum.value?.albumPhotos || []).forEach((photo: any) => {
    (photo.faces || []).forEach((f: any) => {
      if (f.id === faceId) updateFace(f)
    })
  })

  // 如果人脸原来不在confirmedFaces中，将其添加到confirmedFaces
  if (faceToMove && !confirmedFaces.value.find((f: any) => f.id === faceId)) {
    confirmedFaces.value.push(faceToMove)
  }

  // 从其他列表中移除（除了confirmedFaces）
  similarFaces.value = similarFaces.value.filter((f: any) => f.id !== faceId)
  unassignedFaces.value = unassignedFaces.value.filter((f: any) => f.id !== faceId)
  personFaces.value = personFaces.value.filter((f: any) => f.id !== faceId)

  // update persons list counts
  if (prevPersonId !== personId) {
    if (prevPersonId) {
      const prev = persons.value.find((p: any) => p.id === prevPersonId && p.type === 'confirmed')
      if (prev) prev.faceCount = Math.max(0, (prev.faceCount || 0) - 1)
    }
    const cur = persons.value.find((p: any) => p.id === personId && p.type === 'confirmed')
    if (cur) cur.faceCount = (cur.faceCount || 0) + 1
  }
  // 更新相册统计
  updateAlbumClaimedCounts()
  // 更新可见列表
  resetFaceVisible('confirmed')
  resetFaceVisible('similar')
  resetFaceVisible('unassigned')
}

const markFaceUnassignedLocally = (faceId: number) => {
  let prevPersonId: number | null = null
  const updateFace = (face: any) => {
    if (!face) return
    prevPersonId = prevPersonId ?? (face.personId || null)
    face.personId = null
    face.isConfirmed = false
    face.isRemoved = true // 标记为已移除状态
  }
  const listsToUpdate2 = [confirmedFaces.value, similarFaces.value, unassignedFaces.value, personFaces.value]
  for (const list of listsToUpdate2) {
    if (!Array.isArray(list)) continue
    const f = list.find((x: any) => x.id === faceId)
    if (f) updateFace(f)
  }
  // 从confirmedFaces列表中移除已移除的人脸
  const confirmedIndex = confirmedFaces.value.findIndex((f: any) => f.id === faceId)
  if (confirmedIndex >= 0) {
    confirmedFaces.value.splice(confirmedIndex, 1)
  }
  ;(selectedAlbum.value?.albumPhotos || []).forEach((photo: any) => {
    (photo.faces || []).forEach((f: any) => {
      if (f.id === faceId) updateFace(f)
    })
  })
  if (prevPersonId) {
    const prev = persons.value.find((p: any) => p.id === prevPersonId && p.type === 'confirmed')
    if (prev) prev.faceCount = Math.max(0, (prev.faceCount || 0) - 1)
  }
  // 更新相册统计
  updateAlbumClaimedCounts()
  // 更新各 tab 的可见列表，确保已认领/未认领项目会立刻反映在 UI（不触发后端重新计算）
  resetFaceVisible('similar')
  resetFaceVisible('unassigned')
  resetFaceVisible('confirmed')
  resetFaceVisible('cluster')
}

const markFaceRestoredLocally = (faceId: number) => {
  const personId = selectedPersonId.value
  let restoredFace: any = null

  const updateFace = (face: any) => {
    if (!face) return
    face.personId = personId
    face.isConfirmed = true
    face.isRemoved = false // 移除已移除标记
    restoredFace = face
  }

  const listsToUpdate = [confirmedFaces.value, similarFaces.value, unassignedFaces.value, personFaces.value]
  for (const list of listsToUpdate) {
    if (!Array.isArray(list)) continue
    const f = list.find((x: any) => x.id === faceId)
    if (f) updateFace(f)
  }
  ;(selectedAlbum.value?.albumPhotos || []).forEach((photo: any) => {
    (photo.faces || []).forEach((f: any) => {
      if (f.id === faceId) updateFace(f)
    })
  })

  // 将恢复的人脸添加到confirmedFaces列表
  if (restoredFace && !confirmedFaces.value.find((f: any) => f.id === faceId)) {
    confirmedFaces.value.push(restoredFace)
  }

  // 更新人物统计
  const cur = persons.value.find((p: any) => p.id === personId && p.type === 'confirmed')
  if (cur) cur.faceCount = (cur.faceCount || 0) + 1
  // 更新相册统计
  updateAlbumClaimedCounts()
  // 更新各 tab 的可见列表
  resetFaceVisible('confirmed')
}

const markPhotoAssignedLocally = (photoId: number, personId: number) => {
  const p = (selectedAlbum.value?.albumPhotos || []).find((p: any) => p.id === photoId)
  if (p) p.assignedPersonId = personId
  // update albumRecommendations claimedPhotoCount
  updateAlbumClaimedCounts()
  // 图片指派后，刷新 albums / similar / unassigned 可见列表以反映变化
  resetFaceVisible('albums')
  resetFaceVisible('similar')
  resetFaceVisible('unassigned')
}

const markPhotoUnassignedLocally = (photoId: number) => {
  // remove from assignedPhotos list
  const index = assignedPhotos.value.findIndex((p: any) => p.id === photoId)
  if (index >= 0) {
    assignedPhotos.value.splice(index, 1)
    console.info('本地移除已指派图片', { photoId, 剩余数量: assignedPhotos.value.length })
  }
  // update albumRecommendations claimedPhotoCount
  updateAlbumClaimedCounts()
  // force reactive update for assignedPhotos
  assignedPhotos.value = [...assignedPhotos.value]
}

const updateAlbumClaimedCounts = () => {
  albumRecommendations.value.forEach((a: any) => {
    // 只有在该相册已加载过 albumPhotos 时才本地重算，避免把后端返回的统计覆盖成 0
    if (!Array.isArray(a.albumPhotos)) return
    const claimed = a.albumPhotos.filter((photo: any) =>
      photo.assignedPersonId === selectedPersonId.value ||
      (photo.faces || []).some((f: any) => f.personId === selectedPersonId.value)
    ).length
    a.claimedPhotoCount = claimed
    if (selectedAlbum.value && selectedAlbum.value.albumId === a.albumId) {
      selectedAlbum.value.claimedPhotoCount = claimed
    }
  })
}

const toggleSelectAuto = (id: number) => {
  const set = new Set(selectedAuto.value)
  if (set.has(id)) set.delete(id)
  else set.add(id)
  selectedAuto.value = set
}

const toggleSelectSimilar = (id: number) => {
  const set = new Set(selectedSimilar.value)
  if (set.has(id)) set.delete(id)
  else set.add(id)
  selectedSimilar.value = set
}


const toggleSelectUnassigned = (id: number) => {
  const set = new Set(selectedUnassigned.value)
  if (set.has(id)) set.delete(id)
  else set.add(id)
  selectedUnassigned.value = set
}

const toggleSelectCluster = (id: number) => {
  const set = new Set(selectedClusterFaces.value)
  if (set.has(id)) set.delete(id)
  else set.add(id)
  selectedClusterFaces.value = set
}

const toggleSelectConfirmed = (id: number) => {
  const set = new Set(selectedConfirmed.value)
  if (set.has(id)) set.delete(id)
  else set.add(id)
  selectedConfirmed.value = set
}

// 框选状态
const isSelecting = ref(false)
const selectionStart = ref<{ x: number, y: number } | null>(null)
const selectionEnd = ref<{ x: number, y: number } | null>(null)
const currentTab = ref<string>('')
const lastSelectedIndex = ref<number | null>(null)
// auto-scroll state for drag selection
const autoScrollRAF = ref<number | null>(null)
const lastMouseY = ref<number | null>(null)
const autoScrollContainerRef = ref<HTMLElement | null>(null)

const stopAutoScroll = () => {
  if (autoScrollRAF.value) {
    cancelAnimationFrame(autoScrollRAF.value)
    autoScrollRAF.value = null
  }
  autoScrollContainerRef.value = null
  lastMouseY.value = null
}

const startAutoScrollLoop = (container: HTMLElement) => {
  if (autoScrollRAF.value) return
  // find nearest scrollable ancestor (including self) so scrolling works for different tab layouts
  const findScrollableContainer = (el: HTMLElement | null): HTMLElement | null => {
    let cur: HTMLElement | null = el
    while (cur) {
      if (cur.scrollHeight > cur.clientHeight) return cur
      cur = cur.parentElement
    }
    return document.scrollingElement as HTMLElement
  }
  const scrollCont = findScrollableContainer(container) || document.scrollingElement as HTMLElement
  autoScrollContainerRef.value = scrollCont
  const loop = () => {
    const y = lastMouseY.value
    const cont = autoScrollContainerRef.value
    if (!cont || y == null) {
      autoScrollRAF.value = requestAnimationFrame(loop)
      return
    }
    const rect = cont.getBoundingClientRect()
    const thresh = 60
    let delta = 0
    const maxSpeed = 24
    if (y > rect.bottom - thresh) {
      const ratio = Math.min(1, (y - (rect.bottom - thresh)) / thresh)
      delta = Math.ceil(ratio * maxSpeed)
    } else if (y < rect.top + thresh) {
      const ratio = Math.min(1, ((rect.top + thresh) - y) / thresh)
      delta = -Math.ceil(ratio * maxSpeed)
    }
    if (delta !== 0) {
      cont.scrollBy({ top: delta, behavior: 'auto' })
      // adjust selectionEnd to account for scrolling
      // after scrolling, recompute selectionEnd relative to same container
      const newRect = cont.getBoundingClientRect()
      selectionEnd.value = {
        x: (lastMouseEventX && lastMouseEventX.value !== null) ? lastMouseEventX.value - newRect.left + cont.scrollLeft : (selectionEnd.value ? selectionEnd.value.x : 0),
        y: lastMouseY.value - newRect.top + cont.scrollTop
      }
      // update selection box after scrolling
      updateSelectionFromBox(currentTab.value)
    }
    autoScrollRAF.value = requestAnimationFrame(loop)
  }
  autoScrollRAF.value = requestAnimationFrame(loop)
}
// keep last mouse X to better compute selectionEnd.x when auto-scrolling
const lastMouseEventX = ref<number | null>(null)

// 容器引用
const tabScrollContainer = ref<HTMLElement | null>(null)
const confirmedContainer = ref<HTMLElement | null>(null)
const autoContainer = ref<HTMLElement | null>(null)
const similarContainer = ref<HTMLElement | null>(null)
const unassignedContainer = ref<HTMLElement | null>(null)
const clusterContainer = ref<HTMLElement | null>(null)

// 框选样式
const selectionBoxStyle = computed(() => {
  if (!selectionStart.value || !selectionEnd.value) return {}
  const left = Math.min(selectionStart.value.x, selectionEnd.value.x)
  const top = Math.min(selectionStart.value.y, selectionEnd.value.y)
  const width = Math.abs(selectionEnd.value.x - selectionStart.value.x)
  const height = Math.abs(selectionEnd.value.y - selectionStart.value.y)
  return {
    left: `${left}px`,
    top: `${top}px`,
    width: `${width}px`,
    height: `${height}px`
  }
})

// 获取当前标签页的容器和列表
const getCurrentContainer = (tabType: string) => {
  switch (tabType) {
    case 'confirmed': return confirmedContainer.value
    case 'auto': return autoContainer.value
    case 'similar': return similarContainer.value
    case 'albums': return albumContainer.value
    case 'unassigned': return unassignedContainer.value
    case 'cluster': return clusterContainer.value
    default: return null
  }
}

const getCurrentFaceList = (tabType: string): FaceItem[] => {
  switch (tabType) {
    case 'confirmed': return confirmedFaces.value
    case 'similar': return similarFaces.value
    case 'albums': return selectedAlbum.value?.albumPhotos?.map((photo: any) => ({
      // 始终使用图片ID作为标识符，优先使用最佳人脸数据（如果有）
      ...(photo.bestFace ? {
        ...photo.bestFace,
        id: photo.id, // 强制使用图片ID
        photoId: photo.id,
        photoFilename: photo.filename,
        thumbnailPath: photo.thumbnailPath,
        photoOriginalPath: photo.originalPath,
      } : {
        id: photo.id, // 使用图片ID
        photoId: photo.id,
        photoFilename: photo.filename,
        thumbnailPath: photo.thumbnailPath,
        photoOriginalPath: photo.originalPath,
        x: null,
        y: null,
        width: null,
        height: null,
        personId: null,
        personName: null,
        isConfirmed: false,
      }),
      similarity: photo.similarity || 0,
      faces: photo.faces || [],
      assignedPersonId: photo.assignedPersonId // 传递已分配人物信息
    })) || []
    case 'unassigned': return unassignedFaces.value
    case 'cluster': return personFaces.value
    default: return []
  }
}

const getCurrentSelection = (tabType: string): Ref<Set<number>> => {
  switch (tabType) {
    case 'confirmed': return selectedConfirmed
    case 'similar': return selectedSimilar
    case 'albums': return selectedAlbumFaces
    case 'unassigned': return selectedUnassigned
    case 'cluster': return selectedClusterFaces
    default: return selectedConfirmed
  }
}

const setVisibleFaces = (tabType: FaceTab, limit?: number) => {
  const list = getCurrentFaceList(tabType)
  const size = limit !== undefined ? Math.min(list.length, limit) : list.length
  visibleFacesMap[tabType].value = list.slice(0, size)
  faceVisibleLimits[tabType] = size
  facePlaceholderCounts[tabType] = Math.max(list.length - size, 0)
  maybeFillFaceViewport(tabType)
}

const recalcFacePageSize = () => {
  const width = tabScrollContainer.value?.clientWidth || 960
  const approxCardWidth = 160
  const cols = Math.max(2, Math.floor(width / approxCardWidth))
  facePageSize.value = Math.max(cols * FACE_ROWS_PER_PAGE, cols * 2)
  const t = currentFaceTab.value
  if (t) {
    const current = faceVisibleLimits[t] || 0
    if (current > 0) {
      setVisibleFaces(t, Math.min(getCurrentFaceList(t).length, Math.max(current, facePageSize.value * 2)))
    }
  }
}

// 已认领 tab：用“真实 grid 列数 + 可视高度可容纳的行数”计算优先加载数量
// 解决仅用 approxCardWidth 估算导致的“一页数量偏小”（例如实际 4 列但只预加载出 2.x 行）。
const confirmedGridCols = ref(3)
const confirmedGridRows = ref(FACE_ROWS_PER_PAGE)
const updateConfirmedGridMetrics = () => {
  const grid = confirmedContainer.value
  const scroll = tabScrollContainer.value
  if (!grid || !scroll) return

  try {
    // 列数：从 gridTemplateColumns 解析（最贴近 Tailwind 的 grid-cols-* 响应式结果）
    const cs = window.getComputedStyle(grid)
    const tpl = (cs.gridTemplateColumns || '').trim()
    const cols = tpl ? tpl.split(/\s+/).length : 0
    if (cols > 0) confirmedGridCols.value = cols

    // 行数：用一个卡片的实际高度 + rowGap 推算当前可视能容纳几行（向上取整）
    const firstCard = grid.querySelector('[data-face-id]') as HTMLElement | null
    const rowGap = parseFloat(cs.rowGap || '0') || 0
    const cardH = firstCard ? firstCard.getBoundingClientRect().height : 160
    const rowH = Math.max(1, cardH + rowGap)
    const rows = Math.max(1, Math.ceil(scroll.clientHeight / rowH))
    confirmedGridRows.value = rows
  } catch {
    // ignore
  }
}
const confirmedPriorityRowCount = computed(() => confirmedGridCols.value) // 一行
const confirmedPriorityPageCount = computed(() => Math.max(confirmedGridCols.value * confirmedGridRows.value, confirmedGridCols.value)) // 一页（按可视高度）

const maybeFillFaceViewport = (tabType: FaceTab) => {
  nextTick(() => {
    const container = getCurrentContainer(tabType) || tabScrollContainer.value
    if (!container) return
    let safety = 0
    while ((facePlaceholderCounts[tabType] || 0) > 0 && container.scrollHeight <= container.clientHeight + 40 && safety < 6) {
      ensureMoreFaces(tabType)
      safety++
    }
  })
}

const resetFaceVisible = (tabType: FaceTab, pages: number = 3) => {
  recalcFacePageSize()
  const list = getCurrentFaceList(tabType)
  const safePages = Math.max(1, Math.floor(pages || 3))
  const baseLimit = facePageSize.value * safePages || list.length // 默认预加载三页
  setVisibleFaces(tabType, Math.min(list.length, baseLimit))
}

const ensureMoreFaces = (tabType: FaceTab) => {
  const list = getCurrentFaceList(tabType)
  const current = faceVisibleLimits[tabType] || 0
  if (current >= list.length) return
  const next = Math.min(list.length, current + (facePageSize.value || list.length))
  setVisibleFaces(tabType, next)
}

// 鼠标按下
const handleMouseDown = (e: MouseEvent, tabType: string) => {
  // 如果点击的是按钮或输入框，不启动框选
  const target = e.target as HTMLElement
  if (target.tagName === 'BUTTON' || target.tagName === 'INPUT' || target.closest('button') || target.closest('input') || target.closest('label')) {
    return
  }
  
  // 如果按住Ctrl或Shift，不启动框选（用于多选）
  if (e.ctrlKey || e.shiftKey) {
    return
  }
  
  const container = getCurrentContainer(tabType)
  if (!container) return
  
  const rect = container.getBoundingClientRect()
  isSelecting.value = true
  currentTab.value = tabType
  selectionStart.value = {
    x: e.clientX - rect.left + container.scrollLeft,
    y: e.clientY - rect.top + container.scrollTop
  }
  selectionEnd.value = selectionStart.value
  e.preventDefault()
}

// 鼠标移动
const handleMouseMove = (e: MouseEvent, tabType: string) => {
  if (!isSelecting.value || currentTab.value !== tabType || !selectionStart.value) return
  
  const container = getCurrentContainer(tabType)
  if (!container) return
  
  const rect = container.getBoundingClientRect()
  selectionEnd.value = {
    x: e.clientX - rect.left + container.scrollLeft,
    y: e.clientY - rect.top + container.scrollTop
  }
  
  // update last mouse position for auto-scroll loop
  lastMouseY.value = e.clientY
  lastMouseEventX.value = e.clientX
  // start auto-scroll loop if not started
  try {
    startAutoScrollLoop(container)
  } catch (err) {
    console.warn('auto-scroll start failed', err)
  }
  
  // 更新框选范围内的人脸选择状态
  updateSelectionFromBox(tabType)
}

// 鼠标释放
const handleMouseUp = (e: MouseEvent, tabType: string) => {
  if (!isSelecting.value || currentTab.value !== tabType) return
  
  isSelecting.value = false
  selectionStart.value = null
  selectionEnd.value = null
  currentTab.value = ''
  // stop auto scroll loop
  stopAutoScroll()
}

// 根据框选范围更新选择
const updateSelectionFromBox = (tabType: string) => {
  if (!selectionStart.value || !selectionEnd.value) return
  
  const container = getCurrentContainer(tabType)
  if (!container) return
  
  const selection = getCurrentSelection(tabType)
  const left = Math.min(selectionStart.value.x, selectionEnd.value.x)
  const right = Math.max(selectionStart.value.x, selectionEnd.value.x)
  const top = Math.min(selectionStart.value.y, selectionEnd.value.y)
  const bottom = Math.max(selectionStart.value.y, selectionEnd.value.y)
  
  const faces = container.querySelectorAll('[data-face-id]')
  const newSelection = new Set(selection.value)
  
  faces.forEach((faceEl) => {
    const rect = faceEl.getBoundingClientRect()
    const containerRect = container.getBoundingClientRect()
    const faceLeft = rect.left - containerRect.left + container.scrollLeft
    const faceRight = faceLeft + rect.width
    const faceTop = rect.top - containerRect.top + container.scrollTop
    const faceBottom = faceTop + rect.height
    
    // 检查是否与框选范围相交
    const intersects = !(faceRight < left || faceLeft > right || faceBottom < top || faceTop > bottom)
    const faceId = parseInt(faceEl.getAttribute('data-face-id') || '0')
    
    if (intersects) {
      newSelection.add(faceId)
    }
  })
  
  selection.value = newSelection
}

// 记录最近两次点击的人脸，用于更精确地区分“双击同一张图”和“快速点到不同图”
const lastClickFaceId = ref<number | null>(null)
const lastClickTime = ref<number>(0)
const prevClickFaceId = ref<number | null>(null)
const prevClickTime = ref<number>(0)

// 处理人脸点击（支持Shift/Ctrl/Ctrl+Shift）
const handleFaceClick = (e: MouseEvent, faceId: number, tabType: string) => {
  // 如果点击的是按钮或输入框，不处理
  const target = e.target as HTMLElement
  if (target.tagName === 'BUTTON' || target.tagName === 'INPUT' || target.closest('button') || target.closest('input') || target.closest('label')) {
    return
  }

  // 更新最近两次点击记录（用于双击判定）
  prevClickFaceId.value = lastClickFaceId.value
  prevClickTime.value = lastClickTime.value
  lastClickFaceId.value = faceId
  lastClickTime.value = Date.now()
  
  const selection = getCurrentSelection(tabType)
  const faceList = getCurrentFaceList(tabType)
  const currentIndex = faceList.findIndex(f => f.id === faceId)
  
  if (e.shiftKey && lastSelectedIndex.value !== null && currentIndex !== -1) {
    // Shift+点击：连续选择
    const start = Math.min(lastSelectedIndex.value, currentIndex)
    const end = Math.max(lastSelectedIndex.value, currentIndex)
    const newSelection = new Set(selection.value)
    
    if (e.ctrlKey) {
      // Ctrl+Shift+点击：添加到连续选择
      for (let i = start; i <= end; i++) {
        newSelection.add(faceList[i].id)
      }
    } else {
      // Shift+点击：替换为连续选择
      newSelection.clear()
      for (let i = start; i <= end; i++) {
        newSelection.add(faceList[i].id)
      }
    }
    
    selection.value = newSelection
  } else if (e.ctrlKey || e.metaKey) {
    // Ctrl+点击：切换选择
    const newSelection = new Set(selection.value)
    if (newSelection.has(faceId)) {
      newSelection.delete(faceId)
    } else {
      newSelection.add(faceId)
    }
    selection.value = newSelection
    lastSelectedIndex.value = currentIndex
  } else {
    // 普通点击：单独选择
    selection.value = new Set([faceId])
    lastSelectedIndex.value = currentIndex
  }

  // 更新键盘激活索引为当前点击的人脸（基于可见列表）
  const visibleList = getCurrentVisibleFaceList()
  const vIndex = visibleList.findIndex(f => f.id === faceId)
  activeFaceIndex.value = vIndex >= 0 ? vIndex : null
}

// 处理双击：只在“最近两次单击都落在同一张图片且间隔很短”时才真正打开大图，
// 避免在快速点击不同图片（但位置相近、列表在刷新）时误触发双击。
const handleFaceDblClick = (face: FaceItem) => {
  const now = Date.now()

  // 需要有两次有效的最近点击记录
  if (lastClickFaceId.value == null || prevClickFaceId.value == null) return

  // 双击整体时间窗口（最后一次点击到现在要足够近）
  if (now - lastClickTime.value > 600) return

  // 两次点击必须都是同一张图片，且两次点击之间的时间足够短
  const sameFace =
    lastClickFaceId.value === face.id &&
    prevClickFaceId.value === face.id
  const betweenClicks = lastClickTime.value - prevClickTime.value

  if (!sameFace || betweenClicks <= 0 || betweenClicks > 400) {
    // 不符合“真正双击同一张图”的条件，忽略本次双击
    return
  }

  openViewer(face, { highlightedFaceId: face.id, preferredFaceId: face.id })
}

// 反选
const invertSelection = (tabType: string) => {
  const selection = getCurrentSelection(tabType)
  const faceList = getCurrentFaceList(tabType)
  const newSelection = new Set<number>()
  
  faceList.forEach(face => {
    if (!selection.value.has(face.id)) {
      newSelection.add(face.id)
    }
  })
  
  selection.value = newSelection
  lastSelectedIndex.value = faceList.length > 0 ? faceList.length - 1 : null
}

// 批量移除已确认照片
const removeSelectedConfirmed = async () => {
  if (selectedConfirmed.value.size === 0) return
  const ids = Array.from(selectedConfirmed.value)
  await api.post('/admin/faces/batch-unassign', { faceIds: ids })
  selectedConfirmed.value.clear()
  await loadAllFaces()
  await refreshPersonsAfterFaceChange()
}

// 批量移除聚类照片
const removeSelectedClusterFaces = async () => {
  if (selectedClusterFaces.value.size === 0) return
  if (!confirm(`确定要移除 ${selectedClusterFaces.value.size} 张照片吗？`)) return
  
  const ids = Array.from(selectedClusterFaces.value)
  await api.post('/admin/faces/batch-unassign', { faceIds: ids })
  selectedClusterFaces.value.clear()
  await loadClusterFaces()
  await refreshPersonsAfterFaceChange()
}

// 处理移除操作（根据tab类型）
const handleRemoveSelected = async () => {
  const currentTabType = getCurrentTabType()
  const selection = getCurrentSelection(currentTabType)

  if (selection.value.size === 0) return

  const ids = Array.from(selection.value)

  console.info('开始批量移除', { currentTabType, ids, selectedPersonId: selectedPersonId.value })
  isBatchAssigning.value = true
  try {
    // 根据tab类型处理移除（批量）
    switch (currentTabType) {
      case 'confirmed':
        // 批量解绑已确认的人脸
        console.info('批量解绑已确认人脸', { faceIds: ids })
        await api.post('/admin/faces/batch-unassign', { faceIds: ids })
        selection.value.clear()
        await loadConfirmedFaces()
        break

      case 'similar':
        // 批量解绑相似推荐的人脸
        console.info('批量解绑相似推荐人脸', { faceIds: ids })
        await api.post('/admin/faces/batch-unassign', { faceIds: ids })
        selection.value.clear()
        await loadSimilarFaces()
        break

      case 'albums': {
        // 对于相册：通过人脸ID解绑，不直接解绑整张图片（避免误伤其他人物）
        const albumPhotos = selectedAlbum.value?.albumPhotos || []
        const faceMap: Record<number, any> = {}
        albumPhotos.forEach((photo: any) => {
          (photo.faces || []).forEach((face: any) => {
            faceMap[face.id] = { ...face, photoId: photo.id }
          })
        })

        // 收集要解绑的人脸ID：选中的 face id，以及选中的 photo 瓦片中属于当前人物的 face id
        const faceIdsToUnassign: number[] = []
        for (const id of ids) {
          const f = faceMap[id]
          if (f) {
            // 直接选中了人脸条目
            if (f.personId === selectedPersonId.value) {
              faceIdsToUnassign.push(f.id)
            }
          } else {
            // 选中的是图片瓦片，收集该图片中已经被当前人物指派的人脸 id
            const photoById = albumPhotos.find((p: any) => p.id === id)
            if (photoById) {
              (photoById.faces || []).forEach((face: any) => {
                if (face.personId === selectedPersonId.value) {
                  faceIdsToUnassign.push(face.id)
                }
              })
            }
          }
        }

        // 去重
        const uniqueFaceIds = Array.from(new Set(faceIdsToUnassign))
        console.info('批量解绑相册内人脸（通过人脸ID）', { uniqueFaceIds })
        if (uniqueFaceIds.length > 0) {
          await api.post('/admin/faces/batch-unassign', { faceIds: uniqueFaceIds })
        } else {
          console.info('批量解绑相册内人脸：没有找到属于当前人物的人脸 ID')
          alert('没有找到属于当前人物的人脸用于移除')
        }

        selection.value.clear()
        if (selectedAlbum.value) {
          await loadAlbumPhotos(selectedAlbum.value.albumId)
        }
        break
      }

      case 'unassigned':
        // 批量解绑未分配的人脸（通常不会有，但保持接口一致）
        console.info('批量解绑未分配人脸', { faceIds: ids })
        await api.post('/admin/faces/batch-unassign', { faceIds: ids })
        selection.value.clear()
        await loadContextualUnassigned()
        break

      case 'cluster':
        // 批量解绑聚类人脸
        console.info('批量解绑聚类人脸', { faceIds: ids })
        await api.post('/admin/faces/batch-unassign', { faceIds: ids })
        selection.value.clear()
        await loadClusterFaces()
        break
    }
  } catch (e: any) {
    console.error('批量移除出错', e)
    alert('批量移除失败: ' + (e.response?.data?.error || e.message))
  } finally {
    isBatchAssigning.value = false
  }

  await refreshPersonsAfterFaceChange()
}

// 获取当前tab的选中数量
const getCurrentSelectionCount = (): number => {
  if (tab.value === 'confirmed' && selectedItem.value?.type === 'cluster') {
    return selectedClusterFaces.value.size
  }
  switch (tab.value) {
    case 'confirmed':
      return selectedConfirmed.value.size
    case 'auto':
      return selectedAuto.value.size
    case 'similar':
      return selectedSimilar.value.size
    case 'unassigned':
      return selectedUnassigned.value.size
    default:
      return 0
  }
}

const getCurrentVisibleFaceList = (): FaceItem[] => {
  if (selectedItem.value?.type === 'cluster' && tab.value === 'confirmed') {
    return visibleClusterFaces.value
  }
  switch (tab.value) {
    case 'confirmed': return visibleConfirmedFaces.value
    case 'similar': return visibleSimilarFaces.value
    case 'unassigned': return visibleUnassignedFaces.value
    default: return []
  }
}

// 获取当前tab的人脸总数
const getCurrentTabFaceCount = (): number => {
  if (tab.value === 'confirmed' && selectedItem.value?.type === 'cluster') {
    return personFaces.value.length
  }
  switch (tab.value) {
    case 'confirmed':
      return confirmedFaces.value.length
    case 'similar':
      return similarFaces.value.length
    case 'unassigned':
      return unassignedFaces.value.length
    default:
      return 0
  }
}

// 获取当前tab类型
const getCurrentTabType = (): string => {
  if (tab.value === 'confirmed' && selectedItem.value?.type === 'cluster') {
    return 'cluster'
  }
  return tab.value
}

// 计算认领按钮的文本和可用性
const getClaimButtonState = computed(() => {
  const currentTabType = getCurrentTabType()
  const selection = getCurrentSelection(currentTabType)
  const hasSelection = selection.value.size > 0

  // 检查当前tab中是否有任何未认领的项目
  let hasAnyUnclaimedItems = false

  switch (currentTabType) {
    case 'confirmed':
      // confirmed tab 没有未认领的项目
      hasAnyUnclaimedItems = false
      break
    case 'similar':
      hasAnyUnclaimedItems = similarFaces.value.some((face: any) => face.personId !== selectedPersonId.value)
      break
    case 'albums':
      const albumPhotos = selectedAlbum.value?.albumPhotos || []
      hasAnyUnclaimedItems = albumPhotos.some((photo: any) => {
        // 检查是否有未认领的人脸
        const hasUnclaimedFaces = photo.faces && photo.faces.some((face: any) => face.personId !== selectedPersonId.value)
        // 检查图片本身是否未认领
        const photoUnclaimed = photo.assignedPersonId !== selectedPersonId.value
        return hasUnclaimedFaces || photoUnclaimed
      })
      break
    case 'unassigned':
      hasAnyUnclaimedItems = unassignedFaces.value.some((face: any) => face.personId !== selectedPersonId.value)
      break
    case 'cluster':
      hasAnyUnclaimedItems = personFaces.value.some((face: any) => face.personId !== selectedPersonId.value)
      break
    default:
      hasAnyUnclaimedItems = false
  }

  // 如果没有任何未认领的项目，按钮不可用
  if (!hasAnyUnclaimedItems && !hasSelection) {
    return { text: '无可用项目', disabled: true, claimType: null }
  }

  if (hasSelection) {
    // 检查选中项目中是否包含已认领的项目
    const selectedItems = Array.from(selection.value)
    let hasClaimedFaces = false
    let hasClaimedPhotos = false
    let hasUnclaimedFaces = false
    let hasUnclaimedPhotos = false

    switch (currentTabType) {
      case 'confirmed':
        // confirmed tab 选中项都是已认领的人脸
        hasClaimedFaces = selectedItems.length > 0
        break
      case 'similar':
        // 检查选中的人脸是否已认领
        hasClaimedFaces = selectedItems.some(faceId =>
          similarFaces.value.some((face: any) => face.id === faceId && face.personId === selectedPersonId.value)
        )
        hasUnclaimedFaces = selectedItems.some(faceId =>
          similarFaces.value.some((face: any) => face.id === faceId && face.personId !== selectedPersonId.value)
        )
        break
      case 'albums': {
        // 检查选中的人脸或图片是否已认领，并统计可认领的人脸数量（只计 similarity>0）
        const albumPhotos = selectedAlbum.value?.albumPhotos || []
        // 建立 faceId -> face 映射
        const faceMap: Record<number, any> = {}
        albumPhotos.forEach((photo: any) => {
          (photo.faces || []).forEach((face: any) => {
            faceMap[face.id] = { ...face, photoId: photo.id }
          })
        })

        let totalClaimableFaces = 0
        let removeFaceCount = 0
        let removePhotoCount = 0

        selectedItems.forEach(id => {
          const face = faceMap[id]
          if (face) {
            // 单独选中的人脸
            if (face.personId === selectedPersonId.value) {
              hasClaimedFaces = true
              removeFaceCount++
            } else if ((face.similarity || 0) > 0) {
              totalClaimableFaces++
              hasUnclaimedFaces = true
            }
          } else {
            // 选中的是图片ID，只统计该照片中相似度最高的那张人脸
            const photoById = albumPhotos.find((photo: any) => photo.id === id)
            if (photoById) {
              // 检查照片是否有人脸
              const hasFaces = (photoById.faces || []).length > 0

              if (hasFaces) {
                // 照片有人脸，只取最优的那张
                let bestUnclaimedFace = null
                let bestSimilarity = -1
                ;(photoById.faces || []).forEach((f: any) => {
                  if (f.personId === selectedPersonId.value) {
                    hasClaimedFaces = true
                    removeFaceCount++
                  } else if ((f.similarity || 0) > 0 && f.personId !== selectedPersonId.value) {
                    if ((f.similarity || 0) > bestSimilarity) {
                      bestSimilarity = f.similarity || 0
                      bestUnclaimedFace = f
                    }
                  }
                })
                if (bestUnclaimedFace) {
                  totalClaimableFaces++
                  hasUnclaimedFaces = true
                }
              } else {
                // 照片没有人脸，作为图片处理
                if (photoById.assignedPersonId === selectedPersonId.value) {
                  hasClaimedPhotos = true
                  removePhotoCount++
                } else if (photoById.assignedPersonId === null || photoById.assignedPersonId === undefined) {
                  hasUnclaimedPhotos = true
                }
              }
            }
          }
        })

        // 如果选中包含已认领项，优先展示并执行移除（显示数量按已认领的人脸或图片计数）
        if (removeFaceCount > 0) {
          return { text: `移除${removeFaceCount}张人脸`, disabled: false, isRemove: true }
        }
        if (removePhotoCount > 0) {
          return { text: `移除${removePhotoCount}张图片`, disabled: false, isRemove: true }
        }

        // 否则显示可认领的人脸数量（忽略无脸图片）
        if (totalClaimableFaces > 0) {
          return { text: `认领${totalClaimableFaces}张人脸`, disabled: false, isRemove: false }
        }

        break
      }
      case 'unassigned':
        // unassigned tab 选中项都是未认领的人脸
        hasUnclaimedFaces = selectedItems.length > 0
        break
      case 'cluster':
        // 检查选中的人脸是否已认领
        hasClaimedFaces = selectedItems.some(faceId =>
          personFaces.value.some((face: any) => face.id === faceId && face.personId === selectedPersonId.value)
        )
        hasUnclaimedFaces = selectedItems.some(faceId =>
          personFaces.value.some((face: any) => face.id === faceId && face.personId !== selectedPersonId.value)
        )
        break
    }

    // 优先显示移除操作
    if (hasClaimedFaces) {
      return { text: `移除${selection.value.size}张人脸`, disabled: false, isRemove: true, claimType: 'face' }
    } else if (hasClaimedPhotos) {
      return { text: `移除${selection.value.size}张图片`, disabled: false, isRemove: true, claimType: 'photo' }
    } else if (hasUnclaimedFaces) {
      return { text: `认领${selection.value.size}张人脸`, disabled: false, isRemove: false, claimType: 'face' }
    } else if (hasUnclaimedPhotos) {
      return { text: `认领${selection.value.size}张图片`, disabled: false, isRemove: false, claimType: 'photo' }
    }
  } else {
    // 没有选中项目时，检查当前tab中是否有未认领的人脸或图片
    let hasUnclaimedFaces = false
    let hasUnclaimedPhotos = false

    switch (currentTabType) {
      case 'confirmed':
        // confirmed tab 没有未认领的项目
        break
      case 'similar':
        hasUnclaimedFaces = similarFaces.value.some((face: any) => face.personId !== selectedPersonId.value)
        break
      case 'albums':
        const albumPhotos = selectedAlbum.value?.albumPhotos || []
        hasUnclaimedFaces = albumPhotos.some((photo: any) =>
          photo.faces && photo.faces.some((face: any) => face.personId !== selectedPersonId.value)
        )
        hasUnclaimedPhotos = albumPhotos.some((photo: any) =>
          photo.assignedPersonId !== selectedPersonId.value &&
          !(photo.faces && photo.faces.some((face: any) => face.personId === selectedPersonId.value))
        )
        break
      case 'unassigned':
        hasUnclaimedFaces = unassignedFaces.value.some((face: any) => face.personId !== selectedPersonId.value)
        break
      case 'cluster':
        hasUnclaimedFaces = personFaces.value.some((face: any) => face.personId !== selectedPersonId.value)
        break
      default:
        break
    }

    if (hasUnclaimedFaces) {
      return { text: '认领全部人脸', disabled: false, isRemove: false, claimType: 'face' }
    } else if (hasUnclaimedPhotos) {
      return { text: '认领全部图片', disabled: false, isRemove: false, claimType: 'photo' }
    } else {
      return { text: '无可用项目', disabled: true, isRemove: false, claimType: null }
    }
  }

  return { text: '认领全部', disabled: false, isRemove: false, claimType: 'face' }
})

// 为了向后兼容，提供text属性
const getClaimButtonText = computed(() => getClaimButtonState.value.text)

// 全选当前tab的所有人脸
const selectAllCurrentTab = () => {
  const faceList = getCurrentFaceList(tab.value)
  const selection = getCurrentSelection(tab.value)
  const newSelection = new Set<number>()
  
  faceList.forEach(face => {
    newSelection.add(face.id)
  })
  
  selection.value = newSelection
  lastSelectedIndex.value = faceList.length > 0 ? faceList.length - 1 : null
}

// 处理认领操作（根据tab类型）
const selectAlbum = async (album: AlbumRecommendation) => {
  selectedAlbum.value = album
  selectedAlbumFaces.value.clear() // 切换相册时清空选择

  // 清空之前的图片数据，显示加载状态
  selectedAlbum.value.albumPhotos = []
  selectedAlbum.value.similarFaces = []

  // 立即显示加载状态
  loadingAlbums.value = true

  try {
    // 加载相册中的所有图片（包括无脸图片）
    await loadAlbumPhotos(album.albumId)
  } finally {
    // 确保加载状态被重置
    loadingAlbums.value = false
    // 重置可见面部列表
    resetFaceVisible('albums')
  }
}

const claimSelectedAlbumFaces = async () => {

  if (!selectedPersonId.value) {
    console.warn('没有选择人物')
    alert('请先选择一个已确认的人物')
    return
  }

  if (!selectedAlbum.value) {
    console.warn('没有选择相册')
    alert('请先选择一个相册')
    return
  }

  // 从本地缓存的相册数据中收集所有人脸ID，只要不是当前人物的就认领
  const faceIds: number[] = []
  const albumPhotos = selectedAlbum.value.albumPhotos || []

  if (!albumPhotos || albumPhotos.length === 0) {
    alert('相册数据未加载，请先选择相册并等待数据加载完成')
    return
  }

  albumPhotos.forEach((photo: any) => {
    if (photo.faces && photo.faces.length > 0) {
      photo.faces.forEach((face: any) => {
        // 只认领未分配（personId为null）或分配给其他人物的人脸
        if (face.personId === null || face.personId === undefined || face.personId !== selectedPersonId.value) {
          faceIds.push(face.id)
        }
      })
    }
  })

  console.log('可认领人脸IDs:', faceIds)

  if (faceIds.length === 0) {
    alert('没有可认领的人脸（可能已被认领或所选项目没有人脸）')
    return
  }

  try {
    await api.post('/admin/faces/batch-assign', {
      faceIds,
      personId: selectedPersonId.value,
      confirmed: true
    })

    // 重新加载相册数据以反映认领结果
    if (selectedAlbum.value) {
      await loadAlbumPhotos(selectedAlbum.value.albumId)
    }

    // 刷新其他数据
    await Promise.all([
      loadAllFaces(),
      loadAlbumRecommendations()
    ])

    // 如果当前在相似推荐tab，也刷新相似推荐
    if (tab.value === 'similar') {
      await loadSimilarFaces()
    }

  } catch (error) {
    console.error('认领相册人脸失败:', error)
    alert('认领失败，请重试')
  }
}

const handleClaimSelected = async () => {
  if (!selectedPersonId.value) {
    alert('请先选择一个已确认的人物')
    return
  }
  isBatchAssigning.value = true
  try {
  switch (tab.value) {
    case 'auto':
      await confirmSelectedAuto()
      break
    case 'similar':
      await assignSelectedSimilar()
      break
      case 'albums':
        // decide whether to claim photos or faces based on computed claimType
        if (getClaimButtonState.value.claimType === 'photo') {
          await assignSelectedAlbumPhotos()
        } else {
          await assignSelectedAlbumFaces()
        }
      break
    case 'unassigned':
      await assignSelectedUnassigned()
      break
    }
  } finally {
    isBatchAssigning.value = false
  }
}

// wrapper so we can always log when user clicks the claim/remove button,
// even when the computed state marks the button as disabled.
const attemptClaimOrRemove = async (e?: Event) => {
  console.info('尝试认领或移除按钮点击', {
    tab: tab.value,
    selectedPersonId: selectedPersonId.value,
    claimState: getClaimButtonState.value,
    selectionSizes: {
      confirmed: selectedConfirmed.value.size,
      similar: selectedSimilar.value.size,
      albums: selectedAlbumFaces.value.size,
      unassigned: selectedUnassigned.value.size,
      cluster: selectedClusterFaces.value.size
    }
  })

  if (getClaimButtonState.value.disabled) {
    console.warn('认领按钮被点击但处于禁用状态')
    alert(getClaimButtonState.value.text || '当前没有可用项目')
    return
  }

  if (getClaimButtonState.value.isRemove) {
    await handleRemoveSelected()
  } else {
    await handleClaimSelected()
  }
}

const getImageUrl = (path?: string) => {
  if (!path) return ''
  return path.startsWith('http') ? path : `/api/files${path}`
}

const getPersonThumb = (p: PersonListItem) => {
  return getImageUrl(p.sampleThumbnailPath || p.sampleOriginalPath)
}

const getFaceThumb = (f: FaceItem) => {
  // 对于相册中的图片，使用 thumbnailPath；对于人脸，使用 photoThumbnailPath
  return getImageUrl(f.thumbnailPath || f.photoThumbnailPath || f.photoOriginalPath)
}

const getActiveFacesForViewer = () => {
  if (selectedItem.value?.type === 'cluster' && tab.value === 'confirmed') {
    return personFaces.value
  }
  switch (tab.value) {
    case 'confirmed': return confirmedFaces.value
    case 'auto': return autoAssignedFaces.value
    case 'similar': return similarFaces.value
    case 'albums':
      // 返回所有照片（包括无脸的），用于PhotoViewer浏览
      const albumPhotos = selectedAlbum.value?.albumPhotos || []
      // 返回所有照片（不是只返回人脸）
      return albumPhotos.map((photo: any) => ({
        id: photo.id,
        photoId: photo.id,
        thumbnailPath: photo.thumbnailPath,
        photoThumbnailPath: photo.thumbnailPath,
        photoOriginalPath: photo.originalPath,
        photoFilename: photo.filename,
        faces: photo.faces || [],
        assignedPersonId: photo.assignedPersonId,
        similarity: photo.similarity || 0,
        bestFace: photo.bestFace || null
      }))
    case 'unassigned': return unassignedFaces.value
    default: return []
  }
}

const openViewer = async (faceOrPhoto: any, options: { highlightedFaceId?: number; highlightedClusterId?: number; preferredFaceId?: number } | null = null) => {
  // 判断是照片对象还是人脸对象
  // 照片对象有 id 和 thumbnailPath 等属性
  // 人脸对象有 photoId、photoThumbnailPath 等属性
  const isPhotoObject = faceOrPhoto.thumbnailPath !== undefined && faceOrPhoto.photoId === undefined
  const photoId = faceOrPhoto.photoId || faceOrPhoto.id

  if (!photoId) {
    // 如果没有photoId，构造一个最小的照片对象用于显示
    const fallback = {
      id: faceOrPhoto.id,
      filename: faceOrPhoto.filename || '',
      originalPath: faceOrPhoto.originalPath || faceOrPhoto.thumbnailPath || '',
      thumbnailPath: faceOrPhoto.thumbnailPath || faceOrPhoto.originalPath || '',
      webpPath: undefined,
      faces: faceOrPhoto.faces || []
    }
    viewerPhotos.value = [fallback]
    viewerIndex.value = 0
    viewerVisible.value = true
    return
  }

  try {
    showViewerLoadingOverlay.value = true

    // 获取所有相关的照片ID
  const list = getActiveFacesForViewer()
  const facesForViewer = list.length ? list : [faceOrPhoto]
    const photoIds = [...new Set(facesForViewer.filter((f: any) => f.photoId || f.id).map((f: any) => f.photoId || f.id).filter(Boolean))]

    if (photoIds.length === 0) {
      // 如果没有有效的photoId，使用fallback
      const fallback = {
        id: faceOrPhoto.id,
        filename: faceOrPhoto.filename || faceOrPhoto.photoFilename || '',
        originalPath: faceOrPhoto.originalPath || faceOrPhoto.photoOriginalPath || faceOrPhoto.thumbnailPath || '',
        thumbnailPath: faceOrPhoto.thumbnailPath || faceOrPhoto.photoThumbnailPath || faceOrPhoto.originalPath || '',
        webpPath: undefined,
        faces: faceOrPhoto.faces || []
      }
      viewerPhotos.value = [fallback]
      viewerIndex.value = 0
      viewerVisible.value = true
      return
    }

    // 并发获取所有照片的完整信息
    const photoPromises = photoIds.map(id => photoStore.fetchPhotoById(id))
    const photos = await Promise.all(photoPromises)

    // 为每张照片添加人脸信息（只保留相似度最高的那张人脸）
    const enrichedPhotos = photos.map(photo => {
      // 对于相册tab，faces可能已经在对象中
      const existingFaces = faceOrPhoto.faces && photo.id === (faceOrPhoto.photoId || faceOrPhoto.id) ? faceOrPhoto.faces : []
      let photoFaces: any[] = []

      if (existingFaces.length > 0) {
        // 相册tab：只取相似度最高的那张人脸
        const bestFace = existingFaces.reduce((best: any, current: any) =>
          (current.similarity || 0) > (best.similarity || 0) ? current : best
        , existingFaces[0])
        photoFaces = [bestFace]
      } else {
        // 其他tab：从facesForViewer中过滤，然后取最优的
        const allFacesForPhoto = facesForViewer
          .filter((f: any) => (f.photoId || f.id) === photo.id)
          .map((f: any) => ({
            id: f.id,
            personId: f.personId,
            personName: f.personName,
            personDescription: f.personDescription,
            isConfirmed: f.isConfirmed,
            confidence: f.confidence,
            x: f.x,
            y: f.y,
            width: f.width,
            height: f.height
          }))
        // 只取置信度最高的那张人脸
        if (allFacesForPhoto.length > 0) {
          const bestFace = allFacesForPhoto.reduce((best: any, current: any) =>
            (current.confidence || 0) > (best.confidence || 0) ? current : best
          , allFacesForPhoto[0])
          photoFaces = [bestFace]
        }
      }

      return {
        ...photo,
        faces: photoFaces
      }
    })

    viewerPhotos.value = enrichedPhotos
    const targetId = photoId
    const idx = viewerPhotos.value.findIndex(p => p.id === targetId)
    viewerIndex.value = idx >= 0 ? idx : 0
    // set highlight options for PhotoViewer — prefer explicit options.
    // Prefer highlighting the person (personId) or cluster (clusterId) if available so that
    // when navigating between photos the same person's faces remain highlighted.
    const fallbackOptions = (() => {
      // Only prefer the selectedItem (confirmed person) when we're in the 'confirmed' tab.
      // This prevents the left-person selection from overriding highlights when clicking inside
      // the 'similar' / 'albums' / 'unassigned' tabs.
      if (selectedItem.value && selectedItem.value.type === 'confirmed' && tab.value === 'confirmed') {
        return { highlightedPersonId: selectedItem.value.id }
      }
      // For albums tab, don't set fallback options since we want to use highlightedFaceIds
      if (tab.value === 'albums') {
        return {}
      }
      // prefer using the face's personId (if assigned), then clusterId (if available), otherwise fallback to faceId
      if (faceOrPhoto.personId) {
        return { highlightedPersonId: faceOrPhoto.personId }
      }
      if (faceOrPhoto.clusterId) {
        return { highlightedClusterId: faceOrPhoto.clusterId }
      }
      return { highlightedFaceId: faceOrPhoto.id }
    })()

    // collect all face ids from an appropriate source so PhotoViewer can highlight them across photos
    // Reason: facesForViewer might only include faces currently visible in the grid; for confirmed persons
    // we want to include all confirmed faces for that person so switching photos still highlights them.
    let allFacesSource: FaceItem[] = facesForViewer
    if (selectedItem.value?.type === 'confirmed' && tab.value === 'confirmed') {
      // For confirmed persons (only when we're viewing the 'confirmed' tab), show all confirmed faces for that person
      allFacesSource = confirmedFaces.value.filter(f => f.personId === selectedItem.value.id)
    } else if (selectedItem.value?.type === 'cluster' && tab.value === 'confirmed') {
      // For clusters (only in 'confirmed' tab), show all faces in that cluster
      allFacesSource = personFaces.value.filter(f => f.clusterId === selectedItem.value.id)
    } else {
      // No person/cluster selected - for recommended tabs, highlight all faces in the current tab
      if (tab.value === 'similar') {
        allFacesSource = similarFaces.value
      } else if (tab.value === 'albums' && selectedAlbum.value) {
        // 对于albums tab，不设置highlightedFaceIds，因为相似face和photo face的ID不匹配
        // 只使用preferredFaceId进行高亮
        allFacesSource = []
      } else if (tab.value === 'unassigned') {
        allFacesSource = unassignedFaces.value
      } else {
        allFacesSource = facesForViewer
      }
    }

    const allFaceIds = allFacesSource.map((f: any) => Number(f.id)).filter(Boolean)
    const uniqueFaceIds = Array.from(new Set(allFaceIds))
    const finalOptions = { ...fallbackOptions, highlightedFaceIds: uniqueFaceIds, ...options }
    viewerOpenOptions.value = finalOptions
    viewerVisible.value = true

  } catch (error) {
    console.error('获取照片信息失败:', error)
    // 出错时使用fallback
    const fallback = {
      id: faceOrPhoto.id,
      filename: faceOrPhoto.filename || faceOrPhoto.photoFilename || '',
      originalPath: faceOrPhoto.originalPath || faceOrPhoto.photoOriginalPath || faceOrPhoto.thumbnailPath || '',
      thumbnailPath: faceOrPhoto.thumbnailPath || faceOrPhoto.photoThumbnailPath || faceOrPhoto.originalPath || '',
      webpPath: undefined,
      faces: faceOrPhoto.faces || []
    }
    viewerPhotos.value = [fallback]
    viewerIndex.value = 0
    viewerVisible.value = true
  } finally {
    showViewerLoadingOverlay.value = false
  }
}

const getFaceCropStyle = (face: FaceItem) => {
  const thumb = getFaceThumb(face)
  const hasSize = face.width && face.height && face.width > 0 && face.height > 0
  if (!thumb || !hasSize) {
    return { position: 'absolute', inset: 0, objectFit: 'cover', objectPosition: 'center center' }
  }
  // Robust centering:
  // face.{x,y,width,height} may be:
  // - normalized in [0,1], or
  // - percentages in [0,100], or
  // - absolute pixels (unknown image dims) -> fallback to center
  const fw = face.width || 0
  const fh = face.height || 0
  const fx = face.x || 0
  const fy = face.y || 0

  let centerXPercent = 50
  let centerYPercent = 50

  if (fw > 0 && fh > 0) {
    if (fw <= 1 && fh <= 1) {
      // normalized [0,1]
      centerXPercent = (fx + fw / 2) * 100
      centerYPercent = (fy + fh / 2) * 100
    } else if (fw <= 100 && fh <= 100) {
      // already in percent [0,100]
      centerXPercent = fx + fw / 2
      centerYPercent = fy + fh / 2
    } else {
      // pixels - we don't have original image size, fallback to center
      centerXPercent = 50
      centerYPercent = 50
    }
  }

  // clamp to [0,100]
  const clampedCenterX = Math.max(0, Math.min(100, centerXPercent))
  const clampedCenterY = Math.max(0, Math.min(100, centerYPercent))

  return {
    position: 'absolute',
    inset: 0,
    width: '100%',
    height: '100%',
    objectFit: 'cover',
    objectPosition: `${clampedCenterX}% ${clampedCenterY}%`
  }
}

const openPhoto = (photoId?: number) => {
  if (!photoId) return
  window.open(`/photo/${photoId}`, '_blank')
}

// Open a photo inside the PhotoViewer (single-photo mode)
const openViewerForPhoto = async (photoId?: number) => {
  if (!photoId) return
  try {
    showViewerLoadingOverlay.value = true
    const photo = await photoStore.fetchPhotoById(photoId)
    if (!photo) return
    // ensure faces array exists
    const enriched = { ...photo, faces: photo.faces || [] }
    viewerPhotos.value = [enriched]
    viewerIndex.value = 0
    // no specific highlighted face; rely on highlightedFaceIds if any
    viewerOpenOptions.value = {}
    viewerVisible.value = true
  } catch (e) {
    console.error('openViewerForPhoto error', e)
  } finally {
    showViewerLoadingOverlay.value = false
  }
}

const onViewerIndexChange = (payload: { index: number; photoId?: number; faceIds?: number[] }) => {
  const pid = payload.photoId
  // determine current tab mapping used by selection logic
  let tabType = tab.value
  if (tabType === 'confirmed' && selectedItem.value?.type === 'cluster') {
    tabType = 'cluster'
  }
  const faceList = getCurrentFaceList(tabType)
  // find faces in current list that belong to this photo
  const matching = faceList.filter(f => f.photoId === pid).map(f => f.id)
  const selectionRef = getCurrentSelection(tabType)
  if (matching.length > 0) {
    selectionRef.value = new Set(matching)
    // update lastSelectedIndex for shift-click behavior
    const firstId = matching[0]
    const idx = faceList.findIndex(f => f.id === firstId)
    lastSelectedIndex.value = idx >= 0 ? idx : null
    // scroll the container to show the selected face element if present
    nextTick(() => {
      const container = getCurrentContainer(tabType) || tabScrollContainer.value
      if (!container) return
      const el: HTMLElement | null = container.querySelector(`[data-face-id="${firstId}"]`)
      if (el) {
        el.scrollIntoView({ behavior: 'smooth', block: 'nearest', inline: 'center' })
      }
    })
  } else {
    // no match in current list: clear selection
    selectionRef.value = new Set()
    lastSelectedIndex.value = null
  }
}

// viewer open options to pass to PhotoViewer
const viewerOpenOptions = ref<any>(null)

// 预取缩略图：让图片尽量按“当前列表显示顺序”加载，而不是由浏览器调度随机抢占。
// 只做轻量预取：小并发、可取消、去重，避免影响其它请求。
let confirmedPrefetchAbort: AbortController | null = null
const confirmedPrefetched = new Set<string>()
const prefetchImageQueue = async (urls: string[], opts: { signal?: AbortSignal; concurrency?: number } = {}) => {
  const concurrency = Math.max(1, Math.min(4, opts.concurrency ?? 2))
  const signal = opts.signal
  let idx = 0
  const workers = Array.from({ length: concurrency }).map(async () => {
    while (idx < urls.length && !(signal?.aborted)) {
      const url = urls[idx++]
      if (!url || confirmedPrefetched.has(url)) continue
      confirmedPrefetched.add(url)
      await new Promise<void>((resolve) => {
        const img = new Image()
        img.decoding = 'async'
        img.onload = () => resolve()
        img.onerror = () => resolve()
        // 某些浏览器支持 fetchPriority（非标准字段），尽量提高首屏体验；不支持也无碍
        ;(img as any).fetchPriority = 'low'
        img.src = url
      })
    }
  })
  await Promise.all(workers)
}

watch(tab, (v) => {
  // 切换人物时已经触发过 loadAllFaces() 预加载；
  // 这里改为“按需加载”（仅当数据为空/未加载时才请求），避免切 tab 反复拉取造成卡顿。
  if (v === 'similar' && selectedPersonId.value) {
    if (!loadingSimilarFaces.value && similarFaces.value.length === 0) loadSimilarFaces()
  } else if (v === 'unassigned') {
    // 首次进入tab时加载数据
    if (!unassignedLoadedOnce.value) {
      loadContextualUnassigned()
    } else if (unassignedFaces.value.length === 0) {
      // 已加载过但被清空过，需要重新加载
      loadContextualUnassigned()
    }
  } else if (v === 'auto' && selectedPersonId.value) {
    if (!loadingAuto.value && autoAssignedFaces.value.length === 0) loadAutoAssignedFaces()
  } else if (v === 'confirmed') {
    if (selectedPersonId.value) {
      if (!loadingConfirmedFaces.value && confirmedFaces.value.length === 0) loadConfirmedFaces()
    } else if (selectedClusterIndex.value !== null) {
      if (!loadingPersonFaces.value && personFaces.value.length === 0) loadClusterFaces()
    }
  } else if (v === 'albums' && selectedPersonId.value) {
    // 切换到套图推荐 tab：只在必要时加载相册列表，并在未选中相册时选中第一个
    const selectFirstAlbum = async () => {
      if (albumRecommendations.value.length === 0) {
        await loadAlbumRecommendations()
      }
      if (!selectedAlbum.value && albumRecommendations.value.length > 0) {
        await selectAlbum(albumRecommendations.value[0])
      }
    }
    if (!selectedAlbum.value) {
      selectFirstAlbum()
    }
  }
})

watch(currentFaceTab, (v) => {
  if (v && (faceVisibleLimits[v] || 0) === 0 && getCurrentFaceList(v).length > 0) {
    resetFaceVisible(v)
  }
})

// 已认领 tab：当可见列表变化时，按顺序预取缩略图，减少“先出固定两张”的概率
watch(visibleConfirmedFaces, (list) => {
  if (!list || list.length === 0) return
  // 可见列表变化时同步更新一次“优先加载一页”的估算
  nextTick(() => updateConfirmedGridMetrics())
  // 取消上一次预取（例如快速切人/切tab）
  if (confirmedPrefetchAbort) {
    confirmedPrefetchAbort.abort()
    confirmedPrefetchAbort = null
  }
  confirmedPrefetchAbort = new AbortController()
  const urls = list
    .map((f: any) => getFaceThumb(f))
    .filter((u: string) => !!u)
  // 跳过前面已经 eager/high 的部分，从后面开始轻量预取
  const tail = urls.slice(confirmedPriorityPageCount.value)
  prefetchImageQueue(tail, { signal: confirmedPrefetchAbort.signal, concurrency: 2 })
})

let resizeObserver: ResizeObserver | null = null
let faceResizeObserver: ResizeObserver | null = null
let albumResizeObserver: ResizeObserver | null = null
let confirmedGridResizeObserver: ResizeObserver | null = null

onMounted(() => {
  loadPersons()
  nextTick(() => {
    updateContainerWidth()
    recalcFacePageSize()
    if (personListContainer.value && 'ResizeObserver' in window) {
      resizeObserver = new ResizeObserver(() => {
        updateContainerWidth()
      })
      resizeObserver.observe(personListContainer.value)
    }
    if (tabScrollContainer.value && 'ResizeObserver' in window) {
      faceResizeObserver = new ResizeObserver(() => {
        recalcFacePageSize()
        updateConfirmedGridMetrics()
      })
      faceResizeObserver.observe(tabScrollContainer.value)
    }
    // confirmed grid cols/rows depend on its own container; observe it as well
    if (confirmedContainer.value && 'ResizeObserver' in window) {
      confirmedGridResizeObserver = new ResizeObserver(() => updateConfirmedGridMetrics())
      confirmedGridResizeObserver.observe(confirmedContainer.value)
    }
    if (albumContainer.value && 'ResizeObserver' in window) {
      albumResizeObserver = new ResizeObserver(() => {
        updateAlbumContainerWidth()
      })
      albumResizeObserver.observe(albumContainer.value)
      // initialize width
      updateAlbumContainerWidth()
    }
    window.addEventListener('resize', updateContainerWidth)
    window.addEventListener('resize', setAlbumMaxHeight)
    setAlbumMaxHeight()
    window.addEventListener('resize', recalcFacePageSize)
    window.addEventListener('keydown', handleGlobalKeydown)

    // 添加人物列表滚动监听器
    if (personListContainer.value) {
      personListContainer.value.addEventListener('scroll', handlePersonScroll, { passive: true })
    }
  })
})

// when albumContainer is mounted later (tab switch), ensure observer attached
watch(albumContainer, (el) => {
  if (el && 'ResizeObserver' in window) {
    if (albumResizeObserver && albumContainer.value) albumResizeObserver.unobserve(albumContainer.value)
    albumResizeObserver = new ResizeObserver(() => updateAlbumContainerWidth())
    albumResizeObserver.observe(el)
    nextTick(() => updateAlbumContainerWidth())
  }
})

watch(tab, (v) => {
  if (v === 'albums') {
    nextTick(() => updateAlbumContainerWidth())
  }
})

// 合并聚类到现有人物
const mergeToExistingPerson = async (targetPerson: PersonListItem) => {
  if (!selectedItem.value || selectedItem.value.type !== 'cluster') return

  // 保存滚动位置和已加载的聚类页数
  const savedScrollTop = personListContainer.value?.scrollTop ?? 0
  const savedClusterPage = clusterPage.value

  try {
    // 获取当前聚类的人脸ID
    const res = await api.get(`/admin/clusters/${selectedItem.value.id}/faces`, {
      params: { threshold: clusterThreshold.value }
    })
    const faces = res.data || []
    const faceIds = faces.map((f: FaceItem) => f.id)

    if (faceIds.length === 0) return

    // 批量将人脸分配到目标人物
    await api.post('/admin/faces/batch-assign', {
      faceIds,
      personId: targetPerson.id,
      confirmed: true
    })

    // 刷新人物列表（恢复之前的聚类页数）并聚焦到目标人物
    await loadPersons({ restoreClusterPages: savedClusterPage })
    const found = persons.value.find(p => p.type === 'confirmed' && p.id === targetPerson.id)
    if (found) {
      selectPerson(found)
    } else {
      // fallback: clear selection
      selectedItem.value = null
      selectedClusterIndex.value = null
    }

    // 恢复滚动位置
    await nextTick()
    if (personListContainer.value) {
      personListContainer.value.scrollTop = savedScrollTop
    }
  } catch (error) {
    console.error('合并到现有人物失败:', error)
    alert('合并失败，请重试')
  }
}

// 打开认领为弹窗
const openClaimDialog = async (sourceTab: 'cluster' | 'unassigned') => {
  // 根据来源tab获取选中的人脸ID
  let selectedFaceIds: number[] = []
  if (sourceTab === 'cluster') {
    if (selectedClusterFaces.value.size === 0) return
    selectedFaceIds = Array.from(selectedClusterFaces.value)
  } else if (sourceTab === 'unassigned') {
    if (selectedUnassigned.value.size === 0) return
    selectedFaceIds = Array.from(selectedUnassigned.value)
  } else {
    return
  }
  
  claimDialogSourceTab.value = sourceTab
  showClaimDialog.value = true
  claimDialogSearchKeyword.value = ''
  selectedClaimPersonId.value = null
  loadingClaimDialogPersons.value = true
  
  try {
    // 获取所有已确认人物（使用with-sample端点获取完整信息）
    const res = await api.get('/admin/persons/with-sample')
    const allPersons = res.data || []
    
    if (selectedFaceIds.length === 0) {
      // 如果没有选中人脸，只显示人物列表
      claimDialogPersons.value = allPersons.map((person: any) => ({
        type: 'confirmed' as const,
        id: person.id,
        name: person.name,
        faceCount: person.faceCount,
        sampleThumbnailPath: person.sampleThumbnailPath,
        sampleOriginalPath: person.sampleOriginalPath,
        samplePhotoId: person.samplePhotoId,
        sampleFaceId: person.sampleFaceId,
        similarity: undefined
      }))
      filterClaimDialogPersons()
      // 聚焦搜索框
      await nextTick()
      claimDialogSearchInput.value?.focus()
      return
    }
    
    // 计算相似度：调用后端API计算选中人脸与所有人物的相似度
    const similarityRes = await api.post('/admin/faces/calculate-similarity-to-persons', {
      faceIds: selectedFaceIds
    })
    
    const similarities = similarityRes.data || []
    const similarityMap = new Map(similarities.map((s: any) => [s.personId, s.similarity]))
    
    // 合并人物信息和相似度
    claimDialogPersons.value = allPersons.map((person: any) => ({
      type: 'confirmed' as const,
      id: person.id,
      name: person.name,
      faceCount: person.faceCount,
      sampleThumbnailPath: person.sampleThumbnailPath,
      sampleOriginalPath: person.sampleOriginalPath,
      samplePhotoId: person.samplePhotoId,
      sampleFaceId: person.sampleFaceId,
      similarity: similarityMap.get(person.id) || 0
    }))
    
    // 按相似度降序排序
    claimDialogPersons.value.sort((a, b) => (b.similarity || 0) - (a.similarity || 0))
    
    // 初始化过滤列表
    filterClaimDialogPersons()
    
    // 聚焦搜索框
    await nextTick()
    claimDialogSearchInput.value?.focus()
  } catch (error) {
    console.error('加载人物列表失败:', error)
    alert('加载人物列表失败: ' + (error.response?.data?.error || error.message || '请重试'))
  } finally {
    loadingClaimDialogPersons.value = false
  }
}

// 关闭认领为弹窗
const closeClaimDialog = () => {
  showClaimDialog.value = false
  claimDialogSearchKeyword.value = ''
  claimDialogPersons.value = []
  filteredClaimDialogPersons.value = []
  selectedClaimPersonId.value = null
  claimDialogSourceTab.value = null
}

// 为单个人脸打开认领为弹窗
const openClaimDialogForSingleFace = async (faceId: number) => {
  if (!selectedPersonId.value) {
    alert('请先选择一个已确认的人物')
    return
  }

  claimDialogSourceTab.value = 'unassigned' // 来源设置为unassigned
  showClaimDialog.value = true
  claimDialogSearchKeyword.value = ''
  selectedClaimPersonId.value = null
  loadingClaimDialogPersons.value = true

  try {
    // 计算选中人脸与所有人物的相似度
    const similarityRes = await api.post('/admin/faces/calculate-similarity-to-persons', {
      faceIds: [faceId]
    })

    const similarities = similarityRes.data || []
    const similarityMap = new Map(similarities.map((s: any) => [s.personId, s.similarity]))

    // 获取人物列表
    const personsRes = await api.get('/admin/persons/with-sample')
    let allPersons = personsRes.data || []

    // 转换为PersonListItem格式并添加相似度
    claimDialogPersons.value = allPersons.map((person: any) => ({
      type: 'confirmed' as const,
      id: person.id,
      name: person.name,
      description: person.description,
      faceCount: person.faceCount,
      sampleThumbnailPath: person.sampleThumbnailPath,
      sampleOriginalPath: person.sampleOriginalPath,
      samplePhotoId: person.samplePhotoId,
      sampleFaceId: person.sampleFaceId,
      similarity: similarityMap.get(person.id)
    }))

    // 按相似度降序排序
    claimDialogPersons.value.sort((a, b) => (b.similarity || 0) - (a.similarity || 0))

    // 初始化过滤列表
    filterClaimDialogPersons()

    // 聚焦搜索框
    await nextTick()
    claimDialogSearchInput.value?.focus()
  } catch (error) {
    console.error('加载人物列表失败:', error)
    alert('加载人物列表失败: ' + (error.response?.data?.error || error.message || '请重试'))
  } finally {
    loadingClaimDialogPersons.value = false
  }
}

// 过滤认领弹窗中的人物列表
const filterClaimDialogPersons = () => {
  const keyword = claimDialogSearchKeyword.value.trim()
  const keywordLower = keyword.toLowerCase()
  
  if (!keywordLower) {
    filteredClaimDialogPersons.value = [...claimDialogPersons.value]
    selectedClaimPersonId.value = null
  } else {
    filteredClaimDialogPersons.value = claimDialogPersons.value.filter((person) =>
      (person.name || '').toLowerCase().includes(keywordLower)
    )
    
    // 检查是否有完全匹配的人物名字（优先）
    const exactMatches = filteredClaimDialogPersons.value.filter((person) =>
      (person.name || '').toLowerCase() === keywordLower
    )
    
    if (exactMatches.length > 0) {
      // 如果有完全匹配的，默认选中第一个
      selectedClaimPersonId.value = exactMatches[0].id
    } else if (filteredClaimDialogPersons.value.length === 1) {
      // 如果没有完全匹配的，但只有一个结果，也自动选中
      selectedClaimPersonId.value = filteredClaimDialogPersons.value[0].id
    } else {
      // 如果有多个不完全匹配的结果，清空选择
      selectedClaimPersonId.value = null
    }
  }
}

// 处理搜索框回车键
const handleClaimDialogEnter = () => {
  if (selectedClaimPersonId.value !== null) {
    confirmClaimToPerson()
  }
}

// 从认领弹窗新建人物
const handleCreatePersonFromClaimDialog = async () => {
  const name = (claimDialogSearchKeyword.value || '').trim()
  if (!name) {
    alert('请输入人物名字')
    claimDialogSearchInput.value?.focus()
    return
  }

  // 获取来源tab
  const sourceTab = claimDialogSourceTab.value
  if (!sourceTab) return

  // 获取选中的人脸ID
  let selectedFaceIds: number[] = []
  if (sourceTab === 'cluster') {
    if (selectedClusterFaces.value.size === 0) return
    selectedFaceIds = Array.from(selectedClusterFaces.value)
  } else if (sourceTab === 'unassigned') {
    if (selectedUnassigned.value.size === 0) return
    selectedFaceIds = Array.from(selectedUnassigned.value)
  } else {
    return
  }

  // 保存滚动位置和已加载的聚类页数
  const savedScrollTop = personListContainer.value?.scrollTop ?? 0
  const savedClusterPage = clusterPage.value

  try {
    // 创建新人物并直接分配人脸
    const res = await api.post('/admin/persons/from-faces', {
      faceIds: selectedFaceIds,
      name
    })
    const newPersonId = res.data.id

    // 关闭弹窗
    closeClaimDialog()

    // 刷新人物列表（恢复之前的聚类页数）
    await loadPersons({ restoreClusterPages: savedClusterPage })

    // 选中新创建的人物
    const found = persons.value.find(p => p.type === 'confirmed' && p.id === newPersonId)
    if (found) {
      selectPerson(found)
    }

    // 恢复滚动位置
    await nextTick()
    if (personListContainer.value) {
      personListContainer.value.scrollTop = savedScrollTop
    }

    // 如果是从聚类tab来的，刷新聚类人脸列表
    if (sourceTab === 'cluster' && selectedItem.value?.type === 'cluster' && selectedClusterIndex.value !== null) {
      await loadClusterFaces()
    }
  } catch (error) {
    console.error('创建人物失败:', error)
    alert('创建人物失败: ' + (error.response?.data?.error || error.message || '请重试'))
  }
}

// 选择认领人物（单选）- 点击直接认领
const selectClaimPerson = async (person: PersonListItem) => {
  // 设置选中并直接确认认领
  selectedClaimPersonId.value = person.id
  await confirmClaimToPerson()
}

// 确认认领到选中的人物
const confirmClaimToPerson = async () => {
  if (!claimDialogSourceTab.value || selectedClaimPersonId.value === null) return
  
  // 保存来源tab，因为关闭弹窗会清空它
  const sourceTab = claimDialogSourceTab.value
  
  // 根据来源tab获取选中的人脸ID
  let selectedFaceIds: number[] = []
  if (sourceTab === 'cluster') {
    if (selectedClusterFaces.value.size === 0) return
    selectedFaceIds = Array.from(selectedClusterFaces.value)
  } else if (sourceTab === 'unassigned') {
    if (selectedUnassigned.value.size === 0) return
    selectedFaceIds = Array.from(selectedUnassigned.value)
  } else {
    return
  }

  // 保存滚动位置和已加载的聚类页数
  const savedScrollTop = personListContainer.value?.scrollTop ?? 0
  const savedClusterPage = clusterPage.value
  
  try {
    const targetPersonId = selectedClaimPersonId.value
    
    // 批量将人脸分配到选中的人物
    await api.post('/admin/faces/batch-assign', {
      faceIds: selectedFaceIds,
      personId: targetPersonId,
      confirmed: true
    })
    
    // 关闭弹窗
    closeClaimDialog()
    
    // 根据来源tab处理不同的逻辑
    if (sourceTab === 'cluster') {
      // 聚类tab：清空选中，刷新人物列表和聚类人脸列表
      selectedClusterFaces.value.clear()
      
      await loadPersons({ restoreClusterPages: savedClusterPage })
      const found = persons.value.find(p => p.type === 'confirmed' && p.id === targetPersonId)
      if (found) {
        selectPerson(found)
      } else {
        selectedItem.value = null
        selectedClusterIndex.value = null
      }

      // 恢复滚动位置
      await nextTick()
      if (personListContainer.value) {
        personListContainer.value.scrollTop = savedScrollTop
      }
      
      if (selectedItem.value?.type === 'cluster' && selectedClusterIndex.value !== null) {
        await loadClusterFaces()
      }
    } else if (sourceTab === 'unassigned') {
      // 未分配tab：只移除已认领的人脸，保持tab显示
      // 从未分配列表中移除已认领的人脸
      unassignedFaces.value = unassignedFaces.value.filter(face => !selectedFaceIds.includes(face.id))

      // 清空选中
      selectedUnassigned.value.clear()

      // 重新计算未分配tab的可见列表，立即从界面上去掉这些图片
      resetFaceVisible('unassigned')

      // 刷新人物列表（用于更新人物数量等统计）
      await loadPersons({ restoreClusterPages: savedClusterPage })

      // 恢复滚动位置
      await nextTick()
      if (personListContainer.value) {
        personListContainer.value.scrollTop = savedScrollTop
      }

      // 不切换tab，保持未分配tab显示
    }
  } catch (error) {
    console.error('认领失败:', error)
    alert('认领失败: ' + (error.response?.data?.error || error.message || '请重试'))
  }
}

const handleGlobalKeydown = (e: KeyboardEvent) => {
  if (e.key === 'Escape') {
    // 认领弹窗打开时，关闭弹窗
    if (showClaimDialog.value) {
      closeClaimDialog()
      return
    }
    // PhotoViewer 打开时，交给 PhotoViewer 自己处理（它会自己关闭）
    if (viewerVisible.value) {
      return
    }
    // 否则返回首页
    router.push('/admin')
  }
}

onBeforeUnmount(() => {
  if (isResizing.value) {
    stopResize()
  }
  if (isResizingAlbums.value) {
    stopResizeAlbums()
  }
  if (resizeObserver && personListContainer.value) {
    resizeObserver.unobserve(personListContainer.value)
  }
  resizeObserver = null
  if (faceResizeObserver && tabScrollContainer.value) {
    faceResizeObserver.unobserve(tabScrollContainer.value)
  }
  faceResizeObserver = null
  if (confirmedGridResizeObserver && confirmedContainer.value) {
    confirmedGridResizeObserver.unobserve(confirmedContainer.value)
  }
  confirmedGridResizeObserver = null
  window.removeEventListener('resize', updateContainerWidth)
  window.removeEventListener('resize', setAlbumMaxHeight)
  if (albumResizeObserver && albumContainer.value) {
    albumResizeObserver.unobserve(albumContainer.value)
  }
  albumResizeObserver = null
  window.removeEventListener('resize', recalcFacePageSize)
  window.removeEventListener('keydown', handleGlobalKeydown)

  // 移除人物列表滚动监听器
  if (personListContainer.value) {
    personListContainer.value.removeEventListener('scroll', handlePersonScroll)
  }
})

watch(clusterThreshold, (v) => {
  // 限制范围，但不再强制吸附，保证 spin 输入的数值可以精确生效
  let val = v || DEFAULT_CLUSTER_THRESHOLD
  if (Number.isNaN(val as any)) {
    val = DEFAULT_CLUSTER_THRESHOLD
  }
  val = Math.max(0.1, Math.min(0.9, val))
  clusterThreshold.value = parseFloat(Number(val).toFixed(2))
  localStorage.setItem(CLUSTER_THRESHOLD_KEY, String(clusterThreshold.value))
  if (thresholdTimer) {
    clearTimeout(thresholdTimer)
  }
  thresholdTimer = window.setTimeout(async () => {
    await loadPersons()
    if (selectedItem.value?.type === 'cluster' && selectedClusterIndex.value !== null) {
      await loadClusterFaces()
    }
  }, 200)
})
</script>

<style scoped>
.cluster-slider {
  -webkit-appearance: none;
  appearance: none;
  background: transparent;
  height: 24px;
  padding: 0;
  margin: 0;
}
.cluster-slider::-webkit-slider-thumb {
  -webkit-appearance: none;
  appearance: none;
  width: 14px;
  height: 14px;
  background: #3b82f6;
  border-radius: 9999px;
  border: 2px solid #dbeafe;
  box-shadow: 0 0 0 4px rgba(59, 130, 246, 0.25);
  cursor: pointer;
  /* 向上偏移，让圆点中心对齐中间那条线（根据 14px thumb + 2px 线条手调） */
  margin-top: -6px;
}
.cluster-slider::-moz-range-thumb {
  width: 14px;
  height: 14px;
  background: #3b82f6;
  border-radius: 9999px;
  border: 2px solid #dbeafe;
  box-shadow: 0 0 0 4px rgba(59, 130, 246, 0.25);
  cursor: pointer;
  margin-top: -6px;
}
.cluster-slider::-webkit-slider-runnable-track {
  height: 2px;
  background: transparent;
}
.cluster-slider::-moz-range-track {
  height: 2px;
  background: transparent;
}

.no-spinner::-webkit-outer-spin-button,
.no-spinner::-webkit-inner-spin-button {
  -webkit-appearance: none;
  margin: 0;
}

.no-spinner[type='number'] {
  -moz-appearance: textfield;
}

/* 人物右键菜单毛玻璃样式（与相册管理一致） */
.person-glass-menu {
  background: rgba(31, 41, 55, 0.75);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  border: 1px solid rgba(75, 85, 99, 0.4);
  box-shadow: 0 10px 40px rgba(0, 0, 0, 0.5);
}
</style>

