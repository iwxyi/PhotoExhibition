<template>
  <section class="glass-panel p-6 space-y-6 admin-theme-settings-panel">
    <div class="flex items-start justify-between gap-4 flex-wrap">
      <div>
        <h2 class="text-lg font-light text-[color:var(--pe-admin-text-primary)]">后台主题</h2>
      </div>
      <div class="flex items-center gap-2 text-xs">
        <span v-if="themeStore.loading" class="rounded-full border border-white/10 bg-white/5 px-2.5 py-1 text-[color:var(--pe-admin-text-secondary)]">同步中</span>
        <span v-else-if="themeStore.saving" class="rounded-full border border-sky-400/20 bg-sky-500/10 px-2.5 py-1 text-sky-100">保存中</span>
        <span v-else-if="themeStore.lastSavedAt" class="rounded-full border border-emerald-400/20 bg-emerald-500/10 px-2.5 py-1 text-emerald-100">已保存</span>
      </div>
    </div>

    <div class="space-y-5 admin-theme-settings-layout">
      <div class="admin-soft-surface rounded-[28px] border p-5 space-y-4 admin-theme-style-block">
        <div class="space-y-1">
          <div class="text-sm text-[color:var(--pe-admin-text-primary)]">设计风格</div>
          <div class="text-xs text-[color:var(--pe-admin-text-muted)]">决定后台整体的卡片层次、按钮质感、密度与布局氛围。</div>
        </div>
        <div class="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-5 gap-3">
          <button
            v-for="[key, def] in styleFamilyEntries"
            :key="key"
            type="button"
            @click="handleSetStyleFamily(key)"
            class="admin-style-card text-left transition-all duration-200"
            :class="key === themeStore.currentStyleFamily ? 'admin-mode-option-active' : 'admin-soft-surface hover:bg-white/5'"
          >
            <div class="admin-style-card-preview" :data-style="key">
              <div class="admin-style-card-preview-window">
                <div class="admin-style-card-preview-bar">
                  <span class="admin-style-dot"></span>
                  <span class="admin-style-dot"></span>
                  <span class="admin-style-dot"></span>
                </div>
                <div class="admin-style-card-preview-body">
                  <div class="admin-style-card-preview-nav">
                    <span></span>
                    <span></span>
                    <span></span>
                  </div>
                  <div class="admin-style-card-preview-main">
                    <div class="admin-style-card-preview-hero"></div>
                    <div class="admin-style-card-preview-grid">
                      <div></div>
                      <div></div>
                      <div></div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
            <div class="mt-4 flex items-start justify-between gap-3">
              <div class="min-w-0">
                <div class="text-sm text-[color:var(--pe-admin-text-primary)]">{{ def.name }}</div>
                <div class="mt-1 text-xs text-[color:var(--pe-admin-text-faint)]">{{ def.vibe }}</div>
              </div>
              <span class="theme-gallery-chip shrink-0">{{ def.emphasis }}</span>
            </div>
            <div class="mt-2 text-xs leading-6 text-[color:var(--pe-admin-text-muted)]">{{ def.description }}</div>
          </button>
        </div>
      </div>

      <div class="grid grid-cols-1 xl:grid-cols-[320px,minmax(0,1fr)] gap-5">
        <div class="admin-soft-surface rounded-[28px] border p-5 space-y-4 admin-theme-mode-block">
        <div class="space-y-1">
          <div class="text-sm text-[color:var(--pe-admin-text-primary)]">显示模式</div>
          <div class="text-xs text-[color:var(--pe-admin-text-muted)]">主题保存到当前账号，切换设备也会同步。</div>
        </div>
        <button
          type="button"
          @click="handleSetColorMode(false)"
          class="flex w-full items-center justify-between rounded-[22px] border px-4 py-4 text-left transition-all duration-200"
          :class="!themeStore.isDark ? 'admin-mode-option-active' : 'admin-soft-surface hover:bg-white/5'"
        >
          <div class="space-y-1">
            <div class="text-sm text-[color:var(--pe-admin-text-primary)]">浅色模式</div>
            <div class="text-xs text-[color:var(--pe-admin-text-muted)]">更通透，适合明亮主题。</div>
          </div>
          <span class="theme-gallery-chip">日间</span>
        </button>
        <button
          type="button"
          @click="handleSetColorMode(true)"
          class="flex w-full items-center justify-between rounded-[22px] border px-4 py-4 text-left transition-all duration-200"
          :class="themeStore.isDark ? 'admin-mode-option-active' : 'admin-soft-surface hover:bg-white/5'"
        >
          <div class="space-y-1">
            <div class="text-sm text-[color:var(--pe-admin-text-primary)]">深色模式</div>
            <div class="text-xs text-[color:var(--pe-admin-text-muted)]">更沉浸，适合高对比主题。</div>
          </div>
          <span class="theme-gallery-chip">夜间</span>
        </button>
        </div>

        <div class="space-y-5 admin-theme-gallery-stage">
          <section v-if="currentThemeEntry" class="theme-gallery-spotlight">
            <div class="theme-gallery-spotlight-preview" :style="previewStyle(currentThemeEntry[0])">
              <div class="theme-gallery-browser">
                <div class="theme-gallery-browser-bar">
                  <div class="flex gap-2">
                    <span class="h-2.5 w-2.5 rounded-full bg-white/85"></span>
                    <span class="h-2.5 w-2.5 rounded-full bg-white/60"></span>
                    <span class="h-2.5 w-2.5 rounded-full bg-white/38"></span>
                  </div>
                  <div class="theme-gallery-browser-line w-20"></div>
                </div>
                <div class="theme-gallery-browser-grid">
                  <div class="space-y-2">
                    <div class="theme-gallery-swatch h-11" :style="{ background: swatchGradient(currentThemeEntry[1].preview.primary, currentThemeEntry[1].preview.secondary) }"></div>
                    <div class="grid grid-cols-2 gap-2">
                      <div class="theme-gallery-swatch h-12" :style="{ background: swatchGradient(currentThemeEntry[1].preview.base, currentThemeEntry[1].preview.primary) }"></div>
                      <div class="theme-gallery-swatch h-12" :style="{ background: swatchGradient(currentThemeEntry[1].preview.secondary, '#ffffff22') }"></div>
                    </div>
                  </div>
                  <div class="space-y-2">
                    <div class="theme-gallery-swatch h-[72px]" :style="{ background: swatchGradient(currentThemeEntry[1].preview.secondary, currentThemeEntry[1].preview.base) }"></div>
                    <div class="theme-gallery-browser-line w-full"></div>
                    <div class="theme-gallery-browser-line w-3/4"></div>
                  </div>
                </div>
              </div>
              <div class="absolute left-5 top-5">
                <span class="theme-gallery-chip">当前主题</span>
              </div>
            </div>
            <div class="theme-gallery-spotlight-copy">
              <div class="flex items-start justify-between gap-4 flex-wrap">
                <div class="space-y-2">
                  <div class="flex items-center gap-3 flex-wrap">
                    <h3 class="theme-gallery-title text-xl">{{ currentThemeEntry[1].name }}</h3>
                    <span class="theme-gallery-chip">{{ currentThemeEntry[0] }}</span>
                  </div>
                  <p class="text-sm text-[color:var(--pe-admin-text-secondary)]">{{ currentThemeEntry[1].mood }}</p>
                </div>
                <div class="flex gap-2">
                  <span class="h-4 w-4 rounded-full ring-1 ring-black/10" :style="{ background: currentThemeEntry[1].preview.primary }"></span>
                  <span class="h-4 w-4 rounded-full ring-1 ring-black/10" :style="{ background: currentThemeEntry[1].preview.secondary }"></span>
                  <span class="h-4 w-4 rounded-full ring-1 ring-black/10" :style="{ background: currentThemeEntry[1].preview.base }"></span>
                </div>
              </div>
              <div class="theme-gallery-spotlight-tags">
                <span class="theme-gallery-chip">{{ currentThemeCategoryLabel }}</span>
                <span class="theme-gallery-chip">{{ themeStore.isDark ? 'Dark' : 'Light' }}</span>
                <span class="theme-gallery-chip">{{ currentStyleFamilyName }}</span>
              </div>
            </div>
          </section>

          <section class="theme-gallery-filterbar">
            <label class="theme-gallery-filter-search">
              <span class="theme-gallery-filter-search-icon">搜索</span>
              <input
                v-model="themeSearch"
                type="text"
                class="theme-gallery-filter-input"
                placeholder="搜索主题名、风格、氛围、使用场景"
              />
            </label>
            <div class="theme-gallery-filter-meta">
              <span class="theme-gallery-chip">{{ currentStyleFamilyName }} 优先推荐</span>
              <span class="theme-gallery-filter-count">当前显示 {{ visibleThemeCount }} 款</span>
            </div>
            <div class="theme-gallery-filter-chips">
              <button
                v-for="filter in availableThemeFilters"
                :key="filter.key"
                type="button"
                class="theme-gallery-filter-chip"
                :class="{ 'theme-gallery-filter-chip--active': activeThemeFilter === filter.key }"
                @click="activeThemeFilter = filter.key"
              >
                <span>{{ filter.label }}</span>
                <span class="theme-gallery-filter-chip-count">{{ filter.count }}</span>
              </button>
            </div>
          </section>

          <section v-if="activeThemeFilter === 'all' && matchedThemeEntries.length" class="theme-gallery-match-strip">
            <div class="theme-gallery-group-head">
              <div>
                <h3 class="theme-gallery-group-title">风格速配</h3>
                <p class="theme-gallery-group-note">优先展示和 {{ currentStyleFamilyName }} 更协调的主题，方便快速试用。</p>
              </div>
              <span class="theme-gallery-chip">{{ matchedThemeEntries.length }} 款</span>
            </div>
            <div class="theme-gallery-match-grid">
              <button
                v-for="[key, def] in matchedThemeEntries"
                :key="`matched-${key}`"
                type="button"
                class="theme-gallery-match-card"
                :class="{ 'theme-gallery-match-card--active': key === themeStore.currentThemeKey }"
                @click="handleSetThemeKey(key)"
              >
                <div class="theme-gallery-match-preview" :style="previewStyle(key)"></div>
                <div class="min-w-0">
                  <div class="flex items-center gap-2">
                    <span class="theme-gallery-title text-sm truncate">{{ def.name }}</span>
                    <span v-if="key === themeStore.currentThemeKey" class="theme-gallery-chip">当前</span>
                  </div>
                  <div class="theme-gallery-mood text-xs mt-1 truncate">{{ def.mood }}</div>
                </div>
              </button>
            </div>
          </section>

          <section
            v-for="group in filteredThemeGroups"
            :key="group.key"
            class="theme-gallery-group"
          >
            <div class="theme-gallery-group-head">
              <div>
                <h3 class="theme-gallery-group-title">{{ group.label }}</h3>
                <p class="theme-gallery-group-note">{{ group.description }}</p>
              </div>
              <span class="theme-gallery-chip">{{ group.items.length }} 款</span>
            </div>
            <div class="theme-gallery-card-grid">
              <button
                v-for="[key, def] in group.items"
                :key="key"
                type="button"
                @click="handleSetThemeKey(key)"
                class="theme-gallery-card text-left"
                :class="{ 'theme-gallery-card--active': key === themeStore.currentThemeKey }"
              >
                <div class="theme-gallery-preview" :style="previewStyle(key)">
                  <div class="theme-gallery-browser">
                    <div class="theme-gallery-browser-bar">
                      <div class="flex gap-2">
                        <span class="h-2.5 w-2.5 rounded-full bg-white/85"></span>
                        <span class="h-2.5 w-2.5 rounded-full bg-white/60"></span>
                        <span class="h-2.5 w-2.5 rounded-full bg-white/38"></span>
                      </div>
                      <div class="theme-gallery-browser-line w-20"></div>
                    </div>
                    <div class="theme-gallery-browser-grid">
                      <div class="space-y-2">
                        <div class="theme-gallery-swatch h-11" :style="{ background: swatchGradient(def.preview.primary, def.preview.secondary) }"></div>
                        <div class="grid grid-cols-2 gap-2">
                          <div class="theme-gallery-swatch h-12" :style="{ background: swatchGradient(def.preview.base, def.preview.primary) }"></div>
                          <div class="theme-gallery-swatch h-12" :style="{ background: swatchGradient(def.preview.secondary, '#ffffff22') }"></div>
                        </div>
                      </div>
                      <div class="space-y-2">
                        <div class="theme-gallery-swatch h-[72px]" :style="{ background: swatchGradient(def.preview.secondary, def.preview.base) }"></div>
                        <div class="theme-gallery-browser-line w-full"></div>
                        <div class="theme-gallery-browser-line w-3/4"></div>
                      </div>
                    </div>
                  </div>
                  <div class="absolute left-4 top-4">
                    <span class="theme-gallery-chip">{{ themeStore.isDark ? 'Dark' : 'Light' }}</span>
                  </div>
                  <div class="absolute right-4 top-4">
                    <span class="theme-gallery-chip">{{ key }}</span>
                  </div>
                </div>
                <div class="theme-gallery-meta">
                  <div class="min-w-0 flex-1">
                    <div class="flex items-center gap-3">
                      <div class="theme-gallery-title text-base">{{ def.name }}</div>
                      <div class="flex gap-1.5">
                        <span class="h-3.5 w-3.5 rounded-full ring-1 ring-black/10" :style="{ background: def.preview.primary }"></span>
                        <span class="h-3.5 w-3.5 rounded-full ring-1 ring-black/10" :style="{ background: def.preview.secondary }"></span>
                        <span class="h-3.5 w-3.5 rounded-full ring-1 ring-black/10" :style="{ background: def.preview.base }"></span>
                      </div>
                    </div>
                    <div class="theme-gallery-mood mt-1 text-sm">{{ def.mood }}</div>
                    <div class="mt-3 flex flex-wrap gap-2">
                      <span class="theme-gallery-chip">{{ group.label }}</span>
                      <span v-if="isThemeMatchedToStyle(key)" class="theme-gallery-chip theme-gallery-chip--accent">匹配当前风格</span>
                      <span class="theme-gallery-chip theme-gallery-chip--soft">{{ themeScenarioLabel(key) }}</span>
                      <span class="theme-gallery-chip theme-gallery-chip--soft">{{ recommendedStyleLabel(key) }}</span>
                    </div>
                  </div>
                  <div v-if="key === themeStore.currentThemeKey" class="shrink-0 self-stretch flex items-center">
                    <span class="theme-gallery-chip min-h-[48px] px-4">
                      当前
                    </span>
                  </div>
                </div>
              </button>
            </div>
          </section>
          <section v-if="visibleThemeCount === 0" class="theme-gallery-empty">
            <div class="theme-gallery-empty-title">没有找到匹配的主题</div>
            <div class="theme-gallery-empty-note">可以尝试切换分组，或搜索主题名、氛围、推荐风格。</div>
          </section>
        </div>
      </div>
    </div>

    <div v-if="themeStore.errorMessage" class="rounded-2xl border border-rose-400/20 bg-rose-500/10 px-4 py-3 text-sm text-rose-100">
      {{ themeStore.errorMessage }}
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useThemeStore } from '@/stores/theme'

