/**
 * 数据统计API服务
 */

import { API_CONFIG, DEFAULT_HEADERS } from '../config/api';

// 定义数据统计响应数据类型
export interface DataStatisticsResponseDTO {
  activeAgentCount: number;
  clientCount: number;
  mcpToolCount: number;
  systemPromptCount: number;
  ragOrderCount: number;
  advisorCount: number;
  modelCount: number;
  todayRequestCount: number;
  successRate: number;
  runningTaskCount: number;
}

// 定义API响应格式
export interface ApiResponse<T> {
  code: string;
  info: string;
  data: T;
}

/**
 * 数据统计API服务类
 */
export class DataStatisticsService {
  private static readonly BASE_URL = `${API_CONFIG.BASE_DOMAIN}/api/v1/admin/data/statistics`;

  /**
   * 获取系统数据统计
   */
  static async getDataStatistics(): Promise<DataStatisticsResponseDTO> {
    try {
      const response = await fetch(`${this.BASE_URL}/get-data-statistics`, {
        method: 'GET',
        headers: DEFAULT_HEADERS,
      });

      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`);
      }

      const result: ApiResponse<DataStatisticsResponseDTO> = await response.json();
      
      if (result.code === '0000') {
        return result.data;
      } else {
        throw new Error(result.info || '获取数据统计失败');
      }
    } catch (error) {
      console.error('获取数据统计失败:', error);
      // 返回默认数据作为降级处理
      return {
        activeAgentCount: 0,
        clientCount: 0,
        mcpToolCount: 0,
        systemPromptCount: 0,
        ragOrderCount: 0,
        advisorCount: 0,
        modelCount: 0,
        todayRequestCount: 0,
        successRate: 0,
        runningTaskCount: 0,
      };
    }
  }
}