<script setup lang="ts">
/**
 * 智能体列表：编排配置的查询 / 装配 / 删除。
 * 只读列表（配置本身在编排画布中创建与保存），所以 descriptor.readonly = true，
 * 仅保留「装配」行操作与删除。
 */
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import CrudPage from '@/components/admin/CrudPage.vue'
import type { CrudDescriptor } from '@/components/admin/types'
import { AgentApi } from '@/api/agent'
import { AiAgentDrawApi, type AiAgentDrawConfigItem } from '@/api/ai-agent-draw'

const router = useRouter()

const descriptor: CrudDescriptor<AiAgentDrawConfigItem> = {
  title: '编排配置',
  rowKey: 'configId',
  queryFields: [
    { name: 'configName', label: '配置名称', type: 'input', placeholder: '支持模糊匹配', width: 210 },
    { name: 'agentId', label: '智能体ID', type: 'input', placeholder: '精确匹配', width: 170 },
  ],
  columns: [
    { title: '配置ID', dataIndex: 'configId', width: 170, render: 'mono' },
    { title: '配置名称', dataIndex: 'configName', width: 200 },
    { title: '描述', dataIndex: 'description', render: 'truncate' },
    { title: '智能体ID', dataIndex: 'agentId', width: 110, render: 'mono' },
    { title: '版本', dataIndex: 'version', width: 80, render: 'mono' },
    { title: '状态', dataIndex: 'status', width: 90, render: 'status' },
    { title: '更新时间', dataIndex: 'updateTime', width: 160, render: 'time' },
  ],
  // 只读列表：descriptor 不提供 create / update，因此只渲染「装配」等行操作与删除；
  // 配置本身由编排画布创建与保存
  formFields: [],
  api: {
    query: (payload) => AiAgentDrawApi.queryList(payload),
    remove: (record) => AiAgentDrawApi.deleteConfig(record.configId),
  },
  rowActions: [
    {
      label: '查看',
      run: (record) => {
        void router.push({ path: '/admin/agent-config', query: { configId: record.configId, mode: 'view' } })
      },
    },
    {
      label: '修改',
      run: (record) => {
        void router.push({ path: '/admin/agent-config', query: { configId: record.configId } })
      },
    },
    {
      label: '装配',
      run: async (record) => {
        if (!record.agentId) {
          message.warning('该配置未关联智能体ID，无法装配')
          return
        }
        try {
          await AgentApi.armoryAgent(record.agentId)
          message.success(`智能体「${record.configName}」装配成功`)
        } catch (error) {
          // 失败原因已由请求层提示
          console.error('[agent-list] 装配失败', error)
        }
      },
    },
  ],
  notice: '「装配」会把该配置关联到运行中的智能体（POST /v1/agent/armory_agent）；修改流程后需重新装配才会生效。',
}
</script>

<template>
  <CrudPage :descriptor="descriptor" />
</template>
