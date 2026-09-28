-- ==============================================================================
-- 01-schema.sql —— MySQL 基线建库脚本（库名：ai-agent-station-study）
-- ==============================================================================
-- 来源：2026-09-28 直接从运行中的 MySQL 8.0.32 实例导出（docker exec mysql mysqldump
--       --no-data --skip-comments --skip-add-drop-table --databases ai-agent-station-study），
--       **仅表结构、不含任何数据**。
-- 后处理仅两处：① 删除 /*!xxxxx SET ...*/ 条件指令行；② CREATE TABLE 补 IF NOT EXISTS。
--       未手工改动任何列定义 —— 本文件即现网真实结构的快照。
-- 用途：挂载到 /docker-entrypoint-initdb.d，供全新环境首次初始化时一次性建库建表。
-- 幂等：全部为 IF NOT EXISTS，可重复执行；**不含任何 DROP / DELETE / TRUNCATE**。
-- 约定：主键为雪花 ID（bigint，无 AUTO_INCREMENT）；表间为逻辑外键，无物理约束。
--
-- 表结构变更后重新导出（保持本文件与现网一致）：
--   docker exec mysql mysqldump -uroot -p<PASSWORD> --no-data --skip-comments \
--     --skip-add-drop-table --databases ai-agent-station-study > docs/dev-ops/mysql/sql/01-schema.sql
--   （导出后仍需删掉 /*!xxxxx ...*/ 行并为 CREATE TABLE 补 IF NOT EXISTS）
-- ==============================================================================
CREATE DATABASE /*!32312 IF NOT EXISTS*/ `ai-agent-station-study` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;

