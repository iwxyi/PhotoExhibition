<template>
  <section class="glass-panel p-6 space-y-5 admin-api-tool-panel">
    <div>
      <h2 class="text-lg font-light">API测试工具</h2>
      <p class="text-xs text-gray-400">仅超级管理员可用，用于触发高权限维护接口、诊断接口和异步任务。</p>
    </div>

    <div class="space-y-4">
      <div>
        <label class="block text-sm text-gray-400 mb-2">选择API端点</label>
        <select
          v-model="selectedApi"
          class="w-full px-4 py-2 bg-gray-900/70 border border-white/10 rounded-lg text-white focus:outline-none focus:ring-2 focus:ring-purple-500"
        >
          <option value="">-- 选择API --</option>
          <option value="GET /albums">获取所有相册</option>
          <option value="GET /photos">获取所有图片</option>
          <option value="GET /tags">获取所有标签</option>
          <option value="POST /admin/scan">触发扫描</option>
          <option value="POST /admin/scan/force">强制扫描（重新处理所有图片）</option>
          <option value="POST /admin/thumbnails/clear">清空缩略图（重新生成三级缩略图）</option>
          <option value="POST /admin/faces/clear">清空人脸数据（重新生成人脸识别）</option>
          <option value="POST /admin/smart-tags/clear">清空智能标签（重新生成AI标签）</option>
          <option value="POST /admin/cleanup/orphaned">清理删除残留（清理不存在文件的记录）</option>
          <option value="POST /admin/cleanup/duplicate-faces">清理重复人脸（删除同一照片的重复人脸记录）</option>
          <option value="POST /admin/albums/update-times">更新相册时间（重新计算拍摄时间和相册名时间）</option>
          <option value="POST /admin/photos/update-times">更新照片时间（重新从EXIF和路径提取拍摄时间）</option>
          <option value="POST /admin/update-exif-data">更新 EXIF 数值字段（回填历史图片）</option>
          <option value="POST /admin/update-color-categories">更新颜色分类（为历史图片设置颜色分类）</option>
          <option value="POST /admin/recalculate-photo-colors">更新照片颜色（重新计算色调、分类、相册氛围等）</option>
          <option value="POST /admin/ai-analysis/clear-all">清空照片AI分析</option>
          <option value="POST /admin/ai-analysis/update-all">更新所有照片AI分析</option>
          <option value="GET /admin/faces/{id}/similar">相似人脸查询</option>
          <option value="GET /admin/scan/analyze-unscanned">分析未扫描的文件</option>
          <option value="POST /admin/cleanup/all">清理所有数据（只保留账号）</option>
          <option value="POST /admin/background-removal/batch">批量移除背景（抠图处理）</option>
          <option value="DELETE /admin/photos/clear-background-cache">清空抠图缓存（删除所有抠图文件）</option>
        </select>
      </div>

      <div v-if="showPathInput">
        <label class="block text-sm text-gray-400 mb-2">可选：指定扫描路径</label>
        <input
          v-model="pathInput"
          placeholder="不填则使用系统配置的扫描根目录"
          class="w-full px-4 py-2 bg-gray-900/70 border border-white/10 rounded-lg text-white focus:outline-none focus:ring-2 focus:ring-purple-500"
        />
        <div class="mt-2 text-xs text-gray-500">建议填写当前存储根目录下的相对路径，避免误扫整库。</div>
      </div>

      <div v-if="showAlbumIdInput">
        <label class="block text-sm text-gray-400 mb-2">相册ID（选填，留空处理所有图片）</label>
        <input
          v-model="albumIdInput"
          type="number"
          placeholder="留空则处理所有图片"
          class="w-full px-4 py-2 bg-gray-900/70 border border-white/10 rounded-lg text-white focus:outline-none focus:ring-2 focus:ring-purple-500"
        />
        <div class="mt-2 text-xs text-gray-500">建议只对单个相册试跑，确认结果后再批量执行。</div>
      </div>

      <div v-if="showFaceSimilarInputs" class="grid grid-cols-1 sm:grid-cols-3 gap-3">
        <div>
          <label class="block text-sm text-gray-400 mb-1">人脸ID</label>
          <input
            v-model="faceIdInput"
            placeholder="请输入要查询的人脸 ID"
            class="w-full px-3 py-2 bg-gray-900/70 border border-white/10 rounded text-sm focus:outline-none focus:ring-2 focus:ring-purple-500"
          />
          <div class="mt-1 text-[11px] text-gray-500">必填，填数据库中的人脸记录 ID。</div>
        </div>
        <div>
          <label class="block text-sm text-gray-400 mb-1">Top</label>
          <input
            v-model="topInput"
            type="number"
            min="1"
            placeholder="默认 10"
            class="w-full px-3 py-2 bg-gray-900/70 border border-white/10 rounded text-sm focus:outline-none focus:ring-2 focus:ring-purple-500"
          />
          <div class="mt-1 text-[11px] text-gray-500">返回结果条数，默认 10。</div>
        </div>
        <div>
          <label class="block text-sm text-gray-400 mb-1">阈值</label>
          <input
            v-model="thresholdInput"
            type="number"
            step="0.01"
            min="0"
            max="1"
            placeholder="默认 0.6"
            class="w-full px-3 py-2 bg-gray-900/70 border border-white/10 rounded text-sm focus:outline-none focus:ring-2 focus:ring-purple-500"
          />
          <div class="mt-1 text-[11px] text-gray-500">范围 0-1，越高越严格。</div>
        </div>
      </div>

      <button
        @click="testApi"
        :disabled="!selectedApi || testing"
        class="px-4 py-2 bg-purple-600 hover:bg-purple-500 rounded-lg transition-colors disabled:opacity-50"
      >
        {{ testing ? '请求中...' : '发送请求' }}
      </button>

      <div v-if="apiResponse" class="mt-4">
        <label class="block text-sm text-gray-400 mb-2">响应结果</label>
        <pre class="bg-gray-950/80 p-4 rounded-lg overflow-auto text-sm">{{ JSON.stringify(apiResponse, null, 2) }}</pre>
      </div>

      <div v-if="taskStatus" class="mt-4 bg-gray-950/80 p-3 rounded-lg">
        <div class="flex items-center justify-between mb-2 gap-3">
          <div>
            <div class="text-sm text-gray-200">任务 ID: <span class="text-sky-300">{{ taskStatus.taskId }}</span></div>
            <div class="text-xs text-gray-400">状态: <span class="text-sky-300">{{ taskStatus.status }}</span></div>
            <div class="text-xs text-gray-400">进度: <span class="text-sky-300">{{ taskStatus.current }} / {{ taskStatus.total }}</span></div>
          </div>
          <button @click="stopTaskPoll" class="px-2 py-1 text-xs bg-rose-600 hover:bg-rose-500 rounded">停止</button>
        </div>
        <div class="text-xs text-gray-300 max-h-48 overflow-auto">
          <pre class="whitespace-pre-wrap break-words">{{ taskStatus.logs.join('\n') }}</pre>
        </div>
      </div>
    </div>

    <div class="rounded-2xl border border-white/10 bg-white/5 p-5 space-y-4">
      <div>
        <div class="text-base text-white">支付回调调试</div>
        <div class="text-xs text-gray-400 mt-1">用于直接命中统一支付回调 / 返回页入口，联调验签骨架、订单号识别和状态回写。</div>
      </div>

      <div class="grid grid-cols-1 lg:grid-cols-2 gap-4">
        <label class="space-y-2">
          <span class="text-sm text-gray-300">支付平台</span>
          <select v-model="paymentProviderType" class="w-full px-4 py-2 bg-gray-900/70 border border-white/10 rounded-lg">
            <option value="ALIPAY">支付宝</option>
            <option value="WECHAT_PAY">微信支付</option>
            <option value="STRIPE">Stripe</option>
            <option value="PAYPAL">PayPal</option>
            <option value="UNIONPAY">银联</option>
            <option value="PADDLE">Paddle</option>
            <option value="LEMON_SQUEEZY">Lemon Squeezy</option>
            <option value="ADYEN">Adyen</option>
            <option value="MOLLIE">Mollie</option>
            <option value="XENDIT">Xendit</option>
            <option value="MIDTRANS">Midtrans</option>
            <option value="CUSTOM_WEBHOOK">自定义 Webhook</option>
          </select>
        </label>
        <label class="space-y-2">
          <span class="text-sm text-gray-300">返回页查询参数 JSON</span>
          <textarea v-model="paymentReturnQueryJson" rows="4" class="w-full px-4 py-2 bg-gray-900/70 border border-white/10 rounded-lg font-mono text-xs" placeholder='{"orderNo":"VIP202603250001"}' />
        </label>
      </div>

      <div class="grid grid-cols-1 xl:grid-cols-2 gap-4">
        <label class="space-y-2">
          <span class="text-sm text-gray-300">回调负载 JSON</span>
          <textarea v-model="paymentNotifyPayloadJson" rows="9" class="w-full px-4 py-2 bg-gray-900/70 border border-white/10 rounded-lg font-mono text-xs" placeholder='{"orderNo":"VIP202603250001","status":"PAID","mock":true}' />
        </label>
        <label class="space-y-2">
          <span class="text-sm text-gray-300">回调 Header JSON</span>
          <textarea v-model="paymentNotifyHeadersJson" rows="9" class="w-full px-4 py-2 bg-gray-900/70 border border-white/10 rounded-lg font-mono text-xs" placeholder='{"Stripe-Signature":"demo","Wechatpay-Serial":"serial-001"}' />
        </label>
      </div>

      <label class="space-y-2">
        <span class="text-sm text-gray-300">原始回调体（可选）</span>
        <textarea
          v-model="paymentNotifyRawBody"
          rows="5"
          class="w-full px-4 py-2 bg-gray-900/70 border border-white/10 rounded-lg font-mono text-xs"
          placeholder="留空时按上方 JSON 负载发送；填写后会额外携带 rawBody，真实回调可按 text/plain 方式直发"
        />
        <div class="text-[11px] text-gray-500">
          适用于微信支付证书验签、Stripe / 自定义 Webhook、text/plain / form 原始报文联调。
        </div>
      </label>

      <div class="flex flex-wrap gap-3">
        <button
          class="px-4 py-2 bg-emerald-600 hover:bg-emerald-500 rounded-lg transition-colors"
          @click="fillPaymentProviderTemplate"
        >
          填充平台示例
        </button>
        <button
          class="px-4 py-2 bg-violet-600 hover:bg-violet-500 rounded-lg transition-colors disabled:opacity-50"
          :disabled="paymentNotifyPreviewing"
          @click="previewPaymentNotify"
        >
          {{ paymentNotifyPreviewing ? '预演中...' : '预演支付回调' }}
        </button>
        <button
          class="px-4 py-2 bg-indigo-600 hover:bg-indigo-500 rounded-lg transition-colors disabled:opacity-50"
          :disabled="paymentNotifyTesting"
          @click="testPaymentNotify"
        >
          {{ paymentNotifyTesting ? '回调测试中...' : '测试支付回调' }}
        </button>
        <button
          class="px-4 py-2 bg-sky-600 hover:bg-sky-500 rounded-lg transition-colors disabled:opacity-50"
          :disabled="paymentReturnTesting"
          @click="testPaymentReturn"
        >
          {{ paymentReturnTesting ? '返回页测试中...' : '测试返回页' }}
        </button>
        <button
          class="px-4 py-2 bg-gray-700 hover:bg-gray-600 rounded-lg transition-colors"
          @click="copyPreviewCurl"
        >
          复制预演 cURL
        </button>
        <button
          class="px-4 py-2 bg-gray-700 hover:bg-gray-600 rounded-lg transition-colors"
          @click="copyNotifyCurl"
        >
          复制回调 cURL
        </button>
        <button
          class="px-4 py-2 bg-gray-700 hover:bg-gray-600 rounded-lg transition-colors"
          @click="copyReturnCurl"
        >
          复制返回页 cURL
        </button>
        <button
          class="px-4 py-2 bg-amber-700 hover:bg-amber-600 rounded-lg transition-colors"
          @click="exportCurrentPreset"
        >
          导出当前预设
        </button>
        <button
          class="px-4 py-2 bg-rose-700 hover:bg-rose-600 rounded-lg transition-colors"
          @click="resetCurrentPreset"
        >
          重置当前预设
        </button>
      </div>

      <div v-if="copyMessage" class="text-xs" :class="copyMessageType === 'error' ? 'text-rose-300' : 'text-emerald-300'">
        {{ copyMessage }}
      </div>

      <div class="grid grid-cols-1 xl:grid-cols-3 gap-4">
        <div class="rounded-xl border border-white/10 bg-black/20 p-4 space-y-2">
          <div class="text-sm text-gray-300">预演命令</div>
          <pre class="text-xs text-gray-400 whitespace-pre-wrap break-words">{{ previewCurlCommand }}</pre>
        </div>
        <div class="rounded-xl border border-white/10 bg-black/20 p-4 space-y-2">
          <div class="text-sm text-gray-300">真实回调命令</div>
          <pre class="text-xs text-gray-400 whitespace-pre-wrap break-words">{{ notifyCurlCommand }}</pre>
        </div>
        <div class="rounded-xl border border-white/10 bg-black/20 p-4 space-y-2">
          <div class="text-sm text-gray-300">返回页命令</div>
          <pre class="text-xs text-gray-400 whitespace-pre-wrap break-words">{{ returnCurlCommand }}</pre>
        </div>
      </div>

      <div v-if="paymentNotifyPreviewResponse" class="space-y-2">
        <label class="block text-sm text-gray-400">回调预演结果</label>
        <div v-if="paymentPreviewSummary" class="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-5 gap-3">
          <div class="rounded-xl border border-white/10 bg-black/20 p-3">
            <div class="text-[11px] text-gray-400">平台</div>
            <div class="mt-1 text-sm text-white">{{ paymentPreviewSummary.providerLabel || paymentProviderType }}</div>
          </div>
          <div class="rounded-xl border border-white/10 bg-black/20 p-3">
            <div class="text-[11px] text-gray-400">订单号来源</div>
            <div class="mt-1 text-sm text-white break-all">{{ paymentPreviewSummary.resolvedOrderNoSource || '-' }}</div>
          </div>
          <div class="rounded-xl border border-white/10 bg-black/20 p-3">
            <div class="text-[11px] text-gray-400">识别状态</div>
            <div class="mt-1 text-sm text-white">{{ paymentPreviewSummary.recognizedStatus || '-' }}</div>
          </div>
          <div class="rounded-xl border border-white/10 bg-black/20 p-3">
            <div class="text-[11px] text-gray-400">验签模式</div>
            <div class="mt-1 text-sm text-white">{{ paymentPreviewSummary.verificationMode || '-' }}</div>
          </div>
          <div class="rounded-xl border border-white/10 bg-black/20 p-3">
            <div class="text-[11px] text-gray-400">预计动作</div>
            <div class="mt-1 text-sm text-white">{{ paymentPreviewSummary.predictedLifecycleAction || '-' }}</div>
            <div class="mt-1 text-[11px] text-gray-500">
              {{ paymentPreviewSummary.wouldUpdateOrder ? `命中后预计回写为 ${paymentPreviewSummary.predictedFinalStatus || '-'}` : '当前不会直接回写订单' }}
            </div>
          </div>
        </div>
        <div v-if="paymentPreviewSummary?.recommendedActions?.length" class="rounded-xl border border-white/10 bg-black/20 p-4 space-y-2">
          <div class="text-sm text-gray-300">建议动作</div>
          <ul class="space-y-1 text-xs text-gray-300">
            <li v-for="(action, index) in paymentPreviewSummary.recommendedActions" :key="`preview-action-${index}`" class="flex gap-2">
              <span class="text-emerald-400">•</span>
              <span>{{ action }}</span>
            </li>
          </ul>
        </div>
        <pre class="bg-gray-950/80 p-4 rounded-lg overflow-auto text-sm">{{ JSON.stringify(paymentNotifyPreviewResponse, null, 2) }}</pre>
      </div>

      <div v-if="paymentNotifyResponse" class="space-y-2">
        <label class="block text-sm text-gray-400">回调响应</label>
        <pre class="bg-gray-950/80 p-4 rounded-lg overflow-auto text-sm">{{ JSON.stringify(paymentNotifyResponse, null, 2) }}</pre>
      </div>

      <div v-if="paymentReturnResponse" class="space-y-2">
        <label class="block text-sm text-gray-400">返回页响应</label>
        <div v-if="paymentReturnSummary" class="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-5 gap-3">
          <div class="rounded-xl border border-white/10 bg-black/20 p-3">
            <div class="text-[11px] text-gray-400">平台</div>
            <div class="mt-1 text-sm text-white">{{ paymentReturnSummary.providerLabel || paymentProviderType }}</div>
          </div>
          <div class="rounded-xl border border-white/10 bg-black/20 p-3">
            <div class="text-[11px] text-gray-400">订单号来源</div>
            <div class="mt-1 text-sm text-white break-all">{{ paymentReturnSummary.resolvedOrderNoSource || '-' }}</div>
          </div>
          <div class="rounded-xl border border-white/10 bg-black/20 p-3">
            <div class="text-[11px] text-gray-400">订单状态</div>
            <div class="mt-1 text-sm text-white">{{ paymentReturnSummary.status || '-' }}</div>
          </div>
          <div class="rounded-xl border border-white/10 bg-black/20 p-3">
            <div class="text-[11px] text-gray-400">终态</div>
            <div class="mt-1 text-sm text-white">{{ paymentReturnSummary.terminal ? '是' : '否' }}</div>
          </div>
          <div class="rounded-xl border border-white/10 bg-black/20 p-3">
            <div class="text-[11px] text-gray-400">建议轮询</div>
            <div class="mt-1 text-sm text-white">{{ paymentReturnSummary.suggestedPollIntervalSeconds ?? '-' }} 秒</div>
          </div>
        </div>
        <div v-if="paymentReturnSummary?.recommendedActions?.length" class="rounded-xl border border-white/10 bg-black/20 p-4 space-y-2">
          <div class="text-sm text-gray-300">返回页建议动作</div>
          <ul class="space-y-1 text-xs text-gray-300">
            <li v-for="(action, index) in paymentReturnSummary.recommendedActions" :key="`return-action-${index}`" class="flex gap-2">
              <span class="text-sky-400">•</span>
              <span>{{ action }}</span>
            </li>
          </ul>
        </div>
        <pre class="bg-gray-950/80 p-4 rounded-lg overflow-auto text-sm">{{ JSON.stringify(paymentReturnResponse, null, 2) }}</pre>
      </div>

      <div v-if="paymentDebugLinks.length" class="rounded-xl border border-white/10 bg-black/20 p-4 space-y-3">
        <div class="text-sm text-gray-300">快捷跳转</div>
        <div class="flex flex-wrap gap-3">
          <router-link
            v-for="item in paymentDebugLinks"
            :key="item.label"
            :to="item.to"
            class="px-4 py-2 rounded-lg border border-white/10 bg-gray-900/70 hover:bg-gray-800 text-sm text-white transition-colors"
          >
            {{ item.label }}
          </router-link>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from 'vue'
