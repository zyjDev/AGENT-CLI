package cn.bugstack.ai.domain.agent.service.execute.guard;

/**
 * 节点治理失败且处于 {@link NodeDegradeMode#FAIL_FAST} 时抛出。
 * <p>
 * 继承 RuntimeException：链路上的 {@code doApply} 签名本身就声明抛异常，无需层层改写。
 * 最终由 {@code AgentDispatchDispatchService} 的 catch 统一转成 SSE error 事件，
 * 用户能看到「规划节点超时」而不是「执行步骤失败: null」这种被降维的信息。
 */
public class NodeGuardException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String nodeKey;

    private final NodeGuardOutcome outcome;

    public NodeGuardException(String nodeKey, NodeGuardOutcome outcome, String message) {
        super(message);
        this.nodeKey = nodeKey;
        this.outcome = outcome;
    }

    public NodeGuardException(String nodeKey, NodeGuardOutcome outcome, String message, Throwable cause) {
        super(message, cause);
        this.nodeKey = nodeKey;
        this.outcome = outcome;
    }

    public String getNodeKey() {
        return nodeKey;
    }

    public NodeGuardOutcome getOutcome() {
        return outcome;
    }
}
