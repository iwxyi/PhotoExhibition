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
          :style="{ display: 'grid', gridTemplateColumns: `repeat(${personColumns}, 1fr)`, gap: '8px' }"
        >
          <!-- 已确认人物 -->
          <div
            v-for="p in confirmedPersons"
            :key="`confirmed-${p.id}`"
            class="flex flex-col items-center p-1.5 rounded cursor-pointer transition-all"
            :class="isSelected(p) ? 'ring-2 ring-blue-500' : ''"
          >
            <div 
              @click.stop="selectPerson(p)"
              class="w-12 h-12 rounded-full bg-gray-600 overflow-hidden mb-1 cursor-pointer"
            >
              <img v-if="getPersonThumb(p)" :src="getPersonThumb(p)" class="w-full h-full object-cover" />
            </div>
            <div class="text-center w-full">
              <input
                v-if="editingPersonId === p.id"
                v-model="editingName"
                @blur="cancelEdit"
                @keyup.enter="savePersonName(p)"
                @keyup.esc="cancelEdit"
                class="w-full px-1.5 py-0.5 bg-gray-600 border border-blue-500 rounded text-xs text-center focus:outline-none focus:ring-1 focus:ring-blue-500"
                @click.stop
                ref="nameInput"
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
            class="flex flex-col items-center p-1.5 rounded cursor-pointer transition-all opacity-60"
            :class="isSelected(p) ? 'ring-2 ring-yellow-500 opacity-100' : ''"
          >
            <div 
              @click.stop="selectPerson(p)"
              class="w-12 h-12 rounded-full bg-gray-600 overflow-hidden mb-1 cursor-pointer"
            >
              <img v-if="getPersonThumb(p)" :src="getPersonThumb(p)" class="w-full h-full object-cover" />
            </div>
            <div class="text-center w-full">
              <input
                v-if="editingPersonId === p.id"
                v-model="editingName"
                @blur="cancelEdit"
                @keyup.enter="createPersonFromName(p)"
                @keyup.esc="cancelEdit"
                class="w-full px-1.5 py-0.5 bg-gray-600 border border-yellow-500 rounded text-xs text-center focus:outline-none focus:ring-1 focus:ring-yellow-500"
                @click.stop
                ref="nameInput"
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
          <div class="flex gap-1 mb-3 border-b border-gray-700 flex-shrink-0 overflow-x-auto">
            <button
              v-if="selectedItem.type === 'confirmed'"
              class="px-3 py-1.5 rounded-t text-xs transition-colors whitespace-nowrap"
              :class="tab === 'confirmed' ? 'bg-gray-700 text-blue-400 border-b-2 border-blue-400' : 'text-gray-400 hover:text-gray-200'"
              @click="tab = 'confirmed'"
            >
              已确认 ({{ confirmedFaces.length }})
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

          <!-- Tab内容 -->
          <div class="flex-1 overflow-y-auto pr-1">
            <!-- 已确认照片 -->
            <div v-if="tab === 'confirmed' && selectedItem.type === 'confirmed'">
              <div class="grid grid-cols-3 md:grid-cols-4 xl:grid-cols-5 2xl:grid-cols-6 gap-2 pb-4">
                <div
                  v-for="f in confirmedFaces"
                  :key="f.id"
                  class="bg-gray-700 rounded overflow-hidden border border-gray-600 relative group"
                >
                  <div class="relative h-32 bg-gray-800 overflow-hidden">
                    <img
                      v-if="getFaceThumb(f)"
                      :src="getFaceThumb(f)"
                      class="w-full h-full object-cover"
                      :style="getFaceCropStyle(f)"
                      loading="lazy"
                    />
                    <button
                      @click="unassignFace(f.id)"
                      class="absolute top-1 right-1 bg-red-600 hover:bg-red-700 text-white px-1.5 py-0.5 rounded text-[10px] opacity-0 group-hover:opacity-100 transition-opacity"
                    >
                      移除
                    </button>
                  </div>
                  <div class="p-1.5">
                    <div class="text-[10px] text-gray-300 truncate" :title="f.photoFilename">{{ f.photoFilename || '-' }}</div>
                    <button @click="openPhoto(f.photoId)" class="text-blue-400 text-[10px] hover:underline mt-0.5">查看</button>
                  </div>
                </div>
              </div>
              <div v-if="confirmedFaces.length === 0" class="text-gray-400 text-xs text-center py-8">暂无已确认照片</div>
            </div>

            <!-- 自动分配照片 -->
            <div v-if="tab === 'auto' && selectedItem.type === 'confirmed'">
              <div class="mb-2 flex items-center justify-between">
                <span class="text-xs text-gray-400">自动分配的人脸（相似度≥75%）</span>
                <button
                  @click="confirmSelectedAuto"
                  :disabled="selectedAuto.size === 0"
                  class="px-2 py-1 bg-blue-600 hover:bg-blue-700 rounded text-[10px] disabled:opacity-50"
                >
                  批量确认 ({{ selectedAuto.size }})
                </button>
              </div>
              <div class="grid grid-cols-3 md:grid-cols-4 xl:grid-cols-5 2xl:grid-cols-6 gap-2 pb-4">
                <div
                  v-for="f in autoAssignedFaces"
                  :key="f.id"
                  class="bg-gray-700 rounded overflow-hidden border border-orange-600/50 relative group"
                >
                  <label class="absolute top-1 left-1 bg-black/70 px-1.5 py-0.5 rounded text-[10px] z-10 cursor-pointer">
                    <input type="checkbox" class="mr-0.5" :checked="selectedAuto.has(f.id)" @change="toggleSelectAuto(f.id)" />
                    选
                  </label>
                  <div class="relative h-32 bg-gray-800 overflow-hidden">
                    <img
                      v-if="getFaceThumb(f)"
                      :src="getFaceThumb(f)"
                      class="w-full h-full object-cover"
                      :style="getFaceCropStyle(f)"
                      loading="lazy"
                    />
                    <button
                      @click="confirmFace(f.id)"
                      class="absolute bottom-1 right-1 bg-green-600 hover:bg-green-700 text-white px-1.5 py-0.5 rounded text-[10px] opacity-0 group-hover:opacity-100 transition-opacity"
                    >
                      确认
                    </button>
                  </div>
                  <div class="p-1.5">
                    <div class="text-[10px] text-gray-300 truncate" :title="f.photoFilename">{{ f.photoFilename || '-' }}</div>
                    <button @click="openPhoto(f.photoId)" class="text-blue-400 text-[10px] hover:underline mt-0.5">查看</button>
                  </div>
                </div>
              </div>
              <div v-if="autoAssignedFaces.length === 0" class="text-gray-400 text-xs text-center py-8">暂无自动分配照片</div>
            </div>

            <!-- 相似推荐 -->
            <div v-if="tab === 'similar' && selectedItem.type === 'confirmed'">
              <div class="mb-2 flex items-center justify-between">
                <span class="text-xs text-gray-400">按相似度排序的可能照片（60%-75%）</span>
                <button
                  @click="assignSelectedSimilar"
                  :disabled="selectedSimilar.size === 0"
                  class="px-2 py-1 bg-blue-600 hover:bg-blue-700 rounded text-[10px] disabled:opacity-50"
                >
                  批量认领 ({{ selectedSimilar.size }})
                </button>
              </div>
              <div class="grid grid-cols-3 md:grid-cols-4 xl:grid-cols-5 2xl:grid-cols-6 gap-2 pb-4">
                <div
                  v-for="f in similarFaces"
                  :key="f.id"
                  class="bg-gray-700 rounded overflow-hidden border border-green-600/50 relative group"
                >
                  <label class="absolute top-1 left-1 bg-black/70 px-1.5 py-0.5 rounded text-[10px] z-10 cursor-pointer">
                    <input type="checkbox" class="mr-0.5" :checked="selectedSimilar.has(f.id)" @change="toggleSelectSimilar(f.id)" />
                    选
                  </label>
                  <div class="absolute top-1 right-1 bg-green-600/80 px-1.5 py-0.5 rounded text-[10px] z-10">
                    {{ ((f.similarity || 0) * 100).toFixed(0) }}%
                  </div>
                  <div class="relative h-32 bg-gray-800 overflow-hidden">
                    <img
                      v-if="getFaceThumb(f)"
                      :src="getFaceThumb(f)"
                      class="w-full h-full object-cover"
                      :style="getFaceCropStyle(f)"
                      loading="lazy"
                    />
                    <button
                      @click="assignFace(f.id, false)"
                      class="absolute bottom-1 right-1 bg-blue-600 hover:bg-blue-700 text-white px-1.5 py-0.5 rounded text-[10px] opacity-0 group-hover:opacity-100 transition-opacity"
                    >
                      认领
                    </button>
                  </div>
                  <div class="p-1.5">
                    <div class="text-[10px] text-gray-300 truncate" :title="f.photoFilename">{{ f.photoFilename || '-' }}</div>
                    <button @click="openPhoto(f.photoId)" class="text-blue-400 text-[10px] hover:underline mt-0.5">查看</button>
                  </div>
                </div>
              </div>
              <div v-if="similarFaces.length === 0" class="text-gray-400 text-xs text-center py-8">暂无相似推荐</div>
            </div>

            <!-- 套图推荐 -->
            <div v-if="tab === 'sameFolder' && selectedItem.type === 'confirmed'">
              <div class="mb-2 flex items-center justify-between">
                <span class="text-xs text-gray-400">同一文件夹的相似人脸（50%-60%）</span>
                <button
                  @click="assignSelectedSameFolder"
                  :disabled="selectedSameFolder.size === 0"
                  class="px-2 py-1 bg-blue-600 hover:bg-blue-700 rounded text-[10px] disabled:opacity-50"
                >
                  批量认领 ({{ selectedSameFolder.size }})
                </button>
              </div>
              <div class="grid grid-cols-3 md:grid-cols-4 xl:grid-cols-5 2xl:grid-cols-6 gap-2 pb-4">
                <div
                  v-for="f in sameFolderFaces"
                  :key="f.id"
                  class="bg-gray-700 rounded overflow-hidden border border-purple-600/50 relative group"
                >
                  <label class="absolute top-1 left-1 bg-black/70 px-1.5 py-0.5 rounded text-[10px] z-10 cursor-pointer">
                    <input type="checkbox" class="mr-0.5" :checked="selectedSameFolder.has(f.id)" @change="toggleSelectSameFolder(f.id)" />
                    选
                  </label>
                  <div class="absolute top-1 right-1 bg-purple-600/80 px-1.5 py-0.5 rounded text-[10px] z-10">
                    {{ ((f.similarity || 0) * 100).toFixed(0) }}%
                  </div>
                  <div class="relative h-32 bg-gray-800 overflow-hidden">
                    <img
                      v-if="getFaceThumb(f)"
                      :src="getFaceThumb(f)"
                      class="w-full h-full object-cover"
                      :style="getFaceCropStyle(f)"
                      loading="lazy"
                    />
                    <button
                      @click="assignFace(f.id, false)"
                      class="absolute bottom-1 right-1 bg-blue-600 hover:bg-blue-700 text-white px-1.5 py-0.5 rounded text-[10px] opacity-0 group-hover:opacity-100 transition-opacity"
                    >
                      认领
                    </button>
                  </div>
                  <div class="p-1.5">
                    <div class="text-[10px] text-gray-300 truncate" :title="f.photoFilename">{{ f.photoFilename || '-' }}</div>
                    <button @click="openPhoto(f.photoId)" class="text-blue-400 text-[10px] hover:underline mt-0.5">查看</button>
                  </div>
                </div>
              </div>
              <div v-if="sameFolderFaces.length === 0" class="text-gray-400 text-xs text-center py-8">暂无套图推荐</div>
            </div>

            <!-- 未分配照片 -->
            <div v-if="tab === 'unassigned'">
              <div class="mb-2 flex items-center justify-between">
                <span class="text-xs text-gray-400">所有未分配的照片</span>
                <button
                  v-if="selectedItem.type === 'confirmed'"
                  @click="assignSelectedUnassigned"
                  :disabled="selectedUnassigned.size === 0"
                  class="px-2 py-1 bg-blue-600 hover:bg-blue-700 rounded text-[10px] disabled:opacity-50"
                >
                  批量认领 ({{ selectedUnassigned.size }})
                </button>
              </div>
              <div class="grid grid-cols-3 md:grid-cols-4 xl:grid-cols-5 2xl:grid-cols-6 gap-2 pb-4">
                <div
                  v-for="f in unassignedFaces"
                  :key="f.id"
                  class="bg-gray-700 rounded overflow-hidden border border-gray-600 relative group"
                >
                  <label v-if="selectedItem.type === 'confirmed'" class="absolute top-1 left-1 bg-black/70 px-1.5 py-0.5 rounded text-[10px] z-10 cursor-pointer">
                    <input type="checkbox" class="mr-0.5" :checked="selectedUnassigned.has(f.id)" @change="toggleSelectUnassigned(f.id)" />
                    选
                  </label>
                  <div class="relative h-32 bg-gray-800 overflow-hidden">
                    <img
                      v-if="getFaceThumb(f)"
                      :src="getFaceThumb(f)"
                      class="w-full h-full object-cover"
                      :style="getFaceCropStyle(f)"
                      loading="lazy"
                    />
                    <button
                      v-if="selectedItem.type === 'confirmed'"
                      @click="assignFace(f.id, false)"
                      class="absolute bottom-1 right-1 bg-blue-600 hover:bg-blue-700 text-white px-1.5 py-0.5 rounded text-[10px] opacity-0 group-hover:opacity-100 transition-opacity"
                    >
                      认领
                    </button>
                  </div>
                  <div class="p-1.5">
                    <div class="text-[10px] text-gray-300 truncate" :title="f.photoFilename">{{ f.photoFilename || '-' }}</div>
                    <button @click="openPhoto(f.photoId)" class="text-blue-400 text-[10px] hover:underline mt-0.5">查看</button>
                  </div>
                </div>
              </div>
              <div v-if="unassignedFaces.length === 0" class="text-gray-400 text-xs text-center py-8">暂无未分配照片</div>
            </div>

            <!-- 聚类照片（未确认聚类） -->
            <div v-if="tab === 'confirmed' && selectedItem.type === 'cluster'">
              <div class="grid grid-cols-3 md:grid-cols-4 xl:grid-cols-5 2xl:grid-cols-6 gap-2 pb-4">
                <div
                  v-for="f in personFaces"
                  :key="f.id"
                  class="bg-gray-700 rounded overflow-hidden border border-gray-600 relative group"
                >
                  <div class="relative h-32 bg-gray-800 overflow-hidden">
                    <img
                      v-if="getFaceThumb(f)"
                      :src="getFaceThumb(f)"
                      class="w-full h-full object-cover"
                      :style="getFaceCropStyle(f)"
                      loading="lazy"
                    />
                  </div>
                  <div class="p-1.5">
                    <div class="text-[10px] text-gray-300 truncate" :title="f.photoFilename">{{ f.photoFilename || '-' }}</div>
                    <button @click="openPhoto(f.photoId)" class="text-blue-400 text-[10px] hover:underline mt-0.5">查看</button>
                  </div>
                </div>
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
const nameInput = ref<HTMLInputElement | null>(null)
const personListContainer = ref<HTMLElement | null>(null)

