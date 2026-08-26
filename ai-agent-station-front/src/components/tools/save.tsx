import { useState, useEffect, useCallback } from 'react';

import { useClientContext, getNodeForm, FlowNodeEntity } from '@flowgram.ai/free-layout-editor';
import { Button, Badge, Toast } from '@douyinfe/semi-ui';
import { API_ENDPOINTS, DEFAULT_HEADERS } from '../../config/api';

export function Save(props: { disabled: boolean }) {
  const [errorCount, setErrorCount] = useState(0);
  const clientContext = useClientContext();

  const updateValidateData = useCallback(() => {
    const allForms = clientContext.document.getAllNodes().map((node) => getNodeForm(node));
    const count = allForms.filter((form) => form?.state.invalid).length;
    setErrorCount(count);
  }, [clientContext]);

  /**
   * 调用后端API保存流程图配置
   */
  const saveToBackend = async (configData: any) => {
    try {
      const getAgentInfoFromConfig = (config: any) => {
        let name = `流程图配置_${new Date().toLocaleString()}`;
        let desc = '通过前端拖拽生成的流程图配置';
        if (config?.nodes && Array.isArray(config.nodes)) {
          const agentNode = config.nodes.find(
            (n: any) => n?.type === 'agent' && n?.data && n?.data.inputsValues
          );
          if (agentNode) {
            const inputsValues = agentNode.data.inputsValues;
            const agentNameVal = inputsValues?.agentName;
            if (Array.isArray(agentNameVal) && agentNameVal.length > 0) {
              const first = agentNameVal[0];
              if (typeof first === 'string') name = first;
              else if (typeof first?.value === 'string') name = first.value;
              else if (typeof first?.value?.content === 'string') name = first.value.content;
              else if (typeof first?.content === 'string') name = first.content;
            } else if (typeof agentNameVal === 'string') {
              name = agentNameVal;
            }

            const descVal = inputsValues?.description;
            if (Array.isArray(descVal) && descVal.length > 0) {
              const first2 = descVal[0];
              if (typeof first2 === 'string') desc = first2;
              else if (typeof first2?.value === 'string') desc = first2.value;
              else if (typeof first2?.value?.content === 'string') desc = first2.value.content;
              else if (typeof first2?.content === 'string') desc = first2.content;
            } else if (typeof descVal === 'string') {
              desc = descVal;
            }
          }
        }
        return { name, desc };
      };

      const { name: configName, desc: description } = getAgentInfoFromConfig(configData);
      const requestData = {
        configName,
        description,
        agentId: '1', // 默认agentId，可以根据实际需求修改
        configData: JSON.stringify(configData),
        createBy: 'system',
        updateBy: 'system'
      };

      const response = await fetch(`${API_ENDPOINTS.AI_AGENT_DRAW.BASE}${API_ENDPOINTS.AI_AGENT_DRAW.SAVE_CONFIG}`, {
        method: 'POST',
        headers: DEFAULT_HEADERS,
        body: JSON.stringify(requestData),
      });

      const result = await response.json();

      if (result.code === '0000') {
        Toast.success(`保存成功！配置ID: ${result.data}`);
        return result.data;
      } else {
        Toast.error(`保存失败: ${result.info}`);
        throw new Error(result.info);
      }
    } catch (error) {
      console.error('保存流程图配置失败:', error);
      Toast.error('保存失败，请检查网络连接');
      throw error;
    }
  };

  /**
   * 提取客户端信息并添加到配置数据中
   */
  const extractClientInfo = (configData: any) => {
    // 遍历所有节点，查找client类型的节点
    if (configData.nodes) {
      configData.nodes.forEach((node: any) => {
        if (node.type === 'client' && node.data && node.data.inputsValues) {
          const inputsValues = node.data.inputsValues;

          // clientName现在是字符串格式，clientId应该已经在组件中设置到inputsValues中
          if (inputsValues.clientName && typeof inputsValues.clientName === 'string') {
            // 如果clientId已经存在，保持不变；如果不存在，尝试从clientName推断
            if (!inputsValues.clientId) {
              console.warn('clientId为空，可能是选择组件没有正确设置clientId');
              // 这里可以添加降级处理逻辑，比如使用clientName作为临时ID
              inputsValues.clientId = '';
            }
            console.info('Client info:', {
              clientName: inputsValues.clientName,
              clientId: inputsValues.clientId
            });
          }
        }
      });
    }

    return configData;
  };

  /**
   * Validate all node and Save
   */
  const onSave = useCallback(async () => {
    try {
      const allForms = clientContext.document.getAllNodes().map((node) => getNodeForm(node));
      await Promise.all(allForms.map(async (form) => form?.validate()));

      const originalConfigData = clientContext.document.toJSON();

      // 提取客户端信息并添加到配置数据中
      const configData = extractClientInfo(originalConfigData);

      console.log('>>>>> save data: ', configData);

      // 调用后端API保存配置
      await saveToBackend(configData);
    } catch (error) {
      console.error('保存过程中发生错误:', error);
    }
  }, [clientContext]);

  /**
   * Listen single node validate
   */
  useEffect(() => {
    const listenSingleNodeValidate = (node: FlowNodeEntity) => {
      const form = getNodeForm(node);
      if (form) {
        const formValidateDispose = form.onValidate(() => updateValidateData());
        node.onDispose(() => formValidateDispose.dispose());
      }
    };
    clientContext.document.getAllNodes().map((node) => listenSingleNodeValidate(node));
    const dispose = clientContext.document.onNodeCreate(({ node }) =>
      listenSingleNodeValidate(node)
    );
    return () => dispose.dispose();
  }, [clientContext]);

  if (errorCount === 0) {
    return (
      <Button
        disabled={props.disabled}
        onClick={onSave}
        style={{ backgroundColor: 'rgba(171,181,255,0.3)', borderRadius: '8px' }}
      >
        Save
      </Button>
    );
  }
  return (
    <Badge count={errorCount} position="rightTop" type="danger">
      <Button
        type="danger"
        disabled={props.disabled}
        onClick={onSave}
        style={{ backgroundColor: 'rgba(255, 179, 171, 0.3)', borderRadius: '8px' }}
      >
        Save
      </Button>
    </Badge>
  );
}
