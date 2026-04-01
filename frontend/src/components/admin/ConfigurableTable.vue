<template>
  <div class="space-y-3">
    <div class="flex items-center justify-between gap-3">
      <div class="text-xs text-gray-500">
        支持按列排序、显示隐藏和拖动列顺序，偏好会自动保存。
      </div>
      <div class="relative">
        <button
          type="button"
          class="px-3 py-2 rounded-lg bg-gray-800 hover:bg-gray-700 text-sm border border-white/10"
          @click="columnPanelOpen = !columnPanelOpen"
        >
          列设置
        </button>
        <div
          v-if="columnPanelOpen"
          class="absolute right-0 z-20 mt-2 w-72 rounded-xl border border-white/10 bg-gray-950/95 p-3 shadow-2xl"
        >
          <div class="mb-2 text-sm text-gray-200">列显示</div>
          <div class="space-y-2">
            <label
              v-for="column in orderedColumns"
              :key="column.key"
              class="flex items-center justify-between gap-3 rounded-lg px-2 py-1.5 hover:bg-white/5"
            >
              <span class="text-sm text-gray-300">{{ column.label }}</span>
              <input
                :checked="!hiddenColumnSet.has(column.key)"
                type="checkbox"
                class="w-4 h-4 rounded"
                @change="toggleColumn(column.key)"
              />
            </label>
          </div>
        </div>
      </div>
    </div>

    <div v-if="loading" class="text-sm text-gray-400">{{ loadingText }}</div>
    <div v-else-if="!sortedRows.length" class="text-sm text-gray-400">{{ emptyText }}</div>
    <div v-else class="overflow-auto rounded-2xl border border-white/10">
      <table class="w-full text-sm text-gray-200">
        <thead class="bg-white/5 text-gray-400 border-b border-white/10">
          <tr>
            <th
              v-for="column in visibleColumns"
              :key="column.key"
              draggable="true"
              class="px-3 py-3 text-left whitespace-nowrap select-none"
              :class="[column.headerClass, draggingColumnKey === column.key ? 'opacity-60' : '']"
              @dragstart="startDrag(column.key)"
              @dragover.prevent
              @drop="dropColumn(column.key)"
            >
              <button
                v-if="column.sortable"
                type="button"
                class="inline-flex items-center gap-1 hover:text-white"
                @click="toggleSort(column.key)"
              >
                <span>{{ column.label }}</span>
                <span class="text-xs">{{ sortIndicator(column.key) }}</span>
              </button>
              <span v-else>{{ column.label }}</span>
            </th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="row in sortedRows"
            :key="resolveRowKey(row)"
            class="border-b border-white/5 align-top last:border-b-0"
          >
            <td
              v-for="column in visibleColumns"
              :key="column.key"
              class="px-3 py-3"
              :class="column.cellClass"
            >
              <slot
                :name="`cell-${column.key}`"
                :row="row"
                :column="column"
                :value="row[column.key]"
              >
                {{ formatDefaultValue(row[column.key]) }}
              </slot>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import type { ConfigurableTablePreference } from '@/api'

export interface ConfigurableColumn {
  key: string
  label: string
  sortable?: boolean
  defaultVisible?: boolean
  headerClass?: string
  cellClass?: string
}

const props = withDefaults(defineProps<{
  columns: ConfigurableColumn[]
  rows: Record<string, any>[]
  rowKey?: string | ((row: Record<string, any>) => string | number)
  loading?: boolean
  loadingText?: string
  emptyText?: string
  preferences?: ConfigurableTablePreference | null
}>(), {
  rowKey: 'id',
  loading: false,
  loadingText: '加载中...',
  emptyText: '暂无数据',
  preferences: null
})

const emit = defineEmits<{
  (e: 'update:preferences', value: ConfigurableTablePreference): void
}>()

const draggingColumnKey = ref<string | null>(null)
const columnPanelOpen = ref(false)
const localPreferences = reactive<ConfigurableTablePreference>({
  columnOrder: [],
  hiddenColumns: [],
  sortKey: null,
  sortDirection: null
})

const clonePreferences = (preferences?: ConfigurableTablePreference | null): ConfigurableTablePreference => ({
  columnOrder: [...(preferences?.columnOrder || [])],
  hiddenColumns: [...(preferences?.hiddenColumns || [])],
  sortKey: preferences?.sortKey || null,
  sortDirection: preferences?.sortDirection || null
})

