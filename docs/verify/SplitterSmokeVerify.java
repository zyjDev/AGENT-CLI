import cn.bugstack.ai.config.AiAgentConfig;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;

import java.util.List;

/**
 * 打包冒烟探针 —— 用 <b>fat jar 的真实 classpath</b> 运行，验证交付物里的切分器实现。
 *
 * <p>与 {@code docs/rag-eval} 下的专项探针（18 项断言）不同，本探针只做「冒烟」：
 * 确认打包产物里的实现能被真实 Bean 工厂方法造出来，且基本行为正确。
 *
 * <p>验证四件事：
 * <ul>
 *   <li>[1] {@code AiAgentConfig#tokenTextSplitter()} 这个真实 Bean 工厂方法返回的是新实现</li>
 *   <li>[2] AsciiDoc 表格原子化：表格不被切断（每块内 {@code |====} 计数必须为偶数）</li>
 *   <li>[3] 相邻块之间存在重叠</li>
 *   <li>[4] 切分后实义字符零丢失</li>
 * </ul>
 *
 * <p>运行方式见 {@code docs/verify/README.md}「打包冒烟」一节。
 */
public class SplitterSmokeVerify {

    private static int pass = 0;
    private static int fail = 0;

    public static void main(String[] args) {
        System.out.println("=== 打包冒烟探针（运行于 fat jar classpath）===");
        System.out.println("JVM: " + System.getProperty("java.version"));

        // ---------- [1] Bean 工厂方法 ----------
        AiAgentConfig config = new AiAgentConfig();
        TokenTextSplitter splitter = config.tokenTextSplitter();
        String actualType = splitter.getClass().getName();

        System.out.println("\n[1] AiAgentConfig#tokenTextSplitter() 实际返回类型:");
        System.out.println("    " + actualType);
        check("Bean 是新实现 OverlapTokenTextSplitter",
                "cn.bugstack.ai.domain.agent.service.rag.splitter.OverlapTokenTextSplitter".equals(actualType));
        check("Bean 是 TokenTextSplitter 的子类（注入点无需改动）",
                splitter instanceof TokenTextSplitter);

        // ---------- [2] 表格原子化 ----------
        // 注意：表格内部刻意夹了空行 —— 这正是曾导致「整表散架」的那个缺陷场景
        String tableDoc = "Spring AI 支持多种聊天模型。\n"
                + "\n"
                + "|====\n"
                + "| 模型 | 说明\n"
                + "| openai | OpenAI 聊天模型\n"
                + "\n"
                + "| deepseek | DeepSeek 聊天模型\n"
                + "| zhipuai | 智谱聊天模型\n"
                + "|====\n"
                + "\n"
                + "以上是当前支持的模型列表。\n";

        List<Document> chunks = splitter.apply(List.of(new Document(tableDoc)));
        System.out.println("\n[2] 含 AsciiDoc 表格（内部夹空行）的文档 -> " + chunks.size() + " 块");
        int brokenTables = 0;
        for (int i = 0; i < chunks.size(); i++) {
            String t = chunks.get(i).getText();
            int markers = countOccurrences(t, "|====");
            System.out.println("    块" + (i + 1) + ": |==== 标记数=" + markers
                    + ", 字符数=" + t.length());
            if (markers % 2 != 0) {
                brokenTables++;
                System.out.println("        ^^^ 奇数标记 = 表格被切断！");
            }
        }
        check("表格未被切断（每块 |==== 计数为偶数）", brokenTables == 0);

        // ---------- [3] 相邻块重叠 ----------
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 200; i++) {
            sb.append("这是第 ").append(i).append(" 句测试文本，用于验证相邻块之间的重叠语义。");
            if (i % 5 == 4) {
                sb.append("\n\n");
            } else {
                sb.append(" ");
            }
        }
        String longDoc = sb.toString();
        List<Document> bigChunks = splitter.apply(List.of(new Document(longDoc)));
        System.out.println("\n[3] 长文档（" + longDoc.length() + " 字符）-> " + bigChunks.size() + " 块");

        int overlapPairs = 0;
        for (int i = 1; i < bigChunks.size(); i++) {
            String prev = bigChunks.get(i - 1).getText();
            String cur = bigChunks.get(i).getText();
            String head = cur.substring(0, Math.min(20, cur.length()));
            if (prev.contains(head)) {
                overlapPairs++;
            }
        }
        System.out.println("    有重叠的相邻对: " + overlapPairs + " / " + (bigChunks.size() - 1));
        check("相邻块存在重叠", overlapPairs > 0);

        // ---------- [4] 零丢失 ----------
        // ⚠️ 有重叠时「各块顺序拼接」会含重复片段（块 A 的尾 = 块 B 的头），
        // 所以 joined 里**不会**出现原文作为连续子串 —— 用 contains 判必然假失败。
        // 正确判据：原文实义串是「各块顺序拼接」的子序列。
        String joined = String.join("", bigChunks.stream().map(Document::getText).toList());
        String normalizedSrc = longDoc.replaceAll("\\s+", "");
        String normalizedOut = joined.replaceAll("\\s+", "");
        int cursor = 0;
        for (int i = 0; i < normalizedOut.length() && cursor < normalizedSrc.length(); i++) {
            if (normalizedOut.charAt(i) == normalizedSrc.charAt(cursor)) {
                cursor++;
            }
        }
        System.out.println("    原文实义字符 " + normalizedSrc.length()
                + " 个，按子序列匹配到 " + cursor + " 个");
        check("切分后实义字符零丢失（原文为各块拼接的子序列）", cursor == normalizedSrc.length());

        System.out.println("\n=== PASS=" + pass + " FAIL=" + fail + " ===");
        if (fail > 0) {
            System.exit(1);
        }
    }

    private static int countOccurrences(String s, String sub) {
        int c = 0;
        int i = 0;
        while ((i = s.indexOf(sub, i)) >= 0) {
            c++;
            i += sub.length();
        }
        return c;
    }

    private static void check(String name, boolean ok) {
        System.out.println((ok ? "  [PASS] " : "  [FAIL] ") + name);
        if (ok) {
            pass++;
        } else {
            fail++;
        }
    }
}
