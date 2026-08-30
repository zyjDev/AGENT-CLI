# RAG 可复用智能体设计（交接文档）

## 1. 背景

当前知识入库后，RAG 检索并不会对所有知识自动生效。  
检索命中由智能体绑定的 `RagAnswer` 顾问中的 `filterExpression` 决定。  
因此要实现“入库即可检索”，需要建立一套**可复用、可配置、不硬编码**的智能体模板。

> **当前阶段已完成关键改造**：支持通过请求参数 `knowledgeTag` 动态路由到指定知识域，新文档入库后同一智能体可直接检索到。
>
> **2026-08-28 落地补充（重要）**：如果你使用 `autoAgentExecuteStrategy`，除 RagAnswer 外，还必须在 `ai_agent_flow_config` 中配置完整四步 `client_type`，否则会在第一步直接 NPE。  
> 必须包含：`TASK_ANALYZER_CLIENT / PRECISION_EXECUTOR_CLIENT / QUALITY_SUPERVISOR_CLIENT / RESPONSE_ASSISTANT`。  
> 并且至少有一个 `client_id` 绑定 RagAnswer 顾问，RAG 才会真正生效。

> **2026-08-28 Bug 修复记录**：本次排查共修复 4 个问题，详见第 13 节。

> **2026-08-30 独立 RAG 智能体落地**：新增独立 `Agent（RAG）`（agentId=7）模板、专属客户端/顾问与迁移脚本；知识标签由前端下拉框从数据库加载；执行链路四步统一注入 `knowledgeTag`。详见第 14 节。

## 2. 当前现状

### 2.0 已完成功能（2026-08-30 更新）
- `AutoAgentRequestDTO` 新增可选字段 `knowledgeTag`
- `ExecuteCommandEntity` 新增 `knowledgeTag`
- `AiAgentController` 在调度时透传 `knowledgeTag`
- 自动执行四步（`Step1-Step4`）与 `FixedAgentExecuteStrategy` 统一注入 `knowledgeTag`
- `RagAnswerAdvisor` 支持动态解析 `knowledgeTag` 与 `${knowledgeTag}` 占位符
- 新增独立 `Agent（RAG）` 模板：agentId=7 + 专属客户端 7101-7104 + 专属顾问 4005（见第 14 节）
- 前端演示聊天页知识标签改为下拉框，从 `ai_client_rag_order` 已有标签加载

这意味着：
1. 同一个智能体可支持不同知识标签
2. 新文档入库后，只要查询请求带相同 `knowledgeTag`，即可被召回
3. 不需要为每个标签硬编码新顾问或新智能体

### 2.1 已有能力
- 入库时写入元数据：`metadata.put("knowledge", tag)`
- 顾问类型支持 `RagAnswer`
- 顾问配置支持：
  - `topK`
  - `filterExpression`

### 2.2 现有问题
- `filterExpression` 容易被配成静态字符串（例如 `knowledge == 'article'` 或 `knowledge == '测试'`），只能命中固定标签
- 不同知识标签如果都新建独立顾问，会导致大量重复配置
- 新建 `autoAgentExecuteStrategy` 智能体时，如果只配一个默认客户端，会因缺少四步流程而直接 NPE
- 独立 RAG 智能体已建立专属客户端与顾问，不再复用其他智能体配置（见第 14 节）

## 3. 目标

建立一个标准 `Agent（RAG）` 模板，满足：
1. **入库即支持检索**
2. **支持多标签扩展**
3. **不硬编码 ID / 标签**
4. **后续切片/更新可复用同一套流程**

## 4. 推荐方案

### 4.1 方案A（推荐）：动态 filterExpression + 配置模板

在 `RagAnswer` 顾问中支持动态表达式：

```java
knowledge == '{knowledgeTag}'
```

运行时从以下位置读取 `{knowledgeTag}`：

1. 请求参数（优先）
2. 会话上下文
3. 默认知识域标签（兜底）

#### 优点
- 不新增硬编码
- 可支持任意新标签
- 可复用同一个 Agent 模板

### 4.2 方案B：标签索引表

新增关系表：

- `ai_rag_tag_bindng(agent_id, knowledge_tag)`

当新标签入库后，自动绑定到指定 Agent。  
查询时在 `filterExpression` 中使用 `in (...)` 或动态路由。

#### 优点
- 多标签管理更清晰
- 适合标签数量多、需要治理的场景

## 5. 数据流设计

