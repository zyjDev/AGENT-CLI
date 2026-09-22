package cn.bugstack.ai.domain.agent.service.armory.node.factory.element;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.postretrieval.document.DocumentPostProcessor;
import org.springframework.ai.rag.preretrieval.query.expansion.QueryExpander;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionTextParser;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
public class RagAnswerAdvisor implements BaseAdvisor {

    private static final int DEFAULT_TOP_K = 4;

    private final VectorStore vectorStore;
    private final SearchRequest searchRequest;
    private final String userTextAdvise;

    /** 最终送进 prompt 的条数（改造前等价于 searchRequest.topK） */
    private final int topK;
    /** 召回池大小；= topK 时表示不精排 */
    private final int recallK;
    /**
     * 精排器。为 null 表示未开启精排 —— 此时 before() 的行为与改造前**完全等价**，
     * 这就是「零侵入可回退」的实现方式：关开关不靠分支判断，靠「压根不构造精排器」。
     */
    private final DocumentPostProcessor postProcessor;

    /**
     * 查询改写器。为 null 表示未开启多查询 —— 此时 before() 只召回一次，
     * 行为与改造前**完全等价**（与精排同一个降级思路：不靠分支判断，靠「压根不构造」）。
     */
    private final QueryExpander queryExpander;

    /** 兼容旧调用：不精排、不扩查询 */
    public RagAnswerAdvisor(VectorStore vectorStore, SearchRequest searchRequest) {
        this(vectorStore, searchRequest, resolveTopK(searchRequest), resolveTopK(searchRequest), null, null);
    }

    /** 兼容旧调用：带精排、不扩查询 */
    public RagAnswerAdvisor(VectorStore vectorStore, SearchRequest searchRequest,
                            int topK, int recallK, DocumentPostProcessor postProcessor) {
        this(vectorStore, searchRequest, topK, recallK, postProcessor, null);
    }

    public RagAnswerAdvisor(VectorStore vectorStore, SearchRequest searchRequest,
                            int topK, int recallK, DocumentPostProcessor postProcessor,
                            QueryExpander queryExpander) {
        this.vectorStore = vectorStore;
        this.searchRequest = searchRequest;
        this.topK = topK > 0 ? topK : DEFAULT_TOP_K;
        this.recallK = Math.max(recallK, this.topK);
        this.postProcessor = postProcessor;
        this.queryExpander = queryExpander;
        this.userTextAdvise = "\nContext information is below, surrounded by ---------------------\n\n---------------------\n{question_answer_context}\n---------------------\n\nGiven the context and provided history information and not prior knowledge,\nreply to the user comment. If the answer is not in the context, inform\nthe user that you can't answer the question.\n";
    }

    private static int resolveTopK(SearchRequest searchRequest) {
        Integer topK = searchRequest == null ? null : searchRequest.getTopK();
        return topK == null || topK <= 0 ? DEFAULT_TOP_K : topK;
    }

    /**
     * 查询改写：把用户原问题扩成多条「不同角度」的查询。
     * <p>
     * <b>永不抛异常、永不返回空列表</b> —— 改写是可选增强，它失败必须退化成「单查询」，
     * 而不是把整次问答打断。这与精排器「解析失败即降级」的取舍一致。
     * <p>
     * <b>降级要能被看见</b>：Spring AI 的 {@code MultiQueryExpander} 在两种情况下会静默原样返回
     * 1 条查询 —— ① 模型 content 为 null；② 输出按换行切出来的行数 ≠ numberOfQueries。
     * 此时链路上完全看不出「改写没生效」，故这里显式打 WARN 日志。
     *
     * @param userText 用户原始问题
     * @return 查询列表；未开启改写时是只含原查询的单元素列表
     */
    private List<Query> expandQueries(String userText) {
        if (this.queryExpander == null) {
            return List.of(new Query(userText));
        }
        try {
            List<Query> expanded = this.queryExpander.expand(new Query(userText));
            if (expanded == null || expanded.isEmpty()) {
                log.warn("查询改写返回空结果，降级为单查询。query={}", userText);
                return List.of(new Query(userText));
            }
            if (expanded.size() == 1) {
                log.warn("查询改写未生效（框架降级为单查询：模型返回空 或 输出行数不匹配），按原查询召回。query={}", userText);
            } else {
                log.info("查询改写生效：共 {} 条查询（含原始查询），query={}", expanded.size(), userText);
            }
            return expanded;
        } catch (Exception e) {
            log.warn("查询改写调用异常，降级为单查询。query={}, err={}", userText, e.toString());
            return List.of(new Query(userText));
        }
    }

