<template>
  <div class="flex items-center space-x-2">
    <router-link 
      to="/" 
      class="inline-flex items-center justify-center w-[86px] text-sm font-medium px-3 py-2 rounded border border-gray-400/50 dark:border-gray-600 transition-colors whitespace-nowrap text-center"
      :class="linkClass('/')"
    >
      相册
    </router-link>
    <router-link 
      to="/wall" 
      class="inline-flex items-center justify-center w-[86px] text-sm font-medium px-3 py-2 rounded border border-gray-400/50 dark:border-gray-600 transition-colors whitespace-nowrap text-center"
      :class="linkClass('/wall')"
    >
      图墙
    </router-link>
    <router-link 
      to="/random" 
      class="inline-flex items-center justify-center w-[86px] text-sm font-medium px-3 py-2 rounded border border-gray-400/50 dark:border-gray-600 transition-colors whitespace-nowrap text-center"
      :class="linkClass('/random')"
    >
      随机
    </router-link>
  </div>
</template>

<script setup lang="ts">
import { useRoute } from 'vue-router'

const route = useRoute()

const linkClass = (path: string) => {
  const active = route.path === path
  return active
    ? 'bg-gray-900 text-white dark:bg-white dark:text-gray-900'
    : 'text-gray-700 dark:text-gray-300 bg-gray-100/60 dark:bg-gray-800/60 hover:bg-gray-200 dark:hover:bg-gray-700'
}
</script>

