import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { publicUserApi } from '@/api'

export const usePublicSiteStore = defineStore('publicSite', () => {
  const slug = ref<string | null>(null)
  const nickname = ref<string | null>(null)
  const projectNameZh = ref<string | null>(null)
  const projectNameEn = ref<string | null>(null)
  const avatarPath = ref<string | null>(null)
  const loading = ref(false)

  const displayTitle = computed(() => projectNameZh.value || projectNameEn.value || null)

  const reset = () => {
    slug.value = null
    nickname.value = null
    projectNameZh.value = null
    projectNameEn.value = null
    avatarPath.value = null
  }

  const fetchBySlug = async (userSlug?: string | null) => {
    if (!userSlug) {
      reset()
      return null
    }
    if (slug.value === userSlug && (projectNameZh.value || projectNameEn.value || nickname.value)) {
      return {
        slug: slug.value,
        nickname: nickname.value,
        projectNameZh: projectNameZh.value,
        projectNameEn: projectNameEn.value,
        avatarPath: avatarPath.value
      }
    }
    loading.value = true
    try {
      const { data } = await publicUserApi.getProfile(userSlug)
      slug.value = data.slug
      nickname.value = data.nickname || null
      projectNameZh.value = data.projectNameZh || null
      projectNameEn.value = data.projectNameEn || null
      avatarPath.value = data.avatarPath || null
      return data
    } catch {
      reset()
      return null
    } finally {
      loading.value = false
    }
  }

  return {
    slug,
    nickname,
    projectNameZh,
    projectNameEn,
    avatarPath,
    loading,
    displayTitle,
    reset,
    fetchBySlug
  }
})
