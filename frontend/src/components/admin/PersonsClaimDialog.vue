<template>
  <div v-if="modelValue" class="fixed inset-0 z-50 flex items-center justify-center" @click.self="close">
    <div class="absolute inset-0 admin-persons-claim-backdrop"></div>

    <div class="admin-modal-card admin-persons-claim-dialog relative w-[90vw] max-w-4xl h-[80vh] max-h-[800px] flex flex-col">
      <div class="flex items-center justify-between p-4 admin-persons-claim-head">
        <h2 class="text-lg font-medium">认领为</h2>
        <button type="button" @click="close" class="admin-persons-claim-close transition-colors" aria-label="关闭">
          <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"></path>
          </svg>
        </button>
      </div>

      <div class="p-4 admin-persons-claim-head flex items-center gap-2">
        <label class="flex-1 space-y-1">
          <span class="text-[11px] admin-persons-note">搜索人物</span>
          <input
            ref="searchInput"
            :value="searchKeyword"
            @input="onSearchInput"
            @keyup.enter="$emit('enter')"
            placeholder="输入人物姓名关键词"
            class="w-full px-3 py-2 admin-persons-claim-input rounded text-sm focus:outline-none focus:ring-1 focus:ring-blue-500"
          />
        </label>
        <button
          type="button"
          @click="$emit('action')"
          :disabled="!canCreate && selectedPersonId === null"
          class="admin-persons-success-button px-3 py-2 rounded text-sm transition-colors whitespace-nowrap disabled:opacity-50 disabled:cursor-not-allowed"
        >
          {{ canCreate ? '新建人物' : '加入人物' }}
        </button>
      </div>

      <div class="flex-1 overflow-y-auto p-2">
        <div v-if="loading" class="admin-persons-claim-loading flex items-center justify-center h-full">
          <div class="h-8 w-8 rounded-full border-2 border-gray-300 border-t-transparent animate-spin"></div>
        </div>
        <div v-else-if="persons.length === 0" class="admin-persons-note text-center py-8">暂无人物</div>
        <div v-else class="grid grid-cols-3 sm:grid-cols-4 md:grid-cols-5 lg:grid-cols-6 xl:grid-cols-7 gap-2">
          <div
            v-for="person in persons"
            :key="person.id"
            @click.stop="$emit('select', person)"
            class="admin-persons-claim-card flex flex-col items-center p-1.5 rounded cursor-pointer transition-colors border-2"
            :class="selectedPersonId === person.id ? 'admin-persons-claim-card--active' : 'admin-persons-claim-card--idle'"
          >
            <div class="w-12 h-12 rounded-full admin-persons-claim-avatar overflow-hidden mb-1 relative">
              <img v-if="person.thumbnailUrl" :src="person.thumbnailUrl" class="w-full h-full object-cover" :class="selectedPersonId === person.id ? 'brightness-110' : ''" />
              <div v-if="selectedPersonId === person.id" class="absolute inset-0 flex items-center justify-center bg-white/20">
                <svg class="w-6 h-6 text-white" fill="currentColor" viewBox="0 0 20 20">
                  <path fill-rule="evenodd" d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293z" clip-rule="evenodd"></path>
                </svg>
              </div>
            </div>
            <div class="text-center w-full">
              <div class="text-xs truncate leading-tight" :class="selectedPersonId === person.id ? 'text-[color:var(--pe-admin-text-primary)] font-medium' : 'admin-persons-note'" :title="person.name || '未命名'">
                {{ person.name || '未命名' }} <span :class="selectedPersonId === person.id ? 'text-[color:var(--pe-admin-text-secondary)]' : 'admin-persons-note'">({{ person.faceCount || 0 }})</span>
              </div>
              <div v-if="person.similarity !== undefined" class="admin-persons-photo-link text-[10px] text-blue-300 mt-0.5">{{ (person.similarity * 100).toFixed(0) }}%</div>
            </div>
          </div>
        </div>
      </div>

      <div class="p-4 admin-persons-claim-head">
        <div class="text-sm admin-persons-note text-center">点击人物卡片即可直接认领人脸 | 按回车键确认选择</div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { nextTick, ref } from 'vue'

export interface ClaimPersonItem {
  id: number
  name?: string | null
  faceCount?: number
  thumbnailUrl?: string
  similarity?: number
}

defineProps<{
  modelValue: boolean
  loading: boolean
  persons: ClaimPersonItem[]
  searchKeyword: string
  selectedPersonId: number | null
  canCreate: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  'update:searchKeyword': [value: string]
  enter: []
  action: []
  select: [person: ClaimPersonItem]
}>()

const searchInput = ref<HTMLInputElement | null>(null)
const close = () => emit('update:modelValue', false)
const onSearchInput = (event: Event) => emit('update:searchKeyword', (event.target as HTMLInputElement).value)
const focus = async () => {
  await nextTick()
  searchInput.value?.focus()
}

defineExpose({ focus })
</script>
