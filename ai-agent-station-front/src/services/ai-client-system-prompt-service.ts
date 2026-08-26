/**
 * AI客户端系统提示词API服务
 */

import { API_ENDPOINTS, DEFAULT_HEADERS } from '../config';

// 定义响应数据类型
export interface AiClientSystemPromptResponseDTO {
  id: number;
  promptId: string;
  promptName: string;
  promptContent: string;
  description?: string;
  status: number;
  createTime: string;
  updateTime: string;
}

// 定义API响应格式
export interface ApiResponse<T> {
  code: string;
  info: string;
  data: T;
}

/**
 * AI客户端系统提示词服务类
 */
export class AiClientSystemPromptService {
  /**
   * 查询所有AI客户端系统提示词
   * @returns Promise<AiClientSystemPromptResponseDTO[]>
   */
  static async queryAllAiClientSystemPrompts(): Promise<AiClientSystemPromptResponseDTO[]> {
    try {
      const response = await fetch(
        `${API_ENDPOINTS.AI_CLIENT_SYSTEM_PROMPT.BASE}${API_ENDPOINTS.AI_CLIENT_SYSTEM_PROMPT.QUERY_ALL}`,
        {
          method: 'GET',
          headers: DEFAULT_HEADERS,
        }
      );

      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`);
      }

      const result: ApiResponse<AiClientSystemPromptResponseDTO[]> = await response.json();
      
      if (result.code === '0000') {
        return result.data || [];
      } else {
        throw new Error(result.info || '获取系统提示词列表失败');
      }
    } catch (error) {
      console.error('查询系统提示词列表失败:', error);
      throw error;
    }
  }
}