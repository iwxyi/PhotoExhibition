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
                class="font-medium text-xs truncate"
                :title="p.name || '未命名'"
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
                :class="person.similarity >= 60
                  ? 'bg-blue-600/20 hover:bg-blue-600/40 border border-blue-500/30 text-blue-300 hover:text-blue-200'
                  : 'bg-gray-600/20 hover:bg-gray-600/40 border border-gray-500/30 text-gray-400 hover:text-gray-300'"
              >
                {{ person.name || '未命名' }} ({{ person.similarity.toFixed(0) }}%)
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
        class="w-1 bg-gray-700 cursor-col-resize hover:bg-gray-600 transition-colors flex-shrink-0"
        @mousedown="startResize"
      ></div>

      <!-- 右侧内容区域 -->
      <div class="flex-1 bg-gray-800 rounded-lg p-3 overflow-hidden flex flex-col min-w-0 relative">
        <!-- 全屏透明遮罩，阻止操作但显示loading图标 -->
        <div
          v-if="showLoadingOverlay"
          class="absolute inset-0 z-30 pointer-events-auto flex items-center justify-center"
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
                <template v-if="selectedItem.type === 'confirmed'">已认领 ({{ confirmedFaces.length }})</template>
                <template v-else>聚类 ({{ personFaces.length }})</template>
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
                相似推荐 ({{ similarFaces.length }})
              </button>
              <button
                v-if="selectedItem.type === 'confirmed'"
                class="px-3 py-1.5 rounded-t text-xs transition-colors whitespace-nowrap"
                :class="tab === 'albums' ? 'bg-gray-700 text-purple-400 border-b-2 border-purple-400' : 'text-gray-400 hover:text-gray-200'"
                @click="tab = 'albums'"
              >
                套图推荐 ({{ albumRecommendations.length }})
              </button>
              <button
                class="px-3 py-1.5 rounded-t text-xs transition-colors whitespace-nowrap"
                :class="tab === 'unassigned' ? 'bg-gray-700 text-gray-300 border-b-2 border-gray-300' : 'text-gray-400 hover:text-gray-200'"
                @click="tab = 'unassigned'"
              >
                未分配 ({{ unassignedFaces.length }})
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
                @click="handleClaimSelected"
                :disabled="getCurrentSelection(getCurrentTabType()).size === 0"
                class="px-2 py-1 bg-blue-600 hover:bg-blue-700 rounded text-[10px] disabled:opacity-50"
              >
                认领<template v-if="getCurrentSelection(getCurrentTabType()).size > 0"> ({{ getCurrentSelection(getCurrentTabType()).size }})</template>
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
          <div class="flex-1 overflow-y-auto pr-1" ref="tabScrollContainer" @scroll.passive="handleFaceScroll">
            <!-- 已认领照片 -->
            <div v-if="tab === 'confirmed' && selectedItem.type === 'confirmed'">
              <div class="mb-2">
                <span class="text-xs text-gray-400">已认领的人脸</span>
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
                  <div class="relative h-32 bg-gray-800 overflow-hidden" @dblclick.stop="openViewer(f)">
                    <img
                      v-if="getFaceThumb(f)"
                      :src="getFaceThumb(f)"
                      class="w-full h-full object-cover pointer-events-none"
                      :style="getFaceCropStyle(f)"
                      loading="lazy"
                    />
                    <button
                      @click.stop="unassignFace(f.id)"
                      class="absolute top-1 right-1 bg-red-600 hover:bg-red-700 text-white px-1.5 py-0.5 rounded text-[10px] opacity-0 group-hover:opacity-100 transition-opacity"
                    >
                      移除
                    </button>
                  </div>
                  <div class="p-1.5">
                    <div
                      class="text-[10px] text-blue-300 truncate cursor-pointer"
                      :title="f.photoFilename"
                      @click.stop="openPhoto(f.photoId)"
                      @dblclick.stop="openViewer(f)"
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
              <div v-if="confirmedFaces.length === 0" class="text-gray-400 text-xs text-center py-8">暂无已确认照片</div>
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
                    <div class="font-medium text-sm truncate">{{ album.albumName }}</div>
                    <div class="text-xs opacity-75 truncate">{{ album.albumPath }}</div>
                  </div>
                  <div v-if="albumRecommendations.length === 0" class="text-gray-400 text-xs text-center py-4">
                    暂无相册推荐
                  </div>
                </div>
              </div>

              <!-- 可拖拽分割线 -->
              <div
                class="w-1 bg-gray-700 cursor-col-resize hover:bg-gray-600 transition-colors flex-shrink-0 mx-2"
                @mousedown="startResizeAlbums"
              ></div>

              <!-- 右列：选中相册的人脸图片 -->
              <div class="flex-1 bg-gray-800 rounded-lg p-3 flex flex-col min-h-0">
                <div class="mb-3">
                  <div v-if="selectedAlbum" class="text-sm font-medium mt-1">
                    {{ selectedAlbum.albumName }}
                  </div>
                </div>

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
                      <div class="absolute top-1 right-1 px-1.5 py-0.5 rounded text-[10px] z-10 bg-purple-600/80">
                        {{ ((f.similarity || 0) * 100).toFixed(0) }}%
                      </div>
                    <div style="position:relative;width:100%;padding-top:100%;background:#111;" @dblclick.stop="openViewer(f)">
                      <img
                        v-if="getFaceThumb(f)"
                        :src="getFaceThumb(f)"
                        class="absolute inset-0 w-full h-full object-cover pointer-events-none"
                        :style="getFaceCropStyle(f)"
                        loading="lazy"
                      />
                      <button
                        @click.stop="assignFace(f.id, true)"
                        class="absolute bottom-1 right-1 bg-blue-600 hover:bg-blue-700 text-white px-1.5 py-0.5 rounded text-[10px] opacity-0 group-hover:opacity-100 transition-opacity"
                      >
                        认领
                      </button>
                    </div>
                      <div class="p-1.5">
                        <div
                          class="text-[10px] text-blue-300 truncate cursor-pointer"
                          :title="f.photoFilename"
                          @click.stop="openPhoto(f.photoId)"
                          @dblclick.stop="openViewer(f)"
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
                  <div class="relative h-32 bg-gray-800 overflow-hidden" @dblclick.stop="openViewer(f)">
                    <img
                      v-if="getFaceThumb(f)"
                      :src="getFaceThumb(f)"
                      class="w-full h-full object-cover pointer-events-none"
                      :style="getFaceCropStyle(f)"
                      loading="lazy"
                    />
                    <button
                      @click.stop="assignFace(f.id, true)"
                      class="absolute bottom-1 right-1 bg-blue-600 hover:bg-blue-700 text-white px-1.5 py-0.5 rounded text-[10px] opacity-0 group-hover:opacity-100 transition-opacity"
                    >
                      认领
                    </button>
                  </div>
                  <div class="p-1.5">
                    <div
                      class="text-[10px] text-blue-300 truncate cursor-pointer"
                      :title="f.photoFilename"
                      @click.stop="openPhoto(f.photoId)"
                      @dblclick.stop="openViewer(f)"
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
              </div>
              <div 
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
                  <div class="relative h-32 bg-gray-800 overflow-hidden" @dblclick.stop="openViewer(f)">
                    <img
                      v-if="getFaceThumb(f)"
                      :src="getFaceThumb(f)"
                      class="w-full h-full object-cover pointer-events-none"
                      :style="getFaceCropStyle(f)"
                      loading="lazy"
                    />
                    <button
                      v-if="selectedItem.type === 'confirmed'"
                      @click.stop="assignFace(f.id, false)"
                      class="absolute bottom-1 right-1 bg-blue-600 hover:bg-blue-700 text-white px-1.5 py-0.5 rounded text-[10px] opacity-0 group-hover:opacity-100 transition-opacity"
                    >
                      认领
                    </button>
                  </div>
                  <div class="p-1.5">
                    <div
                      class="text-[10px] text-blue-300 truncate cursor-pointer"
                      :title="f.photoFilename"
                      @click.stop="openPhoto(f.photoId)"
                      @dblclick.stop="openViewer(f)"
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
                  <div class="relative h-32 bg-gray-800 overflow-hidden" @dblclick.stop="openViewer(f)">
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
                      @dblclick.stop="openViewer(f)"
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
</template>

