import { FlowNodeRegistry } from '../typings';
import { ToolMcpNodeRegistry } from './tool_mcp';
// import { TaskNodeRegistry } from './task';
import { StartNodeRegistry } from './start';
import { PromptNodeRegistry } from './prompt';
import { ModelNodeRegistry } from './model';
// import { LoopNodeRegistry } from './loop';
// import { LLMNodeRegistry } from './llm';
import { EndNodeRegistry } from './end';
import { WorkflowNodeType } from './constants';
// import { ConditionNodeRegistry } from './condition';
import { CommentNodeRegistry } from './comment';
import { ClientNodeRegistry } from './client';
import { AgentNodeRegistry } from './agent';
import { AdvisorNodeRegistry } from './advisor';
export { WorkflowNodeType } from './constants';

export const nodeRegistries: FlowNodeRegistry[] = [
  // TaskNodeRegistry,
  AgentNodeRegistry,
  AdvisorNodeRegistry,
  PromptNodeRegistry,
  ClientNodeRegistry,
  ToolMcpNodeRegistry,
  ModelNodeRegistry,
  // ConditionNodeRegistry,
  StartNodeRegistry,
  EndNodeRegistry,
  // LLMNodeRegistry,
  // LoopNodeRegistry,
  CommentNodeRegistry,
];

export const visibleNodeRegistries = nodeRegistries.filter(
  (r) => r.type !== WorkflowNodeType.Comment
);
