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
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

}