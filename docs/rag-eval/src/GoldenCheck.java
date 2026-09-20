import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * golden set 静态校验（离线，不连库、不调模型）。
 *
 * 为什么必须有这一步：标注集是整条评测链路的「基准尺」。尺子本身错了，
 * 后面跑出来的所有数字都是错的，而且错得看不出来（比如指纹写错一个字，
 * 命中率会莫名其妙地低，很容易被误读成「检索效果差」）。
 *
 * 校验项：
 *   1. id 唯一；bucket 合法；必须字段齐全
 *   2. mustHit 里的每个指纹，都能在对应 knowledge 的 chunk 快照里找到
 *      （同时校验 source 是否对得上 —— 指纹写对但 source 写错，同样会污染结论）
 *   3. 桶约束：no_answer 必须无 mustHit；其余桶必须有 mustHit
 *   4. 3-gram 重叠率：query 与「命中该指纹的 chunk」的词面重叠过高 → 词汇泄漏警告
 *      （题从原文反推、复用原文用词，会让目标 chunk 稳排第一，精排收益被抹平）
 *   5. mustNotAppear：这些串**不允许**出现在该 knowledge 的任何 chunk 里
 *      （no_answer 题要证明「语料里真的没有」；false_premise 题要证明「错误值不在语料里」）
 *   6. 桶分布是否符合 5:3:2
 *
 * 用法：
 *   java GoldenCheck --golden docs/rag-eval/golden/golden-set-v1.jsonl \
 *        --snapshot eval-fictional-v1=docs/rag-eval/_local/runs/chunks-fictional-v1.jsonl \
 *        --snapshot eval-spring-docs-v1=docs/rag-eval/_local/runs/chunks-spring-docs-v1.jsonl \
 *        [--overlap 0.5] [--report docs/rag-eval/_local/runs/golden-check.md]
 */
public class GoldenCheck {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Map<String, Integer> EXPECTED_DISTRIBUTION = Map.of(
            "easy", 50, "multi_hop", 30, "no_answer", 10, "false_premise", 10);
    private static final Set<String> VALID_BUCKETS = EXPECTED_DISTRIBUTION.keySet();
    private static final Set<String> RETRIEVAL_BUCKETS = Set.of("easy", "multi_hop", "false_premise");

    private static final List<String> errors = new ArrayList<>();
    private static final List<String> warnings = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        Map<String, String> opt = parseArgs(args);
        Path goldenPath = Paths.get(require(opt, "golden"));
        double overlapLimit = Double.parseDouble(opt.getOrDefault("overlap", "0.5"));

