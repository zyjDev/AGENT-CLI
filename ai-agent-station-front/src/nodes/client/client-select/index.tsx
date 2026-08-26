import { useEffect, useState } from 'react';

import { Field } from '@flowgram.ai/free-layout-editor';
import { Select } from '@douyinfe/semi-ui';

import { AiClientService } from '../../../services';
import type { AiClientResponseDTO } from '../../../services/ai-client-service';
import { useIsSidebar, useNodeRenderContext } from '../../../hooks';
import { FormItem } from '../../../form-components';
import { ClientPort } from './styles';

export function ClientSelect() {
  const readonly = !useIsSidebar();
  const nodeRender = useNodeRenderContext();
  const [clientOptions, setClientOptions] = useState<{ label: string; value: string }[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(false);

  useEffect(() => {
    // 从后端API获取客户端数据
    const fetchClients = async () => {
      setLoading(true);
      setError(false);
      try {
        const clients: AiClientResponseDTO[] = await AiClientService.queryAllAiClients();
        // 转换API数据为Select组件需要的格式，使用clientId和clientName
        const options = clients.map(client => ({
          label: client.clientName,
          value: client.clientId,
        }));
        setClientOptions(options);
      } catch (error) {
        console.error('获取客户端数据失败:', error);
        setError(true);
        // 设置空选项作为降级处理
        setClientOptions([]);
      } finally {
        setLoading(false);
      }
    };

    fetchClients();
  }, []);

  return (
    <>
      <Field<string> name="inputsValues.clientName">
        {({ field: clientNameField }) => (
          <Field<string> name="inputsValues.clientId">
            {({ field: clientIdField }) => {
              // 获取节点的原始数据
              const nodeMeta = nodeRender.node.getNodeMeta();
              const nodeInputsValues = nodeMeta?.inputsValues;
              let displayValue = '';
              let displayLabel = '';
              
              // 处理数据显示逻辑
              if (clientIdField.value && typeof clientIdField.value === 'string') {
                // 优先使用clientId字段的值
                displayValue = clientIdField.value;
                // 从clientOptions中找到对应的clientName
                const matchedClient = clientOptions.find(option => option.value === displayValue);
                if (matchedClient) {
                  displayLabel = matchedClient.label;
                  // 同步更新clientName字段
                  if (clientNameField.value !== displayLabel) {
                    clientNameField.onChange(displayLabel);
                  }
                }
              } else if (clientNameField.value && typeof clientNameField.value === 'string') {
                // 如果没有clientId但有clientName，从clientOptions中查找匹配的clientId
                displayLabel = clientNameField.value;
                const matchedClient = clientOptions.find(option => option.label === displayLabel);
                if (matchedClient) {
                  displayValue = matchedClient.value;
                  // 同步更新clientId字段
                  clientIdField.onChange(displayValue);
                }
              } else if (nodeInputsValues) {
                // 从nodeInputsValues初始化数据
                if (typeof nodeInputsValues.clientId === 'string' && nodeInputsValues.clientId) {
                  displayValue = nodeInputsValues.clientId;
                  const matchedClient = clientOptions.find(option => option.value === displayValue);
                  if (matchedClient) {
                    displayLabel = matchedClient.label;
                    // 初始化字段值
                    if (!clientIdField.value) {
                      clientIdField.onChange(displayValue);
                    }
                    if (!clientNameField.value) {
                      clientNameField.onChange(displayLabel);
                    }
                  }
                } else if (typeof nodeInputsValues.clientName === 'string' && nodeInputsValues.clientName) {
                  displayLabel = nodeInputsValues.clientName;
                  const matchedClient = clientOptions.find(option => option.label === displayLabel);
                  if (matchedClient) {
                    displayValue = matchedClient.value;
                    // 初始化字段值
                    if (!clientNameField.value) {
                      clientNameField.onChange(displayLabel);
                    }
                    if (!clientIdField.value) {
                      clientIdField.onChange(displayValue);
                    }
                  }
                }
              }

              return (
                <FormItem name="客户端" type="string" required={true} labelWidth={80}>
                  <Select
                    placeholder={
                      loading 
                        ? "加载中..." 
                        : error 
                          ? "加载失败，请刷新重试" 
                          : clientOptions.length === 0 
                            ? "暂无可用的客户端类型" 
                            : "请选择客户端类型"
                    }
                    style={{ width: '100%' }}
                    value={displayValue}
                    onChange={(value) => {
                      // 根据选中的clientId查找对应的clientName
                      const selectedClient = clientOptions.find(option => option.value === value);
                      const clientName = selectedClient?.label || '';
                      const clientId = selectedClient?.value || '';
                      
                      // 同时更新两个字段
                      clientNameField.onChange(clientName);
                      clientIdField.onChange(clientId);
                    }}
                    disabled={readonly || loading}
                    optionList={clientOptions}
                    loading={loading}
                  />
                  {/* 显示当前选中的客户端名称和ID（只读模式下） */}
                  {readonly && (displayLabel || displayValue) && (
                    <div style={{ marginTop: '4px', fontSize: '12px', color: '#666' }}>
                      {displayLabel && <div>客户端名称: {displayLabel}</div>}
                      {displayValue && <div>客户端ID: {displayValue}</div>}
                    </div>
                  )}
                  {/* 添加输出端口标记，使节点可以从右侧连线 */}
                  <ClientPort data-port-id="client-output" data-port-type="output" />
                </FormItem>
              );
            }}
          </Field>
        )}
      </Field>
    </>
  );

}