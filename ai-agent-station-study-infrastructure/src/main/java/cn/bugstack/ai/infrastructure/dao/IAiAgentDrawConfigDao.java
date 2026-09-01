package cn.bugstack.ai.infrastructure.dao;

import cn.bugstack.ai.infrastructure.dao.po.AiAgentDrawConfig;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.List;

/**
 * AI智能体拖拉拽配置主表 DAO
 * @author bugstack虫洞栈
 * @description AI智能体拖拉拽配置主表数据访问对象（MyBatis-Plus 迁移版，SQL 由 Wrapper 拼接，无 XML）
 */
@Mapper
public interface IAiAgentDrawConfigDao extends BaseMapper<AiAgentDrawConfig> {

    default int updateById(AiAgentDrawConfig aiAgentDrawConfig) {
        UpdateWrapper<AiAgentDrawConfig> uw = new UpdateWrapper<>();
        uw.eq("id", aiAgentDrawConfig.getId());
        uw.set("config_id", aiAgentDrawConfig.getConfigId());
        uw.set("config_name", aiAgentDrawConfig.getConfigName());
        uw.set("description", aiAgentDrawConfig.getDescription());
        uw.set("agent_id", aiAgentDrawConfig.getAgentId());
        uw.set("config_data", aiAgentDrawConfig.getConfigData());
        uw.set("version", aiAgentDrawConfig.getVersion());
        uw.set("status", aiAgentDrawConfig.getStatus());
        uw.set("create_by", aiAgentDrawConfig.getCreateBy());
        uw.set("update_by", aiAgentDrawConfig.getUpdateBy());
        uw.set("update_time", LocalDateTime.now());
        return update(null, uw);
    }

    default int updateByConfigId(AiAgentDrawConfig aiAgentDrawConfig) {
        UpdateWrapper<AiAgentDrawConfig> uw = new UpdateWrapper<>();
        uw.eq("config_id", aiAgentDrawConfig.getConfigId());
        uw.set("config_name", aiAgentDrawConfig.getConfigName());
        uw.set("description", aiAgentDrawConfig.getDescription());
        uw.set("agent_id", aiAgentDrawConfig.getAgentId());
        uw.set("config_data", aiAgentDrawConfig.getConfigData());
        uw.set("version", aiAgentDrawConfig.getVersion());
        uw.set("status", aiAgentDrawConfig.getStatus());
        uw.set("update_by", aiAgentDrawConfig.getUpdateBy());
        uw.set("update_time", LocalDateTime.now());
        return update(null, uw);
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
