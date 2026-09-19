package cn.bugstack.ai.domain.agent.service.context.advisor;

import cn.bugstack.ai.domain.agent.service.context.ITokenCounter;
import cn.bugstack.ai.domain.agent.service.context.TokenUsageRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.model.tool.ToolCallingChatOptions;

import java.util.HashMap;
import java.util.Map;

/**
 * token 用量采集顾问（纯旁路，不改写 prompt）
 * <p>
 * 设计取舍：项目里有 8 处 {@code .call().content()}，改成 {@code .call().chatResponse()}
 * 才能拿到 usage，但那样要动所有调用点。这里改用 Advisor 的 after() 旁路读取，
 * 一处新增、零处改动，且 Auto / Fixed 策略自动全覆盖。
 *
 * @author xiaofuge bugstack.cn @小傅哥
 * 2026/09/14
 */
@Slf4j
public class TokenUsageAdvisor implements BaseAdvisor {

    /**
     * 在 context 里传递「本次请求的估算样本」
     */
    public static final String SAMPLE_KEY = "token_usage_sample";

    private final ITokenCounter tokenCounter;

    private final TokenUsageRegistry registry;

    private final int order;

    public TokenUsageAdvisor(ITokenCounter tokenCounter, TokenUsageRegistry registry, int order) {
        this.tokenCounter = tokenCounter;
        this.registry = registry;
        this.order = order;
    }

    @Override
    public ChatClientRequest before(ChatClientRequest request, AdvisorChain chain) {
        try {
            int estimated = tokenCounter.estimateMessages(request.prompt().getInstructions());
            boolean toolCallPresent = hasToolCallbacks(request);
            // ChatClientRequest 是 record，直接用公开构造器构造，避免依赖 Builder 的细节
            Map<String, Object> context = new HashMap<>(request.context());
            context.put(SAMPLE_KEY, new int[]{estimated, toolCallPresent ? 1 : 0});
            return new ChatClientRequest(request.prompt(), context);
        } catch (Exception e) {
            log.debug("token 估算失败，跳过本次采样：{}", e.getMessage());
            return request;
        }
    }

    @Override
    public ChatClientResponse after(ChatClientResponse response, AdvisorChain chain) {
        try {
            Object raw = response.context().get(SAMPLE_KEY);
            if (!(raw instanceof int[] sample)) {
                return response;
            }

            ChatResponse chatResponse = response.chatResponse();
            if (chatResponse == null || chatResponse.getMetadata() == null) {
                return response;
            }
            Usage usage = chatResponse.getMetadata().getUsage();
            if (usage == null || usage.getPromptTokens() == null || usage.getPromptTokens() <= 0) {
                return response;
            }

            String conversationId = (String) response.context().get(ChatMemory.CONVERSATION_ID);
            registry.record(conversationId, sample[0], usage.getPromptTokens(),
                    usage.getCompletionTokens() == null ? 0 : usage.getCompletionTokens(), sample[1] == 1);
        } catch (Exception e) {
            log.debug("token 用量采集失败：{}", e.getMessage());
        }
        return response;
    }

    /**
     * 判断本次调用是否挂了工具回调。
     * <p>
     * getToolCallbacks() 定义在 ToolCallingChatOptions 上而非 ChatOptions，故需先做类型判断。
     */
    private boolean hasToolCallbacks(ChatClientRequest request) {
        try {
            if (request.prompt().getOptions() instanceof ToolCallingChatOptions toolOptions) {
                return toolOptions.getToolCallbacks() != null && !toolOptions.getToolCallbacks().isEmpty();
            }
        } catch (Exception e) {
            log.debug("工具回调探测失败：{}", e.getMessage());
        }
        return false;
    }

    /**
     * 必须大于记忆顾问的 order（{@code Advisor.DEFAULT_CHAT_MEMORY_PRECEDENCE_ORDER} = HIGHEST_PRECEDENCE + 1000，
     * MessageChatMemoryAdvisor / PromptChatMemoryAdvisor 共用该常量）。
     * <p>
     * Advisor 是「环绕」语义：order 越大越靠近模型，before 越晚执行、after 越早执行。
     * 取 +2000 可保证本类的 after() 先于 ChatMemory.add() 执行，用量先落账。
     */
    @Override
    public int getOrder() {
        return order;
    }

}
