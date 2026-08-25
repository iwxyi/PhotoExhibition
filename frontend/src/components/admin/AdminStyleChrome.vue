<template>
  <nav v-if="authStore.token" class="admin-utility-nav" aria-label="管理导航">
    <div class="admin-utility-nav__inner">
      <router-link to="/admin" class="admin-utility-nav__brand">管理台</router-link>
      <div class="admin-utility-nav__links">
        <router-link
          v-for="link in links"
          :key="link.to"
          :to="link.to"
          class="admin-utility-nav__link"
          :class="{ 'admin-utility-nav__link--active': route.path === link.to }"
        >
          {{ link.label }}
        </router-link>
      </div>
      <div class="admin-utility-nav__actions">
        <div class="admin-color-mode-control" role="group" aria-label="颜色模式">
          <button
            v-for="mode in colorModes"
            :key="mode.value"
            type="button"
            class="admin-color-mode-control__option"
            :class="{ 'admin-color-mode-control__option--active': themeStore.adminColorMode === mode.value }"
            @click="themeStore.setAdminColorMode(mode.value)"
          >{{ mode.label }}</button>
        </div>
        <button type="button" class="admin-utility-nav__logout" @click="logout">退出</button>
      </div>
    </div>
  </nav>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useThemeStore } from '@/stores/theme'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const themeStore = useThemeStore()

const colorModes = [
  { value: 'light' as const, label: '浅' },
  { value: 'dark' as const, label: '深' },
  { value: 'system' as const, label: '自动' }
]

const links = computed(() => {
  const items = [
    { to: '/admin', label: '概览' },
    { to: '/admin/file-browser', label: '文件' },
    { to: '/admin/albums', label: '相册' },
    { to: '/admin/photos', label: '照片' },
    { to: '/admin/persons', label: '人物' },
    { to: '/admin/settings', label: '设置' }
  ]
  if (authStore.isSuperAdmin) items.push({ to: '/admin/super-admin', label: '超管' })
  return items
})

const logout = () => {
  authStore.logout()
  router.push('/admin/login')
}
</script>
