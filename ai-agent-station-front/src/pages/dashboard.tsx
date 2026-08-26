import React, { useEffect, useState } from 'react';
import { Layout, Button, Card, Typography, Space, Toast, Row, Col, Progress } from '@douyinfe/semi-ui';
import { IconApps, IconActivity, IconUser, IconBranch } from '@douyinfe/semi-icons';
import { useNavigate } from 'react-router-dom';
import styled from 'styled-components';
import { theme } from '../styles/theme';
import { Card as CustomCard } from '../components/common';
import { Sidebar, Header } from '../components/layout';
import { DataStatisticsService, DataStatisticsResponseDTO } from '../services/data-statistics-service';

const { Content } = Layout;
const { Title, Text } = Typography;

// 样式组件
const DashboardLayout = styled(Layout)`
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

const WelcomeSection = styled(CustomCard)`
  margin-bottom: ${theme.spacing.xl};
  background: ${theme.colors.gradient.primary};
  color: white;
  border: none !important;
  
  .semi-typography {
    color: white !important;
  }
`;

const StatsGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: ${theme.spacing.lg};
  margin-bottom: ${theme.spacing.xl};
`;

const StatCard = styled(CustomCard)`
  text-align: center;
  transition: all ${theme.animation.duration.normal} ${theme.animation.easing.cubic};
  
  &:hover {
    transform: translateY(-4px);
    box-shadow: ${theme.shadows.lg};
  }
`;

const StatIcon = styled.div<{ $color: string }>`
  width: 64px;
  height: 64px;
  border-radius: ${theme.borderRadius.xl};
  background: ${props => props.$color};
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 auto ${theme.spacing.base};
  
  .semi-icon {
    font-size: 28px;
    color: white;
  }
`;

const StatValue = styled.div`
  font-size: ${theme.typography.fontSize['3xl']};
  font-weight: ${theme.typography.fontWeight.bold};
  color: ${theme.colors.text.primary};
  margin-bottom: ${theme.spacing.sm};
`;

const StatLabel = styled.div`
  font-size: ${theme.typography.fontSize.base};
  color: ${theme.colors.text.secondary};
`;

const QuickActionsGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
  gap: ${theme.spacing.lg};
  margin-bottom: ${theme.spacing.xl};
`;

const ActionCard = styled(CustomCard)`
  cursor: pointer;
  transition: all ${theme.animation.duration.normal} ${theme.animation.easing.cubic};
  
  &:hover {
    transform: translateY(-2px);
    box-shadow: ${theme.shadows.md};
    border-color: ${theme.colors.primary};
  }
`;

const ActionIcon = styled.div<{ $color: string }>`
  width: 48px;
  height: 48px;
  border-radius: ${theme.borderRadius.base};
  background: ${props => props.$color};
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: ${theme.spacing.base};
  
  .semi-icon {
    font-size: 20px;
    color: white;
  }
`;

const ActionTitle = styled(Title)`
  margin-bottom: ${theme.spacing.sm} !important;
  color: ${theme.colors.text.primary};
`;

const ActionDescription = styled(Text)`
  color: ${theme.colors.text.secondary};
  line-height: ${theme.typography.lineHeight.relaxed};
`;

const RecentActivityCard = styled(CustomCard)`
  height: 400px;
`;

const ActivityItem = styled.div`
  display: flex;
  align-items: center;
  gap: ${theme.spacing.base};
  padding: ${theme.spacing.base} 0;
  border-bottom: 1px solid ${theme.colors.border.secondary};
  
  &:last-child {
    border-bottom: none;
  }
`;

const ActivityIcon = styled.div<{ $color: string }>`
  width: 32px;
  height: 32px;
  border-radius: ${theme.borderRadius.base};
  background: ${props => props.$color};
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  
  .semi-icon {
    font-size: 14px;
    color: white;
  }
`;

const ActivityContent = styled.div`
  flex: 1;
`;

const ActivityTitle = styled.div`
  font-weight: ${theme.typography.fontWeight.medium};
  color: ${theme.colors.text.primary};
  margin-bottom: 2px;
`;

const ActivityTime = styled.div`
  font-size: ${theme.typography.fontSize.sm};
  color: ${theme.colors.text.tertiary};
