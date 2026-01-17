<template>
  <!-- 分类 Tabs -->
  <div class="mb-6">
    <div
      class="flex gap-3 overflow-x-auto pb-2 px-1 py-1 scroll-smooth category-tabs-container"
      style="scrollbar-width: none; -ms-overflow-style: none;"
    >
          <button
            v-for="c in ['全部', ...categories]"
            :key="c"
            @click="handleCategoryClick(c)"
            class="flex-shrink-0 px-4 py-2 rounded-full border transition-all duration-200 hover:scale-105 hover:shadow-sm transform-gpu group relative overflow-hidden font-medium text-sm whitespace-nowrap"
            style="transform-origin: center; will-change: transform;"
            :class="c === selectedCategory
              ? 'bg-gray-900 text-white border-gray-800 dark:bg-white dark:text-gray-900 dark:border-white shadow-lg ring-2 ring-gray-900/20 dark:ring-white/20 scale-102'
              : 'bg-gray-100 text-gray-800 border-gray-300 hover:bg-gray-200 dark:bg-gray-800 dark:text-gray-100 dark:border-gray-700 dark:hover:bg-gray-700'"
          >
            <span class="relative z-10 transition-transform duration-200 group-hover:scale-105">{{ c }}</span>
            <div
              v-if="c === selectedCategory"
              class="absolute inset-0 bg-gradient-to-r from-blue-500/20 to-purple-500/20 rounded-full transition-all duration-300 animate-pulse"
            ></div>
            <div class="absolute inset-0 bg-gradient-to-r from-gray-500/10 to-gray-600/10 opacity-0 group-hover:opacity-100 transition-opacity duration-200 rounded-full"></div>
          </button>
    </div>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  selectedCategory: string
  categories: string[]
}>()

const emit = defineEmits<{
  'category-changed': [category: string]
}>()

const handleCategoryClick = (category: string) => {
  emit('category-changed', category)
}
</script>

<style scoped>
/* 隐藏分类标签容器的滚动条 */
.category-tabs-container::-webkit-scrollbar {
  display: none;
}
</style>
