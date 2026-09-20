import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * RAG 评测语料入库程序（独立 main，不启动 Spring 容器）。
 *
 * 为什么不用 @SpringBootTest：
 *   1) 项目 skipTests=true，且起容器会连带拉起 Armory 装配 / 任务调度 / MySQL 连接，
 *      为了往 pgvector 写几万条 chunk 付这个代价不值；
 *   2) 评测要能高频重跑，独立 main 秒级启动。
 *
 * 关键纪律：本程序**复刻** AiAgentConfig#pgVectorStore 的构建参数
 *   （表名 vector_store_openai、embedding 走 DashScope text-embedding-v3 / 512 维、
 *     TokenTextSplitter 用默认构造），任何一处不一致都会让评测结论对不上生产行为。
 *
 * 用法：
 *   count
 *   clear      <knowledge>                     # 删该标签全部 chunk
 *   clear-all                                  # 清空向量表（评测专用，慎用）
 *   ingest     <corpusDir> <knowledge> [limit] # 入库（先删同标签，再写）
 *   snapshot   <knowledge> <outJsonl>          # 导出 chunk 快照（给 golden set 生成用）
 *   search     <knowledge> <query> <topK>      # 冒烟：跑一次相似度检索
 *
 * 环境变量（都有默认值，默认值 = application-dev.yml 的生产配置）：
 *   PG_URL / PG_USER / PG_PASSWORD
 *   EMBEDDING_BASE_URL / EMBEDDING_MODEL / EMBEDDING_DIMENSIONS / EMBEDDING_API_KEY
 *   INGEST_BATCH（单次 embedding 请求的文档数，默认 10）
 */
public class RagCorpusIngest {

    // ===== 与 application-dev.yml 保持一致 =====
    private static final String DEFAULT_PG_URL = "jdbc:postgresql://127.0.0.1:15432/ai-rag-knowledge";
    private static final String DEFAULT_PG_USER = "postgres";
    private static final String DEFAULT_PG_PASSWORD = "postgres";
    private static final String DEFAULT_EMB_BASE_URL = "https://dashscope.aliyuncs.com/compatible-mode";
    private static final String DEFAULT_EMB_MODEL = "text-embedding-v3";
    private static final int DEFAULT_EMB_DIMENSIONS = 512;
    private static final String VECTOR_TABLE = "vector_store_openai";
    private static final String YML_PATH = "ai-agent-station-study-app/src/main/resources/application-dev.yml";

    private static final ObjectMapper JSON = new ObjectMapper();
    private static int failures = 0;

    public static void main(String[] args) {
        try {
            run(args);
        } catch (Throwable t) {
            // 必须显式兜住：System.out 在重定向到文件时是带缓冲的，
            // 异常直接冒泡会导致「日志停在半截、看不到任何错误」（已踩过）。
            say("%n!! 未捕获异常：%s", t);
            t.printStackTrace(System.err);
            System.out.flush();
            System.err.flush();
            System.exit(1);
        }
    }

    private static void run(String[] args) throws Exception {
        if (args.length == 0) {
            usage();
            System.exit(2);
        }
        String mode = args[0].toLowerCase(Locale.ROOT);

        JdbcTemplate jdbc = new JdbcTemplate(driverManager());
        switch (mode) {
            case "count" -> count(jdbc);
            case "clear" -> {
                require(args, 2, "clear <knowledge>");
                System.out.println("删除 knowledge=" + args[1] + " 的 chunk：" + deleteByKnowledge(jdbc, args[1]) + " 行");
                count(jdbc);
            }
            case "clear-all" -> {
                int n = jdbc.update("DELETE FROM " + VECTOR_TABLE);
                System.out.println("清空 " + VECTOR_TABLE + "：" + n + " 行");
                count(jdbc);
            }
            case "ingest" -> {
                require(args, 3, "ingest <corpusDir> <knowledge> [limit]");
                int limit = args.length > 3 ? Integer.parseInt(args[3]) : Integer.MAX_VALUE;
                ingest(jdbc, Paths.get(args[1]), args[2], limit);
            }
            case "snapshot" -> {
                require(args, 3, "snapshot <knowledge> <outJsonl>");
                snapshot(jdbc, args[1], Paths.get(args[2]));
            }
            case "search" -> {
                require(args, 4, "search <knowledge> <query> <topK>");
                search(jdbc, args[1], args[2], Integer.parseInt(args[3]));
            }
            default -> {
                usage();
                System.exit(2);
            }
        }
        if (failures > 0) {
            System.out.println("\n失败 " + failures + " 项");
            System.exit(1);
        }
    }

