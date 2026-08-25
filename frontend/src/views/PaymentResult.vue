<template>
  <div class="min-h-screen bg-white dark:bg-gray-900">
    <nav class="sticky top-0 z-50 bg-white/80 dark:bg-gray-900/80 backdrop-blur-md border-b border-gray-200 dark:border-gray-800">
      <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div class="flex justify-between items-center h-12">
          <AppHeader />
          <PublicAccountMenu />
        </div>
      </div>
    </nav>

    <main class="max-w-3xl mx-auto px-4 sm:px-6 lg:px-8 py-10">
      <div class="glass-panel p-8 space-y-6">
        <div class="flex items-start justify-between gap-4 flex-wrap">
          <div>
            <h1 class="text-3xl font-light text-gray-900 dark:text-white">支付结果</h1>
            <p class="mt-2 text-sm text-gray-500 dark:text-gray-400">用于联调支付返回页与订单状态回查，当前展示的是系统侧记录结果。</p>
          </div>
          <div class="flex gap-3">
            <router-link
              v-if="authStore.isAuthenticated"
              to="/vip"
              class="px-4 py-2 rounded-lg border border-gray-200 dark:border-gray-700 text-sm text-gray-700 dark:text-gray-200 hover:bg-gray-50 dark:hover:bg-gray-800"
            >
              返回会员中心
            </router-link>
            <router-link
              v-else
              :to="loginRoute"
              class="px-4 py-2 rounded-lg border border-gray-200 dark:border-gray-700 text-sm text-gray-700 dark:text-gray-200 hover:bg-gray-50 dark:hover:bg-gray-800"
            >
              去登录
            </router-link>
            <button class="btn-primary disabled:opacity-60" :disabled="loading" @click="loadResult">
              {{ loading ? '查询中...' : '刷新结果' }}
            </button>
          </div>
        </div>

        <div v-if="message" class="text-sm" :class="messageType === 'success' ? 'text-emerald-500' : 'text-rose-500'">
          {{ message }}
        </div>

        <div
          v-if="shouldAutoPoll"
          class="rounded-xl border border-sky-200/70 dark:border-sky-800/70 bg-sky-50/80 dark:bg-sky-950/30 px-4 py-3 text-sm text-sky-700 dark:text-sky-200 flex items-center justify-between gap-3 flex-wrap"
        >
          <div>
            正在自动轮询支付状态，剩余 {{ pollRemainingSeconds }} 秒
            <span v-if="pollAttempt > 0"> · 第 {{ pollAttempt }} 次</span>
          </div>
          <button
            class="px-3 py-1.5 rounded-lg border border-sky-300/70 dark:border-sky-700 text-xs hover:bg-sky-100 dark:hover:bg-sky-900/40"
            @click="stopPolling"
          >
            停止自动刷新
          </button>
        </div>

        <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white/80 dark:bg-gray-900/50 p-4">
            <div class="text-xs text-gray-500 dark:text-gray-400">支付平台</div>
            <div class="mt-2 text-lg text-gray-900 dark:text-white">{{ result?.providerLabel || paymentProviderLabel(providerType) }}</div>
          </div>
          <div class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white/80 dark:bg-gray-900/50 p-4">
            <div class="text-xs text-gray-500 dark:text-gray-400">订单号</div>
            <div class="mt-2 text-lg text-gray-900 dark:text-white break-all">{{ orderNo || '-' }}</div>
          </div>
          <div class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white/80 dark:bg-gray-900/50 p-4">
            <div class="text-xs text-gray-500 dark:text-gray-400">支付状态</div>
            <div class="mt-2 flex items-center gap-2 flex-wrap">
              <span class="text-lg" :class="result?.paid ? 'text-emerald-500' : 'text-amber-500'">
                {{ result?.status || 'UNKNOWN' }}
              </span>
              <span
                class="inline-flex items-center px-2.5 py-1 rounded-full text-xs border"
                :class="statusBadgeClass"
              >
                {{ statusBadgeText }}
              </span>
            </div>
          </div>
          <div class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white/80 dark:bg-gray-900/50 p-4">
            <div class="text-xs text-gray-500 dark:text-gray-400">系统判断</div>
            <div class="mt-2 text-lg text-gray-900 dark:text-white">{{ result?.paid ? '已支付' : '未支付/待确认' }}</div>
            <div class="mt-2 text-xs text-gray-500 dark:text-gray-400">阶段：{{ result?.orderStageLabel || '-' }}<span v-if="result?.renewalChainType"> · {{ result.renewalChainType === 'RENEWAL_CHILD' ? '续费子单' : '主订单' }}</span></div>
          </div>
        </div>

        <div v-if="result" class="flex flex-wrap gap-3">
          <router-link
            :to="buildVipOrderRoute(orderNo || result.orderNo)"
            class="px-4 py-2 rounded-lg border border-gray-200 dark:border-gray-700 text-sm text-gray-700 dark:text-gray-200 hover:bg-gray-50 dark:hover:bg-gray-800"
          >
            查看当前订单
          </router-link>
          <router-link
            v-if="result.renewalSourceOrderNo"
            :to="buildVipOrderRoute(result.renewalSourceOrderNo)"
            class="px-4 py-2 rounded-lg border border-gray-200 dark:border-gray-700 text-sm text-gray-700 dark:text-gray-200 hover:bg-gray-50 dark:hover:bg-gray-800"
          >
            查看来源单
          </router-link>
          <router-link
            v-if="result.renewalChildOrderNo"
            :to="buildVipOrderRoute(result.renewalChildOrderNo)"
            class="px-4 py-2 rounded-lg border border-gray-200 dark:border-gray-700 text-sm text-gray-700 dark:text-gray-200 hover:bg-gray-50 dark:hover:bg-gray-800"
          >
            查看续费子单
          </router-link>
          <router-link
            v-if="result.canInitiatePayment"
            :to="buildVipOrderRoute(orderNo || result.orderNo)"
            class="px-4 py-2 rounded-lg bg-indigo-600 hover:bg-indigo-500 text-sm text-white"
          >
            去继续支付
          </router-link>
          <router-link
            v-if="result.orderNo && authStore.isAuthenticated"
            :to="buildVipOrderRoute(result.orderNo)"
            class="px-4 py-2 rounded-lg border border-indigo-200 dark:border-indigo-800 text-sm text-indigo-600 dark:text-indigo-300 hover:bg-indigo-50 dark:hover:bg-indigo-950/40"
          >
            去会员中心定位
          </router-link>
          <router-link
            v-if="result.orderNo && authStore.isSuperAdmin"
            :to="buildSuperAdminVipOrderRoute(result.orderNo)"
            class="px-4 py-2 rounded-lg border border-violet-200 dark:border-violet-800 text-sm text-violet-600 dark:text-violet-300 hover:bg-violet-50 dark:hover:bg-violet-950/40"
          >
            去超管订单页
          </router-link>
        </div>

        <div class="rounded-xl border border-dashed border-gray-200 dark:border-gray-700 p-5 space-y-2 text-sm text-gray-500 dark:text-gray-400">
          <div>{{ result?.message || '支付返回页等待查询结果。' }}</div>
          <div>若第三方已回跳但状态仍未变更，请继续检查支付回调、验签配置与订单回写逻辑。</div>
        </div>

        <div v-if="result?.recommendedActions?.length" class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white/80 dark:bg-gray-900/50 p-4 space-y-3">
          <div class="text-sm text-gray-900 dark:text-white">建议动作</div>
          <ul class="space-y-2 text-sm text-gray-600 dark:text-gray-300">
            <li v-for="(action, index) in result.recommendedActions" :key="`${index}-${action}`" class="flex gap-2">
              <span class="text-indigo-500">•</span>
              <span>{{ action }}</span>
            </li>
          </ul>
          <div v-if="actionLinks.length" class="flex flex-wrap gap-3 pt-1">
            <router-link
              v-for="action in actionLinks"
              :key="action.label"
              :to="action.to"
              class="px-3 py-2 rounded-lg border border-gray-200 dark:border-gray-700 text-sm text-gray-700 dark:text-gray-200 hover:bg-gray-50 dark:hover:bg-gray-800"
            >
              {{ action.label }}
            </router-link>
          </div>
        </div>

        <div v-if="result" class="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white/80 dark:bg-gray-900/50 p-4 space-y-2 text-sm">
            <div><span class="text-gray-500 dark:text-gray-400">网关状态：</span><span class="text-gray-900 dark:text-white">{{ result.gatewayStatus || '-' }}</span></div>
            <div><span class="text-gray-500 dark:text-gray-400">外部单号：</span><span class="text-gray-900 dark:text-white break-all">{{ result.externalTradeNo || '-' }}</span></div>
            <div><span class="text-gray-500 dark:text-gray-400">订单号来源：</span><span class="text-gray-900 dark:text-white">{{ result.resolvedOrderNoSource || '-' }}</span></div>
            <div><span class="text-gray-500 dark:text-gray-400">通知时间：</span><span class="text-gray-900 dark:text-white">{{ formatDate(result.paymentNotifiedAt) }}</span></div>
            <div><span class="text-gray-500 dark:text-gray-400">支付时间：</span><span class="text-gray-900 dark:text-white">{{ formatDate(result.paidAt) }}</span></div>
            <div><span class="text-gray-500 dark:text-gray-400">取消时间：</span><span class="text-gray-900 dark:text-white">{{ formatDate(result.cancelledAt) }}</span></div>
          </div>
          <div class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white/80 dark:bg-gray-900/50 p-4 space-y-2 text-sm">
            <div><span class="text-gray-500 dark:text-gray-400">退款状态：</span><span class="text-gray-900 dark:text-white">{{ result.refundStatus || '-' }}</span></div>
            <div><span class="text-gray-500 dark:text-gray-400">退款金额：</span><span class="text-gray-900 dark:text-white">{{ formatFen(result.refundAmountFen) }}</span></div>
            <div><span class="text-gray-500 dark:text-gray-400">续费来源单：</span><span class="text-gray-900 dark:text-white">{{ result.renewalSourceOrderNo || result.renewalSourceOrderId || '-' }}</span><span v-if="result.renewalSourceOrderStatus" class="text-gray-500 dark:text-gray-400"> · {{ result.renewalSourceOrderStatus }}</span></div>
            <div><span class="text-gray-500 dark:text-gray-400">续费子单：</span><span class="text-gray-900 dark:text-white">{{ result.renewalChildOrderNo || '-' }}</span><span v-if="result.renewalChildOrderStatus" class="text-gray-500 dark:text-gray-400"> · {{ result.renewalChildOrderStatus }}</span></div>
            <div><span class="text-gray-500 dark:text-gray-400">退款时间：</span><span class="text-gray-900 dark:text-white">{{ formatDate(result.refundedAt) }}</span></div>
          </div>
        </div>

        <div v-if="result" class="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white/80 dark:bg-gray-900/50 p-4 space-y-2 text-sm">
            <div><span class="text-gray-500 dark:text-gray-400">自动续费：</span><span class="text-gray-900 dark:text-white">{{ result.autoRenewEnabled ? '已开启' : '未开启' }}</span></div>
            <div><span class="text-gray-500 dark:text-gray-400">下次续费：</span><span class="text-gray-900 dark:text-white">{{ formatDate(result.nextRenewalAt) }}</span></div>
            <div><span class="text-gray-500 dark:text-gray-400">到期时间：</span><span class="text-gray-900 dark:text-white">{{ formatDate(result.expireAt) }}</span></div>
          </div>
          <div class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white/80 dark:bg-gray-900/50 p-4 space-y-2 text-sm">
            <div><span class="text-gray-500 dark:text-gray-400">可继续支付：</span><span class="text-gray-900 dark:text-white">{{ result.canInitiatePayment ? '是' : '否' }}</span></div>
            <div><span class="text-gray-500 dark:text-gray-400">可改续费：</span><span class="text-gray-900 dark:text-white">{{ result.canToggleAutoRenew ? '是' : '否' }}</span></div>
            <div><span class="text-gray-500 dark:text-gray-400">备注：</span><span class="text-gray-900 dark:text-white break-all">{{ result.remark || '-' }}</span></div>
          </div>
        </div>

        <div v-if="result?.returnQuery && Object.keys(result.returnQuery).length" class="rounded-xl border border-dashed border-gray-200 dark:border-gray-700 p-5 space-y-3">
          <div class="text-sm text-gray-900 dark:text-white">回跳参数</div>
          <pre class="rounded-xl border border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-950/50 p-4 text-xs text-gray-700 dark:text-gray-200 overflow-auto">{{ JSON.stringify(result.returnQuery, null, 2) }}</pre>
        </div>
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { RouteLocationRaw } from 'vue-router'
import AppHeader from '@/components/AppHeader.vue'
import PublicAccountMenu from '@/components/PublicAccountMenu.vue'
import { paymentApi, type PaymentReturnResult } from '@/api'
import { useAuthStore } from '@/stores/auth'
import { paymentProviderLabel } from '@/utils/providerLabels'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const loading = ref(false)
const message = ref('')
const messageType = ref<'success' | 'error'>('success')
const result = ref<PaymentReturnResult | null>(null)
const pollRemainingSeconds = ref(30)
const pollAttempt = ref(0)
let pollTimer: ReturnType<typeof setInterval> | null = null
let countdownTimer: ReturnType<typeof setInterval> | null = null

