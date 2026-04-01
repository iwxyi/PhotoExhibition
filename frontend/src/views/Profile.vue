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

    <main class="max-w-4xl mx-auto px-4 sm:px-6 lg:px-8 py-10">
      <div class="glass-panel p-8">
        <div class="flex flex-col md:flex-row md:items-center md:justify-between gap-6">
          <div>
            <h1 class="text-3xl font-light text-gray-900 dark:text-white">个人资料</h1>
            <p class="mt-2 text-sm text-gray-500 dark:text-gray-400">当前支持头像、项目名、邮箱与手机号资料维护；手机号可立即发送验证码完成绑定，邮箱能力已进入账号体系。</p>
          </div>
          <router-link to="/admin" class="btn-primary">进入后台</router-link>
        </div>

        <div v-if="loading" class="py-12 text-center text-gray-500 dark:text-gray-400">正在加载账号信息...</div>

        <div v-else class="mt-8 space-y-6">
          <div class="rounded-2xl border border-gray-200 dark:border-gray-700 bg-white/70 dark:bg-gray-800/70 p-6">
            <div class="flex flex-col md:flex-row md:items-center gap-6">
              <div class="flex items-center gap-4">
                <div class="h-20 w-20 rounded-full overflow-hidden bg-blue-500/15 flex items-center justify-center text-2xl font-semibold text-blue-600 dark:text-blue-300">
                  <img v-if="authStore.avatarPath" :src="authStore.avatarPath" alt="avatar" class="h-full w-full object-cover" />
                  <span v-else>{{ initials }}</span>
                </div>
                <div>
                  <div class="text-lg text-gray-900 dark:text-white">{{ authStore.displayName }}</div>
                  <div class="text-sm text-gray-500 dark:text-gray-400">{{ authStore.projectDisplayName || '未设置项目名' }}</div>
                </div>
              </div>
              <div class="flex items-center gap-3">
                <label class="btn-primary cursor-pointer">
                  上传头像
                  <input type="file" accept="image/png,image/jpeg,image/webp" class="hidden" @change="handleAvatarChange" />
                </label>
                <span class="text-xs text-gray-500 dark:text-gray-400">支持 jpg/png/webp，5MB 以内</span>
              </div>
            </div>
          </div>

          <div class="grid grid-cols-1 md:grid-cols-2 gap-6">
          <div class="rounded-2xl border border-gray-200 dark:border-gray-700 bg-white/70 dark:bg-gray-800/70 p-5">
            <div class="text-xs uppercase tracking-[0.2em] text-gray-400 mb-3">基础信息</div>
            <div class="space-y-3 text-sm">
              <div><span class="text-gray-500 dark:text-gray-400">用户名：</span><span class="text-gray-900 dark:text-white">{{ authStore.username || '-' }}</span></div>
              <div><span class="text-gray-500 dark:text-gray-400">昵称：</span><span class="text-gray-900 dark:text-white">{{ authStore.nickname || '-' }}</span></div>
              <div><span class="text-gray-500 dark:text-gray-400">手机号：</span><span class="text-gray-900 dark:text-white">{{ authStore.phone || '-' }}</span></div>
              <div><span class="text-gray-500 dark:text-gray-400">手机验证：</span><span class="text-gray-900 dark:text-white">{{ authStore.phoneVerified ? '已验证' : '未验证' }}</span></div>
              <div><span class="text-gray-500 dark:text-gray-400">邮箱：</span><span class="text-gray-900 dark:text-white">{{ authStore.email || '-' }}</span></div>
              <div><span class="text-gray-500 dark:text-gray-400">邮箱验证：</span><span class="text-gray-900 dark:text-white">{{ authStore.emailVerified ? '已验证' : '未验证' }}</span></div>
              <div><span class="text-gray-500 dark:text-gray-400">角色：</span><span class="text-gray-900 dark:text-white">{{ roleLabel }}</span></div>
            </div>
          </div>

          <div class="rounded-2xl border border-gray-200 dark:border-gray-700 bg-white/70 dark:bg-gray-800/70 p-5">
            <div class="text-xs uppercase tracking-[0.2em] text-gray-400 mb-3">多用户信息</div>
            <div class="space-y-3 text-sm">
              <div><span class="text-gray-500 dark:text-gray-400">用户 ID：</span><span class="text-gray-900 dark:text-white">{{ authStore.userId || '-' }}</span></div>
              <div><span class="text-gray-500 dark:text-gray-400">Slug：</span><span class="text-gray-900 dark:text-white">{{ authStore.slug || '-' }}</span></div>
              <div><span class="text-gray-500 dark:text-gray-400">公开地址：</span><span class="text-gray-900 dark:text-white break-all">{{ publicUrl }}</span></div>
              <div><span class="text-gray-500 dark:text-gray-400">项目名（中文）：</span><span class="text-gray-900 dark:text-white">{{ authStore.projectNameZh || '-' }}</span></div>
              <div><span class="text-gray-500 dark:text-gray-400">项目名（英文）：</span><span class="text-gray-900 dark:text-white">{{ authStore.projectNameEn || '-' }}</span></div>
              <div><span class="text-gray-500 dark:text-gray-400">多用户模式：</span><span class="text-gray-900 dark:text-white">{{ authStore.multiUserEnabled ? '已开启' : '未开启' }}</span></div>
            </div>
          </div>
          </div>

        <div v-if="!loading" class="rounded-2xl border border-gray-200 dark:border-gray-700 bg-white/70 dark:bg-gray-800/70 p-5 mt-6">
          <div class="flex items-center justify-between gap-4 flex-wrap">
            <div>
              <div class="text-xs uppercase tracking-[0.2em] text-gray-400 mb-2">资料编辑</div>
              <p class="text-sm text-gray-500 dark:text-gray-400">可修改昵称、邮箱、手机号和项目中英文名称；项目名会联动浏览器标题与右上角账号菜单。</p>
            </div>
            <button
              class="btn-primary disabled:opacity-60"
              :disabled="saving"
              @click="saveProfile"
            >
              {{ saving ? '保存中...' : '保存资料' }}
            </button>
          </div>

          <div class="grid grid-cols-1 md:grid-cols-2 gap-4 mt-6">
            <label class="space-y-2">
              <span class="text-sm text-gray-500 dark:text-gray-400">公开 Slug</span>
              <input v-model="form.slug" type="text" placeholder="例如 zhangsan" class="w-full px-4 py-3 rounded-xl border border-gray-200 dark:border-gray-700 bg-white/80 dark:bg-gray-900/60 text-gray-900 dark:text-white" />
              <div class="text-xs text-gray-500 dark:text-gray-400">仅支持小写字母、数字、下划线和连字符</div>
            </label>
            <label class="space-y-2">
              <span class="text-sm text-gray-500 dark:text-gray-400">昵称</span>
              <input v-model="form.nickname" type="text" class="w-full px-4 py-3 rounded-xl border border-gray-200 dark:border-gray-700 bg-white/80 dark:bg-gray-900/60 text-gray-900 dark:text-white" />
            </label>
            <label class="space-y-2">
              <span class="text-sm text-gray-500 dark:text-gray-400">手机号</span>
              <input v-model="form.phone" type="text" placeholder="例如 13800138000" class="w-full px-4 py-3 rounded-xl border border-gray-200 dark:border-gray-700 bg-white/80 dark:bg-gray-900/60 text-gray-900 dark:text-white" />
            </label>
            <label class="space-y-2">
              <span class="text-sm text-gray-500 dark:text-gray-400">邮箱</span>
              <input v-model="form.email" type="email" placeholder="例如 user@example.com" class="w-full px-4 py-3 rounded-xl border border-gray-200 dark:border-gray-700 bg-white/80 dark:bg-gray-900/60 text-gray-900 dark:text-white" />
            </label>
            <label class="space-y-2">
              <span class="text-sm text-gray-500 dark:text-gray-400">项目名（中文）</span>
              <input v-model="form.projectNameZh" type="text" class="w-full px-4 py-3 rounded-xl border border-gray-200 dark:border-gray-700 bg-white/80 dark:bg-gray-900/60 text-gray-900 dark:text-white" />
            </label>
            <label class="space-y-2">
              <span class="text-sm text-gray-500 dark:text-gray-400">项目名（英文）</span>
              <input v-model="form.projectNameEn" type="text" class="w-full px-4 py-3 rounded-xl border border-gray-200 dark:border-gray-700 bg-white/80 dark:bg-gray-900/60 text-gray-900 dark:text-white" />
            </label>
          </div>

          <div v-if="message" class="mt-4 text-sm" :class="messageType === 'success' ? 'text-emerald-500' : 'text-rose-500'">
            {{ message }}
          </div>
        </div>

        <div v-if="!loading && authStore.phone" class="rounded-2xl border border-gray-200 dark:border-gray-700 bg-white/70 dark:bg-gray-800/70 p-5 mt-6">
          <div class="flex items-center justify-between gap-4 flex-wrap">
            <div>
              <div class="text-xs uppercase tracking-[0.2em] text-gray-400 mb-2">手机号验证</div>
              <p class="text-sm text-gray-500 dark:text-gray-400">
                当前手机号：{{ authStore.phone }} · {{ authStore.phoneVerified ? '已验证' : '未验证' }}
              </p>
            </div>
            <button
              class="btn-primary disabled:opacity-60"
              :disabled="sendingPhoneCode || !authStore.phone"
              @click="sendPhoneCode"
            >
              {{ sendingPhoneCode ? '发送中...' : '发送验证码' }}
            </button>
          </div>

          <div class="grid grid-cols-1 md:grid-cols-[1fr_auto] gap-3 mt-5">
            <label class="block space-y-2">
              <span class="text-sm text-gray-500 dark:text-gray-400">短信验证码</span>
              <input
                v-model="phoneVerifyCode"
                type="text"
                placeholder="输入收到的验证码"
                class="w-full px-4 py-3 rounded-xl border border-gray-200 dark:border-gray-700 bg-white/80 dark:bg-gray-900/60 text-gray-900 dark:text-white"
              />
            </label>
            <button
              class="btn-primary disabled:opacity-60"
              :disabled="verifyingPhone || !phoneVerifyCode.trim()"
              @click="verifyPhoneCode"
            >
              {{ verifyingPhone ? '验证中...' : '确认绑定' }}
            </button>
          </div>

          <div v-if="phoneDebugCode" class="mt-3 text-xs text-amber-500">
            当前为模拟短信模式，验证码：{{ phoneDebugCode }}
          </div>
        </div>

        <div v-if="!loading && authStore.email" class="rounded-2xl border border-gray-200 dark:border-gray-700 bg-white/70 dark:bg-gray-800/70 p-5 mt-6">
          <div class="flex items-center justify-between gap-4 flex-wrap">
            <div>
              <div class="text-xs uppercase tracking-[0.2em] text-gray-400 mb-2">邮箱验证</div>
              <p class="text-sm text-gray-500 dark:text-gray-400">
                当前邮箱：{{ authStore.email }} · {{ authStore.emailVerified ? '已验证' : '未验证' }}
              </p>
            </div>
            <button
              class="btn-primary disabled:opacity-60"
              :disabled="sendingEmailCode || !authStore.email"
              @click="sendEmailCode"
            >
              {{ sendingEmailCode ? '发送中...' : '发送验证码' }}
            </button>
          </div>

          <div class="grid grid-cols-1 md:grid-cols-[1fr_auto] gap-3 mt-5">
            <label class="block space-y-2">
              <span class="text-sm text-gray-500 dark:text-gray-400">邮箱验证码</span>
              <input
                v-model="emailVerifyCode"
                type="text"
                placeholder="输入收到的验证码"
                class="w-full px-4 py-3 rounded-xl border border-gray-200 dark:border-gray-700 bg-white/80 dark:bg-gray-900/60 text-gray-900 dark:text-white"
              />
            </label>
            <button
              class="btn-primary disabled:opacity-60"
              :disabled="verifyingEmail || !emailVerifyCode.trim()"
              @click="verifyEmailCode"
            >
              {{ verifyingEmail ? '验证中...' : '确认邮箱' }}
            </button>
          </div>

          <div v-if="emailDebugCode" class="mt-3 text-xs text-amber-500">
            当前为模拟邮箱模式，验证码：{{ emailDebugCode }}
          </div>
        </div>

        <div id="vip-center" v-if="!loading" class="rounded-2xl border border-gray-200 dark:border-gray-700 bg-white/70 dark:bg-gray-800/70 p-5 mt-6 space-y-5">
          <div class="flex items-center justify-between gap-4 flex-wrap">
            <div>
              <div class="text-xs uppercase tracking-[0.2em] text-gray-400 mb-2">会员中心</div>
              <p class="text-sm text-gray-500 dark:text-gray-400">这里展示当前配额、已生效套餐、可购买方案和最近订单。支付仍处于预埋阶段，但下单与 Mock 联调链路已经可用。</p>
            </div>
            <div class="flex items-center gap-3 flex-wrap">
              <router-link to="/vip" class="px-4 py-2 rounded-lg border border-gray-200 dark:border-gray-700 text-sm text-gray-700 dark:text-gray-200 hover:bg-gray-50 dark:hover:bg-gray-800">
                打开独立会员页
              </router-link>
              <button
                class="btn-primary disabled:opacity-60"
                :disabled="vipLoading"
                @click="loadVipData"
              >
                {{ vipLoading ? '刷新中...' : '刷新会员信息' }}
              </button>
            </div>
          </div>

          <div v-if="vipOverview" class="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-4 gap-4">
            <div class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white/80 dark:bg-gray-900/50 p-4 space-y-1">
              <div class="text-xs text-gray-500 dark:text-gray-400">当前总配额</div>
              <div class="text-2xl font-light text-gray-900 dark:text-white">{{ formatBytesAsGb(vipOverview.storageQuotaBytes) }}</div>
            </div>
            <div class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white/80 dark:bg-gray-900/50 p-4 space-y-1">
              <div class="text-xs text-gray-500 dark:text-gray-400">已使用</div>
              <div class="text-2xl font-light text-gray-900 dark:text-white">{{ formatBytesAsGb(vipOverview.storageUsedBytes) }}</div>
            </div>
            <div class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white/80 dark:bg-gray-900/50 p-4 space-y-1">
              <div class="text-xs text-gray-500 dark:text-gray-400">当前套餐</div>
              <div class="text-lg text-gray-900 dark:text-white">{{ vipOverview.currentVipPlanName || '未开通' }}</div>
            </div>
            <div class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white/80 dark:bg-gray-900/50 p-4 space-y-1">
              <div class="text-xs text-gray-500 dark:text-gray-400">到期时间</div>
              <div class="text-lg text-gray-900 dark:text-white">{{ formatDate(vipOverview.vipExpireAt) }}</div>
            </div>
          </div>

          <div class="rounded-xl border border-dashed border-gray-200 dark:border-gray-700 p-4">
            <div class="flex items-center justify-between gap-3 flex-wrap">
              <div>
                <div class="text-sm text-gray-900 dark:text-white">支付通道</div>
                <div class="text-xs text-gray-500 dark:text-gray-400">
                  当前平台：{{ paymentProviderLabel(vipOverview?.paymentProviderType) }} · 支付开关 {{ vipOverview?.paymentEnabled ? '已开启' : '未开启' }} · Mock {{ vipOverview?.paymentMockEnabled ? '已开启' : '未开启' }}
                </div>
              </div>
              <div class="text-xs text-gray-500 dark:text-gray-400">
                基础配额 {{ formatBytesAsGb(vipOverview?.baseQuotaBytes) }} / 管理员附加 {{ formatBytesAsGb(vipOverview?.vipExtraQuotaBytes) }}
              </div>
            </div>
          </div>

          <div>
            <div class="text-sm font-medium text-gray-900 dark:text-white mb-3">可购买套餐</div>
            <div v-if="!vipPlans.length" class="text-sm text-gray-500 dark:text-gray-400">暂无可购买套餐。</div>
            <div v-else class="grid grid-cols-1 lg:grid-cols-2 gap-4">
              <article v-for="plan in vipPlans" :key="plan.id" class="rounded-2xl border border-gray-200 dark:border-gray-700 bg-white/80 dark:bg-gray-900/50 p-5 space-y-3">
                <div class="flex items-start justify-between gap-3">
                  <div>
                    <div class="text-lg text-gray-900 dark:text-white">{{ plan.name }}</div>
                    <div class="text-xs text-gray-500 dark:text-gray-400">{{ plan.code }} · {{ plan.durationDays }} 天</div>
                  </div>
                  <div class="text-right">
                    <div class="text-2xl font-light text-gray-900 dark:text-white">¥{{ plan.priceYuan }}</div>
                    <div class="text-xs text-emerald-600 dark:text-emerald-300">+{{ formatBytesAsGb(plan.extraQuotaBytes) }}</div>
                  </div>
                </div>
                <p class="text-sm text-gray-500 dark:text-gray-400 min-h-[40px]">{{ plan.description || '暂无套餐说明' }}</p>
                <div class="flex items-center justify-between gap-3">
                  <span class="text-xs text-gray-500 dark:text-gray-400">排序 {{ plan.sortOrder }}</span>
                  <button class="btn-primary disabled:opacity-60" :disabled="creatingPlanId === plan.id" @click="purchasePlan(plan.id)">
                    {{ creatingPlanId === plan.id ? '创建订单中...' : '立即购买' }}
                  </button>
                </div>
              </article>
            </div>
          </div>

          <div>
            <div class="flex items-center justify-between gap-3 flex-wrap mb-3">
              <div class="text-sm font-medium text-gray-900 dark:text-white">最近订单</div>
              <div class="text-xs text-gray-500 dark:text-gray-400">仅显示最近 20 条订单</div>
            </div>
            <div v-if="!vipOrders.length" class="text-sm text-gray-500 dark:text-gray-400">暂无订单。</div>
            <div v-else class="space-y-3">
              <article v-for="order in vipOrders" :key="order.id" class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white/80 dark:bg-gray-900/50 p-4 space-y-3">
                <div class="flex items-start justify-between gap-3 flex-wrap">
                  <div>
                    <div class="text-sm text-gray-900 dark:text-white">{{ order.orderNo }}</div>
                    <div class="text-xs text-gray-500 dark:text-gray-400">{{ order.vipPlanName || order.vipPlanCode || '未知套餐' }} · {{ order.orderStageLabel || order.status }} · ¥{{ Number(order.amountYuan || 0).toFixed(2) }}</div>
                  </div>
                  <div class="flex gap-2 flex-wrap">
                    <button class="px-3 py-2 rounded-lg border border-gray-200 dark:border-gray-700 text-sm text-gray-700 dark:text-gray-200 hover:bg-gray-50 dark:hover:bg-gray-800 disabled:opacity-50" :disabled="previewingOrderId === order.id" @click="previewCheckout(order.id)">
                      {{ previewingOrderId === order.id ? '生成中...' : '支付预览' }}
                    </button>
                    <button v-if="vipOverview?.paymentMockEnabled" class="px-3 py-2 rounded-lg bg-emerald-600 hover:bg-emerald-500 text-sm text-white disabled:opacity-50" :disabled="mockingOrderId === order.id || !order.canInitiatePayment" @click="mockPay(order.id)">
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
                <div class="flex justify-end gap-3 flex-wrap">
                  <router-link
                    :to="buildResultRoute(order)"
                    class="text-xs text-indigo-500 hover:text-indigo-400"
                  >
                    查看支付结果页
                  </router-link>
                  <router-link
                    :to="buildVipCenterFocusRoute(order.orderNo)"
                    class="text-xs text-gray-500 hover:text-gray-400"
                  >
                    在会员中心定位
                  </router-link>
                </div>
              </article>
            </div>
          </div>

          <div v-if="checkoutPreview" class="rounded-2xl border border-gray-200 dark:border-gray-700 bg-white/80 dark:bg-gray-900/50 p-5 space-y-4">
            <div class="flex items-center justify-between gap-3 flex-wrap">
              <div>
                <div class="text-lg text-gray-900 dark:text-white">支付预览</div>
                <div class="text-xs text-gray-500 dark:text-gray-400">{{ checkoutPreview.payment.providerLabel }} · {{ checkoutPreview.order.orderNo }}</div>
              </div>
              <button class="px-3 py-2 rounded-lg border border-gray-200 dark:border-gray-700 text-sm text-gray-700 dark:text-gray-200 hover:bg-gray-50 dark:hover:bg-gray-800" @click="checkoutPreview = null">
                关闭
              </button>
            </div>
            <div class="grid grid-cols-1 xl:grid-cols-2 gap-4">
              <div class="rounded-xl border border-gray-200 dark:border-gray-700 p-4 space-y-2 text-sm text-gray-600 dark:text-gray-300">
                <div>接口地址：{{ checkoutPreview.payment.apiBaseUrl }}</div>
                <div>状态说明：{{ checkoutPreview.payment.supportMessage }}</div>
                <div v-if="checkoutPreview.payment.missingFields.length">缺失字段：{{ checkoutPreview.payment.missingFields.join('、') }}</div>
                <div>下单签名就绪：{{ checkoutPreview.payment.signatureReady ? '是' : '否' }}</div>
                <div>回调验签就绪：{{ checkoutPreview.payment.callbackVerificationReady ? '是' : '否' }}</div>
                <div>退款就绪：{{ checkoutPreview.payment.refundReady ? '是' : '否' }}</div>
                <div v-if="checkoutPreview.payment.readinessWarnings?.length" class="text-amber-600 dark:text-amber-300">接入告警：{{ checkoutPreview.payment.readinessWarnings.join('；') }}</div>
              </div>
              <div class="rounded-xl border border-gray-200 dark:border-gray-700 p-4 space-y-2 text-sm text-gray-600 dark:text-gray-300">
                <div>订单金额：¥{{ Number(checkoutPreview.order.amountYuan || 0).toFixed(2) }}</div>
                <div>支付开关：{{ checkoutPreview.payment.enabled ? '已开启' : '未开启' }}</div>
                <div>Mock：{{ checkoutPreview.payment.mockEnabled ? '已开启' : '未开启' }}</div>
              </div>
            </div>
            <div class="grid grid-cols-1 xl:grid-cols-2 gap-4">
              <pre class="rounded-xl border border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-950/50 p-4 text-xs text-gray-700 dark:text-gray-200 overflow-auto">{{ JSON.stringify(checkoutPreview.payment.requestPayload, null, 2) }}</pre>
              <pre class="rounded-xl border border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-950/50 p-4 text-xs text-gray-700 dark:text-gray-200 overflow-auto">{{ JSON.stringify(checkoutPreview.payment.callbackPayload, null, 2) }}</pre>
            </div>
            <div v-if="checkoutPreview.payment.stageReadiness?.length" class="rounded-xl border border-gray-200 dark:border-gray-700 p-4 space-y-3 text-xs text-gray-600 dark:text-gray-300">
              <div class="text-sm text-gray-900 dark:text-white">阶段检查</div>
              <div
                v-for="stage in checkoutPreview.payment.stageReadiness"
                :key="stage.stageKey"
                class="rounded-lg border border-gray-200 dark:border-gray-700 p-3 space-y-2"
              >
                <div class="flex items-center justify-between gap-3">
                  <div class="text-sm text-gray-900 dark:text-white">{{ stage.stageLabel }}</div>
                  <span :class="stage.ready ? 'text-emerald-600 dark:text-emerald-300' : 'text-amber-600 dark:text-amber-300'">
                    {{ stage.ready ? '已就绪' : '待补齐' }}
                  </span>
                </div>
                <div v-for="check in stage.checks || []" :key="`${stage.stageKey}-${check.label}`" class="flex items-start justify-between gap-4">
                  <div>{{ check.label }}</div>
                  <div :class="check.passed ? 'text-emerald-600 dark:text-emerald-300' : 'text-amber-600 dark:text-amber-300'">
                    {{ check.passed ? '通过' : (check.failureReason || '未通过') }}
                  </div>
                </div>
              </div>
            </div>
            <div v-if="checkoutPreview.payment.recommendedConfigFields?.length || checkoutPreview.payment.nextActionHints?.length" class="grid grid-cols-1 xl:grid-cols-2 gap-4">
              <div v-if="checkoutPreview.payment.recommendedConfigFields?.length" class="rounded-xl border border-gray-200 dark:border-gray-700 p-4 space-y-2 text-xs text-gray-600 dark:text-gray-300">
                <div class="text-sm text-gray-900 dark:text-white">建议重点配置</div>
                <div v-for="field in checkoutPreview.payment.recommendedConfigFields" :key="field">
                  {{ field }}
                </div>
              </div>
              <div v-if="checkoutPreview.payment.nextActionHints?.length" class="rounded-xl border border-gray-200 dark:border-gray-700 p-4 space-y-2 text-xs text-gray-600 dark:text-gray-300">
                <div class="text-sm text-gray-900 dark:text-white">建议下一步</div>
                <div v-for="(hint, index) in checkoutPreview.payment.nextActionHints" :key="`${checkoutPreview.payment.providerType}-hint-${index}`">
                  {{ index + 1 }}. {{ hint }}
                </div>
              </div>
            </div>
            <div class="flex justify-end">
              <router-link
                :to="buildResultRoute(checkoutPreview.order)"
                class="px-4 py-2 rounded-lg border border-gray-200 dark:border-gray-700 text-sm text-gray-700 dark:text-gray-200 hover:bg-gray-50 dark:hover:bg-gray-800"
              >
                打开支付结果页
              </router-link>
            </div>
          </div>
        </div>
        </div>
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import type { RouteLocationRaw } from 'vue-router'
import AppHeader from '@/components/AppHeader.vue'
import PublicAccountMenu from '@/components/PublicAccountMenu.vue'
import { authProfileApi, type UserVipCheckoutPreview, type UserVipOverview, type UserVipPlan, type VipOrderSummary } from '@/api'
import { useAuthStore } from '@/stores/auth'
import { paymentProviderLabel } from '@/utils/providerLabels'

