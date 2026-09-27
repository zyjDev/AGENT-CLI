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
}
