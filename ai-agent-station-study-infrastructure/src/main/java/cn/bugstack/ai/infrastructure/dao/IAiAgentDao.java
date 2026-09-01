package cn.bugstack.ai.infrastructure.dao;

import cn.bugstack.ai.infrastructure.dao.po.AiAgent;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.List;

/**
 * AI智能体配置表 DAO
 * @description AI智能体配置表数据访问对象（MyBatis-Plus 迁移版，SQL 由 Wrapper 拼接，无 XML）
 */
@Mapper
public interface IAiAgentDao extends BaseMapper<AiAgent> {

    default int updateById(AiAgent aiAgent) {
        UpdateWrapper<AiAgent> uw = new UpdateWrapper<>();
        uw.eq("id", aiAgent.getId());
        uw.set("agent_id", aiAgent.getAgentId());
        uw.set("agent_name", aiAgent.getAgentName());
        uw.set("description", aiAgent.getDescription());
        uw.set("channel", aiAgent.getChannel());
        uw.set("strategy", aiAgent.getStrategy());
        uw.set("status", aiAgent.getStatus());
        uw.set("update_time", LocalDateTime.now());
        return update(null, uw);
    }

    default int updateByAgentId(AiAgent aiAgent) {
        UpdateWrapper<AiAgent> uw = new UpdateWrapper<>();
        uw.eq("agent_id", aiAgent.getAgentId());
        uw.set("agent_name", aiAgent.getAgentName());
        uw.set("description", aiAgent.getDescription());
        uw.set("channel", aiAgent.getChannel());
        uw.set("strategy", aiAgent.getStrategy());
        uw.set("status", aiAgent.getStatus());
        uw.set("update_time", LocalDateTime.now());
        return update(null, uw);
    }

    default int deleteByAgentId(String agentId) {
        return delete(new QueryWrapper<AiAgent>().eq("agent_id", agentId));
    }

    default AiAgent queryById(Long id) {
        return selectById(id);
    }

    default AiAgent queryByAgentId(String agentId) {
        return selectOne(new QueryWrapper<AiAgent>().eq("agent_id", agentId));
    }

    default List<AiAgent> queryEnabledAgents() {
        return selectList(new QueryWrapper<AiAgent>().eq("status", 1).orderByDesc("create_time"));
    }

    default List<AiAgent> queryByChannel(String channel) {
        return selectList(new QueryWrapper<AiAgent>().eq("channel", channel).orderByDesc("create_time"));
    }

    default List<AiAgent> queryAll() {
        return selectList(new QueryWrapper<AiAgent>().orderByDesc("create_time"));
    }

}
