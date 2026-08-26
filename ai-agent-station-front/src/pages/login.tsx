import React, { useState } from 'react';
import { Button, Form, Input, Toast, Typography } from '@douyinfe/semi-ui';
import { IconEyeClosed, IconEyeOpened, IconLock, IconSafe, IconUser } from '@douyinfe/semi-icons';
import { useNavigate } from 'react-router-dom';
import styled from 'styled-components';
import { theme } from '../styles/theme';
import { Card } from '../components/common';
import { AdminUserService } from '../services';

const { Title, Text } = Typography;

// 样式组件
const LoginContainer = styled.div`
  min-height: 100vh;
  background: ${theme.colors.bg.secondary};
  display: flex;
  align-items: center;
  justify-content: center;
  padding: ${theme.spacing.lg};
  position: relative;
  
  &::before {
    content: '';
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
    background: ${theme.colors.gradient.primary};
    opacity: 0.05;
    pointer-events: none;
  }
`;

const LoginWrapper = styled.div`
  width: 100%;
  max-width: 1200px;
  display: grid;
  grid-template-columns: 1fr 400px;
  gap: ${theme.spacing['3xl']};
  align-items: center;
  
  @media (max-width: ${theme.breakpoints.lg}) {
    grid-template-columns: 1fr;
    max-width: 400px;
    gap: ${theme.spacing.xl};
  }
`;

const BrandSection = styled.div`
  text-align: center;
  
  @media (max-width: ${theme.breakpoints.lg}) {
    display: none;
  }
`;

const BrandLogo = styled.div`
  width: 120px;
  height: 120px;
  margin: 0 auto ${theme.spacing.xl};
  background: ${theme.colors.gradient.primary};
  border-radius: ${theme.borderRadius['2xl']};
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: ${theme.shadows.lg};
  
  .semi-icon {
    font-size: 48px;
    color: white;
  }
`;

const BrandTitle = styled(Title)`
  color: ${theme.colors.text.primary};
  margin-bottom: ${theme.spacing.base} !important;
  font-weight: ${theme.typography.fontWeight.bold};
`;

const BrandDescription = styled(Text)`
  color: ${theme.colors.text.secondary};
  font-size: ${theme.typography.fontSize.lg};
  line-height: ${theme.typography.lineHeight.relaxed};
`;

const LoginCard = styled(Card)`
  padding: ${theme.spacing['2xl']} !important;
  box-shadow: ${theme.shadows.xl} !important;
  border: none !important;
`;

const LoginHeader = styled.div`
  text-align: center;
  margin-bottom: ${theme.spacing.xl};
`;

const LoginTitle = styled(Title)`
  color: ${theme.colors.text.primary};
  margin-bottom: ${theme.spacing.sm} !important;
  font-weight: ${theme.typography.fontWeight.bold};
`;

const LoginSubtitle = styled(Text)`
  color: ${theme.colors.text.secondary};
  font-size: ${theme.typography.fontSize.base};
`;

const StyledForm = styled(Form)`
  .semi-form-field {
    margin-bottom: ${theme.spacing.lg};
  }
`;
styled(Input)`
  height: 48px;
  border-radius: ${theme.borderRadius.base};
  border: 1px solid ${theme.colors.border.primary};
  
  &:focus {
    border-color: ${theme.colors.primary};
    box-shadow: 0 0 0 2px rgba(24, 144, 255, 0.1);
  }
  
  .semi-input-prefix {
    color: ${theme.colors.text.tertiary};
  }
`;
const LoginButton = styled(Button)`
  width: 100%;
  height: 48px;
  border-radius: ${theme.borderRadius.base};
  background: ${theme.colors.gradient.primary} !important;
  border: none !important;
  color: white !important;
  font-weight: ${theme.typography.fontWeight.medium};
  font-size: ${theme.typography.fontSize.base};
  
  &:hover {
    opacity: 0.9;
    transform: translateY(-1px);
  }
`;

