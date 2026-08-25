<template>
  <div class="min-h-screen admin-shell admin-face-batch-page">
    <div class="max-w-[1800px] mx-auto px-4 sm:px-6 lg:px-8 py-4">
      <div class="admin-face-batch-workspace flex flex-col gap-4 min-h-[calc(100vh-10rem)]">
    <!-- 头部 -->
    <div class="admin-face-batch-hero flex-shrink-0 px-4 sm:px-5 py-4 flex items-center justify-between gap-4">
      <h1 class="text-2xl font-light admin-page-title">批量分配人脸</h1>
      <router-link to="/admin/persons" class="admin-button-soft admin-page-back-link px-3 py-1.5 rounded text-sm">返回</router-link>
    </div>

    <!-- 主内容区 -->
    <div class="admin-face-batch-panel flex-1 flex flex-col overflow-hidden px-4 sm:px-5 py-4">
      <!-- 推荐人物列表 + 搜索新建 -->
      <div class="flex-shrink-0 mb-4">
        <div class="flex items-center justify-between mb-3">
          <h2 class="text-lg font-medium">是TA吗？(推荐人物)</h2>
          <!-- 快捷键提示 -->
          <div class="relative group">
            <button class="admin-face-batch-help-trigger cursor-help">
              <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8.228 9c.549-1.165 2.03-2 3.772-2 2.21 0 4 1.343 4 3 0 1.4-1.278 2.575-3.006 2.907-.542.104-.994.54-.994 1.093m0 3h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
              </svg>
            </button>
            <div class="admin-face-batch-shortcuts absolute right-0 top-full mt-2 px-3 py-2 rounded-lg text-sm opacity-0 invisible group-hover:opacity-100 group-hover:visible transition-all whitespace-nowrap z-10">
              <div class="space-y-1">
                <div><kbd class="admin-face-batch-kbd px-1.5 py-0.5 rounded text-xs">1-9</kbd> 选择推荐人物</div>
                <div><kbd class="admin-face-batch-kbd px-1.5 py-0.5 rounded text-xs">Enter</kbd> 分配/创建</div>
                <div><kbd class="admin-face-batch-kbd px-1.5 py-0.5 rounded text-xs">Space</kbd> 跳过</div>
                <div><kbd class="admin-face-batch-kbd px-1.5 py-0.5 rounded text-xs">←→</kbd> 翻页</div>
              </div>
            </div>
          </div>
        </div>
        <div v-if="recommendedPersons.length > 0 || searchResults.length > 0 || searchQuery" class="flex gap-3 overflow-x-auto pb-2">
          <!-- 推荐人物 -->
          <div
            v-for="(person, index) in recommendedPersons"
            :key="'rec-' + person.personId"
            @click="assignToRecommendedPerson(person)"
            class="admin-face-batch-person-card flex-shrink-0 flex items-center gap-3 p-2 rounded-lg cursor-pointer transition-colors bg-gray-800 hover:bg-gray-700"
          >
            <div class="w-12 h-12 rounded-full overflow-hidden bg-gray-600 flex-shrink-0">
              <img
                v-if="person.sampleThumbnailPath"
                :src="buildPhotoAssetUrl({ thumbnailPath: person.sampleThumbnailPath }, 'thumbnail') || ''"
                class="w-full h-full object-cover"
              />
              <div v-else class="w-full h-full flex items-center justify-center">
                <svg class="w-6 h-6 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z" />
                </svg>
              </div>
            </div>
            <div class="min-w-0">
              <p class="font-medium truncate">{{ person.personName }}</p>
              <p class="admin-face-batch-meta text-xs">相似度 {{ Math.round(person.similarity * 100) }}%</p>
            </div>
            <span
              v-if="index < 9"
              class="admin-face-batch-index px-2 py-0.5 rounded text-xs font-mono"
            >
              {{ index + 1 }}
            </span>
          </div>
          <!-- 搜索结果 -->
          <div
            v-for="person in searchResults"
            :key="'search-' + person.id"
            @click="assignToSearchResult(person)"
            class="admin-face-batch-person-card flex-shrink-0 flex items-center gap-3 p-2 rounded-lg cursor-pointer transition-colors bg-gray-800 hover:bg-gray-700 border"
            :class="selectedPersonId === person.id ? 'border-blue-500 ring-1 ring-blue-500' : 'border-transparent'"
          >
            <div class="w-12 h-12 rounded-full overflow-hidden bg-gray-600 flex-shrink-0">
              <img
                v-if="person.sampleThumbnailPath"
                :src="buildPhotoAssetUrl({ thumbnailPath: person.sampleThumbnailPath }, 'thumbnail') || ''"
                class="w-full h-full object-cover"
              />
              <div v-else class="w-full h-full flex items-center justify-center">
                <svg class="w-6 h-6 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z" />
                </svg>
              </div>
            </div>
            <div class="min-w-0">
              <p class="font-medium truncate">{{ person.name }}</p>
              <p v-if="person.similarity !== undefined" class="admin-face-batch-meta text-xs">相似度 {{ Math.round(person.similarity * 100) }}%</p>
            </div>
          </div>
          <!-- 新建人物按钮 -->
          <div
            v-if="searchQuery && !isExactMatch"
            @click="createAndAssignPerson"
            class="admin-face-batch-person-card flex-shrink-0 flex items-center gap-3 p-2 rounded-lg cursor-pointer transition-colors bg-green-900/50 hover:bg-green-800/50 border"
            :class="selectedPersonId === null ? 'border-green-500 ring-1 ring-green-500' : 'border-green-700'"
          >
            <div class="w-12 h-12 rounded-full overflow-hidden bg-green-700 flex-shrink-0 flex items-center justify-center">
              <svg class="w-6 h-6 text-green-300" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4" />
              </svg>
            </div>
            <div class="min-w-0">
              <p class="font-medium truncate text-green-300">新建: {{ searchQuery }}</p>
            </div>
          </div>
        </div>
        <!-- 搜索框 + 跳过按钮 -->
        <div class="mt-2 flex items-center justify-between gap-2">
          <label class="flex-1 max-w-xs space-y-1">
            <span class="admin-face-batch-meta text-[11px]">搜索或新建人物</span>
            <input
              ref="assignSearchInput"
              v-model="searchQuery"
              @input="onSearchInput"
              @keydown.enter="onEnterKey"
              type="text"
              placeholder="输入人物姓名关键词"
              class="admin-face-batch-input px-3 py-1.5 rounded text-sm w-full"
            />
          </label>
          <button
            @click="skipCluster"
            class="admin-button-soft px-3 py-1.5 rounded text-sm"
            title="空格键跳过"
          >
            跳过
          </button>
        </div>
        <div v-if="!recommendedPersons.length && !searchQuery" class="admin-face-batch-empty text-sm py-2">
          没有找到相似度超过10%的推荐人物
        </div>
      </div>

      <!-- 当前聚类的人脸 -->
      <div class="flex-1 flex flex-col min-h-0">
        <div class="flex items-center justify-between mb-2">
          <h2 class="text-lg font-medium">
            当前聚类
            <span v-if="currentCluster" class="admin-face-batch-meta text-sm ml-2">
              (共 {{ currentCluster.count }} 张人脸)
            </span>
          </h2>
        </div>

        <!-- 人脸网格 -->
        <div v-if="currentClusterFaces.length > 0" class="flex-1 overflow-y-auto">
          <div class="grid grid-cols-4 sm:grid-cols-6 md:grid-cols-8 lg:grid-cols-10 xl:grid-cols-12 gap-2">
            <div
              v-for="(face, index) in currentClusterFaces"
              :key="face.id"
              class="admin-face-batch-face-card aspect-square bg-gray-800 rounded-lg overflow-hidden cursor-pointer hover:ring-2 hover:ring-blue-500 relative group"
              @click="openPhotoViewer(index)"
              @click.right.prevent="openPhotoViewer(index)"
            >
              <img
                v-if="getFaceUrl(face)"
                :src="getFaceUrl(face)"
                class="w-full h-full object-cover"
              />
              <div v-else class="w-full h-full flex items-center justify-center">
                <svg class="w-8 h-8 text-gray-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z" />
                </svg>
              </div>
            </div>
          </div>
        </div>
        <div v-else-if="loadingCluster" class="flex-1 flex items-center justify-center">
          <div class="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-500"></div>
        </div>
        <div v-else class="flex-1 flex items-center justify-center admin-face-batch-empty">
          <div class="text-center">
            <p>没有更多未分配的人脸聚类了</p>
            <p class="text-sm mt-2">已完成所有聚类的分配</p>
          </div>
        </div>

        <!-- 分页 -->
        <div class="flex-shrink-0 mt-4 flex items-center justify-center gap-2">
          <button
            @click="goToPage(currentPage - 1)"
            :disabled="currentPage === 0"
            class="admin-button-soft px-3 py-1.5 rounded text-sm disabled:opacity-50 disabled:cursor-not-allowed"
          >
            ← 上一个
          </button>
          <div class="flex items-center gap-1">
            <template v-for="page in visiblePages" :key="page">
              <span v-if="page === '...'" class="px-2">...</span>
              <button
                v-else
                @click="goToPage(page as number)"
                class="w-8 h-8 rounded text-sm"
                :class="currentPage === page ? 'admin-face-batch-page-btn admin-face-batch-page-btn--active' : 'admin-face-batch-page-btn admin-face-batch-page-btn--idle'"
              >
                {{ page }}
              </button>
            </template>
          </div>
          <button
            @click="goToPage(currentPage + 1)"
            :disabled="!hasMore"
            class="admin-button-soft px-3 py-1.5 rounded text-sm disabled:opacity-50 disabled:cursor-not-allowed"
          >
            下一个 →
          </button>
          <div class="flex items-center gap-1 ml-2">
            <label class="space-y-1">
              <span class="admin-face-batch-meta text-[11px]">页码</span>
              <input
                v-model.number="pageInput"
                @keyup.enter="goToPage(pageInput - 1)"
                type="number"
                min="1"
                :max="totalClusters"
                placeholder="输入页码"
                class="admin-face-batch-input w-14 px-2 py-1.5 rounded text-sm"
              />
            </label>
            <span class="admin-face-batch-meta text-sm">/ {{ totalClusters }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- PhotoViewer -->
    <PhotoViewer
      v-if="viewerPhotos.length > 0"
      :photos="viewerPhotos"
      :visible="viewerVisible"
      :start-index="viewerIndex"
      :auto-show-faces="true"
      :open-options="viewerOpenOptions"
      @update:visible="viewerVisible = $event"
    />
    </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { api, aiApi, configApi } from '@/api'
import PhotoViewer from '@/components/PhotoViewer.vue'
import { buildPhotoAssetUrl } from '@/utils/photoUrl'
import { useAdminFeedback } from '@/composables/useAdminFeedback'

const { notify } = useAdminFeedback()
const getErrorMessage = (error: unknown) => {
  const e = error as any
  return e?.response?.data?.message || e?.response?.data?.error || e?.message || '未知错误'
}
const alert = (message: unknown) => notify(String(message), /失败|错误|不能|无效/.test(String(message)) ? 'error' : 'info')

interface Face {
  id: number
  photoId?: number
  photoFilename?: string
  photoThumbnailPath?: string
  photoOriginalPath?: string
  thumbnailPath?: string
  originalPath?: string
  personId?: number
  personName?: string
  isConfirmed?: boolean
  x?: number
  y?: number
  width?: number
  height?: number
  photoWidth?: number
  photoHeight?: number
  photoTakenAt?: string
  albumId?: number
}

interface ClusterFace {
  faces: Face[]
  count: number
  avgConfidence?: number
  representativeFaceId?: number
}

interface RecommendedPerson {
  personId: number
  personName: string
  sampleThumbnailPath?: string
  similarity: number
}

interface SearchPerson {
  id: number
  name: string
  sampleThumbnailPath?: string
  faceCount?: number
  similarity?: number
}

// 聚类阈值 - 从后端获取
const DEFAULT_CLUSTER_THRESHOLD = 0.7
const clusterThreshold = ref<number>(DEFAULT_CLUSTER_THRESHOLD)

// 加载阈值配置
const loadClusterThreshold = async () => {
  try {
    const response = await configApi.getFaceClusterThreshold()
    clusterThreshold.value = response.data.faceClusterThreshold || DEFAULT_CLUSTER_THRESHOLD
  } catch (e) {
    console.error('加载聚类阈值失败:', e)
    // 使用本地默认值
    const saved = localStorage.getItem('pe-cluster-threshold')
    if (saved) {
      clusterThreshold.value = parseFloat(saved)
    }
  }
}

// 聚类数据
const clusters = ref<ClusterFace[]>([])
const currentPage = ref(0)
const loadingCluster = ref(false)
const totalClusters = ref(0)
const pageInput = ref(1)

// 推荐人物
const recommendedPersons = ref<RecommendedPerson[]>([])
const selectedPersonId = ref<number | null>(null)

// 搜索功能
const searchQuery = ref('')
const assignSearchInput = ref<HTMLInputElement | null>(null)
const searchResults = ref<SearchPerson[]>([])
const searchTimeout = ref<number | null>(null)

// 判断是否选中的是推荐人物
const isRecommendedSelected = computed(() => {
  if (!selectedPersonId.value) return false
  return recommendedPersons.value.some(p => p.personId === selectedPersonId.value)
})

// 判断是否有精确匹配（已有人物名称与搜索词相同）
const isExactMatch = computed(() => {
  if (!searchQuery.value || searchResults.value.length === 0) return false
  return searchResults.value.some(p => p.name.toLowerCase() === searchQuery.value.toLowerCase())
})

// 当前聚类
const currentCluster = computed(() => clusters.value[0] || null)
const currentClusterFaces = computed(() => currentCluster.value?.faces || [])

// 加载聚类数据和推荐人物
const loadClusterData = async (page: number) => {
  loadingCluster.value = true
  pageInput.value = page + 1
  // 清除搜索状态
  searchQuery.value = ''
  searchResults.value = []
  selectedPersonId.value = null
  try {
    // 获取聚类列表
    const clustersResponse = await api.get<any[]>(
      `/admin/faces/clusters?threshold=${clusterThreshold.value}`
    )
    const allClusters = clustersResponse.data || []
    totalClusters.value = allClusters.length

    if (page < allClusters.length) {
      const clusterData = allClusters[page]
      clusters.value = [clusterData]

      // 获取该聚类的详细人脸
      const facesResponse = await api.get<{ faces: Face[] }>(
        `/admin/clusters/${page}/faces?threshold=${clusterThreshold.value}`
      )
      if (facesResponse.data.faces && facesResponse.data.faces.length > 0) {
        clusters.value[0].faces = facesResponse.data.faces
      }

      // 获取推荐人物列表（使用聚类阈值来获取聚类人脸，筛选阈值固定为0.1）
      const similarPersonsResponse = await api.get<RecommendedPerson[]>(
        `/admin/clusters/${page}/similar-persons?clusterThreshold=${clusterThreshold.value}&recommendThreshold=0.1`
      )
      recommendedPersons.value = similarPersonsResponse.data || []

      // 默认选中第一个推荐人物
      if (recommendedPersons.value.length > 0) {
        selectedPersonId.value = recommendedPersons.value[0].personId
      }
    } else {
      clusters.value = []
      recommendedPersons.value = []
    }
  } catch (e) {
    console.error('加载聚类失败:', e)
    clusters.value = []
    recommendedPersons.value = []
  } finally {
    loadingCluster.value = false
  }
}

// 分页
const hasMore = computed(() => {
  return (currentPage.value + 1) < totalClusters.value
})

const visiblePages = computed(() => {
  const total = totalClusters.value
  const pages: (number | string)[] = []
  const current = currentPage.value

  if (total <= 7) {
    for (let i = 0; i < total; i++) pages.push(i)
  } else {
    if (current < 4) {
      for (let i = 0; i < 5; i++) pages.push(i)
      pages.push('...')
      pages.push(total - 1)
    } else if (current > total - 5) {
      pages.push(0)
      pages.push('...')
      for (let i = total - 5; i < total; i++) pages.push(i)
    } else {
      pages.push(0)
      pages.push('...')
      for (let i = current - 1; i <= current + 1; i++) pages.push(i)
      pages.push('...')
      pages.push(total - 1)
    }
  }
  return pages
})

const goToPage = (page: number) => {
  if (page < 0 || page >= totalClusters.value) return
  currentPage.value = page
  pageInput.value = page + 1
  loadClusterData(page)
}

// 选择推荐人物（点击直接分配）
const selectRecommendedPerson = async (person: RecommendedPerson) => {
  await assignToPerson(person.personId)
}

// 分配给人脸
const assignToPerson = async (personId: number) => {
  if (!currentCluster.value || !currentCluster.value.faces) return

  const faceIds = currentCluster.value.faces.map(f => f.id)
  try {
    await aiApi.batchAssignFaces(faceIds, personId, true)
    await skipCluster()
  } catch (e) {
    console.error('分配失败:', e)
    alert('分配失败：' + getErrorMessage(e))
  }
}

// 点击推荐人物直接分配
const assignToRecommendedPerson = async (person: RecommendedPerson) => {
  await assignToPerson(person.personId)
}

// 点击搜索结果直接分配
const assignToSearchResult = async (person: SearchPerson) => {
  await assignToPerson(person.id)
}

// 搜索人物
const onSearchInput = async () => {
  if (searchTimeout.value) {
    clearTimeout(searchTimeout.value)
  }

  const query = searchQuery.value.trim()
  if (!query) {
    searchResults.value = []
    selectedPersonId.value = null
    return
  }

  // Debounce 搜索
  searchTimeout.value = window.setTimeout(async () => {
    try {
      const response = await api.get<{ data: SearchPerson[] }>(
        `/admin/persons/search?q=${encodeURIComponent(query)}`
      )
      let searchResultsData = response.data || []

      // 如果有搜索结果且当前有聚类，计算相似度
      if (searchResultsData.length > 0 && currentCluster.value?.faces?.length) {
        const faceIds = currentCluster.value.faces.map(f => f.id)
        try {
          const similarityResponse = await api.post<{ data: any[] }>('/admin/faces/calculate-similarity-to-persons', {
            faceIds
          })
          const similarityMap = new Map(
            (similarityResponse.data || []).map((s: any) => [s.personId, s.similarity])
          )
          // 合并相似度到搜索结果
          searchResultsData = searchResultsData.map(p => ({
            ...p,
            similarity: similarityMap.get(p.id)
          }))
        } catch (e) {
          console.error('计算相似度失败:', e)
        }
      }

      searchResults.value = searchResultsData

      // 自动选中：优先选已有人物，否则选中新建
      const exactMatch = searchResults.value.find(p => p.name.toLowerCase() === query.toLowerCase())
      if (exactMatch) {
        selectedPersonId.value = exactMatch.id
      } else {
        selectedPersonId.value = null // 选中新建
      }
    } catch (e) {
      console.error('搜索失败:', e)
      searchResults.value = []
      selectedPersonId.value = null
    }
  }, 300)
}

// 选择搜索结果
const selectSearchResult = (person: SearchPerson) => {
  selectedPersonId.value = person.id
}

// 回车键处理（搜索或新建）
const onEnterKey = () => {
  // 如果有搜索结果，直接分配给第一个
  if (searchResults.value.length > 0) {
    assignToSearchResult(searchResults.value[0])
  } else if (searchQuery.value.trim()) {
    // 没有搜索结果但有搜索词 -> 创建新人物
    createAndAssignPerson()
  } else if (recommendedPersons.value.length > 0) {
    // 没有搜索词但有推荐 -> 直接分配给第一个推荐
    assignToRecommendedPerson(recommendedPersons.value[0])
  }
}

// 新建人物并分配
const createAndAssignPerson = async () => {
  const query = searchQuery.value.trim()
  if (!query || !currentCluster.value || !currentCluster.value.faces) return

  const faceIds = currentCluster.value.faces.map(f => f.id)
  try {
    await aiApi.assignFacesToPerson(faceIds, query)
    // 成功后跳到下一个聚类
    searchQuery.value = ''
    searchResults.value = []
    // 取消聚焦搜索框
    assignSearchInput.value?.blur()
    await skipCluster()
  } catch (e) {
    console.error('创建人物失败:', e)
    alert('创建人物失败：' + getErrorMessage(e))
  }
}

// 加入现有人物并分配
const joinExistingPerson = async () => {
  const firstMatch = searchResults.value.find(
    p => p.name.toLowerCase() === searchQuery.value.toLowerCase()
  )
  if (!firstMatch) return

  selectedPersonId.value = firstMatch.id

  // 取消聚焦搜索框
  assignSearchInput.value?.blur()

  await confirmAssign()
}

// 确认分配
const confirmAssign = async () => {
  if (!selectedPersonId.value || !currentCluster.value || !currentCluster.value.faces) return

  const faceIds = currentCluster.value.faces.map(f => f.id)
  try {
    await aiApi.batchAssignFaces(faceIds, selectedPersonId.value, true)
    await skipCluster()
  } catch (e) {
    console.error('分配失败:', e)
    alert('分配失败：' + getErrorMessage(e))
  }
}

// 跳过当前聚类
const skipCluster = async () => {
  if (hasMore.value) {
    await goToPage(currentPage.value + 1)
  } else if (currentPage.value + 1 < totalClusters.value) {
    await goToPage(currentPage.value + 1)
  } else {
    // 已到最后一页
    clusters.value = []
    recommendedPersons.value = []
    selectedPersonId.value = null
  }
}

// 人脸图片URL
const getFaceUrl = (face: Face) => {
  return buildPhotoAssetUrl({
    thumbnailPath: face.photoThumbnailPath || face.thumbnailPath,
    originalPath: face.photoOriginalPath
  }, 'thumbnail') || ''
}

// PhotoViewer
const viewerVisible = ref(false)
const viewerPhotos = ref<any[]>([])
const viewerIndex = ref(0)
const viewerOpenOptions = ref<any>(null)

const openPhotoViewer = (index: number) => {
  viewerIndex.value = index
  viewerPhotos.value = currentClusterFaces.value.map(face => ({
    id: face.photoId || face.id,
    filename: face.photoFilename || '人脸',
    originalPath: face.photoOriginalPath || face.originalPath || '',
    thumbnailPath: face.photoThumbnailPath || face.thumbnailPath,
    mediumThumbPath: face.photoThumbnailPath || face.thumbnailPath,
    largeThumbPath: face.photoOriginalPath || face.originalPath || '',
    albumId: face.albumId || 0,
    width: face.photoWidth || 0,
    height: face.photoHeight || 0,
    takenAt: face.photoTakenAt || '',
    faces: [{
      id: face.id,
      x: face.x,
      y: face.y,
      width: face.width,
      height: face.height
    }]
  }))

  // 设置高亮选项：只高亮当前聚类的人脸
  const clusterFaceIds = currentClusterFaces.value.map(f => Number(f.id))
  viewerOpenOptions.value = {
    highlightedFaceIds: clusterFaceIds,
    preferredFaceId: currentClusterFaces.value[index]?.id
  }

  viewerVisible.value = true
}

// 键盘事件
const handleKeydown = (e: KeyboardEvent) => {
  // 如果正在输入框中，只处理 Escape 键
  if ((e.target as HTMLElement).tagName === 'INPUT') {
    if (e.key === 'Escape') {
      searchQuery.value = ''
      searchResults.value = []
      ;(e.target as HTMLInputElement).blur()
    }
    return
  }

  // 空格键跳过
  if (e.key === ' ' || e.key === 'Spacebar') {
    e.preventDefault()
    skipCluster()
  }
  // 数字键 1-9 选择推荐人物（直接分配）
  if (e.key >= '1' && e.key <= '9') {
    const index = parseInt(e.key) - 1
    if (recommendedPersons.value[index]) {
      assignToRecommendedPerson(recommendedPersons.value[index])
    }
  }
  // 空格键跳过
  if (e.key === ' ' || e.key === 'Spacebar') {
    e.preventDefault()
    skipCluster()
  }
  // 回车键：搜索结果直接分配，或创建新人物，或用第一个推荐
  if (e.key === 'Enter') {
    e.preventDefault()
    onEnterKey()
  }
  // 左箭头上一页
  if (e.key === 'ArrowLeft') {
    e.preventDefault()
    goToPage(currentPage.value - 1)
  }
  // 右箭头下一页
  if (e.key === 'ArrowRight') {
    e.preventDefault()
    goToPage(currentPage.value + 1)
  }
}

onMounted(async () => {
  await loadClusterThreshold()
  loadClusterData(0)
  window.addEventListener('keydown', handleKeydown)
})

onUnmounted(() => {
  window.removeEventListener('keydown', handleKeydown)
})
</script>
