<template>
  <div class="min-h-screen bg-white dark:bg-gray-900 px-4 py-10">
    <div class="max-w-2xl mx-auto">
      <div class="glass-panel p-8">
        <div class="text-center mb-8">
          <h1 class="text-3xl font-light text-gray-900 dark:text-white">创建账号</h1>
          <p class="mt-2 text-sm text-gray-500 dark:text-gray-400">
            {{ forceBindPhone ? '当前系统要求注册时绑定手机号，后续可直接使用短信验证码登录。' : '当前支持账号密码注册，手机号绑定与短信登录可选使用。' }}
          </p>
        </div>

        <form class="grid grid-cols-1 md:grid-cols-2 gap-5" @submit.prevent="handleRegister">
          <div class="md:col-span-1">
            <label class="block text-sm mb-2 text-gray-700 dark:text-gray-300">用户名</label>
            <input v-model="form.username" type="text" required class="w-full px-4 py-3 rounded-xl border border-gray-200 dark:border-gray-700 bg-white/80 dark:bg-gray-800/80 text-gray-900 dark:text-white outline-none focus:ring-2 focus:ring-blue-500" placeholder="3-50 位，支持字母/数字/下划线" />
          </div>
          <div class="md:col-span-1">
            <label class="block text-sm mb-2 text-gray-700 dark:text-gray-300">公开标识</label>
            <input v-model="form.slug" type="text" class="w-full px-4 py-3 rounded-xl border border-gray-200 dark:border-gray-700 bg-white/80 dark:bg-gray-800/80 text-gray-900 dark:text-white outline-none focus:ring-2 focus:ring-blue-500" placeholder="可选，例如 zhangsan" />
          </div>
          <div class="md:col-span-1">
            <label class="block text-sm mb-2 text-gray-700 dark:text-gray-300">昵称</label>
            <input v-model="form.nickname" type="text" class="w-full px-4 py-3 rounded-xl border border-gray-200 dark:border-gray-700 bg-white/80 dark:bg-gray-800/80 text-gray-900 dark:text-white outline-none focus:ring-2 focus:ring-blue-500" placeholder="对外显示的昵称" />
          </div>
          <div class="md:col-span-1">
            <label class="block text-sm mb-2 text-gray-700 dark:text-gray-300">邮箱</label>
            <input v-model="form.email" type="email" class="w-full px-4 py-3 rounded-xl border border-gray-200 dark:border-gray-700 bg-white/80 dark:bg-gray-800/80 text-gray-900 dark:text-white outline-none focus:ring-2 focus:ring-blue-500" placeholder="可选，例如 user@example.com" />
          </div>
          <div class="md:col-span-1">
            <label class="block text-sm mb-2 text-gray-700 dark:text-gray-300">
              手机号 <span v-if="forceBindPhone" class="text-red-500">*</span>
            </label>
            <input
              v-model="form.phone"
              type="text"
              :required="forceBindPhone"
              class="w-full px-4 py-3 rounded-xl border border-gray-200 dark:border-gray-700 bg-white/80 dark:bg-gray-800/80 text-gray-900 dark:text-white outline-none focus:ring-2 focus:ring-blue-500"
              :placeholder="forceBindPhone ? '必填，例如 13800138000' : '可选，例如 13800138000'"
            />
          </div>
          <div class="md:col-span-1">
            <label class="block text-sm mb-2 text-gray-700 dark:text-gray-300">密码</label>
            <input v-model="form.password" type="password" required class="w-full px-4 py-3 rounded-xl border border-gray-200 dark:border-gray-700 bg-white/80 dark:bg-gray-800/80 text-gray-900 dark:text-white outline-none focus:ring-2 focus:ring-blue-500" placeholder="至少 6 位密码" />
          </div>
          <div class="md:col-span-1">
            <label class="block text-sm mb-2 text-gray-700 dark:text-gray-300">确认密码</label>
            <input v-model="form.confirmPassword" type="password" required class="w-full px-4 py-3 rounded-xl border border-gray-200 dark:border-gray-700 bg-white/80 dark:bg-gray-800/80 text-gray-900 dark:text-white outline-none focus:ring-2 focus:ring-blue-500" placeholder="再次输入上面的密码" />
          </div>

          <div class="md:col-span-2">
            <div v-if="error" class="text-sm text-red-500 mb-3">{{ error }}</div>
            <button type="submit" :disabled="loading" class="w-full btn-primary justify-center py-3 disabled:opacity-60 disabled:cursor-not-allowed">
              {{ loading ? '注册中...' : '注册并登录' }}
            </button>
          </div>
        </form>

        <div class="mt-6 flex items-center justify-between text-sm">
          <router-link to="/" class="text-gray-500 dark:text-gray-400 hover:text-blue-500 transition-colors">返回首页</router-link>
          <router-link to="/login" class="text-blue-600 dark:text-blue-400 hover:underline">已有账号？去登录</router-link>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { getDefaultPostAuthPath } from '@/utils/publicRoute'

const router = useRouter()
const authStore = useAuthStore()

const loading = ref(false)
const error = ref('')
const forceBindPhone = ref(false)
const form = reactive({
  username: '',
  slug: '',
  nickname: '',
  email: '',
  phone: '',
  password: '',
  confirmPassword: ''
})

const handleRegister = async () => {
  loading.value = true
  error.value = ''
  try {
    if (forceBindPhone.value && !/^1\d{10}$/.test(form.phone.trim())) {
      error.value = '当前系统要求绑定有效手机号'
      return
    }

    const result = await authStore.register({
      username: form.username.trim(),
      slug: form.slug.trim() || undefined,
      nickname: form.nickname.trim() || undefined,
      email: form.email.trim() || undefined,
      phone: form.phone.trim() || undefined,
      password: form.password,
      confirmPassword: form.confirmPassword
    })

    if (!result.success) {
      error.value = result.message || '注册失败'
      return
    }

    router.push(getDefaultPostAuthPath(authStore.slug, authStore.multiUserEnabled))
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  try {
    const data = await authStore.fetchPublicSettings()
    forceBindPhone.value = !!data?.forceBindPhone
    if (!data?.multiUserEnabled) {
      router.replace('/login')
    }
  } catch {
    forceBindPhone.value = false
  }
})
</script>
