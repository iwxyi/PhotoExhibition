import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import './style.css'
import { loadGlobalSettings } from './composables/useUiSettings'

const app = createApp(App)
app.use(createPinia())
app.use(router)

// 初始化全局设置（氛围特效开关等）
loadGlobalSettings().then(() => {
  app.mount('#app')
})

