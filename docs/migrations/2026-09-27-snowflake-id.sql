-- ============================================================================
-- 2026-09-27 主键改雪花：15 张表去掉 AUTO_INCREMENT
-- ============================================================================
-- 背景：改用雪花算法统一生成主键（Long 19 位）。MySQL 的自增列会与雪花"抢主键"，
-- 所以必须去掉 AUTO_INCREMENT —— 去掉之后 MyBatis-Plus 会在插入前用 IdWorker 生成雪花值
-- （PO 上 @TableId(type = IdType.ASSIGN_ID)）。
--
-- 现有数据的主键值一个都不动（历史值 1、2、3… 继续有效），只是不再由数据库自动分配。
--
-- ⚠️ 配套改动（代码侧，已在同一次改动里完成）：
--   1. 15 个 PO 的 @TableId(type = IdType.AUTO) → ASSIGN_ID；
--   2. 后端统一把 Long 序列化成字符串返回前端（Jackson 配置），否则 19 位数字会被 JS 丢精度；
--   3. 业务 ID（userId / agentId / configId / ragId / clientId / apiId / modelId / advisorId /
--      promptId / mcpId）也改用雪花字符串。
--
-- 幂等：去掉 AUTO_INCREMENT 天然幂等，重复执行无副作用。
-- 执行：mysql -h127.0.0.1 -P13306 -uroot -p ai-agent-station-study < 2026-09-27-snowflake-id.sql
-- ============================================================================

ALTER TABLE `admin_user`              MODIFY COLUMN `id` bigint NOT NULL COMMENT '主键ID（雪花）';
ALTER TABLE `ai_agent`                MODIFY COLUMN `id` bigint NOT NULL COMMENT '主键ID（雪花）';
ALTER TABLE `ai_agent_draw_config`    MODIFY COLUMN `id` bigint NOT NULL COMMENT '主键ID（雪花）';
ALTER TABLE `ai_agent_flow_config`    MODIFY COLUMN `id` bigint NOT NULL COMMENT '主键ID（雪花）';
ALTER TABLE `ai_agent_task_schedule`  MODIFY COLUMN `id` bigint NOT NULL COMMENT '主键ID（雪花）';
ALTER TABLE `ai_client`               MODIFY COLUMN `id` bigint NOT NULL COMMENT '主键ID（雪花）';
ALTER TABLE `ai_client_api`           MODIFY COLUMN `id` bigint NOT NULL COMMENT '主键ID（雪花）';
ALTER TABLE `ai_client_advisor`       MODIFY COLUMN `id` bigint NOT NULL COMMENT '主键ID（雪花）';
ALTER TABLE `ai_client_config`        MODIFY COLUMN `id` bigint NOT NULL COMMENT '主键ID（雪花）';
ALTER TABLE `ai_client_model`         MODIFY COLUMN `id` bigint NOT NULL COMMENT '主键ID（雪花）';
ALTER TABLE `ai_client_rag_order`     MODIFY COLUMN `id` bigint NOT NULL COMMENT '主键ID（雪花）';
ALTER TABLE `ai_client_system_prompt` MODIFY COLUMN `id` bigint NOT NULL COMMENT '主键ID（雪花）';
ALTER TABLE `ai_client_tool_mcp`      MODIFY COLUMN `id` bigint NOT NULL COMMENT '主键ID（雪花）';
ALTER TABLE `ai_rag_update_task`      MODIFY COLUMN `id` bigint NOT NULL COMMENT '主键ID（雪花）';
ALTER TABLE `ai_rag_version_history`  MODIFY COLUMN `id` bigint NOT NULL COMMENT '主键ID（雪花）';

-- 核对：extra 里不应再出现 auto_increment
-- SELECT table_name, column_name, data_type, extra
--   FROM information_schema.columns
--  WHERE table_schema = DATABASE() AND column_name = 'id' ORDER BY table_name;
