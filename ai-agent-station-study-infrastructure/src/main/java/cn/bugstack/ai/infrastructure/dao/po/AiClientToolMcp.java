package cn.bugstack.ai.infrastructure.dao.po;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * MCP客户端配置表
 * @author bugstack虫洞栈
 * @description MCP客户端配置表 PO 对象
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@TableName("ai_client_tool_mcp")
public class AiClientToolMcp {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * MCP ID
     */
    private String mcpId;

    /**
     * MCP名称
     */
    private String mcpName;

    /**
     * 传输类型(sse/stdio)
     */
    private String transportType;

    /**
     * 传输配置(sse/stdio)
     */
    private String transportConfig;

    /**
     * 请求超时时间(分钟)
     */
    private Integer requestTimeout;

    /**
     * 环境变量（应用层字段，旧 XML 未持久化）
     * <p>
     * 【已废弃，请勿使用】不是数据库列，MP 不会生成对应 SELECT 列；全项目**既无赋值也无读取**。
     * MCP 的 stdio 环境变量实际由 Spring AI 的 `StdioServerParameters` 承载
     * （见 `AiClientToolMcpNode:116` 的 `stdio.getEnv()`）。
     *
     * @deprecated 无任何读写点，保留仅为兼容 `docs/MyBatis-Plus-迁移-交接文档.md`
     * 中「已知非数据库字段」的记录
     */
    @Deprecated
    @TableField(exist = false)
    private String env;

    /**
     * 状态(0:禁用,1:启用)
     */
    private Integer status;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

}
