<template>
  <div class="min-h-screen admin-shell text-white">
    <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8 space-y-8">
      <div class="flex items-center justify-between flex-wrap gap-4">
        <div>
          <h1 class="text-2xl font-light tracking-wide mb-1">超级管理员</h1>
          <p class="text-sm text-gray-300">集中管理多用户开关、空间配额、默认存储与用户后台权限。</p>
        </div>
        <router-link
          to="/admin"
          class="px-4 py-2 bg-gray-900/70 hover:bg-gray-700 rounded-lg border border-white/10 transition-colors text-sm"
        >
          返回后台管理
        </router-link>
      </div>

      <AdminSectionTabs />

      <div class="flex flex-wrap gap-3">
        <button
          v-for="tab in superAdminTabs"
          :key="tab.key"
          type="button"
          class="px-4 py-2 rounded-xl text-sm border transition-colors"
          :class="activeTab === tab.key
            ? 'bg-purple-600 text-white border-purple-500/40'
            : 'bg-gray-900/60 hover:bg-gray-800 text-gray-200 border-white/10'"
          @click="changeActiveTab(tab.key)"
        >
          {{ tab.label }}
        </button>
      </div>

      <div
        v-if="statusMessage"
        class="rounded-xl border px-4 py-3 text-sm"
        :class="statusType === 'success'
          ? 'bg-emerald-500/10 border-emerald-400/30 text-emerald-200'
          : 'bg-rose-500/10 border-rose-400/30 text-rose-200'"
      >
        {{ statusMessage }}
      </div>

      <section v-if="activeTab === 'overview'" class="space-y-4">
        <div class="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-4 gap-4">
          <div class="glass-panel p-5 space-y-2">
            <div class="text-sm text-gray-400">用户总数</div>
            <div class="text-3xl font-light">{{ overview.userCount }}</div>
            <div class="text-xs text-gray-400">
              启用 {{ overview.activeUserCount }} / 禁用 {{ overview.disabledUserCount }} / 锁定 {{ overview.lockedUserCount }}
            </div>
          </div>
          <div class="glass-panel p-5 space-y-2">
            <div class="text-sm text-gray-400">默认用户配额</div>
            <div class="text-3xl font-light">{{ formatQuotaGb(overview.defaultUserQuotaBytes) }}</div>
            <div class="text-xs text-gray-400">
              VIP 默认增量 {{ formatQuotaGb(overview.defaultVipExtraQuotaBytes) }} · 已用 {{ formatBytes(overview.totalUsedBytes) }} / 总配额 {{ formatBytes(overview.totalQuotaBytes) }}
            </div>
          </div>
          <div class="glass-panel p-5 space-y-2">
            <div class="text-sm text-gray-400">存储提供者</div>
            <div class="text-3xl font-light">{{ overview.storageProviderCount }}</div>
            <div class="text-xs text-gray-400">可用 {{ overview.enabledStorageProviderCount }} 个</div>
          </div>
          <div class="glass-panel p-5 space-y-2">
            <div class="text-sm text-gray-400">系统开关</div>
            <div class="flex flex-wrap gap-2">
              <span class="chip" :class="overview.multiUserEnabled ? 'text-emerald-200' : 'text-gray-300'">
                多用户：{{ overview.multiUserEnabled ? '开启' : '关闭' }}
              </span>
              <span class="chip" :class="overview.scanSchedulerEnabled ? 'text-amber-200' : 'text-gray-300'">
                定时扫描：{{ overview.scanSchedulerEnabled ? '开启' : '关闭' }}
              </span>
              <span class="chip" :class="overview.forceBindPhone ? 'text-cyan-200' : 'text-gray-300'">
                强绑手机：{{ overview.forceBindPhone ? '开启' : '关闭' }}
              </span>
              <span class="chip" :class="overview.autoRenewSchedulerEnabled ? 'text-lime-200' : 'text-gray-300'">
                自动续费：{{ overview.autoRenewSchedulerEnabled ? '建单任务开启' : '关闭' }}
              </span>
              <span class="chip" :class="overview.smsEnabled ? 'text-fuchsia-200' : 'text-gray-300'">
                短信发送：{{ overview.smsEnabled ? `${smsProviderLabel(overview.smsProviderType)} 已开启` : '关闭' }}
              </span>
              <span class="chip" :class="overview.smsMockEnabled ? 'text-sky-200' : 'text-gray-300'">
                Mock 验证码：{{ overview.smsMockEnabled ? '开启' : '关闭' }}
              </span>
              <span class="chip" :class="overview.emailEnabled ? 'text-emerald-200' : 'text-gray-300'">
                邮件发送：{{ overview.emailEnabled ? `${emailProviderLabel(overview.emailProviderType)} 已开启` : '关闭' }}
              </span>
              <span class="chip" :class="overview.emailMockEnabled ? 'text-sky-200' : 'text-gray-300'">
                邮件 Mock：{{ overview.emailMockEnabled ? '开启' : '关闭' }}
              </span>
              <span class="chip" :class="overview.emailCodeLoginEnabled ? 'text-cyan-200' : 'text-gray-300'">
                邮箱验证码：{{ overview.emailCodeLoginEnabled ? `开启 · ${overview.emailCodeExpireMinutes || 5} 分钟` : '关闭' }}
              </span>
            </div>
          </div>
        </div>
        <div class="grid grid-cols-1 lg:grid-cols-3 gap-4">
          <div class="glass-panel p-5 space-y-3">
            <div class="text-sm text-gray-300">存储与目录</div>
            <div class="text-xs text-gray-400 break-all">原图根目录：{{ overview.localStorageRoot || '—' }}</div>
            <div class="text-xs text-gray-400 break-all">用户数据目录：{{ overview.userDataRoot || '—' }}</div>
            <div class="text-xs text-gray-400">平均每用户已用：{{ overview.userCount ? formatBytes(Math.round(overview.totalUsedBytes / overview.userCount)) : '0 B' }}</div>
          </div>
          <div class="glass-panel p-5 space-y-3">
            <div class="text-sm text-gray-300">认证与通知</div>
            <div class="text-xs text-gray-400">短信：{{ overview.smsEnabled ? smsProviderLabel(overview.smsProviderType) : '关闭' }}</div>
            <div class="text-xs text-gray-400">邮件：{{ overview.emailEnabled ? emailProviderLabel(overview.emailProviderType) : '关闭' }}</div>
            <div class="text-xs text-gray-400">邮箱验证码：{{ overview.emailCodeLoginEnabled ? `开启 · ${overview.emailCodeExpireMinutes || 5} 分钟` : '关闭' }} / Mock {{ overview.emailMockEnabled ? '开启' : '关闭' }}</div>
            <div class="text-xs text-gray-400">支付：{{ overview.paymentEnabled ? paymentProviderLabel(overview.paymentProviderType) : '关闭' }} / Mock {{ overview.paymentMockEnabled ? '开启' : '关闭' }}</div>
          </div>
          <div class="glass-panel p-5 space-y-3">
            <div class="text-sm text-gray-300">风险提示</div>
            <div class="text-xs text-gray-400">锁定用户：{{ overview.lockedUserCount }}</div>
            <div class="text-xs text-gray-400">禁用用户：{{ overview.disabledUserCount }}</div>
            <div class="text-xs text-gray-400">默认容量：{{ formatQuotaGb(overview.defaultUserQuotaBytes) }} / VIP 增量 {{ formatQuotaGb(overview.defaultVipExtraQuotaBytes) }}</div>
          </div>
        </div>
      </section>

      <ApiTestToolPanel v-if="activeTab === 'integrations'" />

      <section v-if="activeTab === 'global'" class="glass-panel p-6 space-y-5">
        <div class="flex items-center justify-between flex-wrap gap-3">
          <div>
            <h2 class="text-lg font-light">全局设置</h2>
            <p class="text-xs text-gray-400">当前先支持多用户总开关、默认空间、存储根目录与默认上传位置。</p>
          </div>
          <button
            class="px-4 py-2 rounded-lg bg-purple-600 hover:bg-purple-500 disabled:opacity-60 text-sm"
            :disabled="loading || savingSettings"
            @click="saveSettings"
          >
            {{ savingSettings ? '保存中...' : '保存全局设置' }}
          </button>
        </div>

        <div class="grid grid-cols-1 lg:grid-cols-2 gap-4">
          <label class="glass-panel p-4 flex items-center justify-between gap-3">
            <div>
              <div class="text-sm">开启多用户</div>
              <div class="text-xs text-gray-400">关闭时暂不开放注册入口，仍保留超级管理员登录。</div>
            </div>
            <input v-model="settings.multiUserEnabled" type="checkbox" class="w-5 h-5 rounded" />
          </label>

          <label class="glass-panel p-4 flex items-center justify-between gap-3">
            <div>
              <div class="text-sm">开启定时扫描</div>
              <div class="text-xs text-gray-400">当前默认关闭，避免低配服务器自动拉满。</div>
            </div>
            <input v-model="settings.scanSchedulerEnabled" type="checkbox" class="w-5 h-5 rounded" />
          </label>

          <label class="space-y-2">
            <span class="text-sm text-gray-300">扫描工作线程数</span>
            <input
              v-model.number="settings.scanWorkerCount"
              type="number"
              min="1"
              max="2"
              step="1"
              class="w-full px-4 py-3 bg-gray-900/70 border border-white/10 rounded-xl"
            />
            <div class="text-xs text-gray-400">建议弱性能服务器保持 1，需要略微提速时可调为 2。</div>
          </label>

          <label class="glass-panel p-4 flex items-center justify-between gap-3">
            <div>
              <div class="text-sm">强制绑定手机号</div>
              <div class="text-xs text-gray-400">开启后注册必须填写手机号，但仍允许账号密码登录；手机号验证码登录作为补充能力开放。</div>
            </div>
            <input v-model="settings.forceBindPhone" type="checkbox" class="w-5 h-5 rounded" />
          </label>

          <label class="glass-panel p-4 flex items-center justify-between gap-3">
            <div>
              <div class="text-sm">开启自动续费建单任务</div>
              <div class="text-xs text-gray-400">每 5 分钟检查一次到期自动续费订单，仅自动创建续费待支付订单，不直接扣款。</div>
            </div>
            <input v-model="settings.autoRenewSchedulerEnabled" type="checkbox" class="w-5 h-5 rounded" />
          </label>

          <label class="space-y-2">
            <span class="text-sm text-gray-300">默认用户空间限额（GB）</span>
            <input
              v-model.number="settingsQuotaView.defaultUserQuotaGb"
              type="number"
              min="0"
              step="0.5"
              class="w-full px-4 py-3 bg-gray-900/70 border border-white/10 rounded-xl"
            />
            <div class="text-xs text-gray-400">注册用户默认获得的基础原图空间。</div>
          </label>

          <label class="space-y-2">
            <span class="text-sm text-gray-300">默认 VIP 额外配额（GB）</span>
            <input
              v-model.number="settingsQuotaView.defaultVipExtraQuotaGb"
              type="number"
              min="0"
              step="0.5"
              class="w-full px-4 py-3 bg-gray-900/70 border border-white/10 rounded-xl"
            />
            <div class="text-xs text-gray-400">与基础配额分离，后续 VIP 方案可在此基础上继续演进。</div>
          </label>

          <label class="space-y-2">
            <span class="text-sm text-gray-300">默认上传存储</span>
            <select
              v-model="settings.defaultStorageProviderId"
              class="w-full px-4 py-3 bg-gray-900/70 border border-white/10 rounded-xl"
            >
              <option :value="null">请选择默认存储</option>
              <option
                v-for="provider in storageProviders"
                :key="provider.id"
                :value="provider.id"
                :disabled="!provider.enabled || !provider.uploadSupported"
              >
                {{ provider.name }} · {{ storageTypeLabel(provider.type) }}{{ provider.uploadSupported ? '' : '（暂不可用）' }}
              </option>
            </select>
          </label>

          <label class="space-y-2">
            <span class="text-sm text-gray-300">原图存储根目录</span>
            <input
              v-model="settings.localStorageRoot"
              type="text"
              placeholder="例如 data/photos"
              class="w-full px-4 py-3 bg-gray-900/70 border border-white/10 rounded-xl"
            />
          </label>

          <label class="space-y-2">
            <span class="text-sm text-gray-300">用户资料根目录</span>
            <input
              v-model="settings.userDataRoot"
              type="text"
              placeholder="例如 data/users"
              class="w-full px-4 py-3 bg-gray-900/70 border border-white/10 rounded-xl"
            />
          </label>
        </div>
      </section>

      <section v-if="activeTab === 'integrations'" class="glass-panel p-6 space-y-5">
        <div class="flex items-center justify-between flex-wrap gap-3">
          <div>
            <h2 class="text-lg font-light">短信登录设置</h2>
            <p class="text-xs text-gray-400">支持阿里云、腾讯云、Twilio、通用 Webhook 与 mock 模式；默认可仅开启 mock 便于联调测试。</p>
          </div>
          <button
            class="px-4 py-2 rounded-lg bg-purple-600 hover:bg-purple-500 disabled:opacity-60 text-sm"
            :disabled="loading || savingSettings"
            @click="saveSettings"
          >
            {{ savingSettings ? '保存中...' : '保存集成配置' }}
          </button>
        </div>

        <div class="grid grid-cols-1 lg:grid-cols-2 gap-4">
          <label class="space-y-2">
            <span class="text-sm text-gray-300">短信平台</span>
            <select
              v-model="settings.smsProviderType"
              class="w-full px-4 py-3 bg-gray-900/70 border border-white/10 rounded-xl"
            >
              <option value="ALIYUN">阿里云短信</option>
              <option value="TENCENT_CLOUD">腾讯云短信</option>
              <option value="TWILIO">Twilio</option>
              <option value="HUAWEI_CLOUD">华为云短信</option>
              <option value="VOLCENGINE">火山引擎短信</option>
              <option value="CLOOPEN">容联云通讯</option>
              <option value="AWS_SNS">AWS SNS</option>
              <option value="YUNPIAN">云片</option>
              <option value="SUBMAIL">Submail</option>
              <option value="MESSAGEBIRD">MessageBird</option>
              <option value="VONAGE">Vonage</option>
              <option value="INFOBIP">Infobip</option>
              <option value="PLIVO">Plivo</option>
              <option value="SINCH">Sinch</option>
              <option value="TELNYX">Telnyx</option>
              <option value="SMSAERO">SMS Aero</option>
              <option value="HTTP_WEBHOOK">通用 Webhook</option>
            </select>
          </label>

          <label class="glass-panel p-4 flex items-center justify-between gap-3">
            <div>
              <div class="text-sm">开启真实短信发送</div>
              <div class="text-xs text-gray-400">开启后将使用当前所选短信平台发送验证码。</div>
            </div>
            <input v-model="settings.smsEnabled" type="checkbox" class="w-5 h-5 rounded" />
          </label>

          <label class="glass-panel p-4 flex items-center justify-between gap-3">
            <div>
              <div class="text-sm">开启 Mock 验证码</div>
              <div class="text-xs text-gray-400">建议开发期保持开启，前端可直接看到 debugCode 方便联调。</div>
            </div>
            <input v-model="settings.smsMockEnabled" type="checkbox" class="w-5 h-5 rounded" />
          </label>

          <label class="space-y-2">
            <span class="text-sm text-gray-300">短信 Endpoint</span>
            <input
              v-model="settings.smsEndpoint"
              type="text"
              :placeholder="smsEndpointPlaceholder"
              class="w-full px-4 py-3 bg-gray-900/70 border border-white/10 rounded-xl"
            />
            <span class="text-xs text-gray-500">用于请求短信服务商接口地址；如平台有默认网关，也可留空交给推荐配置。</span>
          </label>

          <label class="space-y-2">
            <span class="text-sm text-gray-300">RegionId</span>
            <input
              v-model="settings.smsRegionId"
              type="text"
              :placeholder="smsRegionPlaceholder"
              class="w-full px-4 py-3 bg-gray-900/70 border border-white/10 rounded-xl"
            />
            <span class="text-xs text-gray-500">云厂商通常要求填写地域，例如 `cn-hangzhou`、`ap-guangzhou`。</span>
          </label>

          <label class="space-y-2">
            <span class="text-sm text-gray-300">{{ smsAccessKeyIdLabel }}</span>
            <input
              v-model="settings.smsAccessKeyId"
              type="text"
              :placeholder="`${smsAccessKeyIdLabel}`"
              class="w-full px-4 py-3 bg-gray-900/70 border border-white/10 rounded-xl"
            />
            <span class="text-xs text-gray-500">这里填写平台提供的公开标识，密钥本体填在下面的 Secret 字段。</span>
          </label>

          <label class="space-y-2">
            <span class="text-sm text-gray-300">{{ smsAccessKeySecretLabel }}</span>
            <input
              v-model="settings.smsAccessKeySecret"
              type="password"
              autocomplete="new-password"
              class="w-full px-4 py-3 bg-gray-900/70 border border-white/10 rounded-xl"
            />
            <span class="text-xs text-gray-500">敏感字段，保存后前端不会主动回显，请妥善保管。</span>
          </label>

          <label class="space-y-2">
            <span class="text-sm text-gray-300">{{ smsSignLabel }}</span>
            <input
              v-model="settings.smsSignName"
              type="text"
              :placeholder="`${smsSignLabel}`"
              class="w-full px-4 py-3 bg-gray-900/70 border border-white/10 rounded-xl"
            />
          </label>

          <label class="space-y-2">
            <span class="text-sm text-gray-300">{{ smsTemplateLabel }}</span>
            <input
              v-model="settings.smsTemplateCode"
              type="text"
              :placeholder="`${smsTemplateLabel}`"
              class="w-full px-4 py-3 bg-gray-900/70 border border-white/10 rounded-xl"
            />
          </label>

          <label v-if="settings.smsProviderType === 'TENCENT_CLOUD'" class="space-y-2">
            <span class="text-sm text-gray-300">SmsSdkAppId</span>
            <input
              v-model="settings.smsSdkAppId"
              type="text"
              placeholder="腾讯云短信应用 ID"
              class="w-full px-4 py-3 bg-gray-900/70 border border-white/10 rounded-xl"
            />
          </label>

          <label class="space-y-2">
            <span class="text-sm text-gray-300">模板参数名</span>
            <input
              v-model="settings.smsTemplateParamName"
              type="text"
              placeholder="code"
              class="w-full px-4 py-3 bg-gray-900/70 border border-white/10 rounded-xl"
              :disabled="settings.smsProviderType === 'TENCENT_CLOUD'"
            />
            <span class="text-xs text-gray-500">短信模板中验证码变量名；腾讯云通常走固定参数格式，因此这里会禁用。</span>
          </label>

          <label class="space-y-2">
            <span class="text-sm text-gray-300">验证码有效期（分钟）</span>
            <input
              v-model.number="settings.smsCodeExpireMinutes"
              type="number"
              min="1"
              class="w-full px-4 py-3 bg-gray-900/70 border border-white/10 rounded-xl"
            />
            <span class="text-xs text-gray-500">控制短信验证码失效时间，建议保持在 `5~15` 分钟区间。</span>
          </label>
        </div>
      </section>

      <section v-if="activeTab === 'integrations'" class="glass-panel p-6 space-y-5">
        <div class="flex items-center justify-between flex-wrap gap-3">
          <div>
            <h2 class="text-lg font-light">邮件发送设置</h2>
            <p class="text-xs text-gray-400">当前以 SMTP 为基础，后续可兼容 SES、SendGrid、Mailgun、Resend、企业邮箱等 SMTP 接入方式。</p>
          </div>
          <div class="flex items-center gap-3">
            <button
              class="px-4 py-2 rounded-lg bg-sky-600 hover:bg-sky-500 disabled:opacity-60 text-sm"
              :disabled="sendingTestEmail"
              @click="sendTestEmail"
            >
              {{ sendingTestEmail ? '发送中...' : '发送测试邮件' }}
            </button>
            <button
              class="px-4 py-2 rounded-lg bg-emerald-600 hover:bg-emerald-500 disabled:opacity-60 text-sm"
              :disabled="sendingCustomEmail"
              @click="sendCustomEmail"
            >
              {{ sendingCustomEmail ? '发送中...' : '发送自定义邮件' }}
            </button>
            <button
              class="px-4 py-2 rounded-lg bg-gray-800 hover:bg-gray-700 disabled:opacity-60 text-sm border border-white/10"
              :disabled="savingSettings"
              @click="applyEmailPreset"
            >
              应用平台推荐配置
            </button>
          </div>
        </div>

        <div class="grid grid-cols-1 lg:grid-cols-2 gap-4">
          <label class="space-y-2">
            <span class="text-sm text-gray-300">邮件平台</span>
            <select
              v-model="settings.emailProviderType"
              class="w-full px-4 py-3 bg-gray-900/70 border border-white/10 rounded-xl"
            >
              <option value="SMTP">SMTP</option>
              <option value="CUSTOM_SMTP">自定义 SMTP</option>
              <option value="ALIYUN_DIRECTMAIL">阿里云邮件推送</option>
              <option value="TENCENT_EXMAIL">腾讯企业邮</option>
              <option value="AWS_SES">AWS SES</option>
              <option value="SENDGRID">SendGrid</option>
              <option value="MAILGUN">Mailgun</option>
              <option value="RESEND">Resend</option>
              <option value="POSTMARK">Postmark</option>
              <option value="BREVO">Brevo</option>
              <option value="MAILERSEND">MailerSend</option>
              <option value="ZEPTOMAIL">ZeptoMail</option>
              <option value="MAILJET">Mailjet</option>
              <option value="SPARKPOST">SparkPost</option>
              <option value="ELASTIC_EMAIL">Elastic Email</option>
              <option value="SMTP2GO">SMTP2GO</option>
              <option value="SENDLAYER">SendLayer</option>
              <option value="QQ_EXMAIL">QQ 企业邮箱</option>
              <option value="NETEASE_EXMAIL">网易企业邮箱</option>
            </select>
          </label>

          <label class="glass-panel p-4 flex items-center justify-between gap-3">
            <div>
              <div class="text-sm">开启邮件发送</div>
              <div class="text-xs text-gray-400">用于后续邮件注册、通知与找回密码等能力。</div>
            </div>
            <input v-model="settings.emailEnabled" type="checkbox" class="w-5 h-5 rounded" />
          </label>

          <label class="glass-panel p-4 flex items-center justify-between gap-3">
            <div>
              <div class="text-sm">邮箱验证码 Mock</div>
              <div class="text-xs text-gray-400">未接真实 SMTP 时也可先联调邮箱验证码登录与绑定。</div>
            </div>
            <input v-model="settings.emailMockEnabled" type="checkbox" class="w-5 h-5 rounded" />
          </label>

          <label class="glass-panel p-4 flex items-center justify-between gap-3">
            <div>
              <div class="text-sm">开启邮箱验证码登录</div>
              <div class="text-xs text-gray-400">允许使用邮箱 + 验证码直接登录。</div>
            </div>
            <input v-model="settings.emailCodeLoginEnabled" type="checkbox" class="w-5 h-5 rounded" />
          </label>

          <label class="space-y-2">
            <span class="text-sm text-gray-300">SMTP Host</span>
            <input
              v-model="settings.emailHost"
              type="text"
              :placeholder="emailPreset.host || 'smtp.example.com'"
              class="w-full px-4 py-3 bg-gray-900/70 border border-white/10 rounded-xl"
            />
            <span class="text-xs text-gray-500">邮件服务器域名；切换平台后可优先使用下方推荐主机。</span>
          </label>

          <label class="space-y-2">
            <span class="text-sm text-gray-300">SMTP Port</span>
            <input
              v-model.number="settings.emailPort"
              type="number"
              min="1"
              class="w-full px-4 py-3 bg-gray-900/70 border border-white/10 rounded-xl"
            />
            <span class="text-xs text-gray-500">常见为 `465`（SSL）或 `587`（STARTTLS）。</span>
          </label>

          <label class="space-y-2">
            <span class="text-sm text-gray-300">用户名</span>
            <input
              v-model="settings.emailUsername"
              type="text"
              placeholder="SMTP 用户名 / 邮箱账号"
              class="w-full px-4 py-3 bg-gray-900/70 border border-white/10 rounded-xl"
            />
            <span class="text-xs text-gray-500">多数平台要求填写完整邮箱地址或专用 SMTP 用户名。</span>
          </label>

          <label class="space-y-2">
            <span class="text-sm text-gray-300">密码 / 授权码</span>
            <input
              v-model="settings.emailPassword"
              type="password"
              autocomplete="new-password"
              class="w-full px-4 py-3 bg-gray-900/70 border border-white/10 rounded-xl"
            />
            <span class="text-xs text-gray-500">建议使用 SMTP 授权码而不是主账号登录密码。</span>
          </label>

          <label class="space-y-2">
            <span class="text-sm text-gray-300">协议</span>
            <input
              v-model="settings.emailProtocol"
              type="text"
              :placeholder="emailPreset.protocol"
              class="w-full px-4 py-3 bg-gray-900/70 border border-white/10 rounded-xl"
            />
            <span class="text-xs text-gray-500">通常填写 `smtp`；如后续接其它协议，可在这里扩展。</span>
          </label>

          <label class="space-y-2">
            <span class="text-sm text-gray-300">发件邮箱</span>
            <input
              v-model="settings.emailFromAddress"
              type="email"
              placeholder="noreply@example.com"
              class="w-full px-4 py-3 bg-gray-900/70 border border-white/10 rounded-xl"
            />
            <span class="text-xs text-gray-500">用于真正发送邮件时显示在 From 头中的邮箱地址。</span>
          </label>

          <label class="space-y-2">
            <span class="text-sm text-gray-300">发件人名称</span>
            <input
              v-model="settings.emailFromName"
              type="text"
              placeholder="Photo Exhibition"
              class="w-full px-4 py-3 bg-gray-900/70 border border-white/10 rounded-xl"
            />
          </label>

          <label class="space-y-2">
            <span class="text-sm text-gray-300">回复邮箱</span>
            <input
              v-model="settings.emailReplyTo"
              type="email"
              placeholder="support@example.com"
              class="w-full px-4 py-3 bg-gray-900/70 border border-white/10 rounded-xl"
            />
          </label>

          <label class="space-y-2">
            <span class="text-sm text-gray-300">测试收件人</span>
            <input
              v-model="settings.emailTestRecipient"
              type="email"
              placeholder="you@example.com"
              class="w-full px-4 py-3 bg-gray-900/70 border border-white/10 rounded-xl"
            />
            <span class="text-xs text-gray-500">点击“发送测试邮件”时默认投递到这里。</span>
          </label>

          <label class="space-y-2">
            <span class="text-sm text-gray-300">邮箱验证码有效期（分钟）</span>
            <input
              v-model.number="settings.emailCodeExpireMinutes"
              type="number"
              min="1"
              step="1"
              class="w-full px-4 py-3 bg-gray-900/70 border border-white/10 rounded-xl"
            />
            <span class="text-xs text-gray-500">登录验证码与邮箱绑定验证码共用该有效期。</span>
          </label>

          <div class="grid grid-cols-1 md:grid-cols-2 gap-3">
            <label class="glass-panel p-4 flex items-center justify-between gap-3">
              <div>
                <div class="text-sm">启用 SSL</div>
                <div class="text-xs text-gray-400">常见于 465 端口。</div>
              </div>
              <input v-model="settings.emailSslEnabled" type="checkbox" class="w-5 h-5 rounded" />
            </label>
            <label class="glass-panel p-4 flex items-center justify-between gap-3">
              <div>
                <div class="text-sm">启用 STARTTLS</div>
                <div class="text-xs text-gray-400">常见于 587 端口。</div>
              </div>
              <input v-model="settings.emailStarttlsEnabled" type="checkbox" class="w-5 h-5 rounded" />
            </label>
          </div>
        </div>
        <div class="rounded-xl border border-white/10 bg-white/5 px-4 py-3 text-xs text-gray-300 space-y-1">
          <div>推荐主机：{{ emailPreset.host || '请按服务商文档填写' }}</div>
          <div>推荐端口：{{ emailPreset.port }} · 协议：{{ emailPreset.protocol }} · SSL：{{ emailPreset.sslEnabled ? '开启' : '关闭' }} · STARTTLS：{{ emailPreset.starttlsEnabled ? '开启' : '关闭' }}</div>
          <div>{{ emailPreset.hint }}</div>
        </div>
        <div class="rounded-xl border border-white/10 bg-white/5 px-4 py-4 space-y-4">
          <div>
            <div class="text-sm text-white">自定义邮件发送</div>
            <div class="text-xs text-gray-400 mt-1">用于预演通知邮件、邮箱注册和找回密码等后续链路。</div>
          </div>
          <div class="grid grid-cols-1 lg:grid-cols-2 gap-4">
            <label class="space-y-2">
              <span class="text-sm text-gray-300">收件人</span>
              <input v-model="customEmail.recipient" type="email" placeholder="user@example.com" class="w-full px-4 py-3 bg-gray-900/70 border border-white/10 rounded-xl" />
            </label>
            <label class="space-y-2">
              <span class="text-sm text-gray-300">主题</span>
              <input v-model="customEmail.subject" type="text" placeholder="欢迎使用 Photo Exhibition" class="w-full px-4 py-3 bg-gray-900/70 border border-white/10 rounded-xl" />
            </label>
          </div>
          <label class="space-y-2 block">
            <span class="text-sm text-gray-300">正文</span>
            <textarea v-model="customEmail.content" rows="6" class="w-full px-4 py-3 bg-gray-900/70 border border-white/10 rounded-xl" placeholder="请输入邮件正文"></textarea>
          </label>
          <label class="glass-panel p-4 flex items-center justify-between gap-3">
            <div>
              <div class="text-sm">HTML 邮件</div>
              <div class="text-xs text-gray-400">关闭时按纯文本发送，更适合验证码和系统通知。</div>
            </div>
            <input v-model="customEmail.html" type="checkbox" class="w-5 h-5 rounded" />
          </label>
        </div>
      </section>

      <section v-if="activeTab === 'integrations'" class="glass-panel p-6 space-y-5">
        <div class="flex items-center justify-between flex-wrap gap-3">
          <div>
            <h2 class="text-lg font-light">支付设置</h2>
            <p class="text-xs text-gray-400">先支持支付宝、微信支付、Stripe、PayPal 与自定义 Webhook 的统一配置骨架，后续再接真实下单与回调。</p>
          </div>
          <button
            class="px-4 py-2 rounded-lg bg-gray-800 hover:bg-gray-700 disabled:opacity-60 text-sm border border-white/10"
            :disabled="savingSettings"
            @click="applyPaymentPreset"
          >
            应用平台推荐配置
          </button>
        </div>
        <div class="grid grid-cols-1 lg:grid-cols-2 gap-4">
          <label class="space-y-2">
            <span class="text-sm text-gray-300">支付平台</span>
            <select v-model="settings.paymentProviderType" class="w-full px-4 py-3 bg-gray-900/70 border border-white/10 rounded-xl">
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
          <label class="glass-panel p-4 flex items-center justify-between gap-3">
            <div>
              <div class="text-sm">启用支付</div>
              <div class="text-xs text-gray-400">关闭时仅保留订单骨架，不开放真实支付。</div>
            </div>
            <input v-model="settings.paymentEnabled" type="checkbox" class="w-5 h-5 rounded" />
          </label>
          <label class="glass-panel p-4 flex items-center justify-between gap-3">
            <div>
              <div class="text-sm">启用支付 Mock</div>
              <div class="text-xs text-gray-400">开发期建议开启，用于联调下单和订单状态流转。</div>
            </div>
            <input v-model="settings.paymentMockEnabled" type="checkbox" class="w-5 h-5 rounded" />
          </label>
          <label
            v-for="field in paymentVisibleFields"
            :key="field.key"
            class="space-y-2"
            :class="field.wide ? 'lg:col-span-2' : ''"
          >
            <div class="flex items-center justify-between gap-3">
              <span class="text-sm text-gray-300">{{ field.label }}</span>
              <span class="text-[10px] px-2 py-0.5 rounded-full border border-white/10 text-gray-500">
                {{ field.required ? '必填' : '可选' }} · {{ field.shortHint }}
              </span>
            </div>
            <textarea
              v-if="field.multiline"
              v-model="(settings as any)[field.key]"
              :rows="field.rows || 3"
              :placeholder="field.placeholder"
              class="w-full px-4 py-3 bg-gray-900/70 border border-white/10 rounded-xl"
            />
            <input
              v-else
              v-model="(settings as any)[field.key]"
              type="text"
              :placeholder="field.placeholder"
              class="w-full px-4 py-3 bg-gray-900/70 border border-white/10 rounded-xl"
            />
            <span class="text-xs text-gray-500">{{ field.description }}</span>
          </label>
          <div class="lg:col-span-2 rounded-xl border border-white/10 bg-white/5 px-4 py-3 flex items-center justify-between gap-3 flex-wrap">
            <div class="text-xs text-gray-400">
              推荐优先使用系统统一回调入口，减少各支付平台分别拼接返回页地址的出错概率。
            </div>
            <div class="flex gap-2 flex-wrap">
              <button
                type="button"
                class="px-3 py-2 rounded-lg bg-gray-800 hover:bg-gray-700 text-xs border border-white/10"
                @click="fillPaymentCallbackUrls"
              >
                一键填充统一回调地址
              </button>
              <button
                type="button"
                class="px-3 py-2 rounded-lg bg-gray-800 hover:bg-gray-700 text-xs border border-white/10"
                @click="copyPaymentCallbackUrls"
              >
                复制统一回调地址
              </button>
            </div>
          </div>
          <label class="space-y-2">
            <div class="flex items-center justify-between gap-3">
              <span class="text-sm text-gray-300">验签模式</span>
              <span class="text-[10px] px-2 py-0.5 rounded-full border border-white/10 text-gray-500">
                平台可用：{{ paymentVerificationModeOptions.map(item => item.label).join(' / ') }}
              </span>
            </div>
            <select v-model="settings.paymentVerificationMode" class="w-full px-4 py-3 bg-gray-900/70 border border-white/10 rounded-xl">
              <option
                v-for="option in paymentVerificationModeOptions"
                :key="`${settings.paymentProviderType}-${option.value}`"
                :value="option.value"
              >
                {{ option.label }}
              </option>
            </select>
            <span class="text-xs text-gray-500">{{ paymentVerificationModeDescription }}</span>
          </label>
        </div>
        <div class="rounded-xl border border-white/10 bg-white/5 px-4 py-3 text-xs text-gray-300 space-y-1">
          <div>推荐接口地址：{{ paymentPreset.apiBaseUrl }}</div>
          <div>{{ paymentPreset.hint }}</div>
          <div>当前验签模式：{{ settings.paymentVerificationMode }}，后续真实网关接入时会按此模式补齐验签链路。</div>
          <div>建议拉起方式：{{ paymentProviderMeta.initiationMode }}</div>
          <div>建议退款方式：{{ paymentProviderMeta.refundMode }}</div>
          <div v-if="paymentProviderMeta.capabilityTags.length">平台能力：{{ paymentProviderMeta.capabilityTags.join('、') }}</div>
        </div>
        <div class="grid grid-cols-1 xl:grid-cols-2 gap-4">
          <div class="rounded-xl border px-4 py-4 space-y-2 text-xs"
            :class="paymentConfigAssessment.liveModeReady
              ? 'border-emerald-400/30 bg-emerald-500/5 text-emerald-100'
              : 'border-amber-400/30 bg-amber-500/5 text-amber-100'">
            <div class="text-sm text-white">配置体检</div>
            <div>当前平台：{{ paymentProviderLabel(settings.paymentProviderType) }}</div>
            <div>真实支付：{{ paymentConfigAssessment.liveModeReady ? '可进入联调' : '仍需补配置' }}</div>
            <div>当前状态：{{ paymentConfigAssessment.summary }}</div>
            <div v-if="paymentConfigAssessment.requiredMissing.length">
              必填缺失：{{ paymentConfigAssessment.requiredMissing.map(item => item.label).join('、') }}
            </div>
            <div v-if="paymentConfigAssessment.verificationHints.length">
              验签建议：{{ paymentConfigAssessment.verificationHints.join('；') }}
            </div>
          </div>
          <div class="rounded-xl border border-white/10 bg-white/5 px-4 py-4 space-y-2 text-xs text-gray-300">
            <div class="text-sm text-white">统一回调建议</div>
            <div>推荐异步回调：{{ paymentUnifiedUrls.notifyUrl || '—' }}</div>
            <div>推荐完成返回：{{ paymentUnifiedUrls.returnUrl || '—' }}</div>
            <div :class="paymentConfigAssessment.notifyMatches ? 'text-emerald-200' : 'text-amber-200'">
              当前回调地址：{{ paymentConfigAssessment.notifyMatches ? '已使用统一入口' : '未使用统一入口' }}
            </div>
            <div :class="paymentConfigAssessment.returnMatches ? 'text-emerald-200' : 'text-amber-200'">
              当前返回地址：{{ paymentConfigAssessment.returnMatches ? '已使用统一入口' : '未使用统一入口' }}
            </div>
          </div>
        </div>
        <div class="rounded-xl border border-white/10 bg-white/5 px-4 py-4 text-xs text-gray-300 space-y-2">
          <div class="text-sm text-white">接入步骤建议</div>
          <div v-for="(step, index) in paymentProviderMeta.integrationSteps" :key="`${settings.paymentProviderType}-${index}`">
            {{ index + 1 }}. {{ step }}
          </div>
        </div>
      </section>

      <section v-if="activeTab === 'integrations'" class="glass-panel p-6 space-y-5">
        <div class="flex items-center justify-between flex-wrap gap-3">
          <div>
            <h2 class="text-lg font-light">旧数据迁移</h2>
            <p class="text-xs text-gray-400">用于把历史相册、照片、人脸、评论等补齐 `user_id`，并尝试把旧目录搬到 `data/photos/{userId}`。</p>
          </div>
          <button
            class="px-4 py-2 rounded-lg bg-amber-600 hover:bg-amber-500 disabled:opacity-60 text-sm"
            :disabled="loading || runningMigration"
            @click="runLegacyMigration"
          >
            {{ runningMigration ? '迁移中...' : '执行旧数据迁移' }}
          </button>
        </div>
        <div class="text-xs text-gray-400">
          启动时也会自动尝试一次；这里提供手动重跑入口，便于修复“登录后看不到历史照片数据”这类问题。
        </div>
        <div v-if="migrationSummary" class="rounded-2xl border border-white/10 bg-black/20 p-4 space-y-2">
          <div class="flex items-center justify-between gap-3 flex-wrap">
            <div class="text-sm text-gray-200">最近一次迁移结果</div>
            <div class="text-[11px] text-gray-400">
              {{ migrationSummary.startedAt ? `开始：${formatDateTime(migrationSummary.startedAt)}` : '' }}
              {{ migrationSummary.finishedAt ? ` · 完成：${formatDateTime(migrationSummary.finishedAt)}` : '' }}
            </div>
          </div>
          <div class="grid grid-cols-1 lg:grid-cols-3 gap-3 text-xs text-gray-300">
            <div class="rounded-xl border border-white/10 bg-white/5 p-3">
              <div class="text-[11px] text-gray-400">归属迁移总计</div>
              <div class="text-lg text-white mt-1">{{ migrationSummary.totalOwnershipMigrationCount ?? 0 }}</div>
              <div class="mt-1">归属用户：{{ migrationSummary.ownerUsername || migrationSummary.ownerUserId || '—' }}</div>
            </div>
            <div class="rounded-xl border border-white/10 bg-white/5 p-3">
              <div class="text-[11px] text-gray-400">路径改写总计</div>
              <div class="text-lg text-white mt-1">{{ migrationSummary.totalPathRewriteCount ?? 0 }}</div>
              <div class="mt-1">存储引用：{{ migrationSummary.rewrittenPhotoStorageRefCount ?? 0 }}</div>
            </div>
            <div class="rounded-xl border border-white/10 bg-white/5 p-3">
              <div class="text-[11px] text-gray-400">执行结果</div>
              <div class="text-lg mt-1" :class="migrationSummary.success ? 'text-emerald-300' : 'text-amber-300'">
                {{ migrationSummary.success ? '完成' : '未完成' }}
              </div>
              <div class="mt-1">{{ migrationSummary.message || '—' }}</div>
            </div>
          </div>
          <div class="grid grid-cols-2 lg:grid-cols-4 gap-3 text-xs text-gray-300">
            <div>相册归属：{{ migrationSummary.migratedAlbumOwnershipCount ?? 0 }}</div>
            <div>照片归属：{{ migrationSummary.migratedPhotoOwnershipCount ?? 0 }}</div>
            <div>人物归属：{{ migrationSummary.migratedPersonOwnershipCount ?? 0 }}</div>
            <div>人脸归属：{{ migrationSummary.migratedFaceOwnershipCount ?? 0 }}</div>
            <div>评论归属：{{ migrationSummary.migratedCommentOwnershipCount ?? 0 }}</div>
            <div>标签归属：{{ migrationSummary.migratedTagOwnershipCount ?? 0 }}</div>
            <div>目录迁移：{{ migrationSummary.movedTopLevelEntryCount ?? 0 }}</div>
            <div>路径回写：{{ (migrationSummary.rewrittenAlbumPathCount ?? 0) + (migrationSummary.rewrittenPhotoPathCount ?? 0) }}</div>
            <div>相册目录搬迁：{{ migrationSummary.movedAlbumDirectoryCount ?? 0 }}</div>
            <div>照片文件搬迁：{{ migrationSummary.movedPhotoFileCount ?? 0 }}</div>
            <div>相册路径重写：{{ migrationSummary.rewrittenAlbumPathCount ?? 0 }}</div>
            <div>照片存储引用：{{ migrationSummary.rewrittenPhotoStorageRefCount ?? 0 }}</div>
          </div>
        </div>
      </section>

      <section v-if="activeTab === 'users'" class="glass-panel p-6 space-y-5">
        <div class="flex items-center justify-between flex-wrap gap-3">
          <div>
            <h2 class="text-lg font-light">用户管理</h2>
            <p class="text-xs text-gray-400">支持搜索、分页、邮箱/手机号维护、配额调整、角色状态切换与密码重置。</p>
          </div>
          <div class="flex items-center gap-3 flex-wrap">
            <input
              v-model.trim="userKeyword"
              type="text"
              placeholder="搜索用户名 / 昵称 / slug / 手机号 / 邮箱"
              class="px-4 py-2 rounded-lg bg-gray-900/70 border border-white/10 text-sm min-w-[280px]"
              @keyup.enter="searchUsers"
            />
            <button
              class="px-4 py-2 rounded-lg bg-gray-800 hover:bg-gray-700 text-sm border border-white/10"
              :disabled="loading"
              @click="searchUsers"
            >
              搜索
            </button>
            <select
              v-model.number="usersPage.size"
              class="px-4 py-2 rounded-lg bg-gray-900/70 border border-white/10 text-sm"
              @change="handleUsersPageSizeChange"
            >
              <option :value="4">每页 4 个</option>
              <option :value="8">每页 8 个</option>
              <option :value="12">每页 12 个</option>
              <option :value="20">每页 20 个</option>
            </select>
          </div>
        </div>

        <ConfigurableTable
          :columns="userTableColumns"
          :rows="users"
          :preferences="getTablePreference('users')"
          :loading="loading"
          loading-text="正在加载用户列表..."
          empty-text="暂无用户数据，可尝试调整搜索条件。"
          @update:preferences="updateTablePreference('users', $event)"
        >
          <template #cell-user="{ row: user }">
            <div class="space-y-2">
              <label class="block space-y-1">
                <span class="text-[11px] text-gray-400">昵称</span>
                <input v-model="user.nickname" type="text" class="w-full px-3 py-2 bg-gray-900/70 border border-white/10 rounded-lg" />
              </label>
              <div class="text-xs text-gray-400">
                `{{ user.username }}` · slug `{{ user.slug }}`
              </div>
              <label class="block space-y-1">
                <span class="text-[11px] text-gray-400">项目名（中文）</span>
                <input v-model="user.projectNameZh" type="text" class="w-full px-3 py-2 bg-gray-900/70 border border-white/10 rounded-lg text-xs" />
              </label>
              <label class="block space-y-1">
                <span class="text-[11px] text-gray-400">项目名（英文）</span>
                <input v-model="user.projectNameEn" type="text" class="w-full px-3 py-2 bg-gray-900/70 border border-white/10 rounded-lg text-xs" />
              </label>
            </div>
          </template>
          <template #cell-phone="{ row: user }">
            <div class="space-y-2">
              <label class="block space-y-1">
                <span class="text-[11px] text-gray-400">手机号</span>
                <input v-model="user.phone" type="text" class="w-full px-3 py-2 bg-gray-900/70 border border-white/10 rounded-lg" />
              </label>
              <label class="flex items-center gap-2 text-xs text-gray-300">
                <input v-model="user.phoneVerified" type="checkbox" class="w-4 h-4 rounded" />
                手机已验证
              </label>
              <label class="block space-y-1">
                <span class="text-[11px] text-gray-400">邮箱</span>
                <input v-model="user.email" type="email" class="w-full px-3 py-2 bg-gray-900/70 border border-white/10 rounded-lg" />
              </label>
              <label class="flex items-center gap-2 text-xs text-gray-300">
                <input v-model="user.emailVerified" type="checkbox" class="w-4 h-4 rounded" />
                邮箱已验证
              </label>
            </div>
          </template>
          <template #cell-role="{ row: user }">
            <select v-model="user.role" class="w-full px-3 py-2 bg-gray-900/70 border border-white/10 rounded-lg">
              <option value="SUPER_ADMIN">SUPER_ADMIN</option>
              <option value="USER_ADMIN">USER_ADMIN</option>
            </select>
          </template>
          <template #cell-status="{ row: user }">
            <select v-model="user.status" class="w-full px-3 py-2 bg-gray-900/70 border border-white/10 rounded-lg">
              <option value="ACTIVE">ACTIVE</option>
              <option value="PENDING">PENDING</option>
              <option value="DISABLED">DISABLED</option>
              <option value="LOCKED">LOCKED</option>
            </select>
          </template>
          <template #cell-quota="{ row: user }">
            <div class="space-y-2">
              <label class="block space-y-1">
                <span class="text-[11px] text-gray-400">基础配额（GB）</span>
                <input v-model.number="user.storageQuotaGb" type="number" min="0" step="0.5" class="w-full px-3 py-2 bg-gray-900/70 border border-white/10 rounded-lg" />
              </label>
              <label class="block space-y-1">
                <span class="text-[11px] text-gray-400">VIP 额外配额（GB）</span>
                <input v-model.number="user.vipExtraQuotaGb" type="number" min="0" step="0.5" class="w-full px-3 py-2 bg-gray-900/70 border border-white/10 rounded-lg" />
              </label>
              <div class="text-xs text-gray-400">总配额 {{ formatQuotaGb(user.effectiveStorageQuotaBytes) }}</div>
            </div>
          </template>
          <template #cell-vip="{ row: user }">
            <div class="space-y-2">
              <select v-model="user.currentVipPlanId" class="w-full px-3 py-2 bg-gray-900/70 border border-white/10 rounded-lg">
                <option :value="null">未开通套餐</option>
                <option v-for="plan in vipPlans" :key="plan.id" :value="plan.id" :disabled="!plan.enabled">
                  {{ plan.name }} · +{{ formatQuotaGb(plan.extraQuotaBytes) }}
                </option>
              </select>
              <input v-model="user.vipExpireAt" type="datetime-local" class="w-full px-3 py-2 bg-gray-900/70 border border-white/10 rounded-lg" />
            </div>
          </template>
          <template #cell-storage="{ row: user }">
            <div class="space-y-2">
              <select v-model="user.preferredStorageProviderId" class="w-full px-3 py-2 bg-gray-900/70 border border-white/10 rounded-lg">
                <option :value="null">跟随系统默认上传存储</option>
                <option
                  v-for="provider in storageProviders"
                  :key="provider.id"
                  :value="provider.id"
                  :disabled="!provider.enabled || !provider.uploadSupported"
                >
                  {{ provider.name }}{{ provider.uploadSupported ? '' : '（暂不可用）' }}
                </option>
              </select>
              <div class="text-xs text-gray-400">{{ describeAssignedStorage(user) }}</div>
            </div>
          </template>
          <template #cell-usage="{ row: user }">
            <div class="space-y-1 text-xs text-gray-300">
              <div>已用 {{ formatBytes(user.storageUsedBytes) }}</div>
              <div>剩余 {{ formatBytes(user.remainingStorageBytes) }}</div>
              <div v-if="user.currentVipPlanName">套餐 {{ user.currentVipPlanName }}</div>
            </div>
          </template>
          <template #cell-visible="{ row: user }">
            <label class="flex items-center gap-2 text-sm text-gray-300">
              <input v-model="user.multiUserVisible" type="checkbox" class="w-4 h-4 rounded" />
              公开
            </label>
          </template>
          <template #cell-lastLoginAt="{ row: user }">
            <div class="space-y-1 text-xs text-gray-300">
              <div>{{ formatDate(user.lastLoginAt) }}</div>
              <div class="text-gray-500">创建于 {{ formatDate(user.createdAt) }}</div>
            </div>
          </template>
          <template #cell-actions="{ row: user }">
            <div class="space-y-2">
              <label class="block space-y-1">
                <span class="text-[11px] text-gray-400">新密码</span>
                <input v-model="user.pendingPassword" type="password" autocomplete="new-password" class="w-full px-3 py-2 bg-gray-900/70 border border-white/10 rounded-lg text-xs" />
              </label>
              <label class="block space-y-1">
                <span class="text-[11px] text-gray-400">确认新密码</span>
                <input v-model="user.pendingPasswordConfirm" type="password" autocomplete="new-password" class="w-full px-3 py-2 bg-gray-900/70 border border-white/10 rounded-lg text-xs" />
              </label>
              <button
                class="w-full px-3 py-2 rounded-lg bg-purple-600 hover:bg-purple-500 disabled:opacity-60 text-sm"
                :disabled="savingUserId === user.id"
                @click="saveUser(user)"
              >
                {{ savingUserId === user.id ? '保存中...' : '保存' }}
              </button>
            </div>
          </template>
        </ConfigurableTable>
        <div class="flex items-center justify-between pt-2 text-sm text-gray-300">
          <span>第 {{ usersPage.page + 1 }} / {{ Math.max(usersPage.totalPages, 1) }} 页，共 {{ usersPage.totalElements }} 个用户</span>
          <div class="flex items-center gap-2">
            <button class="px-3 py-1 rounded bg-gray-800 border border-white/10 disabled:opacity-40" :disabled="usersPage.first" @click="changeUsersPage(usersPage.page - 1)">上一页</button>
            <button class="px-3 py-1 rounded bg-gray-800 border border-white/10 disabled:opacity-40" :disabled="usersPage.last" @click="changeUsersPage(usersPage.page + 1)">下一页</button>
          </div>
        </div>
      </section>

      <section v-if="activeTab === 'logins'" class="glass-panel p-6 space-y-5">
        <div class="flex items-center justify-between flex-wrap gap-3">
          <div>
            <h2 class="text-lg font-light">登录记录</h2>
            <p class="text-xs text-gray-400">用于查看最近登录成功/失败情况，便于定位账号异常、撞库或验证码登录问题。</p>
          </div>
          <div class="flex items-center gap-3 flex-wrap">
            <select
              v-model="selectedLoginRecordUserId"
              class="px-4 py-2 bg-gray-900/70 border border-white/10 rounded-xl text-sm"
              @change="handleLoginRecordUserChange"
            >
              <option :value="null">全部用户</option>
              <option v-for="user in users" :key="user.id" :value="user.id">
                {{ user.nickname || user.username }}
              </option>
            </select>
            <button
              class="px-4 py-2 rounded-lg bg-gray-800 hover:bg-gray-700 text-sm border border-white/10"
              :disabled="loading"
              @click="loadLoginRecords"
            >
              刷新记录
            </button>
            <select
              v-model.number="loginRecordsPage.size"
              class="px-4 py-2 bg-gray-900/70 border border-white/10 rounded-xl text-sm"
              @change="handleLoginRecordsPageSizeChange"
            >
              <option :value="10">每页 10 条</option>
              <option :value="20">每页 20 条</option>
              <option :value="50">每页 50 条</option>
              <option :value="100">每页 100 条</option>
            </select>
          </div>
        </div>

        <div class="text-[11px] text-gray-500">筛选用户来自当前已加载用户页；如需精确筛选，可先在“用户管理”中搜索到目标用户。</div>
        <ConfigurableTable
          :columns="loginTableColumns"
          :rows="loginRecords"
          :preferences="getTablePreference('logins')"
          :loading="loading"
          loading-text="正在加载登录记录..."
          empty-text="暂无登录记录。"
          @update:preferences="updateTablePreference('logins', $event)"
        >
          <template #cell-createdAt="{ row: record }">
            <div class="text-xs text-gray-300">{{ formatDate(record.createdAt) }}</div>
          </template>
          <template #cell-account="{ row: record }">
            <div>{{ record.nickname || record.usernameSnapshot || '未知账号' }}</div>
            <div class="text-xs text-gray-500">
              {{ record.userSlug ? `slug: ${record.userSlug}` : '未绑定用户' }}
              <span v-if="record.phoneSnapshot"> · {{ record.phoneSnapshot }}</span>
            </div>
          </template>
          <template #cell-loginMethod="{ row: record }">
            <div class="text-xs text-gray-300">{{ loginMethodLabel(record.loginMethod) }}</div>
          </template>
          <template #cell-success="{ row: record }">
            <span
              class="px-2 py-1 rounded-full text-xs border"
              :class="record.success
                ? 'bg-emerald-500/10 border-emerald-400/30 text-emerald-200'
                : 'bg-rose-500/10 border-rose-400/30 text-rose-200'"
            >
              {{ record.success ? '成功' : '失败' }}
            </span>
          </template>
          <template #cell-source="{ row: record }">
            <div class="text-xs text-gray-400">
              <div>{{ record.ipAddress || '未知 IP' }}</div>
              <div class="mt-1 break-all text-gray-500">{{ record.userAgent || '未知 UA' }}</div>
            </div>
          </template>
          <template #cell-failureReason="{ row: record }">
            <div class="text-xs text-gray-400">{{ record.failureReason || '—' }}</div>
          </template>
        </ConfigurableTable>
        <div class="flex items-center justify-between pt-2 text-sm text-gray-300">
          <span>第 {{ loginRecordsPage.page + 1 }} / {{ Math.max(loginRecordsPage.totalPages, 1) }} 页，共 {{ loginRecordsPage.totalElements }} 条记录</span>
          <div class="flex items-center gap-2">
            <button class="px-3 py-1 rounded bg-gray-800 border border-white/10 disabled:opacity-40" :disabled="loginRecordsPage.first" @click="changeLoginRecordsPage(loginRecordsPage.page - 1)">上一页</button>
            <button class="px-3 py-1 rounded bg-gray-800 border border-white/10 disabled:opacity-40" :disabled="loginRecordsPage.last" @click="changeLoginRecordsPage(loginRecordsPage.page + 1)">下一页</button>
          </div>
        </div>
      </section>

      <section v-if="activeTab === 'operations'" class="glass-panel p-6 space-y-5">
        <div class="flex items-center justify-between flex-wrap gap-3">
          <div>
            <h2 class="text-lg font-light">操作记录</h2>
            <p class="text-xs text-gray-400">记录上传、删除、移动、重命名、扫描控制等后台操作，便于追溯谁改了什么。</p>
          </div>
          <div class="flex items-center gap-3 flex-wrap">
            <select
              v-model="selectedOperationLogUserId"
              class="px-4 py-2 bg-gray-900/70 border border-white/10 rounded-xl text-sm"
              @change="handleOperationLogUserChange"
            >
              <option :value="null">全部用户</option>
              <option v-for="user in users" :key="user.id" :value="user.id">
                {{ user.nickname || user.username }}
              </option>
            </select>
            <button
              class="px-4 py-2 rounded-lg bg-gray-800 hover:bg-gray-700 text-sm border border-white/10"
              :disabled="loading"
              @click="loadOperationLogs"
            >
              刷新记录
            </button>
            <select
              v-model.number="operationLogsPage.size"
              class="px-4 py-2 bg-gray-900/70 border border-white/10 rounded-xl text-sm"
              @change="handleOperationLogsPageSizeChange"
            >
              <option :value="10">每页 10 条</option>
              <option :value="20">每页 20 条</option>
              <option :value="50">每页 50 条</option>
              <option :value="100">每页 100 条</option>
            </select>
          </div>
        </div>

        <div class="text-[11px] text-gray-500">筛选用户来自当前已加载用户页；操作详情保留原始 JSON，便于后续继续扩展更多审计字段。</div>
        <ConfigurableTable
          :columns="operationTableColumns"
          :rows="operationLogs"
          :preferences="getTablePreference('operations')"
          :loading="loading"
          loading-text="正在加载操作记录..."
          empty-text="暂无操作记录。"
          @update:preferences="updateTablePreference('operations', $event)"
        >
          <template #cell-createdAt="{ row: log }">
            <div class="text-xs text-gray-300">{{ formatDate(log.createdAt) }}</div>
          </template>
          <template #cell-account="{ row: log }">
            <div>{{ log.nickname || log.username || log.operatorUsername || '未知账号' }}</div>
            <div class="text-xs text-gray-500">
              {{ log.userSlug ? `slug: ${log.userSlug}` : '未绑定 slug' }}
              <span v-if="log.ipAddress"> · {{ log.ipAddress }}</span>
            </div>
          </template>
          <template #cell-operationType="{ row: log }">
            <div class="text-xs text-gray-300">{{ operationTypeLabel(log.operationType) }}</div>
          </template>
          <template #cell-target="{ row: log }">
            <div class="text-xs text-gray-400">
              <div>{{ log.targetType || '—' }}</div>
              <div v-if="log.targetId != null" class="mt-1 text-gray-500">ID: {{ log.targetId }}</div>
            </div>
          </template>
          <template #cell-targetPath="{ row: log }">
            <div class="text-xs text-gray-400 break-all">{{ log.targetPath || '—' }}</div>
          </template>
          <template #cell-detailJson="{ row: log }">
            <div class="text-xs text-gray-400 break-all">{{ log.detailJson || '—' }}</div>
          </template>
        </ConfigurableTable>
        <div class="flex items-center justify-between pt-2 text-sm text-gray-300">
          <span>第 {{ operationLogsPage.page + 1 }} / {{ Math.max(operationLogsPage.totalPages, 1) }} 页，共 {{ operationLogsPage.totalElements }} 条记录</span>
          <div class="flex items-center gap-2">
            <button class="px-3 py-1 rounded bg-gray-800 border border-white/10 disabled:opacity-40" :disabled="operationLogsPage.first" @click="changeOperationLogsPage(operationLogsPage.page - 1)">上一页</button>
            <button class="px-3 py-1 rounded bg-gray-800 border border-white/10 disabled:opacity-40" :disabled="operationLogsPage.last" @click="changeOperationLogsPage(operationLogsPage.page + 1)">下一页</button>
          </div>
        </div>
      </section>

      <section v-if="activeTab === 'vip'" class="glass-panel p-6 space-y-5">
        <div>
          <h2 class="text-lg font-light">VIP 套餐</h2>
          <p class="text-xs text-gray-400">用于预置后续付费套餐；当前支持管理套餐名称、扩容空间、时长与价格，并可分配给用户。</p>
        </div>

        <div class="rounded-2xl border border-dashed border-white/15 p-5 space-y-4">
          <div class="text-sm text-gray-200">新增套餐</div>
          <div class="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-4 gap-3">
            <label class="block space-y-1">
              <span class="text-[11px] text-gray-400">套餐编码</span>
              <input v-model="newVipPlan.code" type="text" placeholder="例如 vip-30g-annual" class="px-3 py-2 bg-gray-900/70 border border-white/10 rounded-lg w-full" />
            </label>
            <label class="block space-y-1">
              <span class="text-[11px] text-gray-400">套餐名称</span>
              <input v-model="newVipPlan.name" type="text" placeholder="例如 年费 30GB" class="px-3 py-2 bg-gray-900/70 border border-white/10 rounded-lg w-full" />
            </label>
            <label class="block space-y-1">
              <span class="text-[11px] text-gray-400">额外空间（GB）</span>
              <input v-model.number="newVipPlan.extraQuotaGb" type="number" min="0" step="0.5" class="px-3 py-2 bg-gray-900/70 border border-white/10 rounded-lg w-full" />
            </label>
            <label class="block space-y-1">
              <span class="text-[11px] text-gray-400">时长（天）</span>
              <input v-model.number="newVipPlan.durationDays" type="number" min="1" class="px-3 py-2 bg-gray-900/70 border border-white/10 rounded-lg w-full" />
            </label>
            <label class="block space-y-1">
              <span class="text-[11px] text-gray-400">价格（元）</span>
              <input v-model.number="newVipPlan.priceYuan" type="number" min="0" step="0.01" class="px-3 py-2 bg-gray-900/70 border border-white/10 rounded-lg w-full" />
            </label>
            <label class="block space-y-1">
              <span class="text-[11px] text-gray-400">排序</span>
              <input v-model.number="newVipPlan.sortOrder" type="number" class="px-3 py-2 bg-gray-900/70 border border-white/10 rounded-lg w-full" />
            </label>
            <label class="flex items-center gap-2 px-3 py-2 bg-gray-900/50 border border-white/10 rounded-lg text-sm">
              <input v-model="newVipPlan.enabled" type="checkbox" class="w-4 h-4 rounded" />
              启用
            </label>
          </div>
          <label class="block space-y-1">
            <span class="text-[11px] text-gray-400">套餐说明</span>
            <textarea v-model="newVipPlan.description" rows="3" placeholder="描述套餐权益、适用时长、说明等" class="w-full px-3 py-2 bg-gray-900/70 border border-white/10 rounded-lg" />
          </label>
          <div class="flex justify-end">
            <button class="px-4 py-2 rounded-lg bg-purple-600 hover:bg-purple-500 disabled:opacity-60 text-sm" :disabled="savingVipPlanId === 0" @click="createVipPlan">
              {{ savingVipPlanId === 0 ? '创建中...' : '新增 VIP 套餐' }}
            </button>
          </div>
        </div>

        <ConfigurableTable
          :columns="vipPlanTableColumns"
          :rows="vipPlans"
          :preferences="getTablePreference('vipPlans')"
          :loading="loading"
          loading-text="正在加载 VIP 套餐..."
          empty-text="暂无 VIP 套餐。"
          @update:preferences="updateTablePreference('vipPlans', $event)"
        >
          <template #cell-code="{ row: plan }">
            <label class="block space-y-1">
              <span class="text-[11px] text-gray-400">套餐编码</span>
              <input v-model="plan.code" type="text" class="w-full px-3 py-2 bg-gray-900/70 border border-white/10 rounded-lg" />
            </label>
          </template>
          <template #cell-name="{ row: plan }">
            <div class="space-y-2">
              <label class="block space-y-1">
                <span class="text-[11px] text-gray-400">套餐名称</span>
                <input v-model="plan.name" type="text" class="w-full px-3 py-2 bg-gray-900/70 border border-white/10 rounded-lg" />
              </label>
              <div class="text-xs text-gray-500">创建于 {{ formatDate(plan.createdAt) }}</div>
            </div>
          </template>
          <template #cell-extraQuotaGb="{ row: plan }">
            <label class="block space-y-1">
              <span class="text-[11px] text-gray-400">额外空间（GB）</span>
              <input v-model.number="plan.extraQuotaGb" type="number" min="0" step="0.5" class="w-full px-3 py-2 bg-gray-900/70 border border-white/10 rounded-lg" />
            </label>
          </template>
          <template #cell-durationDays="{ row: plan }">
            <label class="block space-y-1">
              <span class="text-[11px] text-gray-400">时长（天）</span>
              <input v-model.number="plan.durationDays" type="number" min="1" class="w-full px-3 py-2 bg-gray-900/70 border border-white/10 rounded-lg" />
            </label>
          </template>
          <template #cell-priceYuan="{ row: plan }">
            <label class="block space-y-1">
              <span class="text-[11px] text-gray-400">价格（元）</span>
              <input v-model.number="plan.priceYuan" type="number" min="0" step="0.01" class="w-full px-3 py-2 bg-gray-900/70 border border-white/10 rounded-lg" />
            </label>
          </template>
          <template #cell-sortOrder="{ row: plan }">
            <label class="block space-y-1">
              <span class="text-[11px] text-gray-400">排序</span>
              <input v-model.number="plan.sortOrder" type="number" class="w-full px-3 py-2 bg-gray-900/70 border border-white/10 rounded-lg" />
            </label>
          </template>
          <template #cell-enabled="{ row: plan }">
            <label class="flex items-center gap-2 text-sm text-gray-300">
              <input v-model="plan.enabled" type="checkbox" class="w-4 h-4 rounded" />
              启用
            </label>
          </template>
          <template #cell-description="{ row: plan }">
            <label class="block space-y-1">
              <span class="text-[11px] text-gray-400">套餐说明</span>
              <textarea v-model="plan.description" rows="3" class="w-full px-3 py-2 bg-gray-900/70 border border-white/10 rounded-lg text-xs" />
            </label>
          </template>
          <template #cell-actions="{ row: plan }">
            <button class="w-full px-3 py-2 rounded-lg bg-purple-600 hover:bg-purple-500 disabled:opacity-60 text-sm" :disabled="savingVipPlanId === plan.id" @click="saveVipPlan(plan)">
              {{ savingVipPlanId === plan.id ? '保存中...' : '保存' }}
            </button>
          </template>
        </ConfigurableTable>
      </section>

      <section v-if="activeTab === 'vipOrders'" class="glass-panel p-6 space-y-5">
        <div class="flex items-center justify-between flex-wrap gap-3">
          <div>
            <h2 class="text-lg font-light">VIP 订单</h2>
            <p class="text-xs text-gray-400">当前先提供后台手工建单、状态维护和套餐生效联动，后续再接支付回调。</p>
          </div>
          <div class="flex items-center gap-3 flex-wrap">
            <select v-model="selectedVipOrderUserId" class="px-4 py-2 bg-gray-900/70 border border-white/10 rounded-xl text-sm" @change="handleVipOrderUserChange">
              <option :value="null">全部用户</option>
              <option v-for="user in users" :key="user.id" :value="user.id">{{ user.nickname || user.username }}</option>
            </select>
            <label class="flex items-center gap-2 px-3 py-2 rounded-xl border border-white/10 bg-gray-900/50 text-sm">
              <input v-model="vipOrderAutoRenewOnly" type="checkbox" class="w-4 h-4 rounded" @change="handleVipOrderFilterChange" />
              仅自动续费
            </label>
            <label class="flex items-center gap-2 px-3 py-2 rounded-xl border border-white/10 bg-gray-900/50 text-sm">
              <input v-model="vipOrderDueOnly" type="checkbox" class="w-4 h-4 rounded" @change="handleVipOrderFilterChange" />
              仅看待续费
            </label>
            <button class="px-4 py-2 rounded-lg bg-gray-800 hover:bg-gray-700 text-sm border border-white/10" :disabled="loading" @click="loadVipOrders">
              刷新订单
            </button>
            <button class="px-4 py-2 rounded-lg bg-gray-800 hover:bg-gray-700 text-sm border border-white/10" :disabled="loadingVipRenewalPreview" @click="loadVipRenewalPreview">
              {{ loadingVipRenewalPreview ? '预演中...' : '刷新续费预演' }}
            </button>
            <button class="px-4 py-2 rounded-lg bg-amber-600 hover:bg-amber-500 text-sm disabled:opacity-60" :disabled="executingVipRenewals" @click="executeVipRenewals">
              {{ executingVipRenewals ? '执行中...' : '执行续费建单' }}
            </button>
          </div>
        </div>

        <div
          v-if="focusedVipOrder"
          class="rounded-2xl border border-indigo-500/30 bg-indigo-500/10 p-4 text-sm text-gray-200 space-y-3"
        >
          <div class="flex flex-wrap items-start justify-between gap-3">
            <div class="space-y-1">
              <div class="text-base font-medium">当前定位订单：{{ focusedVipOrder.orderNo }}</div>
              <div class="text-xs text-gray-300">
                {{ focusedVipOrder.nickname || focusedVipOrder.username || `用户 #${focusedVipOrder.userId}` }}
                · {{ focusedVipOrder.vipPlanName || focusedVipOrder.vipPlanCode || '未知套餐' }}
                · {{ focusedVipOrder.orderStageLabel || focusedVipOrder.status }}
              </div>
              <div class="text-xs text-gray-400">
                {{ focusedVipOrder.renewalChainType === 'RENEWAL_CHILD' ? '续费子单' : '主订单' }}
                · 支付渠道：{{ paymentProviderLabel(focusedVipOrder.paymentProviderType) }}
                · 下次续费：{{ formatDate(focusedVipOrder.nextRenewalAt) }}
              </div>
            </div>
            <div class="flex flex-wrap gap-2">
              <router-link
                :to="buildPaymentResultRoute(focusedVipOrder)"
                class="px-3 py-2 rounded-lg bg-sky-600 hover:bg-sky-500 text-xs"
              >
                打开结果页
              </router-link>
              <button
                class="px-3 py-2 rounded-lg bg-gray-800 hover:bg-gray-700 text-xs border border-white/10"
                @click="clearFocusedVipOrder"
              >
                清除定位
              </button>
            </div>
          </div>
          <div class="text-xs text-gray-400">
            已自动切换到该订单所属用户并刷新订单列表，便于继续在本页编辑、发起、Mock、取消或退款。
          </div>
        </div>

        <div class="rounded-2xl border border-amber-500/20 bg-amber-500/5 p-5 space-y-4">
          <div class="flex items-start justify-between gap-3">
            <div>
              <div class="text-sm text-amber-100">自动续费预演</div>
              <div class="text-xs text-gray-400">{{ vipRenewalPreview?.message || '当前仅做干跑预演，不会真实扣款，但会评估子单支付是否可继续发起。' }}</div>
            </div>
            <div class="text-xs text-gray-400">
              {{ vipRenewalPreview?.generatedAt ? `生成时间：${formatDate(vipRenewalPreview.generatedAt)}` : '尚未生成' }}
            </div>
          </div>
          <div class="grid grid-cols-1 md:grid-cols-3 gap-3 text-sm">
            <div class="rounded-xl border border-white/10 bg-white/5 p-4">
              <div class="text-gray-400 text-xs">开启自动续费订单</div>
              <div class="mt-2 text-xl">{{ vipRenewalPreview?.activeAutoRenewOrderCount ?? 0 }}</div>
            </div>
            <div class="rounded-xl border border-white/10 bg-white/5 p-4">
              <div class="text-gray-400 text-xs">待续费候选</div>
              <div class="mt-2 text-xl">{{ vipRenewalPreview?.dueCount ?? 0 }}</div>
            </div>
            <div class="rounded-xl border border-white/10 bg-white/5 p-4">
              <div class="text-gray-400 text-xs">本次返回</div>
              <div class="mt-2 text-xl">{{ vipRenewalPreview?.returnedCount ?? 0 }}</div>
            </div>
          </div>
          <div v-if="vipRenewalPreview?.content?.length" class="grid grid-cols-1 xl:grid-cols-2 gap-3">
            <article v-for="candidate in vipRenewalPreview.content" :key="candidate.orderId" class="rounded-xl border border-white/10 bg-black/20 p-4 space-y-2 text-sm">
              <div class="flex items-start justify-between gap-3">
                <div>
                  <div>{{ candidate.orderNo }}</div>
                  <div class="text-xs text-gray-400">{{ candidate.nickname || candidate.username || `用户 ${candidate.userId}` }} · {{ candidate.vipPlanName || candidate.vipPlanCode || `套餐 ${candidate.vipPlanId}` }}</div>
                </div>
                <span class="text-xs px-2 py-1 rounded-full border border-amber-400/30 text-amber-100">{{ candidate.hoursOverdue }}h</span>
              </div>
              <div class="grid grid-cols-2 gap-2 text-xs text-gray-400">
                <div>状态：{{ candidate.status }}</div>
                <div>金额：¥{{ Number(candidate.amountYuan || 0).toFixed(2) }}</div>
                <div>下次续费：{{ formatDate(candidate.nextRenewalAt) }}</div>
                <div>动作：{{ candidate.renewalAction }}</div>
                <div>支付平台：{{ candidate.paymentProviderLabel || '-' }}</div>
                <div>支付就绪：{{ candidate.paymentEnabled ? (candidate.paymentMockMode ? 'Mock 可发起' : (candidate.paymentLiveModeReady ? 'Live Ready' : '配置未就绪')) : '未启用' }}</div>
              </div>
              <div
                class="text-xs"
                :class="candidate.renewalBlocked ? 'text-amber-200' : 'text-emerald-200'"
              >
                {{ candidate.renewalMessage || (candidate.renewalBlocked ? '当前被阻塞' : '可创建续费订单') }}
              </div>
              <div v-if="candidate.paymentMissingFields?.length" class="text-xs text-amber-200">
                缺失字段：{{ candidate.paymentMissingFields.join('、') }}
              </div>
              <div v-if="candidate.paymentReadinessWarnings?.length" class="text-xs text-gray-400">
                告警：{{ candidate.paymentReadinessWarnings.join('；') }}
              </div>
              <div v-if="candidate.existingRenewalOrderNo" class="text-xs text-gray-400">
                已有关联续费单：{{ candidate.existingRenewalOrderNo }}
                <span v-if="candidate.existingRenewalOrderStatus"> · {{ candidate.existingRenewalOrderStatus }}</span>
                <span v-if="candidate.existingRenewalOrderCreatedAt"> · {{ formatDate(candidate.existingRenewalOrderCreatedAt) }}</span>
              </div>
            </article>
          </div>
          <div v-else class="text-sm text-gray-400">当前没有待续费订单候选。</div>
        </div>

        <div v-if="vipRenewalExecution" class="rounded-2xl border border-emerald-500/20 bg-emerald-500/5 p-5 space-y-4">
          <div class="flex items-start justify-between gap-3">
            <div>
              <div class="text-sm text-emerald-100">最近一次续费建单结果</div>
              <div class="text-xs text-gray-400">{{ vipRenewalExecution.message }}</div>
            </div>
            <div class="text-xs text-gray-400">{{ formatDate(vipRenewalExecution.executedAt) }}</div>
          </div>
          <div class="grid grid-cols-1 md:grid-cols-3 gap-3 text-sm">
            <div class="rounded-xl border border-white/10 bg-white/5 p-4">
              <div class="text-gray-400 text-xs">候选数</div>
              <div class="mt-2 text-xl">{{ vipRenewalExecution.candidateCount }}</div>
            </div>
            <div class="rounded-xl border border-white/10 bg-white/5 p-4">
              <div class="text-gray-400 text-xs">新建续费订单</div>
              <div class="mt-2 text-xl">{{ vipRenewalExecution.createdCount }}</div>
            </div>
            <div class="rounded-xl border border-white/10 bg-white/5 p-4">
              <div class="text-gray-400 text-xs">跳过</div>
              <div class="mt-2 text-xl">{{ vipRenewalExecution.skippedCount }}</div>
            </div>
          </div>
          <div v-if="vipRenewalExecution.createdOrders.length" class="space-y-2">
            <div class="text-sm text-gray-300">新建订单</div>
            <div class="grid grid-cols-1 xl:grid-cols-2 gap-3">
              <article v-for="item in vipRenewalExecution.createdOrders" :key="`${item.sourceOrderId}-${item.createdOrderId}`" class="rounded-xl border border-white/10 bg-black/20 p-4 text-sm space-y-1">
                <div>{{ item.sourceOrderNo }} → {{ item.createdOrderNo }}</div>
                <div class="text-xs text-gray-400">{{ item.nickname || item.username || `用户 ${item.userId}` }} · {{ item.vipPlanName || `套餐 ${item.vipPlanId}` }}</div>
                <div class="text-xs text-emerald-200">{{ item.reason }}</div>
                <div class="text-xs text-gray-400">
                  支付平台：{{ item.paymentProviderLabel || '-' }}
                  <span v-if="item.initiationAttempted"> · 发起结果：{{ item.initiationSuccess ? '成功' : '失败' }}</span>
                  <span v-else> · 发起结果：未尝试</span>
                </div>
                <div v-if="item.initiationMessage" class="text-xs text-gray-400">{{ item.initiationMessage }}</div>
                <div v-if="item.paymentMissingFields?.length" class="text-xs text-amber-200">
                  缺失字段：{{ item.paymentMissingFields.join('、') }}
                </div>
                <div v-if="item.paymentReadinessWarnings?.length" class="text-xs text-gray-400">
                  告警：{{ item.paymentReadinessWarnings.join('；') }}
                </div>
              </article>
            </div>
          </div>
          <div v-if="vipRenewalExecution.skippedOrders.length" class="space-y-2">
            <div class="text-sm text-gray-300">跳过订单</div>
            <div class="grid grid-cols-1 xl:grid-cols-2 gap-3">
              <article v-for="item in vipRenewalExecution.skippedOrders" :key="`${item.sourceOrderId}-${item.existingRenewalOrderId || 'skip'}`" class="rounded-xl border border-white/10 bg-black/20 p-4 text-sm space-y-1">
                <div>{{ item.sourceOrderNo }}</div>
                <div v-if="item.existingRenewalOrderNo" class="text-xs text-gray-400">
                  已有关联续费单：{{ item.existingRenewalOrderNo }}
                  <span v-if="item.existingRenewalOrderStatus"> · {{ item.existingRenewalOrderStatus }}</span>
                </div>
                <div class="text-xs text-amber-200">{{ item.reason }}</div>
              </article>
            </div>
          </div>
        </div>

        <div class="rounded-2xl border border-dashed border-white/15 p-5 space-y-4">
          <div class="text-sm text-gray-200">新增手工订单</div>
          <div class="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-4 gap-3">
            <label class="block space-y-1">
              <span class="text-[11px] text-gray-400">用户</span>
              <select v-model="newVipOrder.userId" class="px-3 py-2 bg-gray-900/70 border border-white/10 rounded-lg w-full">
                <option :value="undefined">选择用户</option>
                <option v-for="user in users" :key="user.id" :value="user.id">{{ user.nickname || user.username }}</option>
              </select>
            </label>
            <label class="block space-y-1">
              <span class="text-[11px] text-gray-400">套餐</span>
              <select v-model="newVipOrder.vipPlanId" class="px-3 py-2 bg-gray-900/70 border border-white/10 rounded-lg w-full">
                <option :value="undefined">选择套餐</option>
                <option v-for="plan in vipPlans" :key="plan.id" :value="plan.id">{{ plan.name }}</option>
              </select>
            </label>
            <label class="block space-y-1">
              <span class="text-[11px] text-gray-400">金额（元）</span>
              <input v-model.number="newVipOrder.amountYuan" type="number" min="0" step="0.01" class="px-3 py-2 bg-gray-900/70 border border-white/10 rounded-lg w-full" />
            </label>
            <label class="block space-y-1">
              <span class="text-[11px] text-gray-400">订单状态</span>
              <select v-model="newVipOrder.status" class="px-3 py-2 bg-gray-900/70 border border-white/10 rounded-lg w-full">
                <option value="CREATED">CREATED</option>
                <option value="PAID">PAID</option>
                <option value="ACTIVE">ACTIVE</option>
                <option value="CANCELLED">CANCELLED</option>
              </select>
            </label>
            <label class="block space-y-1">
              <span class="text-[11px] text-gray-400">支付时间</span>
              <input v-model="newVipOrder.paidAt" type="datetime-local" class="px-3 py-2 bg-gray-900/70 border border-white/10 rounded-lg w-full" />
            </label>
            <label class="block space-y-1">
              <span class="text-[11px] text-gray-400">下次续费时间</span>
              <input v-model="newVipOrder.nextRenewalAt" type="datetime-local" class="px-3 py-2 bg-gray-900/70 border border-white/10 rounded-lg w-full" />
            </label>
            <label class="block space-y-1">
              <span class="text-[11px] text-gray-400">到期时间</span>
              <input v-model="newVipOrder.expireAt" type="datetime-local" class="px-3 py-2 bg-gray-900/70 border border-white/10 rounded-lg w-full" />
            </label>
            <label class="block space-y-1">
              <span class="text-[11px] text-gray-400">订单来源</span>
              <input v-model="newVipOrder.source" type="text" class="px-3 py-2 bg-gray-900/70 border border-white/10 rounded-lg w-full" />
            </label>
            <label class="flex items-center gap-2 px-3 py-2 bg-gray-900/50 border border-white/10 rounded-lg text-sm">
              <input v-model="newVipOrder.autoRenewEnabled" type="checkbox" class="w-4 h-4 rounded" />
              自动续费
            </label>
          </div>
          <label class="block space-y-1">
            <span class="text-[11px] text-gray-400">备注</span>
            <textarea v-model="newVipOrder.remark" rows="2" placeholder="补充人工建单说明、来源、测试信息等" class="w-full px-3 py-2 bg-gray-900/70 border border-white/10 rounded-lg" />
          </label>
          <div class="flex justify-end">
            <button class="px-4 py-2 rounded-lg bg-purple-600 hover:bg-purple-500 disabled:opacity-60 text-sm" :disabled="savingVipOrderId === 0" @click="createVipOrder">
              {{ savingVipOrderId === 0 ? '创建中...' : '新增订单' }}
            </button>
          </div>
        </div>

        <ConfigurableTable
          :columns="vipOrderTableColumns"
          :rows="vipOrders"
          :preferences="getTablePreference('vipOrders')"
          :loading="loading"
          loading-text="正在加载 VIP 订单..."
          empty-text="暂无 VIP 订单。"
          @update:preferences="updateTablePreference('vipOrders', $event)"
        >
          <template #cell-order="{ row: order }">
            <div class="space-y-1">
              <div class="font-medium">{{ order.orderNo }}</div>
              <div class="text-xs text-gray-400">{{ order.nickname || order.username || '未知用户' }}</div>
              <div class="text-xs text-gray-500">{{ order.vipPlanName || order.vipPlanCode || '未知套餐' }}</div>
            </div>
          </template>
          <template #cell-amountYuan="{ row: order }">
            <input v-model.number="order.amountYuan" type="number" min="0" step="0.01" class="w-full px-3 py-2 bg-gray-900/70 border border-white/10 rounded-lg" />
          </template>
          <template #cell-status="{ row: order }">
            <select v-model="order.status" class="w-full px-3 py-2 bg-gray-900/70 border border-white/10 rounded-lg">
              <option value="CREATED">CREATED</option>
              <option value="PAID">PAID</option>
              <option value="ACTIVE">ACTIVE</option>
              <option value="CANCELLED">CANCELLED</option>
            </select>
          </template>
          <template #cell-source="{ row: order }">
            <input v-model="order.source" type="text" class="w-full px-3 py-2 bg-gray-900/70 border border-white/10 rounded-lg" />
          </template>
          <template #cell-autoRenewEnabled="{ row: order }">
            <label class="flex items-center gap-2 text-sm text-gray-300">
              <input v-model="order.autoRenewEnabled" type="checkbox" class="w-4 h-4 rounded" />
              自动续费
            </label>
          </template>
          <template #cell-timeline="{ row: order }">
            <div class="space-y-2">
              <input v-model="order.paidAt" type="datetime-local" class="w-full px-3 py-2 bg-gray-900/70 border border-white/10 rounded-lg text-xs" />
              <input v-model="order.nextRenewalAt" type="datetime-local" class="w-full px-3 py-2 bg-gray-900/70 border border-white/10 rounded-lg text-xs" />
              <input v-model="order.expireAt" type="datetime-local" class="w-full px-3 py-2 bg-gray-900/70 border border-white/10 rounded-lg text-xs" />
            </div>
          </template>
          <template #cell-payment="{ row: order }">
            <div class="space-y-1 text-xs text-gray-400">
              <div>渠道：{{ paymentProviderLabel(order.paymentProviderType) }}</div>
              <div>网关：{{ order.gatewayStatus || order.status || '-' }}</div>
              <div>外部单号：{{ order.externalTradeNo || '-' }}</div>
              <div>回调：{{ formatDate(order.paymentNotifiedAt) }}</div>
            </div>
          </template>
          <template #cell-renewal="{ row: order }">
            <div class="space-y-1 text-xs text-gray-400">
              <div>下次续费：{{ formatDate(order.nextRenewalAt) }}</div>
              <div>
                来源单：
                <router-link
                  v-if="order.renewalSourceOrderNo"
                  :to="buildSuperAdminVipOrderRoute(order.renewalSourceOrderNo)"
                  class="text-indigo-400 hover:text-indigo-300"
                >
                  {{ order.renewalSourceOrderNo }}
                </router-link>
                <span v-else>{{ order.renewalSourceOrderId || '-' }}</span>
              </div>
              <div>
                子单：
                <router-link
                  v-if="order.renewalChildOrderNo"
                  :to="buildSuperAdminVipOrderRoute(order.renewalChildOrderNo)"
                  class="text-indigo-400 hover:text-indigo-300"
                >
                  {{ order.renewalChildOrderNo }}
                </router-link>
                <span v-else>-</span>
              </div>
              <div>待续费：{{ order.dueForRenewal ? '是' : '否' }}</div>
              <div>链路：{{ order.renewalChainType || '-' }}</div>
            </div>
          </template>
          <template #cell-remark="{ row: order }">
            <textarea v-model="order.remark" rows="3" class="w-full px-3 py-2 bg-gray-900/70 border border-white/10 rounded-lg text-xs" />
          </template>
          <template #cell-actions="{ row: order }">
            <div class="grid grid-cols-2 gap-2">
              <button class="px-2 py-2 rounded-lg bg-gray-800 hover:bg-gray-700 disabled:opacity-60 text-xs border border-white/10" :disabled="previewingVipOrderId === order.id" @click="previewVipOrderPayment(order)">
                {{ previewingVipOrderId === order.id ? '生成中' : '预览' }}
              </button>
              <button class="px-2 py-2 rounded-lg bg-indigo-600 hover:bg-indigo-500 disabled:opacity-60 text-xs" :disabled="initiatingVipOrderId === order.id" @click="initiateVipOrderPayment(order)">
                {{ initiatingVipOrderId === order.id ? '发起中' : '发起' }}
              </button>
              <button class="px-2 py-2 rounded-lg bg-emerald-600 hover:bg-emerald-500 disabled:opacity-60 text-xs" :disabled="mockingVipOrderId === order.id" @click="mockPayVipOrder(order)">
                {{ mockingVipOrderId === order.id ? '处理中' : 'Mock' }}
              </button>
              <button class="px-2 py-2 rounded-lg bg-amber-600 hover:bg-amber-500 disabled:opacity-60 text-xs" :disabled="cancellingVipOrderId === order.id" @click="cancelVipOrder(order)">
                {{ cancellingVipOrderId === order.id ? '处理中' : '取消' }}
              </button>
              <button class="px-2 py-2 rounded-lg bg-fuchsia-600 hover:bg-fuchsia-500 text-xs border border-white/10" @click="previewVipOrderRefund(order)">
                退款预览
              </button>
              <button class="px-2 py-2 rounded-lg bg-rose-600 hover:bg-rose-500 disabled:opacity-60 text-xs" :disabled="refundingVipOrderId === order.id" @click="refundVipOrder(order)">
                {{ refundingVipOrderId === order.id ? '处理中' : '退款' }}
              </button>
              <button class="px-2 py-2 rounded-lg bg-cyan-600 hover:bg-cyan-500 disabled:opacity-60 text-xs" :disabled="confirmingVipOrderRefundId === order.id || order.refundStatus !== 'REQUESTED'" @click="confirmVipOrderRefund(order)">
                {{ confirmingVipOrderRefundId === order.id ? '处理中' : '确认退款' }}
              </button>
              <button class="px-2 py-2 rounded-lg bg-orange-600 hover:bg-orange-500 disabled:opacity-60 text-xs" :disabled="failingVipOrderRefundId === order.id || order.refundStatus !== 'REQUESTED'" @click="markVipOrderRefundFailed(order)">
                {{ failingVipOrderRefundId === order.id ? '处理中' : '退款失败' }}
              </button>
              <button class="px-2 py-2 rounded-lg bg-purple-600 hover:bg-purple-500 disabled:opacity-60 text-xs" :disabled="savingVipOrderId === order.id" @click="saveVipOrder(order)">
                {{ savingVipOrderId === order.id ? '保存中' : '保存' }}
              </button>
              <router-link
                :to="buildSuperAdminVipOrderRoute(order.orderNo)"
                class="px-2 py-2 rounded-lg bg-gray-800 hover:bg-gray-700 text-xs text-center border border-white/10"
              >
                定位
              </router-link>
              <router-link
                :to="buildPaymentResultRoute(order)"
                class="px-2 py-2 rounded-lg bg-sky-600 hover:bg-sky-500 text-xs text-center"
              >
                结果页
              </router-link>
            </div>
          </template>
        </ConfigurableTable>
        <div v-if="paymentPreview" class="rounded-2xl border border-white/10 bg-black/20 p-5 space-y-4">
          <div class="flex items-start justify-between gap-3">
            <div>
              <div class="text-lg">支付预览 · {{ paymentPreview.orderNo }}</div>
              <div class="text-xs text-gray-400">{{ paymentPreview.providerLabel }} · {{ paymentPreview.liveModeReady ? '真实支付配置完整' : '仍需补配置' }}</div>
            </div>
            <button class="px-3 py-2 rounded-lg bg-gray-800 hover:bg-gray-700 text-sm border border-white/10" @click="paymentPreview = null">
              关闭
            </button>
          </div>
          <div class="grid grid-cols-1 xl:grid-cols-2 gap-4 text-sm">
            <div class="rounded-xl border border-white/10 bg-white/5 p-4 space-y-2">
              <div>支付平台：{{ paymentPreview.providerLabel }}</div>
              <div>接口地址：{{ paymentPreview.apiBaseUrl }}</div>
              <div>币种：{{ paymentPreview.currency }}</div>
              <div>状态说明：{{ paymentPreview.supportMessage }}</div>
              <div>建议拉起：{{ paymentPreview.initiationMode || '—' }}</div>
              <div>建议验签：{{ paymentPreview.verificationMode || '—' }}</div>
              <div>建议退款：{{ paymentPreview.refundMode || '—' }}</div>
              <div v-if="paymentPreview.missingFields.length">缺失字段：{{ paymentPreview.missingFields.join('、') }}</div>
              <div>下单签名就绪：{{ paymentPreview.signatureReady ? '是' : '否' }}</div>
              <div>回调验签就绪：{{ paymentPreview.callbackVerificationReady ? '是' : '否' }}</div>
              <div>退款就绪：{{ paymentPreview.refundReady ? '是' : '否' }}</div>
              <div v-if="paymentPreview.readinessWarnings?.length" class="text-amber-300">接入告警：{{ paymentPreview.readinessWarnings.join('；') }}</div>
              <div v-if="paymentPreview.capabilityTags?.length">能力：{{ paymentPreview.capabilityTags.join('、') }}</div>
            </div>
            <div class="rounded-xl border border-white/10 bg-white/5 p-4 space-y-2">
              <div>订单：{{ paymentPreview.orderNo }}</div>
              <div>用户：{{ paymentPreview.username || paymentPreview.userId }}</div>
              <div>套餐：{{ paymentPreview.vipPlanName || paymentPreview.vipPlanId }}</div>
              <div>已启用：{{ paymentPreview.enabled ? '是' : '否' }}</div>
              <div>Mock 模式：{{ paymentPreview.mockEnabled ? '开启' : '关闭' }}</div>
            </div>
          </div>
          <div class="grid grid-cols-1 xl:grid-cols-2 gap-4">
            <div class="space-y-2">
              <div class="text-sm text-gray-300">请求载荷</div>
              <pre class="rounded-xl border border-white/10 bg-gray-950/70 p-4 text-xs text-gray-200 overflow-auto">{{ JSON.stringify(paymentPreview.requestPayload, null, 2) }}</pre>
            </div>
            <div class="space-y-2">
              <div class="text-sm text-gray-300">回调样例</div>
              <pre class="rounded-xl border border-white/10 bg-gray-950/70 p-4 text-xs text-gray-200 overflow-auto">{{ JSON.stringify(paymentPreview.callbackPayload, null, 2) }}</pre>
            </div>
          </div>
          <div v-if="paymentPreview.integrationSteps?.length" class="rounded-xl border border-white/10 bg-white/5 p-4 space-y-2 text-xs text-gray-300">
            <div class="text-sm text-white">真实接入步骤</div>
            <div v-for="(step, index) in paymentPreview.integrationSteps" :key="`${paymentPreview.providerType}-step-${index}`">
              {{ index + 1 }}. {{ step }}
            </div>
          </div>
          <div v-if="paymentPreview.recommendedConfigFields?.length || paymentPreview.nextActionHints?.length" class="grid grid-cols-1 xl:grid-cols-2 gap-4">
            <div v-if="paymentPreview.recommendedConfigFields?.length" class="rounded-xl border border-white/10 bg-white/5 p-4 space-y-2 text-xs text-gray-300">
              <div class="text-sm text-white">建议重点配置</div>
              <div v-for="field in paymentPreview.recommendedConfigFields" :key="field">
                {{ field }}
              </div>
            </div>
            <div v-if="paymentPreview.nextActionHints?.length" class="rounded-xl border border-white/10 bg-white/5 p-4 space-y-2 text-xs text-gray-300">
              <div class="text-sm text-white">建议下一步</div>
              <div v-for="(hint, index) in paymentPreview.nextActionHints" :key="`${paymentPreview.providerType}-hint-${index}`">
                {{ index + 1 }}. {{ hint }}
              </div>
            </div>
          </div>
          <div v-if="paymentPreview.stageReadiness?.length" class="rounded-xl border border-white/10 bg-white/5 p-4 space-y-3 text-xs text-gray-300">
            <div class="text-sm text-white">阶段检查</div>
            <div
              v-for="stage in paymentPreview.stageReadiness"
              :key="`${paymentPreview.providerType}-${stage.stageKey}`"
              class="rounded-lg border border-white/10 bg-black/20 p-3 space-y-2"
            >
              <div class="flex items-center justify-between gap-3">
                <div class="text-sm text-white">{{ stage.stageLabel }}</div>
                <span :class="stage.ready ? 'text-emerald-300' : 'text-amber-300'">
                  {{ stage.ready ? '已就绪' : '待补齐' }}
                </span>
              </div>
              <div v-for="check in stage.checks || []" :key="`${stage.stageKey}-${check.label}`" class="flex items-start justify-between gap-4">
                <div>{{ check.label }}</div>
                <div :class="check.passed ? 'text-emerald-300' : 'text-amber-300'">
                  {{ check.passed ? '通过' : (check.failureReason || '未通过') }}
                </div>
              </div>
            </div>
          </div>
        </div>
        <div v-if="paymentInitiation" class="rounded-2xl border border-indigo-500/30 bg-indigo-500/5 p-5 space-y-4">
          <div class="flex items-start justify-between gap-3">
            <div>
              <div class="text-lg">支付发起骨架 · {{ paymentInitiation.orderNo }}</div>
              <div class="text-xs text-gray-400">{{ paymentInitiation.providerLabel }} · {{ paymentInitiation.liveModeReady ? '可进入真实对接' : '仍需补配置' }}</div>
            </div>
            <button class="px-3 py-2 rounded-lg bg-gray-800 hover:bg-gray-700 text-sm border border-white/10" @click="paymentInitiation = null">
              关闭
            </button>
          </div>
          <div class="grid grid-cols-1 xl:grid-cols-2 gap-4 text-sm">
            <div class="rounded-xl border border-white/10 bg-white/5 p-4 space-y-2">
              <div>发起地址：{{ paymentInitiation.launchUrl }}</div>
              <div>请求方式：{{ paymentInitiation.httpMethod }}</div>
              <div>拉起类型：{{ paymentInitiation.actionType || 'API_REQUEST' }}</div>
              <div>跳转模式：{{ paymentInitiation.redirect ? '页面跳转' : '服务端/API' }}</div>
              <div>状态说明：{{ paymentInitiation.message }}</div>
            </div>
            <pre class="rounded-xl border border-white/10 bg-gray-950/70 p-4 text-xs text-gray-200 overflow-auto">{{ JSON.stringify(paymentInitiation.payload, null, 2) }}</pre>
          </div>
          <div v-if="paymentInitiation.formFields || paymentInitiation.headers || paymentInitiation.qrCodeText || paymentInitiation.payload?.requestBodyJson || paymentInitiation.payload?.requestBodyEncoded" class="grid grid-cols-1 xl:grid-cols-4 gap-4">
            <div v-if="paymentInitiation.formFields" class="space-y-2">
              <div class="text-sm text-gray-300">表单字段</div>
              <pre class="rounded-xl border border-white/10 bg-gray-950/70 p-4 text-xs text-gray-200 overflow-auto">{{ JSON.stringify(paymentInitiation.formFields, null, 2) }}</pre>
            </div>
            <div v-if="paymentInitiation.headers" class="space-y-2">
              <div class="text-sm text-gray-300">请求头</div>
              <pre class="rounded-xl border border-white/10 bg-gray-950/70 p-4 text-xs text-gray-200 overflow-auto">{{ JSON.stringify(paymentInitiation.headers, null, 2) }}</pre>
            </div>
            <div v-if="paymentInitiation.qrCodeText" class="space-y-2">
              <div class="text-sm text-gray-300">二维码 / 拉起串</div>
              <pre class="rounded-xl border border-white/10 bg-gray-950/70 p-4 text-xs text-gray-200 overflow-auto">{{ paymentInitiation.qrCodeText }}</pre>
            </div>
            <div v-if="paymentInitiation.payload?.requestBodyJson" class="space-y-2">
              <div class="text-sm text-gray-300">请求体 JSON</div>
              <pre class="rounded-xl border border-white/10 bg-gray-950/70 p-4 text-xs text-gray-200 overflow-auto">{{ paymentInitiation.payload.requestBodyJson }}</pre>
            </div>
            <div v-if="paymentInitiation.payload?.requestBodyEncoded" class="space-y-2">
              <div class="text-sm text-gray-300">请求体表单编码</div>
              <pre class="rounded-xl border border-white/10 bg-gray-950/70 p-4 text-xs text-gray-200 overflow-auto whitespace-pre-wrap break-all">{{ paymentInitiation.payload.requestBodyEncoded }}</pre>
            </div>
          </div>
        </div>
        <div v-if="paymentRefundPreview" class="rounded-2xl border border-rose-500/30 bg-rose-500/5 p-5 space-y-4">
          <div class="flex items-start justify-between gap-3">
            <div>
              <div class="text-lg">退款骨架预览 · {{ paymentRefundPreview.orderNo }}</div>
              <div class="text-xs text-gray-400">{{ paymentRefundPreview.providerLabel }} · {{ paymentRefundPreview.liveModeReady ? '可进入真实退款联调' : '仍需补配置' }}</div>
            </div>
            <button class="px-3 py-2 rounded-lg bg-gray-800 hover:bg-gray-700 text-sm border border-white/10" @click="paymentRefundPreview = null">
              关闭
            </button>
          </div>
          <div class="grid grid-cols-1 xl:grid-cols-2 gap-4 text-sm">
            <div class="rounded-xl border border-white/10 bg-white/5 p-4 space-y-2">
              <div>退款地址：{{ paymentRefundPreview.launchUrl }}</div>
              <div>请求方式：{{ paymentRefundPreview.httpMethod }}</div>
              <div>退款模式：{{ paymentRefundPreview.refundMode || '—' }}</div>
              <div>建议验签：{{ paymentRefundPreview.verificationMode || '—' }}</div>
              <div>退款就绪：{{ paymentRefundPreview.refundReady ? '是' : '否' }}</div>
              <div>退款金额：¥{{ paymentRefundPreview.refundAmountYuan }}</div>
              <div>状态说明：{{ paymentRefundPreview.message }}</div>
              <div v-if="paymentRefundPreview.supportMessage">接入提示：{{ paymentRefundPreview.supportMessage }}</div>
              <div v-if="paymentRefundPreview.capabilityTags?.length">平台能力：{{ paymentRefundPreview.capabilityTags.join('、') }}</div>
            </div>
            <pre class="rounded-xl border border-white/10 bg-gray-950/70 p-4 text-xs text-gray-200 overflow-auto">{{ JSON.stringify(paymentRefundPreview.payload, null, 2) }}</pre>
          </div>
          <div v-if="paymentRefundPreview.missingFields?.length || paymentRefundPreview.readinessWarnings?.length || paymentRefundPreview.nextActionHints?.length" class="grid grid-cols-1 xl:grid-cols-3 gap-4 text-xs">
            <div v-if="paymentRefundPreview.missingFields?.length" class="rounded-xl border border-amber-400/20 bg-amber-500/5 p-4 space-y-2 text-amber-100">
              <div class="text-sm text-white">缺失字段</div>
              <div>{{ paymentRefundPreview.missingFields.join('、') }}</div>
            </div>
            <div v-if="paymentRefundPreview.readinessWarnings?.length" class="rounded-xl border border-rose-400/20 bg-rose-500/5 p-4 space-y-2 text-rose-100">
              <div class="text-sm text-white">接入风险</div>
              <div v-for="(item, index) in paymentRefundPreview.readinessWarnings" :key="`refund-warning-${index}`">{{ item }}</div>
            </div>
            <div v-if="paymentRefundPreview.nextActionHints?.length" class="rounded-xl border border-sky-400/20 bg-sky-500/5 p-4 space-y-2 text-sky-100">
              <div class="text-sm text-white">下一步建议</div>
              <div v-for="(item, index) in paymentRefundPreview.nextActionHints" :key="`refund-next-${index}`">{{ index + 1 }}. {{ item }}</div>
            </div>
          </div>
          <div v-if="paymentRefundPreview.stageReadiness?.length" class="rounded-xl border border-white/10 bg-white/5 p-4 space-y-3 text-xs text-gray-300">
            <div class="text-sm text-white">退款阶段检查</div>
            <div v-for="stage in paymentRefundPreview.stageReadiness" :key="`refund-stage-${stage.stageKey}`" class="rounded-lg border border-white/10 bg-black/20 p-3 space-y-2">
              <div class="flex items-center justify-between gap-2">
                <span>{{ stage.stageLabel }}</span>
                <span :class="stage.ready ? 'text-emerald-300' : 'text-amber-300'">{{ stage.ready ? '已就绪' : '未就绪' }}</span>
              </div>
              <div v-for="(check, index) in stage.checks || []" :key="`refund-check-${stage.stageKey}-${index}`" class="flex items-start justify-between gap-3">
                <span>{{ check.label }}</span>
                <span :class="check.passed ? 'text-emerald-300' : 'text-rose-300'">{{ check.passed ? '通过' : (check.failureReason || '未通过') }}</span>
              </div>
            </div>
          </div>
          <div v-if="paymentRefundPreview.headers" class="space-y-2">
            <div class="text-sm text-gray-300">请求头</div>
            <pre class="rounded-xl border border-white/10 bg-gray-950/70 p-4 text-xs text-gray-200 overflow-auto">{{ JSON.stringify(paymentRefundPreview.headers, null, 2) }}</pre>
          </div>
          <div v-if="paymentRefundPreview.payload?.requestBodyJson || paymentRefundPreview.payload?.requestBodyEncoded" class="grid grid-cols-1 xl:grid-cols-2 gap-4">
            <div v-if="paymentRefundPreview.payload?.requestBodyJson" class="space-y-2">
              <div class="text-sm text-gray-300">JSON 请求体</div>
              <pre class="rounded-xl border border-white/10 bg-gray-950/70 p-4 text-xs text-gray-200 overflow-auto">{{ paymentRefundPreview.payload.requestBodyJson }}</pre>
            </div>
            <div v-if="paymentRefundPreview.payload?.requestBodyEncoded" class="space-y-2">
              <div class="text-sm text-gray-300">Form 请求体</div>
              <pre class="rounded-xl border border-white/10 bg-gray-950/70 p-4 text-xs text-gray-200 overflow-auto whitespace-pre-wrap break-all">{{ paymentRefundPreview.payload.requestBodyEncoded }}</pre>
            </div>
          </div>
          <div v-if="paymentRefundPreview.integrationSteps?.length" class="rounded-xl border border-white/10 bg-white/5 p-4 space-y-2 text-xs text-gray-300">
            <div class="text-sm text-white">真实退款接入步骤</div>
            <div v-for="(step, index) in paymentRefundPreview.integrationSteps" :key="`${paymentRefundPreview.providerType}-refund-step-${index}`">
              {{ index + 1 }}. {{ step }}
            </div>
          </div>
        </div>
        <div class="flex items-center justify-between pt-2 text-sm text-gray-300">
          <span>第 {{ vipOrdersPage.page + 1 }} / {{ Math.max(vipOrdersPage.totalPages, 1) }} 页，共 {{ vipOrdersPage.totalElements }} 条订单</span>
          <div class="flex items-center gap-2">
            <button class="px-3 py-1 rounded bg-gray-800 border border-white/10 disabled:opacity-40" :disabled="vipOrdersPage.first" @click="changeVipOrdersPage(vipOrdersPage.page - 1)">上一页</button>
            <button class="px-3 py-1 rounded bg-gray-800 border border-white/10 disabled:opacity-40" :disabled="vipOrdersPage.last" @click="changeVipOrdersPage(vipOrdersPage.page + 1)">下一页</button>
          </div>
        </div>
      </section>

      <section v-if="activeTab === 'storage'" class="glass-panel p-6 space-y-5">
        <div>
          <h2 class="text-lg font-light">存储提供者</h2>
          <p class="text-xs text-gray-400">LOCAL / FTP / WebDAV / COS 以及 S3 兼容家族已可作为上传存储；当前文件浏览器仅展示已接通浏览能力的存储。</p>
        </div>

        <div class="rounded-2xl border border-dashed border-white/15 p-5 space-y-4">
          <div class="text-sm text-gray-200">新增存储提供者</div>
          <div class="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-4 gap-3">
            <label class="block space-y-1">
              <span class="text-[11px] text-gray-400">存储名称</span>
              <input v-model="newProvider.name" type="text" placeholder="例如 cos-main" class="px-3 py-2 bg-gray-900/70 border border-white/10 rounded-lg w-full" />
            </label>
            <label class="block space-y-1">
              <span class="text-[11px] text-gray-400">存储类型</span>
              <select v-model="newProvider.type" class="px-3 py-2 bg-gray-900/70 border border-white/10 rounded-lg w-full" @change="handleNewProviderTypeChange">
                <option v-for="option in storageTypeOptions" :key="option.value" :value="option.value">
                  {{ option.value }}{{ option.label ? ` · ${option.label}` : '' }}
                </option>
              </select>
            </label>
            <label class="block space-y-1">
              <span class="text-[11px] text-gray-400">优先级</span>
              <input v-model.number="newProvider.priority" type="number" class="px-3 py-2 bg-gray-900/70 border border-white/10 rounded-lg w-full" />
              <span class="text-[11px] text-gray-500">数值越小越优先，默认上传存储建议设为最高优先级。</span>
            </label>
            <label class="flex items-center gap-2 px-3 py-2 bg-gray-900/50 border border-white/10 rounded-lg text-sm">
              <input v-model="newProvider.enabled" type="checkbox" class="w-4 h-4 rounded" />
              启用
            </label>
            <label class="flex items-center gap-2 px-3 py-2 bg-gray-900/50 border border-white/10 rounded-lg text-sm">
              <input v-model="newProvider.isDefault" type="checkbox" class="w-4 h-4 rounded" />
              设为默认
            </label>
          </div>
          <div class="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-3">
            <label
              v-for="field in getVisibleStorageFields(newProvider.type)"
              :key="`new-${field.key}`"
              class="block space-y-1 rounded-xl border border-white/10 bg-gray-950/30 p-3"
            >
              <div class="flex items-center justify-between gap-3">
                <span class="text-[11px] text-gray-300">{{ field.label }}</span>
                <span class="text-[10px] px-2 py-0.5 rounded-full border border-white/10 text-gray-500">{{ field.shortHint }}</span>
              </div>
              <input
                v-model="(newProvider as any)[field.key]"
                type="text"
                :placeholder="field.placeholder"
                class="px-3 py-2 bg-gray-900/70 border border-white/10 rounded-lg w-full"
              />
              <span class="text-[11px] text-gray-500">{{ field.description }}</span>
            </label>
          </div>
          <div
            v-if="!getVisibleStorageFields(newProvider.type).length"
            class="rounded-xl border border-white/10 bg-white/5 px-4 py-3 text-xs text-gray-400"
          >
            当前类型没有额外位置参数，只需填写名称、类型和扩展配置即可。
          </div>
          <div class="grid grid-cols-1 xl:grid-cols-2 gap-4">
            <div class="rounded-xl border border-white/10 bg-white/5 px-4 py-4 text-xs text-gray-300 space-y-2">
              <div class="text-sm text-white">配置建议</div>
              <div>当前类型：{{ storageTypeLabel(newProvider.type) }}</div>
              <div>{{ storagePreset.hint }}</div>
              <div v-if="shouldShowStorageField(newProvider.type, 'endpoint')">推荐{{ getStorageFieldLabel(newProvider.type, 'endpoint') }}：{{ storagePreset.endpoint || '—' }}</div>
              <div v-if="shouldShowStorageField(newProvider.type, 'bucketName')">推荐{{ getStorageFieldLabel(newProvider.type, 'bucketName') }}：{{ storagePreset.bucketName || '—' }}</div>
              <div v-if="shouldShowStorageField(newProvider.type, 'baseDirectory')">推荐{{ getStorageFieldLabel(newProvider.type, 'baseDirectory') }}：{{ storagePreset.baseDirectory || '—' }}</div>
              <div v-if="newProviderAssessment.missingLabels.length" class="text-amber-200">
                缺失字段：{{ newProviderAssessment.missingLabels.join('、') }}
              </div>
            </div>
            <div class="rounded-xl border border-white/10 bg-white/5 px-4 py-4 text-xs text-gray-300 space-y-2">
              <div class="flex items-center justify-between gap-3">
                <div class="text-sm text-white">示例配置 JSON</div>
                <button
                  type="button"
                  class="px-3 py-1.5 rounded-lg bg-gray-800 hover:bg-gray-700 text-xs border border-white/10"
                  @click="applyStoragePreset"
                >
                  应用推荐值
                </button>
              </div>
              <pre class="rounded-lg border border-white/10 bg-gray-950/70 p-3 overflow-auto">{{ storagePreset.configJson }}</pre>
            </div>
          </div>
          <label class="block space-y-1">
            <span class="text-[11px] text-gray-400">扩展配置 JSON</span>
            <textarea
              v-model="newProvider.configJson"
              rows="3"
              placeholder="例如密钥、路径映射、认证参数等"
              class="w-full px-3 py-2 bg-gray-900/70 border border-white/10 rounded-lg"
            />
          </label>
          <div class="flex justify-end">
            <button
              class="px-4 py-2 rounded-lg bg-purple-600 hover:bg-purple-500 disabled:opacity-60 text-sm"
              :disabled="savingProviderId === 0"
              @click="createProvider"
            >
              {{ savingProviderId === 0 ? '创建中...' : '新增存储提供者' }}
            </button>
          </div>
        </div>

        <ConfigurableTable
          :columns="storageTableColumns"
          :rows="storageProviders"
          :preferences="getTablePreference('storage')"
          :loading="loading"
          loading-text="正在加载存储配置..."
          empty-text="暂无存储提供者。"
          @update:preferences="updateTablePreference('storage', $event)"
        >
          <template #cell-name="{ row: provider }">
            <div class="space-y-2">
              <label class="block space-y-1">
                <span class="text-[11px] text-gray-400">存储名称</span>
                <input v-model="provider.name" type="text" class="w-full px-3 py-2 bg-gray-900/70 border border-white/10 rounded-lg" />
              </label>
              <div class="text-xs text-gray-500">创建于 {{ formatDate(provider.createdAt) }}</div>
            </div>
          </template>
          <template #cell-type="{ row: provider }">
            <label class="block space-y-1">
              <span class="text-[11px] text-gray-400">存储类型</span>
              <select v-model="provider.type" class="w-full px-3 py-2 bg-gray-900/70 border border-white/10 rounded-lg" @change="handleProviderTypeChange(provider)">
                <option v-for="option in storageTypeOptions" :key="option.value" :value="option.value">
                  {{ option.value }}{{ option.label ? ` · ${option.label}` : '' }}
                </option>
              </select>
            </label>
          </template>
          <template #cell-priority="{ row: provider }">
            <label class="block space-y-1">
              <span class="text-[11px] text-gray-400">优先级</span>
              <input v-model.number="provider.priority" type="number" class="w-full px-3 py-2 bg-gray-900/70 border border-white/10 rounded-lg" />
              <span class="text-[11px] text-gray-500">数值越小越优先。</span>
            </label>
          </template>
          <template #cell-capability="{ row: provider }">
            <div class="flex flex-wrap gap-2 text-[11px]">
              <span class="chip" :class="provider.browserSupported ? 'text-emerald-200' : 'text-gray-400'">浏览器：{{ provider.browserSupported ? '可用' : '未接通' }}</span>
              <span class="chip" :class="provider.uploadSupported ? 'text-emerald-200' : 'text-gray-400'">上传：{{ provider.uploadSupported ? '可用' : '未接通' }}</span>
              <span class="chip" :class="provider.scanSupported ? 'text-emerald-200' : 'text-gray-400'">扫描：{{ provider.scanSupported ? '可用' : '未接通' }}</span>
              <span class="chip" :class="provider.previewSupported ? 'text-emerald-200' : 'text-gray-400'">预览：{{ provider.previewSupported ? '可用' : '未接通' }}</span>
            </div>
          </template>
          <template #cell-location="{ row: provider }">
            <div class="space-y-2">
              <label
                v-for="field in getVisibleStorageFields(provider.type)"
                :key="`${provider.id}-${field.key}`"
                class="block space-y-1 rounded-xl border border-white/10 bg-gray-950/30 p-3"
              >
                <div class="flex items-center justify-between gap-3">
                  <span class="text-[11px] text-gray-300">{{ field.label }}</span>
                  <span class="text-[10px] px-2 py-0.5 rounded-full border border-white/10 text-gray-500">{{ field.shortHint }}</span>
                </div>
                <input
                  v-model="(provider as any)[field.key]"
                  type="text"
                  :placeholder="field.placeholder"
                  class="w-full px-3 py-2 bg-gray-900/70 border border-white/10 rounded-lg text-xs"
                />
                <span class="text-[11px] text-gray-500">{{ field.description }}</span>
              </label>
              <div
                v-if="!getVisibleStorageFields(provider.type).length"
                class="rounded-xl border border-white/10 bg-white/5 px-3 py-2 text-[11px] text-gray-400"
              >
                当前类型没有额外位置字段。
              </div>
              <div class="text-[11px] text-gray-400 space-y-1">
                <div v-if="shouldShowStorageField(provider.type, 'endpoint')">推荐{{ getStorageFieldLabel(provider.type, 'endpoint') }}：{{ getStoragePreset(provider.type).endpoint || '—' }}</div>
                <div v-if="shouldShowStorageField(provider.type, 'bucketName')">推荐{{ getStorageFieldLabel(provider.type, 'bucketName') }}：{{ getStoragePreset(provider.type).bucketName || '—' }}</div>
                <div v-if="shouldShowStorageField(provider.type, 'baseDirectory')">推荐{{ getStorageFieldLabel(provider.type, 'baseDirectory') }}：{{ getStoragePreset(provider.type).baseDirectory || '—' }}</div>
              </div>
            </div>
          </template>
          <template #cell-configJson="{ row: provider }">
            <div class="space-y-2">
              <label class="block space-y-1">
                <span class="text-[11px] text-gray-400">扩展配置 JSON</span>
                <textarea v-model="provider.configJson" rows="3" class="w-full px-3 py-2 bg-gray-900/70 border border-white/10 rounded-lg text-xs" />
              </label>
              <div class="flex items-start justify-between gap-2 text-[11px]">
                <div class="text-gray-400">
                  {{ getStoragePreset(provider.type).hint }}
                  <span v-if="getProviderAssessment(provider).missingLabels.length" class="text-amber-200">
                    · 缺失：{{ getProviderAssessment(provider).missingLabels.join('、') }}
                  </span>
                </div>
                <button
                  type="button"
                  class="shrink-0 px-2 py-1 rounded bg-gray-800 hover:bg-gray-700 border border-white/10 text-gray-200"
                  @click="applyProviderPreset(provider)"
                >
                  套用推荐
                </button>
              </div>
            </div>
          </template>
          <template #cell-status="{ row: provider }">
            <div class="space-y-2 text-xs text-gray-300">
              <div>解析根目录：{{ provider.resolvedBaseDirectory || '—' }}</div>
              <div>状态：{{ provider.supportMessage || '当前能力已接通' }}</div>
              <label class="flex items-center gap-2">
                <input v-model="provider.enabled" type="checkbox" class="w-4 h-4 rounded" />
                启用
              </label>
              <label class="flex items-center gap-2">
                <input v-model="provider.isDefault" type="checkbox" class="w-4 h-4 rounded" />
                默认上传位置
              </label>
            </div>
          </template>
          <template #cell-actions="{ row: provider }">
            <button
              class="w-full px-3 py-2 rounded-lg bg-purple-600 hover:bg-purple-500 disabled:opacity-60 text-sm"
              :disabled="savingProviderId === provider.id"
              @click="saveProvider(provider)"
            >
              {{ savingProviderId === provider.id ? '保存中...' : '保存' }}
            </button>
          </template>
        </ConfigurableTable>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import ApiTestToolPanel from '@/components/admin/ApiTestToolPanel.vue'
