import { nanoid } from 'nanoid';

import { WorkflowNodeType } from '../constants';
import { FlowNodeRegistry } from '../../typings';
import iconAgent from '../../assets/icon-agent.jpg';
import { formMeta } from './form-meta';
// import agents from './agents.json';

let index = 0;
export const AgentNodeRegistry: FlowNodeRegistry = {
  type: WorkflowNodeType.Agent,
  info: {
    icon: iconAgent,
    description: '智能体',
  },
  formMeta,
  meta: {
    defaultPorts: [{ type: 'input' }],
    // Condition Outputs use dynamic port
    useDynamicPort: true,
    expandable: false, // disable expanded
  },
  onAdd() {
    return {
      id: `agent_${nanoid(5)}`,
      type: WorkflowNodeType.Agent,
      data: {
        title: `Agent_${++index}`,
        inputsValues: {
          agentName: '',
          description: '',
          channel: '',
          strategy: '',
        },
        inputs: {
          type: 'object',
          properties: {
            agentName: {
              type: 'string',
            },
            description: {
              type: 'string',
            },
            channel: {
              type: 'string',
            },
            strategy: {
              type: 'string',
            },
          },
        },
      },
    };
  },
};
