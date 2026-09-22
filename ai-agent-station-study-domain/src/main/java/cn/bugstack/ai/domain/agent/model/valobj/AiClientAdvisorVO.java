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

        // ===== 精排相关（全部用包装类型：null 表示未配置，回落默认值）=====
        /**
         * 召回池大小。null 或 &lt;= topK 表示不精排（直接取 topK 条）
         */
        private Integer recallK;
        /**
         * 是否开启精排。null 视为 false
         */
        private Boolean rerankEnabled;
        /**
         * 精排使用的 ChatModel Bean 名（如 ai_client_model_3001）。为空则退回默认
         */
        private String rerankModelBeanName;
        /**
         * 精排调用超时（毫秒）。默认 25000
         * <p>
         * 为什么不是 3000：精排是一次「20 候选 × 上千字」的 listwise 调用，即使关掉推理
         * 实测也要 6~11s。超时给太短等于每次都走降级分支，精排形同虚设。
         */
        private Integer rerankTimeoutMs;
        /**
         * 送进精排 prompt 的每个候选片段最大字符数。默认 1200
         * <p>
         * 为什么不能太小：语料 chunk 中位长度在 850~3500 字符之间，截断到 400 会把答案
         * 本身切掉，精排退化成「凭开头猜主题」。实测同一批候选用 400 字符时排序质量明显更差。
         */
        private Integer rerankDocChars;
        /**
         * 精排模型的推理强度（OpenAI 标准参数 reasoning_effort）。
         * <p>
         * <b>实测必须配 "none"</b>：项目默认模型 mimo-v2.5 是推理模型，20 候选的 listwise
         * prompt 会让它先产出 800+ 推理 token —— 实测单次 29.8s、finish_reason=length、
         * content 为空（输出预算全被思考吃光），精排必然超时降级。关掉推理后降到约 7~10s
         * 且能正常返回 JSON 分数。null 表示沿用模型默认（即保留推理）。
         */
        private String rerankReasoningEffort;

        // ===== 多查询改写（可选增强）=====

        /**
         * 是否开启多查询改写。null 视为 false
         */
        private Boolean multiQueryEnabled;
        /**
         * 生成的查询变体数量（不含原始查询）。未配置时默认 3
         */
        private Integer multiQueryCount;
        /**
         * 改写用的 ChatModel Bean 名（如 ai_client_model_3001）。为空则视为未开启
         */
        private String multiQueryModelBeanName;

        /**
         * 是否真正启用精排：显式打开 + 配了模型 + 池子比目标条数大，三者同时满足。
         */
        public boolean rerankActive() {
            return Boolean.TRUE.equals(rerankEnabled)
                    && rerankModelBeanName != null && !rerankModelBeanName.isBlank()
                    && recallK != null && recallK > topK;
        }

        /**
         * 是否真正启用多查询改写：显式打开 + 配了模型。
         * <p>
         * 与精排的启用条件不同 —— 多查询作用在<b>召回阶段</b>，不要求 {@code recallK > topK}。
         * 即使不开精排，它也能把「多个角度的召回结果」合并去重后取 topK，直接改善送进 prompt 的片段。
         */
        public boolean multiQueryActive() {
            return Boolean.TRUE.equals(multiQueryEnabled)
                    && multiQueryModelBeanName != null && !multiQueryModelBeanName.isBlank();
        }

        /** 实际使用的变体数量（未配置或非法时回落 3） */
        public int resolveMultiQueryCount() {
            return multiQueryCount == null || multiQueryCount <= 0 ? 3 : multiQueryCount;
        }
    }

}
