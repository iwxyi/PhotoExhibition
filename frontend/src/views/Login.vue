<template>
  <div class="min-h-screen bg-white dark:bg-gray-900 px-4 py-10">
    <div class="max-w-md mx-auto">
      <div class="glass-panel p-8">
        <div class="text-center mb-8">
          <h1 class="text-3xl font-light text-gray-900 dark:text-white">账号登录</h1>
          <p class="mt-2 text-sm text-gray-500 dark:text-gray-400">
            {{ loginDescription }}
          </p>
        </div>

        <div
          class="mb-6 p-1 rounded-xl bg-gray-100 dark:bg-gray-800"
          :class="loginTabClass"
        >
          <button
            type="button"
            @click="loginMode = 'password'"
            :class="loginMode === 'password' ? 'bg-white dark:bg-gray-700 text-blue-600 dark:text-blue-300 shadow-sm' : 'text-gray-500 dark:text-gray-400'"
            class="py-2 rounded-lg text-sm transition-colors"
          >
            密码登录
          </button>
          <button
            v-if="authStore.smsLoginEnabled"
            type="button"
            @click="loginMode = 'smsCode'"
            :class="loginMode === 'smsCode' ? 'bg-white dark:bg-gray-700 text-blue-600 dark:text-blue-300 shadow-sm' : 'text-gray-500 dark:text-gray-400'"
            class="py-2 rounded-lg text-sm transition-colors"
          >
            验证码登录
          </button>
          <button
            v-if="authStore.emailCodeLoginEnabled"
            type="button"
            @click="loginMode = 'emailCode'"
            :class="loginMode === 'emailCode' ? 'bg-white dark:bg-gray-700 text-blue-600 dark:text-blue-300 shadow-sm' : 'text-gray-500 dark:text-gray-400'"
            class="py-2 rounded-lg text-sm transition-colors"
          >
            邮箱验证码
          </button>
        </div>

        <form class="space-y-5" @submit.prevent="handleLogin">
          <div>
            <label for="identifier" class="block text-sm mb-2 text-gray-700 dark:text-gray-300">
              {{ loginMode === 'password' ? '用户名、邮箱或手机号' : loginMode === 'smsCode' ? '手机号' : '邮箱' }}
            </label>
            <input
              id="identifier"
              v-model="identifier"
              type="text"
              required
              class="w-full px-4 py-3 rounded-xl border border-gray-200 dark:border-gray-700 bg-white/80 dark:bg-gray-800/80 text-gray-900 dark:text-white outline-none focus:ring-2 focus:ring-blue-500"
              :placeholder="loginMode === 'password'
                ? '例如 admin、user@example.com 或 13800138000'
                : loginMode === 'smsCode'
                  ? '请输入 11 位手机号'
                  : '请输入已绑定的邮箱地址'"
            />
          </div>

          <div v-if="loginMode === 'password'">
            <label for="password" class="block text-sm mb-2 text-gray-700 dark:text-gray-300">密码</label>
            <input
              id="password"
              v-model="password"
              type="password"
              required
              class="w-full px-4 py-3 rounded-xl border border-gray-200 dark:border-gray-700 bg-white/80 dark:bg-gray-800/80 text-gray-900 dark:text-white outline-none focus:ring-2 focus:ring-blue-500"
              placeholder="输入登录密码"
            />
          </div>

          <div v-else>
            <label for="code" class="block text-sm mb-2 text-gray-700 dark:text-gray-300">验证码</label>
            <div class="flex gap-3">
              <input
                id="code"
                v-model="smsCode"
                type="text"
                required
                class="flex-1 px-4 py-3 rounded-xl border border-gray-200 dark:border-gray-700 bg-white/80 dark:bg-gray-800/80 text-gray-900 dark:text-white outline-none focus:ring-2 focus:ring-blue-500"
                :placeholder="loginMode === 'smsCode' ? '输入短信验证码' : '输入邮箱验证码'"
              />
              <button
                type="button"
                :disabled="sendingCode || countdown > 0"
                @click="loginMode === 'smsCode' ? handleSendCode() : handleSendEmailCode()"
                class="px-4 py-3 rounded-xl border border-blue-200 dark:border-blue-700 text-blue-600 dark:text-blue-300 disabled:opacity-60 disabled:cursor-not-allowed"
              >
                {{ sendingCode ? '发送中...' : countdown > 0 ? `${countdown}s` : '发送验证码' }}
              </button>
            </div>
            <div v-if="debugCode" class="mt-2 text-xs text-amber-500">
              当前为模拟{{ loginMode === 'smsCode' ? '短信' : '邮箱' }}模式，验证码：{{ debugCode }}
            </div>
          </div>

          <div v-if="error" class="text-sm text-red-500">{{ error }}</div>

          <button
            type="submit"
            :disabled="loading"
            class="w-full btn-primary justify-center py-3 disabled:opacity-60 disabled:cursor-not-allowed"
          >
            {{ loading ? '登录中...' : '登录' }}
          </button>
        </form>

        <div class="mt-6 flex items-center justify-between text-sm">
          <router-link to="/" class="text-gray-500 dark:text-gray-400 hover:text-blue-500 transition-colors">返回首页</router-link>
          <div class="flex items-center gap-4">
            <router-link to="/forgot-password" class="text-gray-500 dark:text-gray-400 hover:text-blue-500 transition-colors">找回密码</router-link>
            <router-link
              v-if="authStore.multiUserEnabled"
              to="/register"
              class="text-blue-600 dark:text-blue-400 hover:underline"
            >
              注册账号
            </router-link>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { attachExplicitPublicSlug, getDefaultPostAuthPath } from '@/utils/publicRoute'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

