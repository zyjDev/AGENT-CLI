-- ============================================================================
-- 2026-09-26 管理员角色：admin_user 加 user_role
-- ============================================================================
-- 为什么需要它：数据按用户隔离后，「公共资源」（owner_id 为空）默认对所有人可见，
-- 若不做限制，任何普通用户都能改到公共资源（例如 6 个基础智能体的画布配置）。
-- 因此引入角色：
--   user_role = 'admin' → 可改公共资源（并管理用户账号）
--   user_role = 'user'  → 只能改自己的资源（公共资源只读）
--
-- 写权限口径（后端 cn.bugstack.ai.types.common.OwnerScope#canWrite）：
--   owner_id 为空            → 仅 admin 可写
--   owner_id = 本人 userId   → 可写
--   其它（他人私有）          → 谁都不可写
--
-- 幂等：内部先查 information_schema，可重复执行。
-- 执行：mysql -h127.0.0.1 -P13306 -uroot -p ai-agent-station-study < 2026-09-26-add-user-role.sql
-- ============================================================================

DROP PROCEDURE IF EXISTS add_user_role_if_missing;

DELIMITER $$
CREATE PROCEDURE add_user_role_if_missing()
BEGIN
    IF NOT EXISTS (SELECT 1
                   FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'admin_user'
                     AND COLUMN_NAME = 'user_role') THEN
        ALTER TABLE `admin_user`
            ADD COLUMN `user_role` varchar(32) NOT NULL DEFAULT 'user'
            COMMENT '角色：admin=管理员（可改公共资源、可管理账号），user=普通用户（只能改自己的资源）';
    END IF;
END$$
DELIMITER ;

CALL add_user_role_if_missing();
DROP PROCEDURE IF EXISTS add_user_role_if_missing;

-- 现有账号全部默认 user（DDL 的 DEFAULT 已保证），把已知的管理员账号提升为 admin。
-- ⚠️ 若你的管理员账号不叫 admin，请把下面这条的 username 改成实际账号名再执行。
UPDATE `admin_user` SET `user_role` = 'admin' WHERE `username` = 'admin';

-- 核对：
-- SELECT id, user_id, username, user_role, status FROM admin_user;
