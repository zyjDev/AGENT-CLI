<script setup lang="ts">
/**
 * 智能体编排（LogicFlow 画布）。
 *
 * 职责：
 * 1. 画布交互：新增 / 连线 / 选中 / 删除 / 缩放 / 撤销重做；
 * 2. 与后端契约互转（logicflow/adapter.ts），保存时产出 DrawConfigParser 能解析的 JSON；
 * 3. 保存后可从「智能体列表」或本页直接装配（POST /v1/agent/armory_agent）。
 *
 * 只读模式由 query.mode === 'view' 控制（旧实现的该参数此前是死代码，这里补全）。
 *
 * 数据分层：画布节点只持有基本类型属性，业务 JSON（inputsValues / inputs / outputs）
 * 由 rawNodes、rawEdges 两张表保管，保存时以 raw 为基底合并 —— 既避免 LogicFlow 深度观测，
 * 也保证未在 UI 暴露的字段不会丢。
 */
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import dayjs from 'dayjs'
import LogicFlow from '@logicflow/core'
import '@logicflow/core/dist/style/index.css'
import { Button, Input, message, Spin, Tooltip } from 'ant-design-vue'
import { ArrowLeft, Maximize2, Minus, Plus, Redo2, Save, Undo2, Zap } from 'lucide-vue-next'
import { AgentApi } from '@/api/agent'
import { AiAgentDrawApi } from '@/api/ai-agent-draw'
import { useUserStore } from '@/store/modules/user'
import type { DrawConfigJson, DrawEdge, DrawNode, DrawNodeType } from '@/types/draw'
import NodeFormDrawer from './components/NodeFormDrawer.vue'
import {
  fromDrawConfigJson,
  referenceHintOf,
  toDrawConfigJson,
  type CanvasGraphData,
  type ZhishuNodeProperties,
} from './logicflow/adapter'
import {
  AGENT_NAME_PLACEHOLDER,
  defaultInputsValues,
  generateNodeId,
  NODE_CATALOG,
  NODE_GROUP_ORDER,
} from './logicflow/catalog'
import { nodeRegistration, NODE_TYPE_NAME } from './logicflow/node'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const canvasRef = ref<HTMLDivElement>()
const lf = ref<LogicFlow | null>(null)

const loading = ref(false)
const saving = ref(false)
const arming = ref(false)
const readonly = ref(false)
const drawerOpen = ref(false)
const zoomPercent = ref(100)
const activeNodeId = ref('')

const DEFAULT_CONFIG_NAME = '新建编排配置'

/** 面板拖拽到画布时使用的自定义 MIME，避免与外部拖入的文本/文件混淆 */
const PALETTE_DND_MIME = 'application/x-zhishu-node-type'

const meta = reactive({
  configId: '',
  configName: DEFAULT_CONFIG_NAME,
  description: '',
  agentId: '',
  /** 配置版本号（ai_agent_draw_config.version） */
  version: 0,
  /** 状态 0:禁用 1:启用 */
  status: 1,
  /** 最后保存时间（update_time） */
  updateTime: '',
})

/** 画布左下状态条：节点 / 连线数 */
const nodeCount = ref(0)
const edgeCount = ref(0)

/** 左侧面板分组：只在后端认识的类型里分组（原型里那些后端不认的节点不列） */
const paletteGroups = computed(() =>
  NODE_GROUP_ORDER.map((label) => ({
    label,
    items: NODE_CATALOG.filter((item) => item.canAdd && item.group === label),
  })).filter((group) => group.items.length > 0),
)

/** 是否已有落库的配置：决定顶栏是否展示「已启用 / 版本 / 最后保存」 */
const hasPersistedConfig = computed(() => Boolean(meta.configId))

/** 最后保存时间：后端返回的是 datetime 字符串，按原型的 MM-DD HH:mm 展示 */
const lastSavedText = computed(() => {
  if (!meta.updateTime) return ''
  const parsed = dayjs(meta.updateTime)
  return parsed.isValid() ? parsed.format('MM-DD HH:mm') : meta.updateTime
})