USE `ai-agent-station-study`;
CREATE TABLE IF NOT EXISTS `admin_user` (
  `id` bigint NOT NULL COMMENT '主键ID（雪花）',
  `user_id` varchar(64) NOT NULL COMMENT '用户ID（唯一标识）',
  `username` varchar(50) NOT NULL COMMENT '用户名（登录账号）',
  `password` varchar(128) NOT NULL COMMENT '密码（加密存储）',
  `status` tinyint(1) DEFAULT '1' COMMENT '状态(0:禁用,1:启用,2:锁定)',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `user_role` varchar(32) NOT NULL DEFAULT 'user' COMMENT '角色：admin=管理员（可改公共资源、可管理账号），user=普通用户（只能改自己的资源）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_id` (`user_id`),
  KEY `idx_status` (`status`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='管理员用户表';
CREATE TABLE IF NOT EXISTS `ai_agent` (
  `id` bigint NOT NULL COMMENT '主键ID（雪花）',
  `agent_id` varchar(64) NOT NULL COMMENT '智能体ID',
  `agent_name` varchar(50) NOT NULL COMMENT '智能体名称',
  `description` varchar(255) DEFAULT NULL COMMENT '描述',
  `channel` varchar(32) DEFAULT NULL COMMENT '渠道类型(agent，chat_stream)',
  `strategy` varchar(64) DEFAULT NULL COMMENT '执行策略(auto、flow)',
  `status` tinyint(1) DEFAULT '1' COMMENT '状态(0:禁用,1:启用)',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `owner_id` varchar(64) DEFAULT NULL COMMENT '归属用户ID；NULL=公共资源，人人可用',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agent_id` (`agent_id`),
  KEY `idx_ai_agent_owner` (`owner_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI智能体配置表';
CREATE TABLE IF NOT EXISTS `ai_agent_draw_config` (
  `id` bigint NOT NULL COMMENT '主键ID（雪花）',
  `config_id` varchar(64) NOT NULL COMMENT '配置ID（唯一标识）',
  `config_name` varchar(100) NOT NULL COMMENT '配置名称',
  `description` varchar(500) DEFAULT NULL COMMENT '配置描述',
  `agent_id` varchar(64) DEFAULT NULL COMMENT '关联的智能体ID（来自ai_agent表）',
  `config_data` longtext NOT NULL COMMENT '完整的拖拉拽配置JSON数据（包含nodes和edges）',
  `version` int DEFAULT '1' COMMENT '配置版本号',
  `status` tinyint(1) DEFAULT '1' COMMENT '状态(0:禁用,1:启用)',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `owner_id` varchar(64) DEFAULT NULL COMMENT '归属用户ID；NULL=公共资源，人人可用',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_config_id` (`config_id`),
  KEY `idx_agent_id` (`agent_id`),
  KEY `idx_config_name` (`config_name`),
  KEY `idx_status` (`status`),
  KEY `idx_ai_agent_draw_config_owner` (`owner_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI智能体拖拉拽配置主表';
CREATE TABLE IF NOT EXISTS `ai_agent_flow_config` (
  `id` bigint NOT NULL COMMENT '主键ID（雪花）',
  `agent_id` varchar(64) NOT NULL COMMENT '智能体ID',
  `client_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '客户端ID',
  `client_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '客户端名称',
  `client_type` varchar(64) DEFAULT NULL COMMENT '客户端类型',
  `sequence` int NOT NULL COMMENT '序列号(执行顺序)',
  `step_prompt` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT '步骤提示词',
  `status` int DEFAULT '1' COMMENT '状态；0无效，1有效',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `owner_id` varchar(64) DEFAULT NULL COMMENT '归属用户ID；NULL=公共资源，人人可用',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agent_client_seq` (`agent_id`,`client_id`,`sequence`),
  KEY `idx_ai_agent_flow_config_owner` (`owner_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='智能体-客户端关联表';
CREATE TABLE IF NOT EXISTS `ai_agent_task_schedule` (
  `id` bigint NOT NULL COMMENT '主键ID（雪花）',
  `agent_id` bigint NOT NULL COMMENT '智能体ID',
  `task_name` varchar(64) DEFAULT NULL COMMENT '任务名称',
  `description` varchar(255) DEFAULT NULL COMMENT '任务描述',
  `cron_expression` varchar(50) NOT NULL COMMENT '时间表达式(如: 0/3 * * * * *)',
  `task_param` text COMMENT '任务入参配置(JSON格式)',
  `status` tinyint(1) DEFAULT '1' COMMENT '状态(0:无效,1:有效)',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_agent_id` (`agent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='智能体任务调度配置表';
CREATE TABLE IF NOT EXISTS `ai_client` (
  `id` bigint NOT NULL COMMENT '主键ID（雪花）',
  `client_id` varchar(64) NOT NULL COMMENT '客户端ID',
  `client_name` varchar(50) NOT NULL COMMENT '客户端名称',
  `description` varchar(1024) DEFAULT NULL COMMENT '描述',
  `status` tinyint(1) DEFAULT '1' COMMENT '状态(0:禁用,1:启用)',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `owner_id` varchar(64) DEFAULT NULL COMMENT '归属用户ID；NULL=公共资源，人人可用',
  PRIMARY KEY (`id`),
  UNIQUE KEY `client_id` (`client_id`),
  KEY `idx_ai_client_owner` (`owner_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI客户端配置表';
CREATE TABLE IF NOT EXISTS `ai_client_advisor` (
  `id` bigint NOT NULL COMMENT '主键ID（雪花）',
  `advisor_id` varchar(64) NOT NULL COMMENT '顾问ID',
  `advisor_name` varchar(50) NOT NULL COMMENT '顾问名称',
  `advisor_type` varchar(50) NOT NULL COMMENT '顾问类型(PromptChatMemory/RagAnswer/SimpleLoggerAdvisor等)',
  `order_num` int DEFAULT '0' COMMENT '顺序号',
  `ext_param` varchar(2048) DEFAULT NULL COMMENT '扩展参数配置，json 记录',
  `status` tinyint(1) DEFAULT '1' COMMENT '状态(0:禁用,1:启用)',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `owner_id` varchar(64) DEFAULT NULL COMMENT '归属用户ID；NULL=公共资源，人人可用',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_advisor_id` (`advisor_id`),
  KEY `idx_ai_client_advisor_owner` (`owner_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='顾问配置表';
CREATE TABLE IF NOT EXISTS `ai_client_api` (
  `id` bigint NOT NULL COMMENT '主键ID（雪花）',
  `api_id` varchar(64) NOT NULL COMMENT '全局唯一配置ID',
  `base_url` varchar(255) NOT NULL COMMENT 'API基础URL',
  `api_key` varchar(255) NOT NULL COMMENT 'API密钥',
  `completions_path` varchar(255) NOT NULL COMMENT '补全API路径',
  `embeddings_path` varchar(255) NOT NULL COMMENT '嵌入API路径',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：0-禁用，1-启用',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `owner_id` varchar(64) DEFAULT NULL COMMENT '归属用户ID；NULL=公共资源，人人可用',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_api_id` (`api_id`),
  KEY `idx_status` (`status`),
  KEY `idx_ai_client_api_owner` (`owner_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='OpenAI API配置表';
CREATE TABLE IF NOT EXISTS `ai_client_config` (
  `id` bigint NOT NULL COMMENT '主键ID（雪花）',
  `source_type` varchar(32) NOT NULL COMMENT '源类型（model、client）',
  `source_id` varchar(64) NOT NULL COMMENT '源ID（如 chatModelId、chatClientId 等）',
  `target_type` varchar(32) NOT NULL COMMENT '目标类型（model、client）',
  `target_id` varchar(64) NOT NULL COMMENT '目标ID（如 openAiApiId、chatModelId、systemPromptId、advisorId 等）',
  `ext_param` varchar(1024) DEFAULT NULL COMMENT '扩展参数（JSON格式）',
  `status` tinyint(1) DEFAULT '1' COMMENT '状态(0:禁用,1:启用)',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `owner_id` varchar(64) DEFAULT NULL COMMENT '归属用户ID；NULL=公共资源，人人可用',
  PRIMARY KEY (`id`),
  KEY `idx_source_id` (`source_id`),
  KEY `idx_target_id` (`target_id`),
  KEY `idx_ai_client_config_owner` (`owner_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI客户端统一关联配置表';
CREATE TABLE IF NOT EXISTS `ai_client_model` (
  `id` bigint NOT NULL COMMENT '主键ID（雪花）',
  `model_id` varchar(64) NOT NULL COMMENT '全局唯一模型ID',
  `api_id` varchar(64) NOT NULL COMMENT '关联的API配置ID',
  `model_usage` varchar(128) NOT NULL DEFAULT '缺省的' COMMENT '模型用途',
  `model_name` varchar(64) NOT NULL COMMENT '模型名称',
  `model_type` varchar(32) NOT NULL COMMENT '模型类型：openai、deepseek、claude',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：0-禁用，1-启用',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `owner_id` varchar(64) DEFAULT NULL COMMENT '归属用户ID；NULL=公共资源，人人可用',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_model_id` (`model_id`),
  KEY `idx_api_config_id` (`api_id`),
  KEY `idx_status` (`status`),
  KEY `idx_ai_client_model_owner` (`owner_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='聊天模型配置表';
CREATE TABLE IF NOT EXISTS `ai_client_rag_order` (
  `id` bigint NOT NULL COMMENT '主键ID（雪花）',
  `rag_id` varchar(50) NOT NULL COMMENT '知识库ID',
  `rag_name` varchar(50) NOT NULL COMMENT '知识库名称',
  `knowledge_tag` varchar(50) NOT NULL COMMENT '知识标签',
  `status` tinyint(1) DEFAULT '1' COMMENT '状态(0:禁用,1:启用)',
  `version` int DEFAULT '1' COMMENT '版本号',
  `file_hash` varchar(64) DEFAULT NULL COMMENT '文件内容哈希',
  `update_reason` varchar(255) DEFAULT NULL COMMENT '更新原因',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `owner_id` varchar(64) DEFAULT NULL COMMENT '归属用户ID；NULL=公共资源，人人可用',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rag_id` (`rag_id`),
  KEY `idx_update_time` (`update_time`),
  KEY `idx_ai_client_rag_order_owner` (`owner_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='知识库配置表';
CREATE TABLE IF NOT EXISTS `ai_client_system_prompt` (
  `id` bigint NOT NULL COMMENT '主键ID（雪花）',
  `prompt_id` varchar(64) NOT NULL COMMENT '提示词ID',
  `prompt_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '提示词名称',
  `prompt_content` text NOT NULL COMMENT '提示词内容',
  `description` varchar(1024) DEFAULT NULL COMMENT '描述',
  `status` tinyint(1) DEFAULT '1' COMMENT '状态(0:禁用,1:启用)',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `owner_id` varchar(64) DEFAULT NULL COMMENT '归属用户ID；NULL=公共资源，人人可用',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_prompt_id` (`prompt_id`),
  KEY `idx_ai_client_system_prompt_owner` (`owner_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统提示词配置表';
CREATE TABLE IF NOT EXISTS `ai_client_tool_mcp` (
  `id` bigint NOT NULL COMMENT '主键ID（雪花）',
  `mcp_id` varchar(64) NOT NULL COMMENT 'MCP名称',
  `mcp_name` varchar(50) NOT NULL COMMENT 'MCP名称',
  `transport_type` varchar(20) NOT NULL COMMENT '传输类型(sse/stdio)',
  `transport_config` varchar(1024) DEFAULT NULL COMMENT '传输配置(sse/stdio)',
  `request_timeout` int DEFAULT '180' COMMENT '请求超时时间(分钟)',
  `status` tinyint(1) DEFAULT '1' COMMENT '状态(0:禁用,1:启用)',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `owner_id` varchar(64) DEFAULT NULL COMMENT '归属用户ID；NULL=公共资源，人人可用',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_mcp_id` (`mcp_id`),
  KEY `idx_ai_client_tool_mcp_owner` (`owner_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='MCP客户端配置表';
CREATE TABLE IF NOT EXISTS `ai_rag_update_task` (
  `id` bigint NOT NULL COMMENT '主键ID（雪花）',
  `task_id` varchar(64) NOT NULL COMMENT '任务ID',
  `rag_ids` text COMMENT '知识库ID列表(JSON)',
  `update_reason` varchar(255) DEFAULT NULL COMMENT '更新原因',
  `status` varchar(20) DEFAULT 'PENDING' COMMENT '状态(PENDING/PROCESSING/COMPLETED/FAILED)',
  `progress` int DEFAULT '0' COMMENT '进度(0-100)',
  `total_items` int DEFAULT '0' COMMENT '总条目数',
  `processed_items` int DEFAULT '0' COMMENT '已处理条目数',
  `failed_items` int DEFAULT '0' COMMENT '失败条目数',
  `error_message` text COMMENT '错误信息',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `owner_id` varchar(64) DEFAULT NULL COMMENT '归属用户ID；NULL=公共资源，人人可用',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_task_id` (`task_id`),
  KEY `idx_status` (`status`),
  KEY `idx_create_time` (`create_time`),
  KEY `idx_ai_rag_update_task_owner` (`owner_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='知识库更新任务表';
CREATE TABLE IF NOT EXISTS `ai_rag_version_history` (
  `id` bigint NOT NULL COMMENT '主键ID（雪花）',
  `rag_id` varchar(50) NOT NULL COMMENT '知识库ID',
  `version` int NOT NULL COMMENT '版本号',
  `file_hash` varchar(64) DEFAULT NULL COMMENT '文件哈希',
  `update_reason` varchar(255) DEFAULT NULL COMMENT '更新原因',
  `metadata_snapshot` json DEFAULT NULL COMMENT '元数据快照',
  `document_count` int DEFAULT '0' COMMENT '文档数量',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `owner_id` varchar(64) DEFAULT NULL COMMENT '归属用户ID；NULL=公共资源，人人可用',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rag_version` (`rag_id`,`version`),
  KEY `idx_rag_id` (`rag_id`),
  KEY `idx_version` (`version`),
  KEY `idx_ai_rag_version_history_owner` (`owner_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='知识库版本历史表';
