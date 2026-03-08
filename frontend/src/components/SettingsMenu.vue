<template>
  <!-- 设置按钮留在原地 -->
    <button
      ref="buttonRef"
      @click="toggleSettings"
      class="settings-btn p-2 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-800 transition-all duration-200 hover:scale-105 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500/70 group relative overflow-hidden"
      title="设置"
      :class="{ 'text-blue-600 dark:text-blue-400': showSettings }"
      @mouseenter="settingsHover = true"
      @mouseleave="settingsHover = false"
    >
      <svg
        class="settings-svg w-6 h-6 transition-transform duration-200"
        :class="{ 'rotate-90': showSettings, 'is-hovering': settingsHover }"
        fill="none"
        stroke="currentColor"
        viewBox="0 0 24 24"
      >
        <!-- 齿轮外圈 -->
        <path
          class="gear-outer"
          stroke-linecap="round"
          stroke-linejoin="round"
          stroke-width="1.5"
          d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.072c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.072 2.573c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.072 2.573c.94 1.543-.827 3.31-2.37 2.37a1.724 1.724 0 00-2.573 1.072c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.072c-1.543.94-3.31-.827-2.37-2.37a1.724 1.724 0 00-1.072-2.573c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.072-2.573c-.94-1.543.827-3.31 2.37-2.37.964.587 2.203.138 2.573-1.072z"
        />
        <!-- 中心圆点 -->
        <path
          class="gear-center"
          stroke-linecap="round"
          stroke-linejoin="round"
          stroke-width="1.5"
          d="M15 12a3 3 0 11-6 0 3 3 0 016 0z"
        />
      </svg>
      <div class="absolute inset-0 bg-gradient-to-r from-cyan-500/10 to-blue-500/10 opacity-0 group-hover:opacity-100 transition-opacity duration-200 rounded-lg"></div>
    </button>

  <!-- 菜单弹窗使用teleport移动到body级别 -->
  <Teleport to="body">
    <!-- 点击外部区域隐藏菜单的遮罩层 -->
    <div
      v-if="showSettings"
      class="fixed top-0 left-0 w-screen h-screen z-[2000] bg-black/5 cursor-pointer"
      @click="closeSettings"
    ></div>

    <transition
      enter-active-class="animate-settings-menu-in"
      leave-active-class="animate-settings-menu-out"
    >
      <div
        v-if="showSettings"
        ref="menuRef"
        class="fixed w-72 glass-panel z-[2100] p-4 space-y-4 shadow-2xl"
        :style="menuStyle"
        @click.stop
      >
      <!-- 管理入口 -->
      <!--
      <div class="pb-3 border-b border-gray-200 dark:border-gray-700">
        <div class="flex items-center justify-between">
          <span class="text-sm font-medium text-gray-700 dark:text-gray-200">管理入口</span>
          <button
            class="px-3 py-1.5 text-xs rounded-md bg-gradient-to-r from-blue-600 to-blue-700 hover:from-blue-700 hover:to-blue-800 text-white font-medium transition-all duration-200 hover:shadow-md transform hover:scale-105"
            @click="goAdmin"
          >
            进入后台
          </button>
        </div>
      </div>
      -->

      <!-- 服务器设置 -->
      <!--
      <div class="pb-3 border-b border-gray-200 dark:border-gray-700">
        <div>
          <label class="block text-xs font-medium text-gray-600 dark:text-gray-300 mb-2">服务器地址</label>
          <div class="flex gap-2">
            <input
              v-model="serverUrl"
              type="text"
              placeholder="例如: 192.168.1.100:6060"
              class="flex-1 px-3 py-2 text-xs rounded-md border bg-white dark:bg-gray-800 border-gray-300 dark:border-gray-600 text-gray-900 dark:text-gray-100 focus:ring-2 focus:ring-blue-500 focus:border-transparent transition-all duration-200"
              @keyup.enter="saveServerUrl"
            >
            <button
              @click="saveServerUrl"
              class="px-3 py-2 text-xs rounded-md bg-blue-600 hover:bg-blue-700 text-white font-medium transition-all duration-200 hover:shadow-md transform hover:scale-105"
            >
              保存
            </button>
          </div>
          <p class="text-xs text-amber-600 dark:text-amber-400 mt-1 leading-relaxed">
            💡 设置后需要刷新页面才能生效
          </p>
        </div>
      </div>
      -->

      <!-- 显示设置 -->
      <div class="space-y-3">
        <div>
          <label class="block text-xs font-medium text-gray-600 dark:text-gray-300 mb-2">封面尺寸</label>
          <div class="flex gap-1.5">
            <button
              v-for="s in coverOptions"
              :key="s.value"
              @click="setCoverSize(s.value)"
              class="flex-1 px-3 py-1.5 text-xs rounded-md border transition-all duration-200 hover:scale-105"
              :class="coverSize === s.value ? activeBtnClass : inactiveBtnClass"
            >
              {{ s.label }}
            </button>
          </div>
        </div>

        <div>
          <label class="block text-xs font-medium text-gray-600 dark:text-gray-300 mb-2">预览图尺寸</label>
          <div class="flex gap-1.5">
            <button
              v-for="p in previewOptions"
              :key="p.value"
              @click="setPreviewSize(p.value)"
              class="flex-1 px-3 py-1.5 text-xs rounded-md border transition-all duration-200 hover:scale-105"
              :class="previewSize === p.value ? activeBtnClass : inactiveBtnClass"
            >
              {{ p.label }}
            </button>
          </div>
        </div>
      </div>

      <!-- 语言设置 -->
      <!-- <div class="pt-3 border-t border-gray-200 dark:border-gray-700">
        <div>
          <label class="block text-xs font-medium text-gray-600 dark:text-gray-300 mb-2">标签语言</label>
          <div class="flex gap-1.5">
            <button
              @click="setLanguage('zh')"
              class="flex-1 px-3 py-1.5 text-xs rounded-md border transition-all duration-200 hover:scale-105"
              :class="language === 'zh' ? activeBtnClass : inactiveBtnClass"
            >
              中文
            </button>
            <button
              @click="setLanguage('en')"
              class="flex-1 px-3 py-1.5 text-xs rounded-md border transition-all duration-200 hover:scale-105"
              :class="language === 'en' ? activeBtnClass : inactiveBtnClass"
            >
              English
            </button>
          </div>
          <p class="text-xs text-amber-600 dark:text-amber-400 mt-2 leading-relaxed">
            💡 需要重启后端服务才能生效
          </p>
        </div>
      </div> -->

      <!-- 功能开关 -->
      <div class="pt-3 border-t border-gray-200 dark:border-gray-700 space-y-3">
        <div class="flex items-center justify-between">
          <span class="text-sm font-medium text-gray-700 dark:text-gray-200">深色模式</span>
          <button
            @click="toggleDark"
            class="relative inline-flex h-6 w-11 items-center rounded-full transition-all duration-200 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500/70 hover:scale-105"
            :class="isDark ? 'bg-blue-600 shadow-blue-500/30 shadow-lg' : 'bg-gray-300 dark:bg-gray-600'"
          >
            <span
              class="inline-block h-4 w-4 transform rounded-full bg-white transition-all duration-200 shadow-sm"
              :class="isDark ? 'translate-x-6' : 'translate-x-1'"
            ></span>
          </button>
        </div>

        <div class="flex items-center justify-between">
          <span class="text-sm font-medium text-gray-700 dark:text-gray-200">相册氛围</span>
          <button
            @click="setAtmosphereEnabled(!atmosphereEnabled)"
            class="relative inline-flex h-6 w-11 items-center rounded-full transition-all duration-200 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500/70 hover:scale-105"
            :class="atmosphereEnabled ? 'bg-green-600 shadow-green-500/30 shadow-lg' : 'bg-gray-300 dark:bg-gray-600'"
          >
            <span
              class="inline-block h-4 w-4 transform rounded-full bg-white transition-all duration-200 shadow-sm"
              :class="atmosphereEnabled ? 'translate-x-6' : 'translate-x-1'"
            ></span>
          </button>
        </div>

        <div v-if="isPhotoWall" class="flex items-center justify-between">
          <span class="text-sm font-medium text-gray-700 dark:text-gray-200">视差滚动</span>
          <button
            @click="setParallaxEnabled(!parallaxEnabled)"
            class="relative inline-flex h-6 w-11 items-center rounded-full transition-all duration-200 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500/70 hover:scale-105"
            :class="parallaxEnabled ? 'bg-purple-600 shadow-purple-500/30 shadow-lg' : 'bg-gray-300 dark:bg-gray-600'"
          >
            <span
              class="inline-block h-4 w-4 transform rounded-full bg-white transition-all duration-200 shadow-sm"
              :class="parallaxEnabled ? 'translate-x-6' : 'translate-x-1'"
            ></span>
          </button>
        </div>

        <div class="flex items-center justify-between">
          <span class="text-sm font-medium text-gray-700 dark:text-gray-200">查看原图</span>
          <button
            @click="setViewOriginalEnabled(!viewOriginalEnabled)"
            class="relative inline-flex h-6 w-11 items-center rounded-full transition-all duration-200 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500/70 hover:scale-105"
            :class="viewOriginalEnabled ? 'bg-orange-600 shadow-orange-500/30 shadow-lg' : 'bg-gray-300 dark:bg-gray-600'"
          >
            <span
              class="inline-block h-4 w-4 transform rounded-full bg-white transition-all duration-200 shadow-sm"
              :class="viewOriginalEnabled ? 'translate-x-6' : 'translate-x-1'"
            ></span>
          </button>
        </div>
      </div>
      </div>
    </transition>
  </Teleport>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUiSettings } from '@/composables/useUiSettings'
