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

    /**
     * 当前登录用户ID：用于把对话记忆按用户分区。
     * <p>
     * 为什么放在实体里而不是读线程上下文：执行（含记忆读写）跑在线程池里，
     * ThreadLocal 在那里是空的，只能显式携带。
     */
    private String userId;

    /**
     * 对话记忆的会话键：{@code userId::sessionId}。
     * <p>
     * 改造前直接用 sessionId（前端生成的时间戳+随机串），换个账号只要 sessionId 撞上
     * 就会读到别人的上下文记忆；加上用户前缀后记忆天然按用户隔离。
     * userId 缺失（未登录/旧调用）时退化为原行为，保持兼容。
     */
    public String getMemoryConversationId() {
        if (userId == null || userId.isBlank()) {
            return sessionId;
        }
        return userId + "::" + sessionId;
    }
}
