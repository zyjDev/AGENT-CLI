package cn.bugstack.ai.domain.agent.model.valobj.enums;

import cn.bugstack.ai.domain.agent.model.valobj.AdvisorCreateContextVO;
import cn.bugstack.ai.domain.agent.model.valobj.AiClientAdvisorVO;
import cn.bugstack.ai.domain.agent.model.valobj.ContextBudgetVO;
import cn.bugstack.ai.domain.agent.service.context.TokenBudgetChatMemory;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.rag.postretrieval.document.DocumentPostProcessor;
import org.springframework.ai.vectorstore.SearchRequest;
import cn.bugstack.ai.domain.agent.service.armory.node.factory.element.RagAnswerAdvisor;
import cn.bugstack.ai.domain.agent.service.rag.rerank.LlmDocumentPostProcessor;

import java.util.HashMap;
import java.util.Map;

/**
 * 顾问类型枚举
 *
 * @author xiaofuge bugstack.cn @小傅哥
 * 2025/7/19 09:02
 */
@Getter
@AllArgsConstructor
@NoArgsConstructor
public enum AiClientAdvisorTypeEnumVO {

    CHAT_MEMORY("ChatMemory", "上下文记忆（Token 预算 + 摘要压缩）") {
        @Override
        public Advisor createAdvisor(AiClientAdvisorVO aiClientAdvisorVO, AdvisorCreateContextVO ctx) {
            // DB ext_param 逐项覆盖 yml 默认值；ext_param 为空时 override(null) 直接返回全局默认
            ContextBudgetVO budget = ctx.getDefaultBudget().override(aiClientAdvisorVO.getChatMemory());

            // 每个 ChatClient 持有独立的记忆仓库，与改造前 MessageWindowChatMemory 的行为保持一致
            // （同一 sessionId 在不同 client 之间不共享记忆）
            ChatMemory chatMemory = new TokenBudgetChatMemory(
                    new InMemoryChatMemoryRepository(),
                    ctx.getTokenCounter(),
                    ctx.getContextSummarizer(),
                    budget);

            // Spring AI 1.1.3 起 PromptChatMemoryAdvisor 标记待删除（1.1.6 起 conversationId 变必填），
            // 迁移到 MessageChatMemoryAdvisor：记忆不再渲染进 system prompt，而是作为消息插入 prompt，
            // 且不做消息类型过滤 —— TokenBudgetChatMemory 用 UserMessage 承载摘要的方式继续有效。
            return MessageChatMemoryAdvisor.builder(chatMemory).build();
        }
    },
    
    RAG_ANSWER("RagAnswer", "知识库") {
        @Override
        public Advisor createAdvisor(AiClientAdvisorVO aiClientAdvisorVO, AdvisorCreateContextVO ctx) {
            // ⚠️ ext_param 为空时 getRagAnswer() 返回 null，原实现直接 .getTopK() 会 NPE ——
            //    而这一步在 Armory 装配期执行，一旦抛异常整个应用启动即挂（存量脏数据就会触发）。
            AiClientAdvisorVO.RagAnswer cfg = aiClientAdvisorVO.getRagAnswer() == null
                    ? new AiClientAdvisorVO.RagAnswer()
                    : aiClientAdvisorVO.getRagAnswer();

            // 精排器只在「开关打开 + 配了模型 + 池子比目标条数大」时才构造。
            // 拿不到模型（Bean 名写错 / 模型节点未装配）一律降级为不精排，不在装配期报错。
            DocumentPostProcessor postProcessor = null;
            if (cfg.rerankActive() && ctx.getRerankChatModel() != null) {
                postProcessor = new LlmDocumentPostProcessor(
                        ctx.getRerankChatModel(),
                        cfg.getTopK(),
                        // 默认 25s：精排是一次 20 候选的 listwise 调用。实测 mimo-v2.5 关推理后，
                        // 候选片段截断到 1200 字符时单次约 11s，给 3s 等于每次都降级（踩过）。
                        cfg.getRerankTimeoutMs() == null ? 25000 : cfg.getRerankTimeoutMs(),
                        // 默认 1200：语料 chunk 中位长度在 850~3500 字符之间，截断太狠会把答案本身
                        // 切掉 —— 精排就变成「凭开头猜主题」，实测 400 字符时排序质量明显更差。
                        cfg.getRerankDocChars() == null ? 1200 : cfg.getRerankDocChars());
            }
            // 没精排时 recallK 回落成 topK → 只召回一次、不做重排，与改造前行为完全等价
            int recallK = postProcessor == null ? cfg.getTopK() : cfg.getRecallK();

            return new RagAnswerAdvisor(ctx.getVectorStore(), SearchRequest.builder()
                    .topK(cfg.getTopK())
                    .filterExpression(cfg.getFilterExpression())
                    .build(),
                    cfg.getTopK(), recallK, postProcessor);
        }
    }
    
    ;

    private String code;
    private String info;
    
    // 静态Map缓存，用于快速查找
    private static final Map<String, AiClientAdvisorTypeEnumVO> CODE_MAP = new HashMap<>();
    
    // 静态初始化块，在类加载时初始化Map
    static {
        for (AiClientAdvisorTypeEnumVO enumVO : values()) {
            CODE_MAP.put(enumVO.getCode(), enumVO);
        }
    }
    
    /**
     * 策略方法：创建顾问对象
     * <p>
     * 参数从单个 VectorStore 改为 {@link AdvisorCreateContextVO}：
     * 枚举静态方法拿不到 Spring Bean，而 Token 预算版 ChatMemory 需要 token 计数器与摘要器，
     * 故把创建期依赖打包传入。
     *
     * @param aiClientAdvisorVO 顾问配置对象
     * @param ctx               创建期依赖包
     * @return 顾问对象
     */
    public abstract Advisor createAdvisor(AiClientAdvisorVO aiClientAdvisorVO, AdvisorCreateContextVO ctx);
    
    /**
     * 根据code获取枚举
     * @param code 编码
     * @return 枚举对象
     */
    public static AiClientAdvisorTypeEnumVO getByCode(String code) {
        AiClientAdvisorTypeEnumVO enumVO = CODE_MAP.get(code);
        if (enumVO == null) {
            throw new RuntimeException("err! advisorType " + code + " not exist!");
        }
        return enumVO;
    }

}