import ConfigurableTable, { type ConfigurableColumn } from '@/components/admin/ConfigurableTable.vue'
import AdminSectionTabs from '@/components/AdminSectionTabs.vue'
import {
  type ConfigurableTablePreference,
  type LegacyMigrationSummary,
  type LoginRecordSummary,
  type OperationLogSummary,
  type PageResponse,
  type SuperAdminTablePreferences,
  superAdminApi,
  type StorageProviderSummary,
  type SuperAdminOverview,
  type SuperAdminSettings,
  type UserAccountSummary,
  type PaymentInitiationResponse,
  type PaymentRefundPreview,
  type VipOrderPaymentPreview,
  type VipRenewalExecuteResponse,
  type VipRenewalPreviewResponse,
  type VipOrderSummary,
  type VipPlanSummary
} from '@/api'
import { useAuthStore } from '@/stores/auth'
import { emailProviderLabel, paymentProviderLabel, smsProviderLabel, storageTypeLabel } from '@/utils/providerLabels'

const authStore = useAuthStore()
const route = useRoute()
const router = useRouter()

type SuperAdminTabKey = 'overview' | 'global' | 'integrations' | 'users' | 'logins' | 'operations' | 'vip' | 'vipOrders' | 'storage'

const loading = ref(false)
const savingSettings = ref(false)
const savingUserId = ref<number | null>(null)
const savingProviderId = ref<number | null>(null)
const savingVipPlanId = ref<number | null>(null)
const savingVipOrderId = ref<number | null>(null)
const runningMigration = ref(false)
const sendingTestEmail = ref(false)
const sendingCustomEmail = ref(false)
const statusMessage = ref('')
const statusType = ref<'success' | 'error'>('success')
const migrationSummary = ref<LegacyMigrationSummary | null>(null)
const selectedLoginRecordUserId = ref<number | null>(null)
const userKeyword = ref('')
const usersPage = reactive({ page: 0, size: 8, totalElements: 0, totalPages: 0, first: true, last: true })
const loginRecordsPage = reactive({ page: 0, size: 20, totalElements: 0, totalPages: 0, first: true, last: true })
const operationLogsPage = reactive({ page: 0, size: 20, totalElements: 0, totalPages: 0, first: true, last: true })
const selectedOperationLogUserId = ref<number | null>(null)
const tablePreferences = ref<SuperAdminTablePreferences>({})
let tablePreferenceSaveTimer: ReturnType<typeof setTimeout> | null = null

