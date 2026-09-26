/**
 * 客户端 API 管理（/api/v1/admin/ai-client-api）
 * ⚠️ 注意：本模块的 update-by-id 后端为 **PUT**（旧 React 实现误用了 POST，会 405）。
 */
import { http } from './http'
import { ADMIN_BASE, CRUD } from './endpoints'

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
  queryList: (payload: Record<string, unknown>) => http.post<AiClientApiItem[]>(CRUD.queryList(BASE), payload),
  create: (payload: Record<string, unknown>) => http.post<boolean>(CRUD.create(BASE), payload),
  updateById: (payload: Record<string, unknown>) => http.put<boolean>(CRUD.updateById(BASE), payload),
  deleteById: (id: number | string) => http.delete<boolean>(CRUD.deleteById(BASE, id)),
  queryAll: () => http.get<AiClientApiItem[]>(CRUD.queryAll(BASE)),
  queryEnabled: () => http.get<AiClientApiItem[]>(CRUD.queryEnabled(BASE)),
}
