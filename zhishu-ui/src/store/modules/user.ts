/**
 * 统一登录态：用户端与管理端共用同一份 token。
 * 持久化交给 utils/auth（localStorage），store 只做内存镜像，保证刷新后仍保持登录。
 */
import { defineStore } from 'pinia'
import { AdminUserApi, type AdminUserInfo, type AdminUserLoginParams } from '@/api/admin-user'
import {
  clearAuth,
  getToken,
  getUserInfo,
  isTokenExpired,
  setAuth,
  type StoredUserInfo,
} from '@/utils/auth'

interface UserState {
  token: string
  userInfo: StoredUserInfo | null
}

export const useUserStore = defineStore('user', {
  state: (): UserState => ({
    token: getToken(),
    userInfo: getUserInfo(),
  }),

  getters: {
    /** 是否已登录：token 存在且未过期（与路由守卫共用同一判定） */
    isAuthenticated(state): boolean {
      return Boolean(state.token) && !isTokenExpired(state.token)
    },
    /** 顶栏展示名 */
    displayName(state): string {
      return state.userInfo?.username ?? '未登录'
    },
    /** 头像占位：取前两位大写 */
    initials(state): string {
      const name = state.userInfo?.username ?? ''
      return name.slice(0, 2).toUpperCase() || 'U'
    },
  },

  actions: {
    /** 登录：失败由调用方处理提示（接口已设 silent） */
    async login(params: AdminUserLoginParams): Promise<AdminUserInfo> {
      const result = await AdminUserApi.login(params)
      if (!result?.token) {
        throw new Error('账号或密码错误')
      }

      const userInfo: StoredUserInfo = {
        username: result.username,
        loginTime: new Date().toISOString(),
      }

      this.token = result.token
      this.userInfo = userInfo
      setAuth(result.token, userInfo)
      return result
    },

    /** 从本地存储恢复登录态（刷新 / 重开浏览器） */
    restore(): void {
      this.token = getToken()
      this.userInfo = getUserInfo()
    },

    logout(): void {
      this.token = ''
      this.userInfo = null
      clearAuth()
    },
  },
})
