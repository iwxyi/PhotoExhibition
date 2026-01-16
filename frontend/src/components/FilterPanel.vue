<template>
  <div>
    <button
      @click="onTogglePanel"
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
          class="filter-panel-glass pointer-events-auto p-0 max-w-2xl w-full mx-4 max-h-[90vh] flex flex-col"
          :class="{ 'opacity-0 scale-95': !animating, 'opacity-100 scale-100': animating }"
          style="transition: all 0.3s ease;"
            @click.stop
          >
          <!-- 头部区域 -->
          <div class="flex justify-between items-center p-6 pb-4">
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

          <!-- 可滚动内容区域 -->
          <div class="flex-1 overflow-y-auto px-6">
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

            <!-- 设备信息 -->
              <div class="grid grid-cols-2 gap-4">
                <div>
                  <label class="block text-sm font-medium mb-2">相机型号</label>
                <select
                    v-model="filters.cameraModel"
                    class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 focus:ring-2 focus:ring-gray-900 dark:focus:ring-white"
                >
                  <option value="">全部</option>
                  <option v-for="model in filterOptions.cameraModels" :key="model.name" :value="model.name">
                    {{ model.name }} ({{ model.count }})
                  </option>
                </select>
                </div>
                <div>
                  <label class="block text-sm font-medium mb-2">镜头型号</label>
                <select
                    v-model="filters.lensModel"
                    class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 focus:ring-2 focus:ring-gray-900 dark:focus:ring-white"
                >
                  <option value="">全部</option>
                  <option v-for="model in filterOptions.lensModels" :key="model.name" :value="model.name">
                    {{ model.name }} ({{ model.count }})
                  </option>
                </select>
              </div>
            </div>

            <!-- 焦距范围 -->
            <div>
              <label class="block text-sm font-medium mb-2">
                焦距: {{ filters.focalLengthRange[0] === null ? '不限' : formatFocalLength(filters.focalLengthRange[0]) }} - {{ filters.focalLengthRange[1] === null ? '不限' : formatFocalLength(filters.focalLengthRange[1]) }}{{ filters.focalLengthRange[0] !== null && filters.focalLengthRange[1] !== null ? 'mm' : '' }}
              </label>
              <div class="relative px-2 py-2">
                <!-- 背景线 -->
                <div class="absolute top-1/2 left-0 right-0 h-1 bg-gray-300 dark:bg-gray-600 rounded-full transform -translate-y-1/2"></div>

                <!-- 高亮范围线 -->
                <div
                  class="absolute top-1/2 h-1 bg-blue-500 rounded-full transform -translate-y-1/2"
                  :style="{
                    left: getPositionPercent(filters.focalLengthRange[0], filterOptions.focalLengthRange[0] || 0, filterOptions.focalLengthRange[1] || 1000, 'focalLength', true) + '%',
                    right: (100 - getPositionPercent(filters.focalLengthRange[1], filterOptions.focalLengthRange[0] || 0, filterOptions.focalLengthRange[1] || 1000, 'focalLength', false)) + '%'
                  }"
                ></div>

                <!-- 吸附点标记 -->
                <div
                  v-for="(mark, index) in focalLengthMarks"
                  :key="'focal-' + mark"
                  class="absolute top-1/2 w-1.5 h-1.5 bg-white dark:bg-gray-800 border border-blue-500 rounded-full transform -translate-x-1/2 -translate-y-1/2 z-10"
                  :style="{ left: (index / (focalLengthMarks.length - 1)) * 100 + '%' }"
                  v-show="index >= getFocalLengthStartIndex() && index <= getFocalLengthEndIndex()"
                ></div>

                <!-- 最小值滑块 -->
                <div
                  class="absolute top-1/2 w-4 h-4 bg-white dark:bg-gray-800 border-2 border-blue-500 rounded-full shadow-lg cursor-pointer transform -translate-x-1/2 -translate-y-1/2 z-20"
                  :style="{ left: getPositionPercent(filters.focalLengthRange[0], filterOptions.focalLengthRange[0] || 0, filterOptions.focalLengthRange[1] || 1000, 'focalLength', true) + '%' }"
                  @mousedown="startDrag($event, 'focalLength', 0)"
                ></div>

                <!-- 最大值滑块 -->
                <div
                  class="absolute top-1/2 w-4 h-4 bg-white dark:bg-gray-800 border-2 border-blue-500 rounded-full shadow-lg cursor-pointer transform -translate-x-1/2 -translate-y-1/2 z-20"
                  :style="{ left: getPositionPercent(filters.focalLengthRange[1], filterOptions.focalLengthRange[0] || 0, filterOptions.focalLengthRange[1] || 1000, 'focalLength', false) + '%' }"
                  @mousedown="startDrag($event, 'focalLength', 1)"
                ></div>

                <!-- 刻度标记 -->
                <div class="relative mt-5">
                  <div class="text-xs text-gray-500" ref="focalMarksRef">
                    <span v-for="(mark, index) in focalLengthMarks" :key="mark"
                          v-show="index >= getFocalLengthStartIndex() && index <= getFocalLengthEndIndex() && visibleFocal.has(index)"
                          class="absolute text-center"
                          :style="{ left: 'calc(' + (index / (focalLengthMarks.length - 1)) * 100 + '% + ' + getMarkOffset(index, focalLengthMarks) + 'px)', transform: 'translateX(-50%)' }">
                      {{ mark === '不限' ? '∞' : mark }}
                    </span>
                  </div>
                </div>
              </div>
            </div>

            <!-- 快门速度范围 -->
            <div>
              <label class="block text-sm font-medium mb-2">
                快门速度: {{ filters.shutterSpeedRange[0] === null ? '不限' : formatShutterSpeed(filters.shutterSpeedRange[0]) }} - {{ filters.shutterSpeedRange[1] === null ? '不限' : formatShutterSpeed(filters.shutterSpeedRange[1]) }}
              </label>
              <div class="relative px-2 py-2">
                <!-- 背景线 -->
                <div class="absolute top-1/2 left-0 right-0 h-1 bg-gray-300 dark:bg-gray-600 rounded-full transform -translate-y-1/2"></div>

                <!-- 高亮范围线 -->
                <div
                  class="absolute top-1/2 h-1 bg-green-500 rounded-full transform -translate-y-1/2"
                  :style="{
                    left: getPositionPercent(filters.shutterSpeedRange[0], filterOptions.shutterSpeedRange[0] || 0.0001, filterOptions.shutterSpeedRange[1] || 60, 'shutterSpeed', true) + '%',
                    right: (100 - getPositionPercent(filters.shutterSpeedRange[1], filterOptions.shutterSpeedRange[0] || 0.0001, filterOptions.shutterSpeedRange[1] || 60, 'shutterSpeed', false)) + '%'
                  }"
                ></div>

                <!-- 吸附点标记 -->
                <div
                  v-for="(mark, index) in shutterSpeedMarks"
                  :key="'shutter-' + mark"
                  class="absolute top-1/2 w-1.5 h-1.5 bg-white dark:bg-gray-800 border border-green-500 rounded-full transform -translate-x-1/2 -translate-y-1/2 z-10"
                  :style="{ left: (index / (shutterSpeedMarks.length - 1)) * 100 + '%' }"
                  v-show="index >= getShutterSpeedStartIndex() && index <= getShutterSpeedEndIndex()"
                ></div>

                <!-- 最小值滑块 -->
                <div
                  class="absolute top-1/2 w-4 h-4 bg-white dark:bg-gray-800 border-2 border-green-500 rounded-full shadow-lg cursor-pointer transform -translate-x-1/2 -translate-y-1/2 z-20"
                  :style="{ left: getPositionPercent(filters.shutterSpeedRange[0], filterOptions.shutterSpeedRange[0] || 0.0001, filterOptions.shutterSpeedRange[1] || 60, 'shutterSpeed', true) + '%' }"
                  @mousedown="startDrag($event, 'shutterSpeed', 0)"
                ></div>

                <!-- 最大值滑块 -->
                <div
                  class="absolute top-1/2 w-4 h-4 bg-white dark:bg-gray-800 border-2 border-green-500 rounded-full shadow-lg cursor-pointer transform -translate-x-1/2 -translate-y-1/2 z-20"
                  :style="{ left: getPositionPercent(filters.shutterSpeedRange[1], filterOptions.shutterSpeedRange[0] || 0.0001, filterOptions.shutterSpeedRange[1] || 60, 'shutterSpeed', false) + '%' }"
                  @mousedown="startDrag($event, 'shutterSpeed', 1)"
                ></div>

                <!-- 刻度标记 -->
                <div class="relative mt-5">
                  <div class="text-xs text-gray-500" ref="shutterMarksRef">
                    <span v-for="(mark, index) in shutterSpeedMarks" :key="mark"
                          v-show="index >= getShutterSpeedStartIndex() && index <= getShutterSpeedEndIndex() && visibleShutter.has(index)"
                          class="absolute text-center"
                          :style="{ left: 'calc(' + (index / (shutterSpeedMarks.length - 1)) * 100 + '% + ' + getMarkOffset(index, shutterSpeedMarks) + 'px)', transform: 'translateX(-50%)' }">
                      {{ mark === '不限' ? '∞' : (shutterHiddenIndexes.includes(index) ? '' : displayShutterLabel(mark)) }}
                    </span>
                  </div>
                </div>
              </div>
            </div>

            <!-- 光圈范围 -->
                <div>
              <label class="block text-sm font-medium mb-2">
                光圈: {{ filters.apertureRange[0] === null ? '不限' : 'f/' + formatAperture(filters.apertureRange[0]) }} - {{ filters.apertureRange[1] === null ? '不限' : 'f/' + formatAperture(filters.apertureRange[1]) }}
              </label>
              <div class="relative px-2 py-2">
                <!-- 背景线 -->
                <div class="absolute top-1/2 left-0 right-0 h-1 bg-gray-300 dark:bg-gray-600 rounded-full transform -translate-y-1/2"></div>

                <!-- 高亮范围线 -->
                <div
                  class="absolute top-1/2 h-1 bg-purple-500 rounded-full transform -translate-y-1/2"
                  :style="{
                    left: getPositionPercent(filters.apertureRange[0], filterOptions.apertureRange[0] || 1.0, filterOptions.apertureRange[1] || 32.0, 'aperture', true) + '%',
                    right: (100 - getPositionPercent(filters.apertureRange[1], filterOptions.apertureRange[0] || 1.0, filterOptions.apertureRange[1] || 32.0, 'aperture', false)) + '%'
                  }"
                ></div>

                <!-- 吸附点标记 -->
                <div
                  v-for="(mark, index) in apertureMarks"
                  :key="'aperture-' + mark"
                  class="absolute top-1/2 w-1.5 h-1.5 bg-white dark:bg-gray-800 border border-purple-500 rounded-full transform -translate-x-1/2 -translate-y-1/2 z-10"
                  :style="{ left: (index / (apertureMarks.length - 1)) * 100 + '%' }"
                  v-show="index >= getApertureStartIndex() && index <= getApertureEndIndex()"
                ></div>

                <!-- 最小值滑块 -->
                <div
                  class="absolute top-1/2 w-4 h-4 bg-white dark:bg-gray-800 border-2 border-purple-500 rounded-full shadow-lg cursor-pointer transform -translate-x-1/2 -translate-y-1/2 z-20"
                  :style="{ left: getPositionPercent(filters.apertureRange[0], filterOptions.apertureRange[0] || 1.0, filterOptions.apertureRange[1] || 32.0, 'aperture', true) + '%' }"
                  @mousedown="startDrag($event, 'aperture', 0)"
                ></div>

                <!-- 最大值滑块 -->
                <div
                  class="absolute top-1/2 w-4 h-4 bg-white dark:bg-gray-800 border-2 border-purple-500 rounded-full shadow-lg cursor-pointer transform -translate-x-1/2 -translate-y-1/2 z-20"
                  :style="{ left: getPositionPercent(filters.apertureRange[1], filterOptions.apertureRange[0] || 1.0, filterOptions.apertureRange[1] || 32.0, 'aperture', false) + '%' }"
                  @mousedown="startDrag($event, 'aperture', 1)"
                ></div>

                <!-- 刻度标记 -->
                <div class="relative mt-5">
                  <div class="text-xs text-gray-500" ref="apertureMarksRef">
                    <span v-for="(mark, index) in apertureMarks" :key="mark"
                          v-show="index >= getApertureStartIndex() && index <= getApertureEndIndex() && visibleAperture.has(index)"
                          class="absolute text-center"
                          :style="{ left: 'calc(' + (index / (apertureMarks.length - 1)) * 100 + '% + ' + getMarkOffset(index, apertureMarks) + 'px)', transform: 'translateX(-50%)' }">
                      {{ mark === '不限' ? '∞' : 'f/' + mark }}
                    </span>
                  </div>
                </div>
              </div>
            </div>

            <!-- ISO范围 -->
                <div>
              <label class="block text-sm font-medium mb-2">
                ISO: {{ filters.isoRange[0] === null ? '不限' : filters.isoRange[0] }} - {{ filters.isoRange[1] === null ? '不限' : filters.isoRange[1] }}
              </label>
              <div class="relative px-2 py-2">
                <!-- 背景线 -->
                <div class="absolute top-1/2 left-0 right-0 h-1 bg-gray-300 dark:bg-gray-600 rounded-full transform -translate-y-1/2"></div>

                <!-- 高亮范围线 -->
                <div
                  class="absolute top-1/2 h-1 bg-orange-500 rounded-full transform -translate-y-1/2"
                  :style="{
                    left: getPositionPercent(filters.isoRange[0], filterOptions.isoRange[0] || 50, filterOptions.isoRange[1] || 25600, 'iso', true) + '%',
                    right: (100 - getPositionPercent(filters.isoRange[1], filterOptions.isoRange[0] || 50, filterOptions.isoRange[1] || 25600, 'iso', false)) + '%'
                  }"
                ></div>

                <!-- 吸附点标记 -->
                <div
                  v-for="(mark, index) in isoMarks"
                  :key="'iso-' + mark"
                  class="absolute top-1/2 w-1.5 h-1.5 bg-white dark:bg-gray-800 border border-orange-500 rounded-full transform -translate-x-1/2 -translate-y-1/2 z-10"
                  :style="{ left: (index / (isoMarks.length - 1)) * 100 + '%' }"
                  v-show="index >= getIsoStartIndex() && index <= getIsoEndIndex()"
                ></div>

                <!-- 最小值滑块 -->
                <div
                  class="absolute top-1/2 w-4 h-4 bg-white dark:bg-gray-800 border-2 border-orange-500 rounded-full shadow-lg cursor-pointer transform -translate-x-1/2 -translate-y-1/2 z-20"
                  :style="{ left: getPositionPercent(filters.isoRange[0], filterOptions.isoRange[0] || 50, filterOptions.isoRange[1] || 25600, 'iso', true) + '%' }"
                  @mousedown="startDrag($event, 'iso', 0)"
                ></div>

                <!-- 最大值滑块 -->
                <div
                  class="absolute top-1/2 w-4 h-4 bg-white dark:bg-gray-800 border-2 border-orange-500 rounded-full shadow-lg cursor-pointer transform -translate-x-1/2 -translate-y-1/2 z-20"
                  :style="{ left: getPositionPercent(filters.isoRange[1], filterOptions.isoRange[0] || 50, filterOptions.isoRange[1] || 25600, 'iso', false) + '%' }"
                  @mousedown="startDrag($event, 'iso', 1)"
                ></div>

                <!-- 刻度标记 -->
                <div class="relative mt-5">
                  <div class="text-xs text-gray-500" ref="isoMarksRef">
                    <span v-for="(mark, index) in isoMarks" :key="mark"
                          v-show="index >= getIsoStartIndex() && index <= getIsoEndIndex() && visibleIso.has(index)"
                          class="absolute text-center"
                          :style="{ left: 'calc(' + (index / (isoMarks.length - 1)) * 100 + '% + ' + getMarkOffset(index, isoMarks) + 'px)', transform: 'translateX(-50%)' }">
                      {{ mark === '不限' ? '∞' : (isoHiddenIndexes.includes(index) ? '' : displayIsoLabel(mark)) }}
                    </span>
                  </div>
                </div>
                </div>
              </div>

              <!-- 色彩筛选 -->
              <div>
                <label class="block text-sm font-medium mb-2">主色调</label>
                <input
                :value="filters.dominantColor || '#ffffff'"
                @input="filters.dominantColor = $event.target.value !== '#ffffff' ? $event.target.value : null"
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

            </form>
          </div>

          <!-- 固定底部按钮区域 -->
          <div class="flex justify-end space-x-4 p-6 pt-4 rounded-b-2xl">
                <button
                  type="button"
                  @click="resetFilters"
                  class="px-6 py-2 border border-gray-300 dark:border-gray-600 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-700 transition-colors"
                >
                  重置
                </button>
                <button
              type="button"
              @click="applyFilters"
                  class="px-6 py-2 bg-gray-900 dark:bg-white text-white dark:text-gray-900 rounded-lg hover:bg-gray-800 dark:hover:bg-gray-100 transition-colors"
                >
                  应用筛选
                </button>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, nextTick, onMounted, onBeforeUnmount } from 'vue'
