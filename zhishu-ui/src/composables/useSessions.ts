/**
 * 会话与消息的本地持久化。
 *
 * 与旧静态页保持一致：不引入后端会话存储，全部落在 localStorage。
 * 只持久化「已完成 / 已产生内容」的轮次，避免把流式中间态写坏。
 */
import { computed, ref, watch } from 'vue'
import type { ChatRound, ChatSession } from '@/types/chat'

const STORAGE_KEY = 'zhishu:chat:sessions'
/** 最多保留的会话数，防止 localStorage 无限膨胀 */
const MAX_SESSIONS = 30

function createSessionId(): string {
  return `session_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`
}

function createBlankSession(agentId: string, maxStep: number): ChatSession {
  const now = Date.now()
  return {
    id: createSessionId(),
    title: '新对话',
    createdAt: now,
    updatedAt: now,
    agentId,
    maxStep,
    rounds: [],
  }
}

function loadFromStorage(): ChatSession[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return []
    const parsed = JSON.parse(raw) as ChatSession[]
    return Array.isArray(parsed) ? parsed : []
  } catch (error) {
    console.error('[sessions] 本地会话数据损坏，已忽略', error)
    return []
  }
}

/** 模块级单例：多个组件共享同一份会话状态 */
const sessions = ref<ChatSession[]>(loadFromStorage())
const currentId = ref<string>('')

watch(
  sessions,
  (value) => {
    try {
      const trimmed = value.slice(0, MAX_SESSIONS)
      localStorage.setItem(STORAGE_KEY, JSON.stringify(trimmed))
    } catch (error) {
      // 配额写满时不影响当前会话继续使用，只是不再持久化
      console.error('[sessions] 会话持久化失败', error)
    }
  },
  { deep: true },
)

export function useSessions() {
  const currentSession = computed<ChatSession | undefined>(() =>
    sessions.value.find((item) => item.id === currentId.value),
  )

  /** 确保至少有一个会话，并保证 currentId 有效 */
  function ensureSession(agentId: string, maxStep: number): ChatSession {
    const existing = currentSession.value
    if (existing) return existing

    const first = sessions.value[0]
    if (first) {
      currentId.value = first.id
      return first
    }

    const created = createBlankSession(agentId, maxStep)
    sessions.value.unshift(created)
    currentId.value = created.id
    return created
  }

  function createSession(agentId: string, maxStep: number): ChatSession {
    const created = createBlankSession(agentId, maxStep)
    sessions.value.unshift(created)
    currentId.value = created.id
    return created
  }

  function selectSession(id: string): void {
    currentId.value = id
  }

  function removeSession(id: string): void {
    sessions.value = sessions.value.filter((item) => item.id !== id)
    if (currentId.value === id) {
      currentId.value = sessions.value[0]?.id ?? ''
    }
  }

  function clearAll(): void {
    sessions.value = []
    currentId.value = ''
  }

  /** 写入一轮对话（新的一轮插到最前面） */
  function appendRound(sessionId: string, round: ChatRound): void {
    const target = sessions.value.find((item) => item.id === sessionId)
    if (!target) return
    target.rounds.unshift(round)
    target.updatedAt = Date.now()
    if (target.title === '新对话' && round.question) {
      target.title = round.question.slice(0, 28)
    }
  }

  /** 更新指定轮次（流式过程中增量刷新） */
  function updateRound(sessionId: string, roundId: string, patch: Partial<ChatRound>): void {
    const target = sessions.value.find((item) => item.id === sessionId)
    if (!target) return
    const round = target.rounds.find((item) => item.id === roundId)
    if (!round) return
    Object.assign(round, patch)
    target.updatedAt = Date.now()
  }

  function updateSessionMeta(sessionId: string, patch: Partial<Pick<ChatSession, 'agentId' | 'maxStep'>>): void {
    const target = sessions.value.find((item) => item.id === sessionId)
    if (!target) return
    Object.assign(target, patch)
  }

  return {
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
  }
}

export { createSessionId }
