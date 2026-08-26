import { FormRenderProps, FormMeta, ValidateTrigger } from '@flowgram.ai/free-layout-editor';

import { FlowNodeJSON } from '../../typings';
import { FormHeader, FormContent } from '../../form-components';
import { AgentSelect } from './agent-select';

export const renderForm = ({ form }: FormRenderProps<FlowNodeJSON>) => (
  <>
    <FormHeader />
    <FormContent>
      <AgentSelect />
    </FormContent>
  </>
);

export const formMeta: FormMeta<FlowNodeJSON> = {
  render: renderForm,
  validateTrigger: ValidateTrigger.onChange,
  validate: {
    title: ({ value }: { value: string }) => (value ? undefined : 'Title is required'),
    'inputsValues.agentName.*': ({ value }) => {
      if (!value?.value?.content) return 'Agent名称是必填项';
      return undefined;
    },
    'inputsValues.description.*': ({ value }) => {
      if (!value?.value?.content) return '描述是必填项';
      return undefined;
    },
    'inputsValues.channel': ({ value }) => {
      if (!value || value.trim() === '') return '渠道是必选项';
      return undefined;
    },
    'inputsValues.strategy': ({ value }) => {
      if (!value || value.trim() === '') return '策略是必选项';
      return undefined;
    },
  },
};
