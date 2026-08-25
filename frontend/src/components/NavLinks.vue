<template>
  <div class="flex items-center space-x-2.5 transition-opacity duration-300" :style="{ opacity: navOpacity }">
    <router-link
      :to="buildPublicPath('/', route.path)"
      class="inline-flex h-9 items-center justify-center w-[84px] rounded-full border transition-all duration-200 hover:scale-[1.02] transform-gpu group relative overflow-hidden whitespace-nowrap text-center"
      :class="linkClass('/')"
    >
      <div class="absolute inset-0 rounded-full transition-transform duration-300 ease-out" :class="panelClass('/')"></div>
      <span class="relative z-10 text-[13px] font-medium tracking-[0.08em] transition-transform duration-200 group-hover:scale-[1.03]">相册</span>
    </router-link>
    <router-link
      :to="buildPublicPath('/wall', route.path)"
      class="inline-flex h-9 items-center justify-center w-[84px] rounded-full border transition-all duration-200 hover:scale-[1.02] transform-gpu group relative overflow-hidden whitespace-nowrap text-center"
      :class="linkClass('/wall')"
    >
      <div class="absolute inset-0 rounded-full transition-transform duration-300 ease-out" :class="panelClass('/wall')"></div>
      <span class="relative z-10 text-[13px] font-medium tracking-[0.08em] transition-transform duration-200 group-hover:scale-[1.03]">图墙</span>
    </router-link>
    <router-link
      :to="buildPublicPath('/random', route.path)"
      class="inline-flex h-9 items-center justify-center w-[84px] rounded-full border transition-all duration-200 hover:scale-[1.02] transform-gpu group relative overflow-hidden whitespace-nowrap text-center"
      :class="linkClass('/random')"
    >
      <div class="absolute inset-0 rounded-full transition-transform duration-300 ease-out" :class="panelClass('/random')"></div>
      <span class="relative z-10 text-[13px] font-medium tracking-[0.08em] transition-transform duration-200 group-hover:scale-[1.03]">随机</span>
    </router-link>
    <router-link
      :to="buildPublicPath('/persons', route.path)"
      class="inline-flex h-9 items-center justify-center w-[84px] rounded-full border transition-all duration-200 hover:scale-[1.02] transform-gpu group relative overflow-hidden whitespace-nowrap text-center"
      :class="linkClass('/persons')"
    >
      <div class="absolute inset-0 rounded-full transition-transform duration-300 ease-out" :class="panelClass('/persons')"></div>
      <span class="relative z-10 text-[13px] font-medium tracking-[0.08em] transition-transform duration-200 group-hover:scale-[1.03]">人物</span>
    </router-link>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'
import { buildPublicPath, stripPublicSlug } from '@/utils/publicRoute'

const route = useRoute()
const navOpacity = ref(1)
let lastScrollY = 0
let ticking = false

const linkClass = (path: string) => {
  const active = stripPublicSlug(route.path) === path
  return active
    ? 'border-stone-300/80 text-stone-50 dark:border-white/14 dark:text-stone-950'
    : 'border-stone-300/70 text-stone-700 dark:border-white/10 dark:text-stone-300'
}

const panelClass = (path: string) => {
  const active = stripPublicSlug(route.path) === path
  return active
    ? 'bg-stone-900 dark:bg-stone-100 group-hover:translate-x-[4px]'
    : 'bg-white/72 dark:bg-white/[0.05] group-hover:bg-stone-100/92 dark:group-hover:bg-white/[0.08]'
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
