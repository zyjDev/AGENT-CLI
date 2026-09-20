import cn.bugstack.ai.domain.agent.service.rag.rerank.LlmDocumentPostProcessor;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.rag.Query;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * RAG 检索效果评测 harness（独立 main，不启动 Spring 容器）。
 *
 * 设计要点
 *  1. **检索层指标全部用确定性字符串匹配**：命中判据是「检索回来的 chunk 文本里包含
 *     标注时写下的指纹」，不经过任何模型 → 零 LLM 成本、可高频重跑、结论可复现。
 *  2. **召回池（recallK）与最终条数（topK）分离**：精排只改顺序不改候选集合，
 *     所以必须先把池子放大到 recallK，再重排截断到 topK，才能测出「重排」的贡献。
 *  3. **精排直接调用生产类**（{@code LlmDocumentPostProcessor}），不在 harness 里另写打分器，
 *     否则 C 组测的不是生产代码、before/after 数字不可信。
 *
 * 实验分组（唯一变量是 recallK 与 rerank，标注集与语料完全相同）：
 *   A  组 = recallK=4,  topK=4, rerank=none  —— 现状基线
 *   C0 组 = recallK=20, topK=4, rerank=none  —— 对照：只扩池不精排（证明「扩池不改前 4 名」）
 *   C  组 = recallK=20, topK=4, rerank=llm   —— 精排；A→C 的差值才是精排的贡献
 *   B  组 = recallK=8/20, topK=8/20, none    —— topK 消融
 *
 * 用法：
 *   eval   --golden <file.jsonl> [--topK 4] [--recallK 4] [--rerank none|llm]
 *          [--variants] [--limit N] [--only id1,id2] [--tag name] [--out path]
 *   probe  --golden <file.jsonl> [--topK 4]   # 只打印明细，调试标注用
 *
 * 环境变量：PG_* / EMBEDDING_*（同 RagCorpusIngest）；RERANK_*（见 RerankConfig）
 */
public class EvalHarness {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String VECTOR_TABLE = "vector_store_openai";
    private static final String DEFAULT_PG_URL = "jdbc:postgresql://127.0.0.1:15432/ai-rag-knowledge";
    private static final String DEFAULT_PG_USER = "postgres";
    private static final String DEFAULT_PG_PASSWORD = "postgres";
    private static final String DEFAULT_EMB_BASE_URL = "https://dashscope.aliyuncs.com/compatible-mode";
    private static final String DEFAULT_EMB_MODEL = "text-embedding-v3";
    private static final String YML_PATH = "ai-agent-station-study-app/src/main/resources/application-dev.yml";

    /** 检索层指标只统计这些桶：no_answer 没有 golden context，分母无定义，必须剔除。 */
    private static final List<String> RETRIEVAL_BUCKETS = List.of("easy", "multi_hop", "false_premise");
    private static final List<String> ALL_BUCKETS = List.of("easy", "multi_hop", "no_answer", "false_premise");

    public static void main(String[] args) {
        try {
            run(args);
        } catch (Throwable t) {
            say("%n!! 未捕获异常：%s", t);
            t.printStackTrace(System.err);
            System.out.flush();
            System.err.flush();
            System.exit(1);
        }
    }

