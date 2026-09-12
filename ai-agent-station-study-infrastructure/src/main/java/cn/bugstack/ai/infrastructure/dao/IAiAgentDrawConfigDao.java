package cn.bugstack.ai.infrastructure.dao;

import cn.bugstack.ai.infrastructure.dao.po.AiAgentDrawConfig;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * AI智能体拖拉拽配置主表 DAO
 * @author bugstack虫洞栈
 * @description AI智能体拖拉拽配置主表数据访问对象（MyBatis-Plus 迁移版，SQL 由 Wrapper 拼接，无 XML）
 */
@Mapper
public interface IAiAgentDrawConfigDao extends BaseMapper<AiAgentDrawConfig> {

    /**
     * 根据配置ID更新拖拉拽配置
     * <p>
     * 实体驱动更新：只有非 null 字段进入 SET 子句，未传字段保持库中原值；
     * update_time 由 TimeMetaObjectHandler 自动填充。
     * <p>
     * 注意：不要在此接口中声明 default int updateById(...)，否则会覆盖 BaseMapper.updateById 的 SQL 派发。
     */
    default int updateByConfigId(AiAgentDrawConfig aiAgentDrawConfig) {
        return update(aiAgentDrawConfig, new UpdateWrapper<AiAgentDrawConfig>().eq("config_id", aiAgentDrawConfig.getConfigId()));
    }

    default int deleteByConfigId(String configId) {
        return delete(new QueryWrapper<AiAgentDrawConfig>().eq("config_id", configId));
    }

    default AiAgentDrawConfig queryById(Long id) {
        return selectById(id);
    }

    default AiAgentDrawConfig queryByConfigId(String configId) {
        return selectOne(new QueryWrapper<AiAgentDrawConfig>().eq("config_id", configId));
    }

    default AiAgentDrawConfig queryByAgentId(String agentId) {
        return selectOne(new QueryWrapper<AiAgentDrawConfig>().eq("agent_id", agentId));
    }

    default List<AiAgentDrawConfig> queryEnabledConfigs() {
        return selectList(new QueryWrapper<AiAgentDrawConfig>().eq("status", 1).orderByDesc("create_time"));
    }

    default List<AiAgentDrawConfig> queryByConfigName(String configName) {
        return selectList(new QueryWrapper<AiAgentDrawConfig>().like("config_name", configName).orderByDesc("create_time"));
    }

    default List<AiAgentDrawConfig> queryAll() {
        return selectList(new QueryWrapper<AiAgentDrawConfig>().orderByDesc("create_time"));
    }

}
