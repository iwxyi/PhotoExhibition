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
          <div class="flex items-center space-x-8">
            <router-link to="/" class="text-xl font-light tracking-wider text-gray-900 dark:text-white">
              摄影展
            </router-link>
            <NavLinks v-if="!isMobile" />
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
        class="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5 gap-6"
      >
        <PersonCard
          v-for="person in persons"
          :key="person.id"
          :person="person"
          @click="goToPerson(person.id)"
        />
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
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useThemeStore } from '@/stores/theme'
import { personApi, PersonSummary } from '@/api'
import NavLinks from '@/components/NavLinks.vue'
import MobileBottomNav from '@/components/MobileBottomNav.vue'
import SettingsMenu from '@/components/SettingsMenu.vue'
import PersonCard from '@/components/PersonCard.vue'
import { useMobileNav } from '@/composables/useMobileNav'
import { useNavAutoHide } from '@/composables/useNavAutoHide'

const router = useRouter()
const themeStore = useThemeStore()
const { isMobile } = useMobileNav()
const { isHidden: navHidden } = useNavAutoHide()

const persons = ref<PersonSummary[]>([])
const loading = ref(false)

const goToPerson = (id: number) => {
  window.open(`/person/${id}`, '_blank')
}

const loadPersons = async () => {
  loading.value = true
  try {
    const response = await personApi.getPersonsWithSample()
    persons.value = response.data
  } catch (error) {
    console.error('加载人物列表失败:', error)
    persons.value = []
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadPersons()
})
</script>
