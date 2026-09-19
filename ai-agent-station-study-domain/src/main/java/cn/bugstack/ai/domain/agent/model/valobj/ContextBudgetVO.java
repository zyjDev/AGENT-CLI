package cn.bugstack.ai.domain.agent.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 上下文预算配置值对象
 * <p>
 * 由 app 层 {@code ContextBudgetProperties} 绑定 yml 后构造为 Bean；
 * DB {@code ai_client_advisor.ext_param} 可通过 {@link #override(AiClientAdvisorVO.ChatMemory)} 逐项覆盖。
 *
 * @author xiaofuge bugstack.cn @小傅哥
 * 2026/09/14
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ContextBudgetVO {

    // ==================== A 套：跨请求对话记忆 ====================

    /**
     * 记忆允许占用的 token 上限
     */
    @Builder.Default
    private int memoryTokenBudget = 3000;

    /**
     * 压缩水位：估算 token > budget × ratio 时触发压缩
     */
    @Builder.Default
    private double memoryTriggerRatio = 0.8;

    /**
     * 压缩时保留最近 N 条消息完整不压缩
     */
    @Builder.Default
    private int keepRecentMessages = 6;

    /**
     * 给 system 提示词 + 用户输入 + 工具结果预留的 token
     */
    @Builder.Default
    private int reserveTokens = 500;

    /**
     * 摘要自身允许的最大 token（防止摘要无限膨胀）
     */
    @Builder.Default
    private int maxSummaryTokens = 400;

    /**
     * 兼容旧配置：消息条数硬上限，0 表示不启用（完全由 token 预算接管）
     */
    @Builder.Default
    private int maxMessages = 0;

    // ==================== B 套：单次请求内执行历史 ====================

    /**
     * 执行历史允许占用的 token 上限
     */
    @Builder.Default
    private int historyTokenBudget = 2500;

    /**
     * 执行历史压缩水位
     */
    @Builder.Default
    private double historyTriggerRatio = 0.8;

    /**
     * 压缩时保留最近 N 步的完整记录
     */
    @Builder.Default
    private int historyKeepSteps = 2;

    // ==================== 摘要生成 ====================

    /**
     * 摘要生成方式：LLM（调模型）/ TRUNCATE（规则截断）
     */
    @Builder.Default
    private String summaryMode = "LLM";

    /**
     * 摘要使用的 ChatModel Bean 名
     */
    @Builder.Default
    private String summaryModelBeanName = "openAiChatModel";

    /**
     * 摘要调用超时（毫秒），超时降级为规则截断
     */
    @Builder.Default
    private long summaryTimeoutMs = 8000L;

    /**
     * 规则截断时每段保留的字符数
     */
    @Builder.Default
    private int truncateCharsPerStep = 300;

    // ==================== 估算校准 ====================

    /**
     * 是否用真实 usage 校准本地估算
     */
    @Builder.Default
    private boolean calibrationEnabled = true;

    /**
     * EMA 平滑系数
     */
    @Builder.Default
    private double calibrationAlpha = 0.3;

    /**
     * 至少累计多少次真实 usage 才开始校准
     */
    @Builder.Default
    private int calibrationMinSamples = 2;

    /**
     * 工具调用链路的 usage 只统计最后一次模型调用会低估，默认排除出校准
     */
    @Builder.Default
    private boolean calibrationIncludeToolCalls = false;

    // ==================== 缓存治理 ====================
    // 以下三项都是「按会话维度缓存」的容量约束。会话数会随使用持续增长，
    // 若不设上限，这些 Map 就是内存泄漏源（原实现均为无界 ConcurrentHashMap）。

    /**
     * 真实用量登记的会话数上限，超出按最近最少使用淘汰
     */
    @Builder.Default
    private int usageCacheMaxConversations = 10000;

    /**
     * 真实用量登记的过期时间（秒），自最后一次访问起算
     */
    @Builder.Default
    private long usageCacheTtlSeconds = 3600L;

    /**
     * 记忆摘要 / 压缩告警抑制缓存的会话数上限
     */
    @Builder.Default
    private int memoryCacheMaxConversations = 5000;

    /**
     * 用 DB ext_param 覆盖 yml 默认值。
     * <p>
     * 规则：新增字段用包装类型，null / 非正数 / 空白 一律沿用 yml 默认值，
     * 因此「DB 未更新」不会导致配置失效，只是不生效覆盖。
     * <p>
     * B 套、摘要超时、校准参数不支持按 client 覆盖，一律沿用全局值。
     *
     * @param cm DB 解析出的 ChatMemory 配置，可为 null
     * @return 覆盖后的新实例（不改动 this）
     */
    public ContextBudgetVO override(AiClientAdvisorVO.ChatMemory cm) {
        if (cm == null) {
            return this;
        }
        return ContextBudgetVO.builder()
                .memoryTokenBudget(positive(cm.getTokenBudget(), this.memoryTokenBudget))
                .memoryTriggerRatio(positive(cm.getTriggerRatio(), this.memoryTriggerRatio))
                .keepRecentMessages(positive(cm.getKeepRecentMessages(), this.keepRecentMessages))
                .reserveTokens(positive(cm.getReserveTokens(), this.reserveTokens))
                .maxSummaryTokens(positive(cm.getMaxSummaryTokens(), this.maxSummaryTokens))
                .maxMessages(cm.getMaxMessages() > 0 ? cm.getMaxMessages() : this.maxMessages)
                .summaryMode(blank(cm.getSummaryMode()) ? this.summaryMode : cm.getSummaryMode())
                .summaryModelBeanName(blank(cm.getSummaryModelBeanName()) ? this.summaryModelBeanName : cm.getSummaryModelBeanName())
                // 以下沿用全局，不支持按 client 覆盖
                .historyTokenBudget(this.historyTokenBudget)
                .historyTriggerRatio(this.historyTriggerRatio)
                .historyKeepSteps(this.historyKeepSteps)
                .summaryTimeoutMs(this.summaryTimeoutMs)
                .truncateCharsPerStep(this.truncateCharsPerStep)
                .calibrationEnabled(this.calibrationEnabled)
                .calibrationAlpha(this.calibrationAlpha)
                .calibrationMinSamples(this.calibrationMinSamples)
                .calibrationIncludeToolCalls(this.calibrationIncludeToolCalls)
                // 缓存治理参数同样只走全局，不支持按 client 覆盖
                .usageCacheMaxConversations(this.usageCacheMaxConversations)
                .usageCacheTtlSeconds(this.usageCacheTtlSeconds)
                .memoryCacheMaxConversations(this.memoryCacheMaxConversations)
                .build();
    }

    private static int positive(Integer value, int fallback) {
        return (value == null || value <= 0) ? fallback : value;
    }

    private static double positive(Double value, double fallback) {
        return (value == null || value <= 0) ? fallback : value;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

}
