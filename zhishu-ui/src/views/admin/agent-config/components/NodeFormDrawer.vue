<script setup lang="ts">
/**
 * 节点属性抽屉。
 *
 * 只负责「节点名称 + 该类型的引用与关键参数」，写回时通过 adapter 的 writeRefValue
 * 保留原始的 [{key, value}] 结构（含 value 为 {content} 对象的情形），避免破坏后端契约。
 */
import { computed, reactive, ref, watch } from 'vue'
import { Button, Drawer, Input, InputNumber, message, Select } from 'ant-design-vue'
import { AiClientAdvisorApi } from '@/api/ai-client-advisor'
import { AiClientApi } from '@/api/ai-client'
import { AiClientModelApi } from '@/api/ai-client-model'
import { AiClientSystemPromptApi } from '@/api/ai-client-system-prompt'
import { AiClientToolMcpApi } from '@/api/ai-client-tool-mcp'
import { catalogOf, CLIENT_TYPE_OPTIONS, STRATEGY_OPTIONS } from '../logicflow/catalog'
import { readRefValue, writeRefValue } from '../logicflow/adapter'
import type { DrawNodeType } from '@/types/draw'

interface DrawerNode {
  id: string
  nodeType: DrawNodeType
  title: string
  raw?: { data?: { inputsValues?: Record<string, unknown> } }
}

const props = defineProps<{
  open: boolean
  node: DrawerNode | null
}>()

const emit = defineEmits<{
  (e: 'update:open', value: boolean): void
  (e: 'apply', payload: { nodeId: string; title: string; inputsValues: Record<string, unknown> }): void
  (e: 'remove', nodeId: string): void
}>()

interface Option {
  label: string
  value: string
}

/** 各类节点可编辑的字段描述 */
const REF_FIELD: Partial<Record<DrawNodeType, { key: string; label: string; hint: string }>> = {
  agent: { key: 'agentName', label: '智能体引用', hint: '同名会复用已有智能体记录，装配时按其配置拉起' },
  client: { key: 'clientName', label: '客户端引用', hint: '选择后会同时写入 clientId，装配时按 clientId 关联流程' },
  model: { key: 'modelName', label: '模型引用', hint: '写入模型ID（modelId）' },
  prompt: { key: 'promptName', label: '提示词引用', hint: '写入提示词ID（promptId）' },
  advisor: { key: 'advisorName', label: '顾问引用', hint: '写入顾问ID（advisorId）' },
  tool_mcp: { key: 'toolMcpName', label: 'MCP 工具引用', hint: '写入工具ID（mcpId）' },
}

/** 选项缓存：同一类型只拉一次，避免每次选中节点都打接口 */
const optionCache = ref<Record<string, Option[]>>({})
const optionLoading = ref(false)
/** 客户端原始数据缓存：选中引用时用来回填 clientId，避免二次请求 */
const clientRawCache = ref<Array<{ clientId: string; clientName: string }>>([])

