# AGENTS.md - AI Agent Station Study 项目指南

## 项目概述

**项目名称**: AI Agent Station Study - AI智能体学习平台  
**架构模式**: DDD (领域驱动设计)  
**技术栈**: Java 17 + Spring Boot 3.4.3 + MyBatis + MySQL + PostgreSQL  
**开发者**: xiaofuge (fuzhengwei)

## 项目结构

`
ai-agent-station-study/
├── ai-agent-station-front/          # 前端静态资源 (HTML5 + JS + Tailwind CSS)
├── ai-agent-station-study-api/      # API 接口定义层
├── ai-agent-station-study-app/      # 应用层 (启动类、配置)
├── ai-agent-station-study-domain/   # 领域层 (核心业务逻辑)
├── ai-agent-station-study-infrastructure/ # 基础设施层 (数据访问、外部服务)
├── ai-agent-station-study-trigger/  # 触发器层 (HTTP接口、定时任务)
├── ai-agent-station-study-types/    # 类型定义层 (DTO、枚举等)
├── data/                            # 数据相关资源
└── docs/                            # 文档资源
`

## 核心功能

1. **Auto Agent 智能对话**: 支持流式响应的AI智能体自动对话功能
2. **SSE 流式响应**: Server-Sent Events 实时返回思考过程和执行结果
3. **多智能体支持**: 支持不同类型的AI智能体 (如 aiAgentId: "3")

## 关键API接口

### Auto Agent 智能对话
- **接口**: `POST /api/v1/agent/auto_agent`
- **端口**: 8099 (dev环境)
- **请求格式**: JSON
- **响应格式**: SSE (text/event-stream)

### 请求参数
`json
{
  "aiAgentId": "3",      // 智能体类型ID
  "message": "用户消息",  // 用户输入
  "sessionId": "session_...",  // 会话ID
  "maxStep": 5           // 最大执行步数
}
`

## 技术依赖

### 核心依赖
- **Spring AI**: 1.0.0 (AI能力集成)
- **MyBatis**: 3.0.4 (数据持久化)
- **MySQL**: 8.0.28 (主数据库)
- **PostgreSQL + pgvector**: 向量数据库 (RAG知识库)
- **FastJSON**: 2.0.28 (JSON处理)
- **Guava**: 32.1.3-jre (工具库)

### 开发工具
- **扳手组件 (xfg-wrench)**: 3.0.0 (通用工具组件)
- **JWT**: 4.4.0 (认证)

## 开发环境配置

### 数据库配置 (dev环境)
- **MySQL**: localhost:13306/ai-agent-station-study
- **PostgreSQL**: localhost:15432/ai-rag-knowledge
- **用户名/密码**: root/123456 (MySQL), postgres/postgres (PostgreSQL)

### AI配置
- **Base URL**: https://token-plan-cn.xiaomimimo.com
- **API Key**: 配置在 application-dev.yml

### 服务端口
- **后端服务**: 8099
- **前端演示**: 8080

## 启动命令

`ash
# 启动后端服务
mvn spring-boot:run

# 启动前端 (在 docs/dev-ops/nginx/html 目录)
python3 -m http.server 8080
`

## DDD 分层说明

| 层级 | 职责 | 主要组件 |
|------|------|----------|
| **api** | 接口定义 | Service接口、DTO |
| **app** | 应用层 | 启动类、配置、Application Service |
| **domain** | 领域层 | 实体、值对象、领域服务、仓储接口 |
| **infrastructure** | 基础设施 | 仓储实现、外部服务适配 |
| **trigger** | 触发器 | HTTP Controller、定时任务、消息监听 |
| **types** | 类型定义 | 枚举、常量、通用DTO |

## 消息类型说明

| type | 说明 |
|------|------|
| analysis | 分析阶段 - AI分析用户需求 |
| execution | 执行阶段 - AI执行具体任务 |
| supervision | 监督阶段 - 质量检查 |
| summary | 总结阶段 - 输出最终结果 |
| error | 错误信息 |
| complete | 任务完成 |

## 开发注意事项

1. **代码规范**: 遵循 DDD 分层架构，各层职责清晰
2. **API设计**: RESTful风格，统一响应格式
3. **数据库**: 主库MySQL + 向量库PostgreSQL (pgvector)
4. **配置管理**: 多环境配置 (dev/test/prod)
5. **事务管理**: 使用 @EnableTransactionManagement
6. **线程池**: 已配置线程池 (core:20, max:50)

## 相关资源

- **DDD 教程**: https://bugstack.cn/md/road-map/ddd.html
- **Docker 文档**: https://bugstack.cn/md/road-map/docker.html

---
*本文档由 Codex 自动生成，用于新会话快速了解项目*
