<script setup lang="ts">
/**
 * 数据总览。
 *
 * 只展示后端确实提供的数据（GET /v1/admin/data/statistics/get-data-statistics +
 * POST /v1/admin/ai-agent-draw/query-list）。
 * 刻意不画「近 7 日趋势」：后端没有时序聚合接口，为了不改后端而前端编数据会误导判断；
 * 需要趋势时应在后端补一个按天聚合的接口。
 */
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Button, Table, Tag } from 'ant-design-vue'
import { AlertCircle, ArrowUpRight, RefreshCw } from 'lucide-vue-next'
import { DataStatisticsApi, type DataStatistics } from '@/api/data-statistics'
import { AiAgentDrawApi, type AiAgentDrawConfigItem } from '@/api/ai-agent-draw'

const router = useRouter()

const EMPTY_STATISTICS: DataStatistics = {
  activeAgentCount: 0,
  clientCount: 0,
  mcpToolCount: 0,
  systemPromptCount: 0,
  ragOrderCount: 0,
  advisorCount: 0,
  modelCount: 0,
  todayRequestCount: 0,
  successRate: 0,
  runningTaskCount: 0,
}

const statistics = ref<DataStatistics>({ ...EMPTY_STATISTICS })
const loading = ref(false)
const loadError = ref('')

const configs = ref<AiAgentDrawConfigItem[]>([])
const configsLoading = ref(false)

const primaryCards = computed(() => [
  { label: '活跃智能体', value: statistics.value.activeAgentCount, hint: '已装配且状态为启用' },
  { label: '客户端数', value: statistics.value.clientCount, hint: '对话客户端总数' },
  { label: '接入模型数', value: statistics.value.modelCount, hint: '含对话与嵌入模型' },
  { label: '今日对话调用', value: statistics.value.todayRequestCount, hint: '自当日 00:00 起' },
])

/** 资源分布：用真实计数画横向占比条，代替不可得的时序趋势图 */
const distribution = computed(() => {
  const items = [
    { label: '客户端', value: statistics.value.clientCount },
    { label: '模型', value: statistics.value.modelCount },
    { label: 'MCP 工具', value: statistics.value.mcpToolCount },
    { label: '系统提示词', value: statistics.value.systemPromptCount },
    { label: '顾问', value: statistics.value.advisorCount },
    { label: '知识库', value: statistics.value.ragOrderCount },
  ]
  const max = Math.max(1, ...items.map((item) => item.value))
  return items.map((item) => ({ ...item, percent: Math.round((item.value / max) * 100) }))
})

const configColumns = [
  { title: '配置名称', dataIndex: 'configName', key: 'configName' },
  { title: '智能体ID', dataIndex: 'agentId', key: 'agentId', width: 130 },
  { title: '版本', dataIndex: 'version', key: 'version', width: 90 },
  { title: '状态', dataIndex: 'status', key: 'status', width: 110 },
  { title: '更新时间', dataIndex: 'updateTime', key: 'updateTime', width: 180 },
]

