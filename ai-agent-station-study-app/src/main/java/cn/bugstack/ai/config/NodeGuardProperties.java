package cn.bugstack.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

/**
 * 节点治理配置（挂在项目已有的 xfg.ai 前缀下）
 * <pre>
 * xfg:
 *   ai:
 *     node-guard:
 *       enabled: true
 *       total-budget-ms: 240000          # 全局 deadline，必须 < sse-timeout-millis
 *       default-node-timeout-ms: 30000   # 未在 node-timeout-ms 里指定的节点取这个值
 *       node-timeout-ms:                 # 逐节点覆盖
 *         flow.step4.execute-steps: 90000
 *       max-retries: 2
 *       retry-backoff-base-ms: 500
 *       heartbeat-interval-ms: 15000
 *       step-interval-ms: 300
 *       model-http:
 *         connect-timeout-ms: 10000      # 底层连接超时（真正断 socket 的一刀）
 *         read-timeout-ms: 120000        # 底层读超时，必须大于最长的节点超时
 * </pre>
 */
@Data
@ConfigurationProperties(prefix = "xfg.ai.node-guard")
public class NodeGuardProperties {

    /** 总开关 */
    private Boolean enabled;

    /** 全局 deadline */
    private Long totalBudgetMs;

    /** 默认节点硬超时 */
    private Long defaultNodeTimeoutMs;

    /** 逐节点硬超时 */
    private Map<String, Long> nodeTimeoutMs;

    /** 最大重试次数 */
    private Integer maxRetries;

    /** 重试退避基数 */
    private Long retryBackoffBaseMs;

    /** SSE 心跳间隔 */
    private Long heartbeatIntervalMs;

    /** Step4 逐步执行节流 */
    private Long stepIntervalMs;

    /** 模型网关 HTTP 超时（装配 OpenAiApi 时注入，见 AiHttpClientConfig） */
    private ModelHttp modelHttp = new ModelHttp();

    @Data
    public static class ModelHttp {
        /** 连接超时，默认 10s */
        private Long connectTimeoutMs;
        /** 读超时，默认 120s，必须大于最耗时的那个节点超时，否则会把正常长调用先掐断 */
        private Long readTimeoutMs;
    }
}
