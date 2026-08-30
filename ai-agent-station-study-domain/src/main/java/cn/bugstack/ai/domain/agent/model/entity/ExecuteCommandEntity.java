package cn.bugstack.ai.domain.agent.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 执行命令实体
 * 
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ExecuteCommandEntity {
    /**
     * 智能体ID
     */
    private String aiAgentId;

    /**
     * 执行命令消息
     */
    private String message;
    
    /**
     * 会话ID
     */
    private String sessionId;

    /**
     * 最大执行步数
     */
    private Integer maxStep;

    /**
     * 可选：知识域标签
     */
    private String  knowledgeTag;
}
