package cn.bugstack.ai.domain.agent.service.execute.flow.step;

import cn.bugstack.ai.domain.agent.model.entity.AutoAgentExecuteResultEntity;
import cn.bugstack.ai.domain.agent.model.entity.ExecuteCommandEntity;
import cn.bugstack.ai.domain.agent.model.valobj.AiAgentClientFlowConfigVO;
import cn.bugstack.ai.domain.agent.model.valobj.NodeGuardPolicyVO;
import cn.bugstack.ai.domain.agent.model.valobj.enums.AiClientTypeEnumVO;
import cn.bugstack.ai.domain.agent.service.execute.flow.step.factory.DefaultFlowAgentExecuteStrategyFactory;
import cn.bugstack.ai.domain.agent.service.execute.guard.NodeDegradeMode;
import cn.bugstack.ai.domain.agent.service.execute.guard.NodeGuardOutcome;
import cn.bugstack.ai.domain.agent.service.execute.guard.NodeGuardResult;
import cn.bugstack.ai.domain.agent.service.execute.guard.NodeTask;
import cn.bugstack.ai.types.enums.ResponseCode;
import cn.bugstack.ai.types.exception.BizException;
import cn.bugstack.ai.domain.agent.service.support.tree.StrategyHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 第四步：按顺序执行规划步骤节点
 *
 * @author xiaofuge bugstack.cn @小傅哥
 * 2025/8/25 10:30
 */
@Slf4j
@Component
public class Step4ExecuteStepsNode extends AbstractExecuteSupport {

    @Override
    public String doApply(ExecuteCommandEntity request, DefaultFlowAgentExecuteStrategyFactory.DynamicContext dynamicContext) {
        log.info("开始执行第四步：按顺序执行规划步骤");

        // 配置缺失属于「部署/配置错误」而非「步骤执行失败」，故放在 try 之外直接外抛，
        // 避免被下方 catch 吞成一句 "执行步骤失败: ..."，与 Step1/Step2 的失败语义保持一致。
        Map<String, AiAgentClientFlowConfigVO> flowConfigMap = dynamicContext.getAiAgentClientFlowConfigVOMap();
        AiAgentClientFlowConfigVO aiAgentClientFlowConfigVO = flowConfigMap == null ? null
                : flowConfigMap.get(AiClientTypeEnumVO.EXECUTOR_CLIENT.getCode());
        if (aiAgentClientFlowConfigVO == null) {
            throw new BizException(ResponseCode.UN_ERROR.getCode(), String.format(
                    "Flow 链路缺少流程配置：agentId=%s 未配置 clientType=%s（请检查 ai_agent_flow_config 是否存在该行且 status=1）",
                    request.getAiAgentId(), AiClientTypeEnumVO.EXECUTOR_CLIENT.getCode()));
        }

        try {
            // 获取执行客户端
            ChatClient executorChatClient = getChatClientByClientId(aiAgentClientFlowConfigVO.getClientId());

            // 从动态上下文获取解析的步骤
            Map<String, String> stepsMap = dynamicContext.getStepsMap();
            
            if (stepsMap == null || stepsMap.isEmpty()) {
                return "步骤映射为空，无法执行";
            }
            
            // 按顺序执行规划步骤
            // 传「按用户分区的会话键」而不是裸 sessionId：记忆顾问按它读写上下文，
            // 否则不同账号的 sessionId 撞上就会串记忆（该值同时作为 NodeGuard 事件的 sessionId 上报）
            executeStepsInOrder(executorChatClient, stepsMap, dynamicContext, request.getMemoryConversationId());
            
            // 发送SSE结果
            AutoAgentExecuteResultEntity result = AutoAgentExecuteResultEntity.createExecutionResult(
                    dynamicContext.getStep(),
                    "已完成所有规划步骤的执行",
                    request.getSessionId()
            );
            sendSseResult(dynamicContext, result);
            
            // 发送总结结果到【最终执行结果】区域
            sendSummaryResult(dynamicContext, request.getSessionId());
            
            // 发送完成标识
            sendCompleteResult(dynamicContext, request.getSessionId());
            
            // 更新步骤
            dynamicContext.setStep(dynamicContext.getStep() + 1);
            dynamicContext.setCompleted(true);
            
            log.info("第四步执行完成：所有规划步骤已执行");

            return "所有规划步骤执行完成";
        } catch (Exception e) {
            log.error("第四步执行失败", e);
            return "执行步骤失败: " + e.getMessage();
        }
    }

