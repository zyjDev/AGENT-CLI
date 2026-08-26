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
  Card
} from '@douyinfe/semi-ui';
import { 
  IconSearch, 
  IconPlus, 
  IconEyeOpened, 
  IconEdit, 
  IconDelete 
} from '@douyinfe/semi-icons';
import styled from 'styled-components';
import { theme } from '../styles/theme';
import { Sidebar, Header } from '../components/layout';
import { AiAgentDrawService, AiAgentDrawConfigResponseDTO, AiAgentDrawConfigQueryRequestDTO } from '../services/ai-agent-draw-service';
import { AiAgentService } from '../services/ai-agent-service';

const { Content } = Layout;
const { Title } = Typography;

// 样式组件
const AgentListLayout = styled(Layout)`
  min-height: 100vh;
  background: ${theme.colors.bg.secondary};
`;

const MainContent = styled.div<{ $collapsed: boolean }>`
  display: flex;
  flex: 1;
  margin-left: ${props => props.$collapsed ? '80px' : '280px'}; /* 根据 Sidebar 状态调整左边距 */
  transition: margin-left ${theme.animation.duration.normal} ${theme.animation.easing.cubic};
`;

const ContentArea = styled(Content)`
  flex: 1;
  padding: ${theme.spacing.lg};
  background: ${theme.colors.bg.secondary};
  overflow-y: auto;
`;

const PageHeader = styled.div`
  margin-bottom: ${theme.spacing.lg};
`;

const SearchSection = styled(Card)`
  margin-bottom: ${theme.spacing.lg};
  
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

const TableCard = styled(Card)`
  .semi-card-body {
    padding: 0;
  }
`;

const ActionButton = styled(Button)`
  margin-right: ${theme.spacing.sm};
