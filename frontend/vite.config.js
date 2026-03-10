import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'path'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    host: '0.0.0.0',  // 允许外网访问
    port: 3030,
    allowedHosts: ['photo.iwxyi.com', 'iwxyi.com', 'localhost', '127.0.0.1', '.iwxyi.com', 'claw.iwxyi.com'],  // 允许的域名/IP（包括子域名）
    hmr: {
      overlay: true,
      clientPort: 3030,  // 强制使用相同端口
      path: '/hmr/'  // 使用固定路径避免冲突
    },
    watch: {
      usePolling: true,
      interval: 100
    },
    proxy: {
      '/api': {
        target: 'http://localhost:6060',
        changeOrigin: true,
        timeout: 600000,
        proxyTimeout: 600000
      }
    }
  }
})

