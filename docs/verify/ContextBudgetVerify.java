import cn.bugstack.ai.domain.agent.model.entity.ExecuteCommandEntity;
import cn.bugstack.ai.domain.agent.model.valobj.ContextBudgetVO;
import cn.bugstack.ai.domain.agent.service.context.ContextBudgetSupport;
import cn.bugstack.ai.domain.agent.service.context.IContextSummarizer;
import cn.bugstack.ai.domain.agent.service.context.ITokenCounter;
import cn.bugstack.ai.domain.agent.service.context.JTokkitTokenCounter;
import cn.bugstack.ai.domain.agent.service.context.TokenBudgetChatMemory;
import cn.bugstack.ai.domain.agent.service.execute.auto.step.AbstractExecuteSupport;
import cn.bugstack.ai.domain.agent.service.execute.auto.step.factory.DefaultAutoAgentExecuteStrategyFactory;
import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.tokenizer.JTokkitTokenCountEstimator;
import org.springframework.ai.tokenizer.TokenCountEstimator;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 上下文预算重构的独立验证程序
 * 不依赖 Spring 容器与数据库，直接跑真实 jtokkit + 真实 InMemoryChatMemoryRepository
 */
public class ContextBudgetVerify {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) throws Exception {
        System.out.println("================ 上下文预算重构 · 独立验证 ================\n");

        test1TokenEstimateSanity();
        test2CalibrationConvergesToTrueFactor();
        test3NullSafeMessageEstimate();
        test4MemoryCompression();
        test5SplitHistory();
        test6HardTruncate();
        test7ComposeHistory();
        test8AppendWithoutCompaction();
        test9CompactionKeepsWindowAndSummarizesHead();
        test10SingleOversizedSegmentHardTruncated();
        test11SummaryFailureKeepsOldSummary();
        test12StepCountRegression();
        test13RuleTruncateFallback();