import { usePhotoStore } from '@/stores/photo'
import { api } from '@/api'

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

// 筛选选项数据
const filterOptions = ref({
  cameraModels: [] as string[],
  lensModels: [] as string[],
  focalLengthRange: [null, null] as [number | null, number | null],
  shutterSpeedRange: [null, null] as [number | null, number | null],
  apertureRange: [null, null] as [number | null, number | null],
  isoRange: [null, null] as [number | null, number | null]
})

// 筛选条件
const selectedTags = ref<any[]>([])
const filters = ref({
  cameraModel: '',
  lensModel: '',
  focalLengthRange: [null, null] as [number | null, number | null],
  shutterSpeedRange: [null, null] as [number | null, number | null],
  apertureRange: [null, null] as [number | null, number | null],
  isoRange: [null, null] as [number | null, number | null],
  dominantColor: null,
  minQualityScore: 0
})

// 滑块锚点 (添加不限选项)
const focalLengthMarks = ['不限', 16, 24, 35, 50, 85, 105, 200, 400, 600, 800, '不限']
// 去掉多余的 f/2 刻度
const apertureMarks = ['不限', 1.2, 1.4, 1.8, 2.8, 4.0, 5.6, 8.0, 11.0, 16.0, '不限']
const shutterSpeedMarks = ['不限', 1/8000, 1/4000, 1/2000, 1/1000, 1/500, 1/250, 1/125, 1/60, 1/30, 1/15, 1/8, 1/4, 1/2, 1, 2, 4, 8, 15, 30, '不限']
const isoMarks = ['不限', 50, 100, 200, 400, 800, 1600, 3200, 6400, 12800, 25600, '不限']

