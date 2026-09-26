<script setup lang="ts">
/**
 * RAG 知识库配置：上传面板 + 知识库列表（仅删除）。
 * 知识库本身由文件上传创建，因此列表不做新增/编辑。
 */
import { ref } from 'vue'
import CrudPage from '@/components/admin/CrudPage.vue'
import type { CrudDescriptor } from '@/components/admin/types'
import { AiClientRagOrderApi, type AiClientRagOrderItem } from '@/api/ai-client-rag-order'
import UploadPanel from './UploadPanel.vue'

const STATUS_OPTIONS = [
  { label: '启用', value: 1 },
  { label: '停用', value: 0 },
]

const crudRef = ref<InstanceType<typeof CrudPage>>()

function reloadList(): void {
  crudRef.value?.reload()
}

const descriptor: CrudDescriptor<AiClientRagOrderItem> = {
  title: '知识库',
  rowKey: 'id',
  queryFields: [
    { name: 'ragId', label: '知识库ID', type: 'input', placeholder: '精确匹配', width: 170 },
    { name: 'ragName', label: '知识库名称', type: 'input', placeholder: '支持模糊匹配', width: 210 },
    { name: 'knowledgeTag', label: '知识标签', type: 'input', placeholder: '精确匹配', width: 180 },
    { name: 'status', label: '状态', type: 'select', options: STATUS_OPTIONS, width: 130 },
  ],
  columns: [
    { title: 'ID', dataIndex: 'id', width: 80, render: 'mono' },
    { title: '知识库ID', dataIndex: 'ragId', width: 150, render: 'mono' },
    { title: '知识库名称', dataIndex: 'ragName', width: 210 },
    { title: '知识标签', dataIndex: 'knowledgeTag', width: 160, render: 'mono' },
    { title: '版本', dataIndex: 'version', width: 80, render: 'mono' },
    { title: '状态', dataIndex: 'status', width: 90, render: 'status' },
    { title: '创建时间', dataIndex: 'createTime', width: 160, render: 'time' },
    { title: '更新时间', dataIndex: 'updateTime', width: 160, render: 'time' },
  ],
  formFields: [],
  api: {
    query: (payload) => AiClientRagOrderApi.queryList(payload),
    remove: (record) => AiClientRagOrderApi.deleteById(record.id),
  },
  notice: '删除知识库会同时移除其文档索引，正在引用该「知识标签」的编排节点将召回失败。',
}
</script>

<template>
  <div class="space-y-4">
    <UploadPanel @uploaded="reloadList" />
    <CrudPage ref="crudRef" :descriptor="descriptor" :allow-edit="false" />
  </div>
</template>
