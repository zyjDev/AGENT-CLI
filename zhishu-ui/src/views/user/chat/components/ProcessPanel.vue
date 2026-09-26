<script setup lang="ts">
/**
 * 思考与执行过程面板：按 SSE 的 type/subType 分色呈现。
 */
import { computed } from 'vue'
import { Loader2 } from 'lucide-vue-next'
import { resolveStageMeta } from '@/enums/sse'
import type { ChatMessage } from '@/types/chat'
import MarkdownView from './MarkdownView.vue'

const props = defineProps<{
  messages: ChatMessage[]
  /** 是否正在流式接收 */
  loading: boolean
}>()

const items = computed(() =>
  props.messages.map((item) => ({ ...item, meta: resolveStageMeta(item.type, item.subType) })),
)

const formatTime = (timestamp: number): string =>
  new Date(timestamp).toLocaleTimeString('zh-CN', { hour12: false })
</script>

<template>
  <section class="flex min-h-0 flex-col">
    <div class="flex items-center justify-between border-b border-[#F1F3F9] bg-[#FCFCFE] px-4 py-2.5">
      <div class="flex items-center gap-2">
        <svg viewBox="0 0 24 24" class="h-4 w-4 text-brand" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round">
          <path d="M12 3a6 6 0 0 1 3.5 10.9V17h-7v-3.1A6 6 0 0 1 12 3Z" />
          <path d="M10 20h4" />
        </svg>
        <h2 class="text-[13px] font-medium">思考与执行过程</h2>
        <span v-if="items.length" class="rounded-full bg-[#EEF0FE] px-2 py-0.5 text-[11px] text-brand">
          {{ items.length }} 条
        </span>
      </div>
      <span class="text-[11.5px] text-ink-400">实时流式推送 · SSE</span>
    </div>

    <div class="scroll-thin flex-1 space-y-3 overflow-y-auto p-4">
      <div v-if="!items.length && !loading" class="flex h-full flex-col items-center justify-center text-center">
        <p class="text-[13px] text-ink-600">暂无执行过程</p>
        <p class="mt-1.5 max-w-[320px] text-[12px] leading-6 text-ink-400">
          发送问题后，这里会按「分析 → 执行 → 监督 → 总结」逐步展示智能体的思考与调用过程。
        </p>
      </div>

      <article
        v-for="item in items"
        :key="item.id"
        class="animate-fade-up rounded-xl border border-[#EDF0F8] bg-white p-3.5"
      >
        <div class="mb-2 flex items-center gap-2">
          <span class="stage" :class="item.meta.className">{{ item.meta.label }}</span>
          <span v-if="item.subType" class="sub-tag">{{ item.subType }}</span>
          <span class="ml-auto shrink-0 text-[11px] text-ink-400">
            <template v-if="item.step !== null">步骤 {{ item.step }} · </template>{{ formatTime(item.timestamp) }}
          </span>
        </div>
        <MarkdownView :content="item.content" />
      </article>

      <div v-if="loading" class="flex items-center gap-2 rounded-xl border border-dashed border-[#D9DEF0] bg-white/60 px-3.5 py-3">
        <Loader2 :size="14" class="animate-spin text-brand" />
        <span class="text-[12px] text-ink-600">正在接收流式消息…</span>
        <span class="ml-1 flex items-center gap-1">
          <span class="animate-pulse-dot h-1.5 w-1.5 rounded-full bg-brand"></span>
          <span class="animate-pulse-dot h-1.5 w-1.5 rounded-full bg-brand" style="animation-delay: 0.15s"></span>
          <span class="animate-pulse-dot h-1.5 w-1.5 rounded-full bg-brand" style="animation-delay: 0.3s"></span>
        </span>
      </div>
    </div>
  </section>
</template>
