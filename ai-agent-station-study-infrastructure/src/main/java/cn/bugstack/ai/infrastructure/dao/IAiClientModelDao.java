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

    default int updateById(AiClientModel aiClientModel) {
        UpdateWrapper<AiClientModel> uw = new UpdateWrapper<>();
        uw.eq("id", aiClientModel.getId());
        uw.set("model_id", aiClientModel.getModelId());
        uw.set("api_id", aiClientModel.getApiId());
        uw.set("model_name", aiClientModel.getModelName());
        uw.set("model_type", aiClientModel.getModelType());
        uw.set("model_usage", aiClientModel.getModelUsage());
        uw.set("status", aiClientModel.getStatus());
        uw.set("update_time", aiClientModel.getUpdateTime());
        return update(null, uw);
    }

    default int updateByModelId(AiClientModel aiClientModel) {
        UpdateWrapper<AiClientModel> uw = new UpdateWrapper<>();
        uw.eq("model_id", aiClientModel.getModelId());
        uw.set("api_id", aiClientModel.getApiId());
        uw.set("model_name", aiClientModel.getModelName());
        uw.set("model_type", aiClientModel.getModelType());
        uw.set("model_usage", aiClientModel.getModelUsage());
        uw.set("status", aiClientModel.getStatus());
        uw.set("update_time", aiClientModel.getUpdateTime());
        return update(null, uw);
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