const providerType = computed<PaymentReturnResult['providerType']>(() => {
  const raw = String(route.query.providerType || 'ALIPAY').toUpperCase()
  const allowed = ['ALIPAY', 'WECHAT_PAY', 'STRIPE', 'PAYPAL', 'UNIONPAY', 'PADDLE', 'LEMON_SQUEEZY', 'ADYEN', 'MOLLIE', 'XENDIT', 'MIDTRANS', 'CUSTOM_WEBHOOK']
  return (allowed.includes(raw) ? raw : 'ALIPAY') as PaymentReturnResult['providerType']
})

const orderNo = computed(() => String(route.query.orderNo || result.value?.orderNo || '').trim())
const returnQueryParams = computed(() => {
  const params: Record<string, any> = {}
  Object.entries(route.query || {}).forEach(([key, value]) => {
    if (value == null) return
    params[key] = value
  })
  if (!params.orderNo && orderNo.value) {
    params.orderNo = orderNo.value
  }
  return params
})
const hasResolvableOrderHint = computed(() => Object.keys(returnQueryParams.value).length > 0)
const shouldAutoPoll = computed(() => hasResolvableOrderHint.value && !result.value?.paid && pollRemainingSeconds.value > 0)
const isTerminalStatus = computed(() => {
  const status = String(result.value?.status || '').toUpperCase()
  return ['PAID', 'ACTIVE', 'CANCELLED', 'CANCELED', 'REFUNDED'].includes(status)
})
const statusBadgeText = computed(() => {
  if (result.value?.paid) return '已完成'
  if (result.value?.status === 'CANCELLED') return '已取消'
  if (result.value?.status === 'REFUNDED') return '已退款'
  if (result.value?.status === 'PAID' || result.value?.status === 'ACTIVE') return '已支付'
  return '待确认'
})
const statusBadgeClass = computed(() => {
  if (result.value?.paid) {
    return 'border-emerald-200 bg-emerald-50 text-emerald-600 dark:border-emerald-800 dark:bg-emerald-950/40 dark:text-emerald-300'
  }
  if (result.value?.status === 'CANCELLED') {
    return 'border-rose-200 bg-rose-50 text-rose-600 dark:border-rose-800 dark:bg-rose-950/40 dark:text-rose-300'
  }
  if (result.value?.status === 'REFUNDED') {
    return 'border-violet-200 bg-violet-50 text-violet-600 dark:border-violet-800 dark:bg-violet-950/40 dark:text-violet-300'
  }
  return 'border-amber-200 bg-amber-50 text-amber-600 dark:border-amber-800 dark:bg-amber-950/40 dark:text-amber-300'
})

