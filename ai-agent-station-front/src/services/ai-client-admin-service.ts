import { API_CONFIG, DEFAULT_HEADERS } from '../config/api';

// 请求和响应接口定义
export interface AiClientQueryRequestDTO {
  clientId?: string;
  clientName?: string;
  status?: number;
  pageNum?: number;
  pageSize?: number;
}

// 新增客户端请求接口
export interface AiClientRequestDTO {
  id?: number;
  clientId: string;
  clientName: string;
  description?: string;
  status: number;
}

export interface AiClientResponseDTO {
  id: number;
  clientId: string;
  clientName: string;
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

export class AiClientAdminService {
  private baseUrl: string;

  constructor() {
    this.baseUrl = `${API_CONFIG.BASE_DOMAIN}/api/v1/admin/ai-client`;
  }

  /**
   * 查询客户端列表
   */
  async queryClientList(request: AiClientQueryRequestDTO): Promise<ApiResponse<AiClientResponseDTO[]>> {
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

  /**
   * 根据ID删除客户端
   */
  async deleteClientById(id: number): Promise<ApiResponse<boolean>> {
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
   * 根据客户端ID删除客户端
   */
  async deleteClientByClientId(clientId: string): Promise<ApiResponse<boolean>> {
    const response = await fetch(`${this.baseUrl}/delete-by-client-id/${clientId}`, {
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
   * 根据ID查询客户端详情
   */
  async queryClientById(id: number): Promise<ApiResponse<AiClientResponseDTO>> {
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
   * 查询所有客户端
   */
  async queryAllClients(): Promise<ApiResponse<AiClientResponseDTO[]>> {
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
   * 查询启用的客户端
   */
  async queryEnabledClients(): Promise<ApiResponse<AiClientResponseDTO[]>> {
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
   * 创建客户端
   */
  async createClient(request: AiClientRequestDTO): Promise<ApiResponse<boolean>> {
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
   * 更新客户端信息（根据ID）
   */
  async updateClientById(request: AiClientRequestDTO): Promise<ApiResponse<boolean>> {
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
   * 更新客户端信息（根据客户端ID）
   */
  async updateClientByClientId(request: AiClientRequestDTO): Promise<ApiResponse<boolean>> {
    const response = await fetch(`${this.baseUrl}/update-by-client-id`, {
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
}

// 导出服务实例
export const aiClientAdminService = new AiClientAdminService();