const themeStore = useThemeStore()
const themeSearch = ref('')
const activeThemeFilter = ref<'all' | 'matched' | string>('all')

const themeEntries = computed(() => {
  return Object.entries(themeStore.themes as Record<string, any>) as [string, any][]
})

const styleFamilyEntries = computed(() => {
  return Object.entries(themeStore.styleFamilies as Record<string, any>) as [string, any][]
})

const baseThemeGroups = computed(() => {
  const allEntries = themeEntries.value
  const groups = [
    {
      key: 'oriental',
      label: '东方雅致',
      description: '偏中式、留白、器物与柔和氛围。',
      keys: ['ink', 'vermilion', 'celadon', 'imperial', 'bamboo', 'peony', 'jadeite', 'lotus']
    },
    {
      key: 'classic',
      label: '古典与复古',
      description: '更厚重、剧场化、文艺或复古的后台气质。',
      keys: ['rosewood', 'baroque', 'cathedral', 'manor', 'noir', 'parchment', 'velvet', 'cocoa']
    },
    {
      key: 'nature',
      label: '自然与季节',
      description: '适合偏温和、自然、轻松的使用感。',
      keys: ['forest', 'mint', 'desert', 'harvest', 'matcha', 'coral', 'snowridge', 'sunset']
    },
    {
      key: 'future',
      label: '科技与夜景',
      description: '高对比、发光、现代展陈与中控感。',
      keys: ['default', 'ocean', 'aurora', 'midnight', 'nebula', 'arcade', 'studio', 'steel', 'ember']
    },
    {
      key: 'light',
      label: '轻盈与日常',
      description: '更明快、柔和，适合长时间后台操作。',
      keys: ['mono', 'sakura', 'lavender', 'citrus', 'glacier', 'alpine']
    }
  ]
  return groups
    .map(group => ({
      ...group,
      items: group.keys
        .map(key => allEntries.find(([themeKey]) => themeKey === key))
        .filter((entry): entry is [string, any] => !!entry)
    }))
    .filter(group => group.items.length > 0)
})

