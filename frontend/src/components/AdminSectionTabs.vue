<template>
  <div class="flex items-center gap-3 flex-wrap">
    <router-link
      to="/admin"
      class="px-4 py-2 rounded-xl text-sm border transition-colors"
      :class="isDashboard
        ? 'bg-blue-600 text-white border-blue-500/40'
        : 'bg-gray-900/60 hover:bg-gray-800 text-gray-200 border-white/10'"
    >
      后台管理
    </router-link>
    <router-link
      v-if="authStore.isSuperAdmin"
      to="/admin/super-admin"
      class="px-4 py-2 rounded-xl text-sm border transition-colors"
      :class="isSuperAdmin
        ? 'bg-purple-600 text-white border-purple-500/40'
        : 'bg-gray-900/60 hover:bg-gray-800 text-gray-200 border-white/10'"
    >
      超级管理员
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
