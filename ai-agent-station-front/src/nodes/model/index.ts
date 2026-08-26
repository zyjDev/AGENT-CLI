import { nanoid } from 'nanoid';

import { WorkflowNodeType } from '../constants';
import { FlowNodeRegistry } from '../../typings';
import iconClient from '../../assets/icon-client.jpg'; // 暂时使用客户端图标
import { formMeta } from './form-meta';

let index = 0;
export const ModelNodeRegistry: FlowNodeRegistry = {
  type: WorkflowNodeType.Model,
  info: {
    icon: iconClient,
    description: '模型',
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
      id: `model_${nanoid(5)}`,
      type: 'model',
      data: {
        title: `Model_${++index}`,
        inputsValues: {
          modelName: [
            {
              key: `model_select_${nanoid(6)}`,
              value: '', // 默认为空，显示"请选择模型"
            },
          ],
        },
        inputs: {
          type: 'object',
          properties: {
            modelName: {
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