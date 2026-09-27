/**
 * 统一登录态工具 + 全局 401 拦截。
 *
 * ============================ 背景（沿用旧工程的事故结论）============================
 * 后端 AdminAuthInterceptor 对 /api/v1/admin/**、/api/v1/rag/** 做 JWT 校验，失效时返回
 * **HTTP 401** + {"code":"0001","info":"未登录或登录已过期"}。
 *
 * 旧实现的问题：各页面把 401 一律当成「网络故障」提示（"请检查网络连接"），
 * 导致「登录态过期」被伪装成「后端挂了」，排查方向被完全带偏。
 *
 * 因此这里做两件事：
 * 1. isAuthenticated() 会校验 JWT 的 exp，过期 token 不再放行进入后台；
 * 2. 在请求层与 fetch 层识别 401 → 清登录态 → 明确提示 → 跳登录页，并做并发去重与跳转兜底。
 * =================================================================
 */
import { message } from 'ant-design-vue'

/** localStorage 键统一加前缀，避免与其他本地调试应用互相污染 */
const TOKEN_KEY = 'zhishu:token'
const USER_INFO_KEY = 'zhishu:userInfo'
const LOGGED_IN_KEY = 'zhishu:isLoggedIn'
/** 「记住账号密码」的键：与登录态分开存，见文件末尾 */
const REMEMBERED_ACCOUNT_KEY = 'zhishu:rememberedAccount'

/**
 * 登录态只写 sessionStorage：关掉浏览器就要重新登录。
 *
 * 为什么不写 localStorage：那样「下次启动前端」会直接以上次的账号进去（用户明确不要这种），
 * 他要的是「停在登录页、账号密码已填好、点一下就能进」—— 那是文件末尾
 * saveRememberedAccount 的职责。两者刻意分开，互不牵连。
 */
const readItem = (key: string): string | null => {
  try {
    return sessionStorage.getItem(key)
  } catch {
    return null
  }
}

/**
 * 清 key：登录态在两个存储里都清。
 * 老版本把登录态写在 localStorage，留着既会被误读、也会让"退出了还像没退"。
 */
const removeItem = (key: string): void => {
  try {
    sessionStorage.removeItem(key)
    localStorage.removeItem(key)
  } catch {
    // 隐私模式 / 沙箱环境拿不到 storage：忽略，不影响当前会话
  }
}

/** 登录页路径 */
export const LOGIN_PATH = '/login'

export interface StoredUserInfo {
  /** 登录账号（展示用） */
  username: string
  /**
   * 用户ID：后端按它做数据隔离（owner_id）与对话记忆分区，必须持久化 ——
   * 丢了会导致换账号后仍读同一份本地会话。
   */
  userId: string
  /** 角色：admin=管理员（可维护公共资源），user=普通用户（只能改自己的资源） */
  userRole: string
  loginTime: string
}

/** base64url → 原始字符串（JWT payload 段使用 base64url 且可能含 UTF-8） */
const decodeBase64Url = (input: string): string => {
  const normalized = input.replace(/-/g, '+').replace(/_/g, '/')
  const padding = (4 - (normalized.length % 4)) % 4
  const binary = atob(normalized.padEnd(normalized.length + padding, '='))
  const bytes = Uint8Array.from(binary, (char) => char.charCodeAt(0))
  return new TextDecoder().decode(bytes)
}

/**
 * 判断 JWT 是否已过期。
 * 解析失败（格式非法 / 非 JWT）一律按「已过期」处理 —— 宁可多要求一次登录，
 * 也不要放进后台后让每个接口都报 401。
 */
export const isTokenExpired = (token: string): boolean => {
  try {
    const payloadPart = token.split('.')[1]
    if (!payloadPart) return true
    const payload = JSON.parse(decodeBase64Url(payloadPart)) as { exp?: number }
    // 没有 exp 视为永不过期
    if (typeof payload.exp !== 'number') return false
    return Date.now() >= payload.exp * 1000
  } catch (error) {
    console.error('[auth] JWT 解析失败，按已过期处理', error)
    return true
  }
}

export const getToken = (): string => readItem(TOKEN_KEY) ?? ''

export const getUserInfo = (): StoredUserInfo | null => {
  const raw = readItem(USER_INFO_KEY)
  if (!raw) return null
  try {
    return JSON.parse(raw) as StoredUserInfo
  } catch (error) {
    console.error('[auth] 本地用户信息解析失败，已忽略', error)
    return null
  }
}

/**
 * 写入登录态（三个 key 必须一起写，缺一个路由守卫就会误判）。
 * 只写 sessionStorage：本次浏览器会话有效，关掉浏览器即需重新登录（届时靠预填一键进入）。
 */
export const setAuth = (token: string, userInfo: StoredUserInfo): void => {
  // 先清干净：避免历史遗留的 localStorage 登录态与本次写入混在一起，读到过期的那份
  clearAuth()
  try {
    sessionStorage.setItem(TOKEN_KEY, token)
    sessionStorage.setItem(USER_INFO_KEY, JSON.stringify(userInfo))
    sessionStorage.setItem(LOGGED_IN_KEY, 'true')
  } catch {
    // 拿不到 storage（隐私模式）时不抛：内存里的 store 仍能支撑本次会话
  }
}

/** 清除登录态（三个 key 必须一起清，两个存储都清） */
export const clearAuth = (): void => {
  removeItem(TOKEN_KEY)
  removeItem(USER_INFO_KEY)
  removeItem(LOGGED_IN_KEY)
}