const authStore = useAuthStore()
const loading = ref(false)
const saving = ref(false)
const sendingPhoneCode = ref(false)
const verifyingPhone = ref(false)
const sendingEmailCode = ref(false)
const verifyingEmail = ref(false)
const message = ref('')
const messageType = ref<'success' | 'error'>('success')
const phoneVerifyCode = ref('')
const phoneDebugCode = ref('')
const emailVerifyCode = ref('')
const emailDebugCode = ref('')
const vipLoading = ref(false)
const creatingPlanId = ref<number | null>(null)
const previewingOrderId = ref<number | null>(null)
const mockingOrderId = ref<number | null>(null)
const togglingAutoRenewId = ref<number | null>(null)
const vipOverview = ref<UserVipOverview | null>(null)
const vipPlans = ref<UserVipPlan[]>([])
const vipOrders = ref<VipOrderSummary[]>([])
const checkoutPreview = ref<UserVipCheckoutPreview | null>(null)
const form = reactive({
  slug: '',
  nickname: '',
  phone: '',
  email: '',
  projectNameZh: '',
  projectNameEn: ''
})

const roleLabel = computed(() => {
  if (authStore.role === 'SUPER_ADMIN') return '超级管理员'
  if (authStore.role === 'USER_ADMIN') return '用户后台管理员'
  return authStore.role || '-'
})
const initials = computed(() => (authStore.displayName || 'U').slice(0, 1).toUpperCase())
const publicUrl = computed(() => {
  if (!authStore.slug) return '-'
  return `${window.location.origin}/${authStore.slug}`
})

