package cn.bugstack.ai.infrastructure.dao;

import cn.bugstack.ai.infrastructure.dao.po.AiClientRagOrder;
import cn.bugstack.ai.infrastructure.dao.support.OwnerQuerySupport;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 知识库配置表 DAO
 * @author bugstack.cn
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

    default List<AiClientRagOrder> queryEnabledRagOrders() {
        return selectList(OwnerQuerySupport.visibleLambdaWrapper(AiClientRagOrder::getOwnerId)
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
