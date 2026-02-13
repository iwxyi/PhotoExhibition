<template>
  <!-- 分类 Tabs -->
  <div class="mb-6 relative">
    <div
      class="flex gap-2 sm:gap-3 overflow-x-auto pb-3 px-1 py-1.5 scroll-smooth category-tabs-container"
      style="scrollbar-width: none; -ms-overflow-style: none;"
    >
      <button
        v-for="c in categoriesWithAll"
        :key="c"
        @click="handleCategoryClick(c)"
        @mouseenter="handleMouseEnter($event, c)"
        @mouseleave="handleMouseLeave($event, c)"
        @mousemove="handleMouseMove($event, c)"
        class="category-tab flex-shrink-0 px-5 py-2.5 rounded-full border transition-all duration-300 font-medium text-sm whitespace-nowrap relative overflow-hidden"
        :class="[
          c === selectedCategory 
            ? 'text-white dark:text-gray-900 border-transparent' 
            : 'bg-transparent text-gray-600 dark:text-gray-300 border-gray-200/60 dark:border-gray-700/60'
        ]"
        :style="getTabStyle(c)"
      >
        <!-- 液态背景层 -->
        <div 
          class="liquid-bg absolute inset-0 transition-all duration-500 ease-out"
          :class="c === selectedCategory ? 'opacity-100' : 'opacity-0'"
          :style="getLiquidStyle(c)"
        ></div>
        
        <!-- 边缘高光层 -->
        <div 
          class="edge-glow absolute inset-0 transition-all duration-200 ease-out pointer-events-none"
          :style="getEdgeGlowStyle(c)"
        ></div>
        
        <!-- 光泽扫过效果 -->
        <div 
          class="shine-effect absolute inset-0 -translate-x-full pointer-events-none"
          :class="{ 'animate-shine': getMouseState(c)?.entering && c !== selectedCategory }"
        ></div>
        
        <!-- 内部内容 - 带视差效果 -->
        <span 
          class="tab-text relative z-10 transition-transform duration-150 ease-out block"
          :style="getTextParallax(c)"
        >
          {{ c }}
        </span>
        
        <!-- 选中时的装饰点 -->
        <span 
          v-if="c === selectedCategory"
          class="absolute top-1/2 left-2 -translate-y-1/2 w-1.5 h-1.5 rounded-full bg-white/80 dark:bg-gray-900/80 animate-pulse"
        ></span>
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive } from 'vue'

const props = defineProps<{
  selectedCategory: string
  categories: string[]
}>()

const emit = defineEmits<{
  'category-changed': [category: string]
}>()

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
  const state = getMouseState(category)
  
  let transform = ''
  
  // 磁吸效果 - 轻微朝向鼠标偏移
  if (state.entering && !isSelected) {
    const offsetX = (state.percentX - 0.5) * 3
    const offsetY = (state.percentY - 0.5) * 3
    transform = `translate(${offsetX}px, ${offsetY}px) scale(1.02)`
  }
  
  // 选中状态轻微放大
  if (isSelected) {
    transform = 'scale(1.03)'
  }
  
  return { transform }
}

// 获取液态背景样式
const getLiquidStyle = (category: string) => {
  const isSelected = category === props.selectedCategory
  const state = getMouseState(category)
  
  // 与导航栏"相册"按钮一致的颜色 - 深灰/黑色渐变
  const gradient = 'linear-gradient(135deg, #111827 0%, #374151 50%, #1f2937 100%)'
  const offset = state.entering ? (state.percentX - 0.5) * 15 : 0
  
  return {
    background: gradient,
    transform: `scale(${isSelected ? 1 : 1.02}) translateX(${offset}px)`,
  }
}

// 获取边缘高光样式
const getEdgeGlowStyle = (category: string) => {
  const state = getMouseState(category)
  const isSelected = category === props.selectedCategory
  
  if (!state.entering || isSelected) {
    return { opacity: 0 }
  }
  
  const { percentX, percentY, edge } = state
  const size = 50
  
  let positionStyle: Record<string, string> = {}
  let gradient = ''
  
  switch (edge) {
    case 'left':
      positionStyle = { left: `-${size}px`, top: '0', width: `${size}px`, height: '100%' }
      gradient = 'linear-gradient(to right, rgba(255,255,255,0.25), transparent)'
      break
    case 'right':
      positionStyle = { right: `-${size}px`, top: '0', width: `${size}px`, height: '100%' }
      gradient = 'linear-gradient(to left, rgba(255,255,255,0.25), transparent)'
      break
    case 'top':
      positionStyle = { top: `-${size}px`, left: '0', width: '100%', height: `${size}px` }
      gradient = 'linear-gradient(to bottom, rgba(255,255,255,0.25), transparent)'
      break
    case 'bottom':
      positionStyle = { bottom: `-${size}px`, left: '0', width: '100%', height: `${size}px` }
      gradient = 'linear-gradient(to top, rgba(255,255,255,0.25), transparent)'
      break
    default:
      positionStyle = { 
        left: `${percentX * 100}%`, 
        top: `${percentY * 100}%`,
        width: '100px',
        height: '100px',
        transform: 'translate(-50%, -50%)'
      }
      gradient = 'radial-gradient(circle, rgba(255,255,255,0.3) 0%, transparent 70%)'
  }
  
  return {
    ...positionStyle,
    background: gradient,
    opacity: 1,
  }
}

// 获取文字视差偏移
const getTextParallax = (category: string) => {
  const state = getMouseState(category)
  const isSelected = category === props.selectedCategory
  
  if (!state.entering || isSelected) {
    return { transform: 'translate(0, 0)' }
  }
  
  const offsetX = (0.5 - state.percentX) * 2
  const offsetY = (0.5 - state.percentY) * 2
  
  return {
    transform: `translate(${offsetX}px, ${offsetY}px)`,
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
  will-change: transform, box-shadow;
  backface-visibility: hidden;
}

/* 选中状态阴影 */
.category-tab:has(.liquid-bg[style*="opacity: 1"]) {
  box-shadow: 
    0 4px 15px rgba(17, 24, 39, 0.35),
    0 1px 3px rgba(0, 0, 0, 0.15);
}

/* 未选中时的悬浮态 */
.category-tab:not(:has(.liquid-bg[style*="opacity: 1"])):hover {
  border-color: rgba(55, 65, 81, 0.6) !important;
  box-shadow: 0 2px 8px rgba(17, 24, 39, 0.15);
}

/* 液态背景 */
.liquid-bg {
  border-radius: inherit;
}

/* 边缘高光 */
.edge-glow {
  border-radius: inherit;
}

/* 光泽效果基础 */
.shine-effect {
  border-radius: inherit;
  background: linear-gradient(90deg, transparent, rgba(255,255,255,0.25), transparent);
}

/* 光泽扫过动画 */
@keyframes shineSweep {
  0% { transform: translateX(-150%); }
  50%, 100% { transform: translateX(150%); }
}

.animate-shine {
  animation: shineSweep 0.6s ease-out;
}

/* 文字样式 */
.tab-text {
  will-change: transform;
}

/* 激活时的反馈 */
.category-tab:active {
  transform: scale(0.97);
}

/* 移动端适配 */
@media (max-width: 640px) {
  .category-tab {
    padding: 0.5rem 1rem;
    font-size: 0.8125rem;
  }
}
</style>
