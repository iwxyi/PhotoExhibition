<template>
  <div class="flex items-center space-x-3 flex-1 min-w-0">
    <!-- 拍摄图标 - 可交互线条动画 -->
    <div
      class="p-2 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-800 transition-all duration-200 hover:scale-110 hover:shadow-md transform-gpu group relative overflow-hidden cursor-pointer"
      @click="goToHome"
      @mouseenter="handleCameraHover"
      @mouseleave="handleCameraLeave"
      @mousedown="handleCameraClick"
      @mouseup="handleCameraClickEnd"
    >
      <svg
        class="camera-svg transition-all duration-300 group-hover:rotate-12 group-hover:scale-110"
        :class="{ 'is-hovering': cameraHover, 'is-clicking': cameraClicking }"
        width="24"
        height="24"
        viewBox="0 0 24 24"
        fill="none"
      >
        <!-- 相机主体边框 -->
        <rect
          class="camera-body"
          x="2"
          y="4"
          width="20"
          height="16"
          rx="2"
          stroke="currentColor"
          stroke-width="1.5"
        />
        <!-- 顶部快门按钮 -->
        <path
          class="shutter-btn"
          d="M16 4V2M16 2L14 2L14 4M16 2L18 2L18 4"
          stroke="currentColor"
          stroke-width="1.5"
          stroke-linecap="round"
          stroke-linejoin="round"
        />
        <!-- 镜头外圈 -->
        <circle
          class="lens-outer"
          cx="12"
          cy="12"
          r="5"
          stroke="currentColor"
          stroke-width="1.5"
        />
        <!-- 镜头内圈 -->
        <circle
          class="lens-inner"
          cx="12"
          cy="12"
          r="3"
          stroke="currentColor"
          stroke-width="1"
        />
        <!-- 取景器小窗 -->
        <rect
          class="viewfinder"
          x="4"
          y="6"
          width="4"
          height="3"
          rx="0.5"
          stroke="currentColor"
          stroke-width="1"
        />
        <!-- 闪光灯 -->
        <circle
          class="flash"
          cx="17"
          cy="7"
          r="1"
          fill="currentColor"
        />
      </svg>
      <!-- 渐变遮罩层 -->
      <div class="absolute inset-0 bg-gradient-to-r from-cyan-500/10 to-blue-500/10 opacity-0 group-hover:opacity-100 transition-opacity duration-200 rounded-lg pointer-events-none"></div>
    </div>

    <!-- 标题文字区域 -->
    <div
      class="relative flex items-center h-12 cursor-pointer"
      @mouseenter="handleMouseEnter"
      @mouseleave="handleMouseLeave"
      @click.stop="goToHome"
    >
      <!-- 默认显示：紧凑的中文 -->
      <span
        ref="shortTextRef"
        class="text-xl font-light tracking-wider text-gray-900 dark:text-white whitespace-nowrap transition-all duration-300 max-w-[240px] truncate"
        :class="{
          'text-2xl': isDetailPage,
          'opacity-0': enableAnimatedBrand && isExpanded,
          'opacity-100': !enableAnimatedBrand || !isExpanded
        }"
      >
        {{ shortTitle }}
      </span>

      <!-- 悬浮显示：两行英文 - 带逐字母弹入动画 -->
      <div
        :key="animationKey"
        v-show="enableAnimatedBrand && isExpanded"
        class="absolute left-0 top-1/2 -translate-y-1/2 whitespace-nowrap overflow-hidden"
        :style="{ width: expandedWidth + 'px' }"
      >
        <!-- Aurellic - 从上方弹入 -->
        <span ref="expandedTextRef" class="block text-sm font-light tracking-wider text-gray-900 dark:text-white leading-tight">
          <span
            v-for="(char, index) in 'Aurellic'.split('')"
            :key="'a-' + index"
            class="char-animation-from-top"
            :style="{ animationDelay: (index * 0.05) + 's' }"
          >{{ char }}</span>
        </span>
        <!-- Memoriq - 从下方弹入 -->
        <span class="block text-sm font-light tracking-wider text-gray-900 dark:text-white leading-tight">
          <span
            v-for="(char, index) in 'Memoriq'.split('')"
            :key="'m-' + index"
            class="char-animation-from-bottom"
            :style="{ animationDelay: (0.4 + index * 0.05) + 's' }"
          >{{ char }}</span>
        </span>
      </div>
    </div>

    <!-- 导航链接 -->
    <NavLinks v-if="showNavLinks" />

    <!-- 右侧额外内容插槽 -->
    <slot name="right"></slot>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import NavLinks from './NavLinks.vue'
