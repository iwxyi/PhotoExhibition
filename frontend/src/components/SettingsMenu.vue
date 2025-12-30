<template>
  <div class="relative">
    <button
      @click="toggleSettings"
      class="p-2 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-800 transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500/70"
      title="设置"
    >
      <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.072c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.072 2.573c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.072 2.573c.94 1.543-.827 3.31-2.37 2.37a1.724 1.724 0 00-2.573 1.072c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.072c-1.543.94-3.31-.827-2.37-2.37a1.724 1.724 0 00-1.072-2.573c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.072-2.573c-.94-1.543.827-3.31 2.37-2.37.964.587 2.203.138 2.573-1.072z" />
        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
      </svg>
    </button>
    <div
      v-if="showSettings"
      class="absolute right-0 mt-2 w-64 glass-panel z-50 p-3 space-y-3"
    >
      <div class="flex items-center justify-between">
        <span class="text-sm text-gray-700 dark:text-gray-200">进入后台</span>
        <button
          class="px-2 py-1 text-xs rounded bg-gray-900 dark:bg-white text-white dark:text-gray-900 hover:bg-gray-800 dark:hover:bg-gray-100"
          @click="goAdmin"
        >
          打开
        </button>
      </div>
      <div>
        <div class="text-xs text-gray-500 dark:text-gray-400 mb-1">封面尺寸</div>
        <div class="flex gap-2">
          <button
            v-for="s in coverOptions"
            :key="s.value"
            @click="setCoverSize(s.value)"
            class="flex-1 px-2 py-1 text-xs rounded border"
            :class="coverSize === s.value ? activeBtnClass : inactiveBtnClass"
          >
            {{ s.label }}
          </button>
        </div>
      </div>
      <div>
        <div class="text-xs text-gray-500 dark:text-gray-400 mb-1">预览图尺寸</div>
        <div class="flex gap-2">
          <button
            v-for="p in previewOptions"
            :key="p.value"
            @click="setPreviewSize(p.value)"
            class="flex-1 px-2 py-1 text-xs rounded border"
            :class="previewSize === p.value ? activeBtnClass : inactiveBtnClass"
          >
            {{ p.label }}
          </button>
        </div>
      </div>
      <div>
        <div class="text-xs text-gray-500 dark:text-gray-400 mb-1">标签语言</div>
        <div class="flex gap-2">
          <button
            @click="setLanguage('zh')"
            class="flex-1 px-2 py-1 text-xs rounded border"
            :class="language === 'zh' ? activeBtnClass : inactiveBtnClass"
          >
            中文
          </button>
          <button
            @click="setLanguage('en')"
            class="flex-1 px-2 py-1 text-xs rounded border"
            :class="language === 'en' ? activeBtnClass : inactiveBtnClass"
          >
            English
          </button>
        </div>
        <div class="text-xs text-gray-400 dark:text-gray-500 mt-1">
          注意：需要重启后端服务生效
        </div>
      </div>
      <div>
        <div class="flex items-center justify-between">
          <span class="text-xs text-gray-500 dark:text-gray-400">深色模式</span>
          <button
            @click="toggleDark"
            class="relative inline-flex h-6 w-11 items-center rounded-full transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500/70"
            :class="isDark ? 'bg-blue-600' : 'bg-gray-300 dark:bg-gray-600'"
          >
            <span
              class="inline-block h-4 w-4 transform rounded-full bg-white transition-transform"
              :class="isDark ? 'translate-x-6' : 'translate-x-1'"
            ></span>
          </button>
        </div>
      </div>
      <div>
        <div class="flex items-center justify-between">
          <span class="text-xs text-gray-500 dark:text-gray-400">相册氛围</span>
          <button
            @click="setAtmosphereEnabled(!atmosphereEnabled)"
            class="relative inline-flex h-6 w-11 items-center rounded-full transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500/70"
            :class="atmosphereEnabled ? 'bg-blue-600' : 'bg-gray-300 dark:bg-gray-600'"
          >
            <span
              class="inline-block h-4 w-4 transform rounded-full bg-white transition-transform"
              :class="atmosphereEnabled ? 'translate-x-6' : 'translate-x-1'"
            ></span>
          </button>
        </div>
      </div>
      <div v-if="isPhotoWall">
        <div class="flex items-center justify-between">
          <span class="text-xs text-gray-500 dark:text-gray-400">视差滚动</span>
          <button
            @click="setParallaxEnabled(!parallaxEnabled)"
            class="relative inline-flex h-6 w-11 items-center rounded-full transition-colors"
            :class="parallaxEnabled ? 'bg-blue-600' : 'bg-gray-300 dark:bg-gray-600'"
          >
            <span
              class="inline-block h-4 w-4 transform rounded-full bg-white transition-transform"
              :class="parallaxEnabled ? 'translate-x-6' : 'translate-x-1'"
            ></span>
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUiSettings } from '@/composables/useUiSettings'
import { useLanguageStore } from '@/stores/language'
import { useThemeStore } from '@/stores/theme'

const router = useRouter()
const route = useRoute()
const showSettings = ref(false)

const { coverSize, previewSize, parallaxEnabled, atmosphereEnabled, setCoverSize, setPreviewSize, setParallaxEnabled, setAtmosphereEnabled } = useUiSettings()
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

const toggleSettings = () => {
  showSettings.value = !showSettings.value
}

const toggleDark = () => {
  themeStore.toggleTheme()
}

const goAdmin = () => {
  router.push('/admin')
  showSettings.value = false
}
</script>