import type { RouteLocationRaw } from 'vue-router'
import {
  api,
  paymentApi,
  superAdminApi,
  type PaymentNotifyExecutionResult,
  type PaymentNotifyPreviewResult,
  type PaymentReturnResult
} from '@/api'
import { useAuthStore } from '@/stores/auth'

const PAYMENT_DEBUG_STORAGE_KEY = 'super_admin_payment_debug_presets'

const selectedApi = ref('')
const authStore = useAuthStore()
const testing = ref(false)
const apiResponse = ref<any>(null)
const pathInput = ref('')
const faceIdInput = ref('')
const topInput = ref('')
const thresholdInput = ref('')
const albumIdInput = ref('')
const taskStatus = ref<any | null>(null)
const paymentProviderType = ref('ALIPAY')
const paymentNotifyPayloadJson = ref('{\n  "orderNo": "VIP202603250001",\n  "status": "PAID",\n  "mock": true\n}')
const paymentNotifyHeadersJson = ref('{\n  "X-Signature": "demo-signature"\n}')
const paymentNotifyRawBody = ref('')
const paymentReturnQueryJson = ref('{\n  "orderNo": "VIP202603250001"\n}')
const paymentNotifyPreviewing = ref(false)
const paymentNotifyTesting = ref(false)
const paymentReturnTesting = ref(false)
const copyMessage = ref('')
const copyMessageType = ref<'success' | 'error'>('success')
const paymentNotifyPreviewResponse = ref<PaymentNotifyPreviewResult | Record<string, any> | null>(null)
const paymentNotifyResponse = ref<PaymentNotifyExecutionResult | Record<string, any> | null>(null)
const paymentReturnResponse = ref<PaymentReturnResult | Record<string, any> | null>(null)
let taskPollTimer: number | null = null

