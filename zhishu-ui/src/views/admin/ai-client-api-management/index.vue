<script setup lang="ts">
/**
 * 客户端 API 管理：模型供应商的接入地址与密钥。
 * 注意：本模块 update-by-id 后端为 PUT，已在 api 层纠正（旧实现误用 POST）。
 *
 * 本页除了 CRUD，还承担「把自己的密钥绑定到智能体」：
 * 平台默认密钥只给管理员用，普通用户要用平台默认智能体，必须配好自己的 base_url + api_key 并绑定它；
 * 绑定后运行时优先走他自己的链路（后端 AiClientApiBindingAdminController 负责自动接线与装配）。
 */
import { ref } from 'vue'
import { Button, Empty, Modal, Select, Spin, message } from 'ant-design-vue'
import CrudPage from '@/components/admin/CrudPage.vue'
import type { CrudDescriptor } from '@/components/admin/types'
import { AiClientApiApi, type AiClientApiBoundAgent, type AiClientApiItem } from '@/api/ai-client-api'
import { AgentApi } from '@/api/agent'

const STATUS_OPTIONS = [
  { label: '启用', value: 1 },
  { label: '停用', value: 0 },
]

const descriptor: CrudDescriptor<AiClientApiItem> = {
  title: '客户端 API',
  description: '模型供应商的 baseUrl、对话/嵌入路径与密钥',
  rowKey: 'id',
  generateIdField: 'apiId',
  queryFields: [
    { name: 'apiId', label: 'API ID', type: 'input', placeholder: '精确匹配', width: 180 },
    { name: 'status', label: '状态', type: 'select', options: STATUS_OPTIONS, width: 130 },
  ],
  columns: [
    // 不展示数据库主键：19 位雪花 ID 对用户没有任何意义，只会把表格挤到折行
    // 宽度按 19 位数字的实际宽度给足，配合 CrudPage 的单行省略，保证不折行
    { title: 'API ID', dataIndex: 'apiId', width: 200, render: 'mono' },
    { title: '基础URL', dataIndex: 'baseUrl', width: 260, render: 'truncate' },
    { title: '对话路径', dataIndex: 'completionsPath', width: 170, render: 'mono' },
    { title: '嵌入路径', dataIndex: 'embeddingsPath', width: 160, render: 'mono' },
    { title: '状态', dataIndex: 'status', width: 90, render: 'status' },
    { title: '创建时间', dataIndex: 'createTime', width: 150, render: 'time' },
  ],
  formFields: [
    {
      name: 'apiId',
      label: 'API ID',
      type: 'input',
      disabled: true,
      full: true,
      extra: '由前端自动生成，保存后不可修改',
    },
    {
      name: 'baseUrl',
      label: '基础URL',
      type: 'input',
      required: true,
      full: true,
      placeholder: '如：https://api.deepseek.com（需以 http:// 或 https:// 开头）',
    },
    { name: 'completionsPath', label: '对话路径', type: 'input', required: true, placeholder: 'v1/chat/completions' },
    {
      name: 'embeddingsPath',
      label: '嵌入路径',
      type: 'input',
      // 选填：只有知识库/向量化才会用到，留空后端补 v1/embeddings
      required: false,
      placeholder: '留空默认 v1/embeddings',
      extra: '仅知识库 / 向量化（embedding）需要；只做对话可以留空。',
    },
    { name: 'apiKey', label: 'API 密钥', type: 'password', required: true, full: true, placeholder: 'sk-...' },
    { name: 'status', label: '状态', type: 'select', required: true, options: STATUS_OPTIONS },
  ],
  api: {
    query: (payload) => AiClientApiApi.queryList(payload),
    create: (payload) => AiClientApiApi.create(payload),
    update: (payload) => AiClientApiApi.updateById(payload),
    remove: (record) => AiClientApiApi.deleteById(record.id),
  },
  rowActions: [
    {
      label: '绑定智能体',
      run: (record) => openBind(record),
    },
  ],
  notice: '接口按 PUT 提交更新；密钥以明文返回，请勿在公共环境截图或分享。绑定智能体后，该智能体将改用你的密钥运行。',
}

/* ---------------- 绑定智能体 ---------------- */

const bindOpen = ref(false)
const bindSubmitting = ref(false)
const bindApiId = ref('')
const bindApiLabel = ref('')
const bindAgentId = ref<string | undefined>(undefined)
const agentOptions = ref<{ label: string; value: string }[]>([])
const agentLoading = ref(false)
const boundAgents = ref<AiClientApiBoundAgent[]>([])
const boundLoading = ref(false)

async function openBind(record: AiClientApiItem): Promise<void> {
  bindApiId.value = record.apiId
  bindApiLabel.value = record.baseUrl ? `${record.apiId}（${record.baseUrl}）` : record.apiId
  bindAgentId.value = undefined
  bindOpen.value = true
  await Promise.all([loadAgentOptions(), loadBoundAgents()])
}

