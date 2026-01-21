<template>
  <!-- 自定义PhotoViewer -->
  <transition name="fade">
    <div
      v-if="visible"
      class="fixed inset-0 z-50 bg-black/95 backdrop-blur-sm flex flex-col outline-none focus:outline-none"
      @keydown.stop.prevent="onKeydown"
      @click="onBackdropClick"
      tabindex="0"
      ref="modalRoot"
    >
      <!-- 顶部栏 -->
      <div class="flex items-center justify-between px-4 sm:px-6 py-3 text-white text-sm pointer-events-auto">
        <div class="flex items-center gap-3">
          <button class="p-2 hover:bg-white/10 rounded transition-colors" @click="close" title="关闭">
            <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
          <div class="text-xs sm:text-sm opacity-80 flex items-center gap-2">
            <span>{{ currentPhoto?.filename }}</span>
            <span class="opacity-60">({{ currentIndex + 1 }} / {{ photos.length }})</span>
          </div>
        </div>
        <div class="flex items-center gap-3">
          <button class="p-2 hover:bg-white/10 rounded transition-colors" @click="toggleFullscreen" :title="isFullscreen ? '退出全屏' : '全屏查看'">
            <svg v-if="!isFullscreen" class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 8V4h4M4 4l6 6M20 16v4h-4m4 0l-6-6M16 4h4v4m0-4l-6 6M8 20H4v-4m0 4l6-6" />
            </svg>
            <svg v-else class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 9H5V5m10 10h4v4M9 15H5v4m10-10h4V5" />
            </svg>
          </button>

          <!-- 查看原图按钮 -->
          <button
            v-if="viewOriginalEnabled && currentPhoto?.largeThumbPath && !viewingOriginal"
            class="p-2 hover:bg-white/10 rounded text-xs px-3 py-1.5 bg-orange-600/80 hover:bg-orange-600 text-white font-medium transition-all duration-200"
            @click="viewingOriginal = true"
            title="查看原图"
          >
            查看原图
          </button>
          <!-- 返回缩略图按钮 -->
          <button
            v-if="viewingOriginal"
            class="p-2 hover:bg-white/10 rounded text-xs px-3 py-1.5 bg-blue-600/80 hover:bg-blue-600 text-white font-medium transition-all duration-200"
            @click="viewingOriginal = false"
            title="返回缩略图"
          >
            返回缩略图
          </button>

          <button
            class="p-2 hover:bg-white/10 rounded transition-colors"
            @click="toggleInfo"
            :aria-pressed="!infoCollapsed"
            title="信息面板"
          >
            <svg v-if="infoCollapsed" class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 16h-1v-4h-1m1-4h.01M12 19a7 7 0 100-14 7 7 0 000 14z" />
            </svg>
            <svg v-else class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>
      </div>

      <!-- 主要图片显示区域 -->
      <div class="flex-1 flex items-center justify-center relative px-2 sm:px-6 min-h-0 overflow-hidden">
        <div class="relative w-full h-full flex items-center justify-center">
              <div class="relative w-full h-full flex items-center justify-center">
                <!-- 图片和框的组合容器 -->
                <div class="relative max-w-full max-h-full" :style="imageTransformStyle">
                <img
                  v-if="currentPhoto"
                ref="mainImage"
                :src="getImageUrl(currentPhoto)"
                :alt="currentPhoto.filename"
              class="max-w-full max-h-full object-contain select-none cursor-grab active:cursor-grabbing"
                @load="onImageLoad"
              @error="onImageError"
              @dblclick="onImageDoubleClick"
              @mousedown="onImageMouseDown"
              @mousemove="onImageMouseMove"
              @mouseup="onImageMouseUp"
              @mouseleave="onImageMouseUp"
              @touchstart="onImageTouchStart"
              @touchmove="onImageTouchMove"
              @touchend="onImageTouchEnd"
              @wheel="onImageWheel"
                />

                <!-- 人脸框 - 绑定在图片内部 -->
                <div
                  v-for="face in currentPhoto?.faces || []"
                  :key="face.id"
                  v-show="showFaceBoxes"
                  class="absolute pointer-events-none"
                  :style="getFaceBoxStyle(face)"
                >
                  <div
                    class="absolute inset-0 border-2 rounded-sm shadow-lg"
                    :class="face.isConfirmed ? 'border-green-400 shadow-green-400/50' : 'border-amber-400 shadow-amber-400/50'"
                  ></div>
                  <div
                      class="absolute -top-5 left-0 text-xs px-2 py-1 rounded whitespace-nowrap backdrop-blur-sm"
                      :class="face.isConfirmed ? 'text-green-400 bg-black/80' : 'text-amber-200 bg-black/80'"
                  >
                    {{ face.personName || (face.isConfirmed ? '未命名' : '未确认') }}
                  </div>
                </div>

                <!-- 焦点框 - 绑定在图片内部 -->
                <div
                  v-if="currentPhoto && showFocusBox && currentPhoto.focusX !== undefined && currentPhoto.focusY !== undefined"
                  class="absolute pointer-events-none"
                  :style="getFocusBoxStyle()"
                >
                  <div class="absolute inset-0 border-2 border-yellow-400 shadow-lg shadow-yellow-400/50 rounded-sm"></div>
                  <div class="absolute -top-6 left-0 text-xs text-yellow-400 bg-black/80 px-2 py-1 rounded whitespace-nowrap backdrop-blur-sm">
                    焦点 ({{ currentPhoto.focusX.toFixed(1) }}%, {{ currentPhoto.focusY.toFixed(1) }}%)
                  </div>
                </div>
              </div>
              </div>

          <!-- 拖拽切换指示器 -->
          <div
            v-if="isImageDragging && Math.abs(imageDragOffset) > 50"
            class="absolute inset-0 flex items-center justify-center pointer-events-none"
              >
                <div
              class="flex items-center gap-4 bg-black/50 backdrop-blur-sm rounded-full px-6 py-3 text-white"
            >
              <svg
                v-if="imageDragOffset > 0"
                class="w-6 h-6"
                fill="none"
                stroke="currentColor"
                viewBox="0 0 24 24"
              >
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 19l-7-7 7-7" />
              </svg>
              <span class="text-sm font-medium">
                {{ imageDragOffset > 0 ? '上一张' : '下一张' }}
              </span>
              <svg
                v-if="imageDragOffset < 0"
                class="w-6 h-6"
                fill="none"
                stroke="currentColor"
                viewBox="0 0 24 24"
              >
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5l7 7-7 7" />
              </svg>
                </div>
              </div>

          <!-- 加载状态 -->
          <div v-if="!currentPhoto" class="text-white/50">加载中...</div>
            </div>
          </div>


        <!-- 信息侧栏 -->
      <transition name="slide-right">
        <div
          v-if="!infoCollapsed"
          class="absolute top-12 right-0 w-80 text-white border-l border-white/10 flex flex-col overflow-auto pointer-events-auto z-10"
          :class="infoTransparent ? 'bg-gray-900/30 backdrop-blur-sm' : 'bg-gray-900/90 backdrop-blur-md'"
          :style="{ maxHeight: infoPanelMaxHeight }"
        >
          <div class="flex items-center justify-between px-4 py-3 border-b border-white/10">
            <span class="text-sm font-semibold">信息</span>
            <div class="flex items-center gap-2">
              <!-- 人脸框切换按钮 -->
          <button
                v-if="currentPhoto?.faces?.length"
                class="p-1.5 hover:bg-white/10 rounded transition-colors"
                :class="showFaceBoxes ? 'text-blue-400' : 'text-gray-400'"
                @click="toggleFaceBoxes"
                :title="showFaceBoxes ? '隐藏人脸框' : '显示人脸框'"
              >
                <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z" />
                </svg>
          </button>

              <!-- 聚焦框切换按钮 -->
          <button
                v-if="currentPhoto?.focusX !== undefined && currentPhoto?.focusY !== undefined"
                class="p-1.5 hover:bg-white/10 rounded transition-colors"
                :class="showFocusBox ? 'text-yellow-400' : 'text-gray-400'"
                @click="toggleFocusBox"
                :title="showFocusBox ? '隐藏聚焦框' : '显示聚焦框'"
              >
                <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5l7 7-7 7" />
                  <rect x="3" y="3" width="18" height="18" rx="2" ry="2" stroke-linecap="round" stroke-linejoin="round" stroke-width="2" />
                </svg>
          </button>

              <!-- 透明度切换按钮 -->
              <button class="p-1.5 hover:bg-white/10 rounded transition-colors" @click="toggleInfoTransparency" :title="infoTransparent ? '切换到不透明' : '切换到透明'">
                <svg v-if="infoTransparent" class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z" />
                </svg>
                <svg v-else class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13.875 18.825A10.05 10.05 0 0112 19c-4.478 0-8.268-2.943-9.543-7a9.97 9.97 0 011.563-3.029m5.858.908a3 3 0 114.243 4.243M9.878 9.878l4.242 4.242M9.878 9.878L3 3m6.878 6.878L21 21" />
                </svg>
            </button>
          </div>
          </div>
          <div class="flex-1 overflow-auto px-4 py-3 space-y-3 text-xs leading-relaxed">
            <!-- 基本信息 -->
        <div>
              <span class="opacity-60">文件名：</span>
              <span
                class="cursor-pointer hover:text-blue-300 transition-colors"
                @click="showSimilarPhotos"
                title="点击查看相似照片"
              >
                {{ currentPhoto?.filename }}
              </span>
              <span v-if="currentPhoto?.takenAt" class="opacity-60 ml-4 cursor-pointer hover:opacity-100 transition-opacity" @click="filterByTakenAt">
                {{ formatDate(currentPhoto.takenAt) }}
              </span>
            </div>
            <div v-if="(currentPhoto?.width && currentPhoto?.height) || currentPhoto?.fileSize">
              <span class="opacity-60">文件尺寸：</span>
              <span v-if="currentPhoto?.width && currentPhoto?.height">{{ currentPhoto.width }} × {{ currentPhoto.height }}</span>
              <span v-if="currentPhoto?.fileSize && (currentPhoto?.width && currentPhoto?.height)" class="ml-4">{{ formatFileSize(currentPhoto.fileSize) }}</span>
              <span v-else-if="currentPhoto?.fileSize">{{ formatFileSize(currentPhoto.fileSize) }}</span>
            </div>
            <div v-if="currentAlbumPath">
              <span class="opacity-60">路径：</span>
              <span
                class="truncate opacity-80 cursor-pointer hover:opacity-100 transition-opacity"
                :title="'点击跳转到相册: ' + currentAlbumPath"
                @click="openAlbum"
              >
                {{ currentAlbumPath }}
              </span>
            </div>

            <!-- 相机和镜头信息 -->
            <div v-if="currentPhoto?.cameraMake || currentPhoto?.cameraModel">
              <span class="opacity-60">相机：</span>
              <span class="cursor-pointer hover:opacity-100 transition-opacity" @click="filterByCamera">
                {{ currentPhoto.cameraMake ? currentPhoto.cameraMake + ' ' : '' }}{{ currentPhoto.cameraModel }}
              </span>
            </div>
            <div v-if="currentPhoto?.lensModel">
              <span class="opacity-60">镜头：</span>
              <span class="cursor-pointer hover:opacity-100 transition-opacity" @click="filterByLens">
                {{ currentPhoto.lensModel }}
              </span>
            </div>

            <!-- 参数网格布局 -->
            <div class="grid grid-cols-2 gap-3">
              <div v-if="currentPhoto?.focalLength">
                <span class="opacity-60">焦距：</span>
                <span class="cursor-pointer hover:opacity-100 transition-opacity" @click="filterByFocalLength">
                  {{ currentPhoto.focalLength }}mm
                </span>
              </div>
              <div v-if="currentPhoto?.aperture">
                <span class="opacity-60">光圈：</span>
                <span class="cursor-pointer hover:opacity-100 transition-opacity" @click="filterByAperture">
                  f/{{ currentPhoto.aperture }}
                </span>
              </div>

              <div v-if="currentPhoto?.shutterSpeed">
                <span class="opacity-60">快门：</span>
                <span class="cursor-pointer hover:opacity-100 transition-opacity" @click="filterByShutterSpeed">
                  {{ currentPhoto.shutterSpeed }}
                </span>
              </div>
              <div v-if="currentPhoto?.iso">
                <span class="opacity-60">ISO：</span>
                <span class="cursor-pointer hover:opacity-100 transition-opacity" @click="filterByIso">
                  {{ currentPhoto.iso }}
                </span>
              </div>


              </div>



            <!-- 标签 -->
            <div v-if="currentPhoto?.tags?.length">
              <span class="opacity-60">标签：</span>
              <span class="inline-flex flex-wrap gap-2 ml-1">
                <span
                  v-for="t in currentPhoto.tags.slice(0, 8)"
                  :key="t.id"
                  class="px-2 py-1 bg-white/10 rounded cursor-pointer hover:bg-white/20 transition-colors"
                  @click.stop="openTag(t)"
                >
                  {{ t.name }}
                </span>
              </span>
            </div>

            <!-- 人物列表 -->
            <div v-if="currentPhoto?.faces?.length">
              <span class="opacity-60">人物列表：</span>
              <div class="mt-2 flex flex-wrap gap-2 max-h-40 overflow-y-auto">
                <div
                  v-for="(f, idx) in visibleFaceList"
                  :key="f.id || idx"
                  class="flex items-center gap-2 p-2 rounded transition-colors min-w-0 flex-shrink-0"
                  :class="f.isConfirmed && f.personId && f.personName ? 'cursor-pointer hover:bg-white/10' : ''"
                  @click.stop="f.isConfirmed && f.personId && f.personName ? openPersonByFace(f) : null"
                >
                  <div
                    class="w-8 h-8 rounded-full bg-gray-700 flex-shrink-0 border border-white/10 overflow-hidden"
                    :style="getFaceAvatarStyle(f)"
                    :title="getFaceTooltip(f)"
                  ></div>
                  <div class="text-xs flex-1 min-w-0">
                    <div class="font-semibold truncate" :class="f.isConfirmed ? 'text-green-300' : 'text-amber-200'">
                      {{ f.personName || '未命名' }}
                    </div>
                    <div class="text-[11px] text-gray-400 truncate">
                      置信度: {{ f.confidence !== undefined ? (f.confidence * 100).toFixed(1) + '%' : '-' }}
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <!-- AI增强分析 -->
            <!-- 场景分类 -->
            <div v-if="currentPhoto?.primaryScene" class="mt-3">
              <span class="opacity-60">场景：</span>
              <span class="inline-flex items-center gap-2">
                <span class="px-2 py-1 bg-blue-500/20 text-blue-300 rounded text-xs">
                  {{ currentPhoto.primaryScene }}
                </span>
                <span v-if="currentPhoto.sceneConfidence" class="text-xs opacity-60">
                  {{ (currentPhoto.sceneConfidence * 100).toFixed(1) }}%
                </span>
              </span>
              </div>

            <!-- 情感分析 -->
            <div v-if="currentPhoto?.primaryEmotion" class="mt-3">
              <span class="opacity-60">情感：</span>
              <span class="inline-flex items-center gap-2">
                <span class="px-2 py-1 bg-purple-500/20 text-purple-300 rounded text-xs">
                  {{ currentPhoto.primaryEmotion }}
                </span>
                <span v-if="currentPhoto.emotionConfidence" class="text-xs opacity-60">
                  {{ (currentPhoto.emotionConfidence * 100).toFixed(1) }}%
                </span>
              </span>
            </div>


            <!-- 调色板 -->
            <div v-if="currentPhoto?.colorPalette?.length">
              <span class="opacity-60">调色板：</span>
              <span class="inline-flex items-center gap-2 ml-1">
              <span
                  v-for="(color, idx) in currentPhoto.colorPalette.slice(0, 8)"
                :key="idx"
                  class="inline-block w-4 h-4 rounded border border-white/10 cursor-pointer hover:border-white/30 hover:scale-110 transition-all duration-150"
                :style="{ backgroundColor: color }"
                :title="getColorTooltip(color)"
                @mouseenter="displayedColor = getColorHex(color)"
                @click="copyColorToClipboard(color)"
              ></span>
                <span class="font-mono text-xs ml-2">{{ displayedColor }}</span>
              </span>
            </div>

            <!-- AI评分信息（卡片容器，增加层次感） -->
            <div v-if="currentPhoto?.aiOverallScore" class="mt-4 pt-3 border-t border-white/10">
              <div class="bg-gradient-to-r from-yellow-500/10 to-orange-500/10 rounded-lg p-3 border border-yellow-500/20">
                <div class="flex items-center justify-between mb-3">
                  <span class="text-sm font-medium text-yellow-400">🤖 AI 智能评分</span>
                  <span class="text-xl font-bold text-yellow-400">{{ currentPhoto.aiOverallScore.toFixed(1) }}</span>
              </div>

                <!-- 分维度评分 -->
                <div class="grid grid-cols-3 gap-3 text-xs mb-3">
                  <div v-if="currentPhoto.aiTechnicalScore" class="text-center bg-white/5 rounded px-2 py-1">
                    <div class="opacity-70 text-[10px] uppercase tracking-wide">技术</div>
                    <div class="font-semibold text-sm">{{ Math.round(currentPhoto.aiTechnicalScore) }}</div>
                  </div>
                  <div v-if="currentPhoto.aiCompositionScore" class="text-center bg-white/5 rounded px-2 py-1">
                    <div class="opacity-70 text-[10px] uppercase tracking-wide">构图</div>
                    <div class="font-semibold text-sm">{{ Math.round(currentPhoto.aiCompositionScore) }}</div>
                </div>
                  <div v-if="currentPhoto.aiAppealScore" class="text-center bg-white/5 rounded px-2 py-1">
                    <div class="opacity-70 text-[10px] uppercase tracking-wide">吸引力</div>
                    <div class="font-semibold text-sm">{{ Math.round(currentPhoto.aiAppealScore) }}</div>
              </div>
            </div>

                <!-- 优点和不足详情 -->
                <div v-if="(currentPhoto?.aiStrengths?.length || currentPhoto?.aiWeaknesses?.length)" class="space-y-2">
                  <!-- 优点 -->
                  <div v-if="currentPhoto?.aiStrengths?.length" class="space-y-1">
                    <div class="flex items-center gap-2 text-xs">
                      <span class="text-green-400">✓</span>
                      <span class="text-green-300 font-medium">优点</span>
                    </div>
                    <div class="overflow-x-auto">
                      <div class="flex gap-2 pb-1">
                        <span
                          v-for="strength in currentPhoto.aiStrengths"
                          :key="strength"
                          class="inline-flex items-center gap-1 text-xs px-3 py-1 bg-green-500/20 text-green-200 rounded-full whitespace-nowrap flex-shrink-0"
                        >
                          {{ strength }}
                        </span>
                      </div>
          </div>
        </div>

                  <!-- 不足 -->
                  <div v-if="currentPhoto?.aiWeaknesses?.length" class="space-y-1">
                    <div class="flex items-center gap-2 text-xs">
                      <span class="text-orange-400">⚠</span>
                      <span class="text-orange-300 font-medium">不足</span>
                    </div>
                    <div class="overflow-x-auto">
                      <div class="flex gap-2 pb-1">
                        <span
                          v-for="weakness in currentPhoto.aiWeaknesses"
                          :key="weakness"
                          class="inline-flex items-center gap-1 text-xs px-3 py-1 bg-orange-500/20 text-orange-200 rounded-full whitespace-nowrap flex-shrink-0"
                        >
                          {{ weakness }}
                        </span>
                      </div>
                    </div>
      </div>

                  <!-- 改进建议 -->
                  <div v-if="currentPhoto?.aiImprovementSuggestions?.length" class="mt-2 pt-2 border-t border-white/10">
                    <div class="flex items-center gap-2 text-xs mb-1">
                      <span class="text-blue-400">💡</span>
                      <span class="text-blue-300 font-medium">改进建议</span>
                    </div>
                    <div class="text-xs text-blue-200/80 leading-relaxed">
                      {{ currentPhoto.aiImprovementSuggestions.slice(0, 2).join('；') }}
                      <span v-if="currentPhoto.aiImprovementSuggestions.length > 2" class="opacity-60">
                        ... 等{{ currentPhoto.aiImprovementSuggestions.length }}条建议
                      </span>
                    </div>
                  </div>
                </div>
              </div>
            </div>
                </div>
              </div>
      </transition>

      <!-- 底部缩略图横排 -->
      <transition name="fade">
        <div
          v-if="!isFullscreen"
          class="absolute bottom-0 left-0 right-0 bg-black/90 backdrop-blur-md border-t border-white/10 overflow-x-auto overflow-y-hidden select-none pointer-events-auto z-10"
          :style="{ height: Math.max(thumbHeight, thumbSize + 20) + 'px' }"
        >
          <div
            class="absolute inset-x-0 top-0 h-3 cursor-ns-resize border-b border-white/20 bg-black/40 z-20"
            @mousedown.prevent="startDrag"
            title="拖动调整高度"
          ></div>
          <div class="h-1"></div>
          <div
            class="flex items-center gap-2 px-4 py-2 min-w-max"
            ref="thumbContainer"
          >
            <div
              v-for="(p, idx) in photos"
              :key="p.id"
              class="relative flex-shrink-0 cursor-pointer border-2 transition-all duration-200 rounded-sm overflow-hidden"
              :style="{ width: thumbSize + 'px', height: thumbSize + 'px' }"
              :class="idx === currentIndex ? 'border-white scale-105 shadow-lg shadow-white/20' : 'border-transparent opacity-70 hover:opacity-100 hover:scale-102'"
              @click="jump(idx)"
              :ref="el => (thumbItems[idx] = el)"
            >
              <img
                :src="getThumbUrl(p)"
                :alt="p.filename"
                class="w-full h-full object-cover"
              />
              <div
                v-if="idx === currentIndex"
                class="absolute inset-0 ring-2 ring-white/80 rounded-sm"
              ></div>
            </div>
          </div>
        </div>
      </transition>

      <!-- 相似照片模态框 -->
        <transition name="fade">
          <div
            v-if="similarPhotosVisible"
            class="fixed inset-0 bg-black/80 backdrop-blur-sm z-50 flex items-center justify-center p-4"
            @click.self="similarPhotosVisible = false"
          >
            <div class="bg-gray-900/95 backdrop-blur-md rounded-lg max-w-4xl w-full max-h-[80vh] overflow-hidden border border-white/10">
              <!-- 头部 -->
              <div class="flex items-center justify-between p-4 border-b border-white/10">
                <h3 class="text-lg font-semibold text-white">相似照片</h3>
                <button
                  class="p-1 hover:bg-white/10 rounded transition-colors"
                  @click="similarPhotosVisible = false"
                >
                  <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
                  </svg>
                </button>
              </div>

              <!-- 内容 -->
              <div class="p-4 max-h-[60vh] overflow-y-auto">
                <div v-if="similarPhotosLoading" class="flex items-center justify-center py-8">
                  <div class="text-white/50">加载中...</div>
                </div>

                <div v-else-if="similarPhotos.length === 0" class="flex items-center justify-center py-8">
                  <div class="text-white/50">未找到相似照片</div>
                </div>

                <div v-else class="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 gap-4">
                  <div
                    v-for="item in similarPhotos"
                    :key="item.photoId"
                    class="group cursor-pointer"
                    @click="jumpToPhoto(item.photoId)"
                  >
                    <div class="aspect-square bg-gray-800 rounded-lg overflow-hidden mb-2">
                      <img
                        v-if="item.photo.thumbnailPath"
                        :src="`/api/files${item.photo.thumbnailPath}`"
                        :alt="item.photo.filename"
                        class="w-full h-full object-cover group-hover:scale-105 transition-transform duration-200"
                      />
                      <div v-else class="w-full h-full bg-gray-700 flex items-center justify-center">
                        <svg class="w-8 h-8 text-gray-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z" />
                        </svg>
                      </div>
                    </div>

                    <div class="text-xs text-white/80 truncate" :title="item.photo.filename">
                      {{ item.photo.filename }}
                    </div>

                    <div class="flex items-center justify-between mt-1">
                      <div class="text-xs text-blue-400">
                        {{ (item.similarityScore * 100).toFixed(1) }}% 相似
                      </div>
                      <div v-if="item.photo.takenAt" class="text-xs text-white/50">
                        {{ formatDate(item.photo.takenAt) }}
                      </div>
                    </div>

                    <div v-if="item.matchReasons.length > 0" class="mt-1">
                      <div class="text-xs text-green-400 truncate" :title="item.matchReasons.join(', ')">
                        {{ item.matchReasons[0] }}
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </transition>
    </div>
  </transition>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useUiSettings } from '@/composables/useUiSettings'