const superAdminTabs = [
  { key: 'overview', label: '概览' },
  { key: 'global', label: '全局设置' },
  { key: 'integrations', label: '短信 / 邮件 / 迁移' },
  { key: 'users', label: '用户管理' },
  { key: 'logins', label: '登录记录' },
  { key: 'operations', label: '操作记录' },
  { key: 'vip', label: 'VIP 套餐' },
  { key: 'vipOrders', label: 'VIP 订单' },
  { key: 'storage', label: '存储配置' }
] as const

const validSuperAdminTabs = new Set<SuperAdminTabKey>(superAdminTabs.map(tab => tab.key))
const activeTab = computed<SuperAdminTabKey>(() => {
  const currentTab = typeof route.query.tab === 'string' ? route.query.tab : 'overview'
  return validSuperAdminTabs.has(currentTab as SuperAdminTabKey) ? currentTab as SuperAdminTabKey : 'overview'
})

const changeActiveTab = (tab: SuperAdminTabKey) => {
  router.replace({
    path: route.path,
    query: {
      ...route.query,
      tab: tab === 'overview' ? undefined : tab
    }
  })
}

const overview = reactive<SuperAdminOverview>({
  userCount: 0,
  activeUserCount: 0,
  disabledUserCount: 0,
  lockedUserCount: 0,
  storageProviderCount: 0,
  enabledStorageProviderCount: 0,
  multiUserEnabled: false,
  scanSchedulerEnabled: false,
  scanWorkerCount: 1,
  forceBindPhone: false,
  autoRenewSchedulerEnabled: false,
  smsProviderType: 'ALIYUN',
  smsEnabled: false,
  smsMockEnabled: false,
  emailProviderType: 'SMTP',
  emailEnabled: false,
  emailMockEnabled: false,
  emailCodeLoginEnabled: false,
  emailCodeExpireMinutes: 5,
  paymentProviderType: 'ALIPAY',
  paymentEnabled: false,
  paymentMockEnabled: true,
  defaultUserQuotaBytes: 0,
  defaultVipExtraQuotaBytes: 0,
  localStorageRoot: '',
  userDataRoot: '',
  totalQuotaBytes: 0,
  totalUsedBytes: 0
})

