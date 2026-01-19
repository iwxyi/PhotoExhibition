<template>
  <transition name="fade">
    <div
      v-if="visible"
      class="fixed inset-0 z-50 bg-black/90 backdrop-blur-sm flex flex-col outline-none focus:outline-none"
      @keydown.stop.prevent="onKeydown"
      @click="onBackdropClick"
      tabindex="0"
      ref="modalRoot"
    >
      <!-- 顶部栏 -->
      <div v-if="!isFullscreen" class="flex items-center justify-between px-4 sm:px-6 py-3 text-white text-sm">
        <div class="flex items-center gap-3">
          <button class="p-2 hover:bg-white/10 rounded" @click="close" title="关闭">
            <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
          <div class="text-xs sm:text-sm opacity-80 flex items-center gap-2">
            <span>{{ currentPhoto?.filename }}</span>
          </div>
        </div>
        <div class="flex items-center gap-3">
          <button class="p-2 hover:bg-white/10 rounded" @click="toggleFullscreen" :title="isFullscreen ? '退出全屏' : '全屏查看'">
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
            class="p-2 hover:bg-white/10 rounded"
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

      <!-- 全屏简化控制：左上关闭，右上全屏切换 -->
      <div v-else class="pointer-events-none">
        <div class="absolute left-4 top-4 z-50 pointer-events-auto">
          <button class="p-2 hover:bg-white/10 rounded text-white" @click="close" title="关闭">
            <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>
        <div class="absolute right-4 top-4 z-50 pointer-events-auto">
          <button class="p-2 hover:bg-white/10 rounded text-white" @click="toggleFullscreen" :title="isFullscreen ? '退出全屏' : '全屏查看'">
            <svg v-if="!isFullscreen" class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 8V4h4M4 4l6 6M20 16v4h-4m4 0l-6-6M16 4h4v4m0-4l-6 6M8 20H4v-4m0 4l6-6" />
            </svg>
            <svg v-else class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 9H5V5m10 10h4v4M9 15H5v4m10-10h4V5" />
            </svg>
          </button>
        </div>
      </div>

      <div class="flex-1 flex overflow-hidden min-h-0 relative" @click="onBackdropClick">
        <!-- 主图区域 -->
        <div class="flex-1 flex items-center justify-center relative px-2 sm:px-6 min-h-0">
          <div
            class="relative w-full h-full flex items-center justify-center overflow-hidden"
            ref="imageContainer"
            @touchstart="onTouchStartZoom"
            @touchmove="onTouchMoveZoom"
            @touchend="onTouchEndZoom"
            style="touch-action: none;"
          >
            <div
              class="relative inline-block viewer-open-anim"
              ref="imageWrapper"
              :class="{ 'viewer-open-anim--active': isOpeningFromThumb }"
              :style="[getImageTransformStyle(), openAnimStyle]"
              @wheel="onWheelZoom"
              @dblclick="onDoubleClick"
              @mousedown="onMouseDown"
            >
              <img
                v-if="currentPhoto"
                :key="mainImageKey"
                :src="getImageUrl(currentPhoto)"
                :alt="currentPhoto.filename"
                class="select-none"
                :style="getImageStyle()"
                ref="mainImage"
                @load="onImageLoad"
                @touchstart="onTouchStartZoom"
                @touchmove="onTouchMoveZoom"
                @touchend="onTouchEndZoom"
                draggable="false"
              />

              <!-- 过渡图片（进入动画） -->
              <img
                v-if="transitioningPhoto"
                :key="'transition-' + transitioningPhoto.id"
                :src="getImageUrl(transitioningPhoto)"
                :alt="transitioningPhoto.filename"
                class="select-none absolute inset-0 w-full h-full object-contain z-10"
                :style="getTransitionImageTransformStyle()"
                draggable="false"
              />

              <!-- 焦点框覆盖层（作为 wrapper 的子元素，这样 transform/scale 一致） -->
              <div
                v-if="currentPhoto && showFocusBox && imageLoaded && !isOpeningFromThumb && currentPhoto.focusX !== undefined && currentPhoto.focusY !== undefined"
                class="absolute pointer-events-none"
                :style="getFocusBoxStyle()"
              >
                <div class="absolute inset-0 border-2 border-yellow-400 shadow-lg shadow-yellow-400/50"></div>
                <div class="absolute -top-6 left-0 text-xs text-yellow-400 bg-black/60 px-1 rounded whitespace-nowrap">
                  焦点 ({{ currentPhoto.focusX.toFixed(1) }}%, {{ currentPhoto.focusY.toFixed(1) }}%)
                </div>
              </div>

              <div
                v-for="box in faceBoxes"
                :key="box.id"
                class="absolute pointer-events-none"
                :style="box.style"
              >
                <div
                  class="absolute inset-0 border-2 rounded-sm shadow-lg"
                  :class="box.confirmed ? 'border-green-400 shadow-green-400/50' : 'border-amber-400 shadow-amber-400/50'"
                ></div>
                <div
                  class="absolute -top-5 left-0 text-xs px-1 rounded whitespace-nowrap"
                  :class="box.confirmed ? 'bg-green-500/80 text-white' : 'bg-amber-500/80 text-white'"
                >
                  {{ box.label }}
                </div>
              </div>
            </div>
          </div>

          <!-- 左右切换按钮 -->
          <button
            v-if="!isFullscreen"
            class="absolute left-2 sm:left-6 top-1/2 -translate-y-1/2 p-3 rounded-full bg-black/40 text-white hover:bg-black/60"
            @click="prev"
          >
            ‹
          </button>
          <button
            v-if="!isFullscreen"
            class="absolute right-2 sm:right-6 top-1/2 -translate-y-1/2 p-3 rounded-full bg-black/40 text-white hover:bg-black/60"
            @click="next"
          >
            ›
          </button>
        </div>

        <!-- 信息侧栏 -->
        <div v-if="!infoCollapsed" class="absolute top-0 right-0 bottom-0 w-80 text-white border-l border-white/10 flex flex-col max-h-full overflow-auto transition-all duration-300 z-10"
             :class="infoTransparent ? 'bg-gray-900/30' : 'bg-gray-900/80'">
          <div class="flex items-center justify-between px-4 py-3 border-b border-white/10">
            <span class="text-sm font-semibold">信息</span>
            <button class="text-xs opacity-70 hover:opacity-100" @click="toggleInfoTransparency">
              {{ infoTransparent ? '不透明' : '透明' }}
            </button>
          </div>
          <div class="flex-1 overflow-auto px-4 py-3 space-y-2 text-xs leading-relaxed">
            <!-- 基本信息 -->
            <div><span class="opacity-60">文件名：</span>{{ currentPhoto?.filename }}</div>
            <div v-if="currentAlbumPath">
              <span class="opacity-60">路径：</span>
              <span
                class="truncate opacity-80 cursor-pointer hover:opacity-100"
                :title="'点击跳转到相册: ' + currentAlbumPath"
                @click="openAlbum"
              >
                {{ currentAlbumPath }}
              </span>
            </div>
            <div v-if="currentPhoto?.takenAt">
              <span class="opacity-60">拍摄时间：</span>
              <span class="cursor-pointer hover:opacity-100" @click="filterByTakenAt">
                {{ formatDate(currentPhoto.takenAt) }}
              </span>
            </div>

            <!-- 相机和镜头信息 -->
            <div v-if="currentPhoto?.cameraMake || currentPhoto?.cameraModel">
              <span class="opacity-60">相机：</span>
              <span class="cursor-pointer hover:opacity-100" @click="filterByCamera">
                {{ currentPhoto.cameraMake ? currentPhoto.cameraMake + ' ' : '' }}{{ currentPhoto.cameraModel }}
              </span>
            </div>
            <div v-if="currentPhoto?.lensModel">
              <span class="opacity-60">镜头：</span>
              <span class="cursor-pointer hover:opacity-100" @click="filterByLens">
                {{ currentPhoto.lensModel }}
              </span>
            </div>

            <!-- 参数网格布局 -->
            <div class="grid grid-cols-2 gap-2">
              <!-- 第一行：焦距和光圈 -->
              <div v-if="currentPhoto?.focalLength">
                <span class="opacity-60">焦距：</span>
                <span class="cursor-pointer hover:opacity-100" @click="filterByFocalLength">
                  {{ currentPhoto.focalLength }}
                </span>
              </div>
              <div v-if="currentPhoto?.aperture">
                <span class="opacity-60">光圈：</span>
                <span class="cursor-pointer hover:opacity-100" @click="filterByAperture">
                  {{ currentPhoto.aperture }}
                </span>
              </div>

              <!-- 第二行：快门和ISO -->
              <div v-if="currentPhoto?.shutterSpeed">
                <span class="opacity-60">快门：</span>
                <span class="cursor-pointer hover:opacity-100" @click="filterByShutterSpeed">
                  {{ currentPhoto.shutterSpeed }}
                </span>
              </div>
              <div v-if="currentPhoto?.iso">
                <span class="opacity-60">ISO：</span>
                <span class="cursor-pointer hover:opacity-100" @click="filterByIso">
                  {{ currentPhoto.iso }}
                </span>
              </div>

              <!-- 第三行：尺寸和格式 -->
              <div v-if="currentPhoto?.width && currentPhoto?.height">
                <span class="opacity-60">尺寸：</span>{{ currentPhoto.width }} × {{ currentPhoto.height }}
              </div>
              <div v-if="currentPhoto?.format">
                <span class="opacity-60">格式：</span>{{ currentPhoto.format }}
              </div>

              <!-- 第四行：文件大小和质量评分 -->
              <div v-if="currentPhoto?.fileSize">
                <span class="opacity-60">文件大小：</span>{{ formatFileSize(currentPhoto.fileSize) }}
              </div>
              <div v-if="currentPhoto?.qualityScore">
                <span class="opacity-60">质量评分：</span>{{ currentPhoto.qualityScore?.toFixed(1) }}
              </div>

              <!-- 第五行：查看次数和点赞次数 -->
              <div v-if="currentPhoto?.viewCount">
                <span class="opacity-60">查看次数：</span>{{ currentPhoto.viewCount }}
              </div>
              <div v-if="currentPhoto?.likeCount">
                <span class="opacity-60">点赞次数：</span>{{ currentPhoto.likeCount }}
              </div>
            </div>
            <div v-if="currentPhoto?.focusX !== undefined && currentPhoto?.focusY !== undefined">
              <span class="opacity-60">聚焦位置：</span>
              <span class="inline-flex items-center gap-2">
                X: {{ currentPhoto.focusX.toFixed(1) }}%, Y: {{ currentPhoto.focusY.toFixed(1) }}%
                <button
                  class="text-xs px-2 py-0.5 bg-white/10 hover:bg-white/20 rounded"
                  @click="toggleFocusBox"
                >
                  {{ showFocusBox ? '隐藏框' : '显示框' }}
                </button>
              </span>
            </div>
            <div v-if="currentPhoto?.faces?.length">
              <span class="opacity-60">人脸框：</span>
              <button
                class="ml-2 text-xs px-2 py-0.5 bg-white/10 hover:bg-white/20 rounded"
                @click="toggleFaceBoxes"
              >
                {{ showFaceBoxes ? '隐藏' : '显示' }}
              </button>
            </div>
            <div v-if="currentPhoto?.tags?.length">
              <span class="opacity-60">标签：</span>
              <span class="inline-flex flex-wrap gap-2 mt-1">
                <span
                  v-for="t in currentPhoto.tags.slice(0, 8)"
                  :key="t.id"
                  class="px-2 py-1 bg-white/10 rounded cursor-pointer hover:bg-white/20"
                  @click.stop="openTag(t)"
                >
                  {{ t.name }}
                </span>
              </span>
            </div>
            <div v-if="currentPhoto?.faces?.length">
              <span class="opacity-60">人脸列表：</span>
              <div class="mt-2 grid grid-cols-2 gap-2">
                <div
                  v-for="(f, idx) in visibleFaceList"
                  :key="f.id || idx"
                  class="flex items-center gap-2 p-1 rounded transition-colors"
                  :class="f.isConfirmed && f.personId && f.personName ? 'cursor-pointer hover:bg-white/10' : ''"
                  @click.stop="f.isConfirmed && f.personId && f.personName ? openPersonByFace(f) : null"
                >
                  <div
                    class="w-10 h-10 rounded-full bg-gray-700 flex-shrink-0 border border-white/10"
                    :style="getFaceAvatarStyle(f)"
                    :title="getFaceTooltip(f)"
                  ></div>
                  <div class="text-xs truncate">
                    <div class="font-semibold" :class="f.isConfirmed ? 'text-green-300' : 'text-amber-200'">
                      {{ f.personName || '未命名' }}
                    </div>
                    <div class="text-[11px] text-gray-400 truncate">
                      置信度: {{ f.confidence !== undefined ? (f.confidence * 100).toFixed(1) + '%' : '-' }}
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <!-- 调色板 -->
            <div v-if="currentPhoto?.colorPalette?.length">
              <span class="opacity-60">调色板：</span>
              <!-- 当前显示的十六进制代码 -->
              <span class="font-mono text-xs mr-2">{{ displayedColor }}</span>
              <!-- 调色板颜色 -->
              <span
                v-for="(color, idx) in currentPhoto.colorPalette.slice(0, 6)"
                :key="idx"
                class="inline-block w-3 h-3 rounded border border-white/10 mr-1 cursor-pointer hover:border-white/30 hover:scale-110 transition-all duration-150"
                :style="{ backgroundColor: color }"
                :title="getColorTooltip(color)"
                @mouseenter="displayedColor = getColorHex(color)"
                @click="copyColorToClipboard(color)"
              ></span>
            </div>

            <!-- 调试信息 -->
            <div class="mt-4 p-2 bg-black/20 rounded text-xs max-w-sm">
              <div class="font-medium mb-1">触摸调试:</div>
              <div>最后事件: {{ debugInfo.lastEvent }}</div>
              <div>缩放: {{ debugInfo.scale?.toFixed(1) || '1.0' }}</div>
              <div>拖拽状态: {{ debugInfo.isDragging }} / {{ debugInfo.isDraggingImage }}</div>
              <div class="text-yellow-300">{{ debugInfo.touchStart }}</div>
              <div class="text-blue-300">{{ debugInfo.touchMove }}</div>
              <div class="text-green-300">{{ debugInfo.touchEnd }}</div>

              <!-- 调试日志 -->
              <div class="mt-2">
                <div class="font-medium mb-1 text-purple-300">调试日志:</div>
                <div class="max-h-32 overflow-y-auto bg-black/30 p-1 rounded text-[10px] leading-tight">
                  <div v-for="log in debugInfo.logs" :key="log" class="text-gray-300">
                    {{ log }}
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>

      </div>

      <!-- 底部缩略图横排 -->
      <transition name="fade">
        <div
          v-if="!isFullscreen"
          class="bg-black/80 border-t border-white/10 overflow-x-auto overflow-y-hidden select-none relative"
          :style="{ height: Math.max(thumbHeight, thumbSize + 18) + 'px' }"
        >
          <div
            class="absolute inset-x-0 top-0 h-3 cursor-ns-resize border-b border-white/20 bg-black/35 z-20"
            @mousedown.prevent="startDrag"
            title="拖动调整高度"
          ></div>
          <div class="h-1"></div>
          <div
            class="flex items-center gap-2 px-3 py-1.5 min-w-max"
            ref="thumbContainer"
          >
            <div
              v-for="(p, idx) in photos"
              :key="p.id"
              class="relative flex-shrink-0 cursor-pointer border transition-all duration-150"
              :style="{ width: thumbSize + 'px', height: thumbSize + 'px' }"
              :class="idx === currentIndex ? 'border-white scale-[1.02]' : 'border-transparent opacity-80 hover:opacity-100'"
              @click="jump(idx)"
              :ref="el => (thumbItems[idx] = el)"
            >
              <img
                :src="getThumbUrl(p)"
                :alt="p.filename"
                class="w-full h-full object-cover rounded-sm"
              />
              <div
                v-if="idx === currentIndex"
                class="pointer-events-none absolute inset-0 ring-2 ring-white/90 rounded-sm"
              ></div>
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
import type { Photo } from '@/stores/photo'

