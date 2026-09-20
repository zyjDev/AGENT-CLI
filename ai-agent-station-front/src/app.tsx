import React from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import { LoginPage, DashboardPage, AgentConfigPage, AgentListPage, ClientManagement, AiClientApiManagement, AdvisorManagement, RagOrderManagement, ClientModelManagement, ClientSystemPromptManagement, ClientToolMcpManagement } from './pages';
import { isAuthenticated, installAuthFetchInterceptor } from './utils/auth';

// 全局 401 拦截：登录态失效时给出准确提示并跳登录页，而不是让各页面报「请检查网络连接」
installAuthFetchInterceptor();

// 路由保护组件
const ProtectedRoute: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  return isAuthenticated() ? <>{children}</> : <Navigate to="/login" replace />;
};

// 登录重定向组件
const LoginRedirect: React.FC = () => {
  return isAuthenticated() ? <Navigate to="/dashboard" replace /> : <LoginPage />;
};

const App: React.FC = () => {
  return (
    <Router>
      <Routes>
        <Route path="/login" element={<LoginRedirect />} />
        <Route 
          path="/dashboard" 
          element={
            <ProtectedRoute>
              <DashboardPage />
            </ProtectedRoute>
          } 
        />
        <Route 
          path="/agent-config" 
          element={
            <ProtectedRoute>
              <AgentConfigPage />
            </ProtectedRoute>
          } 
        />
        <Route 
          path="/agent-list" 
          element={
            <ProtectedRoute>
              <AgentListPage />
            </ProtectedRoute>
          } 
        />
        <Route 
          path="/client-management" 
          element={
            <ProtectedRoute>
              <ClientManagement />
            </ProtectedRoute>
          } 
        />
        <Route 
          path="/ai-client-api-management" 
          element={
            <ProtectedRoute>
              <AiClientApiManagement />
            </ProtectedRoute>
          } 
        />
        <Route 
          path="/advisor-management" 
          element={
            <ProtectedRoute>
              <AdvisorManagement />
            </ProtectedRoute>
          } 
        />
        <Route 
          path="/rag-order-management" 
          element={
            <ProtectedRoute>
              <RagOrderManagement />
            </ProtectedRoute>
          } 
        />
        <Route 
          path="/client-model-management" 
          element={
            <ProtectedRoute>
              <ClientModelManagement />
            </ProtectedRoute>
          } 
        />
        <Route 
          path="/client-system-prompt-management" 
          element={
            <ProtectedRoute>
              <ClientSystemPromptManagement />
            </ProtectedRoute>
          } 
        />
        <Route 
          path="/client-tool-mcp-management" 
          element={
            <ProtectedRoute>
              <ClientToolMcpManagement />
            </ProtectedRoute>
          } 
        />
        <Route path="/" element={<Navigate to="/login" replace />} />
        <Route path="*" element={<Navigate to="/login" replace />} />
      </Routes>
    </Router>
  );
};

const app = createRoot(document.getElementById('root')!);

app.render(<App />);