const settings = reactive<SuperAdminSettings>({
  multiUserEnabled: false,
  scanSchedulerEnabled: false,
  scanWorkerCount: 1,
  forceBindPhone: false,
  autoRenewSchedulerEnabled: false,
  smsProviderType: 'ALIYUN',
  smsEnabled: false,
  smsMockEnabled: true,
  smsEndpoint: '',
  smsRegionId: '',
  smsAccessKeyId: '',
  smsAccessKeySecret: '',
  smsSignName: '',
  smsTemplateCode: '',
  smsTemplateParamName: 'code',
  smsSdkAppId: '',
  smsCodeExpireMinutes: 5,
  emailProviderType: 'SMTP',
  emailEnabled: false,
  emailMockEnabled: false,
  emailCodeLoginEnabled: false,
  emailCodeExpireMinutes: 5,
  emailHost: '',
  emailPort: 465,
  emailUsername: '',
  emailPassword: '',
  emailProtocol: 'smtp',
  emailFromAddress: '',
  emailFromName: '',
  emailReplyTo: '',
  emailSslEnabled: true,
  emailStarttlsEnabled: true,
  emailTestRecipient: '',
  paymentProviderType: 'ALIPAY',
  paymentAppId: '',
  paymentMerchantId: '',
  paymentMerchantName: '',
  paymentPrivateKey: '',
  paymentPublicKey: '',
  paymentApiBaseUrl: '',
  paymentNotifyUrl: '',
  paymentReturnUrl: '',
  paymentWebhookSecret: '',
  paymentCurrency: 'CNY',
  paymentVerificationMode: 'AUTO',
  paymentApiSecret: '',
  paymentCertificateSerialNo: '',
  paymentPlatformCertificate: '',
  defaultUserQuotaBytes: 0,
  defaultVipExtraQuotaBytes: 0,
  localStorageRoot: '',
  userDataRoot: '',
  defaultStorageProviderId: null
})

