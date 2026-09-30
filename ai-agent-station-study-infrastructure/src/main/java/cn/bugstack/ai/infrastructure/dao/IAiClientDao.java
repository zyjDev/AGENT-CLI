package cn.bugstack.ai.infrastructure.dao;

import cn.bugstack.ai.infrastructure.dao.po.AiClient;
import cn.bugstack.ai.infrastructure.dao.support.OwnerQuerySupport;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * AI客户端配置表 DAO
 * @description AI客户端配置表数据访问对象（MyBatis-Plus 迁移版，SQL 由 Wrapper 拼接，无 XML）
 */
@Mapper
public interface IAiClientDao extends BaseMapper<AiClient> {

    /**
     * 根据客户端ID更新AI客户端配置
     * <p>
     * 实体驱动更新：只有非 null 字段进入 SET 子句，未传字段保持库中原值；
     * update_time 由 TimeMetaObjectHandler 自动填充。
     * <p>
     * 注意：不要在此接口中声明 default int updateById(...)，否则会覆盖 BaseMapper.updateById 的 SQL 派发，
     * 导致内置「只更新非 null 字段」语义失效并把未传字段写成 NULL。按 id 更新请直接使用 BaseMapper.updateById。
     */
    default int updateByClientId(AiClient aiClient) {
        return update(aiClient, new UpdateWrapper<AiClient>().eq("client_id", aiClient.getClientId()));
    }

    default int deleteByClientId(String clientId) {
        return delete(new QueryWrapper<AiClient>().eq("client_id", clientId));
    }

    default AiClient queryById(Long id) {
        AiClient po = selectById(id);
        if (po != null && !OwnerQuerySupport.visibleToCurrentUser(po.getOwnerId())) {
            return null;
        }
        return po;
    }

    default AiClient queryByClientId(String clientId) {
        return selectOne(new QueryWrapper<AiClient>().eq("client_id", clientId));
    }

    default List<AiClient> queryEnabledClients() {
        return selectList(OwnerQuerySupport.<AiClient>visibleWrapper().eq("status", 1).orderByDesc("create_time"));
    }

    default List<AiClient> queryByClientName(String clientName) {
        return selectList(OwnerQuerySupport.<AiClient>visibleWrapper().like("client_name", clientName).orderByDesc("create_time"));
    }

    default List<AiClient> queryAll() {
        return selectList(OwnerQuerySupport.<AiClient>visibleWrapper().orderByDesc("create_time"));
    }

    /**
     * 管理端列表分页查询：条件与归属过滤一起下推 SQL，total 由 count 得出。
     * 归属口径同 {@link #queryAll()}（visible）；排序保持 create_time 降序（分页必须稳定排序）。
     */
    default IPage<AiClient> queryPage(IPage<AiClient> page, String clientId, String clientName, Integer status) {
        LambdaQueryWrapper<AiClient> wrapper = OwnerQuerySupport.visibleLambdaWrapper(AiClient::getOwnerId);
        if (clientId != null && !clientId.isBlank()) {
            wrapper.eq(AiClient::getClientId, clientId);
        }
        if (clientName != null && !clientName.isBlank()) {
            wrapper.like(AiClient::getClientName, clientName);
        }
        if (status != null) {
            wrapper.eq(AiClient::getStatus, status);
        }
        return selectPage(page, wrapper.orderByDesc(AiClient::getCreateTime));
    }

}
