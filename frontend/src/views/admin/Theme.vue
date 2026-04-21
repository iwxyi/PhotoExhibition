<template>
  <div class="min-h-screen admin-shell admin-theme-page">
    <AdminStyleChrome />
    <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-3 space-y-3 admin-theme-shell">
      <section class="admin-page-header admin-theme-header">
        <div class="admin-page-header-copy">
          <h1 class="admin-page-title">后台主题</h1>
          <p class="admin-page-subtitle">{{ styleFamilyName }} · {{ themeStore.isDark ? '深色模式' : '浅色模式' }} · {{ currentThemeName }}</p>
        </div>
      </section>

      <AdminThemeSettingsPanel />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import AdminStyleChrome from '@/components/admin/AdminStyleChrome.vue'
import AdminThemeSettingsPanel from '@/components/admin/AdminThemeSettingsPanel.vue'
import { useThemeStore } from '@/stores/theme'

const themeStore = useThemeStore()

const styleFamilyName = computed(() => {
  return themeStore.styleFamilies[themeStore.currentStyleFamily]?.name || themeStore.currentStyleFamily
})

const currentThemeName = computed(() => {
  return themeStore.themes[themeStore.currentThemeKey]?.name || themeStore.currentThemeKey
})
</script>