const customEmail = reactive({
  recipient: '',
  subject: '',
  content: '',
  html: false
})

const users = ref<UserAccountSummary[]>([])
const loginRecords = ref<LoginRecordSummary[]>([])
const operationLogs = ref<OperationLogSummary[]>([])
const storageProviders = ref<StorageProviderSummary[]>([])
const vipPlans = ref<VipPlanSummary[]>([])
const vipOrders = ref<VipOrderSummary[]>([])
const focusedVipOrder = ref<VipOrderSummary | null>(null)
const paymentPreview = ref<VipOrderPaymentPreview | null>(null)
const paymentInitiation = ref<PaymentInitiationResponse | null>(null)
const paymentRefundPreview = ref<PaymentRefundPreview | null>(null)
const vipRenewalPreview = ref<VipRenewalPreviewResponse | null>(null)
const vipRenewalExecution = ref<VipRenewalExecuteResponse | null>(null)
const previewingVipOrderId = ref<number | null>(null)
const initiatingVipOrderId = ref<number | null>(null)
const mockingVipOrderId = ref<number | null>(null)
const cancellingVipOrderId = ref<number | null>(null)
const refundingVipOrderId = ref<number | null>(null)
const confirmingVipOrderRefundId = ref<number | null>(null)
const failingVipOrderRefundId = ref<number | null>(null)
const loadingVipRenewalPreview = ref(false)
const executingVipRenewals = ref(false)

const newProvider = reactive<Partial<StorageProviderSummary>>({
  name: '',
  type: 'LOCAL',
  enabled: true,
  isDefault: false,
  priority: 100,
  endpoint: '',
  bucketName: '',
  baseDirectory: '',
  configJson: ''
})

const newVipPlan = reactive<Partial<VipPlanSummary>>({
  code: '',
  name: '',
  description: '',
  extraQuotaGb: 0,
  durationDays: 30,
  priceYuan: 0,
  enabled: true,
  sortOrder: 100
})
const vipOrdersPage = reactive({ page: 0, size: 20, totalElements: 0, totalPages: 0, first: true, last: true })
const selectedVipOrderUserId = ref<number | null>(null)
const vipOrderAutoRenewOnly = ref(false)
const vipOrderDueOnly = ref(false)
const newVipOrder = reactive<Partial<VipOrderSummary>>({
  userId: undefined,
  vipPlanId: undefined,
  amountYuan: 0,
  status: 'PAID',
  source: 'MANUAL',
  paidAt: null,
  nextRenewalAt: null,
  autoRenewEnabled: false,
  expireAt: null,
  remark: ''
})

const showMessage = (message: string, type: 'success' | 'error' = 'success') => {
  statusMessage.value = message
  statusType.value = type
}

const BYTES_PER_GB = 1024 * 1024 * 1024

const storageTypeOptions = [
  { value: 'LOCAL', label: '本地存储（已接通）' },
  { value: 'FTP', label: 'FTP（已接通）' },
  { value: 'WEBDAV', label: 'WebDAV（已接通）' },
  { value: 'COS', label: '腾讯云 COS（已接通）' },
  { value: 'SFTP', label: 'SFTP（挂载目录模式已接通）' },
  { value: 'S3_COMPATIBLE', label: 'S3 兼容对象存储（已接通）' },
  { value: 'MINIO', label: 'MinIO（已接通）' },
  { value: 'OSS', label: '阿里云 OSS（已接通）' },
  { value: 'R2', label: 'Cloudflare R2（已接通）' },
  { value: 'SMB', label: 'SMB 共享（挂载目录模式已接通）' },
  { value: 'NFS', label: 'NFS 共享（挂载目录模式已接通）' },
  { value: 'AZURE_BLOB', label: 'Azure Blob（已接通）' },
  { value: 'GCS', label: 'Google Cloud Storage（已接通）' },
  { value: 'OBS', label: '华为云 OBS（已接通）' },
  { value: 'TOS', label: '火山引擎 TOS（已接通）' },
  { value: 'BOS', label: '百度云 BOS（已接通）' },
  { value: 'UCLOUD_US3', label: 'UCloud US3（已接通）' },
  { value: 'JD_JSS', label: '京东云 JSS（已接通）' },
  { value: 'WASABI', label: 'Wasabi（已接通）' },
  { value: 'QINIU_KODO', label: '七牛云 Kodo（已接通）' },
  { value: 'B2', label: 'Backblaze B2（已接通）' },
  { value: 'UPYUN', label: '又拍云（已接通）' },
  { value: 'DROPBOX', label: 'Dropbox（已接通）' },
  { value: 'ONEDRIVE', label: 'OneDrive（已接通）' }
] as const

const storageTypePresets = {
  LOCAL: { endpoint: '', bucketName: '', baseDirectory: 'data/photos', hint: '本地存储推荐填写服务器本地目录，多用户模式下会自动追加用户目录。', configJson: JSON.stringify({ createIfMissing: true }, null, 2) },
  FTP: { endpoint: 'ftp://127.0.0.1:21', bucketName: '', baseDirectory: '/photos', hint: 'FTP 已接通上传/浏览/预览与基础扫描，建议在配置 JSON 中填写账号密码。', configJson: JSON.stringify({ username: 'ftp-user', password: 'ftp-password', passiveMode: true }, null, 2) },
  WEBDAV: { endpoint: 'https://dav.example.com/remote.php/dav/files/admin', bucketName: '', baseDirectory: '/albums', hint: 'WebDAV 已接通上传/浏览/预览与基础扫描，建议限制根目录并使用独立账号。', configJson: JSON.stringify({ username: 'dav-user', password: 'dav-password' }, null, 2) },
  COS: { endpoint: 'https://cos.ap-guangzhou.myqcloud.com', bucketName: 'photo-bucket-1250000000', baseDirectory: 'albums', hint: 'COS 已接通上传/浏览/预览与基础扫描，推荐配置 region、secretId、secretKey。', configJson: JSON.stringify({ region: 'ap-guangzhou', secretId: 'COS_SECRET_ID', secretKey: 'COS_SECRET_KEY' }, null, 2) },
  SFTP: { endpoint: 'sftp://127.0.0.1:22', bucketName: '', baseDirectory: '/mnt/photo-sftp', hint: 'SFTP 现支持“服务器已挂载目录”模式：先把远端目录通过 sshfs 等方式挂载到宿主机，再把这里填写为挂载目录。endpoint 可保留为来源备注。', configJson: JSON.stringify({ mountedMode: true, username: 'sftp-user', password: 'sftp-password' }, null, 2) },
  S3_COMPATIBLE: { endpoint: 'https://s3.example.com', bucketName: 'photo-bucket', baseDirectory: 'albums', hint: 'S3 兼容存储已接通上传、浏览、预览与扫描，推荐填写 region、accessKey、secretKey。', configJson: JSON.stringify({ region: 'us-east-1', accessKey: 'ACCESS_KEY', secretKey: 'SECRET_KEY' }, null, 2) },
  MINIO: { endpoint: 'https://minio.example.com', bucketName: 'photo-bucket', baseDirectory: 'albums', hint: 'MinIO 已接通上传、浏览、预览与扫描，可沿用 S3 兼容 accessKey / secretKey 配置。', configJson: JSON.stringify({ region: 'us-east-1', accessKey: 'MINIO_ACCESS_KEY', secretKey: 'MINIO_SECRET_KEY' }, null, 2) },
  OSS: { endpoint: 'https://oss-cn-hangzhou.aliyuncs.com', bucketName: 'photo-bucket', baseDirectory: 'albums', hint: '阿里云 OSS 通常需要 endpoint、bucket、AccessKeyId、AccessKeySecret。', configJson: JSON.stringify({ region: 'cn-hangzhou', accessKeyId: 'OSS_ACCESS_KEY_ID', accessKeySecret: 'OSS_ACCESS_KEY_SECRET' }, null, 2) },
  R2: { endpoint: 'https://<accountid>.r2.cloudflarestorage.com', bucketName: 'photo-bucket', baseDirectory: 'albums', hint: 'Cloudflare R2 已接通上传、浏览、预览与扫描，通常使用 S3 兼容接口。', configJson: JSON.stringify({ region: 'auto', accessKey: 'R2_ACCESS_KEY', secretKey: 'R2_SECRET_KEY' }, null, 2) },
  SMB: { endpoint: 'smb://192.168.1.10/share', bucketName: '', baseDirectory: '/mnt/photo-smb', hint: 'SMB 现支持“服务器已挂载目录”模式：先把共享挂载到宿主机，再把这里填写为挂载目录。endpoint 可保留为备注地址。', configJson: JSON.stringify({ mountedMode: true, username: 'smb-user', password: 'smb-password', domain: 'WORKGROUP' }, null, 2) },
  NFS: { endpoint: 'nfs://192.168.1.10:/volume1/photos', bucketName: '', baseDirectory: '/mnt/photo-nfs', hint: 'NFS 现支持“服务器已挂载目录”模式：先完成系统挂载，再把这里填写为挂载目录。endpoint 可保留为来源备注。', configJson: JSON.stringify({ mountedMode: true, mountOptions: 'vers=4.1' }, null, 2) },
  AZURE_BLOB: { endpoint: 'https://account.blob.core.windows.net', bucketName: 'photos', baseDirectory: 'albums', hint: 'Azure Blob 已接通 REST 模式，支持 accountName + accountKey，或直接填写 SAS Token。', configJson: JSON.stringify({ accountName: 'account', accountKey: 'AZURE_ACCOUNT_KEY', sasToken: '?sv=...' }, null, 2) },
  GCS: { endpoint: 'https://storage.googleapis.com', bucketName: 'photo-bucket', baseDirectory: 'albums', hint: 'GCS 当前走 XML API / HMAC 兼容模式，推荐填写 HMAC accessKey / secretKey，region 使用 auto。', configJson: JSON.stringify({ region: 'auto', accessKey: 'GCS_HMAC_ACCESS_KEY', secretKey: 'GCS_HMAC_SECRET' }, null, 2) },
  OBS: { endpoint: 'https://obs.cn-north-4.myhuaweicloud.com', bucketName: 'photo-bucket', baseDirectory: 'albums', hint: '华为云 OBS 已按兼容接口接通上传、浏览、预览与扫描。', configJson: JSON.stringify({ region: 'cn-north-4', accessKey: 'OBS_ACCESS_KEY', secretKey: 'OBS_SECRET_KEY' }, null, 2) },
  TOS: { endpoint: 'https://tos-cn-beijing.volces.com', bucketName: 'photo-bucket', baseDirectory: 'albums', hint: '火山引擎 TOS 已按兼容接口接通上传、浏览、预览与扫描。', configJson: JSON.stringify({ region: 'cn-beijing', accessKey: 'TOS_ACCESS_KEY', secretKey: 'TOS_SECRET_KEY' }, null, 2) },
  BOS: { endpoint: 'https://bj.bcebos.com', bucketName: 'photo-bucket', baseDirectory: 'albums', hint: '百度云 BOS 已按兼容接口接通上传、浏览、预览与扫描。', configJson: JSON.stringify({ region: 'bj', accessKey: 'BOS_ACCESS_KEY', secretKey: 'BOS_SECRET_KEY' }, null, 2) },
  UCLOUD_US3: { endpoint: 'https://us3-cn-bj.ufileos.com', bucketName: 'photo-bucket', baseDirectory: 'albums', hint: 'UCloud US3 已接通上传、浏览、预览与扫描。', configJson: JSON.stringify({ region: 'cn-bj', accessKey: 'US3_PUBLIC_KEY', secretKey: 'US3_PRIVATE_KEY' }, null, 2) },
  JD_JSS: { endpoint: 'https://s3.jcloudcs.com', bucketName: 'photo-bucket', baseDirectory: 'albums', hint: '京东云 JSS 已按 S3 兼容方式接通上传、浏览、预览与扫描。', configJson: JSON.stringify({ region: 'cn-north-1', accessKey: 'JSS_ACCESS_KEY', secretKey: 'JSS_SECRET_KEY' }, null, 2) },
  WASABI: { endpoint: 'https://s3.ap-northeast-1.wasabisys.com', bucketName: 'photo-bucket', baseDirectory: 'albums', hint: 'Wasabi 已按 S3 兼容方式接通上传、浏览、预览与扫描。', configJson: JSON.stringify({ region: 'ap-northeast-1', accessKey: 'WASABI_ACCESS_KEY', secretKey: 'WASABI_SECRET_KEY' }, null, 2) },
  QINIU_KODO: { endpoint: 'https://s3-cn-east-1.qiniucs.com', bucketName: 'photo-bucket', baseDirectory: 'albums', hint: '七牛云 Kodo 已按 S3 兼容方式接通上传、浏览、预览与扫描。', configJson: JSON.stringify({ region: 'z0', accessKey: 'QINIU_ACCESS_KEY', secretKey: 'QINIU_SECRET_KEY' }, null, 2) },
  B2: { endpoint: 'https://s3.us-west-004.backblazeb2.com', bucketName: 'photo-bucket', baseDirectory: 'albums', hint: 'Backblaze B2 已按 S3 兼容方式接通上传、浏览、预览与扫描。', configJson: JSON.stringify({ region: 'us-west-004', accessKey: 'B2_KEY_ID', secretKey: 'B2_APPLICATION_KEY' }, null, 2) },
  UPYUN: { endpoint: 'https://v0.api.upyun.com', bucketName: 'photo-service', baseDirectory: 'albums', hint: '又拍云已接通上传、浏览、下载与批量管理；预览建议额外配置 publicBaseUrl 或 cdnDomain。', configJson: JSON.stringify({ operator: 'upyun-operator', password: 'upyun-password', publicBaseUrl: 'https://img.example.com' }, null, 2) },
  DROPBOX: { endpoint: 'https://api.dropboxapi.com', bucketName: '', baseDirectory: '/Photos', hint: 'Dropbox 已接通 OAuth Access Token 模式，使用 API + content API 完成上传、浏览、预览与管理。', configJson: JSON.stringify({ accessToken: 'DROPBOX_ACCESS_TOKEN', contentEndpoint: 'https://content.dropboxapi.com' }, null, 2) },
  ONEDRIVE: { endpoint: 'https://graph.microsoft.com/v1.0', bucketName: '', baseDirectory: '/Photos', hint: 'OneDrive 已接通 Microsoft Graph 模式，推荐直接填 accessToken；也支持 tenantId/clientId/clientSecret 拉取应用令牌。', configJson: JSON.stringify({ accessToken: 'ONEDRIVE_ACCESS_TOKEN', drivePrefix: '/me/drive', tenantId: 'TENANT_ID', clientId: 'CLIENT_ID', clientSecret: 'CLIENT_SECRET' }, null, 2) }
} as const

const emailProviderPresets = {
  SMTP: { host: '', port: 465, protocol: 'smtp', sslEnabled: true, starttlsEnabled: true, hint: '通用 SMTP，适合自建邮局或任意兼容 SMTP 的邮件服务。' },
  CUSTOM_SMTP: { host: '', port: 465, protocol: 'smtp', sslEnabled: true, starttlsEnabled: true, hint: '完全自定义 SMTP 参数，适合私有部署或特殊企业邮箱。' },
  ALIYUN_DIRECTMAIL: { host: 'smtpdm.aliyun.com', port: 465, protocol: 'smtp', sslEnabled: true, starttlsEnabled: true, hint: '阿里云邮件推送通常使用 SMTP 账号和授权密码。' },
  TENCENT_EXMAIL: { host: 'smtp.exmail.qq.com', port: 465, protocol: 'smtp', sslEnabled: true, starttlsEnabled: true, hint: '腾讯企业邮建议使用企业邮箱账号和 SMTP 服务密码。' },
  AWS_SES: { host: 'email-smtp.us-east-1.amazonaws.com', port: 465, protocol: 'smtp', sslEnabled: true, starttlsEnabled: true, hint: 'AWS SES 区域会影响主机名，请按实际区域替换。' },
  SENDGRID: { host: 'smtp.sendgrid.net', port: 587, protocol: 'smtp', sslEnabled: false, starttlsEnabled: true, hint: 'SendGrid 一般使用 apikey 作为用户名，API Key 作为密码。' },
  MAILGUN: { host: 'smtp.mailgun.org', port: 587, protocol: 'smtp', sslEnabled: false, starttlsEnabled: true, hint: 'Mailgun 欧盟区主机可能不同，请按域所在区域调整。' },
  RESEND: { host: 'smtp.resend.com', port: 465, protocol: 'smtp', sslEnabled: true, starttlsEnabled: true, hint: 'Resend 可通过 SMTP 账号直接接入，也可后续扩展 HTTP API。' },
  POSTMARK: { host: 'smtp.postmarkapp.com', port: 587, protocol: 'smtp', sslEnabled: false, starttlsEnabled: true, hint: 'Postmark 通常使用 Server Token 作为 SMTP 密码。' },
  BREVO: { host: 'smtp-relay.brevo.com', port: 587, protocol: 'smtp', sslEnabled: false, starttlsEnabled: true, hint: 'Brevo（原 Sendinblue）通常使用登录邮箱或固定用户名配合 SMTP key。' },
  MAILERSEND: { host: 'smtp.mailersend.net', port: 587, protocol: 'smtp', sslEnabled: false, starttlsEnabled: true, hint: 'MailerSend 可直接使用 SMTP 用户名和密码，也便于后续扩展官方 API。' },
  ZEPTOMAIL: { host: 'smtp.zeptomail.com', port: 587, protocol: 'smtp', sslEnabled: false, starttlsEnabled: true, hint: 'ZeptoMail 适合事务邮件，通常使用专用 SMTP 用户名与密码。' },
  MAILJET: { host: 'in-v3.mailjet.com', port: 587, protocol: 'smtp', sslEnabled: false, starttlsEnabled: true, hint: 'Mailjet 通常使用 API Key / Secret Key 作为 SMTP 用户名和密码。' },
  SPARKPOST: { host: 'smtp.sparkpostmail.com', port: 587, protocol: 'smtp', sslEnabled: false, starttlsEnabled: true, hint: 'SparkPost 一般使用 SMTP Injections，用户名常为 SMTP_Injection，密码为 API Key。' },
  ELASTIC_EMAIL: { host: 'smtp.elasticemail.com', port: 2525, protocol: 'smtp', sslEnabled: false, starttlsEnabled: true, hint: 'Elastic Email 常用邮箱地址或固定用户名配合 SMTP Key。' },
  SMTP2GO: { host: 'mail.smtp2go.com', port: 587, protocol: 'smtp', sslEnabled: false, starttlsEnabled: true, hint: 'SMTP2GO 适合事务邮件与中继发送，可直接使用 SMTP 用户名和密码。' },
  SENDLAYER: { host: 'smtp.sendlayer.net', port: 587, protocol: 'smtp', sslEnabled: false, starttlsEnabled: true, hint: 'SendLayer 常配合 SMTP 用户名与密码，也便于后续扩展 HTTP API。' },
  QQ_EXMAIL: { host: 'smtp.exmail.qq.com', port: 465, protocol: 'smtp', sslEnabled: true, starttlsEnabled: true, hint: 'QQ 企业邮箱与腾讯企业邮共用 SMTP 主机。' },
  NETEASE_EXMAIL: { host: 'smtp.qiye.163.com', port: 465, protocol: 'smtp', sslEnabled: true, starttlsEnabled: true, hint: '网易企业邮箱通常使用企业邮箱账号和客户端授权码。' }
} as const

