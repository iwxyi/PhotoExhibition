import { computed, ref } from 'vue'

export type SyncNoticeKind = 'theme' | 'ui-settings' | 'system'

export type SyncNoticeItem = {
  id: number
  kind: SyncNoticeKind
  title: string
  message: string
}

const notices = ref<SyncNoticeItem[]>([])
let noticeSeed = 0
const recentNoticeKeys = new Map<string, number>()

const noticePresets: Record<SyncNoticeKind, { title: string; message: string }> = {
  theme: {
    title: '后台主题已同步',
    message: '已应用其他标签页修改的后台主题与风格。'
  },
  'ui-settings': {
    title: '浏览偏好已同步',
    message: '已应用其他标签页修改的公开页显示偏好。'
  },
  system: {
    title: '设置已同步',
    message: '已应用来自其他标签页的设置变更。'
  }
}

export const pushSyncNotice = (
  kind: SyncNoticeKind,
  override?: Partial<Pick<SyncNoticeItem, 'title' | 'message'>>
) => {
  const preset = noticePresets[kind]
  const title = override?.title || preset.title
  const message = override?.message || preset.message
  const dedupeKey = `${kind}:${title}:${message}`
  const now = Date.now()
  const lastShownAt = recentNoticeKeys.get(dedupeKey) || 0
  if (now - lastShownAt < 1200) {
    return
  }
  recentNoticeKeys.set(dedupeKey, now)
  const id = ++noticeSeed
  notices.value = notices.value.concat({
    id,
    kind,
    title,
    message
  })
  globalThis.setTimeout(() => {
    notices.value = notices.value.filter((item) => item.id !== id)
  }, 2600)
}

export function useSyncNotice() {
  return {
    notices: computed(() => notices.value)
  }
}
