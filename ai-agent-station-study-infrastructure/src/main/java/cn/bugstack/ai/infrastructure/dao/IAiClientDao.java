package cn.bugstack.ai.infrastructure.dao;

import cn.bugstack.ai.infrastructure.dao.po.AiClient;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * AI客户端配置表 DAO
 * @author bugstack虫洞栈
 * @description AI客户端配置表数据访问对象（MyBatis-Plus 迁移版，SQL 由 Wrapper 拼接，无 XML）
 */
@Mapper
public interface IAiClientDao extends BaseMapper<AiClient> {

    default int updateById(AiClient aiClient) {
        UpdateWrapper<AiClient> uw = new UpdateWrapper<>();
        uw.eq("id", aiClient.getId());
        uw.set("client_id", aiClient.getClientId());
        uw.set("client_name", aiClient.getClientName());
        uw.set("description", aiClient.getDescription());
        uw.set("status", aiClient.getStatus());
        uw.set("update_time", aiClient.getUpdateTime());
        return update(null, uw);
    }

    default int updateByClientId(AiClient aiClient) {
        UpdateWrapper<AiClient> uw = new UpdateWrapper<>();
        uw.eq("client_id", aiClient.getClientId());
        uw.set("client_name", aiClient.getClientName());
        uw.set("description", aiClient.getDescription());
        uw.set("status", aiClient.getStatus());
        uw.set("update_time", aiClient.getUpdateTime());
        return update(null, uw);
    }

    default int deleteByClientId(String clientId) {
        return delete(new QueryWrapper<AiClient>().eq("client_id", clientId));
    }

    default AiClient queryById(Long id) {
        return selectById(id);
    }

    default AiClient queryByClientId(String clientId) {
        return selectOne(new QueryWrapper<AiClient>().eq("client_id", clientId));
    }

    default List<AiClient> queryEnabledClients() {
        return selectList(new QueryWrapper<AiClient>().eq("status", 1).orderByDesc("create_time"));
    }

    default List<AiClient> queryByClientName(String clientName) {
        return selectList(new QueryWrapper<AiClient>().like("client_name", clientName).orderByDesc("create_time"));
    }

    default List<AiClient> queryAll() {
        return selectList(new QueryWrapper<AiClient>().orderByDesc("create_time"));
    }

}
