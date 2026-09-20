package cn.bugstack.ai.domain.agent.service.armory.node.factory.element;

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
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionTextParser;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

    /** 兼容旧调用：不精排 */
    public RagAnswerAdvisor(VectorStore vectorStore, SearchRequest searchRequest) {
        this(vectorStore, searchRequest, resolveTopK(searchRequest), resolveTopK(searchRequest), null);
    }

    public RagAnswerAdvisor(VectorStore vectorStore, SearchRequest searchRequest,
                            int topK, int recallK, DocumentPostProcessor postProcessor) {
        this.vectorStore = vectorStore;
        this.searchRequest = searchRequest;
        this.topK = topK > 0 ? topK : DEFAULT_TOP_K;
        this.recallK = Math.max(recallK, this.topK);
        this.postProcessor = postProcessor;
        this.userTextAdvise = "\nContext information is below, surrounded by ---------------------\n\n---------------------\n{question_answer_context}\n---------------------\n\nGiven the context and provided history information and not prior knowledge,\nreply to the user comment. If the answer is not in the context, inform\nthe user that you can't answer the question.\n";
    }

    private static int resolveTopK(SearchRequest searchRequest) {
        Integer topK = searchRequest == null ? null : searchRequest.getTopK();
        return topK == null || topK <= 0 ? DEFAULT_TOP_K : topK;
    }

    @Override
    public ChatClientRequest before(ChatClientRequest chatClientRequest, AdvisorChain advisorChain) {
        HashMap<String, Object> context = new HashMap<>(chatClientRequest.context());

        String userText = chatClientRequest.prompt().getUserMessage().getText();

        // ① 召回：池子放到 recallK（未开启精排时 recallK == topK，与改造前完全一致）
        //    ⚠️ SearchRequest.from(...) 是**复制**。绝不能在 this.searchRequest 上直接 set topK ——
        //    这个 advisor 是单例 Bean，改了会污染后续所有请求。
        SearchRequest recallRequest = SearchRequest.from(this.searchRequest)
                .query(userText)
                .topK(Math.max(this.recallK, this.topK))
                .filterExpression(this.resolveFilterExpression(context))
                .build();
        List<Document> candidates = this.vectorStore.similaritySearch(recallRequest);

        // ② 精排：候选比目标条数多时才值得重排；postProcessor 为 null 时直接跳过
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

        // ③ 观测：三个 key 写进**同一份** context。
        //    原实现把 qa_retrieved_documents 塞进局部 context，却又用「原始 context」另建
        //    advisedUserParams 并返回它 → after() 里读到的恒为 null，检索结果从响应侧不可观测。
        context.put("qa_retrieved_documents", documents);
        context.put("qa_recall_documents", candidates);

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
