<template>
  <div class="min-h-screen bg-white dark:bg-gray-900">
    <nav class="sticky top-0 z-50 bg-white/80 dark:bg-gray-900/80 backdrop-blur-md border-b border-gray-200 dark:border-gray-800">
      <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div class="flex justify-between items-center h-16">
          <router-link to="/" class="text-2xl font-light tracking-wider">摄影展</router-link>
          <button @click="handleBack" class="p-2 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-800">
            返回
          </button>
        </div>
      </div>
    </nav>

    <main class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-12">
      <div v-if="album">
        <div class="mb-12">
          <h1 class="text-4xl font-light mb-4">{{ album.name }}</h1>
          <p v-if="album.description" class="text-gray-600 dark:text-gray-400 mb-4">{{ album.description }}</p>
          <p class="text-sm text-gray-500 dark:text-gray-500">{{ album.photoCount }} 张照片</p>
        </div>

        <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-6">
          <div
            v-for="(photo, idx) in photos"
            :key="photo.id"
            class="photo-card cursor-pointer"
            :style="isTransitioning && transitionPhotoIds.includes(photo.id) ? { visibility: 'hidden', transition: 'none' } : {}"
            @click="openViewer(idx, $event)"
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
        </div>
      </div>
    </main>
    <PhotoViewer
      v-model:visible="viewerVisible"
      :photos="photos"
      :start-index="viewerIndex"
      :origin-rect="viewerOriginRect"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, nextTick, watch, type ComponentPublicInstance } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { usePhotoStore } from '@/stores/photo'
import PhotoViewer from '@/components/PhotoViewer.vue'

const route = useRoute()
const router = useRouter()
const photoStore = usePhotoStore()

const album = computed(() => photoStore.currentAlbum)
const photos = computed(() => photoStore.photos)
const loading = computed(() => photoStore.loading)

const viewerVisible = ref(false)
const viewerIndex = ref(0)
const viewerOriginRect = ref<{ top: number; left: number; width: number; height: number } | null>(null)

const photoRefs = ref<Map<number, HTMLElement>>(new Map())
const isTransitioning = ref(false)
const transitionPhotoIds = ref<number[]>([])
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
  if (viewerVisible.value) {
    viewerVisible.value = false
    return
  }

  // 启动返回动画并立即返回相册列表，由 Home 页面继续完成缩回到封面的效果
  startBackTransitionAndNavigate()
}

const handleKeydown = (e: KeyboardEvent) => {
  if (e.key === 'Escape') {
    if (viewerVisible.value) {
      viewerVisible.value = false
    } else {
      handleBack()
    }
  }
}

// 执行从封面到详情页的 FLIP 动画
const performCoverTransition = async () => {
  const albumId = parseInt(route.params.id as string)
  const storageKey = `album-cover-rects-${albumId}`
  const storedData = sessionStorage.getItem(storageKey)
  
  if (!storedData || photos.value.length === 0) {
    return
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
      return
    }
    
    // 创建临时克隆元素
    transitionClones = []
    // transitionPhotoIds 和 isTransitioning 已经在 onMounted 中设置了
    // 如果 transitions 为空，清理状态
    if (transitions.length === 0) {
      transitionPhotoIds.value = []
      isTransitioning.value = false
    }
    
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
      clone.style.transition = 'all 0.6s cubic-bezier(0.4, 0, 0.2, 1)'
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
    
    // 动画完成后（600ms），无缝切换
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
    }, 600)
    
    // 保存清理定时器，以便在组件卸载时清理
    ;(window as any).__albumTransitionCleanupTimer = cleanupTimer
    
  } catch (error) {
    console.error('执行封面过渡动画失败:', error)
    sessionStorage.removeItem(storageKey)
    isTransitioning.value = false
    transitionPhotoIds.value = []
  }
}

// 启动返回相册列表时的克隆动画（真正的缩回动画在 Home 页执行）
const startBackTransitionAndNavigate = () => {
  const albumId = parseInt(route.params.id as string)
  const storageKey = `album-cover-rects-${albumId}`
  const storedData = sessionStorage.getItem(storageKey)

  // 如果没有封面位置信息，直接返回
  if (!storedData || photos.value.length === 0) {
    router.back()
    return
  }

  try {
    const coverRects: Array<{ photoId: number }> = JSON.parse(storedData)

    const usedPhotoIds: number[] = []

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
      clone.style.transition = 'all 0.6s cubic-bezier(0.4, 0, 0.2, 1)'
      clone.style.willChange = 'transform, width, height, top, left'
      clone.classList.add('album-back-clone')
      clone.dataset.albumId = String(albumId)
      clone.dataset.photoId = String(photoId)

      document.body.appendChild(clone)
      usedPhotoIds.push(photoId)
    }

    // 如果没有创建任何克隆，直接返回
    if (usedPhotoIds.length === 0) {
      router.back()
      return
    }

    // 记录本次返回动画需要用到的相册和照片 ID，供 Home 页继续执行缩回动画
    sessionStorage.setItem(
      'album-back-transition',
      JSON.stringify({
        albumId,
        photoIds: usedPhotoIds
      })
    )
  } catch (error) {
    console.error('启动返回相册列表动画失败:', error)
  } finally {
    // 不等待动画，立刻执行返回路由，让用户感觉是“边返回边动画”
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
  
  // 执行封面过渡动画（不需要等待页面切换动画，因为已禁用）
  await performCoverTransition()
})

onUnmounted(() => {
  window.removeEventListener('keydown', handleKeydown)
  
  // 清理所有临时克隆元素
  transitionClones.forEach(clone => {
    clone.remove()
  })
  transitionClones = []
  
  // 清理定时器
  if ((window as any).__albumTransitionCleanupTimer) {
    clearTimeout((window as any).__albumTransitionCleanupTimer)
    delete (window as any).__albumTransitionCleanupTimer
  }
  if ((window as any).__albumTransitionRemoveTimer) {
    clearTimeout((window as any).__albumTransitionRemoveTimer)
    delete (window as any).__albumTransitionRemoveTimer
  }
  
  // 恢复所有照片的显示
  photoRefs.value.forEach((photoElement) => {
    photoElement.style.visibility = ''
    photoElement.style.pointerEvents = ''
    photoElement.style.transition = ''
  })
  
  // 组件卸载时清除 sessionStorage（如果还没有被清除）
  const albumId = parseInt(route.params.id as string)
  const storageKey = `album-cover-rects-${albumId}`
  sessionStorage.removeItem(storageKey)
})
</script>

