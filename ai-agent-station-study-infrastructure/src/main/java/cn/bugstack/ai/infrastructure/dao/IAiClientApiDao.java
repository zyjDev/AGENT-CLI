package cn.bugstack.ai.infrastructure.dao;

import cn.bugstack.ai.infrastructure.dao.po.AiClientApi;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * AI客户端API配置表 DAO
 * @author bugstack虫洞栈
 * @description AI客户端API配置表数据访问对象（MyBatis-Plus 迁移版，SQL 由 Wrapper 拼接，无 XML）
 */
@Mapper
public interface IAiClientApiDao extends BaseMapper<AiClientApi> {

    default int updateById(AiClientApi aiClientApi) {
        UpdateWrapper<AiClientApi> uw = new UpdateWrapper<>();
        uw.eq("id", aiClientApi.getId());
        uw.set("api_id", aiClientApi.getApiId());
        uw.set("base_url", aiClientApi.getBaseUrl());
        uw.set("api_key", aiClientApi.getApiKey());
        uw.set("completions_path", aiClientApi.getCompletionsPath());
        uw.set("embeddings_path", aiClientApi.getEmbeddingsPath());
        uw.set("status", aiClientApi.getStatus());
        uw.set("update_time", aiClientApi.getUpdateTime());
        return update(null, uw);
    }

    default int updateByApiId(AiClientApi aiClientApi) {
        UpdateWrapper<AiClientApi> uw = new UpdateWrapper<>();
        uw.eq("api_id", aiClientApi.getApiId());
        uw.set("base_url", aiClientApi.getBaseUrl());
        uw.set("api_key", aiClientApi.getApiKey());
        uw.set("completions_path", aiClientApi.getCompletionsPath());
        uw.set("embeddings_path", aiClientApi.getEmbeddingsPath());
        uw.set("status", aiClientApi.getStatus());
        uw.set("update_time", aiClientApi.getUpdateTime());
        return update(null, uw);
    }

    default int deleteByApiId(String apiId) {
        return delete(new QueryWrapper<AiClientApi>().eq("api_id", apiId));
    }

    default AiClientApi queryById(Long id) {
        return selectById(id);
    }

    default AiClientApi queryByApiId(String apiId) {
        return selectOne(new QueryWrapper<AiClientApi>().eq("api_id", apiId));
    }

    default List<AiClientApi> queryEnabledApis() {
        return selectList(new QueryWrapper<AiClientApi>().eq("status", 1).orderByDesc("create_time"));
    }

    default List<AiClientApi> queryAll() {
        return selectList(new QueryWrapper<AiClientApi>().orderByDesc("create_time"));
    }

}