import { buildPublicPath } from '@/utils/publicRoute'
import { useAuthStore } from '@/stores/auth'
import { useLanguageStore } from '@/stores/language'
import { usePublicSiteStore } from '@/stores/publicSite'

defineProps<{
  showNavLinks?: boolean
  isDetailPage?: boolean
}>()

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const languageStore = useLanguageStore()
const publicSiteStore = usePublicSiteStore()

const isExpanded = ref(false)
const expandedWidth = ref(0)
const animationKey = ref(0)
const shortTextRef = ref<HTMLElement | null>(null)
const expandedTextRef = ref<HTMLElement | null>(null)
let hoverTimeout: ReturnType<typeof setTimeout> | null = null
let leaveTimeout: ReturnType<typeof setTimeout> | null = null

const activeProjectTitle = computed(() =>
  publicSiteStore.displayTitle || authStore.projectDisplayName || null
)
const enableAnimatedBrand = computed(() => !activeProjectTitle.value)
const shortTitle = computed(() => {
  if (activeProjectTitle.value) {
    return activeProjectTitle.value
  }
  return languageStore.language === 'zh' ? '光忆集' : 'Aurellic Memoriq'
})

// 相机图标交互状态
const cameraHover = ref(false)
const cameraClicking = ref(false)

const handleCameraHover = () => {
  cameraHover.value = true
}

const handleCameraLeave = () => {
  cameraHover.value = false
}

const handleCameraClick = () => {
  cameraClicking.value = true
}

const handleCameraClickEnd = () => {
  cameraClicking.value = false
}

// 返回主页
const goToHome = () => {
  router.push(buildPublicPath('/', route.path))
}

// 测量文字宽度
const measureText = (text: string, fontSize: string) => {
  const span = document.createElement('span')
  span.style.visibility = 'hidden'
  span.style.position = 'absolute'
  span.style.whiteSpace = 'nowrap'
  span.style.font = `${fontSize} light`
  span.style.letterSpacing = '0.025em'
  span.textContent = text
  document.body.appendChild(span)
  const width = span.offsetWidth
  document.body.removeChild(span)
  return width
}

const handleMouseEnter = () => {
  if (!enableAnimatedBrand.value) return
  if (leaveTimeout) {
    clearTimeout(leaveTimeout)
    leaveTimeout = null
  }

  hoverTimeout = setTimeout(() => {
    animationKey.value++
    isExpanded.value = true
  }, 150)
}

const handleMouseLeave = () => {
  if (!enableAnimatedBrand.value) return
  if (hoverTimeout) {
    clearTimeout(hoverTimeout)
    hoverTimeout = null
  }

  leaveTimeout = setTimeout(() => {
    isExpanded.value = false
  }, 150)
}

// 初始化宽度
onMounted(() => {
  // 测量展开时的宽度（基于两个单词中较宽的）
  const fontSize = shortTextRef.value ? window.getComputedStyle(shortTextRef.value).fontSize : '1.25rem'
  const widthAurellic = measureText('Aurellic', fontSize)
  const widthMemoriq = measureText('Memoriq', fontSize)
  expandedWidth.value = Math.max(widthAurellic, widthMemoriq)
})

onUnmounted(() => {
  if (hoverTimeout) clearTimeout(hoverTimeout)
  if (leaveTimeout) clearTimeout(leaveTimeout)
})
</script>

<style scoped>
/* 相机SVG动画样式 */
.camera-svg {
  @apply w-5 h-5 text-gray-700 dark:text-gray-200 transition-all duration-200;
}

.camera-svg.is-hovering,
.camera-svg.is-clicking {
  @apply text-yellow-500;
}

/* 相机主体 */
.camera-body {
  transition: all 0.3s cubic-bezier(0.34, 1.56, 0.64, 1);
}

.camera-svg.is-hovering .camera-body {
  stroke-dasharray: 80;
  stroke-dashoffset: 80;
  animation: drawCamera 0.6s ease forwards;
}

.camera-svg.is-clicking .camera-body {
  transform: scale(0.95);
}

/* 快门按钮动画 */
.shutter-btn {
  transition: all 0.2s ease;
  transform-origin: 16px 2px;
}