const QuickLoginButton = styled(Button)`
  width: 100%;
  height: 40px;
  border-radius: ${theme.borderRadius.base};
  border: 1px solid ${theme.colors.border.primary};
  color: ${theme.colors.text.secondary};
  background: ${theme.colors.bg.primary};
  
  &:hover {
    border-color: ${theme.colors.primary};
    color: ${theme.colors.primary};
  }
`;

const Divider = styled.div`
  display: flex;
  align-items: center;
  margin: ${theme.spacing.xl} 0;
  
  &::before,
  &::after {
    content: '';
    flex: 1;
    height: 1px;
    background: ${theme.colors.border.secondary};
  }
  
  span {
    padding: 0 ${theme.spacing.base};
    color: ${theme.colors.text.tertiary};
    font-size: ${theme.typography.fontSize.sm};
  }
`;

const LoginPage: React.FC = () => {
  const navigate = useNavigate();
  const [loading, setLoading] = useState(false);
  const [showPassword, setShowPassword] = useState(false);

  const handleLogin = async (values: Record<string, any>) => {
    setLoading(true);
    try {
      if (!values.username || !values.password) {
        Toast.error('请输入账号和密码');
        return;
      }

      // 调用后端登录校验接口
      const isLoginSuccess = await AdminUserService.validateAdminUserLogin({
        username: values.username,
        password: values.password
      });
      
      if (isLoginSuccess) {
        const userInfo = {
          username: values.username,
          loginTime: new Date().toISOString(),
          token: 'token-' + Date.now()
        };
        localStorage.setItem('token', userInfo.token);
        localStorage.setItem('userInfo', JSON.stringify(userInfo));
        localStorage.setItem('isLoggedIn', 'true');
        Toast.success('登录成功！');
        navigate('/dashboard');
      } else {
        Toast.error('账号或密码错误，请重试');
      }
    } catch (error) {
      console.error('登录失败:', error);
      Toast.error('登录失败，请检查网络连接或稍后重试');
    } finally {
      setLoading(false);
    }
  };

  const handleTestLogin = () => {
    handleLogin({ username: 'admin', password: '123456' });
  };

  return (
    <LoginContainer>
      <LoginWrapper>
        <BrandSection>
          <BrandLogo>
            <IconSafe />
          </BrandLogo>
          <BrandTitle heading={1}>
            AI Agent Station
          </BrandTitle>
          <BrandDescription>
            智能代理管理平台，为您提供专业的AI代理配置和管理服务。
            <br />
            简单易用，功能强大，助力您的业务智能化升级。
          </BrandDescription>
        </BrandSection>
        
        <LoginCard>
          <LoginHeader>
            <LoginTitle heading={3}>
              欢迎登录
            </LoginTitle>
            <LoginSubtitle>
              请输入您的账号和密码
            </LoginSubtitle>
          </LoginHeader>
          
          <StyledForm onSubmit={handleLogin}>
            <Form.Input
              field="username"
              placeholder="请输入账号"
              prefix={<IconUser />}
              size="large"
              rules={[
                { required: true, message: '请输入账号' },
                { min: 3, message: '账号至少3个字符' }
              ]}
            />
            <Form.Input
              field="password"
              type={showPassword ? 'text' : 'password'}
              placeholder="请输入密码"
              prefix={<IconLock />}
              size="large"
              suffix={
                <Button
                  theme="borderless"
                  icon={showPassword ? <IconEyeClosed /> : <IconEyeOpened />}
                  onClick={() => setShowPassword(!showPassword)}
                  style={{ padding: '4px' }}
                />
              }
              rules={[
                { required: true, message: '请输入密码' },
                { min: 6, message: '密码至少6个字符' }
              ]}
            />
            
            <LoginButton
              type="primary"
              htmlType="submit"
              loading={loading}
            >
              立即登录
            </LoginButton>
          </StyledForm>
          
          <Divider>
            <span>或</span>
          </Divider>
          
          <QuickLoginButton
            onClick={handleTestLogin}
            loading={loading}
          >
            使用admin账号登录
          </QuickLoginButton>
        </LoginCard>
      </LoginWrapper>
    </LoginContainer>
  );
};
export default LoginPage;
