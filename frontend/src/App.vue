<template>
  <div id="app" :class="{ dark: isDark }">
    <div class="app-shell">
      <AdminStyleChrome v-if="isAdminRoute" />
      <router-view v-slot="{ Component, route }">
        <KeepAlive include="Home,Wall,Random">
          <component :is="Component" :key="componentKey" />
        </KeepAlive>
      </router-view>
      <TransitionGroup
        name="sync-notice"
        tag="div"
        class="sync-notice-stack"
      >
        <div
          v-for="notice in notices"
          :key="notice.id"
          class="sync-notice-card"
          :data-kind="notice.kind"
        >
          <div class="sync-notice-dot" />
          <div class="sync-notice-copy">
            <strong>{{ notice.title }}</strong>
            <p>{{ notice.message }}</p>
          </div>
        </div>
      </TransitionGroup>
      <AdminFeedbackLayer />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useThemeStore } from '@/stores/theme'
import { useLanguageStore } from '@/stores/language'
import { useAuthStore } from '@/stores/auth'
import { usePublicSiteStore } from '@/stores/publicSite'
import { useSyncNotice } from '@/composables/useSyncNotice'
import AdminFeedbackLayer from '@/components/admin/AdminFeedbackLayer.vue'
import AdminStyleChrome from '@/components/admin/AdminStyleChrome.vue'

const themeStore = useThemeStore()
const languageStore = useLanguageStore()
const authStore = useAuthStore()
const publicSiteStore = usePublicSiteStore()
const { notices } = useSyncNotice()
const isDark = computed(() => themeStore.isDark)
const route = useRoute()
const isAdminRoute = computed(() => route.path.startsWith('/admin') && route.path !== '/admin/login')

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

.sync-notice-stack {
  position: fixed;
  top: 1.25rem;
  right: 1.25rem;
  z-index: 120;
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  pointer-events: none;
}

.sync-notice-card {
  display: grid;
  grid-template-columns: auto 1fr;
  align-items: start;
  gap: 0.75rem;
  min-width: min(24rem, calc(100vw - 2rem));
  max-width: min(28rem, calc(100vw - 2rem));
  padding: 0.9rem 1rem;
  border-radius: 20px;
  border: 1px solid color-mix(in srgb, var(--pe-theme-secondary, #60a5fa) 24%, rgba(255, 255, 255, 0.18));
  background:
    linear-gradient(180deg, rgba(var(--pe-surface-bg-rgb, 15, 23, 42), 0.86), rgba(var(--pe-surface-bg-rgb, 15, 23, 42), 0.78));
  box-shadow: 0 24px 64px rgba(15, 23, 42, 0.24);
  backdrop-filter: blur(22px) saturate(145%);
  -webkit-backdrop-filter: blur(22px) saturate(145%);
  color: var(--pe-admin-text-primary, rgba(248, 250, 252, 0.96));
}

.sync-notice-card[data-kind='theme'] .sync-notice-dot {
  background: linear-gradient(135deg, var(--pe-theme-primary, #3b82f6), var(--pe-theme-secondary, #60a5fa));
}

.sync-notice-card[data-kind='ui-settings'] .sync-notice-dot {
  background: linear-gradient(135deg, #10b981, #34d399);
}

.sync-notice-dot {
  width: 0.78rem;
  height: 0.78rem;
  margin-top: 0.24rem;
  border-radius: 999px;
  box-shadow: 0 0 0 6px color-mix(in srgb, currentColor 10%, transparent);
}

.sync-notice-copy strong {
  display: block;
  font-size: 0.92rem;
  line-height: 1.35;
}

.sync-notice-copy p {
  margin: 0.18rem 0 0;
  font-size: 0.78rem;
  line-height: 1.45;
  color: var(--pe-admin-text-muted, rgba(148, 163, 184, 0.92));
}

.sync-notice-enter-active,
.sync-notice-leave-active {
  transition: opacity 220ms ease, transform 220ms ease;
}

.sync-notice-enter-from,
.sync-notice-leave-to {
  opacity: 0;
  transform: translateY(-8px) scale(0.98);
}

.sync-notice-move {
  transition: transform 220ms ease;
}

@media (max-width: 640px) {
  .sync-notice-stack {
    top: auto;
    right: 0.9rem;
    bottom: 1rem;
    left: 0.9rem;
  }

  .sync-notice-card {
    min-width: 0;
    max-width: none;
  }
}
</style>
