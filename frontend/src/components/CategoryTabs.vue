<template>
  <!-- 分类 Tabs -->
  <div class="mb-6 relative">
    <div
      class="flex gap-2 sm:gap-2.5 overflow-x-auto pb-3 px-1 py-1 scroll-smooth category-tabs-container"
      style="scrollbar-width: none; -ms-overflow-style: none;"
    >
      <button
        v-for="c in categoriesWithAll"
        :key="c"
        @click="handleCategoryClick(c)"
        @mouseenter="handleMouseEnter($event, c)"
        @mouseleave="handleMouseLeave($event, c)"
        @mousemove="handleMouseMove($event, c)"
        class="category-tab flex-shrink-0 px-4 py-2 rounded-full border transition-all duration-500 font-medium text-sm whitespace-nowrap relative overflow-hidden"
        :class="[
          c === selectedCategory
            ? 'bg-transparent text-white dark:text-gray-900 border-gray-300/70 dark:border-gray-700/80'
            : 'bg-gray-50/70 dark:bg-gray-800/55 text-gray-700 dark:text-gray-300 border-gray-300/70 dark:border-gray-700/80 hover:bg-gray-100/90 dark:hover:bg-gray-800/80'
        ]"
        :style="getTabStyle(c)"
      >
        <!-- 液态背景层 -->
        <div 
          class="liquid-bg absolute transition-all duration-500 ease-out"
          :class="c === selectedCategory ? 'opacity-100' : 'opacity-0'"
          :style="getLiquidStyle(c)"
        ></div>
        
        <!-- 内部内容 - 带视差效果 -->
        <span 
          class="tab-text relative z-10 block"
        >
          {{ c }}
        </span>
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive } from 'vue'
import { storeToRefs } from 'pinia'
import { useThemeStore } from '@/stores/theme'

const props = defineProps<{
  selectedCategory: string
  categories: string[]
}>()

const emit = defineEmits<{
  'category-changed': [category: string]
}>()

const themeStore = useThemeStore()
const { isDark } = storeToRefs(themeStore)

// 包含"全部"的分类列表
const categoriesWithAll = computed(() => ['全部', ...props.categories])

// 鼠标交互状态 - 使用 reactive 便于直接修改
interface MouseState {
  x: number
  y: number
  percentX: number
  percentY: number
  entering: boolean
  edge: 'top' | 'bottom' | 'left' | 'right' | null
  rect: DOMRect | null
}

const mouseStates = reactive<Record<string, MouseState>>({})

// 获取或初始化鼠标状态
const getMouseState = (category: string): MouseState => {
  if (!mouseStates[category]) {
    mouseStates[category] = {
      x: 0,
      y: 0,
      percentX: 0.5,
      percentY: 0.5,
      entering: false,
      edge: null,
      rect: null
    }
  }
  return mouseStates[category]
}

// 处理鼠标进入
const handleMouseEnter = (event: MouseEvent, category: string) => {
  const rect = (event.target as HTMLElement).getBoundingClientRect()
  const x = event.clientX - rect.left
  const y = event.clientY - rect.top
  
  const state = getMouseState(category)
  state.percentX = x / rect.width
  state.percentY = y / rect.height
  state.entering = true
  state.rect = rect
  
  // 确定进入的边缘
  const minEdge = 0.25
  const maxEdge = 0.75
  
  if (state.percentX < minEdge) state.edge = 'left'
  else if (state.percentX > maxEdge) state.edge = 'right'
  else if (state.percentY < minEdge) state.edge = 'top'
  else if (state.percentY > maxEdge) state.edge = 'bottom'
  else state.edge = null
  
  state.x = x
  state.y = y
}

// 处理鼠标移动
const handleMouseMove = (event: MouseEvent, category: string) => {
  const state = getMouseState(category)
  if (!state.entering || !state.rect) return
  
  const rect = state.rect
  const x = event.clientX - rect.left
  const y = event.clientY - rect.top
  
  state.x = x
  state.y = y
  state.percentX = Math.max(0, Math.min(1, x / rect.width))
  state.percentY = Math.max(0, Math.min(1, y / rect.height))
}

// 处理鼠标离开
const handleMouseLeave = (_event: MouseEvent, category: string) => {
  const state = getMouseState(category)
  state.entering = false
  state.edge = null
}

// 获取 tab 样式
const getTabStyle = (category: string) => {
  const isSelected = category === props.selectedCategory

  return {
    transform: isSelected ? 'scale(1.015)' : 'none'
  }
}

// 获取液态背景样式
const getLiquidStyle = (category: string) => {
  const isSelected = category === props.selectedCategory
  const state = getMouseState(category)
  
  const offset = isSelected && state.entering ? (state.percentX - 0.5) * 14 : 0
  
  return {
    inset: '0',
    background: isSelected
      ? (isDark.value ? 'rgb(255 255 255)' : 'rgb(17 24 39)')
      : 'transparent',
    boxShadow: isSelected
      ? (isDark.value
        ? 'inset 0 1px 0 rgba(255,255,255,0.12)'
        : 'inset 0 1px 0 rgba(255,255,255,0.06)')
      : 'none',
    transform: `translateX(${offset}px)`,
  }
}

const handleCategoryClick = (category: string) => {
  emit('category-changed', category)
}
</script>

<style scoped>
/* 隐藏分类标签容器的滚动条 */
.category-tabs-container::-webkit-scrollbar {
  display: none;
}

/* Tab 基础样式 */
.category-tab {
  will-change: transform;
  backface-visibility: hidden;
}

/* 液态背景 */
.liquid-bg {
  border-radius: inherit;
  will-change: transform;
  z-index: 0;
}

/* 激活时的反馈 */
.category-tab:active {
  transform: scale(0.97);
}

/* 移动端适配 */
@media (max-width: 640px) {
  .category-tab {
    padding: 0.5rem 0.875rem;
    font-size: 0.8125rem;
  }
}
</style>
