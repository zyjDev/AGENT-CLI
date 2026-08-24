package cn.bugstack.ai.infrastructure.dao;

import cn.bugstack.ai.infrastructure.dao.po.AiRagUpdateTask;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 知识库更新任务表 DAO
 * @author bugstack.cn
 * @description 知识库更新任务表数据访问对象
 */
@Mapper
public interface IAiRagUpdateTaskDao extends BaseMapper<AiRagUpdateTask> {

}