const formatDate = (value?: string | null) => {
  if (!value) return '-'
  const normalized = value.includes('T') ? value : value.replace(' ', 'T')
  const date = new Date(normalized)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString()
}

const formatBytesAsGb = (value?: number | null) => {
  const bytes = Number(value || 0)
  return `${Math.round((bytes / 1024 / 1024 / 1024) * 100) / 100} GB`
}

const buildResultRoute = (order: Pick<VipOrderSummary, 'orderNo' | 'paymentProviderType'>): RouteLocationRaw => ({
  name: 'PaymentResult',
  query: {
    orderNo: order.orderNo,
    providerType: order.paymentProviderType || vipOverview.value?.paymentProviderType || 'ALIPAY'
  }
})

const buildVipCenterFocusRoute = (orderNo?: string | null): RouteLocationRaw => ({
  name: 'VipCenter',
  query: orderNo ? { focusOrderNo: orderNo } : {}
})

const syncForm = () => {
  form.slug = authStore.slug || ''
  form.nickname = authStore.nickname || ''
  form.phone = authStore.phone || ''
  form.email = authStore.email || ''
  form.projectNameZh = authStore.projectNameZh || ''
  form.projectNameEn = authStore.projectNameEn || ''
}

const saveProfile = async () => {
  saving.value = true
  message.value = ''
  try {
    const { data } = await authProfileApi.updateProfile({
      slug: form.slug.trim() || null,
      nickname: form.nickname.trim() || null,
      phone: form.phone.trim() || null,
      email: form.email.trim() || null,
      projectNameZh: form.projectNameZh.trim() || null,
      projectNameEn: form.projectNameEn.trim() || null
    })
    await authStore.fetchCurrentUser()
    syncForm()
    messageType.value = 'success'
    message.value = '资料已保存'
  } catch (error: any) {
    messageType.value = 'error'
    message.value = error?.response?.data?.error || error?.message || '保存资料失败'
  } finally {
    saving.value = false
  }
}

