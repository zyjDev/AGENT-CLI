import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { 
  Layout,
  Table, 
  Button, 
  Input, 
  Space, 
  Typography, 
  Toast,
  Tag,
  Popconfirm,
  Card,
  Select,
  Modal,
  TextArea
} from '@douyinfe/semi-ui';
import { 
  IconSearch, 
  IconPlus, 
  IconEdit, 
  IconDelete,
  IconRefresh,
  IconEyeOpened
} from '@douyinfe/semi-icons';
import styled from 'styled-components';
import { theme } from '../styles/theme';
import { Sidebar, Header } from '../components/layout';
import { 
  aiClientToolMcpAdminService, 
  AiClientToolMcpQueryRequestDTO, 
  AiClientToolMcpResponseDTO,
  AiClientToolMcpRequestDTO 
} from '../services/ai-client-tool-mcp-admin-service';

const { Content } = Layout;
const { Title } = Typography;
const { Option } = Select;

// 样式组件
const ClientToolMcpManagementLayout = styled(Layout)`
  min-height: 100vh;
  background: ${theme.colors.bg.secondary};
`;

const MainContent = styled.div<{ $collapsed: boolean }>`
  display: flex;
  flex: 1;
  margin-left: ${props => props.$collapsed ? '80px' : '280px'};
  transition: margin-left ${theme.animation.duration.normal} ${theme.animation.easing.cubic};
`;

const ContentArea = styled(Content)`
  flex: 1;
  padding: ${theme.spacing.lg};
  background: ${theme.colors.bg.secondary};
  overflow-y: auto;
`;

const ClientToolMcpManagementContainer = styled.div`
  height: 100%;
  display: flex;
  flex-direction: column;
`;

const PageHeader = styled.div`
  padding: ${theme.spacing.lg};
  border-bottom: 1px solid ${theme.colors.border.secondary};
`;

const SearchSection = styled(Card)`
  margin: ${theme.spacing.lg};
  
  .semi-card-body {
    padding: ${theme.spacing.lg};
  }
`;

const SearchRow = styled.div`
  display: flex;
  align-items: center;
  gap: ${theme.spacing.base};
  flex-wrap: wrap;
`;

const TableContainer = styled.div`
  flex: 1;
  margin: 0 ${theme.spacing.lg} ${theme.spacing.lg};
  display: flex;
  flex-direction: column;
`;

const TableCard = styled(Card)`
  flex: 1;
  display: flex;
  flex-direction: column;
  
  .semi-card-body {
    padding: 0;
    flex: 1;
    display: flex;
    flex-direction: column;
  }
`;

const TableWrapper = styled.div`
  flex: 1;
  overflow: auto;
`;

const ActionButton = styled(Button)`
  margin-right: ${theme.spacing.sm};
`;

const ConfigPreview = styled.div`
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  cursor: pointer;
  color: ${theme.colors.primary};
  
  &:hover {
    text-decoration: underline;
  }
`;

const ConfigModalContent = styled.pre`
  background: #f6f8fa;
  padding: 16px;
  border-radius: 6px;
  overflow-x: auto;
  white-space: pre-wrap;
  word-wrap: break-word;
  font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
  font-size: 12px;
  line-height: 1.5;
  max-height: 400px;
  overflow-y: auto;
`;

