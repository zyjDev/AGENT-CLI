package cn.bugstack.ai.domain.agent.service.context;

import cn.bugstack.ai.domain.agent.model.valobj.ContextBudgetVO;
import cn.bugstack.ai.domain.agent.model.valobj.TokenUsageSnapshotVO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
     */
    private final Map<String, TokenUsageSnapshotVO> lastUsage = new ConcurrentHashMap<>();

    @Resource
    private ITokenCounter tokenCounter;

    @Resource
    private ContextBudgetVO contextBudget;

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
        return conversationId == null ? null : lastUsage.get(conversationId);
    }

    /**
     * 清理某会话的用量记录
     */
    public void clear(String conversationId) {
        if (conversationId != null) {
            lastUsage.remove(conversationId);
        }
    }

}