function formatTime(value: unknown): string {
  if (!value) return '—'
  const date = new Date(String(value).replace(' ', 'T'))
  if (Number.isNaN(date.getTime())) return String(value)
  const pad = (n: number): string => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

async function loadStatistics(): Promise<void> {
  loading.value = true
  loadError.value = ''
  try {
    statistics.value = await DataStatisticsApi.get()
  } catch (error) {
    loadError.value = '统计数据加载失败，请确认后端服务与登录态是否正常'
    console.error('[dashboard] 加载统计数据失败', error)
  } finally {
    loading.value = false
  }
}

async function loadConfigs(): Promise<void> {
  configsLoading.value = true
  try {
    const list = await AiAgentDrawApi.queryList({ pageNum: 1, pageSize: 5 })
    configs.value = Array.isArray(list) ? list : []
  } catch (error) {
    console.error('[dashboard] 加载编排配置失败', error)
    configs.value = []
  } finally {
    configsLoading.value = false
  }
}

function refreshAll(): void {
  void loadStatistics()
  void loadConfigs()
}

function goConfig(configId: string): void {
  void router.push({ path: '/admin/agent-config', query: { configId } })
}

onMounted(refreshAll)
</script>

<template>
  <div class="space-y-4">
    <div class="flex flex-wrap items-center justify-between gap-3">
      <div>
        <h1 class="text-[20px] font-semibold tracking-tight">数据总览</h1>
        <p class="mt-1 text-[12px] text-ink-400">
          统计口径来自后端实时聚合接口，数值随资源变更即时变化
        </p>
      </div>
      <Button class="!h-8 text-[12.5px]" :loading="loading || configsLoading" @click="refreshAll">
        <RefreshCw :size="13" class="mr-1" />
        刷新数据
      </Button>
    </div>

    <div v-if="loadError" class="flex items-start gap-2.5 rounded-xl bg-[#FEF4F4] px-4 py-3">
      <AlertCircle :size="15" class="mt-0.5 shrink-0 text-err" />
      <p class="text-[12.5px] leading-6 text-ink-600">{{ loadError }}</p>
    </div>

    <!-- 主指标 -->
    <div class="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
      <div v-for="card in primaryCards" :key="card.label" class="card-panel card-hover p-4">
        <p class="text-[12px] text-ink-400">{{ card.label }}</p>
        <p class="mt-2 text-[26px] font-semibold leading-none tracking-tight">
          {{ card.value.toLocaleString() }}
        </p>
        <p class="mt-3 text-[11.5px] text-ink-400">{{ card.hint }}</p>
      </div>
    </div>

    <div class="grid grid-cols-1 gap-4 xl:grid-cols-[1.4fr_1fr]">
      <!-- 资源分布 -->
      <div class="card-panel p-5">
        <h2 class="text-[14px] font-medium">资源分布</h2>
        <p class="mt-1 text-[11.5px] text-ink-400">按各类资源配置数量归一化展示（最长条为当前最大值）</p>

        <div class="mt-5 space-y-4">
          <div v-for="item in distribution" :key="item.label">
            <div class="mb-1.5 flex items-center justify-between text-[12.5px]">
              <span class="text-[#3B4252]">{{ item.label }}</span>
              <span class="font-mono text-ink-600">{{ item.value }}</span>
            </div>
            <div class="h-2 overflow-hidden rounded-full bg-[#F1F3F9]">
              <div class="brand-grad h-full rounded-full transition-all duration-500" :style="{ width: `${Math.max(item.percent, item.value > 0 ? 4 : 0)}%` }"></div>
            </div>
          </div>
        </div>
      </div>

      <!-- 运行状态 -->
      <div class="card-panel flex flex-col p-5">
        <h2 class="text-[14px] font-medium">运行状态</h2>
        <p class="mt-1 text-[11.5px] text-ink-400">对话链路健康度与后台任务</p>

        <div class="mt-5 flex items-end gap-2">
          <span class="text-[36px] font-semibold leading-none tracking-tight text-ok">
            {{ statistics.successRate.toFixed(1) }}
          </span>
          <span class="pb-1 text-[14px] text-ink-600">%</span>
          <span class="ml-auto pb-1 text-[11.5px] text-ink-400">请求成功率</span>
        </div>
        <div class="mt-3 h-2 overflow-hidden rounded-full bg-[#F1F3F9]">
          <div class="h-full rounded-full bg-ok transition-all duration-500" :style="{ width: `${Math.min(Math.max(statistics.successRate, 0), 100)}%` }"></div>
        </div>

        <div class="mt-5 grid grid-cols-2 gap-3">
          <div class="rounded-lg bg-page px-3.5 py-3">
            <p class="text-[11.5px] text-ink-400">运行中任务</p>
            <p class="mt-1.5 text-[18px] font-semibold leading-none">{{ statistics.runningTaskCount }}</p>
          </div>
          <div class="rounded-lg bg-page px-3.5 py-3">
            <p class="text-[11.5px] text-ink-400">知识库数量</p>
            <p class="mt-1.5 text-[18px] font-semibold leading-none">{{ statistics.ragOrderCount }}</p>
          </div>
        </div>

        <p class="mt-auto pt-5 text-[11.5px] leading-5 text-ink-400">
          趋势图需要后端提供按天聚合接口；当前不改后端，因此只呈现可得的实时数值。
        </p>
      </div>
    </div>

    <!-- 最近更新的编排配置 -->
    <div class="card-panel">
      <div class="flex flex-wrap items-center justify-between gap-3 border-b border-[#F1F3F9] px-5 py-3.5">
        <div class="flex items-center gap-2.5">
          <h2 class="text-[14px] font-medium">最近更新的编排配置</h2>
          <span class="rounded-full bg-[#EEF0FE] px-2 py-0.5 text-[11px] text-brand">Top 5</span>
        </div>
        <button
          type="button"
          class="flex cursor-pointer items-center gap-1 text-[12.5px] text-brand hover:underline"
          @click="router.push('/admin/agent-list')"
        >
          查看全部
          <ArrowUpRight :size="13" />
        </button>
      </div>

      <Table
        :columns="configColumns"
        :data-source="configs"
        :loading="configsLoading"
        row-key="configId"
        :pagination="false"
        size="middle"
        :scroll="{ x: 900 }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'agentId'">
            <span class="font-mono text-[12.5px] text-ink-600">{{ record.agentId ?? '—' }}</span>
          </template>
          <template v-else-if="column.key === 'version'">
            <span class="font-mono text-[12.5px] text-ink-600">v{{ record.version ?? 1 }}</span>
          </template>
          <template v-else-if="column.key === 'status'">
            <Tag :color="record.status === 1 ? 'success' : 'default'">
              {{ record.status === 1 ? '启用' : '停用' }}
            </Tag>
          </template>
          <template v-else-if="column.key === 'updateTime'">
            <span class="font-mono text-[12px] text-ink-400">{{ formatTime(record.updateTime) }}</span>
          </template>
          <template v-else-if="column.key === 'configName'">
            <button type="button" class="cursor-pointer text-left text-[13px] text-brand hover:underline" @click="goConfig(record.configId)">
              {{ record.configName }}
            </button>
          </template>
        </template>

        <template #emptyText>
          <div class="py-10 text-center text-[12.5px] text-ink-400">暂无编排配置</div>
        </template>
      </Table>
    </div>
  </div>
</template>
