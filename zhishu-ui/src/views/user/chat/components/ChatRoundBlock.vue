<script setup lang="ts">
/**
 * 一轮对话块：提问 + 过程（可折叠成行）+ 最终答案。
 *
 * 由原 ChatTranscript 拆出并**去掉外层标题栏与自己那层滚动条**：
 * 一个会话现在把全部轮次自上而下渲染（见 chat/index.vue 的 displayRounds），
 * 滚动统一由页面负责。原来只渲染最新一轮，用户再发一句，上一轮的内容就从界面上"消失"了。
 */
import { computed, ref, watch } from 'vue'
import { Button, message } from 'ant-design-vue'
import { ChevronDown, Copy, Loader2 } from 'lucide-vue-next'
import { resolveStageMeta, SseMessageType, SseSubType } from '@/enums/sse'
import type { ChatMessage } from '@/types/chat'
import MarkdownView from './MarkdownView.vue'

const props = defineProps<{
  question: string
  askedAt: number
  messages: ChatMessage[]
  /** 最终答案（Markdown 源码） */
  content: string
  /** 是否正在流式接收 */
  loading: boolean
  /** 出错时的提示（有值时展示错误态） */
  error?: string
  /** 错误码：0004 = 需要先配置自己的模型 Key，此时给出「去配置」出口 */
  errorCode?: string
  /** 生成信息：步骤数与耗时 */
  stepCount?: number
  durationMs?: number
  /** 过程是否默认展开；不传 = 按每条内容自动决定（长正文展开、进度噪音收起） */
  expandAll?: boolean | null
}>()

const emit = defineEmits<{ (e: 'goto-config'): void }>()

/** 后端 ResponseCode.NEED_OWN_MODEL_KEY —— 唯一当前需要"给出口"的错误 */
const NEED_OWN_MODEL_KEY_CODE = '0004'

/**
 * 治理类事件默认收起：它们是进度噪音，不是产出。
 * 内容型事件（分析/执行/监督/总结各段）默认展开，用户一眼能看到模型到底说了什么。
 */
const NOISY_SUBTYPES: ReadonlySet<string> = new Set([
  SseSubType.NodeStart,
  SseSubType.NodeEnd,
  SseSubType.NodeSuccess,
  SseSubType.NodeRetry,
  SseSubType.NodeTimeout,
  SseSubType.NodeDegrade,
  SseSubType.NodeFail,
  SseSubType.AnalysisStatus,
  SseSubType.AnalysisProgress,
])

/** 用户手动展开/收起过的项：覆盖默认策略 */
const userToggled = ref<Record<string, boolean>>({})

/** 工具条上的「展开过程/收起过程」切换后，清掉逐条的手动状态，否则会出现一半开一半关 */
watch(
  () => props.expandAll,
  () => {
    userToggled.value = {}
  },
)

const items = computed(() =>
  props.messages
    // 元信息帧不进过程列表：complete 帧的正文是「执行完成」，整段 summary 帧就是答案本身，
    // 增量帧（summary_delta）每次带的是「到目前为止的全文」 —— 留在列表里就是十几条重复条目
    // （答案区与页脚已经表达了同样的信息）。这里过滤，历史轮次里已存下的脏数据也能显示干净。
    .filter(
      (item) =>
        item.type !== SseMessageType.Complete &&
        item.subType !== SseSubType.SummaryDelta &&
        !(item.type === SseMessageType.Summary && !item.subType),
    )
    .map((item) => ({
      ...item,
      meta: resolveStageMeta(item.type, item.subType),
      defaultOpen: !NOISY_SUBTYPES.has(item.subType ?? '') && item.content.trim().length > 80,
    })),
)

type RoundItem = (typeof items.value)[number]

function isOpen(item: RoundItem): boolean {
  const manual = userToggled.value[item.id]
  if (manual !== undefined) return manual
  if (props.expandAll === true || props.expandAll === false) return props.expandAll
  return item.defaultOpen
}

function toggle(item: RoundItem): void {
  userToggled.value = { ...userToggled.value, [item.id]: !isOpen(item) }
}

