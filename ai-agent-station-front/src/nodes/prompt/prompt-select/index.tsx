import { useEffect, useState } from 'react';

import { Field } from '@flowgram.ai/free-layout-editor';
import { Select } from '@douyinfe/semi-ui';

import { AiClientSystemPromptService } from '../../../services';
import { AiClientSystemPromptResponseDTO } from '../../../services/ai-client-system-prompt-service';
import { useIsSidebar } from '../../../hooks';
import { FormItem } from '../../../form-components';
import { PromptPort } from './styles';

interface PromptValue {
  key: string;
  value: string;
}

export function PromptSelect() {
  const readonly = !useIsSidebar();
  const [promptOptions, setPromptOptions] = useState<{ label: string; value: string }[]>([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    const fetchPrompts = async () => {
      try {
        setLoading(true);
        const prompts = await AiClientSystemPromptService.queryAllAiClientSystemPrompts();
        // 转换API数据为Select组件需要的格式
        const options = prompts.map((prompt: AiClientSystemPromptResponseDTO) => ({
          label: prompt.promptName,
          value: prompt.promptId,
        }));
        setPromptOptions(options);
      } catch (error) {
        console.error('获取系统提示词列表失败:', error);
        setPromptOptions([]);
      } finally {
        setLoading(false);
      }
    };

    fetchPrompts();
  }, []);

  return (
    <Field<PromptValue> name="inputsValues.promptName.0">
      {({ field, fieldState }) => (
        <FormItem name="系统提示词" type="string" required={true} labelWidth={80}>
          <Select
            placeholder={loading ? "加载中..." : "请选择系统提示词"}
            style={{ width: '100%' }}
            value={field.value?.value || ''}
            onChange={(value) => field.onChange({ key: field.value?.key || '', value: String(value || '') })}
            disabled={readonly || loading}
            loading={loading}
            optionList={promptOptions}
          />
          {/* 添加输出端口标记，使节点可以从右侧连线 */}
          <PromptPort data-port-id={field.value?.key} data-port-type="output" />
        </FormItem>
      )}
    </Field>
  );
}