    @Override
    public StrategyHandler<ExecuteCommandEntity, DefaultFlowAgentExecuteStrategyFactory.DynamicContext, String> get(ExecuteCommandEntity request, DefaultFlowAgentExecuteStrategyFactory.DynamicContext dynamicContext) {
        return defaultStrategyHandler;
    }
    
    /**
     * 按顺序执行规划步骤
     */
    private void executeStepsInOrder(ChatClient executorChatClient, Map<String, String> stepsMap, DefaultFlowAgentExecuteStrategyFactory.DynamicContext dynamicContext, String sessionId) {
        if (stepsMap == null || stepsMap.isEmpty()) {
            log.warn("步骤映射为空，无法执行");
            return;
        }

        // 按步骤编号排序执行
        List<Integer> stepNumbers = new ArrayList<>();
        for (String stepKey : stepsMap.keySet()) {
            try {
                // 从"第1步"、"第2步"等格式中提取数字
                Pattern numberPattern = Pattern.compile("第(\\d+)步");
                Matcher matcher = numberPattern.matcher(stepKey);
                if (matcher.find()) {
                    stepNumbers.add(Integer.parseInt(matcher.group(1)));
                }
            } catch (NumberFormatException e) {
                log.warn("无法解析步骤编号: {}", stepKey);
            }
        }

        // 排序步骤编号
        stepNumbers.sort(Integer::compareTo);

        // 按顺序执行每个步骤
        for (Integer stepNumber : stepNumbers) {
            String stepKey = "第" + stepNumber + "步";
            String stepContent = null;

            // 查找匹配的步骤内容
            for (Map.Entry<String, String> entry : stepsMap.entrySet()) {
                if (entry.getKey().startsWith(stepKey)) {
                    stepContent = entry.getValue();
                    break;
                }
            }

            if (stepContent != null) {
                executeStep(executorChatClient, stepNumber, stepKey, stepContent, dynamicContext, sessionId);
            } else {
                log.warn("未找到步骤内容: {}", stepKey);
            }
        }
    }
    
    /**
     * 执行单个步骤
     */
    private void executeStep(ChatClient executorChatClient, Integer stepNumber, String stepKey, String stepContent, DefaultFlowAgentExecuteStrategyFactory.DynamicContext dynamicContext, String sessionId) {
        log.info("\n--- 开始执行 {} ---", stepKey);
        log.info("步骤内容: {}", stepContent.substring(0, Math.min(200, stepContent.length())) + "...");

        // 受治理的模型调用。两处刻意的设计：
        // 1) retryable=false —— 本步骤内部会驱动 MCP 工具调用（如发布文章、发通知），
        //    副作用不可撤销，失败后自动重试等于重复执行工具。工具类调用不幂等，就别重试。
        // 2) degradeMode=FALLBACK —— 单步失败只记失败继续下一步，不让一个步骤拖垮整批，
        //    失败原因通过 SSE 事件与最终总结如实告诉用户。
        NodeGuardResult<String> stepResult = nodeGuardEngine.execute(NodeTask.<String>builder()
                .nodeKey(NodeGuardPolicyVO.NodeKeys.FLOW_STEP4_EXECUTE_STEPS)
                .displayName(stepKey)
                .budget(dynamicContext.getBudget())
                .emitter(dynamicContext.getEmitter())
                .sessionId(sessionId)
                .step(stepNumber)
                .degradeMode(NodeDegradeMode.FALLBACK)
                .retryable(false)
                .fallback(() -> String.format("[跳过] %s 因超时/失败未产出结果，已跳过该步骤", stepKey))
                .callable(() -> {
                    String executionResult = executorChatClient.prompt()
                            .user(buildStepExecutionPrompt(stepContent, dynamicContext))
                            // Spring AI 1.1.6 起记忆顾问强制要求 conversationId，缺失会抛 IllegalArgumentException。
                            // param 只能挂在 AdvisorSpec 上（ChatClientRequestSpec 无 param 方法），与 Auto 链路写法一致。
                            .advisors(a -> a.param(CHAT_MEMORY_CONVERSATION_ID_KEY, sessionId))
                            .call()
                            .content();
                    // 显式校验：assert 依赖 -ea 参数，生产环境默认不生效，会导致下方 substring 抛 NPE。
                    if (executionResult == null) {
                        throw new BizException(ResponseCode.UN_ERROR.getCode(),
                                "第 " + stepNumber + " 步执行未返回结果（模型调用失败或超时）");
                    }
                    return executionResult;
                })
                .build());

        if (NodeGuardOutcome.SKIPPED == stepResult.getOutcome()) {
            // 兜底逻辑自身也没产出：这一步彻底没有内容，记录后继续
            handleStepExecutionError(stepNumber, stepKey,
                    new BizException(ResponseCode.UN_ERROR.getCode(), "步骤无产出（含兜底）"), dynamicContext, sessionId);
            return;
        }

        executeDone(stepNumber, stepKey, stepResult, stepContent, dynamicContext, sessionId);
    }

