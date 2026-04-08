<template>
  <div v-if="authStore.isSuperAdmin" class="admin-switcher">
    <router-link
      to="/admin"
      class="admin-switcher-link"
      :class="isDashboard
        ? 'admin-switcher-link-active admin-switcher-link-dashboard'
        : 'admin-switcher-link-idle'"
    >
      <span class="admin-switcher-link-title">后台管理</span>
      <span class="admin-switcher-link-subtitle">相册、照片与个人数据</span>
    </router-link>
    <router-link
      v-if="authStore.isSuperAdmin"
      to="/admin/super-admin"
      class="admin-switcher-link"
      :class="isSuperAdmin
        ? 'admin-switcher-link-active admin-switcher-link-super'
        : 'admin-switcher-link-idle'"
    >
      <span class="admin-switcher-link-title">超级管理员</span>
      <span class="admin-switcher-link-subtitle">平台配置、用户与企业级工具</span>
    </router-link>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const authStore = useAuthStore()

const isDashboard = computed(() => route.path === '/admin')
const isSuperAdmin = computed(() => route.path === '/admin/super-admin')
</script>
