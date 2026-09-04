<template>
  <!-- 自定义PhotoViewer -->
  <transition name="modal">
    <!-- closing 时继续保留节点播放图片缩回动画；事件通过 closing
         状态禁用并交给底层相册，动画结束后再真正移除。 -->
    <div
      v-if="visible || closing"
      class="fixed inset-0 z-[60] bg-black/95 backdrop-blur-sm flex flex-col outline-none focus:outline-none overscroll-none"
      :class="{
        'pointer-events-none viewer-inert': closing || !visible,
        'viewer-returning': returningToThumb && closingAnimationStarted
      }"
      style="overflow: hidden; overscroll-behavior: none; overscroll-behavior-x: none;"
      @keydown.stop.prevent="onKeydown"
      @click="onBackdropClick"
      tabindex="0"
      ref="modalRoot"
      :style="modalStyle"
    >
      <!-- 顶部栏 -->
      <div v-show="controlsVisible" class="top-bar absolute top-0 left-0 right-0 z-20 flex items-center justify-between px-4 sm:px-6 py-3 text-white text-sm pointer-events-auto bg-black/40 backdrop-blur-md">
        <div class="flex items-center gap-3">
          <!-- 必须写成 close()：@click="close" 会把 MouseEvent 当成第一个参数传进去 -->
          <button class="btn-icon" @click="close()" title="关闭">
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
          <div v-if="currentAdminActions.length" class="relative">
            <button class="btn-icon" @click.stop="toggleAdminMenu" title="管理操作">
              <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 6h.01M12 12h.01M12 18h.01" />
              </svg>
            </button>
            <div
              v-if="showAdminMenu"
              class="absolute right-0 mt-2 min-w-[180px] overflow-hidden rounded-2xl border border-white/10 bg-slate-950/95 shadow-[0_20px_60px_rgba(0,0,0,0.45)]"
            >
              <button
                v-for="action in currentAdminActions"
                :key="action.key"
                class="flex w-full items-center justify-between px-4 py-3 text-left text-sm transition hover:bg-white/8"
                :class="action.tone === 'danger' ? 'text-rose-200' : 'text-white/90'"
                @click.stop="triggerAdminAction(action.key)"
              >
                <span>{{ action.label }}</span>
                <span v-if="action.tone === 'danger'" class="text-[11px] text-rose-300/80">高风险</span>
              </button>
            </div>
          </div>
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

      <!-- 收回到缩略图的飞行层。
           查看器里的大图是 object-fit: contain、直角；缩略图是 object-fit: cover、
           8px 圆角且被 overflow:hidden 裁掉。直接把 contain 的图缩放到缩略图框会
           把画面压扁，而且到终点才突然出现裁切和圆角。
           这里改成：外层盒子从「图片当前的可见框」变形到「缩略图框」，圆角同步
           从 0 变到目标值；内层图片始终 object-fit: cover。起点时盒子的宽高比
           正好等于图片比例，cover 与 contain 等价、看到完整图像；随着盒子变形到
           缩略图的比例，裁切是连续长出来的。 -->
      <div
        v-if="flight"
        ref="flightLayer"
        class="closing-flight"
        :style="flightBoxStyle"
        aria-hidden="true"
      >
        <img
          :src="flight.src"
          ref="flightImage"
          class="closing-flight-image"
          :style="flightImageStyle"
          alt=""
        />
      </div>

      <!-- 主要图片显示区域 -->
      <div
        class="flex-1 relative min-h-0 touch-none"
        :style="mainContentStyle"
        ref="mainContentArea"
        @pointerdown="onImagePointerDown"
        @pointermove="onImagePointerMove"
        @pointerup="onImagePointerUp"
        @pointercancel="onImagePointerCancel"
        @click="onImageContainerClick"
      >
        <!-- 相册氛围特效 - 背景层（在图片下方） -->
        <AtmosphereEffects v-if="albumAtmosphereEffects.length > 0" :effects="albumAtmosphereEffects" layer-filter="background" />
        <!-- 图片显示容器 - 独立的空间，不受缩略图影响 -->
        <div
          class="absolute flex items-center justify-center z-[1] cursor-grab active:cursor-grabbing touch-none select-none"
          :style="imageContainerStyle"
          @wheel="onImageWheel"
          @dblclick="onImageDoubleClick"
          ref="imageViewport"
        >
          <!-- 前后邻图参与同一条滑动轨道，拖拽时实时露出 -->
          <div class="absolute inset-0 pointer-events-none overflow-hidden swipe-stage">
            <img
              v-if="trackPreviousPhoto"
              :src="getDisplayUrl(trackPreviousPhoto)"
              :alt="trackPreviousPhoto.filename"
              loading="eager"
              decoding="async"
              class="absolute left-1/2 top-1/2 select-none swipe-adjacent-image"
              :style="getAdjacentImageStyle('previous')"
            />
            <img
              v-if="trackNextPhoto"
              :src="getDisplayUrl(trackNextPhoto)"
              :alt="trackNextPhoto.filename"
              loading="eager"
              decoding="async"
              class="absolute left-1/2 top-1/2 select-none swipe-adjacent-image"
              :style="getAdjacentImageStyle('next')"
            />
          </div>
          <img
            v-if="openingPreviewVisible && currentPhoto"
            :src="getDisplayUrl(currentPhoto)"
            :alt="currentPhoto.filename"
            ref="openingPreviewImage"
            class="absolute left-1/2 top-1/2 z-[3] pointer-events-none select-none opening-preview-image"
            :style="openingPreviewStyle"
          />
          <!-- 图片包装容器 - 应用变换，使人脸框和 -->
          <div
            class="relative photo-viewer-img-wrapper"
            ref="imageWrapper"
            :style="imageTransformStyle"
          >
            <!-- 主图不再使用 Vue transition：切图轨道已经提供唯一的位移动画。
                 同时保留旧图和新图会在索引提交帧叠加，导致上下闪烁。 -->
            <img
              v-if="currentPhoto"
              :key="imageRetryToken"
              ref="mainImage"
              :src="displayedImageUrl"
              :alt="currentPhoto.filename"
              decoding="async"
              class="select-none main-image pointer-events-none"
              :style="{ ...imageStyle, opacity: openingPreviewVisible ? 0 : 1 }"
              @load="onDisplayedImageLoad"
              @error="onImageError"
            />
            <img
              v-if="currentPhoto && largeImagePreloadUrl"
              :key="`large-${currentPhoto.id}-${viewingOriginal}`"
              :src="largeImagePreloadUrl"
              class="absolute w-px h-px opacity-0 pointer-events-none"
              aria-hidden="true"
              @load="onLargeImageLoad"
            />

            <!-- 人脸框 - 作为图片的子元素，会跟随图片变换 -->
            <transition-group
              :name="isSwitchingPhoto ? 'no-animation' : 'face-box'"
              tag="div"
              class="absolute inset-0"
            >
              <div
                v-for="(face, idx) in visibleFaceList"
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

          <!-- 失败时才显示覆盖层；加载过程保留舞台，避免文字层造成视觉跳变。 -->
          <div v-if="imageLoadError" class="absolute inset-0 z-[2] flex items-center justify-center">
            <button class="rounded-full bg-black/65 px-4 py-2 text-xs text-white hover:bg-black/80" @click.stop="retryCurrentImage">图片加载失败，点击重试</button>
          </div>
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
            class="absolute left-0 top-0 bottom-0 w-2 cursor-ew-resize hover:bg-white/20 transition-colors resize-handle touch-none select-none"
            title="拖动调整宽度"
            @pointerdown.prevent="startResize"
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
          v-if="!isFullscreen && controlsVisible"
          class="absolute bottom-0 left-0 right-0 bg-black/40 backdrop-blur-md border-t border-white/10 overflow-x-auto overflow-y-hidden select-none pointer-events-auto z-10 thumbnail-bar"
          :style="{ height: Math.max(thumbHeight, thumbSize + 20) + 'px' }"
        >
          <div
            class="absolute inset-x-0 top-0 h-3 cursor-ns-resize border-b border-white/20 bg-black/40 z-20 drag-handle touch-none select-none"
            @pointerdown.prevent="startDrag"
            @pointermove="onThumbResizeMove"
            @pointerup="endThumbResize"
            @pointercancel="endThumbResize"
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
                        v-if="getThumbUrl(item.photo)"
                        :src="getThumbUrl(item.photo)"
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

      <!-- 相册氛围特效 - 前景层（覆盖在图片上方，pointer-events: none 不影响交互） -->
      <AtmosphereEffects v-if="albumAtmosphereEffects.length > 0" :effects="albumAtmosphereEffects" layer-filter="above" />
    </div>
  </transition>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { buildPublicPath } from '@/utils/publicRoute'
import { useUiSettings } from '@/composables/useUiSettings'
import { usePhotoStore } from '@/stores/photo'
import { aiApi } from '@/api'
import type { Photo } from '@/stores/photo'
import AtmosphereEffects from '@/components/AtmosphereEffects.vue'
import { buildPhotoAssetUrl } from '@/utils/photoUrl'
import { usePhotoViewerAssets } from '@/composables/usePhotoViewerAssets'
import type { PhotoAssetInput } from '@/composables/usePhotoViewerAssets'
import { usePhotoViewerNavigation } from '@/composables/usePhotoViewerNavigation'
import { useFlingTapRepair } from '@/composables/useFlingTapRepair'
import { prefersReducedMotion } from '@/composables/usePrefersReducedMotion'

type AdminMenuAction = {
  key: string
  label: string
  tone?: 'default' | 'danger'
}

const props = defineProps<{
  photos: Photo[]
  visible: boolean
  startIndex?: number
  autoShowFaces?: boolean
  forceShowFaces?: boolean  // 强制显示人脸框（用于人物管理页面）
  originRect?: { top: number; left: number; width: number; height: number } | null
  // 关闭时用来定位「当前这张照片」的缩略图。originRect 只记录了打开时点的那一张，
  // 翻过页之后再关闭就应该收回到当前照片的缩略图上，而不是最初那张。
  // 宿主页面按 photoId 返回缩略图的位置；返回 null 则退回 originRect。
  // radius 是缩略图的圆角（如 '8px'），用于让收回动画把圆角一起补出来。
  resolveOriginRect?: ((photoId: number, index: number) => { top: number; left: number; width: number; height: number; radius?: string } | null) | null
  openOptions?: { highlightedFaceId?: number; highlightedClusterId?: number; highlightedPersonId?: number; highlightedFaceIds?: number[]; preferredFaceId?: number } | null
  adminMenuActions?: AdminMenuAction[] | null
}>()

const emit = defineEmits<{
  (e: 'update:visible', val: boolean): void
  (e: 'viewer-index-change', payload: { index: number; photoId?: number; faceIds?: number[] }): void
  (e: 'admin-action', payload: { key: string; photo?: Photo; index: number }): void
  // 收回动画的起止。宿主页面据此把目标缩略图暂时藏起来，
  // 避免飞回去的图片和原图叠在一起穿帮。
  (e: 'return-transition', payload: { photoId: number | null; active: boolean }): void
}>()

// 核心状态
const photosLength = computed(() => props.photos?.length || 0)
const navigation = usePhotoViewerNavigation({ length: photosLength, index: props.startIndex ?? 0 })
const currentIndex = navigation.currentIndex
const infoCollapsed = ref(true)
const controlsVisible = ref(true)
const infoTransparent = ref(false)
const modalRoot = ref<HTMLElement | null>(null)
const isFullscreen = ref(false)
const showFocusBox = ref(false)
const showFaceBoxes = ref(false)
const showAdminMenu = ref(false)
// 标记是否正在切换图片（切换时禁用框体动画）
const isSwitchingPhoto = ref(false)

// 图片显示和交互状态
const mainImage = ref<HTMLImageElement | null>(null)
const mainContentArea = ref<HTMLElement | null>(null)
const imageViewport = ref<HTMLElement | null>(null) // 图片可视区域（不受 transform 影响的参照系）
const imageWrapper = ref<HTMLElement | null>(null) // 图片包装容器（用于控制动画）
const openingPreviewImage = ref<HTMLImageElement | null>(null) // 打开动画的 FLIP 预览层
const imageSize = ref({ width: 0, height: 0 })
const imageLoaded = ref(false)
const imageLoadError = ref(false)
const displayedImageUrl = ref('')
const largeImagePreloadUrl = ref('')
const largeImageReady = ref(false)
const openingTransformPrepared = ref(false)
const imageRetryToken = ref(0)
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
const activeInfoResizeHandle = ref<HTMLElement | null>(null)
const resizeStartX = ref(0)
const resizeStartWidth = ref(320)
const isResizingThumb = ref(false)
const activeThumbResizeHandle = ref<HTMLElement | null>(null)
const thumbResizeStartY = ref(0)
const thumbResizeStartHeight = ref(112)

