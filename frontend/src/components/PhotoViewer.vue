<template>
  <transition name="fade">
    <div
      v-if="visible"
      class="fixed inset-0 z-50 bg-black/90 backdrop-blur-sm flex flex-col outline-none focus:outline-none"
      @keydown.stop.prevent="onKeydown"
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
          <button class="p-2 hover:bg-white/10 rounded" @click="prev">←</button>
          <span class="text-xs sm:text-sm">{{ currentIndex + 1 }} / {{ photos.length }}</span>
          <button class="p-2 hover:bg-white/10 rounded" @click="next">→</button>
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
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 16h-1v-4h-1m1-4h.01M12 19a7 7 0 100-14 7 7 0 000 14z" />
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

      <div class="flex-1 flex overflow-hidden min-h-0">
        <!-- 主图区域 -->
        <div class="flex-1 flex items-center justify-center relative px-2 sm:px-6 min-h-0">
          <div class="relative w-full h-full flex items-center justify-center overflow-hidden" ref="imageContainer">
            <div
              class="relative inline-block viewer-open-anim"
              :class="{ 'viewer-open-anim--active': isOpeningFromThumb }"
              :style="[getImageTransformStyle(), openAnimStyle]"
              @wheel="onWheelZoom"
              @touchstart="onTouchStartZoom"
              @touchmove="onTouchMoveZoom"
              @touchend="onTouchEndZoom"
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
                draggable="false"
              />
            </div>
            <!-- 焦点框覆盖层 -->
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
        <transition name="slide-fade">
          <div
            v-if="!infoCollapsed"
            class="w-80 max-w-[80vw] bg-gray-900/80 text-white border-l border-white/10 flex flex-col max-h-full overflow-auto"
          >
            <div class="flex items-center justify-between px-4 py-3 border-b border-white/10">
              <span class="text-sm font-semibold">信息</span>
              <button class="text-xs opacity-70 hover:opacity-100" @click="toggleInfo">折叠</button>
            </div>
            <div class="flex-1 overflow-auto px-4 py-3 space-y-2 text-xs leading-relaxed">
              <!-- 基本信息 -->
              <div><span class="opacity-60">文件名：</span>{{ currentPhoto?.filename }}</div>
              <div v-if="currentPhoto?.takenAt"><span class="opacity-60">拍摄时间：</span>{{ formatDate(currentPhoto.takenAt) }}</div>
              <div v-if="currentPhoto?.createdAt"><span class="opacity-60">入库时间：</span>{{ formatDate(currentPhoto.createdAt) }}</div>

              <!-- 相机和镜头信息 -->
              <div v-if="currentPhoto?.cameraMake || currentPhoto?.cameraModel">
                <span class="opacity-60">相机：</span>{{ currentPhoto.cameraMake ? currentPhoto.cameraMake + ' ' : '' }}{{ currentPhoto.cameraModel }}
              </div>
              <div v-if="currentPhoto?.lensModel"><span class="opacity-60">镜头：</span>{{ currentPhoto.lensModel }}</div>

              <!-- 拍摄参数 -->
              <div class="grid grid-cols-2 gap-2">
                <div v-if="currentPhoto?.focalLength"><span class="opacity-60">焦距：</span>{{ currentPhoto.focalLength }}</div>
                <div v-if="currentPhoto?.aperture"><span class="opacity-60">光圈：</span>{{ currentPhoto.aperture }}</div>
                <div v-if="currentPhoto?.shutterSpeed"><span class="opacity-60">快门：</span>{{ currentPhoto.shutterSpeed }}</div>
                <div v-if="currentPhoto?.iso"><span class="opacity-60">ISO：</span>{{ currentPhoto.iso }}</div>
              </div>

              <!-- 图片规格 -->
              <div v-if="currentPhoto?.width && currentPhoto?.height">
                <span class="opacity-60">尺寸：</span>{{ currentPhoto.width }} × {{ currentPhoto.height }}
                <span v-if="currentPhoto?.format" class="ml-2 opacity-60">格式：</span>{{ currentPhoto.format }}
              </div>
              <div v-if="currentPhoto?.fileSize">
                <span class="opacity-60">文件大小：</span>{{ formatFileSize(currentPhoto.fileSize) }}
              </div>

              <!-- 质量和统计 -->
              <div class="grid grid-cols-2 gap-2">
                <div v-if="currentPhoto?.qualityScore"><span class="opacity-60">质量评分：</span>{{ currentPhoto.qualityScore?.toFixed(1) }}</div>
                <div v-if="currentPhoto?.viewCount"><span class="opacity-60">查看次数：</span>{{ currentPhoto.viewCount }}</div>
                <div v-if="currentPhoto?.likeCount"><span class="opacity-60">点赞次数：</span>{{ currentPhoto.likeCount }}</div>
                <div v-if="currentPhoto?.isFeatured !== undefined">
                  <span class="opacity-60">精选：</span>
                  <span :class="currentPhoto.isFeatured ? 'text-yellow-400' : 'text-gray-400'">
                    {{ currentPhoto.isFeatured ? '✓' : '✗' }}
                  </span>
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
                    @click.stop="f.personId && f.personName ? openPersonByFace(f) : null"
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

              <!-- 主色调信息 -->
              <div v-if="currentPhoto?.dominantColor">
                <span class="opacity-60">主色调：</span>
                <span
                  class="inline-block w-4 h-4 rounded border border-white/20 ml-2"
                  :style="{ backgroundColor: currentPhoto.dominantColor }"
                  :title="currentPhoto.dominantColor"
                ></span>
                <span class="ml-2 font-mono text-xs">{{ currentPhoto.dominantColor }}</span>
              </div>

              <!-- 调色板 -->
              <div v-if="currentPhoto?.colorPalette?.length">
                <span class="opacity-60">调色板：</span>
                <div class="flex gap-1 mt-1">
                  <span
                    v-for="(color, idx) in currentPhoto.colorPalette.slice(0, 6)"
                    :key="idx"
                    class="inline-block w-3 h-3 rounded border border-white/10"
                    :style="{ backgroundColor: color }"
                    :title="color"
                  ></span>
                </div>
              </div>

            </div>
          </div>
        </transition>

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
  openOptions?: { highlightedFaceId?: number; highlightedClusterId?: number; highlightedPersonId?: number; highlightedFaceIds?: number[] } | null
}>()

