<script setup lang="ts">
/**
 * 品牌锁定组合：图标 + 中文名（仅中文，不带英文副名）+ 中文定位短句。
 * 登录页（深色底）与两端顶栏（浅色底）共用，保证切换时视觉连续。
 */
import BrandLogo from './BrandLogo.vue'

withDefaults(
  defineProps<{
    /** 图标外框边长（px） */
    size?: number
    /** 图标圆角（px） */
    radius?: number
    /** 定位短句，传空字符串可隐藏 */
    subtitle?: string
    /** 深色底（登录页）用 dark */
    tone?: 'light' | 'dark'
    /** 主标题字号（px） */
    titleSize?: number
  }>(),
  {
    size: 32,
    radius: 9,
    subtitle: '智能体编排 · 知识问答',
    tone: 'light',
    titleSize: 16,
  },
)
</script>

<template>
  <div class="flex items-center gap-3">
    <BrandLogo :size="size" :radius="radius" />
    <div class="leading-tight">
      <p
        class="font-semibold tracking-[0.06em]"
        :class="tone === 'dark' ? 'text-white' : 'text-ink-900'"
        :style="{ fontSize: `${titleSize}px` }"
      >
        智枢
      </p>
      <p
        v-if="subtitle"
        class="mt-0.5 text-[10.5px] tracking-[0.08em]"
        :class="tone === 'dark' ? 'text-white/45' : 'text-ink-400'"
      >
        {{ subtitle }}
      </p>
    </div>
  </div>
</template>
