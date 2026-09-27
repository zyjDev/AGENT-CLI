<script setup lang="ts">
/**
 * 客户端 API 管理：模型供应商的接入地址与密钥。
 * 注意：本模块 update-by-id 后端为 PUT，已在 api 层纠正（旧实现误用 POST）。
 */
import CrudPage from '@/components/admin/CrudPage.vue'
import type { CrudDescriptor } from '@/components/admin/types'
import { AiClientApiApi, type AiClientApiItem } from '@/api/ai-client-api'

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
    { title: 'ID', dataIndex: 'id', width: 80, render: 'mono' },
    { title: 'API ID', dataIndex: 'apiId', width: 120, render: 'mono' },
    { title: '基础URL', dataIndex: 'baseUrl', width: 260, render: 'truncate' },
    { title: '对话路径', dataIndex: 'completionsPath', width: 180, render: 'mono' },
    { title: '嵌入路径', dataIndex: 'embeddingsPath', width: 170, render: 'mono' },
    { title: '状态', dataIndex: 'status', width: 90, render: 'status' },
    { title: '创建时间', dataIndex: 'createTime', width: 160, render: 'time' },
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
    { name: 'embeddingsPath', label: '嵌入路径', type: 'input', required: true, placeholder: 'v1/embeddings' },
    { name: 'apiKey', label: 'API 密钥', type: 'password', required: true, full: true, placeholder: 'sk-...' },
    { name: 'status', label: '状态', type: 'select', required: true, options: STATUS_OPTIONS },
  ],
  api: {
    query: (payload) => AiClientApiApi.queryList(payload),
    create: (payload) => AiClientApiApi.create(payload),
    update: (payload) => AiClientApiApi.updateById(payload),
    remove: (record) => AiClientApiApi.deleteById(record.id),
  },
  notice: '接口按 PUT 提交更新；密钥以明文返回，请勿在公共环境截图或分享。',
}
</script>

<template>
  <div class="space-y-3">
    <!--
      面向普通用户的配置引导。
      为什么放在这一页：普通用户"用平台默认智能体"不需要任何配置，
      但"自己搭智能体"必须自带模型 Key，而 Key 就是在这里填的 —— 所以在入口处把路说清，
      别等他保存编排时才被拦下来、还不知道去哪儿配。
    -->
    <div class="rounded-xl border border-[#E7EAF6] bg-[#F7F8FE] px-4 py-3 text-[12.5px] leading-6 text-ink-600">
      <p class="font-medium text-ink-900">要用自己的模型跑智能体？在这里配</p>
      <p class="mt-1 text-ink-400">
        平台默认智能体（管理员提供的）拿来即用，无需配置；但你<strong>自己搭建</strong>的智能体必须使用你自己的模型，
        否则保存或运行时会被拦下。步骤：
      </p>
      <ol class="mt-2 list-decimal space-y-1 pl-5">
        <li>本页「新增」一条 API：<strong>基础URL</strong> 填你供应商的地址（如 <span class="font-mono">https://api.deepseek.com</span>），<strong>API 密钥</strong> 填你的 Key；</li>
        <li>到「模型管理」新增模型：<strong>API 选上一步那条</strong>，模型名填供应商的模型名（如 <span class="font-mono">deepseek-chat</span>）；</li>
        <li>到「客户端管理」新增客户端，把上面这个模型挂上去；</li>
        <li>回到「智能体编排」，把客户端 / 模型节点换成你自己这几个，保存就能用。</li>
      </ol>
      <p class="mt-1 text-ink-400">你自己的资源只有你自己能看见和修改；平台默认资源仍然可以直接用，互不影响。</p>
    </div>

    <CrudPage :descriptor="descriptor" />
  </div>
</template>
