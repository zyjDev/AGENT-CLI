package cn.bugstack.ai.domain.agent.service.execute.guard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

/**
 * 节点治理执行结果
 *
 * @param <T> 节点产出类型
 */
@Data
@Builder
@AllArgsConstructor
public class NodeGuardResult<T> {

    /** 产出值；SKIPPED / FAIL_FAST 情况下可能为 null */
    private T value;

    private NodeGuardOutcome outcome;

    /** 总耗时（含重试与退避等待） */
    private long costMs;

    /** 实际调用次数（含首次） */
    private int attempts;

    /** 最后一次失败原因 */
    private Throwable lastError;

    /** 是否触发了重试 */
    private boolean retried;

    /** 生效的超时值（便于日志排查「为什么这里被判定超时」） */
    private long effectiveTimeoutMs;

    public boolean degraded() {
        return outcome != NodeGuardOutcome.SUCCESS;
    }

    public static <T> NodeGuardResult<T> success(T value, long costMs, int attempts, boolean retried, long timeoutMs) {
        return new NodeGuardResult<>(value, NodeGuardOutcome.SUCCESS, costMs, attempts, null, retried, timeoutMs);
    }

    public static <T> NodeGuardResult<T> of(NodeGuardOutcome outcome, T value, long costMs, int attempts,
                                            boolean retried, long timeoutMs, Throwable lastError) {
        return new NodeGuardResult<>(value, outcome, costMs, attempts, lastError, retried, timeoutMs);
    }
}
