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

      <div v-if="loading" class="flex justify-center items-center h-96">
        <div class="animate-spin rounded-full h-12 w-12 border-t-2 border-b-2 border-gray-900 dark:border-white"></div>
      </div>

      <div v-else class="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5 gap-6">
        <AlbumCard
          v-for="album in albums"
          :key="album.id"
          :album="album"
          @click="goToAlbum(album.id)"
        />
      </div>

      <!-- 加载更多 -->
      <div v-if="hasMore" class="mt-12 text-center">
        <button
          @click="loadMore"
          :disabled="loading"
          class="px-6 py-3 bg-gray-900 dark:bg-white text-white dark:text-gray-900 rounded-lg hover:bg-gray-800 dark:hover:bg-gray-100 transition-colors disabled:opacity-50"
        >
          {{ loading ? '加载中...' : '加载更多' }}
        </button>
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
defineOptions({ name: 'Home' })
import { ref, onMounted, onActivated, onDeactivated, nextTick, computed } from 'vue'
import { useRouter } from 'vue-router'
import { usePhotoStore } from '@/stores/photo'
import { useThemeStore } from '@/stores/theme'
import AlbumCard from '@/components/AlbumCard.vue'
import FilterPanel from '@/components/FilterPanel.vue'

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

const goToAlbum = (id: number) => {
  router.push(`/album/${id}`)
}

const loadMore = async () => {
  currentPage.value++
  const cat = activeCategory.value === '全部' ? undefined : activeCategory.value
  const data = await photoStore.fetchAlbums(currentPage.value, 12, cat)
  hasMore.value = !data.last
}

onMounted(async () => {
  await photoStore.fetchCategories()
  await photoStore.fetchAlbums(0, 12, undefined)
})

onActivated(() => {
  nextTick(() => {
    window.scrollTo({ top: savedScrollTop.value, behavior: 'instant' as ScrollBehavior })
  })
})

onDeactivated(() => {
  savedScrollTop.value = window.scrollY || 0
})

const selectCategory = async (c: string) => {
  activeCategory.value = c
  currentPage.value = 0
  const cat = c === '全部' ? undefined : c
  const data = await photoStore.fetchAlbums(0, 12, cat)
  hasMore.value = !data.last
}
</script>

