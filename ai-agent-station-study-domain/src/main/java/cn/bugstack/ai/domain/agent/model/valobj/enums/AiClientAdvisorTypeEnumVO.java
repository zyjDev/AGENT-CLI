package cn.bugstack.ai.domain.agent.model.valobj.enums;

import cn.bugstack.ai.domain.agent.model.valobj.AdvisorCreateContextVO;
import cn.bugstack.ai.domain.agent.model.valobj.AiClientAdvisorVO;
import cn.bugstack.ai.domain.agent.model.valobj.ContextBudgetVO;
import cn.bugstack.ai.domain.agent.service.context.TokenBudgetChatMemory;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.ai.chat.client.advisor.PromptChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.vectorstore.SearchRequest;
import cn.bugstack.ai.domain.agent.service.armory.node.factory.element.RagAnswerAdvisor;

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

            return PromptChatMemoryAdvisor.builder(chatMemory).build();
        }
    },
    
    RAG_ANSWER("RagAnswer", "知识库") {
        @Override
        public Advisor createAdvisor(AiClientAdvisorVO aiClientAdvisorVO, AdvisorCreateContextVO ctx) {
            AiClientAdvisorVO.RagAnswer ragAnswer = aiClientAdvisorVO.getRagAnswer();
            return new RagAnswerAdvisor(ctx.getVectorStore(), SearchRequest.builder()
                    .topK(ragAnswer.getTopK())
                    .filterExpression(ragAnswer.getFilterExpression())
                    .build());
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
