<template>
  <div class="min-h-screen bg-gray-50 dark:bg-gray-950">
    <!-- 悬浮返回按钮 - 与相册详情页一致 -->
    <nav class="fixed top-4 left-4 z-50">
      <div
        ref="backButtonRef"
        class="back-button-container"
        :class="{ 'is-collapsed': isBackButtonCollapsed }"
        @click="goBack"
        @mousemove="onBackButtonMouseMove"
        @mouseleave="onBackButtonMouseLeave"
        @mousedown="onBackButtonMouseDown"
        aria-label="返回"
        title="返回"
      >
        <div class="back-button-glass">
          <svg class="back-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
            <path d="M19 12H5M12 19l-7-7 7-7"/>
          </svg>
          <span class="back-text">BACK</span>
        </div>
      </div>
    </nav>

    <!-- 吸顶紧凑导航栏 (滚动时显示) -->
    <nav
      class="fixed top-0 left-0 right-0 z-40 transition-all duration-500 ease-out transform-gpu"
      :class="[
        scrolled > 50 ? 'translate-y-0 py-3' : '-translate-y-full py-4',
        'bg-white/80 dark:bg-gray-900/80 backdrop-blur-xl border-b border-gray-200/50 dark:border-gray-800/50'
      ]"
    >
      <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div class="flex items-center pl-12 lg:pl-14">
          <!-- 头像和名字靠左排列，留出空间给悬浮返回按钮 -->
          <div class="flex items-center gap-3">
            <div class="w-10 h-10 rounded-full overflow-hidden shadow-md ring-2 ring-white/50 dark:ring-gray-700/50">
              <img
                v-if="person?.sampleThumbnailPath"
                :src="convertImagePath(person.sampleThumbnailPath)"
                :alt="person.name"
                class="w-full h-full object-cover"
              />
              <div v-else class="w-full h-full bg-gradient-to-br from-gray-200 to-gray-300 dark:from-gray-700 dark:to-gray-800 flex items-center justify-center">
                <svg class="w-5 h-5 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z" />
                </svg>
              </div>
            </div>
            <span class="text-lg font-medium text-gray-900 dark:text-white tracking-wide">
              {{ person?.name || '加载中...' }}
            </span>
          </div>
        </div>
      </div>
    </nav>

    <!-- 人物信息头部 (视差效果) -->
    <div
      class="relative overflow-hidden transition-all duration-700 ease-out"
      :style="{
        paddingTop: 'calc(env(safe-area-inset-top) + 4rem)',
        paddingBottom: `${Math.max(1, 3 - scrolled / 100)}rem`,
        marginBottom: `${Math.max(0, 1.5 - scrolled / 300)}rem`,
        opacity: Math.max(0, 1 - scrolled / 400),
        transform: `scale(${Math.max(0.95, 1 - scrolled / 2000)}) translateY(${scrolled * 0.1}px)`
      }"
    >
      <div class="relative max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div class="flex flex-col lg:flex-row gap-8 lg:gap-12 items-center lg:items-start">
          <!-- 人物头像 - 带视差效果，改为圆形 -->
          <div
            class="flex-shrink-0 self-center lg:self-start transition-transform duration-300"
            :style="{
              transform: `translateY(${scrolled * 0.15}px) scale(${Math.max(0.7, 1 - scrolled / 500)})`
            }"
          >
            <div class="relative">
              <!-- 头像光晕效果 - 带脉动动画，使用温暖色调 -->
              <div class="absolute -inset-1 bg-gradient-to-r from-amber-500/30 via-orange-500/30 to-rose-500/30 rounded-full blur-xl opacity-60 animate-pulse-slow"></div>
              <div class="relative w-36 h-36 md:w-44 md:h-44 lg:w-52 lg:h-52 rounded-full bg-gradient-to-br from-white to-gray-100 dark:from-gray-800 dark:to-gray-900 shadow-2xl overflow-hidden ring-4 ring-white/50 dark:ring-gray-800/50">
                <img
                  v-if="person?.sampleThumbnailPath"
                  :src="convertImagePath(person.sampleThumbnailPath)"
                  :alt="person.name"
                  class="w-full h-full object-cover"
                />
                <div v-else class="w-full h-full flex items-center justify-center bg-gradient-to-br from-gray-100 to-gray-200 dark:from-gray-800 dark:to-gray-900">
                  <svg class="w-20 h-20 text-gray-300 dark:text-gray-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z" />
                  </svg>
                </div>
              </div>
            </div>
          </div>

          <!-- 人物信息 -->
          <div class="flex-1 min-w-0 py-2 text-center lg:text-left">
            <h1
              class="text-3xl md:text-4xl lg:text-5xl font-light tracking-tight text-gray-900 dark:text-white mb-4 transition-all duration-500"
              :class="{ 'lg:mt-4': scrolled < 50 }"
            >
              <span class="bg-gradient-to-r from-gray-900 via-gray-700 to-gray-900 dark:from-white dark:via-gray-300 dark:to-gray-500 bg-clip-text text-transparent">
                {{ person?.name }}
              </span>
            </h1>

            <p v-if="person?.description" class="text-gray-600 dark:text-gray-400 mb-6 leading-relaxed text-lg max-w-2xl">
              {{ person.description }}
            </p>

            <!-- 统计信息 - 简洁卡片风格 -->
            <div class="flex flex-wrap gap-3">
              <div v-if="person?.faceCount && person.faceCount > 0" class="flex items-center gap-2 px-4 py-2 bg-white/60 dark:bg-gray-800/60 backdrop-blur-sm rounded-xl border border-gray-200/50 dark:border-gray-700/50 shadow-sm hover:shadow-md hover:bg-white/80 dark:hover:bg-gray-800/80 transition-all duration-300">
                <div class="w-8 h-8 rounded-lg bg-purple-50 dark:bg-purple-900/30 flex items-center justify-center">
                  <svg class="w-4 h-4 text-purple-600 dark:text-purple-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z" />
                  </svg>
                </div>
                <span class="text-sm font-medium text-gray-700 dark:text-gray-300">{{ person.faceCount }} 照片</span>
              </div>

              <div v-if="person?.albumCount && person.albumCount > 0" class="flex items-center gap-2 px-4 py-2 bg-white/60 dark:bg-gray-800/60 backdrop-blur-sm rounded-xl border border-gray-200/50 dark:border-gray-700/50 shadow-sm hover:shadow-md hover:bg-white/80 dark:hover:bg-gray-800/80 transition-all duration-300">
                <div class="w-8 h-8 rounded-lg bg-gradient-to-br from-pink-50 to-orange-50 dark:from-pink-900/30 dark:to-orange-900/30 flex items-center justify-center">
                  <svg class="w-4 h-4 text-gradient-to-r from-pink-500 to-orange-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 11H5m14 0a2 2 0 012 2v6a2 2 0 01-2 2H5a2 2 0 01-2-2v-6a2 2 0 012-2m14 0V9a2 2 0 00-2-2M5 11V9a2 2 0 012-2m0 0V5a2 2 0 012-2h6a2 2 0 012 2v2M7 7h10" />
                  </svg>
                </div>
                <span class="text-sm font-medium text-gray-700 dark:text-gray-300">{{ person.albumCount }} 相册</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Tab 切换 -->
    <div class="relative z-20 max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 -mt-2">
      <div class="relative border-b border-gray-200/50 dark:border-gray-800/50 bg-gray-50/50 dark:bg-gray-950/50">
        <nav class="flex gap-8 overflow-x-auto scrollbar-hide">
          <button
            @click="activeTab = 'albums'"
            class="relative whitespace-nowrap py-4 px-2 text-sm font-medium transition-all duration-300"
            :class="activeTab === 'albums'
              ? 'text-gray-900 dark:text-white'
              : 'text-gray-500 hover:text-gray-700 dark:text-gray-400 dark:hover:text-gray-300'"
          >
            <span class="relative z-10 flex items-center gap-2">
              <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 11H5m14 0a2 2 0 012 2v6a2 2 0 01-2 2H5a2 2 0 01-2-2v-6a2 2 0 012-2m14 0V9a2 2 0 00-2-2M5 11V9a2 2 0 012-2m0 0V5a2 2 0 012-2h6a2 2 0 012 2v2M7 7h10" />
              </svg>
              相册
              <span class="px-1.5 py-0.5 text-xs rounded-full bg-gray-100 dark:bg-gray-800">
                {{ albumRecommendations.length }}
              </span>
            </span>
            <span
              v-if="activeTab === 'albums'"
              class="absolute bottom-0 left-0 right-0 h-0.5 bg-gradient-to-r from-blue-500 via-purple-500 to-pink-500 rounded-full shadow-lg shadow-purple-500/50"
            ></span>
            <span
              v-else
              class="absolute bottom-0 left-0 right-0 h-0.5 bg-gray-300 dark:bg-gray-600 rounded-full opacity-0 transition-opacity duration-300 hover:opacity-100"
            ></span>
          </button>
          <button
            @click="activeTab = 'photos'"
            class="relative whitespace-nowrap py-4 px-2 text-sm font-medium transition-all duration-300"
            :class="activeTab === 'photos'
              ? 'text-gray-900 dark:text-white'
              : 'text-gray-500 hover:text-gray-700 dark:text-gray-400 dark:hover:text-gray-300'"
          >
            <span class="relative z-10 flex items-center gap-2">
              <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z" />
              </svg>
              图片
              <span class="px-1.5 py-0.5 text-xs rounded-full bg-gray-100 dark:bg-gray-800">
                {{ person?.faceCount || 0 }}
              </span>
            </span>
            <span
              v-if="activeTab === 'photos'"
              class="absolute bottom-0 left-0 right-0 h-0.5 bg-gradient-to-r from-blue-500 via-purple-500 to-pink-500 rounded-full shadow-lg shadow-purple-500/50"
            ></span>
            <span
              v-else
              class="absolute bottom-0 left-0 right-0 h-0.5 bg-gray-300 dark:bg-gray-600 rounded-full opacity-0 transition-opacity duration-300 hover:opacity-100"
            ></span>
          </button>
        </nav>
      </div>
    </div>

    <!-- 内容区域 -->
    <main class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 pb-8">
      <!-- 相册 Tab -->
      <div :class="['tab-content', { 'tab-enter-active': activeTab === 'albums' }]">
        <div v-if="activeTab === 'albums'">
          <div v-if="loadingAlbums" class="flex justify-center items-center h-64">
            <div class="relative">
              <div class="w-16 h-16 border-4 border-gray-200 dark:border-gray-700 rounded-full"></div>
              <div class="absolute top-0 left-0 w-16 h-16 border-4 border-transparent border-t-blue-500 border-r-purple-500 rounded-full animate-spin"></div>
            </div>
          </div>

          <div
            v-else-if="albumRecommendations.length > 0"
            class="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-5"
          >
            <AlbumRecommendationCard
              v-for="recommendation in albumRecommendations"
              :key="recommendation.albumId"
              :recommendation="recommendation"
              @click="goToAlbum(recommendation.albumId)"
            />
          </div>

          <div v-else class="flex flex-col items-center justify-center h-80 text-gray-500 dark:text-gray-400">
            <div class="relative mb-4">
              <div class="w-20 h-20 rounded-2xl bg-gray-100 dark:bg-gray-800 flex items-center justify-center">
                <svg class="w-10 h-10 opacity-50" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M19 11H5m14 0a2 2 0 012 2v6a2 2 0 01-2 2H5a2 2 0 01-2-2v-6a2 2 0 012-2m14 0V9a2 2 0 00-2-2M5 11V9a2 2 0 012-2m0 0V5a2 2 0 012-2h6a2 2 0 012 2v2M7 7h10" />
                </svg>
              </div>
              <div class="absolute -inset-2 bg-gradient-to-r from-blue-400/20 to-purple-400/20 rounded-2xl blur-lg"></div>
            </div>
            <h3 class="text-lg font-medium mb-1">暂无推荐相册</h3>
            <p class="text-sm opacity-60">该人物暂无可推荐的相册</p>
          </div>
        </div>
      </div>

      <!-- 图片 Tab -->
      <div :class="['tab-content', { 'tab-enter-active': activeTab === 'photos' }]">
        <div v-if="activeTab === 'photos'">
          <div v-if="loadingPhotos" class="flex justify-center items-center h-64">
            <div class="relative">
              <div class="w-16 h-16 border-4 border-gray-200 dark:border-gray-700 rounded-full"></div>
              <div class="absolute top-0 left-0 w-16 h-16 border-4 border-transparent border-t-blue-500 border-r-purple-500 rounded-full animate-spin"></div>
            </div>
          </div>

          <div
            v-else-if="personPhotos.length > 0"
            class="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6 gap-3 pt-4"
          >
            <div
              v-for="face in personPhotos"
              :key="face.id"
              class="group relative aspect-square bg-gray-100 dark:bg-gray-800 rounded-xl overflow-hidden cursor-pointer transition-all duration-300 hover:shadow-lg hover:-translate-y-1"
              @click="goToPhoto(face.photoId!)"
            >
              <img
                :src="convertImagePath(face.photoThumbnailPath || '')"
                :alt="face.photoFilename"
                class="w-full h-full object-cover transition-transform duration-500 group-hover:scale-110"
                loading="lazy"
              />
              <div class="absolute inset-0 bg-gradient-to-t from-black/60 via-black/0 to-transparent opacity-0 group-hover:opacity-100 transition-all duration-300"></div>
              <div class="absolute bottom-2 right-2 opacity-0 group-hover:opacity-100 transition-all duration-300 transform translate-y-2 group-hover:translate-y-0">
                <div class="flex items-center gap-1 px-2 py-1 bg-black/50 backdrop-blur-sm rounded-lg">
                  <svg class="w-3 h-3 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z" />
                  </svg>
                </div>
              </div>
            </div>
          </div>

          <div v-if="loadingMorePhotos" class="mt-8 text-center">
            <div class="relative w-10 h-10 mx-auto">
              <div class="absolute inset-0 border-2 border-gray-200 dark:border-gray-700 rounded-full"></div>
              <div class="absolute inset-0 border-2 border-transparent border-t-blue-500 border-r-purple-500 rounded-full animate-spin"></div>
            </div>
            <p class="mt-3 text-sm text-gray-500 dark:text-gray-400">正在加载更多...</p>
          </div>

          <div v-if="!hasMorePhotos && !loadingMorePhotos && personPhotos.length > 0" class="mt-8 text-center">
            <p class="text-sm text-gray-400 dark:text-gray-500">
              已加载全部 {{ personPhotos.length }} 张
            </p>
          </div>

          <div v-else class="flex flex-col items-center justify-center h-80 text-gray-500 dark:text-gray-400">
            <div class="relative mb-4">
              <div class="w-20 h-20 rounded-2xl bg-gray-100 dark:bg-gray-800 flex items-center justify-center">
                <svg class="w-10 h-10 opacity-50" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z" />
                </svg>
              </div>
              <div class="absolute -inset-2 bg-gradient-to-r from-pink-400/20 to-orange-400/20 rounded-2xl blur-lg"></div>
            </div>
            <h3 class="text-lg font-medium mb-1">暂无照片</h3>
            <p class="text-sm opacity-60">该人物暂无可显示的照片</p>
          </div>
        </div>
      </div>
    </main>

    <!-- 移动端底部导航栏 -->
    <MobileBottomNav v-if="isMobile" />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useThemeStore } from '@/stores/theme'
