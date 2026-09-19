package cn.bugstack.ai.domain.agent.service.support.tree;

/**
 * 策略映射器：决定下一个待执行的策略处理器。
 *
 *
 * @param <T> 入参类型
 * @param <D> 上下文参数
 * @param <R> 返参类型
 */
public interface StrategyMapper<T, D, R> {

    /**
     * 获取待执行策略。
     *
     * @param requestParameter 入参
     * @param dynamicContext   上下文
     * @return 下一个策略处理器；返回 {@code null} 时由 router 落到 defaultStrategyHandler
     * @throws Exception 异常
     */
    StrategyHandler<T, D, R> get(T requestParameter, D dynamicContext) throws Exception;

}
