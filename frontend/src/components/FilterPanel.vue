<template>
  <div>
    <!-- 筛选按钮 -->
    <button
      @click="onTogglePanel"
      class="filter-btn p-2 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-800 transition-all duration-200 hover:scale-110 hover:shadow-md transform-gpu group relative overflow-hidden"
      @mouseenter="filterHover = true"
      @mouseleave="filterHover = false"
    >
      <svg
        class="filter-svg w-5 h-5 transition-all duration-300 group-hover:rotate-12 group-hover:scale-110"
        :class="{ 'is-hovering': filterHover }"
        fill="none"
        stroke="currentColor"
        viewBox="0 0 24 24"
      >
        <!-- 滤镜/漏斗主体 -->
        <path
          class="filter-body"
          stroke-linecap="round"
          stroke-linejoin="round"
          stroke-width="1.5"
          d="M3 4a1 1 0 011-1h16a1 1 0 011 1v2.586a1 1 0 01-.293.707l-6.414 6.414a1 1 0 00-.293.707V17l-4 4v-6.586a1 1 0 00-.293-.707L3.293 7.293A1 1 0 013 6.586V4z"
        />
        <!-- 竖线 -->
        <path
          class="filter-line"
          stroke-linecap="round"
          stroke-linejoin="round"
          stroke-width="1.5"
          d="M12 4v16"
        />
      </svg>
      <div class="absolute inset-0 bg-gradient-to-r from-cyan-500/10 to-blue-500/10 opacity-0 group-hover:opacity-100 transition-opacity duration-200 rounded-lg"></div>
    </button>
  </div>

  <!-- Teleport 弹窗 -->
  <Teleport to="body">
      <!-- 标签弹窗背景遮罩 -->
      <div v-if="showTagSelector" class="fixed inset-0 z-40 bg-black/5 cursor-pointer" @click="closeTagSelector"></div>

      <!-- 筛标签选择弹窗 -->
      <Teleport to="body">
        <div v-if="showTagSelector" class="fixed inset-0 z-50 flex items-center justify-center pointer-events-none p-4">
          <div class="filter-panel-glass tag-selector-modal pointer-events-auto p-0 max-w-2xl w-full mx-4 max-h-[90vh] flex flex-col" :class="{ 'dark-modal-bg': isDarkMode }" @click.stop>
          <!-- 弹窗头部 -->
          <div class="flex justify-between items-center p-6 pb-4 border-b border-gray-200 dark:border-gray-700">
            <h3 class="text-xl font-semibold">选择标签</h3>
            <button @click="closeTagSelector" class="p-2 hover:bg-gray-100 dark:hover:bg-gray-700 rounded-lg transition-colors">
              <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"/>
              </svg>
            </button>
          </div>

          <!-- 弹窗内容 -->
          <div class="flex-1 overflow-y-auto p-6">
            <!-- 搜索框 -->
            <div class="mb-4">
              <input v-model="tagSearchQuery" type="text" placeholder="搜索标签..." class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 focus:ring-2 focus:ring-blue-500 focus:border-transparent"/>
            </div>

            <!-- 标签气泡瀑布流 -->
            <div class="min-h-[300px]">
              <div class="flex flex-wrap gap-3">
                <button
                  v-for="tag in filteredTags"
                  :key="tag.id"
                  @click="toggleTagSelection(tag)"
                  :class="[
                    'px-3 py-2 rounded-full text-sm font-medium transition-all duration-200 hover:scale-105 transform-gpu border',
                    isTagSelectedInModal(tag.id)
                      ? 'border-blue-500 bg-blue-50 dark:bg-blue-900/20 text-blue-700 dark:text-blue-300'
                      : 'border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-700 text-gray-700 dark:text-gray-300 hover:border-gray-400 dark:hover:border-gray-500'
                  ]"
                  :style="isTagSelectedInModal(tag.id) ? {} : {
                    backgroundColor: tag.color,
                    color: isDarkMode ? getDarkModeContrastColor(tag.color) : getContrastColor(tag.color)
                  }"
                >
                  {{ tag.name }}({{ tag.count }})
                </button>
              </div>

              <!-- 无结果提示 -->
              <div v-if="filteredTags.length === 0" class="text-center py-8 text-gray-500 dark:text-gray-400">
                没有找到匹配的标签
              </div>
            </div>
          </div>

          <!-- 弹窗底部 -->
          <div class="flex justify-between items-center p-6 pt-4 border-t border-gray-200 dark:border-gray-700">
            <div class="text-sm text-gray-500 dark:text-gray-400">
              已选择 {{ selectedTagsInModal.length }} 个标签
            </div>
            <div class="flex gap-3">
              <button @click="closeTagSelector" class="px-4 py-2 border border-gray-300 dark:border-gray-600 rounded-lg hover:bg-gray-50 dark:hover:bg-gray-700 transition-colors">
                取消
              </button>
              <button
                @click="confirmTagSelection"
                :disabled="selectedTagsInModal.length === 0"
                class="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:bg-gray-400 disabled:cursor-not-allowed transition-colors"
              >
                确定 ({{ selectedTagsInModal.length }})
              </button>
            </div>
          </div>
        </div>
        </div>
      </Teleport>

      <!-- 遮罩层 -->
      <div
        v-if="show"
        class="fixed inset-0 z-40 bg-black/5 cursor-pointer"
        :class="{ 'opacity-0': !animating, 'opacity-100': animating }"
        style="transition: opacity 0.3s ease;"
        @click="closePanel"
      ></div>

      <!-- 弹窗 -->
        <div
          v-if="show"
        class="fixed inset-0 z-50 flex items-center justify-center pointer-events-none"
        >
          <div
          ref="panelRef"
          class="filter-panel-glass pointer-events-auto p-0 max-w-2xl w-full mx-4 max-h-[90vh] flex flex-col"
          :class="{ 'opacity-0 scale-95': !animating, 'opacity-100 scale-100': animating, 'dark-modal-bg': isDarkMode }"
          style="transition: all 0.3s ease;"
            @click.stop
          >
          <!-- 头部区域 -->
          <div class="flex justify-between items-center p-6 pb-4">
            <div class="flex items-center gap-4 flex-shrink-0">
              <h2 class="text-2xl font-light whitespace-nowrap">高级筛选</h2>
            </div>
            <!-- 操作按钮区域 -->
            <div class="flex items-center gap-2">
              <!-- 分享按钮 -->
              <button
                @click="handleShare"
                class="p-2 w-9 h-9 flex-shrink-0 hover:bg-gray-100 dark:hover:bg-gray-700 rounded-lg transition-all duration-300 hover:scale-110 group"
                title="分享当前筛选"
              >
                <svg class="w-5 h-5 text-gray-600 dark:text-gray-300 group-hover:text-green-500 transition-colors" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8.684 13.342C8.886 12.938 9 12.482 9 12c0-.482-.114-.938-.316-1.342m0 2.684a3 3 0 110-2.684m0 2.684l6.632 3.316m-6.632-6l6.632-3.316m0 0a3 3 0 105.367-2.684 3 3 0 00-5.367 2.684zm0 9.316a3 3 0 105.368 2.684 3 3 0 00-5.368-2.684z" />
                </svg>
              </button>
              <!-- 关闭按钮 - 添加悬停动画 -->
              <button
                @click="closePanel"
                class="p-2 hover:bg-gray-100 dark:hover:bg-gray-700 rounded-lg transition-all duration-300 hover:rotate-90 hover:bg-red-50 dark:hover:bg-red-900/20 group"
              >
                <svg class="w-5 h-5 text-gray-500 dark:text-gray-400 group-hover:text-red-500 transition-colors" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
                </svg>
              </button>
            </div>
          </div>

          <!-- 可滚动内容区域 -->
          <div class="flex-1 overflow-y-auto px-6">
            <form @submit.prevent="applyFilters" class="space-y-6">
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
              <div class="relative px-2 py-2" @click="handleTrackClick($event, 'focalLength')">
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
                  class="absolute top-1/2 w-1.5 h-1.5 bg-white dark:bg-gray-800 border border-blue-500 rounded-full transform -translate-x-1/2 -translate-y-1/2 z-10 cursor-pointer"
                  :style="{ left: (index / (focalLengthMarks.length - 1)) * 100 + '%' }"
                  v-show="index >= getFocalLengthStartIndex() && index <= getFocalLengthEndIndex()"
                  @click="handleMarkClick($event, 'focalLength', mark)"
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
              <div class="relative px-2 py-2" @click="handleTrackClick($event, 'shutterSpeed')">
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
                  class="absolute top-1/2 w-1.5 h-1.5 bg-white dark:bg-gray-800 border border-green-500 rounded-full transform -translate-x-1/2 -translate-y-1/2 z-10 cursor-pointer"
                  :style="{ left: (index / (shutterSpeedMarks.length - 1)) * 100 + '%' }"
                  v-show="index >= getShutterSpeedStartIndex() && index <= getShutterSpeedEndIndex()"
                  @click="handleMarkClick($event, 'shutterSpeed', mark)"
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
              <div class="relative px-2 py-2" @click="handleTrackClick($event, 'aperture')">
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
                  class="absolute top-1/2 w-1.5 h-1.5 bg-white dark:bg-gray-800 border border-purple-500 rounded-full transform -translate-x-1/2 -translate-y-1/2 z-10 cursor-pointer"
                  :style="{ left: (index / (apertureMarks.length - 1)) * 100 + '%' }"
                  v-show="index >= getApertureStartIndex() && index <= getApertureEndIndex()"
                  @click="handleMarkClick($event, 'aperture', mark)"
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
              <div class="relative px-2 py-2" @click="handleTrackClick($event, 'iso')">
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
                  class="absolute top-1/2 w-1.5 h-1.5 bg-white dark:bg-gray-800 border border-orange-500 rounded-full transform -translate-x-1/2 -translate-y-1/2 z-10 cursor-pointer"
                  :style="{ left: (index / (isoMarks.length - 1)) * 100 + '%' }"
                  v-show="index >= getIsoStartIndex() && index <= getIsoEndIndex()"
                  @click="handleMarkClick($event, 'iso', mark)"
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

              <!-- 分类筛选 -->
              <div v-if="props.categories && props.categories.length > 0">
                <label class="block text-sm font-medium mb-2">相册分类</label>
                <div class="relative overflow-visible">
                  <!-- 横向滚动容器 -->
                  <div class="flex gap-3 overflow-x-auto pb-2 px-0 pt-2 scroll-smooth category-container"
                       style="scrollbar-width: none; -ms-overflow-style: none;">
                    <!-- 全部选项 -->
                    <button
                      @click="selectCategory('')"
                      :class="[
                        'flex-shrink-0 px-4 py-2 rounded-full border transition-all duration-200 hover:scale-105 transform-gpu text-sm font-medium whitespace-nowrap',
                        filters.category === ''
                          ? 'border-blue-500 bg-blue-50 dark:bg-blue-900/20 text-blue-700 dark:text-blue-300'
                          : 'border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-700 text-gray-700 dark:text-gray-300 hover:border-gray-400 dark:hover:border-gray-500'
                      ]"
                    >
                      全部
                    </button>

                    <!-- 分类气泡 -->
                    <button
                      v-for="category in props.categories"
                      :key="category"
                      @click="selectCategory(category)"
                      :class="[
                        'flex-shrink-0 px-4 py-2 rounded-full border transition-all duration-200 hover:scale-105 transform-gpu text-sm font-medium whitespace-nowrap',
                        filters.category === category
                          ? 'border-blue-500 bg-blue-50 dark:bg-blue-900/20 text-blue-700 dark:text-blue-300'
                          : 'border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-700 text-gray-700 dark:text-gray-300 hover:border-gray-400 dark:hover:border-gray-500'
                      ]"
                    >
                      {{ category }}
                    </button>
                  </div>
                </div>
              </div>

              <!-- 标签筛选 -->
              <div>
                <label class="block text-sm font-medium mb-2">标签</label>

                <!-- 已选标签 -->
                <div v-if="selectedTags.length > 0" class="flex flex-wrap gap-2 mb-3">
                  <span
                    v-for="tag in selectedTags"
                    :key="tag.id"
                    class="px-3 py-1 rounded-full text-sm flex items-center gap-2"
                    :style="{ backgroundColor: tag.color || '#e5e7eb', color: getContrastColor(tag.color) }"
                  >
                    {{ tag.name }}
                    <button @click="removeTag(tag.id)" class="hover:text-red-500 ml-1">×</button>
                  </span>
                </div>

                <!-- 添加标签按钮 -->
                <button
                  @click="openTagSelector"
                  class="flex items-center gap-2 px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 hover:bg-gray-50 dark:hover:bg-gray-600 transition-colors"
                >
                  <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4" />
                  </svg>
                  <span class="text-sm">添加标签</span>
                </button>
              </div>

              <!-- 色彩筛选 -->
              <div>
                <label class="block text-sm font-medium mb-2">颜色分类</label>
                <div class="relative overflow-visible">
                  <!-- 横向滚动容器 -->
                  <div class="flex gap-3 overflow-x-auto pb-2 px-0 pt-2 scroll-smooth color-dots-container"
                       style="scrollbar-width: none; -ms-overflow-style: none;">
                    <!-- 全部选项 -->
                    <button
                      @click="selectColor('')"
                      :class="[
                        'flex-shrink-0 w-8 h-8 rounded-full border-2 transition-all duration-200 hover:scale-110 transform-gpu flex items-center justify-center text-xs font-medium',
                        filters.colorCategory === ''
                          ? 'border-blue-500 bg-blue-50 dark:bg-blue-900/20'
                          : 'border-gray-300 dark:border-gray-600 bg-gray-100 dark:bg-gray-700 hover:border-gray-400 dark:hover:border-gray-500'
                      ]"
                    >
                      全部
                    </button>

                    <!-- 颜色圆点 -->
                    <button
                      v-for="color in colorOptions"
                      :key="color.value"
                      @click="selectColor(color.value)"
                      :class="[
                        'flex-shrink-0 w-8 h-8 rounded-full border-2 transition-all duration-200 hover:scale-110 transform-gpu',
                        filters.colorCategory === color.value
                          ? 'border-blue-500 ring-2 ring-blue-200 dark:ring-blue-800'
                          : 'border-gray-300 dark:border-gray-600 hover:border-gray-400 dark:hover:border-gray-500'
                      ]"
                      :style="{ backgroundColor: color.hex }"
                      :title="color.label"
                    ></button>
                  </div>
                </div>
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

              <!-- 时间范围筛选 -->
              <div>
                <label class="block text-sm font-medium mb-2">拍摄时间范围</label>
                <div class="grid grid-cols-2 gap-3">
                  <div>
                    <label class="block text-xs text-gray-500 dark:text-gray-400 mb-1">开始日期</label>
                    <input
                      v-model="filters.startDate"
                      type="date"
                      class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 focus:ring-2 focus:ring-gray-900 dark:focus:ring-white text-sm"
                    />
                  </div>
                  <div>
                    <label class="block text-xs text-gray-500 dark:text-gray-400 mb-1">结束日期</label>
                    <input
                      v-model="filters.endDate"
                      type="date"
                      class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 focus:ring-2 focus:ring-gray-900 dark:focus:ring-white text-sm"
                    />
                  </div>
                </div>
                <div v-if="filters.startDate || filters.endDate" class="mt-2">
                  <button
                    @click="clearDateRange"
                    class="text-xs text-blue-500 hover:text-blue-600"
                  >
                    清除日期范围
                  </button>
                </div>
              </div>

            </form>
          </div>

          <!-- 固定底部按钮区域 -->
          <div class="flex justify-end space-x-4 p-6 pt-4 rounded-b-2xl">
                <button
                  type="button"
                  @click="resetFiltersInternal"
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
</template>

