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
        class="bg-gray-800 rounded-lg p-3 flex flex-col flex-shrink-0"
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
              class="w-16 px-2 py-1 bg-gray-700 border border-gray-600 rounded text-xs focus:outline-none focus:ring-1 focus:ring-blue-500"
            />
          </div>
        </div>
        <div 
          ref="personListContainer"
          class="flex-1 overflow-y-auto"
          :style="{ display: 'grid', gridTemplateColumns: `repeat(${personColumns}, 1fr)`, gap: '8px', alignContent: 'start', gridAutoFlow: 'row' }"
        >
          <!-- 已确认人物 -->
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
          <div class="mt-3 flex items-center justify-between text-[11px] text-gray-300">
            <div>共 {{ persons.length }} 个</div>
            <div class="flex items-center gap-1">
              <button
                class="px-1.5 py-1 bg-gray-700 hover:bg-gray-600 rounded disabled:opacity-50"
                :disabled="personPage <= 1"
                @click="prevPersonPage"
              >
                ‹
              </button>
              <div class="flex items-center gap-1">
                <span>第</span>
                <input
                  type="number"
                  min="1"
                  :max="personTotalPages"
                  v-model.number="personPage"
                  @blur="onPersonPageBlur"
                  @keyup.enter="onPersonPageBlur"
                  class="no-spinner w-10 px-1 py-0.5 bg-gray-700 border border-gray-600 rounded text-xs text-center focus:outline-none focus:ring-1 focus:ring-blue-500"
                />
                <span>/ {{ personTotalPages }}</span>
              </div>
              <button
                class="px-1.5 py-1 bg-gray-700 hover:bg-gray-600 rounded disabled:opacity-50"
                :disabled="personPage >= personTotalPages"
                @click="nextPersonPage"
              >
                ›
              </button>
            </div>
          </div>

        <!-- 选中人物的姓名 / 备注 / 删除按钮 -->
        <div v-if="selectedItem" class="mt-3 pt-3 border-t border-gray-700 space-y-2">
          <div>
            <label class="text-[10px] text-gray-400 mb-1 block">姓名</label>
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
            </div>
          </div>
          <div v-if="selectedItem.type === 'confirmed'">
            <label class="text-[10px] text-gray-400 mb-1 block">备注</label>
            <textarea
              v-model="editingDescription"
              @blur="savePersonDescription"
              @keyup.esc="cancelDescriptionEdit"
              rows="2"
              class="w-full px-2 py-1 bg-gray-700 border border-gray-600 rounded text-xs focus:outline-none focus:ring-1 focus:ring-blue-500 resize-none"
              placeholder="添加备注..."
            ></textarea>
          </div>
          <div v-if="selectedItem.type === 'confirmed'" class="flex gap-2">
            <button
              @click="dissolvePerson"
              class="flex-1 px-2 py-1.5 bg-amber-600 hover:bg-amber-700 rounded text-xs transition-colors"
            >
              解散人物
            </button>
            <button
              @click="deletePerson"
              class="flex-1 px-2 py-1.5 bg-red-600 hover:bg-red-700 rounded text-xs transition-colors"
            >
              删除人物
            </button>
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
        <!-- 全屏遮罩等待圈，阻止操作 -->
        <div
          v-if="showLoadingOverlay"
          class="absolute inset-0 z-30 bg-black/50 flex items-center justify-center"
        >
          <div class="h-10 w-10 rounded-full border-2 border-blue-400 border-t-transparent animate-spin"></div>
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
          <div class="flex gap-1 mb-3 border-b border-gray-700 flex-shrink-0 overflow-x-auto items-center">
            <div class="flex gap-1 flex-1 overflow-x-auto">
              <button
                v-if="selectedItem.type === 'confirmed' || selectedItem.type === 'cluster'"
                class="px-3 py-1.5 rounded-t text-xs transition-colors whitespace-nowrap"
                :class="tab === 'confirmed' ? 'bg-gray-700 text-blue-400 border-b-2 border-blue-400' : 'text-gray-400 hover:text-gray-200'"
                @click="tab = 'confirmed'"
              >
                <template v-if="selectedItem.type === 'confirmed'">已确认 ({{ confirmedFaces.length }})</template>
                <template v-else>聚类 ({{ personFaces.length }})</template>
              </button>
              <button
                v-if="selectedItem.type === 'confirmed'"
                class="px-3 py-1.5 rounded-t text-xs transition-colors whitespace-nowrap"
                :class="tab === 'auto' ? 'bg-gray-700 text-orange-400 border-b-2 border-orange-400' : 'text-gray-400 hover:text-gray-200'"
                @click="tab = 'auto'"
              >
                自动分配 ({{ autoAssignedFaces.length }})
              </button>
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
                :class="tab === 'sameFolder' ? 'bg-gray-700 text-purple-400 border-b-2 border-purple-400' : 'text-gray-400 hover:text-gray-200'"
                @click="tab = 'sameFolder'"
              >
                套图推荐 ({{ sameFolderFaces.length }})
              </button>
              <button
                class="px-3 py-1.5 rounded-t text-xs transition-colors whitespace-nowrap"
                :class="tab === 'unassigned' ? 'bg-gray-700 text-gray-300 border-b-2 border-gray-300' : 'text-gray-400 hover:text-gray-200'"
                @click="tab = 'unassigned'"
              >
                未分配 ({{ unassignedFaces.length }})
              </button>
            </div>
          </div>

          <!-- Tab内容 -->
          <div class="flex-1 overflow-y-auto pr-1" ref="tabScrollContainer" @scroll.passive="handleFaceScroll">
            <!-- 已确认照片 -->
            <div v-if="tab === 'confirmed' && selectedItem.type === 'confirmed'">
              <div class="mb-2 flex items-center justify-between">
                <span class="text-xs text-gray-400">已确认的人脸</span>
                <div class="flex gap-2">
                  <button
                    @click="selectAllCurrentTab"
                    :disabled="confirmedFaces.length === 0"
                    class="px-2 py-1 bg-gray-600 hover:bg-gray-500 rounded text-[10px] disabled:opacity-50"
                  >
                    全选
                  </button>
                  <button
                    @click="invertSelection('confirmed')"
                    :disabled="confirmedFaces.length === 0"
                    class="px-2 py-1 bg-gray-600 hover:bg-gray-500 rounded text-[10px] disabled:opacity-50"
                  >
                    反选
                  </button>
                  <button
                    @click="removeSelectedConfirmed"
                    :disabled="selectedConfirmed.size === 0"
                    class="px-2 py-1 bg-red-600 hover:bg-red-700 rounded text-[10px] disabled:opacity-50"
                  >
                    移除<template v-if="selectedConfirmed.size > 0"> ({{ selectedConfirmed.size }})</template>
                  </button>
                </div>
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

            <!-- 自动分配照片 -->
            <div v-if="tab === 'auto' && selectedItem.type === 'confirmed'">
              <div class="mb-2 flex items-center justify-between">
                <span class="text-xs text-gray-400">自动分配的人脸（相似度≥75%）</span>
                <div class="flex gap-2">
                  <button
                    @click="selectAllCurrentTab"
                    :disabled="autoAssignedFaces.length === 0"
                    class="px-2 py-1 bg-gray-600 hover:bg-gray-500 rounded text-[10px] disabled:opacity-50"
                  >
                    全选
                  </button>
                  <button
                    @click="invertSelection('auto')"
                    :disabled="autoAssignedFaces.length === 0"
                    class="px-2 py-1 bg-gray-600 hover:bg-gray-500 rounded text-[10px] disabled:opacity-50"
                  >
                    反选
                  </button>
                  <button
                    @click="confirmSelectedAuto"
                    :disabled="autoAssignedFaces.length === 0"
                    class="px-2 py-1 bg-blue-600 hover:bg-blue-700 rounded text-[10px] disabled:opacity-50"
                  >
                    <template v-if="selectedAuto.size === 0">
                      认领全部
                    </template>
                    <template v-else>
                      认领 ({{ selectedAuto.size }})
                    </template>
                  </button>
                  <button
                    @click="removeSelectedAuto"
                    :disabled="selectedAuto.size === 0"
                    class="px-2 py-1 bg-red-600 hover:bg-red-700 rounded text-[10px] disabled:opacity-50"
                  >
                    移除<template v-if="selectedAuto.size > 0"> ({{ selectedAuto.size }})</template>
                  </button>
                </div>
              </div>
              <div 
                ref="autoContainer"
                class="grid grid-cols-3 md:grid-cols-4 xl:grid-cols-5 2xl:grid-cols-6 gap-2 pb-4 relative"
                @mousedown="handleMouseDown($event, 'auto')"
                @mousemove="handleMouseMove($event, 'auto')"
                @mouseup="handleMouseUp($event, 'auto')"
                @mouseleave="handleMouseUp($event, 'auto')"
              >
                <div
                  v-for="(f, index) in visibleAutoFaces"
                  :key="f.id"
                  :data-face-id="f.id"
                  :data-face-index="index"
                  class="bg-gray-700 rounded overflow-hidden border relative group select-none"
                  :class="selectedAuto.has(f.id) ? 'border-2 border-blue-500' : 'border-orange-600/50'"
                  @click="handleFaceClick($event, f.id, 'auto')"
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
                      @click.stop="confirmFace(f.id)"
                      class="absolute bottom-1 right-1 bg-green-600 hover:bg-green-700 text-white px-1.5 py-0.5 rounded text-[10px] opacity-0 group-hover:opacity-100 transition-opacity"
                    >
                      确认
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
                  v-for="n in facePlaceholderCounts.auto"
                  :key="`auto-ph-${n}`"
                  class="h-40 rounded bg-gray-700/40 border border-gray-700/60 animate-pulse overflow-hidden relative"
                >
                  <div class="absolute top-0 left-0 right-0 h-32 bg-gray-600/50"></div>
                  <div class="absolute bottom-3 left-2 right-2 h-3 bg-gray-600/60 rounded"></div>
                </div>
                <!-- 框选遮罩 -->
                <div
                  v-if="isSelecting && currentTab === 'auto'"
                  class="absolute border-2 border-blue-500 bg-blue-500/20 pointer-events-none z-50"
                  :style="selectionBoxStyle"
                ></div>
              </div>
              <div v-if="autoAssignedFaces.length === 0" class="text-gray-400 text-xs text-center py-8">暂无自动分配照片</div>
            </div>

            <!-- 相似推荐 -->
            <div v-if="tab === 'similar' && selectedItem.type === 'confirmed'">
              <div class="mb-2 flex items-center justify-between">
                <span class="text-xs text-gray-400">按相似度排序的可能照片（相似度≥ 60%）</span>
                <div class="flex gap-2">
                  <button
                    @click="selectAllCurrentTab"
                    :disabled="similarFaces.length === 0"
                    class="px-2 py-1 bg-gray-600 hover:bg-gray-500 rounded text-[10px] disabled:opacity-50"
                  >
                    全选
                  </button>
                  <button
                    @click="invertSelection('similar')"
                    :disabled="similarFaces.length === 0"
                    class="px-2 py-1 bg-gray-600 hover:bg-gray-500 rounded text-[10px] disabled:opacity-50"
                  >
                    反选
                  </button>
                  <button
                    @click="assignSelectedSimilar"
                    :disabled="similarFaces.length === 0"
                    class="px-2 py-1 bg-blue-600 hover:bg-blue-700 rounded text-[10px] disabled:opacity-50"
                  >
                    <template v-if="selectedSimilar.size === 0">
                      认领全部
                    </template>
                    <template v-else>
                      认领 ({{ selectedSimilar.size }})
                    </template>
                  </button>
                </div>
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
                  :class="selectedSimilar.has(f.id) ? 'border-2 border-blue-500' : 'border-green-600/50'"
                  @click="handleFaceClick($event, f.id, 'similar')"
                >
                  <div class="absolute top-1 right-1 bg-green-600/80 px-1.5 py-0.5 rounded text-[10px] z-10">
                    {{ ((f.similarity || 0) * 100).toFixed(0) }}%
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

            <!-- 套图推荐 -->
            <div v-if="tab === 'sameFolder' && selectedItem.type === 'confirmed'">
              <div class="mb-2 flex items-center justify-between">
                <span class="text-xs text-gray-400">同一文件夹的相似人脸（按相似度排序）</span>
                <div class="flex gap-2">
                  <button
                    @click="selectAllCurrentTab"
                    :disabled="sameFolderFaces.length === 0"
                    class="px-2 py-1 bg-gray-600 hover:bg-gray-500 rounded text-[10px] disabled:opacity-50"
                  >
                    全选
                  </button>
                  <button
                    @click="invertSelection('sameFolder')"
                    :disabled="sameFolderFaces.length === 0"
                    class="px-2 py-1 bg-gray-600 hover:bg-gray-500 rounded text-[10px] disabled:opacity-50"
                  >
                    反选
                  </button>
                  <button
                    @click="assignSelectedSameFolder"
                    :disabled="sameFolderFaces.length === 0"
                    class="px-2 py-1 bg-blue-600 hover:bg-blue-700 rounded text-[10px] disabled:opacity-50"
                  >
                    <template v-if="selectedSameFolder.size === 0">
                      认领全部
                    </template>
                    <template v-else>
                      认领 ({{ selectedSameFolder.size }})
                    </template>
                  </button>
                </div>
              </div>
              <div 
                ref="sameFolderContainer"
                class="grid grid-cols-3 md:grid-cols-4 xl:grid-cols-5 2xl:grid-cols-6 gap-2 pb-4 relative"
                @mousedown="handleMouseDown($event, 'sameFolder')"
                @mousemove="handleMouseMove($event, 'sameFolder')"
                @mouseup="handleMouseUp($event, 'sameFolder')"
                @mouseleave="handleMouseUp($event, 'sameFolder')"
              >
                <div
                  v-for="(f, index) in visibleSameFolderFaces"
                  :key="f.id"
                  :data-face-id="f.id"
                  :data-face-index="index"
                  class="bg-gray-700 rounded overflow-hidden border relative group select-none"
                  :class="selectedSameFolder.has(f.id) ? 'border-2 border-blue-500' : 'border-purple-600/50'"
                  @click="handleFaceClick($event, f.id, 'sameFolder')"
                >
                  <div class="absolute top-1 right-1 bg-purple-600/80 px-1.5 py-0.5 rounded text-[10px] z-10">
                    {{ ((f.similarity || 0) * 100).toFixed(0) }}%
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
                  v-for="n in facePlaceholderCounts.sameFolder"
                  :key="`sameFolder-ph-${n}`"
                  class="h-40 rounded bg-gray-700/40 border border-gray-700/60 animate-pulse overflow-hidden relative"
                >
                  <div class="absolute top-0 left-0 right-0 h-32 bg-gray-600/50"></div>
                  <div class="absolute bottom-3 left-2 right-2 h-3 bg-gray-600/60 rounded"></div>
                </div>
                <!-- 框选遮罩 -->
                <div
                  v-if="isSelecting && currentTab === 'sameFolder'"
                  class="absolute border-2 border-blue-500 bg-blue-500/20 pointer-events-none z-50"
                  :style="selectionBoxStyle"
                ></div>
              </div>
              <div v-if="sameFolderFaces.length === 0" class="text-gray-400 text-xs text-center py-8">暂无套图推荐</div>
            </div>

            <!-- 未分配照片 -->
            <div v-if="tab === 'unassigned'">
              <div class="mb-2 flex items-center justify-between">
                <span class="text-xs text-gray-400">所有未分配的照片</span>
                <div v-if="selectedItem.type === 'confirmed'" class="flex gap-2">
                  <button
                    @click="selectAllCurrentTab"
                    :disabled="unassignedFaces.length === 0"
                    class="px-2 py-1 bg-gray-600 hover:bg-gray-500 rounded text-[10px] disabled:opacity-50"
                  >
                    全选
                  </button>
                  <button
                    @click="invertSelection('unassigned')"
                    :disabled="unassignedFaces.length === 0"
                    class="px-2 py-1 bg-gray-600 hover:bg-gray-500 rounded text-[10px] disabled:opacity-50"
                  >
                    反选
                  </button>
                  <button
                    @click="assignSelectedUnassigned"
                    :disabled="selectedUnassigned.size === 0"
                    class="px-2 py-1 bg-blue-600 hover:bg-blue-700 rounded text-[10px] disabled:opacity-50"
                  >
                    认领<template v-if="selectedUnassigned.size > 0"> ({{ selectedUnassigned.size }})</template>
                  </button>
                </div>
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
              <div class="mb-2 flex items-center justify-between">
                <span class="text-xs text-gray-400">聚类中的人脸</span>
                <div class="flex gap-2">
                  <button
                    @click="selectAllCurrentTab"
                    :disabled="personFaces.length === 0"
                    class="px-2 py-1 bg-gray-600 hover:bg-gray-500 rounded text-[10px] disabled:opacity-50"
                  >
                    全选
                  </button>
                  <button
                    @click="invertSelection('cluster')"
                    :disabled="personFaces.length === 0"
                    class="px-2 py-1 bg-gray-600 hover:bg-gray-500 rounded text-[10px] disabled:opacity-50"
                  >
                    反选
                  </button>
                  <button
                    @click="removeSelectedClusterFaces"
                    :disabled="selectedClusterFaces.size === 0"
                    class="px-2 py-1 bg-red-600 hover:bg-red-700 rounded text-[10px] disabled:opacity-50"
                  >
                    移除<template v-if="selectedClusterFaces.size > 0"> ({{ selectedClusterFaces.size }})</template>
                  </button>
                </div>
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
  </div>
  <PhotoViewer
    v-model:visible="viewerVisible"
    :photos="viewerPhotos"
    :start-index="viewerIndex"
    :auto-show-faces="true"
  />