import { aiApi } from '@/api'
import type { Photo } from '@/stores/photo'

const props = defineProps<{
  photos: Photo[]
  visible: boolean
  startIndex?: number
  autoShowFaces?: boolean
  originRect?: { top: number; left: number; width: number; height: number } | null
  openOptions?: { highlightedFaceId?: number; highlightedClusterId?: number; highlightedPersonId?: number; highlightedFaceIds?: number[]; preferredFaceId?: number } | null
}>()

const emit = defineEmits<{
  (e: 'update:visible', val: boolean): void
  (e: 'viewer-index-change', payload: { index: number; photoId?: number; faceIds?: number[] }): void
}>()

// 核心状态
const currentIndex = ref(props.startIndex ?? 0)
const infoCollapsed = ref(true)
const infoTransparent = ref(false)
const modalRoot = ref<HTMLElement | null>(null)
const isFullscreen = ref(false)
const showFocusBox = ref(false)
const showFaceBoxes = ref(false)

// 图片显示和交互状态
const mainImage = ref<HTMLImageElement | null>(null)
const imageSize = ref({ width: 0, height: 0 })
const imageLoaded = ref(false)
const isInitialLoad = ref(true) // 标记是否为初始加载
const scale = ref(1)
const translateX = ref(0)
const translateY = ref(0)