<script setup lang="ts">
import { ref, computed, watch, nextTick, onMounted, onBeforeUnmount } from 'vue'
import { usePhotoStore } from '@/stores/photo'
import { useThemeStore } from '@/stores/theme'
import { api } from '@/api'

const props = defineProps<{
  show: boolean
  initialFilters?: any
  categories?: string[]
}>()

// 内部维护的 URL 参数（用于首次打开面板时同步）
const urlFilters = ref<any>(null)

const emit = defineEmits<{
  (e: 'update:show', value: boolean): void
  (e: 'update:selectedTags', value: any[]): void
  (e: 'reset'): void
  (e: 'filters-applied', filters: any): void
}>()

const photoStore = usePhotoStore()
const themeStore = useThemeStore()
const show = ref(props.show)
const animating = ref(false)
const isAnimating = ref(false) // 防止动画冲突
const panelRef = ref<HTMLElement>()
const shouldResetOnOpen = ref(false)

// 筛选按钮动画状态
const filterHover = ref(false)

// 同步筛选参数到面板
const syncFiltersFromProps = async (filterData: any) => {
  if (!filterData) return

  // 更新筛选条件
  if (filterData.cameraModel !== undefined) filters.value.cameraModel = filterData.cameraModel
  if (filterData.lensModel !== undefined) filters.value.lensModel = filterData.lensModel
  if (filterData.minFocalLength !== undefined || filterData.maxFocalLength !== undefined) {
    filters.value.focalLengthRange = [filterData.minFocalLength || null, filterData.maxFocalLength || null]
  }
  if (filterData.minAperture !== undefined || filterData.maxAperture !== undefined) {
    filters.value.apertureRange = [filterData.minAperture || null, filterData.maxAperture || null]
  }
  if (filterData.minShutterSpeed !== undefined || filterData.maxShutterSpeed !== undefined) {
    filters.value.shutterSpeedRange = [filterData.minShutterSpeed || null, filterData.maxShutterSpeed || null]
  }
  if (filterData.minIso !== undefined || filterData.maxIso !== undefined) {
    filters.value.isoRange = [filterData.minIso || null, filterData.maxIso || null]
  }
  if (filterData.colorCategory !== undefined) filters.value.colorCategory = filterData.colorCategory
  if (filterData.category !== undefined) filters.value.category = filterData.category
  if (filterData.minQualityScore !== undefined) filters.value.minQualityScore = filterData.minQualityScore
  if (filterData.startDate !== undefined) filters.value.startDate = filterData.startDate || ''
  if (filterData.endDate !== undefined) filters.value.endDate = filterData.endDate || ''

  // 处理标签ID - 需要从API加载标签详情
  if (filterData.tagIds && filterData.tagIds.length > 0) {
    // 确保筛选选项已加载
    if (!filterOptions.value.tags || filterOptions.value.tags.length === 0) {
      await loadFilterOptions()
    }
    // 从已加载的标签中找到匹配的标签
    const selected = filterOptions.value.tags.filter((tag: any) => filterData.tagIds.includes(tag.id))
    if (selected.length > 0) {
      selectedTags.value = selected
    }
  }
}

