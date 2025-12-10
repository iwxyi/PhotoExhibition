<template>
  <div class="min-h-screen bg-white dark:bg-gray-900">
    <!-- 导航栏 -->
    <nav class="sticky top-0 z-50 bg-white/80 dark:bg-gray-900/80 backdrop-blur-md border-b border-gray-200 dark:border-gray-800">
      <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div class="flex justify-between items-center h-16">
          <div class="flex items-center space-x-8">
            <router-link to="/" class="text-2xl font-light tracking-wider text-gray-900 dark:text-white">
              摄影展
            </router-link>
            <NavLinks />
          </div>
          <div class="flex items-center space-x-4">
            <button
              @click="themeStore.toggleTheme"
              class="p-2 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-800 transition-colors"
            >
              <svg v-if="!themeStore.isDark" class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M20.354 15.354A9 9 0 018.646 3.646 9.003 9.003 0 0012 21a9.003 9.003 0 008.354-5.646z" />
              </svg>
              <svg v-else class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 3v1m0 16v1m9-9h-1M4 12H3m15.364 6.364l-.707-.707M6.343 6.343l-.707-.707m12.728 0l-.707.707M6.343 17.657l-.707.707M16 12a4 4 0 11-8 0 4 4 0 018 0z" />
              </svg>
            </button>
            <FilterPanel v-model:show="showFilter" />
            <SettingsMenu />
          </div>
        </div>
      </div>
    </nav>

    <!-- 相册网格 -->
    <main class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-12">
      <!-- 分类 Tabs -->
      <div class="mb-6">
        <div class="flex gap-2 overflow-x-auto pb-2">
          <button
            v-for="c in ['全部', ...categories]"
            :key="c"
            @click="selectCategory(c)"
            class="px-4 py-2 rounded-full border transition-colors"
            :class="c === activeCategory
              ? 'bg-gray-900 text-white border-gray-800 dark:bg-white dark:text-gray-900 dark:border-white'
              : 'bg-gray-100 text-gray-800 border-gray-300 hover:bg-gray-200 dark:bg-gray-800 dark:text-gray-100 dark:border-gray-700 dark:hover:bg-gray-700'"
          >
            {{ c }}
          </button>
        </div>
      </div>

      <div v-if="loading && albums.length === 0" class="flex justify-center items-center h-96">
        <div class="animate-spin rounded-full h-12 w-12 border-t-2 border-b-2 border-gray-900 dark:border-white"></div>
      </div>

      <div
        v-if="albums.length > 0"
        :class="coverGridClass"
      >
        <AlbumCard
          v-for="album in albums"
          :key="album.id"
          :album="album"
          :size="coverSize"
          @click="goToAlbum(album.id)"
        />
      </div>

      <!-- 加载状态 -->
      <div v-if="isLoadingMore && albums.length > 0" class="mt-12 text-center">
        <div class="animate-spin rounded-full h-12 w-12 border-t-2 border-b-2 border-gray-900 dark:border-white mx-auto"></div>
      </div>
      <div v-if="!hasMore && albums.length > 0" class="mt-12 text-center text-gray-500 dark:text-gray-400">
        <p>已加载全部相册</p>
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
defineOptions({ name: 'Home' })
import { ref, onMounted, onUnmounted, onActivated, onDeactivated, nextTick, computed } from 'vue'
import { useRouter } from 'vue-router'
import { usePhotoStore } from '@/stores/photo'
import { useThemeStore } from '@/stores/theme'
import AlbumCard from '@/components/AlbumCard.vue'
import FilterPanel from '@/components/FilterPanel.vue'
import SettingsMenu from '@/components/SettingsMenu.vue'
import { useUiSettings } from '@/composables/useUiSettings'

const router = useRouter()
const photoStore = usePhotoStore()
const themeStore = useThemeStore()
import NavLinks from '@/components/NavLinks.vue'