import { useLanguageStore } from '@/stores/language'
import { useThemeStore } from '@/stores/theme'

const router = useRouter()
const route = useRoute()
const showSettings = ref(false)
const settingsHover = ref(false)
const settingsRef = ref<HTMLElement>()
const menuRef = ref<HTMLElement>()
const buttonRef = ref<HTMLElement>()
const serverUrl = ref('')

const { coverSize, previewSize, parallaxEnabled, atmosphereEnabled, viewOriginalEnabled, setCoverSize, setPreviewSize, setParallaxEnabled, setAtmosphereEnabled, setViewOriginalEnabled } = useUiSettings()
const { language, setLanguage } = useLanguageStore()
const themeStore = useThemeStore()

// 判断是否在图墙页面
const isPhotoWall = computed(() => route.path === '/wall')

const coverOptions = [
  { value: 'sm', label: '小' },
  { value: 'md', label: '中' },
  { value: 'lg', label: '大' }
]
const previewOptions = [
  { value: 'sm', label: '小' },
  { value: 'md', label: '中' },
  { value: 'lg', label: '大' }
]

const activeBtnClass = 'border-gray-900 dark:border-white bg-gray-900 dark:bg-white text-white dark:text-gray-900'
const inactiveBtnClass = 'border-gray-300 dark:border-gray-700 text-gray-700 dark:text-gray-200 hover:bg-gray-100 dark:hover:bg-gray-800'