// 外部调用同步方法（供父组件使用）
const syncFromExternal = async (filterData: any) => {
  await syncFiltersFromProps(filterData)

  // 同步完成后，刷新滑块的可见状态
  nextTick(() => {
    updateAllVisible()
    // 延迟刷新确保DOM已渲染
    setTimeout(() => {
      updateAllVisible()
    }, 100)
  })
}

// 分享功能
const handleShare = () => {
  // 构建筛选参数对象，只包含有效的筛选条件
  const filterData: any = {}

  // 只添加有实际值的筛选条件
  if (selectedTags.value.length > 0) {
    filterData.tagIds = selectedTags.value.map(t => t.id)
  }
  if (filters.value.cameraModel) {
    filterData.cameraModel = filters.value.cameraModel
  }
  if (filters.value.lensModel) {
    filterData.lensModel = filters.value.lensModel
  }
  if (filters.value.focalLengthRange[0] !== null) {
    filterData.minFocalLength = filters.value.focalLengthRange[0]
  }
  if (filters.value.focalLengthRange[1] !== null) {
    filterData.maxFocalLength = filters.value.focalLengthRange[1]
  }
  if (filters.value.shutterSpeedRange[0] !== null) {
    filterData.minShutterSpeed = filters.value.shutterSpeedRange[0]
  }
  if (filters.value.shutterSpeedRange[1] !== null) {
    filterData.maxShutterSpeed = filters.value.shutterSpeedRange[1]
  }
  if (filters.value.apertureRange[0] !== null) {
    filterData.minAperture = filters.value.apertureRange[0]
  }
  if (filters.value.apertureRange[1] !== null) {
    filterData.maxAperture = filters.value.apertureRange[1]
  }
  if (filters.value.isoRange[0] !== null) {
    filterData.minIso = filters.value.isoRange[0]
  }
  if (filters.value.isoRange[1] !== null) {
    filterData.maxIso = filters.value.isoRange[1]
  }
  if (filters.value.colorCategory) {
    filterData.colorCategory = filters.value.colorCategory
  }
  if (filters.value.category) {
    filterData.category = filters.value.category
  }
  if (filters.value.minQualityScore > 0) {
    filterData.minQualityScore = filters.value.minQualityScore
  }
  if (filters.value.startDate) {
    filterData.startDate = filters.value.startDate
  }
  if (filters.value.endDate) {
    filterData.endDate = filters.value.endDate
  }

  // 检查是否有有效的筛选条件
  const hasFilters = Object.keys(filterData).length > 0

  if (hasFilters) {
    // 将筛选参数编码为URL查询参数
    const filtersJson = JSON.stringify(filterData)
    const encodedFilters = encodeURIComponent(filtersJson)

    // 获取当前路径（从父组件传入或默认）
    const currentPath = window.location.pathname
    const shareUrl = `${currentPath}?filters=${encodedFilters}`

    // 在新标签页打开分享链接
    window.open(shareUrl, '_blank')
  } else {
    // 没有筛选条件时，直接分享当前页面
    window.open(window.location.pathname, '_blank')
  }
}

