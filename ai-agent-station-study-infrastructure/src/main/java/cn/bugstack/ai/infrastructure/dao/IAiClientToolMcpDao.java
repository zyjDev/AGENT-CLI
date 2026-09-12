package cn.bugstack.ai.infrastructure.dao;

import cn.bugstack.ai.infrastructure.dao.po.AiClientToolMcp;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
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
        return selectById(id);
    }

    default AiClientToolMcp queryByMcpId(String mcpId) {
        return selectOne(new QueryWrapper<AiClientToolMcp>().eq("mcp_id", mcpId));
    }

    default List<AiClientToolMcp> queryAll() {
        return selectList(new QueryWrapper<AiClientToolMcp>().orderByDesc("create_time"));
    }

    default List<AiClientToolMcp> queryByStatus(Integer status) {
        return selectList(new QueryWrapper<AiClientToolMcp>().eq("status", status).orderByDesc("create_time"));
    }

    default List<AiClientToolMcp> queryByTransportType(String transportType) {
        return selectList(new QueryWrapper<AiClientToolMcp>().eq("transport_type", transportType).orderByDesc("create_time"));
    }

    default List<AiClientToolMcp> queryEnabledMcps() {
        return selectList(new QueryWrapper<AiClientToolMcp>().eq("status", 1).orderByDesc("create_time"));
    }

}
