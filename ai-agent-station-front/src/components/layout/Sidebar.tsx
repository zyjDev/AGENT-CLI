import React from 'react';
import { Nav, Avatar, Typography } from '@douyinfe/semi-ui';
import { IconHome, IconSetting, IconBranch, IconApps, IconActivity, IconFolder } from '@douyinfe/semi-icons';
import styled from 'styled-components';
import { theme } from '../../styles/theme';

const { Text } = Typography;

interface SidebarProps {
  selectedKey?: string;
  onSelect?: (key: string) => void;
  collapsed?: boolean;
}

const SidebarContainer = styled.div<{ $collapsed: boolean }>`
  width: ${props => props.$collapsed ? '80px' : '280px'};
  height: 100vh;
  background: ${theme.colors.bg.primary};
  border-right: 1px solid ${theme.colors.border.secondary};
  display: flex;
  flex-direction: column;
  transition: width ${theme.animation.duration.normal} ${theme.animation.easing.cubic};
  position: fixed;
  left: 0;
  top: 0;
  z-index: 1000;
  overflow-y: auto;
`;

const SidebarHeader = styled.div<{ $collapsed: boolean }>`
  padding: ${theme.spacing.lg};
  border-bottom: 1px solid ${theme.colors.border.secondary};
  display: flex;
  align-items: center;
  gap: ${theme.spacing.base};
  min-height: 72px;
`;

const Logo = styled.div`
  width: 40px;
  height: 40px;
  background: ${theme.colors.gradient.primary};
  border-radius: ${theme.borderRadius.base};
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  
  .semi-icon {
    color: white;
    font-size: 20px;
  }
`;

const BrandInfo = styled.div<{ $collapsed: boolean }>`
  display: ${props => props.$collapsed ? 'none' : 'block'};
  
  h4 {
    margin: 0;
    color: ${theme.colors.text.primary};
    font-weight: ${theme.typography.fontWeight.semibold};
    font-size: ${theme.typography.fontSize.base};
  }
  
  p {
    margin: 0;
    color: ${theme.colors.text.tertiary};
    font-size: ${theme.typography.fontSize.sm};
  }
`;

const SidebarContent = styled.div`
  flex: 1;
  padding: ${theme.spacing.base} 0;
  overflow-y: auto;
`;

const StyledNav = styled(Nav)<{ $collapsed: boolean }>`
  background: transparent;
  border: none;
  
  .semi-nav-item {
    margin: 4px ${theme.spacing.base};
    border-radius: ${theme.borderRadius.base};
    transition: all ${theme.animation.duration.normal} ${theme.animation.easing.cubic};
    
    &:hover {
      background: ${theme.colors.bg.tertiary};
    }
    
    &.semi-nav-item-selected {
      background: ${theme.colors.primary};
      color: white;
      
      .semi-icon {
        color: white;
      }
      
      .semi-nav-item-text {
        color: white;
      }
    }
    
    .semi-nav-item-text {
      display: ${props => props.$collapsed ? 'none' : 'block'};
    }
  }
  
  .semi-nav-sub {
    .semi-nav-item {
      padding-left: ${props => props.$collapsed ? theme.spacing.base : theme.spacing.xl};
    }
  }
`;

const SidebarFooter = styled.div<{ $collapsed: boolean }>`
  padding: ${theme.spacing.lg};
  border-top: 1px solid ${theme.colors.border.secondary};
  display: flex;
  align-items: center;
  gap: ${theme.spacing.base};
`;

const UserInfo = styled.div<{ $collapsed: boolean }>`
  display: ${props => props.$collapsed ? 'none' : 'flex'};
  flex-direction: column;
  flex: 1;
  
  .username {
    color: ${theme.colors.text.primary};
    font-weight: ${theme.typography.fontWeight.medium};
    font-size: ${theme.typography.fontSize.sm};
    margin: 0;
  }
  
  .role {
    color: ${theme.colors.text.tertiary};
    font-size: ${theme.typography.fontSize.xs};
    margin: 0;
  }
`;

const menuItems = [
  {
    itemKey: 'dashboard',
    text: '工作台',
    icon: <IconHome />,
  },
  {
    itemKey: 'agents',
    text: '代理管理',
    icon: <IconApps />,
    items: [
      {
        itemKey: 'agent-list',
        text: '代理列表',
      },
      {
        itemKey: 'agent-config',
        text: '代理配置',
      },
    ],
  },
  {
    itemKey: 'resources',
    text: '资源管理',
    icon: <IconFolder />,
    items: [
      {
        itemKey: 'client-management',
        text: '客户端管理',
      },
      {
        itemKey: 'advisor-management',
        text: '顾问管理',
      },
      {
        itemKey: 'rag-order-management',
        text: '知识库配置管理',
      },
      {
        itemKey: 'client-model-management',
        text: '模型管理',
      },
      {
        itemKey: 'ai-client-api-management',
        text: '模型API管理',
      },
      {
        itemKey: 'client-system-prompt-management',
        text: '系统提示词管理',
      },
      {
        itemKey: 'client-tool-mcp-management',
        text: 'MCP工具管理',
      },
    ],
  },
  {
    itemKey: 'analytics',
    text: '数据分析【样例】',
    icon: <IconActivity />,
    items: [
      {
        itemKey: 'performance',
        text: '性能监控',
      },
      {
        itemKey: 'usage',
        text: '使用统计',
      },
    ],
  },
  {
    itemKey: 'settings',
    text: '系统设置【样例】',
    icon: <IconSetting />,
    items: [
      {
        itemKey: 'profile',
        text: '个人设置',
      },
      {
        itemKey: 'system',
        text: '系统配置',
      },
    ],
  },
];

export const Sidebar: React.FC<SidebarProps> = ({
  selectedKey = 'dashboard',
  onSelect,
  collapsed = false,
}) => {
  const userInfo = JSON.parse(localStorage.getItem('userInfo') || '{}');

  return (
    <SidebarContainer $collapsed={collapsed}>
      <SidebarHeader $collapsed={collapsed}>
        <Logo>
          <IconBranch />
        </Logo>
        <BrandInfo $collapsed={collapsed}>
          <h4>AI Agent Station</h4>
          <p>智能代理管理平台</p>
        </BrandInfo>
      </SidebarHeader>
      
      <SidebarContent>
        <StyledNav
          $collapsed={collapsed}
          selectedKeys={[selectedKey]}
          onSelect={({ selectedKeys }: { selectedKeys: string[] }) => {
            const key = selectedKeys[0] as string;
            onSelect?.(key);
          }}
          items={menuItems}
        />
      </SidebarContent>
      
      <SidebarFooter $collapsed={collapsed}>
        <Avatar size="small" color="blue">
          {userInfo.username?.[0]?.toUpperCase() || 'U'}
        </Avatar>
        <UserInfo $collapsed={collapsed}>
          <Text className="username">{userInfo.username || '用户'}</Text>
          <Text className="role">管理员</Text>
        </UserInfo>
      </SidebarFooter>
    </SidebarContainer>
  );
};