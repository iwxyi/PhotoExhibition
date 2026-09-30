<template>
  <dialog ref="dialog" class="admin-shell rounded-lg p-5 w-[calc(100%-2rem)] max-w-sm" aria-labelledby="face-rebuild-title" @cancel.prevent="finish(null)" @close="finish(null)">
    <form @submit.prevent="finish(preserveBindings)">
      <h2 id="face-rebuild-title" class="text-base mb-4">重建人脸</h2>
      <label class="block text-sm">
        人脸重建模式
        <select v-model="preserveBindings" autofocus class="admin-field w-full mt-2 px-3 py-2 rounded">
          <option :value="true">保留已有人物绑定</option>
          <option :value="false">完全重建，不继承人物绑定</option>
        </select>
      </label>
      <div class="mt-5 flex justify-end gap-2">
        <button type="button" class="admin-button-soft px-3 py-2 rounded text-sm" @click="finish(null)">取消</button>
        <button type="submit" class="admin-button-warning px-3 py-2 rounded text-sm">加入队列</button>
      </div>
    </form>
  </dialog>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'

const emit = defineEmits<{ (event: 'choose', value: boolean | null): void }>()
const dialog = ref<HTMLDialogElement | null>(null)
const preserveBindings = ref(true)
let finished = false
const finish = (value: boolean | null) => {
  if (finished) return
  finished = true
  emit('choose', value)
}
onMounted(() => dialog.value?.showModal())
</script>

<style scoped>
dialog::backdrop { background: rgba(0, 0, 0, 0.55); }
</style>