const sendPhoneCode = async () => {
  sendingPhoneCode.value = true
  phoneDebugCode.value = ''
  message.value = ''
  try {
    const { data } = await authProfileApi.sendPhoneCode()
    phoneDebugCode.value = data.debugCode || ''
    messageType.value = 'success'
    message.value = data.message || '验证码已发送'
  } catch (error: any) {
    messageType.value = 'error'
    message.value = error?.response?.data?.error || error?.message || '验证码发送失败'
  } finally {
    sendingPhoneCode.value = false
  }
}

const verifyPhoneCode = async () => {
  verifyingPhone.value = true
  message.value = ''
  try {
    await authProfileApi.verifyPhoneCode(phoneVerifyCode.value.trim())
    await authStore.fetchCurrentUser()
    syncForm()
    phoneVerifyCode.value = ''
    phoneDebugCode.value = ''
    messageType.value = 'success'
    message.value = '手机号已验证'
  } catch (error: any) {
    messageType.value = 'error'
    message.value = error?.response?.data?.error || error?.message || '手机号验证失败'
  } finally {
    verifyingPhone.value = false
  }
}

const sendEmailCode = async () => {
  sendingEmailCode.value = true
  emailDebugCode.value = ''
  message.value = ''
  try {
    const { data } = await authProfileApi.sendBindEmailCode()
    emailDebugCode.value = data.debugCode || ''
    messageType.value = 'success'
    message.value = data.message || '邮箱验证码已发送'
  } catch (error: any) {
    messageType.value = 'error'
    message.value = error?.response?.data?.error || error?.message || '邮箱验证码发送失败'
  } finally {
    sendingEmailCode.value = false
  }
}

