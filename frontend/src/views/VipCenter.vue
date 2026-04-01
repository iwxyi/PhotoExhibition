<template>
  <div class="min-h-screen bg-white dark:bg-gray-900">
    <nav class="sticky top-0 z-50 bg-white/80 dark:bg-gray-900/80 backdrop-blur-md border-b border-gray-200 dark:border-gray-800">
      <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div class="flex justify-between items-center h-12">
          <AppHeader />
          <div class="flex items-center gap-3">
            <PublicAccountMenu />
          </div>
        </div>
      </div>
    </nav>

    <main class="max-w-6xl mx-auto px-4 sm:px-6 lg:px-8 py-10">
      <div class="glass-panel p-8 space-y-6">
        <div class="flex items-center justify-between gap-4 flex-wrap">
          <div>
            <h1 class="text-3xl font-light text-gray-900 dark:text-white">会员中心</h1>
            <p class="mt-2 text-sm text-gray-500 dark:text-gray-400">独立查看配额、套餐、订单和支付预览。当前已支持自助下单与 Mock 支付联调。</p>
          </div>
          <div class="flex items-center gap-3 flex-wrap">
            <router-link to="/profile" class="px-4 py-2 rounded-lg border border-gray-200 dark:border-gray-700 text-sm text-gray-700 dark:text-gray-200 hover:bg-gray-50 dark:hover:bg-gray-800">
              返回个人资料
            </router-link>
            <button class="btn-primary disabled:opacity-60" :disabled="loading" @click="loadData">
              {{ loading ? '刷新中...' : '刷新会员数据' }}
            </button>
          </div>
        </div>

        <div v-if="message" class="text-sm" :class="messageType === 'success' ? 'text-emerald-500' : 'text-rose-500'">
          {{ message }}
        </div>

        <div v-if="overview" class="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-5 gap-4">
          <div class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white/80 dark:bg-gray-900/50 p-4">
            <div class="text-xs text-gray-500 dark:text-gray-400">当前套餐</div>
            <div class="mt-2 text-lg text-gray-900 dark:text-white">{{ overview.currentVipPlanName || '未开通' }}</div>
          </div>
          <div class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white/80 dark:bg-gray-900/50 p-4">
            <div class="text-xs text-gray-500 dark:text-gray-400">总配额</div>
            <div class="mt-2 text-lg text-gray-900 dark:text-white">{{ formatGb(overview.storageQuotaBytes) }}</div>
          </div>
          <div class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white/80 dark:bg-gray-900/50 p-4">
            <div class="text-xs text-gray-500 dark:text-gray-400">已使用</div>
            <div class="mt-2 text-lg text-gray-900 dark:text-white">{{ formatGb(overview.storageUsedBytes) }}</div>
          </div>
          <div class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white/80 dark:bg-gray-900/50 p-4">
            <div class="text-xs text-gray-500 dark:text-gray-400">管理员附加</div>
            <div class="mt-2 text-lg text-gray-900 dark:text-white">{{ formatGb(overview.vipExtraQuotaBytes) }}</div>
          </div>
          <div class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white/80 dark:bg-gray-900/50 p-4">
            <div class="text-xs text-gray-500 dark:text-gray-400">到期时间</div>
            <div class="mt-2 text-lg text-gray-900 dark:text-white">{{ formatDate(overview.vipExpireAt) }}</div>
          </div>
        </div>

        <div class="rounded-2xl border border-dashed border-gray-200 dark:border-gray-700 p-5 space-y-2">
          <div class="text-sm text-gray-900 dark:text-white">支付设置摘要</div>
          <div class="text-xs text-gray-500 dark:text-gray-400">
            支付平台：{{ paymentProviderLabel(overview?.paymentProviderType) }} · 支付开关 {{ overview?.paymentEnabled ? '开启' : '关闭' }} · Mock {{ overview?.paymentMockEnabled ? '开启' : '关闭' }}
          </div>
        </div>

        <section class="space-y-4">
          <div class="flex items-center justify-between gap-3 flex-wrap">
            <h2 class="text-lg font-light text-gray-900 dark:text-white">可购买套餐</h2>
            <span class="text-xs text-gray-500 dark:text-gray-400">仅展示已启用套餐</span>
          </div>
          <div v-if="!plans.length" class="text-sm text-gray-500 dark:text-gray-400">暂无可购买套餐。</div>
          <div v-else class="grid grid-cols-1 lg:grid-cols-2 xl:grid-cols-3 gap-4">
            <article v-for="plan in plans" :key="plan.id" class="rounded-2xl border border-gray-200 dark:border-gray-700 bg-white/80 dark:bg-gray-900/50 p-5 space-y-4">
              <div class="flex items-start justify-between gap-3">
                <div>
                  <div class="text-lg text-gray-900 dark:text-white">{{ plan.name }}</div>
                  <div class="text-xs text-gray-500 dark:text-gray-400">{{ plan.code }} · {{ plan.durationDays }} 天</div>
                </div>
                <div class="text-right">
                  <div class="text-2xl font-light text-gray-900 dark:text-white">¥{{ plan.priceYuan }}</div>
                  <div class="text-xs text-emerald-600 dark:text-emerald-300">+{{ formatGb(plan.extraQuotaBytes) }}</div>
                </div>
              </div>
              <p class="text-sm text-gray-500 dark:text-gray-400 min-h-[40px]">{{ plan.description || '暂无套餐说明' }}</p>
              <button class="btn-primary w-full disabled:opacity-60" :disabled="creatingPlanId === plan.id" @click="createOrder(plan.id)">
                {{ creatingPlanId === plan.id ? '创建订单中...' : '立即购买' }}
              </button>
            </article>
          </div>
        </section>

        <section class="space-y-4">
          <div class="flex items-center justify-between gap-3 flex-wrap">
            <h2 class="text-lg font-light text-gray-900 dark:text-white">最近订单</h2>
            <span class="text-xs text-gray-500 dark:text-gray-400">最近 20 条</span>
          </div>
          <div v-if="!orders.length" class="text-sm text-gray-500 dark:text-gray-400">暂无订单。</div>
          <div v-else class="space-y-3">
            <article
              v-for="order in orders"
              :id="`vip-order-${order.orderNo}`"
              :key="order.id"
              class="rounded-2xl border bg-white/80 dark:bg-gray-900/50 p-5 space-y-4 transition-colors"
              :class="isFocusedOrder(order.orderNo)
                ? 'border-indigo-400 dark:border-indigo-500 ring-2 ring-indigo-200/70 dark:ring-indigo-700/40'
                : 'border-gray-200 dark:border-gray-700'"
            >
              <div class="flex items-start justify-between gap-3 flex-wrap">
                <div>
                  <div class="text-sm text-gray-900 dark:text-white">{{ order.orderNo }}</div>
                  <div class="text-xs text-gray-500 dark:text-gray-400">{{ order.vipPlanName || order.vipPlanCode || '-' }} · {{ order.orderStageLabel || order.status }} · ¥{{ Number(order.amountYuan || 0).toFixed(2) }}</div>
                </div>
                <div class="flex gap-2 flex-wrap">
                  <button class="px-3 py-2 rounded-lg border border-gray-200 dark:border-gray-700 text-sm text-gray-700 dark:text-gray-200 hover:bg-gray-50 dark:hover:bg-gray-800 disabled:opacity-50" :disabled="previewingOrderId === order.id" @click="preview(order.id)">
                    {{ previewingOrderId === order.id ? '生成中...' : '支付预览' }}
                  </button>
                  <button class="px-3 py-2 rounded-lg bg-indigo-600 hover:bg-indigo-500 text-sm text-white disabled:opacity-50" :disabled="initiatingOrderId === order.id || !order.canInitiatePayment" @click="initiate(order.id)">
                    {{ initiatingOrderId === order.id ? '发起中...' : '发起支付' }}
                  </button>
                  <button v-if="overview?.paymentMockEnabled" class="px-3 py-2 rounded-lg bg-emerald-600 hover:bg-emerald-500 text-sm text-white disabled:opacity-50" :disabled="mockingOrderId === order.id || !order.canInitiatePayment" @click="mockPay(order.id)">
                    {{ mockingOrderId === order.id ? '处理中...' : 'Mock 支付' }}
                  </button>
                  <button
                    v-if="order.canToggleAutoRenew"
                    class="px-3 py-2 rounded-lg bg-amber-600 hover:bg-amber-500 text-sm text-white disabled:opacity-50"
                    :disabled="togglingAutoRenewId === order.id"
                    @click="toggleAutoRenew(order)"
                  >
                    {{ togglingAutoRenewId === order.id ? '处理中...' : (order.autoRenewEnabled ? '关闭自动续费' : '开启自动续费') }}
                  </button>
                </div>
              </div>
              <div class="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-4 gap-3 text-xs text-gray-500 dark:text-gray-400">
                <div>支付渠道：{{ paymentProviderLabel(order.paymentProviderType) }}</div>
                <div>外部单号：{{ order.externalTradeNo || '-' }}</div>
                <div>网关状态：{{ order.gatewayStatus || order.status || '-' }}</div>
                <div>支付时间：{{ formatDate(order.paidAt) }}</div>
                <div>退款状态：{{ order.refundStatus || '-' }}</div>
                <div>退款时间：{{ formatDate(order.refundedAt) }}</div>
                <div>取消时间：{{ formatDate(order.cancelledAt) }}</div>
                <div>到期时间：{{ formatDate(order.expireAt) }}</div>
                <div>自动续费：{{ order.autoRenewEnabled ? '开启' : '关闭' }}</div>
                <div>下次续费：{{ formatDate(order.nextRenewalAt) }}</div>
                <div>
                  续费来源单：
                  <router-link
                    v-if="order.renewalSourceOrderNo"
                    :to="buildVipCenterFocusRoute(order.renewalSourceOrderNo)"
                    class="text-indigo-500 hover:text-indigo-400"
                  >
                    {{ order.renewalSourceOrderNo }}
                  </router-link>
                  <span v-else>{{ order.renewalSourceOrderId || '-' }}</span>
                </div>
                <div>
                  续费子单：
                  <router-link
                    v-if="order.renewalChildOrderNo"
                    :to="buildVipCenterFocusRoute(order.renewalChildOrderNo)"
                    class="text-indigo-500 hover:text-indigo-400"
                  >
                    {{ order.renewalChildOrderNo }}
                  </router-link>
                  <span v-else>-</span>
                </div>
                <div>待续费：{{ order.dueForRenewal ? '是' : '否' }}</div>
                <div>订单链路：{{ order.renewalChainType === 'RENEWAL_CHILD' ? '续费子单' : '主订单' }}</div>
              </div>
              <div class="flex justify-end">
                <router-link
                  :to="buildResultRoute(order)"
                  class="text-xs text-indigo-500 hover:text-indigo-400"
                >
                  查看支付结果页
                </router-link>
              </div>
            </article>
          </div>
        </section>

        <section v-if="checkoutPreview" class="space-y-4">
          <div class="flex items-center justify-between gap-3 flex-wrap">
            <div>
              <h2 class="text-lg font-light text-gray-900 dark:text-white">支付预览</h2>
              <div class="text-xs text-gray-500 dark:text-gray-400">{{ checkoutPreview.order.orderNo }} · {{ checkoutPreview.payment.providerLabel }}</div>
            </div>
            <button class="px-3 py-2 rounded-lg border border-gray-200 dark:border-gray-700 text-sm text-gray-700 dark:text-gray-200 hover:bg-gray-50 dark:hover:bg-gray-800" @click="checkoutPreview = null">
              关闭
            </button>
          </div>
          <div class="grid grid-cols-1 xl:grid-cols-2 gap-4">
            <pre class="rounded-xl border border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-950/50 p-4 text-xs text-gray-700 dark:text-gray-200 overflow-auto">{{ JSON.stringify(checkoutPreview.payment.requestPayload, null, 2) }}</pre>
            <pre class="rounded-xl border border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-950/50 p-4 text-xs text-gray-700 dark:text-gray-200 overflow-auto">{{ JSON.stringify(checkoutPreview.payment.callbackPayload, null, 2) }}</pre>
          </div>
          <div class="flex justify-end">
            <router-link
              :to="buildResultRoute(checkoutPreview.order)"
              class="px-4 py-2 rounded-lg border border-gray-200 dark:border-gray-700 text-sm text-gray-700 dark:text-gray-200 hover:bg-gray-50 dark:hover:bg-gray-800"
            >
              打开支付结果页
            </router-link>
          </div>
        </section>

        <section v-if="paymentInitiation" class="space-y-4">
          <div class="flex items-center justify-between gap-3 flex-wrap">
            <div>
              <h2 class="text-lg font-light text-gray-900 dark:text-white">支付发起骨架</h2>
              <div class="text-xs text-gray-500 dark:text-gray-400">{{ paymentInitiation.providerLabel }} · {{ paymentInitiation.orderNo }}</div>
            </div>
            <button class="px-3 py-2 rounded-lg border border-gray-200 dark:border-gray-700 text-sm text-gray-700 dark:text-gray-200 hover:bg-gray-50 dark:hover:bg-gray-800" @click="paymentInitiation = null">
              关闭
            </button>
          </div>
          <div class="grid grid-cols-1 md:grid-cols-2 gap-4 text-sm">
            <div class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white/80 dark:bg-gray-900/50 p-4 space-y-2">
              <div>发起地址：{{ paymentInitiation.launchUrl }}</div>
              <div>请求方式：{{ paymentInitiation.httpMethod }}</div>
              <div>拉起类型：{{ paymentInitiation.actionType || 'API_REQUEST' }}</div>
              <div>是否跳转：{{ paymentInitiation.redirect ? '是' : '否' }}</div>
              <div>状态：{{ paymentInitiation.message }}</div>
            </div>
            <pre class="rounded-xl border border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-950/50 p-4 text-xs text-gray-700 dark:text-gray-200 overflow-auto">{{ JSON.stringify(paymentInitiation.payload, null, 2) }}</pre>
          </div>
          <div v-if="paymentInitiation.formFields || paymentInitiation.headers || paymentInitiation.qrCodeText || paymentInitiation.payload?.requestBodyJson || paymentInitiation.payload?.requestBodyEncoded" class="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-4 gap-4">
            <pre v-if="paymentInitiation.formFields" class="rounded-xl border border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-950/50 p-4 text-xs text-gray-700 dark:text-gray-200 overflow-auto">{{ JSON.stringify(paymentInitiation.formFields, null, 2) }}</pre>
            <pre v-if="paymentInitiation.headers" class="rounded-xl border border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-950/50 p-4 text-xs text-gray-700 dark:text-gray-200 overflow-auto">{{ JSON.stringify(paymentInitiation.headers, null, 2) }}</pre>
            <pre v-if="paymentInitiation.qrCodeText" class="rounded-xl border border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-950/50 p-4 text-xs text-gray-700 dark:text-gray-200 overflow-auto">{{ paymentInitiation.qrCodeText }}</pre>
            <pre v-if="paymentInitiation.payload?.requestBodyJson" class="rounded-xl border border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-950/50 p-4 text-xs text-gray-700 dark:text-gray-200 overflow-auto">{{ paymentInitiation.payload.requestBodyJson }}</pre>
            <pre v-if="paymentInitiation.payload?.requestBodyEncoded" class="rounded-xl border border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-950/50 p-4 text-xs text-gray-700 dark:text-gray-200 overflow-auto break-all whitespace-pre-wrap">{{ paymentInitiation.payload.requestBodyEncoded }}</pre>
          </div>
        </section>
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import type { RouteLocationRaw } from 'vue-router'
import { useRoute } from 'vue-router'
import AppHeader from '@/components/AppHeader.vue'
import PublicAccountMenu from '@/components/PublicAccountMenu.vue'
import { authProfileApi, type PaymentInitiationResponse, type UserVipCheckoutPreview, type UserVipOverview, type UserVipPlan, type VipOrderSummary } from '@/api'
import { useAuthStore } from '@/stores/auth'
import { paymentProviderLabel } from '@/utils/providerLabels'

