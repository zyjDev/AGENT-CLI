import React, { useState, useEffect } from 'react';
import { EditorRenderer, FreeLayoutEditorProvider } from '@flowgram.ai/free-layout-editor';
import { Toast, Spin } from '@douyinfe/semi-ui';
import { useLocation } from 'react-router-dom';

import '@flowgram.ai/free-layout-editor/index.css';
import './styles/index.css';
import { nodeRegistries } from './nodes';
import { initialData } from './initial-data';
import { useEditorProps } from './hooks';
import { DemoTools } from './components/tools';
import { SidebarProvider, SidebarRenderer } from './components/sidebar';
import { AiAgentDrawService } from './services';
import { getUrlParam } from './utils/url';
import { FlowDocumentJSON } from './typings';

export const Editor = () => {
  const [editorData, setEditorData] = useState<FlowDocumentJSON>(initialData);
  const [loading, setLoading] = useState(false);
  const location = useLocation();
  const editorProps = useEditorProps(editorData, nodeRegistries);

  useEffect(() => {
    const configId = getUrlParam('configId');
    console.log('Editor useEffect - configId:', configId);
    
    if (configId) {
      console.log('开始加载配置数据，configId:', configId);
      setLoading(true);
      
      AiAgentDrawService.getDrawConfig(configId)
        .then((response) => {
          console.log('API响应:', response);
          
          if (response && response.configData) {
            try {
              console.log('原始configData:', response.configData);
              const parsedData = JSON.parse(response.configData);
              console.log('解析后的数据:', parsedData);
              
              setEditorData(parsedData);
              Toast.success('配置数据加载成功');
            } catch (error) {
              console.error('解析配置数据失败:', error);
              console.error('原始数据:', response.configData);
              Toast.error('配置数据格式错误');
              setEditorData(initialData);
            }
          } else {
            console.warn('API响应中没有configData字段:', response);
            Toast.warning('未找到配置数据，使用默认配置');
            setEditorData(initialData);
          }
        })
        .catch((error) => {
          console.error('加载配置数据失败:', error);
          Toast.error('加载配置数据失败');
          setEditorData(initialData);
        })
        .finally(() => {
          setLoading(false);
        });
    } else {
      console.log('没有configId参数，使用默认数据');
      setEditorData(initialData);
    }
  }, [location.search]);

  if (loading) {
    return (
      <div className="doc-free-feature-overview" style={{ 
        display: 'flex', 
        justifyContent: 'center', 
        alignItems: 'center', 
        height: '100%' 
      }}>
        <Spin size="large" tip="正在加载配置数据..." />
      </div>
    );
  }

  return (
    <div className="doc-free-feature-overview">
      <FreeLayoutEditorProvider {...editorProps}>
        <SidebarProvider>
          <div className="demo-container">
            <EditorRenderer className="demo-editor" />
          </div>
          <DemoTools />
          <SidebarRenderer />
        </SidebarProvider>
      </FreeLayoutEditorProvider>
    </div>
  );
};
