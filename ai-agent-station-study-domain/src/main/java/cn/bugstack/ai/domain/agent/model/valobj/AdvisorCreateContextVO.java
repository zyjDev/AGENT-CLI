package cn.bugstack.ai.domain.agent.model.valobj;

import cn.bugstack.ai.domain.agent.service.context.IContextSummarizer;
import cn.bugstack.ai.domain.agent.service.context.ITokenCounter;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.ai.vectorstore.VectorStore;

/**
 * Advisor 创建期依赖包
 * <p>
 * 背景：{@code AiClientAdvisorTypeEnumVO.createAdvisor} 是枚举静态方法，拿不到 Spring Bean，
 * 而 Token 预算版的 ChatMemory 需要 token 计数器与摘要器。故把创建期所需依赖打包传入。
 *
 * @author xiaofuge bugstack.cn @小傅哥
 * 2026/09/14
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AdvisorCreateContextVO {

    /**
     * 向量库（RagAnswer 顾问使用）
     */
    private VectorStore vectorStore;

    /**
     * token 估算器
     */
    private ITokenCounter tokenCounter;

    /**
     * 上下文摘要器
     */
    private IContextSummarizer contextSummarizer;

    /**
     * 全局默认预算；DB ext_param 可逐项覆盖
     */
    private ContextBudgetVO defaultBudget;

}
