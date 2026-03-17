<template>
  <!-- 自定义PhotoViewer -->
  <transition name="modal">
    <div
      v-if="visible"
      class="fixed inset-0 z-[60] bg-black/95 backdrop-blur-sm flex flex-col outline-none focus:outline-none overscroll-none"
      style="overflow: hidden; overscroll-behavior: none; overscroll-behavior-x: none;"
      @keydown.stop.prevent="onKeydown"
      @click="onBackdropClick"
      tabindex="0"
      ref="modalRoot"
    >
      <!-- 顶部栏 -->
      <div class="top-bar absolute top-0 left-0 right-0 z-20 flex items-center justify-between px-4 sm:px-6 py-3 text-white text-sm pointer-events-auto bg-black/40 backdrop-blur-md">
        <div class="flex items-center gap-3">
          <button class="btn-icon" @click="close" title="关闭">
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
          <button class="btn-icon" @click="toggleFullscreen" :title="isFullscreen ? '退出全屏' : '全屏查看'">
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
            class="btn-action bg-orange-600/80 hover:bg-orange-600"
            @click="viewingOriginal = true"
            title="查看原图"
          >
            查看原图
          </button>
          <!-- 返回缩略图按钮 -->
          <button
            v-if="viewingOriginal"
            class="btn-action bg-blue-600/80 hover:bg-blue-600"
            @click="viewingOriginal = false"
            title="返回缩略图"
          >
            返回缩略图
          </button>

          <button
            class="btn-icon"
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
      <div class="flex-1 relative min-h-0" :style="mainContentStyle" ref="mainContentArea">
        <!-- 移动端左右切换按钮 - 只在手机上显示 -->
        <button
          v-if="currentIndex > 0"
          class="nav-button left-2"
          @click.stop="prev"
          title="上一张"
        >
          <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 19l-7-7 7-7" />
          </svg>
        </button>
        <button
          v-if="currentIndex < photos.length - 1"
          class="nav-button right-2"
          @click.stop="next"
          title="下一张"
        >
          <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5l7 7-7 7" />
          </svg>
        </button>

        <!-- 图片显示容器 - 独立的空间，不受缩略图影响 -->
        <div
          class="absolute flex items-center justify-center"
          :style="imageContainerStyle"
          @click="onImageContainerClick"
          ref="imageViewport"
        >
          <!-- 图片包装容器 - 应用变换，使人脸框和 -->
          <div
            class="relative photo-viewer-img-wrapper"
            ref="imageWrapper"
            :style="imageTransformStyle"
          >
            <transition name="image-fade">
              <img
                v-if="currentPhoto"
                ref="mainImage"
                :src="getImageUrl(currentPhoto)"
                :alt="currentPhoto.filename"
                class="select-none cursor-grab active:cursor-grabbing main-image"
                :style="imageStyle"
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
            </transition>

            <!-- 人脸框 - 作为图片的子元素，会跟随图片变换 -->
            <transition-group
              :name="isSwitchingPhoto ? 'no-animation' : 'face-box'"
              tag="div"
              class="absolute inset-0"
            >
              <div
                v-for="(face, idx) in currentPhoto?.faces || []"
                :key="face.id"
                v-show="showFaceBoxes"
                class="absolute pointer-events-none"
                :style="getFaceBoxStyle(face)"
              >
                <div
                  class="absolute inset-0 border-2 rounded-sm shadow-lg"
                  :class="[getFaceColor(idx).border, getFaceColor(idx).shadow]"
                ></div>
                <div
                    class="absolute -top-5 left-0 text-xs px-2 py-1 rounded whitespace-nowrap backdrop-blur-sm"
                    :class="[getFaceColor(idx).text, 'bg-black/80']"
                >
                  {{ face.personName || '未命名' }}
                </div>
              </div>
            </transition-group>

            <!-- 焦点框 - 作为图片的子元素，会跟随图片变换 -->
            <transition :name="isSwitchingPhoto ? 'no-animation' : 'focus-box'">
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
            </transition>
          </div>
        </div>

          <!-- 拖拽切换指示器 -->
          <transition name="swipe-indicator">
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
          </transition>

          <!-- 加载状态 -->
          <div v-if="!currentPhoto" class="text-white/50">加载中...</div>
        </div>


        <!-- 信息栏遮罩层（信息栏宽度超过窗口一半时显示，点击关闭信息栏） -->
        <transition name="fade">
          <div
            v-if="showInfoOverlay"
            class="absolute inset-0 z-[5] bg-black/50 backdrop-blur-sm pointer-events-auto"
            @click="toggleInfo"
            title="点击关闭信息栏"
          ></div>
        </transition>

        <!-- 信息侧栏 -->
      <transition name="slide-right">
        <div
          v-if="!infoCollapsed"
          class="absolute top-12 right-0 text-white border-l border-white/10 flex flex-col overflow-auto pointer-events-auto z-10 info-panel"
          :class="infoTransparent ? 'bg-gray-900/30 backdrop-blur-sm' : 'bg-gray-900/90 backdrop-blur-md'"
          :style="{ maxHeight: infoPanelMaxHeight, width: infoPanelWidth + 'px' }"
        >
          <!-- 隐形调整把手（左边） -->
          <div
            class="absolute left-0 top-0 bottom-0 w-1 cursor-ew-resize hover:bg-white/20 transition-colors resize-handle"
            title="拖动调整宽度"
            @pointerdown.prevent="startResize"
            @pointermove="onResize"
            @pointerup="endResize"
            @pointercancel="endResize"
          ></div>
          <div class="flex items-center justify-between px-4 py-3 border-b border-white/10">
            <span class="text-sm font-semibold">信息</span>
            <div class="flex items-center gap-2">
              <!-- 人脸框切换按钮 -->
          <button
                v-if="currentPhoto?.faces?.length"
                class="btn-icon"
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
                class="btn-icon"
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
              <button class="btn-icon" @click="toggleInfoTransparency" :title="infoTransparent ? '切换到不透明' : '切换到透明'">
                <svg v-if="infoTransparent" class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z" />
                </svg>
                <svg v-else class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13.875 18.825A10.05 10.05 0 0112 19c-4.478 0-8.268-2.943-9.543-7a9.97 9.97 0 011.563-3.029m5.858.908a3 3 0 114.243 4.243M9.878 9.878l4.242 4.242M9.878 9.878L3 3m6.878 6.878L21 21" />
                </svg>
              </button>

              <!-- 分享按钮 -->
              <button class="btn-icon" @click="openPhotoPage" title="分享">
                <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M10 6H6a2 2 0 00-2 2v10a2 2 0 002 2h10a2 2 0 002-2v-4M14 4h6m0 0v6m0-6L10 14" />
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
                  class="tag-item"
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
                  class="flex items-center gap-2 p-2 rounded transition-all duration-200 min-w-0 flex-shrink-0 face-item"
                  :class="(f.isConfirmed && f.personId && f.personName) || (!f.personId || !f.personName) ? 'cursor-pointer hover:bg-white/10' : ''"
                  @click.stop="f.isConfirmed && f.personId && f.personName ? openPersonByFace(f) : (!f.personId || !f.personName) ? findSimilarFaces(f) : null"
                >
                  <div
                  class="w-8 h-8 rounded-full bg-gray-700 flex-shrink-0 overflow-hidden face-avatar"
                  :class="[getFaceColor(idx).border, 'border-2']"
                  :style="getFaceAvatarStyle(f)"
                  :title="getFaceTooltip(f)"
                ></div>
                  <div class="text-xs flex-1 min-w-0">
                    <div class="font-semibold truncate" :class="getFaceColor(idx).text">
                      {{ f.personName || '未命名' }}
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
                  class="color-swatch"
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
              <div class="ai-score-card">
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
                          class="badge-success"
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
                          class="badge-warning"
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
      <transition name="thumbnail-bar">
        <div
          v-if="!isFullscreen"
          class="absolute bottom-0 left-0 right-0 bg-black/40 backdrop-blur-md border-t border-white/10 overflow-x-auto overflow-y-hidden select-none pointer-events-auto z-10 thumbnail-bar"
          :style="{ height: Math.max(thumbHeight, thumbSize + 20) + 'px' }"
        >
          <div
            class="absolute inset-x-0 top-0 h-3 cursor-ns-resize border-b border-white/20 bg-black/40 z-20 drag-handle"
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
              class="thumbnail-item"
              :style="{ width: thumbSize + 'px', height: thumbSize + 'px' }"
              :class="idx === currentIndex ? 'active' : ''"
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
        <transition name="modal">
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
                  class="btn-icon"
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
                    class="similar-photo-item"
                    @click="jumpToPhoto(item.photoId)"
                  >
                    <div class="aspect-square bg-gray-800 rounded-lg overflow-hidden mb-2">
                      <img
                        v-if="item.photo.thumbnailPath"
                        :src="`/api/files${item.photo.thumbnailPath}`"
                        :alt="item.photo.filename"
                        class="w-full h-full object-cover"
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
  forceShowFaces?: boolean  // 强制显示人脸框（用于人物管理页面）
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
// 标记是否正在切换图片（切换时禁用框体动画）
const isSwitchingPhoto = ref(false)

