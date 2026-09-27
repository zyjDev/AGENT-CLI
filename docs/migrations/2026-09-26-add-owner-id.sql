-- ============================================================================
-- 2026-09-26 按用户隔离：给业务表补 owner_id，并回填向量库归属
-- ============================================================================
-- 语义约定（后端 cn.bugstack.ai.types.common.OwnerScope 与此一一对应）：
--   owner_id 为空（NULL） → 公共资源，人人可用（现有 6 个基础智能体就是这类）
--   owner_id = 某个 userId → 该用户的私有资源（他拖拉拽搭的智能体、客户端、API Key、
--                            模型、顾问、提示词、MCP 工具、RAG 知识库等）
--   ⚠️ 向量库（PostgreSQL）里没有 NULL 语义可依赖，公共归属统一写成字符串 '__public__'
--
-- 执行顺序：先跑本文件（MySQL 部分），再跑文件末尾的 PostgreSQL 部分。
-- 幂等：MySQL 部分内部先查 information_schema，可重复执行；
--       PostgreSQL 的回填带 WHERE 条件，也可重复执行。
--
-- MySQL（docker 默认 127.0.0.1:13306，库名 ai-agent-station-study）：
--   mysql -h127.0.0.1 -P13306 -uroot -p ai-agent-station-study < 2026-09-26-add-owner-id.sql
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 一、MySQL：为需要按用户隔离的表加 owner_id 与索引
-- ---------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS add_owner_column_if_missing;

DELIMITER $$
CREATE PROCEDURE add_owner_column_if_missing(IN tbl VARCHAR(64))
BEGIN
    IF NOT EXISTS (SELECT 1
                   FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = tbl
                     AND COLUMN_NAME = 'owner_id') THEN
        SET @ddl = CONCAT('ALTER TABLE `', tbl, '` ADD COLUMN `owner_id` varchar(64) NULL '
                          'COMMENT ''归属用户ID；NULL=公共资源，人人可用''');
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;

    IF NOT EXISTS (SELECT 1
                   FROM information_schema.STATISTICS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = tbl
                     AND INDEX_NAME = CONCAT('idx_', tbl, '_owner')) THEN
        SET @ddl2 = CONCAT('CREATE INDEX `idx_', tbl, '_owner` ON `', tbl, '` (`owner_id`)');
        PREPARE stmt2 FROM @ddl2;
        EXECUTE stmt2;
        DEALLOCATE PREPARE stmt2;
    END IF;
END$$
DELIMITER ;

-- 智能体与编排
CALL add_owner_column_if_missing('ai_agent');                 -- 智能体（公共 6 个保持 NULL）
CALL add_owner_column_if_missing('ai_agent_draw_config');     -- 画布配置
CALL add_owner_column_if_missing('ai_agent_flow_config');     -- 智能体 ↔ 客户端 装配关系

-- 资源包（key 等都在这里，必须私有）
CALL add_owner_column_if_missing('ai_client');                -- 对话客户端
CALL add_owner_column_if_missing('ai_client_config');          -- 客户端配置（资源引用关系）
CALL add_owner_column_if_missing('ai_client_api');             -- API 通道（baseUrl / apiKey）
CALL add_owner_column_if_missing('ai_client_model');           -- 模型
CALL add_owner_column_if_missing('ai_client_advisor');         -- 顾问（拦截器）
CALL add_owner_column_if_missing('ai_client_system_prompt');   -- 系统提示词
CALL add_owner_column_if_missing('ai_client_tool_mcp');        -- MCP 工具

-- 知识库
CALL add_owner_column_if_missing('ai_client_rag_order');       -- 知识库配置
CALL add_owner_column_if_missing('ai_rag_update_task');        -- 知识库更新任务
CALL add_owner_column_if_missing('ai_rag_version_history');    -- 知识库版本历史

DROP PROCEDURE IF EXISTS add_owner_column_if_missing;

-- 核对：11+3 张表都应出现 owner_id 列（现有数据全部为 NULL = 公共）
-- SELECT TABLE_NAME, COLUMN_NAME, COLUMN_COMMENT
--   FROM information_schema.COLUMNS
--  WHERE TABLE_SCHEMA = DATABASE() AND COLUMN_NAME = 'owner_id'
--  ORDER BY TABLE_NAME;

-- ---------------------------------------------------------------------------
-- 二、PostgreSQL（向量库）：给现有 chunk 回填 ownerId = '__public__'
-- ---------------------------------------------------------------------------
-- docker 默认 127.0.0.1:15432，库名 ai-rag-knowledge：
--   psql -h 127.0.0.1 -p 15432 -U postgres -d ai-rag-knowledge \
--        -f 2026-09-26-add-owner-id.sql   # 只跑下面这段
--
-- 说明：现有 540 块 chunk 都是课程演示数据（Spring AI 文档），归为公共知识库。
--       新写入的 chunk 由 RagService / RagUpdateServiceImpl 直接写 ownerId，无需回填。
-- UPDATE vector_store_openai
--    SET metadata = jsonb_set(COALESCE(metadata, '{}'::jsonb), '{ownerId}', to_jsonb('__public__'::text), true)
--  WHERE metadata IS NULL
--     OR metadata->>'ownerId' IS NULL;
-- 注意：值必须走 to_jsonb 包成 JSON 字符串，直接写 '{"__public__"}' 会让 jsonb_set 报
--      「Token "__public__" is invalid」—— 已在本地实测。
--
-- 核对：
-- SELECT metadata->>'ownerId' AS owner, count(*) FROM vector_store_openai GROUP BY 1;
