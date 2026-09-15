package cn.bugstack.ai.domain.agent.service.context;

import cn.bugstack.ai.domain.agent.model.valobj.ContextBudgetVO;
import org.springframework.ai.chat.messages.Message;

import java.util.List;

/**
 * 上下文摘要抽象
 * <p>
 * 被挤出预算窗口的历史不直接丢弃，而是压成一段摘要保留，避免多轮/多步之后彻底失忆。
 *
 * @author xiaofuge bugstack.cn @小傅哥
 * 2026/09/14
 */
public interface IContextSummarizer {

    /**
     * A 套：把被挤出窗口的历史消息压成摘要
     *
     * @param conversationId  会话 ID（用于日志与降级）
     * @param previousSummary 上一轮摘要，可为 null
     * @param head            需要被压缩掉的历史消息（不含上一轮摘要消息）
     * @param maxSummaryTokens 摘要允许的最大 token
     * @return 摘要文本；返回 null / 空白表示失败，调用方需降级
     */
    String summarize(String conversationId, String previousSummary, List<Message> head, int maxSummaryTokens);

    /**
     * A 套：按调用方预算配置生成摘要，支持客户端级 summaryModelBeanName / summaryMode 覆盖
     */
    default String summarize(String conversationId, String previousSummary, List<Message> head, ContextBudgetVO budget) {
        int max = budget == null ? 400 : budget.getMaxSummaryTokens();
        return summarize(conversationId, previousSummary, head, max);
    }

    /**
     * B 套：把被挤出窗口的执行历史压成摘要
     *
     * @param sessionId       会话 ID（用于日志与降级）
     * @param previousSummary 上一轮摘要，可为 null
     * @param headText        需要被压缩掉的执行历史文本
     * @param maxSummaryTokens 摘要允许的最大 token
     * @return 摘要文本；返回 null / 空白表示失败，调用方需降级
     */
    String summarizeExecution(String sessionId, String previousSummary, String headText, int maxSummaryTokens);

    /**
     * B 套：按调用方预算配置生成摘要
     */
    default String summarizeExecution(String sessionId, String previousSummary, String headText, ContextBudgetVO budget) {
        int max = budget == null ? 400 : budget.getMaxSummaryTokens();
        return summarizeExecution(sessionId, previousSummary, headText, max);
    }

}
