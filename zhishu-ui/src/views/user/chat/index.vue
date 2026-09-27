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
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { message, Select } from 'ant-design-vue'
import { RefreshCw, Send, Square } from 'lucide-vue-next'
import { AgentApi, type AvailableAgent } from '@/api/agent'
import { AiClientRagOrderApi, type AiClientRagOrderItem } from '@/api/ai-client-rag-order'
import { streamAutoAgent, type SseHandle } from '@/composables/useSse'
import { useRouter } from 'vue-router'
import { reloadSessionsForCurrentUser, useSessions } from '@/composables/useSessions'
import { SseMessageType, SseSubType, type SseMessage } from '@/enums/sse'
import type { ChatMessage, ChatPreset, ChatRound } from '@/types/chat'
import ChatRoundBlock from './components/ChatRoundBlock.vue'
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

const router = useRouter()

const inputText = ref('')
const streaming = ref(false)
const liveMessages = ref<ChatMessage[]>([])
const liveResult = ref('')
const liveError = ref('')
/** 错误码：用于识别"需要先配置自己的模型 Key"（0004）这类可操作错误 */
const liveErrorCode = ref('')
const liveDurationMs = ref(0)

let activeSessionId = ''
let activeRoundId = ''
let startedAt = 0
let handle: SseHandle | null = null
let finalized = true

/* ---------------------------- 展示层派生状态 ---------------------------- */

/**
 * 主区渲染**整个会话的全部轮次**（旧 → 新），而不是只渲染最新一轮。
 *
 * ⚠️ 原先只取 rounds[0]：所以在一个会话里再发一句，上一轮的过程与答案就从界面上消失了
 * （数据其实一直在 localStorage 的 rounds 里，丢的只是展示）。现在按轮次排成一条对话流。
 * 正在流式输出的那一轮用内存里的实时数据（liveMessages / liveResult），其余用已落库的数据。
 */
const displayRounds = computed(() => {
  const rounds = [...(currentSession.value?.rounds ?? [])].reverse()
  return rounds.map((round) => {
    const live = streaming.value && activeRoundId !== '' && activeRoundId === round.id
    const messages = live ? liveMessages.value : round.messages
    return {
      id: round.id,
      question: round.question,
      askedAt: round.askedAt,
      messages,
      content: live ? liveResult.value : round.result,
      loading: live,
      error: live ? liveError.value : (round.error ?? ''),
      // 历史轮次只存了错误文案（没存码），「去配置」按钮只对本轮实时错误生效
      errorCode: live ? liveErrorCode.value : '',
      stepCount: messages.reduce((max, item) => Math.max(max, item.step ?? 0), 0),
      durationMs:
        live || messages.length < 2
          ? live
            ? liveDurationMs.value
            : 0
          : (messages[messages.length - 1]?.timestamp ?? 0) - (messages[0]?.timestamp ?? 0),
    }
  })
})

/** 过程默认展开状态：undefined = 按每条内容自动决定；工具条按钮在展开 / 收起之间切换 */
const expandAll = ref<boolean | undefined>(undefined)

function toggleExpandAll(): void {
  expandAll.value = expandAll.value === true ? false : true
}

/** 缺模型 Key 时按钮的落点：客户端 API 管理（在那里填 base_url + Key） */
function goConfigureOwnKey(): void {
  void router.push('/admin/ai-client-api-management')
}

/**
 * 主区滚动：轮次是自上而下追加的、答案在末尾。
 * 流式期间跟着滚到底（否则新内容出现在视野外）；切换会话时也滚到底（直接看最新一轮）。
 */
const transcriptScroller = ref<HTMLElement | null>(null)

async function scrollTranscriptToBottom(): Promise<void> {
  await nextTick()
  const el = transcriptScroller.value
  if (el) el.scrollTop = el.scrollHeight
}

watch(
  () => [
    displayRounds.value.length,
    displayRounds.value[displayRounds.value.length - 1]?.content.length ?? 0,
    streaming.value,
  ],
  () => {
    if (streaming.value) void scrollTranscriptToBottom()
  },
)

watch(currentId, () => {
  void scrollTranscriptToBottom()
})

/**
 * 下拉只展示智能体名称 —— id 是内部标识，对用户没有意义（用户明确要求不出现）。
 * 名称缺失（历史脏数据）时才退回显示 id，避免出现空白项。
 */
const agentOptions = computed(() => {
  if (agents.value.length) {
    return agents.value.map((item) => ({ value: item.agentId, label: item.agentName || item.agentId }))
  }
  // 列表还没加载回来时的占位项：就是后端默认放行的那个 Auto 智能体
  return [{ value: DEFAULT_AGENT_ID, label: '智能对话体（Auto）' }]
})

const presetOptions = computed(() => PRESETS.map((item) => ({ value: item.label, label: item.label })))

