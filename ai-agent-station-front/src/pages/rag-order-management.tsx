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
  Form,
  Upload
} from '@douyinfe/semi-ui';
import { 
  IconSearch, 
  IconDelete,
  IconRefresh,
  IconUpload,
  IconPlus
} from '@douyinfe/semi-icons';
import styled from 'styled-components';
import { theme } from '../styles/theme';
import { Sidebar, Header } from '../components/layout';
import { 
  aiClientRagOrderAdminService, 
  AiClientRagOrderQueryRequestDTO, 
  AiClientRagOrderResponseDTO
} from '../services/ai-client-rag-order-admin-service';

const { Content } = Layout;
const { Title } = Typography;
const { Option } = Select;

// 样式组件
const RagOrderManagementLayout = styled(Layout)`
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

const RagOrderManagementContainer = styled.div`
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

export const RagOrderManagement: React.FC = () => {
  const navigate = useNavigate();
  const [collapsed, setCollapsed] = useState(false);
  const [loading, setLoading] = useState(false);
  const [dataSource, setDataSource] = useState<AiClientRagOrderResponseDTO[]>([]);
  const [searchText, setSearchText] = useState('');
  const [searchRagName, setSearchRagName] = useState('');
  const [searchKnowledgeTag, setSearchKnowledgeTag] = useState('');
  const [searchStatus, setSearchStatus] = useState<number | undefined>(undefined);
  const [currentPage, setCurrentPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [total, setTotal] = useState(0);

  // 上传弹窗相关状态
  const [uploadModalVisible, setUploadModalVisible] = useState(false);
  const [uploadLoading, setUploadLoading] = useState(false);
  const [uploadFormApi, setUploadFormApi] = useState<any>(null);
  const [fileList, setFileList] = useState<any[]>([]);

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

  // 表格列定义
  const columns = [
    {
      title: 'ID',
      dataIndex: 'id',
      key: 'id',
      width: 80,
    },
    {
      title: '知识库ID',
      dataIndex: 'ragId',
      key: 'ragId',
      width: 150,
    },
    {
      title: '知识库名称',
      dataIndex: 'ragName',
      key: 'ragName',
      width: 200,
    },
    {
      title: '知识标签',
      dataIndex: 'knowledgeTag',
      key: 'knowledgeTag',
      width: 150,
      render: (tag: string) => (
        <Tag color="blue">{tag}</Tag>
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
      width: 120,
      fixed: 'right' as const,
      render: (_: any, record: AiClientRagOrderResponseDTO) => (
        <Space>
          <Popconfirm
            title="确定要删除这个知识库配置吗？"
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

  // 获取知识库配置列表数据
  const fetchRagOrderList = async () => {
    setLoading(true);
    try {
      const request: AiClientRagOrderQueryRequestDTO = {
        ragId: searchText || undefined,
        ragName: searchRagName || undefined,
        knowledgeTag: searchKnowledgeTag || undefined,
        status: searchStatus,
        pageNum: currentPage,
        pageSize: pageSize,
      };

      const result = await aiClientRagOrderAdminService.queryRagOrderList(request);
      
      if (result.code === '0000') {
        const data = result.data || [];
        setDataSource(data);
        setTotal(data.length); // 简单实现，实际应该从后端返回总数
      } else {
        throw new Error(result.info || '查询失败');
      }
    } catch (error) {
      console.error('获取知识库配置列表失败:', error);
      Toast.error('获取知识库配置列表失败，请检查网络连接');
      setDataSource([]);
      setTotal(0);
    } finally {
      setLoading(false);
    }
  };

  // 删除知识库配置
  const handleDelete = async (record: AiClientRagOrderResponseDTO) => {
    try {
      const result = await aiClientRagOrderAdminService.deleteRagOrderById(record.id);
      
      if (result.code === '0000' && result.data) {
        Toast.success('删除成功');
        // 重新加载数据
        fetchRagOrderList();
      } else {
        throw new Error(result.info || '删除失败');
      }
    } catch (error) {
      console.error('删除知识库配置失败:', error);
      Toast.error('删除失败，请检查网络连接');
    }
  };

  // 打开上传弹窗
  const handleOpenUploadModal = () => {
    setUploadModalVisible(true);
    if (uploadFormApi) {
      uploadFormApi.reset();
    }
    setFileList([]);
  };

  // 关闭上传弹窗
  const handleCloseUploadModal = () => {
    setUploadModalVisible(false);
    if (uploadFormApi) {
      uploadFormApi.reset();
    }
    setFileList([]);
  };

  // 处理文件上传
  const handleUploadSubmit = async () => {
    try {
      if (!uploadFormApi) {
        Toast.error('表单未初始化');
        return;
      }

      const values = await uploadFormApi.validate();
      
      if (fileList.length === 0) {
        Toast.error('请选择要上传的文件');
        return;
      }

      setUploadLoading(true);

      // 将文件对象转换为File类型
      const files = fileList.map(item => item.fileInstance);
      
      const result = await aiClientRagOrderAdminService.uploadRagFile(
        values.name,
        values.tag,
        files
      );

      if (result.code === '0000' && result.data) {
        Toast.success('上传成功');
        handleCloseUploadModal();
        // 重新加载数据
        fetchRagOrderList();
      } else {
        throw new Error(result.info || '上传失败');
      }
    } catch (error) {
      console.error('上传知识库文件失败:', error);
      Toast.error('上传失败，请检查网络连接');
    } finally {
      setUploadLoading(false);
    }
  };

  // 文件上传前的处理
  const beforeUpload = (file: any) => {
    // 检查文件扩展名
    if (file && file.name) {
      const fileName = file.name.toLowerCase();
      if (fileName.endsWith('.txt') || fileName.endsWith('.pdf') || 
          fileName.endsWith('.doc') || fileName.endsWith('.docx') || 
          fileName.endsWith('.md')) {
        // 检查文件大小
        if (file.size <= 10 * 1024 * 1024) {
          return true;
        } else {
          Toast.error('文件大小不能超过10MB');
          return false;
        }
      } else {
        Toast.error('不支持的文件类型');
        return false;
      }
    }
    return false;
  };

  // 文件列表变化处理
  const handleFileChange = (options: any) => {
    setFileList(options.fileList || []);
  };



  // 搜索
  const handleSearch = () => {
    setCurrentPage(1);
    fetchRagOrderList();
  };

  // 重置搜索
  const handleReset = () => {
    setSearchText('');
    setSearchRagName('');
    setSearchKnowledgeTag('');
    setSearchStatus(undefined);
    setCurrentPage(1);
    fetchRagOrderList();
  };

  // 分页变化
  const handlePageChange = (page: number, size?: number) => {
    setCurrentPage(page);
    if (size && size !== pageSize) {
      setPageSize(size);
    }
    fetchRagOrderList();
  };

  // 组件挂载时获取数据
  useEffect(() => {
    fetchRagOrderList();
  }, []);

  return (
    <RagOrderManagementLayout>
      <Sidebar 
        collapsed={collapsed}
        selectedKey="rag-order-management"
        onSelect={handleNavigation}
      />
      <MainContent $collapsed={collapsed}>
        <ContentArea>
          <Header 
            collapsed={collapsed}
            onToggleSidebar={() => setCollapsed(!collapsed)}
            onLogout={handleLogout}
          />
          <RagOrderManagementContainer>
            <PageHeader>
              <Title heading={3} style={{ margin: 0 }}>知识库配置管理</Title>
            </PageHeader>

            <SearchSection>
              <SearchRow>
                <Input
                  placeholder="请输入知识库ID"
                  value={searchText}
                  onChange={setSearchText}
                  style={{ width: 200 }}
                  onEnterPress={handleSearch}
                />
                <Input
                  placeholder="请输入知识库名称"
                  value={searchRagName}
                  onChange={setSearchRagName}
                  style={{ width: 200 }}
                  onEnterPress={handleSearch}
                />
                <Input
                  placeholder="请输入知识标签"
                  value={searchKnowledgeTag}
                  onChange={setSearchKnowledgeTag}
                  style={{ width: 200 }}
                  onEnterPress={handleSearch}
                />
                <Select
                  placeholder="选择状态"
                  value={searchStatus}
                  onChange={(value) => setSearchStatus(value as number | undefined)}
                  style={{ width: 120 }}
                >
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
                  icon={<IconUpload />}
                  onClick={handleOpenUploadModal}
                  style={{ marginLeft: 'auto' }}
                >
                  上传知识库
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
                    scroll={{ x: 900 }}
                    empty={
                      <div style={{ padding: '40px', textAlign: 'center' }}>
                        <Typography.Text type="tertiary">暂无数据</Typography.Text>
                      </div>
                    }
                  />
                </TableWrapper>
              </TableCard>
            </TableContainer>
          </RagOrderManagementContainer>

          {/* 上传知识库弹窗 */}
          <Modal
            title="上传知识库文件"
            visible={uploadModalVisible}
            onOk={handleUploadSubmit}
            onCancel={handleCloseUploadModal}
            confirmLoading={uploadLoading}
            width={600}
            okText="确认上传"
            cancelText="取消"
          >
            <Form
              getFormApi={(api) => setUploadFormApi(api)}
              labelPosition="left"
              labelWidth={100}
              style={{ padding: '20px 0' }}
            >
              <Form.Input
                field="name"
                label="知识库名称"
                placeholder="请输入知识库名称"
                rules={[
                  { required: true, message: '请输入知识库名称' },
                  { min: 2, message: '知识库名称至少2个字符' },
                  { max: 50, message: '知识库名称不能超过50个字符' }
                ]}
              />
              <Form.Input
                field="tag"
                label="知识标签"
                placeholder="请输入知识标签"
                rules={[
                  { required: true, message: '请输入知识标签' },
                  { min: 2, message: '知识标签至少2个字符' },
                  { max: 30, message: '知识标签不能超过30个字符' }
                ]}
              />
              <Form.Slot label="上传文件">
                <Upload
                  action=""
                  beforeUpload={beforeUpload}
                  onChange={handleFileChange}
                  fileList={fileList}
                  accept=".txt,.pdf,.doc,.docx,.md"
                  multiple={false}
                  showUploadList={true}
                >
                  <Button icon={<IconPlus />} theme="light">
                    选择文件
                  </Button>
                </Upload>
              </Form.Slot>
            </Form>
          </Modal>
        </ContentArea>
      </MainContent>
    </RagOrderManagementLayout>
  );
};