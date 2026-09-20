package cn.bugstack.ai.domain.agent.service.support.tree;

/**
 * 受理策略处理。
 *
 * 该 starter 被打包成 20MB 的 shaded fat jar（内嵌完整 Spring Boot / Tomcat / Jackson /
 * logback / slf4j / commons-lang3 / lombok），会按 classpath 顺序遮蔽项目自身的日志实现，
 * 历史上曾导致 {@code AbstractMethodError: RootLogLevelConfigurator ... configure(LoggerContext)}。
 * 故将其内联为项目自有代码：语义完全等价，同时消除该依赖风险。
 *
 * @param <T> 入参类型
 * @param <D> 上下文参数
 * @param <R> 返参类型
 */
public interface StrategyHandler<T, D, R> {

    /**
     * 空策略。链路终点的默认实现，{@link #apply} 恒返回 {@code null}。
     */
    StrategyHandler DEFAULT = (T, D) -> null;

    R apply(T requestParameter, D dynamicContext) throws Exception;

}
