<template>
  <div class="min-h-screen transition-colors duration-1000" :style="backgroundStyle">
    <nav class="fixed top-4 right-4 z-50">
      <button @click="handleBack" class="bg-black/20 backdrop-blur-md text-white px-4 py-2 rounded-full hover:bg-black/30 transition-colors duration-200">
        返回
      </button>
    </nav>

    <main class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-12">
        <div v-if="album">
          <div class="mb-12">
            <h1 class="text-4xl font-light mb-4" :style="textStyle">{{ album.name }}</h1>
            <p v-if="album.description" class="mb-4" :style="{ ...textStyle, opacity: 0.8 }">{{ album.description }}</p>
            <p class="text-sm" :style="{ ...textStyle, opacity: 0.6 }">{{ album.photoCount }} 张照片</p>
          </div>

        <MasonryLayout
          :items="masonryItems"
          :column-count="columnCount"
          :gap="24"
        >
          <template #default="{ item: photo, index }">
            <div
              class="photo-card cursor-pointer"
              :style="getPhotoStyle(photo)"
              @click="openViewer(index, $event)"
              :ref="(el: Element | ComponentPublicInstance | null) => setPhotoRef(el as Element | null, photo.id)"
            >
              <img
                :src="getImageUrl(photo)"
                :alt="photo.filename"
                class="photo-image w-full h-full"
                loading="lazy"
              />
              <div class="gradient-overlay">
                <div class="absolute bottom-0 left-0 right-0 p-4 text-white">
                  <p class="text-sm font-light">{{ photo.filename }}</p>
                </div>
              </div>
            </div>
          </template>
        </MasonryLayout>
      </div>
    </main>
    <PhotoViewer
      v-model:visible="viewerVisible"
      :photos="photos"
      :start-index="viewerIndex"
      :origin-rect="viewerOriginRect"
    />

    <!-- 氛围特效 -->
    <AtmosphereEffects :effects="albumAtmosphereEffects" />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, nextTick, type ComponentPublicInstance } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { usePhotoStore } from '@/stores/photo'
import { useUiSettings } from '@/composables/useUiSettings'
import { useThemeStore } from '@/stores/theme'
import PhotoViewer from '@/components/PhotoViewer.vue'
import AtmosphereEffects from '@/components/AtmosphereEffects.vue'
import MasonryLayout from '@/components/MasonryLayout.vue'

const route = useRoute()
const router = useRouter()
const photoStore = usePhotoStore()

const album = computed(() => photoStore.currentAlbum)
const photos = computed(() => photoStore.photos)

const { atmosphereEnabled } = useUiSettings()

// 获取主题store
const themeStore = useThemeStore()

// 背景样式（基于相册的背景颜色或默认主题，支持氛围开关）
const backgroundStyle = computed(() => {
  if (atmosphereEnabled.value && album.value?.backgroundColor) {
    // 启用氛围时使用相册的背景颜色
    const baseColor = album.value.backgroundColor!
    return {
      backgroundColor: baseColor
    }
  } else if (!atmosphereEnabled.value) {
    // 关闭氛围时使用纯色背景，与主页一致
    return {
      backgroundColor: themeStore.isDark ? '#000000' : '#ffffff'
    }
  }
  return {}
})


// 文字样式（确保在任何背景下都有足够对比度）
const textStyle = computed(() => {
  if (atmosphereEnabled.value && album.value?.backgroundColor) {
    // 启用氛围时，根据相册背景色选择文字颜色
    const bgBrightness = getBrightness(album.value.backgroundColor!)
    const isLightBackground = bgBrightness > 0.5

    if (isLightBackground) {
      // 浅色背景 -> 使用深色文字
      return { color: '#1a1a1a' }
    } else {
      // 深色背景 -> 使用浅色文字
      return { color: '#ffffff' }
    }
  } else if (!atmosphereEnabled.value) {
    // 关闭氛围时使用默认的主题文字颜色
    return {
      color: themeStore.isDark ? '#ffffff' : '#1a1a1a'
    }
  }
  return {}
})

// 氛围特效列表
const albumAtmosphereEffects = computed(() => {
  if (!atmosphereEnabled.value) {
    return []
  }
  return album.value?.atmosphereEffects || [] as any[]
})

// 计算列数（响应式）
const columnCount = computed(() => {
  if (typeof window === 'undefined') return 3

  const width = window.innerWidth
  if (width >= 1280) return 4
  if (width >= 1024) return 3
  if (width >= 640) return 2
  return 1
})

