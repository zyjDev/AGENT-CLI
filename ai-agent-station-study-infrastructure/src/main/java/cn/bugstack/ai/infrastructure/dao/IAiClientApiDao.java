package cn.bugstack.ai.infrastructure.dao;

import cn.bugstack.ai.infrastructure.dao.po.AiClientApi;
import cn.bugstack.ai.infrastructure.dao.support.OwnerQuerySupport;
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

    /**
     * 根据API ID更新AI客户端API配置
     * <p>
     * 实体驱动更新：只有非 null 字段进入 SET 子句，未传字段保持库中原值；
     * update_time 由 TimeMetaObjectHandler 自动填充。
     * <p>
     * 注意：不要在此接口中声明 default int updateById(...)，否则会覆盖 BaseMapper.updateById 的 SQL 派发。
     */
    default int updateByApiId(AiClientApi aiClientApi) {
        return update(aiClientApi, new UpdateWrapper<AiClientApi>().eq("api_id", aiClientApi.getApiId()));
    }

    default int deleteByApiId(String apiId) {
        return delete(new QueryWrapper<AiClientApi>().eq("api_id", apiId));
    }

    default AiClientApi queryById(Long id) {
        AiClientApi po = selectById(id);
        // apiKey 属敏感凭据：按主键读也必须过归属校验
        if (po != null && !OwnerQuerySupport.visibleToCurrentUser(po.getOwnerId())) {
            return null;
        }
        return po;
    }

    default AiClientApi queryByApiId(String apiId) {
        return selectOne(new QueryWrapper<AiClientApi>().eq("api_id", apiId));
    }

    default List<AiClientApi> queryEnabledApis() {
        // apiKey 属敏感凭据：列表只出「公共 + 本人」，别人的通道不可见
        return selectList(OwnerQuerySupport.<AiClientApi>visibleWrapper().eq("status", 1).orderByDesc("create_time"));
    }

    default List<AiClientApi> queryAll() {
        return selectList(OwnerQuerySupport.<AiClientApi>visibleWrapper().orderByDesc("create_time"));
    }

}