const authStore = useAuthStore()
const route = useRoute()
const loading = ref(false)
const message = ref('')
const messageType = ref<'success' | 'error'>('success')
const overview = ref<UserVipOverview | null>(null)
const plans = ref<UserVipPlan[]>([])
const orders = ref<VipOrderSummary[]>([])
const checkoutPreview = ref<UserVipCheckoutPreview | null>(null)
const creatingPlanId = ref<number | null>(null)
const previewingOrderId = ref<number | null>(null)
const mockingOrderId = ref<number | null>(null)
const initiatingOrderId = ref<number | null>(null)
const paymentInitiation = ref<PaymentInitiationResponse | null>(null)
const togglingAutoRenewId = ref<number | null>(null)
const focusedOrderNo = computed(() => String(route.query.focusOrderNo || '').trim())

const formatGb = (value?: number | null) => `${Math.round((Number(value || 0) / 1024 / 1024 / 1024) * 100) / 100} GB`
const formatDate = (value?: string | null) => {
  if (!value) return '-'
  const normalized = value.includes('T') ? value : value.replace(' ', 'T')
  const date = new Date(normalized)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString()
}

const buildResultRoute = (order: Pick<VipOrderSummary, 'orderNo' | 'paymentProviderType'>): RouteLocationRaw => ({
  name: 'PaymentResult',
  query: {
    orderNo: order.orderNo,
    providerType: order.paymentProviderType || overview.value?.paymentProviderType || 'ALIPAY'
  }
})