/** 业务 JSON 保管表（不进入 LogicFlow 的 properties） */
let rawNodes = new Map<string, DrawNode>()
let rawEdges = new Map<string, DrawEdge>()
/** 上一次自动回填的配置名：用于判断用户是否手动改过 */
let lastDerivedName = ''

const selectedNode = computed(() => {
  const instance = lf.value
  const id = activeNodeId.value
  if (!instance || !id) return null
  const model = instance.getNodeModelById(id)
  if (!model) return null
  const properties = model.getProperties() as unknown as ZhishuNodeProperties
  return {
    id,
    nodeType: properties.nodeType,
    title: properties.title,
    raw: rawNodes.get(id),
  }
})

const canEdit = computed(() => !readonly.value)

/* --------------------------- 初始化与数据 --------------------------- */

function initCanvas(): void {
  if (!canvasRef.value) return

  const instance = new LogicFlow({
    container: canvasRef.value,
    grid: { size: 20, visible: true, type: 'dot' },
    background: { backgroundColor: '#fbfcff' },
    textEdit: false,
    nodeTextEdit: false,
    edgeTextEdit: false,
    adjustEdge: true,
    // ⚠️ LogicFlow 核心不带内置快捷键（源码 initShortcuts 只注册传入的 shortcuts），
    // 而且它的快捷键是绑在**画布容器**上的 —— 属性抽屉打开后 antd 会把焦点锁在抽屉里，
    // 容器收不到 keydown，实测 Ctrl+Z 完全不响应。因此这里关掉它，改在 document 层自己实现
    // （见 onKeydown），只在输入框里让路。
    keyboard: { enabled: false },
  })

  instance.register(nodeRegistration)
  lf.value = instance

  instance.on('node:click', ({ data }) => {
    activeNodeId.value = data.id
    drawerOpen.value = true
  })
  instance.on('blank:click', () => {
    activeNodeId.value = ''
    drawerOpen.value = false
  })
  instance.on('graph:transform', ({ transform }) => {
    zoomPercent.value = Math.round((transform?.SCALE_X ?? 1) * 100)
  })
  // 状态条计数：节点/连线增删都要跟着动
  instance.on('node:add', ({ data }) => {
    registerRawNodeIfMissing(data)
    refreshCount()
  })
  // 从面板拖进来的节点只会发 node:dnd-add，不发 node:add
  instance.on('node:dnd-add', ({ data }) => {
    registerRawNodeIfMissing(data)
    refreshCount()
  })
  instance.on('node:delete', () => refreshCount())
  instance.on('edge:add', () => refreshCount())
  instance.on('edge:delete', () => refreshCount())
  // 撤销 / 重做走的是历史记录，不会补发 node:add|node:delete，必须单独跟一次
  instance.on('history:change', () => refreshCount())

  instance.render({ nodes: [], edges: [] } as never)
  refreshCount()
}

/** 画布上的节点数 / 连线数（左下状态条用） */
function refreshCount(): void {
  const instance = lf.value
  if (!instance) return
  const graph = instance.getGraphData() as unknown as CanvasGraphData
  nodeCount.value = graph.nodes.length
  edgeCount.value = graph.edges.length
}

/**
 * 拖拽落点新建的节点：LogicFlow 自行生成 id 并创建节点，这里补登业务 JSON 保管表，
 * 否则保存时该节点会退化成默认 inputsValues。
 * 程序内新增（addNode）与加载配置都会先登记 raw，所以这段兜底只在拖拽场景生效。
 */
