/**
 * Auto Agent 流式对话。
 *
 * ⚠️ 必须用原生 fetch：axios 会消费掉响应流。
 *
 * 与旧静态演示页（已随「用户端 + 管理端合并」删除）的差异：
 * 旧实现直接 `chunk.split('\n')`，跨 chunk 的半包会丢数据；这里维护字符串缓冲，
 * 以「行」为事件边界，最后一段可能不完整的行留到下一轮再拼。
 */
import { AUTO_AGENT_URL } from '@/api/agent'
import { getToken } from '@/utils/auth'
import type { SseMessage } from '@/enums/sse'

export interface AutoAgentPayload {
  aiAgentId: string
  message: string
  sessionId: string
  maxStep: number
  knowledgeTag?: string
}

export interface SseCallbacks {
  onMessage: (msg: SseMessage) => void
  onComplete: (info: { aborted: boolean }) => void
  onError: (err: Error) => void
}

export interface SseHandle {
  abort: () => void
}

const DATA_PREFIX = 'data:'
/** SSE 协议中需要忽略的字段前缀（本项目后端未使用，但按协议容错） */
const IGNORED_PREFIXES = ['event:', 'id:', 'retry:', ':']

/**
 * 解析一行 SSE 文本。
 * 返回 undefined 表示该行无需处理（空行 / 注释 / 协议字段）。
 */
function parseLine(line: string): SseMessage | string | undefined {
  const text = line.trim()
  if (!text) return undefined

  if (IGNORED_PREFIXES.some((prefix) => text.startsWith(prefix))) return undefined

  if (!text.startsWith(DATA_PREFIX)) {
    // 非 data: 开头的裸文本：后端 auto_agent 的顶层 catch 就是这么发的
    // （AiAgentController.java 的 errorEmitter.send("请求处理异常：" + msg)），
    // 直接丢弃会让错误静默消失，因此当作错误信息返回。
    return text
  }

  const payloadText = text.slice(DATA_PREFIX.length).trim()
  if (!payloadText || payloadText === '[DONE]') return undefined

  try {
    return JSON.parse(payloadText) as SseMessage
  } catch (error) {
    console.error('[sse] JSON 解析失败，已跳过该行', payloadText, error)
    return undefined
  }
}

/**
 * 发起一次 Auto Agent 流式请求。
 * @returns abort 用于手动中断
 */
export function streamAutoAgent(payload: AutoAgentPayload, callbacks: SseCallbacks): SseHandle {
  const controller = new AbortController()
  let abortedByUser = false

  const token = getToken()
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    Accept: 'text/event-stream',
  }
  // /v1/agent/** 后端默认不鉴权，但带上 token 无害，且为将来开启保护留好路径
  if (token) headers.Authorization = `Bearer ${token}`

  const run = async (): Promise<void> => {
    try {
      const response = await fetch(AUTO_AGENT_URL, {
        method: 'POST',
        headers,
        body: JSON.stringify(payload),
        signal: controller.signal,
      })

      // 401 已由全局 fetch 拦截器处理（清登录态 + 跳登录页），这里只保证不误报成业务错误
      if (!response.ok) {
        throw new Error(
          response.status === 401 ? '登录已过期，请重新登录' : `请求失败（HTTP ${response.status}）`,
        )
      }
      if (!response.body) {
        throw new Error('当前浏览器不支持流式响应')
      }

      const reader = response.body.getReader()
      const decoder = new TextDecoder()
      let buffer = ''

      const consumeLine = (line: string): void => {
        const parsed = parseLine(line)
        if (parsed === undefined) return
        if (typeof parsed === 'string') {
          callbacks.onError(new Error(parsed))
          return
        }
        callbacks.onMessage(parsed)
      }

      for (;;) {
        const { done, value } = await reader.read()
        if (done) break

        buffer += decoder.decode(value, { stream: true })
        const lines = buffer.split('\n')
        // 最后一段可能是半包，留到下一轮
        buffer = lines.pop() ?? ''
        for (const line of lines) consumeLine(line)
      }

      // 收尾：流结束时缓冲里可能还剩最后一行（通常没有换行符结尾）
      if (buffer.trim()) consumeLine(buffer)

      callbacks.onComplete({ aborted: false })
    } catch (error) {
      if (abortedByUser) {
        callbacks.onComplete({ aborted: true })
        return
      }
      console.error('[sse] 流式请求异常', error)
      callbacks.onError(error instanceof Error ? error : new Error('流式请求失败'))
    }
  }

  void run()

  return {
    abort: () => {
      abortedByUser = true
      controller.abort()
    },
  }
}