const isDark = computed(() => themeStore.isDark)

// 计算弹窗位置（相对于设置按钮）
const menuStyle = computed(() => {
  if (!buttonRef.value) return {}

  const rect = buttonRef.value.getBoundingClientRect()
  // 按钮下方 8px，往上偏移一点以适应用户需求
  const top = rect.bottom + 8 - 4 // -4px 往上一点
  const right = window.innerWidth - rect.right

  return {
    top: `${Math.max(8, top)}px`, // 最小距离顶部 8px
    right: `${Math.max(8, right)}px` // 最小距离右边 8px
  }
})

const toggleSettings = () => {
  showSettings.value = !showSettings.value
  // 强制更新位置计算（如果需要的话）
  if (showSettings.value) {
    nextTick(() => {
      // 确保位置计算正确
    })
  }
}

const closeSettings = () => {
  showSettings.value = false
}

// 处理键盘事件
const handleKeydown = (event: KeyboardEvent) => {
  if (event.key === 'Escape' && showSettings.value) {
    closeSettings()
  }
}

const toggleDark = () => {
  themeStore.toggleTheme()
}

const goAdmin = () => {
  router.push('/admin')
  showSettings.value = false
}

const saveServerUrl = () => {
  if (serverUrl.value.trim()) {
    // 保存到localStorage
    localStorage.setItem('server_url', `http://${serverUrl.value.trim()}`)
    // 显示成功提示（可以后续添加）
    console.log('服务器地址已保存:', serverUrl.value.trim())
  }
}

// 初始化服务器URL
const initServerUrl = () => {
  const saved = localStorage.getItem('server_url')
  if (saved) {
    // 移除http://前缀显示
    serverUrl.value = saved.replace(/^https?:\/\//, '')
  }
}

// 生命周期钩子
onMounted(() => {
  document.addEventListener('keydown', handleKeydown)
  initServerUrl()
})

onUnmounted(() => {
  document.removeEventListener('keydown', handleKeydown)
})
</script>

<style scoped>
/* 设置按钮 SVG 动画样式 */
.settings-svg {
  @apply text-gray-700 dark:text-gray-200;
}

.settings-svg.is-hovering {
  @apply text-yellow-500;
}

/* 齿轮外圈 */
.gear-outer {
  transition: all 0.3s cubic-bezier(0.34, 1.56, 0.64, 1);
  transform-origin: 12px 12px;
}

.settings-svg.is-hovering .gear-outer {
  stroke-dasharray: 100;
  stroke-dashoffset: 100;
  animation: drawGear 0.8s ease forwards;
}

/* 中心圆点 */
.gear-center {
  transition: all 0.3s ease;
  transform-origin: 15px 12px;
}

.settings-svg.is-hovering .gear-center {
  stroke-dasharray: 20;
  stroke-dashoffset: 20;
  animation: drawCenter 0.4s ease forwards 0.4s;
}

/* 绘制齿轮外圈动画 */
@keyframes drawGear {
  to {
    stroke-dashoffset: 0;
  }
}

/* 绘制中心圆点动画 */
@keyframes drawCenter {
  to {
    stroke-dashoffset: 0;
  }
}
</style>