</template>

<script setup lang="ts">
import { ref, onMounted, watch, computed, nextTick, onBeforeUnmount, reactive } from 'vue'
import { api } from '@/api'
import PhotoViewer from '@/components/PhotoViewer.vue'

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

const STORAGE_KEY = 'pe-persons-left-width'

const persons = ref<PersonListItem[]>([])
const confirmedPersons = ref<PersonListItem[]>([])
const clusterPersons = ref<PersonListItem[]>([])
const personKeyword = ref('')
const selectedItem = ref<PersonListItem | null>(null)
const selectedPersonId = ref<number | null>(null)
const selectedClusterIndex = ref<number | null>(null)
const loadingPersons = ref(false)
// 右侧灰色等待蒙版，使用延迟显示避免快速切换时闪烁
const showLoadingOverlay = ref(false)
let loadingOverlayTimer: number | null = null
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
let thresholdTimer: number | null = null

// 左侧人物分页
const PERSON_PAGE_SIZE = 24
const personPage = ref(1)
const personTotalPages = computed(() => Math.max(1, Math.ceil(Math.max(persons.value.length, 1) / PERSON_PAGE_SIZE)))
const pagedPersons = computed(() => {
  const start = (personPage.value - 1) * PERSON_PAGE_SIZE
  return persons.value.slice(start, start + PERSON_PAGE_SIZE)
})
const visibleConfirmedPersons = computed(() => pagedPersons.value.filter(p => p.type === 'confirmed'))
const visibleClusterPersons = computed(() => pagedPersons.value.filter(p => p.type === 'cluster'))

