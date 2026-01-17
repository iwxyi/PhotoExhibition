<template>
  <div class="min-h-screen bg-white dark:bg-gray-900">
    <!-- 导航栏 -->
    <nav
      class="sticky top-0 z-50 bg-white/80 dark:bg-gray-900/80 backdrop-blur-md border-b border-gray-200 dark:border-gray-800 safe-area-inset-top transition-transform duration-300 ease-in-out transform-gpu"
      :class="{ '-translate-y-full': isMobile && navHidden }"
      style="padding-top: env(safe-area-inset-top);"
    >
      <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div class="flex justify-between items-center h-12">
          <div class="flex items-center space-x-4">
            <button
              @click="goBack"
              class="p-2 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-100 transition-all duration-200 hover:scale-110 hover:shadow-md transform-gpu group relative overflow-hidden"
            >
              <svg class="w-5 h-5 transition-all duration-300 group-hover:-translate-x-1" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 19l-7-7 7-7" />
              </svg>
              <div class="absolute inset-0 bg-gradient-to-r from-blue-500/10 to-purple-500/10 opacity-0 group-hover:opacity-100 transition-opacity duration-200 rounded-lg"></div>
            </button>
            <router-link to="/" class="text-xl font-light tracking-wider text-gray-900 dark:text-white">
              摄影展
            </router-link>
          </div>
          <div class="flex items-center space-x-4">
            <button
              @click="themeStore.toggleTheme"
              class="p-2 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-100 transition-all duration-200 hover:scale-110 hover:shadow-md transform-gpu group relative overflow-hidden"
            >
              <svg v-if="!themeStore.isDark" class="w-5 h-5 transition-all duration-300 group-hover:rotate-12 group-hover:scale-110" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M20.354 15.354A9 9 0 018.646 3.646 9.003 9.003 0 0012 21a9.003 9.003 0 008.354-5.646z" />
              </svg>
              <svg v-else class="w-5 h-5 transition-all duration-300 group-hover:rotate-12 group-hover:scale-110" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 3v1m0 16v1m9-9h-1M4 12H3m15.364 6.364l-.707-.707M6.343 6.343l-.707-.707m12.728 0l-.707.707M6.343 17.657l-.707.707M16 12a4 4 0 11-8 0 4 4 0 018 0z" />
              </svg>
              <div class="absolute inset-0 bg-gradient-to-r from-yellow-500/10 to-orange-500/10 opacity-0 group-hover:opacity-100 transition-opacity duration-200 rounded-lg"></div>
            </button>
            <SettingsMenu />
          </div>
        </div>
      </div>
    </nav>

    <!-- 人物信息头部 -->
    <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 pt-6">
      <div class="flex flex-col lg:flex-row gap-6 mb-8">
        <!-- 人物头像 -->
        <div class="flex-shrink-0">
          <div class="w-32 h-32 rounded-full bg-gradient-to-br from-gray-100 to-gray-200 dark:from-gray-700 dark:to-gray-800 overflow-hidden border-4 border-white dark:border-gray-800 shadow-lg">
            <img
              v-if="person?.sampleThumbnailPath"
              :src="convertImagePath(person.sampleThumbnailPath)"
              :alt="person.name"
              class="w-full h-full object-cover"
            />
            <div v-else class="w-full h-full flex items-center justify-center">
              <svg class="w-16 h-16 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z" />
              </svg>
            </div>
          </div>
        </div>

        <!-- 人物信息 -->
        <div class="flex-1 min-w-0">
          <h1 class="text-3xl font-light tracking-wide text-gray-900 dark:text-white mb-2">
            {{ person?.name }}
          </h1>
          <p v-if="person?.description" class="text-gray-600 dark:text-gray-400 mb-4 leading-relaxed">
            {{ person.description }}
          </p>

          <!-- 统计信息 -->
          <div class="flex flex-wrap gap-6 text-sm">
            <div class="flex items-center text-gray-500 dark:text-gray-400">
              <svg class="w-5 h-5 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z" />
              </svg>
              <span>{{ person?.faceCount || 0 }} 张人脸照片</span>
            </div>
            <div v-if="person?.photoCount && person.photoCount > 0" class="flex items-center text-gray-500 dark:text-gray-400">
              <svg class="w-5 h-5 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z" />
              </svg>
              <span>{{ person.photoCount }} 张照片</span>
            </div>
            <div v-if="person?.albumCount && person.albumCount > 0" class="flex items-center text-gray-500 dark:text-gray-400">
              <svg class="w-5 h-5 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 11H5m14 0a2 2 0 012 2v6a2 2 0 01-2 2H5a2 2 0 01-2-2v-6a2 2 0 012-2m14 0V9a2 2 0 00-2-2M5 11V9a2 2 0 012-2m0 0V5a2 2 0 012-2h6a2 2 0 012 2v2M7 7h10" />
              </svg>
              <span>{{ person.albumCount }} 个相册</span>
            </div>
          </div>
        </div>
      </div>

      <!-- Tab 切换 -->
      <div class="border-b border-gray-200 dark:border-gray-700 mb-6">
        <nav class="-mb-px flex space-x-8">
          <button
            @click="activeTab = 'albums'"
            class="whitespace-nowrap py-2 px-1 border-b-2 font-medium text-sm transition-colors duration-200"
            :class="activeTab === 'albums'
              ? 'border-gray-900 text-gray-900 dark:border-white dark:text-white'
              : 'border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300 dark:text-gray-400 dark:hover:text-gray-300 dark:hover:border-gray-600'"
          >
            相册 ({{ albumRecommendations.length }})
          </button>
          <button
            @click="activeTab = 'photos'"
            class="whitespace-nowrap py-2 px-1 border-b-2 font-medium text-sm transition-colors duration-200"
            :class="activeTab === 'photos'
              ? 'border-gray-900 text-gray-900 dark:border-white dark:text-white'
              : 'border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300 dark:text-gray-400 dark:hover:text-gray-300 dark:hover:border-gray-600'"
          >
            图片 ({{ person?.faceCount || 0 }})
          </button>
        </nav>
      </div>
    </div>

    <!-- 内容区域 -->
    <main class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 pb-12">
      <!-- 相册 Tab -->
      <div v-if="activeTab === 'albums'">
        <div v-if="loadingAlbums" class="flex justify-center items-center h-64">
          <div class="animate-spin rounded-full h-12 w-12 border-t-2 border-b-2 border-gray-900 dark:border-white"></div>
        </div>

        <div
          v-else-if="albumRecommendations.length > 0"
          class="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-6"
        >
          <AlbumRecommendationCard
            v-for="recommendation in albumRecommendations"
            :key="recommendation.albumId"
            :recommendation="recommendation"
            @click="goToAlbum(recommendation.albumId)"
          />
        </div>

        <div v-else class="flex flex-col items-center justify-center h-64 text-gray-500 dark:text-gray-400">
          <svg class="w-16 h-16 mb-4 opacity-50" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1" d="M19 11H5m14 0a2 2 0 012 2v6a2 2 0 01-2 2H5a2 2 0 01-2-2v-6a2 2 0 012-2m14 0V9a2 2 0 00-2-2M5 11V9a2 2 0 012-2m0 0V5a2 2 0 012-2h6a2 2 0 012 2v2M7 7h10" />
          </svg>
          <h3 class="text-lg font-medium mb-2">暂无推荐相册</h3>
          <p class="text-sm text-center">该人物暂无可推荐的相册</p>
        </div>
      </div>

      <!-- 随机图片 Tab -->
      <div v-if="activeTab === 'photos'">
        <div v-if="loadingPhotos" class="flex justify-center items-center h-64">
          <div class="animate-spin rounded-full h-12 w-12 border-t-2 border-b-2 border-gray-900 dark:border-white"></div>
        </div>

        <div
          v-else-if="personPhotos.length > 0"
          class="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6 gap-4"
        >
          <div
            v-for="face in personPhotos"
            :key="face.id"
            class="group relative aspect-square bg-gray-100 dark:bg-gray-800 rounded-lg overflow-hidden cursor-pointer transform transition-all duration-200 hover:scale-105 hover:shadow-lg"
            @click="goToPhoto(face.photoId!)"
          >
            <img
              :src="convertImagePath(face.photoThumbnailPath)"
              :alt="face.photoFilename"
              class="w-full h-full object-cover"
              loading="lazy"
            />
            <div class="absolute inset-0 bg-black/0 group-hover:bg-black/20 transition-colors duration-200"></div>
          </div>
        </div>

        <!-- 加载更多状态 -->
        <div v-if="loadingMorePhotos" class="mt-8 text-center">
          <div class="animate-spin rounded-full h-8 w-8 border-t-2 border-b-2 border-gray-900 dark:border-white mx-auto"></div>
          <p class="mt-2 text-sm text-gray-600 dark:text-gray-400">正在加载更多照片...</p>
        </div>

        <!-- 已加载全部提示 -->
        <div v-if="!hasMorePhotos && !loadingMorePhotos && personPhotos.length > 0" class="mt-8 text-center">
          <p class="text-sm text-gray-500 dark:text-gray-400">
            已加载全部 {{ personPhotos.length }} 张照片，共 {{ totalPhotoCount }} 张
          </p>
        </div>

        <div v-else class="flex flex-col items-center justify-center h-64 text-gray-500 dark:text-gray-400">
          <svg class="w-16 h-16 mb-4 opacity-50" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1" d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z" />
          </svg>
          <h3 class="text-lg font-medium mb-2">暂无照片</h3>
          <p class="text-sm text-center">该人物暂无可显示的照片</p>
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
import { personApi, PersonSummary, AlbumRecommendation, Photo, FaceFace } from '@/api'
import NavLinks from '@/components/NavLinks.vue'
import MobileBottomNav from '@/components/MobileBottomNav.vue'
import SettingsMenu from '@/components/SettingsMenu.vue'
import AlbumRecommendationCard from '@/components/AlbumRecommendationCard.vue'
import { useMobileNav } from '@/composables/useMobileNav'
import { useNavAutoHide } from '@/composables/useNavAutoHide'