.camera-svg.is-hovering .shutter-btn {
  animation: pressShutter 0.4s ease-in-out 0.3s;
}

.camera-svg.is-clicking .shutter-btn {
  transform: translateY(2px);
}

/* 镜头外圈 */
.lens-outer {
  transition: all 0.3s ease;
  transform-origin: 12px 12px;
}

.camera-svg.is-hovering .lens-outer {
  stroke-dasharray: 35;
  stroke-dashoffset: 35;
  animation: drawLens 0.5s ease forwards 0.2s;
  transform: scale(1.1);
}

/* 镜头内圈 */
.lens-inner {
  transition: all 0.3s ease;
  transform-origin: 12px 12px;
}

.camera-svg.is-hovering .lens-inner {
  animation: lensFocus 0.6s ease-in-out 0.4s;
  fill: rgba(250, 204, 21, 0.3);
}

/* 取景器 */
.viewfinder {
  transition: all 0.3s ease;
}

.camera-svg.is-hovering .viewfinder {
  stroke-dasharray: 14;
  stroke-dashoffset: 14;
  animation: drawViewfinder 0.4s ease forwards 0.3s;
}

/* 闪光灯 */
.flash {
  transition: all 0.2s ease;
}

.camera-svg.is-hovering .flash {
  animation: flashBlink 0.8s ease-in-out 0.5s;
}

/* 绘制边框动画 */
@keyframes drawCamera {
  to {
    stroke-dashoffset: 0;
  }
}

/* 快门按下动画 */
@keyframes pressShutter {
  0%, 100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(3px);
  }
}

/* 镜头绘制 */
@keyframes drawLens {
  to {
    stroke-dashoffset: 0;
  }
}

/* 镜头对焦动画 */
@keyframes lensFocus {
  0%, 100% {
    transform: scale(1);
    r: 3;
  }
  50% {
    transform: scale(0.8);
    r: 2;
  }
}

/* 取景器绘制 */
@keyframes drawViewfinder {
  to {
    stroke-dashoffset: 0;
  }
}

/* 闪光灯闪烁 */
@keyframes flashBlink {
  0%, 100% {
    opacity: 1;
  }
  25%, 75% {
    opacity: 0.3;
  }
  50% {
    opacity: 1;
    fill: #fbbf24;
  }
}

/* 标题字符动画 - 从下方弹入 */
.char-animation {
  display: inline-block;
  font-size: 0.875rem;
  font-weight: 300;
  letter-spacing: 0.025em;
  color: #374151;
  opacity: 0;
  transform: translateY(10px) scale(0.8);
  animation: charReveal 0.4s cubic-bezier(0.34, 1.56, 0.64, 1) forwards;
}

/* 从上方弹入 */
.char-animation-from-top {
  display: inline-block;
  font-size: 0.875rem;
  font-weight: 300;
  letter-spacing: 0.025em;
  color: #374151;
  opacity: 0;
  transform: translateY(-10px) scale(0.8);
  animation: charRevealFromTop 0.4s cubic-bezier(0.34, 1.56, 0.64, 1) forwards;
}

/* 从下方弹入 */
.char-animation-from-bottom {
  display: inline-block;
  font-size: 0.875rem;
  font-weight: 300;
  letter-spacing: 0.025em;
  color: #374151;
  opacity: 0;
  transform: translateY(10px) scale(0.8);
  animation: charRevealFromBottom 0.4s cubic-bezier(0.34, 1.56, 0.64, 1) forwards;
}

:deep(.dark) .char-animation,
:deep(.dark) .char-animation-from-top,
:deep(.dark) .char-animation-from-bottom {
  color: #e5e7eb;
}

@keyframes charReveal {
  0% {
    opacity: 0;
    transform: translateY(10px) scale(0.8);
  }
  60% {
    opacity: 1;
    transform: translateY(-3px) scale(1.05);
  }
  100% {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

@keyframes charRevealFromTop {
  0% {
    opacity: 0;
    transform: translateY(-10px) scale(0.8);
  }
  60% {
    opacity: 1;
    transform: translateY(3px) scale(1.05);
  }
  100% {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

@keyframes charRevealFromBottom {
  0% {
    opacity: 0;
    transform: translateY(10px) scale(0.8);
  }
  60% {
    opacity: 1;
    transform: translateY(-3px) scale(1.05);
  }
  100% {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}
</style>
