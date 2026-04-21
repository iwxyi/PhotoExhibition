<template>
  <div class="min-h-screen admin-shell admin-login-page">
    <div class="admin-login-shell">
      <section class="admin-login-brand">
        <div class="admin-login-brand-mark">PE</div>
        <div class="space-y-4">
          <div class="space-y-2">
            <p class="admin-login-eyebrow">Admin Console</p>
            <h1 class="admin-login-title">管理员登录</h1>
            <p class="admin-login-description">摄影作品展示平台管理后台</p>
          </div>
          <div class="admin-login-points">
            <div class="admin-login-point">
              <span class="admin-login-point-dot"></span>
              <span>相册、照片、标签与主题统一管理</span>
            </div>
            <div class="admin-login-point">
              <span class="admin-login-point-dot"></span>
              <span>扫描、模型、存储与支付能力集中控制</span>
            </div>
            <div class="admin-login-point">
              <span class="admin-login-point-dot"></span>
              <span>当前账号主题会同步到数据库，跨设备保持一致</span>
            </div>
          </div>
        </div>
      </section>

      <section class="admin-login-panel glass-panel">
        <div class="admin-login-panel-head">
          <div>
            <div class="admin-login-panel-kicker">进入后台</div>
            <h2 class="admin-login-panel-title">登录</h2>
          </div>
          <router-link
            to="/"
            class="admin-login-home-link"
          >
            返回首页
          </router-link>
        </div>

        <form class="space-y-6" @submit.prevent="handleLogin">
          <div class="space-y-4">
            <div>
              <label for="username" class="admin-login-label">用户名</label>
            <input
              id="username"
              v-model="username"
              name="username"
              type="text"
              required
              class="admin-login-input"
              placeholder="输入管理员用户名"
            />
          </div>
          <div>
            <label for="password" class="admin-login-label">密码</label>
            <input
              id="password"
              v-model="password"
              name="password"
              type="password"
              required
              class="admin-login-input"
              placeholder="输入管理员密码"
            />
          </div>
          </div>

          <div v-if="showInitHint" class="admin-login-notice admin-login-notice--info">
            <div class="flex">
              <div class="flex-shrink-0">
              <svg class="h-5 w-5 text-blue-400" fill="currentColor" viewBox="0 0 20 20">
                <path fill-rule="evenodd" d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7-4a1 1 0 11-2 0 1 1 0 012 0zM9 9a1 1 0 000 2v3a1 1 0 001 1h1a1 1 0 100-2v-3a1 1 0 00-1-1H9z" clip-rule="evenodd"/>
              </svg>
              </div>
              <div class="ml-3">
                <h3 class="text-sm font-medium text-blue-400">
                系统初始化
                </h3>
                <div class="mt-2 text-sm text-blue-300">
                  <p>检测到系统尚未初始化。首次登录请使用默认管理员账户：</p>
                  <p class="mt-1 font-mono">用户名: admin</p>
                  <p class="font-mono">密码: admin</p>
                  <p class="mt-2 text-yellow-300">⚠️ 登录后请立即修改密码以确保安全。</p>
                </div>
              </div>
            </div>
          </div>

          <div v-if="successMessage" class="admin-login-notice admin-login-notice--success">
            <div class="flex">
              <div class="flex-shrink-0">
              <svg class="h-5 w-5 text-green-400" fill="currentColor" viewBox="0 0 20 20">
                <path fill-rule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z" clip-rule="evenodd"/>
              </svg>
              </div>
              <div class="ml-3">
                <p class="text-sm font-medium text-green-400">
                  {{ successMessage }}
                </p>
              </div>
            </div>
          </div>

          <div v-if="error" class="admin-login-error">
            {{ error }}
          </div>

          <div class="space-y-4">
            <button
              type="submit"
              :disabled="loading"
              class="admin-login-submit"
            >
              <span v-if="!loading">登录</span>
              <span v-else>登录中...</span>
            </button>

            <div class="admin-login-help">
              <h3 class="admin-login-help-title">忘记密码？</h3>
              <div class="admin-login-help-body">
                <p>如果忘记了管理员密码，需要：</p>
                <p class="admin-login-help-code">在数据库中删除 admin_user 表的所有记录</p>
                <p>然后使用默认账户重新登录：</p>
                <p class="font-mono text-blue-400">用户名: admin | 密码: admin</p>
              </div>
            </div>
          </div>
        </form>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const LAST_ADMIN_ROUTE_KEY = 'pe_last_admin_route'

const username = ref('')
const password = ref('')
const loading = ref(false)
const error = ref('')
const successMessage = ref('')
const showInitHint = ref(false)

const handleLogin = async () => {
  error.value = ''
  successMessage.value = ''
  loading.value = true

  try {
    const result = await authStore.login({
      username: username.value,
      password: password.value,
      loginType: 'password'
    })
    if (result.success) {
      const redirectTarget = typeof route.query.redirect === 'string' && route.query.redirect.trim()
        ? route.query.redirect
        : (localStorage.getItem(LAST_ADMIN_ROUTE_KEY) || '/admin')
      // 检查是否是初始化消息
      if (result.message && result.message.includes('系统初始化完成')) {
        successMessage.value = result.message
        // 3秒后跳转到管理页面
        setTimeout(() => {
          router.push(redirectTarget)
        }, 3000)
      } else {
        router.push(redirectTarget)
      }
    } else {
      error.value = result.message || '登录失败'
      // 如果是初始化提示，显示初始化提示
      if (result.message && result.message.includes('系统尚未初始化')) {
        showInitHint.value = true
      }
    }
  } catch (err: any) {
    error.value = err.message || '登录失败，请稍后重试'
    // 如果是初始化提示，显示初始化提示
    if (err.message && err.message.includes('系统尚未初始化')) {
      showInitHint.value = true
    }
  } finally {
    loading.value = false
  }
}
</script>
