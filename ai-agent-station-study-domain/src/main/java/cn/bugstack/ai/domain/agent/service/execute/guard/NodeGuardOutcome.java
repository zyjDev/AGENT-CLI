package cn.bugstack.ai.domain.agent.service.execute.guard;

/**
 * 节点执行结局
 */
public enum NodeGuardOutcome {

    /** 正常成功（含重试后成功） */
    SUCCESS,

    /** 已降级：拿到的是兜底值，不是模型真实产出 */
    DEGRADED,

    /** 已跳过：本节点没有产出，调用方应跳过后续处理 */
    SKIPPED,

    /** 全局预算耗尽：不是节点本身慢，而是前面把时间花光了 */
    BUDGET_EXHAUSTED;

    public boolean hasRealValue() {
        return this == SUCCESS || this == DEGRADED;
    }
}
