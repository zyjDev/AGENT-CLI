package cn.bugstack.ai.domain.agent.service.support.tree;

/**
 * 策略路由抽象类（无异步加载版）。
 *
 * <p>子类实现 {@link StrategyMapper#get} 决定下一个节点，本类提供 {@link #router} 模板方法负责跳转。
 *
 * @param <T> 入参类型
 * @param <D> 上下文参数
 * @param <R> 返参类型
 */
public abstract class AbstractStrategyRouter<T, D, R> implements StrategyMapper<T, D, R>, StrategyHandler<T, D, R> {

    /**
     * 链路终点默认处理器。子类可直接在 {@code get()} 中返回它来表示「链路到此结束」，
     * 例如 {@code Step4LogExecutionSummaryNode#get}。默认值为空策略（恒返回 null）。
     */
    protected StrategyHandler<T, D, R> defaultStrategyHandler = StrategyHandler.DEFAULT;

    /**
     * 路由到 {@link StrategyMapper#get} 返回的下一个策略处理器；为 {@code null} 时落到
     * {@link #defaultStrategyHandler}。
     */
    public R router(T requestParameter, D dynamicContext) throws Exception {
        StrategyHandler<T, D, R> strategyHandler = get(requestParameter, dynamicContext);
        if (null != strategyHandler) {
            return strategyHandler.apply(requestParameter, dynamicContext);
        }
        return defaultStrategyHandler.apply(requestParameter, dynamicContext);
    }

    public StrategyHandler<T, D, R> getDefaultStrategyHandler() {
        return defaultStrategyHandler;
    }

    public void setDefaultStrategyHandler(StrategyHandler<T, D, R> defaultStrategyHandler) {
        this.defaultStrategyHandler = defaultStrategyHandler;
    }

}