    private static void run(String[] args) throws Exception {
        Map<String, String> opt = parseArgs(args);
        String mode = opt.getOrDefault("_mode", "eval");

        Path goldenPath = Paths.get(require(opt, "golden"));
        int topK = Integer.parseInt(opt.getOrDefault("topK", "4"));
        int recallK = Integer.parseInt(opt.getOrDefault("recallK", String.valueOf(topK)));
        String rerank = opt.getOrDefault("rerank", "none");
        boolean variants = opt.containsKey("variants");
        int limit = Integer.parseInt(opt.getOrDefault("limit", "0"));
        String tag = opt.getOrDefault("tag", "run");

        List<Map<String, Object>> golden = loadGolden(goldenPath);
        if (limit > 0 && limit < golden.size()) {
            golden = golden.subList(0, limit);
        }
        // --only id1,id2  只跑指定题：用于「定向复现某几条失败题」，避免为了验证一个假设重跑 100 条
        String only = opt.get("only");
        if (only != null && !only.isBlank()) {
            Set<String> keep = new HashSet<>(Arrays.asList(only.split(",")));
            golden = golden.stream()
                    .filter(g -> keep.contains(str(g.get("id"))))
                    .collect(Collectors.toList());
        }
        say("== EvalHarness ==");
        say("golden   : %s（%d 条）", goldenPath, golden.size());
        say("配置     : topK=%d recallK=%d rerank=%s variants=%s", topK, recallK, rerank, variants);

        JdbcTemplate jdbc = new JdbcTemplate(driverManager());
        PgVectorStore store = buildVectorStore(jdbc);

        ChatModel rerankModel = null;
        String rerankModelName = null;
        int rerankTimeoutMs = Integer.parseInt(env("RERANK_TIMEOUT_MS", "25000"));
        int rerankDocChars = Integer.parseInt(env("RERANK_DOC_CHARS", "1200"));
        if ("llm".equalsIgnoreCase(rerank)) {
            // 刻意直接 new 生产类（cn.bugstack.ai.domain.agent.service.rag.rerank.LlmDocumentPostProcessor），
            // 而不是在 harness 里另写一个打分器 —— 否则 C 组测的就不是生产代码，数据不可信。
            RerankConfig rc = RerankConfig.resolve();
            rerankModelName = rc.modelName;
            rerankModel = buildChatModel(rc);
            say("精排模型 : %s @ %s", rc.modelName, rc.baseUrl);
            say("精排实现 : %s", LlmDocumentPostProcessor.class.getName());
            say("精排参数 : docChars=%d timeoutMs=%d", rerankDocChars, rerankTimeoutMs);
        }
        say("");

        if ("probe".equalsIgnoreCase(mode)) {
            probe(store, golden, recallK, topK);
            return;
        }

        List<Map<String, Object>> records = new ArrayList<>();
        for (int i = 0; i < golden.size(); i++) {
            Map<String, Object> g = golden.get(i);
            String query = variants ? firstVariant(g) : str(g.get("query"));
            Map<String, Object> rec = evalOne(store, rerankModel, rerankTimeoutMs, rerankDocChars,
                    g, query, recallK, topK);
            records.add(rec);
            if ((i + 1) % 10 == 0 || i + 1 == golden.size()) {
                say("  ... %d/%d", i + 1, golden.size());
            }
        }

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("tag", tag);
        report.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        report.put("goldenFile", goldenPath.toString());
        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("topK", topK);
        cfg.put("recallK", recallK);
        cfg.put("rerank", rerank);
        cfg.put("rerankModel", rerankModelName);
        cfg.put("rerankImpl", rerankModel == null ? null : LlmDocumentPostProcessor.class.getName());
        cfg.put("rerankDocChars", rerankModel == null ? null : rerankDocChars);
        cfg.put("variants", variants);
        cfg.put("embeddingModel", env("EMBEDDING_MODEL", DEFAULT_EMB_MODEL));
        report.put("config", cfg);
        report.put("summary", summarize(records));
        report.put("records", records);

        Path out = Paths.get(opt.getOrDefault("out",
                "docs/rag-eval/_local/runs/eval-" + tag + "-topK" + topK + "-recall" + recallK + "-" + rerank + ".json"));
        Files.createDirectories(out.toAbsolutePath().getParent());
        Files.writeString(out, JSON.writerWithDefaultPrettyPrinter().writeValueAsString(report), StandardCharsets.UTF_8);
        printSummary(report, topK, recallK, rerank);
        say("");
        say("明细已写出：%s", out.toAbsolutePath());
    }

    // ------------------------------------------------------------------ 单条评测

