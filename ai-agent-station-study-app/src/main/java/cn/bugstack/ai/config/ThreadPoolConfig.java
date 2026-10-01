package cn.bugstack.ai.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

import java.time.Duration;
import java.util.concurrent.*;

@Slf4j
@EnableAsync
@Configuration
@EnableConfigurationProperties(ThreadPoolConfigProperties.class)
public class ThreadPoolConfig {

    /**
     * 停机时执行池的等待上限：与 {@code spring.lifecycle.timeout-per-shutdown-phase} 共用同一个值，
     * 避免出现「配置说等 30 秒、代码里写死 60 秒」这种两套口径。
     */
    @Value("${spring.lifecycle.timeout-per-shutdown-phase:30s}")
    private Duration shutdownPhaseTimeout = Duration.ofSeconds(30);

    @Bean(destroyMethod = "")
    @ConditionalOnMissingBean(ThreadPoolExecutor.class)
    public ThreadPoolExecutor threadPoolExecutor(ThreadPoolConfigProperties properties) throws ClassNotFoundException, InstantiationException, IllegalAccessException {
        // 实例化策略
        RejectedExecutionHandler handler;
        switch (properties.getPolicy()){
            case "AbortPolicy":
                handler = new ThreadPoolExecutor.AbortPolicy();
                break;
            case "DiscardPolicy":
                handler = new ThreadPoolExecutor.DiscardPolicy();
                break;
            case "DiscardOldestPolicy":
                handler = new ThreadPoolExecutor.DiscardOldestPolicy();
                break;
            case "CallerRunsPolicy":
                handler = new ThreadPoolExecutor.CallerRunsPolicy();
                break;
            default:
                handler = new ThreadPoolExecutor.AbortPolicy();
                break;
        }
        ThreadPoolExecutor executor = new ThreadPoolExecutor(properties.getCorePoolSize(),
                properties.getMaxPoolSize(),
                properties.getKeepAliveTime(),
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(properties.getBlockQueueSize()),
                Executors.defaultThreadFactory(),
                handler);
        // 允许核心线程也被回收：慢节点场景下线程数会长时间顶在 core 上，
        // 平时流量低时这些线程白白占着资源，配 allowCoreThreadTimeOut 可以让它们随 keepAlive 退出。
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }

    /**
     * 停机时把执行池收干净（2026-09-30 加固）。
     *
     * <p><b>为什么需要单独一个 Bean</b>：这个池是 {@code @Bean} 返回的对象，注解加不到它身上；
     * 而 Spring 对 {@code ThreadPoolExecutor} 会「推断」destroyMethod 为 {@code shutdown()} ——
     * 那只是拒绝新任务，<b>不会等</b>队列里的任务跑完，停机时在途的对话就被直接丢掉了。
     * 所以池本身设成 {@code destroyMethod = ""}，收尾动作统一放这里。
     *
     * <p><b>顺序</b>：本 Bean 依赖执行池 → 销毁时先于执行池执行，正好在这个窗口里做
     * {@code shutdown() + awaitTermination()}；此时 {@code server.shutdown=graceful}
     * 已经先一步停止接收新请求并等在途 HTTP 请求（含 SSE 流）收敛，
     * 剩下的这些就是还需要跑完的异步执行任务。
     *
     * <p><b>等待上限</b>取 {@code spring.lifecycle.timeout-per-shutdown-phase}（默认 30s）。
     * 超时仍不排空就 {@code shutdownNow()} 并明确告警 —— 宁可留下可查的日志，
     * 也不要让停机无限期挂着（发布系统会直接 KILL 掉）。
     */
    @Bean
    public DisposableBean threadPoolExecutorGracefulShutdown(
            @Qualifier("threadPoolExecutor") ThreadPoolExecutor threadPoolExecutor) {
        return () -> {
            long waitMillis = Math.max(0L, shutdownPhaseTimeout.toMillis());
            int queued = threadPoolExecutor.getQueue().size();
            log.info("停机：执行池停止接收新任务并等待在途任务收敛（最多 {} ms，当前排队 {} 个）", waitMillis, queued);
            threadPoolExecutor.shutdown();
            try {
                if (threadPoolExecutor.awaitTermination(waitMillis, TimeUnit.MILLISECONDS)) {
                    log.info("停机：执行池已排空");
                    return;
                }
                // 只报 getQueue().size() 会误导：正在执行的任务不在队列里，日志会写成「剩余 0 个」，
                // 看起来像没事，实际是任务还在跑且马上要被中断。
                log.warn("停机：执行池在 {} ms 内未排空（队列剩余 {} 个，正在执行约 {} 个），强制关闭",
                        waitMillis, threadPoolExecutor.getQueue().size(), threadPoolExecutor.getActiveCount());
                threadPoolExecutor.shutdownNow();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("停机：等待执行池收敛时被中断（队列剩余 {} 个，正在执行约 {} 个），强制关闭",
                        threadPoolExecutor.getQueue().size(), threadPoolExecutor.getActiveCount());
                threadPoolExecutor.shutdownNow();
            }
        };
    }

}
