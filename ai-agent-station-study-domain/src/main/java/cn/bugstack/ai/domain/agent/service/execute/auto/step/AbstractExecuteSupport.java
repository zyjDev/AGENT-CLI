package cn.bugstack.ai.domain.agent.service.execute.auto.step;

import cn.bugstack.ai.domain.agent.adapter.repository.IAgentRepository;
import cn.bugstack.ai.domain.agent.model.entity.AutoAgentExecuteResultEntity;
import cn.bugstack.ai.domain.agent.model.entity.ExecuteCommandEntity;
import cn.bugstack.ai.domain.agent.model.valobj.ContextBudgetVO;
import cn.bugstack.ai.domain.agent.model.valobj.enums.AiAgentEnumVO;
import cn.bugstack.ai.domain.agent.service.context.ContextBudgetSupport;
import cn.bugstack.ai.domain.agent.service.context.IContextSummarizer;
import cn.bugstack.ai.domain.agent.service.context.ITokenCounter;
import cn.bugstack.ai.domain.agent.service.execute.auto.step.factory.DefaultAutoAgentExecuteStrategyFactory;
import cn.bugstack.wrench.design.framework.tree.AbstractMultiThreadStrategyRouter;
import com.alibaba.fastjson.JSON;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.ApplicationContext;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

/**
 * @author xiaofuge bugstack.cn @小傅哥
 * 2025/7/27 16:48
 */
public abstract class AbstractExecuteSupport extends AbstractMultiThreadStrategyRouter<ExecuteCommandEntity, DefaultAutoAgentExecuteStrategyFactory.DynamicContext, String> {

    private final Logger log = LoggerFactory.getLogger(AbstractExecuteSupport.class);

    /**
     * 执行历史压缩后可用的最小预算，防止配置过小导致反复压缩却不缩小
     */
    private static final int MIN_EFFECTIVE_HISTORY_BUDGET = 256;

    @Resource
    protected ApplicationContext applicationContext;

    @Resource
    protected IAgentRepository repository;

    @Resource
    protected ITokenCounter tokenCounter;

    @Resource
    protected IContextSummarizer contextSummarizer;

    @Resource
    protected ContextBudgetVO contextBudget;

    public static final String CHAT_MEMORY_CONVERSATION_ID_KEY = "chat_memory_conversation_id";
    public static final String CHAT_MEMORY_RETRIEVE_SIZE_KEY = "chat_memory_response_size";

    @Override
    protected void multiThread(ExecuteCommandEntity requestParameter, DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext) throws ExecutionException, InterruptedException, TimeoutException {

    }

    protected ChatClient getChatClientByClientId(String clientId) {
        return getBean(AiAgentEnumVO.AI_CLIENT.getBeanName(clientId));
    }

    protected <T> T getBean(String beanName) {
        return (T) applicationContext.getBean(beanName);
    }

    /**
     * 通用的SSE结果发送方法
     * @param dynamicContext 动态上下文
     * @param result 要发送的结果实体
     */
    protected void sendSseResult(DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext, 
                                AutoAgentExecuteResultEntity result) {
        try {
            ResponseBodyEmitter emitter = dynamicContext.getValue("emitter");
            if (emitter != null) {
                // 发送SSE格式的数据
                String sseData = "data: " + JSON.toJSONString(result) + "\n\n";
                emitter.send(sseData);
            }
        } catch (IOException e) {
            log.error("发送SSE结果失败：{}", e.getMessage(), e);
        }
    }

    // ==================================================================================
    // 执行历史的 token 预算管理（B 套）
    //
    // 改造前：executionHistory 是 StringBuilder，每步 append 后无界增长，一次请求内可撑爆上下文。
    // 改造后：超出 token 预算时把较早的步骤压成摘要（historySummary），只保留最近 N 步完整记录。
    // 注意：prompt 模板与 String.format 的占位符数量都不变——composeHistory() 返回单个字符串，
    //      直接替换原来 executionHistory.toString() 的位置。
    // ==================================================================================