### 5.1 入库流
1. 用户上传知识
2. 系统切片
3. 写入向量库，并附加 metadata：
   - `knowledge`
   - `fileHash`
   - `version`
   - `updateReason`

### 5.2 检索流
1. 用户输入问题
2. Agent 解析目标知识域（规则/提示词/请求参数）
3. RagAnswerAdvisor 构建 SearchRequest：
   - `topK`
   - `filterExpression = knowledge == '当前标签'`
4. 返回召回结果并生成回答

## 6. 建议改造点（不硬编码）

### 6.4 当前落地方案（RAG 智能体模板）
如果你创建的是 `Agent（RAG）`，建议直接按以下方式配置：

1. `ai_agent.strategy = autoAgentExecuteStrategy`
2. `ai_agent_flow_config` 必须包含四步：
   - `TASK_ANALYZER_CLIENT`
   - `PRECISION_EXECUTOR_CLIENT`
   - `QUALITY_SUPERVISOR_CLIENT`
   - `RESPONSE_ASSISTANT`
3. 至少给其中一个 `client_id` 绑定 `RagAnswer` 顾问
4. RagAnswer 顾问的 `ext_param.filterExpression` 统一使用动态写法：
   - `knowledge == '${knowledgeTag}'`

这样就不会再出现“新增一个标签就要新增一个顾问/智能体”的硬编码问题。

### 6.1 RagAnswerAdvisor 增强
支持占位符解析：

- `${knowledgeTag}`
- `${request.knowledgeTag}`
- `${session.knowledgeTag}`

示例 filter 配置：

```json
{
  "topK": 4,
  "filterExpression": "knowledge == '${knowledgeTag}'"
}
```

### 6.2 AutoAgent 请求扩展
在 `AutoAgentRequestDTO` 增加可选字段：

```java
private String knowledgeTag;
```

用于指定当前查询知识域（可为空）。

当前实现中：
- `AiAgentController` 会把 `knowledgeTag` 传递到执行实体
- 执行策略在调用模型时注入 `qa_filter_expression`
- `RagAnswerAdvisor` 支持动态解析 `knowledgeTag` / `${knowledgeTag}`

### 6.3 管理端增强
- 创建“RAG 通用模板 Agent”
- 允许配置默认知识标签
- 允许启用“动态标签解析”

## 7. 推荐落地步骤

### 第一阶段（最小闭环）
1. 创建标准 `Agent（RAG）` 模板
2. 绑定一个通用 RagAnswer 顾问
3. filter 使用 `knowledge == '${knowledgeTag}'`
4. 通过请求传入 `knowledgeTag=测试`

### 第二阶段（自动化）
1. 新增标签绑定表
2. 在入库后自动建立 `Agent-标签` 关系
3. 检索时自动选择标签（无需手动传）

### 第三阶段（平台化）
1. 管理后台支持“标签路由策略”
2. 支持多标签并发召回
3. 支持标签优先级与权重

## 8. 验证方式

### 8.1 入库验证
- 上传 `测试.txt`，内容包含 `123456`
- 确认 `metadata.knowledge=测试`

### 8.2 检索验证
- 调用 Agent：

```json
{
  "message": "测试",
  "knowledgeTag": "测试"
}
```

- 预期返回包含 `123456`

### 8.3 学校样例验证（新增）
1. 入库文档内容：`学校是123`
2. 入库标签：`学校`
3. 请求体：

```json
{
  "message": "学校是什么",
  "knowledgeTag": "学校"
}
```

4. 预期结果：可召回并回答 `123`

## 9. 当前限制

- 当前仍需显式选择 `knowledgeTag`（前端下拉框从数据库已有标签加载，API 调用方可直接传参）
- 暂未实现“用户问题自动识别知识标签”的能力
- 若不传 `knowledgeTag`，则按当前顾问默认表达式或全库检索逻辑执行；RAG 通用顾问 4005 默认为全库检索

## 10. 后续建议

- 建立“知识标签注册表”（待做）
- 建立“Agent 模板管理页”（RAG 模板已通过 SQL 落地，管理页待完善）
- 建立“入库 -> 自动绑定”流程（待做）
- 增加“问题->标签路由器”（规则/模型混合）（待做）

## 11. 当前改造涉及文件

