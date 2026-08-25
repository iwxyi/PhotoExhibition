import { computed, ref } from 'vue'

export type AdminNoticeTone = 'success' | 'error' | 'info'

export type AdminConfirmOptions = {
  title: string
  message?: string
  confirmLabel?: string
  tone?: 'default' | 'danger'
}
export type AdminPromptOptions = AdminConfirmOptions & { initialValue?: string; placeholder?: string }

type AdminNotice = {
  id: number
  tone: AdminNoticeTone
  message: string
}

type PendingConfirm = AdminConfirmOptions & {
  resolve: (confirmed: boolean) => void
}
type PendingPrompt = AdminPromptOptions & { resolve: (value: string | null) => void }

const notices = ref<AdminNotice[]>([])
const pendingConfirm = ref<PendingConfirm | null>(null)
const pendingPrompt = ref<PendingPrompt | null>(null)
let noticeId = 0

export const notifyAdmin = (message: string, tone: AdminNoticeTone = 'info') => {
  const id = ++noticeId
  notices.value = [...notices.value, { id, tone, message }]
  globalThis.setTimeout(() => {
    notices.value = notices.value.filter(notice => notice.id !== id)
  }, 3600)
}

export const confirmAdmin = (options: AdminConfirmOptions) => new Promise<boolean>(resolve => {
  pendingConfirm.value = { ...options, resolve }
})

export const resolveAdminConfirm = (confirmed: boolean) => {
  const current = pendingConfirm.value
  if (!current) return
  pendingConfirm.value = null
  current.resolve(confirmed)
}
export const promptAdmin = (options: AdminPromptOptions) => new Promise<string | null>(resolve => {
  pendingPrompt.value = { ...options, resolve }
})
export const resolveAdminPrompt = (value: string | null) => {
  const current = pendingPrompt.value
  if (!current) return
  pendingPrompt.value = null
  current.resolve(value)
}

export const useAdminFeedback = () => ({
  notices: computed(() => notices.value),
  pendingConfirm: computed(() => pendingConfirm.value),
  pendingPrompt: computed(() => pendingPrompt.value),
  notify: notifyAdmin,
  confirm: confirmAdmin,
  resolveConfirm: resolveAdminConfirm,
  prompt: promptAdmin,
  resolvePrompt: resolveAdminPrompt
})
