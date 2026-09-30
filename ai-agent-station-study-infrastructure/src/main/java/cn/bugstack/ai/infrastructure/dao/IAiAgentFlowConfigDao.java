package cn.bugstack.ai.infrastructure.dao;

import cn.bugstack.ai.infrastructure.dao.po.AiAgentFlowConfig;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 智能体-客户端关联表 DAO
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

    /**
     * 只删「某个用户在该智能体上的绑定」（owner_id = ownerId 的那几条），公共链路一行不动。
     * <p>
     * ⚠️ 解绑、重新绑定都必须用它，绝不能用 {@link #deleteByAgentId(String)} ——
     * 后者会把系统默认链路（owner 为空）和其它用户的绑定一起删掉。
     */
    default int deleteByAgentIdAndOwner(String agentId, String ownerId) {
        if (ownerId == null || ownerId.isBlank()) {
            return 0;
        }
        return delete(new QueryWrapper<AiAgentFlowConfig>().eq("agent_id", agentId).eq("owner_id", ownerId));
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
     * 根据智能体ID + 归属查询【有效】流程配置（**只查属于 ownerId 的那份**），按 sequence 升序。
     * <p>
     * 「绑定关系」本身就存在本表：{@code agent_id + client_id + owner_id} ——
     * 普通用户给自己的 Key 绑定某个智能体后，就会多出一条归他本人的流程配置，
     * 装配/对话时优先用它（见 {@link #queryEnabledByAgentIdPreferOwner(String, String)}）。
     * 返回空 = 这个用户没为该智能体绑定过自己的 Key。
     */
    default List<AiAgentFlowConfig> queryEnabledByAgentIdAndOwner(String agentId, String ownerId) {
        if (ownerId == null || ownerId.isBlank()) {
            return List.of();
        }
        return selectList(new QueryWrapper<AiAgentFlowConfig>().eq("agent_id", agentId)
                .eq("status", 1).eq("owner_id", ownerId).orderByAsc("sequence"));
    }

    /**
     * 运行期取流程配置：<b>优先用户自己绑定的那份，没有才回落到系统默认（owner_id 为空）</b>。
     * <p>
     * 为什么必须有这个优先级：平台默认智能体的链路指向管理员的 base_url / api_key，
     * 普通用户绑定自己的 Key 之后必须让<b>他的</b>链路生效，否则绑了等于白绑。
     * 没有用户上下文（定时任务、启动装配）或该用户没绑过时，回落的只是系统默认，
     * 不会串到别的用户的私有链路上（旧实现按 agent_id 取全部行，会把别人的私有配置也取到）。
     */
    default List<AiAgentFlowConfig> queryEnabledByAgentIdPreferOwner(String agentId, String ownerId) {
        List<AiAgentFlowConfig> ownConfigs = queryEnabledByAgentIdAndOwner(agentId, ownerId);
        if (!ownConfigs.isEmpty()) {
            return ownConfigs;
        }
        return selectList(new QueryWrapper<AiAgentFlowConfig>().eq("agent_id", agentId)
                .eq("status", 1).isNull("owner_id").orderByAsc("sequence"));
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
