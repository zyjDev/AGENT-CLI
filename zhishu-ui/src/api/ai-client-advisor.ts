/** 顾问管理（/api/v1/admin/ai-client-advisor） */
import { http } from './http'
import { ADMIN_BASE, CRUD } from './endpoints'

export interface AiClientAdvisorItem {
  id: number
  advisorId: string
  advisorName: string
  advisorType: string
  orderNum?: number
  extParam?: string
  status: number
  createTime?: string
  updateTime?: string
}

/** 顾问类型枚举（与旧实现保持一致） */
export const ADVISOR_TYPE_OPTIONS = [
  { label: 'ChatMemory（记忆）', value: 'ChatMemory' },
  { label: 'RagAnswer（知识库）', value: 'RagAnswer' },
  { label: 'SimpleLoggerAdvisor（简单日志）', value: 'SimpleLoggerAdvisor' },
]

const BASE = ADMIN_BASE.AI_CLIENT_ADVISOR

export const AiClientAdvisorApi = {
  queryList: (payload: Record<string, unknown>) => http.post<AiClientAdvisorItem[]>(CRUD.queryList(BASE), payload),
  create: (payload: Record<string, unknown>) => http.post<boolean>(CRUD.create(BASE), payload),
  updateById: (payload: Record<string, unknown>) => http.put<boolean>(CRUD.updateById(BASE), payload),
  deleteById: (id: number | string) => http.delete<boolean>(CRUD.deleteById(BASE, id)),
  queryAll: () => http.get<AiClientAdvisorItem[]>(CRUD.queryAll(BASE)),
  queryEnabled: () => http.get<AiClientAdvisorItem[]>(CRUD.queryEnabled(BASE)),
}