function registerRawNodeIfMissing(data: { id?: string; properties?: Record<string, unknown> }): void {
  const id = data?.id
  if (!id || rawNodes.has(id)) return
  const properties = (data.properties ?? {}) as unknown as ZhishuNodeProperties
  const nodeType: DrawNodeType = properties.nodeType ?? 'agent'
  const title = String(properties.title ?? nodeType)

  // Ctrl+C / Ctrl+V 粘贴出来的节点也是走 addNode，拿不到原节点的业务 JSON。
  // 若画布上「同类型 + 同标题」的 raw 节点唯一，就复用它，避免粘贴后引用值被清空；
  // 不唯一时宁可退回默认值，也不要写错引用。
  const candidates = [...rawNodes.values()].filter((node) => node.type === nodeType && node.data?.title === title)
  const inputsValues = candidates.length === 1 ? candidates[0].data.inputsValues : defaultInputsValues(nodeType)

  rawNodes.set(id, { id, type: nodeType, data: { title, inputsValues } })
  refreshCount()
}

/** 新配置的初始画布：开始 → 智能体（与旧实现的初始数据一致） */
function defaultGraph(): DrawConfigJson {
  const agentId = generateNodeId('agent')
  return {
    nodes: [
      { id: 'start_0', type: 'start', meta: { position: { x: 120, y: 280 } }, data: { title: 'Start', inputsValues: {} } },
      {
        id: agentId,
        type: 'agent',
        meta: { position: { x: 480, y: 280 } },
        data: { title: 'Agent_1', inputsValues: defaultInputsValues('agent') },
      },
    ],
    edges: [{ sourceNodeID: 'start_0', targetNodeID: agentId }],
  }
}

function renderGraph(config: DrawConfigJson): void {
  const instance = lf.value
  if (!instance) return

  // 端点不存在的边会让 LogicFlow 渲染报错，先过滤掉
  const safe: DrawConfigJson = {
    nodes: config.nodes ?? [],
    edges: (config.edges ?? []).filter((edge) => edge.sourceNodeID && edge.targetNodeID),
  }

  const adapted = fromDrawConfigJson(safe)
  rawNodes = adapted.rawNodes
  rawEdges = adapted.rawEdges
  instance.render(adapted.graph as never)
  // 配置里的坐标可能带负值（旧画布原点不在左上），渲染后必须自适应，否则图形会跑到可视区外
  instance.fitView(60, 60)
  zoomPercent.value = Math.round((instance.getTransform().SCALE_X ?? 1) * 100)
  activeNodeId.value = ''
  drawerOpen.value = false
  refreshCount()
  syncMetaFromGraph()
}

/**
 * 从画布反推配置名 / 描述（与旧实现 extractAgentInfoFromJson 的行为一致）。
 * 仅在用户「没手动改过名称」时才自动回填，避免把用户输入覆盖掉。
 */
function syncMetaFromGraph(): void {
  const instance = lf.value
  if (!instance) return
  const graph = instance.getGraphData() as unknown as CanvasGraphData

  const agentNode = graph.nodes.find(
    (node) => (node.properties as unknown as ZhishuNodeProperties)?.nodeType === 'agent',
  )
  if (!agentNode) return

  const raw = rawNodes.get(agentNode.id)
  const inputsValues = raw?.data?.inputsValues as Record<string, unknown> | undefined
  const agentName = readPlainValue(inputsValues?.agentName)
  // 新建节点的 agentName 是占位值（未命名智能体），不能拿它当配置名回填
  const derived = agentName && agentName !== AGENT_NAME_PLACEHOLDER ? agentName : ''
  if (!derived) return

  const untouched = !meta.configName || meta.configName === DEFAULT_CONFIG_NAME || meta.configName === lastDerivedName
  if (untouched) {
    meta.configName = derived
  }
  lastDerivedName = derived

  if (!meta.description) {
    meta.description = readPlainValue(inputsValues?.description)
  }
}

/** 兼容字符串 / [{key, value}] / [{key, value:{content}}] 三种形态 */
function readPlainValue(value: unknown): string {
  if (typeof value === 'string') return value
  if (Array.isArray(value) && value.length) {
    const first = value[0] as { value?: unknown }
    const raw = first?.value
    if (raw && typeof raw === 'object' && 'content' in (raw as object)) {
      return String((raw as { content?: unknown }).content ?? '')
    }
    return raw === undefined || raw === null ? '' : String(raw)
  }
  return ''
}

