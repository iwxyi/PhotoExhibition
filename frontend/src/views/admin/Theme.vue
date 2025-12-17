<template>
  <div class="min-h-screen admin-shell text-white">
    <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8 space-y-8">
      <div class="flex items-center justify-between">
        <div>
          <h1 class="text-2xl font-light tracking-wide mb-1">主题与风格</h1>
          <p class="text-sm text-gray-300">
            全局统一配置颜色、毛玻璃和圆角风格，立即影响前台和后台。
          </p>
        </div>
        <router-link
          to="/admin"
          class="px-4 py-2 bg-gray-900/70 hover:bg-gray-700 rounded-lg border border-white/10 transition-colors text-sm"
        >
          返回控制台
        </router-link>
      </div>

      <!-- 颜色模式 -->
      <section class="glass-panel p-6 space-y-4">
        <div class="flex items-center justify-between flex-wrap gap-4">
          <div>
            <h2 class="text-lg font-light">颜色模式</h2>
            <p class="text-xs text-gray-400">
              支持跟随系统深色/浅色偏好，后台切换会同步前台。
            </p>
          </div>
          <div class="flex items-center gap-3">
            <span class="text-xs text-gray-300">浅色</span>
            <button
              @click="setDark(false)"
              class="relative inline-flex h-7 w-14 items-center rounded-full transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-white/60"
              :class="!themeStore.isDark ? 'bg-blue-500/80' : 'bg-gray-500/70'"
            >
              <span
                class="inline-flex h-6 w-6 transform items-center justify-center rounded-full bg-white text-gray-900 text-[10px] shadow-sm transition-transform"
                :class="themeStore.isDark ? 'translate-x-7' : 'translate-x-1'"
              >
                {{ themeStore.isDark ? '夜' : '日' }}
              </span>
            </button>
            <span class="text-xs text-gray-300">深色</span>
          </div>
        </div>
      </section>

      <!-- 主题预设 -->
      <section class="glass-panel p-6 space-y-4">
        <div class="flex items-center justify-between gap-4">
          <div>
            <h2 class="text-lg font-light">主题预设</h2>
            <p class="text-xs text-gray-400">
              选择一套喜欢的色彩和玻璃风格，立刻应用到全站按钮、面板和重点视觉元素。
            </p>
          </div>
        </div>

        <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 mt-4">
          <button
            v-for="[key, def] in themeEntries"
            :key="key"
            type="button"
            @click="changeThemeKey(key)"
            class="group relative flex flex-col items-stretch rounded-2xl border transition-all duration-200 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-400/80"
            :class="key === themeStore.currentThemeKey
              ? 'border-blue-400/80 shadow-[0_18px_45px_rgba(15,23,42,0.7)]'
              : 'border-white/10 hover:border-blue-300/70 hover:shadow-[0_18px_45px_rgba(15,23,42,0.55)]'"
          >
            <div
              class="relative h-28 overflow-hidden rounded-2xl rounded-b-none"
              :style="previewStyle(key)"
            >
              <div class="absolute inset-0 opacity-70 group-hover:opacity-90 transition-opacity" />
              <div class="absolute inset-x-0 bottom-0 p-3 text-xs flex items-center justify-between">
                <span class="font-medium">{{ def.name }}</span>
                <span
                  class="px-2 py-0.5 rounded-full text-[10px] uppercase tracking-wide"
                  :class="key === themeStore.currentThemeKey ? 'bg-black/60' : 'bg-black/40'"
                >
                  {{ key }}
                </span>
              </div>
            </div>
            <div class="px-3 py-3 flex items-center justify-between text-xs text-gray-300">
              <span>玻璃模糊 {{ def.glassBlur }}</span>
              <span>圆角 {{ def.radius }}</span>
            </div>
          </button>
        </div>
      </section>

      <!-- 预览说明 -->
      <section class="glass-panel p-4 text-xs text-gray-300 space-y-2">
        <p>• 主题配置保存在浏览器本地（localStorage），不会影响其他浏览器或设备。</p>
        <p>• 动画和过渡效果在系统设置为“减少动态效果”时会自动弱化，适配更多用户。</p>
        <p>• 如果后续需要更细粒度的主题控制，可以在现有 CSS 变量基础上继续扩展。</p>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useThemeStore } from '@/stores/theme'

const themeStore = useThemeStore()

const themeEntries = computed(() => {
  // 将 Pinia store 中的 themes 以 [key, def] 的形式暴露给模板
  return Object.entries(themeStore.themes as Record<string, any>) as [string, any][]
})

const setDark = (dark: boolean) => {
  if (dark === themeStore.isDark) return
  themeStore.toggleTheme()
}

const changeThemeKey = (key: string) => {
  themeStore.setThemeKey(key as any)
}

const previewStyle = (key: string) => {
  const def = (themeStore.themes as Record<string, any>)[key]
  const h = def.accentHue ?? def.accentH ?? 222
  const s = def.accentSaturation ?? '84%'
  const l = def.accentLightness ?? '56%'

  return {
    background: `radial-gradient(circle at 0% 0%, hsla(${h}, ${s}, ${l}, 0.95), transparent 55%), radial-gradient(circle at 80% 120%, hsla(${(h + 40) % 360}, ${s}, 50%, 0.9), transparent 55%), linear-gradient(145deg, rgba(15,23,42,1), rgba(15,23,42,0.4))`
  }
}
</script>


