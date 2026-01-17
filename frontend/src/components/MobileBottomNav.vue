<template>
  <nav
    class="fixed bottom-0 left-0 right-0 z-50 bg-white/80 dark:bg-gray-900/80 backdrop-blur-md border-t border-gray-200 dark:border-gray-800 transition-transform duration-300 ease-in-out transform-gpu safe-area-inset-bottom"
    :class="{ 'translate-y-full': isHidden }"
    style="padding-bottom: env(safe-area-inset-bottom);"
  >
    <div class="flex items-center justify-around py-0.5 px-4">
      <router-link
        to="/"
        class="flex flex-col items-center justify-center p-1.5 rounded-lg transition-all duration-200 hover:scale-105 transform-gpu group relative overflow-hidden min-w-[50px]"
        :class="linkClass('/')"
      >
        <svg class="w-5 h-5 transition-all duration-200 group-hover:scale-110" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 11H5m14 0a2 2 0 012 2v6a2 2 0 01-2 2H5a2 2 0 01-2-2v-6a2 2 0 012-2m14 0V9a2 2 0 00-2-2M5 11V9a2 2 0 012-2m0 0V5a2 2 0 012-2h6a2 2 0 012 2v2M7 7h10" />
        </svg>
        <span class="text-[10px] font-medium mt-0 transition-all duration-200 group-hover:scale-105">相册</span>
        <div class="absolute inset-0 bg-gradient-to-r from-blue-500/10 to-purple-500/10 opacity-0 group-hover:opacity-100 transition-opacity duration-200 rounded-lg"></div>
      </router-link>

      <router-link
        to="/wall"
        class="flex flex-col items-center justify-center p-1.5 rounded-lg transition-all duration-200 hover:scale-105 transform-gpu group relative overflow-hidden min-w-[50px]"
        :class="linkClass('/wall')"
      >
        <svg class="w-5 h-5 transition-all duration-200 group-hover:scale-110" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 6a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2H6a2 2 0 01-2-2V6zM14 6a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2h-2a2 2 0 01-2-2V6zM4 16a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2H6a2 2 0 01-2-2v-2zM14 16a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2h-2a2 2 0 01-2-2v-2z" />
        </svg>
        <span class="text-[10px] font-medium mt-0 transition-all duration-200 group-hover:scale-105">图墙</span>
        <div class="absolute inset-0 bg-gradient-to-r from-green-500/10 to-teal-500/10 opacity-0 group-hover:opacity-100 transition-opacity duration-200 rounded-lg"></div>
      </router-link>

      <router-link
        to="/random"
        class="flex flex-col items-center justify-center p-1.5 rounded-lg transition-all duration-200 hover:scale-105 transform-gpu group relative overflow-hidden min-w-[50px]"
        :class="linkClass('/random')"
      >
        <svg class="w-5 h-5 transition-all duration-200 group-hover:scale-110" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4.318 6.318a4.5 4.5 0 000 6.364L12 20.364l7.682-7.682a4.5 4.5 0 00-6.364-6.364L12 7.636l-1.318-1.318a4.5 4.5 0 00-6.364 0z" />
        </svg>
        <span class="text-[10px] font-medium mt-0 transition-all duration-200 group-hover:scale-105">随机</span>
        <div class="absolute inset-0 bg-gradient-to-r from-orange-500/10 to-pink-500/10 opacity-0 group-hover:opacity-100 transition-opacity duration-200 rounded-lg"></div>
      </router-link>

      <router-link
        to="/persons"
        class="flex flex-col items-center justify-center p-1.5 rounded-lg transition-all duration-200 hover:scale-105 transform-gpu group relative overflow-hidden min-w-[50px]"
        :class="linkClass('/persons')"
      >
        <svg class="w-5 h-5 transition-all duration-200 group-hover:scale-110" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z" />
        </svg>
        <span class="text-[10px] font-medium mt-0 transition-all duration-200 group-hover:scale-105">人物</span>
        <div class="absolute inset-0 bg-gradient-to-r from-purple-500/10 to-indigo-500/10 opacity-0 group-hover:opacity-100 transition-opacity duration-200 rounded-lg"></div>
      </router-link>
    </div>
  </nav>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const isHidden = ref(false)
let lastScrollY = 0
let ticking = false
let hideTimeout: number | null = null
let navigationProtectionTimeout: number | null = null
let isNavigationProtected = false

const linkClass = (path: string) => {
  const active = route.path === path
  return active
    ? 'bg-gray-900 text-white dark:bg-white dark:text-gray-900 shadow-lg'
    : 'text-gray-700 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-800'
}

const updateNavVisibility = () => {
  const currentScrollY = window.scrollY
  const scrollThreshold = 50 // 开始隐藏的滚动阈值
  const scrollDelta = currentScrollY - lastScrollY

  // 清除之前的隐藏定时器
  if (hideTimeout) {
    clearTimeout(hideTimeout)
    hideTimeout = null
  }

  // 如果在导航保护期内，保持显示状态
  if (isNavigationProtected) {
    lastScrollY = currentScrollY
    ticking = false
    return
  }

  // 如果在顶部附近，始终显示
  if (currentScrollY < scrollThreshold) {
    isHidden.value = false
    lastScrollY = currentScrollY
    ticking = false
    return
  }

  // 根据滚动方向决定显示/隐藏
  if (scrollDelta > 0) {
    // 向上滚动（内容向上移动）- 隐藏导航栏
    isHidden.value = true
  } else if (scrollDelta < -10) {
    // 向下滚动（内容向下移动）- 显示导航栏
    isHidden.value = false
  }

  lastScrollY = currentScrollY
  ticking = false
}

const handleScroll = () => {
  if (!ticking) {
    requestAnimationFrame(updateNavVisibility)
    ticking = true
  }
}

const handleTouchStart = () => {
  // 触摸开始时临时显示导航栏
  if (isHidden.value) {
    isHidden.value = false
    // 3秒后自动隐藏
    hideTimeout = window.setTimeout(() => {
      isHidden.value = true
      hideTimeout = null
    }, 3000)
  }
}

const startNavigationProtection = () => {
  // 开始导航保护期
  isNavigationProtected = true
  isHidden.value = false // 确保导航栏显示

  // 清除之前的保护定时器
  if (navigationProtectionTimeout) {
    clearTimeout(navigationProtectionTimeout)
  }

  // 2秒后结束导航保护期
  navigationProtectionTimeout = window.setTimeout(() => {
    isNavigationProtected = false
    navigationProtectionTimeout = null
  }, 2000)
}

onMounted(() => {
  window.addEventListener('scroll', handleScroll, { passive: true })
  // 监听触摸事件，用于临时显示导航栏
  window.addEventListener('touchstart', handleTouchStart, { passive: true })

  // 页面加载时开始导航保护
  startNavigationProtection()
})

// 监听路由变化，开始导航保护
watch(
  () => route.path,
  () => {
    startNavigationProtection()
  }
)

onUnmounted(() => {
  window.removeEventListener('scroll', handleScroll)
  window.removeEventListener('touchstart', handleTouchStart)
  if (hideTimeout) {
    clearTimeout(hideTimeout)
  }
  if (navigationProtectionTimeout) {
    clearTimeout(navigationProtectionTimeout)
  }
})
</script>
