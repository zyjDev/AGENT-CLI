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

/**
 * 列表接口的两种响应形态。
 *
 * 后端正把各模块的 query-list 从「裸数组 + 内存分页」迁到 PageResult
 * （{ list, total, pageNum, pageSize }）。迁移期间新旧接口并存，消费方必须两种都能吃下，
 * 统一走 {@link normalizePagePayload} 再使用：
 * - 裸数组：迁移前的旧接口。没有 total，只能用「本页是否取满」推断下一页。
 * - 分页对象：迁移后的新接口。total 是符合条件的真实总行数。
 */
export interface PagePayload<T> {
  list?: T[] | null
  /** 总数。后端全局把 Long 序列化成字符串（见 JacksonConfig），所以是 23 或 "23" 都可能 */
  total?: number | string
  pageNum?: number
  pageSize?: number
}

/** 把 number / 数字字符串 收敛成正整数；认不出来一律 null（不猜） */
function toCount(value: unknown): number | null {
  if (typeof value === 'number') return Number.isFinite(value) ? value : null
  if (typeof value === 'string' && value.trim() !== '') {
    const parsed = Number(value)
    return Number.isFinite(parsed) ? parsed : null
  }
  return null
}

/**
 * 把两种响应形态统一成 { list, total }。
 * total 为 null 表示后端没给（旧裸数组接口），此时不要显示「共 N 条」。
 */
export function normalizePagePayload<T>(input: T[] | PagePayload<T> | null | undefined): {
  list: T[]
  total: number | null
} {
  if (Array.isArray(input)) {
    return { list: input, total: null }
  }
  const list = input?.list
  return {
    list: Array.isArray(list) ? list : [],
    total: toCount(input?.total),
  }
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