const formatDate = (value?: string | null) => {
  if (!value) return '-'
  const normalized = value.includes('T') ? value : value.replace(' ', 'T')
  const date = new Date(normalized)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString()
}

const formatFen = (value?: number | null) => {
  if (value == null) return '-'
  return `¥${(Number(value || 0) / 100).toFixed(2)}`
}

const buildVipOrderRoute = (targetOrderNo?: string | null) => ({
  name: 'VipCenter',
  query: targetOrderNo ? { focusOrderNo: targetOrderNo } : {}
})

const buildSuperAdminVipOrderRoute = (targetOrderNo?: string | null) => ({
  name: 'AdminSuperAdmin',
  query: {
    tab: 'vipOrders',
    focusOrderNo: targetOrderNo || undefined
  }
})

const actionLinks = computed<Array<{ label: string; to: RouteLocationRaw }>>(() => {
  const links: Array<{ label: string; to: RouteLocationRaw }> = []
  const targetOrderNo = result.value?.orderNo || orderNo.value
  if (targetOrderNo && authStore.isAuthenticated) {
    links.push({
      label: '定位当前订单',
      to: buildVipOrderRoute(targetOrderNo)
    })
  }
  if (result.value?.renewalSourceOrderNo && authStore.isAuthenticated) {
    links.push({
      label: '定位来源单',
      to: buildVipOrderRoute(result.value.renewalSourceOrderNo)
    })
  }
  if (result.value?.renewalChildOrderNo && authStore.isAuthenticated) {
    links.push({
      label: '定位续费子单',
      to: buildVipOrderRoute(result.value.renewalChildOrderNo)
    })
  }
  if (targetOrderNo && authStore.isSuperAdmin) {
    links.push({
      label: '超管排查订单',
      to: buildSuperAdminVipOrderRoute(targetOrderNo)
    })
  }
  if (!authStore.isAuthenticated) {
    links.push({
      label: '登录后继续排查',
      to: loginRoute.value
    })
  }
  return links
})

