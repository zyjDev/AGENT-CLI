/** 系统提示词管理（/api/v1/admin/ai-client-system-prompt） */
import { http } from './http'
import { ADMIN_BASE, CRUD } from './endpoints'

export interface AiClientSystemPromptItem {
  id: number
  promptId: string
  promptName: string
  promptContent: string
  description?: string
  status: number
  createTime?: string
  updateTime?: string
}

const BASE = ADMIN_BASE.AI_CLIENT_SYSTEM_PROMPT

export const AiClientSystemPromptApi = {
  queryList: (payload: Record<string, unknown>) =>
    http.post<AiClientSystemPromptItem[]>(CRUD.queryList(BASE), payload),
  create: (payload: Record<string, unknown>) => http.post<boolean>(CRUD.create(BASE), payload),
  updateById: (payload: Record<string, unknown>) => http.put<boolean>(CRUD.updateById(BASE), payload),
  deleteById: (id: number | string) => http.delete<boolean>(CRUD.deleteById(BASE, id)),
  queryAll: () => http.get<AiClientSystemPromptItem[]>(CRUD.queryAll(BASE)),
  queryEnabled: () => http.get<AiClientSystemPromptItem[]>(CRUD.queryEnabled(BASE)),
}
