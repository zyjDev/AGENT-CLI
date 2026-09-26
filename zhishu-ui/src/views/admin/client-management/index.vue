<script setup lang="ts">
/** 客户端管理：编排画布中「客户端节点」引用的资源 */
import CrudPage from '@/components/admin/CrudPage.vue'
import type { CrudDescriptor } from '@/components/admin/types'
import { AiClientApi, type AiClientItem } from '@/api/ai-client'

const STATUS_OPTIONS = [
  { label: '启用', value: 1 },
  { label: '停用', value: 0 },
]

const descriptor: CrudDescriptor<AiClientItem> = {
  title: '客户端',
  description: '对话客户端（模型供应商适配），供编排画布的客户端节点引用',
  rowKey: 'id',
  generateIdField: 'clientId',
  queryFields: [
    { name: 'clientId', label: '客户端ID', type: 'input', placeholder: '精确匹配', width: 170 },
    { name: 'clientName', label: '客户端名称', type: 'input', placeholder: '支持模糊匹配', width: 210 },
    { name: 'status', label: '状态', type: 'select', options: STATUS_OPTIONS, width: 130 },
  ],
  columns: [
    { title: 'ID', dataIndex: 'id', width: 80, render: 'mono' },
    { title: '客户端ID', dataIndex: 'clientId', width: 120, render: 'mono' },
    { title: '客户端名称', dataIndex: 'clientName', width: 200 },
    { title: '描述', dataIndex: 'description', render: 'truncate' },
    { title: '状态', dataIndex: 'status', width: 90, render: 'status' },
    { title: '创建时间', dataIndex: 'createTime', width: 160, render: 'time' },
    { title: '更新时间', dataIndex: 'updateTime', width: 160, render: 'time' },
  ],
  formFields: [
    {
      name: 'clientId',
      label: '客户端ID',
      type: 'input',
      disabled: true,
      extra: '由前端自动生成，保存后不可修改',
    },
    { name: 'clientName', label: '客户端名称', type: 'input', required: true, placeholder: '如：DeepSeek-V3 客户端' },
    { name: 'status', label: '状态', type: 'select', required: true, options: STATUS_OPTIONS },
    {
      name: 'description',
      label: '描述',
      type: 'textarea',
      full: true,
      rows: 3,
      placeholder: '说明该客户端的用途与接入信息',
    },
  ],
  api: {
    query: (payload) => AiClientApi.queryList(payload),
    create: (payload) => AiClientApi.create(payload),
    update: (payload) => AiClientApi.updateById(payload),
    remove: (record) => AiClientApi.deleteById(record.id),
  },
  notice: '新增时客户端ID由前端生成 8 位随机数字；保存后需在「智能体编排」中引用并装配才会生效。',
}
</script>

<template>
  <CrudPage :descriptor="descriptor" />
</template>