    /**
     * 召回候选池。
     * <p>
     * <b>池子上限恒为 {@code max(recallK, topK)}，多查询只改变「池子怎么填」，不改变「池子多大」。</b>
     * 这样精排的输入规模、耗时与改造前一致 —— 打开多查询不会放大下游开销（多查询本身只增加
     * N 次向量检索，那是毫秒级操作）。想让池子更大请调 {@code recallK}，那才是它本来的语义。
     *
     * @param queries 查询列表（≥1 条）
     * @param context 请求上下文（用于解析 filterExpression）
     */
    private List<Document> retrieve(List<Query> queries, Map<String, Object> context) {
        int poolSize = Math.max(this.recallK, this.topK);

        // 单查询：直接返回，检索结果本身已按相似度降序，无需再排
        if (queries.size() == 1) {
            return this.search(queries.get(0).text(), poolSize, context);
        }

        // 多路召回合并：同一个 chunk 很可能被多个变体同时命中 → 按 id 去重，保留分数更高的那份。
        // （框架自带 ConcatenationDocumentJoiner 做同样的事，但它直接对 getScore() 拆箱比较，
        //   遇到 score 为 null 的 VectorStore 实现会 NPE，故这里自己实现并做 null 兜底。）
        Map<String, Document> merged = new LinkedHashMap<>();
        for (Query query : queries) {
            for (Document doc : this.search(query.text(), poolSize, context)) {
                Document exist = merged.get(doc.getId());
                if (exist == null || scoreOf(doc) > scoreOf(exist)) {
                    merged.put(doc.getId(), doc);
                }
            }
        }

        // 多路结果同源（同一 embedding 模型、同一距离度量），分数可直接横向比较 → 全局按相似度降序。
        // 注意：这会丢掉「每路各自保留 topN」的多样性保证，但下游还有精排/截断，
        // 且全局排序与框架 DocumentJoiner 的语义一致，故不额外做 round-robin 交错。
        List<Document> mergedList = new ArrayList<>(merged.values());
        mergedList.sort(Comparator.comparingDouble(RagAnswerAdvisor::scoreOf).reversed());

        return mergedList.size() > poolSize
                ? new ArrayList<>(mergedList.subList(0, poolSize))
                : mergedList;
    }

    /**
     * 单次向量检索。
     * <p>
     * ⚠️ {@code SearchRequest.from(...)} 是**复制**。绝不能在 {@code this.searchRequest} 上直接
     * {@code setTopK} / 改 query —— 这个 advisor 是单例 Bean，改了会污染后续所有请求。
     */
    private List<Document> search(String text, int topK, Map<String, Object> context) {
        SearchRequest request = SearchRequest.from(this.searchRequest)
                .query(text)
                .topK(topK)
                .filterExpression(this.resolveFilterExpression(context))
                .build();
        List<Document> documents = this.vectorStore.similaritySearch(request);
        return documents == null ? List.of() : documents;
    }

    /**
     * 取相似度分数，null 视为「最低」。
     * <p>
     * PgVectorStore 的 rowMapper 一定会回填 score（{@code 1 - distance}），但 VectorStore 是接口，
     * 换成别的实现就可能拿到 null —— 直接拆箱比较会 NPE，故在此统一兜底。
     */
    private static double scoreOf(Document document) {
        Double score = document.getScore();
        return score == null ? Double.NEGATIVE_INFINITY : score;
    }

