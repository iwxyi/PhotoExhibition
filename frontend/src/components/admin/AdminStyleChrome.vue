<template>
  <div v-if="authStore.token && isLegacyStyle" class="admin-style-chrome" :data-style="themeStore.currentStyleFamily">
    <template v-if="themeStore.currentStyleFamily === 'material'">
      <aside class="admin-style-drawer admin-style-drawer--material">
        <div class="admin-style-drawer-head">
          <div class="admin-style-drawer-mark"></div>
          <div>
            <div class="admin-style-drawer-title">Workspace</div>
            <div class="admin-style-drawer-subtitle">{{ authStore.projectDisplayName || '后台管理' }}</div>
          </div>
        </div>
        <nav class="admin-style-drawer-nav">
          <router-link
            v-for="item in quickLinks"
            :key="`material-${item.to}`"
            :to="item.to"
            class="admin-style-drawer-link"
            :class="{ 'admin-style-drawer-link--active': route.path === item.to }"
          >
            <span class="admin-style-drawer-link-dot"></span>
            <span>{{ item.label }}</span>
          </router-link>
        </nav>
      </aside>

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
      <div class="admin-style-island admin-style-island--glass">
        <router-link
          v-for="item in compactLinks"
          :key="`glass-${item.to}`"
          :to="item.to"
          class="admin-style-island-link"
          :class="{ 'admin-style-island-link--active': route.path === item.to }"
        >
          {{ item.label }}
        </router-link>
      </div>
    </template>

    <template v-else-if="themeStore.currentStyleFamily === 'classic'">
      <div class="admin-style-ribbon admin-style-ribbon--classic">
        <div class="admin-style-ribbon-title">Enterprise Console</div>
        <div class="admin-style-ribbon-divider"></div>
        <div class="admin-style-ribbon-path">{{ currentLabel }}</div>
      </div>
    </template>

    <template v-else-if="themeStore.currentStyleFamily === 'gallery'">
      <aside class="admin-style-curator admin-style-curator--gallery">
        <div class="admin-style-curator-title">{{ currentLabel }}</div>
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
        <div class="admin-style-stack-label">Control Grid</div>
        <div class="admin-style-stack-title">{{ currentLabel }}</div>
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
          <div class="admin-style-editorial-kicker">Editorial Desk</div>
          <div class="admin-style-editorial-title">{{ currentLabel }}</div>
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
            <div class="admin-style-hud-kicker">Night Grid</div>
            <div class="admin-style-hud-title">{{ currentLabel }}</div>
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
      <div class="admin-style-breath admin-style-breath--zen">
        <div class="admin-style-breath-title">{{ currentLabel }}</div>
        <div class="admin-style-breath-divider"></div>
        <nav class="admin-style-breath-nav">
          <router-link
            v-for="item in compactLinks"
            :key="`zen-${item.to}`"
            :to="item.to"
            class="admin-style-breath-link"
            :class="{ 'admin-style-breath-link--active': route.path === item.to }"
          >
            {{ item.label }}
          </router-link>
        </nav>
      </div>
    </template>

    <template v-else-if="themeStore.currentStyleFamily === 'terminal'">
      <div class="admin-style-console admin-style-console--terminal">
        <div class="admin-style-console-head">
          <span class="admin-style-console-prompt">$</span>
          <span>{{ currentLabel }}</span>
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
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useThemeStore } from '@/stores/theme'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const themeStore = useThemeStore()
const authStore = useAuthStore()

const isLegacyStyle = computed(() => ['material', 'glass', 'classic', 'gallery', 'compact'].includes(themeStore.currentStyleFamily))

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

const currentLabel = computed(() => {
  const matched = quickLinks.value.find((item) => route.path === item.to)
  if (matched) return matched.label
  if (route.path.startsWith('/admin/super-admin')) return '超管'
  if (route.path.startsWith('/admin/file-browser')) return '文件浏览器'
  if (route.path.startsWith('/admin/settings')) return '系统设置'
  if (route.path.startsWith('/admin/theme')) return '后台主题'
  return '后台管理'
})

const shortLabel = (label: string) => label.slice(0, 2).toUpperCase()
</script>
