<template>
  <div class="min-h-screen admin-shell text-white">
    <div class="max-w-5xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
      <div class="flex items-center justify-between mb-6">
        <h1 class="text-2xl font-light">标签管理</h1>
        <div class="space-x-3">
          <button @click="load" :disabled="loading" class="btn-primary disabled:opacity-50">刷新</button>
          <router-link to="/admin" class="px-4 py-2 bg-gray-900/70 hover:bg-gray-700 rounded-lg border border-white/10 transition-colors">返回</router-link>
        </div>
      </div>

      <div class="glass-panel p-4 flex flex-col max-h-[calc(100vh-140px)]">
        <!-- 顶部操作栏 -->
        <div class="flex flex-wrap items-center gap-3 mb-4 flex-shrink-0">
          <div class="flex flex-wrap items-center gap-3">
            <input
              v-model="keyword"
              placeholder="搜索标签"
              class="px-3 py-2 bg-gray-700 border border-gray-600 rounded w-64 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
            <button
              @click="load"
              :disabled="loading"
              class="px-4 py-2 bg-gray-700 hover:bg-gray-600 rounded-lg text-sm disabled:opacity-50"
            >
              查询
            </button>
          </div>
          <div class="ml-auto">
            <button
              @click="selectAll"
              :disabled="loading || filteredTags.length === 0"
              class="px-3 py-1.5 bg-gray-700 hover:bg-gray-600 rounded text-xs disabled:opacity-40 mr-2"
            >
              全选
            </button>
            <button
              @click="invertSelection"
              :disabled="loading || filteredTags.length === 0"
              class="px-3 py-1.5 bg-gray-700 hover:bg-gray-600 rounded text-xs disabled:opacity-40 mr-3"
            >
              反选
            </button>
            <button
              @click="deleteSelected"
              :disabled="selectedIds.length === 0 || loading"
              class="px-4 py-2 bg-red-600 hover:bg-red-700 rounded-lg text-sm disabled:opacity-50"
            >
              删除 ({{ selectedIds.length }})
            </button>
          </div>
        </div>

        <!-- 自适应列数的紧凑标签卡片（内部滚动，不影响外层框架） -->
        <div
          ref="listContainer"
          class="flex-1 min-h-0 overflow-y-auto pr-1"
          tabindex="0"
          @keydown.stop="onKeydown"
          @click="onContainerClick"
          @mousedown.prevent="onContainerMouseDown"
        >
          <div class="grid gap-2 md:gap-3 [grid-template-columns:repeat(auto-fill,minmax(180px,1fr))]">
          <div
            v-for="(t, idx) in filteredTags"
            :key="t.id"
            class="tag-item bg-gray-700/70 rounded-lg px-3 py-2 flex items-center justify-between cursor-pointer transition-colors select-none"
            :class="isSelected(t.id) ? 'ring-2 ring-blue-400 bg-gray-700' : 'hover:bg-gray-700'"
            :ref="el => setTagItemRef(el, idx)"
            @click="handleItemClick($event, t, idx)"
            @dblclick.stop="openTag(t)"
          >
            <div class="flex items-center gap-2">
              <div>
                <div class="text-xs font-semibold flex items-center gap-2">
                  <span class="truncate max-w-[9rem] text-gray-100">
                    {{ t.name }}
                  </span>
                  <span class="text-[11px] text-gray-300 whitespace-nowrap">({{ t.photoCount ?? 0 }})</span>
                </div>
                <div class="text-[11px] text-gray-400">ID: {{ t.id }}</div>
              </div>
            </div>
            <div class="flex items-center gap-2">
              <div
                v-if="t.color"
                class="w-5 h-5 rounded-full border border-white/30"
                :style="{ background: t.color }"
              ></div>
              <button
                class="p-1 rounded hover:bg-amber-500/10 text-amber-300"
                @click.stop="editOne(t)"
                title="编辑标签"
              >
                <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path
                    stroke-linecap="round"
                    stroke-linejoin="round"
                    stroke-width="2"
                    d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z"
                  />
                </svg>
              </button>
            </div>
          </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '@/api'

const tags = ref<any[]>([])
const loading = ref(false)
const keyword = ref('')
const selectedIds = ref<number[]>([])
const lastClickedIndex = ref<number | null>(null)
const router = useRouter()
const listContainer = ref<HTMLElement | null>(null)
const tagItemEls = ref<HTMLElement[]>([])

const load = async () => {
  loading.value = true
  try {
    const res = await api.get('/tags')
    const list = res.data || []
    tags.value = list
  } finally {
    loading.value = false
  }
}

const filteredTags = computed(() => {
  if (!keyword.value.trim()) return tags.value
  const kw = keyword.value.trim().toLowerCase()
  return tags.value.filter((t: any) => (t.name || '').toLowerCase().includes(kw))
})

const isSelected = (id: number): boolean => {
  return selectedIds.value.includes(id)
}

const clearSelection = () => {
  selectedIds.value = []
  lastClickedIndex.value = null
}

const selectAll = () => {
  selectedIds.value = filteredTags.value.map((t: any) => t.id)
  if (filteredTags.value.length > 0) {
    lastClickedIndex.value = filteredTags.value.length - 1
  }
}

const invertSelection = () => {
  const current = new Set(selectedIds.value)
  selectedIds.value = filteredTags.value
    .filter((t: any) => !current.has(t.id))
    .map((t: any) => t.id)
}

const setTagItemRef = (el: HTMLElement | null, index: number) => {
  if (!el) return
  tagItemEls.value[index] = el
}

