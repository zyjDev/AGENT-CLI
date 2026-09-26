/**
 * 接口端点集中收敛。
 *
 * ⚠️ 路径逐字对齐后端 @RequestMapping，迁移期间不要「顺手规范化」大小写或层级：
 * - AiAgentController                          → /api/v1/agent/**
 * - http/admin/*Controller                     → /api/v1/admin/**
 * - AiRagUpdateController                      → /api/v1/rag/**
 * - AdminWebConfig 放行 /api/v1/admin/admin-user/login 与 /validate-login
 *
 * 后端风格：create=POST、update-by-id=PUT、delete-by-id/{id}=DELETE、query-list=POST（内存分页）。
 * 例外：/v1/agent/armory_agent 与 /v1/agent/armory_api 使用下划线命名。
 *
 * 注意：所有 query-list 统一返回裸数组，响应里**没有 total 字段**，
 * 因此列表页的「总数」只能取当前页条数，不能伪造分页总数。
 */

/** 请求前缀：开发环境为 /api（走 Vite 代理），由 VITE_API_BASE 控制 */
export const API_PREFIX = import.meta.env.VITE_API_BASE || '/api'

/** 各模块基址（对应后端类级 @RequestMapping） */
export const ADMIN_BASE = {
  ADMIN_USER: '/v1/admin/admin-user',
  AI_CLIENT: '/v1/admin/ai-client',
  AI_CLIENT_ADVISOR: '/v1/admin/ai-client-advisor',
  AI_CLIENT_API: '/v1/admin/ai-client-api',
  AI_CLIENT_MODEL: '/v1/admin/ai-client-model',
  AI_CLIENT_RAG_ORDER: '/v1/admin/ai-client-rag-order',
  AI_CLIENT_SYSTEM_PROMPT: '/v1/admin/ai-client-system-prompt',
  AI_CLIENT_TOOL_MCP: '/v1/admin/ai-client-tool-mcp',
  AI_AGENT_DRAW: '/v1/admin/ai-agent-draw',
  DATA_STATISTICS: '/v1/admin/data/statistics',
  RAG: '/v1/rag',
  AGENT: '/v1/agent',
} as const

/**
 * 管理端通用 CRUD 子路径构造器。
 * 各模块路径规则完全一致，用构造器收敛可以避免逐条手抄出拼写错误。
 */
export const CRUD = {
  create: (base: string): string => `${base}/create`,
  updateById: (base: string): string => `${base}/update-by-id`,
  queryList: (base: string): string => `${base}/query-list`,
  queryAll: (base: string): string => `${base}/query-all`,
  queryEnabled: (base: string): string => `${base}/query-enabled`,
  deleteById: (base: string, id: number | string): string =>
    `${base}/delete-by-id/${encodeURIComponent(String(id))}`,
  queryById: (base: string, id: number | string): string =>
    `${base}/query-by-id/${encodeURIComponent(String(id))}`,
} as const

export const ENDPOINTS = {
  /** 管理员用户：全局唯一登录入口 */
  ADMIN_USER: {
    LOGIN: `${ADMIN_BASE.ADMIN_USER}/login`,
    VALIDATE_LOGIN: `${ADMIN_BASE.ADMIN_USER}/validate-login`,
    /** 自助注册：注册成功直接返回 token（无需再登录） */
    REGISTER: `${ADMIN_BASE.ADMIN_USER}/register`,
  },

  /** 智能体编排配置（画布） */
  AI_AGENT_DRAW: {
    QUERY_LIST: `${ADMIN_BASE.AI_AGENT_DRAW}/query-list`,
    SAVE_CONFIG: `${ADMIN_BASE.AI_AGENT_DRAW}/save-config`,
    GET_CONFIG: (configId: string): string =>
      `${ADMIN_BASE.AI_AGENT_DRAW}/get-config/${encodeURIComponent(configId)}`,
    DELETE_CONFIG: (configId: string): string =>
      `${ADMIN_BASE.AI_AGENT_DRAW}/delete-config/${encodeURIComponent(configId)}`,
  },

  /** 知识库文件上传（multipart：name / tag / files） */
  RAG_UPLOAD: `${ADMIN_BASE.AI_CLIENT_RAG_ORDER}/file/upload`,

  /** 数据总览统计 */
  DATA_STATISTICS: `${ADMIN_BASE.DATA_STATISTICS}/get-data-statistics`,

  /** 用户端：Auto Agent 对话与装配 */
  AGENT: {
    AUTO_AGENT: `${ADMIN_BASE.AGENT}/auto_agent`,
    ARMORY_AGENT: `${ADMIN_BASE.AGENT}/armory_agent`,
    ARMORY_API: `${ADMIN_BASE.AGENT}/armory_api`,
    QUERY_AVAILABLE_AGENTS: `${ADMIN_BASE.AGENT}/query_available_agents`,
  },
} as const

/** 拼出可直接用于原生 fetch 的完整地址（SSE 走 fetch，不经过 axios） */
export const buildUrl = (path: string): string => `${API_PREFIX}${path}`
