package cn.bugstack.ai.infrastructure.dao;

import cn.bugstack.ai.infrastructure.dao.po.AiAgentDrawConfig;
import cn.bugstack.ai.infrastructure.dao.support.OwnerQuerySupport;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * AI智能体拖拉拽配置主表 DAO
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

    /**
     * 管理端列表分页查询：条件与归属过滤一起下推 SQL，total 由 count 得出。
     *
     * <p><b>归属口径是 usable（系统默认 + 本人），刻意与其它 7 个资源模块的 visible 不同</b> ——
     * 编排画布要能列出并使用平台默认配置，本模块改造前的 queryAll / queryByConfigName 就是 usable，
     * 这里保持一致（属「原样保留」，不是疏漏）。前端据 platformDefault 字段对平台默认行隐藏编辑 / 删除。
     */
    default IPage<AiAgentDrawConfig> queryPage(IPage<AiAgentDrawConfig> page, String configId, String configName, String agentId, Integer status) {
        LambdaQueryWrapper<AiAgentDrawConfig> wrapper = OwnerQuerySupport.usableLambdaWrapper(AiAgentDrawConfig::getOwnerId);
        if (configId != null && !configId.isBlank()) {
            wrapper.eq(AiAgentDrawConfig::getConfigId, configId);
        }
        if (configName != null && !configName.isBlank()) {
            wrapper.like(AiAgentDrawConfig::getConfigName, configName);
        }
        if (agentId != null && !agentId.isBlank()) {
            wrapper.eq(AiAgentDrawConfig::getAgentId, agentId);
        }
        if (status != null) {
            wrapper.eq(AiAgentDrawConfig::getStatus, status);
        }
        return selectPage(page, wrapper.orderByDesc(AiAgentDrawConfig::getCreateTime));
    }

}
