import { nanoid } from 'nanoid';

import { WorkflowNodeType } from '../constants';
import { FlowNodeRegistry } from '../../typings';
import iconPrompt from '../../assets/icon-prompt.svg';
import { formMeta } from './form-meta';

let index = 0;
export const PromptNodeRegistry: FlowNodeRegistry = {
  type: WorkflowNodeType.Prompt,
  info: {
    icon: iconPrompt,
    description: '系统提示词',
  },
  formMeta,
  meta: {
    defaultPorts: [{ type: 'input' }],
    // Prompt Outputs use dynamic port
    useDynamicPort: true,
    expandable: false, // disable expanded
  },
  onAdd() {
    return {
      id: `prompt_${nanoid(5)}`,
      type: 'prompt',
      data: {
        title: `Prompt_${++index}`,
        inputs: {
          type: 'object',
          properties: {
            promptName: {
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
        inputsValues: {
          promptName: [{ key: '', value: '' }],
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