import { personApi, PersonSummary, AlbumRecommendation, FaceFace } from '@/api'
import MobileBottomNav from '@/components/MobileBottomNav.vue'
import AlbumRecommendationCard from '@/components/AlbumRecommendationCard.vue'
import { useMobileNav } from '@/composables/useMobileNav'

const route = useRoute()
const router = useRouter()
const themeStore = useThemeStore()
const { isMobile } = useMobileNav()

// 返回按钮相关
const backButtonRef = ref<HTMLElement | null>(null)
const isBackButtonCollapsed = ref(false)
const SCROLL_HYSTERESIS = 60 // 滚动滞后值

const handleBackButtonScroll = () => {
  isBackButtonCollapsed.value = window.scrollY > SCROLL_HYSTERESIS
}

// 返回按钮交互：悬停倾斜
const onBackButtonMouseMove = (e: MouseEvent) => {
  const el = e.currentTarget as HTMLElement
  if (!el) return
  const rect = el.getBoundingClientRect()
  const cx = rect.left + rect.width / 2
  const cy = rect.top + rect.height / 2
  const dx = e.clientX - cx
  const dy = e.clientY - cy
  const ry = (dx / rect.width) * 10 // rotateY
  const rx = -(dy / rect.height) * 10 // rotateX
  el.style.transform = `perspective(400px) rotateX(${rx}deg) rotateY(${ry}deg) scale(${isBackButtonCollapsed.value ? 1 : 1.02})`
}