const PAYMENT_PROVIDER_TEMPLATES: Record<string, { payload: Record<string, any>; headers: Record<string, any>; rawBody?: string; returnQuery: Record<string, any> }> = {
  ALIPAY: {
    payload: { out_trade_no: 'VIP202603250001', trade_status: 'TRADE_SUCCESS', trade_no: 'alipay-demo-001', mock: true },
    headers: { 'X-Signature': 'demo-signature' },
    returnQuery: { out_trade_no: 'VIP202603250001' }
  },
  WECHAT_PAY: {
    payload: { trade_state: 'SUCCESS', resource: { out_trade_no: 'VIP202603250001', transaction_id: 'wx-demo-001' }, certificateVerified: true },
    headers: { 'Wechatpay-Serial': 'serial-001' },
    rawBody: '{\n  "id": "evt-demo-001",\n  "event_type": "TRANSACTION.SUCCESS",\n  "resource": {\n    "ciphertext": "demo-ciphertext",\n    "nonce": "demo-nonce",\n    "associated_data": "transaction"\n  }\n}',
    returnQuery: { out_trade_no: 'VIP202603250001' }
  },
  STRIPE: {
    payload: { type: 'checkout.session.completed', mock: true, data: { object: { client_reference_id: 'VIP202603250001', payment_intent: 'pi_demo_001' } } },
    headers: { 'Stripe-Signature': 'demo-signature' },
    rawBody: '{\n  "id": "evt_demo_001",\n  "type": "checkout.session.completed",\n  "data": {\n    "object": {\n      "client_reference_id": "VIP202603250001",\n      "payment_intent": "pi_demo_001"\n    }\n  }\n}',
    returnQuery: { client_reference_id: 'VIP202603250001' }
  },
  PAYPAL: {
    payload: { event_type: 'PAYMENT.CAPTURE.COMPLETED', resource: { invoice_id: 'VIP202603250001', id: 'paypal-demo-001' }, mock: true },
    headers: { 'Paypal-Transmission-Sig': 'demo-signature' },
    returnQuery: { invoice_id: 'VIP202603250001' }
  },
  UNIONPAY: {
    payload: { orderId: 'VIP202603250001', respCode: '00', queryId: 'union-demo-001', mock: true },
    headers: { 'X-Signature': 'demo-signature' },
    returnQuery: { orderId: 'VIP202603250001' }
  },
  PADDLE: {
    payload: { event_type: 'transaction.paid', data: { id: 'paddle-demo-001', custom_data: { orderNo: 'VIP202603250001' } }, mock: true },
    headers: { 'Paddle-Signature': 'demo-signature' },
    returnQuery: { orderNo: 'VIP202603250001' }
  },
  LEMON_SQUEEZY: {
    payload: { meta: { custom_data: { orderNo: 'VIP202603250001' } }, data: { id: 'lemon-demo-001', attributes: { status: 'paid' } } },
    headers: { 'X-Signature': 'demo-signature' },
    returnQuery: { orderNo: 'VIP202603250001' }
  },
  ADYEN: {
    payload: { notificationItems: [{ NotificationRequestItem: { eventCode: 'AUTHORISATION', success: 'true', merchantReference: 'VIP202603250001', pspReference: 'adyen-demo-001' } }] },
    headers: { 'X-Signature': 'demo-signature' },
    returnQuery: { merchantReference: 'VIP202603250001' }
  },
  MOLLIE: {
    payload: { id: 'mollie-demo-001', status: 'paid', metadata: { orderNo: 'VIP202603250001' }, mock: true },
    headers: { 'X-Signature': 'demo-signature' },
    returnQuery: { orderNo: 'VIP202603250001' }
  },
  XENDIT: {
    payload: { event: 'payment.succeeded', reference_id: 'VIP202603250001', id: 'xendit-demo-001', mock: true },
    headers: { 'X-Signature': 'demo-signature' },
    returnQuery: { reference_id: 'VIP202603250001' }
  },
  MIDTRANS: {
    payload: { transaction_status: 'settlement', order_id: 'VIP202603250001', transaction_id: 'midtrans-demo-001', mock: true },
    headers: { 'X-Signature': 'demo-signature' },
    returnQuery: { order_id: 'VIP202603250001' }
  },
  CUSTOM_WEBHOOK: {
    payload: { orderNo: 'VIP202603250001', status: 'PAID', tradeNo: 'custom-demo-001', mock: true },
    headers: { 'X-Signature': 'demo-signature' },
    rawBody: '{\n  "orderNo": "VIP202603250001",\n  "status": "PAID",\n  "tradeNo": "custom-demo-001"\n}',
    returnQuery: { orderNo: 'VIP202603250001' }
  }
}

