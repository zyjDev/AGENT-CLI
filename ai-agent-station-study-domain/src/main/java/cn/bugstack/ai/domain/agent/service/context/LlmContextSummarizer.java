package cn.bugstack.ai.domain.agent.service.context;

import cn.bugstack.ai.domain.agent.model.valobj.ContextBudgetVO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * LLM 摘要实现（带规则截断降级）
 * <p>
 * 关键约束：摘要调用必须用「裸 ChatClient」，不能复用项目里挂了 ChatMemory / RagAnswer 顾问的
 * ChatClient——否则摘要过程会写回记忆，形成递归压缩。
 *
 * @author xiaofuge bugstack.cn @小傅哥
 * 2026/09/14
 */
@Slf4j
@Service
public class LlmContextSummarizer implements IContextSummarizer {

    private static final String CONVERSATION_INSTRUCTION = """
            你是一个对话历史压缩器。请把「需要压缩的新增内容」与「已有摘要」合并，输出一段简洁的对话摘要，要求：
            1. 保留用户的核心诉求、已确认的关键结论、未完成事项与约束条件；
            2. 丢弃寒暄、重复表述与过程性细节；
            3. 用第三人称陈述，不要出现「你」「我」；
            4. 只输出摘要正文，不要任何解释、标题或前缀。
            """;

    private static final String EXECUTION_INSTRUCTION = """
            你是一个任务执行历史压缩器。请把「需要压缩的新增内容」与「已有摘要」合并，输出一段简洁的执行历史摘要，要求：
            1. 按步骤保留：每步的目标、关键动作、产出结果、失败原因；
            2. 丢弃冗长的原文与重复描述；
            3. 只输出摘要正文，不要任何解释、标题或前缀。
            """;

    @Resource
    private ApplicationContext applicationContext;

    @Resource
    private ContextBudgetVO contextBudget;

    @Resource
    private ITokenCounter tokenCounter;

    @Resource(name = "contextSummaryExecutor")
    private ExecutorService summaryExecutor;

    @Override
    public String summarize(String conversationId, String previousSummary, List<Message> head, int maxSummaryTokens) {
        return summarize(conversationId, previousSummary, head, contextBudget, maxSummaryTokens);
    }

    @Override
    public String summarize(String conversationId, String previousSummary, List<Message> head, ContextBudgetVO budget) {
        return summarize(conversationId, previousSummary, head, budget, null);
    }

    private String summarize(String conversationId, String previousSummary, List<Message> head,
                             ContextBudgetVO budget, Integer maxSummaryTokensOverride) {
        if (head == null || head.isEmpty()) {
            return previousSummary;
        }

        StringBuilder sb = new StringBuilder();
        for (Message message : head) {
            String text = safeText(message);
            if (text.isEmpty()) {
                continue;
            }
            sb.append(message.getMessageType().getValue()).append(": ").append(text).append("\n");
        }
        if (sb.length() == 0) {
            return previousSummary;
        }

        return doSummarize("conversation:" + conversationId, previousSummary, sb.toString(),
                budget, maxSummaryTokensOverride, CONVERSATION_INSTRUCTION);
    }

    @Override
    public String summarizeExecution(String sessionId, String previousSummary, String headText, int maxSummaryTokens) {
        return summarizeExecution(sessionId, previousSummary, headText, contextBudget, maxSummaryTokens);
    }

    @Override
    public String summarizeExecution(String sessionId, String previousSummary, String headText, ContextBudgetVO budget) {
        return summarizeExecution(sessionId, previousSummary, headText, budget, null);
    }

    private String summarizeExecution(String sessionId, String previousSummary, String headText,
                                      ContextBudgetVO budget, Integer maxSummaryTokensOverride) {
        if (headText == null || headText.isBlank()) {
            return previousSummary;
        }
        return doSummarize("execution:" + sessionId, previousSummary, headText,
                budget, maxSummaryTokensOverride, EXECUTION_INSTRUCTION);
    }

