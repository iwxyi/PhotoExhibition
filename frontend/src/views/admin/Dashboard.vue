<template>
  <div class="min-h-screen admin-shell text-white">
    <!-- 顶部导航 -->
    <nav class="glass-toolbar">
      <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div class="flex justify-between h-16">
          <div class="flex items-center">
            <div class="space-y-1">
              <h1 class="text-xl font-light tracking-wide">管理后台</h1>
              <AdminSectionTabs />
            </div>
          </div>
          <div class="flex items-center space-x-4">
            <button
              @click="themeStore.toggleTheme"
              class="p-2 rounded-full bg-black/20 hover:bg-black/35 border border-white/15 transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-300/80"
              :title="themeStore.isDark ? '切换为浅色模式' : '切换为深色模式'"
            >
              <svg v-if="!themeStore.isDark" class="w-4 h-4 text-amber-200" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M20.354 15.354A9 9 0 018.646 3.646 9.003 9.003 0 0012 21a9.003 9.003 0 008.354-5.646z" />
              </svg>
              <svg v-else class="w-4 h-4 text-sky-200" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 3v1m0 16v1m9-9h-1M4 12H3m15.364 6.364l-.707-.707M6.343 6.343l-.707-.707m12.728 0l-.707.707M6.343 17.657l-.707.707M16 12a4 4 0 11-8 0 4 4 0 018 0z" />
              </svg>
            </button>
            <span class="text-gray-200/80 text-sm">欢迎，{{ authStore.username }}</span>
            <button
              @click="handleLogout"
              class="px-3 py-1.5 text-xs bg-gray-900/70 hover:bg-gray-800 rounded-lg transition-colors border border-white/15"
            >
              退出登录
            </button>
            <router-link
              to="/"
              class="px-3 py-1.5 text-xs bg-blue-600 hover:bg-blue-700 text-white rounded-lg transition-colors border border-blue-500/30"
            >
              返回首页
            </router-link>
          </div>
        </div>
      </div>
    </nav>

    <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8 space-y-8">
      <!-- Hero 区域 -->
      <section class="admin-hero">
        <div class="admin-hero-gradient"></div>
        <div class="admin-hero-particle admin-hero-particle--small"></div>
        <div class="admin-hero-particle admin-hero-particle--medium"></div>
        <div class="admin-hero-particle admin-hero-particle--large"></div>
        <div class="admin-hero-content">
          <div class="space-y-4">
            <div class="admin-hero-metric">
              <span class="admin-hero-metric-dot"></span>
              <span>Photo Exhibition · Admin</span>
            </div>
            <div>
              <h2 class="text-2xl sm:text-3xl lg:text-4xl font-light tracking-wide mb-2">
                {{ authStore.projectDisplayName || '光忆集' }}
              </h2>
              <p class="text-sm sm:text-base text-slate-300/90 max-w-xl">
                统一管理相册、标签、人脸与文件系统，配合自动扫描与 AI 能力，让你的作品集始终保持有序而精彩。
              </p>
            </div>
            <div class="grid grid-cols-2 sm:grid-cols-3 gap-4 pt-1">
        <router-link
          to="/admin/albums"
                class="admin-hero-stat hover:bg-slate-900/70 transition-colors cursor-pointer"
                style="padding: 14px 16px;"
        >
                <div class="admin-hero-stat-label">相册</div>
                <div class="admin-hero-stat-value">{{ stats.albums }}</div>
        </router-link>
        <router-link
          to="/admin/photos"
                class="admin-hero-stat hover:bg-slate-900/70 transition-colors cursor-pointer"
                style="padding: 14px 16px;"
        >
                <div class="admin-hero-stat-label">照片</div>
                <div class="admin-hero-stat-value">{{ stats.photos }}</div>
        </router-link>
        <router-link
          to="/admin/persons"
                class="admin-hero-stat hover:bg-slate-900/70 transition-colors cursor-pointer"
                style="padding: 14px 16px;"
        >
                <div class="admin-hero-stat-label">人物</div>
                <div class="admin-hero-stat-value">{{ stats.persons }}</div>
        </router-link>
            </div>
          </div>
          <div class="admin-hero-secondary space-y-3">
            <div class="flex items-center justify-between gap-4">
              <div class="text-xs text-slate-300/90">
                <div class="font-medium mb-1">最近扫描状态</div>
                <div class="space-y-0.5">
                  <p>状态：<span class="text-sky-300">{{ scanStatus }}</span></p>
                  <p v-if="runningTaskCount > 0">运行中：<span class="text-emerald-300">{{ runningTaskCount }}</span></p>
                  <p v-if="queueCount > 0">队列：<span class="text-sky-300">{{ queueCount }}</span></p>
                  <p v-if="queuedOwnerCount > 0">排队用户：<span class="text-sky-300">{{ queuedOwnerCount }}</span></p>
                  <p v-if="queuedOwnerSummaryText">队列分布：<span class="text-slate-300">{{ queuedOwnerSummaryText }}</span></p>
                  <p v-if="pausedTaskCount > 0">暂停：<span class="text-amber-300">{{ pausedTaskCount }}</span></p>
                  <p v-if="runningTaskSummaryText">运行分布：<span class="text-slate-300">{{ runningTaskSummaryText }}</span></p>
                  <p>进度：
                    <span
                      class="text-sky-300 cursor-pointer hover:underline hover:text-sky-200 transition-colors"
                      @click="openSkippedFilesModal"
                      title="点击查看跳过文件详情"
                    >{{ scanProgressText }}</span>
                  </p>
                  <p>时间：<span class="text-slate-300">{{ lastScanTime || '—' }}</span></p>
                </div>
              </div>
            <div class="flex flex-col items-end gap-2 min-w-[220px]">
              <div
                v-if="authStore.isSuperAdmin && scanDisabledReason"
                class="w-full rounded-lg border border-amber-400/20 bg-amber-500/10 px-3 py-2 text-[11px] leading-5 text-amber-100"
              >
                {{ scanDisabledReason }}
              </div>
              <select
                v-if="authStore.isSuperAdmin && scanProviderOptions.length"
                v-model="selectedScanProviderId"
                class="w-full px-3 py-1.5 text-xs rounded-lg bg-slate-950/70 border border-white/10 text-slate-200"
              >
                <option :value="null">默认存储</option>
                <option
                  v-for="provider in scanProviderOptions"
                  :key="provider.id"
                  :value="provider.id"
                  :disabled="provider.scanSupported === false"
                >
                  {{ provider.name }} · {{ storageTypeLabel(provider.type) }}{{ provider.scanSupported === false ? '（不可扫描）' : '' }}
                </option>
              </select>
              <button
                @click="triggerScan"
                :disabled="scanning || !scanActionSupported"
                class="w-full px-3 py-1.5 text-xs bg-blue-600 hover:bg-blue-700 text-white rounded-lg transition-colors disabled:opacity-60 border border-blue-500/30"
              >
                {{ scanning ? '扫描中…' : '立即触发扫描' }}
              </button>
            </div>
            </div>
          </div>
        </div>
      </section>

        <!-- 数据管理 -->
      <div class="grid grid-cols-1 gap-4">
        <div class="glass-panel p-4 admin-card-animate admin-card-4">
          <h2 class="text-lg font-light mb-3">数据管理</h2>
          <div class="grid grid-cols-2 gap-2">
            <router-link
              to="/admin/tags"
            class="block px-3 py-2 bg-gray-900/60 hover:bg-gray-700 rounded-lg transition-colors border border-white/10 text-sm"
            >
              <div class="text-center">标签管理</div>
              <div class="text-xs text-gray-400 text-center mt-0.5">{{ stats.tags }} 个</div>
            </router-link>
            <router-link
              to="/admin/file-browser"
            class="block px-3 py-2 bg-gray-900/60 hover:bg-gray-700 rounded-lg transition-colors text-center border border-white/10 text-sm"
            >
              文件浏览器
            </router-link>
            <button
              @click="cleanupOrphaned"
              :disabled="isCleaningUp"
              class="block px-3 py-2 bg-gray-900/60 hover:bg-gray-700 rounded-lg transition-colors text-center border border-white/10 text-sm disabled:opacity-50 disabled:cursor-not-allowed"
            >
              <div class="text-center">{{ isCleaningUp ? '清理中...' : '清理删除残留' }}</div>
            </button>
          <router-link
            to="/admin/theme"
            class="block px-3 py-2 bg-gray-900/60 hover:bg-gray-700 rounded-lg transition-colors text-center border border-white/10 text-sm"
          >
            主题与风格
          </router-link>
          <router-link
            to="/admin/settings"
            class="block px-3 py-2 bg-gray-900/60 hover:bg-gray-700 rounded-lg transition-colors text-center border border-white/10 text-sm"
          >
            系统设置
          </router-link>
          </div>
        </div>
      </div>
    </div>

    <!-- 跳过文件详情弹窗 -->
    <div
      v-if="showSkippedModal"
      class="fixed inset-0 z-50 flex items-center justify-center p-4"
      @click.self="closeSkippedModal"
    >
      <div class="absolute inset-0 bg-black/60 backdrop-blur-sm"></div>
      <div class="relative w-full max-w-4xl max-h-[80vh] flex flex-col bg-slate-900 border border-slate-700 rounded-2xl shadow-2xl overflow-hidden">
        <!-- 弹窗头部 -->
        <div class="flex items-center justify-between px-5 py-4 border-b border-slate-700 shrink-0">
          <div>
            <h3 class="text-base font-medium text-white">扫描异常文件详情</h3>
            <p class="text-xs text-slate-400 mt-0.5">
              <template v-if="scanning">
                <span class="text-cyan-400 animate-pulse">扫描进行中，数据实时更新…</span>
              </template>
              <template v-else>
                共 {{ scanTotal }} 个文件，正常 {{ scanTotal - skippedFiles.length }} 个，异常 {{ skippedFiles.length }} 个
                <template v-if="skippedFiles.length > 0">
                  （重复 {{ skippedFiles.filter(f => f.reason === '内容重复').length }}，
                  空文件 {{ skippedFiles.filter(f => f.reason === '文件为空').length }}，
                  其他 {{ skippedFiles.filter(f => f.reason !== '内容重复' && f.reason !== '文件为空').length }}）
                </template>
              </template>
            </p>
          </div>
          <button @click="closeSkippedModal" class="p-1.5 rounded-lg hover:bg-slate-700 text-slate-400 hover:text-white transition-colors">
            <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"/>
            </svg>
          </button>
        </div>
        <!-- 表格内容 -->
        <div class="overflow-auto flex-1">
          <div v-if="loadingSkipped" class="flex items-center justify-center py-16 text-slate-400 text-sm">
            加载中…
          </div>
          <div v-else-if="skippedFiles.length === 0" class="flex items-center justify-center py-16 text-slate-400 text-sm">
            无跳过文件，进度数据完全一致
          </div>
          <table v-else class="w-full text-xs text-slate-200 border-collapse">
            <thead class="sticky top-0 bg-slate-800 text-slate-400 uppercase tracking-wide">
              <tr>
                <th class="px-4 py-2.5 text-left w-12">#</th>
                <th class="px-4 py-2.5 text-left">相对路径</th>
                <th class="px-4 py-2.5 text-left w-28">原因</th>
                <th class="px-4 py-2.5 text-right w-24">文件大小</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="f in skippedFiles"
                :key="f.index"
                class="border-t border-slate-800 hover:bg-slate-800/50 transition-colors"
              >
                <td class="px-4 py-2 text-slate-500">{{ f.index }}</td>
                <td class="px-4 py-2 font-mono text-slate-300 break-all">{{ f.relativePath }}</td>
                <td class="px-4 py-2">
                  <span
                    class="cursor-help border-b border-dashed"
                    :class="{
                      'text-blue-400 border-blue-400/50': f.reason === '内容重复',
                      'text-slate-400 border-slate-400/50': f.reason === '文件为空',
                      'text-amber-400 border-amber-400/50': f.reason !== '内容重复' && f.reason !== '文件为空'
                    }"
                    :title="f.detail"
                  >{{ f.reason }}</span>
                </td>
                <td class="px-4 py-2 text-right text-slate-400 tabular-nums">{{ formatFileSize(f.fileSizeBytes) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <div
        v-if="showTaskDetailModal"
        class="fixed inset-0 z-50 flex items-center justify-center p-4"
        @click.self="closeTaskDetailModal"
      >
        <div class="absolute inset-0 bg-black/60 backdrop-blur-sm"></div>
        <div class="relative w-full max-w-5xl max-h-[85vh] flex flex-col bg-slate-900 border border-slate-700 rounded-2xl shadow-2xl overflow-hidden">
          <div class="flex items-center justify-between px-5 py-4 border-b border-slate-700 shrink-0">
            <div>
              <h3 class="text-base font-medium text-white">
                扫描任务详情
                <span v-if="selectedTaskDetail" class="text-sky-300 ml-2">#{{ selectedTaskDetail.id }}</span>
              </h3>
              <p class="text-xs text-slate-400 mt-0.5">
                单任务视角查看恢复游标、检查点与实时状态。
              </p>
            </div>
            <div class="flex items-center gap-2">
              <button
                @click="refreshSelectedTaskDetail"
                :disabled="loadingTaskDetail || !selectedTaskDetail"
                class="px-3 py-1.5 text-xs bg-slate-800 hover:bg-slate-700 rounded-lg transition-colors border border-white/10 disabled:opacity-60"
              >
                {{ loadingTaskDetail ? '刷新中…' : '刷新详情' }}
              </button>
              <button @click="closeTaskDetailModal" class="p-1.5 rounded-lg hover:bg-slate-700 text-slate-400 hover:text-white transition-colors">
                <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"/>
                </svg>
              </button>
            </div>
          </div>

          <div class="overflow-auto flex-1 p-5" v-if="selectedTaskDetail">
            <div class="grid grid-cols-1 xl:grid-cols-3 gap-4 text-sm">
              <div class="rounded-xl border border-slate-800 bg-slate-950/40 p-4 space-y-2">
                <div class="text-slate-400 text-xs">基础信息</div>
                <div class="text-white">{{ taskTypeLabel(selectedTaskDetail.taskType) }} · {{ taskStatusLabel(selectedTaskDetail.status) }}</div>
                <div class="text-slate-300 break-all">根路径：{{ selectedTaskDetail.rootPathDisplay || selectedTaskDetail.rootPath || '—' }}</div>
                <div class="text-slate-300">优先级：{{ selectedTaskDetail.priority ?? '—' }}</div>
                <div class="text-slate-300">归属：{{ selectedTaskDetail.ownerLabel || '系统任务' }}</div>
                <div class="text-slate-300">用户 ID：{{ selectedTaskDetail.userId ?? '全局' }}</div>
                <div class="text-slate-300">请求者：{{ selectedTaskDetail.requestedByUserNickname || selectedTaskDetail.requestedByUsername || selectedTaskDetail.requestedByUserId || '系统' }}</div>
                <div class="text-slate-300">存储：{{ selectedTaskDetail.storageProviderName || selectedTaskDetail.storageProviderId || '默认' }}<span v-if="selectedTaskDetail.storageProviderType"> · {{ storageTypeLabel(selectedTaskDetail.storageProviderType) }}</span></div>
              </div>

              <div class="rounded-xl border border-slate-800 bg-slate-950/40 p-4 space-y-2">
                <div class="text-slate-400 text-xs">恢复状态</div>
                <div class="text-slate-300 break-all">
                  恢复游标：{{ selectedTaskDetail.resumeFromPathDisplay || selectedTaskDetail.resumeFromPath || '—' }}
                  <span v-if="selectedTaskDetail.resumeFromType" class="ml-2 text-[11px] px-2 py-0.5 rounded-full border border-sky-500/30 bg-sky-500/10 text-sky-300">
                    {{ pathTypeLabel(selectedTaskDetail.resumeFromType) }}
                  </span>
                </div>
                <div class="text-slate-300 break-all">
                  最近断点：{{ selectedTaskDetail.lastProcessedPathDisplay || selectedTaskDetail.lastProcessedPath || '—' }}
                  <span v-if="selectedTaskDetail.lastProcessedType" class="ml-2 text-[11px] px-2 py-0.5 rounded-full border border-purple-500/30 bg-purple-500/10 text-purple-300">
                    {{ pathTypeLabel(selectedTaskDetail.lastProcessedType) }}
                  </span>
                </div>
                <div class="text-slate-300 break-all">检查点根路径：{{ selectedTaskDetail.checkpoint?.rootPathDisplay || selectedTaskDetail.checkpoint?.rootPath || '—' }}</div>
                <div class="text-slate-300">检查点更新时间：{{ formatDateTime(selectedTaskDetail.checkpointUpdatedAt || selectedTaskDetail.checkpoint?.updatedAt) }}</div>
                <div v-if="selectedTaskDetail.errorMessage" class="text-rose-300 break-all">
                  错误：{{ selectedTaskDetail.errorMessage }}
                </div>
              </div>

              <div class="rounded-xl border border-slate-800 bg-slate-950/40 p-4 space-y-2">
                <div class="text-slate-400 text-xs">进度统计</div>
                <div class="text-white text-lg">{{ selectedTaskDetail.progressPercent || 0 }}%</div>
                <div class="text-slate-300">已处理：{{ selectedTaskDetail.processedItems || 0 }} / {{ selectedTaskDetail.totalItems || 0 }}</div>
                <div class="text-slate-300">跳过：{{ selectedTaskDetail.skippedItems || 0 }}</div>
                <div class="text-slate-300">失败：{{ selectedTaskDetail.failedItems || 0 }}</div>
                <div class="text-slate-500 text-xs">创建：{{ formatDateTime(selectedTaskDetail.createdAt) }}</div>
                <div class="text-slate-500 text-xs">开始：{{ formatDateTime(selectedTaskDetail.startedAt) }}</div>
                <div class="text-slate-500 text-xs">完成：{{ formatDateTime(selectedTaskDetail.finishedAt) }}</div>
              </div>
            </div>

            <div class="mt-4 rounded-xl border border-slate-800 bg-slate-950/40 p-4">
              <div class="text-slate-400 text-xs mb-3">检查点快照</div>
              <pre class="text-xs text-slate-200 whitespace-pre-wrap break-words">{{ JSON.stringify(selectedTaskDetail.checkpoint || {}, null, 2) }}</pre>
            </div>
          </div>

          <div v-else class="flex-1 flex items-center justify-center text-slate-400 text-sm">
            {{ loadingTaskDetail ? '加载任务详情…' : '暂无任务详情' }}
          </div>
        </div>
      </div>

      <section class="glass-panel p-4 admin-card-animate admin-card-4 space-y-4">
        <div class="flex items-center justify-between gap-4">
          <div>
            <h2 class="text-lg font-light">扫描任务队列</h2>
            <p class="text-xs text-slate-400 mt-1">查看最近任务，支持对失败或暂停任务重新入队。</p>
          </div>
          <button
            @click="fetchScanTasks"
            :disabled="loadingScanTasks"
            class="px-3 py-1.5 text-xs bg-gray-900/70 hover:bg-gray-800 rounded-lg transition-colors border border-white/15 disabled:opacity-60"
          >
            {{ loadingScanTasks ? '刷新中…' : '刷新列表' }}
          </button>
        </div>

        <div v-if="scanTasks.length === 0" class="text-sm text-slate-400 py-6 text-center">
          暂无扫描任务
        </div>

        <div v-else class="overflow-auto">
          <table class="w-full text-sm text-slate-200">
            <thead class="text-slate-400 border-b border-slate-800">
              <tr>
                <th class="text-left py-3 pr-4">任务</th>
                <th class="text-left py-3 pr-4">状态</th>
                <th class="text-left py-3 pr-4">归属</th>
                <th class="text-left py-3 pr-4">路径</th>
                <th class="text-left py-3 pr-4">进度</th>
                <th class="text-left py-3 pr-4">时间</th>
                <th class="text-right py-3">操作</th>
              </tr>
            </thead>
            <tbody>
              <template v-for="task in scanTasks" :key="task.id">
                <tr class="border-b border-slate-900/80 align-top">
                  <td class="py-3 pr-4">
                    <button
                      @click="openTaskDetail(task.id)"
                      class="font-medium text-white hover:text-sky-300 transition-colors"
                    >#{{ task.id }}</button>
                    <div class="text-xs text-slate-400 mt-1">{{ taskTypeLabel(task.taskType) }}</div>
                  </td>
                  <td class="py-3 pr-4">
                    <span class="px-2 py-1 rounded-full text-xs border" :class="taskStatusClass(task.status)">
                      {{ taskStatusLabel(task.status) }}
                    </span>
                    <div v-if="task.errorMessage" class="text-xs text-rose-300 mt-2 max-w-xs break-all">
                      {{ task.errorMessage }}
                    </div>
                  </td>
                  <td class="py-3 pr-4 text-xs text-slate-300">
                    <div>{{ task.ownerLabel || '系统任务' }}</div>
                    <div class="text-slate-500 mt-1">
                      存储：{{ task.storageProviderName || task.storageProviderId || '默认' }}
                      <span v-if="task.storageProviderType"> · {{ storageTypeLabel(task.storageProviderType) }}</span>
                    </div>
                    <div
                      v-if="task.scanSupported === false"
                      class="mt-2 inline-flex max-w-xs rounded-full border border-amber-400/30 bg-amber-500/10 px-2 py-1 text-[11px] text-amber-100"
                    >
                      {{ task.supportMessage || '当前存储暂不支持扫描' }}
                    </div>
                  </td>
                  <td class="py-3 pr-4">
                    <div class="max-w-sm break-all text-xs text-slate-300">{{ task.rootPathDisplay || task.rootPath }}</div>
                    <div v-if="task.lastProcessedPath" class="text-xs text-slate-500 mt-1 break-all">
                      断点：{{ task.lastProcessedPathDisplay || task.lastProcessedPath }}
                      <span v-if="task.lastProcessedType" class="ml-1 text-[10px] text-purple-300">· {{ pathTypeLabel(task.lastProcessedType) }}</span>
                    </div>
                  </td>
                  <td class="py-3 pr-4">
                    <div>{{ task.processedItems || 0 }} / {{ task.totalItems || 0 }}</div>
                    <div class="text-xs text-slate-500 mt-1">
                      跳过 {{ task.skippedItems || 0 }} / 失败 {{ task.failedItems || 0 }}
                    </div>
                    <div class="text-xs text-sky-300 mt-1">
                      {{ task.progressPercent || 0 }}%
                    </div>
                  </td>
                  <td class="py-3 pr-4 text-xs text-slate-400 whitespace-nowrap">
                    <div>创建：{{ formatDateTime(task.createdAt) }}</div>
                    <div class="mt-1">开始：{{ formatDateTime(task.startedAt) }}</div>
                    <div class="mt-1">完成：{{ formatDateTime(task.finishedAt) }}</div>
                  </td>
                  <td class="py-3 text-right">
                    <div class="flex justify-end gap-2">
                      <button
                        @click="toggleTaskExpanded(task.id)"
                        class="px-3 py-1.5 text-xs bg-slate-800 hover:bg-slate-700 rounded-lg transition-colors border border-white/10"
                      >
                        {{ isTaskExpanded(task.id) ? '收起详情' : '查看详情' }}
                      </button>
                      <button
                        v-if="canPauseTask(task)"
                        @click="pauseScanTask(task)"
                        class="px-3 py-1.5 text-xs bg-amber-600 hover:bg-amber-700 rounded-lg transition-colors"
                      >
                        暂停
                      </button>
                      <button
                        v-if="canRetryTask(task)"
                        @click="retryScanTask(task)"
                        class="px-3 py-1.5 text-xs bg-blue-600 hover:bg-blue-700 rounded-lg transition-colors"
                      >
                        重新入队
                      </button>
                      <button
                        v-if="canCancelTask(task)"
                        @click="cancelScanTask(task)"
                        class="px-3 py-1.5 text-xs bg-rose-600 hover:bg-rose-700 rounded-lg transition-colors"
                      >
                        取消
                      </button>
                    </div>
                  </td>
                </tr>
                <tr
                  v-if="isTaskExpanded(task.id)"
                  class="border-b border-slate-900/80 bg-slate-950/30"
                >
                  <td colspan="6" class="px-4 py-4">
                    <div class="grid grid-cols-1 lg:grid-cols-3 gap-4 text-xs">
                      <div class="rounded-xl border border-slate-800 bg-slate-950/40 p-3 space-y-2">
                        <div class="text-slate-400">恢复信息</div>
                        <div class="text-slate-200 break-all">
                          恢复游标：{{ task.resumeFromPathDisplay || task.resumeFromPath || '—' }}
                          <span v-if="task.resumeFromType" class="ml-1 text-[10px] text-sky-300">· {{ pathTypeLabel(task.resumeFromType) }}</span>
                        </div>
                        <div class="text-slate-200 break-all">
                          检查点断点：{{ task.checkpoint?.lastProcessedPathDisplay || task.checkpoint?.lastProcessedPath || '—' }}
                          <span v-if="task.checkpoint?.lastProcessedType" class="ml-1 text-[10px] text-purple-300">· {{ pathTypeLabel(task.checkpoint?.lastProcessedType) }}</span>
                        </div>
                        <div class="text-slate-200 break-all">检查点根路径：{{ task.checkpoint?.rootPathDisplay || task.checkpoint?.rootPath || '—' }}</div>
                        <div class="text-slate-500">检查点更新时间：{{ formatDateTime(task.checkpointUpdatedAt || task.checkpoint?.updatedAt) }}</div>
                      </div>
                      <div class="rounded-xl border border-slate-800 bg-slate-950/40 p-3 space-y-2">
                        <div class="text-slate-400">任务元数据</div>
                        <div class="text-slate-200">优先级：{{ task.priority ?? '—' }}</div>
                        <div class="text-slate-200">用户 ID：{{ task.userId ?? '全局' }}</div>
                        <div class="text-slate-200">请求者：{{ task.requestedByUserId ?? '系统' }}</div>
                        <div class="text-slate-200">存储提供者：{{ task.storageProviderName || task.storageProviderId || '默认' }}<span v-if="task.storageProviderType"> · {{ storageTypeLabel(task.storageProviderType) }}</span></div>
                        <div v-if="task.scanSupported === false" class="text-amber-200 break-all">扫描限制：{{ task.supportMessage || '当前存储暂不支持扫描' }}</div>
                        <div class="text-slate-200">计划任务：{{ task.scheduledTask ? '是' : '否' }}</div>
                      </div>
                      <div class="rounded-xl border border-slate-800 bg-slate-950/40 p-3 space-y-2">
                        <div class="text-slate-400">检查点计数</div>
                        <div class="text-slate-200">已处理：{{ task.checkpoint?.processedItems ?? task.processedItems ?? 0 }}</div>
                        <div class="text-slate-200">总数：{{ task.checkpoint?.totalItems ?? task.totalItems ?? 0 }}</div>
                        <div class="text-slate-200">跳过：{{ task.checkpoint?.skippedItems ?? task.skippedItems ?? 0 }}</div>
                        <div class="text-slate-200">失败：{{ task.checkpoint?.failedItems ?? task.failedItems ?? 0 }}</div>
                        <button
                          @click="openTaskDetail(task.id)"
                          class="mt-2 px-3 py-1.5 text-xs bg-sky-700/70 hover:bg-sky-600 rounded-lg transition-colors"
                        >
                          弹窗查看
                        </button>
                      </div>
                    </div>
                  </td>
                </tr>
              </template>
            </tbody>
          </table>
        </div>
      </section>

      <section class="glass-panel p-4 admin-card-animate admin-card-4 space-y-4">
        <div class="flex items-center justify-between gap-4">
          <div>
            <h2 class="text-lg font-light">最近操作记录</h2>
            <p class="text-xs text-slate-400 mt-1">展示最近上传、删除、扫描等操作。</p>
          </div>
          <button
            @click="fetchOperationLogs"
            :disabled="loadingOperationLogs"
            class="px-3 py-1.5 text-xs bg-gray-900/70 hover:bg-gray-800 rounded-lg transition-colors border border-white/15 disabled:opacity-60"
          >
            {{ loadingOperationLogs ? '刷新中…' : '刷新记录' }}
          </button>
        </div>

        <div v-if="operationLogs.length === 0" class="text-sm text-slate-400 py-6 text-center">
          暂无操作记录
        </div>

        <div v-else class="space-y-3">
          <div
            v-for="log in operationLogs"
            :key="log.id"
            class="rounded-xl border border-slate-800 bg-slate-950/40 p-3"
          >
            <div class="flex items-start justify-between gap-4">
              <div>
                <div class="text-sm text-white">
                  {{ operationTypeLabel(log.operationType) }}
                  <span class="text-slate-500 ml-2">{{ log.operatorUsername || '系统' }}</span>
                </div>
                <div class="text-xs text-slate-400 mt-1 break-all">
                  {{ log.targetPath || '—' }}
                </div>
                <div v-if="log.detailJson" class="text-xs text-slate-500 mt-2 break-all">
                  {{ log.detailJson }}
                </div>
              </div>
              <div class="text-xs text-slate-500 whitespace-nowrap">
                {{ formatDateTime(log.createdAt) }}
              </div>
            </div>
          </div>
        </div>
      </section>

      <section class="glass-panel p-4 admin-card-animate admin-card-4 space-y-4">
        <div class="flex items-center justify-between gap-4">
          <div>
            <h2 class="text-lg font-light">最近登录记录</h2>
            <p class="text-xs text-slate-400 mt-1">展示当前账号最近登录成功/失败情况。</p>
          </div>
          <button
            @click="fetchLoginRecords"
            :disabled="loadingLoginRecords"
            class="px-3 py-1.5 text-xs bg-gray-900/70 hover:bg-gray-800 rounded-lg transition-colors border border-white/15 disabled:opacity-60"
          >
            {{ loadingLoginRecords ? '刷新中…' : '刷新记录' }}
          </button>
        </div>

        <div v-if="loginRecords.length === 0" class="text-sm text-slate-400 py-6 text-center">
          暂无登录记录
        </div>

        <div v-else class="space-y-3">
          <div
            v-for="record in loginRecords"
            :key="record.id"
            class="rounded-xl border border-slate-800 bg-slate-950/40 p-3"
          >
            <div class="flex items-start justify-between gap-4">
              <div>
                <div class="text-sm text-white">
                  {{ loginMethodLabel(record.loginMethod) }}
                  <span
                    class="ml-2 px-2 py-0.5 rounded-full text-xs border"
                    :class="record.success
                      ? 'text-emerald-300 border-emerald-500/40 bg-emerald-500/10'
                      : 'text-rose-300 border-rose-500/40 bg-rose-500/10'"
                  >
                    {{ record.success ? '成功' : '失败' }}
                  </span>
                </div>
                <div class="text-xs text-slate-400 mt-1 break-all">
                  {{ record.ipAddress || '未知 IP' }} · {{ record.phoneSnapshot || record.usernameSnapshot || '未知账号' }}
                </div>
                <div v-if="record.failureReason" class="text-xs text-rose-300 mt-2 break-all">
                  {{ record.failureReason }}
                </div>
                <div v-if="record.userAgent" class="text-xs text-slate-500 mt-2 break-all">
                  {{ record.userAgent }}
                </div>
              </div>
              <div class="text-xs text-slate-500 whitespace-nowrap">
                {{ formatDateTime(record.createdAt) }}
              </div>
            </div>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useThemeStore } from '@/stores/theme'
import { api } from '@/api'
import AdminSectionTabs from '@/components/AdminSectionTabs.vue'
import { storageTypeLabel } from '@/utils/providerLabels'

const router = useRouter()
const authStore = useAuthStore()
const themeStore = useThemeStore()

const stats = ref({
  albums: 0,
  photos: 0,
  tags: 0,
  persons: 0,
  faces: 0
})

const scanning = ref(false)
const scanProviderOptions = ref<Array<{
  id: number
  name: string
  type: string
  scanSupported?: boolean
  supportMessage?: string | null
}>>([])
const selectedScanProviderId = ref<number | null>(null)
const queueCount = ref(0)
const queuedOwnerCount = ref(0)
const queuedOwnerSummaries = ref<any[]>([])
const pausedTaskCount = ref(0)
const runningTaskCount = ref(0)
const runningTasks = ref<any[]>([])
const currentScanTask = ref<any | null>(null)
const scanTasks = ref<any[]>([])
const expandedTaskIds = ref<number[]>([])
const showTaskDetailModal = ref(false)
const selectedTaskDetail = ref<any | null>(null)
const loadingTaskDetail = ref(false)
const loadingScanTasks = ref(false)
const loginRecords = ref<any[]>([])
const loadingLoginRecords = ref(false)
const operationLogs = ref<any[]>([])
const loadingOperationLogs = ref(false)
const isCleaningUp = ref(false)
const lastScanTime = ref<string | null>(null)
const scanProgress = ref<{ current: number; total: number }>({ current: 0, total: 0 })
const scanStatus = computed(() => {
  if (currentScanTask.value) return '扫描中'
  if (queueCount.value > 0) return '排队中'
  if (pausedTaskCount.value > 0) return '已暂停'
  return scanning.value ? '扫描中' : '空闲'
})
const scanProgressText = computed(() => {
  const { current, total } = scanProgress.value
  if (!total) return '0 / 0'
  const percentage = total > 0 ? Math.min(100, Math.floor((current / total) * 100)) : 0
  return `${current} / ${total} (${percentage}%)`
})
const queuedOwnerSummaryText = computed(() =>
  queuedOwnerSummaries.value
    .slice(0, 3)
    .map((item: any) => `${item.ownerLabel || '未知'} ${item.taskCount}`)
    .join('，')
)
const runningTaskSummaryText = computed(() =>
  runningTasks.value
    .slice(0, 3)
    .map((item: any) => `${item.requestedByUserNickname || item.requestedByUsername || item.ownerLabel || `任务 ${item.id}`} · ${item.taskType}`)
    .join('，')
)
const selectedScanProvider = computed(() => {
  if (selectedScanProviderId.value == null) return null
  return scanProviderOptions.value.find((provider) => provider.id === selectedScanProviderId.value) ?? null
})
const scanActionSupported = computed(() => selectedScanProvider.value?.scanSupported !== false)
const scanDisabledReason = computed(() => {
  if (!authStore.isSuperAdmin) return ''
  if (selectedScanProvider.value?.scanSupported === false) {
    return selectedScanProvider.value.supportMessage || '当前存储暂不支持扫描，请切换到可扫描的存储配置。'
  }
  return ''
})

const scanCurrentVal = computed(() => scanProgress.value.current)
const scanTotal = computed(() => scanProgress.value.total)

// 跳过文件弹窗
const showSkippedModal = ref(false)
const loadingSkipped = ref(false)
const skippedFiles = ref<Array<{
  index: number
  relativePath: string
  reason: string
  detail: string
  fileSizeBytes: number
}>>([])

const formatFileSize = (bytes: number): string => {
  if (bytes === 0) return '0 B'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(2)} MB`
}

const formatDateTime = (value?: string | null): string => {
  if (!value) return '—'
  return new Date(value).toLocaleString('zh-CN')
}

const taskTypeLabel = (taskType?: string): string => {
  switch (taskType) {
    case 'FULL_SCAN':
      return '强制扫描'
    case 'UPLOAD_SCAN':
      return '上传扫描'
    case 'RESUME_SCAN':
      return '续扫任务'
    default:
      return '增量扫描'
  }
}

const taskStatusLabel = (status?: string): string => {
  switch (status) {
    case 'RUNNING':
      return '执行中'
    case 'QUEUED':
      return '排队中'
    case 'PENDING':
      return '待处理'
    case 'PAUSED':
      return '已暂停'
    case 'FAILED':
      return '失败'
    case 'COMPLETED':
      return '已完成'
    case 'CANCELED':
      return '已取消'
    default:
      return status || '未知'
  }
}

const pathTypeLabel = (pathType?: string): string => {
  switch (pathType) {
    case 'DIRECTORY':
      return '目录'
    case 'FILE':
      return '文件'
    default:
      return pathType || '未知'
  }
}

const taskStatusClass = (status?: string): string => {
  if (status === 'RUNNING') return 'text-sky-300 border-sky-500/40 bg-sky-500/10'
  if (status === 'QUEUED' || status === 'PENDING') return 'text-amber-300 border-amber-500/40 bg-amber-500/10'
  if (status === 'PAUSED') return 'text-purple-300 border-purple-500/40 bg-purple-500/10'
  if (status === 'FAILED') return 'text-rose-300 border-rose-500/40 bg-rose-500/10'
  if (status === 'COMPLETED') return 'text-emerald-300 border-emerald-500/40 bg-emerald-500/10'
  if (status === 'CANCELED') return 'text-slate-300 border-slate-500/40 bg-slate-500/10'
  return 'text-slate-300 border-slate-500/40 bg-slate-500/10'
}

const operationTypeLabel = (operationType?: string): string => {
  switch (operationType) {
    case 'UPLOAD':
      return '上传文件'
    case 'DELETE':
      return '删除内容'
    case 'UPDATE':
      return '修改内容'
    case 'SCAN_START':
      return '发起扫描'
    case 'SCAN_RESUME':
      return '恢复扫描'
    case 'SCAN_FINISH':
      return '扫描完成'
    case 'CONFIG_UPDATE':
      return '更新配置'
    default:
      return operationType || '操作'
  }
}

const loginMethodLabel = (method?: string): string => {
  switch (method) {
    case 'PHONE_PASSWORD':
      return '手机号+密码'
    case 'SMS_CODE':
      return '短信验证码'
    default:
      return '账号+密码'
  }
}

const canRetryTask = (task: any): boolean =>
  ['FAILED', 'PAUSED', 'CANCELED'].includes(task.status)

const canPauseTask = (task: any): boolean =>
  task.status === 'RUNNING'

const canCancelTask = (task: any): boolean =>
  ['RUNNING', 'QUEUED', 'PENDING'].includes(task.status)

const isTaskExpanded = (taskId: number): boolean =>
  expandedTaskIds.value.includes(taskId)

const toggleTaskExpanded = (taskId: number) => {
  if (isTaskExpanded(taskId)) {
    expandedTaskIds.value = expandedTaskIds.value.filter(id => id !== taskId)
    return
  }
  expandedTaskIds.value = [...expandedTaskIds.value, taskId]
}

let skippedPollTimer: number | null = null
let taskDetailPollTimer: number | null = null

const fetchSkippedFiles = async () => {
  try {
    const res = await api.get('/admin/scan/skipped-files')
    skippedFiles.value = res.data || []
  } catch (e) {
    skippedFiles.value = []
  }
}

const fetchScanTasks = async () => {
  loadingScanTasks.value = true
  try {
    const res = await api.get('/admin/scan/tasks')
    scanTasks.value = res.data || []
    expandedTaskIds.value = expandedTaskIds.value.filter(taskId =>
      scanTasks.value.some(task => task.id === taskId)
    )
  } catch (error) {
    console.error('加载扫描任务失败:', error)
  } finally {
    loadingScanTasks.value = false
  }
}

const shouldPollTaskDetail = (task: any | null): boolean =>
  !!task && ['RUNNING', 'QUEUED', 'PENDING', 'PAUSED'].includes(task.status)

const stopTaskDetailPoll = () => {
  if (taskDetailPollTimer) {
    clearInterval(taskDetailPollTimer)
    taskDetailPollTimer = null
  }
}

const fetchTaskDetail = async (taskId: number) => {
  loadingTaskDetail.value = true
  try {
    const res = await api.get(`/admin/scan/tasks/${taskId}`)
    selectedTaskDetail.value = res.data || null
    if (!shouldPollTaskDetail(selectedTaskDetail.value)) {
      stopTaskDetailPoll()
    }
  } catch (error) {
    console.error('加载任务详情失败:', error)
  } finally {
    loadingTaskDetail.value = false
  }
}

const refreshSelectedTaskDetail = async () => {
  if (!selectedTaskDetail.value?.id) return
  await fetchTaskDetail(selectedTaskDetail.value.id)
}

const openTaskDetail = async (taskId: number) => {
  showTaskDetailModal.value = true
  await fetchTaskDetail(taskId)
  stopTaskDetailPoll()
  if (shouldPollTaskDetail(selectedTaskDetail.value)) {
    taskDetailPollTimer = window.setInterval(() => {
      if (selectedTaskDetail.value?.id) {
        fetchTaskDetail(selectedTaskDetail.value.id)
      }
    }, 3000)
  }
}

const closeTaskDetailModal = () => {
  showTaskDetailModal.value = false
  selectedTaskDetail.value = null
  stopTaskDetailPoll()
}

const fetchOperationLogs = async () => {
  loadingOperationLogs.value = true
  try {
    const res = await api.get('/admin/operation-logs')
    operationLogs.value = res.data || []
  } catch (error) {
    console.error('加载操作日志失败:', error)
  } finally {
    loadingOperationLogs.value = false
  }
}

const fetchLoginRecords = async () => {
  loadingLoginRecords.value = true
  try {
    const res = await api.get('/admin/login-records')
    loginRecords.value = res.data || []
  } catch (error) {
    console.error('加载登录记录失败:', error)
  } finally {
    loadingLoginRecords.value = false
  }
}

const retryScanTask = async (task: any) => {
  if (!confirm(`确认重新入队扫描任务 #${task.id} 吗？`)) return
  try {
    await api.post(`/admin/scan/tasks/${task.id}/retry`)
    await Promise.all([fetchScanStatus(), fetchScanTasks()])
  } catch (error: any) {
    alert('重新入队失败: ' + (error.response?.data?.error || error.message))
  }
}

