<template>
  <div v-if="authStore.token && isLegacyStyle" class="admin-style-chrome" :data-style="themeStore.currentStyleFamily">
    <template v-if="themeStore.currentStyleFamily === 'material'">
      <header class="admin-style-topbar admin-style-topbar--material">
        <div class="admin-style-topbar-material-wrap">
          <div class="admin-style-topbar-material-rail">
            <div class="admin-style-topbar-brand admin-style-topbar-brand--material">
              <div class="admin-style-topbar-avatar">{{ shortLabel(authStore.projectDisplayName || '后台') }}</div>
              <div class="admin-style-topbar-copy">
                <div class="admin-style-topbar-title">{{ authStore.projectDisplayName || '后台管理' }}</div>
              </div>
            </div>
            <nav class="admin-style-topbar-nav admin-style-topbar-nav--material">
              <router-link
                v-for="item in quickLinks"
                :key="`material-top-${item.to}`"
                :to="item.to"
                class="admin-style-topbar-nav-link"
                :class="{ 'admin-style-topbar-nav-link--active': route.path === item.to }"
              >
                {{ item.label }}
              </router-link>
            </nav>
          </div>
        </div>
      </header>

      <nav class="admin-style-bottom-nav admin-style-bottom-nav--material">
        <router-link
          v-for="item in mobileLinks"
          :key="`material-mobile-${item.to}`"
          :to="item.to"
          class="admin-style-bottom-nav-link"
          :class="{ 'admin-style-bottom-nav-link--active': route.path === item.to }"
        >
          <span class="admin-style-bottom-nav-icon"></span>
          <span>{{ item.label }}</span>
        </router-link>
      </nav>
    </template>

    <template v-else-if="themeStore.currentStyleFamily === 'glass'">
      <header class="admin-style-orbit admin-style-orbit--glass">
        <div class="admin-style-orbit-copy">
          <div class="admin-style-orbit-title">{{ authStore.projectDisplayName || '后台管理' }}</div>
        </div>
        <nav class="admin-style-orbit-nav">
          <router-link
            v-for="item in compactLinks"
            :key="`glass-${item.to}`"
            :to="item.to"
            class="admin-style-orbit-link"
            :class="{ 'admin-style-orbit-link--active': route.path === item.to }"
          >
            {{ item.label }}
          </router-link>
        </nav>
      </header>
    </template>

    <template v-else-if="themeStore.currentStyleFamily === 'classic'">
      <header class="admin-style-manor admin-style-manor--classic">
        <div class="admin-style-manor-head">
          <div class="admin-style-manor-title">{{ authStore.projectDisplayName || '后台管理' }}</div>
        </div>
        <nav class="admin-style-manor-nav">
          <router-link
            v-for="item in quickLinks"
            :key="`classic-${item.to}`"
            :to="item.to"
            class="admin-style-manor-link"
            :class="{ 'admin-style-manor-link--active': route.path === item.to }"
          >
            {{ item.label }}
          </router-link>
        </nav>
      </header>
    </template>

    <template v-else-if="themeStore.currentStyleFamily === 'gallery'">
      <aside class="admin-style-salon admin-style-salon--gallery">
        <div class="admin-style-salon-title">{{ authStore.projectDisplayName || '后台管理' }}</div>
        <nav class="admin-style-salon-nav">
          <router-link
            v-for="item in quickLinks"
            :key="`gallery-${item.to}`"
            :to="item.to"
            class="admin-style-salon-link"
            :class="{ 'admin-style-salon-link--active': route.path === item.to }"
          >
            <span class="admin-style-salon-index">{{ shortLabel(item.label) }}</span>
            <span>{{ item.label }}</span>
          </router-link>
        </nav>
      </aside>
    </template>

    <template v-else-if="themeStore.currentStyleFamily === 'compact'">
      <div class="admin-style-command admin-style-command--compact">
        <router-link
          v-for="item in compactLinks"
          :key="`compact-${item.to}`"
          :to="item.to"
          class="admin-style-command-link"
          :class="{ 'admin-style-command-link--active': route.path === item.to }"
        >
          {{ item.label }}
        </router-link>
      </div>
    </template>

    <template v-else-if="themeStore.currentStyleFamily === 'brutalist'">
      <aside class="admin-style-stack admin-style-stack--brutalist">
        <div class="admin-style-stack-title">{{ authStore.projectDisplayName || '后台管理' }}</div>
        <nav class="admin-style-stack-nav">
          <router-link
            v-for="item in quickLinks"
            :key="`brutalist-${item.to}`"
            :to="item.to"
            class="admin-style-stack-link"
            :class="{ 'admin-style-stack-link--active': route.path === item.to }"
          >
            <span class="admin-style-stack-index">{{ shortLabel(item.label) }}</span>
            <span>{{ item.label }}</span>
          </router-link>
        </nav>
      </aside>
    </template>

    <template v-else-if="themeStore.currentStyleFamily === 'paper'">
      <header class="admin-style-editorial admin-style-editorial--paper">
        <div>
          <div class="admin-style-editorial-title">{{ authStore.projectDisplayName || '后台管理' }}</div>
        </div>
        <nav class="admin-style-editorial-nav">
          <router-link
            v-for="item in compactLinks"
            :key="`paper-${item.to}`"
            :to="item.to"
            class="admin-style-editorial-link"
            :class="{ 'admin-style-editorial-link--active': route.path === item.to }"
          >
            {{ item.label }}
          </router-link>
        </nav>
      </header>
    </template>

    <template v-else-if="themeStore.currentStyleFamily === 'neon'">
      <aside class="admin-style-hud admin-style-hud--neon">
        <div class="admin-style-hud-head">
          <span class="admin-style-hud-dot"></span>
          <div>
            <div class="admin-style-hud-title">{{ authStore.projectDisplayName || '后台管理' }}</div>
          </div>
        </div>
        <nav class="admin-style-hud-nav">
          <router-link
            v-for="item in quickLinks"
            :key="`neon-${item.to}`"
            :to="item.to"
            class="admin-style-hud-link"
            :class="{ 'admin-style-hud-link--active': route.path === item.to }"
          >
            <span class="admin-style-hud-link-mark"></span>
            <span>{{ item.label }}</span>
          </router-link>
        </nav>
      </aside>
    </template>

    <template v-else-if="themeStore.currentStyleFamily === 'zen'">
      <div
        class="admin-style-breath admin-style-breath--zen"
        :class="{ 'admin-style-breath--compact': isZenCompressed }"
      >
        <div class="admin-style-breath-title">{{ authStore.projectDisplayName || '后台管理' }}</div>
        <div class="admin-style-breath-divider"></div>
        <nav class="admin-style-breath-nav">
          <router-link
            v-for="item in quickLinks"
            :key="`zen-${item.to}`"
            :to="item.to"
            class="admin-style-breath-link"
            :class="{ 'admin-style-breath-link--active': route.path === item.to }"
          >
            {{ item.label }}
          </router-link>
        </nav>
      </div>
      <nav class="admin-style-bottom-nav admin-style-bottom-nav--zen">
        <router-link
          v-for="item in mobileLinks"
          :key="`zen-mobile-${item.to}`"
          :to="item.to"
          class="admin-style-bottom-nav-link"
          :class="{ 'admin-style-bottom-nav-link--active': route.path === item.to }"
        >
          <span>{{ item.label }}</span>
        </router-link>
      </nav>
    </template>

    <template v-else-if="themeStore.currentStyleFamily === 'terminal'">
      <div class="admin-style-console admin-style-console--terminal">
        <div class="admin-style-console-head">
          <span class="admin-style-console-prompt">$</span>
          <span>{{ authStore.projectDisplayName || '后台管理' }}</span>
        </div>
        <nav class="admin-style-console-nav">
          <router-link
            v-for="item in quickLinks"
            :key="`terminal-${item.to}`"
            :to="item.to"
            class="admin-style-console-link"
            :class="{ 'admin-style-console-link--active': route.path === item.to }"
          >
            {{ item.to.replace('/admin', '') || '/home' }}
          </router-link>
        </nav>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { useThemeStore } from '@/stores/theme'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const themeStore = useThemeStore()