// 响应式窗口宽度（用于判断是否显示遮罩层）
const windowWidth = ref(window.innerWidth)

const onWindowResize = () => {
  windowWidth.value = window.innerWidth
}

const toggleAdminMenu = () => {
  showAdminMenu.value = !showAdminMenu.value
}

const triggerAdminAction = (key: string) => {
  emit('admin-action', {
    key,
    photo: currentPhoto.value || undefined,
    index: currentIndex.value
  })
  showAdminMenu.value = false
}

// 判断信息栏遮罩层是否需要显示（信息栏宽度超过窗口一半时显示，点击遮罩可关闭信息栏）
const showInfoOverlay = computed(() => {
  return !infoCollapsed.value && infoPanelWidth.value > windowWidth.value / 2
})
const currentAdminActions = computed(() => props.adminMenuActions || [])

// 记录鼠标/指针最近位置（用于触控板 pinch 时以“鼠标所在位置”为中心缩放）
const lastPointerPos = ref({ x: window.innerWidth / 2, y: window.innerHeight / 2 })

// 图片交互状态
const imageDragStartX = ref(0)
const imageDragStartY = ref(0)
const imageDragOffset = ref(0)
const imageDragVelocity = ref(0)
const isImageDragging = ref(false)
// Resetting the track offset and committing the new index happen in one
// render. Disable the generic idle transition for that handoff frame so the
// outgoing wrapper cannot animate back through the center a second time.
const trackTransitionDisabled = ref(false)
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

// 统一 Pointer Events 手势状态（触摸、鼠标、触控笔共用一套路径）
const activePointers = new Map<number, { x: number; y: number }>()
const pointerGesture = ref<'none' | 'pending' | 'swipe' | 'pan' | 'pinch' | 'dismiss' | 'holdZoom'>('none')
const dismissOffset = ref(0)
const isDismissing = ref(false)
const pointerStartX = ref(0)
const pointerStartY = ref(0)
const pointerLastX = ref(0)
const pointerLastY = ref(0)
const pointerStartTime = ref(0)
const pointerPinchDistance = ref(0)
const pointerPinchScale = ref(1)
const pointerPinchTranslate = ref({ x: 0, y: 0 })
const pointerPinchCenter = ref({ x: 0, y: 0 })
const pointerSwipeBaseOffset = ref(0)
// 松手瞬时速度（px/ms，带符号）。不能用「总位移 / 总时长」代替：接管一次过渡后
// 轨道偏移里含有重新基准化的那一屏宽，它不是手指走过的距离。
let swipeVelocity = 0
let swipeVelocitySampleX = 0
let swipeVelocitySampleAt = 0
// 索引提交后要保留的轨道偏移量（中断过渡时重新基准化的结果）。
// null 表示按常规归零。由监听 currentPhoto 的 watcher 消费。
let pendingTrackOffset: number | null = null

const resetSwipeVelocity = (x: number) => {
  swipeVelocity = 0
  swipeVelocitySampleX = x
  swipeVelocitySampleAt = performance.now()
}

const sampleSwipeVelocity = (x: number) => {
  const now = performance.now()
  const dt = now - swipeVelocitySampleAt
  if (dt < 12) return
  const instant = (x - swipeVelocitySampleX) / dt
  // 轻度平滑，避免最后一两个抖动样本主导判定。
  swipeVelocity = swipeVelocity * 0.4 + instant * 0.6
  swipeVelocitySampleX = x
  swipeVelocitySampleAt = now
}

// 单指滑动切换照片相关
const touchSwipeStartX = ref(0)
const touchSwipeStartY = ref(0)
const touchSwipeOffset = ref(0)
const swipeTransitioning = ref(false)
const pendingSwipeDirection = ref<'previous' | 'next' | null>(null)
let swipeTimer: ReturnType<typeof setTimeout> | null = null
let closeTimer: ReturnType<typeof setTimeout> | null = null
let containerClickTimer: ReturnType<typeof setTimeout> | null = null
let transitionEpoch = 0
let swipeStartedAt = 0
let swipeStartOffset = 0
let swipeTargetOffset = 0
// 切图动画时长由松手速度决定：轻推从容、快甩利落。同一个值同时驱动 CSS
// transition 和提交定时器，两者必须一致，否则会回到 waitForTrackSettle 在
// 补偿的那种"定时器早于动画结束"的错位。
const SWIPE_DURATION_MIN = 170
const SWIPE_DURATION_MAX = 280
const swipeDurationMs = ref(SWIPE_DURATION_MAX)

// distance: 还需要走完的像素；velocity: 松手速度（px/ms）。
const computeSwipeDuration = (distance: number, velocity: number) => {
  // 开启「减弱动态效果」时退化为近乎即时的切换，仍保留一帧过渡避免闪烁。
  if (prefersReducedMotion()) return 1
  const speed = Math.max(Math.abs(velocity), 0.4)
  const estimate = Math.abs(distance) / speed
  return Math.round(Math.min(SWIPE_DURATION_MAX, Math.max(SWIPE_DURATION_MIN, estimate)))
}
// Navigation composable owns the FIFO queue; this alias keeps the existing
// rendering/gesture code readable without maintaining a second queue.
const queuedSwipeDirections = navigation.queue
const deferredTapAction = ref<'close' | 'toggle-controls' | null>(null)
const interactionReadyAt = ref(0)
const ignoredPointerIds = new Set<number>()

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
const opening = ref(false)
const openingPreviewVisible = ref(false)
const openingPreviewTransform = ref<string | null>(null)
let openingPreviewTimer: ReturnType<typeof setTimeout> | null = null
const closing = ref(false)
// Closing is deliberately split into two render phases.  The first phase
// freezes the image at its current visual transform; the second phase (next
// frame) applies the thumbnail target and fades the modal.  Applying both in
// one Vue patch makes browsers skip the transform transition.
const closingAnimationStarted = ref(false)
const closingStartTransform = ref<string | null>(null)
const activeOriginRect = ref<{ top: number; left: number; width: number; height: number } | null>(null)
const originTransform = ref<string | null>(null)
// 收回到缩略图时，飞行中的图片必须保持不透明直到落位：目标缩略图这段时间被
// 宿主页面藏起来了，如果图片也淡出，中途会出现一块什么都没有的空白。
// 所以这种情况只淡出背景和各个操作栏，图片本身不参与淡出。
const returningToThumb = ref(false)

// 收回动画的飞行层。from = 图片当前在屏幕上的可见框，to = 缩略图框。
type FlightRect = { left: number; top: number; width: number; height: number }
const flight = ref<{ src: string; from: FlightRect; to: FlightRect; radius: string } | null>(null)
const flightLayer = ref<HTMLElement | null>(null)
const flightImage = ref<HTMLImageElement | null>(null)
let flightAnimation: Animation | null = null
let flightRadiusAnimation: Animation | null = null
let flightImageAnimation: Animation | null = null

// 飞行层只渲染静态起始几何，动画交给 Web Animations API。
//
// 不能用 CSS 过渡：过渡要求「变化前样式」和「变化后样式」分属两次样式解析。
// Vue 的 DOM 更新是异步的，元素插入（起点几何）和随后改成终点几何这两次
// patch 之间，浏览器往往一次样式重算都还没做过 —— 于是它只看到一个「一出生
// 就在终点」的新元素，过渡根本不会创建。实测 getAnimations() 为空，几何直接
// 跳变；而这取决于两次 patch 之间是否恰好插进了一次重算，所以时好时坏，
// 表现为「有的关闭方式有动画、有的没有」，不同设备还不一样。
// WAAPI 的关键帧是显式的，不依赖任何渲染时序。
const flightBoxStyle = computed(() => {
  const f = flight.value
  if (!f) return {}
  // 布局尺寸固定为起点，位移和缩放全部交给 transform（合成器线程）。
  // 之前用 width/height 做动画，每帧都要主线程重排+重栅格化；而关闭这一刻主线程
  // 正忙着拆查看器、重绘整个相册网格，动画就整段卡住——实测 280ms 的动画只画出 3 帧，
  // 开头有 170ms 一帧不动，移除时还停在回弹中途，于是"掉帧 / 看不到回弹 / 结尾突然
  // 变小"三个现象其实是同一个原因。
  return {
    position: 'fixed' as const,
    left: `${f.from.left}px`,
    top: `${f.from.top}px`,
    width: `${f.from.width}px`,
    height: `${f.from.height}px`,
    transformOrigin: 'top left',
    overflow: 'hidden' as const,
    zIndex: 70,
    pointerEvents: 'none' as const
  }
})

// 图片始终铺满取景窗（object-fit: cover），所以窗口怎么变形，画面就怎么跟着走，
// 裁切也随窗口宽高比连续长出来。视差的那点滞后叠在"铺满"之上，
// 由 transform: scale 单独承担（见 startFlightImageAnimation）。
//
// 曾经改成固定尺寸 + transform 缩放来省栅格化，但那样图片是按自己的时间线走的，
// 和窗口对不上：实测飞行途中画面只填满窗口的 63%，四周长期露底，
// 看起来是"窗口和图片各飞各的"。而 trace 显示这段的栅格化开销主要来自背景淡出时
// 重绘整个相册瀑布流，不是这张图，所以这里优先保证观感正确。
const flightImageStyle = computed(() => {
  if (!flight.value) return {}
  // 图片铺满取景窗，视差滞后叠在上面。整段飞行是把大图往下缩，栅格化一次再降采样，
  // 观感是清晰的（放大才会糊）。
  return {
    width: '100%',
    height: '100%',
    objectFit: 'cover' as const,
    transformOrigin: 'center center'
  }
})

const startFlightAnimation = () => {
  const f = flight.value
  const el = flightLayer.value
  if (!f || !el || typeof el.animate !== 'function') return

  // 起点与终点的宽高比几乎总是一致（瀑布流卡片高度就是按照片比例算的），
  // 所以等比缩放不会让画面变形。
  const scale = f.to.width / f.from.width
  const dx = f.to.left - f.from.left
  const dy = f.to.top - f.from.top
  // 元素上的圆角会被 scale 一起缩小，想让落位时看起来是 8px，元素上就得写 8/scale。
  const endRadius = `${(parseFloat(f.radius) || 0) / scale}px`

  // 盒子（取景窗）单调地从大图可见框收到缩略图框就停住，没有任何回弹。
  // 飞行层是 position: fixed 的，关闭后立刻滚动页面它不会跟着走，所以它该短命；
  // 落位尺寸与缩略图完全一致，摘掉它的那一帧不会有尺寸跳变。
  //
  // transform 必须单独成一条动画：border-radius 不是可合成属性，和 transform 放在
  // 同一条里会把整条动画拉回主线程。实测那样时动画的 currentTime 会跟着主线程一起
  // 停住（卡顿 161ms 期间 currentTime 纹丝不动），于是关闭瞬间的重绘直接冻结动画。
  // 拆开之后位移/缩放跑在合成器线程，圆角即使卡住也只是圆角本身晚一点到位。
  flightAnimation?.cancel()
  flightAnimation = el.animate([
    { transform: 'translate(0px, 0px) scale(1)' },
    { transform: `translate(${dx}px, ${dy}px) scale(${scale})` }
  ], { duration: CLOSE_DURATION_MS, easing: CLOSE_EASE_IN, fill: 'both' })

  flightRadiusAnimation?.cancel()
  flightRadiusAnimation = el.animate([
    { borderRadius: '0px' },
    { borderRadius: endRadius }
  ], { duration: CLOSE_DURATION_MS, easing: CLOSE_EASE_IN, fill: 'both' })

  startFlightImageAnimation(f, scale, dx, dy)
}

// 把 CSS 的 cubic-bezier 求成 y = f(x)。二分而不是牛顿法：迭代次数固定、
// 没有导数为 0 时的发散分支，30 次已到 1e-9，画关键帧绰绰有余。
const cubicBezierEasing = (x1: number, y1: number, x2: number, y2: number) => {
  const curve = (a: number, b: number, t: number) => {
    const mt = 1 - t
    return 3 * mt * mt * t * a + 3 * mt * t * t * b + t * t * t
  }
  return (u: number) => {
    if (u <= 0) return 0
    if (u >= 1) return 1
    let lo = 0
    let hi = 1
    let t = u
    for (let i = 0; i < 30; i++) {
      if (curve(x1, x2, t) < u) lo = t
      else hi = t
      t = (lo + hi) / 2
    }
    return curve(y1, y2, t)
  }
}
const boxProgress = cubicBezierEasing(0.35, 0.6, 0.35, 1)
const imageProgress = cubicBezierEasing(0.4, 0.35, 0.45, 1)

