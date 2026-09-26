import type { Component } from 'vue'
import {
  Boxes,
  Cpu,
  Database,
  LayoutDashboard,
  ListTree,
  Plug,
  ShieldCheck,
  TextCursorInput,
  Wrench,
} from 'lucide-vue-next'

export interface AdminMenuItem {
  path: string
  title: string
  icon: Component
}

export interface AdminMenuGroup {
  label: string
  items: AdminMenuItem[]
}

/** 管理端导航结构：与原型 prototype/dashboard.html 的分组保持一致 */
export const ADMIN_MENU: AdminMenuGroup[] = [
  {
    label: '对话与编排',
    items: [
      { path: '/admin/dashboard', title: '数据总览', icon: LayoutDashboard },
      { path: '/admin/agent-list', title: '智能体列表', icon: ListTree },
      { path: '/admin/agent-config', title: '智能体编排', icon: Boxes },
    ],
  },
  {
    label: '资源管理',
    items: [
      { path: '/admin/client-management', title: '客户端管理', icon: Plug },
      { path: '/admin/ai-client-api-management', title: '客户端 API 管理', icon: TextCursorInput },
      { path: '/admin/advisor-management', title: '顾问管理', icon: ShieldCheck },
      { path: '/admin/client-model-management', title: '模型管理', icon: Cpu },
      { path: '/admin/client-system-prompt-management', title: '系统提示词', icon: TextCursorInput },
      { path: '/admin/client-tool-mcp-management', title: 'MCP 工具管理', icon: Wrench },
    ],
  },
  {
    label: '知识库',
    items: [
      { path: '/admin/rag-order-management', title: 'RAG 知识库配置', icon: Database },
    ],
  },
]

/** 面包屑用：路径 → 标题 */
export const ADMIN_TITLE_MAP: Record<string, string> = ADMIN_MENU.flatMap((group) => group.items).reduce(
  (acc, item) => {
    acc[item.path] = item.title
    return acc
  },
  {} as Record<string, string>,
)
