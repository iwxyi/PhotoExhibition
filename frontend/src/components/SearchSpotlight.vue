<template>
  <!-- 搜索按钮 -->
  <button
    @click="openSpotlight"
    class="search-btn p-2 rounded-xl border border-transparent hover:border-stone-300/70 dark:hover:border-white/10 hover:bg-white/72 dark:hover:bg-white/[0.05] transition-all duration-200 hover:scale-110 transform-gpu group relative overflow-hidden"
    title="搜索"
    @mouseenter="searchHover = true"
    @mouseleave="searchHover = false"
  >
    <div class="absolute inset-0 rounded-xl bg-white/72 opacity-0 transition-all duration-300 ease-out group-hover:opacity-100 group-hover:translate-x-[3px] dark:bg-white/[0.05]"></div>
    <svg
      class="search-svg relative z-10 w-5 h-5 transition-all duration-300 group-hover:scale-110"
      :class="{ 'is-hovering': searchHover }"
      fill="none"
      stroke="currentColor"
      viewBox="0 0 24 24"
    >
      <!-- 放大镜圆圈 -->
      <circle
        class="search-circle"
        cx="11"
        cy="11"
        r="7"
        stroke-width="1.5"
      />
      <!-- 放大镜手柄 -->
      <path
        class="search-handle"
        stroke-linecap="round"
        stroke-width="1.5"
        d="M16.5 16.5L21 21"
      />
    </svg>
  </button>

  <!-- Spotlight 弹窗 -->
  <Teleport to="body">
    <!-- 遮罩层 -->
    <Transition
      enter-active-class="transition-opacity duration-200"
      enter-from-class="opacity-0"
      enter-to-class="opacity-100"
      leave-active-class="transition-opacity duration-150"
      leave-from-class="opacity-100"
      leave-to-class="opacity-0"
    >
      <div
        v-if="showSpotlight"
        class="fixed inset-0 z-[2000] bg-black/5"
        @click="closeSpotlight"
      ></div>
    </Transition>

    <!-- 搜索面板 -->
    <Transition
      enter-active-class="transition-all duration-200 ease-out"
      enter-from-class="opacity-0 -translate-y-4 scale-95"
      enter-to-class="opacity-100 translate-y-0 scale-100"
      leave-active-class="transition-all duration-150 ease-in"
      leave-from-class="opacity-100 translate-y-0 scale-100"
      leave-to-class="opacity-0 -translate-y-4 scale-95"
    >
      <div
        v-if="showSpotlight"
        class="fixed z-[2100] top-[20%] left-1/2 -translate-x-1/2 w-[560px] max-w-[90vw]"
        @click.stop
      >
        <div
          class="relative overflow-hidden rounded-[16px] border border-slate-400/35 shadow-[0_18px_45px_rgba(15,23,42,0.18)] backdrop-blur-[20px] dark:border-slate-600/50 dark:shadow-[0_25px_60px_rgba(0,0,0,0.8)]"
          style="-webkit-backdrop-filter: blur(20px); backdrop-filter: blur(20px);"
        >
          <div class="pointer-events-none absolute inset-0 bg-[rgba(248,250,252,0.8)] dark:bg-[rgba(15,23,42,0.8)]"></div>
          <div class="relative flex items-center px-5 py-4">
            <!-- 左侧放大镜图标 -->
            <svg class="mr-3 h-5 w-5 flex-shrink-0 text-stone-400 dark:text-stone-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <circle cx="11" cy="11" r="7" stroke-width="1.5" />
              <path stroke-linecap="round" stroke-width="1.5" d="M16.5 16.5L21 21" />
            </svg>
            <!-- 搜索输入框 -->
            <input
              ref="searchInputRef"
              v-model="searchKeyword"
              type="text"
              placeholder="输入相册、人物或照片关键词"
              class="flex-1 bg-transparent text-lg text-stone-900 dark:text-stone-100 placeholder-stone-400 dark:placeholder-stone-500 outline-none"
              @keyup.enter="doSearch"
              @keyup.escape="closeSpotlight"
            />
            <!-- 右侧搜索按钮 -->
            <button
              v-if="searchKeyword.trim()"
              @click="doSearch"
              class="ml-3 flex-shrink-0 rounded-lg border border-stone-900 bg-stone-900 px-3 py-1.5 text-xs font-medium text-stone-50 transition-colors duration-150 hover:bg-stone-800 dark:border-stone-100 dark:bg-stone-100 dark:text-stone-900 dark:hover:bg-stone-200"
            >
              搜索
            </button>
            <!-- ESC 提示 -->
            <kbd
              v-else
              class="ml-3 flex-shrink-0 rounded border border-black/[0.05] bg-white/45 px-2 py-1 text-[10px] font-medium text-stone-400 dark:border-white/[0.06] dark:bg-white/[0.04] dark:text-stone-500"
            >ESC</kbd>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import { ref, nextTick, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { buildPublicPath } from '@/utils/publicRoute'

const showSpotlight = ref(false)
const searchHover = ref(false)
const searchKeyword = ref('')
const searchInputRef = ref<HTMLInputElement>()
const route = useRoute()
const router = useRouter()

const openSpotlight = () => {
  showSpotlight.value = true
  nextTick(() => {
    searchInputRef.value?.focus()
  })
}

const closeSpotlight = () => {
  searchInputRef.value?.blur()
  showSpotlight.value = false
  searchKeyword.value = ''
}

const doSearch = () => {
  if (searchKeyword.value.trim()) {
    const targetRoute = router.resolve({
      path: buildPublicPath('/search', route.path),
      query: { q: searchKeyword.value.trim() }
    })
    window.open(targetRoute.href, '_blank')
    closeSpotlight()
  }
}

const handleKeydown = (event: KeyboardEvent) => {
  // Ctrl+F (Windows/Linux) 或 Command+F (Mac) 显示搜索面板
  if ((event.ctrlKey || event.metaKey) && event.key === 'f') {
    event.preventDefault()
    if (!showSpotlight.value) {
      openSpotlight()
    }
    return
  }

  if (event.key === 'Escape' && showSpotlight.value) {
    // 如果搜索框有内容，先清空并失去焦点
    if (searchKeyword.value.trim()) {
      searchKeyword.value = ''
      searchInputRef.value?.blur()
    } else {
      // 搜索框没有内容时才关闭面板
      closeSpotlight()
    }
  }
}

onMounted(() => {
  document.addEventListener('keydown', handleKeydown)
})

onUnmounted(() => {
  document.removeEventListener('keydown', handleKeydown)
})
</script>

<style scoped>
/* 搜索按钮 SVG 动画 */
.search-svg {
  @apply text-gray-700 dark:text-gray-200;
}

.search-svg.is-hovering {
  @apply text-yellow-500;
}

.search-circle {
  transition: all 0.3s cubic-bezier(0.34, 1.56, 0.64, 1);
  transform-origin: 11px 11px;
}

.search-svg.is-hovering .search-circle {
  stroke-dasharray: 44;
  stroke-dashoffset: 44;
  animation: drawCircle 0.6s ease forwards;
}

.search-handle {
  transition: all 0.3s ease;
}

.search-svg.is-hovering .search-handle {
  stroke-dasharray: 8;
  stroke-dashoffset: 8;
  animation: drawHandle 0.3s ease forwards 0.3s;
}

@keyframes drawCircle {
  to { stroke-dashoffset: 0; }
}

@keyframes drawHandle {
  to { stroke-dashoffset: 0; }
}
</style>