const verifyEmailCode = async () => {
  verifyingEmail.value = true
  message.value = ''
  try {
    await authProfileApi.verifyEmailCode(emailVerifyCode.value.trim())
    await authStore.fetchCurrentUser()
    syncForm()
    emailVerifyCode.value = ''
    emailDebugCode.value = ''
    messageType.value = 'success'
    message.value = '邮箱已验证'
  } catch (error: any) {
    messageType.value = 'error'
    message.value = error?.response?.data?.error || error?.message || '邮箱验证失败'
  } finally {
    verifyingEmail.value = false
  }
}

const loadVipData = async () => {
  vipLoading.value = true
  try {
    const [overviewRes, plansRes, ordersRes] = await Promise.all([
      authProfileApi.getVipOverview(),
      authProfileApi.getVipPlans(),
      authProfileApi.getVipOrders()
    ])
    vipOverview.value = overviewRes.data
    vipPlans.value = plansRes.data?.plans || []
    vipOrders.value = ordersRes.data?.orders || []
  } catch (error: any) {
    messageType.value = 'error'
    message.value = error?.response?.data?.error || error?.message || '加载会员信息失败'
  } finally {
    vipLoading.value = false
  }
}

const purchasePlan = async (planId: number) => {
  creatingPlanId.value = planId
  message.value = ''
  try {
    const { data } = await authProfileApi.createVipOrder(planId)
    await loadVipData()
    messageType.value = 'success'
    message.value = `已创建订单 ${data.orderNo}`
  } catch (error: any) {
    messageType.value = 'error'
    message.value = error?.response?.data?.error || error?.message || '创建订单失败'
  } finally {
    creatingPlanId.value = null
  }
}

