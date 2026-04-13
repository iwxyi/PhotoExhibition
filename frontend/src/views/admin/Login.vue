<template>
  <div class="min-h-screen admin-shell flex items-center justify-center px-4">
    <div class="max-w-md w-full space-y-8 glass-panel px-6 py-8">
      <div>
        <h2 class="mt-6 text-center text-3xl font-extralight text-white">
          管理员登录
        </h2>
        <p class="mt-2 text-center text-sm text-gray-400">
          摄影作品展示平台管理后台
        </p>
      </div>
      <form class="mt-8 space-y-6" @submit.prevent="handleLogin">
        <div class="rounded-md shadow-sm space-y-4">
          <div>
            <label for="username" class="block text-sm mb-2 text-gray-300">用户名</label>
            <input
              id="username"
              v-model="username"
              name="username"
              type="text"
              required
              class="appearance-none rounded-lg relative block w-full px-4 py-3 border border-gray-700 bg-gray-800 placeholder-gray-400 text-white focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
              placeholder="输入管理员用户名"
            />
          </div>
          <div>
            <label for="password" class="block text-sm mb-2 text-gray-300">密码</label>
            <input
              id="password"
              v-model="password"
              name="password"
              type="password"
              required
              class="appearance-none rounded-lg relative block w-full px-4 py-3 border border-gray-700 bg-gray-800 placeholder-gray-400 text-white focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
              placeholder="输入管理员密码"
            />
          </div>
        </div>

        <!-- 初始化提示 -->
        <div v-if="showInitHint" class="bg-blue-900/50 border border-blue-500/50 rounded-lg p-4">
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
                <p class="mt-2 text-yellow-300">⚠️ 登录后请立即修改密码以确保安全！</p>
              </div>
            </div>
          </div>
        </div>

        <!-- 成功消息 -->
        <div v-if="successMessage" class="bg-green-900/50 border border-green-500/50 rounded-lg p-4">
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

        <div v-if="error" class="text-red-400 text-sm text-center">
          {{ error }}
        </div>

        <div>
          <button
            type="submit"
            :disabled="loading"
            class="group relative w-full flex justify-center py-3 px-4 border border-transparent text-sm font-medium rounded-lg text-white bg-blue-600 hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
          >
            <span v-if="!loading">登录</span>
            <span v-else>登录中...</span>
          </button>
        </div>

        <div class="text-center">
          <router-link
            to="/"
            class="text-sm text-gray-400 hover:text-gray-300 transition-colors"
          >
            返回首页
          </router-link>
        </div>

        <!-- 忘记密码帮助 -->
        <div class="mt-6 bg-gray-800/50 border border-gray-600 rounded-lg p-4">
          <div class="text-center">
            <h3 class="text-sm font-medium text-gray-300 mb-2">
              忘记密码？
            </h3>
            <div class="text-xs text-gray-400 space-y-1">
              <p>如果您忘记了管理员密码，需要：</p>
              <p class="font-mono bg-gray-900/50 px-2 py-1 rounded">
                在数据库中删除 admin_user 表的所有记录
              </p>
              <p>然后使用默认账户重新登录：</p>
              <p class="font-mono text-blue-400">用户名: admin | 密码: admin</p>
            </div>
          </div>
        </div>
      </form>
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
