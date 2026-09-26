/**
 * 统一登录态：用户端与管理端共用同一份 token。
 * 持久化交给 utils/auth（localStorage），store 只做内存镜像，保证刷新后仍保持登录。
 */
import { defineStore } from 'pinia'
import {
  AdminUserApi,
  type AdminUserInfo,
  type AdminUserLoginParams,
  type AdminUserRegisterParams,
} from '@/api/admin-user'
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
    /**
     * 当前用户ID：本地按用户隔离的数据（会话列表等）都用它做 key。
     * 旧版本 localStorage 里没有该字段时退化为空串，由调用方兜底。
     */
    userId(state): string {
      return state.userInfo?.userId ?? ''
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
      this.applyLogin(result)
      return result
    },

    /**
     * 注册：后端注册成功即签发 token，走与登录完全相同的落地路径，
     * 因此注册完直接进对话页，不需要再登录一次。
     */
    async register(params: AdminUserRegisterParams): Promise<AdminUserInfo> {
      const result = await AdminUserApi.register(params)
      if (!result?.token) {
        throw new Error('注册失败，请稍后重试')
      }
      this.applyLogin(result)
      return result
    },

    /** 登录态落地（login / register 共用）：内存 + localStorage 同时写 */
    applyLogin(result: AdminUserInfo): void {
      const userInfo: StoredUserInfo = {
        username: result.username,
        userId: String(result.userId ?? ''),
        loginTime: new Date().toISOString(),
      }
      this.token = result.token
      this.userInfo = userInfo
      setAuth(result.token, userInfo)
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