/** 收起时显示的一行摘要：取正文首个非空行，过长交给 CSS 截断 */
function preview(item: RoundItem): string {
  const line = item.content.split('\n').find((text) => text.trim()) ?? ''
  return (
    line
      // 预览是纯文本，把 Markdown 标记去掉，否则标题/加粗会露出 ** 与 # 这类噪音
      .replace(/^[#>\-*\s]+/, '')
      .replace(/\*\*/g, '')
      .trim() ||
    item.subType ||
    ''
  )
}

const formatTime = (timestamp: number): string =>
  new Date(timestamp).toLocaleTimeString('zh-CN', { hour12: false })

const formatDuration = (ms?: number): string => (!ms || ms < 0 ? '—' : `${(ms / 1000).toFixed(1)}s`)

const hasOutput = computed(() => items.value.length > 0 || Boolean(props.content))

async function copyAll(): Promise<void> {
  if (!props.content) return
  try {
    await navigator.clipboard.writeText(props.content)
    message.success('答案已复制为 Markdown 源码')
  } catch (error) {
    console.error('[round] 复制失败', error)
    message.error('复制失败，请手动选择内容')
  }
}
</script>

<template>
  <article class="border-b border-[#F1F3F9] px-5 py-3.5 last:border-b-0">
    <!-- 提问行 -->
    <div class="flex items-start gap-2">
      <span class="mt-0.5 shrink-0 rounded-md bg-[#EEF0FE] px-1.5 py-0.5 text-[11px] font-medium text-brand">问</span>
      <p class="min-w-0 flex-1 text-[13.5px] font-medium leading-6 text-ink-900">{{ question }}</p>
      <span class="mt-1 shrink-0 text-[11px] text-ink-400">{{ formatTime(askedAt) }}</span>
      <button
        v-if="content"
        type="button"
        class="btn-ghost mt-0.5 flex h-6 shrink-0 cursor-pointer items-center gap-1 px-2 text-[11.5px]"
        @click="copyAll"
      >
        <Copy :size="11" />
        复制答案
      </button>
    </div>

    <div class="mt-2">
      <div v-if="error" class="flex items-start gap-2.5 rounded-xl bg-[#FEF4F4] px-4 py-3">
        <span class="mt-0.5 text-err">✕</span>
        <div>
          <p class="text-[13px] font-medium text-err">本次执行未完成</p>
          <p class="mt-1 text-[12.5px] leading-6 text-ink-600">{{ error }}</p>
          <!-- 需要自己的模型 Key 时给个能点的出口，否则用户读完报错也不知道去哪儿配 -->
          <Button
            v-if="errorCode === NEED_OWN_MODEL_KEY_CODE"
            size="small"
            type="primary"
            class="mt-2"
            @click="emit('goto-config')"
          >
            去配置我的模型 Key
          </Button>
        </div>
      </div>

      <!-- 过程：折叠成一行行，点开看细节 -->
      <ol v-if="items.length" class="space-y-0.5">
        <li v-for="item in items" :key="item.id" class="rounded-lg transition-colors hover:bg-[#FAFBFF]">
          <button
            type="button"
            class="flex w-full cursor-pointer items-center gap-2 px-2 py-1.5 text-left"
            @click="toggle(item)"
          >
            <ChevronDown
              :size="13"
              class="shrink-0 text-ink-300 transition-transform"
              :class="isOpen(item) ? '' : '-rotate-90'"
            />
            <span class="stage shrink-0" :class="item.meta.className">{{ item.meta.label }}</span>
            <span class="min-w-0 flex-1 truncate text-[12px] text-ink-600">{{ preview(item) }}</span>
            <span class="shrink-0 text-[11px] text-ink-400">
              <template v-if="item.step !== null">步骤 {{ item.step }} · </template>{{ formatTime(item.timestamp) }}
            </span>
          </button>
          <div v-if="isOpen(item)" class="px-8 pb-2.5 pt-0.5">
            <MarkdownView :content="item.content" />
          </div>
        </li>
      </ol>

      <!-- 最终答案：内联在流水末尾（流式时逐字追加 + 光标） -->
      <div v-if="content" class="mt-2 border-t border-[#F1F3F9] pt-2.5">
        <div class="mb-2 flex items-center gap-2">
          <span class="stage stage-summary">最终答案</span>
          <span class="text-[11.5px] text-ink-400">Markdown 渲染</span>
        </div>
        <MarkdownView :content="content" />
        <span
          v-if="loading"
          class="ml-0.5 inline-block h-4 w-[2px] animate-pulse bg-brand align-text-bottom"
        ></span>
      </div>

      <div
        v-else-if="loading && !error"
        class="mt-2 flex items-center gap-2 rounded-xl border border-dashed border-[#D9DEF0] bg-white/60 px-3.5 py-3"
      >
        <Loader2 :size="14" class="animate-spin text-brand" />
        <span class="text-[12px] text-ink-600">正在执行，过程实时推送，完成后在主区输出答案…</span>
      </div>

      <div
        v-if="hasOutput && !loading"
        class="mt-3 flex flex-wrap items-center gap-3 rounded-xl bg-page px-4 py-2.5 text-[11.5px] text-ink-600"
      >
        <span class="flex items-center gap-1.5">
          <span class="h-1.5 w-1.5 rounded-full" :class="error ? 'bg-err' : 'bg-ok'"></span>
          {{ error ? '未完成' : '已完成' }}
        </span>
        <span class="text-[#D9DEF0]">|</span>
        <span>执行步骤 {{ stepCount ?? 0 }}</span>
        <span class="text-[#D9DEF0]">|</span>
        <span>耗时 {{ formatDuration(durationMs) }}</span>
      </div>
    </div>
  </article>
</template>
