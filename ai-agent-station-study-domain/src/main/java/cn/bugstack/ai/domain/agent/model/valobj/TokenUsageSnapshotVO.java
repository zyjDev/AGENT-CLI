package cn.bugstack.ai.domain.agent.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 真实 token 用量快照
 * <p>
 * 记录一次模型调用前后的「本地估算」与「模型返回的真实 usage」，
 * 用于校准估算偏差。
 *
 * @author xiaofuge bugstack.cn @小傅哥
 * 2026/09/14
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TokenUsageSnapshotVO {

    /**
     * 会话 ID
     */
    private String conversationId;

    /**
     * 本地估算的 prompt token
     */
    private int estimatedTokens;

    /**
     * 模型返回的真实 prompt token
     */
    private int promptTokens;

    /**
     * 模型返回的真实 completion token
     */
    private int completionTokens;

    /**
     * 本次调用是否挂载了工具回调（工具链路 usage 会低估，不参与校准）
     */
    private boolean toolCallPresent;

    /**
     * 记录时间（毫秒时间戳）
     */
    private long recordTime;

    /**
     * 真实 prompt token / 本地估算，用于观察偏差
     */
    public double getObservedRatio() {
        if (estimatedTokens <= 0) {
            return 0d;
        }
        return (double) promptTokens / (double) estimatedTokens;
    }

}
