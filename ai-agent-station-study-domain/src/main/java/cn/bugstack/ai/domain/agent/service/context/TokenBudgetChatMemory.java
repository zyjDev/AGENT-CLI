package cn.bugstack.ai.domain.agent.service.context;

import cn.bugstack.ai.domain.agent.model.valobj.ContextBudgetVO;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.ArrayList;
import java.util.List;
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

    /**
     * 会话锁分片数。
     * <p>
     * ⚠️ 原实现是 {@code ConcurrentHashMap<String, ReentrantLock>}：会话数增长即内存无界增长；
     * 且 clear() 里 remove 一把正在被持有的锁会直接破坏互斥语义（新线程拿到新锁，与旧线程并行）。
     * 改为固定分片锁后内存恒定，代价是不同会话可能落到同一把锁上 —— 只降低并发度，不影响正确性。
     */
    private static final int LOCK_STRIPES = 64;

    private static final int MIN_CACHE_CONVERSATIONS = 256;

    private final ChatMemoryRepository repository;

    private final ITokenCounter tokenCounter;

    private final IContextSummarizer summarizer;

    private final ContextBudgetVO budget;

    /**
     * 固定分片锁：同一会话恒定命中同一把锁
     */
    private final ReentrantLock[] locks = new ReentrantLock[LOCK_STRIPES];

    /**
     * 会话摘要缓存，避免把上一轮摘要当作新内容重复压缩。
     * <p>
     * 带上限：淘汰是安全的 —— {@link #compress} 在缓存缺失时会从队首摘要消息本体恢复旧摘要。
     */
    private final Cache<String, String> summaries;

    /**
     * 上次因「已无可压缩内容」而跳过压缩时的消息条数，仅用于抑制重复告警。
     * <p>
     * 带上限：被淘汰最多多打一条 warn，无正确性影响。
     */
    private final Cache<String, Integer> skipWarnedAt;

    public TokenBudgetChatMemory(ChatMemoryRepository repository, ITokenCounter tokenCounter,
                                 IContextSummarizer summarizer, ContextBudgetVO budget) {
        this.repository = repository;
        this.tokenCounter = tokenCounter;
        this.summarizer = summarizer;
        this.budget = budget;

        for (int i = 0; i < LOCK_STRIPES; i++) {
            this.locks[i] = new ReentrantLock();
        }

        // 与固定分片锁一起，让本类的全部状态都是有界的（原实现三个 Map 均无界）
        long maxConversations = Math.max(MIN_CACHE_CONVERSATIONS, budget.getMemoryCacheMaxConversations());
        this.summaries = CacheBuilder.newBuilder().maximumSize(maxConversations).build();
        this.skipWarnedAt = CacheBuilder.newBuilder().maximumSize(maxConversations).build();
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

            // ⚠️ 关键短路：预算已超，但可能已经「无可压缩的实质内容」。
            //    压缩产出固定是「1 条摘要 + keep 条原始消息」，条数恰为 keep + 1；
            //    若此处不短路，下一次压缩的 head 会正好只剩那条摘要，剥离后为空，
            //    摘要器只能原样返回旧摘要 —— 条数与 token 都不变，于是每次 get() 都会
            //    白调一次摘要模型（真实环境实测：每轮叠加 401 重试 + 8s 超时），且永远不收敛。
            if (!hasCompressibleContent(messages)) {
                Integer warned = skipWarnedAt.asMap().put(conversationId, messages.size());
                if (warned == null || warned != messages.size()) {
                    log.warn("⚠️ 记忆超出预算但已无可压缩内容（保留窗口 {} 条），跳过压缩：" +
                                    "conversationId={}, 消息数={}, 估算 {} token, 有效预算 {} token。" +
                                    "如需继续压缩，请调小 keepRecentMessages 或调大 tokenBudget",
                            budget.getKeepRecentMessages(), conversationId, messages.size(), estimated, effectiveBudget);
                }
                return messages;
            }
            skipWarnedAt.invalidate(conversationId);

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
            summaries.invalidate(conversationId);
            skipWarnedAt.invalidate(conversationId);
            // 注意：分片锁是常驻对象，不随会话清理（原实现在此 remove 会话锁，会破坏互斥语义）
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

        String previousSummary = summaries.getIfPresent(conversationId);
        // 剥离上一轮摘要消息，避免「摘要的摘要」层层叠加
        if (!head.isEmpty() && isSummaryMessage(head.get(0))) {
            Message first = head.remove(0);
            if (previousSummary == null) {
                // 兜底：摘要缓存缺失（含被缓存上限淘汰）时从消息本体恢复旧摘要，
                // 避免这段历史被整体丢弃
                String text = first.getText();
                previousSummary = text == null ? null : text.substring(SUMMARY_PREFIX.length());
            }
        }

        String summary = summarizer.summarize(conversationId, previousSummary, head, budget);

        if (summary == null || summary.isBlank()) {
            // 摘要不可用：退化为纯滑动窗口，保证不超预算
            log.warn("摘要不可用，记忆退化为滑动窗口：conversationId={}", conversationId);
            return tail;
        }

        summaries.put(conversationId, summary);

        List<Message> result = new ArrayList<>(tail.size() + 1);
        // 用 UserMessage 承载摘要：摘要是「对前序对话的转述」，以用户轮次注入最自然，
        // 也不会像 SystemMessage 那样与业务 system prompt 争夺指令优先级。
        // （历史原因：早期 PromptChatMemoryAdvisor 只渲染 USER/ASSISTANT，SystemMessage 会被静默丢弃；
        //  现为 MessageChatMemoryAdvisor，不再过滤消息类型，但该承载方式依旧是最优选择）
        result.add(new UserMessage(SUMMARY_PREFIX + summary));
        result.addAll(tail);
        return result;
    }

    /**
     * 是否还有可压缩的「实质消息」。
     * <p>
     * 判定口径必须与 {@link #compress} 的 head 完全一致：先排除队首的旧摘要消息，
     * 剩下的实质消息条数若不超过保留窗口，压缩就没有任何可做的事。
     *
     * @param messages 当前全部消息
     * @return true 表示存在可被压成摘要的历史
     */
    private boolean hasCompressibleContent(List<Message> messages) {
        int substantive = messages.size();
        if (!messages.isEmpty() && isSummaryMessage(messages.get(0))) {
            substantive--;
        }
        return substantive > budget.getKeepRecentMessages();
    }

    private boolean isSummaryMessage(Message message) {
        if (!(message instanceof UserMessage)) {
            return false;
        }
        String text = message.getText();
        return text != null && text.startsWith(SUMMARY_PREFIX);
    }

    private ReentrantLock lockOf(String conversationId) {
        return locks[Math.floorMod(String.valueOf(conversationId).hashCode(), LOCK_STRIPES)];
    }

}