// 图片显示和交互状态
const mainImage = ref<HTMLImageElement | null>(null)
const mainContentArea = ref<HTMLElement | null>(null)
const imageViewport = ref<HTMLElement | null>(null) // 图片可视区域（不受 transform 影响的参照系）
const imageWrapper = ref<HTMLElement | null>(null) // 图片包装容器（用于控制动画）
const imageSize = ref({ width: 0, height: 0 })
const imageLoaded = ref(false)
const isInitialLoad = ref(true) // 标记是否为初始加载
const scale = ref(1)
const translateX = ref(0)
const translateY = ref(0)
// 用户是否手动缩放过图片
const userHasManuallyZoomed = ref(false)
// 信息栏显示时的图片偏移
const infoPanelOffsetX = ref(0)
// 信息栏宽度（可调整）
const infoPanelWidth = ref(320)
// 上一次的信息栏偏移值（用于判断是否需要动画）
const lastInfoPanelOffsetX = ref(0)
// 是否正在动画信息栏（用于控制图片位置的动画）
const isInfoPanelAnimating = ref(false)
// 信息栏宽度调整状态
const isResizingInfoPanel = ref(false)
const resizeStartX = ref(0)
const resizeStartWidth = ref(320)

// 响应式窗口宽度（用于判断是否显示遮罩层）
const windowWidth = ref(window.innerWidth)

const onWindowResize = () => {
  windowWidth.value = window.innerWidth
}

// 判断信息栏遮罩层是否需要显示（信息栏宽度超过窗口一半时显示，点击遮罩可关闭信息栏）
const showInfoOverlay = computed(() => {
  return !infoCollapsed.value && infoPanelWidth.value > windowWidth.value / 2
})

// 记录鼠标/指针最近位置（用于触控板 pinch 时以“鼠标所在位置”为中心缩放）
const lastPointerPos = ref({ x: window.innerWidth / 2, y: window.innerHeight / 2 })

// 图片交互状态
const imageDragStartX = ref(0)
const imageDragStartY = ref(0)
const imageDragOffset = ref(0)
const imageDragVelocity = ref(0)
const isImageDragging = ref(false)
const isZooming = ref(false) // 是否正在缩放
const isPanning = ref(false) // 是否正在平移（触控板双指移动）

// 触摸相关状态
const touches = ref<Touch[]>([])
const initialDistance = ref(0)
const initialScale = ref(1)
const isPinching = ref(false)
const touchStartTime = ref(0) // 触摸开始时间，用于区分点击和拖拽
const touchStartDistance = ref(0) // 触摸开始时的距离，用于检测缩放意图
const lastTouchCenter = ref({ x: 0, y: 0 }) // 上次触摸中心点，用于双指移动
const initialTranslateX = ref(0) // 触摸开始时的 translateX
const initialTranslateY = ref(0) // 触摸开始时的 translateY

// 单指滑动切换照片相关
const touchSwipeStartX = ref(0)
const touchSwipeOffset = ref(0)

// 空白区域滑动相关
const backdropSwipeStartX = ref(0)
const backdropSwipeOffset = ref(0)
const isBackdropSwiping = ref(false)

// 双击放大相关
const lastTapTime = ref(0)
const lastTapX = ref(0)
const lastTapY = ref(0)

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

// 主内容区域样式 - 图片显示区域，不受缩略图影响
const mainContentStyle = computed(() => {
  // 主内容区域占满flex-1的空间
  return {
    height: '100%' // 占满flex-1的空间
  }
})