// 图片交互状态
const imageDragStartX = ref(0)
const imageDragStartY = ref(0)
const imageDragOffset = ref(0)
const imageDragVelocity = ref(0)
const isImageDragging = ref(false)

// 触摸相关状态
const touches = ref<Touch[]>([])
const initialDistance = ref(0)
const initialScale = ref(1)
const isPinching = ref(false)

// 缩略图相关
const thumbContainer = ref<HTMLElement | null>(null)
const thumbItems = ref<any[]>([])
const thumbHeight = ref<number>(parseInt(localStorage.getItem('pe-thumb-height') || '112', 10) || 112)

// 调色板当前显示的颜色值
const displayedColor = ref('')

// 查看原图状态
const viewingOriginal = ref(false)

// 相似照片相关
const similarPhotos = ref<any[]>([])
const similarPhotosLoading = ref(false)
const similarPhotosVisible = ref(false)

// 防止拖拽后意外关闭的状态
const wasDragging = ref(false)

const { viewOriginalEnabled } = useUiSettings()

// 常量
const STORAGE_KEY = 'pe-info-transparent'
const FOCUS_BOX_KEY = 'pe-focus-box-visible'
const FACE_BOXES_KEY = 'pe-face-boxes-visible'
const THUMB_KEY = 'pe-thumb-height'

