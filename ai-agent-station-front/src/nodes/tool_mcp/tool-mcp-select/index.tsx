import { useEffect, useState } from 'react';

import { Field } from '@flowgram.ai/free-layout-editor';
import { Select } from '@douyinfe/semi-ui';

import { AiClientToolMcpService } from '../../../services';
import type { AiClientToolMcpResponseDTO } from '../../../services/ai-client-tool-mcp-service';
import { useIsSidebar } from '../../../hooks';
import { FormItem } from '../../../form-components';
import { ToolMcpPort } from './styles';

interface ToolMcpValue {
  key: string;
  value: string;
}

export function ToolMcpSelect() {
  const readonly = !useIsSidebar();
  const [toolMcpOptions, setToolMcpOptions] = useState<{ label: string; value: string }[]>([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    // 从后端API获取工具MCP数据
    const fetchToolMcps = async () => {
      setLoading(true);
      try {
        const toolMcps: AiClientToolMcpResponseDTO[] = await AiClientToolMcpService.queryAllAiClientToolMcps();
        // 转换API数据为Select组件需要的格式，使用mcpId和mcpName
        const options = toolMcps.map(toolMcp => ({
          label: toolMcp.mcpName,
          value: toolMcp.mcpId,
        }));
        setToolMcpOptions(options);
      } catch (error) {
        console.error('获取工具MCP数据失败:', error);
        // 设置空选项作为降级处理
        setToolMcpOptions([]);
      } finally {
        setLoading(false);
      }
    };

    fetchToolMcps();
  }, []);

  return (
    <Field<ToolMcpValue> name="inputsValues.toolMcpName.0">
      {({ field, fieldState }) => (
        <FormItem name="MCP类型" type="string" required={true} labelWidth={80}>
          <Select
            placeholder={loading ? "加载中..." : "请选择MCP类型"}
            style={{ width: '100%' }}
            value={field.value?.value || ''}
            onChange={(value) => field.onChange({ 
              key: field.value?.key || `tool_mcp_select_${Date.now()}`, 
              value: String(value || '') 
            })}
            disabled={readonly || loading}
            optionList={toolMcpOptions}
            loading={loading}
          />
          {/* 添加输出端口标记，使节点可以从右侧连线 */}
          <ToolMcpPort data-port-id={field.value?.key} data-port-type="output" />
        </FormItem>
      )}
    </Field>
  );
}