<template>
  <div class="h-screen bg-gray-900 text-white flex flex-col overflow-hidden">
    <div class="flex-shrink-0 px-4 sm:px-6 lg:px-8 py-4 border-b border-gray-700">
      <div class="flex items-center justify-between">
        <div>
          <h1 class="text-xl font-light">人物管理</h1>
          <p class="text-xs text-gray-400 mt-0.5">基于人脸聚合的人物，支持快速批量操作</p>
        </div>
        <router-link to="/admin" class="px-3 py-1.5 bg-gray-700 hover:bg-gray-600 rounded text-sm">返回</router-link>
      </div>
    </div>

    <div class="flex-1 flex gap-2 overflow-hidden px-4 sm:px-6 lg:px-8 py-4">
      <!-- 左侧人物头像列表 -->
      <div 
        class="bg-gray-800 rounded-lg p-3 flex flex-col flex-shrink-0"
        :style="{ width: leftPanelWidth + 'px', minWidth: '200px', maxWidth: '500px' }"
      >
        <div class="mb-3">
          <input
            v-model="personKeyword"
            @input="loadPersons"
            placeholder="搜索..."
            class="w-full px-2 py-1.5 bg-gray-700 border border-gray-600 rounded text-xs focus:outline-none focus:ring-1 focus:ring-blue-500"
          />
        </div>
        <div 
          ref="personListContainer"
          class="flex-1 overflow-y-auto"
          :style="{ display: 'grid', gridTemplateColumns: `repeat(${personColumns}, 1fr)`, gap: '8px', alignContent: 'start', gridAutoFlow: 'row' }"
        >
          <!-- 已确认人物 -->
          <div
            v-for="p in confirmedPersons"
            :key="`confirmed-${p.id}`"
            class="flex flex-col items-center p-1.5 rounded cursor-pointer transition-all border border-transparent bg-gray-800/70 hover:bg-gray-700/80"
            :class="isSelected(p) ? 'border-2 border-blue-500 bg-gray-700/80' : ''"
            @click="selectPerson(p)"
          >
            <div 
              class="w-12 h-12 rounded-full bg-gray-600 overflow-hidden mb-1 cursor-pointer"
              @click.stop="selectPerson(p)"
            >
              <img v-if="getPersonThumb(p)" :src="getPersonThumb(p)" class="w-full h-full object-cover" />
            </div>
            <div class="text-center w-full">
              <input
                v-if="editingPersonId === p.id"
                v-model="editingName"
                @blur="handleNameBlur(p)"
                @keyup.enter="savePersonName(p)"
                @keyup.esc="cancelEdit"
                class="w-full px-1.5 py-0.5 bg-gray-600 border border-blue-500 rounded text-xs text-center focus:outline-none focus:ring-1 focus:ring-blue-500"
                @click.stop
                :ref="el => { if (editingPersonId === p.id && el) (el as HTMLInputElement).focus() }"
              />
              <div
                v-else
                @click.stop="startEditName(p)"
                class="font-medium text-xs truncate cursor-text hover:text-blue-400 transition-colors"
                :title="p.name || '未命名'"
              >
                {{ p.name || '未命名' }}
              </div>
              <div class="text-[10px] text-gray-400">({{ p.faceCount || 0 }})</div>
            </div>
          </div>
          
          <!-- 未确认聚类 -->
          <div
            v-for="p in clusterPersons"
            :key="`cluster-${p.id}`"
            class="flex flex-col items-center p-1.5 rounded cursor-pointer transition-all border border-transparent bg-gray-800/60 hover:bg-gray-700/70"
            :class="isSelected(p) ? 'border-2 border-yellow-500 bg-gray-700/80' : ''"
            @click="selectPerson(p)"
          >
            <div 
              class="w-12 h-12 rounded-full bg-gray-600 overflow-hidden mb-1 cursor-pointer"
              @click.stop="selectPerson(p)"
            >
              <img v-if="getPersonThumb(p)" :src="getPersonThumb(p)" class="w-full h-full object-cover" />
            </div>
            <div class="text-center w-full">
              <input
                v-if="editingPersonId === p.id"
                v-model="editingName"
                @blur="handleNameBlur(p)"
                @keyup.enter="createPersonFromName(p)"
                @keyup.esc="cancelEdit"
                class="w-full px-1.5 py-0.5 bg-gray-600 border border-yellow-500 rounded text-xs text-center focus:outline-none focus:ring-1 focus:ring-yellow-500"
                @click.stop
                :ref="el => { if (editingPersonId === p.id && el) (el as HTMLInputElement).focus() }"
                placeholder="输入姓名"
              />
              <div
                v-else
                @click.stop="startEditName(p)"
                class="font-medium text-xs truncate cursor-text hover:text-yellow-400 transition-colors text-yellow-400"
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

        <!-- 选中人物的备注和删除按钮 -->
        <div v-if="selectedItem && selectedItem.type === 'confirmed'" class="mt-3 pt-3 border-t border-gray-700 space-y-2">
          <div>
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
          <button
            @click="deletePerson"
            class="w-full px-2 py-1.5 bg-red-600 hover:bg-red-700 rounded text-xs transition-colors"
          >
            删除人物
          </button>
        </div>
      </div>

      <!-- 可拖拽分割线 -->
      <div
        class="w-1 bg-gray-700 cursor-col-resize hover:bg-gray-600 transition-colors flex-shrink-0"
        @mousedown="startResize"
      ></div>

      <!-- 右侧内容区域 -->
      <div class="flex-1 bg-gray-800 rounded-lg p-3 overflow-hidden flex flex-col min-w-0">
        <div v-if="!selectedItem" class="text-gray-400 text-xs text-center py-8 flex-1 flex items-center justify-center">
          请从左侧选择一个人物
        </div>
        <div v-else class="flex-1 flex flex-col overflow-hidden">
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
          <div class="flex-1 overflow-y-auto pr-1">
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
                  v-for="(f, index) in confirmedFaces"
                  :key="f.id"
                  :data-face-id="f.id"
                  :data-face-index="index"
                  class="bg-gray-700 rounded overflow-hidden border relative group select-none"
                  :class="selectedConfirmed.has(f.id) ? 'border-2 border-blue-500' : 'border-gray-600'"
                  @click="handleFaceClick($event, f.id, 'confirmed')"
                >
                  <label class="absolute top-1 left-1 bg-black/70 px-1.5 py-0.5 rounded text-[10px] z-10 cursor-pointer">
                    <input type="checkbox" :checked="selectedConfirmed.has(f.id)" @change.stop="toggleSelectConfirmed(f.id)" />
                  </label>
                  <div class="relative h-32 bg-gray-800 overflow-hidden">
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
                    <div class="text-[10px] text-gray-300 truncate" :title="f.photoFilename">{{ f.photoFilename || '-' }}</div>
                    <button @click.stop="openPhoto(f.photoId)" class="text-blue-400 text-[10px] hover:underline mt-0.5">查看</button>
                  </div>
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
                    :disabled="selectedAuto.size === 0"
                    class="px-2 py-1 bg-blue-600 hover:bg-blue-700 rounded text-[10px] disabled:opacity-50"
                  >
                    认领<template v-if="selectedAuto.size > 0"> ({{ selectedAuto.size }})</template>
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
                  v-for="(f, index) in autoAssignedFaces"
                  :key="f.id"
                  :data-face-id="f.id"
                  :data-face-index="index"
                  class="bg-gray-700 rounded overflow-hidden border relative group select-none"
                  :class="selectedAuto.has(f.id) ? 'border-2 border-blue-500' : 'border-orange-600/50'"
                  @click="handleFaceClick($event, f.id, 'auto')"
                >
                  <label class="absolute top-1 left-1 bg-black/70 px-1.5 py-0.5 rounded text-[10px] z-10 cursor-pointer">
                    <input type="checkbox" :checked="selectedAuto.has(f.id)" @change.stop="toggleSelectAuto(f.id)" />
                  </label>
                  <div class="relative h-32 bg-gray-800 overflow-hidden">
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
                    <div class="text-[10px] text-gray-300 truncate" :title="f.photoFilename">{{ f.photoFilename || '-' }}</div>
                    <button @click.stop="openPhoto(f.photoId)" class="text-blue-400 text-[10px] hover:underline mt-0.5">查看</button>
                  </div>
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
                <span class="text-xs text-gray-400">按相似度排序的可能照片（60%-75%）</span>
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
                    :disabled="selectedSimilar.size === 0"
                    class="px-2 py-1 bg-blue-600 hover:bg-blue-700 rounded text-[10px] disabled:opacity-50"
                  >
                    认领<template v-if="selectedSimilar.size > 0"> ({{ selectedSimilar.size }})</template>
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
                  v-for="(f, index) in similarFaces"
                  :key="f.id"
                  :data-face-id="f.id"
                  :data-face-index="index"
                  class="bg-gray-700 rounded overflow-hidden border relative group select-none"
                  :class="selectedSimilar.has(f.id) ? 'border-2 border-blue-500' : 'border-green-600/50'"
                  @click="handleFaceClick($event, f.id, 'similar')"
                >
                  <label class="absolute top-1 left-1 bg-black/70 px-1.5 py-0.5 rounded text-[10px] z-10 cursor-pointer">
                    <input type="checkbox" :checked="selectedSimilar.has(f.id)" @change.stop="toggleSelectSimilar(f.id)" />
                  </label>
                  <div class="absolute top-1 right-1 bg-green-600/80 px-1.5 py-0.5 rounded text-[10px] z-10">
                    {{ ((f.similarity || 0) * 100).toFixed(0) }}%
                  </div>
                  <div class="relative h-32 bg-gray-800 overflow-hidden">
                    <img
                      v-if="getFaceThumb(f)"
                      :src="getFaceThumb(f)"
                      class="w-full h-full object-cover pointer-events-none"
                      :style="getFaceCropStyle(f)"
                      loading="lazy"
                    />
                    <button
                      @click.stop="assignFace(f.id, false)"
                      class="absolute bottom-1 right-1 bg-blue-600 hover:bg-blue-700 text-white px-1.5 py-0.5 rounded text-[10px] opacity-0 group-hover:opacity-100 transition-opacity"
                    >
                      认领
                    </button>
                  </div>
                  <div class="p-1.5">
                    <div class="text-[10px] text-gray-300 truncate" :title="f.photoFilename">{{ f.photoFilename || '-' }}</div>
                    <button @click.stop="openPhoto(f.photoId)" class="text-blue-400 text-[10px] hover:underline mt-0.5">查看</button>
                  </div>
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
                <span class="text-xs text-gray-400">同一文件夹的相似人脸（50%-60%）</span>
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
                    :disabled="selectedSameFolder.size === 0"
                    class="px-2 py-1 bg-blue-600 hover:bg-blue-700 rounded text-[10px] disabled:opacity-50"
                  >
                    认领<template v-if="selectedSameFolder.size > 0"> ({{ selectedSameFolder.size }})</template>
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
                  v-for="(f, index) in sameFolderFaces"
                  :key="f.id"
                  :data-face-id="f.id"
                  :data-face-index="index"
                  class="bg-gray-700 rounded overflow-hidden border relative group select-none"
                  :class="selectedSameFolder.has(f.id) ? 'border-2 border-blue-500' : 'border-purple-600/50'"
                  @click="handleFaceClick($event, f.id, 'sameFolder')"
                >
                  <label class="absolute top-1 left-1 bg-black/70 px-1.5 py-0.5 rounded text-[10px] z-10 cursor-pointer">
                    <input type="checkbox" :checked="selectedSameFolder.has(f.id)" @change.stop="toggleSelectSameFolder(f.id)" />
                  </label>
                  <div class="absolute top-1 right-1 bg-purple-600/80 px-1.5 py-0.5 rounded text-[10px] z-10">
                    {{ ((f.similarity || 0) * 100).toFixed(0) }}%
                  </div>
                  <div class="relative h-32 bg-gray-800 overflow-hidden">
                    <img
                      v-if="getFaceThumb(f)"
                      :src="getFaceThumb(f)"
                      class="w-full h-full object-cover pointer-events-none"
                      :style="getFaceCropStyle(f)"
                      loading="lazy"
                    />
                    <button
                      @click.stop="assignFace(f.id, false)"
                      class="absolute bottom-1 right-1 bg-blue-600 hover:bg-blue-700 text-white px-1.5 py-0.5 rounded text-[10px] opacity-0 group-hover:opacity-100 transition-opacity"
                    >
                      认领
                    </button>
                  </div>
                  <div class="p-1.5">
                    <div class="text-[10px] text-gray-300 truncate" :title="f.photoFilename">{{ f.photoFilename || '-' }}</div>
                    <button @click.stop="openPhoto(f.photoId)" class="text-blue-400 text-[10px] hover:underline mt-0.5">查看</button>
                  </div>
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
                  v-for="(f, index) in unassignedFaces"
                  :key="f.id"
                  :data-face-id="f.id"
                  :data-face-index="index"
                  class="bg-gray-700 rounded overflow-hidden border relative group select-none"
                  :class="selectedUnassigned.has(f.id) ? 'border-2 border-blue-500' : 'border-gray-600'"
                  @click="handleFaceClick($event, f.id, 'unassigned')"
                >
                  <label v-if="selectedItem.type === 'confirmed'" class="absolute top-1 left-1 bg-black/70 px-1.5 py-0.5 rounded text-[10px] z-10 cursor-pointer">
                    <input type="checkbox" :checked="selectedUnassigned.has(f.id)" @change.stop="toggleSelectUnassigned(f.id)" />
                  </label>
                  <div class="relative h-32 bg-gray-800 overflow-hidden">
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
                    <div class="text-[10px] text-gray-300 truncate" :title="f.photoFilename">{{ f.photoFilename || '-' }}</div>
                    <button @click.stop="openPhoto(f.photoId)" class="text-blue-400 text-[10px] hover:underline mt-0.5">查看</button>
                  </div>
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
                  v-for="(f, index) in personFaces"
                  :key="f.id"
                  :data-face-id="f.id"
                  :data-face-index="index"
                  class="bg-gray-700 rounded overflow-hidden border relative group select-none"
                  :class="selectedClusterFaces.has(f.id) ? 'border-2 border-blue-500' : 'border-gray-600'"
                  @click="handleFaceClick($event, f.id, 'cluster')"
                >
                  <label class="absolute top-1 left-1 bg-black/70 px-1.5 py-0.5 rounded text-[10px] z-10 cursor-pointer">
                    <input type="checkbox" :checked="selectedClusterFaces.has(f.id)" @change.stop="toggleSelectCluster(f.id)" />
                  </label>
                  <div class="relative h-32 bg-gray-800 overflow-hidden">
                    <img
                      v-if="getFaceThumb(f)"
                      :src="getFaceThumb(f)"
                      class="w-full h-full object-cover pointer-events-none"
                      :style="getFaceCropStyle(f)"
                      loading="lazy"
                    />
                  </div>
                  <div class="p-1.5">
                    <div class="text-[10px] text-gray-300 truncate" :title="f.photoFilename">{{ f.photoFilename || '-' }}</div>
                    <button @click.stop="openPhoto(f.photoId)" class="text-blue-400 text-[10px] hover:underline mt-0.5">查看</button>
                  </div>
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
</template>