watch(personPage, (v) => {
  if (v < 1) personPage.value = 1
  else if (v > personTotalPages.value) personPage.value = personTotalPages.value
})

const nextPersonPage = () => {
  if (personPage.value < personTotalPages.value) personPage.value += 1
}

const prevPersonPage = () => {
  if (personPage.value > 1) personPage.value -= 1
}

const onPersonPageBlur = () => {
  if (!personPage.value || isNaN(personPage.value as any)) {
    personPage.value = 1
    return
  }
  if (personPage.value < 1) personPage.value = 1
  else if (personPage.value > personTotalPages.value) personPage.value = personTotalPages.value
}

// 编辑相关（列表不再内联编辑，仅右侧姓名输入框使用）
const editingPersonId = ref<number | null>(null)
const editingName = ref('')
const originalName = ref('')
const editingDescription = ref('')
const originalDescription = ref('')
const savingPerson = ref(false)
const personListContainer = ref<HTMLElement | null>(null)

// 面板宽度和拖拽
const leftPanelWidth = ref(parseInt(localStorage.getItem(STORAGE_KEY) || '280', 10))
const isResizing = ref(false)
const resizeStartX = ref(0)
const resizeStartWidth = ref(0)
const containerWidth = ref(0)

// 列数自适应
const personColumns = computed(() => {
  const width = containerWidth.value
  if (width <= 0) return 2
  if (width < 200) return 1
  if (width < 260) return 2
  if (width < 340) return 3
  if (width < 420) return 4
  if (width < 500) return 5
  if (width < 580) return 6
  return 7
})