const authStore = useAuthStore()
const isZenCompressed = ref(false)

const isLegacyStyle = computed(() => Object.prototype.hasOwnProperty.call(themeStore.styleFamilies, themeStore.currentStyleFamily))

const quickLinks = computed(() => {
  const links = [
    { to: '/admin', label: '首页' },
    { to: '/admin/file-browser', label: '文件' },
    { to: '/admin/settings', label: '设置' },
    { to: '/admin/theme', label: '主题' }
  ]
  if (authStore.isSuperAdmin) {
    links.push({ to: '/admin/super-admin', label: '超管' })
  }
  return links
})

const mobileLinks = computed(() => quickLinks.value.slice(0, authStore.isSuperAdmin ? 5 : 4))

const compactLinks = computed(() => quickLinks.value.slice(0, authStore.isSuperAdmin ? 4 : 3))

const shortLabel = (label: string) => label.slice(0, 2).toUpperCase()

const updateZenCompression = () => {
  isZenCompressed.value = themeStore.currentStyleFamily === 'zen' && window.scrollY > 24
}

onMounted(() => {
  updateZenCompression()
  window.addEventListener('scroll', updateZenCompression, { passive: true })
})

onUnmounted(() => {
  window.removeEventListener('scroll', updateZenCompression)
})
</script>