const showAlbumIdInput = computed(() =>
  selectedApi.value.includes('/admin/background-removal')
)
const showPathInput = computed(() => selectedApi.value.includes('/admin/scan'))
const showFaceSimilarInputs = computed(() => selectedApi.value.includes('/admin/faces/{id}/similar'))
const paymentPreviewSummary = computed<PaymentNotifyPreviewResult | null>(() => {
  const value = paymentNotifyPreviewResponse.value
  if (!value || 'error' in value) return null
  return value as PaymentNotifyPreviewResult
})
const paymentReturnSummary = computed<PaymentReturnResult | null>(() => {
  const value = paymentReturnResponse.value
  if (!value || 'error' in value) return null
  return value as PaymentReturnResult
})
const currentDebugOrderNo = computed(() =>
  paymentReturnResponse.value?.orderNo ||
  paymentNotifyResponse.value?.orderNo ||
  paymentNotifyPreviewResponse.value?.orderNo ||
  ''
)

const buildPaymentResultRoute = (orderNo?: string): RouteLocationRaw => ({
  name: 'PaymentResult',
  query: {
    orderNo: orderNo || undefined,
    providerType: paymentProviderType.value
  }
})

const buildSuperAdminOrderRoute = (orderNo?: string): RouteLocationRaw => ({
  name: 'AdminSuperAdmin',
  query: {
    tab: 'vipOrders',
    focusOrderNo: orderNo || undefined
  }
})

