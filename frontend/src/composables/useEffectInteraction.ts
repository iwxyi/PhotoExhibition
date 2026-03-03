import { ref, readonly } from 'vue'

export interface ClickEvent {
  x: number
  y: number
  time: number
}

const clicks = ref<ClickEvent[]>([])
const scrollVelocity = ref(0)

let lastScrollY = 0
let lastScrollTime = 0

export function useEffectInteraction() {
  const triggerClick = (x: number, y: number) => {
    clicks.value.push({ x, y, time: performance.now() })
    if (clicks.value.length > 10) clicks.value.shift()
  }

  const updateScroll = () => {
    const now = performance.now()
    const dy = window.scrollY - lastScrollY
    const dt = now - lastScrollTime
    if (dt > 0) {
      scrollVelocity.value = scrollVelocity.value * 0.7 + (dy / dt) * 1000 * 0.3
    }
    lastScrollY = window.scrollY
    lastScrollTime = now
  }

  const consumeClicks = (since: number): ClickEvent[] => {
    const result = clicks.value.filter(c => c.time > since)
    return result
  }

  return {
    clicks: readonly(clicks),
    scrollVelocity: readonly(scrollVelocity),
    triggerClick,
    updateScroll,
    consumeClicks,
  }
}