const pauseScanTask = async (task: any) => {
  if (!confirm(`确认暂停扫描任务 #${task.id} 吗？`)) return
  try {
    const res = await api.post(`/admin/scan/tasks/${task.id}/pause`)
    if (res.data?.message) {
      alert(res.data.message)
    }
    await Promise.all([fetchScanStatus(), fetchScanTasks()])
  } catch (error: any) {
    alert('暂停失败: ' + (error.response?.data?.error || error.message))
  }
}

const cancelScanTask = async (task: any) => {
  if (!confirm(`确认取消扫描任务 #${task.id} 吗？`)) return
  try {
    const res = await api.post(`/admin/scan/tasks/${task.id}/cancel`)
    if (res.data?.message) {
      alert(res.data.message)
    }
    await Promise.all([fetchScanStatus(), fetchScanTasks()])
  } catch (error: any) {
    alert('取消失败: ' + (error.response?.data?.error || error.message))
  }
}

const openSkippedFilesModal = async () => {
  showSkippedModal.value = true
  loadingSkipped.value = true
  await fetchSkippedFiles()
  loadingSkipped.value = false

  // 如果正在扫描，自动轮询刷新异常列表直到扫描结束
  if (skippedPollTimer) clearInterval(skippedPollTimer)
  if (scanning.value) {
    skippedPollTimer = window.setInterval(async () => {
      await fetchSkippedFiles()
      if (!scanning.value) {
        // 扫描结束后再刷新一次，然后停止轮询
        await fetchSkippedFiles()
        if (skippedPollTimer) { clearInterval(skippedPollTimer); skippedPollTimer = null }
      }
    }, 2000)
  }
}