/** 是否已登录：三个 key 齐全 且 token 未过期 */
export const isAuthenticated = (): boolean => {
  if (typeof window === 'undefined') return false
  const token = readItem(TOKEN_KEY)
  const userInfo = readItem(USER_INFO_KEY)
  const isLoggedIn = readItem(LOGGED_IN_KEY)
  if (!token || !userInfo || !isLoggedIn) return false
  return !isTokenExpired(token)
}

/* ------------------------------------------------------------------
 * 登录态失效处理
 * 通过 setUnauthorizedHandler 注入 router 实例，避免 utils → router → store → utils 循环依赖
 * ------------------------------------------------------------------ */
type UnauthorizedHandler = () => void

let unauthorizedHandler: UnauthorizedHandler = () => {
  window.location.replace(LOGIN_PATH)
}

export const setUnauthorizedHandler = (handler: UnauthorizedHandler): void => {
  unauthorizedHandler = handler
}

/** 防止并发请求同时 401 时弹出多个提示 / 触发多次跳转 */
let handlingUnauthorized = false

/** 跳转前停留时长，让提示能被看见 */
const REDIRECT_DELAY_MS = 600
/** 兜底释放锁的时长：超过它还没离开当前页，就认为跳转失败 */
const LATCH_RELEASE_MS = 3000

/** 处理登录态失效：清登录态 → 明确提示 → 跳登录页 */
export const handleUnauthorized = (): void => {
  if (handlingUnauthorized) return
  handlingUnauthorized = true

  clearAuth()
  message.error('登录已过期，请重新登录')

  // 已经在登录页就只清状态，不再跳转（否则会无限刷新）
  if (window.location.pathname === LOGIN_PATH) {
    handlingUnauthorized = false
    return
  }

  setTimeout(() => {
    unauthorizedHandler()
  }, REDIRECT_DELAY_MS)

  // 兜底：若到点仍停在原页（跳转被拦截 / 未生效），释放锁。
  // 否则锁会永久为 true，之后的 401 被静默吞掉 —— 那正是旧工程「所有标签页都报网络问题」的翻版。
  setTimeout(() => {
    if (window.location.pathname !== LOGIN_PATH) {
      handlingUnauthorized = false
    }
  }, LATCH_RELEASE_MS)
}

const INSTALL_FLAG = '__zhishuUnauthorizedFetchInstalled'

/**
 * 安装全局 fetch 401 拦截。
 * SSE（Auto Agent 对话）走原生 fetch，不经过 axios，所以这一层是必需的。
 *
 * ⚠️ 刻意**不读取 response.body** —— 一旦读取，调用方拿到的就是已被消费的流。
 * 这里只判断状态码，响应体原样透传给业务代码。
 */
export const installFetchUnauthorizedInterceptor = (): void => {
  if (typeof window === 'undefined') return
  const globalWindow = window as typeof window & { [INSTALL_FLAG]?: boolean }
  if (globalWindow[INSTALL_FLAG]) return
  globalWindow[INSTALL_FLAG] = true

  const originalFetch = window.fetch.bind(window)

  window.fetch = async (input: RequestInfo | URL, init?: RequestInit): Promise<Response> => {
    const response = await originalFetch(input, init)
    if (response.status === 401) {
      handleUnauthorized()
    }
    return response
  }
}

/* ------------------------------------------------------------------
 * 「记住账号密码」：下次打开登录页自动填好，点一下「登录」即可
 * ------------------------------------------------------------------ */

export interface RememberedAccount {
  username: string
  password: string
}

/**
 * 这里只做一次可逆编码（Base64），**不是加密**。
 *
 * 说清风险：能读到这台浏览器 localStorage 的人（或任意一段同源 XSS）都能还原出密码。
 * 之所以仍然这么做，是因为要的体验就是「下次点一下就能登录」，而这必然要在本地留下凭据。
 * 想真正安全：应由后端下发长期 refresh token、前端只存 token —— 那需要后端新增刷新接口。
 */
const encodeAccount = (text: string): string => btoa(String.fromCharCode(...new TextEncoder().encode(text)))
const decodeAccount = (text: string): string =>
  new TextDecoder().decode(Uint8Array.from(atob(text), (char) => char.charCodeAt(0)))

/** 记住一组账号密码（登录 / 注册成功后调用） */
export const saveRememberedAccount = (account: RememberedAccount): void => {
  try {
    localStorage.setItem(REMEMBERED_ACCOUNT_KEY, encodeAccount(JSON.stringify(account)))
  } catch (error) {
    // 存不进去（隐私模式）就退化为「不记住」，绝不能因此让登录失败
    console.warn('[auth] 记住账号密码失败，已忽略', error)
  }
}

/** 取记住的账号密码：没记住 / 内容损坏都返回 null */
export const getRememberedAccount = (): RememberedAccount | null => {
  try {
    const raw = localStorage.getItem(REMEMBERED_ACCOUNT_KEY)
    if (!raw) return null
    const parsed = JSON.parse(decodeAccount(raw)) as RememberedAccount
    if (!parsed?.username) return null
    return { username: parsed.username, password: parsed.password ?? '' }
  } catch (error) {
    // 内容被改坏：当作没记住并清掉，免得每次进登录页都报错
    console.warn('[auth] 本地记住的账号密码解析失败，已清除', error)
    clearRememberedAccount()
    return null
  }
}

/** 清除记住的账号密码（不勾选就登录时调用） */
export const clearRememberedAccount = (): void => {
  try {
    localStorage.removeItem(REMEMBERED_ACCOUNT_KEY)
  } catch {
    // 隐私模式拿不到 storage：忽略
  }
}