// 计算属性
const currentPhoto = computed(() => props.photos?.[currentIndex.value] || null)
const thumbSize = computed(() => Math.max(24, clampThumbHeight(thumbHeight.value - 24)))
const infoPanelMaxHeight = computed(() => {
  const height = thumbHeight.value || 0
  // 信息栏从 top-12 (48px) 开始，缩略图从底部开始，高度为 thumbHeight
  // 所以信息栏最大高度 = 100vh - 48px - thumbHeight
  return height > 0 ? `calc(100vh - ${48 + height}px)` : 'calc(100vh - 48px)'
})

// 图片变换样式
const imageTransformStyle = computed(() => {
  if (scale.value > 1) {
    return {
      transform: `scale(${scale.value}) translate(${translateX.value}px, ${translateY.value}px)`,
      transformOrigin: 'center center',
      transition: isImageDragging.value ? 'none' : 'transform 0.3s ease',
      opacity: (imageLoaded.value || !isInitialLoad.value) ? 1 : 0.3
    }
  } else {
    return {
      transform: `translateX(${imageDragOffset.value * 0.3}px)`,
      transformOrigin: 'center center',
      transition: isImageDragging.value ? 'none' : 'transform 0.3s ease',
      opacity: (imageLoaded.value || !isInitialLoad.value) ? 1 : 0.3
    }
  }
})

// 人脸框样式计算函数
const getFaceBoxStyle = (face: any) => {
  // 人脸坐标是相对的 (0-1)，直接转换为百分比定位
  const leftPct = (face.x || 0) * 100
  const topPct = (face.y || 0) * 100
  const widthPct = (face.width || 0) * 100
  const heightPct = (face.height || 0) * 100

  return {
    left: `${leftPct}%`,
    top: `${topPct}%`,
    width: `${Math.max(0.5, widthPct)}%`,
    height: `${Math.max(0.5, heightPct)}%`
  }
}