const identifier = ref('')
const password = ref('')
const smsCode = ref('')
const loginMode = ref<'password' | 'smsCode' | 'emailCode'>('password')
const loading = ref(false)
const sendingCode = ref(false)
const countdown = ref(0)
const debugCode = ref('')
const error = ref('')
let countdownTimer: number | null = null

const loginDescription = computed(() => {
  const methods = ['账号/邮箱/手机号密码登录']
  if (authStore.smsLoginEnabled) methods.push('手机号验证码登录')
  if (authStore.emailCodeLoginEnabled) methods.push('邮箱验证码登录')
  return `支持${methods.join('，以及')}`
})

const loginTabClass = computed(() => {
  const count = 1 + (authStore.smsLoginEnabled ? 1 : 0) + (authStore.emailCodeLoginEnabled ? 1 : 0)
  if (count >= 3) return 'grid grid-cols-3 gap-2'
  if (count === 2) return 'grid grid-cols-2 gap-2'
  return 'grid grid-cols-1 gap-2'
})

const startCountdown = (seconds: number) => {
  countdown.value = seconds
  if (countdownTimer) {
    window.clearInterval(countdownTimer)
  }
  countdownTimer = window.setInterval(() => {
    countdown.value -= 1
    if (countdown.value <= 0 && countdownTimer) {
      window.clearInterval(countdownTimer)
      countdownTimer = null
    }
  }, 1000)
}

const handleSendCode = async () => {
  error.value = ''
  debugCode.value = ''
  if (!authStore.smsLoginEnabled) {
    error.value = '当前系统未开启短信验证码登录'
    return
  }
  if (!/^1\d{10}$/.test(identifier.value.trim())) {
    error.value = '请输入正确的手机号'
    return
  }

  sendingCode.value = true
  try {
    const result = await authStore.sendLoginCode(identifier.value.trim())
    if (!result.success) {
      error.value = result.message || '验证码发送失败'
      return
    }
    debugCode.value = result.debugCode || ''
    startCountdown(result.expiresInSeconds ? Math.min(result.expiresInSeconds, 60) : 60)
  } finally {
    sendingCode.value = false
  }
}

const handleSendEmailCode = async () => {
  error.value = ''
  debugCode.value = ''
  if (!authStore.emailCodeLoginEnabled) {
    error.value = '当前系统未开启邮箱验证码登录'
    return
  }
  if (!/^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/.test(identifier.value.trim())) {
    error.value = '请输入正确的邮箱地址'
    return
  }

  sendingCode.value = true
  try {
    const result = await authStore.sendEmailLoginCode(identifier.value.trim())
    if (!result.success) {
      error.value = result.message || '验证码发送失败'
      return
    }
    debugCode.value = result.debugCode || ''
    startCountdown(result.expiresInSeconds ? Math.min(result.expiresInSeconds, 60) : 60)
  } finally {
    sendingCode.value = false
  }
}

const handleLogin = async () => {
  loading.value = true
  error.value = ''

  try {
    const result = await authStore.login({
      username: identifier.value.trim(),
      password: loginMode.value === 'password' ? password.value : undefined,
      code: loginMode.value === 'password' ? undefined : smsCode.value.trim(),
      loginType: loginMode.value
    })
    if (!result.success) {
      error.value = result.message || '登录失败'
      return
    }

    const redirect = typeof route.query.redirect === 'string'
      ? route.query.redirect
      : getDefaultPostAuthPath(authStore.slug, authStore.multiUserEnabled)
    router.push(attachExplicitPublicSlug(redirect, authStore.multiUserEnabled ? authStore.slug : null))
  } finally {
    loading.value = false
  }
}

onBeforeUnmount(() => {
  if (countdownTimer) {
    window.clearInterval(countdownTimer)
  }
})

onMounted(async () => {
  try {
    await authStore.fetchPublicSettings()
    if (!authStore.smsLoginEnabled && loginMode.value === 'smsCode') {
      loginMode.value = authStore.emailCodeLoginEnabled ? 'emailCode' : 'password'
    }
    if (!authStore.emailCodeLoginEnabled && loginMode.value === 'emailCode') {
      loginMode.value = 'password'
    }
  } catch {
    loginMode.value = 'password'
  }
})
</script>
