import React, { useState, useEffect } from 'react';
import {
  Modal,
  Input,
  Select,
  Button,
  Toast,
  Space,
  TextArea
} from '@douyinfe/semi-ui';
import { 
  aiClientAdminService, 
  AiClientRequestDTO,
  AiClientResponseDTO 
} from '../services/ai-client-admin-service';

interface ClientEditModalProps {
  visible: boolean;
  onCancel: () => void;
  onSuccess: () => void;
  clientData: AiClientResponseDTO | null;
}

interface FormData {
  id: number;
  clientId: string;
  clientName: string;
  description: string;
  status: number;
}

interface FormErrors {
  clientName?: string;
  description?: string;
}

export const ClientEditModal: React.FC<ClientEditModalProps> = ({
  visible,
  onCancel,
  onSuccess,
  clientData
}) => {
  const [loading, setLoading] = useState(false);
  const [formData, setFormData] = useState<FormData>({
    id: 0,
    clientId: '',
    clientName: '',
    description: '',
    status: 1
  });
  const [errors, setErrors] = useState<FormErrors>({});

  // 当弹窗打开且有客户端数据时，初始化表单
  useEffect(() => {
    if (visible && clientData) {
      setFormData({
        id: clientData.id,
        clientId: clientData.clientId,
        clientName: clientData.clientName,
        description: clientData.description || '',
        status: clientData.status
      });
      setErrors({});
    }
  }, [visible, clientData]);

  // 表单验证
  const validateForm = (): boolean => {
    const newErrors: FormErrors = {};
    
    if (!formData.clientName.trim()) {
      newErrors.clientName = '请输入客户端名称';
    } else if (formData.clientName.trim().length < 2) {
      newErrors.clientName = '客户端名称至少2个字符';
    } else if (formData.clientName.trim().length > 50) {
      newErrors.clientName = '客户端名称不能超过50个字符';
    }

    if (formData.description && formData.description.length > 200) {
      newErrors.description = '描述不能超过200个字符';
    }

    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  // 处理表单提交
  const handleSubmit = async () => {
    if (!validateForm()) {
      return;
    }

    setLoading(true);
    try {
      const request: AiClientRequestDTO = {
        id: formData.id,
        clientId: formData.clientId,
        clientName: formData.clientName.trim(),
        description: formData.description.trim() || '',
        status: formData.status
      };

      const result = await aiClientAdminService.updateClientById(request);
      
      if (result.code === '0000' && result.data) {
        Toast.success('客户端更新成功');
        handleReset();
        onSuccess();
      } else {
        throw new Error(result.info || '更新失败');
      }
    } catch (error) {
      console.error('更新客户端失败:', error);
      Toast.error('更新失败，请检查网络连接或稍后重试');
    } finally {
      setLoading(false);
    }
  };

  // 重置表单
  const handleReset = () => {
    setFormData({
      id: 0,
      clientId: '',
      clientName: '',
      description: '',
      status: 1
    });
    setErrors({});
  };

  // 处理取消
  const handleCancel = () => {
    handleReset();
    onCancel();
  };

  return (
    <Modal
      title="编辑客户端"
      visible={visible}
      onCancel={handleCancel}
      footer={null}
      width={500}
      maskClosable={false}
    >
      <div style={{ padding: '20px 0' }}>
        <div style={{ marginBottom: '16px' }}>
          <div style={{ marginBottom: '8px', display: 'flex', alignItems: 'center' }}>
            <span style={{ width: '100px', textAlign: 'right', marginRight: '12px' }}>
              客户端ID:
            </span>
            <Input
               value={formData.clientId}
               disabled
               style={{ flex: 1 }}
             />
          </div>
        </div>

        <div style={{ marginBottom: '16px' }}>
          <div style={{ marginBottom: '8px', display: 'flex', alignItems: 'center' }}>
            <span style={{ width: '100px', textAlign: 'right', marginRight: '12px' }}>
              客户端名称<span style={{ color: 'red' }}>*</span>:
            </span>
            <Input
               placeholder="请输入客户端名称"
               value={formData.clientName}
               onChange={(value: string) => setFormData(prev => ({ ...prev, clientName: value }))}
               style={{ flex: 1 }}
             />
          </div>
          {errors.clientName && (
            <div style={{ marginLeft: '112px', color: 'red', fontSize: '12px' }}>
              {errors.clientName}
            </div>
          )}
        </div>

        <div style={{ marginBottom: '16px' }}>
          <div style={{ marginBottom: '8px', display: 'flex', alignItems: 'flex-start' }}>
            <span style={{ width: '100px', textAlign: 'right', marginRight: '12px', paddingTop: '6px' }}>
              描述:
            </span>
            <TextArea
               placeholder="请输入客户端描述（可选）"
               value={formData.description}
               onChange={(value: string) => setFormData(prev => ({ ...prev, description: value }))}
               maxCount={200}
               rows={3}
               style={{ flex: 1 }}
             />
          </div>
          {errors.description && (
            <div style={{ marginLeft: '112px', color: 'red', fontSize: '12px' }}>
              {errors.description}
            </div>
          )}
        </div>

        <div style={{ marginBottom: '16px' }}>
          <div style={{ display: 'flex', alignItems: 'center' }}>
            <span style={{ width: '100px', textAlign: 'right', marginRight: '12px' }}>
              状态<span style={{ color: 'red' }}>*</span>:
            </span>
            <Select
              placeholder="请选择状态"
              value={formData.status}
              onChange={(value) => setFormData(prev => ({ ...prev, status: value as number }))}
              style={{ flex: 1 }}
            >
              <Select.Option value={1}>启用</Select.Option>
              <Select.Option value={0}>禁用</Select.Option>
            </Select>
          </div>
        </div>

        <div style={{ textAlign: 'right', marginTop: '20px' }}>
          <Space>
            <Button onClick={handleCancel}>
              取消
            </Button>
            <Button 
              type="primary" 
              onClick={handleSubmit}
              loading={loading}
            >
              保存
            </Button>
          </Space>
        </div>
      </div>
    </Modal>
  );
};