const closeSkippedModal = () => {
  showSkippedModal.value = false
  if (skippedPollTimer) { clearInterval(skippedPollTimer); skippedPollTimer = null }
}

const loadStats = async () => {
  try {
    const [albumsRes, photosRes, tagsRes, personsRes, facesRes] = await Promise.all([
      api.get('/albums/count'),
      api.get('/photos', { params: { size: 1, page: 0 } }),
      api.get('/tags', { params: { size: 1, page: 0 } }),
      api.get('/admin/persons/count'),
      api.get('/admin/faces', { params: { size: 1, page: 0 } })
    ])

    const albumTotal = albumsRes.data ?? 0
    const photoTotal = photosRes.data.totalElements ?? photosRes.data.total ?? 0
    const tagTotal = Array.isArray(tagsRes.data)
      ? tagsRes.data.length
      : (tagsRes.data.totalElements ?? tagsRes.data.total ?? 0)
    const personTotal = personsRes.data ?? 0
    const faceTotal = facesRes.data.totalElements ?? facesRes.data.total ?? 0

    stats.value = {
      albums: albumTotal,
      photos: photoTotal,
      tags: tagTotal,
      persons: personTotal,
      faces: faceTotal
    }
  } catch (error) {
    console.error('加载统计信息失败:', error)
  }
}

