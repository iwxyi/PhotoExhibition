<template>
  <div class="flex items-center space-x-2 transition-opacity duration-300" :style="{ opacity: navOpacity }">
    <router-link
      to="/"
      class="inline-flex items-center justify-center w-[86px] text-sm font-medium px-3 py-2 rounded border border-gray-400/50 dark:border-gray-600 transition-all duration-200 hover:scale-105 hover:shadow-sm transform-gpu group relative overflow-hidden whitespace-nowrap text-center"
      :class="linkClass('/')"
    >
      <span class="relative z-10 transition-transform duration-200 group-hover:scale-105">相册</span>
      <div class="absolute inset-0 bg-gradient-to-r from-blue-500/10 to-purple-500/10 opacity-0 group-hover:opacity-100 transition-opacity duration-200 rounded"></div>
    </router-link>
    <router-link
      to="/wall"
      class="inline-flex items-center justify-center w-[86px] text-sm font-medium px-3 py-2 rounded border border-gray-400/50 dark:border-gray-600 transition-all duration-200 hover:scale-105 hover:shadow-sm transform-gpu group relative overflow-hidden whitespace-nowrap text-center"
      :class="linkClass('/wall')"
    >
      <span class="relative z-10 transition-transform duration-200 group-hover:scale-105">图墙</span>
      <div class="absolute inset-0 bg-gradient-to-r from-green-500/10 to-teal-500/10 opacity-0 group-hover:opacity-100 transition-opacity duration-200 rounded"></div>
    </router-link>
    <router-link
      to="/random"
      class="inline-flex items-center justify-center w-[86px] text-sm font-medium px-3 py-2 rounded border border-gray-400/50 dark:border-gray-600 transition-all duration-200 hover:scale-105 hover:shadow-sm transform-gpu group relative overflow-hidden whitespace-nowrap text-center"
      :class="linkClass('/random')"
    >
      <span class="relative z-10 transition-transform duration-200 group-hover:scale-105">随机</span>
      <div class="absolute inset-0 bg-gradient-to-r from-orange-500/10 to-pink-500/10 opacity-0 group-hover:opacity-100 transition-opacity duration-200 rounded"></div>
    </router-link>
    <router-link
      to="/persons"
      class="inline-flex items-center justify-center w-[86px] text-sm font-medium px-3 py-2 rounded border border-gray-400/50 dark:border-gray-600 transition-all duration-200 hover:scale-105 hover:shadow-sm transform-gpu group relative overflow-hidden whitespace-nowrap text-center"
      :class="linkClass('/persons')"
    >
      <span class="relative z-10 transition-transform duration-200 group-hover:scale-105">人物</span>
      <div class="absolute inset-0 bg-gradient-to-r from-purple-500/10 to-indigo-500/10 opacity-0 group-hover:opacity-100 transition-opacity duration-200 rounded"></div>
    </router-link>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const navOpacity = ref(1)
let lastScrollY = 0
let ticking = false

const linkClass = (path: string) => {
  const active = route.path === path
  return active
    ? 'bg-gray-900 text-white dark:bg-white dark:text-gray-900'
    : 'text-gray-700 dark:text-gray-300 bg-gray-100/60 dark:bg-gray-800/60 hover:bg-gray-200 dark:hover:bg-gray-700'
}

const updateNavOpacity = () => {
  const currentScrollY = window.scrollY
  const scrollThreshold = 100 // 开始淡化的滚动距离
  const fadeDistance = 200 // 完全淡化的距离

  if (currentScrollY < scrollThreshold) {
    // 在顶部时完全显示
    navOpacity.value = 1
  } else if (currentScrollY < scrollThreshold + fadeDistance) {
    // 在淡化范围内，根据滚动距离计算透明度
    const progress = (currentScrollY - scrollThreshold) / fadeDistance
    navOpacity.value = Math.max(0.3, 1 - progress) // 最小透明度为0.3
  } else {
    // 滚动距离足够大时，保持最小透明度
    navOpacity.value = 0.3
  }

  lastScrollY = currentScrollY
  ticking = false
}

const handleScroll = () => {
  if (!ticking) {
    requestAnimationFrame(updateNavOpacity)
    ticking = true
  }
}

const handleScrollToTop = () => {
  const currentScrollY = window.scrollY
  if (currentScrollY < 50) {
    // 回到顶部时恢复完全显示
    navOpacity.value = 1
  }
}

onMounted(() => {
  window.addEventListener('scroll', handleScroll, { passive: true })
  window.addEventListener('scroll', handleScrollToTop, { passive: true })
  // 初始化时检查一次
  updateNavOpacity()
})

onUnmounted(() => {
  window.removeEventListener('scroll', handleScroll)
  window.removeEventListener('scroll', handleScrollToTop)
})
</script>

