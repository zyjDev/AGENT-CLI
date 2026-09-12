package cn.bugstack.ai.infrastructure.dao;

import cn.bugstack.ai.infrastructure.dao.po.AiAgentFlowConfig;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 智能体-客户端关联表 DAO
 * @author bugstack虫洞栈
 * @description 智能体-客户端关联表数据访问对象（MyBatis-Plus 迁移版，SQL 由 Wrapper 拼接，无 XML）
 * <p>
 * 注意：本接口不要声明 default int updateById(...)，否则会覆盖 BaseMapper.updateById 的 SQL 派发，
 * 导致内置「只更新非 null 字段」语义失效并把未传字段写成 NULL。按 id 更新请直接使用 BaseMapper.updateById。
 */
@Mapper
public interface IAiAgentFlowConfigDao extends BaseMapper<AiAgentFlowConfig> {

    default int deleteByAgentId(String agentId) {
        return delete(new QueryWrapper<AiAgentFlowConfig>().eq("agent_id", agentId));
    }

    default AiAgentFlowConfig queryById(String id) {
        return selectById(id);
    }

    /**
     * 根据智能体ID查询【全部】流程配置（含 status=0 的已禁用配置），按 sequence 升序
     * <p>
     * 注意：执行链路（Flow/Auto/Fixed 策略、Armory 装配）请改用 {@link #queryEnabledByAgentId(String)}，
     * 否则被禁用的流程节点（如 agent_id='1' 的 2101~2103，step_prompt='暂时不需要配置'）也会被执行。
     */
    default List<AiAgentFlowConfig> queryByAgentId(String agentId) {
        return selectList(new QueryWrapper<AiAgentFlowConfig>().eq("agent_id", agentId).orderByAsc("sequence"));
    }

    /**
     * 根据智能体ID查询【有效】流程配置（status=1），按 sequence 升序
     * <p>
     * 建表 SQL 中 status 语义为「0无效，1有效」。执行链路必须使用本方法，避免把已禁用的占位节点一并装配执行。
     */
    default List<AiAgentFlowConfig> queryEnabledByAgentId(String agentId) {
        return selectList(new QueryWrapper<AiAgentFlowConfig>().eq("agent_id", agentId)
                .eq("status", 1).orderByAsc("sequence"));
    }

    /**
     * 根据客户端ID查询流程配置，按 sequence 升序
     * <p>
     * 注意：本方法不过滤 status，会返回已禁用的配置。
     */
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
