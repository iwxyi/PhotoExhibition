<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { albumApi, personApi } from '@/api'
import { buildPublicPath, stripPublicSlug } from '@/utils/publicRoute'

const route = useRoute()
const router = useRouter()

const keyword = ref('')
const loading = ref(true)
const error = ref('')
const redirectPath = ref('')

// 判断当前是 /a/ 还是 /p/ 路由
const linkType = ref<'album' | 'person'>('album')

onMounted(async () => {
  await handleResolve()
})

watch(() => route.params.keyword, async (newKeyword) => {
  if (newKeyword) {
    await handleResolve()
  }
})

const handleResolve = async () => {
  keyword.value = (route.params.keyword as string) || ''

  // 判断路由类型
  const normalizedPath = stripPublicSlug(route.path)
  if (normalizedPath.startsWith('/a/')) {
    linkType.value = 'album'
  } else if (normalizedPath.startsWith('/p/')) {
    linkType.value = 'person'
  }

  // URL 解码关键词（处理 %20 等编码）
  const decodedKeyword = decodeURIComponent(keyword.value)

  // 如果关键词是纯数字，认为是 ID
  if (/^\d+$/.test(decodedKeyword)) {
    if (linkType.value === 'album') {
      redirectPath.value = buildPublicPath(`/album/${decodedKeyword}`, route.path)
    } else {
      redirectPath.value = buildPublicPath(`/person/${decodedKeyword}`, route.path)
    }
    router.replace(redirectPath.value)
    return
  }

  loading.value = true
  error.value = ''

  try {
    if (linkType.value === 'album') {
      // 搜索相册
        const response = await albumApi.searchByName(decodedKeyword)
        if (response.data && response.data.id) {
        redirectPath.value = buildPublicPath(`/album/${response.data.id}`, route.path)
        router.replace(redirectPath.value)
      } else {
        error.value = '未找到匹配的相册'
      }
    } else {
      // 搜索人物
      const response = await personApi.searchByName(decodedKeyword)
      if (response.data && response.data.id) {
        redirectPath.value = buildPublicPath(`/person/${response.data.id}`, route.path)
        router.replace(redirectPath.value)
      } else {
        error.value = '未找到匹配的人物'
      }
    }
  } catch (e: any) {
    error.value = e.response?.data?.message || '搜索失败，请稍后重试'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="min-h-screen bg-gray-100 dark:bg-gray-900 flex items-center justify-center">
    <div class="text-center">
      <!-- 加载状态 -->
      <div v-if="loading" class="flex flex-col items-center">
        <div class="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-500 mb-4"></div>
        <p class="text-gray-600 dark:text-gray-400">
          正在搜索{{ linkType === 'album' ? '相册' : '人物' }}: {{ keyword }}
        </p>
      </div>

      <!-- 错误状态 -->
      <div v-else-if="error" class="flex flex-col items-center">
        <svg class="w-16 h-16 text-red-500 mb-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
            d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
        </svg>
        <p class="text-red-500 text-lg mb-2">{{ error }}</p>
        <p class="text-gray-500 dark:text-gray-400 text-sm">
          关键词: {{ keyword }}
        </p>
        <button
          @click="router.push(buildPublicPath('/', route.path))"
          class="mt-4 px-4 py-2 bg-blue-500 hover:bg-blue-600 text-white rounded-lg transition-colors"
        >
          返回首页
        </button>
      </div>
    </div>
  </div>
</template>
