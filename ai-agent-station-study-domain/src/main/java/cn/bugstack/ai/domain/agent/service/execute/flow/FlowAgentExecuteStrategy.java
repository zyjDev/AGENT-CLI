package cn.bugstack.ai.domain.agent.service.execute.flow;

import cn.bugstack.ai.domain.agent.model.entity.AutoAgentExecuteResultEntity;
import cn.bugstack.ai.domain.agent.model.entity.ExecuteCommandEntity;
import cn.bugstack.ai.domain.agent.model.valobj.NodeGuardPolicyVO;
import cn.bugstack.ai.domain.agent.service.IExecuteStrategy;
import cn.bugstack.ai.domain.agent.service.execute.flow.step.factory.DefaultFlowAgentExecuteStrategyFactory;
import cn.bugstack.ai.domain.agent.service.execute.guard.ExecutionBudget;
import cn.bugstack.ai.domain.agent.service.support.tree.StrategyHandler;
import com.alibaba.fastjson.JSON;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

/**
 * 流程执行策略
 */
@Slf4j
@Service("flowAgentExecuteStrategy")
public class FlowAgentExecuteStrategy implements IExecuteStrategy {

    @Resource
    private DefaultFlowAgentExecuteStrategyFactory defaultFlowAgentExecuteStrategyFactory;

    /**
     * 节点治理策略：链路入口处据此创建「全局预算」。
     * 选在这里初始化的原因：它是整条链路的统一入口，deadline 必须从第一个节点开始计时。
     */
    @Resource
    private NodeGuardPolicyVO nodeGuardPolicy;

    @Override
    public void execute(ExecuteCommandEntity executeCommandEntity, ResponseBodyEmitter emitter) throws Exception {
        StrategyHandler<ExecuteCommandEntity, DefaultFlowAgentExecuteStrategyFactory.DynamicContext, String> executeHandler
                = defaultFlowAgentExecuteStrategyFactory.armoryStrategyHandler();
        
        // 创建动态上下文并初始化必要字段
        DefaultFlowAgentExecuteStrategyFactory.DynamicContext dynamicContext = new DefaultFlowAgentExecuteStrategyFactory.DynamicContext();
        dynamicContext.setMaxStep(executeCommandEntity.getMaxStep() != null ? executeCommandEntity.getMaxStep() : 4);
        dynamicContext.setExecutionHistory(new StringBuilder());
        dynamicContext.setCurrentTask(executeCommandEntity.getMessage());
        dynamicContext.setEmitter(emitter);
        // 全局 deadline：后续每个节点的硬超时都会被它二次裁剪
        dynamicContext.setBudget(ExecutionBudget.start(nodeGuardPolicy.getTotalBudgetMs()));
        // 说明：此处原先还有 setValue("sessionId", executeCommandEntity.getSessionId())，
        // 但全项目无任何读取点（各节点均直接使用 request.getSessionId()），属死写入，已移除。
        
        String apply = executeHandler.apply(executeCommandEntity, dynamicContext);
        log.info("流程执行结果:{}，用时 {}ms", apply, dynamicContext.getBudget().elapsedMillis());
        
        // 发送完成标识
        try {
            AutoAgentExecuteResultEntity completeResult = AutoAgentExecuteResultEntity.createCompleteResult(executeCommandEntity.getSessionId());
            // 发送SSE格式的数据
            String sseData = "data: " + JSON.toJSONString(completeResult) + "\n\n";
            emitter.send(sseData);
        } catch (Exception e) {
            log.error("发送完成标识失败：{}", e.getMessage(), e);
        }
    }

}
