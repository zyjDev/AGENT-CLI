/** MCP 工具管理（/api/v1/admin/ai-client-tool-mcp） */
import { http } from './http'
import { ADMIN_BASE, CRUD } from './endpoints'

export interface AiClientToolMcpItem {
  id: number
  mcpId: string
  mcpName: string
  transportType?: string
  transportConfig?: string
  requestTimeout?: number
  status: number
  createTime?: string
  updateTime?: string
}

/** 传输类型枚举 */
export const TRANSPORT_TYPE_OPTIONS = [
  { label: 'stdio', value: 'stdio' },
  { label: 'sse', value: 'sse' },
  { label: 'websocket', value: 'websocket' },
]

/** 默认请求超时（毫秒）：与旧实现的初始值对齐 */
export const DEFAULT_MCP_TIMEOUT_MS = 30000

const BASE = ADMIN_BASE.AI_CLIENT_TOOL_MCP

export const AiClientToolMcpApi = {
  queryList: (payload: Record<string, unknown>) => http.post<AiClientToolMcpItem[]>(CRUD.queryList(BASE), payload),
  create: (payload: Record<string, unknown>) => http.post<boolean>(CRUD.create(BASE), payload),
  updateById: (payload: Record<string, unknown>) => http.put<boolean>(CRUD.updateById(BASE), payload),
  deleteById: (id: number | string) => http.delete<boolean>(CRUD.deleteById(BASE, id)),
  queryAll: () => http.get<AiClientToolMcpItem[]>(CRUD.queryAll(BASE)),
  queryEnabled: () => http.get<AiClientToolMcpItem[]>(CRUD.queryEnabled(BASE)),
}
