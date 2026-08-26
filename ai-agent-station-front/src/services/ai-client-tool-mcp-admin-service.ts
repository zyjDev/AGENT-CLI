import { API_CONFIG, DEFAULT_HEADERS } from '../config/api';

// 请求和响应接口定义
export interface AiClientToolMcpRequestDTO {
  id?: number;
  mcpId?: string;
  mcpName?: string;
  transportType?: string;
  transportConfig?: string;
  requestTimeout?: number;
  status?: number;
}

export interface AiClientToolMcpQueryRequestDTO {
  mcpId?: string;
  mcpName?: string;
  transportType?: string;
  status?: number;
  pageNum?: number;
  pageSize?: number;
}

export interface AiClientToolMcpResponseDTO {
  id: number;
  mcpId: string;
  mcpName: string;
  mcpDesc?: string;
  mcpCommand?: string;
  mcpArgs?: string;
  mcpEnv?: string;
  transportType?: string;
  transportConfig: string;
  requestTimeout?: number;
  status: number;
  createTime: string;
  updateTime: string;
}

export interface ApiResponse<T> {
  code: string;
  info: string;
  data: T;
}

export class AiClientToolMcpAdminService {
  private baseUrl: string;

  constructor() {
    this.baseUrl = `${API_CONFIG.BASE_DOMAIN}/api/v1/admin/ai-client-tool-mcp`;
  }

  /**
   * 创建MCP客户端工具配置
   */
  async createAiClientToolMcp(request: AiClientToolMcpRequestDTO): Promise<ApiResponse<boolean>> {
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
   * 根据ID更新MCP客户端工具配置
   */
  async updateAiClientToolMcpById(request: AiClientToolMcpRequestDTO): Promise<ApiResponse<boolean>> {
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
   * 根据MCP ID更新MCP客户端工具配置
   */
  async updateAiClientToolMcpByMcpId(request: AiClientToolMcpRequestDTO): Promise<ApiResponse<boolean>> {
    const response = await fetch(`${this.baseUrl}/update-by-mcp-id`, {
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
   * 根据ID删除MCP客户端工具配置
   */
  async deleteAiClientToolMcpById(id: number): Promise<ApiResponse<boolean>> {
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
   * 根据MCP ID删除MCP客户端工具配置
   */
  async deleteAiClientToolMcpByMcpId(mcpId: string): Promise<ApiResponse<boolean>> {
    const response = await fetch(`${this.baseUrl}/delete-by-mcp-id/${mcpId}`, {
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
   * 根据ID查询MCP客户端工具配置
   */
  async queryAiClientToolMcpById(id: number): Promise<ApiResponse<AiClientToolMcpResponseDTO>> {
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
   * 根据MCP ID查询MCP客户端工具配置
   */
  async queryAiClientToolMcpByMcpId(mcpId: string): Promise<ApiResponse<AiClientToolMcpResponseDTO>> {
    const response = await fetch(`${this.baseUrl}/query-by-mcp-id/${mcpId}`, {
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
   * 查询所有MCP客户端工具配置
   */
  async queryAllAiClientToolMcps(): Promise<ApiResponse<AiClientToolMcpResponseDTO[]>> {
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
   * 根据状态查询MCP客户端工具配置
   */
  async queryAiClientToolMcpsByStatus(status: number): Promise<ApiResponse<AiClientToolMcpResponseDTO[]>> {
    const response = await fetch(`${this.baseUrl}/query-by-status/${status}`, {
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
   * 根据传输类型查询MCP客户端工具配置
   */
  async queryAiClientToolMcpsByTransportType(transportType: string): Promise<ApiResponse<AiClientToolMcpResponseDTO[]>> {
    const response = await fetch(`${this.baseUrl}/query-by-transport-type/${transportType}`, {
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
   * 查询启用的MCP客户端工具配置
   */
  async queryEnabledAiClientToolMcps(): Promise<ApiResponse<AiClientToolMcpResponseDTO[]>> {
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
   * 根据条件查询MCP客户端工具配置列表
   */
  async queryAiClientToolMcpList(request: AiClientToolMcpQueryRequestDTO): Promise<ApiResponse<AiClientToolMcpResponseDTO[]>> {
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
export const aiClientToolMcpAdminService = new AiClientToolMcpAdminService();