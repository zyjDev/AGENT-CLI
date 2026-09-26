<script setup lang="ts">
/**
 * 用户端智能对话（登录后默认落地页）。
 *
 * 数据流：输入 → streamAutoAgent(fetch + SSE) → 过程消息与最终结果双栏呈现。
 * 会话与消息持久化在 localStorage（useSessions），不引入后端会话存储。
 *
 * 流式期性能取舍：过程中不把每条消息写回 store（避免整树重渲染 + 反复序列化），
 * 先在本地 reactive 里累积，结束时一次性落库；每 5 条做一次快照兜底刷新丢失。
 */
import { computed, onMounted, ref, watch } from 'vue'
import { message, Select } from 'ant-design-vue'
import { RefreshCw, Send, Square } from 'lucide-vue-next'
import { AgentApi, type AvailableAgent } from '@/api/agent'
import { AiClientRagOrderApi, type AiClientRagOrderItem } from '@/api/ai-client-rag-order'
import { streamAutoAgent, type SseHandle } from '@/composables/useSse'
import { reloadSessionsForCurrentUser, useSessions } from '@/composables/useSessions'
import { SseMessageType, SseSubType, type SseMessage } from '@/enums/sse'
import type { ChatMessage, ChatPreset, ChatRound } from '@/types/chat'
import ProcessPanel from './components/ProcessPanel.vue'
import ResultPanel from './components/ResultPanel.vue'
import SessionList from './components/SessionList.vue'

const MAX_STEP_OPTIONS = [1, 2, 3, 5, 10, 20, 50]
const MAX_INPUT_LENGTH = 1000
/** 后端默认放行的 Auto Agent（根 README：aiAgentId 目前支持 "3"） */
const DEFAULT_AGENT_ID = '3'
/** 流式过程中每积累多少条消息做一次快照落库 */
const SNAPSHOT_EVERY = 5

const PRESETS: ChatPreset[] = [
  {
    label: 'Spring Boot 性能调优学习计划',
    prompt: '请基于知识库帮我制定一份 Spring Boot 性能调优的学习计划，要求分阶段、可执行，并给出关键指标的观测方法。',
  },
  {
    label: 'RAG 召回率优化建议',
    prompt: '我们的 RAG 知识库召回率偏低，请给出可落地的排查与优化建议，并说明每项措施的验证方式。',
  },
  {
    label: 'MCP 工具接入排查清单',
    prompt: 'MCP 工具接入后调用偶发超时，请给出排查清单与常见原因。',
  },
]

const {
  sessions,
  currentId,
  currentSession,
  ensureSession,
  createSession,
  selectSession,
  removeSession,
  clearAll,
  appendRound,
  updateRound,
  updateSessionMeta,
} = useSessions()

const agents = ref<AvailableAgent[]>([])
const agentsLoading = ref(false)
const selectedAgentId = ref(DEFAULT_AGENT_ID)
const maxStep = ref(5)

/**
 * 知识库（RAG）选择：列表来自 /ai-client-rag-order/query-enabled，
 * 后端已按归属过滤（公共库 + 本人私有库），所以这里不需要前端再筛。
 * 选中后把 knowledgeTag 随对话请求发给后端做检索过滤。
 */
const knowledgeBases = ref<AiClientRagOrderItem[]>([])
const knowledgeLoading = ref(false)
const selectedRagId = ref<string | undefined>()
const selectedPreset = ref<string | undefined>(undefined)

const inputText = ref('')
const streaming = ref(false)
const liveMessages = ref<ChatMessage[]>([])
const liveResult = ref('')
const liveError = ref('')
const liveDurationMs = ref(0)

let activeSessionId = ''
let activeRoundId = ''
let startedAt = 0
let handle: SseHandle | null = null
let finalized = true

/* ---------------------------- 展示层派生状态 ---------------------------- */

const currentRound = computed<ChatRound | undefined>(() => currentSession.value?.rounds[0])

/** 当前展示的轮次是否就是正在流式输出的那一轮 */
const isLiveRound = computed(
  () => streaming.value && activeRoundId !== '' && activeRoundId === currentRound.value?.id,
)

