package cn.bugstack.ai.domain.agent.service.support.tree;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

/**
 * 异步资源加载策略路由。
 *
 * <p><b>注意</b>：项目内两个 {@code AbstractExecuteSupport} 子类（auto / flow 各一份）都把
 * {@code multiThread()} 实现为<b>空方法体</b>，即「多线程异步加载」这一能力当前实际未被使用。
 * 此处保留模板结构是为了把改动面压到最小（不改变任何调用方代码），并非暗示该能力仍在生效。
 *
 * @param <T> 入参类型
 * @param <D> 上下文参数
 * @param <R> 返参类型
 */
public abstract class AbstractMultiThreadStrategyRouter<T, D, R> implements StrategyMapper<T, D, R>, StrategyHandler<T, D, R> {

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

    @Override
    public R apply(T requestParameter, D dynamicContext) throws Exception {
        // 异步加载数据
        multiThread(requestParameter, dynamicContext);
        // 业务流程受理
        return doApply(requestParameter, dynamicContext);
    }

    /**
     * 异步加载数据
     */
    protected abstract void multiThread(T requestParameter, D dynamicContext) throws ExecutionException, InterruptedException, TimeoutException;

    /**
     * 业务流程受理
     */
    protected abstract R doApply(T requestParameter, D dynamicContext) throws Exception;

    public StrategyHandler<T, D, R> getDefaultStrategyHandler() {
        return defaultStrategyHandler;
    }

    public void setDefaultStrategyHandler(StrategyHandler<T, D, R> defaultStrategyHandler) {
        this.defaultStrategyHandler = defaultStrategyHandler;
    }

}
