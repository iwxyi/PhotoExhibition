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

const themeStore = useThemeStore()
const languageStore = useLanguageStore()
const isDark = computed(() => themeStore.isDark)
const route = useRoute()

// 为 keep-alive 组件生成稳定的 key，避免路由参数变化导致组件重新创建
const componentKey = computed(() => {
  const name = route.name as string
  // 对于需要缓存的页面（Home, Wall, Random），使用路由名称作为 key
  // 这样可以保持组件状态，避免返回时重新刷新
  if (['Home', 'Wall', 'Random'].includes(name)) {
    return name
  }
  // 其他页面使用完整路径，确保参数变化时组件更新
  return route.fullPath
})

// 根据语言设置页面标题
const baseTitle = computed(() => {
  return languageStore.language === 'zh' ? '光忆集' : 'Aurellic Memoriq'
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
</style>