// 转换照片数据为瀑布流组件需要的格式
const masonryItems = computed(() => {
  return photos.value.map(photo => ({
    id: photo.id,
    data: photo,
    width: photo.width || 1,
    height: photo.height || 1
  }))
})

const viewerVisible = ref(false)
const viewerIndex = ref(0)
const viewerOriginRect = ref<{ top: number; left: number; width: number; height: number } | null>(null)

const photoRefs = ref<Map<number, HTMLElement>>(new Map())
const isTransitioning = ref(false)
const transitionPhotoIds = ref<number[]>([])
const remainingPhotosVisible = ref(false)
const remainingPhotoIndexes = ref<Map<number, number>>(new Map())
let transitionClones: HTMLElement[] = []

const getImageUrl = (photo: any) => {
  if (photo.webpPath) {
    return `/api/files${photo.webpPath}`
  }
  if (photo.thumbnailPath) {
    return `/api/files${photo.thumbnailPath}`
  }
  return `/api/files${photo.originalPath}`
}

const getPhotoStyle = (photo: any) => {
  const photoId = photo.id
  // 如果是封面图片且正在过渡，隐藏它们
  if (isTransitioning.value && transitionPhotoIds.value.includes(photoId)) {
    return {
      visibility: 'hidden' as const,
      transition: 'none'
    }
  }

  // 如果动画还没有开始，为剩余图片添加初始动画样式
  if (!remainingPhotosVisible.value && !transitionPhotoIds.value.includes(photoId)) {
    return {
      opacity: '0',
      transform: 'translateY(30px)',
      transition: 'none'
    }
  }

  // 如果动画已经开始，添加渐进动画
  if (remainingPhotosVisible.value && !transitionPhotoIds.value.includes(photoId)) {
    const index = remainingPhotoIndexes.value.get(photoId) || 0
    // 使用非线性延迟：前面的图片延迟少，后面的图片延迟相对更多，但不是完全线性
    const baseDelay = Math.min(index * 20, 80) // 最大延迟80ms，比之前更短
    const randomFactor = Math.random() * 15 // 添加轻微的随机性使动画更自然
    const delay = Math.max(0, baseDelay + randomFactor - 7)

    return {
      opacity: '1',
      transform: 'translateY(0)',
      transition: 'all 0.5s cubic-bezier(0.22, 1, 0.36, 1)',
      transitionDelay: `${delay}ms`
    }
  }

  // 默认样式
  return {}
}

const setPhotoRef = (el: Element | null, photoId: number) => {
  const domEl = el as HTMLElement | null
  if (domEl) {
    photoRefs.value.set(photoId, domEl)
  } else {
    photoRefs.value.delete(photoId)
  }
}

const openViewer = (idx: number, e: MouseEvent) => {
  viewerIndex.value = idx

  // 以图片本身为主，避免外层卡片比查看器中的图片更大导致"从大缩小"的观感
  const img = (e.target as HTMLElement).closest('img') as HTMLImageElement | null
  const rectSource = img || (e.currentTarget as HTMLElement | null)
  if (rectSource) {
    const rect = rectSource.getBoundingClientRect()
    viewerOriginRect.value = {
      top: rect.top,
      left: rect.left,
      width: rect.width,
      height: rect.height
    }
  } else {
    viewerOriginRect.value = null
  }

  viewerVisible.value = true
}

const handleBack = async () => {
  console.log('[AlbumDetail] handleBack called at', Date.now())

  if (viewerVisible.value) {
    viewerVisible.value = false
    return
  }

  // 在路由切换前清理动画状态，让组件卸载更快
  console.log('[AlbumDetail] Cleaning up animation state before navigation at', Date.now())

  // 清理定时器
  if ((window as any).__albumTransitionCleanupTimer) {
    clearTimeout((window as any).__albumTransitionCleanupTimer)
    delete (window as any).__albumTransitionCleanupTimer
  }
  if ((window as any).__albumTransitionRemoveTimer) {
    clearTimeout((window as any).__albumTransitionRemoveTimer)
    delete (window as any).__albumTransitionRemoveTimer
  }

  // 清理临时克隆元素
  transitionClones.forEach(clone => {
    clone.remove()
  })
  transitionClones = []

  // 恢复所有照片的显示状态
  photoRefs.value.forEach((photoElement) => {
    photoElement.style.visibility = ''
    photoElement.style.pointerEvents = ''
    photoElement.style.transition = ''
  })

  // 启动返回动画并立即返回相册列表，由 Home 页面继续完成缩回到封面的效果
  startBackTransitionAndNavigate()
}