// 筛选选项数据
const filterOptions = ref({
  cameraModels: [] as any[],
  lensModels: [] as any[],
  focalLengthRange: [null, null] as [number | null, number | null],
  shutterSpeedRange: [null, null] as [number | null, number | null],
  apertureRange: [null, null] as [number | null, number | null],
  isoRange: [null, null] as [number | null, number | null],
  tags: [] as any[]
})

// 颜色选项数据
const colorOptions = [
  { value: 'RED', hex: '#ef4444', label: '红色' },
  { value: 'ORANGE', hex: '#f97316', label: '橙色' },
  { value: 'YELLOW', hex: '#eab308', label: '黄色' },
  { value: 'GREEN', hex: '#22c55e', label: '绿色' },
  { value: 'BLUE', hex: '#3b82f6', label: '蓝色' },
  { value: 'PURPLE', hex: '#a855f7', label: '紫色' },
  { value: 'PINK', hex: '#ec4899', label: '粉色' },
  { value: 'BROWN', hex: '#8B4513', label: '棕色' },
  { value: 'GRAY', hex: '#6b7280', label: '灰色' },
  { value: 'BLACK', hex: '#000000', label: '黑色' },
  { value: 'WHITE', hex: '#ffffff', label: '白色' }
]

// 筛选条件
const selectedTags = ref<any[]>([])
const showTagSelector = ref(false)
const tagSearchQuery = ref('')
const selectedTagsInModal = ref<any[]>([])

// 当selectedTags变化时通知父组件
watch(selectedTags, (newTags) => {
  emit('update:selectedTags', newTags)
}, { deep: true })

const filters = ref({
  cameraModel: '',
  lensModel: '',
  focalLengthRange: [null, null] as [number | null, number | null],
  shutterSpeedRange: [null, null] as [number | null, number | null],
  apertureRange: [null, null] as [number | null, number | null],
  isoRange: [null, null] as [number | null, number | null],
  colorCategory: '',
  category: '',
  minQualityScore: 0,
  startDate: '',
  endDate: ''
})

