<template>
  <div class="min-h-screen bg-white dark:bg-gray-900 px-4 py-10">
    <div class="max-w-xl mx-auto">
      <div class="glass-panel p-8">
        <div class="text-center mb-8">
          <h1 class="text-3xl font-light text-gray-900 dark:text-white">找回密码</h1>
          <p class="mt-2 text-sm text-gray-500 dark:text-gray-400">
            {{ description }}
          </p>
        </div>

        <div class="mb-6 p-1 rounded-xl bg-gray-100 dark:bg-gray-800" :class="tabClass">
          <button
            v-if="authStore.smsLoginEnabled"
            type="button"
            @click="mode = 'phone'"
            :class="mode === 'phone' ? activeTabClass : inactiveTabClass"
            class="py-2 rounded-lg text-sm transition-colors"
          >
            手机号找回
          </button>
          <button
            type="button"
            @click="mode = 'email'"
            :class="mode === 'email' ? activeTabClass : inactiveTabClass"
            class="py-2 rounded-lg text-sm transition-colors"
          >
            邮箱找回
          </button>
        </div>

        <form class="space-y-5" @submit.prevent="handleSubmit">
          <div>
            <label class="block text-sm mb-2 text-gray-700 dark:text-gray-300">
              {{ mode === 'phone' ? '手机号' : '邮箱' }}
            </label>
            <input
              v-model="identifier"
              type="text"
              required
              class="w-full px-4 py-3 rounded-xl border border-gray-200 dark:border-gray-700 bg-white/80 dark:bg-gray-800/80 text-gray-900 dark:text-white outline-none focus:ring-2 focus:ring-blue-500"
              :placeholder="mode === 'phone' ? '请输入已绑定手机号' : '请输入已绑定邮箱'"
            />
          </div>

          <div>
            <label class="block text-sm mb-2 text-gray-700 dark:text-gray-300">验证码</label>
            <div class="flex gap-3">
              <input
                v-model="code"
                type="text"
                required
                class="flex-1 px-4 py-3 rounded-xl border border-gray-200 dark:border-gray-700 bg-white/80 dark:bg-gray-800/80 text-gray-900 dark:text-white outline-none focus:ring-2 focus:ring-blue-500"
                placeholder="请输入验证码"
              />
              <button
                type="button"
                :disabled="sendingCode || countdown > 0"
                @click="handleSendCode"
                class="px-4 py-3 rounded-xl border border-blue-200 dark:border-blue-700 text-blue-600 dark:text-blue-300 disabled:opacity-60 disabled:cursor-not-allowed"
              >
                {{ sendingCode ? '发送中...' : countdown > 0 ? `${countdown}s` : '发送验证码' }}
              </button>
            </div>
            <div v-if="debugCode" class="mt-2 text-xs text-amber-500">
              当前为模拟{{ mode === 'phone' ? '短信' : '邮箱' }}模式，验证码：{{ debugCode }}
            </div>
          </div>

          <div>
            <label class="block text-sm mb-2 text-gray-700 dark:text-gray-300">新密码</label>
            <input
              v-model="newPassword"
              type="password"
              required
              class="w-full px-4 py-3 rounded-xl border border-gray-200 dark:border-gray-700 bg-white/80 dark:bg-gray-800/80 text-gray-900 dark:text-white outline-none focus:ring-2 focus:ring-blue-500"
              placeholder="至少 6 位"
            />
          </div>

          <div>
            <label class="block text-sm mb-2 text-gray-700 dark:text-gray-300">确认新密码</label>
            <input
              v-model="confirmPassword"
              type="password"
              required
              class="w-full px-4 py-3 rounded-xl border border-gray-200 dark:border-gray-700 bg-white/80 dark:bg-gray-800/80 text-gray-900 dark:text-white outline-none focus:ring-2 focus:ring-blue-500"
              placeholder="再次输入新密码"
            />
          </div>

          <div v-if="message" class="text-sm text-emerald-500">{{ message }}</div>
          <div v-if="error" class="text-sm text-red-500">{{ error }}</div>

          <button
            type="submit"
            :disabled="submitting"
            class="w-full btn-primary justify-center py-3 disabled:opacity-60 disabled:cursor-not-allowed"
          >
            {{ submitting ? '提交中...' : '重置密码' }}
          </button>
        </form>

        <div class="mt-6 flex items-center justify-between text-sm">
          <router-link to="/" class="text-gray-500 dark:text-gray-400 hover:text-blue-500 transition-colors">返回首页</router-link>
          <router-link to="/login" class="text-blue-600 dark:text-blue-400 hover:underline">返回登录</router-link>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const authStore = useAuthStore()

