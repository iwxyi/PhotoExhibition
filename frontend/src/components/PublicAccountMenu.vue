<template>
  <div class="relative">
    <button
      ref="triggerRef"
      @click="toggleMenu"
      class="group relative z-[71] inline-flex h-10 w-10 items-center justify-center overflow-hidden rounded-full text-stone-700 transition-all duration-300 hover:scale-[1.055] dark:text-stone-200"
      :class="open ? 'scale-[1.045]' : ''"
      :title="authStore.isAuthenticated ? authStore.displayName || '账号菜单' : '账号与设置'"
    >
      <div class="absolute inset-[2px] rounded-full bg-white/0 transition-all duration-300 ease-out group-hover:bg-white/72 group-hover:translate-x-[4px] dark:group-hover:bg-white/[0.05]"></div>
      <div class="absolute inset-0 rounded-full ring-1 ring-transparent transition-all duration-300 group-hover:ring-stone-300/35 dark:group-hover:ring-white/10" :class="open ? 'ring-stone-300/45 dark:ring-white/12' : ''"></div>
      <div class="avatar-glint absolute inset-0 rounded-full"></div>
      <div class="avatar-orbit absolute inset-[1px] rounded-full" :class="open ? 'avatar-orbit-open' : ''"></div>
      <span class="relative z-10 flex h-8 w-8 items-center justify-center rounded-full overflow-hidden ring-1 ring-black/5 dark:ring-white/8 bg-stone-100 dark:bg-white/[0.08] transition-transform duration-300 group-hover:scale-[1.05] group-hover:-translate-y-[0.5px]">
        <img v-if="authStore.avatarPath" :src="authStore.avatarPath" alt="avatar" class="h-full w-full object-cover" />
        <svg v-else class="w-4 h-4 text-gray-400 dark:text-gray-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.7" d="M15 19a4 4 0 00-6 0m6 0a7 7 0 10-6 0m6 0H9" />
        </svg>
      </span>
    </button>
  </div>

  <Teleport to="body">
    <Transition
      enter-active-class="transition-opacity duration-220 ease-out"
      enter-from-class="opacity-0"
      enter-to-class="opacity-100"
      leave-active-class="transition-opacity duration-180 ease-in"
      leave-from-class="opacity-100"
      leave-to-class="opacity-0"
    >
      <button
        v-if="open"
        type="button"
        aria-label="关闭账号菜单"
        class="fixed inset-0 z-[60] bg-black/5"
        @click="open = false"
      ></button>
    </Transition>

    <Transition name="account-menu">
      <div
        v-if="open"
        ref="panelRef"
        class="fixed z-[70] w-72 overflow-hidden rounded-[16px] border border-slate-400/35 shadow-[0_18px_45px_rgba(15,23,42,0.18)] backdrop-blur-[20px] dark:border-slate-600/50 dark:shadow-[0_25px_60px_rgba(0,0,0,0.8)]"
        :style="panelStyle"
      >
        <div class="pointer-events-none absolute inset-0 bg-[rgba(248,250,252,0.8)] dark:bg-[rgba(15,23,42,0.8)]"></div>
      <div class="menu-section menu-section-1 relative px-4 py-3.5">
        <div class="flex items-center gap-3">
          <span class="menu-avatar flex h-11 w-11 items-center justify-center rounded-full overflow-hidden bg-white/65 dark:bg-white/[0.08] ring-1 ring-black/5 dark:ring-white/8">
            <img v-if="authStore.avatarPath" :src="authStore.avatarPath" alt="avatar" class="h-full w-full object-cover" />
            <svg v-else class="w-5 h-5 text-gray-400 dark:text-gray-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.7" d="M15 19a4 4 0 00-6 0m6 0a7 7 0 10-6 0m6 0H9" />
            </svg>
          </span>
          <div class="min-w-0">
            <div class="text-sm font-medium text-stone-800 dark:text-stone-100 truncate">
              {{ authStore.isAuthenticated ? authStore.displayName : '未登录' }}
            </div>
            <div class="text-xs text-stone-500 dark:text-stone-400 truncate">
              {{ authStore.isAuthenticated ? roleLabel : '登录后可查看个人资料与后台' }}
            </div>
            <div v-if="authStore.isAuthenticated && authStore.projectDisplayName" class="text-xs text-sky-600 dark:text-sky-300 truncate">
              {{ authStore.projectDisplayName }}
            </div>
            <div v-if="authStore.isAuthenticated && authStore.currentVipPlanName" class="text-xs text-amber-600 dark:text-amber-300 truncate">
              {{ authStore.currentVipPlanName }}
            </div>
          </div>
        </div>
      </div>

      <div class="menu-section menu-section-2 relative border-t border-black/[0.045] dark:border-white/[0.06]">
        <template v-if="authStore.isAuthenticated">
          <router-link
            to="/profile"
            class="menu-link flex items-center gap-2 px-4 py-3 text-sm text-stone-700 dark:text-stone-200"
            @click="open = false"
          >
            个人资料
          </router-link>
          <router-link
            to="/profile#vip-center"
            class="menu-link flex items-center gap-2 px-4 py-3 text-sm text-stone-700 dark:text-stone-200"
            @click="open = false"
          >
            会员中心
          </router-link>
          <router-link
            to="/vip"
            class="menu-link flex items-center gap-2 px-4 py-3 text-sm text-stone-700 dark:text-stone-200"
            @click="open = false"
          >
            独立会员页
          </router-link>
          <router-link
            to="/admin"
            class="menu-link flex items-center gap-2 px-4 py-3 text-sm text-stone-700 dark:text-stone-200"
            @click="open = false"
          >
            进入后台
          </router-link>
        </template>
        <template v-else>
          <router-link
            to="/login"
            class="menu-link flex items-center gap-2 px-4 py-3 text-sm text-stone-700 dark:text-stone-200"
            @click="open = false"
          >
            登录
          </router-link>
          <router-link
            v-if="authStore.multiUserEnabled"
            to="/register"
            class="menu-link flex items-center gap-2 px-4 py-3 text-sm text-stone-700 dark:text-stone-200"
            @click="open = false"
          >
            注册
          </router-link>
        </template>
      </div>

      <div class="menu-section menu-section-3 relative border-t border-black/[0.045] dark:border-white/[0.06] px-4 py-3.5 space-y-3">
        <div>
          <div class="menu-kicker mb-2 text-[11px] uppercase tracking-[0.18em] text-stone-400 dark:text-stone-500">显示</div>
          <div class="space-y-3">
            <div>
              <label class="mb-2 block text-xs font-medium text-stone-600 dark:text-stone-300">封面尺寸</label>
              <div class="flex gap-1.5">
                <button
                  v-for="s in coverOptions"
                  :key="s.value"
                  @click="setCoverSize(s.value)"
                  class="menu-chip flex-1 px-3 py-1.5 text-xs rounded-md border transition-all duration-200"
                  :class="coverSize === s.value ? activeBtnClass : inactiveBtnClass"
                >
                  {{ s.label }}
                </button>
              </div>
            </div>

            <div>
              <label class="mb-2 block text-xs font-medium text-stone-600 dark:text-stone-300">预览图尺寸</label>
              <div class="flex gap-1.5">
                <button
                  v-for="p in previewOptions"
                  :key="p.value"
                  @click="setPreviewSize(p.value)"
                  class="menu-chip flex-1 px-3 py-1.5 text-xs rounded-md border transition-all duration-200"
                  :class="previewSize === p.value ? activeBtnClass : inactiveBtnClass"
                >
                  {{ p.label }}
                </button>
              </div>
            </div>
          </div>
        </div>

        <div class="border-t border-black/[0.045] dark:border-white/[0.06] pt-3 space-y-3">
          <div class="flex items-center justify-between">
            <span class="text-sm font-medium text-stone-700 dark:text-stone-200">深色模式</span>
            <button
              @click="toggleThemeSwitch"
              class="menu-switch relative inline-flex h-6 w-11 items-center rounded-full"
              :class="[themeStore.isDark ? 'is-on bg-blue-600 shadow-blue-500/30 shadow-lg' : 'bg-gray-300 dark:bg-gray-600', activeSwitchPulse === 'theme' ? 'is-pulsing' : '']"
            >
              <span class="menu-switch-thumb inline-block h-4 w-4 rounded-full bg-white shadow-sm" :class="themeStore.isDark ? 'translate-x-6' : 'translate-x-1'"></span>
            </button>
          </div>

          <div class="flex items-center justify-between">
            <span class="text-sm font-medium text-stone-700 dark:text-stone-200">相册氛围</span>
            <button
              @click="toggleAtmosphereSwitch"
              class="menu-switch relative inline-flex h-6 w-11 items-center rounded-full"
              :class="[atmosphereEnabled ? 'is-on bg-green-600 shadow-green-500/30 shadow-lg' : 'bg-gray-300 dark:bg-gray-600', activeSwitchPulse === 'atmosphere' ? 'is-pulsing' : '']"
            >
              <span class="menu-switch-thumb inline-block h-4 w-4 rounded-full bg-white shadow-sm" :class="atmosphereEnabled ? 'translate-x-6' : 'translate-x-1'"></span>
            </button>
          </div>

          <div v-if="isPhotoWall" class="flex items-center justify-between">
            <span class="text-sm font-medium text-stone-700 dark:text-stone-200">视差滚动</span>
            <button
              @click="toggleParallaxSwitch"
              class="menu-switch relative inline-flex h-6 w-11 items-center rounded-full"
              :class="[parallaxEnabled ? 'is-on bg-purple-600 shadow-purple-500/30 shadow-lg' : 'bg-gray-300 dark:bg-gray-600', activeSwitchPulse === 'parallax' ? 'is-pulsing' : '']"
            >
              <span class="menu-switch-thumb inline-block h-4 w-4 rounded-full bg-white shadow-sm" :class="parallaxEnabled ? 'translate-x-6' : 'translate-x-1'"></span>
            </button>
          </div>

          <div class="flex items-center justify-between">
            <span class="text-sm font-medium text-stone-700 dark:text-stone-200">查看原图</span>
            <button
              @click="toggleViewOriginalSwitch"
              class="menu-switch relative inline-flex h-6 w-11 items-center rounded-full"
              :class="[viewOriginalEnabled ? 'is-on bg-orange-600 shadow-orange-500/30 shadow-lg' : 'bg-gray-300 dark:bg-gray-600', activeSwitchPulse === 'original' ? 'is-pulsing' : '']"
            >
              <span class="menu-switch-thumb inline-block h-4 w-4 rounded-full bg-white shadow-sm" :class="viewOriginalEnabled ? 'translate-x-6' : 'translate-x-1'"></span>
            </button>
          </div>
        </div>
      </div>

      <div v-if="authStore.isAuthenticated" class="menu-section menu-section-4 relative border-t border-black/[0.045] dark:border-white/[0.06]">
        <button
          class="menu-link w-full px-4 py-3 text-left text-sm text-red-600 dark:text-red-400"
          @click="handleLogout"
        >
          退出登录
        </button>
      </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
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
const triggerRef = ref<HTMLElement | null>(null)
const panelRef = ref<HTMLElement | null>(null)
const activeSwitchPulse = ref<'theme' | 'atmosphere' | 'parallax' | 'original' | null>(null)
let switchPulseTimer: ReturnType<typeof setTimeout> | null = null
const panelStyle = ref<Record<string, string>>({
  top: '0px',
  left: '0px',
  'transform-origin': 'calc(100% - 20px) 0px',
  '-webkit-backdrop-filter': 'blur(20px)',
  'backdrop-filter': 'blur(20px)'
})
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