const route = useRoute()
const router = useRouter()
const themeStore = useThemeStore()
const { isMobile } = useMobileNav()
const { isHidden: navHidden } = useNavAutoHide()

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
const photoPageSize = 30 // 每次加载30张照片
const totalPhotoCount = ref(0) // 该人物的总照片数量

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
  router.push('/persons')
}

const goToAlbum = (albumId: number) => {
  window.open(`/album/${albumId}`, '_blank')
}

const goToPhoto = (photoId: number) => {
  window.open(`/photo/${photoId}`, '_blank')
}

// 转换图片路径，将数据库中的绝对路径转换为可访问的API路径
const convertImagePath = (path: string) => {
  if (!path) return path
  // 如果路径以/开头，去掉开头的/，然后加上/api/files/前缀
  if (path.startsWith('/')) {
    return `/api/files${path}`
  }
  return path
}

const loadPerson = async () => {
  try {
    // 直接获取单个人物信息
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

    // 随机排序当前页的照片
    personPhotos.value = photos.sort(() => Math.random() - 0.5)
  } catch (error) {
    console.error('加载人物照片失败:', error)
    personPhotos.value = []
    totalPhotoCount.value = 0
  } finally {
    loadingPhotos.value = false
  }
}

// 加载更多照片
const loadMorePersonPhotos = async () => {
  if (loadingMorePhotos.value || !hasMorePhotos.value) return

  try {
    loadingMorePhotos.value = true
    currentPhotoPage.value++

    const response = await personApi.getPersonPhotos(personId.value, currentPhotoPage.value, photoPageSize)
    const newPhotos = response.data.content || []
    hasMorePhotos.value = !response.data.last

    // 随机排序新照片并添加到现有照片中
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
  loadPerson()
  loadTabContent()
  // 添加滚动监听器，用于图片Tab的分页加载
  window.addEventListener('scroll', handlePhotoScroll, { passive: true })
})

onUnmounted(() => {
  window.removeEventListener('scroll', handlePhotoScroll)
})
</script>
