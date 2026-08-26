import { nanoid } from 'nanoid';

import { WorkflowNodeType } from '../constants';
import { FlowNodeRegistry } from '../../typings';
import iconTask from '../../assets/icon-task.jpg';

let index = 0;
export const TaskNodeRegistry: FlowNodeRegistry = {
  type: WorkflowNodeType.Task,
  info: {
    icon: iconTask,
    description: '大模型任务调度配置',
  },
  meta: {
    size: {
      width: 360,
      height: 305,
    },
  },
  onAdd() {
    return {
      id: `task_${nanoid(5)}`,
      type: 'task',
      data: {
        title: `Task_${++index}`,
        inputsValues: {
          taskName: '任务名称',
          description: '任务描述',
          cronExpression: '* * * * * * ?',
          taskParam: '请求参数',
        },
        inputs: {
          type: 'object',
          required: ['taskName', 'description', 'cronExpression', 'taskParam'],
          properties: {
            taskName: {
              type: 'string',
            },
            description: {
              type: 'string',
            },
            cronExpression: {
              type: 'string',
            },
            taskParam: {
              type: 'string',
            },
          },
        },
        outputs: {
          type: 'object',
          properties: {
            result: { type: 'string' },
          },
        },
      },
    };
  },
};
