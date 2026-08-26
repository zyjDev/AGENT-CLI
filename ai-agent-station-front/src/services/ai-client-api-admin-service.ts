import { API_CONFIG, DEFAULT_HEADERS } from '../config/api';

// 请求和响应接口定义
export interface AiClientApiQueryRequestDTO {
  apiId?: string;
  status?: number;
  pageNum?: number;
  pageSize?: number;
}

// AI客户端API请求接口
export interface AiClientApiRequestDTO {
  id?: number;
  apiId: string;
  baseUrl: string;
  apiKey?: string;
  completionsPath?: string;
  embeddingsPath?: string;
  status: number;
}

export interface AiClientApiResponseDTO {
  id: number;
  apiId: string;
  baseUrl: string;
  apiKey?: string;
  completionsPath?: string;
  embeddingsPath?: string;
  status: number;
  createTime: string;
  updateTime: string;
}

export interface ApiResponse<T> {
  code: string;
  info: string;
  data: T;
}

export class AiClientApiAdminService {
  private baseUrl: string;

  constructor() {
    this.baseUrl = `${API_CONFIG.BASE_DOMAIN}/api/v1/admin/ai-client-api`;
  }

  /**
   * 创建AI客户端API配置
   */
  async createAiClientApi(request: AiClientApiRequestDTO): Promise<ApiResponse<boolean>> {
    const response = await fetch(`${this.baseUrl}/create`, {
      method: 'POST',
      headers: {
        ...DEFAULT_HEADERS,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(request),
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    return await response.json();
  }

  /**
   * 根据ID更新AI客户端API配置
   */
  async updateAiClientApiById(request: AiClientApiRequestDTO): Promise<ApiResponse<boolean>> {
    const response = await fetch(`${this.baseUrl}/update-by-id`, {
      method: 'POST',
      headers: {
        ...DEFAULT_HEADERS,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(request),
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    return await response.json();
  }

  /**
   * 根据API ID更新AI客户端API配置
   */
  async updateAiClientApiByApiId(request: AiClientApiRequestDTO): Promise<ApiResponse<boolean>> {
    const response = await fetch(`${this.baseUrl}/update-by-api-id`, {
      method: 'POST',
      headers: {
        ...DEFAULT_HEADERS,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(request),
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    return await response.json();
  }

  /**
   * 根据ID删除AI客户端API配置
   */
  async deleteAiClientApiById(id: number): Promise<ApiResponse<boolean>> {
    const response = await fetch(`${this.baseUrl}/delete-by-id/${id}`, {
      method: 'DELETE',
      headers: DEFAULT_HEADERS,
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    return await response.json();
  }

  /**
   * 根据API ID删除AI客户端API配置
   */
  async deleteAiClientApiByApiId(apiId: string): Promise<ApiResponse<boolean>> {
    const response = await fetch(`${this.baseUrl}/delete-by-api-id/${apiId}`, {
      method: 'DELETE',
      headers: DEFAULT_HEADERS,
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    return await response.json();
  }

  /**
   * 根据ID查询AI客户端API配置
   */
  async queryAiClientApiById(id: number): Promise<ApiResponse<AiClientApiResponseDTO>> {
    const response = await fetch(`${this.baseUrl}/query-by-id/${id}`, {
      method: 'GET',
      headers: DEFAULT_HEADERS,
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    return await response.json();
  }

  /**
   * 根据API ID查询AI客户端API配置
   */
  async queryAiClientApiByApiId(apiId: string): Promise<ApiResponse<AiClientApiResponseDTO>> {
    const response = await fetch(`${this.baseUrl}/query-by-api-id/${apiId}`, {
      method: 'GET',
      headers: DEFAULT_HEADERS,
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    return await response.json();
  }

  /**
   * 查询所有启用的AI客户端API配置
   */
  async queryEnabledAiClientApis(): Promise<ApiResponse<AiClientApiResponseDTO[]>> {
    const response = await fetch(`${this.baseUrl}/query-enabled`, {
      method: 'GET',
      headers: DEFAULT_HEADERS,
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    return await response.json();
  }

  /**
   * 分页查询AI客户端API配置列表
   */
  async queryAiClientApiList(request: AiClientApiQueryRequestDTO): Promise<ApiResponse<AiClientApiResponseDTO[]>> {
    const response = await fetch(`${this.baseUrl}/query-list`, {
      method: 'POST',
      headers: {
        ...DEFAULT_HEADERS,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(request),
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    return await response.json();
  }

  /**
   * 查询所有AI客户端API配置
   */
  async queryAllAiClientApis(): Promise<ApiResponse<AiClientApiResponseDTO[]>> {
    const response = await fetch(`${this.baseUrl}/query-all`, {
      method: 'GET',
      headers: DEFAULT_HEADERS,
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    return await response.json();
  }
}

export const aiClientApiAdminService = new AiClientApiAdminService();