import { useState, useEffect, useCallback } from 'react';

import { useClientContext, getNodeForm, FlowNodeEntity } from '@flowgram.ai/free-layout-editor';
import { Button, Badge, Toast } from '@douyinfe/semi-ui';
import { API_ENDPOINTS, DEFAULT_HEADERS } from '../../config/api';
import { getUrlParam, setUrlParam } from '../../utils/url';

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

      // 编辑已有配置时必须回传 configId，否则后端一律按「新建」处理 ——
      // 症状：从列表点进某条配置、改完点保存，结果多出一条新配置，原配置纹丝不动（等于另存为副本）。
      // 这里的 configId 来自地址栏（列表页跳转时带上的 ?configId=xxx），与 editor.tsx 加载配置用的是同一个来源。
      const editingConfigId = getUrlParam('configId') ?? undefined;

      const requestData = {
        configId: editingConfigId,
        configName,
        description,
        // agentId 由后端决定：新建时生成唯一值、更新时复用原值（ai_agent_task_schedule 依赖它），
        // 这里传的值会被后端覆盖，保留仅为兼容请求结构
        agentId: '1',
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
        // 新建成功后把 configId 写回地址栏：否则下一次点保存又会新建一份，而不是更新刚创建的这一份。
        // 用 replaceState 写入，不会触发 editor.tsx 对 location.search 的监听，因此画布不会重载。
        if (!editingConfigId && result.data) {
          setUrlParam('configId', result.data);
        }
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
