import { Field } from '@flowgram.ai/free-layout-editor';
import { Input, Select, TextArea } from '@douyinfe/semi-ui';

import { useIsSidebar } from '../../../hooks';
import { FormItem } from '../../../form-components';
import { AgentPort } from './styles';

interface AgentValue {
  key: string;
  value: {
    content: string;
  };
}

interface AgentFormData {
  agentName: AgentValue;
  description: AgentValue;
  channel: string;
  strategy: string;
}

// 渠道选项
const channelOptions = [
  { label: 'Agent', value: 'agent' },
  { label: 'Chat Stream', value: 'chat_stream' },
];

// 策略选项
const strategyOptions = [
  { label: 'Flow Agent Execute Strategy', value: 'flowAgentExecuteStrategy' },
  { label: 'Auto Agent Execute Strategy', value: 'autoAgentExecuteStrategy' },
  { label: 'Fixed Agent Execute Strategy', value: 'fixedAgentExecuteStrategy' },
];

export function AgentSelect() {
  const readonly = !useIsSidebar();

  return (
    <div>
      {/* Agent名称 */}
      <Field<AgentValue> name="inputsValues.agentName.0">
        {({ field, fieldState }) => (
          <FormItem name="Agent名称" type="string" required={true} labelWidth={80}>
            <Input
              placeholder="请输入Agent名称"
              style={{ width: '100%' }}
              value={field.value?.value?.content || ''}
              onChange={(value) => field.onChange({ 
                key: field.value?.key || '', 
                value: { content: String(value || '') } 
              })}
              disabled={readonly}
            />
          </FormItem>
        )}
      </Field>

      {/* Agent描述 */}
      <Field<AgentValue> name="inputsValues.description.0">
        {({ field, fieldState }) => (
          <FormItem name="描述" type="string" required={true} labelWidth={80}>
            <TextArea
              placeholder="请输入Agent描述"
              style={{ width: '100%' }}
              rows={3}
              value={field.value?.value?.content || ''}
              onChange={(value) => field.onChange({ 
                key: field.value?.key || '', 
                value: { content: String(value || '') } 
              })}
              disabled={readonly}
            />
          </FormItem>
        )}
      </Field>

      {/* 渠道选择 */}
      <Field<string> name="inputsValues.channel">
        {({ field, fieldState }) => (
          <FormItem name="渠道" type="string" required={true} labelWidth={80}>
            <Select
              placeholder="请选择渠道"
              style={{ width: '100%' }}
              value={field.value || ''}
              onChange={(value) => field.onChange(String(value || ''))}
              disabled={readonly}
              optionList={channelOptions}
            />
          </FormItem>
        )}
      </Field>

      {/* 策略选择 */}
      <Field<string> name="inputsValues.strategy">
        {({ field, fieldState }) => (
          <FormItem name="策略" type="string" required={true} labelWidth={80}>
            <Select
              placeholder="请选择策略"
              style={{ width: '100%' }}
              value={field.value || ''}
              onChange={(value) => field.onChange(String(value || ''))}
              disabled={readonly}
              optionList={strategyOptions}
            />
            {/* 添加输出端口标记，使节点可以从右侧连线 */}
            <AgentPort data-port-id="agent_output" data-port-type="output" />
          </FormItem>
        )}
      </Field>
    </div>
  );
}