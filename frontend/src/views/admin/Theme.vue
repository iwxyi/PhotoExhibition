<template>
  <div class="min-h-screen admin-shell admin-theme-page">
    <AdminStyleChrome />
    <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8 space-y-8 admin-theme-shell">
      <section class="admin-page-hero admin-theme-hero">
        <div class="admin-page-hero-grid">
          <div class="space-y-4">
            <div class="admin-page-hero-badge">主题与风格</div>
            <div class="space-y-2">
              <h1 class="admin-page-title">后台主题</h1>
              <p class="admin-page-subtitle">这套风格会同步应用到普通后台和超管后台，并支持跨标签页实时刷新。</p>
            </div>
            <div class="admin-theme-hero-metrics">
              <div class="admin-theme-hero-metric">
                <span>当前风格</span>
                <strong>{{ styleFamilyName }}</strong>
              </div>
              <div class="admin-theme-hero-metric">
                <span>显示模式</span>
                <strong>{{ themeStore.isDark ? '深色模式' : '浅色模式' }}</strong>
              </div>
              <div class="admin-theme-hero-metric">
                <span>当前主题</span>
                <strong>{{ currentThemeName }}</strong>
              </div>
            </div>
          </div>
          <div class="flex justify-end items-start">
            <router-link
              to="/admin"
              class="admin-button-contrast rounded-lg px-4 py-2 text-sm transition-colors"
            >
              返回后台首页
            </router-link>
          </div>
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
