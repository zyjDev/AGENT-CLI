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
 * AI智能体拖拉拽配置历史表
 * @author bugstack虫洞栈
 * @description AI智能体拖拉拽配置历史表 PO 对象
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@TableName("ai_agent_draw_history")
public class AiAgentDrawHistory {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 配置ID（关联ai_agent_draw_config）
     */
    private String configId;

    /**
     * 版本号
     */
    private Integer version;

    /**
     * 历史配置JSON数据
     */
    private String configData;

    /**
     * 变更类型（create、update、delete）
     */
    private String changeType;

    /**
     * 变更描述
     */
    private String changeDesc;

    /**
     * 变更人
     */
    private String changeBy;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

}