<template>
  <iframe
    class="admin-html-preview"
    :srcdoc="html"
    sandbox=""
    referrerpolicy="no-referrer"
    title="邮件内容预览"
  />
</template>

<script setup lang="ts">
defineProps<{ html: string }>()
</script>

<style>
.admin-html-preview {
  display: block;
  width: 100%;
  min-height: 20rem;
  border: 0;
  background: #fff;
  color-scheme: light;
}
</style>
