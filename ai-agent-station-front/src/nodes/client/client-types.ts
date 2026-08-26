export enum ClientConfigType {
  DEFAULT = "DEFAULT",
  TASK_ANALYZER_CLIENT = "TASK_ANALYZER_CLIENT",
  PRECISION_EXECUTOR_CLIENT = "PRECISION_EXECUTOR_CLIENT",
  QUALITY_SUPERVISOR_CLIENT = "QUALITY_SUPERVISOR_CLIENT",
  RESPONSE_ASSISTANT = "RESPONSE_ASSISTANT",
  TOOL_MCP_CLIENT = "TOOL_MCP_CLIENT",
  PLANNING_CLIENT = "PLANNING_CLIENT",
  EXECUTOR_CLIENT = "EXECUTOR_CLIENT"
}

export const CLIENT_TYPE_OPTIONS = [
  { label: "通用的", value: ClientConfigType.DEFAULT },
  { label: "任务分析和状态判断", value: ClientConfigType.TASK_ANALYZER_CLIENT },
  { label: "具体任务执行", value: ClientConfigType.PRECISION_EXECUTOR_CLIENT },
  { label: "质量检查和优化", value: ClientConfigType.QUALITY_SUPERVISOR_CLIENT },
  { label: "智能响应助手", value: ClientConfigType.RESPONSE_ASSISTANT },
  { label: "工具分析", value: ClientConfigType.TOOL_MCP_CLIENT },
  { label: "任务规划", value: ClientConfigType.PLANNING_CLIENT },
  { label: "任务执行", value: ClientConfigType.EXECUTOR_CLIENT }
];

export interface ClientTypeValue {
  key: string;
  value: ClientConfigType;
}