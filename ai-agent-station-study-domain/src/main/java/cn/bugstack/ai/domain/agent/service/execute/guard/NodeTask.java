package cn.bugstack.ai.domain.agent.service.execute.guard;

import cn.bugstack.ai.domain.agent.model.valobj.NodeGuardPolicyVO;
import lombok.Builder;
import lombok.Data;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

import java.util.concurrent.Callable;
import java.util.function.Supplier;

/**
 * 节点治理请求描述
 * <p>
 * 关键点：{@code callable} 是「节点内那一次模型调用」，而不是整个节点。
 * 重试时重跑的也就是这一行 —— 节点里已经做过的本地解析、上下文写入不会被重复执行。
 *
 * @param <T> 节点产出类型
 */
@Data
@Builder
public class NodeTask<T> {

    /** 节点 key，用于取超时配置与指标维度，见 {@link NodeGuardPolicyVO.NodeKeys} */
    private String nodeKey;

    /** 展示名，写进 SSE 事件与日志 */
    private String displayName;

    /** 真正会阻塞的那次调用（通常是 chatClient.prompt(...).call().content()） */
    private Callable<T> callable;

    /** FALLBACK 模式下的兜底产出 */
    private Supplier<T> fallback;

    /** 降级策略，默认快速失败 */
    @Builder.Default
    private NodeDegradeMode degradeMode = NodeDegradeMode.FAIL_FAST;

    /** 该调用是否幂等、可安全重试。false 时用 maxAttempts=1 */
    @Builder.Default
    private boolean retryable = true;

    /** 全局预算 */
    private ExecutionBudget budget;

    /** SSE 发射器，用于节点级进度/心跳事件；可为 null（如定时任务链路） */
    private ResponseBodyEmitter emitter;

    private String sessionId;

    private Integer step;

    /** 是否推送节点级 trace 事件 */
    @Builder.Default
    private boolean traceEnabled = true;
}