const tab = ref<'confirmed' | 'auto' | 'similar' | 'sameFolder' | 'unassigned'>('confirmed')

// 已确认照片
const confirmedFaces = ref<FaceItem[]>([])
const selectedConfirmed = ref<Set<number>>(new Set())
const loadingConfirmed = ref(false)

// 自动分配照片
const autoAssignedFaces = ref<FaceItem[]>([])
const selectedAuto = ref<Set<number>>(new Set())
const loadingAuto = ref(false)

// 相似推荐
const similarFaces = ref<FaceItem[]>([])
const selectedSimilar = ref<Set<number>>(new Set())
const loadingSimilar = ref(false)

// 套图推荐
const sameFolderFaces = ref<FaceItem[]>([])
const selectedSameFolder = ref<Set<number>>(new Set())
const loadingSameFolder = ref(false)

// 未分配照片
const unassignedFaces = ref<FaceItem[]>([])
const selectedUnassigned = ref<Set<number>>(new Set())
const loadingUnassigned = ref(false)

// 聚类照片（用于未确认聚类）
const personFaces = ref<FaceItem[]>([])
const selectedClusterFaces = ref<Set<number>>(new Set())
const loadingPersonFaces = ref(false)

type FaceTab = 'confirmed' | 'auto' | 'similar' | 'sameFolder' | 'unassigned' | 'cluster'
const FACE_ROWS_PER_PAGE = 3
const facePageSize = ref(0)
const faceVisibleLimits = reactive<Record<FaceTab, number>>({
  confirmed: 0,
  auto: 0,
  similar: 0,
  sameFolder: 0,
  unassigned: 0,
  cluster: 0
})
const facePlaceholderCounts = reactive<Record<FaceTab, number>>({
  confirmed: 0,
  auto: 0,
  similar: 0,
  sameFolder: 0,
  unassigned: 0,
  cluster: 0
})
const visibleFacesMap: Record<FaceTab, Ref<FaceItem[]>> = {
  confirmed: ref<FaceItem[]>([]),
  auto: ref<FaceItem[]>([]),
  similar: ref<FaceItem[]>([]),
  sameFolder: ref<FaceItem[]>([]),
  unassigned: ref<FaceItem[]>([]),
  cluster: ref<FaceItem[]>([])
}
const visibleConfirmedFaces = computed(() => visibleFacesMap.confirmed.value)
const visibleAutoFaces = computed(() => visibleFacesMap.auto.value)
const visibleSimilarFaces = computed(() => visibleFacesMap.similar.value)
const visibleSameFolderFaces = computed(() => visibleFacesMap.sameFolder.value)
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
  document.removeEventListener('mousemove', handleResize)
  document.removeEventListener('mouseup', stopResize)
}