const mode = ref<'phone' | 'email'>('email')
const identifier = ref('')
const code = ref('')
const newPassword = ref('')
const confirmPassword = ref('')
const debugCode = ref('')
const message = ref('')
const error = ref('')
const sendingCode = ref(false)
const submitting = ref(false)
const countdown = ref(0)
let countdownTimer: number | null = null

const activeTabClass = 'bg-white dark:bg-gray-700 text-blue-600 dark:text-blue-300 shadow-sm'
const inactiveTabClass = 'text-gray-500 dark:text-gray-400'

const description = computed(() => mode.value === 'phone'
  ? '使用已绑定手机号和验证码重置密码。'
  : '使用已绑定邮箱和验证码重置密码。')

const tabClass = computed(() => authStore.smsLoginEnabled ? 'grid grid-cols-2 gap-2' : 'grid grid-cols-1 gap-2')

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
  message.value = ''
  debugCode.value = ''
  sendingCode.value = true
  try {
    if (mode.value === 'phone') {
      if (!/^1\\d{10}$/.test(identifier.value.trim())) {
        error.value = '请输入正确的手机号'
        return
      }
      const result = await authStore.sendPasswordResetPhoneCode(identifier.value.trim())
      if (!result.success) {
        error.value = result.message || '验证码发送失败'
        return
      }
      message.value = result.message || '验证码已发送'
      debugCode.value = result.debugCode || ''
      startCountdown(result.expiresInSeconds ? Math.min(result.expiresInSeconds, 60) : 60)
      return
    }

    if (!/^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$/.test(identifier.value.trim())) {
      error.value = '请输入正确的邮箱地址'
      return
    }
    const result = await authStore.sendPasswordResetEmailCode(identifier.value.trim())
    if (!result.success) {
      error.value = result.message || '验证码发送失败'
      return
    }
    message.value = result.message || '验证码已发送'
    debugCode.value = result.debugCode || ''
    startCountdown(result.expiresInSeconds ? Math.min(result.expiresInSeconds, 60) : 60)
  } finally {
    sendingCode.value = false
  }
}

const handleSubmit = async () => {
  error.value = ''
  message.value = ''
  submitting.value = true
  try {
    if (newPassword.value.length < 6) {
      error.value = '新密码长度不能少于6位'
      return
    }
    if (newPassword.value !== confirmPassword.value) {
      error.value = '两次输入的密码不一致'
      return
    }

    const result = mode.value === 'phone'
      ? await authStore.resetPasswordByPhone({
          phone: identifier.value.trim(),
          code: code.value.trim(),
          newPassword: newPassword.value,
          confirmPassword: confirmPassword.value
        })
      : await authStore.resetPasswordByEmail({
          email: identifier.value.trim(),
          code: code.value.trim(),
          newPassword: newPassword.value,
          confirmPassword: confirmPassword.value
        })

    if (!result.success) {
      error.value = result.message || '重置密码失败'
      return
    }
    message.value = result.message || '密码已重置'
    window.setTimeout(() => router.push('/login'), 800)
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  const settings = await authStore.fetchPublicSettings()
  if (settings?.smsLoginEnabled) {
    mode.value = 'phone'
  } else {
    mode.value = 'email'
  }
})

onBeforeUnmount(() => {
  if (countdownTimer) {
    window.clearInterval(countdownTimer)
  }
})
</script>