// 监听外部传入的初始筛选条件
watch(() => props.initialFilters, async (newFilters) => {
  if (newFilters) {
    // 同步保存到内部变量，供首次打开面板时使用
    urlFilters.value = newFilters
    // 更新内部筛选状态
    if (newFilters.cameraModel !== undefined) filters.value.cameraModel = newFilters.cameraModel
    if (newFilters.lensModel !== undefined) filters.value.lensModel = newFilters.lensModel
    if (newFilters.minFocalLength !== undefined || newFilters.maxFocalLength !== undefined) {
      filters.value.focalLengthRange = [newFilters.minFocalLength || null, newFilters.maxFocalLength || null]
    }
    if (newFilters.minAperture !== undefined || newFilters.maxAperture !== undefined) {
      filters.value.apertureRange = [newFilters.minAperture || null, newFilters.maxAperture || null]
    }
    if (newFilters.minShutterSpeed !== undefined || newFilters.maxShutterSpeed !== undefined) {
      filters.value.shutterSpeedRange = [newFilters.minShutterSpeed || null, newFilters.maxShutterSpeed || null]
    }
    if (newFilters.minIso !== undefined || newFilters.maxIso !== undefined) {
      filters.value.isoRange = [newFilters.minIso || null, newFilters.maxIso || null]
    }
    if (newFilters.colorCategory !== undefined) filters.value.colorCategory = newFilters.colorCategory
    if (newFilters.category !== undefined) filters.value.category = newFilters.category
    if (newFilters.minQualityScore !== undefined) filters.value.minQualityScore = newFilters.minQualityScore
    if (newFilters.startDate !== undefined) filters.value.startDate = newFilters.startDate || ''
    if (newFilters.endDate !== undefined) filters.value.endDate = newFilters.endDate || ''

    // 处理标签ID - 需要从API加载标签详情
    if (newFilters.tagIds && newFilters.tagIds.length > 0) {
      // 确保筛选选项已加载
      if (!filterOptions.value.tags || filterOptions.value.tags.length === 0) {
        await loadFilterOptions()
      }
      // 从已加载的标签中找到匹配的标签
      const selected = filterOptions.value.tags.filter((tag: any) => newFilters.tagIds.includes(tag.id))
      if (selected.length > 0) {
        selectedTags.value = selected
      }
    }
  }
}, { immediate: true })

// 确保范围值的有效性：min <= max
const validateRange = (range: [number | null, number | null], type: string) => {
  const [min, max] = range
  if (min !== null && max !== null && min > max) {
    // 如果min > max，交换它们
    return [max, min] as [number | null, number | null]
  }
  return range
}

watch(() => filters.value.focalLengthRange, (newRange) => {
  filters.value.focalLengthRange = validateRange(newRange, 'focalLength')
}, { deep: true })

watch(() => filters.value.shutterSpeedRange, (newRange) => {
  filters.value.shutterSpeedRange = validateRange(newRange, 'shutterSpeed')
}, { deep: true })

watch(() => filters.value.apertureRange, (newRange) => {
  filters.value.apertureRange = validateRange(newRange, 'aperture')
}, { deep: true })

watch(() => filters.value.isoRange, (newRange) => {
  filters.value.isoRange = validateRange(newRange, 'iso')
}, { deep: true })

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

    // 只有在没有外部传入筛选参数时，才设置默认的全选范围
    if (!urlFilters.value) {
      setDefaultRanges()
    }
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

    // 如果有初始筛选参数，同步筛选面板状态
    // 优先使用 props.initialFilters，如果没有则使用内部保存的 urlFilters
    const filtersToApply = props.initialFilters || urlFilters.value
    if (filtersToApply) {
      await syncFiltersFromProps(filtersToApply)
    }

    // ensure ResizeObserver observes newly rendered mark containers
    if (resizeObserver) {
      if (focalMarksRef.value) resizeObserver.observe(focalMarksRef.value)
      if (shutterMarksRef.value) resizeObserver.observe(shutterMarksRef.value)
      if (apertureMarksRef.value) resizeObserver.observe(apertureMarksRef.value)
      if (isoMarksRef.value) resizeObserver.observe(isoMarksRef.value)
    }
    // recalc visible after DOM is rendered
    updateAllVisible()

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
    isAnimating.value = false
  }, 300)
}

// 可选标签（排除已选中的）
const availableTags = computed(() => {
  return filterOptions.value.tags.filter(tag => !isTagSelected(tag.id))
})

// 过滤后的标签（用于弹窗）
const filteredTags = computed(() => {
  const query = tagSearchQuery.value.toLowerCase()
  return filterOptions.value.tags.filter(tag =>
    tag.name.toLowerCase().includes(query)
  )
})

// 检测是否处于暗色模式
const isDarkMode = computed(() => {
  return themeStore.isDark
})

// 检查标签是否已选中
const isTagSelected = (tagId: number) => {
  return selectedTags.value.some(tag => tag.id === tagId)
}

// 检查标签在弹窗中是否选中
const isTagSelectedInModal = (tagId: number) => {
  return selectedTagsInModal.value.some(tag => tag.id === tagId)
}

// 切换单个标签选择
const toggleTagSelection = (tag: any) => {
  const index = selectedTagsInModal.value.findIndex(t => t.id === tag.id)
  if (index > -1) {
    selectedTagsInModal.value.splice(index, 1)
  } else {
    selectedTagsInModal.value.push(tag)
  }
}

// 不再需要全选功能

// 打开标签选择器
const openTagSelector = () => {
  selectedTagsInModal.value = [...selectedTags.value] // 初始化为当前已选标签
  showTagSelector.value = true
}

// 关闭标签选择器
const closeTagSelector = () => {
  showTagSelector.value = false
  tagSearchQuery.value = ''
  selectedTagsInModal.value = []
}

// 确认标签选择
const confirmTagSelection = async () => {
  selectedTags.value = [...selectedTagsInModal.value]

  // 立即应用筛选
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
    colorCategory: filters.value.colorCategory || null,
    category: filters.value.category || null,
    minQualityScore: filters.value.minQualityScore || null
  }

  try {
    if (hasEffectiveFilters(filterData)) {
      await photoStore.filterPhotos(filterData)
    } else {
      photoStore.clearLastFilters()
    }
    // 通知父组件筛选已应用，需要重置分页状态
    emit('filters-applied', filterData)
    closeTagSelector()
  } catch (error) {
    console.error('标签筛选失败:', error)
  }
}