const updateContainerWidth = () => {
  if (personListContainer.value) {
    containerWidth.value = personListContainer.value.clientWidth
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
    const res = await api.get('/admin/persons/items', { params: { threshold: clusterThreshold.value } })
    let list: PersonListItem[] = res.data || []
    if (personKeyword.value.trim()) {
      const kw = personKeyword.value.trim().toLowerCase()
      list = list.filter(p => (p.name || '').toLowerCase().includes(kw))
    }
    persons.value = list
    confirmedPersons.value = list.filter(p => p.type === 'confirmed')
    clusterPersons.value = list.filter(p => p.type === 'cluster')
    personPage.value = 1
    
    if (persons.value.length && !selectedItem.value) {
      selectPerson(persons.value[0])
    }
  } finally {
    loadingPersons.value = false
  }
}

const isSelected = (p: PersonListItem) => {
  if (!selectedItem.value) return false
  return selectedItem.value.type === p.type && selectedItem.value.id === p.id
}

const selectPerson = (p: PersonListItem) => {
  selectedItem.value = p
  if (p.type === 'confirmed') {
    selectedPersonId.value = p.id
    selectedClusterIndex.value = null
    selectedPersonName.value = p.name || '未命名'
    originalSelectedPersonName.value = selectedPersonName.value
    editingDescription.value = p.description || ''
    originalDescription.value = p.description || ''
    tab.value = 'confirmed'
    loadAllFaces()
  } else {
    selectedPersonId.value = null
    selectedClusterIndex.value = p.id as number
    selectedPersonName.value = ''
    originalSelectedPersonName.value = ''
    editingDescription.value = ''
    originalDescription.value = ''
    tab.value = 'confirmed'
    loadClusterFaces()
  }
}

