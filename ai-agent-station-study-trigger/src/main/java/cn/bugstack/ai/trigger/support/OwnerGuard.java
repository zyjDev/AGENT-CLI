package cn.bugstack.ai.trigger.support;

import cn.bugstack.ai.api.response.Response;
import cn.bugstack.ai.types.common.OwnerScope;
import cn.bugstack.ai.types.context.UserContext;
import cn.bugstack.ai.types.enums.ResponseCode;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 写操作的统一归属校验（改 / 删）。
 *
 * ============================ 权限口径 ============================
 * 读：公共资源（owner_id 为空）人人可读，私有资源只有本人可读。
 *     ⚠️ 「DAO 层已按此过滤」只对下列两类查询成立：
 *        · 列表 / 条件类：queryAll、queryEnabledXxx、queryByStatus / Type / Name…
 *        · 主键查询：queryById（DAO 内部已带同口径校验）
 *     **不成立**：按业务 id 的单条 / 条件查询（queryByClientId、queryByApiId、queryByAgentId…
 *     一律**刻意不带**归属过滤）—— 跨线程装配链路里 {@link UserContext} 是空的，加了过滤会让
 *     私有资源静默装配失败（详见 {@code OwnerQuerySupport} 类注释）。
 *     因此 **请求线程里用 queryByXxxId 取数并返回给用户的地方，必须自己调 {@link #readable} 补校验**，
 *     否则就是「凭一个业务 id 即可读到他人私有资源」的越权读取。
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
 * 用法（各 Controller 的查询入口 —— 走 queryByXxxId 时必须补）：
 * <pre>
 *   Xxx po = xxxDao.queryByClientId(clientId);
 *   if (po != null && !OwnerGuard.readable(po.getOwnerId())) {
 *       po = null;                     // 视同不存在，复用既有的「未找到」分支
 *   }
 *   // 一次查出多行的：Xxx = OwnerGuard.readableOnly(xxxDao.queryByApiId(apiId), Xxx::getOwnerId);
 * </pre>
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

    /**
     * 当前登录用户能否读这条资源（ownerId 为空 = 公共资源，人人可读）。
     *
     * <p>口径与各 DAO 的 {@code queryById} 内部校验、以及
     * {@code OwnerQuerySupport.visibleToCurrentUser} 完全一致：公共资源人人可读，
     * 私有资源只有 owner 本人可读。**刻意不采用「列表可见」口径**
     * （{@code listVisibleToCurrentUser}）—— 那样会改变普通用户读公共资源详情的既有行为。
     *
     * <p>只用于**请求线程**。跨线程装配 / 检索链路里 {@link UserContext} 是空的，
     * 那些地方应显式传 owner，不能调本方法。
     */
    public static boolean readable(String ownerId) {
        return OwnerScope.isVisible(ownerId, UserContext.userId());
    }

    /**
     * 读侧批量过滤：剔除当前用户不可读（他人私有）的行。
     *
     * <p>用于「一次查出多行」的读路径（如按 apiId 查该通道下全部模型、列表接口按业务 id 精确查询）。
     * 入参为 null 时返回空列表，调用方无需再判空。
     *
     * @param list        待过滤的行（可为 null）
     * @param ownerGetter 取该行归属的取值函数，如 {@code AiClientModel::getOwnerId}
     */
    public static <T> List<T> readableOnly(List<T> list, Function<T, String> ownerGetter) {
        if (list == null || list.isEmpty()) {
            return new ArrayList<>();
        }
        return list.stream()
                .filter(po -> po != null && readable(ownerGetter.apply(po)))
                .collect(Collectors.toList());
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
