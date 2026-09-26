package cn.bugstack.ai.infrastructure.dao;

import cn.bugstack.ai.infrastructure.dao.po.AiAgent;
import cn.bugstack.ai.infrastructure.dao.support.OwnerQuerySupport;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * AI智能体配置表 DAO
 * @description AI智能体配置表数据访问对象（MyBatis-Plus 迁移版，SQL 由 Wrapper 拼接，无 XML）
 */
@Mapper
public interface IAiAgentDao extends BaseMapper<AiAgent> {

    /**
     * 根据智能体ID更新AI智能体配置
     * <p>
     * 实体驱动更新：只有非 null 字段进入 SET 子句，未传字段保持库中原值；
     * update_time 由 TimeMetaObjectHandler 自动填充。
     * <p>
     * 注意：不要在此接口中声明 default int updateById(...)，否则会覆盖 BaseMapper.updateById 的 SQL 派发。
     */
    default int updateByAgentId(AiAgent aiAgent) {
        return update(aiAgent, new UpdateWrapper<AiAgent>().eq("agent_id", aiAgent.getAgentId()));
    }

    default int deleteByAgentId(String agentId) {
        return delete(new QueryWrapper<AiAgent>().eq("agent_id", agentId));
    }

    default AiAgent queryById(Long id) {
        AiAgent po = selectById(id);
        // 归属校验：别人的私有资源对当前用户等同「不存在」（列表已过滤，这里堵"按主键直接读"）
        if (po != null && !OwnerQuerySupport.visibleToCurrentUser(po.getOwnerId())) {
            return null;
        }
        return po;
    }

    default AiAgent queryByAgentId(String agentId) {
        return selectOne(new QueryWrapper<AiAgent>().eq("agent_id", agentId));
    }

    default List<AiAgent> queryEnabledAgents() {
        // 归属过滤：公共资源（owner_id 为空，如 6 个基础智能体）+ 本人私有
        return selectList(OwnerQuerySupport.<AiAgent>visibleWrapper().eq("status", 1).orderByDesc("create_time"));
    }

    default List<AiAgent> queryByChannel(String channel) {
        return selectList(OwnerQuerySupport.<AiAgent>visibleWrapper().eq("channel", channel).orderByDesc("create_time"));
    }

    default List<AiAgent> queryAll() {
        return selectList(OwnerQuerySupport.<AiAgent>visibleWrapper().orderByDesc("create_time"));
    }

}
