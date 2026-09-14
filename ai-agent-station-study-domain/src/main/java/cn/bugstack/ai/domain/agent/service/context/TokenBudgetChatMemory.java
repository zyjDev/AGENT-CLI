package cn.bugstack.ai.domain.agent.service.context;

import cn.bugstack.ai.domain.agent.model.valobj.ContextBudgetVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 基于 token 预算的对话记忆
 * <p>
 * Spring AI 1.0.0 只提供 {@code MessageWindowChatMemory}（按条数），没有 TokenWindowChatMemory，故自研。
 * <p>
 * 与 MessageWindowChatMemory 的三点差异：
 * <ol>
 *   <li>裁剪依据从「消息条数」改为「估算 token」；</li>
 *   <li>被裁掉的历史不直接丢弃，而是压成一段摘要消息保留在队首；</li>
 *   <li>压缩发生在 get()（即模型调用前），使当次请求立刻受益，而不是等到下一轮。</li>
 * </ol>
 *
 * @author xiaofuge bugstack.cn @小傅哥
 * 2026/09/14
 */
@Slf4j
public class TokenBudgetChatMemory implements ChatMemory {

    /**
     * 摘要消息前缀，用于识别并剥离上一轮摘要，避免「摘要的摘要」层层叠加
     */
    public static final String SUMMARY_PREFIX = "[历史对话摘要]\n";

    private static final int MIN_EFFECTIVE_BUDGET = 256;

    private final ChatMemoryRepository repository;

    private final ITokenCounter tokenCounter;

    private final IContextSummarizer summarizer;

    private final ContextBudgetVO budget;

    /**
     * 每会话一把锁：线程池执行下，同一 sessionId 的并发请求可能互相覆盖记忆
     */
    private final ConcurrentHashMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    /**
     * 会话摘要缓存，避免把上一轮摘要当作新内容重复压缩
     */
    private final ConcurrentHashMap<String, String> summaries = new ConcurrentHashMap<>();

    public TokenBudgetChatMemory(ChatMemoryRepository repository, ITokenCounter tokenCounter,
                                 IContextSummarizer summarizer, ContextBudgetVO budget) {
        this.repository = repository;
        this.tokenCounter = tokenCounter;
        this.summarizer = summarizer;
        this.budget = budget;
    }

    @Override
    public List<Message> get(String conversationId) {
        ReentrantLock lock = lockOf(conversationId);
        lock.lock();
        try {
            List<Message> messages = repository.findByConversationId(conversationId);
            if (messages == null || messages.size() <= 1) {
                return messages == null ? List.of() : messages;
            }

            int effectiveBudget = Math.max(MIN_EFFECTIVE_BUDGET, budget.getMemoryTokenBudget() - budget.getReserveTokens());
            int estimated = tokenCounter.estimateMessages(messages);
            boolean overCount = budget.getMaxMessages() > 0 && messages.size() > budget.getMaxMessages();

            if (estimated <= effectiveBudget * budget.getMemoryTriggerRatio() && !overCount) {
                return messages;
            }

            log.info("🧮 记忆触发压缩：conversationId={}, 消息数={}, 估算 {} token, 有效预算 {} token",
                    conversationId, messages.size(), estimated, effectiveBudget);

            List<Message> compressed = compress(conversationId, messages);
            // 压缩结果立即持久化，保证后续请求直接命中压缩后的历史
            repository.saveAll(conversationId, compressed);

            log.info("✅ 记忆压缩完成：{} 条 → {} 条, 估算 {} token",
                    messages.size(), compressed.size(), tokenCounter.estimateMessages(compressed));
            return compressed;
        } catch (Exception e) {
            // 压缩失败不能影响正常对话：退化为未压缩历史，由上层预算兜底
            log.error("记忆压缩异常，本次返回未压缩历史：conversationId={}, error={}", conversationId, e.getMessage(), e);
            return repository.findByConversationId(conversationId);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void add(String conversationId, List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }
        ReentrantLock lock = lockOf(conversationId);
        lock.lock();
        try {
            List<Message> existing = new ArrayList<>(repository.findByConversationId(conversationId));
            existing.addAll(messages);
            repository.saveAll(conversationId, existing);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void clear(String conversationId) {
        ReentrantLock lock = lockOf(conversationId);
        lock.lock();
        try {
            repository.deleteByConversationId(conversationId);
            summaries.remove(conversationId);
            locks.remove(conversationId);
        } finally {
            lock.unlock();
        }
    }

    /**
     * 保留最近 keepRecentMessages 条，其余压成摘要消息置于队首
     */
    private List<Message> compress(String conversationId, List<Message> messages) {
        int keep = Math.max(1, Math.min(budget.getKeepRecentMessages(), messages.size() - 1));
        int splitAt = messages.size() - keep;

        List<Message> head = new ArrayList<>(messages.subList(0, splitAt));
        List<Message> tail = new ArrayList<>(messages.subList(splitAt, messages.size()));

        String previousSummary = summaries.get(conversationId);
        // 剥离上一轮摘要消息，避免「摘要的摘要」层层叠加
        if (previousSummary != null && !head.isEmpty() && isSummaryMessage(head.get(0))) {
            head.remove(0);
        }

        String summary = summarizer.summarize(conversationId, previousSummary, head, budget.getMaxSummaryTokens());

        if (summary == null || summary.isBlank()) {
            // 摘要不可用：退化为纯滑动窗口，保证不超预算
            log.warn("摘要不可用，记忆退化为滑动窗口：conversationId={}", conversationId);
            return tail;
        }

        summaries.put(conversationId, summary);

        List<Message> result = new ArrayList<>(tail.size() + 1);
        // 用 UserMessage 承载摘要：PromptChatMemoryAdvisor 只会把 USER/ASSISTANT 消息渲染进 prompt，
        // 用 SystemMessage 会被静默丢弃
        result.add(new UserMessage(SUMMARY_PREFIX + summary));
        result.addAll(tail);
        return result;
    }

    private boolean isSummaryMessage(Message message) {
        if (!(message instanceof UserMessage)) {
            return false;
        }
        String text = message.getText();
        return text != null && text.startsWith(SUMMARY_PREFIX);
    }

    private ReentrantLock lockOf(String conversationId) {
        return locks.computeIfAbsent(conversationId, k -> new ReentrantLock());
    }

}