// 图片容器样式 - 在信息栏左边的区域内居中
// 当信息栏显示/隐藏时，容器宽度会变化，图片自动居中
const imageContainerStyle = computed(() => {
  // 顶部栏高度约 48px
  const topBarHeight = 48
  // 底部缩略图高度
  const bottomThumbHeight = isFullscreen.value ? 0 : thumbHeight.value
  // 信息栏宽度（使用动态值）
  const currentInfoPanelWidth = infoCollapsed.value ? 0 : infoPanelWidth.value

  // 计算图片显示尺寸
  let right = `${currentInfoPanelWidth}px`

  if (!infoCollapsed.value && imageSize.value.width && imageSize.value.height) {
    const imgWidth = imageSize.value.width
    const imgHeight = imageSize.value.height
    const containerWidth = window.innerWidth
    const containerHeight = window.innerHeight - topBarHeight - bottomThumbHeight

    // 计算图片显示宽度
    const scaleX = containerWidth / imgWidth
    const scaleY = containerHeight / imgHeight
    const scale = Math.min(scaleX, scaleY)
    const displayWidth = imgWidth * scale

    // 显示信息栏后，图片在信息栏左边区域居中
    // 新的左边位置 = (windowWidth - currentInfoPanelWidth - displayWidth) / 2
    const leftWithInfo = (containerWidth - currentInfoPanelWidth - displayWidth) / 2

    // 如果新位置 < 0，说明图片在信息栏展开时会左边超出窗口
    // 此时让左边=0，而不是强行居中导致溢出
    if (leftWithInfo < 0) {
      right = 'auto'
    }
  }

  return {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    // 容器从左侧到信息栏之间
    left: '0',
    right,
    top: `${topBarHeight}px`,
    bottom: `${bottomThumbHeight}px`,
    position: 'absolute' as const,
    // 只在信息栏展开/收起时启用动画
    transition: isInfoPanelAnimating.value ? 'right 0.3s ease' : 'none'
  }
})

// 图片样式 - 根据窗口尺寸动态调整，确保图片完整显示
// 宽度计算不减去信息栏（允许信息栏覆盖在图片上方）
const imageStyle = computed(() => {
  if (!currentPhoto.value || !imageSize.value.width || !imageSize.value.height) {
    return {}
  }

  const imgWidth = imageSize.value.width
  const imgHeight = imageSize.value.height

  // 顶部栏高度约 48px
  const topBarHeight = 48
  // 底部缩略图高度
  const bottomThumbHeight = isFullscreen.value ? 0 : thumbHeight.value
  // 可用空间尺寸（使用整个窗口宽度，不减去信息栏）
  const containerWidth = window.innerWidth
  const containerHeight = window.innerHeight - topBarHeight - bottomThumbHeight

  // 计算缩放比例，确保图片完整显示（取宽高缩放比例的最小值）
  const scaleX = containerWidth / imgWidth
  const scaleY = containerHeight / imgHeight
  const scale = Math.min(scaleX, scaleY)

  const finalWidth = imgWidth * scale
  const finalHeight = imgHeight * scale

  return {
    width: `${finalWidth}px`,
    height: `${finalHeight}px`,
    maxWidth: 'none',
    maxHeight: 'none',
    objectFit: 'contain'
  }
})

// 图片变换样式 - 用于缩放和拖拽
const imageTransformStyle = computed(() => {
  // 如果正在拖拽、缩放、平移或触摸操作，禁用过渡效果
  const isInteracting = isImageDragging.value || isZooming.value || isPanning.value || isPinching.value

  if (scale.value > 1) {
    return {
      // transform 从右到左应用：translate(...) scale(...) 表示先 scale 后 translate
      // 这样 translateX/Y 表示屏幕像素平移，不会被 scale 再次放大，缩放/平移更稳定
      transform: `translate(${translateX.value}px, ${translateY.value}px) scale(${scale.value})`,
      transformOrigin: 'center center',
      transition: isInteracting ? 'none' : 'transform 0.3s ease'
    }
  } else {
    // 未缩放时，只需要拖拽切换的偏移
    // 图片位置由 imageContainerStyle 控制，会自动跟随信息栏动画
    const dragOffsetX = imageDragOffset.value * 0.3
    return {
      transform: `translateX(${dragOffsetX}px)`,
      transformOrigin: 'center center',
      // 拖拽切换时禁用动画，信息栏动画由 imageContainerStyle 处理
      transition: isInteracting ? 'none' : 'transform 0.3s ease'
    }
  }
})

// 人脸颜色数组 - 为不同人脸分配不同颜色
const FACE_COLORS = [
  { border: 'border-blue-400', shadow: 'shadow-blue-400/50', text: 'text-blue-400', bg: 'bg-blue-400/20' },
  { border: 'border-green-400', shadow: 'shadow-green-400/50', text: 'text-green-400', bg: 'bg-green-400/20' },
  { border: 'border-yellow-400', shadow: 'shadow-yellow-400/50', text: 'text-yellow-400', bg: 'bg-yellow-400/20' },
  { border: 'border-purple-400', shadow: 'shadow-purple-400/50', text: 'text-purple-400', bg: 'bg-purple-400/20' },
  { border: 'border-pink-400', shadow: 'shadow-pink-400/50', text: 'text-pink-400', bg: 'bg-pink-400/20' },
  { border: 'border-cyan-400', shadow: 'shadow-cyan-400/50', text: 'text-cyan-400', bg: 'bg-cyan-400/20' },
  { border: 'border-orange-400', shadow: 'shadow-orange-400/50', text: 'text-orange-400', bg: 'bg-orange-400/20' },
  { border: 'border-red-400', shadow: 'shadow-red-400/50', text: 'text-red-400', bg: 'bg-red-400/20' },
  { border: 'border-indigo-400', shadow: 'shadow-indigo-400/50', text: 'text-indigo-400', bg: 'bg-indigo-400/20' },
  { border: 'border-teal-400', shadow: 'shadow-teal-400/50', text: 'text-teal-400', bg: 'bg-teal-400/20' },
]

// 获取人脸颜色 - 根据人脸在列表中的索引
const getFaceColor = (faceIndex: number) => {
  return FACE_COLORS[faceIndex % FACE_COLORS.length]
}

