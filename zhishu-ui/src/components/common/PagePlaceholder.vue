<script setup lang="ts">
/**
 * 页面占位（临时）。
 * 迁移期间先占住最终路由与文件路径，页面正文在后续步骤逐个替换实现。
 * 全部页面落地后本组件可删除。
 */
withDefaults(
  defineProps<{
    title: string
    description?: string
  }>(),
  { description: '该页面将在后续步骤实现。' },
)
</script>

<template>
  <div class="card-panel flex flex-col items-center justify-center px-6 py-16 text-center">
    <p class="text-[15px] font-medium text-ink-900">{{ title }}</p>
    <p class="mt-2 max-w-[560px] text-[12.5px] leading-6 text-ink-400">{{ description }}</p>
  </div>
</template>
