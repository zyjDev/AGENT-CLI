package cn.bugstack.ai.infrastructure.dao;

import cn.bugstack.ai.infrastructure.dao.po.AiRagVersionHistory;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 知识库版本历史表 DAO
 * @author bugstack.cn
 * @description 知识库版本历史表数据访问对象
 */
@Mapper
public interface IAiRagVersionHistoryDao extends BaseMapper<AiRagVersionHistory> {

}
