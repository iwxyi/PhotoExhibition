<template>
  <div class="flex items-center space-x-2.5 transition-opacity duration-300" :style="{ opacity: navOpacity }">
    <router-link
      :to="buildPublicPath('/', route.path)"
      @mouseenter="handleMouseEnter($event, '/')"
      @mousemove="handleMouseMove($event, '/')"
      @mouseleave="handleMouseLeave('/')"
      class="inline-flex min-h-[34px] items-center justify-center w-[84px] rounded-full border transition-all duration-500 hover:scale-[1.015] transform-gpu group relative overflow-hidden whitespace-nowrap text-center focus:outline-none focus-visible:ring-2 focus-visible:ring-stone-400 focus-visible:ring-offset-2 dark:focus-visible:ring-stone-500 dark:focus-visible:ring-offset-gray-900"
      :class="linkClass('/')"
    >
      <div class="absolute inset-0 rounded-full transition-all duration-500 ease-out" :class="panelClass('/')" :style="getPanelStyle('/')"></div>
      <span class="relative z-10 text-[13px] font-medium tracking-[0.08em] transition-transform duration-200 group-hover:scale-[1.03]">相册</span>
    </router-link>
    <router-link
      :to="buildPublicPath('/wall', route.path)"
      @mouseenter="handleMouseEnter($event, '/wall')"
      @mousemove="handleMouseMove($event, '/wall')"
      @mouseleave="handleMouseLeave('/wall')"
      class="inline-flex min-h-[34px] items-center justify-center w-[84px] rounded-full border transition-all duration-500 hover:scale-[1.015] transform-gpu group relative overflow-hidden whitespace-nowrap text-center focus:outline-none focus-visible:ring-2 focus-visible:ring-stone-400 focus-visible:ring-offset-2 dark:focus-visible:ring-stone-500 dark:focus-visible:ring-offset-gray-900"
      :class="linkClass('/wall')"
    >
      <div class="absolute inset-0 rounded-full transition-all duration-500 ease-out" :class="panelClass('/wall')" :style="getPanelStyle('/wall')"></div>
      <span class="relative z-10 text-[13px] font-medium tracking-[0.08em] transition-transform duration-200 group-hover:scale-[1.03]">图墙</span>
    </router-link>
    <router-link
      :to="buildPublicPath('/random', route.path)"
      @mouseenter="handleMouseEnter($event, '/random')"
      @mousemove="handleMouseMove($event, '/random')"
      @mouseleave="handleMouseLeave('/random')"
      class="inline-flex min-h-[34px] items-center justify-center w-[84px] rounded-full border transition-all duration-500 hover:scale-[1.015] transform-gpu group relative overflow-hidden whitespace-nowrap text-center focus:outline-none focus-visible:ring-2 focus-visible:ring-stone-400 focus-visible:ring-offset-2 dark:focus-visible:ring-stone-500 dark:focus-visible:ring-offset-gray-900"
      :class="linkClass('/random')"
    >
      <div class="absolute inset-0 rounded-full transition-all duration-500 ease-out" :class="panelClass('/random')" :style="getPanelStyle('/random')"></div>
      <span class="relative z-10 text-[13px] font-medium tracking-[0.08em] transition-transform duration-200 group-hover:scale-[1.03]">发现</span>
    </router-link>
    <router-link
      :to="buildPublicPath('/persons', route.path)"
      @mouseenter="handleMouseEnter($event, '/persons')"
      @mousemove="handleMouseMove($event, '/persons')"
      @mouseleave="handleMouseLeave('/persons')"
      class="inline-flex min-h-[34px] items-center justify-center w-[84px] rounded-full border transition-all duration-500 hover:scale-[1.015] transform-gpu group relative overflow-hidden whitespace-nowrap text-center focus:outline-none focus-visible:ring-2 focus-visible:ring-stone-400 focus-visible:ring-offset-2 dark:focus-visible:ring-stone-500 dark:focus-visible:ring-offset-gray-900"
      :class="linkClass('/persons')"
    >
      <div class="absolute inset-0 rounded-full transition-all duration-500 ease-out" :class="panelClass('/persons')" :style="getPanelStyle('/persons')"></div>
      <span class="relative z-10 text-[13px] font-medium tracking-[0.08em] transition-transform duration-200 group-hover:scale-[1.03]">人物</span>
    </router-link>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, onMounted, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'
import { buildPublicPath, stripPublicSlug } from '@/utils/publicRoute'

const route = useRoute()
const navOpacity = ref(1)
let lastScrollY = 0
let ticking = false

interface NavMouseState {
  percentX: number
  hovering: boolean
  rect: DOMRect | null
}

const navMouseStates = reactive<Record<string, NavMouseState>>({})

const getMouseState = (path: string): NavMouseState => {
  if (!navMouseStates[path]) {
    navMouseStates[path] = { percentX: 0.5, hovering: false, rect: null }
  }
  return navMouseStates[path]
}

const updateMousePosition = (event: MouseEvent, path: string) => {
  const state = getMouseState(path)
  const rect = state.rect || (event.currentTarget as HTMLElement | null)?.getBoundingClientRect()
  if (!rect) return
  state.rect = rect
  state.percentX = Math.max(0, Math.min(1, (event.clientX - rect.left) / rect.width))
}

const handleMouseEnter = (event: MouseEvent, path: string) => {
  const state = getMouseState(path)
  state.rect = (event.currentTarget as HTMLElement | null)?.getBoundingClientRect() || null
  state.hovering = true
  updateMousePosition(event, path)
}

const handleMouseMove = (event: MouseEvent, path: string) => {
  updateMousePosition(event, path)
}

const handleMouseLeave = (path: string) => {
  const state = getMouseState(path)
  state.hovering = false
  state.rect = null
}

const linkClass = (path: string) => {
  const active = stripPublicSlug(route.path) === path
  return active
    ? 'border-stone-300/80 text-stone-50 dark:border-white/14 dark:text-stone-950'
    : 'bg-white/72 dark:bg-white/[0.05] border-stone-300/70 text-stone-700 dark:border-white/10 dark:text-stone-300'
}

const panelClass = (path: string) => {
  const active = stripPublicSlug(route.path) === path
  return active
    ? 'bg-stone-900 dark:bg-stone-100'
    : 'bg-stone-100/92 dark:bg-white/[0.08] opacity-0 group-hover:opacity-100'
}

const getPanelStyle = (path: string) => {
  const state = getMouseState(path)
  const offset = state.hovering ? (state.percentX - 0.5) * 18 : 0
  return { transform: `translateX(${offset}px)` }
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