const loadAllFaces = async () => {
  if (!selectedPersonId.value) return
  // 先加载已确认照片，然后根据结果决定是否切换tab
  await loadConfirmedFaces()
  loadAutoAssignedFaces()
  loadSimilarFaces()
  loadSameFolderFaces()
  loadUnassigned()
}

// 人脸认领 / 移除后，刷新左侧人物数量并保持当前选中与页码
const refreshPersonsAfterFaceChange = async () => {
  const current = selectedItem.value
    ? { id: selectedItem.value.id, type: selectedItem.value.type }
    : null
  const currentPage = personPage.value

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

  if (personTotalPages.value > 0) {
    const safePage = Math.min(
      Math.max(currentPage || 1, 1),
      personTotalPages.value
    )
    personPage.value = safePage
  }
}

const loadConfirmedFaces = async () => {
  if (!selectedPersonId.value) return
  loadingConfirmed.value = true
  try {
    const res = await api.get(`/admin/persons/${selectedPersonId.value}/faces/confirmed`, {
      params: { page: 0, size: 200 }
    })
    confirmedFaces.value = res.data.content || res.data || []
    selectedConfirmed.value.clear()
    resetFaceVisible('confirmed')
    
    // 如果已确认tab没有照片，自动切换到自动分配tab
    if (confirmedFaces.value.length === 0 && tab.value === 'confirmed' && selectedItem.value?.type === 'confirmed') {
      tab.value = 'auto'
    }
  } finally {
    loadingConfirmed.value = false
  }
}

