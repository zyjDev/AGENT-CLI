/** 客户端管理（/api/v1/admin/ai-client） */
import { http } from './http'
import { ADMIN_BASE, CRUD } from './endpoints'

export interface AiClientItem {
  id: number
  clientId: string
  clientName: string
  description?: string
  status: number
  createTime?: string
  updateTime?: string
}

const BASE = ADMIN_BASE.AI_CLIENT

export const AiClientApi = {
  queryList: (payload: Record<string, unknown>) => http.post<AiClientItem[]>(CRUD.queryList(BASE), payload),
  create: (payload: Record<string, unknown>) => http.post<boolean>(CRUD.create(BASE), payload),
  updateById: (payload: Record<string, unknown>) => http.put<boolean>(CRUD.updateById(BASE), payload),
  deleteById: (id: number | string) => http.delete<boolean>(CRUD.deleteById(BASE, id)),
  /** 编排画布的下拉数据源 */
  queryAll: () => http.get<AiClientItem[]>(CRUD.queryAll(BASE)),
  queryEnabled: () => http.get<AiClientItem[]>(CRUD.queryEnabled(BASE)),
}