const cleanupOrphaned = async () => {
  const confirmed = confirm(
    '🧹 清理删除残留数据\n\n' +
    '此操作将：\n' +
    '• 扫描所有照片记录，检查文件是否还存在\n' +
    '• 删除不存在文件的照片记录\n' +
    '• 删除相关的人脸识别数据\n' +
    '• 删除相关的标签关联\n' +
    '• 删除没有照片的空相册\n' +
    '• 删除文件夹路径不存在的相册\n\n' +
    '⚠️ 此操作不可恢复，请谨慎使用！\n' +
    '建议在删除大量文件或文件夹后执行此清理。\n\n' +
    '确定要继续吗？'
  )
  if (!confirmed) return

  isCleaningUp.value = true
  try {
    const response = await api.post('/admin/cleanup/orphaned')
    await loadStats()
    alert(response.data.message || '清理完成！')
  } catch (error: any) {
    alert('清理失败: ' + (error.response?.data?.error || error.message))
  } finally {
    isCleaningUp.value = false
  }
}

const triggerScan = async () => {
  if (!scanActionSupported.value) {
    alert(scanDisabledReason.value || '当前存储暂不支持扫描')
    return
  }
  scanning.value = true
  lastScanTime.value = new Date().toLocaleString('zh-CN')
  try {
    const params = authStore.isSuperAdmin && selectedScanProviderId.value != null
      ? { storageProviderId: selectedScanProviderId.value }
      : undefined
    await api.post('/admin/scan', null, { params })
  } catch (error: any) {
    alert('触发扫描失败: ' + (error.response?.data?.message || error.message))
  } finally {
    scanning.value = false
  }
}

