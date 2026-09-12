package cn.bugstack.ai.infrastructure.dao;

import cn.bugstack.ai.infrastructure.dao.po.AiClientConfig;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * AI客户端统一关联配置表 DAO
 * @author bugstack虫洞栈
 * @description AI客户端统一关联配置表数据访问对象（MyBatis-Plus BaseMapper + 自定义方法用 Wrapper 实现，XML 已移除）
 */
@Mapper
public interface IAiClientConfigDao extends BaseMapper<AiClientConfig> {

    /**
     * 根据ID查询AI客户端配置（复用 BaseMapper.selectById）
     */
    default AiClientConfig queryById(Long id) {
        return selectById(id);
    }

    /**
     * 根据源ID更新AI客户端配置
     * <p>
     * 实体驱动更新：只有非 null 字段进入 SET 子句，未传字段保持库中原值；
     * update_time 由 TimeMetaObjectHandler 自动填充。
     * <p>
     * 注意：不要在此接口中声明 default int updateById(...)，否则会覆盖 BaseMapper.updateById 的 SQL 派发。
     */
    default int updateBySourceId(AiClientConfig aiClientConfig) {
        return update(aiClientConfig, new LambdaUpdateWrapper<AiClientConfig>()
                .eq(AiClientConfig::getSourceId, aiClientConfig.getSourceId()));
    }

    /**
     * 根据源ID删除AI客户端配置
     */
    default int deleteBySourceId(String sourceId) {
        return delete(new LambdaQueryWrapper<AiClientConfig>()
                .eq(AiClientConfig::getSourceId, sourceId));
    }

    /**
     * 根据源ID查询AI客户端配置
     */
    default List<AiClientConfig> queryBySourceId(String sourceId) {
        return selectList(new LambdaQueryWrapper<AiClientConfig>()
                .eq(AiClientConfig::getSourceId, sourceId));
    }

    /**
     * 根据目标ID查询AI客户端配置
     */
    default List<AiClientConfig> queryByTargetId(String targetId) {
        return selectList(new LambdaQueryWrapper<AiClientConfig>()
                .eq(AiClientConfig::getTargetId, targetId));
    }

    /**
     * 根据源类型和源ID查询AI客户端配置
     */
    default List<AiClientConfig> queryBySourceTypeAndId(String sourceType, String sourceId) {
        return selectList(new LambdaQueryWrapper<AiClientConfig>()
                .eq(AiClientConfig::getSourceType, sourceType)
                .eq(AiClientConfig::getSourceId, sourceId));
    }

    /**
     * 根据目标类型和目标ID查询AI客户端配置
     */
    default List<AiClientConfig> queryByTargetTypeAndId(String targetType, String targetId) {
        return selectList(new LambdaQueryWrapper<AiClientConfig>()
                .eq(AiClientConfig::getTargetType, targetType)
                .eq(AiClientConfig::getTargetId, targetId));
    }

    /**
     * 根据源类型、源ID、目标类型、目标ID查询AI客户端配置
     */
    default List<AiClientConfig> queryByConditions(String sourceType, String sourceId, String targetType, String targetId) {
        return selectList(new LambdaQueryWrapper<AiClientConfig>()
                .eq(AiClientConfig::getSourceType, sourceType)
                .eq(AiClientConfig::getSourceId, sourceId)
                .eq(AiClientConfig::getTargetType, targetType)
                .eq(AiClientConfig::getTargetId, targetId));
    }

    /**
     * 查询启用状态的AI客户端配置
     */
    default List<AiClientConfig> queryEnabledConfigs() {
        return selectList(new LambdaQueryWrapper<AiClientConfig>()
                .eq(AiClientConfig::getStatus, 1)
                .orderByDesc(AiClientConfig::getCreateTime));
    }

    /**
     * 查询所有AI客户端配置
     */
    default List<AiClientConfig> queryAll() {
        return selectList(new LambdaQueryWrapper<AiClientConfig>()
                .orderByDesc(AiClientConfig::getCreateTime));
    }

}
