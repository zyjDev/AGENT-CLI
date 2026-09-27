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
  /** 角色：admin=管理员（可维护公共资源），user=普通用户 */
  userRole?: string
  id?: number
  userId?: string
  username: string
  status?: number
  token: string
}

export interface AdminUserRegisterParams {
  username: string
  password: string
  confirmPassword: string
}

/** 修改密码：不含 userId —— 后端只认 JWT 里的身份 */
export interface AdminUserChangePasswordParams {
  oldPassword: string
  newPassword: string
  confirmPassword: string
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

  /** 自助注册：失败静默，由表单内联提示；成功后返回带 token 的用户信息 */
  register(params: AdminUserRegisterParams) {
    return http.post<AdminUserInfo>(ENDPOINTS.ADMIN_USER.REGISTER, params, { silent: true })
  },

  /** 修改自己的密码：失败静默，由弹窗内联提示（空密码/原密码错都要看到具体原因） */
  changePassword(params: AdminUserChangePasswordParams) {
    return http.post<boolean>(ENDPOINTS.ADMIN_USER.CHANGE_PASSWORD, params, { silent: true })
  },
}
