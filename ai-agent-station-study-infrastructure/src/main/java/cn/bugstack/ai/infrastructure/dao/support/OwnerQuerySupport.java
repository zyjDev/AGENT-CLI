package cn.bugstack.ai.infrastructure.dao.support;

import cn.bugstack.ai.types.context.UserContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;

/**
 * 「列表类」查询的归属过滤。
 *
 * ============================ 用在哪、不用在哪 ============================
 * ✅ 用在：各资源模块的**列表/条件查询**（queryAll、queryEnabledXxx、queryBy 名称/状态/类型…）
 *    —— 这些是用户能看到别人数据的唯一入口。
 * ❌ **不要**用在：按主键/按业务 id 的单条查询（queryById、queryByAgentId、queryByClientId…）。
 *    原因见下：
 *      装配与向量检索会切线程池（AiClientLoadDataStrategy 的 CompletableFuture），
 *      那些线程里 {@link UserContext} 是空的。若在按 id 查询上也加归属过滤，
 *      私有智能体装配时会"查不到任何资源"，表现为静默装配失败（最难查的一类故障）。
 *      因此跨线程链路必须显式传 owner，越权访问则在 Controller 入口处校验。
 *
 * 语义：{@code owner_id IS NULL}（公共资源）对所有人可见；
 *      非空则只有 owner 本人可见。
 *      无用户上下文（启动装配 / 定时任务）时**只返回公共资源** —— 这是刻意的：
 *      私有资源应由其 owner 触发装配，而不是开机时替所有用户全量装进 Spring 容器。
 * ==========================================================================
 *
 * @author bugstack虫洞栈
 */
public final class OwnerQuerySupport {

    /** 业务表统一的归属列名 */
    public static final String OWNER_COLUMN = "owner_id";

    private OwnerQuerySupport() {
    }

    /** 字符串列名版本（配合 {@code QueryWrapper}） */
    public static <T> QueryWrapper<T> visibleWrapper() {
        QueryWrapper<T> wrapper = new QueryWrapper<>();
        String ownerId = UserContext.userId();
        if (ownerId == null || ownerId.isBlank()) {
            wrapper.isNull(OWNER_COLUMN);
        } else {
            wrapper.and(w -> w.isNull(OWNER_COLUMN).or().eq(OWNER_COLUMN, ownerId));
        }
        return wrapper;
    }

    /** 方法引用版本（配合 {@code LambdaQueryWrapper}） */
    public static <T> LambdaQueryWrapper<T> visibleLambdaWrapper(SFunction<T, ?> ownerGetter) {
        LambdaQueryWrapper<T> wrapper = new LambdaQueryWrapper<>();
        String ownerId = UserContext.userId();
        if (ownerId == null || ownerId.isBlank()) {
            wrapper.isNull(ownerGetter);
        } else {
            wrapper.and(w -> w.isNull(ownerGetter).or().eq(ownerGetter, ownerId));
        }
        return wrapper;
    }

    /** 资源是否对当前登录用户可见（公共 或 本人） */
    public static boolean visibleToCurrentUser(String ownerId) {
        return cn.bugstack.ai.types.common.OwnerScope.isVisible(ownerId, UserContext.userId());
    }
}
