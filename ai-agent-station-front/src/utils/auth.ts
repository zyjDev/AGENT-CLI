/**
 * 管理端登录态工具 + 全局 401 拦截
 *
 * ============================ 背景（2026-09-20 真实事故）============================
 * 后端 `AdminAuthInterceptor` 对 `/api/v1/admin/**`、`/api/v1/rag/**` 做 JWT 校验，
 * 失效时返回 **HTTP 401** + `{"code":"0001","info":"未登录或登录已过期"}`。
 *
 * 而前端有 15 个 service、93 处 `fetch` 调用，统一长这样：
 *
 *     if (!response.ok) { throw new Error(`HTTP error! status: ${response.status}`); }
 *
 * 页面 catch 里则一律写死：
 *
 *     Toast.error('获取知识库配置列表失败，请检查网络连接');
 *
 * 于是「登录态过期」被**伪装成「网络故障」** —— 所有标签页都报网络问题，
 * 排查方向被彻底带偏（实际只要重新登录即可）。
 *
 * ============================ 两个修复 ============================
 * 1. `isAuthenticated()` 会校验 JWT 的 `exp`。原路由守卫只检查 token 字符串
 *    **是否存在**，过期的 token 照样放行进入后台，然后每个接口都 401。
 * 2. `installAuthFetchInterceptor()` 在**唯一的 fetch 入口**处识别 401，
 *    清登录态 → 给准确提示 → 跳登录页。等价于在后端加一个 Filter，
 *    93 处调用点一行都不用改。
 * =================================================================
 */

import { Toast } from '@douyinfe/semi-ui';

const TOKEN_KEY = 'token';
const USER_INFO_KEY = 'userInfo';
const LOGGED_IN_KEY = 'isLoggedIn';

const LOGIN_PATH = '/login';

/** base64url → 原始字符串（JWT 的 payload 段用 base64url 且可能含 UTF-8） */
const decodeBase64Url = (input: string): string => {
  const normalized = input.replace(/-/g, '+').replace(/_/g, '/');
  const padding = (4 - (normalized.length % 4)) % 4;
  const binary = atob(normalized.padEnd(normalized.length + padding, '='));
  const bytes = Uint8Array.from(binary, (char) => char.charCodeAt(0));
  return new TextDecoder().decode(bytes);
};

/**
 * 判断 JWT 是否已过期。
 * 解析失败（格式非法 / 非 JWT）一律按「已过期」处理 ——
 * 宁可多要求一次登录，也不要放进后台后让每个接口都报 401。
 */
export const isTokenExpired = (token: string): boolean => {
  try {
    const payloadPart = token.split('.')[1];
    if (!payloadPart) return true;
    const payload = JSON.parse(decodeBase64Url(payloadPart)) as { exp?: number };
    // 没有 exp 视为永不过期
    if (typeof payload.exp !== 'number') return false;
    return Date.now() >= payload.exp * 1000;
  } catch {
    return true;
  }
};

/**
 * 是否已登录：三个 key 齐全 **且 token 未过期**。
 * 与 `app.tsx` 的路由守卫共用，避免出现「能进后台但接口全 401」的状态。
 */
export const isAuthenticated = (): boolean => {
  if (typeof localStorage === 'undefined') return false;
  const token = localStorage.getItem(TOKEN_KEY);
  const userInfo = localStorage.getItem(USER_INFO_KEY);
  const isLoggedIn = localStorage.getItem(LOGGED_IN_KEY);
  if (!token || !userInfo || !isLoggedIn) return false;
  return !isTokenExpired(token);
};

/** 清除本地登录态（三个 key 必须一起清，缺一个路由守卫就会误判） */
export const clearAuthState = (): void => {
  if (typeof localStorage === 'undefined') return;
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(USER_INFO_KEY);
  localStorage.removeItem(LOGGED_IN_KEY);
};

/** 防止并发请求同时 401 时弹出多个提示 / 触发多次跳转 */
let handlingUnauthorized = false;

/** 跳转前停留时长，让 Toast 能被看见 */
const REDIRECT_DELAY_MS = 600;
/** 兜底释放锁的时长：超过它还没离开当前页，就认为跳转失败 */
const LATCH_RELEASE_MS = 3000;

/**
 * 处理登录态失效：清登录态 → 提示 → 跳登录页。
 */
export const handleUnauthorized = (): void => {
  if (handlingUnauthorized) return;
  handlingUnauthorized = true;

  clearAuthState();
  Toast.error('登录已过期，请重新登录');

  // 已经在登录页就只清状态，不再跳转（否则会无限刷新）
  if (typeof window === 'undefined' || window.location.pathname === LOGIN_PATH) {
    handlingUnauthorized = false;
    return;
  }

  setTimeout(() => {
    window.location.replace(LOGIN_PATH);
  }, REDIRECT_DELAY_MS);

  // 兜底：若到点仍停在原页（跳转被拦截 / replace 未生效），释放锁。
  // 否则 handlingUnauthorized 会永久为 true，之后的 401 被静默吞掉 —— 那正是
  // 本次事故「所有标签页都报网络问题」的翻版：失效状态被无声掩盖。
  setTimeout(() => {
    if (window.location.pathname !== LOGIN_PATH) {
      handlingUnauthorized = false;
    }
  }, LATCH_RELEASE_MS);
};

const INSTALL_FLAG = '__adminAuthFetchInstalled';

/**
 * 安装全局 fetch 拦截：识别 401 并交给 `handleUnauthorized`。
 *
 * ⚠️ 刻意**不读取 response.body** —— 一旦读取，调用方拿到的就是已被消费的流。
 * 这里只判断状态码，响应体原样透传给业务代码。
 */
export const installAuthFetchInterceptor = (): void => {
  if (typeof window === 'undefined') return;
  const globalWindow = window as typeof window & { [INSTALL_FLAG]?: boolean };
  if (globalWindow[INSTALL_FLAG]) return;
  globalWindow[INSTALL_FLAG] = true;

  const originalFetch = window.fetch.bind(window);

  window.fetch = async (input: RequestInfo | URL, init?: RequestInit): Promise<Response> => {
    const response = await originalFetch(input, init);
    if (response.status === 401) {
      handleUnauthorized();
    }
    return response;
  };
};
