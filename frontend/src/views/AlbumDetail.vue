<template>
  <div class="min-h-screen bg-white dark:bg-gray-900">
    <nav class="sticky top-0 z-50 bg-white/80 dark:bg-gray-900/80 backdrop-blur-md border-b border-gray-200 dark:border-gray-800">
      <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div class="flex justify-between items-center h-16">
          <router-link to="/" class="text-2xl font-light tracking-wider">摄影展</router-link>
          <button @click="$router.back()" class="p-2 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-800">
            返回
          </button>
        </div>
      </div>
    </nav>

    <main class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-12">
      <div v-if="loading" class="flex justify-center items-center h-96">
        <div class="animate-spin rounded-full h-12 w-12 border-t-2 border-b-2 border-gray-900 dark:border-white"></div>
      </div>

      <div v-else-if="album">
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
            @click="openViewer(idx)"
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
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { usePhotoStore } from '@/stores/photo'
import PhotoViewer from '@/components/PhotoViewer.vue'

const route = useRoute()
const photoStore = usePhotoStore()

const album = computed(() => photoStore.currentAlbum)
const photos = computed(() => photoStore.photos)
const loading = computed(() => photoStore.loading)

const viewerVisible = ref(false)
const viewerIndex = ref(0)

const getImageUrl = (photo: any) => {
  if (photo.webpPath) {
    return `/api/files${photo.webpPath}`
  }
  if (photo.thumbnailPath) {
    return `/api/files${photo.thumbnailPath}`
  }
  return `/api/files${photo.originalPath}`
}

const openViewer = (idx: number) => {
  viewerIndex.value = idx
  viewerVisible.value = true
}

onMounted(async () => {
  const albumId = parseInt(route.params.id as string)
  await photoStore.fetchAlbumById(albumId)
  await photoStore.fetchPhotosByAlbum(albumId)
})
</script>

