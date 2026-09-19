package cn.bugstack.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 上下文预算配置
 * <p>
 * 对应 application-*.yml 中的 {@code xfg.ai.context.*}，
 * 由 {@link ContextBudgetConfig} 汇总为 {@code ContextBudgetVO} Bean 供 domain 层使用。
 *
 * @author xiaofuge bugstack.cn @小傅哥
 * 2026/09/14
 */
@Data
@ConfigurationProperties(prefix = "xfg.ai.context", ignoreInvalidFields = true)
public class ContextBudgetProperties {

    /**
     * A 套：跨请求对话记忆
     */
    private Memory memory = new Memory();

    /**
     * B 套：单次请求内执行历史
     */
    private History history = new History();

    /**
     * 摘要生成
     */
    private Summary summary = new Summary();

    /**
     * 估算校准
     */
    private Calibration calibration = new Calibration();

    /**
     * 缓存治理：按会话维度缓存的容量约束
     */
    private Cache cache = new Cache();

    @Data
    public static class Memory {
        /** 记忆允许占用的 token 上限 */
        private Integer tokenBudget = 3000;
        /** 压缩水位：估算 token > budget × 该比例即触发压缩 */
        private Double triggerRatio = 0.8;
        /** 压缩时保留最近 N 条消息完整不压缩 */
        private Integer keepRecentMessages = 6;
        /** 给 system 提示词 + 用户输入 + 工具结果预留的 token */
        private Integer reserveTokens = 500;
        /** 摘要自身允许的最大 token（防摘要无限膨胀） */
        private Integer maxSummaryTokens = 400;
        /** 兼容旧配置：消息条数硬上限，0 表示不启用 */
        private Integer maxMessages = 0;
    }

    @Data
    public static class History {
        /** 执行历史允许占用的 token 上限 */
        private Integer tokenBudget = 2500;
        /** 压缩水位 */
        private Double triggerRatio = 0.8;
        /** 压缩时保留最近 N 步的完整记录 */
        private Integer keepSteps = 2;
    }

    @Data
    public static class Summary {
        /** LLM：调模型生成摘要；TRUNCATE：规则截断 */
        private String mode = "LLM";
        /** 摘要使用的 ChatModel Bean 名 */
        private String modelBeanName = "openAiChatModel";
        /** 摘要调用超时（毫秒），超时降级为规则截断 */
        private Long timeoutMs = 8000L;
        /** 规则截断时每段保留的字符数 */
        private Integer truncateCharsPerStep = 300;
    }

    @Data
    public static class Calibration {
        /** 是否用真实 usage 校准本地估算 */
        private Boolean enabled = true;
        /** EMA 平滑系数 */
        private Double alpha = 0.3;
        /** 至少累计多少次真实 usage 才开始校准 */
        private Integer minSamples = 2;
        /** 工具调用链路的 usage 会低估，默认不参与校准 */
        private Boolean includeToolCalls = false;
    }

    @Data
    public static class Cache {
        /** 真实用量登记的会话数上限，超出按最近最少使用淘汰 */
        private Integer usageMaxConversations = 10000;
        /** 真实用量登记的过期时间（秒），自最后一次访问起算 */
        private Long usageTtlSeconds = 3600L;
        /** 记忆摘要 / 压缩告警抑制缓存的会话数上限 */
        private Integer memoryMaxConversations = 5000;
    }

}
