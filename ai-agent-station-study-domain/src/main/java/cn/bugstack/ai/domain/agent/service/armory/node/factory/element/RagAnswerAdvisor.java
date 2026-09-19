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

    private final VectorStore vectorStore;
    private final SearchRequest searchRequest;
    private final String userTextAdvise;

    public RagAnswerAdvisor(VectorStore vectorStore, SearchRequest searchRequest) {
        this.vectorStore = vectorStore;
        this.searchRequest = searchRequest;
        this.userTextAdvise = "\nContext information is below, surrounded by ---------------------\n\n---------------------\n{question_answer_context}\n---------------------\n\nGiven the context and provided history information and not prior knowledge,\nreply to the user comment. If the answer is not in the context, inform\nthe user that you can't answer the question.\n";
    }

    @Override
    public ChatClientRequest before(ChatClientRequest chatClientRequest, AdvisorChain advisorChain) {
        HashMap<String, Object> context = new HashMap(chatClientRequest.context());

        String userText = chatClientRequest.prompt().getUserMessage().getText();

        SearchRequest searchRequestToUse = SearchRequest.from(this.searchRequest).query(userText).filterExpression(this.resolveFilterExpression(context)).build();
        List<Document> documents = this.vectorStore.similaritySearch(searchRequestToUse);
        context.put("qa_retrieved_documents", documents);

        String documentContext = documents.stream().map(Document::getText).collect(Collectors.joining(System.lineSeparator()));
        Map<String, Object> advisedUserParams = new HashMap(chatClientRequest.context());
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
