import { API_CONFIG, DEFAULT_HEADERS } from '../config/api';

// 请求和响应接口定义
export interface AiClientSystemPromptRequestDTO {
  id?: number;
  promptId?: string;
  promptName?: string;
  promptContent?: string;
  description?: string;
  status?: number;
}

export interface AiClientSystemPromptQueryRequestDTO {
  promptId?: string;
  promptName?: string;
  status?: number;
  pageNum?: number;
  pageSize?: number;
}

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

export interface ApiResponse<T> {
  code: string;
  info: string;
  data: T;
}

export class AiClientSystemPromptAdminService {
  private baseUrl: string;

  constructor() {
    this.baseUrl = `${API_CONFIG.BASE_DOMAIN}/api/v1/admin/ai-client-system-prompt`;
  }

  /**
   * 创建系统提示词配置
   */
  async createSystemPrompt(request: AiClientSystemPromptRequestDTO): Promise<ApiResponse<boolean>> {
    const response = await fetch(`${this.baseUrl}/create`, {
      method: 'POST',
      headers: {
        ...DEFAULT_HEADERS,
      },
      body: JSON.stringify(request),
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    return await response.json();
  }

  /**
   * 根据ID更新系统提示词配置
   */
  async updateSystemPromptById(request: AiClientSystemPromptRequestDTO): Promise<ApiResponse<boolean>> {
    const response = await fetch(`${this.baseUrl}/update-by-id`, {
      method: 'PUT',
      headers: {
        ...DEFAULT_HEADERS,
      },
      body: JSON.stringify(request),
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    return await response.json();
  }

  /**
   * 根据提示词ID更新系统提示词配置
   */
  async updateSystemPromptByPromptId(request: AiClientSystemPromptRequestDTO): Promise<ApiResponse<boolean>> {
    const response = await fetch(`${this.baseUrl}/update-by-prompt-id`, {
      method: 'PUT',
      headers: {
        ...DEFAULT_HEADERS,
      },
      body: JSON.stringify(request),
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    return await response.json();
  }

  /**
   * 根据ID删除系统提示词配置
   */
  async deleteSystemPromptById(id: number): Promise<ApiResponse<boolean>> {
    const response = await fetch(`${this.baseUrl}/delete-by-id/${id}`, {
      method: 'DELETE',
      headers: {
        ...DEFAULT_HEADERS,
      },
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    return await response.json();
  }

  /**
   * 根据提示词ID删除系统提示词配置
   */
  async deleteSystemPromptByPromptId(promptId: string): Promise<ApiResponse<boolean>> {
    const response = await fetch(`${this.baseUrl}/delete-by-prompt-id/${promptId}`, {
      method: 'DELETE',
      headers: {
        ...DEFAULT_HEADERS,
      },
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    return await response.json();
  }

  /**
   * 根据ID查询系统提示词配置详情
   */
  async querySystemPromptById(id: number): Promise<ApiResponse<AiClientSystemPromptResponseDTO>> {
    const response = await fetch(`${this.baseUrl}/query-by-id/${id}`, {
      method: 'GET',
      headers: {
        ...DEFAULT_HEADERS,
      },
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    return await response.json();
  }

  /**
   * 根据提示词ID查询系统提示词配置详情
   */
  async querySystemPromptByPromptId(promptId: string): Promise<ApiResponse<AiClientSystemPromptResponseDTO>> {
    const response = await fetch(`${this.baseUrl}/query-by-prompt-id/${promptId}`, {
      method: 'GET',
      headers: {
        ...DEFAULT_HEADERS,
      },
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    return await response.json();
  }

  /**
   * 查询所有系统提示词配置
   */
  async queryAllSystemPrompts(): Promise<ApiResponse<AiClientSystemPromptResponseDTO[]>> {
    const response = await fetch(`${this.baseUrl}/query-all`, {
      method: 'GET',
      headers: {
        ...DEFAULT_HEADERS,
      },
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    return await response.json();
  }

  /**
   * 查询启用的系统提示词配置
   */
  async queryEnabledSystemPrompts(): Promise<ApiResponse<AiClientSystemPromptResponseDTO[]>> {
    const response = await fetch(`${this.baseUrl}/query-enabled`, {
      method: 'GET',
      headers: {
        ...DEFAULT_HEADERS,
      },
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    return await response.json();
  }

  /**
   * 根据提示词名称查询系统提示词配置
   */
  async querySystemPromptsByPromptName(promptName: string): Promise<ApiResponse<AiClientSystemPromptResponseDTO[]>> {
    const response = await fetch(`${this.baseUrl}/query-by-prompt-name/${promptName}`, {
      method: 'GET',
      headers: {
        ...DEFAULT_HEADERS,
      },
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    return await response.json();
  }

  /**
   * 根据条件查询系统提示词配置列表
   */
  async querySystemPromptList(request: AiClientSystemPromptQueryRequestDTO): Promise<ApiResponse<AiClientSystemPromptResponseDTO[]>> {
    const response = await fetch(`${this.baseUrl}/query-list`, {
      method: 'POST',
      headers: {
        ...DEFAULT_HEADERS,
      },
      body: JSON.stringify(request),
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    return await response.json();
  }
}

// 导出服务实例
export const aiClientSystemPromptAdminService = new AiClientSystemPromptAdminService();