const activeBtnClass = 'border-stone-900 dark:border-stone-100 bg-stone-900 dark:bg-stone-100 text-stone-50 dark:text-stone-900'
const inactiveBtnClass = 'border-stone-300/80 dark:border-white/10 text-stone-700 dark:text-stone-200 bg-white/50 dark:bg-white/[0.03] hover:bg-black/[0.035] dark:hover:bg-white/[0.04]'

const roleLabel = computed(() => {
  if (authStore.role === 'SUPER_ADMIN') return '超级管理员'
  if (authStore.role === 'USER_ADMIN') return '用户后台'
  return '账号'
})

const isPhotoWall = computed(() => stripPublicSlug(route.path) === '/wall')

const pulseSwitch = (key: 'theme' | 'atmosphere' | 'parallax' | 'original') => {
  activeSwitchPulse.value = key
  if (switchPulseTimer) clearTimeout(switchPulseTimer)
  switchPulseTimer = setTimeout(() => {
    activeSwitchPulse.value = null
    switchPulseTimer = null
  }, 420)
}

const toggleThemeSwitch = () => {
  themeStore.toggleTheme()
  pulseSwitch('theme')
}

const toggleAtmosphereSwitch = () => {
  setAtmosphereEnabled(!atmosphereEnabled.value)
  pulseSwitch('atmosphere')
}

