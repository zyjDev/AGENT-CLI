package cn.bugstack.ai.domain.agent.service.dispatch;

import cn.bugstack.ai.domain.agent.adapter.repository.IAgentRepository;
import cn.bugstack.ai.domain.agent.model.entity.AutoAgentExecuteResultEntity;
import cn.bugstack.ai.domain.agent.model.entity.ExecuteCommandEntity;
import cn.bugstack.ai.domain.agent.model.valobj.AiAgentVO;
import cn.bugstack.ai.domain.agent.service.IAgentDispatchService;
import cn.bugstack.ai.domain.agent.service.IExecuteStrategy;
import cn.bugstack.ai.domain.agent.service.metrics.AgentRequestMetrics;
import cn.bugstack.ai.types.exception.BizException;
import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

import jakarta.annotation.Resource;
import java.util.Map;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Agent 服务接口
 *
 */
@Slf4j
@Service
public class AgentDispatchDispatchService implements IAgentDispatchService {

    @Resource
    private Map<String, IExecuteStrategy> executeStrategyMap;

    @Resource
    private IAgentRepository repository;

    @Resource
    private ThreadPoolExecutor threadPoolExecutor;

    /**
     * 当日执行指标（内存态）。埋点位置选在这里，因为它是 Auto / Flow / Fixed 三条链路的唯一公共入口。
     * 统计口径见 {@link AgentRequestMetrics} 的类注释。
     */
    @Resource
    private AgentRequestMetrics agentRequestMetrics;

    /**
     * 调度执行命令
     * @param requestParameter 执行命令实体
     * @param emitter 响应体发射器
     * @throws Exception 异常
     */
    @Override
    public void dispatch(ExecuteCommandEntity requestParameter, ResponseBodyEmitter emitter) throws Exception {
        agentRequestMetrics.recordRequest();

        AiAgentVO aiAgentVO = repository.queryAiAgentByAgentId(requestParameter.getAiAgentId());
        if (null == aiAgentVO) {
            agentRequestMetrics.recordFailure();
            throw new BizException("智能体不存在: " + requestParameter.getAiAgentId());
        }

        String strategy = aiAgentVO.getStrategy();
        IExecuteStrategy executeStrategy = executeStrategyMap.get(strategy);
        if (null == executeStrategy) {
            agentRequestMetrics.recordFailure();
            throw new BizException("不存在的执行策略类型 strategy:" + strategy);
        }

        // 背压：队列快满时提前失败。
        // 慢接口的典型发展路径是「节点变慢 → 业务线程被占满 → 任务堆积到队列 → 新请求排队几十秒才启动 →
        // 用户觉得更慢 → 疯狂重试 → 彻底雪崩」。与其让用户排队到超时，不如当场拒绝并给出明确原因。
        int remainingCapacity = threadPoolExecutor.getQueue().remainingCapacity();
        int queueSize = threadPoolExecutor.getQueue().size() + remainingCapacity;
        if (queueSize > 0 && remainingCapacity <= queueSize * 0.1) {
            agentRequestMetrics.recordFailure();
            log.warn("执行线程池积压，拒绝本次请求：queue={}, remaining={}", queueSize, remainingCapacity);
            throw new BizException("系统繁忙（执行队列已积压 " + queueSize + " 个任务），请稍后重试");
        }

        // 3. 异步执行AutoAgent
        try {
            threadPoolExecutor.execute(() -> {
                try {
                    executeStrategy.execute(requestParameter, emitter);
                    agentRequestMetrics.recordSuccess();
                } catch (Exception e) {
                    agentRequestMetrics.recordFailure();
                    log.error("AutoAgent执行异常：{}", e.getMessage(), e);
                    // 规范化 error 事件：原来是裸文本（"执行异常：xxx"），前端按 SSE data + JSON 解析会失败。
                    // 统一走 createErrorResult，让「节点超时快速失败」这类异常也能被前端正常展示。
                    try {
                        AutoAgentExecuteResultEntity errorResult = AutoAgentExecuteResultEntity
                                .createErrorResult("执行异常：" + e.getMessage(), requestParameter.getSessionId());
                        emitter.send("data: " + JSON.toJSONString(errorResult) + "\n\n");
                    } catch (Exception ex) {
                        log.error("发送异常信息失败：{}", ex.getMessage(), ex);
                    }
                } finally {
                    try {
                        emitter.complete();
                    } catch (Exception e) {
                        log.error("完成流式输出失败：{}", e.getMessage(), e);
                    }
                }
            });
        } catch (RejectedExecutionException e) {
            // 线程池打满：任务从未开始执行，必须记失败，否则这次请求在指标里只有分子没有结局
            agentRequestMetrics.recordFailure();
            throw e;
        }

    }

}
