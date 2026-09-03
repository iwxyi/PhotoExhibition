<template>
  <div
    v-if="modelValue"
    class="fixed inset-0 admin-modal-backdrop flex items-center justify-center z-50"
    @click.self="close"
  >
    <div class="admin-modal-card admin-persons-modal p-6 max-w-md w-full mx-4">
      <h3 class="text-lg font-semibold mb-4">删除人物</h3>
      <p class="admin-persons-note mb-6">
        确定要删除人物
        <span class="font-semibold text-[color:var(--pe-admin-text-primary)]">"{{ selectedName || '未命名' }}"</span>
        吗？
      </p>

      <div class="space-y-3 mb-6">
        <div class="admin-persons-warning-card p-3 rounded">
          <div class="font-medium text-amber-400 mb-1">解散人物</div>
          <div class="text-sm admin-persons-note">将所有关联人脸重新设为未分配状态，然后删除人物记录。</div>
        </div>

        <div class="admin-persons-danger-card p-3 rounded">
          <div class="font-medium text-red-400 mb-1">删除人物</div>
          <div class="text-sm admin-persons-note">直接删除人物记录，人脸仍保持已分配状态但指向不存在的人物。</div>
        </div>
      </div>

      <div class="flex gap-3 justify-end">
        <button type="button" @click="close" class="admin-button-soft px-4 py-2 rounded transition-colors">取消</button>
        <button type="button" @click="$emit('dissolve')" class="admin-button-warning px-4 py-2 rounded transition-colors">解散人物</button>
        <button type="button" @click="$emit('remove')" class="admin-button-danger px-4 py-2 rounded transition-colors">删除人物</button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  modelValue: boolean
  selectedName?: string | null
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  dissolve: []
  remove: []
}>()

const close = () => emit('update:modelValue', false)
</script>
