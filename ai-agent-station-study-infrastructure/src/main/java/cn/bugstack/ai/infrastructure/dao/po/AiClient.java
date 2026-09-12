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
 * AI客户端配置表
 * @author bugstack虫洞栈
 * @description AI客户端配置表 PO 对象
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@TableName("ai_client")
public class AiClient {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 客户端ID
     */
    private String clientId;

    /**
     * 客户端名称
     */
    private String clientName;

    /**
     * 客户端描述
     * <p>
     * 【已废弃，请勿使用】本字段不是数据库列（`ai_client` 表只有 `description`），
     * 且 MP 不会为 `exist = false` 的字段生成 SELECT 列，因此**读取值恒为 null**。
     * 客户端描述请使用 {@link #description}。
     *
     * @deprecated 用 {@link #description} 代替；保留仅为兼容 `docs/MyBatis-Plus-迁移-交接文档.md`
     * 中「已知非数据库字段」的记录
     */
    @Deprecated
    @TableField(exist = false)
    private String clientDesc;

    /**
     * 描述
     */
    private String description;

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
