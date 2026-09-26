<script setup lang="ts">
/** 系统提示词管理：编排画布中「提示词节点」引用的角色设定 */
import CrudPage from '@/components/admin/CrudPage.vue'
import type { CrudDescriptor } from '@/components/admin/types'
import { AiClientSystemPromptApi, type AiClientSystemPromptItem } from '@/api/ai-client-system-prompt'

const STATUS_OPTIONS = [
  { label: '启用', value: 1 },
  { label: '停用', value: 0 },
]

const descriptor: CrudDescriptor<AiClientSystemPromptItem> = {
  title: '系统提示词',
  description: '智能体的角色设定与输出约束',
  rowKey: 'id',
  // 提示词ID 由使用者填写，不做随机生成
  queryFields: [
    { name: 'promptName', label: '提示词名称', type: 'input', placeholder: '支持模糊匹配', width: 210 },
    { name: 'status', label: '状态', type: 'select', options: STATUS_OPTIONS, width: 130 },
  ],
  columns: [
    { title: 'ID', dataIndex: 'id', width: 80, render: 'mono' },
    { title: '提示词ID', dataIndex: 'promptId', width: 120, render: 'mono' },
    { title: '提示词名称', dataIndex: 'promptName', width: 190 },
    { title: '提示词内容', dataIndex: 'promptContent', render: 'truncate' },
    { title: '状态', dataIndex: 'status', width: 90, render: 'status' },
    { title: '创建时间', dataIndex: 'createTime', width: 160, render: 'time' },
    { title: '更新时间', dataIndex: 'updateTime', width: 160, render: 'time' },
  ],
  formFields: [
    { name: 'promptId', label: '提示词ID', type: 'input', required: true, disabledOnEdit: true, placeholder: '自动生成，保存后不可修改' },
    { name: 'promptName', label: '提示词名称', type: 'input', required: true, placeholder: '如：数据分析助手' },
    {
      name: 'promptContent',
      label: '提示词内容',
      type: 'textarea',
      required: true,
      full: true,
      rows: 6,
      placeholder: '你是一个专业的数据分析助手，需要…',
    },
    { name: 'description', label: '描述', type: 'input', full: true, placeholder: '简要说明使用场景' },
    { name: 'status', label: '状态', type: 'select', required: true, options: STATUS_OPTIONS },
  ],
  api: {
    query: (payload) => AiClientSystemPromptApi.queryList(payload),
    create: (payload) => AiClientSystemPromptApi.create(payload),
    update: (payload) => AiClientSystemPromptApi.updateById(payload),
    remove: (record) => AiClientSystemPromptApi.deleteById(record.id),
  },
  notice: '提示词正文会作为 System 消息注入对话链路，修改后需重新装配对应的智能体才会生效。',
}
</script>

<template>
  <CrudPage :descriptor="descriptor" />
</template>