        Map<String, List<Chunk>> corpora = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : opt.entrySet()) {
            if (e.getKey().startsWith("snapshot:")) {
                String knowledge = e.getKey().substring("snapshot:".length());
                corpora.put(knowledge, loadSnapshot(Paths.get(e.getValue())));
            }
        }
        if (corpora.isEmpty()) {
            throw new IllegalArgumentException("至少要给一个 --snapshot <knowledge>=<file>");
        }

        List<Map<String, Object>> golden = loadGolden(goldenPath);

        System.out.println("== GoldenCheck ==");
        System.out.println("golden   : " + goldenPath);
        System.out.println("标注条数 : " + golden.size());
        System.out.println("语料快照 :");
        corpora.forEach((k, v) -> System.out.printf("  %-22s %4d chunk  %d 文档%n", k, v.size(),
                v.stream().map(c -> c.source).distinct().count()));
        System.out.println();

        Set<String> ids = new HashSet<>();
        Map<String, Integer> dist = new TreeMap<>();
        for (int i = 0; i < golden.size(); i++) {
            checkOne(golden.get(i), i + 1, corpora, ids, dist, overlapLimit);
        }
        checkDistribution(golden, dist);

        System.out.println("== 结果 ==");
        System.out.println("桶分布   : " + dist);
        if (!warnings.isEmpty()) {
            System.out.println();
            System.out.println("-- 警告 " + warnings.size() + " 条 --");
            warnings.forEach(w -> System.out.println("  [WARN] " + w));
        }
        System.out.println();
        if (errors.isEmpty()) {
            System.out.println("通过：" + golden.size() + " 条标注全部校验通过，0 条错误。");
        } else {
            System.out.println("-- 错误 " + errors.size() + " 条 --");
            errors.forEach(e -> System.out.println("  [FAIL] " + e));
            System.out.println();
            System.out.println("校验未通过：" + errors.size() + " 条错误。");
        }

        String report = opt.get("report");
        if (report != null) {
            Path out = Paths.get(report);
            Files.createDirectories(out.toAbsolutePath().getParent());
            Files.writeString(out, buildReport(golden, corpora, dist, overlapLimit), StandardCharsets.UTF_8);
            System.out.println("报告已写出：" + out.toAbsolutePath());
        }

        if (!errors.isEmpty()) {
            System.exit(1);
        }
    }

    // ------------------------------------------------------------------ 单条

    private static void checkOne(Map<String, Object> g, int line, Map<String, List<Chunk>> corpora,
                                 Set<String> ids, Map<String, Integer> dist, double overlapLimit) {
        String id = str(g.get("id"));
        String where = "第 " + line + " 行" + (id.isEmpty() ? "" : "（" + id + "）");

        if (id.isEmpty()) {
            errors.add(where + "：缺少 id");
        } else if (!ids.add(id)) {
            errors.add(where + "：id 重复");
        }
        String bucket = str(g.get("bucket"));
        if (!VALID_BUCKETS.contains(bucket)) {
            errors.add(where + "：bucket 非法 -> '" + bucket + "'");
            return;
        }
        dist.merge(bucket, 1, Integer::sum);

        String knowledge = str(g.get("knowledge"));
        List<Chunk> corpus = corpora.get(knowledge);
        if (corpus == null) {
            errors.add(where + "：knowledge '" + knowledge + "' 没有对应的 --snapshot");
            return;
        }
        String query = str(g.get("query"));
        if (query.isBlank()) {
            errors.add(where + "：query 为空");
        }
        if (str(g.get("goldenAnswer")).isBlank()) {
            errors.add(where + "：缺少 goldenAnswer");
        }
        if (str(g.get("expect")).isBlank()) {
            errors.add(where + "：缺少 expect");
        }

        List<Map<String, Object>> mustHit = mustHit(g);
        if ("no_answer".equals(bucket)) {
            if (!mustHit.isEmpty()) {
                errors.add(where + "：no_answer 桶不应有 mustHit（该桶不参与检索层指标）");
            }
        } else if (mustHit.isEmpty()) {
            errors.add(where + "：" + bucket + " 桶必须有 mustHit");
        }

        // 指纹必须在快照里真实存在
        for (Map<String, Object> mh : mustHit) {
            String fp = str(mh.get("fingerprint"));
            String src = str(mh.get("source"));
            if (fp.isBlank()) {
                errors.add(where + "：mustHit 缺少 fingerprint");
                continue;
            }
            List<Chunk> matched = corpus.stream().filter(c -> c.content.contains(fp)).collect(Collectors.toList());
            if (matched.isEmpty()) {
                errors.add(where + "：指纹在 " + knowledge + " 的语料里找不到 -> 「" + brief(fp) + "」");
                continue;
            }
            if (!src.isBlank() && matched.stream().noneMatch(c -> c.source.equals(src))) {
                errors.add(where + "：指纹找到了，但 source 对不上（标注 '" + src + "'，实际 "
                        + matched.stream().map(c -> c.source).distinct().collect(Collectors.joining("/")) + "）");
            }
            // 3-gram 重叠率（词汇泄漏）
            for (Chunk c : matched) {
                double ov = overlap(query, c.content);
                if (ov > overlapLimit) {
                    warnings.add(where + "：query 与目标 chunk（" + c.source + "）的 3-gram 重叠率 "
                            + String.format("%.2f", ov) + " > " + overlapLimit + "，存在词汇泄漏风险 -> 「" + brief(query) + "」");
                }
            }
        }

        // mustNotAppear：证明「语料里真的没有」
        Object mna = g.get("mustNotAppear");
        if (mna instanceof List<?> l) {
            for (Object o : l) {
                String probe = String.valueOf(o);
                List<Chunk> hit = corpus.stream().filter(c -> c.content.contains(probe)).collect(Collectors.toList());
                if (!hit.isEmpty()) {
                    errors.add(where + "：mustNotAppear 里的「" + probe + "」实际出现在语料中（"
                            + hit.stream().map(c -> c.source).distinct().collect(Collectors.joining("/")) + "）");
                }
            }
        }
        if ("no_answer".equals(bucket) && !(mna instanceof List<?> l && !l.isEmpty())) {
            warnings.add(where + "：no_answer 桶建议补 mustNotAppear，用来证明语料里确实没有该信息");
        }
    }

    private static void checkDistribution(List<Map<String, Object>> golden, Map<String, Integer> dist) {
        if (golden.size() != 100) {
            warnings.add("标注条数是 " + golden.size() + "，目标 100 条");
        }
        for (Map.Entry<String, Integer> e : EXPECTED_DISTRIBUTION.entrySet()) {
            int actual = dist.getOrDefault(e.getKey(), 0);
            if (actual != e.getValue()) {
                warnings.add("桶 " + e.getKey() + " 数量 " + actual + "，目标 " + e.getValue());
            }
        }
    }

    // ------------------------------------------------------------------ 3-gram

    /** 归一化：小写 + 只留字母数字汉字。中英混排下按字符切 3-gram，是词汇泄漏的保守代理指标。 */
    private static Set<String> trigrams(String s) {
        String norm = s.toLowerCase(Locale.ROOT).replaceAll("[^\\p{IsHan}a-z0-9]", "");
        Set<String> out = new LinkedHashSet<>();
        for (int i = 0; i + 3 <= norm.length(); i++) {
            out.add(norm.substring(i, i + 3));
        }
        return out;
    }

    private static double overlap(String query, String chunk) {
        Set<String> q = trigrams(query);
        if (q.isEmpty()) {
            return 0;
        }
        Set<String> c = trigrams(chunk);
        long inter = q.stream().filter(c::contains).count();
        return (double) inter / q.size();
    }

    // ------------------------------------------------------------------ IO

    private static List<Chunk> loadSnapshot(Path p) throws Exception {
        List<Chunk> out = new ArrayList<>();
        for (String line : Files.readAllLines(p, StandardCharsets.UTF_8)) {
            String s = line.trim();
            if (s.isEmpty()) {
                continue;
            }
            Map<String, Object> rec = JSON.readValue(s, new TypeReference<Map<String, Object>>() {
            });
            out.add(new Chunk(str(rec.get("source")), str(rec.get("content"))));
        }
        return out;
    }

    private static List<Map<String, Object>> loadGolden(Path p) throws Exception {
        List<Map<String, Object>> out = new ArrayList<>();
        int n = 0;
        for (String line : Files.readAllLines(p, StandardCharsets.UTF_8)) {
            n++;
            String s = line.trim();
            if (s.isEmpty() || s.startsWith("//")) {
                continue;
            }
            try {
                out.add(JSON.readValue(s, new TypeReference<Map<String, Object>>() {
                }));
            } catch (Exception e) {
                errors.add("第 " + n + " 行不是合法 JSON：" + e.getMessage());
            }
        }
        return out;
    }

    private static String buildReport(List<Map<String, Object>> golden, Map<String, List<Chunk>> corpora,
                                      Map<String, Integer> dist, double overlapLimit) {
        StringBuilder sb = new StringBuilder();
        sb.append("# golden set 校验报告\n\n");
        sb.append("- 标注条数：").append(golden.size()).append("\n");
        sb.append("- 桶分布：").append(dist).append("（目标 50/30/10/10）\n");
        sb.append("- 3-gram 重叠率阈值：").append(overlapLimit).append("\n\n");
        sb.append("## 语料\n\n| knowledge | chunk 数 | 文档数 |\n| --- | --- | --- |\n");
        corpora.forEach((k, v) -> sb.append("| ").append(k).append(" | ").append(v.size()).append(" | ")
                .append(v.stream().map(c -> c.source).distinct().count()).append(" |\n"));
        sb.append("\n## 错误（").append(errors.size()).append("）\n\n");
        errors.forEach(e -> sb.append("- ").append(e).append("\n"));
        sb.append("\n## 警告（").append(warnings.size()).append("）\n\n");
        warnings.forEach(w -> sb.append("- ").append(w).append("\n"));
        return sb.toString();
    }

    // ------------------------------------------------------------------ 工具

    private static List<Map<String, Object>> mustHit(Map<String, Object> g) {
        Object mh = g.get("mustHit");
        List<Map<String, Object>> out = new ArrayList<>();
        if (mh instanceof List<?> l) {
            for (Object o : l) {
                if (o instanceof Map<?, ?> m) {
                    Map<String, Object> mm = new HashMap<>();
                    m.forEach((k, v) -> mm.put(String.valueOf(k), v));
                    out.add(mm);
                }
            }
        }
        return out;
    }

    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if (!a.startsWith("--")) {
                continue;
            }
            String key = a.substring(2);
            String val = (i + 1 < args.length && !args[i + 1].startsWith("--")) ? args[++i] : "true";
            if ("snapshot".equals(key)) {
                // --snapshot 允许重复出现，因此直接展开成 snapshot:<knowledge> -> path
                String[] kv = val.split("=", 2);
                if (kv.length != 2) {
                    throw new IllegalArgumentException("--snapshot 需要 <knowledge>=<file> 形式，收到：" + val);
                }
                m.put("snapshot:" + kv[0], kv[1]);
            } else {
                m.put(key, val);
            }
        }
        return m;
    }

    private static String require(Map<String, String> opt, String key) {
        String v = opt.get(key);
        if (v == null) {
            throw new IllegalArgumentException("缺少参数 --" + key);
        }
        return v;
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static String brief(String s) {
        String flat = s.replaceAll("\\s+", " ").trim();
        return flat.length() <= 60 ? flat : flat.substring(0, 60) + "…";
    }

    private record Chunk(String source, String content) {
    }
}
