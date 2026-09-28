package cn.bugstack.ai.infrastructure.dao;

import cn.bugstack.ai.infrastructure.dao.po.AiClientToolMcp;
import cn.bugstack.ai.infrastructure.dao.support.OwnerQuerySupport;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * MCP客户端配置表 DAO
 * @author bugstack虫洞栈
 * @description MCP客户端配置表数据访问对象（MyBatis-Plus 迁移版，SQL 由 Wrapper 拼接，无 XML）
 */
@Mapper
public interface IAiClientToolMcpDao extends BaseMapper<AiClientToolMcp> {

    /**
     * 根据MCP ID更新MCP客户端配置
     * <p>
     * 实体驱动更新：只有非 null 字段进入 SET 子句，未传字段保持库中原值；
     * update_time 由 TimeMetaObjectHandler 自动填充。
     * <p>
     * 注意：不要在此接口中声明 default int updateById(...)，否则会覆盖 BaseMapper.updateById 的 SQL 派发。
     */
    default int updateByMcpId(AiClientToolMcp aiClientToolMcp) {
        return update(aiClientToolMcp, new UpdateWrapper<AiClientToolMcp>().eq("mcp_id", aiClientToolMcp.getMcpId()));
    }

    default int deleteByMcpId(String mcpId) {
        return delete(new QueryWrapper<AiClientToolMcp>().eq("mcp_id", mcpId));
    }

    default AiClientToolMcp queryById(Long id) {
        AiClientToolMcp po = selectById(id);
        if (po != null && !OwnerQuerySupport.visibleToCurrentUser(po.getOwnerId())) {
            return null;
        }
        return po;
    }

    default AiClientToolMcp queryByMcpId(String mcpId) {
        return selectOne(new QueryWrapper<AiClientToolMcp>().eq("mcp_id", mcpId));
    }

    default List<AiClientToolMcp> queryAll() {
        return selectList(OwnerQuerySupport.<AiClientToolMcp>visibleWrapper().orderByDesc("create_time"));
    }

    /**
     * 管理端列表分页查询：条件与归属过滤一起下推 SQL，total 由 count 得出。
     * 归属口径同 {@link #queryAll()}（visible）。
     * 改造前该列表接口完全没有分页（一次返回全量），现与其它列表接口对齐。
     */
    default IPage<AiClientToolMcp> queryPage(IPage<AiClientToolMcp> page, String mcpId, String mcpName, String transportType, Integer status) {
        LambdaQueryWrapper<AiClientToolMcp> wrapper = OwnerQuerySupport.visibleLambdaWrapper(AiClientToolMcp::getOwnerId);
        if (mcpId != null && !mcpId.isBlank()) {
            wrapper.eq(AiClientToolMcp::getMcpId, mcpId);
        }
        if (mcpName != null && !mcpName.isBlank()) {
            wrapper.like(AiClientToolMcp::getMcpName, mcpName);
        }
        if (transportType != null && !transportType.isBlank()) {
            wrapper.eq(AiClientToolMcp::getTransportType, transportType);
        }
        if (status != null) {
            wrapper.eq(AiClientToolMcp::getStatus, status);
        }
        return selectPage(page, wrapper.orderByDesc(AiClientToolMcp::getCreateTime));
    }

    default List<AiClientToolMcp> queryByStatus(Integer status) {
        return selectList(OwnerQuerySupport.<AiClientToolMcp>visibleWrapper().eq("status", status).orderByDesc("create_time"));
    }

    default List<AiClientToolMcp> queryByTransportType(String transportType) {
        return selectList(OwnerQuerySupport.<AiClientToolMcp>visibleWrapper().eq("transport_type", transportType).orderByDesc("create_time"));
    }

    default List<AiClientToolMcp> queryEnabledMcps() {
        return selectList(OwnerQuerySupport.<AiClientToolMcp>visibleWrapper().eq("status", 1).orderByDesc("create_time"));
    }

}