const loadAutoAssignedFaces = async () => {
  if (!selectedPersonId.value) return
  loadingAuto.value = true
  try {
    const res = await api.get(`/admin/persons/${selectedPersonId.value}/faces/auto-assigned`, {
      params: { page: 0, size: 200 }
    })
    autoAssignedFaces.value = res.data.content || res.data || []
    selectedAuto.value.clear()
    resetFaceVisible('auto')
  } finally {
    loadingAuto.value = false
  }
}

const loadSimilarFaces = async () => {
  if (!selectedPersonId.value) return
  loadingSimilar.value = true
  try {
    const res = await api.get(`/admin/persons/${selectedPersonId.value}/similar-unassigned`, {
      params: { top: 200, threshold: 0.6 }
    })
    // 不再设置相似度上限，只依赖后端的下限阈值
    similarFaces.value = (res.data || []).filter((f: FaceItem) => (f.similarity || 0) >= 0.6)
    selectedSimilar.value.clear()
    resetFaceVisible('similar')
  } finally {
    loadingSimilar.value = false
  }
}

const loadSameFolderFaces = async () => {
  if (!selectedPersonId.value) return
  loadingSameFolder.value = true
  try {
    const res = await api.get(`/admin/persons/${selectedPersonId.value}/faces/same-folder`, {
      params: { top: 200 }
    })
    sameFolderFaces.value = res.data || []
    selectedSameFolder.value.clear()
    resetFaceVisible('sameFolder')
  } finally {
    loadingSameFolder.value = false
  }
}

const loadClusterFaces = async () => {
  if (selectedClusterIndex.value === null) return
  loadingPersonFaces.value = true
  try {
    const res = await api.get(`/admin/clusters/${selectedClusterIndex.value}/faces`, {
      params: { threshold: clusterThreshold.value }
    })
    personFaces.value = res.data || []
    selectedClusterFaces.value.clear()
    resetFaceVisible('cluster')
  } finally {
    loadingPersonFaces.value = false
  }
}

const loadUnassigned = async () => {
  loadingUnassigned.value = true
  try {
    const res = await api.get('/admin/faces/unassigned', { params: { page: 0, size: 200 } })
    unassignedFaces.value = res.data.content || res.data || []
    selectedUnassigned.value.clear()
    resetFaceVisible('unassigned')
  } finally {
    loadingUnassigned.value = false
  }
}

// 监听 loading 状态，延迟显示等待蒙版，避免快速切换人物时闪烁
watch(
  () => loadingPersons.value || loadingPersonFaces.value || loadingConfirmed.value || loadingAuto.value || loadingSimilar.value || loadingSameFolder.value || loadingUnassigned.value,
  (isLoading) => {
    if (loadingOverlayTimer !== null) {
      clearTimeout(loadingOverlayTimer)
      loadingOverlayTimer = null
    }
    if (isLoading) {
      // 延迟 150ms 再显示蒙版，如果期间请求很快完成，就不显示
      loadingOverlayTimer = window.setTimeout(() => {
        showLoadingOverlay.value = true
        loadingOverlayTimer = null
      }, 150)
    } else {
      showLoadingOverlay.value = false
    }
  }
)

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
  } else if (selectedItem.value.type === 'cluster') {
    await createPersonFromSelectedCluster()
  }
}

