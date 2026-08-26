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
            <div
              class="w-10 h-10 rounded-full overflow-hidden shadow-md ring-2 ring-white/50 dark:ring-gray-700/50 bg-transparent transition-opacity duration-300"
              :class="avatarEnterComplete ? 'opacity-100' : 'opacity-0'"
            >
              <img
                v-if="person?.sampleThumbnailPath"
                :src="convertImagePath(person.sampleThumbnailPath, person.samplePhotoId, person.sampleOriginalPath)"
                :alt="person.name"
                class="w-full h-full object-cover"
                :class="avatarEnterComplete ? 'avatar-scale-in' : 'avatar-hidden'"
                @load="onAvatarLoad"
              />
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
              <div
                class="absolute -inset-1 bg-gradient-to-r from-amber-500/30 via-orange-500/30 to-rose-500/30 rounded-full blur-xl opacity-0 animate-pulse-slow transition-opacity duration-300"
                :class="avatarEnterComplete ? 'opacity-60' : 'opacity-0'"
              ></div>
              <div
                class="relative w-36 h-36 md:w-44 md:h-44 lg:w-52 lg:h-52 rounded-full bg-transparent shadow-2xl overflow-hidden ring-4 ring-white/50 dark:ring-gray-800/50 opacity-0 transition-opacity duration-300"
                :class="avatarEnterComplete ? 'avatar-enter-active' : 'opacity-0'"
              >
                <img
                  v-if="person?.sampleThumbnailPath"
                  :src="convertImagePath(person.sampleThumbnailPath, person.samplePhotoId, person.sampleOriginalPath)"
                  :alt="person.name"
                  class="w-full h-full object-cover"
                  :class="avatarEnterComplete ? 'avatar-scale-in' : 'avatar-hidden'"
                  @load="onAvatarLoad"
                />
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
      <div class="relative border-b border-gray-200/50 dark:border-gray-800/50 overflow-visible">
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
            :class="albumGridClass"
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
              class="group relative aspect-square bg-gray-100 dark:bg-gray-800 rounded-xl overflow-visible cursor-pointer transition-all duration-300 hover:shadow-lg hover:-translate-y-1 isolate"
              :class="{ 'z-50': visiblePhotoId === face.photoId || animatingPhotoId === face.photoId }"
              :style="{ transform: (visiblePhotoId === face.photoId || animatingPhotoId === face.photoId) ? 'translateZ(0)' : 'none' }"
              @click="goToPhoto(face.photoId!)"
              @mouseenter="onPhotoHover(face)"
              @mouseleave="onPhotoLeave(face)"
            >
              <!-- 背景图容器（负责裁切） -->
              <div class="absolute inset-0 overflow-hidden rounded-xl">
              <img
                :src="convertImagePath(face.photoThumbnailPath || '', face.photoId, face.photoOriginalPath)"
                :alt="face.photoFilename"
                  class="w-full h-full object-cover"
                :style="{ 
                  transform: visiblePhotoId === face.photoId ? 'scale(1.44)' : 'scale(1)', 
                  transformOrigin: 'center',
                  transition: 'transform 500ms cubic-bezier(0.34, 1.56, 0.64, 1)'
                }"
                loading="lazy"
              />
                </div>
              
              <!-- 抠图浮层（允许溢出容器） -->
              <div 
                class="absolute inset-0 z-20 pointer-events-none overflow-visible rounded-xl"
                :style="{ 
                  ...getBgRemoveContainerStyle(face, visiblePhotoId === face.photoId), 
                  transformOrigin: 'center', 
                  transition: 'all 500ms cubic-bezier(0.34, 1.56, 0.64, 1)',
                  opacity: (visiblePhotoId === face.photoId || bgRemoveSuccessCache[face.photoId!] || bgRemoveLoadingCache[face.photoId!]) && !bgRemoveFailedCache[face.photoId!] ? 1 : 0
                }"
              >
                <img
                  :src="bgRemoveBlobUrlCache[face.photoId!] || getBackgroundRemovedUrl(face.photoId!, 'medium')"
                  :alt="face.photoFilename"
                  class="w-full h-full rounded-xl"
                  :style="{ objectFit: 'cover' }"
                  @load="handleBgRemoveSuccess(face.photoId!)"
                  @error="handleBgRemoveError($event, face)"
                />
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
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useThemeStore } from '@/stores/theme'
import { useLanguageStore } from '@/stores/language'
import { useUiSettings } from '@/composables/useUiSettings'
import { personApi, PersonSummary, AlbumRecommendation, FaceFace, backgroundRemovalApi } from '@/api'
import { buildPublicPath, stripPublicSlug } from '@/utils/publicRoute'
import { buildPhotoAssetUrl } from '@/utils/photoUrl'
import MobileBottomNav from '@/components/MobileBottomNav.vue'
import AlbumRecommendationCard from '@/components/AlbumRecommendationCard.vue'
import { useMobileNav } from '@/composables/useMobileNav'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const themeStore = useThemeStore()
const languageStore = useLanguageStore()
const { coverSize } = useUiSettings()
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

