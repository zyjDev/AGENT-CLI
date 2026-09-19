package cn.bugstack.ai.domain.agent.service.execute.auto.step.factory;

import cn.bugstack.ai.domain.agent.model.entity.ExecuteCommandEntity;
import cn.bugstack.ai.domain.agent.model.valobj.AiAgentClientFlowConfigVO;
import cn.bugstack.ai.domain.agent.service.execute.auto.step.RootNode;
import cn.bugstack.ai.domain.agent.service.support.tree.StrategyHandler;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

import java.util.Map;

/**
 * 工厂类
 *
 * @author xiaofuge bugstack.cn @小傅哥
 * 2025/7/27 16:34
 */
@Service
public class DefaultAutoAgentExecuteStrategyFactory {

    private final RootNode executeRootNode;

    public DefaultAutoAgentExecuteStrategyFactory(RootNode executeRootNode) {
        this.executeRootNode = executeRootNode;
    }

    public StrategyHandler<ExecuteCommandEntity, DefaultAutoAgentExecuteStrategyFactory.DynamicContext, String> armoryStrategyHandler(){
        return executeRootNode;
    }

    /**
     * 动态上下文，用于后续节点流转时的数据传递
     */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class DynamicContext {

        // 任务执行步骤
        private int step = 1;

        // 最大任务步骤
        private int maxStep = 1;

        // 最近若干步的完整执行记录（受 token 预算约束，超出时压缩到 historySummary）
        private StringBuilder executionHistory;

        // 更早步骤的压缩摘要（token 预算管理新增）
        private String historySummary;

        // 已执行完成的步数（不受压缩影响，供 Step4 统计使用；token 预算管理新增）
        private int executedSteps = 0;

        private String currentTask;

        boolean isCompleted = false;

        private Map<String, AiAgentClientFlowConfigVO> aiAgentClientFlowConfigVOMap;

        // ── 以下为跨节点传递的运行时数据 ──────────────────────────────
        // 原实现放在 Map<String, Object> dataObjects 中，以裸字符串 key 存取：
        //   setValue("analysisResult", x) / getValue("analysisResult")
        // 问题：key 拼错编译期无感、取值靠调用方强转（ClassCastException 要跑起来才发现）。
        // 现改为强类型字段，访问器由 Lombok @Data 生成，编译器与 IDE 均可校验。

        /** SSE 发射器（原 key: "emitter"） */
        private ResponseBodyEmitter emitter;

        /** Step1 分析结果（原 key: "analysisResult"），Step2 / Step3 读取 */
        private String analysisResult;

        /** Step2 执行结果（原 key: "executionResult"），Step3 读取 */
        private String executionResult;
    }

}
