/** 数据总览统计（/api/v1/admin/data/statistics/get-data-statistics） */
import { http } from './http'
import { ENDPOINTS } from './endpoints'

export interface DataStatistics {
  activeAgentCount: number
  clientCount: number
  mcpToolCount: number
  systemPromptCount: number
  ragOrderCount: number
  advisorCount: number
  modelCount: number
  todayRequestCount: number
  /** 百分比数值（如 98.6 表示 98.6%） */
  successRate: number
  runningTaskCount: number
}

export const DataStatisticsApi = {
  get: () => http.get<DataStatistics>(ENDPOINTS.DATA_STATISTICS),
}
