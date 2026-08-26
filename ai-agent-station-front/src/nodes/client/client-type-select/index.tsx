import { Field } from '@flowgram.ai/free-layout-editor';
import { Select } from '@douyinfe/semi-ui';

import { useIsSidebar } from '../../../hooks';
import { FormItem } from '../../../form-components';
import { CLIENT_TYPE_OPTIONS, ClientTypeValue, ClientConfigType } from '../client-types';

export function ClientTypeSelect() {
  const readonly = !useIsSidebar();

  return (
    <Field<ClientTypeValue> name="inputsValues.clientType.0">
      {({ field, fieldState }) => {
        // 处理不同的数据格式
        let displayValue = ClientConfigType.DEFAULT;
        
        if (field.value) {
          if (typeof field.value === 'object' && field.value.value) {
            // 标准格式：{key: string, value: ClientConfigType}
            displayValue = field.value.value;
          } else if (typeof field.value === 'string') {
            // 兼容格式：直接是字符串值
            displayValue = field.value as ClientConfigType;
          }
        }

        return (
          <FormItem name="客户端类型" type="string" required={true} labelWidth={80}>
            <Select
              placeholder="请选择客户端配置类型"
              style={{ width: '100%' }}
              value={displayValue}
              onChange={(value) => field.onChange({ 
                key: field.value?.key || `client_type_${Date.now()}`, 
                value: value as ClientConfigType 
              })}
              disabled={readonly}
              optionList={CLIENT_TYPE_OPTIONS}
            />
          </FormItem>
        );
      }}
    </Field>
  );
}