// 获取人脸在列表中的索引
const getFaceIndex = (face: any) => {
  if (!currentPhoto.value?.faces) return 0
  return currentPhoto.value.faces.findIndex(f => f.id === face.id)
}

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
    // 重新初始化框体状态（forceShowFaces需要在每次打开时生效）
    initializeBoxStates()
    // 聚焦到PhotoViewer以接收键盘事件
    nextTick(() => {
      modalRoot.value?.focus()
      // 滚动缩略图到当前图片（无动画，因为距离可能很长）
      scrollThumbIntoView(false)
    })
    // 打开时应用信息栏偏移
    applyInfoPanelOffset(false)
    console.log('👁️ PhotoViewer: 打开查看器，设置起始索引', {
      startIndex: props.startIndex,
      currentIndex: currentIndex.value,
      forceShowFaces: props.forceShowFaces
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
    // 重置用户缩放状态并重新应用信息栏偏移
    userHasManuallyZoomed.value = false
    applyInfoPanelOffset(false)
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

// 初始化框体状态：首次使用默认隐藏，但如果设置了forceShowFaces则强制显示
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

  // 如果设置了forceShowFaces，强制显示人脸框（但允许用户手动切换）
  if (props.forceShowFaces) {
    showFaceBoxes.value = true
    // 不设置userInteractedWithFaceBoxes，让用户可以手动切换
  } else if (savedFaceBoxes !== null) {
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
// 初始化信息栏宽度
const savedWidth = localStorage.getItem('pe-info-panel-width')
if (savedWidth) {
  infoPanelWidth.value = parseInt(savedWidth, 10)
}

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
  // 标记正在切换图片，禁用框体动画
  isSwitchingPhoto.value = true
  currentIndex.value = (currentIndex.value - 1 + props.photos.length) % props.photos.length
  // 标记为非初始加载，避免切换时的透明度闪烁
  isInitialLoad.value = false
  // 重置缩放和位置
  scale.value = 1
  translateX.value = 0
  translateY.value = 0
  // 重置用户缩放状态和信息栏偏移
  userHasManuallyZoomed.value = false
  // 重新计算信息栏偏移（而不是直接重置为0）
  applyInfoPanelOffset(false)
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
  // 标记正在切换图片，禁用框体动画
  isSwitchingPhoto.value = true
  currentIndex.value = (currentIndex.value + 1) % props.photos.length
  // 标记为非初始加载，避免切换时的透明度闪烁
  isInitialLoad.value = false
  // 重置缩放和位置
  scale.value = 1
  translateX.value = 0
  translateY.value = 0
  // 重置用户缩放状态和信息栏偏移
  userHasManuallyZoomed.value = false
  // 重新计算信息栏偏移（而不是直接重置为0）
  applyInfoPanelOffset(false)
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
  // 标记正在切换图片，禁用框体动画
  isSwitchingPhoto.value = true
  currentIndex.value = idx
  // 标记为非初始加载，避免切换时的透明度闪烁
  isInitialLoad.value = false
  // 重置缩放和位置
  scale.value = 1
  translateX.value = 0
  translateY.value = 0
  // 重置用户缩放状态和信息栏偏移
  userHasManuallyZoomed.value = false
  // 重新计算信息栏偏移（而不是直接重置为0）
  applyInfoPanelOffset(false)
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

// 计算信息栏显示时的图片偏移（确保图片不被遮挡）
// 返回值：负数表示向左移动，正数表示向右移动，0表示不需要移动
const calculateInfoPanelOffset = (): number => {
  // 如果用户手动缩放，不调整位置
  if (userHasManuallyZoomed.value) {
    return 0
  }

  // 图片显示尺寸
  const imgWidth = imageSize.value.width
  const imgHeight = imageSize.value.height

  if (!imgWidth || !imgHeight) {
    return 0
  }

  // 信息栏宽度（使用动态值）
  const currentInfoPanelWidth = infoPanelWidth.value
  // 顶部栏高度约 48px
  const topBarHeight = 48
  // 底部缩略图高度
  const bottomThumbHeight = isFullscreen.value ? 0 : thumbHeight.value
  // 可用容器高度
  const containerHeight = window.innerHeight - topBarHeight - bottomThumbHeight
  // 窗口宽度
  const windowWidth = window.innerWidth

  // 计算图片适应容器后的尺寸
  const scaleX = windowWidth / imgWidth
  const scaleY = containerHeight / imgHeight
  const displayScale = Math.min(scaleX, scaleY)
  const displayWidth = imgWidth * displayScale

  // 如果信息栏已隐藏，不需要偏移（图片在容器内居中）
  if (infoCollapsed.value) {
    return 0
  }

  // 减去信息栏后的可用宽度
  const availableWidth = windowWidth - currentInfoPanelWidth

  // 计算图片在剩余空间中的居中位置
  // 剩余空间的中心 = infoPanelWidth + (availableWidth - displayWidth) / 2
  // 窗口中心 = windowWidth / 2
  // 偏移 = 剩余空间中心 - 窗口中心
  const centerInAvailable = currentInfoPanelWidth + (availableWidth - displayWidth) / 2
  const centerInWindow = windowWidth / 2
  let offset = centerInAvailable - centerInWindow

  // 判断是否溢出：如果图片宽度 > 可用宽度，让左边=0而不是居中
  if (displayWidth > availableWidth) {
    // 图片比可用空间宽时，左边贴着窗口左边
    offset = 0
  }

  // 返回偏移量（负数表示向左移）
  return offset
}

// 应用信息栏偏移的函数
// animate: 是否带动画（切换信息栏显示/隐藏时为true，打开/切换图片时为false）
const applyInfoPanelOffset = (animate = false) => {
  // 保存上一次的偏移值
  lastInfoPanelOffsetX.value = infoPanelOffsetX.value
  const offset = calculateInfoPanelOffset()
  infoPanelOffsetX.value = offset

  // 如果不动画，禁用过渡效果
  if (!animate && imageWrapper.value) {
    imageWrapper.value.style.transition = 'none'
    requestAnimationFrame(() => {
      if (imageWrapper.value) {
        imageWrapper.value.style.transition = ''
      }
    })
  }
}

const toggleInfo = () => {
  const wasCollapsed = infoCollapsed.value
  infoCollapsed.value = !infoCollapsed.value
  localStorage.setItem('pe-info-collapsed', infoCollapsed.value ? '1' : '0')

  // 如果用户手动缩放过，不再自动调整图片位置
  if (userHasManuallyZoomed.value) {
    return
  }

  // 设置动画标记（因为信息栏有动画）
  isInfoPanelAnimating.value = true

  // 应用偏移（带动画）
  applyInfoPanelOffset(true)

  // 动画结束后重置标记
  setTimeout(() => {
    isInfoPanelAnimating.value = false
  }, 300)
}

const toggleInfoTransparency = () => {
  infoTransparent.value = !infoTransparent.value
  localStorage.setItem(STORAGE_KEY, infoTransparent.value ? '1' : '0')
}

// 开始调整信息栏宽度
const startResize = (e: PointerEvent) => {
  isResizingInfoPanel.value = true
  resizeStartX.value = e.clientX
  resizeStartWidth.value = infoPanelWidth.value
  ;(e.target as HTMLElement).setPointerCapture(e.pointerId)
}

// 调整信息栏宽度中
const onResize = (e: PointerEvent) => {
  if (!isResizingInfoPanel.value) return

  const deltaX = resizeStartX.value - e.clientX // 向左拖动时 deltaX > 0
  const newWidth = Math.max(200, Math.min(600, resizeStartWidth.value + deltaX))
  infoPanelWidth.value = newWidth

  // 调整图片位置（不带动画）
  applyInfoPanelOffset(false)
}

// 结束调整信息栏宽度
const endResize = (e: PointerEvent) => {
  if (!isResizingInfoPanel.value) return

  isResizingInfoPanel.value = false
  ;(e.target as HTMLElement).releasePointerCapture(e.pointerId)

  // 保存到 localStorage
  localStorage.setItem('pe-info-panel-width', infoPanelWidth.value.toString())
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

  // 点击根容器本身，直接关闭
  if (target === currentTarget) {
    close()
    return
  }
}

// 处理图片容器点击事件（点击空白处关闭）
const onImageContainerClick = (event: MouseEvent) => {
  // 如果正在拖拽，不处理点击
  if (wasDragging.value || isImageDragging.value) {
    return
  }

  const target = event.target as HTMLElement
  const currentTarget = event.currentTarget as HTMLElement

  // 如果点击的是容器本身（空白处），而不是图片或其他子元素，则关闭查看器
  if (target === currentTarget) {
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
      // 记录图片原始尺寸
      imageSize.value = {
        width: img.naturalWidth,
        height: img.naturalHeight
      }

      // 重置缩放和平移
      scale.value = 1
      translateX.value = 0
      translateY.value = 0

      // 应用信息栏偏移
      applyInfoPanelOffset(false)

      imageLoaded.value = true
      // 图片加载完成后，重置切换标记，允许框体动画
      isSwitchingPhoto.value = false

      console.log('📸 PhotoViewer 图片加载完成:', {
        filename: currentPhoto.value?.filename,
        naturalSize: `${img.naturalWidth}x${img.naturalHeight}`,
        windowSize: `${window.innerWidth}x${window.innerHeight}`,
        url: getImageUrl(currentPhoto.value!)
      })
    }
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
    // 标记用户手动缩放，之后信息栏不再自动调整图片位置
    userHasManuallyZoomed.value = true
  }

  // 人脸框和焦点框现在直接绑定在图片内部，会自动跟随变换

  e.preventDefault()
  e.stopPropagation()
}

// 切换放大/缩小（用于双击）
const toggleZoom = () => {
  if (scale.value > 1) {
    // 缩小到适应屏幕
    scale.value = 1
    translateX.value = 0
    translateY.value = 0
  } else {
    // 放大到2倍，中心点基于屏幕中心
    const viewportRect = imageViewport.value?.getBoundingClientRect()
    if (viewportRect) {
      const centerX = viewportRect.width / 2
      const centerY = viewportRect.height / 2
      translateX.value = 0
      translateY.value = 0
      scale.value = 2
    }
  }
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
  // 记录鼠标位置（即使没拖拽），用于触控板缩放以鼠标点为中心
  lastPointerPos.value = { x: e.clientX, y: e.clientY }
  if (!isImageDragging.value) return

  const deltaX = e.clientX - imageDragStartX.value
  const deltaY = e.clientY - imageDragStartY.value

  if (scale.value > 1) {
    // 放大状态下，移动图片
    // 放大状态下，1:1 跟随鼠标拖拽（更跟手）
    const dragSensitivity = 1.0
    translateX.value += deltaX * dragSensitivity
    translateY.value += deltaY * dragSensitivity

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
  touchStartTime.value = Date.now()

  if (e.touches.length === 2) {
    // 双指触摸：可能是缩放或移动
    const distance = getTouchDistance(e.touches[0], e.touches[1])
    touchStartDistance.value = distance
    
    // 计算双指中心点
    const centerX = (e.touches[0].clientX + e.touches[1].clientX) / 2
    const centerY = (e.touches[0].clientY + e.touches[1].clientY) / 2
    lastTouchCenter.value = { x: centerX, y: centerY }
    
    // 保存当前的缩放和位置状态
    initialScale.value = scale.value
    initialTranslateX.value = translateX.value
    initialTranslateY.value = translateY.value
    
    // 先假设是缩放，如果后续移动距离变化不大则确认为缩放
    isPinching.value = true
    isImageDragging.value = false
    
    console.log('🤏 PhotoViewer: 开始双指触摸', {
      distance: distance,
      center: { x: centerX, y: centerY }
    })
  } else if (e.touches.length === 1) {
    // 记录滑动开始的X坐标
    touchSwipeStartX.value = e.touches[0].clientX

    // 单指触摸：在放大状态下可以拖拽
    if (scale.value > 1) {
      isImageDragging.value = true
      imageDragStartX.value = e.touches[0].clientX
      imageDragStartY.value = e.touches[0].clientY
      initialTranslateX.value = translateX.value
      initialTranslateY.value = translateY.value

      console.log('👆 PhotoViewer: 开始单指触摸拖拽', {
        startX: imageDragStartX.value,
        startY: imageDragStartY.value
      })
    }
    isPinching.value = false
  }

  e.preventDefault()
}

const onImageTouchMove = (e: TouchEvent) => {
  if (e.touches.length === 2) {
    // 双指操作：可能是缩放或移动
    const currentDistance = getTouchDistance(e.touches[0], e.touches[1])
    const distanceChange = Math.abs(currentDistance - touchStartDistance.value)
    const distanceChangeRatio = distanceChange / touchStartDistance.value
    
    // 计算双指中心点
    const centerX = (e.touches[0].clientX + e.touches[1].clientX) / 2
    const centerY = (e.touches[0].clientY + e.touches[1].clientY) / 2
    const centerDeltaX = centerX - lastTouchCenter.value.x
    const centerDeltaY = centerY - lastTouchCenter.value.y
    const centerMoveDistance = Math.sqrt(centerDeltaX * centerDeltaX + centerDeltaY * centerDeltaY)
    
    // 判断是缩放还是移动：
    // 提高阈值，减少双指移动被误判为缩放（尤其是上下方向时的轻微距离抖动）
    // 如果距离变化超过15%或超过30px，认为是缩放
    // 否则如果中心点移动明显，认为是双指移动
    if (distanceChangeRatio > 0.15 || distanceChange > 30) {
      // 双指捏合缩放
      isPinching.value = true
      isImageDragging.value = false
      
      const scaleRatio = currentDistance / touchStartDistance.value
      const newScale = Math.max(0.5, Math.min(5, initialScale.value * scaleRatio))
      scale.value = newScale
      // 标记用户手动缩放
      if (newScale > 1) {
        userHasManuallyZoomed.value = true
      } else if (newScale === 1) {
        userHasManuallyZoomed.value = false
      }
      
      // 计算缩放中心点（双指中心点），同样用 viewport 做参照，避免使用被 transform 影响的 boundingRect
      const viewportRect = imageViewport.value?.getBoundingClientRect()
      if (viewportRect) {
        const centerRefX = viewportRect.left + viewportRect.width / 2
        const centerRefY = viewportRect.top + viewportRect.height / 2

        // 双指中心点相对 viewport 中心的向量（屏幕坐标）
        const px = centerX - centerRefX
        const py = centerY - centerRefY

        // 使用触摸开始时的 scale/translate 作为基准，避免累计误差
        const qx = (px - initialTranslateX.value) / initialScale.value
        const qy = (py - initialTranslateY.value) / initialScale.value

        translateX.value = px - newScale * qx
        translateY.value = py - newScale * qy
      }
      
      console.log('🤏 PhotoViewer: 双指缩放', {
        scale: newScale,
        distanceChange: distanceChange.toFixed(1)
      })
    } else if (centerMoveDistance > 5 && scale.value > 1) {
      // 双指移动（平移）
      isPinching.value = false
      isImageDragging.value = true
      
      translateX.value = initialTranslateX.value + centerDeltaX
      translateY.value = initialTranslateY.value + centerDeltaY
      
      console.log('👆 PhotoViewer: 双指移动', {
        deltaX: centerDeltaX.toFixed(1),
        deltaY: centerDeltaY.toFixed(1)
      })
    }
    
    // 更新中心点
    lastTouchCenter.value = { x: centerX, y: centerY }
  } else if (e.touches.length === 1 && isImageDragging.value && scale.value > 1) {
    // 单指拖拽移动（仅在放大状态下）
    const deltaX = e.touches[0].clientX - imageDragStartX.value
    const deltaY = e.touches[0].clientY - imageDragStartY.value

    translateX.value = initialTranslateX.value + deltaX
    translateY.value = initialTranslateY.value + deltaY

    console.log('👆 PhotoViewer: 单指拖拽移动', {
      deltaX: deltaX.toFixed(1),
      deltaY: deltaY.toFixed(1),
      translateX: translateX.value.toFixed(1),
      translateY: translateY.value.toFixed(1)
    })
  } else if (e.touches.length === 1 && scale.value <= 1) {
    // 单指水平滑动（仅在未放大状态下，用于切换照片）
    const currentX = e.touches[0].clientX
    const offset = currentX - touchSwipeStartX.value
    touchSwipeOffset.value = offset

    // 提供视觉反馈：图片跟随稍微移动（限制最大移动距离）
    const maxOffset = 100 // 最大移动距离
    const visualOffset = Math.max(-maxOffset, Math.min(maxOffset, offset))
    translateX.value = visualOffset
    // 稍微改变透明度提供切换提示
    translateY.value = 0

    console.log('👆 PhotoViewer: 单指滑动', {
      offset: offset.toFixed(1),
      visualOffset: visualOffset.toFixed(1)
    })
  }

  e.preventDefault()
}

const onImageTouchEnd = (e: TouchEvent) => {
  // 单指触摸结束时的处理
  if (e.touches.length === 0) {
    const swipeOffset = touchSwipeOffset.value
    const didSwitch = Math.abs(swipeOffset) > 50

    // 如果达到切换阈值，切换图片
    if (didSwitch) {
      if (swipeOffset > 0) {
        // 向右滑动：上一张
        prev()
      } else {
        // 向左滑动：下一张
        next()
      }
    } else {
      // 未达到切换阈值，弹回原位
      translateX.value = 0
    }

    // 弹回原位后重置偏移量
    touchSwipeOffset.value = 0

    // 检查是否是双击（两次点击间隔小于300ms，且距离小于30px）
    const now = Date.now()
    const tapTimeDiff = now - lastTapTime.value
    const tapDistance = Math.sqrt(
      Math.pow((e.changedTouches[0]?.clientX || 0) - lastTapX.value, 2) +
      Math.pow((e.changedTouches[0]?.clientY || 0) - lastTapY.value, 2)
    )

    if (tapTimeDiff < 300 && tapDistance < 30) {
      // 双击：切换放大/缩小
      toggleZoom()
    }

    // 记录这次点击的位置和时间
    lastTapTime.value = now
    lastTapX.value = e.changedTouches[0]?.clientX || 0
    lastTapY.value = e.changedTouches[0]?.clientY || 0
  }

  // 如果还有触摸点，更新状态
  if (e.touches.length === 1) {
    // 从双指变为单指，重置单指拖拽状态
    if (scale.value > 1) {
      isImageDragging.value = true
      imageDragStartX.value = e.touches[0].clientX
      imageDragStartY.value = e.touches[0].clientY
      initialTranslateX.value = translateX.value
      initialTranslateY.value = translateY.value
    }
    isPinching.value = false
  } else if (e.touches.length === 0) {
    // 所有触摸点都离开
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
  }

  touches.value = Array.from(e.touches)
  e.preventDefault()
}

// 滚轮事件处理（触控板双指缩放和移动）
const onImageWheel = (e: WheelEvent) => {
  e.preventDefault()
  e.stopPropagation()

  // 检测是否是缩放操作：
  // 1. Mac/Windows 触控板“捏合缩放”在浏览器中通常会带 ctrlKey（Chrome/Electron 等）
  // 2. 为避免把双指上下/左右移动误判为缩放，这里不再用 deltaY/deltaX 比例做推断
  const absDeltaY = Math.abs(e.deltaY)
  const absDeltaX = Math.abs(e.deltaX)
  
  // 缩放判断：只认 ctrlKey/metaKey（最可靠）
  const isZoomGesture = e.ctrlKey || e.metaKey

  if (isZoomGesture) {
    // 触控板缩放：平滑无级缩放
    isZooming.value = true
    isPanning.value = false // 确保不是平移状态
    
    // 计算缩放增量：增加灵敏度，使缩放更快
    // deltaY 的值通常在 -100 到 100 之间，我们将其转换为缩放因子
    // 用指数函数让缩放更“线性手感”（更像系统图片浏览）
    const zoomK = 0.008 // 越大越快（你反馈偏慢，这里明显加速）
    const newScale = Math.max(0.5, Math.min(5, scale.value * Math.exp(-e.deltaY * zoomK)))

    if (newScale !== scale.value) {
      // 用“图片可视区域（viewport）”做参照，避免使用被 transform 影响的 boundingRect 导致漂移
      const viewportRect = imageViewport.value?.getBoundingClientRect()
      if (viewportRect) {
        const centerX = viewportRect.left + viewportRect.width / 2
        const centerY = viewportRect.top + viewportRect.height / 2

        // 触控板 pinch 时，wheel 事件的 clientX/Y 在部分环境下会不稳定，
        // 这里优先用我们记录的“鼠标最近位置”
        const pointerX = lastPointerPos.value.x
        const pointerY = lastPointerPos.value.y

        // 指针点相对 viewport 中心的向量（屏幕坐标）
        const px = pointerX - centerX
        const py = pointerY - centerY

        // 当前变换：screen = center + translate + scale * q
        // => q = (p - translate) / scale
        const qx = (px - translateX.value) / scale.value
        const qy = (py - translateY.value) / scale.value

        // 缩放后保持该屏幕点不动：translate' = p - scale' * q
        translateX.value = px - newScale * qx
        translateY.value = py - newScale * qy
      }
      
      // 更新缩放（放在最后，避免 q 用到新 scale）
      scale.value = newScale

      // 标记用户手动缩放
      if (newScale > 1) {
        userHasManuallyZoomed.value = true
      } else if (newScale === 1) {
        userHasManuallyZoomed.value = false
      }

      // 人脸框和焦点框现在直接绑定在图片内部，会自动跟随变换
    }
    
    // 延迟重置缩放状态，允许平滑的连续缩放
    clearTimeout((onImageWheel as any).zoomTimeout)
    ;(onImageWheel as any).zoomTimeout = setTimeout(() => {
      isZooming.value = false
    }, 150)
  } else {
    // 触控板双指移动：平滑平移（仅在放大状态下）
    // 加一点阈值，过滤非常小的抖动，减少“误触”
    if (scale.value > 1 && (absDeltaX > 2 || absDeltaY > 2)) {
      isPanning.value = true
      isZooming.value = false // 确保不是缩放状态
      
      // 使用更平滑的移动系数
      const panSensitivity = 0.9 // 移动灵敏度（稍微加快一点，更跟手）
      const deltaX = e.deltaX * panSensitivity
      const deltaY = e.deltaY * panSensitivity

      translateX.value -= deltaX
      translateY.value -= deltaY
      
      // 延迟重置平移状态，避免移动结束后的过渡效果
      clearTimeout((onImageWheel as any).panTimeout)
      ;(onImageWheel as any).panTimeout = setTimeout(() => {
        isPanning.value = false
      }, 100)
    }
  }
}

// 键盘快捷键处理
const onKeyDown = (e: KeyboardEvent) => {
  // 左右键切换图片（始终可用，不受焦点限制）
  if (e.key === 'ArrowLeft') {
    e.preventDefault()
    prev()
    return
  } else if (e.key === 'ArrowRight') {
    e.preventDefault()
    next()
    return
  }

  // Ctrl + +/- 缩放
  if (e.ctrlKey && (e.key === '=' || e.key === '+' || e.key === '-')) {
    e.preventDefault()
    e.stopPropagation()

    const delta = e.key === '-' ? 0.8 : 1.25
    const newScale = Math.max(0.5, Math.min(5, scale.value * delta))

    if (newScale !== scale.value) {
      scale.value = newScale
      // 标记用户手动缩放
      if (newScale > 1) {
        userHasManuallyZoomed.value = true
      } else if (newScale === 1) {
        userHasManuallyZoomed.value = false
      }
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

const scrollThumbIntoView = (animate = true) => {
  nextTick(() => {
    const el = thumbItems.value[currentIndex.value]
    const container = thumbContainer.value
    if (el && container) {
      el.scrollIntoView({
        behavior: animate ? 'smooth' : 'auto',
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
  // 使用短路由 /a/ID
  const route = router.resolve({ path: `/a/${currentPhoto.value.albumId}` })
  window.open(route.href, '_blank')
}

// 打开照片详情页
const openPhotoPage = () => {
  if (!currentPhoto.value?.id) return
  const route = router.resolve({ path: `/photo/${currentPhoto.value.id}` })
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

// 跳转到搜索结果页面，搜索该人脸的相似人脸
const findSimilarFaces = (face: { id?: number }) => {
  if (!face.id) return
  const route = router.resolve({
    path: '/search',
    query: {
      faceId: face.id.toString()
    }
  })
  window.open(route.href, '_blank')
}

// 过滤函数
const filterByTakenAt = () => {
  if (!currentPhoto.value?.takenAt) return
  const date = new Date(currentPhoto.value.takenAt)
  // 格式化为 YYYY-MM-DD
  const dateStr = date.toISOString().slice(0, 10)
  // 设置同一天的开始和结束
  const filters = {
    startDate: dateStr,
    endDate: dateStr
  }
  // 打开随机页面
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
  // 直接使用当前显示的图片
  const firstPath = [
    photo.originalPath,
    photo.webpPath,
    photo.thumbnailPath,
    face.photoOriginalPath,
    face.photoThumbnailPath
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

  // 人脸中心坐标（0-1 范围转为百分比）
  const faceCenterX = (face.x || 0) + face.width / 2
  const faceCenterY = (face.y || 0) + face.height / 2

  // 计算相对于中心的偏移（-0.5 到 0.5）
  const offsetX = faceCenterX - 0.5
  const offsetY = faceCenterY - 0.5

  // 距离中心的欧几里得距离
  const distFromCenter = Math.sqrt(offsetX * offsetX + offsetY * offsetY)

  // 添加位置补偿：距离中心越远，补偿越大
  const compensationFactor = 0.3
  const compensationX = offsetX * compensationFactor * (distFromCenter / 0.5)
  const compensationY = offsetY * compensationFactor * (distFromCenter / 0.5)

  // 应用补偿后的中心坐标
  const centerX = (faceCenterX + compensationX) * 100
  const centerY = (faceCenterY + compensationY) * 100

  // 目标填充比例：人脸占满圆圈的 80%
  const fillRatio = 0.8
  let scalePercent = (fillRatio / face.width) * 100
  // 限制缩放范围
  scalePercent = Math.min(Math.max(scalePercent, 150), 500)

  return {
    backgroundImage: `url(${base})`,
    backgroundSize: `${scalePercent}%`,
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

    // 输出容器尺寸信息用于调试
    console.log('🏗️ PhotoViewer 容器信息:', {
      windowSize: `${window.innerWidth}x${window.innerHeight}`,
      imageContainerStyle: imageContainerStyle.value,
      visible: props.visible,
      currentPhoto: currentPhoto.value?.filename
    })
  })

  window.addEventListener('keydown', onKeyDown)
  window.addEventListener('resize', onWindowResize)
  window.addEventListener('resize', onImageLoad)
  document.addEventListener('fullscreenchange', onFullscreenChange)
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', onKeydown)
  window.removeEventListener('keydown', onKeyDown)
  window.removeEventListener('resize', onWindowResize)
  window.removeEventListener('resize', onImageLoad)
  document.removeEventListener('fullscreenchange', onFullscreenChange)
})
</script>

<style scoped>
/* 模态框显示/隐藏动画 */
.modal-enter-active {
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}
.modal-leave-active {
  transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
}
.modal-enter-from {
  opacity: 0;
}
.modal-leave-to {
  opacity: 0;
}

/* 图片淡入动画 */
.image-fade-enter-active {
  transition: opacity 0.2s ease;
}
.image-fade-leave-active {
  transition: opacity 0.15s ease;
}
.image-fade-enter-from,
.image-fade-leave-to {
  opacity: 0;
}

/* 滑动指示器动画 */
.swipe-indicator-enter-active,
.swipe-indicator-leave-active {
  transition: all 0.2s ease;
}
.swipe-indicator-enter-from,
.swipe-indicator-leave-to {
  opacity: 0;
  transform: scale(0.9);
}

/* 缩略图栏动画 */
.thumbnail-bar-enter-active,
.thumbnail-bar-leave-active {
  transition: all 0.3s ease;
}
.thumbnail-bar-enter-from,
.thumbnail-bar-leave-to {
  transform: translateY(100%);
  opacity: 0;
}

/* 顶部栏动画 */
.top-bar {
  animation: slideDown 0.3s ease;
}
@keyframes slideDown {
  from {
    transform: translateY(-100%);
    opacity: 0;
  }
  to {
    transform: translateY(0);
    opacity: 1;
  }
}

/* 导航按钮动画 */
.nav-button {
  @apply absolute top-1/2 -translate-y-1/2 z-10 p-2 rounded-full bg-black/30 hover:bg-black/50 text-white/80 hover:text-white transition-all duration-200 md:hidden touch-manipulation;
}
.nav-button:hover {
  transform: translateY(-50%) scale(1.1);
  box-shadow: 0 0 20px rgba(255, 255, 255, 0.3);
}
.nav-button:active {
  transform: translateY(-50%) scale(0.95);
}

/* 图标按钮动画 */
.btn-icon {
  @apply p-2 rounded transition-all duration-200;
}
.btn-icon:hover {
  @apply bg-white/10;
  transform: scale(1.1);
}
.btn-icon:active {
  transform: scale(0.9);
}

/* 操作按钮动画 */
.btn-action {
  @apply p-2 text-xs px-3 py-1.5 text-white font-medium transition-all duration-200 rounded;
}
.btn-action:hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.3);
}
.btn-action:active {
  transform: translateY(0);
}

/* 标签动画 */
.tag-item {
  @apply px-2 py-1 bg-white/10 rounded cursor-pointer transition-all duration-200;
}
.tag-item:hover {
  @apply bg-white/20;
  transform: translateY(-1px);
}

/* 人物项动画 */
.face-item {
  @apply rounded;
}
.face-item:hover {
  transform: translateX(4px) scale(1.02);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.3);
}
.face-item:active {
  transform: translateX(2px) scale(0.98);
  transition-duration: 0.1s;
}

/* 人脸头像动画 */
.face-avatar {
  @apply transition-all duration-300;
}
.face-item:hover .face-avatar {
  transform: scale(1.15) rotate(5deg);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.4);
}
.face-item:active .face-avatar {
  transform: scale(0.95);
  transition-duration: 0.1s;
}

/* 颜色色卡动画 */
.color-swatch {
  @apply inline-block w-4 h-4 rounded border border-white/10 cursor-pointer transition-all duration-150;
}
.color-swatch:hover {
  @apply border-white/30;
  transform: scale(1.2) rotate(15deg);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.3);
}
.color-swatch:active {
  transform: scale(1.1);
}

/* AI评分卡片动画 */
.ai-score-card {
  @apply bg-gradient-to-r from-yellow-500/10 to-orange-500/10 rounded-lg p-3 border border-yellow-500/20;
  animation: cardPulse 3s ease-in-out infinite;
}
@keyframes cardPulse {
  0%, 100% {
    box-shadow: 0 0 0 0 rgba(234, 179, 8, 0);
  }
  50% {
    box-shadow: 0 0 20px 0 rgba(234, 179, 8, 0.1);
  }
}

/* 徽章动画 */
.badge-success {
  @apply inline-flex items-center gap-1 text-xs px-3 py-1 bg-green-500/20 text-green-200 rounded-full whitespace-nowrap flex-shrink-0 transition-all duration-200;
}
.badge-success:hover {
  @apply bg-green-500/30;
  transform: translateY(-1px);
}

.badge-warning {
  @apply inline-flex items-center gap-1 text-xs px-3 py-1 bg-orange-500/20 text-orange-200 rounded-full whitespace-nowrap flex-shrink-0 transition-all duration-200;
}
.badge-warning:hover {
  @apply bg-orange-500/30;
  transform: translateY(-1px);
}

/* 缩略图项动画 */
.thumbnail-item {
  @apply relative flex-shrink-0 cursor-pointer border-2 transition-all duration-200 rounded-sm overflow-hidden border-transparent;
}
.thumbnail-item:hover {
  @apply opacity-100;
  transform: scale(1.02);
}
.thumbnail-item.active {
  @apply border-white;
  transform: scale(1.05);
  box-shadow: 0 0 15px rgba(255, 255, 255, 0.2);
}
.thumbnail-item.active:hover {
  transform: scale(1.07);
}

/* 相似照片项动画 */
.similar-photo-item {
  @apply cursor-pointer transition-all duration-200;
}
.similar-photo-item:hover {
  transform: translateY(-4px);
}
.similar-photo-item:hover img {
  transform: scale(1.05);
}

/* 信息面板调整把手动画 */
.resize-handle {
  @apply transition-all duration-200;
}
.resize-handle:hover {
  @apply bg-white/40;
  box-shadow: 0 0 10px rgba(255, 255, 255, 0.3);
}

/* 拖动把手动画 */
.drag-handle {
  @apply transition-all duration-200;
}
.drag-handle:hover {
  @apply bg-white/30;
  box-shadow: 0 -2px 10px rgba(255, 255, 255, 0.1);
}

/* 图片加载时的脉冲动画 */
.main-image.loading {
  animation: imagePulse 1.5s ease-in-out infinite;
}
@keyframes imagePulse {
  0%, 100% {
    opacity: 0.5;
  }
  50% {
    opacity: 0.8;
  }
}

/* 人脸框显示动画 */
.face-box-enter-active,
.face-box-leave-active {
  transition: all 0.3s ease;
}
.face-box-enter-from,
.face-box-leave-to {
  opacity: 0;
  transform: scale(0.8);
}

/* 无动画（切换图片时使用） */
.no-animation-enter-active,
.no-animation-leave-active {
  transition: none;
}
.no-animation-enter-from,
.no-animation-leave-to {
  opacity: 1;
  transform: scale(1);
}

/* 焦点框显示动画 */
.focus-box-enter-active,
.focus-box-leave-active {
  transition: all 0.3s ease;
}
.focus-box-enter-from,
.focus-box-leave-to {
  opacity: 0;
  transform: scale(0.8);
}

/* 滑入动画 (原有) */
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

/* 淡入淡出动画 (原有) */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.15s ease;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
