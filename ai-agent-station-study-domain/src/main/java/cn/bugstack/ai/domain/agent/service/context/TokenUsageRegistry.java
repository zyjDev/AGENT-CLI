package cn.bugstack.ai.domain.agent.service.context;

import cn.bugstack.ai.domain.agent.model.valobj.ContextBudgetVO;
import cn.bugstack.ai.domain.agent.model.valobj.TokenUsageSnapshotVO;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 真实 token 用量登记
 * <p>
 * 本地估算解决「调用前门控」，真实 usage 解决「估算准不准」。
 * 本类把模型返回的真实 usage 登记下来，并驱动 {@link ITokenCounter} 更新校准系数。
 *
 * @author xiaofuge bugstack.cn @小傅哥
 * 2026/09/14
 */
@Slf4j
@Service
public class TokenUsageRegistry {

    /**
     * conversationId -> 最近一次真实用量快照
     * <p>
     * ⚠️ 原实现是无界 {@code ConcurrentHashMap}：会话数增长即内存无界增长，永不淘汰。
     * 本类存的只是「最近一次用量」的旁路观测数据，丢失不影响对话正确性，
     * 因此改为带「上限 + 空闲过期」的 Guava Cache：超出上限按最近最少使用淘汰，
     * 超过 TTL 未访问自动过期。
     */
    private Cache<String, TokenUsageSnapshotVO> lastUsage;

    @Resource
    private ITokenCounter tokenCounter;

    @Resource
    private ContextBudgetVO contextBudget;

    @PostConstruct
    void initUsageCache() {
        this.lastUsage = CacheBuilder.newBuilder()
                .maximumSize(contextBudget.getUsageCacheMaxConversations())
                .expireAfterAccess(Duration.ofSeconds(contextBudget.getUsageCacheTtlSeconds()))
                .build();
        log.info("token 用量登记缓存初始化完成：maxConversations={}, ttlSeconds={}",
                contextBudget.getUsageCacheMaxConversations(), contextBudget.getUsageCacheTtlSeconds());
    }

    /**
     * 登记一次真实用量
     *
     * @param conversationId     会话 ID
     * @param estimated          调用前本地估算的 token
     * @param promptTokens       模型返回的真实 prompt token
     * @param completionTokens   模型返回的真实 completion token
     * @param toolCallPresent    本次调用是否挂载了工具回调
     */
    public void record(String conversationId, int estimated, int promptTokens,
                       int completionTokens, boolean toolCallPresent) {
        if (conversationId == null || conversationId.isBlank()) {
            return;
        }

        lastUsage.put(conversationId, TokenUsageSnapshotVO.builder()
                .conversationId(conversationId)
                .estimatedTokens(estimated)
                .promptTokens(promptTokens)
                .completionTokens(completionTokens)
                .toolCallPresent(toolCallPresent)
                .recordTime(System.currentTimeMillis())
                .build());

        // 工具调用链路里 Spring AI 会多次调用模型，最终 ChatResponse 的 usage 通常只反映最后一次，
        // 与「完整 prompt 的本地估算」不可比，默认不参与校准，避免把系数带偏
        if (toolCallPresent && !contextBudget.isCalibrationIncludeToolCalls()) {
            log.debug("工具调用链路样本，跳过校准：conversationId={}, estimated={}, actual={}",
                    conversationId, estimated, promptTokens);
            return;
        }

        tokenCounter.calibrate(estimated, promptTokens);
    }

    /**
     * 取某会话最近一次真实用量快照
     */
    public TokenUsageSnapshotVO get(String conversationId) {
        return conversationId == null ? null : lastUsage.getIfPresent(conversationId);
    }

    /**
     * 清理某会话的用量记录
     */
    public void clear(String conversationId) {
        if (conversationId != null) {
            lastUsage.invalidate(conversationId);
        }
    }

    /**
     * 当前登记的会话数，仅用于观测
     */
    public long size() {
        return lastUsage.size();
    }

}
