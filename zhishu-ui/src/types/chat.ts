/** 用户端对话相关类型 */

/** 会话中的一条过程消息（来自 SSE） */
export interface ChatMessage {
  id: string
  type: string
  subType: string | null
  step: number | null
  content: string
  timestamp: number
}

/** 一轮对话：过程消息 + 最终结果 */
export interface ChatRound {
  id: string
  /** 用户提问 */
  question: string
  /** 提问时间 */
  askedAt: number
  /** 过程消息（分析 / 执行 / 监督 / 总结） */
  messages: ChatMessage[]
  /** 最终结果（Markdown 原文） */
  result: string
  /** 是否已完成 */
  completed: boolean
  /** 失败原因（若有） */
  error?: string
}

/** 一个会话 */
export interface ChatSession {
  id: string
  title: string
  createdAt: number
  updatedAt: number
  agentId: string
  maxStep: number
  rounds: ChatRound[]
}

/** 预设案例：一键填入输入框 */
export interface ChatPreset {
  label: string
  prompt: string
}
