import { ref, onMounted, onUnmounted, watch } from 'vue'
import { useRoute } from 'vue-router'

export function useNavAutoHide() {
  const isHidden = ref(false)
  const route = useRoute()
  let lastScrollY = 0
  let ticking = false
  let hideTimeout: number | null = null
  let navigationProtectionTimeout: number | null = null
  let isNavigationProtected = false

  const updateNavVisibility = () => {
    const currentScrollY = window.scrollY
    const scrollThreshold = 50 // 开始隐藏的滚动阈值
    const scrollDelta = currentScrollY - lastScrollY

    // 清除之前的隐藏定时器
    if (hideTimeout) {
      clearTimeout(hideTimeout)
      hideTimeout = null
    }

    // 如果在导航保护期内，保持显示状态
    if (isNavigationProtected) {
      lastScrollY = currentScrollY
      ticking = false
      return
    }

    // 如果在顶部附近，始终显示
    if (currentScrollY < scrollThreshold) {
      isHidden.value = false
      lastScrollY = currentScrollY
      ticking = false
      return
    }

    // 根据滚动方向决定显示/隐藏
    if (scrollDelta > 0) {
      // 向上滚动（内容向上移动）- 隐藏导航栏
      isHidden.value = true
    } else if (scrollDelta < -10) {
      // 向下滚动（内容向下移动）- 显示导航栏
      isHidden.value = false
    }

    lastScrollY = currentScrollY
    ticking = false
  }

  const handleScroll = () => {
    if (!ticking) {
      requestAnimationFrame(updateNavVisibility)
      ticking = true
    }
  }

  const handleTouchStart = () => {
    // 触摸开始时临时显示导航栏
    if (isHidden.value) {
      isHidden.value = false
      // 3秒后自动隐藏
      hideTimeout = window.setTimeout(() => {
        isHidden.value = true
        hideTimeout = null
      }, 3000)
    }
  }

  const startNavigationProtection = () => {
    // 开始导航保护期
    isNavigationProtected = true
    isHidden.value = false // 确保导航栏显示

    // 清除之前的保护定时器
    if (navigationProtectionTimeout) {
      clearTimeout(navigationProtectionTimeout)
    }

    // 2秒后结束导航保护期
    navigationProtectionTimeout = window.setTimeout(() => {
      isNavigationProtected = false
      navigationProtectionTimeout = null
    }, 2000)
  }

  onMounted(() => {
    window.addEventListener('scroll', handleScroll, { passive: true })
    // 监听触摸事件，用于临时显示导航栏
    window.addEventListener('touchstart', handleTouchStart, { passive: true })

    // 页面加载时开始导航保护
    startNavigationProtection()
  })

  // 监听路由变化，开始导航保护
  watch(
    () => route.path,
    () => {
      startNavigationProtection()
    }
  )

  onUnmounted(() => {
    window.removeEventListener('scroll', handleScroll)
    window.removeEventListener('touchstart', handleTouchStart)
    if (hideTimeout) {
      clearTimeout(hideTimeout)
    }
    if (navigationProtectionTimeout) {
      clearTimeout(navigationProtectionTimeout)
    }
  })

  return {
    isHidden
  }
}
