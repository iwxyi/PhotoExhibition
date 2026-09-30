import { createApp } from 'vue'
import FaceRebuildOptionsDialog from '@/components/admin/FaceRebuildOptionsDialog.vue'

export const chooseFaceRebuildOptions = (): Promise<boolean | null> => new Promise(resolve => {
  const host = document.createElement('div')
  document.body.appendChild(host)
  const app = createApp(FaceRebuildOptionsDialog, {
    onChoose: (value: boolean | null) => {
      app.unmount()
      host.remove()
      resolve(value)
    }
  })
  app.mount(host)
})
