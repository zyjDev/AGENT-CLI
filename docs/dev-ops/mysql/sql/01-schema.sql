-- =============================================================================
-- MySQL 基线建库脚本（全新环境唯一需要执行的 SQL）
-- =============================================================================
-- 为什么有这个文件（对应 CODE-REVIEW-2026-09-27.md 的 P0-2）：
--   本目录被 docker-compose 挂载到容器的 /docker-entrypoint-initdb.d，但此前**是空目录**，
--   仓库里只有 docs/migrations/*.sql 六个 ALTER/UPDATE 增量脚本 —— 增量脚本依赖「表已存在」，
--   于是一个全新的 MySQL 卷起来后库里一张表都没有，应用启动即失败。本文件就是缺失的那份基线。
--
-- 执行方式（三种任选）：
--   1) docker（推荐）：docker-compose-environment.yml 起库时自动执行本目录下 *.sql
--      —— 仅当数据卷为空时才执行；已有数据卷请用第 3 种。
--   2) 命令行（建库 + 建表）：
--      mysql -h127.0.0.1 -P13306 -uroot -p < 01-schema.sql
--   3) 已有库补建（不会动任何现有数据）：
--      mysql -h127.0.0.1 -P13306 -uroot -p ai-agent-station-study < 01-schema.sql
--
-- 幂等性：全部使用 CREATE DATABASE / CREATE TABLE IF NOT EXISTS，**不含 DROP**，
--         重复执行只补缺失的表，永远不会删除已有数据。
--
-- 与 docs/migrations/*.sql 的关系：
--   2026-09-26「补 owner_id」「补 user_role」与 2026-09-27「主键改雪花」等迁移的效果
--   **已经合并进本文件**（owner_id 列、user_role 列、id 无 AUTO_INCREMENT）。
--   全新环境跑完本文件即可，无需再按顺序跑 migrations（重复跑也无害，它们都是幂等的）。
--
-- 内容来源与局限（重要）：
--   * 5 张绘图表（ai_agent_draw_*）的字段/宽度/索引取自仓库内原始 DDL docs/ai-agent-draw-tables.sql，
--     仅做两处必要调整：id 去掉 AUTO_INCREMENT、ai_agent_draw_config 增加 owner_id。
--   * 其余 14 张表**没有**原始 DDL 落在仓库里：字段名来自 PO 实体
--     （cn.bugstack.ai.infrastructure.dao.po.*）+ Mapper SQL 中实际出现的列名，
--     owner_id / user_role 的列定义与索引名直接对齐 docs/migrations/2026-09-26-*.sql。
--     ⚠️ 字符串列宽按项目既有约定推断（业务ID varchar(64)、名称 varchar(100)、描述 varchar(500)、
--        状态 tinyint(1)、JSON 用 text/longtext），并刻意取偏宽的值以避免插入截断。
--        若手上有现网库，建议逐表比对照抄：SHOW CREATE TABLE `表名`;
--   * 业务ID列（agent_id / client_id / api_id …）**刻意只建普通索引、不建唯一约束**：
--     原始 DDL 是否唯一无从考证，加唯一约束可能在导入历史数据时报错。如要收紧，先自查重复：
--       SELECT agent_id, COUNT(*) FROM ai_agent GROUP BY agent_id HAVING COUNT(*) > 1;
-- =============================================================================

CREATE DATABASE IF NOT EXISTS `ai-agent-station-study`
    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

USE `ai-agent-station-study`;

-- -----------------------------------------------------------------------------
-- 一、管理端用户
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `admin_user`
(
    `id`          bigint       NOT NULL COMMENT '主键ID（雪花，应用侧生成）',
    `user_id`     varchar(64)  NOT NULL COMMENT '用户ID（对外暴露的业务ID）',
    `username`    varchar(64)  NOT NULL COMMENT '登录用户名',
    `password`    varchar(128) NOT NULL COMMENT '口令哈希（预留 128 以兼容 BCrypt/MD5 等实现）',
    `status`      tinyint(1)   NOT NULL DEFAULT '1' COMMENT '状态(0:禁用,1:启用)',
    `user_role`   varchar(32)  NOT NULL DEFAULT 'user' COMMENT '角色：admin=管理员（可改公共资源、可管理账号），user=普通用户（只能改自己的资源）',
    `create_time` datetime              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_status` (`status`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='管理端用户表';

-- -----------------------------------------------------------------------------
-- 二、智能体与编排
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `ai_agent`
(
    `id`          bigint       NOT NULL COMMENT '主键ID（雪花）',
    `agent_id`    varchar(64)  NOT NULL COMMENT '智能体ID（业务ID）',
    `agent_name`  varchar(100) NOT NULL COMMENT '智能体名称',
    `description` varchar(500)          DEFAULT NULL COMMENT '描述',
    `channel`     varchar(64)           DEFAULT NULL COMMENT '渠道/类型',
    `strategy`    varchar(255)          DEFAULT NULL COMMENT '执行策略（留宽以兼容完整类名/策略标识）',
    `status`      tinyint(1)   NOT NULL DEFAULT '1' COMMENT '状态(0:禁用,1:启用)',
    `owner_id`    varchar(64)           DEFAULT NULL COMMENT '归属用户ID；NULL=公共资源，人人可用',
    `create_time` datetime              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_agent_id` (`agent_id`),
    KEY `idx_ai_agent_owner` (`owner_id`),
    KEY `idx_status` (`status`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='智能体表';

CREATE TABLE IF NOT EXISTS `ai_agent_flow_config`
(
    `id`          bigint      NOT NULL COMMENT '主键ID（雪花）',
    `agent_id`    varchar(64) NOT NULL COMMENT '智能体ID',
    `client_id`   varchar(64) NOT NULL COMMENT '对话客户端ID',
    `client_name` varchar(100)         DEFAULT NULL COMMENT '客户端名称（冗余，便于展示）',
    `client_type` varchar(32)          DEFAULT NULL COMMENT '客户端类型',
    `sequence`    int                  DEFAULT '0' COMMENT '装配顺序',
    `step_prompt` text COMMENT '该步骤的提示词',
    `status`      tinyint(1)           DEFAULT '1' COMMENT '状态(0:禁用,1:启用)',
    `owner_id`    varchar(64)          DEFAULT NULL COMMENT '归属用户ID；NULL=公共资源，人人可用',
    `create_time` datetime             DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_agent_id` (`agent_id`),
    KEY `idx_client_id` (`client_id`),
    KEY `idx_ai_agent_flow_config_owner` (`owner_id`),
    KEY `idx_sequence` (`sequence`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='智能体↔客户端装配关系表';

CREATE TABLE IF NOT EXISTS `ai_agent_task_schedule`
(
    `id`              bigint       NOT NULL COMMENT '主键ID（雪花）',
    `agent_id`        varchar(64)  NOT NULL COMMENT '智能体ID',
    `task_name`       varchar(100) NOT NULL COMMENT '任务名称',
    `description`     varchar(500)          DEFAULT NULL COMMENT '任务描述',
    `cron_expression` varchar(64)  NOT NULL COMMENT 'cron 表达式',
    `task_param`      text COMMENT '任务参数（JSON/字符串）',
    `status`          tinyint(1)            DEFAULT '1' COMMENT '状态(0:禁用,1:启用)',
    `create_time`     datetime              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     datetime              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_agent_id` (`agent_id`),
    KEY `idx_status` (`status`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='智能体定时任务表';

-- -----------------------------------------------------------------------------
-- 三、拖拉拽画布（字段/索引取自 docs/ai-agent-draw-tables.sql，仅去掉 AUTO_INCREMENT 并补 owner_id）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `ai_agent_draw_config`
(
    `id`          bigint       NOT NULL COMMENT '主键ID（雪花）',
    `config_id`   varchar(64)  NOT NULL COMMENT '配置ID（唯一标识）',
    `config_name` varchar(100) NOT NULL COMMENT '配置名称',
    `description` varchar(500)          DEFAULT NULL COMMENT '配置描述',
    `agent_id`    varchar(64)           DEFAULT NULL COMMENT '关联的智能体ID（来自ai_agent表）',
    `config_data` longtext     NOT NULL COMMENT '完整的拖拉拽配置JSON数据（包含nodes和edges）',
    `version`     int                   DEFAULT '1' COMMENT '配置版本号',
    `status`      tinyint(1)            DEFAULT '1' COMMENT '状态(0:禁用,1:启用)',
    `owner_id`    varchar(64)           DEFAULT NULL COMMENT '归属用户ID；NULL=公共资源，人人可用',
    `create_by`   varchar(64)           DEFAULT NULL COMMENT '创建人',
    `update_by`   varchar(64)           DEFAULT NULL COMMENT '更新人',
    `create_time` datetime              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_config_id` (`config_id`),
    KEY `idx_agent_id` (`agent_id`),
    KEY `idx_ai_agent_draw_config_owner` (`owner_id`),
    KEY `idx_config_name` (`config_name`),
    KEY `idx_status` (`status`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='AI智能体拖拉拽配置主表';

CREATE TABLE IF NOT EXISTS `ai_agent_draw_nodes`
(
    `id`          bigint         NOT NULL COMMENT '主键ID（雪花）',
    `config_id`   varchar(64)    NOT NULL COMMENT '配置ID（关联ai_agent_draw_config）',
    `node_id`     varchar(64)    NOT NULL COMMENT '节点ID（在配置中的唯一标识）',
    `node_type`   varchar(32)    NOT NULL COMMENT '节点类型（start、client、agent、task、advisor、prompt、model、tool_mcp、end等）',
    `node_title`  varchar(100)            DEFAULT NULL COMMENT '节点标题',
    `position_x`  decimal(10, 2)          DEFAULT NULL COMMENT 'X坐标位置',
    `position_y`  decimal(10, 2)          DEFAULT NULL COMMENT 'Y坐标位置',
    `node_data`   text COMMENT '节点数据（JSON格式，包含inputs、outputs、inputsValues等）',
    `ref_id`      varchar(64)             DEFAULT NULL COMMENT '引用的实际资源ID（如agent_id、client_id等）',
    `ref_type`    varchar(32)             DEFAULT NULL COMMENT '引用的资源类型（agent、client、model、prompt、advisor、tool_mcp）',
    `sequence`    int                     DEFAULT '0' COMMENT '节点序号（用于排序）',
    `status`      tinyint(1)              DEFAULT '1' COMMENT '状态(0:禁用,1:启用)',
    `create_time` datetime                DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime                DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_config_node` (`config_id`, `node_id`),
    KEY `idx_config_id` (`config_id`),
    KEY `idx_node_type` (`node_type`),
    KEY `idx_ref_id` (`ref_id`),
    KEY `idx_sequence` (`sequence`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='AI智能体拖拉拽配置节点表';

CREATE TABLE IF NOT EXISTS `ai_agent_draw_edges`
(
    `id`             bigint      NOT NULL COMMENT '主键ID（雪花）',
    `config_id`      varchar(64) NOT NULL COMMENT '配置ID（关联ai_agent_draw_config）',
    `edge_id`        varchar(64) NOT NULL COMMENT '连线ID（自动生成的唯一标识）',
    `source_node_id` varchar(64) NOT NULL COMMENT '源节点ID',
    `target_node_id` varchar(64) NOT NULL COMMENT '目标节点ID',
    `source_port_id` varchar(64)          DEFAULT NULL COMMENT '源端口ID（可选）',
    `target_port_id` varchar(64)          DEFAULT NULL COMMENT '目标端口ID（可选）',
    `edge_type`      varchar(32)          DEFAULT 'default' COMMENT '连线类型（default、conditional等）',
    `edge_data`      text COMMENT '连线数据（JSON格式，扩展信息）',
    `sequence`       int                  DEFAULT '0' COMMENT '连线序号（用于排序）',
    `status`         tinyint(1)           DEFAULT '1' COMMENT '状态(0:禁用,1:启用)',
    `create_time`    datetime             DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`    datetime             DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_config_edge` (`config_id`, `edge_id`),
    KEY `idx_config_id` (`config_id`),
    KEY `idx_source_node` (`source_node_id`),
    KEY `idx_target_node` (`target_node_id`),
    KEY `idx_sequence` (`sequence`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='AI智能体拖拉拽配置连线表';

CREATE TABLE IF NOT EXISTS `ai_agent_draw_relations`
(
    `id`             bigint        NOT NULL COMMENT '主键ID（雪花）',
    `config_id`      varchar(64)   NOT NULL COMMENT '配置ID（关联ai_agent_draw_config）',
    `source_node_id` varchar(64)   NOT NULL COMMENT '源节点ID',
    `source_type`    varchar(32)   NOT NULL COMMENT '源类型（model、client、agent、prompt、advisor、tool_mcp）',
    `source_ref_id`  varchar(64)   NOT NULL COMMENT '源引用ID（实际的资源ID）',
    `target_node_id` varchar(64)   NOT NULL COMMENT '目标节点ID',
    `target_type`    varchar(32)   NOT NULL COMMENT '目标类型（model、client、agent、prompt、advisor、tool_mcp）',
    `target_ref_id`  varchar(64)   NOT NULL COMMENT '目标引用ID（实际的资源ID）',
    `relation_type`  varchar(32)            DEFAULT 'default' COMMENT '关系类型（default、conditional、loop等）',
    `ext_param`      varchar(1024)          DEFAULT NULL COMMENT '扩展参数（JSON格式）',
    `sequence`       int                    DEFAULT '0' COMMENT '关系序号（用于排序）',
    `sync_status`    tinyint(1)             DEFAULT '0' COMMENT '同步状态(0:未同步,1:已同步到ai_client_config)',
    `status`         tinyint(1)             DEFAULT '1' COMMENT '状态(0:禁用,1:启用)',
    `create_time`    datetime               DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`    datetime               DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_config_relation` (`config_id`, `source_node_id`, `target_node_id`),
    KEY `idx_config_id` (`config_id`),
    KEY `idx_source_ref` (`source_type`, `source_ref_id`),
    KEY `idx_target_ref` (`target_type`, `target_ref_id`),
    KEY `idx_sync_status` (`sync_status`),
    KEY `idx_sequence` (`sequence`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='AI智能体拖拉拽配置关系表';

CREATE TABLE IF NOT EXISTS `ai_agent_draw_history`
(
    `id`          bigint      NOT NULL COMMENT '主键ID（雪花）',
    `config_id`   varchar(64) NOT NULL COMMENT '配置ID（关联ai_agent_draw_config）',
    `version`     int         NOT NULL COMMENT '版本号',
    `config_data` longtext    NOT NULL COMMENT '历史配置JSON数据',
    `change_type` varchar(32)          DEFAULT 'update' COMMENT '变更类型（create、update、delete）',
    `change_desc` varchar(500)         DEFAULT NULL COMMENT '变更描述',
    `change_by`   varchar(64)          DEFAULT NULL COMMENT '变更人',
    `create_time` datetime             DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_config_version` (`config_id`, `version`),
    KEY `idx_config_id` (`config_id`),
    KEY `idx_change_type` (`change_type`),
    KEY `idx_create_time` (`create_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='AI智能体拖拉拽配置历史表';

-- -----------------------------------------------------------------------------
-- 四、对话资源包（客户端 / 装配关系 / 通道 / 模型 / 顾问 / 提示词 / MCP）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `ai_client`
(
    `id`          bigint       NOT NULL COMMENT '主键ID（雪花）',
    `client_id`   varchar(64)  NOT NULL COMMENT '对话客户端ID',
    `client_name` varchar(100) NOT NULL COMMENT '客户端名称',
    `description` varchar(500)          DEFAULT NULL COMMENT '描述',
    `status`      tinyint(1)            DEFAULT '1' COMMENT '状态(0:禁用,1:启用)',
    `owner_id`    varchar(64)           DEFAULT NULL COMMENT '归属用户ID；NULL=公共资源，人人可用',
    `create_time` datetime              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_client_id` (`client_id`),
    KEY `idx_ai_client_owner` (`owner_id`),
    KEY `idx_status` (`status`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='对话客户端表';

CREATE TABLE IF NOT EXISTS `ai_client_config`
(
    `id`          bigint        NOT NULL COMMENT '主键ID（雪花）',
    `source_type` varchar(32)   NOT NULL COMMENT '源类型',
    `source_id`   varchar(64)   NOT NULL COMMENT '源ID',
    `target_type` varchar(32)   NOT NULL COMMENT '目标类型',
    `target_id`   varchar(64)   NOT NULL COMMENT '目标ID',
    `ext_param`   varchar(1024)          DEFAULT NULL COMMENT '扩展参数（JSON）',
    `status`      tinyint(1)             DEFAULT '1' COMMENT '状态(0:禁用,1:启用)',
    `owner_id`    varchar(64)            DEFAULT NULL COMMENT '归属用户ID；NULL=公共资源，人人可用',
    `create_time` datetime               DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime               DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_source` (`source_type`, `source_id`),
    KEY `idx_target` (`target_type`, `target_id`),
    KEY `idx_ai_client_config_owner` (`owner_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='客户端资源装配关系表';

CREATE TABLE IF NOT EXISTS `ai_client_api`
(
    `id`               bigint       NOT NULL COMMENT '主键ID（雪花）',
    `api_id`           varchar(64)  NOT NULL COMMENT 'API通道ID',
    `base_url`         varchar(512) NOT NULL COMMENT '服务地址（含 /v1 等前缀）',
    `api_key`          varchar(512)          DEFAULT NULL COMMENT 'API Key（敏感，随 owner_id 隔离）',
    `completions_path` varchar(255)          DEFAULT NULL COMMENT '对话补全路径',
    `embeddings_path`  varchar(255)          DEFAULT NULL COMMENT '向量化路径',
    `status`           tinyint(1)            DEFAULT '1' COMMENT '状态(0:禁用,1:启用)',
    `owner_id`         varchar(64)           DEFAULT NULL COMMENT '归属用户ID；NULL=公共资源，人人可用',
    `create_time`      datetime              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`      datetime              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_api_id` (`api_id`),
    KEY `idx_ai_client_api_owner` (`owner_id`),
    KEY `idx_status` (`status`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='API通道表';

CREATE TABLE IF NOT EXISTS `ai_client_model`
(
    `id`          bigint       NOT NULL COMMENT '主键ID（雪花）',
    `model_id`    varchar(64)  NOT NULL COMMENT '模型ID',
    `api_id`      varchar(64)  NOT NULL COMMENT '所属API通道ID',
    `model_name`  varchar(100) NOT NULL COMMENT '模型名称（如 qwen-plus）',
    `model_type`  varchar(32)           DEFAULT NULL COMMENT '模型类型（openai/ollama…）',
    `model_usage` varchar(32)           DEFAULT NULL COMMENT '用途（chat/embedding…）',
    `status`      tinyint(1)            DEFAULT '1' COMMENT '状态(0:禁用,1:启用)',
    `owner_id`    varchar(64)           DEFAULT NULL COMMENT '归属用户ID；NULL=公共资源，人人可用',
    `create_time` datetime              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_model_id` (`model_id`),
    KEY `idx_api_id` (`api_id`),
    KEY `idx_ai_client_model_owner` (`owner_id`),
    KEY `idx_status` (`status`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='模型表';

CREATE TABLE IF NOT EXISTS `ai_client_advisor`
(
    `id`           bigint        NOT NULL COMMENT '主键ID（雪花）',
    `advisor_id`   varchar(64)   NOT NULL COMMENT '顾问ID',
    `advisor_name` varchar(100)  NOT NULL COMMENT '顾问名称',
    `advisor_type` varchar(32)            DEFAULT NULL COMMENT '顾问类型（拦截器）',
    `order_num`    int                    DEFAULT '0' COMMENT '顺序',
    `ext_param`    varchar(1024)          DEFAULT NULL COMMENT '扩展参数（JSON）',
    `status`       tinyint(1)             DEFAULT '1' COMMENT '状态(0:禁用,1:启用)',
    `owner_id`     varchar(64)            DEFAULT NULL COMMENT '归属用户ID；NULL=公共资源，人人可用',
    `create_time`  datetime               DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`  datetime               DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_advisor_id` (`advisor_id`),
    KEY `idx_ai_client_advisor_owner` (`owner_id`),
    KEY `idx_status` (`status`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='顾问（拦截器）表';

CREATE TABLE IF NOT EXISTS `ai_client_system_prompt`
(
    `id`             bigint       NOT NULL COMMENT '主键ID（雪花）',
    `prompt_id`      varchar(64)  NOT NULL COMMENT '提示词ID',
    `prompt_name`    varchar(100) NOT NULL COMMENT '提示词名称',
    `prompt_content` text COMMENT '提示词内容',
    `description`    varchar(500)          DEFAULT NULL COMMENT '描述',
    `status`         tinyint(1)            DEFAULT '1' COMMENT '状态(0:禁用,1:启用)',
    `owner_id`       varchar(64)           DEFAULT NULL COMMENT '归属用户ID；NULL=公共资源，人人可用',
    `create_time`    datetime              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`    datetime              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_prompt_id` (`prompt_id`),
    KEY `idx_ai_client_system_prompt_owner` (`owner_id`),
    KEY `idx_status` (`status`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='系统提示词表';

CREATE TABLE IF NOT EXISTS `ai_client_tool_mcp`
(
    `id`               bigint      NOT NULL COMMENT '主键ID（雪花）',
    `mcp_id`           varchar(64) NOT NULL COMMENT 'MCP工具ID',
    `mcp_name`         varchar(100)         DEFAULT NULL COMMENT 'MCP工具名称',
    `transport_type`   varchar(32)          DEFAULT NULL COMMENT '传输类型（sse/stdio…）',
    `transport_config` text COMMENT '传输配置（JSON：baseUri、sseEndpoint、requestTimeout 等）',
    `request_timeout`  int                  DEFAULT NULL COMMENT '请求超时（秒）',
    `status`           tinyint(1)           DEFAULT '1' COMMENT '状态(0:禁用,1:启用)',
    `owner_id`         varchar(64)          DEFAULT NULL COMMENT '归属用户ID；NULL=公共资源，人人可用',
    `create_time`      datetime             DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`      datetime             DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_mcp_id` (`mcp_id`),
    KEY `idx_ai_client_tool_mcp_owner` (`owner_id`),
    KEY `idx_status` (`status`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='MCP工具表';

-- -----------------------------------------------------------------------------
-- 五、知识库（配置 / 更新任务 / 版本历史）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `ai_client_rag_order`
(
    `id`            bigint       NOT NULL COMMENT '主键ID（雪花）',
    `rag_id`        varchar(64)  NOT NULL COMMENT '知识库ID',
    `rag_name`      varchar(100) NOT NULL COMMENT '知识库名称',
    `knowledge_tag` varchar(255)          DEFAULT NULL COMMENT '知识标签（多个以逗号分隔，对应向量库 metadata 的 knowledge 过滤）',
    `status`        tinyint(1)            DEFAULT '1' COMMENT '状态(0:禁用,1:启用)',
    `owner_id`      varchar(64)           DEFAULT NULL COMMENT '归属用户ID；NULL=公共资源，人人可用',
    `version`       int                   DEFAULT '1' COMMENT '当前版本号',
    `file_hash`     varchar(128)          DEFAULT NULL COMMENT '当前文件内容哈希（用于判断是否需要重建）',
    `update_reason` varchar(500)          DEFAULT NULL COMMENT '最近一次更新的原因',
    `create_time`   datetime              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   datetime              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_rag_id` (`rag_id`),
    KEY `idx_ai_client_rag_order_owner` (`owner_id`),
    KEY `idx_status` (`status`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='知识库配置表';

CREATE TABLE IF NOT EXISTS `ai_rag_update_task`
(
    `id`              bigint        NOT NULL COMMENT '主键ID（雪花）',
    `task_id`         varchar(64)   NOT NULL COMMENT '更新任务ID',
    `rag_ids`         varchar(1024)          DEFAULT NULL COMMENT '涉及的知识库ID（多个以逗号分隔）',
    `update_reason`   varchar(500)           DEFAULT NULL COMMENT '更新原因',
    `status`          varchar(32)   NOT NULL DEFAULT 'pending' COMMENT '任务状态（字符串枚举：pending/running/success/failed…）',
    `owner_id`        varchar(64)            DEFAULT NULL COMMENT '归属用户ID；NULL=公共资源，人人可用',
    `progress`        int                    DEFAULT '0' COMMENT '进度百分比',
    `total_items`     int                    DEFAULT '0' COMMENT '总条目数',
    `processed_items` int                    DEFAULT '0' COMMENT '已处理条目数',
    `failed_items`    int                    DEFAULT '0' COMMENT '失败条目数',
    `error_message`   varchar(1024)          DEFAULT NULL COMMENT '错误信息',
    `create_time`     datetime               DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     datetime               DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_task_id` (`task_id`),
    KEY `idx_ai_rag_update_task_owner` (`owner_id`),
    KEY `idx_status` (`status`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='知识库更新任务表';

CREATE TABLE IF NOT EXISTS `ai_rag_version_history`
(
    `id`                bigint      NOT NULL COMMENT '主键ID（雪花）',
    `rag_id`            varchar(64) NOT NULL COMMENT '知识库ID',
    `version`           int         NOT NULL COMMENT '版本号',
    `file_hash`         varchar(128)         DEFAULT NULL COMMENT '该版本的文件内容哈希',
    `update_reason`     varchar(500)         DEFAULT NULL COMMENT '更新原因',
    `metadata_snapshot` longtext COMMENT '元数据快照（JSON）',
    `document_count`    int                  DEFAULT '0' COMMENT '该版本文档数',
    `owner_id`          varchar(64)          DEFAULT NULL COMMENT '归属用户ID；NULL=公共资源，人人可用',
    `create_time`       datetime             DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_rag_version` (`rag_id`, `version`),
    KEY `idx_ai_rag_version_history_owner` (`owner_id`),
    KEY `idx_create_time` (`create_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='知识库版本历史表';

-- -----------------------------------------------------------------------------
-- 六、自检：应返回 19 张表
-- -----------------------------------------------------------------------------
-- SELECT COUNT(*) AS table_count FROM information_schema.TABLES
--  WHERE TABLE_SCHEMA = 'ai-agent-station-study' AND TABLE_TYPE = 'BASE TABLE';
-- 期望值 19：admin_user, ai_agent, ai_agent_flow_config, ai_agent_task_schedule,
--           ai_agent_draw_config, ai_agent_draw_nodes, ai_agent_draw_edges,
--           ai_agent_draw_relations, ai_agent_draw_history,
--           ai_client, ai_client_config, ai_client_api, ai_client_model, ai_client_advisor,
--           ai_client_system_prompt, ai_client_tool_mcp,
--           ai_client_rag_order, ai_rag_update_task, ai_rag_version_history
--
-- id 列自查（应无 auto_increment）：
-- SELECT table_name, extra FROM information_schema.COLUMNS
--  WHERE TABLE_SCHEMA = 'ai-agent-station-study' AND column_name = 'id' ORDER BY table_name;