const handleSelectedPersonNameEnter = async () => {
  await handleSelectedPersonNameBlur()
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

const deletePerson = async () => {
  if (!selectedPersonId.value || !selectedItem.value) return
  if (!confirm(`确定要删除人物 "${selectedItem.value.name || '未命名'}" 吗？这将解除所有人脸的关联。`)) return
  
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
  selectedSameFolder.value.delete(faceId)
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

  // 如果本人物已没有自动分配人脸，自动切换到“已确认”Tab
  if (autoAssignedFaces.value.length === 0 && tab.value === 'auto') {
    tab.value = 'confirmed'
  }
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

const assignSelectedSameFolder = async () => {
  if (!selectedPersonId.value) return

  const hasSelection = selectedSameFolder.value.size > 0
  const targetIds = hasSelection
    ? Array.from(selectedSameFolder.value)
    : sameFolderFaces.value.map(f => f.id)

  if (targetIds.length === 0) return

  await Promise.all(
    targetIds.map(id =>
      api.put(`/admin/faces/${id}/assign`, null, {
        params: { personId: selectedPersonId.value, confirmed: true }
      })
    )
  )

  selectedSameFolder.value.clear()
  await loadAllFaces()
  await refreshPersonsAfterFaceChange()
}

const assignSelectedUnassigned = async () => {
  const ids = Array.from(selectedUnassigned.value)
  for (const id of ids) {
    await assignFace(id, false) // 自动分配，不确认
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

const toggleSelectSameFolder = (id: number) => {
  const set = new Set(selectedSameFolder.value)
  if (set.has(id)) set.delete(id)
  else set.add(id)
  selectedSameFolder.value = set
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
const sameFolderContainer = ref<HTMLElement | null>(null)
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
    case 'sameFolder': return sameFolderContainer.value
    case 'unassigned': return unassignedContainer.value
    case 'cluster': return clusterContainer.value
    default: return null
  }
}

const getCurrentFaceList = (tabType: string): FaceItem[] => {
  switch (tabType) {
    case 'confirmed': return confirmedFaces.value
    case 'auto': return autoAssignedFaces.value
    case 'similar': return similarFaces.value
    case 'sameFolder': return sameFolderFaces.value
    case 'unassigned': return unassignedFaces.value
    case 'cluster': return personFaces.value
    default: return []
  }
}

const getCurrentSelection = (tabType: string): Ref<Set<number>> => {
  switch (tabType) {
    case 'confirmed': return selectedConfirmed
    case 'auto': return selectedAuto
    case 'similar': return selectedSimilar
    case 'sameFolder': return selectedSameFolder
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
    case 'sameFolder':
      return selectedSameFolder.value.size
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
    case 'auto': return visibleAutoFaces.value
    case 'similar': return visibleSimilarFaces.value
    case 'sameFolder': return visibleSameFolderFaces.value
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
    case 'auto':
      return autoAssignedFaces.value.length
    case 'similar':
      return similarFaces.value.length
    case 'sameFolder':
      return sameFolderFaces.value.length
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
    case 'sameFolder':
      await assignSelectedSameFolder()
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
    case 'sameFolder': return sameFolderFaces.value
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
  const centerX = ((face.x || 0) + face.width / 2) * 100
  const centerY = ((face.y || 0) + face.height / 2) * 100
  return {
    position: 'absolute',
    inset: 0,
    width: '100%',
    height: '100%',
    objectFit: 'cover',
    objectPosition: `${centerX}% ${centerY}%`
  }
}

const openPhoto = (photoId?: number) => {
  if (!photoId) return
  window.open(`/photo/${photoId}`, '_blank')
}

watch(tab, (v) => {
  if (v === 'similar' && selectedPersonId.value) {
    loadSimilarFaces()
  } else if (v === 'sameFolder' && selectedPersonId.value) {
    loadSameFolderFaces()
  } else if (v === 'unassigned') {
    loadUnassigned()
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
    window.addEventListener('resize', updateContainerWidth)
    window.addEventListener('resize', recalcFacePageSize)
  })
})

onBeforeUnmount(() => {
  if (isResizing.value) {
    stopResize()
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
  window.removeEventListener('resize', recalcFacePageSize)
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
