package cn.bugstack.ai.trigger.support;

import cn.bugstack.ai.api.response.Response;
import cn.bugstack.ai.types.common.OwnerScope;
import cn.bugstack.ai.types.context.UserContext;
import cn.bugstack.ai.types.enums.ResponseCode;

/**
 * 写操作的统一归属校验（改 / 删）。
 *
 * ============================ 权限口径 ============================
 * 读：公共资源（owner_id 为空）人人可见，私有资源只有本人可见（DAO 层已按此过滤）。
 * 写：
 *   - 本人资源        → 本人可写；
 *   - 公共资源        → <b>仅管理员</b>可写（普通用户改不了 6 个基础智能体这类共享数据）；
 *   - 他人私有资源    → 谁都不可写（管理员也不越权覆盖别人的私有数据）。
 * =================================================================
 *
 * 用法（各 Controller 的 update / delete 入口）：
 * <pre>
 *   Xxx existing = xxxDao.queryById(request.getId());
 *   if (existing == null || !OwnerGuard.writable(existing.getOwnerId())) {
 *       return OwnerGuard.deny("客户端");
 *   }
 * </pre>
 * 注意：DAO 的 queryById 本身已带归属过滤，非本人非公共的记录会直接返回 null，
 * 所以这里的 existing == null 同时覆盖了「不存在」与「无权见」两种情况。
 *
 * @author bugstack虫洞栈
 */
public final class OwnerGuard {

    private OwnerGuard() {
    }

    /** 当前登录用户能否写这条资源（ownerId 为空 = 公共资源） */
    public static boolean writable(String ownerId) {
        return OwnerScope.canWrite(ownerId, UserContext.userId(), UserContext.isAdmin());
    }

    /** 统一的无权限响应：语义上不区分「不存在」与「无权」，避免探测他人资源是否存在 */
    public static <T> Response<T> deny(String resourceName) {
        return Response.<T>builder()
                .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                .info(resourceName + "不存在或无权修改")
                .data(null)
                .build();
    }

    /**
     * 新建时给实体盖上归属（各 Controller 的 create 入口，插库前调用一次）。
     *
     * <p>规则与产品确认的一致：<b>普通用户新建 → 归自己；管理员新建 → 留空（平台默认，人人可用）；
     * 无用户上下文（装配 / 定时任务 / 异步线程）→ 留空</b>。
     *
     * <p>为什么不靠 MyBatis-Plus 的 {@code MetaObjectHandler} 自动填充：
     * 项目里虽然有 {@code TimeMetaObjectHandler}，但运行态实测**从未生效**
     * （新建行的 owner_id 一直是 NULL，MP 的 insertFill 门槛与实体注解都满足、方法本身也验证过是对的），
     * 排查成本已经超过收益 —— 关键创建路径改为在这里显式盖章，填充器保留作为兜底。
     *
     * <p>用反射取 {@code ownerId} 而不是让 12 个模块各写一遍 set：既省事，也不会漏。
     * 没有 ownerId 字段的实体（如 admin_user）直接跳过。
     *
     * @param entity 待插入的 PO
     */
    public static void stampOwnerOnCreate(Object entity) {
        if (entity == null) {
            return;
        }
        org.apache.ibatis.reflection.MetaObject metaObject =
                org.apache.ibatis.reflection.SystemMetaObject.forObject(entity);
        if (!metaObject.hasSetter("ownerId")) {
            return;
        }
        if (metaObject.getValue("ownerId") != null) {
            return;
        }
        if (UserContext.isAdmin()) {
            return;
        }
        String userId = UserContext.userId();
        if (userId == null || userId.isBlank()) {
            return;
        }
        metaObject.setValue("ownerId", userId);
    }
}
