<script setup lang="ts">
/** 模型管理：编排画布中「模型节点」引用的大模型配置 */
import CrudPage from '@/components/admin/CrudPage.vue'
import type { CrudDescriptor } from '@/components/admin/types'
import { AiClientModelApi, type AiClientModelItem } from '@/api/ai-client-model'

const STATUS_OPTIONS = [
  { label: '启用', value: 1 },
  { label: '停用', value: 0 },
]

const descriptor: CrudDescriptor<AiClientModelItem> = {
  title: '模型',
  description: '具体大模型（含类型、归属 API 与用途说明）',
  rowKey: 'id',
  // 模型ID 由使用者按实际模型名填写（如 deepseek-chat），不做随机生成
  queryFields: [
    { name: 'modelId', label: '模型ID/名称', type: 'input', placeholder: '精确匹配', width: 180 },
    { name: 'modelType', label: '模型类型', type: 'input', placeholder: '如：openai / deepseek', width: 190 },
    { name: 'status', label: '状态', type: 'select', options: STATUS_OPTIONS, width: 130 },
  ],
  columns: [
    { title: 'ID', dataIndex: 'id', width: 80, render: 'mono' },
    { title: '模型ID', dataIndex: 'modelId', width: 130, render: 'mono' },
    { title: '模型名称', dataIndex: 'modelName', width: 190 },
    { title: '模型类型', dataIndex: 'modelType', width: 140, render: 'mono' },
    { title: 'API ID', dataIndex: 'apiId', width: 110, render: 'mono' },
    { title: '模型用途', dataIndex: 'modelUsage', render: 'truncate' },
    { title: '状态', dataIndex: 'status', width: 90, render: 'status' },
    { title: '创建时间', dataIndex: 'createTime', width: 160, render: 'time' },
  ],
  formFields: [
    { name: 'modelId', label: '模型ID', type: 'input', required: true, placeholder: '如：deepseek-chat' },
    { name: 'modelName', label: '模型名称', type: 'input', required: true, placeholder: '如：DeepSeek-V3' },
    { name: 'modelType', label: '模型类型', type: 'input', placeholder: '如：openai' },
    { name: 'apiId', label: 'API ID', type: 'input', placeholder: '关联的客户端 API ID' },
    { name: 'modelUsage', label: '模型用途', type: 'input', full: true, placeholder: '如：长文本推理 / 工具调用' },
    { name: 'status', label: '状态', type: 'select', required: true, options: STATUS_OPTIONS },
  ],
  api: {
    query: (payload) => AiClientModelApi.queryList(payload),
    create: (payload) => AiClientModelApi.create(payload),
    update: (payload) => AiClientModelApi.updateById(payload),
    remove: (record) => AiClientModelApi.deleteById(record.id),
  },
  notice: '模型需归属到一个已启用的客户端 API；装配时按模型ID回查客户端配置。',
}
</script>

<template>
  <CrudPage :descriptor="descriptor" />
</template>
