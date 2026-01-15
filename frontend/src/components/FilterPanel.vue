<template>
  <div>
    <button
      @click="show = !show"
      class="p-2 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-800 transition-all duration-200 hover:scale-110 hover:shadow-md transform-gpu group relative overflow-hidden"
    >
      <svg class="w-5 h-5 transition-all duration-300 group-hover:rotate-12 group-hover:scale-110" fill="none" stroke="currentColor" viewBox="0 0 24 24">
        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M3 4a1 1 0 011-1h16a1 1 0 011 1v2.586a1 1 0 01-.293.707l-6.414 6.414a1 1 0 00-.293.707V17l-4 4v-6.586a1 1 0 00-.293-.707L3.293 7.293A1 1 0 013 6.586V4z" />
      </svg>
      <div class="absolute inset-0 bg-gradient-to-r from-cyan-500/10 to-blue-500/10 opacity-0 group-hover:opacity-100 transition-opacity duration-200 rounded-lg"></div>
    </button>

    <Teleport to="body">
      <!-- 遮罩层 -->
      <div
        v-if="show"
        class="fixed inset-0 z-[2000] bg-black/5 cursor-pointer"
        :class="{ 'opacity-0': !animating, 'opacity-100': animating }"
        style="transition: opacity 0.3s ease;"
        @click="closePanel"
      ></div>

      <!-- 弹窗 -->
      <div
        v-if="show"
        class="fixed inset-0 z-[2100] flex items-center justify-center pointer-events-none"
      >
        <div
          ref="panelRef"
          class="filter-panel-glass pointer-events-auto p-6 max-w-2xl w-full mx-4 max-h-[90vh] overflow-y-auto"
          :class="{ 'opacity-0 scale-95': !animating, 'opacity-100 scale-100': animating }"
          style="transition: all 0.3s ease;"
          @click.stop
        >
          <div class="flex justify-between items-center mb-6">
            <h2 class="text-2xl font-light">高级筛选</h2>
            <button
              @click="closePanel"
              class="p-2 hover:bg-gray-100 dark:hover:bg-gray-700 rounded-lg transition-colors"
            >
              <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
              </svg>
            </button>
          </div>

          <form @submit.prevent="applyFilters" class="space-y-6">
            <!-- 标签筛选 -->
            <div>
              <label class="block text-sm font-medium mb-2">标签</label>
              <div class="flex flex-wrap gap-2">
                <span
                  v-for="tag in selectedTags"
                  :key="tag.id"
                  class="px-3 py-1 bg-gray-200 dark:bg-gray-700 rounded-full text-sm flex items-center gap-2"
                >
                  {{ tag.name }}
                  <button
                    @click="removeTag(tag.id)"
                    class="hover:text-red-500"
                  >
                    ×
                  </button>
                </span>
              </div>
            </div>

            <!-- EXIF筛选 -->
            <div class="grid grid-cols-2 gap-4">
              <div>
                <label class="block text-sm font-medium mb-2">相机型号</label>
                <input
                  v-model="filters.cameraModel"
                  type="text"
                  class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 focus:ring-2 focus:ring-gray-900 dark:focus:ring-white"
                />
              </div>
              <div>
                <label class="block text-sm font-medium mb-2">镜头型号</label>
                <input
                  v-model="filters.lensModel"
                  type="text"
                  class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 focus:ring-2 focus:ring-gray-900 dark:focus:ring-white"
                />
              </div>
              <div>
                <label class="block text-sm font-medium mb-2">最小光圈</label>
                <input
                  v-model.number="filters.minAperture"
                  type="number"
                  step="0.1"
                  class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 focus:ring-2 focus:ring-gray-900 dark:focus:ring-white"
                />
              </div>
              <div>
                <label class="block text-sm font-medium mb-2">最大光圈</label>
                <input
                  v-model.number="filters.maxAperture"
                  type="number"
                  step="0.1"
                  class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 focus:ring-2 focus:ring-gray-900 dark:focus:ring-white"
                />
              </div>
            </div>

            <!-- 色彩筛选 -->
            <div>
              <label class="block text-sm font-medium mb-2">主色调</label>
              <input
                v-model="filters.dominantColor"
                type="color"
                class="h-10 w-full rounded-lg cursor-pointer"
              />
            </div>

            <!-- 质量评分 -->
            <div>
              <label class="block text-sm font-medium mb-2">最小质量评分: {{ filters.minQualityScore || 0 }}</label>
              <input
                v-model.number="filters.minQualityScore"
                type="range"
                min="0"
                max="100"
                step="5"
                class="w-full"
              />
            </div>

            <div class="flex justify-end space-x-4 pt-4">
              <button
                type="button"
                @click="resetFilters"
                class="px-6 py-2 border border-gray-300 dark:border-gray-600 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-700 transition-colors"
              >
                重置
              </button>
              <button
                type="submit"
                class="px-6 py-2 bg-gray-900 dark:bg-white text-white dark:text-gray-900 rounded-lg hover:bg-gray-800 dark:hover:bg-gray-100 transition-colors"
              >
                应用筛选
              </button>
            </div>
          </form>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, nextTick } from 'vue'
import { usePhotoStore } from '@/stores/photo'

const props = defineProps<{
  show: boolean
}>()

const emit = defineEmits<{
  'update:show': [value: boolean]
}>()

const photoStore = usePhotoStore()
const show = ref(props.show)
const animating = ref(false)
const panelRef = ref<HTMLElement>()
const selectedTags = ref<any[]>([])
const filters = ref({
  cameraModel: '',
  lensModel: '',
  minAperture: null as number | null,
  maxAperture: null as number | null,
  dominantColor: '',
  minQualityScore: 0
})

watch(() => props.show, async (val) => {
  if (val) {
    // 显示弹窗
    show.value = true
    await nextTick()
    // 延迟一帧开始动画，确保DOM已渲染
    requestAnimationFrame(() => {
      animating.value = true
    })
  } else {
    // 隐藏弹窗
    animating.value = false
    // 等待动画完成后隐藏
    setTimeout(() => {
      show.value = false
    }, 300)
  }
})

watch(show, (val) => {
  emit('update:show', val)
})

const closePanel = () => {
  animating.value = false
  setTimeout(() => {
    show.value = false
  }, 300)
}

const removeTag = (tagId: number) => {
  selectedTags.value = selectedTags.value.filter(t => t.id !== tagId)
}

const applyFilters = async () => {
  const filterData = {
    ...filters.value,
    tagIds: selectedTags.value.map(t => t.id)
  }
  await photoStore.filterPhotos(filterData)
  closePanel()
}

const resetFilters = () => {
  filters.value = {
    cameraModel: '',
    lensModel: '',
    minAperture: null,
    maxAperture: null,
    dominantColor: '',
    minQualityScore: 0
  }
  selectedTags.value = []
}
</script>

<style scoped>
/* 毛玻璃效果 - 从一开始就生效 */
:deep(.filter-panel-glass) {
  border-radius: 16px;
  border: 1px solid rgba(148, 163, 184, 0.35);
  box-shadow: 0 18px 45px rgba(15, 23, 42, 0.18);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  background: rgba(248, 250, 252, 0.7);
}

.dark :deep(.filter-panel-glass) {
  background: rgba(15, 23, 42, 0.7);
  border-color: rgba(148, 163, 184, 0.35);
  box-shadow: 0 22px 55px rgba(0, 0, 0, 0.75);
}
</style>

