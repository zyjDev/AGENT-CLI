/**
 * 客户端 API 管理（/api/v1/admin/ai-client-api）
 * ⚠️ 注意：本模块的 update-by-id 后端为 **PUT**（旧 React 实现误用了 POST，会 405）。
 */
import { http } from './http'
import { ADMIN_BASE, CRUD, ENDPOINTS } from './endpoints'
import type { PagePayload } from '@/types/api'

export interface AiClientApiItem {
  id: number
  apiId: string
  baseUrl: string
  apiKey?: string
  completionsPath?: string
  embeddingsPath?: string
  status: number
  createTime?: string
  updateTime?: string
}

const BASE = ADMIN_BASE.AI_CLIENT_API

export const AiClientApiApi = {
  queryList: (payload: Record<string, unknown>) =>
    http.post<AiClientApiItem[] | PagePayload<AiClientApiItem>>(CRUD.queryList(BASE), payload),
  create: (payload: Record<string, unknown>) => http.post<boolean>(CRUD.create(BASE), payload),
  updateById: (payload: Record<string, unknown>) => http.put<boolean>(CRUD.updateById(BASE), payload),
  deleteById: (id: number | string) => http.delete<boolean>(CRUD.deleteById(BASE, id)),
  queryAll: () => http.get<AiClientApiItem[]>(CRUD.queryAll(BASE)),
  queryEnabled: () => http.get<AiClientApiItem[]>(CRUD.queryEnabled(BASE)),

  /**
   * 把这条密钥绑定到指定智能体。
   * 后端会自动接线（用你的 base_url + api_key 建出属于你的模型 / 客户端 / 装配关系）并立即装配，
   * 你不需要懂「API → 模型 → 客户端 → 装配关系」四层。同一智能体重复绑定 = 覆盖。
   */
  bindAgent: (apiId: string, agentId: string) =>
    http.post<boolean>(ENDPOINTS.AI_CLIENT_API_BINDING.BIND_AGENT, { apiId, agentId }),

  /** 解绑：解绑后该智能体对自己不再可用（平台默认 Key 只给管理员用），需要重新绑定 */
  unbindAgent: (apiId: string, agentId: string) =>
    http.post<boolean>(ENDPOINTS.AI_CLIENT_API_BINDING.UNBIND_AGENT, { apiId, agentId }),

  /** 查询这条密钥当前绑定给了哪些智能体 */
  queryBoundAgents: (apiId: string) =>
    http.post<AiClientApiBoundAgent[]>(ENDPOINTS.AI_CLIENT_API_BINDING.BOUND_AGENTS, { apiId }),
}

/** 已绑定某条密钥的智能体 */
export interface AiClientApiBoundAgent {
  agentId: string
  agentName: string
  /** 平台默认智能体（管理员维护，只能绑定不能改） */
  platformDefault?: boolean
}
