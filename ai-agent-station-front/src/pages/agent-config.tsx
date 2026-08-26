import React, { useEffect, useState } from 'react';
import { Layout, Button, Typography, Space, Toast, Breadcrumb } from '@douyinfe/semi-ui';
import { IconArrowLeft, IconSave, IconPlay, IconStop } from '@douyinfe/semi-icons';
import { useNavigate } from 'react-router-dom';
import styled from 'styled-components';
import { theme } from '../styles/theme';
import { Card } from '../components/common';
import { Sidebar, Header } from '../components/layout';
import { Editor } from '../editor';

const { Content } = Layout;
const { Title, Text } = Typography;

// 样式组件
const AgentConfigLayout = styled(Layout)`
  min-height: 100vh;
  background: ${theme.colors.bg.secondary};
  position: relative;
`;

const MainContent = styled.div`
  display: flex;
  flex: 1;
  height: 100vh;
  overflow: hidden;
`;

const ContentWrapper = styled.div<{ $sidebarWidth: number }>`
  flex: 1;
  display: flex;
  flex-direction: column;
  margin-left: ${props => props.$sidebarWidth}px;
  height: 100vh;
  overflow: hidden;
  transition: margin-left ${theme.animation.duration.normal} ${theme.animation.easing.cubic};
`;

const ContentArea = styled(Content)`
  padding: ${theme.spacing.lg};
  background: ${theme.colors.bg.secondary};
  overflow-y: auto;
  flex: 1;
`;

const PageHeader = styled(Card)`
  margin-bottom: ${theme.spacing.lg};
  background: ${theme.colors.bg.primary};
  border: 1px solid ${theme.colors.border.primary};
`;

const HeaderContent = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
`;

const HeaderLeft = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${theme.spacing.sm};
`;

const HeaderActions = styled.div`
  display: flex;
  gap: ${theme.spacing.base};
  align-items: center;
`;

const ActionButton = styled(Button)<{ $variant?: 'primary' | 'success' | 'danger' }>`
  border-radius: ${theme.borderRadius.base};
  font-weight: ${theme.typography.fontWeight.medium};
  transition: all ${theme.animation.duration.normal} ${theme.animation.easing.cubic};
  
  ${props => props.$variant === 'primary' && `
    background: ${theme.colors.gradient.primary};
    border: none;
    color: white;
    
    &:hover {
      background: ${theme.colors.gradient.primary};
      transform: translateY(-1px);
      box-shadow: ${theme.shadows.md};
    }
  `}
  
  ${props => props.$variant === 'success' && `
    background: #52c41a;
    border: none;
    color: white;
    
    &:hover {
      background: #73d13d;
      transform: translateY(-1px);
      box-shadow: ${theme.shadows.md};
    }
  `}
  
  ${props => props.$variant === 'danger' && `
    background: #ff4d4f;
    border: none;
    color: white;
    
    &:hover {
      background: #ff7875;
      transform: translateY(-1px);
      box-shadow: ${theme.shadows.md};
    }
  `}
`;

const EditorSection = styled(Card)`
  flex: 1;
  min-height: 600px;
  padding: 0;
  overflow: hidden;
  border: 1px solid ${theme.colors.border.primary};
  margin-bottom: ${theme.spacing.lg};
`;

const EditorContainer = styled.div`
  height: 100%;
  width: 100%;
  
  .doc-free-feature-overview {
    height: 100%;
    width: 100%;
  }
  
  .demo-container {
    height: 100%;
    width: 100%;
  }
  
  .demo-editor {
    height: 100% !important;
    width: 100% !important;
  }
`;

const StatusIndicator = styled.div<{ $status: 'idle' | 'running' | 'stopped' }>`
  display: flex;
  align-items: center;
  gap: ${theme.spacing.xs};
  padding: ${theme.spacing.xs} ${theme.spacing.sm};
  border-radius: ${theme.borderRadius.base};
  font-size: ${theme.typography.fontSize.sm};
  font-weight: ${theme.typography.fontWeight.medium};
  
  ${props => props.$status === 'idle' && `
    background: ${theme.colors.bg.tertiary};
    color: ${theme.colors.text.secondary};
  `}
  
  ${props => props.$status === 'running' && `
    background: rgba(82, 196, 26, 0.1);
    color: #52c41a;
  `}
  
  ${props => props.$status === 'stopped' && `
    background: rgba(255, 77, 79, 0.1);
    color: #ff4d4f;
  `}
`;

const StatusDot = styled.div<{ $status: 'idle' | 'running' | 'stopped' }>`
  width: 8px;
  height: 8px;
  border-radius: 50%;
  
  ${props => props.$status === 'idle' && `
    background: ${theme.colors.text.tertiary};
  `}
  
  ${props => props.$status === 'running' && `
    background: #52c41a;
    animation: pulse 2s infinite;
  `}
  
  ${props => props.$status === 'stopped' && `
    background: #ff4d4f;
  `}
  
  @keyframes pulse {
    0% {
      opacity: 1;
    }
    50% {
      opacity: 0.5;
    }
    100% {
      opacity: 1;
    }
  }
`;

