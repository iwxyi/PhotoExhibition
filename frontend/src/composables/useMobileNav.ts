import { ref, onMounted, onUnmounted } from 'vue'

export function useMobileNav() {
  const isMobile = ref(false)

const updateMobileStatus = () => {
  isMobile.value = window.innerWidth < 640 // sm breakpoint - typical mobile size
}

  onMounted(() => {
    updateMobileStatus()
    window.addEventListener('resize', updateMobileStatus)
  })

  onUnmounted(() => {
    window.removeEventListener('resize', updateMobileStatus)
  })

  return {
    isMobile
  }
}