const displayMessages = computed<ChatMessage[]>(() =>
  isLiveRound.value ? liveMessages.value : (currentRound.value?.messages ?? []),
)
const displayResult = computed(() => (isLiveRound.value ? liveResult.value : (currentRound.value?.result ?? '')))
const displayError = computed(() => (isLiveRound.value ? liveError.value : (currentRound.value?.error ?? '')))
const displayLoading = computed(() => streaming.value && isLiveRound.value)

const displayStepCount = computed(() => {
  const messages = displayMessages.value
  return messages.reduce((max, item) => Math.max(max, item.step ?? 0), 0)
})

const displayDurationMs = computed(() => {
  if (isLiveRound.value) return liveDurationMs.value
  const messages = currentRound.value?.messages ?? []
  if (messages.length < 2) return 0
  return (messages[messages.length - 1]?.timestamp ?? 0) - (messages[0]?.timestamp ?? 0)
})

const agentOptions = computed(() => {
  if (agents.value.length) {
    return agents.value.map((item) => ({ value: item.agentId, label: `${item.agentName}（agentId=${item.agentId}）` }))
  }
  return [{ value: DEFAULT_AGENT_ID, label: 'Auto Agent 自动智能对话体（agentId=3）' }]
})

const presetOptions = computed(() => PRESETS.map((item) => ({ value: item.label, label: item.label })))

const knowledgeOptions = computed(() =>
  knowledgeBases.value.map((item) => ({
    value: item.ragId,
    label: `${item.ragName}（${item.knowledgeTag}）`,
  })),
)

/** 当前选中的知识库标签：未选 = 不限定知识域（沿用智能体自身的 RAG 配置） */
const selectedKnowledgeTag = computed(() => {
  const current = knowledgeBases.value.find((item) => item.ragId === selectedRagId.value)
  return current?.knowledgeTag || undefined
})

const canSend = computed(() => Boolean(inputText.value.trim()) && !streaming.value)

/* ---------------------------- 行为 ---------------------------- */

async function loadKnowledgeBases(): Promise<void> {
  knowledgeLoading.value = true
  try {
    const list = await AiClientRagOrderApi.queryEnabled()
    knowledgeBases.value = Array.isArray(list) ? list : []
    // 之前选中的库若已不可见（被删除 / 换了账号），清掉选择避免带着脏 tag 去检索
    if (selectedRagId.value && !knowledgeBases.value.some((item) => item.ragId === selectedRagId.value)) {
      selectedRagId.value = undefined
    }
  } catch (error) {
    console.error('[chat] 获取知识库列表失败', error)
  } finally {
    knowledgeLoading.value = false
  }
}

async function loadAgents(): Promise<void> {
  agentsLoading.value = true
  try {
    const list = await AgentApi.queryAvailableAgents()
    agents.value = Array.isArray(list) ? list : []
    // 列表里若没有默认 Auto Agent，则回落到第一项，避免选中值不在选项中
    if (agents.value.length && !agents.value.some((item) => item.agentId === selectedAgentId.value)) {
      selectedAgentId.value = agents.value[0]?.agentId ?? DEFAULT_AGENT_ID
    }
  } catch (error) {
    // 具体原因（网络 / 401）已由请求层提示，这里只保留默认 Auto Agent 保证页面可用
    console.error('[chat] 获取可用智能体失败，已回落到默认智能体', error)
  } finally {
    agentsLoading.value = false
  }
}

function roundId(): string {
  return `round_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`
}

/** 把本地累积的流式数据一次性落库 */
function finalize(aborted: boolean): void {
  if (finalized) return
  finalized = true
  streaming.value = false
  liveDurationMs.value = startedAt ? Date.now() - startedAt : 0

  updateRound(activeSessionId, activeRoundId, {
    messages: liveMessages.value.map((item) => ({ ...item })),
    result: liveResult.value,
    completed: !liveError.value && !aborted,
    error: liveError.value || undefined,
  })

  handle = null
}

