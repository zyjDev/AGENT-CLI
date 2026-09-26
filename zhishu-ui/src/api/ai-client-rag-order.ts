/**
 * RAG 知识库配置（/api/v1/admin/ai-client-rag-order）
 * 文件上传为 multipart：name / tag / files（可重复）。
 */
import { http } from './http'
import { ADMIN_BASE, CRUD, ENDPOINTS } from './endpoints'

export interface AiClientRagOrderItem {
  id: number
  ragId: string
  ragName: string
  knowledgeTag: string
  version?: number
  status: number
  createTime?: string
  updateTime?: string
}

/** 允许上传的知识库文件类型与大小上限（与旧实现一致） */
export const RAG_ALLOWED_EXTENSIONS = ['.txt', '.pdf', '.doc', '.docx', '.md']
export const RAG_MAX_FILE_SIZE_MB = 10

const BASE = ADMIN_BASE.AI_CLIENT_RAG_ORDER

export const AiClientRagOrderApi = {
  queryList: (payload: Record<string, unknown>) => http.post<AiClientRagOrderItem[]>(CRUD.queryList(BASE), payload),
  create: (payload: Record<string, unknown>) => http.post<boolean>(CRUD.create(BASE), payload),
  updateById: (payload: Record<string, unknown>) => http.put<boolean>(CRUD.updateById(BASE), payload),
  deleteById: (id: number | string) => http.delete<boolean>(CRUD.deleteById(BASE, id)),
  queryAll: () => http.get<AiClientRagOrderItem[]>(CRUD.queryAll(BASE)),
  queryEnabled: () => http.get<AiClientRagOrderItem[]>(CRUD.queryEnabled(BASE)),

  /**
   * 上传知识库文件。
   * 刻意不设置 Content-Type：交给浏览器补 multipart boundary，手写会丢失 boundary。
   */
  uploadFiles(name: string, tag: string, files: File[]) {
    const formData = new FormData()
    formData.append('name', name)
    formData.append('tag', tag)
    for (const file of files) {
      formData.append('files', file)
    }
    return http.post<boolean>(ENDPOINTS.RAG_UPLOAD, formData, {
      headers: { 'Content-Type': undefined as unknown as string },
      timeout: 120_000,
    })
  },
}