async function loadConfig(configId: string): Promise<void> {
  loading.value = true
  try {
    const config = await AiAgentDrawApi.getConfig(configId)
    if (!config) return
    meta.configId = config.configId
    meta.configName = config.configName
    meta.description = config.description ?? ''
    meta.agentId = config.agentId
    meta.version = config.version ?? 0
    meta.status = config.status ?? 1
    meta.updateTime = config.updateTime ?? ''

    let parsed: DrawConfigJson = { nodes: [], edges: [] }
    if (config.configData) {
      try {
        parsed = JSON.parse(config.configData) as DrawConfigJson
      } catch (error) {
        console.error('[agent-config] configData 不是合法 JSON', error)
        message.error('该配置的画布数据无法解析，已置为空白画布')
      }
    }
    renderGraph(parsed)
  } catch (error) {
    console.error('[agent-config] 加载配置失败', error)
  } finally {
    loading.value = false
  }
}

/* --------------------------- 画布操作 --------------------------- */

/** 新节点的默认标题：agent 用 Agent_n，其余用「类型_n」（与旧实现的命名习惯一致） */
function nextNodeTitle(type: DrawNodeType): string {
  const count = lf.value?.getGraphData().nodes.length ?? 0
  const catalog = NODE_CATALOG.find((item) => item.type === type)
  return type === 'agent' ? `Agent_${count}` : `${catalog?.label ?? type}_${count}`
}

/** 面板上的节点项开始拖拽：把类型塞进 dataTransfer，落点由画布的 drop 处理 */
function onPaletteDragStart(type: DrawNodeType, event: DragEvent): void {
  if (!canEdit.value || !event.dataTransfer) return
  event.dataTransfer.setData(PALETTE_DND_MIME, type)
  event.dataTransfer.effectAllowed = 'copy'
}

/** 画布上松手：按落点坐标建节点（对应面板文案「拖拽节点到画布」） */
function onCanvasDrop(event: DragEvent): void {
  const type = event.dataTransfer?.getData(PALETTE_DND_MIME) as DrawNodeType | undefined
  if (!type || !canEdit.value) return
  const instance = lf.value
  if (!instance) return
  event.preventDefault()
  // canvasOverlayPosition 才是「画布内容坐标」（已抵消缩放/平移），节点坐标用它
  const point = instance.getPointByClient(event.clientX, event.clientY)
  addNode(type, { x: point.canvasOverlayPosition.x, y: point.canvasOverlayPosition.y })
}

/**
 * 往画布加一个节点。
 * @param position 落点（面板拖拽 / 粘贴时给出），缺省按网格排布
 * @param sourceRaw Ctrl+C 粘贴时的源业务 JSON：带上它就完整复制原节点（含 inputs/outputs）
 */
function addNode(type: DrawNodeType, position?: { x: number; y: number }, sourceRaw?: DrawNode): void {
  const instance = lf.value
  if (!instance || !canEdit.value) return

  const catalog = NODE_CATALOG.find((item) => item.type === type)
  const count = instance.getGraphData().nodes.length
  const id = generateNodeId(type)
  const title = sourceRaw?.data?.title || nextNodeTitle(type)
  const inputsValues = sourceRaw
    ? (JSON.parse(JSON.stringify(sourceRaw.data.inputsValues ?? {})) as Record<string, unknown>)
    : defaultInputsValues(type)

  // 同步登记业务 JSON，保存时以它为基底；粘贴过来的节点连 inputs / outputs 一起带过来
  rawNodes.set(id, {
    ...(sourceRaw ? (JSON.parse(JSON.stringify(sourceRaw)) as DrawNode) : { id, type }),
    id,
    type,
    data: { ...(sourceRaw?.data ?? {}), title, inputsValues },
  })

  instance.addNode({
    id,
    type: NODE_TYPE_NAME,
    x: position?.x ?? 200 + (count % 5) * 240,
    y: position?.y ?? 160 + Math.floor(count / 5) * 140,
    properties: { nodeType: type, title, hint: referenceHintOf(type, inputsValues) },
  } as never)

  activeNodeId.value = id
  drawerOpen.value = true
}

