<template>
  <TransitionGroup name="admin-notice" tag="div" class="admin-notice-stack" aria-live="polite">
    <div v-for="notice in notices" :key="notice.id" class="admin-notice" :data-tone="notice.tone">
      {{ notice.message }}
    </div>
  </TransitionGroup>

  <Teleport to="body">
    <div v-if="pendingConfirm" class="admin-confirm-backdrop" @click.self="resolveConfirm(false)">
      <section class="admin-confirm-dialog" role="alertdialog" aria-modal="true" :aria-labelledby="'admin-confirm-title'">
        <h2 id="admin-confirm-title">{{ pendingConfirm.title }}</h2>
        <p v-if="pendingConfirm.message">{{ pendingConfirm.message }}</p>
        <div class="admin-confirm-actions">
          <button type="button" class="admin-button-soft" @click="resolveConfirm(false)">取消</button>
          <button type="button" :class="pendingConfirm.tone === 'danger' ? 'admin-button-danger' : 'btn-primary'" @click="resolveConfirm(true)">
            {{ pendingConfirm.confirmLabel || '确认' }}
          </button>
        </div>
      </section>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { useAdminFeedback } from '@/composables/useAdminFeedback'

const { notices, pendingConfirm, resolveConfirm } = useAdminFeedback()
</script>

<style>
.admin-notice-stack { position: fixed; top: 1rem; right: 1rem; z-index: 160; display: grid; gap: .5rem; width: min(23rem, calc(100vw - 2rem)); pointer-events: none; }
.admin-notice { padding: .72rem .9rem; border: 1px solid var(--pe-admin-border, rgba(148,163,184,.22)); border-left-width: 3px; border-radius: 6px; background: color-mix(in srgb, var(--pe-surface-bg, #0f172a) 92%, transparent); box-shadow: 0 12px 30px rgba(0,0,0,.18); backdrop-filter: blur(18px) saturate(135%); color: var(--pe-admin-text-primary, #f8fafc); font-size: .875rem; line-height: 1.45; }
.admin-notice[data-tone='success'] { border-left-color: #34d399; }
.admin-notice[data-tone='error'] { border-left-color: #fb7185; }
.admin-notice[data-tone='info'] { border-left-color: #60a5fa; }
.admin-notice-enter-active, .admin-notice-leave-active { transition: opacity .16s ease, transform .16s ease; }
.admin-notice-enter-from, .admin-notice-leave-to { opacity: 0; transform: translateY(-6px); }
.admin-confirm-backdrop { position: fixed; inset: 0; z-index: 170; display: grid; place-items: center; padding: 1rem; background: rgba(2,6,23,.58); backdrop-filter: blur(5px); }
.admin-confirm-dialog { width: min(26rem, 100%); border: 1px solid var(--pe-admin-border, rgba(148,163,184,.25)); border-radius: 8px; background: var(--pe-surface-bg, #0f172a); box-shadow: 0 24px 64px rgba(0,0,0,.36); padding: 1.25rem; color: var(--pe-admin-text-primary, #f8fafc); }
.admin-confirm-dialog h2 { margin: 0; font-size: 1rem; font-weight: 650; }
.admin-confirm-dialog p { white-space: pre-line; margin: .65rem 0 0; color: var(--pe-admin-text-muted, #94a3b8); font-size: .875rem; line-height: 1.55; }
.admin-confirm-actions { display: flex; justify-content: flex-end; gap: .65rem; margin-top: 1.25rem; }
.admin-confirm-actions > button { min-height: 2.25rem; padding: .42rem .8rem; border-radius: 5px; font-size: .875rem; }
.admin-button-danger { border: 1px solid rgba(244,63,94,.5); background: #e11d48; color: #fff; }
.admin-button-danger:hover { background: #be123c; }
@media (max-width: 640px) { .admin-notice-stack { top: auto; right: 1rem; bottom: 1rem; } }
</style>
