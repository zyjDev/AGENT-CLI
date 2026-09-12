package cn.bugstack.ai.infrastructure.dao;

import cn.bugstack.ai.infrastructure.dao.po.AiClientModel;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 聊天模型配置表 DAO
 * @author bugstack虫洞栈
 * @description 聊天模型配置表数据访问对象（MyBatis-Plus 迁移版，SQL 由 Wrapper 拼接，无 XML）
 */
@Mapper
public interface IAiClientModelDao extends BaseMapper<AiClientModel> {

    /**
     * 根据模型ID更新聊天模型配置
     * <p>
     * 实体驱动更新：只有非 null 字段进入 SET 子句，未传字段保持库中原值；
     * update_time 由 TimeMetaObjectHandler 自动填充。
     * <p>
     * 注意：不要在此接口中声明 default int updateById(...)，否则会覆盖 BaseMapper.updateById 的 SQL 派发。
     */
    default int updateByModelId(AiClientModel aiClientModel) {
        return update(aiClientModel, new UpdateWrapper<AiClientModel>().eq("model_id", aiClientModel.getModelId()));
    }

    default int deleteByModelId(String modelId) {
        return delete(new QueryWrapper<AiClientModel>().eq("model_id", modelId));
    }

    default AiClientModel queryById(Long id) {
        return selectById(id);
    }

    default AiClientModel queryByModelId(String modelId) {
        return selectOne(new QueryWrapper<AiClientModel>().eq("model_id", modelId));
    }

    default List<AiClientModel> queryByApiId(String apiId) {
        return selectList(new QueryWrapper<AiClientModel>().eq("api_id", apiId).orderByDesc("create_time"));
    }

    default List<AiClientModel> queryByModelType(String modelType) {
        return selectList(new QueryWrapper<AiClientModel>().eq("model_type", modelType).orderByDesc("create_time"));
    }

    default List<AiClientModel> queryEnabledModels() {
        return selectList(new QueryWrapper<AiClientModel>().eq("status", 1).orderByDesc("create_time"));
    }

    default List<AiClientModel> queryAll() {
        return selectList(new QueryWrapper<AiClientModel>().orderByDesc("create_time"));
    }

}
