package cn.bugstack.ai.domain.agent.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI智能体配置值对象
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AiAgentVO {

    /**
     * 智能体ID
     */
    private String agentId;

    /**
     * 智能体名称
     */
    private String agentName;

    /**
     * 描述
     */
    private String description;

    /**
     * 渠道类型(agent，chat_stream)
     */
    private String channel;

    /**
     * 执行策略(auto、flow)
     */
    private String strategy;

    /**
     * 状态(0:禁用,1:启用)
     */
    private Integer status;

    /**
     * 归属用户；空/null = 平台默认资源（人人可用）。
     * <p>
     * 归属校验（能否使用 / 是否"自建"）本属领域规则，必须能在领域层判定，
     * 因此把它带进 VO —— 改造前 trigger 层只能再直连 DAO 查一次 {@code ai_agent} 才拿得到它。
     */
    private String ownerId;

}
