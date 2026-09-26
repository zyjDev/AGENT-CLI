/**
 * 后端统一响应包装。
 * 对应 cn.bugstack.ai.api.response.Response：成功码固定为 "0000"。
 */
export interface ApiResponse<T = unknown> {
  code: string
  info: string
  data: T
}

/** 后端成功码 */
export const API_SUCCESS_CODE = '0000'

/** 列表类接口的通用分页入参（具体字段以各模块为准） */
export interface PageQueryParams {
  pageNum?: number
  pageSize?: number
}

/** 业务错误：HTTP 200 但 code !== "0000" 时抛出 */
export class BusinessError extends Error {
  readonly code: string

  constructor(info: string, code: string) {
    super(info)
    this.name = 'BusinessError'
    this.code = code
  }
}
