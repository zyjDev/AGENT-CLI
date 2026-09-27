-- ============================================================================
-- 2026-09-27 基础客户端配置恢复为「系统默认」（owner_id = NULL）
-- ============================================================================
-- 背景：2026-09-27-own-legacy-resources-to-admin.sql 把 ai_client 及子表整体划给了管理员，
--       于是普通用户既看不到、也「数不到」这些基础配置 —— 数据总览里客户端/模型/MCP/提示词/
--       顾问/知识库全是 0（实测确认），看起来像功能坏了。
--
-- 现在的模型（与 6 个基础智能体一致）：
--   owner_id 为空 = 系统默认：全体用户「可用、可计数」，但**不在管理端列表里出现** ——
--                  列表走 visibleWrapper（普通用户只看到本人私有），所以普通用户看不到
--                  里边的 apiKey；写入仍要求管理员（OwnerScope.canWrite）。
--   因此：普通用户的对话能力、数据总览都会算上它们，管理员自己的数字不变。
--
-- 幂等：只更新 owner_id 正好等于管理员 user_id 的行，可重复执行。
-- 执行：mysql -h127.0.0.1 -P13306 -uroot -p ai-agent-station-study < 2026-09-27-clients-back-to-public.sql
-- ============================================================================

-- 说明：adminUserId 取「第一个管理员账号」的 user_id（本地是 10001）。
SET @admin_user_id = (SELECT user_id FROM admin_user WHERE user_role = 'admin' ORDER BY id LIMIT 1);
SELECT CONCAT('把归属 admin(', IFNULL(@admin_user_id, '未找到管理员，脚本不做任何修改'),
              ') 的基础客户端配置改为系统默认') AS notice;

UPDATE ai_client                SET owner_id = NULL WHERE owner_id = @admin_user_id;
UPDATE ai_client_config         SET owner_id = NULL WHERE owner_id = @admin_user_id;
UPDATE ai_client_model          SET owner_id = NULL WHERE owner_id = @admin_user_id;
UPDATE ai_client_api            SET owner_id = NULL WHERE owner_id = @admin_user_id;
UPDATE ai_client_advisor        SET owner_id = NULL WHERE owner_id = @admin_user_id;
UPDATE ai_client_system_prompt  SET owner_id = NULL WHERE owner_id = @admin_user_id;
UPDATE ai_client_tool_mcp       SET owner_id = NULL WHERE owner_id = @admin_user_id;
UPDATE ai_client_rag_order      SET owner_id = NULL WHERE owner_id = @admin_user_id;

-- 刻意**不动**的表：
--   ai_rag_update_task / ai_rag_version_history —— 更新任务与版本历史是运行记录，
--       不是「人人可用的配置资源」，仍归管理员（数据总览也不统计它们）。
--   ai_agent / ai_agent_draw_config / ai_agent_flow_config —— 本来就是系统默认。
--   ai_client 下属的正常私有行（某个普通用户自己建的）—— 不碰。

-- 核对：按表看归属分布（系统默认 = owner_id 为空）
-- SELECT 'ai_client' t, IFNULL(owner_id,'(系统默认)') owner, COUNT(*) c FROM ai_client GROUP BY 2
-- UNION ALL SELECT 'ai_client_model', IFNULL(owner_id,'(系统默认)'), COUNT(*) FROM ai_client_model GROUP BY 2
-- UNION ALL SELECT 'ai_client_tool_mcp', IFNULL(owner_id,'(系统默认)'), COUNT(*) FROM ai_client_tool_mcp GROUP BY 2;
