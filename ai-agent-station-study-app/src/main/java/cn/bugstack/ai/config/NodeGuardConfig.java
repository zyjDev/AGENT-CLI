package cn.bugstack.ai.config;

import cn.bugstack.ai.domain.agent.model.valobj.NodeGuardPolicyVO;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 节点治理配置
 * <p>
 * 职责：
 * 1. 把 yml 配置汇总为 domain 层可直接注入的 {@link NodeGuardPolicyVO}；
 * 2. 提供两个专用线程池 —— 节点执行池与心跳调度池。
 *
 * @author bugstack.cn
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(NodeGuardProperties.class)
public class NodeGuardConfig {

    private ExecutorService nodeGuardExecutor;

    private ScheduledExecutorService nodeHeartbeatScheduler;

    @Bean
    public NodeGuardPolicyVO nodeGuardPolicyVO(NodeGuardProperties properties) {
        // 逐节点超时：以默认表为基础，yml 里配置了才覆盖，避免漏配导致某些节点没有硬超时
        Map<String, Long> nodeTimeouts = new HashMap<>(NodeGuardPolicyVO.defaultNodeTimeouts());
        if (properties.getNodeTimeoutMs() != null) {
            nodeTimeouts.putAll(properties.getNodeTimeoutMs());
        }

        NodeGuardPolicyVO policy = NodeGuardPolicyVO.builder()
                .enabled(properties.getEnabled() == null || properties.getEnabled())
                .totalBudgetMs(properties.getTotalBudgetMs() == null ? 240_000L : properties.getTotalBudgetMs())
                .defaultNodeTimeoutMs(properties.getDefaultNodeTimeoutMs() == null ? 30_000L : properties.getDefaultNodeTimeoutMs())
                .nodeTimeoutMs(nodeTimeouts)
                .maxRetries(properties.getMaxRetries() == null ? 2 : Math.max(0, properties.getMaxRetries()))
                .retryBackoffBaseMs(properties.getRetryBackoffBaseMs() == null ? 500L : properties.getRetryBackoffBaseMs())
                .heartbeatIntervalMs(properties.getHeartbeatIntervalMs() == null ? 15_000L : properties.getHeartbeatIntervalMs())
                .stepIntervalMs(properties.getStepIntervalMs() == null ? 300L : properties.getStepIntervalMs())
                .build();

        log.info("节点治理配置加载完成：enabled={}, totalBudget={}ms, nodeTimeouts={}, maxRetries={}, heartbeat={}ms",
                policy.isEnabled(), policy.getTotalBudgetMs(), policy.getNodeTimeoutMs(),
                policy.getMaxRetries(), policy.getHeartbeatIntervalMs());
        return policy;
    }

    /**
     * 节点执行线程池：专供 NodeGuardEngine 做带超时的模型调用包装。
     * <p>
     * 必须与业务线程池 threadPoolExecutor 分开 —— 节点任务被提交后，调用线程会同步等待它的 Future，
     * 如果两者共用一个池且该池跑满，提交的任务要排队，而排着队的任务又在等别人线程，直接死锁。
     * <p>
     * 拒绝策略用 Abort：CallerRuns 会让任务在调用线程上执行，等于绕开了 Future.get(timeout) 的超时保护。
     */
    @Bean(name = "nodeGuardExecutor")
    public ExecutorService nodeGuardExecutor() {
        this.nodeGuardExecutor = new ThreadPoolExecutor(
                8, 32, 60L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(256),
                runnable -> {
                    Thread thread = new Thread(runnable, "node-guard-" + System.nanoTime());
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy());
        return this.nodeGuardExecutor;
    }

    /**
     * 心跳调度池：只做定时 send(": ping")，2 个线程足够
     */
    @Bean(name = "nodeHeartbeatScheduler")
    public ScheduledExecutorService nodeHeartbeatScheduler() {
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(2, runnable -> {
            Thread thread = new Thread(runnable, "node-heartbeat-" + System.nanoTime());
            thread.setDaemon(true);
            return thread;
        });
        executor.setRemoveOnCancelPolicy(true);
        this.nodeHeartbeatScheduler = executor;
        return this.nodeHeartbeatScheduler;
    }

    @PreDestroy
    public void shutdown() {
        if (nodeGuardExecutor != null && !nodeGuardExecutor.isShutdown()) {
            nodeGuardExecutor.shutdown();
            log.info("节点执行线程池已关闭");
        }
        if (nodeHeartbeatScheduler != null && !nodeHeartbeatScheduler.isShutdown()) {
            nodeHeartbeatScheduler.shutdown();
            log.info("心跳调度线程池已关闭");
        }
    }
}
