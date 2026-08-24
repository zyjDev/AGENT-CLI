package cn.bugstack.ai.infrastructure.dao;

import cn.bugstack.ai.infrastructure.dao.po.AiClientRagOrder;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 知识库配置表 DAO
 * @author bugstack.cn
 * @description 知识库配置表数据访问对象
 */
@Mapper
public interface IAiClientRagOrderDao extends BaseMapper<AiClientRagOrder> {

    /**
     * 查询指定时间之后更新的知识库配置
     * @param updateTime 更新时间
     * @return 知识库配置列表
     */
    List<AiClientRagOrder> queryByUpdateTimeAfter(LocalDateTime updateTime);

    AiClientRagOrder queryById(Long id);

    AiClientRagOrder queryByRagId(String ragId);

    List<AiClientRagOrder> queryAll();

    List<AiClientRagOrder> queryEnabledRagOrders();

    List<AiClientRagOrder> queryByKnowledgeTag(String knowledgeTag);

    int updateByRagId(AiClientRagOrder aiClientRagOrder);

    int deleteByRagId(String ragId);

}