    private static Map<String, Object> evalOne(PgVectorStore store, ChatModel rerankModel,
                                               int rerankTimeoutMs, int rerankDocChars,
                                               Map<String, Object> g, String query,
                                               int recallK, int topK) throws Exception {
        String id = str(g.get("id"));
        String bucket = str(g.get("bucket"));
        String knowledge = str(g.get("knowledge"));
        List<String> fps = fingerprints(g);
        boolean retrievalScored = RETRIEVAL_BUCKETS.contains(bucket) && !fps.isEmpty();

        SearchRequest req = SearchRequest.builder()
                .query(query)
                .topK(recallK)
                .filterExpression("knowledge == '" + knowledge + "'")
                .build();
        List<Document> pool = store.similaritySearch(req);
        if (pool == null) {
            pool = List.of();
        }

        Map<String, Object> rec = new LinkedHashMap<>();
        rec.put("id", id);
        rec.put("bucket", bucket);
        rec.put("knowledge", knowledge);
        rec.put("query", query);
        rec.put("expectedFingerprints", fps);
        rec.put("poolSize", pool.size());
        rec.put("retrievalScored", retrievalScored);

        // 池内诊断：目标片段在「未经精排」的召回序里排第几
        int goldRank = firstHitRank(pool, fps);
        rec.put("goldRankInPool", goldRank);
        rec.put("poolRecall", fps.isEmpty() ? null : (double) hitCount(pool, fps, pool.size()) / fps.size());

        // 精排（直接调用生产类；它内部自带降级，异常时返回原顺序前 finalTopK 条）
        //
        // ★ 关键设计：把精排器的 finalTopK 设成「池大小 - 1」，等于让它**只重排、不截断**
        //   （生产类只在 candidates.size() > finalTopK 时才精排）。真正的截断交给 harness 按 --topK 做。
        //   原因：精排打分与「最后保留几条」无关（同候选、同 prompt、temperature=0），
        //   所以一次昂贵的 LLM 调用就能离线评估任意最终条数 k，不必为每个 k 重跑一遍。
        //   完整排序记录在 rerankedIds / rerankedSources 里。
        List<Document> ranked = pool;
        boolean rerankApplied = false;
        boolean orderUnchanged = false;
        long rerankMs = 0;
        if (rerankModel != null && pool.size() > topK) {
            LlmDocumentPostProcessor postProcessor = new LlmDocumentPostProcessor(
                    rerankModel, Math.max(1, pool.size() - 1), rerankTimeoutMs, rerankDocChars);
            long t0 = System.currentTimeMillis();
            List<Document> reranked = postProcessor.process(new Query(query), pool);
            rerankMs = System.currentTimeMillis() - t0;
            if (reranked == null || reranked.isEmpty()) {
                reranked = pool.subList(0, Math.min(topK, pool.size()));
            }
            ranked = reranked;
            rerankApplied = true;
            // 生产类把降级藏在内部（这是对的：精排失败不该改变业务语义），外部只能观测到
            // 「排序是否与原始召回序完全一致」。它是「降级次数」的上界 —— 模型恰好保持原序也会被计入。
            orderUnchanged = sameOrder(ranked, pool);
        }
        List<Document> finalDocs = new ArrayList<>(ranked.subList(0, Math.min(topK, ranked.size())));
        rec.put("rerankApplied", rerankApplied);
        rec.put("orderUnchanged", orderUnchanged);
        rec.put("rerankMs", rerankMs);
        rec.put("finalSize", finalDocs.size());
        // 精排后的「完整排序」——留给离线分析：一次运行即可评估任意最终条数 k
        rec.put("rerankedIds", ranked.stream().map(Document::getId).collect(Collectors.toList()));
        rec.put("rerankedSources", ranked.stream()
                .map(d -> String.valueOf(d.getMetadata().get("source")))
                .collect(Collectors.toList()));

        if (retrievalScored) {
            int hits = hitCount(finalDocs, fps, finalDocs.size());
            // 注意：这里必须用「最终列表里的名次」判 0。
            // 池里有 gold 但 topK 截断后没有，是正常情况（goldRankInPool>0 而 finalGoldRank==0），
            // 若拿池内名次做守卫会走到 1.0/0 = Infinity（已踩过）。
            int finalRank = firstHitRank(finalDocs, fps);
            rec.put("hits", hits);
            rec.put("recall", (double) hits / fps.size());
            rec.put("precision", finalDocs.isEmpty() ? 0.0 : (double) hits / finalDocs.size());
            rec.put("mrr", finalRank == 0 ? 0.0 : 1.0 / finalRank);
            rec.put("contextPrecision", averagePrecision(finalDocs, fps, topK));
            rec.put("allHit", hits == fps.size());
            rec.put("finalGoldRank", finalRank);
        }

        rec.put("finalSources", finalDocs.stream()
                .map(d -> String.valueOf(d.getMetadata().get("source")))
                .collect(Collectors.toList()));
        rec.put("finalIds", finalDocs.stream().map(Document::getId).collect(Collectors.toList()));
        rec.put("poolIds", pool.stream().map(Document::getId).collect(Collectors.toList()));
        rec.put("goldenAnswer", g.get("goldenAnswer"));
        rec.put("expect", g.get("expect"));
        return rec;
    }

