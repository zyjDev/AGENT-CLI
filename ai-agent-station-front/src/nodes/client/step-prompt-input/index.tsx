import React from 'react';
import { Field } from '@flowgram.ai/free-layout-editor';
import { TextArea } from '@douyinfe/semi-ui';

import { FormItem, Feedback } from '../../../form-components';
import { useIsSidebar } from '../../../hooks';

interface StepPromptValue {
  key: string;
  value: string;
}

export function StepPromptInput() {
  const readonly = !useIsSidebar();

  return (
    <Field<StepPromptValue> name="inputsValues.stepPrompt.0">
      {({ field, fieldState }) => (
        <FormItem name="步骤提示词" type="string">
          <TextArea
            value={field.value?.value || ''}
            onChange={(value) => {
              field.onChange({ key: field.value?.key || '', value: value || '' });
            }}
            disabled={readonly}
            style={{ width: '100%', minHeight: '120px' }}
            placeholder="请输入步骤提示词（可选）"
            rows={6}
            autosize={{ minRows: 6, maxRows: 12 }}
          />
          <Feedback errors={fieldState?.errors} />
        </FormItem>
      )}
    </Field>
  );
}