    private static void usage() {
        System.out.println("""
                RagCorpusIngest —— RAG 评测语料入库（独立 main，不启 Spring 容器）

                  count
                  clear      <knowledge>
                  clear-all
                  ingest     <corpusDir> <knowledge> [limit]
                  snapshot   <knowledge> <outJsonl>
                  search     <knowledge> <query> <topK>
                """);
    }

    private static void require(String[] args, int n, String hint) {
        if (args.length < n) {
            System.out.println("参数不足，应为：" + hint);
            System.exit(2);
        }
    }

    /**
     * 用 println 而非 printf —— println 会自动 flush，printf 不会。
     * 之前用 printf 时进程异常退出，日志停在半截且看不到任何错误信息。
     */
    private static void say(String fmt, Object... args) {
        System.out.println(args.length == 0 ? fmt : String.format(fmt, args));
    }

    // ------------------------------------------------------------------ 连接

    private static DriverManagerDataSource driverManager() {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.postgresql.Driver");
        ds.setUrl(env("PG_URL", DEFAULT_PG_URL));
        ds.setUsername(env("PG_USER", DEFAULT_PG_USER));
        ds.setPassword(env("PG_PASSWORD", DEFAULT_PG_PASSWORD));
        return ds;
    }

    /**
     * 复刻 AiAgentConfig#pgVectorStore（表名、embedding 模型、维度全部对齐）。
     */
    private static PgVectorStore buildVectorStore(JdbcTemplate jdbc) throws Exception {
        OpenAiApi embeddingApi = OpenAiApi.builder()
                .baseUrl(env("EMBEDDING_BASE_URL", DEFAULT_EMB_BASE_URL))
                .apiKey(resolveEmbeddingApiKey())
                .build();
        OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder()
                .model(env("EMBEDDING_MODEL", DEFAULT_EMB_MODEL))
                .dimensions(Integer.parseInt(env("EMBEDDING_DIMENSIONS", String.valueOf(DEFAULT_EMB_DIMENSIONS))))
                .build();
        OpenAiEmbeddingModel embeddingModel = new OpenAiEmbeddingModel(embeddingApi, MetadataMode.EMBED, options);
        PgVectorStore store = PgVectorStore.builder(jdbc, embeddingModel)
                .vectorTableName(VECTOR_TABLE)
                .build();
        // 手工构建时 Spring 不会回调 InitializingBean；显式调用以复刻 @Bean 的生命周期。
        store.afterPropertiesSet();
        return store;
    }