const styleAffinityMap: Record<string, string[]> = {
  material: ['default', 'ocean', 'aurora', 'midnight', 'studio', 'steel', 'glacier', 'mono'],
  glass: ['ocean', 'glacier', 'sakura', 'lavender', 'citrus', 'aurora', 'lotus'],
  classic: ['rosewood', 'baroque', 'cathedral', 'manor', 'parchment', 'cocoa', 'velvet'],
  gallery: ['ink', 'celadon', 'imperial', 'jadeite', 'lotus', 'forest', 'alpine'],
  compact: ['mono', 'mint', 'matcha', 'citrus', 'glacier', 'studio', 'steel'],
  brutalist: ['ember', 'arcade', 'noir', 'steel', 'vermilion', 'cathedral'],
  paper: ['parchment', 'cocoa', 'matcha', 'desert', 'harvest', 'peony'],
  neon: ['aurora', 'midnight', 'nebula', 'arcade', 'ember', 'ocean'],
  zen: ['ink', 'celadon', 'bamboo', 'lotus', 'matcha', 'forest', 'jadeite'],
  terminal: ['midnight', 'nebula', 'noir', 'steel', 'studio', 'ember']
}

const styleMatchScore = (themeKey: string) => {
  const affinity = styleAffinityMap[themeStore.currentStyleFamily] || []
  if (affinity.includes(themeKey)) return 2
  const label = recommendedStyleLabel(themeKey)
  if (label.includes(currentStyleFamilyName.value)) return 1
  return 0
}