const personId = computed(() => {
  const id = route.params.id || route.params.keyword
  if (!id) return null
  const parsed = parseInt(id as string)
  return isNaN(parsed) ? null : parsed
})

// 用于存储按人名搜索找到的人物 ID（当路由参数是人名时）
const resolvedPersonId = ref<number | null>(null)

// 最终使用的人物 ID（优先使用路由 ID，否则使用搜索结果）
const finalPersonId = computed(() => {
  return personId.value || resolvedPersonId.value
})

const person = ref<PersonSummary | null>(null)

// 监听人物数据变化，更新页面标题
watch(person, (newPerson) => {
  if (newPerson?.name) {
    const baseTitle = languageStore.language === 'zh'
      ? (authStore.projectNameZh || authStore.projectNameEn || '光忆集')
      : (authStore.projectNameEn || authStore.projectNameZh || 'Aurellic Memoriq')
    document.title = `${baseTitle} - ${newPerson.name}`
  }
}, { immediate: true })

const loadingPerson = ref(true)
const avatarEnterComplete = ref(false)
const activeTab = ref<'albums' | 'photos'>('albums')
const albumRecommendations = ref<AlbumRecommendation[]>([])
const personPhotos = ref<FaceFace[]>([])
const loadingAlbums = ref(false)
const loadingPhotos = ref(false)

// 相册推荐网格布局（与主页保持一致）
const albumGridClass = computed(() => {
  if (coverSize.value === 'sm') {
    return 'grid grid-cols-3 sm:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6 gap-3'
  }
  if (coverSize.value === 'md') {
    return 'grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 gap-4'
  }
  if (coverSize.value === 'lg') {
    return 'grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-6'
  }
  return 'grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 gap-4'
})

// 人物抠图悬浮效果相关状态
// 使用普通对象以支持更好的响应式
const bgRemoveLoadingCache = ref<Record<number, boolean>>({})
const bgRemoveFailedCache = ref<Record<number, boolean>>({})
const bgRemoveSuccessCache = ref<Record<number, boolean>>({})
// 存储 blob URL 以避免重复请求
const bgRemoveBlobUrlCache = ref<Record<number, string>>({})
const bgRemoveEnabled = ref<boolean | null>(null) // null = unknown, true = enabled, false = disabled

// 用于控制两层同步显示的 ID（抠图加载完成后才设置）
const visiblePhotoId = ref<number | null>(null)

// 用于保持 z-index 高直到收缩动画完成
const animatingPhotoId = ref<number | null>(null)

// 悬浮显示抠图
const onPhotoHover = (face: FaceFace) => {
  if (face.photoId) {
    // 立即触发放大动画，不需要等待抠图完成
    visiblePhotoId.value = face.photoId
    
    // 如果功能明确未启用，直接返回
    if (bgRemoveEnabled.value === false) {
      return
    }
    // 异步预加载抠图（不阻塞放大动画）
    preloadBackgroundRemovedImage(face.photoId)
  }
}

