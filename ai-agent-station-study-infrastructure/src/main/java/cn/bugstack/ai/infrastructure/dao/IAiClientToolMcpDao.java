package cn.bugstack.ai.infrastructure.dao;

import cn.bugstack.ai.infrastructure.dao.po.AiClientToolMcp;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.List;

/**
 * MCP客户端配置表 DAO
 * @author bugstack虫洞栈
 * @description MCP客户端配置表数据访问对象（MyBatis-Plus 迁移版，SQL 由 Wrapper 拼接，无 XML）
 */
@Mapper
public interface IAiClientToolMcpDao extends BaseMapper<AiClientToolMcp> {

    default int updateById(AiClientToolMcp aiClientToolMcp) {
        UpdateWrapper<AiClientToolMcp> uw = new UpdateWrapper<>();
        uw.eq("id", aiClientToolMcp.getId());
        uw.set("mcp_id", aiClientToolMcp.getMcpId());
        uw.set("mcp_name", aiClientToolMcp.getMcpName());
        uw.set("transport_type", aiClientToolMcp.getTransportType());
        uw.set("transport_config", aiClientToolMcp.getTransportConfig());
        uw.set("request_timeout", aiClientToolMcp.getRequestTimeout());
        uw.set("status", aiClientToolMcp.getStatus());
        uw.set("update_time", LocalDateTime.now());
        return update(null, uw);
    }

    default int updateByMcpId(AiClientToolMcp aiClientToolMcp) {
        UpdateWrapper<AiClientToolMcp> uw = new UpdateWrapper<>();
        uw.eq("mcp_id", aiClientToolMcp.getMcpId());
        uw.set("mcp_name", aiClientToolMcp.getMcpName());
        uw.set("transport_type", aiClientToolMcp.getTransportType());
        uw.set("transport_config", aiClientToolMcp.getTransportConfig());
        uw.set("request_timeout", aiClientToolMcp.getRequestTimeout());
        uw.set("status", aiClientToolMcp.getStatus());
        uw.set("update_time", LocalDateTime.now());
        return update(null, uw);
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
