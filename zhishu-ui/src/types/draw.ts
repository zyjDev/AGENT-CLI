/**
 * 画布配置持久化契约。
 *
 * ⚠️ 这是与后端 DrawConfigParser 的唯一契约，字段名不可自行「规范化」：
 * - 顶层：nodes / edges（小写）
 * - 连线：sourceNodeID / targetNodeID（**大写 ID**，不是 LogicFlow 原生的 sourceNodeId）
 * - 节点：id / type / data.title / data.inputsValues
 *
 * 引用 ID 的取值方式（DrawConfigParser 逐个分支读取）：
 * - client   → inputsValues.clientId（字符串优先）
 * - client   → inputsValues.clientName[0].value（clientId 缺失时回退）
 * - agent    → inputsValues.agentName（数组 [0].value 或字符串）
 * - tool_mcp → inputsValues.toolMcpName[0].value
 * - model    → inputsValues.modelName[0].value
 * - prompt   → inputsValues.promptName[0].value（或字符串）
 * - advisor  → inputsValues.advisorName[0].value
 *
 * agent 节点的 inputsValues 还会被写入 ai_agent 表：
 * agentName / description / channel / strategy
 */

/** inputsValues 中「引用选择器」的取值结构 */
export interface DrawInputRef {
  key: string
  value: string | number
}

export interface DrawNodeData {
  title: string
  inputsValues: Record<string, unknown>
  [key: string]: unknown
}

export interface DrawNode {
  id: string
  type: DrawNodeType
  data: DrawNodeData
  meta?: Record<string, unknown>
}

export interface DrawEdge {
  sourceNodeID: string
  targetNodeID: string
  sourcePortID?: string
}

export interface DrawConfigJson {
  nodes: DrawNode[]
  edges: DrawEdge[]
}

/** 后端 DrawConfigParser 能识别的节点类型（其余类型会被忽略） */
export type DrawNodeType = 'start' | 'end' | 'agent' | 'advisor' | 'client' | 'model' | 'prompt' | 'tool_mcp'
