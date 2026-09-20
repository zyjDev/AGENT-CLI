import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.EncodingType;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 用**真实 tokenizer**（cl100k_base，与 TokenTextSplitter 同一套编码）统计
 * 「最终送进回答模型的检索片段」到底有多少 token。
 *
 * 为什么需要它：之前的报告用「1.5 字符/token」粗估，那是中英混排的保守口径；
 * 本语料是英文 Spring AI 文档，实际约 4 字符/token → 粗估把 token 数放大了 2 倍多。
 * 组间**比例**不受影响（token 与字符近似线性），但绝对值会误导决策。
 *
 * 用法：
 *   java @docs/rag-eval/_local/args/java-token-args.txt --run <eval-xxx.json> --runs docs/rag-eval/_local/runs --ks 1,2,3,4
 */
public class TokenCount {

    private static final ObjectMapper JSON = new ObjectMapper();

    public static void main(String[] args) throws Exception {
        Map<String, String> opt = parseArgs(args);
        String run = require(opt, "run");
        String runsDir = opt.getOrDefault("runs", "docs/rag-eval/_local/runs");
        List<Integer> ks = new ArrayList<>();
        for (String s : opt.getOrDefault("ks", "1,2,3,4").split(",")) {
            ks.add(Integer.parseInt(s.trim()));
        }

        // 1) 载入 chunk 快照：chunkId -> 正文
        Map<String, String> snap = new LinkedHashMap<>();
        try (var stream = Files.list(Paths.get(runsDir))) {
            for (Path p : stream.filter(x -> x.getFileName().toString().startsWith("chunks-")).toList()) {
                for (String line : Files.readAllLines(p, StandardCharsets.UTF_8)) {
                    line = line.trim();
                    if (line.isEmpty()) continue;
                    Map<String, Object> d = JSON.readValue(line, new TypeReference<>() {});
                    snap.put(String.valueOf(d.get("chunkId")), String.valueOf(d.get("content")));
                }
            }
        }

        // 2) 载入评测明细
        Map<String, Object> report = JSON.readValue(
                Files.readString(Paths.get(runsDir, run), StandardCharsets.UTF_8), new TypeReference<>() {});
        List<Map<String, Object>> records = castList(report.get("records"));

        EncodingRegistry registry = Encodings.newDefaultEncodingRegistry();
        Encoding enc = registry.getEncoding(EncodingType.CL100K_BASE);

        Map<String, Object> cfg = castMap(report.get("config"));
        System.out.printf("== TokenCount（cl100k_base 真实 tokenizer）==%n");
        System.out.printf("run      : %s%n", run);
        System.out.printf("配置     : topK=%s recallK=%s rerank=%s docChars=%s%n",
                cfg.get("topK"), cfg.get("recallK"), cfg.get("rerank"), cfg.get("rerankDocChars"));
        System.out.println();
        System.out.printf("%3s %10s %10s %10s %12s%n", "k", "平均字符", "平均token", "字符/token", "条数");
        System.out.println("-".repeat(50));
        for (int k : ks) {
            long chars = 0, tokens = 0;
            int n = 0;
            for (Map<String, Object> r : records) {
                if (!Boolean.TRUE.equals(r.get("retrievalScored"))) continue;
                List<String> ids = orderOf(r);
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < Math.min(k, ids.size()); i++) {
                    String text = snap.get(ids.get(i));
                    if (text == null) continue;
                    if (sb.length() > 0) sb.append("\n\n");
                    sb.append(text);
                }
                if (sb.length() == 0) continue;
                chars += sb.length();
                tokens += enc.countTokens(sb.toString());
                n++;
            }
            if (n == 0) continue;
            double avgChars = (double) chars / n;
            double avgTok = (double) tokens / n;
            System.out.printf("%3d %10.0f %10.0f %10.2f %12d%n", k, avgChars, avgTok, avgChars / avgTok, n);
        }