const paymentProviderPresets = {
  ALIPAY: { apiBaseUrl: 'https://openapi.alipay.com/gateway.do', hint: '支付宝通常需要应用 ID、应用私钥、支付宝公钥与异步通知地址。' },
  WECHAT_PAY: { apiBaseUrl: 'https://api.mch.weixin.qq.com', hint: '微信支付通常需要商户号、应用 ID、API 证书/密钥与回调地址。' },
  STRIPE: { apiBaseUrl: 'https://api.stripe.com', hint: 'Stripe 主要使用 Secret Key 与 Webhook Secret；国内场景注意跨境和结算限制。' },
  PAYPAL: { apiBaseUrl: 'https://api-m.paypal.com', hint: 'PayPal 一般需要 Client ID、Secret 和 Webhook 配置。' },
  UNIONPAY: { apiBaseUrl: 'https://gateway.95516.com', hint: '银联在线支付通常需要商户号、签名证书、公钥证书与前后台通知地址。' },
  PADDLE: { apiBaseUrl: 'https://api.paddle.com', hint: 'Paddle 适合 SaaS/订阅场景，通常需要 Seller ID、API Key 与 Webhook Secret。' },
  LEMON_SQUEEZY: { apiBaseUrl: 'https://api.lemonsqueezy.com', hint: 'Lemon Squeezy 适合数字产品出海，通常需要 Store ID、API Key 与 Webhook Secret。' },
  ADYEN: { apiBaseUrl: 'https://checkout-live.adyen.com', hint: 'Adyen 适合多支付方式聚合，通常需要 Merchant Account、API Key 与 HMAC/Webhook 配置。' },
  MOLLIE: { apiBaseUrl: 'https://api.mollie.com', hint: 'Mollie 常用于欧洲收单，通常需要 API Key、回跳地址与 Webhook 通知地址。' },
  XENDIT: { apiBaseUrl: 'https://api.xendit.co', hint: 'Xendit 常用于东南亚收单，通常需要 API Key、商户/业务标识与 Webhook Secret。' },
  MIDTRANS: { apiBaseUrl: 'https://api.midtrans.com', hint: 'Midtrans 常用于印尼支付，通常需要 Server Key、Merchant ID 与回跳通知配置。' },
  CUSTOM_WEBHOOK: { apiBaseUrl: 'https://example.com/payment', hint: '适合先对接自建支付网关或第三方聚合支付服务。' }
} as const

const paymentProviderCapabilityMeta = {
  ALIPAY: {
    initiationMode: 'REDIRECT_FORM',
    refundMode: 'API_REFUND',
    capabilityTags: ['表单跳转', 'RSA 验签', '页面回跳'],
    integrationSteps: ['补应用私钥签名与 biz_content 序列化', '前端提交表单跳转支付宝收银台', '回调侧使用支付宝公钥完成 RSA 验签', '退款时接 alipay.trade.refund']
  },
  WECHAT_PAY: {
    initiationMode: 'QR_CODE',
    refundMode: 'API_REFUND',
    capabilityTags: ['二维码拉起', '证书/HMAC 验签', '异步回调'],
    integrationSteps: ['补商户私钥签名与请求头构造', '解析 Native 下单返回的 code_url', '回调侧按平台证书或 APIv3 Key 验签', '退款时接 v3/refund/domestic/refunds']
  },
  STRIPE: {
    initiationMode: 'API_REQUEST',
    refundMode: 'API_REFUND',
    capabilityTags: ['Hosted Checkout', 'Webhook 验签', '退款 API'],
    integrationSteps: ['创建 Checkout Session 并回填 hosted URL', '回调侧校验 Stripe-Signature', '支付成功后同步 session/payment_intent', '退款时接 refunds.create']
  },
  PAYPAL: {
    initiationMode: 'API_REQUEST',
    refundMode: 'API_REFUND',
    capabilityTags: ['Approve 链接', 'Webhook 验签', '退款 API'],
    integrationSteps: ['创建 checkout order 并提取 approve 链接', '回调/Webhook 校验 PayPal 签名', '支付成功后 capture order', '退款时接 capture refund API']
  },
  UNIONPAY: {
    initiationMode: 'REDIRECT_FORM',
    refundMode: 'API_REFUND',
    capabilityTags: ['网关表单', '证书签名', '前后台通知'],
    integrationSteps: ['补证书签名并提交前台表单', '处理前台回跳和后台通知', '按银联证书体系完成验签', '退款时接退款交易接口']
  },
  PADDLE: {
    initiationMode: 'API_REQUEST',
    refundMode: 'DASHBOARD_OR_API',
    capabilityTags: ['订阅/数字商品', 'Hosted Checkout', 'Webhook 验签'],
    integrationSteps: ['创建 transaction 并回填 hosted checkout 链接', '校验 Paddle Webhook Secret', '联通订阅/自动续费映射', '退款时接 Paddle transaction adjustment']
  },
  LEMON_SQUEEZY: {
    initiationMode: 'API_REQUEST',
    refundMode: 'DASHBOARD_OR_API',
    capabilityTags: ['订阅/数字商品', 'Hosted Checkout', 'Webhook 验签'],
    integrationSteps: ['创建 checkout 并回填 hosted checkout 链接', '校验 Lemon Squeezy 签名头', '联通订阅与 License/Order 映射', '退款优先走控制台或补官方 API']
  },
  ADYEN: {
    initiationMode: 'API_REQUEST',
    refundMode: 'API_REFUND',
    capabilityTags: ['聚合收单', '3DS 扩展', 'Webhook 验签'],
    integrationSteps: ['先拉 paymentMethods 再发起 payments', '补 3DS / redirectResult 回跳处理', '校验 HMAC 通知签名', '退款时接 modifications/refunds']
  },
  MOLLIE: {
    initiationMode: 'API_REQUEST',
    refundMode: 'API_REFUND',
    capabilityTags: ['欧洲收单', 'Hosted Checkout', '退款 API'],
    integrationSteps: ['创建 payment 并回填 checkoutUrl', '回调后主动查询 payment 状态', '校验 metadata 与来源单映射', '退款时接 refunds API']
  },
  XENDIT: {
    initiationMode: 'API_REQUEST',
    refundMode: 'API_REFUND',
    capabilityTags: ['东南亚支付', 'Hosted Payment Page', 'Webhook 验签'],
    integrationSteps: ['创建 payment request 并回填 hosted payment 链接', '校验 Xendit webhook secret', '支付后根据 referenceId 回写订单', '退款时接 refund / payment token API']
  },
  MIDTRANS: {
    initiationMode: 'API_REQUEST',
    refundMode: 'API_REFUND',
    capabilityTags: ['东南亚支付', 'Hosted Payment Page', 'Webhook 验签'],
    integrationSteps: ['选定 Snap 或 Core API 并生成 token/redirect_url', '校验 transaction_status / fraud_status 回调', '订单完成后根据 order_id 回写', '退款时接 Midtrans refund API']
  },
  CUSTOM_WEBHOOK: {
    initiationMode: 'API_REQUEST',
    refundMode: 'CUSTOM_CALLBACK',
    capabilityTags: ['自定义网关', '内部收银台', '自定义验签'],
    integrationSteps: ['定义内部支付网关请求协议', '约定统一回调签名头与订单号字段', '完成支付后调用统一回调入口', '退款时复用自定义退款回调协议']
  }
} as const

const paymentVerificationModeMeta = {
  AUTO: {
    label: 'AUTO',
    description: '按平台推荐值自动选择验签方式，适合先完成联调再做精细化收敛。'
  },
  HMAC: {
    label: 'HMAC',
    description: '适合 Stripe、Adyen、Webhook Secret 一类共享密钥验签场景。'
  },
  RSA: {
    label: 'RSA',
    description: '适合支付宝、银联等公私钥签名验签模式。'
  },
  CERTIFICATE: {
    label: 'CERTIFICATE',
    description: '适合微信支付这类证书链或平台证书验签模式。'
  },
  CUSTOM: {
    label: 'CUSTOM',
    description: '适合自建网关或临时兼容特殊签名协议。'
  }
} as const

const paymentVerificationModesByProvider: Record<string, Array<keyof typeof paymentVerificationModeMeta>> = {
  ALIPAY: ['AUTO', 'RSA'],
  WECHAT_PAY: ['AUTO', 'HMAC', 'CERTIFICATE'],
  STRIPE: ['AUTO', 'HMAC'],
  PAYPAL: ['AUTO', 'HMAC', 'RSA'],
  UNIONPAY: ['AUTO', 'RSA', 'CERTIFICATE'],
  PADDLE: ['AUTO', 'HMAC'],
  LEMON_SQUEEZY: ['AUTO', 'HMAC'],
  ADYEN: ['AUTO', 'HMAC'],
  MOLLIE: ['AUTO', 'CUSTOM'],
  XENDIT: ['AUTO', 'HMAC'],
  MIDTRANS: ['AUTO', 'HMAC', 'CUSTOM'],
  CUSTOM_WEBHOOK: ['AUTO', 'HMAC', 'RSA', 'CERTIFICATE', 'CUSTOM']
}

const userTableColumns: ConfigurableColumn[] = [
  { key: 'user', label: '用户', sortable: true, cellClass: 'min-w-[280px]' },
  { key: 'phone', label: '手机号 / 邮箱', sortable: true, cellClass: 'min-w-[260px]' },
  { key: 'role', label: '角色', sortable: true, cellClass: 'min-w-[160px]' },
  { key: 'status', label: '状态', sortable: true, cellClass: 'min-w-[160px]' },
  { key: 'quota', label: '配额', sortable: false, cellClass: 'min-w-[220px]' },
  { key: 'vip', label: 'VIP', sortable: false, cellClass: 'min-w-[260px]' },
  { key: 'storage', label: '上传存储', sortable: false, cellClass: 'min-w-[220px]' },
  { key: 'usage', label: '用量', sortable: true, cellClass: 'min-w-[200px]' },
  { key: 'visible', label: '公开展示', sortable: true, cellClass: 'min-w-[140px]' },
  { key: 'lastLoginAt', label: '最近登录', sortable: true, cellClass: 'min-w-[170px]' },
  { key: 'actions', label: '操作', sortable: false, cellClass: 'min-w-[220px]' }
]

const loginTableColumns: ConfigurableColumn[] = [
  { key: 'createdAt', label: '时间', sortable: true, cellClass: 'whitespace-nowrap min-w-[170px]' },
  { key: 'account', label: '账号', sortable: true, cellClass: 'min-w-[220px]' },
  { key: 'loginMethod', label: '方式', sortable: true, cellClass: 'min-w-[120px]' },
  { key: 'success', label: '结果', sortable: true, cellClass: 'min-w-[100px]' },
  { key: 'source', label: '来源', sortable: false, cellClass: 'min-w-[260px]' },
  { key: 'failureReason', label: '备注', sortable: true, cellClass: 'min-w-[220px]' }
]

const operationTableColumns: ConfigurableColumn[] = [
  { key: 'createdAt', label: '时间', sortable: true, cellClass: 'whitespace-nowrap min-w-[170px]' },
  { key: 'account', label: '账号', sortable: true, cellClass: 'min-w-[220px]' },
  { key: 'operationType', label: '操作', sortable: true, cellClass: 'min-w-[140px]' },
  { key: 'target', label: '目标', sortable: false, cellClass: 'min-w-[180px]' },
  { key: 'targetPath', label: '路径', sortable: true, cellClass: 'min-w-[220px] break-all' },
  { key: 'detailJson', label: '详情', sortable: false, cellClass: 'min-w-[260px] break-all' }
]

const vipPlanTableColumns: ConfigurableColumn[] = [
  { key: 'code', label: '编码', sortable: true, cellClass: 'min-w-[160px]' },
  { key: 'name', label: '名称', sortable: true, cellClass: 'min-w-[200px]' },
  { key: 'extraQuotaGb', label: '额外空间(GB)', sortable: true, cellClass: 'min-w-[130px]' },
  { key: 'durationDays', label: '时长(天)', sortable: true, cellClass: 'min-w-[120px]' },
  { key: 'priceYuan', label: '价格(元)', sortable: true, cellClass: 'min-w-[120px]' },
  { key: 'sortOrder', label: '排序', sortable: true, cellClass: 'min-w-[100px]' },
  { key: 'enabled', label: '启用', sortable: true, cellClass: 'min-w-[100px]' },
  { key: 'description', label: '说明', sortable: false, cellClass: 'min-w-[260px]' },
  { key: 'actions', label: '操作', sortable: false, cellClass: 'min-w-[120px]' }
]

const vipOrderTableColumns: ConfigurableColumn[] = [
  { key: 'order', label: '订单', sortable: true, cellClass: 'min-w-[220px]' },
  { key: 'amountYuan', label: '金额', sortable: true, cellClass: 'min-w-[120px]' },
  { key: 'status', label: '状态', sortable: true, cellClass: 'min-w-[130px]' },
  { key: 'source', label: '来源', sortable: true, cellClass: 'min-w-[140px]' },
  { key: 'autoRenewEnabled', label: '自动续费', sortable: true, cellClass: 'min-w-[120px]' },
  { key: 'timeline', label: '时间轴', sortable: false, cellClass: 'min-w-[220px]' },
  { key: 'payment', label: '支付', sortable: false, cellClass: 'min-w-[200px]' },
  { key: 'renewal', label: '续费', sortable: false, cellClass: 'min-w-[180px]' },
  { key: 'remark', label: '备注', sortable: false, cellClass: 'min-w-[220px]' },
  { key: 'actions', label: '操作', sortable: false, cellClass: 'min-w-[220px]' }
]

const storageTableColumns: ConfigurableColumn[] = [
  { key: 'name', label: '名称', sortable: true, cellClass: 'min-w-[180px]' },
  { key: 'type', label: '类型', sortable: true, cellClass: 'min-w-[150px]' },
  { key: 'priority', label: '优先级', sortable: true, cellClass: 'min-w-[110px]' },
  { key: 'capability', label: '能力', sortable: false, cellClass: 'min-w-[240px]' },
  { key: 'location', label: '位置', sortable: false, cellClass: 'min-w-[260px]' },
  { key: 'configJson', label: '配置', sortable: false, cellClass: 'min-w-[260px]' },
  { key: 'status', label: '状态', sortable: false, cellClass: 'min-w-[220px]' },
  { key: 'actions', label: '操作', sortable: false, cellClass: 'min-w-[120px]' }
]

const smsEndpointPlaceholder = computed(() => {
  switch (settings.smsProviderType) {
    case 'TENCENT_CLOUD':
      return 'https://sms.tencentcloudapi.com'
    case 'TWILIO':
      return 'https://api.twilio.com/2010-04-01'
    case 'HUAWEI_CLOUD':
      return 'https://smsapi.cn-north-4.myhuaweicloud.com'
    case 'VOLCENGINE':
      return 'https://sms.volcengineapi.com'
    case 'CLOOPEN':
      return 'https://app.cloopen.com:8883'
    case 'AWS_SNS':
      return 'https://sns.ap-southeast-1.amazonaws.com'
    case 'YUNPIAN':
      return 'https://sms.yunpian.com/v2/sms/single_send.json'
    case 'SUBMAIL':
      return 'https://api-v4.mysubmail.com/sms/send'
    case 'MESSAGEBIRD':
      return 'https://rest.messagebird.com/messages'
    case 'VONAGE':
      return 'https://rest.nexmo.com/sms/json'
    case 'INFOBIP':
      return 'https://api.infobip.com/sms/2/text/advanced'
    case 'PLIVO':
      return 'https://api.plivo.com/v1/Account/{authId}/Message/'
    case 'SINCH':
      return 'https://sms.api.sinch.com/xms/v1/{servicePlanId}/batches'
    case 'TELNYX':
      return 'https://api.telnyx.com/v2/messages'
    case 'SMSAERO':
      return 'https://gate.smsaero.ru/v2/sms/send'
    case 'HTTP_WEBHOOK':
      return 'https://example.com/sms/send'
    case 'ALIYUN':
    default:
      return 'https://dysmsapi.aliyuncs.com/'
  }
})

const smsRegionPlaceholder = computed(() => {
  switch (settings.smsProviderType) {
    case 'TENCENT_CLOUD':
      return 'ap-guangzhou'
    case 'TWILIO':
      return '+86'
    case 'HUAWEI_CLOUD':
      return 'cn-north-4'
    case 'VOLCENGINE':
      return 'cn-north-1'
    case 'CLOOPEN':
      return '可选，业务分组或应用区域'
    case 'AWS_SNS':
      return 'ap-southeast-1'
    case 'YUNPIAN':
      return 'cn'
    case 'SUBMAIL':
      return '可选，项目空间或区域标识'
    case 'MESSAGEBIRD':
      return 'eu'
    case 'VONAGE':
      return '86'
    case 'INFOBIP':
      return 'global'
    case 'PLIVO':
      return 'us'
    case 'SINCH':
      return 'global'
    case 'TELNYX':
      return 'us'
    case 'SMSAERO':
      return 'ru'
    case 'HTTP_WEBHOOK':
      return '可选，自定义区域或国家码'
    case 'ALIYUN':
    default:
      return 'cn-hangzhou'
  }
})

const smsAccessKeyIdLabel = computed(() => {
  switch (settings.smsProviderType) {
    case 'TENCENT_CLOUD':
      return 'SecretId'
    case 'TWILIO':
      return 'Account SID'
    case 'HUAWEI_CLOUD':
      return 'App Key / Access Key'
    case 'VOLCENGINE':
      return 'Access Key ID'
    case 'CLOOPEN':
      return 'Account SID'
    case 'AWS_SNS':
      return 'Access Key ID'
    case 'YUNPIAN':
      return 'API Key'
    case 'SUBMAIL':
      return 'App ID'
    case 'MESSAGEBIRD':
      return 'Access Key'
    case 'VONAGE':
      return 'API Key'
    case 'INFOBIP':
      return 'API Key'
    case 'PLIVO':
      return 'Auth ID'
    case 'SINCH':
      return 'Project ID'
    case 'TELNYX':
      return 'API Key'
    case 'SMSAERO':
      return 'Email / Login'
    case 'HTTP_WEBHOOK':
      return 'Webhook 用户名 / Key'
    case 'ALIYUN':
    default:
      return 'AccessKeyId'
  }
})

const smsAccessKeySecretLabel = computed(() => {
  switch (settings.smsProviderType) {
    case 'TENCENT_CLOUD':
      return 'SecretKey'
    case 'TWILIO':
      return 'Auth Token'
    case 'HUAWEI_CLOUD':
      return 'App Secret / Secret Key'
    case 'VOLCENGINE':
      return 'Secret Access Key'
    case 'CLOOPEN':
      return 'Auth Token'
    case 'AWS_SNS':
      return 'Secret Access Key'
    case 'YUNPIAN':
      return 'API Secret（可选）'
    case 'SUBMAIL':
      return 'App Key'
    case 'MESSAGEBIRD':
      return 'Signing Key（可选）'
    case 'VONAGE':
      return 'API Secret'
    case 'INFOBIP':
      return 'API Secret / Token'
    case 'PLIVO':
      return 'Auth Token'
    case 'SINCH':
      return 'API Token'
    case 'TELNYX':
      return 'API Token'
    case 'SMSAERO':
      return 'API Key'
    case 'HTTP_WEBHOOK':
      return 'Webhook 密钥 / Secret'
    case 'ALIYUN':
    default:
      return 'AccessKeySecret'
  }
})

const smsSignLabel = computed(() => {
  switch (settings.smsProviderType) {
    case 'TWILIO':
      return 'From 号码'
    case 'AWS_SNS':
      return 'Sender ID'
    case 'YUNPIAN':
      return '签名 / 扩展号'
    case 'SUBMAIL':
      return '短信签名 / Project'
    case 'MESSAGEBIRD':
      return 'Originator'
    case 'VONAGE':
      return 'From'
    case 'INFOBIP':
      return 'Sender'
    case 'PLIVO':
      return 'Src / Sender'
    case 'SINCH':
      return 'From'
    case 'TELNYX':
      return 'From'
    case 'SMSAERO':
      return '签名 / Sender'
    case 'HTTP_WEBHOOK':
      return '签名 / 发送方标识'
    default:
      return '短信签名'
  }
})

const smsTemplateLabel = computed(() => {
  switch (settings.smsProviderType) {
    case 'TENCENT_CLOUD':
      return '模板 ID'
    case 'TWILIO':
      return 'Messaging Service SID（可选）'
    case 'HUAWEI_CLOUD':
      return '模板 ID'
    case 'VOLCENGINE':
      return '模板 ID'
    case 'CLOOPEN':
      return '模板 ID'
    case 'AWS_SNS':
      return '消息类型 / 模板标识（可选）'
    case 'YUNPIAN':
      return '模板 ID / 模板内容标识'
    case 'SUBMAIL':
      return 'Project / 模板 ID'
    case 'MESSAGEBIRD':
      return '模板 ID / Route'
    case 'VONAGE':
      return '模板 ID（可选）'
    case 'INFOBIP':
      return '模板 ID / Entity ID'
    case 'PLIVO':
      return 'Template ID / Powerpack UUID'
    case 'SINCH':
      return '模板 ID / 类型'
    case 'TELNYX':
      return 'Messaging Profile ID / 模板 ID'
    case 'SMSAERO':
      return '模板 ID'
    case 'HTTP_WEBHOOK':
      return '模板编号 / 自定义模板'
    default:
      return '模板编号'
  }
})

const emailPreset = computed(() => {
  return emailProviderPresets[settings.emailProviderType] || emailProviderPresets.SMTP
})

const paymentPreset = computed(() => {
  return paymentProviderPresets[settings.paymentProviderType] || paymentProviderPresets.ALIPAY
})

const paymentProviderMeta = computed(() => {
  return paymentProviderCapabilityMeta[settings.paymentProviderType] || paymentProviderCapabilityMeta.ALIPAY
})

const paymentVerificationModeOptions = computed(() => {
  const providerType = settings.paymentProviderType || 'ALIPAY'
  const modes = paymentVerificationModesByProvider[providerType] || paymentVerificationModesByProvider.ALIPAY
  return modes.map((value) => ({
    value,
    label: paymentVerificationModeMeta[value].label,
    description: paymentVerificationModeMeta[value].description
  }))
})

const getNormalizedPaymentVerificationMode = (
  providerType?: string | null,
  currentMode?: string | null
) => {
  const type = providerType || 'ALIPAY'
  const options = paymentVerificationModesByProvider[type] || paymentVerificationModesByProvider.ALIPAY
  const normalizedCurrent = String(currentMode || '').trim().toUpperCase()
  if (normalizedCurrent && options.includes(normalizedCurrent as keyof typeof paymentVerificationModeMeta)) {
    return normalizedCurrent
  }
  return options[0] || 'AUTO'
}

const paymentVerificationModeDescription = computed(() => {
  const currentMode = getNormalizedPaymentVerificationMode(
    settings.paymentProviderType,
    settings.paymentVerificationMode
  ) as keyof typeof paymentVerificationModeMeta
  return paymentVerificationModeMeta[currentMode]?.description || paymentVerificationModeMeta.AUTO.description
})

type PaymentFieldKey =
  | 'paymentAppId'
  | 'paymentMerchantId'
  | 'paymentMerchantName'
  | 'paymentApiBaseUrl'
  | 'paymentCurrency'
  | 'paymentNotifyUrl'
  | 'paymentReturnUrl'
  | 'paymentPrivateKey'
  | 'paymentPublicKey'
  | 'paymentWebhookSecret'
  | 'paymentApiSecret'
  | 'paymentCertificateSerialNo'
  | 'paymentPlatformCertificate'

const paymentPlatformFieldSets: Record<string, PaymentFieldKey[]> = {
  ALIPAY: ['paymentAppId', 'paymentMerchantId', 'paymentMerchantName', 'paymentApiBaseUrl', 'paymentCurrency', 'paymentNotifyUrl', 'paymentReturnUrl', 'paymentPrivateKey', 'paymentPublicKey'],
  WECHAT_PAY: ['paymentAppId', 'paymentMerchantId', 'paymentMerchantName', 'paymentApiBaseUrl', 'paymentCurrency', 'paymentNotifyUrl', 'paymentPrivateKey', 'paymentApiSecret', 'paymentCertificateSerialNo', 'paymentPlatformCertificate'],
  STRIPE: ['paymentMerchantName', 'paymentApiBaseUrl', 'paymentCurrency', 'paymentNotifyUrl', 'paymentReturnUrl', 'paymentPrivateKey', 'paymentWebhookSecret'],
  PAYPAL: ['paymentAppId', 'paymentMerchantId', 'paymentMerchantName', 'paymentApiBaseUrl', 'paymentCurrency', 'paymentNotifyUrl', 'paymentReturnUrl', 'paymentPrivateKey', 'paymentWebhookSecret'],
  UNIONPAY: ['paymentMerchantId', 'paymentMerchantName', 'paymentApiBaseUrl', 'paymentCurrency', 'paymentNotifyUrl', 'paymentReturnUrl', 'paymentPrivateKey', 'paymentPublicKey'],
  PADDLE: ['paymentAppId', 'paymentMerchantName', 'paymentApiBaseUrl', 'paymentCurrency', 'paymentNotifyUrl', 'paymentReturnUrl', 'paymentPrivateKey', 'paymentWebhookSecret'],
  LEMON_SQUEEZY: ['paymentAppId', 'paymentMerchantName', 'paymentApiBaseUrl', 'paymentCurrency', 'paymentNotifyUrl', 'paymentReturnUrl', 'paymentPrivateKey', 'paymentWebhookSecret'],
  ADYEN: ['paymentMerchantId', 'paymentMerchantName', 'paymentApiBaseUrl', 'paymentCurrency', 'paymentNotifyUrl', 'paymentReturnUrl', 'paymentApiSecret', 'paymentWebhookSecret'],
  MOLLIE: ['paymentMerchantName', 'paymentApiBaseUrl', 'paymentCurrency', 'paymentNotifyUrl', 'paymentReturnUrl', 'paymentPrivateKey'],
  XENDIT: ['paymentMerchantId', 'paymentMerchantName', 'paymentApiBaseUrl', 'paymentCurrency', 'paymentNotifyUrl', 'paymentReturnUrl', 'paymentApiSecret', 'paymentWebhookSecret'],
  MIDTRANS: ['paymentMerchantId', 'paymentMerchantName', 'paymentApiBaseUrl', 'paymentCurrency', 'paymentNotifyUrl', 'paymentReturnUrl', 'paymentPrivateKey'],
  CUSTOM_WEBHOOK: ['paymentMerchantName', 'paymentApiBaseUrl', 'paymentCurrency', 'paymentNotifyUrl', 'paymentPrivateKey', 'paymentWebhookSecret']
}