<script setup lang="ts">
import { ref, onMounted, watch, computed, nextTick, onBeforeUnmount, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '@/api'
import PhotoViewer from '@/components/PhotoViewer.vue'

const router = useRouter()

interface PersonListItem {
  type: 'confirmed' | 'cluster'
  id: number
  name?: string
  description?: string
  faceCount?: number
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
  takenAt?: string
}

const STORAGE_KEY = 'pe-persons-left-width'

const persons = ref<PersonListItem[]>([])
const confirmedPersons = ref<PersonListItem[]>([])
const clusterPersons = ref<PersonListItem[]>([])
const personKeyword = ref('')
const selectedItem = ref<PersonListItem | null>(null)

// 聚类分页
const clusterPage = ref(0)
const clusterPageSize = ref(40) // 每次加载40个聚类
const hasMoreClusters = ref(true)
const loadingClusters = ref(false)
const selectedPersonId = ref<number | null>(null)
const selectedClusterIndex = ref<number | null>(null)
const loadingPersons = ref(false)
// 右侧灰色等待蒙版，优化切换人物时的闪烁问题
const showLoadingOverlay = ref(false)
let loadingOverlayTimer: number | null = null
// 全局loading状态，用于控制蒙版显示
const globalLoadingFaces = ref(false)
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
    // 没输入名字，显示所有相似度>=40%的推荐人物
    return similarPersonsData.value.filter(person => person.similarity >= 40)
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

// 已确认照片
const confirmedFaces = ref<FaceItem[]>([])
const selectedConfirmed = ref<Set<number>>(new Set())
const loadingConfirmed = ref(false)

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

const startResize = (e: MouseEvent) => {
  isResizing.value = true
  resizeStartX.value = e.clientX
  resizeStartWidth.value = leftPanelWidth.value
  updateContainerWidth()
  document.body.style.userSelect = 'none'
  document.body.style.cursor = 'col-resize'
  document.addEventListener('mousemove', handleResize)
  document.addEventListener('mouseup', stopResize)
  e.preventDefault()
}

const handleResize = (e: MouseEvent) => {
  if (!isResizing.value) return
  const diff = e.clientX - resizeStartX.value
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
}

// 相册区域分割线拖拽
const startResizeAlbums = (e: MouseEvent) => {
  isResizingAlbums.value = true
  resizeStartX.value = e.clientX
  resizeStartWidth.value = albumsPanelWidth.value
  document.body.style.userSelect = 'none'
  document.body.style.cursor = 'col-resize'
  document.addEventListener('mousemove', handleResizeAlbums)
  document.addEventListener('mouseup', stopResizeAlbums)
  e.preventDefault()
}

const handleResizeAlbums = (e: MouseEvent) => {
  if (!isResizingAlbums.value) return
  const diff = e.clientX - resizeStartX.value
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

const loadPersons = async () => {
  loadingPersons.value = true
  try {
    // 先加载已确认人物（一次性加载所有）
    const confirmedRes = await api.get('/admin/persons/items', {
      params: { threshold: clusterThreshold.value, clusterPage: 0, clusterSize: 0 }
    })
    let list: PersonListItem[] = confirmedRes.data || []

    // 过滤出已确认人物，按确认照片总数降序排序
    confirmedPersons.value = list.filter(p => p.type === 'confirmed')
      .sort((a, b) => (b.faceCount || 0) - (a.faceCount || 0))

    // 初始化聚类数据
    clusterPersons.value = []
    clusterPage.value = 0
    hasMoreClusters.value = true

    // 加载第一页聚类
    await loadMoreClusters()

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
  if (p.type === 'confirmed') {
    selectedPersonId.value = p.id
    selectedClusterIndex.value = null
    selectedPersonName.value = p.name || '未命名'
    originalSelectedPersonName.value = selectedPersonName.value
    editingDescription.value = p.description || ''
    originalDescription.value = p.description || ''
    // 智能选择初始tab：优先显示有内容的tab
    tab.value = 'confirmed' // 默认先显示confirmed，加载后再根据数据调整
    loadAllFaces(abortController.signal)
    loadAlbumRecommendations(abortController.signal)
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

  globalLoadingFaces.value = true

  // 在开始加载前，记录当前的数据状态（用于保持显示）
  const previousData = {
    confirmedFaces: [...confirmedFaces.value],
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

    // 加载其他数据（不清除选择状态和可见状态）
    // 跳过自动分配数据加载，直接到已确认状态
    await Promise.all([
      loadSimilarFaces(signal, false),
      loadContextualUnassigned(signal)
    ])

    if (signal?.aborted) {
      // 如果被取消，恢复之前的数据
      confirmedFaces.value = previousData.confirmedFaces
      similarFaces.value = previousData.similarFaces
      unassignedFaces.value = previousData.unassignedFaces
      return
    }

    // 所有数据加载完成后，统一更新UI状态
    selectedConfirmed.value.clear()
    resetFaceVisible('confirmed')
    selectedSimilar.value.clear()
    resetFaceVisible('similar')
    selectedUnassigned.value.clear()
    resetFaceVisible('unassigned')

    // 智能选择初始显示的tab：优先显示有内容的tab
    // 但如果当前就在相似推荐tab，则保持不变（避免认领后自动跳转）
    nextTick(() => {
      if (tab.value === 'similar') {
        // 如果当前在相似推荐tab，保持不变
        return
      }

      if (confirmedFaces.value.length > 0) {
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
      similarFaces.value = previousData.similarFaces
      unassignedFaces.value = previousData.unassignedFaces
    }
  } finally {
    if (!signal?.aborted) {
      globalLoadingFaces.value = false
    }
  }
}

// 人脸认领 / 移除后，刷新左侧人物数量并保持当前选中与页码
const refreshPersonsAfterFaceChange = async () => {
  const current = selectedItem.value
    ? { id: selectedItem.value.id, type: selectedItem.value.type }
    : null

  await loadPersons()

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
}

const loadConfirmedFaces = async (signal?: AbortSignal, clearData = true) => {
  if (!selectedPersonId.value) return
  try {
    const res = await api.get(`/admin/persons/${selectedPersonId.value}/faces/confirmed`, {
      params: { page: 0, size: 200 },
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
    albumRecommendations.value = (res.data || [])

    // 如果需要保持选择，尝试找到对应的相册
    if (keepSelection && currentAlbumId) {
      selectedAlbum.value = albumRecommendations.value.find(a => a.albumId === currentAlbumId) || null
    }

    // 如果没有选中相册，默认选择第一个
    if (!selectedAlbum.value && albumRecommendations.value.length > 0) {
      selectedAlbum.value = albumRecommendations.value[0]
    }

    // 加载选中相册的相似人脸
    if (selectedAlbum.value) {
      await loadAlbumSimilarFaces(selectedAlbum.value.albumId, signal)
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

const loadSimilarFaces = async (signal?: AbortSignal, clearData = true) => {
  if (!selectedPersonId.value) return
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

    if (clearData) {
      selectedSimilar.value.clear()
      resetFaceVisible('similar')
    }
  } catch (error) {
    if (error.name !== 'AbortError') {
      console.error('加载相似人脸失败:', error)
    }
  }
}


const loadClusterFaces = async (signal?: AbortSignal, clearData = true) => {
  if (selectedClusterIndex.value === null) return

  globalLoadingFaces.value = true

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
    if (!signal?.aborted) {
      globalLoadingFaces.value = false
    }
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
  console.log('loadContextualUnassigned: 开始加载')
  console.log('selectedItem:', selectedItem.value)
  console.log('selectedPersonId:', selectedPersonId.value)
  console.log('selectedClusterIndex:', selectedClusterIndex.value)

  try {
    const params: any = { page: 0, size: 100, sort: 'confidence' }

    // 根据当前选择添加上下文参数
    if (selectedItem.value?.type === 'confirmed' && selectedPersonId.value) {
      params.personId = selectedPersonId.value
      console.log('添加personId参数:', params.personId)
    } else if (selectedItem.value?.type === 'cluster' && selectedClusterIndex.value !== null) {
      params.clusterIndex = selectedClusterIndex.value
      console.log('添加clusterIndex参数:', params.clusterIndex)
    } else {
      console.log('没有上下文参数，使用全局未分配人脸')
    }

    console.log('API请求参数:', params)

    const res = await api.get('/admin/faces/unassigned', {
      params,
      signal
    })
    if (signal?.aborted) return

    console.log('API响应数据长度:', res.data?.content?.length || res.data?.length || 0)

    unassignedFaces.value = res.data.content || res.data || []
    selectedUnassigned.value.clear()
    resetFaceVisible('unassigned')
  } catch (error) {
    if (error.name !== 'AbortError') {
      console.error('加载上下文未分配人脸失败:', error)
    }
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

// 监听全局loading状态，优化蒙版显示逻辑
watch(globalLoadingFaces, (isLoading) => {
  if (loadingOverlayTimer !== null) {
    clearTimeout(loadingOverlayTimer)
    loadingOverlayTimer = null
  }
  if (isLoading) {
    // 延迟 200ms 再显示蒙版，给足够时间避免快速切换时的闪烁
    loadingOverlayTimer = window.setTimeout(() => {
      showLoadingOverlay.value = true
      loadingOverlayTimer = null
    }, 200)
  } else {
    // 延迟 100ms 再隐藏蒙版，确保不会出现闪烁
    loadingOverlayTimer = window.setTimeout(() => {
      showLoadingOverlay.value = false
      loadingOverlayTimer = null
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
    console.debug('[Persons] savePersonName request', {
      apiPersonId: p.id,
      newName
    })
    await api.put(`/admin/persons/${p.id}`, {
      name: newName,
      description: p.description || ''
    })
    await loadPersons()
    cancelEdit()
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
    await api.put(`/admin/persons/${selectedItem.value.id}`, {
      name: newName,
      description: selectedItem.value.description || ''
    })
    originalSelectedPersonName.value = newName
    // 刷新人物列表，保持选中人物
    const prevId = selectedItem.value.id
    await loadPersons()
    const found = persons.value.find(p => p.id === prevId && p.type === 'confirmed')
    if (found) {
      selectedItem.value = found
      selectedPersonId.value = found.id
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
    // 获取当前聚类的人脸ID
    const res = await api.get(`/admin/clusters/${selectedClusterIndex.value}/faces`, {
      params: { threshold: clusterThreshold.value }
    })
    const faces = res.data || []
    const faceIds = faces.map((f: FaceItem) => f.id)

    await api.post('/admin/persons/from-faces', {
      faceIds,
      name,
      description: ''
    })

    // 重新加载人物列表，并选中新建或合并后的人物
    await loadPersons()
    const created = persons.value.find(p => p.type === 'confirmed' && (p.name || '未命名') === name)
    if (created) {
      selectPerson(created)
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
    const res = await api.get(`/admin/clusters/${selectedClusterIndex.value}/faces`, {
      params: { threshold: clusterThreshold.value }
    })
    const faces = res.data || []
    const faceIds = faces.map((f: FaceItem) => f.id)
    
    await api.post('/admin/persons/from-faces', {
      faceIds,
      name: name,
      description: ''
    })
    await loadPersons()
    cancelEdit()
    if (persons.value.length > 0) {
      selectPerson(persons.value[0])
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
  
  try {
    await api.put(`/admin/persons/${selectedPersonId.value}`, {
      name: selectedItem.value.name || '未命名',
      description: newDesc
    })
    originalDescription.value = newDesc
    await loadPersons()
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
    for (const id of ids) {
      await api.put(`/admin/faces/${id}/assign`, null, { params: { personId: null } })
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
    for (const id of ids) {
      await api.put(`/admin/faces/${id}/assign`, null, { params: { personId: null } })
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

const assignFace = async (faceId: number, confirmed: boolean = true) => {
  if (!selectedPersonId.value) return
  await api.put(`/admin/faces/${faceId}/assign`, null, { 
    params: { personId: selectedPersonId.value, confirmed: confirmed } 
  })
  selectedUnassigned.value.delete(faceId)
  selectedSimilar.value.delete(faceId)
  await loadAllFaces()
}

const confirmSelectedAuto = async () => {
  if (!selectedPersonId.value) return

  const hasSelection = selectedAuto.value.size > 0
  const targetIds = hasSelection
    ? Array.from(selectedAuto.value)
    : autoAssignedFaces.value.map(f => f.id)

  if (targetIds.length === 0) return

  // 批量认领：一口气提交所有人脸，再统一刷新
  await Promise.all(
    targetIds.map(id =>
      api.put(`/admin/faces/${id}/assign`, null, {
        params: { personId: selectedPersonId.value, confirmed: true }
      })
    )
  )

  selectedAuto.value.clear()
  await loadAllFaces()
  await refreshPersonsAfterFaceChange()

  // 移除自动切换逻辑，让用户自己选择要查看的tab
}

const removeSelectedAuto = async () => {
  if (selectedAuto.value.size === 0) return
  const ids = Array.from(selectedAuto.value)
  for (const id of ids) {
    await api.put(`/admin/faces/${id}/assign`, null, { 
      params: { personId: null } 
    })
  }
  selectedAuto.value.clear()
  await loadAllFaces()
  await refreshPersonsAfterFaceChange()
}

const assignSelectedSimilar = async () => {
  if (!selectedPersonId.value) return

  const hasSelection = selectedSimilar.value.size > 0
  const targetIds = hasSelection
    ? Array.from(selectedSimilar.value)
    : similarFaces.value.map(f => f.id)

  if (targetIds.length === 0) return

  await Promise.all(
    targetIds.map(id =>
      api.put(`/admin/faces/${id}/assign`, null, {
        params: { personId: selectedPersonId.value, confirmed: true }
      })
    )
  )

  selectedSimilar.value.clear()
  await loadAllFaces()
  await refreshPersonsAfterFaceChange()
}

const assignSelectedAlbumFaces = async () => {
  if (!selectedPersonId.value) return

  const hasSelection = selectedAlbumFaces.value.size > 0
  const targetIds = hasSelection
    ? Array.from(selectedAlbumFaces.value)
    : (selectedAlbum.value?.similarFaces.map(f => f.id) || [])

  if (targetIds.length === 0) return

  await Promise.all(
    targetIds.map(id =>
      api.put(`/admin/faces/${id}/assign`, null, {
        params: { personId: selectedPersonId.value, confirmed: true }
      })
    )
  )

  selectedAlbumFaces.value.clear()
  await loadAllFaces()

  // 重新加载相册推荐，但保持当前选中相册
  await loadAlbumRecommendations(undefined, true)

  await refreshPersonsAfterFaceChange()
}

const assignSelectedUnassigned = async () => {
  const ids = Array.from(selectedUnassigned.value)
  for (const id of ids) {
    await assignFace(id, true) // 直接确认，不经过自动分配
  }
  selectedUnassigned.value.clear()
  await refreshPersonsAfterFaceChange()
}

const unassignFace = async (faceId: number) => {
  await api.put(`/admin/faces/${faceId}/assign`, null, { 
    params: { personId: null } 
  })
  await loadAllFaces()
  await refreshPersonsAfterFaceChange()
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
    case 'albums': return selectedAlbum.value?.similarFaces || []
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

const resetFaceVisible = (tabType: FaceTab) => {
  recalcFacePageSize()
  const list = getCurrentFaceList(tabType)
  const baseLimit = facePageSize.value * 3 || list.length // 预加载至少三页
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

// 处理人脸点击（支持Shift/Ctrl/Ctrl+Shift）
const handleFaceClick = (e: MouseEvent, faceId: number, tabType: string) => {
  // 如果点击的是按钮或输入框，不处理
  const target = e.target as HTMLElement
  if (target.tagName === 'BUTTON' || target.tagName === 'INPUT' || target.closest('button') || target.closest('input') || target.closest('label')) {
    return
  }
  
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
  for (const id of ids) {
    await api.put(`/admin/faces/${id}/assign`, null, { 
      params: { personId: null } 
    })
  }
  selectedConfirmed.value.clear()
  await loadAllFaces()
  await refreshPersonsAfterFaceChange()
}

// 批量移除聚类照片
const removeSelectedClusterFaces = async () => {
  if (selectedClusterFaces.value.size === 0) return
  if (!confirm(`确定要移除 ${selectedClusterFaces.value.size} 张照片吗？`)) return
  
  const ids = Array.from(selectedClusterFaces.value)
  for (const id of ids) {
    await api.put(`/admin/faces/${id}/assign`, null, { 
      params: { personId: null } 
    })
  }
  selectedClusterFaces.value.clear()
  await loadClusterFaces()
  await refreshPersonsAfterFaceChange()
}

// 处理移除操作（根据tab类型）
const handleRemoveSelected = async () => {
  if (tab.value === 'confirmed' && selectedItem.value?.type === 'confirmed') {
    await removeSelectedConfirmed()
  } else if (tab.value === 'confirmed' && selectedItem.value?.type === 'cluster') {
    await removeSelectedClusterFaces()
  }
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

  // 清空之前的相似人脸数据，显示加载状态
  selectedAlbum.value.similarFaces = []

  // 立即显示加载状态
  loadingAlbums.value = true

  try {
    // 总是重新加载数据，确保数据是最新的
    await loadAlbumSimilarFaces(album.albumId)
  } finally {
    // 确保加载状态被重置
    loadingAlbums.value = false
    // 重置可见面部列表
    resetFaceVisible('albums')
  }
}

const claimSelectedAlbumFaces = async () => {
  if (!selectedPersonId.value) {
    alert('请先选择一个已确认的人物')
    return
  }

  if (!selectedAlbum.value) {
    alert('请先选择一个相册')
    return
  }

  if (selectedAlbum.value.similarFaces.length === 0) {
    alert('该相册没有相似人脸')
    return
  }

  const faceIds = selectedAlbum.value.similarFaces.map(f => f.id)

  try {
    await api.put('/admin/faces/batch-assign', {
      faceIds,
      personId: selectedPersonId.value,
      confirmed: true
    })

    // 从选中相册中移除这些照片
    selectedAlbum.value.similarFaces = []

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
  switch (tab.value) {
    case 'auto':
      await confirmSelectedAuto()
      break
    case 'similar':
      await assignSelectedSimilar()
      break
    case 'albums':
      await assignSelectedAlbumFaces()
      break
    case 'unassigned':
      await assignSelectedUnassigned()
      break
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
  return getImageUrl(f.photoThumbnailPath || f.photoOriginalPath)
}

const getActiveFacesForViewer = () => {
  if (selectedItem.value?.type === 'cluster' && tab.value === 'confirmed') {
    return personFaces.value
  }
  switch (tab.value) {
    case 'confirmed': return confirmedFaces.value
    case 'auto': return autoAssignedFaces.value
    case 'similar': return similarFaces.value
    case 'albums': return selectedAlbum.value?.similarFaces || []
    case 'unassigned': return unassignedFaces.value
    default: return []
  }
}

const openViewer = (face: FaceItem) => {
  const list = getActiveFacesForViewer()
  const facesForViewer = list.length ? list : [face]
  const photoMap = new Map<number | string, any>()

  facesForViewer.forEach(f => {
    const key = f.photoId ?? `face-${f.id}`
    const original = f.photoOriginalPath || f.photoThumbnailPath || ''
    const thumb = f.photoThumbnailPath || original
    if (!photoMap.has(key)) {
      photoMap.set(key, {
        id: f.photoId ?? f.id,
        filename: f.photoFilename || '',
        originalPath: original,
        thumbnailPath: thumb,
        webpPath: undefined,
        faces: [] as any[]
      })
    }
    const photoEntry = photoMap.get(key)!
    // 如果原图/缩略图后续遇到非空路径，进行补全
    if (!photoEntry.originalPath && original) photoEntry.originalPath = original
    if (!photoEntry.thumbnailPath && thumb) photoEntry.thumbnailPath = thumb
    photoEntry.faces.push({
      id: f.id,
      x: f.x,
      y: f.y,
      width: f.width,
      height: f.height,
      personId: f.personId,
      personName: f.personName,
      isConfirmed: f.isConfirmed ?? (!!f.personId),
      confidence: f.confidence,
      photoThumbnailPath: f.photoThumbnailPath,
      photoOriginalPath: f.photoOriginalPath
    })
  })

  if (!photoMap.size) {
    const fallback = {
      id: face.photoId ?? face.id,
      filename: face.photoFilename || '',
      originalPath: face.photoOriginalPath || face.photoThumbnailPath || '',
      thumbnailPath: face.photoThumbnailPath || face.photoOriginalPath || '',
      webpPath: undefined,
      faces: []
    }
    viewerPhotos.value = [fallback]
    viewerIndex.value = 0
    viewerVisible.value = true
    return
  }

  viewerPhotos.value = Array.from(photoMap.values())
  const targetId = face.photoId ?? face.id
  const idx = viewerPhotos.value.findIndex(p => p.id === targetId)
  viewerIndex.value = idx >= 0 ? idx : 0
  viewerVisible.value = true
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

watch(tab, (v) => {
  if (v === 'similar' && selectedPersonId.value) {
    loadSimilarFaces()
  } else if (v === 'unassigned') {
    loadContextualUnassigned()
  } else if (v === 'auto' && selectedPersonId.value) {
    loadAutoAssignedFaces()
  } else if (v === 'confirmed') {
    if (selectedPersonId.value) {
      loadConfirmedFaces()
    } else if (selectedClusterIndex.value !== null) {
      loadClusterFaces()
    }
  }
})

watch(currentFaceTab, (v) => {
  if (v && (faceVisibleLimits[v] || 0) === 0 && getCurrentFaceList(v).length > 0) {
    resetFaceVisible(v)
  }
})

let resizeObserver: ResizeObserver | null = null
let faceResizeObserver: ResizeObserver | null = null
let albumResizeObserver: ResizeObserver | null = null

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
      })
      faceResizeObserver.observe(tabScrollContainer.value)
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

  try {
    // 获取当前聚类的人脸ID
    const res = await api.get(`/admin/clusters/${selectedItem.value.id}/faces`, {
      params: { threshold: clusterThreshold.value }
    })
    const faces = res.data || []
    const faceIds = faces.map((f: FaceItem) => f.id)

    if (faceIds.length === 0) return

    // 将人脸分配到目标人物
    await Promise.all(
      faceIds.map((faceId: number) =>
        api.put(`/admin/faces/${faceId}/assign`, null, {
          params: { personId: targetPerson.id, confirmed: true }
        })
      )
    )

    // 刷新人物列表
    await loadPersons()
    // 清空选择
    selectedItem.value = null
    selectedClusterIndex.value = null
  } catch (error) {
    console.error('合并到现有人物失败:', error)
    alert('合并失败，请重试')
  }
}

const handleGlobalKeydown = (e: KeyboardEvent) => {
  if (e.key === 'Escape') {
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
</style>
