import React, { useState, useEffect } from 'react';
import {
  Modal,
  Form,
  Input,
  Select,
  InputNumber,
  Button,
  Toast,
  TextArea
} from '@douyinfe/semi-ui';
import styled from 'styled-components';
import { theme } from '../styles/theme';
import { 
  AiClientAdvisorRequestDTO,
  aiClientAdvisorAdminService 
} from '../services/ai-client-advisor-admin-service';

const { Option } = Select;

// 样式组件
const StyledModal = styled(Modal)`
  .semi-modal-content {
    border-radius: ${theme.borderRadius.lg};
  }
`;

const FormContainer = styled.div`
  padding: ${theme.spacing.base};
`;

const FormRow = styled.div`
  display: flex;
  gap: ${theme.spacing.base};
  margin-bottom: ${theme.spacing.base};
  
  .semi-form-field {
    flex: 1;
  }
`;

const ButtonGroup = styled.div`
  display: flex;
  justify-content: flex-end;
  gap: ${theme.spacing.sm};
  margin-top: ${theme.spacing.lg};
  padding-top: ${theme.spacing.base};
  border-top: 1px solid ${theme.colors.border.secondary};
`;

const RegenerateButton = styled(Button)`
  margin-left: ${theme.spacing.sm};
`;

// 生成随机8位数字字符串
const generateRandomAdvisorId = (): string => {
  return Math.floor(10000000 + Math.random() * 90000000).toString();
};

interface AdvisorFormModalProps {
  visible: boolean;
  mode: 'create' | 'edit';
  initialData?: Partial<AiClientAdvisorRequestDTO>;
  onCancel: () => void;
  onSuccess: () => void;
}

export const AdvisorFormModal: React.FC<AdvisorFormModalProps> = ({
  visible,
  mode,
  initialData,
  onCancel,
  onSuccess,
}) => {
  const [formApi, setFormApi] = useState<any>(null);
  const [loading, setLoading] = useState(false);
  const [advisorId, setAdvisorId] = useState<string>('');

  // 初始化advisorId
  useEffect(() => {
    if (mode === 'create') {
      setAdvisorId(generateRandomAdvisorId());
    } else if (initialData?.advisorId) {
      setAdvisorId(initialData.advisorId);
    }
  }, [mode, initialData]);

  // 设置表单初始值
  useEffect(() => {
    if (formApi && visible) {
      if (mode === 'edit' && initialData) {
        formApi.setValues({
          ...initialData,
          advisorId: advisorId,
        });
      } else if (mode === 'create') {
        formApi.setValues({
          advisorId: advisorId,
          advisorName: '',
          advisorType: '',
          orderNum: 0,
          extParam: '{}',
          status: 1,
        });
      }
    }
  }, [formApi, visible, mode, initialData, advisorId]);

  // 重新生成advisorId
  const handleRegenerateId = () => {
    if (formApi) {
      const newId = generateRandomAdvisorId();
      setAdvisorId(newId);
      formApi.setValue('advisorId', newId);
    }
  };

  // 提交表单
  const handleSubmit = async () => {
    if (!formApi) return;

    try {
      const values = await formApi.validate();
      setLoading(true);

      const requestData: AiClientAdvisorRequestDTO = {
        ...values,
        advisorId: advisorId,
      };

      if (mode === 'create') {
        await aiClientAdvisorAdminService.createAdvisor(requestData);
        Toast.success('顾问创建成功');
      } else {
        await aiClientAdvisorAdminService.updateAdvisorByAdvisorId(advisorId, requestData);
        Toast.success('顾问更新成功');
      }

      onSuccess();
    } catch (error: any) {
      console.error('操作失败:', error);
      Toast.error(error.message || '操作失败，请重试');
    } finally {
      setLoading(false);
    }
  };

  // 取消操作
  const handleCancel = () => {
    if (formApi) {
      formApi.reset();
    }
    onCancel();
  };

  return (
    <StyledModal
      title={mode === 'create' ? '新增顾问' : '编辑顾问'}
      visible={visible}
      onCancel={handleCancel}
      footer={null}
      width={600}
      maskClosable={false}
    >
      <FormContainer>
        <Form
          getFormApi={(api: any) => setFormApi(api)}
          labelPosition="left"
          labelWidth={100}
        >
          <Form.Item
            field="advisorId"
            label="顾问ID"
            rules={[
              { required: true, message: '顾问ID不能为空' },
              { pattern: /^\d{8}$/, message: '顾问ID必须是8位数字' }
            ]}
          >
            <Input
              value={advisorId}
              disabled
              suffix={
                mode === 'create' && (
                  <RegenerateButton
                    type="tertiary"
                    size="small"
                    onClick={handleRegenerateId}
                  >
                    重新生成
                  </RegenerateButton>
                )
              }
            />
          </Form.Item>

          <Form.Item
            field="advisorName"
            label="顾问名称"
            rules={[
              { required: true, message: '顾问名称不能为空' },
              { max: 50, message: '顾问名称不能超过50个字符' }
            ]}
          >
            <Input placeholder="请输入顾问名称" />
          </Form.Item>

          <Form.Item
            field="advisorType"
            label="顾问类型"
            rules={[
              { required: true, message: '顾问类型不能为空' },
              { max: 30, message: '顾问类型不能超过30个字符' }
            ]}
          >
            <Select placeholder="请选择顾问类型">
              <Option value="GENERAL">通用顾问</Option>
              <Option value="TECHNICAL">技术顾问</Option>
              <Option value="BUSINESS">商务顾问</Option>
              <Option value="CUSTOMER_SERVICE">客服顾问</Option>
            </Select>
          </Form.Item>

          <Form.Item
            field="orderNum"
            label="排序号"
            rules={[
              { type: 'number', message: '排序号必须是数字' },
              { min: 0, message: '排序号不能小于0' }
            ]}
          >
            <InputNumber
              placeholder="请输入排序号"
              min={0}
              style={{ width: '100%' }}
            />
          </Form.Item>

          <Form.Item
            field="extParam"
            label="扩展参数"
            rules={[
              { required: true, message: '扩展参数不能为空' },
              { 
                validator: (rule: any, value: any) => {
                  if (!value) return Promise.resolve();
                  try {
                    JSON.parse(value);
                    return Promise.resolve();
                  } catch {
                    return Promise.reject('扩展参数必须是有效的JSON格式');
                  }
                }
              }
            ]}
          >
            <TextArea
              placeholder="请输入JSON格式的扩展参数，如：{}"
              rows={3}
              showClear
            />
          </Form.Item>

          <Form.Item
            field="status"
            label="状态"
            rules={[{ required: true, message: '状态不能为空' }]}
          >
            <Select placeholder="请选择状态">
              <Option value={1}>启用</Option>
              <Option value={0}>禁用</Option>
            </Select>
          </Form.Item>
        </Form>

        <ButtonGroup>
          <Button onClick={handleCancel}>
            取消
          </Button>
          <Button
            type="primary"
            loading={loading}
            onClick={handleSubmit}
          >
            {mode === 'create' ? '创建' : '更新'}
          </Button>
        </ButtonGroup>
      </FormContainer>
    </StyledModal>
  );
};