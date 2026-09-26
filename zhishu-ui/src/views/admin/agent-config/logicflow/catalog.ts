/**
 * 画布节点目录。
 *
 * 节点类型集合严格对齐后端 DrawConfigParser 的 switch 分支
 * （client / agent / tool_mcp / model / prompt / advisor，另有 start 作为流程入口），
 * 不引入后端不认识的类型（condition / loop / llm 等旧目录里存在但未注册的节点），
 * 否则保存后这些节点会在装配时被静默忽略、关系表为空。
 */
import type { DrawNodeType } from '@/types/draw'

export interface NodeCatalogItem {
  type: DrawNodeType
  label: string
  /** 节点卡片副标题（默认值；有引用时会被引用名覆盖） */
  hint: string
  color: string
  /** 是否允许从面板新增 */
  canAdd: boolean
  /** 左侧面板分组标题（'' 表示不出现在面板，如 start / end） */
  group: string
}

/** 面板分组顺序（对齐原型的分组标题；只列后端认识的类型，故没有「基础」「流程控制」两组） */
export const NODE_GROUP_ORDER = ['智能', '资源引用']

export const NODE_CATALOG: NodeCatalogItem[] = [
  { type: 'start', label: '开始', hint: '流程入口', color: '#16A34A', canAdd: false, group: '' },
  { type: 'agent', label: '智能体', hint: '调度与编排主体', color: '#4F46E5', canAdd: true, group: '智能' },
  // 资源引用组内的顺序与原型一致：客户端 → 顾问 → 模型 → 提示词 → MCP 工具
  { type: 'client', label: '客户端', hint: '对话客户端引用', color: '#0EA5E9', canAdd: true, group: '资源引用' },
  { type: 'advisor', label: '顾问', hint: '拦截器引用', color: '#F59E0B', canAdd: true, group: '资源引用' },
  { type: 'model', label: '模型', hint: '大模型引用', color: '#6366F1', canAdd: true, group: '资源引用' },
  { type: 'prompt', label: '提示词', hint: '系统提示词引用', color: '#14B8A6', canAdd: true, group: '资源引用' },
  { type: 'tool_mcp', label: 'MCP 工具', hint: '外部工具服务', color: '#DB2777', canAdd: true, group: '资源引用' },
  { type: 'end', label: '结束', hint: '流程出口', color: '#E5484D', canAdd: false, group: '' },
]

/** 智能体名称的占位默认值：仅用于新建节点的初始值，不会被当成「配置名」回填 */
export const AGENT_NAME_PLACEHOLDER = '未命名智能体'

/** 各类型节点在 inputsValues 里的「引用值」键名（与后端 DrawConfigParser 的读取键一致） */
export const REF_KEY_BY_TYPE: Partial<Record<DrawNodeType, string>> = {
  agent: 'agentName',
  client: 'clientName',
  model: 'modelName',
  prompt: 'promptName',
  advisor: 'advisorName',
  tool_mcp: 'toolMcpName',
}

export const NODE_CATALOG_MAP: Record<string, NodeCatalogItem> = NODE_CATALOG.reduce(
  (acc, item) => {
    acc[item.type] = item
    return acc
  },
  {} as Record<string, NodeCatalogItem>,
)

export function catalogOf(type: string): NodeCatalogItem {
  return (
    NODE_CATALOG_MAP[type] ?? { type: 'agent', label: type, hint: '自定义节点', color: '#8C94A3', canAdd: false, group: '' }
  )
}

/** 节点 id 前缀（与旧实现的命名习惯保持一致） */
export function nodeIdPrefix(type: DrawNodeType): string {
  return type === 'start' ? 'start' : type === 'end' ? 'end' : type
}

export function generateNodeId(type: DrawNodeType): string {
  return `${nodeIdPrefix(type)}_${Math.random().toString(36).slice(2, 7)}`
}

/** 各类型节点的 inputsValues 初始结构（键名与后端解析契约一致） */
export function defaultInputsValues(type: DrawNodeType): Record<string, unknown> {
  switch (type) {
    case 'agent':
      return {
        agentName: [{ key: '', value: { content: AGENT_NAME_PLACEHOLDER } }],
        description: [{ key: '', value: { content: '' } }],
        channel: 'agent',
        strategy: 'flowAgentExecuteStrategy',
      }
    case 'client':
      return {
        clientType: [{ key: 'client_type_1', value: 'DEFAULT' }],
        clientId: '',
        clientName: '',
        sequence: [{ key: 'seq_1', value: 1 }],
        stepPrompt: [{ key: 'sp_1', value: '' }],
      }
    case 'model':
      return { modelName: [{ key: 'model_select_1', value: '' }] }
    case 'advisor':
      return { advisorName: [{ key: 'advisor_select_1', value: '' }] }
    case 'prompt':
      return { promptName: [{ key: 'prompt_select_1', value: '' }] }
    case 'tool_mcp':
      return { toolMcpName: [{ key: 'tool_mcp_select_1', value: '' }] }
    default:
      return {}
  }
}

/** 客户端类型枚举（与旧实现 nodes/client/client-types.ts 对齐） */
export const CLIENT_TYPE_OPTIONS = [
  { label: 'DEFAULT（默认）', value: 'DEFAULT' },
  { label: 'TASK_ANALYZER_CLIENT（任务分析）', value: 'TASK_ANALYZER_CLIENT' },
  { label: 'PRECISION_EXECUTOR_CLIENT（精准执行）', value: 'PRECISION_EXECUTOR_CLIENT' },
  { label: 'QUALITY_SUPERVISOR_CLIENT（质量监督）', value: 'QUALITY_SUPERVISOR_CLIENT' },
  { label: 'RESPONSE_ASSISTANT（应答助手）', value: 'RESPONSE_ASSISTANT' },
  { label: 'TOOL_MCP_CLIENT（工具调用）', value: 'TOOL_MCP_CLIENT' },
  { label: 'PLANNING_CLIENT（规划）', value: 'PLANNING_CLIENT' },
  { label: 'EXECUTOR_CLIENT（执行）', value: 'EXECUTOR_CLIENT' },
]

/** 执行策略枚举（与旧实现 agent 节点默认值一致） */
export const STRATEGY_OPTIONS = [
  { label: 'flowAgentExecuteStrategy', value: 'flowAgentExecuteStrategy' },
  { label: 'autoAgentExecuteStrategy', value: 'autoAgentExecuteStrategy' },
  { label: 'fixedAgentExecuteStrategy', value: 'fixedAgentExecuteStrategy' },
]
