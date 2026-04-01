<template>
  <div ref="menuRef" class="relative">
    <button
      @click="toggleMenu"
      class="inline-flex items-center justify-center h-10 w-10 rounded-full border border-gray-200/70 dark:border-gray-700/70 bg-white/70 dark:bg-gray-800/70 hover:bg-white dark:hover:bg-gray-800 text-gray-600 dark:text-gray-200 shadow-sm transition-all duration-200"
      :title="authStore.isAuthenticated ? authStore.displayName || '账号菜单' : '账号与设置'"
    >
      <span class="flex h-8 w-8 items-center justify-center rounded-full overflow-hidden bg-gray-100 dark:bg-gray-700">
        <img v-if="authStore.avatarPath" :src="authStore.avatarPath" alt="avatar" class="h-full w-full object-cover" />
        <svg v-else class="w-4 h-4 text-gray-400 dark:text-gray-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.7" d="M15 19a4 4 0 00-6 0m6 0a7 7 0 10-6 0m6 0H9" />
        </svg>
      </span>
    </button>

    <div
      v-if="open"
      class="absolute right-0 top-12 w-72 rounded-2xl border border-gray-200 dark:border-gray-700 bg-white/95 dark:bg-gray-900/95 shadow-2xl backdrop-blur-md overflow-hidden z-[70]"
    >
      <div class="px-4 py-3">
        <div class="flex items-center gap-3">
          <span class="flex h-11 w-11 items-center justify-center rounded-full overflow-hidden bg-gray-100 dark:bg-gray-700">
            <img v-if="authStore.avatarPath" :src="authStore.avatarPath" alt="avatar" class="h-full w-full object-cover" />
            <svg v-else class="w-5 h-5 text-gray-400 dark:text-gray-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.7" d="M15 19a4 4 0 00-6 0m6 0a7 7 0 10-6 0m6 0H9" />
            </svg>
          </span>
          <div class="min-w-0">
            <div class="text-sm font-medium text-gray-800 dark:text-gray-100 truncate">
              {{ authStore.isAuthenticated ? authStore.displayName : '未登录' }}
            </div>
            <div class="text-xs text-gray-500 dark:text-gray-400 truncate">
              {{ authStore.isAuthenticated ? roleLabel : '登录后可查看个人资料与后台' }}
            </div>
            <div v-if="authStore.isAuthenticated && authStore.projectDisplayName" class="text-xs text-blue-500 dark:text-blue-300 truncate">
              {{ authStore.projectDisplayName }}
            </div>
            <div v-if="authStore.isAuthenticated && authStore.currentVipPlanName" class="text-xs text-amber-500 dark:text-amber-300 truncate">
              {{ authStore.currentVipPlanName }}
            </div>
          </div>
        </div>
      </div>

      <div class="border-t border-gray-100 dark:border-gray-800">
        <template v-if="authStore.isAuthenticated">
          <router-link
            to="/profile"
            class="flex items-center gap-2 px-4 py-3 text-sm text-gray-700 dark:text-gray-200 hover:bg-gray-50 dark:hover:bg-gray-800 transition-colors"
            @click="open = false"
          >
            个人资料
          </router-link>
          <router-link
            to="/profile#vip-center"
            class="flex items-center gap-2 px-4 py-3 text-sm text-gray-700 dark:text-gray-200 hover:bg-gray-50 dark:hover:bg-gray-800 transition-colors"
            @click="open = false"
          >
            会员中心
          </router-link>
          <router-link
            to="/vip"
            class="flex items-center gap-2 px-4 py-3 text-sm text-gray-700 dark:text-gray-200 hover:bg-gray-50 dark:hover:bg-gray-800 transition-colors"
            @click="open = false"
          >
            独立会员页
          </router-link>
          <router-link
            to="/admin"
            class="flex items-center gap-2 px-4 py-3 text-sm text-gray-700 dark:text-gray-200 hover:bg-gray-50 dark:hover:bg-gray-800 transition-colors"
            @click="open = false"
          >
            进入后台
          </router-link>
        </template>
        <template v-else>
          <router-link
            to="/login"
            class="flex items-center gap-2 px-4 py-3 text-sm text-gray-700 dark:text-gray-200 hover:bg-gray-50 dark:hover:bg-gray-800 transition-colors"
            @click="open = false"
          >
            登录
          </router-link>
          <router-link
            v-if="authStore.multiUserEnabled"
            to="/register"
            class="flex items-center gap-2 px-4 py-3 text-sm text-gray-700 dark:text-gray-200 hover:bg-gray-50 dark:hover:bg-gray-800 transition-colors"
            @click="open = false"
          >
            注册
          </router-link>
        </template>
      </div>

      <div class="border-t border-gray-100 dark:border-gray-800 px-4 py-3 space-y-3">
        <div>
          <div class="text-[11px] uppercase tracking-[0.18em] text-gray-400 dark:text-gray-500 mb-2">显示</div>
          <div class="space-y-3">
            <div>
              <label class="block text-xs font-medium text-gray-600 dark:text-gray-300 mb-2">封面尺寸</label>
              <div class="flex gap-1.5">
                <button
                  v-for="s in coverOptions"
                  :key="s.value"
                  @click="setCoverSize(s.value)"
                  class="flex-1 px-3 py-1.5 text-xs rounded-md border transition-all duration-200"
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
                  class="flex-1 px-3 py-1.5 text-xs rounded-md border transition-all duration-200"
                  :class="previewSize === p.value ? activeBtnClass : inactiveBtnClass"
                >
                  {{ p.label }}
                </button>
              </div>
            </div>
          </div>
        </div>

        <div class="border-t border-gray-100 dark:border-gray-800 pt-3 space-y-3">
          <div class="flex items-center justify-between">
            <span class="text-sm font-medium text-gray-700 dark:text-gray-200">深色模式</span>
            <button
              @click="themeStore.toggleTheme"
              class="relative inline-flex h-6 w-11 items-center rounded-full transition-all duration-200"
              :class="themeStore.isDark ? 'bg-blue-600 shadow-blue-500/30 shadow-lg' : 'bg-gray-300 dark:bg-gray-600'"
            >
              <span class="inline-block h-4 w-4 transform rounded-full bg-white transition-all duration-200 shadow-sm" :class="themeStore.isDark ? 'translate-x-6' : 'translate-x-1'"></span>
            </button>
          </div>

          <div class="flex items-center justify-between">
            <span class="text-sm font-medium text-gray-700 dark:text-gray-200">相册氛围</span>
            <button
              @click="setAtmosphereEnabled(!atmosphereEnabled)"
              class="relative inline-flex h-6 w-11 items-center rounded-full transition-all duration-200"
              :class="atmosphereEnabled ? 'bg-green-600 shadow-green-500/30 shadow-lg' : 'bg-gray-300 dark:bg-gray-600'"
            >
              <span class="inline-block h-4 w-4 transform rounded-full bg-white transition-all duration-200 shadow-sm" :class="atmosphereEnabled ? 'translate-x-6' : 'translate-x-1'"></span>
            </button>
          </div>

          <div v-if="isPhotoWall" class="flex items-center justify-between">
            <span class="text-sm font-medium text-gray-700 dark:text-gray-200">视差滚动</span>
            <button
              @click="setParallaxEnabled(!parallaxEnabled)"
              class="relative inline-flex h-6 w-11 items-center rounded-full transition-all duration-200"
              :class="parallaxEnabled ? 'bg-purple-600 shadow-purple-500/30 shadow-lg' : 'bg-gray-300 dark:bg-gray-600'"
            >
              <span class="inline-block h-4 w-4 transform rounded-full bg-white transition-all duration-200 shadow-sm" :class="parallaxEnabled ? 'translate-x-6' : 'translate-x-1'"></span>
            </button>
          </div>

          <div class="flex items-center justify-between">
            <span class="text-sm font-medium text-gray-700 dark:text-gray-200">查看原图</span>
            <button
              @click="setViewOriginalEnabled(!viewOriginalEnabled)"
              class="relative inline-flex h-6 w-11 items-center rounded-full transition-all duration-200"
              :class="viewOriginalEnabled ? 'bg-orange-600 shadow-orange-500/30 shadow-lg' : 'bg-gray-300 dark:bg-gray-600'"
            >
              <span class="inline-block h-4 w-4 transform rounded-full bg-white transition-all duration-200 shadow-sm" :class="viewOriginalEnabled ? 'translate-x-6' : 'translate-x-1'"></span>
            </button>
          </div>
        </div>
      </div>

      <div v-if="authStore.isAuthenticated" class="border-t border-gray-100 dark:border-gray-800">
        <button
          class="w-full text-left px-4 py-3 text-sm text-red-600 dark:text-red-400 hover:bg-red-50 dark:hover:bg-red-950/40 transition-colors"
          @click="handleLogout"
        >
          退出登录
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { buildPublicPath, stripPublicSlug } from '@/utils/publicRoute'
import { useUiSettings } from '@/composables/useUiSettings'
import { useThemeStore } from '@/stores/theme'