const emit = defineEmits<{
  (e: 'update:visible', val: boolean): void
}>()

const currentIndex = ref(0)
const infoCollapsed = ref(false)
const modalRoot = ref<HTMLElement | null>(null)
const touchStartX = ref(0)
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
const imageSize = ref({ width: 0, height: 0 })
const imageLoaded = ref(false)

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

const STORAGE_KEY = 'pe-info-collapsed'
const FOCUS_BOX_KEY = 'pe-focus-box-visible'
const FACE_BOXES_KEY = 'pe-face-boxes-visible'
const THUMB_KEY = 'pe-thumb-height'

const currentPhoto = computed(() => props.photos?.[currentIndex.value])
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
  const saved = localStorage.getItem(STORAGE_KEY)
  infoCollapsed.value = saved === '1'

  // 初始化框体状态
  initializeBoxStates()

  window.addEventListener('keydown', onKeydown)
  window.addEventListener('resize', onImageLoad)
  // 监听 fullscreen 变化以便在进入/退出全屏时重新计算图片尺寸
  document.addEventListener('fullscreenchange', onFullscreenChange)
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', onKeydown)
  window.removeEventListener('resize', onImageLoad)
  document.removeEventListener('fullscreenchange', onFullscreenChange)
})

const toggleInfo = () => {
  infoCollapsed.value = !infoCollapsed.value
  localStorage.setItem(STORAGE_KEY, infoCollapsed.value ? '1' : '0')
}

const close = () => {
  emit('update:visible', false)
}

const prev = () => {
  if (!props.photos?.length) return
  currentIndex.value = (currentIndex.value - 1 + props.photos.length) % props.photos.length
  // 切换照片时保持用户的全局偏好设置
}

