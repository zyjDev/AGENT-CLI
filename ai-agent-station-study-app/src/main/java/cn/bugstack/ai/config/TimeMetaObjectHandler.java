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
     * 资源归属：插入时自动把「当前登录用户」写进 owner_id，这样各模块的 create 接口
     * 都不必逐处 set，也不会漏。
     * <p>
     * 刻意保持两条语义：
     * 1. 实体没有 ownerId 字段（如 admin_user）→ 什么都不做；
     * 2. 没有用户上下文（启动装配、定时任务、异步线程）→ 不写，保持 NULL = 公共资源。
     *    正因如此，跨线程链路绝不能依赖本方法，必须显式传 owner。
     */
    private void fillOwnerIfAbsent(MetaObject metaObject) {
        if (!metaObject.hasSetter("ownerId")) {
            return;
        }
        if (metaObject.getValue("ownerId") != null) {
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