`;

interface AgentListPageProps {
  selectedKey?: string;
  onMenuSelect?: (key: string) => void;
}

export const AgentListPage: React.FC<AgentListPageProps> = ({
  selectedKey = 'agent-list',
  onMenuSelect
}) => {
  const navigate = useNavigate();
  const [collapsed, setCollapsed] = useState(false);

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

  // 处理退出登录
  const handleLogout = () => {
    localStorage.removeItem('token');
    localStorage.removeItem('userInfo');
    localStorage.removeItem('isLoggedIn');
    navigate('/login');
  };
  const [loading, setLoading] = useState(false);
  const [dataSource, setDataSource] = useState<AiAgentDrawConfigResponseDTO[]>([]);
  const [searchParams, setSearchParams] = useState<AiAgentDrawConfigQueryRequestDTO>({
    pageNum: 1,
    pageSize: 10
  });
  const [total, setTotal] = useState(0);

  // 表格列定义
  const columns = [
    {
      title: '配置ID',
      dataIndex: 'configId',
      key: 'configId',
      width: 120,
      render: (text: string) => (
        <span style={{ fontFamily: 'monospace', fontSize: '12px' }}>
          {text}
        </span>
      )
    },
    {
      title: '配置名称',
      dataIndex: 'configName',
      key: 'configName',
      width: 120,
      render: (text: string) => (
        <span style={{ fontWeight: 500 }}>{text}</span>
      )
    },
    {
      title: '描述',
      dataIndex: 'description',
      key: 'description',
      width: 250,
      render: (text: string) => text || '-'
    },
    {
      title: '智能体ID',
      dataIndex: 'agentId',
      key: 'agentId',
      width: 120,
      render: (text: string) => (
        <span style={{ fontFamily: 'monospace', fontSize: '12px' }}>
          {text}
        </span>
      )
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 80,
      render: (status: number) => (
        <Tag color={status === 1 ? 'green' : 'red'}>
          {status === 1 ? '启用' : '禁用'}
        </Tag>
      )
    },
    // {
    //   title: '创建时间',
    //   dataIndex: 'createTime',
    //   key: 'createTime',
    //   width: 160,
    //   render: (time: string) => {
    //     if (!time) return '-';
    //     return new Date(time).toLocaleString('zh-CN');
    //   }
    // },
    {
      title: '操作',
      key: 'action',
      width: 250,
      fixed: 'right' as const,
      render: (_: any, record: AiAgentDrawConfigResponseDTO) => (
        <Space>
          <ActionButton
            type="tertiary"
            size="small"
            icon={<IconEyeOpened />}
            onClick={() => handleView(record)}
          >
            查看
          </ActionButton>
          <ActionButton
            type="tertiary"
            size="small"
            icon={<IconEdit />}
            onClick={() => handleEdit(record)}
          >
            修改
          </ActionButton>
          <ActionButton
            type="primary"
            size="small"
            onClick={() => handleLoad(record)}
          >
            加载
          </ActionButton>
          <Popconfirm
            title="确定要删除这个配置吗？"
            content="删除后无法恢复，请谨慎操作"
            onConfirm={() => handleDelete(record)}
          >
            <ActionButton
              type="danger"
              size="small"
              icon={<IconDelete />}
            >
              删除
            </ActionButton>
          </Popconfirm>
        </Space>
      )
    }
  ];

  // 加载数据
  const loadData = async (params?: AiAgentDrawConfigQueryRequestDTO) => {
    setLoading(true);
    try {
      const queryParams = { ...searchParams, ...params };
      const data = await AiAgentDrawService.queryDrawConfigList(queryParams);
      setDataSource(data);
      // 注意：后端返回的是简单分页，这里设置一个估算的总数
      setTotal(data.length >= (queryParams.pageSize || 10) ? (queryParams.pageNum || 1) * (queryParams.pageSize || 10) + 1 : data.length);
    } catch (error) {
      Toast.error('加载数据失败');
      console.error('加载数据失败:', error);
    } finally {
      setLoading(false);
    }
  };

  // 搜索
  const handleSearch = () => {
    const params = { ...searchParams, pageNum: 1 };
    setSearchParams(params);
    loadData(params);
  };

  // 重置搜索
  const handleReset = () => {
    const params = { pageNum: 1, pageSize: 10 };
    setSearchParams(params);
    loadData(params);
  };

  // 分页变化
  const handlePageChange = (pageNum: number, pageSize: number) => {
    const params = { ...searchParams, pageNum, pageSize };
    setSearchParams(params);
    loadData(params);
  };

  // 新建
  const handleCreate = () => {
    // 跳转到 agent-config 页面，新建模式
    navigate('/agent-config');
  };

  // 查看
  const handleView = (record: AiAgentDrawConfigResponseDTO) => {
    // 跳转到 agent-config 页面，传递 configId 参数，并添加 mode=view 表示只读模式
    navigate(`/agent-config?configId=${record.configId}&mode=view`);
  };

  // 编辑
  const handleEdit = (record: AiAgentDrawConfigResponseDTO) => {
    // 跳转到 agent-config 页面，传递 configId 参数，默认为编辑模式
    navigate(`/agent-config?configId=${record.configId}`);
  };

  // 加载 - 装配智能体
  const handleLoad = async (record: AiAgentDrawConfigResponseDTO) => {
    try {
      setLoading(true);
      Toast.info(`正在装配智能体: ${record.configName}`);
      
      const success = await AiAgentService.armoryAgent(record.agentId);
      
      if (success) {
        Toast.success(`智能体 ${record.configName} 装配成功！`);
      } else {
        Toast.error(`智能体 ${record.configName} 装配失败`);
      }
    } catch (error) {
      console.error('装配智能体失败:', error);
      Toast.error(`装配失败: ${error instanceof Error ? error.message : '未知错误'}`);
    } finally {
      setLoading(false);
    }
  };

  // 删除
  const handleDelete = async (record: AiAgentDrawConfigResponseDTO) => {
    try {
      setLoading(true);
      const success = await AiAgentDrawService.deleteDrawConfig(record.configId);
      if (success) {
        Toast.success(`成功删除配置: ${record.configName}`);
        // 重新加载数据
        await loadData();
      } else {
        Toast.error('删除配置失败');
      }
    } catch (error) {
      Toast.error('删除配置失败');
      console.error('删除配置失败:', error);
    } finally {
      setLoading(false);
    }
  };

  // 初始化加载数据
  useEffect(() => {
    loadData();
  }, []);

  return (
    <AgentListLayout>
      <Sidebar 
        selectedKey={selectedKey} 
        onSelect={handleNavigation}
        collapsed={collapsed}
      />
      <MainContent $collapsed={collapsed}>
        <div style={{ flex: 1, display: 'flex', flexDirection: 'column' }}>
          <Header
            onToggleSidebar={() => setCollapsed(!collapsed)}
            onLogout={handleLogout}
            collapsed={collapsed}
          />
          <ContentArea>
            <PageHeader>
              <Title heading={3}>代理列表</Title>
            </PageHeader>

            <SearchSection>
              <SearchRow>
                <Input
                  placeholder="请输入配置名称"
                  value={searchParams.configName || ''}
                  onChange={(value) => setSearchParams(prev => ({ ...prev, configName: value }))}
                  style={{ width: 200 }}
                  prefix={<IconSearch />}
                />
                <Input
                  placeholder="请输入智能体ID"
                  value={searchParams.agentId || ''}
                  onChange={(value) => setSearchParams(prev => ({ ...prev, agentId: value }))}
                  style={{ width: 200 }}
                />
                <Button 
                  type="primary" 
                  onClick={handleSearch}
                  loading={loading}
                >
                  搜索
                </Button>
                <Button onClick={handleReset}>
                  重置
                </Button>
                <div style={{ marginLeft: 'auto' }}>
                  <Button 
                    type="primary" 
                    icon={<IconPlus />}
                    onClick={handleCreate}
                  >
                    新建
                  </Button>
                </div>
              </SearchRow>
            </SearchSection>

            <TableCard>
              <Table
                columns={columns}
                dataSource={dataSource}
                loading={loading}
                pagination={{
                  currentPage: searchParams.pageNum || 1,
                  pageSize: searchParams.pageSize || 10,
                  total: total,
                  showSizeChanger: true,
                  showQuickJumper: true,
                  onChange: handlePageChange
                }}
                rowKey="configId"
                scroll={{ x: 1200 }}
                size="middle"
              />
            </TableCard>
          </ContentArea>
        </div>
      </MainContent>
    </AgentListLayout>
  );
};

export default AgentListPage;