const onBackButtonMouseLeave = () => {
  const el = backButtonRef.value
  if (el) {
    el.style.transform = ''
  }
}

const onBackButtonMouseDown = () => {
  const el = backButtonRef.value
  if (el) {
    el.style.transform = 'perspective(400px) rotateX(0) rotateY(0) scale(0.95)'
  }
}

// 滚动位置
const scrolled = ref(0)

const handleScroll = () => {
  scrolled.value = window.scrollY
  handleBackButtonScroll()
}

const personId = ref<number>(parseInt(route.params.id as string))
const person = ref<PersonSummary | null>(null)
const activeTab = ref<'albums' | 'photos'>('albums')
const albumRecommendations = ref<AlbumRecommendation[]>([])
const personPhotos = ref<FaceFace[]>([])
const loadingAlbums = ref(false)
const loadingPhotos = ref(false)

// 分页加载状态
const currentPhotoPage = ref(0)
const loadingMorePhotos = ref(false)
const hasMorePhotos = ref(true)
const photoPageSize = 30
const totalPhotoCount = ref(0)

// 滚动加载更多照片
let scrollThrottleTimer: ReturnType<typeof setTimeout> | null = null
const SCROLL_THROTTLE_MS = 200
const LOAD_THRESHOLD = 1000

const handlePhotoScroll = () => {
  if (scrollThrottleTimer) return

  scrollThrottleTimer = setTimeout(() => {
    const scrollTop = window.scrollY || document.documentElement.scrollTop
    const windowHeight = window.innerHeight
    const documentHeight = document.documentElement.scrollHeight

    if (scrollTop + windowHeight >= documentHeight - LOAD_THRESHOLD) {
      loadMorePersonPhotos()
    }

    scrollThrottleTimer = null
  }, SCROLL_THROTTLE_MS)
}

