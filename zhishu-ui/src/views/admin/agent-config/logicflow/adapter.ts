/**
 * 画布数据 ⇄ 后端契约 的双向适配器（本次迁移最关键的兼容层）。
 *
 * 差异点（读后端源码确认，勿「顺手规范化」）：
 * 1. 连线字段名：后端是 sourceNodeID / targetNodeID（**大写 ID**），LogicFlow 原生是 sourceNodeId / targetNodeId；
 * 2. 坐标位置：后端放在 node.meta.position，LogicFlow 放在节点自身的 x / y；
 * 3. 业务类型：后端用 node.type，LogicFlow 统一用 zhishu-node，真实类型藏在 properties.nodeType；
 * 4. data 下还有后端要用的其它字段（inputs / outputs 的 JsonSchema），必须原样保留。
 *
 * 因此策略是「业务 JSON 与画布数据分离」：
 * - 画布侧只拿基本类型（nodeType / title / hint），避免 LogicFlow 深度观测业务对象；
 * - 原始节点/边由调用方用 Map 保管，保存时以 raw 为基底做增量合并，未识别的字段不会丢。
 */
import type { DrawConfigJson, DrawEdge, DrawNode, DrawNodeType } from '@/types/draw'
import { AGENT_NAME_PLACEHOLDER, catalogOf, defaultInputsValues, REF_KEY_BY_TYPE } from './catalog'
import { NODE_TYPE_NAME } from './node'

export interface CanvasNode {
  id: string
  type?: string
  x: number
  y: number
  properties?: Record<string, unknown>
}

export interface CanvasEdge {
  id?: string
  /** LogicFlow 需要显式指定连线类型，缺省会报「找不到 undefined 对应的边」 */
  type?: string
  sourceNodeId: string
  targetNodeId: string
  sourceAnchorId?: string
  targetAnchorId?: string
  properties?: Record<string, unknown>
}

export interface CanvasGraphData {
  nodes: CanvasNode[]
  edges: CanvasEdge[]
}

/** 画布属性（只放基本类型） */
export interface ZhishuNodeProperties {
  nodeType: DrawNodeType
  title: string
  hint: string
}

export interface ZhishuEdgeProperties {
  /** 后端 sourcePortID（有则原样带回） */
  sourcePortID?: string
}

/** 适配结果：画布数据 + 业务 JSON 的保管表 */
export interface AdaptedGraph {
  graph: CanvasGraphData
  /** 节点 id → 后端原始节点 JSON */
  rawNodes: Map<string, DrawNode>
  /** 画布边 id → 后端原始边 JSON */
  rawEdges: Map<string, DrawEdge>
}

/** 后端 JSON → 画布数据（同时抽出 raw 保管表） */
export function fromDrawConfigJson(config: DrawConfigJson): AdaptedGraph {
  const rawNodes = new Map<string, DrawNode>()
  const rawEdges = new Map<string, DrawEdge>()

  const nodes: CanvasNode[] = (config.nodes ?? []).map((node) => {
    const catalog = catalogOf(node.type)
    const position = (node.meta?.position as { x?: number; y?: number } | undefined) ?? {}
    rawNodes.set(node.id, node)

    const properties: ZhishuNodeProperties = {
      nodeType: node.type,
      title: node.data?.title || catalog.label,
      hint: referenceHintOf(node.type, node.data?.inputsValues),
    }
    return {
      id: node.id,
      type: NODE_TYPE_NAME,
      x: Number(position.x ?? 0),
      y: Number(position.y ?? 0),
      properties: properties as unknown as Record<string, unknown>,
    }
  })

  const edges: CanvasEdge[] = (config.edges ?? []).map((edge, index) => {
    const id = `edge_${index}_${edge.sourceNodeID}_${edge.targetNodeID}`
    rawEdges.set(id, edge)
    const properties: ZhishuEdgeProperties = { sourcePortID: edge.sourcePortID }
    return {
      id,
      type: 'polyline',
      sourceNodeId: edge.sourceNodeID,
      targetNodeId: edge.targetNodeID,
      // 锚点固定「右出 / 左入」，与 ZhishuNodeModel.getDefaultAnchor 的 id 命名保持一致
      sourceAnchorId: `${edge.sourceNodeID}_out`,
      targetAnchorId: `${edge.targetNodeID}_in`,
      properties: properties as unknown as Record<string, unknown>,
    }
  })

  return { graph: { nodes, edges }, rawNodes, rawEdges }
}

