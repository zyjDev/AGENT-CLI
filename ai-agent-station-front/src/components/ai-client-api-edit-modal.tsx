import React, { useState, useEffect } from 'react';
import {
  Modal,
  Input,
  Select,
  Button,
  Toast,
  Space
} from '@douyinfe/semi-ui';
import { 
  aiClientApiAdminService, 
  AiClientApiRequestDTO,
  AiClientApiResponseDTO 
} from '../services/ai-client-api-admin-service';

interface AiClientApiEditModalProps {
  visible: boolean;
  editingRecord: AiClientApiResponseDTO | null;
  onCancel: () => void;
  onSuccess: () => void;
}

interface FormData {
  baseUrl: string;
  apiKey: string;
  completionsPath: string;
  embeddingsPath: string;
  status: number;
}

interface FormErrors {
  baseUrl?: string;
  apiKey?: string;
  completionsPath?: string;
  embeddingsPath?: string;
}

export const AiClientApiEditModal: React.FC<AiClientApiEditModalProps> = ({
  visible,
  editingRecord,
  onCancel,
  onSuccess
}) => {
  const [loading, setLoading] = useState(false);
  const [formData, setFormData] = useState<FormData>({
    baseUrl: '',
    apiKey: '',
    completionsPath: '',
    embeddingsPath: '',
    status: 1
  });
  const [errors, setErrors] = useState<FormErrors>({});

  // 当编辑记录变化时，更新表单数据
  useEffect(() => {
    if (editingRecord) {
      setFormData({
        baseUrl: editingRecord.baseUrl || '',
        apiKey: editingRecord.apiKey || '',
        completionsPath: editingRecord.completionsPath || '',
        embeddingsPath: editingRecord.embeddingsPath || '',
        status: editingRecord.status
      });
    }
  }, [editingRecord]);

  // 表单验证
  const validateForm = (): boolean => {
    const newErrors: FormErrors = {};
    
    if (!formData.baseUrl.trim()) {
      newErrors.baseUrl = '请输入基础URL';
    } else if (!/^https?:\/\/.+/.test(formData.baseUrl.trim())) {
      newErrors.baseUrl = '请输入有效的URL格式（以http://或https://开头）';
    }

    if (!formData.apiKey.trim()) {
      newErrors.apiKey = '请输入API密钥';
    } else if (formData.apiKey.trim().length < 10) {
      newErrors.apiKey = 'API密钥长度至少10个字符';
    }

    if (!formData.completionsPath.trim()) {
      newErrors.completionsPath = '请输入对话路径';
    }

    if (!formData.embeddingsPath.trim()) {
      newErrors.embeddingsPath = '请输入嵌入路径';
    }

    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  // 处理表单提交
  const handleSubmit = async () => {
    if (!validateForm() || !editingRecord) {
      return;
    }

    setLoading(true);
    try {
      const request: AiClientApiRequestDTO = {
        id: editingRecord.id,
        apiId: editingRecord.apiId,
        baseUrl: formData.baseUrl.trim(),
        apiKey: formData.apiKey.trim(),
        completionsPath: formData.completionsPath.trim(),
        embeddingsPath: formData.embeddingsPath.trim(),
        status: formData.status
      };

      const result = await aiClientApiAdminService.updateAiClientApiById(request);
      
      if (result.code === '0000' && result.data) {
        Toast.success('AI客户端API更新成功');
        handleReset();
        onSuccess();
      } else {
        throw new Error(result.info || '更新失败');
      }
    } catch (error) {
      console.error('更新AI客户端API失败:', error);
      Toast.error('更新失败，请检查网络连接或稍后重试');
    } finally {
      setLoading(false);
    }
  };

  // 重置表单
  const handleReset = () => {
    setFormData({
      baseUrl: '',
      apiKey: '',
      completionsPath: '',
      embeddingsPath: '',
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
      title="编辑AI客户端API"
      visible={visible}
      onCancel={handleCancel}
      footer={null}
      width={600}
      maskClosable={false}
    >
      <div style={{ padding: '20px 0' }}>
        <div style={{ marginBottom: '16px' }}>
          <div style={{ marginBottom: '8px', display: 'flex', alignItems: 'center' }}>
            <span style={{ width: '120px', textAlign: 'right', marginRight: '12px' }}>
              API ID:
            </span>
            <Input
               value={editingRecord?.apiId || ''}
               disabled
               style={{ flex: 1 }}
             />
          </div>
        </div>

        <div style={{ marginBottom: '16px' }}>
          <div style={{ marginBottom: '8px', display: 'flex', alignItems: 'center' }}>
            <span style={{ width: '120px', textAlign: 'right', marginRight: '12px' }}>
              基础URL<span style={{ color: 'red' }}>*</span>:
            </span>
            <Input
               placeholder="请输入基础URL，如：https://api.openai.com"
               value={formData.baseUrl}
               onChange={(value: string) => setFormData(prev => ({ ...prev, baseUrl: value }))}
               style={{ flex: 1 }}
             />
          </div>
          {errors.baseUrl && (
            <div style={{ marginLeft: '132px', color: 'red', fontSize: '12px' }}>
              {errors.baseUrl}
            </div>
          )}
        </div>

        <div style={{ marginBottom: '16px' }}>
          <div style={{ marginBottom: '8px', display: 'flex', alignItems: 'center' }}>
            <span style={{ width: '120px', textAlign: 'right', marginRight: '12px' }}>
              API密钥<span style={{ color: 'red' }}>*</span>:
            </span>
            <Input
               placeholder="请输入API密钥"
               value={formData.apiKey}
               onChange={(value: string) => setFormData(prev => ({ ...prev, apiKey: value }))}
               style={{ flex: 1 }}
               type="password"
             />
          </div>
          {errors.apiKey && (
            <div style={{ marginLeft: '132px', color: 'red', fontSize: '12px' }}>
              {errors.apiKey}
            </div>
          )}
        </div>

        <div style={{ marginBottom: '16px' }}>
          <div style={{ marginBottom: '8px', display: 'flex', alignItems: 'center' }}>
            <span style={{ width: '120px', textAlign: 'right', marginRight: '12px' }}>
              对话路径<span style={{ color: 'red' }}>*</span>:
            </span>
            <Input
               placeholder="对话补全路径"
               value={formData.completionsPath}
               onChange={(value: string) => setFormData(prev => ({ ...prev, completionsPath: value }))}
               style={{ flex: 1 }}
             />
          </div>
          {errors.completionsPath && (
            <div style={{ marginLeft: '132px', color: 'red', fontSize: '12px' }}>
              {errors.completionsPath}
            </div>
          )}
        </div>

        <div style={{ marginBottom: '16px' }}>
          <div style={{ marginBottom: '8px', display: 'flex', alignItems: 'center' }}>
            <span style={{ width: '120px', textAlign: 'right', marginRight: '12px' }}>
              嵌入路径<span style={{ color: 'red' }}>*</span>:
            </span>
            <Input
               placeholder="嵌入向量路径"
               value={formData.embeddingsPath}
               onChange={(value: string) => setFormData(prev => ({ ...prev, embeddingsPath: value }))}
               style={{ flex: 1 }}
             />
          </div>
          {errors.embeddingsPath && (
            <div style={{ marginLeft: '132px', color: 'red', fontSize: '12px' }}>
              {errors.embeddingsPath}
            </div>
          )}
        </div>

        <div style={{ marginBottom: '16px' }}>
          <div style={{ display: 'flex', alignItems: 'center' }}>
            <span style={{ width: '120px', textAlign: 'right', marginRight: '12px' }}>
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