const paymentDebugLinks = computed<Array<{ label: string; to: RouteLocationRaw }>>(() => {
  if (!currentDebugOrderNo.value) return []
  return [
    {
      label: '打开支付结果页',
      to: buildPaymentResultRoute(currentDebugOrderNo.value)
    },
    {
      label: '定位超管订单',
      to: buildSuperAdminOrderRoute(currentDebugOrderNo.value)
    }
  ]
})

const stopTaskPoll = async () => {
  if (taskPollTimer) {
    clearInterval(taskPollTimer)
    taskPollTimer = null
  }
  if (taskStatus.value?.taskId) {
    try {
      await api.post(`/admin/tasks/${taskStatus.value.taskId}/stop`)
    } catch {
      // ignore
    }
  }
  taskStatus.value = null
}

const pollTask = async (taskId: string) => {
  await stopTaskPoll()
  taskStatus.value = { taskId, status: 'pending', current: 0, total: 0, logs: [] }
  taskPollTimer = window.setInterval(async () => {
    try {
      const res = await api.get(`/admin/tasks/${taskId}`)
      const data = res.data
      if (data && data.found) {
        taskStatus.value = {
          taskId: data.taskId,
          status: data.status,
          current: data.current,
          total: data.total,
          complete: data.complete,
          logs: data.logs || []
        }
        if (data.complete) {
          await stopTaskPoll()
        }
      } else {
        await stopTaskPoll()
      }
    } catch {
      // ignore transient errors
    }
  }, 2000)
}