const toggleParallaxSwitch = () => {
  setParallaxEnabled(!parallaxEnabled.value)
  pulseSwitch('parallax')
}

const toggleViewOriginalSwitch = () => {
  setViewOriginalEnabled(!viewOriginalEnabled.value)
  pulseSwitch('original')
}

const updatePanelPosition = () => {
  const trigger = triggerRef.value
  if (!trigger) return

  const rect = trigger.getBoundingClientRect()
  const panelWidth = 288
  const viewportPadding = 16
  const gap = 8
  const left = Math.min(
    window.innerWidth - panelWidth - viewportPadding,
    Math.max(viewportPadding, rect.right - panelWidth)
  )
  const top = Math.min(window.innerHeight - viewportPadding, rect.bottom + gap)

  panelStyle.value = {
    top: `${top}px`,
    left: `${left}px`,
    'transform-origin': `${Math.round(rect.right - left - rect.width / 2)}px 0px`,
    '-webkit-backdrop-filter': 'blur(20px)',
    'backdrop-filter': 'blur(20px)'
  }
}

const toggleMenu = () => {
  open.value = !open.value
}

const handleLogout = () => {
  authStore.logout()
  open.value = false
  router.push(buildPublicPath('/'))
}

const handleClickOutside = (event: MouseEvent) => {
  const target = event.target as Node | null
  if (!target) return
  if (triggerRef.value?.contains(target)) return
  if (panelRef.value?.contains(target)) return
  if (open.value) {
    open.value = false
  }
}

