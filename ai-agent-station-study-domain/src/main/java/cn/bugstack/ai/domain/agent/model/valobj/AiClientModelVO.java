package cn.bugstack.ai.domain.agent.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 聊天模型配置，值对象
 * @author xiaofuge bugstack.cn @小傅哥
 * 2025/6/27 17:43
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AiClientModelVO {

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
     * 【已废弃，请勿使用】全项目**无任何读取点**，此前唯一的赋值点
     * `AgentRepository:144`、`:457` 也从恒为 null 的 `AiClientModel.typeName` 取值
     * （该 PO 字段标了 `@TableField(exist = false)`，库中无 `type_name` 列）。
     * 若需要展示名称，请基于 {@link #modelType} 自行映射。
     *
     * @deprecated 无任何数据来源
     */
    @Deprecated
    private String typeName;

    /**
     * 状态：0-禁用，1-启用
     */
    private Integer status;

    /**
     * 工具 mcp ids
     */
    private List<String> toolMcpIds;

}