- `ai-agent-station-study-api/src/main/java/cn/bugstack/ai/api/dto/AutoAgentRequestDTO.java`
- `ai-agent-station-study-domain/src/main/java/cn/bugstack/ai/domain/agent/model/entity/ExecuteCommandEntity.java`
- `ai-agent-station-study-trigger/src/main/java/cn/bugstack/ai/trigger/http/AiAgentController.java`
- `ai-agent-station-study-domain/.../execute/fixed/FixedAgentExecuteStrategy.java`
- `ai-agent-station-study-domain/.../execute/auto/step/Step1AnalyzerNode.java`
- `ai-agent-station-study-domain/.../execute/auto/step/Step2PrecisionExecutorNode.java`
- `ai-agent-station-study-domain/.../execute/auto/step/Step3QualitySupervisorNode.java`
- `ai-agent-station-study-domain/.../execute/auto/step/Step4LogExecutionSummaryNode.java`
- `ai-agent-station-study-domain/.../armory/node/factory/element/RagAnswerAdvisor.java`
- `ai-agent-station-study-app/.../config/AiAgentConfig.java`
- `ai-agent-station-study-app/src/main/resources/application-dev.yml`
- `ai-agent-station-study-app/src/main/resources/mybatis/mapper/ai_client_rag_order_mapper.xml`
- `docs/dev-ops/mysql/sql/rag-agent-template.sql`
- `docs/dev-ops/nginx/html/index.html`

## 12. 结论

入库只是第一步，要稳定支持 RAG 检索，需要把"标签绑定 + 动态过滤 + Agent 模板"做成标准能力。  
推荐采用**动态 filterExpression**方案，能以最小改动实现长期可复用。

## 13. 2026-08-28 Bug 修复记录

本次排查共修复 4 个问题，按出现顺序记录如下：

### 13.1 Embedding 模型构造参数错误

**现象**：启动时报编译错误 `OpenAiEmbeddingOptions 无法转换为 MetadataMode`

**根因**：`AiAgentConfig` 手动构建 `OpenAiEmbeddingModel` 时，将 `OpenAiEmbeddingOptions` 作为第二个参数传入，但 `spring-ai-openai 1.0.0` 的构造函数签名第二个参数是 `MetadataMode`。

**修复**：使用三参数构造函数 `new OpenAiEmbeddingModel(embeddingApi, MetadataMode.EMBED, options)`，并从 `application-dev.yml` 读取 `embedding.options.model` 和 `embedding.options.dimensions`。

**涉及文件**：
- `ai-agent-station-study-app/src/main/java/cn/bugstack/ai/config/AiAgentConfig.java`

### 13.2 自动配置排除类名错误

**现象**：配置文件排除了 `OpenAiEmbeddingAutoConfiguration`，但实际未生效，Embedding 仍使用默认模型 `text-embedding-ada-002`。

**根因**：`application-dev.yml` 中排除的类名是 `org.springframework.ai.openai.autoconfig.OpenAiEmbeddingAutoConfiguration`，而 `spring-ai-openai 1.0.0` 中实际类名是 `org.springframework.ai.model.openai.autoconfigure.OpenAiEmbeddingAutoConfiguration`（包路径不同）。

**修复**：更正排除类名为正确路径。

**涉及文件**：
- `ai-agent-station-study-app/src/main/resources/application-dev.yml`

### 13.3 RAG 订单表缺少 insert 映射

**现象**：上传知识库文件时报 `Invalid bound statement (not found): IAiClientRagOrderDao.insert`

**根因**：`ai_client_rag_order_mapper.xml` 中只定义了 select/update/delete 语句，缺少 `insert` 语句。

**修复**：在 mapper 中补充 `id="insert"` 的 SQL 映射，写入 `rag_id, rag_name, knowledge_tag, status, version, file_hash, update_reason, create_time, update_time` 字段。

**涉及文件**：
- `ai-agent-station-study-app/src/main/resources/mybatis/mapper/ai_client_rag_order_mapper.xml`

### 13.4 RAG 过滤表达式解析异常（核心 Bug）

**现象**：autoAgent 执行时报 `FilterExpressionParseException: no viable alternative at input 'Expression['`

**根因**：`RagAnswerAdvisor.resolveFilterExpression` 的兜底逻辑中：
```java
// 问题代码
this.searchRequest.getFilterExpression().toString()
```
`getFilterExpression()` 返回 `Filter.Expression` 对象（Java Record），`.toString()` 产出的是 Record 格式 `Expression[EQ, Key('knowledge'), Value('测试')]`，再交给 `FilterExpressionTextParser.parse()` 解析就会报语法错误。

**触发条件**：请求未传 `knowledgeTag`（或为空）时，`Step1AnalyzerNode` 不会注入 `qa_filter_expression`，`resolveFilterExpression` 走到兜底分支触发此问题。