const syncPreferences = (preferences?: ConfigurableTablePreference | null) => {
  const next = clonePreferences(preferences)
  localPreferences.columnOrder = next.columnOrder
  localPreferences.hiddenColumns = next.hiddenColumns
  localPreferences.sortKey = next.sortKey
  localPreferences.sortDirection = next.sortDirection
}

watch(() => props.preferences, value => syncPreferences(value), { immediate: true, deep: true })

const orderedColumns = computed(() => {
  const columnsByKey = new Map(props.columns.map(column => [column.key, column]))
  const used = new Set<string>()
  const ordered = localPreferences.columnOrder
    .map(key => {
      const column = columnsByKey.get(key)
      if (column) {
        used.add(key)
      }
      return column
    })
    .filter((column): column is ConfigurableColumn => !!column)
  props.columns.forEach(column => {
    if (!used.has(column.key)) {
      ordered.push(column)
    }
  })
  return ordered
})

const hiddenColumnSet = computed(() => {
  const hidden = new Set(localPreferences.hiddenColumns)
  props.columns.forEach(column => {
    if (column.defaultVisible === false && !localPreferences.columnOrder.length && !props.preferences) {
      hidden.add(column.key)
    }
  })
  return hidden
})

const visibleColumns = computed(() => orderedColumns.value.filter(column => !hiddenColumnSet.value.has(column.key)))

const compareValues = (left: any, right: any) => {
  if (left == null && right == null) return 0
  if (left == null) return 1
  if (right == null) return -1
  const leftNumber = Number(left)
  const rightNumber = Number(right)
  if (!Number.isNaN(leftNumber) && !Number.isNaN(rightNumber) && `${left}` !== '' && `${right}` !== '') {
    return leftNumber - rightNumber
  }
  return String(left).localeCompare(String(right), 'zh-CN')
}

const sortedRows = computed(() => {
  const rows = [...props.rows]
  if (!localPreferences.sortKey || !localPreferences.sortDirection) {
    return rows
  }
  const direction = localPreferences.sortDirection === 'asc' ? 1 : -1
  return rows.sort((left, right) => compareValues(left[localPreferences.sortKey!], right[localPreferences.sortKey!]) * direction)
})

const emitPreferences = () => {
  emit('update:preferences', clonePreferences(localPreferences))
}

const toggleColumn = (key: string) => {
  const hidden = new Set(localPreferences.hiddenColumns)
  if (hidden.has(key)) {
    hidden.delete(key)
  } else {
    hidden.add(key)
  }
  localPreferences.hiddenColumns = orderedColumns.value
    .map(column => column.key)
    .filter(columnKey => hidden.has(columnKey))
  emitPreferences()
}

const toggleSort = (key: string) => {
  if (localPreferences.sortKey !== key) {
    localPreferences.sortKey = key
    localPreferences.sortDirection = 'asc'
  } else if (localPreferences.sortDirection === 'asc') {
    localPreferences.sortDirection = 'desc'
  } else if (localPreferences.sortDirection === 'desc') {
    localPreferences.sortKey = null
    localPreferences.sortDirection = null
  } else {
    localPreferences.sortDirection = 'asc'
  }
  emitPreferences()
}

const sortIndicator = (key: string) => {
  if (localPreferences.sortKey !== key) return '↕'
  return localPreferences.sortDirection === 'asc' ? '↑' : localPreferences.sortDirection === 'desc' ? '↓' : '↕'
}

const startDrag = (key: string) => {
  draggingColumnKey.value = key
}

const dropColumn = (targetKey: string) => {
  if (!draggingColumnKey.value || draggingColumnKey.value === targetKey) return
  const order = orderedColumns.value.map(column => column.key)
  const from = order.indexOf(draggingColumnKey.value)
  const to = order.indexOf(targetKey)
  if (from < 0 || to < 0) return
  const [moved] = order.splice(from, 1)
  order.splice(to, 0, moved)
  localPreferences.columnOrder = order
  draggingColumnKey.value = null
  emitPreferences()
}

const resolveRowKey = (row: Record<string, any>) => {
  if (typeof props.rowKey === 'function') return props.rowKey(row)
  return row[props.rowKey]
}

const formatDefaultValue = (value: any) => {
  if (value == null || value === '') return '—'
  if (typeof value === 'boolean') return value ? '是' : '否'
  return value
}
</script>
