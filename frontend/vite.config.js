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
    allowedHosts: ['iwxyi.com', 'localhost', '127.0.0.1'],  // 允许的域名/IP
    hmr: {
      overlay: true
    },
    watch: {
      usePolling: true,
      interval: 100
    },
    proxy: {
      '/api': {
        target: 'http://localhost:6060',
        changeOrigin: true
      }
    }
  }
})

