<script setup lang="ts">
/** MCP 工具管理：编排画布中「MCP 工具节点」引用的外部工具服务 */
import CrudPage from '@/components/admin/CrudPage.vue'
import type { CrudDescriptor } from '@/components/admin/types'
import {
  AiClientToolMcpApi,
  DEFAULT_MCP_TIMEOUT_MS,
  TRANSPORT_TYPE_OPTIONS,
  type AiClientToolMcpItem,
} from '@/api/ai-client-tool-mcp'

const STATUS_OPTIONS = [
  { label: '启用', value: 1 },
  { label: '停用', value: 0 },
]

const descriptor: CrudDescriptor<AiClientToolMcpItem> = {
  title: 'MCP 工具',
  description: '以 stdio / sse / websocket 接入的外部工具服务',
  rowKey: 'id',
  generateIdField: 'mcpId',
  queryFields: [
    { name: 'mcpName', label: 'MCP 名称', type: 'input', placeholder: '支持模糊匹配', width: 200 },
    { name: 'transportType', label: '传输类型', type: 'select', options: TRANSPORT_TYPE_OPTIONS, width: 160 },
    { name: 'status', label: '状态', type: 'select', options: STATUS_OPTIONS, width: 130 },
  ],
  columns: [
    { title: 'ID', dataIndex: 'id', width: 80, render: 'mono' },
    { title: 'MCP ID', dataIndex: 'mcpId', width: 120, render: 'mono' },
    { title: 'MCP 名称', dataIndex: 'mcpName', width: 190 },
    { title: '传输类型', dataIndex: 'transportType', width: 120, render: 'mono' },
    { title: '传输配置', dataIndex: 'transportConfig', render: 'truncate' },
    { title: '超时(ms)', dataIndex: 'requestTimeout', width: 110, render: 'mono' },
    { title: '状态', dataIndex: 'status', width: 90, render: 'status' },
    { title: '创建时间', dataIndex: 'createTime', width: 160, render: 'time' },
  ],
  formFields: [
    {
      name: 'mcpId',
      label: 'MCP ID',
      type: 'input',
      disabled: true,
      extra: '由前端自动生成，保存后不可修改',
    },
    { name: 'mcpName', label: 'MCP 名称', type: 'input', required: true, placeholder: '如：知识库检索工具' },
    { name: 'transportType', label: '传输类型', type: 'select', required: true, options: TRANSPORT_TYPE_OPTIONS },
    {
      name: 'transportConfig',
      label: '传输配置',
      type: 'textarea',
      required: true,
      full: true,
      rows: 4,
      placeholder: '{"command": "node", "args": ["server.js"]}',
      extra: '必须是合法 JSON；stdio 填 command/args，sse 与 websocket 填 url',
    },
    {
      name: 'requestTimeout',
      label: '请求超时(ms)',
      type: 'number',
      min: 1,
      extra: `默认 ${DEFAULT_MCP_TIMEOUT_MS}，超时会触发自动重试`,
    },
    { name: 'status', label: '状态', type: 'select', required: true, options: STATUS_OPTIONS },
  ],
  api: {
    query: (payload) => AiClientToolMcpApi.queryList(payload),
    create: (payload) => AiClientToolMcpApi.create(payload),
    update: (payload) => AiClientToolMcpApi.updateById(payload),
    remove: (record) => AiClientToolMcpApi.deleteById(record.id),
  },
  notice: '传输配置为 JSON 字符串；装配时会按该配置拉起或连接工具服务，配置错误会直接导致对话链路报错。',
}
</script>

<template>
  <CrudPage :descriptor="descriptor" />
</template>
