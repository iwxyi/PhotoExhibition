<template>
  <nav class="admin-tabbar admin-super-admin-tabbar admin-super-admin-tabbar-shell" aria-label="超级管理模块">
    <button
      v-for="tab in tabs"
      :key="tab.key"
      type="button"
      class="admin-tab-pill"
      :class="activeTab === tab.key ? 'admin-tab-pill-active' : ''"
      :aria-current="activeTab === tab.key ? 'page' : undefined"
      @click="$emit('change', tab.key)"
    >
      <span class="admin-tab-pill-label">{{ tab.label }}</span>
    </button>
  </nav>
</template>

<script setup lang="ts">
defineProps<{
  tabs: ReadonlyArray<{ key: string; label: string }>
  activeTab: string
}>()

defineEmits<{ change: [key: string] }>()
</script>
