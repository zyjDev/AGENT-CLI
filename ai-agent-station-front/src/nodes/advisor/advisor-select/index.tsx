import { useEffect, useState } from 'react';

import { Field } from '@flowgram.ai/free-layout-editor';
import { Select } from '@douyinfe/semi-ui';

import { AiClientAdvisorService } from '../../../services';
import type { AiClientAdvisorResponseDTO } from '../../../services/ai-client-advisor-service';
import { useIsSidebar } from '../../../hooks';
import { FormItem } from '../../../form-components';
import { AdvisorPort } from './styles';

interface AdvisorValue {
  key: string;
  value: string;
}

export function AdvisorSelect() {
  const readonly = !useIsSidebar();
  const [advisorOptions, setAdvisorOptions] = useState<{ label: string; value: string }[]>([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    // 从后端API获取顾问数据
    const fetchAdvisors = async () => {
      setLoading(true);
      try {
        const advisors: AiClientAdvisorResponseDTO[] = await AiClientAdvisorService.queryAllAiClientAdvisors();
        // 转换API数据为Select组件需要的格式，使用advisorId和advisorName
        const options = advisors.map(advisor => ({
          label: advisor.advisorName,
          value: advisor.advisorId,
        }));
        setAdvisorOptions(options);
      } catch (error) {
        console.error('获取顾问数据失败:', error);
        // 设置空选项作为降级处理
        setAdvisorOptions([]);
      } finally {
        setLoading(false);
      }
    };

    fetchAdvisors();
  }, []);

  return (
    <Field<AdvisorValue> name="inputsValues.advisorName.0">
      {({ field, fieldState }) => (
        <FormItem name="顾问角色" type="string" required={true} labelWidth={80}>
          <Select
            placeholder={loading ? "加载中..." : "请选择顾问角色"}
            style={{ width: '100%' }}
            value={field.value?.value || ''}
            onChange={(value) => field.onChange({ key: field.value?.key || '', value: String(value || '') })}
            disabled={readonly || loading}
            optionList={advisorOptions}
            loading={loading}
          />
          {/* 添加输出端口标记，使节点可以从右侧连线 */}
          <AdvisorPort data-port-id={field.value?.key} data-port-type="output" />
        </FormItem>
      )}
    </Field>
  );
}