const paymentRequiredFieldSets: Record<string, PaymentFieldKey[]> = {
  ALIPAY: ['paymentAppId', 'paymentMerchantId', 'paymentPrivateKey', 'paymentPublicKey', 'paymentNotifyUrl', 'paymentReturnUrl'],
  WECHAT_PAY: ['paymentAppId', 'paymentMerchantId', 'paymentPrivateKey', 'paymentNotifyUrl', 'paymentApiSecret'],
  STRIPE: ['paymentPrivateKey', 'paymentWebhookSecret', 'paymentNotifyUrl', 'paymentReturnUrl'],
  PAYPAL: ['paymentAppId', 'paymentMerchantId', 'paymentPrivateKey', 'paymentWebhookSecret', 'paymentNotifyUrl', 'paymentReturnUrl'],
  UNIONPAY: ['paymentMerchantId', 'paymentPrivateKey', 'paymentPublicKey', 'paymentNotifyUrl', 'paymentReturnUrl'],
  PADDLE: ['paymentAppId', 'paymentPrivateKey', 'paymentWebhookSecret', 'paymentNotifyUrl', 'paymentReturnUrl'],
  LEMON_SQUEEZY: ['paymentAppId', 'paymentPrivateKey', 'paymentWebhookSecret', 'paymentNotifyUrl', 'paymentReturnUrl'],
  ADYEN: ['paymentMerchantId', 'paymentApiSecret', 'paymentWebhookSecret', 'paymentNotifyUrl', 'paymentReturnUrl'],
  MOLLIE: ['paymentPrivateKey', 'paymentNotifyUrl', 'paymentReturnUrl'],
  XENDIT: ['paymentMerchantId', 'paymentApiSecret', 'paymentWebhookSecret', 'paymentNotifyUrl', 'paymentReturnUrl'],
  MIDTRANS: ['paymentMerchantId', 'paymentPrivateKey', 'paymentNotifyUrl', 'paymentReturnUrl'],
  CUSTOM_WEBHOOK: ['paymentPrivateKey', 'paymentNotifyUrl', 'paymentApiBaseUrl']
}

const paymentFieldMeta: Record<PaymentFieldKey, { label: string; placeholder: string; description: string; shortHint: string; multiline?: boolean; rows?: number; wide?: boolean }> = {
  paymentAppId: { label: '应用 ID', placeholder: '平台应用 ID / Client ID / Store ID', description: '例如支付宝 AppId、微信 AppID、PayPal Client ID、Paddle Seller ID。', shortHint: '标识' },
  paymentMerchantId: { label: '商户号', placeholder: '商户号 / Merchant ID / Merchant Account', description: '用于支付网关识别商户主体，通常和应用 ID 分开。', shortHint: '商户' },
  paymentMerchantName: { label: '商户名称', placeholder: '例如 Photo Exhibition', description: '主要用于订单展示、支付文案和平台侧品牌展示。', shortHint: '展示' },
  paymentApiBaseUrl: { label: 'API 基础地址', placeholder: '支付平台 API Root URL', description: '真实联调时请求将基于该地址发起，建议优先使用推荐值。', shortHint: '地址' },
  paymentCurrency: { label: '币种', placeholder: '例如 CNY、USD、EUR、IDR', description: '建议统一使用 ISO 货币代码。', shortHint: '金额' },
  paymentNotifyUrl: { label: '回调地址', placeholder: '异步回调地址', description: '第三方支付平台后台通知地址，优先使用系统统一入口。', shortHint: '回调' },
  paymentReturnUrl: { label: '完成返回地址', placeholder: '支付完成后返回前端的地址', description: '浏览器支付完成后的跳转地址，建议指向统一支付结果页。', shortHint: '跳转' },
  paymentPrivateKey: { label: '私钥 / Secret', placeholder: '粘贴商户私钥、Secret Key 或 Server Key', description: '不同平台用途不同，但都属于服务端机密，不应暴露给前端。', shortHint: '机密', multiline: true, rows: 4, wide: true },
  paymentPublicKey: { label: '公钥 / 平台公钥', placeholder: '粘贴平台公钥 / 公钥文本', description: '支付宝、银联等 RSA 回调验签通常使用该字段。', shortHint: '验签', multiline: true, rows: 4, wide: true },
  paymentWebhookSecret: { label: 'Webhook Secret', placeholder: 'Webhook 验签密钥', description: 'HMAC / Webhook 场景通常依赖该值来校验回调来源。', shortHint: 'HMAC' },
  paymentApiSecret: { label: 'API Secret / APIv3 Key', placeholder: '平台 API Secret / APIv3 Key', description: '用于服务端主动请求支付网关，例如微信 APIv3 Key、Adyen/Xendit API Secret。', shortHint: 'API' },
  paymentCertificateSerialNo: { label: '证书序列号', placeholder: '证书序列号 / Serial No', description: '微信支付等证书验签场景通常需要填写该值。', shortHint: '证书' },
  paymentPlatformCertificate: { label: '平台证书 / 公钥链', placeholder: '粘贴 PEM 证书 / Base64 证书内容', description: '微信支付证书验签或平台证书链校验场景使用。', shortHint: '证书', multiline: true, rows: 5, wide: true }
}

const getPaymentRequiredFields = (providerType?: string | null) => {
  const type = providerType || 'ALIPAY'
  const fields = [...(paymentRequiredFieldSets[type] || paymentRequiredFieldSets.ALIPAY)]
  if (type === 'WECHAT_PAY' && settings.paymentVerificationMode === 'CERTIFICATE') {
    if (!fields.includes('paymentCertificateSerialNo')) fields.push('paymentCertificateSerialNo')
    if (!fields.includes('paymentPlatformCertificate')) fields.push('paymentPlatformCertificate')
  }
  return fields
}

const paymentVisibleFields = computed(() => {
  const type = settings.paymentProviderType || 'ALIPAY'
  const visible = paymentPlatformFieldSets[type] || paymentPlatformFieldSets.ALIPAY
  const required = new Set(getPaymentRequiredFields(type))
  return visible.map((key) => ({
    key,
    ...paymentFieldMeta[key],
    required: required.has(key)
  }))
})

const storagePreset = computed(() => {
  return storageTypePresets[newProvider.type || 'LOCAL'] || storageTypePresets.LOCAL
})

const storageEndpointTypes = new Set([
  'FTP', 'WEBDAV', 'COS', 'SFTP', 'S3_COMPATIBLE', 'MINIO', 'OSS', 'R2',
  'AZURE_BLOB', 'GCS', 'OBS', 'TOS', 'BOS', 'UCLOUD_US3', 'JD_JSS', 'WASABI',
  'QINIU_KODO', 'B2', 'UPYUN', 'DROPBOX', 'ONEDRIVE', 'SMB', 'NFS'
])

const storageBucketTypes = new Set([
  'COS', 'S3_COMPATIBLE', 'MINIO', 'OSS', 'R2', 'AZURE_BLOB', 'GCS', 'OBS',
  'TOS', 'BOS', 'UCLOUD_US3', 'JD_JSS', 'WASABI', 'QINIU_KODO', 'B2', 'UPYUN'
])

const storageBaseDirectoryTypes = new Set([
  'LOCAL', 'FTP', 'WEBDAV', 'COS', 'SFTP', 'S3_COMPATIBLE', 'MINIO', 'OSS', 'R2',
  'SMB', 'NFS', 'AZURE_BLOB', 'GCS', 'OBS', 'TOS', 'BOS', 'UCLOUD_US3',
  'JD_JSS', 'WASABI', 'QINIU_KODO', 'B2', 'UPYUN', 'DROPBOX', 'ONEDRIVE'
])

type StorageFieldKey = 'endpoint' | 'bucketName' | 'baseDirectory'

const shouldShowStorageField = (type?: string | null, field?: 'endpoint' | 'bucketName' | 'baseDirectory') => {
  const normalizedType = type || 'LOCAL'
  if (field === 'endpoint') return storageEndpointTypes.has(normalizedType)
  if (field === 'bucketName') return storageBucketTypes.has(normalizedType)
  if (field === 'baseDirectory') return storageBaseDirectoryTypes.has(normalizedType)
  return false
}

const getStorageFieldLabel = (type?: string | null, field?: 'endpoint' | 'bucketName' | 'baseDirectory') => {
  const normalizedType = type || 'LOCAL'
  if (field === 'endpoint') {
    if (['FTP', 'SFTP'].includes(normalizedType)) return '服务器地址'
    if (normalizedType === 'WEBDAV') return 'WebDAV 地址'
    if (normalizedType === 'SMB') return '共享地址'
    if (normalizedType === 'NFS') return '挂载地址'
    if (normalizedType === 'DROPBOX') return 'API 地址'
    if (normalizedType === 'ONEDRIVE') return 'Graph API 地址'
    return 'Endpoint'
  }
  if (field === 'bucketName') {
    if (normalizedType === 'AZURE_BLOB') return '容器名'
    if (normalizedType === 'UPYUN') return '服务名'
    return 'Bucket / 容器'
  }
  if (field === 'baseDirectory') {
    if (normalizedType === 'LOCAL') return '本地根目录'
    if (storageBucketTypes.has(normalizedType)) return '桶内目录前缀'
    return '基础目录'
  }
  return ''
}

const getStorageFieldDescription = (type?: string | null, field?: StorageFieldKey) => {
  const normalizedType = type || 'LOCAL'
  if (field === 'endpoint') {
    if (['FTP', 'SFTP'].includes(normalizedType)) return '填写协议、主机与端口；用户名密码放到扩展配置 JSON。'
    if (normalizedType === 'WEBDAV') return '填写 WebDAV 根地址，建议限制到专用目录。'
    if (['SMB', 'NFS'].includes(normalizedType)) return '填写共享或挂载入口，后续浏览和扫描都基于这里解析。'
    if (['DROPBOX', 'ONEDRIVE'].includes(normalizedType)) return '填写 API 根地址，认证令牌放到扩展配置 JSON。'
    return '填写服务入口地址，地域、密钥等附加信息放到扩展配置 JSON。'
  }
  if (field === 'bucketName') {
    if (normalizedType === 'AZURE_BLOB') return 'Azure Blob 这里填写容器名。'
    if (normalizedType === 'UPYUN') return '又拍云这里填写服务名。'
    return '对象存储通常在这里填写 Bucket、容器或空间名称。'
  }
  if (field === 'baseDirectory') {
    if (normalizedType === 'LOCAL') return '填写服务器本地根目录；多用户模式下系统会自动追加用户目录。'
    if (storageBucketTypes.has(normalizedType)) return '填写桶内目录前缀，避免把整桶都暴露给当前业务。'
    return '填写该存储的业务根目录或前缀。'
  }
  return ''
}

const getVisibleStorageFields = (type?: string | null) => {
  const fields: Array<{ key: StorageFieldKey; label: string; placeholder: string; description: string; shortHint: string }> = []
  ;(['baseDirectory', 'endpoint', 'bucketName'] as StorageFieldKey[]).forEach((key) => {
    if (!shouldShowStorageField(type, key)) return
    fields.push({
      key,
      label: getStorageFieldLabel(type, key),
      placeholder: getStorageFieldPlaceholder(type, key),
      description: getStorageFieldDescription(type, key),
      shortHint: key === 'baseDirectory' ? '位置' : key === 'endpoint' ? '入口' : '容器'
    })
  })
  return fields
}

const normalizeStorageFieldValue = (value?: string | null) => {
  const text = String(value || '').trim()
  return text ? text : null
}

const clearHiddenStorageFields = (provider: Partial<StorageProviderSummary>) => {
  const type = provider.type || 'LOCAL'
  ;(['endpoint', 'bucketName', 'baseDirectory'] as StorageFieldKey[]).forEach((key) => {
    if (!shouldShowStorageField(type, key)) {
      ;(provider as any)[key] = ''
    }
  })
}

const toStorageProviderPayload = (provider: Partial<StorageProviderSummary>) => {
  const type = provider.type || 'LOCAL'
  return {
    name: String(provider.name || '').trim(),
    type,
    enabled: !!provider.enabled,
    isDefault: !!provider.isDefault,
    priority: provider.priority ?? 100,
    endpoint: shouldShowStorageField(type, 'endpoint') ? normalizeStorageFieldValue(provider.endpoint) : null,
    bucketName: shouldShowStorageField(type, 'bucketName') ? normalizeStorageFieldValue(provider.bucketName) : null,
    baseDirectory: shouldShowStorageField(type, 'baseDirectory') ? normalizeStorageFieldValue(provider.baseDirectory) : null,
    configJson: normalizeStorageFieldValue(provider.configJson)
  }
}

const getStorageFieldPlaceholder = (type?: string | null, field?: 'endpoint' | 'bucketName' | 'baseDirectory') => {
  const normalizedType = type || 'LOCAL'
  if (field === 'endpoint') {
    return storageTypePresets[normalizedType]?.endpoint || '请输入服务地址'
  }
  if (field === 'bucketName') {
    if (['AZURE_BLOB'].includes(normalizedType)) return '容器名'
    if (['UPYUN'].includes(normalizedType)) return '服务名'
    return 'Bucket / 容器名'
  }
  if (field === 'baseDirectory') {
    if (normalizedType === 'LOCAL') return '本地目录，例如 data/photos'
    if (storageBucketTypes.has(normalizedType)) return '桶内前缀 / 基础目录'
    return '基础目录'
  }
  return ''
}

const newProviderAssessment = computed(() => {
  const type = newProvider.type || 'LOCAL'
  const hasText = (value?: string | null) => !!String(value || '').trim()
  const missingLabels: string[] = []
  if (shouldShowStorageField(type, 'baseDirectory') && !hasText(newProvider.baseDirectory)) {
    missingLabels.push('基础目录')
  }
  if (shouldShowStorageField(type, 'endpoint') && !hasText(newProvider.endpoint)) {
    missingLabels.push(getStorageFieldLabel(type, 'endpoint'))
  }
  if (shouldShowStorageField(type, 'bucketName') && !hasText(newProvider.bucketName)) {
    missingLabels.push(getStorageFieldLabel(type, 'bucketName'))
  }
  return { missingLabels }
})

const getStoragePreset = (type?: string | null) => {
  return storageTypePresets[type || 'LOCAL'] || storageTypePresets.LOCAL
}

const getProviderAssessment = (provider: Partial<StorageProviderSummary>) => {
  const type = provider.type || 'LOCAL'
  const hasText = (value?: string | null) => !!String(value || '').trim()
  const missingLabels: string[] = []
  if (shouldShowStorageField(type, 'baseDirectory') && !hasText(provider.baseDirectory)) {
    missingLabels.push('基础目录')
  }
  if (shouldShowStorageField(type, 'endpoint') && !hasText(provider.endpoint)) {
    missingLabels.push(getStorageFieldLabel(type, 'endpoint'))
  }
  if (shouldShowStorageField(type, 'bucketName') && !hasText(provider.bucketName)) {
    missingLabels.push(getStorageFieldLabel(type, 'bucketName'))
  }
  return { missingLabels }
}

const paymentFieldLabels: Record<string, string> = {
  paymentAppId: '应用 ID',
  paymentMerchantId: '商户号',
  paymentMerchantName: '商户名称',
  paymentApiBaseUrl: 'API 基础地址',
  paymentCurrency: '币种',
  paymentPrivateKey: '私钥 / Secret',
  paymentPublicKey: '公钥 / 平台证书',
  paymentNotifyUrl: '回调地址',
  paymentReturnUrl: '完成返回地址',
  paymentWebhookSecret: 'Webhook Secret',
  paymentApiSecret: 'API Secret / APIv3 Key',
  paymentCertificateSerialNo: '证书序列号',
  paymentPlatformCertificate: '平台证书 / 公钥链'
}

const paymentUnifiedUrls = computed(() => {
  if (typeof window === 'undefined') {
    return { notifyUrl: '', returnUrl: '' }
  }
  const origin = window.location.origin.replace(/\/+$/, '')
  const providerType = settings.paymentProviderType || 'ALIPAY'
  return {
    notifyUrl: `${origin}/api/payments/notify/${providerType}`,
    returnUrl: `${origin}/api/payments/return/${providerType}`
  }
})

const paymentConfigAssessment = computed(() => {
  const providerType = settings.paymentProviderType || 'ALIPAY'
  const allowedModes = paymentVerificationModesByProvider[providerType] || paymentVerificationModesByProvider.ALIPAY
  const normalizedMode = getNormalizedPaymentVerificationMode(providerType, settings.paymentVerificationMode)
  const hasText = (value?: string | null) => !!String(value || '').trim()
  const requiredMissingKeys: string[] = []
  const requireField = (field: keyof typeof paymentFieldLabels, required: boolean) => {
    if (required && !hasText(settings[field] as string | null | undefined)) {
      requiredMissingKeys.push(field)
    }
  }

  getPaymentRequiredFields(providerType).forEach((field) => {
    requireField(field as keyof typeof paymentFieldLabels, true)
  })

  const verificationHints: string[] = []
  if (normalizedMode !== settings.paymentVerificationMode) {
    verificationHints.push(`当前平台仅支持 ${allowedModes.join(' / ')}，已建议切换为 ${normalizedMode}`)
  }
  if (normalizedMode === 'HMAC' && !hasText(settings.paymentWebhookSecret) && !hasText(settings.paymentApiSecret)) {
    verificationHints.push('HMAC 建议至少配置 Webhook Secret 或 API Secret')
  }
  if (normalizedMode === 'RSA' && !hasText(settings.paymentPublicKey)) {
    verificationHints.push('RSA 验签建议补充平台公钥')
  }
  if (normalizedMode === 'CERTIFICATE') {
    if (!hasText(settings.paymentPlatformCertificate)) {
      verificationHints.push('证书验签建议补充平台证书')
    }
    if (!hasText(settings.paymentCertificateSerialNo)) {
      verificationHints.push('证书验签建议补充证书序列号')
    }
  }
  if (normalizedMode === 'CUSTOM' && !hasText(settings.paymentWebhookSecret)) {
    verificationHints.push('自定义验签建议配置共享密钥')
  }

  const notifyUrl = (settings.paymentNotifyUrl || '').trim()
  const returnUrl = (settings.paymentReturnUrl || '').trim()
  const notifyMatches = !!paymentUnifiedUrls.value.notifyUrl && notifyUrl === paymentUnifiedUrls.value.notifyUrl
  const returnMatches = !!paymentUnifiedUrls.value.returnUrl && returnUrl === paymentUnifiedUrls.value.returnUrl
  const liveModeReady = !!settings.paymentEnabled && requiredMissingKeys.length === 0 && verificationHints.length === 0

  let summary = '支付已关闭，仅保留订单与 Mock 骨架'
  if (settings.paymentEnabled) {
    summary = liveModeReady
      ? '基础字段齐全，可继续做真实网关联调'
      : `仍缺 ${requiredMissingKeys.length + verificationHints.length} 项配置，建议先补齐后再切真实支付`
  } else if (settings.paymentMockEnabled) {
    summary = '真实支付关闭，但 Mock 模式可用于联调订单流转'
  }

  return {
    liveModeReady,
    notifyMatches,
    returnMatches,
    summary,
    requiredMissing: requiredMissingKeys.map(key => ({ key, label: paymentFieldLabels[key] || key })),
    verificationHints
  }
})

const applyOverview = (data: SuperAdminOverview) => Object.assign(overview, data)
const applySettings = (data: SuperAdminSettings) => {
  Object.assign(settings, data)
  settings.paymentVerificationMode = getNormalizedPaymentVerificationMode(
    settings.paymentProviderType,
    settings.paymentVerificationMode
  )
  authStore.multiUserEnabled = !!data.multiUserEnabled
}
const applyTablePreferences = (data: SuperAdminTablePreferences) => {
  tablePreferences.value = data || {}
}

const bytesToGb = (value?: number | null) => {
  const bytes = Number(value || 0)
  if (!bytes) return 0
  return Math.round((bytes / BYTES_PER_GB) * 100) / 100
}

const gbToBytes = (value?: number | null) => {
  const gb = Number(value || 0)
  if (!gb || gb < 0) return 0
  return Math.round(gb * BYTES_PER_GB)
}

const formatQuotaGb = (value?: number | null) => `${bytesToGb(value)} GB`

const settingsQuotaView = reactive({
  defaultUserQuotaGb: 0,
  defaultVipExtraQuotaGb: 0
})

watch(() => settings.defaultUserQuotaBytes, value => {
  settingsQuotaView.defaultUserQuotaGb = bytesToGb(value)
}, { immediate: true })

watch(() => settings.defaultVipExtraQuotaBytes, value => {
  settingsQuotaView.defaultVipExtraQuotaGb = bytesToGb(value)
}, { immediate: true })

watch(() => settings.paymentProviderType, value => {
  settings.paymentVerificationMode = getNormalizedPaymentVerificationMode(
    value,
    settings.paymentVerificationMode
  )
}, { immediate: true })

watch(() => newProvider.type, () => {
  handleNewProviderTypeChange()
}, { immediate: true })

const loadOverview = async () => {
  const { data } = await superAdminApi.getOverview()
  applyOverview(data)
}

const loadSettings = async () => {
  const { data } = await superAdminApi.getSettings()
  applySettings(data)
}

const loadTablePreferences = async () => {
  const { data } = await superAdminApi.getTablePreferences()
  applyTablePreferences(data)
}

const loadUsers = async () => {
  const { data } = await superAdminApi.getUsers(usersPage.page, usersPage.size, userKeyword.value)
  users.value = data.content.map(user => ({
    ...user,
    user: user.nickname || user.username,
    visible: !!user.multiUserVisible,
    usage: Number(user.storageUsedBytes || 0)
  }))
  Object.assign(usersPage, {
    page: data.page,
    size: data.size,
    totalElements: data.totalElements,
    totalPages: data.totalPages,
    first: data.first,
    last: data.last
  })
  sanitizeStorageSelections()
}

const loadLoginRecords = async () => {
  const { data } = await superAdminApi.getLoginRecords(selectedLoginRecordUserId.value, loginRecordsPage.page, loginRecordsPage.size)
  loginRecords.value = data.content.map(record => ({
    ...record,
    account: record.nickname || record.usernameSnapshot || '',
    source: `${record.ipAddress || ''} ${record.userAgent || ''}`.trim()
  }))
  Object.assign(loginRecordsPage, {
    page: data.page,
    size: data.size,
    totalElements: data.totalElements,
    totalPages: data.totalPages,
    first: data.first,
    last: data.last
  })
}

const loadOperationLogs = async () => {
  const { data } = await superAdminApi.getOperationLogs(selectedOperationLogUserId.value, operationLogsPage.page, operationLogsPage.size)
  operationLogs.value = data.content.map(log => ({
    ...log,
    account: log.nickname || log.username || log.operatorUsername || '',
    target: `${log.targetType || ''}${log.targetId != null ? ` #${log.targetId}` : ''}`.trim()
  }))
  Object.assign(operationLogsPage, {
    page: data.page,
    size: data.size,
    totalElements: data.totalElements,
    totalPages: data.totalPages,
    first: data.first,
    last: data.last
  })
}

const loadVipPlans = async () => {
  const { data } = await superAdminApi.getVipPlans()
  vipPlans.value = (data.vipPlans || []).map(plan => ({
    ...plan,
    name: plan.name,
    extraQuotaGb: bytesToGb(plan.extraQuotaBytes),
    priceYuan: Math.round((Number(plan.priceFen || 0) / 100) * 100) / 100
  }))
}

const normalizeVipOrder = (order: VipOrderSummary): VipOrderSummary => ({
  ...order,
  order: order.orderNo,
  amountYuan: Math.round((Number(order.amountFen || 0) / 100) * 100) / 100,
  paidAt: normalizeDateTimeLocal(order.paidAt),
  nextRenewalAt: normalizeDateTimeLocal(order.nextRenewalAt),
  expireAt: normalizeDateTimeLocal(order.expireAt)
})

const loadVipOrders = async () => {
  const { data } = await superAdminApi.getVipOrders(
    selectedVipOrderUserId.value,
    vipOrdersPage.page,
    vipOrdersPage.size,
    vipOrderAutoRenewOnly.value,
    vipOrderDueOnly.value
  )
  vipOrders.value = data.content.map(order => normalizeVipOrder(order))
  Object.assign(vipOrdersPage, {
    page: data.page,
    size: data.size,
    totalElements: data.totalElements,
    totalPages: data.totalPages,
    first: data.first,
    last: data.last
  })
}

const loadFocusedVipOrder = async () => {
  const orderNo = typeof route.query.focusOrderNo === 'string' ? route.query.focusOrderNo.trim() : ''
  if (!orderNo) {
    focusedVipOrder.value = null
    return
  }
  const { data } = await superAdminApi.getVipOrderByOrderNo(orderNo)
  focusedVipOrder.value = normalizeVipOrder(data)
}

const loadVipRenewalPreview = async () => {
  loadingVipRenewalPreview.value = true
  try {
    const { data } = await superAdminApi.getVipRenewalPreview(20)
    vipRenewalPreview.value = data
  } catch (error: any) {
    showMessage(error?.response?.data?.error || error?.message || '加载续费预演失败', 'error')
  } finally {
    loadingVipRenewalPreview.value = false
  }
}

const buildPaymentResultRoute = (order: { orderNo?: string | null; paymentProviderType?: string | null }) => ({
  name: 'PaymentResult',
  query: {
    orderNo: order.orderNo || '',
    providerType: order.paymentProviderType || settings.paymentProviderType || 'ALIPAY'
  }
})

const buildSuperAdminVipOrderRoute = (orderNo?: string | null) => ({
  name: 'AdminSuperAdmin',
  query: {
    ...route.query,
    tab: 'vipOrders',
    focusOrderNo: orderNo || undefined
  }
})

const clearFocusedVipOrder = () => {
  focusedVipOrder.value = null
  const nextQuery = { ...route.query }
  delete nextQuery.focusOrderNo
  router.replace({
    path: route.path,
    query: nextQuery
  })
}

const executeVipRenewals = async () => {
  if (!confirm('确认执行自动续费建单？该操作会创建续费子单；若支付配置已就绪，还会同步写入支付发起参数，但不会直接真实扣款。')) return
  executingVipRenewals.value = true
  try {
    const { data } = await superAdminApi.executeVipRenewals(20)
    vipRenewalExecution.value = data
    await Promise.all([loadVipOrders(), loadVipRenewalPreview(), loadUsers(), loadOverview()])
    showMessage(`自动续费建单完成：新增 ${data.createdCount} 条，跳过 ${data.skippedCount} 条`)
  } catch (error: any) {
    showMessage(error?.response?.data?.error || error?.message || '执行自动续费建单失败', 'error')
  } finally {
    executingVipRenewals.value = false
  }
}

const isSelectableStorageProvider = (providerId?: number | null) => {
  if (providerId == null) return true
  const provider = storageProviders.value.find(item => item.id === providerId)
  return !!provider && !!provider.enabled && !!provider.uploadSupported
}

const sanitizeStorageSelections = () => {
  if (!isSelectableStorageProvider(settings.defaultStorageProviderId)) {
    settings.defaultStorageProviderId = null
  }
  users.value = users.value.map(user => ({
    ...user,
    storageQuotaGb: bytesToGb(user.storageQuotaBytes),
    vipExtraQuotaGb: bytesToGb(user.vipExtraQuotaBytes),
    preferredStorageProviderName: isSelectableStorageProvider(user.preferredStorageProviderId)
      ? user.preferredStorageProviderName
      : null,
    preferredStorageProviderType: isSelectableStorageProvider(user.preferredStorageProviderId)
      ? user.preferredStorageProviderType
      : null,
    preferredStorageProviderId: isSelectableStorageProvider(user.preferredStorageProviderId)
      ? user.preferredStorageProviderId
      : null
  }))
}

const describeAssignedStorage = (user: UserAccountSummary) => {
  if (!user.preferredStorageProviderId) {
    return '系统默认上传存储'
  }
  const provider = storageProviders.value.find(item => item.id === user.preferredStorageProviderId)
  if (provider) {
    return `${provider.name} · ${storageTypeLabel(provider.type)}`
  }
  if (user.preferredStorageProviderName) {
    return user.preferredStorageProviderType
      ? `${user.preferredStorageProviderName} · ${storageTypeLabel(user.preferredStorageProviderType)}`
      : user.preferredStorageProviderName
  }
  return `ID ${user.preferredStorageProviderId}`
}

const loadStorageProviders = async () => {
  const { data } = await superAdminApi.getStorageProviders()
  storageProviders.value = data.storageProviders.map(provider => ({
    ...provider
  }))
  sanitizeStorageSelections()
}