// 可见人脸列表
const visibleFaceList = computed(() => {
  if (!currentPhoto.value?.faces?.length) return []

  return currentPhoto.value.faces
})

// 获取当前相册路径
const currentAlbumPath = computed(() => {
  const photo = currentPhoto.value
  if (!photo?.originalPath) return null

  try {
    const pathParts = photo.originalPath.split('/').filter(p => p.length > 0)
    if (pathParts.length >= 5 && pathParts[0] === 'data' && pathParts[1] === 'photos') {
      return `${pathParts[2]}/${pathParts[3]}`
    } else if (pathParts.length >= 4 && pathParts[0] === 'data' && pathParts[1] === 'photos') {
      return `未分类/${pathParts[2]}`
    } else if (pathParts.length >= 3) {
      return `${pathParts[0]}/${pathParts[1]}`
    } else if (pathParts.length >= 2) {
      return `未分类/${pathParts[0]}`
    }
      return null
  } catch (error) {
    return null
  }
})

// 监听 visible 变化，重置状态
watch(() => props.visible, (newVisible) => {
  if (newVisible) {
    // 打开查看器时，根据 startIndex 设置当前索引
    currentIndex.value = props.startIndex ?? 0
    // 标记为初始加载
    isInitialLoad.value = true
    // 聚焦到PhotoViewer以接收键盘事件
    nextTick(() => {
      modalRoot.value?.focus()
      // 人脸框现在直接绑定在图片内部，无需额外计算
    })
    console.log('👁️ PhotoViewer: 打开查看器，设置起始索引', {
      startIndex: props.startIndex,
      currentIndex: currentIndex.value
    })
  } else {
    // 人脸框现在直接绑定在图片内部，无需清理
  }
})

// 监听 startIndex 变化
watch(() => props.startIndex, (newStartIndex) => {
  if (props.visible && newStartIndex !== undefined) {
    currentIndex.value = newStartIndex
    // 标记为非初始加载，避免切换时的透明度闪烁
    isInitialLoad.value = false
    // 重置缩放和位置
    scale.value = 1
    translateX.value = 0
    translateY.value = 0
    // 重置图片加载状态，确保人脸框重新计算
    nextTick(() => {
      imageLoaded.value = false
    })
    console.log('🔄 PhotoViewer: startIndex 变化，更新当前索引', {
      newStartIndex,
      currentIndex: currentIndex.value
    })
  }
})

// 监听图片加载状态变化，确保人脸框在图片加载完成后重新计算
watch(() => imageLoaded.value, (newLoaded) => {
  if (newLoaded) {
    console.log('🔄 PhotoViewer: 图片加载完成，人脸框将重新计算')
  // 人脸框现在直接绑定在图片内部，会自动更新
  } else {
    // 人脸框现在直接绑定在图片内部，无需清理
  }
})

// 人脸框和焦点框现在直接绑定在图片内部，
// 所有变换都会自动应用，无需额外计算

// 工具函数
const formatDate = (val?: string) => val ? val.slice(0, 10) : ''
const formatFileSize = (bytes?: number) => {
  if (!bytes || bytes === 0) return '未知'
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  const k = 1024
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + units[i]
}

const clampThumbHeight = (val: number) => Math.min(260, Math.max(60, val))

// 获取图片URL的工具函数
const getImageUrl = (photo: Photo) => {
  if (viewingOriginal.value && photo.originalPath) {
    return `/api/files${photo.originalPath}`
  }
  if (photo.largeThumbPath) {
    return `/api/files${photo.largeThumbPath}`
  }
  if (photo.webpPath) return `/api/files${photo.webpPath}`
  if (photo.originalPath) return `/api/files${photo.originalPath}`
  return ''
}

const getThumbUrl = (photo: Photo) => {
  if (photo.thumbnailPath) return `/api/files${photo.thumbnailPath}`
  return getImageUrl(photo)
}

// 焦点框样式
const getFocusBoxStyle = () => {
  if (!currentPhoto.value || currentPhoto.value.focusX === undefined || currentPhoto.value.focusY === undefined) return {}

  // 聚焦点在原始图片中的相对坐标 (0-1)
  const focusX = Number(currentPhoto.value.focusX) / 100
  const focusY = Number(currentPhoto.value.focusY) / 100

  // 聚焦框的大小（相对于图片大小的20%）
  const boxSize = 20 // 固定大小，因为框是相对于图片定位的

  // 计算聚焦框的位置（中心对齐到聚焦点）
  const leftPct = focusX * 100 - boxSize / 2
  const topPct = focusY * 100 - boxSize / 2

  return {
    left: `${leftPct}%`,
    top: `${topPct}%`,
    width: `${boxSize}%`,
    height: `${boxSize}%`
  }
}

// 保留的变量已在上面声明

// 跟踪用户是否手动操作过框体显示状态，避免自动覆盖用户设置
const userInteractedWithFocusBox = ref(false)
const userInteractedWithFaceBoxes = ref(false)

// 初始化框体状态：首次使用默认隐藏
const initializeBoxStates = () => {
  // 从localStorage恢复已保存的状态
  const savedFocusBox = localStorage.getItem(FOCUS_BOX_KEY)
  const savedFaceBoxes = localStorage.getItem(FACE_BOXES_KEY)

  if (savedFocusBox !== null) {
    showFocusBox.value = savedFocusBox === '1'
    userInteractedWithFocusBox.value = true
  } else {
    showFocusBox.value = false // 首次使用默认隐藏
  }

  if (savedFaceBoxes !== null) {
    showFaceBoxes.value = savedFaceBoxes === '1'
    userInteractedWithFaceBoxes.value = true
  } else {
    showFaceBoxes.value = false // 首次使用默认隐藏
  }
}

// 事件处理函数已在下面定义

// 初始化状态
infoCollapsed.value = localStorage.getItem('pe-info-collapsed') === '1'
infoTransparent.value = localStorage.getItem(STORAGE_KEY) === '1'

// 初始化框体状态已在上面声明

// 基本功能函数
const close = () => {
  emit('update:visible', false)
}

const toggleFullscreen = () => {
  if (!document.fullscreenElement) {
    modalRoot.value?.requestFullscreen?.()
    isFullscreen.value = true
    } else {
    document.exitFullscreen?.()
    isFullscreen.value = false
  }
}

const prev = () => {
  if (!props.photos?.length) return
  const oldIndex = currentIndex.value
  currentIndex.value = (currentIndex.value - 1 + props.photos.length) % props.photos.length
  // 标记为非初始加载，避免切换时的透明度闪烁
  isInitialLoad.value = false
  // 重置缩放和位置
  scale.value = 1
  translateX.value = 0
  translateY.value = 0
  // 重置图片加载状态
  nextTick(() => {
    imageLoaded.value = false
  })
  scrollThumbIntoView()

  // 人脸框和焦点框现在直接绑定在图片内部，会自动更新

  console.log('⬅️ PhotoViewer: 切换到上一张', {
    from: oldIndex,
    to: currentIndex.value,
    filename: currentPhoto.value?.filename
  })
}

