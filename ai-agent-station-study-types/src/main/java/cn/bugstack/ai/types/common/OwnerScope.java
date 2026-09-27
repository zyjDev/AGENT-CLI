package cn.bugstack.ai.types.common;

/**
 * 资源归属（owner）约定 —— 全项目「按用户隔离」的唯一口径。
 *
 * ============================ 语义 ============================
 * MySQL 业务表：owner_id 为空（NULL） = 公共资源，人人可用；
 *              owner_id = 某个 userId  = 该用户的私有资源。
 *   例：现有 agent_id = 1~6 的 6 个基础智能体、课程演示用的公共知识库，都保持为空，
 *       因此"底层给的智能体人人都有"不需要任何额外配置。
 *
 * 向量库（PostgreSQL jsonb 的 metadata）：jsonb 里没有 NULL 语义可比对，
 *   因此公共归属统一写成字符串 {@link #PUBLIC_OWNER}，私有归属写 userId。
 *   检索时拼 `knowledge == 'tag' && ownerId == '<owner>'`：
 *     - 私有知识库 → ownerId = 自己的 userId
 *     - 公共知识库 → ownerId = __public__
 *   这样即便两个用户用了同名 knowledgeTag，也不会互相召回。
 *
 * ⚠️ 视图层（Controller）用 {@link #isVisible} 判断；跨线程的装配/检索链路必须显式传 owner，
 *    不能用线程上下文（详见 trigger 模块的 UserContext 注释）。
 * ==============================================================
 *
 * @author bugstack虫洞栈
 */
public final class OwnerScope {

    /** 向量库 metadata 里存放归属的字段名 */
    public static final String VECTOR_OWNER_FIELD = "ownerId";

    /** 公共资源在向量 metadata 中的取值（MySQL 侧公共资源是 NULL） */
    public static final String PUBLIC_OWNER = "__public__";

    private OwnerScope() {
    }

    /** 是否公共资源（owner 为空即公共） */
    public static boolean isPublic(String ownerId) {
        return !hasText(ownerId);
    }

    /**
     * 资源对指定用户是否可见：公共资源人人可见；私有资源只有 owner 本人可见。
     *
     * @param ownerId       资源归属（为空 = 公共）
     * @param currentUserId 当前登录用户 id
     */
    public static boolean isVisible(String ownerId, String currentUserId) {
        if (isPublic(ownerId)) {
            return true;
        }
        return hasText(currentUserId) && ownerId.equals(currentUserId);
    }

    /** 向量 metadata 用的归属值：公共资源转成 {@link #PUBLIC_OWNER}，私有资源原样用 userId */
    public static String vectorOwnerOf(String ownerId) {
        return isPublic(ownerId) ? PUBLIC_OWNER : ownerId;
    }

    /**
     * 写权限（改 / 删）判定：
     * <ul>
     *   <li>{@code owner_id} 为空（公共资源，如 6 个基础智能体）→ <b>仅管理员</b>可写；</li>
     *   <li>{@code owner_id = 本人} → 可写；</li>
     *   <li>他人私有资源 → 谁都不可写（管理员也不越权覆盖别人的私有数据）。</li>
     * </ul>
     *
     * @param ownerId       资源归属
     * @param currentUserId 当前登录用户 id
     * @param admin         当前用户是否管理员
     */
    public static boolean canWrite(String ownerId, String currentUserId, boolean admin) {
        if (isPublic(ownerId)) {
            return admin;
        }
        return hasText(currentUserId) && ownerId.equals(currentUserId);
    }

    private static boolean hasText(String value) {
        if (value == null) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isWhitespace(value.charAt(i))) {
                return true;
            }
        }
        return false;
    }
}
