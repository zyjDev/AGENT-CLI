<script setup lang="ts">
/**
 * 对话输出（原「思考与执行过程」+「最终结果」两个面板合并而来）。
 *
 * 为什么合并：原来是左右各一半，过程在左、答案在右，读答案时眼睛要在两栏之间来回跳；
 * 而真实的阅读顺序是「过程 → 答案」。现在改成自上而下的一条流水账（参考 CodeBuddy 的输出框）：
 *   · 过程按事件收成一行行小标题，点开看细节（有正文产出的默认展开，进度类默认收起）；
 *   · 最终答案内联渲染在流水末尾，流式时逐字追加并带光标。
 *
 * 数据来源不变：messages 是过程事件（后端逐条推送），content 是最终答案（后端流式增量 / 整段下发）。
 */
import { computed, nextTick, ref, watch } from 'vue'
import { Button, message } from 'ant-design-vue'
import { ChevronDown, Copy, FileText, Loader2 } from 'lucide-vue-next'
import { resolveStageMeta, SseMessageType, SseSubType } from '@/enums/sse'
import type { ChatMessage } from '@/types/chat'
import MarkdownView from './MarkdownView.vue'

const props = defineProps<{
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

const items = computed(() =>
  props.messages
    // 元信息帧不进过程列表：complete 帧的正文是「执行完成」，整段 summary 帧就是答案本身，
    // 增量帧（summary_delta）每次带的是「到目前为止的全文」 —— 留在列表里就是十几条重复条目
    // （答案区与页脚已经表达了同样的信息）。这里再过滤一次，历史轮次里已存下的脏数据也能显示干净。
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

type TranscriptItem = (typeof items.value)[number]

function isOpen(item: TranscriptItem): boolean {
  return userToggled.value[item.id] ?? item.defaultOpen
}

function toggle(item: TranscriptItem): void {
  userToggled.value = { ...userToggled.value, [item.id]: !isOpen(item) }
}

const allOpen = computed(() => items.value.length > 0 && items.value.every((item) => isOpen(item)))

function toggleAll(): void {
  const target = !allOpen.value
  userToggled.value = Object.fromEntries(items.value.map((item) => [item.id, target])) as Record<string, boolean>
}

/** 收起时显示的一行摘要：取正文首个非空行，过长交给 CSS 截断 */
function preview(item: TranscriptItem): string {
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
    message.success('结果已复制为 Markdown 源码')
  } catch (error) {
    console.error('[transcript] 复制失败', error)
    message.error('复制失败，请手动选择内容')
  }
}

/**
 * 流式期间自动滚到底：新内容追加在末尾，不跟着滚就看不到正在生成的部分。
 * 只在 loading 时自动滚，避免用户手动往回翻时被硬拽回来。
 */
const scroller = ref<HTMLElement | null>(null)

watch(
  () => [props.messages.length, props.content.length, props.loading],
  async () => {
    if (!props.loading) return
    await nextTick()
    const el = scroller.value
    if (el) el.scrollTop = el.scrollHeight
  },
)

/**
 * 切换/加载某轮对话时也滚到底：过程条数多的时候（一轮十几个节点）答案在流水末尾，
 * 不滚动就完全看不到，用户会以为"没有输出"。轮次用首条消息 id 判定（每轮唯一）。
 */
watch(
  () => props.messages[0]?.id,
  async () => {
    await nextTick()
    const el = scroller.value
    if (el) el.scrollTop = el.scrollHeight
  },
  { immediate: true },
)
</script>

<template>
  <section class="flex min-h-0 flex-1 flex-col bg-white">
    <div class="flex items-center justify-between border-b border-[#F1F3F9] bg-[#FCFCFE] px-4 py-2.5">
      <div class="flex items-center gap-2">
        <svg
          viewBox="0 0 24 24"
          class="h-4 w-4 text-brand"
          fill="none"
          stroke="currentColor"
          stroke-width="1.9"
          stroke-linecap="round"
        >
          <path d="M4 5.5h16v10H9l-5 4z" />
        </svg>
        <h2 class="text-[13px] font-medium">对话输出</h2>
        <span v-if="loading" class="rounded-full bg-[#EEF0FE] px-2 py-0.5 text-[11px] text-brand">
          实时流式推送
        </span>
        <span v-else-if="hasOutput" class="rounded-full bg-[#E4F6EA] px-2 py-0.5 text-[11px] text-[#15803D]">
          已完成
        </span>
      </div>

      <div class="flex items-center gap-2">
        <button
          v-if="items.length"
          type="button"
          class="btn-ghost flex h-7 cursor-pointer items-center gap-1.5 px-2.5 text-[12px]"
          @click="toggleAll"
        >
          {{ allOpen ? '收起全部' : '展开全部' }}
        </button>
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
    </div>

    <div ref="scroller" class="scroll-thin flex-1 overflow-y-auto px-5 py-4">
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

      <div
        v-if="!hasOutput && !loading && !error"
        class="flex h-full flex-col items-center justify-center text-center"
      >
        <FileText :size="22" class="text-ink-300" />
        <p class="mt-2 text-[13px] text-ink-600">暂无输出</p>
        <p class="mt-1.5 max-w-[340px] text-[12px] leading-6 text-ink-400">
          发送问题后，这里会按「分析 → 执行 → 监督 → 总结」逐步展示智能体的思考过程，最后给出渲染好的答案。
        </p>
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
      <div v-if="content" class="mt-3 border-t border-[#F1F3F9] pt-3">
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
        class="mt-3 flex items-center gap-2 rounded-xl border border-dashed border-[#D9DEF0] bg-white/60 px-3.5 py-3"
      >
        <Loader2 :size="14" class="animate-spin text-brand" />
        <span class="text-[12px] text-ink-600">正在执行，过程实时推送，完成后在主区输出答案…</span>
      </div>

      <div
        v-if="hasOutput && !loading"
        class="mt-5 flex flex-wrap items-center gap-3 rounded-xl bg-page px-4 py-3 text-[11.5px] text-ink-600"
      >
        <span class="flex items-center gap-1.5">
          <span class="h-1.5 w-1.5 rounded-full bg-ok"></span>已完成
        </span>
        <span class="text-[#D9DEF0]">|</span>
        <span>执行步骤 {{ stepCount ?? 0 }}</span>
        <span class="text-[#D9DEF0]">|</span>
        <span>耗时 {{ formatDuration(durationMs) }}</span>
      </div>
    </div>
  </section>
</template>
