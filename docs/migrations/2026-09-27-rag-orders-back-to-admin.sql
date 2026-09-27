-- ============================================================================
-- 2026-09-27 知识库（ai_client_rag_order）改回「管理员私有」
-- ============================================================================
-- 背景：2026-09-27-clients-back-to-public.sql 把 ai_client 及其**全部子表**（含 ai_client_rag_order）
--       设成了系统默认（owner_id = NULL，人人可用）。业务复核后确认：
--       客户端/模型/MCP/提示词/顾问 仍然"人人可用"没问题，但**知识库是私有的** ——
--       默认知识库属于管理员，普通用户看不到、用不到、也不该计入他的数据总览；
--       普通用户要知识库就自己建（owner_id = 他自己）。
--
-- 幂等：只更新 owner_id 仍为空的行，可重复执行。
-- 执行：mysql -h127.0.0.1 -P13306 -uroot -p ai-agent-station-study < 2026-09-27-rag-orders-back-to-admin.sql
-- ============================================================================

SET @admin_user_id = (SELECT user_id FROM admin_user WHERE user_role = 'admin' ORDER BY id LIMIT 1);
SELECT CONCAT('把系统默认知识库划归 admin(', IFNULL(@admin_user_id, '未找到管理员，脚本不做任何修改'),
              ')；普通用户的数据总览知识库数将变为 0') AS notice;

-- 只动"系统默认"那几行；普通用户自己建的知识库（owner 已是本人）不受影响
UPDATE ai_client_rag_order SET owner_id = @admin_user_id
 WHERE owner_id IS NULL AND @admin_user_id IS NOT NULL;

-- 核对：按归属看分布（应只剩管理员私有；若还有 NULL 说明有行没划过来）
-- SELECT IFNULL(owner_id,'(系统默认)') owner, COUNT(*) c FROM ai_client_rag_order GROUP BY 1;

-- 说明：ai_rag_update_task / ai_rag_version_history 本来就是管理员私有（前一个迁移划过去的），这里不动。