const props = defineProps<{
  photos: Photo[]
  visible: boolean
  startIndex?: number
  /** 是否在打开时自动显示人脸框（例如人物管理中查看照片） */
  autoShowFaces?: boolean
  /** 可选：从缩略图平滑放大时的初始矩形（相对于视口） */
  originRect?: { top: number; left: number; width: number; height: number } | null
  /** 可选：外部传入的打开选项（例如指定高亮的 faceId / clusterId） */
  openOptions?: { highlightedFaceId?: number; highlightedClusterId?: number; highlightedPersonId?: number; highlightedFaceIds?: number[]; preferredFaceId?: number } | null
}>()

const emit = defineEmits<{
  (e: 'update:visible', val: boolean): void
  (e: 'viewer-index-change', payload: { index: number; photoId?: number; faceIds?: number[] }): void
}>()

const currentIndex = ref(0)
const infoCollapsed = ref(true)
const infoTransparent = ref(false) // 控制信息栏透明度
const modalRoot = ref<HTMLElement | null>(null)
const touchStartX = ref(0)
const touchCurrentX = ref(0)
const thumbContainer = ref<HTMLElement | null>(null)
const thumbItems = ref<any[]>([])
const mainImageKey = ref(0)
const thumbHeight = ref<number>(parseInt(localStorage.getItem('pe-thumb-height') || '112', 10) || 112)
const dragging = ref(false)
const dragStartY = ref(0)
const dragStartHeight = ref(0)
const thumbSize = computed(() => Math.max(24, clampThumbHeight(thumbHeight.value - 24)))
const isFullscreen = ref(false)
const showFocusBox = ref(false)
const showFaceBoxes = ref(false)

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
const mainImage = ref<HTMLImageElement | null>(null)
const imageContainer = ref<HTMLElement | null>(null)
const imageWrapper = ref<HTMLElement | null>(null)
const imageSize = ref({ width: 0, height: 0 })
const imageLoaded = ref(false)

// 调色板当前显示的颜色值
const displayedColor = ref('')

// 缩略图到查看器的开场动画
const isOpeningFromThumb = ref(false)
const openAnimStyle = ref<Record<string, string>>({})

// 缩放相关状态
const scale = ref(1)
const translateX = ref(0)
const translateY = ref(0)
const isDragging = ref(false)
const dragStart = ref({ x: 0, y: 0, translateX: 0, translateY: 0 })
const lastTouchDistance = ref(0)
const touchCenter = ref({ x: 0, y: 0 })
const isPinching = ref(false)

// 触摸滑动相关状态
const touchStartY = ref(0)
const touchCurrentY = ref(0)
const isDraggingImage = ref(false)
const dragStartTime = ref(0)
const dragVelocity = ref(0) // 滑动速度

// 防止拖拽后意外关闭的状态
const wasDragging = ref(false)