export const ClientToolMcpManagement: React.FC = () => {
  const navigate = useNavigate();
  const [collapsed, setCollapsed] = useState(false);
  const [loading, setLoading] = useState(false);
  const [dataSource, setDataSource] = useState<AiClientToolMcpResponseDTO[]>([]);
  const [searchText, setSearchText] = useState('');
  const [transportType, setTransportType] = useState<string>('');
  const [status, setStatus] = useState<number | undefined>(undefined);
  const [currentPage, setCurrentPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [total, setTotal] = useState(0);
  
  // 传输配置弹窗相关状态
  const [configModalVisible, setConfigModalVisible] = useState(false);
  const [selectedConfig, setSelectedConfig] = useState<string>('');
  const [selectedMcpName, setSelectedMcpName] = useState<string>('');
  
  // 新增MCP配置弹窗相关状态
  const [createModalVisible, setCreateModalVisible] = useState(false);
  const [createLoading, setCreateLoading] = useState(false);
  const [formData, setFormData] = useState<AiClientToolMcpRequestDTO>({
    mcpName: '',
    transportType: 'stdio',
    transportConfig: '{}',
    requestTimeout: 30000,
    status: 1
  });

  // 编辑MCP配置弹窗相关状态
  const [editModalVisible, setEditModalVisible] = useState(false);
  const [editLoading, setEditLoading] = useState(false);
  const [editFormData, setEditFormData] = useState<AiClientToolMcpRequestDTO>({
    mcpName: '',
    transportType: 'stdio',
    transportConfig: '{}',
    requestTimeout: 30000,
    status: 1
  });
  const [currentEditRecord, setCurrentEditRecord] = useState<AiClientToolMcpResponseDTO | null>(null);

  // 处理退出登录
  const handleLogout = () => {
    localStorage.removeItem('token');
    localStorage.removeItem('userInfo');
    localStorage.removeItem('isLoggedIn');
    Toast.success('已退出登录');
    navigate('/login');
  };

  // 处理侧边栏导航
  const handleNavigation = (path: string) => {
    switch (path) {
      case 'dashboard':
        navigate('/dashboard');
        break;
      case 'agent-list':
        navigate('/agent-list');
        break;
      case 'agent-config':
        navigate('/agent-config');
        break;
      case 'client-management':
        navigate('/client-management');
        break;
      case 'ai-client-api-management':
        navigate('/ai-client-api-management');
        break;
      case 'advisor-management':
        navigate('/advisor-management');
        break;
      case 'rag-order-management':
        navigate('/rag-order-management');
        break;
      case 'client-model-management':
        navigate('/client-model-management');
        break;
      case 'client-system-prompt-management':
        navigate('/client-system-prompt-management');
        break;
      case 'client-tool-mcp-management':
        navigate('/client-tool-mcp-management');
        break;
      default:
        navigate(path);
        break;
    }
  };

  // 显示传输配置详情
  const showConfigDetail = (config: string, mcpName: string) => {
    setSelectedConfig(config);
    setSelectedMcpName(mcpName);
    setConfigModalVisible(true);
  };

  // 格式化JSON配置
  const formatConfig = (config: string) => {
    try {
      const parsed = JSON.parse(config);
      return JSON.stringify(parsed, null, 2);
    } catch (error) {
      return config;
    }
  };

  // 表格列定义
  const columns = [
    {
      title: 'ID',
      dataIndex: 'id',
      key: 'id',
      width: 80,
    },
    {
      title: 'MCP ID',
      dataIndex: 'mcpId',
      key: 'mcpId',
      width: 150,
    },
    {
      title: 'MCP名称',
      dataIndex: 'mcpName',
      key: 'mcpName',
      width: 200,
    },
    {
      title: '传输配置',
      dataIndex: 'transportConfig',
      key: 'transportConfig',
      width: 250,
      render: (config: string, record: AiClientToolMcpResponseDTO) => (
        <ConfigPreview onClick={() => showConfigDetail(config, record.mcpName)}>
          {config ? `${config.substring(0, 50)}...` : '-'}
        </ConfigPreview>
      ),
    },
    {
      title: '传输类型',
      dataIndex: 'transportType',
      key: 'transportType',
      width: 120,
      render: (type: string) => (
        <Tag color={type === 'stdio' ? 'blue' : type === 'sse' ? 'green' : 'orange'}>
          {type || '-'}
        </Tag>
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (status: number) => (
        <Tag color={status === 1 ? 'green' : 'red'}>
          {status === 1 ? '启用' : '禁用'}
        </Tag>
      ),
    },
    {
      title: '创建时间',
      dataIndex: 'createTime',
      key: 'createTime',
      width: 180,
      render: (time: string) => new Date(time).toLocaleString(),
    },
    {
      title: '更新时间',
      dataIndex: 'updateTime',
      key: 'updateTime',
      width: 180,
      render: (time: string) => new Date(time).toLocaleString(),
    },
    {
      title: '操作',
      key: 'action',
      width: 200,
      fixed: 'right' as const,
      render: (_: any, record: AiClientToolMcpResponseDTO) => (
        <Space>
          <ActionButton
            theme="borderless"
            type="tertiary"
            icon={<IconEyeOpened />}
            size="small"
            onClick={() => showConfigDetail(record.transportConfig, record.mcpName)}
          >
            查看配置
          </ActionButton>
          <ActionButton
            theme="borderless"
            type="primary"
            icon={<IconEdit />}
            size="small"
            onClick={() => handleEdit(record)}
          >
            编辑
          </ActionButton>
          <Popconfirm
            title="确定要删除这个MCP配置吗？"
            content="删除后无法恢复，请谨慎操作"
            onConfirm={() => handleDelete(record)}
            okText="确定"
            cancelText="取消"
          >
            <ActionButton
              theme="borderless"
              type="danger"
              icon={<IconDelete />}
              size="small"
            >
              删除
            </ActionButton>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  // 获取MCP客户端工具列表数据
  const fetchMcpList = async () => {
    setLoading(true);
    try {
      const request: AiClientToolMcpQueryRequestDTO = {
        mcpName: searchText || undefined,
        transportType: transportType || undefined,
        status: status,
        pageNum: currentPage,
        pageSize: pageSize,
      };

      const result = await aiClientToolMcpAdminService.queryAiClientToolMcpList(request);
      
      if (result.code === '0000') {
        const data = result.data || [];
        setDataSource(data);
        setTotal(data.length); // 简单实现，实际应该从后端返回总数
      } else {
        throw new Error(result.info || '查询失败');
      }
    } catch (error) {
      console.error('获取MCP客户端工具列表失败:', error);
      Toast.error('获取MCP客户端工具列表失败，请检查网络连接');
      setDataSource([]);
      setTotal(0);
    } finally {
      setLoading(false);
    }
  };

  // 删除MCP配置
  const handleDelete = async (record: AiClientToolMcpResponseDTO) => {
    try {
      const result = await aiClientToolMcpAdminService.deleteAiClientToolMcpById(record.id);
      
      if (result.code === '0000' && result.data) {
        Toast.success('删除成功');
        // 重新加载数据
        fetchMcpList();
      } else {
        throw new Error(result.info || '删除失败');
      }
    } catch (error) {
      console.error('删除MCP配置失败:', error);
      Toast.error('删除失败，请检查网络连接');
    }
  };

  // 编辑MCP配置
  const handleEdit = (record: AiClientToolMcpResponseDTO) => {
    setCurrentEditRecord(record);
    setEditFormData({
      mcpId: record.mcpId,
      mcpName: record.mcpName,
      transportType: record.transportType,
      transportConfig: record.transportConfig,
      requestTimeout: record.requestTimeout,
      status: record.status
    });
    setEditModalVisible(true);
  };

  // 关闭编辑弹窗
  const handleEditCancel = () => {
    setEditModalVisible(false);
    setCurrentEditRecord(null);
    setEditFormData({
      mcpName: '',
      transportType: 'stdio',
      transportConfig: '{}',
      requestTimeout: 30,
      status: 1
    });
  };

  // 处理编辑表单字段变化
  const handleEditFormChange = (field: keyof AiClientToolMcpRequestDTO, value: any) => {
    setEditFormData(prev => ({
      ...prev,
      [field]: value
    }));
  };

  // 提交编辑MCP配置
  const handleEditSubmit = async () => {
    // 表单验证
    if (!editFormData.mcpName?.trim()) {
      Toast.error('请输入MCP名称');
      return;
    }
    if (!editFormData.transportType) {
      Toast.error('请选择传输类型');
      return;
    }
    if (!editFormData.transportConfig?.trim()) {
      Toast.error('请输入传输配置');
      return;
    }

    // 验证JSON格式
    try {
      JSON.parse(editFormData.transportConfig);
    } catch (error) {
      Toast.error('传输配置必须是有效的JSON格式');
      return;
    }

    if (!currentEditRecord?.mcpId) {
      Toast.error('MCP ID不能为空');
      return;
    }

    setEditLoading(true);
    try {
      const request: AiClientToolMcpRequestDTO = {
        ...editFormData,
        mcpId: currentEditRecord.mcpId, // 使用原有的mcpId
        mcpName: editFormData.mcpName.trim(),
        transportConfig: editFormData.transportConfig.trim()
      };

      const result = await aiClientToolMcpAdminService.updateAiClientToolMcpByMcpId(request);
      
      if (result.code === '0000' && result.data) {
        Toast.success('更新成功');
        setEditModalVisible(false);
        // 重新加载数据
        fetchMcpList();
        // 重置表单
        handleEditCancel();
      } else {
        throw new Error(result.info || '更新失败');
      }
    } catch (error) {
      console.error('更新MCP配置失败:', error);
      Toast.error('更新失败，请检查网络连接');
    } finally {
      setEditLoading(false);
    }
  };

  // 生成8位数字类型的字符串作为mcpId
  const generateMcpId = () => {
    let result = '';
    for (let i = 0; i < 8; i++) {
      result += Math.floor(Math.random() * 10).toString();
    }
    return result;
  };

  // 打开新增弹窗
  const handleCreate = () => {
    setFormData({
      mcpName: '',
      transportType: 'stdio',
      transportConfig: '{}',
      requestTimeout: 30,
      status: 1
    });
    setCreateModalVisible(true);
  };

  // 关闭新增弹窗
  const handleCreateCancel = () => {
    setCreateModalVisible(false);
    setFormData({
      mcpName: '',
      transportType: 'stdio',
      transportConfig: '{}',
      requestTimeout: 30,
      status: 1
    });
  };

  // 处理表单字段变化
  const handleFormChange = (field: keyof AiClientToolMcpRequestDTO, value: any) => {
    setFormData(prev => ({
      ...prev,
      [field]: value
    }));
  };

  // 提交新增MCP配置
  const handleCreateSubmit = async () => {
    // 表单验证
    if (!formData.mcpName?.trim()) {
      Toast.error('请输入MCP名称');
      return;
    }
    if (!formData.transportType) {
      Toast.error('请选择传输类型');
      return;
    }
    if (!formData.transportConfig?.trim()) {
      Toast.error('请输入传输配置');
      return;
    }

    // 验证JSON格式
    try {
      JSON.parse(formData.transportConfig);
    } catch (error) {
      Toast.error('传输配置必须是有效的JSON格式');
      return;
    }

    setCreateLoading(true);
    try {
      const request: AiClientToolMcpRequestDTO = {
        ...formData,
        mcpId: generateMcpId(), // 生成随机mcpId
        mcpName: formData.mcpName.trim(),
        transportConfig: formData.transportConfig.trim()
      };

      const result = await aiClientToolMcpAdminService.createAiClientToolMcp(request);
      
      if (result.code === '0000' && result.data) {
        Toast.success('创建成功');
        setCreateModalVisible(false);
        // 重新加载数据
        fetchMcpList();
        // 重置表单
        handleCreateCancel();
      } else {
        throw new Error(result.info || '创建失败');
      }
    } catch (error) {
      console.error('创建MCP配置失败:', error);
      Toast.error('创建失败，请检查网络连接');
    } finally {
      setCreateLoading(false);
    }
  };

  // 处理传输类型变化
  const handleTransportTypeChange = (value: any) => {
    setTransportType(value || '');
  };

  // 处理状态变化
  const handleStatusChange = (value: any) => {
    setStatus(value === '' ? undefined : Number(value));
  };

  // 搜索
  const handleSearch = () => {
    setCurrentPage(1);
    fetchMcpList();
  };

  // 重置搜索
  const handleReset = () => {
    setSearchText('');
    setTransportType('');
    setStatus(undefined);
    setCurrentPage(1);
    fetchMcpList();
  };

  // 分页变化
  const handlePageChange = (page: number, size?: number) => {
    setCurrentPage(page);
    if (size && size !== pageSize) {
      setPageSize(size);
    }
    fetchMcpList();
  };

  // 组件挂载时获取数据
  useEffect(() => {
    fetchMcpList();
  }, []);

  return (
    <ClientToolMcpManagementLayout>
      <Sidebar 
        collapsed={collapsed}
        selectedKey="client-tool-mcp-management"
        onSelect={handleNavigation}
      />
      <MainContent $collapsed={collapsed}>
        <ContentArea>
          <Header 
            collapsed={collapsed}
            onToggleSidebar={() => setCollapsed(!collapsed)}
            onLogout={handleLogout}
          />
          <ClientToolMcpManagementContainer>
            <PageHeader>
              <Title heading={3} style={{ margin: 0 }}>MCP客户端工具管理</Title>
            </PageHeader>

            <SearchSection>
              <SearchRow>
                <Input
                  placeholder="请输入MCP名称"
                  value={searchText}
                  onChange={setSearchText}
                  style={{ width: 200 }}
                  onEnterPress={handleSearch}
                />
                <Select
                  placeholder="选择传输类型"
                  value={transportType}
                  onChange={handleTransportTypeChange}
                  style={{ width: 150 }}
                >
                  <Option value="">全部</Option>
                  <Option value="stdio">stdio</Option>
                  <Option value="sse">sse</Option>
                  <Option value="websocket">websocket</Option>
                </Select>
                <Select
                  placeholder="选择状态"
                  value={status === undefined ? '' : status}
                  onChange={handleStatusChange}
                  style={{ width: 120 }}
                >
                  <Option value="">全部</Option>
                  <Option value={1}>启用</Option>
                  <Option value={0}>禁用</Option>
                </Select>
                <Button
                  type="primary"
                  icon={<IconSearch />}
                  onClick={handleSearch}
                >
                  搜索
                </Button>
                <Button
                  icon={<IconRefresh />}
                  onClick={handleReset}
                >
                  重置
                </Button>
                <Button
                  type="primary"
                  theme="solid"
                  icon={<IconPlus />}
                  onClick={handleCreate}
                >
                  新增MCP配置
                </Button>
              </SearchRow>
            </SearchSection>

            <TableContainer>
              <TableCard>
                <TableWrapper>
                  <Table
                    columns={columns}
                    dataSource={dataSource}
                    loading={loading}
                    pagination={{
                      currentPage: currentPage,
                      pageSize: pageSize,
                      total: total,
                      showSizeChanger: true,
                      showQuickJumper: true,
                      onChange: handlePageChange
                    }}
                    rowKey="id"
                    scroll={{ x: 1400 }}
                    empty={
                      <div style={{ padding: '40px', textAlign: 'center' }}>
                        <Typography.Text type="tertiary">暂无数据</Typography.Text>
                      </div>
                    }
                  />
                </TableWrapper>
              </TableCard>
            </TableContainer>
          </ClientToolMcpManagementContainer>

          {/* 传输配置详情弹窗 */}
          <Modal
            title={`传输配置详情 - ${selectedMcpName}`}
            visible={configModalVisible}
            onCancel={() => setConfigModalVisible(false)}
            footer={
              <Button onClick={() => setConfigModalVisible(false)}>
                关闭
              </Button>
            }
            width={800}
            style={{ maxWidth: '90vw' }}
          >
            <ConfigModalContent>
              {formatConfig(selectedConfig)}
            </ConfigModalContent>
          </Modal>

          {/* 新增MCP配置弹窗 */}
          <Modal
            title="新增MCP配置"
            visible={createModalVisible}
            onCancel={handleCreateCancel}
            footer={
              <Space>
                <Button onClick={handleCreateCancel}>
                  取消
                </Button>
                <Button 
                  type="primary" 
                  loading={createLoading}
                  onClick={handleCreateSubmit}
                >
                  保存
                </Button>
              </Space>
            }
            width={600}
            style={{ maxWidth: '90vw' }}
          >
            <div style={{ padding: '20px 0' }}>
               <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
                 <div>
                   <Typography.Text strong style={{ display: 'block', marginBottom: 8 }}>
                     MCP名称 <Typography.Text type="danger">*</Typography.Text>
                   </Typography.Text>
                   <Input
                     placeholder="请输入MCP名称"
                     value={formData.mcpName}
                     onChange={(value: string) => handleFormChange('mcpName', value)}
                     style={{ width: '100%' }}
                   />
                 </div>

                 <div>
                   <Typography.Text strong style={{ display: 'block', marginBottom: 8 }}>
                     传输类型 <Typography.Text type="danger">*</Typography.Text>
                   </Typography.Text>
                   <Select
                     placeholder="请选择传输类型"
                     value={formData.transportType}
                     onChange={(value: any) => handleFormChange('transportType', value)}
                     style={{ width: '100%' }}
                   >
                     <Option value="stdio">stdio</Option>
                     <Option value="sse">sse</Option>
                     <Option value="websocket">websocket</Option>
                   </Select>
                 </div>

                 <div>
                   <Typography.Text strong style={{ display: 'block', marginBottom: 8 }}>
                     传输配置 <Typography.Text type="danger">*</Typography.Text>
                   </Typography.Text>
                   <TextArea
                     placeholder="请输入传输配置（JSON格式）"
                     value={formData.transportConfig}
                     onChange={(value: string) => handleFormChange('transportConfig', value)}
                     rows={6}
                     style={{ width: '100%', fontFamily: 'Monaco, Menlo, Ubuntu Mono, monospace' }}
                   />
                   <Typography.Text type="tertiary" size="small">
                     请输入有效的JSON格式配置，例如：{"{"}"command": "node", "args": ["server.js"]{"}"} 
                   </Typography.Text>
                 </div>

                 <div>
                   <Typography.Text strong style={{ display: 'block', marginBottom: 8 }}>
                     请求超时时间（秒）
                   </Typography.Text>
                   <Input
                     type="number"
                     placeholder="请输入超时时间"
                     value={formData.requestTimeout}
                     onChange={(value: string) => handleFormChange('requestTimeout', Number(value))}
                     style={{ width: '100%' }}
                   />
                 </div>

                 <div>
                   <Typography.Text strong style={{ display: 'block', marginBottom: 8 }}>
                     状态
                   </Typography.Text>
                   <Select
                     value={formData.status}
                     onChange={(value: any) => handleFormChange('status', value)}
                     style={{ width: '100%' }}
                   >
                     <Option value={1}>启用</Option>
                     <Option value={0}>禁用</Option>
                   </Select>
                 </div>
               </div>
             </div>
          </Modal>

          {/* 编辑MCP配置弹窗 */}
          <Modal
            title="编辑MCP配置"
            visible={editModalVisible}
            onCancel={handleEditCancel}
            footer={
              <Space>
                <Button onClick={handleEditCancel}>
                  取消
                </Button>
                <Button 
                  type="primary" 
                  loading={editLoading}
                  onClick={handleEditSubmit}
                >
                  保存
                </Button>
              </Space>
            }
            width={600}
            style={{ maxWidth: '90vw' }}
          >
            <div style={{ padding: '20px 0' }}>
               <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
                 <div>
                   <Typography.Text strong style={{ display: 'block', marginBottom: 8 }}>
                     MCP ID
                   </Typography.Text>
                   <Input
                     value={editFormData.mcpId}
                     disabled
                     style={{ width: '100%', backgroundColor: '#f6f8fa' }}
                   />
                   <Typography.Text type="tertiary" size="small">
                     MCP ID不可修改
                   </Typography.Text>
                 </div>

                 <div>
                   <Typography.Text strong style={{ display: 'block', marginBottom: 8 }}>
                     MCP名称 <Typography.Text type="danger">*</Typography.Text>
                   </Typography.Text>
                   <Input
                     placeholder="请输入MCP名称"
                     value={editFormData.mcpName}
                     onChange={(value: string) => handleEditFormChange('mcpName', value)}
                     style={{ width: '100%' }}
                   />
                 </div>

                 <div>
                   <Typography.Text strong style={{ display: 'block', marginBottom: 8 }}>
                     传输类型 <Typography.Text type="danger">*</Typography.Text>
                   </Typography.Text>
                   <Select
                     placeholder="请选择传输类型"
                     value={editFormData.transportType}
                     onChange={(value: any) => handleEditFormChange('transportType', value)}
                     style={{ width: '100%' }}
                   >
                     <Option value="stdio">stdio</Option>
                     <Option value="sse">sse</Option>
                     <Option value="websocket">websocket</Option>
                   </Select>
                 </div>

                 <div>
                   <Typography.Text strong style={{ display: 'block', marginBottom: 8 }}>
                     传输配置 <Typography.Text type="danger">*</Typography.Text>
                   </Typography.Text>
                   <TextArea
                     placeholder="请输入传输配置（JSON格式）"
                     value={editFormData.transportConfig}
                     onChange={(value: string) => handleEditFormChange('transportConfig', value)}
                     rows={6}
                     style={{ width: '100%', fontFamily: 'Monaco, Menlo, Ubuntu Mono, monospace' }}
                   />
                   <Typography.Text type="tertiary" size="small">
                     请输入有效的JSON格式配置，例如：{"{"}"command": "node", "args": ["server.js"]{"}"} 
                   </Typography.Text>
                 </div>

                 <div>
                   <Typography.Text strong style={{ display: 'block', marginBottom: 8 }}>
                     请求超时时间（秒）
                   </Typography.Text>
                   <Input
                     type="number"
                     placeholder="请输入超时时间"
                     value={editFormData.requestTimeout}
                     onChange={(value: string) => handleEditFormChange('requestTimeout', Number(value))}
                     style={{ width: '100%' }}
                   />
                 </div>

                 <div>
                   <Typography.Text strong style={{ display: 'block', marginBottom: 8 }}>
                     状态
                   </Typography.Text>
                   <Select
                     value={editFormData.status}
                     onChange={(value: any) => handleEditFormChange('status', value)}
                     style={{ width: '100%' }}
                   >
                     <Option value={1}>启用</Option>
                     <Option value={0}>禁用</Option>
                   </Select>
                 </div>
               </div>
             </div>
          </Modal>
        </ContentArea>
      </MainContent>
    </ClientToolMcpManagementLayout>
  );
};