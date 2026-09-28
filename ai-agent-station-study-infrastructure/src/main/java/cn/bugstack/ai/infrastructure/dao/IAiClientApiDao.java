package cn.bugstack.ai.infrastructure.dao;

import cn.bugstack.ai.infrastructure.dao.po.AiClientApi;
import cn.bugstack.ai.infrastructure.dao.support.OwnerQuerySupport;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
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

    /**
     * 管理端列表分页查询：条件与归属过滤一起下推 SQL，total 由 count 得出。
     * 归属口径同 {@link #queryAll()}（visible）。
     * 注意：apiId / baseUrl 沿用改造前的<b>模糊匹配</b>语义（原内存 contains），勿改成精确匹配。
     */
    default IPage<AiClientApi> queryPage(IPage<AiClientApi> page, String apiId, String baseUrl, Integer status) {
        LambdaQueryWrapper<AiClientApi> wrapper = OwnerQuerySupport.visibleLambdaWrapper(AiClientApi::getOwnerId);
        if (apiId != null && !apiId.isBlank()) {
            wrapper.like(AiClientApi::getApiId, apiId);
        }
        if (baseUrl != null && !baseUrl.isBlank()) {
            wrapper.like(AiClientApi::getBaseUrl, baseUrl);
        }
        if (status != null) {
            wrapper.eq(AiClientApi::getStatus, status);
        }
        return selectPage(page, wrapper.orderByDesc(AiClientApi::getCreateTime));
    }

}
