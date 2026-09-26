/**
 * 统一 axios 实例。
 *
 * 职责：
 * 1. 注入 Authorization: Bearer <token>；
 * 2. 解包后端统一响应 { code, info, data } —— 业务层只拿 data；
 * 3. code !== "0000" 抛 BusinessError 并提示 info；
 * 4. HTTP 401 交给 utils/auth 的 handleUnauthorized（清态 → 提示 → 跳登录页）。
 *
 * 注意：SSE（/v1/agent/auto_agent）**不走这里**，必须用原生 fetch，
 * 否则 axios 会消费掉响应流。
 */
import axios, { AxiosError, type AxiosInstance, type AxiosRequestConfig } from 'axios'
import { message } from 'ant-design-vue'
import { API_SUCCESS_CODE, BusinessError, type ApiResponse } from '@/types/api'
import { getToken, handleUnauthorized } from '@/utils/auth'
import { API_PREFIX } from './endpoints'

declare module 'axios' {
  export interface AxiosRequestConfig {
    /** 置 true 时不弹全局错误提示，由调用方自行处理（如表单内联报错） */
    silent?: boolean
  }
}

const REQUEST_TIMEOUT_MS = 30_000

const instance: AxiosInstance = axios.create({
  baseURL: API_PREFIX,
  timeout: REQUEST_TIMEOUT_MS,
  headers: { 'Content-Type': 'application/json' },
})

instance.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

instance.interceptors.response.use(
  (response) => {
    const payload = response.data as ApiResponse | undefined

    // 非统一包装的响应（文件流 / 纯文本）原样透传
    if (!payload || typeof payload !== 'object' || !('code' in payload)) {
      return payload as unknown as typeof response
    }

    if (payload.code === API_SUCCESS_CODE) {
      // 解包 data：业务层直接拿到目标结构
      return payload.data as unknown as typeof response
    }

    const info = payload.info || '请求失败'
    if (!response.config.silent) {
      message.error(info)
    }
    console.error('[http] 业务失败', response.config.url, payload.code, info)
    return Promise.reject(new BusinessError(info, payload.code))
  },
  (error: AxiosError) => {
    const silent = Boolean(error.config?.silent)
    const status = error.response?.status

    // 401：统一由 handleUnauthorized 提示与跳转，绝不叠加「网络故障」类误导性提示
    if (status === 401) {
      handleUnauthorized()
      return Promise.reject(new BusinessError('登录已过期，请重新登录', '401'))
    }

    let info: string
    if (error.code === 'ECONNABORTED') {
      info = '请求超时，请稍后重试'
    } else if (status) {
      info = `请求失败（HTTP ${status}）`
    } else {
      info = '网络异常，请确认后端服务已启动（默认 8099）'
    }

    if (!silent) {
      message.error(info)
    }
    console.error('[http] 请求异常', error.config?.url, error.message)
    return Promise.reject(new BusinessError(info, String(status ?? 'NETWORK')))
  },
)

/** 类型化请求：返回值即后端 data 字段 */
export function request<T>(config: AxiosRequestConfig): Promise<T> {
  return instance.request(config) as unknown as Promise<T>
}

export const http = {
  get<T>(url: string, params?: Record<string, unknown>, config?: AxiosRequestConfig): Promise<T> {
    return request<T>({ url, method: 'get', params, ...config })
  },
  post<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
    return request<T>({ url, method: 'post', data, ...config })
  },
  put<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
    return request<T>({ url, method: 'put', data, ...config })
  },
  delete<T>(url: string, params?: Record<string, unknown>, config?: AxiosRequestConfig): Promise<T> {
    return request<T>({ url, method: 'delete', params, ...config })
  },
}

export default instance
