import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import './style.css'
import { loadGlobalSettings } from './composables/useUiSettings'
import { useAuthStore } from './stores/auth'

const app = createApp(App)
const pinia = createPinia()
app.use(pinia)
app.use(router)

// 初始化全局设置（氛围特效开关等）
Promise.allSettled([
  loadGlobalSettings(),
  useAuthStore(pinia).bootstrap()
]).finally(() => {
  app.mount('#app')
})