interface UserInfo {
  username: string;
  loginTime: string;
  token: string;
  isTestAccount?: boolean;
}

export const AgentConfigPage: React.FC = () => {
  const navigate = useNavigate();
  const [collapsed, setCollapsed] = useState(false);
  const [userInfo, setUserInfo] = useState<UserInfo | null>(null);
  const [agentStatus, setAgentStatus] = useState<'idle' | 'running' | 'stopped'>('idle');
  const [isSaving, setIsSaving] = useState(false);

  useEffect(() => {
    const token = localStorage.getItem('token');
    const storedUserInfo = localStorage.getItem('userInfo');
    
    if (!token || !storedUserInfo) {
      Toast.error('请先登录');
      navigate('/login');
      return;
    }
    
    try {
      const parsedUserInfo = JSON.parse(storedUserInfo);
      setUserInfo(parsedUserInfo);
    } catch (error) {
      Toast.error('用户信息解析失败');
      navigate('/login');
    }
  }, [navigate]);

  const handleLogout = () => {
    localStorage.removeItem('token');
    localStorage.removeItem('userInfo');
    localStorage.removeItem('isLoggedIn');
    Toast.success('已退出登录');
    navigate('/login');
  };

  const handleNavigation = (path: string) => {
    // 处理侧边栏菜单项的导航
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

  const handleBack = () => {
    navigate('/dashboard');
  };

  const handleSave = async () => {
    setIsSaving(true);
    try {
      // 模拟保存操作
      await new Promise(resolve => setTimeout(resolve, 1000));
      Toast.success('配置已保存');
    } catch (error) {
      Toast.error('保存失败');
    } finally {
      setIsSaving(false);
    }
  };

  const handleRun = () => {
    if (agentStatus === 'running') {
      setAgentStatus('stopped');
      Toast.info('Agent已停止');
    } else {
      setAgentStatus('running');
      Toast.success('Agent已启动');
    }
  };

  const getStatusText = () => {
    switch (agentStatus) {
      case 'running':
        return '运行中';
      case 'stopped':
        return '已停止';
      default:
        return '待启动';
    }
  };

  if (!userInfo) {
    return null;
  }

  const sidebarWidth = collapsed ? 80 : 280;

  return (
    <AgentConfigLayout>
      <Sidebar
        selectedKey="agent-config"
        onSelect={handleNavigation}
        collapsed={collapsed}
      />
      <MainContent>
        <ContentWrapper $sidebarWidth={sidebarWidth}>
          <Header
            onToggleSidebar={() => setCollapsed(!collapsed)}
            onLogout={handleLogout}
            collapsed={collapsed}
          />
          
          <ContentArea>
            <PageHeader>
              <HeaderContent>
                <HeaderLeft>
                  <Breadcrumb>
                    <Breadcrumb.Item onClick={handleBack} style={{ cursor: 'pointer' }}>
                      控制台
                    </Breadcrumb.Item>
                    <Breadcrumb.Item>Agent配置</Breadcrumb.Item>
                  </Breadcrumb>
                  
                  <Space vertical spacing="tight">
                    <Title heading={3} style={{ margin: 0 }}>
                      Agent执行流程配置
                    </Title>
                    <Text type="secondary">
                      通过可视化界面配置AI Agent的执行流程，支持拖拽式操作
                    </Text>
                  </Space>
                </HeaderLeft>
                
                <HeaderActions>
                  <StatusIndicator $status={agentStatus}>
                    <StatusDot $status={agentStatus} />
                    {getStatusText()}
                  </StatusIndicator>
                  
                  <ActionButton
                    icon={<IconSave />}
                    loading={isSaving}
                    onClick={handleSave}
                    $variant="primary"
                  >
                    保存配置
                  </ActionButton>
                  
                  <ActionButton
                    icon={agentStatus === 'running' ? <IconStop /> : <IconPlay />}
                    onClick={handleRun}
                    $variant={agentStatus === 'running' ? 'danger' : 'success'}
                  >
                    {agentStatus === 'running' ? '停止' : '启动'}
                  </ActionButton>
                  
                  <Button
                    icon={<IconArrowLeft />}
                    onClick={handleBack}
                  >
                    返回
                  </Button>
                </HeaderActions>
              </HeaderContent>
            </PageHeader>
            
            <EditorSection>
              <EditorContainer>
                <Editor />
              </EditorContainer>
            </EditorSection>
          </ContentArea>
        </ContentWrapper>
      </MainContent>
    </AgentConfigLayout>
  );
};