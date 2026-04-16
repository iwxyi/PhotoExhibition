<template>
  <div v-if="authStore.isSuperAdmin" class="admin-section-tabs flex items-center gap-2">
    <router-link
      to="/admin"
      class="admin-section-tab-link rounded-full border px-3 py-1.5 text-xs transition-colors"
      :class="isDashboard
        ? 'admin-tab-button--active'
        : 'admin-tab-button'"
    >
      <span class="admin-section-tab-icon"></span>
      后台
    </router-link>
    <router-link
      v-if="authStore.isSuperAdmin"
      to="/admin/super-admin"
      class="admin-section-tab-link rounded-full border px-3 py-1.5 text-xs transition-colors"
      :class="isSuperAdmin
        ? 'admin-tab-button--active'
        : 'admin-tab-button'"
    >
      <span class="admin-section-tab-icon"></span>
      超管
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
