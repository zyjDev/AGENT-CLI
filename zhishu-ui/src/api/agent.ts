/**
 * Auto Agent 接口（用户端对话）。
 *
 * auto_agent 是 SSE 流式接口，**必须用原生 fetch**（见 composables/useSse.ts），
 * 这里只提供地址与 JSON 类接口。
 */
import { http } from './http'
import { buildUrl, ENDPOINTS } from './endpoints'

export interface AvailableAgent {
  agentId: string
  agentName: string
  description?: string
  channel?: string
  strategy?: string
  status?: number
}

export const AgentApi = {
  /** 查询可用智能体列表 */
  queryAvailableAgents() {
    return http.get<AvailableAgent[]>(ENDPOINTS.AGENT.QUERY_AVAILABLE_AGENTS)
  },

  /** 装配智能体：让编排结果真正生效 */
  armoryAgent(agentId: string) {
    return http.post<boolean>(ENDPOINTS.AGENT.ARMORY_AGENT, { agentId })
  },

  /** 装配客户端模型 API */
  armoryApi(apiId: string) {
    return http.post<boolean>(ENDPOINTS.AGENT.ARMORY_API, { apiId })
  },
}

/** SSE 端点完整地址（供原生 fetch 使用） */
export const AUTO_AGENT_URL = buildUrl(ENDPOINTS.AGENT.AUTO_AGENT)
