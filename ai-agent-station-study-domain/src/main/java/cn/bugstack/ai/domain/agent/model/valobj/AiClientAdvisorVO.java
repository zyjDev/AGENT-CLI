package cn.bugstack.ai.domain.agent.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 顾问配置，值对象
 *
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AiClientAdvisorVO {

    /**
     * 顾问ID
     */
    private String advisorId;

    /**
     * 顾问名称
     */
    private String advisorName;

    /**
     * 顾问类型(PromptChatMemory/RagAnswer/SimpleLoggerAdvisor等)
     */
    private String advisorType;

    /**
     * 顺序号
     */
    private Integer orderNum;

    /**
     * 扩展；记忆
     */
    private ChatMemory chatMemory;

    /**
     * 扩展；rag 问答
     */
    private RagAnswer ragAnswer;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ChatMemory {
        /**
         * 兼容旧字段：消息条数上限。
         * 0 表示不启用，完全由 token 预算接管（新方案默认 0）。
         */
        private int maxMessages;

        // ==================== 以下为 token 预算新增字段 ====================
        // 一律用包装类型：null 才能区分「DB 未配置」与「配置为 0」，
        // 未配置时由 ContextBudgetVO.override 回落 yml 默认值。

        /**
         * 记忆允许占用的 token 上限
         */
        private Integer tokenBudget;

        /**
         * 压缩水位：估算 token > budget × 该比例即触发压缩
         */
        private Double triggerRatio;

        /**
         * 压缩时保留最近 N 条消息完整不压缩
         */
        private Integer keepRecentMessages;

        /**
         * 给 system 提示词 + 用户输入 + 工具结果预留的 token
         */
        private Integer reserveTokens;

        /**
         * 摘要自身允许的最大 token
         */
        private Integer maxSummaryTokens;

        /**
         * 摘要生成方式：LLM / TRUNCATE
         */
        private String summaryMode;

        /**
         * 摘要使用的 ChatModel Bean 名
         */
        private String summaryModelBeanName;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class RagAnswer {
        private int topK = 4;
        private String filterExpression;
    }

}