const availableThemeFilters = computed(() => {
  return [
    { key: 'all', label: '全部主题', count: themeEntries.value.length },
    { key: 'matched', label: '匹配当前风格', count: themeEntries.value.filter(([key]) => styleMatchScore(key) > 0).length },
    ...baseThemeGroups.value.map(group => ({
      key: group.key,
      label: group.label,
      count: group.items.length
    }))
  ]
})

const filteredThemeGroups = computed(() => {
  const search = themeSearch.value.trim().toLowerCase()

  return baseThemeGroups.value
    .map(group => {
      const items = group.items
        .filter(([key, def]) => {
          if (activeThemeFilter.value === 'matched' && styleMatchScore(key) <= 0) return false
          if (activeThemeFilter.value !== 'all' && activeThemeFilter.value !== 'matched' && group.key !== activeThemeFilter.value) return false
          if (!search) return true
          const haystack = [
            key,
            def.name,
            def.mood,
            group.label,
            group.description,
            themeScenarioLabel(key),
            recommendedStyleLabel(key),
            currentStyleFamilyName.value
          ]
            .join(' ')
            .toLowerCase()
          return haystack.includes(search)
        })
        .sort((a, b) => {
          if (a[0] === themeStore.currentThemeKey) return -1
          if (b[0] === themeStore.currentThemeKey) return 1
          const scoreDiff = styleMatchScore(b[0]) - styleMatchScore(a[0])
          if (scoreDiff !== 0) return scoreDiff
          return a[1].name.localeCompare(b[1].name, 'zh-CN')
        })

      return {
        ...group,
        items
      }
    })
    .filter(group => group.items.length > 0)
})