const requireConfirm = (message: string) => confirm(message)

const parseJsonObject = (raw: string, label: string) => {
  const text = raw.trim()
  if (!text) return {}
  let parsed: any
  try {
    parsed = JSON.parse(text)
  } catch (error) {
    throw new Error(`${label} 不是合法 JSON`)
  }
  if (!parsed || Array.isArray(parsed) || typeof parsed !== 'object') {
    throw new Error(`${label} 必须是 JSON 对象`)
  }
  return parsed
}

const fillPaymentProviderTemplate = () => {
  const template = PAYMENT_PROVIDER_TEMPLATES[paymentProviderType.value] || PAYMENT_PROVIDER_TEMPLATES.ALIPAY
  paymentNotifyPayloadJson.value = JSON.stringify(template.payload, null, 2)
  paymentNotifyHeadersJson.value = JSON.stringify(template.headers, null, 2)
  paymentNotifyRawBody.value = template.rawBody || ''
  paymentReturnQueryJson.value = JSON.stringify(template.returnQuery, null, 2)
}

const loadPaymentDebugPresets = () => {
  try {
    const raw = localStorage.getItem(PAYMENT_DEBUG_STORAGE_KEY)
    if (!raw) return {}
    const parsed = JSON.parse(raw)
    return parsed && typeof parsed === 'object' ? parsed : {}
  } catch {
    return {}
  }
}

const savePaymentDebugPreset = () => {
  try {
    const presets = loadPaymentDebugPresets()
    presets[paymentProviderType.value] = {
      paymentNotifyPayloadJson: paymentNotifyPayloadJson.value,
      paymentNotifyHeadersJson: paymentNotifyHeadersJson.value,
      paymentNotifyRawBody: paymentNotifyRawBody.value,
      paymentReturnQueryJson: paymentReturnQueryJson.value
    }
    localStorage.setItem(PAYMENT_DEBUG_STORAGE_KEY, JSON.stringify(presets))
  } catch {
    // ignore localStorage failures
  }
}

const restorePaymentDebugPreset = (providerType: string) => {
  const presets = loadPaymentDebugPresets()
  const preset = presets[providerType]
  if (preset && typeof preset === 'object') {
    paymentNotifyPayloadJson.value = String(preset.paymentNotifyPayloadJson || '')
    paymentNotifyHeadersJson.value = String(preset.paymentNotifyHeadersJson || '')
    paymentNotifyRawBody.value = String(preset.paymentNotifyRawBody || '')
    paymentReturnQueryJson.value = String(preset.paymentReturnQueryJson || '')
    return true
  }
  return false
}

const shellQuote = (value: string) => `'${String(value).replace(/'/g, `'\"'\"'`)}'`

const normalizeStringHeaders = (headers: Record<string, any>) =>
  Object.fromEntries(Object.entries(headers).map(([key, value]) => [key, String(value)]))

const resolveNotifyMode = (headers: Record<string, string>, rawBody: string) => {
  const contentTypeHeader = Object.entries(headers).find(([key]) => key.toLowerCase() === 'content-type')?.[1] || ''
  const trimmedRawBody = rawBody.trim()
  const hasRawBody = trimmedRawBody.length > 0
  const prefersJson = contentTypeHeader.toLowerCase().includes('json')
  const prefersForm = contentTypeHeader.toLowerCase().includes('x-www-form-urlencoded')
  const effectiveContentType = contentTypeHeader || (hasRawBody ? (prefersForm ? 'application/x-www-form-urlencoded' : 'text/plain') : 'application/json')
  return {
    trimmedRawBody,
    hasRawBody,
    prefersJson,
    prefersForm,
    effectiveContentType
  }
}

const buildPaymentNotifyPreviewRequest = () => {
  const payload = parseJsonObject(paymentNotifyPayloadJson.value, '回调负载')
  const headers = normalizeStringHeaders(parseJsonObject(paymentNotifyHeadersJson.value, '回调 Header'))
  const { trimmedRawBody, hasRawBody, prefersJson, effectiveContentType } = resolveNotifyMode(headers, paymentNotifyRawBody.value)
  const nextHeaders = { ...headers }
  if (!Object.keys(nextHeaders).some(key => key.toLowerCase() === 'content-type')) {
    nextHeaders['Content-Type'] = hasRawBody ? effectiveContentType : 'application/json'
  }
  if (hasRawBody) {
    return {
      body: prefersJson ? { ...payload, rawBody: trimmedRawBody } : trimmedRawBody,
      headers: nextHeaders
    }
  }
  return {
    body: payload,
    headers: nextHeaders
  }
}

const buildPaymentNotifyRequest = () => {
  const payload = parseJsonObject(paymentNotifyPayloadJson.value, '回调负载')
  const headers = normalizeStringHeaders(parseJsonObject(paymentNotifyHeadersJson.value, '回调 Header'))
  const { trimmedRawBody, hasRawBody, prefersJson, effectiveContentType } = resolveNotifyMode(headers, paymentNotifyRawBody.value)
  const nextHeaders = { ...headers }
  if (!Object.keys(nextHeaders).some(key => key.toLowerCase() === 'content-type')) {
    nextHeaders['Content-Type'] = effectiveContentType
  }
  if (hasRawBody) {
    return {
      body: prefersJson ? { ...payload, rawBody: trimmedRawBody } : trimmedRawBody,
      headers: nextHeaders
    }
  }
  return {
    body: payload,
    headers: nextHeaders
  }
}

