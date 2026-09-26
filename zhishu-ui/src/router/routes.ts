import type { RouteRecordRaw } from 'vue-router'

/**
 * 路由表：一层接入、两端视图。
 * - /login  全局唯一登录入口
 * - /chat   用户端（登录后默认落地）
 * - /admin  管理端（由用户端导航进入，复用同一登录态，无需二次输入密码）
 */
export const routes: RouteRecordRaw[] = [
  { path: '/', redirect: '/chat' },

  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/sys/login/Login.vue'),
    meta: { title: '登录', public: true },
  },

  {
    path: '/chat',
    component: () => import('@/layouts/user/UserLayout.vue'),
    children: [
      {
        path: '',
        name: 'Chat',
        component: () => import('@/views/user/chat/index.vue'),
        meta: { title: '智能对话' },
      },
    ],
  },

  {
    path: '/admin',
    component: () => import('@/layouts/admin/AdminLayout.vue'),
    redirect: '/admin/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'AdminDashboard',
        component: () => import('@/views/admin/dashboard/index.vue'),
        meta: { title: '数据总览' },
      },
      {
        path: 'agent-list',
        name: 'AdminAgentList',
        component: () => import('@/views/admin/agent-list/index.vue'),
        meta: { title: '智能体列表' },
      },
      {
        path: 'agent-config',
        name: 'AdminAgentConfig',
        component: () => import('@/views/admin/agent-config/index.vue'),
        meta: { title: '智能体编排' },
      },
      {
        path: 'client-management',
        name: 'AdminClientManagement',
        component: () => import('@/views/admin/client-management/index.vue'),
        meta: { title: '客户端管理' },
      },
      {
        path: 'ai-client-api-management',
        name: 'AdminAiClientApiManagement',
        component: () => import('@/views/admin/ai-client-api-management/index.vue'),
        meta: { title: '客户端 API 管理' },
      },
      {
        path: 'advisor-management',
        name: 'AdminAdvisorManagement',
        component: () => import('@/views/admin/advisor-management/index.vue'),
        meta: { title: '顾问管理' },
      },
      {
        path: 'rag-order-management',
        name: 'AdminRagOrderManagement',
        component: () => import('@/views/admin/rag-order-management/index.vue'),
        meta: { title: 'RAG 知识库配置' },
      },
      {
        path: 'client-model-management',
        name: 'AdminClientModelManagement',
        component: () => import('@/views/admin/client-model-management/index.vue'),
        meta: { title: '模型管理' },
      },
      {
        path: 'client-system-prompt-management',
        name: 'AdminClientSystemPromptManagement',
        component: () => import('@/views/admin/client-system-prompt-management/index.vue'),
        meta: { title: '系统提示词' },
      },
      {
        path: 'client-tool-mcp-management',
        name: 'AdminClientToolMcpManagement',
        component: () => import('@/views/admin/client-tool-mcp-management/index.vue'),
        meta: { title: 'MCP 工具管理' },
      },
    ],
  },

  { path: '/:pathMatch(.*)*', redirect: '/chat' },
]