async function loadOptions(type: DrawNodeType): Promise<Option[]> {
  const cached = optionCache.value[type]
  if (cached) return cached

  optionLoading.value = true
  let options: Option[] = []
  try {
    if (type === 'agent') {
      // 智能体以「名称」为引用值（后端按 agentName 建 ai_agent 记录）
      const { AgentApi } = await import('@/api/agent')
      const list = await AgentApi.queryAvailableAgents()
      options = (list ?? []).map((item) => ({ label: `${item.agentName}（agentId=${item.agentId}）`, value: item.agentName }))
    } else if (type === 'client') {
      const list = await AiClientApi.queryAll()
      clientRawCache.value = (list ?? []).map((item) => ({ clientId: item.clientId, clientName: item.clientName }))
      options = clientRawCache.value.map((item) => ({
        label: `${item.clientName}（clientId=${item.clientId}）`,
        value: item.clientName,
      }))
    } else if (type === 'model') {
      const list = await AiClientModelApi.queryEnabled()
      options = (list ?? []).map((item) => ({ label: `${item.modelName}（modelId=${item.modelId}）`, value: item.modelId }))
    } else if (type === 'prompt') {
      const list = await AiClientSystemPromptApi.queryAll()
      options = (list ?? []).map((item) => ({ label: `${item.promptName}（promptId=${item.promptId}）`, value: item.promptId }))
    } else if (type === 'advisor') {
      const list = await AiClientAdvisorApi.queryAll()
      options = (list ?? []).map((item) => ({ label: `${item.advisorName}（advisorId=${item.advisorId}）`, value: item.advisorId }))
    } else if (type === 'tool_mcp') {
      const list = await AiClientToolMcpApi.queryAll()
      options = (list ?? []).map((item) => ({ label: `${item.mcpName}（mcpId=${item.mcpId}）`, value: item.mcpId }))
    }
    optionCache.value = { ...optionCache.value, [type]: options }
  } catch (error) {
    // 拉取失败不阻塞编辑：仍可手工保留原有值
    console.error('[node-drawer] 加载引用选项失败', error)
  } finally {
    optionLoading.value = false
  }
  return options
}

const form = reactive({
  title: '',
  refValue: undefined as string | undefined,
  description: '',
  channel: '',
  strategy: 'flowAgentExecuteStrategy',
  clientType: 'DEFAULT',
  clientId: '',
  sequence: 1,
})

const nodeType = computed<DrawNodeType>(() => props.node?.nodeType ?? 'agent')
const catalog = computed(() => catalogOf(nodeType.value))
const refField = computed(() => REF_FIELD[nodeType.value])
const refOptions = computed<Option[]>(() => optionCache.value[nodeType.value] ?? [])
const isClient = computed(() => nodeType.value === 'client')
const isAgent = computed(() => nodeType.value === 'agent')

function syncFromNode(): void {
  const node = props.node
  if (!node) return

  const inputsValues = node.raw?.data?.inputsValues
  form.title = node.title
  form.description = readRefValue(inputsValues, 'description')
  form.channel = readRefValue(inputsValues, 'channel') || 'agent'
  form.strategy = readRefValue(inputsValues, 'strategy') || 'flowAgentExecuteStrategy'
  form.clientType = readRefValue(inputsValues, 'clientType') || 'DEFAULT'
  form.clientId = readRefValue(inputsValues, 'clientId')
  form.sequence = Number(readRefValue(inputsValues, 'sequence') || 1)

  const key = refField.value?.key
  form.refValue = key ? readRefValue(inputsValues, key) || undefined : undefined

  if (refField.value) void loadOptions(nodeType.value)
}

watch(() => [props.open, props.node?.id], () => {
  if (props.open) syncFromNode()
}, { immediate: true })

/** 选择客户端时同步 clientId（后端装配按 clientId 关联流程） */
function onRefChange(value: unknown): void {
  if (nodeType.value !== 'client') return
  if (typeof value !== 'string') return
  const hit = clientRawCache.value.find((item) => item.clientName === value)
  if (hit) form.clientId = hit.clientId
}

function handleApply(): void {
  const node = props.node
  if (!node) return

  const base = { ...(node.raw?.data?.inputsValues ?? {}) }
  let next = base

  const key = refField.value?.key
  if (key) {
    next = writeRefValue(next, key, form.refValue ?? '')
  }
  if (isClient.value) {
    next = { ...next, clientId: form.clientId }
    next = writeRefValue(next, 'clientType', form.clientType)
    next = writeRefValue(next, 'sequence', String(form.sequence))
  }
  if (isAgent.value) {
    next = writeRefValue(next, 'description', form.description)
    next = writeRefValue(next, 'channel', form.channel)
    next = writeRefValue(next, 'strategy', form.strategy)
  }

  emit('apply', { nodeId: node.id, title: form.title || catalog.value.label, inputsValues: next })
}

/** 重置：丢弃尚未应用的改动，回到画布上该节点的当前值 */
function handleReset(): void {
  syncFromNode()
  message.info('已重置为画布上的当前属性')
}
</script>