// 视差：取景窗和画面各走各的曲线，但走的是同一段路。
//
// 图片 object-fit: cover 铺满盒子，所以盒子怎么变形画面就跟着怎么走——两者天然
// 严丝合缝。要让画面「慢半拍」，就给它自己的 transform 补上两者的差：
//
//   取景窗   eB = boxProgress(t / D)            —— D 毫秒走完
//   画面     eV = imageProgress(t / (D+LAG))    —— 晚 LAG 毫秒走完
//   相对缩放 rel   = (1+(s-1)eV) / (1+(s-1)eB)   > 1，画面比窗口大一圈、被多裁一点
//   相对位移 shift ∝ (rel-1)，方向与飞行方向相反 —— 画面被落在后面
//
// eB、eV 在 0 和 1 处都相等，所以起飞时画面正好铺满窗口、落位时又正好铺满，
// 摘掉飞行层换回真缩略图的那一帧没有任何跳变。
//
// 为什么画面的尺寸必须由 eV 推出来，而不能自己写死一条 rel 曲线：
// 画面在屏幕上的绝对尺寸是 rel × eB，要它全程只缩不涨，就得 rel'/rel ≤ -eB'/eB
// ——rel 能涨多快，被盒子当下的收缩速度卡死。试过直接写 rel 的三关键帧（线性上升
// 到峰值再回落），峰值前画面反而变大了：那会儿盒子已经在减速，rel 还在匀速涨。
// 现在 eV 是一条独立的单调曲线，rel = eV/eB 只是个记账结果，单调性白送。
//
// 峰值封顶。rel 的峰值随 s 变——窗口大、缩略图小的时候 s 小，两条曲线拉得更开。
// 阻尼 Vd = (1-k)·B + k·V 是两条单调曲线的凸组合，端点、单调性都不受影响，而且
//   Vd/B - 1 = k · (V/B - 1)
// ——阻尼正好把整条 rel-1 曲线等比缩放。所以先采一遍求原始峰值，再取
// k = CLOSE_IMAGE_PEAK / 峰值 封顶，任何 s 下都稳定在这个幅度。
//
// 位移量按「当前可用的溢出量」给：rel 比 1 大多少，画面四周就富余多少，
// 位移取其中的 CLOSE_IMAGE_SHIFT。这样它永远不可能把窗口推出画面边缘露白，
// 而且天然与缩放联动——两条动画是分开算的，却始终一起涨、一起收。
const startFlightImageAnimation = (f: { from: FlightRect }, scale: number, dx: number, dy: number) => {
  const img = flightImage.value
  if (!img || typeof img.animate !== 'function') return

  const total = CLOSE_DURATION_MS + CLOSE_IMAGE_LAG_MS
  // 位移方向与飞行方向相反：盒子往左上走，画面被落在右下，
  // 于是窗口取到的是画面偏左上的那一块——正是「窗口在画面上缓缓平移」的观感。
  const travel = Math.hypot(dx, dy) || 1
  const ux = -dx / travel
  const uy = -dy / travel

  const steps = 30
  const sample = (offset: number) => {
    const t = total * offset
    return {
      box: 1 + (scale - 1) * boxProgress(Math.min(t / CLOSE_DURATION_MS, 1)),
      visual: 1 + (scale - 1) * imageProgress(offset)
    }
  }

  let peak = 0
  for (let i = 0; i <= steps; i++) {
    const { box, visual } = sample(i / steps)
    peak = Math.max(peak, visual / box - 1)
  }
  const damping = peak > CLOSE_IMAGE_PEAK ? CLOSE_IMAGE_PEAK / peak : 1

  const frames: Keyframe[] = []
  for (let i = 0; i <= steps; i++) {
    const offset = i / steps
    const { box, visual } = sample(offset)
    const rel = 1 + damping * (visual / box - 1)
    const overflow = (rel - 1) / 2 * CLOSE_IMAGE_SHIFT
    frames.push({
      offset,
      transform: `translate(${(ux * overflow * f.from.width).toFixed(2)}px, ${(uy * overflow * f.from.height).toFixed(2)}px) scale(${rel.toFixed(5)})`
    })
  }

  flightImageAnimation?.cancel()
  // 关键帧之间用 linear：曲线本身已经采样进关键帧了，再叠一层缓动会把它扭歪。
  flightImageAnimation = img.animate(frames, { duration: total, easing: 'linear', fill: 'both' })
}
const modalStyle = computed(() => {
  const fadedOut = (!props.visible && !closing.value) || (closing.value && closingAnimationStarted.value)
  if (returningToThumb.value) {
    return {
      opacity: 1,
      backgroundColor: fadedOut ? 'rgba(0, 0, 0, 0)' : undefined,
      backdropFilter: fadedOut ? 'blur(0px)' : undefined,
      transition: `background-color ${CLOSE_DURATION_MS}ms ease, backdrop-filter ${CLOSE_DURATION_MS}ms ease`
    }
  }
  // Once the parent has released visibility and the handoff animation has
  // finished, keep the leave-transition node transparent.  Otherwise the
  // inline opacity would override Vue's leave class and briefly reveal a
  // fully opaque modal before it is removed.
  return {
    opacity: fadedOut ? 0 : 1,
    transition: 'opacity 260ms cubic-bezier(0.4, 0, 0.2, 1)'
  }
})

const { viewOriginalEnabled } = useUiSettings()
const { armFlingTapRepair, disposeFlingTapRepair } = useFlingTapRepair()

// 常量
const STORAGE_KEY = 'pe-info-transparent'
const FOCUS_BOX_KEY = 'pe-focus-box-visible'
const FACE_BOXES_KEY = 'pe-face-boxes-visible'
const THUMB_KEY = 'pe-thumb-height'
const OPENING_INPUT_GUARD_MS = 220
// 展开用不回弹的减速曲线：图片正在变大，回弹会显得晃。
const OPEN_EASE = 'cubic-bezier(0.22, 1, 0.36, 1)'
// 收回的两层分工：
//   盒子（取景窗）——单调地从大图的可见框收到缩略图框就停住；
//   画面本身——沿另一条曲线、晚 CLOSE_IMAGE_LAG_MS 才收完。
// 于是观感是「窗口先稳稳落位，画面再收进这个框里」，有层次而不晃。
//
// 这条曲线别再调得更陡了。原来是 cubic-bezier(0.33, 0.9, 0.2, 1)，前 40% 的时间
// 就走掉 90% 的路，剩下 140ms 基本在爬——画面正好要在这段里做视差，盒子却已经
// 不动了，两个近乎静止叠在一起，看着就是「顿一下」。换成这条之后落位时刻画面
// 还留着约四分之一的峰值速度（实测 0.16~0.28，原来只有 0.02~0.12）。
const CLOSE_EASE_IN = 'cubic-bezier(0.35, 0.6, 0.35, 1)'
const CLOSE_DURATION_MS = 230
// 画面比取景窗慢多少。它也是飞行层（position: fixed）的额外存活时间，
// 关闭后立刻滚动页面这段是不跟随滚动的，所以别太长。
const CLOSE_IMAGE_LAG_MS = 150
// rel（画面 / 窗口）的峰值封顶。再大画面中段就被裁得太狠，
// 窗口里只剩一小块，看着不像视差像穿帮。见 startFlightImageAnimation 的阻尼。
const CLOSE_IMAGE_PEAK = 0.2
// 画面相对窗口的横移量，取「此刻可用溢出量」的比例。留出余量，
// 免得取整误差把窗口推到画面外面去露白边。
const CLOSE_IMAGE_SHIFT = 0.75
// 没有缩略图落点时（键盘/无 originRect）纯淡出的时长
const CLOSE_FADE_MS = 260
// 宿主没通过 resolveOriginRect 告诉我们缩略图圆角时的兜底值。
// 这里不猜：保持直角＝与加飞行层之前的表现一致，不会给其它宿主页面
// （PhotoWall / Search / RandomGallery，它们只传 originRect）引入错误的圆角。
const DEFAULT_THUMB_RADIUS = '0px'

// 计算属性
const currentPhoto = computed(() => props.photos?.[currentIndex.value] || null)
const previousPhoto = computed(() => currentIndex.value > 0 ? props.photos[currentIndex.value - 1] : null)
const nextPhoto = computed(() => currentIndex.value < props.photos.length - 1 ? props.photos[currentIndex.value + 1] : null)
const trackPreviousPhoto = computed(() => navigation.incomingIndex.value !== null && navigation.incomingIndex.value < currentIndex.value
  ? props.photos[navigation.incomingIndex.value] || null
  : previousPhoto.value)
const trackNextPhoto = computed(() => navigation.incomingIndex.value !== null && navigation.incomingIndex.value > currentIndex.value
  ? props.photos[navigation.incomingIndex.value] || null
  : nextPhoto.value)
const swipeOffset = computed(() => touchSwipeOffset.value || imageDragOffset.value)

// 获取当前相册的氛围特效（用于在查看器中显示）
const photoStore = usePhotoStore()
const assetManager = usePhotoViewerAssets((photo) => photo)
const albumAtmosphereEffects = computed(() => {
  const album = photoStore.currentAlbum
  if (!album?.atmosphereEffects) return []
  return album.atmosphereEffects
})

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
    height: '100%',
    opacity: isDismissing.value ? Math.max(0.15, 1 - dismissOffset.value / Math.max(window.innerHeight * 0.8, 1)) : 1
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

const openingPreviewStyle = computed(() => ({
  ...imageStyle.value,
  // 静止态与 FLIP 态保持相同的函数结构（平移+平移+缩放），
  // 浏览器就能逐项插值，而不用退化成矩阵插值。
  transform: openingPreviewTransform.value || 'translate(-50%, -50%) translate(0px, 0px) scale(1, 1)',
  transformOrigin: 'center center',
  transition: openingPreviewTransform.value ? 'none' : `transform 300ms ${OPEN_EASE}`
}))

// Photo records normally contain the intrinsic dimensions. Use them before
// the large asset finishes loading so the image box has a stable aspect ratio
// from the first paint (otherwise an <img> with no dimensions briefly lays out
// as a thin strip and then grows while decoding).
const getKnownImageSize = (photo: Photo | null | undefined) => {
  const width = Number(photo?.width || 0)
  const height = Number(photo?.height || 0)
  return width > 0 && height > 0 ? { width, height } : { width: 0, height: 0 }
}

const waitForPhotoReady = (photo: Photo | null | undefined, source: 'thumbnail' | 'large' = 'large', timeoutMs = 1200) => {
  if (!photo) return Promise.resolve(false)
  const asset = toAssetInput(photo)
  const quality = source === 'thumbnail' ? 'thumbnail' : 'original'
  const load = assetManager.loadQuality(asset, quality)
  return Promise.race([
    load,
    new Promise<boolean>((resolve) => window.setTimeout(() => resolve(false), timeoutMs))
  ])
}

const waitForTrackSettle = () => new Promise<void>((resolve) => {
  requestAnimationFrame(() => requestAnimationFrame(() => resolve()))
})

// 读取切图轨道当前真实的动画位置（而不是它的目标值）。滑动时 scale 恒为 1，
// imageTransformStyle 就是纯 translateX，所以矩阵的 m41 即当前偏移量。
// 中断一次进行中的过渡时必须用这个真实值，否则重新基准化会算错、画面跳变。
const readLiveTrackOffset = () => {
  const el = imageWrapper.value
  if (el) {
    try {
      const transform = window.getComputedStyle(el).transform
      if (transform && transform !== 'none') {
        const m41 = new DOMMatrixReadOnly(transform).m41
        if (Number.isFinite(m41)) return m41
      } else {
        return 0
      }
    } catch {
      // 读不到就退回下面的时间估算
    }
  }
  // 兜底：按已过时间在起点和终点之间线性取值。只有拿不到计算样式时才会走到
  // 这里；曲线不精确会让接手瞬间有微小偏差，但不会卡住手势。
  if (!swipeTransitioning.value) return swipeOffset.value
  const elapsed = performance.now() - swipeStartedAt
  const progress = Math.min(1, Math.max(0, elapsed / Math.max(swipeDurationMs.value, 1)))
  return swipeStartOffset + (swipeTargetOffset - swipeStartOffset) * progress
}

// 预热当前位置往外两张的缩略图。连滑不再需要等待后会更快到达 index±2，
// 只预热相邻一张的话，新露出的那张可能来不及解码。
// 这里逐张调用 loadQuality 而不是 preloadThumbnails：后者会自增 preloadEpoch，
// 把打开时启动的整册预热批次取消掉；loadQuality 对已就绪/在途的槽位是幂等的。
const preloadSwipeNeighbors = () => {
  const photos = props.photos
  if (!photos?.length) return
  for (const delta of [1, -1, 2, -2]) {
    const photo = photos[currentIndex.value + delta]
    if (photo) void assetManager.loadQuality(toAssetInput(photo), 'thumbnail')
  }
}