const goBack = () => {
  // URL 参数优先，清理 sessionStorage
  const fromParam = route.query.from as string
  const sessionEntry = sessionStorage.getItem('person-entry-page')

  // URL 参数优先（更可靠），其次是 sessionStorage
  const targetPage = fromParam || sessionEntry

  console.log('[PersonDetail] goBack - fromParam:', fromParam, 'sessionEntry:', sessionEntry, 'target:', targetPage)

  // 清理 sessionStorage
  sessionStorage.removeItem('person-entry-page')

  // 根据来源决定去向
  if (targetPage && targetPage !== '/persons' && targetPage !== '') {
    // 从其他页面来的，返回该页面
    console.log('[PersonDetail] goBack - navigating to:', targetPage)
    router.push(targetPage)
  } else {
    // 默认返回人物列表
    console.log('[PersonDetail] goBack - navigating to /persons')
    router.push('/persons')
  }
}

const goToAlbum = (albumId: number) => {
  window.open(`/album/${albumId}`, '_blank')
}

const goToPhoto = (photoId: number) => {
  window.open(`/photo/${photoId}`, '_blank')
}

const convertImagePath = (path: string) => {
  if (!path) return path
  if (path.startsWith('/')) {
    return `/api/files${path}`
  }
  return path
}

const loadPerson = async () => {
  try {
    const response = await personApi.getPerson(personId.value)
    person.value = response.data
  } catch (error) {
    console.error('加载人物信息失败:', error)
  }
}

