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
 * 智能体-客户端关联表
 * @author bugstack虫洞栈
 * @description 智能体-客户端关联表 PO 对象
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@TableName("ai_agent_flow_config")
public class AiAgentFlowConfig {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 智能体ID
     */
    private String agentId;

    /**
     * 客户端ID
     */
    private String clientId;

    /**
     * 客户端名称
     */
    private String clientName;

    /**
     * 客户端枚举
     */
    private String clientType;

    /**
     * 序列号(执行顺序)
     */
    private Integer sequence;

    /**
     * 执行步骤提示词
     */
    private String stepPrompt;

    /**
     * 状态；0无效，1有效
     * <p>
     * 对应建表 SQL ai_agent_flow_config.status。执行链路（Flow/Auto/Fixed 策略与 Armory 装配）
     * 只允许加载 status=1 的流程配置，请使用 IAiAgentFlowConfigDao#queryEnabledByAgentId 查询；
     * 数据中存在 status=0 的占位/禁用节点（如 agent_id='1' 的 2101~2103），若一并加载会被真实执行。
     */
    private Integer status;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

}