/* --------------------------- 键盘快捷键对应的操作 --------------------------- */

/** 粘贴偏移量：与 LogicFlow 官方示例一致，避免副本完全压在原件上 */
const PASTE_OFFSET = 40

/** 内部剪贴板：存业务 JSON（深拷贝），而不是画布节点 id */
let clipboardNodes: DrawNode[] = []

/** 画布上选中的节点 id */
function selectedNodeIds(): string[] {
  const instance = lf.value
  if (!instance) return []
  const selected = instance.getSelectElements(true) as unknown as { nodes?: Array<{ id: string }> }
  return (selected?.nodes ?? []).map((node) => node.id)
}

/** Ctrl+C：复制选中节点的业务 JSON（含 inputs / outputs，保持后端契约） */
function copySelection(): void {
  if (!canEdit.value) return
  const raws = selectedNodeIds()
    .map((id) => rawNodes.get(id))
    .filter((node): node is DrawNode => Boolean(node))
  if (!raws.length) return
  clipboardNodes = JSON.parse(JSON.stringify(raws)) as DrawNode[]
  message.success(`已复制 ${raws.length} 个节点`)
}

/** Ctrl+V：把剪贴板里的节点按其原坐标偏移后粘进画布 */
function pasteClipboard(): void {
  const instance = lf.value
  if (!instance || !canEdit.value || !clipboardNodes.length) return
  const items = clipboardNodes
  items.forEach((raw) => {
    const position = (raw.meta?.position as { x?: number; y?: number } | undefined) ?? {}
    addNode(raw.type, { x: Number(position.x ?? 200) + PASTE_OFFSET, y: Number(position.y ?? 160) + PASTE_OFFSET }, raw)
  })
  message.success(`已粘贴 ${items.length} 个节点`)
}

/** Delete / Backspace：删除选中节点与连线 */
function deleteSelection(): void {
  const instance = lf.value
  if (!instance || !canEdit.value) return
  const selected = instance.getSelectElements(true) as unknown as {
    nodes?: Array<{ id: string }>
    edges?: Array<{ id?: string }>
  }
  const nodeIds = (selected?.nodes ?? []).map((node) => node.id)
  const edgeIds = (selected?.edges ?? []).map((edge) => edge.id).filter((id): id is string => Boolean(id))
  if (!nodeIds.length && !edgeIds.length) return

  nodeIds.forEach((id) => {
    instance.deleteNode(id)
    // 刻意不删 rawNodes：撤销（Ctrl+Z）会把同名节点还原回来，raw 还在就能原样恢复
  })
  edgeIds.forEach((id) => instance.deleteEdge(id))

  activeNodeId.value = ''
  drawerOpen.value = false
  refreshCount()
  message.success('已删除选中元素')
}

/** 画布上是否有选中元素（决定要不要接管剪贴板 / 删除键） */
function hasSelection(): boolean {
  const instance = lf.value
  if (!instance) return false
  const selected = instance.getSelectElements(true) as unknown as {
    nodes?: unknown[]
    edges?: unknown[]
  }
  return Boolean(selected?.nodes?.length || selected?.edges?.length)
}

/**
 * 面板文案里承诺的快捷键在这里实现。
 * 不用 LogicFlow 自带 keyboard 的原因：它把事件绑在画布容器上，属性抽屉一开焦点就被 antd 抢走，
 * 快捷键直接失效（实测）。放在 document 层只多一条规则：正在输入框里打字时不抢按键。
 */
