/** 模型管理（/api/v1/admin/ai-client-model） */
import { http } from './http'
import { ADMIN_BASE, CRUD } from './endpoints'

export interface AiClientModelItem {
  id: number
  modelId: string
  apiId?: string
  modelName: string
  modelType?: string
  modelUsage?: string
  status: number
  createTime?: string
  updateTime?: string
}

const BASE = ADMIN_BASE.AI_CLIENT_MODEL

export const AiClientModelApi = {
  queryList: (payload: Record<string, unknown>) => http.post<AiClientModelItem[]>(CRUD.queryList(BASE), payload),
  create: (payload: Record<string, unknown>) => http.post<boolean>(CRUD.create(BASE), payload),
  updateById: (payload: Record<string, unknown>) => http.put<boolean>(CRUD.updateById(BASE), payload),
  deleteById: (id: number | string) => http.delete<boolean>(CRUD.deleteById(BASE, id)),
  queryAll: () => http.get<AiClientModelItem[]>(CRUD.queryAll(BASE)),
  queryEnabled: () => http.get<AiClientModelItem[]>(CRUD.queryEnabled(BASE)),
}