<script setup lang="ts">
import { ref, onMounted, watch, computed, nextTick, onBeforeUnmount } from 'vue'
import { api } from '@/api'

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
const clusterThreshold = ref(0.65)

// 编辑相关
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
  if (width < 280) return 2
  if (width < 360) return 3
  if (width < 440) return 4
  if (width < 520) return 5
  return 6
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
    editingDescription.value = p.description || ''
    originalDescription.value = p.description || ''
    tab.value = 'confirmed'
    loadAllFaces()
  } else {
    selectedPersonId.value = null
    selectedClusterIndex.value = p.id as number
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

const loadConfirmedFaces = async () => {
  if (!selectedPersonId.value) return
  loadingConfirmed.value = true
  try {
    const res = await api.get(`/admin/persons/${selectedPersonId.value}/faces/confirmed`, {
      params: { page: 0, size: 200 }
    })
    confirmedFaces.value = res.data.content || res.data || []
    selectedConfirmed.value.clear()
    
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
    // 过滤相似度在60%-75%之间的
    similarFaces.value = (res.data || []).filter((f: FaceItem) => {
      const sim = f.similarity || 0
      return sim >= 0.6 && sim < 0.75
    })
    selectedSimilar.value.clear()
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
  } finally {
    loadingUnassigned.value = false
  }
}

const startEditName = (p: PersonListItem) => {
  editingPersonId.value = p.id
  editingName.value = p.name || ''
  originalName.value = p.name || ''
}

const cancelEdit = () => {
  editingName.value = originalName.value
  editingPersonId.value = null
}

const cancelDescriptionEdit = () => {
  editingDescription.value = originalDescription.value
}

const savePersonName = async (p: PersonListItem) => {
  if (p.type !== 'confirmed' || !selectedPersonId.value) {
    cancelEdit()
    return
  }
  
  const newName = editingName.value.trim()
  if (!newName || newName === originalName.value) {
    cancelEdit()
    return
  }
  
  savingPerson.value = true
  try {
    await api.put(`/admin/persons/${selectedPersonId.value}`, {
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

const handleNameBlur = (p: PersonListItem) => {
  if (editingPersonId.value !== p.id) return
  if (p.type === 'confirmed') {
    savePersonName(p)
  } else {
    createPersonFromName(p)
  }
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

const confirmFace = async (faceId: number) => {
  await assignFace(faceId, true)
}

const confirmSelectedAuto = async () => {
  const ids = Array.from(selectedAuto.value)
  for (const id of ids) {
    await confirmFace(id)
  }
  selectedAuto.value.clear()
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
}

const assignSelectedSimilar = async () => {
  const ids = Array.from(selectedSimilar.value)
  for (const id of ids) {
    await assignFace(id, false) // 自动分配，不确认
  }
  selectedSimilar.value.clear()
}

const assignSelectedSameFolder = async () => {
  const ids = Array.from(selectedSameFolder.value)
  for (const id of ids) {
    await assignFace(id, false) // 自动分配，不确认
  }
  selectedSameFolder.value.clear()
}

const assignSelectedUnassigned = async () => {
  const ids = Array.from(selectedUnassigned.value)
  for (const id of ids) {
    await assignFace(id, false) // 自动分配，不确认
  }
  selectedUnassigned.value.clear()
}

const unassignFace = async (faceId: number) => {
  await api.put(`/admin/faces/${faceId}/assign`, null, { 
    params: { personId: null } 
  })
  await loadAllFaces()
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

const getFaceCropStyle = (face: FaceItem) => {
  const thumb = getFaceThumb(face)
  const hasSize = face.width && face.height && face.width > 0 && face.height > 0
  
  if (!thumb || !hasSize || !face.x || !face.y) {
    return {}
  }
  
  const x = Math.max(0, Math.min(1, face.x))
  const y = Math.max(0, Math.min(1, face.y))
  const w = Math.max(0.01, Math.min(1, face.width!))
  const h = Math.max(0.01, Math.min(1, face.height!))
  
  const scaleX = 1 / w
  const scaleY = 1 / h
  const scale = Math.min(3, Math.max(1, Math.min(scaleX, scaleY)))
  
  const faceCenterX = x + w / 2
  const faceCenterY = y + h / 2
  
  const offsetX = (0.5 - faceCenterX) * scale * 100
  const offsetY = (0.5 - faceCenterY) * scale * 100
  
  const maxOffset = 50
  const clampedOffsetX = Math.min(maxOffset, Math.max(-maxOffset, offsetX))
  const clampedOffsetY = Math.min(maxOffset, Math.max(-maxOffset, offsetY))
  
  return {
    width: `${scale * 100}%`,
    height: `${scale * 100}%`,
    objectPosition: `${50 + clampedOffsetX}% ${50 + clampedOffsetY}%`,
    objectFit: 'cover'
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

let resizeObserver: ResizeObserver | null = null

onMounted(() => {
  loadPersons()
  nextTick(() => {
    updateContainerWidth()
    if (personListContainer.value && 'ResizeObserver' in window) {
      resizeObserver = new ResizeObserver(() => {
        updateContainerWidth()
      })
      resizeObserver.observe(personListContainer.value)
    }
    window.addEventListener('resize', updateContainerWidth)
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
  window.removeEventListener('resize', updateContainerWidth)
})
</script>