    private String doSummarize(String tag, String previousSummary, String content,
                               ContextBudgetVO budget, Integer maxSummaryTokensOverride, String instruction) {
        ContextBudgetVO effectiveBudget = budget != null ? budget : contextBudget;
        int maxSummaryTokens = maxSummaryTokensOverride != null
                ? maxSummaryTokensOverride
                : effectiveBudget.getMaxSummaryTokens();

        if (!"LLM".equalsIgnoreCase(effectiveBudget.getSummaryMode())) {
            return ContextBudgetSupport.ruleTruncate(tokenCounter, previousSummary, content,
                    effectiveBudget.getTruncateCharsPerStep(), maxSummaryTokens);
        }

        try {
            ChatModel chatModel = resolveSummaryChatModel(effectiveBudget);
            if (chatModel == null) {
                return ContextBudgetSupport.ruleTruncate(tokenCounter, previousSummary, content,
                        effectiveBudget.getTruncateCharsPerStep(), maxSummaryTokens);
            }

            String prompt = instruction
                    + "\n\n【已有摘要】\n" + (previousSummary == null || previousSummary.isBlank() ? "（无）" : previousSummary)
                    + "\n\n【需要压缩的新增内容】\n" + content
                    + "\n\n【硬性要求】\n摘要总长度必须控制在 " + maxSummaryTokens + " 个 token 以内。";

            // 裸 ChatClient：不挂任何 advisor，避免摘要过程写回记忆造成递归
            ChatClient bareClient = ChatClient.builder(chatModel).build();
            String summary = CompletableFuture
                    .supplyAsync(() -> bareClient.prompt(prompt).call().content(), summaryExecutor)
                    .get(effectiveBudget.getSummaryTimeoutMs(), TimeUnit.MILLISECONDS);

            if (summary == null || summary.isBlank()) {
                return ContextBudgetSupport.ruleTruncate(tokenCounter, previousSummary, content,
                        effectiveBudget.getTruncateCharsPerStep(), maxSummaryTokens);
            }

            String result = summary.trim();
            // 摘要自身超预算时硬截断兜底，防止「摘要的摘要」无限膨胀
            if (tokenCounter.estimate(result) > maxSummaryTokens) {
                result = ContextBudgetSupport.hardTruncate(tokenCounter, result, maxSummaryTokens);
            }

            log.info("🧾 摘要生成成功：tag={}, 原内容 {} token → 摘要 {} token",
                    tag, tokenCounter.estimate(content), tokenCounter.estimate(result));
            return result;
        } catch (TimeoutException e) {
            log.warn("摘要生成超时（{}ms），降级为规则截断：tag={}", effectiveBudget.getSummaryTimeoutMs(), tag);
            return ContextBudgetSupport.ruleTruncate(tokenCounter, previousSummary, content,
                    effectiveBudget.getTruncateCharsPerStep(), maxSummaryTokens);
        } catch (Exception e) {
            log.warn("摘要生成失败，降级为规则截断：tag={}, error={}", tag, e.getMessage());
            return ContextBudgetSupport.ruleTruncate(tokenCounter, previousSummary, content,
                    effectiveBudget.getTruncateCharsPerStep(), maxSummaryTokens);
        }
    }

    /**
     * 解析摘要用的 ChatModel：优先配置的 Bean 名，其次容器中的 ChatModel
     */
    private ChatModel resolveSummaryChatModel(ContextBudgetVO budget) {
        ContextBudgetVO effectiveBudget = budget != null ? budget : contextBudget;
        String beanName = effectiveBudget.getSummaryModelBeanName();
        if (beanName != null && !beanName.isBlank() && applicationContext.containsBean(beanName)) {
            Object bean = applicationContext.getBean(beanName);
            if (bean instanceof ChatModel chatModel) {
                return chatModel;
            }
            log.warn("配置的摘要模型 Bean 不是 ChatModel：{}", beanName);
        }
        try {
            return applicationContext.getBean(ChatModel.class);
        } catch (Exception e) {
            log.warn("未找到可用于摘要的 ChatModel，将使用规则截断");
            return null;
        }
    }

    private String safeText(Message message) {
        if (message == null) {
            return "";
        }
        try {
            String text = message.getText();
            if (text != null) {
                return text;
            }
        } catch (Exception ignore) {
            // 落到下面的序列化兜底
        }
        try {
            return String.valueOf(message);
        } catch (Exception e) {
            return "";
        }
    }

}
