import React from 'react';
import { Field } from '@flowgram.ai/free-layout-editor';
import { InputNumber } from '@douyinfe/semi-ui';

import { FormItem, Feedback } from '../../../form-components';
import { useIsSidebar } from '../../../hooks';

interface SequenceValue {
  key: string;
  value: number;
}

export function SequenceInput() {
  const readonly = !useIsSidebar();

  return (
    <Field<SequenceValue> name="inputsValues.sequence.0">
      {({ field, fieldState }) => (
        <FormItem name="执行序号" type="number" required>
          <InputNumber
            value={field.value?.value || 1}
            onChange={(value) => {
              field.onChange({ key: field.value?.key || '', value: Number(value) || 1 });
            }}
            disabled={readonly}
            min={1}
            step={1}
            style={{ width: '100%' }}
            placeholder="请输入执行序号"
          />
          <Feedback errors={fieldState?.errors} />
        </FormItem>
      )}
    </Field>
  );
}