    /**
     * 追加一步执行记录，并在超出 token 预算时触发压缩（B 套统一入口）
     *
     * @param requestParameter 请求参数
     * @param dynamicContext   动态上下文
     * @param record           本步的执行记录文本
     */
    protected void appendHistory(ExecuteCommandEntity requestParameter,
                                 DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext,
                                 String record) {
        if (dynamicContext.getExecutionHistory() == null) {
            dynamicContext.setExecutionHistory(new StringBuilder());
        }
        dynamicContext.getExecutionHistory().append(record);
        dynamicContext.setExecutedSteps(dynamicContext.getExecutedSteps() + 1);
        compactHistoryIfNeeded(requestParameter, dynamicContext);
    }

    /**
     * 执行历史超预算时，把较早的步骤压成摘要，只保留最近 N 步完整记录
     */
    protected void compactHistoryIfNeeded(ExecuteCommandEntity requestParameter,
                                          DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext) {
        StringBuilder history = dynamicContext.getExecutionHistory();
        if (history == null || history.length() == 0) {
            return;
        }

        String summary = dynamicContext.getHistorySummary();
        int effectiveBudget = Math.max(MIN_EFFECTIVE_HISTORY_BUDGET,
                contextBudget.getHistoryTokenBudget() - contextBudget.getReserveTokens());

        int estimated = tokenCounter.estimate(summary == null ? "" : summary)
                + tokenCounter.estimate(history.toString());
        if (estimated <= effectiveBudget * contextBudget.getHistoryTriggerRatio()) {
            return;
        }

        log.info("🧮 执行历史触发压缩：step={}, 估算 {} token, 有效预算 {} token",
                dynamicContext.getStep(), estimated, effectiveBudget);

        List<String> segments = ContextBudgetSupport.splitHistory(history.toString());

        // 单步记录就超预算：已无法再拆分，硬截断，避免「反复压缩却不缩小」
        if (segments.size() <= 1) {
            String truncated = ContextBudgetSupport.hardTruncate(tokenCounter, history.toString(), effectiveBudget);
            dynamicContext.setExecutionHistory(new StringBuilder(truncated));
            log.warn("⚠️ 单步记录即超预算，已硬截断：step={}", dynamicContext.getStep());
            return;
        }

        int keep = Math.max(1, Math.min(contextBudget.getHistoryKeepSteps(), segments.size() - 1));
        String headText = String.join("", segments.subList(0, segments.size() - keep));
        String tailText = String.join("", segments.subList(segments.size() - keep, segments.size()));

        String newSummary = contextSummarizer.summarizeExecution(
                requestParameter.getSessionId(), summary, headText, contextBudget.getMaxSummaryTokens());

        if (newSummary == null || newSummary.isBlank()) {
            // 摘要失败：只丢弃被挤出的窗口，保留旧摘要
            newSummary = summary;
            log.warn("⚠️ 执行历史摘要不可用，仅做窗口裁剪：step={}", dynamicContext.getStep());
        }

        dynamicContext.setHistorySummary(newSummary);
        dynamicContext.setExecutionHistory(new StringBuilder(tailText));

        log.info("✅ 执行历史压缩完成：摘要 {} token + 近期窗口 {} token",
                tokenCounter.estimate(newSummary == null ? "" : newSummary),
                tokenCounter.estimate(tailText));
    }

    /**
     * 供 Step1 / Step4 取用：早期摘要 + 近期完整记录
     * <p>
     * 返回值直接替换原来 {@code executionHistory.toString()} 的位置，prompt 模板占位符数量不变。
     *
     * @return 可直接注入 prompt 的历史文本；无历史时返回 "[首次执行]"
     */
    protected String composeHistory(DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext) {
        String summary = dynamicContext.getHistorySummary();
        StringBuilder history = dynamicContext.getExecutionHistory();

        boolean noSummary = summary == null || summary.isBlank();
        boolean noHistory = history == null || history.length() == 0;
        if (noSummary && noHistory) {
            return "[首次执行]";
        }

        StringBuilder sb = new StringBuilder();
        if (!noSummary) {
            sb.append("=== 早期步骤摘要（已压缩） ===\n").append(summary).append("\n\n");
        }
        if (!noHistory) {
            sb.append("=== 最近步骤完整记录 ===\n").append(history);
        }
        return sb.toString();
    }

}