const knowledgeOptions = computed(() =>
  // 只展示知识库名称：knowledgeTag 落库时带 <userId>: 作用域前缀（防同名串库），
  // 对用户无意义，没必要暴露在选择项里
  knowledgeBases.value.map((item) => ({
    value: item.ragId,
    label: item.ragName,
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

  // 落库前先把「按帧合并」里挂着的答案刷出来，否则最后一段会丢
  flushPendingResult()

  updateRound(activeSessionId, activeRoundId, {
    messages: liveMessages.value.map((item) => ({ ...item })),
    result: liveResult.value,
    completed: !liveError.value && !aborted,
    error: liveError.value || undefined,
  })

  handle = null
}

/**
 * 答案写入做一层「按帧合并」。
 *
 * 流式增量帧每秒可能来十几条，每条都直接改 liveResult 会让 Markdown 全量重渲染
 * （marked + highlight.js + DOMPurify）把主线程顶满，反而卡。这里攒到下一帧渲染时落地一次，
 * 视觉上仍是逐字出现。
 */
let pendingResult: string | null = null
let resultFrame = 0

function flushPendingResult(): void {
  if (resultFrame) {
    cancelAnimationFrame(resultFrame)
    resultFrame = 0
  }
  if (pendingResult !== null) {
    liveResult.value = pendingResult
    pendingResult = null
  }
}

function applyResult(text: string): void {
  if (!text) return
  pendingResult = text
  if (resultFrame) return
  resultFrame = requestAnimationFrame(() => {
    resultFrame = 0
    if (pendingResult !== null) {
      liveResult.value = pendingResult
      pendingResult = null
    }
  })
}

function handleMessage(msg: SseMessage): void {
  const content = msg.content ?? ''

  /**
   * 最终答案来源（一律「替换」，不再拼接）：
   *   1) summary_delta —— 流式增量帧，content 是「到目前为止的全文」；重试重发也不会变成重复段落；
   *   2) summary 且无 subType —— 正常结尾的整段帧，或总结超时后的本地兜底报告（同样应覆盖前面的半截内容）。
   * 各**分段**帧（summary_overview / completed_work / suggestions …）只作为过程展示，不并入答案：
   * 旧实现把它们拼起来，实际只会拿到第一段，答案区经常是空的（这就是"结果面板老是暂无结果"的原因）。
   */
  const isSummaryDelta = msg.type === SseMessageType.Summary && msg.subType === SseSubType.SummaryDelta
  const isWholeSummary = msg.type === SseMessageType.Summary && !msg.subType && Boolean(content.trim())
  const isComplete = msg.type === SseMessageType.Complete

  /*
   * 增量帧与完成帧不记进过程列表：
   *   · 增量帧每次带的是「到目前为止的全文」，存下来就是十几条几乎一样的条目（重载后满屏重复的"总结阶段"）；
   *   · 完成帧的正文只是「执行完成」这类状态文案，页脚已经表达了"已完成"。
   * 答案本身单独存在 round.result 里，不依赖这两类帧。
   */
  if (!isSummaryDelta && !isComplete) {
    liveMessages.value.push({
      id: `${activeRoundId}_${msg.step ?? 0}_${msg.timestamp ?? Date.now()}_${liveMessages.value.length}`,
      type: msg.type,
      subType: msg.subType ?? null,
      step: msg.step ?? null,
      content,
      timestamp: msg.timestamp ?? Date.now(),
    })
  }

  if (isSummaryDelta || isWholeSummary) {
    applyResult(content)
  }
  // ⚠️ complete 帧**不**并入答案：后端完成帧的 content 是「执行完成」这类状态文案，
  // 并进去会把刚流式出来的答案整段覆盖掉（实测踩到过，答案区只剩四个字）。

  if (msg.type === SseMessageType.Error && content.trim()) {
    liveError.value = content.trim()
    liveErrorCode.value = msg.code ?? ''
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
  liveErrorCode.value = ''
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

/**
 * 工具条两个选择器：显示值由 v-model 直接绑定本地 ref（见模板），
 * 这里只负责把选择同步到当前会话 —— 会话记住它，切换会话时再回填（见下面 watch）。
 *
 * ⚠️ 之前这里只调 updateSessionMeta、没更新本地 ref，导致"选了没反应"：
 * 值确实写进了会话（发送时用的是 session.maxStep），但 Select 显示的仍是老值，
 * 看起来就是"最大执行步骤改不了"（管理员同样改不了，因为与权限无关）。
 */
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
            v-model:value="selectedAgentId"
            :options="agentOptions"
            :loading="agentsLoading"
            class="min-w-[240px]"
            size="small"
            @change="onAgentChange"
          />
        </div>

        <div class="flex items-center gap-2">
          <span class="text-[12px] text-ink-400">最大执行步数</span>
          <Select
            v-model:value="maxStep"
            :options="MAX_STEP_OPTIONS.map((v) => ({ value: v, label: String(v) }))"
            size="small"
            class="w-[76px]"
            @change="onMaxStepChange"
          />
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
            v-if="displayRounds.length"
            type="button"
            class="btn-ghost flex h-8 cursor-pointer items-center gap-1.5 px-2.5 text-[12.5px]"
            @click="toggleExpandAll"
          >
            {{ expandAll === true ? '收起过程' : '展开过程' }}
          </button>
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

      <!--
        输出区：**整个会话的全部轮次**自上而下渲染（旧 → 新），滚动统一由这里负责。
        每个轮次块内含「提问 + 可折叠的思考过程 + 最终答案」，见 ChatRoundBlock。
      -->
      <div ref="transcriptScroller" class="scroll-thin min-h-0 flex-1 overflow-y-auto">
        <div
          v-if="!displayRounds.length"
          class="flex h-full flex-col items-center justify-center px-6 text-center"
        >
          <p class="text-[13px] text-ink-600">暂无输出</p>
          <p class="mt-1.5 max-w-[340px] text-[12px] leading-6 text-ink-400">
            发送问题后，这里会按「分析 → 执行 → 监督 → 总结」逐步展示智能体的思考过程，最后给出渲染好的答案。
          </p>
        </div>

        <ChatRoundBlock
          v-for="round in displayRounds"
          :key="round.id"
          :question="round.question"
          :asked-at="round.askedAt"
          :messages="round.messages"
          :content="round.content"
          :loading="round.loading"
          :error="round.error"
          :error-code="round.errorCode"
          :step-count="round.stepCount"
          :duration-ms="round.durationMs"
          :expand-all="expandAll"
          @goto-config="goConfigureOwnKey"
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