        // 3) 精排侧：重建 buildPrompt() 的候选段，统计「送精排模型的输入 token」
        System.out.println();
        System.out.println("-- 精排侧输入（重建 LlmDocumentPostProcessor.buildPrompt 的候选段）--");
        System.out.printf("%12s %12s %12s %12s%n", "docChars", "平均字符", "平均token", "字符/token");
        System.out.println("-".repeat(50));
        for (String dc : opt.getOrDefault("docChars", "1200,3600").split(",")) {
            int docChars = Integer.parseInt(dc.trim());
            long chars = 0, tokens = 0;
            int n = 0;
            for (Map<String, Object> r : records) {
                if (!Boolean.TRUE.equals(r.get("retrievalScored"))) continue;
                if (!"llm".equalsIgnoreCase(String.valueOf(cfg.get("rerank")))) continue;
                String question = String.valueOf(r.get("query"));
                List<String> pool = strList(r.get("poolIds"));
                if (pool.isEmpty()) continue;
                StringBuilder sb = new StringBuilder();
                sb.append("You are a retrieval reranker. For each candidate passage, score how useful it is ");
                sb.append("for answering the question.\n");
                sb.append("Score 0-10 (10 = the passage alone is enough to answer). ");
                sb.append("Judge only whether the needed information is present; ignore length and writing style.\n");
                sb.append("Output JSON only. No explanation, no markdown code fence.\n\n");
                sb.append("Question: ").append(question).append("\n\nCandidates:\n");
                for (int i = 0; i < pool.size(); i++) {
                    String text = snap.get(pool.get(i));
                    if (text == null) continue;
                    sb.append('[').append(i + 1).append("] ").append(truncate(text, docChars)).append('\n');
                }
                sb.append("\nOutput format: {\"scores\":[{\"index\":1,\"score\":9},{\"index\":2,\"score\":3}]}");
                chars += sb.length();
                tokens += enc.countTokens(sb.toString());
                n++;
            }
            if (n == 0) continue;
            double avgChars = (double) chars / n;
            double avgTok = (double) tokens / n;
            System.out.printf("%12d %12.0f %12.0f %12.2f%n", docChars, avgChars, avgTok, avgChars / avgTok);
        }
    }

    /** 与生产 LlmDocumentPostProcessor#truncate 完全一致：压连续空白 → 取前 docChars 字符 → 加省略号。 */
    private static String truncate(String text, int docChars) {
        if (text == null) return "";
        String flat = text.replaceAll("\\s+", " ").trim();
        return flat.length() <= docChars ? flat : flat.substring(0, docChars) + "…";
    }

    /** 与 analyze-by-k.py 的 order_of 保持一致：精排后完整序 → finalIds → poolIds。 */
    private static List<String> orderOf(Map<String, Object> r) {
        if (Boolean.TRUE.equals(r.get("rerankApplied"))) {
            List<String> ids = strList(r.get("rerankedIds"));
            if (!ids.isEmpty()) return ids;
            return strList(r.get("finalIds"));
        }
        return strList(r.get("poolIds"));
    }

    @SuppressWarnings("unchecked")
    private static List<String> strList(Object o) {
        List<String> out = new ArrayList<>();
        if (o instanceof List<?> l) {
            for (Object x : l) out.add(String.valueOf(x));
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> castList(Object o) {
        return o instanceof List<?> l ? (List<Map<String, Object>>) l : List.of();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object o) {
        return o instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < args.length; i++) {
            if (!args[i].startsWith("--")) continue;
            String key = args[i].substring(2);
            String val = (i + 1 < args.length && !args[i + 1].startsWith("--")) ? args[++i] : "true";
            m.put(key, val);
        }
        return m;
    }

    private static String require(Map<String, String> m, String k) {
        String v = m.get(k);
        if (v == null || v.isBlank()) throw new IllegalArgumentException("缺少 --" + k);
        return v;
    }
}