const buildVipCenterFocusRoute = (orderNo?: string | null): RouteLocationRaw => ({
  name: 'VipCenter',
  query: orderNo ? { focusOrderNo: orderNo } : {}
})

const isFocusedOrder = (orderNo?: string | null) => !!orderNo && !!focusedOrderNo.value && orderNo === focusedOrderNo.value

const scrollToFocusedOrder = async () => {
  if (!focusedOrderNo.value) return
  await nextTick()
  document.getElementById(`vip-order-${focusedOrderNo.value}`)?.scrollIntoView({ behavior: 'smooth', block: 'center' })
}

const loadData = async () => {
  loading.value = true
  try {
    await authStore.fetchCurrentUser()
    const [overviewRes, plansRes, ordersRes] = await Promise.all([
      authProfileApi.getVipOverview(),
      authProfileApi.getVipPlans(),
      authProfileApi.getVipOrders()
    ])
    overview.value = overviewRes.data
    plans.value = plansRes.data?.plans || []
    orders.value = ordersRes.data?.orders || []
    await scrollToFocusedOrder()
  } catch (error: any) {
    messageType.value = 'error'
    message.value = error?.response?.data?.error || error?.message || '加载会员信息失败'
  } finally {
    loading.value = false
  }
}

const createOrder = async (planId: number) => {
  creatingPlanId.value = planId
  try {
    const { data } = await authProfileApi.createVipOrder(planId)
    await loadData()
    messageType.value = 'success'
    message.value = `已创建订单 ${data.orderNo}`
  } catch (error: any) {
    messageType.value = 'error'
    message.value = error?.response?.data?.error || error?.message || '创建订单失败'
  } finally {
    creatingPlanId.value = null
  }
}