const handleKeydown = (e: KeyboardEvent) => {
  if (e.key === 'Escape') {
    e.preventDefault() // 防止浏览器默认行为
    if (viewerVisible.value) {
      // 直接关闭查看器，不要等待
      viewerVisible.value = false
    } else {
      // 使用完整的返回动画逻辑（与按钮点击保持一致）
      handleBack()
    }
  }
}

// 执行从封面到详情页的 FLIP 动画
const performCoverTransition = async (): Promise<boolean> => {
  const albumId = parseInt(route.params.id as string)
  const storageKey = `album-cover-rects-${albumId}`
  const storedData = sessionStorage.getItem(storageKey)

  if (!storedData || photos.value.length === 0) {
    return false
  }
  
  try {
    const coverRects: Array<{ photoId: number; rect: { top: number; left: number; width: number; height: number } }> = JSON.parse(storedData)
    
    // 等待 DOM 更新完成
    await nextTick()
    
    // 找到对应的照片元素
    const transitions: Array<{
      photoId: number
      fromRect: DOMRect
      toRect: DOMRect
      img: HTMLImageElement
    }> = []
    
    for (const { photoId, rect: fromRectData } of coverRects) {
      const photoElement = photoRefs.value.get(photoId)
      if (!photoElement) continue
      
      const img = photoElement.querySelector('img') as HTMLImageElement
      if (!img) continue
      
      const toRect = photoElement.getBoundingClientRect()
      
      transitions.push({
        photoId,
        fromRect: new DOMRect(fromRectData.left, fromRectData.top, fromRectData.width, fromRectData.height),
        toRect,
        img
      })
    }
    
    if (transitions.length === 0) {
      sessionStorage.removeItem(storageKey)
      return true
    }
    
    // 创建临时克隆元素
    transitionClones = []
    // transitionPhotoIds 和 isTransitioning 已经在 onMounted 中设置了
    // 如果 transitions 为空，清理状态
    if (transitions.length === 0) {
      transitionPhotoIds.value = []
      isTransitioning.value = false
      return false
    }
    
    // 在封面动画开始前，同时开始剩余图片的动画
    nextTick(() => {
      remainingPhotosVisible.value = true
    })

    for (const { photoId, fromRect, toRect, img } of transitions) {
      // 确保图片已经加载完成
      if (!img.complete) {
        await new Promise((resolve) => {
          if (img.complete) {
            resolve(undefined)
          } else {
            img.onload = () => resolve(undefined)
            img.onerror = () => resolve(undefined) // 即使加载失败也继续
          }
        })
      }
      
      const clone = img.cloneNode(true) as HTMLImageElement
      // 确保克隆的图片也使用相同的 src
      clone.src = img.src
      clone.style.position = 'fixed'
      clone.style.top = `${fromRect.top}px`
      clone.style.left = `${fromRect.left}px`
      clone.style.width = `${fromRect.width}px`
      clone.style.height = `${fromRect.height}px`
      clone.style.objectFit = 'cover'
      clone.style.zIndex = '9999'
      clone.style.pointerEvents = 'none'
      clone.style.borderRadius = '8px'
      clone.style.boxShadow = '0 4px 6px -1px rgba(0, 0, 0, 0.1), 0 2px 4px -1px rgba(0, 0, 0, 0.06)'
      // 使用更快且非线性的 easing，使打开封面更有弹性且更快
      clone.style.transition = 'all 380ms cubic-bezier(0.22, 1, 0.36, 1)'
      clone.style.willChange = 'transform, width, height, top, left'
      
      document.body.appendChild(clone)
      transitionClones.push(clone)
      
      // 完全隐藏原始图片（使用 visibility 而不是 opacity，避免过渡效果）
      const photoElement = photoRefs.value.get(photoId)
      if (photoElement) {
        photoElement.style.visibility = 'hidden'
        photoElement.style.pointerEvents = 'none'
        photoElement.style.transition = 'none' // 禁用所有过渡效果
      }
      
      // 触发动画
      requestAnimationFrame(() => {
        requestAnimationFrame(() => {
          clone.style.top = `${toRect.top}px`
          clone.style.left = `${toRect.left}px`
          clone.style.width = `${toRect.width}px`
          clone.style.height = `${toRect.height}px`
        })
      })
    }
    
    // 动画完成后（约 420ms），无缝切换
    const cleanupTimer = setTimeout(() => {
      // 使用 requestAnimationFrame 确保在下一帧执行，避免闪烁
      requestAnimationFrame(() => {
        // 先让原始图片可见
        transitions.forEach(({ photoId }) => {
          const photoElement = photoRefs.value.get(photoId)
          if (photoElement) {
            photoElement.style.visibility = 'visible'
            photoElement.style.pointerEvents = ''
          }
        })

        // 在同一帧中立即移除克隆元素
        requestAnimationFrame(() => {
          transitionClones.forEach(clone => {
            clone.remove()
          })
          transitionClones = []

          // 恢复原始图片的样式
          transitions.forEach(({ photoId }) => {
            const photoElement = photoRefs.value.get(photoId)
            if (photoElement) {
              photoElement.style.visibility = ''
              photoElement.style.pointerEvents = ''
              photoElement.style.transition = ''
            }
          })

          isTransitioning.value = false
          transitionPhotoIds.value = []

          // 不要在这里清除 sessionStorage，保留它以便返回时执行反向动画
          // sessionStorage.removeItem(storageKey)
        })
      })
    }, 420)
    
    // 保存清理定时器，以便在组件卸载时清理
    ;(window as any).__albumTransitionCleanupTimer = cleanupTimer
    
    return true
  } catch (error) {
    console.error('执行封面过渡动画失败:', error)
    sessionStorage.removeItem(storageKey)
    isTransitioning.value = false
    transitionPhotoIds.value = []
    return false
  }
}