const handleKeydown = (event: KeyboardEvent) => {
  if (event.key === 'Escape') {
    open.value = false
  }
}

const handleWindowChange = () => {
  if (!open.value) return
  updatePanelPosition()
}

onMounted(() => {
  document.addEventListener('click', handleClickOutside)
  document.addEventListener('keydown', handleKeydown)
  window.addEventListener('resize', handleWindowChange, { passive: true })
  window.addEventListener('scroll', handleWindowChange, { passive: true })
  authStore.fetchPublicSettings().catch(() => {
    // ignore
  })
})

watch(open, async (value) => {
  if (!value) return
  await nextTick()
  updatePanelPosition()
})

onUnmounted(() => {
  document.removeEventListener('click', handleClickOutside)
  document.removeEventListener('keydown', handleKeydown)
  window.removeEventListener('resize', handleWindowChange)
  window.removeEventListener('scroll', handleWindowChange)
  if (switchPulseTimer) clearTimeout(switchPulseTimer)
})
</script>

<style scoped>
.avatar-orbit {
  border: 1px solid transparent;
  opacity: 0;
  transform: scale(0.82);
  transition: opacity 0.28s ease, transform 0.38s cubic-bezier(0.22, 1, 0.36, 1), border-color 0.28s ease;
}

.avatar-glint {
  opacity: 0;
  background: radial-gradient(circle at 35% 30%, rgba(255,255,255,0.72), rgba(255,255,255,0) 52%);
  transform: scale(0.78);
  transition: opacity 0.24s ease, transform 0.34s ease;
}

.menu-avatar {
  position: relative;
  animation: menuAvatarBreath 3.2s ease-in-out infinite;
  transition: transform 0.34s cubic-bezier(0.22, 1, 0.36, 1), box-shadow 0.28s ease;
}

.menu-section-1:hover .menu-avatar {
  transform: translateY(-1px) scale(1.05);
  box-shadow: 0 10px 22px rgba(15, 23, 42, 0.1);
}

:deep(.dark) .menu-section-1:hover .menu-avatar {
  box-shadow: 0 10px 22px rgba(0, 0, 0, 0.28);
}

.group:hover .avatar-orbit,
.avatar-orbit-open {
  opacity: 1;
  transform: scale(1.06);
  border-color: rgba(214, 211, 209, 0.42);
}

:deep(.dark) .group:hover .avatar-orbit,
:deep(.dark) .avatar-orbit-open {
  border-color: rgba(255, 255, 255, 0.12);
}

