/**
 * AI Agent Draw 配置服务
 */
import { API_ENDPOINTS, DEFAULT_HEADERS } from '../config';

export interface AiAgentDrawConfigResponseDTO {
  id?: number;
  configId: string;
  configName: string;
  description?: string;
  agentId: string;
  configData?: string;
  version?: number;
  status?: number;
  createBy?: string;
  updateBy?: string;
  createTime?: string;
  updateTime?: string;
}

export interface ApiResponse<T> {
  code: string;
  info: string;
  data: T;
}

export interface AiAgentDrawConfigQueryRequestDTO {
  configId?: string;
  configName?: string;
  agentId?: string;
  status?: number;
  pageNum?: number;
  pageSize?: number;
}

export class AiAgentDrawService {
  private static readonly BASE_URL = API_ENDPOINTS.AI_AGENT_DRAW.BASE;

  static async queryDrawConfigList(
    payload: AiAgentDrawConfigQueryRequestDTO
  ): Promise<AiAgentDrawConfigResponseDTO[]> {
    try {
      const response = await fetch(`${this.BASE_URL}${API_ENDPOINTS.AI_AGENT_DRAW.QUERY_LIST}`, {
        method: 'POST',
        headers: DEFAULT_HEADERS,
        body: JSON.stringify(payload),
      });

      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`);
      }

      const result: ApiResponse<AiAgentDrawConfigResponseDTO[]> = await response.json();
      if (result.code === '0000') {
        return result.data || [];
      } else {
        throw new Error(result.info || '查询失败');
      }
    } catch (error) {
      console.error('查询Agent Draw配置列表失败:', error);
      return [];
    }
  }

  static async getDrawConfig(configId: string): Promise<AiAgentDrawConfigResponseDTO | null> {
    try {
      const url = `${this.BASE_URL}${API_ENDPOINTS.AI_AGENT_DRAW.GET_CONFIG}/${encodeURIComponent(configId)}`;
      console.log('发起API请求:', url);
      
      const response = await fetch(url, {
        method: 'GET',
        headers: DEFAULT_HEADERS,
      });

      console.log('API响应状态:', response.status, response.statusText);

      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`);
      }

      const result: ApiResponse<AiAgentDrawConfigResponseDTO> = await response.json();
      console.log('API响应数据:', result);
      
      if (result.code === '0000') {
        console.log('API调用成功，返回数据:', result.data);
        return result.data || null;
      } else {
        console.error('API返回错误:', result.code, result.info);
        throw new Error(result.info || '获取配置失败');
      }
    } catch (error) {
      console.error('获取Agent Draw配置失败:', error);
      return null;
    }
  }

  static async deleteDrawConfig(configId: string): Promise<boolean> {
    try {
      const response = await fetch(
        `${this.BASE_URL}${API_ENDPOINTS.AI_AGENT_DRAW.DELETE_CONFIG}/${encodeURIComponent(configId)}`,
        {
          method: 'DELETE',
          headers: DEFAULT_HEADERS,
        }
      );

      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`);
      }

      const result: ApiResponse<boolean> = await response.json();
      if (result.code === '0000') {
        return result.data || false;
      } else {
        throw new Error(result.info || '删除配置失败');
      }
    } catch (error) {
      console.error('删除Agent Draw配置失败:', error);
      throw error;
    }
  }
}