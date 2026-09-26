/**
 * 管理员用户接口（全局唯一登录入口）。
 * 登录成功后返回 JWT，同一 token 同时用于 /v1/admin/** 与 /v1/agent/**，因此双端切换无需二次登录。
 */
import { http } from './http'
import { ENDPOINTS } from './endpoints'

export interface AdminUserLoginParams {
  username: string
  password: string
}

export interface AdminUserInfo {
  id?: number
  userId?: string
  username: string
  status?: number
  token: string
}

export const AdminUserApi = {
  /** 登录：失败时静默，由登录页做表单内联提示，避免同时弹全局提示 */
  login(params: AdminUserLoginParams) {
    return http.post<AdminUserInfo>(ENDPOINTS.ADMIN_USER.LOGIN, params, { silent: true })
  },

  /** 校验账号密码（不签发 token），用于需要二次确认的场景 */
  validateLogin(params: AdminUserLoginParams) {
    return http.post<boolean>(ENDPOINTS.ADMIN_USER.VALIDATE_LOGIN, params, { silent: true })
  },
}