// 面板宽度和拖拽
const leftPanelWidth = ref(parseInt(localStorage.getItem(STORAGE_KEY) || '280', 10))
const isResizing = ref(false)
const resizeStartX = ref(0)
const resizeStartWidth = ref(0)

// 列数自适应
const personColumns = computed(() => {
  if (!personListContainer.value) return 2
  const width = personListContainer.value.clientWidth
  if (width < 200) return 1
  if (width < 280) return 2
  if (width < 360) return 3
  if (width < 440) return 4
  return 5
})

const tab = ref<'confirmed' | 'auto' | 'similar' | 'sameFolder' | 'unassigned'>('confirmed')

// 已确认照片
const confirmedFaces = ref<FaceItem[]>([])
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
const loadingPersonFaces = ref(false)

const startResize = (e: MouseEvent) => {
  isResizing.value = true
  resizeStartX.value = e.clientX
  resizeStartWidth.value = leftPanelWidth.value
  document.addEventListener('mousemove', handleResize)
  document.addEventListener('mouseup', stopResize)
  e.preventDefault()
}

const handleResize = (e: MouseEvent) => {
  if (!isResizing.value) return
  const diff = e.clientX - resizeStartX.value
  const newWidth = Math.max(200, Math.min(500, resizeStartWidth.value + diff))
  leftPanelWidth.value = newWidth
}

const stopResize = () => {
  isResizing.value = false
  localStorage.setItem(STORAGE_KEY, String(leftPanelWidth.value))
  document.removeEventListener('mousemove', handleResize)
  document.removeEventListener('mouseup', stopResize)
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
  loadConfirmedFaces()
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
  nextTick(() => {
    nameInput.value?.focus()
  })
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
  await api.put(`/admin/faces/${faceId}/assign`)
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

onMounted(() => {
  loadPersons()
})

onBeforeUnmount(() => {
  if (isResizing.value) {
    stopResize()
  }
})
</script>