<template>
  <Drawer
    :open="open"
    placement="right"
    :width="340"
    :mask="false"
    :body-style="{ padding: '16px' }"
    @update:open="(value: boolean) => emit('update:open', value)"
  >
    <template #title>
      <div class="flex items-center gap-2">
        <span class="text-[13.5px] font-medium">节点属性</span>
        <span class="rounded-full px-2 py-0.5 text-[11px]" :style="{ background: `${catalog.color}1A`, color: catalog.color }">
          {{ catalog.label }}
        </span>
      </div>
    </template>

    <div v-if="!node" class="py-10 text-center text-[12.5px] text-ink-400">请在画布中选中一个节点</div>

    <div v-else class="space-y-4">
      <div>
        <label class="mb-1.5 block text-[12px] text-ink-600">
          节点名称 <span class="text-[#E5484D]">*</span>
        </label>
        <Input v-model:value="form.title" placeholder="用于卡片标题，也是保存时写入的 data.title" />
        <p class="mt-1.5 font-mono text-[10.5px] text-ink-400">{{ node.id }}</p>
      </div>

      <div v-if="refField">
        <label class="mb-1.5 block text-[12px] text-ink-600">{{ refField.label }}</label>
        <Select
          v-model:value="form.refValue"
          :options="refOptions"
          :loading="optionLoading"
          :placeholder="`请选择${refField.label}`"
          :allow-clear="true"
          show-search
          :filter-option="(input: string, option: any) => String(option?.label ?? '').toLowerCase().includes(input.toLowerCase())"
          class="w-full"
          @change="onRefChange"
        />
        <p class="mt-1.5 text-[11px] leading-5 text-ink-400">{{ refField.hint }}</p>
      </div>

      <div v-if="isClient">
        <label class="mb-1.5 block text-[12px] text-ink-600">客户端ID（clientId）</label>
        <Input v-model:value="form.clientId" placeholder="选择客户端引用后自动填充" />
      </div>

      <div v-if="isClient">
        <label class="mb-1.5 block text-[12px] text-ink-600">客户端类型（clientType）</label>
        <Select v-model:value="form.clientType" :options="CLIENT_TYPE_OPTIONS" class="w-full" />
      </div>

      <div v-if="isClient">
        <label class="mb-1.5 block text-[12px] text-ink-600">执行顺序（sequence）</label>
        <InputNumber v-model:value="form.sequence" :min="1" class="w-full" />
      </div>

      <template v-if="isAgent">
        <div>
          <label class="mb-1.5 block text-[12px] text-ink-600">描述</label>
          <Input v-model:value="form.description" placeholder="智能体用途说明，会写入 ai_agent 表" />
        </div>
        <div>
          <label class="mb-1.5 block text-[12px] text-ink-600">渠道 channel</label>
          <Input v-model:value="form.channel" placeholder="如 agent / chat_stream" />
        </div>
        <div>
          <label class="mb-1.5 block text-[12px] text-ink-600">执行策略 strategy</label>
          <Select v-model:value="form.strategy" :options="STRATEGY_OPTIONS" class="w-full" />
        </div>
      </template>

      <div class="rounded-lg bg-page px-3 py-2.5 text-[11px] leading-5 text-ink-400">
        保存时序列化为后端可解析的 JSON：<span class="font-mono text-ink-600">nodes[].{id,type,data.inputsValues}</span>
        与 <span class="font-mono text-ink-600">edges[].{sourceNodeID,targetNodeID}</span>。
      </div>

      <div class="flex items-center gap-2 border-t border-line pt-4">
        <Button danger class="!h-8 text-[12.5px]" @click="emit('remove', node.id)">删除节点</Button>
        <Button class="btn-ghost !h-8 ml-auto text-[12.5px]" @click="handleReset">重置</Button>
        <Button type="primary" class="btn-grad !h-8 text-[12.5px]" @click="handleApply">应用</Button>
      </div>
    </div>
  </Drawer>
</template>
