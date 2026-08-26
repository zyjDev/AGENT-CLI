# RAG 知识库更新功能实现总结

## 1. 实现概述

### 1.1 功能完成状态

| 阶段 | 状态 | 说明 |
|------|------|------|
| Phase 1 | ✅ 完成 | 基础数据结构 |
| Phase 2 | ✅ 完成 | DAO 层实现 |
| Phase 3 | ✅ 完成 | 版本管理服务 |
| Phase 4 | ✅ 完成 | 核心更新服务 |
| Phase 5 | ✅ 完成 | 异步任务服务 |
| Phase 6 | ✅ 完成 | 回滚服务 |
| Phase 7 | ✅ 完成 | 接口层实现 |
| Phase 8 | ✅ 完成 | 测试和优化 |

### 1.2 核心功能

- ✅ 基于 updateTime 筛选待更新文档
- ✅ 通过元数据字段定位文档
- ✅ 更新文档内容和元数据
- ✅ 版本号自增管理
- ✅ 异步批量更新
- ✅ 任务状态跟踪
- ✅ 版本回滚机制
- ✅ 文件哈希变更检测

---

## 2. 代码结构

### 2.1 新增文件清单

#### 2.1.1 数据库脚本
- `docs/dev-ops/mysql/sql/rag-update-feature.sql`

#### 2.1.2 PO 对象
- `infrastructure/dao/po/AiRagUpdateTask.java`
- `infrastructure/dao/po/AiRagVersionHistory.java`

#### 2.1.3 DTO 对象
- `api/dto/RagUpdateRequestDTO.java`
- `api/dto/TaskStatusResponseDTO.java`
- `api/dto/RollbackRequestDTO.java`

#### 2.1.4 DAO 接口
- `infrastructure/dao/IAiRagUpdateTaskDao.java`
- `infrastructure/dao/IAiRagVersionHistoryDao.java`

#### 2.1.5 Service 接口
- `domain/agent/service/IRagUpdateService.java`
- `domain/agent/service/IRagVersionService.java`
- `domain/agent/service/IAsyncRagUpdateService.java`
- `domain/agent/service/IRollbackService.java`

#### 2.1.6 Service 实现
- `domain/agent/service/rag/RagUpdateServiceImpl.java`
- `domain/agent/service/rag/RagVersionService.java`
- `domain/agent/service/rag/AsyncRagUpdateService.java`
- `domain/agent/service/rag/RollbackService.java`

#### 2.1.7 仓储接口和实现
- `domain/agent/adapter/repository/IRagUpdateRepository.java`
- `infrastructure/adapter/repository/RagUpdateRepository.java`

#### 2.1.8 配置类
- `config/RagUpdateThreadPoolConfig.java`

#### 2.1.9 Controller
- `trigger/http/admin/AiRagUpdateController.java`
- `trigger/http/GlobalExceptionHandler.java`

#### 2.1.10 测试类
- `test/domain/RagUpdateServiceTest.java`
- `test/domain/RagUpdateIntegrationTest.java`

### 2.2 修改文件清单

- `pom.xml` - 添加 MyBatis-Plus 依赖
- `ai-agent-station-study-infrastructure/pom.xml` - 添加 MyBatis-Plus 依赖
- `ai-agent-station-study-domain/pom.xml` - 添加 API 模块依赖
- `infrastructure/dao/IAiClientRagOrderDao.java` - 继承 BaseMapper
- `infrastructure/dao/po/AiClientRagOrder.java` - 新增字段
- `infrastructure/dao/po/AiClientToolMcp.java` - 新增 env 字段
- `infrastructure/dao/po/AiClient.java` - 新增 clientDesc 字段
- `infrastructure/dao/po/AiClientModel.java` - 新增 typeName 字段
- `api/dto/AiClientRagOrderRequestDTO.java` - 新增字段
- `api/dto/AiClientRagOrderResponseDTO.java` - 新增字段
- `domain/agent/adapter/repository/IAgentRepository.java` - 新增方法
- `domain/agent/model/valobj/AiRagOrderVO.java` - 新增字段
- `domain/agent/model/valobj/AiClientToolMcpVO.java` - 新增字段
- `domain/agent/model/valobj/AiClientVO.java` - 新增字段
- `domain/agent/model/valobj/AiClientModelVO.java` - 新增字段
- `domain/agent/model/valobj/AiClientSystemPromptVO.java` - 新增 status 字段
- `infrastructure/adapter/repository/AgentRepository.java` - 实现新方法
- `domain/agent/service/rag/RagService.java` - 支持元数据

---

## 3. API 接口文档

### 3.1 接口列表

| 接口 | 方法 | 说明 |
|------|------|------|
| `GET /api/v1/rag/updated` | queryUpdatedRagOrders | 查询待更新文档 |
| `GET /api/v1/rag/list` | queryAllRagOrders | 查询所有知识库配置 |
| `GET /api/v1/rag/{ragId}` | queryRagOrderById | 根据ID查询配置 |
| `POST /api/v1/rag/update` | updateRagDocuments | 更新知识库文档 |
| `POST /api/v1/rag/async-batch-update` | asyncBatchUpdateRag | 异步批量更新 |
| `GET /api/v1/rag/task-status` | queryUpdateTaskStatus | 查询任务状态 |
| `POST /api/v1/rag/cancel-task` | cancelTask | 取消任务 |
| `POST /api/v1/rag/retry-task` | retryFailedTask | 重试失败任务 |
| `POST /api/v1/rag/rollback` | rollbackRagVersion | 回滚版本 |
| `GET /api/v1/rag/version-history/{ragId}` | getVersionHistory | 获取版本历史 |
| `GET /api/v1/rag/latest-version/{ragId}` | getLatestVersion | 获取最新版本号 |

