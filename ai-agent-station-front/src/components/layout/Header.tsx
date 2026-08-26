import React from 'react';
import { Button, Dropdown, Avatar, Badge, Space, Typography, Input } from '@douyinfe/semi-ui';
import { IconSearch, IconBell, IconSetting, IconExit, IconMenu, IconMoon, IconSun } from '@douyinfe/semi-icons';
import styled from 'styled-components';
import { theme } from '../../styles/theme';

const { Text } = Typography;

interface HeaderProps {
  onToggleSidebar?: () => void;
  onLogout?: () => void;
  collapsed?: boolean;
}

const HeaderContainer = styled.div`
  height: 72px;
  background: ${theme.colors.bg.primary};
  border-bottom: 1px solid ${theme.colors.border.secondary};
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 ${theme.spacing.lg};
  position: sticky;
  top: 0;
  z-index: 100;
  box-shadow: ${theme.shadows.sm};
  flex-shrink: 0;
`;

const HeaderLeft = styled.div`
  display: flex;
  align-items: center;
  gap: ${theme.spacing.base};
`;

const ToggleButton = styled(Button)`
  width: 40px;
  height: 40px;
  border-radius: ${theme.borderRadius.base};
  border: 1px solid ${theme.colors.border.primary};
  
  &:hover {
    border-color: ${theme.colors.primary};
    color: ${theme.colors.primary};
  }
`;

const SearchContainer = styled.div`
  width: 400px;
  
  @media (max-width: ${theme.breakpoints.md}) {
    width: 200px;
  }
  
  @media (max-width: ${theme.breakpoints.sm}) {
    display: none;
  }
`;

const StyledSearchInput = styled(Input)`
  .semi-input-wrapper {
    border-radius: ${theme.borderRadius.xl};
    background: ${theme.colors.bg.secondary};
    border: 1px solid transparent;
    
    &:hover {
      background: ${theme.colors.bg.primary};
      border-color: ${theme.colors.border.primary};
    }
    
    &:focus-within {
      background: ${theme.colors.bg.primary};
      border-color: ${theme.colors.primary};
      box-shadow: 0 0 0 2px rgba(24, 144, 255, 0.1);
    }
  }
`;

const HeaderRight = styled.div`
  display: flex;
  align-items: center;
  gap: ${theme.spacing.base};
`;

const ActionButton = styled(Button)`
  width: 40px;
  height: 40px;
  border-radius: ${theme.borderRadius.base};
  border: 1px solid transparent;
  
  &:hover {
    background: ${theme.colors.bg.tertiary};
    border-color: ${theme.colors.border.primary};
  }
`;

const UserDropdown = styled.div`
  display: flex;
  align-items: center;
  gap: ${theme.spacing.sm};
  padding: ${theme.spacing.sm} ${theme.spacing.base};
  border-radius: ${theme.borderRadius.base};
  cursor: pointer;
  transition: all ${theme.animation.duration.normal} ${theme.animation.easing.cubic};
  
  &:hover {
    background: ${theme.colors.bg.tertiary};
  }
`;

const UserInfo = styled.div`
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  
  @media (max-width: ${theme.breakpoints.sm}) {
    display: none;
  }
  
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

export const Header: React.FC<HeaderProps> = ({
  onToggleSidebar,
  onLogout,
  collapsed = false,
}) => {
  const userInfo = JSON.parse(localStorage.getItem('userInfo') || '{}');

  const userMenuItems = [
    {
      node: 'item' as const,
      name: '个人设置',
      icon: <IconSetting />,
    },
    {
      node: 'divider' as const,
    },
    {
      node: 'item' as const,
      name: '退出登录',
      icon: <IconExit />,
      onClick: onLogout,
    },
  ];

  return (
    <HeaderContainer>
      <HeaderLeft>
        <ToggleButton
          theme="borderless"
          icon={<IconMenu />}
          onClick={onToggleSidebar}
        />
        
        <SearchContainer>
          <StyledSearchInput
            prefix={<IconSearch />}
            placeholder="搜索功能、代理、文档..."
            showClear
          />
        </SearchContainer>
      </HeaderLeft>
      
      <HeaderRight>
        <ActionButton
          theme="borderless"
          icon={<IconSun />}
          title="切换主题"
        />
        
        <Badge count={3}>
          <ActionButton
            theme="borderless"
            icon={<IconBell />}
            title="消息通知"
          />
        </Badge>
        
        <Dropdown
          trigger="click"
          position="bottomRight"
          menu={userMenuItems}
        >
          <UserDropdown>
            <UserInfo>
              <Text className="username">{userInfo.username || '用户'}</Text>
              <Text className="role">管理员</Text>
            </UserInfo>
            <Avatar size="small" color="blue">
              {userInfo.username?.[0]?.toUpperCase() || 'U'}
            </Avatar>
          </UserDropdown>
        </Dropdown>
      </HeaderRight>
    </HeaderContainer>
  );
};