// 图片切换动画相关状态
const imageTransitionOffset = ref(0) // 图片切换时的水平偏移
const isImageTransitioning = ref(false) // 是否正在进行图片切换动画
const pendingTransitionDirection = ref<'prev' | 'next' | null>(null) // 等待执行的切换方向
const transitioningPhoto = ref<Photo | null>(null) // 正在过渡的图片（进入的图片）
const transitionDirection = ref<'left' | 'right' | null>(null) // 过渡方向

// 调试信息
const debugInfo = ref({
  touchStart: '',
  touchMove: '',
  touchEnd: '',
  isDragging: false,
  isDraggingImage: false,
  scale: 1,
  lastEvent: '',
  logs: [] as string[]
})

// 调试日志函数
const addDebugLog = (message: string) => {
  const timestamp = new Date().toLocaleTimeString()
  const logEntry = `[${timestamp}] ${message}`
  debugInfo.value.logs.unshift(logEntry)
  // 只保留最近15条日志
  if (debugInfo.value.logs.length > 15) {
    debugInfo.value.logs = debugInfo.value.logs.slice(0, 15)
  }
  console.log(`[${debugInfo.value.logs.length}] ${message}`) // 同时输出到控制台，显示日志数量
}

const STORAGE_KEY = 'pe-info-transparent'
const FOCUS_BOX_KEY = 'pe-focus-box-visible'
const FACE_BOXES_KEY = 'pe-face-boxes-visible'
const THUMB_KEY = 'pe-thumb-height'

// 初始化状态
infoCollapsed.value = localStorage.getItem('pe-info-collapsed') === '1'
infoTransparent.value = localStorage.getItem(STORAGE_KEY) === '1'

// 使用ref来确保响应式更新
const currentPhotoRef = ref<Photo | null>(null)

// 监听currentIndex变化，更新currentPhotoRef
watch(
  () => currentIndex.value,
  (newIndex) => {
    currentPhotoRef.value = props.photos?.[newIndex] || null
  },
  { immediate: true }
)

// 同时监听props.photos变化
watch(
  () => props.photos,
  (newPhotos) => {
    if (newPhotos) {
      currentPhotoRef.value = newPhotos[currentIndex.value] || null
    }
  },
  { immediate: true }
)

const currentPhoto = computed(() => currentPhotoRef.value)

// 当照片变化时，初始化调色板显示的颜色值
watch(currentPhoto, (newPhoto) => {
  if (newPhoto?.dominantColor) {
    displayedColor.value = newPhoto.dominantColor
  } else if (newPhoto?.colorPalette?.length) {
    displayedColor.value = getColorHex(newPhoto.colorPalette[0])
  } else {
    displayedColor.value = ''
  }
}, { immediate: true })

// 获取当前照片的相册路径
const currentAlbumPath = computed(() => {
  const photo = currentPhoto.value
  const photoId = photo?.id
  const originalPath = photo?.originalPath

  // 检查是否有照片数据
  if (!photo) {
    console.warn(`[路径计算] 照片数据不存在 - 照片ID: ${photoId}`)
    return null
  }

  // 检查是否有originalPath
  if (!originalPath) {
    console.warn(`[路径计算] originalPath为空 - 照片ID: ${photo?.id}, 文件名: ${photo?.filename}`)
    return null
  }

  // 检查originalPath是否为字符串
  if (typeof originalPath !== 'string') {
    console.warn(`[路径计算] originalPath不是字符串类型 - 照片ID: ${photoId}, 类型: ${typeof originalPath}, 值:`, originalPath)
    return null
  }

  try {
    // 从originalPath中提取相册路径
    // originalPath格式: /data/photos/分类/相册名/文件名.jpg
    // 我们需要提取: 分类/相册名
    const pathParts = originalPath.split('/').filter(p => p.length > 0)

    // 处理不同的路径格式
    let category = ''
    let album = ''

    if (pathParts.length >= 5 && pathParts[0] === 'data' && pathParts[1] === 'photos') {
      // 标准格式：/data/photos/分类/相册名/文件名.jpg
      category = pathParts[2]
      album = pathParts[3]
      console.log(`[路径计算] 使用标准格式解析 - 照片ID: ${photoId}, 路径: ${originalPath}`)
    } else if (pathParts.length >= 4 && pathParts[0] === 'data' && pathParts[1] === 'photos') {
      // 简化的标准格式：/data/photos/相册名/文件名.jpg（没有分类）
      category = '未分类'
      album = pathParts[2]
      console.log(`[路径计算] 使用简化标准格式解析 - 照片ID: ${photoId}, 路径: ${originalPath}`)
    } else if (pathParts.length >= 3) {
      // 直接格式：/分类/相册名/文件名.jpg
      category = pathParts[0]
      album = pathParts[1]
      console.log(`[路径计算] 使用直接格式解析 - 照片ID: ${photoId}, 路径: ${originalPath}`)
    } else if (pathParts.length >= 2) {
      // 最简格式：/相册名/文件名.jpg（没有分类）
      category = '未分类'
      album = pathParts[0]
      console.log(`[路径计算] 使用最简格式解析 - 照片ID: ${photoId}, 路径: ${originalPath}`)
    } else {
      console.warn(`[路径计算] 路径分段太少，无法提取相册信息 - 照片ID: ${photoId}, 文件名: ${photo?.filename}, 路径: ${originalPath}, 分段数: ${pathParts.length}, 分段:`, pathParts)
      return null
    }

    // 检查提取的结果是否有效
    if (!category || !album) {
      console.warn(`[路径计算] 分类或相册名称为空 - 照片ID: ${photoId}, 文件名: ${photo?.filename}, 路径: ${originalPath}, 分类: "${category}", 相册: "${album}"`)
      return null
    }

    const result = `${category}/${album}`
    console.log(`[路径计算] 成功提取路径 - 照片ID: ${photoId}, 结果: ${result}`)
    return result

    // 路径格式不符合预期
    console.warn(`[路径计算] 路径格式不符合预期 - 照片ID: ${photoId}, 文件名: ${photo?.filename}, 路径: ${originalPath}, 分段数: ${pathParts.length}, 分段:`, pathParts)
    return null

  } catch (error) {
    console.error(`[路径计算] 提取相册路径时发生异常 - 照片ID: ${photoId}, 文件名: ${photo?.filename}, 路径: ${originalPath}, 错误:`, error)
    return null
  }
})

// 获取过渡图片的变换样式
const getTransitionImageTransformStyle = () => {
  if (!transitioningPhoto.value || !transitionDirection.value) return {}

  const direction = transitionDirection.value
  const container = imageContainer.value
  if (!container) return {}

  // 1:1 跟手效果：直接使用像素偏移，不转换为百分比
  const containerWidth = container.clientWidth
  const pixelOffset = imageTransitionOffset.value

  // 过渡图片从边缘滑入覆盖当前图片
  // next方向（右滑到下一张）：从右边滑入 (containerWidth -> 0)
  // prev方向（左滑到上一张）：从左边滑入 (-containerWidth -> 0)
  let transitionOffset = 0

  if (direction === 'right') {
    // 下一张图片从右边进入，最终覆盖当前图片
    transitionOffset = containerWidth - Math.abs(pixelOffset)
  } else {
    // 上一张图片从左边进入，最终覆盖当前图片
    transitionOffset = -containerWidth + Math.abs(pixelOffset)
  }

  return {
    transform: `translateX(${transitionOffset}px)`,
    transition: 'none' // 动画由JavaScript控制
  }
}

const router = useRouter()

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

// 打开相册
const openAlbum = () => {
  if (!currentPhoto.value) return
  const route = router.resolve({ path: `/album/${currentPhoto.value.albumId}` })
  window.open(route.href, '_blank')
}

// 根据拍摄时间筛选
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

// 根据相机筛选
const filterByCamera = () => {
  if (!currentPhoto.value?.cameraMake && !currentPhoto.value?.cameraModel) return
  const cameraModel = currentPhoto.value.cameraModel || ''
  const route = router.resolve({ path: '/random', query: { filters: JSON.stringify({ cameraModel }) } })
  window.open(route.href, '_blank')
}

// 根据镜头筛选
const filterByLens = () => {
  if (!currentPhoto.value?.lensModel) return
  const lensModel = currentPhoto.value.lensModel
  const route = router.resolve({ path: '/random', query: { filters: JSON.stringify({ lensModel }) } })
  window.open(route.href, '_blank')
}

// 根据焦距筛选
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

// 根据光圈筛选
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

