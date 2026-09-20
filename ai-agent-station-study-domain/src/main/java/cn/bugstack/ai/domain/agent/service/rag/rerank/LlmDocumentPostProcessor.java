package cn.bugstack.ai.domain.agent.service.rag.rerank;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.postretrieval.document.DocumentPostProcessor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 基于 LLM 的检索结果精排（listwise 打分）。
 * <p>
 * 为什么挂在 {@link DocumentPostProcessor} 而不是自己定义接口：
 * 这是 Spring AI 1.1.8 官方的「检索后处理」扩展点（{@code org.springframework.ai.rag} 模块，
 * 需要显式引入 {@code spring-ai-rag} 依赖）。用它有两个好处：
 * <ol>
 *     <li>语义对齐 —— 它本来就是为「召回之后、拼 prompt 之前」这一步设计的；</li>
 *     <li>可替换 —— 将来若换成 cross-encoder 或云厂商 rerank API，只换实现类，调用方不动。</li>
 * </ol>
 * 注意：1.1.8 里 <b>没有</b> {@code DocumentRanker} 接口（里程碑版本有过，已移除），
 * 别照着旧资料去实现它。
 * <p>
 * 为什么是 listwise（一次调用给全部候选打分）而不是逐条打分：
 * 逐条打分要做 N 次模型调用，且不同调用之间的分数不可比；listwise 一次调用里模型能看到全部候选，
 * 分数在同一上下文中产生，排序才可解释。
 * <p>
 * <b>失败必须降级</b>：精排是可选增强，不是业务语义的一部分。任何异常（超时 / 返回不可解析 /
 * index 越界）都退回「原始召回顺序的前 topK 条」，绝不能让精排变成新的故障点。
 * 尤其要兜住「返回空列表」这条路径 —— 空上下文比不精排更糟（模型会凭空编答案）。
 *
 * @author 评测改造
 */
@Slf4j
public class LlmDocumentPostProcessor implements DocumentPostProcessor {

