<template>
  <div class="relative block" ref="containerRef">
    <!-- 触发区域 -->
    <div
      ref="triggerEl"
      @click="handleClick"
      @mouseenter="handleMouseEnter"
      @mouseleave="handleMouseLeave"
    >
      <slot name="trigger"></slot>
    </div>

    <!-- teleport 模式：脱离 DOM 层级，用 JS 计算位置（用于顶层菜单逃脱 overflow/z-index 约束） -->
    <template v-if="teleport">
      <Teleport to="body">
        <Transition name="menu-fade">
          <div
            v-if="visible"
            ref="menuEl"
            class="fixed z-[2000]"
            :style="menuStyle"
            @click.stop
            @mouseenter="handleMenuMouseEnter"
            @mouseleave="handleMenuMouseLeave"
          >
            <slot :close="close"></slot>
          </div>
        </Transition>
      </Teleport>
    </template>

    <!-- 非 teleport 模式：保留在 DOM 层级内，用 CSS absolute 定位（用于嵌套子菜单，避免父子面板互相触发 mouseleave） -->
    <template v-else>
      <Transition name="menu-fade">
        <div
          v-if="visible"
          ref="menuEl"
          class="absolute z-50"
          :class="localPlacementClass"
          @click.stop
          @mouseenter="handleMenuMouseEnter"
          @mouseleave="handleMenuMouseLeave"
        >
          <slot :close="close"></slot>
        </div>
      </Transition>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'

interface Props {
  trigger?: 'click' | 'hover'
  placement?: 'bottom' | 'right'
  offset?: number
  /** 是否使用 Teleport 渲染面板。顶层菜单用 true（默认），嵌套子菜单用 false 避免 mouseleave 问题 */
  teleport?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  trigger: 'hover',
  placement: 'bottom',
  offset: 4,
  teleport: true
})

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
}>()

const containerRef = ref<HTMLElement>()
const triggerEl = ref<HTMLElement>()
const menuEl = ref<HTMLElement>()
const visible = ref(false)

// 用于触发 teleport 模式下 computed 重新计算的 tick 值
const posTick = ref(0)

// hover 菜单共享的关闭 timer，trigger 和面板共同维护
let closeTimer: ReturnType<typeof setTimeout> | null = null

const clearCloseTimer = () => {
  if (closeTimer !== null) {
    clearTimeout(closeTimer)
    closeTimer = null
  }
}

const scheduleClose = (delay = 300) => {
  clearCloseTimer()
  closeTimer = setTimeout(() => {
    close()
  }, delay)
}

watch(visible, (val) => {
  emit('update:visible', val)
  if (val && props.teleport) {
    posTick.value++
  }
})

// ─── teleport 模式：JS 计算位置（自动适应屏幕边缘）──────────────────────────
const menuStyle = computed(() => {
  void posTick.value
  if (!triggerEl.value) return { visibility: 'hidden', top: '0px', left: '0px' }

  const rect = triggerEl.value.getBoundingClientRect()
  const vw = window.innerWidth
  const vh = window.innerHeight

  if (props.placement === 'right') {
    const menuW = 220
    const menuH = 200
    const useLeft = (vw - rect.right) < menuW && rect.left > (vw - rect.right)
    const top = Math.min(rect.top + rect.height / 2, vh - menuH)

    if (useLeft) {
      return { top: `${Math.max(0, top)}px`, right: `${vw - rect.left + props.offset}px`, left: 'auto' }
    }
    return { top: `${Math.max(0, top)}px`, left: `${rect.right + props.offset}px` }
  }

  // bottom
  const menuH = 300
  const menuW = 224
  const useTop = (vh - rect.bottom) < menuH && rect.top > (vh - rect.bottom)
  const leftPos = Math.max(0, (rect.left + menuW > vw) ? vw - menuW - props.offset : rect.left)

  if (useTop) {
    return { bottom: `${vh - rect.top + props.offset}px`, top: 'auto', left: `${leftPos}px` }
  }
  return { top: `${rect.bottom + props.offset}px`, left: `${leftPos}px` }
})

// ─── 非 teleport 模式：CSS class 定位（自动检测空间翻转方向）────────────────
const localPlacementClass = computed(() => {
  if (!props.teleport) {
    // 在挂载后根据 triggerEl 位置动态决定方向
    void posTick.value
    if (props.placement === 'right') {
      const shouldFlip = triggerEl.value
        ? (window.innerWidth - triggerEl.value.getBoundingClientRect().right) < 220
        : false
      return shouldFlip
        ? `right-full top-0 mr-[${props.offset}px]`
        : `left-full top-0 ml-[${props.offset}px]`
    }
    // bottom
    const shouldFlip = triggerEl.value
      ? (window.innerHeight - triggerEl.value.getBoundingClientRect().bottom) < 200
      : false
    return shouldFlip
      ? `bottom-full left-0 mb-[${props.offset}px]`
      : `top-full left-0 mt-[${props.offset}px]`
  }
  return ''
})

// ─── 事件处理 ───────────────────────────────────────────────────────────────
const close = () => {
  clearCloseTimer()
  visible.value = false
}

const open = () => {
  clearCloseTimer()
  visible.value = true
  if (!props.teleport) posTick.value++
}

const handleClick = () => {
  if (props.trigger === 'click') {
    visible.value ? close() : open()
  }
}

const handleMouseEnter = () => {
  if (props.trigger === 'hover') {
    clearCloseTimer()
    open()
  }
}

const handleMouseLeave = () => {
  if (props.trigger === 'hover') {
    scheduleClose()
  }
}

const handleMenuMouseEnter = () => {
  if (props.trigger === 'hover') {
    clearCloseTimer()
  }
}

const handleMenuMouseLeave = () => {
  if (props.trigger === 'hover') {
    scheduleClose()
  }
}

// ─── 点击外部关闭（仅 click 触发或 teleport 模式）───────────────────────────
const handleClickOutside = (event: MouseEvent) => {
  if (
    containerRef.value &&
    !containerRef.value.contains(event.target as Node) &&
    menuEl.value &&
    !menuEl.value.contains(event.target as Node)
  ) {
    close()
  }
}

const handleKeydown = (event: KeyboardEvent) => {
  if (event.key === 'Escape' && visible.value) close()
}

const handleResize = () => {
  if (visible.value) posTick.value++
}

onMounted(() => {
  document.addEventListener('click', handleClickOutside)
  document.addEventListener('keydown', handleKeydown)
  window.addEventListener('resize', handleResize)
  window.addEventListener('scroll', handleResize, true)
})

onUnmounted(() => {
  clearCloseTimer()
  document.removeEventListener('click', handleClickOutside)
  document.removeEventListener('keydown', handleKeydown)
  window.removeEventListener('resize', handleResize)
  window.removeEventListener('scroll', handleResize, true)
})

defineExpose({ open, close, visible })
</script>

<style scoped>
.menu-fade-enter-active,
.menu-fade-leave-active {
  transition: opacity 0.12s ease, transform 0.12s ease;
}
.menu-fade-enter-from,
.menu-fade-leave-to {
  opacity: 0;
  transform: scale(0.97);
}
</style>