// 启动返回相册列表时的克隆动画（真正的缩回动画在 Home 页执行）
const startBackTransitionAndNavigate = () => {
  console.log('[AlbumDetail] startBackTransitionAndNavigate called at', Date.now())

  const albumId = parseInt(route.params.id as string)
  const storageKey = `album-cover-rects-${albumId}`
  const storedData = sessionStorage.getItem(storageKey)

  // 如果没有封面位置信息，直接跳转
  if (!storedData || photos.value.length === 0) {
    console.log('[AlbumDetail] No stored data, direct navigation at', Date.now())
    router.back()
    return
  }

  try {
    const coverRects: Array<{ photoId: number }> = JSON.parse(storedData)
    const usedPhotoIds: number[] = []

    // 保存当前滚动位置
    const currentScrollTop = window.scrollY || document.documentElement.scrollTop
    const currentScrollLeft = window.scrollX || document.documentElement.scrollLeft

    console.log('[AlbumDetail] Scroll position saved:', currentScrollTop, 'at', Date.now())

    // 临时禁用滚动
    const preventScroll = (e: Event) => {
      e.preventDefault()
      window.scrollTo(currentScrollLeft, currentScrollTop)
    }

    // 添加滚动事件监听器，强制保持滚动位置
    window.addEventListener('scroll', preventScroll, { passive: false })
    window.addEventListener('wheel', preventScroll, { passive: false })
    window.addEventListener('touchmove', preventScroll, { passive: false })

    console.log('[AlbumDetail] Scroll protection added at', Date.now())

    // 使用 requestAnimationFrame 延迟创建克隆元素，避免影响当前页面布局
    requestAnimationFrame(() => {
      console.log('[AlbumDetail] requestAnimationFrame callback at', Date.now())

      // 为三张封面对应的照片创建克隆元素，停留在当前详情页的位置
      for (const { photoId } of coverRects) {
        const photoElement = photoRefs.value.get(photoId)
        if (!photoElement) continue

        const img = photoElement.querySelector('img') as HTMLImageElement
        if (!img) continue

        const fromRect = photoElement.getBoundingClientRect()

        const clone = img.cloneNode(true) as HTMLImageElement
        clone.src = img.src
        clone.style.position = 'fixed'
        clone.style.top = `${fromRect.top}px`
        clone.style.left = `${fromRect.left}px`
        clone.style.width = `${fromRect.width}px`
        clone.style.height = `${fromRect.height}px`
        clone.style.objectFit = 'cover'
        clone.style.zIndex = '9999'
        clone.style.pointerEvents = 'none'
        clone.style.borderRadius = '8px'
        clone.style.boxShadow = '0 4px 6px -1px rgba(0, 0, 0, 0.1), 0 2px 4px -1px rgba(0, 0, 0, 0.06)'
        // 反向/返回克隆也使用更快且非线性的 easing，保持与打开动画一致
        clone.style.transition = 'all 380ms cubic-bezier(0.22, 1, 0.36, 1)'
        clone.style.willChange = 'transform, width, height, top, left'
        clone.classList.add('album-back-clone')
        clone.dataset.albumId = String(albumId)
        clone.dataset.photoId = String(photoId)

        document.body.appendChild(clone)
        usedPhotoIds.push(photoId)
      }

      console.log('[AlbumDetail] Clones created, count:', usedPhotoIds.length, 'at', Date.now())

      // 如果没有创建任何克隆，直接跳转
      if (usedPhotoIds.length === 0) {
        console.log('[AlbumDetail] No clones created, direct navigation at', Date.now())
        router.back()
        return
      }

      // 记录本次返回动画需要用到的相册和照片 ID，供 Home 页继续执行缩回动画
      sessionStorage.setItem(
        'album-back-transition',
        JSON.stringify({
          albumId,
          photoIds: usedPhotoIds,
          scrollTop: currentScrollTop,
          scrollLeft: currentScrollLeft
        })
      )

      console.log('[AlbumDetail] Session storage set at', Date.now())

      // 立即跳转，让用户感觉响应更快
      console.log('[AlbumDetail] Calling router.back() at', Date.now())
      router.back()

      // 在路由切换后移除滚动防护（使用 setTimeout 确保在下一事件循环中执行）
      setTimeout(() => {
        console.log('[AlbumDetail] Removing scroll protection at', Date.now())
        window.removeEventListener('scroll', preventScroll)
        window.removeEventListener('wheel', preventScroll)
        window.removeEventListener('touchmove', preventScroll)
      }, 0)
    })
  } catch (error) {
    console.error('启动返回相册列表动画失败:', error)
    // 出错时也要跳转
    router.back()
  }
}