const next = () => {
  if (!props.photos?.length) return
  currentIndex.value = (currentIndex.value + 1) % props.photos.length
  // 切换照片时保持用户的全局偏好设置
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
  if (e.touches.length === 1) {
    // 单指：准备拖拽
    isDragging.value = true
    dragStart.value = {
      x: e.touches[0].clientX,
      y: e.touches[0].clientY,
      translateX: translateX.value,
      translateY: translateY.value
    }
    touchStartX.value = e.touches[0].clientX
  } else if (e.touches.length === 2) {
    // 双指：准备缩放
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
  e.preventDefault()
  
  if (e.touches.length === 1 && isDragging.value && scale.value > 1) {
    // 单指拖拽（仅在缩放后）
    const dx = e.touches[0].clientX - dragStart.value.x
    const dy = e.touches[0].clientY - dragStart.value.y
    translateX.value = dragStart.value.translateX + dx
    translateY.value = dragStart.value.translateY + dy
    constrainTranslation()
  } else if (e.touches.length === 2 && isPinching.value) {
    // 双指缩放
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
  if (e.touches.length === 0) {
    // 所有手指抬起
    if (isDragging.value && scale.value === 1) {
      // 如果未缩放，检查是否是滑动切换图片
      const dx = e.changedTouches[0].clientX - touchStartX.value
      if (Math.abs(dx) > 40) {
        if (dx > 0) prev()
        else next()
      }
    }
    isDragging.value = false
    isPinching.value = false
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

const getTouchDistance = (touch1: Touch, touch2: Touch) => {
  const dx = touch2.clientX - touch1.clientX
  const dy = touch2.clientY - touch1.clientY
  return Math.sqrt(dx * dx + dy * dy)
}

// 鼠标拖拽（仅在缩放后）
const onMouseDown = (e: MouseEvent) => {
  if (scale.value > 1 && e.button === 0) {
    e.preventDefault()
    isDragging.value = true
    dragStart.value = {
      x: e.clientX,
      y: e.clientY,
      translateX: translateX.value,
      translateY: translateY.value
    }
    window.addEventListener('mousemove', onMouseMove)
    window.addEventListener('mouseup', onMouseUp)
  }
}

const onMouseMove = (e: MouseEvent) => {
  if (isDragging.value && scale.value > 1) {
    const dx = e.clientX - dragStart.value.x
    const dy = e.clientY - dragStart.value.y
    translateX.value = dragStart.value.translateX + dx
    translateY.value = dragStart.value.translateY + dy
    constrainTranslation()
  }
}

const onMouseUp = () => {
  if (isDragging.value) {
    isDragging.value = false
    window.removeEventListener('mousemove', onMouseMove)
    window.removeEventListener('mouseup', onMouseUp)
  }
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
  const metrics = getImageMetrics()
  if (!metrics) return {}
  const { baseDisplayWidth, baseDisplayHeight, offsetX, offsetY, imgOffsetX, imgOffsetY } = metrics
  const boxSize = Math.min(baseDisplayWidth, baseDisplayHeight) * 0.2 * scale.value
  const baseBoxSize = boxSize / scale.value
  const focusXOnBase = (currentPhoto.value.focusX! / 100) * baseDisplayWidth
  const focusYOnBase = (currentPhoto.value.focusY! / 100) * baseDisplayHeight
  const boxLeftOnBase = focusXOnBase - baseBoxSize / 2
  const boxTopOnBase = focusYOnBase - baseBoxSize / 2
  const clampedLeft = Math.max(0, Math.min(boxLeftOnBase, baseDisplayWidth - baseBoxSize))
  const clampedTop = Math.max(0, Math.min(boxTopOnBase, baseDisplayHeight - baseBoxSize))
  const left = imgOffsetX + offsetX + clampedLeft * scale.value + translateX.value
  const top = imgOffsetY + offsetY + clampedTop * scale.value + translateY.value
  return {
    left: `${left}px`,
    top: `${top}px`,
    width: `${boxSize}px`,
    height: `${boxSize}px`
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
  const usingHighlight = highlightFaceId !== null || highlightClusterId !== null || highlightPersonId !== null || highlightFaceIdsArr.length > 0

  const boxes = currentPhoto.value.faces
    .filter(face => face.x !== undefined && face.y !== undefined && face.width && face.height)
    .map((face, idx) => {
      const usePixel = (face.x ?? 0) > 1 || (face.y ?? 0) > 1 || (face.width ?? 0) > 1 || (face.height ?? 0) > 1
      const normX = usePixel ? (face.x || 0) / naturalWidth : (face.x || 0)
      const normY = usePixel ? (face.y || 0) / naturalHeight : (face.y || 0)
      const normW = usePixel ? (face.width || 0) / naturalWidth : (face.width || 0)
      const normH = usePixel ? (face.height || 0) / naturalHeight : (face.height || 0)
      const baseLeft = normX * baseDisplayWidth
      const baseTop = normY * baseDisplayHeight
      const baseWidth = normW * baseDisplayWidth
      const baseHeight = normH * baseDisplayHeight
      const left = imgOffsetX + offsetX + baseLeft * scale.value + translateX.value
      const top = imgOffsetY + offsetY + baseTop * scale.value + translateY.value
      const width = Math.max(8, baseWidth * scale.value)
      const height = Math.max(8, baseHeight * scale.value)
      // consider explicit id set first
      const fid = Number(face.id)
      let isHighlighted = false
      if (highlightFaceIdsSet.size > 0) {
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
          left: `${left}px`,
          top: `${top}px`,
          width: `${width}px`,
          height: `${height}px`
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
  const usingHighlight = highlightFaceId !== null || highlightClusterId !== null || highlightPersonId !== null || highlightFaceIdsArr.length > 0

  // debug logging removed

  if (!usingHighlight) return currentPhoto.value.faces
  const filtered = currentPhoto.value.faces.filter(face => {
    const fid = Number(face.id)
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
  if (scale.value > 1) {
    // 如果已缩放，重置
    resetZoom()
  } else {
    // 否则放大到2倍
    zoomAtPoint(e.clientX, e.clientY, 1)
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

// 重置缩放
const resetZoom = () => {
  scale.value = 1
  translateX.value = 0
  translateY.value = 0
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
  return {
    transform: `translate(${translateX.value}px, ${translateY.value}px) scale(${scale.value})`,
    transformOrigin: 'center center'
  }
}

watch(
  () => currentIndex.value,
  () => {
    scrollThumbIntoView()
    // 图片切换时重置状态，等待新图片加载
    imageSize.value = { width: 0, height: 0 }
    imageLoaded.value = false
    // 重置缩放
    resetZoom()
    // 图片加载完成后会自动调用 onImageLoad
  }
)

watch(
  () => props.photos,
  () => {
    thumbItems.value = []
    nextTick(() => scrollThumbIntoView())
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

