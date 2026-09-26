/**
 * 管理端列表页的共享描述类型。
 *
 * 背景：后端 10 个资源模块的接口风格完全一致
 * （POST query-list / POST create / PUT update-by-id / DELETE delete-by-id/{id}），
 * 差异只在「查询字段、表格列、表单字段」。
 * 因此把差异收敛为一份 descriptor，由 CrudPage 统一承载交互范式。
 */

export type FieldType = 'input' | 'password' | 'textarea' | 'number' | 'select'

export interface FieldOption {
  label: string
  value: string | number
}

export interface FormField {
  name: string
  label: string
  type: FieldType
  required?: boolean
  placeholder?: string
  options?: FieldOption[]
  /** 编辑态禁用（如业务主键） */
  disabledOnEdit?: boolean
  /** 始终禁用（如前端自动生成的业务 ID） */
  disabled?: boolean
  rows?: number
  /** 数字输入的范围 */
  min?: number
  max?: number
  extra?: string
  /** 查询区控件宽度（px） */
  width?: number
  /** 弹窗中占据整行（默认两列布局） */
  full?: boolean
}

export type CellRender = 'text' | 'mono' | 'status' | 'time' | 'truncate'

export interface ColumnDef {
  title: string
  dataIndex: string
  width?: number
  render?: CellRender
  /** 列宽自适应（占剩余空间） */
  flexible?: boolean
}

/** 行内附加操作（如 agent-list 的装配、api 管理的加载） */
export interface RowAction<T = CrudRow> {
  label: string
  danger?: boolean
  run(record: T): Promise<void> | void
}

/**
 * 表格行类型。
 * 用 `Record<string, any>` 而不是 `unknown`：TS 允许任意 interface 赋给它（无需索引签名），
 * 而 `Record<string, unknown>` 会要求每个业务 DTO 都加索引签名，徒增噪音。
 */
export type CrudRow = Record<string, any>

/**
 * 用「方法简写」而不是「属性 + 函数类型」声明：
 * 方法参数是双变的，这样 `CrudApi<具体DTO>` 才能赋给 `CrudApi<CrudRow>`。
 */
export interface CrudApi<T = CrudRow> {
  query(payload: Record<string, unknown>): Promise<T[]>
  create?(payload: Record<string, unknown>): Promise<unknown>
  update?(payload: Record<string, unknown>): Promise<unknown>
  remove?(record: T): Promise<unknown>
}

export interface CrudDescriptor<T = CrudRow> {
  /** 页面与卡片标题 */
  title: string
  /** 一句话说明（用来解释该模块的用途） */
  description?: string
  /** 表格 rowKey 字段（后端响应里的唯一标识字段名） */
  rowKey: string
  queryFields: FormField[]
  columns: ColumnDef[]
  formFields: FormField[]
  api: CrudApi<T>
  /** 新增时自动生成的业务 ID 字段名（如 clientId / apiId / advisorId / mcpId） */
  generateIdField?: string
  /** 只读模块：隐藏新增/编辑/删除（如 agent-list 由编排页创建） */
  readonly?: boolean
  rowActions?: RowAction<T>[]
  /** 表格上方提示 */
  notice?: string
}