const visibleThemeCount = computed(() => filteredThemeGroups.value.reduce((sum, group) => sum + group.items.length, 0))
const matchedThemeEntries = computed(() => {
  return themeEntries.value
    .filter(([key]) => styleMatchScore(key) > 0)
    .sort((a, b) => {
      if (a[0] === themeStore.currentThemeKey) return -1
      if (b[0] === themeStore.currentThemeKey) return 1
      const scoreDiff = styleMatchScore(b[0]) - styleMatchScore(a[0])
      if (scoreDiff !== 0) return scoreDiff
      return a[1].name.localeCompare(b[1].name, 'zh-CN')
    })
    .slice(0, 6)
})

const currentThemeEntry = computed(() => themeEntries.value.find(([key]) => key === themeStore.currentThemeKey) || null)

const currentThemeCategoryLabel = computed(() => {
  const matchedGroup = baseThemeGroups.value.find(group => group.items.some(([key]) => key === themeStore.currentThemeKey))
  return matchedGroup?.label || '精选主题'
})

const currentStyleFamilyName = computed(() => {
  const matched = styleFamilyEntries.value.find(([key]) => key === themeStore.currentStyleFamily)
  return matched?.[1]?.name || 'Material'
})

const themeScenarioLabel = (key: string) => {
  if (['default', 'ocean', 'aurora', 'midnight', 'nebula', 'studio', 'steel', 'arcade'].includes(key)) return '适合中控 / 科技后台'
  if (['ink', 'vermilion', 'celadon', 'imperial', 'bamboo', 'peony', 'jadeite', 'lotus'].includes(key)) return '适合内容 / 品牌后台'
  if (['forest', 'mint', 'desert', 'harvest', 'matcha', 'coral', 'alpine', 'sunset'].includes(key)) return '适合日常运营'
  if (['rosewood', 'baroque', 'cathedral', 'manor', 'noir', 'parchment', 'velvet', 'cocoa'].includes(key)) return '适合企业 / 展陈后台'
  return '适合长时间管理'
}

