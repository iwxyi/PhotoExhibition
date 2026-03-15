<template>
  <div id="app" :class="{ dark: isDark }">
    <div class="app-shell">
    <router-view v-slot="{ Component, route }">
      <KeepAlive include="Home,Wall,Random">
        <component :is="Component" :key="route.fullPath" />
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