### 3.2 请求示例

#### 查询待更新文档
```http
GET /api/v1/rag/updated?updateTime=2025-01-01T00:00:00
```

#### 更新知识库文档
```http
POST /api/v1/rag/update
Content-Type: multipart/form-data

ragId: 9001
updateReason: 内容优化
files: [文件1, 文件2]
```

#### 异步批量更新
```http
POST /api/v1/rag/async-batch-update
Content-Type: application/json

{
  "ragIds": ["9001", "9002"],
  "updateReason": "批量更新"
}
```

#### 查询任务状态
```http
GET /api/v1/rag/task-status?taskId=task_123456
```

#### 回滚版本
```http
POST /api/v1/rag/rollback
Content-Type: application/json

{
  "ragId": "9001",
  "targetVersion": 1
}
```

---

## 4. 技术实现

### 4.1 架构设计

采用 DDD 分层架构，遵循依赖倒置原则：

```
api (接口定义)
    ↓
domain (领域层 - 定义接口)
    ↓
infrastructure (基础设施层 - 实现接口)
    ↓
trigger (触发器层 - HTTP接口)
```

**关键设计**：
- domain 层定义 `IRagUpdateRepository` 接口
- infrastructure 层实现 `RagUpdateRepository`
- 通过依赖注入解耦，避免循环依赖

### 4.2 数据库变更

#### 4.2.1 新增字段
```sql
ALTER TABLE ai_client_rag_order 
ADD COLUMN version INT DEFAULT 1 COMMENT '版本号',
ADD COLUMN file_hash VARCHAR(64) COMMENT '文件内容哈希',
ADD COLUMN update_reason VARCHAR(255) COMMENT '更新原因';
```

#### 4.2.2 新增表
- `ai_rag_update_task` - 更新任务表
- `ai_rag_version_history` - 版本历史表

### 4.3 向量数据库元数据

```java
Map<String, Object> metadata = new HashMap<>();
metadata.put("knowledge", knowledgeTag);
metadata.put("ragId", ragId);
metadata.put("version", version);
metadata.put("lastUpdateTime", LocalDateTime.now().toString());
metadata.put("fileHash", fileHash);
metadata.put("updateReason", updateReason);
```

### 4.4 异步任务处理

- 使用 `@Async` 注解实现异步执行
- 线程池配置: 核心线程数 5, 最大线程数 10
- 任务状态跟踪: PENDING -> PROCESSING -> COMPLETED/FAILED/CANCELLED
- 支持任务取消和重试

### 4.5 版本回滚机制

- 保存版本历史快照
- 验证回滚可行性
- 原子性回滚操作
- 回滚日志记录

---

## 5. 测试用例

### 5.1 单元测试

- `RagUpdateServiceTest.java` - 知识库更新服务测试
  - 测试查询待更新文档
  - 测试查询所有知识库配置
  - 测试根据ID查询配置
  - 测试查询任务状态
  - 测试获取版本历史
  - 测试获取最新版本号
  - 测试回滚验证

### 5.2 集成测试

- `RagUpdateIntegrationTest.java` - 知识库更新集成测试
  - 测试完整的更新流程
  - 测试异步批量更新
  - 测试任务取消
  - 测试版本回滚
  - 测试并发更新

---

## 6. 启动和使用

### 6.1 执行数据库脚本

```bash
# 执行数据库脚本
mysql -u root -p < docs/dev-ops/mysql/sql/rag-update-feature.sql
```

### 6.2 启动服务

```bash
# 启动后端服务
mvn spring-boot:run

# 启动前端
cd docs/dev-ops/nginx/html
python3 -m http.server 8080
```

### 6.3 访问接口

- 后端服务: http://localhost:8099
- 前端演示: http://localhost:8080/index.html

### 6.4 使用流程

1. **上传知识库文件**
   ```bash
   POST /api/v1/rag/upload
   ```

2. **查询待更新文档**
   ```bash
   GET /api/v1/rag/updated?updateTime=2025-01-01T00:00:00
   ```

3. **更新知识库文档**
   ```bash
   POST /api/v1/rag/update
   ```

4. **异步批量更新**
   ```bash
   POST /api/v1/rag/async-batch-update
   ```

5. **查询任务状态**
   ```bash
   GET /api/v1/rag/task-status?taskId=xxx
   ```

6. **回滚版本**
   ```bash
   POST /api/v1/rag/rollback
   ```

---

## 7. 注意事项

### 7.1 数据一致性
- 向量库和数据库操作在同一事务中
- 使用 Spring 事务管理

### 7.2 性能考虑
- 大文件分割异步处理
- 批量更新使用任务队列
- 线程池配置合理

### 7.3 并发控制
- 同一知识库的并发更新需要加锁
- 使用数据库乐观锁

### 7.4 回滚机制
- 保存版本历史快照
- 支持手动回滚到指定版本
- 回滚操作有日志记录

---

## 8. 后续优化

### 8.1 功能增强
- 支持更多文件格式
- 增量更新优化
- 版本对比功能
- 批量回滚支持

### 8.2 性能优化
- 向量库批量操作
- 缓存优化
- 异步处理优化

### 8.3 监控告警
- 任务执行监控
- 异常告警
- 性能指标

---

*文档版本: v1.1*
*更新时间: 2025-01-24*
*创建人: Codex*
*实现状态: 已完成*
