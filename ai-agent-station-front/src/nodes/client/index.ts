import { nanoid } from 'nanoid';

import { WorkflowNodeType } from '../constants';
import { FlowNodeRegistry } from '../../typings';
import iconClient from '../../assets/icon-client.jpg';
import { formMeta } from './form-meta';
import { ClientConfigType } from './client-types';

let index = 0;
export const ClientNodeRegistry: FlowNodeRegistry = {
  type: WorkflowNodeType.Client,
  info: {
    icon: iconClient,
    description: '客户端',
  },
  meta: {
    defaultPorts: [{ type: 'input' }],
    // Condition Outputs use dynamic port
    useDynamicPort: true,
    expandable: false, // disable expanded
  },
  formMeta,
  onAdd() {
    return {
      id: `client_${nanoid(5)}`,
      type: WorkflowNodeType.Client,
      data: {
        title: `Client_${++index}`,
        inputsValues: {
          clientType: [
            {
              key: `client_type_${nanoid(6)}`,
              value: ClientConfigType.DEFAULT, // 默认值为DEFAULT
            },
          ],
          clientId: '', // 客户端ID，用于唯一标识客户端
          clientName: '', // 直接字符串格式，与实际JSON数据保持一致
          sequence: [
            {
              key: `sequence_${nanoid(6)}`,
              value: 1, // 默认执行序号为1
            },
          ],
          stepPrompt: [
            {
              key: `step_prompt_${nanoid(6)}`,
              value: '', // 默认为空，用户可选填
            },
          ],
        },
        inputs: {
          type: 'object',
          properties: {
            clientType: {
              type: 'array',
              items: {
                type: 'object',
                properties: {
                  key: {
                    type: 'string',
                  },
                  value: {
                    type: 'string',
                  },
                },
              },
            },
            clientId: {
              type: 'string', // 客户端ID字符串类型
            },
            clientName: {
              type: 'string', // 直接字符串类型
            },
            sequence: {
              type: 'array',
              items: {
                type: 'object',
                properties: {
                  key: {
                    type: 'string',
                  },
                  value: {
                    type: 'number',
                  },
                },
              },
            },
            stepPrompt: {
              type: 'array',
              items: {
                type: 'object',
                properties: {
                  key: {
                    type: 'string',
                  },
                  value: {
                    type: 'string',
                  },
                },
              },
            },
          },
        },
      },
    };
  },
}