const authStore = useAuthStore()
const router = useRouter()
const route = useRoute()
const themeStore = useThemeStore()
const open = ref(false)
const menuRef = ref<HTMLElement | null>(null)
const { coverSize, previewSize, parallaxEnabled, atmosphereEnabled, viewOriginalEnabled, setCoverSize, setPreviewSize, setParallaxEnabled, setAtmosphereEnabled, setViewOriginalEnabled } = useUiSettings()

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

const roleLabel = computed(() => {
  if (authStore.role === 'SUPER_ADMIN') return '超级管理员'
  if (authStore.role === 'USER_ADMIN') return '用户后台'
  return '账号'
})

const isPhotoWall = computed(() => stripPublicSlug(route.path) === '/wall')

const toggleMenu = () => {
  open.value = !open.value
}

const handleLogout = () => {
  authStore.logout()
  open.value = false
  router.push(buildPublicPath('/'))
}

const handleClickOutside = (event: MouseEvent) => {
  if (!menuRef.value) return
  if (!menuRef.value.contains(event.target as Node)) {
    open.value = false
  }
}

const handleKeydown = (event: KeyboardEvent) => {
  if (event.key === 'Escape') {
    open.value = false
  }
}

onMounted(() => {
  document.addEventListener('click', handleClickOutside)
  document.addEventListener('keydown', handleKeydown)
  authStore.fetchPublicSettings().catch(() => {
    // ignore
  })
})

onUnmounted(() => {
  document.removeEventListener('click', handleClickOutside)
  document.removeEventListener('keydown', handleKeydown)
})
</script>
