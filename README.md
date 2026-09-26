# 智枢 · 智能体编排与知识问答平台

## 项目简介

本项目是一个基于 DDD 架构的 AI 智能体学习平台：**私有知识库驱动，把智能体编排、知识问答与流式对话收进同一个工作台**。

- 用户端：Auto Agent 自动智能对话，支持 SSE 流式响应与实时交互体验
- 管理端：智能体编排画布、客户端/模型/提示词/MCP 工具/顾问/RAG 知识库等资源配置
- 两端共用同一个登录入口与登录态，一键切换、无需二次登录

## 技术架构

- **架构模式**: DDD（领域驱动设计）
- **后端技术**: Spring Boot + Java
- **前端技术**: Vue 3 + TypeScript + Ant Design Vue 4 + Vite 5 + Pinia + vue-router + Tailwind CSS + LogicFlow
- **通信方式**: Server-Sent Events (SSE) 流式响应
- **容器化**: Docker

## 前端工程（zhishu-ui · 智枢）

用户端与管理后台已合并为**同一个前端工程**，全局只有一个登录入口：

- **默认落地页**：登录后进入用户端智能对话（`/chat`）
- **一键切换**：顶栏「进入管理后台」直达 `/admin/dashboard`，**不需要二次输入密码**
  （两端共用同一个 JWT：同一 token 同时用于 `/api/v1/admin/**` 与 `/api/v1/agent/**`）
- **登录账号**：`admin` / `123456`（登录页提供快捷登录按钮）
- **接口前缀**：开发环境走 Vite 代理（`/api` → `http://127.0.0.1:8099`），因此**不需要改后端 CORS 白名单**

### 页面清单

| 路由 | 页面 |
|------|------|
| `/login` | 统一登录页（全局唯一） |
| `/chat` | 用户端智能对话（会话列表、智能体/步数选择、过程与结果双栏、SSE 流式、Markdown 渲染） |
| `/admin/dashboard` | 数据总览（实时聚合统计 + 资源分布 + 最近更新的编排配置） |
| `/admin/agent-list` | 智能体列表（配置查询 / 查看 / 修改 / 装配 / 删除） |
| `/admin/agent-config` | 智能体编排（LogicFlow 画布：节点面板 / 连线 / 属性抽屉 / 保存 / 装配） |
| `/admin/client-management` 等 7 个 | 客户端、客户端 API、顾问、模型、系统提示词、MCP 工具、RAG 知识库配置 |

管理端 10 个资源页共用同一套「查询 + 分页 + 新增/编辑弹窗 + 删除确认」范式（`src/components/admin/CrudPage.vue` + 各模块 descriptor）。

### 接口契约要点（改动前务必先读）

1. 后端统一响应包装 `{ code, info, data }`，成功码固定 `"0000"`；`code !== "0000"` 会被请求层统一抛业务错误。
2. 所有 `query-list` 都是**内存分页**且**只返回裸数组**（响应里没有 total），因此列表页不展示「共 N 条」，翻页以「本页是否取满」推断。
3. 登录态失效返回 **HTTP 401**：请求层会清登录态、明确提示「登录已过期」并跳登录页（不会伪装成网络故障）。
4. 画布配置持久化 JSON 的字段名为 `nodes[].{id,type,data.title,data.inputsValues}` 与 `edges[].{sourceNodeID,targetNodeID,sourcePortID?}`（**连线字段是大写 ID**，与 LogicFlow 原生的 `sourceNodeId` 不同，由 `logicflow/adapter.ts` 双向转换）。
5. `ai-client-api/update-by-id` 后端为 **PUT**（历史实现误用过 POST）。

## 相关文档

