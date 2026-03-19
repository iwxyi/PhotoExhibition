import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

const apiProxyTarget = process.env.VITE_API_PROXY_TARGET || 'http://localhost:6060'
const allowedHostsEnv = process.env.VITE_ALLOWED_HOSTS
const allowedHosts = allowedHostsEnv
  ? allowedHostsEnv.split(',').map((host) => host.trim()).filter(Boolean)
  : ['photo.iwxyi.com', 'iwxyi.com', 'localhost', '127.0.0.1', '.iwxyi.com', 'claw.iwxyi.com']

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    host: '0.0.0.0',
    port: 3030,
    allowedHosts,
    hmr: {
      overlay: true,
      clientPort: 3030,
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
