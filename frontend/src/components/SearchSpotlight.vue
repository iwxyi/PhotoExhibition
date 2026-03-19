<template>
  <!-- 搜索按钮 -->
  <button
    @click="openSpotlight"
    class="search-btn p-2 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-800 transition-all duration-200 hover:scale-110 hover:shadow-md transform-gpu group relative overflow-hidden"
    title="搜索"
    @mouseenter="searchHover = true"
    @mouseleave="searchHover = false"
  >
    <svg
      class="search-svg w-5 h-5 transition-all duration-300 group-hover:scale-110"
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
    <div class="absolute inset-0 bg-gradient-to-r from-cyan-500/10 to-blue-500/10 opacity-0 group-hover:opacity-100 transition-opacity duration-200 rounded-lg pointer-events-none"></div>
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
        class="fixed inset-0 z-[2000] bg-black/30 backdrop-blur-sm"
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
        <div class="bg-white/90 dark:bg-gray-800/90 backdrop-blur-xl rounded-2xl shadow-2xl border border-gray-200/50 dark:border-gray-700/50 overflow-hidden">
          <div class="flex items-center px-5 py-4">
            <!-- 左侧放大镜图标 -->
            <svg class="w-5 h-5 text-gray-400 dark:text-gray-500 flex-shrink-0 mr-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <circle cx="11" cy="11" r="7" stroke-width="1.5" />
              <path stroke-linecap="round" stroke-width="1.5" d="M16.5 16.5L21 21" />
            </svg>
            <!-- 搜索输入框 -->
            <input
              ref="searchInputRef"
              v-model="searchKeyword"
              type="text"
              placeholder="搜索相册、人物、照片..."
              class="flex-1 bg-transparent text-lg text-gray-900 dark:text-white placeholder-gray-400 dark:placeholder-gray-500 outline-none"
              @keyup.enter="doSearch"
              @keyup.escape="closeSpotlight"
            />
            <!-- 右侧搜索按钮 -->
            <button
              v-if="searchKeyword.trim()"
              @click="doSearch"
              class="flex-shrink-0 ml-3 px-3 py-1.5 text-xs font-medium text-white bg-blue-500 hover:bg-blue-600 rounded-lg transition-colors duration-150"
            >
              搜索
            </button>
            <!-- ESC 提示 -->
            <kbd
              v-else
              class="flex-shrink-0 ml-3 px-2 py-1 text-[10px] font-medium text-gray-400 dark:text-gray-500 bg-gray-100 dark:bg-gray-700 rounded border border-gray-200 dark:border-gray-600"
            >ESC</kbd>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import { ref, nextTick, onMounted, onUnmounted } from 'vue'

const showSpotlight = ref(false)
const searchHover = ref(false)
const searchKeyword = ref('')
const searchInputRef = ref<HTMLInputElement>()

const openSpotlight = () => {
  showSpotlight.value = true
  nextTick(() => {
    searchInputRef.value?.focus()
  })
}

const closeSpotlight = () => {
  showSpotlight.value = false
  searchKeyword.value = ''
}

const doSearch = () => {
  if (searchKeyword.value.trim()) {
    const searchUrl = `/search?q=${encodeURIComponent(searchKeyword.value.trim())}`
    window.open(searchUrl, '_blank')
    closeSpotlight()
  }
}

const handleKeydown = (event: KeyboardEvent) => {
  if (event.key === 'Escape' && showSpotlight.value) {
    closeSpotlight()
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
