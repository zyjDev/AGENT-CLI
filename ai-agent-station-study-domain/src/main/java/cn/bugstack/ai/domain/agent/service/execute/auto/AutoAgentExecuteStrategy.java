package cn.bugstack.ai.domain.agent.service.execute.auto;

import cn.bugstack.ai.domain.agent.model.entity.AutoAgentExecuteResultEntity;
import cn.bugstack.ai.domain.agent.model.entity.ExecuteCommandEntity;
import cn.bugstack.ai.domain.agent.model.valobj.NodeGuardPolicyVO;
import cn.bugstack.ai.domain.agent.service.IExecuteStrategy;
import cn.bugstack.ai.domain.agent.service.execute.auto.step.factory.DefaultAutoAgentExecuteStrategyFactory;
import cn.bugstack.ai.domain.agent.service.execute.guard.ExecutionBudget;
import cn.bugstack.ai.domain.agent.service.support.tree.StrategyHandler;
import com.alibaba.fastjson.JSON;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

/**
 * 自动执行策略
 * @author xiaofuge bugstack.cn @小傅哥
 * 2025/8/5 09:49
 */
@Slf4j
@Service("autoAgentExecuteStrategy")
public class AutoAgentExecuteStrategy implements IExecuteStrategy {

    @Resource
    private DefaultAutoAgentExecuteStrategyFactory defaultAutoAgentExecuteStrategyFactory;

    /**
     * 节点治理策略：链路入口创建全局预算。
     * Auto 链路会多轮循环（Analyzer → Executor → Supervisor → 回到 Analyzer），
     * 没有全局预算时总耗时完全不可控。
     */
    @Resource
    private NodeGuardPolicyVO nodeGuardPolicy;

    @Override
    public void execute(ExecuteCommandEntity executeCommandEntity, ResponseBodyEmitter emitter) throws Exception {
        StrategyHandler<ExecuteCommandEntity, DefaultAutoAgentExecuteStrategyFactory.DynamicContext, String> executeHandler
                = defaultAutoAgentExecuteStrategyFactory.armoryStrategyHandler();
        
        // 创建动态上下文并初始化必要字段
        DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext = new DefaultAutoAgentExecuteStrategyFactory.DynamicContext();
        // 如果最大步数未设置则默认为3
        dynamicContext.setMaxStep(executeCommandEntity.getMaxStep() != null ? executeCommandEntity.getMaxStep() : 3);
        dynamicContext.setExecutionHistory(new StringBuilder());
        // 上下文 token 预算管理：早期步骤摘要 / 已完成步数
        dynamicContext.setHistorySummary(null);
        dynamicContext.setExecutedSteps(0);
        dynamicContext.setCurrentTask(executeCommandEntity.getMessage());
        dynamicContext.setEmitter(emitter);
        // 全局 deadline：多轮循环共享这一份预算，预算耗尽时收敛到总结节点
        dynamicContext.setBudget(ExecutionBudget.start(nodeGuardPolicy.getTotalBudgetMs()));
        
        String apply = executeHandler.apply(executeCommandEntity, dynamicContext);
        log.info("测试结果:{}，用时 {}ms", apply, dynamicContext.getBudget().elapsedMillis());
        
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