const loginRoute = computed<RouteLocationRaw>(() => {
  const targetOrderNo = result.value?.orderNo || orderNo.value
  const redirect = targetOrderNo
    ? `/vip?focusOrderNo=${encodeURIComponent(targetOrderNo)}`
    : '/vip'
  return {
    name: 'Login',
    query: { redirect }
  }
})

const loadResult = async () => {
  loading.value = true
  try {
    const { data } = await paymentApi.getReturnResult(providerType.value, returnQueryParams.value)
    result.value = data
    const nextQuery: Record<string, any> = { ...route.query }
    let shouldReplaceQuery = false
    if (!nextQuery.providerType && data.providerType) {
      nextQuery.providerType = data.providerType
      shouldReplaceQuery = true
    }
    if (!nextQuery.orderNo && data.orderNo) {
      nextQuery.orderNo = data.orderNo
      shouldReplaceQuery = true
    }
    if (shouldReplaceQuery) {
      router.replace({ query: nextQuery }).catch(() => undefined)
    }
    messageType.value = data.success ? 'success' : 'error'
    message.value = data.message || (data.success ? '支付结果已更新' : '未找到支付结果')
    if (data.paid || ['CANCELLED', 'CANCELED', 'REFUNDED'].includes(String(data.status || '').toUpperCase())) {
      stopPolling()
    }
  } catch (error: any) {
    messageType.value = 'error'
    message.value = error?.response?.data?.error || error?.message || '查询支付结果失败'
  } finally {
    loading.value = false
  }
}

const stopPolling = () => {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
  if (countdownTimer) {
    clearInterval(countdownTimer)
    countdownTimer = null
  }
  if (!result.value?.paid && pollRemainingSeconds.value > 0) {
    pollRemainingSeconds.value = 0
  }
}

const startPolling = () => {
  stopPolling()
  if (!hasResolvableOrderHint.value) return
  const intervalSeconds = Math.max(Number(result.value?.suggestedPollIntervalSeconds || 3), 1)
  pollRemainingSeconds.value = 30
  pollAttempt.value = 0
  countdownTimer = setInterval(() => {
    if (pollRemainingSeconds.value <= 1) {
      stopPolling()
      return
    }
    pollRemainingSeconds.value -= 1
  }, 1000)
  pollTimer = setInterval(async () => {
    if (loading.value) return
    pollAttempt.value += 1
    await loadResult()
  }, intervalSeconds * 1000)
}

onMounted(async () => {
  await loadResult()
  if (hasResolvableOrderHint.value && !isTerminalStatus.value) {
    startPolling()
  }
})

onUnmounted(() => {
  stopPolling()
})
</script>