        System.out.println("\n================ 结果：通过 " + passed + " / 失败 " + failed + " ================");
        if (failed > 0) {
            System.exit(1);
        }
    }

    // ------------------------------------------------------------------ 用例 1

    private static void test1TokenEstimateSanity() throws Exception {
        System.out.println("[用例 1] token 估算基本合理性");
        JTokkitTokenCounter counter = newCounter(defaultBudget());

        int zh = counter.estimate("你好，世界");
        int en = counter.estimate("Hello, world");
        int empty = counter.estimate("");
        int nullText = counter.estimate(null);

        System.out.println("  中文「你好，世界」 = " + zh + " token");
        System.out.println("  英文「Hello, world」 = " + en + " token");

        check("中文估算 > 0", zh > 0);
        check("英文估算 > 0", en > 0);
        check("空串估算 = 0", empty == 0);
        check("null 估算 = 0（不抛 NPE）", nullText == 0);
    }

    // ------------------------------------------------------------------ 用例 2

    /**
     * 这是本次实施中修掉的那个数学错误的回归用例。
     * 用「线性 EMA」实现时，不动点会落在 sqrt(f*)，真实系数 1.6 只能收敛到 1.265。
     */
    private static void test2CalibrationConvergesToTrueFactor() throws Exception {
        System.out.println("\n[用例 2] 校准系数收敛性（回归：线性 EMA 会收敛到 sqrt(f*)）");

        JTokkitTokenCounter counter = newCounter(defaultBudget());

        String text = "请分析当前任务的状态，评估已经执行过的步骤，并给出下一步的具体执行策略。"
                + "需要特别关注数据一致性、异常处理与边界条件，同时给出可验证的验收标准。";

        // 以 jtokkit 的「原始估算」为基准，构造一个固定的真实 token 数（真实系数 = 1.6）
        TokenCountEstimator rawEstimator = new JTokkitTokenCountEstimator();
        int rawTokens = rawEstimator.estimate(text);
        double trueFactor = 1.6d;
        int trueTokens = (int) Math.round(rawTokens * trueFactor);

        for (int i = 0; i < 300; i++) {
            int estimated = counter.estimate(text);
            counter.calibrate(estimated, trueTokens);
        }

        double factor = counter.calibrationFactor();
        double linearEmaResult = Math.sqrt(trueFactor);

        System.out.printf("  原始估算 = %d token，构造真实值 = %d token（真实系数 %.3f）%n",
                rawTokens, trueTokens, trueFactor);
        System.out.printf("  收敛系数 = %.4f%n", factor);
        System.out.printf("  若用线性 EMA 会停在 sqrt(%.2f) = %.4f%n", trueFactor, linearEmaResult);
        System.out.printf("  校准后估算 = %d token（真实 %d）%n", counter.estimate(text), trueTokens);

        check("校准系数收敛到真实系数 1.6（而非 sqrt 值 1.265）",
                Math.abs(factor - trueFactor) < 0.05);
        check("校准后估算值贴近真实值",
                Math.abs(counter.estimate(text) - trueTokens) <= Math.max(2, rawTokens * 0.05));
    }

    // ------------------------------------------------------------------ 用例 3

    private static void test3NullSafeMessageEstimate() throws Exception {
        System.out.println("\n[用例 3] 消息列表估算的 null 安全性");
        JTokkitTokenCounter counter = newCounter(defaultBudget());

        List<Message> messages = List.of(
                new UserMessage("你好"),
                new AssistantMessage("你好，有什么可以帮你？"));

        int est = counter.estimateMessages(messages);
        int emptyList = counter.estimateMessages(List.of());
        int nullList = counter.estimateMessages(null);

        // 含 null 元素：ToolResponseMessage 之类 getText() 可能为 null，这里验证兜底不炸
        int withNullElement = counter.estimateMessages(Arrays.asList(new UserMessage("hi"), null));

        System.out.println("  2 条消息估算 = " + est + " token");
        System.out.println("  含 null 元素估算 = " + withNullElement + " token（未抛异常）");

        check("消息列表估算 > 0", est > 0);
        check("空列表估算 = 0", emptyList == 0);
        check("null 列表估算 = 0", nullList == 0);
        check("含 null 元素不抛异常", withNullElement > 0);
    }

    // ------------------------------------------------------------------ 用例 4

    private static void test4MemoryCompression() throws Exception {
        System.out.println("\n[用例 4] TokenBudgetChatMemory 超预算触发摘要压缩");
        JTokkitTokenCounter counter = newCounter(defaultBudget());

        ContextBudgetVO budget = ContextBudgetVO.builder()
                .memoryTokenBudget(200)
                .reserveTokens(0)
                .memoryTriggerRatio(0.8)
                .keepRecentMessages(2)
                .maxSummaryTokens(50)
                .maxMessages(0)
                .summaryMode("TRUNCATE")
                .build();

        IContextSummarizer stubSummarizer = new IContextSummarizer() {
            @Override
            public String summarize(String conversationId, String previousSummary, List<Message> head, int maxSummaryTokens) {
                return "STUB_SUMMARY(head=" + head.size() + ",prev=" + (previousSummary == null ? "null" : "set") + ")";
            }

            @Override
            public String summarizeExecution(String sessionId, String previousSummary, String headText, int maxSummaryTokens) {
                return "STUB_EXEC";
            }
        };

        InMemoryChatMemoryRepository repository = new InMemoryChatMemoryRepository();
        TokenBudgetChatMemory memory = new TokenBudgetChatMemory(repository, counter, stubSummarizer, budget);

        String cid = "sess-verify-1";
        List<Message> batch = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            batch.add(new UserMessage("第 " + i + " 条历史消息，内容刻意写长一些，用来把估算 token 顶过 200 × 0.8 的压缩水位。"));
        }
        int beforeTokens = counter.estimateMessages(batch);
        memory.add(cid, batch);

        List<Message> afterCompress = memory.get(cid);
        int afterTokens = counter.estimateMessages(afterCompress);

        System.out.println("  压缩前：" + batch.size() + " 条 / " + beforeTokens + " token");
        System.out.println("  压缩后：" + afterCompress.size() + " 条 / " + afterTokens + " token");
        System.out.println("  队首消息：" + preview(afterCompress.get(0).getText(), 70));

        check("超预算触发压缩，10 条 → 3 条（1 条摘要 + 保留最近 2 条）", afterCompress.size() == 3);
        check("队首为摘要消息（SUMMARY_PREFIX）",
                afterCompress.get(0).getText().startsWith(TokenBudgetChatMemory.SUMMARY_PREFIX));
        check("摘要收到的是被挤出的 8 条（head=8）",
                afterCompress.get(0).getText().contains("head=8"));
        check("压缩后 token 显著下降", afterTokens < beforeTokens / 2);
        check("压缩结果已回写仓库", repository.findByConversationId(cid).size() == 3);

        // 再取一次：已低于水位，不应重复压缩（避免每轮都白调一次模型）
        List<Message> second = memory.get(cid);
        System.out.println("  二次 get：" + second.size() + " 条 / " + counter.estimateMessages(second) + " token");
        check("未超水位时不重复压缩", second.size() == 3);

        // clear 后应彻底清空
        memory.clear(cid);
        check("clear 后记忆为空", repository.findByConversationId(cid).isEmpty());
    }

    // ------------------------------------------------------------------ 用例 5

    private static void test5SplitHistory() {
        System.out.println("\n[用例 5] 执行历史按步切分");
        String history = "=== 第 1 步完整记录 ===\n【分析阶段】A\n【执行阶段】B\n"
                + "=== 第 2 步完整记录 ===\n【分析阶段】C\n"
                + "=== 第 3 步完整记录 ===\n【分析阶段】D\n";

        List<String> segments = ContextBudgetSupport.splitHistory(history);
        System.out.println("  切出 " + segments.size() + " 段");
        for (int i = 0; i < segments.size(); i++) {
            System.out.println("    段 " + (i + 1) + " 首行 = " + preview(segments.get(i).trim(), 30));
        }

        check("3 步历史切出 3 段", segments.size() == 3);
        check("无分隔符时返回 1 段（退化分支）",
                ContextBudgetSupport.splitHistory("一段没有分隔符的文本").size() == 1);
        check("空文本返回空列表", ContextBudgetSupport.splitHistory("").isEmpty());
        check("null 返回空列表", ContextBudgetSupport.splitHistory(null).isEmpty());
    }

    // ------------------------------------------------------------------ 用例 6

    private static void test6HardTruncate() throws Exception {
        System.out.println("\n[用例 6] 按 token 上限硬截断");
        JTokkitTokenCounter counter = newCounter(defaultBudget());

        String longText = "这是一段很长的执行记录。".repeat(120);
        int originalTokens = counter.estimate(longText);
        int maxTokens = 50;

        String truncated = ContextBudgetSupport.hardTruncate(counter, longText, maxTokens);
        int truncatedTokens = counter.estimate(truncated);

        System.out.println("  原文：" + longText.length() + " 字符 / " + originalTokens + " token");
        System.out.println("  截断：" + truncated.length() + " 字符 / " + truncatedTokens + " token（上限 " + maxTokens + "）");
        System.out.println("  尾部标记：" + preview(truncated.substring(Math.max(0, truncated.length() - 12)), 20));

        check("截断后明显短于原文", truncatedTokens < originalTokens / 2);
        // 二分找到的前缀不超过 maxTokens，尾部再补 "[已截断]" 标记，故允许小幅溢出
        check("截断后不超过上限 + 标记开销", truncatedTokens <= maxTokens + 20);
        check("未超限时原样返回",
                ContextBudgetSupport.hardTruncate(counter, "短文本", 100).equals("短文本"));
    }

    // ==================================================================================
    // B 套：执行历史（executionHistory）的 token 预算 + 摘要压缩
    //
    // 改造前：executionHistory 是无界 StringBuilder，每步 append 后线性增长；
    //        且 Step2 与 Step3 各 append 一次，同一步记录出现两遍。
    // 改造后：appendHistory() 统一入口 → 超预算时 compactHistoryIfNeeded() 把较早的步骤
    //        压成 historySummary，只保留最近 N 步完整记录；composeHistory() 供 prompt 取用。
    // ==================================================================================

    // ------------------------------------------------------------------ 用例 7

    private static void test7ComposeHistory() throws Exception {
        System.out.println("\n[用例 7] composeHistory 的三种形态（替换原 executionHistory.toString()）");
        TestSupport support = newSupport(defaultBudget(), newCounter(defaultBudget()), new RecordingSummarizer());

        DefaultAutoAgentExecuteStrategyFactory.DynamicContext none = newContext();
        String noneText = support.compose(none);

        DefaultAutoAgentExecuteStrategyFactory.DynamicContext onlyHistory = newContext();
        onlyHistory.setExecutionHistory(new StringBuilder("=== 第 1 步完整记录 ===\nA"));
        String historyText = support.compose(onlyHistory);

        DefaultAutoAgentExecuteStrategyFactory.DynamicContext both = newContext();
        both.setHistorySummary("早期摘要内容");
        both.setExecutionHistory(new StringBuilder("=== 第 3 步完整记录 ===\nC"));
        String bothText = support.compose(both);

        System.out.println("  空上下文      → " + preview(noneText, 40));
        System.out.println("  只有历史      → " + preview(historyText, 60));
        System.out.println("  摘要 + 历史   → " + preview(bothText, 100));

        check("无摘要无历史 → [首次执行]", "[首次执行]".equals(noneText));
        check("只有历史 → 含「最近步骤完整记录」且不含摘要段",
                historyText.contains("=== 最近步骤完整记录 ===") && !historyText.contains("早期步骤摘要"));
        check("摘要 + 历史 → 两段都出现",
                bothText.contains("=== 早期步骤摘要（已压缩） ===") && bothText.contains("=== 最近步骤完整记录 ==="));
        check("摘要段排在历史段之前（时间顺序）",
                bothText.indexOf("早期步骤摘要") < bothText.indexOf("最近步骤完整记录"));
        check("只有摘要时也能正常输出（历史段可缺省）", composeOnlySummary(support).contains("早期步骤摘要"));
    }

    private static String composeOnlySummary(TestSupport support) {
        DefaultAutoAgentExecuteStrategyFactory.DynamicContext ctx = newContext();
        ctx.setHistorySummary("只有摘要");
        return support.compose(ctx);
    }

    // ------------------------------------------------------------------ 用例 8

    private static void test8AppendWithoutCompaction() throws Exception {
        System.out.println("\n[用例 8] 未超水位时不压缩（避免每步白调一次摘要模型）");
        JTokkitTokenCounter counter = newCounter(defaultBudget());
        int perStep = counter.estimate(stepRecord(1, 8));
        int budget = (int) Math.round(perStep * 2.8d);
        RecordingSummarizer summarizer = new RecordingSummarizer();
        TestSupport support = newSupport(budgetForHistory(budget), counter, summarizer);

        DefaultAutoAgentExecuteStrategyFactory.DynamicContext ctx = newContext();
        ExecuteCommandEntity req = request();

        support.append(req, ctx, stepRecord(1, 8));
        support.append(req, ctx, stepRecord(2, 8));

        String history = ctx.getExecutionHistory().toString();
        int segments = ContextBudgetSupport.splitHistory(history).size();

        System.out.printf("  单步 ≈ %d token；historyTokenBudget=%d，触发水位=%d%n",
                perStep, budget, (int) (budget * 0.8d));
        System.out.printf("  追加 2 步后：%d 段 / %d token%n", segments, counter.estimate(history));

        check("2 步未超水位，未触发压缩（historySummary 仍为 null）", ctx.getHistorySummary() == null);
        check("摘要生成器一次都没被调用", summarizer.execCallCount == 0);
        check("历史保留 2 段完整记录", segments == 2);
        check("executedSteps 递增到 2", ctx.getExecutedSteps() == 2);
        check("step 字段不被 appendHistory 篡改（仍为初始值 1）", ctx.getStep() == 1);
    }

    // ------------------------------------------------------------------ 用例 9

    private static void test9CompactionKeepsWindowAndSummarizesHead() throws Exception {
        System.out.println("\n[用例 9] 超预算压缩：被挤出的步骤进摘要，近期窗口保留完整记录");
        JTokkitTokenCounter counter = newCounter(defaultBudget());
        int perStep = counter.estimate(stepRecord(1, 8));
        int budget = (int) Math.round(perStep * 2.8d);
        RecordingSummarizer summarizer = new RecordingSummarizer();
        TestSupport support = newSupport(budgetForHistory(budget), counter, summarizer);

        DefaultAutoAgentExecuteStrategyFactory.DynamicContext ctx = newContext();
        ExecuteCommandEntity req = request();

        support.append(req, ctx, stepRecord(1, 8));
        support.append(req, ctx, stepRecord(2, 8));
        support.append(req, ctx, stepRecord(3, 8));

        String history = ctx.getExecutionHistory().toString();
        int segments = ContextBudgetSupport.splitHistory(history).size();

        System.out.printf("  单步 ≈ %d token；触发水位 = %d%n", perStep, (int) (budget * 0.8d));
        System.out.println("  追加 3 步后 → 摘要：" + preview(ctx.getHistorySummary(), 50));
        System.out.printf("  近期窗口：%d 段 / %d token%n", segments, counter.estimate(history));
        System.out.printf("  摘要收到 head：%d 段（含第 1 步=%b，含第 2 步=%b）%n",
                summarizer.lastHeadSegments,
                summarizer.lastExecHead != null && summarizer.lastExecHead.contains("第 1 步"),
                summarizer.lastExecHead != null && summarizer.lastExecHead.contains("第 2 步"));

        check("触发压缩，摘要非空", ctx.getHistorySummary() != null);
        check("近期窗口只保留 1 段（historyKeepSteps=1）", segments == 1);
        check("保留的是最新一步（第 3 步）", history.contains("第 3 步"));
        check("被挤出的第 1 步已不在近期窗口", !history.contains("第 1 步"));
        check("摘要收到的 head 恰好 2 段", summarizer.lastHeadSegments == 2);
        check("head 覆盖第 1、2 步",
                summarizer.lastExecHead.contains("第 1 步") && summarizer.lastExecHead.contains("第 2 步"));
        check("首次压缩时 previousSummary 为 null（无「摘要的摘要」）", summarizer.lastExecPrev == null);
        check("摘要生成器只被调用 1 次", summarizer.execCallCount == 1);

        String composed = support.compose(ctx);
        System.out.println("  composeHistory → " + preview(composed, 110));
        check("composeHistory 同时含摘要段与近期窗口段",
                composed.contains("=== 早期步骤摘要（已压缩） ===") && composed.contains("=== 最近步骤完整记录 ==="));
        check("摘要文本确实出现在 composeHistory 里（信息未丢）",
                composed.contains("SUMMARY[head=2]"));
        check("压缩后 token 低于水位（不会立刻二次压缩）",
                counter.estimate(composed) <= budget * 0.8d);

        // 再走两步：第 4 步后仍未超，第 5 步后再次超 → 验证 previousSummary 传递
        support.append(req, ctx, stepRecord(4, 8));
        support.append(req, ctx, stepRecord(5, 8));

        System.out.println("  继续追加第 4、5 步 → previousSummary = "
                + preview(summarizer.lastExecPrev, 30));
        check("二次压缩把旧摘要作为 previousSummary 传入", summarizer.lastExecPrev != null);
        check("二次压缩后窗口仍为 1 段",
                ContextBudgetSupport.splitHistory(ctx.getExecutionHistory().toString()).size() == 1);
        check("二次压缩后仍是 5 步（executedSteps 不受压缩影响）", ctx.getExecutedSteps() == 5);
    }

    // ------------------------------------------------------------------ 用例 10

    private static void test10SingleOversizedSegmentHardTruncated() throws Exception {
        System.out.println("\n[用例 10] 单步记录即超预算：无法再拆分 → 硬截断（避免反复压缩却不缩小）");
        JTokkitTokenCounter counter = newCounter(defaultBudget());
        int perStep = counter.estimate(stepRecord(1, 8));
        int budget = (int) Math.round(perStep * 2.8d);
        RecordingSummarizer summarizer = new RecordingSummarizer();
        TestSupport support = newSupport(budgetForHistory(budget), counter, summarizer);

        DefaultAutoAgentExecuteStrategyFactory.DynamicContext ctx = newContext();
        ExecuteCommandEntity req = request();

        support.append(req, ctx, stepRecord(1, 32));   // 单步 ≈ 4 倍常规步长

        String history = ctx.getExecutionHistory().toString();
        System.out.printf("  单步 %d token → 截断为 %d token（有效预算 %d）%n",
                counter.estimate(stepRecord(1, 32)), counter.estimate(history), budget);
        System.out.println("  尾部：" + preview(history.substring(Math.max(0, history.length() - 12)), 20));

        check("单步即超预算，未走摘要分支（摘要仍为 null）", ctx.getHistorySummary() == null);
        check("摘要生成器未被调用", summarizer.execCallCount == 0);
        check("记录被硬截断（带 [已截断] 标记）", history.endsWith("[已截断]"));
        check("截断后 token 不超有效预算 + 标记开销", counter.estimate(history) <= budget + 20);
        check("截断后确实比原文短", counter.estimate(history) < counter.estimate(stepRecord(1, 32)));
        check("executedSteps 仍为 1", ctx.getExecutedSteps() == 1);
    }

    // ------------------------------------------------------------------ 用例 11

    private static void test11SummaryFailureKeepsOldSummary() throws Exception {
        System.out.println("\n[用例 11] 摘要生成失败（LLM 超时/降级返回 null）时：保旧摘要 + 仍裁剪窗口");
        JTokkitTokenCounter counter = newCounter(defaultBudget());
        int perStep = counter.estimate(stepRecord(1, 8));
        int budget = (int) Math.round(perStep * 2.8d);
        RecordingSummarizer summarizer = new RecordingSummarizer();
        TestSupport support = newSupport(budgetForHistory(budget), counter, summarizer);

        DefaultAutoAgentExecuteStrategyFactory.DynamicContext ctx = newContext();
        ExecuteCommandEntity req = request();

        support.append(req, ctx, stepRecord(1, 8));
        support.append(req, ctx, stepRecord(2, 8));
        support.append(req, ctx, stepRecord(3, 8));

        String firstSummary = ctx.getHistorySummary();
        check("第一次压缩成功拿到摘要", firstSummary != null);

        summarizer.returnNull = true;
        support.append(req, ctx, stepRecord(4, 8));
        support.append(req, ctx, stepRecord(5, 8));

        String history = ctx.getExecutionHistory().toString();
        System.out.println("  摘要失败后的 historySummary = " + preview(ctx.getHistorySummary(), 40));
        System.out.println("  近期窗口段数 = " + ContextBudgetSupport.splitHistory(history).size());

        check("摘要失败时保留旧摘要（不丢已压缩的信息）", firstSummary.equals(ctx.getHistorySummary()));
        check("摘要失败时仍裁剪窗口（防止反复压缩却不缩小）",
                ContextBudgetSupport.splitHistory(history).size() == 1);
        check("摘要失败也不影响步数统计", ctx.getExecutedSteps() == 5);
    }

    // ------------------------------------------------------------------ 用例 12

    /**
     * Step4 原来用 executionHistory 里 "=== 第" 的段数推导已执行步数。
     * 引入压缩后这个推导必然失真——本用例把它固化成回归断言。
     */
    private static void test12StepCountRegression() throws Exception {
        System.out.println("\n[用例 12] 回归：压缩后「历史段数」≠「实际步数」，Step4 必须用 executedSteps");
        JTokkitTokenCounter counter = newCounter(defaultBudget());
        int perStep = counter.estimate(stepRecord(1, 8));
        int budget = (int) Math.round(perStep * 2.8d);
        TestSupport support = newSupport(budgetForHistory(budget), counter, new RecordingSummarizer());

        DefaultAutoAgentExecuteStrategyFactory.DynamicContext ctx = newContext();
        ExecuteCommandEntity req = request();
        for (int i = 1; i <= 3; i++) {
            support.append(req, ctx, stepRecord(i, 8));
        }

        String history = ctx.getExecutionHistory().toString();
        int segmentsByOldApproach = ContextBudgetSupport.splitHistory(history).size();
        int stepsByNewApproach = ctx.getExecutedSteps();

        System.out.printf("  旧算法（split(\"=== 第\") 段数）= %d%n", segmentsByOldApproach);
        System.out.printf("  新算法（executedSteps）      = %d%n", stepsByNewApproach);
        System.out.printf("  若不修，Step4 会把 3 步的任务报成 %d 步%n", segmentsByOldApproach);

        check("两者确实不一致（证明必须修）", segmentsByOldApproach != stepsByNewApproach);
        check("executedSteps 给出正确的 3", stepsByNewApproach == 3);
        check("历史段数只剩 1（被压缩掉了）", segmentsByOldApproach == 1);
    }

    // ------------------------------------------------------------------ 用例 13

    private static void test13RuleTruncateFallback() throws Exception {
        System.out.println("\n[用例 13] ruleTruncate 规则降级（LLM 摘要不可用时的兜底）");
        JTokkitTokenCounter counter = newCounter(defaultBudget());

        String content = stepRecord(1, 3) + stepRecord(2, 3) + stepRecord(3, 3);

        // a) 每段按 charsPerSegment 截断（实现里对 charsPerSegment 有 64 的下限）
        String out = ContextBudgetSupport.ruleTruncate(counter, null, content, 64, 5000);
        int dots = countOccurrences(out, "...");
        System.out.printf("  原文 %d 字符 → 降级输出 %d 字符，出现 %d 个「...」%n",
                content.length(), out.length(), dots);

        check("3 段各自被截断（3 个「...」）", dots == 3);
        check("降级输出明显短于原文", out.length() < content.length());

        // b) 保留 previousSummary 前缀
        String withPrev = ContextBudgetSupport.ruleTruncate(counter, "上一轮摘要", content, 64, 5000);
        check("保留 previousSummary 前缀", withPrev.startsWith("上一轮摘要"));

        // c) 总长受 maxSummaryTokens 约束
        String capped = ContextBudgetSupport.ruleTruncate(counter, "上一轮摘要", content, 64, 60);
        System.out.printf("  maxSummaryTokens=60 → 实际 %d token%n", counter.estimate(capped));
        check("总 token 不超上限 + 截断标记开销", counter.estimate(capped) <= 60 + 20);
        check("超限时带 [已截断] 标记", capped.contains("[已截断]"));

        // d) 空输入不抛异常
        check("空内容返回可预期结果（不抛 NPE）",
                ContextBudgetSupport.ruleTruncate(counter, null, "", 64, 100) != null);
    }

    // ------------------------------------------------------------------ 辅助

    /**
     * AbstractExecuteSupport 的最小可实例化子类。
     * 只暴露 B 套的三个 protected 方法；tokenCounter / contextSummarizer / contextBudget
     * 是 protected 字段，外部类无法直接赋值，故由子类自己完成注入。
     */
    private static class TestSupport extends AbstractExecuteSupport {

        @Override
        protected String doApply(ExecuteCommandEntity requestParameter,
                                 DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext) {
            return null;
        }

        @Override
        public StrategyHandler<ExecuteCommandEntity, DefaultAutoAgentExecuteStrategyFactory.DynamicContext, String> get(
                ExecuteCommandEntity requestParameter,
                DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext) {
            return null;
        }

        void inject(ITokenCounter counter, IContextSummarizer summarizer, ContextBudgetVO budget) {
            this.tokenCounter = counter;
            this.contextSummarizer = summarizer;
            this.contextBudget = budget;
        }

        void append(ExecuteCommandEntity req,
                    DefaultAutoAgentExecuteStrategyFactory.DynamicContext ctx,
                    String record) {
            appendHistory(req, ctx, record);
        }

        String compose(DefaultAutoAgentExecuteStrategyFactory.DynamicContext ctx) {
            return composeHistory(ctx);
        }
    }

    /** 摘要器替身：记录被传入的 head / previousSummary，可切换为「返回 null」模拟降级 */
    private static class RecordingSummarizer implements IContextSummarizer {

        String lastExecHead;
        String lastExecPrev;
        int lastHeadSegments;
        int execCallCount;
        boolean returnNull;

        @Override
        public String summarize(String conversationId, String previousSummary, List<Message> head, int maxSummaryTokens) {
            return "STUB_SUMMARY(head=" + head.size() + ")";
        }

        @Override
        public String summarizeExecution(String sessionId, String previousSummary, String headText, int maxSummaryTokens) {
            execCallCount++;
            lastExecHead = headText;
            lastExecPrev = previousSummary;
            lastHeadSegments = ContextBudgetSupport.splitHistory(headText).size();
            if (returnNull) {
                return null;
            }
            return "SUMMARY[head=" + lastHeadSegments + "]";
        }
    }

    private static TestSupport newSupport(ContextBudgetVO budget, ITokenCounter counter, IContextSummarizer summarizer) {
        TestSupport support = new TestSupport();
        support.inject(counter, summarizer, budget);
        return support;
    }

    private static DefaultAutoAgentExecuteStrategyFactory.DynamicContext newContext() {
        DefaultAutoAgentExecuteStrategyFactory.DynamicContext ctx =
                new DefaultAutoAgentExecuteStrategyFactory.DynamicContext();
        ctx.setExecutionHistory(new StringBuilder());
        return ctx;
    }

    private static ExecuteCommandEntity request() {
        return ExecuteCommandEntity.builder()
                .aiAgentId("3")
                .sessionId("sess-b-verify")
                .maxStep(5)
                .build();
    }

    /**
     * 造一条形如 Step2 / Step3 真实写入格式的执行记录。
     * 注意首行必须用 "=== 第 N 步" 作为分段标记，与 ContextBudgetSupport.HISTORY_SEGMENT_DELIMITER 一致。
     */
    private static String stepRecord(int step, int repeat) {
        return "=== 第 " + step + " 步完整记录 ===\n"
                + "【分析阶段】" + "需要分析当前任务状态并制定下一步执行策略。".repeat(repeat) + "\n"
                + "【执行阶段】" + "调用工具获取数据并处理，注意异常与边界条件。".repeat(repeat) + "\n"
                + "【监督阶段】" + "检查结果是否符合预期并给出验收结论。".repeat(repeat) + "\n";
    }

    /** B 套预算：reserveTokens=0、keepSteps=1，便于用例精确控制压缩行为 */
    private static ContextBudgetVO budgetForHistory(int historyTokenBudget) {
        return ContextBudgetVO.builder()
                .historyTokenBudget(historyTokenBudget)
                .historyTriggerRatio(0.8d)
                .historyKeepSteps(1)
                .reserveTokens(0)
                .maxSummaryTokens(60)
                .build();
    }

    private static int countOccurrences(String text, String needle) {
        if (text == null || needle == null || needle.isEmpty()) {
            return 0;
        }
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(needle, idx)) >= 0) {
            count++;
            idx += needle.length();
        }
        return count;
    }

    private static ContextBudgetVO defaultBudget() {
        return ContextBudgetVO.builder()
                .calibrationEnabled(true)
                .calibrationAlpha(0.3d)
                .calibrationMinSamples(2)
                .build();
    }

    /**
     * JTokkitTokenCounter 是 Spring Bean（字段注入 ContextBudgetVO），
     * 这里用反射注入，避免为了验证而引入整个 Spring 容器。
     */
    private static JTokkitTokenCounter newCounter(ContextBudgetVO budget) throws Exception {
        JTokkitTokenCounter counter = new JTokkitTokenCounter();
        Field field = JTokkitTokenCounter.class.getDeclaredField("contextBudget");
        field.setAccessible(true);
        field.set(counter, budget);
        return counter;
    }

    private static void check(String name, boolean condition) {
        if (condition) {
            passed++;
            System.out.println("    [PASS] " + name);
        } else {
            failed++;
            System.out.println("    [FAIL] " + name);
        }
    }

    private static String preview(String text, int max) {
        if (text == null) {
            return "null";
        }
        String oneLine = text.replace("\n", "\\n");
        return oneLine.length() <= max ? oneLine : oneLine.substring(0, max) + "...";
    }

}
