package cn.bugstack.ai.config;

import cn.bugstack.ai.domain.agent.model.valobj.ContextBudgetVO;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 上下文预算配置
 * <p>
 * 职责：
 * 1. 把 yml 的 {@link ContextBudgetProperties} 汇总为 domain 层可直接注入的 {@link ContextBudgetVO} Bean；
 * 2. 提供摘要专用线程池——摘要调用会阻塞等待模型，放在业务线程池里会拖累正常请求。
 *
 * @author xiaofuge bugstack.cn @小傅哥
 * 2026/09/14
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(ContextBudgetProperties.class)
public class ContextBudgetConfig {

    private ExecutorService contextSummaryExecutor;

    /**
     * 全局默认预算；DB ai_client_advisor.ext_param 可逐项覆盖
     */
    @Bean
    public ContextBudgetVO contextBudgetVO(ContextBudgetProperties properties) {
        ContextBudgetProperties.Memory memory = properties.getMemory();
        ContextBudgetProperties.History history = properties.getHistory();
        ContextBudgetProperties.Summary summary = properties.getSummary();
        ContextBudgetProperties.Calibration calibration = properties.getCalibration();
        ContextBudgetProperties.Cache cache = properties.getCache();

        ContextBudgetVO budget = ContextBudgetVO.builder()
                .memoryTokenBudget(nvl(memory.getTokenBudget(), 3000))
                .memoryTriggerRatio(nvl(memory.getTriggerRatio(), 0.8d))
                .keepRecentMessages(nvl(memory.getKeepRecentMessages(), 6))
                .reserveTokens(nvl(memory.getReserveTokens(), 500))
                .maxSummaryTokens(nvl(memory.getMaxSummaryTokens(), 400))
                .maxMessages(nvl(memory.getMaxMessages(), 0))
                .historyTokenBudget(nvl(history.getTokenBudget(), 2500))
                .historyTriggerRatio(nvl(history.getTriggerRatio(), 0.8d))
                .historyKeepSteps(nvl(history.getKeepSteps(), 2))
                .summaryMode(nvl(summary.getMode(), "LLM"))
                .summaryModelBeanName(nvl(summary.getModelBeanName(), "openAiChatModel"))
                .summaryTimeoutMs(nvl(summary.getTimeoutMs(), 8000L))
                .truncateCharsPerStep(nvl(summary.getTruncateCharsPerStep(), 300))
                .calibrationEnabled(nvl(calibration.getEnabled(), true))
                .calibrationAlpha(nvl(calibration.getAlpha(), 0.3d))
                .calibrationMinSamples(nvl(calibration.getMinSamples(), 2))
                .calibrationIncludeToolCalls(nvl(calibration.getIncludeToolCalls(), false))
                // 缓存治理：按会话维度缓存的容量约束，避免这些 Map 无界增长
                .usageCacheMaxConversations(nvl(cache.getUsageMaxConversations(), 10000))
                .usageCacheTtlSeconds(nvl(cache.getUsageTtlSeconds(), 3600L))
                .memoryCacheMaxConversations(nvl(cache.getMemoryMaxConversations(), 5000))
                .build();

        log.info("上下文预算配置加载完成：memoryBudget={} token, historyBudget={} token, summaryMode={}, calibration={}",
                budget.getMemoryTokenBudget(), budget.getHistoryTokenBudget(),
                budget.getSummaryMode(), budget.isCalibrationEnabled());
        return budget;
    }

    /**
     * 摘要专用线程池：小并发、有界队列，避免摘要调用抢占业务线程
     */
    @Bean(name = "contextSummaryExecutor")
    public ExecutorService contextSummaryExecutor() {
        this.contextSummaryExecutor = new ThreadPoolExecutor(
                2, 4, 60L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(64),
                runnable -> {
                    Thread thread = new Thread(runnable, "context-summary-" + System.nanoTime());
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.CallerRunsPolicy());
        return this.contextSummaryExecutor;
    }

    @PreDestroy
    public void shutdown() {
        if (contextSummaryExecutor != null && !contextSummaryExecutor.isShutdown()) {
            contextSummaryExecutor.shutdown();
            log.info("上下文摘要线程池已关闭");
        }
    }

    private static int nvl(Integer value, int fallback) {
        return value == null ? fallback : value;
    }

    private static long nvl(Long value, long fallback) {
        return value == null ? fallback : value;
    }

    private static double nvl(Double value, double fallback) {
        return value == null ? fallback : value;
    }

    private static boolean nvl(Boolean value, boolean fallback) {
        return value == null ? fallback : value;
    }

    private static String nvl(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }

}