const preview = async (orderId: number) => {
  previewingOrderId.value = orderId
  try {
    const { data } = await authProfileApi.getVipCheckout(orderId)
    checkoutPreview.value = data
  } catch (error: any) {
    messageType.value = 'error'
    message.value = error?.response?.data?.error || error?.message || '生成支付预览失败'
  } finally {
    previewingOrderId.value = null
  }
}

const initiate = async (orderId: number) => {
  initiatingOrderId.value = orderId
  try {
    const { data } = await authProfileApi.initiateVipCheckout(orderId)
    paymentInitiation.value = data
    messageType.value = 'success'
    message.value = data.message || '支付发起骨架已生成'
  } catch (error: any) {
    messageType.value = 'error'
    message.value = error?.response?.data?.error || error?.message || '发起支付失败'
  } finally {
    initiatingOrderId.value = null
  }
}

const mockPay = async (orderId: number) => {
  mockingOrderId.value = orderId
  try {
    await authProfileApi.mockPayVipOrder(orderId)
    await loadData()
    if (checkoutPreview.value?.order.id === orderId) {
      checkoutPreview.value = await authProfileApi.getVipCheckout(orderId).then(res => res.data)
    }
    messageType.value = 'success'
    message.value = 'Mock 支付成功'
  } catch (error: any) {
    messageType.value = 'error'
    message.value = error?.response?.data?.error || error?.message || 'Mock 支付失败'
  } finally {
    mockingOrderId.value = null
  }
}

const toggleAutoRenew = async (order: VipOrderSummary) => {
  togglingAutoRenewId.value = order.id
  try {
    await authProfileApi.updateVipOrderAutoRenew(order.id, !order.autoRenewEnabled)
    await loadData()
    messageType.value = 'success'
    message.value = `订单 ${order.orderNo} 已${order.autoRenewEnabled ? '关闭' : '开启'}自动续费`
  } catch (error: any) {
    messageType.value = 'error'
    message.value = error?.response?.data?.error || error?.message || '更新自动续费失败'
  } finally {
    togglingAutoRenewId.value = null
  }
}

onMounted(loadData)

watch(() => route.query.focusOrderNo, () => {
  scrollToFocusedOrder()
})
</script>