const previewCurlCommand = computed(() => {
  const { body, headers } = buildPaymentNotifyPreviewRequest()
  const authHeader = authStore.token ? { Authorization: `Bearer ${authStore.token}` } : { Authorization: 'Bearer <SUPER_ADMIN_TOKEN>' }
  const dataValue = typeof body === 'string' ? body : JSON.stringify(body)
  const headerArgs = Object.entries({
    ...authHeader,
    ...headers
  }).map(([key, value]) => `-H ${shellQuote(`${key}: ${value}`)}`).join(' ')
  return `curl -X POST ${headerArgs} --data ${shellQuote(dataValue)} http://127.0.0.1:6060/api/admin/super-admin/payments/notify-preview/${paymentProviderType.value}`
})

const notifyCurlCommand = computed(() => {
  const { body, headers } = buildPaymentNotifyRequest()
  const dataValue = typeof body === 'string' ? body : JSON.stringify(body)
  const headerArgs = Object.entries(headers).map(([key, value]) => `-H ${shellQuote(`${key}: ${value}`)}`).join(' ')
  return `curl -X POST ${headerArgs} --data ${shellQuote(dataValue)} http://127.0.0.1:6060/api/payments/notify/${paymentProviderType.value}`
})

const returnCurlCommand = computed(() => {
  const params = new URLSearchParams()
  const query = {
    ...parseJsonObject(paymentReturnQueryJson.value, '返回页查询参数'),
    format: 'json'
  }
  Object.entries(query).forEach(([key, value]) => {
    if (value == null) return
    params.append(key, String(value))
  })
  return `curl ${shellQuote(`http://127.0.0.1:6060/api/payments/return/${paymentProviderType.value}?${params.toString()}`)}`
})

const copyText = async (text: string, successMessage: string) => {
  await navigator.clipboard.writeText(text)
  copyMessageType.value = 'success'
  copyMessage.value = successMessage
  window.setTimeout(() => {
    if (copyMessage.value === successMessage) {
      copyMessage.value = ''
    }
  }, 2500)
}

const copyNotifyCurl = async () => {
  try {
    await copyText(notifyCurlCommand.value, '已复制回调 cURL')
  } catch (error: any) {
    copyMessageType.value = 'error'
    copyMessage.value = error?.message || '复制回调 cURL 失败'
  }
}

const copyPreviewCurl = async () => {
  try {
    await copyText(previewCurlCommand.value, '已复制预演 cURL')
  } catch (error: any) {
    copyMessageType.value = 'error'
    copyMessage.value = error?.message || '复制预演 cURL 失败'
  }
}

const copyReturnCurl = async () => {
  try {
    await copyText(returnCurlCommand.value, '已复制返回页 cURL')
  } catch (error: any) {
    copyMessageType.value = 'error'
    copyMessage.value = error?.message || '复制返回页 cURL 失败'
  }
}

const exportCurrentPreset = async () => {
  try {
    const payload = {
      providerType: paymentProviderType.value,
      paymentNotifyPayloadJson: paymentNotifyPayloadJson.value,
      paymentNotifyHeadersJson: paymentNotifyHeadersJson.value,
      paymentNotifyRawBody: paymentNotifyRawBody.value,
      paymentReturnQueryJson: paymentReturnQueryJson.value
    }
    await copyText(JSON.stringify(payload, null, 2), `已导出 ${paymentProviderType.value} 调试预设`)
  } catch (error: any) {
    copyMessageType.value = 'error'
    copyMessage.value = error?.message || '导出当前预设失败'
  }
}

const resetCurrentPreset = () => {
  try {
    const presets = loadPaymentDebugPresets()
    delete presets[paymentProviderType.value]
    localStorage.setItem(PAYMENT_DEBUG_STORAGE_KEY, JSON.stringify(presets))
    fillPaymentProviderTemplate()
    copyMessageType.value = 'success'
    copyMessage.value = `已重置 ${paymentProviderType.value} 调试预设`
    window.setTimeout(() => {
      if (copyMessage.value === `已重置 ${paymentProviderType.value} 调试预设`) {
        copyMessage.value = ''
      }
    }, 2500)
  } catch (error: any) {
    copyMessageType.value = 'error'
    copyMessage.value = error?.message || '重置当前预设失败'
  }
}