.group:hover .avatar-glint {
  opacity: 1;
  transform: scale(1);
}

:deep(.dark) .avatar-glint {
  background: radial-gradient(circle at 35% 30%, rgba(255,255,255,0.18), rgba(255,255,255,0) 52%);
}

.account-menu-enter-active,
.account-menu-leave-active {
  transition: opacity 0.22s ease, transform 0.26s cubic-bezier(0.22, 1, 0.36, 1), filter 0.26s ease;
}

.account-menu-enter-from,
.account-menu-leave-to {
  opacity: 0;
  transform: translateY(-6px) scale(0.96);
  filter: blur(4px);
}

.account-menu-enter-to,
.account-menu-leave-from {
  opacity: 1;
  transform: translateY(0) scale(1);
  filter: blur(0);
}

.menu-section {
  opacity: 1;
  transform: translateY(0);
  transition: opacity 0.28s ease, transform 0.34s cubic-bezier(0.22, 1, 0.36, 1);
}

.menu-link {
  position: relative;
  overflow: hidden;
  transition: background-color 0.22s ease, transform 0.22s ease, color 0.22s ease;
}

.menu-link::before {
  content: '';
  position: absolute;
  inset: 0.25rem;
  border-radius: 0.75rem;
  background: rgba(255, 255, 255, 0);
  transform: translateX(-6px);
  opacity: 0;
  transition: opacity 0.24s ease, transform 0.28s ease, background-color 0.24s ease;
}

.menu-link:hover {
  transform: translateX(2px);
  background-color: transparent;
}

.menu-link:hover::before {
  opacity: 1;
  transform: translateX(0);
  background: rgba(255, 255, 255, 0.4);
}

:deep(.dark) .menu-link:hover::before {
  background: rgba(255, 255, 255, 0.05);
}

.menu-kicker {
  transition: letter-spacing 0.28s ease, opacity 0.28s ease, transform 0.28s ease;
}

.menu-section:hover .menu-kicker {
  letter-spacing: 0.22em;
  transform: translateX(1px);
}

.menu-chip {
  position: relative;
  overflow: hidden;
}

.menu-chip::after {
  content: '';
  position: absolute;
  inset: 0;
  background: linear-gradient(90deg, rgba(255,255,255,0), rgba(255,255,255,0.22), rgba(255,255,255,0));
  opacity: 0;
  transform: translateX(-24%);
  transition: opacity 0.24s ease, transform 0.32s ease;
}

.menu-chip:hover::after {
  opacity: 1;
  transform: translateX(24%);
}

.menu-switch {
  transition: transform 0.22s ease, box-shadow 0.22s ease, background-color 0.22s ease;
}

.menu-switch:hover {
  transform: scale(1.04);
}

.menu-switch:active {
  transform: scale(0.96);
}

.menu-switch-thumb {
  transition: transform 0.44s cubic-bezier(0.22, 1.32, 0.32, 1), box-shadow 0.24s ease;
  will-change: transform;
}

.menu-switch.is-pulsing {
  animation: switchTrackPulse 0.42s cubic-bezier(0.22, 1.32, 0.32, 1);
}

.menu-switch.is-pulsing .menu-switch-thumb {
  animation: switchThumbBounce 0.42s cubic-bezier(0.22, 1.32, 0.32, 1);
}

@keyframes switchThumbBounce {
  0% { scale: 0.88; }
  58% { scale: 1.12; }
  100% { scale: 1; }
}

@keyframes switchTrackPulse {
  0% { transform: scale(1); }
  45% { transform: scale(1.06); }
  100% { transform: scale(1); }
}

@keyframes menuAvatarBreath {
  0%, 100% {
    transform: scale(1);
  }
  50% {
    transform: scale(1.05);
  }
}

.account-menu-enter-from .menu-section,
.account-menu-leave-to .menu-section {
  opacity: 0;
  transform: translateY(-8px);
}

.account-menu-enter-active .menu-section-1 { transition-delay: 0.03s; }
.account-menu-enter-active .menu-section-2 { transition-delay: 0.06s; }
.account-menu-enter-active .menu-section-3 { transition-delay: 0.09s; }
.account-menu-enter-active .menu-section-4 { transition-delay: 0.12s; }
</style>
