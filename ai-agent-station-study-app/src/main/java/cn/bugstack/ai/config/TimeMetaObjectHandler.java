package cn.bugstack.ai.config;

import cn.bugstack.ai.types.context.UserContext;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class TimeMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, now);
        this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, now);
        fillOwnerIfAbsent(metaObject);
    }

    /**
     * 资源归属：插入时按「谁建的归谁」自动写 owner_id，各模块的 create 接口不必逐处 set，也不会漏。
     * <p>
     * 归属规则（2026-09-27 与业务确认）：
     * <ol>
     *   <li><b>普通用户</b>新建 → 归自己：私有资源，只有本人可见 / 可用 / 可改；</li>
     *   <li><b>管理员</b>新建 → 平台默认资源（owner_id 留空）：人人可用、由管理员维护。
     *       这样管理员建"给大家用的模型/客户端"时不必再手动改库。</li>
     * </ol>
     * 另外两条边界：
     * <ul>
     *   <li>实体没有 ownerId 字段（如 admin_user、ai_agent_task_schedule）→ 什么都不做；</li>
     *   <li>没有用户上下文（启动装配、定时任务、异步线程）→ 不写，保持 NULL = 公共资源。
     *       正因如此，跨线程链路绝不能依赖本方法，必须显式传 owner。</li>
     * </ul>
     */
    private void fillOwnerIfAbsent(MetaObject metaObject) {
        if (!metaObject.hasSetter("ownerId")) {
            return;
        }
        if (metaObject.getValue("ownerId") != null) {
            return;
        }
        // 管理员新建 = 平台默认资源：留空（NULL）才是"人人可用"，写成管理员自己的 id 会变成他私有，
        // 普通用户就拿不到了 —— 这跟"管理员维护公共资源"的定位正好相反。
        if (UserContext.isAdmin()) {
            return;
        }
        String userId = UserContext.userId();
        if (userId == null || userId.isBlank()) {
            return;
        }
        metaObject.setValue("ownerId", userId);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
    }

}
