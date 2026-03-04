<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRoute } from 'vue-router'
import { albumApi } from '@/api'

interface AlbumDTO {
  id: number
  name: string
  description?: string
  path?: string
  coverImageId?: number
  coverImagePath?: string
  photoCount?: number
}

interface PersonSummary {
  id: number
  name: string
  samplePhotoId?: number
  sampleThumbnailPath?: string
  faceCount?: number
}

const route = useRoute()

const keyword = ref('')
const loading = ref(true)
const albums = ref<AlbumDTO[]>([])
const persons = ref<PersonSummary[]>([])
const hasSearched = ref(false)

onMounted(async () => {
  keyword.value = (route.params.keyword as string) || ''
  if (keyword.value) {
    await search()
  } else {
    loading.value = false
  }
})

const search = async () => {
  loading.value = true
  hasSearched.value = true

  try {
    const decodedKeyword = decodeURIComponent(keyword.value)
    const response = await albumApi.searchAll(decodedKeyword)
    albums.value = response.data.albums || []
    persons.value = response.data.persons || []
  } catch (e) {
    console.error('搜索失败:', e)
    albums.value = []
    persons.value = []
  } finally {
    loading.value = false
  }
}

const hasResults = computed(() => albums.value.length > 0 || persons.value.length > 0)

const getPersonPhotoUrl = (person: PersonSummary) => {
  if (person.sampleThumbnailPath) {
    return `/api/photos/${person.sampleThumbnailPath}`
  }
  return ''
}

const getAlbumCoverUrl = (album: AlbumDTO) => {
  if (album.coverImagePath) {
    return `/api/photos/${album.coverImagePath}`
  }
  if (album.coverImageId) {
    return `/api/photos/thumbnail/${album.coverImageId}`
  }
  return ''
}
</script>

<template>
  <div class="min-h-screen bg-gray-100 dark:bg-gray-900">
    <div class="container mx-auto px-4 py-8">
      <!-- 搜索标题 -->
      <div class="mb-8">
        <h1 class="text-3xl font-bold text-gray-800 dark:text-white mb-2">
          搜索结果
        </h1>
        <p class="text-gray-600 dark:text-gray-400" v-if="keyword">
          关键词: {{ decodeURIComponent(keyword) }}
        </p>
      </div>

      <!-- 加载状态 -->
      <div v-if="loading" class="flex justify-center py-20">
        <div class="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-500"></div>
      </div>

      <!-- 无结果 -->
      <div v-else-if="hasSearched && !hasResults" class="text-center py-20">
        <svg class="w-16 h-16 text-gray-400 mx-auto mb-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
            d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
        </svg>
        <p class="text-gray-500 dark:text-gray-400 text-lg">
          未找到相关结果
        </p>
        <p class="text-gray-400 dark:text-gray-500 mt-2">
          试试其他关键词？
        </p>
      </div>

      <!-- 有结果 -->
      <div v-else>
        <!-- 人物结果 -->
        <div v-if="persons.length > 0" class="mb-10">
          <h2 class="text-xl font-semibold text-gray-800 dark:text-white mb-4 flex items-center">
            <svg class="w-6 h-6 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z" />
            </svg>
            人物 ({{ persons.length }})
          </h2>
          <div class="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 gap-4">
            <router-link
              v-for="person in persons"
              :key="person.id"
              :to="`/p/${person.id}`"
              class="block group"
            >
              <div class="bg-white dark:bg-gray-800 rounded-lg overflow-hidden shadow-md hover:shadow-lg transition-shadow">
                <div class="aspect-square bg-gray-200 dark:bg-gray-700 relative">
                  <img
                    v-if="getPersonPhotoUrl(person)"
                    :src="getPersonPhotoUrl(person)"
                    :alt="person.name"
                    class="w-full h-full object-cover"
                  />
                  <div v-else class="w-full h-full flex items-center justify-center">
                    <svg class="w-12 h-12 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                        d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z" />
                    </svg>
                  </div>
                </div>
                <div class="p-3">
                  <h3 class="font-medium text-gray-800 dark:text-white truncate group-hover:text-blue-500 transition-colors">
                    {{ person.name }}
                  </h3>
                  <p class="text-sm text-gray-500 dark:text-gray-400">
                    {{ person.faceCount || 0 }} 张照片
                  </p>
                </div>
              </div>
            </router-link>
          </div>
        </div>

        <!-- 相册结果 -->
        <div v-if="albums.length > 0">
          <h2 class="text-xl font-semibold text-gray-800 dark:text-white mb-4 flex items-center">
            <svg class="w-6 h-6 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z" />
            </svg>
            相册 ({{ albums.length }})
          </h2>
          <div class="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 gap-4">
            <router-link
              v-for="album in albums"
              :key="album.id"
              :to="`/a/${album.id}`"
              class="block group"
            >
              <div class="bg-white dark:bg-gray-800 rounded-lg overflow-hidden shadow-md hover:shadow-lg transition-shadow">
                <div class="aspect-square bg-gray-200 dark:bg-gray-700 relative">
                  <img
                    v-if="getAlbumCoverUrl(album)"
                    :src="getAlbumCoverUrl(album)"
                    :alt="album.name"
                    class="w-full h-full object-cover"
                  />
                  <div v-else class="w-full h-full flex items-center justify-center">
                    <svg class="w-12 h-12 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                        d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z" />
                    </svg>
                  </div>
                  <!-- 照片数量标签 -->
                  <div class="absolute bottom-2 right-2 bg-black/60 text-white text-xs px-2 py-1 rounded">
                    {{ album.photoCount || 0 }}
                  </div>
                </div>
                <div class="p-3">
                  <h3 class="font-medium text-gray-800 dark:text-white truncate group-hover:text-blue-500 transition-colors">
                    {{ album.name }}
                  </h3>
                  <p v-if="album.description" class="text-sm text-gray-500 dark:text-gray-400 truncate">
                    {{ album.description }}
                  </p>
                </div>
              </div>
            </router-link>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