onMounted(async () => {
  const albumId = parseInt(route.params.id as string)
  const storageKey = `album-cover-rects-${albumId}`
  const storedData = sessionStorage.getItem(storageKey)
  
  // 如果有需要动画的图片，立即隐藏它们（在数据加载前）
  if (storedData) {
    try {
      const coverRects: Array<{ photoId: number }> = JSON.parse(storedData)
      const photoIdsToHide = coverRects.map(r => r.photoId)
      
      // 在数据加载前，先标记需要隐藏的图片
      transitionPhotoIds.value = photoIdsToHide
      isTransitioning.value = true
    } catch (e) {
      // 忽略解析错误
    }
  }
  
  // 清除可能遗留的上一相册图片，避免在加载新相册前闪现旧内容
  photoStore.photos = []
  photoStore.currentAlbum = null
  await photoStore.fetchAlbumById(albumId)
  await photoStore.fetchPhotosByAlbum(albumId)
  window.addEventListener('keydown', handleKeydown)
  
  // 等待照片元素渲染完成
  await nextTick()
  
  // 如果有需要隐藏的图片，立即隐藏它们
  if (transitionPhotoIds.value.length > 0) {
    transitionPhotoIds.value.forEach(photoId => {
      const photoElement = photoRefs.value.get(photoId)
      if (photoElement) {
        photoElement.style.visibility = 'hidden'
        photoElement.style.transition = 'none'
      }
    })
  }
  
  // 再等待一帧，确保所有图片都已渲染
  await new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)))
  
  // 在开始封面动画之前，先准备剩余图片的动画数据
  let remainingIndex = 0
  photos.value.forEach((photo) => {
    if (!transitionPhotoIds.value.includes(photo.id)) {
      remainingPhotoIndexes.value.set(photo.id, remainingIndex++)
    }
  })

  // 执行封面过渡动画（不需要等待页面切换动画，因为已禁用）
  const hasCoverTransition = await performCoverTransition()

  // 如果没有封面动画，直接开始剩余图片动画
  if (!hasCoverTransition) {
    remainingPhotosVisible.value = true
  }
})

onUnmounted(() => {
  console.log('[AlbumDetail] onUnmounted called at', Date.now())

  window.removeEventListener('keydown', handleKeydown)

  // 注意：动画状态已经在 handleBack 中提前清理了，这里只需要处理可能遗漏的情况
  console.log('[AlbumDetail] onUnmounted completed at', Date.now())
})

// 颜色处理工具函数
const hexToRgb = (hex: string) => {
  const result = /^#?([a-f\d]{2})([a-f\d]{2})([a-f\d]{2})$/i.exec(hex)
  return result ? {
    r: parseInt(result[1], 16),
    g: parseInt(result[2], 16),
    b: parseInt(result[3], 16)
  } : null
}



const getBrightness = (hex: string) => {
  const rgb = hexToRgb(hex)
  if (!rgb) return 0.5

  // 使用相对亮度公式
  return (rgb.r * 0.299 + rgb.g * 0.587 + rgb.b * 0.114) / 255
}
</script>

