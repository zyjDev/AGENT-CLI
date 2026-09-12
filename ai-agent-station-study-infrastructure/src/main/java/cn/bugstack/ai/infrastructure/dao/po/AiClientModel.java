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
 * 聊天模型配置表
 * @author bugstack虫洞栈
 * @description 聊天模型配置表 PO 对象
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@TableName("ai_client_model")
public class AiClientModel {

    /**
     * 自增主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 全局唯一模型ID
     */
    private String modelId;

    /**
     * 关联的API配置ID
     */
    private String apiId;

    /**
     * 模型名称
     */
    private String modelName;

    /**
     * 模型类型：openai、deepseek、claude
     */
    private String modelType;

    /**
     * 模型类型名称
     * <p>
     * 【已废弃，请勿使用】本字段不是数据库列（`ai_client_model` 表只有 `model_type`），
     * 且 MP 不会为 `exist = false` 的字段生成 SELECT 列，因此**读取值恒为 null**；
     * 项目中也没有 `modelType` → 中文名称 的映射表。
     * 若确实需要展示名称，请基于 {@link #modelType} 自行映射。
     *
     * @deprecated 无任何数据来源，保留仅为兼容 `docs/MyBatis-Plus-迁移-交接文档.md`
     * 中「已知非数据库字段」的记录
     */
    @Deprecated
    @TableField(exist = false)
    private String typeName;

    /**
     * 模型用途
     */
    private String modelUsage;

    /**
     * 状态：0-禁用，1-启用
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