const preloadAllThumbnails = (photos: Photo[]) => {
  const assets: PhotoAssetInput[] = photos.map(toAssetInput)
  assetManager.preloadThumbnails(assets)
}

const prepareOpeningTransform = () => {
  if (openingTransformPrepared.value || !opening.value || !activeOriginRect.value || !openingPreviewVisible.value) return
  // FLIP 的起始几何必须量预览图**自己**的框，而不是它所在的容器。
  // 预览图按图片比例缩放居中显示，和铺满视口的容器既不同尺寸也不同位置；
  // 用容器的宽高算缩放，落点会系统性偏移（实测偏 271px、高度差 79px）。
  const target = openingPreviewImage.value?.getBoundingClientRect()
  const origin = activeOriginRect.value
  if (!target || target.width <= 0 || target.height <= 0) return
  openingTransformPrepared.value = true
  const epoch = transitionEpoch
  const dx = origin.left + origin.width / 2 - (target.left + target.width / 2)
  const dy = origin.top + origin.height / 2 - (target.top + target.height / 2)
  // 预览图靠 left/top 50% 定位，居中完全依赖 translate(-50%, -50%)。
  // FLIP 变换会整体替换 transform，所以必须把这段居中平移一起写进来，
  // 否则图片会先跳到容器中心的右下方再飞回来。
  openingPreviewTransform.value =
    `translate(-50%, -50%) translate(${dx}px, ${dy}px) scale(${origin.width / target.width}, ${origin.height / target.height})`
  opening.value = false
  if (openingPreviewTimer) clearTimeout(openingPreviewTimer)
  requestAnimationFrame(() => {
    if (epoch !== transitionEpoch) return
    requestAnimationFrame(() => {
      if (epoch !== transitionEpoch) return
      openingPreviewTransform.value = null
      opening.value = true
      requestAnimationFrame(() => {
        if (epoch !== transitionEpoch) return
        opening.value = false
        openingPreviewTimer = window.setTimeout(() => {
          openingPreviewVisible.value = false
          openingPreviewTimer = null
        }, 280)
      })
    })
  })
}

// 图片变换样式 - 用于缩放和拖拽
const imageTransformStyle = computed(() => {
  // 如果正在拖拽、缩放、平移或触摸操作，禁用过渡效果
  const isInteracting = isImageDragging.value || isZooming.value || isPanning.value || isPinching.value

  // Closing owns the image transform for the entire handoff.  Keep the
  // captured transform for one frame, then animate to the thumbnail origin.
  // This branch must precede dismiss/zoom branches so those states cannot
  // overwrite the closing animation when the parent sets visible=false.
  if (closing.value) {
    // 收回到缩略图时由飞行层负责演出，原图层直接隐藏（两者同时可见会重影）。
    if (flight.value) {
      return {
        transform: closingStartTransform.value || 'none',
        transformOrigin: 'center center',
        transition: 'none',
        opacity: 0
      }
    }
    // 没有缩略图落点时不做位移动画，只保持当前画面等背景淡出。
    // originTransform 已经不再由关闭流程写入（收回一律走飞行层）。
    return {
      transform: closingStartTransform.value || 'none',
      transformOrigin: 'center center',
      transition: 'none'
    }
  }

  if (isDismissing.value) {
    const progress = Math.min(1, dismissOffset.value / Math.max(window.innerHeight * 0.8, 1))
    return {
      transform: `translateY(${dismissOffset.value}px) scale(${1 - progress * 0.35})`,
      transformOrigin: 'center center',
      transition: isInteracting ? 'none' : 'transform 260ms cubic-bezier(0.22, 1, 0.36, 1)'
    }
  }

  if (originTransform.value) {
    return {
      transform: originTransform.value,
      transformOrigin: 'center center',
      transition: opening.value || closing.value ? 'transform 260ms cubic-bezier(0.22, 1, 0.36, 1)' : 'none'
    }
  }
  if (scale.value > 1) {
    return {
      // transform 从右到左应用：translate(...) scale(...) 表示先 scale 后 translate
      // 这样 translateX/Y 表示屏幕像素平移，不会被 scale 再次放大，缩放/平移更稳定
      transform: `translate(${translateX.value}px, ${translateY.value}px) scale(${scale.value})`,
      transformOrigin: 'center center',
      transition: trackTransitionDisabled.value ? 'none' : (isInteracting ? 'none' : 'transform 0.3s ease')
    }
  } else {
    // 未缩放时，只需要拖拽切换的偏移
    // 图片位置由 imageContainerStyle 控制，会自动跟随信息栏动画
    const dragOffsetX = swipeOffset.value
    return {
      transform: `translateX(${dragOffsetX}px)`,
      transformOrigin: 'center center',
      // 拖拽跟手；松手时由同一条轨道完成滑入或弹回
      transition: trackTransitionDisabled.value ? 'none' : (swipeTransitioning.value ? `transform ${swipeDurationMs.value}ms cubic-bezier(0.22, 1, 0.36, 1)` : (isInteracting ? 'none' : 'transform 0.3s ease'))
    }
  }
})

const getAdjacentImageStyle = (direction: 'previous' | 'next') => {
  const offset = swipeOffset.value
  const width = window.innerWidth
  const base = direction === 'previous' ? -width : width
  return {
    maxWidth: '100%',
    maxHeight: '100%',
    width: 'auto',
    height: 'auto',
    objectFit: 'contain',
    transform: `translate(calc(-50% + ${base + offset}px), -50%)`,
    opacity: Math.min(1, Math.max(0, Math.abs(offset) / Math.max(width * 0.35, 1))),
    transition: trackTransitionDisabled.value
      ? 'none'
      : (swipeTransitioning.value
        ? `transform ${swipeDurationMs.value}ms cubic-bezier(0.22, 1, 0.36, 1), opacity ${Math.round(swipeDurationMs.value * 0.85)}ms ease`
        : 'none')
  }
}

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

  const faces = currentPhoto.value.faces
  const options = props.openOptions

  // 如果有 highlightedFaceIds 且不为空，按 ID 过滤
  if (options?.highlightedFaceIds?.length) {
    const highlightedSet = new Set(options.highlightedFaceIds.map((id: number | string) => Number(id)))
    const filtered = faces.filter(f => highlightedSet.has(Number(f.id)))
    if (filtered.length > 0) return filtered
  }

  // 如果有 highlightedPersonId 且不为空，按 personId 过滤
  if (options?.highlightedPersonId) {
    const filtered = faces.filter(f => f.personId === options?.highlightedPersonId)
    if (filtered.length > 0) return filtered
  }

  // 没有设置高亮选项时，显示所有人脸
  return faces
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
    transitionEpoch += 1
    if (closeTimer) { clearTimeout(closeTimer); closeTimer = null }
    if (swipeTimer) { clearTimeout(swipeTimer); swipeTimer = null }
    if (openingPreviewTimer) { clearTimeout(openingPreviewTimer); openingPreviewTimer = null }
    showAdminMenu.value = false
    controlsVisible.value = true
    interactionReadyAt.value = performance.now() + OPENING_INPUT_GUARD_MS
    ignoredPointerIds.clear()
    activePointers.clear()
    pointerGesture.value = 'none'
    isPinching.value = false
    isImageDragging.value = false
    swipeTransitioning.value = false
    trackTransitionDisabled.value = false
    pendingSwipeDirection.value = null
    queuedSwipeDirections.value = []
    pendingTrackOffset = null
    deferredTapAction.value = null
    openingTransformPrepared.value = false
    openingPreviewVisible.value = !!props.originRect
    openingPreviewTransform.value = null
    closingAnimationStarted.value = false
    closingStartTransform.value = null
    returningToThumb.value = false
    lastTapTime.value = 0
    lastTapX.value = 0
    lastTapY.value = 0
    imageLoaded.value = false
    imageLoadError.value = false
    // imageSize is the layout source of truth. Never let a new session use the
    // previous photo's aspect ratio while its own resource is loading.
    imageSize.value = getKnownImageSize(props.photos[props.startIndex ?? 0])
    scale.value = 1
    translateX.value = 0
    translateY.value = 0
    userHasManuallyZoomed.value = false
    touchSwipeOffset.value = 0
    imageDragOffset.value = 0
    dismissOffset.value = 0
    isDismissing.value = false
    infoPanelOffsetX.value = 0
    lastInfoPanelOffsetX.value = 0
    // 打开查看器时，根据 startIndex 设置当前索引
    navigation.reset(props.startIndex ?? 0)
    preloadAllThumbnails(props.photos)
    opening.value = !!props.originRect
    activeOriginRect.value = props.originRect ? { ...props.originRect } : null
    closing.value = false
    originTransform.value = null
    // 标记为初始加载
    isInitialLoad.value = true
    // 重新初始化框体状态（forceShowFaces需要在每次打开时生效）
    initializeBoxStates()
    // 聚焦到PhotoViewer以接收键盘事件
    nextTick(() => {
      modalRoot.value?.focus()
      // 滚动缩略图到当前图片（无动画，因为距离可能很长）
      scrollThumbIntoView(false)
      // Prepare FLIP from the known layout box before the image decode callback.
      // This prevents a final-size strip from appearing before the opening motion.
      prepareOpeningTransform()
    })
    // 图片自身尺寸可用后再计算信息栏偏移，避免沿用上一张的几何信息。
  } else {
    // Parent-driven close must invalidate pending transition callbacks too;
    // otherwise a late timer can mutate state while the viewer is hidden.
    transitionEpoch += 1
    if (swipeTimer) { clearTimeout(swipeTimer); swipeTimer = null }
    pendingSwipeDirection.value = null
    swipeTransitioning.value = false
    navigation.cancel()
    showAdminMenu.value = false
    // 人脸框现在直接绑定在图片内部，无需清理
  }
})

// 监听 startIndex 变化
watch(() => props.startIndex, (newStartIndex) => {
  if (props.visible && newStartIndex !== undefined) {
    abortNavigation()
    showAdminMenu.value = false
    navigation.reset(newStartIndex)
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
  }
})

// 相册数据可能在查看器已打开后异步替换（随机页、筛选页尤其常见）。
// 以 photoId 作为资源生命周期边界，确保旧图片的 load/error 回调不会污染新图片状态。
watch(() => [currentPhoto.value?.id, viewingOriginal.value, props.photos.length] as const, () => {
  imageLoaded.value = false
  imageLoadError.value = false
  const knownSize = getKnownImageSize(currentPhoto.value)
  // During in-viewer navigation, keep the last stable box when metadata is
  // incomplete. Clearing it to 0x0 makes both the main and adjacent image
  // disappear for a frame while the new asset is decoding (black flash).
  if (knownSize.width > 0 && knownSize.height > 0) {
    imageSize.value = knownSize
  } else if (opening.value) {
    imageSize.value = { width: 0, height: 0 }
  }
  isSwitchingPhoto.value = true
  scale.value = 1
  translateX.value = 0
  translateY.value = 0
  // 提交索引时通常轨道就该归零；但中断过渡接手时，偏移量被重新基准化成了一个
  // 非零值来保持画面不动，这里必须沿用它，否则轨道会被拉回中心、画面跳一下。
  const preservedOffset = pendingTrackOffset
  pendingTrackOffset = null
  touchSwipeOffset.value = preservedOffset ?? 0
  imageDragOffset.value = preservedOffset ?? 0
  displayedImageUrl.value = currentPhoto.value ? getDisplayUrl(currentPhoto.value) : ''
  largeImagePreloadUrl.value = currentPhoto.value ? getImageUrl(currentPhoto.value) : ''
  largeImageReady.value = false
  if (currentPhoto.value) {
    const photoAtRequest = currentPhoto.value
    void assetManager.prepareForDisplay(toAssetInput(photoAtRequest)).then((slot) => {
      if (currentPhoto.value?.id !== photoAtRequest.id) return
      if (slot.displayUrl && slot.displayUrl !== displayedImageUrl.value) {
        displayedImageUrl.value = slot.displayUrl
        if (slot.quality === 'original') largeImageReady.value = true
      }
    })
    // Keep the adjacent slots warm so a rapid swipe can promote an already
    // decoded preview/original without blocking the track animation.
    ;[previousPhoto.value, nextPhoto.value].forEach((adjacent) => {
      if (adjacent) void assetManager.prepareForDisplay(toAssetInput(adjacent))
    })
  }
})

watch(() => props.photos, (nextPhotos) => {
  if (props.visible) abortNavigation()
  if (!nextPhotos.length) {
    navigation.reset(0)
    imageLoaded.value = false
    return
  }
  if (currentIndex.value >= nextPhotos.length) {
    navigation.reset(nextPhotos.length - 1)
  }
}, { deep: false })