    /**
     * embedding key 优先取环境变量；没有则从 application-dev.yml 里读，
     * 避免把密钥再抄一份到评测目录（那个文件本来就在库里）。
     */
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
            throw new IllegalStateException(yml + " 里没有 embedding: 段，请用 EMBEDDING_API_KEY 环境变量提供密钥");
        }
        int baseIndent = indentOf(lines.get(embIdx));
        for (int i = embIdx + 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.isBlank()) {
                continue;
            }
            if (indentOf(line) <= baseIndent) {
                break; // 走出 embedding 段
            }
            Matcher m = Pattern.compile("^\\s*api-key:\\s*(\\S+)\\s*$").matcher(line);
            if (m.find()) {
                return m.group(1);
            }
        }
        throw new IllegalStateException("embedding 段里没有 api-key，请用 EMBEDDING_API_KEY 环境变量提供");
    }

    private static int indentOf(String line) {
        int i = 0;
        while (i < line.length() && line.charAt(i) == ' ') {
            i++;
        }
        return i;
    }

    // ------------------------------------------------------------------ 入库

    private static void ingest(JdbcTemplate jdbc, Path corpusDir, String knowledge, int limit) throws Exception {
        if (!Files.isDirectory(corpusDir)) {
            throw new IllegalArgumentException("不是目录：" + corpusDir.toAbsolutePath());
        }
        List<Path> files = listCorpusFiles(corpusDir);
        if (files.isEmpty()) {
            throw new IllegalStateException("目录里没有可入库的文本：" + corpusDir.toAbsolutePath());
        }
        if (limit < files.size()) {
            files = files.subList(0, limit);
        }

        System.out.println("== 入库 ==");
        System.out.println("语料目录 : " + corpusDir.toAbsolutePath());
        System.out.println("knowledge: " + knowledge);
        System.out.println("文件数   : " + files.size() + (limit < Integer.MAX_VALUE ? "（已按 limit 截断）" : ""));
        System.out.println();

        PgVectorStore store = buildVectorStore(jdbc);
        TokenTextSplitter splitter = new TokenTextSplitter(); // 与 AiAgentConfig#tokenTextSplitter 等价

        int removed = deleteByKnowledge(jdbc, knowledge);
        System.out.println("清理同标签旧数据：" + removed + " 行\n");

        int batch = Integer.parseInt(env("INGEST_BATCH", "10"));
        long t0 = System.currentTimeMillis();
        int totalChunks = 0;
        int totalChars = 0;
        int minLen = Integer.MAX_VALUE;
        int maxLen = 0;
        List<Map<String, Object>> perFile = new ArrayList<>();

        for (int fi = 0; fi < files.size(); fi++) {
            Path file = files.get(fi);
            String rel = rel(corpusDir, file);
            String text = Files.readString(file, StandardCharsets.UTF_8);

            // 复刻 RagService#storeRagFile：先定 ragId 再写 chunk（否则更新链路删不掉）
            String ragId = UUID.randomUUID().toString();
            String fileHash = md5(text.getBytes(StandardCharsets.UTF_8));

            Document raw = new Document(text, new LinkedHashMap<>(Map.of("source", rel)));
            List<Document> chunks = splitter.apply(List.of(raw));
            for (Document doc : chunks) {
                Map<String, Object> metadata = new LinkedHashMap<>();
                metadata.put("knowledge", knowledge);
                metadata.put("ragId", ragId);
                metadata.put("version", "1");
                metadata.put("lastUpdateTime", LocalDateTime.now().toString());
                metadata.put("fileHash", fileHash);
                metadata.put("updateReason", "初始上传");
                metadata.put("source", rel);
                doc.getMetadata().putAll(metadata);
            }

            int lenSum = 0;
            for (Document doc : chunks) {
                int len = doc.getText().length();
                lenSum += len;
                minLen = Math.min(minLen, len);
                maxLen = Math.max(maxLen, len);
            }

            // 分批写入：DashScope 单请求的输入条数有限制，不依赖框架的 token 分批策略
            for (int i = 0; i < chunks.size(); i += batch) {
                List<Document> slice = chunks.subList(i, Math.min(i + batch, chunks.size()));
                try {
                    store.add(slice);
                } catch (Exception e) {
                    failures++;
                    say("  [FAIL] %s chunk[%d,%d) -> %s%n",
                            rel, i, Math.min(i + batch, chunks.size()), e.getMessage());
                }
            }

            totalChunks += chunks.size();
            totalChars += lenSum;
            perFile.add(Map.of(
                    "file", rel, "chars", text.length(), "chunks", chunks.size(),
                    "chunkCharsSum", lenSum, "ragId", ragId, "fileHash", fileHash));
            say("[%3d/%3d] %-58s %7d 字 -> %3d chunk%n",
                    fi + 1, files.size(), rel, text.length(), chunks.size());
        }

        long ms = System.currentTimeMillis() - t0;
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("knowledge", knowledge);
        stats.put("corpusDir", corpusDir.toAbsolutePath().toString());
        stats.put("files", files.size());
        stats.put("chunks", totalChunks);
        stats.put("chars", totalChars);
        stats.put("avgChunkChars", totalChunks == 0 ? 0 : totalChars / totalChunks);
        stats.put("minChunkChars", minLen == Integer.MAX_VALUE ? 0 : minLen);
        stats.put("maxChunkChars", maxLen);
        stats.put("elapsedMs", ms);
        stats.put("perFile", perFile);

        Path out = Paths.get("docs/rag-eval/_local/runs/ingest-" + knowledge + ".json");
        Files.createDirectories(out.getParent());
        Files.writeString(out, JSON.writerWithDefaultPrettyPrinter().writeValueAsString(stats), StandardCharsets.UTF_8);

        System.out.println();
        System.out.println("== 入库完成 ==");
        System.out.println("chunk 总数 : " + totalChunks);
        System.out.println("字符总数   : " + totalChars);
        System.out.println("chunk 长度 : min=" + stats.get("minChunkChars") + " avg=" + stats.get("avgChunkChars") + " max=" + maxLen);
        System.out.println("耗时       : " + ms + " ms");
        System.out.println("统计已写出 : " + out.toAbsolutePath());
        count(jdbc);
    }

    private static List<Path> listCorpusFiles(Path dir) throws Exception {
        try (Stream<Path> s = Files.walk(dir)) {
            return s.filter(Files::isRegularFile)
                    .filter(p -> {
                        String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
                        return n.endsWith(".txt") || n.endsWith(".md") || n.endsWith(".adoc");
                    })
                    .sorted(Comparator.comparing(Path::toString))
                    .collect(Collectors.toList());
        }
    }

    // ------------------------------------------------------------------ 快照

    /**
     * 导出 chunk 快照（JSONL）。用途：给 golden set 生成阶段挑「目标片段」。
     *
     * ⚠️ 快照里的 chunk id 是**一次性的**（更新链路先删后插，UUID 每次都变）。
     *    golden set 只允许锚「文档名 + 段落文本指纹」，禁止锚 chunk UUID。
     */
    private static void snapshot(JdbcTemplate jdbc, String knowledge, Path out) throws Exception {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id::text AS id, content, metadata FROM " + VECTOR_TABLE
                        + " WHERE metadata->>'knowledge' = ? ORDER BY metadata->>'source', id", knowledge);

        Files.createDirectories(out.toAbsolutePath().getParent());
        StringBuilder sb = new StringBuilder();
        long chars = 0;
        for (Map<String, Object> row : rows) {
            Map<String, Object> meta = JSON.readValue(String.valueOf(row.get("metadata")), Map.class);
            String content = String.valueOf(row.get("content"));
            chars += content.length();
            Map<String, Object> rec = new LinkedHashMap<>();
            rec.put("chunkId", row.get("id"));
            rec.put("source", meta.get("source"));
            rec.put("knowledge", meta.get("knowledge"));
            rec.put("ragId", meta.get("ragId"));
            rec.put("charLen", content.length());
            rec.put("content", content);
            sb.append(JSON.writeValueAsString(rec)).append('\n');
        }
        Files.writeString(out, sb.toString(), StandardCharsets.UTF_8);

        System.out.println("== chunk 快照 ==");
        System.out.println("knowledge : " + knowledge);
        System.out.println("chunk 数  : " + rows.size());
        System.out.println("字符总数  : " + chars);
        System.out.println("平均长度  : " + (rows.isEmpty() ? 0 : chars / rows.size()));
        System.out.println("已写出    : " + out.toAbsolutePath());

        Map<String, Long> bySource = rows.stream().collect(Collectors.groupingBy(
                r -> {
                    try {
                        Map<?, ?> m = JSON.readValue(String.valueOf(r.get("metadata")), Map.class);
                        return String.valueOf(m.get("source"));
                    } catch (Exception e) {
                        return "?";
                    }
                }, Collectors.counting()));
        System.out.println("文档数    : " + bySource.size());
        bySource.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(e -> say("  %-58s %3d chunk%n", e.getKey(), e.getValue()));
    }

    // ------------------------------------------------------------------ 检索冒烟

    private static void search(JdbcTemplate jdbc, String knowledge, String query, int topK) throws Exception {
        PgVectorStore store = buildVectorStore(jdbc);
        SearchRequest req = SearchRequest.builder()
                .query(query)
                .topK(topK)
                .filterExpression("knowledge == '" + knowledge + "'")
                .build();
        List<Document> docs = store.similaritySearch(req);
        System.out.println("== 检索冒烟 ==");
        System.out.println("query     : " + query);
        System.out.println("filter    : knowledge == '" + knowledge + "'");
        System.out.println("topK      : " + topK);
        System.out.println("返回条数  : " + (docs == null ? 0 : docs.size()));
        if (docs != null) {
            for (int i = 0; i < docs.size(); i++) {
                Document d = docs.get(i);
                say("%n#%d  score=%.4f  source=%s  len=%d%n    %s%n",
                        i + 1, d.getScore() == null ? -1 : d.getScore(),
                        d.getMetadata().get("source"), d.getText().length(), head(d.getText(), 220));
            }
        }
    }

    // ------------------------------------------------------------------ 工具

    private static int deleteByKnowledge(JdbcTemplate jdbc, String knowledge) {
        return jdbc.update("DELETE FROM " + VECTOR_TABLE + " WHERE metadata->>'knowledge' = ?", knowledge);
    }

    private static void count(JdbcTemplate jdbc) {
        System.out.println();
        System.out.println("== 向量表现状 ==");
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT COALESCE(metadata->>'knowledge','(无标签)') AS k, count(*) AS n, "
                        + "count(*) FILTER (WHERE COALESCE(metadata->>'ragId','') = '') AS no_rag_id "
                        + "FROM " + VECTOR_TABLE + " GROUP BY 1 ORDER BY 2 DESC");
        if (rows.isEmpty()) {
            System.out.println("(空表)");
            return;
        }
        for (Map<String, Object> r : rows) {
            say("  %-28s %6s 行   其中无 ragId: %s%n", r.get("k"), r.get("n"), r.get("no_rag_id"));
        }
    }

    private static String rel(Path base, Path file) {
        return base.toAbsolutePath().relativize(file.toAbsolutePath()).toString().replace('\\', '/');
    }

    private static String md5(byte[] bytes) throws Exception {
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] digest = md.digest(bytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static String head(String s, int n) {
        String flat = s.replaceAll("\\s+", " ").trim();
        return flat.length() <= n ? flat : flat.substring(0, n) + "…";
    }

    private static String env(String key, String def) {
        String v = System.getenv(key);
        return (v == null || v.isBlank()) ? def : v.trim();
    }
}