const next = () => {
  if (!props.photos?.length) return
  const oldIndex = currentIndex.value
  currentIndex.value = (currentIndex.value + 1) % props.photos.length
  // 标记为非初始加载，避免切换时的透明度闪烁
  isInitialLoad.value = false
  // 重置缩放和位置
  scale.value = 1
  translateX.value = 0
  translateY.value = 0
  // 重置图片加载状态，确保人脸框重新计算
  nextTick(() => {
    imageLoaded.value = false
  })
  scrollThumbIntoView()
  console.log('➡️ PhotoViewer: 切换到下一张', {
    from: oldIndex,
    to: currentIndex.value,
    filename: currentPhoto.value?.filename
  })
}

const jump = (idx: number) => {
  const oldIndex = currentIndex.value
  currentIndex.value = idx
  // 标记为非初始加载，避免切换时的透明度闪烁
  isInitialLoad.value = false
  // 重置缩放和位置
  scale.value = 1
  translateX.value = 0
  translateY.value = 0
  // 重置图片加载状态，确保人脸框重新计算
  nextTick(() => {
    imageLoaded.value = false
  })
  scrollThumbIntoView()
  console.log('🔄 PhotoViewer: 跳转到指定图片', {
    from: oldIndex,
    to: idx,
    filename: currentPhoto.value?.filename
  })
}

const toggleInfo = () => {
  infoCollapsed.value = !infoCollapsed.value
  localStorage.setItem('pe-info-collapsed', infoCollapsed.value ? '1' : '0')
}

const toggleInfoTransparency = () => {
  infoTransparent.value = !infoTransparent.value
  localStorage.setItem(STORAGE_KEY, infoTransparent.value ? '1' : '0')
}

const toggleFocusBox = () => {
  showFocusBox.value = !showFocusBox.value
  localStorage.setItem(FOCUS_BOX_KEY, showFocusBox.value ? '1' : '0')
}

const toggleFaceBoxes = () => {
  showFaceBoxes.value = !showFaceBoxes.value
  localStorage.setItem(FACE_BOXES_KEY, showFaceBoxes.value ? '1' : '0')
}

const onBackdropClick = (event: MouseEvent) => {
  if (wasDragging.value) {
    wasDragging.value = false
    return
}

  const target = event.target as HTMLElement
  const currentTarget = event.currentTarget as HTMLElement

  if (target === currentTarget) {
    close()
    return
  }

  if (target.closest('.flex-1.flex.overflow-hidden') && !target.closest('img, button, svg') && !target.closest('.absolute.right-0.w-80')) {
    close()
  }
}

const onKeydown = (e: KeyboardEvent) => {
  if (!props.visible) return
  if (e.key === 'Escape') {
    close()
  } else if (e.key === 'ArrowLeft') {
    prev()
  } else if (e.key === 'ArrowRight') {
    next()
  }
}

const onImageLoad = () => {
  if (mainImage.value) {
    const img = mainImage.value
    if (img.complete && img.naturalWidth > 0 && img.naturalHeight > 0) {
      imageSize.value = {
        width: img.offsetWidth,
        height: img.offsetHeight
      }
      imageLoaded.value = true
      console.log('✅ PhotoViewer: 图片加载成功', {
        filename: currentPhoto.value?.filename,
        size: `${img.naturalWidth}x${img.naturalHeight}`,
        url: getImageUrl(currentPhoto.value!),
        photoId: currentPhoto.value?.id
      })
  } else {
      console.warn('⚠️ PhotoViewer: 图片加载不完整', {
        filename: currentPhoto.value?.filename,
        complete: img.complete,
        naturalWidth: img.naturalWidth,
        naturalHeight: img.naturalHeight
      })
      imageLoaded.value = false
    }
    } else {
    console.warn('⚠️ PhotoViewer: mainImage ref 不存在')
    }
  }

const onImageError = () => {
  // 图片加载失败，设置为已加载状态避免一直显示加载中
  console.error('❌ PhotoViewer: 图片加载失败', {
    currentPhoto: currentPhoto.value?.filename,
    imageUrl: currentPhoto.value ? getImageUrl(currentPhoto.value) : 'N/A',
    photoId: currentPhoto.value?.id,
    error: '图片加载失败，可能的原因：网络错误、文件不存在、权限问题等'
  })
  imageLoaded.value = true
}

// 图片双击放大/缩小
const onImageDoubleClick = (e: MouseEvent) => {
  const wasZoomed = scale.value > 1
  if (wasZoomed) {
    // 缩小到适应屏幕
    scale.value = 1
    translateX.value = 0
    translateY.value = 0
  } else {
    // 放大到2倍，中心点基于点击位置
    const rect = (e.currentTarget as HTMLElement).getBoundingClientRect()
    const centerX = rect.width / 2
    const centerY = rect.height / 2
    const clickX = e.clientX - rect.left
    const clickY = e.clientY - rect.top

    // 计算放大后的偏移，使点击点成为中心
    translateX.value = centerX - clickX
    translateY.value = centerY - clickY
    scale.value = 2
  }

  // 人脸框和焦点框现在直接绑定在图片内部，会自动跟随变换

  e.preventDefault()
  e.stopPropagation()
}

// 图片拖拽处理
const onImageMouseDown = (e: MouseEvent) => {
  if (e.button !== 0) return // 只处理左键

  isImageDragging.value = true
  imageDragStartX.value = e.clientX
  imageDragStartY.value = e.clientY

  if (scale.value > 1) {
    // 放大状态下，记录当前的translate位置
    imageDragOffset.value = 0
    imageDragVelocity.value = 0
    } else {
    // 原始大小状态下，重置切换相关状态
    imageDragOffset.value = 0
    imageDragVelocity.value = 0
  }

  // 防止文本选择
  e.preventDefault()
}

const onImageMouseMove = (e: MouseEvent) => {
  if (!isImageDragging.value) return

  const deltaX = e.clientX - imageDragStartX.value
  const deltaY = e.clientY - imageDragStartY.value

  if (scale.value > 1) {
    // 放大状态下，移动图片
    translateX.value += deltaX * 0.5 // 降低灵敏度
    translateY.value += deltaY * 0.5

    // 更新起始位置，用于下一次移动
    imageDragStartX.value = e.clientX
    imageDragStartY.value = e.clientY

    // 人脸框和焦点框现在直接绑定在图片内部，会自动跟随移动

    console.log('🖱️ PhotoViewer: 拖拽移动图片', {
      deltaX, deltaY,
      translateX: translateX.value,
      translateY: translateY.value
    })
    } else {
    // 原始大小状态下，如果是水平拖拽且距离足够，准备切换图片
    if (Math.abs(deltaX) > Math.abs(deltaY) && Math.abs(deltaX) > 10) {
      imageDragOffset.value = deltaX
      imageDragVelocity.value = deltaX - (imageDragOffset.value - deltaX)
    }
  }

  e.preventDefault()
}

const onImageMouseUp = () => {
  if (!isImageDragging.value) return

  if (scale.value <= 1) {
    // 只有在原始大小状态下才处理图片切换
    const offset = imageDragOffset.value
    const velocity = imageDragVelocity.value

    // 判断是否应该切换图片
    const threshold = 100 // 切换阈值
    const velocityThreshold = 300 // 速度阈值

    const shouldSwitch = Math.abs(offset) > threshold || Math.abs(velocity) > velocityThreshold

    if (shouldSwitch) {
      if (offset > 0) {
        // 向右拖拽，切换到上一张
        console.log('👆 PhotoViewer: 拖拽切换到上一张', {
          offset: offset.toFixed(1),
          velocity: velocity.toFixed(1),
          threshold,
          filename: currentPhoto.value?.filename
        })
        prev()
    } else {
        // 向左拖拽，切换到下一张
        console.log('👇 PhotoViewer: 拖拽切换到下一张', {
          offset: offset.toFixed(1),
          velocity: velocity.toFixed(1),
          threshold,
          filename: currentPhoto.value?.filename
        })
        next()
      }
    } else {
      console.log('🚫 PhotoViewer: 拖拽取消，未达到切换阈值', {
        offset: offset.toFixed(1),
        velocity: velocity.toFixed(1),
        threshold,
        filename: currentPhoto.value?.filename
      })
    }
  }

      // 重置状态
  isImageDragging.value = false
  imageDragOffset.value = 0
  imageDragVelocity.value = 0
}

