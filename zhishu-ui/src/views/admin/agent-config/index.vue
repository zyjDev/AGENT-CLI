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
import LogicFlow from '@logicflow/core'
import '@logicflow/core/dist/style/index.css'
import { Alert, Button, Input, message, Spin, Tooltip } from 'ant-design-vue'
import { ArrowLeft, Maximize2, Minus, Plus, Redo2, Save, Undo2, Zap } from 'lucide-vue-next'
import { AgentApi } from '@/api/agent'
import { AiAgentDrawApi } from '@/api/ai-agent-draw'
import { useUserStore } from '@/store/modules/user'
import type { DrawConfigJson, DrawEdge, DrawNode, DrawNodeType } from '@/types/draw'
import NodeFormDrawer from './components/NodeFormDrawer.vue'
import {
  fromDrawConfigJson,
  toDrawConfigJson,
  type CanvasGraphData,
  type ZhishuNodeProperties,
} from './logicflow/adapter'
import { defaultInputsValues, generateNodeId, NODE_CATALOG } from './logicflow/catalog'
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

const meta = reactive({
  configId: '',
  configName: DEFAULT_CONFIG_NAME,
  description: '',
  agentId: '',
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
    keyboard: { enabled: true },
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

  instance.render({ nodes: [], edges: [] } as never)
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
  const derived = readPlainValue(inputsValues?.agentName) || agentNode.properties?.title?.toString() || ''
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

function addNode(type: DrawNodeType): void {
  const instance = lf.value
  if (!instance || !canEdit.value) return

  const catalog = NODE_CATALOG.find((item) => item.type === type)
  const count = instance.getGraphData().nodes.length
  const id = generateNodeId(type)
  const title = type === 'agent' ? `Agent_${count}` : `${catalog?.label ?? type}_${count}`
  const inputsValues = defaultInputsValues(type)

  // 同步登记业务 JSON，保存时以它为基底
  rawNodes.set(id, { id, type, data: { title, inputsValues } })

  instance.addNode({
    id,
    type: NODE_TYPE_NAME,
    x: 200 + (count % 5) * 240,
    y: 160 + Math.floor(count / 5) * 140,
    properties: { nodeType: type, title, hint: catalog?.hint ?? '' },
  } as never)

  activeNodeId.value = id
  drawerOpen.value = true
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

  model.setProperties({ title: payload.title })
  message.success('节点属性已应用')
  syncMetaFromGraph()
}

function removeNode(nodeId: string): void {
  const instance = lf.value
  if (!instance || !canEdit.value) return
  instance.deleteNode(nodeId)
  rawNodes.delete(nodeId)
  activeNodeId.value = ''
  drawerOpen.value = false
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

  const configId = typeof route.query.configId === 'string' ? route.query.configId : ''
  if (configId) {
    await loadConfig(configId)
  } else {
    renderGraph(defaultGraph())
  }
  applyReadonlyConfig(readonly.value)
})

onBeforeUnmount(() => {
  // 切换路由时销毁实例，避免 LogicFlow 的全局事件与 DOM 引用泄漏
  lf.value?.destroy()
  lf.value = null
})
</script>

<template>
  <div class="flex h-full min-h-0 flex-col bg-white">
    <!-- 工具栏 -->
    <header class="flex h-12 shrink-0 flex-nowrap items-center gap-2 overflow-x-auto border-b border-line px-4">
      <button
        type="button"
        class="flex h-7 cursor-pointer items-center gap-1.5 rounded-md px-2 text-[12.5px] text-ink-600 transition hover:bg-[#F2F4FB] hover:text-brand"
        @click="goBack"
      >
        <ArrowLeft :size="13" />
        返回
      </button>
      <span class="h-4 w-px bg-line"></span>

      <Input v-model:value="meta.configName" :disabled="!canEdit" class="!w-[220px] shrink-0" size="small" placeholder="配置名称" />
      <span v-if="meta.configId" class="max-w-[180px] truncate rounded-md bg-[#F2F4FB] px-1.5 py-0.5 font-mono text-[11px] text-ink-600">
        {{ meta.configId }}
      </span>
      <span v-if="readonly" class="rounded-full bg-[#FDF0E0] px-2 py-0.5 text-[11px] text-[#B45309]">只读</span>

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
        <Button size="small" class="whitespace-nowrap" :loading="saving" :disabled="!canEdit" @click="handleSave">
          <Save :size="13" class="mr-1" />
          保存
        </Button>
        <Button
          size="small"
          type="primary"
          class="btn-grad whitespace-nowrap"
          :loading="arming"
          :disabled="!canEdit"
          @click="handleArmory"
        >
          <Zap :size="13" class="mr-1" />
          装配
        </Button>
      </div>
    </header>

    <div class="flex min-h-0 flex-1">
      <!-- 节点面板 -->
      <aside class="scroll-thin flex w-[200px] shrink-0 flex-col gap-1.5 overflow-y-auto border-r border-line p-3">
        <p class="mb-1 text-[11px] tracking-wider text-ink-400">点击添加到画布</p>

        <button
          v-for="item in NODE_CATALOG.filter((node) => node.canAdd)"
          :key="item.type"
          type="button"
          class="flex cursor-pointer items-center gap-2 rounded-lg border border-line px-2.5 py-2 text-[12.5px] transition hover:border-brand-500 hover:bg-[#F8F9FE] disabled:cursor-not-allowed disabled:opacity-50"
          :disabled="!canEdit"
          @click="addNode(item.type)"
        >
          <span class="h-2.5 w-2.5 shrink-0 rounded-sm" :style="{ background: item.color }"></span>
          <span>{{ item.label }}</span>
        </button>

        <div class="mt-3 rounded-lg bg-page px-3 py-2.5 text-[11px] leading-5 text-ink-400">
          拖动节点右侧圆点连线；<span class="font-mono">Ctrl+Z</span> 撤销 · <span class="font-mono">Delete</span> 删除 · 滚轮缩放。
        </div>
      </aside>

      <!-- 画布 -->
      <section class="relative min-w-0 flex-1">
        <div ref="canvasRef" class="h-full w-full"></div>

        <div v-if="loading" class="absolute inset-0 z-10 flex items-center justify-center bg-white/70">
          <Spin tip="正在加载画布配置…" />
        </div>

        <Alert
          class="absolute bottom-4 left-4 !w-[380px]"
          type="info"
          show-icon
          message="保存按后端契约序列化"
          description="连线字段为 sourceNodeID / targetNodeID；未在属性面板暴露的字段（如 inputs / outputs 的 JsonSchema）会原样保留。"
        />
      </section>
    </div>

    <NodeFormDrawer v-model:open="drawerOpen" :node="selectedNode" @apply="applyNodePatch" @remove="removeNode" />
  </div>
</template>
