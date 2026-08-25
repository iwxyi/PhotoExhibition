import { computed, ref } from 'vue'

export type AdminNoticeTone = 'success' | 'error' | 'info'

export type AdminConfirmOptions = {
  title: string
  message?: string
  confirmLabel?: string
  tone?: 'default' | 'danger'
}

type AdminNotice = {
  id: number
  tone: AdminNoticeTone
  message: string
}

type PendingConfirm = AdminConfirmOptions & {
  resolve: (confirmed: boolean) => void
}

const notices = ref<AdminNotice[]>([])
const pendingConfirm = ref<PendingConfirm | null>(null)
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

export const useAdminFeedback = () => ({
  notices: computed(() => notices.value),
  pendingConfirm: computed(() => pendingConfirm.value),
  notify: notifyAdmin,
  confirm: confirmAdmin,
  resolveConfirm: resolveAdminConfirm
})