`;

interface UserInfo {
  username: string;
  loginTime: string;
  token: string;
  isTestAccount?: boolean;
}

export const DashboardPage: React.FC = () => {
  const navigate = useNavigate();
  const [collapsed, setCollapsed] = useState(false);
  const [userInfo, setUserInfo] = useState<UserInfo | null>(null);
  const [statisticsData, setStatisticsData] = useState<DataStatisticsResponseDTO | null>(null);
  const [loading, setLoading] = useState(true);

  // 获取统计数据
  const fetchStatisticsData = async () => {
    try {
      setLoading(true);
      const data = await DataStatisticsService.getDataStatistics();
      setStatisticsData(data);
    } catch (error) {
      console.error('获取统计数据失败:', error);
      Toast.error('获取统计数据失败');
    } finally {
      setLoading(false);
    }
  };

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

    // 获取统计数据
    fetchStatisticsData();
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

  const statsData = statisticsData ? [
    {
      icon: <IconApps />,
      value: statisticsData.activeAgentCount.toString(),
      label: '活跃代理',
      color: theme.colors.gradient.primary,
    },
    {
      icon: <IconActivity />,
      value: statisticsData.todayRequestCount.toLocaleString(),
      label: '今日请求',
      color: theme.colors.gradient.secondary,
    },
    {
      icon: <IconUser />,
      value: `${statisticsData.successRate.toFixed(1)}%`,
      label: '成功率',
      color: theme.colors.gradient.tertiary,
    },
    {
      icon: <IconBranch />,
      value: statisticsData.runningTaskCount.toString(),
      label: '运行中任务',
      color: '#52c41a',
    },
  ] : [
    {
      icon: <IconApps />,
      value: '-',
      label: '活跃代理',
      color: theme.colors.gradient.primary,
    },
    {
      icon: <IconActivity />,
      value: '-',
      label: '今日请求',
      color: theme.colors.gradient.secondary,
    },
    {
      icon: <IconUser />,
      value: '-',
      label: '成功率',
      color: theme.colors.gradient.tertiary,
    },
    {
      icon: <IconBranch />,
      value: '-',
      label: '运行中任务',
      color: '#52c41a',
    },
  ];

  const quickActions = [
    {
      icon: <IconApps />,
      title: '创建新代理',
      description: '快速创建和配置新的AI代理',
      color: theme.colors.gradient.primary,
      onClick: () => handleNavigation('/agent-config'),
    },
    {
      icon: <IconActivity />,
      title: '查看分析',
      description: '查看代理性能和使用统计',
      color: theme.colors.gradient.secondary,
      onClick: () => handleNavigation('/analytics'),
    },
    {
      icon: <IconUser />,
      title: '管理用户',
      description: '管理用户权限和访问控制',
      color: theme.colors.gradient.tertiary,
      onClick: () => handleNavigation('/users'),
    },
    {
      icon: <IconBranch />,
      title: '系统设置',
      description: '配置系统参数和偏好设置',
      color: '#52c41a',
      onClick: () => handleNavigation('/settings'),
    },
  ];

  const recentActivities = [
    {
      icon: <IconApps />,
      title: '代理 "客服助手" 已启动',
      time: '2分钟前',
      color: theme.colors.gradient.primary,
    },
    {
      icon: <IconActivity />,
      title: '完成了 156 个对话请求',
      time: '15分钟前',
      color: theme.colors.gradient.secondary,
    },
    {
      icon: <IconUser />,
      title: '新用户 "张三" 已注册',
      time: '1小时前',
      color: theme.colors.gradient.tertiary,
    },
    {
      icon: <IconBranch />,
      title: '系统配置已更新',
      time: '2小时前',
      color: '#52c41a',
    },
  ];

  if (!userInfo) {
    return null;
  }

  return (
    <DashboardLayout>
      <Sidebar
        selectedKey="dashboard"
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
            <WelcomeSection padding="xl">
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                <Space vertical align="start" spacing="loose">
                  <Title heading={2}>
                    欢迎回来，{userInfo.username}！
                  </Title>
                  <Text>
                    今天是美好的一天，让我们开始管理您的AI代理吧。
                  </Text>
                </Space>
                <Button 
                  theme="borderless" 
                  type="primary" 
                  loading={loading}
                  onClick={fetchStatisticsData}
                  style={{ color: 'white', borderColor: 'white' }}
                >
                  刷新数据
                </Button>
              </div>
            </WelcomeSection>
            
            <StatsGrid>
              {statsData.map((stat, index) => (
                <StatCard key={index} hover>
                  <StatIcon $color={stat.color}>
                    {stat.icon}
                  </StatIcon>
                  <StatValue>
                    {loading ? '...' : stat.value}
                  </StatValue>
                  <StatLabel>{stat.label}</StatLabel>
                </StatCard>
              ))}
            </StatsGrid>
            
            <Row gutter={[24, 24]}>
              <Col span={16}>
                <Title heading={4} style={{ marginBottom: theme.spacing.lg }}>
                  快速操作
                </Title>
                <QuickActionsGrid>
                  {quickActions.map((action, index) => (
                    <ActionCard key={index} hover onClick={action.onClick}>
                      <ActionIcon $color={action.color}>
                        {action.icon}
                      </ActionIcon>
                      <ActionTitle heading={5}>
                        {action.title}
                      </ActionTitle>
                      <ActionDescription>
                        {action.description}
                      </ActionDescription>
                    </ActionCard>
                  ))}
                </QuickActionsGrid>
              </Col>
              
              <Col span={8}>
                <Title heading={4} style={{ marginBottom: theme.spacing.lg }}>
                  最近活动
                </Title>
                <RecentActivityCard>
                   <Space vertical style={{ width: '100%' }} spacing="tight">
                     {recentActivities.map((activity, index) => (
                      <ActivityItem key={index}>
                        <ActivityIcon $color={activity.color}>
                          {activity.icon}
                        </ActivityIcon>
                        <ActivityContent>
                          <ActivityTitle>{activity.title}</ActivityTitle>
                          <ActivityTime>{activity.time}</ActivityTime>
                        </ActivityContent>
                      </ActivityItem>
                    ))}
                  </Space>
                </RecentActivityCard>
              </Col>
            </Row>
          </ContentArea>
        </div>
      </MainContent>
    </DashboardLayout>
  );
};