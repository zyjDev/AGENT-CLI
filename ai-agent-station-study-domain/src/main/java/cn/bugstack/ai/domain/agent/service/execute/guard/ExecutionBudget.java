package cn.bugstack.ai.domain.agent.service.execute.guard;

import lombok.Getter;

/**
 * 单次请求的全局执行预算（全局 deadline）
 * <p>
 * 为什么必须有它：只有「节点硬超时」是不够的。
 * 反例 —— 4 个节点各配 60s 硬超时，每个都没超时，但串行执行总耗时可以到 240s，
 * 一旦链路里还有循环（Auto 链路会 Analyzer→Executor→Supervisor 多轮回到 Analyzer），
 * 总耗时完全不可控，最终撞上 SSE 的 600s 连接超时：用户会话报废、已花的 token 白花。
 * <p>
 * 语义：预算是「剩余可分配的时间」，每个节点开始时会取 {@code remaining = deadline - now}，
 * 实际超时 = min(节点硬超时, remaining)。预算耗尽不等于失败，而是「停止调用模型，直接降级收敛」。
 */
@Getter
public class ExecutionBudget {

    private final long startMillis;

    private final long deadlineMillis;

    public ExecutionBudget(long budgetMs) {
        this.startMillis = System.currentTimeMillis();
        this.deadlineMillis = this.startMillis + Math.max(0L, budgetMs);
    }

    public static ExecutionBudget start(long budgetMs) {
        return new ExecutionBudget(budgetMs);
    }

    /** 剩余预算（毫秒），已耗尽返回 0（不会为负，便于直接比较） */
    public long remainingMillis() {
        return Math.max(0L, deadlineMillis - System.currentTimeMillis());
    }

    /** 已消耗时间 */
    public long elapsedMillis() {
        return System.currentTimeMillis() - startMillis;
    }

    public boolean exhausted() {
        return remainingMillis() <= 0;
    }

    /** 剩余比例（0~1），用于判断是否该跳过「锦上添花」的节点 */
    public double remainingRatio() {
        long total = deadlineMillis - startMillis;
        if (total <= 0) {
            return 0d;
        }
        return Math.max(0d, Math.min(1d, remainingMillis() * 1.0d / total));
    }

    @Override
    public String toString() {
        return String.format("已耗时 %dms / 剩余 %dms",
                elapsedMillis(), remainingMillis());
    }
}