// 触摸事件处理
const onImageTouchStart = (e: TouchEvent) => {
  touches.value = Array.from(e.touches)

  if (e.touches.length === 2) {
    // 双指触摸，开始捏合缩放
    isPinching.value = true
    initialDistance.value = getTouchDistance(e.touches[0], e.touches[1])
    initialScale.value = scale.value
    console.log('🤏 PhotoViewer: 开始捏合缩放', {
      initialDistance: initialDistance.value,
      initialScale: initialScale.value
    })
  } else if (e.touches.length === 1 && scale.value > 1) {
    // 单指触摸，在放大状态下开始拖拽
    isImageDragging.value = true
    imageDragStartX.value = e.touches[0].clientX
    imageDragStartY.value = e.touches[0].clientY
    console.log('👆 PhotoViewer: 开始触摸拖拽', {
      startX: imageDragStartX.value,
      startY: imageDragStartY.value
    })
  }

    e.preventDefault()
}

const onImageTouchMove = (e: TouchEvent) => {
  if (e.touches.length === 2 && isPinching.value) {
    // 双指捏合缩放
    const currentDistance = getTouchDistance(e.touches[0], e.touches[1])
    const scaleRatio = currentDistance / initialDistance.value
    const newScale = Math.max(0.5, Math.min(5, initialScale.value * scaleRatio))

    scale.value = newScale

    // 人脸框和焦点框现在直接绑定在图片内部，会自动跟随变换
  } else if (e.touches.length === 1 && isImageDragging.value && scale.value > 1) {
    // 单指拖拽移动（仅在放大状态下）
    const deltaX = e.touches[0].clientX - imageDragStartX.value
    const deltaY = e.touches[0].clientY - imageDragStartY.value

    translateX.value += deltaX
    translateY.value += deltaY

    // 更新起始位置
    imageDragStartX.value = e.touches[0].clientX
    imageDragStartY.value = e.touches[0].clientY

    // 人脸框和焦点框现在直接绑定在图片内部，会自动跟随移动

    console.log('👆 PhotoViewer: 触摸拖拽移动', {
      deltaX, deltaY,
      translateX: translateX.value,
      translateY: translateY.value
    })
  }

  e.preventDefault()
}

const onImageTouchEnd = (e: TouchEvent) => {
  if (isPinching.value) {
    isPinching.value = false
    console.log('🤏 PhotoViewer: 结束捏合缩放', {
      finalScale: scale.value
    })
  }

  if (isImageDragging.value) {
    isImageDragging.value = false
    console.log('👆 PhotoViewer: 结束触摸拖拽')
  }

  touches.value = []
  e.preventDefault()
}

// 滚轮事件处理（触控板双指缩放和移动）
const onImageWheel = (e: WheelEvent) => {
  e.preventDefault()
  e.stopPropagation()

  if (e.ctrlKey) {
    // Ctrl + 滚轮：缩放
    const delta = e.deltaY > 0 ? 0.9 : 1.1
    const newScale = Math.max(0.5, Math.min(5, scale.value * delta))

    if (newScale !== scale.value) {
      // 如果是第一次缩放，以鼠标位置为中心
      if (scale.value === 1) {
        const rect = (e.currentTarget as HTMLElement).getBoundingClientRect()
        const mouseX = e.clientX - rect.left
        const mouseY = e.clientY - rect.top
        const centerX = rect.width / 2
        const centerY = rect.height / 2

        translateX.value = centerX - mouseX
        translateY.value = centerY - mouseY
      }

      scale.value = newScale

      // 人脸框和焦点框现在直接绑定在图片内部，会自动跟随变换
    }
  } else {
    // 普通滚轮：移动（仅在放大状态下）
    if (scale.value > 1) {
      const deltaX = e.deltaX * 0.5
      const deltaY = e.deltaY * 0.5

      translateX.value -= deltaX
      translateY.value -= deltaY
    }
  }
}

// 键盘快捷键处理
const onKeyDown = (e: KeyboardEvent) => {
  // Ctrl + +/- 缩放
  if (e.ctrlKey && (e.key === '=' || e.key === '+' || e.key === '-')) {
    e.preventDefault()
    e.stopPropagation()

    const delta = e.key === '-' ? 0.8 : 1.25
    const newScale = Math.max(0.5, Math.min(5, scale.value * delta))

    if (newScale !== scale.value) {
      scale.value = newScale
      console.log('⌨️ 键盘缩放', {
        key: e.key,
        scale: newScale
      })
    }
  }

  // ESC 键已在组件级别处理，这里不需要重复
}

// 辅助函数：计算两点间距离
const getTouchDistance = (touch1: Touch, touch2: Touch): number => {
  const dx = touch1.clientX - touch2.clientX
  const dy = touch1.clientY - touch2.clientY
  return Math.sqrt(dx * dx + dy * dy)
}

const scrollThumbIntoView = () => {
  nextTick(() => {
    const el = thumbItems.value[currentIndex.value]
    const container = thumbContainer.value
    if (el && container) {
      el.scrollIntoView({
        behavior: 'smooth',
        inline: 'center',
        block: 'nearest'
      })
    }
  })
}

const startDrag = (e: MouseEvent) => {
  const startY = e.clientY
  const startHeight = thumbHeight.value

  const handleMouseMove = (moveEvent: MouseEvent) => {
    const deltaY = startY - moveEvent.clientY // 向上拖动增加高度
    const newHeight = Math.max(80, Math.min(300, startHeight + deltaY))
    thumbHeight.value = newHeight
  }

  const handleMouseUp = () => {
    // 保存到本地存储
    localStorage.setItem(THUMB_KEY, thumbHeight.value.toString())

    document.removeEventListener('mousemove', handleMouseMove)
    document.removeEventListener('mouseup', handleMouseUp)
    document.body.style.cursor = ''
    document.body.style.userSelect = ''
  }

  document.addEventListener('mousemove', handleMouseMove)
  document.addEventListener('mouseup', handleMouseUp)
  document.body.style.cursor = 'ns-resize'
  document.body.style.userSelect = 'none'
}

// 路由跳转函数
const router = useRouter()

const openAlbum = () => {
  if (!currentPhoto.value) return
  const route = router.resolve({ path: `/album/${currentPhoto.value.albumId}` })
  window.open(route.href, '_blank')
}

const openTag = (tag: any) => {
  if (!tag?.id) return
  const route = router.resolve({ path: '/wall', query: { tagId: tag.id, tagName: tag.name } })
  window.open(route.href, '_blank')
}

const openPersonByFace = (face: { personId?: number; personName?: string }) => {
  if (!face.personId || !face.personName) return
  const route = router.resolve({
    path: '/wall',
    query: {
      personId: face.personId,
      personName: face.personName
    }
  })
  window.open(route.href, '_blank')
}

