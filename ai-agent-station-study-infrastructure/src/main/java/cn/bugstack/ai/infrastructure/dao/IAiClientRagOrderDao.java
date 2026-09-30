package cn.bugstack.ai.infrastructure.dao;

import cn.bugstack.ai.infrastructure.dao.po.AiClientRagOrder;
import cn.bugstack.ai.infrastructure.dao.support.OwnerQuerySupport;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 知识库配置表 DAO
 * @description 知识库配置表数据访问对象（MyBatis-Plus BaseMapper + 自定义方法用 Wrapper 实现，XML 已移除）
 */
@Mapper
public interface IAiClientRagOrderDao extends BaseMapper<AiClientRagOrder> {

    /**
     * 查询指定时间之后更新的知识库配置
     * @param updateTime 更新时间
     * @return 知识库配置列表
     */
    default List<AiClientRagOrder> queryByUpdateTimeAfter(LocalDateTime updateTime) {
        return selectList(OwnerQuerySupport.visibleLambdaWrapper(AiClientRagOrder::getOwnerId)
                .gt(AiClientRagOrder::getUpdateTime, updateTime)
                .orderByDesc(AiClientRagOrder::getUpdateTime));
    }

    default AiClientRagOrder queryById(Long id) {
        AiClientRagOrder po = selectById(id);
        if (po != null && !OwnerQuerySupport.visibleToCurrentUser(po.getOwnerId())) {
            return null;
        }
        return po;
    }

    default AiClientRagOrder queryByRagId(String ragId) {
        return selectOne(new LambdaQueryWrapper<AiClientRagOrder>()
                .eq(AiClientRagOrder::getRagId, ragId)
                .last("LIMIT 1"));
    }

    default List<AiClientRagOrder> queryAll() {
        return selectList(OwnerQuerySupport.visibleLambdaWrapper(AiClientRagOrder::getOwnerId)
                .orderByDesc(AiClientRagOrder::getUpdateTime));
    }

    /**
     * 管理端列表分页查询：条件与归属过滤一起下推 SQL，total 由 count 得出。
     * 归属口径与排序同 {@link #queryAll()}（visible；update_time 降序）。
     * 三个条件沿用改造前的<b>模糊匹配</b>语义（原内存 contains），勿改成精确匹配。
     */
    default IPage<AiClientRagOrder> queryPage(IPage<AiClientRagOrder> page, String ragId, String ragName, String knowledgeTag, Integer status) {
        LambdaQueryWrapper<AiClientRagOrder> wrapper = OwnerQuerySupport.visibleLambdaWrapper(AiClientRagOrder::getOwnerId);
        if (ragId != null && !ragId.isBlank()) {
            wrapper.like(AiClientRagOrder::getRagId, ragId);
        }
        if (ragName != null && !ragName.isBlank()) {
            wrapper.like(AiClientRagOrder::getRagName, ragName);
        }
        if (knowledgeTag != null && !knowledgeTag.isBlank()) {
            wrapper.like(AiClientRagOrder::getKnowledgeTag, knowledgeTag);
        }
        if (status != null) {
            wrapper.eq(AiClientRagOrder::getStatus, status);
        }
        return selectPage(page, wrapper.orderByDesc(AiClientRagOrder::getUpdateTime));
    }

    default List<AiClientRagOrder> queryEnabledRagOrders() {
        // 「可用」口径：系统默认知识库 + 本人私有（用户端知识库下拉、对话检索都走这里）
        return selectList(OwnerQuerySupport.usableLambdaWrapper(AiClientRagOrder::getOwnerId)
                .eq(AiClientRagOrder::getStatus, 1)
                .orderByDesc(AiClientRagOrder::getUpdateTime));
    }

    default List<AiClientRagOrder> queryByKnowledgeTag(String knowledgeTag) {
        return selectList(OwnerQuerySupport.visibleLambdaWrapper(AiClientRagOrder::getOwnerId)
                .eq(AiClientRagOrder::getKnowledgeTag, knowledgeTag)
                .orderByDesc(AiClientRagOrder::getUpdateTime));
    }

    /**
     * 根据知识库ID更新知识库配置
     * <p>
     * 实体驱动更新：只有非 null 字段进入 SET 子句，未传字段保持库中原值；
     * update_time 由 TimeMetaObjectHandler 自动填充。
     */
    default int updateByRagId(AiClientRagOrder aiClientRagOrder) {
        return update(aiClientRagOrder, new LambdaUpdateWrapper<AiClientRagOrder>()
                .eq(AiClientRagOrder::getRagId, aiClientRagOrder.getRagId()));
    }

    default int deleteByRagId(String ragId) {
        return delete(new LambdaQueryWrapper<AiClientRagOrder>()
                .eq(AiClientRagOrder::getRagId, ragId));
    }

}
