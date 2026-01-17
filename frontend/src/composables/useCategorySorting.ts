import { ref, computed } from 'vue'
import { api } from '@/api'

// 全局相册分类排序设置
const categorySortOrder = ref('')

// 从服务器加载分类排序设置
export const loadCategorySortOrder = async () => {
  try {
    const response = await api.get('/admin/config/album-category-sort-order')
    categorySortOrder.value = response.data.albumCategorySortOrder || ''
  } catch (error) {
    console.warn('获取相册分类排序设置失败:', error)
    categorySortOrder.value = ''
  }
}

// 根据排序设置对分类进行排序
export const sortCategories = (categories: string[]): string[] => {
  if (!categorySortOrder.value.trim()) {
    return [...categories].sort()
  }

  // 支持中英文逗号、空格作为分隔符
  const separators = /[，,\s]+/
  const sortOrder = categorySortOrder.value.split(separators).map(s => s.trim()).filter(s => s)
  const sortedCategories: string[] = []
  const remainingCategories: string[] = []

  // 先添加排序中指定的分类
  for (const category of sortOrder) {
    if (categories.includes(category)) {
      sortedCategories.push(category)
    }
  }

  // 然后添加剩余的分类
  for (const category of categories) {
    if (!sortedCategories.includes(category)) {
      remainingCategories.push(category)
    }
  }

  // 对剩余的分类进行字母排序
  remainingCategories.sort()

  return [...sortedCategories, ...remainingCategories]
}

// 获取当前的排序设置
export const getCategorySortOrder = () => categorySortOrder.value

// 设置排序（用于测试或直接设置）
export const setCategorySortOrder = (order: string) => {
  categorySortOrder.value = order
}