// 过滤函数
const filterByTakenAt = () => {
  if (!currentPhoto.value?.takenAt) return
  const date = new Date(currentPhoto.value.takenAt)
  const year = date.getFullYear()
  const month = date.getMonth() + 1
  const filters = {
    takenAtYear: year,
    takenAtMonth: month
  }
  const route = router.resolve({ path: '/random', query: { filters: JSON.stringify(filters) } })
  window.open(route.href, '_blank')
}

const filterByCamera = () => {
  if (!currentPhoto.value?.cameraMake && !currentPhoto.value?.cameraModel) return
  const cameraModel = currentPhoto.value.cameraModel || ''
  const route = router.resolve({ path: '/random', query: { filters: JSON.stringify({ cameraModel }) } })
  window.open(route.href, '_blank')
}

const filterByLens = () => {
  if (!currentPhoto.value?.lensModel) return
  const lensModel = currentPhoto.value.lensModel
  const route = router.resolve({ path: '/random', query: { filters: JSON.stringify({ lensModel }) } })
  window.open(route.href, '_blank')
}

const filterByFocalLength = () => {
  if (!currentPhoto.value?.focalLength) return
  const focalLength = parseFloat(currentPhoto.value.focalLength.replace('mm', ''))
  const filters = {
    minFocalLength: focalLength,
    maxFocalLength: focalLength
  }
  const route = router.resolve({ path: '/random', query: { filters: JSON.stringify(filters) } })
  window.open(route.href, '_blank')
}

const filterByAperture = () => {
  if (!currentPhoto.value?.aperture) return
  const aperture = parseFloat(currentPhoto.value.aperture.replace('f/', ''))
  const filters = {
    minAperture: aperture,
    maxAperture: aperture
  }
  const route = router.resolve({ path: '/random', query: { filters: JSON.stringify(filters) } })
  window.open(route.href, '_blank')
}

const filterByShutterSpeed = () => {
  if (!currentPhoto.value?.shutterSpeed) return
  let shutterSpeed = currentPhoto.value.shutterSpeed
  if (shutterSpeed.startsWith('1/')) {
    shutterSpeed = (1 / parseInt(shutterSpeed.substring(2))).toString()
  }
  const filters = {
    minShutterSpeed: parseFloat(shutterSpeed),
    maxShutterSpeed: parseFloat(shutterSpeed)
  }
  const route = router.resolve({ path: '/random', query: { filters: JSON.stringify(filters) } })
  window.open(route.href, '_blank')
}

const filterByIso = () => {
  if (!currentPhoto.value?.iso) return
  const filters = {
    minIso: currentPhoto.value.iso,
    maxIso: currentPhoto.value.iso
  }
  const route = router.resolve({ path: '/random', query: { filters: JSON.stringify(filters) } })
  window.open(route.href, '_blank')
}

// 颜色相关函数
const copyColorToClipboard = async (color: string) => {
  try {
    const hexColor = color.startsWith('#') ? color : `#${color}`
    await navigator.clipboard.writeText(hexColor)
  } catch (err) {
    const textArea = document.createElement('textarea')
    const hexColor = color.startsWith('#') ? color : `#${color}`
    textArea.value = hexColor
    document.body.appendChild(textArea)
    textArea.select()
    document.execCommand('copy')
    document.body.removeChild(textArea)
  }
}

const getColorHex = (color: string) => {
  return color.startsWith('#') ? color : `#${color}`
}

const getColorTooltip = (color: string) => {
  if (!color) return ''
  const hexColor = color.startsWith('#') ? color : `#${color}`
  return `HEX: ${hexColor}`
}

// 人脸相关函数
const resolveFaceAvatarUrl = (face: any) => {
  const photo = currentPhoto.value
  if (!photo) return ''
  const firstPath = [
    face.photoThumbnailPath,
    face.photoOriginalPath,
    photo.thumbnailPath,
    photo.webpPath,
    photo.originalPath
  ].find(p => p && typeof p === 'string' && p.length > 0) || ''
  const base = firstPath
    ? firstPath.startsWith('/api/files') ? firstPath : `/api/files${firstPath}`
    : ''
  if (!base) return ''
  const prefix = '/api/files'
  if (base.startsWith(prefix)) {
    const raw = base.slice(prefix.length)
    return `${prefix}${encodeURI(raw)}`
  }
  return encodeURI(base)
}

const getFaceAvatarStyle = (face: any) => {
  const base = resolveFaceAvatarUrl(face)
  const hasSize = face?.width && face?.height && face.width > 0 && face.height > 0
  if (!base) {
    return { backgroundColor: '#374151', backgroundSize: 'cover', backgroundPosition: 'center center' }
  }
  if (!hasSize) {
    return { backgroundImage: `url(${base})`, backgroundSize: 'cover', backgroundPosition: 'center center' }
  }
  const centerX = ((face.x || 0) + face.width / 2) * 100
  const centerY = ((face.y || 0) + face.height / 2) * 100
  return {
    backgroundImage: `url(${base})`,
    backgroundSize: 'cover',
    backgroundPosition: `${centerX}% ${centerY}%`
  }
}

const getFaceTooltip = (face: any) => {
  const ratio = face?.width && face?.height ? (face.width / face.height).toFixed(2) : '-'
  const area = face?.width && face?.height ? (face.width * face.height * 100).toFixed(2) + '%' : '-'
  const conf = face?.confidence !== undefined ? (face.confidence * 100).toFixed(1) + '%' : '-'
  return `比例: ${ratio}，面积: ${area}，置信度: ${conf}`
}

// 相似照片相关函数
const showSimilarPhotos = async () => {
  if (!currentPhoto.value?.id) return

  similarPhotosLoading.value = true
  similarPhotosVisible.value = true

  try {
    const response = await aiApi.findSimilarPhotos(currentPhoto.value.id, 12)
    if (response.data.success) {
      similarPhotos.value = response.data.data
      } else {
      console.error('获取相似照片失败:', response.data.error)
      similarPhotos.value = []
    }
  } catch (error) {
    console.error('获取相似照片出错:', error)
    similarPhotos.value = []
  } finally {
    similarPhotosLoading.value = false
  }
}

const jumpToPhoto = (photoId: number) => {
  // 找到目标照片在当前列表中的索引
  const targetIndex = props.photos.findIndex(p => p.id === photoId)
  if (targetIndex >= 0) {
    // 跳转到目标照片
    currentIndex.value = targetIndex
    // 关闭相似照片模态框
    similarPhotosVisible.value = false
  } else {
    console.warn('目标照片不在当前列表中:', photoId)
  }
}

const onFullscreenChange = () => {
  isFullscreen.value = !!document.fullscreenElement
}

onMounted(() => {
  nextTick(() => {
    initializeBoxStates()
  })

  window.addEventListener('keydown', onKeyDown)
  window.addEventListener('resize', onImageLoad)
  document.addEventListener('fullscreenchange', onFullscreenChange)
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', onKeydown)
  window.removeEventListener('keydown', onKeyDown)
  window.removeEventListener('resize', onImageLoad)
  document.removeEventListener('fullscreenchange', onFullscreenChange)
})
</script>

<style scoped>
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.15s ease;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

.slide-right-enter-active,
.slide-right-leave-active {
  transition: transform 0.3s ease;
}
.slide-right-enter-from {
  transform: translateX(100%);
}
.slide-right-leave-to {
  transform: translateX(100%);
}
</style>