    /** 指纹命中数：一个 chunk 可能同时覆盖多个指纹（多跳题尤其常见）。 */
    private static int hitCount(List<Document> docs, List<String> fps, int limit) {
        List<String> remaining = new ArrayList<>(fps);
        for (int i = 0; i < Math.min(limit, docs.size()); i++) {
            String text = norm(docs.get(i).getText());
            remaining.removeIf(fp -> text.contains(norm(fp)));
        }
        return fps.size() - remaining.size();
    }

    /** 第一个命中任一指纹的位置（1-based）；未命中返回 0。 */
    private static int firstHitRank(List<Document> docs, List<String> fps) {
        if (fps.isEmpty()) {
            return 0;
        }
        for (int i = 0; i < docs.size(); i++) {
            String text = norm(docs.get(i).getText());
            for (String fp : fps) {
                if (text.contains(norm(fp))) {
                    return i + 1;
                }
            }
        }
        return 0;
    }

    /** 两个列表是否「顺序完全一致」（按 Document id 比）。 */
    private static boolean sameOrder(List<Document> a, List<Document> b) {
        if (a.size() > b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            if (!String.valueOf(a.get(i).getId()).equals(String.valueOf(b.get(i).getId()))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Context Precision@k（排名加权，即 Average Precision 口径）——
     * 不是「相关数 / k」。RAGAS 的 context_precision 也是这个定义：
     * 越早出现相关片段，分越高；分母用 min(相关总数, k) 截断。
     */
    private static double averagePrecision(List<Document> docs, List<String> fps, int k) {
        List<String> remaining = new ArrayList<>(fps);
        double sum = 0;
        int found = 0;
        int n = Math.min(k, docs.size());
        for (int i = 0; i < n; i++) {
            String text = norm(docs.get(i).getText());
            boolean rel = false;
            for (String fp : new ArrayList<>(remaining)) {
                if (text.contains(norm(fp))) {
                    remaining.remove(fp);
                    rel = true;
                }
            }
            if (rel) {
                found++;
                sum += (double) found / (i + 1);
            }
        }
        int denom = Math.min(fps.size(), k);
        return denom == 0 ? 0.0 : sum / denom;
    }

    // ------------------------------------------------------------------ 汇总

    private static Map<String, Object> summarize(List<Map<String, Object>> records) {
        Map<String, Object> out = new LinkedHashMap<>();
        Map<String, List<Map<String, Object>>> byBucket = records.stream()
                .collect(Collectors.groupingBy(r -> str(r.get("bucket")), LinkedHashMap::new, Collectors.toList()));
        for (String b : ALL_BUCKETS) {
            out.put(b, aggregate(byBucket.getOrDefault(b, List.of())));
        }
        out.put("retrievalOverall", aggregate(records.stream()
                .filter(r -> Boolean.TRUE.equals(r.get("retrievalScored")))
                .collect(Collectors.toList())));
        out.put("allBuckets", aggregate(records));
        out.put("rerankOrderUnchangedCount", records.stream()
                .filter(r -> Boolean.TRUE.equals(r.get("orderUnchanged"))).count());
        out.put("avgRerankMs", records.stream().mapToLong(r -> num(r.get("rerankMs"))).filter(v -> v > 0)
                .average().orElse(0.0));
        return out;
    }

    private static Map<String, Object> aggregate(List<Map<String, Object>> rs) {
        List<Map<String, Object>> scored = rs.stream()
                .filter(r -> Boolean.TRUE.equals(r.get("retrievalScored")))
                .collect(Collectors.toList());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("n", rs.size());
        m.put("nScored", scored.size());
        if (scored.isEmpty()) {
            return m;
        }
        m.put("recall", avg(scored, "recall"));
        m.put("precision", avg(scored, "precision"));
        m.put("mrr", avg(scored, "mrr"));
        m.put("contextPrecision", avg(scored, "contextPrecision"));
        m.put("allHitRate", scored.stream().filter(r -> Boolean.TRUE.equals(r.get("allHit"))).count() / (double) scored.size());
        m.put("hitRate", scored.stream().filter(r -> num(r.get("hits")) > 0).count() / (double) scored.size());
        m.put("poolRecall", scored.stream().filter(r -> r.get("poolRecall") != null)
                .mapToDouble(r -> (Double) r.get("poolRecall")).average().orElse(0.0));
        m.put("goldRankInPoolAvg", scored.stream().mapToInt(r -> (int) num(r.get("goldRankInPool")))
                .filter(v -> v > 0).average().orElse(0.0));
        return m;
    }

    private static double avg(List<Map<String, Object>> rs, String key) {
        return rs.stream().filter(r -> r.get(key) != null).mapToDouble(r -> (Double) r.get(key)).average().orElse(0.0);
    }

    private static void printSummary(Map<String, Object> report, int topK, int recallK, String rerank) {
        say("");
        say("================ 汇总 ================");
        say("%-16s %5s %8s %10s %8s %10s %9s", "bucket", "n", "Recall", "Precision", "MRR", "CtxPrec", "全中率");
        Map<String, Object> sum = asMap(report.get("summary"));
        for (String b : ALL_BUCKETS) {
            printRow(b, asMap(sum.get(b)));
        }
        say("-".repeat(72));
        printRow("检索层总体", asMap(sum.get("retrievalOverall")));
        printRow("全部样本", asMap(sum.get("allBuckets")));
        say("");
        say("配置：topK=%d  recallK=%d  rerank=%s", topK, recallK, rerank);
    }

    private static void printRow(String label, Map<String, Object> a) {
        if (a == null || a.isEmpty() || a.get("recall") == null) {
            say("%-16s %5s %8s", label, a == null ? "-" : a.getOrDefault("n", "-"), "(不参与检索指标)");
            return;
        }
        say("%-16s %5s %8.4f %10.4f %8.4f %10.4f %9.4f", label, a.get("n"), a.get("recall"),
                a.get("precision"), a.get("mrr"), a.get("contextPrecision"), a.get("allHitRate"));
    }

    // ------------------------------------------------------------------ probe

    private static void probe(PgVectorStore store, List<Map<String, Object>> golden, int recallK, int topK)
            throws Exception {
        for (Map<String, Object> g : golden) {
            List<String> fps = fingerprints(g);
            SearchRequest req = SearchRequest.builder()
                    .query(str(g.get("query")))
                    .topK(recallK)
                    .filterExpression("knowledge == '" + str(g.get("knowledge")) + "'")
                    .build();
            List<Document> pool = store.similaritySearch(req);
            say("--------------------------------------------------------------------------");
            say("%s [%s] %s", g.get("id"), g.get("bucket"), g.get("query"));
            say("  指纹 %d 个，池 %d 条，goldRank=%d", fps.size(), pool == null ? 0 : pool.size(),
                    pool == null ? 0 : firstHitRank(pool, fps));
            if (pool != null) {
                for (int i = 0; i < pool.size(); i++) {
                    Document d = pool.get(i);
                    boolean hit = fps.stream().anyMatch(fp -> norm(d.getText()).contains(norm(fp)));
                    say("  #%d %s score=%.4f %s  %s", i + 1, hit ? "*" : " ",
                            d.getScore() == null ? -1 : d.getScore(),
                            d.getMetadata().get("source"), head(d.getText(), 90));
                }
            }
        }
    }

    // ------------------------------------------------------------------ LLM 精排

    /**
     * 构造精排用的 ChatModel。
     * <p>
     * 注意：真正的精排逻辑**不在 harness 里**，而是直接 new 生产类
     * {@code cn.bugstack.ai.domain.agent.service.rag.rerank.LlmDocumentPostProcessor}。
     * 如果在 harness 里另写一个打分器，C 组测的就不是生产代码，before/after 数字不可信。
     */
    private static ChatModel buildChatModel(RerankConfig c) {
        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(c.baseUrl)
                .apiKey(c.apiKey)
                .completionsPath(c.completionsPath)
                .build();
        OpenAiChatOptions.Builder options = OpenAiChatOptions.builder()
                .model(c.modelName)
                // 精排是判定任务：temperature=0 让同一份候选稳定重排，否则 before/after 差异里
                // 会混进采样噪声，测出来的「提升」可能只是这一次运气好。
                .temperature(0.0)
                // 必须显式给 maxTokens：默认不传时服务端会自己定，而推理模型会把预算全用在
                // 思考上 —— 实测 20 候选在推理模式下 29.8s 后 finish_reason=length、content 为空。
                .maxTokens(Integer.parseInt(env("RERANK_MAX_TOKENS", "2048")));
        // ★关键开关：mimo-v2.5 是推理模型，不关推理则精排每次都会超时降级（实测全部 >20s）。
        //   置空字符串可关掉该参数、退回模型默认行为，用于做「推理开/关」对照。
        String effort = env("RERANK_REASONING_EFFORT", "none");
        if (!effort.isBlank()) {
            options.reasoningEffort(effort.trim());
        }
        return OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(options.build())
                .build();
    }

    /** 精排模型配置：默认从 MySQL 读，与生产 Armory 装配用的是同一份数据。 */
    static class RerankConfig {
        String baseUrl;
        String apiKey;
        String modelName;
        String completionsPath;

        static RerankConfig resolve() {
            String fromEnv = System.getenv("RERANK_BASE_URL");
            RerankConfig c = new RerankConfig();
            if (fromEnv != null && !fromEnv.isBlank()) {
                c.baseUrl = fromEnv.trim();
                c.apiKey = env("RERANK_API_KEY", "");
                c.modelName = env("RERANK_MODEL", "mimo-v2.5");
                c.completionsPath = env("RERANK_COMPLETIONS_PATH", "v1/chat/completions");
                return c;
            }
            String url = env("MYSQL_URL", "jdbc:mysql://127.0.0.1:13306/ai-agent-station-study"
                    + "?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false");
            DriverManagerDataSource ds = new DriverManagerDataSource();
            ds.setDriverClassName("com.mysql.cj.jdbc.Driver");
            ds.setUrl(url);
            ds.setUsername(env("MYSQL_USER", "root"));
            ds.setPassword(env("MYSQL_PASSWORD", "123456"));
            JdbcTemplate jdbc = new JdbcTemplate(ds);
            Map<String, Object> row = jdbc.queryForMap(
                    "SELECT a.base_url, a.api_key, a.completions_path, m.model_name "
                            + "FROM ai_client_model m JOIN ai_client_api a ON m.api_id = a.api_id "
                            + "WHERE m.model_id = ?", Integer.parseInt(env("RERANK_MODEL_ID", "3001")));
            c.baseUrl = String.valueOf(row.get("base_url"));
            c.apiKey = String.valueOf(row.get("api_key"));
            c.completionsPath = String.valueOf(row.get("completions_path"));
            c.modelName = String.valueOf(row.get("model_name"));
            return c;
        }
    }

    // ------------------------------------------------------------------ 基础

    private static List<Map<String, Object>> loadGolden(Path path) throws Exception {
        List<Map<String, Object>> list = new ArrayList<>();
        int lineno = 0;
        for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            lineno++;
            String s = line.trim();
            if (s.isEmpty() || s.startsWith("//")) {
                continue;
            }
            try {
                list.add(JSON.readValue(s, new TypeReference<Map<String, Object>>() {
                }));
            } catch (Exception e) {
                throw new IllegalArgumentException(path + " 第 " + lineno + " 行不是合法 JSON：" + e.getMessage());
            }
        }
        return list;
    }

    private static List<String> fingerprints(Map<String, Object> g) {
        Object mh = g.get("mustHit");
        if (!(mh instanceof List<?> l)) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (Object o : l) {
            if (o instanceof Map<?, ?> m && m.get("fingerprint") != null) {
                out.add(String.valueOf(m.get("fingerprint")));
            }
        }
        return out;
    }

    private static String firstVariant(Map<String, Object> g) {
        Object v = g.get("variants");
        if (v instanceof List<?> l && !l.isEmpty()) {
            return String.valueOf(l.get(0));
        }
        return str(g.get("query"));
    }

    /** 匹配前归一化：小写 + 连续空白压成单空格。两侧必须用同一套规则，否则指纹匹配会假阴性。 */
    static String norm(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private static DriverManagerDataSource driverManager() {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.postgresql.Driver");
        ds.setUrl(env("PG_URL", DEFAULT_PG_URL));
        ds.setUsername(env("PG_USER", DEFAULT_PG_USER));
        ds.setPassword(env("PG_PASSWORD", DEFAULT_PG_PASSWORD));
        return ds;
    }

    private static PgVectorStore buildVectorStore(JdbcTemplate jdbc) throws Exception {
        OpenAiApi embeddingApi = OpenAiApi.builder()
                .baseUrl(env("EMBEDDING_BASE_URL", DEFAULT_EMB_BASE_URL))
                .apiKey(resolveEmbeddingApiKey())
                .build();
        OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder()
                .model(env("EMBEDDING_MODEL", DEFAULT_EMB_MODEL))
                .dimensions(Integer.parseInt(env("EMBEDDING_DIMENSIONS", "512")))
                .build();
        OpenAiEmbeddingModel embeddingModel = new OpenAiEmbeddingModel(embeddingApi, MetadataMode.EMBED, options);
        PgVectorStore store = PgVectorStore.builder(jdbc, embeddingModel)
                .vectorTableName(VECTOR_TABLE)
                .build();
        store.afterPropertiesSet();
        return store;
    }

    private static String resolveEmbeddingApiKey() throws Exception {
        String fromEnv = System.getenv("EMBEDDING_API_KEY");
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv.trim();
        }
        Path yml = Paths.get(YML_PATH);
        if (!Files.exists(yml)) {
            throw new IllegalStateException("找不到 " + YML_PATH + "，请用 EMBEDDING_API_KEY 环境变量提供密钥");
        }
        List<String> lines = Files.readAllLines(yml, StandardCharsets.UTF_8);
        int embIdx = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).trim().equals("embedding:")) {
                embIdx = i;
            }
        }
        if (embIdx < 0) {
            throw new IllegalStateException("yml 里没有 embedding: 段");
        }
        int baseIndent = indentOf(lines.get(embIdx));
        for (int i = embIdx + 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.isBlank()) {
                continue;
            }
            if (indentOf(line) <= baseIndent) {
                break;
            }
            Matcher m = Pattern.compile("^\\s*api-key:\\s*(\\S+)\\s*$").matcher(line);
            if (m.find()) {
                return m.group(1);
            }
        }
        throw new IllegalStateException("embedding 段里没有 api-key");
    }

    private static int indentOf(String line) {
        int i = 0;
        while (i < line.length() && line.charAt(i) == ' ') {
            i++;
        }
        return i;
    }

    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> m = new LinkedHashMap<>();
        int i = 0;
        if (args.length > 0 && !args[0].startsWith("--")) {
            m.put("_mode", args[0]);
            i = 1;
        }
        for (; i < args.length; i++) {
            String a = args[i];
            if (a.startsWith("--")) {
                String key = a.substring(2);
                if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                    m.put(key, args[++i]);
                } else {
                    m.put(key, "true");
                }
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

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object o) {
        return o instanceof Map ? (Map<String, Object>) o : new LinkedHashMap<>();
    }

    private static long num(Object o) {
        return o instanceof Number n ? n.longValue() : 0L;
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static String head(String s, int n) {
        String flat = s == null ? "" : s.replaceAll("\\s+", " ").trim();
        return flat.length() <= n ? flat : flat.substring(0, n) + "…";
    }

    private static String env(String key, String def) {
        String v = System.getenv(key);
        return (v == null || v.isBlank()) ? def : v.trim();
    }

    private static void say(String fmt, Object... args) {
        System.out.println(args.length == 0 ? fmt : String.format(fmt, args));
    }
}