const loadAlbumRecommendations = async () => {
  loadingAlbums.value = true
  try {
    const response = await personApi.getPersonAlbumRecommendations(personId.value)
    albumRecommendations.value = response.data
  } catch (error) {
    console.error('加载相册推荐失败:', error)
    albumRecommendations.value = []
  } finally {
    loadingAlbums.value = false
  }
}

const loadPersonPhotos = async () => {
  loadingPhotos.value = true
  try {
    currentPhotoPage.value = 0
    const response = await personApi.getPersonPhotos(personId.value, currentPhotoPage.value, photoPageSize)
    const photos = response.data.content || []
    totalPhotoCount.value = person.value?.faceCount || response.data.totalElements || 0
    hasMorePhotos.value = !response.data.last
    personPhotos.value = photos.sort(() => Math.random() - 0.5)
  } catch (error) {
    console.error('加载人物照片失败:', error)
    personPhotos.value = []
    totalPhotoCount.value = 0
  } finally {
    loadingPhotos.value = false
  }
}

const loadMorePersonPhotos = async () => {
  if (loadingMorePhotos.value || !hasMorePhotos.value) return

  try {
    loadingMorePhotos.value = true
    currentPhotoPage.value++

    const response = await personApi.getPersonPhotos(personId.value, currentPhotoPage.value, photoPageSize)
    const newPhotos = response.data.content || []
    hasMorePhotos.value = !response.data.last

    const shuffledNewPhotos = newPhotos.sort(() => Math.random() - 0.5)
    personPhotos.value = [...personPhotos.value, ...shuffledNewPhotos]
  } catch (error) {
    console.error('加载更多人物照片失败:', error)
  } finally {
    loadingMorePhotos.value = false
  }
}