const testApi = async () => {
  if (!selectedApi.value) return

  if (selectedApi.value === 'POST /admin/cleanup/all') {
    const confirmed = requireConfirm(
      '⚠️ 危险操作警告 ⚠️\n\n' +
      '此操作将删除所有照片、相册、标签、人脸和人物，只保留账号数据。\n\n' +
      '此操作不可恢复！确定要继续吗？'
    )
    if (!confirmed || !requireConfirm('请再次确认：你真的要删除所有数据吗？')) {
      return
    }
  }

  if (selectedApi.value === 'POST /admin/thumbnails/clear' &&
      !requireConfirm('🖼️ 清空缩略图数据后需要重新扫描生成，确定要继续吗？')) return

  if (selectedApi.value === 'POST /admin/faces/clear' &&
      !requireConfirm('👤 清空人脸识别数据后需要重新扫描生成，确定要继续吗？')) return

  if (selectedApi.value === 'POST /admin/smart-tags/clear' &&
      !requireConfirm('🏷️ 清空智能标签数据后需要重新扫描生成，确定要继续吗？')) return

  if (selectedApi.value === 'POST /admin/cleanup/orphaned' &&
      !requireConfirm('🧹 将删除找不到源文件的照片、人脸、标签关联和空相册，确定要继续吗？')) return

  if (selectedApi.value === 'POST /admin/albums/update-times' &&
      !requireConfirm('📅 将重新计算所有相册的拍摄时间与相册名时间，确定要继续吗？')) return

  if (selectedApi.value === 'POST /admin/ai-analysis/clear-all' &&
      !requireConfirm('🗑️ 将清空所有照片AI分析结果，此操作不可恢复，确定要继续吗？')) return

  if (selectedApi.value === 'POST /admin/ai-analysis/update-all' &&
      !requireConfirm('🤖 将覆盖现有 AI 分析并可能耗时较长，确定要继续吗？')) return

  if (selectedApi.value === 'POST /admin/photos/update-times' &&
      !requireConfirm('📸 将重新从 EXIF、路径和文件时间回填拍摄时间，确定要继续吗？')) return

  if (selectedApi.value === 'POST /admin/update-exif-data' &&
      !requireConfirm('⚠️ 将回填所有照片的 EXIF 字段，可能耗时较长，确定要继续吗？')) return

  if (selectedApi.value === 'POST /admin/update-color-categories' &&
      !requireConfirm('🎨 将更新所有照片的颜色分类，确定要继续吗？')) return

  if (selectedApi.value === 'POST /admin/recalculate-photo-colors' &&
      !requireConfirm('🎨🖼️ 将重新计算所有照片的颜色，可能耗时较长，确定要继续吗？')) return

  if (selectedApi.value === 'POST /admin/cleanup/duplicate-faces' &&
      !requireConfirm('🧹 将清理同一照片内的重复人脸记录，确定要继续吗？')) return

  testing.value = true
  apiResponse.value = null

  try {
    let [method, path] = selectedApi.value.split(' ')
    const params: Record<string, any> = {}

    if (showFaceSimilarInputs.value) {
      if (!faceIdInput.value.trim()) {
        alert('请填写人脸ID')
        testing.value = false
        return
      }
      path = path.replace('{id}', faceIdInput.value.trim())
      if (topInput.value) params.top = topInput.value
      if (thresholdInput.value) params.threshold = thresholdInput.value
    }

    const config: Record<string, any> = {
      method: method.toLowerCase(),
      url: path
    }

    if (showAlbumIdInput.value) {
      const albumId = albumIdInput.value.trim()
      config.params = {
        ...(albumId ? { albumId } : {}),
        batchSize: 50,
        saveToPhoto: true
      }
    }

    if (showPathInput.value && pathInput.value.trim()) {
      config.params = { ...(config.params || {}), path: pathInput.value.trim() }
    }
    if (Object.keys(params).length) {
      config.params = { ...(config.params || {}), ...params }
    }

    const response = await api(config)
    apiResponse.value = response.data

    if (response.data?.taskId) {
      await pollTask(response.data.taskId)
    }
  } catch (error: any) {
    apiResponse.value = {
      error: true,
      message: error.message,
      response: error.response?.data
    }
  } finally {
    testing.value = false
  }
}

const testPaymentNotify = async () => {
  paymentNotifyTesting.value = true
  paymentNotifyResponse.value = null
  try {
    const { body, headers } = buildPaymentNotifyRequest()
    const { data } = await paymentApi.notify(
      paymentProviderType.value as PaymentReturnResult['providerType'],
      body,
      headers
    )
    paymentNotifyResponse.value = data
  } catch (error: any) {
    paymentNotifyResponse.value = {
      error: true,
      message: error?.response?.data?.error || error?.message || '支付回调测试失败',
      response: error?.response?.data
    }
  } finally {
    paymentNotifyTesting.value = false
  }
}

const previewPaymentNotify = async () => {
  paymentNotifyPreviewing.value = true
  paymentNotifyPreviewResponse.value = null
  try {
    const { body, headers } = buildPaymentNotifyPreviewRequest()
    const { data } = await superAdminApi.previewPaymentNotify(paymentProviderType.value, body, headers)
    paymentNotifyPreviewResponse.value = data
  } catch (error: any) {
    paymentNotifyPreviewResponse.value = {
      error: true,
      message: error?.response?.data?.error || error?.message || '支付回调预演失败',
      response: error?.response?.data
    }
  } finally {
    paymentNotifyPreviewing.value = false
  }
}

const testPaymentReturn = async () => {
  paymentReturnTesting.value = true
  paymentReturnResponse.value = null
  try {
    const params = parseJsonObject(paymentReturnQueryJson.value, '返回页查询参数')
    const { data } = await paymentApi.getReturnResult(
      paymentProviderType.value as PaymentReturnResult['providerType'],
      { ...params, format: 'json' }
    )
    paymentReturnResponse.value = data
  } catch (error: any) {
    paymentReturnResponse.value = {
      error: true,
      message: error?.response?.data?.error || error?.message || '支付返回页测试失败',
      response: error?.response?.data
    }
  } finally {
    paymentReturnTesting.value = false
  }
}

onUnmounted(() => {
  stopTaskPoll()
})

watch(paymentProviderType, () => {
  if (!restorePaymentDebugPreset(paymentProviderType.value)) {
    fillPaymentProviderTemplate()
  }
}, { immediate: true })

watch([paymentProviderType, paymentNotifyPayloadJson, paymentNotifyHeadersJson, paymentNotifyRawBody, paymentReturnQueryJson], () => {
  savePaymentDebugPreset()
})
</script>
