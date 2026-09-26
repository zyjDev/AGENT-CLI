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
  <CrudPage :descriptor="descriptor" />
</template>
