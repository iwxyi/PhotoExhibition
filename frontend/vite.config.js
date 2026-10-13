import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

const apiProxyTarget = process.env.VITE_API_PROXY_TARGET || 'http://localhost:6060'
const devPort = Number(process.env.VITE_PORT || process.env.PORT || 3030)
const allowedHostsEnv = process.env.VITE_ALLOWED_HOSTS
const allowedHosts = allowedHostsEnv
  ? allowedHostsEnv.split(',').map((host) => host.trim()).filter(Boolean)
  : ['photo.iwxyi.com', 'iwxyi.com', 'localhost', '127.0.0.1', '.iwxyi.com', 'claw.iwxyi.com']

export default defineConfig({
  plugins: [vue()],
  build: {
    chunkSizeWarningLimit: 900,
    rollupOptions: {
      output: {
        manualChunks: {
          vue: ['vue', 'vue-router', 'pinia'],
          vendor: ['axios', '@vueuse/core']
        }
      }
    }
  },
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    host: '0.0.0.0',
    port: devPort,
    strictPort: true,
    allowedHosts,
    hmr: {
      overlay: true,
      clientPort: devPort,
      path: '/hmr/'
    },
    watch: {
      usePolling: true,
      interval: 100
    },
    proxy: {
      '/api': {
        target: apiProxyTarget,
        changeOrigin: true,
        timeout: 600000,
        proxyTimeout: 600000
      }
    }
  }
})
