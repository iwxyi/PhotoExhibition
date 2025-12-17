<template>
  <div id="app" :class="{ dark: isDark }">
    <div class="app-shell">
    <router-view v-slot="{ Component, route }">
        <Transition
          :name="route.meta.transitionName || 'page-fade-slide'"
          mode="out-in"
        >
      <KeepAlive include="Home,Wall,Random">
        <component :is="Component" :key="route.fullPath" />
      </KeepAlive>
        </Transition>
    </router-view>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { useThemeStore } from '@/stores/theme'

const themeStore = useThemeStore()
const isDark = computed(() => themeStore.isDark)
const route = useRoute()

onMounted(() => {
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

