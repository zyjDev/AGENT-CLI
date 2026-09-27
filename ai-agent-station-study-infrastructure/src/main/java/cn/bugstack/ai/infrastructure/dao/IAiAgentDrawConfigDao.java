package cn.bugstack.ai.infrastructure.dao;

import cn.bugstack.ai.infrastructure.dao.po.AiAgentDrawConfig;
import cn.bugstack.ai.infrastructure.dao.support.OwnerQuerySupport;
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
        AiAgentDrawConfig po = selectById(id);
        if (po != null && !OwnerQuerySupport.visibleToCurrentUser(po.getOwnerId())) {
            return null;
        }
        return po;
    }

    default AiAgentDrawConfig queryByConfigId(String configId) {
        return selectOne(new QueryWrapper<AiAgentDrawConfig>().eq("config_id", configId));
    }

    default AiAgentDrawConfig queryByAgentId(String agentId) {
        return selectOne(new QueryWrapper<AiAgentDrawConfig>().eq("agent_id", agentId));
    }

    default List<AiAgentDrawConfig> queryEnabledConfigs() {
        // 归属过滤：别人拖拉拽搭的编排配置不该出现在我的列表里
        return selectList(OwnerQuerySupport.<AiAgentDrawConfig>visibleWrapper().eq("status", 1).orderByDesc("create_time"));
    }

    default List<AiAgentDrawConfig> queryByConfigName(String configName) {
        return selectList(OwnerQuerySupport.<AiAgentDrawConfig>usableWrapper().like("config_name", configName).orderByDesc("create_time"));
    }

    /**
     * 编排配置列表。
     * <p>
     * ⚠️ 这里用「可用」口径（系统默认 + 本人）而不是 visibleWrapper：产品要求普通用户
     * **能看到默认智能体的信息（只读）**，只是不能改 —— 列表藏起来他连"平台给了什么"都不知道。
     * 写入仍然要靠 {@code OwnerGuard.writable}（公共资源只有管理员能改），所以放开可见不等于放开可改。
     */
    default List<AiAgentDrawConfig> queryAll() {
        return selectList(OwnerQuerySupport.<AiAgentDrawConfig>usableWrapper().orderByDesc("create_time"));
    }

}