// 框选相关状态
const isDraggingBox = ref(false)
const dragStartPoint = ref<{ x: number; y: number }>({ x: 0, y: 0 })
const dragCurrentPoint = ref<{ x: number; y: number }>({ x: 0, y: 0 })
const dragBaseSelection = ref<number[]>([])
const justFinishedDrag = ref(false)

const updateSelectionByBox = () => {
  if (!listContainer.value || !isDraggingBox.value) return
  const containerRect = listContainer.value.getBoundingClientRect()

  const x1 = dragStartPoint.value.x
  const y1 = dragStartPoint.value.y
  const x2 = dragCurrentPoint.value.x
  const y2 = dragCurrentPoint.value.y
  const left = Math.min(x1, x2)
  const right = Math.max(x1, x2)
  const top = Math.min(y1, y2)
  const bottom = Math.max(y1, y2)

  const baseSet = new Set(dragBaseSelection.value)

  filteredTags.value.forEach((t: any, idx: number) => {
    const el = tagItemEls.value[idx]
    if (!el) return
    const r = el.getBoundingClientRect()

    const overlap = !(r.right < left || r.left > right || r.bottom < top || r.top > bottom)
    if (overlap) {
      baseSet.add(t.id)
    }
  })

  selectedIds.value = Array.from(baseSet)
}

const onContainerMouseDown = (e: MouseEvent) => {
  if (e.button !== 0) return
  if (!listContainer.value) return

  // 如果点击在标签卡片上，则不启用框选（交给单个选择逻辑处理）
  const target = e.target as HTMLElement
  if (target.closest('.tag-item')) {
    return
  }

  isDraggingBox.value = true
  dragStartPoint.value = { x: e.clientX, y: e.clientY }
  dragCurrentPoint.value = { x: e.clientX, y: e.clientY }
  // Ctrl/Cmd 拖拽时在原有选择基础上追加，否则从空开始
  dragBaseSelection.value = (e.ctrlKey || e.metaKey) ? [...selectedIds.value] : []

  const onMove = (ev: MouseEvent) => {
    if (!isDraggingBox.value) return
    dragCurrentPoint.value = { x: ev.clientX, y: ev.clientY }
    updateSelectionByBox()
  }

  const onUp = () => {
    isDraggingBox.value = false
    justFinishedDrag.value = true
    // 延迟重置，避免紧随其后的 click 事件清空选择
    setTimeout(() => {
      justFinishedDrag.value = false
    }, 50)
    window.removeEventListener('mousemove', onMove)
    window.removeEventListener('mouseup', onUp)
  }

  window.addEventListener('mousemove', onMove)
  window.addEventListener('mouseup', onUp)
}

const onContainerClick = (e: MouseEvent) => {
  // 如果刚完成框选，忽略这次 click（避免清空刚框选的结果）
  if (justFinishedDrag.value) return
  const target = e.target as HTMLElement
  // 如果点击在标签卡片或其子元素上，则不清空（交给 item 点击逻辑）
  if (target.closest('.tag-item')) return
  clearSelection()
}

const handleItemClick = (event: MouseEvent, tag: any, index: number) => {
  event.preventDefault()
  const ctrl = event.ctrlKey || event.metaKey
  const shift = event.shiftKey

  if (shift && lastClickedIndex.value !== null) {
    // Shift 连续选择
    const start = Math.min(lastClickedIndex.value, index)
    const end = Math.max(lastClickedIndex.value, index)
    const rangeIds = filteredTags.value.slice(start, end + 1).map((t: any) => t.id)

    if (ctrl) {
      // Ctrl + Shift：在原有基础上追加范围
      const set = new Set(selectedIds.value)
      rangeIds.forEach(id => set.add(id))
      selectedIds.value = Array.from(set)
    } else {
      // 仅 Shift：直接用范围替换
      selectedIds.value = rangeIds
    }
  } else if (ctrl) {
    // Ctrl 单个多选/反选
    const set = new Set(selectedIds.value)
    if (set.has(tag.id)) set.delete(tag.id)
    else set.add(tag.id)
    selectedIds.value = Array.from(set)
  } else {
    // 普通点击：只选当前
    selectedIds.value = [tag.id]
  }

  lastClickedIndex.value = index
}

const onKeydown = (e: KeyboardEvent) => {
  // Ctrl/Cmd + A 全选
  if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'a') {
    e.preventDefault()
    selectAll()
    return
  }

  // Esc 取消选择（列表容器内的局部处理，保留现有行为）
  if (e.key === 'Escape') {
    clearSelection()
  }
}

const handleGlobalKeydown = (e: KeyboardEvent) => {
  if (e.key === 'Escape') {
    // 如果有选中项，先清空选择
    if (selectedIds.value.length > 0) {
      clearSelection()
      return
    }
    // 否则返回首页
    router.push('/admin')
  }
}

const editOne = async (t: any) => {
  const newName = window.prompt('修改标签名称', t.name)
  if (newName === null || newName.trim() === '') return
  const newColor = window.prompt('修改颜色(可选)', t.color || '')
  await api.put(`/tags/${t.id}`, { name: newName.trim(), color: newColor || null })
  await load()
}

const deleteSelected = async () => {
  if (selectedIds.value.length === 0) return
  if (!window.confirm(`确定删除选中的 ${selectedIds.value.length} 个标签？`)) return
  for (const id of selectedIds.value) {
    await api.delete(`/tags/${id}`)
  }
  selectedIds.value = []
  await load()
}

const openTag = (tag: any) => {
  if (!tag?.id) return
  const route = router.resolve({ path: '/wall', query: { tagId: tag.id, tagName: tag.name } })
  window.open(route.href, '_blank')
}

onMounted(() => {
  load()
  window.addEventListener('keydown', handleGlobalKeydown)
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', handleGlobalKeydown)
})
</script>