const previewCheckout = async (orderId: number) => {
  previewingOrderId.value = orderId
  message.value = ''
  try {
    const { data } = await authProfileApi.getVipCheckout(orderId)
    checkoutPreview.value = data
    messageType.value = 'success'
    message.value = `已生成订单 ${data.order.orderNo} 的支付预览`
  } catch (error: any) {
    messageType.value = 'error'
    message.value = error?.response?.data?.error || error?.message || '生成支付预览失败'
  } finally {
    previewingOrderId.value = null
  }
}

const mockPay = async (orderId: number) => {
  mockingOrderId.value = orderId
  message.value = ''
  try {
    await authProfileApi.mockPayVipOrder(orderId)
    await Promise.all([authStore.fetchCurrentUser(), loadVipData()])
    if (checkoutPreview.value?.order?.id === orderId) {
      checkoutPreview.value = await authProfileApi.getVipCheckout(orderId).then(res => res.data)
    }
    messageType.value = 'success'
    message.value = 'Mock 支付成功，套餐已尝试生效'
  } catch (error: any) {
    messageType.value = 'error'
    message.value = error?.response?.data?.error || error?.message || 'Mock 支付失败'
  } finally {
    mockingOrderId.value = null
  }
}

const toggleAutoRenew = async (order: VipOrderSummary) => {
  togglingAutoRenewId.value = order.id
  message.value = ''
  try {
    await authProfileApi.updateVipOrderAutoRenew(order.id, !order.autoRenewEnabled)
    await loadVipData()
    messageType.value = 'success'
    message.value = `订单 ${order.orderNo} 已${order.autoRenewEnabled ? '关闭' : '开启'}自动续费`
  } catch (error: any) {
    messageType.value = 'error'
    message.value = error?.response?.data?.error || error?.message || '更新自动续费失败'
  } finally {
    togglingAutoRenewId.value = null
  }
}

const handleAvatarChange = async (event: Event) => {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) {
    return
  }
  saving.value = true
  message.value = ''
  try {
    await authProfileApi.uploadAvatar(file)
    await authStore.fetchCurrentUser()
    messageType.value = 'success'
    message.value = '头像已更新'
  } catch (error: any) {
    messageType.value = 'error'
    message.value = error?.response?.data?.error || error?.message || '上传头像失败'
  } finally {
    saving.value = false
    input.value = ''
  }
}

onMounted(async () => {
  loading.value = true
  try {
    await authStore.fetchCurrentUser()
    syncForm()
    await loadVipData()
  } finally {
    loading.value = false
  }
})
</script>