- docker 使用文档：[https://bugstack.cn/md/road-map/docker.html](https://bugstack.cn/md/road-map/docker.html)
- DDD 教程；
  - [DDD 概念理论](https://bugstack.cn/md/road-map/ddd-guide-01.html)
  - [DDD 建模方法](https://bugstack.cn/md/road-map/ddd-guide-02.html)
  - [DDD 工程模型](https://bugstack.cn/md/road-map/ddd-guide-03.html)
  - [DDD 架构设计](https://bugstack.cn/md/road-map/ddd.html)
  - [DDD 建模案例](https://bugstack.cn/md/road-map/ddd-model.html)

## API接口文档

### Auto Agent 智能对话接口

#### 接口概述

该接口提供AI智能体自动对话功能，支持流式响应，实时返回AI的思考过程和执行结果。

#### 接口信息

- **接口地址**: `POST /api/v1/agent/auto_agent`
- **请求方式**: POST
- **响应格式**: Server-Sent Events (SSE) 流式响应
- **Content-Type**: `application/json`
- **Accept**: `text/event-stream`

#### 请求参数

**请求体 (JSON格式)**

```json
{
  "aiAgentId": "3",
  "message": "1 + 1",
  "sessionId": "session_1642345678901_abc123def",
  "maxStep": 5
}
```

**参数说明**

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| aiAgentId | String | 是 | AI智能体类型ID，目前支持："3"（Auto Agent - 自动智能对话体） |
| message | String | 是 | 用户输入的问题或指令，最大长度1000字符 |
| sessionId | String | 是 | 会话ID，用于标识唯一对话会话，格式：session_时间戳_随机字符串 |
| maxStep | Integer | 是 | 最大执行步数，可选值：1、2、3、5、10、20、50 |

#### 响应格式

**SSE流式响应**

响应采用Server-Sent Events格式，每条消息以`data: `开头，包含JSON格式的数据：

```
data: {"type":"analysis","subType":"analysis_status","step":1,"content":"开始分析用户需求...","completed":false,"timestamp":1642345678901,"sessionId":"session_1642345678901_abc123def"}

data: {"type":"execution","subType":"execution_process","step":2,"content":"正在执行搜索任务...","completed":false,"timestamp":1642345678902,"sessionId":"session_1642345678901_abc123def"}

data: {"type":"summary","subType":"summary_overview","step":5,"content":"## 学习计划\n\n基于检索结果，为您制定以下学习计划...","completed":true,"timestamp":1642345678905,"sessionId":"session_1642345678901_abc123def"}
```

**响应字段说明**

| 字段名 | 类型 | 说明 |
|--------|------|------|
| type | String | 消息类型，详见下方类型说明 |
| subType | String | 消息子类型，详见下方子类型说明 |
| step | Integer | 当前执行步骤 |
| content | String | 消息内容，支持Markdown格式 |
| completed | Boolean | 是否完成 |
| timestamp | Long | 时间戳 |
| sessionId | String | 会话ID |

#### 消息类型说明

**主要类型 (type)**

| 类型 | 名称 | 图标 | 说明 |
|------|------|------|------|
| analysis | 分析阶段 | 🎯 | AI分析用户需求和制定策略 |
| execution | 执行阶段 | ⚡ | AI执行具体任务 |
| supervision | 监督阶段 | 🔍 | AI监督和质量检查 |
| summary | 总结阶段 | 📊 | AI总结结果和输出最终答案 |
| error | 错误信息 | ❌ | 执行过程中的错误信息 |
| complete | 完成 | ✅ | 任务执行完成标识 |

**子类型 (subType)**

| 子类型 | 说明 |
|--------|------|
| analysis_status | 任务状态 |
| analysis_history | 历史评估 |
| analysis_strategy | 执行策略 |
| analysis_progress | 完成度 |
| execution_target | 执行目标 |
| execution_process | 执行过程 |
| execution_result | 执行结果 |
| execution_quality | 质量检查 |
| assessment | 质量评估 |
| issues | 问题识别 |
| suggestions | 改进建议 |
| score | 质量评分 |
| pass | 检查结果 |
| completed_work | 已完成工作 |
| incomplete_reasons | 未完成原因 |
| evaluation | 效果评估 |
| summary_overview | 总结概览 |

#### 前端集成示例

**JavaScript调用示例**

```javascript
// 准备请求数据
const requestData = {
    aiAgentId: "3",
    message: "1 + 1",
    sessionId: "session_" + Date.now() + "_" + Math.random().toString(36).substr(2, 9),
    maxStep: 5
};

// 发送POST请求
fetch('http://localhost:8099/api/v1/agent/auto_agent', {
    method: 'POST',
    headers: {
        'Content-Type': 'application/json',
        'Accept': 'text/event-stream'
    },
    body: JSON.stringify(requestData)
})
.then(response => {
    if (!response.ok) {
        throw new Error('网络请求失败: ' + response.status);
    }
    
    // 处理流式响应
    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    
    function readStream() {
        reader.read().then(({ done, value }) => {
            if (done) {
                console.log('流式响应结束');
                return;
            }
            
            // 解码数据块
            const chunk = decoder.decode(value, { stream: true });
            
            // 处理SSE数据
            const lines = chunk.split('\n');
            for (let line of lines) {
                if (line.startsWith('data: ')) {
                    const data = line.substring(6).trim();
                    if (data && data !== '[DONE]') {
                        try {
                            const jsonData = JSON.parse(data);
                            handleSSEMessage(jsonData);
                        } catch (e) {
                            console.warn('无法解析JSON数据:', data);
                        }
                    }
                }
            }
            
            // 继续读取流
            readStream();
        });
    }
    
    readStream();
})
.catch(error => {
    console.error('请求错误:', error);
});

// 处理SSE消息
function handleSSEMessage(jsonData) {
    const { type, subType, step, content, completed, timestamp, sessionId } = jsonData;
    
    // 根据消息类型进行不同处理
    if (type === 'summary') {
        // 显示最终结果
        displayFinalResult(content);
    } else {
        // 显示思考过程
        displayThinkingProcess(type, subType, content, step);
    }
}
```

#### 错误处理

**常见错误码**

| 错误码 | 说明 | 解决方案 |
|--------|------|----------|
| 400 | 请求参数错误 | 检查请求参数格式和必填字段 |
| 500 | 服务器内部错误 | 查看服务器日志，联系技术支持 |
| 503 | 服务不可用 | 检查服务状态，稍后重试 |

**错误响应示例**

```
data: {"type":"error","subType":null,"step":null,"content":"请求参数错误：message不能为空","completed":false,"timestamp":1642345678901,"sessionId":"session_1642345678901_abc123def"}
```

#### 使用注意事项

1. **会话管理**: 每次新对话建议生成新的sessionId
2. **连接管理**: SSE连接建立后，需要正确处理连接断开和重连
3. **内容渲染**: content字段支持Markdown格式，建议使用Markdown解析器渲染
4. **性能优化**: 对于长时间的对话，建议实现消息缓存和分页显示
5. **错误处理**: 需要处理网络异常、解析异常等各种错误情况

#### 部署配置

**CORS配置**

接口已配置跨域支持：
```java
@CrossOrigin(origins = "*", allowedHeaders = "*", methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.OPTIONS})
```

**服务端口**

- 后端端口：8099
- 前端开发端口：5173（该端口已在后端 CORS 白名单内；开发环境走 Vite 代理，实际不触发跨域）

## 快速开始

1. **启动后端服务**
   ```bash
   mvn spring-boot:run
   ```

2. **启动前端工程**
   ```bash
   cd zhishu-ui
   npm install
   npm run dev
   ```

3. **打开浏览器访问**
   ```
   http://localhost:5173
   ```
   使用 `admin / 123456` 登录（或点登录页的「使用 admin 账号快捷登录」）。
   登录后默认进入智能对话，点顶栏「进入管理后台」即可切换到管理端。

4. **Windows 一键启动**：双击根目录 `start-ui-admin.bat`（会自动打开浏览器并启动前端）。