    /**
     * 共享线程池：超时控制靠 future.get(timeout) 实现，而 future 必须跑在独立线程上。
     * 用静态池是为了避免「每次调用 new 一个线程池」—— 那样在 QPS 上来后会持续创建/销毁线程。
     */
    private static final ExecutorService TIMEOUT_POOL = Executors.newCachedThreadPool(new ThreadFactory() {
        private final AtomicInteger seq = new AtomicInteger();

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "llm-rerank-" + seq.incrementAndGet());
            t.setDaemon(true);
            return t;
        }
    });

    private final ChatModel chatModel;
    /** 精排后最终要保留的条数（= 送进 prompt 的条数） */
    private final int finalTopK;
    private final long timeoutMs;
    /** 每个候选片段送进 prompt 的最大字符数；截断只为控成本，不影响「是否包含答案」的判断 */
    private final int docChars;

    public LlmDocumentPostProcessor(ChatModel chatModel, int finalTopK, long timeoutMs, int docChars) {
        this.chatModel = chatModel;
        this.finalTopK = finalTopK;
        this.timeoutMs = timeoutMs > 0 ? timeoutMs : 3000L;
        this.docChars = docChars > 0 ? docChars : 400;
    }

    public int getFinalTopK() {
        return finalTopK;
    }

    @Override
    public List<Document> process(Query query, List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return documents;
        }
        // 候选本来就不比目标条数多，没有可重排的空间，直接原样返回（也省一次模型调用）
        if (documents.size() <= finalTopK) {
            return documents;
        }

        try {
            String prompt = buildPrompt(query == null ? "" : query.text(), documents);
            Map<Integer, Double> scores = scoreWithTimeout(prompt, documents.size());
            if (scores.isEmpty()) {
                throw new IllegalStateException("精排返回无法解析出任何有效分数");
            }
            List<Document> ordered = reorder(documents, scores);
            List<Document> result = ordered.subList(0, Math.min(finalTopK, ordered.size()));
            if (log.isDebugEnabled()) {
                log.debug("LLM 精排完成，候选 {} 条 -> 保留 {} 条", documents.size(), result.size());
            }
            return new ArrayList<>(result);
        } catch (Exception e) {
            // 降级：任何异常都退回原始召回顺序的前 topK 条
            log.warn("LLM 精排失败，降级为原始召回顺序：{}", e.toString());
            return new ArrayList<>(documents.subList(0, Math.min(finalTopK, documents.size())));
        }
    }

    // ------------------------------------------------------------------ 打分

    private Map<Integer, Double> scoreWithTimeout(String prompt, int candidateCount) throws Exception {
        Future<String> future = TIMEOUT_POOL.submit((Callable<String>) () -> chatModel.call(prompt));
        try {
            String raw = future.get(timeoutMs, TimeUnit.MILLISECONDS);
            return parseScores(raw, candidateCount);
        } catch (Exception e) {
            // 超时后必须 cancel，否则这个线程会继续占着模型连接跑到天荒地老
            future.cancel(true);
            throw e;
        }
    }

    private String buildPrompt(String question, List<Document> documents) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are a retrieval reranker. For each candidate passage, score how useful it is ");
        sb.append("for answering the question.\n");
        sb.append("Score 0-10 (10 = the passage alone is enough to answer). ");
        sb.append("Judge only whether the needed information is present; ignore length and writing style.\n");
        sb.append("Output JSON only. No explanation, no markdown code fence.\n\n");
        sb.append("Question: ").append(question).append("\n\nCandidates:\n");
        for (int i = 0; i < documents.size(); i++) {
            sb.append('[').append(i + 1).append("] ").append(truncate(documents.get(i).getText())).append('\n');
        }
        sb.append("\nOutput format: {\"scores\":[{\"index\":1,\"score\":9},{\"index\":2,\"score\":3}]}");
        return sb.toString();
    }

    /**
     * 用「序号」而不是 Document id 作为候选标识。
     * 原因：pgvector 的 Document id 是 36 字符 UUID，20 个候选光 id 就 720 字符，
     * 对模型判断毫无帮助却显著抬高输入成本；序号 1-2 位即可，且模型更不容易抄错。
     */
    private Map<Integer, Double> parseScores(String raw, int candidateCount) {
        Map<Integer, Double> scores = new LinkedHashMap<>();
        if (raw == null || raw.isBlank()) {
            return scores;
        }
        String text = stripCodeFence(raw);
        JSONArray array = null;
        int objStart = text.indexOf('{');
        int objEnd = text.lastIndexOf('}');
        if (objStart >= 0 && objEnd > objStart) {
            JSONObject obj = JSON.parseObject(text.substring(objStart, objEnd + 1));
            if (obj != null) {
                array = obj.getJSONArray("scores");
            }
        }
        if (array == null) {
            // 容错：模型直接返回了裸数组
            int arrStart = text.indexOf('[');
            int arrEnd = text.lastIndexOf(']');
            if (arrStart >= 0 && arrEnd > arrStart) {
                array = JSON.parseArray(text.substring(arrStart, arrEnd + 1));
            }
        }
        if (array == null) {
            return scores;
        }
        for (int i = 0; i < array.size(); i++) {
            JSONObject item = array.getJSONObject(i);
            if (item == null) {
                continue;
            }
            Integer index = item.getInteger("index");
            Double score = item.getDouble("score");
            if (index == null || score == null) {
                continue;
            }
            if (index >= 1 && index <= candidateCount) {
                scores.putIfAbsent(index, score);
            }
        }
        return scores;
    }

    /**
     * 按分数降序重排。
     * <p>
     * 未被模型打分的候选<b>不丢弃</b>，而是追加在已打分项之后并保持原有相对顺序。
     * 直接丢弃会让「部分解析失败」的结果比不精排还差 —— 退化路径必须兜住。
     */
    private List<Document> reorder(List<Document> documents, Map<Integer, Double> scores) {
        List<Map.Entry<Integer, Double>> entries = new ArrayList<>(scores.entrySet());
        entries.sort((a, b) -> {
            int cmp = Double.compare(b.getValue(), a.getValue());
            return cmp != 0 ? cmp : Integer.compare(a.getKey(), b.getKey());
        });
        List<Document> ordered = new ArrayList<>(documents.size());
        for (Map.Entry<Integer, Double> e : entries) {
            ordered.add(documents.get(e.getKey() - 1));
        }
        for (int i = 0; i < documents.size(); i++) {
            if (!scores.containsKey(i + 1)) {
                ordered.add(documents.get(i));
            }
        }
        return ordered;
    }

    private String stripCodeFence(String raw) {
        String s = raw.trim();
        if (s.startsWith("```")) {
            int firstNewline = s.indexOf('\n');
            int lastFence = s.lastIndexOf("```");
            if (firstNewline > 0 && lastFence > firstNewline) {
                s = s.substring(firstNewline + 1, lastFence);
            }
        }
        return s;
    }

    private String truncate(String text) {
        if (text == null) {
            return "";
        }
        String flat = text.replaceAll("\\s+", " ").trim();
        return flat.length() <= docChars ? flat : flat.substring(0, docChars) + "…";
    }
}