function handleMessage(msg: SseMessage): void {
  const content = msg.content ?? ''
  liveMessages.value.push({
    id: `${activeRoundId}_${msg.step ?? 0}_${msg.timestamp ?? Date.now()}_${liveMessages.value.length}`,
    type: msg.type,
    subType: msg.subType ?? null,
    step: msg.step ?? null,
    content,
    timestamp: msg.timestamp ?? Date.now(),
  })

  // 最终结果来源：summary_overview 为主线；complete 若带正文也并入（后端完成帧）
  const isSummaryOverview = msg.type === SseMessageType.Summary && msg.subType === SseSubType.SummaryOverview
  const isCompleteWithContent = msg.type === SseMessageType.Complete && Boolean(content.trim())
  if ((isSummaryOverview || isCompleteWithContent) && content.trim()) {
    liveResult.value = liveResult.value ? `${liveResult.value}\n\n${content}` : content
  }

  if (msg.type === SseMessageType.Error && content.trim()) {
    liveError.value = content.trim()
  }

  // 兜底快照：长时间流式期间刷新页面不至于全丢
  if (liveMessages.value.length % SNAPSHOT_EVERY === 0) {
    updateRound(activeSessionId, activeRoundId, {
      messages: liveMessages.value.map((item) => ({ ...item })),
      result: liveResult.value,
    })
  }
}

function send(): void {
  const text = inputText.value.trim()
  if (!text) {
    message.warning('请输入您的问题')
    return
  }
  if (streaming.value) return

  const session = ensureSession(selectedAgentId.value, maxStep.value)
  const id = roundId()

  appendRound(session.id, {
    id,
    question: text,
    askedAt: Date.now(),
    messages: [],
    result: '',
    completed: false,
  })

  selectedPreset.value = undefined
  inputText.value = ''
  liveMessages.value = []
  liveResult.value = ''
  liveError.value = ''
  liveDurationMs.value = 0
  activeSessionId = session.id
  activeRoundId = id
  startedAt = Date.now()
  finalized = false
  streaming.value = true

  handle = streamAutoAgent(
    {
      aiAgentId: session.agentId,
      message: text,
      sessionId: session.id,
      maxStep: session.maxStep,
      // 选了知识库才带 tag：不选时保持智能体自身的 RAG 配置
      knowledgeTag: selectedKnowledgeTag.value,
    },
    {
      onMessage: handleMessage,
      onComplete: ({ aborted }) => {
        finalize(aborted)
        if (aborted) message.info('已停止本次执行')
      },
      onError: (error) => {
        liveError.value = error.message
        finalize(false)
      },
    },
  )
}

function stop(): void {
  handle?.abort()
}

function handleCreateSession(): void {
  createSession(selectedAgentId.value, maxStep.value)
  message.success('已新建对话')
}

function handleClearAll(): void {
  clearAll()
  message.success('已清空全部会话记录')
}

function applyPreset(value: string | undefined): void {
  if (!value) return
  const preset = PRESETS.find((item) => item.label === value)
  if (preset) inputText.value = preset.prompt
}

function onAgentChange(value: unknown): void {
  if (typeof value === 'string') updateSessionMeta(currentId.value, { agentId: value })
}

function onMaxStepChange(value: unknown): void {
  if (typeof value === 'number') updateSessionMeta(currentId.value, { maxStep: value })
}

/** 切换会话时，把该会话的智能体与步数同步到工具条 */
watch(currentId, () => {
  const session = currentSession.value
  if (!session) return
  selectedAgentId.value = session.agentId || DEFAULT_AGENT_ID
  maxStep.value = session.maxStep || 5
})

watch(selectedPreset, (value) => applyPreset(value))

onMounted(() => {
  // 会话按用户隔离：模块级单例只在首次 import 时读过 localStorage，
  // 登录 / 换账号后必须按当前用户重载，否则会显示上一个账号的会话
  reloadSessionsForCurrentUser()
  ensureSession(DEFAULT_AGENT_ID, 5)
  void loadAgents()
  void loadKnowledgeBases()
})
</script>

