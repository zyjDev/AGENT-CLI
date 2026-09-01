package cn.bugstack.ai.infrastructure.dao;

import cn.bugstack.ai.infrastructure.dao.po.AiClientRagOrder;
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
        return selectList(new LambdaQueryWrapper<AiClientRagOrder>()
                .gt(AiClientRagOrder::getUpdateTime, updateTime)
                .orderByDesc(AiClientRagOrder::getUpdateTime));
    }

    default AiClientRagOrder queryById(Long id) {
        return selectById(id);
    }

    default AiClientRagOrder queryByRagId(String ragId) {
        return selectOne(new LambdaQueryWrapper<AiClientRagOrder>()
                .eq(AiClientRagOrder::getRagId, ragId));
    }

    default List<AiClientRagOrder> queryAll() {
        return selectList(new LambdaQueryWrapper<AiClientRagOrder>()
                .orderByDesc(AiClientRagOrder::getUpdateTime));
    }

    default List<AiClientRagOrder> queryEnabledRagOrders() {
        return selectList(new LambdaQueryWrapper<AiClientRagOrder>()
                .eq(AiClientRagOrder::getStatus, 1)
                .orderByDesc(AiClientRagOrder::getUpdateTime));
    }

    default List<AiClientRagOrder> queryByKnowledgeTag(String knowledgeTag) {
        return selectList(new LambdaQueryWrapper<AiClientRagOrder>()
                .eq(AiClientRagOrder::getKnowledgeTag, knowledgeTag)
                .orderByDesc(AiClientRagOrder::getUpdateTime));
    }

    default int updateByRagId(AiClientRagOrder aiClientRagOrder) {
        return update(null, new LambdaUpdateWrapper<AiClientRagOrder>()
                .eq(AiClientRagOrder::getRagId, aiClientRagOrder.getRagId())
                .set(AiClientRagOrder::getRagName, aiClientRagOrder.getRagName())
                .set(AiClientRagOrder::getKnowledgeTag, aiClientRagOrder.getKnowledgeTag())
                .set(AiClientRagOrder::getStatus, aiClientRagOrder.getStatus())
                .set(AiClientRagOrder::getVersion, aiClientRagOrder.getVersion())
                .set(AiClientRagOrder::getFileHash, aiClientRagOrder.getFileHash())
                .set(AiClientRagOrder::getUpdateReason, aiClientRagOrder.getUpdateReason())
                .set(AiClientRagOrder::getUpdateTime, LocalDateTime.now()));
    }

    default int deleteByRagId(String ragId) {
        return delete(new LambdaQueryWrapper<AiClientRagOrder>()
                .eq(AiClientRagOrder::getRagId, ragId));
    }

}