// 移除标签
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
    colorCategory: filters.value.colorCategory || null,
    category: filters.value.category || null,
    minQualityScore: filters.value.minQualityScore || null,
    startDate: filters.value.startDate || null,
    endDate: filters.value.endDate || null
  }

  try {
    // 只有当有有效的筛选条件时才进行筛选
    if (hasEffectiveFilters(filterData)) {
      await photoStore.filterPhotos(filterData)
    } else {
      // 没有有效筛选条件时，清除之前的筛选状态
      photoStore.clearLastFilters()
    }

    // 通知父组件更新选中的标签状态
    emit('update:selectedTags', selectedTags.value)
    // 通知父组件筛选已应用，需要重置分页状态
    emit('filters-applied', filterData)

    closePanel()
  } catch (error) {
    console.error('筛选失败:', error)
    // 可以在这里添加用户友好的错误提示
    alert('筛选失败，请稍后重试')
  }
}

// 检查筛选条件是否有实际限制（非默认值）
const hasEffectiveFilters = (filterData: any) => {
  return (
    (filterData.tagIds && filterData.tagIds.length > 0) ||
    (filterData.cameraModel && filterData.cameraModel.trim() !== '') ||
    (filterData.lensModel && filterData.lensModel.trim() !== '') ||
    (filterData.colorCategory && filterData.colorCategory.trim() !== '') ||
    (filterData.category && filterData.category.trim() !== '') ||
    (filterData.minQualityScore && filterData.minQualityScore > 0) ||
    (filterData.minFocalLength !== null && filterData.minFocalLength !== undefined) ||
    (filterData.maxFocalLength !== null && filterData.maxFocalLength !== undefined) ||
    (filterData.minShutterSpeed !== null && filterData.minShutterSpeed !== undefined) ||
    (filterData.maxShutterSpeed !== null && filterData.maxShutterSpeed !== undefined) ||
    (filterData.minAperture !== null && filterData.minAperture !== undefined) ||
    (filterData.maxAperture !== null && filterData.maxAperture !== undefined) ||
    (filterData.minIso !== null && filterData.minIso !== undefined) ||
    (filterData.maxIso !== null && filterData.maxIso !== undefined) ||
    (filterData.startDate && filterData.startDate.trim() !== '') ||
    (filterData.endDate && filterData.endDate.trim() !== '')
  )
}

// 内部重置方法（用于重置按钮）
const resetFiltersInternal = async () => {
  setDefaultRanges()
  filters.value.cameraModel = ''
  filters.value.lensModel = ''
  filters.value.colorCategory = ''
  filters.value.category = ''
  filters.value.minQualityScore = 0
  filters.value.startDate = ''
  filters.value.endDate = ''
  selectedTags.value = []
  // 清除筛选状态
  photoStore.clearLastFilters()
  // 设置标志，下次打开面板时重置UI状态
  shouldResetOnOpen.value = true
  // 触发页面刷新 - 通知父组件
  emit('reset')
}

// 外部可调用的重置方法（只重置UI状态，不清除后端筛选）
const resetFilters = () => {
  setDefaultRanges()
  filters.value.cameraModel = ''
  filters.value.lensModel = ''
  filters.value.colorCategory = ''
  filters.value.category = ''
  filters.value.minQualityScore = 0
  filters.value.startDate = ''
  filters.value.endDate = ''
  selectedTags.value = []
  // 设置标志，下次打开面板时重置UI状态
  shouldResetOnOpen.value = true
}

// 清除日期范围
const clearDateRange = () => {
  filters.value.startDate = ''
  filters.value.endDate = ''
}