<template>
  <div class="flex h-full min-h-0">
    <SessionList
      :sessions="sessions"
      :current-id="currentId"
      @create="handleCreateSession"
      @select="selectSession"
      @remove="removeSession"
      @clear="handleClearAll"
    />

    <div class="flex min-w-0 flex-1 flex-col">
      <!-- 工具条 -->
      <div class="flex flex-wrap items-center gap-3 border-b border-line bg-white px-4 py-2.5">
        <div class="flex items-center gap-2">
          <span class="text-[12px] text-ink-400">智能体</span>
          <Select
            :value="selectedAgentId"
            :options="agentOptions"
            :loading="agentsLoading"
            class="min-w-[240px]"
            size="small"
            @change="onAgentChange"
          />
        </div>

        <div class="flex items-center gap-2">
          <span class="text-[12px] text-ink-400">最大执行步数</span>
          <Select :value="maxStep" :options="MAX_STEP_OPTIONS.map((v) => ({ value: v, label: String(v) }))" size="small" class="w-[76px]" @change="onMaxStepChange" />
        </div>

        <div class="flex items-center gap-2">
          <span class="text-[12px] text-ink-400">知识库</span>
          <Select
            v-model:value="selectedRagId"
            :options="knowledgeOptions"
            :loading="knowledgeLoading"
            :allow-clear="true"
            placeholder="不限定知识库"
            size="small"
            class="min-w-[200px]"
          />
        </div>

        <div class="flex items-center gap-2">
          <span class="text-[12px] text-ink-400">预设案例</span>
          <Select
            v-model:value="selectedPreset"
            :options="presetOptions"
            :allow-clear="true"
            placeholder="选择一个案例填入输入框"
            size="small"
            class="min-w-[200px]"
          />
        </div>

        <div class="ml-auto flex items-center gap-2">
          <span class="text-[11.5px] text-ink-400">
            共 {{ currentSession?.rounds.length ?? 0 }} 轮对话
          </span>
          <button
            type="button"
            class="btn-ghost flex h-8 cursor-pointer items-center gap-1.5 px-2.5 text-[12.5px]"
            :disabled="agentsLoading"
            @click="loadAgents"
          >
            <RefreshCw :size="13" :class="agentsLoading ? 'animate-spin' : ''" />
            刷新
          </button>
        </div>
      </div>

      <!-- 双栏 -->
      <div class="grid min-h-0 flex-1 grid-cols-1 divide-x divide-line xl:grid-cols-2">
        <ProcessPanel :messages="displayMessages" :loading="displayLoading" />
        <ResultPanel
          :content="displayResult"
          :loading="displayLoading"
          :error="displayError"
          :step-count="displayStepCount"
          :duration-ms="displayDurationMs"
        />
      </div>

      <!-- 输入区 -->
      <div class="border-t border-line bg-white px-4 py-3">
        <div class="rounded-xl border border-line p-2 transition focus-within:border-brand-500 focus-within:ring-[3px] focus-within:ring-brand-500/20">
          <textarea
            v-model="inputText"
            rows="2"
            :maxlength="MAX_INPUT_LENGTH"
            :disabled="streaming"
            placeholder="请输入您的问题，例如：帮我评估当前连接池配置是否存在风险..."
            class="scroll-thin w-full resize-none border-0 bg-transparent text-[13px] leading-6 text-ink-900 outline-none placeholder:text-ink-300 disabled:opacity-60"
            @keydown.enter.exact.prevent="send"
          ></textarea>

          <div class="flex items-center justify-between pt-1">
            <span class="text-[11px] text-ink-400">Enter 发送 · Shift + Enter 换行</span>
            <div class="flex items-center gap-3">
              <span class="text-[11px] text-ink-400">{{ inputText.length }} / {{ MAX_INPUT_LENGTH }}</span>
              <button
                v-if="streaming"
                type="button"
                class="btn-ghost flex h-8 cursor-pointer items-center gap-1.5 px-3 text-[12.5px] text-err hover:!border-[#F5C2C4] hover:!text-err"
                @click="stop"
              >
                <Square :size="11" />
                停止
              </button>
              <button
                type="button"
                class="btn-grad flex h-8 items-center gap-1.5 px-4 text-[12.5px]"
                :class="canSend ? 'cursor-pointer' : 'cursor-not-allowed opacity-60'"
                :disabled="!canSend"
                @click="send"
              >
                <Send :size="13" />
                {{ streaming ? '执行中' : '发送' }}
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