**修复**：重构 `resolveFilterExpression` 方法，三个分支各自独立处理：
1. 有 `qa_filter_expression` → 解析字符串表达式
2. 有 `knowledgeTag` → 动态拼接 `knowledge == 'xxx'` 并解析
3. 兜底 → **直接返回 `this.searchRequest.getFilterExpression()` 对象**，不再 toString 再重新 parse

**涉及文件**：
- `ai-agent-station-study-domain/src/main/java/cn/bugstack/ai/domain/agent/service/armory/node/factory/element/RagAnswerAdvisor.java`

### 13.5 排查过程中的辅助 SQL

以下 SQL 用于定位"绑定了 RagAnswer 顾问的智能体"，便于快速排查配置问题：

```sql
-- 查看所有绑定了 RagAnswer 的 agent 及其顾问配置
SELECT a.agent_id, a.agent_name, f.client_id, adv.advisor_id, adv.advisor_name, adv.ext_param
FROM ai_agent a
JOIN ai_agent_flow_config f ON f.agent_id = a.agent_id
JOIN ai_client_config c
  ON c.source_type = 'client' AND c.source_id = f.client_id AND c.target_type = 'advisor' AND c.status = 1
JOIN ai_client_advisor adv
  ON adv.advisor_id = c.target_id AND adv.status = 1 AND adv.advisor_type = 'RagAnswer'
WHERE a.status = 1
ORDER BY a.agent_id, f.client_id;
```

## 14. 2026-08-30 独立 RAG 智能体落地

### 14.1 背景

此前 RAG 智能体直接复用其他智能体的 `client_id`（如 3101-3104），而 ChatClient Bean 是按 `client_id` 全局注册的，导致修改模型/提示词/顾问会互相影响。同时 RagAnswer 顾问常被配置成静态表达式（例如 `knowledge == '测试'`），新知识入库后也无法被检索。

### 14.2 落地内容

新增独立 `Agent（RAG）`，与其他 Auto 智能体保持同一结构，但拥有专属配置：

| 配置项 | 值 |
|--------|-----|
| `ai_agent.agent_id` | `7` |
| `ai_agent.strategy` | `autoAgentExecuteStrategy` |
| `ai_agent_flow_config` | `7101-7104` 四步流程（TASK_ANALYZER/PRECISION_EXECUTOR/QUALITY_SUPERVISOR/RESPONSE_ASSISTANT） |
| `ai_client` | 专属客户端 `7101-7104` |
| `ai_client_system_prompt` | 专属提示词 `9111-9114` |
| `ai_client_advisor` | 专属 `RagAnswer` 顾问 `4005`，`ext_param = {"topK": 4}`，**无静态 filterExpression** |
| 模型复用 | `3001`（gpt-5-mini），不影响客户端隔离 |
| 记忆顾问 | 复用 `4001`（ChatMemory），按会话隔离，可安全复用 |

SQL 迁移脚本：`docs/dev-ops/mysql/sql/rag-agent-template.sql`，可重复执行。

### 14.3 动态检索规则

1. 请求带 `knowledgeTag` → `RagAnswerAdvisor` 动态生成 `knowledge == '<tag>'`，任意标签均可检索
2. 请求不带 `knowledgeTag` → 顾问默认无过滤，按全库检索，新入库知识也能被召回
3. 执行链路 `Step1-Step4` 与 `FixedAgentExecuteStrategy` 统一向顾问上下文注入 `knowledgeTag`

> **注意**：不要把 `${knowledgeTag}` 直接写入顾问的 `ext_param.filterExpression`。顾问构建时会把 `filterExpression` 当作静态表达式解析，含占位符会导致装配失败；动态标签统一由请求上下文中的 `knowledgeTag` 驱动。

### 14.4 使用方式

1. 执行 `docs/dev-ops/mysql/sql/rag-agent-template.sql`
2. 重启服务或调用 `/api/v1/agent/armory_agent` 装配 `agentId=7`
3. 上传知识库时填写知识标签（如 `学校`）
4. 对话请求：

```json
{
  "aiAgentId": "7",
  "message": "学校是什么",
  "knowledgeTag": "学校",
  "sessionId": "session_xxx",
  "maxStep": 5
}
```

不传 `knowledgeTag` 时，RAG 智能体将在整个向量库中检索。

演示聊天页的知识标签为下拉框，选项从 `ai_client_rag_order.knowledge_tag` 已有标签加载，无需手工输入；选择“全库检索（不传标签）”时不传 `knowledgeTag`。
