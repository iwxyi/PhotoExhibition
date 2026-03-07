<template>
  <div class="relative block" ref="containerRef">
    <!-- 触发按钮 -->
    <div
      ref="triggerEl"
      @click="handleClick"
      @mouseenter="handleMouseEnter"
    >
      <slot name="trigger"></slot>
    </div>

    <!-- 菜单面板 -->
    <Teleport to="body">
      <Transition name="menu-fade">
        <div
          v-if="visible"
          ref="menuEl"
          class="fixed z-[2000]"
          :style="menuStyle"
          @click.stop
        >
          <slot :close="close"></slot>
        </div>
      </Transition>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'

interface Props {
  trigger?: 'click' | 'hover'
  placement?: 'bottom' | 'right'
  offset?: number
}

const props = withDefaults(defineProps<Props>(), {
  trigger: 'hover',
  placement: 'bottom',
  offset: 4
})

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
}>()

const containerRef = ref<HTMLElement>()
const triggerEl = ref<HTMLElement>()
const menuEl = ref<HTMLElement>()
const visible = ref(false)

// 用于触发 computed 重新计算的 tick 值
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

// 监听 visible 变化
watch(visible, (val) => {
  emit('update:visible', val)
  if (val) {
    posTick.value++ // 触发菜单位置重新计算
  }
})

// 计算菜单位置（自动适应屏幕边缘）
const menuStyle = computed(() => {
  // 依赖 posTick 使 computed 响应式更新
  void posTick.value
  if (!triggerEl.value) {
    return { visibility: 'hidden', top: '0px', left: '0px' }
  }

  const rect = triggerEl.value.getBoundingClientRect()
  const vw = window.innerWidth
  const vh = window.innerHeight

  if (props.placement === 'right') {
    // 判断右边是否有足够空间（估计菜单宽度180px）
    const menuW = 220
    const menuH = 200
    const spaceRight = vw - rect.right
    const spaceLeft = rect.left
    const useLeft = spaceRight < menuW && spaceLeft > spaceRight

    const top = rect.top + rect.height / 2
    // 自动向上偏移防止底部溢出
    const adjustedTop = Math.min(top, vh - menuH)

    if (useLeft) {
      return {
        top: `${Math.max(0, adjustedTop)}px`,
        right: `${vw - rect.left + props.offset}px`,
        left: 'auto'
      }
    }
    return {
      top: `${Math.max(0, adjustedTop)}px`,
      left: `${rect.right + props.offset}px`
    }
  }

  // bottom: 判断下方是否有足够空间
  const menuH = 300
  const spaceBottom = vh - rect.bottom
  const spaceTop = rect.top
  const useTop = spaceBottom < menuH && spaceTop > spaceBottom

  // 判断右边是否溢出
  const menuW = 224
  const rightEdge = rect.left + menuW
  const leftPos = rightEdge > vw ? vw - menuW - props.offset : rect.left

  if (useTop) {
    return {
      bottom: `${vh - rect.top + props.offset}px`,
      top: 'auto',
      left: `${Math.max(0, leftPos)}px`
    }
  }
  return {
    top: `${rect.bottom + props.offset}px`,
    left: `${Math.max(0, leftPos)}px`
  }
})

const close = () => {
  clearCloseTimer()
  visible.value = false
}

const open = () => {
  clearCloseTimer()
  visible.value = true
}

const handleClick = () => {
  if (props.trigger === 'click') {
    visible.value = !visible.value
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
    // 鼠标进入面板，取消关闭计时
    clearCloseTimer()
  }
}

const handleMenuMouseLeave = () => {
  if (props.trigger === 'hover') {
    scheduleClose()
  }
}

// 点击外部关闭
const handleClickOutside = (event: MouseEvent) => {
  // menuEl 通过 teleport 渲染在 body，需要额外检查
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
  if (event.key === 'Escape' && visible.value) {
    close()
  }
}

// 窗口 resize 时更新菜单位置
const handleResize = () => {
  if (visible.value) {
    posTick.value++
  }
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

defineExpose({
  open,
  close,
  visible
})
</script>

<style scoped>
.menu-fade-enter-active,
.menu-fade-leave-active {
  transition: opacity 0.1s ease, transform 0.1s ease;
}

.menu-fade-enter-from,
.menu-fade-leave-to {
  opacity: 0;
  transform: scale(0.97);
}
</style>
