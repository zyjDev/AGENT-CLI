/**
 * Auto Agent SSE 消息协议枚举。
 *
 * 主类型来自根 README 的协议表；`progress` 是实测补充 —— 真实流里大量出现
 * 由 NodeTraceNotifier 发出的「节点治理事件」（node_start / node_timeout / node_retry / node_degrade），
 * README 未列出该类型，未收录时会全部落到兜底文案，排查问题时看不出严重度。
 */

/** 主类型 */
export enum SseMessageType {
  /** 分析阶段 */
  Analysis = 'analysis',
  /** 执行阶段 */
  Execution = 'execution',
  /** 监督阶段 */
  Supervision = 'supervision',
  /** 总结阶段 */
  Summary = 'summary',
  /** 错误信息 */
  Error = 'error',
  /** 任务完成标识 */
  Complete = 'complete',
  /** 节点治理 / 进度事件（实测补充） */
  Progress = 'progress',
}

/** 子类型 */
export enum SseSubType {
  AnalysisStatus = 'analysis_status',
  AnalysisHistory = 'analysis_history',
  AnalysisStrategy = 'analysis_strategy',
  AnalysisProgress = 'analysis_progress',
  ExecutionTarget = 'execution_target',
  ExecutionProcess = 'execution_process',
  ExecutionResult = 'execution_result',
  ExecutionQuality = 'execution_quality',
  Assessment = 'assessment',
  Issues = 'issues',
  Suggestions = 'suggestions',
  Score = 'score',
  Pass = 'pass',
  CompletedWork = 'completed_work',
  IncompleteReasons = 'incomplete_reasons',
  Evaluation = 'evaluation',
  SummaryOverview = 'summary_overview',

  /* ---- 节点治理事件（NodeTraceNotifier 实测输出） ---- */
  NodeStart = 'node_start',
  NodeEnd = 'node_end',
  NodeSuccess = 'node_success',
  NodeFail = 'node_fail',
  NodeTimeout = 'node_timeout',
  NodeRetry = 'node_retry',
  NodeDegrade = 'node_degrade',
}

/** 阶段展示元信息：中文名 + 样式类，供过程面板直接绑定 */
export interface StageMeta {
  label: string
  className: string
}

const STAGE_META: Record<SseMessageType, StageMeta> = {
  [SseMessageType.Analysis]: { label: '分析阶段', className: 'stage-analysis' },
  [SseMessageType.Execution]: { label: '执行阶段', className: 'stage-execution' },
  [SseMessageType.Supervision]: { label: '监督阶段', className: 'stage-supervision' },
  [SseMessageType.Summary]: { label: '总结阶段', className: 'stage-summary' },
  [SseMessageType.Error]: { label: '异常', className: 'stage-error' },
  [SseMessageType.Complete]: { label: '完成', className: 'stage-summary' },
  [SseMessageType.Progress]: { label: '执行进度', className: 'stage-execution' },
}

/**
 * 子类型级别的覆盖：治理事件需要按严重度区分颜色，
 * 否则「超时 / 降级」会被渲染成和「开始执行」一样的普通进度，问题被淹没。
 */
const SUBTYPE_META: Partial<Record<SseSubType, StageMeta>> = {
  [SseSubType.NodeStart]: { label: '节点开始', className: 'stage-analysis' },
  [SseSubType.NodeEnd]: { label: '节点结束', className: 'stage-analysis' },
  [SseSubType.NodeSuccess]: { label: '节点成功', className: 'stage-summary' },
  [SseSubType.NodeRetry]: { label: '节点重试', className: 'stage-supervision' },
  [SseSubType.NodeTimeout]: { label: '节点超时', className: 'stage-error' },
  [SseSubType.NodeDegrade]: { label: '节点降级', className: 'stage-error' },
  [SseSubType.NodeFail]: { label: '节点失败', className: 'stage-error' },
}

/** 未知类型兜底为「信息」，避免后端新增枚举时前端渲染空白 */
const UNKNOWN_STAGE_META: StageMeta = { label: '信息', className: 'stage-analysis' }

export function resolveStageMeta(type?: string, subType?: string | null): StageMeta {
  const sub = subType ? SUBTYPE_META[subType as SseSubType] : undefined
  if (sub) return sub
  if (!type) return UNKNOWN_STAGE_META
  return STAGE_META[type as SseMessageType] ?? UNKNOWN_STAGE_META
}

/** 一条 SSE 消息 */
export interface SseMessage {
  type: string
  subType: string | null
  step: number | null
  content: string
  completed: boolean
  timestamp: number
  sessionId: string
}
