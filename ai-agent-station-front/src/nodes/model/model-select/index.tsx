import { useEffect, useState } from 'react';

import { Field } from '@flowgram.ai/free-layout-editor';
import { Select } from '@douyinfe/semi-ui';

import { AiClientModelService } from '../../../services';
import type { AiClientModelResponseDTO } from '../../../services/ai-client-model-service';
import { useIsSidebar } from '../../../hooks';
import { FormItem } from '../../../form-components';
import { ModelPort } from './styles';

interface ModelValue {
  key: string;
  value: string;
}

export function ModelSelect() {
  const readonly = !useIsSidebar();
  const [modelOptions, setModelOptions] = useState<{ label: string; value: string; disabled?: boolean }[]>([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    // 从后端API获取模型数据
    const fetchModels = async () => {
      setLoading(true);
      try {
        const models: AiClientModelResponseDTO[] = await AiClientModelService.queryEnabledAiClientModels();
        // 转换API数据为Select组件需要的格式，使用modelId和modelUsage
        const options = [
          { label: '请选择模型', value: '', disabled: true }, // 添加默认提示选项
          ...models.map(model => ({
            label: model.modelUsage,
            value: model.modelId,
          }))
        ];
        setModelOptions(options);
      } catch (error) {
        console.error('获取模型数据失败:', error);
        // 设置默认提示选项作为降级处理
        setModelOptions([{ label: '请选择模型', value: '', disabled: true }]);
      } finally {
        setLoading(false);
      }
    };

    fetchModels();
  }, []);

  return (
    <Field<ModelValue> name="inputsValues.modelName.0">
      {({ field, fieldState }) => (
        <FormItem name="模型" type="string" required={true} labelWidth={80}>
          <Select
            placeholder={loading ? "加载中..." : "请选择模型"}
            style={{ width: '100%' }}
            value={field.value?.value || ''}
            onChange={(value) => {
              // 如果选择的是默认提示选项，则不设置值
              if (value === '') return;
              field.onChange({ key: field.value?.key || '', value: String(value || '') });
            }}
            disabled={readonly || loading}
            optionList={modelOptions}
            loading={loading}
            showClear={true}
          />
          {/* 添加输出端口标记，使节点可以从右侧连线 */}
          <ModelPort data-port-id={field.value?.key} data-port-type="output" />
        </FormItem>
      )}
    </Field>
  );
}