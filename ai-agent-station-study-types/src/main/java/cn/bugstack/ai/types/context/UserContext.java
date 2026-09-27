package cn.bugstack.ai.types.context;

/**
 * 当前登录用户上下文（请求线程内有效）。
 *
 * ============================ 为什么需要它 ============================
 * 改造前：HTTP 鉴权拦截器校验完 token 就放行，**不落地任何身份**，
 * 业务代码无从得知"当前是谁"，于是 ai_agent / 知识库 / 客户端资源全都对所有人可见。
 * 现在数据要按用户隔离（owner_id 为空 = 公共资源，非空 = 本人私有），
 * 第一件事就是把 JWT 里的 subject（userId）与 claim（username）落到线程上下文里。
 *
 * ⚠️ 两个必须遵守的约束：
 * 1. 只在**请求线程**里有效。装配、向量检索等链路会切线程池（CompletableFuture），
 *    那里取不到值 —— 这类跨线程链路必须把 owner 作为**方法参数**显式传递。
 * 2. 必须在请求结束时清理（拦截器 afterCompletion），否则 Tomcat 复用线程时会串用户。
 * =====================================================================
 *
 * @author bugstack虫洞栈
 */
public final class UserContext {

    /** 管理员角色标识（与 admin_user.user_role 一致） */
    public static final String ROLE_ADMIN = "admin";

    /** 普通用户角色标识 */
    public static final String ROLE_USER = "user";

    /**
     * 当前登录用户：userId 是 JWT 的 subject（同时也是业务表 owner_id 的取值），
     * username 用于展示与审计，role 决定能否修改公共资源
     */
    public record LoginUser(String userId, String username, String role) {

        public boolean isAdmin() {
            return ROLE_ADMIN.equalsIgnoreCase(role);
        }
    }

    private static final ThreadLocal<LoginUser> HOLDER = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(LoginUser loginUser) {
        HOLDER.set(loginUser);
    }

    public static LoginUser get() {
        return HOLDER.get();
    }

    /** 当前用户 id；未登录（或跨线程取不到）返回 null，调用方需自行处理 */
    public static String userId() {
        LoginUser loginUser = HOLDER.get();
        return loginUser == null ? null : loginUser.userId();
    }

    public static String username() {
        LoginUser loginUser = HOLDER.get();
        return loginUser == null ? null : loginUser.username();
    }

    /** 当前用户是否管理员；未登录 / 取不到上下文时按"非管理员"处理（fail-closed） */
    public static boolean isAdmin() {
        LoginUser loginUser = HOLDER.get();
        return loginUser != null && loginUser.isAdmin();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
