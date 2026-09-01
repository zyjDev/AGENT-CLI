package cn.bugstack.ai.infrastructure.dao;

import cn.bugstack.ai.infrastructure.dao.po.AiAgentFlowConfig;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 智能体-客户端关联表 DAO
 * @author bugstack虫洞栈
 * @description 智能体-客户端关联表数据访问对象（MyBatis-Plus 迁移版，SQL 由 Wrapper 拼接，无 XML）
 */
@Mapper
public interface IAiAgentFlowConfigDao extends BaseMapper<AiAgentFlowConfig> {

    default int updateById(AiAgentFlowConfig aiAgentFlowConfig) {
        UpdateWrapper<AiAgentFlowConfig> uw = new UpdateWrapper<>();
        uw.eq("id", aiAgentFlowConfig.getId());
        uw.set("agent_id", aiAgentFlowConfig.getAgentId());
        uw.set("client_id", aiAgentFlowConfig.getClientId());
        uw.set("sequence", aiAgentFlowConfig.getSequence());
        uw.set("step_prompt", aiAgentFlowConfig.getStepPrompt());
        return update(null, uw);
    }

    default int deleteByAgentId(String agentId) {
        return delete(new QueryWrapper<AiAgentFlowConfig>().eq("agent_id", agentId));
    }

    default AiAgentFlowConfig queryById(String id) {
        return selectById(id);
    }

    default List<AiAgentFlowConfig> queryByAgentId(String agentId) {
        return selectList(new QueryWrapper<AiAgentFlowConfig>().eq("agent_id", agentId).orderByAsc("sequence"));
    }

    default List<AiAgentFlowConfig> queryByClientId(String clientId) {
        return selectList(new QueryWrapper<AiAgentFlowConfig>().eq("client_id", clientId).orderByAsc("sequence"));
    }

    default AiAgentFlowConfig queryByAgentIdAndClientId(String agentId, String clientId) {
        return selectOne(new QueryWrapper<AiAgentFlowConfig>().eq("agent_id", agentId).eq("client_id", clientId));
    }

    default List<AiAgentFlowConfig> queryAll() {
        return selectList(new QueryWrapper<AiAgentFlowConfig>().orderByAsc("agent_id").orderByAsc("sequence"));
    }

}
