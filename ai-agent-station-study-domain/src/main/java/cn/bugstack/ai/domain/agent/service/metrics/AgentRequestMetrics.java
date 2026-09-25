package cn.bugstack.ai.domain.agent.service.metrics;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Agent 执行请求的当日指标（内存态，无持久化、无 DDL）。
 * <p>
 * 背景：管理端数据统计接口原先把「今日请求数 / 成功率」写死成 0 和 95.5，是编造的数据。
 * 项目自有表里也没有任何请求/会话日志表，无法从库里统计，故改用内存计数器给出真实值。
 * <p>
 * 口径说明（有意为之，勿按「业务成功率」理解）：
 * <ul>
 *   <li>requests：{@code IAgentDispatchService#dispatch} 被调用的次数。含 HTTP SSE 入口
 *       （/api/v1/agent/auto_agent）与定时任务（AgentTaskJob）两条来源。</li>
 *   <li>successes：整条执行链路未向外抛异常、正常返回的次数。</li>
 *   <li>failures：抛异常或执行被线程池拒绝的次数。</li>
 *   <li>successRate = successes / (successes + failures)。分母刻意不含 requests ——
 *       万一某个请求的结局没被记上（进程重启、线程被强杀），也不会把成功率算歪。</li>
 * </ul>
 * <p>
 * 已知局限：多实例部署时各进程各算一份（本机单实例运行，够用）；重启后当日计数清零。
 * 若要跨重启、跨实例，需落库或接 Micrometer，不在本次重构范围内。
 *
 * @author bugstack.cn
 */
@Slf4j
@Component
public class AgentRequestMetrics {

    /**
     * 当天的计数器。跨日时整体替换为一个新实例，避免给每个计数再套一层日期判断。
     */
    private static final class DailyCounter {

        private final LocalDate date;
        private final AtomicLong requests = new AtomicLong();
        private final AtomicLong successes = new AtomicLong();
        private final AtomicLong failures = new AtomicLong();

        private DailyCounter(LocalDate date) {
            this.date = date;
        }
    }

    private final AtomicReference<DailyCounter> current = new AtomicReference<>(new DailyCounter(LocalDate.now()));

    /**
     * 取当天计数器；跨日则换新。仅在日期变化时才进入同步块，常规路径只是一次 get + equals。
     */
    private DailyCounter current() {
        DailyCounter counter = current.get();
        if (counter.date.equals(LocalDate.now())) {
            return counter;
        }
        synchronized (this) {
            counter = current.get();
            LocalDate today = LocalDate.now();
            if (!counter.date.equals(today)) {
                counter = new DailyCounter(today);
                current.set(counter);
                log.info("Agent 请求指标跨日重置：date={}", today);
            }
            return counter;
        }
    }

    /**
     * 记录一次请求进入执行链路。
     */
    public void recordRequest() {
        current().requests.incrementAndGet();
    }

    /**
     * 记录一次执行正常结束（未抛异常）。
     */
    public void recordSuccess() {
        current().successes.incrementAndGet();
    }

    /**
     * 记录一次执行失败（抛异常 / 被线程池拒绝）。
     */
    public void recordFailure() {
        current().failures.incrementAndGet();
    }

    // ── 以下为节点级埋点（治理新增） ─────────────────────────────────
    //
    // 原实现只有整条链路的成败计数，「到底是哪个节点慢」完全靠翻日志。
    // 这里按 nodeKey 记：调用次数 / 成功次数 / 超时次数 / 失败次数 / 预算耗尽次数 / 累计耗时 / 最大耗时，
    // 足以算出 P50 之外的粗粒度均值与最大值，用来定位瓶颈节点与调超时。
    // 注意：仍是内存态、进程重启清零，与上面的整体计数口径一致（多实例需接 Micrometer）。

    private final ConcurrentMap<String, NodeCounter> nodeCounters = new ConcurrentHashMap<>();

    /**
     * 单个节点的累计计数器
     */
    public static final class NodeCounter {
        private final AtomicLong invokes = new AtomicLong();
        private final AtomicLong successes = new AtomicLong();
        private final AtomicLong timeouts = new AtomicLong();
        private final AtomicLong failures = new AtomicLong();
        private final AtomicLong budgetHits = new AtomicLong();
        private final AtomicLong totalCostMs = new AtomicLong();
        private final AtomicLong maxCostMs = new AtomicLong();

        public long invokes() {
            return invokes.get();
        }

        public long successes() {
            return successes.get();
        }

        public long timeouts() {
            return timeouts.get();
        }

        public long failures() {
            return failures.get();
        }

        public long budgetHits() {
            return budgetHits.get();
        }

        public long totalCostMs() {
            return totalCostMs.get();
        }

        public long maxCostMs() {
            return maxCostMs.get();
        }

        /** 平均耗时；没样本时返回 0L */
        public long avgCostMs() {
            long count = successes.get() + failures.get();
            return count == 0 ? 0L : totalCostMs.get() / count;
        }

        /** 超时率（百分比，1 位小数） */
        public double timeoutRate() {
            long total = invokes.get();
            return total == 0 ? 0d : Math.round(timeouts.get() * 1000.0 / total) / 10.0;
        }
    }

    public void recordNodeSuccess(String nodeKey, long costMs) {
        NodeCounter counter = counterOf(nodeKey);
        counter.invokes.incrementAndGet();
        counter.successes.incrementAndGet();
        counter.totalCostMs.addAndGet(costMs);
        counter.maxCostMs.updateAndGet(prev -> Math.max(prev, costMs));
    }

    public void recordNodeFailure(String nodeKey, long costMs) {
        NodeCounter counter = counterOf(nodeKey);
        counter.invokes.incrementAndGet();
        counter.failures.incrementAndGet();
        counter.totalCostMs.addAndGet(costMs);
        counter.maxCostMs.updateAndGet(prev -> Math.max(prev, costMs));
    }

    public void recordNodeTimeout(String nodeKey) {
        counterOf(nodeKey).timeouts.incrementAndGet();
    }

    public void recordNodeBudgetExhausted(String nodeKey) {
        counterOf(nodeKey).budgetHits.incrementAndGet();
    }

    private NodeCounter counterOf(String nodeKey) {
        return nodeCounters.computeIfAbsent(nodeKey == null ? "unknown" : nodeKey, key -> new NodeCounter());
    }

    /**
     * 节点级统计快照（供管理端/日志排查使用）
     */
    public Map<String, NodeCounter> nodeSnapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(nodeCounters));
    }

    /**
     * 当日请求数。
     */
    public long getTodayRequestCount() {
        return current().requests.get();
    }

    /**
     * 当日成功率（百分比，保留 1 位小数）。没有任何已记录结局时返回 0.0。
     */
    public double getSuccessRate() {
        DailyCounter counter = current();
        long success = counter.successes.get();
        long total = success + counter.failures.get();
        if (total == 0) {
            return 0.0;
        }
        return Math.round(success * 1000.0 / total) / 10.0;
    }

}
