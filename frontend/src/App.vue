<template>
  <div id="app" :class="{ dark: isDark }">
    <div class="app-shell">
    <router-view v-slot="{ Component, route }">
      <KeepAlive include="Home,Wall,Random">
        <component :is="Component" :key="componentKey" />
      </KeepAlive>
    </router-view>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useThemeStore } from '@/stores/theme'
import { useLanguageStore } from '@/stores/language'
import { useAuthStore } from '@/stores/auth'
import { usePublicSiteStore } from '@/stores/publicSite'

const themeStore = useThemeStore()
const languageStore = useLanguageStore()
const authStore = useAuthStore()
const publicSiteStore = usePublicSiteStore()
const isDark = computed(() => themeStore.isDark)
const route = useRoute()

// 为 keep-alive 组件生成稳定的 key，避免路由参数变化导致组件重新创建
const componentKey = computed(() => {
  const name = route.name as string
  const userSlug = typeof route.params.userSlug === 'string' ? route.params.userSlug : ''
  // 对于需要缓存的页面（Home, Wall, Random），使用路由名称作为 key
  // 但多用户场景下必须把 userSlug 纳入 key，避免不同站点之间复用同一缓存实例
  if (['Home', 'Wall', 'Random'].includes(name)) {
    return `${name}:${userSlug || 'default'}`
  }
  // 其他页面使用完整路径，确保参数变化时组件更新
  return route.fullPath
})

// 根据语言设置页面标题
const baseTitle = computed(() => {
  const publicTitle = publicSiteStore.projectNameZh || publicSiteStore.projectNameEn
  if (publicTitle) {
    return publicTitle
  }
  if (languageStore.language === 'zh') {
    return authStore.projectNameZh || authStore.projectNameEn || '光忆集'
  }
  return authStore.projectNameEn || authStore.projectNameZh || 'Aurellic Memoriq'
})

// 根据路由更新页面标题
const updateTitle = () => {
  const routeTitle = route.meta?.title
  if (routeTitle) {
    document.title = `${baseTitle.value} - ${routeTitle}`
  } else {
    document.title = baseTitle.value
  }
}

// 监听路由变化
watch(() => route.fullPath, updateTitle, { immediate: true })
// 监听语言变化
watch(() => languageStore.language, updateTitle)
watch(() => authStore.projectNameZh, updateTitle)
watch(() => authStore.projectNameEn, updateTitle)
watch(() => publicSiteStore.projectNameZh, updateTitle)
watch(() => publicSiteStore.projectNameEn, updateTitle)

watch(
  () => [route.params.userSlug, route.path],
  async ([userSlug, path]) => {
    const normalizedPath = String(path)
    const isPublicRoute =
      !normalizedPath.startsWith('/admin') &&
      normalizedPath !== '/login' &&
      normalizedPath !== '/register' &&
      !normalizedPath.startsWith('/profile') &&
      !normalizedPath.startsWith('/vip')
    if (isPublicRoute && typeof userSlug === 'string' && userSlug) {
      await publicSiteStore.fetchBySlug(userSlug)
    } else {
      publicSiteStore.reset()
    }
  },
  { immediate: true }
)

// 初始化标题
onMounted(() => {
  updateTitle()
  authStore.bootstrap()
  // 初次挂载时，根据当前路由给 body 一个过渡友好的背景状态
  document.body.dataset.route = (route.name as string) || ''
})
</script>

<style>
#app {
  min-height: 100vh;
}

.app-shell {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}
</style>
