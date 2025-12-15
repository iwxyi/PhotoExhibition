import { defineStore } from 'pinia'
import { ref, watch } from 'vue'

export type Language = 'zh' | 'en'

export const useLanguageStore = defineStore('language', () => {
  const language = ref<Language>(
    (localStorage.getItem('language') as Language) || 'zh'
  )

  const setLanguage = (lang: Language) => {
    language.value = lang
    localStorage.setItem('language', lang)
    // 通知后端更新语言设置（如果需要）
    updateBackendLanguage(lang)
  }

  const updateBackendLanguage = async (lang: Language) => {
    try {
      // 可以通过API更新后端配置，或者仅在前端使用
      // 这里暂时只在前端使用，后端通过配置文件设置
      console.log('Language changed to:', lang)
    } catch (error) {
      console.error('Failed to update backend language:', error)
    }
  }

  // 监听变化
  watch(language, (newLang) => {
    localStorage.setItem('language', newLang)
  })

  return {
    language,
    setLanguage
  }
})

