import { nanoid } from 'nanoid';

import { WorkflowNodeType } from '../constants';
import { FlowNodeRegistry } from '../../typings';
import iconAdvisor from '../../assets/icon-advisor.svg';
import { formMeta } from './form-meta';

let index = 0;
export const AdvisorNodeRegistry: FlowNodeRegistry = {
  type: WorkflowNodeType.Advisor,
  info: {
    icon: iconAdvisor,
    description: '顾问角色',
  },
  formMeta,
  meta: {
    defaultPorts: [{ type: 'input' }],
    // Advisor Outputs use dynamic port
    useDynamicPort: true,
    expandable: false, // disable expanded
  },
  onAdd() {
    return {
      id: `advisor_${nanoid(5)}`,
      type: 'advisor',
      data: {
        title: `Advisor_${++index}`,
        inputsValues: {
          advisorName: [
            {
              key: `advisor_select_${nanoid(6)}`,
              value: '', // 初始值为空，由用户从API数据中选择
            },
          ],
        },
        inputs: {
          type: 'object',
          properties: {
            advisorName: {
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
        outputs: {
          type: 'object',
          properties: {},
        },
      },
      position: { x: 0, y: 0 },
    };
  },
};