    @Override
    public ChatClientRequest before(ChatClientRequest chatClientRequest, AdvisorChain advisorChain) {
        HashMap<String, Object> context = new HashMap<>(chatClientRequest.context());

        String userText = chatClientRequest.prompt().getUserMessage().getText();

        // ① 查询改写（可选增强）：把 1 个问题扩成 N 个「不同角度」的变体。
        //    这是给「答案分散在多个文档 / 需要多跳」的问题准备的 —— 单条查询只能命中一个语义方向，
        //    换几个说法去问，能把别的文档里的相关 chunk 一起捞进池子。
        //    返回列表**至少含原始查询**（框架降级时原样返回 1 条）。
        List<Query> queries = this.expandQueries(userText);

        // ② 召回：池子上限固定为 recallK（未开启精排时 recallK == topK，与改造前完全一致）。
        //    单查询 → 一次检索；多查询 → 每路各召回 recallK 条，按 id 去重后截回 recallK 条。
        List<Document> candidates = this.retrieve(queries, context);

        // ③ 精排：候选比目标条数多时才值得重排；postProcessor 为 null 时直接跳过
        List<Document> documents = candidates;
        if (this.postProcessor != null && candidates.size() > this.topK) {
            List<Document> reranked = this.postProcessor.process(new Query(userText), candidates);
            // 精排器内部已降级过一次，这里再兜一层：拿到空列表就退回原顺序前 topK 条。
            // 空上下文会让模型凭空作答，比不精排更糟。
            documents = (reranked == null || reranked.isEmpty())
                    ? candidates.subList(0, Math.min(this.topK, candidates.size()))
                    : reranked;
        } else if (documents.size() > this.topK) {
            documents = documents.subList(0, this.topK);
        }

        // ④ 观测：三个 key 写进**同一份** context。
        //    原实现把 qa_retrieved_documents 塞进局部 context，却又用「原始 context」另建
        //    advisedUserParams 并返回它 → after() 里读到的恒为 null，检索结果从响应侧不可观测。
        context.put("qa_retrieved_documents", documents);
        context.put("qa_recall_documents", candidates);
        // 改写变体也放进观测：多查询有没有真生效，只看这一项就够
        // （框架在「模型返回 null」或「输出行数 ≠ numberOfQueries」时会静默降级为单查询）。
        context.put("qa_expanded_queries", queries.stream().map(Query::text).collect(Collectors.toList()));

        String documentContext = documents.stream().map(Document::getText).collect(Collectors.joining(System.lineSeparator()));
        Map<String, Object> advisedUserParams = new HashMap<>(context);
        advisedUserParams.put("question_answer_context", documentContext);

        String advisedUserText = userText + System.lineSeparator()
                + new PromptTemplate(this.userTextAdvise).render(advisedUserParams);

        // 保留 prompt 中已有的全部消息（system prompt + ChatMemoryAdvisor 注入的历史），
        // 只把「最后一条用户消息」替换成注入了 RAG 上下文的版本。
        // ⚠️ 原实现是 Prompt.builder().messages(new UserMessage(advisedUserText)) ——
        //    messages(x) 属于**整体替换**而非追加，会把记忆消息与 defaultSystem 一并丢弃；
        //    又因本 advisor 的 order(=0) 大于 DEFAULT_CHAT_MEMORY_PRECEDENCE_ORDER
        //    (HIGHEST_PRECEDENCE + 1000)，它排在记忆注入之后执行，正好把记忆覆盖掉。
        List<Message> instructions = new ArrayList<>(chatClientRequest.prompt().getInstructions());
        int lastUserIndex = -1;
        for (int i = instructions.size() - 1; i >= 0; i--) {
            if (instructions.get(i) instanceof UserMessage) {
                lastUserIndex = i;
                break;
            }
        }
        if (lastUserIndex >= 0) {
            instructions.set(lastUserIndex, new UserMessage(advisedUserText));
        } else {
            instructions.add(new UserMessage(advisedUserText));
        }

        return ChatClientRequest.builder()
                .prompt(Prompt.builder().messages(instructions).build())
                .context(advisedUserParams)
                .build();
    }

    @Override
    public ChatClientResponse after(ChatClientResponse chatClientResponse, AdvisorChain advisorChain) {
        ChatResponse.Builder chatResponseBuilder = ChatResponse.builder().from(chatClientResponse.chatResponse());
        chatResponseBuilder.metadata("qa_retrieved_documents", chatClientResponse.context().get("qa_retrieved_documents"));
        ChatResponse chatResponse = chatResponseBuilder.build();

        return ChatClientResponse.builder()
                .chatResponse(chatResponse)
                .context(chatClientResponse.context())
                .build();
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain callAdvisorChain) {
        ChatClientResponse chatClientResponse = callAdvisorChain.nextCall(this.before(chatClientRequest, callAdvisorChain));
        return this.after(chatClientResponse, callAdvisorChain);
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest chatClientRequest, StreamAdvisorChain streamAdvisorChain) {
        return BaseAdvisor.super.adviseStream(chatClientRequest, streamAdvisorChain);
    }

    @Override
    public int getOrder() {
        return 0;
    }

    @Override
    public String getName() {
        return this.getClass().getSimpleName();
    }

    protected Filter.Expression doGetFilterExpression(Map<String, Object> context) {
        return context.containsKey("qa_filter_expression") && StringUtils.hasText((String) context.get("qa_filter_expression")) ? (new FilterExpressionTextParser()).parse((String) context.get("qa_filter_expression")) : this.searchRequest.getFilterExpression();
    }

    private Filter.Expression resolveFilterExpression(Map<String, Object> context) {
        // 1. 显式表达式优先
        if (context.containsKey("qa_filter_expression") && StringUtils.hasText((String) context.get("qa_filter_expression"))) {
            String expression = (String) context.get("qa_filter_expression");
            expression = resolvePlaceholders(expression, context);
            // 占位符未解析完成时不做强制解析，继续走动态标签/兜底逻辑
            if (StringUtils.hasText(expression) && !expression.contains("${")) {
                return (new FilterExpressionTextParser()).parse(expression);
            }
        }

        // 2. 动态注入 knowledgeTag
        Object tagObj = context.getOrDefault("knowledgeTag", context.get("ragKnowledgeTag"));
        if (tagObj != null && StringUtils.hasText(tagObj.toString())) {
            String expression = "knowledge == '" + tagObj.toString().replace("'", "") + "'";
            return (new FilterExpressionTextParser()).parse(expression);
        }

        // 3. 兜底：直接返回已解析的 Filter.Expression 对象，避免 toString 再解析
        return this.searchRequest.getFilterExpression();
    }

    private String resolvePlaceholders(String template, Map<String, Object> context) {
        if (template == null || !template.contains("${")) {
            return template;
        }
        String result = template;
        for (Map.Entry<String, Object> entry : context.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (key != null && value != null) {
                result = result.replace("${" + key + "}", value.toString());
            }
        }
        return result;
    }

}
