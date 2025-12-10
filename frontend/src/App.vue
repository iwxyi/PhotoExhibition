<template>
  <div id="app" :class="{ 'dark': isDark }">
    <router-view />
  </div>
</template>

<script setup lang="ts">
import { useThemeStore } from '@/stores/theme'
import { computed } from 'vue'

const themeStore = useThemeStore()
const isDark = computed(() => themeStore.isDark)
</script>

<style>
#app {
  min-height: 100vh;
}
</style>