const loadScanProviderOptions = async () => {
  if (!authStore.isSuperAdmin) {
    scanProviderOptions.value = []
    selectedScanProviderId.value = null
    return
  }
  try {
    const { data } = await api.get('/admin/folders/base-path')
    const providers = Array.isArray(data?.availableStorageProviders) ? data.availableStorageProviders : []
    scanProviderOptions.value = providers
      .filter((item: any) => item?.browserSupported)
      .map((item: any) => ({
        id: item.id,
        name: item.name,
        type: item.type,
        scanSupported: item.scanSupported !== false,
        supportMessage: item.supportMessage || null
      }))
    selectedScanProviderId.value = data?.storageProviderId ?? null
    if (selectedScanProviderId.value != null) {
      const exists = scanProviderOptions.value.some((provider) => provider.id === selectedScanProviderId.value)
      if (!exists) {
        selectedScanProviderId.value = null
      }
    }
  } catch (error) {
    console.warn('加载扫描存储提供者失败', error)
  }
}

const fetchScanStatus = async () => {
  try {
    const res = await api.get('/admin/scan/status')
    const data = res.data || {}
    scanning.value = !!data.scanning
    queueCount.value = data.queuedTaskCount ?? 0
    queuedOwnerCount.value = data.queuedOwnerCount ?? 0
    queuedOwnerSummaries.value = Array.isArray(data.queuedOwnerSummaries) ? data.queuedOwnerSummaries : []
    pausedTaskCount.value = data.pausedTaskCount ?? 0
    runningTaskCount.value = data.runningTaskCount ?? 0
    runningTasks.value = Array.isArray(data.runningTasks) ? data.runningTasks : []
    currentScanTask.value = data.currentTask ?? null
    scanProgress.value = {
      current: data.current ?? 0,
      total: data.total ?? 0
    }
    if (data.lastScanStart) {
      lastScanTime.value = new Date(data.lastScanStart).toLocaleString('zh-CN')
    }
  } catch (e) {
    // ignore
  }
}

const handleLogout = () => {
  authStore.logout()
  router.push('/admin/login')
}

let scanTimer: number | null = null

onMounted(async () => {
  await loadStats()
  await Promise.all([fetchScanStatus(), fetchScanTasks(), fetchOperationLogs(), fetchLoginRecords(), loadScanProviderOptions()])
  scanTimer = window.setInterval(() => {
    fetchScanStatus()
    fetchScanTasks()
    fetchOperationLogs()
    fetchLoginRecords()
  }, 5000)
})

onUnmounted(() => {
  if (scanTimer) {
    clearInterval(scanTimer)
    scanTimer = null
  }
  stopTaskDetailPoll()
})
</script>
