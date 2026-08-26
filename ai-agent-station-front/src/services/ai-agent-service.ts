/**
 * AI Agent 服务
 */
import { API_ENDPOINTS, DEFAULT_HEADERS } from '../config';

export interface ArmoryAgentRequestDTO {
  agentId: string;
}

export interface ArmoryApiRequestDTO {
  apiId: string;
}

export interface ApiResponse<T> {
  code: string;
  info: string;
  data: T;
}

export class AiAgentService {
  private static readonly BASE_URL = API_ENDPOINTS.AI_AGENT.BASE;

  /**
   * 装配智能体
   * @param agentId 智能体ID
   * @returns Promise<boolean> 装配是否成功
   */
  static async armoryAgent(agentId: string): Promise<boolean> {
    try {
      const payload: ArmoryAgentRequestDTO = {
        agentId
      };

      const response = await fetch(`${this.BASE_URL}${API_ENDPOINTS.AI_AGENT.ARMORY_AGENT}`, {
        method: 'POST',
        headers: DEFAULT_HEADERS,
        body: JSON.stringify(payload),
      });

      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`);
      }

      const result: ApiResponse<boolean> = await response.json();
      if (result.code === '0000') {
        return result.data || false;
      } else {
        throw new Error(result.info || '装配失败');
      }
    } catch (error) {
      console.error('装配智能体失败:', error);
      throw error;
    }
  }

  /**
   * 装配API
   * @param apiId API ID
   * @returns Promise<boolean> 装配是否成功
   */
  static async armoryApi(apiId: string): Promise<boolean> {
    try {
      const payload: ArmoryApiRequestDTO = {
        apiId
      };

      const response = await fetch(`${this.BASE_URL}${API_ENDPOINTS.AI_AGENT.ARMORY_API}`, {
        method: 'POST',
        headers: DEFAULT_HEADERS,
        body: JSON.stringify(payload),
      });

      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`);
      }

      const result: ApiResponse<boolean> = await response.json();
      if (result.code === '0000') {
        return result.data || false;
      } else {
        throw new Error(result.info || '装配API失败');
      }
    } catch (error) {
      console.error('装配API失败:', error);
      throw error;
    }
  }
}