function onKeydown(event: KeyboardEvent): void {
  const target = event.target as HTMLElement | null
  const tag = target?.tagName
  if (tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT' || target?.isContentEditable) return

  const withMod = event.ctrlKey || event.metaKey
  const key = event.key.toLowerCase()

  if (withMod && key === 'z') {
    event.preventDefault()
    if (event.shiftKey) lf.value?.redo()
    else lf.value?.undo()
    return
  }
  if (withMod && key === 'y') {
    event.preventDefault()
    lf.value?.redo()
    return
  }
  if (withMod && key === 'c' && hasSelection()) {
    event.preventDefault()
    copySelection()
    return
  }
  if (withMod && key === 'v' && clipboardNodes.length) {
    event.preventDefault()
    pasteClipboard()
    return
  }
  if ((key === 'delete' || key === 'backspace') && hasSelection()) {
    event.preventDefault()
    deleteSelection()
  }
}

function applyNodePatch(payload: { nodeId: string; title: string; inputsValues: Record<string, unknown> }): void {
  const instance = lf.value
  if (!instance) return
  const model = instance.getNodeModelById(payload.nodeId)
  if (!model) return

  const raw = rawNodes.get(payload.nodeId)
  if (raw) {
    rawNodes.set(payload.nodeId, {
      ...raw,
      data: { ...raw.data, title: payload.title, inputsValues: payload.inputsValues },
    })
  }

  // 卡片副标题跟着引用走（原型的副标题就是引用到的资源名）
  model.setProperties({
    title: payload.title,
    hint: referenceHintOf(raw?.type ?? ((model.getProperties() as unknown as ZhishuNodeProperties).nodeType), payload.inputsValues),
  })
  message.success('节点属性已应用')
  syncMetaFromGraph()
}

function removeNode(nodeId: string): void {
  const instance = lf.value
  if (!instance || !canEdit.value) return
  instance.deleteNode(nodeId)
  // 同样保留 rawNodes：撤销后节点回来时业务 JSON 还在
  activeNodeId.value = ''
  drawerOpen.value = false
  refreshCount()
  message.success('节点已删除')
  syncMetaFromGraph()
}

function zoom(delta: number): void {
  lf.value?.zoom(delta)
}

function fitView(): void {
  lf.value?.fitView(60, 60)
}

/** 只读态：静默模式 + 禁止拖动与连线（LogicFlow EditConfig 的字段名） */
function applyReadonlyConfig(on: boolean): void {
  lf.value?.updateEditConfig({
    isSilentMode: on,
    adjustNodePosition: !on,
    adjustEdge: !on,
    hideAnchors: on,
    allowRotation: false,
  })
}

function toggleReadonly(): void {
  readonly.value = !readonly.value
  applyReadonlyConfig(readonly.value)
  message.info(readonly.value ? '已切换为只读模式' : '已退出只读模式')
}

/* --------------------------- 保存与装配 --------------------------- */

async function handleSave(): Promise<void> {
  const instance = lf.value
  if (!instance) return
  if (!meta.configName.trim()) {
    message.warning('请填写配置名称')
    return
  }
  if (!canEdit.value) {
    message.warning('只读模式下不可保存')
    return
  }

  const config = toDrawConfigJson(instance.getGraphData() as unknown as CanvasGraphData, rawNodes, rawEdges)
  if (!config.nodes.length) {
    message.warning('画布为空，至少需要一个节点')
    return
  }

  saving.value = true
  try {
    const configId = await AiAgentDrawApi.saveConfig({
      configId: meta.configId,
      configName: meta.configName.trim(),
      description: meta.description,
      agentId: meta.agentId,
      configData: JSON.stringify(config),
      createBy: userStore.displayName,
      updateBy: userStore.displayName,
    })
    if (configId) {
      meta.configId = configId
      // 首次保存后把 configId 写进地址栏，刷新不丢上下文
      void router.replace({ path: '/admin/agent-config', query: { configId } })
      // 顶栏的「版本 / 最后保存」取库里真实值，别自己猜
      try {
        const fresh = await AiAgentDrawApi.getConfig(configId)
        if (fresh) {
          meta.version = fresh.version ?? meta.version
          meta.status = fresh.status ?? meta.status
          meta.updateTime = fresh.updateTime ?? ''
        }
      } catch (error) {
        console.error('[agent-config] 保存后回读版本信息失败', error)
      }
    }
    message.success(`保存成功，配置ID：${configId || meta.configId}`)
  } catch (error) {
    console.error('[agent-config] 保存失败', error)
  } finally {
    saving.value = false
  }
}

async function handleArmory(): Promise<void> {
  if (!meta.agentId) {
    message.warning('该配置尚未关联智能体，请先保存（保存时会自动创建智能体记录）')
    return
  }
  arming.value = true
  try {
    await AgentApi.armoryAgent(meta.agentId)
    message.success('装配成功，流程图已生效')
  } catch (error) {
    console.error('[agent-config] 装配失败', error)
  } finally {
    arming.value = false
  }
}

function goBack(): void {
  void router.push('/admin/agent-list')
}

onMounted(async () => {
  readonly.value = route.query.mode === 'view'
  initCanvas()
  window.addEventListener('keydown', onKeydown)

  const configId = typeof route.query.configId === 'string' ? route.query.configId : ''
  if (configId) {
    await loadConfig(configId)
  } else {
    renderGraph(defaultGraph())
  }
  applyReadonlyConfig(readonly.value)
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', onKeydown)
  // 切换路由时销毁实例，避免 LogicFlow 的全局事件与 DOM 引用泄漏
  lf.value?.destroy()
  lf.value = null
})
</script>

<template>
  <div class="flex h-full min-h-0 flex-col bg-white">
    <!-- 工具栏 -->
    <!-- 工具栏元素多（原型同款），窄窗口下允许换行，避免「运行 / 装配」被裁掉 -->
    <header class="flex min-h-12 shrink-0 flex-wrap items-center gap-x-2 gap-y-1.5 border-b border-line px-4 py-1.5">
      <button
        type="button"
        class="flex h-7 cursor-pointer items-center gap-1.5 rounded-md px-2 text-[12.5px] text-ink-600 transition hover:bg-[#F2F4FB] hover:text-brand"
        @click="goBack"
      >
        <ArrowLeft :size="13" />
        返回
      </button>
      <span class="h-4 w-px bg-line"></span>

      <Input v-model:value="meta.configName" :disabled="!canEdit" class="!w-[176px] shrink-0" size="small" placeholder="配置名称" />
      <!-- 原型此处是「配置名 + 已启用 + 版本 vN · 最后保存 MM-DD HH:mm」，取值都来自配置表，不做假数据 -->
      <span v-if="hasPersistedConfig" class="stage shrink-0" :class="meta.status === 1 ? 'stage-summary' : 'stage-error'">
        {{ meta.status === 1 ? '已启用' : '已禁用' }}
      </span>
      <span v-if="hasPersistedConfig" class="shrink-0 whitespace-nowrap text-[11.5px] text-ink-400">
        版本 v{{ meta.version }}<template v-if="lastSavedText"> · 最后保存 {{ lastSavedText }}</template>
      </span>
      <span v-if="meta.configId" class="max-w-[180px] shrink-0 truncate rounded-md bg-[#F2F4FB] px-1.5 py-0.5 font-mono text-[11px] text-ink-600">
        configId: {{ meta.configId }}
      </span>
      <span v-if="readonly" class="shrink-0 rounded-full bg-[#FDF0E0] px-2 py-0.5 text-[11px] text-[#B45309]">只读</span>

      <span class="mx-1 h-4 w-px bg-line"></span>
      <Tooltip title="撤销">
        <Button size="small" :disabled="!canEdit" @click="lf?.undo()">
          <Undo2 :size="13" />
        </Button>
      </Tooltip>
      <Tooltip title="重做">
        <Button size="small" :disabled="!canEdit" @click="lf?.redo()">
          <Redo2 :size="13" />
        </Button>
      </Tooltip>

      <span class="mx-1 h-4 w-px shrink-0 bg-line"></span>
      <div class="flex shrink-0 items-center gap-1 rounded-md border border-line px-1">
        <Button size="small" type="text" @click="zoom(-0.1)">
          <Minus :size="13" />
        </Button>
        <span class="w-[42px] text-center font-mono text-[11.5px] text-ink-600">{{ zoomPercent }}%</span>
        <Button size="small" type="text" @click="zoom(0.1)">
          <Plus :size="13" />
        </Button>
      </div>
      <Button size="small" class="shrink-0 whitespace-nowrap" @click="fitView">
        <Maximize2 :size="13" class="mr-1" />
        适应画布
      </Button>
      <Button size="small" class="shrink-0 whitespace-nowrap" @click="toggleReadonly">
        {{ readonly ? '退出只读' : '只读' }}
      </Button>

      <div class="ml-auto flex shrink-0 items-center gap-2">
        <Tooltip title="保存时序列化为后端可解析的 JSON：nodes[].{id,type,data.inputsValues} 与 edges[].{sourceNodeID,targetNodeID}">
          <Button size="small" class="whitespace-nowrap" :loading="saving" :disabled="!canEdit" @click="handleSave">
            <Save :size="13" class="mr-1" />
            保存
          </Button>
        </Tooltip>
        <Button
          size="small"
          type="primary"
          class="btn-grad whitespace-nowrap"
          :loading="arming"
          :disabled="!canEdit"
          @click="handleArmory"
        >
          <Zap :size="13" class="mr-1" />
          运行 / 装配
        </Button>
      </div>
    </header>

    <div class="flex min-h-0 flex-1">
      <!-- 节点面板 -->
      <aside class="scroll-thin flex w-[200px] shrink-0 flex-col overflow-y-auto border-r border-line p-3">
        <p class="mb-2 px-1 text-[11px] tracking-wider text-ink-400">拖拽节点到画布</p>

        <template v-for="group in paletteGroups" :key="group.label">
          <p class="mb-1.5 mt-2 px-1 text-[11.5px] font-medium text-ink-600">{{ group.label }}</p>
          <div class="flex flex-col gap-1.5">
            <button
              v-for="item in group.items"
              :key="item.type"
              type="button"
              draggable="true"
              class="flex cursor-grab items-center gap-2 rounded-lg border border-line px-2.5 py-2 text-[12.5px] transition hover:border-brand-500 hover:bg-[#F8F9FE] disabled:cursor-not-allowed disabled:opacity-50"
              :disabled="!canEdit"
              @dragstart="onPaletteDragStart(item.type, $event)"
              @click="addNode(item.type)"
            >
              <span class="h-2.5 w-2.5 shrink-0 rounded-sm" :style="{ background: item.color }"></span>
              <span>{{ item.label }}</span>
            </button>
          </div>
        </template>

        <div class="mt-4 rounded-lg bg-page px-3 py-2.5 text-[11px] leading-5 text-ink-400">
          <span class="font-medium text-ink-600">快捷键：</span>
          <span class="font-mono">Ctrl+Z</span> 撤销 · <span class="font-mono">Ctrl+C/V</span> 复制 ·
          <span class="font-mono">Delete</span> 删除 · 滚轮缩放 · 拖动节点右侧圆点连线
        </div>
      </aside>

      <!-- 画布 -->
      <section class="relative min-w-0 flex-1" @dragover.prevent @drop="onCanvasDrop">
        <div ref="canvasRef" class="h-full w-full"></div>

        <div v-if="loading" class="absolute inset-0 z-10 flex items-center justify-center bg-white/70">
          <Spin tip="正在加载画布配置…" />
        </div>

        <!-- 右下状态条（原型：N 节点 · M 连线 | 点击节点编辑属性） -->
        <div
          class="pointer-events-none absolute bottom-4 right-4 flex items-center gap-2 rounded-lg border border-line bg-white/90 px-3 py-1.5 text-[11px] text-ink-600 backdrop-blur"
        >
          <span class="font-mono">{{ nodeCount }} 节点 · {{ edgeCount }} 连线</span>
          <span class="text-[#D9DEF0]">|</span>
          <span>点击节点编辑属性</span>
        </div>
      </section>
    </div>

    <NodeFormDrawer v-model:open="drawerOpen" :node="selectedNode" @apply="applyNodePatch" @remove="removeNode" />
  </div>
</template>
