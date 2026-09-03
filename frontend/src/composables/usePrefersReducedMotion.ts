import { ref, onMounted, onUnmounted } from 'vue'

const QUERY = '(prefers-reduced-motion: reduce)'

/**
 * 读取系统的「减弱动态效果」设置。
 *
 * 相册展开/缩回的 FLIP 动画会让三张缩略图横跨整个屏幕飞行，前庭功能敏感的
 * 用户开启该设置后不应再看到这类大幅位移。CSS 层面由 @media 查询处理，
 * 这里供 JS 路径判断：直接跳过动画、走无过渡的即时导航。
 */
export const prefersReducedMotion = () =>
  typeof window !== 'undefined'
  && typeof window.matchMedia === 'function'
  && window.matchMedia(QUERY).matches

/** 组件内使用的响应式版本，跟随用户中途改设置。 */
export function usePrefersReducedMotion() {
  const reduced = ref(prefersReducedMotion())
  let media: MediaQueryList | null = null
  const onChange = (e: MediaQueryListEvent) => { reduced.value = e.matches }

  onMounted(() => {
    if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') return
    media = window.matchMedia(QUERY)
    reduced.value = media.matches
    media.addEventListener('change', onChange)
  })

  onUnmounted(() => {
    media?.removeEventListener('change', onChange)
    media = null
  })

  return { reduced }
}
