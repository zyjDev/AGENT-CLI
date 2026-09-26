package cn.bugstack.ai.domain.agent.service.execute.flow.step;

import cn.bugstack.ai.domain.agent.model.entity.AutoAgentExecuteResultEntity;
import cn.bugstack.ai.domain.agent.model.entity.ExecuteCommandEntity;
import cn.bugstack.ai.domain.agent.model.valobj.AiAgentClientFlowConfigVO;
import cn.bugstack.ai.domain.agent.model.valobj.NodeGuardPolicyVO;
import cn.bugstack.ai.domain.agent.model.valobj.enums.AiClientTypeEnumVO;
import cn.bugstack.ai.domain.agent.service.execute.flow.step.factory.DefaultFlowAgentExecuteStrategyFactory;
import cn.bugstack.ai.domain.agent.service.execute.guard.NodeDegradeMode;
import cn.bugstack.ai.domain.agent.service.execute.guard.NodeGuardResult;
import cn.bugstack.ai.domain.agent.service.execute.guard.NodeTask;
import cn.bugstack.ai.domain.agent.service.support.tree.StrategyHandler;
import cn.bugstack.ai.types.enums.ResponseCode;
import cn.bugstack.ai.types.exception.BizException;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 步骤1：MCP工具能力分析节点
 *
 * @author xiaofuge bugstack.cn @小傅哥
 * 2025/8/25 09:56
 */
@Slf4j
@Service
public class Step1McpToolsAnalysisNode extends AbstractExecuteSupport {

    @Resource
    private Step2PlanningNode step2PlanningNode;

    @Override
    protected String doApply(ExecuteCommandEntity requestParameter, DefaultFlowAgentExecuteStrategyFactory.DynamicContext dynamicContext) throws Exception {
        log.info("\n--- 步骤1: MCP工具能力分析（仅分析阶段，不执行用户请求） ---");

        // 获取配置信息
        Map<String, AiAgentClientFlowConfigVO> flowConfigMap = dynamicContext.getAiAgentClientFlowConfigVOMap();
        AiAgentClientFlowConfigVO aiAgentClientFlowConfigVO = flowConfigMap == null ? null
                : flowConfigMap.get(AiClientTypeEnumVO.TOOL_MCP_CLIENT.getCode());

        // 防御：ai_agent_flow_config 缺少该 clientType（或该行 status=0 被 queryEnabledByAgentId 过滤掉）时，
        // 原实现会在下一行直接 NPE —— 调用方只看到空指针，无法定位是哪个 Agent 少了哪类节点。
        if (aiAgentClientFlowConfigVO == null) {
            throw new BizException(ResponseCode.UN_ERROR.getCode(), String.format(
                    "Flow 链路缺少流程配置：agentId=%s 未配置 clientType=%s（请检查 ai_agent_flow_config 是否存在该行且 status=1）",
                    requestParameter.getAiAgentId(), AiClientTypeEnumVO.TOOL_MCP_CLIENT.getCode()));
        }

        // 获取MCP工具分析客户端
        ChatClient mcpToolsChatClient = getChatClientByClientId(aiAgentClientFlowConfigVO.getClientId());
        
        String mcpAnalysisPrompt = String.format(
                """
                        # MCP工具能力分析任务
                        
                        ## 重要说明
                        **注意：本阶段仅进行MCP工具能力分析，不执行用户的实际请求。**\s
                        这是一个纯分析阶段，目的是评估可用工具的能力和适用性，为后续的执行规划提供依据。
                        
                        ## 用户请求
                        %s
                        
                        ## 分析要求
                        请基于上述实际的MCP工具信息，针对用户请求进行详细的工具能力分析（仅分析，不执行）：
                        
                        ### 1. 工具匹配分析
                        - 分析每个可用工具的核心功能和适用场景
                        - 评估哪些工具能够满足用户请求的具体需求
                        - 标注每个工具的匹配度（高/中/低）
                        
                        ### 2. 工具使用指南
                        - 提供每个相关工具的具体调用方式
                        - 说明必需的参数和可选参数
                        - 给出参数的示例值和格式要求
                        
                        ### 3. 执行策略建议
                        - 推荐最优的工具组合方案
                        - 建议工具的调用顺序和依赖关系
                        - 提供备选方案和降级策略
                        
                        ### 4. 注意事项
                        - 标注工具的使用限制和约束条件
                        - 提醒可能的错误情况和处理方式
                        - 给出性能优化建议
                        
                        ### 5. 分析总结
                        - 明确说明这是分析阶段，不要执行用的任何实际操作
                        - 总结工具能力评估结果
                        - 为后续执行阶段提供建议
                        
                        请确保分析结果准确、详细、可操作，并再次强调这仅是分析阶段。""",
                dynamicContext.getCurrentTask()
        );

        // 受治理的模型调用：硬超时 45s（被全局剩余预算二次裁剪）、瞬时故障退避重试、
        // 全部失败后降级为「无工具上下文」——本节点只做能力分析，丢了不产生错误结论，
        // 后续 Step2 会带着这个降级事实继续规划（清空工具依赖即可）。
        NodeGuardResult<String> guarded = nodeGuardEngine.execute(NodeTask.<String>builder()
                .nodeKey(NodeGuardPolicyVO.NodeKeys.FLOW_STEP1_TOOL_ANALYSIS)
                .displayName("Step1 MCP工具分析")
                .budget(dynamicContext.getBudget())
                .emitter(dynamicContext.getEmitter())
                .sessionId(requestParameter.getSessionId())
                .step(dynamicContext.getStep())
                .degradeMode(NodeDegradeMode.FALLBACK)
                .retryable(true)
                .fallback(() -> """
                        ## MCP 工具能力分析不可用（已降级）
                        该节点超时或不可用，本次链路按「不依赖外部工具」继续。
                        请在后续规划中仅使用模型自身能力完成任务。""")
                .callable(() -> mcpToolsChatClient.prompt()
                        .user(mcpAnalysisPrompt)
                        // Spring AI 1.1.6 起记忆顾问强制要求 conversationId，缺失会抛 IllegalArgumentException。
                        // param 只能挂在 AdvisorSpec 上（ChatClientRequestSpec 无 param 方法），与 Auto 链路写法一致。
                        .advisors(a -> a.param(CHAT_MEMORY_CONVERSATION_ID_KEY, requestParameter.getMemoryConversationId()))
                        .call()
                        .content())
                .build());

        String mcpToolsAnalysis = guarded.getValue();
        rememberDegrade(dynamicContext, "Step1 MCP工具分析", guarded);

        log.info("MCP工具分析结果（仅分析，未执行实际操作）: {}", mcpToolsAnalysis);
        
        // 保存分析结果到上下文
        dynamicContext.setMcpToolsAnalysis(mcpToolsAnalysis);
        
        // 发送SSE结果
        AutoAgentExecuteResultEntity result = AutoAgentExecuteResultEntity.createAnalysisSubResult(
                dynamicContext.getStep(), 
                "analysis_tools", 
                mcpToolsAnalysis, 
                requestParameter.getSessionId());
        sendSseResult(dynamicContext, result);
        
        // 更新步骤
        dynamicContext.setStep(dynamicContext.getStep() + 1);
        
        return router(requestParameter, dynamicContext);
    }

    @Override
    public StrategyHandler<ExecuteCommandEntity, DefaultFlowAgentExecuteStrategyFactory.DynamicContext, String> get(ExecuteCommandEntity requestParameter, DefaultFlowAgentExecuteStrategyFactory.DynamicContext dynamicContext) throws Exception {
        return step2PlanningNode;
    }

}