/** 可选智能体 = 后端给的「我能用的」（平台默认 + 我自己搭的） */
async function loadAgentOptions(): Promise<void> {
  if (agentOptions.value.length) return
  agentLoading.value = true
  try {
    const agents = await AgentApi.queryAvailableAgents()
    // 只显示名称（id 是内部标识，不展示）；名称缺失时才退回 id，避免出现空白项
    agentOptions.value = (agents ?? []).map((item) => ({
      label: item.agentName || item.agentId,
      value: item.agentId,
    }))
  } catch (error) {
    console.error('[ai-client-api] 获取可用智能体失败', error)
  } finally {
    agentLoading.value = false
  }
}

async function loadBoundAgents(): Promise<void> {
  boundLoading.value = true
  try {
    boundAgents.value = (await AiClientApiApi.queryBoundAgents(bindApiId.value)) ?? []
  } catch (error) {
    console.error('[ai-client-api] 查询已绑定智能体失败', error)
  } finally {
    boundLoading.value = false
  }
}

async function submitBind(): Promise<void> {
  if (!bindAgentId.value) {
    message.warning('请先选择要绑定的智能体')
    return
  }
  bindSubmitting.value = true
  try {
    await AiClientApiApi.bindAgent(bindApiId.value, bindAgentId.value)
    message.success('绑定成功：该智能体将使用你的模型密钥')
    bindAgentId.value = undefined
    await loadBoundAgents()
  } catch (error) {
    // 失败原因已由请求层提示
    console.error('[ai-client-api] 绑定失败', error)
  } finally {
    bindSubmitting.value = false
  }
}

function unbind(agentId: string): void {
  Modal.confirm({
    title: '确认解绑？',
    content: '解绑后该智能体不再可用（平台默认密钥只给管理员使用），需要重新绑定才能继续使用。',
    okText: '确认解绑',
    okType: 'danger',
    cancelText: '取消',
    onOk: async () => {
      await AiClientApiApi.unbindAgent(bindApiId.value, agentId)
      message.success('已解绑')
      await loadBoundAgents()
    },
  })
}
</script>

<template>
  <div class="space-y-3">
    <!--
      面向普通用户的配置引导。
      为什么放在这一页：普通用户"用平台默认智能体"必须配自己的 Key 并绑定（平台 Key 只给管理员用），
      而 Key 就是在这里填的 —— 所以在入口处把路说清，别等他保存编排或对话时才被拦下来、还不知道去哪儿配。
    -->
    <div class="rounded-xl border border-[#E7EAF6] bg-[#F7F8FE] px-4 py-3 text-[12.5px] leading-6 text-ink-600">
      <p class="font-medium text-ink-900">要用自己的模型跑智能体？在这里配</p>
      <p class="mt-1 text-ink-400">
        平台默认密钥只给管理员使用。普通用户要用智能体（含平台默认的那些），需配好自己的
        <strong>base_url + API 密钥</strong> 并绑定它：
        <strong>①</strong> 本页「新增」填基础URL与API密钥 →
        <strong>②</strong> 点该行「绑定智能体」选择目标 →
        <strong>③</strong> 即可用它对话（模型与客户端由后端自动建好）。
      </p>
      <p class="mt-1 text-ink-400">
        绑定后运行时优先走你的密钥；同一智能体重复绑定会覆盖上一次，解绑后该智能体对你不再可用。
      </p>
    </div>

    <CrudPage :descriptor="descriptor" />

    <Modal
      v-model:open="bindOpen"
      :title="`绑定智能体：${bindApiLabel}`"
      :confirm-loading="bindSubmitting"
      ok-text="绑定"
      cancel-text="关闭"
      @ok="submitBind"
    >
      <p class="text-[12.5px] leading-6 text-ink-600">
        选择要用这条密钥的智能体。绑定后运行时优先走你自己的链路（不会再用平台默认密钥）；
        同一智能体重复绑定会覆盖上一次。
      </p>
      <Select
        v-model:value="bindAgentId"
        class="mt-3 w-full"
        show-search
        option-filter-prop="label"
        placeholder="选择智能体（可搜索）"
        :options="agentOptions"
        :loading="agentLoading"
      />

      <div class="mt-4">
        <p class="text-[12.5px] font-medium text-ink-900">已绑定我的密钥的智能体</p>
        <Spin :spinning="boundLoading">
          <Empty
            v-if="!boundAgents.length"
            :image="Empty.PRESENTED_IMAGE_SIMPLE"
            description="还没有绑定任何智能体"
          />
          <ul v-else class="mt-2 space-y-1.5">
            <li
              v-for="item in boundAgents"
              :key="item.agentId"
              class="flex items-center justify-between rounded-lg border border-line px-3 py-2"
            >
              <span class="text-[12.5px] text-ink-900">
                {{ item.agentName }}
                <span
                  v-if="item.platformDefault"
                  class="ml-1.5 rounded bg-[#EEF2FF] px-1.5 py-0.5 text-[11px] text-brand"
                  >平台默认</span
                >
              </span>
              <button
                type="button"
                class="cursor-pointer text-[12.5px] text-err hover:underline"
                @click="unbind(item.agentId)"
              >
                解绑
              </button>
            </li>
          </ul>
        </Spin>
        <p class="mt-2 text-[12px] text-ink-400">解绑后该智能体对你不可用，需要重新绑定。</p>
      </div>
    </Modal>
  </div>
</template>