// 根据快门速度筛选
const filterByShutterSpeed = () => {
  if (!currentPhoto.value?.shutterSpeed) return
  // 需要将快门速度转换为数值（秒）
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

// 根据ISO筛选
const filterByIso = () => {
  if (!currentPhoto.value?.iso) return
  const filters = {
    minIso: currentPhoto.value.iso,
    maxIso: currentPhoto.value.iso
  }
  const route = router.resolve({ path: '/random', query: { filters: JSON.stringify(filters) } })
  window.open(route.href, '_blank')
}

// 复制颜色值到剪贴板
const copyColorToClipboard = async (color: string) => {
  try {
    // 确保是十六进制格式
    const hexColor = color.startsWith('#') ? color : `#${color}`
    await navigator.clipboard.writeText(hexColor)
    // 可以添加一个简单的反馈提示
    console.log(`颜色 ${hexColor} 已复制到剪贴板`)
  } catch (err) {
    console.error('复制失败:', err)
    // 降级方案
    const textArea = document.createElement('textarea')
    const hexColor = color.startsWith('#') ? color : `#${color}`
    textArea.value = hexColor
    document.body.appendChild(textArea)
    textArea.select()
    document.execCommand('copy')
    document.body.removeChild(textArea)
  }
}

// 获取颜色的十六进制表示
const getColorHex = (color: string) => {
  if (!color) return ''
  return color.startsWith('#') ? color : `#${color}`
}

// 生成多种格式的颜色值显示
const getColorTooltip = (color: string) => {
  if (!color) return ''

  // 确保是有效的颜色值
  const hexColor = color.startsWith('#') ? color : `#${color}`

  try {
    // 创建临时元素来转换颜色格式
    const tempDiv = document.createElement('div')
    tempDiv.style.color = hexColor
    document.body.appendChild(tempDiv)

    const computedStyle = window.getComputedStyle(tempDiv)
    const rgbColor = computedStyle.color

    document.body.removeChild(tempDiv)

    // 解析RGB值
    const rgbMatch = rgbColor.match(/rgb\((\d+),\s*(\d+),\s*(\d+)\)/)
    if (rgbMatch) {
      const r = parseInt(rgbMatch[1])
      const g = parseInt(rgbMatch[2])
      const b = parseInt(rgbMatch[3])

      return `HEX: ${hexColor}\nRGB: ${rgbColor}\nHSL: hsl(${rgbToHsl(r, g, b)})`
    }
  } catch (e) {
    // 如果转换失败，只显示HEX
  }

  return `HEX: ${hexColor}`
}

// RGB转HSL的辅助函数
const rgbToHsl = (r: number, g: number, b: number): string => {
  r /= 255
  g /= 255
  b /= 255

  const max = Math.max(r, g, b)
  const min = Math.min(r, g, b)
  let h = 0
  let s = 0
  const l = (max + min) / 2

  if (max !== min) {
    const d = max - min
    s = l > 0.5 ? d / (2 - max - min) : d / (max + min)

    switch (max) {
      case r: h = (g - b) / d + (g < b ? 6 : 0); break
      case g: h = (b - r) / d + 2; break
      case b: h = (r - g) / d + 4; break
    }
    h /= 6
  }

  return `${Math.round(h * 360)}°, ${Math.round(s * 100)}%, ${Math.round(l * 100)}%`
}

watch(
  () => props.visible,
  (val) => {
    if (val) {
      // 聚焦以接收键盘
      requestAnimationFrame(() => modalRoot.value?.focus())
      // 初始化索引
      if (typeof props.startIndex === 'number') {
        currentIndex.value = Math.min(Math.max(props.startIndex, 0), props.photos.length - 1)
      }
      scrollThumbIntoView()

      // 如果开启了自动显示人脸框，并且当前照片有人脸，且用户没有手动操作过，则默认打开人脸框
      if (props.autoShowFaces && currentPhoto.value?.faces?.length && !userInteractedWithFaceBoxes.value) {
        showFaceBoxes.value = true
      }
      // 如果用户已经手动操作过人脸框设置，则使用保存的状态（已在onMounted中恢复）

      // 如果用户已经手动操作过焦点框设置，则恢复用户的设置
      if (userInteractedWithFocusBox.value) {
        // 用户已经操作过，使用保存的状态
        // 状态已经在onMounted中从localStorage恢复了
      }
      // 首次使用时保持默认隐藏，不自动显示

      // 如果提供了缩略图矩形信息，则计算从缩略图平滑放大的动画参数
      if (props.originRect) {
        nextTick(() => {
          if (!imageContainer.value || !mainImage.value) return
          const targetRect = mainImage.value.getBoundingClientRect()
          const origin = props.originRect!

          // 以容器中心为坐标系，计算缩略图中心与目标中心的偏移
          const originCenterX = origin.left + origin.width / 2
          const originCenterY = origin.top + origin.height / 2
          const targetCenterX = targetRect.left + targetRect.width / 2
          const targetCenterY = targetRect.top + targetRect.height / 2

          const tx = originCenterX - targetCenterX
          const ty = originCenterY - targetCenterY

          // 使用宽度比作为初始缩放近似值，并且始终从「略小」放大到 1，避免从大缩小的观感
          const ratio = origin.width / Math.max(targetRect.width, 1)
          const scaleApprox = Math.max(0.4, Math.min(1, ratio))

          openAnimStyle.value = {
            '--pe-open-tx': `${tx}px`,
            '--pe-open-ty': `${ty}px`,
            '--pe-open-scale': String(scaleApprox)
          }
          isOpeningFromThumb.value = true

          // 动画结束后重置标记，仅保留最终状态
          setTimeout(() => {
            isOpeningFromThumb.value = false
            openAnimStyle.value = {}
          }, 260)
        })
      }
    }
  },
  { immediate: true }
)

onMounted(() => {
  // 确保在DOM完全渲染后设置状态
  nextTick(() => {
    // 初始化框体状态（焦点框、人脸框）
    initializeBoxStates()
  })

  window.addEventListener('keydown', onKeydown)
  window.addEventListener('resize', onImageLoad)
  // 监听 fullscreen 变化以便在进入/退出全屏时重新计算图片尺寸
  document.addEventListener('fullscreenchange', onFullscreenChange)
})

// 监听信息栏状态变化，确保UI立即更新
watch(infoCollapsed, (newValue) => {
  // 确保状态变化后UI能立即响应
  nextTick(() => {
    // 状态更新后的处理逻辑
  })
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', onKeydown)
  window.removeEventListener('resize', onImageLoad)
  document.removeEventListener('fullscreenchange', onFullscreenChange)
})

// 切换信息栏显示/隐藏
const toggleInfo = () => {
  const wasCollapsed = infoCollapsed.value
  infoCollapsed.value = !infoCollapsed.value
  localStorage.setItem('pe-info-collapsed', infoCollapsed.value ? '1' : '0')
}

// 切换信息栏透明度
const toggleInfoTransparency = () => {
  infoTransparent.value = !infoTransparent.value
  localStorage.setItem(STORAGE_KEY, infoTransparent.value ? '1' : '0')
}

const close = () => {
  emit('update:visible', false)
}

const onBackdropClick = (event: MouseEvent) => {
  console.log('🎯 onBackdropClick triggered, wasDragging:', wasDragging.value, 'event.type:', event.type, 'target:', event.target)

  // 如果刚刚进行过拖拽操作，不关闭PhotoViewer
  if (wasDragging.value) {
    console.log('🚫 Blocking close due to wasDragging flag')
    return
  }

  // Close if clicked on backdrop or main content area (but not on interactive elements)
  const target = event.target as HTMLElement
  const currentTarget = event.currentTarget as HTMLElement

  // Always close if clicked on the backdrop itself
  if (target === currentTarget) {
    close()
    return
  }

  // Also close if clicked on main content area (but not on the image or info sidebar)
  if (target.closest('.flex-1.flex.overflow-hidden') && !target.closest('img, button, svg') && !target.closest('.absolute.right-0.w-80')) {
    close()
  }
}

const prev = () => {
  if (!props.photos?.length) return

  // 如果正在触摸滑动中，设置等待切换方向
  if (isDragging.value && scale.value === 1) {
    pendingTransitionDirection.value = 'prev'
    return
  }

  // 如果正在动画中，强制中断并立即切换
  if (isImageTransitioning.value) {
    // 重置动画状态
    isImageTransitioning.value = false
    imageTransitionOffset.value = 0
    transitioningPhoto.value = null
    transitionDirection.value = null
  }

  animateImageTransition('prev')
}

const next = () => {
  if (!props.photos?.length) return

  // 如果正在触摸滑动中，设置等待切换方向
  if (isDragging.value && scale.value === 1) {
    pendingTransitionDirection.value = 'next'
    return
  }

  // 如果正在动画中，强制中断并立即切换
  if (isImageTransitioning.value) {
    // 重置动画状态
    isImageTransitioning.value = false
    imageTransitionOffset.value = 0
    transitioningPhoto.value = null
    transitionDirection.value = null
  }

  animateImageTransition('next')
}

// 带动画的图片切换
const animateImageTransition = (direction: 'prev' | 'next') => {
  animateImageTransitionWithVelocity(direction, 0)
}

// 带速度的图片切换动画
const animateImageTransitionWithVelocity = (direction: 'prev' | 'next', velocity: number = 0) => {
  if (isImageTransitioning.value) return

  isImageTransitioning.value = true

  // 记录当前的图片索引（动画过程中保持不变）
  const currentIndexDuringAnimation = currentIndex.value

  // 计算下一张图片的索引
  const nextIndex = direction === 'next'
    ? (currentIndexDuringAnimation + 1) % props.photos.length
    : (currentIndexDuringAnimation - 1 + props.photos.length) % props.photos.length

  // 设置过渡图片（新图片）和方向
  transitioningPhoto.value = props.photos[nextIndex]
  transitionDirection.value = direction === 'next' ? 'right' : 'left'

  // 根据滑动速度调整动画时长
  const baseDuration = 300 // 基础动画时长
  const velocityFactor = Math.min(Math.abs(velocity) * 200, 100) // 速度影响最大100ms
  const duration = Math.max(180, baseDuration - velocityFactor) // 最短180ms

  const startTime = Date.now()

  // 动画开始时的偏移（应该是从手指松开时的位置开始）
  const startOffset = Math.abs(imageTransitionOffset.value)

  const animate = () => {
    const elapsed = Date.now() - startTime
    const progress = Math.min(elapsed / duration, 1)

    // 使用ease-out缓动：由快到慢，更自然
    const easeProgress = 1 - Math.pow(1 - progress, 3)

    // 从当前偏移位置过渡到0（完全覆盖）
    imageTransitionOffset.value = startOffset * (1 - easeProgress)

    if (progress < 1) {
      requestAnimationFrame(animate)
    } else {
      // 动画完成：过渡图片完全覆盖了当前图片
      // 现在切换到新的图片索引，移除过渡图片
      currentIndex.value = nextIndex

      // 重置状态
      imageTransitionOffset.value = 0
      transitioningPhoto.value = null
      transitionDirection.value = null
      isImageTransitioning.value = false
    }
  }

  animate()
}

const jump = (idx: number) => {
  currentIndex.value = idx
  // 切换照片时保持用户的全局偏好设置，不重置交互状态
}

const clampThumbHeight = (val: number) => Math.min(260, Math.max(60, val))

const toggleFocusBox = () => {
  userInteractedWithFocusBox.value = true
  showFocusBox.value = !showFocusBox.value
  localStorage.setItem(FOCUS_BOX_KEY, showFocusBox.value ? '1' : '0')
}

const toggleFaceBoxes = () => {
  userInteractedWithFaceBoxes.value = true
  showFaceBoxes.value = !showFaceBoxes.value
  localStorage.setItem(FACE_BOXES_KEY, showFaceBoxes.value ? '1' : '0')
}

const startDrag = (e: MouseEvent) => {
  dragging.value = true
  dragStartY.value = e.clientY
  dragStartHeight.value = thumbHeight.value
  window.addEventListener('mousemove', onDrag)
  window.addEventListener('mouseup', stopDrag)
}

const onDrag = (e: MouseEvent) => {
  if (!dragging.value) return
  const delta = dragStartY.value - e.clientY
  thumbHeight.value = clampThumbHeight(dragStartHeight.value + delta)
}

const stopDrag = () => {
  if (!dragging.value) return
  dragging.value = false
  thumbHeight.value = clampThumbHeight(thumbHeight.value)
  localStorage.setItem(THUMB_KEY, String(thumbHeight.value))
  window.removeEventListener('mousemove', onDrag)
  window.removeEventListener('mouseup', stopDrag)
}

const toggleFullscreen = async () => {
  const el = modalRoot.value
  if (!el) return
  try {
    if (!document.fullscreenElement) {
      await el.requestFullscreen()
      isFullscreen.value = true
      // 等待一帧，确保渲染完成后重新计算图片尺寸
      await nextTick()
      onImageLoad()
      resetZoom()
    } else {
      await document.exitFullscreen()
      isFullscreen.value = false
    }
  } catch (e) {
    // ignore fullscreen errors
    isFullscreen.value = !!document.fullscreenElement
  }
}

watch(
  () => props.visible,
  (val) => {
    if (!val && document.fullscreenElement) {
      document.exitFullscreen().catch(() => {})
      isFullscreen.value = false
    }
  }
)

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

// fullscreen change handler: 更新状态并在需要时重新计算图片布局
const onFullscreenChange = async () => {
  isFullscreen.value = !!document.fullscreenElement
  // 等待 DOM 更新后重新计算图片尺寸/位置
  await nextTick()
  onImageLoad()
  resetZoom()
  // 强制重新渲染图片元素以清除任何残留样式（少量延迟以让浏览器完成布局变化）
  setTimeout(() => {
    mainImageKey.value = (mainImageKey.value || 0) + 1
  }, 50)
}

// 触控板/鼠标滚轮缩放
const onWheelZoom = (e: WheelEvent) => {
  // 如果按住 Ctrl/Cmd 键，进行缩放
  if (e.ctrlKey || e.metaKey) {
    e.preventDefault()
    const delta = e.deltaY > 0 ? -0.1 : 0.1
    zoomAtPoint(e.clientX, e.clientY, delta)
  } else {
    // 否则用于切换图片（原有逻辑）
    if (Math.abs(e.deltaY) > Math.abs(e.deltaX)) {
      if (e.deltaY > 0) next()
      else prev()
    } else {
      if (e.deltaX > 0) next()
      else prev()
    }
  }
}

// 触摸缩放和拖拽
const onTouchStartZoom = (e: TouchEvent) => {
  const targetElement = (e.target as HTMLElement)?.tagName || 'unknown'
  console.log('🔥 TOUCH START FIRED!', e.touches.length, 'touches, target:', targetElement)
  addDebugLog(`Touch start: ${e.touches.length} touches (来自: ${targetElement})`)
  e.preventDefault() // 总是阻止默认行为，确保触摸事件被处理

  // 更新调试信息
  debugInfo.value.touchStart = `开始: ${e.touches.length}个触摸点`
  debugInfo.value.lastEvent = 'touchstart'

  addDebugLog(`检查条件: e.touches.length === 1 ? ${e.touches.length === 1}`)
  if (e.touches.length === 1) {
    addDebugLog('✅ 条件满足，进入单指触摸分支')
    addDebugLog('准备获取触摸信息')
    // 单指：准备拖拽或图片切换
    const touch = e.touches[0]
    addDebugLog(`触摸坐标: (${touch.clientX}, ${touch.clientY})`)
    touchStartX.value = touch.clientX
    touchStartY.value = touch.clientY
    touchCurrentX.value = touch.clientX
    touchCurrentY.value = touch.clientY
    dragStartTime.value = Date.now()
    dragVelocity.value = 0
    addDebugLog('基础变量设置完成')

    addDebugLog('准备设置 isDragging = true')
    isDragging.value = true
    wasDragging.value = false // 重置拖拽标志
    console.log('🔄 Touch start: wasDragging reset to false')
    addDebugLog(`✅ isDragging 设置完成: ${isDragging.value}`)
    // 立即验证设置是否成功
    setTimeout(() => {
      addDebugLog(`⏰ 延迟检查 isDragging: ${isDragging.value}`)
    }, 0)
    dragStart.value = {
      x: touch.clientX,
      y: touch.clientY,
      translateX: translateX.value,
      translateY: translateY.value
    }

    // 如果未缩放，准备图片滑动切换
    addDebugLog(`检查缩放条件: scale.value === 1 ? ${scale.value === 1} (当前值: ${scale.value})`)
    if (scale.value === 1) {
      addDebugLog('✅ 缩放条件满足，准备设置 isDraggingImage = true')
      isDraggingImage.value = true
      imageTransitionOffset.value = 0
      transitioningPhoto.value = null
      transitionDirection.value = null
      pendingTransitionDirection.value = null
      addDebugLog(`✅ isDraggingImage 设置完成: ${isDraggingImage.value}`)
    } else {
      addDebugLog('❌ 缩放条件不满足，不设置图片拖拽')
      isDraggingImage.value = false
    }
  } else if (e.touches.length === 2) {
    // 双指：准备缩放
    isDraggingImage.value = false
    isPinching.value = true
    isDragging.value = false
    const touch1 = e.touches[0]
    const touch2 = e.touches[1]
    lastTouchDistance.value = getTouchDistance(touch1, touch2)
    touchCenter.value = {
      x: (touch1.clientX + touch2.clientX) / 2,
      y: (touch1.clientY + touch2.clientY) / 2
    }
  }
}

const onTouchMoveZoom = (e: TouchEvent) => {
  addDebugLog(`Touch move 开始: 触摸点=${e.touches.length}, 当前isDragging=${isDragging.value}, 当前isDraggingImage=${isDraggingImage.value}`)
  e.preventDefault() // 总是阻止默认行为

  // 一旦触摸移动，就标记为拖拽操作
  wasDragging.value = true
  console.log('👆 Touch move: wasDragging set to true')

  // 更新调试信息
  debugInfo.value.touchMove = `移动: ${e.touches.length}个触摸点, 缩放:${scale.value.toFixed(1)}, 拖拽:${isDragging.value}, 图片拖拽:${isDraggingImage.value}`
  debugInfo.value.lastEvent = 'touchmove'
  debugInfo.value.isDragging = isDragging.value
  debugInfo.value.isDraggingImage = isDraggingImage.value
  debugInfo.value.scale = scale.value

  addDebugLog(`Touch move - 触摸点: ${e.touches.length}, isDragging: ${isDragging.value}, scale: ${scale.value}`)

  if (e.touches.length === 1 && isDragging.value) {
    addDebugLog('进入单指拖拽分支')
    const touch = e.touches[0]
    touchCurrentX.value = touch.clientX
    touchCurrentY.value = touch.clientY

    if (scale.value > 1) {
      // 单指拖拽（仅在缩放后）
      const dx = touch.clientX - dragStart.value.x
      const dy = touch.clientY - dragStart.value.y
      addDebugLog(`缩放拖拽: dx=${dx.toFixed(1)}, dy=${dy.toFixed(1)}, translateX=${translateX.value.toFixed(1)} -> ${(dragStart.value.translateX + dx).toFixed(1)}`)
      translateX.value = dragStart.value.translateX + dx
      translateY.value = dragStart.value.translateY + dy
      constrainTranslation()
    } else if (isDraggingImage.value) {
      // 未缩放时，图片跟随手指滑动切换
      const dx = touch.clientX - touchStartX.value
      const dy = touch.clientY - touchStartY.value
      addDebugLog(`图片切换: dx=${dx.toFixed(1)}, dy=${dy.toFixed(1)}, touchX=${touch.clientX.toFixed(1)}, startX=${touchStartX.value.toFixed(1)}`)

      // 垂直滑动距离太大时，不触发水平切换
      if (Math.abs(dy) > Math.abs(dx) * 0.5) {
        addDebugLog(`垂直滑动过大，跳过处理: |${dy.toFixed(1)}| > |${dx.toFixed(1)}| * 0.5 = ${(Math.abs(dx) * 0.5).toFixed(1)}`)
        return
      }
      addDebugLog(`通过垂直检查，继续处理`)

      const container = imageContainer.value
      if (container) {
        const containerWidth = container.clientWidth
        addDebugLog(`容器宽度: ${containerWidth}`)

        // 根据滑动方向立即设置过渡图片
        addDebugLog(`检查滑动距离: |${dx.toFixed(1)}| > 2 ? ${Math.abs(dx) > 2}`)
        if (Math.abs(dx) > 2) { // 更小的移动就开始响应
          addDebugLog(`滑动距离足够，开始设置过渡图片`)
          const direction = dx > 0 ? 'prev' : 'next'
          const nextIndex = direction === 'next'
            ? (currentIndex.value + 1) % props.photos.length
            : (currentIndex.value - 1 + props.photos.length) % props.photos.length

          // 如果还没有设置过渡图片，设置它
          if (!transitioningPhoto.value) {
            transitioningPhoto.value = props.photos[nextIndex]
            transitionDirection.value = direction === 'next' ? 'right' : 'left'
            pendingTransitionDirection.value = direction
          }

          // 计算滑动速度（用于后续动画）
          const now = Date.now()
          const timeDelta = now - dragStartTime.value
          if (timeDelta > 0) {
            dragVelocity.value = dx / timeDelta
          }

          // 1:1 移动，不使用阻尼效果
          const moveDistance = dx
          addDebugLog(`1:1移动距离: dx=${dx.toFixed(1)}, moveDistance=${moveDistance.toFixed(1)}`)

          imageTransitionOffset.value = moveDistance
          addDebugLog(`设置偏移: ${moveDistance.toFixed(1)}`)
        }
      }
    }
  } else if (e.touches.length === 2 && isPinching.value) {
    // 双指缩放
    e.preventDefault()
    const touch1 = e.touches[0]
    const touch2 = e.touches[1]
    const distance = getTouchDistance(touch1, touch2)
    const scaleDelta = distance / lastTouchDistance.value

    // 更新缩放中心
    touchCenter.value = {
      x: (touch1.clientX + touch2.clientX) / 2,
      y: (touch1.clientY + touch2.clientY) / 2
    }

    zoomAtPoint(touchCenter.value.x, touchCenter.value.y, (scaleDelta - 1) * scale.value)
    lastTouchDistance.value = distance
  }
}

const onTouchEndZoom = (e: TouchEvent) => {
  e.preventDefault() // 总是阻止默认行为

  // 更新调试信息（在重置之前）
  debugInfo.value.touchEnd = `结束: ${e.touches.length}个触摸点`
  debugInfo.value.lastEvent = 'touchend'

  if (e.touches.length === 0) {
    // 所有手指抬起
    if (isDragging.value && scale.value === 1 && isDraggingImage.value) {
      // 处理图片滑动切换
      const container = imageContainer.value
      if (container && transitioningPhoto.value) {
        const containerWidth = container.clientWidth
        const dx = touchCurrentX.value - touchStartX.value
        const dy = touchCurrentY.value - touchStartY.value

        // 垂直滑动太大时，不切换
        if (Math.abs(dy) > Math.abs(dx) * 0.5) {
          animateImageReset()
        } else {
          // 计算是否应该切换：基于距离和速度
          const distanceThreshold = containerWidth * 0.3 // 30%的容器宽度
          const velocityThreshold = 0.5 // 最小滑动速度（像素/毫秒）

          const shouldSwitch = Math.abs(dx) > distanceThreshold ||
                              (Math.abs(dragVelocity.value) > velocityThreshold && Math.abs(dx) > containerWidth * 0.1)

          if (shouldSwitch) {
            // 完成切换
            const direction = dx > 0 ? 'prev' : 'next'
            animateImageTransitionWithVelocity(direction, dragVelocity.value)
          } else {
            // 回到原位
            animateImageReset()
          }
        }
      }
    }

    // 重置状态
    isDragging.value = false
    isDraggingImage.value = false
    isPinching.value = false
    dragVelocity.value = 0

    // 延迟重置wasDragging，给异步click事件缓冲时间
    setTimeout(() => {
      if (!isDragging.value) { // 确保没有新的拖拽开始
        wasDragging.value = false
        console.log('🔄 Touch end: wasDragging reset to false after delay')
      }
    }, 300)

    // 更新调试信息（重置后）
    debugInfo.value.isDragging = false
    debugInfo.value.isDraggingImage = false
  } else if (e.touches.length === 1) {
    // 从双指变为单指
    isPinching.value = false
    isDragging.value = true
    dragStart.value = {
      x: e.touches[0].clientX,
      y: e.touches[0].clientY,
      translateX: translateX.value,
      translateY: translateY.value
    }
  }
}

// 图片重置动画（回到原位）
const animateImageReset = () => {
  if (imageTransitionOffset.value === 0) return

  const startOffset = Math.abs(imageTransitionOffset.value)
  const duration = 250 // 稍微快一点的重置动画
  const startTime = Date.now()

  const animate = () => {
    const elapsed = Date.now() - startTime
    const progress = Math.min(elapsed / duration, 1)

    // 使用ease-out缓动
    const easeProgress = 1 - Math.pow(1 - progress, 3)

    imageTransitionOffset.value = startOffset * (1 - easeProgress)

    if (progress < 1) {
      requestAnimationFrame(animate)
    } else {
      // 重置完成
      imageTransitionOffset.value = 0
      transitioningPhoto.value = null
      transitionDirection.value = null
      pendingTransitionDirection.value = null
    }
  }

  animate()
}

const getTouchDistance = (touch1: Touch, touch2: Touch) => {
  const dx = touch2.clientX - touch1.clientX
  const dy = touch2.clientY - touch1.clientY
  return Math.sqrt(dx * dx + dy * dy)
}

// 鼠标拖拽（缩放后拖拽图片，或未缩放时切换图片）
const onMouseDown = (e: MouseEvent) => {
  if (e.button === 0) { // 仅处理左键
    e.preventDefault()
    isDragging.value = true
    wasDragging.value = false // 重置拖拽标志
    console.log('🖱️ Mouse down: wasDragging reset to false')
    touchStartX.value = e.clientX
    touchStartY.value = e.clientY
    touchCurrentX.value = e.clientX
    touchCurrentY.value = e.clientY
    dragStartTime.value = Date.now()
    dragVelocity.value = 0

    dragStart.value = {
      x: e.clientX,
      y: e.clientY,
      translateX: translateX.value,
      translateY: translateY.value
    }

    // 如果未缩放，准备图片滑动切换
    if (scale.value === 1) {
      isDraggingImage.value = true
      imageTransitionOffset.value = 0
      transitioningPhoto.value = null
      transitionDirection.value = null
      pendingTransitionDirection.value = null
    } else {
      // 缩放后，准备拖拽图片
      isDraggingImage.value = false
    }

    window.addEventListener('mousemove', onMouseMove)
    window.addEventListener('mouseup', onMouseUp)
  }
}

const onMouseMove = (e: MouseEvent) => {
  if (!isDragging.value) return

  touchCurrentX.value = e.clientX
  touchCurrentY.value = e.clientY

  // 一旦鼠标移动，就标记为拖拽操作
  wasDragging.value = true
  console.log('🐭 Mouse move: wasDragging set to true')

  if (scale.value > 1) {
    // 缩放后，鼠标拖拽图片
    const dx = e.clientX - dragStart.value.x
    const dy = e.clientY - dragStart.value.y
    translateX.value = dragStart.value.translateX + dx
    translateY.value = dragStart.value.translateY + dy
    constrainTranslation()
  } else if (isDraggingImage.value) {
    // 未缩放时，鼠标滑动切换图片
    const dx = e.clientX - touchStartX.value
    const dy = e.clientY - touchStartY.value

    // 垂直滑动距离太大时，不触发水平切换
    if (Math.abs(dy) > Math.abs(dx) * 0.5) {
      return
    }

    const container = imageContainer.value
    if (container && Math.abs(dx) > 2) {
      const direction = dx > 0 ? 'prev' : 'next'
      const nextIndex = direction === 'next'
        ? (currentIndex.value + 1) % props.photos.length
        : (currentIndex.value - 1 + props.photos.length) % props.photos.length

      // 如果还没有设置过渡图片，设置它
      if (!transitioningPhoto.value) {
        transitioningPhoto.value = props.photos[nextIndex]
        transitionDirection.value = direction === 'next' ? 'right' : 'left'
        pendingTransitionDirection.value = direction
      }

      // 计算滑动速度（用于后续动画）
      const now = Date.now()
      const timeDelta = now - dragStartTime.value
      if (timeDelta > 0) {
        dragVelocity.value = dx / timeDelta
      }

      // 1:1 移动，不使用阻尼效果
      imageTransitionOffset.value = dx
    }
  }
}

const onMouseUp = () => {
  if (!isDragging.value) return

  // 如果正在图片切换中，处理切换逻辑
  if (isDraggingImage.value && scale.value === 1 && transitioningPhoto.value) {
    const container = imageContainer.value
    if (container) {
      const dx = touchCurrentX.value - touchStartX.value
      const dy = touchCurrentY.value - touchStartY.value

      // 垂直滑动太大时，不切换
      if (Math.abs(dy) > Math.abs(dx) * 0.5) {
        animateImageReset()
      } else {
        // 计算是否应该切换：基于距离和速度
        const distanceThreshold = container.clientWidth * 0.3 // 30%的容器宽度
        const velocityThreshold = 0.5 // 最小滑动速度（像素/毫秒）

        const shouldSwitch = Math.abs(dx) > distanceThreshold ||
                            (Math.abs(dragVelocity.value) > velocityThreshold && Math.abs(dx) > container.clientWidth * 0.1)

        if (shouldSwitch) {
          // 完成切换
          const direction = dx > 0 ? 'prev' : 'next'
          animateImageTransitionWithVelocity(direction, dragVelocity.value)
        } else {
          // 回到原位
          animateImageReset()
        }
      }
    }
  }

  // 重置状态
  isDragging.value = false
  isDraggingImage.value = false
  dragVelocity.value = 0

  // 延迟重置wasDragging，给异步click事件缓冲时间
  setTimeout(() => {
    if (!isDragging.value) { // 确保没有新的拖拽开始
      wasDragging.value = false
      console.log('🔄 Mouse up: wasDragging reset to false after delay')
    }
  }, 300)

  window.removeEventListener('mousemove', onMouseMove)
  window.removeEventListener('mouseup', onMouseUp)
}

const formatDate = (val?: string) => {
  if (!val) return ''
  return val.slice(0, 10)
}

const formatFileSize = (bytes?: number) => {
  if (!bytes || bytes === 0) return '未知'
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  const k = 1024
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + units[i]
}

// 查看原图状态
const viewingOriginal = ref(false)
const { viewOriginalEnabled } = useUiSettings()

const getImageUrl = (photo: Photo) => {
  // 如果启用了查看原图功能且当前正在查看原图
  if (viewOriginalEnabled.value && viewingOriginal.value) {
    if (photo.originalPath) return `/api/files${photo.originalPath}`
  }

  // 优先使用大缩略图（如果有的话）
  if (photo.largeThumbPath) {
    return `/api/files${photo.largeThumbPath}`
  }

  // 回退到webp或原图
  if (photo.webpPath) return `/api/files${photo.webpPath}`
  if (photo.originalPath) return `/api/files${photo.originalPath}`
  return ''
}

const getThumbUrl = (photo: Photo) => {
  if (photo.thumbnailPath) return `/api/files${photo.thumbnailPath}`
  return getImageUrl(photo)
}

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
    : getThumbUrl(photo) || ''
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

const onImageLoad = () => {
  if (mainImage.value) {
    // 确保图片已完全加载
    const img = mainImage.value
    if (img.complete && img.naturalWidth > 0 && img.naturalHeight > 0) {
      imageSize.value = {
        width: img.offsetWidth,
        height: img.offsetHeight
      }
      imageLoaded.value = true
    } else {
      imageLoaded.value = false
    }
  }
}

const getImageMetrics = () => {
  if (!currentPhoto.value || !mainImage.value || !imageContainer.value || !imageLoaded.value) return null
  
  const img = mainImage.value
  const container = imageContainer.value
  if (!img.complete || img.naturalWidth === 0 || img.naturalHeight === 0) return null
  
  const imgRect = img.getBoundingClientRect()
  const containerRect = container.getBoundingClientRect()
  const naturalWidth = img.naturalWidth
  const naturalHeight = img.naturalHeight
  const elementWidth = imgRect.width
  const elementHeight = imgRect.height
  const scaleX = elementWidth / naturalWidth
  const scaleY = elementHeight / naturalHeight
  const baseScale = Math.min(scaleX, scaleY)
  const baseDisplayWidth = naturalWidth * baseScale
  const baseDisplayHeight = naturalHeight * baseScale
  const offsetX = (elementWidth - baseDisplayWidth) / 2
  const offsetY = (elementHeight - baseDisplayHeight) / 2
  const imgOffsetX = imgRect.left - containerRect.left
  const imgOffsetY = imgRect.top - containerRect.top
  
  return { baseDisplayWidth, baseDisplayHeight, offsetX, offsetY, imgOffsetX, imgOffsetY, naturalWidth, naturalHeight }
}

const getFocusBoxStyle = () => {
  if (!currentPhoto.value || currentPhoto.value.focusX === undefined || currentPhoto.value.focusY === undefined) return {}
  // Compute focus box using percentages relative to the image element so it scales with the wrapper transform.
  const img = mainImage.value
  if (!img) return {}
  const naturalW = currentPhoto.value.width || img.naturalWidth || 1
  const naturalH = currentPhoto.value.height || img.naturalHeight || 1
  const fw = 0.2 // focus box size as proportion of min dimension (20%)
  const focusX = Number(currentPhoto.value.focusX) / 100
  const focusY = Number(currentPhoto.value.focusY) / 100
  const boxPercent = Math.max(3, Math.min(40, fw * 100)) // percent value clamped
  const leftPercent = Math.max(0, Math.min(100, focusX * 100 - boxPercent / 2))
  const topPercent = Math.max(0, Math.min(100, focusY * 100 - boxPercent / 2))
  return {
    left: `${leftPercent}%`,
    top: `${topPercent}%`,
    width: `${boxPercent}%`,
    height: `${boxPercent}%`
  }
}

const faceBoxes = computed(() => {
  if (!showFaceBoxes.value || !currentPhoto.value?.faces?.length || isOpeningFromThumb.value) return []
  const metrics = getImageMetrics()
  if (!metrics) return []
  const { baseDisplayWidth, baseDisplayHeight, offsetX, offsetY, imgOffsetX, imgOffsetY, naturalWidth, naturalHeight } = metrics
  // determine highlighting options from props.openOptions
  const opts: any = (props as any).openOptions || {}
  const highlightFaceId = opts?.highlightedFaceId ?? null
  const highlightClusterId = opts?.highlightedClusterId ?? null
  const highlightPersonId = opts?.highlightedPersonId ?? null
  const highlightFaceIdsArr: number[] = Array.isArray(opts?.highlightedFaceIds) ? opts.highlightedFaceIds.map((v: any) => Number(v)) : []
  const highlightFaceIdsSet = new Set<number>(highlightFaceIdsArr)
  const preferredFaceId = opts?.preferredFaceId ?? null
  // If preferredFaceId exists and this photo contains that face, prefer it over other highlights.
  const preferredOnThisPhoto = preferredFaceId !== null && currentPhoto.value?.faces?.some((f: any) => Number(f.id) === Number(preferredFaceId))
  const usingHighlight = preferredOnThisPhoto || highlightFaceId !== null || highlightClusterId !== null || highlightPersonId !== null || highlightFaceIdsArr.length > 0

  const boxes = currentPhoto.value.faces
    .filter(face => face.x !== undefined && face.y !== undefined && face.width && face.height)
    .map((face, idx) => {
      // compute normalized coordinates in [0,1]
      const usePixel = (face.x ?? 0) > 1 || (face.y ?? 0) > 1 || (face.width ?? 0) > 1 || (face.height ?? 0) > 1
      const normX = usePixel ? (face.x || 0) / naturalWidth : (face.x || 0)
      const normY = usePixel ? (face.y || 0) / naturalHeight : (face.y || 0)
      const normW = usePixel ? (face.width || 0) / naturalWidth : (face.width || 0)
      const normH = usePixel ? (face.height || 0) / naturalHeight : (face.height || 0)
      // convert to percentage values relative to image element
      const leftPct = Math.max(0, Math.min(100, normX * 100))
      const topPct = Math.max(0, Math.min(100, normY * 100))
      const widthPct = Math.max(0.5, Math.min(100, normW * 100))
      const heightPct = Math.max(0.5, Math.min(100, normH * 100))
      // consider explicit id set first
      const fid = Number(face.id || idx)
      let isHighlighted = false
      if (preferredOnThisPhoto) {
        isHighlighted = fid === Number(preferredFaceId)
      } else if (highlightFaceIdsSet.size > 0) {
        isHighlighted = highlightFaceIdsSet.has(fid)
      } else if (highlightFaceId !== null) {
        isHighlighted = fid === Number(highlightFaceId)
      } else if (highlightClusterId !== null) {
        isHighlighted = Number((face as any).clusterId) === Number(highlightClusterId)
      } else if (highlightPersonId !== null) {
        isHighlighted = Number((face as any).personId) === Number(highlightPersonId)
      } else {
        isHighlighted = false
      }
      const muted = usingHighlight ? !isHighlighted : false
      return {
        id: face.id ?? idx,
        style: {
          left: `${leftPct}%`,
          top: `${topPct}%`,
          width: `${widthPct}%`,
          height: `${heightPct}%`
        },
        confirmed: face.isConfirmed,
        label: face.personName || (face.isConfirmed ? '未命名' : '未确认'),
        highlighted: isHighlighted,
        muted
      }
    })
    .filter(b => {
      // if usingHighlight, keep only highlighted boxes
      if (usingHighlight) return b.highlighted
      return true
    })

  // debug logging removed

  return boxes
})

// visible face list for sidebar: if using highlight, show only highlighted faces (no semi-transparent)
const visibleFaceList = computed(() => {
  if (!currentPhoto.value?.faces?.length) return []
  const opts: any = (props as any).openOptions || {}
  const highlightFaceId = opts?.highlightedFaceId ?? null
  const highlightClusterId = opts?.highlightedClusterId ?? null
  const highlightPersonId = opts?.highlightedPersonId ?? null
  // normalize incoming highlighted ids to numbers to avoid string/number mismatch
  const highlightFaceIdsArr: number[] = Array.isArray(opts?.highlightedFaceIds)
    ? opts.highlightedFaceIds.map((v: any) => Number(v))
    : []
  const highlightFaceIdsSet = new Set<number>(highlightFaceIdsArr)
  const preferredFaceId = opts?.preferredFaceId ?? null
  const preferredOnThisPhoto = preferredFaceId !== null && currentPhoto.value?.faces?.some((f: any) => Number(f.id) === Number(preferredFaceId))
  const usingHighlight = preferredOnThisPhoto || highlightFaceId !== null || highlightClusterId !== null || highlightPersonId !== null || highlightFaceIdsArr.length > 0

  if (!usingHighlight) return currentPhoto.value.faces
  if (preferredOnThisPhoto) {
    return currentPhoto.value.faces.filter(face => Number(face.id) === Number(preferredFaceId))
  }
  const filtered = currentPhoto.value.faces.filter(face => {
    const fid = Number(face.id || 0)
    if (highlightFaceIdsSet.size > 0) return highlightFaceIdsSet.has(fid)
    if (highlightFaceId !== null) return fid === Number(highlightFaceId)
    if (highlightClusterId !== null) return Number((face as any).clusterId) === Number(highlightClusterId)
    if (highlightPersonId !== null) return Number((face as any).personId) === Number(highlightPersonId)
    return false
  })
  return filtered
})

// 双击缩放
const onDoubleClick = (e: MouseEvent) => {
  if (!mainImage.value || !imageContainer.value) return

  if (scale.value > 1) {
    // 如果已缩放，重置到原始大小
    animateZoomToFit(1)
  } else {
    // 放大到2倍，以点击位置为中心
    animateZoomToPoint(2, e.clientX, e.clientY)
  }
}

// 在指定点缩放
const zoomAtPoint = (clientX: number, clientY: number, delta: number) => {
  if (!mainImage.value || !imageContainer.value) return
  
  const container = imageContainer.value
  const containerRect = container.getBoundingClientRect()
  
  // 计算相对于容器的坐标
  const x = clientX - containerRect.left - containerRect.width / 2
  const y = clientY - containerRect.top - containerRect.height / 2
  
  // 计算新的缩放值
  const targetScale = Math.max(1, Math.min(5, scale.value + delta))
  const scaleDelta = targetScale / scale.value
  
  // 调整平移，使缩放中心点保持不变
  translateX.value = x - (x - translateX.value) * scaleDelta
  translateY.value = y - (y - translateY.value) * scaleDelta
  
  scale.value = targetScale
  constrainTranslation()
}

// 限制平移范围
const constrainTranslation = () => {
  if (!mainImage.value || !imageContainer.value || scale.value <= 1) {
    translateX.value = 0
    translateY.value = 0
    return
  }
  
  const img = mainImage.value
  const container = imageContainer.value
  
  const imgRect = img.getBoundingClientRect()
  const containerRect = container.getBoundingClientRect()
  
  const scaledWidth = imgRect.width * scale.value
  const scaledHeight = imgRect.height * scale.value
  
  const maxX = (scaledWidth - containerRect.width) / 2
  const maxY = (scaledHeight - containerRect.height) / 2
  
  translateX.value = Math.max(-maxX, Math.min(maxX, translateX.value))
  translateY.value = Math.max(-maxY, Math.min(maxY, translateY.value))
}

const resetZoom = () => {
  animateZoomToFit(1)
}

// 缩放到适合大小（重置）
const animateZoomToFit = (targetScale: number) => {
  const duration = 200 // 动画持续时间ms，加快到200ms
  const startTime = Date.now()
  const startScale = scale.value
  const startTranslateX = translateX.value
  const startTranslateY = translateY.value

  const animate = () => {
    const elapsed = Date.now() - startTime
    const progress = Math.min(elapsed / duration, 1)

    // 使用ease-in缓动：由快到慢
    const easeProgress = progress * progress // ease-in quadratic

    scale.value = startScale + (targetScale - startScale) * easeProgress
    translateX.value = startTranslateX + (0 - startTranslateX) * easeProgress
    translateY.value = startTranslateY + (0 - startTranslateY) * easeProgress

    if (progress >= 1) {
      constrainTranslation() // 动画结束后再约束位置
    }

    if (progress < 1) {
      requestAnimationFrame(animate)
    }
  }

  animate()
}

// 以指定点为中心的缩放动画
const animateZoomToPoint = (targetScale: number, clientX: number, clientY: number) => {
  if (!mainImage.value || !imageContainer.value) return

  const container = imageContainer.value
  const containerRect = container.getBoundingClientRect()

  // 计算点击位置相对于容器的坐标
  const clickX = clientX - containerRect.left
  const clickY = clientY - containerRect.top

  // 计算容器的中心点
  const centerX = containerRect.width / 2
  const centerY = containerRect.height / 2

  // 当前状态：图片中心在容器中的位置
  const currentCenterX = centerX + translateX.value
  const currentCenterY = centerY + translateY.value

  // 计算点击点相对于当前图片中心的偏移
  const offsetX = clickX - currentCenterX
  const offsetY = clickY - currentCenterY

  // 计算目标状态：点击点应该成为新的图片中心
  // 所以新的translate应该使点击点移动到容器中心
  const targetTranslateX = centerX - clickX
  const targetTranslateY = centerY - clickY

  const duration = 200 // 动画持续时间ms，加快到200ms
  const startTime = Date.now()
  const startScale = scale.value
  const startTranslateX = translateX.value
  const startTranslateY = translateY.value

  const animate = () => {
    const elapsed = Date.now() - startTime
    const progress = Math.min(elapsed / duration, 1)

    // 使用ease-in缓动：由快到慢
    const easeProgress = progress * progress // ease-in quadratic

    scale.value = startScale + (targetScale - startScale) * easeProgress
    translateX.value = startTranslateX + (targetTranslateX - startTranslateX) * easeProgress
    translateY.value = startTranslateY + (targetTranslateY - startTranslateY) * easeProgress

    if (progress >= 1) {
      constrainTranslation() // 动画结束后再约束位置
    }

    if (progress < 1) {
      requestAnimationFrame(animate)
    }
  }

  animate()
}

// 获取图片样式（确保 object-contain 正确工作）
const getImageStyle = (): Record<string, string> => {
  if (!mainImage.value || !imageContainer.value) {
    return {
      maxWidth: '100%',
      maxHeight: '100%',
      objectFit: 'contain',
      display: 'block'
    }
  }
  
  const container = imageContainer.value
  const containerRect = container.getBoundingClientRect()
  
  // 当处于全屏模式时，优先让图片铺满视口（使用 cover），以消除上下留白。
  if (isFullscreen.value) {
    return {
      width: '100vw',
      height: '100vh',
      maxWidth: 'none',
      maxHeight: 'none',
      objectFit: 'cover',
      display: 'block'
    }
  }

  return {
    maxWidth: `${containerRect.width}px`,
    maxHeight: `${containerRect.height}px`,
    width: 'auto',
    height: 'auto',
    objectFit: 'contain',
    display: 'block'
  }
}

// 获取图片变换样式
const getImageTransformStyle = () => {
  // 基础变换：缩放和平移
  let transform = `translate(${translateX.value}px, ${translateY.value}px) scale(${scale.value})`

  // 在触摸滑动时，主图片跟随手指移动（1:1 跟手效果）
  if (isDraggingImage.value) {
    transform += ` translateX(${imageTransitionOffset.value}px)`
  }

  // 在图片切换动画过程中，主图片保持不动（过渡图片会覆盖它）
  // 不在这里应用偏移，而是让过渡图片负责动画

  return {
    transform: transform,
    transformOrigin: 'center center',
    transition: isImageTransitioning.value ? 'none' : 'transform 0.3s ease-out'
  }
}

watch(
  () => currentIndex.value,
  () => {
    // 只有在非动画状态下才执行这些操作
    if (!isImageTransitioning.value) {
      scrollThumbIntoView()
      // 图片切换时重置状态，等待新图片加载
      imageSize.value = { width: 0, height: 0 }
      imageLoaded.value = false
      // 重置缩放
      resetZoom()
      // 重置图片切换动画状态
      imageTransitionOffset.value = 0
      transitioningPhoto.value = null
      transitionDirection.value = null
      pendingTransitionDirection.value = null
      // notify parent about index change so parent can sync selection/scroll in list
      emit('viewer-index-change', {
        index: currentIndex.value,
        photoId: currentPhoto.value?.id,
        faceIds: currentPhoto.value?.faces?.map((f: any) => f.id) || []
      })
    }
    // 图片加载完成后会自动调用 onImageLoad
  }
)

watch(
  () => props.photos,
  () => {
    thumbItems.value = []
    // Force re-render of main image when photos list changes to avoid stale metrics
    // (handles case when same photo is reopened or photos array is replaced)
    mainImageKey.value = (mainImageKey.value || 0) + 1
    imageLoaded.value = false
    resetZoom()
    nextTick(() => scrollThumbIntoView())
  }
)

// Ensure startIndex updates are applied even when viewer is already open.
watch(
  () => (props as any).startIndex,
  (val) => {
    if (typeof val === 'number' && props.photos?.length) {
      currentIndex.value = Math.min(Math.max(val, 0), props.photos.length - 1)
      // force image re-render to ensure load event fires and metrics recalc
      mainImageKey.value = (mainImageKey.value || 0) + 1
      imageLoaded.value = false
      resetZoom()
      nextTick(() => {
        scrollThumbIntoView()
      })
    }
  }
)

onBeforeUnmount(() => {
  stopDrag()
  onMouseUp()
  window.removeEventListener('mousemove', onMouseMove)
  window.removeEventListener('mouseup', onMouseUp)
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

.slide-fade-enter-active,
.slide-fade-leave-active {
  transition: all 0.18s ease;
}
.slide-fade-enter-from {
  opacity: 0;
  transform: translateX(12px);
}
.slide-fade-leave-to {
  opacity: 0;
  transform: translateX(12px);
}
</style>

