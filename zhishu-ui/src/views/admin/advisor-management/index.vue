<script setup lang="ts">
/** 顾问管理：编排画布中「顾问节点」引用的拦截器（记忆 / 知识库 / 日志） */
import CrudPage from '@/components/admin/CrudPage.vue'
import type { CrudDescriptor } from '@/components/admin/types'
import { ADVISOR_TYPE_OPTIONS, AiClientAdvisorApi, type AiClientAdvisorItem } from '@/api/ai-client-advisor'

const STATUS_OPTIONS = [
  { label: '启用', value: 1 },
  { label: '停用', value: 0 },
]

const descriptor: CrudDescriptor<AiClientAdvisorItem> = {
  title: '顾问',
  description: '对话链路中的顾问角色（记忆、知识库召回、日志）',
  rowKey: 'id',
  generateIdField: 'advisorId',
  queryFields: [
    { name: 'advisorName', label: '顾问名称', type: 'input', placeholder: '支持模糊匹配', width: 190 },
    { name: 'advisorType', label: '顾问类型', type: 'select', options: ADVISOR_TYPE_OPTIONS, width: 210 },
    { name: 'status', label: '状态', type: 'select', options: STATUS_OPTIONS, width: 130 },
  ],
  columns: [
    { title: 'ID', dataIndex: 'id', width: 80, render: 'mono' },
    { title: '顾问ID', dataIndex: 'advisorId', width: 120, render: 'mono' },
    { title: '顾问名称', dataIndex: 'advisorName', width: 200 },
    { title: '顾问类型', dataIndex: 'advisorType', width: 190, render: 'mono' },
    { title: '排序', dataIndex: 'orderNum', width: 80 },
    { title: '状态', dataIndex: 'status', width: 90, render: 'status' },
    { title: '创建时间', dataIndex: 'createTime', width: 160, render: 'time' },
  ],
  formFields: [
    {
      name: 'advisorId',
      label: '顾问ID',
      type: 'input',
      disabled: true,
      extra: '由前端自动生成，保存后不可修改',
    },
    { name: 'advisorName', label: '顾问名称', type: 'input', required: true, placeholder: '如：知识库召回顾问' },
    { name: 'advisorType', label: '顾问类型', type: 'select', required: true, options: ADVISOR_TYPE_OPTIONS },
    { name: 'orderNum', label: '排序', type: 'number', min: 1, extra: '数字越小越先执行' },
    { name: 'status', label: '状态', type: 'select', required: true, options: STATUS_OPTIONS },
    {
      name: 'extParam',
      label: '扩展参数',
      type: 'textarea',
      full: true,
      rows: 3,
      placeholder: '{"topK": 4}',
      extra: 'JSON 字符串，留空时提交 {}',
    },
  ],
  api: {
    query: (payload) => AiClientAdvisorApi.queryList(payload),
    create: (payload) => AiClientAdvisorApi.create(payload),
    update: (payload) => AiClientAdvisorApi.updateById(payload),
    remove: (record) => AiClientAdvisorApi.deleteById(record.id),
  },
  notice: '顾问类型决定该角色在对话链路中的行为：ChatMemory 维护上下文，RagAnswer 负责知识库召回，SimpleLoggerAdvisor 仅做日志。',
}
</script>

<template>
  <CrudPage :descriptor="descriptor" />
</template>
