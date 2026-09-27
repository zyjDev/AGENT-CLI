-- ============================================================================
-- 2026-09-27 现有资源归属：把「资源包 + 知识库」划归管理员账号
-- ============================================================================
-- 目标模型（三类）：
--   1) owner_id 为空   = 系统默认：全体用户「可用但不可见、不可改」
--                        → 6 个基础智能体 + 1 个示例 RAG 智能体 + 它们的画布配置/装配关系
--   2) owner_id = admin = 管理员私有：普通用户完全看不到（原有那批客户端/模型/Key/MCP/顾问/提示词/知识库）
--   3) owner_id = 某用户 = 该用户私有：只有本人可见、可用、可改
--
-- 为什么这批资源要归 admin：它们是课程演示/管理员配好的资产，
-- 普通用户不需要（也不应该）看到里面的 apiKey；但系统默认智能体引用它们时
-- 依然能正常工作 —— 装配链路是按 ID 直查的，不做归属过滤。
--
-- 幂等：只更新 owner_id 仍为空的行，可重复执行。
-- 执行：mysql -h127.0.0.1 -P13306 -uroot -p ai-agent-station-study < 2026-09-27-own-legacy-resources-to-admin.sql
-- ============================================================================

-- 说明：adminUserId 取「第一个管理员账号」的 user_id（本地是 10001）。
--       若你有多个管理员账号，请把子查询改成你希望的那个账号。
SET @admin_user_id = (SELECT user_id FROM admin_user WHERE user_role = 'admin' ORDER BY id LIMIT 1);
SELECT CONCAT('归属目标管理员 user_id = ', IFNULL(@admin_user_id, '(未找到管理员账号，脚本将不做任何修改)')) AS notice;

UPDATE ai_client             SET owner_id = @admin_user_id WHERE owner_id IS NULL AND @admin_user_id IS NOT NULL;
UPDATE ai_client_config      SET owner_id = @admin_user_id WHERE owner_id IS NULL AND @admin_user_id IS NOT NULL;
UPDATE ai_client_model       SET owner_id = @admin_user_id WHERE owner_id IS NULL AND @admin_user_id IS NOT NULL;
UPDATE ai_client_api         SET owner_id = @admin_user_id WHERE owner_id IS NULL AND @admin_user_id IS NOT NULL;
UPDATE ai_client_advisor     SET owner_id = @admin_user_id WHERE owner_id IS NULL AND @admin_user_id IS NOT NULL;
UPDATE ai_client_system_prompt SET owner_id = @admin_user_id WHERE owner_id IS NULL AND @admin_user_id IS NOT NULL;
UPDATE ai_client_tool_mcp    SET owner_id = @admin_user_id WHERE owner_id IS NULL AND @admin_user_id IS NOT NULL;
UPDATE ai_client_rag_order   SET owner_id = @admin_user_id WHERE owner_id IS NULL AND @admin_user_id IS NOT NULL;

-- 刻意**不动**的表（保持 owner_id 为空 = 系统默认，可用但不可见不可改）：
--   ai_agent             —— 6 个基础智能体 + 示例 RAG 智能体
--   ai_agent_draw_config —— 它们的画布配置
--   ai_agent_flow_config —— 它们的装配关系

-- 核对：按表看归属分布
-- SELECT 'ai_agent' t, IFNULL(owner_id,'(系统默认)') owner, COUNT(*) c FROM ai_agent GROUP BY 2
-- UNION ALL SELECT 'ai_client', IFNULL(owner_id,'(系统默认)'), COUNT(*) FROM ai_client GROUP BY 2
-- UNION ALL SELECT 'ai_client_api', IFNULL(owner_id,'(系统默认)'), COUNT(*) FROM ai_client_api GROUP BY 2
-- UNION ALL SELECT 'ai_client_rag_order', IFNULL(owner_id,'(系统默认)'), COUNT(*) FROM ai_client_rag_order GROUP BY 2;