const albums = computed(() => photoStore.albums)
const loading = computed(() => photoStore.loading)
const categories = computed(() => photoStore.categories)
const showFilter = ref(false)
const currentPage = ref(0)
const hasMore = ref(true)
const activeCategory = ref('全部')
const savedScrollTop = ref(0)
const isLoadingMore = ref(false)
const { coverSize } = useUiSettings()
const coverGridClass = computed(() => {
  if (coverSize.value === 'sm') {
    return 'grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5 gap-6'
  }
  if (coverSize.value === 'lg') {
    return 'grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-3 xl:grid-cols-4 gap-6'
  }
  return 'grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5 gap-6'
})

const goToAlbum = (id: number) => {
  router.push(`/album/${id}`)
}

const loadMore = async () => {
  // 防止重复加载
  if (loading.value || isLoadingMore.value || !hasMore.value) return
  
  // 保存当前滚动位置
  const scrollTop = window.scrollY || document.documentElement.scrollTop
  
  try {
    isLoadingMore.value = true
    currentPage.value++
    const cat = activeCategory.value === '全部' ? undefined : activeCategory.value
    
    // 直接调用 API，不通过 store，避免触发 loading
    const { api } = await import('@/api')
    const params: any = { page: currentPage.value, size: 12 }
    if (cat) params.category = cat
    const response = await api.get('/albums', { params })
    const data = response.data
    
    if (!data || !data.content || data.content.length === 0) {
      hasMore.value = false
      return
    }
    
    // 通过 store 追加数据，但不触发 loading
    // 直接操作 store 的 albums ref
    const currentAlbums = [...photoStore.albums]
    photoStore.albums = [...currentAlbums, ...data.content]
    hasMore.value = !data.last
    
    // 恢复滚动位置
    nextTick(() => {
      window.scrollTo({ top: scrollTop, behavior: 'instant' })
    })
  } catch (error) {
    console.error('加载更多失败:', error)
    // 加载失败时回退页码
    currentPage.value--
    hasMore.value = false
    // 恢复滚动位置
    nextTick(() => {
      window.scrollTo({ top: scrollTop, behavior: 'instant' })
    })
  } finally {
    isLoadingMore.value = false
  }
}

// 防抖滚动处理
let scrollTimer: ReturnType<typeof setTimeout> | null = null
const handleScroll = () => {
  if (scrollTimer) clearTimeout(scrollTimer)
  scrollTimer = setTimeout(() => {
    const scrollTop = window.scrollY || document.documentElement.scrollTop
    const windowHeight = window.innerHeight
    const documentHeight = document.documentElement.scrollHeight
    
    // 距离底部 800px 时开始加载
    if (scrollTop + windowHeight >= documentHeight - 800) {
      loadMore()
    }
  }, 100)
}

onMounted(async () => {
  try {
    await photoStore.fetchCategories()
    const data = await photoStore.fetchAlbums(0, 12, undefined)
    hasMore.value = !data.last
    window.addEventListener('scroll', handleScroll, { passive: true })
  } catch (error) {
    console.error('初始化加载失败:', error)
    hasMore.value = false
  }
})

onUnmounted(() => {
  window.removeEventListener('scroll', handleScroll)
  if (scrollTimer) clearTimeout(scrollTimer)
})

onActivated(() => {
  nextTick(() => {
    window.scrollTo({ top: savedScrollTop.value, behavior: 'instant' as ScrollBehavior })
    window.addEventListener('scroll', handleScroll, { passive: true })
  })
})

onDeactivated(() => {
  savedScrollTop.value = window.scrollY || 0
  window.removeEventListener('scroll', handleScroll)
})

const selectCategory = async (c: string) => {
  activeCategory.value = c
  currentPage.value = 0
  hasMore.value = true
  const cat = c === '全部' ? undefined : c
  const data = await photoStore.fetchAlbums(0, 12, cat)
  hasMore.value = !data.last
}
</script>

