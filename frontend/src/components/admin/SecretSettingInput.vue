<template>
  <div class="space-y-2">
    <input :value="modelValue" type="password" autocomplete="new-password"
      :aria-label="label" :placeholder="cleared ? '保存后清除' : configured ? '已配置，留空保留' : '未配置'"
      class="admin-input w-full px-4 py-3 rounded-xl"
      @input="onInput" @paste="onPaste" />
    <button v-if="configured" type="button" class="text-xs admin-table-muted"
      @click="$emit('clear', !cleared)">{{ cleared ? '取消清除' : '清除已保存的密钥' }}</button>
  </div>
</template>
<script setup lang="ts">
defineProps<{ modelValue: string; configured: boolean; cleared: boolean; label?: string }>()
const emit = defineEmits<{ (event: 'update:modelValue', value: string): void; (event: 'clear', value: boolean): void }>()
const onInput = (event: Event) => {
  const value = (event.target as HTMLInputElement).value
  emit('update:modelValue', value)
  if (value) emit('clear', false)
}
const onPaste = (event: ClipboardEvent) => {
  const value = event.clipboardData?.getData('text')
  if (!value) return
  // Password inputs strip line breaks; preserve pasted PEM keys in the saved value.
  event.preventDefault()
  emit('update:modelValue', value)
  emit('clear', false)
}
</script>