// 预加载抠图图片（先检查响应状态，避免显示错误图片）
const preloadBackgroundRemovedImage = (photoId: number) => {
  // 如果正在加载或已成功，直接返回
  if (bgRemoveSuccessCache.value[photoId] || bgRemoveLoadingCache.value[photoId]) {
    return
  }

  // 标记正在加载
  bgRemoveLoadingCache.value[photoId] = true

  const url = getBackgroundRemovedUrl(photoId)

  // 先用 fetch 检查响应状态，避免显示错误图片
  fetch(url)
    .then(response => {
      // 202 Accepted 表示处理中，稍后重试
      if (response.status === 202) {
        delete bgRemoveLoadingCache.value[photoId]
        // 2秒后重试
        setTimeout(() => preloadBackgroundRemovedImage(photoId), 2000)
        return null
      }
      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`)
      }
      return response.blob()
    })
    .then(blob => {
      clearTimeout(timeout)
      delete bgRemoveLoadingCache.value[photoId]
      // 创建 blob URL 用于显示
      const blobUrl = URL.createObjectURL(blob)
      // 存储 blob URL 供 img 使用
      bgRemoveBlobUrlCache.value[photoId] = blobUrl
      bgRemoveSuccessCache.value[photoId] = true
      // 如果是第一次检测成功，认为功能已启用
      if (bgRemoveEnabled.value === null) {
        bgRemoveEnabled.value = true
      }
    })
    .catch(() => {
      clearTimeout(timeout)
      delete bgRemoveLoadingCache.value[photoId]
      // 加载失败时，认为功能未启用或该图片处理失败
      if (bgRemoveEnabled.value === null) {
        bgRemoveEnabled.value = false
      }
    })
  
  // 设置超时：3秒内没加载成功则视为失败
  const timeout = setTimeout(() => {
    delete bgRemoveLoadingCache.value[photoId]
    if (bgRemoveEnabled.value === null) {
      bgRemoveEnabled.value = false
    }
  }, 3000)
}

// 离开时保持 z-index 直到动画完成
const onPhotoLeave = (face: FaceFace) => {
  if (face.photoId && (visiblePhotoId.value === face.photoId || animatingPhotoId.value === face.photoId)) {
    const photoId = face.photoId
    visiblePhotoId.value = null
    // 保持 z-index 高直到动画完成(500ms)
    animatingPhotoId.value = photoId
    setTimeout(() => {
      if (animatingPhotoId.value === photoId) {
        animatingPhotoId.value = null
      }
    }, 550)
  }
}

// 获取抠图后的图片URL
const getBackgroundRemovedUrl = (photoId: number, quality: string = 'medium'): string => {
  return backgroundRemovalApi.getUrl(photoId, quality)
}

// 处理抠图加载成功
const handleBgRemoveSuccess = (photoId: number) => {
  bgRemoveSuccessCache.value[photoId] = true
}

// 处理抠图加载失败
const handleBgRemoveError = (event: Event, face: FaceFace) => {
  console.warn('[抠图] 加载失败:', face.photoId, event)
  // 标记为失败，避免重复尝试
  if (face.photoId) {
    delete bgRemoveLoadingCache.value[face.photoId]
    bgRemoveFailedCache.value[face.photoId] = true
  }
  // 清理 blob URL
  if (face.photoId && bgRemoveBlobUrlCache.value[face.photoId]) {
    URL.revokeObjectURL(bgRemoveBlobUrlCache.value[face.photoId])
    delete bgRemoveBlobUrlCache.value[face.photoId]
  }
}

// 根据人脸位置计算剪裁路径（抠出人物区域）
const getFaceClipPath = (face: FaceFace): string => {
  if (face.x !== undefined && face.y !== undefined && face.width !== undefined && face.height !== undefined) {
    // 人脸在图片中的归一化坐标
    const left = face.x * 100
    const top = face.y * 100
    const faceWidth = face.width * 100
    const faceHeight = face.height * 100
    
    // 扩展区域，让人物更完整（头部+肩部）
    const paddingX = faceWidth * 0.5
    const paddingY = faceHeight * 0.8
    
    const clipLeft = Math.max(0, left - paddingX)
    const clipTop = Math.max(0, top - paddingY * 0.3)
    const clipWidth = Math.min(100 - clipLeft, faceWidth + paddingX * 2)
    const clipHeight = Math.min(100 - clipTop, faceHeight + paddingY * 1.5)
    
    return `inset(${clipTop}% ${100 - clipLeft - clipWidth}% ${100 - clipTop - clipHeight}% ${clipLeft}%)`
  }
  return 'none'
}

// 根据照片宽高比计算抠图层的展开参数
// 横图：左右展开；竖图：上下展开

// 计算抠图层容器的尺寸（展开时显示完整图片，需要重新居中）
const getBgRemoveContainerStyle = (face: FaceFace, isExpanded: boolean): Record<string, string> => {
  if (!face.photoWidth || !face.photoHeight) {
    return { width: '100%', height: '100%' }
  }
  
  const ratio = face.photoWidth / face.photoHeight
  const baseScale = isExpanded ? 1.44 : 1
  
  if (isExpanded && ratio > 1) {
    // 横图：宽度展开，需要向左偏移使中心对齐
    const width = `${ratio * 100}%`
    const leftOffset = -((ratio - 1) / 2) * 100
    return { width, height: '100%', left: `${leftOffset}%`, transform: `scale(${baseScale})` }
  } else if (isExpanded && ratio < 1) {
    // 竖图：高度展开，需要向上偏移使中心对齐
    const height = `${(1 / ratio) * 100}%`
    const topOffset = -((1 / ratio - 1) / 2) * 100
    return { width: '100%', height, top: `${topOffset}%`, transform: `scale(${baseScale})` }
  }
  
  // 方形图或不展开时
  return { width: '100%', height: '100%', transform: `scale(${baseScale})` }
}

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
  const normalizedTarget = targetPage ? stripPublicSlug(targetPage) : ''
  if (targetPage && normalizedTarget !== '/persons' && normalizedTarget !== '') {
    // 从其他页面来的，返回该页面
    console.log('[PersonDetail] goBack - navigating to:', targetPage)
    router.push(buildPublicPath(targetPage, route.path))
  } else {
    // 默认返回人物列表
    console.log('[PersonDetail] goBack - navigating to /persons')
    router.push(buildPublicPath('/persons', route.path))
  }
}

const goToAlbum = (albumId: number) => {
  window.open(buildPublicPath(`/a/${albumId}`, route.path), '_blank')
}

const goToPhoto = (photoId: number) => {
  window.open(buildPublicPath(`/photo/${photoId}`, route.path), '_blank')
}

const convertImagePath = (path: string, photoId?: number | null, originalPath?: string | null) => {
  return buildPhotoAssetUrl({
    id: photoId,
    originalPath: originalPath,
    mediumThumbPath: path
  }, 'medium') || path
}

// 头像图片加载完成后的动画
const onAvatarLoad = () => {
  avatarEnterComplete.value = true
}

// 解析人物 ID（支持 ID 或名称）
const resolvePersonId = async (): Promise<number | null> => {
  // 如果是数字 ID，直接返回并存储
  if (personId.value) {
    resolvedPersonId.value = personId.value
    return personId.value
  }

  // 否则尝试按名称搜索
  const keyword = route.params.id || route.params.keyword
  if (keyword) {
    try {
      const searchResponse = await personApi.searchByName(decodeURIComponent(keyword as string))
      if (searchResponse.data && searchResponse.data.id) {
        resolvedPersonId.value = searchResponse.data.id
        return searchResponse.data.id
      }
    } catch (error) {
      console.error('按名称搜索人物失败:', error)
    }
  }
  return null
}

const loadPerson = async (personIdToLoad: number) => {
  loadingPerson.value = true
  if (!personIdToLoad) {
    loadingPerson.value = false
    console.error('[PersonDetail] 无法确定人物 ID')
    return
  }

  try {
    const response = await personApi.getPerson(personIdToLoad)
    person.value = response.data
  } catch (error) {
    console.error('加载人物信息失败:', error)
  } finally {
    loadingPerson.value = false
  }
}

const loadAlbumRecommendations = async (personIdToLoad: number) => {
  loadingAlbums.value = true
  try {
    const response = await personApi.getPersonAlbumRecommendations(personIdToLoad)
    albumRecommendations.value = response.data
  } catch (error) {
    console.error('加载相册推荐失败:', error)
    albumRecommendations.value = []
  } finally {
    loadingAlbums.value = false
  }
}

const loadPersonPhotos = async (personIdToLoad: number) => {
  loadingPhotos.value = true
  try {
    currentPhotoPage.value = 0
    const response = await personApi.getPersonPhotos(personIdToLoad, currentPhotoPage.value, photoPageSize)
    const photos = response.data.content || []
    totalPhotoCount.value = person.value?.faceCount || response.data.totalElements || 0
    hasMorePhotos.value = !response.data.last
    personPhotos.value = photos
  } catch (error) {
    console.error('加载人物照片失败:', error)
    personPhotos.value = []
    totalPhotoCount.value = 0
  } finally {
    loadingPhotos.value = false
  }
}

const loadMorePersonPhotos = async () => {
  if (loadingMorePhotos.value || !hasMorePhotos.value || !resolvedPersonId.value) return

  const nextPage = currentPhotoPage.value + 1

  try {
    loadingMorePhotos.value = true

    const response = await personApi.getPersonPhotos(resolvedPersonId.value, nextPage, photoPageSize)
    const newPhotos = response.data.content || []
    hasMorePhotos.value = !response.data.last

    // 去重：过滤掉已存在的 photoId
    const existingIds = new Set(personPhotos.value.map(p => p.photoId))
    const uniqueNewPhotos = newPhotos.filter(p => !existingIds.has(p.photoId))

    // 更新页码
    currentPhotoPage.value = nextPage

    // 直接追加（后端已按拍摄时间倒序）
    personPhotos.value = [...personPhotos.value, ...uniqueNewPhotos]
  } catch (error) {
    console.error('加载更多人物照片失败:', error)
  } finally {
    loadingMorePhotos.value = false
  }
}

const loadTabContent = (personIdToLoad: number) => {
  if (activeTab.value === 'albums') {
    loadAlbumRecommendations(personIdToLoad)
  } else if (activeTab.value === 'photos') {
    loadPersonPhotos(personIdToLoad)
  }
}

watch(activeTab, () => {
  if (resolvedPersonId.value) {
    loadTabContent(resolvedPersonId.value)
  }
})

onMounted(async () => {
  // 确保页面从顶部开始显示
  window.scrollTo(0, 0)

  // 解析人物 ID（支持 ID 或名称）- 异步等待搜索结果
  const targetPersonId = await resolvePersonId()

  // 检查人物 ID 是否有效
  if (!targetPersonId) {
    console.error('[PersonDetail] 无法确定人物 ID')
    router.push(buildPublicPath('/persons', route.path))
    return
  }

  // 加载人物数据和标签内容（并行加载以提高速度）
  await Promise.all([
    loadPerson(targetPersonId),
    loadTabContent(targetPersonId)
  ])

  // 优先级：URL query 参数 > document.referrer > 默认当前路径
  const fromParam = route.query.from as string
  let entryPage = ''


  if (fromParam) {
    // 从新标签页打开时，通过 URL 参数传递来源
    entryPage = decodeURIComponent(fromParam)
  } else if (document.referrer && document.referrer.includes(window.location.origin)) {
    // 同标签页导航，从 referrer 获取来源
    try {
      const referrerUrl = new URL(document.referrer)
      entryPage = referrerUrl.pathname
    } catch {
      entryPage = buildPublicPath('/persons', route.path)
    }
  } else {
    // 直接 URL 访问或跨域来源，默认返回人物列表
    entryPage = ''
  }

  sessionStorage.setItem('person-entry-page', entryPage)

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

/* 头像隐藏状态 */
.avatar-hidden {
  opacity: 0;
}

/* 头像展开动画 */
.avatar-enter-active {
  animation: avatarScaleIn 0.4s cubic-bezier(0.34, 1.56, 0.64, 1) forwards;
}

.avatar-scale-in {
  animation: avatarImageReveal 0.5s cubic-bezier(0.34, 1.56, 0.64, 1) forwards;
}

@keyframes avatarScaleIn {
  0% {
    transform: scale(0.9);
    opacity: 0;
  }
  100% {
    transform: scale(1);
    opacity: 1;
  }
}

@keyframes avatarImageReveal {
  0% {
    opacity: 0;
    transform: scale(0.8);
  }
  100% {
    opacity: 1;
    transform: scale(1);
  }
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
