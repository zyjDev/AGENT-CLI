package cn.bugstack.ai.domain.agent.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 节点治理策略（超时 / 重试 / 降级 / 全局预算）
 * <p>
 * 解决的问题：原实现里 Auto / Flow 链路的 8 个节点都是 {@code .call().content()} 裸调用，
 * 唯一的「超时」是 {@code AiAgentController} 上的 600s SSE 连接超时 —— 单个节点卡死会一路吃满整个窗口，
 * 且 N 个节点各自「没超时」加起来照样拖垮请求（见 {@code ExecutionBudget} 的说明）。
 * <p>
 * 三层超时语义，务必区分：
 * <ol>
 *   <li><b>节点硬超时</b>（本类的 nodeTimeoutMs）：单个节点最多多久，防单点卡死。</li>
 *   <li><b>全局 deadline</b>（本类的 totalBudgetMs）：整条链路的总预算，防「每个节点都没超时，但加起来超时」。</li>
 *   <li><b>底层 HTTP 超时</b>（app 层 RestClient.Builder 配置）：真正断开 socket 的那一刀。
 *       Java 无法中断阻塞的 socket 读，{@code Future#get(timeout)} 只能让调用方「不再等待」，
 *       被放弃的任务线程仍要等到 HTTP read timeout 才释放 —— 三者必须同时配置才有意义。</li>
 * </ol>
 *
 * @author bugstack.cn
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class NodeGuardPolicyVO {

    /** 节点治理总开关；关闭后退化成原有的裸调用行为（便于排障回滚） */
    @Builder.Default
    private boolean enabled = true;

    /**
     * 单次执行的总预算（全局 deadline）。
     * 必须小于 SSE 超时（默认 600s），否则链路还没到总结就被 emitter 掐断。
     */
    @Builder.Default
    private long totalBudgetMs = 240_000L;

    /** 节点默认硬超时 */
    @Builder.Default
    private long defaultNodeTimeoutMs = 30_000L;

    /**
     * 按节点覆盖硬超时，key 见 {@code NodeKeys}。
     * 之所以要逐个配：不同节点的「正常耗时」差一个量级 ——
     * 监督/总结只是短文本生成（15s 足够），而 Step4 执行节点内部会驱动 MCP 工具调用（需要 60s+）。
     * 用同一个值必然是「快的太宽松、慢的太容易误杀」。
     */
    @Builder.Default
    private Map<String, Long> nodeTimeoutMs = defaultNodeTimeouts();

    /** 最多重试次数（不含首次调用）。0 表示不重试 */
    @Builder.Default
    private int maxRetries = 2;

    /** 重试退避基数：第 n 次重试等待 base * 2^(n-1) + jitter，避免同一瞬间重试打爆上游 */
    @Builder.Default
    private long retryBackoffBaseMs = 500L;

    /** SSE 心跳间隔（毫秒）：防止慢节点期间连接因 idle 被网关/代理掐断。<=0 表示不发送心跳 */
    @Builder.Default
    private long heartbeatIntervalMs = 15_000L;

    /** Step4 逐步执行之间的节流间隔（替代原先硬编码的 Thread.sleep(1000)） */
    @Builder.Default
    private long stepIntervalMs = 300L;

    // ── 节点 key 常量 ──────────────────────────────────────────────
    public static final class NodeKeys {
        /** Flow 链路：MCP 工具能力分析 */
        public static final String FLOW_STEP1_TOOL_ANALYSIS = "flow.step1.tool-analysis";
        /** Flow 链路：执行步骤规划（不可降级） */
        public static final String FLOW_STEP2_PLANNING = "flow.step2.planning";
        /** Flow 链路：规划结果解析（纯本地解析，无模型调用） */
        public static final String FLOW_STEP3_PARSE_STEPS = "flow.step3.parse-steps";
        /** Flow 链路：逐步执行（内部会调 MCP 工具，耗时最长） */
        public static final String FLOW_STEP4_EXECUTE_STEPS = "flow.step4.execute-steps";

        /** Auto 链路：任务分析 */
        public static final String AUTO_STEP1_ANALYZER = "auto.step1.analyzer";
        /** Auto 链路：精准执行 */
        public static final String AUTO_STEP2_EXECUTOR = "auto.step2.executor";
        /** Auto 链路：质量监督 */
        public static final String AUTO_STEP3_SUPERVISOR = "auto.step3.supervisor";
        /** Auto 链路：执行总结（可跳过） */
        public static final String AUTO_STEP4_SUMMARY = "auto.step4.summary";

        /** Fixed 链路：按客户端顺序串行的一次模型调用（取默认超时） */
        public static final String FIXED_CLIENT_CALL = "fixed.client-call";
    }

    /**
     * 默认逐节点超时。依据：纯生成 / 带工具调用 / 长文本规划 三类 workload 区分。
     */
    public static Map<String, Long> defaultNodeTimeouts() {
        Map<String, Long> map = new HashMap<>();
        map.put(NodeKeys.FLOW_STEP1_TOOL_ANALYSIS, 45_000L);
        map.put(NodeKeys.FLOW_STEP2_PLANNING, 60_000L);
        map.put(NodeKeys.FLOW_STEP3_PARSE_STEPS, 5_000L);
        map.put(NodeKeys.FLOW_STEP4_EXECUTE_STEPS, 90_000L);
        map.put(NodeKeys.AUTO_STEP1_ANALYZER, 30_000L);
        map.put(NodeKeys.AUTO_STEP2_EXECUTOR, 60_000L);
        map.put(NodeKeys.AUTO_STEP3_SUPERVISOR, 30_000L);
        map.put(NodeKeys.AUTO_STEP4_SUMMARY, 45_000L);
        return map;
    }

    /**
     * 取节点硬超时：优先逐节点配置，回落到默认值。
     */
    public long timeoutOf(String nodeKey) {
        if (nodeKey == null) {
            return defaultNodeTimeoutMs;
        }
        Long timeout = nodeTimeoutMs == null ? null : nodeTimeoutMs.get(nodeKey);
        return timeout == null || timeout <= 0 ? defaultNodeTimeoutMs : timeout;
    }

    /**
     * 节点实际生效超时 = min(节点硬超时, 全局剩余预算)
     */
    public long effectiveTimeout(String nodeKey, long remainingBudgetMs) {
        long timeout = timeoutOf(nodeKey);
        if (remainingBudgetMs <= 0) {
            return Math.min(timeout, 1L);
        }
        return Math.min(timeout, remainingBudgetMs);
    }

    public Map<String, Long> safeNodeTimeouts() {
        return nodeTimeoutMs == null ? Collections.emptyMap() : nodeTimeoutMs;
    }
}
