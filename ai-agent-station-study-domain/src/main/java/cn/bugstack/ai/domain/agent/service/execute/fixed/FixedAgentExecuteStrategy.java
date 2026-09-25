package cn.bugstack.ai.domain.agent.service.execute.fixed;

import cn.bugstack.ai.domain.agent.adapter.repository.IAgentRepository;
import cn.bugstack.ai.domain.agent.model.entity.AutoAgentExecuteResultEntity;
import cn.bugstack.ai.domain.agent.model.entity.ExecuteCommandEntity;
import cn.bugstack.ai.domain.agent.model.valobj.AiAgentClientFlowConfigVO;
import cn.bugstack.ai.domain.agent.model.valobj.NodeGuardPolicyVO;
import cn.bugstack.ai.domain.agent.model.valobj.enums.AiAgentEnumVO;
import cn.bugstack.ai.domain.agent.service.IExecuteStrategy;
import cn.bugstack.ai.domain.agent.service.execute.guard.ExecutionBudget;
import cn.bugstack.ai.domain.agent.service.execute.guard.NodeDegradeMode;
import cn.bugstack.ai.domain.agent.service.execute.guard.NodeGuardEngine;
import cn.bugstack.ai.domain.agent.service.execute.guard.NodeGuardResult;
import cn.bugstack.ai.domain.agent.service.execute.guard.NodeTask;
import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

import jakarta.annotation.Resource;
import java.time.LocalDate;
import java.util.List;

/**
 * 固定执行策略
 *
 */
@Slf4j
@Service("fixedAgentExecuteStrategy")
public class FixedAgentExecuteStrategy implements IExecuteStrategy {
    // 注入智能体仓库
    @Resource
    private IAgentRepository repository;

    // 注入应用上下文
    @Resource
    protected ApplicationContext applicationContext;

    /**
     * 节点治理：Fixed 链路虽然只有一层循环，但同样是裸 {@code .call()}，一样会被慢接口拖死，
     * 需要和 Auto / Flow 一样受统一治理。
     */
    @Resource
    private NodeGuardEngine nodeGuardEngine;

    @Resource
    private NodeGuardPolicyVO nodeGuardPolicy;

    // 定义会话ID参数
    public static final String CHAT_MEMORY_CONVERSATION_ID_KEY = "chat_memory_conversation_id";
    // 定义模型对话轮数
    public static final String CHAT_MEMORY_RETRIEVE_SIZE_KEY = "chat_memory_response_size";

    @Override
    public void execute(ExecuteCommandEntity requestParameter, ResponseBodyEmitter emitter) throws Exception {
        // 1. 获取配置客户端
        List<AiAgentClientFlowConfigVO> aiAgentClientList = repository.queryAiAgentClientsByAgentId(requestParameter.getAiAgentId());

        // 2. 循环执行客户端
        String content = "";

        // 这一层也需要全局预算：客户端是串行执行的，N 个客户端各跑一次，
        // 没有 deadline 时最坏情况就是 N × 节点超时。
        ExecutionBudget budget = ExecutionBudget.start(nodeGuardPolicy.getTotalBudgetMs());

        for (AiAgentClientFlowConfigVO config : aiAgentClientList) {
            ChatClient chatClient = getChatClientByClientId(config.getClientId());
            final String previous = content;

            NodeGuardResult<String> guarded = nodeGuardEngine.execute(NodeTask.<String>builder()
                    .nodeKey(NodeGuardPolicyVO.NodeKeys.FIXED_CLIENT_CALL)
                    .displayName("客户端调用 " + config.getClientId())
                    .budget(budget)
                    .emitter(emitter)
                    .sessionId(requestParameter.getSessionId())
                    // 任一客户端失败都不该让整条链路空手而归：保留上一轮的 content 作为兜底
                    .degradeMode(NodeDegradeMode.FALLBACK)
                    .retryable(true)
                    .fallback(() -> previous)
                    .callable(() -> chatClient.prompt(requestParameter.getMessage() + "，" + previous)
                            .system(s -> s.param("current_date", LocalDate.now().toString()))
                            .advisors(a -> {
                                a.param(CHAT_MEMORY_CONVERSATION_ID_KEY, requestParameter.getSessionId());
                                a.param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 100);
                                if (requestParameter.getKnowledgeTag() != null && !requestParameter.getKnowledgeTag().trim().isEmpty()) {
                                    a.param("knowledgeTag", requestParameter.getKnowledgeTag().trim());
                                }
                            })
                            .call().content())
                    .build());

            content = guarded.getValue();
            log.info("智能体对话进行，客户端ID {}，结果 {}", requestParameter.getAiAgentId(), guarded.getOutcome());
        }

        log.info("智能体对话请求，结果 {} {}", requestParameter.getAiAgentId(), content);
        
        // 发送最终结果通知（确保 content 不为空）
        if (content != null && !content.trim().isEmpty()) {
            sendFinalResult(emitter, content, requestParameter.getSessionId());
        }
        
        // 发送完成标识
        sendCompleteResult(emitter, requestParameter.getSessionId());
    }

    private ChatClient getChatClientByClientId(String clientId) {
        return getBean(AiAgentEnumVO.AI_CLIENT.getBeanName(clientId));
    }

    private <T> T getBean(String beanName) {
        return (T) applicationContext.getBean(beanName);
    }
    
    /**
     * 发送最终结果到流式输出
     */
    private void sendFinalResult(ResponseBodyEmitter emitter, String content, String sessionId) {
        try {
            AutoAgentExecuteResultEntity result = AutoAgentExecuteResultEntity.createSummaryResult(content, sessionId);
            String sseData = "data: " + JSON.toJSONString(result) + "\n\n";
            emitter.send(sseData);
            log.info("✅ 已发送最终结果");
        } catch (Exception e) {
            log.error("发送最终结果失败：{}", e.getMessage(), e);
        }
    }
    
    /**
     * 发送完成标识到流式输出
     */
    private void sendCompleteResult(ResponseBodyEmitter emitter, String sessionId) {
        try {
            AutoAgentExecuteResultEntity result = AutoAgentExecuteResultEntity.createCompleteResult(sessionId);
            String sseData = "data: " + JSON.toJSONString(result) + "\n\n";
            emitter.send(sseData);
            log.info("✅ 已发送完成标识");
        } catch (Exception e) {
            log.error("发送完成标识失败：{}", e.getMessage(), e);
        }
    }

}
