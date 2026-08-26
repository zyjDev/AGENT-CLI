import { FormRenderProps, FormMeta, ValidateTrigger } from '@flowgram.ai/free-layout-editor';

import { FlowNodeJSON } from '../../typings';
import { FormHeader, FormContent } from '../../form-components';
import { ClientSelect } from './client-select';
import { ClientTypeSelect } from './client-type-select';
import { SequenceInput } from './sequence-input';
import { StepPromptInput } from './step-prompt-input';

export const renderForm = ({ form }: FormRenderProps<FlowNodeJSON>) => (
  <>
    <FormHeader />
    <FormContent>
      <ClientTypeSelect />
      <ClientSelect />
      <SequenceInput />
      <StepPromptInput />
    </FormContent>
  </>
);

export const formMeta: FormMeta<FlowNodeJSON> = {
  render: renderForm,
  validateTrigger: ValidateTrigger.onChange,
  validate: {
    title: ({ value }: { value: string }) => (value ? undefined : 'Title is required'),
    'inputsValues.clientType.*': ({ value }) => {
      if (!value?.value) return '请选择客户端配置类型';
      return undefined;
    },
    'inputsValues.clientName.*': ({ value }) => {
      if (!value?.value) return '请选择客户端类型';
      return undefined;
    },
    'inputsValues.sequence.*': ({ value }) => {
      if (!value?.value || value.value < 1) return '执行序号必须大于等于1';
      return undefined;
    },
    // stepPrompt 是可选字段，不需要验证
  },
};