const recommendedStyleLabel = (key: string) => {
  if (['default', 'ocean', 'aurora', 'midnight', 'nebula', 'steel', 'studio', 'arcade', 'ember'].includes(key)) return '推荐搭配 Material / Neon'
  if (['ink', 'celadon', 'bamboo', 'lotus', 'matcha', 'alpine'].includes(key)) return '推荐搭配 Zen / Gallery'
  if (['rosewood', 'baroque', 'cathedral', 'manor', 'parchment', 'cocoa', 'velvet'].includes(key)) return '推荐搭配 Classic / Paper'
  if (['mono', 'sakura', 'lavender', 'citrus', 'glacier'].includes(key)) return '推荐搭配 Glass / Material'
  return '推荐搭配 Compact / Gallery'
}

const isThemeMatchedToStyle = (key: string) => styleMatchScore(key) > 0

const handleSetColorMode = async (dark: boolean) => {
  if (dark === themeStore.isDark) return
  await themeStore.setColorMode(dark)
}

const handleSetThemeKey = async (key: string) => {
  if (key === themeStore.currentThemeKey) return
  await themeStore.setThemeKey(key as any)
}

const handleSetStyleFamily = async (key: string) => {
  if (key === themeStore.currentStyleFamily) return
  await themeStore.setStyleFamily(key as any)
}

const previewStyle = (key: string) => {
  const def = (themeStore.themes as Record<string, any>)[key]
  return {
    background: `radial-gradient(circle at 10% 12%, ${def.preview.primary}, transparent 34%), radial-gradient(circle at 88% 16%, ${def.preview.secondary}, transparent 40%), linear-gradient(135deg, ${def.preview.base}, color-mix(in srgb, ${def.preview.base} 58%, #ffffff10))`
  }
}

const swatchGradient = (start: string, end: string) => {
  return `linear-gradient(135deg, ${start}, ${end})`
}
</script>
