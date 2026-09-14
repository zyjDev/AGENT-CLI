package cn.bugstack.ai.domain.agent.service.context;

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
     * B 套：把被挤出窗口的执行历史压成摘要
     *
     * @param sessionId       会话 ID（用于日志与降级）
     * @param previousSummary 上一轮摘要，可为 null
     * @param headText        需要被压缩掉的执行历史文本
     * @param maxSummaryTokens 摘要允许的最大 token
     * @return 摘要文本；返回 null / 空白表示失败，调用方需降级
     */
    String summarizeExecution(String sessionId, String previousSummary, String headText, int maxSummaryTokens);

}