// 打开/关闭面板的处理，确保按需加载筛选选项（避免刷新时自动请求）
const onTogglePanel = async () => {
  // 如果正在动画过程中，忽略点击
  if (isAnimating.value) {
    return
  }

  const opening = !show.value
  show.value = opening
  isAnimating.value = true
  if (opening) {
    // 如果有重置标志，说明刚刚清除了筛选，重置UI状态
    if (shouldResetOnOpen.value) {
      setDefaultRanges()
      filters.value.cameraModel = ''
      filters.value.lensModel = ''
      filters.value.colorCategory = ''
      filters.value.category = ''
      filters.value.minQualityScore = 0
      filters.value.startDate = ''
      filters.value.endDate = ''
      selectedTags.value = []
      shouldResetOnOpen.value = false
    }
    if (!filterOptions.value || !filterOptions.value.cameraModels || filterOptions.value.cameraModels.length === 0) {
      await loadFilterOptions().catch(err => console.error('加载筛选选项失败:', err))
    }
    await nextTick()
    requestAnimationFrame(() => {
      animating.value = true
      // 动画完成后重置动画标志
      setTimeout(() => {
        isAnimating.value = false
      }, 300)
    })
  } else {
    animating.value = false
    setTimeout(() => {
      show.value = false
      isAnimating.value = false
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

// 获取当前选中颜色的标签
const getCurrentColorLabel = () => {
  if (!filters.value.colorCategory) return ''
  const color = colorOptions.find(c => c.value === filters.value.colorCategory)
  return color ? color.label : filters.value.colorCategory
}

// 获取对比色（用于标签文字颜色）
const getContrastColor = (hexColor: string) => {
  if (!hexColor) return '#374151' // 默认深色文字

  // 移除#号
  const color = hexColor.replace('#', '')

  // 转换为RGB
  const r = parseInt(color.substr(0, 2), 16)
  const g = parseInt(color.substr(2, 2), 16)
  const b = parseInt(color.substr(4, 2), 16)

  // 计算亮度 (YIQ公式)
  const brightness = (r * 299 + g * 587 + b * 114) / 1000

  // 返回黑色或白色文字
  return brightness > 128 ? '#374151' : '#f9fafb'
}

// 获取暗色模式下的对比色（确保在深色背景下可见）
const getDarkModeContrastColor = (hexColor: string) => {
  if (!hexColor) return '#f9fafb' // 默认白色文字

  // 移除#号
  const color = hexColor.replace('#', '')

  // 转换为RGB
  const r = parseInt(color.substr(0, 2), 16)
  const g = parseInt(color.substr(2, 2), 16)
  const b = parseInt(color.substr(4, 2), 16)

  // 计算亮度 (YIQ公式)
  const brightness = (r * 299 + g * 587 + b * 114) / 1000

  // 在暗色模式下，确保文字在深色弹窗背景下可见
  // 如果标签背景较浅，使用深色文字；如果标签背景较深，使用浅色文字
  return brightness > 160 ? '#1f2937' : '#f9fafb' // 深灰色或白色
}

// 选择颜色并立即应用筛选
const selectColor = async (colorValue: string) => {
  filters.value.colorCategory = colorValue
  // 立即应用筛选
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
    colorCategory: filters.value.colorCategory || null,
    category: filters.value.category || null,
    minQualityScore: filters.value.minQualityScore || null
  }

  try {
    if (hasEffectiveFilters(filterData)) {
      await photoStore.filterPhotos(filterData)
    } else {
      // 没有有效筛选条件时，清除筛选并刷新页面显示所有照片
      photoStore.clearLastFilters()
      await photoStore.filterPhotos({})
    }
    // 通知父组件筛选已应用，需要重置分页状态
    emit('filters-applied', filterData)
    // 自动关闭面板
    closePanel()
  } catch (error) {
    console.error('颜色筛选失败:', error)
  }
}

// 选择分类并立即应用筛选
const selectCategory = async (categoryValue: string) => {
  filters.value.category = categoryValue
  // 立即应用筛选
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
    colorCategory: filters.value.colorCategory || null,
    category: filters.value.category || null,
    minQualityScore: filters.value.minQualityScore || null
  }

  try {
    if (hasEffectiveFilters(filterData)) {
      await photoStore.filterPhotos(filterData)
    } else {
      // 没有有效筛选条件时，清除筛选并刷新页面显示所有照片
      photoStore.clearLastFilters()
      // 刷新图墙页面 - 使用 filterPhotos 传入空对象来获取所有照片
      await photoStore.filterPhotos({})
    }
    // 通知父组件筛选已应用，需要重置分页状态
    emit('filters-applied', filterData)
    // 自动关闭面板
    closePanel()
  } catch (error) {
    console.error('分类筛选失败:', error)
  }
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

// 处理标记点击 - 直接设置标记值到距离最近的滑块
const handleMarkClick = (event: MouseEvent, type: string, mark: any) => {
  event.stopPropagation() // 防止触发轨道点击

  const adjustedValue = mark === '不限' ? null : mark

  // 确保range数组已初始化
  const rangeKey = `${type}Range`
  if (!filters.value[rangeKey]) {
    filters.value[rangeKey] = [null, null]
  }

  // 获取当前范围值
  const currentRange = filters.value[rangeKey]
  const [currentMin, currentMax] = currentRange

  // 获取对应的marks数组
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

  // 决定设置哪个滑块的逻辑
  if (currentMin === null && currentMax === null) {
    // 两个都是null，设置最小值
    filters.value[rangeKey][0] = adjustedValue
  } else if (currentMin === null) {
    // 只有最小值为null，设置最小值
    filters.value[rangeKey][0] = adjustedValue
  } else if (currentMax === null) {
    // 只有最大值为null，设置最大值
    filters.value[rangeKey][1] = adjustedValue
  } else {
    // 两个都有值，基于刻度索引距离选择最近的滑块
    const clickedIndex = marks.indexOf(mark)
    const minIndex = marks.indexOf(currentMin)
    const maxIndex = marks.indexOf(currentMax)

    if (clickedIndex === -1 || minIndex === -1 || maxIndex === -1) {
      // 如果找不到索引，回退到数值距离计算
      const minDiff = Math.abs(adjustedValue - currentMin)
      const maxDiff = Math.abs(adjustedValue - currentMax)
      if (minDiff <= maxDiff) {
        filters.value[rangeKey][0] = adjustedValue
      } else {
        filters.value[rangeKey][1] = adjustedValue
      }
    } else {
      // 基于刻度索引距离计算
      const minIndexDiff = Math.abs(clickedIndex - minIndex)
      const maxIndexDiff = Math.abs(clickedIndex - maxIndex)
      if (minIndexDiff <= maxIndexDiff) {
        filters.value[rangeKey][0] = adjustedValue
      } else {
        filters.value[rangeKey][1] = adjustedValue
      }
    }
  }
}

// 处理轨道点击 - 移动距离点击位置最近的滑块
const handleTrackClick = (event: MouseEvent, type: string) => {
  // 防止与滑块拖拽冲突
  if (isDragging) return

  const container = (event.currentTarget as HTMLElement)
  const rect = container.getBoundingClientRect()
  const x = event.clientX - rect.left
  const clickPercent = Math.max(0, Math.min(100, (x / rect.width) * 100))

  const min = getCurrentMin(type)
  const max = getCurrentMax(type)
  const clickValue = min + (clickPercent / 100) * (max - min)

  // 根据类型调整步长
  let adjustedValue = clickValue
  switch (type) {
    case 'focalLength':
      const focalIndex = Math.round(clickPercent / 100 * (focalLengthMarks.length - 1))
      const clampedFocalIndex = Math.max(0, Math.min(focalLengthMarks.length - 1, focalIndex))
      const focalMark = focalLengthMarks[clampedFocalIndex]
      adjustedValue = focalMark === '不限' ? null : focalMark
      break
    case 'shutterSpeed':
      const shutterIndex = Math.round(clickPercent / 100 * (shutterSpeedMarks.length - 1))
      const clampedShutterIndex = Math.max(0, Math.min(shutterSpeedMarks.length - 1, shutterIndex))
      const shutterMark = shutterSpeedMarks[clampedShutterIndex]
      adjustedValue = shutterMark === '不限' ? null : shutterMark
      break
    case 'aperture':
      const apertureIndex = Math.round(clickPercent / 100 * (apertureMarks.length - 1))
      const clampedApertureIndex = Math.max(0, Math.min(apertureMarks.length - 1, apertureIndex))
      const apertureMark = apertureMarks[clampedApertureIndex]
      adjustedValue = apertureMark === '不限' ? null : apertureMark
      break
    case 'iso':
      const isoIndex = Math.round(clickPercent / 100 * (isoMarks.length - 1))
      const clampedIsoIndex = Math.max(0, Math.min(isoMarks.length - 1, isoIndex))
      const isoMark = isoMarks[clampedIsoIndex]
      adjustedValue = isoMark === '不限' ? null : isoMark
      break
  }

  // 确保range数组已初始化
  const rangeKey = `${type}Range`
  if (!filters.value[rangeKey]) {
    filters.value[rangeKey] = [null, null]
  }

  // 获取当前范围值
  const currentRange = filters.value[rangeKey]
  const [currentMin, currentMax] = currentRange

  // 如果两个滑块都是null，根据点击位置设置（左侧设置最小值，右侧设置最大值）
  if (currentMin === null && currentMax === null) {
    if (clickPercent < 50) {
      // 点击左侧，设置最小值
      filters.value[rangeKey][0] = adjustedValue
    } else {
      // 点击右侧，设置最大值
      filters.value[rangeKey][1] = adjustedValue
    }
    return
  }

  // 如果只有一个滑块被设置，设置另一个滑块
  if (currentMin === null) {
    filters.value[rangeKey][0] = adjustedValue
    return
  }
  if (currentMax === null) {
    filters.value[rangeKey][1] = adjustedValue
    return
  }

  // 两个滑块都被设置，基于刻度索引距离选择最近的滑块
  // 获取对应的marks数组
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

  const clickedIndex = marks.indexOf(adjustedValue)
  const minIndex = marks.indexOf(currentMin)
  const maxIndex = marks.indexOf(currentMax)

  if (clickedIndex === -1 || minIndex === -1 || maxIndex === -1) {
    // 如果找不到索引，回退到数值距离计算
    const minDiff = Math.abs(adjustedValue - currentMin)
    const maxDiff = Math.abs(adjustedValue - currentMax)
    if (minDiff <= maxDiff) {
      filters.value[rangeKey][0] = adjustedValue
    } else {
      filters.value[rangeKey][1] = adjustedValue
    }
  } else {
    // 基于刻度索引距离计算
    const minIndexDiff = Math.abs(clickedIndex - minIndex)
    const maxIndexDiff = Math.abs(clickedIndex - maxIndex)

    if (minIndexDiff < maxIndexDiff) {
      // 距离最小值更近，设置最小值
      filters.value[rangeKey][0] = adjustedValue
    } else if (maxIndexDiff < minIndexDiff) {
      // 距离最大值更近，设置最大值
      filters.value[rangeKey][1] = adjustedValue
    } else {
      // 距离相同时，根据点击位置相对于滑块条中心的位置来决定
      // 点击位置在左半边偏向设置最小值，右半边偏向设置最大值
      if (clickPercent < 50) {
        filters.value[rangeKey][0] = adjustedValue
      } else {
        filters.value[rangeKey][1] = adjustedValue
      }
    }
  }
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

// 处理键盘事件
const handleKeydown = (event: KeyboardEvent) => {
  if (event.key === 'Escape') {
    // 优先关闭标签选择器
    if (showTagSelector.value) {
      closeTagSelector()
    } else if (show.value) {
      // 然后关闭主筛选面板
      closePanel()
    }
  }
}

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

  // 添加ESC键监听
  document.addEventListener('keydown', handleKeydown)
})
onBeforeUnmount(() => {
  if (resizeObserver) {
    resizeObserver.disconnect()
    resizeObserver = null
  }
  // 移除ESC键监听
  document.removeEventListener('keydown', handleKeydown)
})

// 暴露方法给父组件调用
defineExpose({
  resetFilters,
  syncFromExternal
})
</script>

<style scoped>
/* 筛选按钮 SVG 动画样式 */
.filter-svg {
  @apply text-gray-700 dark:text-gray-200;
}

.filter-svg.is-hovering {
  @apply text-yellow-500;
}

/* 漏斗主体 */
.filter-body {
  transition: all 0.3s cubic-bezier(0.34, 1.56, 0.64, 1);
}

.filter-svg.is-hovering .filter-body {
  stroke-dasharray: 60;
  stroke-dashoffset: 60;
  animation: drawFilter 0.6s ease forwards;
}

/* 竖线 */
.filter-line {
  transition: all 0.3s ease;
}

.filter-svg.is-hovering .filter-line {
  stroke-dasharray: 20;
  stroke-dashoffset: 20;
  animation: drawLine 0.4s ease forwards 0.3s;
}

/* 绘制漏斗动画 */
@keyframes drawFilter {
  to {
    stroke-dashoffset: 0;
  }
}

/* 绘制竖线动画 */
@keyframes drawLine {
  to {
    stroke-dashoffset: 0;
  }
}

/* 慢速旋转动画 */
@keyframes spin-slow {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}

.animate-spin-slow {
  animation: spin-slow 2s linear infinite;
}

/* 毛玻璃效果 - 从一开始就生效 */
:deep(.filter-panel-glass) {
  border-radius: 16px;
  border: 1px solid rgba(148, 163, 184, 0.35);
  box-shadow: 0 18px 45px rgba(15, 23, 42, 0.18);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  background: rgba(248, 250, 252, 0.7);
}

/* 暗色模式弹窗样式 */
.dark-modal-bg {
  background: rgba(15, 23, 42, 0.8) !important;
  border-color: rgba(71, 85, 105, 0.5) !important;
  box-shadow: 0 25px 60px rgba(0, 0, 0, 0.8) !important;
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

/* 颜色圆点容器样式 */
.color-dots-container::-webkit-scrollbar {
  display: none;
}

.color-dots-container {
  -ms-overflow-style: none;
  scrollbar-width: none;
}

/* 为白色圆点添加特殊边框 */
.color-dots-container button[style*="ffffff"] {
  border-color: #d1d5db !important;
}

.dark .color-dots-container button[style*="ffffff"] {
  border-color: #374151 !important;
}
</style>

