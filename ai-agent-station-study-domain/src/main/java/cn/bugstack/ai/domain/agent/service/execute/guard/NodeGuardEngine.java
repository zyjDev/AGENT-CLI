package cn.bugstack.ai.domain.agent.service.execute.guard;

import cn.bugstack.ai.domain.agent.model.valobj.NodeGuardPolicyVO;
import cn.bugstack.ai.domain.agent.service.metrics.AgentRequestMetrics;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 节点执行治理引擎（超时 / 重试 / 降级 / 心跳 / 埋点）
 * <p>
 * 统一收口每个节点都要重复处理的四件事——散在各节点里写，只能靠复制粘贴维持「写法一致」，
 * 而只要有一个节点忘了写，整条链路的保护就出现缺口。新增节点时，把唯一会阻塞的那行调用
 * 包成 {@link NodeTask} 交给本引擎即可。
 * <p>
 * <b>执行流程</b>
 * <ol>
 *   <li>预算检查：全局 deadline 耗尽 → 直接降级/快速失败，不再发起模型调用。</li>
 *   <li>计算本次硬超时：{@code min(节点配置超时, 全局剩余预算)}。</li>
 *   <li>启动心跳（仅当节点超时大于心跳间隔时）。</li>
 *   <li>循环执行：{@code Future.get(timeout)}，超时或瞬时故障才重试，其余立即收敛。</li>
 *   <li>重试耗尽 → 按 {@link NodeDegradeMode} 走 FALLBACK / SKIP / FAIL_FAST。</li>
 * </ol>
 * <p>
 * <b>已知边界</b>：Java 无法中断正在阻塞 socket 读的线程，{@code Future#get(timeout)} 只让调用方停止等待，
 * 被放弃的那次调用会一直占到「底层 HTTP read timeout」才释放当前线线程。若要在
 * AiAgentController → 服务链路的部署环境里少一些悬挂线程：NodeTask 的底层 HTTP 超时必须 <b>同时</b>配好
 * （app 层 {@code AiHttpClientConfig} 为 OpenAiApi 注入带超时的 RestClient.Builder）。
 *
 * @author bugstack.cn
 */
@Slf4j
@Service
public class NodeGuardEngine {

    @Resource
    private NodeGuardPolicyVO nodeGuardPolicy;

    /**
     * 专用执行线程池 —— 必须是独立于业务线程池（threadPoolExecutor）的池。
     * 原因：节点调用在业务线程里同步等待 {@code Future} 完成，如果任务被调度到同一个池且该池已跑满，
     * 任务只能排队等待一个「正在等待它的线程」，直接死锁。
     */
    @Resource(name = "nodeGuardExecutor")
    private Executor nodeGuardExecutor;

    @Resource(name = "nodeHeartbeatScheduler")
    private ScheduledExecutorService nodeHeartbeatScheduler;

    @Resource
    private AgentRequestMetrics agentRequestMetrics;

    /**
     * 执行一次受治理的节点调用
     *
     * @param task 节点任务
     * @param <T>  产出类型
     * @return 执行结果；{@link NodeDegradeMode#FAIL_FAST} 且重试耗尽时直接抛 {@link NodeGuardException}
     */
    public <T> NodeGuardResult<T> execute(NodeTask<T> task) {
        if (task == null || task.getCallable() == null) {
            throw new IllegalArgumentException("NodeTask 缺少 callable");
        }

        String nodeKey = task.getNodeKey();
        String displayName = task.getDisplayName() == null ? nodeKey : task.getDisplayName();
        long startedAt = System.currentTimeMillis();

        // 治理总开关关闭：退化成原来的裸调用，方便线上排障时一键回滚
        if (!nodeGuardPolicy.isEnabled()) {
            try {
                return NodeGuardResult.success(task.getCallable().call(),
                        elapsed(startedAt), 1, false, 0L);
            } catch (Exception e) {
                throw new NodeGuardException(nodeKey, NodeGuardOutcome.SUCCESS, displayName + " 执行失败: " + e.getMessage(), e);
            }
        }

        trace(task, NodeTraceNotifier.PHASE_START, "开始执行");

        ExecutionBudget budget = task.getBudget();
        boolean budgetExhausted = budget != null && budget.exhausted();
        long remaining = budget == null ? nodeGuardPolicy.getTotalBudgetMs() : budget.remainingMillis();
        long timeoutMs = nodeGuardPolicy.effectiveTimeout(nodeKey, remaining);

        if (budgetExhausted) {
            log.warn("⏳ 全局预算已耗尽，跳过节点 {}: {}", displayName, budget);
            agentRequestMetrics.recordNodeBudgetExhausted(nodeKey);
            return convergeExhausted(task, startedAt, displayName);
        }

        int maxAttempts = task.isRetryable() ? Math.max(1, nodeGuardPolicy.getMaxRetries() + 1) : 1;
        Throwable lastError = null;
        int attempts = 0;

        SseHeartbeat heartbeat = task.getEmitter() == null ? null
                : SseHeartbeat.start(nodeHeartbeatScheduler, task.getEmitter(),
                nodeGuardPolicy.getHeartbeatIntervalMs(), timeoutMs);

        try {
            while (attempts < maxAttempts) {
                attempts++;
                // 每一轮都重新按「当下剩余预算」裁剪超时，避免重试把总耗时顶穿 deadline
                long attemptRemaining = budget == null
                        ? timeoutMs
                        : Math.min(timeoutMs, Math.max(1L, budget.remainingMillis()));

                // 只有首次调用才重复发 node_start（带上限时信息）；重试由 node_retry 事件表达，
                // 否则每轮两条 node_start，前端无法区分「新节点」和「同一节点又试了一次」
                if (attempts == 1) {
                    trace(task, NodeTraceNotifier.PHASE_START,
                            String.format("调用中（第 %d/%d 次，限时 %dms）", attempts, maxAttempts, attemptRemaining));
                }

                try {
                    T value = callWithTimeout(task.getCallable(), attemptRemaining);
                    long cost = elapsed(startedAt);
                    agentRequestMetrics.recordNodeSuccess(nodeKey, cost);
                    trace(task, NodeTraceNotifier.PHASE_END, String.format("完成（%dms）", cost));
                    return NodeGuardResult.success(value, cost, attempts, attempts > 1, attemptRemaining);
                } catch (TimeoutException e) {
                    lastError = e;
                    agentRequestMetrics.recordNodeTimeout(nodeKey);
                    log.warn("⏱️ 节点超时 node={}, 第 {}/{} 次, limit={}ms, {}",
                            displayName, attempts, maxAttempts, attemptRemaining, budget);
                    trace(task, NodeTraceNotifier.PHASE_TIMEOUT,
                            String.format("超时（%dms），剩余重试 %d 次", attemptRemaining, maxAttempts - attempts));
                } catch (ExecutionException e) {
                    lastError = unwrap(e);
                    log.warn("节点执行异常 node={}, 第 {}/{} 次: {}",
                            displayName, attempts, maxAttempts, lastError.getMessage());
                } catch (Throwable e) {
                    lastError = e;
                    log.warn("节点执行异常 node={}, 第 {}/{} 次: {}",
                            displayName, attempts, maxAttempts, e.getMessage());
                }

                if (attempts >= maxAttempts || !isRetryable(lastError, attempts)) {
                    break;
                }
                long backoff = backoffMs(attempts);
                trace(task, NodeTraceNotifier.PHASE_RETRY,
                        String.format("触发重试（%dms 后第 %d 次）", backoff, attempts + 1));
                sleepQuietly(backoff);
            }
        } finally {
            if (heartbeat != null) {
                heartbeat.stop();
            }
        }

        agentRequestMetrics.recordNodeFailure(nodeKey, elapsed(startedAt));
        return convergeFailure(task, startedAt, displayName, attempts, lastError);
    }

    // ── 内部方法 ────────────────────────────────────────────────

    private <T> T callWithTimeout(java.util.concurrent.Callable<T> callable, long timeoutMs)
            throws ExecutionException, InterruptedException, TimeoutException {
        CompletableFuture<T> future = CompletableFuture.supplyAsync(() -> {
            try {
                return callable.call();
            } catch (Throwable t) {
                // supplyAsync 只接受 Supplier，受检异常必须在此包一层，外层再拆回来
                throw new IllegalStateException(t);
            }
        }, nodeGuardExecutor);
        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } finally {
            if (!future.isDone()) {
                // 调用方不再等待；任务线程要等底层 HTTP read timeout 才释放，这里显式标记便于排查
                future.cancel(false);
            }
        }
    }

    /**
     * 是否重试：第 1 次允许读超时重试，后续只剩 connect 类异常才重试（避免读超时反复烧钱）
     */
    private boolean isRetryable(Throwable t, int attempts) {
        return RetryJudge.isTransient(t, attempts == 1);
    }

    private long backoffMs(int attemptsDone) {
        long base = Math.max(1L, nodeGuardPolicy.getRetryBackoffBaseMs());
        long backoff = base * (1L << Math.max(0, attemptsDone - 1));
        long jitter = (long) (Math.random() * base);
        return Math.min(backoff + jitter, Math.max(base, base * 8));
    }

    private <T> NodeGuardResult<T> convergeExhausted(NodeTask<T> task, long startedAt, String displayName) {
        return converge(task, startedAt, displayName, 0, null, NodeGuardOutcome.BUDGET_EXHAUSTED);
    }

    private <T> NodeGuardResult<T> convergeFailure(NodeTask<T> task, long startedAt, String displayName,
                                                   int attempts, Throwable lastError) {
        return converge(task, startedAt, displayName, attempts, lastError, NodeGuardOutcome.DEGRADED);
    }

    /**
     * 收敛：按降级档位产出兜底值 / 跳过 / 快速失败
     */
    private <T> NodeGuardResult<T> converge(NodeTask<T> task, long startedAt, String displayName,
                                            int attempts, Throwable lastError, NodeGuardOutcome outcome) {
        String reason = describeFailure(lastError, task.getBudget());

        if (task.getDegradeMode() == NodeDegradeMode.FAIL_FAST) {
            log.error("❌ 节点不可降级，链路终止 node={}, reason={}", displayName, reason);
            trace(task, NodeTraceNotifier.PHASE_DEGRADE, "失败且不可降级，链路终止：" + reason);
            throw new NodeGuardException(task.getNodeKey(), outcome,
                    "节点执行失败且不可降级：" + displayName + "；原因：" + reason, lastError);
        }

        if (task.getDegradeMode() == NodeDegradeMode.SKIP) {
            log.warn("⏭️ 节点已跳过 node={}, reason={}", displayName, reason);
            trace(task, outcome == NodeGuardOutcome.BUDGET_EXHAUSTED
                    ? NodeTraceNotifier.PHASE_BUDGET
                    : NodeTraceNotifier.PHASE_DEGRADE, "已跳过（" + reason + "）");
            return NodeGuardResult.of(NodeGuardOutcome.SKIPPED, null, elapsed(startedAt),
                    attempts, false, 0L, lastError);
        }

        // FALLBACK
        if (task.getFallback() == null) {
            log.warn("⚠️ FALLBACK 模式但未配置 fallback supplier，按 SKIP 处理 node={}", displayName);
            return NodeGuardResult.of(NodeGuardOutcome.SKIPPED, null, elapsed(startedAt),
                    attempts, false, 0L, lastError);
        }

        String phase = outcome == NodeGuardOutcome.BUDGET_EXHAUSTED
                ? NodeTraceNotifier.PHASE_BUDGET
                : NodeTraceNotifier.PHASE_DEGRADE;
        try {
            T fallbackValue = task.getFallback().get();
            log.warn("🪂 节点降级兜底 node={}, reason={}", displayName, reason);
            trace(task, phase, "已降级（" + reason + "）");
            return NodeGuardResult.of(NodeGuardOutcome.DEGRADED, fallbackValue, elapsed(startedAt),
                    attempts, attempts > 1, 0L, lastError);
        } catch (Exception e) {
            log.error("兜底逻辑自身异常 node={}", displayName, e);
            trace(task, phase, "降级失败：" + e.getMessage());
            return NodeGuardResult.of(NodeGuardOutcome.DEGRADED, null, elapsed(startedAt),
                    attempts, attempts > 1, 0L, lastError);
        }
    }

    /**
     * 失败原因的人类可读描述。
     * <p>
     * 之所以要单独写：Future.get 超时抛的是 {@code TimeoutException}，它的 {@code getMessage()} 恒为 null，
     * 直接拼字符串会得到「已降级（null）」—— 用户和日志都看不出发生了什么。
     * 超时必须显式说成「超过 xxms 未返回」。
     */
    private String describeFailure(Throwable lastError, ExecutionBudget budget) {
        if (lastError == null) {
            return budget == null ? "未知原因" : "全局预算耗尽（" + budget + "）";
        }
        String message = lastError.getMessage();
        if (message != null && !message.isBlank()) {
            return message;
        }
        if (lastError instanceof TimeoutException) {
            return "调用超时未返回";
        }
        return lastError.getClass().getSimpleName();
    }

    private void trace(NodeTask<?> task, String phase, String detail) {
        if (!task.isTraceEnabled()) {
            return;
        }
        NodeTraceNotifier.send(task.getEmitter(), task.getSessionId(), task.getStep(),
                task.getDisplayName() == null ? task.getNodeKey() : task.getDisplayName(), phase, detail);
    }

    private long elapsed(long startedAt) {
        return System.currentTimeMillis() - startedAt;
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private Throwable unwrap(ExecutionException e) {
        Throwable cause = e.getCause();
        // callWithTimeout 里把受检异常包成 IllegalStateException，这里拆回原始类型便于重试判定
        if (cause instanceof IllegalStateException && cause.getCause() != null) {
            return cause.getCause();
        }
        return cause == null ? e : cause;
    }
}
