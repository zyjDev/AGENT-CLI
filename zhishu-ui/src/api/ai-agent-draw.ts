/**
 * 智能体编排配置（/api/v1/admin/ai-agent-draw）
 * configData 是画布 JSON 字符串，字段契约见 types/draw.ts。
 */
import { http } from './http'
import { ENDPOINTS } from './endpoints'

export interface AiAgentDrawConfigItem {
  id?: number
  configId: string
  configName: string
  description?: string
  agentId: string
  /** 画布 JSON（字符串形态） */
  configData?: string
  version?: number
  status?: number
  createBy?: string
  updateBy?: string
  createTime?: string
  updateTime?: string
}

export interface SaveDrawConfigPayload {
  configId: string
  configName: string
  description?: string
  agentId: string
  /** JSON.stringify 后的画布数据 */
  configData: string
  createBy?: string
  updateBy?: string
}

export const AiAgentDrawApi = {
  queryList: (payload: Record<string, unknown>) =>
    http.post<AiAgentDrawConfigItem[]>(ENDPOINTS.AI_AGENT_DRAW.QUERY_LIST, payload),

  /** 保存成功时返回 configId */
  saveConfig: (payload: SaveDrawConfigPayload) =>
    http.post<string>(ENDPOINTS.AI_AGENT_DRAW.SAVE_CONFIG, payload),

  getConfig: (configId: string) =>
    http.get<AiAgentDrawConfigItem>(ENDPOINTS.AI_AGENT_DRAW.GET_CONFIG(configId)),

  deleteConfig: (configId: string) =>
    http.delete<string>(ENDPOINTS.AI_AGENT_DRAW.DELETE_CONFIG(configId)),
}