    /**
     * 步骤执行成功/降级后的收尾：推送结果、累加执行历史、节流
     */
    private void executeDone(Integer stepNumber, String stepKey, NodeGuardResult<String> stepResult,
                             String stepContent, DefaultFlowAgentExecuteStrategyFactory.DynamicContext dynamicContext,
                             String sessionId) {
        try {
            String executionResult = stepResult.getValue();
            rememberDegrade(dynamicContext, stepKey, stepResult);
            log.info("步骤 {} 执行结果: {}", stepNumber, executionResult.substring(0, Math.min(150, executionResult.length())) + "...");

            // 发送步骤执行结果的SSE
            AutoAgentExecuteResultEntity result = AutoAgentExecuteResultEntity.createExecutionResult(
                    stepNumber,
                    stepKey + " 执行完成: " + executionResult.substring(0, Math.min(500, executionResult.length())),
                    sessionId
            );
            sendSseResult(dynamicContext, result);

            // 把本步结果写进执行历史，供最终总结使用
            dynamicContext.getExecutionHistory()
                    .append(String.format("\n### %s：%s\n%s\n", stepKey,
                            stepContent.split("\n")[0], executionResult));

            // 节流：原来这里是无条件的 Thread.sleep(1000)，既占着线程池线程又不解释缘由。
            // 由配置控制（默认 300ms，配 0 可完全关闭）。
            long intervalMs = nodeGuardPolicy.getStepIntervalMs();
            if (intervalMs > 0) {
                Thread.sleep(intervalMs);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("执行步骤 {} 收尾失败: {}", stepNumber, e.getMessage());
            handleStepExecutionError(stepNumber, stepKey, e, dynamicContext, sessionId);
        }

        log.info("--- 完成执行 {} ---", stepKey);
    }
    
    /**
     * 处理步骤执行错误
     */
    private void handleStepExecutionError(Integer stepNumber, String stepKey, Exception e, DefaultFlowAgentExecuteStrategyFactory.DynamicContext dynamicContext, String sessionId) {
        log.warn("步骤 {} 执行失败，尝试恢复策略", stepNumber);

        // 说明：原先此处维护 stepErrorStats（写入动态上下文并自增计数），
        // 但自增后的 Map 从未被任何代码消费 —— 属「读了但结果直接丢弃」的死逻辑，已整体移除。

        // 说明：原先此处对 timeout / connection 关键字只打了一句「将在后续重试机制中处理」，
        // 而那个「后续重试机制」并不存在 —— 真正的重试已在 NodeGuardEngine 中完成，
        // 失败信息通过下面这条 SSE 事件与最终总结如实告知用户，不再假装会重试。

        // 说明：原先此处 setValue("step{n}Status", "FAILED_WITH_ERROR") 无任何读取点，属死写入，已移除。
        
        // 发送错误结果的SSE
        try {
            AutoAgentExecuteResultEntity errorResult = AutoAgentExecuteResultEntity.createExecutionResult(
                    stepNumber,
                    stepKey + " 执行失败: " + e.getMessage(),
                    sessionId
            );
            sendSseResult(dynamicContext, errorResult);
        } catch (Exception sseException) {
            log.error("发送错误SSE结果失败", sseException);
        }
    }
    
    /**
     * 构建步骤执行提示词
     */
    private String buildStepExecutionPrompt(String stepContent, DefaultFlowAgentExecuteStrategyFactory.DynamicContext dynamicContext) {
        return "你是一个智能执行助手，需要执行以下步骤:\n\n" +
                "**步骤内容:**\n" +
                stepContent + "\n\n" +
                "**用户原始请求:**\n" +
                dynamicContext.getCurrentTask() + "\n\n" +
                "**执行要求:**\n" +
                "1. 仔细分析步骤内容，理解需要执行的具体任务\n" +
                "2. 如果涉及MCP工具调用，请使用相应的工具\n" +
                "3. 提供详细的执行过程和结果\n" +
                "4. 如果遇到问题，请说明具体的错误信息\n" +
                "5. **重要**: 执行完成后，必须在回复末尾明确输出执行结果，格式如下:\n" +
                "   ```\n" +
                "   === 执行结果 ===\n" +
                "   状态: [成功/失败]\n" +
                "   结果描述: [具体的执行结果描述]\n" +
                "   输出数据: [如果有具体的输出数据，请在此列出]\n" +
                "   ```\n\n" +
                "请开始执行这个步骤，并严格按照要求提供详细的执行报告和结果输出。";
    }
    
    /**
     * 发送总结结果到流式输出
     */
    private void sendSummaryResult(DefaultFlowAgentExecuteStrategyFactory.DynamicContext dynamicContext, String sessionId) {
        // 构建执行总结内容
        StringBuilder summaryContent = new StringBuilder();
        summaryContent.append("## 执行步骤完成总结\n\n");
        
        // 获取执行历史
        StringBuilder executionHistory = dynamicContext.getExecutionHistory();
        if (executionHistory != null && executionHistory.length() > 0) {
            summaryContent.append("### 已完成的工作\n");
            summaryContent.append(executionHistory.toString());
            summaryContent.append("\n\n");
        }
        
        summaryContent.append("### 执行状态\n");
        if (dynamicContext.getDegradedNodes() > 0) {
            // 降级必须如实呈现：让用户知道哪些内容是兜底产出，而不是模型真实结论
            summaryContent.append("⚠️ 部分环节降级完成（共 ")
                    .append(dynamicContext.getDegradedNodes())
                    .append(" 处）：")
                    .append(String.join("、", dynamicContext.getDegradedNodeNames()))
                    .append("\n");
            summaryContent.append("说明：以上环节因超时或不可用未从模型拿到结果，已按兜底策略处理。\n\n");
        } else {
            summaryContent.append("✅ 所有规划步骤已成功执行完成\n\n");
        }

        summaryContent.append("### 执行效果评估\n");
        summaryContent.append("📊 任务执行流程顺利完成，各步骤按计划执行");
        if (dynamicContext.getBudget() != null) {
            summaryContent.append(String.format("\n\n### 耗时\n全程 %dms（预算 %dms）",
                    dynamicContext.getBudget().elapsedMillis(),
                    dynamicContext.getBudget().elapsedMillis() + dynamicContext.getBudget().remainingMillis()));
        }
        
        AutoAgentExecuteResultEntity result = AutoAgentExecuteResultEntity.createSummaryResult(
                summaryContent.toString(), sessionId);
        sendSseResult(dynamicContext, result);
        log.info("📊 已发送总结结果到【最终执行结果】区域");
    }
    
    /**
     * 发送完成标识到流式输出
     */
    private void sendCompleteResult(DefaultFlowAgentExecuteStrategyFactory.DynamicContext dynamicContext, String sessionId) {
        AutoAgentExecuteResultEntity result = AutoAgentExecuteResultEntity.createCompleteResult(sessionId);
        sendSseResult(dynamicContext, result);
        log.info("✅ 已发送完成标识");
    }
}
