# API 配置说明

## 概述

本目录包含了项目的统一 API 配置，用于管理所有 API 端点、请求头和环境相关的配置。

## 文件结构

```
config/
├── api.ts          # API 配置文件
├── index.ts        # 配置入口文件
└── README.md       # 说明文档
```

## 使用方法

### 1. 导入配置

```typescript
import { API_ENDPOINTS, DEFAULT_HEADERS, API_CONFIG } from '../config';
```

### 2. 使用 API 端点

```typescript
// 使用预定义的端点
const response = await fetch(`${API_ENDPOINTS.AI_CLIENT.BASE}${API_ENDPOINTS.AI_CLIENT.QUERY_ALL}`, {
  method: 'GET',
  headers: DEFAULT_HEADERS,
});
```

### 3. 添加新的 API 端点

在 `api.ts` 文件的 `API_ENDPOINTS` 对象中添加新的模块：

```typescript
export const API_ENDPOINTS = {
  // 现有的 AI_CLIENT 配置...
  
  // 新增模块
  USER: {
    BASE: `${API_CONFIG.BASE_DOMAIN}/api/${API_CONFIG.API_VERSION}/user`,
    LOGIN: '/login',
    LOGOUT: '/logout',
    PROFILE: '/profile',
  },
} as const;
```

## 环境配置

- **开发环境**: `http://localhost:8080`
- **生产环境**: 需要在 `api.ts` 中更新 `BASE_DOMAIN` 的生产环境地址

## 优势

1. **统一管理**: 所有 API 配置集中在一个地方
2. **环境切换**: 自动根据 NODE_ENV 切换不同环境的配置
3. **类型安全**: 使用 TypeScript 提供完整的类型支持
4. **易于维护**: 修改 API 地址只需要在配置文件中更新
5. **可扩展**: 可以轻松添加新的 API 模块和配置项