// 加载筛选选项
const loadFilterOptions = async () => {
  try {
    const response = await api.get('/photos/filter-options')
    filterOptions.value = response.data

    // 设置默认的全选范围
    setDefaultRanges()
  } catch (error) {
    console.error('加载筛选选项失败:', error)
  }
}

// 设置默认的不限范围
const setDefaultRanges = () => {
  // 所有参数默认设置为不限 (null值表示不限)
  filters.value.focalLengthRange = [null, null]      // 焦距不限
  filters.value.shutterSpeedRange = [null, null]     // 快门速度不限
  filters.value.apertureRange = [null, null]         // 光圈不限
  filters.value.isoRange = [null, null]              // ISO不限
}

watch(() => props.show, async (val) => {
  if (val) {
    // 显示弹窗
    show.value = true
    await nextTick()
    // ensure ResizeObserver observes newly rendered mark containers
    if (resizeObserver) {
      if (focalMarksRef.value) resizeObserver.observe(focalMarksRef.value)
      if (shutterMarksRef.value) resizeObserver.observe(shutterMarksRef.value)
      if (apertureMarksRef.value) resizeObserver.observe(apertureMarksRef.value)
      if (isoMarksRef.value) resizeObserver.observe(isoMarksRef.value)
    }
    // recalc visible after DOM is rendered
    updateAllVisible()
    // 延迟一帧开始动画，确保DOM已渲染
    requestAnimationFrame(() => {
      animating.value = true
    })
    // extra recalculation after a short delay (fonts/layout)
    setTimeout(() => {
      updateAllVisible()
    }, 50)
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
    tagIds: selectedTags.value.map(t => t.id),
    cameraModel: filters.value.cameraModel || null,
    lensModel: filters.value.lensModel || null,
    minFocalLength: filters.value.focalLengthRange[0],
    maxFocalLength: filters.value.focalLengthRange[1],
    minShutterSpeed: filters.value.shutterSpeedRange[0],
    maxShutterSpeed: filters.value.shutterSpeedRange[1],
    minAperture: filters.value.apertureRange[0],
    maxAperture: filters.value.apertureRange[1],
    minIso: filters.value.isoRange[0],
    maxIso: filters.value.isoRange[1],
    dominantColor: filters.value.dominantColor || null,
    minQualityScore: filters.value.minQualityScore || null
  }


  // 只有当有有效的筛选条件时才进行筛选
  if (hasEffectiveFilters(filterData)) {
    await photoStore.filterPhotos(filterData)
  } else {
    // 没有有效筛选条件时，清除之前的筛选状态
    photoStore.clearLastFilters()
  }

  closePanel()
}

// 检查筛选条件是否有实际限制（非默认值）
const hasEffectiveFilters = (filterData: any) => {
  return (
    (filterData.tagIds && filterData.tagIds.length > 0) ||
    (filterData.cameraModel && filterData.cameraModel.trim() !== '') ||
    (filterData.lensModel && filterData.lensModel.trim() !== '') ||
    (filterData.dominantColor && filterData.dominantColor.trim() !== '') ||
    (filterData.minQualityScore && filterData.minQualityScore > 0) ||
    (filterData.minFocalLength !== null && filterData.minFocalLength !== undefined) ||
    (filterData.maxFocalLength !== null && filterData.maxFocalLength !== undefined) ||
    (filterData.minShutterSpeed !== null && filterData.minShutterSpeed !== undefined) ||
    (filterData.maxShutterSpeed !== null && filterData.maxShutterSpeed !== undefined) ||
    (filterData.minAperture !== null && filterData.minAperture !== undefined) ||
    (filterData.maxAperture !== null && filterData.maxAperture !== undefined) ||
    (filterData.minIso !== null && filterData.minIso !== undefined) ||
    (filterData.maxIso !== null && filterData.maxIso !== undefined)
  )
}

const resetFilters = () => {
  setDefaultRanges()
  filters.value.cameraModel = ''
  filters.value.lensModel = ''
  filters.value.dominantColor = null
  filters.value.minQualityScore = 0
  selectedTags.value = []
  // 清除筛选状态
  photoStore.clearLastFilters()
}

// 打开/关闭面板的处理，确保按需加载筛选选项（避免刷新时自动请求）
const onTogglePanel = async () => {
  const opening = !show.value
  show.value = opening
  if (opening) {
    if (!filterOptions.value || !filterOptions.value.cameraModels || filterOptions.value.cameraModels.length === 0) {
      await loadFilterOptions().catch(err => console.error('加载筛选选项失败:', err))
    }
    await nextTick()
    requestAnimationFrame(() => {
      animating.value = true
    })
  } else {
    animating.value = false
    setTimeout(() => {
      show.value = false
    }, 300)
  }
}

// 格式化函数
const formatFocalLength = (value: number | null) => {
  return value ? `${value}mm` : '0mm'
}

const formatShutterSpeed = (value: number | null) => {
  if (!value || value === 0) return '0'
  if (value >= 1) return `${Math.round(value)}`  // 超出1秒显示整数
  if (value >= 0.1) return `${value.toFixed(1)}`  // 0.1秒到1秒之间显示小数
  // 小于一秒显示倒数，分母取整
  const denominator = Math.round(1 / value)
  return `1/${denominator}`
}

const formatAperture = (value: number | null) => {
  return value ? value.toFixed(1) : '0.0'
}

const displayShutterLabel = (mark: any) => {
  if (mark === '不限') return '∞'
  // if value is number use formatShutterSpeed but apply narrow-screen shortening if needed
  const cw = shutterMarksRef.value ? shutterMarksRef.value.clientWidth : 9999
  const txt = formatShutterSpeed(mark)
  if (cw < 320 && typeof mark === 'number') {
    // Prefer fractional display for small shutter speeds (1/xxx) to avoid "0.00" issues.
    if (mark < 1) {
      const denom = Math.round(1 / mark)
      if (denom >= 1000) return `1/${Math.round(denom / 1000)}k`
      return `1/${denom}`
    }
    // >=1s: show integer seconds
    return `${Math.round(mark)}`
  }
  return txt
}

const displayIsoLabel = (mark: any) => {
  if (mark === '不限') return '∞'
  const cw = isoMarksRef.value ? isoMarksRef.value.clientWidth : 9999
  if (cw < 320 && typeof mark === 'number' && mark >= 1000) {
    const short = mark / 1000
    return Number.isInteger(short) ? `${short}k` : `${short.toFixed(1)}k`
  }
  return String(mark)
}

// 拖拽相关状态
let isDragging = false
let dragType = ''
let dragIndex = -1
let dragContainer: HTMLElement | null = null

// 拖拽功能
const startDrag = (event: MouseEvent, type: string, index: number) => {
  isDragging = true
  dragType = type
  dragIndex = index
  dragContainer = (event.target as HTMLElement).closest('.relative') as HTMLElement

  // 确保range数组已初始化
  const rangeKey = `${type}Range`
  if (!filters.value[rangeKey]) {
    // 如果数组不存在，初始化为不限状态
    filters.value[rangeKey] = [null, null]
  }

  document.addEventListener('mousemove', handleDrag)
  document.addEventListener('mouseup', stopDrag)
  event.preventDefault()
}

const handleDrag = (event: MouseEvent) => {
  if (!isDragging || !dragContainer) return

  const rect = dragContainer.getBoundingClientRect()
  const x = event.clientX - rect.left
  const percent = Math.max(0, Math.min(100, (x / rect.width) * 100))

  const min = getCurrentMin(dragType)
  const max = getCurrentMax(dragType)

  const value = min + (percent / 100) * (max - min)

  // 根据类型调整步长
  let adjustedValue = value
  switch (dragType) {
    case 'focalLength':
      // 对于焦距，根据百分比位置找到对应的标记值
      const focalIndex = Math.round(percent / 100 * (focalLengthMarks.length - 1))
      const clampedFocalIndex = Math.max(0, Math.min(focalLengthMarks.length - 1, focalIndex))
      const focalMark = focalLengthMarks[clampedFocalIndex]
      adjustedValue = focalMark === '不限' ? null : focalMark
      break
    case 'shutterSpeed':
      // 对于快门速度，根据百分比位置找到对应的标记值
      const shutterIndex = Math.round(percent / 100 * (shutterSpeedMarks.length - 1))
      const clampedIndex = Math.max(0, Math.min(shutterSpeedMarks.length - 1, shutterIndex))
      const shutterMark = shutterSpeedMarks[clampedIndex]
      adjustedValue = shutterMark === '不限' ? null : shutterMark
      break
    case 'aperture':
      // 对于光圈，根据百分比位置找到对应的标记值
      const apertureIndex = Math.round(percent / 100 * (apertureMarks.length - 1))
      const clampedApertureIndex = Math.max(0, Math.min(apertureMarks.length - 1, apertureIndex))
      const apertureMark = apertureMarks[clampedApertureIndex]
      adjustedValue = apertureMark === '不限' ? null : apertureMark
      break
    case 'iso':
      // 对于ISO，根据百分比位置找到对应的标记值
      const isoIndex = Math.round(percent / 100 * (isoMarks.length - 1))
      const clampedIsoIndex = Math.max(0, Math.min(isoMarks.length - 1, isoIndex))
      const isoMark = isoMarks[clampedIsoIndex]
      adjustedValue = isoMark === '不限' ? null : isoMark
      break
  }

  // 确保range数组存在
  const rangeKey = `${dragType}Range`
  if (!filters.value[rangeKey]) {
    filters.value[rangeKey] = [min, max]
  }

  filters.value[rangeKey][dragIndex] = adjustedValue
}

const stopDrag = () => {
  isDragging = false
  dragType = ''
  dragIndex = -1
  dragContainer = null
  document.removeEventListener('mousemove', handleDrag)
  document.removeEventListener('mouseup', stopDrag)
}

// 获取最接近的标记值
const getClosestMark = (value: number, marks: any[]) => {
  let closest = marks[0]
  let minDiff = Infinity

  for (const mark of marks) {
    if (mark === '不限') continue // 跳过不限选项

    const diff = Math.abs(value - mark)
    if (diff < minDiff) {
      minDiff = diff
      closest = mark
    }
  }

  return closest
}

// 获取最接近的快门速度标记值
const getClosestShutterSpeed = (value: number) => {
  return getClosestMark(value, shutterSpeedMarks)
}

// 获取最接近的焦距标记值
const getClosestFocalLength = (value: number) => {
  return getClosestMark(value, focalLengthMarks)
}

// 获取最接近的光圈标记值
const getClosestAperture = (value: number) => {
  return getClosestMark(value, apertureMarks)
}

// 获取最接近的ISO标记值
const getClosestIso = (value: number) => {
  return getClosestMark(value, isoMarks)
}

// 获取焦距刻度显示的起始索引
const getFocalLengthStartIndex = () => {
  const minValue = filterOptions.value.focalLengthRange?.[0] || 0
  const closestMin = getClosestFocalLength(minValue)
  return focalLengthMarks.indexOf(closestMin)
}

// 获取焦距刻度显示的结束索引
const getFocalLengthEndIndex = () => {
  const maxValue = filterOptions.value.focalLengthRange?.[1] || 1000
  const closestMax = getClosestFocalLength(maxValue)
  return focalLengthMarks.indexOf(closestMax)
}

// 获取快门速度刻度显示的起始索引
const getShutterSpeedStartIndex = () => {
  const minValue = filterOptions.value.shutterSpeedRange?.[0] || 0.0001
  const closestMin = getClosestShutterSpeed(minValue)
  return shutterSpeedMarks.indexOf(closestMin)
}

// 获取快门速度刻度显示的结束索引
const getShutterSpeedEndIndex = () => {
  const maxValue = filterOptions.value.shutterSpeedRange?.[1] || 60
  const closestMax = getClosestShutterSpeed(maxValue)
  return shutterSpeedMarks.indexOf(closestMax)
}

// 获取光圈刻度显示的起始索引
const getApertureStartIndex = () => {
  const minValue = filterOptions.value.apertureRange?.[0] || 1.0
  const closestMin = getClosestAperture(minValue)
  return apertureMarks.indexOf(closestMin)
}

// 获取光圈刻度显示的结束索引
const getApertureEndIndex = () => {
  const maxValue = filterOptions.value.apertureRange?.[1] || 32.0
  const closestMax = getClosestAperture(maxValue)
  return apertureMarks.indexOf(closestMax)
}

// 获取ISO刻度显示的起始索引
const getIsoStartIndex = () => {
  const minValue = filterOptions.value.isoRange?.[0] || 50
  const closestMin = getClosestIso(minValue)
  return isoMarks.indexOf(closestMin)
}

// 获取ISO刻度显示的结束索引
const getIsoEndIndex = () => {
  const maxValue = filterOptions.value.isoRange?.[1] || 25600
  const closestMax = getClosestIso(maxValue)
  return isoMarks.indexOf(closestMax)
}

// 获取位置百分比
const getPositionPercent = (value: number | null, min: number, max: number, type?: string, isMin?: boolean) => {
  // 如果是null（不限），根据isMin决定位置
  if (value === null) {
    return isMin ? 0 : 100 // 最小值不限放在左边，最大值不限放在右边
  }

  // 对于所有参数，使用非线性刻度位置计算
  if (type && value) {
    let marks: any[] = []
    switch (type) {
      case 'focalLength':
        marks = focalLengthMarks
        break
      case 'shutterSpeed':
        marks = shutterSpeedMarks
        break
      case 'aperture':
        marks = apertureMarks
        break
      case 'iso':
        marks = isoMarks
        break
    }

    if (marks.length > 0) {
      let snappedValue = value
      switch (type) {
        case 'focalLength':
          snappedValue = getClosestFocalLength(value)
          break
        case 'shutterSpeed':
          snappedValue = getClosestShutterSpeed(value)
          break
        case 'aperture':
          snappedValue = getClosestAperture(value)
          break
        case 'iso':
          snappedValue = getClosestIso(value)
          break
      }

      // 找到吸附值在marks数组中的索引
      const index = marks.indexOf(snappedValue)
      if (index !== -1) {
        // 将索引转换为百分比位置
        return (index / (marks.length - 1)) * 100
      }
    }
    // 如果找不到，使用线性计算作为后备
    return Math.max(0, Math.min(100, ((value - min) / (max - min)) * 100))
  }

  return Math.max(0, Math.min(100, ((value - min) / (max - min)) * 100))
}

// 获取当前范围的最小值
const getCurrentMin = (type: string) => {
  const rangeKey = `${type}Range`
  const range = filterOptions.value[rangeKey]
  if (range && range[0] !== null) {
    return range[0]
  }

  // 后备默认值
  switch (type) {
    case 'focalLength': return 0
    case 'shutterSpeed': return 0.0001
    case 'aperture': return 1.0
    case 'iso': return 50
    default: return 0
  }
}

// 获取当前范围的最大值
const getCurrentMax = (type: string) => {
  const rangeKey = `${type}Range`
  const range = filterOptions.value[rangeKey]
  if (range && range[1] !== null) {
    return range[1]
  }

  // 后备默认值
  switch (type) {
    case 'focalLength': return 1000
    case 'shutterSpeed': return 60
    case 'aperture': return 32.0
    case 'iso': return 25600
    default: return 100
  }
}

// 计算刻度文本微调偏移（像素） - 默认把刻度往两端稍微拉开，便于阅读
const getMarkOffset = (index: number, marks: any[], spreadPx = 1) => {
  const center = (marks.length - 1) / 2
  // 返回正/负像素值，靠左为负，靠右为正
  return Math.round((index - center) * spreadPx)
}

// 快门速度隐藏文本的索引（但保留吸附点），如果需要调整只改这组数组
const shutterHiddenIndexes: number[] = [] // 2, 4, 6, 8, 10, 12
// ISO 隐藏文本索引（保留吸附点），例如隐藏 12800 文本
const isoHiddenIndexes: number[] = []  // 7, 9

const shouldShowShutterText = (index: number) => {
  return !shutterHiddenIndexes.includes(index)
}

// 简易动态刻度显示：基于容器宽度与估算的最小标签宽度，按步长显示刻度文本（不影响吸附点）
const focalMarksRef = ref<HTMLElement | null>(null)
const shutterMarksRef = ref<HTMLElement | null>(null)
const apertureMarksRef = ref<HTMLElement | null>(null)
const isoMarksRef = ref<HTMLElement | null>(null)

const visibleFocal = ref<Set<number>>(new Set())
const visibleShutter = ref<Set<number>>(new Set())
const visibleAperture = ref<Set<number>>(new Set())
const visibleIso = ref<Set<number>>(new Set())

const minLabelGapPx = 0 // minimum extra gap between labels (pixels)

// measure text width by creating a hidden DOM span (more accurate with exact CSS)
const measureTextWidth = (() => {
  let measurer: HTMLSpanElement | null = null
  return (container: HTMLElement | null, text: string) => {
    try {
      if (!measurer) {
        measurer = document.createElement('span')
        measurer.style.position = 'absolute'
        measurer.style.visibility = 'hidden'
        measurer.style.whiteSpace = 'nowrap'
        measurer.style.pointerEvents = 'none'
        measurer.style.left = '-9999px'
        document.body.appendChild(measurer)
      }
      // copy container classes if available so Tailwind styles (font-size, color) match exactly
      if (container && container.className) {
        measurer.className = String(container.className)
      } else {
        const style = window.getComputedStyle(document.body)
        measurer.style.font = style.font || ''
        measurer.style.fontSize = style.fontSize || ''
        measurer.style.fontFamily = style.fontFamily || ''
        measurer.style.fontWeight = style.fontWeight || ''
        measurer.style.fontStyle = style.fontStyle || ''
      }
      measurer.textContent = String(text)
      return measurer.offsetWidth || String(text).length * 8
    } catch (e) {
      return String(text).length * 8
    }
  }
})()

const measureVisible = (container: HTMLElement | null, marks: any[], outRef: any, formatter: (m: any) => string, manualHidden: number[] = []) => {
  // outRef is a Ref<Set<number>>
  const outSet: Set<number> = outRef.value
  outSet.clear()
  if (!container || marks.length === 0) {
    for (let i = 0; i < marks.length; i++) outSet.add(i)
    return
  }
  const containerWidth = container.clientWidth || 1
  const n = marks.length
  // measure all label widths
  const widths: number[] = marks.map((m, i) => {
    const text = formatter(m)
    return measureTextWidth(container, text) + 8 // small padding
  })
  // compute target centers for each mark (including getMarkOffset)
  const centers: number[] = []
  for (let i = 0; i < n; i++) {
    const percent = (n > 1) ? (i / (n - 1)) : 0.5
    const base = percent * containerWidth
    // apply slightly larger spread for shutter/iso to reduce collisions of dense numeric labels
    let spreadForSet = 1
    if (marks === shutterSpeedMarks) spreadForSet = 3
    else if (marks === isoMarks) spreadForSet = 2
    else if (marks === apertureMarks) spreadForSet = 1.5
    const offset = getMarkOffset(i, marks, spreadForSet)
    centers.push(base + offset)
  }

  // Build candidate intervals [start, end] with small required gap
  const halfG = minLabelGapPx / 2
  type Candidate = { idx: number; start: number; end: number; weight: number }
  const candidates: Candidate[] = []
  for (let i = 0; i < n; i++) {
    if (manualHidden.includes(i)) continue
    const half = widths[i] / 2
    const start = Math.max(0, centers[i] - half - halfG)
    const end = Math.min(containerWidth, centers[i] + half + halfG)
    // weight favors shorter labels: more weight for smaller widths
    const weight = Math.max(1, Math.round(containerWidth / widths[i]))
    candidates.push({ idx: i, start, end, weight })
  }

  // sort by end for DP
  candidates.sort((a, b) => a.end - b.end)

  // compute p[j] = rightmost index < j that doesn't overlap j
  const overlaps = (aStart: number, aEnd: number, bStart: number, bEnd: number) => aStart < bEnd && bStart < aEnd
  const m = candidates.length
  const p: number[] = new Array(m).fill(-1)
  for (let j = 0; j < m; j++) {
    for (let i = j - 1; i >= 0; i--) {
      if (!overlaps(candidates[i].start, candidates[i].end, candidates[j].start, candidates[j].end)) {
        p[j] = i
        break
      }
    }
  }

  // DP for weighted interval scheduling
  const dp: number[] = new Array(m).fill(0)
  const take: boolean[] = new Array(m).fill(false)
  for (let j = 0; j < m; j++) {
    const includeWeight = candidates[j].weight + (p[j] !== -1 ? dp[p[j]] : 0)
    const excludeWeight = j > 0 ? dp[j - 1] : 0
    if (includeWeight >= excludeWeight) {
      dp[j] = includeWeight
      take[j] = true
    } else {
      dp[j] = excludeWeight
      take[j] = false
    }
  }

  // reconstruct selected set
  const selectedIdxs = new Set<number>()
  let j = m - 1
  while (j >= 0) {
    if (take[j]) {
      selectedIdxs.add(candidates[j].idx)
      j = p[j]
    } else {
      j--
    }
  }

  // Ensure endpoints (0 and n-1) are present when possible: try to add them by removing conflicting ones
  const findByIdx = (idx: number) => candidates.find((c) => c.idx === idx)
  const tryAddEndpoint = (endpointIdx: number) => {
    const cand = findByIdx(endpointIdx)
    if (!cand) return
    if (selectedIdxs.has(endpointIdx)) return
    // find selected that overlap with cand
    const overlapping = Array.from(selectedIdxs).filter((selIdx) => {
      const selCand = findByIdx(selIdx)
      if (!selCand) return false
      return overlaps(selCand.start, selCand.end, cand.start, cand.end)
    })
    if (overlapping.length === 0) {
      selectedIdxs.add(endpointIdx)
    } else {
      // replace the first overlapping only if endpoint is shorter (prefer showing endpoint)
      const selCand = findByIdx(overlapping[0])
      if (selCand && (cand.end - cand.start) <= (selCand.end - selCand.start)) {
        selectedIdxs.delete(selCand.idx)
        selectedIdxs.add(cand.idx)
      }
    }
  }
  tryAddEndpoint(0)
  tryAddEndpoint(n - 1)

  // write to outSet
  for (const idx of selectedIdxs) outSet.add(idx)
}

const updateAllVisible = () => {
  measureVisible(focalMarksRef.value, focalLengthMarks, visibleFocal, (m) => (m === '不限' ? '∞' : String(m)))
  // use the same display formatter used for rendering to avoid mismatch
  measureVisible(shutterMarksRef.value, shutterSpeedMarks, visibleShutter, displayShutterLabel, shutterHiddenIndexes)
  measureVisible(apertureMarksRef.value, apertureMarks, visibleAperture, (m) => (m === '不限' ? '∞' : 'f/' + String(m)))
  measureVisible(isoMarksRef.value, isoMarks, visibleIso, displayIsoLabel, isoHiddenIndexes)
}

let resizeObserver: ResizeObserver | null = null

onMounted(() => {
  updateAllVisible()
  resizeObserver = new ResizeObserver(() => {
    updateAllVisible()
  })
  // Observe all slider containers
  if (focalMarksRef.value) resizeObserver.observe(focalMarksRef.value)
  if (shutterMarksRef.value) resizeObserver.observe(shutterMarksRef.value)
  if (apertureMarksRef.value) resizeObserver.observe(apertureMarksRef.value)
  if (isoMarksRef.value) resizeObserver.observe(isoMarksRef.value)
})
onBeforeUnmount(() => {
  if (resizeObserver) {
    resizeObserver.disconnect()
    resizeObserver = null
  }
})
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

/* 范围滑块样式 */
.range-slider {
  -webkit-appearance: none;
  appearance: none;
  background: transparent;
  cursor: pointer;
}

.range-slider::-webkit-slider-thumb {
  -webkit-appearance: none;
  appearance: none;
  height: 16px;
  width: 16px;
  border-radius: 50%;
  background: #3b82f6;
  border: 2px solid #ffffff;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.2);
  cursor: pointer;
}

.range-slider::-moz-range-thumb {
  height: 16px;
  width: 16px;
  border-radius: 50%;
  background: #3b82f6;
  border: 2px solid #ffffff;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.2);
  cursor: pointer;
  border: none;
}

.dark .range-slider::-webkit-slider-thumb {
  background: #60a5fa;
  border-color: #1f2937;
}

.dark .range-slider::-moz-range-thumb {
  background: #60a5fa;
  border-color: #1f2937;
}
</style>