const loadAll = async () => {
  loading.value = true
  try {
    const results = await Promise.allSettled([
      loadOverview(),
      loadSettings(),
      loadTablePreferences(),
      loadUsers(),
      loadStorageProviders(),
      loadLoginRecords(),
      loadOperationLogs(),
      loadVipPlans(),
      loadVipOrders(),
      loadFocusedVipOrder(),
      loadVipRenewalPreview()
    ])
    const moduleLabels = ['概览', '全局设置', '表格偏好', '用户管理', '存储配置', '登录记录', '操作记录', 'VIP套餐', 'VIP订单', '订单定位', '续费预演']
    const failedItems = results
      .map((result, index) => ({ result, index }))
      .filter(item => item.result.status === 'rejected')
      .map(item => ({
        label: moduleLabels[item.index],
        error: (item.result as PromiseRejectedResult).reason
      }))
    if (failedItems.length) {
      failedItems.forEach(item => {
        console.error(`[SuperAdmin] 模块加载失败: ${item.label}`, item.error)
      })

      const authFailure = failedItems.every(item => {
        const status = item.error?.response?.status
        return status === 401 || status === 403
      })
      if (authFailure) {
        showMessage('当前账号已无超级管理员权限，已返回后台首页', 'error')
        router.replace('/admin')
        return
      }

      const failedModules = failedItems.map(item => item.label)
      const firstDetail = failedItems[0]?.error?.response?.data?.error || failedItems[0]?.error?.message
      showMessage(
        firstDetail
          ? `以下模块加载失败：${failedModules.join('、')}（${firstDetail}）`
          : `以下模块加载失败：${failedModules.join('、')}`,
        'error'
      )
    }
  } catch (error: any) {
    showMessage(error?.response?.data?.error || error?.message || '加载超级管理员数据失败', 'error')
  } finally {
    loading.value = false
  }
}

const getTablePreference = (key: string): ConfigurableTablePreference => {
  const preference = tablePreferences.value[key]
  return {
    columnOrder: [...(preference?.columnOrder || [])],
    hiddenColumns: [...(preference?.hiddenColumns || [])],
    sortKey: preference?.sortKey || null,
    sortDirection: preference?.sortDirection || null
  }
}

const persistTablePreferences = async () => {
  const { data } = await superAdminApi.updateTablePreferences(tablePreferences.value)
  applyTablePreferences(data)
}

const updateTablePreference = (key: string, preference: ConfigurableTablePreference) => {
  tablePreferences.value = {
    ...tablePreferences.value,
    [key]: {
      columnOrder: [...(preference.columnOrder || [])],
      hiddenColumns: [...(preference.hiddenColumns || [])],
      sortKey: preference.sortKey || null,
      sortDirection: preference.sortDirection || null
    }
  }
  if (tablePreferenceSaveTimer) {
    clearTimeout(tablePreferenceSaveTimer)
  }
  tablePreferenceSaveTimer = setTimeout(async () => {
    try {
      await persistTablePreferences()
    } catch (error: any) {
      showMessage(error?.response?.data?.error || error?.message || '保存表格偏好失败', 'error')
    }
  }, 400)
}

watch(
  () => route.query.tab,
  value => {
    if (typeof value === 'string' && !validSuperAdminTabs.has(value as SuperAdminTabKey)) {
      changeActiveTab('overview')
    }
  },
  { immediate: true }
)

watch(
  () => route.query.focusOrderNo,
  async value => {
    const orderNo = typeof value === 'string' ? value.trim() : ''
    if (!orderNo) {
      focusedVipOrder.value = null
      return
    }
    try {
      await loadFocusedVipOrder()
      if (focusedVipOrder.value?.userId && selectedVipOrderUserId.value !== focusedVipOrder.value.userId) {
        selectedVipOrderUserId.value = focusedVipOrder.value.userId
      }
      if (activeTab.value !== 'vipOrders') {
        changeActiveTab('vipOrders')
        return
      }
      vipOrdersPage.page = 0
      await loadVipOrders()
      showMessage(`已定位订单 ${orderNo}`)
    } catch (error: any) {
      focusedVipOrder.value = null
      showMessage(error?.response?.data?.error || error?.message || '定位订单失败', 'error')
    }
  },
  { immediate: true }
)

const searchUsers = () => {
  usersPage.page = 0
  loadUsers()
}

const changeUsersPage = (page: number) => {
  if (page < 0 || page >= Math.max(usersPage.totalPages, 1)) return
  usersPage.page = page
  loadUsers()
}

const handleUsersPageSizeChange = () => {
  usersPage.page = 0
  loadUsers()
}

const handleLoginRecordUserChange = () => {
  loginRecordsPage.page = 0
  loadLoginRecords()
}

const changeLoginRecordsPage = (page: number) => {
  if (page < 0 || page >= Math.max(loginRecordsPage.totalPages, 1)) return
  loginRecordsPage.page = page
  loadLoginRecords()
}

const handleLoginRecordsPageSizeChange = () => {
  loginRecordsPage.page = 0
  loadLoginRecords()
}

const handleOperationLogUserChange = () => {
  operationLogsPage.page = 0
  loadOperationLogs()
}

const changeOperationLogsPage = (page: number) => {
  if (page < 0 || page >= Math.max(operationLogsPage.totalPages, 1)) return
  operationLogsPage.page = page
  loadOperationLogs()
}

const handleOperationLogsPageSizeChange = () => {
  operationLogsPage.page = 0
  loadOperationLogs()
}

const handleVipOrderUserChange = () => {
  vipOrdersPage.page = 0
  loadVipOrders()
}

const handleVipOrderFilterChange = () => {
  if (vipOrderDueOnly.value) {
    vipOrderAutoRenewOnly.value = true
  }
  vipOrdersPage.page = 0
  loadVipOrders()
}

const changeVipOrdersPage = (page: number) => {
  if (page < 0 || page >= Math.max(vipOrdersPage.totalPages, 1)) return
  vipOrdersPage.page = page
  loadVipOrders()
}

const saveSettings = async () => {
  savingSettings.value = true
  try {
    const payload = {
      ...settings,
      paymentVerificationMode: getNormalizedPaymentVerificationMode(
        settings.paymentProviderType,
        settings.paymentVerificationMode
      ),
      defaultUserQuotaBytes: gbToBytes(settingsQuotaView.defaultUserQuotaGb),
      defaultVipExtraQuotaBytes: gbToBytes(settingsQuotaView.defaultVipExtraQuotaGb),
      defaultStorageProviderId: settings.defaultStorageProviderId ?? null
    }
    const { data } = await superAdminApi.updateSettings(payload)
    applySettings(data)
    await Promise.all([loadOverview(), loadStorageProviders()])
    showMessage('全局设置已保存')
  } catch (error: any) {
    showMessage(error?.response?.data?.error || error?.message || '保存设置失败', 'error')
  } finally {
    savingSettings.value = false
  }
}

const sendTestEmail = async () => {
  sendingTestEmail.value = true
  try {
    const { data } = await superAdminApi.sendTestEmail(settings.emailTestRecipient || undefined)
    showMessage(data?.message || '测试邮件已发送')
  } catch (error: any) {
    showMessage(error?.response?.data?.error || error?.message || '发送测试邮件失败', 'error')
  } finally {
    sendingTestEmail.value = false
  }
}

const sendCustomEmail = async () => {
  if (!customEmail.recipient.trim()) {
    showMessage('请输入收件人邮箱', 'error')
    return
  }
  if (!customEmail.subject.trim()) {
    showMessage('请输入邮件主题', 'error')
    return
  }
  if (!customEmail.content.trim()) {
    showMessage('请输入邮件正文', 'error')
    return
  }
  sendingCustomEmail.value = true
  try {
    const { data } = await superAdminApi.sendEmail({
      recipient: customEmail.recipient.trim(),
      subject: customEmail.subject.trim(),
      content: customEmail.content,
      html: customEmail.html
    })
    showMessage(data?.message || '邮件已发送')
  } catch (error: any) {
    showMessage(error?.response?.data?.error || error?.message || '发送邮件失败', 'error')
  } finally {
    sendingCustomEmail.value = false
  }
}

const applyEmailPreset = () => {
  const preset = emailPreset.value
  settings.emailHost = preset.host
  settings.emailPort = preset.port
  settings.emailProtocol = preset.protocol
  settings.emailSslEnabled = preset.sslEnabled
  settings.emailStarttlsEnabled = preset.starttlsEnabled
  showMessage(`已应用 ${settings.emailProviderType} 推荐配置`)
}

const applyPaymentPreset = () => {
  const preset = paymentPreset.value
  settings.paymentApiBaseUrl = preset.apiBaseUrl
  settings.paymentVerificationMode = getNormalizedPaymentVerificationMode(
    settings.paymentProviderType,
    settings.paymentVerificationMode
  )
  showMessage(`已应用 ${settings.paymentProviderType} 推荐配置`)
}

const applyStoragePreset = () => {
  const preset = storagePreset.value
  newProvider.endpoint = shouldShowStorageField(newProvider.type, 'endpoint') ? preset.endpoint : ''
  newProvider.bucketName = shouldShowStorageField(newProvider.type, 'bucketName') ? preset.bucketName : ''
  newProvider.baseDirectory = shouldShowStorageField(newProvider.type, 'baseDirectory') ? preset.baseDirectory : ''
  if (!String(newProvider.configJson || '').trim()) {
    newProvider.configJson = preset.configJson
  }
  showMessage(`已应用 ${storageTypeLabel(newProvider.type)} 推荐配置`)
}

const fillProviderPresetByVisibility = (provider: Partial<StorageProviderSummary>) => {
  const preset = getStoragePreset(provider.type)
  if (shouldShowStorageField(provider.type, 'endpoint') && !String(provider.endpoint || '').trim()) {
    provider.endpoint = preset.endpoint
  }
  if (shouldShowStorageField(provider.type, 'bucketName') && !String(provider.bucketName || '').trim()) {
    provider.bucketName = preset.bucketName
  }
  if (shouldShowStorageField(provider.type, 'baseDirectory') && !String(provider.baseDirectory || '').trim()) {
    provider.baseDirectory = preset.baseDirectory
  }
  if (!String(provider.configJson || '').trim()) {
    provider.configJson = preset.configJson
  }
}

const handleNewProviderTypeChange = () => {
  clearHiddenStorageFields(newProvider)
  fillProviderPresetByVisibility(newProvider)
}

const handleProviderTypeChange = (provider: Partial<StorageProviderSummary>) => {
  clearHiddenStorageFields(provider)
  fillProviderPresetByVisibility(provider)
}

const applyProviderPreset = (provider: Partial<StorageProviderSummary>) => {
  const preset = getStoragePreset(provider.type)
  provider.endpoint = shouldShowStorageField(provider.type, 'endpoint') ? preset.endpoint : ''
  provider.bucketName = shouldShowStorageField(provider.type, 'bucketName') ? preset.bucketName : ''
  provider.baseDirectory = shouldShowStorageField(provider.type, 'baseDirectory') ? preset.baseDirectory : ''
  if (!String(provider.configJson || '').trim()) {
    provider.configJson = preset.configJson
  }
  showMessage(`已为 ${provider.name || storageTypeLabel(provider.type)} 套用推荐配置`)
}

const validateProviderDraft = (provider: Partial<StorageProviderSummary>) => {
  if (!String(provider.name || '').trim()) {
    return '请输入存储名称'
  }
  const assessment = getProviderAssessment(provider)
  if (assessment.missingLabels.length) {
    return `当前存储缺少：${assessment.missingLabels.join('、')}`
  }
  return ''
}

const buildUnifiedPaymentUrls = () => paymentUnifiedUrls.value

const fillPaymentCallbackUrls = () => {
  const { notifyUrl, returnUrl } = buildUnifiedPaymentUrls()
  settings.paymentNotifyUrl = notifyUrl
  settings.paymentReturnUrl = returnUrl
  showMessage(`已填充 ${settings.paymentProviderType} 统一回调地址`)
}

const copyPaymentCallbackUrls = async () => {
  const { notifyUrl, returnUrl } = buildUnifiedPaymentUrls()
  const text = `Notify: ${notifyUrl}\nReturn: ${returnUrl}`
  try {
    await navigator.clipboard.writeText(text)
    showMessage('统一回调地址已复制到剪贴板')
  } catch (error) {
    settings.paymentNotifyUrl = notifyUrl
    settings.paymentReturnUrl = returnUrl
    showMessage('复制失败，已自动填充到表单中', 'error')
  }
}

const runLegacyMigration = async () => {
  runningMigration.value = true
  try {
    const { data } = await superAdminApi.runLegacyMigration()
    migrationSummary.value = data || null
    await Promise.all([loadOverview(), loadUsers(), loadStorageProviders(), loadSettings(), loadLoginRecords(), loadOperationLogs(), loadVipPlans(), loadVipOrders()])
    showMessage(data?.message || '旧数据迁移已执行')
  } catch (error: any) {
    showMessage(error?.response?.data?.error || error?.message || '执行旧数据迁移失败', 'error')
  } finally {
    runningMigration.value = false
  }
}

const saveUser = async (user: UserAccountSummary) => {
  savingUserId.value = user.id
  try {
    if ((user.pendingPassword || user.pendingPasswordConfirm) && user.pendingPassword !== user.pendingPasswordConfirm) {
      throw new Error('两次输入的新密码不一致')
    }
    const { data } = await superAdminApi.updateUser(user.id, {
      nickname: user.nickname,
      phone: user.phone,
      email: user.email,
      role: user.role,
      status: user.status,
      projectNameZh: user.projectNameZh,
      projectNameEn: user.projectNameEn,
      storageQuotaBytes: gbToBytes((user as any).storageQuotaGb),
      vipExtraQuotaBytes: gbToBytes((user as any).vipExtraQuotaGb),
      currentVipPlanId: user.currentVipPlanId,
      vipExpireAt: normalizeDateTimeLocal(user.vipExpireAt),
      preferredStorageProviderId: user.preferredStorageProviderId,
      multiUserVisible: user.multiUserVisible,
      phoneVerified: user.phoneVerified,
      emailVerified: user.emailVerified
    })
    if (user.pendingPassword) {
      await superAdminApi.resetUserPassword(user.id, user.pendingPassword)
    }
    users.value = users.value.map(item => item.id === user.id ? {
      ...data,
      storageQuotaGb: bytesToGb(data.storageQuotaBytes),
      vipExtraQuotaGb: bytesToGb(data.vipExtraQuotaBytes),
      vipExpireAt: normalizeDateTimeLocal(data.vipExpireAt),
      pendingPassword: '',
      pendingPasswordConfirm: ''
    } as any : item)
    await loadOverview()
    showMessage(`用户 ${data.username} 已更新${user.pendingPassword ? '，密码已重置' : ''}`)
  } catch (error: any) {
    showMessage(error?.response?.data?.error || error?.message || '保存用户失败', 'error')
  } finally {
    savingUserId.value = null
  }
}

const resetNewProvider = () => {
  Object.assign(newProvider, {
    name: '',
    type: 'LOCAL',
    enabled: true,
    isDefault: false,
    priority: 100,
    endpoint: '',
    bucketName: '',
    baseDirectory: '',
    configJson: ''
  })
}

const createProvider = async () => {
  const validationMessage = validateProviderDraft(newProvider)
  if (validationMessage) {
    showMessage(validationMessage, 'error')
    return
  }
  savingProviderId.value = 0
  try {
    await superAdminApi.createStorageProvider(toStorageProviderPayload(newProvider))
    resetNewProvider()
    await Promise.all([loadOverview(), loadStorageProviders(), loadSettings()])
    showMessage('存储提供者已创建')
  } catch (error: any) {
    showMessage(error?.response?.data?.error || error?.message || '创建存储提供者失败', 'error')
  } finally {
    savingProviderId.value = null
  }
}

const resetNewVipPlan = () => {
  Object.assign(newVipPlan, {
    code: '',
    name: '',
    description: '',
    extraQuotaGb: 0,
    durationDays: 30,
    priceYuan: 0,
    enabled: true,
    sortOrder: 100
  })
}

const createVipPlan = async () => {
  savingVipPlanId.value = 0
  try {
    await superAdminApi.createVipPlan({
      code: newVipPlan.code,
      name: newVipPlan.name,
      description: newVipPlan.description,
      extraQuotaBytes: gbToBytes(newVipPlan.extraQuotaGb),
      durationDays: newVipPlan.durationDays,
      priceFen: Math.round(Number(newVipPlan.priceYuan || 0) * 100),
      enabled: !!newVipPlan.enabled,
      sortOrder: newVipPlan.sortOrder
    } as any)
    resetNewVipPlan()
    await Promise.all([loadVipPlans(), loadUsers(), loadOverview()])
    showMessage('VIP 套餐已创建')
  } catch (error: any) {
    showMessage(error?.response?.data?.error || error?.message || '创建 VIP 套餐失败', 'error')
  } finally {
    savingVipPlanId.value = null
  }
}

const saveVipPlan = async (plan: VipPlanSummary) => {
  savingVipPlanId.value = plan.id
  try {
    await superAdminApi.updateVipPlan(plan.id, {
      code: plan.code,
      name: plan.name,
      description: plan.description,
      extraQuotaBytes: gbToBytes(plan.extraQuotaGb),
      durationDays: plan.durationDays,
      priceFen: Math.round(Number(plan.priceYuan || 0) * 100),
      enabled: plan.enabled,
      sortOrder: plan.sortOrder
    } as any)
    await Promise.all([loadVipPlans(), loadUsers(), loadOverview()])
    showMessage(`VIP 套餐 ${plan.name} 已更新`)
  } catch (error: any) {
    showMessage(error?.response?.data?.error || error?.message || '保存 VIP 套餐失败', 'error')
  } finally {
    savingVipPlanId.value = null
  }
}

const saveProvider = async (provider: StorageProviderSummary) => {
  const validationMessage = validateProviderDraft(provider)
  if (validationMessage) {
    showMessage(validationMessage, 'error')
    return
  }
  savingProviderId.value = provider.id
  try {
    const { data } = await superAdminApi.updateStorageProvider(provider.id, toStorageProviderPayload(provider))
    storageProviders.value = storageProviders.value.map(item => item.id === provider.id ? { ...item, ...data } : item)
    await Promise.all([loadOverview(), loadStorageProviders(), loadSettings()])
    showMessage(`存储提供者 ${data.name} 已更新`)
  } catch (error: any) {
    showMessage(error?.response?.data?.error || error?.message || '保存存储提供者失败', 'error')
  } finally {
    savingProviderId.value = null
  }
}

const createVipOrder = async () => {
  savingVipOrderId.value = 0
  try {
    await superAdminApi.createVipOrder({
      userId: newVipOrder.userId,
      vipPlanId: newVipOrder.vipPlanId,
      amountFen: Math.round(Number(newVipOrder.amountYuan || 0) * 100),
      status: newVipOrder.status,
      source: newVipOrder.source,
      paidAt: normalizeDateTimeLocal(newVipOrder.paidAt),
      nextRenewalAt: normalizeDateTimeLocal(newVipOrder.nextRenewalAt),
      autoRenewEnabled: !!newVipOrder.autoRenewEnabled,
      expireAt: normalizeDateTimeLocal(newVipOrder.expireAt),
      remark: newVipOrder.remark
    } as any)
    Object.assign(newVipOrder, { userId: undefined, vipPlanId: undefined, amountYuan: 0, status: 'PAID', source: 'MANUAL', paidAt: null, nextRenewalAt: null, autoRenewEnabled: false, expireAt: null, remark: '' })
    await Promise.all([loadVipOrders(), loadUsers(), loadOverview(), loadVipRenewalPreview()])
    showMessage('VIP 订单已创建')
  } catch (error: any) {
    showMessage(error?.response?.data?.error || error?.message || '创建 VIP 订单失败', 'error')
  } finally {
    savingVipOrderId.value = null
  }
}

const saveVipOrder = async (order: VipOrderSummary) => {
  savingVipOrderId.value = order.id
  try {
    await superAdminApi.updateVipOrder(order.id, {
      amountFen: Math.round(Number(order.amountYuan || 0) * 100),
      status: order.status,
      source: order.source,
      paidAt: normalizeDateTimeLocal(order.paidAt),
      nextRenewalAt: normalizeDateTimeLocal(order.nextRenewalAt),
      autoRenewEnabled: !!order.autoRenewEnabled,
      expireAt: normalizeDateTimeLocal(order.expireAt),
      remark: order.remark
    } as any)
    await Promise.all([loadVipOrders(), loadUsers(), loadOverview(), loadVipRenewalPreview()])
    showMessage(`VIP 订单 ${order.orderNo} 已更新`)
  } catch (error: any) {
    showMessage(error?.response?.data?.error || error?.message || '保存 VIP 订单失败', 'error')
  } finally {
    savingVipOrderId.value = null
  }
}

const previewVipOrderPayment = async (order: VipOrderSummary) => {
  previewingVipOrderId.value = order.id
  try {
    const { data } = await superAdminApi.previewVipOrderPayment(order.id)
    paymentPreview.value = data
    showMessage(`已生成订单 ${order.orderNo} 的支付预览`)
  } catch (error: any) {
    showMessage(error?.response?.data?.error || error?.message || '生成支付预览失败', 'error')
  } finally {
    previewingVipOrderId.value = null
  }
}

const previewVipOrderRefund = async (order: VipOrderSummary) => {
  const defaultAmountFen = Number(order.amountFen || 0)
  const input = window.prompt('请输入退款预览金额（元），留空则按全额退款', defaultAmountFen ? String((defaultAmountFen / 100).toFixed(2)) : '0.00')
  if (input === null) return
  const refundAmountFen = input.trim()
    ? Math.max(0, Math.round(Number(input.trim()) * 100))
    : defaultAmountFen
  try {
    const { data } = await superAdminApi.previewVipOrderRefund(order.id, refundAmountFen)
    paymentRefundPreview.value = data
    showMessage(`已生成订单 ${order.orderNo} 的退款骨架预览`)
  } catch (error: any) {
    showMessage(error?.response?.data?.error || error?.message || '生成退款预览失败', 'error')
  }
}

const initiateVipOrderPayment = async (order: VipOrderSummary) => {
  initiatingVipOrderId.value = order.id
  try {
    const { data } = await superAdminApi.initiateVipOrderPayment(order.id)
    paymentInitiation.value = data
    showMessage(`已生成订单 ${order.orderNo} 的支付发起骨架`)
  } catch (error: any) {
    showMessage(error?.response?.data?.error || error?.message || '发起支付失败', 'error')
  } finally {
    initiatingVipOrderId.value = null
  }
}

const mockPayVipOrder = async (order: VipOrderSummary) => {
  mockingVipOrderId.value = order.id
  try {
    await superAdminApi.mockPayVipOrder(order.id)
    await Promise.all([loadVipOrders(), loadUsers(), loadOverview(), loadVipRenewalPreview()])
    if (paymentPreview.value?.orderId === order.id) {
      paymentPreview.value = await superAdminApi.previewVipOrderPayment(order.id).then(res => res.data)
    }
    showMessage(`订单 ${order.orderNo} 已通过 Mock 支付标记为已支付`)
  } catch (error: any) {
    showMessage(error?.response?.data?.error || error?.message || 'Mock 支付失败', 'error')
  } finally {
    mockingVipOrderId.value = null
  }
}

const cancelVipOrder = async (order: VipOrderSummary) => {
  if (!confirm(`确认将订单 ${order.orderNo} 标记为已取消？`)) return
  cancellingVipOrderId.value = order.id
  try {
    await superAdminApi.cancelVipOrder(order.id, 'SUPER_ADMIN_MANUAL_CANCEL')
    await Promise.all([loadVipOrders(), loadUsers(), loadOverview(), loadVipRenewalPreview()])
    showMessage(`订单 ${order.orderNo} 已标记为取消`)
  } catch (error: any) {
    showMessage(error?.response?.data?.error || error?.message || '取消订单失败', 'error')
  } finally {
    cancellingVipOrderId.value = null
  }
}

const refundVipOrder = async (order: VipOrderSummary) => {
  const defaultAmountFen = Number(order.amountFen || 0)
  const input = window.prompt('请输入退款金额（元），留空则按全额退款', defaultAmountFen ? String((defaultAmountFen / 100).toFixed(2)) : '0.00')
  if (input === null) return
  const refundAmountFen = input.trim()
    ? Math.max(0, Math.round(Number(input.trim()) * 100))
    : defaultAmountFen
  refundingVipOrderId.value = order.id
  try {
    const { data } = await superAdminApi.refundVipOrder(order.id, refundAmountFen, 'SUPER_ADMIN_MANUAL_REFUND')
    await Promise.all([loadVipOrders(), loadUsers(), loadOverview(), loadVipRenewalPreview()])
    showMessage(data?.message || `订单 ${order.orderNo} 退款状态已更新`)
  } catch (error: any) {
    showMessage(error?.response?.data?.error || error?.message || '退款订单失败', 'error')
  } finally {
    refundingVipOrderId.value = null
  }
}

const confirmVipOrderRefund = async (order: VipOrderSummary) => {
  const defaultAmountFen = Number(order.refundAmountFen || order.amountFen || 0)
  const input = window.prompt('请输入确认退款金额（元），留空则沿用当前退款金额', defaultAmountFen ? String((defaultAmountFen / 100).toFixed(2)) : '0.00')
  if (input === null) return
  const refundAmountFen = input.trim()
    ? Math.max(0, Math.round(Number(input.trim()) * 100))
    : defaultAmountFen
  if (!confirm(`确认将订单 ${order.orderNo} 标记为退款成功？`)) return
  confirmingVipOrderRefundId.value = order.id
  try {
    const { data } = await superAdminApi.confirmVipOrderRefund(order.id, refundAmountFen, 'SUPER_ADMIN_MANUAL_REFUND_CONFIRM_SUCCESS')
    await Promise.all([loadVipOrders(), loadUsers(), loadOverview(), loadVipRenewalPreview()])
    showMessage(data?.message || `订单 ${order.orderNo} 已确认退款成功`)
  } catch (error: any) {
    showMessage(error?.response?.data?.error || error?.message || '确认退款失败', 'error')
  } finally {
    confirmingVipOrderRefundId.value = null
  }
}

const markVipOrderRefundFailed = async (order: VipOrderSummary) => {
  const remark = window.prompt('请输入退款失败原因，留空则使用默认备注', 'SUPER_ADMIN_MANUAL_REFUND_FAILED')
  if (remark === null) return
  if (!confirm(`确认将订单 ${order.orderNo} 标记为退款失败？`)) return
  failingVipOrderRefundId.value = order.id
  try {
    const { data } = await superAdminApi.markVipOrderRefundFailed(order.id, remark.trim() || 'SUPER_ADMIN_MANUAL_REFUND_FAILED')
    await Promise.all([loadVipOrders(), loadUsers(), loadOverview(), loadVipRenewalPreview()])
    showMessage(data?.message || `订单 ${order.orderNo} 已标记为退款失败`)
  } catch (error: any) {
    showMessage(error?.response?.data?.error || error?.message || '标记退款失败失败', 'error')
  } finally {
    failingVipOrderRefundId.value = null
  }
}

const formatBytes = (value?: number | null) => {
  const bytes = Number(value || 0)
  if (!bytes) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  let size = bytes
  let index = 0
  while (size >= 1024 && index < units.length - 1) {
    size /= 1024
    index += 1
  }
  return `${size.toFixed(size >= 10 || index === 0 ? 0 : 1)} ${units[index]}`
}

const formatDate = (value?: string | null) => {
  if (!value) return '暂无'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString()
}

const normalizeDateTimeLocal = (value?: string | null) => {
  if (!value) return null
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  const pad = (num: number) => String(num).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`
}

const operationTypeLabel = (method?: string | null) => {
  switch (method) {
    case 'UPLOAD':
      return '上传'
    case 'DELETE':
      return '删除'
    case 'UPDATE':
      return '修改'
    case 'SCAN_START':
      return '开始扫描'
    case 'SCAN_RESUME':
      return '继续扫描'
    case 'SCAN_PAUSE':
      return '暂停扫描'
    case 'SCAN_CANCEL':
      return '取消扫描'
    default:
      return method || '未知操作'
  }
}

const loginMethodLabel = (method?: string | null) => {
  switch (method) {
    case 'PHONE_PASSWORD':
      return '手机号+密码'
    case 'EMAIL_PASSWORD':
      return '邮箱+密码'
    case 'SMS_CODE':
      return '短信验证码'
    case 'EMAIL_CODE':
      return '邮箱验证码'
    case 'USERNAME_PASSWORD':
    default:
      return '账号+密码'
  }
}

onMounted(loadAll)
</script>
