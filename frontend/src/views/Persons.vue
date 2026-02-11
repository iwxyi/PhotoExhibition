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
          <div class="flex items-center space-x-3">
            <!-- 拍摄图标（深色背景、浅色图标） -->
            <div class="w-8 h-8 rounded-lg bg-gray-900 dark:bg-white flex items-center justify-center shadow-md">
              <svg class="w-5 h-5 text-white dark:text-gray-900" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M3 9a2 2 0 012-2h.93a2 2 0 001.664-.89l.812-1.22A2 2 0 0110.07 4h3.86a2 2 0 011.664.89l.812 1.22A2 2 0 0018.07 7H19a2 2 0 012 2v9a2 2 0 01-2 2H5a2 2 0 01-2-2V9z" />
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 13a3 3 0 11-6 0 3 3 0 016 0z" />
              </svg>
            </div>
            <router-link to="/" class="text-xl font-light tracking-wider text-gray-900 dark:text-white">
              摄影展
            </router-link>
            <NavLinks v-if="!isMobile" />
          </div>
          <div class="flex items-center space-x-4">
            <SettingsMenu />
          </div>
        </div>
      </div>
    </nav>

    <!-- 人物网格 -->
    <main class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 pt-6 pb-12">
      <!-- 标题 -->
      <div class="mb-6">
        <h1 class="text-2xl font-light tracking-wide text-gray-900 dark:text-white mb-2">人物</h1>
        <p class="text-sm text-gray-600 dark:text-gray-400">探索不同人物的精彩瞬间</p>
      </div>

      <!-- 加载状态 -->
      <div v-if="loading && persons.length === 0" class="flex justify-center items-center h-96">
        <div class="animate-spin rounded-full h-12 w-12 border-t-2 border-b-2 border-gray-900 dark:border-white"></div>
      </div>

      <!-- 人物网格 -->
      <div
        v-if="persons.length > 0"
        :class="personGridClass"
      >
        <PersonCard
          v-for="person in persons"
          :key="person.id"
          :person="person"
          :size="coverSize"
          @click="goToPerson(person.id)"
        />
      </div>

      <!-- 加载更多状态 -->
      <div v-if="isLoadingMore" class="mt-8 text-center">
        <div class="relative w-8 h-8 mx-auto">
          <div class="absolute inset-0 border-2 border-gray-200 dark:border-gray-700 rounded-full"></div>
          <div class="absolute inset-0 border-2 border-transparent border-t-blue-500 border-r-purple-500 rounded-full animate-spin"></div>
        </div>
        <p class="mt-3 text-sm text-gray-500 dark:text-gray-400">正在加载更多...</p>
      </div>

      <!-- 已加载全部提示 -->
      <div v-if="!hasMore && persons.length > 0 && !loading" class="mt-8 text-center">
        <p class="text-sm text-gray-400 dark:text-gray-500">
          已加载全部 {{ persons.length }} 位人物
        </p>
      </div>

      <!-- 空状态 -->
      <div v-if="!loading && persons.length === 0" class="flex flex-col items-center justify-center h-96 text-gray-500 dark:text-gray-400">
        <svg class="w-16 h-16 mb-4 opacity-50" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1" d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z" />
        </svg>
        <h3 class="text-lg font-medium mb-2">暂无人物数据</h3>
        <p class="text-sm text-center">请先在管理后台添加人物信息</p>
      </div>
    </main>

    <!-- 移动端底部导航栏 -->
    <MobileBottomNav v-if="isMobile" />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, onActivated, onDeactivated, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { useThemeStore } from '@/stores/theme'
import { personApi, PersonSummary } from '@/api'
import NavLinks from '@/components/NavLinks.vue'
import MobileBottomNav from '@/components/MobileBottomNav.vue'
import SettingsMenu from '@/components/SettingsMenu.vue'
import PersonCard from '@/components/PersonCard.vue'
import { useMobileNav } from '@/composables/useMobileNav'
import { useNavAutoHide } from '@/composables/useNavAutoHide'
import { useUiSettings } from '@/composables/useUiSettings'

const router = useRouter()
const themeStore = useThemeStore()
const { isMobile } = useMobileNav()
const { isHidden: navHidden } = useNavAutoHide()
const { coverSize } = useUiSettings()

const persons = ref<PersonSummary[]>([])
const loading = ref(false)
const currentPage = ref(0)
const hasMore = ref(true)
const isLoadingMore = ref(false)
const isDataLoaded = ref(false)  // 标记数据是否已加载
const savedScrollTop = ref(0)  // 保存滚动位置
const PAGE_SIZE = 20

const goToPerson = (id: number) => {
  window.open(`/person/${id}`, '_blank')
}

const loadPersons = async () => {
  loading.value = true
  try {
    const response = await personApi.getPersonsWithSample(0, PAGE_SIZE)
    const data = response.data
    persons.value = data.content || []
    hasMore.value = !data.last
    currentPage.value = 0
    isDataLoaded.value = true
  } catch (error) {
    console.error('加载人物列表失败:', error)
    persons.value = []
    hasMore.value = false
    isDataLoaded.value = true
  } finally {
    loading.value = false
  }
}

const loadMorePersons = async () => {
  if (isLoadingMore.value || !hasMore.value || loading.value) return

  try {
    isLoadingMore.value = true
    const nextPage = currentPage.value + 1
    const response = await personApi.getPersonsWithSample(nextPage, PAGE_SIZE)
    const data = response.data
    const newPersons = data.content || []

    if (newPersons.length > 0) {
      persons.value = [...persons.value, ...newPersons]
      currentPage.value = nextPage
      hasMore.value = !data.last
    } else {
      hasMore.value = false
    }
  } catch (error) {
    console.error('加载更多人物列表失败:', error)
  } finally {
    isLoadingMore.value = false
  }
}

// 滚动加载更多
let scrollTimer: ReturnType<typeof setTimeout> | null = null
const handleScroll = () => {
  if (scrollTimer) return

  scrollTimer = setTimeout(() => {
    const scrollTop = window.scrollY || document.documentElement.scrollTop
    const windowHeight = window.innerHeight
    const documentHeight = document.documentElement.scrollHeight

    // 距离底部 1000px 时开始加载
    if (scrollTop + windowHeight >= documentHeight - 1000) {
      loadMorePersons()
    }
    scrollTimer = null
  }, 100)
}

// 人物卡片网格布局（与相册列表保持一致）
const personGridClass = computed(() => {
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

onMounted(() => {
  if (!isDataLoaded.value) {
    loadPersons()
  }
  window.addEventListener('scroll', handleScroll, { passive: true })
})

onUnmounted(() => {
  window.removeEventListener('scroll', handleScroll)
  if (scrollTimer) clearTimeout(scrollTimer)
})

onActivated(() => {
  // 只在首次进入时加载数据，后续只恢复滚动位置和监听
  if (!isDataLoaded.value) {
    loadPersons()
  }
  // 重置加载状态，防止重复加载
  isLoadingMore.value = false
  // 恢复滚动位置
  nextTick(() => {
    window.scrollTo({ top: savedScrollTop.value, behavior: 'instant' as ScrollBehavior })
  })
  // 重新添加滚动监听
  window.addEventListener('scroll', handleScroll, { passive: true })
})

onDeactivated(() => {
  // 保存滚动位置
  savedScrollTop.value = window.scrollY || 0
  // 移除滚动监听，防止后台触发
  window.removeEventListener('scroll', handleScroll)
})
</script>
