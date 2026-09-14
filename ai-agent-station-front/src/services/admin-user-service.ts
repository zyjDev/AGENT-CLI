/**
 * 管理员用户API服务
 */

import { API_ENDPOINTS, DEFAULT_HEADERS } from '../config';

// 定义登录请求数据类型
export interface AdminUserLoginRequestDTO {
  username: string;
  password: string;
}

export interface AdminUserResponseDTO {
  id?: number;
  userId?: string;
  username: string;
  status?: number;
  token: string;
}

// 定义API响应格式
export interface ApiResponse<T> {
  code: string;
  info: string;
  data: T;
}

/**
 * 管理员用户API服务类
 */
export class AdminUserService {
  private static readonly BASE_URL = API_ENDPOINTS.ADMIN_USER.BASE;

  /**
   * 管理员用户登录
   * @param loginData 登录数据
   * @returns 登录成功后的用户信息（含token）
   */
  static async loginAdminUser(loginData: AdminUserLoginRequestDTO): Promise<AdminUserResponseDTO | null> {
    try {
      const response = await fetch(`${this.BASE_URL}${API_ENDPOINTS.ADMIN_USER.LOGIN}`, {
        method: 'POST',
        headers: DEFAULT_HEADERS,
        body: JSON.stringify(loginData),
      });

      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`);
      }

      const result: ApiResponse<AdminUserResponseDTO> = await response.json();

      if (result.code === '0000' && result.data?.token) {
        return result.data;
      }
      console.error('登录失败:', result.info);
      return null;
    } catch (error) {
      console.error('登录请求失败:', error);
      return null;
    }
  }


  /**
   * 验证管理员用户登录
   * @param loginData 登录数据
   * @returns Promise<boolean> 登录是否成功
   */
  static async validateAdminUserLogin(loginData: AdminUserLoginRequestDTO): Promise<boolean> {
    try {
      const response = await fetch(`${this.BASE_URL}${API_ENDPOINTS.ADMIN_USER.VALIDATE_LOGIN}`, {
        method: 'POST',
        headers: DEFAULT_HEADERS,
        body: JSON.stringify(loginData),
      });

      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`);
      }

      const result: ApiResponse<boolean> = await response.json();
      
      if (result.code === '0000') {
        return result.data || false;
      } else {
        console.error('登录验证失败:', result.info);
        return false;
      }
    } catch (error) {
      console.error('登录验证请求失败:', error);
      return false;
    }
  }
}