// 监听图片加载状态变化，确保人脸框在图片加载完成后重新计算
watch(() => imageLoaded.value, (newLoaded) => {
  if (newLoaded) {
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
  if (viewingOriginal.value) {
    return buildPhotoAssetUrl(photo, 'original') || ''
  }
  return buildPhotoAssetUrl(photo, 'large') || ''
}

const getThumbUrl = (photo: Photo) => {
  return buildPhotoAssetUrl(photo, 'thumbnail') || getImageUrl(photo)
}

const toAssetInput = (photo: Photo): PhotoAssetInput => ({
  id: photo.id,
  thumbnailUrl: getThumbUrl(photo),
  previewUrl: photo.largeThumbPath ? buildPhotoAssetUrl(photo, 'large') || undefined : undefined,
  originalUrl: getImageUrl(photo)
})

const getDisplayUrl = (photo: Photo | null | undefined) => {
  if (!photo) return ''
  const slot = assetManager.ensureSlot(toAssetInput(photo))
  return slot.displayUrl || getThumbUrl(photo)
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
const savedInfoCollapsed = localStorage.getItem('pe-info-collapsed')
// 首次使用没有保存记录时默认隐藏信息面板；之后才恢复用户上次的选择。
infoCollapsed.value = savedInfoCollapsed === null ? true : savedInfoCollapsed === '1'
infoTransparent.value = localStorage.getItem(STORAGE_KEY) === '1'
// 初始化信息栏宽度
const savedWidth = localStorage.getItem('pe-info-panel-width')
if (savedWidth) {
  infoPanelWidth.value = parseInt(savedWidth, 10)
}

// 初始化框体状态已在上面声明

// 基本功能函数
const close = () => {
  showAdminMenu.value = false
  if (closing.value) return
  if (closeTimer) {
    clearTimeout(closeTimer)
    closeTimer = null
  }
  // 飞行层用绝对坐标，起点直接取图片此刻在屏幕上的可见框即可 —— 不需要再把
  // dismiss / 缩放 / 滑动的变换还原掉。此时状态尚未清理，DOM 上仍带着手势变换。
  const measuredRect = mainImage.value?.getBoundingClientRect() ?? null
  abortNavigation()
  if (openingPreviewTimer) { clearTimeout(openingPreviewTimer); openingPreviewTimer = null }
  // Capture the exact transform currently visible on screen before clearing
  // dismiss/zoom state.  Without this freeze, resetting isDismissing/scale in
  // the same frame snaps the image to center and leaves no animated start.
  const progress = dismissOffset.value / Math.max(window.innerHeight * 0.8, 1)
  const currentTransform = isDismissing.value
    ? `translateY(${dismissOffset.value}px) scale(${1 - Math.min(1, progress) * 0.35})`
    : scale.value > 1
      ? `translate(${translateX.value}px, ${translateY.value}px) scale(${scale.value})`
      : `translateX(${swipeOffset.value}px)`

  openingPreviewVisible.value = false
  openingPreviewTransform.value = null
  opening.value = false
  closingStartTransform.value = currentTransform
  originTransform.value = null
  closingAnimationStarted.value = false
  // The closing branch now owns the visual state; dismiss/gesture state can
  // be cleared immediately without affecting the frozen first frame.
  isDismissing.value = false
  dismissOffset.value = 0
  isImageDragging.value = false
  touchSwipeOffset.value = 0
  imageDragOffset.value = 0
  // 收回目标优先取「当前这张照片」的缩略图；宿主页面没提供解析器（或解析不到，
  // 比如那张照片已被筛掉）时，才退回打开时记录的那一张。
  const closingPhotoId = currentPhoto.value?.id ?? null
  const resolved = closingPhotoId !== null
    ? props.resolveOriginRect?.(closingPhotoId, currentIndex.value) ?? null
    : null
  const target = resolved || activeOriginRect.value

  if (target && measuredRect && measuredRect.width > 0 && measuredRect.height > 0) {
    closing.value = true
    returningToThumb.value = true
    // 大图交给飞行层去演，原来的图片层立刻藏起来，避免两份图叠在一起。
    flight.value = {
      src: displayedImageUrl.value,
      from: { left: measuredRect.left, top: measuredRect.top, width: measuredRect.width, height: measuredRect.height },
      to: { left: target.left, top: target.top, width: target.width, height: target.height },
      radius: target.radius || DEFAULT_THUMB_RADIUS
    }
    // 让宿主页面把目标缩略图藏起来，收回的图片落位时才不会和原图重影。
    emit('return-transition', { photoId: closingPhotoId, active: true })
    // 收尾：先摘掉飞行层、同时让宿主恢复缩略图（同一次 patch 内完成，不会有空档），
    // 之后才把 closing 置否让模态走离场过渡。
    //
    // 注意不要 cancel 动画：cancel 会让元素回到 inline 的起点几何，而模态一旦进入
    // 离场过渡，Vue 就不再 patch 这棵子树，v-if 也就摘不掉它了 —— 那张大图会带着
    // 起点尺寸在原地停留整个离场时长。用 fill: 'both' 让它保持终点，直接移除即可。
    let finalized = false
    const finalizeClose = () => {
      // 兜底计时器和动画的 finished 都会调它，只允许生效一次
      if (finalized) return
      finalized = true
      if (closeTimer) { clearTimeout(closeTimer); closeTimer = null }
      flightAnimation = null
      flightRadiusAnimation = null
      flightImageAnimation = null
      flight.value = null
      returningToThumb.value = false
      emit('return-transition', { photoId: closingPhotoId, active: false })
      originTransform.value = null
      closingStartTransform.value = null
      closingAnimationStarted.value = false
      closing.value = false
    }

    // 飞行层挂上 DOM 之后立刻起飞。WAAPI 不要求先上屏一帧。
    nextTick(() => {
      if (!closing.value) return
      startFlightAnimation()
      closingAnimationStarted.value = true

      // 摘掉飞行层要等最后落地的那条动画——是画面（晚 CLOSE_IMAGE_LAG_MS），
      // 不是盒子。按盒子的时长摘会把视差那一小段直接切掉。
      //
      // 兜底计时器也必须从动画真正开始的时刻算起。之前从 close() 调用时刻起算，
      // 而动画要等 Vue 渲染 + 主线程空闲才开始（实测晚了 128ms），
      // 于是计时器抢在动画结束前就把飞行层摘掉，画面停在中途然后突然变小。
      const settleAnimation = flightImageAnimation || flightAnimation
      const settleMs = flightImageAnimation ? CLOSE_DURATION_MS + CLOSE_IMAGE_LAG_MS : CLOSE_DURATION_MS
      if (closeTimer) clearTimeout(closeTimer)
      closeTimer = window.setTimeout(finalizeClose, settleMs + 80)
      settleAnimation?.finished
        .then(() => { if (closing.value) finalizeClose() })
        .catch(() => { /* 被取消（重新打开/卸载），由对应流程收尾 */ })

      // 立刻把控制权交还父组件。曾经尝试等 animation.ready 之后再 emit，想让动画
      // 抢在相册网格重绘之前起跑——既没有缩短起跑延迟（仍是 ~148ms），又会在关闭
      // 动画被中途打断时把这次 emit 丢掉，查看器就再也关不上了。
      emit('update:visible', false)
    })
    return
  }

  // No origin rectangle (keyboard/backdrop close): still use the same
  // two-phase fade so the background never disappears abruptly.
  closing.value = true
  emit('update:visible', false)
  requestAnimationFrame(() => {
    if (closing.value) closingAnimationStarted.value = true
  })
  closeTimer = window.setTimeout(() => {
    closingStartTransform.value = null
    closingAnimationStarted.value = false
    closing.value = false
    closeTimer = null
  }, CLOSE_FADE_MS)
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

const prev = (alreadyAtIndex = false) => {
  if (!props.photos?.length) return
  if (!alreadyAtIndex && swipeTransitioning.value) {
    queuedSwipeDirections.value.push('previous')
    return
  }
  if (!alreadyAtIndex && currentIndex.value <= 0) return
  showAdminMenu.value = false
  const oldIndex = currentIndex.value
  // 标记正在切换图片，禁用框体动画
  isSwitchingPhoto.value = true
  if (!alreadyAtIndex) navigation.reset(currentIndex.value - 1)
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

}

const next = (alreadyAtIndex = false) => {
  if (!props.photos?.length) return
  if (!alreadyAtIndex && swipeTransitioning.value) {
    queuedSwipeDirections.value.push('next')
    return
  }
  if (!alreadyAtIndex && currentIndex.value >= props.photos.length - 1) return
  showAdminMenu.value = false
  const oldIndex = currentIndex.value
  // 标记正在切换图片，禁用框体动画
  isSwitchingPhoto.value = true
  if (!alreadyAtIndex) navigation.reset(currentIndex.value + 1)
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
}

// 把索引提交推进一格，并把轨道偏移设为 nextOffset。
// 正常动画结束时 nextOffset 是 0；中断接手时是重新基准化后的值，见
// interruptSwipeTransition。两条路径共用这一段，避免提交序列出现两份实现。
const commitSwipeIndex = (direction: 'previous' | 'next', nextOffset: number) => {
  const targetPhoto = navigation.incomingIndex.value !== null
    ? props.photos[navigation.incomingIndex.value] || null
    : (direction === 'previous' ? previousPhoto.value : nextPhoto.value)
  // 不要为了等图片解码而锁住交互状态：那会让下一次 pointerdown 看起来像
  // 动画被打断，从而丢掉后续的滑动动画。资源管理器同步提供缩略图兜底。
  if (targetPhoto) void waitForPhotoReady(targetPhoto, 'thumbnail')
  // 让 currentPhoto 的 watcher 知道这次提交后轨道不该归零
  pendingTrackOffset = nextOffset !== 0 ? nextOffset : null
  pendingSwipeDirection.value = null
  // End the outgoing transform before changing currentIndex. If the index
  // watcher resets the offset while this flag is still true, CSS animates
  // the old image back toward center (the occasional right-then-left jump).
  trackTransitionDisabled.value = true
  swipeTransitioning.value = false
  touchSwipeOffset.value = nextOffset
  imageDragOffset.value = nextOffset
  // Prime the main slot with the already decoded target thumbnail before
  // committing the index. This keeps one stable drawable in the main image
  // element while Vue applies the new photo geometry.
  if (targetPhoto) {
    displayedImageUrl.value = getDisplayUrl(targetPhoto)
    largeImagePreloadUrl.value = getImageUrl(targetPhoto)
    imageLoaded.value = true
  }
  navigation.commit()
  direction === 'previous' ? prev(true) : next(true)
  preloadSwipeNeighbors()
}

// 在过渡进行中按下手指时接管当前动画，而不是把手势挡住、等动画播完。
//
// 轨道是三槽结构：相邻图位于 base + offset，base = ∓屏宽。向后切换时 offset
// 从 0 走向 -W，在中途位置 o 时 next 图正好位于 W + o。此刻提交索引并把偏移
// 重新基准化为 o + W，新当前图就仍在 W + o，旧当前图落到 -W + (W+o) = o，
// 也就是它原来的位置 —— 像素级恒等，不可能跳变。向前切换同理取 o - W。
//
// 返回重新基准化后的偏移量；没有过渡在进行时返回 null。
const interruptSwipeTransition = (): number | null => {
  if (!swipeTransitioning.value) return null

  const live = readLiveTrackOffset()
  const direction = pendingSwipeDirection.value

  // 作废在途定时器与其 epoch 校验，接下来由手势接管。
  transitionEpoch += 1
  if (swipeTimer) { clearTimeout(swipeTimer); swipeTimer = null }

  let rebased = live
  if (direction) {
    // 会提交的过渡：提前完成它，偏移量平移一个屏宽保持画面不动。
    rebased = direction === 'previous' ? live - window.innerWidth : live + window.innerWidth
    commitSwipeIndex(direction, rebased)
  } else {
    // 回弹或到头的过渡：没有索引变化，就地接手当前位置。
    swipeTransitioning.value = false
    trackTransitionDisabled.value = true
    touchSwipeOffset.value = live
    imageDragOffset.value = live
    navigation.cancel()
  }

  // 重新基准化的这一帧不能带过渡，否则会从旧目标值平滑滑过来。
  requestAnimationFrame(() => { trackTransitionDisabled.value = false })
  return rebased
}

const finishSwipe = (direction: 'previous' | 'next', releaseVelocity = 0) => {
  const navDirection = direction
  if (swipeTransitioning.value) {
    queuedSwipeDirections.value.push(direction)
    return
  }
  const canMove = direction === 'previous' ? currentIndex.value > 0 : currentIndex.value < props.photos.length - 1
  if (!canMove) {
    const epoch = ++transitionEpoch
    trackTransitionDisabled.value = false
    swipeTransitioning.value = true
    swipeStartedAt = performance.now()
    swipeStartOffset = touchSwipeOffset.value || imageDragOffset.value
    swipeTargetOffset = 0
    swipeDurationMs.value = computeSwipeDuration(swipeStartOffset, releaseVelocity)
    touchSwipeOffset.value = 0
    imageDragOffset.value = 0
    if (swipeTimer) clearTimeout(swipeTimer)
    swipeTimer = window.setTimeout(async () => {
      await waitForTrackSettle()
      if (epoch !== transitionEpoch) return
      swipeTransitioning.value = false
      navigation.cancel()
      swipeTimer = null
      const queued = queuedSwipeDirections.value.shift() || null
      if (queued) requestAnimationFrame(() => finishSwipe(queued))
      else flushDeferredTapAction()
    }, swipeDurationMs.value)
    return
  }

  if (!navigation.begin(navDirection)) return

  const epoch = ++transitionEpoch

  // A new gesture owns the track again, even if it begins in the same frame
  // as the previous commit's no-transition handoff.
  trackTransitionDisabled.value = false
  swipeTransitioning.value = true
  pendingSwipeDirection.value = direction
  const target = direction === 'previous' ? window.innerWidth : -window.innerWidth
  swipeStartedAt = performance.now()
  swipeStartOffset = touchSwipeOffset.value
  swipeTargetOffset = target
  swipeDurationMs.value = computeSwipeDuration(target - swipeStartOffset, releaseVelocity)
  touchSwipeOffset.value = target
  imageDragOffset.value = target
  if (swipeTimer) clearTimeout(swipeTimer)
  if (openingPreviewTimer) { clearTimeout(openingPreviewTimer); openingPreviewTimer = null }
  // A swipe after the opening guard takes ownership of the visual track;
  // never leave the FLIP preview layer mounted above the next image.
  openingPreviewVisible.value = false
  openingPreviewTransform.value = null
  opening.value = false
  if (closeTimer) clearTimeout(closeTimer)
  swipeTimer = window.setTimeout(async () => {
    // Let the browser present the final transform frame before replacing the
    // current slot/index. Committing at the exact CSS duration can race the
    // compositor and briefly repaint the outgoing image in reverse.
    await waitForTrackSettle()
    if (epoch !== transitionEpoch || !props.visible || pendingSwipeDirection.value !== direction) {
      swipeTransitioning.value = false
      pendingSwipeDirection.value = null
      swipeTimer = null
      return
    }
    commitSwipeIndex(direction, 0)
    swipeTimer = null
    requestAnimationFrame(() => {
      trackTransitionDisabled.value = false
    })
    const queued = queuedSwipeDirections.value.shift() || null
    if (queued) {
      // Start queued gestures on the next frame so the committed slot has
      // settled before the next transform begins.
      requestAnimationFrame(() => {
        requestAnimationFrame(() => finishSwipe(queued))
      })
    } else {
      flushDeferredTapAction()
    }
  }, 260)
}

const cancelSwipe = (releaseVelocity = 0) => {
  const epoch = ++transitionEpoch
  swipeStartedAt = performance.now()
  swipeStartOffset = touchSwipeOffset.value || imageDragOffset.value
  swipeTargetOffset = 0
  swipeDurationMs.value = computeSwipeDuration(swipeStartOffset, releaseVelocity)
  trackTransitionDisabled.value = false
  swipeTransitioning.value = true
  pendingSwipeDirection.value = null
  touchSwipeOffset.value = 0
  imageDragOffset.value = 0
  if (swipeTimer) clearTimeout(swipeTimer)
  swipeTimer = window.setTimeout(async () => {
    await waitForTrackSettle()
    if (epoch !== transitionEpoch) return
    swipeTransitioning.value = false
    navigation.cancel()
    swipeTimer = null
    const queued = queuedSwipeDirections.value.shift() || null
    if (queued) requestAnimationFrame(() => finishSwipe(queued))
    else flushDeferredTapAction()
  }, swipeDurationMs.value)
}

const abortNavigation = () => {
  transitionEpoch += 1
  pendingTrackOffset = null
  if (swipeTimer) { clearTimeout(swipeTimer); swipeTimer = null }
  pendingSwipeDirection.value = null
  swipeTransitioning.value = false
  trackTransitionDisabled.value = false
  touchSwipeOffset.value = 0
  imageDragOffset.value = 0
  navigation.cancel()
  queuedSwipeDirections.value = []
}

const jump = (idx: number) => {
  const oldIndex = currentIndex.value
  abortNavigation()
  // 标记正在切换图片，禁用框体动画
  isSwitchingPhoto.value = true
  navigation.reset(idx)
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
  const handle = e.currentTarget as HTMLElement
  activeInfoResizeHandle.value = handle
  handle.setPointerCapture?.(e.pointerId)
  window.addEventListener('pointermove', onResize, true)
  window.addEventListener('pointerup', endResize, true)
  window.addEventListener('pointercancel', endResize, true)
  e.preventDefault()
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
  activeInfoResizeHandle.value?.releasePointerCapture?.(e.pointerId)
  activeInfoResizeHandle.value = null
  window.removeEventListener('pointermove', onResize, true)
  window.removeEventListener('pointerup', endResize, true)
  window.removeEventListener('pointercancel', endResize, true)

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
  if (performance.now() < interactionReadyAt.value) {
    event.stopPropagation()
    return
  }
  // 如果正在拖拽，不处理点击
  if (wasDragging.value || isImageDragging.value) {
    return
  }

  event.stopPropagation()

  // The content area fills the whole space around the image, so event.target
  // cannot tell whether the user clicked the drawable image or the black
  // backdrop. Use the rendered image rectangle instead. A backdrop tap is a
  // single-purpose action and must close immediately; only taps on the image
  // are delayed to allow the second click of a double-click to cancel them.
  const action = getTapAction(event.target, event.clientX, event.clientY)
  if (action === 'close') {
    if (containerClickTimer) {
      clearTimeout(containerClickTimer)
      containerClickTimer = null
    }
    close()
    return
  }

  if (deferredTapAction.value && !swipeTransitioning.value) {
    flushDeferredTapAction()
    return
  }
  if (swipeTransitioning.value) {
    return
  }

  // Browser dispatches two click events before dblclick. Defer the image
  // click briefly so a double-click can cancel both and perform only zoom.
  if (containerClickTimer) clearTimeout(containerClickTimer)
  containerClickTimer = window.setTimeout(() => {
    containerClickTimer = null
    if (closing.value || !props.visible) return
    controlsVisible.value = !controlsVisible.value
  }, 240)
}

const isPointInsideDisplayedImage = (x: number, y: number) => {
  const imageRect = mainImage.value?.getBoundingClientRect()
  if (imageRect && imageRect.width > 1 && imageRect.height > 1) {
    return x >= imageRect.left && x <= imageRect.right && y >= imageRect.top && y <= imageRect.bottom
  }
  const wrapperRect = imageWrapper.value?.getBoundingClientRect()
  return !!wrapperRect && wrapperRect.width > 1 && wrapperRect.height > 1
    && x >= wrapperRect.left && x <= wrapperRect.right
    && y >= wrapperRect.top && y <= wrapperRect.bottom
}

const getTapAction = (target: EventTarget | null, x?: number, y?: number): 'close' | 'toggle-controls' => {
  // Coordinates are authoritative for the full-size content area. The target
  // fallback keeps deferred taps working in browsers that omit client coords
  // on synthetic events.
  if (typeof x === 'number' && typeof y === 'number') {
    return isPointInsideDisplayedImage(x, y) ? 'toggle-controls' : 'close'
  }
  return target === imageWrapper.value || target === mainImage.value
    ? 'toggle-controls'
    : 'close'
}

const flushDeferredTapAction = () => {
  const action = deferredTapAction.value
  deferredTapAction.value = null
  if (!action || closing.value || !props.visible) return
  if (action === 'close') close()
  else controlsVisible.value = !controlsVisible.value
}

const onKeydown = (e: KeyboardEvent) => {
  if (!props.visible) return
  if (e.key === 'Escape') {
    close()
  } else if (e.key === 'ArrowLeft') {
    prev()
  } else if (e.key === 'ArrowRight') {
    next()
  } else if (e.key === 'Home') {
    jump(0)
  } else if (e.key === 'End') {
    jump(Math.max(0, props.photos.length - 1))
  } else if (e.key === '0') {
    scale.value = 1
    translateX.value = 0
    translateY.value = 0
    userHasManuallyZoomed.value = false
  } else if (e.key === ' ') {
    controlsVisible.value = !controlsVisible.value
  }
}

const onImageLoad = async (event?: Event) => {
  const img = event?.currentTarget as HTMLImageElement | null
  if (!img || img !== mainImage.value || !currentPhoto.value) return
  {
    if (img.complete && img.naturalWidth > 0 && img.naturalHeight > 0) {
      // 记录图片原始尺寸
      imageSize.value = {
        width: img.naturalWidth,
        height: img.naturalHeight
      }

      // Keep the user's current transform when a background original replaces
      // an already-visible thumbnail. Reset only for a genuinely new photo.
      if (!imageLoaded.value) {
        scale.value = 1
        translateX.value = 0
        translateY.value = 0
      }

      imageLoaded.value = true
      imageLoadError.value = false
      // 图片加载完成后，重置切换标记，允许框体动画
      isSwitchingPhoto.value = false
      // Vue has to commit the new intrinsic ratio before a FLIP target can be
      // measured. Measuring in the load callback sampled the previous image's
      // rendered box, which caused horizontal-to-vertical opening distortion.
      await nextTick()
      if (img !== mainImage.value || !currentPhoto.value) return
      applyInfoPanelOffset(false)
      await nextTick()
      if (img !== mainImage.value || !currentPhoto.value) return
      if (openingPreviewVisible.value && openingPreviewTransform.value === null) {
        requestAnimationFrame(() => {
          if (img === mainImage.value) openingPreviewVisible.value = false
        })
      }
      prepareOpeningTransform()

    }
  }
}

const onDisplayedImageLoad = (event?: Event) => {
  const img = event?.currentTarget as HTMLImageElement | null
  if (!img || img !== mainImage.value || !currentPhoto.value) return
  if ((!imageSize.value.width || !imageSize.value.height) && img.naturalWidth > 0 && img.naturalHeight > 0) {
    imageSize.value = { width: img.naturalWidth, height: img.naturalHeight }
    void nextTick(() => applyInfoPanelOffset(false))
  }
  // The thumbnail is drawable immediately; layout dimensions come from photo
  // metadata. Full-image lifecycle is handled by the hidden preloader below.
  if (largeImageReady.value && img.currentSrc === largeImagePreloadUrl.value) {
    onImageLoad(event)
  } else {
    // A decoded thumbnail is already a valid drawable image. Do not keep the
    // opening FLIP layer waiting for the original asset.
    imageLoaded.value = true
  }
}

const onLargeImageLoad = (event?: Event) => {
  const img = event?.currentTarget as HTMLImageElement | null
  if (!img || !currentPhoto.value || img.naturalWidth <= 0) return
  const url = largeImagePreloadUrl.value
  if (!url || url !== getImageUrl(currentPhoto.value)) return
  largeImageReady.value = true
  imageLoadError.value = false
  displayedImageUrl.value = url
}

const onImageError = (event?: Event) => {
  const img = event?.currentTarget as HTMLImageElement | null
  if (img && img !== mainImage.value) return
  // 图片加载失败，设置为已加载状态避免一直显示加载中
  console.error('❌ PhotoViewer: 图片加载失败', {
    currentPhoto: currentPhoto.value?.filename,
    imageUrl: currentPhoto.value ? getImageUrl(currentPhoto.value) : 'N/A',
    photoId: currentPhoto.value?.id,
    error: '图片加载失败，可能的原因：网络错误、文件不存在、权限问题等'
  })
  imageLoaded.value = true
  imageLoadError.value = true
  openingPreviewVisible.value = false
}

const retryCurrentImage = () => {
  imageLoadError.value = false
  imageLoaded.value = false
  imageRetryToken.value += 1
}

// 图片双击放大/缩小
const onImageDoubleClick = (e: MouseEvent) => {
  if (containerClickTimer) {
    clearTimeout(containerClickTimer)
    containerClickTimer = null
  }
  if (performance.now() < interactionReadyAt.value || swipeTransitioning.value) {
    e.preventDefault()
    e.stopPropagation()
    return
  }
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

const pointerDistance = (a: { x: number; y: number }, b: { x: number; y: number }) => {
  return Math.hypot(a.x - b.x, a.y - b.y)
}

const pointerCenter = (points: { x: number; y: number }[]) => ({
  x: points.reduce((sum, p) => sum + p.x, 0) / points.length,
  y: points.reduce((sum, p) => sum + p.y, 0) / points.length
})

// Pointer Events 主路径：不再依赖浏览器合成 mouse/touch 事件，避免同一次手势被处理两遍。
const onImagePointerDown = (e: PointerEvent) => {
  if (closing.value) return
  if (performance.now() < interactionReadyAt.value) {
    ignoredPointerIds.add(e.pointerId)
    ;(e.currentTarget as HTMLElement).setPointerCapture?.(e.pointerId)
    e.preventDefault()
    return
  }
  // 过渡进行中按下手指时接管当前动画，让连续滑动不必等上一次播完。
  // 提交索引 + 重新基准化偏移量后画面像素位置不变，手势从这里直接续上。
  const rebasedOffset = interruptSwipeTransition()
  const target = e.currentTarget as HTMLElement
  target.setPointerCapture?.(e.pointerId)
  // 清理浏览器未发送 pointerup 的陈旧指针，避免下一次单指被误判为双指。
  if (pointerGesture.value === 'none' && activePointers.size > 0) {
    activePointers.clear()
    isPinching.value = false
  }
  activePointers.set(e.pointerId, { x: e.clientX, y: e.clientY })
  lastPointerPos.value = { x: e.clientX, y: e.clientY }

  const now = performance.now()
  const tapDistance = Math.hypot(e.clientX - lastTapX.value, e.clientY - lastTapY.value)
  const isSecondTap = now - lastTapTime.value < 320 && tapDistance < 32
  if (isSecondTap && activePointers.size === 1) {
    pointerGesture.value = 'holdZoom'
    initialScale.value = scale.value > 1 ? scale.value : 1
    pointerStartY.value = e.clientY
    isZooming.value = true
    userHasManuallyZoomed.value = true
    e.preventDefault()
    return
  }

  if (activePointers.size >= 2) {
    const points = [...activePointers.values()]
    pointerGesture.value = 'pinch'
    pointerPinchDistance.value = pointerDistance(points[0], points[1]) || 1
    pointerPinchScale.value = scale.value
    pointerPinchTranslate.value = { x: translateX.value, y: translateY.value }
    pointerPinchCenter.value = pointerCenter(points)
    isImageDragging.value = false
    isPinching.value = true
    e.preventDefault()
    return
  }

  pointerGesture.value = 'pending'
  pointerStartX.value = pointerLastX.value = e.clientX
  pointerStartY.value = pointerLastY.value = e.clientY
  pointerStartTime.value = performance.now()
  // 接管了一次过渡时，偏移量必须保留重新基准化后的值，手势从当前画面位置
  // 继续；清零会让画面瞬间跳回中心，正是要避免的那种跳变。
  pointerSwipeBaseOffset.value = rebasedOffset ?? 0
  imageDragOffset.value = rebasedOffset ?? 0
  touchSwipeOffset.value = rebasedOffset ?? 0
  isImageDragging.value = false
  resetSwipeVelocity(e.clientX)
  e.preventDefault()
}

const onImagePointerMove = (e: PointerEvent) => {
  if (ignoredPointerIds.has(e.pointerId)) {
    e.preventDefault()
    return
  }
  if (!activePointers.has(e.pointerId)) return
  activePointers.set(e.pointerId, { x: e.clientX, y: e.clientY })
  lastPointerPos.value = { x: e.clientX, y: e.clientY }

  if (activePointers.size >= 2 || pointerGesture.value === 'pinch') {
    const points = [...activePointers.values()]
    if (points.length < 2) return
    const distance = pointerDistance(points[0], points[1]) || 1
    const center = pointerCenter(points)
    const viewport = imageViewport.value?.getBoundingClientRect()
    const ratio = distance / Math.max(pointerPinchDistance.value, 1)
    const nextScale = Math.min(5, Math.max(1, pointerPinchScale.value * ratio))
    if (viewport) {
      const px = center.x - (viewport.left + viewport.width / 2)
      const py = center.y - (viewport.top + viewport.height / 2)
      const qx = (px - pointerPinchTranslate.value.x) / Math.max(pointerPinchScale.value, 1)
      const qy = (py - pointerPinchTranslate.value.y) / Math.max(pointerPinchScale.value, 1)
      translateX.value = px - nextScale * qx
      translateY.value = py - nextScale * qy
    }
    scale.value = nextScale
    userHasManuallyZoomed.value = nextScale > 1
    pointerGesture.value = 'pinch'
    isPinching.value = true
    e.preventDefault()
    return
  }

  const dx = e.clientX - pointerStartX.value
  const dy = e.clientY - pointerStartY.value
  if (pointerGesture.value === 'pending') {
    if (Math.hypot(dx, dy) < 8) return
    if (scale.value > 1) {
      pointerGesture.value = 'pan'
      isImageDragging.value = true
      initialTranslateX.value = translateX.value
      initialTranslateY.value = translateY.value
    } else if (Math.abs(dx) >= Math.abs(dy) * 1.1) {
      pointerGesture.value = 'swipe'
      isImageDragging.value = true
    } else if (dy > 0 && Math.abs(dy) >= Math.abs(dx) * 1.1 && scale.value <= 1) {
      pointerGesture.value = 'dismiss'
      isDismissing.value = true
      isImageDragging.value = true
    } else {
      pointerGesture.value = 'none'
      return
    }
  }

  if (pointerGesture.value === 'pan') {
    translateX.value = initialTranslateX.value + dx
    translateY.value = initialTranslateY.value + dy
  } else if (pointerGesture.value === 'swipe') {
    const rawOffset = pointerSwipeBaseOffset.value + dx
    const bounded = (rawOffset > 0 && !previousPhoto.value) || (rawOffset < 0 && !nextPhoto.value)
      ? pointerSwipeBaseOffset.value + dx * 0.25
      : rawOffset
    imageDragOffset.value = bounded
    touchSwipeOffset.value = bounded
    sampleSwipeVelocity(e.clientX)
  } else if (pointerGesture.value === 'dismiss') {
    dismissOffset.value = Math.max(0, dy)
  } else if (pointerGesture.value === 'holdZoom') {
    const nextScale = Math.min(5, Math.max(1, initialScale.value + (pointerStartY.value - e.clientY) * 0.01))
    scale.value = nextScale
  }
  pointerLastX.value = e.clientX
  pointerLastY.value = e.clientY
  e.preventDefault()
}

const onImagePointerUp = (e: PointerEvent) => {
  if (ignoredPointerIds.delete(e.pointerId)) {
    try { (e.currentTarget as HTMLElement).releasePointerCapture?.(e.pointerId) } catch { /* already released */ }
    e.preventDefault()
    return
  }
  activePointers.delete(e.pointerId)
  const target = e.currentTarget as HTMLElement
  try { target.releasePointerCapture?.(e.pointerId) } catch { /* pointer may already be released */ }

  if (activePointers.size > 0) {
    if (activePointers.size === 1 && pointerGesture.value === 'pinch') {
      const remaining = [...activePointers.values()][0]
      pointerStartX.value = pointerLastX.value = remaining.x
      pointerStartY.value = pointerLastY.value = remaining.y
      pointerGesture.value = scale.value > 1 ? 'pan' : 'none'
      initialTranslateX.value = translateX.value
      initialTranslateY.value = translateY.value
    }
    return
  }

  const mode = pointerGesture.value
  pointerGesture.value = 'none'
  isPinching.value = false
  if (mode === 'holdZoom') {
    isZooming.value = false
    userHasManuallyZoomed.value = scale.value > 1
    lastTapTime.value = 0
  } else if (mode === 'dismiss') {
    const elapsed = Math.max(1, performance.now() - pointerStartTime.value)
    const progress = dismissOffset.value / Math.max(window.innerHeight, 1)
    const velocity = dismissOffset.value / elapsed
    isImageDragging.value = false
    if (progress > 0.22 || velocity > 0.65) {
      // close() 会自己读取图片此刻的可见框，并负责清理手势状态。
      close()
      // 带速度松手会让浏览器启动 fling，随后吞掉相册详情页上的第一次点击。
      if (e.pointerType === 'touch') armFlingTapRepair()
    } else {
      // Keep the dismiss transform mounted while it animates back to center;
      // clearing the mode in the same frame would make the image snap.
      dismissOffset.value = 0
      window.setTimeout(() => { isDismissing.value = false }, 260)
    }
  } else if (mode === 'swipe') {
    const offset = touchSwipeOffset.value
    // 本次手势自身走过的距离。接管过渡时 pointerSwipeBaseOffset 是重新基准化
    // 后的起点，所以它和轨道总偏移不是一回事。
    const dragDelta = offset - pointerSwipeBaseOffset.value
    const threshold = Math.min(140, Math.max(64, window.innerWidth * 0.18))
    const halfWidth = window.innerWidth / 2

    if (Math.abs(swipeVelocity) > 0.6) {
      // 甩动优先：方向由速度决定，哪怕位移还很小。
      finishSwipe(swipeVelocity > 0 ? 'previous' : 'next', swipeVelocity)
    } else if (Math.abs(dragDelta) > threshold) {
      finishSwipe(dragDelta > 0 ? 'previous' : 'next', swipeVelocity)
    } else if (Math.abs(offset) > halfWidth) {
      // 接管过渡后手指几乎没动：就近吸附，而不是硬拉回接管前的那一张。
      finishSwipe(offset > 0 ? 'previous' : 'next', swipeVelocity)
    } else {
      cancelSwipe(swipeVelocity)
    }
  } else if (touchSwipeOffset.value !== 0 || imageDragOffset.value !== 0) {
    // 点按接管了一次过渡（手指没移动，但轨道停在重新基准化后的位置）。
    // 直接清零会让主图带过渡缓动、相邻图却瞬间归位；交给 cancelSwipe 走
    // 同一条轨道动画，整体一起落位。
    cancelSwipe(0)
    if (mode === 'pending') {
      lastTapTime.value = performance.now()
      lastTapX.value = e.clientX
      lastTapY.value = e.clientY
    }
  } else {
    imageDragOffset.value = 0
    touchSwipeOffset.value = 0
    if (mode === 'pending') {
      lastTapTime.value = performance.now()
      lastTapX.value = e.clientX
      lastTapY.value = e.clientY
    }
  }
  isImageDragging.value = false
  e.preventDefault()
}

// pointercancel 表示浏览器/系统已经接管并取消了当前指针序列，不能再把
// 它当成普通 pointerup 继续提交滑动或点击状态。快速下滑时移动端更容易
// 触发该事件；若仍走 pointerup 路径，会留下兼容 click/捕获状态，吞掉关闭
// PhotoViewer 后的下一次点击。
const onImagePointerCancel = (e: PointerEvent) => {
  if (ignoredPointerIds.delete(e.pointerId)) {
    try { (e.currentTarget as HTMLElement).releasePointerCapture?.(e.pointerId) } catch { /* already released */ }
    return
  }

  const mode = pointerGesture.value
  const shouldDismiss = mode === 'dismiss' && dismissOffset.value > 0
    && dismissOffset.value / Math.max(window.innerHeight, 1) > 0.22

  activePointers.delete(e.pointerId)
  try { (e.currentTarget as HTMLElement).releasePointerCapture?.(e.pointerId) } catch { /* already released */ }

  activePointers.clear()
  pointerGesture.value = 'none'
  isPinching.value = false
  isZooming.value = false
  isPanning.value = false
  isImageDragging.value = false
  isDismissing.value = false
  dismissOffset.value = 0
  imageDragOffset.value = 0
  touchSwipeOffset.value = 0
  deferredTapAction.value = null
  lastTapTime.value = 0

  if (shouldDismiss) {
    close()
    if (e.pointerType === 'touch') armFlingTapRepair()
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

    } else {
    // 原始大小状态下，如果是水平拖拽且距离足够，准备切换图片
    if (Math.abs(deltaX) > Math.abs(deltaY) && Math.abs(deltaX) > 10) {
      imageDragOffset.value = ((deltaX > 0 && !previousPhoto.value) || (deltaX < 0 && !nextPhoto.value))
        ? deltaX * 0.25
        : deltaX
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
    // 判断是否应该切换图片
    const threshold = Math.min(140, Math.max(64, window.innerWidth * 0.18))
    const shouldSwitch = Math.abs(offset) > threshold

    if (shouldSwitch) {
      if (offset > 0) {
        finishSwipe('previous')
      } else {
        finishSwipe('next')
      }
    } else {
      cancelSwipe()
    }
  }

  // 重置状态
  isImageDragging.value = false
  if (!swipeTransitioning.value) imageDragOffset.value = 0
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
    
  } else if (e.touches.length === 1) {
    // 记录滑动开始的X坐标
    touchSwipeStartX.value = e.touches[0].clientX
    touchSwipeStartY.value = e.touches[0].clientY
    touchSwipeOffset.value = 0

    // 单指触摸：在放大状态下可以拖拽
    if (scale.value > 1) {
      isImageDragging.value = true
      imageDragStartX.value = e.touches[0].clientX
      imageDragStartY.value = e.touches[0].clientY
      initialTranslateX.value = translateX.value
      initialTranslateY.value = translateY.value

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
      
    } else if (centerMoveDistance > 5 && scale.value > 1) {
      // 双指移动（平移）
      isPinching.value = false
      isImageDragging.value = true
      
      translateX.value = initialTranslateX.value + centerDeltaX
      translateY.value = initialTranslateY.value + centerDeltaY
      
    }
    
    // 更新中心点
    lastTouchCenter.value = { x: centerX, y: centerY }
  } else if (e.touches.length === 1 && isImageDragging.value && scale.value > 1) {
    // 单指拖拽移动（仅在放大状态下）
    const deltaX = e.touches[0].clientX - imageDragStartX.value
    const deltaY = e.touches[0].clientY - imageDragStartY.value

    translateX.value = initialTranslateX.value + deltaX
    translateY.value = initialTranslateY.value + deltaY

  } else if (e.touches.length === 1 && scale.value <= 1) {
    // 单指水平滑动（仅在未放大状态下，用于切换照片）
    const currentX = e.touches[0].clientX
    const currentY = e.touches[0].clientY
    const offset = currentX - touchSwipeStartX.value
    const verticalOffset = currentY - touchSwipeStartY.value
    if (Math.abs(verticalOffset) > Math.abs(offset) * 1.1) {
      touchSwipeOffset.value = 0
      return
    }
    touchSwipeOffset.value = offset

    // 提供视觉反馈：图片跟随稍微移动（限制最大移动距离）
    // 到边界时使用阻尼，保留“拉不动”的反馈
    if ((offset > 0 && !previousPhoto.value) || (offset < 0 && !nextPhoto.value)) {
      touchSwipeOffset.value = offset * 0.25
    }
  }

  e.preventDefault()
}

const onImageTouchEnd = (e: TouchEvent) => {
  // 单指触摸结束时的处理
  if (e.touches.length === 0) {
    const swipeOffset = touchSwipeOffset.value
    const threshold = Math.min(140, Math.max(64, window.innerWidth * 0.18))
    const didSwitch = Math.abs(swipeOffset) > threshold

    // 如果达到切换阈值，切换图片
    if (didSwitch) {
      if (swipeOffset > 0) {
        // 向右滑动：上一张
        finishSwipe('previous')
      } else {
        // 向左滑动：下一张
        finishSwipe('next')
      }
    } else {
      // 未达到切换阈值，弹回原位
      cancelSwipe()
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
    }

    if (isImageDragging.value) {
      isImageDragging.value = false
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
  if (!props.visible || closing.value) return
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

  if (e.key === 'Home') { e.preventDefault(); jump(0); return }
  if (e.key === 'End') { e.preventDefault(); jump(Math.max(0, props.photos.length - 1)); return }
  if (e.key === '0') {
    e.preventDefault()
    scale.value = 1
    translateX.value = 0
    translateY.value = 0
    userHasManuallyZoomed.value = false
    return
  }
  if (e.key === ' ') { e.preventDefault(); controlsVisible.value = !controlsVisible.value; return }

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

const startDrag = (e: PointerEvent) => {
  isResizingThumb.value = true
  thumbResizeStartY.value = e.clientY
  thumbResizeStartHeight.value = thumbHeight.value
  const handle = e.currentTarget as HTMLElement
  activeThumbResizeHandle.value = handle
  handle.setPointerCapture?.(e.pointerId)
  document.body.style.cursor = 'ns-resize'
  document.body.style.userSelect = 'none'
  window.addEventListener('pointermove', onThumbResizeMove, true)
  window.addEventListener('pointerup', endThumbResize, true)
  window.addEventListener('pointercancel', endThumbResize, true)
  e.preventDefault()
}

const onThumbResizeMove = (e: PointerEvent) => {
  if (!isResizingThumb.value) return
  const deltaY = thumbResizeStartY.value - e.clientY
  thumbHeight.value = Math.max(80, Math.min(300, thumbResizeStartHeight.value + deltaY))
  e.preventDefault()
}

const endThumbResize = (e: PointerEvent) => {
  if (!isResizingThumb.value) return
  isResizingThumb.value = false
  try { activeThumbResizeHandle.value?.releasePointerCapture?.(e.pointerId) } catch { /* already released */ }
  activeThumbResizeHandle.value = null
  localStorage.setItem(THUMB_KEY, thumbHeight.value.toString())
  document.body.style.cursor = ''
  document.body.style.userSelect = ''
  window.removeEventListener('pointermove', onThumbResizeMove, true)
  window.removeEventListener('pointerup', endThumbResize, true)
  window.removeEventListener('pointercancel', endThumbResize, true)
}

// 路由跳转函数
const router = useRouter()

const openAlbum = () => {
  if (!currentPhoto.value) return
  // 使用短路由 /a/ID
  const route = router.resolve({ path: buildPublicPath(`/a/${currentPhoto.value.albumId}`) })
  window.open(route.href, '_blank')
}

// 打开照片详情页
const openPhotoPage = () => {
  if (!currentPhoto.value?.id) return
  const route = router.resolve({ path: buildPublicPath(`/photo/${currentPhoto.value.id}`) })
  window.open(route.href, '_blank')
}

const openTag = (tag: any) => {
  if (!tag?.id) return
  const route = router.resolve({ path: buildPublicPath('/wall'), query: { tagId: tag.id, tagName: tag.name } })
  window.open(route.href, '_blank')
}

const openPersonByFace = (face: { personId?: number; personName?: string }) => {
  if (!face.personId || !face.personName) return
  const route = router.resolve({
    path: buildPublicPath('/wall'),
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
    path: buildPublicPath('/search'),
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
  const route = router.resolve({ path: buildPublicPath('/random'), query: { filters: JSON.stringify(filters) } })
  window.open(route.href, '_blank')
}

const filterByCamera = () => {
  if (!currentPhoto.value?.cameraMake && !currentPhoto.value?.cameraModel) return
  const cameraModel = currentPhoto.value.cameraModel || ''
  const route = router.resolve({ path: buildPublicPath('/random'), query: { filters: JSON.stringify({ cameraModel }) } })
  window.open(route.href, '_blank')
}

const filterByLens = () => {
  if (!currentPhoto.value?.lensModel) return
  const lensModel = currentPhoto.value.lensModel
  const route = router.resolve({ path: buildPublicPath('/random'), query: { filters: JSON.stringify({ lensModel }) } })
  window.open(route.href, '_blank')
}

const filterByFocalLength = () => {
  if (!currentPhoto.value?.focalLength) return
  const focalLength = parseFloat(currentPhoto.value.focalLength.replace('mm', ''))
  const filters = {
    minFocalLength: focalLength,
    maxFocalLength: focalLength
  }
  const route = router.resolve({ path: buildPublicPath('/random'), query: { filters: JSON.stringify(filters) } })
  window.open(route.href, '_blank')
}

const filterByAperture = () => {
  if (!currentPhoto.value?.aperture) return
  const aperture = parseFloat(currentPhoto.value.aperture.replace('f/', ''))
  const filters = {
    minAperture: aperture,
    maxAperture: aperture
  }
  const route = router.resolve({ path: buildPublicPath('/random'), query: { filters: JSON.stringify(filters) } })
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
  const route = router.resolve({ path: buildPublicPath('/random'), query: { filters: JSON.stringify(filters) } })
  window.open(route.href, '_blank')
}

const filterByIso = () => {
  if (!currentPhoto.value?.iso) return
  const filters = {
    minIso: currentPhoto.value.iso,
    maxIso: currentPhoto.value.iso
  }
  const route = router.resolve({ path: buildPublicPath('/random'), query: { filters: JSON.stringify(filters) } })
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
  return buildPhotoAssetUrl({
    id: photo.id || face?.photoId,
    originalPath: photo.originalPath || face?.photoOriginalPath,
    thumbnailPath: face?.photoThumbnailPath || photo.thumbnailPath,
    webpPath: photo.webpPath
  }, 'thumbnail') || ''
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
    navigation.reset(targetIndex)
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
  })

  window.addEventListener('keydown', onKeyDown)
  window.addEventListener('resize', onWindowResize)
  window.addEventListener('resize', onImageLoad)
  document.addEventListener('fullscreenchange', onFullscreenChange)
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', onKeyDown)
  window.removeEventListener('resize', onWindowResize)
  window.removeEventListener('resize', onImageLoad)
  document.removeEventListener('fullscreenchange', onFullscreenChange)
  activePointers.clear()
  pointerGesture.value = 'none'
  clearTimeout((onImageWheel as any).zoomTimeout)
  clearTimeout((onImageWheel as any).panTimeout)
  if (swipeTimer) clearTimeout(swipeTimer)
  if (openingPreviewTimer) clearTimeout(openingPreviewTimer)
  if (containerClickTimer) clearTimeout(containerClickTimer)
  window.removeEventListener('pointermove', onResize, true)
  window.removeEventListener('pointerup', endResize, true)
  window.removeEventListener('pointercancel', endResize, true)
  window.removeEventListener('pointermove', onThumbResizeMove, true)
  window.removeEventListener('pointerup', endThumbResize, true)
  window.removeEventListener('pointercancel', endThumbResize, true)
  document.body.style.cursor = ''
  document.body.style.userSelect = ''
  activeInfoResizeHandle.value = null
  activeThumbResizeHandle.value = null
  // 收回动画途中被卸载时，结束事件不会由定时器发出；这里补一次，
  // 否则宿主页面藏起来的那张缩略图会一直不显示。
  if (returningToThumb.value) {
    returningToThumb.value = false
    emit('return-transition', { photoId: currentPhoto.value?.id ?? null, active: false })
  }
  flightAnimation?.cancel()
  flightRadiusAnimation?.cancel()
  flightImageAnimation?.cancel()
  flightAnimation = null
  flightRadiusAnimation = null
  flightImageAnimation = null
  flight.value = null
  disposeFlingTapRepair()
  assetManager.clear()
})
</script>

<style scoped>
/* 收回飞行层：外层负责裁切与圆角，内层图片用 cover 填满，
   于是盒子变形的过程就是裁切逐渐长出来的过程。 */
.closing-flight-image {
  /* 尺寸/裁切由 flightImageStyle 给出（铺满取景窗 + cover），回弹由 WAAPI 驱动。 */
  display: block;
  max-width: none;
  max-height: none;
  transition: none;
}

/* 收回到缩略图期间只淡出操作栏，图片保持不透明飞回去（见 modalStyle 的说明）。 */
.viewer-returning :deep(.top-bar),
.viewer-returning :deep(.thumbnail-bar),
.viewer-returning :deep(.info-panel) {
  opacity: 0;
  transition: opacity 180ms ease-out;
}

/* 关闭期间（closing 计时 + leave 过渡，约 540ms）节点还挂在最上层，而根节点上的
   pointer-events: none 不会覆盖顶部栏、缩略图栏、信息面板上的 pointer-events: auto，
   这些元素会挡住本该落到相册详情页的点击（顶部栏正好压住返回按钮）。 */
.viewer-inert,
.viewer-inert :deep(*) {
  pointer-events: none !important;
}

/* 模态框显示/隐藏动画 */
.modal-enter-active {
  transition: opacity 0.3s cubic-bezier(0.4, 0, 0.2, 1), background-color 0.3s ease;
  will-change: opacity, background-color;
}
.modal-leave-active {
  transition: opacity 0.26s cubic-bezier(0.4, 0, 0.2, 1), background-color 0.26s ease;
  will-change: opacity, background-color;
}
.modal-enter-from {
  opacity: 0;
  background-color: transparent;
}
.modal-leave-to {
  opacity: 0;
  background-color: transparent;
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
.main-image {
  will-change: transform, opacity;
  backface-visibility: hidden;
  -webkit-backface-visibility: hidden;
}

.swipe-stage {
  z-index: 0;
}
.swipe-stage,
.photo-viewer-img-wrapper {
  touch-action: none;
}
.resize-handle,
.drag-handle,
.photo-viewer-img-wrapper {
  touch-action: none;
  -webkit-user-select: none;
  user-select: none;
}
.swipe-adjacent-image {
  will-change: transform, opacity;
  border-radius: 2px;
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

@media (prefers-reduced-motion: reduce) {
  .main-image,
  .swipe-adjacent-image,
  .photo-viewer-img-wrapper,
  .modal-enter-active,
  .modal-leave-active {
    transition: none !important;
    animation: none !important;
  }
}
</style>