const loadTabContent = () => {
  if (activeTab.value === 'albums') {
    loadAlbumRecommendations()
  } else if (activeTab.value === 'photos') {
    loadPersonPhotos()
  }
}

watch(activeTab, () => {
  loadTabContent()
})

onMounted(() => {
  // 获取并保存来源页面（用于返回导航）
  // 优先级：URL query 参数 > document.referrer > 默认当前路径
  const fromParam = route.query.from as string
  let entryPage = ''

  console.log('[PersonDetail] onMounted - fromParam:', fromParam, 'referrer:', document.referrer)

  if (fromParam) {
    // 从新标签页打开时，通过 URL 参数传递来源
    entryPage = decodeURIComponent(fromParam)
    console.log('[PersonDetail] onMounted - entry from param:', entryPage)
  } else if (document.referrer && document.referrer.includes(window.location.origin)) {
    // 同标签页导航，从 referrer 获取来源
    try {
      const referrerUrl = new URL(document.referrer)
      entryPage = referrerUrl.pathname
      console.log('[PersonDetail] onMounted - entry from referrer:', entryPage)
    } catch {
      entryPage = '/persons'
      console.log('[PersonDetail] onMounted - entry default to /persons')
    }
  } else {
    // 直接 URL 访问或跨域来源，默认返回人物列表
    entryPage = ''
    console.log('[PersonDetail] onMounted - entry empty (direct URL)')
  }

  sessionStorage.setItem('person-entry-page', entryPage)

  // 确保页面从顶部开始显示
  window.scrollTo(0, 0)

  loadPerson()
  loadTabContent()
  window.addEventListener('scroll', handleScroll, { passive: true })
  window.addEventListener('scroll', handlePhotoScroll, { passive: true })
  window.addEventListener('keydown', handleKeydown)
})

// ESC 键返回
const handleKeydown = (e: KeyboardEvent) => {
  if (e.key === 'Escape') {
    e.preventDefault()
    goBack()
  }
}

onUnmounted(() => {
  window.removeEventListener('scroll', handleScroll)
  window.removeEventListener('scroll', handlePhotoScroll)
  window.removeEventListener('keydown', handleKeydown)
})
</script>

<style scoped>
.scrollbar-hide::-webkit-scrollbar {
  display: none;
}

.scrollbar-hide {
  -ms-overflow-style: none;
  scrollbar-width: none;
}

/* Tab切换动画 - 使用CSS类控制 */
.tab-content {
  animation-duration: 0.3s;
  animation-timing-function: ease-out;
  animation-fill-mode: both;
}

.tab-enter-active {
  animation-name: slideInRight;
}

/* 头像光晕慢速脉动动画 */
@keyframes pulse-slow {
  0%, 100% {
    opacity: 0.6;
    transform: scale(1);
  }
  50% {
    opacity: 0.8;
    transform: scale(1.05);
  }
}

.animate-pulse-slow {
  animation: pulse-slow 3s ease-in-out infinite;
}

@keyframes slideInRight {
  from {
    opacity: 0;
    transform: translateX(30px);
  }
  to {
    opacity: 1;
    transform: translateX(0);
  }
}
</style>
