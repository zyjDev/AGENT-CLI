<script setup lang="ts">
/**
 * 最终结果面板：Markdown 渲染 + 全文复制 + 生成信息。
 */
import { message } from 'ant-design-vue'
import { Copy, FileText, Loader2 } from 'lucide-vue-next'
import MarkdownView from './MarkdownView.vue'

const props = defineProps<{
  content: string
  loading: boolean
  /** 出错时的提示（有值时优先展示错误态） */
  error?: string
  /** 生成信息：步骤数与耗时 */
  stepCount?: number
  durationMs?: number
}>()

async function copyAll(): Promise<void> {
  if (!props.content) return
  try {
    await navigator.clipboard.writeText(props.content)
    message.success('结果已复制为 Markdown 源码')
  } catch (error) {
    console.error('[result] 复制失败', error)
    message.error('复制失败，请手动选择内容')
  }
}

const formatDuration = (ms?: number): string => {
  if (!ms || ms < 0) return '—'
  return `${(ms / 1000).toFixed(1)}s`
}
</script>

<template>
  <section class="flex min-h-0 flex-col bg-white">
    <div class="flex items-center justify-between border-b border-[#F1F3F9] bg-[#FCFCFE] px-4 py-2.5">
      <div class="flex items-center gap-2">
        <svg viewBox="0 0 24 24" class="h-4 w-4 text-ok" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round">
          <path d="M5 4.5h9l5 5V20H5z" />
          <path d="M14 4.5V10h5M8.5 14h7M8.5 17h4" />
        </svg>
        <h2 class="text-[13px] font-medium">最终结果</h2>
        <span class="rounded-full bg-[#E4F6EA] px-2 py-0.5 text-[11px] text-[#15803D]">Markdown 渲染</span>
      </div>
      <button
        type="button"
        class="btn-ghost flex h-7 cursor-pointer items-center gap-1.5 px-2.5 text-[12px] disabled:cursor-not-allowed disabled:opacity-50"
        :disabled="!content"
        @click="copyAll"
      >
        <Copy :size="12" />
        复制全文
      </button>
    </div>

    <div class="scroll-thin flex-1 overflow-y-auto px-5 py-4">
      <div v-if="error" class="flex items-start gap-2.5 rounded-xl bg-[#FEF4F4] px-4 py-3">
        <span class="mt-0.5 text-err">✕</span>
        <div>
          <p class="text-[13px] font-medium text-err">本次执行未完成</p>
          <p class="mt-1 text-[12.5px] leading-6 text-ink-600">{{ error }}</p>
        </div>
      </div>

      <div v-else-if="loading && !content" class="flex h-full flex-col items-center justify-center gap-2 text-center">
        <Loader2 :size="20" class="animate-spin text-brand" />
        <p class="text-[12.5px] text-ink-400">正在生成结果，完成后自动渲染</p>
      </div>

      <div v-else-if="!content" class="flex h-full flex-col items-center justify-center text-center">
        <FileText :size="22" class="text-ink-300" />
        <p class="mt-2 text-[13px] text-ink-600">暂无结果</p>
        <p class="mt-1.5 max-w-[300px] text-[12px] leading-6 text-ink-400">
          总结阶段产出的内容会渲染在这里，支持标题、列表、表格与代码高亮。
        </p>
      </div>

      <template v-else>
        <MarkdownView :content="content" />
        <div class="mt-5 flex flex-wrap items-center gap-3 rounded-xl bg-page px-4 py-3 text-[11.5px] text-ink-600">
          <span class="flex items-center gap-1.5">
            <span class="h-1.5 w-1.5 rounded-full bg-ok"></span>已完成
          </span>
          <span class="text-[#D9DEF0]">|</span>
          <span>执行步骤 {{ stepCount ?? 0 }}</span>
          <span class="text-[#D9DEF0]">|</span>
          <span>耗时 {{ formatDuration(durationMs) }}</span>
        </div>
      </template>
    </div>
  </section>
</template>
