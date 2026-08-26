import { FlowDocumentJSON } from './typings';

export const initialData: FlowDocumentJSON = {
  nodes: [
    {
      id: "start_0",
      type: "start",
      meta: {
        position: {
          x: -842,
          y: 39.5
        }
      },
      data: {
        title: "Start",
        outputs: {
          type: "object",
          required: []
        }
      }
    },
    {
      id: "agent_QyqMj",
      type: "agent",
      meta: {
        position: {
          x: -444,
          y: 39.5
        }
      },
      data: {
        title: "Agent_1",
        inputsValues: {
          agentName: "智能体名称",
          description: "智能体描述",
          channel: "agent",
          strategy: "flowAgentExecuteStrategy"
        },
        inputs: {
          type: "object",
          properties: {
            agentName: {
              type: "array",
              items: {
                type: "object",
                properties: {
                  key: {
                    type: "string"
                  },
                  value: {
                    type: "string"
                  }
                }
              }
            }
          }
        },
        outputs: {
          type: "object",
          properties: {
            result: {
              type: "string"
            }
          }
        }
      }
    }
  ],
  edges: [
    {
      sourceNodeID: "start_0",
      targetNodeID: "agent_QyqMj"
    }
  ]
};
