package cn.bugstack.ai.domain.agent.service.execute.flow.step.factory;

import cn.bugstack.ai.domain.agent.model.entity.ExecuteCommandEntity;
import cn.bugstack.ai.domain.agent.model.valobj.AiAgentClientFlowConfigVO;
import cn.bugstack.ai.domain.agent.service.execute.flow.step.RootNode;
import cn.bugstack.ai.domain.agent.service.support.tree.StrategyHandler;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

import java.util.Map;

/**
 * 流程执行策略工厂类
 *
 * @author xiaofuge bugstack.cn @小傅哥
 * 2025/8/24 14:28
 */
@Service
public class DefaultFlowAgentExecuteStrategyFactory {

    private final RootNode flowRootNode;

    public DefaultFlowAgentExecuteStrategyFactory(RootNode flowRootNode) {
        this.flowRootNode = flowRootNode;
    }

    public StrategyHandler<ExecuteCommandEntity, DefaultFlowAgentExecuteStrategyFactory.DynamicContext, String> armoryStrategyHandler(){
        return flowRootNode;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class DynamicContext {

        // 任务执行步骤
        private int step = 1;

        // 最大任务步骤
        private int maxStep = 4;

        private StringBuilder executionHistory;

        private String currentTask;

        boolean isCompleted = false;

        private Map<String, AiAgentClientFlowConfigVO> aiAgentClientFlowConfigVOMap;

        // ── 以下为跨节点传递的运行时数据 ──────────────────────────────
        // 原实现放在 Map<String, Object> dataObjects 中，以裸字符串 key 存取：
        //   setValue("planningResult", x) / getValue("planningResult")
        // 问题：key 拼错编译期无感、取值靠调用方强转（ClassCastException 要跑起来才发现）。
        // 现改为强类型字段，访问器由 Lombok @Data 生成，编译器与 IDE 均可校验。

        /** SSE 发射器（原 key: "emitter"） */
        private ResponseBodyEmitter emitter;

        /** Step1 MCP 工具分析结果（原 key: "mcpToolsAnalysis"），Step2 读取 */
        private String mcpToolsAnalysis;

        /** Step2 规划结果（原 key: "planningResult"），Step3 读取 */
        private String planningResult;

        /** Step3 解析出的步骤映射（原 key: "stepsMap"），Step4 读取 */
        private Map<String, String> stepsMap;
    }

}
