package cn.bugstack.ai.domain.agent.service.context;

import java.util.ArrayList;
import java.util.List;

/**
 * 上下文预算的通用工具
 * <p>
 * 抽出来是为了避免「执行历史切分」与「按 token 硬截断」这两段逻辑在
 * LlmContextSummarizer 与 AbstractExecuteSupport 里各写一遍。
 *
 * @author xiaofuge bugstack.cn @小傅哥
 * 2026/09/14
 */
public final class ContextBudgetSupport {

    /**
     * 执行历史的分段标记，与 Step2 / Step3 写入的记录标题保持一致
     */
    public static final String HISTORY_SEGMENT_DELIMITER = "=== 第";

    private ContextBudgetSupport() {
    }

    /**
     * 按 "=== 第 N 步" 切分执行历史，保留分隔符本身
     *
     * @param text 执行历史全文
     * @return 分段列表；文本为空时返回空列表
     */
    public static List<String> splitHistory(String text) {
        List<String> segments = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return segments;
        }
        int from = 0;
        int idx;
        while ((idx = text.indexOf(HISTORY_SEGMENT_DELIMITER, from + 1)) > 0) {
            segments.add(text.substring(from, idx));
            from = idx;
        }
        segments.add(text.substring(from));
        return segments;
    }

    /**
     * 按 token 上限硬截断。
     * <p>
     * 用二分逼近而非逐字符估算，把 estimate 调用次数从 O(n) 降到 O(log n)。
     *
     * @param counter   估算器
     * @param text      原文
     * @param maxTokens 上限
     * @return 截断后的文本；未超限时原样返回
     */
    public static String hardTruncate(ITokenCounter counter, String text, int maxTokens) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        if (counter.estimate(text) <= maxTokens) {
            return text;
        }
        int lo = 0;
        int hi = text.length();
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (counter.estimate(text.substring(0, mid)) <= maxTokens) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        return text.substring(0, Math.max(1, lo)) + "\n...[已截断]";
    }

    /**
     * 规则截断：每段只保留前 N 个字符，再按 token 上限硬截断。
     * <p>
     * 用于 LLM 摘要不可用（超时 / 失败 / 未配置模型）时的降级，保证功能不中断。
     *
     * @param counter            估算器
     * @param previousSummary    上一轮摘要，可为 null
     * @param content            需要压缩的内容
     * @param charsPerSegment    每段保留字符数
     * @param maxSummaryTokens   摘要 token 上限
     * @return 降级后的摘要文本
     */
    public static String ruleTruncate(ITokenCounter counter, String previousSummary, String content,
                                      int charsPerSegment, int maxSummaryTokens) {
        StringBuilder sb = new StringBuilder();
        if (previousSummary != null && !previousSummary.isBlank()) {
            sb.append(previousSummary.trim()).append("\n");
        }

        // 优先按执行历史分段，其次按空行分段
        List<String> segments = splitHistory(content);
        if (segments.size() <= 1) {
            segments = splitByBlankLine(content);
        }

        int keep = Math.max(64, charsPerSegment);
        for (String segment : segments) {
            if (segment == null || segment.isBlank()) {
                continue;
            }
            String trimmed = segment.trim();
            sb.append(trimmed.length() > keep ? trimmed.substring(0, keep) + "..." : trimmed).append("\n");
        }

        return hardTruncate(counter, sb.toString().trim(), maxSummaryTokens);
    }

    private static List<String> splitByBlankLine(String text) {
        List<String> segments = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return segments;
        }
        for (String part : text.split("\\n\\s*\\n")) {
            if (!part.isBlank()) {
                segments.add(part);
            }
        }
        return segments;
    }

}
