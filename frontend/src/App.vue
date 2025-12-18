<template>
  <div id="app" :class="{ dark: isDark }">
    <div class="app-shell">
    <router-view v-slot="{ Component, route }">
        <Transition
          :name="getTransitionName(route)"
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
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useThemeStore } from '@/stores/theme'

const themeStore = useThemeStore()
const isDark = computed(() => themeStore.isDark)
const route = useRoute()

// 记录上一个路由，用于判断是否从 Album 返回
const previousRoute = ref<string | null>(null)

watch(() => route.name, (_newName, oldName) => {
  previousRoute.value = oldName as string | null
})

const getTransitionName = (route: any) => {
  // 如果路由有指定的 transitionName，使用它
  if (route.meta.transitionName) {
    return route.meta.transitionName
  }
  
  // 如果是从 Album 返回到 Home，禁用过渡动画
  if (route.name === 'Home' && previousRoute.value === 'Album') {
    return 'none'
  }
  
  // 默认使用 page-fade-slide
  return 'page-fade-slide'
}

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