/** 画布数据 → 后端 JSON（保存前调用） */
export function toDrawConfigJson(graph: CanvasGraphData, rawNodes: Map<string, DrawNode>, rawEdges: Map<string, DrawEdge>): DrawConfigJson {
  const nodeIds = new Set(graph.nodes.map((node) => node.id))

  const nodes: DrawNode[] = graph.nodes.map((node) => {
    const properties = (node.properties ?? {}) as unknown as ZhishuNodeProperties
    const existing = rawNodes.get(node.id)
    const base: DrawNode = existing
      ? (JSON.parse(JSON.stringify(existing)) as DrawNode)
      : {
          id: node.id,
          type: properties.nodeType,
          data: { title: properties.title, inputsValues: defaultInputsValues(properties.nodeType) },
        }

    base.id = node.id
    base.type = properties.nodeType
    base.data = base.data ?? { title: properties.title, inputsValues: {} }
    base.data.title = properties.title
    if (!base.data.inputsValues) {
      base.data.inputsValues = defaultInputsValues(properties.nodeType)
    }
    // 坐标写回 meta.position：装配不依赖它，但保留后回读更稳定
    base.meta = { ...(base.meta ?? {}), position: { x: Math.round(node.x), y: Math.round(node.y) } }

    return base
  })

  const edges: DrawEdge[] = graph.edges
    .map((edge) => {
      const properties = (edge.properties ?? {}) as unknown as ZhishuEdgeProperties
      const existing = edge.id ? rawEdges.get(edge.id) : undefined
      const base: DrawEdge = existing
        ? (JSON.parse(JSON.stringify(existing)) as DrawEdge)
        : { sourceNodeID: edge.sourceNodeId, targetNodeID: edge.targetNodeId }

      base.sourceNodeID = edge.sourceNodeId
      base.targetNodeID = edge.targetNodeId
      if (properties.sourcePortID) {
        base.sourcePortID = properties.sourcePortID
      } else {
        delete (base as Partial<DrawEdge>).sourcePortID
      }

      // 端点已被删除的边直接丢弃，避免后端解析出空关系
      if (!nodeIds.has(base.sourceNodeID) || !nodeIds.has(base.targetNodeID)) {
        return null
      }
      return base
    })
    .filter((edge): edge is DrawEdge => edge !== null)

  return { nodes, edges }
}

/**
 * 节点卡片副标题：优先展示引用到的资源名（原型的副标题就是这个），
 * 还没选引用时退回该类型的说明文案。仅用于展示，不参与持久化。
 */
export function referenceHintOf(type: DrawNodeType, inputsValues: Record<string, unknown> | undefined): string {
  const key = REF_KEY_BY_TYPE[type]
  if (key) {
    const value = readRefValue(inputsValues, key)
    // 占位名（未命名智能体）不算「已选引用」，否则新节点副标题会挂着一个占位词
    if (value && value !== AGENT_NAME_PLACEHOLDER) return value
  }
  return catalogOf(type).hint
}

/** 从一个业务类型的 inputsValues 中读取「引用值」 */
export function readRefValue(inputsValues: Record<string, unknown> | undefined, key: string): string {
  const raw = inputsValues?.[key]
  if (typeof raw === 'string') return raw
  if (Array.isArray(raw) && raw.length) {
    const first = raw[0] as { value?: unknown }
    return normalizeInputValue(first?.value)
  }
  return ''
}

/** 兼容三种 value 形态：字符串 / 数字 / { content: 'x' } 对象 */
export function normalizeInputValue(value: unknown): string {
  if (value === null || value === undefined) return ''
  if (typeof value === 'object') {
    const content = (value as { content?: unknown }).content
    return content === undefined || content === null ? '' : String(content)
  }
  return String(value)
}

/** 写回「引用值」：保留原有的 [{key, value}] 结构，只替换 value */
export function writeRefValue(inputsValues: Record<string, unknown>, key: string, next: string): Record<string, unknown> {
  const current = inputsValues[key]
  if (Array.isArray(current) && current.length) {
    const first = current[0] as { key?: string; value?: unknown }
    const isObjectValue = typeof first.value === 'object' && first.value !== null
    const updated = [...current]
    updated[0] = {
      ...first,
      value: isObjectValue ? { ...(first.value as object), content: next } : next,
    }
    return { ...inputsValues, [key]: updated }
  }
  if (typeof current === 'string') {
    return { ...inputsValues, [key]: next }
  }
  // 原值不存在：按主流形态（对象 value）新建
  return { ...inputsValues, [key]: [{ key: '', value: { content: next } }] }
}

/** 新建节点的 properties */
export function createNodeProperties(type: DrawNodeType, title?: string): ZhishuNodeProperties {
  const catalog = catalogOf(type)
  return { nodeType: type, title: title ?? catalog.label, hint: catalog.hint }
}

export { NODE_TYPE_NAME }
